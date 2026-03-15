package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Config Cascade — (CONFIG, PRESENTATION), (CONFIG, VOCABULARY)")
class ConfigCascadeTest {

    static final ItemID AUTHOR_PRED = ItemID.fromString("cg:pred/author");

    static Librarian librarian;

    @BeforeAll
    static void setup() {
        librarian = Librarian.createInMemory();
    }

    @Nested
    @DisplayName("ThematicRole seeds")
    class Seeds {

        @Test
        @DisplayName("Presentation role is seeded")
        void presentationSeeded() {
            assertThat(ThematicRole.Presentation.SEED).isNotNull();
            assertThat(ThematicRole.Presentation.SEED.iid()).isNotNull();
            assertThat(ThematicRole.fromName("PRESENTATION")).isSameAs(ThematicRole.Presentation.SEED);
        }

        @Test
        @DisplayName("Vocabulary role is seeded")
        void vocabularySeeded() {
            assertThat(ThematicRole.Vocabulary.SEED).isNotNull();
            assertThat(ThematicRole.Vocabulary.SEED.iid()).isNotNull();
            assertThat(ThematicRole.fromName("VOCABULARY")).isSameAs(ThematicRole.Vocabulary.SEED);
        }

        @Test
        @DisplayName("Config role still works")
        void configSeeded() {
            assertThat(ThematicRole.Config.SEED).isNotNull();
            assertThat(ThematicRole.fromName("CONFIG")).isSameAs(ThematicRole.Config.SEED);
        }
    }

    @Nested
    @DisplayName("Compound bindings on FrameBody")
    class CompoundBindings {

        @Test
        @DisplayName("(CONFIG, PRESENTATION) compound binding is readable")
        void configPresentation() {
            Literal presentationLit = Literal.ofText("gold-border");

            FrameBody body = new FrameBody(AUTHOR_PRED, ItemID.random(), List.of(
                    Binding.compound(
                            List.of(ThematicRole.Config.SEED.iid(),
                                    ThematicRole.Presentation.SEED.iid()),
                            presentationLit, false, false)
            ));

            assertThat(body.configPresentationPayload()).isNotNull();
            assertThat(body.configVocabularyPayload()).isNull();
        }

