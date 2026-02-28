package dev.everydaythings.graph.ui.skia;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-level scroll state — maps element IDs to scroll offsets.
 *
 * <p>Survives across {@code rebuildLayout()} calls so scroll position
 * is preserved when the layout tree is rebuilt.
 */
public class ScrollState {

    private final Map<String, Float> offsets = new HashMap<>();

    /**
     * Get the Y scroll offset for an element.
     *
     * @param id element ID
     * @return scroll offset in pixels (0 = top)
     */
    public float getScrollY(String id) {
        if (id == null) return 0;
        return offsets.getOrDefault(id, 0f);
    }

    /**
     * Set the Y scroll offset for an element, clamped to [0, maxOffset].
     *
     * @param id     element ID
     * @param offset scroll offset in pixels
     */
    public void setScrollY(String id, float offset) {
        if (id == null) return;
        offsets.put(id, Math.max(0, offset));
    }

    /**
     * Scroll by a delta, clamped to [0, maxOffset].
     *
     * @param id        element ID
     * @param delta     scroll delta in pixels (positive = scroll down)
     * @param maxOffset maximum scroll offset (contentHeight - viewportHeight)
     */
    public void scrollBy(String id, float delta, float maxOffset) {
        if (id == null) return;
        float current = getScrollY(id);
        float next = Math.max(0, Math.min(maxOffset, current + delta));
        offsets.put(id, next);
    }

    /**
     * Clear all scroll state.
     */
    public void clear() {
        offsets.clear();
    }
}
