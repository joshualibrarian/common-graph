package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single node type for all rendering pipeline stages.
 *
 * <p>A SceneNode tree is the universal representation at every stage of the
 * pipeline. The same tree is mutated in place as it progresses:
 * <ol>
 *   <li><b>Declared</b> — built from annotations, CBOR, or programmatic API</li>
 *   <li><b>Resolved</b> — bindings evaluated, styles cascaded, repeats expanded
 *       (by {@link SceneResolver})</li>
 *   <li><b>Laid out</b> — units resolved to pixels, bounds computed
 *       (by ScenePresenter)</li>
 *   <li><b>Painted</b> — traversed for output (by {@link ScenePainter})</li>
 * </ol>
 *
 * <p>Replaces the previous {@code Node} (sealed: Container, Text, Body) and
 * {@code LayoutNode} (sealed: BoxNode, TextNode, ShapeNode, ImageNode)
 * hierarchies with a single mutable type.
 *
 * <p>All nodes are CBOR-serializable for wire transfer between librarian
 * and window. The resolved (but not laid out) tree is the wire format.
 *
 * @see SceneResolver
 * @see ScenePainter
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization
public class SceneNode implements Canonical {

    // =================================================================================
    // Node Type
    // =================================================================================

    public enum NodeType {
        /** Structural node with children and layout. */
        CONTAINER,
        /** Text content — literal or semantic tokens. */
        TEXT,
        /** Visual content — shape, image, model, glyph with fallback chain. */
        BODY
    }

    @Canon(order = 0)
    private NodeType type;

    // =================================================================================
    // Identity
    // =================================================================================

    @Canon(order = 1)
    private String id;

    @Getter(lombok.AccessLevel.NONE)
    @Canon(order = 2)
    private List<String> classes;

    // =================================================================================
    // Box Model
    // =================================================================================

    @Canon(order = 10)
    private String width;

    @Canon(order = 11)
    private String height;

    @Canon(order = 12)
    private String margin;

    @Canon(order = 13)
    private String padding;

    @Canon(order = 14)
    private String border;

    /** Corner radius — String "4px" → Float 4.0f after presentation. */
    @Canon(order = 15)
    private Object corner;

    /** Background — String "#1E1E2E" → Integer 0xFF1E1E2E after presentation. */
    @Canon(order = 16)
    private Object background;

    @Canon(order = 17)
    private String overflow;

    // =================================================================================
    // Typography (Object fields — progressively mutated by pipeline)
    // =================================================================================

    @Canon(order = 20)
    private String fontFamily;

    /** Font size — String "1.2em" → Float 18.0f after presentation. */
    @Canon(order = 21)
    private Object fontSize;

    @Canon(order = 22)
    private String fontWeight;

    /** Foreground color — String "#CDD6F4" → Integer 0xFFCDD6F4 after presentation. */
    @Canon(order = 23)
    private Object foreground;

    /** Opacity — String "0.8" → Float 0.8f after presentation. */
    @Canon(order = 24)
    private Object opacity;

    // =================================================================================
    // Transform
    // =================================================================================

    /** Rotation — String "45deg" → Float 45.0f after presentation. */
    @Canon(order = 30)
    private Object rotation;

    @Canon(order = 31)
    private double scale = 1.0;

    @Canon(order = 32)
    private double elevation;

    @Canon(order = 33)
    private double posX;

    @Canon(order = 34)
    private double posY;

    @Canon(order = 35)
    private double posZ;

    // =================================================================================
    // Interaction
    // =================================================================================

    @Canon(order = 40)
    private List<SceneEvent> events;

    @Canon(order = 41)
    private boolean capturesFocus;

    @Canon(order = 42)
    private String cursor;

    @Canon(order = 43)
    private boolean editable;

    // =================================================================================
    // Data Binding
    // =================================================================================

    @Canon(order = 50)
    private String bind;

    @Canon(order = 51)
    private String visible;

    // =================================================================================
    // State Declarations
    // =================================================================================

    @Canon(order = 60)
    private List<StateDecl> state;

    /** A state declaration — the runtime holds the actual value. */
    @Canonical.Canonization
    public record StateDecl(
            @Canon(order = 0) String key,
            @Canon(order = 1) String defaultValue
    ) implements Canonical {}

