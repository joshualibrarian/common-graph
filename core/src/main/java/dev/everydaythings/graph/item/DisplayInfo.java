package dev.everydaythings.graph.item;

import dev.everydaythings.graph.value.Color;

/**
 * Visual display metadata for Items and Components.
 *
 * <p>Types provide default DisplayInfo via {@link Item#displayInfo()}.
 * Instances can override to customize their appearance.
 *
 * <p>The display system uses this to render items consistently:
 * <ul>
 *   <li>{@link #name} - Human-readable name (shown prominently)</li>
 *   <li>{@link #typeName} - Type identifier (shown as subtitle/badge)</li>
 *   <li>{@link #color} - Primary color for icons, borders, accents</li>
 *   <li>{@link #iconText} - Single character, emoji, or short text for icon overlay</li>
 *   <li>{@link #shape} - Shape kind for the icon (SPHERE, CUBE, DISC)</li>
 *   <li>{@link #iconPath2D} - Path to 2D icon resource (optional)</li>
 *   <li>{@link #iconPath3D} - Path to 3D model resource (optional)</li>
 * </ul>
 *
 * <p>Shape meanings:
 * <ul>
 *   <li>{@link Shape#SPHERE} - Items (first-class entities with identity)</li>
 *   <li>{@link Shape#CUBE} - Components (building blocks, parts of items)</li>
 *   <li>{@link Shape#DISC} - Values/Data (atomic data, expressions)</li>
 * </ul>
 *
 * <p>Example type defaults:
 * <pre>{@code
 * // In Librarian.java
 * public static final DisplayInfo TYPE_DISPLAY = DisplayInfo.builder()
 *     .typeName("Librarian")
 *     .color(Color.rgb(75, 110, 175))
 *     .iconText("📚")
 *     .shape(Shape.SPHERE)
 *     .build();
 *
 * @Override
 * public DisplayInfo displayInfo() {
 *     return TYPE_DISPLAY.withName(getDisplayName());
 * }
 * }</pre>
 */
public record DisplayInfo(
        String name,
        String typeName,
        Color color,
        String iconText,
        Shape shape,
        String iconPath2D,
        String iconPath3D
) {

    /**
     * Shape kind for the icon.
     *
     * <p>Semantic meaning:
     * <ul>
     *   <li>SPHERE - Items (first-class entities, complete wholes)</li>
     *   <li>CUBE - Components (building blocks, structural parts)</li>
     *   <li>DISC - Values (data, expressions, atomic units)</li>
     * </ul>
     */
    public enum Shape {
        /** Sphere (3D) / Circle (2D) - for Items */
        SPHERE,
        /** Cube (3D) / Rounded Square (2D) - for Components */
        CUBE,
        /** Flat Disc/Cylinder (3D) / Pill/Oval (2D) - for Values */
        DISC
    }

    // ==================================================================================
    // Default for unknown items
    // ==================================================================================

    public static final DisplayInfo DEFAULT = new DisplayInfo(
            null,           // name (will use ID)
            "Item",         // typeName
            Color.rgb(120, 120, 140),  // neutral gray
            "?",            // iconText
            Shape.SPHERE,   // shape (default to item shape)
            null,           // iconPath2D
            null            // iconPath3D
    );

    // ==================================================================================
    // Builder for fluent construction
    // ==================================================================================

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a copy with a different name.
     */
    public DisplayInfo withName(String newName) {
        return new DisplayInfo(newName, typeName, color, iconText, shape, iconPath2D, iconPath3D);
    }

    /**
     * Create a copy with a different type name.
     */
    public DisplayInfo withTypeName(String newTypeName) {
        return new DisplayInfo(name, newTypeName, color, iconText, shape, iconPath2D, iconPath3D);
    }

    /**
     * Create a copy with a different color.
     */
    public DisplayInfo withColor(Color newColor) {
        return new DisplayInfo(name, typeName, newColor, iconText, shape, iconPath2D, iconPath3D);
    }

    /**
     * Create a copy with a different shape.
     */
    public DisplayInfo withShape(Shape newShape) {
        return new DisplayInfo(name, typeName, color, iconText, newShape, iconPath2D, iconPath3D);
    }

    /**
     * Get the display name, falling back to type name if no instance name.
     */
    public String displayName() {
        return name != null && !name.isBlank() ? name : typeName;
    }

    /**
     * Get the effective icon text (never null).
     */
    public String effectiveIconText() {
        return iconText != null ? iconText : "?";
    }

    /**
     * Get the effective color (never null).
     */
    public Color effectiveColor() {
        return color != null ? color : DEFAULT.color();
    }

    /**
     * Get the effective shape (never null).
     */
    public Shape effectiveShape() {
        return shape != null ? shape : Shape.SPHERE;
    }

    // ==================================================================================
    // ANSI Terminal Color Support
    // ==================================================================================

    /**
     * ANSI 24-bit foreground escape for this item's color.
     */
    public String toAnsiForeground() {
        return effectiveColor().toAnsiForeground();
    }

    /**
     * ANSI 24-bit background escape (darkened) for this item's color.
     */
    public String toAnsiBackground() {
        return effectiveColor().toAnsiBackground();
    }

    /**
     * ANSI 24-bit foreground escape for the given color.
     */
    public static String colorToAnsiForeground(Color color) {
        return color.toAnsiForeground();
    }

    /**
     * ANSI 24-bit background escape (darkened) for the given color.
     */
    public static String colorToAnsiBackground(Color color) {
        return color.toAnsiBackground();
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    public static class Builder {
        private String name;
        private String typeName = "Item";
        private Color color = DEFAULT.color();
        private String iconText = "?";
        private Shape shape = Shape.SPHERE;
        private String iconPath2D;
        private String iconPath3D;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder color(Color color) {
            this.color = color;
            return this;
        }

        public Builder color(int r, int g, int b) {
            this.color = Color.rgb(r, g, b);
            return this;
        }

        public Builder iconText(String iconText) {
            this.iconText = iconText;
            return this;
        }

        public Builder shape(Shape shape) {
            this.shape = shape;
            return this;
        }

        public Builder iconPath2D(String path) {
            this.iconPath2D = path;
            return this;
        }

        public Builder iconPath3D(String path) {
            this.iconPath3D = path;
            return this;
        }

        public DisplayInfo build() {
            return new DisplayInfo(name, typeName, color, iconText, shape, iconPath2D, iconPath3D);
        }
    }
}
