package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Link wrapped for tree navigation.
 *
 * <p>TreeLink wraps a {@link Link} and adds lazy child resolution.
 * This is what the tree UI works with - each node knows how to
 * expand itself by resolving its target and asking for children.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a root tree link
 * TreeLink root = TreeLink.of(Link.of(itemId), ChildMode.PRESENTATION, resolver);
 *
 * // Check if expandable
 * if (root.isExpandable()) {
 *     List<TreeLink> children = root.children();
 * }
 *
 * // Get the underlying link for display
 * Link target = root.target();
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

    private final Link target;
    private final ChildMode mode;
    private final Function<ItemID, Optional<Item>> resolver;

    // Cached state
    private List<TreeLink> cachedChildren;
    private Boolean cachedExpandable;

    private TreeLink(Link target, ChildMode mode, Function<ItemID, Optional<Item>> resolver) {
        this.target = Objects.requireNonNull(target, "target");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Create a TreeLink for navigation.
     *
     * @param target   the link to wrap
     * @param mode     which child view to use
     * @param resolver function to resolve ItemID to Item
     */
    public static TreeLink of(Link target, ChildMode mode, Function<ItemID, Optional<Item>> resolver) {
        return new TreeLink(target, mode, resolver);
    }

    /**
     * Create a TreeLink in PRESENTATION mode.
     */
    public static TreeLink presentation(Link target, Function<ItemID, Optional<Item>> resolver) {
        return new TreeLink(target, ChildMode.PRESENTATION, resolver);
    }

    /**
     * Create a TreeLink in INSPECT mode.
     */
    public static TreeLink inspect(Link target, Function<ItemID, Optional<Item>> resolver) {
        return new TreeLink(target, ChildMode.INSPECT, resolver);
    }

    /**
     * Whether this link can be expanded (has children).
     *
     * <p>Resolves the target and checks if it has children in the current mode.
     * In presentation mode, path-based links may be virtual directories with children.
     */
    public boolean isExpandable() {
        if (cachedExpandable != null) {
            return cachedExpandable;
        }

        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            String p = path.get();
            if (p.contains("#")) {
                // Entry nodes are always leaf nodes
                cachedExpandable = false;
            } else if (mode == ChildMode.PRESENTATION) {
                // Virtual directories are expandable if they have children
                Optional<Item> item = resolver.apply(target.item());
                cachedExpandable = item.map(i -> !i.childrenAtPath(p).isEmpty()).orElse(false);
            } else {
                // INSPECT mode: check if component has entries
                cachedExpandable = resolveComponent(p)
                        .map(cc -> !cc.inspectEntries().isEmpty())
                        .orElse(false);
            }
            return cachedExpandable;
        }

        Optional<Item> item = resolver.apply(target.item());
        cachedExpandable = item.map(i -> i.isExpandable(mode)).orElse(false);
        return cachedExpandable;
    }

    /**
     * Get the children of this tree link.
     *
     * <p>Resolves the target and gets its children as TreeLinks.
     * Results are cached for repeated access.
     *
     * <p>In presentation mode, path-based links resolve children at their path
     * (supporting virtual directories). In inspect mode, path-based links have
     * no children.
     *
     * @return list of child TreeLinks, empty if not expandable
     */
    public List<TreeLink> children() {
        if (cachedChildren != null) {
            return cachedChildren;
        }

        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            String p = path.get();
            if (p.contains("#")) {
                // Entry nodes have no children
                cachedChildren = List.of();
            } else if (mode == ChildMode.PRESENTATION) {
                // Virtual directory or component with sub-mounts
                Optional<Item> item = resolver.apply(target.item());
                if (item.isPresent()) {
                    cachedChildren = item.get().childrenAtPath(p).stream()
                            .map(link -> new TreeLink(link, mode, resolver))
                            .toList();
                } else {
                    cachedChildren = List.of();
                }
            } else {
                // INSPECT mode: return component entries as children
                cachedChildren = resolveComponent(p)
                        .map(cc -> cc.inspectEntries().stream()
                                .map(e -> {
                                    Link entryLink = Link.of(target.item(), p + "#" + e.id());
                                    return new TreeLink(entryLink, mode, resolver);
                                })
                                .toList())
                        .orElse(List.of());
            }
            return cachedChildren;
        }

        Optional<Item> item = resolver.apply(target.item());
        if (item.isEmpty()) {
            cachedChildren = List.of();
            return cachedChildren;
        }

        cachedChildren = item.get().children(mode).stream()
                .map(link -> new TreeLink(link, mode, resolver))
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
     * If the link has a path, resolves through the Item to find the component.
     */
    public String displayToken() {
        Optional<Item> item = resolver.apply(target.item());
        if (item.isEmpty()) {
            return truncate(target.toString());
        }

        // If link has a path, resolve to the component
        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            String p = path.get();
            // Entry path: resolve from component's inspectEntries()
            if (p.contains("#")) {
                return truncate(resolveEntry(p)
                        .map(Component.InspectEntry::label)
                        .orElse(p.substring(p.indexOf('#') + 1)));
            }
            return truncate(item.get().resolvePathDisplayToken(p)
                    .orElse(p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p));
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
     * If the link has a path, resolves through the Item to find the component.
     */
    public String emoji() {
        Optional<Item> item = resolver.apply(target.item());
        if (item.isEmpty()) {
            return "❓";
        }

        // If link has a path, resolve to the component
        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            String p = path.get();
            // Entry path: resolve from component's inspectEntries()
            if (p.contains("#")) {
                return resolveEntry(p)
                        .map(Component.InspectEntry::emoji)
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
        Optional<Item> item = resolver.apply(target.item());
        if (item.isEmpty()) return null;

        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            return item.get().resolvePathIconResource(path.get()).orElse(null);
        }

        return null;
    }

    /**
     * Get the type color for this tree link's target as ARGB.
     * Returns 0 if unavailable.
     */
    public int typeColorArgb() {
        Optional<Item> item = resolver.apply(target.item());
        if (item.isEmpty()) return 0;

        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            // Component — use the component type's color if available
            return item.get().resolvePathTypeColor(path.get())
                    .map(c -> 0xFF000000 | c.toPacked())
                    .orElse(0);
        }

        // Item — use item type color
        var color = item.get().displayInfo().color();
        return color != null ? (0xFF000000 | color.toPacked()) : 0;
    }

    /**
     * Get the unique ID for this tree link (for tree model).
     * Includes path to make component links unique.
     */
    public String treeId() {
        if (target.item() == null) {
            return "link:" + System.identityHashCode(this);
        }
        String base = "iid:" + target.item().encodeText();
        Optional<String> path = target.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            return base + path.get();  // e.g., "iid:abc123/componentName"
        }
        return base;
    }

    /**
     * Create a child TreeLink with the same mode and resolver.
     */
    public TreeLink child(Link childLink) {
        return new TreeLink(childLink, mode, resolver);
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
     * Resolve a path (without #) to its live Component.
     */
    private Optional<Component> resolveComponent(String path) {
        Optional<Item> item = resolver.apply(target.item());
        if (item.isEmpty()) return Optional.empty();
        String handle = path.startsWith("/") ? path.substring(1) : path;
        HandleID hid = handle.startsWith(HandleID.HID_PREFIX)
                ? HandleID.parse(handle)
                : HandleID.of(handle);
        return item.get().content().getLive(hid)
                .filter(o -> o instanceof Component)
                .map(o -> (Component) o);
    }

    /**
     * Resolve a path containing # to the InspectEntry.
     */
    private Optional<Component.InspectEntry> resolveEntry(String path) {
        String[] parts = path.split("#", 2);
        String compPath = parts[0];
        String entryId = parts[1];
        return resolveComponent(compPath)
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
