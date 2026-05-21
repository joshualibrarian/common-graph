package dev.everydaythings.graph.bridges.keri;

import dev.everydaythings.graph.ref.ItemRef;
import io.ipfs.multihash.Multihash;

/**
 * Maps between KERI's AID (Autonomic IDentifier) and CG's ItemRef.
 *
 * <p>Both identifier families are <i>content-addressed</i>: a KERI AID is the
 * qb64 encoding of either a public key (basic prefix, e.g. CESR code
 * {@code D}) or a digest of an inception event (self-addressing prefix, e.g.
 * code {@code E} for Blake3 or {@code I} for SHA2-256).  CG's {@link ItemRef}
 * wraps a multihash whose type-byte records the same intent — {@code id} for
 * "these bytes ARE the identity" (raw public key) or a hash family for
 * "these bytes are a digest under this algorithm."
 *
 * <p>So the mapping is mechanical: pick the multihash type that corresponds
 * to the CESR matter code (via {@link MatterCode#multihashType}), then wrap
 * the raw bytes.
 */
public final class AidMapping {

    private AidMapping() {}

    /** Convert a KERI AID (qb64 string) to a CG {@link ItemRef}. */
    public static ItemRef aidToItemRef(String aid) {
        Cesr.Primitive primitive = Cesr.decodePrimitive(aid);
        Multihash.Type type = MatterCode.multihashType(primitive.code());
        if (type == null) {
            throw new IllegalArgumentException(
                    "No multihash mapping for CESR matter code " + primitive.code());
        }
        return new ItemRef(primitive.raw(), type);
    }

    /** Convert a CG {@link ItemRef} back to a KERI AID under the given matter code. */
    public static String itemRefToAid(ItemRef ref, String code) {
        byte[] raw = Multihash.deserialize(ref.multihash()).getHash();
        int expected = MatterCode.rawLength(code);
        if (raw.length != expected) {
            throw new IllegalArgumentException(
                    "ItemRef hash length " + raw.length + " ≠ matter code "
                            + code + " raw length " + expected);
        }
        return Cesr.encodePrimitive(code, raw);
    }
}
