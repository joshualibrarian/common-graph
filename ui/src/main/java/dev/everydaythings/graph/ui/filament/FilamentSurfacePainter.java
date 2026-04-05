package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;

/**
 * Filament orthographic (GPU 2D) implementation of {@link ScenePainter}.
 *
 * <p>Paints a resolved, laid-out SceneNode tree as Filament geometry.
 * TODO: Port the full rendering logic from the deleted LegacyFilamentSurfacePainter.
 */
public class FilamentSurfacePainter implements ScenePainter {

    @Override
    public void paint(SceneNode tree) {
        // TODO: Implement Filament 2D painting from SceneNode
    }

    @Override
    public void clear() {
        // TODO: Clear Filament entities
    }
}
