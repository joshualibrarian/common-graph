package dev.everydaythings.graph.bridges.tcp;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.IpAddress;
import dev.everydaythings.graph.network.NetworkVocabulary;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.TcpEndpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * TCP {@link Transport} implementation backed by Netty NIO event loops.
 * Lives in {@code :bridges:tcp} so the rest of the codebase (and apps that
 * only need a different transport) don't pay for Netty transitively.
 *
 * <h2>Threading model</h2>
 *
 * <p>One boss group (single-threaded accept loop) and one worker group
 * (default Netty sizing — typically 2× CPU count) are created when the
 * transport is constructed.  Both are shared across every connect / listen
 * call on this instance.  Connections are inherently asynchronous: bytes
 * arriving from a peer are delivered on a worker thread.  The
 * {@link Tunnel#onReceive Tunnel} contract requires callers to keep
 * receive consumers brief and offload heavy work themselves.
 *
 * <p>{@link #close} shuts both groups down gracefully — call it on
 * application exit to free Netty's threads.
 *
 * <h2>Security</h2>
 *
 * <p>Produces plaintext, unauthenticated tunnels.  Layer Noise or TLS on
 * top by wrapping the returned {@link Tunnel}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #listen} binds a {@link io.netty.channel.socket.nio.NioServerSocketChannel
 * NioServerSocketChannel} synchronously and returns a {@link Listener}
 * whose {@link Listener#actualEndpoint} reports the address actually bound
 * — relevant when the caller passed port 0 (ephemeral).  Closing the
 * Listener closes the server channel; in-flight {@link Tunnel}s already
 * handed to the accept handler are unaffected.
 *
 * <p>{@link #connect} returns a future that completes when the TCP
 * three-way handshake is done; the {@link Tunnel} is then ready for bytes.
 */
@Log4j2
public final class TcpTransport implements Transport, AutoCloseable {

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public TcpTransport() {
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Tcp.KEY);
    }

    // ==================================================================================
    // Connect
    // ==================================================================================

    @Override
    public CompletableFuture<Tunnel> connect(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!(endpoint instanceof TcpEndpoint tcp)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "TcpTransport.connect requires a TcpEndpoint, got "
                            + endpoint.getClass().getName()));
        }
        CompletableFuture<Tunnel> future = new CompletableFuture<>();
        Bootstrap bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // Pipeline is empty; TcpTunnel installs its inbound handler
                        // on construction.  Doing it post-init means a tunnel
                        // wrapping the same channel always sees the same handler.
                    }
                });
        ChannelFuture cf = bootstrap.connect(
                new InetSocketAddress(tcp.host().toInetAddress(), tcp.port()));
        cf.addListener(f -> {
            if (f.isSuccess()) {
                future.complete(new TcpTunnel(cf.channel()));
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    // ==================================================================================
    // Listen
    // ==================================================================================

    @Override
    public Listener listen(Endpoint endpoint, Consumer<Tunnel> onAccept) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(onAccept, "onAccept");
        if (!(endpoint instanceof TcpEndpoint tcp)) {
            throw new IllegalArgumentException(
                    "TcpTransport.listen requires a TcpEndpoint, got "
                            + endpoint.getClass().getName());
        }
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // Construct the tunnel BEFORE handing the child channel
                        // to userland — its inbound handler is registered
                        // before any read can fire, and early bytes (TCP
                        // ACK-then-immediate-write from the peer) are buffered
                        // until onReceive is wired up.
                        Tunnel tunnel = new TcpTunnel(ch);
                        try {
                            onAccept.accept(tunnel);
                        } catch (RuntimeException e) {
                            logger.warn("Accept handler threw; closing channel", e);
                            ch.close();
                        }
                    }
                });
        Channel serverChannel;
        try {
            serverChannel = bootstrap.bind(
                    new InetSocketAddress(tcp.host().toInetAddress(), tcp.port()))
                    .sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("TcpTransport.listen interrupted", e);
        }
        InetSocketAddress local = (InetSocketAddress) serverChannel.localAddress();
        TcpEndpoint actual = TcpEndpoint.of(
                IpAddress.fromInetAddress(local.getAddress()),
                local.getPort());
        return new TcpListener(serverChannel, actual);
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /** Shut both Netty event loops down. Idempotent. */
    @Override
    public void close() {
        try {
            bossGroup.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
            workerGroup.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class TcpListener implements Listener {
        private final Channel serverChannel;
        private final TcpEndpoint actualEndpoint;

        TcpListener(Channel serverChannel, TcpEndpoint actualEndpoint) {
            this.serverChannel = serverChannel;
            this.actualEndpoint = actualEndpoint;
        }

        @Override
        public Endpoint actualEndpoint() {
            return actualEndpoint;
        }

        @Override
        public void close() {
            serverChannel.close();
        }
    }
}
