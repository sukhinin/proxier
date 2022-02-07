package com.github.sukhinin.proxier.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.FutureListener
import org.slf4j.LoggerFactory

class SocksServerConnectHandler : SimpleChannelInboundHandler<SocksMessage>() {

    private val logger = LoggerFactory.getLogger(SocksServerConnectHandler::class.java)
    private val bootstrap = Bootstrap()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: SocksMessage) {
        when (msg) {
            is Socks5CommandRequest -> handleCommandRequest(ctx, msg)
            else -> ctx.close()
        }
    }

    private fun handleCommandRequest(ctx: ChannelHandlerContext, msg: Socks5CommandRequest) {
        val promise = ctx.executor().newPromise<Channel>()
        promise.addListener(FutureListener { future ->
            val outboundChannel = future.now
            if (future.isSuccess) {
                val response = DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    msg.dstAddrType(), msg.dstAddr(), msg.dstPort()
                )
                ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener {
                    ctx.pipeline().remove(this)
                    ctx.pipeline().addLast(RelayHandler(outboundChannel))
                    outboundChannel.pipeline().addLast(RelayHandler(ctx.channel()))
                })
            } else {
                val response = DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, msg.dstAddrType())
                ctx.channel().writeAndFlush(response)
                SocketServerUtils.closeOnFlush(ctx.channel())
            }
        })

        bootstrap
            .group(ctx.channel().eventLoop())
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(DirectClientHandler(promise))

        bootstrap.connect(msg.dstAddr(), msg.dstPort()).addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                val response = DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, msg.dstAddrType())
                ctx.channel().writeAndFlush(response)
                SocketServerUtils.closeOnFlush(ctx.channel())
            }
        })
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Error processing socks message", cause)
        SocketServerUtils.closeOnFlush(ctx.channel())
    }
}
