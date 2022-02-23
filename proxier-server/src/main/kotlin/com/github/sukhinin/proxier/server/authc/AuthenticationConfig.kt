package com.github.sukhinin.proxier.server.authc

import java.time.Duration

data class AuthenticationConfig(
    val clientId: String,
    val clientScope: String,
    val tokenRefreshInterval: Duration,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String
)

