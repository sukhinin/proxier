package com.github.sukhinin.proxier.server.authc

data class AuthenticationConfig(
    val clientId: String,
    val clientScope: String,
    val tokenRefreshMargin: Long,
    val tokenRefreshInterval: Long,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String
)

