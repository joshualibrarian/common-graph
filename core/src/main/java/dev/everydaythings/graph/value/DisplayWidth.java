package dev.everydaythings.graph.value;

import dev.everydaythings.graph.value.Unit;

/**
 * Display width specification using proper units.
 *
 * <p>Types define their own display widths by declaring a static DISPLAY_WIDTH field:
 * <pre>{@code
 * public static final DisplayWidth DISPLAY_WIDTH = DisplayWidth.of(
 *     1, Unit.CHARACTER_WIDTH,   // min: 1 character (emoji)
 *     8, Unit.CHARACTER_WIDTH,   // pref: 8 characters (short hash)
 *     40, Unit.CHARACTER_WIDTH   // max: 40 characters (full hash)
 * );
 * }</pre>
 *
 * <p>The renderer converts these to pixels/cells based on context:
 * <ul>
 *   <li>TUI: ch = 1 cell, ln = 1 row</li>
 *   <li>GUI: ch ≈ 8-10px depending on font</li>
 * </ul>
 *
 * <p>Width modes:
 * <ul>
 *   <li>FIXED: Always uses preferred width (for icons, checkmarks)</li>
 *   <li>SHRINK: Can shrink to min but won't grow past pref</li>
 *   <li>GROW: Can grow to max when space available</li>
 *   <li>FLEX: Fully flexible - shrinks to min, grows to max</li>
 * </ul>
 */
