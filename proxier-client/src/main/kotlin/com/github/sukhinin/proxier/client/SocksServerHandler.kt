package com.github.sukhinin.proxier.client

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest
import io.netty.handler.codec.socksx.v4.Socks4CommandType
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.LoggerFactory

class SocksServerHandler : SimpleChannelInboundHandler<SocksMessage>() {

    private val logger = LoggerFactory.getLogger(SocksServerHandler::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: SocksMessage) {
        when (msg.version()) {
            SocksVersion.SOCKS4a -> handleSocks4Read(ctx, msg)
            SocksVersion.SOCKS5 -> handleSocks5Read(ctx, msg)
            else -> ctx.close()
        }
    }

    private fun handleSocks4Read(ctx: ChannelHandlerContext, msg: SocksMessage) {
        when (msg) {
            is Socks4CommandRequest -> handleSocks4CommandRequest(ctx, msg)
            else -> ctx.close()
        }
    }

    private fun handleSocks4CommandRequest(ctx: ChannelHandlerContext, msg: Socks4CommandRequest) {
        if (msg.type() == Socks4CommandType.CONNECT) {
            ctx.pipeline().addLast(SocksServerConnectHandler())
            ctx.pipeline().remove(this)
            ctx.fireChannelRead(msg)
        } else {
            ctx.close()
        }
    }

    private fun handleSocks5Read(ctx: ChannelHandlerContext, msg: SocksMessage) {
        when (msg) {
            is Socks5InitialRequest -> handleSocks5InitialRequest(ctx)
            is Socks5PasswordAuthRequest -> handleSocks5PasswordAuthRequest(ctx)
            is Socks5CommandRequest -> handleSocks5CommandRequest(ctx, msg)
            else -> ctx.close()
        }
    }

    private fun handleSocks5InitialRequest(ctx: ChannelHandlerContext) {
        ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
        ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
    }

    private fun handleSocks5PasswordAuthRequest(ctx: ChannelHandlerContext) {
        ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
        ctx.write(DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
    }

    private fun handleSocks5CommandRequest(ctx: ChannelHandlerContext, msg: Socks5CommandRequest) {
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
