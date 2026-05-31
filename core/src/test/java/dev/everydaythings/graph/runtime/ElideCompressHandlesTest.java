package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.cryptography.OpaqueOpsVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import io.ipfs.multihash.Multihash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Librarian-as-handler for {@link OpaqueOpsVocabulary.Elide ELIDE} and
 * {@link OpaqueOpsVocabulary.Compress COMPRESS}: command frames whose THEME
 * is an inline {@link Body} produce a result frame whose THEME is the
 * corresponding {@link Opaque} form, with a record signing the request.
 */
@DisplayName("Elide / Compress command handlers on Librarian")
class ElideCompressHandlesTest {

    /** A small body to elide/compress: a generic predicate with one string binding. */
    private static Body sampleSource() {
        return Body.of(
                ItemRef.of(ItemRef.fromString("cg.predicate:test-note")),
                List.of(new Binding(
                        ItemRef.iid(ThematicRole.Value.KEY),
                        "the dossier contents")));
    }

    private static Body command(String predicateKey, Body source) {
        return Body.of(
                ItemRef.of(ItemRef.iid(predicateKey)),
                List.of(new Binding(ItemRef.iid(ThematicRole.Theme.KEY), source)));
    }

    @Nested
    @DisplayName("Elide")
    class Elide {

        @Test
        @DisplayName("returns a frame whose THEME is an Opaque.Redacted preserving the source's hash")
        void redactsBody() {
            Librarian lib = Librarian.inMemory();
            Body source = sampleSource();
            Frame request = Frame.of(command(OpaqueOpsVocabulary.Elide.KEY, source), List.of());

            Frame result = lib.handleElide(request);

            assertThat(result.body().headRef())
                    .isEqualTo(ItemRef.iid(OpaqueOpsVocabulary.Elide.KEY));
            Object themeTarget = result.body()
                    .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .map(Binding::target).orElseThrow();
            assertThat(themeTarget).isInstanceOf(Opaque.Redacted.class);

            Opaque.Redacted opaque = (Opaque.Redacted) themeTarget;
            byte[] expected = HashTree.hashOf(source, Multihash.Type.sha2_256);
            assertThat(opaque.wrappedHash())
                    .as("wrappedHash equals the source body's structural hash")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("the result frame carries a record signing the request body")
        void recordSignsRequest() {
            Librarian lib = Librarian.inMemory();
            Frame request = Frame.of(
                    command(OpaqueOpsVocabulary.Elide.KEY, sampleSource()), List.of());

            Frame result = lib.handleElide(request);

            assertThat(result.records()).hasSize(1);
            assertThat(result.records().get(0).head())
                    .as("record head = the request body's DatumRef")
                    .isEqualTo(request.body().datumId());
        }

        @Test
        @DisplayName("a command without THEME is rejected")
        void rejectsMissingTheme() {
            Librarian lib = Librarian.inMemory();
            Body bare = Body.of(
                    ItemRef.of(ItemRef.iid(OpaqueOpsVocabulary.Elide.KEY)), List.of());
            assertThatThrownBy(() -> lib.handleElide(Frame.of(bare, List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("THEME");
        }
    }

    @Nested
    @DisplayName("Compress")
    class Compress {

        @Test
        @DisplayName("returns a frame whose THEME is an Opaque.Compressed with the source's hash and a deflated payload")
        void compressesBody() {
            Librarian lib = Librarian.inMemory();
            Body source = sampleSource();
            Frame request = Frame.of(command(OpaqueOpsVocabulary.Compress.KEY, source), List.of());

            Frame result = lib.handleCompress(request);

            assertThat(result.body().headRef())
                    .isEqualTo(ItemRef.iid(OpaqueOpsVocabulary.Compress.KEY));
            Object themeTarget = result.body()
                    .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .map(Binding::target).orElseThrow();
            assertThat(themeTarget).isInstanceOf(Opaque.Compressed.class);

            Opaque.Compressed opaque = (Opaque.Compressed) themeTarget;
            byte[] expected = HashTree.hashOf(source, Multihash.Type.sha2_256);
            assertThat(opaque.wrappedHash())
                    .as("wrappedHash equals the source body's structural hash")
                    .isEqualTo(expected);
            assertThat(opaque.compressedPayload())
                    .as("compressed payload is non-empty")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("the compressed payload decompresses back to the source")
        void roundTrips() {
            Librarian lib = Librarian.inMemory();
            Body source = sampleSource();
            Frame request = Frame.of(command(OpaqueOpsVocabulary.Compress.KEY, source), List.of());

            Frame result = lib.handleCompress(request);
            Opaque.Compressed opaque = (Opaque.Compressed) result.body()
                    .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .map(Binding::target).orElseThrow();

            Body restored = dev.everydaythings.graph.encoding.Compress
                    .decompress(opaque, lib.encoder().orElseThrow());
            assertThat(restored).isEqualTo(source);
        }

        @Test
        @DisplayName("a command without THEME is rejected")
        void rejectsMissingTheme() {
            Librarian lib = Librarian.inMemory();
            Body bare = Body.of(
                    ItemRef.of(ItemRef.iid(OpaqueOpsVocabulary.Compress.KEY)), List.of());
            assertThatThrownBy(() -> lib.handleCompress(Frame.of(bare, List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("THEME");
        }
    }

    @Nested
    @DisplayName("Decompress")
    class Decompress {

        @Test
        @DisplayName("recovers the original body from an Opaque.Compressed")
        void recoversBody() {
            Librarian lib = Librarian.inMemory();
            Body source = sampleSource();

            // First compress to get an Opaque.Compressed.
            Frame compressResult = lib.handleCompress(
                    Frame.of(command(OpaqueOpsVocabulary.Compress.KEY, source), List.of()));
            Opaque.Compressed compressed = (Opaque.Compressed) compressResult.body()
                    .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .map(Binding::target).orElseThrow();

            // Now decompress through the command path.
            Body decompressBody = Body.of(
                    ItemRef.of(ItemRef.iid(OpaqueOpsVocabulary.Decompress.KEY)),
                    List.of(new Binding(
                            ItemRef.iid(ThematicRole.Theme.KEY), compressed)));
            Frame result = lib.handleDecompress(Frame.of(decompressBody, List.of()));

            assertThat(result.body().headRef())
                    .isEqualTo(ItemRef.iid(OpaqueOpsVocabulary.Decompress.KEY));
            Object themeTarget = result.body()
                    .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .map(Binding::target).orElseThrow();
            assertThat(themeTarget)
                    .as("THEME is the recovered Body")
                    .isEqualTo(source);
        }

        @Test
        @DisplayName("a command without THEME is rejected")
        void rejectsMissingTheme() {
            Librarian lib = Librarian.inMemory();
            Body bare = Body.of(
                    ItemRef.of(ItemRef.iid(OpaqueOpsVocabulary.Decompress.KEY)), List.of());
            assertThatThrownBy(() -> lib.handleDecompress(Frame.of(bare, List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("THEME");
        }
    }
}
