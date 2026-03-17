package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Lightweight wrapper for an open ITEM_VIEW frame.
 *
 * <p>Bundles the frame key (address on the Session), the target item IID,
 * and the current {@link ViewConfig}. This is a convenience projection —
 * the canonical source of truth is the frame itself on the Session's
 * endorsements table.
 *
 * @param frameKey the ITEM_VIEW frame's key on the session
 * @param target   the IID of the viewed item
 * @param config   the current view configuration
 */
public record ViewHandle(
        FrameKey frameKey,
        ItemID target,
        ViewConfig config
) {
    /**
     * Create a ViewHandle with default config.
     */
    public static ViewHandle of(FrameKey frameKey, ItemID target) {
        return new ViewHandle(frameKey, target, ViewConfig.defaults());
    }

    /**
     * Return a copy with an updated config.
     */
    public ViewHandle withConfig(ViewConfig config) {
        return new ViewHandle(frameKey, target, config);
    }
}
