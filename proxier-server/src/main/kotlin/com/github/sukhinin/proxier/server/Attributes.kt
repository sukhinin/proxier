package com.github.sukhinin.proxier.server

import io.netty.handler.ssl.SslContext
import io.netty.util.AttributeKey

object Attributes {
    val serverSslContext = AttributeKey.newInstance<SslContext?>("serverSslContext")
}
