package com.github.sukhinin.proxier.server

import com.github.sukhinin.simpleconfig.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.Namespace
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

object Application {

    private val logger = LoggerFactory.getLogger(Application::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val ns = parseCommandLineArgs(args)
        val config = getApplicationConfig(ns)

        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .handler(LoggingHandler(LogLevel.DEBUG))
                .childHandler(HttpProxyServerInitializer())
            bootstrap
                .childAttr(Attributes.serverSslContext, getServerSslContext(config))
            bootstrap
                .bind(config.getInteger("server.port")).sync()
                .channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    private fun getServerSslContext(config: Config): SslContext? {
        if (!config.contains("server.ssl.cert") && !config.contains("server.ssl.key")) {
            return null
        }

        val certFile = File(config.get("server.ssl.cert"))
        val keyFile = File(config.get("server.ssl.key"))

        val builder = when (config.contains("server.ssl.password")) {
            true -> SslContextBuilder.forServer(certFile, keyFile, config.get("server.ssl.password"))
            else -> SslContextBuilder.forServer(certFile, keyFile)
        }

        return builder.build()
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun parseCommandLineArgs(args: Array<String>): Namespace {
        val parser = ArgumentParsers.newFor("proxier-server").build()
        parser.addArgument("-c", "--config")
            .metavar("PATH")
            .required(true)
            .help("path to configuration file")

        return try {
            parser.parseArgs(args)
        } catch (e: ArgumentParserException) {
            parser.handleError(e)
            exitProcess(1)
        }
    }

    private fun getApplicationConfig(ns: Namespace): Config {
        val config = ConfigLoader.getConfigFromPath(ns.getString("config"))
        logger.info("Loaded configuration:\n\t" + config.masked().dump().replace("\n", "\n\t"))
        return config
    }
}

