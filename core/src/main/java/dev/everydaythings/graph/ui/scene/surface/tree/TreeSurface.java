package dev.everydaythings.graph.ui.scene.surface.tree;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface for displaying an expandable hierarchy.
 *
 * <p>TreeSurface renders data as a tree with expandable/collapsible nodes.
 * Each node has structural properties (id, expanded, selected, children)
 * and a content surface for display.
 *
 * <p>For simple cases, use {@link HandleSurface} as node content.
 * For complex cases, use any SurfaceSchema.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Simple tree with HandleSurface content
 * TreeSurface.of(List.of(
 *     Node.of("root", HandleSurface.of("📁", "Root"))
 *         .expanded(true)
 *         .addChild(Node.of("child1", HandleSurface.of("📄", "File 1")))
 *         .addChild(Node.of("child2", HandleSurface.of("📄", "File 2")))
 * ))
 *
 * // With custom content
 * Node.of("item", myCustomSurface)
 * }</pre>
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ▼ 📁 Root
 * │   ├── 📄 File 1
 * │   └── 📄 File 2
 * ▶ 📁 Collapsed Folder
 * </pre>
 */
@Scene.Rule(match = ".chrome!tui", display = "visible", opacity = "dim")
@Scene.Rule(match = ".chrome!gui", display = "hidden")
@Scene.Rule(match = ".chrome!space", display = "hidden")
@Scene.Rule(match = ".chrome", color = "#6C7086", fontFamily = "monospace")
@Scene.Rule(match = ".expand-indicator", color = "#89B4FA")
@Scene.Rule(match = ":expanded .expand-indicator!gui", rotation = "90deg")
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"tree"}, gap = "0.125em")
@Scene.State(when = "selectable", style = {"selectable"})
public class TreeSurface extends SurfaceSchema {

    // ==================== Declarative Structure ====================
    // TODO: Enable once @Surface.Repeat with recursion is implemented
    // Tree rendering requires recursive iteration which is complex

