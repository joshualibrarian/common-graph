package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.value.Unit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for style resolution - describes the current rendering environment.
 *
 * <p>RenderContext carries information about:
 * <ul>
 *   <li><b>Renderer type</b> - "tui", "gui", "space"</li>
 *   <li><b>Breakpoint</b> - current size category ("sm", "md", "lg")</li>
 *   <li><b>Active states</b> - currently active pseudo-classes ("hover", "selected", etc.)</li>
 *   <li><b>Capabilities</b> - what the renderer supports ("color", "mouse", "images")</li>
 *   <li><b>Librarian</b> - for resolving unit symbols to Unit items via the graph</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RenderContext ctx = RenderContext.builder()
 *     .renderer("tui")
 *     .breakpoint("md")
 *     .addState("selected")
 *     .addCapability("color")
 *     .librarian(librarianHandle)
 *     .build();
 *
 * // Or use presets
 * RenderContext ctx = RenderContext.tui(librarianHandle);
 * RenderContext ctx = RenderContext.gui(librarianHandle);
 * }</pre>
 */
public class RenderContext {

    /** TUI renderer type. */
    public static final String RENDERER_TUI = "tui";

    /** GUI renderer type. */
    public static final String RENDERER_GUI = "gui";

    /** Skia renderer type. */
    public static final String RENDERER_SKIA = "skia";

    /** Space (3D) renderer type. */
    public static final String RENDERER_SPACE = "space";

    /** Small breakpoint. */
    public static final String BREAKPOINT_SM = "sm";

    /** Medium breakpoint. */
    public static final String BREAKPOINT_MD = "md";

    /** Large breakpoint. */
    public static final String BREAKPOINT_LG = "lg";

    /**
     * Shared in-memory LibrarianHandle for convenience presets and tests.
     *
     * <p>Production code should always pass a real LibrarianHandle via
     * the builder or parameterized preset factories.
     */
    private static volatile LibrarianHandle sharedLibrarian;

    private static LibrarianHandle sharedLibrarian() {
        if (sharedLibrarian == null) {
            synchronized (RenderContext.class) {
                if (sharedLibrarian == null) {
                    sharedLibrarian = LibrarianHandle.inMemory();
                }
            }
        }
        return sharedLibrarian;
    }

    private final String renderer;
    private final String breakpoint;
    private final Set<String> states;
    private final Set<String> capabilities;
    private final LibrarianHandle librarian;
    private final RenderMetrics renderMetrics;
    private final float baseFontSize;       // default 16f
    private final float viewportWidth;      // -1 = unknown
    private final float viewportHeight;     // -1 = unknown
    private final float dpi;                // default 96 (CSS reference pixel)
    private final float devicePixelRatio;   // default 1.0
    private final Map<String, Unit> unitCache = new ConcurrentHashMap<>();

    private RenderContext(Builder builder) {
        this.renderer = builder.renderer;
        this.breakpoint = builder.breakpoint;
        this.states = builder.states.isEmpty() ? Set.of() : Set.copyOf(builder.states);
        this.capabilities = builder.capabilities.isEmpty() ? Set.of() : Set.copyOf(builder.capabilities);
        this.librarian = builder.librarian;
        this.renderMetrics = builder.renderMetrics;
        this.baseFontSize = builder.baseFontSize;
        this.viewportWidth = builder.viewportWidth;
        this.viewportHeight = builder.viewportHeight;
        this.dpi = builder.dpi;
        this.devicePixelRatio = builder.devicePixelRatio;
    }

    // ==================== Unit Resolution ====================

