package dev.everydaythings.graph.bridges.keri.translator;

import dev.everydaythings.graph.bridges.keri.AidMapping;
import dev.everydaythings.graph.bridges.keri.Cesr;
import dev.everydaythings.graph.bridges.keri.MatterCode;
import dev.everydaythings.graph.bridges.keri.event.KelEvents;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.ThematicRole;
import io.ipfs.multihash.Multihash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates between KERI Key Event Log (KEL) events and Common Graph
 * {@link Body Bodies}.  This is where the wire format meets CG's semantic
 * model: a KEL inception event, parsed by {@link dev.everydaythings.graph.bridges.keri.event.KelJson},
 * becomes a CG INCEPTION body with the canonical thematic-role bindings,
 * and the reverse rebuilds a KEL event from the same bindings.
 *
 * <p>The translator is intentionally <i>body-only</i> in v1.  KERI signatures
 * are over the KEL JSON bytes; CG records sign over CG-encoded bodies.  Those
 * digests don't match by construction, so a faithful Frame translation would
 * either need to embed the original KERI bytes (defeating the semantic
 * translation) or re-sign with the controller's private key (which the bridge
 * doesn't hold).  Verification of the original KERI signatures stays in the
 * wire layer (see {@code SignedKelEvent.parseWire}); this layer produces the
 * <i>semantic view</i> of the event for query and integration purposes.
 *
 * <h3>v1 scope</h3>
 *
 * <ul>
 *   <li>Inception ({@code icp}) only — single signing key, single next-key
 *       digest, no witnesses, no delegators, no thresholds beyond the
 *       implicit single-controller case.</li>
 *   <li>Rotation, delegation, interaction events follow the same pattern;
 *       added once the inception path is validated end-to-end.</li>
 * </ul>
 *
 * <h3>Mapping</h3>
 *
 * <pre>
 *   KEL "i"    →  Body theme (AID → ItemRef via AidMapping)
 *   KEL "k"[0] →  INSTRUMENT [MULTIKEY] binding (Ed25519 MultiKey)
 *   KEL "n"[0] →  INSTRUMENT [NEXT]     binding (ContentRef, SHA2-256)
 *                 + PURPOSE → @signing  (KERI keys are signing keys)
 * </pre>
 */
public final class KelTranslator {

    private KelTranslator() {}

