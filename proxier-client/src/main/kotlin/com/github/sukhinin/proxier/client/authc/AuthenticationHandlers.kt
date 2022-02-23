package com.github.sukhinin.proxier.client.authc

import io.undertow.server.handlers.BlockingHandler
import io.undertow.util.Headers
import io.undertow.util.StatusCodes

class AuthenticationHandlers(private val authenticationService: AuthenticationService) {

    fun authenticationRequestHandler() = BlockingHandler { exchange ->
        val location = authenticationService.getRemoteProviderAuthenticationUrl(exchange.requestURL)

        exchange.statusCode = StatusCodes.FOUND
        exchange.responseHeaders.put(Headers.LOCATION, location)
        exchange.endExchange()
    }

    fun authenticationCallbackHandler() = BlockingHandler { exchange ->
        val state = requireNotNull(exchange.queryParameters["state"]?.single()) { "Missing query parameter: state" }
        val code = requireNotNull(exchange.queryParameters["code"]?.single()) { "Missing query parameter: code" }

        authenticationService.authenticate(exchange.requestURL, state, code)

        exchange.statusCode = 200
        exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/plain")
        exchange.responseSender.send("OK")
        exchange.endExchange()
    }
}
