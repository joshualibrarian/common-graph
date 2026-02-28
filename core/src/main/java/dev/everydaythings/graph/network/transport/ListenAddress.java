package dev.everydaythings.graph.network.transport;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Address specification for a network listener.
 *
 * <p>Can be either:
 * <ul>
 *   <li>{@link Unix} - Unix domain socket at a filesystem path</li>
 *   <li>{@link Tcp} - TCP socket on host:port</li>
 * </ul>
 */
public sealed interface ListenAddress {

    TransportType type();

    /**
     * Unix domain socket address.
     *
     * @param path Path to the socket file (e.g., /home/user/.librarian/graph.sock)
     */
    record Unix(Path path) implements ListenAddress {
        public Unix {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public TransportType type() {
            return TransportType.UNIX_SOCKET;
        }

        @Override
        public String toString() {
            return "unix:" + path;
        }
    }

    /**
     * TCP socket address.
     *
     * @param host Bind address (null or "0.0.0.0" for all interfaces)
     * @param port Port number (0 for auto-assign)
     * @param tls  Whether to use TLS
     */
    record Tcp(String host, int port, boolean tls) implements ListenAddress {
        public Tcp {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid port: " + port);
            }
        }

        /**
         * TCP on all interfaces, no TLS.
         */
        public static Tcp onPort(int port) {
            return new Tcp(null, port, false);
        }

        /**
         * TCP on all interfaces with TLS.
         */
        public static Tcp onPortTls(int port) {
            return new Tcp(null, port, true);
        }

        /**
         * TCP on localhost only, no TLS.
         */
        public static Tcp localhost(int port) {
            return new Tcp("127.0.0.1", port, false);
        }

        @Override
        public TransportType type() {
            return TransportType.TCP;
        }

        public InetSocketAddress toInetAddress() {
            return host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
        }

        @Override
        public String toString() {
            String scheme = tls ? "tls" : "tcp";
            String h = host == null ? "*" : host;
            return scheme + "://" + h + ":" + port;
        }
    }

    /**
     * Parse a listen address from a string.
     *
     * <p>Formats:
     * <ul>
     *   <li>{@code unix:/path/to/socket}</li>
     *   <li>{@code tcp://host:port}</li>
     *   <li>{@code tls://host:port}</li>
     *   <li>{@code :port} (TCP on all interfaces)</li>
     * </ul>
     */
    static ListenAddress parse(String spec) {
        if (spec.startsWith("unix:")) {
            return new Unix(Path.of(spec.substring(5)));
        }
        if (spec.startsWith("tcp://")) {
            return parseTcp(spec.substring(6), false);
        }
        if (spec.startsWith("tls://")) {
            return parseTcp(spec.substring(6), true);
        }
        if (spec.startsWith(":")) {
            return Tcp.onPort(Integer.parseInt(spec.substring(1)));
        }
        throw new IllegalArgumentException("Invalid listen address: " + spec);
    }

    private static Tcp parseTcp(String hostPort, boolean tls) {
        int colon = hostPort.lastIndexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Missing port in: " + hostPort);
        }
        String host = hostPort.substring(0, colon);
        int port = Integer.parseInt(hostPort.substring(colon + 1));
        if (host.equals("*") || host.isEmpty()) {
            host = null;
        }
        return new Tcp(host, port, tls);
    }
}
