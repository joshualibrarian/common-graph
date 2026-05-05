package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefTest {

    // Well-known deterministic IDs for testing
    static final ItemID ALICE = ItemID.fromString("cg:test/alice");
    static final ItemID BOB = ItemID.fromString("cg:test/bob");
    static final ItemID GLOSS = ItemID.fromString("cg:pred/gloss");
    static final ItemID ENG = ItemID.fromString("cg:language/eng");
    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID CHAT = ItemID.fromString("cg:pred/chat");

    // A deterministic CID (hash of some content)
    static final ContentID VERSION_1 = ContentID.of("version-one-content".getBytes());
    static final ContentID VERSION_2 = ContentID.of("version-two-content".getBytes());

    @Nested
    @DisplayName("Factory methods and accessors")
    class Factories {

        @Test
        @DisplayName("simple ref — just an item")
        void simpleRef() {
            Ref ref = Ref.of(ALICE);

            assertThat(ref.target()).isEqualTo(ALICE);
            assertThat(ref.version()).isNull();
            assertThat(ref.frameKey()).isNull();
            assertThat(ref.selector()).isNull();
            assertThat(ref.isSimple()).isTrue();
            assertThat(ref.isVersioned()).isFalse();
            assertThat(ref.hasFrame()).isFalse();
            assertThat(ref.hasSelector()).isFalse();
        }

        @Test
        @DisplayName("versioned ref")
        void versionedRef() {
            Ref ref = Ref.of(ALICE, VERSION_1);

            assertThat(ref.target()).isEqualTo(ALICE);
            assertThat(ref.version()).isEqualTo(VERSION_1);
            assertThat(ref.isSimple()).isFalse();
            assertThat(ref.isVersioned()).isTrue();
            assertThat(ref.hasFrame()).isFalse();
        }

        @Test
        @DisplayName("ref with frame key")
        void refWithFrame() {
            CompoundKey key = CompoundKey.of(AUTHOR);
            Ref ref = Ref.of(ALICE, key);

            assertThat(ref.target()).isEqualTo(ALICE);
            assertThat(ref.version()).isNull();
            assertThat(ref.frameKey()).isEqualTo(key);
            assertThat(ref.isSimple()).isFalse();
            assertThat(ref.hasFrame()).isTrue();
        }

        @Test
        @DisplayName("full ref — version + frame + selector")
        void fullRef() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);
            Selector sel = Selector.byteRange(0, 1024);
            Ref ref = Ref.of(ALICE, VERSION_1, key, sel);

            assertThat(ref.target()).isEqualTo(ALICE);
            assertThat(ref.version()).isEqualTo(VERSION_1);
            assertThat(ref.frameKey()).isEqualTo(key);
            assertThat(ref.selector()).isEqualTo(sel);
            assertThat(ref.isSimple()).isFalse();
            assertThat(ref.isVersioned()).isTrue();
            assertThat(ref.hasFrame()).isTrue();
            assertThat(ref.hasSelector()).isTrue();
        }

        @Test
        @DisplayName("null target throws")
        void nullTargetThrows() {
            assertThatThrownBy(() -> Ref.of(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Binary round-trip")
    class BinaryRoundTrip {

        @Test
        @DisplayName("simple ref — bare multihash")
        void simpleRef() {
            Ref original = Ref.of(ALICE);
            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.isSimple()).isTrue();
        }

        @Test
        @DisplayName("simple ref bytes are just the multihash")
        void simpleRefBytesAreJustMultihash() {
            Ref ref = Ref.of(ALICE);
            byte[] refBytes = ref.toRefBytes();
            byte[] iidBytes = ALICE.encodeBinary();

            assertThat(refBytes).isEqualTo(iidBytes);
        }

        @Test
        @DisplayName("versioned ref")
        void versionedRef() {
            Ref original = Ref.of(ALICE, VERSION_1);
            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.version()).isEqualTo(VERSION_1);
        }

        @Test
        @DisplayName("ref with single sememe frame key")
        void singleSememeFrame() {
            Ref original = Ref.of(ALICE, CompoundKey.of(AUTHOR));
            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.frameKey().headSememe()).isEqualTo(AUTHOR);
        }

        @Test
        @DisplayName("ref with compound sememe frame key")
        void compoundSememeFrame() {
            Ref original = Ref.of(ALICE, CompoundKey.of(GLOSS, ENG));
            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.frameKey().size()).isEqualTo(2);
        }

        @Test
        @DisplayName("ref with qualified frame key (sememe + string qualifier)")
        void qualifiedFrame() {
            Ref original = Ref.of(ALICE, CompoundKey.of(CHAT, "tavern"));
            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.frameKey().size()).isEqualTo(2);
            assertThat(decoded.frameKey().headSememe()).isEqualTo(CHAT);
        }

        @Test
        @DisplayName("ref with selector")
        void withSelector() {
            Selector sel = Selector.byteRange(100, 2000);
            Ref original = Ref.of(ALICE, (ContentID) null, null, sel);
            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded.target()).isEqualTo(ALICE);
            assertThat(decoded.selector()).isEqualTo(sel);
            assertThat(decoded.selector().text()).isEqualTo("100..2000");
        }

        @Test
        @DisplayName("full ref — all components")
        void fullRef() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);
            Selector sel = Selector.byteRange(0, 1024);
            Ref original = Ref.of(ALICE, VERSION_1, key, sel);

            byte[] bytes = original.toRefBytes();
            Ref decoded = Ref.fromRefBytes(bytes);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.target()).isEqualTo(ALICE);
            assertThat(decoded.version()).isEqualTo(VERSION_1);
            assertThat(decoded.frameKey()).isEqualTo(key);
            assertThat(decoded.selector().text()).isEqualTo("0..1024");
        }

    }

    @Nested
    @DisplayName("CBOR Tag 6 round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("simple ref encodes as Tag 6")
        void simpleRefTag6() {
            Ref ref = Ref.of(ALICE);
            CBORObject cbor = ref.toCborTree(Canonical.Scope.BODY);

            assertThat(cbor.isTagged()).isTrue();
            assertThat(cbor.getMostInnerTag().ToInt32Checked()).isEqualTo(Canonical.CgTag.REF);
        }

        @Test
        @DisplayName("simple ref CBOR round-trip")
        void simpleRefCborRoundTrip() {
            Ref original = Ref.of(ALICE);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Ref decoded = Ref.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("full ref CBOR round-trip")
        void fullRefCborRoundTrip() {
            CompoundKey key = CompoundKey.of(CHAT, "tavern");
            Selector sel = Selector.byteRange(0, 100);
            Ref original = Ref.of(BOB, VERSION_2, key, sel);

            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Ref decoded = Ref.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR binary round-trip (encode to bytes, decode from bytes)")
        void binaryBytesRoundTrip() {
            Ref original = Ref.of(ALICE, VERSION_1, CompoundKey.of(GLOSS, ENG));
            byte[] cborBytes = original.toCborTree(Canonical.Scope.BODY).EncodeToBytes();
            Ref decoded = Ref.fromCborTree(CBORObject.DecodeFromBytes(cborBytes));

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("backward compat — bare multihash Tag 6 decodes as simple ref")
        void bareMultihashBackwardCompat() {
            // Simulate the old encoding: Tag 6 wrapping bare IID multihash bytes
            CBORObject tagged = CBORObject.FromCBORObjectAndTag(
                    CBORObject.FromByteArray(ALICE.encodeBinary()),
                    Canonical.CgTag.REF
            );

            Ref decoded = Ref.fromCborTree(tagged);

            assertThat(decoded.isSimple()).isTrue();
            assertThat(decoded.target()).isEqualTo(ALICE);
        }
    }

    @Nested
    @DisplayName("Text round-trip")
    class TextRoundTrip {

        @Test
        @DisplayName("simple ref text round-trip")
        void simpleRef() {
            Ref original = Ref.of(ALICE);
            String text = original.encodeText();
            Ref decoded = Ref.parse(text);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("versioned ref text round-trip")
        void versionedRef() {
            Ref original = Ref.of(ALICE, VERSION_1);
            String text = original.encodeText();
            Ref decoded = Ref.parse(text);

            assertThat(decoded).isEqualTo(original);
            assertThat(text).contains("@");
        }

        @Test
        @DisplayName("ref with sememe frame key text round-trip")
        void sememeFrame() {
            Ref original = Ref.of(ALICE, CompoundKey.of(AUTHOR));
            String text = original.encodeText();
            Ref decoded = Ref.parse(text);

            assertThat(decoded).isEqualTo(original);
            assertThat(text).contains("\\");
        }

        @Test
        @DisplayName("ref with compound frame key text round-trip")
        void compoundFrame() {
            Ref original = Ref.of(ALICE, CompoundKey.of(GLOSS, ENG));
            String text = original.encodeText();
            Ref decoded = Ref.parse(text);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("ref with qualified frame key text round-trip")
        void qualifiedFrame() {
            Ref original = Ref.of(ALICE, CompoundKey.of(CHAT, "tavern"));
            String text = original.encodeText();
            Ref decoded = Ref.parse(text);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("full ref text round-trip")
        void fullRef() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);
            Selector sel = Selector.byteRange(0, 1024);
            Ref original = Ref.of(ALICE, VERSION_1, key, sel);
            String text = original.encodeText();
            Ref decoded = Ref.parse(text);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("text contains expected structural markers")
        void textStructure() {
            CompoundKey key = CompoundKey.of(CHAT, "tavern");
            Selector sel = Selector.byteRange(0, 100);
            Ref ref = Ref.of(ALICE, VERSION_1, key, sel);
            String text = ref.encodeText();

            // Has exactly one @ (version)
            assertThat(text.chars().filter(c -> c == '@').count()).isEqualTo(1);
            // Has backslash separators for frame key tokens
            assertThat(text.chars().filter(c -> c == '\\').count()).isGreaterThanOrEqualTo(1);
            // Has selector in brackets
            assertThat(text).contains("[0..100]");
        }

        @Test
        @DisplayName("empty text throws")
        void emptyThrows() {
            assertThatThrownBy(() -> Ref.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null text throws")
        void nullThrows() {
            assertThatThrownBy(() -> Ref.parse(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal refs")
        void equalRefs() {
            Ref a = Ref.of(ALICE, VERSION_1, CompoundKey.of(AUTHOR));
            Ref b = Ref.of(ALICE, VERSION_1, CompoundKey.of(AUTHOR));

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different targets are not equal")
        void differentTargets() {
            assertThat(Ref.of(ALICE)).isNotEqualTo(Ref.of(BOB));
        }

        @Test
        @DisplayName("different versions are not equal")
        void differentVersions() {
            assertThat(Ref.of(ALICE, VERSION_1)).isNotEqualTo(Ref.of(ALICE, VERSION_2));
        }

        @Test
        @DisplayName("with vs without version are not equal")
        void versionedVsNot() {
            assertThat(Ref.of(ALICE)).isNotEqualTo(Ref.of(ALICE, VERSION_1));
        }

        @Test
        @DisplayName("different frame keys are not equal")
        void differentFrameKeys() {
            assertThat(Ref.of(ALICE, CompoundKey.of(AUTHOR)))
                    .isNotEqualTo(Ref.of(ALICE, CompoundKey.of(GLOSS)));
        }

        @Test
        @DisplayName("with vs without frame are not equal")
        void frameVsNot() {
            assertThat(Ref.of(ALICE))
                    .isNotEqualTo(Ref.of(ALICE, CompoundKey.of(AUTHOR)));
        }
    }

    @Nested
    @DisplayName("Varint encoding")
    class Varint {

        @Test
        @DisplayName("single-byte varint (< 128)")
        void singleByte() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Ref.writeVarint(out, 42);
            byte[] bytes = out.toByteArray();

            assertThat(bytes).hasSize(1);
            assertThat(bytes[0]).isEqualTo((byte) 42);

            int[] result = Ref.readVarint(bytes, 0);
            assertThat(result[0]).isEqualTo(42);
            assertThat(result[1]).isEqualTo(1);
        }

        @Test
        @DisplayName("two-byte varint (128-16383)")
        void twoByte() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Ref.writeVarint(out, 200);
            byte[] bytes = out.toByteArray();

            assertThat(bytes).hasSize(2);

            int[] result = Ref.readVarint(bytes, 0);
            assertThat(result[0]).isEqualTo(200);
            assertThat(result[1]).isEqualTo(2);
        }

        @Test
        @DisplayName("zero varint")
        void zero() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Ref.writeVarint(out, 0);
            byte[] bytes = out.toByteArray();

            assertThat(bytes).hasSize(1);
            assertThat(bytes[0]).isEqualTo((byte) 0);

            int[] result = Ref.readVarint(bytes, 0);
            assertThat(result[0]).isEqualTo(0);
        }

        @Test
        @DisplayName("max single-byte varint (127)")
        void maxSingleByte() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Ref.writeVarint(out, 127);
            byte[] bytes = out.toByteArray();

            assertThat(bytes).hasSize(1);

            int[] result = Ref.readVarint(bytes, 0);
            assertThat(result[0]).isEqualTo(127);
        }

        @Test
        @DisplayName("large varint round-trip")
        void largeVarint() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Ref.writeVarint(out, 100_000);
            byte[] bytes = out.toByteArray();

            int[] result = Ref.readVarint(bytes, 0);
            assertThat(result[0]).isEqualTo(100_000);
        }
    }

    @Nested
    @DisplayName("As-entered provenance")
    class AsEntered {

        @Test
        @DisplayName("asEntered records original input")
        void recordsInput() {
            Ref ref = Ref.of(ALICE).asEntered("chess-club");

            assertThat(ref.asEntered()).isEqualTo("chess-club");
            assertThat(ref.target()).isEqualTo(ALICE);
        }

        @Test
        @DisplayName("asEntered does not affect equality")
        void doesNotAffectEquality() {
            Ref a = Ref.of(ALICE).asEntered("chess-club");
            Ref b = Ref.of(ALICE).asEntered("/games/chess");
            Ref c = Ref.of(ALICE);

            assertThat(a).isEqualTo(b);
            assertThat(a).isEqualTo(c);
            assertThat(a.hashCode()).isEqualTo(c.hashCode());
        }

        @Test
        @DisplayName("asEntered appears in toString")
        void appearsInToString() {
            Ref ref = Ref.of(ALICE).asEntered("/games/chess");

            assertThat(ref.toString()).contains("entered: /games/chess");
        }

        @Test
        @DisplayName("asEntered is not in encodeText")
        void notInEncodeText() {
            Ref ref = Ref.of(ALICE).asEntered("chess-club");

            assertThat(ref.encodeText()).doesNotContain("chess-club");
            assertThat(ref.encodeText()).doesNotContain("entered");
        }

        @Test
        @DisplayName("asEntered survives no binary round-trip (transient)")
        void transientOnBinaryRoundTrip() {
            Ref original = Ref.of(ALICE).asEntered("chess-club");
            Ref decoded = Ref.fromRefBytes(original.toRefBytes());

            assertThat(decoded.asEntered()).isNull();
            assertThat(decoded).isEqualTo(original); // still equal — asEntered excluded
        }

        @Test
        @DisplayName("null asEntered by default")
        void nullByDefault() {
            assertThat(Ref.of(ALICE).asEntered()).isNull();
        }
    }

    @Nested
    @DisplayName("Cross-format consistency")
    class CrossFormat {

        @Test
        @DisplayName("binary and CBOR produce same ref")
        void binaryMatchesCbor() {
            Ref original = Ref.of(ALICE, VERSION_1, CompoundKey.of(GLOSS, ENG));

            // Via binary
            Ref fromBinary = Ref.fromRefBytes(original.toRefBytes());

            // Via CBOR
            Ref fromCbor = Ref.fromCborTree(original.toCborTree(Canonical.Scope.BODY));

            assertThat(fromBinary).isEqualTo(fromCbor);
        }

        @Test
        @DisplayName("text and binary produce same ref")
        void textMatchesBinary() {
            Ref original = Ref.of(ALICE, VERSION_1, CompoundKey.of(CHAT, "tavern"));

            Ref fromText = Ref.parse(original.encodeText());
            Ref fromBinary = Ref.fromRefBytes(original.toRefBytes());

            assertThat(fromText).isEqualTo(fromBinary);
        }

        @Test
        @DisplayName("all three formats agree")
        void allFormatsAgree() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);
            Selector sel = Selector.byteRange(0, 1024);
            Ref original = Ref.of(BOB, VERSION_2, key, sel);

            Ref fromBinary = Ref.fromRefBytes(original.toRefBytes());
            Ref fromCbor = Ref.fromCborTree(original.toCborTree(Canonical.Scope.BODY));
            Ref fromText = Ref.parse(original.encodeText());

            assertThat(fromBinary).isEqualTo(original);
            assertThat(fromCbor).isEqualTo(original);
            assertThat(fromText).isEqualTo(original);
        }
    }
}
