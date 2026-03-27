package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.value.Unit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes the rendering environment for condition evaluation and unit resolution.
 *
 * <p>RenderEnvironment carries everything a {@code when} condition, {@code visible}
 * binding, or size value might need: viewport dimensions, renderer type, capabilities,
 * librarian handle, and contextual unit measurements.
 *
 * <h2>Unit Resolution — One Pipeline</h2>
 *
 * <p>Units are Items. The librarian resolves unit symbols ("ch", "em") to Unit items.
 * Physical units (m, cm, in, px) have fixed conversion ratios on the Unit items
 * themselves. Contextual units (em, ch, ln, rem) depend on the renderer's current
 * font — the renderer provides these measurements via {@link #unitContext()}.
 *
 * <p>No RenderMetrics. No static conversion tables. The pipeline is:
 * <ol>
 *   <li>Parse "40ch" → SizeValue(40, "ch")</li>
 *   <li>Librarian resolves "ch" → CharacterWidth Unit item</li>
 *   <li>Look up CharacterWidth IID in unitContext → 8.5 pixels</li>
 *   <li>40 × 8.5 = 340 pixels</li>
 * </ol>
 *
 * <h2>Condition Language</h2>
 *
 * <h3>Tags (simple match)</h3>
 * <pre>
 * :skia  :filament  :tui  :cli  :web  :3d  :2d
 * @xs  @sm  @md  @lg  @xl
 * landscape  portrait
 * mouse  touch  color  images  spatial
 * </pre>
 *
 * <h3>Dimensional queries (any unit)</h3>
 * <pre>
 * viewport.width &gt;= 768px
 * viewport.width &lt; 40ch
 * viewport.height &gt; 10cm
 * viewport.width &gt;= 0.5m
 * </pre>
 *
 * <h3>Scalar queries</h3>
 * <pre>
 * dpi &gt; 192
 * devicePixelRatio &gt;= 2
 * </pre>
 */
public class RenderEnvironment {

    // ==================================================================================
    // Renderer Types
    // ==================================================================================

    public static final String SKIA = "skia";
    public static final String FILAMENT = "filament";
    public static final String TUI = "tui";
    public static final String CLI = "cli";
    public static final String WEB = "web";

    // ==================================================================================
    // Default Breakpoint Thresholds (pixels — overridable)
    // ==================================================================================

    public static final float DEFAULT_SM_THRESHOLD = 480f;
    public static final float DEFAULT_MD_THRESHOLD = 768f;
    public static final float DEFAULT_LG_THRESHOLD = 1024f;
    public static final float DEFAULT_XL_THRESHOLD = 1440f;

    // ==================================================================================
    // Dimensional Query Pattern
    // ==================================================================================

    private static final Pattern QUERY_PATTERN = Pattern.compile(
            "^(viewport\\.width|viewport\\.height|dpi|devicePixelRatio)\\s*([><=!]+)\\s*(.+)$");

    // ==================================================================================
    // Fields
    // ==================================================================================

    private final String renderer;
    private final float viewportWidth;
    private final float viewportHeight;
    private final float dpi;
    private final float devicePixelRatio;
    private final float baseFontSize;
    private final String orientation;
    private final String inputModality;
    private final Set<String> capabilities;
    private final String breakpoint;
    private final LibrarianHandle librarian;
    private final ItemID language;             // active language for label resolution

    /**
     * Contextual unit measurements — the renderer's actual pixel equivalents.
     *
     * <p>Keyed by Unit ItemID (e.g., {@code Unit.Em.IID → 15.0} means "1em = 15px
     * in this renderer"). Physical units don't need entries — they resolve via DPI.
     * Only contextual/font-dependent units go here.
     *
     * <p>The renderer populates this from its font metrics at construction time.
     */
    private final Map<ItemID, Double> unitContext;

    // Configurable breakpoint thresholds (pixels)
    private final float smThreshold;
    private final float mdThreshold;
    private final float lgThreshold;
    private final float xlThreshold;

    private RenderEnvironment(Builder b) {
        this.renderer = b.renderer;
        this.viewportWidth = b.viewportWidth;
        this.viewportHeight = b.viewportHeight;
        this.dpi = b.dpi;
        this.devicePixelRatio = b.devicePixelRatio;
        this.baseFontSize = b.baseFontSize;
        this.inputModality = b.inputModality;
        this.capabilities = b.capabilities.isEmpty() ? Set.of() : Set.copyOf(b.capabilities);
        this.librarian = b.librarian;
        this.language = b.language;
        this.unitContext = b.unitContext.isEmpty() ? Map.of() : Map.copyOf(b.unitContext);
        this.smThreshold = b.smThreshold;
        this.mdThreshold = b.mdThreshold;
        this.lgThreshold = b.lgThreshold;
        this.xlThreshold = b.xlThreshold;
        this.orientation = b.viewportWidth >= b.viewportHeight ? "landscape" : "portrait";
        this.breakpoint = b.breakpoint != null ? b.breakpoint : computeBreakpoint(b.viewportWidth);
    }

    // ==================================================================================
    // Unit Resolution — One Pipeline
    // ==================================================================================

    /**
     * Resolve a SizeValue to pixels.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Bare pixels: 1px = 1px</li>
     *   <li>Viewport-relative: vw, vh → viewport fraction</li>
     *   <li>Percentage: against a provided base</li>
     *   <li>Physical units: in, cm, mm, pt, m, km, ft → DPI conversion</li>
     *   <li>Contextual units: em, ch, ln, rem → unitContext lookup</li>
     *   <li>Librarian resolution: unknown symbol → resolve Unit item → convert</li>
     * </ol>
     *
     * @param sv  the parsed size value
     * @param base available dimension for percentage resolution (0 if not applicable)
     * @return resolved pixel value
     */
    public double resolveToPixels(SizeValue sv, double base) {
        if (sv == null || sv.isAuto()) return -1;
        if (sv.isPercentage()) return base * sv.value() / 100.0;

        String unit = sv.unit();

        // Viewport-relative
        if ("vw".equals(unit)) return sv.value() * viewportWidth / 100.0;
        if ("vh".equals(unit)) return sv.value() * viewportHeight / 100.0;

        // Physical units — fixed DPI-based conversion
        double physical = resolvePhysical(sv.value(), unit);
        if (!Double.isNaN(physical)) return physical;

        // Contextual units — look up in renderer's unit context
        double contextual = resolveContextual(sv.value(), unit);
        if (!Double.isNaN(contextual)) return contextual;

        // Last resort: treat as pixels
        return sv.value();
    }

    /** Convenience: resolve with no percentage base. */
    public double resolveToPixels(SizeValue sv) {
        return resolveToPixels(sv, 0);
    }

    /**
     * Resolve a size string to pixels.
     */
    public double resolveToPixels(String sizeExpr) {
        SizeValue sv = SizeValue.parse(sizeExpr);
        return resolveToPixels(sv, 0);
    }

    /**
     * Physical unit → logical pixels via DPI. Returns NaN if not a physical unit.
     * All results are in logical pixels (px). Renderers multiply by DPR for device pixels.
     */
    private double resolvePhysical(double value, String unit) {
        return switch (unit) {
            case "px"  -> value;                         // 1 px = 1 logical pixel (identity)
            case "dpx" -> value / devicePixelRatio;      // 1 dpx = 1/DPR logical pixels
            case "in"  -> value * 96;                    // 1 in = 96 px (by CSS definition)
            case "cm"  -> value * 96 / 2.54;
            case "mm"  -> value * 96 / 25.4;
            case "pt"  -> value * 96 / 72.0;
            case "m"   -> value * 39.3701 * 96;
            case "km"  -> value * 39370.1 * 96;
            case "ft"  -> value * 12.0 * 96;
            default -> Double.NaN;
        };
    }

    /**
     * Contextual unit → pixels via unitContext map + librarian.
     * Returns NaN if the unit can't be resolved.
     */
    private double resolveContextual(double value, String symbol) {
        // Try well-known contextual units by symbol
        ItemID unitId = resolveUnitId(symbol);
        if (unitId != null) {
            Double pxPerUnit = unitContext.get(unitId);
            if (pxPerUnit != null) return value * pxPerUnit;
        }

        // Try librarian resolution for unknown units
        if (librarian != null && unitId != null) {
            Unit resolvedUnit = Unit.lookupBySymbol(symbol);
            Unit pixel = Unit.lookupBySymbol("px");
            if (resolvedUnit != null && pixel != null && resolvedUnit.isCompatibleWith(pixel)) {
                try {
                    return resolvedUnit.convert(value, pixel);
                } catch (Exception ignored) {}
            }
        }

        return Double.NaN;
    }

    /**
     * Resolve a unit symbol to its ItemID. Uses well-known seeds first.
     */
    private static ItemID resolveUnitId(String symbol) {
        return switch (symbol) {
            case "em"  -> Unit.Em.IID;
            case "rem" -> Unit.Rem.IID;
            case "ch"  -> Unit.CharacterWidth.IID;
            case "ln"  -> Unit.LineHeight.IID;
            case "px"  -> Unit.Pixel.IID;
            case "dpx" -> Unit.DevicePixel.IID;
            case "%"   -> Unit.Percent.IID;
            case "fr"  -> Unit.Fraction.IID;
            default -> null;
        };
    }

    // ==================================================================================
    // Condition Matching
    // ==================================================================================

    /**
     * Test whether a condition matches this environment.
     *
     * @param condition the condition string
     * @return true if this environment satisfies the condition
     */
    public boolean matches(String condition) {
        if (condition == null || condition.isEmpty()) return true;

        // Renderer: :skia, :filament, :tui, :cli, :web, :3d, :2d
        if (condition.startsWith(":")) {
            String tag = condition.substring(1);
            return switch (tag) {
                case "3d" -> FILAMENT.equals(renderer);
                case "2d" -> !FILAMENT.equals(renderer) && !CLI.equals(renderer);
                default -> tag.equals(renderer);
            };
        }

        // Named breakpoint: @xs, @sm, @md, @lg, @xl (current or wider)
        if (condition.startsWith("@")) {
            String target = condition.substring(1);
            return breakpointOrdinal(breakpoint) >= breakpointOrdinal(target);
        }

        // Orientation
        if ("landscape".equals(condition) || "portrait".equals(condition)) {
            return condition.equals(orientation);
        }

        // Dimensional/scalar query: "viewport.width >= 768px"
        Matcher m = QUERY_PATTERN.matcher(condition.trim());
        if (m.matches()) {
            return evaluateQuery(m.group(1), m.group(2), m.group(3).trim());
        }

        // Capability: color, mouse, touch, images, spatial
        return capabilities.contains(condition);
    }

    private boolean evaluateQuery(String property, String operator, String valueExpr) {
        double lhs = resolveProperty(property);
        double rhs = resolveQueryValue(property, valueExpr);

        return switch (operator) {
            case ">=" -> lhs >= rhs;
            case "<=" -> lhs <= rhs;
            case ">"  -> lhs > rhs;
            case "<"  -> lhs < rhs;
            case "==" -> lhs == rhs;
            case "!=" -> lhs != rhs;
            default -> false;
        };
    }

    private double resolveProperty(String property) {
        return switch (property) {
            case "viewport.width" -> viewportWidth;
            case "viewport.height" -> viewportHeight;
            case "dpi" -> dpi;
            case "devicePixelRatio" -> devicePixelRatio;
            default -> 0;
        };
    }

    /**
     * Resolve the right-hand side of a query to the same units as the property.
     */
    private double resolveQueryValue(String property, String valueExpr) {
        // Scalar properties — bare number
        if ("dpi".equals(property) || "devicePixelRatio".equals(property)) {
            try {
                return Double.parseDouble(valueExpr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Dimensional — parse as SizeValue, convert to pixels
        SizeValue sv = SizeValue.parse(valueExpr);
        if (sv == null || sv.isAuto()) return 0;
        return resolveToPixels(sv, resolveProperty(property));
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public String renderer() { return renderer; }
    public float viewportWidth() { return viewportWidth; }
    public float viewportHeight() { return viewportHeight; }
    public float dpi() { return dpi; }
    public float devicePixelRatio() { return devicePixelRatio; }
    public float baseFontSize() { return baseFontSize; }
    public String orientation() { return orientation; }
    public String inputModality() { return inputModality; }
    public Set<String> capabilities() { return capabilities; }
    public String breakpoint() { return breakpoint; }
    public LibrarianHandle librarian() { return librarian; }
    public ItemID language() { return language; }
    public Map<ItemID, Double> unitContext() { return unitContext; }

    public boolean hasCapability(String cap) { return capabilities.contains(cap); }
    public boolean isSpatial() { return FILAMENT.equals(renderer); }
    public boolean isGraphical() { return SKIA.equals(renderer) || FILAMENT.equals(renderer) || WEB.equals(renderer); }
    public boolean isText() { return TUI.equals(renderer) || CLI.equals(renderer); }

    // ==================================================================================
    // Breakpoint Computation
    // ==================================================================================

    private String computeBreakpoint(float vpWidth) {
        if (vpWidth < smThreshold) return "xs";
        if (vpWidth < mdThreshold) return "sm";
        if (vpWidth < lgThreshold) return "md";
        if (vpWidth < xlThreshold) return "lg";
        return "xl";
    }

    private static int breakpointOrdinal(String bp) {
        return switch (bp) {
            case "xs" -> 0;
            case "sm" -> 1;
            case "md" -> 2;
            case "lg" -> 3;
            case "xl" -> 4;
            default -> -1;
        };
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String renderer = TUI;
        private float viewportWidth = 80;
        private float viewportHeight = 24;
        private float dpi = 96;
        private float devicePixelRatio = 1.0f;
        private float baseFontSize = 16f;
        private String inputModality = "keyboard";
        private final java.util.HashSet<String> capabilities = new java.util.HashSet<>();
        private final Map<ItemID, Double> unitContext = new HashMap<>();
        private String breakpoint;
        private LibrarianHandle librarian;
        private ItemID language;
        private float smThreshold = DEFAULT_SM_THRESHOLD;
        private float mdThreshold = DEFAULT_MD_THRESHOLD;
        private float lgThreshold = DEFAULT_LG_THRESHOLD;
        private float xlThreshold = DEFAULT_XL_THRESHOLD;

        public Builder renderer(String r) { this.renderer = r; return this; }
        public Builder viewportWidth(float w) { this.viewportWidth = w; return this; }
        public Builder viewportHeight(float h) { this.viewportHeight = h; return this; }
        public Builder dpi(float d) { this.dpi = d; return this; }
        public Builder devicePixelRatio(float r) { this.devicePixelRatio = r; return this; }
        public Builder baseFontSize(float s) { this.baseFontSize = s; return this; }
        public Builder inputModality(String m) { this.inputModality = m; return this; }
        public Builder breakpoint(String bp) { this.breakpoint = bp; return this; }
        public Builder librarian(LibrarianHandle lib) { this.librarian = lib; return this; }
        public Builder language(ItemID lang) { this.language = lang; return this; }

        /**
         * Register a contextual unit measurement — "in this renderer, 1 of this unit = N pixels."
         *
         * <p>Call this for each font-dependent unit the renderer measures:
         * <pre>{@code
         * .unit(Unit.Em.IID, 15.0)         // 1em = 15px (from font metrics)
         * .unit(Unit.CharacterWidth.IID, 8.5)  // 1ch = 8.5px
         * .unit(Unit.LineHeight.IID, 20.0)  // 1ln = 20px
         * }</pre>
         */
        public Builder unit(ItemID unitId, double pixelsPerUnit) {
            this.unitContext.put(unitId, pixelsPerUnit);
            return this;
        }

        /** Set named breakpoint thresholds in pixels. */
        public Builder breakpointThresholds(float sm, float md, float lg, float xl) {
            this.smThreshold = sm;
            this.mdThreshold = md;
            this.lgThreshold = lg;
            this.xlThreshold = xl;
            return this;
        }

        public Builder capability(String cap) {
            if (cap != null) capabilities.add(cap);
            return this;
        }

        public Builder capabilities(String... caps) {
            for (String c : caps) if (c != null) capabilities.add(c);
            return this;
        }

        public RenderEnvironment build() { return new RenderEnvironment(this); }
    }
}
