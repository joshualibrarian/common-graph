package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.CanonicalSchema;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.TreeLink;
import dev.everydaythings.graph.item.VerbEntry;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.policy.PolicySet;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.expression.EvalInputSnapshot;
import dev.everydaythings.graph.ui.scene.surface.ButtonSurface;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.InputSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.scene.surface.tree.TreeModel;
import dev.everydaythings.graph.ui.scene.SceneMode;
import dev.everydaythings.graph.ui.scene.SceneModel;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.spatial.ItemSpace;
import dev.everydaythings.graph.ui.scene.spatial.SpatialSchema;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.bool.ToggleSurface;
import dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Model for the primary Item viewing interface.
 *
 * <p>ItemModel manages state and is annotated with {@link Surface} and
 * {@link ConstraintSurface.Layout} to define layout declaratively.
 *
 * <h2>Layout Structure</h2>
 * <pre>
 * ┌─ header ─────────────────────────────────┐
 * │ 📚 Librarian                             │  ← root (constant)
 * ├─ tree ──────┬─ detail ───────────────────┤
 * │ ▼ 📚 Root   │                            │
 * │  ├── 📦 A   │  [selected item view]      │  ← context (selected)
 * │  └── 🔑 B   │                            │
 * ├─────────────┴────────────────────────────┤
 * │ 📦 A> _                                  │  ← prompt (errors shown here)
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * <h2>State</h2>
 * <ul>
 *   <li><strong>root</strong> - The item being viewed (the "stage")</li>
 *   <li><strong>context</strong> - The selected item (prompt target)</li>
 *   <li><strong>history</strong> - Navigation stack for going back</li>
 * </ul>
 */
@Log4j2
@Getter
@Scene(as = ConstraintSurface.class)
@Scene.Border(all = "0.2em solid #5A6A8A", radius = "0.5em")
public class ItemModel extends SceneModel<SurfaceSchema> {

    // ==================== State ====================

    /**
     * The root item being viewed (the "stage").
     */
    private Link root;

    /**
     * Tree model for the left pane.
     *
     * <p>In PRESENTATION mode, shows path-mount children (table of contents).
     * In INSPECT mode, shows raw components (developer view).
     */
    private TreeModel<?> treeModel;

    /**
     * Config panel surface for the left pane (CONFIG mode only).
     *
     * <p>Replaces the tree with an editable settings form. Built from
     * {@code @Canon(setting = true)} fields on the context item's components.
     */
    private SurfaceSchema<?> configPanel;

    /**
     * Current structure mode — controls how item structure is interpreted.
     *
     * <p>PRESENTATION: path mounts (user-facing structure).
     * INSPECT: raw components (developer tools view).
     */
    private TreeLink.ChildMode structureMode = TreeLink.ChildMode.PRESENTATION;

    /**
     * Left tree-pane mode.
     *
     * <p>Unlike structure mode, vocabulary/config modes are metadata views that do not
     * change context selection targets.
     */
    private TreePaneMode treePaneMode = TreePaneMode.PRESENTATION;

    /**
     * The currently selected item (context).
     */
    private Link context;

    /**
     * Navigation history (previous roots).
     */
    private final List<Link> history = new ArrayList<>();

    /**
     * Resolver for looking up Items by IID.
     */
    private final Function<ItemID, Optional<Item>> resolver;

    /**
     * Current input state snapshot (from EvalInput).
     *
     * <p>Fed by the session whenever EvalInput state changes.
     * Used by {@link #prompt()} to render the input field.
     */
    private EvalInputSnapshot inputSnapshot;

    /**
     * Whether the prompt should be rendered in the surface tree.
     *
     * <p>True (default) for GUI renderers that rely on the surface tree
     * for all rendering. False for TUI/CLI where JLineInputRenderer
     * handles the prompt separately.
     */
    private boolean renderInputInSurface = true;
    private String treeModeHint;

    @Canon(order = 30)
    private List<TreeModeSpec> treeModes;

    private enum TreePaneMode {
        PRESENTATION,
        INSPECT,
        VOCABULARY,
        CONFIG
    }

    @Getter
    public static final class TreeModeSpec implements dev.everydaythings.graph.Canonical {
        @Canon(order = 0) private TreePaneMode mode;
        @Canon(order = 1) private SpecialKey hotkey;
        @Canon(order = 2) private String glyph;
        @Canon(order = 3) private String action;
        @Canon(order = 4) private String hint;

        public TreeModeSpec(TreePaneMode mode, SpecialKey hotkey, String glyph, String action, String hint) {
            this.mode = mode;
            this.hotkey = hotkey;
            this.glyph = glyph;
            this.action = action;
            this.hint = hint;
        }

        @SuppressWarnings("unused")
        private TreeModeSpec() {}
    }

    // ==================== Input State ====================

    /**
     * The last evaluation result text to display in the feedback line.
     *
     * <p>Set by the session after each dispatch. Cleared when the user
     * starts typing again. This is NOT stored data — it's the ephemeral
     * output of the activity log query "last result for this context."
     */
    private String feedbackText;

    /**
     * Whether the feedback was an error.
     */
    private boolean feedbackIsError;

    /**
     * Update the input state from an EvalInput snapshot.
     *
     * <p>Called by the session when EvalInput fires onChange.
     * Does NOT trigger a full re-render — the platform-specific input
     * renderer (e.g., JLineInputRenderer) handles immediate visual updates.
     * The snapshot is stored so the next structural re-render (navigation,
     * selection) shows the current input state in the surface tree.
     *
     * @param snapshot the current input state
     */
    public void updateInput(EvalInputSnapshot snapshot) {
        this.inputSnapshot = snapshot;
    }

