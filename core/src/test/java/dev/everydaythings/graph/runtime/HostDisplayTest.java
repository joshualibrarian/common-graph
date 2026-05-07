package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.DisplayConfig;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.language.DeviceVocabulary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Host DEVICE frame management")
class HostDisplayTest {

    private static LibrarianOld librarian;
    private HostOld host;

    @BeforeAll
    static void setupLibrarian() {
        librarian = LibrarianOld.createInMemory();
    }

    @BeforeEach
    void setUp() {
        host = librarian.host();
        host.clearDevices();
        assertThat(host).isNotNull();
    }

    @Nested
    @DisplayName("registerDisplay")
    class RegisterDisplay {

        @Test
        @DisplayName("creates a DEVICE frame on the host")
        void createsFrame() {
            DisplayConfig config = DisplayConfig.builder()
                    .name("Built-in Retina Display")
                    .widthPx(2560).heightPx(1600)
                    .refreshRate(60).scalePercent(200)
                    .osX(0).osY(0)
                    .build();

            CompoundKey key = host.registerDisplay("retina-0", config);
            assertThat(key).isNotNull();

            List<HostOld.DeviceInfo> displays = host.displays();
            assertThat(displays).hasSize(1);
            assertThat(displays.getFirst().deviceId()).isEqualTo("retina-0");
            assertThat(displays.getFirst().deviceType()).isEqualTo(DeviceVocabulary.Display.IID);
            assertThat(((DisplayConfig) displays.getFirst().config()).widthPx()).isEqualTo(2560);
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
            assertThat(((DisplayConfig) host.displays().getFirst().config()).name()).isEqualTo("New");
        }
    }

    @Nested
    @DisplayName("clearDevices")
    class ClearDevices {

        @Test
        @DisplayName("removes all DEVICE frames")
        void removesAll() {
            host.registerDisplay("monitor-0", DisplayConfig.builder()
                    .name("A").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(0).osY(0).build());
            host.registerDisplay("monitor-1", DisplayConfig.builder()
                    .name("B").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(1920).osY(0).build());

            assertThat(host.displays()).hasSize(2);

            host.clearDevices();
            assertThat(host.displays()).isEmpty();
        }

        @Test
        @DisplayName("no-op when no devices")
        void noOpWhenEmpty() {
            host.clearDevices();
            assertThat(host.displays()).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateDisplays")
    class UpdateDisplays {

        @Test
        @DisplayName("adds new displays and disconnects removed ones")
        void addsAndRemoves() {
            // Start with two displays
            host.registerDisplay("monitor-0", DisplayConfig.builder()
                    .name("A").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(0).osY(0).build());
            host.registerDisplay("monitor-1", DisplayConfig.builder()
                    .name("B").widthPx(1920).heightPx(1080)
                    .refreshRate(60).scalePercent(100).osX(1920).osY(0).build());
            assertThat(host.displays()).hasSize(2);

            // Update: monitor-1 gone, monitor-2 added
            host.updateDisplays(List.of(
                    new HostOld.DisplayUpdate("monitor-0", DisplayConfig.builder()
                            .name("A").widthPx(1920).heightPx(1080)
                            .refreshRate(60).scalePercent(100).osX(0).osY(0).build()),
                    new HostOld.DisplayUpdate("monitor-2", DisplayConfig.builder()
                            .name("C").widthPx(3840).heightPx(2160)
                            .refreshRate(60).scalePercent(150).osX(1920).osY(0).build())
            ));

            List<HostOld.DeviceInfo> displays = host.displays();
            assertThat(displays).hasSize(2);
            assertThat(displays.stream().map(HostOld.DeviceInfo::deviceId))
                    .containsExactlyInAnyOrder("monitor-0", "monitor-2");
        }
    }

    @Nested
    @DisplayName("DeviceInfo.refOn")
    class CompoundRef {

        @Test
        @DisplayName("builds a compound Ref targeting device on host")
        void compoundRef() {
            host.registerDisplay("retina-0", DisplayConfig.builder()
                    .name("Built-in").widthPx(2560).heightPx(1600)
                    .refreshRate(60).scalePercent(200).osX(0).osY(0).build());

            HostOld.DeviceInfo display = host.displays().getFirst();
            Ref ref = display.refOn(host.iid());

            assertThat(ref.target()).isEqualTo(host.iid());
            assertThat(ref.frameKey()).isEqualTo(display.frameKey());
        }
    }
}
