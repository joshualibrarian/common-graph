package dev.everydaythings.graph.language;

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
        @DisplayName("Java seed has deterministic IID")
        void javaSeed() {
            assertThat(RuntimeVocabulary.Java.SEED).isNotNull();
            assertThat(RuntimeVocabulary.Java.SEED.iid())
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.Java.KEY));
        }

        @Test
        @DisplayName("Python seed has deterministic IID")
        void pythonSeed() {
            assertThat(RuntimeVocabulary.Python.SEED).isNotNull();
            assertThat(RuntimeVocabulary.Python.SEED.iid())
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.Python.KEY));
        }

        @Test
        @DisplayName("JavaScript seed has deterministic IID")
        void jsSeed() {
            assertThat(RuntimeVocabulary.JavaScript.SEED).isNotNull();
            assertThat(RuntimeVocabulary.JavaScript.SEED.iid())
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.JavaScript.KEY));
        }

        @Test
        @DisplayName("Rust seed has deterministic IID")
        void rustSeed() {
            assertThat(RuntimeVocabulary.Rust.SEED).isNotNull();
            assertThat(RuntimeVocabulary.Rust.SEED.iid())
                    .isEqualTo(ItemID.fromString(RuntimeVocabulary.Rust.KEY));
        }

        @Test
        @DisplayName("all language seeds have distinct IIDs")
        void allDistinct() {
            assertThat(RuntimeVocabulary.Java.SEED.iid())
                    .isNotEqualTo(RuntimeVocabulary.Python.SEED.iid())
                    .isNotEqualTo(RuntimeVocabulary.JavaScript.SEED.iid())
                    .isNotEqualTo(RuntimeVocabulary.Rust.SEED.iid());
        }
    }

    @Nested
    @DisplayName("PresentationVocabulary")
    class Presentation {

        @Test
        @DisplayName("Primary seed has deterministic IID")
        void primarySeed() {
            assertThat(PresentationVocabulary.Primary.SEED).isNotNull();
            assertThat(PresentationVocabulary.Primary.SEED.iid())
                    .isEqualTo(ItemID.fromString(PresentationVocabulary.Primary.KEY));
        }

        @Test
        @DisplayName("all palette seeds are non-null and distinct")
        void allPaletteSeedsDistinct() {
            ItemID[] ids = {
                    PresentationVocabulary.Primary.SEED.iid(),
                    PresentationVocabulary.Secondary.SEED.iid(),
                    PresentationVocabulary.Accent.SEED.iid(),
                    PresentationVocabulary.Surface.SEED.iid(),
                    PresentationVocabulary.OnPrimary.SEED.iid(),
                    PresentationVocabulary.OnSurface.SEED.iid(),
                    PresentationVocabulary.Error.SEED.iid(),
                    PresentationVocabulary.Outline.SEED.iid(),
                    PresentationVocabulary.Muted.SEED.iid()
            };

            for (ItemID id : ids) {
                assertThat(id).isNotNull();
            }

            // All distinct
            assertThat(ids).doesNotHaveDuplicates();
        }
    }
}