    /**
     * Convert a parsed KEL inception event into a CG {@link Body} matching
     * the canonical {@link IdentityVocabulary.Inception} shape.
     *
     * @throws IllegalArgumentException if {@code event} is not an inception
     *         or doesn't carry the v1-supported single-key shape
     */
    public static Body bodyFromInception(Map<String, Object> event) {
        if (!"icp".equals(event.get("t"))) {
            throw new IllegalArgumentException(
                    "expected inception event ('icp'), got: " + event.get("t"));
        }
        @SuppressWarnings("unchecked")
        List<String> signingKeys = (List<String>) event.get("k");
        @SuppressWarnings("unchecked")
        List<String> nextDigests = (List<String>) event.get("n");
        if (signingKeys == null || signingKeys.size() != 1) {
            throw new IllegalArgumentException(
                    "v1 translator requires exactly one signing key, got: " + signingKeys);
        }
        if (nextDigests == null || nextDigests.size() != 1) {
            throw new IllegalArgumentException(
                    "v1 translator requires exactly one next-key digest, got: " + nextDigests);
        }
        ItemRef identity = AidMapping.aidToItemRef((String) event.get("i"));
        Cesr.Primitive signingKey = Cesr.decodePrimitive(signingKeys.get(0));
        Cesr.Primitive nextDigest = Cesr.decodePrimitive(nextDigests.get(0));

        MultiKey multiKey = MultiKey.of(Signing.Ed25519.builtin(), signingKey.raw());
        ContentRef nextRef = new ContentRef(
                nextDigest.raw(), MatterCode.multihashType(nextDigest.code()));

        List<Binding> bindings = new ArrayList<>(4);
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), identity));
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY),
                ItemRef.iid(IdentityVocabulary.Signing.KEY)));
        bindings.add(Binding.qualified(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(EncryptionVocabulary.Multikey.KEY))),
                multiKey.encoded()));
        bindings.add(Binding.qualified(
                ItemRef.iid(ThematicRole.Instrument.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(IdentityVocabulary.Next.KEY))),
                nextRef));

        return Body.of(ItemRef.of(ItemRef.iid(IdentityVocabulary.Inception.KEY)), bindings);
    }

    /**
     * Convert a CG INCEPTION body back to a KEL inception event map.  The
     * returned map carries placeholders for {@code v} and {@code d}; pass it
     * through {@link dev.everydaythings.graph.bridges.keri.event.KelJson#encode}
     * to back-patch the size and SAID.
     *
     * @throws IllegalArgumentException if the body's predicate is not
     *         INCEPTION, or required bindings are missing
     */
    public static Map<String, Object> inceptionFromBody(Body body) {
        if (!ItemRef.iid(IdentityVocabulary.Inception.KEY).equals(body.head())) {
            throw new IllegalArgumentException(
                    "expected INCEPTION body, got head: " + body.head());
        }
        ItemRef identity = extractTheme(body);
        MultiKey signingKey = extractMultiKey(body);
        ContentRef nextDigest = extractNext(body);

        String aid = AidMapping.itemRefToAid(identity, MatterCode.ED25519);
        String signingKeyQb64 = Cesr.encodePrimitive(MatterCode.ED25519, signingKey.rawKey());
        byte[] nextRawDigest = Multihash.deserialize(nextDigest.multihash()).getHash();
        String nextQb64 = Cesr.encodePrimitive(
                matterCodeFor(Multihash.deserialize(nextDigest.multihash()).getType()),
                nextRawDigest);

        return KelEvents.inception(
                aid, List.of(signingKeyQb64), List.of(nextQb64), "1", "1");
    }

    // ==================================================================================
    // Binding extractors
    // ==================================================================================

    private static ItemRef extractTheme(Body body) {
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        for (Binding b : body.bindings()) {
            if (b.role().equals(themeRole) && b.qualifiers().isEmpty()) {
                if (b.target() instanceof ItemRef ref) return ref;
            }
        }
        throw new IllegalArgumentException("INCEPTION body missing THEME binding");
    }

    private static MultiKey extractMultiKey(Body body) {
        ItemRef instrumentRole = ItemRef.iid(ThematicRole.Instrument.KEY);
        ItemRef multikeyQualifier = ItemRef.iid(EncryptionVocabulary.Multikey.KEY);
        for (Binding b : body.bindings()) {
            if (!b.role().equals(instrumentRole)) continue;
            if (b.qualifiers().size() != 1) continue;
            if (!(b.qualifiers().get(0) instanceof CompoundKey.Sememe s)) continue;
            if (!s.id().equals(multikeyQualifier)) continue;
            if (b.target() instanceof byte[] bytes) return MultiKey.decode(bytes);
        }
        throw new IllegalArgumentException("INCEPTION body missing INSTRUMENT [MULTIKEY] binding");
    }

    private static ContentRef extractNext(Body body) {
        ItemRef instrumentRole = ItemRef.iid(ThematicRole.Instrument.KEY);
        ItemRef nextQualifier = ItemRef.iid(IdentityVocabulary.Next.KEY);
        for (Binding b : body.bindings()) {
            if (!b.role().equals(instrumentRole)) continue;
            if (b.qualifiers().size() != 1) continue;
            if (!(b.qualifiers().get(0) instanceof CompoundKey.Sememe s)) continue;
            if (!s.id().equals(nextQualifier)) continue;
            if (b.target() instanceof ContentRef ref) return ref;
        }
        throw new IllegalArgumentException("INCEPTION body missing INSTRUMENT [NEXT] binding");
    }

    private static String matterCodeFor(Multihash.Type type) {
        return switch (type) {
            case sha2_256 -> MatterCode.SHA2_256;
            case sha3_256 -> MatterCode.SHA3_256;
            case blake3 -> MatterCode.BLAKE3_256;
            case blake2b_256 -> MatterCode.BLAKE2B_256;
            default -> throw new IllegalArgumentException(
                    "No CESR matter code for multihash type " + type);
        };
    }
}
