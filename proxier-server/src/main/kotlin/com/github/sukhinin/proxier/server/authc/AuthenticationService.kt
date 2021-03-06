package com.github.sukhinin.proxier.server.authc

import com.github.sukhinin.proxier.authc.ClientAuthenticationConfig

class AuthenticationService(private val config: AuthenticationConfig) {

    fun getClientConfig(): ClientAuthenticationConfig {
        return ClientAuthenticationConfig(
            clientId = config.clientId,
            clientScope = config.clientScope,
            tokenRefreshMargin = config.tokenRefreshMargin,
            tokenRefreshInterval = config.tokenRefreshInterval,
            authorizationEndpoint = config.authorizationEndpoint,
            tokenEndpoint = config.tokenEndpoint
        )
    }
}
