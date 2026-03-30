package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Manifest config map")
class ManifestConfigTest {

    static final Binding TEST_IMPL = Manifest.javaImplementation(Item.class);

    @Nested
    @DisplayName("Builder and accessors")
    class BuilderAndAccessors {

        @Test
        @DisplayName("manifest with no config has empty config list")
        void noConfig() {
            Manifest m = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .build();

            assertThat(m.nonIdentityBindings()).isEmpty();
            assertThat(m.nonIdentityBinding(ThematicRole.Presentation.IID)).isNull();
        }

        @Test
        @DisplayName("config entries are accessible via builder")
        void withConfig() {
            Literal presLit = Literal.ofText("item-theme");
            Binding presBinding = Binding.nonIdentity(
                    ThematicRole.Presentation.IID, presLit);

            Manifest m = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .binding(presBinding)
                    .build();

            assertThat(m.nonIdentityBindings()).hasSize(1);
            assertThat(m.nonIdentityBinding(ThematicRole.Presentation.IID)).isNotNull();
            assertThat(m.nonIdentityBinding(ThematicRole.Vocabulary.IID)).isNull();
        }

        @Test
        @DisplayName("multiple config entries")
        void multipleEntries() {
            Literal presLit = Literal.ofText("theme");
            Literal vocabLit = Literal.ofText("vocab");

            Manifest m = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .binding(Binding.nonIdentity(
                            ThematicRole.Presentation.IID, presLit))
                    .binding(Binding.nonIdentity(
                            ThematicRole.Vocabulary.IID, vocabLit))
                    .build();

            assertThat(m.nonIdentityBindings()).hasSize(2);
            assertThat(m.nonIdentityBinding(ThematicRole.Presentation.IID)).isNotNull();
            assertThat(m.nonIdentityBinding(ThematicRole.Vocabulary.IID)).isNotNull();
        }

        @Test
        @DisplayName("config via list setter")
        void configViaList() {
            Literal lit = Literal.ofText("data");

            Manifest m = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .bindings(List.of(
                            Binding.nonIdentity(ThematicRole.Presentation.IID, lit)))
                    .build();

            assertThat(m.nonIdentityBindings()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("VID stability")
    class VidStability {

        @Test
        @DisplayName("config does not affect VID")
        void configDoesNotAffectVid() {
            ItemID iid = ItemID.random();

            Manifest without = Manifest.builder()
                    .iid(iid)
                    .implementation(TEST_IMPL)
                    .build();

            Manifest with = Manifest.builder()
                    .iid(iid)
                    .implementation(TEST_IMPL)
                    .binding(Binding.nonIdentity(
                            ThematicRole.Presentation.IID,
                            Literal.ofText("theme-data")))
                    .build();

            assertThat(with.vid()).isEqualTo(without.vid());
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("manifest with no config round-trips")
        void noConfigRoundTrip() {
            Manifest original = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .build();

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            Manifest decoded = Manifest.decode(bytes);

            assertThat(decoded.iid()).isEqualTo(original.iid());
            assertThat(decoded.vid()).isEqualTo(original.vid());
            assertThat(decoded.nonIdentityBindings()).isEmpty();
        }

        @Test
        @DisplayName("manifest with config round-trips")
        void withConfigRoundTrip() {
            Literal presLit = Literal.ofText("theme-data");
            Literal vocabLit = Literal.ofText("vocab-data");

            Manifest original = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .binding(Binding.nonIdentity(
                            ThematicRole.Presentation.IID, presLit))
                    .binding(Binding.nonIdentity(
                            ThematicRole.Vocabulary.IID, vocabLit))
                    .build();

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            Manifest decoded = Manifest.decode(bytes);

            assertThat(decoded.iid()).isEqualTo(original.iid());
            assertThat(decoded.vid()).isEqualTo(original.vid());
            assertThat(decoded.nonIdentityBindings()).hasSize(2);

            Binding pres = decoded.nonIdentityBinding(ThematicRole.Presentation.IID);
            assertThat(pres).isNotNull();
            assertThat(pres.target()).isInstanceOf(Literal.class);
            assertThat(((Literal) pres.target()).payload()).isEqualTo(presLit.payload());

            Binding vocab = decoded.nonIdentityBinding(ThematicRole.Vocabulary.IID);
            assertThat(vocab).isNotNull();
        }

        @Test
        @DisplayName("old manifest without config decodes with empty config")
        void backwardCompatDecode() {
            // Build manifest, encode WITHOUT config, then decode
            Manifest original = Manifest.builder()
                    .iid(ItemID.random())
                    .implementation(TEST_IMPL)
                    .build();

            // Encode with no config — this is what old manifests look like
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            Manifest decoded = Manifest.decode(bytes);

            assertThat(decoded.nonIdentityBindings()).isEmpty();
            assertThat(decoded.vid()).isEqualTo(original.vid());
        }
    }
}
