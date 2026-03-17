package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.TickRegistry;
import dev.everydaythings.graph.ui.skia.FontCache;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.SkiaKeyAdapter;
import dev.everydaythings.graph.ui.skia.SkiaPainter;
import dev.everydaythings.graph.ui.text.FontRegistry;
import lombok.Getter;

/**
 * Session-wide rendering resources shared across all ViewWindows.
 *
 * <p>These resources are initialized once when the graphical session starts
 * and persist for the entire session lifetime. Individual ViewWindows
 * hold references to these but do not own them.
 */
@Getter
public class SharedResources {

    private final FontRegistry fontRegistry;
    private final FontCache fontCache;
    private final LayoutEngine layoutEngine;
    private final SkiaPainter painter;
    private final SkiaKeyAdapter keyAdapter;
    private final TickRegistry tickRegistry;

    public SharedResources() {
        this.fontRegistry = FontRegistry.shared();
        this.fontCache = new FontCache(fontRegistry);
        this.layoutEngine = new LayoutEngine(fontCache);
        this.painter = new SkiaPainter(fontCache);
        this.keyAdapter = new SkiaKeyAdapter();
        this.tickRegistry = new TickRegistry();
    }

    /**
     * Clean up all resources. Call when the session ends.
     */
    public void destroy() {
        if (fontCache != null) fontCache.close();
    }
}
