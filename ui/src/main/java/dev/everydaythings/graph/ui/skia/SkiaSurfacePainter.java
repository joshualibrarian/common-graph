package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Skia canvas implementation of {@link ScenePainter}.
 *
 * <p>Paints a resolved, laid-out SceneNode tree onto a Skia {@link Canvas}.
 * This is the 2D reference implementation — FilamentSurfacePainter and
 * WebSurfacePainter should produce pixel-identical output.
 */
public class SkiaSurfacePainter implements ScenePainter {

    private final SkiaFontManager fontCache;
    private Canvas canvas;

    // Resource caches (classpath path → parsed resource)
    private final Map<String, SVGDOM> svgCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();

    public SkiaSurfacePainter(SkiaFontManager fontCache) {
        this.fontCache = fontCache;
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
        paintNode(tree);
    }

    @Override
    public void clear() {
        canvas = null;
    }

    // =================================================================================
    // Node Painting
    // =================================================================================

    private void paintNode(SceneNode node) {
        if (node == null) return;
        if (!node.isVisible()) return;
        if (node.boundsWidth() <= 0 || node.boundsHeight() <= 0) return;

        int saveCount = canvas.save();

        // Common: rotation (translate to center, rotate, translate back)
        float rotation = node.rotationFloat();
        if (rotation != 0) {
            float cx = node.boundsX() + node.boundsWidth() / 2;
            float cy = node.boundsY() + node.boundsHeight() / 2;
            canvas.translate(cx, cy);
            canvas.rotate(rotation);
            canvas.translate(-cx, -cy);
        }

        // Common: opacity
        float opacity = node.opacityFloat();
        if (opacity < 1.0f) {
            canvas.saveLayerAlpha(
                    Rect.makeXYWH(node.boundsX(), node.boundsY(),
                            node.boundsWidth(), node.boundsHeight()),
                    Math.round(opacity * 255));
        }

        // Common: clipping
        String overflow = node.overflow();
        if ("hidden".equals(overflow) || node.isScrollContainer()) {
            float cr = node.cornerFloat();
            if (cr > 0) {
                canvas.clipRRect(RRect.makeXYWH(
                        node.boundsX(), node.boundsY(),
                        node.boundsWidth(), node.boundsHeight(), cr));
            } else {
                canvas.clipRect(Rect.makeXYWH(
                        node.boundsX(), node.boundsY(),
                        node.boundsWidth(), node.boundsHeight()));
            }
        }

        // Common: background and border (all node types)
        paintBackground(node);
        paintBorder(node);

        // Type-specific content
        switch (node.type()) {
            case CONTAINER -> {
                if (node.children() != null) {
                    for (SceneNode child : node.children()) {
                        paintNode(child);
                    }
                }
            }
            case TEXT -> paintText(node);
            case BODY -> paintBody(node);
            case null -> {}
        }

        canvas.restoreToCount(saveCount);
    }

    // =================================================================================
    // Common Painting
    // =================================================================================

    private void paintBackground(SceneNode node) {
        int bg = node.backgroundColor();
        if (bg == -1) return;
        try (var paint = new Paint().setColor(bg)) {
            float r = node.cornerFloat();
            if (r > 0) {
                canvas.drawRRect(RRect.makeXYWH(
                        node.boundsX(), node.boundsY(),
                        node.boundsWidth(), node.boundsHeight(), r), paint);
            } else {
                canvas.drawRect(Rect.makeXYWH(
                        node.boundsX(), node.boundsY(),
                        node.boundsWidth(), node.boundsHeight()), paint);
            }
        }
    }

    private void paintBorder(SceneNode node) {
        int color = node.borderColor();
        if (color == -1) return;

        float x = node.boundsX();
        float y = node.boundsY();
        float w = node.boundsWidth();
        float h = node.boundsHeight();

        try (var paint = new Paint().setColor(color)) {
            float top = node.borderTopWidth();
            float right = node.borderRightWidth();
            float bottom = node.borderBottomWidth();
            float left = node.borderLeftWidth();
            if (top > 0)    canvas.drawRect(Rect.makeXYWH(x, y, w, top), paint);
            if (right > 0)  canvas.drawRect(Rect.makeXYWH(x + w - right, y, right, h), paint);
            if (bottom > 0) canvas.drawRect(Rect.makeXYWH(x, y + h - bottom, w, bottom), paint);
            if (left > 0)   canvas.drawRect(Rect.makeXYWH(x, y, left, h), paint);
        }
    }

    // =================================================================================
    // Text Painting
    // =================================================================================

    private void paintText(SceneNode node) {
        String text = node.text();
        if (text == null || text.isEmpty()) return;

        float fontSize = node.fontSizeFloat() > 0 ? node.fontSizeFloat() : fontCache.baseFontSize();
        int color = node.foregroundColor() != -1 ? node.foregroundColor() : 0xFFCDD6F4;

        SkiaFontManager.FontProfile profile = fontCache.profileFor(node.fontFamily(), fontSize);
        float maxWidth = node.boundsWidth() > 0 ? node.boundsWidth() : Float.MAX_VALUE;
        try (var para = fontCache.buildParagraph(text, profile, color, maxWidth)) {
            para.paint(canvas, node.boundsX(), node.boundsY());
        }
    }

