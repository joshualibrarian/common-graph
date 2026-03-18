package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Vocabulary seed determinism")
class VocabularySeedTest {

    @Nested
    @DisplayName("RuntimeVocabulary")
    class Runtime {

        @Test
        @DisplayName("Java seed has deterministic IID and @ItemSeed annotation")
        void javaSeed() {
            assertThat(RuntimeVocabulary.Java.class).hasAnnotation(ItemSeed.class);
            assertThat(RuntimeVocabulary.Java.IID)
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.Java.KEY));
        }

        @Test
        @DisplayName("Python seed has deterministic IID and @ItemSeed annotation")
        void pythonSeed() {
            assertThat(RuntimeVocabulary.Python.class).hasAnnotation(ItemSeed.class);
            assertThat(RuntimeVocabulary.Python.IID)
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.Python.KEY));
        }

        @Test
        @DisplayName("JavaScript seed has deterministic IID and @ItemSeed annotation")
        void jsSeed() {
            assertThat(RuntimeVocabulary.JavaScript.class).hasAnnotation(ItemSeed.class);
            assertThat(RuntimeVocabulary.JavaScript.IID)
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.JavaScript.KEY));
        }

        @Test
        @DisplayName("Rust seed has deterministic IID and @ItemSeed annotation")
        void rustSeed() {
            assertThat(RuntimeVocabulary.Rust.class).hasAnnotation(ItemSeed.class);
            assertThat(RuntimeVocabulary.Rust.IID)
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.Rust.KEY));
        }

        @Test
        @DisplayName("all language seeds have distinct IIDs")
        void allDistinct() {
            assertThat(RuntimeVocabulary.Java.IID)
                    .isNotEqualTo(RuntimeVocabulary.Python.IID)
                    .isNotEqualTo(RuntimeVocabulary.JavaScript.IID)
                    .isNotEqualTo(RuntimeVocabulary.Rust.IID);
        }
    }

    @Nested
    @DisplayName("PresentationVocabulary")
    class Presentation {

        @Test
        @DisplayName("Primary seed has deterministic IID and @ItemSeed annotation")
        void primarySeed() {
            assertThat(PresentationVocabulary.Primary.class).hasAnnotation(ItemSeed.class);
            assertThat(PresentationVocabulary.Primary.IID)
                    .isEqualTo(ItemID.fromString(PresentationVocabulary.Primary.KEY));
        }

        @Test
        @DisplayName("all palette seeds are non-null and distinct")
        void allPaletteSeedsDistinct() {
            ItemID[] ids = {
                    PresentationVocabulary.Primary.IID,
                    PresentationVocabulary.Secondary.IID,
                    PresentationVocabulary.Accent.IID,
                    PresentationVocabulary.Surface.IID,
                    PresentationVocabulary.OnPrimary.IID,
                    PresentationVocabulary.OnSurface.IID,
                    PresentationVocabulary.Error.IID,
                    PresentationVocabulary.Outline.IID,
                    PresentationVocabulary.Muted.IID
            };

            for (ItemID id : ids) {
                assertThat(id).isNotNull();
            }

            // All distinct
            assertThat(ids).doesNotHaveDuplicates();
        }
    }
}
