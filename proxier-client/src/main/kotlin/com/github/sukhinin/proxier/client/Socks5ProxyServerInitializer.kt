package com.github.sukhinin.proxier.client

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder

class Socks5ProxyServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(
            Socks5ServerEncoder(Socks5AddressEncoder.DEFAULT),
            Socks5InitialRequestDecoder(),
            Socks5CommandRequestDecoder(),
            Socks5ProxyServerHandler()
        )
    }
}

