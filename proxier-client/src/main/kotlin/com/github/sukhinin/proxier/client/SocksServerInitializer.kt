package com.github.sukhinin.proxier.client

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

class SocksServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(
            LoggingHandler(LogLevel.DEBUG),
            SocksPortUnificationServerHandler(),
            SocksServerHandler()
        )
    }
}
