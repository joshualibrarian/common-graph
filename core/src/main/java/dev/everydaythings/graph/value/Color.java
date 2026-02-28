package dev.everydaythings.graph.value;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * Immutable RGBA color.
 *
 * <p>CG-native color representation.
 * Channels are stored as ints 0–255.
 *
 * <p>Factory methods mirror common patterns:
 * <pre>{@code
 * Color.rgb(75, 110, 175)          // opaque RGB
 * Color.rgba(130, 151, 105, 204)   // with alpha
 * Color.fromPacked(0x4B6EAF)       // from annotation int
 * Color.web("#4B6EAF")             // from hex string
 * }</pre>
 */
@Getter
@EqualsAndHashCode
public final class Color {

    private final int red;
    private final int green;
    private final int blue;
    private final int alpha;

    // ==================================================================================
    // Named constants
    // ==================================================================================

    public static final Color BLACK = new Color(0, 0, 0, 255);
    public static final Color WHITE = new Color(255, 255, 255, 255);

    // ==================================================================================
    // Construction
    // ==================================================================================

    private Color(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    /**
     * Create an opaque color from RGB components (0–255).
     */
    public static Color rgb(int r, int g, int b) {
        checkRange(r, "red");
        checkRange(g, "green");
        checkRange(b, "blue");
        return new Color(r, g, b, 255);
    }

    /**
     * Create a color from RGBA components (0–255).
     */
    public static Color rgba(int r, int g, int b, int a) {
        checkRange(r, "red");
        checkRange(g, "green");
        checkRange(b, "blue");
        checkRange(a, "alpha");
        return new Color(r, g, b, a);
    }

    /**
     * Create an opaque color from a packed 0xRRGGBB int
     * (the format used by {@code @Item.Type(color=...)}).
     */
    public static Color fromPacked(int rgb) {
        return new Color(
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF,
                255
        );
    }

    /**
     * Parse a CSS-style hex color string.
     *
     * <p>Accepted formats (leading '#' is optional):
     * <ul>
     *   <li>{@code #RGB} — expanded to RRGGBB</li>
     *   <li>{@code #RRGGBB} — opaque</li>
     *   <li>{@code #RRGGBBAA} — with alpha</li>
     * </ul>
     */
    public static Color web(String hex) {
        Objects.requireNonNull(hex, "hex");
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        return switch (s.length()) {
            case 3 -> {
                int r = Integer.parseInt(s.substring(0, 1), 16);
                int g = Integer.parseInt(s.substring(1, 2), 16);
                int b = Integer.parseInt(s.substring(2, 3), 16);
                yield new Color(r * 17, g * 17, b * 17, 255);
            }
            case 6 -> {
                int v = Integer.parseInt(s, 16);
                yield new Color((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, 255);
            }
            case 8 -> {
                long v = Long.parseLong(s, 16);
                yield new Color(
                        (int) ((v >> 24) & 0xFF),
                        (int) ((v >> 16) & 0xFF),
                        (int) ((v >> 8) & 0xFF),
                        (int) (v & 0xFF)
                );
            }
            default -> throw new IllegalArgumentException("Invalid hex color: " + hex);
        };
    }

    /**
     * Pack as 0xRRGGBB (alpha is dropped).
     */
    public int toPacked() {
        return (red << 16) | (green << 8) | blue;
    }

    // ==================================================================================
    // Accessors (double 0.0–1.0)
    // ==================================================================================

    public double redDouble() { return red / 255.0; }
    public double greenDouble() { return green / 255.0; }
    public double blueDouble() { return blue / 255.0; }
    public double alphaDouble() { return alpha / 255.0; }

    // ==================================================================================
    // Manipulation
    // ==================================================================================

    /**
     * Return a copy with the given alpha (0–255).
     */
    public Color withAlpha(int a) {
        checkRange(a, "alpha");
        return new Color(red, green, blue, a);
    }

    /**
     * Return a darker copy. Factor 0.0 = black, 1.0 = unchanged.
     */
    public Color darken(double factor) {
        if (factor < 0 || factor > 1)
            throw new IllegalArgumentException("factor must be 0.0–1.0, got " + factor);
        return new Color(
                (int) (red * factor),
                (int) (green * factor),
                (int) (blue * factor),
                alpha
        );
    }

    // ==================================================================================
    // ANSI Terminal
    // ==================================================================================

    /**
     * 24-bit ANSI foreground escape: {@code ESC[38;2;R;G;Bm}.
     */
    public String toAnsiForeground() {
        return String.format("\u001B[38;2;%d;%d;%dm", red, green, blue);
    }

    /**
     * 24-bit ANSI background escape: {@code ESC[48;2;R;G;Bm}.
     * Darkened to 40% for readability.
     */
    public String toAnsiBackground() {
        return String.format("\u001B[48;2;%d;%d;%dm",
                (int) (red * 0.4),
                (int) (green * 0.4),
                (int) (blue * 0.4));
    }

    /**
     * Returns {@code "#RRGGBB"} for opaque colors, {@code "#RRGGBBAA"} otherwise.
     */
    @Override
    public String toString() {
        if (alpha == 255) {
            return String.format("#%02X%02X%02X", red, green, blue);
        }
        return String.format("#%02X%02X%02X%02X", red, green, blue, alpha);
    }

    // ==================================================================================
    // Internal
    // ==================================================================================

    private static void checkRange(int value, String name) {
        if (value < 0 || value > 255)
            throw new IllegalArgumentException(name + " must be 0–255, got " + value);
    }
}
