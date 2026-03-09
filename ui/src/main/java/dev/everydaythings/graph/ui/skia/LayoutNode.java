package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.paragraph.Paragraph;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.TransitionSpec;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout tree node for the Skia renderer.
 *
 * <p>Sealed class hierarchy with common bounds, styling, rotation, and event
 * fields in the base. Four node types:
 * <ul>
 *   <li>{@link BoxNode} — container with direction and children</li>
 *   <li>{@link TextNode} — text content</li>
 *   <li>{@link ShapeNode} — vector shape primitive</li>
 *   <li>{@link ImageNode} — image/emoji fallback</li>
 * </ul>
 */
public sealed abstract class LayoutNode
        permits LayoutNode.BoxNode, LayoutNode.TextNode,
                LayoutNode.ShapeNode, LayoutNode.ImageNode {

    // Computed bounds (set by LayoutEngine)
    private float x, y, width, height;

    // Style classes from the SurfaceRenderer call
    private final List<String> styles;

    // Element type and ID
    private String type;
    private String id;

    // Events attached to this node
    private final List<PendingEvent> events;

    // Resolved style values (set by StyleResolver, consumed by painters)
    private int color = -1;            // ARGB text color (-1 = use renderer default)
    private int backgroundColor = -1;  // ARGB background color (-1 = transparent)
    private float fontSize;            // resolved font size in px (0 = use base font size)
    private String fontFamily;         // "monospace" or null (= proportional)
    private boolean bold;

    // Rotation in degrees (0 = no rotation, clockwise)
    private float rotation;

    // Transform origin for rotation pivot (0.0 = left/top, 0.5 = center, 1.0 = right/bottom)
    private float transformOriginX = 0.5f;
    private float transformOriginY = 0.5f;

    protected LayoutNode(List<String> styles) {
        this.styles = styles != null ? new ArrayList<>(styles) : new ArrayList<>();
        this.events = new ArrayList<>();
    }

    // Bounds
    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }
    public void setBounds(float x, float y, float width, float height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    // Styles
    public List<String> styles() { return styles; }

    // Type and ID
    public String type() { return type; }
    public void type(String type) { this.type = type; }
    public String id() { return id; }
    public void id(String id) { this.id = id; }

    // Events
    public List<PendingEvent> events() { return events; }

    // Rotation
    public float rotation() { return rotation; }
    public void rotation(float rotation) { this.rotation = rotation; }

    // Transform origin
    public float transformOriginX() { return transformOriginX; }
    public void transformOriginX(float x) { this.transformOriginX = x; }
    public float transformOriginY() { return transformOriginY; }
    public void transformOriginY(float y) { this.transformOriginY = y; }

    // Resolved style values
    public int color() { return color; }
    public void color(int color) { this.color = color; }
    public int backgroundColor() { return backgroundColor; }
    public void backgroundColor(int bg) { this.backgroundColor = bg; }
    public float fontSize() { return fontSize; }
    public void fontSize(float size) { this.fontSize = size; }
    public String fontFamily() { return fontFamily; }
    public void fontFamily(String family) { this.fontFamily = family; }
    public boolean bold() { return bold; }
    public void bold(boolean bold) { this.bold = bold; }

    // Display: hidden (node measures as 0×0, not painted)
    private boolean hidden;
    public boolean hidden() { return hidden; }
    public void hidden(boolean hidden) { this.hidden = hidden; }

    // ==================================================================================
    // Event record
    // ==================================================================================

    public record PendingEvent(String on, String action, String target) {}

    // ==================================================================================
    // Hit testing
    // ==================================================================================

    public static PendingEvent hitTest(LayoutNode root, float x, float y, String eventType) {
        if (root instanceof BoxNode box) {
            return hitTestBox(box, x, y, eventType);
        }
        return hitTestLeaf(root, x, y, eventType);
    }

    private static PendingEvent hitTestBox(BoxNode box, float x, float y, String eventType) {
        if (x < box.x() || x > box.x() + box.width() ||
            y < box.y() || y > box.y() + box.height()) {
            return null;
        }
        // Check overlays first (scrollbar shapes sit on top)
        for (int i = box.overlays().size() - 1; i >= 0; i--) {
            PendingEvent found = hitTest(box.overlays().get(i), x, y, eventType);
            if (found != null) return found;
        }
        // Adjust Y for scroll offset when hit-testing children
        float testY = y;
        if (box.isScrollContainer()) {
            testY = y + box.scrollOffsetY();
        }
        List<LayoutNode> children = box.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            PendingEvent found = hitTest(children.get(i), x, testY, eventType);
            if (found != null) return found;
        }
        for (PendingEvent ev : box.events()) {
            if (ev.on().equals(eventType)) {
                return ev;
            }
        }
        return null;
    }

    private static PendingEvent hitTestLeaf(LayoutNode node, float x, float y, String eventType) {
        if (x < node.x() || x > node.x() + node.width() ||
            y < node.y() || y > node.y() + node.height()) {
            return null;
        }
        for (PendingEvent ev : node.events()) {
            if (ev.on().equals(eventType)) {
                return ev;
            }
        }
        return null;
    }

    // ==================================================================================
    // Box Node — container with direction and children
    // ==================================================================================

    public static final class BoxNode extends LayoutNode {
        private final Scene.Direction direction;
        private final List<LayoutNode> children = new ArrayList<>();

        // Transition specs (from @Transition annotations)
        private List<TransitionSpec> transitions = List.of();

        // Visual properties (from extended beginBox)
        private BoxBorder border;
        private String background;
        private float explicitWidth = -1;
        private float explicitHeight = -1;

        // Raw size specs (set by renderer, resolved by LayoutEngine)
        private String widthSpec;
        private String heightSpec;
        private String fontSizeSpec;
        private String maxWidthSpec;
        private String gapSpec;
        private float gap;

        private String paddingSpec;
        private float paddingTop, paddingRight, paddingBottom, paddingLeft;
        private float borderTopWidth, borderRightWidth, borderBottomWidth, borderLeftWidth;
        private float borderRadius;

        // Aspect ratio (width/height): parsed from "1", "16/9", "4/3"
        private float aspectRatio;

        // Container shape type (from @Surface.Shape on container)
        private String shapeType;

        // 3D elevation hint
        private double elevation;
        private boolean elevated;
        private boolean elevationSolid = true;

        // Scroll container fields
        private String overflow = "visible";
        private String maxHeightSpec;
        private float contentHeight;        // total child height (set by LayoutEngine)
        private float scrollOffsetY;        // current offset (set from ScrollState)
        private boolean overflowsY;         // contentHeight > viewport

        // Overlay nodes (scrollbar shapes) — painted after children, outside scroll offset
        private final List<LayoutNode> overlays = new ArrayList<>();

        public BoxNode(Scene.Direction direction, List<String> styles) {
            super(styles);
            this.direction = direction;
        }

        public Scene.Direction direction() { return direction; }
        public List<LayoutNode> children() { return children; }
        public void addChild(LayoutNode child) { children.add(child); }

        // Aspect ratio
        public float aspectRatio() { return aspectRatio; }
        public void aspectRatio(float ratio) { this.aspectRatio = ratio; }

        // Container shape type
        public String shapeType() { return shapeType; }
        public void shapeType(String shapeType) { this.shapeType = shapeType; }

        // Visual properties
        public BoxBorder border() { return border; }
        public void border(BoxBorder border) { this.border = border; }
        public String background() { return background; }
        public void background(String background) { this.background = background; }
        public float explicitWidth() { return explicitWidth; }
        public void explicitWidth(float w) { this.explicitWidth = w; }
        public float explicitHeight() { return explicitHeight; }
        public void explicitHeight(float h) { this.explicitHeight = h; }

        // Raw size specs
        public String widthSpec() { return widthSpec; }
        public void widthSpec(String spec) { this.widthSpec = spec; }
        public String heightSpec() { return heightSpec; }
        public void heightSpec(String spec) { this.heightSpec = spec; }
        public String fontSizeSpec() { return fontSizeSpec; }
        public void fontSizeSpec(String spec) { this.fontSizeSpec = spec; }
        public String maxWidthSpec() { return maxWidthSpec; }
        public void maxWidthSpec(String spec) { this.maxWidthSpec = spec; }
        public String gapSpec() { return gapSpec; }
        public void gapSpec(String spec) { this.gapSpec = spec; }
        public float gap() { return gap; }
        public void gap(float gap) { this.gap = gap; }

        // Padding
        public String paddingSpec() { return paddingSpec; }
        public void paddingSpec(String spec) { this.paddingSpec = spec; }
        public float paddingTop() { return paddingTop; }
        public float paddingRight() { return paddingRight; }
        public float paddingBottom() { return paddingBottom; }
        public float paddingLeft() { return paddingLeft; }
        public void padding(float top, float right, float bottom, float left) {
            this.paddingTop = top; this.paddingRight = right;
            this.paddingBottom = bottom; this.paddingLeft = left;
        }

        // Border (resolved pixel widths)
        public float borderTopWidth() { return borderTopWidth; }
        public float borderRightWidth() { return borderRightWidth; }
        public float borderBottomWidth() { return borderBottomWidth; }
        public float borderLeftWidth() { return borderLeftWidth; }
        public float borderRadius() { return borderRadius; }
        public void borderWidths(float top, float right, float bottom, float left) {
            this.borderTopWidth = top; this.borderRightWidth = right;
            this.borderBottomWidth = bottom; this.borderLeftWidth = left;
        }
        public void borderRadius(float r) { this.borderRadius = r; }

        // Elevation
        public double elevation() { return elevation; }
        public void elevation(double e) { this.elevation = e; this.elevated = e != 0.0; }
        public boolean isElevated() { return elevated; }
        public boolean elevationSolid() { return elevationSolid; }
        public void elevationSolid(boolean s) { this.elevationSolid = s; }

        // Scroll container
        public String overflow() { return overflow; }
        public void overflow(String overflow) { this.overflow = overflow; }
        public String maxHeightSpec() { return maxHeightSpec; }
        public void maxHeightSpec(String spec) { this.maxHeightSpec = spec; }
        public float contentHeight() { return contentHeight; }
        public void contentHeight(float h) { this.contentHeight = h; }
        public float scrollOffsetY() { return scrollOffsetY; }
        public void scrollOffsetY(float offset) { this.scrollOffsetY = offset; }
        public boolean overflowsY() { return overflowsY; }
        public void overflowsY(boolean v) { this.overflowsY = v; }
        public List<LayoutNode> overlays() { return overlays; }

        public boolean isScrollContainer() {
            return "scroll".equals(overflow) || ("auto".equals(overflow) && overflowsY);
        }

        // Transitions
        public List<TransitionSpec> transitions() { return transitions; }
        public void transitions(List<TransitionSpec> transitions) { this.transitions = transitions; }
    }

    // ==================================================================================
    // Text Node — text content
    // ==================================================================================

    public static final class TextNode extends LayoutNode {
        private final String content;
        private final String format;

        // Font size spec from @Scene.Text(fontSize = "80%") — resolved by LayoutEngine
        private String fontSizeSpec;

        // Rich text spans (null = plain text)
        private List<TextSpan> spans;

        // Laid-out paragraph (set by measurer, reused by painter)
        private Paragraph paragraph;

        // Measured text size (set by layout engine via text measurer)
        private float measuredWidth;
        private float measuredHeight;

        public TextNode(String content, List<String> styles) {
            this(content, null, styles);
        }

        public TextNode(String content, String format, List<String> styles) {
            super(styles);
            this.content = content != null ? content : "";
            this.format = format;
        }

        public String content() { return content; }
        public String format() { return format; }

        public String fontSizeSpec() { return fontSizeSpec; }
        public void fontSizeSpec(String spec) { this.fontSizeSpec = spec; }

        public List<TextSpan> spans() { return spans; }
        public void spans(List<TextSpan> spans) { this.spans = spans; }

        public Paragraph paragraph() { return paragraph; }
        public void paragraph(Paragraph paragraph) { this.paragraph = paragraph; }

        public float measuredWidth() { return measuredWidth; }
        public float measuredHeight() { return measuredHeight; }
        public void measuredSize(float w, float h) { this.measuredWidth = w; this.measuredHeight = h; }
    }

    // ==================================================================================
    // Shape Node — vector shape primitive
    // ==================================================================================

    public static final class ShapeNode extends LayoutNode {
        private final String shapeType;     // "rectangle", "circle", "line", "path", "point", "ellipse", "polygon", etc.
        private final String cornerRadius;
        private final String fill;
        private final String stroke;
        private final String strokeWidth;
        private final String path;          // SVG path data or polygon sides (for type = "path"/"polygon"/"line" endpoints)

        // Raw size specs (set by renderer, resolved by LayoutEngine for % units)
        private String widthSpec;
        private String heightSpec;

        // Optional explicit sizing
        private float explicitWidth = -1;
        private float explicitHeight = -1;

        // 3D transform (set when @Scene.Transform is applied to a shape)
        private float transform3dX;
        private float transform3dY;
        private float transform3dZ;
        private float transform3dYaw;
        private float transform3dScaleX = 1;
        private float transform3dScaleY = 1;
        private float transform3dScaleZ = 1;
        private boolean hasTransform3d;

        public ShapeNode(String shapeType, String cornerRadius, String fill,
                         String stroke, String strokeWidth, String path,
                         List<String> styles) {
            super(styles);
            this.shapeType = shapeType != null ? shapeType : "rectangle";
            this.cornerRadius = cornerRadius != null ? cornerRadius : "";
            this.fill = fill != null ? fill : "";
            this.stroke = stroke != null ? stroke : "";
            this.strokeWidth = strokeWidth != null ? strokeWidth : "";
            this.path = path != null ? path : "";
        }

        public String shapeType() { return shapeType; }
        public String cornerRadius() { return cornerRadius; }
        public String fill() { return fill; }
        public String stroke() { return stroke; }
        public String strokeWidth() { return strokeWidth; }
        public String path() { return path; }

        public String widthSpec() { return widthSpec; }
        public void widthSpec(String spec) { this.widthSpec = spec; }
        public String heightSpec() { return heightSpec; }
        public void heightSpec(String spec) { this.heightSpec = spec; }

        public float explicitWidth() { return explicitWidth; }
        public void explicitWidth(float w) { this.explicitWidth = w; }
        public float explicitHeight() { return explicitHeight; }
        public void explicitHeight(float h) { this.explicitHeight = h; }

        // 3D transform accessors
        public boolean hasTransform3d() { return hasTransform3d; }
        public float transform3dX() { return transform3dX; }
        public float transform3dY() { return transform3dY; }
        public float transform3dZ() { return transform3dZ; }
        public float transform3dYaw() { return transform3dYaw; }
        public float transform3dScaleX() { return transform3dScaleX; }
        public float transform3dScaleY() { return transform3dScaleY; }
        public float transform3dScaleZ() { return transform3dScaleZ; }

        public void setTransform3d(float x, float y, float z, float yaw,
                                   float scaleX, float scaleY, float scaleZ) {
            this.transform3dX = x;
            this.transform3dY = y;
            this.transform3dZ = z;
            this.transform3dYaw = yaw;
            this.transform3dScaleX = scaleX;
            this.transform3dScaleY = scaleY;
            this.transform3dScaleZ = scaleZ;
            this.hasTransform3d = true;
        }
    }

    // ==================================================================================
    // Image Node — image/emoji fallback
    // ==================================================================================

    public static final class ImageNode extends LayoutNode {
        private final String alt;
        private final String resource;
        private final String size;
        private final String fit;

        // Shape decoration (circle, rounded-rect)
        private String shape;
        private String shapeBackground;

        // 3D model hint (for elevated rendering)
        private String modelResource;
        private int modelColor = -1;

        // Foreground tint for glyph/emoji (0 = use renderer default)
        private int tintColor = 0;

        public ImageNode(String alt, String resource, String size, String fit, List<String> styles) {
            super(styles);
            this.alt = alt != null ? alt : "?";
            this.resource = resource;
            this.size = size;
            this.fit = fit;
        }

        public ImageNode(String alt, String size, String fit, List<String> styles) {
            this(alt, null, size, fit, styles);
        }

        public String alt() { return alt; }
        public String resource() { return resource; }
        public boolean hasResource() { return resource != null && !resource.isEmpty(); }
        public String size() { return size; }
        public String fit() { return fit; }

        public String shape() { return shape; }
        public void shape(String shape) { this.shape = shape; }
        public String shapeBackground() { return shapeBackground; }
        public void shapeBackground(String color) { this.shapeBackground = color; }

        public String modelResource() { return modelResource; }
        public void modelResource(String modelResource) { this.modelResource = modelResource; }
        public int modelColor() { return modelColor; }
        public void modelColor(int color) { this.modelColor = color; }
        public boolean hasModelResource() { return modelResource != null && !modelResource.isEmpty(); }

        public int tintColor() { return tintColor; }
        public void tintColor(int color) { this.tintColor = color; }
        public boolean hasTintColor() { return tintColor != 0; }
    }
}