    /**
     * Set the feedback text to display above the prompt.
     *
     * <p>Called by the session after logging an activity entry.
     * The feedback line shows the result of the last evaluation
     * for the current context — an ephemeral query result, not stored data.
     */
    public void setFeedback(String text, boolean isError) {
        this.feedbackText = text;
        this.feedbackIsError = isError;
    }

    /**
     * Clear the feedback text (e.g., when user starts typing).
     */
    public void clearFeedback() {
        this.feedbackText = null;
        this.feedbackIsError = false;
    }

    /**
     * Set whether the prompt should be rendered in the surface tree.
     *
     * <p>GUI renderers should leave this as true (default).
     * TUI/CLI renderers that use JLineInputRenderer should set false
     * to avoid double-rendering.
     */
    public void setRenderInputInSurface(boolean render) {
        this.renderInputInSurface = render;
    }

    private List<TreeModeSpec> treeModes() {
        if (treeModes == null || treeModes.isEmpty()) {
            treeModes = defaultTreeModes();
        }
        return treeModes;
    }

    private static List<TreeModeSpec> defaultTreeModes() {
        return List.of(
                new TreeModeSpec(TreePaneMode.VOCABULARY, SpecialKey.F1, "📖", "treeMode:vocabulary", "F1 Vocabulary tree"),
                new TreeModeSpec(TreePaneMode.PRESENTATION, SpecialKey.F2, "🗂", "treeMode:presentation", "F2 Mount tree"),
                new TreeModeSpec(TreePaneMode.CONFIG, SpecialKey.F3, "⚙", "treeMode:config", "F3 Config tree"),
                new TreeModeSpec(TreePaneMode.INSPECT, SpecialKey.F4, "🔍", "treeMode:inspect", "F4 Inspect tree")
        );
    }

    // ==================== View Mode ====================

    /**
     * Toggle between PRESENTATION and INSPECT modes.
     *
     * <p>Rebuilds the tree for the new mode and triggers a re-render.
     * PRESENTATION shows path-mount children; INSPECT shows raw components.
     */
    public void toggleMode() {
        structureMode = (structureMode == TreeLink.ChildMode.PRESENTATION)
                ? TreeLink.ChildMode.INSPECT
                : TreeLink.ChildMode.PRESENTATION;
        treePaneMode = structureMode == TreeLink.ChildMode.PRESENTATION
                ? TreePaneMode.PRESENTATION
                : TreePaneMode.INSPECT;
        rebuildTree();
        changed();
    }

    /**
     * Get the current view mode.
     */
    public TreeLink.ChildMode viewMode() {
        return structureMode;
    }

    private void setTreePaneMode(TreePaneMode mode) {
        if (mode == null || mode == treePaneMode) return;
        treePaneMode = mode;
        if (mode == TreePaneMode.PRESENTATION) {
            structureMode = TreeLink.ChildMode.PRESENTATION;
        } else if (mode == TreePaneMode.INSPECT) {
            structureMode = TreeLink.ChildMode.INSPECT;
        }
        rebuildTree();
        changed();
    }

    // ==================== Layout Regions (via methods) ====================

    /**
     * Header region - shows the current context item's handle and mode indicator.
     */
    @Scene(id = "header", classes = {"chrome"})
    @Scene.Constraint(top = "0", left = "0", right = "100%", height = "fit")
    @Scene.Border(all = "0.3ch solid #4A90D9", radius = "0.3em")
    public SurfaceSchema header() {
        return resolver.apply(context.item())
                .map(item -> {
                    String typeName = item.displayInfo().typeName();
                    if (structureMode == TreeLink.ChildMode.INSPECT) {
                        typeName = (typeName != null ? typeName + " " : "") + "[Inspect]";
                    }
                    return HandleSurface.forHeader(
                            item.emoji(), item.displayToken(), typeName);
                })
                .orElse(null);
    }

    /**
     * Tree region - shows the root item's children in a navigable tree.
     */
    @Scene(id = "tree", classes = {"panel", "tree-panel"})
    @Scene.Constraint(
            topTo = "header.bottom",
            bottomTo = "prompt.top",
            left = "0",
            width = "auto",
            maxWidth = "40%",
            overflow = "auto"
    )
    @Scene.Border(all = "0.5em solid #6ABF69", radius = "0.2ch")
    public SurfaceSchema tree() {
        ContainerSurface panel = ContainerSurface.vertical().gap("0.25em").style("tree-pane");
        panel.add(buildTreeModeBar());
        if (treePaneMode == TreePaneMode.CONFIG && configPanel != null) {
            panel.add(configPanel);
        } else if (treeModel != null) {
            panel.add(treeModel.toSurface());
        }
        return panel;
    }

    /**
     * Detail region - shows the context item's detail view.
     *
     * <p>Mode-aware behavior:
     * <ul>
     *   <li>When context == root (root item selected):
     *     <ul>
     *       <li>PRESENTATION: assembled surface mounts (the item's "face")</li>
     *       <li>INSPECT: component listing (all raw components)</li>
     *     </ul>
     *   </li>
     *   <li>When context != root (tree node selected):
     *     both modes show that node's own surface/content</li>
     * </ul>
     */
    @Scene(id = "detail", classes = {"panel"})
    @Scene.Constraint(
            topTo = "header.bottom",
            bottomTo = "prompt.top",
            leftTo = "tree.right",
            right = "100%",
            overflow = "auto"
    )
    @Scene.Border(all = "0.2rem solid #D9834A", radius = "0.25em")
    public SurfaceSchema detail() {
        Optional<Item> resolved = resolver.apply(context.item());
        if (resolved.isEmpty()) return null;
        Item item = resolved.get();

        // Context == root → show composite/assembled view
        if (context.equals(root)) {
            return detailForRoot(item);
        }

        // Context != root → show selected node's own content
        return detailForSelected(item);
    }