    /** A semantic token — a sememe reference with grammatical features. */
    @Canonical.Canonization
    public record SemanticToken(
            @Canon(order = 0) ItemID sememe,
            @Canon(order = 1) List<ItemID> features
    ) implements Canonical {
        public static SemanticToken of(ItemID sememe) {
            return new SemanticToken(sememe, List.of());
        }
        public static SemanticToken of(ItemID sememe, ItemID... features) {
            return new SemanticToken(sememe, List.of(features));
        }
    }

    // =================================================================================
    // Conditional Property Blocks
    // =================================================================================

    @Canon(order = 70)
    private Map<String, Map<String, String>> when;

    // =================================================================================
    // Container Properties (type == CONTAINER)
    // =================================================================================

    @Canon(order = 100)
    private String layout;

    /** Gap — String "0.5em" → Float 7.5f after presentation. */
    @Canon(order = 101)
    private Object gap;

    @Canon(order = 102)
    private String align;

    @Canon(order = 103)
    private String justify;

    @Canon(order = 104)
    private boolean wrap;

    @Canon(order = 105)
    private int columns;

    @Canon(order = 106)
    private int rows;

    @Canon(order = 107)
    private float aspectRatio;

    @Canon(order = 110)
    private List<SceneNode> children;

    @Canon(order = 111)
    private String repeat;

    @Canon(order = 112)
    private SceneNode childTemplate;

    // =================================================================================
    // Text Properties (type == TEXT)
    // =================================================================================

    @Canon(order = 200)
    private String text;

    @Canon(order = 201)
    private ItemID format;

    @Canon(order = 210)
    private List<SemanticToken> tokens;

    // =================================================================================
    // Body Properties (type == BODY)
    // =================================================================================

    /** Geometric shape: "circle", "line", "rect", "sphere", "cone", "cylinder", "box". */
    @Canon(order = 300)
    private String shape;

    /** 2D image path or CID (SVG, PNG). */
    @Canon(order = 301)
    private String image;

    /** 3D model path or CID (GLB, GLTF). */
    @Canon(order = 302)
    private String model;

    /** Unicode glyph (single character or emoji). */
    @Canon(order = 303)
    private String glyph;

    /** Text description fallback. */
    @Canon(order = 304)
    private String alt;

    /** Fill color — String "#color" → Integer 0xFFcolor after presentation. */
    @Canon(order = 310)
    private Object fill;

    /** Stroke color — String "#color" → Integer 0xFFcolor after presentation. */
    @Canon(order = 311)
    private Object stroke;

    /** Stroke width for shapes. */
    @Canon(order = 312)
    private String strokeWidth;

    /** Radius for circle/sphere shapes. */
    @Canon(order = 313)
    private String radius;

    /** Material reference (PBR properties). */
    @Canon(order = 320)
    private String material;

    /** Named surfaces on this body's geometry (e.g., "front" → container for that face). */
    @Canon(order = 330)
    private Map<String, SceneNode> surfaces;

    // =================================================================================
    // Layout Internals (filled by ScenePresenter — not serialized)
    // =================================================================================

    // Final pixel bounds (computed by ScenePresenter)
    private transient float boundsX;
    private transient float boundsY;
    private transient float boundsWidth;
    private transient float boundsHeight;

    // Layout-internal intermediates (computed by ScenePresenter)
    private transient float explicitWidth = -1;
    private transient float explicitHeight = -1;
    private transient float paddingTop, paddingRight, paddingBottom, paddingLeft;
    private transient float borderTopWidth, borderRightWidth, borderBottomWidth, borderLeftWidth;
    private transient float measuredWidth;
    private transient float measuredHeight;
    private transient float contentHeight;
    private transient float scrollOffsetY;
    private transient boolean overflowsY;

    @Getter(lombok.AccessLevel.NONE)
    private transient boolean hidden;

    // =================================================================================
    // Factories
    // =================================================================================

    public static SceneNode container(String layout) {
        SceneNode n = new SceneNode();
        n.type = NodeType.CONTAINER;
        n.layout = layout;
        return n;
    }

    public static SceneNode vertical() { return container("vertical"); }
    public static SceneNode horizontal() { return container("horizontal"); }
    public static SceneNode stack() { return container("stack"); }

    public static SceneNode grid(int columns, int rows) {
        SceneNode n = container("grid");
        n.columns = columns;
        n.rows = rows;
        return n;
    }

