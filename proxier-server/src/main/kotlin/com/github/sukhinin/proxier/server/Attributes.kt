package com.github.sukhinin.proxier.server

import com.github.sukhinin.proxier.server.authc.AuthenticationService
import io.netty.handler.ssl.SslContext
import io.netty.util.AttributeKey

object Attributes {
    val serverSslContext: AttributeKey<SslContext?> = AttributeKey.newInstance("serverSslContext")
    val authenticationService: AttributeKey<AuthenticationService> = AttributeKey.newInstance("authenticationService")
}
