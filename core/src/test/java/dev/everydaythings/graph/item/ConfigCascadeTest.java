package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Config Cascade — (CONFIG, PRESENTATION), (CONFIG, VOCABULARY)")
class ConfigCascadeTest {

    static final ItemID AUTHOR_PRED = ItemID.fromString("cg:pred/author");

    static LibrarianOld librarian;

    @BeforeAll
    static void setup() {
        librarian = LibrarianOld.createInMemory();
    }

    @Nested
    @DisplayName("ThematicRole seeds")
    class Seeds {

        @Test
        @DisplayName("Presentation role is seeded")
        void presentationSeeded() {
            assertThat(ThematicRole.Presentation.IID).isNotNull();
        }

        @Test
        @DisplayName("Vocabulary role is seeded")
        void vocabularySeeded() {
            assertThat(ThematicRole.Vocabulary.IID).isNotNull();
        }

        @Test
        @DisplayName("Config role still works")
        void configSeeded() {
            assertThat(ThematicRole.Config.IID).isNotNull();
        }
    }

    @Nested
    @DisplayName("Compound bindings on FrameBody")
    class CompoundBindings {

        @Test
        @DisplayName("(CONFIG, PRESENTATION) compound binding is readable")
        void configPresentation() {
            Literal presentationLit = Literal.ofText("gold-border");

            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, ItemID.random(), List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Presentation.IID)),
                            presentationLit, false, false)
            ));

            assertThat(body.configPresentationPayload()).isNotNull();
            assertThat(body.configVocabularyPayload()).isNull();
        }

        @Test
        @DisplayName("(CONFIG, VOCABULARY) compound binding is readable")
        void configVocabulary() {
            Literal vocabLit = Literal.ofText("tolkien");

            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, ItemID.random(), List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Vocabulary.IID)),
                            vocabLit, false, false)
            ));

            assertThat(body.configVocabularyPayload()).isNotNull();
            assertThat(body.configPresentationPayload()).isNull();
        }
    }

    @Nested
    @DisplayName("Cascade resolution")
    class Cascade {

        @Test
        @DisplayName("step 1: finds (CONFIG, PRESENTATION) on the frame body")
        void step1_frameBinding() {
            ItemOld item = new ItemOld(librarian);

            Literal lit = Literal.ofText("per-frame-style");

            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Presentation.IID)),
                            lit, false, false)
            ));

            FrameOld frame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, body, body.hash(), false);

            byte[] resolved = item.resolvePresentation(frame);
            assertThat(resolved).isNotNull();
        }

        @Test
        @DisplayName("step 2: falls through to (PRESENTATION) frame on the item")
        void step2_itemFrame() {
            ItemOld item = new ItemOld(librarian);

            // Add a (PRESENTATION) frame on the item with a TOPIC binding
            Literal topicLit = Literal.ofText("item-level-theme");

            CompoundKey presKey = CompoundKey.of(ThematicRole.Presentation.IID);
            FrameBodyOld presBody = new FrameBodyOld(
                    ThematicRole.Presentation.IID, item.iid(),
                    List.of(new Binding(ThematicRole.Topic.IID, topicLit)));
            FrameOld presFrame = new FrameOld(presKey,
                    ThematicRole.Presentation.IID, presBody, presBody.hash(), false);
            item.frames().add(presFrame);

            // Query frame has NO config binding — should cascade to item
            FrameBodyOld authorBody = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of());
            FrameOld authorFrame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            byte[] resolved = item.resolvePresentation(authorFrame);
            assertThat(resolved).as("should cascade to item-level (PRESENTATION) frame").isNotNull();
        }

        @Test
        @DisplayName("step 1 wins over step 2 — per-frame overrides item-level")
        void step1WinsOverStep2() {
            ItemOld item = new ItemOld(librarian);

            // Add item-level (PRESENTATION)
            Literal itemLit = Literal.ofText("item-level");
            CompoundKey presKey = CompoundKey.of(ThematicRole.Presentation.IID);
            FrameBodyOld presBody = new FrameBodyOld(
                    ThematicRole.Presentation.IID, item.iid(),
                    List.of(new Binding(ThematicRole.Topic.IID, itemLit)));
            item.frames().add(new FrameOld(presKey,
                    ThematicRole.Presentation.IID, presBody, presBody.hash(), false));

            // Frame with its own (CONFIG, PRESENTATION) — should win
            Literal frameLit = Literal.ofText("per-frame-override");
            FrameBodyOld authorBody = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Presentation.IID)),
                            frameLit, false, false)
            ));
            FrameOld authorFrame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            byte[] resolved = item.resolvePresentation(authorFrame);
            // Should get the per-frame override, not the item-level
            assertThat(resolved).isEqualTo(frameLit.payload());
        }

        @Test
        @DisplayName("returns null when no config exists at any level")
        void noConfigReturnsNull() {
            ItemOld item = new ItemOld(librarian);

            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of());
            FrameOld frame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, body, body.hash(), false);

            assertThat(item.resolvePresentation(frame)).isNull();
            assertThat(item.resolveVocabulary(frame)).isNull();
        }

        @Test
        @DisplayName("resolveGeneralConfig finds (CONFIG) simple binding on frame")
        void generalConfigFromFrame() {
            ItemOld item = new ItemOld(librarian);

            Literal lit = Literal.ofText("general-settings");

            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of(
                    Binding.nonIdentity(ThematicRole.Config.IID, lit)
            ));
            FrameOld frame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, body, body.hash(), false);

            byte[] resolved = item.resolveGeneralConfig(frame);
            assertThat(resolved).isEqualTo(lit.payload());
        }

        @Test
        @DisplayName("step 1: finds PRESENTATION in config map (new path)")
        void step1_configMap() {
            ItemOld item = new ItemOld(librarian);

            Literal lit = Literal.ofText("config-map-style");

            // Use the new config map path — direct PRESENTATION key
            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of())
                    .withConfig(ThematicRole.Presentation.IID, lit);

            FrameOld frame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, body, body.hash(), false);

            byte[] resolved = item.resolvePresentation(frame);
            assertThat(resolved).isEqualTo(lit.payload());
        }

        @Test
        @DisplayName("config map takes precedence over compound binding")
        void configMapWinsOverCompound() {
            ItemOld item = new ItemOld(librarian);

            Literal compoundLit = Literal.ofText("compound-style");
            Literal configMapLit = Literal.ofText("config-map-style");

            // Build body with both old compound binding AND new config map entry
            FrameBodyOld body = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Presentation.IID)),
                            compoundLit, false, false)))
                    .withConfig(ThematicRole.Presentation.IID, configMapLit);

            FrameOld frame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, body, body.hash(), false);

            byte[] resolved = item.resolvePresentation(frame);
            assertThat(resolved).as("config map should win over compound binding")
                    .isEqualTo(configMapLit.payload());
        }

        @Test
        @DisplayName("vocabulary cascade works independently from presentation")
        void vocabularyCascade() {
            ItemOld item = new ItemOld(librarian);

            // Add item-level (VOCABULARY) frame
            Literal vocabLit = Literal.ofText("item-vocab-tokens");
            CompoundKey vocabKey = CompoundKey.of(ThematicRole.Vocabulary.IID);
            FrameBodyOld vocabBody = new FrameBodyOld(
                    ThematicRole.Vocabulary.IID, item.iid(),
                    List.of(new Binding(ThematicRole.Topic.IID, vocabLit)));
            item.frames().add(new FrameOld(vocabKey,
                    ThematicRole.Vocabulary.IID, vocabBody, vocabBody.hash(), false));

            // Frame has no vocab config
            FrameBodyOld authorBody = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of());
            FrameOld authorFrame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            // Vocabulary should cascade to item, presentation should be null
            assertThat(item.resolveVocabulary(authorFrame)).isNotNull();
            assertThat(item.resolvePresentation(authorFrame)).isNull();
        }

        @Test
        @DisplayName("step 2: manifest config wins over item-level frame")
        void step2_manifestConfig() {
            ItemOld item = new ItemOld(librarian);

            // Set a manifest with presentation config
            Literal manifestLit = Literal.ofText("manifest-level-theme");
            ManifestOld manifest = ManifestOld.builder()
                    .iid(item.iid())
                    .binding(Binding.nonIdentity(
                            ThematicRole.Presentation.IID, manifestLit))
                    .build();
            // Inject manifest via reflection (normally set during commit/hydrate)
            item.current = manifest;

            // Also add item-level (PRESENTATION) frame — should lose to manifest config
            Literal frameLit = Literal.ofText("frame-level-theme");
            CompoundKey presKey = CompoundKey.of(ThematicRole.Presentation.IID);
            FrameBodyOld presBody = new FrameBodyOld(
                    ThematicRole.Presentation.IID, item.iid(),
                    List.of(new Binding(ThematicRole.Topic.IID, frameLit)));
            item.frames().add(new FrameOld(presKey,
                    ThematicRole.Presentation.IID, presBody, presBody.hash(), false));

            // Query frame has NO config binding — should cascade to manifest config
            FrameBodyOld authorBody = new FrameBodyOld(AUTHOR_PRED, item.iid(), List.of());
            FrameOld authorFrame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:author")),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            byte[] resolved = item.resolvePresentation(authorFrame);
            assertThat(resolved).as("manifest config should win over item-level frame")
                    .isEqualTo(manifestLit.payload());
        }
    }
}
