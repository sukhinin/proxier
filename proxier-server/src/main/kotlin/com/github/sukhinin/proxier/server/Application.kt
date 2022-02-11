package com.github.sukhinin.proxier.server

import com.github.sukhinin.simpleconfig.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.Namespace
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

object Application {

    private const val DEFAULT_SERVER_PORT = 3128

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
                .bind(config.getInteger("server.port")).sync()
                .channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun parseCommandLineArgs(args: Array<String>): Namespace {
        val parser = ArgumentParsers.newFor("proxier-server").build()
        parser.addArgument("-c", "--config")
            .metavar("PATH")
            .help("path to configuration file")

        parser.addArgument("--server.port").metavar("PORT")
            .type(Integer::class.java)
            .setDefault(DEFAULT_SERVER_PORT)
            .help("accept incoming connections on this port")

        return try {
            parser.parseArgs(args)
        } catch (e: ArgumentParserException) {
            parser.handleError(e)
            exitProcess(1)
        }
    }

    private fun getApplicationConfig(ns: Namespace): Config {
        val propertiesConfig = ns.getString("config")?.let(ConfigLoader::getConfigFromPath)
        val commandLineConfig = ns.attrs
            .filter { (k, v) -> k.contains('.') && v != null }
            .mapValues { (_, v) -> v.toString() }
            .let(::MapConfig)

        val config = commandLineConfig.withFallback(propertiesConfig ?: MapConfig(emptyMap())).resolved()
        logger.info("Loaded configuration:\n\t" + config.masked().dump().replace("\n", "\n\t"))

        return config
    }
}

