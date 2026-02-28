package dev.everydaythings.graph.ui.scene.surface.tree;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.scene.SceneModel;
import dev.everydaythings.graph.ui.scene.spatial.TreeBody;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Generic tree model for interactive tree views.
 *
 * <p>TreeModel is a stateful model that wraps a tree of values and provides:
 * <ul>
 *   <li>Selection state - which node is selected</li>
 *   <li>Expansion state - which nodes are expanded</li>
 *   <li>Keyboard navigation - arrow keys to move selection</li>
 *   <li>Event handling - select, expand, collapse, toggle</li>
 * </ul>
 *
 * <p>This is a generic component that works with any
 * value type. You provide functions to extract children, labels, icons, and IDs
 * from your values.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Build a tree model for Items
 * TreeModel<Item> model = TreeModel.forItem(rootItem);
 * model.onChange(this::render);
 *
 * // Or build with custom providers
 * TreeModel<MyNode> model = TreeModel.builder(root)
 *     .children(MyNode::getChildren)
 *     .label(MyNode::getName)
 *     .icon(MyNode::getIcon)
 *     .id(node -> String.valueOf(node.hashCode()))
 *     .build();
 *
 * // Render
 * TreeSurface surface = model.toSurface();
 * surface.render(output);
 *
 * // Handle keyboard
 * if (model.handleKey(chord)) {
 *     return; // consumed
 * }
 * }</pre>
 *
 * @param <T> The type of values in the tree
 */
public class TreeModel<T> extends SceneModel<TreeSurface> {

    // ==================================================================================
    // Configuration (immutable after construction)
    // ==================================================================================

    /** The root node of the tree. */
    private final T root;

    /** Function to get children of a node. */
    private final Function<T, List<T>> childrenProvider;

    /** Function to get the unique ID of a node. */
    private final Function<T, String> idProvider;

    /** Function to get the display label of a node. */
    private final Function<T, String> labelProvider;

    /** Function to get the icon/emoji of a node. */
    private final Function<T, String> iconProvider;

    /** Function to get a rich ImageSurface icon for a node (mutually exclusive with iconProvider). */
    private final Function<T, ImageSurface> iconSurfaceProvider;

    /** Predicate to check if a node is expandable (has or can have children). */
    private final Predicate<T> expandablePredicate;

    /** Function to get custom content for a node (optional). */
    private final Function<T, SurfaceSchema> contentProvider;

    /** Whether to show the root node. */
    private final boolean showRoot;

    // ==================================================================================
    // State
    // ==================================================================================

    /** ID of the currently selected node. */
    private String selectedId;

    /** IDs of expanded nodes. */
    private final Set<String> expandedIds = new HashSet<>();

    /** Flattened list of visible nodes (for navigation). */
    private List<T> visibleNodes;

    /** Cache: node ID → node. */
    private final Map<String, T> nodeIndex = new HashMap<>();

    /** Cache: node ID → parent ID. */
    private final Map<String, String> parentIndex = new HashMap<>();

    // ==================================================================================
    // Construction
    // ==================================================================================

    private TreeModel(Builder<T> builder) {
        this.root = Objects.requireNonNull(builder.root, "root");
        this.childrenProvider = Objects.requireNonNull(builder.childrenProvider, "childrenProvider");
        this.idProvider = Objects.requireNonNull(builder.idProvider, "idProvider");
        this.labelProvider = builder.labelProvider != null ? builder.labelProvider : Object::toString;
        this.iconProvider = builder.iconProvider != null ? builder.iconProvider : n -> null;
        this.iconSurfaceProvider = builder.iconSurfaceProvider;
        this.expandablePredicate = builder.expandablePredicate != null ? builder.expandablePredicate :
                n -> !childrenProvider.apply(n).isEmpty();
        this.contentProvider = builder.contentProvider;
        this.showRoot = builder.showRoot;

        // Start with root selected and expanded
        this.selectedId = idProvider.apply(root);
        this.expandedIds.add(selectedId);
        rebuildIndex();
    }

    /**
     * Create a builder for a tree model.
     *
     * @param root The root value
     * @param <T> The value type
     * @return A builder
     */
    public static <T> Builder<T> builder(T root) {
        return new Builder<>(root);
    }

    /**
     * Create a tree model for Item values.
     *
     * <p>This is the common case - a tree of Items where
     * children, labels, and icons come from the Item methods.
     *
     * @param root The root Item
     * @return A configured TreeModel
     */
    public static TreeModel<Item> forItem(Item root) {
        return TreeModel.<Item>builder(root)
                .children(item -> List.of())  // Items don't have child Items directly
                .label(Item::displayToken)
                .icon(Item::emoji)
                .expandable(Item::isExpandable)
                .id(TreeModel::defaultItemId)
                .build();
    }

