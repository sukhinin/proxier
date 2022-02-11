package com.github.sukhinin.proxier.netty

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener

object ChannelUtils {
    fun Channel.flushAndClose() {
        if (this.isActive) {
            this.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}
