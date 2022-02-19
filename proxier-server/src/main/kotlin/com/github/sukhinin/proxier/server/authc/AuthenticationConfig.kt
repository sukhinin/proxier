package com.github.sukhinin.proxier.server.authc

data class AuthenticationConfig(
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String
)
