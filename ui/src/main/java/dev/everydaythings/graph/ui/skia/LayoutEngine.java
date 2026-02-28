package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.ui.style.StyleResolver;
import dev.everydaythings.graph.ui.style.Stylesheet;

/**
 * Simple box layout engine for the Skia renderer.
 *
 * <p>Computes pixel positions and sizes for a {@link LayoutNode} tree.
 * Phase 1 implements simple vertical/horizontal stacking:
 * <ul>
 *   <li>VERTICAL: children stack top-to-bottom, each gets full container width</li>
 *   <li>HORIZONTAL: children placed left-to-right, each gets content width</li>
 * </ul>
 *
 * <p>Text nodes must have their {@link LayoutNode.TextNode#measuredSize} set
 * before calling layout (via {@link TextMeasurer}).
 */
public class LayoutEngine {

    /**
     * Measures text dimensions for layout.
     * In production, backed by Skia Font.measureText().
     * In tests, can use a fixed-width approximation.
     */
    @FunctionalInterface
    public interface TextMeasurer {
        /**
         * Measure text and set measuredSize on the node.
         *
         * @param node the text node to measure
         * @param maxWidth available width for wrapping (-1 = no limit)
         */
        void measure(LayoutNode.TextNode node, float maxWidth);
    }

    public interface ImageMeasurer {
        void measure(LayoutNode.ImageNode node);
    }

    private final TextMeasurer textMeasurer;
    private final ImageMeasurer imageMeasurer;
    private final RenderContext renderContext;
    private final Stylesheet stylesheet;
    private final float baseFontSize;

    public LayoutEngine(TextMeasurer textMeasurer) {
        this(textMeasurer, null);
    }

    public LayoutEngine(TextMeasurer textMeasurer, RenderContext renderContext) {
        this(textMeasurer, renderContext, null, 15f);
    }

    public LayoutEngine(TextMeasurer textMeasurer, RenderContext renderContext,
                         Stylesheet stylesheet, float baseFontSize) {
        this.textMeasurer = textMeasurer;
        this.imageMeasurer = (textMeasurer instanceof ImageMeasurer im) ? im : null;
        this.renderContext = renderContext;
        this.stylesheet = stylesheet;
        this.baseFontSize = baseFontSize;
    }

    /**
     * Lay out a node tree within the given available bounds.
     *
     * @param node the root node
     * @param availableWidth available width in pixels
     * @param availableHeight available height in pixels
     */
    public void layout(LayoutNode node, float availableWidth, float availableHeight) {
        // Resolve styles before measurement (font size affects text metrics)
        if (stylesheet != null && renderContext != null) {
            new StyleResolver(stylesheet, renderContext, baseFontSize).resolve(node);
        }
        measure(node, availableWidth, availableHeight);
        if (isFillChild(node)) {
            // Fill node expands to available space
            float w = Math.max(node.width(), availableWidth);
            float h = Math.max(node.height(), availableHeight);
            node.setBounds(0, 0, w, h);
        } else {
            node.setBounds(0, 0, node.width(), node.height());
        }
        // Re-run flex distribution now that the root has its final size.
        // Always called (not just for fill roots) because the implicit root wrapper
        // from SkiaSurfaceRenderer has no "fill" style but still acts as viewport.
        // For roots without fill children, this is a no-op.
        if (node instanceof LayoutNode.BoxNode box && box.direction() == Scene.Direction.VERTICAL) {
            redistributeFill(box);
        }
        position(node);
    }

    /**
     * Resolve a size spec string to pixels.
     *
     * @param spec      raw size string ("200px", "50%", "10vw", "1in", null)
     * @param available available parent dimension for % resolution
     * @return resolved pixel value, or -1 if spec is null/empty
     */
    private float resolveSize(String spec, float available) {
        if (spec == null || spec.isEmpty()) return -1;
        SizeValue sv = SizeValue.parse(spec);
        if (sv == null) return -1;
        if (sv.isAuto()) return -1;

        if (sv.isPercentage()) {
            return available * (float) sv.value() / 100f;
        }
        if (renderContext != null) {
            return (float) sv.toPixels(renderContext);
        }
        // Fallback (no context, e.g. tests): treat value as pixels
        return (float) sv.value();
    }

