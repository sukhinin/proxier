package com.github.sukhinin.proxier.client.authc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.sukhinin.proxier.authc.ClientAuthenticationConfig
import com.github.sukhinin.proxier.http.HttpUtils.formEncode
import com.github.sukhinin.proxier.http.HttpUtils.urlResolve
import com.google.common.io.BaseEncoding
import io.undertow.server.handlers.BlockingHandler
import io.undertow.util.Headers
import io.undertow.util.StatusCodes
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class AuthenticationService(private val config: ClientAuthenticationConfig, private val callbackPath: String) {

    private val challengeGenerator = ChallengeGenerator()
    private val objectMapper = ObjectMapper()
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val tokensRef = AtomicReference<Tokens?>()

    fun authenticationRequestHandler() = BlockingHandler { exchange ->
        exchange.statusCode = StatusCodes.FOUND
        exchange.responseHeaders.put(Headers.LOCATION, getRemoteAuthorizationUrl(exchange.requestURL))
        exchange.endExchange()
    }

    internal fun getRemoteAuthorizationUrl(requestUrl: String): String {
        val seed = challengeGenerator.generateSeed()
        val challenge = challengeGenerator.generateChallenge(seed)

        val params = HashMap<String, String>()
        params["client_id"] = config.clientId
        params["redirect_uri"] = urlResolve(requestUrl, callbackPath)
        params["response_type"] = "code"
        params["state"] = BaseEncoding.base64Url().encode(seed)
        params["code_challenge"] = challenge
        params["code_challenge_method"] = challengeGenerator.getMethod()
        params["scope"] = "openid"

        return config.authorizationEndpoint + "?" + formEncode(params)
    }

    fun authenticationCallbackHandler() = BlockingHandler { exchange ->
        val state = requireNotNull(exchange.queryParameters["state"]?.single()) { "Missing query parameter: state" }
        val code = requireNotNull(exchange.queryParameters["code"]?.single()) { "Missing query parameter: code" }

        val tokens = getTokens(exchange.requestURL, state, code)
        tokensRef.set(tokens)

        exchange.statusCode = 200
        exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/plain")
        exchange.responseSender.send("OK")
        exchange.endExchange()
    }

    internal fun getTokens(requestUrl: String, state: String, code: String): Tokens {
        val seed = BaseEncoding.base64Url().decode(state)
        val verifier = challengeGenerator.generateVerifier(seed)

        val params = HashMap<String, String>()
        params["client_id"] = config.clientId
        params["grant_type"] = "authorization_code"
        params["code"] = code
        params["redirect_uri"] = urlResolve(requestUrl, callbackPath)
        params["code_verifier"] = verifier

        val json = wrapAuthenticationExceptions("Failed to retrieve tokens") { callTokenEndpoint(params) }
        val refreshToken = json.path("refresh_token").textValue()
        val accessToken = json.path("access_token").textValue()
        val expiresAfter = json.path("expires_in").longValue()

        when {
            refreshToken == null -> throw AuthenticationException("Missing refresh token")
            accessToken == null -> throw AuthenticationException("Missing access token")
            expiresAfter <= 0 -> throw AuthenticationException("Missing expiration interval")
        }

        return Tokens(refreshToken, accessToken, Duration.ofSeconds(expiresAfter), Instant.now())
    }

    internal fun refreshTokens(tokens: Tokens?): Tokens {
        if (tokens == null) {
            throw AuthenticationException("Authentication required")
        }

        val params = HashMap<String, String>()
        params["client_id"] = config.clientId
        params["grant_type"] = "refresh_token"
        params["refresh_token"] = tokens.refresh

        val json = wrapAuthenticationExceptions("Failed to refresh tokens") { callTokenEndpoint(params) }
        val accessToken = json.path("access_token").textValue()
        val refreshToken = json.path("refresh_token").asText(tokens.refresh)
        val expiresAfter = json.path("expires_in").asLong(tokens.expiresAfter.toSeconds())

        if (accessToken == null) {
            throw AuthenticationException("Missing access token")
        }

        return Tokens(refreshToken, accessToken, Duration.ofSeconds(expiresAfter), Instant.now())
    }

    internal fun <T> wrapAuthenticationExceptions(genericErrorMessage: String, block: () -> T): T {
        try {
            return block.invoke()
        } catch (e: AuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw AuthenticationException(genericErrorMessage, e)
        }
    }

    internal fun callTokenEndpoint(params: Map<String, String>): JsonNode {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(config.tokenEndpoint))
            .POST(HttpRequest.BodyPublishers.ofString(formEncode(params)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(5))
            .build()

        val res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() !in 200..299) {
            val message = try {
                val json = objectMapper.readTree(res.body())
                val error = requireNotNull(json.path("error").textValue())
                val description = json.path("error_description").textValue()
                if (description == null) error else "$description ($error)"
            } catch (e: Exception) {
                "Bad HTTP response: code ${res.statusCode()}"
            }
            throw AuthenticationException(message)
        }

        return objectMapper.readTree(res.body())
    }

    internal data class Tokens(
        val refresh: String,
        val access: String,
        val expiresAfter: Duration,
        val timestamp: Instant
    )
}