    public static SceneNode ofText(String text) {
        SceneNode n = new SceneNode();
        n.type = NodeType.TEXT;
        n.text = text;
        return n;
    }

    public static SceneNode ofText(String text, ItemID format) {
        SceneNode n = new SceneNode();
        n.type = NodeType.TEXT;
        n.text = text;
        n.format = format;
        return n;
    }

    public static SceneNode ofSememe(ItemID sememe) {
        SceneNode n = new SceneNode();
        n.type = NodeType.TEXT;
        n.tokens = List.of(SemanticToken.of(sememe));
        n.on(SceneEvent.of("doubleClick", "view", sememe.encodeText()));
        return n;
    }

    public static SceneNode ofTokens(List<SemanticToken> tokens) {
        SceneNode n = new SceneNode();
        n.type = NodeType.TEXT;
        n.tokens = List.copyOf(tokens);
        return n;
    }

    public static SceneNode body() {
        SceneNode n = new SceneNode();
        n.type = NodeType.BODY;
        return n;
    }

    public static SceneNode ofGlyph(String glyph) {
        SceneNode n = body();
        n.glyph = glyph;
        return n;
    }

    public static SceneNode ofImage(String imagePath) {
        SceneNode n = body();
        n.image = imagePath;
        return n;
    }

    public static SceneNode ofModel(String modelPath) {
        SceneNode n = body();
        n.model = modelPath;
        return n;
    }

    public static SceneNode ofShape(String shape) {
        SceneNode n = body();
        n.shape = shape;
        return n;
    }

    // =================================================================================
    // Body Fidelity Resolution
    // =================================================================================

    public String bestFor3D() {
        if (model != null) return model;
        if (shape != null) return shape;
        if (image != null) return image;
        return glyph;
    }

    public String bestFor2D() {
        if (image != null) return image;
        if (shape != null) return shape;
        return glyph;
    }

    public String bestForText() {
        if (glyph != null) return glyph;
        return alt;
    }

    public boolean hasContent() {
        return shape != null || image != null || model != null || glyph != null;
    }

    // =================================================================================
    // Children
    // =================================================================================

    public List<String> classes() { return classes; }

    public SceneNode add(SceneNode child) {
        if (children == null) children = new ArrayList<>();
        children.add(child);
        return this;
    }

    public SceneNode add(SceneNode... nodes) {
        if (children == null) children = new ArrayList<>();
        for (SceneNode n : nodes) children.add(n);
        return this;
    }

    // =================================================================================
    // Events
    // =================================================================================

    public SceneNode on(SceneEvent event) {
        if (events == null) events = new ArrayList<>();
        events.add(event);
        return this;
    }

    public SceneNode on(String event, String action) {
        if (events == null) events = new ArrayList<>();
        events.add(SceneEvent.of(event, action, ""));
        return this;
    }

    public SceneNode on(String event, String action, String target) {
        if (events == null) events = new ArrayList<>();
        events.add(SceneEvent.of(event, action, target));
        return this;
    }

    // =================================================================================
    // State
    // =================================================================================

    public SceneNode declareState(String key, String defaultValue) {
        if (state == null) state = new ArrayList<>();
        state.add(new StateDecl(key, defaultValue));
        return this;
    }

    public SceneNode when(String condition, String property, String value) {
        if (when == null) when = new LinkedHashMap<>();
        when.computeIfAbsent(condition, k -> new LinkedHashMap<>()).put(property, value);
        return this;
    }

    // =================================================================================
    // Bounds (filled by ScenePresenter)
    // =================================================================================

    public void setBounds(float x, float y, float w, float h) {
        this.boundsX = x;
        this.boundsY = y;
        this.boundsWidth = w;
        this.boundsHeight = h;
    }

    // =================================================================================
    // Progressive Mutation Helpers
    // =================================================================================

    /**
     * Read an Object field as a float (after pipeline resolution).
     * Returns defaultValue if the field isn't a Number.
     */
    public static float asFloat(Object field, float defaultValue) {
        if (field instanceof Number n) return n.floatValue();
        return defaultValue;
    }

    /**
     * Read an Object field as an int (after pipeline resolution).
     * Returns defaultValue if the field isn't a Number.
     */
    public static int asInt(Object field, int defaultValue) {
        if (field instanceof Number n) return n.intValue();
        return defaultValue;
    }

