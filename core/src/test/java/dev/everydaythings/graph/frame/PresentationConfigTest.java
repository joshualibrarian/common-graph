package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.PresentationVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PresentationConfig")
class PresentationConfigTest {

    @Nested
    @DisplayName("Construction and accessors")
    class Construction {

        @Test
        @DisplayName("empty config")
        void empty() {
            PresentationConfig cfg = PresentationConfig.empty();
            assertThat(cfg.palette()).isEmpty();
            assertThat(cfg.rules()).isEmpty();
            assertThat(cfg.paletteColor(PresentationVocabulary.Primary.SEED.iid())).isEqualTo(-1);
        }

        @Test
        @DisplayName("palette lookup by token")
        void paletteLookup() {
            ItemID primaryId = PresentationVocabulary.Primary.SEED.iid();
            PresentationConfig cfg = new PresentationConfig(
                    List.of(new PresentationConfig.PaletteEntry(primaryId, 0x336699)),
                    List.of());

            assertThat(cfg.paletteColor(primaryId)).isEqualTo(0x336699);
            assertThat(cfg.paletteColor(PresentationVocabulary.Accent.SEED.iid())).isEqualTo(-1);
        }

        @Test
        @DisplayName("palette map view")
        void paletteMap() {
            ItemID primary = PresentationVocabulary.Primary.SEED.iid();
            ItemID accent = PresentationVocabulary.Accent.SEED.iid();
            PresentationConfig cfg = new PresentationConfig(
                    List.of(
                            new PresentationConfig.PaletteEntry(primary, 0xFF0000),
                            new PresentationConfig.PaletteEntry(accent, 0x00FF00)),
                    List.of());

            Map<ItemID, Integer> map = cfg.paletteMap();
            assertThat(map).hasSize(2);
            assertThat(map.get(primary)).isEqualTo(0xFF0000);
            assertThat(map.get(accent)).isEqualTo(0x00FF00);
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class RoundTrip {

        @Test
        @DisplayName("empty config round-trips")
        void emptyRoundTrip() {
            PresentationConfig original = PresentationConfig.empty();
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            PresentationConfig decoded = Canonical.decodeBinary(
                    bytes, PresentationConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.palette()).isEmpty();
            assertThat(decoded.rules()).isEmpty();
        }

        @Test
        @DisplayName("palette entries round-trip")
        void paletteRoundTrip() {
            ItemID primary = PresentationVocabulary.Primary.SEED.iid();
            ItemID surface = PresentationVocabulary.Surface.SEED.iid();

            PresentationConfig original = new PresentationConfig(
                    List.of(
                            new PresentationConfig.PaletteEntry(primary, 0x336699),
                            new PresentationConfig.PaletteEntry(surface, 0xFAFAFA)),
                    List.of());

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            PresentationConfig decoded = Canonical.decodeBinary(
                    bytes, PresentationConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.paletteColor(primary)).isEqualTo(0x336699);
            assertThat(decoded.paletteColor(surface)).isEqualTo(0xFAFAFA);
        }

        @Test
        @DisplayName("rules round-trip")
        void rulesRoundTrip() {
            PresentationConfig.Rule rule = new PresentationConfig.Rule(
                    ".title",
                    List.of(
                            new PresentationConfig.Property("font-weight", "bold"),
                            new PresentationConfig.Property("color", "#336699")));

            PresentationConfig original = new PresentationConfig(List.of(), List.of(rule));

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            PresentationConfig decoded = Canonical.decodeBinary(
                    bytes, PresentationConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.rules()).hasSize(1);
            assertThat(decoded.rules().getFirst().selector()).isEqualTo(".title");
            assertThat(decoded.rules().getFirst().properties()).hasSize(2);
        }

        @Test
        @DisplayName("full config round-trip")
        void fullRoundTrip() {
            PresentationConfig original = new PresentationConfig(
                    List.of(new PresentationConfig.PaletteEntry(
                            PresentationVocabulary.Primary.SEED.iid(), 0xABCDEF)),
                    List.of(new PresentationConfig.Rule(
                            ".handle",
                            List.of(new PresentationConfig.Property("opacity", "dim")))));

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            PresentationConfig decoded = Canonical.decodeBinary(
                    bytes, PresentationConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.palette()).hasSize(1);
            assertThat(decoded.rules()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Merge")
    class Merge {

        @Test
        @DisplayName("override palette tokens take precedence")
        void paletteMerge() {
            ItemID primary = PresentationVocabulary.Primary.SEED.iid();
            ItemID accent = PresentationVocabulary.Accent.SEED.iid();

            PresentationConfig base = new PresentationConfig(
                    List.of(
                            new PresentationConfig.PaletteEntry(primary, 0xFF0000),
                            new PresentationConfig.PaletteEntry(accent, 0x00FF00)),
                    List.of());

            PresentationConfig override = new PresentationConfig(
                    List.of(new PresentationConfig.PaletteEntry(primary, 0x0000FF)),
                    List.of());

            PresentationConfig merged = base.merge(override);
            assertThat(merged.paletteColor(primary)).isEqualTo(0x0000FF);
            assertThat(merged.paletteColor(accent)).isEqualTo(0x00FF00);
        }

        @Test
        @DisplayName("rules are concatenated")
        void rulesMerge() {
            PresentationConfig base = new PresentationConfig(List.of(),
                    List.of(new PresentationConfig.Rule(".base", List.of())));

            PresentationConfig override = new PresentationConfig(List.of(),
                    List.of(new PresentationConfig.Rule(".override", List.of())));

            PresentationConfig merged = base.merge(override);
            assertThat(merged.rules()).hasSize(2);
            assertThat(merged.rules().get(0).selector()).isEqualTo(".base");
            assertThat(merged.rules().get(1).selector()).isEqualTo(".override");
        }

        @Test
        @DisplayName("merge with null returns self")
        void mergeNull() {
            PresentationConfig cfg = new PresentationConfig(
                    List.of(new PresentationConfig.PaletteEntry(
                            PresentationVocabulary.Primary.SEED.iid(), 0xFF0000)),
                    List.of());

            assertThat(cfg.merge(null)).isSameAs(cfg);
        }
    }
}
