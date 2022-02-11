package com.github.sukhinin.proxier.server

import com.github.sukhinin.proxier.netty.BackPressureHandler
import com.github.sukhinin.proxier.netty.ChannelActivationHandler
import com.github.sukhinin.proxier.netty.ChannelUtils.flushAndClose
import com.github.sukhinin.proxier.netty.RelayHandler
import com.google.common.net.HostAndPort
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.FutureListener
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.URI

class HttpProxyServerHandler : SimpleChannelInboundHandler<HttpObject>() {

    private val logger = LoggerFactory.getLogger(HttpProxyServerHandler::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        when (msg) {
            is HttpRequest -> handleHttpRequest(ctx, msg)
            is HttpContent -> handleHttpContent(ctx, msg)
            else -> handleUnexpectedMessage(ctx, msg)
        }
    }

    private fun handleHttpRequest(ctx: ChannelHandlerContext, msg: HttpRequest) {
        when (msg.method()) {
            HttpMethod.CONNECT -> handleHttpConnectRequest(ctx, msg)
            else -> handleUnexpectedMessage(ctx, msg)
        }
    }

    private fun handleHttpContent(ctx: ChannelHandlerContext, msg: HttpContent) {
        if (msg != LastHttpContent.EMPTY_LAST_CONTENT) {
            handleUnexpectedMessage(ctx, msg)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun handleHttpConnectRequest(ctx: ChannelHandlerContext, msg: HttpRequest) {
        val promise = ctx.executor().newPromise<Channel>()
        promise.addListener(FutureListener { future ->
            logger.info("${ctx.channel()} connected to ${future.now}")
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

        val hostAndPort = HostAndPort.fromString(msg.uri())
        bootstrap.connect(hostAndPort.host, hostAndPort.port).addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                handleOutboundConnectionFailed(ctx, msg)
            }
        })
    }

    private fun handleOutboundConnectionEstablished(ctx: ChannelHandlerContext, msg: HttpRequest, ch: Channel) {
        val response = DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.OK)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener {
            ctx.pipeline().remove(HttpServerCodec::class.java)
            ctx.pipeline().remove(this)

            ctx.pipeline().addLast(BackPressureHandler(ch))
            ctx.pipeline().addLast(RelayHandler(ch))
            ch.pipeline().addLast(BackPressureHandler(ctx.channel()))
            ch.pipeline().addLast(RelayHandler(ctx.channel()))
        })
    }

    private fun handleOutboundConnectionFailed(ctx: ChannelHandlerContext, msg: HttpRequest) {
        val response = DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.BAD_GATEWAY)
        ctx.channel().write(response)
        ctx.channel().flushAndClose()
    }

    private fun handleUnexpectedMessage(ctx: ChannelHandlerContext, msg: HttpObject) {
        logger.error("${ctx.channel()} Unexpected message: $msg")
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("${ctx.channel()} Exception caught", cause)
        ctx.channel().flushAndClose()
    }
}
