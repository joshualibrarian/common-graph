package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.encoding.Canonical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DisplayConfig")
class DisplayConfigTest {

    @Nested
    @DisplayName("Construction and accessors")
    class Construction {

        @Test
        @DisplayName("builder with all fields")
        void builder() {
            DisplayConfig cfg = DisplayConfig.builder()
                    .name("Built-in Retina Display")
                    .widthPx(2560)
                    .heightPx(1600)
                    .refreshRate(60)
                    .scalePercent(200)
                    .osX(0)
                    .osY(0)
                    .build();

            assertThat(cfg.name()).isEqualTo("Built-in Retina Display");
            assertThat(cfg.widthPx()).isEqualTo(2560);
            assertThat(cfg.heightPx()).isEqualTo(1600);
            assertThat(cfg.refreshRate()).isEqualTo(60);
            assertThat(cfg.scalePercent()).isEqualTo(200);
            assertThat(cfg.osX()).isZero();
            assertThat(cfg.osY()).isZero();
        }

        @Test
        @DisplayName("external monitor with offset position")
        void externalMonitor() {
            DisplayConfig cfg = DisplayConfig.builder()
                    .name("DELL U2720Q")
                    .widthPx(3840)
                    .heightPx(2160)
                    .refreshRate(60)
                    .scalePercent(150)
                    .osX(2560)
                    .osY(-200)
                    .build();

            assertThat(cfg.osX()).isEqualTo(2560);
            assertThat(cfg.osY()).isEqualTo(-200);
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class RoundTrip {

        @Test
        @DisplayName("full config round-trip")
        void fullRoundTrip() {
            DisplayConfig original = DisplayConfig.builder()
                    .name("Built-in Retina Display")
                    .widthPx(2560)
                    .heightPx(1600)
                    .refreshRate(60)
                    .scalePercent(200)
                    .osX(0)
                    .osY(0)
                    .build();

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            DisplayConfig decoded = Canonical.decodeBinary(
                    bytes, DisplayConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.name()).isEqualTo("Built-in Retina Display");
            assertThat(decoded.widthPx()).isEqualTo(2560);
            assertThat(decoded.heightPx()).isEqualTo(1600);
            assertThat(decoded.refreshRate()).isEqualTo(60);
            assertThat(decoded.scalePercent()).isEqualTo(200);
            assertThat(decoded.osX()).isZero();
            assertThat(decoded.osY()).isZero();
        }

        @Test
        @DisplayName("external monitor round-trip preserves negative offsets")
        void negativeOffsetRoundTrip() {
            DisplayConfig original = DisplayConfig.builder()
                    .name("External")
                    .widthPx(1920)
                    .heightPx(1080)
                    .refreshRate(144)
                    .scalePercent(100)
                    .osX(-1920)
                    .osY(100)
                    .build();

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            DisplayConfig decoded = Canonical.decodeBinary(
                    bytes, DisplayConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.osX()).isEqualTo(-1920);
            assertThat(decoded.osY()).isEqualTo(100);
            assertThat(decoded.refreshRate()).isEqualTo(144);
        }
    }
}
