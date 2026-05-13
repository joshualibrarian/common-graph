package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.encoding.Canonical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ViewConfig")
class ViewConfigTest {

    @Nested
    @DisplayName("Construction and accessors")
    class Construction {

        @Test
        @DisplayName("defaults")
        void defaults() {
            ViewConfig cfg = ViewConfig.defaults();
            assertThat(cfg.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
            assertThat(cfg.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
            assertThat(cfg.collapsed()).isFalse();
            assertThat(cfg.x()).isZero();
            assertThat(cfg.y()).isZero();
            assertThat(cfg.width()).isZero();
            assertThat(cfg.height()).isZero();
        }

        @Test
        @DisplayName("builder with all fields")
        void builder() {
            ViewConfig cfg = ViewConfig.builder()
                    .mode(ViewConfig.ViewMode.INSPECT)
                    .inspectMode(ViewConfig.InspectMode.VERSIONS)
                    .x(100).y(200).width(800).height(600)
                    .collapsed(true)
                    .build();

            assertThat(cfg.mode()).isEqualTo(ViewConfig.ViewMode.INSPECT);
            assertThat(cfg.inspectMode()).isEqualTo(ViewConfig.InspectMode.VERSIONS);
            assertThat(cfg.x()).isEqualTo(100);
            assertThat(cfg.y()).isEqualTo(200);
            assertThat(cfg.width()).isEqualTo(800);
            assertThat(cfg.height()).isEqualTo(600);
            assertThat(cfg.collapsed()).isTrue();
        }

        @Test
        @DisplayName("withMode returns new instance")
        void withMode() {
            ViewConfig original = ViewConfig.defaults();
            ViewConfig updated = original.withMode(ViewConfig.ViewMode.INSPECT);

            assertThat(updated.mode()).isEqualTo(ViewConfig.ViewMode.INSPECT);
            assertThat(original.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
        }

        @Test
        @DisplayName("withInspectMode returns new instance")
        void withInspectMode() {
            ViewConfig original = ViewConfig.defaults();
            ViewConfig updated = original.withInspectMode(ViewConfig.InspectMode.VERSIONS);

            assertThat(updated.inspectMode()).isEqualTo(ViewConfig.InspectMode.VERSIONS);
            assertThat(original.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
        }

        @Test
        @DisplayName("enum values")
        void enumValues() {
            assertThat(ViewConfig.ViewMode.values()).containsExactly(
                    ViewConfig.ViewMode.PRESENTATION, ViewConfig.ViewMode.INSPECT);
            assertThat(ViewConfig.InspectMode.values()).containsExactly(
                    ViewConfig.InspectMode.FRAMES, ViewConfig.InspectMode.VERSIONS);
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class RoundTrip {

        @Test
        @DisplayName("defaults round-trip")
        void defaultsRoundTrip() {
            ViewConfig original = ViewConfig.defaults();
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            ViewConfig decoded = Canonical.decodeBinary(
                    bytes, ViewConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
            assertThat(decoded.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
            assertThat(decoded.collapsed()).isFalse();
        }

        @Test
        @DisplayName("full config round-trip")
        void fullRoundTrip() {
            ViewConfig original = ViewConfig.builder()
                    .mode(ViewConfig.ViewMode.INSPECT)
                    .inspectMode(ViewConfig.InspectMode.VERSIONS)
                    .x(50).y(75).width(1024).height(768)
                    .collapsed(true)
                    .build();

            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            ViewConfig decoded = Canonical.decodeBinary(
                    bytes, ViewConfig.class, Canonical.Scope.RECORD);

            assertThat(decoded.mode()).isEqualTo(ViewConfig.ViewMode.INSPECT);
            assertThat(decoded.inspectMode()).isEqualTo(ViewConfig.InspectMode.VERSIONS);
            assertThat(decoded.x()).isEqualTo(50);
            assertThat(decoded.y()).isEqualTo(75);
            assertThat(decoded.width()).isEqualTo(1024);
            assertThat(decoded.height()).isEqualTo(768);
            assertThat(decoded.collapsed()).isTrue();
        }
    }
}
