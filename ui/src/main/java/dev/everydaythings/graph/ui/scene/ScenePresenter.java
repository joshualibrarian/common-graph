package dev.everydaythings.graph.ui.scene;

import java.util.List;
import java.util.Map;

/**
 * Applies interaction state, resolves dimensional units, and computes
 * layout geometry on a resolved SceneNode tree.
 *
 * <p>Runs on the <b>window side</b> — needs viewport dimensions, DPI,
 * font metrics, and interaction state (hover, selected, expanded).
 *
 * <p>Layout algorithm ported from {@code LayoutEngine} — same two-phase
 * measure+position approach with flex distribution, aspect ratios,
 * overflow detection, cross-axis alignment, and justify.
 *
 * @see SceneResolver
 * @see ScenePainter
 */
public class ScenePresenter {

    // =================================================================================
    // Context
    // =================================================================================

    @FunctionalInterface
    public interface TextMeasurer {
        /**
         * Measure text, returning [width, height] in pixels.
         */
        float[] measure(String text, String fontFamily, float fontSize, boolean bold, float maxWidth);
    }

    public record PresentContext(
            float viewportWidth,
            float viewportHeight,
            float baseFontSize,
            float dpi,
            TextMeasurer textMeasurer,
            InteractionState state
    ) {}

    // =================================================================================
    // Entry Point
    // =================================================================================

    public void present(SceneNode tree, PresentContext ctx) {
        applyInteractionState(tree, ctx);
        measure(tree, ctx.viewportWidth(), ctx.viewportHeight(), false, ctx);
        if (tree.isFillChild()) {
            float w = Math.max(tree.boundsWidth(), ctx.viewportWidth());
            float h = Math.max(tree.boundsHeight(), ctx.viewportHeight());
            tree.setBounds(0, 0, w, h);
        } else {
            tree.setBounds(0, 0, tree.boundsWidth(), tree.boundsHeight());
        }
        if (isVertical(tree)) {
            redistributeFill(tree);
        }
        position(tree);
        // Resolve colors and remaining visual properties
        resolveVisualProperties(tree, ctx);
    }

    // =================================================================================
    // Visual Property Resolution (String → typed values)
    // =================================================================================

    private void resolveVisualProperties(SceneNode node, PresentContext ctx) {
        if (node == null) return;

        // Background: "#RRGGBB" → 0xFFRRGGBB
        if (node.background() instanceof String bg && !bg.isEmpty()) {
            int color = parseColor(bg);
            if (color != -1) node.background(color);
        }

        // Foreground: "#RRGGBB" → 0xFFRRGGBB
        if (node.foreground() instanceof String fg && !fg.isEmpty()) {
            int color = parseColor(fg);
            if (color != -1) node.foreground(color);
        }

        // Corner radius: "4px" → 4.0f
        if (node.corner() instanceof String c && !c.isEmpty()) {
            float r = resolveSize(c, 0, ctx);
            if (r > 0) node.corner(r);
        }

        // Fill: "#RRGGBB" → 0xFFRRGGBB
        if (node.fill() instanceof String f && !f.isEmpty()) {
            int color = parseColor(f);
            if (color != -1) node.fill(color);
        }

        // Stroke: "#RRGGBB" → 0xFFRRGGBB
        if (node.stroke() instanceof String s && !s.isEmpty()) {
            int color = parseColor(s);
            if (color != -1) node.stroke(color);
        }

        // Stroke width: "2px" → 2.0f
        if (node.strokeWidth() instanceof String sw && !sw.isEmpty()) {
            float w = resolveSize(sw, 0, ctx);
            node.strokeWidth(w);
        }

        // Rotation: "45deg" or "45" → 45.0f
        if (node.rotation() instanceof String r && !r.isEmpty()) {
            try {
                String clean = r.endsWith("deg") ? r.substring(0, r.length() - 3) : r;
                node.rotation(Float.parseFloat(clean));
            } catch (NumberFormatException ignored) {}
        }

        // Recurse
        if (node.children() != null) {
            for (SceneNode child : node.children()) resolveVisualProperties(child, ctx);
        }
        if (node.surfaces() != null) {
            for (SceneNode surface : node.surfaces().values()) resolveVisualProperties(surface, ctx);
        }
    }

