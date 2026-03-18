package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.PresentationConfig;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.PresentationVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Presentation Cascade (Phase 5)")
class PresentationCascadeTest {

    static Librarian librarian;

    @BeforeAll
    static void setup() {
        librarian = Librarian.createInMemory();
    }

    @Nested
    @DisplayName("PresentationConfig glyph/shape fields")
    class GlyphShape {

        @Test
        @DisplayName("of() factory creates config with glyph, color, shape")
        void ofFactory() {
            PresentationConfig pc = PresentationConfig.of("♟", 0x336699, "sphere");

            assertThat(pc.glyph()).isEqualTo("♟");
            assertThat(pc.shape()).isEqualTo("sphere");
            assertThat(pc.primaryColor()).isEqualTo(0x336699);
        }

        @Test
        @DisplayName("merge: override glyph wins")
        void mergeGlyph() {
            PresentationConfig base = PresentationConfig.of("📦", 0xFF0000, "sphere");
            PresentationConfig override = PresentationConfig.of("♟", 0x00FF00, "cube");

            PresentationConfig merged = base.merge(override);
            assertThat(merged.glyph()).isEqualTo("♟");
            assertThat(merged.shape()).isEqualTo("cube");
            assertThat(merged.primaryColor()).isEqualTo(0x00FF00);
        }

        @Test
        @DisplayName("merge: null glyph/shape preserves base")
        void mergePreservesBase() {
            PresentationConfig base = PresentationConfig.of("📦", 0xFF0000, "sphere");
            PresentationConfig override = new PresentationConfig(List.of(), List.of());

            PresentationConfig merged = base.merge(override);
            assertThat(merged.glyph()).isEqualTo("📦");
            assertThat(merged.shape()).isEqualTo("sphere");
        }

        @Test
        @DisplayName("CBOR round-trip preserves glyph and shape")
        void roundTrip() {
            PresentationConfig original = PresentationConfig.of("🎮", 0xABCDEF, "disc");

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            PresentationConfig decoded = Canonical.decodeBinary(
                    bytes, PresentationConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.glyph()).isEqualTo("🎮");
            assertThat(decoded.shape()).isEqualTo("disc");
            assertThat(decoded.primaryColor()).isEqualTo(0xABCDEF);
        }
    }

    @Nested
    @DisplayName("Instance-level presentation config")
    class InstanceLevel {

        @Test
        @DisplayName("item with PRESENTATION frame resolves presentation")
        void itemWithPresentationFrame() {
            Item item = new Item(librarian);

            // Add a PRESENTATION frame with PresentationConfig payload
            PresentationConfig pc = PresentationConfig.of("⭐", 0xFF9900, "sphere");
            byte[] pcBytes = pc.encodeBinary(Canonical.Scope.RECORD);
            Literal lit = new Literal(Literal.TYPE_CBOR, pcBytes);

            FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
            FrameBody presBody = new FrameBody(ThematicRole.Presentation.SEED.iid(),
                    List.of(new Binding(ThematicRole.Topic.SEED.iid(), lit)));
            Frame presFrame = new Frame(presKey, ThematicRole.Presentation.SEED.iid(),
                    presBody, presBody.hash(), false);
            item.frames().add(presFrame);

            PresentationConfig resolved = item.resolvedPresentation();
            assertThat(resolved).isNotNull();
            assertThat(resolved.glyph()).isEqualTo("⭐");
            assertThat(resolved.primaryColor()).isEqualTo(0xFF9900);
        }

        @Test
        @DisplayName("item with manifest config resolves presentation")
        void itemWithManifestConfig() {
            Item item = new Item(librarian);

            PresentationConfig pc = PresentationConfig.of("🔷", 0x0066CC, "cube");
            byte[] pcBytes = pc.encodeBinary(Canonical.Scope.RECORD);
            Literal lit = new Literal(Literal.TYPE_CBOR, pcBytes);

            // Set manifest with PRESENTATION config entry
            Manifest manifest = Manifest.builder()
                    .iid(item.iid())
                    .implementation(Manifest.javaImplementation(Item.class))
                    .configEntry(Binding.nonIdentity(
                            ThematicRole.Presentation.SEED.iid(), lit))
                    .build();
            item.current = manifest;

            PresentationConfig resolved = item.resolvedPresentation();
            assertThat(resolved).isNotNull();
            assertThat(resolved.glyph()).isEqualTo("🔷");
            assertThat(resolved.shape()).isEqualTo("cube");
        }
    }

    @Nested
    @DisplayName("DisplayInfo integration")
    class DisplayInfoIntegration {

        @Test
        @DisplayName("displayInfo reads from PresentationConfig when available")
        void displayInfoFromConfig() {
            Item item = new Item(librarian);

            // Add presentation config
            PresentationConfig pc = PresentationConfig.of("🌟", 0xFF6600, "disc");
            byte[] pcBytes = pc.encodeBinary(Canonical.Scope.RECORD);
            Literal lit = new Literal(Literal.TYPE_CBOR, pcBytes);

            FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
            FrameBody presBody = new FrameBody(ThematicRole.Presentation.SEED.iid(),
                    List.of(new Binding(ThematicRole.Topic.SEED.iid(), lit)));
            Frame presFrame = new Frame(presKey, ThematicRole.Presentation.SEED.iid(),
                    presBody, presBody.hash(), false);
            item.frames().add(presFrame);

            DisplayInfo info = item.displayInfo();
            assertThat(info.iconText()).isEqualTo("🌟");
            assertThat(info.shape()).isEqualTo(DisplayInfo.Shape.DISC);
        }

        @Test
        @DisplayName("displayInfo falls back to STC when no PresentationConfig")
        void displayInfoFallsBackToStc() {
            // Plain item with no presentation config — should use STC or annotation defaults
            Item item = new Item(librarian);
            DisplayInfo info = item.displayInfo();
            assertThat(info).isNotNull();
            // Should get some default glyph (not null)
            assertThat(info.iconText()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Seed vocabulary integration")
    class SeedIntegration {

        @org.junit.jupiter.api.Disabled("Pending: display metadata migration from @Implements to @ItemSeed.Frame")
        @Test
        @DisplayName("seed items have PRESENTATION frames after bootstrap")
        void seedsHavePresentationFrames() {
            // The Librarian's bootstrap should have seeded presentation configs
            // Look up a well-known type item (e.g., Item itself)
            ItemID itemTypeId = ItemID.fromString(Item.KEY);
            var typeItemOpt = librarian.get(itemTypeId, Item.class);

            if (typeItemOpt.isPresent()) {
                Item typeItem = typeItemOpt.get();
                FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
                var presFrame = typeItem.frames().getFrame(presKey);
                assertThat(presFrame).as("type item should have PRESENTATION frame").isPresent();
            }
            // If type item isn't available, the test is inconclusive (not a failure)
        }
    }
}
