package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;

/**
 * Filament perspective (3D) implementation of {@link ScenePainter}.
 *
 * <p>Paints a resolved, laid-out SceneNode tree as 3D Filament geometry.
 * TODO: Port the full rendering logic from the deleted FilamentSpatialRenderer.
 */
public class FilamentSpatialPainter implements ScenePainter {

    @Override
    public void paint(SceneNode tree) {
        // TODO: Implement Filament 3D painting from SceneNode
    }

    @Override
    public void clear() {
        // TODO: Clear Filament 3D entities
    }
}
