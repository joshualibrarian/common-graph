package dev.everydaythings.graph.bridges.noise;

import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.algorithm.Aead;
import dev.everydaythings.graph.identity.algorithm.KeyAgreement;
import dev.everydaythings.graph.identity.vault.Vault;
import dev.everydaythings.graph.network.tunnel.Tunnel;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Noise-XX-based {@link Tunnel} between two parties holding static X25519
 * keypairs.  Wraps any underlying {@link Tunnel}, adding confidentiality
 * and authenticated counterparty identification via the Noise protocol
 * framework.
 *
 * <p>The handshake runs over the underlying tunnel; each side calls
 * {@link #handshake} concurrently.  Once both futures complete, both
 * tunnels are ready to send/receive AEAD-encrypted bytes and both
 * {@link #counterparty} accessors return the peer's static X25519 key.
 *
 * <p>Static-key DH steps delegate to {@link Vault#agree}; the static private
 * key never leaves the vault.  See {@link NoiseHandshake} for the state
 * machine.
 */
public final class NoiseTunnel implements Tunnel {

    public enum Role { INITIATOR, RESPONDER }

    /**
     * Run the Noise XX handshake over {@code underlying} and produce a
     * Tunnel for AEAD-encrypted application traffic.  The returned future
     * completes when the three-message handshake finishes.
     */
    public static CompletableFuture<NoiseTunnel> handshake(Tunnel underlying, Vault vault, Role role) {
        CompletableFuture<NoiseTunnel> future = new CompletableFuture<>();
        NoiseHandshake state = new NoiseHandshake(
                role == Role.INITIATOR ? NoiseHandshake.Role.INITIATOR : NoiseHandshake.Role.RESPONDER,
                vault);

        // The handshake is synchronous over the underlying tunnel: each
        // {@code send} may re-enter this callback before returning.  Guard
        // {@code finalize} with {@code future.isDone()} so the receiver-side
        // and the sender-side both check for completion without double-
        // finalizing (which would otherwise create a second NoiseTunnel and
        // overwrite the underlying tunnel's receiver).
        underlying.onReceive(bytes -> {
            try {
                state.readMessage(bytes);
                if (state.isComplete() && !future.isDone()) {
                    future.complete(finalize(underlying, state));
                } else if (state.isMyTurnToWrite()) {
                    underlying.send(state.writeMessage(EMPTY));
                    if (state.isComplete() && !future.isDone()) {
                        future.complete(finalize(underlying, state));
                    }
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        if (role == Role.INITIATOR) {
            try {
                underlying.send(state.writeMessage(EMPTY));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    private static NoiseTunnel finalize(Tunnel underlying, NoiseHandshake state) {
        NoiseHandshake.TransportKeys keys = state.split();
        MultiKey peer = MultiKey.of(KeyAgreement.X25519.builtin(), state.remoteStaticPub());
        return new NoiseTunnel(underlying, keys.send(), keys.receive(), peer);
    }

    // ==================================================================================
    // Transport
    // ==================================================================================

    private static final byte[] EMPTY = new byte[0];
    private static final Aead.AesGcm256 AEAD = Aead.AesGcm256.builtin();

    private final Tunnel underlying;
    private final byte[] sendKey;
    private final byte[] receiveKey;
    private final MultiKey peerStatic;
    private long sendNonce;
    private long receiveNonce;
    private volatile Consumer<byte[]> userReceiver;
    private volatile boolean open = true;

    private NoiseTunnel(Tunnel underlying, byte[] sendKey, byte[] receiveKey, MultiKey peerStatic) {
        this.underlying = underlying;
        this.sendKey = sendKey;
        this.receiveKey = receiveKey;
        this.peerStatic = peerStatic;
        underlying.onReceive(this::receiveEncrypted);
    }

    @Override
    public CompletableFuture<Void> send(byte[] bytes) {
        if (!open) throw new IllegalStateException("NoiseTunnel is closed");
        byte[] ciphertext = AEAD.encrypt(sendKey, nonce(sendNonce++), null, bytes);
        return underlying.send(ciphertext);
    }

    @Override
    public void onReceive(Consumer<byte[]> consumer) {
        this.userReceiver = consumer;
    }

    private void receiveEncrypted(byte[] ciphertext) {
        if (!open) return;
        byte[] plaintext = AEAD.decrypt(receiveKey, nonce(receiveNonce++), null, ciphertext);
        Consumer<byte[]> r = userReceiver;
        if (r != null) r.accept(plaintext);
    }

    // Noise spec: 96-bit AES-GCM nonce = 32 zero bits || 64-bit little-endian counter.
    private static byte[] nonce(long counter) {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putInt(0);
        buf.putLong(Long.reverseBytes(counter));
        return buf.array();
    }

    // ==================================================================================
    // Security properties
    // ==================================================================================

    @Override
    public Optional<MultiKey> counterparty() {
        return Optional.of(peerStatic);
    }

    @Override
    public boolean isConfidential() { return true; }

    @Override
    public boolean isAuthenticated() { return true; }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        underlying.close();
    }
}
