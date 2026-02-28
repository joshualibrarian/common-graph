package dev.everydaythings.graph.ui.skia;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.Paragraph;
import io.github.humbleui.skija.paragraph.RectHeightMode;
import io.github.humbleui.skija.paragraph.RectWidthMode;
import io.github.humbleui.skija.paragraph.TextBox;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.text.EmojiIconResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Paints a laid-out {@link LayoutNode} tree onto a Skia {@link Canvas}.
 *
 * <p>Traverses the tree and issues Skia draw calls:
 * <ul>
 *   <li>{@link LayoutNode.BoxNode} → drawRect/drawRRect for background, then recurse children</li>
 *   <li>{@link LayoutNode.TextNode} → drawString for text</li>
 *   <li>{@link LayoutNode.ImageNode} → drawString for alt text fallback</li>
 * </ul>
 */
public class SkiaPainter {

    // Default colors for unresolved nodes (ARGB format)
    private static final int COLOR_BG = 0xFF1E1E2E;          // Dark background
    private static final int COLOR_TEXT = 0xFFCDD6F4;         // Light text
    private static final int COLOR_ACCENT = 0xFF89B4FA;       // Shape fill default
    private static final int COLOR_MUTED = 0xFF6C7086;        // Line/separator default

    private final FontCache fontCache;
    private final Map<String, Image> imageCache = new HashMap<>();
    private final Map<String, SVGDOM> svgCache = new HashMap<>();

    public SkiaPainter(FontCache fontCache) {
        this.fontCache = fontCache;
    }

    /**
     * Paint the entire layout tree.
     */
    public void paint(Canvas canvas, LayoutNode root) {
        paintNode(canvas, root);
    }

    private void paintNode(Canvas canvas, LayoutNode node) {
        boolean rotated = node.rotation() != 0;
        if (rotated) {
            canvas.save();
            float cx = node.x() + node.width() * node.transformOriginX();
            float cy = node.y() + node.height() * node.transformOriginY();
            canvas.translate(cx, cy);
            canvas.rotate(node.rotation());
            canvas.translate(-cx, -cy);
        }

        switch (node) {
            case LayoutNode.BoxNode box -> paintBox(canvas, box);
            case LayoutNode.TextNode text -> paintText(canvas, text);
            case LayoutNode.ShapeNode shape -> paintShape(canvas, shape);
            case LayoutNode.ImageNode image -> paintImage(canvas, image);
        }

        if (rotated) {
            canvas.restore();
        }
    }

    private void paintBox(Canvas canvas, LayoutNode.BoxNode box) {
        float x = box.x(), y = box.y(), w = box.width(), h = box.height();
        float bt = box.borderTopWidth(), br = box.borderRightWidth();
        float bb = box.borderBottomWidth(), bl = box.borderLeftWidth();
        float radius = box.borderRadius();

        // Draw background (inside borders)
        if (box.background() != null || box.backgroundColor() != -1) {
            int bgColor = resolveBackgroundColor(box);
            try (Paint paint = new Paint()) {
                paint.setColor(bgColor);
                paint.setAntiAlias(true);
                float bgX = x + bl, bgY = y + bt;
                float bgW = Math.max(0, w - bl - br), bgH = Math.max(0, h - bt - bb);
                if ("circle".equals(box.shapeType())) {
                    float cx = bgX + bgW / 2f, cy = bgY + bgH / 2f;
                    float r = Math.min(bgW, bgH) / 2f;
                    canvas.drawCircle(cx, cy, r, paint);
                } else if (radius > 0) {
                    float innerRadius = Math.max(0, radius - Math.max(bt, bl));
                    canvas.drawRRect(RRect.makeXYWH(bgX, bgY, bgW, bgH, innerRadius), paint);
                } else {
                    canvas.drawRect(Rect.makeXYWH(bgX, bgY, bgW, bgH), paint);
                }
            }
        }

        // Draw borders
        paintBorders(canvas, box);

        // Legal-target dot indicator (empty legal-target squares)
        if (hasStyle(box, "legal-target") && box.children().isEmpty()) {
            try (Paint dotPaint = new Paint()) {
                dotPaint.setColor(0x5546B964);
                dotPaint.setAntiAlias(true);
                float cx = x + w / 2f;
                float cy = y + h / 2f;
                canvas.drawCircle(cx, cy, w * 0.15f, dotPaint);
            }
        }

        // Draw children (clipped to content area — skip clip for STACK to avoid cutting rotated children)
        if (box.direction() != Scene.Direction.STACK) {
            float clipW = Math.max(0, w - bl - br);
            float clipH = Math.max(0, h - bt - bb);
            canvas.save();
            canvas.clipRect(Rect.makeXYWH(x + bl, y + bt, clipW, clipH));

            // Apply scroll offset for scroll containers
            if (box.isScrollContainer()) {
                canvas.translate(0, -box.scrollOffsetY());
            }

            for (LayoutNode child : box.children()) {
                paintNode(canvas, child);
            }

            canvas.restore();

            // Paint overlays (scrollbar shapes) without scroll offset but within clip
            if (!box.overlays().isEmpty()) {
                canvas.save();
                canvas.clipRect(Rect.makeXYWH(x + bl, y + bt, clipW, clipH));
                for (LayoutNode overlay : box.overlays()) {
                    paintNode(canvas, overlay);
                }
                canvas.restore();
            }
        } else {
            // STACK children paint without clipping — rotated children extend beyond bounds
            for (LayoutNode child : box.children()) {
                paintNode(canvas, child);
            }
        }

    }

