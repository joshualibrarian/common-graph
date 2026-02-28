package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.value.Unit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed size value with unit, e.g., "40ch", "200px", "2em".
 *
 * <p>SizeValue is the bridge between the CSS-like size strings used in
 * annotations and stylesheets, and the concrete pixel/character measurements
 * needed by renderers.
 *
 * <p>All conversions require a {@link RenderContext} which provides:
 * <ul>
 *   <li>{@link RenderContext#resolveUnit(String)} to resolve the unit symbol
 *       (e.g., "ch") to a {@link Unit} item via the graph's token dictionary</li>
 *   <li>A {@link RenderMetrics} with conversion tables keyed by Unit ItemID</li>
 * </ul>
 *
 * <p>There are no fallback conversions. If the unit can't be resolved
 * or the metrics don't have a mapping, it fails hard. The seed vocabulary
 * guarantees standard units exist — if they don't, the system is broken.
 *
 * <h2>Supported Units</h2>
 * <ul>
 *   <li>{@code px} — device pixels (96px = 1 inch by convention)</li>
 *   <li>{@code em} — font size of the current element</li>
 *   <li>{@code ch} — width of the "0" character (1 column in monospace)</li>
 *   <li>{@code rem} — font size of the root element</li>
 *   <li>{@code ln} — line height (1 row in terminal)</li>
 *   <li>{@code %} — percentage of parent dimension</li>
 * </ul>
 */
public record SizeValue(double value, String unit) {

    /** Explicit shrink-to-content sizing. */
    public static final SizeValue AUTO = new SizeValue(0, "auto");

    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "^(-?\\d+(?:\\.\\d+)?)(px|em|ch|rem|ln|%|vw|vh|in|cm|mm|km|m|ft|pt)$", Pattern.CASE_INSENSITIVE);

    /** True if this is an auto-sized value (shrink-to-content). */
    public boolean isAuto() { return "auto".equals(unit); }

    /** Check if a raw size spec string represents auto sizing. */
    public static boolean isAutoSpec(String spec) {
        return "auto".equalsIgnoreCase(spec != null ? spec.trim() : null);
    }

    /**
     * Parse a size string. Bare numbers default to pixels.
     *
     * <p>Parsing is pure string → value + unit symbol. No resolver needed.
     *
     * @param s size string like "40ch", "200px", "2em", "auto"
     * @return parsed SizeValue, or null if unparseable
     */
    public static SizeValue parse(String s) {
        return parse(s, "px");
    }

    /**
     * Parse a size string with a custom default unit for bare numbers.
     *
     * @param s           size string like "40ch", "200px", "2m", "auto"
     * @param defaultUnit unit to use when the string is a bare number (e.g., "m", "px")
     * @return parsed SizeValue, or null if unparseable
     */
    public static SizeValue parse(String s, String defaultUnit) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();

        // Auto keyword — explicit shrink-to-content
        if ("auto".equalsIgnoreCase(s)) return AUTO;

        // Plain number → treat as defaultUnit
        try {
            double val = Double.parseDouble(s);
            return new SizeValue(val, defaultUnit);
        } catch (NumberFormatException ignored) {}

        Matcher m = SIZE_PATTERN.matcher(s);
        if (m.matches()) {
            double val = Double.parseDouble(m.group(1));
            String unit = m.group(2).toLowerCase();
            return new SizeValue(val, unit);
        }
        return null;
    }

    /** True if this is a percentage unit (requires parent dimension for resolution). */
    public boolean isPercentage() { return "%".equals(unit); }

    /** True if this uses viewport-relative units (vw or vh). */
    public boolean isViewportRelative() { return "vw".equals(unit) || "vh".equals(unit); }

    /**
     * Convert to TUI character columns.
     *
     * <p>Resolves the unit symbol through the graph via the context's
     * LibrarianHandle, then converts using the context's RenderMetrics.
     *
     * @param ctx render context with resolver and metrics
     * @return column count (ceiling)
     */
    public int toColumns(RenderContext ctx) {
        if (isAuto()) return -1;
        Unit resolved = ctx.resolveUnit(unit);
        return (int) Math.ceil(ctx.renderMetrics().toColumns(resolved, value));
    }

    /**
     * Convert to TUI rows (line count).
     *
     * @param ctx render context with resolver and metrics
     * @return row count (ceiling)
     */
    public int toRows(RenderContext ctx) {
        if (isAuto()) return -1;
        Unit resolved = ctx.resolveUnit(unit);
        return (int) Math.ceil(ctx.renderMetrics().toRows(resolved, value));
    }

    /**
     * Convert to pixels.
     *
     * <p>Handles viewport-relative units (vw, vh), physical units (in, cm, mm, m, km, ft, pt),
     * and contextual units (px, em, ch, rem, ln, %). Percentage passes through as
     * an identity value (50% → 50.0) — the layout engine resolves against parent
     * dimensions where needed.
     *
     * @param ctx render context with resolver and metrics
     * @return pixel value
     */
    public double toPixels(RenderContext ctx) {
        if (isAuto()) return -1;

        // Viewport-relative units
        if ("vw".equals(unit)) return value * ctx.viewportWidth() / 100.0;
        if ("vh".equals(unit)) return value * ctx.viewportHeight() / 100.0;

        // Physical units — hardcoded DPI-based conversion (always available,
        // doesn't require graph resolution)
        if ("in".equals(unit)) return value * ctx.dpi();
        if ("cm".equals(unit)) return value * ctx.dpi() / 2.54;
        if ("mm".equals(unit)) return value * ctx.dpi() / 25.4;
        if ("pt".equals(unit)) return value * ctx.dpi() / 72.0;
        if ("m".equals(unit))  return value * 39.3701 * ctx.dpi();
        if ("km".equals(unit)) return value * 39370.1 * ctx.dpi();
        if ("ft".equals(unit)) return value * 12.0 * ctx.dpi();

        // Contextual units (px, em, ch, rem, ln, %) — resolve via graph
        Unit resolved = ctx.resolveUnit(unit);
        return ctx.renderMetrics().toPixels(resolved, value);
    }

    /**
     * Convert to meters via the graph's Unit system.
     *
     * <p>Resolves the unit symbol through the graph via the context's
     * LibrarianHandle, then converts to meters using {@link Unit#convert}.
     *
     * @param ctx render context with resolver
     * @return value in meters
     */
    public double toMeters(RenderContext ctx) {
        if (isAuto()) return 0;
        Unit resolved = ctx.resolveUnit(unit);
        Unit meter = ctx.resolveUnit("m");
        return resolved.convert(value, meter);
    }

    @Override
    public String toString() {
        if (value == (long) value) {
            return (long) value + unit;
        }
        return value + unit;
    }
}
