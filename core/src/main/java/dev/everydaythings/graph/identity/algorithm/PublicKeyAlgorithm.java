package dev.everydaythings.graph.identity.algorithm;

import java.security.KeyPair;
import java.security.PublicKey;

/**
 * Shared contract for {@link Algorithm} sub-archetypes whose instances have a
 * public-key wire encoding.  Implemented by {@link Signing} (signature
 * algorithms) and {@link KeyAgreement} (key-agreement algorithms) — the two
 * sub-archetype trees whose runtime form involves a long-term keypair and a
 * multikey codec.
 *
 * <p>Symmetric algorithms ({@link Aead}, {@link Kdf}) and hash algorithms
 * ({@link Hash}) have no public keys and do not implement this interface.
 *
 * <p>This is the surface that {@link dev.everydaythings.graph.identity.MultiKey
 * MultiKey} uses to materialize a JCA {@link PublicKey} from raw multikey
 * payload bytes, regardless of whether the key is for signing or for key
 * agreement.
 */
public interface PublicKeyAlgorithm {

    /**
     * Decode raw public-key bytes (multikey payload minus the codec prefix)
     * into a JCA {@link PublicKey}.
     */
    PublicKey decodePublicKey(byte[] rawBytes);

    /**
     * Extract this algorithm's raw public-key bytes from a JCA {@link PublicKey}.
     * The inverse of {@link #decodePublicKey(byte[])}.
     */
    byte[] publicKeyToRaw(PublicKey jcaKey);

    /** The multikey codec code identifying this algorithm's key type. */
    long multikeyCode();

    /** Generate a fresh JCA {@link KeyPair} for this algorithm. */
    KeyPair generateKeyPair();
}