    /**
     * Default ID function for Items.
     * Uses IID if available, falls back to display token + identity hash.
     */
    private static String defaultItemId(Item item) {
        if (item.iid() != null) {
            return "iid:" + item.iid().encodeText();
        }
        return item.displayToken() + "@" + Integer.toHexString(System.identityHashCode(item));
    }

    // ==================================================================================
    // Index Management
    // ==================================================================================

    /**
     * Rebuild the node index and visible nodes list.
     */
    private void rebuildIndex() {
        nodeIndex.clear();
        parentIndex.clear();
        visibleNodes = new ArrayList<>();
        indexNode(root, null);
    }

    private void indexNode(T node, String parentId) {
        String id = idProvider.apply(node);
        nodeIndex.put(id, node);
        if (parentId != null) {
            parentIndex.put(id, parentId);
        }

        // Add to visible nodes
        visibleNodes.add(node);

        // Recurse into children if expanded
        if (expandedIds.contains(id)) {
            for (T child : childrenProvider.apply(node)) {
                indexNode(child, id);
            }
        }
    }

    // ==================================================================================
    // SceneModel Implementation
    // ==================================================================================

    @Override
    public TreeSurface toSurface() {
        TreeSurface surface = new TreeSurface();
        surface.addRoot(buildSurfaceNode(root));
        surface.showRoot(showRoot);
        return surface;
    }

    /**
     * Convert the current tree state into a 3D botanical tree scene.
     *
     * <p>Uses default appearance: spheres colored by label hash.
     *
     * @return A TreeBody with the current tree laid out in 3D space
     */
    public TreeBody toScene() {
        return toScene(TreeBody.defaultAppearance());
    }

    /**
     * Convert the current tree state into a 3D botanical tree scene
     * with custom node appearance.
     *
     * @param appearance maps each node to its 3D shape and color
     * @return A TreeBody with the current tree laid out in 3D space
     */
    public TreeBody toScene(TreeBody.NodeAppearance appearance) {
        TreeSurface surface = toSurface();
        return TreeBody.from(surface.roots(), appearance);
    }

    /**
     * Build a TreeSurface.Node from a value.
     */
    private TreeSurface.Node buildSurfaceNode(T node) {
        String id = idProvider.apply(node);
        boolean isExpanded = expandedIds.contains(id);
        boolean isSelected = id.equals(selectedId);
        boolean hasChildren = expandablePredicate.test(node);

        // Determine content: custom provider or default HandleSurface
        SurfaceSchema content;
        if (contentProvider != null) {
            content = contentProvider.apply(node);
        } else if (iconSurfaceProvider != null) {
            // Rich icon: ImageSurface with shape/background
            ImageSurface icon = iconSurfaceProvider.apply(node);
            String label = labelProvider.apply(node);
            if (icon != null) {
                content = HandleSurface.forNode(icon, label);
            } else {
                content = HandleSurface.ofLabel(label);
            }
        } else {
            // Default: HandleSurface with string icon and label
            String icon = iconProvider.apply(node);
            String label = labelProvider.apply(node);
            if (icon != null) {
                content = HandleSurface.forNode(icon, label);
            } else {
                content = HandleSurface.ofLabel(label);
            }
        }

        TreeSurface.Node surfaceNode = TreeSurface.Node.of(id, content)
                .expanded(isExpanded)
                .selected(isSelected)
                .expandable(hasChildren);

        // Add children if expanded
        if (isExpanded && hasChildren) {
            for (T child : childrenProvider.apply(node)) {
                surfaceNode.addChild(buildSurfaceNode(child));
            }
        }

        return surfaceNode;
    }

