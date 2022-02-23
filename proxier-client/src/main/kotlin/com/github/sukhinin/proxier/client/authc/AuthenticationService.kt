package com.github.sukhinin.proxier.client.authc

import com.github.sukhinin.proxier.authc.ClientAuthenticationConfig
import com.github.sukhinin.proxier.http.HttpUtils.formEncode
import com.github.sukhinin.proxier.http.HttpUtils.urlResolve
import com.google.common.io.BaseEncoding
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AuthenticationService(
    private val config: ClientAuthenticationConfig,
    private val callbackPath: String
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(AuthenticationService::class.java)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val challengeGenerator = ChallengeGenerator()
    private val tokensRef = AtomicReference<Tokens?>()

    init {
        scheduler.scheduleWithFixedDelay(
            { ignoreAuthenticationExceptions(::refreshTokens) },
            config.tokenRefreshInterval, config.tokenRefreshInterval, TimeUnit.SECONDS
        )
    }

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
        params["scope"] = config.clientScope

        return config.authorizationEndpoint + "?" + formEncode(params)
    }

    @Synchronized
    fun authenticate(requestUrl: String, state: String, code: String) {
        exchangeAuthorizationCodeForTokens(requestUrl, state, code).also(tokensRef::set)
    }

    @Synchronized
    fun getAccessToken(): String {
        val tokens = tokensRef.get() ?: throw AuthenticationException("Authentication required")
        if (tokens.timestamp + tokens.expiresAfter - Duration.ofSeconds(config.tokenRefreshMargin) > Instant.now()) {
            return tokens.access
        }
        val refreshedTokens = exchangeRefreshTokenForTokens(tokens)
        tokensRef.set(refreshedTokens)
        return refreshedTokens.access
    }

    @Synchronized
    fun refreshTokens() {
        val tokens = tokensRef.get() ?: throw AuthenticationException("Authentication required")
        val refreshedTokens = exchangeRefreshTokenForTokens(tokens)
        tokensRef.set(refreshedTokens)
    }

    internal fun exchangeAuthorizationCodeForTokens(requestUrl: String, state: String, code: String): Tokens {
        logger.info("Exchanging authorization code for tokens")

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

    internal fun exchangeRefreshTokenForTokens(tokens: Tokens): Tokens {
        logger.info("Refreshing tokens")

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

    internal fun ignoreAuthenticationExceptions(block: () -> Unit) {
        try {
            block.invoke()
        } catch (e: AuthenticationException) {
            // Authentication exceptions ignored
        }
    }

    override fun close() {
        scheduler.shutdown()
    }

    internal data class Tokens(
        val refresh: String,
        val access: String,
        val expiresAfter: Duration,
        val timestamp: Instant
    )
}