    /**
     * Parse a hex color string to ARGB integer.
     * Named colors are NOT handled here — they resolve through sememes
     * in the SceneResolver, not as hardcoded values.
     */
    private static int parseColor(String color) {
        if (color == null || !color.startsWith("#")) return -1;
        try {
            if (color.length() == 7) {
                return 0xFF000000 | Integer.parseInt(color.substring(1), 16);
            } else if (color.length() == 9) {
                return (int) Long.parseLong(color.substring(1), 16);
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    // =================================================================================
    // Interaction State
    // =================================================================================

    private void applyInteractionState(SceneNode node, PresentContext ctx) {
        if (node == null) return;

        if (node.when() != null && node.id() != null) {
            for (Map.Entry<String, Map<String, String>> entry : node.when().entrySet()) {
                String condition = entry.getKey();
                String bare = condition.startsWith("!") ? condition.substring(1) : condition;
                if (!InteractionState.isPseudoState(bare)) continue;

                boolean negate = condition.startsWith("!");
                boolean active = ctx.state().getPseudoStates(node.id()).contains(bare);
                if (negate) active = !active;

                if (active) {
                    for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
                        applyProperty(node, prop.getKey(), prop.getValue());
                    }
                }
            }
        }

        if (node.children() != null) {
            for (SceneNode child : node.children()) applyInteractionState(child, ctx);
        }
        if (node.surfaces() != null) {
            for (SceneNode surface : node.surfaces().values()) applyInteractionState(surface, ctx);
        }
    }

    // =================================================================================
    // Size Resolution
    // =================================================================================

    private float resolveSize(String spec, float available, PresentContext ctx) {
        if (spec == null || spec.isEmpty()) return -1;
        if ("auto".equalsIgnoreCase(spec)) return -1;

        if (spec.endsWith("%")) {
            float num = parseNum(spec, 1);
            return available * num / 100f;
        }
        if (spec.endsWith("px")) return parseNum(spec, 2);
        if (spec.endsWith("em") || spec.endsWith("ln")) return ctx.baseFontSize() * parseNum(spec, 2);
        if (spec.endsWith("rem")) return ctx.baseFontSize() * parseNum(spec, 3);
        if (spec.endsWith("ch")) return ctx.baseFontSize() * 0.6f * parseNum(spec, 2);
        if (spec.endsWith("cm")) return parseNum(spec, 2) * ctx.dpi() / 2.54f;
        if (spec.endsWith("in")) return parseNum(spec, 2) * ctx.dpi();
        if (spec.endsWith("vw")) return ctx.viewportWidth() * parseNum(spec, 2) / 100f;
        if (spec.endsWith("vh")) return ctx.viewportHeight() * parseNum(spec, 2) / 100f;
        if (spec.endsWith("fr")) return -1; // flex handled separately

        // Try plain number (pixels)
        try { return Float.parseFloat(spec); } catch (NumberFormatException e) { return -1; }
    }

    private static float parseNum(String value, int suffixLen) {
        try { return Float.parseFloat(value.substring(0, value.length() - suffixLen)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static boolean isAutoSpec(String spec) {
        return "auto".equalsIgnoreCase(spec);
    }

    // =================================================================================
    // Padding Resolution
    // =================================================================================

    private void resolvePadding(SceneNode node, float availW, float availH, PresentContext ctx) {
        String spec = node.padding();
        if (spec == null || spec.isEmpty()) return;

        String[] parts = spec.trim().split("\\s+");
        if (parts.length == 1) {
            float v = Math.max(0, resolveSize(parts[0], availH, ctx));
            float h = Math.max(0, resolveSize(parts[0], availW, ctx));
            if (v < 0) v = 0; if (h < 0) h = 0;
            node.padding(v, h, v, h);
        } else if (parts.length == 2) {
            float v = Math.max(0, resolveSize(parts[0], availH, ctx));
            float h = Math.max(0, resolveSize(parts[1], availW, ctx));
            if (v < 0) v = 0; if (h < 0) h = 0;
            node.padding(v, h, v, h);
        } else if (parts.length == 4) {
            float top = Math.max(0, resolveSize(parts[0], availH, ctx));
            float right = Math.max(0, resolveSize(parts[1], availW, ctx));
            float bottom = Math.max(0, resolveSize(parts[2], availH, ctx));
            float left = Math.max(0, resolveSize(parts[3], availW, ctx));
            node.padding(top < 0 ? 0 : top, right < 0 ? 0 : right,
                         bottom < 0 ? 0 : bottom, left < 0 ? 0 : left);
        }
    }

    private void resolveBorder(SceneNode node, PresentContext ctx) {
        if (!(node.border() instanceof String spec) || spec.isEmpty()) return;
        BoxBorder border = BoxBorder.parse(spec);
        if (border == null || !border.isVisible()) return;
        // Resolve width (uniform for now)
        String widthStr = border.top() != null ? border.top().width() : "1px";
        float w = resolveSize(widthStr, 0, ctx);
        if (w <= 0) w = 1;
        node.borderWidths(w, w, w, w);
        // Resolve color and mutate border field: String → int
        String colorStr = border.top() != null ? border.top().color() : null;
        int color = colorStr != null ? parseColor(colorStr) : 0xFF888888;
        node.border(color != -1 ? color : 0xFF888888);
    }

    // =================================================================================
    // Measure Phase
    // =================================================================================

    private void measure(SceneNode node, float availW, float availH,
                         boolean shrinkWrap, PresentContext ctx) {
        if (node == null) return;
        if (node.hidden() || !node.isVisible()) {
            node.setBounds(0, 0, 0, 0);
            return;
        }

        switch (node.type()) {
            case TEXT -> measureText(node, availW, availH, ctx);
            case BODY -> measureBody(node, availW, availH, ctx);
            case CONTAINER -> measureContainer(node, availW, availH, shrinkWrap, ctx);
            case null -> {}
        }
    }

    private void measureText(SceneNode node, float maxWidth, float parentHeight, PresentContext ctx) {
        // Resolve font size — mutate in place (String → Float)
        float fontSize = ctx.baseFontSize();
        String fontSizeSpec = node.fontSizeSpec();
        if (fontSizeSpec != null && !fontSizeSpec.isEmpty()) {
            float resolved = resolveSize(fontSizeSpec, parentHeight, ctx);
            if (resolved > 0) fontSize = resolved;
        }
        if (node.fontSizeFloat() > 0) fontSize = node.fontSizeFloat();
        node.fontSize(fontSize); // mutate: String → Float

        boolean bold = node.isBold();

        String text = node.text() != null ? node.text() : "";
        float[] measured = ctx.textMeasurer().measure(text, node.fontFamily(), fontSize, bold, maxWidth);
        node.measuredSize(measured[0], measured[1]);

        float w = measured[0];
        if (maxWidth > 0 && w > maxWidth) w = maxWidth;
        node.setBounds(0, 0, w, measured[1]);
    }

    private void measureBody(SceneNode node, float availW, float availH, PresentContext ctx) {
        // Resolve explicit sizes
        float w = -1, h = -1;
        if (node.width() != null) w = resolveSize(node.width(), availW, ctx);
        if (node.height() != null) h = resolveSize(node.height(), availH, ctx);

        if (node.shape() != null) {
            switch (node.shape()) {
                case "line" -> {
                    if (w < 0) w = availW;
                    if (h < 0) h = 1;
                }
                case "circle" -> {
                    float dim = w > 0 ? w : h > 0 ? h : 24;
                    w = dim; h = dim;
                }
                default -> {
                    if (w < 0) w = availW;
                    if (h < 0) h = 24;
                }
            }
        } else {
            if (w < 0) w = 24;
            if (h < 0) h = 24;
        }

        node.setBounds(0, 0, Math.max(w, 0), Math.max(h, 0));
    }

    private void measureContainer(SceneNode node, float availW, float availH,
                                  boolean shrinkWrap, PresentContext ctx) {
        boolean autoWidth = isAutoSpec(node.width());

        // Resolve explicit sizes
        float rw = resolveSize(node.width(), availW, ctx);
        if (rw > 0) node.explicitWidth(rw);
        float rh = resolveSize(node.height(), availH, ctx);
        if (rh > 0) node.explicitHeight(rh);

        // Aspect ratio
        if (node.aspectRatio() > 0) {
            float ratio = node.aspectRatio();
            boolean hasW = node.explicitWidth() > 0;
            boolean hasH = node.explicitHeight() > 0;
            if (hasW && !hasH) node.explicitHeight(node.explicitWidth() / ratio);
            else if (hasH && !hasW) node.explicitWidth(node.explicitHeight() * ratio);
            else if (!hasW) {
                float w = (availW > 0 && availH > 0)
                        ? Math.min(availW, availH * ratio) : availW > 0 ? availW : 100;
                node.explicitWidth(w);
                node.explicitHeight(w / ratio);
            }
        }

        // Resolve gap — mutate in place (String → Float)
        String gapSpec = node.gapSpec();
        if (gapSpec != null) {
            float g = resolveSize(gapSpec, availW, ctx);
            if (g > 0) node.gap(g); // mutate: String → Float
        }

        // Resolve padding and border
        resolvePadding(node, availW, availH, ctx);
        resolveBorder(node, ctx);

        float padH = node.paddingLeft() + node.paddingRight() + node.borderLeftWidth() + node.borderRightWidth();
        float padV = node.paddingTop() + node.paddingBottom() + node.borderTopWidth() + node.borderBottomWidth();
        float contentW = Math.max(0, availW - padH);
        float contentH = Math.max(0, availH - padV);

        if (node.explicitWidth() > 0) contentW = node.explicitWidth() - padH;
        if (node.explicitHeight() > 0) contentH = node.explicitHeight() - padV;

        if (node.children() == null || node.children().isEmpty()) {
            float w = node.explicitWidth() > 0 ? node.explicitWidth() : padH;
            float h = node.explicitHeight() > 0 ? node.explicitHeight() : padV;
            node.setBounds(0, 0, w, h);
            return;
        }

        String dir = node.layout() != null ? node.layout() : "vertical";
        switch (dir) {
            case "horizontal" -> measureHorizontal(node, contentW, contentH, ctx);
            case "stack" -> measureStack(node, contentW, contentH, ctx);
            default -> measureVertical(node, contentW, contentH, shrinkWrap || autoWidth, ctx);
        }
    }

    // =================================================================================
    // Vertical Layout
    // =================================================================================

    private void measureVertical(SceneNode box, float contentW, float contentH,
                                 boolean shrinkWrap, PresentContext ctx) {
        float padH = box.paddingLeft() + box.paddingRight() + box.borderLeftWidth() + box.borderRightWidth();
        float padV = box.paddingTop() + box.paddingBottom() + box.borderTopWidth() + box.borderBottomWidth();

        float fixedHeight = 0;
        int fillCount = 0;
        for (SceneNode child : box.children()) {
            if (child.isFillChild()) { fillCount++; }
            else { measure(child, contentW, contentH, shrinkWrap, ctx); fixedHeight += child.boundsHeight(); }
        }

        int childCount = box.children().size();
        float totalGap = (box.gapFloat() > 0 && childCount > 1) ? (childCount - 1) * box.gapFloat() : 0;
        float availForFill = contentH - fixedHeight - totalGap;

        if (fillCount > 0 && availForFill > 0) {
            float fillH = availForFill / fillCount;
            for (SceneNode child : box.children()) {
                if (child.isFillChild()) {
                    measure(child, contentW, fillH, shrinkWrap, ctx);
                    child.setBounds(child.boundsX(), child.boundsY(), child.boundsWidth(), fillH);
                }
            }
        } else if (fillCount > 0) {
            for (SceneNode child : box.children()) {
                if (child.isFillChild()) measure(child, contentW, 0, shrinkWrap, ctx);
            }
        }

        float totalHeight = totalGap;
        float maxChildW = 0;
        for (SceneNode child : box.children()) {
            totalHeight += child.boundsHeight();
            maxChildW = Math.max(maxChildW, child.boundsWidth());
        }

        float width = box.explicitWidth() > 0 ? box.explicitWidth()
                : shrinkWrap ? maxChildW + padH : contentW + padH;
        float naturalH = totalHeight + padV;
        float height = box.explicitHeight() > 0 ? box.explicitHeight() : naturalH;

        // Overflow detection
        String overflow = box.overflow();
        if (overflow != null && !"visible".equals(overflow) && box.explicitHeight() > 0) {
            box.contentHeight(naturalH);
            box.overflowsY(naturalH > box.explicitHeight());
        }

        box.setBounds(0, 0, width, height);
    }

    // =================================================================================
    // Horizontal Layout
    // =================================================================================

    private void measureHorizontal(SceneNode box, float contentW, float contentH, PresentContext ctx) {
        float padH = box.paddingLeft() + box.paddingRight() + box.borderLeftWidth() + box.borderRightWidth();
        float padV = box.paddingTop() + box.paddingBottom() + box.borderTopWidth() + box.borderBottomWidth();

        int childCount = box.children().size();
        float totalGap = (box.gapFloat() > 0 && childCount > 1) ? (childCount - 1) * box.gapFloat() : 0;

        float fixedWidth = 0;
        int fillCount = 0;
        float maxChildH = 0;
        for (SceneNode child : box.children()) {
            if (child.isFillChild()) { fillCount++; }
            else {
                measure(child, contentW, contentH, true, ctx);
                fixedWidth += child.boundsWidth();
                maxChildH = Math.max(maxChildH, child.boundsHeight());
            }
        }

        float availForFill = contentW - fixedWidth - totalGap;
        if (fillCount > 0 && availForFill > 0) {
            float fillW = availForFill / fillCount;
            for (SceneNode child : box.children()) {
                if (child.isFillChild()) {
                    measure(child, fillW, contentH, false, ctx);
                    float w = fillW;
                    if (child.aspectRatio() > 0 && child.explicitWidth() > 0
                            && child.explicitWidth() < fillW) w = child.explicitWidth();
                    float h = child.boundsHeight();
                    if (child.explicitHeight() <= 0 && box.explicitHeight() > 0) h = contentH;
                    child.setBounds(child.boundsX(), child.boundsY(), w, h);
                    maxChildH = Math.max(maxChildH, child.boundsHeight());
                }
            }
        } else if (fillCount > 0) {
            for (SceneNode child : box.children()) {
                if (child.isFillChild()) {
                    measure(child, 0, contentH, true, ctx);
                    maxChildH = Math.max(maxChildH, child.boundsHeight());
                }
            }
        }

        float totalWidth = fixedWidth + totalGap;
        if (fillCount > 0 && availForFill > 0) totalWidth += availForFill;
        else for (SceneNode child : box.children()) {
            if (child.isFillChild()) totalWidth += child.boundsWidth();
        }

        float width = box.explicitWidth() > 0 ? box.explicitWidth() : totalWidth + padH;
        float height = box.explicitHeight() > 0 ? box.explicitHeight() : maxChildH + padV;
        box.setBounds(0, 0, width, height);
    }

    // =================================================================================
    // Stack Layout
    // =================================================================================

    private void measureStack(SceneNode box, float contentW, float contentH, PresentContext ctx) {
        float padH = box.paddingLeft() + box.paddingRight() + box.borderLeftWidth() + box.borderRightWidth();
        float padV = box.paddingTop() + box.paddingBottom() + box.borderTopWidth() + box.borderBottomWidth();

        float maxChildW = 0, maxChildH = 0;
        for (SceneNode child : box.children()) {
            measure(child, contentW, contentH, false, ctx);
            maxChildW = Math.max(maxChildW, child.boundsWidth());
            maxChildH = Math.max(maxChildH, child.boundsHeight());
        }

        float width = box.explicitWidth() > 0 ? box.explicitWidth() : maxChildW + padH;
        float height = box.explicitHeight() > 0 ? box.explicitHeight() : maxChildH + padV;
        box.setBounds(0, 0, width, height);

        // Stretch children without explicit sizes
        float finalCW = width - padH;
        float finalCH = height - padV;
        for (SceneNode child : box.children()) {
            boolean hasExW = child.explicitWidth() > 0;
            boolean hasExH = child.explicitHeight() > 0;
            if (!hasExW || !hasExH) {
                float w = hasExW ? child.boundsWidth() : finalCW;
                float h = hasExH ? child.boundsHeight() : finalCH;
                child.setBounds(0, 0, w, h);
            }
        }
    }

    // =================================================================================
    // Fill Redistribution
    // =================================================================================

    private void redistributeFill(SceneNode box) {
        if (box.children() == null) return;
        float padV = box.paddingTop() + box.paddingBottom() + box.borderTopWidth() + box.borderBottomWidth();
        float contentH = box.boundsHeight() - padV;

        float fixedH = 0;
        int fillCount = 0;
        for (SceneNode child : box.children()) {
            if (child.isFillChild()) fillCount++;
            else fixedH += child.boundsHeight();
        }
        int childCount = box.children().size();
        if (box.gapFloat() > 0 && childCount > 1) fixedH += (childCount - 1) * box.gapFloat();

        if (fillCount > 0 && contentH > fixedH) {
            float fillH = (contentH - fixedH) / fillCount;
            for (SceneNode child : box.children()) {
                if (child.isFillChild()) {
                    child.setBounds(child.boundsX(), child.boundsY(), child.boundsWidth(), fillH);
                    if (isHorizontal(child)) stretchHorizontalChildren(child);
                    else if (isVertical(child)) redistributeFill(child);
                }
            }
        }
    }

    private void stretchHorizontalChildren(SceneNode box) {
        if (box.children() == null) return;
        float contentH = box.boundsHeight() - box.paddingTop() - box.paddingBottom()
                        - box.borderTopWidth() - box.borderBottomWidth();
        for (SceneNode child : box.children()) {
            if (child.boundsHeight() < contentH) {
                child.setBounds(child.boundsX(), child.boundsY(), child.boundsWidth(), contentH);
                if (isVertical(child)) redistributeFill(child);
                else if (isHorizontal(child)) stretchHorizontalChildren(child);
            }
        }
    }

    // =================================================================================
    // Position Phase
    // =================================================================================

    private void position(SceneNode node) {
        if (node == null || node.type() != SceneNode.NodeType.CONTAINER) return;
        if (node.children() == null) return;
        positionChildren(node);
    }

    private void positionChildren(SceneNode box) {
        float startX = box.boundsX() + box.borderLeftWidth() + box.paddingLeft();
        float startY = box.boundsY() + box.borderTopWidth() + box.paddingTop();
        float gap = box.gapFloat();
        String dir = box.layout() != null ? box.layout() : "vertical";
        String crossAlign = box.align();
        String justify = box.justify();

        float contentW = box.boundsWidth() - box.borderLeftWidth() - box.paddingLeft()
                        - box.borderRightWidth() - box.paddingRight();
        float contentH = box.boundsHeight() - box.borderTopWidth() - box.paddingTop()
                        - box.borderBottomWidth() - box.paddingBottom();

        if ("stack".equals(dir)) {
            for (SceneNode child : box.children()) {
                boolean hasExW = child.explicitWidth() > 0;
                boolean hasExH = child.explicitHeight() > 0;
                float w = hasExW ? child.boundsWidth() : contentW;
                float h = hasExH ? child.boundsHeight() : contentH;
                child.setBounds(startX, startY, w, h);
                position(child);
            }
        } else if ("horizontal".equals(dir)) {
            if ("center".equals(justify)) {
                float total = totalChildExtent(box, true);
                startX += (contentW - total) / 2f;
            } else if ("end".equals(justify)) {
                float total = totalChildExtent(box, true);
                startX += contentW - total;
            }

            float childX = startX;
            for (int i = 0; i < box.children().size(); i++) {
                SceneNode child = box.children().get(i);
                float childY = startY;
                if ("center".equals(crossAlign)) childY = startY + (contentH - child.boundsHeight()) / 2f;
                else if ("end".equals(crossAlign)) childY = startY + contentH - child.boundsHeight();
                child.setBounds(childX, childY, child.boundsWidth(), child.boundsHeight());
                position(child);
                childX += child.boundsWidth();
                if (gap > 0 && i < box.children().size() - 1) childX += gap;
            }
        } else {
            // Vertical
            if ("center".equals(justify)) {
                float total = totalChildExtent(box, false);
                startY += (contentH - total) / 2f;
            } else if ("end".equals(justify)) {
                float total = totalChildExtent(box, false);
                startY += contentH - total;
            }

            float childY = startY;
            for (int i = 0; i < box.children().size(); i++) {
                SceneNode child = box.children().get(i);
                float childX = startX;
                if ("center".equals(crossAlign)) childX = startX + (contentW - child.boundsWidth()) / 2f;
                else if ("end".equals(crossAlign)) childX = startX + contentW - child.boundsWidth();
                child.setBounds(childX, childY, child.boundsWidth(), child.boundsHeight());
                position(child);
                childY += child.boundsHeight();
                if (gap > 0 && i < box.children().size() - 1) childY += gap;
            }
        }
    }

    // =================================================================================
    // Helpers
    // =================================================================================

    private float totalChildExtent(SceneNode box, boolean horizontal) {
        if (box.children() == null) return 0;
        float total = 0;
        for (SceneNode child : box.children())
            total += horizontal ? child.boundsWidth() : child.boundsHeight();
        int count = box.children().size();
        if (box.gapFloat() > 0 && count > 1) total += (count - 1) * box.gapFloat();
        return total;
    }

    private static boolean isVertical(SceneNode node) {
        return node.type() == SceneNode.NodeType.CONTAINER
                && (node.layout() == null || "vertical".equals(node.layout()));
    }

    private static boolean isHorizontal(SceneNode node) {
        return node.type() == SceneNode.NodeType.CONTAINER
                && "horizontal".equals(node.layout());
    }

    private void applyProperty(SceneNode node, String property, String value) {
        switch (property) {
            case "background" -> node.background(value);
            case "foreground" -> node.foreground(value);
            case "border" -> node.border(value);
            case "padding" -> node.padding(value);
            case "margin" -> node.margin(value);
            case "width" -> node.width(value);
            case "height" -> node.height(value);
            case "gap" -> node.gap(value);
            case "corner" -> node.corner(value);
            case "fontSize" -> node.fontSize(value);
            case "fontFamily" -> node.fontFamily(value);
            case "fontWeight" -> node.fontWeight(value);
            case "opacity" -> node.opacity(value);
            case "overflow" -> node.overflow(value);
            case "visible" -> node.visible(value);
            case "cursor" -> node.cursor(value);
            case "fill" -> node.fill(value);
            case "stroke" -> node.stroke(value);
            case "strokeWidth" -> node.strokeWidth(value);
            case "rotation" -> node.rotation(value);
            case "align" -> node.align(value);
            case "justify" -> node.justify(value);
        }
    }
}