    /**
     * Detail content when the root item itself is selected.
     */
    private SurfaceSchema detailForRoot(Item item) {
        if (structureMode == TreeLink.ChildMode.PRESENTATION) {
            // Assemble surface mounts (the item's "face")
            SurfaceSchema assembled = assembleSurfaceMounts(item);
            if (assembled != null) return assembled;

            // Fallback: directory listing of path-mount children
            List<Link> children = item.childrenAtPath("/");
            if (!children.isEmpty()) {
                return (SurfaceSchema) buildDirectoryListing(item, "/", children);
            }
        } else {
            // Inspect: show all components as a listing
            List<Link> children = item.children(TreeLink.ChildMode.INSPECT);
            if (!children.isEmpty()) {
                return (SurfaceSchema) buildDirectoryListing(item, "/", children);
            }
        }
        return null;
    }

    /**
     * Detail content when a specific tree node is selected (not root).
     */
    private SurfaceSchema detailForSelected(Item item) {
        Optional<String> path = context.path();
        if (path.isPresent() && !path.get().isEmpty()) {
            // 1. Try component panel (has @Surface annotation)
            SurfaceSchema<?> componentSurface = resolveComponentSurface(item, path.get());
            if (componentSurface != null) {
                return componentSurface;
            }

            // 2. Try virtual directory listing
            List<Link> children = item.childrenAtPath(path.get());
            if (!children.isEmpty()) {
                return (SurfaceSchema) buildDirectoryListing(item, path.get(), children);
            }
        }

        // Selected item is a different IID (not a path within root)
        if (!context.item().equals(root.item())) {
            Optional<Item> contextItem = resolver.apply(context.item());
            if (contextItem.isPresent()) {
                Item ci = contextItem.get();
                SurfaceSchema s = resolveLiveSurface(ci);
                if (s != null) return s;

                // Check the item's components for a @Surface annotation.
                // This allows navigating to an Item (e.g., a clock) and
                // automatically seeing its component's rendered surface.
                for (FrameEntry entry : ci.content()) {
                    Object live = ci.component(entry.frameKey());
                    if (live != null) {
                        SurfaceSchema cs = resolveLiveSurface(live);
                        if (cs != null) return cs;
                    }
                }

                List<Link> children = ci.childrenAtPath("/");
                if (!children.isEmpty()) {
                    return (SurfaceSchema) buildDirectoryListing(ci, "/", children);
                }
            }
        }

        return null;
    }

    /**
     * Resolve a component path to its surface panel.
     *
     * <p>Follows the same path resolution as {@link Item#resolvePathDisplayToken}:
     * strip leading slash, resolve FrameKey, get live component, check @Surface.
     * Falls back to {@link SceneCompiler#compile} for auto-generated surfaces.
     *
     * <p>Also handles entry paths containing {@code #} (e.g., "/keyLog#abc123"),
     * delegating to {@link #resolveEntrySurface}.
     *
     * @param item the owning item
     * @param path the component path (e.g., "/chess", "/keyLog#abc123")
     * @return the component's surface panel, or null if not found
     */
    @SuppressWarnings("unchecked")
    private SurfaceSchema<?> resolveComponentSurface(Item item, String path) {
        // Handle entry paths
        if (path.contains("#")) {
            return resolveEntrySurface(item, path);
        }

        String handle = path.startsWith("/") ? path.substring(1) : path;
        FrameKey key = FrameKey.literal(handle);

        // Resolve by textual ref first, then by FrameKey.
        Object component = item.component(handle);
        if (component == null) {
            component = item.component(key);
        }
        if (component == null) {
            return null;
        }

        // 1. Try custom @Scene annotation
        Class<?> asClass = null;
        Scene sceneAnno = component.getClass().getAnnotation(Scene.class);
        if (sceneAnno != null && sceneAnno.as() != SceneSchema.class) {
            asClass = sceneAnno.as();
        }
        if (asClass != null) {
            if (SurfaceSchema.class.isAssignableFrom(asClass)) {
                try {
                    SurfaceSchema surface = ((Class<? extends SurfaceSchema>) asClass)
                            .getDeclaredConstructor().newInstance();
                    ((SurfaceSchema) surface).value(component);
                    return surface;
                } catch (Exception e) {
                    logger.warn("Failed to instantiate surface {} for component {}",
                            asClass.getSimpleName(), component.getClass().getSimpleName(), e);
                }
            } else if (SceneCompiler.canCompile(asClass)) {
                Object modelValue = SceneCompiler.resolveModelValue(component, asClass);
                SurfaceSchema<Object> surface = new SurfaceSchema<>() {};
                surface.value(modelValue);
                surface.structureClass(asClass);
                return surface;
            }
        }

        // 2. Fall back to SceneCompiler auto-generation (with resolver for ItemID enrichment)
        View view = SceneCompiler.compile(component, SceneMode.FULL, resolver);
        return view != null && view.root() != null ? view.root() : null;
    }

