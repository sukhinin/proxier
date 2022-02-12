package com.github.sukhinin.proxier.server

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec

class HttpProxyServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val serverSslContext = ch.attr(Attributes.serverSslContext).get()
        if (serverSslContext != null) {
            ch.pipeline().addLast(serverSslContext.newHandler(ch.alloc()))
        }

        ch.pipeline().addLast(
            HttpServerCodec(),
            HttpProxyServerHandler()
        )
    }
}
