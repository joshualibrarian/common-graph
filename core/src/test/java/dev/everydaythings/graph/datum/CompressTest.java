package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Compress;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import com.upokecenter.cbor.CBORObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compression preserves a body's structural identity (DatumID) across
 * compress/decompress.  The Opaque.Compressed carries the original hash plus
 * the deflated payload; the canonical walker uses the cached hash directly
 * (Node.Hashed short-circuit), so parent merkle roots are computable
 * without decompression.
 */
class CompressTest {

    private static final ItemRef HEAD = ItemRef.fromString("cg.test:doc");
    private static final ItemRef BODY_ROLE = ItemRef.iid(ThematicRole.Value.KEY);
    private static final Encoding ENCODING = CgCbor.codec();

    /** Build a sample body with some prose-y content that benefits from compression. */
    private static Body sampleBody() {
        String text = "The Hobbit, or There and Back Again, is a children's fantasy novel "
                + "by the English author J. R. R. Tolkien, published 21 September 1937, "
                + "to wide critical acclaim, nominated for the Carnegie Medal and awarded "
                + "a prize from the New York Herald Tribune for best juvenile fiction.";
        return Body.of(HEAD, List.of(
                new Binding(BODY_ROLE, text),
                new Binding(ItemRef.iid(ThematicRole.Topic.KEY), "fantasy")));
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("compress then decompress yields a body equal to the original")
        void compressDecompress() {
            Body original = sampleBody();
            Opaque.Compressed target = Compress.compress(original, ENCODING);
            Body recovered = Compress.decompress(target, ENCODING);
            assertThat(recovered).isEqualTo(original);
        }

        @Test
        @DisplayName("CompressedTarget round-trips through CBOR")
        void cborRoundTrip() {
            Body original = sampleBody();
            Opaque.Compressed target = Compress.compress(original, ENCODING);

            CBORObject encoded = CgCbor.toCbor(target);
            Object decoded = CgCbor.decodeBindingTarget(encoded);
            assertThat(decoded).isInstanceOf(Opaque.Compressed.class);
            Opaque.Compressed recoveredTarget =
                    (Opaque.Compressed) decoded;
            assertThat(recoveredTarget).isEqualTo(target);

            Body recovered = Compress.decompress(recoveredTarget, ENCODING);
            assertThat(recovered).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Hash preservation")
    class HashPreservation {

        @Test
        @DisplayName("CompressedTarget hashes to the original body's DatumID")
        void compressedHashEqualsOriginal() {
            Body original = sampleBody();
            byte[] originalHash = HashTree.hashOf(original, HashTree.DEFAULT_DIGEST);

            Opaque.Compressed target = Compress.compress(original, ENCODING);

            assertThat(target.wrappedHash()).isEqualTo(originalHash);
        }

        @Test
        @DisplayName("a parent body's DatumID is the same whether its child is compressed or inline")
        void parentHashInvariantAcrossCompression() {
            Body child = sampleBody();
            Opaque.Compressed compressedChild = Compress.compress(child, ENCODING);

            ItemRef parentHead = ItemRef.fromString("cg.test:parent");
            ItemRef childRole = ItemRef.iid(ThematicRole.Theme.KEY);

            // Parent with the child inline as a FrameTarget.
            Body parentInline = Body.of(parentHead, List.of(
                    new Binding(childRole, BindingTarget.frame(child))));
            // Parent with the child compressed.
            Body parentCompressed = Body.of(parentHead, List.of(
                    new Binding(childRole, compressedChild)));

            byte[] hashInline     = HashTree.hashOf(parentInline,     HashTree.DEFAULT_DIGEST);
            byte[] hashCompressed = HashTree.hashOf(parentCompressed, HashTree.DEFAULT_DIGEST);

            // The whole point of CompressedTarget: same parent hash either way.
            assertThat(hashCompressed).isEqualTo(hashInline);
        }

        @Test
        @DisplayName("decompressAndVerify accepts an honest payload")
        void verifyAccepts() {
            Body original = sampleBody();
            Opaque.Compressed target = Compress.compress(original, ENCODING);
            Body recovered = Compress.decompressAndVerify(target, ENCODING);
            assertThat(recovered).isEqualTo(original);
        }

        @Test
        @DisplayName("decompressAndVerify rejects a payload whose hash doesn't match the cached one")
        void verifyRejectsForgery() {
            Body original = sampleBody();
            Opaque.Compressed honest = Compress.compress(original, ENCODING);

            // Construct a forged target: real (compressed) payload, but a
            // hash claimed to belong to a different body.
            byte[] wrongHash = HashTree.hashOf(
                    Body.of(HEAD, List.of(new Binding(BODY_ROLE, "different text"))),
                    HashTree.DEFAULT_DIGEST);
            Opaque.Compressed forged =
                    new Opaque.Compressed(wrongHash, honest.compressedPayload());

            assertThatThrownBy(() -> Compress.decompressAndVerify(forged, ENCODING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hash mismatch");
        }
    }

    @Nested
    @DisplayName("Compression is actually compressing")
    class Compression {

        @Test
        @DisplayName("compressed payload is smaller than original CBOR for prose-y content")
        void actuallyCompresses() {
            Body original = sampleBody();
            byte[] cborBytes = CgCbor.codec().encode(original);
            Opaque.Compressed target = Compress.compress(original, ENCODING);
            // For our sample prose body, deflate should noticeably shrink it.
            assertThat(target.compressedPayload().length).isLessThan(cborBytes.length);
        }
    }
}
