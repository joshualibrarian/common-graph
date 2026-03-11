package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("@Frame Annotation")
class FrameAnnotationTest {

    // ==================================================================================
    // Test fixtures — tiny Item subclasses with @Frame fields
    // ==================================================================================

    @Type("cg:test/frame-endorsed")
    static class EndorsedFrameItem extends Item {
        @Frame(handle = "vault", path = ".vault", localOnly = true)
        private String vault;

        EndorsedFrameItem() {
            super(ItemID.fromString("cg:test/endorsed-item"));
        }
    }

    @Type("cg:test/frame-semantic")
    static class SemanticFrameItem extends Item {
        @Frame(key = {"cg:pred/title"})
        private String title;

        SemanticFrameItem() {
            super(ItemID.fromString("cg:test/semantic-item"));
        }
    }

    @Type("cg:test/frame-unendorsed")
    static class UnendorsedFrameItem extends Item {
        @Frame(key = {"cg:pred/author"}, endorsed = false)
        private ItemID author;

        UnendorsedFrameItem() {
            super(ItemID.fromString("cg:test/unendorsed-item"));
        }
    }

    @Type("cg:test/frame-mixed")
    static class MixedFrameItem extends Item {
        @Frame(handle = "data")
        private String data;

        @Frame(key = {"cg:pred/likes"}, endorsed = false)
        private ItemID likes;

        MixedFrameItem() {
            super(ItemID.fromString("cg:test/mixed-item"));
        }
    }

    // ==================================================================================
    // Tests
    // ==================================================================================

    @Nested
    @DisplayName("scanning")
    class Scanning {

        @Test
        @DisplayName("endorsed frame is in frameFields and endorsedFrameFields")
        void endorsedFrame() {
            ItemSchema schema = ItemScanner.schemaFor(EndorsedFrameItem.class);

            assertThat(schema.frameFields()).hasSize(1);
            assertThat(schema.endorsedFrameFields()).hasSize(1);

            FrameFieldSpec frame = schema.frameFields().getFirst();
            assertThat(frame.endorsed()).isTrue();
            assertThat(frame.handleKey()).isEqualTo("vault");
            assertThat(frame.localOnly()).isTrue();
            assertThat(frame.frameKey().isLiteral()).isTrue();
        }

        @Test
        @DisplayName("semantic key frame has FrameKey with sememe token")
        void semanticKeyFrame() {
            ItemSchema schema = ItemScanner.schemaFor(SemanticFrameItem.class);

            assertThat(schema.frameFields()).hasSize(1);
            FrameFieldSpec frame = schema.frameFields().getFirst();
            assertThat(frame.isSemantic()).isTrue();
            assertThat(frame.frameKey().isSemantic()).isTrue();
            assertThat(frame.predicate()).isEqualTo(ItemID.fromString("cg:pred/title"));
        }

        @Test
        @DisplayName("unendorsed frame is in frameFields and unendorsedFrameFields")
        void unendorsedFrame() {
            ItemSchema schema = ItemScanner.schemaFor(UnendorsedFrameItem.class);

            assertThat(schema.frameFields()).hasSize(1);
            assertThat(schema.unendorsedFrameFields()).hasSize(1);

            FrameFieldSpec frame = schema.frameFields().getFirst();
            assertThat(frame.endorsed()).isFalse();
            assertThat(frame.predicate()).isEqualTo(ItemID.fromString("cg:pred/author"));
        }

        @Test
        @DisplayName("mixed item has both endorsed and unendorsed frames")
        void mixedItem() {
            ItemSchema schema = ItemScanner.schemaFor(MixedFrameItem.class);

            assertThat(schema.frameFields()).hasSize(2);
            assertThat(schema.endorsedFrameFields()).hasSize(1);
            assertThat(schema.unendorsedFrameFields()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("FrameFieldSpec")
    class Spec {

        @Test
        @DisplayName("endorsed frame preserves handle and path")
        void endorsedPreservesMetadata() {
            ItemSchema schema = ItemScanner.schemaFor(EndorsedFrameItem.class);
            FrameFieldSpec frame = schema.frameFields().getFirst();

            assertThat(frame.handleKey()).isEqualTo("vault");
            assertThat(frame.path()).isEqualTo(".vault");
            assertThat(frame.localOnly()).isTrue();
        }

        @Test
        @DisplayName("default handle from field name")
        void defaultHandle() {
            ItemSchema schema = ItemScanner.schemaFor(SemanticFrameItem.class);
            FrameFieldSpec frame = schema.frameFields().getFirst();

            // key specified but not handle — handleKey falls back to key[0]
            assertThat(frame.handleKey()).isEqualTo("cg:pred/title");
        }

        @Test
        @DisplayName("hasComponentFields includes endorsed frames")
        void hasComponentFieldsIncludesFrames() {
            ItemSchema schema = ItemScanner.schemaFor(EndorsedFrameItem.class);
            assertThat(schema.hasComponentFields()).isTrue();
        }

        @Test
        @DisplayName("hasRelationFields includes unendorsed frames")
        void hasRelationFieldsIncludesFrames() {
            ItemSchema schema = ItemScanner.schemaFor(UnendorsedFrameItem.class);
            assertThat(schema.hasRelationFields()).isTrue();
        }
    }
}
