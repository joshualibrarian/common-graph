package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.ref.ItemRef;

/**
 * The substrate-shape surface for a signer — produce a {@link VarSig} over
 * bytes.  Concrete {@code Signer} (runtime) implements this; substrate-shape
 * code (manifest builders, record builders) signs through this interface
 * without pulling in vault or attestation surfaces.
 *
 * <p>Verification — which requires algorithm-resolution against a public key
 * — lives on runtime {@code Signer} directly, not here, because the
 * {@code MultiKey} type and algorithm dispatch are runtime concerns.
 */
public interface SignerHandle {

    /** The IID of the signer (its graph identity). */
    ItemRef iid();

    /** Sign {@code message} and return a self-describing {@link VarSig}. */
    VarSig sign(byte[] message);

    /** Whether this signer holds private material and can produce signatures. */
    boolean canSign();
}
