package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.surface.item.ViewSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ViewSurface")
class ViewSurfaceTest {

    @Test
    @DisplayName("factory creates surface with handle and mode")
    void factoryCreatesWithHandle() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        ViewConfig config = ViewConfig.defaults();
        ViewSurface view = ViewSurface.of(item, TextSurface.of("content"), TextSurface.of("prompt"), config);

        assertThat(view.handle()).isNotNull();
        assertThat(view.content()).isNotNull();
        assertThat(view.prompt()).isNotNull();
        assertThat(view.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
    }

    @Test
    @DisplayName("mode reflects config")
    void modeReflectsConfig() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        ViewConfig config = ViewConfig.builder()
                .mode(ViewConfig.ViewMode.INSPECT)
                .inspectMode(ViewConfig.InspectMode.FRAMES)
                .build();
        ViewSurface view = ViewSurface.of(item, null, null, config);

        assertThat(view.mode()).isEqualTo(ViewConfig.ViewMode.INSPECT);
    }

    @Test
    @DisplayName("null config defaults to PRESENTATION")
    void nullConfigDefaults() {
        Librarian lib = Librarian.createInMemory();
        Item item = Item.create(lib);

        ViewSurface view = ViewSurface.of(item, null, null, null);
        assertThat(view.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
    }
}
