package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;

/**
 * Filament orthographic (GPU 2D) implementation of {@link ScenePainter}.
 *
 * <p>Paints a resolved, laid-out SceneNode tree as Filament geometry:
 * <ul>
 *   <li>Containers → colored quads (flat_color material)</li>
 *   <li>Text → per-glyph MSDF quads (msdf_text material)</li>
 *   <li>Bodies → colored geometry or emoji fallback via MSDF</li>
 * </ul>
 *
 * <p>This is the new ScenePainter-based replacement for
 * {@link LegacyFilamentSurfacePainter}, which operates on LayoutNode trees.
 * During migration, delegates to the legacy painter via bridge conversion.
 *
 * @see LegacyFilamentSurfacePainter
 */
public class FilamentSurfacePainter implements ScenePainter {

    private final LegacyFilamentSurfacePainter legacy;

    public FilamentSurfacePainter(LegacyFilamentSurfacePainter legacy) {
        this.legacy = legacy;
    }

    @Override
    public void paint(SceneNode tree) {
        if (tree == null) return;
        // TODO: Bridge via SceneNodeBridge.toLayoutNode(tree) → legacy.paint(layoutTree)
        // Then replace with direct SceneNode traversal.
        paintNode(tree);
    }

    @Override
    public void clear() {
        legacy.clear();
    }

    private void paintNode(SceneNode node) {
        if (node == null) return;
        if ("false".equals(node.visible())) return;

        // Placeholder: direct SceneNode painting will be implemented
        // when we remove the legacy bridge in Phase 5.
        // For now, the migration path uses SceneNodeBridge.toLayoutNode()
        // at the ViewWindow level.

        if (node.children() != null) {
            for (SceneNode child : node.children()) {
                paintNode(child);
            }
        }
    }
}
