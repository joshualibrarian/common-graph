package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.surface.item.InspectSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InspectSurface")
class InspectSurfaceTest {

    @Test
    @DisplayName("FRAMES mode lists frames")
    void framesModeLists() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);

        assertThat(surface.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
        assertThat(surface.iid()).isNotNull();
        assertThat(surface.frames()).isNotNull();
    }

    @Test
    @DisplayName("VERSIONS mode shows version info")
    void versionsMode() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.VERSIONS);

        assertThat(surface.inspectMode()).isEqualTo(ViewConfig.InspectMode.VERSIONS);
        assertThat(surface.vid()).isNotNull();
    }

    @Test
    @DisplayName("handles empty item")
    void handlesEmptyItem() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);
        assertThat(surface.frames()).isNotNull();
    }

    @Test
    @DisplayName("null inspect mode defaults to FRAMES")
    void nullModeDefaults() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        InspectSurface surface = InspectSurface.of(item, null);
        assertThat(surface.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
    }

    @Test
    @DisplayName("dirty flag reflects item state")
    void dirtyFlag() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        InspectSurface surface = InspectSurface.of(item, ViewConfig.InspectMode.FRAMES);
        assertThat(surface.dirty()).isTrue();
    }
}
