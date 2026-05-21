package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.ThematicRole;
import com.upokecenter.cbor.CBORObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link Opaque} family — Redacted / Compressed / Encrypted.  Each
 * variant carries the structural hash of the subtree it stands in for, so
 * inserting an Opaque in place of a real subtree leaves the parent body's
 * DatumID invariant.  Each variant also carries an optional list of
 * record-refs that explain the opacity; those refs are <b>not</b> part of
 * the Opaque's hash contribution and can change without breaking the
 * parent's signatures.
 */
class OpaqueTest {

    private static final ItemRef HEAD       = ItemRef.fromString("cg.test:doc");
    private static final ItemRef BODY_ROLE  = ItemRef.iid(ThematicRole.Value.KEY);
    private static final ItemRef CHILD_ROLE = ItemRef.iid(ThematicRole.Theme.KEY);

    /** A sample body to use as a "real" subtree we can opaque-ify. */
    private static Body sampleBody() {
        return Body.of(HEAD, List.of(new Binding(BODY_ROLE, "hello, world")));
    }

    /** Some arbitrary HashID refs for use as recordRefs. */
    private static HashID datumRef(int n) {
        byte[] bytes = new byte[32];
        bytes[31] = (byte) n;
        return DatumRef.of(bytes);
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class Roundtrip {

        @Test
        @DisplayName("Opaque.Redacted round-trips through CBOR (with and without refs)")
        void redactedRoundtrip() {
            byte[] hash = HashTree.hashOf(sampleBody(), HashTree.DEFAULT_DIGEST);

            Opaque.Redacted bare = new Opaque.Redacted(hash);
            assertThat(roundTrip(bare)).isEqualTo(bare);

            Opaque.Redacted withRefs = new Opaque.Redacted(hash, List.of(datumRef(1), datumRef(2)));
            assertThat(roundTrip(withRefs)).isEqualTo(withRefs);
        }

        @Test
        @DisplayName("Opaque.Compressed round-trips through CBOR (with and without refs)")
        void compressedRoundtrip() {
            Opaque.Compressed bare = Compress.compress(sampleBody());
            assertThat(roundTrip(bare)).isEqualTo(bare);

            Opaque.Compressed withRefs = new Opaque.Compressed(
                    bare.wrappedHash(), bare.compressedPayload(),
                    List.of(datumRef(3)));
            assertThat(roundTrip(withRefs)).isEqualTo(withRefs);
        }

        @Test
        @DisplayName("Opaque.Encrypted round-trips through CBOR (with and without refs)")
        void encryptedRoundtrip() {
            byte[] hash = HashTree.hashOf(sampleBody(), HashTree.DEFAULT_DIGEST);
            byte[] cipher = new byte[]{0x01, 0x02, 0x03, 0x04};

            Opaque.Encrypted bare = new Opaque.Encrypted(hash, cipher);
            assertThat(roundTrip(bare)).isEqualTo(bare);

            Opaque.Encrypted withRefs = new Opaque.Encrypted(
                    hash, cipher, List.of(datumRef(5), datumRef(6), datumRef(7)));
            assertThat(roundTrip(withRefs)).isEqualTo(withRefs);
        }

        private static Opaque roundTrip(Opaque op) {
            CBORObject encoded = CgCbor.toCbor(op);
            return CgCbor.decodeOpaque(encoded);
        }
    }

    @Nested
    @DisplayName("Merkle preservation")
    class MerklePreservation {

        @Test
        @DisplayName("parent hash is invariant when a target is replaced with Opaque.Redacted")
        void redactedTargetPreservesParentHash() {
            Body child = sampleBody();
            byte[] childHash = HashTree.hashOf(child, HashTree.DEFAULT_DIGEST);

            Body parentInline   = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, BindingTarget.frame(child))));
            Body parentRedacted = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, new Opaque.Redacted(childHash))));

            assertThat(HashTree.hashOf(parentRedacted, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(HashTree.hashOf(parentInline, HashTree.DEFAULT_DIGEST));
        }

        @Test
        @DisplayName("parent hash is invariant when a target is replaced with Opaque.Compressed")
        void compressedTargetPreservesParentHash() {
            Body child = sampleBody();
            Opaque.Compressed compressed = Compress.compress(child);

            Body parentInline     = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, BindingTarget.frame(child))));
            Body parentCompressed = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, compressed)));

            assertThat(HashTree.hashOf(parentCompressed, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(HashTree.hashOf(parentInline, HashTree.DEFAULT_DIGEST));
        }

        @Test
        @DisplayName("parent hash is invariant when a target is replaced with Opaque.Encrypted")
        void encryptedTargetPreservesParentHash() {
            Body child = sampleBody();
            byte[] childHash = HashTree.hashOf(child, HashTree.DEFAULT_DIGEST);
            byte[] cipher = new byte[]{0x42, 0x43, 0x44};

            Body parentInline    = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, BindingTarget.frame(child))));
            Body parentEncrypted = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, new Opaque.Encrypted(childHash, cipher))));

            assertThat(HashTree.hashOf(parentEncrypted, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(HashTree.hashOf(parentInline, HashTree.DEFAULT_DIGEST));
        }
    }

    @Nested
    @DisplayName("Opaque as a single binding-list entry (position 2)")
    class OpaqueAsBindingEntry {

        @Test
        @DisplayName("a body with mixed Binding + Opaque entries round-trips through CBOR")
        void mixedEntriesRoundtrip() {
            byte[] hiddenBindingHash = HashTree.hashOf(
                    new Binding(BODY_ROLE, "hidden"), HashTree.DEFAULT_DIGEST);

            Body body = Body.of(HEAD, List.of(
                    new Binding(BODY_ROLE, "visible"),
                    new Opaque.Redacted(hiddenBindingHash)));

            byte[] encoded = CgCbor.codec().encode(body);
            Body decoded = (Body) CgCbor.codec().decode(encoded);

            assertThat(decoded.entries()).hasSize(2);
            assertThat(decoded.entries()).anyMatch(e -> e instanceof Opaque.Redacted);
            assertThat(decoded.entries()).anyMatch(e -> e instanceof Binding);
            assertThat(decoded).isEqualTo(body);
        }

        @Test
        @DisplayName("parent hash is invariant when a whole Binding is replaced with Opaque.Redacted")
        void wholeBindingRedactionPreservesHash() {
            Binding sensitive = new Binding(BODY_ROLE, "secret");
            byte[] bindingHash = HashTree.hashOf(sensitive, HashTree.DEFAULT_DIGEST);

            Body full     = Body.of(HEAD, List.of(sensitive));
            Body redacted = Body.of(HEAD, List.of(new Opaque.Redacted(bindingHash)));

            assertThat(HashTree.hashOf(redacted, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(HashTree.hashOf(full, HashTree.DEFAULT_DIGEST));
        }

        @Test
        @DisplayName("the soft-deprecated bindings() accessor filters Opaque entries out")
        void deprecatedBindingsFiltersOpaques() {
            byte[] hash = HashTree.hashOf(sampleBody(), HashTree.DEFAULT_DIGEST);
            Body body = Body.of(HEAD, List.of(
                    new Binding(BODY_ROLE, "visible"),
                    new Opaque.Redacted(hash)));

            assertThat(body.entries()).hasSize(2);
            List<Binding> bindings = body.bindings();
            assertThat(bindings).hasSize(1);
            assertThat(bindings.get(0).target()).isEqualTo("visible");
        }
    }

    @Nested
    @DisplayName("Opaque as a CompoundKey qualifier (position 3)")
    class OpaqueAsQualifier {

        @Test
        @DisplayName("a CompoundKey with a mixed qualifier list round-trips through CBOR")
        void mixedQualifiersRoundtrip() {
            ItemRef englishQualifierIid = ItemRef.iid(Language.English.KEY);
            ItemRef lemmaQualifierIid = ItemRef.iid(GrammaticalFeature.Lemma.KEY);
            byte[] hiddenQualifierHash = HashTree.hashOf(
                    new CompoundKey.Sememe(lemmaQualifierIid),
                    HashTree.DEFAULT_DIGEST);

            CompoundKey key = CompoundKey.of(
                    BODY_ROLE,
                    new CompoundKey.Sememe(englishQualifierIid),
                    new Opaque.Redacted(hiddenQualifierHash));

            Binding binding = new Binding(key, "value");
            Body body = Body.of(HEAD, java.util.List.of(binding));

            byte[] encoded = CgCbor.codec().encode(body);
            Body decoded = (Body) CgCbor.codec().decode(encoded);

            assertThat(decoded).isEqualTo(body);

            Binding recoveredBinding = (Binding) decoded.entries().get(0);
            assertThat(recoveredBinding.key().parts()).hasSize(2);
            assertThat(recoveredBinding.key().parts())
                    .anyMatch(p -> p instanceof Opaque.Redacted)
                    .anyMatch(p -> p instanceof CompoundKey.Sememe);
        }

        @Test
        @DisplayName("parent body hash is invariant when a qualifier is replaced with Opaque.Redacted")
        void qualifierRedactionPreservesParentHash() {
            ItemRef englishQualifierIid = ItemRef.iid(Language.English.KEY);
            ItemRef lemmaQualifierIid = ItemRef.iid(GrammaticalFeature.Lemma.KEY);

            CompoundKey.Sememe sensitiveQualifier = new CompoundKey.Sememe(lemmaQualifierIid);
            byte[] sensitiveHash = HashTree.hashOf(sensitiveQualifier, HashTree.DEFAULT_DIGEST);

            CompoundKey fullKey = CompoundKey.of(
                    BODY_ROLE,
                    new CompoundKey.Sememe(englishQualifierIid),
                    sensitiveQualifier);

            CompoundKey redactedKey = CompoundKey.of(
                    BODY_ROLE,
                    new CompoundKey.Sememe(englishQualifierIid),
                    new Opaque.Redacted(sensitiveHash));

            Body fullBody     = Body.of(HEAD, java.util.List.of(new Binding(fullKey, "x")));
            Body redactedBody = Body.of(HEAD, java.util.List.of(new Binding(redactedKey, "x")));

            assertThat(HashTree.hashOf(redactedBody, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(HashTree.hashOf(fullBody, HashTree.DEFAULT_DIGEST));
        }

        @Test
        @DisplayName("the soft-deprecated qualifiers() accessor filters Opaque parts out")
        void deprecatedQualifiersFiltersOpaques() {
            ItemRef englishQualifierIid = ItemRef.iid(Language.English.KEY);
            byte[] hash = HashTree.hashOf(sampleBody(), HashTree.DEFAULT_DIGEST);

            CompoundKey key = CompoundKey.of(
                    BODY_ROLE,
                    new CompoundKey.Sememe(englishQualifierIid),
                    new Opaque.Redacted(hash));

            assertThat(key.parts()).hasSize(2);
            java.util.List<CompoundKey.Qualifier> filtered = key.qualifiers();
            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0)).isInstanceOf(CompoundKey.Sememe.class);
        }
    }

    @Nested
    @DisplayName("recordRefs do not contribute to parent hash")
    class RecordRefsAreNotHashed {

        @Test
        @DisplayName("two Opaque.Encrypted with the same hash but different recordRefs produce the same parent hash")
        void encryptedRefsDontAffectParentHash() {
            byte[] hash = HashTree.hashOf(sampleBody(), HashTree.DEFAULT_DIGEST);
            byte[] cipher = new byte[]{0x01, 0x02};

            Opaque.Encrypted noRefs   = new Opaque.Encrypted(hash, cipher);
            Opaque.Encrypted withRefs = new Opaque.Encrypted(hash, cipher,
                    List.of(datumRef(10), datumRef(11)));

            Body parentNoRefs   = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, noRefs)));
            Body parentWithRefs = Body.of(HEAD, List.of(new Binding(CHILD_ROLE, withRefs)));

            assertThat(HashTree.hashOf(parentWithRefs, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(HashTree.hashOf(parentNoRefs, HashTree.DEFAULT_DIGEST));
        }

        @Test
        @DisplayName("the two Opaques themselves are NOT equal (their wire identities differ)")
        void opaquesWithDifferentRefsAreNotEqual() {
            byte[] hash = HashTree.hashOf(sampleBody(), HashTree.DEFAULT_DIGEST);
            byte[] cipher = new byte[]{0x01, 0x02};

            Opaque.Encrypted noRefs   = new Opaque.Encrypted(hash, cipher);
            Opaque.Encrypted withRefs = new Opaque.Encrypted(hash, cipher,
                    List.of(datumRef(10)));

            assertThat(noRefs).isNotEqualTo(withRefs);
        }
    }
}
