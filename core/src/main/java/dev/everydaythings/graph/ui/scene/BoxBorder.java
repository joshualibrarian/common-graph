package dev.everydaythings.graph.ui.scene;

import java.util.regex.Pattern;

/**
 * Per-side border specification, modeled after CSS.
 *
 * <p>Each side has an independent style, width, and color. This allows
 * for mixed borders like "heavy top, light sides, no bottom."
 *
 * <h2>CSS Shorthand Parsing</h2>
 * <pre>
 * "1px solid blue"     → all sides: 1px solid blue
 * "2px dashed"         → all sides: 2px dashed, no color
 * "solid"              → all sides: solid, default width
 * "none"               → no border
 * </pre>
 *
 * <h2>Per-Side Construction</h2>
 * <pre>{@code
 * BoxBorder.builder()
 *     .top("2px solid red")
 *     .bottom("1px solid gray")
 *     .radius("4px")
 *     .build()
 * }</pre>
 */
public record BoxBorder(
        BorderSide top,
        BorderSide right,
        BorderSide bottom,
        BorderSide left,
        String radius
) {
    /** No border. */
    public static final BoxBorder NONE = new BoxBorder(
            BorderSide.NONE, BorderSide.NONE, BorderSide.NONE, BorderSide.NONE, "none");

    /**
     * A single side of a border.
     */
    public record BorderSide(String style, String width, String color) {

        /** No border side. */
        public static final BorderSide NONE = new BorderSide("none", "0", null);

        /** Whether this side is visible. */
        public boolean isVisible() {
            return style != null && !"none".equals(style) && !"0".equals(width);
        }

        /**
         * Parse a CSS border shorthand for a single side.
         *
         * <p>Accepts tokens in any order:
         * <ul>
         *   <li>Width: number+unit (e.g., "1px", "0.5em", "2ch")</li>
         *   <li>Style: "solid", "dashed", "double", "dotted", "none"</li>
         *   <li>Color: anything else (e.g., "blue", "red", "#333")</li>
         * </ul>
         *
         * @param shorthand CSS-like shorthand, e.g., "1px solid blue"
         * @return parsed BorderSide
         */
        public static BorderSide parse(String shorthand) {
            if (shorthand == null || shorthand.isBlank()) {
                return NONE;
            }
            shorthand = shorthand.trim();
            if ("none".equalsIgnoreCase(shorthand)) {
                return NONE;
            }

            String width = "1px";
            String style = "solid";
            String color = null;

            String[] tokens = shorthand.split("\\s+");
            for (String token : tokens) {
                if (isStyle(token)) {
                    style = token.toLowerCase();
                } else if (isWidth(token)) {
                    width = token;
                } else {
                    color = token;
                }
            }

            return new BorderSide(style, width, color);
        }

        private static final Pattern WIDTH_PATTERN = Pattern.compile(
                "^\\d+(\\.\\d+)?(px|em|ch|rem|ln|%)$", Pattern.CASE_INSENSITIVE);

        private static boolean isWidth(String token) {
            return WIDTH_PATTERN.matcher(token).matches() || "0".equals(token);
        }

        private static boolean isStyle(String token) {
            return switch (token.toLowerCase()) {
                case "none", "solid", "dashed", "double", "dotted" -> true;
                default -> false;
            };
        }
    }

    /** Whether any side has a visible border. */
    public boolean isVisible() {
        return top.isVisible() || right.isVisible()
                || bottom.isVisible() || left.isVisible();
    }

    /** Whether a border radius is specified. */
    public boolean hasRadius() {
        return radius != null && !"none".equalsIgnoreCase(radius) && !"0".equals(radius);
    }

    // ==================== Parsing ====================

    /**
     * Parse a CSS shorthand into a uniform border (all sides the same).
     *
     * @param shorthand e.g., "1px solid blue"
     * @return BoxBorder with all sides identical
     */
    public static BoxBorder parse(String shorthand) {
        return parse(shorthand, "none");
    }

    /**
     * Parse a CSS shorthand into a uniform border with radius.
     */
    public static BoxBorder parse(String shorthand, String radius) {
        if (shorthand == null || shorthand.isBlank() || "none".equalsIgnoreCase(shorthand.trim())) {
            return NONE;
        }
        BorderSide side = BorderSide.parse(shorthand);
        return new BoxBorder(side, side, side, side,
                radius != null ? radius : "none");
    }

    /**
     * Construct a border with explicit per-side shorthands.
     * Null sides default to no border.
     */
    public static BoxBorder of(String top, String right, String bottom, String left, String radius) {
        return new BoxBorder(
                top != null ? BorderSide.parse(top) : BorderSide.NONE,
                right != null ? BorderSide.parse(right) : BorderSide.NONE,
                bottom != null ? BorderSide.parse(bottom) : BorderSide.NONE,
                left != null ? BorderSide.parse(left) : BorderSide.NONE,
                radius != null ? radius : "none"
        );
    }

    /**
     * Resolve a BoxBorder from a combination of shorthand and per-side values.
     *
     * <p>Per-side values override the shorthand, matching CSS specificity rules.
     *
     * @param all    shorthand for all sides (from @Surface.Border.all)
     * @param top    per-side override
     * @param right  per-side override
     * @param bottom per-side override
     * @param left   per-side override
     * @param width  width shorthand (applied to all sides without explicit width)
     * @param style  style shorthand
     * @param color  color shorthand
     * @param radius border radius
     * @return resolved BoxBorder
     */
    public static BoxBorder resolve(String all, String top, String right, String bottom,
                                    String left, String width, String style, String color,
                                    String radius) {
        // Start with the "all" shorthand as base
        BorderSide base = isSet(all) ? BorderSide.parse(all) : BorderSide.NONE;

        // Apply width/style/color shorthands to base
        if (isSet(width) || isSet(style) || isSet(color)) {
            base = new BorderSide(
                    isSet(style) ? style : (base.isVisible() ? base.style() : "solid"),
                    isSet(width) ? width : (base.isVisible() ? base.width() : "1px"),
                    isSet(color) ? color : base.color()
            );
        }

        // Per-side overrides
        BorderSide topSide = isSet(top) ? BorderSide.parse(top) : base;
        BorderSide rightSide = isSet(right) ? BorderSide.parse(right) : base;
        BorderSide bottomSide = isSet(bottom) ? BorderSide.parse(bottom) : base;
        BorderSide leftSide = isSet(left) ? BorderSide.parse(left) : base;

        return new BoxBorder(topSide, rightSide, bottomSide, leftSide,
                isSet(radius) ? radius : "none");
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String all;
        private String top, right, bottom, left;
        private String width, style, color;
        private String radius = "none";

        /** Set all sides via CSS shorthand. */
        public Builder all(String shorthand) { this.all = shorthand; return this; }

        /** Set top side via CSS shorthand. */
        public Builder top(String shorthand) { this.top = shorthand; return this; }

        /** Set right side via CSS shorthand. */
        public Builder right(String shorthand) { this.right = shorthand; return this; }

        /** Set bottom side via CSS shorthand. */
        public Builder bottom(String shorthand) { this.bottom = shorthand; return this; }

        /** Set left side via CSS shorthand. */
        public Builder left(String shorthand) { this.left = shorthand; return this; }

        /** Set width for all sides. */
        public Builder width(String width) { this.width = width; return this; }

        /** Set style for all sides. */
        public Builder style(String style) { this.style = style; return this; }

        /** Set color for all sides. */
        public Builder color(String color) { this.color = color; return this; }

        /** Set border radius. */
        public Builder radius(String radius) { this.radius = radius; return this; }

        public BoxBorder build() {
            return BoxBorder.resolve(all, top, right, bottom, left,
                    width, style, color, radius);
        }
    }
}
