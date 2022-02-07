package com.github.sukhinin.proxier.client

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.LoggerFactory

class SocksServerHandler : SimpleChannelInboundHandler<SocksMessage>() {

    private val logger = LoggerFactory.getLogger(SocksServerHandler::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: SocksMessage) {
        when (msg) {
            is Socks5InitialRequest -> handleInitialRequest(ctx)
            is Socks5PasswordAuthRequest -> handlePasswordAuthRequest(ctx)
            is Socks5CommandRequest -> handleCommandRequest(ctx, msg)
            else -> ctx.close()
        }
    }

    private fun handleInitialRequest(ctx: ChannelHandlerContext) {
        ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
        ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
    }

    private fun handlePasswordAuthRequest(ctx: ChannelHandlerContext) {
        ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
        ctx.write(DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
    }

    private fun handleCommandRequest(ctx: ChannelHandlerContext, msg: Socks5CommandRequest) {
        if (msg.type() == Socks5CommandType.CONNECT) {
            ctx.pipeline().addLast(SocksServerConnectHandler())
            ctx.pipeline().remove(this)
            ctx.fireChannelRead(msg)
        } else {
            ctx.close()
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Exception caught", cause)
        SocketServerUtils.closeOnFlush(ctx.channel())
    }
}
