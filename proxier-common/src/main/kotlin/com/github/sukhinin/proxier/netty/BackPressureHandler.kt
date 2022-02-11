package com.github.sukhinin.proxier.netty

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption

class BackPressureHandler(private val inbound: Channel) : ChannelInboundHandlerAdapter() {

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        inbound.config().isAutoRead = ctx.channel().isWritable
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        inbound.config().isAutoRead = true
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        inbound.config().isAutoRead = ctx.channel().isWritable
        ctx.fireChannelWritabilityChanged()
    }
}
