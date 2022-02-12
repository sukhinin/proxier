package com.github.sukhinin.proxier.client

import com.github.sukhinin.proxier.utils.HostAndPort
import io.netty.handler.ssl.SslContext
import io.netty.util.AttributeKey

object Attributes {
    val serverAddress = AttributeKey.newInstance<HostAndPort>("serverAddress")
    val clientSslContext = AttributeKey.newInstance<SslContext>("clientSslContext")
}
