package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.ref.ItemRef;

/**
 * The surface signing-related code needs from a signer.  {@link Signer} is
 * the concrete implementation; substrate-shape code binds to this interface
 * so it doesn't pull in vault, key-management, or attestation surfaces.
 *
 * <p>Future split: this interface lives in :substrate, Signer-the-class
 * implements it from runtime.
 */
public interface SignerHandle {

    /** The IID of the signer (its graph identity). */
    ItemRef iid();

    /** Sign {@code message} and return a self-describing {@link VarSig}. */
    VarSig sign(byte[] message);

    /** Whether this signer holds private material and can produce signatures. */
    boolean canSign();

    /** Verify {@code varsig} over {@code message} under {@code publicKey}. */
    boolean verify(MultiKey publicKey, byte[] message, VarSig varsig);
}
