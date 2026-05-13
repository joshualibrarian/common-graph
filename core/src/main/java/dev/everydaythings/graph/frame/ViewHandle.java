package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.datum.*;

import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;

/**
 * Lightweight wrapper for an open ITEM_VIEW frame.
 *
 * <p>Bundles the frame key (address on the Session), the target item IID,
 * the display reference, and the current {@link ViewConfig}. This is a convenience
 * projection — the canonical source of truth is the frame itself on the
 * Session's endorsements table.
 *
 * @param frameKey the ITEM_VIEW frame's key on the session
 * @param target   the IID of the viewed item
 * @param display  compound Ref to the display device on a host (null if unassigned)
 * @param config   the current view configuration
 */
public record ViewHandle(
        CompoundKey frameKey,
        ItemID target,
        Ref display,
        ViewConfig config
) {
    /**
     * Create a ViewHandle with default config and no display.
     */
    public static ViewHandle of(CompoundKey frameKey, ItemID target) {
        return new ViewHandle(frameKey, target, null, ViewConfig.defaults());
    }

    /**
     * Return a copy with an updated config.
     */
    public ViewHandle withConfig(ViewConfig config) {
        return new ViewHandle(frameKey, target, display, config);
    }

    /**
     * Return a copy with an updated display.
     */
    public ViewHandle withDisplay(Ref display) {
        return new ViewHandle(frameKey, target, display, config);
    }
}
