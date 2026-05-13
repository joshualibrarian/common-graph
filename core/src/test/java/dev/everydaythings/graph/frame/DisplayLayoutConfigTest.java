package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DisplayLayoutConfig")
class DisplayLayoutConfigTest {

    private static final ItemID HOST_ID = ItemID.random();

    @Nested
    @DisplayName("Construction and accessors")
    class Construction {

        @Test
        @DisplayName("builder with all fields")
        void builder() {
            DisplayLayoutConfig cfg = DisplayLayoutConfig.builder()
                    .hostId(HOST_ID)
                    .displayId("retina-0")
                    .sessionX(0)
                    .sessionY(0)
                    .widthPx(2560)
                    .heightPx(1600)
                    .build();

            assertThat(cfg.hostId()).isEqualTo(HOST_ID);
            assertThat(cfg.displayId()).isEqualTo("retina-0");
            assertThat(cfg.sessionX()).isZero();
            assertThat(cfg.sessionY()).isZero();
            assertThat(cfg.widthPx()).isEqualTo(2560);
            assertThat(cfg.heightPx()).isEqualTo(1600);
        }
    }

    @Nested
    @DisplayName("Intersection")
    class Intersection {

        private final DisplayLayoutConfig display = DisplayLayoutConfig.builder()
                .hostId(HOST_ID)
                .displayId("main")
                .sessionX(0)
                .sessionY(0)
                .widthPx(1920)
                .heightPx(1080)
                .build();

        @Test
        @DisplayName("fully contained rect intersects")
        void fullyContained() {
            assertThat(display.intersects(100, 100, 800, 600)).isTrue();
        }

        @Test
        @DisplayName("partial overlap intersects")
        void partialOverlap() {
            assertThat(display.intersects(1800, 900, 400, 300)).isTrue();
        }

        @Test
        @DisplayName("rect to the right does not intersect")
        void rightOfDisplay() {
            assertThat(display.intersects(1920, 0, 800, 600)).isFalse();
        }

        @Test
        @DisplayName("rect below does not intersect")
        void belowDisplay() {
            assertThat(display.intersects(0, 1080, 800, 600)).isFalse();
        }

        @Test
        @DisplayName("rect to the left does not intersect")
        void leftOfDisplay() {
            assertThat(display.intersects(-800, 0, 800, 600)).isFalse();
        }

        @Test
        @DisplayName("rect above does not intersect")
        void aboveDisplay() {
            assertThat(display.intersects(0, -600, 800, 600)).isFalse();
        }

        @Test
        @DisplayName("second monitor with offset position")
        void secondMonitor() {
            DisplayLayoutConfig second = DisplayLayoutConfig.builder()
                    .hostId(HOST_ID)
                    .displayId("external")
                    .sessionX(1920)
                    .sessionY(0)
                    .widthPx(2560)
                    .heightPx(1440)
                    .build();

            // Window on the second monitor
            assertThat(second.intersects(2000, 100, 800, 600)).isTrue();
            // Window only on the first monitor
            assertThat(second.intersects(100, 100, 800, 600)).isFalse();
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class RoundTrip {

        @Test
        @DisplayName("full config round-trip")
        void fullRoundTrip() {
            DisplayLayoutConfig original = DisplayLayoutConfig.builder()
                    .hostId(HOST_ID)
                    .displayId("retina-0")
                    .sessionX(100)
                    .sessionY(200)
                    .widthPx(2560)
                    .heightPx(1600)
                    .build();

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            DisplayLayoutConfig decoded = Canonical.decodeBinary(
                    bytes, DisplayLayoutConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.hostId()).isEqualTo(HOST_ID);
            assertThat(decoded.displayId()).isEqualTo("retina-0");
            assertThat(decoded.sessionX()).isEqualTo(100);
            assertThat(decoded.sessionY()).isEqualTo(200);
            assertThat(decoded.widthPx()).isEqualTo(2560);
            assertThat(decoded.heightPx()).isEqualTo(1600);
        }
    }
}