    private void paintBorders(Canvas canvas, LayoutNode.BoxNode box) {
        float bt = box.borderTopWidth(), br = box.borderRightWidth();
        float bb = box.borderBottomWidth(), bl = box.borderLeftWidth();

        if (bt == 0 && br == 0 && bb == 0 && bl == 0) return;

        float x = box.x(), y = box.y(), w = box.width(), h = box.height();
        float radius = box.borderRadius();

        // Check if all sides are identical (uniform border)
        boolean uniform = bt == br && br == bb && bb == bl && bt > 0;
        String topColor = borderColor(box, "top");
        if (topColor == null) return; // no color info — can't paint borders
        boolean sameColor = uniform
                && topColor.equals(borderColor(box, "right"))
                && topColor.equals(borderColor(box, "bottom"))
                && topColor.equals(borderColor(box, "left"));

        if (uniform && sameColor) {
            // Single stroked shape
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(topColor, COLOR_TEXT));
                paint.setAntiAlias(true);
                paint.setStroke(true);
                paint.setStrokeWidth(bt);
                float inset = bt / 2f;
                if ("circle".equals(box.shapeType())) {
                    float cx = x + w / 2f, cy = y + h / 2f;
                    float r = Math.min(w, h) / 2f - inset;
                    canvas.drawCircle(cx, cy, r, paint);
                } else if (radius > 0) {
                    canvas.drawRRect(RRect.makeXYWH(
                            x + inset, y + inset, w - bt, h - bt, radius), paint);
                } else {
                    canvas.drawRect(Rect.makeXYWH(
                            x + inset, y + inset, w - bt, h - bt), paint);
                }
            }
        } else {
            // Per-side filled rectangle strips
            paintBorderSide(canvas, x, y, w, bt, borderColor(box, "top"), true);         // top
            paintBorderSide(canvas, x + w - br, y, br, h, borderColor(box, "right"), false); // right
            paintBorderSide(canvas, x, y + h - bb, w, bb, borderColor(box, "bottom"), true); // bottom
            paintBorderSide(canvas, x, y, bl, h, borderColor(box, "left"), false);          // left
        }
    }

    private void paintBorderSide(Canvas canvas, float x, float y,
                                 float w, float h, String color, boolean horizontal) {
        if ((horizontal && h <= 0) || (!horizontal && w <= 0)) return;
        try (Paint paint = new Paint()) {
            paint.setColor(parseColor(color, COLOR_TEXT));
            canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
        }
    }

    private String borderColor(LayoutNode.BoxNode box, String side) {
        if (box.border() == null) return null;
        return switch (side) {
            case "top" -> box.border().top().color();
            case "right" -> box.border().right().color();
            case "bottom" -> box.border().bottom().color();
            case "left" -> box.border().left().color();
            default -> null;
        };
    }

    private void paintText(Canvas canvas, LayoutNode.TextNode text) {
        if (text.content().isEmpty()) return;

        int color = resolveTextColor(text);

        // Draw code background if needed
        if (text.backgroundColor() != -1) {
            try (Paint bgPaint = new Paint()) {
                bgPaint.setColor(text.backgroundColor());
                bgPaint.setAntiAlias(true);
                canvas.drawRRect(
                    RRect.makeXYWH(text.x() - 2, text.y(), text.width() + 4, text.height(), 3),
                    bgPaint);
            }
        }

        FontCache.FontProfile profile = fontCache.profileFor(text);
        try (Paragraph para = fontCache.buildParagraph(
                text.content(), profile, color, text.width())) {
            para.paint(canvas, text.x(), text.y());
        }
    }

    private void paintImage(Canvas canvas, LayoutNode.ImageNode image) {
        float x = image.x(), y = image.y(), w = image.width(), h = image.height();

        // Draw circular background if shape is "circle"
        if ("circle".equals(image.shape())) {
            float cx = x + w / 2f, cy = y + h / 2f;
            float radius = Math.min(w, h) / 2f;
            try (Paint bgPaint = new Paint()) {
                bgPaint.setColor(parseColor(
                        image.shapeBackground() != null ? image.shapeBackground() : "#3C3C4E",
                        0xFF3C3C4E));
                bgPaint.setAntiAlias(true);
                canvas.drawCircle(cx, cy, radius, bgPaint);
            }
        }

        boolean shaped = image.shape() != null;
        // Content area: shrink to leave padding inside shape
        float contentScale = shaped ? 0.6f : 1.0f;
        float contentSize = Math.min(w, h) * contentScale;
        float contentX = shaped ? x + (w - contentSize) / 2f : x;
        float contentY = shaped ? y + (h - contentSize) / 2f : y;

        // Try explicit/emoji-mapped resource first
        String resource = EmojiIconResolver.resolveResource(
                image.hasResource() ? image.resource() : null, image.alt());
        if (resource != null && !resource.isBlank()) {

            // SVG resources — render as vector via SVGDOM
            if (resource.endsWith(".svg")) {
                SVGDOM svg = loadSvg(resource);
                if (svg != null) {
                    float targetSize = shaped ? contentSize : (h > 0 ? h : 40);
                    canvas.save();
                    canvas.translate(shaped ? contentX : x, shaped ? contentY : y);
                    svg.setContainerSize(targetSize, targetSize);
                    svg.render(canvas);
                    canvas.restore();
                    return;
                }
            }

            // Raster resources (PNG, JPEG, WebP, etc.)
            Image skiaImage = loadImage(resource);
            if (skiaImage != null) {
                float targetSize = shaped ? contentSize : (h > 0 ? h : 20);
                float scale = targetSize / skiaImage.getHeight();
                float drawW = skiaImage.getWidth() * scale;
                float drawH = targetSize;
                float drawX = shaped ? contentX : x;
                float drawY = shaped ? contentY : y;
                canvas.drawImageRect(skiaImage,
                        Rect.makeWH(skiaImage.getWidth(), skiaImage.getHeight()),
                        Rect.makeXYWH(drawX, drawY, drawW, drawH),
                        SamplingMode.LINEAR, null, true);
                return;
            }
        }

        // Fall back to emoji text rendering
        String alt = image.alt();
        int emojiColor = image.hasTintColor() ? image.tintColor() : COLOR_TEXT;
        float emojiFontSize = shaped
                ? Math.min(w, h) * 0.6f
                : Math.min(w, h);
        if (emojiFontSize <= 0) {
            emojiFontSize = fontCache.baseFontSize();
        }
        FontCache.FontProfile emojiProfile = new FontCache.FontProfile(
                fontCache.emojiProfile().families(), emojiFontSize);
        if (shaped) {
            // Measure emoji and center it within the shape
            try (Paragraph para = fontCache.buildParagraph(
                    alt, emojiProfile, emojiColor, Float.MAX_VALUE)) {
                float[] bounds = tightBounds(para, alt.length());
                float textW = bounds[2] - bounds[0];
                float textH = bounds[3] - bounds[1];
                float advance = Math.max(0f, para.getMaxIntrinsicWidth());
                float visualCenterX = (bounds[0] + bounds[2]) * 0.5f;
                float logicalCenterX = advance * 0.5f;
                float correction = clamp(visualCenterX - logicalCenterX,
                        -emojiFontSize * 0.18f, emojiFontSize * 0.18f);
                float centeredX = x + w * 0.5f - logicalCenterX - correction;
                float textX = centeredX;
                float textY = y + (h - textH) / 2f - bounds[1];
                para.paint(canvas, textX, textY);
            }
        } else {
            try (Paragraph para = fontCache.buildParagraph(
                    alt, emojiProfile, emojiColor, Float.MAX_VALUE)) {
                para.paint(canvas, x, y);
            }
        }
    }

    private static float[] tightBounds(Paragraph para, int endIndex) {
        TextBox[] boxes = para.getRectsForRange(0, endIndex, RectHeightMode.TIGHT, RectWidthMode.TIGHT);
        if (boxes == null || boxes.length == 0) {
            return new float[] {0f, 0f, para.getMaxIntrinsicWidth(), para.getHeight()};
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (TextBox box : boxes) {
            io.github.humbleui.types.Rect r = box.getRect();
            minX = Math.min(minX, r.getLeft());
            minY = Math.min(minY, r.getTop());
            maxX = Math.max(maxX, r.getRight());
            maxY = Math.max(maxY, r.getBottom());
        }
        return new float[] {minX, minY, maxX, maxY};
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private SVGDOM loadSvg(String resourcePath) {
        SVGDOM cached = svgCache.get(resourcePath);
        if (cached != null) return cached;

        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            SVGDOM svg = new SVGDOM(Data.makeFromBytes(bytes));
            svgCache.put(resourcePath, svg);
            return svg;
        } catch (IOException e) {
            return null;
        }
    }

    private Image loadImage(String resourcePath) {
        Image cached = imageCache.get(resourcePath);
        if (cached != null) return cached;

        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] data = in.readAllBytes();
            Image img = Image.makeDeferredFromEncodedBytes(data);
            imageCache.put(resourcePath, img);
            return img;
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== Shape Painting ====================

    private void paintShape(Canvas canvas, LayoutNode.ShapeNode shape) {
        float x = shape.x(), y = shape.y(), w = shape.width(), h = shape.height();

        // Apply 2D projection of 3D transform if present
        boolean saved = false;
        if (shape.hasTransform3d()) {
            canvas.save();
            saved = true;
            canvas.translate(shape.transform3dX(), shape.transform3dY());
            if (shape.transform3dYaw() != 0) {
                canvas.translate(x + w / 2f, y + h / 2f);
                canvas.rotate((float) shape.transform3dYaw());
                canvas.translate(-(x + w / 2f), -(y + h / 2f));
            }
            if (shape.transform3dScaleX() != 1 || shape.transform3dScaleY() != 1) {
                canvas.translate(x + w / 2f, y + h / 2f);
                canvas.scale(shape.transform3dScaleX(), shape.transform3dScaleY());
                canvas.translate(-(x + w / 2f), -(y + h / 2f));
            }
        }

        switch (shape.shapeType()) {
            case "rectangle", "plane" -> paintRectangleShape(canvas, shape, x, y, w, h);
            case "circle", "sphere" -> paintCircleShape(canvas, shape, x, y, w, h);
            case "ellipse" -> paintEllipseShape(canvas, shape, x, y, w, h);
            case "point" -> paintPointShape(canvas, shape, x, y, w, h);
            case "polygon" -> paintPolygonShape(canvas, shape, x, y, w, h);
            case "line" -> paintLineShape(canvas, shape, x, y, w, h);
            case "path" -> paintPathShape(canvas, shape, x, y, w, h);
            case "cone" -> paintConeShape(canvas, shape, x, y, w, h);
            case "capsule" -> paintCapsuleShape(canvas, shape, x, y, w, h);
            default -> {} // unknown shape, no-op
        }

        if (saved) {
            canvas.restore();
        }
    }

    private void paintRectangleShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                     float x, float y, float w, float h) {
        float radius = resolveCornerRadius(shape.cornerRadius(), h);

        // Fill
        if (!shape.fill().isEmpty()) {
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                paint.setAntiAlias(true);
                if (radius > 0) {
                    canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), paint);
                } else {
                    canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                }
            }
        }

        // Stroke
        if (!shape.stroke().isEmpty()) {
            float sw = parseStrokeWidth(shape.strokeWidth());
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                paint.setAntiAlias(true);
                paint.setMode(PaintMode.STROKE);
                paint.setStrokeWidth(sw);
                float inset = sw / 2f;
                if (radius > 0) {
                    canvas.drawRRect(RRect.makeXYWH(x + inset, y + inset,
                            w - sw, h - sw, radius), paint);
                } else {
                    canvas.drawRect(Rect.makeXYWH(x + inset, y + inset,
                            w - sw, h - sw), paint);
                }
            }
        }
    }

    private void paintCircleShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                  float x, float y, float w, float h) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float radius = Math.min(w, h) / 2f;

        // Fill
        if (!shape.fill().isEmpty()) {
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                paint.setAntiAlias(true);
                canvas.drawCircle(cx, cy, radius, paint);
            }
        }

        // Stroke
        if (!shape.stroke().isEmpty()) {
            float sw = parseStrokeWidth(shape.strokeWidth());
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                paint.setAntiAlias(true);
                paint.setMode(PaintMode.STROKE);
                paint.setStrokeWidth(sw);
                canvas.drawCircle(cx, cy, radius - sw / 2f, paint);
            }
        }
    }

    private void paintLineShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                float x, float y, float w, float h) {
        float sw = parseStrokeWidth(shape.strokeWidth());
        String color = !shape.stroke().isEmpty() ? shape.stroke()
                     : !shape.fill().isEmpty() ? shape.fill()
                     : "";
        if (color.isEmpty()) color = "gray";

        try (Paint paint = new Paint()) {
            paint.setColor(parseColor(color, COLOR_MUTED));
            paint.setAntiAlias(true);
            paint.setStrokeWidth(sw);

            // Parse d attribute for endpoints: "x1,y1 x2,y2" (relative to bounding box)
            String d = shape.path();
            if (!d.isEmpty() && d.contains(",")) {
                String[] points = d.trim().split("\\s+");
                if (points.length >= 2) {
                    String[] p1 = points[0].split(",");
                    String[] p2 = points[1].split(",");
                    if (p1.length == 2 && p2.length == 2) {
                        float x1 = x + Float.parseFloat(p1[0]) * w / 100f;
                        float y1 = y + Float.parseFloat(p1[1]) * h / 100f;
                        float x2 = x + Float.parseFloat(p2[0]) * w / 100f;
                        float y2 = y + Float.parseFloat(p2[1]) * h / 100f;
                        canvas.drawLine(x1, y1, x2, y2, paint);
                        return;
                    }
                }
            }

            // Default: horizontal line at midY
            float midY = y + h / 2f;
            canvas.drawLine(x, midY, x + w, midY, paint);
        }
    }

    private void paintPathShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                float x, float y, float w, float h) {
        if (shape.path().isEmpty()) return;

        try (Path svgPath = Path.makeFromSVGString(shape.path())) {
            if (svgPath == null) return;

            // Scale path to fit bounds
            io.github.humbleui.types.Rect pathBounds = svgPath.getBounds();
            float pathW = pathBounds.getWidth();
            float pathH = pathBounds.getHeight();
            if (pathW <= 0 || pathH <= 0) return;

            canvas.save();
            canvas.translate(x, y);
            canvas.scale(w / pathW, h / pathH);
            canvas.translate(-pathBounds.getLeft(), -pathBounds.getTop());

            // Fill
            if (!shape.fill().isEmpty()) {
                try (Paint paint = new Paint()) {
                    paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                    paint.setAntiAlias(true);
                    canvas.drawPath(svgPath, paint);
                }
            }

            // Stroke
            if (!shape.stroke().isEmpty()) {
                try (Paint paint = new Paint()) {
                    paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                    paint.setAntiAlias(true);
                    paint.setMode(PaintMode.STROKE);
                    paint.setStrokeWidth(parseStrokeWidth(shape.strokeWidth()));
                    canvas.drawPath(svgPath, paint);
                }
            }

            canvas.restore();
        }
    }

    private void paintPointShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                 float x, float y, float w, float h) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float sw = parseStrokeWidth(shape.strokeWidth());
        float radius = Math.max(sw / 2f, 1.5f);
        String color = !shape.fill().isEmpty() ? shape.fill()
                     : !shape.stroke().isEmpty() ? shape.stroke()
                     : "";
        if (color.isEmpty()) color = "gray";

        try (Paint paint = new Paint()) {
            paint.setColor(parseColor(color, COLOR_TEXT));
            paint.setAntiAlias(true);
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    private void paintEllipseShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                   float x, float y, float w, float h) {
        // Fill
        if (!shape.fill().isEmpty()) {
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                paint.setAntiAlias(true);
                canvas.drawOval(Rect.makeXYWH(x, y, w, h), paint);
            }
        }

        // Stroke
        if (!shape.stroke().isEmpty()) {
            float sw = parseStrokeWidth(shape.strokeWidth());
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                paint.setAntiAlias(true);
                paint.setMode(PaintMode.STROKE);
                paint.setStrokeWidth(sw);
                float inset = sw / 2f;
                canvas.drawOval(Rect.makeXYWH(x + inset, y + inset,
                        w - sw, h - sw), paint);
            }
        }
    }

    private void paintPolygonShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                   float x, float y, float w, float h) {
        int sides = 6; // default hexagon
        String d = shape.path();
        if (!d.isEmpty()) {
            try {
                sides = Integer.parseInt(d.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (sides < 3) sides = 3;

        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float rx = w / 2f;
        float ry = h / 2f;

        io.github.humbleui.types.Point[] pts = new io.github.humbleui.types.Point[sides];
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides - Math.PI / 2; // start at top
            pts[i] = new io.github.humbleui.types.Point(
                    cx + rx * (float) Math.cos(angle),
                    cy + ry * (float) Math.sin(angle));
        }

        try (Path path = Path.makePolygon(pts, true)) {
            // Fill
            if (!shape.fill().isEmpty()) {
                try (Paint paint = new Paint()) {
                    paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                    paint.setAntiAlias(true);
                    canvas.drawPath(path, paint);
                }
            }

            // Stroke
            if (!shape.stroke().isEmpty()) {
                try (Paint paint = new Paint()) {
                    paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                    paint.setAntiAlias(true);
                    paint.setMode(PaintMode.STROKE);
                    paint.setStrokeWidth(parseStrokeWidth(shape.strokeWidth()));
                    canvas.drawPath(path, paint);
                }
            }
        }
    }

    private void paintConeShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                float x, float y, float w, float h) {
        // 2D projection of a cone: isoceles triangle inscribed in bounding box
        io.github.humbleui.types.Point[] pts = new io.github.humbleui.types.Point[] {
                new io.github.humbleui.types.Point(x + w / 2f, y),      // apex
                new io.github.humbleui.types.Point(x + w, y + h),        // bottom right
                new io.github.humbleui.types.Point(x, y + h)             // bottom left
        };

        try (Path path = Path.makePolygon(pts, true)) {
            if (!shape.fill().isEmpty()) {
                try (Paint paint = new Paint()) {
                    paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                    paint.setAntiAlias(true);
                    canvas.drawPath(path, paint);
                }
            }
            if (!shape.stroke().isEmpty()) {
                try (Paint paint = new Paint()) {
                    paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                    paint.setAntiAlias(true);
                    paint.setMode(PaintMode.STROKE);
                    paint.setStrokeWidth(parseStrokeWidth(shape.strokeWidth()));
                    canvas.drawPath(path, paint);
                }
            }
        }
    }

    private void paintCapsuleShape(Canvas canvas, LayoutNode.ShapeNode shape,
                                   float x, float y, float w, float h) {
        // 2D projection of a capsule: rounded rectangle with corner radius = width/2
        float radius = Math.min(w, h) / 2f;

        if (!shape.fill().isEmpty()) {
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.fill(), COLOR_ACCENT));
                paint.setAntiAlias(true);
                canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), paint);
            }
        }
        if (!shape.stroke().isEmpty()) {
            float sw = parseStrokeWidth(shape.strokeWidth());
            try (Paint paint = new Paint()) {
                paint.setColor(parseColor(shape.stroke(), COLOR_TEXT));
                paint.setAntiAlias(true);
                paint.setMode(PaintMode.STROKE);
                paint.setStrokeWidth(sw);
                float inset = sw / 2f;
                canvas.drawRRect(RRect.makeXYWH(x + inset, y + inset,
                        w - sw, h - sw, radius), paint);
            }
        }
    }

    private float resolveCornerRadius(String radius, float height) {
        if (radius == null || radius.isEmpty()) return 0;
        return switch (radius) {
            case "small" -> 4;
            case "medium" -> 8;
            case "large" -> 16;
            case "pill", "circle" -> height / 2f;
            default -> {
                try {
                    yield Float.parseFloat(radius.replaceAll("[^0-9.]", ""));
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
        };
    }

    private float parseStrokeWidth(String strokeWidth) {
        if (strokeWidth == null || strokeWidth.isEmpty()) return 1;
        try {
            return Float.parseFloat(strokeWidth.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // ==================== Color Resolution ====================

    private int resolveTextColor(LayoutNode node) {
        return node.color() != -1 ? node.color() : COLOR_TEXT;
    }

    private int resolveBackgroundColor(LayoutNode node) {
        if (node.backgroundColor() != -1) return node.backgroundColor();
        if (node instanceof LayoutNode.BoxNode box) {
            // Chess square state blending (selected/legal-target modifies base bg)
            if (box.backgroundColor() != -1 && hasStyle(box, "selected")) {
                return blendColors(box.backgroundColor(), 0x6646B964);
            }
            if (box.backgroundColor() != -1 && hasStyle(box, "legal-target")) {
                return blendColors(box.backgroundColor(), 0x3346B964);
            }
            if (box.background() != null) {
                return parseColor(box.background(), COLOR_BG);
            }
        }
        return COLOR_BG;
    }

    /**
     * Blend an overlay color (with alpha) onto a base color.
     *
     * <p>Uses standard alpha compositing (source-over). The overlay's alpha
     * channel controls blending intensity.
     */
    private static int blendColors(int base, int overlay) {
        int oa = (overlay >>> 24) & 0xFF;
        int or = (overlay >>> 16) & 0xFF;
        int og = (overlay >>> 8) & 0xFF;
        int ob = overlay & 0xFF;

        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;

        float alpha = oa / 255f;
        int r = Math.round(or * alpha + br * (1 - alpha));
        int g = Math.round(og * alpha + bg * (1 - alpha));
        int b = Math.round(ob * alpha + bb * (1 - alpha));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private boolean hasStyle(LayoutNode node, String style) {
        return node.styles().contains(style);
    }

    /**
     * Parse a CSS color name or hex value to ARGB int.
     * Phase 1: supports hex (#RRGGBB) and a few named colors.
     */
    private int parseColor(String color, int fallback) {
        if (color == null || color.isEmpty()) return fallback;

        if (color.startsWith("#")) {
            try {
                String hex = color.substring(1);
                if (hex.length() == 6) {
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                } else if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        return switch (color.toLowerCase()) {
            case "black" -> 0xFF000000;
            case "white" -> 0xFFFFFFFF;
            case "red" -> 0xFFFF0000;
            case "green" -> 0xFF00FF00;
            case "blue" -> 0xFF0000FF;
            case "gray", "grey" -> 0xFF808080;
            case "transparent" -> 0x00000000;
            default -> fallback;
        };
    }
}
