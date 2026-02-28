package dev.everydaythings.graph.network.transport;

/**
 * Types of network transport supported.
 */
public enum TransportType {
    /**
     * Unix domain socket - local IPC only.
     *
     * <p>Fast, secure (filesystem permissions), no network stack overhead.
     * Socket file lives in the library's working tree.
     */
    UNIX_SOCKET,

    /**
     * TCP socket - local or remote connections.
     *
     * <p>Supports TLS for encrypted, authenticated connections.
     * Required for cross-machine communication.
     */
    TCP
}
