package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.style.Selector;
import dev.everydaythings.graph.ui.style.StyleProperties;
import dev.everydaythings.graph.ui.style.Stylesheet;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Text/Terminal implementation of SurfaceRenderer.
 *
 * <p>Builds ANSI-styled text from render instructions.
 * This is a "dumb" renderer - it just renders primitives based on
 * computed styles from the stylesheet. It doesn't know about trees,
 * buttons, or any other semantic structures.
 *
 * <p>Supports bordered boxes via buffered rendering: when a box has
 * a visible border, its children render into a sub-buffer. On endBox(),
 * the content is wrapped with unicode box-drawing characters.
 *
 * <p>Supports side-by-side horizontal layout: when a HORIZONTAL container
 * has bordered children, each child renders into its own column buffer.
 * On endBox() of the horizontal container, columns are zipped line by line.
 *
 * <p>Tracks element positions for mouse hit-testing.
 */
public class TuiSurfaceRenderer implements SurfaceRenderer {

    private final StringBuilder buffer = new StringBuilder();
    private int indentLevel = 0;
    private static final String INDENT = "  ";

    // Style system
    private final Stylesheet stylesheet;
    private RenderContext renderContext;

    // Container context stack
    private final Deque<ContainerContext> contextStack = new ArrayDeque<>();

    // Current metadata for next element
    private String nextType;
    private String nextId;
    private boolean nextEditable;

    // Pending properties for the next box
    private int pendingMaxWidthColumns = -1;
    private int pendingGapColumns = -1;
    private String pendingOverflow;

    // Pending shape state
    private String pendingShapeType;
    private String pendingShapeFill;
    private String pendingShapeStroke;
    private String pendingShapeStrokeWidth;
    private String pendingShapeCornerRadius;
    private String pendingShapePath;
    private String pendingShapeWidth;
    private String pendingShapeHeight;

    // Pending rotation/transform state
    private double pendingRotation;
    private boolean hasPendingRotation;
    private float pendingTransformOriginX = 0.5f;
    private float pendingTransformOriginY = 0.5f;

    // Container query skip depth (> 0 = skipping nested content)
    private int querySkipDepth = 0;

    // Hit-testing support
    private int currentRow = 0;
    private int currentCol = 0;
    private final List<HitRegion> hitRegions = new ArrayList<>();
    private final List<PendingEvent> pendingEvents = new ArrayList<>();
    private int elementStartRow = 0;
    private int elementStartCol = 0;

    // Event handler
    private BiConsumer<String, String> eventHandler;

    public record HitRegion(
            int startRow, int startCol,
            int endRow, int endCol,
            String eventType,
            String action,
            String target,
            String id
    ) {
        public boolean contains(int row, int col) {
            if (row < startRow || row > endRow) return false;
            if (row == startRow && col < startCol) return false;
            if (row == endRow && col > endCol) return false;
            return true;
        }
    }

    private record PendingEvent(String on, String action, String target) {}

    /**
     * Container context with optional sub-buffer for bordered boxes
     * and optional column collection for horizontal layout.
     */
    private static class ContainerContext {
        final Scene.Direction direction;
        final Selector elementSelector;
        final StyleProperties computedStyles;

        // Border rendering (null = no border, use parent buffer)
        final StringBuilder ownBuffer;
        final BoxBorder border;
        final String background;
        final int explicitWidth;
        final int explicitHeight;
        final int[] padding; // [top, right, bottom, left]
        final boolean rounded;

        // Horizontal column layout (null = not collecting columns)
        final List<List<String>> childColumns;
        final int gapColumns;
        int availableWidth;
        int usedWidth;

        // Overflow behavior ("visible", "hidden", "scroll", "auto")
        String overflow = "visible";

        // Grid-backed STACK rendering
        CharGrid grid;
        double containerRotation;
        float transformOriginX = 0.5f;
        float transformOriginY = 0.5f;
        String widthSpec;
        String heightSpec;

        /** Simple context (no border, no column collection). */
        ContainerContext(Scene.Direction direction, Selector selector, StyleProperties styles) {
            this.direction = direction;
            this.elementSelector = selector;
            this.computedStyles = styles;
            this.ownBuffer = null;
            this.border = null;
            this.background = null;
            this.explicitWidth = -1;
            this.explicitHeight = -1;
            this.padding = null;
            this.rounded = false;
            this.childColumns = null;
            this.gapColumns = 0;
        }

        /** Horizontal column-collecting context (no border, collects child columns). */
        ContainerContext(Scene.Direction direction, Selector selector, StyleProperties styles,
                         int gapColumns, int availableWidth) {
            this.direction = direction;
            this.elementSelector = selector;
            this.computedStyles = styles;
            this.ownBuffer = null;
            this.border = null;
            this.background = null;
            this.explicitWidth = -1;
            this.explicitHeight = -1;
            this.padding = null;
            this.rounded = false;
            this.childColumns = new ArrayList<>();
            this.gapColumns = gapColumns;
            this.availableWidth = availableWidth;
            this.usedWidth = 0;
        }

        /** Bordered context with own buffer. */
        ContainerContext(Scene.Direction direction, Selector selector, StyleProperties styles,
                         BoxBorder border, String background,
                         int explicitWidth, int explicitHeight,
                         int[] padding, boolean rounded) {
            this.direction = direction;
            this.elementSelector = selector;
            this.computedStyles = styles;
            this.ownBuffer = new StringBuilder();
            this.border = border;
            this.background = background;
            this.explicitWidth = explicitWidth;
            this.explicitHeight = explicitHeight;
            this.padding = padding;
            this.rounded = rounded;
            this.childColumns = null;
            this.gapColumns = 0;
        }

        boolean hasBorder() {
            return border != null && border.isVisible();
        }

        boolean isBuffered() {
            return ownBuffer != null;
        }

        boolean isCollectingColumns() {
            return childColumns != null;
        }

        /**
         * Buffered non-bordered context (child of column-collecting parent).
         * Content renders into ownBuffer, then gets added to parent's childColumns.
         * If collectColumns is true, this context also collects its own children as columns.
         */
        ContainerContext(Scene.Direction direction, Selector selector, StyleProperties styles,
                         int explicitWidth, boolean collectColumns, int gapColumns,
                         int availableWidth) {
            this.direction = direction;
            this.elementSelector = selector;
            this.computedStyles = styles;
            this.ownBuffer = new StringBuilder();
            this.border = null;
            this.background = null;
            this.explicitWidth = explicitWidth;
            this.explicitHeight = -1;
            this.padding = null;
            this.rounded = false;
            if (collectColumns) {
                this.childColumns = new ArrayList<>();
                this.gapColumns = gapColumns;
                this.availableWidth = availableWidth;
            } else {
                this.childColumns = null;
                this.gapColumns = 0;
                this.availableWidth = 0;
            }
            this.usedWidth = 0;
        }
    }

    // ==================== Construction ====================

