package dev.everydaythings.graph.ui.scene.node;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lightweight tree keyboard navigation state.
 *
 * <p>Tracks selection and expansion for a tree built by {@link TreeNodes}.
 * Works with the same data and providers. Does not produce any rendering
 * output — only tracks navigation state.
 *
 * <p>When expansion or selection changes, the caller should update the
 * SceneRenderer's state store to keep rendering in sync, then trigger
 * a re-render.
 *
 * @param <T> the data type for tree nodes
 */
public class TreeNav<T> {

    private final T root;
    private final Function<T, List<T>> childrenProvider;
    private final Function<T, String> idProvider;
    private final Predicate<T> expandablePredicate;
    private final boolean showRoot;

    private String selectedId;
    private final Set<String> expandedIds = new HashSet<>();

    // Indexes rebuilt on navigation
    private final Map<String, T> nodeIndex = new HashMap<>();
    private final Map<String, String> parentIndex = new HashMap<>();
    private List<T> visibleNodes = new ArrayList<>();

    private TreeNav(T root, Function<T, List<T>> childrenProvider,
                    Function<T, String> idProvider, Predicate<T> expandablePredicate,
                    boolean showRoot) {
        this.root = root;
        this.childrenProvider = childrenProvider;
        this.idProvider = idProvider;
        this.expandablePredicate = expandablePredicate;
        this.showRoot = showRoot;
        this.selectedId = idProvider.apply(root);
        this.expandedIds.add(selectedId);
        rebuildIndex();
    }

    /**
     * Create a TreeNav with the same configuration as a TreeNodes builder.
     */
    public static <T> TreeNav<T> from(T root, Function<T, List<T>> children,
                                       Function<T, String> id, Predicate<T> expandable,
                                       boolean showRoot) {
        return new TreeNav<>(root, children, id, expandable, showRoot);
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    public String selectedId() { return selectedId; }

    public void select(String id) {
        if (id != null && nodeIndex.containsKey(id)) {
            selectedId = id;
        }
    }

    public void selectNext() {
        rebuildIndex();
        int idx = indexOfSelected();
        if (idx >= 0 && idx < visibleNodes.size() - 1) {
            selectedId = idProvider.apply(visibleNodes.get(idx + 1));
        }
    }

    public void selectPrevious() {
        rebuildIndex();
        int idx = indexOfSelected();
        if (idx > 0) {
            selectedId = idProvider.apply(visibleNodes.get(idx - 1));
        }
    }

    public void selectParent() {
        String parentId = parentIndex.get(selectedId);
        if (parentId != null) {
            selectedId = parentId;
        }
    }

    public void selectFirstChild() {
        T current = nodeIndex.get(selectedId);
        if (current != null) {
            List<T> children = childrenProvider.apply(current);
            if (!children.isEmpty()) {
                selectedId = idProvider.apply(children.get(0));
            }
        }
    }

    // ==================================================================================
    // Expansion
    // ==================================================================================

    public boolean isExpanded(String id) {
        return expandedIds.contains(id);
    }

    public void expand(String id) {
        if (id != null) {
            expandedIds.add(id);
            rebuildIndex();
        }
    }

    public void collapse(String id) {
        if (id != null) {
            expandedIds.remove(id);
            rebuildIndex();
        }
    }

    public void toggle(String id) {
        if (expandedIds.contains(id)) collapse(id);
        else expand(id);
    }

    public boolean hasChildren(String id) {
        T node = nodeIndex.get(id);
        return node != null && expandablePredicate.test(node);
    }

    /**
     * Sync expansion state TO a SceneRenderer's state store.
     * Call this after keyboard-driven expansion changes so the next render picks up the change.
     */
    public void syncToStateStore(Map<String, Map<String, Object>> store) {
        for (Map.Entry<String, T> entry : nodeIndex.entrySet()) {
            String id = entry.getKey();
            if (expandablePredicate.test(entry.getValue())) {
                store.computeIfAbsent(id, k -> new HashMap<>())
                        .put("expanded", expandedIds.contains(id));
            }
        }
    }

    // ==================================================================================
    // Index
    // ==================================================================================

    private void rebuildIndex() {
        nodeIndex.clear();
        parentIndex.clear();
        visibleNodes = new ArrayList<>();
        if (showRoot) {
            indexNode(root, null);
        } else {
            for (T child : childrenProvider.apply(root)) {
                indexNode(child, null);
            }
        }
    }

    private void indexNode(T node, String parentId) {
        String id = idProvider.apply(node);
        nodeIndex.put(id, node);
        if (parentId != null) parentIndex.put(id, parentId);
        visibleNodes.add(node);
        if (expandedIds.contains(id)) {
            for (T child : childrenProvider.apply(node)) {
                indexNode(child, id);
            }
        }
    }

    private int indexOfSelected() {
        if (selectedId == null) return -1;
        for (int i = 0; i < visibleNodes.size(); i++) {
            if (idProvider.apply(visibleNodes.get(i)).equals(selectedId)) return i;
        }
        return -1;
    }
}
