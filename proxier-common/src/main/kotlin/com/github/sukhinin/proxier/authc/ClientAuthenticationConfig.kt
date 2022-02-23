package com.github.sukhinin.proxier.authc

data class ClientAuthenticationConfig(
    val clientId: String,
    val clientScope: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String
) {
    fun dump(): String {
        val builder = StringBuilder()
        builder.appendLine("oidc.client.id=$clientId")
        builder.appendLine("oidc.client.scope=$clientScope")
        builder.appendLine("oidc.endpoints.authorization=$authorizationEndpoint")
        builder.appendLine("oidc.endpoints.token=$tokenEndpoint")
        return builder.toString().trim()
    }
}
