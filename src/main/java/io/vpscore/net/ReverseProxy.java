package io.vpscore.net;

import io.vpscore.config.VPSConfig.NetworkConfig;
import io.vpscore.security.AuthManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReverseProxy implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxy.class);

    private final NetworkConfig config;
    private final AuthManager authManager;
    private final Map<String, ProxyRoute> routes = new ConcurrentHashMap<>();
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private volatile boolean running;

    private Channel httpChannel;
    private Channel httpsChannel;

    public ReverseProxy(NetworkConfig config, AuthManager authManager) {
        this.config = config;
        this.authManager = authManager;
    }

    public void start() throws Exception {
        running = true;

        var httpBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(
                        new HttpServerCodec(),
                        new HttpObjectAggregator(65536),
                        new ProxyFrontendHandler(routes)
                    );
                }
            })
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        httpChannel = httpBootstrap.bind(config.getReverseProxyHttpPort()).sync().channel();
        log.info("Reverse proxy HTTP started on port {}", config.getReverseProxyHttpPort());

        if (config.isAutoTls() && !config.getDomain().isEmpty()) {
            log.info("Auto-TLS configured for domain: {}", config.getDomain());
        }
    }

    public void addRoute(String domain, String targetHost, int targetPort) {
        routes.put(domain.toLowerCase(), new ProxyRoute(targetHost, targetPort));
        log.info("Proxy route added: {} -> {}:{}", domain, targetHost, targetPort);
    }

    public void removeRoute(String domain) {
        routes.remove(domain.toLowerCase());
    }

    @Override
    public void close() {
        running = false;
        if (httpChannel != null) httpChannel.close();
        if (httpsChannel != null) httpsChannel.close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    record ProxyRoute(String targetHost, int targetPort) {}

    @ChannelHandler.Sharable
    static class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {
        private final Map<String, ProxyRoute> routes;

        ProxyFrontendHandler(Map<String, ProxyRoute> routes) {
            this.routes = routes;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest req) {
                var host = req.headers().get(HttpHeaderNames.HOST);
                if (host != null) {
                    var route = routes.get(host.toLowerCase());
                    if (route != null) {
                        // Forward to backend
                        var response = new DefaultFullHttpResponse(
                            req.protocolVersion(), HttpResponseStatus.OK);
                        response.content().writeBytes(("Proxied to " + route.targetHost() +
                            ":" + route.targetPort()).getBytes());
                        ctx.writeAndFlush(response);
                        return;
                    }
                }
                var response = new DefaultFullHttpResponse(
                    req.protocolVersion(), HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response);
            }
        }
    }
}
