package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.SceneEvent;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal base for all scene nodes — Container, Text, Body.
 *
 * <p>Properties describe WHAT a node is. Renderers decide HOW to render it.
 * The same node tree renders in 2D (Skia), 3D (Filament), TUI (terminal),
 * or CLI (plain text). Each renderer interprets the properties for its platform.
 *
 * <p>Three primitives extend this base:
 * <ul>
 *   <li>{@link Container} — children + layout. The structural primitive.</li>
 *   <li>{@link Text} — text content. The content primitive.</li>
 *   <li>{@link Body} — visual content: shape, image, model, glyph.</li>
 * </ul>
 *
 * <p>State is declared on the node via {@link #state} but managed by the
 * renderer — not stored on the node itself. Events update state, bindings
 * read from state. No model objects needed.
 *
 * <p>All nodes are CBOR-serializable for wire transfer and storage as frames.
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization
public abstract class Node implements Canonical {

    // ==================================================================================
    // Identity
    // ==================================================================================

    @Canon(order = 0)
    protected String id;

    @Getter(lombok.AccessLevel.NONE)  // manual getter below — avoids varargs setter collision
    @Canon(order = 1)
    protected List<String> classes;

    // ==================================================================================
    // Box Model
    // ==================================================================================

    @Canon(order = 10)
    protected String width;

    @Canon(order = 11)
    protected String height;

    @Canon(order = 12)
    protected String margin;

    @Canon(order = 13)
    protected String padding;

    @Canon(order = 14)
    protected String border;

    @Canon(order = 15)
    protected String corner;

    @Canon(order = 16)
    protected String background;

    @Canon(order = 17)
    protected String overflow;

    // ==================================================================================
    // Typography
    // ==================================================================================

    @Canon(order = 20)
    protected String fontFamily;

    @Canon(order = 21)
    protected String fontSize;

    @Canon(order = 22)
    protected String fontWeight;

    @Canon(order = 23)
    protected String foreground;

    @Canon(order = 24)
    protected String opacity;

    // ==================================================================================
    // Transform
    // ==================================================================================

    @Canon(order = 30)
    protected String rotation;

    @Canon(order = 31)
    protected double scale = 1.0;

    @Canon(order = 32)
    protected double elevation;

    @Canon(order = 33)
    protected double posX;

    @Canon(order = 34)
    protected double posY;

    @Canon(order = 35)
    protected double posZ;

    // ==================================================================================
    // Interaction
    // ==================================================================================

    @Canon(order = 40)
    protected List<SceneEvent> events;

    @Canon(order = 41)
    protected boolean capturesFocus;

    @Canon(order = 42)
    protected String cursor;

    @Canon(order = 43)
    protected boolean editable;

    // ==================================================================================
    // Data Binding
    // ==================================================================================

    /** Content binding expression — where this node's content comes from. */
    @Canon(order = 50)
    protected String bind;

    /** Visibility binding — show/hide based on expression result. */
    @Canon(order = 51)
    protected String visible;

    // ==================================================================================
    // State Declarations (renderer-managed)
    // ==================================================================================

    @Canon(order = 60)
    protected List<StateDecl> state;

    /** A state declaration — the renderer holds the actual value. */
    @Getter
    @Accessors(fluent = true)
    @Canonical.Canonization
    public static class StateDecl implements Canonical {
        @Canon(order = 0) private String key;
        @Canon(order = 1) private String defaultValue;

        public StateDecl() {}
        public StateDecl(String key, String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    // ==================================================================================
    // Conditional Property Blocks
    // ==================================================================================

    /**
     * Conditional property overrides — keyed by condition name.
     *
     * <p>Conditions: "hover", "selected", "focused", "narrow", "wide", ":3d", ":2d".
     * Values: property maps that override base properties when the condition is met.
     */
    @Canon(order = 70)
    protected Map<String, Map<String, String>> when;

    // ==================================================================================
    // Fluent Builder API
    // ==================================================================================

    /** Get the style classes for this node. */
    public List<String> classes() { return classes; }

    @SuppressWarnings("unchecked")
    protected <T extends Node> T self() { return (T) this; }

    public <T extends Node> T id(String id) { this.id = id; return self(); }
    public <T extends Node> T classes(String... classes) { this.classes = List.of(classes); return self(); }
    public <T extends Node> T width(String w) { this.width = w; return self(); }
    public <T extends Node> T height(String h) { this.height = h; return self(); }
    public <T extends Node> T margin(String m) { this.margin = m; return self(); }
    public <T extends Node> T padding(String p) { this.padding = p; return self(); }
    public <T extends Node> T border(String b) { this.border = b; return self(); }
    public <T extends Node> T corner(String c) { this.corner = c; return self(); }
    public <T extends Node> T background(String bg) { this.background = bg; return self(); }
    public <T extends Node> T overflow(String o) { this.overflow = o; return self(); }
    public <T extends Node> T fontFamily(String f) { this.fontFamily = f; return self(); }
    public <T extends Node> T fontSize(String s) { this.fontSize = s; return self(); }
    public <T extends Node> T fontWeight(String w) { this.fontWeight = w; return self(); }
    public <T extends Node> T foreground(String c) { this.foreground = c; return self(); }
    public <T extends Node> T opacity(String o) { this.opacity = o; return self(); }
    public <T extends Node> T rotation(String r) { this.rotation = r; return self(); }
    public <T extends Node> T scale(double s) { this.scale = s; return self(); }
    public <T extends Node> T elevation(double e) { this.elevation = e; return self(); }
    public <T extends Node> T position(double x, double y, double z) {
        this.posX = x; this.posY = y; this.posZ = z; return self();
    }
    public <T extends Node> T cursor(String c) { this.cursor = c; return self(); }
    public <T extends Node> T capturesFocus(boolean cf) { this.capturesFocus = cf; return self(); }
    public <T extends Node> T editable(boolean e) { this.editable = e; return self(); }
    public <T extends Node> T bind(String expr) { this.bind = expr; return self(); }
    public <T extends Node> T visible(String expr) { this.visible = expr; return self(); }

    public <T extends Node> T on(String event, String action) {
        if (events == null) events = new ArrayList<>();
        events.add(SceneEvent.of(event, action, ""));
        return self();
    }

    public <T extends Node> T on(String event, String action, String target) {
        if (events == null) events = new ArrayList<>();
        events.add(SceneEvent.of(event, action, target));
        return self();
    }

    public <T extends Node> T declareState(String key, String defaultValue) {
        if (state == null) state = new ArrayList<>();
        state.add(new StateDecl(key, defaultValue));
        return self();
    }

    public <T extends Node> T when(String condition, String property, String value) {
        if (when == null) when = new LinkedHashMap<>();
        when.computeIfAbsent(condition, k -> new LinkedHashMap<>()).put(property, value);
        return self();
    }
}