    /**
     * Read an Object field as a String (before resolution, or if it stayed a String).
     * Returns null if not a String.
     */
    public static String asString(Object field) {
        return field instanceof String s ? s : null;
    }

    // Convenience accessors using the generic helpers
    public float fontSizeFloat() { return asFloat(fontSize, 0); }
    public int foregroundColor() { return asInt(foreground, -1); }
    public int backgroundColor() { return asInt(background, -1); }
    public float cornerFloat() { return asFloat(corner, 0); }
    public float rotationFloat() { return asFloat(rotation, 0); }
    public float opacityFloat() { return asFloat(opacity, 1.0f); }
    public float gapFloat() { return asFloat(gap, 0); }
    public int fillColor() { return asInt(fill, -1); }
    public int strokeColor() { return asInt(stroke, -1); }

    /** Whether font weight is bold. */
    public boolean isBold() { return "bold".equals(fontWeight); }

    /** Get font size as declared string (before resolution). */
    public String fontSizeSpec() {
        return fontSize instanceof String s ? s : fontSize != null ? fontSize.toString() : null;
    }

    /** Get background as declared string (before resolution). */
    public String backgroundSpec() {
        return background instanceof String s ? s : null;
    }

    /** Get foreground as declared string (before resolution). */
    public String foregroundSpec() {
        return foreground instanceof String s ? s : null;
    }

    /** Get gap as declared string (before resolution). */
    public String gapSpec() {
        return gap instanceof String s ? s : null;
    }

    /** Get corner as declared string (before resolution). */
    public String cornerSpec() {
        return corner instanceof String s ? s : null;
    }

    /** Get rotation as declared string (before resolution). */
    public String rotationSpec() {
        return rotation instanceof String s ? s : null;
    }

    // =================================================================================
    // Layout Internal Accessors
    // =================================================================================

    public float explicitWidth() { return explicitWidth; }
    public void explicitWidth(float w) { this.explicitWidth = w; }
    public float explicitHeight() { return explicitHeight; }
    public void explicitHeight(float h) { this.explicitHeight = h; }

    public float paddingTop() { return paddingTop; }
    public float paddingRight() { return paddingRight; }
    public float paddingBottom() { return paddingBottom; }
    public float paddingLeft() { return paddingLeft; }
    public void padding(float top, float right, float bottom, float left) {
        this.paddingTop = top; this.paddingRight = right;
        this.paddingBottom = bottom; this.paddingLeft = left;
    }

    public float borderTopWidth() { return borderTopWidth; }
    public float borderRightWidth() { return borderRightWidth; }
    public float borderBottomWidth() { return borderBottomWidth; }
    public float borderLeftWidth() { return borderLeftWidth; }
    public void borderWidths(float top, float right, float bottom, float left) {
        this.borderTopWidth = top; this.borderRightWidth = right;
        this.borderBottomWidth = bottom; this.borderLeftWidth = left;
    }

    public float measuredWidth() { return measuredWidth; }
    public float measuredHeight() { return measuredHeight; }
    public void measuredSize(float w, float h) { this.measuredWidth = w; this.measuredHeight = h; }

    public float contentHeight() { return contentHeight; }
    public void contentHeight(float h) { this.contentHeight = h; }
    public float scrollOffsetY() { return scrollOffsetY; }
    public void scrollOffsetY(float offset) { this.scrollOffsetY = offset; }
    public boolean overflowsY() { return overflowsY; }
    public void overflowsY(boolean v) { this.overflowsY = v; }
    public boolean isScrollContainer() {
        return "scroll".equals(overflow) || ("auto".equals(overflow) && overflowsY);
    }

    public boolean hidden() { return hidden; }
    public void hidden(boolean h) { this.hidden = h; }

    public boolean isFillChild() {
        return (width != null && width.endsWith("fr"))
            || (height != null && height.endsWith("fr"));
    }

    // =================================================================================
    // Surfaces (Body faces)
    // =================================================================================

    public SceneNode surface(String name, SceneNode content) {
        if (surfaces == null) surfaces = new LinkedHashMap<>();
        surfaces.put(name, content);
        return this;
    }

    // =================================================================================
    // Fluent Setters
    // =================================================================================

