package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.InspectEntry;
import dev.everydaythings.graph.frame.Inspectable;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Ref wrapped for tree navigation.
 *
 * <p>TreeLink wraps a {@link Ref} and adds lazy child resolution.
 * This is what the tree UI works with - each node knows how to
 * expand itself by resolving its target and asking for children.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a root tree link
 * TreeLink root = TreeLink.of(Ref.of(itemId), ChildMode.PRESENTATION, resolver);
 *
 * // Check if expandable
 * if (root.isExpandable()) {
 *     List<TreeLink> children = root.children();
 * }
 *
 * // Get the underlying ref for display
 * Ref target = root.target();
 * }</pre>
 *
 * <h2>Child Modes</h2>
 * <ul>
 *   <li>{@link ChildMode#PRESENTATION} - Mount table tree (user-facing structure)</li>
 *   <li>{@link ChildMode#INSPECT} - Raw metadata tables (content, actions, relations - flat)</li>
 * </ul>
 */
@Getter
public class TreeLink {

    /**
     * How to present an Item's children.
     */
    public enum ChildMode {
        /**
         * Presentation mode - shows the mount table tree.
         * This is the organized, user-facing structure.
         */
        PRESENTATION,

        /**
         * Inspect mode - shows raw metadata tables.
         * Like browser dev tools - content table, action table, etc. flat.
         */
        INSPECT
    }

    private final Ref target;
    private final ChildMode mode;
    private final Function<ItemID, Optional<Item>> resolver;

    // Cached state
    private List<TreeLink> cachedChildren;
    private Boolean cachedExpandable;

    private TreeLink(Ref target, ChildMode mode, Function<ItemID, Optional<Item>> resolver) {
        this.target = Objects.requireNonNull(target, "target");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Create a TreeLink for navigation.
     *
     * @param target   the ref to wrap
     * @param mode     which child view to use
     * @param resolver function to resolve ItemID to Item
     */
    public static TreeLink of(Ref target, ChildMode mode, Function<ItemID, Optional<Item>> resolver) {
        return new TreeLink(target, mode, resolver);
    }

    /**
     * Create a TreeLink in PRESENTATION mode.
     */
    public static TreeLink presentation(Ref target, Function<ItemID, Optional<Item>> resolver) {
        return new TreeLink(target, ChildMode.PRESENTATION, resolver);
    }

    /**
     * Create a TreeLink in INSPECT mode.
     */
    public static TreeLink inspect(Ref target, Function<ItemID, Optional<Item>> resolver) {
        return new TreeLink(target, ChildMode.INSPECT, resolver);
    }

    /**
     * Whether this ref can be expanded (has children).
     *
     * <p>Resolves the target and checks if it has children in the current mode.
     * In presentation mode, frame-key refs may be virtual directories with children.
     */
    public boolean isExpandable() {
        if (cachedExpandable != null) {
            return cachedExpandable;
        }

        FrameKey fk = target.frameKey();
        if (fk != null) {
            String p = "/" + fk.toCanonicalString();
            if (p.contains("#")) {
                // Entry nodes are always leaf nodes
                cachedExpandable = false;
            } else if (mode == ChildMode.PRESENTATION) {
                // Virtual directories are expandable if they have children
                Optional<Item> item = resolver.apply(target.target());
                cachedExpandable = item.map(i -> !i.childrenAtPath(p).isEmpty()).orElse(false);
            } else {
                // INSPECT mode: check if component has entries
                cachedExpandable = resolveInspectable(fk)
                        .map(cc -> !cc.inspectEntries().isEmpty())
                        .orElse(false);
            }
            return cachedExpandable;
        }

        Optional<Item> item = resolver.apply(target.target());
        cachedExpandable = item.map(i -> i.isExpandable(mode)).orElse(false);
        return cachedExpandable;
    }

    /**
     * Get the children of this tree link.
     *
     * <p>Resolves the target and gets its children as TreeLinks.
     * Results are cached for repeated access.
     *
     * <p>In presentation mode, frame-key refs resolve children at their path
     * (supporting virtual directories). In inspect mode, frame-key refs
     * resolve component entries.
     *
     * @return list of child TreeLinks, empty if not expandable
     */
    public List<TreeLink> children() {
        if (cachedChildren != null) {
            return cachedChildren;
        }

        FrameKey fk = target.frameKey();
        if (fk != null) {
            String p = "/" + fk.toCanonicalString();
            if (p.contains("#")) {
                // Entry nodes have no children
                cachedChildren = List.of();
            } else if (mode == ChildMode.PRESENTATION) {
                // Virtual directory or component with sub-mounts
                Optional<Item> item = resolver.apply(target.target());
                if (item.isPresent()) {
                    cachedChildren = item.get().childrenAtPath(p).stream()
                            .map(ref -> new TreeLink(ref, mode, resolver))
                            .toList();
                } else {
                    cachedChildren = List.of();
                }
            } else {
                // INSPECT mode: return component entries as children
                cachedChildren = resolveInspectable(fk)
                        .map(cc -> cc.inspectEntries().stream()
                                .map(e -> {
                                    Ref entryRef = Ref.of(target.target(), FrameKey.literal(fk.toCanonicalString() + "#" + e.id()));
                                    return new TreeLink(entryRef, mode, resolver);
                                })
                                .toList())
                        .orElse(List.of());
            }
            return cachedChildren;
        }

        Optional<Item> item = resolver.apply(target.target());
        if (item.isEmpty()) {
            cachedChildren = List.of();
            return cachedChildren;
        }

        cachedChildren = item.get().children(mode).stream()
                .map(ref -> new TreeLink(ref, mode, resolver))
                .toList();
        return cachedChildren;
    }

    /**
     * Clear cached children (for refresh).
     */
    public void invalidate() {
        cachedChildren = null;
        cachedExpandable = null;
    }

    // ==================== Display Delegation ====================

    /**
     * Get the display token for this tree link.
     *
     * <p>Resolves the target and returns its displayToken.
     * If the ref has a frame key, resolves through the Item to find the component.
     */
    public String displayToken() {
        Optional<Item> item = resolver.apply(target.target());
        if (item.isEmpty()) {
            return truncate(target.toString());
        }

        // If ref has a frame key, resolve to the component
        FrameKey fk = target.frameKey();
        if (fk != null) {
            String p = "/" + fk.toCanonicalString();
            // Entry path: resolve from component's inspectEntries()
            if (p.contains("#")) {
                return truncate(resolveEntryFromPath(p)
                        .map(InspectEntry::label)
                        .orElse(p.substring(p.indexOf('#') + 1)));
            }
            return truncate(item.get().resolvePathDisplayToken(p)
                    .orElse(fk.toCanonicalString()));
        }

        return truncate(item.get().displayToken());
    }

    private static final int MAX_LABEL_LENGTH = 24;

    private static String truncate(String label) {
        if (label == null || label.length() <= MAX_LABEL_LENGTH) return label;
        return label.substring(0, MAX_LABEL_LENGTH - 1) + "\u2026";
    }

    /**
     * Get the emoji/icon for this tree link.
     *
     * <p>Resolves the target and returns its emoji.
     * If the ref has a frame key, resolves through the Item to find the component.
     */
    public String emoji() {
        Optional<Item> item = resolver.apply(target.target());
        if (item.isEmpty()) {
            return "❓";
        }

        // If ref has a frame key, resolve to the component
        FrameKey fk = target.frameKey();
        if (fk != null) {
            String p = "/" + fk.toCanonicalString();
            // Entry path: resolve from component's inspectEntries()
            if (p.contains("#")) {
                return resolveEntryFromPath(p)
                        .map(InspectEntry::emoji)
                        .orElse("📄");
            }
            return item.get().resolvePathEmoji(p)
                    .orElse("📁");  // Virtual directory (no component at this path)
        }

        return item.get().emoji();
    }

    /**
     * Get the classpath resource path for a 2D icon, if available.
     *
     * <p>Checks the component's {@code @Type(icon=...)} annotation
     * for a resource path. Returns null if no icon resource is defined.
     */
    public String iconResource() {
        Optional<Item> item = resolver.apply(target.target());
        if (item.isEmpty()) return null;

        FrameKey fk = target.frameKey();
        if (fk != null) {
            String p = "/" + fk.toCanonicalString();
            return item.get().resolvePathIconResource(p).orElse(null);
        }

        return null;
    }

    /**
     * Get the type color for this tree link's target as ARGB.
     * Returns 0 if unavailable.
     */
    public int typeColorArgb() {
        Optional<Item> item = resolver.apply(target.target());
        if (item.isEmpty()) return 0;

        FrameKey fk = target.frameKey();
        if (fk != null) {
            String p = "/" + fk.toCanonicalString();
            // Component — use the component type's color if available
            return item.get().resolvePathTypeColor(p)
                    .map(c -> 0xFF000000 | c.toPacked())
                    .orElse(0);
        }

        // Item — use item type color
        var color = item.get().displayInfo().color();
        return color != null ? (0xFF000000 | color.toPacked()) : 0;
    }

    /**
     * Get the unique ID for this tree link (for tree model).
     * Includes frame key to make component refs unique.
     */
    public String treeId() {
        if (target.target() == null) {
            return "link:" + System.identityHashCode(this);
        }
        String base = "iid:" + target.target().encodeText();
        FrameKey fk = target.frameKey();
        if (fk != null) {
            return base + "/" + fk.toCanonicalString();
        }
        return base;
    }

    /**
     * Create a child TreeLink with the same mode and resolver.
     */
    public TreeLink child(Ref childRef) {
        return new TreeLink(childRef, mode, resolver);
    }

    /**
     * Switch to a different mode (creates new TreeLink).
     */
    public TreeLink withMode(ChildMode newMode) {
        if (newMode == mode) return this;
        return new TreeLink(target, newMode, resolver);
    }

    // ==================== Internal Helpers ====================

    /**
     * Resolve a FrameKey to its live Inspectable instance.
     */
    private Optional<Inspectable> resolveInspectable(FrameKey key) {
        Optional<Item> item = resolver.apply(target.target());
        if (item.isEmpty()) return Optional.empty();
        return item.get().content().getLive(key)
                .filter(o -> o instanceof Inspectable)
                .map(o -> (Inspectable) o);
    }

    /**
     * Resolve a path containing # to the InspectEntry.
     */
    private Optional<InspectEntry> resolveEntryFromPath(String path) {
        String[] parts = path.split("#", 2);
        String compPath = parts[0];
        String entryId = parts[1];
        String handle = compPath.startsWith("/") ? compPath.substring(1) : compPath;
        FrameKey key = FrameKey.literal(handle);
        return resolveInspectable(key)
                .flatMap(cc -> cc.inspectEntries().stream()
                        .filter(e -> e.id().equals(entryId))
                        .findFirst());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TreeLink treeLink)) return false;
        return Objects.equals(target, treeLink.target) && mode == treeLink.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, mode);
    }

    @Override
    public String toString() {
        return "TreeLink[" + target + ", " + mode + "]";
    }
}
