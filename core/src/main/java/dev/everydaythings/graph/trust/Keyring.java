package dev.everydaythings.graph.trust;

import lombok.Value;

import java.security.PrivateKey;
import java.util.Optional;

public interface Keyring extends AutoCloseable {

    @Value
    class KeyId {
        String value;
    }

    /** True if this keyring currently can use private keys (unlocked). */
    boolean isUnlocked();

    /** Unlocks the keyring for private-key operations. */
    void unlock(char[] password);

    /** Returns true if the keyring has private key material for this key id. */
    boolean hasPrivateKey(KeyId keyId);

    /**
     * Signs message bytes with the private key identified by keyId.
     * (Preferred over returning PrivateKey.)
     */
    byte[] sign(KeyId keyId, byte[] message);

    /**
     * Escape hatch: return JCA PrivateKey if you truly need it (keep v1 small).
     * Optional.empty() if locked or not present.
     */
    Optional<PrivateKey> getPrivateKey(KeyId keyId);

    @Override
    void close();
}


