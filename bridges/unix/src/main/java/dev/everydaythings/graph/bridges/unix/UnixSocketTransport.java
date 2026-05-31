package dev.everydaythings.graph.bridges.unix;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.network.NetworkVocabulary;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.network.Endpoint;
import dev.everydaythings.graph.network.UnixEndpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import lombok.extern.log4j.Log4j2;

import java.net.UnixDomainSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Unix domain socket {@link Transport} implementation backed by Netty's
 * pure-NIO domain channel types ({@link NioDomainSocketChannel} /
 * {@link NioServerDomainSocketChannel}).  Lives in {@code :bridges:unix}.
 *
 * <p>Uses JDK 16+ {@link UnixDomainSocketAddress} under the hood — no
 * platform-specific native libraries.  Works on every platform where the
 * JDK supports Unix domain sockets (Linux, macOS, BSDs, Windows 10+).
 *
 * <h2>Threading model</h2>
 *
 * <p>One boss group (single-threaded accept loop) and one worker group
 * (default Netty sizing) are created at construction and shared across
 * every connect/listen call.  {@link #close} shuts both groups down
 * gracefully.
 *
 * <h2>Security</h2>
 *
 * <p>Produces plaintext, unauthenticated tunnels.  Local peers are
 * implicitly authenticated by filesystem permissions on the socket path
 * (a peer can only connect if it has read+write on the socket file).
 * Confidentiality / cryptographic peer auth is added by wrapping in a
 * security layer if needed.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #listen} unlinks any stale socket file before binding
 * (mutual-exclusion above this — only one librarian per data dir
 * via {@code LibrarianPresence}'s flock — means no two processes
 * compete for the same socket path).  The returned {@link Listener}'s
 * {@link Listener#actualEndpoint} reports the path actually bound.
 */
@Log4j2
public final class UnixSocketTransport implements Transport, AutoCloseable {

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public UnixSocketTransport() {
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Unix.KEY);
    }

    // ==================================================================================
    // Connect
    // ==================================================================================

    @Override
    public CompletableFuture<Tunnel> connect(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!(endpoint instanceof UnixEndpoint unix)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "UnixSocketTransport.connect requires a UnixEndpoint, got "
                            + endpoint.getClass().getName()));
        }
        CompletableFuture<Tunnel> future = new CompletableFuture<>();
        Bootstrap bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioDomainSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // Pipeline empty; UnixSocketTunnel installs its inbound
                        // handler on construction.
                    }
                });
        ChannelFuture cf = bootstrap.connect(
                UnixDomainSocketAddress.of(unix.path()));
        cf.addListener(f -> {
            if (f.isSuccess()) {
                future.complete(new UnixSocketTunnel(cf.channel()));
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
        if (!(endpoint instanceof UnixEndpoint unix)) {
            throw new IllegalArgumentException(
                    "UnixSocketTransport.listen requires a UnixEndpoint, got "
                            + endpoint.getClass().getName());
        }

        // Unlink any stale socket file at this path before bind.  Caller is
        // responsible for ensuring no other librarian is running here
        // (LibrarianPresence.acquire enforces this via flock).
        java.nio.file.Path socketPath = java.nio.file.Path.of(unix.path());
        try {
            java.nio.file.Files.deleteIfExists(socketPath);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(
                    "Failed to remove stale socket file " + socketPath, e);
        }

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerDomainSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        Tunnel tunnel = new UnixSocketTunnel(ch);
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
                    UnixDomainSocketAddress.of(unix.path()))
                    .sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("UnixSocketTransport.listen interrupted", e);
        }
        UnixEndpoint actual = UnixEndpoint.of(unix.path());
        return new UnixSocketListener(serverChannel, actual, socketPath);
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        try {
            bossGroup.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
            workerGroup.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================================================================================
    // Listener
    // ==================================================================================

    private static final class UnixSocketListener implements Listener {
        private final Channel serverChannel;
        private final UnixEndpoint actualEndpoint;
        private final java.nio.file.Path socketPath;

        UnixSocketListener(Channel serverChannel, UnixEndpoint actualEndpoint,
                           java.nio.file.Path socketPath) {
            this.serverChannel = serverChannel;
            this.actualEndpoint = actualEndpoint;
            this.socketPath = socketPath;
        }

        @Override
        public Endpoint actualEndpoint() {
            return actualEndpoint;
        }

        @Override
        public void close() {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Unlink the socket file so a future listener on the same path
            // doesn't see a stale entry.
            try {
                java.nio.file.Files.deleteIfExists(socketPath);
            } catch (java.io.IOException ignored) {}
        }
    }
}
