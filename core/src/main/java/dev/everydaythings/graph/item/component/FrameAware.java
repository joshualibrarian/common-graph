package dev.everydaythings.graph.item.component;

/**
 * Frame instances that need to know their context when placed on an item.
 *
 * <p>Every frame has a theme (the owning item), a key, and an entry.
 * Most simple frame values (scalars, plain data) don't need this context.
 * Stateful frame values that interact with their owning item — logs that
 * need store access, games that publish config settings — implement this
 * interface to receive context at hydration time.
 *
 * <p>This replaces the old {@code Component.initComponent(Item)} pattern
 * with a richer context that includes the frame key and entry metadata.
 */
public interface FrameAware {

    /**
     * Called when this frame instance is placed on an item.
     *
     * <p>The context provides access to the theme (owning item),
     * the frame's key, and the endorsement entry.
     *
     * @param context the frame's placement context
     */
    void onFramePlaced(FrameContext context);
}