    /**
     * Resolve a {@code #entry} path to the entry value, then compile its surface.
     *
     * @param item the owning item
     * @param path the full path including fragment (e.g., "/keyLog#abc123")
     * @return a surface for the entry's value, or null if not found
     */
    private SurfaceSchema<?> resolveEntrySurface(Item item, String path) {
        String[] parts = path.split("#", 2);
        String compPath = parts[0];
        String entryId = parts[1];

        String handle = compPath.startsWith("/") ? compPath.substring(1) : compPath;
        FrameKey key = FrameKey.literal(handle);

        Object comp = item.component(key);
        if (comp == null) return null;
        if (comp instanceof Component cc) {
            Object entryValue = cc.inspectEntries().stream()
                    .filter(e -> e.id().equals(entryId))
                    .findFirst()
                    .map(Component.InspectEntry::value)
                    .orElse(null);
            if (entryValue != null) {
                View view = SceneCompiler.compile(entryValue, SceneMode.FULL, resolver);
                return view != null && view.root() != null ? view.root() : null;
            }
        }
        return null;
    }

    /**
     * Assemble surface mounts into a composite view.
     *
     * <p>In presentation mode, when the root item is selected, the detail pane
     * shows the "face" of the item — the assembled surface mounts. For example,
     * a book's cover image would be a surface mount, and that's what you see
     * when viewing the book as a whole.
     *
     * @param item the item whose surface mounts to assemble
     * @return a surface composing all surface-mounted components, or null if none
     */
    @SuppressWarnings("unchecked")
    private SurfaceSchema assembleSurfaceMounts(Item item) {
        List<SurfaceSchema> surfaces = new ArrayList<>();
        for (FrameEntry entry : item.content()) {
            for (Mount mount : entry.presentation().layout().mounts()) {
                if (mount instanceof Mount.SurfaceMount) {
                    Object live = item.component(entry.frameKey());
                    if (live != null) {
                        SurfaceSchema s = resolveLiveSurface(live);
                        if (s != null) surfaces.add(s);
                    }
                }
            }
        }
        if (surfaces.isEmpty()) return null;
        if (surfaces.size() == 1) return surfaces.getFirst();
        ContainerSurface container = ContainerSurface.vertical();
        surfaces.forEach(container::add);
        return container;
    }

    /**
     * Resolve a live component instance to its surface.
     *
     * <p>Checks for a {@link Surface @Surface} annotation on the component's class.
     * If found, instantiates the surface type and binds the component as its value.
     * Falls back to {@link SceneCompiler#compile} for auto-generated surfaces.
     *
     * @param component a live component instance
     * @return the component's surface, or null if no surface can be generated
     */
    @SuppressWarnings("unchecked")
    private SurfaceSchema resolveLiveSurface(Object component) {
        Class<?> asClass = null;
        Scene sceneAnno = component.getClass().getAnnotation(Scene.class);
        if (sceneAnno != null && sceneAnno.as() != SceneSchema.class) {
            asClass = sceneAnno.as();
        }
        if (asClass != null) {
            if (SurfaceSchema.class.isAssignableFrom(asClass)) {
                try {
                    SurfaceSchema surface = ((Class<? extends SurfaceSchema>) asClass)
                            .getDeclaredConstructor().newInstance();
                    surface.value(component);
                    return surface;
                } catch (Exception e) {
                    logger.warn("Failed to instantiate surface for {}",
                            component.getClass().getSimpleName(), e);
                }
            } else if (SceneCompiler.canCompile(asClass)) {
                // as points to a model class with surface annotations (not a SurfaceSchema)
                Object modelValue = SceneCompiler.resolveModelValue(component, asClass);
                SurfaceSchema<Object> surface = new SurfaceSchema<>() {};
                surface.value(modelValue);
                surface.structureClass(asClass);
                return surface;
            }
        }

        // Fall back to auto-generated surface from @Canon fields (with resolver for ItemID enrichment)
        View view = SceneCompiler.compile(component, SceneMode.FULL, resolver);
        return view != null && view.root() != null ? view.root() : null;
    }



    /**
     * Build a directory listing surface for a virtual directory.
     *
     * <p>Shows each child as a handle with click-to-navigate events.
     * Real components show their component emoji/name; virtual dirs show 📁.
     *
     * @param item the owning item
     * @param path the virtual directory path
     * @param children the child links at this path
     * @return a surface showing the directory contents
     */
    private SurfaceSchema<?> buildDirectoryListing(Item item, String path, List<Link> children) {
        ContainerSurface listing = ContainerSurface.vertical();
        listing.id("directory-listing");
        listing.gap("0.25em");

        // Header: show the directory name
        String segment = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;
        listing.add(TextSurface.of(segment).style("heading"));

        for (Link child : children) {
            String childPath = child.path().orElse("");
            String childName = childPath.contains("/")
                    ? childPath.substring(childPath.lastIndexOf('/') + 1)
                    : childPath;

            // Resolve emoji: real component → its emoji, virtual dir → 📁
            String emoji = item.resolvePathEmoji(childPath).orElse("📁");

            HandleSurface handle = HandleSurface.of(emoji, childName);
            listing.add(handle);
        }

        return listing;
    }