    public TuiSurfaceRenderer() {
        this(Stylesheet.fromClasspath(), RenderContext.tui());
    }

    public TuiSurfaceRenderer(Stylesheet stylesheet, RenderContext renderContext) {
        this.stylesheet = stylesheet != null ? stylesheet : Stylesheet.fromClasspath();
        this.renderContext = renderContext;
    }

    public TuiSurfaceRenderer onEvent(BiConsumer<String, String> handler) {
        this.eventHandler = handler;
        return this;
    }

    /**
     * Update the render context (e.g., to add/remove states).
     */
    public TuiSurfaceRenderer withContext(RenderContext context) {
        this.renderContext = context;
        return this;
    }

    /**
     * Add a state to the render context.
     */
    public TuiSurfaceRenderer withState(String state) {
        this.renderContext = renderContext.withState(state);
        return this;
    }

    public String result() {
        return buffer.toString();
    }

    public List<HitRegion> hitRegions() {
        return hitRegions;
    }

    public HitRegion hitTest(int row, int col) {
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            HitRegion region = hitRegions.get(i);
            if (region.contains(row, col)) {
                return region;
            }
        }
        return null;
    }

    public HitRegion hitTest(int row, int col, String eventType) {
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            HitRegion region = hitRegions.get(i);
            if (region.contains(row, col) && region.eventType().equals(eventType)) {
                return region;
            }
        }
        return null;
    }

    // ==================== Active Buffer ====================

    /**
     * Get the active buffer to write to.
     *
     * <p>Walks up the context stack to find the nearest buffered ancestor
     * (bordered box with its own buffer). Non-buffered contexts (simple boxes,
     * column-collecting horizontals) are transparent.
     */
    private StringBuilder activeBuffer() {
        for (ContainerContext ctx : contextStack) {
            if (ctx.isBuffered()) {
                return ctx.ownBuffer;
            }
        }
        return buffer;
    }

    // ==================== Style Resolution ====================

    /**
     * Resolve styles for an element.
     */
    private StyleProperties resolveStyles(List<String> classes) {
        Selector element = Selector.element(nextType, classes, nextId);
        return stylesheet.resolve(element, renderContext);
    }

    /**
     * Consume and clear pending metadata (type, id).
     */
    private void clearPendingMetadata() {
        nextType = null;
        nextId = null;
        nextEditable = false;
    }

    // ==================== Pending Properties ====================

    @Override
    public void maxWidth(String maxWidth) {
        if (querySkipDepth > 0) return;
        this.pendingMaxWidthColumns = resolveMaxWidthColumns(maxWidth);
    }

    @Override
    public void gap(String gap) {
        if (querySkipDepth > 0) return;
        this.pendingGapColumns = resolveGapColumns(gap);
    }

    @Override
    public void overflow(String overflow) {
        if (querySkipDepth > 0) return;
        this.pendingOverflow = overflow;
    }

    // ==================== Shape / Rotation ====================

    @Override
    public void shape(String type, String cornerRadius, String fill,
                      String stroke, String strokeWidth, String path) {
        if (querySkipDepth > 0) return;
        // Check if we're inside a grid-backed STACK — draw shape into grid
        CharGrid grid = findNearestGrid();
        if (grid != null) {
            drawShapeIntoGrid(grid, type, fill, stroke);
            clearPendingShapeState();
            return;
        }

        // Otherwise store as pending (consumed by next beginBox or rendered inline)
        pendingShapeType = type;
        pendingShapeCornerRadius = cornerRadius;
        pendingShapeFill = fill;
        pendingShapeStroke = stroke;
        pendingShapeStrokeWidth = strokeWidth;
        pendingShapePath = path;
    }

    @Override
    public void shapeSize(String width, String height) {
        if (querySkipDepth > 0) return;
        pendingShapeWidth = width;
        pendingShapeHeight = height;
    }

    @Override
    public void rotation(double degrees) {
        if (querySkipDepth > 0) return;
        pendingRotation = degrees;
        hasPendingRotation = true;
    }

    @Override
    public void transformOrigin(float xFrac, float yFrac) {
        if (querySkipDepth > 0) return;
        pendingTransformOriginX = xFrac;
        pendingTransformOriginY = yFrac;
    }

    private void clearPendingShapeState() {
        pendingShapeType = null;
        pendingShapeFill = null;
        pendingShapeStroke = null;
        pendingShapeStrokeWidth = null;
        pendingShapeCornerRadius = null;
        pendingShapePath = null;
        pendingShapeWidth = null;
        pendingShapeHeight = null;
    }

    private void clearPendingRotationState() {
        pendingRotation = 0;
        hasPendingRotation = false;
        pendingTransformOriginX = 0.5f;
        pendingTransformOriginY = 0.5f;
    }

    /**
     * Transfer pending rotation state onto a ContainerContext and clear it.
     */
    private void consumePendingOverflow(ContainerContext ctx) {
        if (pendingOverflow != null) {
            ctx.overflow = pendingOverflow;
            pendingOverflow = null;
        }
    }

    private void consumePendingRotation(ContainerContext ctx) {
        if (hasPendingRotation) {
            ctx.containerRotation = pendingRotation;
            ctx.transformOriginX = pendingTransformOriginX;
            ctx.transformOriginY = pendingTransformOriginY;
            clearPendingRotationState();
        }
    }

    // ==================== Text Primitive ====================

    @Override
    public void text(String content, List<String> classes) {
        if (querySkipDepth > 0) return;
        StyleProperties styles = resolveStyles(classes);

        // Check if hidden
        if (styles.isHidden()) {
            clearPendingMetadata();
            return;
        }

        if (content == null || content.isEmpty()) {
            clearPendingMetadata();
            return;
        }

        // Check if inside a grid-backed STACK — place text centered in grid
        CharGrid grid = findNearestGrid();
        if (grid != null) {
            String color = styles.getColor().map(this::colorToAnsi).orElse(null);
            // Place near the bottom third of the clock
            grid.drawTextCentered(grid.width() / 2, grid.height() * 2 / 3, content, color);
            clearPendingMetadata();
            return;
        }

        StringBuilder buf = activeBuffer();

        markElementStart();
        if (isHorizontalContext()) {
            appendStyledText(buf, content, styles);
            buf.append(" ");
            currentCol += BoxDrawing.stripAnsiLength(content) + 1;
        } else {
            appendIndent(buf);
            appendStyledText(buf, content, styles);
            createHitRegions();
            buf.append("\n");
            trackNewline();
        }
        clearPendingMetadata();
    }

    @Override
    public void formattedText(String content, String format, List<String> classes) {
        if (querySkipDepth > 0) return;
        StyleProperties styles = resolveStyles(classes);

        if (styles.isHidden()) {
            clearPendingMetadata();
            return;
        }

        if (content == null || content.isEmpty()) {
            clearPendingMetadata();
            return;
        }

        StringBuilder buf = activeBuffer();

        markElementStart();
        if (isHorizontalContext()) {
            if ("code".equals(format)) {
                buf.append(ANSI.CYAN).append(content).append(ANSI.RESET);
            } else {
                appendStyledText(buf, content, styles);
            }
            buf.append(" ");
            currentCol += BoxDrawing.stripAnsiLength(content) + 1;
        } else {
            appendIndent(buf);
            if ("code".equals(format)) {
                buf.append(ANSI.CYAN).append(content).append(ANSI.RESET);
            } else {
                appendStyledText(buf, content, styles);
            }
            createHitRegions();
            buf.append("\n");
            trackNewline();
        }
        clearPendingMetadata();
    }

    // ==================== Rich Text ====================

    @Override
    public void richText(List<TextSpan> spans, List<String> paragraphStyles) {
        if (querySkipDepth > 0) return;
        StyleProperties styles = resolveStyles(paragraphStyles);

        if (styles.isHidden()) {
            clearPendingMetadata();
            return;
        }

        if (spans == null || spans.isEmpty()) {
            clearPendingMetadata();
            return;
        }

        StringBuilder buf = activeBuffer();
        markElementStart();

        if (isHorizontalContext()) {
            for (TextSpan span : spans) {
                appendSpan(buf, span, styles);
            }
            buf.append(" ");
            int totalWidth = 0;
            for (TextSpan span : spans) {
                totalWidth += BoxDrawing.stripAnsiLength(span.text());
            }
            currentCol += totalWidth + 1;
        } else {
            appendIndent(buf);
            for (TextSpan span : spans) {
                appendSpan(buf, span, styles);
            }
            createHitRegions();
            buf.append("\n");
            trackNewline();
        }
        clearPendingMetadata();
    }

    /**
     * Render a single TextSpan with its own ANSI formatting.
     */
    private void appendSpan(StringBuilder buf, TextSpan span, StyleProperties parentStyles) {
        String text = span.text();
        if (text == null || text.isEmpty()) return;

        List<String> spanStyles = span.styles();
        boolean hasFormat = false;

        if (spanStyles != null && !spanStyles.isEmpty()) {
            if (spanStyles.contains("bold") || spanStyles.contains("strong")) {
                buf.append(ANSI.BOLD);
                hasFormat = true;
            }
            if (spanStyles.contains("dim") || spanStyles.contains("muted")) {
                buf.append(ANSI.DIM);
                hasFormat = true;
            }
            if (spanStyles.contains("underline")) {
                buf.append(ANSI.UNDERLINE);
                hasFormat = true;
            }
            if (spanStyles.contains("reverse")) {
                buf.append(ANSI.REVERSE);
                hasFormat = true;
            }
            if (spanStyles.contains("code")) {
                buf.append(ANSI.CYAN);
                hasFormat = true;
            }
            // Check for color in span styles
            for (String cls : spanStyles) {
                String ansi = colorToAnsi(cls);
                if (ansi != null) {
                    buf.append(ansi);
                    hasFormat = true;
                    break;
                }
            }
        }

        // If no span-level formatting, apply parent paragraph styles
        if (!hasFormat) {
            appendStyledText(buf, text, parentStyles);
            return;
        }

        buf.append(text);
        buf.append(ANSI.RESET);
    }

    // ==================== Image Primitive ====================

    @Override
    public void image(String alt, ContentID image, ContentID solid, String size, String fit, List<String> classes) {
        if (querySkipDepth > 0) return;
        StyleProperties styles = resolveStyles(classes);

        if (styles.isHidden()) {
            clearPendingMetadata();
            return;
        }

        if (alt == null || alt.isEmpty()) {
            clearPendingMetadata();
            return;
        }

        StringBuilder buf = activeBuffer();

        markElementStart();
        if (isHorizontalContext()) {
            appendStyledText(buf, alt, styles);
            buf.append(" ");
            currentCol += BoxDrawing.stripAnsiLength(alt) + 1;
        } else {
            appendIndent(buf);
            appendStyledText(buf, alt, styles);
            createHitRegions();
            buf.append("\n");
            trackNewline();
        }
        clearPendingMetadata();
    }

    // ==================== Container Primitive ====================

    @Override
    public void beginBox(Scene.Direction direction, List<String> classes) {
        if (querySkipDepth > 0) return;
        StyleProperties styles = resolveStyles(classes);
        Selector element = Selector.element(nextType, classes, nextId);

        // 2-arg beginBox is used by content-level containers (tree nodes, etc.)
        // These are always simple contexts — no column collection.
        ContainerContext ctx = new ContainerContext(direction, element, styles);
        consumePendingOverflow(ctx);
        consumePendingRotation(ctx);
        contextStack.push(ctx);

        // If hidden, we still push context but won't render children
        if (styles.isHidden()) {
            clearPendingMetadata();
            return;
        }

        // Handle indentation for horizontal containers
        if (direction == Scene.Direction.HORIZONTAL) {
            appendIndent(activeBuffer());
        }

        clearPendingMetadata();
    }

    @Override
    public void beginBox(Scene.Direction direction, List<String> classes,
                          BoxBorder border, String background,
                          String width, String height, String padding) {
        if (querySkipDepth > 0) return;
        StyleProperties styles = resolveStyles(classes);
        Selector element = Selector.element(nextType, classes, nextId);

        // STACK direction with a pending background shape → grid-backed rendering
        if (direction == Scene.Direction.STACK && pendingShapeType != null) {
            ContainerContext ctx = createGridStackContext(element, styles, width, height);
            consumePendingOverflow(ctx);
            consumePendingRotation(ctx);
            contextStack.push(ctx);
            clearPendingMetadata();
            return;
        }

        // STACK without shape — just use simple context (children render normally)
        if (direction == Scene.Direction.STACK) {
            ContainerContext ctx = new ContainerContext(direction, element, styles);
            consumePendingOverflow(ctx);
            consumePendingRotation(ctx);
            contextStack.push(ctx);
            clearPendingMetadata();
            return;
        }

        boolean hasBorder = border != null && border.isVisible();

        if (hasBorder) {
            // Parse sizing
            int explicitWidth = -1;
            int explicitHeight = -1;
            if (width != null && !width.isEmpty()) {
                SizeValue sv = SizeValue.parse(width);
                if (sv != null && !sv.isAuto()) explicitWidth = sv.toColumns(renderContext);
            }
            if (height != null && !height.isEmpty()) {
                SizeValue sv = SizeValue.parse(height);
                if (sv != null && !sv.isAuto()) explicitHeight = sv.toRows(renderContext);
            }

            // Parse padding
            int[] pad = parsePadding(padding);

            // Check if rounded
            boolean rounded = border.hasRadius();

            // Apply maxWidth constraint from pending or compute fill width
            ContainerContext parentCtx = contextStack.peek();
            if (parentCtx != null && parentCtx.isCollectingColumns()) {
                int maxW = consumePendingMaxWidth();
                if (maxW > 0 && explicitWidth < 0) {
                    // maxWidth sets the total column width (including border + padding)
                    int borderCols = (border.left().isVisible() ? 1 : 0)
                            + (border.right().isVisible() ? 1 : 0);
                    explicitWidth = maxW - borderCols - pad[1] - pad[3];
                    if (explicitWidth < 1) explicitWidth = 1;
                } else if (explicitWidth < 0) {
                    // Fill remaining space
                    int gapBefore = parentCtx.usedWidth > 0 ? parentCtx.gapColumns : 0;
                    int remaining = parentCtx.availableWidth - parentCtx.usedWidth - gapBefore;
                    int borderCols = (border.left().isVisible() ? 1 : 0)
                            + (border.right().isVisible() ? 1 : 0);
                    explicitWidth = remaining - borderCols - pad[1] - pad[3];
                    if (explicitWidth < 1) explicitWidth = 1;
                }
            } else {
                consumePendingMaxWidth(); // discard if not in horizontal
            }

            ContainerContext borderedCtx = new ContainerContext(direction, element, styles,
                    border, background, explicitWidth, explicitHeight, pad, rounded);
            consumePendingOverflow(borderedCtx);
            consumePendingRotation(borderedCtx);
            contextStack.push(borderedCtx);
        } else {
            // No visible border — 7-arg form is used by layout-level containers
            ContainerContext parentCtx = contextStack.peek();
            ContainerContext newCtx;
            if (parentCtx != null && parentCtx.isCollectingColumns()) {
                // Non-bordered child inside column-collecting parent:
                // buffer so parent can collect this as a column.
                int colWidth = resolveChildWidth(width, parentCtx);
                int maxW = consumePendingMaxWidth();
                if (colWidth < 0 && maxW > 0) colWidth = maxW;

                if (direction == Scene.Direction.HORIZONTAL) {
                    // Also collect this child's own sub-columns
                    int gap = consumePendingGap();
                    int available = colWidth > 0 ? colWidth : parentCtx.availableWidth - parentCtx.usedWidth;
                    newCtx = new ContainerContext(direction, element, styles,
                            colWidth, true, gap, available);
                } else {
                    consumePendingGap();
                    newCtx = new ContainerContext(direction, element, styles,
                            colWidth, false, 0, 0);
                }
            } else if (direction == Scene.Direction.HORIZONTAL) {
                // Layout-level HORIZONTAL: enable column collection for side-by-side children
                int gap = consumePendingGap();
                int available = computeAvailableWidth();
                newCtx = new ContainerContext(direction, element, styles, gap, available);
                consumePendingMaxWidth(); // discard
            } else {
                newCtx = new ContainerContext(direction, element, styles);
                consumePendingMaxWidth(); // discard
            }
            consumePendingOverflow(newCtx);
            consumePendingRotation(newCtx);
            contextStack.push(newCtx);
        }

        clearPendingMetadata();
    }

    @Override
    public void endBox() {
        if (querySkipDepth > 0) return;
        ContainerContext ctx = contextStack.poll();
        if (ctx == null) return;

        // Skip if this box was hidden
        if (ctx.computedStyles.isHidden()) {
            return;
        }

        // Grid-backed STACK: convert grid to lines and write to parent buffer
        if (ctx.grid != null) {
            List<String> gridLines = ctx.grid.toLines();
            StringBuilder parentBuf = activeBuffer();
            for (String line : gridLines) {
                appendIndent(parentBuf);
                parentBuf.append(line);
                parentBuf.append("\n");
                trackNewline();
            }
            return;
        }

        // STACK without grid: children already wrote to parent buffer, nothing special to do
        if (ctx.direction == Scene.Direction.STACK && !ctx.isBuffered() && !ctx.isCollectingColumns()) {
            return;
        }

        if (ctx.hasBorder() && ctx.isBuffered()) {
            // Bordered box: wrap the buffered content
            // Border color is now baked into border characters by wrapBorderedContent
            List<String> bordered = wrapBorderedContent(ctx);

            // Check if the parent is collecting horizontal columns
            ContainerContext parentCtx = contextStack.peek();
            if (parentCtx != null && parentCtx.isCollectingColumns()) {
                // Apply background color if present
                String bgAnsi = backgroundToAnsi(ctx.background);
                if (bgAnsi != null) {
                    List<String> colored = new ArrayList<>(bordered.size());
                    for (String line : bordered) {
                        colored.add(bgAnsi + line + ANSI.RESET);
                    }
                    bordered = colored;
                }
                parentCtx.childColumns.add(bordered);
                // Track used width (including gap before this column)
                int colWidth = bordered.isEmpty() ? 0 : BoxDrawing.stripAnsiLength(bordered.get(0));
                if (parentCtx.usedWidth > 0) {
                    parentCtx.usedWidth += parentCtx.gapColumns;
                }
                parentCtx.usedWidth += colWidth;
            } else {
                // Write directly to parent buffer (existing vertical layout)
                String bgAnsi = backgroundToAnsi(ctx.background);
                StringBuilder parentBuf = activeBuffer();
                for (String line : bordered) {
                    appendIndent(parentBuf);
                    if (bgAnsi != null) parentBuf.append(bgAnsi);
                    parentBuf.append(line);
                    if (bgAnsi != null) parentBuf.append(ANSI.RESET);
                    parentBuf.append("\n");
                    trackNewline();
                }
            }
        } else if (ctx.isCollectingColumns()) {
            if (ctx.isBuffered()) {
                // Buffered + collecting: zip own children into own buffer first
                if (!ctx.childColumns.isEmpty()) {
                    zipColumns(ctx.childColumns, ctx.gapColumns, ctx.ownBuffer);
                }
                // Then add own buffer to parent's columns
                addBufferAsColumn(ctx);
            } else if (!ctx.childColumns.isEmpty()) {
                // Normal collecting: zip into active buffer (parent)
                StringBuilder buf = activeBuffer();
                zipColumns(ctx.childColumns, ctx.gapColumns, buf);
            }
        } else if (ctx.isBuffered()) {
            // Buffered non-bordered, non-collecting: add to parent's columns
            addBufferAsColumn(ctx);
        } else {
            // Simple box (no border, no columns)
            if (ctx.direction == Scene.Direction.HORIZONTAL) {
                StringBuilder buf = activeBuffer();
                buf.append(ANSI.RESET);
                buf.append("\n");
                trackNewline();
            }
        }
    }

    /**
     * Wrap buffered content with border characters.
     */
    private List<String> wrapBorderedContent(ContainerContext ctx) {
        String childContent = ctx.ownBuffer.toString();

        // Split into lines (remove trailing newline if present)
        List<String> contentLines = new ArrayList<>();
        if (!childContent.isEmpty()) {
            String[] split = childContent.split("\n", -1);
            for (String line : split) {
                contentLines.add(line);
            }
            // Remove trailing empty line from final \n
            if (!contentLines.isEmpty() && contentLines.getLast().isEmpty()) {
                contentLines.removeLast();
            }
        }

        // Clip content when overflow is not "visible" and we have a height limit
        if (!"visible".equals(ctx.overflow) && ctx.explicitHeight > 0
                && contentLines.size() > ctx.explicitHeight) {
            contentLines = new ArrayList<>(contentLines.subList(0, ctx.explicitHeight));
        }

        // Get padding
        int padTop = 0, padRight = 0, padBottom = 0, padLeft = 0;
        if (ctx.padding != null) {
            padTop = ctx.padding[0];
            padRight = ctx.padding[1];
            padBottom = ctx.padding[2];
            padLeft = ctx.padding[3];
        }

        // Wrap with border characters — pass border color so it's applied
        // directly to border chars (survives content ANSI resets)
        String borderColor = getBorderColor(ctx.border);
        return BoxDrawing.wrapInBorder(
                contentLines, ctx.border,
                ctx.explicitWidth,
                padTop, padRight, padBottom, padLeft,
                ctx.rounded, renderContext, borderColor);
    }

    /**
     * Zip multiple columns of lines side by side with gap spacing.
     */
    private void zipColumns(List<List<String>> columns, int gapCols, StringBuilder buf) {
        if (columns.isEmpty()) return;

        // Find max row count across all columns
        int maxRows = 0;
        for (List<String> col : columns) {
            if (col.size() > maxRows) maxRows = col.size();
        }

        // Compute visible width of each column (from first line)
        int[] colWidths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            List<String> col = columns.get(i);
            colWidths[i] = col.isEmpty() ? 0 : BoxDrawing.stripAnsiLength(col.get(0));
        }

        String gapStr = gapCols > 0 ? " ".repeat(gapCols) : "";

        // Zip rows
        for (int row = 0; row < maxRows; row++) {
            appendIndent(buf);
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) buf.append(gapStr);
                List<String> col = columns.get(i);
                if (row < col.size()) {
                    buf.append(col.get(row));
                    // Pad to column width if this line is shorter (shouldn't happen with borders, but safety)
                    int lineWidth = BoxDrawing.stripAnsiLength(col.get(row));
                    if (lineWidth < colWidths[i]) {
                        buf.append(" ".repeat(colWidths[i] - lineWidth));
                    }
                } else {
                    // Empty row — pad with spaces
                    buf.append(" ".repeat(colWidths[i]));
                }
            }
            buf.append("\n");
            trackNewline();
        }
    }

    // ==================== Container Queries ====================

    @Override
    public boolean beginQuery(String condition) {
        if (querySkipDepth > 0) {
            querySkipDepth++;
            return false;
        }
        int available = computeAvailableWidth();
        // Use tighter parent constraint if available
        ContainerContext parent = contextStack.peek();
        if (parent != null && parent.explicitWidth > 0) {
            available = parent.explicitWidth;
        } else if (parent != null && parent.isCollectingColumns()) {
            int remaining = parent.availableWidth - parent.usedWidth;
            if (parent.usedWidth > 0) remaining -= parent.gapColumns;
            if (remaining > 0) available = remaining;
        }
        if (!evaluateQueryCondition(condition, available)) {
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
     * Evaluate a container query condition against available width in columns.
     *
     * <p>Supports: {@code width >= 30ch}, {@code width < 200px}, {@code width >= 50%}
     */
    private boolean evaluateQueryCondition(String condition, int availableColumns) {
        // Parse: "width >= 30ch"
        String[] parts = condition.trim().split("\\s+", 3);
        if (parts.length != 3) return true; // malformed → show content

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

        // Convert to columns
        double threshold;
        switch (unit) {
            case "ch", "" -> threshold = value;
            case "px" -> threshold = value / 8.0; // approximate px-to-char
            case "%" -> threshold = availableColumns * value / 100.0;
            case "em" -> threshold = value * 2; // approximate em-to-char
            default -> threshold = value;
        }

        double actual = availableColumns;
        return switch (op) {
            case ">=" -> actual >= threshold;
            case "<=" -> actual <= threshold;
            case ">" -> actual > threshold;
            case "<" -> actual < threshold;
            case "==" -> actual == threshold;
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

    // ==================== Events ====================

    @Override
    public void event(String on, String action, String target) {
        pendingEvents.add(new PendingEvent(on, action, target != null ? target : ""));
    }

    // ==================== Helpers ====================

    /**
     * Check if we're inside a horizontal container (for inline text flow).
     * Returns true if any ancestor is a simple (non-column-collecting) HORIZONTAL,
     * meaning text should render inline rather than vertical.
     */
    private boolean isHorizontalContext() {
        for (ContainerContext ctx : contextStack) {
            if (ctx.direction == Scene.Direction.HORIZONTAL && !ctx.isCollectingColumns()) {
                return true;
            }
            // Stop at buffered boundaries — inline doesn't cross bordered containers
            if (ctx.isBuffered()) {
                return false;
            }
        }
        return false;
    }

    /**
     * Check if the nearest ancestor is a column-collecting horizontal context.
     */
    private boolean isInsideColumnCollector() {
        ContainerContext ctx = contextStack.peek();
        return ctx != null && ctx.isCollectingColumns();
    }

    private boolean isParentHidden() {
        ContainerContext ctx = contextStack.peek();
        return ctx != null && ctx.computedStyles.isHidden();
    }

    private void appendIndent(StringBuilder buf) {
        for (int i = 0; i < indentLevel; i++) {
            buf.append(INDENT);
            currentCol += INDENT.length();
        }
    }

    /**
     * Append text with styles from computed StyleProperties.
     */
    private void appendStyledText(StringBuilder buf, String text, StyleProperties styles) {
        // Apply ANSI styles based on computed properties
        boolean hasStyle = false;

        if (styles.isBold()) {
            buf.append(ANSI.BOLD);
            hasStyle = true;
        }
        if (styles.isDim()) {
            buf.append(ANSI.DIM);
            hasStyle = true;
        }
        if (styles.isReverse()) {
            buf.append(ANSI.REVERSE);
            hasStyle = true;
        }
        if (styles.isUnderline()) {
            buf.append(ANSI.UNDERLINE);
            hasStyle = true;
        }

        // Color
        styles.getColor().ifPresent(color -> {
            String ansi = colorToAnsi(color);
            if (ansi != null) {
                buf.append(ansi);
            }
        });

        buf.append(text);
        currentCol += BoxDrawing.stripAnsiLength(text);

        if (hasStyle || styles.getColor().isPresent()) {
            buf.append(ANSI.RESET);
        }
    }

    /**
     * Convert a color name/value to ANSI code.
     * Supports named colors and #RRGGBB hex via true-color escape sequences.
     */
    private String colorToAnsi(String color) {
        if (color.startsWith("#") && color.length() == 7) {
            try {
                int r = Integer.parseInt(color.substring(1, 3), 16);
                int g = Integer.parseInt(color.substring(3, 5), 16);
                int b = Integer.parseInt(color.substring(5, 7), 16);
                return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (color.toLowerCase()) {
            case "red", "danger" -> ANSI.RED;
            case "green", "success" -> ANSI.GREEN;
            case "yellow", "warning" -> ANSI.YELLOW;
            case "blue", "primary" -> ANSI.BLUE;
            case "cyan", "info" -> ANSI.CYAN;
            case "magenta" -> ANSI.MAGENTA;
            case "gray", "muted" -> ANSI.DIM;
            case "white" -> ANSI.WHITE;
            default -> null;
        };
    }

    /**
     * Convert a background color name to ANSI code.
     * Supports named colors and #RRGGBB hex via true-color escape sequences.
     */
    private String backgroundToAnsi(String bg) {
        if (bg == null || bg.isEmpty()) return null;
        if (bg.startsWith("#") && bg.length() == 7) {
            try {
                int r = Integer.parseInt(bg.substring(1, 3), 16);
                int g = Integer.parseInt(bg.substring(3, 5), 16);
                int b = Integer.parseInt(bg.substring(5, 7), 16);
                return "\u001B[48;2;" + r + ";" + g + ";" + b + "m";
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (bg.toLowerCase()) {
            case "red" -> ANSI.BG_RED;
            case "green" -> ANSI.BG_GREEN;
            case "yellow" -> ANSI.BG_YELLOW;
            case "blue" -> ANSI.BG_BLUE;
            case "cyan" -> ANSI.BG_CYAN;
            case "magenta" -> ANSI.BG_MAGENTA;
            case "white" -> ANSI.BG_WHITE;
            case "gray", "grey" -> ANSI.BG_GRAY;
            case "reverse" -> ANSI.REVERSE;
            default -> null;
        };
    }

    /**
     * Get the primary border color for ANSI rendering.
     * Uses the first visible side's color.
     */
    private String getBorderColor(BoxBorder border) {
        if (border == null) return null;
        String color = null;
        if (border.top().isVisible() && border.top().color() != null) {
            color = border.top().color();
        } else if (border.left().isVisible() && border.left().color() != null) {
            color = border.left().color();
        } else if (border.right().isVisible() && border.right().color() != null) {
            color = border.right().color();
        } else if (border.bottom().isVisible() && border.bottom().color() != null) {
            color = border.bottom().color();
        }
        return color != null ? colorToAnsi(color) : null;
    }

    /**
     * Add a buffered non-bordered context's content to the parent's column list.
     * If the parent is not collecting columns, writes directly to parent buffer.
     */
    private void addBufferAsColumn(ContainerContext ctx) {
        ContainerContext parentCtx = contextStack.peek();
        if (parentCtx == null || !parentCtx.isCollectingColumns()) {
            // Not inside a column-collecting parent — write buffer directly
            StringBuilder parentBuf = activeBuffer();
            parentBuf.append(ctx.ownBuffer);
            return;
        }

        // Split buffer into lines
        String content = ctx.ownBuffer.toString();
        List<String> lines = new ArrayList<>();
        if (!content.isEmpty()) {
            String[] split = content.split("\n", -1);
            for (String line : split) lines.add(line);
            // Remove trailing empty line from final \n
            if (!lines.isEmpty() && lines.getLast().isEmpty()) {
                lines.removeLast();
            }
        }

        // Determine column width: max of explicit width and actual content width
        int maxLineWidth = 0;
        for (String line : lines) {
            int w = BoxDrawing.stripAnsiLength(line);
            if (w > maxLineWidth) maxLineWidth = w;
        }
        int colWidth = ctx.explicitWidth > 0
                ? Math.max(ctx.explicitWidth, maxLineWidth)
                : maxLineWidth;

        // Pad all lines to column width
        List<String> padded = new ArrayList<>(lines.size());
        for (String line : lines) {
            int w = BoxDrawing.stripAnsiLength(line);
            if (w < colWidth) {
                padded.add(line + " ".repeat(colWidth - w));
            } else {
                padded.add(line);
            }
        }

        parentCtx.childColumns.add(padded);
        if (parentCtx.usedWidth > 0) {
            parentCtx.usedWidth += parentCtx.gapColumns;
        }
        parentCtx.usedWidth += colWidth;
    }

    /**
     * Resolve a child width string against a column-collecting parent.
     * Percentages resolve against parent's available width.
     */
    private int resolveChildWidth(String width, ContainerContext parentCtx) {
        if (width == null || width.isEmpty()) return -1;
        SizeValue sv = SizeValue.parse(width);
        if (sv == null || sv.isAuto()) return -1;
        if (sv.isPercentage()) {
            return (int) (sv.value() * parentCtx.availableWidth / 100.0);
        }
        return sv.toColumns(renderContext);
    }

    /**
     * Resolve a maxWidth string to terminal columns.
     * Handles percentages relative to viewport width.
     */
    private int resolveMaxWidthColumns(String maxWidth) {
        if (maxWidth == null || maxWidth.isEmpty()) return -1;
        SizeValue sv = SizeValue.parse(maxWidth);
        if (sv == null || sv.isAuto()) return -1;
        if (sv.isPercentage()) {
            return (int) (sv.value() * renderContext.viewportWidth() / 100.0);
        }
        return sv.toColumns(renderContext);
    }

    /**
     * Resolve a gap string to terminal columns.
     */
    private int resolveGapColumns(String gap) {
        if (gap == null || gap.isEmpty()) return 0;
        SizeValue sv = SizeValue.parse(gap);
        if (sv == null || sv.isAuto()) return 0;
        int cols = sv.toColumns(renderContext);
        return Math.max(cols, 1);
    }

    /**
     * Consume the pending maxWidth value, resetting it to -1.
     */
    private int consumePendingMaxWidth() {
        int v = pendingMaxWidthColumns;
        pendingMaxWidthColumns = -1;
        return v;
    }

    /**
     * Consume the pending gap value, resetting it to -1.
     */
    private int consumePendingGap() {
        int v = pendingGapColumns;
        pendingGapColumns = -1;
        return Math.max(v, 0);
    }

    /**
     * Compute available width for a new horizontal context.
     * Subtracts parent border/padding from viewport width.
     */
    private int computeAvailableWidth() {
        int available = (int) renderContext.viewportWidth();
        // Account for indent
        available -= indentLevel * INDENT.length();
        // Account for parent bordered context's border and padding
        for (ContainerContext ctx : contextStack) {
            if (ctx.hasBorder()) {
                int borderCols = (ctx.border.left().isVisible() ? 1 : 0)
                        + (ctx.border.right().isVisible() ? 1 : 0);
                int padCols = 0;
                if (ctx.padding != null) {
                    padCols = ctx.padding[1] + ctx.padding[3]; // right + left
                }
                available -= borderCols + padCols;
                break; // Only the nearest bordered ancestor matters
            }
        }
        return Math.max(available, 10); // minimum sane width
    }

    /**
     * Parse CSS-like padding shorthand into [top, right, bottom, left].
     *
     * <p>Follows CSS rules:
     * <ul>
     *   <li>1 value: all sides</li>
     *   <li>2 values: top/bottom, left/right</li>
     *   <li>3 values: top, left/right, bottom</li>
     *   <li>4 values: top, right, bottom, left</li>
     * </ul>
     */
    private int[] parsePadding(String padding) {
        if (padding == null || padding.isEmpty()) {
            return new int[]{0, 0, 0, 0};
        }

        String[] parts = padding.trim().split("\\s+");
        int[] result = new int[4];

        if (parts.length == 1) {
            int v = parsePaddingValue(parts[0]);
            Arrays.fill(result, v);
        } else if (parts.length == 2) {
            int tb = parsePaddingValue(parts[0]);
            int lr = parsePaddingValue(parts[1]);
            result[0] = tb; result[1] = lr; result[2] = tb; result[3] = lr;
        } else if (parts.length == 3) {
            result[0] = parsePaddingValue(parts[0]);
            result[1] = parsePaddingValue(parts[1]);
            result[2] = parsePaddingValue(parts[2]);
            result[3] = result[1];
        } else if (parts.length >= 4) {
            result[0] = parsePaddingValue(parts[0]);
            result[1] = parsePaddingValue(parts[1]);
            result[2] = parsePaddingValue(parts[2]);
            result[3] = parsePaddingValue(parts[3]);
        }

        return result;
    }

    private int parsePaddingValue(String value) {
        SizeValue sv = SizeValue.parse(value);
        if (sv == null) return 0;
        // For padding, horizontal values use columns, vertical use rows
        // We'll use columns as default since most padding is horizontal-ish
        return sv.toColumns(renderContext);
    }

    private void markElementStart() {
        elementStartRow = currentRow;
        elementStartCol = currentCol;
    }

    private void createHitRegions() {
        if (!pendingEvents.isEmpty()) {
            String id = nextId;

            for (PendingEvent event : pendingEvents) {
                hitRegions.add(new HitRegion(
                        elementStartRow, elementStartCol,
                        currentRow, currentCol,
                        event.on, event.action, event.target, id
                ));
            }
            pendingEvents.clear();
        }
    }

    private void trackNewline() {
        currentRow++;
        currentCol = 0;
    }

    // ==================== Grid-Backed STACK ====================

    /** Maximum grid width for TUI shape rendering (columns). */
    private static final int MAX_GRID_WIDTH = 40;

    /**
     * Terminal character aspect ratio — chars are ~2x taller than wide.
     * Used to convert between cell coordinates and visual space.
     */
    private static final double CHAR_ASPECT = 2.0;

    /**
     * Create a grid-backed STACK context for shape rendering.
     *
     * <p>When a STACK container has a pending background shape (like a circle),
     * we create a CharGrid and render the background shape into it. Children
     * (tick marks, hands) then draw into this same grid via radial lines.
     */
    private ContainerContext createGridStackContext(Selector selector, StyleProperties styles,
                                                    String width, String height) {
        // Determine grid width from context:
        // 1. Parent column constraint (if inside horizontal layout)
        // 2. Explicit width spec on the container
        // 3. Fall back to available viewport width, capped at MAX_GRID_WIDTH
        int gridW = -1;

        // Check if parent is constraining our width (column layout)
        ContainerContext parentCtx = contextStack.peek();
        if (parentCtx != null && parentCtx.isCollectingColumns()) {
            int remaining = parentCtx.availableWidth - parentCtx.usedWidth;
            if (parentCtx.usedWidth > 0) remaining -= parentCtx.gapColumns;
            if (remaining > 0) gridW = remaining;
        }
        if (parentCtx != null && parentCtx.isBuffered() && parentCtx.explicitWidth > 0) {
            gridW = parentCtx.explicitWidth;
        }

        // Explicit width spec on this container
        if (gridW < 0 && width != null && !width.isEmpty()) {
            SizeValue sv = SizeValue.parse(width);
            if (sv != null && !sv.isAuto()) {
                gridW = sv.toColumns(renderContext);
            }
        }

        // Fallback to available viewport width
        if (gridW < 0) {
            gridW = computeAvailableWidth();
        }

        gridW = Math.min(gridW, MAX_GRID_WIDTH);

        // For circles, gridH = gridW / 2 for visually square aspect
        // (each cell row is ~2x taller than wide)
        int gridH;
        if ("circle".equals(pendingShapeType)) {
            gridH = gridW / 2;
        } else {
            gridH = gridW / 2;
        }

        // Minimum size check
        if (gridW < 10 || gridH < 5) {
            ContainerContext ctx = new ContainerContext(Scene.Direction.STACK, selector, styles);
            clearPendingShapeState();
            return ctx;
        }

        CharGrid grid = new CharGrid(gridW, gridH);

        // Draw the background shape
        String strokeColor = colorToAnsi(pendingShapeStroke != null ? pendingShapeStroke : "#CDD6F4");
        String fillColor = pendingShapeFill != null ? colorToAnsi(pendingShapeFill) : null;

        double cx = gridW / 2.0;
        double cy = gridH / 2.0;
        // Ellipse radii in cell coordinates — rx in columns, ry in rows
        double rx = cx - 1;
        double ry = cy - 1;

        if ("circle".equals(pendingShapeType)) {
            if (fillColor != null) {
                grid.fillEllipse(cx, cy, rx, ry, ' ', fillColor);
            }
            grid.drawEllipse(cx, cy, rx, ry, strokeColor);
        } else if ("rectangle".equals(pendingShapeType)) {
            for (int x = 0; x < gridW; x++) {
                grid.set(x, 0, '─', strokeColor);
                grid.set(x, gridH - 1, '─', strokeColor);
            }
            for (int y = 0; y < gridH; y++) {
                grid.set(0, y, '│', strokeColor);
                grid.set(gridW - 1, y, '│', strokeColor);
            }
            grid.set(0, 0, '┌', strokeColor);
            grid.set(gridW - 1, 0, '┐', strokeColor);
            grid.set(0, gridH - 1, '└', strokeColor);
            grid.set(gridW - 1, gridH - 1, '┘', strokeColor);
        }

        ContainerContext ctx = new ContainerContext(Scene.Direction.STACK, selector, styles);
        ctx.grid = grid;
        ctx.widthSpec = width;
        ctx.heightSpec = height;
        clearPendingShapeState();
        return ctx;
    }

    /**
     * Find the nearest grid in the context stack.
     * Returns null if no ancestor has a grid.
     */
    private CharGrid findNearestGrid() {
        for (ContainerContext ctx : contextStack) {
            if (ctx.grid != null) return ctx.grid;
        }
        return null;
    }

    /**
     * Draw a shape into a grid context.
     *
     * <p>For shapes inside rotated containers within a grid-backed STACK,
     * this draws radial lines (clock hands/ticks) or filled circles
     * (center dot) into the grid.
     *
     * <p>Coordinate system: the grid is gridW columns × gridH rows.
     * Each cell is ~1 unit wide and ~2 units tall visually (CHAR_ASPECT = 2.0).
     * For a visually-square clock with gridH = gridW/2:
     * <ul>
     *   <li>Visual width = gridW</li>
     *   <li>Visual height = gridH × 2 = gridW (square!)</li>
     *   <li>Visual radius r — in column units (X cell units)</li>
     *   <li>Cell X = cx + sin(θ) × r</li>
     *   <li>Cell Y = cy - cos(θ) × r / CHAR_ASPECT</li>
     * </ul>
     */
    private void drawShapeIntoGrid(CharGrid grid, String shapeType, String fill, String stroke) {
        double cx = grid.width() / 2.0;
        double cy = grid.height() / 2.0;

        // Find the nearest container with rotation (walk up to grid boundary)
        double rotation = 0;
        float originY = 0.5f;
        boolean hasRotation = false;
        boolean isTick = false;

        for (ContainerContext ctx : contextStack) {
            if (ctx.containerRotation != 0 || ctx.transformOriginY != 0.5f) {
                rotation = ctx.containerRotation;
                originY = ctx.transformOriginY;
                hasRotation = true;
                // Detect tick vs hand by checking style classes on the selector
                if (ctx.elementSelector != null && ctx.elementSelector.classes().contains("tick")) {
                    isTick = true;
                }
                // Also detect by padding — ticks have top padding
                if (ctx.padding != null && ctx.padding[0] > 0) {
                    isTick = true;
                }
                break;
            }
            if (ctx.grid != null) break;
        }

        String color = colorToAnsi(fill != null ? fill : (stroke != null ? stroke : "#CDD6F4"));

        if ("circle".equals(shapeType)) {
            // Center dot
            grid.set((int) cx, (int) cy, '●', color);
            clearPendingShapeState();
            return;
        }

        if ("rectangle".equals(shapeType) && hasRotation && originY > 0.8f) {
            // Radial line (clock hand or tick mark)
            // Visual radius = half the grid width (in column units)
            double visualRadius = cx - 1;

            // Shape height as fraction of container → fraction of radius
            double shapeHeightFrac = parsePercentageFraction(pendingShapeHeight, 0.1);
            // Container height = 50% of STACK → the shape occupies shapeHeightFrac of that half
            double lineLen = visualRadius * shapeHeightFrac;

            // Ticks: start near the edge. Hands: start from center.
            double rStart, rEnd;
            if (isTick) {
                rEnd = visualRadius - 1;
                rStart = rEnd - lineLen;
                if (rStart < 0) rStart = 0;
            } else {
                // Hand: start from center, extend outward by its length
                rStart = 1;
                rEnd = rStart + lineLen;
                if (rEnd > visualRadius - 1) rEnd = visualRadius - 1;
            }

            // Angle: 0° = 12 o'clock, clockwise
            double angleRad = Math.toRadians(rotation);
            double sinA = Math.sin(angleRad);
            double cosA = Math.cos(angleRad);

            // Convert visual-space radial coords to cell coords
            // X: 1 visual unit = 1 cell column
            // Y: 1 visual unit = 0.5 cell rows (divide by CHAR_ASPECT)
            double x0 = cx + sinA * rStart;
            double y0 = cy - cosA * rStart / CHAR_ASPECT;
            double x1 = cx + sinA * rEnd;
            double y1 = cy - cosA * rEnd / CHAR_ASPECT;

            // Thick line for wider hands (width > 1.5%)
            double shapeWidthFrac = parsePercentageFraction(pendingShapeWidth, 0.006);
            if (shapeWidthFrac > 0.015) {
                grid.drawThickLine(x0, y0, x1, y1, color);
            } else {
                grid.drawLine(x0, y0, x1, y1, color);
            }
        }

        clearPendingShapeState();
    }

    /**
     * Parse a percentage spec like "10%" against a total dimension.
     * Returns the absolute value.
     */
    private double parsePercentage(String spec, double total) {
        if (spec == null || spec.isEmpty()) return 0;
        SizeValue sv = SizeValue.parse(spec);
        if (sv == null) return 0;
        if (sv.isPercentage()) return sv.value() * total / 100.0;
        return sv.toColumns(renderContext);
    }

    /**
     * Parse a percentage spec like "10%" returning the fraction (0.1).
     * Falls back to the given default if spec is null/empty.
     */
    private double parsePercentageFraction(String spec, double defaultFrac) {
        if (spec == null || spec.isEmpty()) return defaultFrac;
        SizeValue sv = SizeValue.parse(spec);
        if (sv == null) return defaultFrac;
        if (sv.isPercentage()) return sv.value() / 100.0;
        return defaultFrac;
    }

    // ==================== Inline Shape Fallback ====================

    /**
     * Render a standalone shape as inline text when not inside a grid.
     * Called for shapes that were not consumed by beginBox.
     */
    private void renderInlineShape(String type, String fill, String stroke) {
        StringBuilder buf = activeBuffer();
        String color = null;
        if (fill != null) color = colorToAnsi(fill);
        else if (stroke != null) color = colorToAnsi(stroke);

        String ch = switch (type != null ? type : "") {
            case "circle", "sphere" -> fill != null ? "●" : "○";
            case "ellipse" -> fill != null ? "●" : "◯";
            case "rectangle", "plane" -> "■";
            case "line" -> "─";
            case "point" -> "•";
            case "polygon" -> "⬡";
            case "cone" -> "▲";
            case "capsule" -> "⬮";
            case "path" -> "~";
            default -> "";
        };

        if (!ch.isEmpty()) {
            appendIndent(buf);
            if (color != null) buf.append(color);
            buf.append(ch);
            if (color != null) buf.append(ANSI.RESET);
            buf.append("\n");
            trackNewline();
        }
    }

    // ==================== ANSI Constants ====================

    private static class ANSI {
        static final String RESET = "\u001B[0m";
        static final String BOLD = "\u001B[1m";
        static final String DIM = "\u001B[2m";
        static final String UNDERLINE = "\u001B[4m";
        static final String REVERSE = "\u001B[7m";
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String MAGENTA = "\u001B[35m";
        static final String CYAN = "\u001B[36m";
        static final String WHITE = "\u001B[37m";
        static final String BG_RED = "\u001B[41m";
        static final String BG_GREEN = "\u001B[42m";
        static final String BG_YELLOW = "\u001B[43m";
        static final String BG_BLUE = "\u001B[44m";
        static final String BG_MAGENTA = "\u001B[45m";
        static final String BG_CYAN = "\u001B[46m";
        static final String BG_WHITE = "\u001B[47m";
        static final String BG_GRAY = "\u001B[100m";
    }
}
