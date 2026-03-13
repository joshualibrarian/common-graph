package dev.everydaythings.graph.crypt;

import java.util.Optional;

/**
 * PKCS#12-backed keyring implementation.
 * Stub - full implementation TBD.
 */
public class Pkcs12Keyring implements Keyring {

    @Override
    public boolean isUnlocked() {
        return false;
    }

    @Override
    public void unlock(char[] password) {
        throw new UnsupportedOperationException("Pkcs12Keyring not implemented");
    }

    @Override
    public boolean hasPrivateKey(KeyId keyId) {
        return false;
    }

    @Override
    public byte[] sign(KeyId keyId, byte[] message) {
        throw new UnsupportedOperationException("Pkcs12Keyring not implemented");
    }

    @Override
    public Optional<java.security.PrivateKey> getPrivateKey(KeyId keyId) {
        return Optional.empty();
    }

    @Override
    public void close() {
        // Nothing to close in stub
    }
}
