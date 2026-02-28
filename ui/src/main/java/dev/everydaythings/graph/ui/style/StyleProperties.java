package dev.everydaythings.graph.ui.style;

import java.util.*;
import java.util.OptionalInt;

/**
 * A set of style properties - the values that get applied to elements.
 *
 * <p>Properties are stored as a map of name → value. Values can be:
 * <ul>
 *   <li>Strings (colors, keywords)</li>
 *   <li>Numbers (sizes, opacities)</li>
 *   <li>Booleans (display: hidden)</li>
 * </ul>
 *
 * <h2>Common Properties</h2>
 * <ul>
 *   <li>{@code display} - "visible", "hidden"</li>
 *   <li>{@code opacity} - "dim", "normal", "bright" or 0.0-1.0</li>
 *   <li>{@code color} - color value</li>
 *   <li>{@code background} - background color or "reverse"</li>
 *   <li>{@code font-weight} - "normal", "bold"</li>
 *   <li>{@code text-decoration} - "none", "underline"</li>
 *   <li>{@code padding} - spacing values</li>
 *   <li>{@code content} - content override (e.g., "box-drawing")</li>
 *   <li>{@code icon} - icon/emoji override</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * StyleProperties props = StyleProperties.builder()
 *     .set("display", "visible")
 *     .set("opacity", "dim")
 *     .set("color", "#3b82f6")
 *     .build();
 *
 * if (props.isHidden()) { return; }
 * String color = props.getString("color").orElse("inherit");
 * }</pre>
 */
public class StyleProperties {

    /** Empty properties (inherit everything). */
    public static final StyleProperties EMPTY = new StyleProperties(Map.of());

    private final Map<String, Object> properties;

    private StyleProperties(Map<String, Object> properties) {
        this.properties = Map.copyOf(properties);
    }

    // ==================== Factories ====================

    public static Builder builder() {
        return new Builder();
    }

    public static StyleProperties of(String key, Object value) {
        return builder().set(key, value).build();
    }

    public static StyleProperties of(Map<String, Object> properties) {
        return new StyleProperties(properties);
    }

    // ==================== Accessors ====================

