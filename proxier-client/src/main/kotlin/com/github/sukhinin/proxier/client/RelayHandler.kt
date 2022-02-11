package com.github.sukhinin.proxier.client

import com.github.sukhinin.proxier.client.ChannelUtils.flushAndClose
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

class RelayHandler(private val outbound: Channel) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(RelayHandler::class.java)

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        outbound.flushAndClose()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (outbound.isActive) {
            outbound.writeAndFlush(msg)
        } else {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("${ctx.channel()} Exception caught", cause)
        outbound.flushAndClose()
    }
}
