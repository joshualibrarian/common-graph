package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.TransitionSpec;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Skia implementation of SurfaceRenderer.
 *
 * <p>Builds a {@link LayoutNode} tree from render instructions emitted by
 * Surface.render(). The tree is then laid out by {@link LayoutEngine} and
 * painted by {@link SkiaPainter}.
 *
 * <p>Uses a container-stack pattern:
 * <ul>
 *   <li>{@link #beginBox} pushes a new BoxNode onto the stack</li>
 *   <li>{@link #endBox} pops it and adds it as a child of the parent</li>
 *   <li>{@link #text} creates a TextNode and adds it to the current container</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
 * surface.render(renderer);
 * LayoutNode.BoxNode root = renderer.result();
 * // Then: engine.layout(root, width, height); painter.paint(canvas, root);
 * </pre>
 */
public class SkiaSurfaceRenderer implements SurfaceRenderer {

    // Container stack for nested structures
    private final Deque<LayoutNode.BoxNode> containerStack = new ArrayDeque<>();

    // The root container
    private final LayoutNode.BoxNode root;

    // Render context for unit resolution
    private final RenderContext renderContext;

    // Current metadata for next element
    private String nextType;
    private String nextId;
    private boolean nextEditable;
    private final List<LayoutNode.PendingEvent> pendingEvents = new ArrayList<>();

    // Pending transition specs for next element
    private List<TransitionSpec> nextTransitions;

    // Pending gap for next box
    private String pendingGap;

    // Pending maxWidth for next box
    private String pendingMaxWidth;

    // Pending model state (for 3D image hint)
    private String pendingModelResource;
    private int pendingModelColor = -1;

    // Pending tint color for next image glyph (0 = default)
    private int pendingTint = 0;

    // Pending rotation state (for rotated containers)
    private float pendingRotation;
    private boolean hasPendingRotation;

    // Pending transform-origin state
    private float pendingTransformOriginX = 0.5f;
    private float pendingTransformOriginY = 0.5f;

    // Pending aspect ratio for next box
    private float pendingAspectRatio;

    // Pending text font size for next text node
    private String pendingTextFontSize;
    // Pending text font family for next text/container node
    private String pendingTextFontFamily;

    // Pending overflow for next box
    private String pendingOverflow;
    private String pendingMaxHeight;

    // Pending elevation state (for 3D box hint)
    private double pendingElevation;
    private boolean pendingElevationSolid = true;
    private boolean hasPendingElevation;

    // Pending shape state (for container+shape composition)
    private String pendingShapeType;
    private String pendingCornerRadius;
    private String pendingShapeFill;
    private String pendingShapeStroke;
    private String pendingShapeStrokeWidth;
    private String pendingShapeWidth;
    private String pendingShapeHeight;

    // Container query skip depth (> 0 = skipping nested content)
    private int querySkipDepth = 0;

    // Event handler (action, target) -> dispatch
    private BiConsumer<String, String> eventHandler;

    public SkiaSurfaceRenderer() {
        this(RenderContext.gui());
    }

    public SkiaSurfaceRenderer(RenderContext renderContext) {
        this.renderContext = renderContext;
        this.root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
        containerStack.push(root);
    }

    /**
     * Set the event handler for all surface events.
     */
    public SkiaSurfaceRenderer onEvent(BiConsumer<String, String> handler) {
        this.eventHandler = handler;
        return this;
    }

    /**
     * Get the rendered layout tree.
     */
    public LayoutNode.BoxNode result() {
        return root;
    }

    /**
     * Get the render context used by this renderer.
     */
    public RenderContext renderContext() {
        return renderContext;
    }

    // ==================== Text Primitive ====================

    @Override
    public void text(String content, List<String> styles) {
        if (querySkipDepth > 0) return;
        flushPendingShape();
        var text = new LayoutNode.TextNode(content, styles);
        applyPendingTextStyle(text);
        applyMetadata(text);
        addToCurrentContainer(text);
    }

    @Override
    public void formattedText(String content, String format, List<String> styles) {
        if (querySkipDepth > 0) return;
        flushPendingShape();
        var text = new LayoutNode.TextNode(content, format, styles);
        applyPendingTextStyle(text);
        applyMetadata(text);
        addToCurrentContainer(text);
    }

    @Override
    public void richText(List<TextSpan> spans, List<String> paragraphStyles) {
        if (querySkipDepth > 0) return;
        flushPendingShape();
        // Concatenate span text for the content field (backward compat for measurement)
        StringBuilder sb = new StringBuilder();
        for (TextSpan span : spans) sb.append(span.text());
        var text = new LayoutNode.TextNode(sb.toString(), paragraphStyles);
        text.spans(spans);
        applyPendingTextStyle(text);
        applyMetadata(text);
        addToCurrentContainer(text);
    }

    private void applyPendingTextStyle(LayoutNode.TextNode text) {
        if (pendingTextFontSize != null) {
            text.fontSizeSpec(pendingTextFontSize);
            pendingTextFontSize = null;
        }
        if (pendingTextFontFamily != null && !pendingTextFontFamily.isEmpty()) {
            text.fontFamily(pendingTextFontFamily);
            pendingTextFontFamily = null;
        }
    }

    // ==================== Image Primitive ====================

    @Override
    public void image(String alt, ContentID image, ContentID solid, String resource,
                      String size, String fit, List<String> styles) {
        if (querySkipDepth > 0) return;
        // Transfer pending shape to image node (from ImageSurface.render() emitting shape before image)
        var imageNode = new LayoutNode.ImageNode(alt, resource, size, fit, styles);
        if (pendingShapeType != null) {
            imageNode.shape(pendingShapeType);
            imageNode.shapeBackground(pendingShapeFill);
            clearPendingShape();
        }
        // Transfer pending model to image node (3D hint)
        if (pendingModelResource != null) {
            imageNode.modelResource(pendingModelResource);
            imageNode.modelColor(pendingModelColor);
            pendingModelResource = null;
            pendingModelColor = -1;
        }
        // Transfer pending tint color
        if (pendingTint != 0) {
            imageNode.tintColor(pendingTint);
            pendingTint = 0;
        }
        applyMetadata(imageNode);
        addToCurrentContainer(imageNode);
    }

    @Override
    public void image(String alt, ContentID image, ContentID solid,
                      String size, String fit, List<String> styles) {
        image(alt, image, solid, null, size, fit, styles);
    }

    // ==================== Container Primitive ====================

    @Override
    public void beginBox(Scene.Direction direction, List<String> styles) {
        if (querySkipDepth > 0) return;
        var box = new LayoutNode.BoxNode(direction, styles);
        applyPendingShapeToBox(box);
        applyPendingElevationToBox(box);
        applyPendingRotationToBox(box);
        applyPendingAspectRatio(box);
        applyPendingOverflow(box);
        applyPendingFontSize(box);
        applyPendingFontFamily(box);
        applyMetadata(box);

        // Gap (from preceding gap() call)
        if (pendingGap != null) {
            box.gapSpec(pendingGap);
            pendingGap = null;
        }

        // MaxWidth (from preceding maxWidth() call)
        if (pendingMaxWidth != null) {
            box.maxWidthSpec(pendingMaxWidth);
            pendingMaxWidth = null;
        }

        addToCurrentContainer(box);
        containerStack.push(box);
    }

    @Override
    public void beginBox(Scene.Direction direction, List<String> styles,
                         BoxBorder border, String background,
                         String width, String height, String padding) {
        if (querySkipDepth > 0) return;
        var box = new LayoutNode.BoxNode(direction, styles);
        applyPendingShapeToBox(box);
        applyPendingElevationToBox(box);
        applyPendingRotationToBox(box);
        applyPendingAspectRatio(box);
        applyPendingOverflow(box);
        applyPendingFontSize(box);
        applyPendingFontFamily(box);
        applyMetadata(box);

        // Visual properties
        if (border != null && border.isVisible()) {
            box.border(border);
            resolveBorder(box, border);
        }
        if (background != null && !background.isEmpty()) {
            box.background(background);
        }

        // Store raw size specs — LayoutEngine resolves all units
        if (width != null && !width.isEmpty()) {
            box.widthSpec(width);
        }
        if (height != null && !height.isEmpty()) {
            box.heightSpec(height);
        }

        // Gap (from preceding gap() call)
        if (pendingGap != null) {
            box.gapSpec(pendingGap);
            pendingGap = null;
        }

        // MaxWidth (from preceding maxWidth() call)
        if (pendingMaxWidth != null) {
            box.maxWidthSpec(pendingMaxWidth);
            pendingMaxWidth = null;
        }

        // Padding
        if (padding != null && !padding.isEmpty()) {
            parsePadding(box, padding);
        }

        addToCurrentContainer(box);
        containerStack.push(box);
    }

    @Override
    public void endBox() {
        if (querySkipDepth > 0) return;
        // Flush any pending shape that wasn't consumed by another primitive.
        // This handles standalone @Surface.Shape children inside containers —
        // without this, the shape props leak into the next beginBox() as background.
        flushPendingShape();
        if (containerStack.size() > 1) {
            containerStack.pop();
        }
    }

    // ==================== Container Queries ====================

    @Override
    public boolean beginQuery(String condition) {
        if (querySkipDepth > 0) {
            querySkipDepth++;
            return false;
        }
        // Get available width from the current container's resolved size or render context.
        // During tree construction, sizes may not yet be resolved (layout hasn't run).
        // If no size is available, default to showing the content (all branches pass).
        float availablePx = renderContext.viewportWidth();
        LayoutNode.BoxNode parent = containerStack.peek();
        if (parent != null && parent.width() > 0) {
            availablePx = parent.width();
        }
        if (availablePx <= 0) {
            // No size context yet — assume large (optimistic default).
            // ">=" queries pass, "<" queries fail, showing the full/default layout.
            availablePx = Float.MAX_VALUE;
        }
        if (!evaluateQueryCondition(condition, availablePx)) {
            querySkipDepth = 1;
            return false;
        }
        return true;
    }

    @Override
    public void endQuery() {
        if (querySkipDepth > 0) {
            querySkipDepth--;
        }
    }

    /**
     * Evaluate a container query condition against available width in pixels.
     */
    private boolean evaluateQueryCondition(String condition, float availablePx) {
        String[] parts = condition.trim().split("\\s+", 3);
        if (parts.length != 3) return true;

        String property = parts[0];
        String op = parts[1];
        String valueStr = parts[2];

        if (!"width".equals(property) && !"height".equals(property)) return true;

        // Parse value and unit
        int i = 0;
        while (i < valueStr.length() && (Character.isDigit(valueStr.charAt(i)) || valueStr.charAt(i) == '.')) {
            i++;
        }
        if (i == 0) return true;
        double value = Double.parseDouble(valueStr.substring(0, i));
        String unit = valueStr.substring(i).trim();

        // Convert to pixels
        double threshold;
        switch (unit) {
            case "px", "" -> threshold = value;
            case "ch" -> threshold = value * renderContext.baseFontSize() * 0.6; // ch ≈ 0.6em
            case "em" -> threshold = value * renderContext.baseFontSize();
            case "%" -> threshold = availablePx * value / 100.0;
            default -> threshold = value;
        }

        return switch (op) {
            case ">=" -> availablePx >= threshold;
            case "<=" -> availablePx <= threshold;
            case ">" -> availablePx > threshold;
            case "<" -> availablePx < threshold;
            case "==" -> Math.abs(availablePx - threshold) < 0.5;
            default -> true;
        };
    }

    // ==================== Metadata ====================

    @Override
    public void type(String type) {
        this.nextType = type;
    }

    @Override
    public void id(String id) {
        this.nextId = id;
    }

    @Override
    public void editable(boolean editable) {
        this.nextEditable = editable;
    }

    @Override
    public void transitions(List<TransitionSpec> specs) {
        if (specs != null && !specs.isEmpty()) {
            this.nextTransitions = specs;
        }
    }

    // ==================== Events ====================

    @Override
    public void event(String on, String action, String target) {
        pendingEvents.add(new LayoutNode.PendingEvent(on, action, target != null ? target : ""));
    }

    // ==================== Rotation ====================

    @Override
    public void transformOrigin(float xFrac, float yFrac) {
        if (querySkipDepth > 0) return;
        this.pendingTransformOriginX = xFrac;
        this.pendingTransformOriginY = yFrac;
    }

    @Override
    public void rotation(double degrees) {
        if (querySkipDepth > 0) return;
        this.pendingRotation = (float) degrees;
        this.hasPendingRotation = true;
    }

    // ==================== Text Font Size ====================

    @Override
    public void textFontSize(String spec) {
        if (querySkipDepth > 0) return;
        this.pendingTextFontSize = spec;
    }

    @Override
    public void textFontFamily(String family) {
        if (querySkipDepth > 0) return;
        this.pendingTextFontFamily = (family != null && !family.isBlank()) ? family : null;
    }

    // ==================== Aspect Ratio ====================

    @Override
    public void aspectRatio(String ratio) {
        if (querySkipDepth > 0) return;
        this.pendingAspectRatio = parseAspectRatio(ratio);
    }

    private static float parseAspectRatio(String ratio) {
        if (ratio == null || ratio.isEmpty()) return 0;
        int slash = ratio.indexOf('/');
        if (slash >= 0) {
            try {
                float num = Float.parseFloat(ratio.substring(0, slash).trim());
                float den = Float.parseFloat(ratio.substring(slash + 1).trim());
                return den > 0 ? num / den : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Float.parseFloat(ratio.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== Gap ====================

    @Override
    public void gap(String gap) {
        if (querySkipDepth > 0) return;
        this.pendingGap = gap;
    }

    // ==================== MaxWidth ====================

    @Override
    public void maxWidth(String maxWidth) {
        if (querySkipDepth > 0) return;
        this.pendingMaxWidth = maxWidth;
    }

    // ==================== Overflow ====================

    @Override
    public void overflow(String overflow) {
        if (querySkipDepth > 0) return;
        this.pendingOverflow = overflow;
    }

    @Override
    public void maxHeight(String maxHeight) {
        if (querySkipDepth > 0) return;
        this.pendingMaxHeight = maxHeight;
    }

    // ==================== Model (3D hint) ====================

    @Override
    public void model(String modelResource, int modelColor) {
        if (querySkipDepth > 0) return;
        this.pendingModelResource = modelResource;
        this.pendingModelColor = modelColor;
    }

    // ==================== Tint (glyph color) ====================

    @Override
    public void tint(int argb) {
        if (querySkipDepth > 0) return;
        this.pendingTint = argb;
    }

    // ==================== Elevation (3D hint) ====================

    @Override
    public void elevation(double height, boolean solid) {
        if (querySkipDepth > 0) return;
        this.pendingElevation = height;
        this.pendingElevationSolid = solid;
        this.hasPendingElevation = true;
    }

    // ==================== Shape ====================

    @Override
    public void shapeSize(String width, String height) {
        if (querySkipDepth > 0) return;
        this.pendingShapeWidth = width;
        this.pendingShapeHeight = height;
    }

    @Override
    public void shape(String type, String cornerRadius, String fill,
                      String stroke, String strokeWidth, String path) {
        if (querySkipDepth > 0) return;
        // Stash shape properties — if a beginBox() follows immediately,
        // these will be applied to the BoxNode (container+shape composition).
        // If no beginBox() follows before the next primitive, the stashed
        // state is consumed by flushPendingShape() as a standalone ShapeNode.
        pendingShapeType = type;
        pendingCornerRadius = cornerRadius;
        pendingShapeFill = fill;
        pendingShapeStroke = stroke;
        pendingShapeStrokeWidth = strokeWidth;
    }

    // ==================== Helpers ====================

    /**
     * If shape state is pending and not consumed by a beginBox(), flush it
     * as a standalone ShapeNode before emitting other primitives.
     */
    private void flushPendingShape() {
        if (pendingShapeType != null) {
            var shape = new LayoutNode.ShapeNode(
                    pendingShapeType, pendingCornerRadius, pendingShapeFill,
                    pendingShapeStroke, pendingShapeStrokeWidth, null, List.of());
            // Store raw specs for deferred % resolution by LayoutEngine.
            // Pre-resolve non-% values to explicitWidth/Height for backward compat.
            if (pendingShapeWidth != null && !pendingShapeWidth.isEmpty()) {
                shape.widthSpec(pendingShapeWidth);
                if (!pendingShapeWidth.contains("%")) {
                    shape.explicitWidth(parsePixels(pendingShapeWidth));
                }
            }
            if (pendingShapeHeight != null && !pendingShapeHeight.isEmpty()) {
                shape.heightSpec(pendingShapeHeight);
                if (!pendingShapeHeight.contains("%")) {
                    shape.explicitHeight(parsePixels(pendingShapeHeight));
                }
            }
            // Apply pending rotation/transformOrigin to shape
            if (pendingTransformOriginX != 0.5f || pendingTransformOriginY != 0.5f) {
                shape.transformOriginX(pendingTransformOriginX);
                shape.transformOriginY(pendingTransformOriginY);
                pendingTransformOriginX = 0.5f;
                pendingTransformOriginY = 0.5f;
            }
            if (hasPendingRotation) {
                shape.rotation(pendingRotation);
                hasPendingRotation = false;
                pendingRotation = 0;
            }
            applyMetadata(shape);
            addToCurrentContainer(shape);
            clearPendingShape();
        }
    }

    /**
     * Apply pending shape properties to a BoxNode (container+shape composition).
     */
    private void applyPendingShapeToBox(LayoutNode.BoxNode box) {
        if (pendingShapeType == null) return;

        // Store shape type on box (for circle/pill rendering)
        box.shapeType(pendingShapeType);

        // Map shape fill to box background
        if (pendingShapeFill != null && !pendingShapeFill.isEmpty() && box.background() == null) {
            box.background(pendingShapeFill);
        }

        // Map cornerRadius to box borderRadius
        if (pendingCornerRadius != null && !pendingCornerRadius.isEmpty()) {
            float radius = resolveCornerRadius(pendingCornerRadius);
            if (radius > 0) {
                box.borderRadius(radius);
            }
        }

        // Map stroke to border (if no border already set)
        if (pendingShapeStroke != null && !pendingShapeStroke.isEmpty()
                && box.borderTopWidth() == 0) {
            String swStr = (pendingShapeStrokeWidth != null && !pendingShapeStrokeWidth.isEmpty())
                    ? pendingShapeStrokeWidth : "1px";
            float sw = 1.0f;
            SizeValue sv = SizeValue.parse(swStr);
            if (sv != null) sw = (float) sv.toPixels(renderContext);
            box.borderWidths(sw, sw, sw, sw);
            // Also set the BoxBorder so paintBorders can resolve colors
            BoxBorder.BorderSide side = new BoxBorder.BorderSide("solid", swStr, pendingShapeStroke);
            box.border(new BoxBorder(side, side, side, side, null));
        }

        clearPendingShape();
    }

    private float resolveCornerRadius(String radius) {
        if (radius == null || radius.isEmpty()) return 0;
        return switch (radius) {
            case "small" -> 4;
            case "medium" -> 8;
            case "large" -> 16;
            case "pill" -> 9999; // will be clamped to height/2 by painter
            case "circle" -> 9999;
            default -> {
                SizeValue sv = SizeValue.parse(radius);
                yield sv != null ? (float) sv.toPixels(renderContext) : 0;
            }
        };
    }

    /**
     * Apply pending rotation to a BoxNode.
     */
    private void applyPendingRotationToBox(LayoutNode.BoxNode box) {
        // Always apply transform-origin (even without rotation, for future transforms)
        if (pendingTransformOriginX != 0.5f || pendingTransformOriginY != 0.5f) {
            box.transformOriginX(pendingTransformOriginX);
            box.transformOriginY(pendingTransformOriginY);
            pendingTransformOriginX = 0.5f;
            pendingTransformOriginY = 0.5f;
        }
        if (!hasPendingRotation) return;
        box.rotation(pendingRotation);
        hasPendingRotation = false;
        pendingRotation = 0;
    }

    /**
     * Apply pending elevation properties to a BoxNode.
     */
    private void applyPendingElevationToBox(LayoutNode.BoxNode box) {
        if (!hasPendingElevation) return;
        box.elevation(pendingElevation);
        box.elevationSolid(pendingElevationSolid);
        hasPendingElevation = false;
        pendingElevation = 0;
        pendingElevationSolid = true;
    }

    private void applyPendingAspectRatio(LayoutNode.BoxNode box) {
        if (pendingAspectRatio > 0) {
            box.aspectRatio(pendingAspectRatio);
            pendingAspectRatio = 0;
        }
    }

    private void applyPendingOverflow(LayoutNode.BoxNode box) {
        if (pendingOverflow != null) {
            box.overflow(pendingOverflow);
            pendingOverflow = null;
        }
        if (pendingMaxHeight != null) {
            box.maxHeightSpec(pendingMaxHeight);
            pendingMaxHeight = null;
        }
    }

    private void applyPendingFontFamily(LayoutNode.BoxNode box) {
        if (pendingTextFontFamily != null && !pendingTextFontFamily.isEmpty()) {
            box.fontFamily(pendingTextFontFamily);
            pendingTextFontFamily = null;
        }
    }

    private void applyPendingFontSize(LayoutNode.BoxNode box) {
        if (pendingTextFontSize != null && !pendingTextFontSize.isEmpty()) {
            box.fontSizeSpec(pendingTextFontSize);
            pendingTextFontSize = null;
        }
    }

    private void clearPendingShape() {
        pendingShapeType = null;
        pendingCornerRadius = null;
        pendingShapeFill = null;
        pendingShapeStroke = null;
        pendingShapeStrokeWidth = null;
        pendingShapeWidth = null;
        pendingShapeHeight = null;
    }

    private void addToCurrentContainer(LayoutNode node) {
        LayoutNode.BoxNode current = containerStack.peek();
        if (current != null) {
            current.addChild(node);
        }
    }

    private void applyMetadata(LayoutNode.BoxNode node) {
        if (nextType != null) {
            node.type(nextType);
            node.styles().add(nextType);
            nextType = null;
        }
        if (nextId != null) {
            node.id(nextId);
            nextId = null;
        }
        if (nextEditable) {
            node.styles().add("editable");
            nextEditable = false;
        }
        if (nextTransitions != null) {
            node.transitions(nextTransitions);
            nextTransitions = null;
        }
        if (!pendingEvents.isEmpty()) {
            node.events().addAll(pendingEvents);
            pendingEvents.clear();
        }
    }

    private void applyMetadata(LayoutNode.TextNode node) {
        if (nextType != null) {
            node.type(nextType);
            node.styles().add(nextType);
            nextType = null;
        }
        if (nextId != null) {
            node.id(nextId);
            nextId = null;
        }
        if (nextEditable) {
            node.styles().add("editable");
            nextEditable = false;
        }
        if (!pendingEvents.isEmpty()) {
            node.events().addAll(pendingEvents);
            pendingEvents.clear();
        }
    }

    private void applyMetadata(LayoutNode.ShapeNode node) {
        if (nextType != null) {
            node.type(nextType);
            node.styles().add(nextType);
            nextType = null;
        }
        if (nextId != null) {
            node.id(nextId);
            nextId = null;
        }
        nextEditable = false;
        if (!pendingEvents.isEmpty()) {
            node.events().addAll(pendingEvents);
            pendingEvents.clear();
        }
    }

    private void applyMetadata(LayoutNode.ImageNode node) {
        if (nextType != null) {
            node.type(nextType);
            node.styles().add(nextType);
            nextType = null;
        }
        if (nextId != null) {
            node.id(nextId);
            nextId = null;
        }
        nextEditable = false;
        if (!pendingEvents.isEmpty()) {
            node.events().addAll(pendingEvents);
            pendingEvents.clear();
        }
    }

    /**
     * Parse CSS-like padding value into top/right/bottom/left.
     * Supports: "8px", "8px 16px", "8px 16px 8px 16px".
     *
     * <p>Always stores the raw spec on the node for deferred resolution.
     * Non-% values are pre-resolved here; % values resolve to 0 now and
     * get re-resolved by {@link LayoutEngine} when parent dimensions are known.
     */
    private void parsePadding(LayoutNode.BoxNode box, String padding) {
        box.paddingSpec(padding);
        String[] parts = padding.trim().split("\\s+");
        try {
            if (parts.length == 1) {
                float p = parsePixels(parts[0]);
                box.padding(p, p, p, p);
            } else if (parts.length == 2) {
                float v = parsePixels(parts[0]);
                float h = parsePixels(parts[1]);
                box.padding(v, h, v, h);
            } else if (parts.length == 4) {
                box.padding(
                    parsePixels(parts[0]),
                    parsePixels(parts[1]),
                    parsePixels(parts[2]),
                    parsePixels(parts[3])
                );
            }
        } catch (NumberFormatException ignored) {
            // Malformed padding — leave as zero
        }
    }

    private float parsePixels(String value) {
        SizeValue sv = SizeValue.parse(value);
        if (sv != null) {
            return (float) sv.toPixels(renderContext);
        }
        // Fallback: try bare number
        return Float.parseFloat(value.replaceAll("[^0-9.]", ""));
    }

    /**
     * Resolve border widths and radius from BoxBorder spec to pixel floats.
     */
    private void resolveBorder(LayoutNode.BoxNode box, BoxBorder border) {
        box.borderWidths(
                resolveBorderSideWidth(border.top()),
                resolveBorderSideWidth(border.right()),
                resolveBorderSideWidth(border.bottom()),
                resolveBorderSideWidth(border.left())
        );
        if (border.hasRadius()) {
            SizeValue rv = SizeValue.parse(border.radius());
            if (rv != null) {
                box.borderRadius((float) rv.toPixels(renderContext));
            }
        }
    }

    private float resolveBorderSideWidth(BoxBorder.BorderSide side) {
        if (!side.isVisible()) return 0;
        SizeValue sv = SizeValue.parse(side.width());
        return sv != null ? (float) sv.toPixels(renderContext) : 1.0f;
    }
}
