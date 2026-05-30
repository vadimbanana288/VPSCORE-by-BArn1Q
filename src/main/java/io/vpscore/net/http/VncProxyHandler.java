package io.vpscore.net.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class VncProxyHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(VncProxyHandler.class);

    private final String vncHost;
    private final int vncPort;
    private Channel tcpChannel;
    private ChannelHandlerContext wsCtx;
    private NioEventLoopGroup vncGroup;
    private boolean connecting;

    public VncProxyHandler(String vncHost, int vncPort) {
        this.vncHost = vncHost;
        this.vncPort = vncPort;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.wsCtx = ctx;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof HandshakeComplete) {
            log.info("WebSocket handshake complete, connecting VNC {}:{}", vncHost, vncPort);
            connectToVnc(ctx);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeAll();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof BinaryWebSocketFrame) {
            if (tcpChannel != null && tcpChannel.isActive()) {
                tcpChannel.writeAndFlush(frame.content().retain());
            }
        } else if (frame instanceof CloseWebSocketFrame) {
            closeAll();
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
    }

    private void connectToVnc(ChannelHandlerContext ctx) {
        if (connecting) return;
        connecting = true;
        if (vncGroup == null || vncGroup.isShuttingDown() || vncGroup.isShutdown()) {
            vncGroup = new NioEventLoopGroup(1);
        }
        var b = new Bootstrap()
            .group(vncGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx0, Object msg) {
                            if (msg instanceof ByteBuf buf && wsCtx != null && wsCtx.channel().isActive()) {
                                wsCtx.writeAndFlush(new BinaryWebSocketFrame(buf.retain()));
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx0) {
                            if (wsCtx != null && wsCtx.channel().isActive()) {
                                wsCtx.writeAndFlush(new CloseWebSocketFrame(1000, "VNC disconnected"));
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx0, Throwable cause) {
                            log.warn("VNC TCP error", cause);
                            ctx0.close();
                        }
                    });
                }
            });

        b.connect(vncHost, vncPort).addListener((ChannelFutureListener) future -> {
            connecting = false;
            if (future.isSuccess()) {
                tcpChannel = future.channel();
                log.info("VNC proxy connected to {}:{}", vncHost, vncPort);
            } else {
                log.warn("VNC proxy connect failed to {}:{} - {}", vncHost, vncPort, future.cause().getMessage());
                if (wsCtx != null && wsCtx.channel().isActive()) {
                    wsCtx.executor().schedule(() -> connectToVnc(ctx), 2, TimeUnit.SECONDS);
                }
            }
        });
    }

    private void closeAll() {
        if (tcpChannel != null) {
            tcpChannel.close();
            tcpChannel = null;
        }
        if (vncGroup != null && !vncGroup.isShuttingDown()) {
            vncGroup.shutdownGracefully();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("VNC WS error", cause);
        closeAll();
        ctx.close();
    }
}
