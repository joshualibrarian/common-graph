package dev.everydaythings.graph.web;

import dev.everydaythings.graph.network.transport.TransportDetector;
import dev.everydaythings.graph.runtime.Librarian;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * HTTP/WebSocket gateway for browser-based CG access.
 *
 * <p>Serves two purposes on a single port:
 * <ul>
 *   <li>WebSocket at {@code /ws} — carries CG session protocol (same CBOR messages
 *       as the TCP SessionServer, but framed as WebSocket binary frames)</li>
 *   <li>HTTP for everything else — serves the static JS web client from classpath
 *       resources</li>
 * </ul>
 *
 * <p>This is the on-ramp for web users. The experience is intentionally simpler
 * than the native client — browse, interact, get a taste, install the real thing.
 *
 * <p>The same HTTP server will host ActivityPub federation endpoints when that
 * bridge is built (WebFinger, Actor, Inbox/Outbox).
 */
@Log4j2
public class WebGateway implements AutoCloseable {

    private final Librarian librarian;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private InetSocketAddress boundAddress;

    public WebGateway(Librarian librarian) {
        this.librarian = librarian;
    }

    /**
     * Start the HTTP/WebSocket gateway.
     *
     * @param host bind address (e.g., "0.0.0.0" for all interfaces, "127.0.0.1" for local)
     * @param port port to listen on (0 for auto-assign)
     * @return the bound address
     */
    public InetSocketAddress start(String host, int port) throws IOException {
        if (serverChannel != null) {
            throw new IllegalStateException("Web gateway already running");
        }

        bossGroup = TransportDetector.newEventLoopGroup(1);
        workerGroup = TransportDetector.newEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(TransportDetector.serverChannelClass())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();

                            // HTTP codec
                            p.addLast("httpCodec", new HttpServerCodec());
                            p.addLast("httpAggregator", new HttpObjectAggregator(65536));

                            // Static file serving (handles non-WebSocket HTTP requests)
                            p.addLast("staticFiles", new StaticFileHandler());

                            // WebSocket upgrade at /ws
                            p.addLast("wsProtocol", new WebSocketServerProtocolHandler(
                                    "/ws", null, true, 65536));

                            // Session protocol over WebSocket
                            p.addLast("wsSession", new WebSocketSessionHandler(librarian));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            serverChannel = bootstrap.bind(host, port).syncUninterruptibly().channel();
            boundAddress = (InetSocketAddress) serverChannel.localAddress();

            logger.info("Web gateway listening on http://{}:{}", boundAddress.getHostString(), boundAddress.getPort());
            return boundAddress;
        } catch (Exception e) {
            close();
            throw new IOException("Failed to start web gateway", e);
        }
    }

    public InetSocketAddress boundAddress() {
        return boundAddress;
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isOpen();
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        boundAddress = null;
        logger.info("Web gateway stopped");
    }
}
