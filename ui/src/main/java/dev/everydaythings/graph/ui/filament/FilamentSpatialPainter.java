package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;

/**
 * Filament perspective (3D) implementation of {@link ScenePainter}.
 *
 * <p>Paints a resolved, laid-out SceneNode tree as 3D Filament geometry:
 * <ul>
 *   <li>Body nodes with models → GLB assets loaded via gltfio</li>
 *   <li>Body nodes with shapes → primitive meshes (box, sphere, cylinder)</li>
 *   <li>Body surfaces → 2D content rendered as textures on 3D faces</li>
 *   <li>Container/Text nodes → presented as 2D panels in 3D space</li>
 * </ul>
 *
 * <p>This is the new ScenePainter-based replacement for
 * {@link FilamentSpatialRenderer}, which implements the old SpatialRenderer
 * interface. During migration, delegates to the legacy renderer via bridge.
 *
 * @see FilamentSpatialRenderer
 */
public class FilamentSpatialPainter implements ScenePainter {

    private final FilamentSpatialRenderer legacy;

    public FilamentSpatialPainter(FilamentSpatialRenderer legacy) {
        this.legacy = legacy;
    }

    @Override
    public void paint(SceneNode tree) {
        if (tree == null) return;
        // TODO: Bridge via legacy renderer for 3D scene construction.
        // The migration path converts SceneNode 3D properties to
        // SpatialRenderer calls (body, light, camera, environment).
        // Direct SceneNode painting replaces this in Phase 5.
        paintNode(tree);
    }

    @Override
    public void clear() {
        legacy.clear();
    }

    private void paintNode(SceneNode node) {
        if (node == null) return;
        if ("false".equals(node.visible())) return;

        // Placeholder: direct 3D painting will be implemented
        // when we remove the legacy bridge in Phase 5.

        if (node.children() != null) {
            for (SceneNode child : node.children()) {
                paintNode(child);
            }
        }
    }
}
