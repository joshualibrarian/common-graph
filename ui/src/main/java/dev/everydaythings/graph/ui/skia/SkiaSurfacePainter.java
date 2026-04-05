package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.AnimationState;
import dev.everydaythings.graph.ui.scene.Easing;
import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;
import dev.everydaythings.graph.ui.scene.TransitionSpec;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.paragraph.Paragraph;
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
    private final AnimationState animationState;
    private Canvas canvas;

    // Resource caches (classpath path → parsed resource)
    private final Map<String, SVGDOM> svgCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();

    public SkiaSurfacePainter(SkiaFontManager fontCache, AnimationState animationState) {
        this.fontCache = fontCache;
        this.animationState = animationState;
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

        // Register transitions for this node if it declares them
        registerTransitions(node);

        int saveCount = canvas.save();

        // Common: transforms (scale, rotation) around transform origin
        float scaleX = node.scaleXFloat();
        float scaleY = node.scaleYFloat();
        float rotZ = node.rotationZFloat();
        boolean hasTransform = scaleX != 1.0f || scaleY != 1.0f || rotZ != 0;
        if (hasTransform) {
            float[] origin = resolveTransformOrigin(node);
            canvas.translate(origin[0], origin[1]);
            if (scaleX != 1.0f || scaleY != 1.0f) {
                canvas.scale(scaleX, scaleY);
            }
            if (rotZ != 0) {
                canvas.rotate(rotZ);
            }
            canvas.translate(-origin[0], -origin[1]);
        }

        // Common: opacity (animated)
        float opacity = animatedFloat(node, "opacity", node.opacityFloat());
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

        // Common: background, background image, and border (all node types)
        paintBackground(node);
        paintBackgroundImage(node);
        paintBorder(node);

        // Type-specific content
        switch (node.type()) {
            case CONTAINER -> {
                if (node.children() != null) {
                    float scrollY = node.scrollOffsetY();
                    if (scrollY != 0) canvas.translate(0, -scrollY);
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
        int bg = animatedColor(node, "backgroundColor", node.backgroundColorInt());
        if (bg == -1) return;
        try (Paint paint = new Paint().setColor(bg)) {
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

    private void paintBackgroundImage(SceneNode node) {
        String resource = node.backgroundImage();
        if (resource == null || resource.isEmpty()) return;

        float x = node.boundsX();
        float y = node.boundsY();
        float w = node.boundsWidth();
        float h = node.boundsHeight();
        String sizing = node.backgroundSize();

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

            if ("fill".equals(sizing)) {
                canvas.drawImageRect(img, Rect.makeXYWH(x, y, w, h));
            } else if ("cover".equals(sizing)) {
                float imgW = img.getWidth();
                float imgH = img.getHeight();
                float scale = Math.max(w / imgW, h / imgH);
                float sw = imgW * scale;
                float sh = imgH * scale;
                float sx = x + (w - sw) / 2;
                float sy = y + (h - sh) / 2;
                int sc = canvas.save();
                canvas.clipRect(Rect.makeXYWH(x, y, w, h));
                canvas.drawImageRect(img, Rect.makeXYWH(sx, sy, sw, sh));
                canvas.restoreToCount(sc);
            } else if ("contain".equals(sizing)) {
                float imgW = img.getWidth();
                float imgH = img.getHeight();
                float scale = Math.min(w / imgW, h / imgH);
                float sw = imgW * scale;
                float sh = imgH * scale;
                float sx = x + (w - sw) / 2;
                float sy = y + (h - sh) / 2;
                canvas.drawImageRect(img, Rect.makeXYWH(sx, sy, sw, sh));
            } else {
                // Natural size, centered
                float imgW = img.getWidth();
                float imgH = img.getHeight();
                float sx = x + (w - imgW) / 2;
                float sy = y + (h - imgH) / 2;
                canvas.drawImageRect(img, Rect.makeXYWH(sx, sy, imgW, imgH));
            }
        }
    }

    private void paintBorder(SceneNode node) {
        float x = node.boundsX();
        float y = node.boundsY();
        float w = node.boundsWidth();
        float h = node.boundsHeight();

        drawBorderSide(node.borderTopColorInt(), node.borderTopWidthFloat(),
                x, y, w, true);
        drawBorderSide(node.borderRightColorInt(), node.borderRightWidthFloat(),
                x + w - node.borderRightWidthFloat(), y, h, false);
        drawBorderSide(node.borderBottomColorInt(), node.borderBottomWidthFloat(),
                x, y + h - node.borderBottomWidthFloat(), w, true);
        drawBorderSide(node.borderLeftColorInt(), node.borderLeftWidthFloat(),
                x, y, h, false);
    }

    private void drawBorderSide(int color, float width, float x, float y,
                                 float length, boolean horizontal) {
        if (color == -1 || width <= 0) return;
        try (Paint paint = new Paint().setColor(color)) {
            if (horizontal) {
                canvas.drawRect(Rect.makeXYWH(x, y, length, width), paint);
            } else {
                canvas.drawRect(Rect.makeXYWH(x, y, width, length), paint);
            }
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
        SkiaFontManager.TextParams params = textParamsFrom(node);
        float maxWidth = node.boundsWidth() > 0 ? node.boundsWidth() : Float.MAX_VALUE;
        try (Paragraph para = fontCache.buildParagraph(text, profile, color, maxWidth, params)) {
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
        int strokeColor = node.strokeColorInt();
        float strokeWidth = node.strokeWidthFloat();

        switch (shape) {
            case "rect", "box" -> {
                float cr = node.cornerFloat();
                if (fillColor != -1) {
                    try (Paint paint = new Paint().setColor(fillColor)) {
                        if (cr > 0) {
                            canvas.drawRRect(RRect.makeXYWH(x, y, w, h, cr), paint);
                        } else {
                            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                        }
                    }
                }
                if (strokeColor != -1 && strokeWidth > 0) {
                    try (Paint paint = new Paint().setColor(strokeColor)
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
                    try (Paint paint = new Paint().setColor(fillColor)) {
                        canvas.drawCircle(cx, cy, r, paint);
                    }
                }
                if (strokeColor != -1 && strokeWidth > 0) {
                    try (Paint paint = new Paint().setColor(strokeColor)
                            .setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)) {
                        canvas.drawCircle(cx, cy, r, paint);
                    }
                }
            }
            case "line" -> {
                float sw = strokeWidth > 0 ? strokeWidth : 1;
                int color = strokeColor != -1 ? strokeColor : (fillColor != -1 ? fillColor : 0xFF000000);
                try (Paint paint = new Paint().setColor(color)
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
        SkiaFontManager.TextParams params = textParamsFrom(node);
        try (Paragraph para = fontCache.buildParagraph(glyph, profile, color, Float.MAX_VALUE, params)) {
            para.paint(canvas, node.boundsX(), node.boundsY());
        }
    }

    private static SkiaFontManager.TextParams textParamsFrom(SceneNode node) {
        return new SkiaFontManager.TextParams(
                node.isBold(), node.isItalic(),
                node.hasUnderline(), node.hasLineThrough(), node.hasOverline(),
                node.textAlign(), node.lineHeightFloat(), node.letterSpacingFloat(),
                node.textOverflow(), node.whiteSpace());
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

    // =================================================================================
    // Animation Integration
    // =================================================================================

    /**
     * Resolve transform origin to absolute pixel coordinates.
     * Defaults to center of bounds. Accepts "center", "top left", "50% 0%", etc.
     */
    private static float[] resolveTransformOrigin(SceneNode node) {
        float x = node.boundsX();
        float y = node.boundsY();
        float w = node.boundsWidth();
        float h = node.boundsHeight();

        String origin = node.transformOrigin();
        if (origin == null || origin.isEmpty() || "center".equals(origin)) {
            return new float[]{x + w / 2, y + h / 2};
        }

        String[] parts = origin.trim().split("\\s+");
        float ox = resolveOriginComponent(parts[0], w) + x;
        float oy = parts.length > 1 ? resolveOriginComponent(parts[1], h) + y : y + h / 2;
        return new float[]{ox, oy};
    }

    private static float resolveOriginComponent(String value, float dimension) {
        return switch (value) {
            case "left", "top" -> 0;
            case "right", "bottom" -> dimension;
            case "center" -> dimension / 2;
            default -> {
                if (value.endsWith("%")) {
                    yield dimension * Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
                }
                yield dimension / 2;
            }
        };
    }

    /**
     * Register transition specs for a node if it declares transition properties.
     */
    private void registerTransitions(SceneNode node) {
        String id = node.id();
        if (id == null || id.isEmpty()) return;

        String property = node.transitionProperty();
        if (property == null || property.isEmpty()) return;

        float duration = node.transitionDurationFloat();
        String easing = node.transitionEasing();
        float delay = node.transitionDelayFloat();

        // Build a TransitionSpec from the resolved longhand fields
        String[] properties = property.split(",\\s*");
        TransitionSpec spec = new TransitionSpec(
                java.util.List.of(properties),
                duration,
                easing != null ? Easing.parse(easing) : Easing.EASE,
                delay);
        animationState.registerTransitions(id, java.util.List.of(spec));
    }

    /**
     * Get an animated float value. Sets the target on AnimationState and returns
     * the current interpolated value.
     */
    private float animatedFloat(SceneNode node, String property, float resolvedValue) {
        String id = node.id();
        if (id == null || id.isEmpty()) return resolvedValue;
        animationState.setTarget(id, property, resolvedValue);
        return (float) animationState.getValue(id, property, resolvedValue);
    }

    /**
     * Get an animated color value. Interpolates each ARGB channel independently.
     */
    private int animatedColor(SceneNode node, String property, int resolvedColor) {
        String id = node.id();
        if (id == null || id.isEmpty() || resolvedColor == -1) return resolvedColor;

        // Encode color as a double for AnimationState (pack ARGB into bits)
        // AnimationState interpolates doubles, so we interpolate each channel separately
        animationState.setTarget(id, property + ".a", (resolvedColor >> 24) & 0xFF);
        animationState.setTarget(id, property + ".r", (resolvedColor >> 16) & 0xFF);
        animationState.setTarget(id, property + ".g", (resolvedColor >> 8) & 0xFF);
        animationState.setTarget(id, property + ".b", resolvedColor & 0xFF);

        int a = clamp((int) animationState.getValue(id, property + ".a", (resolvedColor >> 24) & 0xFF));
        int r = clamp((int) animationState.getValue(id, property + ".r", (resolvedColor >> 16) & 0xFF));
        int g = clamp((int) animationState.getValue(id, property + ".g", (resolvedColor >> 8) & 0xFF));
        int b = clamp((int) animationState.getValue(id, property + ".b", resolvedColor & 0xFF));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
