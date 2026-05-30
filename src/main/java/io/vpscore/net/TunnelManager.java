package io.vpscore.net;

import io.vpscore.config.VPSConfig.NetworkConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TunnelManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TunnelManager.class);

    private final NetworkConfig config;
    private final Map<Integer, Channel> tunnels = new ConcurrentHashMap<>();
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private volatile boolean running;

    public TunnelManager(NetworkConfig config) {
        this.config = config;
    }

    public void start() {
        running = true;
        log.info("Tunnel manager started (ports: {}-{})", config.getProxyStartPort(), config.getProxyEndPort());
    }

    public int createTunnel(int localPort, String remoteHost, int remotePort) throws Exception {
        if (localPort <= 0) {
            localPort = findAvailablePort();
        }

        var b = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new TunnelBackendHandler(remoteHost, remotePort, workerGroup));
                }
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        var future = b.bind(localPort).sync();
        tunnels.put(localPort, future.channel());
        log.info("Tunnel created: localhost:{} -> {}:{}", localPort, remoteHost, remotePort);
        return localPort;
    }

    public void closeTunnel(int port) {
        var ch = tunnels.remove(port);
        if (ch != null) {
            ch.close();
            log.info("Tunnel closed: port {}", port);
        }
    }

    private int findAvailablePort() {
        for (int port = config.getProxyStartPort(); port <= config.getProxyEndPort(); port++) {
            if (!tunnels.containsKey(port)) return port;
        }
        throw new IllegalStateException("No available ports in range " +
            config.getProxyStartPort() + "-" + config.getProxyEndPort());
    }

    @Override
    public void close() {
        running = false;
        tunnels.values().forEach(Channel::close);
        tunnels.clear();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @ChannelHandler.Sharable
    static class TunnelBackendHandler extends ChannelInboundHandlerAdapter {
        private final String remoteHost;
        private final int remotePort;
        private final NioEventLoopGroup workerGroup;
        private Channel outboundChannel;

        TunnelBackendHandler(String remoteHost, int remotePort, NioEventLoopGroup workerGroup) {
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
            this.workerGroup = workerGroup;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            var b = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new TunnelRelayHandler(ctx.channel()));
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true);

            var future = b.connect(remoteHost, remotePort);
            outboundChannel = future.channel();
            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    ctx.close();
                }
            });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (outboundChannel != null && outboundChannel.isActive()) {
                outboundChannel.writeAndFlush(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (outboundChannel != null) outboundChannel.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("Tunnel error", cause);
            ctx.close();
        }
    }

    static class TunnelRelayHandler extends ChannelInboundHandlerAdapter {
        private final Channel inboundChannel;

        TunnelRelayHandler(Channel inboundChannel) {
            this.inboundChannel = inboundChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            inboundChannel.writeAndFlush(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            inboundChannel.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("Tunnel relay error", cause);
            ctx.close();
        }
    }
}
