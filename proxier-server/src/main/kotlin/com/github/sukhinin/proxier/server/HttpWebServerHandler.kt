package com.github.sukhinin.proxier.server

import com.github.sukhinin.proxier.authc.ClientAuthenticationConfigSerializer
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.slf4j.LoggerFactory
import java.net.URI

class HttpWebServerHandler : SimpleChannelInboundHandler<FullHttpRequest>() {

    private val logger = LoggerFactory.getLogger(HttpWebServerHandler::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        if (msg.decoderResult().isFailure) {
            handleDecoderFailure(ctx, msg)
            return
        }

        val uri = URI(msg.uri())
        val response = when {
            msg.method() == HttpMethod.GET && uri.path == "/auth/config" -> handleAuthConfigRequest(ctx)
            else -> DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
        }

        when (HttpUtil.isKeepAlive(msg)) {
            true -> respondWithKeepAlive(ctx, response)
            else -> respondWithClose(ctx, response)
        }
    }

    private fun handleAuthConfigRequest(ctx: ChannelHandlerContext): FullHttpResponse {
        val auth = ctx.channel().attr(Attributes.authenticationService).get()
        val config = auth.getClientConfig().let(ClientAuthenticationConfigSerializer::serialize)
        val dataBuf = Unpooled.copiedBuffer(config, CharsetUtil.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, dataBuf)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, ClientAuthenticationConfigSerializer.MIME_TYPE)
        return response
    }

    private fun respondWithKeepAlive(ctx: ChannelHandlerContext, response: FullHttpResponse) {
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
        ctx.writeAndFlush(response)
    }

    private fun respondWithClose(ctx: ChannelHandlerContext, response: FullHttpResponse) {
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun handleDecoderFailure(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        logger.error("${ctx.channel()} Decoder failure: $msg")
        respondWithClose(ctx, DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
    }
}
