package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.surface.item.InspectSurface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InspectSurface")
class InspectSurfaceTest {

    private static Librarian librarian;

    @BeforeAll
    static void setup() {
        librarian = Librarian.createInMemory();
    }

    @Test
    @DisplayName("FRAMES mode lists frames")
    void framesModeLists() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);

        assertThat(surface.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
        assertThat(surface.iid()).isNotNull();
        assertThat(surface.frames()).isNotNull();
    }

    @Test
    @DisplayName("VERSIONS mode shows version info")
    void versionsMode() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.VERSIONS);

        assertThat(surface.inspectMode()).isEqualTo(ViewConfig.InspectMode.VERSIONS);
        assertThat(surface.vid()).isNotNull();
    }

    @Test
    @DisplayName("handles empty item")
    void handlesEmptyItem() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);
        assertThat(surface.frames()).isNotNull();
    }

    @Test
    @DisplayName("null inspect mode defaults to FRAMES")
    void nullModeDefaults() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, null);
        assertThat(surface.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
    }

    @Test
    @DisplayName("dirty flag reflects item state")
    void dirtyFlag() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);
        assertThat(surface.dirty()).isTrue();
    }

    @Test
    @DisplayName("frames have resolved names, not hashes")
    void framesHaveResolvedNames() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);

        // Every frame should have a name that doesn't start with "iid:"
        for (InspectSurface.FrameInfo frame : surface.frames()) {
            assertThat(frame.name())
                    .as("Frame name should not be a raw IID hash")
                    .doesNotStartWith("iid:");
        }
    }

    @Test
    @DisplayName("frame info has name and qualifier fields")
    void frameInfoShape() {
        Item item = Item.create(librarian);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);

        if (!surface.frames().isEmpty()) {
            InspectSurface.FrameInfo frame = surface.frames().getFirst();
            assertThat(frame.name()).isNotNull();
            // qualifier may be null (no qualifier on simple frames)
        }
    }
}
