package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.canonical.Scope;

/**
 * Encoding scope — whether bytes are being produced for body-hashing or for
 * the full attestation record.
 *
 * <ul>
 *   <li>{@link #BODY} — exclude signatures and other non-identity bindings;
 *       the resulting bytes feed the structural Merkle hash that defines a
 *       Body's {@code DatumID}.</li>
 *   <li>{@link #RECORD} — include everything, signatures and all; the full
 *       wire form that crosses Parley or hits the OBJECTS store.</li>
 * </ul>
 *
 * <p>Scope is purely a serialization-time flag — the canonical Merkle walk
 * itself is encoder-agnostic and doesn't need this distinction. It lives here
 * for the codec layer, not for the data classes.
 */
public enum Scope {
    BODY,
    RECORD
}