    /**
     * Resolve a unit symbol (e.g., "ch", "em", "px") to its {@link Unit} item
     * via the graph's token dictionary.
     *
     * <p>Results are cached for performance.
     *
     * @param symbol the unit symbol to resolve
     * @return the resolved Unit item
     * @throws IllegalArgumentException if the symbol cannot be resolved
     */
    public Unit resolveUnit(String symbol) {
        LibrarianHandle handle = librarian != null ? librarian : sharedLibrarian();
        return unitCache.computeIfAbsent(symbol, sym ->
            handle.lookup(sym)
                .map(posting -> handle.get(posting.target(), Unit.class).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown unit: " + sym))
        );
    }

    // ==================== Presets ====================

    /**
     * Create a TUI render context using the shared in-memory Librarian.
     *
     * <p>Convenience for tests and simple usage. Production code should
     * use {@link #tui(LibrarianHandle)} with the session's real Librarian.
     */
    public static RenderContext tui() {
        return tui(sharedLibrarian());
    }

    /**
     * Create a TUI render context with typical settings.
     */
    public static RenderContext tui(LibrarianHandle librarian) {
        return builder()
                .renderer(RENDERER_TUI)
                .breakpoint(BREAKPOINT_MD)
                .addCapability("color")
                .librarian(librarian)
                .renderMetrics(RenderMetrics.TUI_DEFAULT)
                .build();
    }

    /** Convenience overload using the shared in-memory Librarian. */
    public static RenderContext gui() {
        return gui(sharedLibrarian());
    }

    /**
     * Create a GUI render context with typical settings.
     */
    public static RenderContext gui(LibrarianHandle librarian) {
        return builder()
                .renderer(RENDERER_GUI)
                .breakpoint(BREAKPOINT_LG)
                .addCapability("color")
                .addCapability("mouse")
                .addCapability("images")
                .librarian(librarian)
                .renderMetrics(RenderMetrics.FX_DEFAULT)
                .build();
    }

    /** Convenience overload using the shared in-memory Librarian. */
    public static RenderContext skia() {
        return skia(sharedLibrarian());
    }

    /**
     * Create a Skia render context with typical settings.
     * Same capabilities as GUI but uses the Skia renderer.
     */
    public static RenderContext skia(LibrarianHandle librarian) {
        return builder()
                .renderer(RENDERER_SKIA)
                .breakpoint(BREAKPOINT_LG)
                .addCapability("color")
                .addCapability("mouse")
                .addCapability("images")
                .librarian(librarian)
                .renderMetrics(RenderMetrics.SKIA_DEFAULT)
                .build();
    }

    /**
     * Create a Skia render context with viewport dimensions and device pixel ratio.
     */
    public static RenderContext skia(LibrarianHandle librarian, float viewportW, float viewportH, float dpr) {
        return builder()
                .renderer(RENDERER_SKIA)
                .breakpoint(BREAKPOINT_LG)
                .viewportWidth(viewportW)
                .viewportHeight(viewportH)
                .devicePixelRatio(dpr)
                .dpi(96 * dpr)
                .addCapability("color")
                .addCapability("mouse")
                .addCapability("images")
                .librarian(librarian)
                .renderMetrics(RenderMetrics.SKIA_DEFAULT)
                .build();
    }

    /** Convenience overload using the shared in-memory Librarian. */
    public static RenderContext space() {
        return space(sharedLibrarian());
    }

    /**
     * Create a Space (3D) render context with typical settings.
     */
    public static RenderContext space(LibrarianHandle librarian) {
        return builder()
                .renderer(RENDERER_SPACE)
                .breakpoint(BREAKPOINT_LG)
                .addCapability("color")
                .addCapability("mouse")
                .addCapability("images")
                .addCapability("3d")
                .librarian(librarian)
                .renderMetrics(RenderMetrics.FX_DEFAULT)
                .build();
    }

    // ==================== Factories ====================

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new context with an additional state.
     */
    public RenderContext withState(String state) {
        Set<String> newStates = new HashSet<>(this.states);
        newStates.add(state);
        return builder()
                .renderer(renderer)
                .breakpoint(breakpoint)
                .states(newStates)
                .capabilities(capabilities)
                .librarian(librarian)
                .renderMetrics(renderMetrics)
                .baseFontSize(baseFontSize)
                .viewportWidth(viewportWidth)
                .viewportHeight(viewportHeight)
                .dpi(dpi)
                .devicePixelRatio(devicePixelRatio)
                .build();
    }

    /**
     * Create a new context without a state.
     */
    public RenderContext withoutState(String state) {
        Set<String> newStates = new HashSet<>(this.states);
        newStates.remove(state);
        return builder()
                .renderer(renderer)
                .breakpoint(breakpoint)
                .states(newStates)
                .capabilities(capabilities)
                .librarian(librarian)
                .renderMetrics(renderMetrics)
                .baseFontSize(baseFontSize)
                .viewportWidth(viewportWidth)
                .viewportHeight(viewportHeight)
                .dpi(dpi)
                .devicePixelRatio(devicePixelRatio)
                .build();
    }

    /**
     * Create a new context with a different breakpoint.
     */
    public RenderContext withBreakpoint(String breakpoint) {
        return builder()
                .renderer(renderer)
                .breakpoint(breakpoint)
                .states(states)
                .capabilities(capabilities)
                .librarian(librarian)
                .renderMetrics(renderMetrics)
                .baseFontSize(baseFontSize)
                .viewportWidth(viewportWidth)
                .viewportHeight(viewportHeight)
                .dpi(dpi)
                .devicePixelRatio(devicePixelRatio)
                .build();
    }

    /**
     * Create a new context with a different base font size.
     *
     * <p>Does NOT auto-regenerate RenderMetrics — the caller is responsible
     * for providing measured metrics since only the renderer knows its
     * actual font measurements.
     */
    public RenderContext withFontSize(float fontSize) {
        return builder()
                .renderer(renderer)
                .breakpoint(breakpoint)
                .states(states)
                .capabilities(capabilities)
                .librarian(librarian)
                .renderMetrics(renderMetrics)
                .baseFontSize(fontSize)
                .viewportWidth(viewportWidth)
                .viewportHeight(viewportHeight)
                .dpi(dpi)
                .devicePixelRatio(devicePixelRatio)
                .build();
    }

    // ==================== Accessors ====================

    public String renderer() { return renderer; }
    public String breakpoint() { return breakpoint; }
    public Set<String> states() { return states; }
    public Set<String> capabilities() { return capabilities; }
    public LibrarianHandle librarian() { return librarian; }
    public RenderMetrics renderMetrics() { return renderMetrics; }
    public float baseFontSize() { return baseFontSize; }
    public float viewportWidth() { return viewportWidth; }
    public float viewportHeight() { return viewportHeight; }
    public float dpi() { return dpi; }
    public float devicePixelRatio() { return devicePixelRatio; }

    public boolean isTui() { return RENDERER_TUI.equals(renderer); }
    public boolean isGui() { return RENDERER_GUI.equals(renderer); }
    public boolean isSkia() { return RENDERER_SKIA.equals(renderer); }
    public boolean isSpace() { return RENDERER_SPACE.equals(renderer); }

    public boolean hasState(String state) { return states.contains(state); }
    public boolean hasCapability(String capability) { return capabilities.contains(capability); }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RenderContext that)) return false;
        return Objects.equals(renderer, that.renderer)
            && Objects.equals(breakpoint, that.breakpoint)
            && Objects.equals(states, that.states)
            && Objects.equals(capabilities, that.capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderer, breakpoint, states, capabilities);
    }

    @Override
    public String toString() {
        return "RenderContext{" +
                "renderer='" + renderer + '\'' +
                ", breakpoint='" + breakpoint + '\'' +
                ", states=" + states +
                ", capabilities=" + capabilities +
                '}';
    }

    // ==================== Builder ====================

    public static class Builder {
        private String renderer = RENDERER_TUI;
        private String breakpoint = BREAKPOINT_MD;
        private final Set<String> states = new HashSet<>();
        private final Set<String> capabilities = new HashSet<>();
        private LibrarianHandle librarian;
        private RenderMetrics renderMetrics;
        private float baseFontSize = 16f;
        private float viewportWidth = -1;
        private float viewportHeight = -1;
        private float dpi = 96;
        private float devicePixelRatio = 1.0f;

        public Builder renderer(String renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder breakpoint(String breakpoint) {
            this.breakpoint = breakpoint;
            return this;
        }

        public Builder addState(String state) {
            if (state != null) {
                this.states.add(state);
            }
            return this;
        }

        public Builder states(Set<String> states) {
            if (states != null) {
                this.states.addAll(states);
            }
            return this;
        }

        public Builder addCapability(String capability) {
            if (capability != null) {
                this.capabilities.add(capability);
            }
            return this;
        }

        public Builder capabilities(Set<String> capabilities) {
            if (capabilities != null) {
                this.capabilities.addAll(capabilities);
            }
            return this;
        }

        public Builder librarian(LibrarianHandle librarian) {
            this.librarian = librarian;
            return this;
        }

        public Builder renderMetrics(RenderMetrics metrics) {
            this.renderMetrics = metrics;
            return this;
        }

        public Builder baseFontSize(float size) {
            this.baseFontSize = size;
            return this;
        }

        public Builder viewportWidth(float width) {
            this.viewportWidth = width;
            return this;
        }

        public Builder viewportHeight(float height) {
            this.viewportHeight = height;
            return this;
        }

        public Builder dpi(float dpi) {
            this.dpi = dpi;
            return this;
        }

        public Builder devicePixelRatio(float dpr) {
            this.devicePixelRatio = dpr;
            return this;
        }

        public RenderContext build() {
            return new RenderContext(this);
        }
    }
}
