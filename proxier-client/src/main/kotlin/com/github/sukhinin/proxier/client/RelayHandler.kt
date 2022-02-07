package com.github.sukhinin.proxier.client

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

class RelayHandler(private val relayChannel: Channel) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(RelayHandler::class.java)

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        SocketServerUtils.closeOnFlush(relayChannel)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (relayChannel.isActive) {
            relayChannel.writeAndFlush(msg)
        } else {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Error processing socks message", cause)
        SocketServerUtils.closeOnFlush(ctx.channel())
    }
}
