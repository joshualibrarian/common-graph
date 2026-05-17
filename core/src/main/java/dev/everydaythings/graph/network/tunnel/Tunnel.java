package dev.everydaythings.graph.network.tunnel;

import dev.everydaythings.graph.identity.MultiKey;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A byte channel between two parties, beneath Parley.
 *
 * <p>A Tunnel as exposed to Parley is <b>already handshaken</b> — bytes can flow
 * immediately. Each implementation handles its own handshake before producing
 * the Tunnel; Parley never sees pre-handshake state.
 *
 * <p>What Parley needs from a tunnel: ordered bytes between two endpoints, with
 * declared properties about confidentiality, authentication, and the
 * counterparty's verified identity. The tunnel layer doesn't frame; framing is
 * the codec's job. The tunnel layer doesn't dispatch; that's Parley's job. The
 * tunnel layer is purely the byte channel + a few properties about its
 * security guarantees.
 *
 * <h2>Implementations across the spectrum</h2>
 *
 * <table>
 *   <tr><th>Tunnel</th><th>Confidential</th><th>Authenticated</th><th>Counterparty</th></tr>
 *   <tr><td>{@link LoopbackTunnel}</td><td>no</td><td>no</td><td>empty</td></tr>
 *   <tr><td>{@code PlaintextTunnel}</td><td>no</td><td>no</td><td>empty</td></tr>
 *   <tr><td>{@link dev.everydaythings.graph.network.parley.NoiseTunnel}</td><td>yes</td><td>yes</td><td>static key</td></tr>
 *   <tr><td>{@code TLSTunnel}</td><td>yes</td><td>yes</td><td>cert public key</td></tr>
 *   <tr><td>{@code ReticulumTunnel}</td><td>yes</td><td>yes</td><td>Reticulum identity</td></tr>
 * </table>
 *
 * <p>Tunnels expose their security guarantees through {@link #isConfidential}
 * and {@link #isAuthenticated}. Parley's trust matrix decides whether to accept
 * the connection given those properties — e.g., a strict policy refuses
 * non-authenticated tunnels; a permissive same-host policy may accept a
 * plaintext Unix socket (where filesystem permissions provide trust).
 *
 * <h2>Push receive model</h2>
 *
 * <p>Bytes from the counterparty arrive via the consumer registered with
 * {@link #onReceive}. The tunnel decides chunk granularity — there's no
 * guarantee that one logical "message" arrives in one callback. The codec
 * above the tunnel buffers and frames as needed.
 *
 * <p>Calling {@link #onReceive} replaces the prior consumer. Passing
 * {@code null} unregisters. A tunnel without a registered consumer drops
 * arriving bytes (or buffers them, implementation choice).
 *
 * <h2>What this interface deliberately doesn't expose</h2>
 *
 * <ul>
 *   <li><b>Handshake</b> — already done by the time the Tunnel is constructed.</li>
 *   <li><b>Framing</b> — codec's concern.</li>
 *   <li><b>Reconnection</b> — higher-level connection management on top of Parley.</li>
 *   <li><b>Flow control</b> — underlying transport handles it.</li>
 *   <li><b>Multiplexing</b> — one Tunnel = one logical channel; multiplexing
 *       is achieved by opening multiple tunnels if needed.</li>
 * </ul>
 */
public interface Tunnel extends AutoCloseable {

    // ==================================================================================
    // Byte I/O
    // ==================================================================================

    /**
     * Send bytes to the counterparty. The returned future completes when the
     * bytes have been handed off to the underlying transport (not necessarily
     * when the counterparty has received them).
     *
     * <p>Bytes sent through this method may arrive at the counterparty in
     * different chunk sizes than they were sent; the tunnel doesn't preserve
     * caller-visible chunk boundaries. Application-layer framing (length
     * prefixes, terminator bytes, codec-native framing) is the caller's
     * responsibility.
     *
     * @throws IllegalStateException if the tunnel is closed
     */
    CompletableFuture<Void> send(byte[] bytes);

    /**
     * Register a consumer to receive bytes arriving from the counterparty.
     * Replaces any prior consumer.
     *
     * <p>The consumer is invoked on whatever thread the underlying transport
     * delivers bytes on; implementations should be brief and offload heavy
     * work. The consumer may be invoked many times with small chunks, or once
     * with a large chunk — the tunnel makes no guarantees about chunk size.
     *
     * <p>Passing {@code null} unregisters the consumer. A tunnel with no
     * registered consumer drops or buffers arriving bytes (implementation
     * defined).
     */
    void onReceive(Consumer<byte[]> consumer);

    // ==================================================================================
    // Security properties (declared by the implementation, true for the
    // tunnel's whole lifetime)
    // ==================================================================================

    /**
     * The counterparty's verified public key, if the tunnel layer authenticated
     * one.
     *
     * <p>Present for tunnels that perform identity verification during their
     * handshake (Noise XX, TLS with cert verification, Reticulum). Empty for
     * tunnels that don't verify identity ({@link LoopbackTunnel}, raw
     * plaintext over an untrusted transport).
     *
     * <p>Parley compares this key against the IID claimed in the HELLO frame
     * to decide whether the counterparty is who they say they are. Tunnels
     * that return empty here can still carry Parley traffic, but the trust
     * matrix may refuse to honor the connection's claimed identity.
     */
    Optional<MultiKey> counterparty();

    /**
     * Whether bytes are encrypted in transit on this tunnel.
     *
     * <p>{@code true} for tunnels that perform end-to-end encryption (Noise,
     * TLS, Reticulum). {@code false} for plaintext tunnels (Loopback,
     * explicit-opt-in plaintext over trusted transports like filesystem-permission-gated
     * Unix sockets).
     *
     * <p>This is the tunnel's declared property, not a runtime check.
     * Implementations that perform encryption return {@code true} unconditionally;
     * implementations that don't return {@code false} unconditionally.
     */
    boolean isConfidential();

    /**
     * Whether the counterparty's identity has been cryptographically verified.
     *
     * <p>{@code true} when {@link #counterparty} is present and was established
     * through a cryptographic handshake. {@code false} for tunnels that don't
     * verify identity, even if they're encrypted (e.g., a hypothetical TLS
     * tunnel without certificate verification, or a Noise-NN handshake
     * without static keys).
     */
    boolean isAuthenticated();

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /**
     * Whether the tunnel can carry bytes. Returns {@code false} after
     * {@link #close} has been called or after the underlying transport has
     * failed.
     */
    boolean isOpen();

    /**
     * Close the tunnel. Idempotent. After close, {@link #send} throws and any
     * registered {@link #onReceive} consumer will not be invoked further.
     * Underlying transport resources are released.
     */
    @Override
    void close();
}