    @Override
    public boolean handleEvent(String action, String target) {
        return switch (action) {
            case "select" -> {
                select(target);
                yield true;
            }
            case "expand" -> {
                expand(target);
                yield true;
            }
            case "collapse" -> {
                collapse(target);
                yield true;
            }
            case "toggle" -> {
                toggle(target);
                yield true;
            }
            case "activate" -> {
                // Double-click or Enter - expand and select
                expand(target);
                select(target);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean handleKey(KeyChord chord) {
        // Arrow key navigation
        if (chord.isKey(SpecialKey.UP)) {
            selectPrevious();
            return true;
        }
        if (chord.isKey(SpecialKey.DOWN)) {
            selectNext();
            return true;
        }
        if (chord.isKey(SpecialKey.RIGHT)) {
            // Expand current node, or move to first child if already expanded
            if (isExpanded(selectedId)) {
                selectFirstChild();
            } else {
                expand(selectedId);
            }
            return true;
        }
        if (chord.isKey(SpecialKey.LEFT)) {
            // Collapse current node, or move to parent if already collapsed
            if (isExpanded(selectedId) && hasChildren(selectedId)) {
                collapse(selectedId);
            } else {
                selectParent();
            }
            return true;
        }

        // Toggle expansion
        if (chord.isKey(SpecialKey.ENTER) || chord.isChar(' ')) {
            toggle(selectedId);
            return true;
        }

        // Home/End navigation
        if (chord.isKey(SpecialKey.HOME)) {
            selectFirst();
            return true;
        }
        if (chord.isKey(SpecialKey.END)) {
            selectLast();
            return true;
        }

        // Expand/collapse all (* and -)
        if (chord.isChar('*')) {
            expandAll();
            return true;
        }
        if (chord.isChar('-')) {
            collapseAll();
            return true;
        }

        return false;
    }

    // ==================================================================================
    // Selection
    // ==================================================================================

    /**
     * Select a node by ID.
     */
    public void select(String id) {
        if (id != null && nodeIndex.containsKey(id) && !id.equals(selectedId)) {
            selectedId = id;
            changed();
        }
    }

    /**
     * Get the currently selected value.
     */
    public T getSelected() {
        return selectedId != null ? nodeIndex.get(selectedId) : null;
    }

    /**
     * Get the selected node ID.
     */
    public String getSelectedId() {
        return selectedId;
    }

    /**
     * Select the next visible node.
     */
    public void selectNext() {
        rebuildIndex(); // Ensure visible list is current
        int idx = indexOfSelected();
        if (idx >= 0 && idx < visibleNodes.size() - 1) {
            select(idProvider.apply(visibleNodes.get(idx + 1)));
        }
    }

    /**
     * Select the previous visible node.
     */
    public void selectPrevious() {
        rebuildIndex();
        int idx = indexOfSelected();
        if (idx > 0) {
            select(idProvider.apply(visibleNodes.get(idx - 1)));
        }
    }

    /**
     * Select the first visible node.
     */
    public void selectFirst() {
        rebuildIndex();
        if (!visibleNodes.isEmpty()) {
            select(idProvider.apply(visibleNodes.get(0)));
        }
    }

    /**
     * Select the last visible node.
     */
    public void selectLast() {
        rebuildIndex();
        if (!visibleNodes.isEmpty()) {
            select(idProvider.apply(visibleNodes.get(visibleNodes.size() - 1)));
        }
    }

    /**
     * Select the parent of the current node.
     */
    public void selectParent() {
        String parentId = parentIndex.get(selectedId);
        if (parentId != null) {
            select(parentId);
        }
    }

    /**
     * Select the first child of the current node.
     */
    public void selectFirstChild() {
        T current = nodeIndex.get(selectedId);
        if (current != null) {
            List<T> children = childrenProvider.apply(current);
            if (!children.isEmpty()) {
                select(idProvider.apply(children.get(0)));
            }
        }
    }

    private int indexOfSelected() {
        if (selectedId == null) return -1;
        for (int i = 0; i < visibleNodes.size(); i++) {
            if (idProvider.apply(visibleNodes.get(i)).equals(selectedId)) {
                return i;
            }
        }
        return -1;
    }

    // ==================================================================================
    // Expansion
    // ==================================================================================

    /**
     * Expand a node by ID.
     */
    public void expand(String id) {
        if (id != null && !expandedIds.contains(id) && hasChildren(id)) {
            expandedIds.add(id);
            rebuildIndex();
            changed();
        }
    }

    /**
     * Collapse a node by ID.
     */
    public void collapse(String id) {
        if (id != null && expandedIds.contains(id)) {
            expandedIds.remove(id);
            rebuildIndex();
            changed();
        }
    }

    /**
     * Toggle expansion of a node.
     */
    public void toggle(String id) {
        if (id != null) {
            if (expandedIds.contains(id)) {
                collapse(id);
            } else {
                expand(id);
            }
        }
    }

    /**
     * Check if a node is expanded.
     */
    public boolean isExpanded(String id) {
        return expandedIds.contains(id);
    }

    /**
     * Check if a node has children.
     */
    public boolean hasChildren(String id) {
        T node = nodeIndex.get(id);
        return node != null && expandablePredicate.test(node);
    }

    /**
     * Expand all ancestors of a node (to make it visible).
     */
    public void reveal(String id) {
        String current = parentIndex.get(id);
        while (current != null) {
            expandedIds.add(current);
            current = parentIndex.get(current);
        }
        rebuildIndex();
        changed();
    }

    /**
     * Expand all nodes.
     */
    public void expandAll() {
        expandAllFrom(root);
        rebuildIndex();
        changed();
    }

    private void expandAllFrom(T node) {
        String id = idProvider.apply(node);
        if (expandablePredicate.test(node)) {
            expandedIds.add(id);
            for (T child : childrenProvider.apply(node)) {
                expandAllFrom(child);
            }
        }
    }

    /**
     * Collapse all nodes except root.
     */
    public void collapseAll() {
        String rootId = idProvider.apply(root);
        expandedIds.clear();
        expandedIds.add(rootId);
        rebuildIndex();
        changed();
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /**
     * Get the root value.
     */
    public T getRoot() {
        return root;
    }

    /**
     * Get all visible nodes in order.
     */
    public List<T> getVisibleNodes() {
        rebuildIndex();
        return Collections.unmodifiableList(visibleNodes);
    }

    /**
     * Get a node by ID.
     */
    public T getNode(String id) {
        return nodeIndex.get(id);
    }

    /**
     * Check if the root node is visible.
     */
    public boolean isShowRoot() {
        return showRoot;
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    /**
     * Builder for TreeModel.
     *
     * @param <T> The value type
     */
    public static class Builder<T> {
        private final T root;
        private Function<T, List<T>> childrenProvider;
        private Function<T, String> idProvider;
        private Function<T, String> labelProvider;
        private Function<T, String> iconProvider;
        private Function<T, ImageSurface> iconSurfaceProvider;
        private Predicate<T> expandablePredicate;
        private Function<T, SurfaceSchema> contentProvider;
        private boolean showRoot = true;

        private Builder(T root) {
            this.root = root;
        }

        /**
         * Set the function to get children of a node.
         * Required.
         */
        public Builder<T> children(Function<T, List<T>> provider) {
            this.childrenProvider = provider;
            return this;
        }

        /**
         * Set the function to get the unique ID of a node.
         * Required.
         */
        public Builder<T> id(Function<T, String> provider) {
            this.idProvider = provider;
            return this;
        }

        /**
         * Set the function to get the display label of a node.
         * Optional - defaults to toString().
         */
        public Builder<T> label(Function<T, String> provider) {
            this.labelProvider = provider;
            return this;
        }

        /**
         * Set the function to get the icon/emoji of a node.
         * Optional - defaults to null (no icon).
         */
        public Builder<T> icon(Function<T, String> provider) {
            this.iconProvider = provider;
            return this;
        }

        /**
         * Set the function to get a rich ImageSurface icon for a node.
         * Mutually exclusive with {@link #icon(Function)}.
         * Optional - defaults to null (uses icon provider instead).
         */
        public Builder<T> iconSurface(Function<T, ImageSurface> provider) {
            this.iconSurfaceProvider = provider;
            return this;
        }

        /**
         * Set the predicate to check if a node is expandable.
         * Optional - defaults to checking if children list is non-empty.
         */
        public Builder<T> expandable(Predicate<T> predicate) {
            this.expandablePredicate = predicate;
            return this;
        }

        /**
         * Set the function to get custom content for a node.
         *
         * <p>The content is rendered inside the tree node after the label.
         * Use this to add controls, badges, progress bars, or any other
         * custom UI elements to tree nodes.
         *
         * <p>Example:
         * <pre>{@code
         * .content(node -> {
         *     if (node.hasProgress()) {
         *         return ProgressSurface.of(node.getProgress());
         *     }
         *     return null;
         * })
         * }</pre>
         *
         * Optional - defaults to no custom content.
         */
        public Builder<T> content(Function<T, SurfaceSchema> provider) {
            this.contentProvider = provider;
            return this;
        }

        /**
         * Set whether to show the root node.
         *
         * <p>When false, the root node is hidden but its children are shown
         * as top-level items.
         *
         * Optional - defaults to true.
         */
        public Builder<T> showRoot(boolean show) {
            this.showRoot = show;
            return this;
        }

        /**
         * Build the TreeModel.
         */
        public TreeModel<T> build() {
            return new TreeModel<>(this);
        }
    }
}
