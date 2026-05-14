package dev.everydaythings.graph.network.parley;

import lombok.extern.log4j.Log4j2;

/**
 * Noise-XX-based {@link Tunnel} between two parties holding static X25519
 * keypairs.
 *
 * <h2>Handshake (XX pattern)</h2>
 * <pre>
 *   →  e
 *   ←  e, ee, s, es
 *   →  s, se
 * </pre>
 * Each party contributes an ephemeral key and their static key; after the
 * three messages both sides have authenticated each other and derived a
 * symmetric session key. Subsequent payloads are AEAD-encrypted.
 *
 * <p>Tunnel establishment happens <em>before</em> Parley's codec handshake —
 * point-and-grunt runs across an already-secure channel.
 *
 * <h2>Migration note</h2>
 * The existing Netty-pipeline implementation lives at
 * {@code network/transport/TransportEncryptionHandler} and
 * {@code network/transport/TransportCrypto}. When Parley is wired in, that
 * logic adapts to provide a {@code byte[] → byte[]} duplex matching this
 * interface, instead of plugging directly into the Netty pipeline.
 *
 * <p>STUB.
 */
@Log4j2
public final class NoiseTunnel implements Tunnel {

    // TODO: Noise XX state machine (initiator + responder)
    // TODO: static keypair source (Vault for signing-key-derived X25519, or
    //       dedicated transport keypair)
    // TODO: AEAD send/receive (ChaCha20-Poly1305)
    // TODO: rekeying policy
    // TODO: adapter to Netty pipeline (for RemoteConnection) OR pure-byte
    //       duplex (for testing / non-Netty transports)

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() {
    }
}
