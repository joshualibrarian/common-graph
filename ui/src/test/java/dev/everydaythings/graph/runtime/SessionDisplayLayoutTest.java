package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.ui.Session;
import dev.everydaythings.graph.frame.DisplayLayoutConfig;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Session DISPLAY_LAYOUT frame management")
class SessionDisplayLayoutTest {

    private Librarian librarian;
    private TestSession session;

    static class TestSession extends Session {
        TestSession(LibrarianHandle librarian, Ref context) {
            super(librarian, context);
        }

        @Override public int run() { return 0; }
        @Override public Integer call() { return 0; }
        @Override public void close() {}
        @Override protected void render() {}
        @Override protected void output(String message) {}
    }

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
        LocalLibrarian handle = new LocalLibrarian(librarian);
        session = new TestSession(handle, Ref.of(librarian.iid()));
    }

    @Nested
    @DisplayName("registerDisplayLayout")
    class RegisterDisplayLayout {

        @Test
        @DisplayName("creates DISPLAY_LAYOUT frame on session")
        void createsFrame() {
            ItemID hostId = ItemID.random();
            DisplayLayoutConfig config = DisplayLayoutConfig.builder()
                    .hostId(hostId)
                    .displayId("retina-0")
                    .sessionX(0).sessionY(0)
                    .widthPx(2560).heightPx(1600)
                    .build();

            FrameKey key = session.registerDisplayLayout(config);
            assertThat(key).isNotNull();

            List<DisplayLayoutConfig> layouts = session.displayLayouts();
            assertThat(layouts).hasSize(1);
            assertThat(layouts.getFirst().displayId()).isEqualTo("retina-0");
            assertThat(layouts.getFirst().widthPx()).isEqualTo(2560);
        }

        @Test
        @DisplayName("multiple layouts for multiple monitors")
        void multipleLayouts() {
            ItemID hostId = ItemID.random();
            session.registerDisplayLayout(DisplayLayoutConfig.builder()
                    .hostId(hostId).displayId("monitor-0")
                    .sessionX(0).sessionY(0).widthPx(2560).heightPx(1600).build());
            session.registerDisplayLayout(DisplayLayoutConfig.builder()
                    .hostId(hostId).displayId("monitor-1")
                    .sessionX(2560).sessionY(0).widthPx(3840).heightPx(2160).build());

            assertThat(session.displayLayouts()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("clearDisplayLayouts")
    class ClearDisplayLayouts {

        @Test
        @DisplayName("removes layouts for a specific host")
        void clearsForHost() {
            ItemID host1 = ItemID.random();
            ItemID host2 = ItemID.random();

            session.registerDisplayLayout(DisplayLayoutConfig.builder()
                    .hostId(host1).displayId("a")
                    .sessionX(0).sessionY(0).widthPx(1920).heightPx(1080).build());
            session.registerDisplayLayout(DisplayLayoutConfig.builder()
                    .hostId(host2).displayId("b")
                    .sessionX(1920).sessionY(0).widthPx(1920).heightPx(1080).build());

            assertThat(session.displayLayouts()).hasSize(2);

            session.clearDisplayLayouts(host1);
            assertThat(session.displayLayouts()).hasSize(1);
            assertThat(session.displayLayouts().getFirst().hostId()).isEqualTo(host2);
        }
    }

    @Nested
    @DisplayName("registerHostDisplays")
    class RegisterHostDisplays {

        @Test
        @DisplayName("replaces all layouts for a host")
        void replacesAll() {
            ItemID hostId = ItemID.random();

            // Initial registration
            session.registerHostDisplays(hostId, List.of(
                    DisplayLayoutConfig.builder()
                            .hostId(hostId).displayId("old-0")
                            .sessionX(0).sessionY(0).widthPx(1920).heightPx(1080).build()));
            assertThat(session.displayLayouts()).hasSize(1);

            // Replace with two new displays
            session.registerHostDisplays(hostId, List.of(
                    DisplayLayoutConfig.builder()
                            .hostId(hostId).displayId("new-0")
                            .sessionX(0).sessionY(0).widthPx(2560).heightPx(1600).build(),
                    DisplayLayoutConfig.builder()
                            .hostId(hostId).displayId("new-1")
                            .sessionX(2560).sessionY(0).widthPx(3840).heightPx(2160).build()));

            List<DisplayLayoutConfig> layouts = session.displayLayouts();
            assertThat(layouts).hasSize(2);
            assertThat(layouts).extracting(DisplayLayoutConfig::displayId)
                    .containsExactlyInAnyOrder("new-0", "new-1");
        }
    }

    @Nested
    @DisplayName("Geometry intersection (via DisplayLayoutConfig)")
    class GeometryIntersection {

        @Test
        @DisplayName("view on left monitor")
        void viewOnLeftMonitor() {
            ItemID hostId = ItemID.random();
            DisplayLayoutConfig left = DisplayLayoutConfig.builder()
                    .hostId(hostId).displayId("left")
                    .sessionX(0).sessionY(0).widthPx(1920).heightPx(1080).build();
            DisplayLayoutConfig right = DisplayLayoutConfig.builder()
                    .hostId(hostId).displayId("right")
                    .sessionX(1920).sessionY(0).widthPx(2560).heightPx(1440).build();

            // View fully on left monitor
            assertThat(left.intersects(100, 100, 800, 600)).isTrue();
            assertThat(right.intersects(100, 100, 800, 600)).isFalse();
        }

        @Test
        @DisplayName("view spanning both monitors")
        void viewSpanningBothMonitors() {
            ItemID hostId = ItemID.random();
            DisplayLayoutConfig left = DisplayLayoutConfig.builder()
                    .hostId(hostId).displayId("left")
                    .sessionX(0).sessionY(0).widthPx(1920).heightPx(1080).build();
            DisplayLayoutConfig right = DisplayLayoutConfig.builder()
                    .hostId(hostId).displayId("right")
                    .sessionX(1920).sessionY(0).widthPx(2560).heightPx(1440).build();

            // View spanning the boundary
            assertThat(left.intersects(1800, 100, 400, 600)).isTrue();
            assertThat(right.intersects(1800, 100, 400, 600)).isTrue();
        }
    }
}
