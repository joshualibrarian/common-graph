package dev.everydaythings.graph.value;

import dev.everydaythings.graph.value.DisplayWidth;
import lombok.Getter;

/**
 * A normalized decimal: value = unscaled * 10^-scale
 *
 * <p>Normalization:
 * <ul>
 *   <li>scale is adjusted so unscaled has no trailing base-10 zeros</li>
 *   <li>0 is always represented as (0, 0)</li>
 * </ul>
 *
 * <p>This is identity-safe (no float rounding surprises), compact,
 * and suitable for quantities with units.
 */
@Getter
@Value.Type("cg.value:decimal")
public final class Decimal implements Numeric {

    /** Display width: decimal numbers are typically 4-12 characters */
    public static final DisplayWidth DISPLAY_WIDTH = DisplayWidth.of(3, 8, 20, Unit.CharacterWidth.SEED);

    @Canon(order = 1)
    private final long unscaled;

    @Canon(order = 2)
    private final int scale;

    public Decimal(long unscaled, int scale) {
        long u = unscaled;
        int s = scale;

        if (u == 0) {
            this.unscaled = 0;
            this.scale = 0;
            return;
        }

        // normalize trailing zeros in base 10
        while (s > 0 && (u % 10L) == 0L) {
            u /= 10L;
            s -= 1;
        }
        this.unscaled = u;
        this.scale = s;
    }

    public static Decimal of(long unscaled, int scale) {
        return new Decimal(unscaled, scale);
    }

    public static Decimal ofLong(long v) {
        return new Decimal(v, 0);
    }

    public static Decimal ofInt(int v) {
        return new Decimal(v, 0);
    }

    /**
     * Parse a decimal string like "3.14159" or "-42" or "1.5e-3".
     */
    public static Decimal parse(String s) {
        s = s.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Empty decimal string");

        // Handle scientific notation
        int eIdx = s.indexOf('e');
        if (eIdx < 0) eIdx = s.indexOf('E');

        int exponent = 0;
        if (eIdx >= 0) {
            exponent = Integer.parseInt(s.substring(eIdx + 1));
            s = s.substring(0, eIdx);
        }

        // Find decimal point
        int dotIdx = s.indexOf('.');
        if (dotIdx < 0) {
            // No decimal point - integer
            long unscaled = Long.parseLong(s);
            int scale = -exponent;
            return new Decimal(unscaled, Math.max(0, scale));
        }

        // Has decimal point
        String intPart = s.substring(0, dotIdx);
        String fracPart = s.substring(dotIdx + 1);

        String combined = intPart + fracPart;
        long unscaled = Long.parseLong(combined);
        int scale = fracPart.length() - exponent;

        return new Decimal(unscaled, Math.max(0, scale));
    }

    @Override
    public String token() {
        if (scale == 0) {
            return String.valueOf(unscaled);
        }

        // Convert to decimal string representation
        String digits = String.valueOf(Math.abs(unscaled));
        boolean negative = unscaled < 0;

        if (scale >= digits.length()) {
            // Need leading zeros: 0.00123
            StringBuilder sb = new StringBuilder();
            if (negative) sb.append('-');
            sb.append("0.");
            for (int i = 0; i < scale - digits.length(); i++) {
                sb.append('0');
            }
            sb.append(digits);
            return sb.toString();
        } else {
            // Insert decimal point: 123.45
            int insertPoint = digits.length() - scale;
            StringBuilder sb = new StringBuilder();
            if (negative) sb.append('-');
            sb.append(digits, 0, insertPoint);
            sb.append('.');
            sb.append(digits, insertPoint, digits.length());
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        return token();
    }

    // ==================================================================================
    // Renderable Implementation
    // ==================================================================================

    @Override
    public String emoji() {
        return "#";  // Numeric
    }

    @Override
    public String colorCategory() {
        return "value";
    }

    /**
     * Convert to double (may lose precision).
     */
    public double toDouble() {
        return unscaled * Math.pow(10, -scale);
    }

    // No-arg constructor for Canonical decoding
    @SuppressWarnings("unused")
    private Decimal() {
        this.unscaled = 0;
        this.scale = 0;
    }
}
