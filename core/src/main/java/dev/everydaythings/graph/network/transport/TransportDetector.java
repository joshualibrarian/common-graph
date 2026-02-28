package dev.everydaythings.graph.network.transport;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Detects the best available Netty transport at runtime.
 *
 * <p>Prefers native transports (epoll on Linux, kqueue on macOS) over NIO
 * for lower latency and reduced GC pressure. Falls back to NIO if native
 * transports aren't available.
 */
public final class TransportDetector {

    private static final Logger log = LogManager.getLogger(TransportDetector.class);

    private static final TransportType DETECTED = detect();

    private TransportDetector() {}

    private static TransportType detect() {
        try {
            Class<?> epoll = Class.forName("io.netty.channel.epoll.Epoll");
            if ((boolean) epoll.getMethod("isAvailable").invoke(null)) {
                log.info("Using epoll native transport");
                return TransportType.EPOLL;
            }
        } catch (Exception ignored) {}

        try {
            Class<?> kqueue = Class.forName("io.netty.channel.kqueue.KQueue");
            if ((boolean) kqueue.getMethod("isAvailable").invoke(null)) {
                log.info("Using kqueue native transport");
                return TransportType.KQUEUE;
            }
        } catch (Exception ignored) {}

        log.info("Using NIO transport");
        return TransportType.NIO;
    }

    public static EventLoopGroup newEventLoopGroup() {
        return newEventLoopGroup(0);
    }

    public static EventLoopGroup newEventLoopGroup(int nThreads) {
        return switch (DETECTED) {
            case EPOLL -> createEpollGroup(nThreads);
            case KQUEUE -> createKQueueGroup(nThreads);
            case NIO -> new NioEventLoopGroup(nThreads);
        };
    }

    public static Class<? extends ServerChannel> serverChannelClass() {
        return switch (DETECTED) {
            case EPOLL -> epollServerSocketChannel();
            case KQUEUE -> kqueueServerSocketChannel();
            case NIO -> NioServerSocketChannel.class;
        };
    }

    public static Class<? extends SocketChannel> socketChannelClass() {
        return switch (DETECTED) {
            case EPOLL -> epollSocketChannel();
            case KQUEUE -> kqueueSocketChannel();
            case NIO -> NioSocketChannel.class;
        };
    }

    public static TransportType detected() {
        return DETECTED;
    }

    // Reflective construction to avoid hard compile-time dependency on native jars

    @SuppressWarnings("unchecked")
    private static EventLoopGroup createEpollGroup(int nThreads) {
        try {
            Class<?> cls = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
            return (EventLoopGroup) cls.getConstructor(int.class).newInstance(nThreads);
        } catch (Exception e) {
            throw new RuntimeException("Epoll detected but failed to create event loop group", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static EventLoopGroup createKQueueGroup(int nThreads) {
        try {
            Class<?> cls = Class.forName("io.netty.channel.kqueue.KQueueEventLoopGroup");
            return (EventLoopGroup) cls.getConstructor(int.class).newInstance(nThreads);
        } catch (Exception e) {
            throw new RuntimeException("KQueue detected but failed to create event loop group", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends ServerChannel> epollServerSocketChannel() {
        try {
            return (Class<? extends ServerChannel>) Class.forName("io.netty.channel.epoll.EpollServerSocketChannel");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends SocketChannel> epollSocketChannel() {
        try {
            return (Class<? extends SocketChannel>) Class.forName("io.netty.channel.epoll.EpollSocketChannel");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends ServerChannel> kqueueServerSocketChannel() {
        try {
            return (Class<? extends ServerChannel>) Class.forName("io.netty.channel.kqueue.KQueueServerSocketChannel");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends SocketChannel> kqueueSocketChannel() {
        try {
            return (Class<? extends SocketChannel>) Class.forName("io.netty.channel.kqueue.KQueueSocketChannel");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private enum TransportType {
        EPOLL, KQUEUE, NIO
    }
}