        @Test
        @DisplayName("(CONFIG, VOCABULARY) compound binding is readable")
        void configVocabulary() {
            Literal vocabLit = Literal.ofText("tolkien");

            FrameBody body = new FrameBody(AUTHOR_PRED, ItemID.random(), List.of(
                    Binding.compound(
                            List.of(ThematicRole.Config.SEED.iid(),
                                    ThematicRole.Vocabulary.SEED.iid()),
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
            Item item = new Item(librarian);

            Literal lit = Literal.ofText("per-frame-style");

            FrameBody body = new FrameBody(AUTHOR_PRED, item.iid(), List.of(
                    Binding.compound(
                            List.of(ThematicRole.Config.SEED.iid(),
                                    ThematicRole.Presentation.SEED.iid()),
                            lit, false, false)
            ));

            Frame frame = new Frame(
                    FrameKey.literal("author"),
                    AUTHOR_PRED, body, body.hash(), false);

            byte[] resolved = item.resolvePresentation(frame);
            assertThat(resolved).isNotNull();
        }

        @Test
        @DisplayName("step 2: falls through to (PRESENTATION) frame on the item")
        void step2_itemFrame() {
            Item item = new Item(librarian);

            // Add a (PRESENTATION) frame on the item with a TOPIC binding
            Literal topicLit = Literal.ofText("item-level-theme");

            FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
            FrameBody presBody = new FrameBody(
                    ThematicRole.Presentation.SEED.iid(), item.iid(),
                    List.of(new Binding(ThematicRole.Topic.SEED.iid(), topicLit)));
            Frame presFrame = new Frame(presKey,
                    ThematicRole.Presentation.SEED.iid(), presBody, presBody.hash(), false);
            item.frames().add(presFrame);

            // Query frame has NO config binding — should cascade to item
            FrameBody authorBody = new FrameBody(AUTHOR_PRED, item.iid(), List.of());
            Frame authorFrame = new Frame(
                    FrameKey.literal("author"),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            byte[] resolved = item.resolvePresentation(authorFrame);
            assertThat(resolved).as("should cascade to item-level (PRESENTATION) frame").isNotNull();
        }

        @Test
        @DisplayName("step 1 wins over step 2 — per-frame overrides item-level")
        void step1WinsOverStep2() {
            Item item = new Item(librarian);

            // Add item-level (PRESENTATION)
            Literal itemLit = Literal.ofText("item-level");
            FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
            FrameBody presBody = new FrameBody(
                    ThematicRole.Presentation.SEED.iid(), item.iid(),
                    List.of(new Binding(ThematicRole.Topic.SEED.iid(), itemLit)));
            item.frames().add(new Frame(presKey,
                    ThematicRole.Presentation.SEED.iid(), presBody, presBody.hash(), false));

            // Frame with its own (CONFIG, PRESENTATION) — should win
            Literal frameLit = Literal.ofText("per-frame-override");
            FrameBody authorBody = new FrameBody(AUTHOR_PRED, item.iid(), List.of(
                    Binding.compound(
                            List.of(ThematicRole.Config.SEED.iid(),
                                    ThematicRole.Presentation.SEED.iid()),
                            frameLit, false, false)
            ));
            Frame authorFrame = new Frame(
                    FrameKey.literal("author"),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            byte[] resolved = item.resolvePresentation(authorFrame);
            // Should get the per-frame override, not the item-level
            assertThat(resolved).isEqualTo(frameLit.payload());
        }

        @Test
        @DisplayName("returns null when no config exists at any level")
        void noConfigReturnsNull() {
            Item item = new Item(librarian);

            FrameBody body = new FrameBody(AUTHOR_PRED, item.iid(), List.of());
            Frame frame = new Frame(
                    FrameKey.literal("author"),
                    AUTHOR_PRED, body, body.hash(), false);

            assertThat(item.resolvePresentation(frame)).isNull();
            assertThat(item.resolveVocabulary(frame)).isNull();
        }

        @Test
        @DisplayName("resolveGeneralConfig finds (CONFIG) simple binding on frame")
        void generalConfigFromFrame() {
            Item item = new Item(librarian);

            Literal lit = Literal.ofText("general-settings");

            FrameBody body = new FrameBody(AUTHOR_PRED, item.iid(), List.of(
                    Binding.nonIdentity(ThematicRole.Config.SEED.iid(), lit)
            ));
            Frame frame = new Frame(
                    FrameKey.literal("author"),
                    AUTHOR_PRED, body, body.hash(), false);

            byte[] resolved = item.resolveGeneralConfig(frame);
            assertThat(resolved).isEqualTo(lit.payload());
        }

        @Test
        @DisplayName("vocabulary cascade works independently from presentation")
        void vocabularyCascade() {
            Item item = new Item(librarian);

            // Add item-level (VOCABULARY) frame
            Literal vocabLit = Literal.ofText("item-vocab-tokens");
            FrameKey vocabKey = FrameKey.of(ThematicRole.Vocabulary.SEED.iid());
            FrameBody vocabBody = new FrameBody(
                    ThematicRole.Vocabulary.SEED.iid(), item.iid(),
                    List.of(new Binding(ThematicRole.Topic.SEED.iid(), vocabLit)));
            item.frames().add(new Frame(vocabKey,
                    ThematicRole.Vocabulary.SEED.iid(), vocabBody, vocabBody.hash(), false));

            // Frame has no vocab config
            FrameBody authorBody = new FrameBody(AUTHOR_PRED, item.iid(), List.of());
            Frame authorFrame = new Frame(
                    FrameKey.literal("author"),
                    AUTHOR_PRED, authorBody, authorBody.hash(), false);

            // Vocabulary should cascade to item, presentation should be null
            assertThat(item.resolveVocabulary(authorFrame)).isNotNull();
            assertThat(item.resolvePresentation(authorFrame)).isNull();
        }
    }
}
