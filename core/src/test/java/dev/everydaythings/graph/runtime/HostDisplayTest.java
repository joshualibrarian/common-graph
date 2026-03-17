package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.DisplayConfig;
import dev.everydaythings.graph.item.id.FrameKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Host DISPLAY frame management")
class HostDisplayTest {

    private Librarian librarian;
    private Host host;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
        host = librarian.host();
        assertThat(host).isNotNull();
    }

    @Nested
    @DisplayName("registerDisplay")
    class RegisterDisplay {

        @Test
        @DisplayName("creates a DISPLAY frame on the host")
        void createsFrame() {
            DisplayConfig config = DisplayConfig.builder()
                    .name("Built-in Retina Display")
                    .widthPx(2560).heightPx(1600)
                    .refreshRate(60).scalePercent(200)
                    .osX(0).osY(0)
                    .build();

            FrameKey key = host.registerDisplay("retina-0", config);
            assertThat(key).isNotNull();

            List<Host.DisplayInfo> displays = host.displays();
            assertThat(displays).hasSize(1);
            assertThat(displays.getFirst().displayId()).isEqualTo("retina-0");
            assertThat(displays.getFirst().config().widthPx()).isEqualTo(2560);
        }

        @Test
        @DisplayName("multiple displays")
        void multipleDisplays() {
            host.registerDisplay("monitor-0", DisplayConfig.builder()
                    .name("Built-in").widthPx(2560).heightPx(1600)
                    .refreshRate(60).scalePercent(200).osX(0).osY(0).build());
            host.registerDisplay("monitor-1", DisplayConfig.builder()
                    .name("External").widthPx(3840).heightPx(2160)
                    .refreshRate(60).scalePercent(150).osX(2560).osY(0).build());

            assertThat(host.displays()).hasSize(2);
        }

        @Test
        @DisplayName("replaces existing display with same id")
        void replacesExisting() {
            host.registerDisplay("retina-0", DisplayConfig.builder()
                    .name("Old").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(0).osY(0).build());
            host.registerDisplay("retina-0", DisplayConfig.builder()
                    .name("New").widthPx(2560).heightPx(1600)
                    .refreshRate(60).scalePercent(200).osX(0).osY(0).build());

            assertThat(host.displays()).hasSize(1);
            assertThat(host.displays().getFirst().config().name()).isEqualTo("New");
        }
    }

    @Nested
    @DisplayName("clearDisplays")
    class ClearDisplays {

        @Test
        @DisplayName("removes all DISPLAY frames")
        void removesAll() {
            host.registerDisplay("monitor-0", DisplayConfig.builder()
                    .name("A").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(0).osY(0).build());
            host.registerDisplay("monitor-1", DisplayConfig.builder()
                    .name("B").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(1920).osY(0).build());

            assertThat(host.displays()).hasSize(2);

            host.clearDisplays();
            assertThat(host.displays()).isEmpty();
        }

        @Test
        @DisplayName("no-op when no displays")
        void noOpWhenEmpty() {
            host.clearDisplays(); // Should not throw
            assertThat(host.displays()).isEmpty();
        }
    }
}
