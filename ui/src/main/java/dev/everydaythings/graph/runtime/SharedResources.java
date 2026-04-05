package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.TickRegistry;
import dev.everydaythings.graph.ui.skia.FontCache;
import dev.everydaythings.graph.ui.skia.SkiaKeyAdapter;
import dev.everydaythings.graph.ui.text.FontRegistry;
import lombok.Getter;

/**
 * Session-wide rendering resources shared across all ViewWindows.
 */
@Getter
public class SharedResources {

    private final FontRegistry fontRegistry;
    private final FontCache fontCache;
    private final SkiaKeyAdapter keyAdapter;
    private final TickRegistry tickRegistry;

    public SharedResources() {
        this.fontRegistry = FontRegistry.shared();
        this.fontCache = new FontCache(fontRegistry);
        this.keyAdapter = new SkiaKeyAdapter();
        this.tickRegistry = new TickRegistry();
    }

    public void destroy() {
        if (fontCache != null) fontCache.close();
    }
}
