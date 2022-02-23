package com.github.sukhinin.proxier.authc

import java.time.Duration

data class ClientAuthenticationConfig(
    val clientId: String,
    val clientScope: String,
    val tokenRefreshInterval: Duration,
    val authorizationEndpoint: String,
    val tokenEndpoint: String
) {
    fun dump(): String {
        val builder = StringBuilder()
        builder.appendLine("oidc.client.id=$clientId")
        builder.appendLine("oidc.client.scope=$clientScope")
        builder.appendLine("oidc.token.refresh.interval=$tokenRefreshInterval")
        builder.appendLine("oidc.endpoints.authorization=$authorizationEndpoint")
        builder.appendLine("oidc.endpoints.token=$tokenEndpoint")
        return builder.toString().trim()
    }
}