    /**
     * Prompt region — feedback line + input field.
     *
     * <p>The feedback line shows the result of the last evaluation for
     * the current context — an ephemeral query result from the session's
     * activity log, not stored data on this item. It appears above the
     * input field and disappears when the user starts typing.
     *
     * <p>The input field renders the full EvalInput state: prompt label,
     * token chips, pending text with cursor, and completions.
     */
    @Scene(id = "prompt", classes = {"input"})
    @Scene.Constraint(bottom = "0", left = "0", right = "100%", height = "fit")
    @Scene.Border(all = "0.15ln solid #B04A9D", radius = "0.4ch")
    public SurfaceSchema prompt() {
        SurfaceSchema inputSurface;

        if (inputSnapshot != null && renderInputInSurface) {
            inputSurface = InputSurface.fromSnapshot(inputSnapshot, resolver);
        } else if (inputSnapshot != null) {
            // Platform input renderer (JLineInputRenderer) handles display.
            inputSurface = null;
        } else {
            // No input snapshot — show static prompt (non-interactive mode)
            String promptText = resolver.apply(context.item())
                    .map(item -> {
                        String icon = item.emoji();
                        String label = item.displayToken();
                        return (icon != null ? icon + " " : "") + label + "> ";
                    })
                    .orElse("graph> ");
            inputSurface = InputSurface.empty(promptText, "");
        }

        // If there's feedback text, wrap input with a feedback line above it
        if (feedbackText != null && !feedbackText.isBlank()) {
            ContainerSurface prompt = ContainerSurface.vertical();
            TextSurface feedback = TextSurface.of(
                    (feedbackIsError ? "error: " : "  → ") + feedbackText);
            feedback.style(feedbackIsError ? "feedback-error" : "feedback");
            prompt.add(feedback);
            if (inputSurface != null) {
                prompt.add(inputSurface);
            }
            return prompt;
        }

        return inputSurface;
    }

    // ==================== Construction ====================

    public ItemModel(Link root, Function<ItemID, Optional<Item>> resolver) {
        this.root = root;
        this.context = root;  // Start with root selected
        this.resolver = resolver;
        rebuildTree();
    }

    /**
     * Create an ItemModel for an Item.
     */
    public static ItemModel of(Item item, Function<ItemID, Optional<Item>> resolver) {
        return new ItemModel(Link.of(item.iid()), resolver);
    }

    /**
     * Build (or rebuild) the tree model for the current root.
     *
     * <p>Creates a {@link TreeModel} backed by {@link TreeLink} nodes
     * using the current {@link #viewMode}. The root node is hidden —
     * its children appear as top-level items. Selection changes drive
     * the context (detail pane + prompt).
     */
    private void rebuildTree() {
        if (treePaneMode == TreePaneMode.PRESENTATION || treePaneMode == TreePaneMode.INSPECT) {
            TreeLink.ChildMode mode = treePaneMode == TreePaneMode.PRESENTATION
                    ? TreeLink.ChildMode.PRESENTATION
                    : TreeLink.ChildMode.INSPECT;
            TreeLink rootTreeLink = TreeLink.of(root, mode, resolver);
            treeModel = TreeModel.<TreeLink>builder(rootTreeLink)
                    .children(TreeLink::children)
                    .label(TreeLink::displayToken)
                    .iconSurface(link -> {
                        ImageSurface icon;
                        String resource = link.iconResource();
                        if (resource != null) {
                            icon = ImageSurface.ofResource(resource, link.emoji());
                        } else {
                            icon = ImageSurface.of(link.emoji());
                        }
                        int tint = link.typeColorArgb();
                        if (tint != 0) {
                            icon.color(tint);
                        }
                        return icon.size("medium").circle().backgroundColor("#3C3C4E");
                    })
                    .expandable(TreeLink::isExpandable)
                    .id(TreeLink::treeId)
                    .showRoot(false)
                    .build();
            treeModel.onChange(() -> {
                @SuppressWarnings("unchecked")
                TreeModel<TreeLink> navTree = (TreeModel<TreeLink>) treeModel;
                TreeLink selected = navTree.getSelected();
                if (selected != null) {
                    select(selected.target());
                }
            });
            return;
        }

        Optional<Item> focused = resolver.apply(context.item());
        if (focused.isEmpty()) {
            treeModel = null;
            configPanel = null;
            return;
        }

        if (treePaneMode == TreePaneMode.CONFIG) {
            treeModel = null;
            configPanel = buildConfigPanel(focused.get());
        } else {
            configPanel = null;
            treeModel = buildVocabularyTree(focused.get());
            treeModel.onChange(this::changed);
        }
    }

    /**
     * Refresh the tree model to reflect external state changes.
     *
     * <p>Call this after modifying an item's components or relations
     * outside the tree's own interaction (e.g., adding a component via
     * command dispatch). Rebuilds the tree from scratch, clearing all
     * cached TreeLink state.
     */
    public void refresh() {
        rebuildTree();
        changed();
    }

    // ==================== Navigation ====================

    /**
     * Navigate into an item (makes it the new root).
     *
     * <p>Pushes current root to history, sets new root.
     *
     * @param target The item to navigate into
     */
    public void navigateInto(Link target) {
        if (target == null) return;
        history.add(root);
        root = target;
        context = target;
        rebuildTree();
        changed();
    }

    /**
     * Navigate into an Item.
     */
    public void navigateInto(Item item) {
        if (item == null) return;
        navigateInto(Link.of(item.iid()));
    }

    /**
     * Go back to previous root.
     *
     * @return true if we went back, false if at start
     */
    public boolean goBack() {
        if (history.isEmpty()) {
            return false;
        }
        root = history.remove(history.size() - 1);
        context = root;
        rebuildTree();
        changed();
        return true;
    }

    /**
     * Check if we can go back.
     */
    public boolean canGoBack() {
        return !history.isEmpty();
    }

