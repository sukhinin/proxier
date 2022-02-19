package com.github.sukhinin.proxier.server

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

class HttpDispatcherHandler : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(HttpDispatcherHandler::class.java)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is HttpRequest -> handleHttpRequest(ctx, msg)
            else -> handleUnexpectedMessage(ctx, msg)
        }
    }

    private fun handleHttpRequest(ctx: ChannelHandlerContext, msg: HttpRequest) {
        when (msg.method()) {
            HttpMethod.CONNECT -> handleHttpConnectRequest(ctx, msg)
            HttpMethod.GET -> handleHttpWebRequest(ctx, msg)
            else -> handleUnexpectedHttpMessage(ctx, msg)
        }
    }

    private fun handleHttpConnectRequest(ctx: ChannelHandlerContext, msg: HttpRequest) {
        ctx.channel().pipeline().addAfter(ctx.name(), null, HttpProxyServerHandler())
        ctx.channel().pipeline().remove(this)
        ctx.fireChannelRead(msg)
    }

    private fun handleHttpWebRequest(ctx: ChannelHandlerContext, msg: HttpRequest) {
        ctx.channel().pipeline().addAfter(ctx.name(), null, HttpWebServerHandler())
        ctx.channel().pipeline().addAfter(ctx.name(), null, HttpObjectAggregator(65536, true))
        ctx.channel().pipeline().remove(this)
        ctx.fireChannelRead(msg)
    }

    private fun handleUnexpectedHttpMessage(ctx: ChannelHandlerContext, msg: HttpRequest) {
        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        ctx.channel().writeAndFlush(response)
        handleUnexpectedMessage(ctx, msg)
    }

    private fun handleUnexpectedMessage(ctx: ChannelHandlerContext, msg: Any) {
        logger.error("${ctx.channel()} Unexpected message: $msg")
        ReferenceCountUtil.release(msg)
        ctx.close()
    }
}
