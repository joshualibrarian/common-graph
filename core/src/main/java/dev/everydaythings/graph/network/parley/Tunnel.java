package dev.everydaythings.graph.network.parley;

/**
 * A secure byte channel between two parties, beneath Parley.
 *
 * <p>Tunnels provide confidentiality and authenticity at the byte layer.
 * Parley's codec point-and-grunt speaks plaintext across an already-established
 * tunnel; Parley itself is tunnel-agnostic.
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link NoiseTunnel} — Noise XX handshake (mutual auth via static
 *       X25519 keypairs + ephemeral keys + AEAD session). The CG-native
 *       option.</li>
 *   <li>(TLS tunnel — TBD, for bridging into ecosystems that prefer it.)</li>
 *   <li>{@link LocalConnection} skips the tunnel entirely — no encryption
 *       inside a single JVM.</li>
 * </ul>
 *
 * <p>STUB.
 */
public interface Tunnel extends AutoCloseable {

    /** True if the tunnel is open and bytes can flow. */
    boolean isOpen();

    @Override
    void close();
}