    public SceneNode type(NodeType t) { this.type = t; return this; }
    public SceneNode id(String id) { this.id = id; return this; }
    public SceneNode classes(String... classes) { this.classes = List.of(classes); return this; }
    public SceneNode classes(List<String> classes) { this.classes = classes; return this; }
    public SceneNode width(String w) { this.width = w; return this; }
    public SceneNode height(String h) { this.height = h; return this; }
    public SceneNode margin(String m) { this.margin = m; return this; }
    public SceneNode padding(String p) { this.padding = p; return this; }
    public SceneNode border(String b) { this.border = b; return this; }
    public SceneNode corner(Object c) { this.corner = c; return this; }
    public SceneNode background(Object bg) { this.background = bg; return this; }
    public SceneNode overflow(String o) { this.overflow = o; return this; }
    public SceneNode fontFamily(String f) { this.fontFamily = f; return this; }
    public SceneNode fontSize(Object s) { this.fontSize = s; return this; }
    public SceneNode fontWeight(String w) { this.fontWeight = w; return this; }
    public SceneNode foreground(Object c) { this.foreground = c; return this; }
    public SceneNode opacity(Object o) { this.opacity = o; return this; }
    public SceneNode rotation(Object r) { this.rotation = r; return this; }
    public SceneNode scale(double s) { this.scale = s; return this; }
    public SceneNode elevation(double e) { this.elevation = e; return this; }
    public SceneNode position(double x, double y, double z) {
        this.posX = x; this.posY = y; this.posZ = z; return this;
    }
    public SceneNode cursor(String c) { this.cursor = c; return this; }
    public SceneNode capturesFocus(boolean cf) { this.capturesFocus = cf; return this; }
    public SceneNode editable(boolean e) { this.editable = e; return this; }
    public SceneNode bind(String expr) { this.bind = expr; return this; }
    public SceneNode visible(String expr) { this.visible = expr; return this; }
    public SceneNode layout(String l) { this.layout = l; return this; }
    public SceneNode gap(Object g) { this.gap = g; return this; }
    public SceneNode align(String a) { this.align = a; return this; }
    public SceneNode justify(String j) { this.justify = j; return this; }
    public SceneNode wrap(boolean w) { this.wrap = w; return this; }
    public SceneNode columns(int c) { this.columns = c; return this; }
    public SceneNode rows(int r) { this.rows = r; return this; }
    public SceneNode aspectRatio(float ratio) { this.aspectRatio = ratio; return this; }
    public SceneNode repeat(String expr) { this.repeat = expr; return this; }
    public SceneNode childTemplate(SceneNode template) { this.childTemplate = template; return this; }
    public SceneNode text(String t) { this.text = t; return this; }
    public SceneNode format(ItemID f) { this.format = f; return this; }
    public SceneNode tokens(List<SemanticToken> t) { this.tokens = t; return this; }
    public SceneNode shape(String s) { this.shape = s; return this; }
    public SceneNode image(String i) { this.image = i; return this; }
    public SceneNode model(String m) { this.model = m; return this; }
    public SceneNode glyph(String g) { this.glyph = g; return this; }
    public SceneNode alt(String a) { this.alt = a; return this; }
    public SceneNode fill(Object f) { this.fill = f; return this; }
    public SceneNode stroke(Object s) { this.stroke = s; return this; }
    public SceneNode strokeWidth(String w) { this.strokeWidth = w; return this; }
    public SceneNode radius(String r) { this.radius = r; return this; }
    public SceneNode material(String m) { this.material = m; return this; }

    // =================================================================================
    // Hit Testing
    // =================================================================================

    /**
     * Find the deepest node at the given pixel coordinates.
     * Requires bounds to be computed (post-presentation).
     */
    public static SceneNode hitTest(SceneNode tree, float x, float y) {
        if (tree == null) return null;
        if (x < tree.boundsX || x > tree.boundsX + tree.boundsWidth
                || y < tree.boundsY || y > tree.boundsY + tree.boundsHeight) {
            return null;
        }
        // Check children in reverse order (last child is on top)
        if (tree.children != null) {
            for (int i = tree.children.size() - 1; i >= 0; i--) {
                SceneNode hit = hitTest(tree.children.get(i), x, y);
                if (hit != null) return hit;
            }
        }
        return tree;
    }
}