    /**
     * Re-resolve padding spec when it contains % values.
     * Called during measureBox() when parent dimensions are available.
     * Horizontal padding resolves against availableWidth, vertical against availableHeight.
     */
    private void resolvePaddingSpec(LayoutNode.BoxNode box, float availableWidth, float availableHeight) {
        String spec = box.paddingSpec();
        if (spec == null || spec.isEmpty() || !spec.contains("%")) return;

        String[] parts = spec.trim().split("\\s+");
        try {
            if (parts.length == 1) {
                // Single value: use the smaller axis? No — padding is directional.
                // Top/bottom resolve against height, left/right against width.
                // For a single value, we use height for vertical and width for horizontal.
                float v = resolveSize(parts[0], availableHeight);
                float h = resolveSize(parts[0], availableWidth);
                if (v < 0) v = 0;
                if (h < 0) h = 0;
                box.padding(v, h, v, h);
            } else if (parts.length == 2) {
                float v = resolveSize(parts[0], availableHeight);
                float h = resolveSize(parts[1], availableWidth);
                if (v < 0) v = 0;
                if (h < 0) h = 0;
                box.padding(v, h, v, h);
            } else if (parts.length == 4) {
                float top = resolveSize(parts[0], availableHeight);
                float right = resolveSize(parts[1], availableWidth);
                float bottom = resolveSize(parts[2], availableHeight);
                float left = resolveSize(parts[3], availableWidth);
                if (top < 0) top = 0;
                if (right < 0) right = 0;
                if (bottom < 0) bottom = 0;
                if (left < 0) left = 0;
                box.padding(top, right, bottom, left);
            }
        } catch (NumberFormatException ignored) {
            // Malformed — keep existing padding
        }
    }

    /**
     * Redistribute remaining vertical space to fill children.
     * Called after the parent box has its final height.
     */
    private void redistributeFill(LayoutNode.BoxNode box) {
        float padV = box.paddingTop() + box.paddingBottom()
                   + box.borderTopWidth() + box.borderBottomWidth();
        float contentHeight = box.height() - padV;

        float fixedHeight = 0;
        int fillCount = 0;
        int childCount = box.children().size();
        for (LayoutNode child : box.children()) {
            if (isFillChild(child)) {
                fillCount++;
            } else {
                fixedHeight += child.height();
            }
        }
        // Account for gap spacing
        if (box.gap() > 0 && childCount > 1) {
            fixedHeight += (childCount - 1) * box.gap();
        }

        if (fillCount > 0 && contentHeight > fixedHeight) {
            float fillHeight = (contentHeight - fixedHeight) / fillCount;
            for (LayoutNode child : box.children()) {
                if (isFillChild(child)) {
                    child.setBounds(child.x(), child.y(), child.width(), fillHeight);
                    if (child instanceof LayoutNode.BoxNode childBox) {
                        if (childBox.direction() == Scene.Direction.HORIZONTAL) {
                            // Stretch horizontal children to fill the cross-axis
                            stretchHorizontalChildren(childBox);
                        } else if (childBox.direction() == Scene.Direction.VERTICAL) {
                            // Recurse: vertical fill children may have their own fill children
                            // that need height redistribution (e.g., constraint container → horizontal wrapper)
                            redistributeFill(childBox);
                        }
                    }
                    // Check overflow for fill children that were assigned a fixed height
                    detectOverflowAfterResize(child);
                }
            }
        }
    }

    /**
     * Stretch children of a horizontal box to fill the parent's height.
     * Called after redistributeFill assigns a new height to the horizontal wrapper.
     *
     * <p>After stretching, recursively redistributes fill for vertical children
     * so their fill grandchildren also stretch to the new height.
     */
    private void stretchHorizontalChildren(LayoutNode.BoxNode box) {
        float contentHeight = box.height() - box.paddingTop() - box.paddingBottom()
                            - box.borderTopWidth() - box.borderBottomWidth();
        for (LayoutNode child : box.children()) {
            if (child.height() < contentHeight) {
                child.setBounds(child.x(), child.y(), child.width(), contentHeight);
                // Recurse: if this stretched child is a vertical box, redistribute
                // fill so its fill children also stretch to the new height.
                if (child instanceof LayoutNode.BoxNode childBox
                        && childBox.direction() == Scene.Direction.VERTICAL) {
                    redistributeFill(childBox);
                }
                // Check overflow for stretched children that were assigned a fixed height
                detectOverflowAfterResize(child);
            } else if (child.height() > contentHeight
                    && child instanceof LayoutNode.BoxNode childBox
                    && !"visible".equals(childBox.overflow())) {
                // Child is taller than viewport and has overflow — clamp to viewport
                // height and detect overflow (content exceeds available space)
                child.setBounds(child.x(), child.y(), child.width(), contentHeight);
                detectOverflowAfterResize(child);
            }
        }
    }