public record DisplayWidth(
        double minValue, Unit minUnit,
        double prefValue, Unit prefUnit,
        double maxValue, Unit maxUnit,
        Mode mode
) {
    /**
     * How the width behaves when space is constrained or abundant.
     */
    public enum Mode {
        /** Fixed size - always preferred width */
        FIXED,
        /** Can shrink below preferred, won't grow */
        SHRINK,
        /** Won't shrink below preferred, can grow */
        GROW,
        /** Fully flexible - both shrinks and grows */
        FLEX
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a flexible width (shrinks and grows).
     */
    public static DisplayWidth of(double min, Unit minUnit,
                                   double pref, Unit prefUnit,
                                   double max, Unit maxUnit) {
        return new DisplayWidth(min, minUnit, pref, prefUnit, max, maxUnit, Mode.FLEX);
    }

    /**
     * Create a width with all values in the same unit.
     */
    public static DisplayWidth of(double min, double pref, double max, Unit unit) {
        return new DisplayWidth(min, unit, pref, unit, max, unit, Mode.FLEX);
    }

    /**
     * Create a fixed-width specification.
     */
    public static DisplayWidth fixed(double value, Unit unit) {
        return new DisplayWidth(value, unit, value, unit, value, unit, Mode.FIXED);
    }

    /**
     * Create a width that can shrink but not grow.
     */
    public static DisplayWidth shrinkable(double min, double pref, Unit unit) {
        return new DisplayWidth(min, unit, pref, unit, pref, unit, Mode.SHRINK);
    }

    /**
     * Create a width that can grow but not shrink.
     */
    public static DisplayWidth growable(double pref, double max, Unit unit) {
        return new DisplayWidth(pref, unit, pref, unit, max, unit, Mode.GROW);
    }

    // ==================================================================================
    // Common Presets
    // ==================================================================================

    /** Icon/emoji column - single character, fixed */
    public static final DisplayWidth ICON = fixed(1, Unit.CHARACTER_WIDTH);

    /** Boolean/checkbox - small fixed width */
    public static final DisplayWidth BOOLEAN = fixed(1, Unit.CHARACTER_WIDTH);

    /** Short numeric - small but can grow slightly */
    public static final DisplayWidth SHORT_NUMBER = of(2, 4, 8, Unit.CHARACTER_WIDTH);

    /** Standard number - moderate width */
    public static final DisplayWidth NUMBER = of(4, 8, 12, Unit.CHARACTER_WIDTH);

    /** Enum badge - small but shows full text when space allows */
    public static final DisplayWidth ENUM = of(1, 3, 12, Unit.CHARACTER_WIDTH);

    /** Count/size - just a number */
    public static final DisplayWidth COUNT = of(2, 4, 6, Unit.CHARACTER_WIDTH);

    /** Short text - name fields, labels */
    public static final DisplayWidth SHORT_TEXT = of(6, 15, 40, Unit.CHARACTER_WIDTH);

    /** Medium text - descriptions */
    public static final DisplayWidth MEDIUM_TEXT = of(10, 25, 80, Unit.CHARACTER_WIDTH);

    /** Long text - full content */
    public static final DisplayWidth LONG_TEXT = of(15, 40, 150, Unit.CHARACTER_WIDTH);

    /** Hash ID - emoji → short → medium → full */
    public static final DisplayWidth HASH_ID = of(2, 15, 50, Unit.CHARACTER_WIDTH);

    // ==================================================================================
    // Conversion
    // ==================================================================================

    /**
     * Convert to pixels for GUI rendering.
     *
     * @param charWidthPx Width of one character in pixels
     * @param lineHeightPx Height of one line in pixels
     * @return Array of [minPx, prefPx, maxPx]
     */
    public double[] toPixels(double charWidthPx, double lineHeightPx) {
        return new double[] {
            toPixels(minValue, minUnit, charWidthPx, lineHeightPx),
            toPixels(prefValue, prefUnit, charWidthPx, lineHeightPx),
            toPixels(maxValue, maxUnit, charWidthPx, lineHeightPx)
        };
    }

    private static double toPixels(double value, Unit unit, double charWidthPx, double lineHeightPx) {
        if (unit == Unit.CHARACTER_WIDTH) {
            return value * charWidthPx;
        } else if (unit == Unit.LINE_HEIGHT) {
            return value * lineHeightPx;
        } else if (unit == Unit.PIXEL) {
            return value;
        } else if (unit == Unit.PERCENT) {
            // Percent needs a reference - return as-is, caller handles
            return value;
        } else if (unit == Unit.EM || unit == Unit.REM) {
            // Approximate: 1em ≈ charWidth * 1.6 (assumes average char is 0.6em)
            return value * charWidthPx * 1.6;
        } else {
            // Fallback: treat as pixels
            return value;
        }
    }

    /**
     * Convert to character cells for TUI rendering.
     *
     * @return Array of [minCells, prefCells, maxCells]
     */
    public int[] toCells() {
        return new int[] {
            toCells(minValue, minUnit),
            toCells(prefValue, prefUnit),
            toCells(maxValue, maxUnit)
        };
    }

    private static int toCells(double value, Unit unit) {
        if (unit == Unit.CHARACTER_WIDTH) {
            return (int) Math.ceil(value);
        } else if (unit == Unit.LINE_HEIGHT) {
            // Line height doesn't apply to width - treat as 1
            return 1;
        } else if (unit == Unit.PIXEL) {
            // Approximate: 8px per cell
            return (int) Math.ceil(value / 8.0);
        } else if (unit == Unit.EM || unit == Unit.REM) {
            // 1em ≈ 1.6 cells
            return (int) Math.ceil(value * 1.6);
        } else {
            return (int) Math.ceil(value);
        }
    }

    /**
     * Check if this width can shrink below preferred.
     */
    public boolean canShrink() {
        return mode == Mode.SHRINK || mode == Mode.FLEX;
    }

    /**
     * Check if this width can grow above preferred.
     */
    public boolean canGrow() {
        return mode == Mode.GROW || mode == Mode.FLEX;
    }

    // ==================================================================================
    // Type Lookup
    // ==================================================================================

    /**
     * Get the DisplayWidth for a type by looking for a static DISPLAY_WIDTH field.
     *
     * <p>Types can define their preferred display width by declaring:
     * <pre>{@code
     * public static final DisplayWidth DISPLAY_WIDTH = DisplayWidth.of(1, 10, 40, Unit.CHARACTER_WIDTH);
     * }</pre>
     *
     * @param type The class to look up
     * @return The type's DisplayWidth, or null if not defined
     */
    public static DisplayWidth getForType(Class<?> type) {
        if (type == null) return null;

        try {
            var field = type.getField("DISPLAY_WIDTH");
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && DisplayWidth.class.isAssignableFrom(field.getType())) {
                return (DisplayWidth) field.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Type doesn't define DISPLAY_WIDTH - that's fine
        }

        // Check superclass
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            DisplayWidth inherited = getForType(superclass);
            if (inherited != null) return inherited;
        }

        // Check interfaces
        for (Class<?> iface : type.getInterfaces()) {
            DisplayWidth inherited = getForType(iface);
            if (inherited != null) return inherited;
        }

        return null;
    }

    /**
     * Get the DisplayWidth for a type, with a fallback default.
     *
     * @param type The class to look up
     * @param fallback Default to use if type doesn't define DISPLAY_WIDTH
     * @return The type's DisplayWidth, or the fallback
     */
    public static DisplayWidth getForType(Class<?> type, DisplayWidth fallback) {
        DisplayWidth found = getForType(type);
        return found != null ? found : fallback;
    }
}
