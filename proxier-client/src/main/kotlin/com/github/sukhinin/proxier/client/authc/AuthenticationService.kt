package com.github.sukhinin.proxier.client.authc

import com.github.sukhinin.proxier.authc.ClientAuthenticationConfig
import com.github.sukhinin.proxier.http.HttpUtils.formEncode
import com.github.sukhinin.proxier.http.HttpUtils.urlResolve
import com.google.common.io.BaseEncoding
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class AuthenticationService(private val config: ClientAuthenticationConfig, private val callbackPath: String) {

    private val tokenExpirationMargin = Duration.ofSeconds(30)

    private val logger = LoggerFactory.getLogger(AuthenticationService::class.java)
    private val challengeGenerator = ChallengeGenerator()
    private val tokensRef = AtomicReference<Tokens?>()

    fun getRemoteProviderAuthenticationUrl(requestUrl: String): String {
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

    fun authenticate(requestUrl: String, state: String, code: String) {
        retrieveTokens(requestUrl, state, code).also(tokensRef::set)
    }

    internal fun retrieveTokens(requestUrl: String, state: String, code: String): Tokens {
        logger.info("Retrieving tokens")

        val seed = BaseEncoding.base64Url().decode(state)
        val verifier = challengeGenerator.generateVerifier(seed)

        val params = HashMap<String, String>()
        params["client_id"] = config.clientId
        params["grant_type"] = "authorization_code"
        params["code"] = code
        params["redirect_uri"] = urlResolve(requestUrl, callbackPath)
        params["code_verifier"] = verifier

        val json = wrapAuthenticationExceptions("Failed to retrieve tokens") {
            RemoteProvider.call(config.tokenEndpoint, params)
        }

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

    fun getAccessToken(): String {
        val tokens = tokensRef.get() ?: throw AuthenticationException("Authentication required")
        val expired = tokens.timestamp + tokens.expiresAfter - tokenExpirationMargin < Instant.now()
        return if (expired) refreshTokens(tokens).also(tokensRef::set).access else tokens.access
    }

    internal fun refreshTokens(tokens: Tokens): Tokens {
        logger.info("Refreshing access token")

        val params = HashMap<String, String>()
        params["client_id"] = config.clientId
        params["grant_type"] = "refresh_token"
        params["refresh_token"] = tokens.refresh

        val json = wrapAuthenticationExceptions("Failed to refresh tokens") {
            RemoteProvider.call(config.tokenEndpoint, params)
        }

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

    internal data class Tokens(
        val refresh: String,
        val access: String,
        val expiresAfter: Duration,
        val timestamp: Instant
    )
}
