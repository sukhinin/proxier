package com.github.sukhinin.proxier.utils

import java.net.InetSocketAddress
import java.net.URI

data class HostAndPort(val host: String, val port: Int) {
    companion object {
        fun parse(s: String): HostAndPort {
            val uri = URI.create("my://$s")
            return HostAndPort(uri.host, uri.port)
        }
    }

    fun toInetSocketAddress(): InetSocketAddress {
        return InetSocketAddress(host, port)
    }

    override fun toString(): String {
        return "$host:$port"
    }
}