    /**
     * Measure phase: compute intrinsic sizes bottom-up.
     */
    private void measure(LayoutNode node, float availableWidth, float availableHeight) {
        measure(node, availableWidth, availableHeight, false);
    }

    /**
     * Measure phase with shrink-wrap hint.
     *
     * @param shrinkWrap true when inside a horizontal parent — vertical children
     *                   should use content width, not fill available width
     */
    private void measure(LayoutNode node, float availableWidth, float availableHeight, boolean shrinkWrap) {
        switch (node) {
            case LayoutNode.TextNode text -> measureText(text, availableWidth, availableHeight);
            case LayoutNode.ImageNode image -> measureImage(image, availableWidth, availableHeight);
            case LayoutNode.ShapeNode shape -> measureShape(shape, availableWidth, availableHeight);
            case LayoutNode.BoxNode box -> measureBox(box, availableWidth, availableHeight, shrinkWrap);
        }
    }

    private void measureText(LayoutNode.TextNode text, float maxWidth, float parentHeight) {
        // Resolve font size spec (e.g., "80%", "20px") before measuring
        // Only resolve when no concrete size has already been set by StyleResolver.
        String spec = text.fontSizeSpec();
        if (text.fontSize() <= 0 && spec != null && !spec.isEmpty()) {
            SizeValue sv = SizeValue.parse(spec);
            if (sv != null) {
                if (sv.isPercentage() && parentHeight > 0) {
                    text.fontSize((float) (parentHeight * sv.value() / 100.0));
                } else if (renderContext != null) {
                    text.fontSize((float) sv.toPixels(renderContext));
                }
            }
        }

        textMeasurer.measure(text, maxWidth);
        float w = text.measuredWidth();
        // Clamp width to available space — text wraps within maxWidth
        if (maxWidth > 0 && w > maxWidth) {
            w = maxWidth;
        }
        text.setBounds(0, 0, w, text.measuredHeight());
    }

    private void measureImage(LayoutNode.ImageNode image, float availableWidth, float availableHeight) {
        // Resolve percentage image sizes against the parent allocation, not viewport.
        // This is required for responsive grids where image size is specified like "80%".
        String sizeSpec = image.size();
        if (sizeSpec != null && !sizeSpec.isEmpty()) {
            SizeValue sv = SizeValue.parse(sizeSpec);
            if (sv != null && sv.isPercentage()) {
                float basis = Math.min(availableWidth, availableHeight);
                if (basis > 0) {
                    float px = basis * (float) (sv.value() / 100.0);
                    image.setBounds(0, 0, px, px);
                    return;
                }
            }
        }
        if (imageMeasurer != null) {
            imageMeasurer.measure(image);
        } else {
            // Fallback: fixed-size placeholder
            float size = 24;
            image.setBounds(0, 0, size, size);
        }
    }

    private void measureShape(LayoutNode.ShapeNode shape, float availableWidth, float availableHeight) {
        // Resolve % specs against parent dimensions
        if (shape.widthSpec() != null) {
            float resolved = resolveSize(shape.widthSpec(), availableWidth);
            if (resolved > 0) shape.explicitWidth(resolved);
        }
        if (shape.heightSpec() != null) {
            float resolved = resolveSize(shape.heightSpec(), availableHeight);
            if (resolved > 0) shape.explicitHeight(resolved);
        }

        float w = shape.explicitWidth() > 0 ? shape.explicitWidth() : availableWidth;
        float h = shape.explicitHeight() > 0 ? shape.explicitHeight() : 0;

        switch (shape.shapeType()) {
            case "line" -> {
                // Lines fill available width, height is stroke width
                float sw = parseStrokeWidth(shape.strokeWidth());
                if (h <= 0) h = sw;
            }
            case "circle" -> {
                // Circles have square bounding box
                float dim = shape.explicitWidth() > 0 ? shape.explicitWidth()
                          : shape.explicitHeight() > 0 ? shape.explicitHeight()
                          : 24;
                w = dim;
                h = dim;
            }
            case "rectangle" -> {
                if (h <= 0) h = 24; // reasonable default
            }
            case "path" -> {
                if (h <= 0) h = 24;
                if (w <= 0) w = 24;
            }
            default -> {
                if (h <= 0) h = 24;
            }
        }

        shape.setBounds(0, 0, w, h);
    }

