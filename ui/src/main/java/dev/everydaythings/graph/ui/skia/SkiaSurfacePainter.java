package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;
import io.github.humbleui.skija.Canvas;

/**
 * Skia canvas implementation of {@link ScenePainter}.
 *
 * <p>Paints a resolved, laid-out SceneNode tree onto a Skia {@link Canvas}.
 * Reads pixel bounds directly from SceneNode fields (set by ScenePresenter).
 *
 * <p>During migration, delegates to the existing {@link SkiaPainter} via
 * {@link dev.everydaythings.graph.ui.scene.SceneNodeBridge} conversion.
 * This bridge will be removed when paint logic is ported directly to SceneNode.
 */
public class SkiaSurfacePainter implements ScenePainter {

    private final FontCache fontCache;
    private final SkiaPainter legacyPainter;
    private Canvas canvas;

    public SkiaSurfacePainter(FontCache fontCache) {
        this.fontCache = fontCache;
        this.legacyPainter = new SkiaPainter(fontCache);
    }

    /**
     * Set the canvas for the next paint call.
     * Must be called before {@link #paint(SceneNode)}.
     */
    public void canvas(Canvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public void paint(SceneNode tree) {
        if (canvas == null || tree == null) return;
        // TODO: Port direct SceneNode painting. For now, use bridge:
        // SceneNodeBridge.toLayoutNode(tree) → legacyPainter.paint(canvas, layoutTree)
        // This bridge is temporary — to be replaced with direct SceneNode traversal.
        paintNode(tree);
    }

    @Override
    public void clear() {
        canvas = null;
    }

    // =================================================================================
    // Direct SceneNode painting (replaces bridge incrementally)
    // =================================================================================

    private void paintNode(SceneNode node) {
        if (node == null) return;
        if ("false".equals(node.visible())) return;
        if (node.boundsWidth() <= 0 || node.boundsHeight() <= 0) return;

        switch (node.type()) {
            case CONTAINER -> {
                paintBackground(node);
                if (node.children() != null) {
                    for (SceneNode child : node.children()) {
                        paintNode(child);
                    }
                }
            }
            case TEXT -> {
                paintBackground(node);
                paintText(node);
            }
            case BODY -> {
                paintBackground(node);
                paintBody(node);
            }
            case null -> {}
        }
    }

    private void paintBackground(SceneNode node) {
        int bg = node.backgroundColor();
        if (bg == -1) return;
        var paint = new io.github.humbleui.skija.Paint().setColor(bg);
        float r = node.cornerFloat();
        if (r > 0) {
            canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                    node.boundsX(), node.boundsY(),
                    node.boundsWidth(), node.boundsHeight(), r), paint);
        } else {
            canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(
                    node.boundsX(), node.boundsY(),
                    node.boundsWidth(), node.boundsHeight()), paint);
        }
        paint.close();
    }

    private void paintText(SceneNode node) {
        String text = node.text();
        if (text == null || text.isEmpty()) return;

        float fontSize = node.fontSizeFloat() > 0 ? node.fontSizeFloat() : fontCache.baseFontSize();
        int color = node.foregroundColor() != -1 ? node.foregroundColor() : 0xFFCDD6F4;

        var paint = new io.github.humbleui.skija.Paint().setColor(color);
        var font = new io.github.humbleui.skija.Font(null, fontSize);
        canvas.drawString(text, node.boundsX(), node.boundsY() + fontSize, font, paint);
        font.close();
        paint.close();
    }

    private void paintBody(SceneNode node) {
        String glyph = node.glyph();
        if (glyph != null && !glyph.isEmpty()) {
            float fontSize = node.fontSizeFloat() > 0 ? node.fontSizeFloat() : fontCache.baseFontSize();
            int color = node.foregroundColor() != -1 ? node.foregroundColor() : 0xFFCDD6F4;

            var paint = new io.github.humbleui.skija.Paint().setColor(color);
            var font = new io.github.humbleui.skija.Font(null, fontSize);
            canvas.drawString(glyph, node.boundsX(), node.boundsY() + fontSize, font, paint);
            font.close();
            paint.close();
        }
    }

    private static float parseCornerRadius(String corner) {
        try {
            if (corner.endsWith("px")) {
                return Float.parseFloat(corner.substring(0, corner.length() - 2));
            }
            return Float.parseFloat(corner);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
