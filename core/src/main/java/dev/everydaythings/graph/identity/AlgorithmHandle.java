package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.id.ItemRef;

import java.security.PublicKey;

/**
 * Runtime handle for a cryptographic algorithm — the verify-side surface
 * that wraps an algorithm's metadata with the JCA (or polyglot) machinery
 * that actually computes against it.
 *
 * <p>Handles are *verify-side*: they cover operations that need only
 * <i>public</i> key material — signature verification, public-key decoding,
 * codec lookups.  Signing operations live entirely on {@link
 * dev.everydaythings.graph.identity.vault.Vault} — private keys never
 * leave the vault, so they never need to pass through a handle.
 *
 * <p>Each handle is keyed by its algorithm sememe's IID
 * (e.g., {@code @cg.algorithm:ed25519}) and exposes the on-the-wire codec
 * identifiers (COSE, varsig, multikey) the wire format uses to address it.
 *
 * <p>Two flavors of implementation:
 * <ul>
 *   <li>{@link JcaAlgorithmHandle} — built-in algorithms backed by the JVM's
 *       JCA provider.  One instance per algorithm, parameterized by the
 *       metadata read from the algorithm sememe.</li>
 *   <li>(future) {@code PolyglotAlgorithmHandle} — algorithms deployed as
 *       code items implementing the algorithm sememe in a non-Java language.
 *       Delegates {@code verify} / {@code decodePublicKey} to the polyglot
 *       code item via the Stage.</li>
 * </ul>
 *
 * <p>The librarian's {@code AlgorithmCache} warms one handle per known
 * algorithm sememe at bootstrap (or lazily on first miss).  Lookups use
 * codec ints (cheap map hits); after that, the handle is direct dispatch.
 */
public interface AlgorithmHandle {

    /** The algorithm sememe's IID (e.g., {@code @cg.algorithm:ed25519}). */
    ItemRef sememeIid();

    /** COSE algorithm identifier — the IANA-registered integer. */
    long coseId();

    /** Multicodec varsig code — identifies the signature shape on the wire. */
    long varsigCode();

    /** Multicodec multikey code — identifies the public-key shape on the wire. */
    long multikeyCode();

    /**
     * Verify a signature against a public key and message bytes.
     * Returns false on any error.
     */
    boolean verify(byte[] message, byte[] signature, PublicKey publicKey);

    /**
     * Decode raw public-key bytes (the encoding-specific multikey payload,
     * minus the codec prefix) into a JCA {@link PublicKey} suitable for
     * passing to {@link #verify}.
     *
     * @throws RuntimeException if the bytes don't decode as a public key of
     *                          this algorithm's shape
     */
    PublicKey decodePublicKey(byte[] rawBytes);
}