    private float parseStrokeWidth(String strokeWidth) {
        if (strokeWidth == null || strokeWidth.isEmpty()) return 1;
        try {
            return Float.parseFloat(strokeWidth.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void measureBox(LayoutNode.BoxNode box, float availableWidth, float availableHeight, boolean shrinkWrap) {
        // Detect auto sizing — forces shrink-wrap regardless of parent context
        boolean autoWidth = SizeValue.isAutoSpec(box.widthSpec());
        boolean autoHeight = SizeValue.isAutoSpec(box.heightSpec());

        // Resolve size specs to absolute values
        float resolvedWidth = resolveSize(box.widthSpec(), availableWidth);
        if (resolvedWidth > 0) box.explicitWidth(resolvedWidth);

        float resolvedHeight = resolveSize(box.heightSpec(), availableHeight);
        if (resolvedHeight > 0) box.explicitHeight(resolvedHeight);

        // Resolve maxHeight for scroll containers
        if (box.maxHeightSpec() != null && !box.maxHeightSpec().isEmpty()) {
            float maxH = resolveSize(box.maxHeightSpec(), availableHeight);
            if (maxH > 0 && box.explicitHeight() <= 0) {
                box.explicitHeight(maxH);
            }
        }

        // Apply aspect ratio: derive missing dimension from the other
        if (box.aspectRatio() > 0) {
            float ratio = box.aspectRatio();
            boolean hasW = box.explicitWidth() > 0;
            boolean hasH = box.explicitHeight() > 0;
            if (hasW && !hasH) {
                box.explicitHeight(box.explicitWidth() / ratio);
            } else if (hasH && !hasW) {
                box.explicitWidth(box.explicitHeight() * ratio);
            } else if (!hasW) {
                // Neither set — fit within both available dimensions
                float w;
                if (availableWidth > 0 && availableHeight > 0) {
                    float fromHeight = availableHeight * ratio;
                    w = Math.min(availableWidth, fromHeight);
                } else if (availableWidth > 0) {
                    w = availableWidth;
                } else {
                    w = 100;
                }
                box.explicitWidth(w);
                box.explicitHeight(w / ratio);
            }
            // Both set → ignore aspectRatio
        }

        // Resolve gap
        if (box.gapSpec() != null) {
            float g = resolveSize(box.gapSpec(), availableWidth);
            if (g > 0) box.gap(g);
        }

        // Re-resolve % padding now that parent dimensions are known.
        // The renderer pre-resolves non-% values; % values need the available size.
        resolvePaddingSpec(box, availableWidth, availableHeight);

        float padH = box.paddingLeft() + box.paddingRight()
                   + box.borderLeftWidth() + box.borderRightWidth();
        float padV = box.paddingTop() + box.paddingBottom()
                   + box.borderTopWidth() + box.borderBottomWidth();
        float contentWidth = Math.max(0, availableWidth - padH);
        float contentHeight = Math.max(0, availableHeight - padV);

        if (box.explicitWidth() > 0) {
            contentWidth = box.explicitWidth() - padH;
        }
        if (box.explicitHeight() > 0) {
            contentHeight = box.explicitHeight() - padV;
        }

        // Apply maxWidth constraint BEFORE measuring children so text wraps
        // at the correct width (like CSS max-width constraining content layout)
        float maxW = -1;
        if (box.maxWidthSpec() != null && !box.maxWidthSpec().isEmpty()) {
            maxW = resolveSize(box.maxWidthSpec(), availableWidth);
            if (maxW > 0) {
                float maxContentWidth = maxW - padH;
                if (maxContentWidth < contentWidth) {
                    contentWidth = maxContentWidth;
                }
            }
        }

        if (box.children().isEmpty()) {
            float w = box.explicitWidth() > 0 ? box.explicitWidth() : padH;
            float h = box.explicitHeight() > 0 ? box.explicitHeight() : padV;
            box.setBounds(0, 0, w, h);
            return;
        }

        if (box.direction() == Scene.Direction.HORIZONTAL) {
            measureHorizontal(box, contentWidth, contentHeight);
        } else if (box.direction() == Scene.Direction.STACK) {
            measureStack(box, contentWidth, contentHeight);
        } else {
            // VERTICAL is default
            // Auto width forces shrink-wrap even in vertical (block) context
            measureVertical(box, contentWidth, contentHeight, shrinkWrap || autoWidth);
        }

        // Clamp to maxWidth if children measured wider than the constraint
        if (maxW > 0 && box.width() > maxW) {
            box.setBounds(box.x(), box.y(), maxW, box.height());
        }
    }

    private void measureVertical(LayoutNode.BoxNode box, float contentWidth, float contentHeight, boolean shrinkWrap) {
        // Pass 1: measure non-fill children to find their heights
        // Propagate shrinkWrap to children — when the parent is shrink-wrapping
        // (auto width or inside horizontal), children must also shrink-wrap so
        // they use content width instead of filling the available space.
        float fixedHeight = 0;
        int fillCount = 0;

        for (LayoutNode child : box.children()) {
            if (isFillChild(child)) {
                fillCount++;
            } else {
                measure(child, contentWidth, contentHeight, shrinkWrap);
                fixedHeight += child.height();
            }
        }

        // Account for gap spacing in available space calculation
        int childCount = box.children().size();
        float totalGap = (box.gap() > 0 && childCount > 1) ? (childCount - 1) * box.gap() : 0;

        // Pass 2: measure fill children with remaining space
        float availableForFill = contentHeight - fixedHeight - totalGap;
        if (fillCount > 0 && availableForFill > 0) {
            float fillHeight = availableForFill / fillCount;
            for (LayoutNode child : box.children()) {
                if (isFillChild(child)) {
                    measure(child, contentWidth, fillHeight, shrinkWrap);
                    // Force fill child to take the allocated height
                    child.setBounds(child.x(), child.y(), child.width(), fillHeight);
                }
            }
        } else if (fillCount > 0) {
            // No remaining space — measure fill children with zero
            for (LayoutNode child : box.children()) {
                if (isFillChild(child)) {
                    measure(child, contentWidth, 0, shrinkWrap);
                }
            }
        }

        float totalHeight = 0;
        float maxChildWidth = 0;
        for (LayoutNode child : box.children()) {
            totalHeight += child.height();
            maxChildWidth = Math.max(maxChildWidth, child.width());
        }
        totalHeight += totalGap;

        // Vertical boxes: fill available width when in block context (default),
        // shrink-wrap to content width when inside a horizontal parent (flex item)
        float width = box.explicitWidth() > 0
                ? box.explicitWidth()
                : shrinkWrap
                    ? maxChildWidth + box.paddingLeft() + box.paddingRight()
                                    + box.borderLeftWidth() + box.borderRightWidth()
                    : contentWidth + box.paddingLeft() + box.paddingRight()
                                   + box.borderLeftWidth() + box.borderRightWidth();
        float naturalHeight = totalHeight + box.paddingTop() + box.paddingBottom()
                             + box.borderTopWidth() + box.borderBottomWidth();
        float height = box.explicitHeight() > 0
                ? box.explicitHeight()
                : naturalHeight;

        // Overflow detection: if box has overflow != "visible" and explicit height,
        // record content dimensions and detect overflow
        String overflow = box.overflow();
        if (!"visible".equals(overflow) && box.explicitHeight() > 0) {
            box.contentHeight(naturalHeight);
            box.overflowsY(naturalHeight > box.explicitHeight());
        }

        box.setBounds(0, 0, width, height);
    }

    /**
     * Detect overflow after a box has been resized by fill redistribution or stretching.
     *
     * <p>During initial measurement, overflow detection only triggers when a box has
     * an explicit height. Fill children get their height assigned post-measurement by
     * {@link #redistributeFill} or {@link #stretchHorizontalChildren}, so this method
     * runs the overflow check for those boxes once they have their final height.
     */
    private void detectOverflowAfterResize(LayoutNode node) {
        if (!(node instanceof LayoutNode.BoxNode box)) return;
        String overflow = box.overflow();
        if ("visible".equals(overflow)) return;

        // Sum children's natural heights to get the content extent
        float padV = box.paddingTop() + box.paddingBottom()
                   + box.borderTopWidth() + box.borderBottomWidth();
        float viewportHeight = box.height() - padV;
        float childrenHeight = 0;
        int childCount = box.children().size();
        for (LayoutNode child : box.children()) {
            childrenHeight += child.height();
        }
        if (box.gap() > 0 && childCount > 1) {
            childrenHeight += (childCount - 1) * box.gap();
        }
        float naturalHeight = childrenHeight + padV;
        box.contentHeight(naturalHeight);
        box.overflowsY(childrenHeight > viewportHeight);
    }

    /**
     * Check if a layout node should fill remaining space in its parent.
     * Nodes with the "fill" style class participate in flex-fill distribution.
     */
    private boolean isFillChild(LayoutNode node) {
        return node.styles() != null && node.styles().contains("fill");
    }

    private void measureHorizontal(LayoutNode.BoxNode box, float contentWidth, float contentHeight) {
        int childCount = box.children().size();
        float totalGap = (box.gap() > 0 && childCount > 1) ? (childCount - 1) * box.gap() : 0;

        // Pass 1: measure non-fill children (shrinkWrap=true), sum their widths
        float fixedWidth = 0;
        int fillCount = 0;
        float maxChildHeight = 0;

        for (LayoutNode child : box.children()) {
            if (isFillChild(child)) {
                fillCount++;
            } else {
                // Use full parent content width as the percentage basis for each child.
                // Using shrinking remaining width here compounds percentage specs across
                // siblings (e.g., 11.111% gets progressively smaller in each column).
                measure(child, contentWidth, contentHeight, true);
                fixedWidth += child.width();
                maxChildHeight = Math.max(maxChildHeight, child.height());
            }
        }

        // Pass 2: distribute remaining width to fill children
        float availableForFill = contentWidth - fixedWidth - totalGap;
        if (fillCount > 0 && availableForFill > 0) {
            float fillWidth = availableForFill / fillCount;
            for (LayoutNode child : box.children()) {
                if (isFillChild(child)) {
                    measure(child, fillWidth, contentHeight, false);
                    // Force fill child to take the allocated width, but respect
                    // aspect-ratio-constrained width (cell shouldn't stretch wider
                    // than its aspect ratio allows within the cross-axis)
                    float w = fillWidth;
                    if (child instanceof LayoutNode.BoxNode b && b.aspectRatio() > 0
                            && b.explicitWidth() > 0 && b.explicitWidth() < fillWidth) {
                        w = b.explicitWidth();
                    }
                    float h = child.height();
                    // Horizontal fill children should stretch on the cross-axis unless
                    // they declare an explicit height. This keeps fill columns truly
                    // full-height and prevents top-biased layouts in tall panes.
                    if (child instanceof LayoutNode.BoxNode b && b.explicitHeight() <= 0) {
                        h = contentHeight;
                    }
                    child.setBounds(child.x(), child.y(), w, h);
                    maxChildHeight = Math.max(maxChildHeight, child.height());
                }
            }
        } else if (fillCount > 0) {
            // No remaining space — measure fill children with zero
            for (LayoutNode child : box.children()) {
                if (isFillChild(child)) {
                    measure(child, 0, contentHeight, true);
                    maxChildHeight = Math.max(maxChildHeight, child.height());
                }
            }
        }

        float totalWidth = fixedWidth + totalGap;
        if (fillCount > 0 && availableForFill > 0) {
            totalWidth += availableForFill;
        } else {
            // Add fill children's measured widths when no redistribution
            for (LayoutNode child : box.children()) {
                if (isFillChild(child)) {
                    totalWidth += child.width();
                }
            }
        }

        float width = box.explicitWidth() > 0
                ? box.explicitWidth()
                : totalWidth + box.paddingLeft() + box.paddingRight()
                             + box.borderLeftWidth() + box.borderRightWidth();
        float height = box.explicitHeight() > 0
                ? box.explicitHeight()
                : maxChildHeight + box.paddingTop() + box.paddingBottom()
                                 + box.borderTopWidth() + box.borderBottomWidth();

        box.setBounds(0, 0, width, height);
    }

    /**
     * Measure STACK layout: all children overlap at the same position.
     * Container size = max(child widths) × max(child heights).
     *
     * <p>Children without explicit sizes stretch to fill the STACK's content area,
     * like CSS {@code position: absolute} with {@code inset: 0}. This is essential
     * for the clock trick: hand containers must fill the full 200×200 STACK so
     * rotation pivots around the clock center.
     */
    private void measureStack(LayoutNode.BoxNode box, float contentWidth, float contentHeight) {
        // When the STACK has a circle shape, the visible content area is the inscribed
        // circle — force a square so children's percentage sizes resolve correctly.
        // Without this, a tall container (e.g., 400×600) gives children height="50%"
        // resolving to 300px, which extends past the circle radius of 200.
        if ("circle".equals(box.shapeType())) {
            float side = Math.min(contentWidth, contentHeight);
            contentWidth = side;
            contentHeight = side;
        }

        // First pass: measure children to determine intrinsic sizes
        float maxChildWidth = 0;
        float maxChildHeight = 0;

        for (LayoutNode child : box.children()) {
            measure(child, contentWidth, contentHeight);
            maxChildWidth = Math.max(maxChildWidth, child.width());
            maxChildHeight = Math.max(maxChildHeight, child.height());
        }

        float padH = box.paddingLeft() + box.paddingRight()
                    + box.borderLeftWidth() + box.borderRightWidth();
        float padV = box.paddingTop() + box.paddingBottom()
                    + box.borderTopWidth() + box.borderBottomWidth();

        float width = box.explicitWidth() > 0
                ? box.explicitWidth()
                : maxChildWidth + padH;
        float height = box.explicitHeight() > 0
                ? box.explicitHeight()
                : maxChildHeight + padV;

        box.setBounds(0, 0, width, height);

        // Second pass: stretch children without explicit sizes to fill the content area.
        // In a STACK, children act like CSS absolute-positioned elements with inset: 0 —
        // they fill the parent unless they have their own explicit dimensions.
        float finalContentWidth = width - padH;
        float finalContentHeight = height - padV;

        // For circle-shaped STACKs, constrain stretch area to inscribed square
        if ("circle".equals(box.shapeType())) {
            float side = Math.min(finalContentWidth, finalContentHeight);
            finalContentWidth = side;
            finalContentHeight = side;
        }

        for (LayoutNode child : box.children()) {
            if (child instanceof LayoutNode.BoxNode childBox) {
                boolean hasExplicitW = childBox.explicitWidth() > 0;
                boolean hasExplicitH = childBox.explicitHeight() > 0;
                if (!hasExplicitW || !hasExplicitH) {
                    // Re-measure with the full content area, forcing stretch
                    float w = hasExplicitW ? childBox.explicitWidth() : finalContentWidth;
                    float h = hasExplicitH ? childBox.explicitHeight() : finalContentHeight;
                    childBox.setBounds(0, 0, w, h);
                }
            }
        }
    }

    /**
     * Position phase: assign absolute (x, y) to children based on parent bounds.
     */
    private void position(LayoutNode node) {
        if (node instanceof LayoutNode.BoxNode box) {
            positionChildren(box);
        }
    }

    private void positionChildren(LayoutNode.BoxNode box) {
        float startX = box.x() + box.borderLeftWidth() + box.paddingLeft();
        float startY = box.y() + box.borderTopWidth() + box.paddingTop();
        float gap = box.gap();

        // Cross-axis alignment (from @Surface.Container(align = "center"))
        String crossAlign = getCrossAlign(box);
        // Main-axis justification (from style class "justify-center", etc.)
        String justify = getJustify(box);

        if (box.direction() == Scene.Direction.STACK) {
            // STACK: children overlap, painted in order (last on top).
            // Children without explicit sizes fill the content area.
            // Children with explicit sizes keep their measured dimensions and are centered.
            float contentWidth = box.width() - box.borderLeftWidth() - box.paddingLeft()
                               - box.borderRightWidth() - box.paddingRight();
            float contentHeight = box.height() - box.borderTopWidth() - box.paddingTop()
                                - box.borderBottomWidth() - box.paddingBottom();

            // For circle-shaped STACKs, constrain to square and center
            float childAreaW = contentWidth;
            float childAreaH = contentHeight;
            float offsetX = 0, offsetY = 0;
            if ("circle".equals(box.shapeType())) {
                float side = Math.min(contentWidth, contentHeight);
                childAreaW = side;
                childAreaH = side;
                offsetX = (contentWidth - side) / 2f;
                offsetY = (contentHeight - side) / 2f;
            }

            for (LayoutNode child : box.children()) {
                boolean hasExplicitW = hasExplicitWidth(child);
                boolean hasExplicitH = hasExplicitHeight(child);

                float w = hasExplicitW ? child.width() : childAreaW;
                float h = hasExplicitH ? child.height() : childAreaH;

                child.setBounds(startX + offsetX, startY + offsetY, w, h);
                position(child);
            }
        } else if (box.direction() == Scene.Direction.HORIZONTAL) {
            float contentWidth = box.width() - box.borderLeftWidth() - box.paddingLeft()
                               - box.borderRightWidth() - box.paddingRight();
            float contentHeight = box.height() - box.borderTopWidth() - box.paddingTop()
                                - box.borderBottomWidth() - box.paddingBottom();

            if ("center".equals(justify)) {
                float totalWidth = totalChildExtent(box, true);
                startX += (contentWidth - totalWidth) / 2f;
            } else if ("end".equals(justify)) {
                float totalWidth = totalChildExtent(box, true);
                startX += contentWidth - totalWidth;
            }

            // Distribute slack from aspect-ratio-constrained fill children.
            // When fill children are narrower than their allocation (due to
            // aspect ratio), center each within its slot.
            float totalChildWidth = 0;
            int fillCount = 0;
            for (LayoutNode child : box.children()) {
                totalChildWidth += child.width();
                if (isFillChild(child)) fillCount++;
            }
            float totalGapSpace = (gap > 0 && box.children().size() > 1)
                    ? (box.children().size() - 1) * gap : 0;
            float slack = contentWidth - totalChildWidth - totalGapSpace;
            float perFillSlack = (fillCount > 0 && slack > 1) ? slack / fillCount : 0;

            float childX = startX;
            for (int i = 0; i < box.children().size(); i++) {
                LayoutNode child = box.children().get(i);
                float childY = startY;
                if ("center".equals(crossAlign)) {
                    childY = startY + (contentHeight - child.height()) / 2f;
                } else if ("end".equals(crossAlign)) {
                    childY = startY + contentHeight - child.height();
                }
                // Center fill children within their slot when there's slack
                float slotOffset = (isFillChild(child) && perFillSlack > 0)
                        ? perFillSlack / 2f : 0;
                child.setBounds(childX + slotOffset, childY, child.width(), child.height());
                position(child);
                childX += child.width() + (isFillChild(child) ? perFillSlack : 0);
                if (gap > 0 && i < box.children().size() - 1) childX += gap;
            }
        } else {
            float contentWidth = box.width() - box.borderLeftWidth() - box.paddingLeft()
                               - box.borderRightWidth() - box.paddingRight();
            float contentHeight = box.height() - box.borderTopWidth() - box.paddingTop()
                                - box.borderBottomWidth() - box.paddingBottom();

            if ("center".equals(justify)) {
                float totalHeight = totalChildExtent(box, false);
                startY += (contentHeight - totalHeight) / 2f;
            } else if ("end".equals(justify)) {
                float totalHeight = totalChildExtent(box, false);
                startY += contentHeight - totalHeight;
            }

            float childY = startY;
            for (int i = 0; i < box.children().size(); i++) {
                LayoutNode child = box.children().get(i);
                float childX = startX;
                if ("center".equals(crossAlign)) {
                    childX = startX + (contentWidth - child.width()) / 2f;
                } else if ("end".equals(crossAlign)) {
                    childX = startX + contentWidth - child.width();
                }
                child.setBounds(childX, childY, child.width(), child.height());
                position(child);
                childY += child.height();
                if (gap > 0 && i < box.children().size() - 1) childY += gap;
            }
        }
    }

    /**
     * Extract cross-axis alignment from style classes (e.g., "align-center" → "center").
     */
    private static String getCrossAlign(LayoutNode.BoxNode box) {
        if (box.styles() == null) return null;
        for (String style : box.styles()) {
            if (style.startsWith("align-")) {
                return style.substring(6);
            }
        }
        return null;
    }

    private static boolean hasExplicitWidth(LayoutNode child) {
        if (child instanceof LayoutNode.BoxNode cb) return cb.explicitWidth() > 0;
        if (child instanceof LayoutNode.ShapeNode sn) return sn.explicitWidth() > 0;
        return child.width() > 0;
    }

    private static boolean hasExplicitHeight(LayoutNode child) {
        if (child instanceof LayoutNode.BoxNode cb) return cb.explicitHeight() > 0;
        if (child instanceof LayoutNode.ShapeNode sn) return sn.explicitHeight() > 0;
        return child.height() > 0;
    }

    /**
     * Extract main-axis justification from style classes (e.g., "justify-center" → "center").
     */
    private static String getJustify(LayoutNode.BoxNode box) {
        if (box.styles() == null) return null;
        for (String style : box.styles()) {
            if (style.startsWith("justify-")) {
                return style.substring(8);
            }
        }
        return null;
    }

    /**
     * Compute total child extent along the main axis, including gaps.
     */
    private float totalChildExtent(LayoutNode.BoxNode box, boolean horizontal) {
        float total = 0;
        for (LayoutNode child : box.children()) {
            total += horizontal ? child.width() : child.height();
        }
        int count = box.children().size();
        if (box.gap() > 0 && count > 1) {
            total += (count - 1) * box.gap();
        }
        return total;
    }
}
