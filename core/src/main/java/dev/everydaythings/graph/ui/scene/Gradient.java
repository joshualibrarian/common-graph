package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.canonical.Order;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.canonical.Layout;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * A gradient fill — linear or radial, with color stops.
 *
 * <p>CBOR-serializable, carried on SceneNode as {@code backgroundGradient}.
 * The presenter resolves color stops from strings to integers.
 * The painter reads the resolved structure directly.
 */
@Getter
@Accessors(fluent = true)
@Layout
public class Gradient implements Canonical {

    /** "linear" or "radial". */
    @Order(0)
    private String type;

    /** Angle in degrees for linear gradients (0 = upward, 90 = rightward). */
    @Order(1)
    private float angle;

    /** Shape for radial gradients: "circle" or "ellipse". */
    @Order(2)
    private String shape;

    /** Center position for radial gradients: "center", "top left", "50% 50%". */
    @Order(3)
    private String position;

    /** Color stops along the gradient. */
    @Order(4)
    private List<ColorStop> stops;

    public Gradient() {}

    public Gradient type(String type) { this.type = type; return this; }
    public Gradient angle(float angle) { this.angle = angle; return this; }
    public Gradient shape(String shape) { this.shape = shape; return this; }
    public Gradient position(String position) { this.position = position; return this; }
    public Gradient stops(List<ColorStop> stops) { this.stops = stops; return this; }

    public Gradient addStop(Object color, float at) {
        if (stops == null) stops = new ArrayList<>();
        stops.add(new ColorStop().color(color).at(at));
        return this;
    }

    // ==================== Factories ====================

    public static Gradient linear(float angle) {
        return new Gradient().type("linear").angle(angle);
    }

    public static Gradient radial() {
        return new Gradient().type("radial").shape("circle").position("center");
    }

    // ==================== Color Stop ====================

    /**
     * A single color stop in a gradient.
     */
    @Getter
    @Accessors(fluent = true)
    @Layout
    public static class ColorStop implements Canonical {

        /** Color — String "#RRGGBB" → Integer 0xFFRRGGBB after presentation. */
        @Order(0)
        private Object color;

        /** Position along the gradient, 0-100 (percentage). */
        @Order(1)
        private float at;

        public ColorStop() {}

        public ColorStop color(Object color) { this.color = color; return this; }
        public ColorStop at(float at) { this.at = at; return this; }

        public int colorInt() { return SceneNode.asInt(color, -1); }
    }

    // ==================== CSS Parsing ====================

    /**
     * Parse a CSS gradient string into a Gradient.
     *
     * <p>Supports:
     * <ul>
     *   <li>{@code "linear-gradient(45deg, #000, #fff)"}</li>
     *   <li>{@code "linear-gradient(to right, red 0%, blue 100%)"}</li>
     *   <li>{@code "radial-gradient(circle, #000, #fff)"}</li>
     * </ul>
     *
     * @return parsed Gradient, or null if not a gradient string
     */
    public static Gradient parse(String css) {
        if (css == null) return null;
        String s = css.trim();

        if (s.startsWith("linear-gradient(") && s.endsWith(")")) {
            return parseLinear(s.substring("linear-gradient(".length(), s.length() - 1));
        }
        if (s.startsWith("radial-gradient(") && s.endsWith(")")) {
            return parseRadial(s.substring("radial-gradient(".length(), s.length() - 1));
        }
        return null;
    }

    private static Gradient parseLinear(String inner) {
        String[] parts = inner.split(",");
        if (parts.length < 2) return null;

        Gradient g = new Gradient().type("linear");

        // First part: angle or direction
        String first = parts[0].trim();
        if (first.endsWith("deg")) {
            g.angle(Float.parseFloat(first.substring(0, first.length() - 3).trim()));
        } else if (first.startsWith("to ")) {
            g.angle(directionToAngle(first.substring(3).trim()));
        } else {
            // First part is a color stop, no angle specified (default 180 = top to bottom)
            g.angle(180);
            return parseStops(g, parts, 0);
        }

        return parseStops(g, parts, 1);
    }

    private static Gradient parseRadial(String inner) {
        String[] parts = inner.split(",");
        if (parts.length < 2) return null;

        Gradient g = new Gradient().type("radial").position("center");

        String first = parts[0].trim();
        if (first.equals("circle") || first.equals("ellipse")) {
            g.shape(first);
            return parseStops(g, parts, 1);
        }
        if (first.startsWith("circle at ") || first.startsWith("ellipse at ")) {
            int atIdx = first.indexOf(" at ");
            g.shape(first.substring(0, atIdx).trim());
            g.position(first.substring(atIdx + 4).trim());
            return parseStops(g, parts, 1);
        }

        // Default shape
        g.shape("circle");
        return parseStops(g, parts, 0);
    }

    private static Gradient parseStops(Gradient g, String[] parts, int startIndex) {
        List<ColorStop> stops = new ArrayList<>();
        int count = parts.length - startIndex;
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i].trim();
            String[] tokens = part.split("\\s+");
            String color = tokens[0];
            float at;
            if (tokens.length > 1 && tokens[1].endsWith("%")) {
                at = Float.parseFloat(tokens[1].substring(0, tokens[1].length() - 1));
            } else {
                // Distribute evenly
                at = count > 1 ? ((i - startIndex) * 100f) / (count - 1) : 0;
            }
            stops.add(new ColorStop().color(color).at(at));
        }
        g.stops(stops);
        return g;
    }

    private static float directionToAngle(String direction) {
        return switch (direction) {
            case "top" -> 0;
            case "right" -> 90;
            case "bottom" -> 180;
            case "left" -> 270;
            case "top right" -> 45;
            case "bottom right" -> 135;
            case "bottom left" -> 225;
            case "top left" -> 315;
            default -> 180;
        };
    }
}
