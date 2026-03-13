package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import lombok.Getter;

/**
 * Context provided to every frame instance when placed on an item.
 *
 * <p>Every frame has a theme (the owning item). This context provides
 * the frame instance with access to its theme and its position within
 * the item's frame table.
 *
 * <p>Frame-aware instances receive this at hydration time via
 * {@link FrameAware#onFramePlaced(FrameContext)}.
 */
@Getter
public final class FrameContext {

    /** The item this frame lives on (the theme). */
    private final Item theme;

    /** This frame's key on the item. */
    private final FrameKey key;

    /** The endorsement entry for this frame (null for unendorsed frames). */
    private final FrameEntry entry;

    public FrameContext(Item theme, FrameKey key, FrameEntry entry) {
        this.theme = theme;
        this.key = key;
        this.entry = entry;
    }
}
