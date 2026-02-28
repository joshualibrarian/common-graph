package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.Item;

import java.util.List;

/**
 * Optional interface for objects stored in a ComponentTable.
 *
 * <p>Any object can be a component — implementing this interface is optional.
 * Objects that implement it get display customization in tree views,
 * lifecycle hooks, and inspect entries. Objects that don't implement it
 * use defaults from their ComponentEntry metadata.
 */
public interface Component {

    /**
     * Display token - the primary short text label.
     */
    default String displayToken() {
        return getClass().getSimpleName();
    }

    /**
     * Whether this component can have children in a tree view.
     */
    default boolean isExpandable() {
        return false;
    }

    /**
     * Color category for theming.
     */
    default String colorCategory() {
        return "component";
    }

    /**
     * Emoji/icon for compact display.
     */
    default String emoji() {
        return "🧩";
    }

    /**
     * Display subtitle - secondary info shown in larger modes.
     */
    default String displaySubtitle() {
        return null;
    }

    // ==================================================================================
    // Lifecycle Hooks
    // ==================================================================================

    /**
     * Called after the component is opened/created and injected into the owning Item.
     *
     * <p>Override this to perform post-initialization setup that requires
     * access to the owning Item or Library.
     *
     * @param owningItem the Item that owns this component
     */
    default void initComponent(Item owningItem) {
        // Default: no-op. Override in subclasses that need post-init setup.
    }

    // ==================================================================================
    // Inspect Entries
    // ==================================================================================

    /**
     * Entry children visible in INSPECT mode tree.
     *
     * <p>Override to expose log entries, DAG events, key history, move history, etc.
     * Each entry appears as a selectable child node when the component is expanded
     * in the tree.
     *
     * @return list of browsable entries, empty by default
     */
    default List<InspectEntry> inspectEntries() { return List.of(); }

    /**
     * A browsable entry within a component, visible in INSPECT mode.
     *
     * @param id    unique identifier within the component (used in tree link paths)
     * @param label human-readable label for the tree node
     * @param emoji glyph/emoji for the tree node icon
     * @param value the data object to render in the detail pane
     */
    record InspectEntry(String id, String label, String emoji, Object value) {}
}
