package com.github.sukhinin.proxier.client

import com.github.sukhinin.proxier.netty.BackPressureHandler
import com.github.sukhinin.proxier.netty.ChannelActivationHandler
import com.github.sukhinin.proxier.netty.ChannelUtils.flushAndClose
import com.github.sukhinin.proxier.netty.RelayHandler
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.*
import io.netty.util.concurrent.FutureListener
import org.slf4j.LoggerFactory

class Socks5ProxyServerHandler : SimpleChannelInboundHandler<SocksMessage>() {

    private val logger = LoggerFactory.getLogger(Socks5ProxyServerHandler::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: SocksMessage) {
        when (msg) {
            is Socks5InitialRequest -> handleSocks5InitialRequest(ctx)
            is Socks5CommandRequest -> handleSocks5CommandRequest(ctx, msg)
            else -> handleUnexpectedMessage(ctx, msg)
        }
    }

    private fun handleSocks5InitialRequest(ctx: ChannelHandlerContext) {
        ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
    }

    private fun handleSocks5CommandRequest(ctx: ChannelHandlerContext, msg: Socks5CommandRequest) {
        when (msg.type()) {
            Socks5CommandType.CONNECT -> handleSocks5ConnectCommandRequest(ctx, msg)
            else -> handleUnexpectedMessage(ctx, msg)
        }
    }

    private fun handleSocks5ConnectCommandRequest(ctx: ChannelHandlerContext, msg: Socks5CommandRequest) {
        val promise = ctx.executor().newPromise<Channel>()
        promise.addListener(FutureListener { future ->
            when (future.isSuccess) {
                true -> handleOutboundConnectionEstablished(ctx, msg, future.now)
                else -> handleOutboundConnectionFailed(ctx, msg)
            }
        })

        val bootstrap = Bootstrap()
            .group(ctx.channel().eventLoop())
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(ChannelActivationHandler(promise))

        bootstrap.connect(msg.dstAddr(), msg.dstPort()).addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                handleOutboundConnectionFailed(ctx, msg)
            }
        })
    }

    private fun handleOutboundConnectionEstablished(ctx: ChannelHandlerContext, msg: Socks5CommandRequest, ch: Channel) {
            val response = DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS,
                msg.dstAddrType(), msg.dstAddr(), msg.dstPort()
            )
            ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener {
                ctx.pipeline().remove(Socks5ServerEncoder::class.java)
                ctx.pipeline().remove(Socks5InitialRequestDecoder::class.java)
                ctx.pipeline().remove(Socks5CommandRequestDecoder::class.java)
                ctx.pipeline().remove(this)

                ctx.pipeline().addLast(BackPressureHandler(ch))
                ctx.pipeline().addLast(RelayHandler(ch))
                ch.pipeline().addLast(BackPressureHandler(ctx.channel()))
                ch.pipeline().addLast(RelayHandler(ctx.channel()))
            })
    }

    private fun handleOutboundConnectionFailed(ctx: ChannelHandlerContext, msg: Socks5CommandRequest) {
        val response = DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, msg.dstAddrType())
        ctx.channel().writeAndFlush(response)
        ctx.channel().flushAndClose()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    private fun handleUnexpectedMessage(ctx: ChannelHandlerContext, msg: SocksMessage) {
        logger.error("${ctx.channel()} Unexpected message: $msg")
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("${ctx.channel()} Exception caught", cause)
        ctx.channel().flushAndClose()
    }
}
