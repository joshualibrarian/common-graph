package dev.everydaythings.graph.ui.scene.node;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Factory for building declarative tree Node structures.
 *
 * <p>TreeNodes replaces TreeModel — it produces a pure Node tree with
 * state declarations and events. The renderer's state runtime handles
 * all interactivity (expand/collapse, selection). No model objects needed.
 *
 * <p>The output is plain Container/Text/Body nodes with:
 * <ul>
 *   <li>{@code declareState("expanded", ...)} on each expandable node</li>
 *   <li>{@code on("click", "toggle:expanded")} on the expand indicator</li>
 *   <li>{@code visible("$state.expanded")} on the children container</li>
 *   <li>{@code classes("tree-node", "tree-children", "tree-expand")} for styling</li>
 * </ul>
 *
 * <p>No chrome (├── └──) is emitted — that's the renderer's job based on
 * platform and style settings.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Node tree = TreeNodes.from(rootData)
 *     .children(MyNode::getChildren)
 *     .label(MyNode::getName)
 *     .icon(MyNode::getEmoji)
 *     .id(MyNode::getId)
 *     .expandable(n -> !n.children().isEmpty())
 *     .build();
 * }</pre>
 *
 * @param <T> the data type for tree nodes
 */
public class TreeNodes<T> {

    private final T root;
    private Function<T, List<T>> childrenProvider;
    private Function<T, String> idProvider;
    private Function<T, String> labelProvider;
    private Function<T, String> iconProvider;
    private Predicate<T> expandablePredicate;
    private boolean showRoot = true;
    private boolean expandedByDefault = false;

    private TreeNodes(T root) {
        this.root = Objects.requireNonNull(root);
    }

    public static <T> TreeNodes<T> from(T root) {
        return new TreeNodes<>(root);
    }

    public TreeNodes<T> children(Function<T, List<T>> provider) {
        this.childrenProvider = provider;
        return this;
    }

    public TreeNodes<T> id(Function<T, String> provider) {
        this.idProvider = provider;
        return this;
    }

    public TreeNodes<T> label(Function<T, String> provider) {
        this.labelProvider = provider;
        return this;
    }

    public TreeNodes<T> icon(Function<T, String> provider) {
        this.iconProvider = provider;
        return this;
    }

    public TreeNodes<T> expandable(Predicate<T> predicate) {
        this.expandablePredicate = predicate;
        return this;
    }

    public TreeNodes<T> showRoot(boolean show) {
        this.showRoot = show;
        return this;
    }

    public TreeNodes<T> expandedByDefault(boolean expanded) {
        this.expandedByDefault = expanded;
        return this;
    }

    /**
     * Build the Node tree.
     */
    public Node build() {
        Objects.requireNonNull(childrenProvider, "children provider required");
        Objects.requireNonNull(idProvider, "id provider required");
        if (labelProvider == null) labelProvider = Object::toString;
        if (iconProvider == null) iconProvider = n -> null;
        if (expandablePredicate == null) expandablePredicate = n -> !childrenProvider.apply(n).isEmpty();

        Container tree = Container.vertical();
        tree.classes("tree");

        if (showRoot) {
            tree.add(buildNode(root, 0));
        } else {
            List<T> topLevel = childrenProvider.apply(root);
            for (T child : topLevel) {
                tree.add(buildNode(child, 0));
            }
        }

        return tree;
    }

    private Node buildNode(T data, int depth) {
        String nodeId = idProvider.apply(data);
        String label = labelProvider.apply(data);
        String icon = iconProvider.apply(data);
        boolean canExpand = expandablePredicate.test(data);
        List<T> children = childrenProvider.apply(data);

        // Outer container for this node + its children
        Container nodeWrapper = Container.vertical();
        nodeWrapper.id(nodeId);
        nodeWrapper.declareState("selected", "false");
        nodeWrapper.when("selected", "background", "#313244");
        if (canExpand) {
            nodeWrapper.declareState("expanded", expandedByDefault ? "true" : "false");
        }

        // The visible row: [expand indicator] [icon] [label]
        Container row = Container.horizontal();
        row.classes("tree-node");
        row.gap("0.25em");
        row.on("click", "select", nodeId);

        // Indentation via left padding (depth * indent unit)
        if (depth > 0) {
            row.padding("0 0 0 " + (depth * 1.2) + "em");
        }

        // Expand indicator (only if expandable)
        if (canExpand) {
            Text indicator = Text.of("\u25B6");
            indicator.classes("tree-expand");
            indicator.on("click", "toggle:expanded");
            indicator.when("expanded", "text", "\u25BC");
            row.add(indicator);
        } else {
            // Spacer to align with expandable siblings
            Text spacer = Text.of(" ");
            spacer.classes("tree-expand-spacer");
            row.add(spacer);
        }

        // Icon
        if (icon != null && !icon.isEmpty()) {
            row.add(Body.ofGlyph(icon).classes("tree-icon"));
        }

        // Label
        row.add(Text.of(label).classes("tree-label"));

        nodeWrapper.add(row);

        // Children container (visible only when expanded)
        if (canExpand && !children.isEmpty()) {
            Container childrenContainer = Container.vertical();
            childrenContainer.classes("tree-children");
            childrenContainer.visible("$state.expanded");

            for (T child : children) {
                childrenContainer.add(buildNode(child, depth + 1));
            }

            nodeWrapper.add(childrenContainer);
        }

        return nodeWrapper;
    }
}
