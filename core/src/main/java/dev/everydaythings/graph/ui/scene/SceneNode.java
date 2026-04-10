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
 * <p>All nodes are CBOR-serializable for wire transfer between librarian
 * and window. The resolved (but not laid out) tree is the wire format.
 * Serializes as a CBOR map with string keys — null/default fields are omitted.
 *
 * <h2>Field organization</h2>
 *
 * Top-level fields hold node identity, box-model geometry, and content. Visual
 * properties that group naturally are nested into seven inner classes:
 *
 * <ul>
 *   <li>{@link Border} — per-side width/style/color (12 fields)</li>
 *   <li>{@link Transform} — rotation, scale, origin, elevation, position</li>
 *   <li>{@link Typography} — font + text styling (11 fields)</li>
 *   <li>{@link Background} — color, image, size, gradient</li>
 *   <li>{@link Transition} — state-driven property animation (4 fields)</li>
 *   <li>{@link Animation} — keyframe timeline animation (8 fields)</li>
 *   <li>{@link Layout} — container flow + grid (8 fields)</li>
 * </ul>
 *
 * <p>Nested fields are addressed in the cascade as dotted keys
 * ({@code "border.topWidth"}, {@code "transform.rotationZ"},
 * {@code "typography.fontSize"}, {@code "layout.mode"}).
 * Each nested object is lazy-allocated on first mutation and stays {@code null}
 * for nodes that don't use it, keeping the wire format compact and the hot
 * paint path allocation-free.
 *
 * <p>Fluent setters on SceneNode itself ({@code node.fontSize("16px")},
 * {@code node.borderTopWidth("1px")}) act as delegators that lazy-create the
 * relevant nested object — callers don't need to navigate the nesting unless
 * they want to.
 *
 * @see SceneResolver
 * @see ScenePainter
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
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

    /** Min width constraint — String "200px" → Float 200.0f after presentation. */
    @Canon(order = 12)
    private Object minWidth;

    /** Max width constraint — String "80%" → Float after presentation. */
    @Canon(order = 13)
    private Object maxWidth;

    /** Min height constraint. */
    @Canon(order = 14)
    private Object minHeight;

    /** Max height constraint. */
    @Canon(order = 15)
    private Object maxHeight;

    @Canon(order = 16)
    private String margin;

    @Canon(order = 17)
    private String padding;

    /** Corner radius — String "4px" → Float 4.0f after presentation. */
    @Canon(order = 30)
    private Object corner;

    @Canon(order = 35)
    private String overflow;

    /** Opacity — String "0.8" → Float 0.8f after presentation. */
    @Canon(order = 51)
    private Object opacity;

    // =================================================================================
    // Nested Visual Groups
    // =================================================================================

    @Canon(order = 18)
    private Border border;

    @Canon(order = 31)
    private Background background;

    @Canon(order = 40)
    private Typography typography;

    @Canon(order = 60)
    private Transform transform;

    @Canon(order = 75)
    private Transition transition;

    @Canon(order = 79)
    private Animation animation;

    /** Container flow + grid layout. Custom getter returns the mode string for back-compat. */
    @Getter(lombok.AccessLevel.NONE)
    @Canon(order = 200)
    private Layout layout;

    // =================================================================================
    // Interaction
    // =================================================================================

    @Canon(order = 90)
    private List<SceneEvent> events;

    @Canon(order = 91)
    private boolean capturesFocus;

    @Canon(order = 92)
    private String cursor;

    @Canon(order = 93)
    private boolean editable;

    // =================================================================================
    // Data Binding
    // =================================================================================

    @Canon(order = 100)
    private String bind;

    /** Visibility — String expression → Boolean after resolution. */
    @Canon(order = 101)
    private Object visible;

    // =================================================================================
    // State Declarations
    // =================================================================================

    @Canon(order = 105)
    private List<StateDecl> state;

    /** Conditional property blocks: condition → (dotted-property → value). */
    @Canon(order = 106)
    private Map<String, Map<String, String>> when;

    // =================================================================================
    // Anchor Positioning (child-side, takes node out of parent flow)
    // =================================================================================

    @Canon(order = 208) private String anchorTop;
    @Canon(order = 209) private String anchorRight;
    @Canon(order = 210) private String anchorBottom;
    @Canon(order = 211) private String anchorLeft;

    // =================================================================================
    // Container Content (type == CONTAINER)
    // =================================================================================

    @Canon(order = 220)
    private List<SceneNode> children;

    @Canon(order = 221)
    private String repeat;

    @Canon(order = 222)
    private SceneNode childTemplate;

    // =================================================================================
    // Text Content (type == TEXT)
    // =================================================================================

    @Canon(order = 300)
    private String text;

    @Canon(order = 301)
    private ItemID format;

    @Canon(order = 302)
    private List<SemanticToken> tokens;

    // =================================================================================
    // Body Content (type == BODY)
    // =================================================================================

    /** Geometric shape: "circle", "line", "rect", "sphere", "cone", "cylinder", "box". */
    @Canon(order = 400)
    private String shape;

    /** 2D image path or CID (SVG, PNG, JPEG, WebP, GIF). */
    @Canon(order = 401)
    private String image;

    /** 3D model path or CID (GLB, GLTF). */
    @Canon(order = 402)
    private String model;

    /** Unicode glyph (single character or emoji). */
    @Canon(order = 403)
    private String glyph;

    /** Text description fallback. */
    @Canon(order = 404)
    private String alt;

    /** Fill color — String "#color" → Integer 0xFFcolor after presentation. */
    @Canon(order = 410)
    private Object fill;

    /** Stroke color — String "#color" → Integer 0xFFcolor after presentation. */
    @Canon(order = 411)
    private Object strokeColor;

    /** Stroke width — String "2px" → Float 2.0f after presentation. */
    @Canon(order = 412)
    private Object strokeWidth;

    /** Radius for circle/sphere shapes. */
    @Canon(order = 413)
    private String radius;

    /** SVG path data (the 'd' attribute) for shape="path". */
    @Canon(order = 414)
    private String pathData;

    /** Material reference (PBR properties). */
    @Canon(order = 420)
    private String material;

    /** Named surfaces on this body's geometry (e.g., "front" → container for that face). */
    @Canon(order = 430)
    private Map<String, SceneNode> surfaces;

    // =================================================================================
    // Layout Internals (filled by ScenePresenter — not serialized)
    // =================================================================================

    private transient float boundsX;
    private transient float boundsY;
    private transient float boundsWidth;
    private transient float boundsHeight;

    private transient float explicitWidth = -1;
    private transient float explicitHeight = -1;
    private transient float paddingTop, paddingRight, paddingBottom, paddingLeft;
    private transient float measuredWidth;
    private transient float measuredHeight;
    private transient float contentHeight;
    private transient float scrollOffsetY;
    private transient boolean overflowsY;

    @Getter(lombok.AccessLevel.NONE)
    private transient boolean hidden;

    // =================================================================================
    // Nested Inner Classes
    // =================================================================================

    /** Per-side border width, style, and color. Cascade prefix: {@code border.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Border implements Canonical {
        @Canon(order = 0)  private Object topWidth;
        @Canon(order = 1)  private String topStyle;
        @Canon(order = 2)  private Object topColor;
        @Canon(order = 3)  private Object rightWidth;
        @Canon(order = 4)  private String rightStyle;
        @Canon(order = 5)  private Object rightColor;
        @Canon(order = 6)  private Object bottomWidth;
        @Canon(order = 7)  private String bottomStyle;
        @Canon(order = 8)  private Object bottomColor;
        @Canon(order = 9)  private Object leftWidth;
        @Canon(order = 10) private String leftStyle;
        @Canon(order = 11) private Object leftColor;

        public Border() {}

        public Border topWidth(Object w)    { this.topWidth = w; return this; }
        public Border topStyle(String s)    { this.topStyle = s; return this; }
        public Border topColor(Object c)    { this.topColor = c; return this; }
        public Border rightWidth(Object w)  { this.rightWidth = w; return this; }
        public Border rightStyle(String s)  { this.rightStyle = s; return this; }
        public Border rightColor(Object c)  { this.rightColor = c; return this; }
        public Border bottomWidth(Object w) { this.bottomWidth = w; return this; }
        public Border bottomStyle(String s) { this.bottomStyle = s; return this; }
        public Border bottomColor(Object c) { this.bottomColor = c; return this; }
        public Border leftWidth(Object w)   { this.leftWidth = w; return this; }
        public Border leftStyle(String s)   { this.leftStyle = s; return this; }
        public Border leftColor(Object c)   { this.leftColor = c; return this; }

        public float topWidthFloat()    { return asFloat(topWidth, 0); }
        public float rightWidthFloat()  { return asFloat(rightWidth, 0); }
        public float bottomWidthFloat() { return asFloat(bottomWidth, 0); }
        public float leftWidthFloat()   { return asFloat(leftWidth, 0); }
        public int topColorInt()    { return asInt(topColor, -1); }
        public int rightColorInt()  { return asInt(rightColor, -1); }
        public int bottomColorInt() { return asInt(bottomColor, -1); }
        public int leftColorInt()   { return asInt(leftColor, -1); }
    }

    /** Per-axis rotation, scale, transform origin, elevation, position. Cascade prefix: {@code transform.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Transform implements Canonical {
        // Rotation per-axis — String "45deg" → Float after presentation
        @Canon(order = 0) private Object rotationX;
        @Canon(order = 1) private Object rotationY;
        @Canon(order = 2) private Object rotationZ;

        // Scale per-axis — String "1.5" → Float after presentation (default 1.0)
        @Canon(order = 3) private Object scaleX;
        @Canon(order = 4) private Object scaleY;
        @Canon(order = 5) private Object scaleZ;

        /** Transform origin — "center" (default), "top left", "50% 0%", "50% 50% 20px". */
        @Canon(order = 6) private String origin;

        /**
         * Raises (positive) or recesses (negative) the node above its parent surface.
         * Drives drop shadows in 2D, real Z displacement in 3D.
         * String "4px" / "-2px" / "1cm" → Float after presentation.
         */
        @Canon(order = 7) private Object elevation;

        @Canon(order = 8)  private double posX;
        @Canon(order = 9)  private double posY;
        @Canon(order = 10) private double posZ;

        public Transform() {}

        public Transform rotationX(Object v) { this.rotationX = v; return this; }
        public Transform rotationY(Object v) { this.rotationY = v; return this; }
        public Transform rotationZ(Object v) { this.rotationZ = v; return this; }
        public Transform scaleX(Object v)    { this.scaleX = v; return this; }
        public Transform scaleY(Object v)    { this.scaleY = v; return this; }
        public Transform scaleZ(Object v)    { this.scaleZ = v; return this; }
        public Transform origin(String v)    { this.origin = v; return this; }
        public Transform elevation(Object v) { this.elevation = v; return this; }
        public Transform posX(double v)      { this.posX = v; return this; }
        public Transform posY(double v)      { this.posY = v; return this; }
        public Transform posZ(double v)      { this.posZ = v; return this; }

        public float rotationXFloat() { return asFloat(rotationX, 0); }
        public float rotationYFloat() { return asFloat(rotationY, 0); }
        public float rotationZFloat() { return asFloat(rotationZ, 0); }
        public float scaleXFloat()    { return asFloat(scaleX, 1.0f); }
        public float scaleYFloat()    { return asFloat(scaleY, 1.0f); }
        public float scaleZFloat()    { return asFloat(scaleZ, 1.0f); }
        public float elevationFloat() { return asFloat(elevation, 0); }
    }

    /** Font and text styling — 11 fields. Cascade prefix: {@code typography.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Typography implements Canonical {
        @Canon(order = 0)  private String fontFamily;
        /** Font size — String "1.2em" → Float after presentation. */
        @Canon(order = 1)  private Object fontSize;
        @Canon(order = 2)  private String fontWeight;
        /** Font style — "italic" or "normal". */
        @Canon(order = 3)  private String fontStyle;
        /** Text decoration — "underline", "line-through", "overline", or space-separated combination. */
        @Canon(order = 4)  private String textDecoration;
        /** Text alignment — "left", "center", "right", "justify". */
        @Canon(order = 5)  private String textAlign;
        /** Line height — String "1.5" or "24px" → Float after presentation. */
        @Canon(order = 6)  private Object lineHeight;
        /** Letter spacing — String "0.5px" → Float after presentation. */
        @Canon(order = 7)  private Object letterSpacing;
        /** Text overflow — "ellipsis", "clip". */
        @Canon(order = 8)  private String textOverflow;
        /** White space handling — "normal", "nowrap", "pre", "pre-wrap". */
        @Canon(order = 9)  private String whiteSpace;
        /** Foreground color — String "#CDD6F4" → Integer after presentation. */
        @Canon(order = 10) private Object foreground;

        public Typography() {}

        public Typography fontFamily(String v)     { this.fontFamily = v; return this; }
        public Typography fontSize(Object v)       { this.fontSize = v; return this; }
        public Typography fontWeight(String v)     { this.fontWeight = v; return this; }
        public Typography fontStyle(String v)      { this.fontStyle = v; return this; }
        public Typography textDecoration(String v) { this.textDecoration = v; return this; }
        public Typography textAlign(String v)      { this.textAlign = v; return this; }
        public Typography lineHeight(Object v)     { this.lineHeight = v; return this; }
        public Typography letterSpacing(Object v)  { this.letterSpacing = v; return this; }
        public Typography textOverflow(String v)   { this.textOverflow = v; return this; }
        public Typography whiteSpace(String v)     { this.whiteSpace = v; return this; }
        public Typography foreground(Object v)     { this.foreground = v; return this; }

        public float fontSizeFloat()      { return asFloat(fontSize, 0); }
        public float lineHeightFloat()    { return asFloat(lineHeight, 0); }
        public float letterSpacingFloat() { return asFloat(letterSpacing, 0); }
        public int foregroundColor()      { return asInt(foreground, -1); }

        public boolean isBold()         { return "bold".equals(fontWeight); }
        public boolean isItalic()       { return "italic".equals(fontStyle); }
        public boolean hasUnderline()   { return textDecoration != null && textDecoration.contains("underline"); }
        public boolean hasLineThrough() { return textDecoration != null && textDecoration.contains("line-through"); }
        public boolean hasOverline()    { return textDecoration != null && textDecoration.contains("overline"); }
    }

    /** Background color, image, size, gradient. Cascade prefix: {@code background.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Background implements Canonical {
        /** Background color — String "#1E1E2E" → Integer after presentation. */
        @Canon(order = 0) private Object color;
        /** Background image resource path (SVG, PNG, JPEG, WebP, GIF). */
        @Canon(order = 1) private String image;
        /** Background image sizing: "fill", "cover", "contain", or null for natural size. */
        @Canon(order = 2) private String size;
        /** Background gradient — linear or radial with color stops. */
        @Canon(order = 3) private Gradient gradient;

        public Background() {}

        public Background color(Object v)      { this.color = v; return this; }
        public Background image(String v)      { this.image = v; return this; }
        public Background size(String v)       { this.size = v; return this; }
        public Background gradient(Gradient v) { this.gradient = v; return this; }

        public int colorInt() { return asInt(color, -1); }
    }

    /** State-driven property animation. Cascade prefix: {@code transition.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Transition implements Canonical {
        /** Transition property: "all", "backgroundColor", "opacity, backgroundColor". */
        @Canon(order = 0) private String property;
        /** Transition duration: "0.3s" → Float after presentation. */
        @Canon(order = 1) private Object duration;
        /** Transition easing: "ease-out", "spring", "cubic-bezier(...)". */
        @Canon(order = 2) private String easing;
        /** Transition delay: "0.1s" → Float after presentation. */
        @Canon(order = 3) private Object delay;

        public Transition() {}

        public Transition property(String v) { this.property = v; return this; }
        public Transition duration(Object v) { this.duration = v; return this; }
        public Transition easing(String v)   { this.easing = v; return this; }
        public Transition delay(Object v)    { this.delay = v; return this; }

        public float durationFloat() { return asFloat(duration, 0); }
        public float delayFloat()    { return asFloat(delay, 0); }
    }

    /** Keyframe timeline animation. Cascade prefix: {@code animation.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Animation implements Canonical {
        /** Animation duration: "2s", "500ms" → Float after presentation. */
        @Canon(order = 0) private Object duration;
        /** Iteration count: "infinite", "3", "1". */
        @Canon(order = 1) private String iterationCount;
        /** Direction: "normal", "reverse", "alternate", "alternate-reverse". */
        @Canon(order = 2) private String direction;
        /** Easing between keyframes — same functions as transition easing. */
        @Canon(order = 3) private String easing;
        /** Delay before animation starts: "0.5s" → Float after presentation. */
        @Canon(order = 4) private Object delay;
        /** Fill mode: "none", "forwards", "backwards", "both". */
        @Canon(order = 5) private String fillMode;
        /** Play state: "running", "paused". */
        @Canon(order = 6) private String playState;
        /** Keyframe sequence — list of percentage stops with property values. */
        @Canon(order = 7) private List<Keyframe> keyframes;

        public Animation() {}

        public Animation duration(Object v)        { this.duration = v; return this; }
        public Animation iterationCount(String v)  { this.iterationCount = v; return this; }
        public Animation direction(String v)       { this.direction = v; return this; }
        public Animation easing(String v)          { this.easing = v; return this; }
        public Animation delay(Object v)           { this.delay = v; return this; }
        public Animation fillMode(String v)        { this.fillMode = v; return this; }
        public Animation playState(String v)       { this.playState = v; return this; }
        public Animation keyframes(List<Keyframe> v) { this.keyframes = v; return this; }

        public float durationFloat() { return asFloat(duration, 0); }
        public float delayFloat()    { return asFloat(delay, 0); }
    }

    /** Container layout: flow direction, gap, alignment, grid dimensions. Cascade prefix: {@code layout.*}. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.MAP)
    public static class Layout implements Canonical {
        /** Flow mode: "vertical", "horizontal", "stack", "grid". */
        @Canon(order = 0) private String mode;
        /** Gap between children — String "0.5em" → Float after presentation. */
        @Canon(order = 1) private Object gap;
        @Canon(order = 2) private String align;
        @Canon(order = 3) private String justify;
        @Canon(order = 4) private boolean wrap;
        @Canon(order = 5) private int columns;
        @Canon(order = 6) private int rows;
        @Canon(order = 7) private float aspectRatio;

        public Layout() {}

        public Layout mode(String v)        { this.mode = v; return this; }
        public Layout gap(Object v)         { this.gap = v; return this; }
        public Layout align(String v)       { this.align = v; return this; }
        public Layout justify(String v)     { this.justify = v; return this; }
        public Layout wrap(boolean v)       { this.wrap = v; return this; }
        public Layout columns(int v)        { this.columns = v; return this; }
        public Layout rows(int v)           { this.rows = v; return this; }
        public Layout aspectRatio(float v)  { this.aspectRatio = v; return this; }

        public float gapFloat() { return asFloat(gap, 0); }
    }

    /** A keyframe in an animation timeline — percentage stop with property values. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization
    public static class Keyframe implements Canonical {
        @Canon(order = 0) private float at;
        @Canon(order = 1) private Map<String, String> properties;

        public Keyframe() {}

        public Keyframe at(float at) { this.at = at; return this; }
        public Keyframe properties(Map<String, String> props) { this.properties = props; return this; }

        public static Keyframe of(float at, Map<String, String> properties) {
            return new Keyframe().at(at).properties(Map.copyOf(properties));
        }
    }

    /** A state declaration — the runtime holds the actual value. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization
    public static class StateDecl implements Canonical {
        @Canon(order = 0) private String key;
        @Canon(order = 1) private String defaultValue;

        public StateDecl() {}

        public StateDecl key(String key) { this.key = key; return this; }
        public StateDecl defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }

        public static StateDecl of(String key, String defaultValue) {
            return new StateDecl().key(key).defaultValue(defaultValue);
        }
    }

    /** A semantic token — a sememe reference with grammatical features. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization
    public static class SemanticToken implements Canonical {
        @Canon(order = 0) private ItemID sememe;
        @Canon(order = 1) private List<ItemID> features;

        public SemanticToken() {}

        public SemanticToken sememe(ItemID sememe) { this.sememe = sememe; return this; }
        public SemanticToken features(List<ItemID> features) { this.features = features; return this; }

        public static SemanticToken of(ItemID sememe) {
            SemanticToken t = new SemanticToken();
            t.sememe = sememe;
            t.features = List.of();
            return t;
        }
        public static SemanticToken of(ItemID sememe, ItemID... features) {
            SemanticToken t = new SemanticToken();
            t.sememe = sememe;
            t.features = List.of(features);
            return t;
        }
    }

    // =================================================================================
    // Factories
    // =================================================================================

    public static SceneNode container(String layoutMode) {
        SceneNode n = new SceneNode();
        n.type = NodeType.CONTAINER;
        n.layout = new Layout().mode(layoutMode);
        return n;
    }

    public static SceneNode vertical()   { return container("vertical"); }
    public static SceneNode horizontal() { return container("horizontal"); }
    public static SceneNode stack()      { return container("stack"); }

    public static SceneNode grid(int columns, int rows) {
        SceneNode n = container("grid");
        n.layout.columns(columns).rows(rows);
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
        state.add(StateDecl.of(key, defaultValue));
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

    // =================================================================================
    // Convenience Accessors — Typography (delegates)
    // =================================================================================

    public String fontFamily()         { return typography != null ? typography.fontFamily() : null; }
    public Object fontSize()           { return typography != null ? typography.fontSize() : null; }
    public String fontWeight()         { return typography != null ? typography.fontWeight() : null; }
    public String fontStyle()          { return typography != null ? typography.fontStyle() : null; }
    public String textDecoration()     { return typography != null ? typography.textDecoration() : null; }
    public String textAlign()          { return typography != null ? typography.textAlign() : null; }
    public Object lineHeight()         { return typography != null ? typography.lineHeight() : null; }
    public Object letterSpacing()      { return typography != null ? typography.letterSpacing() : null; }
    public String textOverflow()       { return typography != null ? typography.textOverflow() : null; }
    public String whiteSpace()         { return typography != null ? typography.whiteSpace() : null; }
    public Object foreground()         { return typography != null ? typography.foreground() : null; }

    public float fontSizeFloat()      { return typography != null ? typography.fontSizeFloat() : 0; }
    public float lineHeightFloat()    { return typography != null ? typography.lineHeightFloat() : 0; }
    public float letterSpacingFloat() { return typography != null ? typography.letterSpacingFloat() : 0; }
    public int   foregroundColor()    { return typography != null ? typography.foregroundColor() : -1; }

    public boolean isBold()         { return typography != null && typography.isBold(); }
    public boolean isItalic()       { return typography != null && typography.isItalic(); }
    public boolean hasUnderline()   { return typography != null && typography.hasUnderline(); }
    public boolean hasLineThrough() { return typography != null && typography.hasLineThrough(); }
    public boolean hasOverline()    { return typography != null && typography.hasOverline(); }

    /** Get font size as declared string (before resolution). */
    public String fontSizeSpec() {
        Object fs = fontSize();
        return fs instanceof String s ? s : fs != null ? fs.toString() : null;
    }

    /** Get foreground as declared string (before resolution). */
    public String foregroundSpec() {
        return foreground() instanceof String s ? s : null;
    }

    // =================================================================================
    // Convenience Accessors — Background (delegates)
    // =================================================================================

    public Object   backgroundColor()    { return background != null ? background.color() : null; }
    public String   backgroundImage()    { return background != null ? background.image() : null; }
    public String   backgroundSize()     { return background != null ? background.size() : null; }
    public Gradient backgroundGradient() { return background != null ? background.gradient() : null; }
    public int      backgroundColorInt() { return background != null ? background.colorInt() : -1; }

    /** Get background color as declared string (before resolution). */
    public String backgroundColorSpec() {
        return backgroundColor() instanceof String s ? s : null;
    }

    // =================================================================================
    // Convenience Accessors — Border (delegates)
    // =================================================================================

    public Object borderTopWidth()    { return border != null ? border.topWidth() : null; }
    public Object borderRightWidth()  { return border != null ? border.rightWidth() : null; }
    public Object borderBottomWidth() { return border != null ? border.bottomWidth() : null; }
    public Object borderLeftWidth()   { return border != null ? border.leftWidth() : null; }
    public String borderTopStyle()    { return border != null ? border.topStyle() : null; }
    public String borderRightStyle()  { return border != null ? border.rightStyle() : null; }
    public String borderBottomStyle() { return border != null ? border.bottomStyle() : null; }
    public String borderLeftStyle()   { return border != null ? border.leftStyle() : null; }
    public Object borderTopColor()    { return border != null ? border.topColor() : null; }
    public Object borderRightColor()  { return border != null ? border.rightColor() : null; }
    public Object borderBottomColor() { return border != null ? border.bottomColor() : null; }
    public Object borderLeftColor()   { return border != null ? border.leftColor() : null; }

    public float borderTopWidthFloat()    { return border != null ? border.topWidthFloat() : 0; }
    public float borderRightWidthFloat()  { return border != null ? border.rightWidthFloat() : 0; }
    public float borderBottomWidthFloat() { return border != null ? border.bottomWidthFloat() : 0; }
    public float borderLeftWidthFloat()   { return border != null ? border.leftWidthFloat() : 0; }
    public int borderTopColorInt()    { return border != null ? border.topColorInt() : -1; }
    public int borderRightColorInt()  { return border != null ? border.rightColorInt() : -1; }
    public int borderBottomColorInt() { return border != null ? border.bottomColorInt() : -1; }
    public int borderLeftColorInt()   { return border != null ? border.leftColorInt() : -1; }

    // =================================================================================
    // Convenience Accessors — Transform (delegates)
    // =================================================================================

    public Object rotationX()       { return transform != null ? transform.rotationX() : null; }
    public Object rotationY()       { return transform != null ? transform.rotationY() : null; }
    public Object rotationZ()       { return transform != null ? transform.rotationZ() : null; }
    public Object scaleX()          { return transform != null ? transform.scaleX() : null; }
    public Object scaleY()          { return transform != null ? transform.scaleY() : null; }
    public Object scaleZ()          { return transform != null ? transform.scaleZ() : null; }
    public String transformOrigin() { return transform != null ? transform.origin() : null; }
    public Object elevation()       { return transform != null ? transform.elevation() : null; }
    public double posX()            { return transform != null ? transform.posX() : 0; }
    public double posY()            { return transform != null ? transform.posY() : 0; }
    public double posZ()            { return transform != null ? transform.posZ() : 0; }

    public float rotationXFloat() { return transform != null ? transform.rotationXFloat() : 0; }
    public float rotationYFloat() { return transform != null ? transform.rotationYFloat() : 0; }
    public float rotationZFloat() { return transform != null ? transform.rotationZFloat() : 0; }
    public float scaleXFloat()    { return transform != null ? transform.scaleXFloat() : 1.0f; }
    public float scaleYFloat()    { return transform != null ? transform.scaleYFloat() : 1.0f; }
    public float scaleZFloat()    { return transform != null ? transform.scaleZFloat() : 1.0f; }
    public float elevationFloat() { return transform != null ? transform.elevationFloat() : 0; }

    // =================================================================================
    // Convenience Accessors — Transition (delegates)
    // =================================================================================

    public String transitionProperty() { return transition != null ? transition.property() : null; }
    public Object transitionDuration() { return transition != null ? transition.duration() : null; }
    public String transitionEasing()   { return transition != null ? transition.easing() : null; }
    public Object transitionDelay()    { return transition != null ? transition.delay() : null; }

    public float transitionDurationFloat() { return transition != null ? transition.durationFloat() : 0; }
    public float transitionDelayFloat()    { return transition != null ? transition.delayFloat() : 0; }

    // =================================================================================
    // Convenience Accessors — Animation (delegates)
    // =================================================================================

    public Object         animationDuration()       { return animation != null ? animation.duration() : null; }
    public String         animationIterationCount() { return animation != null ? animation.iterationCount() : null; }
    public String         animationDirection()      { return animation != null ? animation.direction() : null; }
    public String         animationEasing()         { return animation != null ? animation.easing() : null; }
    public Object         animationDelay()          { return animation != null ? animation.delay() : null; }
    public String         animationFillMode()       { return animation != null ? animation.fillMode() : null; }
    public String         animationPlayState()      { return animation != null ? animation.playState() : null; }
    public List<Keyframe> keyframes()               { return animation != null ? animation.keyframes() : null; }

    public float animationDurationFloat() { return animation != null ? animation.durationFloat() : 0; }
    public float animationDelayFloat()    { return animation != null ? animation.delayFloat() : 0; }

    // =================================================================================
    // Convenience Accessors — Layout (delegates)
    // =================================================================================

    /** Layout flow mode: "vertical", "horizontal", "stack", "grid". */
    public String  layout()      { return layout != null ? layout.mode() : null; }
    public Object  gap()         { return layout != null ? layout.gap() : null; }
    public String  align()       { return layout != null ? layout.align() : null; }
    public String  justify()     { return layout != null ? layout.justify() : null; }
    public boolean wrap()        { return layout != null && layout.wrap(); }
    public int     columns()     { return layout != null ? layout.columns() : 0; }
    public int     rows()        { return layout != null ? layout.rows() : 0; }
    public float   aspectRatio() { return layout != null ? layout.aspectRatio() : 0; }

    public float gapFloat() { return layout != null ? layout.gapFloat() : 0; }

    /** Get gap as declared string (before resolution). */
    public String gapSpec() {
        return gap() instanceof String s ? s : null;
    }

    // =================================================================================
    // Convenience Accessors — Box Model (root-level)
    // =================================================================================

    public float cornerFloat()    { return asFloat(corner, 0); }
    public float opacityFloat()   { return asFloat(opacity, 1.0f); }
    public float minWidthFloat()  { return asFloat(minWidth, 0); }
    public float maxWidthFloat()  { return asFloat(maxWidth, 0); }
    public float minHeightFloat() { return asFloat(minHeight, 0); }
    public float maxHeightFloat() { return asFloat(maxHeight, 0); }

    /** Get corner as declared string (before resolution). */
    public String cornerSpec() {
        return corner instanceof String s ? s : null;
    }

    // =================================================================================
    // Convenience Accessors — Body (root-level)
    // =================================================================================

    public int   fillColor()        { return asInt(fill, -1); }
    public int   strokeColorInt()   { return asInt(strokeColor, -1); }
    public float strokeWidthFloat() { return asFloat(strokeWidth, 0); }

    /** Whether this node is visible. True unless explicitly set to false by the resolver. */
    public boolean isVisible() {
        if (visible instanceof Boolean b) return b;
        if (visible instanceof String s) return !"false".equals(s);
        return true;  // null or unset = visible
    }

    // =================================================================================
    // Layout Internal Accessors
    // =================================================================================

    public float explicitWidth() { return explicitWidth; }
    public void  explicitWidth(float w) { this.explicitWidth = w; }
    public float explicitHeight() { return explicitHeight; }
    public void  explicitHeight(float h) { this.explicitHeight = h; }

    public float paddingTop()    { return paddingTop; }
    public float paddingRight()  { return paddingRight; }
    public float paddingBottom() { return paddingBottom; }
    public float paddingLeft()   { return paddingLeft; }
    public void  padding(float top, float right, float bottom, float left) {
        this.paddingTop = top; this.paddingRight = right;
        this.paddingBottom = bottom; this.paddingLeft = left;
    }

    public float measuredWidth()  { return measuredWidth; }
    public float measuredHeight() { return measuredHeight; }
    public void  measuredSize(float w, float h) { this.measuredWidth = w; this.measuredHeight = h; }

    public float contentHeight() { return contentHeight; }
    public void  contentHeight(float h) { this.contentHeight = h; }
    public float scrollOffsetY() { return scrollOffsetY; }
    public void  scrollOffsetY(float offset) { this.scrollOffsetY = offset; }
    public boolean overflowsY() { return overflowsY; }
    public void    overflowsY(boolean v) { this.overflowsY = v; }
    public boolean isScrollContainer() {
        return "scroll".equals(overflow) || ("auto".equals(overflow) && overflowsY);
    }

    /** Whether this node uses anchor positioning (taken out of parent flow). */
    public boolean isAnchored() {
        return anchorTop != null || anchorRight != null || anchorBottom != null || anchorLeft != null;
    }

    public boolean hidden() { return hidden; }
    public void    hidden(boolean h) { this.hidden = h; }

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
    // Fluent Setters — Identity & Box Model
    // =================================================================================

    public SceneNode type(NodeType t)             { this.type = t; return this; }
    public SceneNode id(String id)                { this.id = id; return this; }
    public SceneNode classes(String... classes)   { this.classes = List.of(classes); return this; }
    public SceneNode classes(List<String> classes) { this.classes = classes; return this; }
    public SceneNode width(String w)              { this.width = w; return this; }
    public SceneNode height(String h)             { this.height = h; return this; }
    public SceneNode minWidth(Object w)           { this.minWidth = w; return this; }
    public SceneNode maxWidth(Object w)           { this.maxWidth = w; return this; }
    public SceneNode minHeight(Object h)          { this.minHeight = h; return this; }
    public SceneNode maxHeight(Object h)          { this.maxHeight = h; return this; }
    public SceneNode margin(String m)             { this.margin = m; return this; }
    public SceneNode padding(String p)            { this.padding = p; return this; }
    public SceneNode corner(Object c)             { this.corner = c; return this; }
    public SceneNode overflow(String o)           { this.overflow = o; return this; }
    public SceneNode opacity(Object o)            { this.opacity = o; return this; }
    public SceneNode visible(Object v)            { this.visible = v; return this; }
    public SceneNode cursor(String c)             { this.cursor = c; return this; }
    public SceneNode capturesFocus(boolean cf)    { this.capturesFocus = cf; return this; }
    public SceneNode editable(boolean e)          { this.editable = e; return this; }
    public SceneNode bind(String expr)            { this.bind = expr; return this; }

    // =================================================================================
    // Fluent Setters — Border (delegators that lazy-create)
    // =================================================================================

    /** Set border shorthand — decomposes to per-side fields using BoxBorder.parse(). */
    public SceneNode border(String shorthand) {
        BoxBorder parsed = BoxBorder.parse(shorthand);
        if (parsed != null && parsed.isVisible()) {
            if (border == null) border = new Border();
            applyBorderSide(parsed.top(),    s -> border.topWidth(s.width()).topStyle(s.style()).topColor(s.color()));
            applyBorderSide(parsed.right(),  s -> border.rightWidth(s.width()).rightStyle(s.style()).rightColor(s.color()));
            applyBorderSide(parsed.bottom(), s -> border.bottomWidth(s.width()).bottomStyle(s.style()).bottomColor(s.color()));
            applyBorderSide(parsed.left(),   s -> border.leftWidth(s.width()).leftStyle(s.style()).leftColor(s.color()));
        }
        return this;
    }

    private static void applyBorderSide(BoxBorder.BorderSide side, java.util.function.Consumer<BoxBorder.BorderSide> apply) {
        if (side != null && side.isVisible()) apply.accept(side);
    }

    public SceneNode borderTopWidth(Object w)    { if (border == null) border = new Border(); border.topWidth(w);    return this; }
    public SceneNode borderRightWidth(Object w)  { if (border == null) border = new Border(); border.rightWidth(w);  return this; }
    public SceneNode borderBottomWidth(Object w) { if (border == null) border = new Border(); border.bottomWidth(w); return this; }
    public SceneNode borderLeftWidth(Object w)   { if (border == null) border = new Border(); border.leftWidth(w);   return this; }
    public SceneNode borderTopStyle(String s)    { if (border == null) border = new Border(); border.topStyle(s);    return this; }
    public SceneNode borderRightStyle(String s)  { if (border == null) border = new Border(); border.rightStyle(s);  return this; }
    public SceneNode borderBottomStyle(String s) { if (border == null) border = new Border(); border.bottomStyle(s); return this; }
    public SceneNode borderLeftStyle(String s)   { if (border == null) border = new Border(); border.leftStyle(s);   return this; }
    public SceneNode borderTopColor(Object c)    { if (border == null) border = new Border(); border.topColor(c);    return this; }
    public SceneNode borderRightColor(Object c)  { if (border == null) border = new Border(); border.rightColor(c);  return this; }
    public SceneNode borderBottomColor(Object c) { if (border == null) border = new Border(); border.bottomColor(c); return this; }
    public SceneNode borderLeftColor(Object c)   { if (border == null) border = new Border(); border.leftColor(c);   return this; }

    // =================================================================================
    // Fluent Setters — Background (delegators that lazy-create)
    // =================================================================================

    public SceneNode backgroundColor(Object bg) {
        if (background == null) background = new Background();
        background.color(bg);
        return this;
    }
    public SceneNode backgroundImage(String path) {
        if (background == null) background = new Background();
        background.image(path);
        return this;
    }
    public SceneNode backgroundSize(String size) {
        if (background == null) background = new Background();
        background.size(size);
        return this;
    }
    public SceneNode backgroundGradient(Gradient g) {
        if (background == null) background = new Background();
        background.gradient(g);
        return this;
    }

    // =================================================================================
    // Fluent Setters — Typography (delegators that lazy-create)
    // =================================================================================

    public SceneNode fontFamily(String f) {
        if (typography == null) typography = new Typography();
        typography.fontFamily(f);
        return this;
    }
    public SceneNode fontSize(Object s) {
        if (typography == null) typography = new Typography();
        typography.fontSize(s);
        return this;
    }
    public SceneNode fontWeight(String w) {
        if (typography == null) typography = new Typography();
        typography.fontWeight(w);
        return this;
    }
    public SceneNode fontStyle(String s) {
        if (typography == null) typography = new Typography();
        typography.fontStyle(s);
        return this;
    }
    public SceneNode textDecoration(String d) {
        if (typography == null) typography = new Typography();
        typography.textDecoration(d);
        return this;
    }
    public SceneNode textAlign(String a) {
        if (typography == null) typography = new Typography();
        typography.textAlign(a);
        return this;
    }
    public SceneNode lineHeight(Object lh) {
        if (typography == null) typography = new Typography();
        typography.lineHeight(lh);
        return this;
    }
    public SceneNode letterSpacing(Object ls) {
        if (typography == null) typography = new Typography();
        typography.letterSpacing(ls);
        return this;
    }
    public SceneNode textOverflow(String to) {
        if (typography == null) typography = new Typography();
        typography.textOverflow(to);
        return this;
    }
    public SceneNode whiteSpace(String ws) {
        if (typography == null) typography = new Typography();
        typography.whiteSpace(ws);
        return this;
    }
    public SceneNode foreground(Object c) {
        if (typography == null) typography = new Typography();
        typography.foreground(c);
        return this;
    }

    // =================================================================================
    // Fluent Setters — Transform (delegators that lazy-create)
    // =================================================================================

    /** Shorthand — sets rotationZ (the common 2D case). */
    public SceneNode rotation(Object r) {
        if (transform == null) transform = new Transform();
        transform.rotationZ(r);
        return this;
    }
    public SceneNode rotationX(Object r) {
        if (transform == null) transform = new Transform();
        transform.rotationX(r);
        return this;
    }
    public SceneNode rotationY(Object r) {
        if (transform == null) transform = new Transform();
        transform.rotationY(r);
        return this;
    }
    public SceneNode rotationZ(Object r) {
        if (transform == null) transform = new Transform();
        transform.rotationZ(r);
        return this;
    }
    /** Shorthand — sets uniform scaleX/Y/Z. */
    public SceneNode scale(Object s) {
        if (transform == null) transform = new Transform();
        transform.scaleX(s).scaleY(s).scaleZ(s);
        return this;
    }
    public SceneNode scaleX(Object s) {
        if (transform == null) transform = new Transform();
        transform.scaleX(s);
        return this;
    }
    public SceneNode scaleY(Object s) {
        if (transform == null) transform = new Transform();
        transform.scaleY(s);
        return this;
    }
    public SceneNode scaleZ(Object s) {
        if (transform == null) transform = new Transform();
        transform.scaleZ(s);
        return this;
    }
    public SceneNode transformOrigin(String o) {
        if (transform == null) transform = new Transform();
        transform.origin(o);
        return this;
    }
    public SceneNode elevation(Object e) {
        if (transform == null) transform = new Transform();
        transform.elevation(e);
        return this;
    }
    public SceneNode position(double x, double y, double z) {
        if (transform == null) transform = new Transform();
        transform.posX(x).posY(y).posZ(z);
        return this;
    }

    // =================================================================================
    // Fluent Setters — Transition (delegators that lazy-create)
    // =================================================================================

    public SceneNode transitionProperty(String p) {
        if (transition == null) transition = new Transition();
        transition.property(p);
        return this;
    }
    public SceneNode transitionDuration(Object d) {
        if (transition == null) transition = new Transition();
        transition.duration(d);
        return this;
    }
    public SceneNode transitionEasing(String e) {
        if (transition == null) transition = new Transition();
        transition.easing(e);
        return this;
    }
    public SceneNode transitionDelay(Object d) {
        if (transition == null) transition = new Transition();
        transition.delay(d);
        return this;
    }

    // =================================================================================
    // Fluent Setters — Animation (delegators that lazy-create)
    // =================================================================================

    public SceneNode keyframes(List<Keyframe> kf) {
        if (animation == null) animation = new Animation();
        animation.keyframes(kf);
        return this;
    }
    public SceneNode animationDuration(Object d) {
        if (animation == null) animation = new Animation();
        animation.duration(d);
        return this;
    }
    public SceneNode animationIterationCount(String c) {
        if (animation == null) animation = new Animation();
        animation.iterationCount(c);
        return this;
    }
    public SceneNode animationDirection(String d) {
        if (animation == null) animation = new Animation();
        animation.direction(d);
        return this;
    }
    public SceneNode animationEasing(String e) {
        if (animation == null) animation = new Animation();
        animation.easing(e);
        return this;
    }
    public SceneNode animationDelay(Object d) {
        if (animation == null) animation = new Animation();
        animation.delay(d);
        return this;
    }
    public SceneNode animationFillMode(String m) {
        if (animation == null) animation = new Animation();
        animation.fillMode(m);
        return this;
    }
    public SceneNode animationPlayState(String s) {
        if (animation == null) animation = new Animation();
        animation.playState(s);
        return this;
    }

    // =================================================================================
    // Fluent Setters — Layout (delegators that lazy-create)
    // =================================================================================

    /** Set layout flow mode. */
    public SceneNode layout(String mode) {
        if (layout == null) layout = new Layout();
        layout.mode(mode);
        return this;
    }
    public SceneNode gap(Object g) {
        if (layout == null) layout = new Layout();
        layout.gap(g);
        return this;
    }
    public SceneNode align(String a) {
        if (layout == null) layout = new Layout();
        layout.align(a);
        return this;
    }
    public SceneNode justify(String j) {
        if (layout == null) layout = new Layout();
        layout.justify(j);
        return this;
    }
    public SceneNode wrap(boolean w) {
        if (layout == null) layout = new Layout();
        layout.wrap(w);
        return this;
    }
    public SceneNode columns(int c) {
        if (layout == null) layout = new Layout();
        layout.columns(c);
        return this;
    }
    public SceneNode rows(int r) {
        if (layout == null) layout = new Layout();
        layout.rows(r);
        return this;
    }
    public SceneNode aspectRatio(float ratio) {
        if (layout == null) layout = new Layout();
        layout.aspectRatio(ratio);
        return this;
    }

    // =================================================================================
    // Fluent Setters — Anchor & Container & Text & Body
    // =================================================================================

    public SceneNode anchorTop(String a)    { this.anchorTop = a; return this; }
    public SceneNode anchorRight(String a)  { this.anchorRight = a; return this; }
    public SceneNode anchorBottom(String a) { this.anchorBottom = a; return this; }
    public SceneNode anchorLeft(String a)   { this.anchorLeft = a; return this; }
    public SceneNode repeat(String expr)    { this.repeat = expr; return this; }
    public SceneNode childTemplate(SceneNode template) { this.childTemplate = template; return this; }
    public SceneNode text(String t)         { this.text = t; return this; }
    public SceneNode format(ItemID f)       { this.format = f; return this; }
    public SceneNode tokens(List<SemanticToken> t) { this.tokens = t; return this; }
    public SceneNode shape(String s)        { this.shape = s; return this; }
    public SceneNode image(String i)        { this.image = i; return this; }
    public SceneNode model(String m)        { this.model = m; return this; }
    public SceneNode glyph(String g)        { this.glyph = g; return this; }
    public SceneNode alt(String a)          { this.alt = a; return this; }
    public SceneNode fill(Object f)         { this.fill = f; return this; }
    public SceneNode strokeColor(Object s)  { this.strokeColor = s; return this; }
    public SceneNode strokeWidth(Object w)  { this.strokeWidth = w; return this; }
    public SceneNode radius(String r)       { this.radius = r; return this; }
    public SceneNode pathData(String d)     { this.pathData = d; return this; }
    public SceneNode material(String m)     { this.material = m; return this; }

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
