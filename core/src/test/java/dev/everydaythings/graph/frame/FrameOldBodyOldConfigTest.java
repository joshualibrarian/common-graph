package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrameBody config map")
class FrameOldBodyOldConfigTest {

    static final ItemID PRED = ItemID.fromString("cg:test/predicate");
    static final ItemID THEME = ItemID.random();

    @Nested
    @DisplayName("Config field basics")
    class Basics {

        @Test
        @DisplayName("default constructor has empty config")
        void defaultConfigEmpty() {
            FrameBodyOld body = new FrameBodyOld(PRED, List.of());
            assertThat(body.config()).isEmpty();
        }

        @Test
        @DisplayName("explicit config is stored and accessible")
        void explicitConfig() {
            Literal lit = Literal.ofText("my-style");
            Binding configBinding = Binding.nonIdentity(
                    ThematicRole.Presentation.IID, lit);

            FrameBodyOld body = new FrameBodyOld(PRED, List.of(), List.of(configBinding));
            assertThat(body.config()).hasSize(1);
            assertThat(body.configBinding(ThematicRole.Presentation.IID)).isNotNull();
            assertThat(body.configBinding(ThematicRole.Vocabulary.IID)).isNull();
        }

        @Test
        @DisplayName("convenience constructors default to empty config")
        void convenienceConstructors() {
            assertThat(new FrameBodyOld(PRED, THEME).config()).isEmpty();
            assertThat(new FrameBodyOld(PRED, THEME, List.of()).config()).isEmpty();
            assertThat(new FrameBodyOld(PRED, THEME, java.util.Map.of()).config()).isEmpty();
        }
    }

    @Nested
    @DisplayName("withConfig builder")
    class WithConfig {

        @Test
        @DisplayName("adds config to empty body")
        void addToEmpty() {
            FrameBodyOld original = new FrameBodyOld(PRED, List.of());
            Literal lit = Literal.ofText("gold-border");

            FrameBodyOld updated = original.withConfig(
                    ThematicRole.Presentation.IID, lit);

            assertThat(original.config()).isEmpty();
            assertThat(updated.config()).hasSize(1);
            assertThat(updated.configBinding(ThematicRole.Presentation.IID))
                    .isNotNull();
        }

        @Test
        @DisplayName("replaces existing config for same qualifier")
        void replaceExisting() {
            Literal first = Literal.ofText("first");
            Literal second = Literal.ofText("second");

            FrameBodyOld body = new FrameBodyOld(PRED, List.of())
                    .withConfig(ThematicRole.Presentation.IID, first)
                    .withConfig(ThematicRole.Presentation.IID, second);

            assertThat(body.config()).hasSize(1);
            Binding b = body.configBinding(ThematicRole.Presentation.IID);
            assertThat(b.target()).isEqualTo(second);
        }

        @Test
        @DisplayName("preserves semantic bindings and body hash")
        void preservesBodyHash() {
            Binding themeBinding = FrameBodyOld.homeBinding(THEME);
            FrameBodyOld original = new FrameBodyOld(PRED, List.of(themeBinding));

            FrameBodyOld updated = original.withConfig(
                    ThematicRole.Presentation.IID, Literal.ofText("style"));

            assertThat(updated.hash()).isEqualTo(original.hash());
            assertThat(updated.frameBindings()).isEqualTo(original.frameBindings());
        }
    }

    @Nested
    @DisplayName("Config payload accessors with backward compat")
    class PayloadAccessors {

        @Test
        @DisplayName("configPresentationPayload reads from config map")
        void presentationFromConfigMap() {
            Literal lit = Literal.ofText("new-style");
            FrameBodyOld body = new FrameBodyOld(PRED, List.of(),
                    List.of(Binding.nonIdentity(ThematicRole.Presentation.IID, lit)));

            assertThat(body.configPresentationPayload()).isEqualTo(lit.payload());
        }

        @Test
        @DisplayName("configPresentationPayload falls back to compound binding")
        void presentationFallsBackToCompound() {
            Literal lit = Literal.ofText("old-style");
            FrameBodyOld body = new FrameBodyOld(PRED, List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Presentation.IID)),
                            lit, false, false)));

            assertThat(body.configPresentationPayload()).isEqualTo(lit.payload());
        }

        @Test
        @DisplayName("config map takes precedence over compound binding")
        void configMapWins() {
            Literal oldLit = Literal.ofText("old");
            Literal newLit = Literal.ofText("new");

            FrameBodyOld body = new FrameBodyOld(PRED, List.of(
                    Binding.qualified(ThematicRole.Config.IID,
                            List.of(new CompoundKey.Sememe(ThematicRole.Presentation.IID)),
                            oldLit, false, false)),
                    List.of(Binding.nonIdentity(ThematicRole.Presentation.IID, newLit)));

            assertThat(body.configPresentationPayload()).isEqualTo(newLit.payload());
        }

        @Test
        @DisplayName("configVocabularyPayload reads from config map")
        void vocabularyFromConfigMap() {
            Literal lit = Literal.ofText("vocab-tokens");
            FrameBodyOld body = new FrameBodyOld(PRED, List.of(),
                    List.of(Binding.nonIdentity(ThematicRole.Vocabulary.IID, lit)));

            assertThat(body.configVocabularyPayload()).isEqualTo(lit.payload());
        }

        @Test
        @DisplayName("configPayload reads from config map")
        void generalConfigFromConfigMap() {
            Literal lit = Literal.ofText("general-config");
            FrameBodyOld body = new FrameBodyOld(PRED, List.of(),
                    List.of(Binding.nonIdentity(ThematicRole.Config.IID, lit)));

            assertThat(body.configPayload()).isEqualTo(lit.payload());
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("body with no config round-trips as 2-element array")
        void noConfigRoundTrip() {
            FrameBodyOld original = new FrameBodyOld(PRED, THEME, List.of());

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.RECORD);

            assertThat(decoded.predicate()).isEqualTo(PRED);
            assertThat(decoded.config()).isEmpty();
        }

        @Test
        @DisplayName("body with config round-trips with config preserved")
        void configRoundTrip() {
            Literal presLit = Literal.ofText("theme-data");
            Literal vocabLit = Literal.ofText("vocab-data");

            FrameBodyOld original = new FrameBodyOld(PRED, THEME, List.of())
                    .withConfig(ThematicRole.Presentation.IID, presLit)
                    .withConfig(ThematicRole.Vocabulary.IID, vocabLit);

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.RECORD);

            assertThat(decoded.config()).hasSize(2);
            assertThat(decoded.configPresentationPayload()).isEqualTo(presLit.payload());
            assertThat(decoded.configVocabularyPayload()).isEqualTo(vocabLit.payload());
        }

        @Test
        @DisplayName("config does not affect BODY scope encoding or hash")
        void configNotInBodyScope() {
            FrameBodyOld withoutConfig = new FrameBodyOld(PRED, THEME, List.of());
            FrameBodyOld withConfig = withoutConfig.withConfig(
                    ThematicRole.Presentation.IID, Literal.ofText("style"));

            assertThat(withConfig.bodyBytes()).isEqualTo(withoutConfig.bodyBytes());
            assertThat(withConfig.hash()).isEqualTo(withoutConfig.hash());
        }
    }
}