    // @Scene.Repeat(bind = "showRoot ? roots : flattenedChildren(roots)")
    // @Scene.Recursive  // Special marker for recursive node rendering
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"node"})
    @Scene.State(when = "expanded", style = {"expanded"})
    @Scene.State(when = "!expanded && hasChildren()", style = {"collapsed"})
    @Scene.State(when = "selected", style = {"selected"})
    static class NodeTemplate {
        // Tree chrome (├──, └──, │) requires tracking ancestor state
        @Scene.Text(bind = "$chrome", style = {"chrome"})
        static class Chrome {}

        @Scene.Text(bind = "expanded ? '▼ ' : '▶ '", style = {"expand-indicator"})
        @Scene.If("hasChildren()")
        @Scene.On(event = "click", action = "$expanded ? 'collapse' : 'expand'", target = "$id")
        static class ExpandIndicator {}

        // Node content (renders the content SurfaceSchema)
        // @Scene.Embed(bind = "content")
        static class Content {}

        // Recursive children
        // @Scene.Repeat(bind = "children", recursive = true)
        // @Scene.If("expanded")
    }

    /**
     * Root nodes of the tree.
     */
    @Canon(order = 10)
    private List<Node> roots = new ArrayList<>();

    /**
     * Whether to show expand/collapse indicators.
     */
    @Canon(order = 11)
    private boolean showExpandIndicators = true;

    /**
     * Whether selection is enabled.
     */
    @Canon(order = 12)
    private boolean selectable = true;

    /**
     * Whether to show root node(s).
     *
     * <p>When false, the root nodes are hidden but their children
     * are shown as top-level items.
     */
    @Canon(order = 13)
    private boolean showRoot = true;

    public TreeSurface() {}

    public static TreeSurface of(List<Node> roots) {
        TreeSurface surface = new TreeSurface();
        surface.roots.addAll(roots);
        return surface;
    }

    public TreeSurface addRoot(Node node) {
        roots.add(node);
        return this;
    }

    public TreeSurface roots(List<Node> roots) {
        this.roots = new ArrayList<>(roots);
        return this;
    }

    public TreeSurface showExpandIndicators(boolean show) {
        this.showExpandIndicators = show;
        return this;
    }

    public TreeSurface selectable(boolean selectable) {
        this.selectable = selectable;
        return this;
    }

    public TreeSurface showRoot(boolean show) {
        this.showRoot = show;
        return this;
    }

    public List<Node> roots() {
        return roots;
    }

    public boolean showRoot() {
        return showRoot;
    }

    public boolean showExpandIndicators() {
        return showExpandIndicators;
    }

    public boolean selectable() {
        return selectable;
    }

    /**
     * A node in the tree - purely structural with generic content.
     *
     * <p>Node holds the tree structure (parent-child relationships, expansion state)
     * while the content can be any SurfaceSchema. Use {@link HandleSurface} for
     * the common case of icon + label + badges.
     *
     * <p>Tree chrome (├──, └──, │) is emitted as text primitives with class "chrome",
     * allowing the stylesheet to control visibility per renderer.
     */
    @Getter @NoArgsConstructor
    public static class Node implements Canonical {
        @Canon(order = 0) private String id;
        @Canon(order = 1) private boolean expanded = false;
        @Canon(order = 2) private boolean selected = false;
        @Canon(order = 3) private List<Node> children = new ArrayList<>();
        @Canon(order = 4) private SurfaceSchema content;
        @Canon(order = 5) private boolean expandable = false;

        public Node(String id, SurfaceSchema content) {
            this.id = id;
            this.content = content;
        }

        public static Node of(String id, SurfaceSchema content) { return new Node(id, content); }
        public static Node of(String id, String icon, String label) { return new Node(id, HandleSurface.of(icon, label)); }

        public Node id(String id) { this.id = id; return this; }
        public Node expanded(boolean expanded) { this.expanded = expanded; return this; }
        public Node selected(boolean selected) { this.selected = selected; return this; }
        public Node expandable(boolean expandable) { this.expandable = expandable; return this; }
        public Node addChild(Node child) { children.add(child); return this; }
        public Node children(List<Node> children) { this.children = new ArrayList<>(children); return this; }
        public Node content(SurfaceSchema content) { this.content = content; return this; }

        public boolean hasChildren() { return expandable || !children.isEmpty(); }

        /**
         * Render this node and its children recursively (entry point for root nodes).
         */
        void render(SurfaceRenderer out, boolean selectable, boolean isLast) {
            render(out, selectable, isLast, new ArrayList<>());
        }

        /**
         * Render this node and its children recursively.
         *
         * @param ancestorIsLast tracks whether each ancestor was the last child (for drawing │ vs space)
         */
        void render(SurfaceRenderer out, boolean selectable, boolean isLast, List<Boolean> ancestorIsLast) {
            // Build node styles
            List<String> nodeStyles = new ArrayList<>();
            nodeStyles.add("node");
            if (expanded) nodeStyles.add("expanded");
            if (!expanded && hasChildren()) nodeStyles.add("collapsed");
            if (selected) nodeStyles.add("selected");

            // Set element type and ID for style matching
            out.type("TreeNode");
            out.id(id);

            // Events for this node
            if (hasChildren()) {
                out.event("click", expanded ? "collapse" : "expand", id);
            }
            if (selectable) {
                out.event("click", "select", id);
            }

            // Render the node row as a horizontal box
            out.beginBox(Scene.Direction.HORIZONTAL, nodeStyles);

            // Emit tree chrome: continuation lines for ancestors
            for (int i = 0; i < ancestorIsLast.size(); i++) {
                boolean ancestorWasLast = ancestorIsLast.get(i);
                if (ancestorWasLast) {
                    out.text("    ", List.of("chrome", "spacer"));  // No line - ancestor was last
                } else {
                    out.text("│   ", List.of("chrome", "continuation"));  // Continuation line
                }
            }

            // Emit tree chrome: branch for this node (only if not root level)
            if (!ancestorIsLast.isEmpty()) {
                if (isLast) {
                    out.text("└── ", List.of("chrome", "branch", "last"));
                } else {
                    out.text("├── ", List.of("chrome", "branch"));
                }
            }

            // Emit expand/collapse indicator in a fixed-width box for alignment
            out.beginBox(Scene.Direction.HORIZONTAL, List.of("expand-area"),
                    BoxBorder.NONE, null, "1.5em", null, null);
            if (hasChildren()) {
                out.text(expanded ? "▼ " : "▶ ", List.of("expand-indicator"));
            }
            out.endBox();

            // Node content (icon + label from HandleSurface or custom content)
            if (content != null) {
                content.render(out);
            }

            out.endBox();  // End node row

            // Children (only if expanded)
            if (expanded && hasChildren()) {
                // Build new ancestor list for children
                List<Boolean> childAncestors = new ArrayList<>(ancestorIsLast);
                childAncestors.add(isLast);

                for (int i = 0; i < children.size(); i++) {
                    boolean childIsLast = (i == children.size() - 1);
                    children.get(i).render(out, selectable, childIsLast, childAncestors);
                }
            }
        }
    }

    // TODO: Remove procedural render() after @Surface.Repeat with recursion is implemented
    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        List<String> treeStyles = new ArrayList<>(style());
        treeStyles.add("tree");
        if (selectable) treeStyles.add("selectable");

        out.beginBox(Scene.Direction.VERTICAL, treeStyles);

        if (showRoot) {
            for (int i = 0; i < roots.size(); i++) {
                boolean isLast = (i == roots.size() - 1);
                roots.get(i).render(out, selectable, isLast);
            }
        } else {
            // Collect all top-level children
            List<Node> topLevel = new ArrayList<>();
            for (Node root : roots) {
                topLevel.addAll(root.children());
            }
            for (int i = 0; i < topLevel.size(); i++) {
                boolean isLast = (i == topLevel.size() - 1);
                topLevel.get(i).render(out, selectable, isLast);
            }
        }

        out.endBox();
    }
}
