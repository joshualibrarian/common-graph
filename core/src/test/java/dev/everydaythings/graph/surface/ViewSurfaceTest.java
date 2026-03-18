package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.surface.item.ViewSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ViewSurface")
class ViewSurfaceTest {

    private static Librarian librarian;

    @BeforeAll
    static void setup() {
        librarian = Librarian.createInMemory();
    }

    @Test
    @DisplayName("factory creates surface with handle and mode")
    void factoryCreatesWithHandle() {
        Item item = Item.create(librarian);

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
        Item item = Item.create(librarian);

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
        Item item = Item.create(librarian);

        ViewSurface view = ViewSurface.of(item, null, null, null);
        assertThat(view.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
    }
}