    /**
     * Select an item (changes context, not root).
     *
     * <p>Updates detail pane, prompt, and tree selection.
     *
     * @param target The item to select
     */
    public void select(Link target) {
        if (target == null) return;
        context = target;

        // Sync tree selection to match the new context
        if (treeModel != null && target.item() != null) {
            String treeId = "iid:" + target.item().encodeText();
            if (target.path().isPresent() && !target.path().get().isEmpty()) {
                treeId += target.path().get();
            }
            treeModel.select(treeId);
        }

        changed();
    }

    /**
     * Select an Item.
     */
    public void select(Item item) {
        if (item == null) return;
        select(Link.of(item.iid()));
    }

    // ==================== Key Handling ====================

    @Override
    public boolean handleKey(KeyChord chord) {
        for (TreeModeSpec mode : treeModes()) {
            if (mode.hotkey() != null && chord.isKey(mode.hotkey())) {
                setTreePaneMode(mode.mode());
                return true;
            }
        }

        if (treeModel != null) {
            // Alt+arrows → tree navigation
            if (chord.alt() && !chord.ctrl() && !chord.shift()) {
                if (chord.isKey(SpecialKey.UP)) { treeModel.selectPrevious(); return true; }
                if (chord.isKey(SpecialKey.DOWN)) { treeModel.selectNext(); return true; }
                if (chord.isKey(SpecialKey.LEFT)) {
                    if (treeModel.isExpanded(treeModel.getSelectedId())) {
                        treeModel.collapse(treeModel.getSelectedId());
                    } else {
                        treeModel.selectParent();
                    }
                    return true;
                }
                if (chord.isKey(SpecialKey.RIGHT)) {
                    if (treeModel.isExpanded(treeModel.getSelectedId())) {
                        treeModel.selectFirstChild();
                    } else {
                        treeModel.expand(treeModel.getSelectedId());
                    }
                    return true;
                }
            }
            // Alt+Enter → navigate into selected
            if (chord.alt()
                    && chord.isKey(SpecialKey.ENTER)
                    && (treePaneMode == TreePaneMode.PRESENTATION || treePaneMode == TreePaneMode.INSPECT)) {
                @SuppressWarnings("unchecked")
                TreeModel<TreeLink> navTree = (TreeModel<TreeLink>) treeModel;
                TreeLink sel = navTree.getSelected();
                if (sel != null) {
                    navigateInto(sel.target());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handleEvent(String action, String target) {
        if (action != null && action.startsWith("treeMode:")) {
            String mode = action.substring("treeMode:".length());
            switch (mode) {
                case "presentation" -> setTreePaneMode(TreePaneMode.PRESENTATION);
                case "inspect" -> setTreePaneMode(TreePaneMode.INSPECT);
                case "vocabulary" -> setTreePaneMode(TreePaneMode.VOCABULARY);
                case "config" -> setTreePaneMode(TreePaneMode.CONFIG);
                default -> { return false; }
            }
            return true;
        }
        if ("treeHint".equals(action)) {
            treeModeHint = target == null || target.isBlank() ? null : target;
            changed();
            return true;
        }
        // Config panel setting changes
        if ("configToggle".equals(action) && target != null) {
            return handleConfigToggle(target);
        }
        if ("configCycle".equals(action) && target != null) {
            return handleConfigCycle(target);
        }
        return switch (action) {
            case "back" -> goBack();
            case "select", "expand", "collapse", "toggle", "activate" -> {
                // Route tree events to the tree model
                yield treeModel != null && treeModel.handleEvent(action, target);
            }
            default -> {
                // Route to the focused item's verb dispatch
                Optional<Item> focused = resolver.apply(context.item());
                if (focused.isEmpty()) yield false;
                var result = focused.get().dispatch(action, target != null ? List.of(target) : List.of());
                yield result != null && result.success();
            }
        };
    }

    // ==================== Config Setting Handlers ====================

    /**
     * Handle a boolean toggle from the config panel.
     *
     * @param fieldKey "handleKey:fieldName" identifying the component and field
     */
    private boolean handleConfigToggle(String fieldKey) {
        var resolved = resolveConfigField(fieldKey);
        if (resolved == null) return false;

        boolean current = resolved.fs().getValue(resolved.instance()) instanceof Boolean b && b;
        resolved.fs().setValue(resolved.instance(), !current);
        rebuildTree();
        changed();
        return true;
    }

    /**
     * Handle an enum cycle from the config panel.
     *
     * @param fieldKey "handleKey:fieldName" identifying the component and field
     */
    private boolean handleConfigCycle(String fieldKey) {
        var resolved = resolveConfigField(fieldKey);
        if (resolved == null) return false;

        Object[] constants = resolved.fs().enumConstants();
        if (constants == null || constants.length == 0) return false;

        Object current = resolved.fs().getValue(resolved.instance());
        int idx = 0;
        if (current != null) {
            for (int i = 0; i < constants.length; i++) {
                if (constants[i].equals(current)) {
                    idx = (i + 1) % constants.length;
                    break;
                }
            }
        }
        resolved.fs().setValue(resolved.instance(), constants[idx]);
        rebuildTree();
        changed();
        return true;
    }

    /**
     * Resolve a config field key ("handleKey:fieldName") to the live instance and field schema.
     */
    private ConfigFieldRef resolveConfigField(String fieldKey) {
        int sep = fieldKey.lastIndexOf(':');
        if (sep <= 0) return null;

        String handleKey = fieldKey.substring(0, sep);
        String fieldName = fieldKey.substring(sep + 1);

        Optional<Item> focused = resolver.apply(context.item());
        if (focused.isEmpty()) return null;

        for (FrameEntry entry : focused.get().content()) {
            if (!entry.frameKey().toCanonicalString().equals(handleKey)) continue;

            Object instance = entry.instance();
            if (instance == null) return null;

            CanonicalSchema schema = CanonicalSchema.of(instance.getClass());
            for (CanonicalSchema.FieldSchema fs : schema.fields()) {
                if (fs.isSetting() && fs.name().equals(fieldName)) {
                    return new ConfigFieldRef(instance, fs, entry);
                }
            }
        }
        return null;
    }

    private record ConfigFieldRef(Object instance, CanonicalSchema.FieldSchema fs, FrameEntry entry) {}

    private SurfaceSchema buildTreeModeBar() {
        ContainerSurface modeBar = ContainerSurface.horizontal()
                .gap("0.2em")
                .style("tree-mode-bar");

        for (TreeModeSpec mode : treeModes()) {
            modeBar.add(modeButton(mode.glyph(), mode.action(), treePaneMode == mode.mode(), mode.hint()));
        }
        if (treeModeHint != null && !treeModeHint.isBlank()) {
            modeBar.add(TextSurface.of("  " + treeModeHint).style("muted"));
        }
        return modeBar;
    }

    private ButtonSurface modeButton(String glyph, String action, boolean active, String hint) {
        TextSurface glyphSurface = TextSurface.of(glyph).style("icon");
        glyphSurface.onClick(action);
        glyphSurface.on("hover", "treeHint", hint);

        ButtonSurface button = ButtonSurface.action(action).ghost().style("tree-mode-button");
        button.add(glyphSurface);
        button.on("hover", "treeHint", hint);
        if (active) {
            button.style("active");
        }
        return button;
    }

    // ==================== Config Panel ====================

    /**
     * Build the config panel for an item.
     *
     * <p>Shows editable settings for each component that has
     * {@code @Canon(setting = true)} fields. Components are shown as
     * accordion sections; settings render as appropriate widgets
     * (toggle for boolean, dropdown for enum, text for string/number).
     */
    private SurfaceSchema<?> buildConfigPanel(Item item) {
        ContainerSurface panel = ContainerSurface.vertical()
                .gap("0.5em")
                .style("config-panel");

        List<FrameEntry> entries = new ArrayList<>();
        item.content().forEach(entries::add);
        entries.sort(Comparator.comparing(FrameEntry::displayToken, String.CASE_INSENSITIVE_ORDER));

        boolean hasSettings = false;
        for (FrameEntry entry : entries) {
            ContainerSurface section = buildComponentConfigSection(entry);
            if (section != null) {
                panel.add(section);
                hasSettings = true;
            }
        }

        if (!hasSettings) {
            panel.add(TextSurface.of("No configurable settings").style("muted"));
        }

        return panel;
    }

    /**
     * Build a config section for a single component.
     *
     * <p>Shows {@code @Canon(setting = true)} fields as editable widgets,
     * plus the component's policy if present.
     *
     * @return a container with header + settings + policy, or null if nothing to show
     */
    private ContainerSurface buildComponentConfigSection(FrameEntry entry) {
        // Gather settings from live instance
        List<CanonicalSchema.FieldSchema> settings = List.of();
        Object instance = entry.instance();
        if (instance != null) {
            CanonicalSchema schema = CanonicalSchema.of(instance.getClass());
            settings = schema.fields().stream()
                    .filter(CanonicalSchema.FieldSchema::isSetting)
                    .toList();
        }

        PolicySet policy = entry.policy();

        // Nothing to show for this component
        if (settings.isEmpty() && policy == null) return null;

        String handleKey = entry.frameKey().toCanonicalString();

        // Section container
        ContainerSurface section = ContainerSurface.vertical()
                .gap("0.3em")
                .style("config-section");

        // Component header
        ContainerSurface header = ContainerSurface.horizontal()
                .gap("0.4em")
                .style("config-section-header");
        header.add(ImageSurface.of(entry.emoji()).size("small"));
        header.add(TextSurface.of(entry.displayToken()).style("config-section-title"));
        section.add(header);

        // Setting widgets
        for (CanonicalSchema.FieldSchema fs : settings) {
            Object value = fs.getValue(instance);
            String fieldKey = handleKey + ":" + fs.name();
            SurfaceSchema<?> widget = buildSettingWidget(fs, value, fieldKey);
            if (widget != null) {
                section.add(widget);
            }
        }

        // Policy
        if (policy != null) {
            ContainerSurface policyRow = ContainerSurface.horizontal()
                    .gap("0.5em")
                    .style("config-field");
            policyRow.add(TextSurface.of("Policy").style("config-label"));
            policyRow.add(TextSurface.of(policy.getClass().getSimpleName()).style("config-value"));
            section.add(policyRow);
        }

        return section;
    }

    /**
     * Build the appropriate editable widget for a setting field.
     *
     * <p>Boolean → toggle switch. Enum → cycling button (click to advance).
     * Numeric/string → read-only display (editable input TODO).
     */
    private SurfaceSchema<?> buildSettingWidget(
            CanonicalSchema.FieldSchema fs, Object value, String fieldKey) {

        if (fs.isBoolean()) {
            boolean on = value instanceof Boolean b && b;
            ToggleSurface toggle = ToggleSurface.of(on, fs.displayName())
                    .editable(true);
            toggle.id("cfg:" + fieldKey);
            toggle.onClick("configToggle", fieldKey);
            return toggle;
        }

        if (fs.isEnum()) {
            String display = value != null ? ((Enum<?>) value).name() : "—";
            ContainerSurface row = ContainerSurface.horizontal()
                    .gap("0.5em")
                    .style("config-field");
            row.add(TextSurface.of(fs.displayName()).style("config-label"));
            ButtonSurface cycleButton = ButtonSurface.of(display, "configCycle");
            cycleButton.on("click", "configCycle", fieldKey);
            cycleButton.style("config-enum-button");
            row.add(cycleButton);
            return row;
        }

        if (fs.isNumeric() || fs.isString()) {
            String display = value != null ? value.toString() : "";
            ContainerSurface row = ContainerSurface.horizontal()
                    .gap("0.5em")
                    .style("config-field");
            row.add(TextSurface.of(fs.displayName()).style("config-label"));
            row.add(TextSurface.of(display).style("config-value"));
            return row;
        }

        // Fallback: read-only display
        String display = value != null ? value.toString() : "null";
        ContainerSurface row = ContainerSurface.horizontal()
                .gap("0.5em")
                .style("config-field");
        row.add(TextSurface.of(fs.displayName()).style("config-label"));
        row.add(TextSurface.of(display).style("config-value", "muted"));
        return row;
    }

    private TreeModel<MetaNode> buildVocabularyTree(Item item) {
        MetaNode rootNode = new MetaNode("vocab-root", "📖", "Vocabulary", new ArrayList<>());

        List<MetaNode> verbChildren = new ArrayList<>();
        for (VerbEntry verb : item.vocabulary()) {
            String label = shortSememe(verb.sememeId()) + " → " + verb.methodName();
            verbChildren.add(new MetaNode(
                    "vocab:verb:" + verb.sememeId().encodeText(),
                    "🗣️",
                    label,
                    List.of()));
        }
        if (!verbChildren.isEmpty()) {
            rootNode.children.add(new MetaNode("vocab:verbs", "🧭", "Effective Verbs", verbChildren));
        }

        List<FrameEntry> entries = new ArrayList<>();
        item.content().forEach(entries::add);
        entries.sort(Comparator.comparing(FrameEntry::displayToken, String.CASE_INSENSITIVE_ORDER));
        for (FrameEntry entry : entries) {
            Map<String, List<FrameEntry.VocabularyContribution>> byScope = new LinkedHashMap<>();
            for (FrameEntry.VocabularyContribution term : entry.vocabulary().contributions()) {
                String scope = term.scope() == null || term.scope().isBlank()
                        ? "/"
                        : term.scope();
                byScope.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(term);
            }
            if (byScope.isEmpty()) continue;

            List<MetaNode> componentChildren = new ArrayList<>();
            for (Map.Entry<String, List<FrameEntry.VocabularyContribution>> scoped : byScope.entrySet()) {
                List<MetaNode> scopeChildren = new ArrayList<>();
                for (FrameEntry.VocabularyContribution term : scoped.getValue()) {
                    String token = term.token();
                    if (token == null || token.isBlank()) {
                        token = term.termRef() != null ? shortSememe(term.termRef()) : "(unnamed)";
                    }
                    String label = token;
                    scopeChildren.add(new MetaNode(
                            "vocab:" + entry.frameKey().toCanonicalString() + ":" + scoped.getKey() + ":" + token,
                            "🏷️",
                            label,
                            List.of()));
                }
                componentChildren.add(new MetaNode(
                        "vocab:" + entry.frameKey().toCanonicalString() + ":" + scoped.getKey(),
                        "📍",
                        "Scope " + scoped.getKey(),
                        scopeChildren));
            }

            rootNode.children.add(new MetaNode(
                    "vocab:" + entry.frameKey().toCanonicalString(),
                    entry.emoji(),
                    entry.displayToken(),
                    componentChildren));
        }

        if (rootNode.children.isEmpty()) {
            rootNode.children.add(new MetaNode("vocab:none", "ℹ️", "No vocabulary metadata on current context", List.of()));
        }
        return metaTree(rootNode);
    }

    private TreeModel<MetaNode> metaTree(MetaNode rootNode) {
        return TreeModel.<MetaNode>builder(rootNode)
                .children(node -> node.children)
                .label(node -> node.label)
                .icon(node -> node.icon)
                .expandable(node -> !node.children.isEmpty())
                .id(node -> node.id)
                .showRoot(false)
                .build();
    }

    private String shortSememe(ItemID sememeId) {
        String encoded = sememeId.encodeText();
        int colon = encoded.lastIndexOf(':');
        return (colon >= 0 && colon < encoded.length() - 1) ? encoded.substring(colon + 1) : encoded;
    }

    private static final class MetaNode {
        private final String id;
        private final String icon;
        private final String label;
        private final List<MetaNode> children;

        private MetaNode(String id, String icon, String label, List<MetaNode> children) {
            this.id = id;
            this.icon = icon;
            this.label = label;
            this.children = children != null ? children : List.of();
        }
    }

    // ==================== Surface Generation ====================

    /**
     * Generate surface by compiling from annotations.
     *
     * <p>The layout is defined declaratively via {@link Surface} and
     * {@link ConstraintSurface.Layout} annotations on this model's methods.
     * {@link SurfaceLayoutCompiler} reads these and builds the layout.
     */
    @Override
    public SurfaceSchema toSurface() {
        return SceneCompiler.compile(this).root();
    }

    /**
     * Generate a 3D scene.
     *
     * @return A SpatialSchema for 3D rendering
     */
    public SpatialSchema<?> toScene() {
        if (treeModel != null) {
            return treeModel.toScene();
        }
        return new ItemSpace();
    }
}
