package com.github.sukhinin.proxier.client

import com.github.sukhinin.proxier.authc.ClientAuthenticationConfig
import com.github.sukhinin.proxier.authc.ClientAuthenticationConfigSerializer
import com.github.sukhinin.proxier.client.authc.AuthenticationService
import com.github.sukhinin.proxier.utils.HostAndPort
import com.github.sukhinin.simpleconfig.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.undertow.Handlers
import io.undertow.Undertow
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParserException
import net.sourceforge.argparse4j.inf.Namespace
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

object Application {

    private val logger = LoggerFactory.getLogger(Application::class.java)

    init {
        System.setProperty("org.jboss.logging.provider", "slf4j")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val ns = parseCommandLineArgs(args)
        val config = getApplicationConfig(ns)

        val serverAddress = HostAndPort.parse(config.get("server.address"))

        val authenticationConfig = resolveClientAuthenticationConfig(serverAddress.toString())
        logger.info("Retrieved authentication configuration:\n\t" + authenticationConfig.dump().replace("\n", "\n\t"))

        val authenticationService = AuthenticationService(authenticationConfig, "/auth/callback")

        Undertow.builder()
            .addHttpListener(config.getInteger("web.admin.port"), "localhost")
            .setHandler(
                Handlers.routing()
                    .get("/auth", authenticationService.authenticationRequestHandler())
                    .get("/auth/callback", authenticationService.authenticationCallbackHandler())
            )
            .build().start()

        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(Socks5ProxyServerInitializer())
            bootstrap
                .childAttr(Attributes.serverAddress, serverAddress)
                .childAttr(Attributes.clientSslContext, getClientSslContext(config))
            bootstrap
                .bind(config.getInteger("proxy.socks.port")).sync()
                .channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    private fun resolveClientAuthenticationConfig(serverAddress: String): ClientAuthenticationConfig {
        val uri = URI.create("https://$serverAddress/auth/config")
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val request = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(5)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw IOException("Bad status code: ${response.statusCode()}")
        }

        return ClientAuthenticationConfigSerializer.deserialize(response.body())
    }

    private fun getClientSslContext(config: Config): SslContext {
        val builder = SslContextBuilder.forClient()
        if (config.contains("server.ssl.ca")) {
            builder.trustManager(File(config.get("server.ssl.ca")))
        }
        return builder.build()
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun parseCommandLineArgs(args: Array<String>): Namespace {
        val parser = ArgumentParsers.newFor("proxier-client").build()
        parser.addArgument("-c", "--config").metavar("PATH")
            .help("path to configuration file")

        parser.addArgument("--server.address").metavar("ADDRESS")
            .type(String::class.java)
            .help("access server host and port")
        parser.addArgument("--server.ssl.ca").metavar("PATH")
            .type(String::class.java)
            .help("trusted CA certificate file")

        parser.addArgument("--proxy.socks.port").metavar("PORT")
            .type(Integer::class.java)
            .help("run SOCKS5 proxy on this port")

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
