package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.policy.PolicySet;
import dev.everydaythings.graph.crypt.Algorithm;
import dev.everydaythings.graph.crypt.EncryptionPublicKey;

import java.util.List;

/**
 * Provides encryption policy for the commit flow.
 *
 * <p>During commit, each frame is checked against this context to determine
 * whether it should be encrypted and to whom. This decouples the encryption
 * decision from the encoding logic.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link #NONE} — no encryption (all frames stored cleartext)</li>
 *   <li>{@link #allFrames(List)} — encrypt all non-local frames to the given recipients</li>
 *   <li>{@link #fromEncryptionPolicy(PolicySet.EncryptionPolicy, List)} — from a per-frame policy</li>
 * </ul>
 */
public interface EncryptionContext {

    /** No encryption — all frames stored cleartext. */
    EncryptionContext NONE = new EncryptionContext() {
        @Override public boolean shouldEncrypt(FrameKey key) { return false; }
        @Override public List<EncryptionPublicKey> recipients(FrameKey key) { return List.of(); }
        @Override public Algorithm.Aead aeadAlgorithm() { return Algorithm.Aead.AES_GCM_256; }
    };

    /**
     * Should the frame with this key be encrypted?
     */
    boolean shouldEncrypt(FrameKey key);

    /**
     * The recipients for encrypting the frame with this key.
     * Only called when {@link #shouldEncrypt(FrameKey)} returns true.
     */
    List<EncryptionPublicKey> recipients(FrameKey key);

    /**
     * The AEAD algorithm to use. Default: AES-256-GCM.
     */
    default Algorithm.Aead aeadAlgorithm() {
        return Algorithm.Aead.AES_GCM_256;
    }

    /**
     * Encrypt all non-local frames to the given recipients.
     */
    static EncryptionContext allFrames(List<EncryptionPublicKey> recipients) {
        return allFrames(recipients, Algorithm.Aead.AES_GCM_256);
    }

    /**
     * Encrypt all non-local frames to the given recipients with the specified AEAD.
     */
    static EncryptionContext allFrames(List<EncryptionPublicKey> recipients, Algorithm.Aead aead) {
        List<EncryptionPublicKey> r = List.copyOf(recipients);
        return new EncryptionContext() {
            @Override public boolean shouldEncrypt(FrameKey key) { return !r.isEmpty(); }
            @Override public List<EncryptionPublicKey> recipients(FrameKey key) { return r; }
            @Override public Algorithm.Aead aeadAlgorithm() { return aead; }
        };
    }

    /**
     * Create an EncryptionContext from a per-frame {@link PolicySet.EncryptionPolicy}.
     *
     * <p>The caller must pre-resolve recipients to their encryption public keys.
     * For {@code encryptToReaders} policies, the caller resolves the access policy's
     * READ subjects to keys before calling this factory.
     *
     * @param policy            the encryption policy (from EntryConfig.policy.encryption)
     * @param resolvedRecipients pre-resolved encryption public keys
     */
    static EncryptionContext fromEncryptionPolicy(PolicySet.EncryptionPolicy policy,
                                                   List<EncryptionPublicKey> resolvedRecipients) {
        if (policy == null || !policy.isEnabled()) return NONE;
        List<EncryptionPublicKey> r = List.copyOf(resolvedRecipients);
        Algorithm.Aead aead = policy.algorithm() != null
                ? Algorithm.Aead.valueOf(policy.algorithm())
                : Algorithm.Aead.AES_GCM_256;
        return new EncryptionContext() {
            @Override public boolean shouldEncrypt(FrameKey key) { return !r.isEmpty(); }
            @Override public List<EncryptionPublicKey> recipients(FrameKey key) { return r; }
            @Override public Algorithm.Aead aeadAlgorithm() { return aead; }
        };
    }
}
