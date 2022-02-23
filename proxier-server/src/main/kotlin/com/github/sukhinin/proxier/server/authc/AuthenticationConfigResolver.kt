package com.github.sukhinin.proxier.server.authc

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.sukhinin.simpleconfig.Config
import com.github.sukhinin.simpleconfig.contains
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object AuthenticationConfigResolver {
    fun resolve(config: Config): AuthenticationConfig {
        return when (config.contains("oidc.endpoints.wellknown")) {
            true -> resolveFromWellKnownEndpoint(config)
            else -> resolveFromStaticConfig(config)
        }
    }

    private fun resolveFromWellKnownEndpoint(config: Config): AuthenticationConfig {
        val endpoint = config.get("oidc.endpoints.wellknown")

        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
        val request = HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(Duration.ofSeconds(5)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

        if (response.statusCode() !in 200..299) {
            throw IOException("Bad status code: ${response.statusCode()}")
        }

        val json = ObjectMapper().readTree(response.body())
        return AuthenticationConfig(
            clientId = config.get("oidc.client.id"),
            clientScope = config.get("oidc.client.scope"),
            tokenRefreshInterval = config.get("oidc.token.refresh.interval").let(Duration::parse),
            authorizationEndpoint = json.get("authorization_endpoint").asText(),
            tokenEndpoint = json.get("token_endpoint").asText(),
            userInfoEndpoint = json.get("userinfo_endpoint").asText()
        )
    }

    private fun resolveFromStaticConfig(config: Config): AuthenticationConfig {
        return AuthenticationConfig(
            clientId = config.get("oidc.client.id"),
            clientScope = config.get("oidc.client.scope"),
            tokenRefreshInterval = config.get("oidc.token.refresh.interval").let(Duration::parse),
            authorizationEndpoint = config.get("oidc.endpoints.authorization"),
            tokenEndpoint = config.get("oidc.endpoints.token"),
            userInfoEndpoint = config.get("oidc.endpoints.userinfo")
        )
    }
}
