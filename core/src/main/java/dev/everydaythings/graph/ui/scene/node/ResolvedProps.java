package dev.everydaythings.graph.ui.scene.node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The final merged properties for a node after condition evaluation.
 *
 * <p>Built by the SceneRenderer's default {@code resolveProperties()} method.
 * Base node properties are copied, then matching {@code when} blocks are applied
 * on top (environment conditions, pseudo-states, declared state).
 *
 * <p>Paint methods receive this instead of the raw Node, so they never
 * need to evaluate conditions themselves.
 */
public class ResolvedProps {

    private final Map<String, Object> props;

    private ResolvedProps(Map<String, Object> props) {
        this.props = props;
    }

    // ==================================================================================
    // Property Access
    // ==================================================================================

    public String string(String key) {
        Object v = props.get(key);
        return v instanceof String s ? s : null;
    }

    public boolean bool(String key) {
        Object v = props.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equals(s);
        return false;
    }

    public int integer(String key, int defaultValue) {
        Object v = props.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public double number(String key, double defaultValue) {
        Object v = props.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public Object raw(String key) {
        return props.get(key);
    }

    public boolean has(String key) {
        return props.containsKey(key);
    }

    // ==================================================================================
    // Convenience (common node properties)
    // ==================================================================================

    public String id()         { return string("id"); }
    public List<String> classes() {
        Object v = props.get("classes");
        if (v instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) list;
            return result;
        }
        return List.of();
    }

    public String width()      { return string("width"); }
    public String height()     { return string("height"); }
    public String padding()    { return string("padding"); }
    public String margin()     { return string("margin"); }
    public String border()     { return string("border"); }
    public String corner()     { return string("corner"); }
    public String background() { return string("background"); }
    public String overflow()   { return string("overflow"); }
    public String foreground() { return string("foreground"); }
    public String fontFamily() { return string("fontFamily"); }
    public String fontSize()   { return string("fontSize"); }
    public String fontWeight() { return string("fontWeight"); }
    public String opacity()    { return string("opacity"); }
    public String cursor()     { return string("cursor"); }
    public double elevation()  { return number("elevation", 0.0); }
    public double scale()      { return number("scale", 1.0); }
    public boolean editable()  { return bool("editable"); }

    // Container-specific
    public String layout()     { return string("layout"); }
    public String gap()        { return string("gap"); }
    public String align()      { return string("align"); }
    public String justify()    { return string("justify"); }

    // Text-specific
    public String text()       { return string("text"); }

    // Body-specific
    public String shape()      { return string("shape"); }
    public String image()      { return string("image"); }
    public String model()      { return string("model"); }
    public String glyph()      { return string("glyph"); }
    public String alt()        { return string("alt"); }
    public String fill()       { return string("fill"); }
    public String stroke()     { return string("stroke"); }
    public String strokeWidth(){ return string("strokeWidth"); }
    public String material()   { return string("material"); }

    // Text semantic tokens (for label resolution)
    @SuppressWarnings("unchecked")
    public java.util.List<Text.SemanticToken> tokens() {
        Object v = props.get("tokens");
        if (v instanceof java.util.List<?> list) return (java.util.List<Text.SemanticToken>) list;
        return null;
    }
    public dev.everydaythings.graph.item.id.ItemID format() {
        Object v = props.get("format");
        return v instanceof dev.everydaythings.graph.item.id.ItemID id ? id : null;
    }

    // ==================================================================================
    // Building
    // ==================================================================================

    /**
     * Build resolved props from a node's base properties.
     */
    public static Builder from(Node node) {
        Builder b = new Builder();

        // Identity
        if (node.id() != null)      b.put("id", node.id());
        if (node.classes() != null)  b.put("classes", node.classes());

        // Box model
        if (node.width() != null)      b.put("width", node.width());
        if (node.height() != null)     b.put("height", node.height());
        if (node.padding() != null)    b.put("padding", node.padding());
        if (node.margin() != null)     b.put("margin", node.margin());
        if (node.border() != null)     b.put("border", node.border());
        if (node.corner() != null)     b.put("corner", node.corner());
        if (node.background() != null) b.put("background", node.background());
        if (node.overflow() != null)   b.put("overflow", node.overflow());

        // Typography
        if (node.fontFamily() != null) b.put("fontFamily", node.fontFamily());
        if (node.fontSize() != null)   b.put("fontSize", node.fontSize());
        if (node.fontWeight() != null) b.put("fontWeight", node.fontWeight());
        if (node.foreground() != null) b.put("foreground", node.foreground());
        if (node.opacity() != null)    b.put("opacity", node.opacity());

        // Transform
        if (node.elevation() != 0.0) b.put("elevation", node.elevation());
        if (node.scale() != 1.0)     b.put("scale", node.scale());
        if (node.rotation() != null) b.put("rotation", node.rotation());

        // Interaction
        if (node.editable()) b.put("editable", true);
        if (node.cursor() != null) b.put("cursor", node.cursor());

        // Type-specific
        if (node instanceof Container c) {
            if (c.layout() != null)  b.put("layout", c.layout());
            if (c.gap() != null)     b.put("gap", c.gap());
            if (c.align() != null)   b.put("align", c.align());
            if (c.justify() != null) b.put("justify", c.justify());
        } else if (node instanceof Text t) {
            if (t.text() != null) b.put("text", t.text());
            if (t.tokens() != null && !t.tokens().isEmpty()) b.put("tokens", t.tokens());
            if (t.format() != null) b.put("format", t.format());
        } else if (node instanceof Body bd) {
            if (bd.shape() != null)       b.put("shape", bd.shape());
            if (bd.image() != null)       b.put("image", bd.image());
            if (bd.model() != null)       b.put("model", bd.model());
            if (bd.glyph() != null)       b.put("glyph", bd.glyph());
            if (bd.alt() != null)         b.put("alt", bd.alt());
            if (bd.fill() != null)        b.put("fill", bd.fill());
            if (bd.stroke() != null)      b.put("stroke", bd.stroke());
            if (bd.strokeWidth() != null) b.put("strokeWidth", bd.strokeWidth());
            if (bd.material() != null)    b.put("material", bd.material());
        }

        return b;
    }

    public static class Builder {
        private final Map<String, Object> props = new HashMap<>();

        public Builder put(String key, Object value) {
            if (value != null) props.put(key, value);
            return this;
        }

        /**
         * Apply a when-block's overrides on top of current properties.
         */
        public Builder applyOverrides(Map<String, String> overrides) {
            if (overrides != null) props.putAll(overrides);
            return this;
        }

        public ResolvedProps build() {
            return new ResolvedProps(Map.copyOf(props));
        }
    }
}
