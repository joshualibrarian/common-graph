package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.datum.*;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.CompoundKey;
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
    private final ItemOld theme;

    /** This frame's key on the item. */
    private final CompoundKey key;

    /** The frame (null for unendorsed frames). */
    private final FrameOld frame;

    public FrameContext(ItemOld theme, CompoundKey key, FrameOld frame) {
        this.theme = theme;
        this.key = key;
        this.frame = frame;
    }
}