    public Optional<Object> get(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    public Optional<String> getString(String key) {
        return get(key).filter(v -> v instanceof String).map(v -> (String) v);
    }

    public Optional<Number> getNumber(String key) {
        return get(key).filter(v -> v instanceof Number).map(v -> (Number) v);
    }

    public Optional<Boolean> getBoolean(String key) {
        return get(key).filter(v -> v instanceof Boolean).map(v -> (Boolean) v);
    }

    public String getString(String key, String defaultValue) {
        return getString(key).orElse(defaultValue);
    }

    public boolean has(String key) {
        return properties.containsKey(key);
    }

    public Set<String> keys() {
        return properties.keySet();
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    // ==================== Common Property Shortcuts ====================

    /**
     * Check if display is hidden.
     */
    public boolean isHidden() {
        return "hidden".equals(properties.get("display"));
    }

    /**
     * Check if display is visible (not hidden).
     */
    public boolean isVisible() {
        return !isHidden();
    }

    /**
     * Get opacity as a keyword or number.
     */
    public Optional<String> getOpacity() {
        return getString("opacity");
    }

    /**
     * Check if opacity is "dim".
     */
    public boolean isDim() {
        return "dim".equals(properties.get("opacity"));
    }

    /**
     * Get color value.
     */
    public Optional<String> getColor() {
        return getString("color");
    }

    /**
     * Get background value.
     */
    public Optional<String> getBackground() {
        return getString("background");
    }

    /**
     * Check if background is "reverse" (inverted).
     */
    public boolean isReverse() {
        return "reverse".equals(properties.get("background"));
    }

    /**
     * Get font-weight.
     */
    public Optional<String> getFontWeight() {
        return getString("font-weight");
    }

    /**
     * Check if font-weight is "bold".
     */
    public boolean isBold() {
        return "bold".equals(properties.get("font-weight"));
    }

    /**
     * Get text-decoration.
     */
    public Optional<String> getTextDecoration() {
        return getString("text-decoration");
    }

    /**
     * Check if text-decoration includes "underline".
     */
    public boolean isUnderline() {
        return "underline".equals(properties.get("text-decoration"));
    }

    /**
     * Get content override.
     */
    public Optional<String> getContent() {
        return getString("content");
    }

    /**
     * Get icon override.
     */
    public Optional<String> getIcon() {
        return getString("icon");
    }

    // ==================== Typed Color/Font Accessors ====================

    /**
     * Get color as an ARGB int.
     * Stored under the "color-int" key to avoid collision with the string "color" key.
     */
    public OptionalInt getColorInt() {
        Object v = properties.get("color-int");
        if (v instanceof Integer i) return OptionalInt.of(i);
        return OptionalInt.empty();
    }

    /**
     * Get background color as an ARGB int.
     */
    public OptionalInt getBackgroundColorInt() {
        Object v = properties.get("background-color-int");
        if (v instanceof Integer i) return OptionalInt.of(i);
        return OptionalInt.empty();
    }

    /**
     * Get font size ratio (relative to base font size).
     * Returns 1.0 if not set.
     */
    public float getFontSizeRatio() {
        Object v = properties.get("font-size-ratio");
        if (v instanceof Number n) return n.floatValue();
        return 1.0f;
    }

    /**
     * Get font family ("monospace" or null for proportional).
     */
    public Optional<String> getFontFamily() {
        return getString("font-family");
    }

    // ==================== Border Properties ====================

    /** Get border shorthand (all sides). */
    public Optional<String> getBorder() { return getString("border"); }

    /** Get border-top. */
    public Optional<String> getBorderTop() { return getString("border-top"); }

    /** Get border-right. */
    public Optional<String> getBorderRight() { return getString("border-right"); }

    /** Get border-bottom. */
    public Optional<String> getBorderBottom() { return getString("border-bottom"); }

    /** Get border-left. */
    public Optional<String> getBorderLeft() { return getString("border-left"); }

    /** Get border-radius. */
    public Optional<String> getBorderRadius() { return getString("border-radius"); }

    /** Get border-width shorthand (all sides). */
    public Optional<String> getBorderWidth() { return getString("border-width"); }

    /** Get border-style shorthand (all sides). */
    public Optional<String> getBorderStyle() { return getString("border-style"); }

    /** Get border-color shorthand (all sides). */
    public Optional<String> getBorderColor() { return getString("border-color"); }

    // ==================== Size Properties ====================

    /** Get explicit width. */
    public Optional<String> getWidth() { return getString("width"); }

    /** Get explicit height. */
    public Optional<String> getHeight() { return getString("height"); }

    // ==================== Merging ====================

    /**
     * Merge with another set of properties.
     * The other properties override this one's values.
     */
    public StyleProperties merge(StyleProperties other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }

        Map<String, Object> merged = new HashMap<>(this.properties);
        merged.putAll(other.properties);
        return new StyleProperties(merged);
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StyleProperties that)) return false;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }

    // ==================== Builder ====================

    public static class Builder {
        private final Map<String, Object> properties = new LinkedHashMap<>();

        public Builder set(String key, Object value) {
            if (key != null && value != null) {
                properties.put(key, value);
            }
            return this;
        }

        public Builder display(String value) { return set("display", value); }
        public Builder hidden() { return display("hidden"); }
        public Builder visible() { return display("visible"); }

        public Builder opacity(String value) { return set("opacity", value); }
        public Builder dim() { return opacity("dim"); }
        public Builder bright() { return opacity("bright"); }

        public Builder color(String value) { return set("color", value); }
        public Builder background(String value) { return set("background", value); }
        public Builder reverse() { return background("reverse"); }

        public Builder fontWeight(String value) { return set("font-weight", value); }
        public Builder bold() { return fontWeight("bold"); }

        public Builder textDecoration(String value) { return set("text-decoration", value); }
        public Builder underline() { return textDecoration("underline"); }

        public Builder content(String value) { return set("content", value); }
        public Builder icon(String value) { return set("icon", value); }

        // Typed color/font builders
        public Builder colorInt(int argb) { return set("color-int", argb); }
        public Builder backgroundColorInt(int argb) { return set("background-color-int", argb); }
        public Builder fontSizeRatio(float ratio) { return set("font-size-ratio", ratio); }
        public Builder fontFamily(String family) { return set("font-family", family); }

        public Builder padding(int value) { return set("padding", value); }
        public Builder paddingLeft(int value) { return set("padding-left", value); }

        // Border builders
        public Builder border(String value) { return set("border", value); }
        public Builder borderTop(String value) { return set("border-top", value); }
        public Builder borderRight(String value) { return set("border-right", value); }
        public Builder borderBottom(String value) { return set("border-bottom", value); }
        public Builder borderLeft(String value) { return set("border-left", value); }
        public Builder borderRadius(String value) { return set("border-radius", value); }
        public Builder borderWidth(String value) { return set("border-width", value); }
        public Builder borderStyle(String value) { return set("border-style", value); }
        public Builder borderColor(String value) { return set("border-color", value); }

        // Size builders
        public Builder width(String value) { return set("width", value); }
        public Builder height(String value) { return set("height", value); }

        public StyleProperties build() {
            return new StyleProperties(properties);
        }
    }
}