    // =================================================================================
    // Body Painting — 2D selection chain: image → shape → glyph
    // =================================================================================

    private void paintBody(SceneNode node) {
        // 2D fidelity chain
        String image = node.image();
        if (image != null && !image.isEmpty()) {
            paintImage(node, image);
            return;
        }

        String shape = node.shape();
        if (shape != null && !shape.isEmpty()) {
            paintShape(node, shape);
            return;
        }

        String glyph = node.glyph();
        if (glyph != null && !glyph.isEmpty()) {
            paintGlyph(node, glyph);
        }
    }

    private void paintImage(SceneNode node, String resource) {
        float x = node.boundsX();
        float y = node.boundsY();
        float w = node.boundsWidth();
        float h = node.boundsHeight();

        if (resource.endsWith(".svg")) {
            SVGDOM svg = loadSvg(resource);
            if (svg == null) return;

            int saveCount = canvas.save();
            canvas.translate(x, y);
            svg.setContainerSize(w, h);
            svg.render(canvas);
            canvas.restoreToCount(saveCount);
        } else {
            Image img = loadImage(resource);
            if (img == null) return;

            canvas.drawImageRect(img, Rect.makeXYWH(x, y, w, h));
        }
    }

    private void paintShape(SceneNode node, String shape) {
        float x = node.boundsX();
        float y = node.boundsY();
        float w = node.boundsWidth();
        float h = node.boundsHeight();
        int fillColor = node.fillColor();
        int strokeColor = node.strokeColor();
        float strokeWidth = node.strokeWidthFloat();

        switch (shape) {
            case "rect", "box" -> {
                float cr = node.cornerFloat();
                if (fillColor != -1) {
                    try (var paint = new Paint().setColor(fillColor)) {
                        if (cr > 0) {
                            canvas.drawRRect(RRect.makeXYWH(x, y, w, h, cr), paint);
                        } else {
                            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                        }
                    }
                }
                if (strokeColor != -1 && strokeWidth > 0) {
                    try (var paint = new Paint().setColor(strokeColor)
                            .setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)) {
                        if (cr > 0) {
                            canvas.drawRRect(RRect.makeXYWH(x, y, w, h, cr), paint);
                        } else {
                            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                        }
                    }
                }
            }
            case "circle", "sphere" -> {
                float cx = x + w / 2;
                float cy = y + h / 2;
                float r = Math.min(w, h) / 2;
                if (fillColor != -1) {
                    try (var paint = new Paint().setColor(fillColor)) {
                        canvas.drawCircle(cx, cy, r, paint);
                    }
                }
                if (strokeColor != -1 && strokeWidth > 0) {
                    try (var paint = new Paint().setColor(strokeColor)
                            .setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)) {
                        canvas.drawCircle(cx, cy, r, paint);
                    }
                }
            }
            case "line" -> {
                float sw = strokeWidth > 0 ? strokeWidth : 1;
                int color = strokeColor != -1 ? strokeColor : (fillColor != -1 ? fillColor : 0xFF000000);
                try (var paint = new Paint().setColor(color)
                        .setMode(PaintMode.STROKE).setStrokeWidth(sw)) {
                    canvas.drawLine(x, y + h / 2, x + w, y + h / 2, paint);
                }
            }
            case "path" -> {
                // SVG path data from the 'd' property — not yet wired through SceneNode
                // Will use Path.makeFromSVGString when available
            }
            default -> {
                // Unknown shape — fall through to glyph if available
                String glyph = node.glyph();
                if (glyph != null && !glyph.isEmpty()) {
                    paintGlyph(node, glyph);
                }
            }
        }
    }

    private void paintGlyph(SceneNode node, String glyph) {
        float fontSize = node.fontSizeFloat() > 0 ? node.fontSizeFloat() : fontCache.baseFontSize();
        int color = node.foregroundColor() != -1 ? node.foregroundColor() : 0xFFCDD6F4;

        SkiaFontManager.FontProfile profile = fontCache.profileFor(null, fontSize);
        try (var para = fontCache.buildParagraph(glyph, profile, color, Float.MAX_VALUE)) {
            para.paint(canvas, node.boundsX(), node.boundsY());
        }
    }

    // =================================================================================
    // Resource Loading
    // =================================================================================

    private SVGDOM loadSvg(String resource) {
        return svgCache.computeIfAbsent(resource, path -> {
            byte[] bytes = loadClasspathResource(path);
            if (bytes == null) return null;
            return new SVGDOM(Data.makeFromBytes(bytes));
        });
    }

    private Image loadImage(String resource) {
        return imageCache.computeIfAbsent(resource, path -> {
            byte[] bytes = loadClasspathResource(path);
            if (bytes == null) return null;
            return Image.makeDeferredFromEncodedBytes(bytes);
        });
    }

    private static byte[] loadClasspathResource(String resource) {
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = SkiaSurfacePainter.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

}
