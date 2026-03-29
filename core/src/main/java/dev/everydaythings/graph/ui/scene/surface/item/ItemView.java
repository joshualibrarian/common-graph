package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.dispatch.ParamSpec;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.item.HandleResolver;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.TreeLink;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.parse.CompletionEntry;
import dev.everydaythings.graph.parse.ExpressionToken;
import dev.everydaythings.graph.parse.InputSnapshot;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.Text;
import dev.everydaythings.graph.ui.scene.node.TreeNav;
import dev.everydaythings.graph.ui.scene.node.TreeNodes;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The universal item chrome — header, tree, detail, prompt.
 *
 * <p>Layout is declarative via static inner classes with {@code @Scene} annotations.
 * The class hierarchy IS the layout. Dynamic content is exposed through view-model
 * methods that binding expressions resolve against. Only genuinely dynamic content
 * (tree building, input rendering) is procedural.
 *
 * <p>This class is the reference pattern for building Common Graph UI surfaces.
 * Structure in annotations. Style in rules. Logic in methods. Nothing hardcoded.
 */
@Scene.Root
@Scene.Container(direction = Direction.VERTICAL, id = "item-view", height = "100%")
@Scene.Rule(match = ".header", background = "#1E1E2E")
@Scene.Rule(match = ".mode-btn", opacity = "dim")
@Scene.Rule(match = ".mode-btn-active", background = "#3C3C4E", opacity = "bright")
@Scene.Rule(match = ".muted", opacity = "dim")
@Scene.Rule(match = ".bold", fontWeight = "bold")
@Scene.Rule(match = ".mono", fontFamily = "monospace")
@Scene.Rule(match = ".feedback-error", color = "#F38BA8")
@Scene.On(event = "F1", action = "toggle:help")
@Scene.On(event = "F2", action = "toggle:mounts")
@Scene.On(event = "F3", action = "toggle:frames")
@Scene.On(event = "F4", action = "toggle:versions")
public class ItemView {

    // ==================================================================================
    // Declarative Layout — inner classes define the structure
    // ==================================================================================

    // ── Handle (compact identity for accordion, breadcrumbs, tree nodes) ─────

    @Scene.Handle
    @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.5em", align = "center")
    static class Handle {
        @Scene.Image(bind = "value.itemEmoji", size = "1em")
        static class Icon {}

        @Scene.Text.Literal(bind = "value.typeName", style = {"bold"})
        static class TypeName {}

        @Scene.If("value.handleLabel")
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.25em")
        static class Label {
            @Scene.Text.Literal(content = "\u2014", style = {"muted"})
            static class Dash {}

            @Scene.Text.Literal(bind = "value.handleLabel")
            static class LabelText {}
        }
    }

    // ── Header (order=0) ────────────────────────────────────────────────────

    @Scene.Container(order = 0, direction = Direction.HORIZONTAL, id = "header",
            style = {"header"}, gap = "0.5em", padding = "0.3em")
    static class Header {
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.5em", align = "center")
        static class Identity {
            @Scene.Image(bind = "value.itemEmoji", size = "1.2em")
            static class Icon {}

            @Scene.Text.Literal(bind = "value.typeName", style = {"bold"})
            static class TypeName {}

            @Scene.If("value.handleLabel")
            @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.25em")
            static class Label {
                @Scene.Text.Literal(content = "\u2014", style = {"muted"})
                static class Dash {}

                @Scene.Text.Literal(bind = "value.handleLabel")
                static class LabelText {}
            }
        }

        @Scene.Container(width = "1fr")
        static class Spacer {}

        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.2em")
        static class ModeBar {
            @Scene.On(event = "click", action = "toggle:help")
            @Scene.State(style = {"mode-btn-active"}, when = "value.helpVisible")
            @Scene.State(style = {"mode-btn"}, when = "!value.helpVisible")
            @Scene.Text.Literal(content = "\uD83D\uDCD6")
            static class HelpBtn {}

            @Scene.On(event = "click", action = "toggle:mounts")
            @Scene.State(style = {"mode-btn-active"}, when = "value.mountsActive")
            @Scene.State(style = {"mode-btn"}, when = "!value.mountsActive")
            @Scene.Text.Literal(content = "\uD83D\uDDC2")
            static class MountsBtn {}

            @Scene.On(event = "click", action = "toggle:frames")
            @Scene.State(style = {"mode-btn-active"}, when = "value.framesActive")
            @Scene.State(style = {"mode-btn"}, when = "!value.framesActive")
            @Scene.Text.Literal(content = "\uD83D\uDCCB")
            static class FramesBtn {}

            @Scene.On(event = "click", action = "toggle:versions")
            @Scene.State(style = {"mode-btn-active"}, when = "value.versionsActive")
            @Scene.State(style = {"mode-btn"}, when = "!value.versionsActive")
            @Scene.Text.Literal(content = "\uD83D\uDCDC")
            static class VersionsBtn {}
        }
    }

    // ── Body (order=1) ──────────────────────────────────────────────────────

    @Scene.Container(order = 1, direction = Direction.HORIZONTAL, id = "body", height = "1fr")
    static class BodyArea {
        @Scene.If("value.treeVisible")
        @Scene.Container(direction = Direction.VERTICAL, id = "tree", style = {"tree-panel"},
                gap = "0.25em", overflow = "auto")
        static class TreePanel {
            @Scene.Embed(bind = "value.treeContent")
            static class Tree {}
        }

        @Scene.Container(direction = Direction.VERTICAL, id = "detail",
                width = "1fr", overflow = "auto")
        static class DetailPanel {
            @Scene.Embed(bind = "value.detailContent")
            static class Detail {}
        }
    }

    // ── Prompt (order=2) ────────────────────────────────────────────────────

    @Scene.Container(order = 2, direction = Direction.VERTICAL, id = "prompt")
    static class PromptArea {
        @Scene.If("value.hasFeedback")
        @Scene.State(style = {"feedback-error"}, when = "value.feedbackIsError")
        @Scene.Text.Literal(bind = "value.feedbackDisplay")
        static class Feedback {}

        @Scene.Embed(bind = "value.inputNode")
        static class Input {}
    }

    // Key events are declared on the root class via @Scene.On above.

    // ==================================================================================
    // View-Model — methods that binding expressions resolve against
    // ==================================================================================

    /** Item emoji for icon display. */
    public String itemEmoji() {
        Item ctx = item();
        return ctx != null && ctx.emoji() != null ? ctx.emoji() : "";
    }

    /** Type display name (resolved from @Implements key). */
    public String typeName() {
        Item ctx = item();
        if (ctx == null) return "";
        ItemID typeId = typeIdOf(ctx);
        if (typeId == null) return ctx.displayToken();
        String resolved = ctx.resolveDisplayToken(typeId);
        return resolved != null ? resolved : ctx.displayToken();
    }

    /** Distinguishing label from HandleResolver (null if none). */
    public String handleLabel() {
        Item ctx = item();
        if (ctx == null) return null;
        Collection<Item> siblings = siblingsProvider != null
                ? siblingsProvider.get() : List.of();
        return HandleResolver.resolve(ctx, siblings);
    }

    /** Whether the help panel (F1) is visible. */
    public boolean helpVisible() { return helpVisible; }

    /** Whether any tree panel is visible. */
    public boolean treeVisible() { return activeTreeView != null; }

    /** Whether the mounts tree (F2) is active. */
    public boolean mountsActive() { return activeTreeView == TreeView.MOUNTS; }

    /** Whether the frames tree (F3) is active. */
    public boolean framesActive() { return activeTreeView == TreeView.FRAMES; }

    /** Whether the versions tree (F4) is active. */
    public boolean versionsActive() { return activeTreeView == TreeView.VERSIONS; }

    /** Whether there is feedback text to display. */
    public boolean hasFeedback() { return feedbackText != null && !feedbackText.isBlank(); }

    /** Formatted feedback text with prefix. */
    public String feedbackDisplay() {
        if (feedbackText == null) return "";
        return (feedbackIsError ? "! " : "  \u2192 ") + feedbackText;
    }

    /** Whether the feedback is an error (for styling). */
    public boolean feedbackIsError() { return feedbackIsError; }

    // ==================================================================================
    // Dynamic Content — procedural node building for @Scene.Embed
    // ==================================================================================

    /**
     * Tree content node — built from the active tree view (mounts/frames/versions).
     *
     * <p>Tree building is complex data transformation. The tree structure is built
     * procedurally via TreeNodes, then returned as a pre-built Node for embedding.
     */
    public Node treeContent() { return treeContentNode; }

    /**
     * Detail panel content — routes to help, frame detail, or item scene.
     *
     * <p>The routing logic is inherently conditional (which mode are we in?
     * what's selected in the tree?). Each branch produces a Node.
     */
    public Node detailContent() {
        if (helpVisible) return helpContent();
        if (selectedTreeNodeId != null && activeTreeView != null) return selectedNodeContent();
        return itemContent();
    }

    /**
     * Input node — the prompt/input area built from InputSnapshot.
     *
     * <p>Token chips, cursor positioning, and completion lists are genuinely
     * dynamic and require procedural construction.
     */
    public Node inputNode() {
        if (inputSnapshot != null && renderInputInSurface) {
            return inputFromSnapshot(inputSnapshot);
        }
        if (inputSnapshot != null) return null;

        // Empty prompt placeholder
        Item ctx = item();
        String p = ctx != null
                ? (ctx.emoji() != null ? ctx.emoji() + " " : "") + ctx.displayToken() + "> "
                : "> ";
        Container empty = Container.vertical().classes("input-surface");
        Container row = Container.horizontal().gap("0.25em").classes("input-row");
        row.add(Text.of(p).classes("prompt"));
        empty.add(row);
        return empty;
    }

    // ==================================================================================
    // State
    // ==================================================================================

    private Ref root;
    private Ref context;
    private final List<Ref> history = new ArrayList<>();
    private final Function<ItemID, Optional<Item>> resolver;
    private Supplier<Collection<Item>> siblingsProvider;

    private TreeView activeTreeView = null;
    private boolean helpVisible = false;
    private TreeNav<?> treeNav;
    private Node treeContentNode;

    private Map<String, Map<String, Object>> stateStore;
    private String selectedTreeNodeId;
    private ViewHandle activeView;
    private InputSnapshot inputSnapshot;
    private boolean renderInputInSurface = true;
    private String feedbackText;
    private boolean feedbackIsError;

    public enum TreeView { MOUNTS, FRAMES, VERSIONS }

    // ==================================================================================
    // Change Notification
    // ==================================================================================

    private Runnable onChanged;
    public void onChange(Runnable listener) { this.onChanged = listener; }
    protected void changed() { if (onChanged != null) onChanged.run(); }

    // ==================================================================================
    // Constructor
    // ==================================================================================

    public ItemView(Item item, Function<ItemID, Optional<Item>> resolver) {
        this.root = Ref.of(item.iid());
        this.context = this.root;
        this.resolver = resolver;
        watchFrames();
    }

    /** Currently watched item (for unsubscribing on navigation). */
    private transient Item watchedItem;

    /**
     * Subscribe to the current item's frame changes.
     * When frames are added/removed, the tree rebuilds and the UI re-renders.
     */
    private void watchFrames() {
        // Unsubscribe from old item
        if (watchedItem != null && watchedItem.frames() != null) {
            watchedItem.frames().onChanged(null);
        }

        Item current = item();
        watchedItem = current;

        if (current != null && current.frames() != null) {
            current.frames().onChanged(() -> {
                rebuildTree();
                changed();
            });
        }
    }

    // ==================================================================================
    // Compiled Node Tree
    // ==================================================================================

    public Node toNode() {
        return SceneCompiler.compileToNode(this);
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    public Ref root() { return root; }
    public Ref context() { return context; }

    public Item item() {
        return resolver.apply(context.target()).orElse(null);
    }

    public void navigateInto(Ref target) {
        if (target == null) return;
        history.add(root);
        root = target; context = target;
        watchFrames();
        rebuildTree(); changed();
    }

    public void navigateInto(Item item) {
        if (item == null) return;
        navigateInto(Ref.of(item.iid()));
    }

    public boolean goBack() {
        if (history.isEmpty()) return false;
        root = history.removeLast(); context = root;
        watchFrames();
        rebuildTree(); changed();
        return true;
    }

    public boolean canGoBack() { return !history.isEmpty(); }

    public void select(Ref target) {
        if (target == null) return;
        this.context = target;
        if (treeNav != null && target.target() != null)
            treeNav.select(target.target().encodeText());
        changed();
    }

    public void select(Item item) {
        if (item == null) return;
        select(Ref.of(item.iid()));
    }

    public void refresh() { rebuildTree(); changed(); }

    // ==================================================================================
    // Input / View State
    // ==================================================================================

    public void updateInput(InputSnapshot snapshot) { this.inputSnapshot = snapshot; changed(); }
    public void setFeedback(String text, boolean isError) {
        this.feedbackText = text; this.feedbackIsError = isError; changed();
    }
    public void clearFeedback() { this.feedbackText = null; this.feedbackIsError = false; }
    public void setRenderInputInSurface(boolean render) { this.renderInputInSurface = render; }
    public void setSiblingsProvider(Supplier<Collection<Item>> provider) {
        this.siblingsProvider = provider;
    }
    public void setStateStore(Map<String, Map<String, Object>> store) { this.stateStore = store; }
    public InputSnapshot inputSnapshot() { return inputSnapshot; }
    public void setActiveView(ViewHandle view) { this.activeView = view; changed(); }
    public void clearActiveView() { this.activeView = null; changed(); }
    public boolean hasActiveView() { return activeView != null; }
    public ViewHandle activeView() { return activeView; }

    // ==================================================================================
    // Toggles
    // ==================================================================================

    public void toggleHelp() { helpVisible = !helpVisible; changed(); }
    public void toggleTreeView(TreeView view) {
        activeTreeView = (activeTreeView == view) ? null : view;
        rebuildTree(); changed();
    }
    public TreeView activeTreeView() { return activeTreeView; }

    // ==================================================================================
    // Detail Content (procedural — routing logic)
    // ==================================================================================

    private Node selectedNodeContent() {
        Item resolved = item();
        if (resolved == null) return Text.of("");

        // Try as an ItemID — shows the item's scene
        try {
            ItemID iid = ItemID.fromString(selectedTreeNodeId);
            Optional<Item> selected = resolver.apply(iid);
            if (selected.isPresent()) return itemContent(selected.get());
        } catch (Exception ignored) {}

        // Try as a frame reference — shows the frame's bindings
        if (resolved.frames() != null) {
            for (Frame frame : resolved.frames()) {
                String id = "frame:" + (frame.bodyHash() != null
                        ? frame.bodyHash().displayAtWidth(12) : frame.frameKey().toString());
                if (id.equals(selectedTreeNodeId)) {
                    return buildFrameDetail(frame, resolved);
                }
            }
        }

        return itemContent();
    }

    private Node buildFrameDetail(Frame frame, Item resolved) {
        Container detail = Container.vertical().gap("0.5em");

        // Heading — resolved predicate name
        String pred = FrameNode.resolvePredicate(frame, resolved);
        detail.add(Text.of(pred).fontWeight("bold").classes("heading"));

        // Render the CBOR content — body or instance, whichever is available
        java.util.function.Function<ItemID, String> resolver = iid -> {
            if (resolved.itemLibrarian() == null) return null;
            return resolved.itemLibrarian().get(iid)
                    .map(item -> {
                        String emoji = item.emoji();
                        String name = item.displayToken();
                        return (emoji != null ? emoji + " " : "") + name;
                    })
                    .orElse(null);
        };
        ItemID typeId = frame.type() != null ? frame.type() : FrameBody.TYPE_ID;
        if (frame.body() != null) {
            detail.add(CborInspector.render(frame.body(), resolver, FrameBody.TYPE_ID));
        } else if (frame.instance() instanceof Canonical canonical) {
            detail.add(CborInspector.render(canonical, resolver, typeId));
        }

        // Hash footer
        if (frame.bodyHash() != null) {
            detail.add(Text.of("Hash: " + frame.bodyHash().fullDisplay()).classes("mono", "muted"));
        }

        return detail;
    }

    private Node itemContent(Item resolved) {
        Class<?> clazz = resolved.getClass();
        if (clazz != Item.class && SceneCompiler.has2DAnnotation(clazz)) {
            try {
                Node content = SceneCompiler.compileToNode(resolved);
                if (content != null) return content;
            } catch (Exception ignored) {}
        }
        return defaultItemSummary(resolved);
    }

    private Node itemContent() {
        Item resolved = item();
        if (resolved == null) return Text.of("");
        return itemContent(resolved);
    }

    private Node defaultItemSummary(Item resolved) {
        Container s = Container.vertical().gap("0.5em");
        s.add(Text.ofSememe(resolved.iid()).fontWeight("bold").classes("heading"));
        if (resolved.iid() != null)
            s.add(Text.of("IID: " + resolved.iid().displayAtWidth(20)).classes("mono", "muted"));
        int frames = 0;
        if (resolved.frames() != null) for (var f : resolved.frames()) frames++;
        s.add(Text.of(frames + " frames").classes("muted"));
        s.add(Text.of(resolved.vocabulary().size() + " verbs").classes("muted"));
        return s;
    }

    // ==================================================================================
    // Help Content (procedural — vocabulary iteration)
    // ==================================================================================

    private Node helpContent() {
        Item ctx = item();
        Librarian lib = ctx != null ? ctx.itemLibrarian() : null;
        Container help = Container.vertical().gap("0.5em");
        if (ctx != null) help.add(scopeSection(ctx.iid(), ctx.vocabulary()));
        if (lib != null) help.add(scopeSection(lib.iid(), lib.vocabulary()));
        return help;
    }

    private Node scopeSection(ItemID nameId, Vocabulary vocab) {
        Container s = Container.vertical().gap("0.25em");
        s.add(Text.ofSememe(nameId).fontWeight("bold"));
        if (vocab != null) {
            List<VerbEntry> verbs = new ArrayList<>();
            for (VerbEntry v : vocab) verbs.add(v);
            if (!verbs.isEmpty()) {
                Container vl = Container.vertical().gap("0.125em");
                for (VerbEntry v : verbs) vl.add(verbRow(v));
                s.add(vl);
            }
            List<Posting> tokens = vocab.prefixMatch("");
            if (!tokens.isEmpty()) {
                Container tl = Container.vertical().gap("0.0625em");
                for (Posting p : tokens) {
                    Container row = Container.horizontal().gap("0.5em");
                    row.add(Text.of(p.token()).fontWeight("bold"));
                    row.add(Text.of("\u2192").classes("muted"));
                    row.add(Text.of(p.target() != null ? p.target().displayAtWidth(16) : "\u2014").classes("muted"));
                    tl.add(row);
                }
                s.add(tl);
            }
        }
        return s;
    }

    private Node verbRow(VerbEntry verb) {
        Container r = Container.vertical();
        Container nameRow = Container.horizontal().gap("0.5em");
        String mn = verb.methodName();
        String dn = mn.startsWith("action") ? mn.substring(6, 7).toLowerCase() + mn.substring(7) : mn;
        nameRow.add(Text.of(dn).fontWeight("bold"));
        if (verb.doc() != null) nameRow.add(Text.of(verb.doc()).classes("muted"));
        r.add(nameRow);
        if (verb.params() != null && !verb.params().isEmpty()) {
            Container params = Container.vertical();
            for (ParamSpec p : verb.params()) {
                Container pr = Container.horizontal().gap("0.25em");
                pr.add(Text.of("  " + p.name()).fontWeight("bold"));
                pr.add(Text.of(": " + p.type().getSimpleName()).classes("muted"));
                params.add(pr);
            }
            r.add(params);
        }
        return r;
    }

    // ==================================================================================
    // Input Rendering (procedural — tokens, cursor, completions)
    // ==================================================================================

    private Node inputFromSnapshot(InputSnapshot snap) {
        Container outer = Container.vertical().gap("0.25em").classes("input-surface");
        Container row = Container.horizontal().gap("0.25em").classes("input-row");
        if (snap.prompt() != null && !snap.prompt().isEmpty())
            row.add(Text.of(snap.prompt()).classes("prompt"));
        for (ExpressionToken token : snap.tokens()) row.add(tokenChip(token));
        String pending = snap.pendingText() != null ? snap.pendingText() : "";
        boolean hasContent = !snap.tokens().isEmpty() || !pending.isEmpty();
        if (!pending.isEmpty()) {
            row.add(Text.of(insertCursor(pending, snap.cursor())).editable(true).classes("pending"));
        } else if (!hasContent && snap.hint() != null && !snap.hint().isEmpty()) {
            row.add(Text.of(snap.hint()).classes("hint", "muted"));
        } else {
            row.add(Text.of("").editable(true));
        }
        outer.add(row);
        if (snap.error() != null && !snap.error().isEmpty())
            outer.add(Text.of(snap.error()).classes("error"));
        if (snap.showCompletions() && snap.completionEntries() != null && !snap.completionEntries().isEmpty())
            outer.add(completionsList(snap.completionEntries(), snap.selectedCompletion()));
        return outer;
    }

    private Node tokenChip(ExpressionToken token) {
        boolean resolved = token instanceof ExpressionToken.RefToken;
        String emoji = null;
        if (token instanceof ExpressionToken.RefToken ref && resolver != null) {
            try {
                Optional<Item> it = resolver.apply(ref.target());
                if (it.isPresent()) emoji = it.get().emoji();
            } catch (Exception ignored) {}
        }
        if (resolved) {
            Container c = Container.horizontal().classes("token-chip", "token-chip-resolved")
                    .padding("0.1em 0.4em");
            if (emoji != null && !emoji.isEmpty()) c.add(Text.of(emoji));
            c.add(Text.of(token.displayText()));
            return c;
        }
        Container c = Container.horizontal().classes("token-chip");
        c.add(Text.of(token.displayText()));
        return c;
    }

    private Node completionsList(List<CompletionEntry> entries, int selected) {
        Container list = Container.vertical().gap("0.125em").classes("completions");
        for (int i = 0; i < entries.size(); i++) {
            CompletionEntry e = entries.get(i);
            boolean sel = (i == selected);
            Container row = Container.horizontal().gap("0.5em")
                    .classes(sel ? "completion completion-selected" : "completion");
            row.add(Text.of(sel ? "\u25B8 " : "  ").classes(sel ? "completion-indicator" : "completion-spacer"));
            if (e.emoji() != null && !e.emoji().isEmpty()) row.add(Text.of(e.emoji()));
            row.add(Text.of(e.token()));
            if (e.typeName() != null && !e.typeName().isEmpty()) row.add(Text.of(e.typeName()).classes("muted"));
            list.add(row);
        }
        return list;
    }

    // ==================================================================================
    // Tree Building
    // ==================================================================================

    private void rebuildTree() {
        if (activeTreeView == null) { treeNav = null; treeContentNode = null; return; }
        switch (activeTreeView) {
            case MOUNTS -> buildMountsTree();
            case FRAMES -> buildFramesTree();
            case VERSIONS -> buildVersionsTree();
        }
    }

    private void buildMountsTree() {
        TreeLink tl = TreeLink.of(root, TreeLink.ChildMode.PRESENTATION, resolver);
        treeContentNode = TreeNodes.from(tl).children(TreeLink::children).label(TreeLink::displayToken)
                .icon(TreeLink::emoji).expandable(TreeLink::isExpandable).id(TreeLink::treeId)
                .showRoot(false).build();
        treeNav = TreeNav.from(tl, TreeLink::children, TreeLink::treeId, TreeLink::isExpandable, false);
    }

    private void buildFramesTree() {
        Item resolved = item();
        if (resolved == null) { treeNav = null; treeContentNode = null; return; }
        List<FrameNode> frameNodes = new ArrayList<>();
        if (resolved.frames() != null) {
            for (Frame f : resolved.frames()) frameNodes.add(new FrameNode(f, resolved));
        }
        FrameNode root = new FrameNode("Frames (" + frameNodes.size() + ")",
                "\uD83D\uDCC2", "group:frames", frameNodes);
        treeContentNode = TreeNodes.from(root).children(FrameNode::children).label(FrameNode::label)
                .icon(FrameNode::emoji).expandable(n -> !n.children().isEmpty()).id(FrameNode::id)
                .showRoot(false).build();
        treeNav = TreeNav.from(root, FrameNode::children, FrameNode::id, n -> !n.children().isEmpty(), false);
    }

    private void buildVersionsTree() {
        Item resolved = item();
        String vid = resolved != null && resolved.base() != null
                ? resolved.base().displayAtWidth(16) : "?";
        FrameNode r = new FrameNode("Versions", "\uD83D\uDCC2", "group:versions",
                List.of(new FrameNode(vid, "\uD83D\uDCCB", "version:" + vid, List.of())));
        treeContentNode = TreeNodes.from(r).children(FrameNode::children).label(FrameNode::label)
                .icon(FrameNode::emoji).expandable(n -> !n.children().isEmpty()).id(FrameNode::id)
                .showRoot(false).build();
        treeNav = TreeNav.from(r, FrameNode::children, FrameNode::id, n -> !n.children().isEmpty(), false);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    static ItemID typeIdOf(Item item) {
        try {
            return Item.idOf(item.getClass());
        } catch (IllegalArgumentException e) {
            return item.iid();
        }
    }

    private static String insertCursor(String text, int pos) {
        if (text == null || text.isEmpty()) return "\u258F";
        int c = Math.max(0, Math.min(pos, text.length()));
        return c >= text.length() ? text + "\u258F" : text.substring(0, c) + "\u258F" + text.substring(c);
    }

    // ==================================================================================
    // Key / Event Handling
    // ==================================================================================

    public boolean handleKey(KeyChord chord) {
        if (chord.isKey(SpecialKey.F1)) return handleEvent("toggle:help", null);
        if (chord.isKey(SpecialKey.F2)) return handleEvent("toggle:mounts", null);
        if (chord.isKey(SpecialKey.F3)) return handleEvent("toggle:frames", null);
        if (chord.isKey(SpecialKey.F4)) return handleEvent("toggle:versions", null);
        if (treeNav != null && chord.alt() && !chord.ctrl() && !chord.shift()) {
            if (chord.isKey(SpecialKey.UP)) { treeNav.selectPrevious(); changed(); return true; }
            if (chord.isKey(SpecialKey.DOWN)) { treeNav.selectNext(); changed(); return true; }
            if (chord.isKey(SpecialKey.LEFT)) {
                if (treeNav.isExpanded(treeNav.selectedId())) { treeNav.collapse(treeNav.selectedId()); syncTreeNav(); }
                else treeNav.selectParent();
                changed(); return true;
            }
            if (chord.isKey(SpecialKey.RIGHT)) {
                if (treeNav.isExpanded(treeNav.selectedId())) treeNav.selectFirstChild();
                else { treeNav.expand(treeNav.selectedId()); syncTreeNav(); }
                changed(); return true;
            }
        }
        return false;
    }

    public boolean handleEvent(String action, String target) {
        if (action == null) return false;
        if (action.startsWith("toggle:")) {
            return switch (action.substring("toggle:".length())) {
                case "help" -> { toggleHelp(); yield true; }
                case "mounts" -> { toggleTreeView(TreeView.MOUNTS); yield true; }
                case "frames" -> { toggleTreeView(TreeView.FRAMES); yield true; }
                case "versions" -> { toggleTreeView(TreeView.VERSIONS); yield true; }
                default -> false;
            };
        }
        if ("select".equals(action) && target != null && !target.isEmpty()) {
            if (stateStore != null && selectedTreeNodeId != null) {
                var prev = stateStore.get(selectedTreeNodeId);
                if (prev != null) prev.put("selected", false);
            }
            selectedTreeNodeId = target;
            if (stateStore != null) {
                stateStore.computeIfAbsent(target, k -> new java.util.HashMap<>())
                        .put("selected", true);
            }
            try {
                ItemID iid = ItemID.fromString(target);
                Optional<Item> item = resolver.apply(iid);
                if (item.isPresent()) {
                    select(Ref.of(iid));
                    return true;
                }
            } catch (Exception ignored) {}
            if (treeNav != null) treeNav.select(target);
            changed();
            return true;
        }
        return false;
    }

    private void syncTreeNav() {
        if (treeNav != null && stateStore != null) treeNav.syncToStateStore(stateStore);
    }

    // ==================================================================================
    // FrameNode (data record for frame tree)
    // ==================================================================================

    @Log4j2
    static class FrameNode {
        private final String label, emoji, id;
        private final Frame frame;
        private final List<FrameNode> children;

        FrameNode(String label, String emoji, String id, List<FrameNode> children) {
            this.label = label; this.emoji = emoji; this.id = id;
            this.frame = null; this.children = children;
        }

        FrameNode(Frame frame, Item item) {
            this.frame = frame;
            this.emoji = "\uD83D\uDCCB";
            this.id = "frame:" + (frame.bodyHash() != null
                    ? frame.bodyHash().displayAtWidth(12)
                    : frame.frameKey().toString());
            this.label = buildSummaryLabel(frame, item);
            this.children = List.of();
        }

        String label() { return label; }
        String emoji() { return emoji; }
        String id() { return id; }
        List<FrameNode> children() { return children; }
        Frame frame() { return frame; }

        private static final int MAX_LABEL_LENGTH = 50;

        private static String buildSummaryLabel(Frame frame, Item item) {
            FrameBody body = frame.body();
            if (body == null) {
                return truncate(resolveFrameKeyLabel(frame, item));
            }

            Librarian lib = item.itemLibrarian();
            if (lib != null) {
                return truncate(HandleResolver.labelForFrame(body, item.iid(), lib));
            }

            // No librarian — fall back to predicate name only
            String pred = resolvePredicate(frame, item);
            return truncate(pred != null ? pred : "frame");
        }

        private static String truncate(String s) {
            if (s == null) return "?";
            return s.length() > MAX_LABEL_LENGTH
                    ? s.substring(0, MAX_LABEL_LENGTH - 1) + "\u2026" : s;
        }

        /**
         * Resolve a FrameKey into a human-readable label via the librarian.
         * Resolves sememe tokens to display names, keeps literal tokens as-is.
         * Never returns raw hashes.
         */
        private static String resolveFrameKeyLabel(Frame frame, Item item) {
            // Try the frame type first (same IID as the head sememe, but may be set even without body)
            if (frame.type() != null) {
                String r = item.resolveDisplayToken(frame.type());
                if (r != null) {
                    // Append any literal qualifiers from the key for context
                    String qualifier = literalQualifiers(frame.frameKey());
                    return qualifier != null ? r + " \u2014 " + qualifier : r;
                }
            }

            // Try resolving FrameKey tokens directly
            FrameKey key = frame.frameKey();
            if (key != null && !key.tokens().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (FrameKey.FrameToken token : key.tokens()) {
                    if (token instanceof FrameKey.Sememe s) {
                        String r = item.resolveDisplayToken(s.id());
                        if (r != null) {
                            if (!sb.isEmpty()) sb.append(" \u2014 ");
                            sb.append(r);
                        }
                        // Skip unresolvable sememe tokens — don't show hashes
                    } else if (token instanceof FrameKey.Literal l) {
                        if (!sb.isEmpty()) sb.append(" \u2014 ");
                        sb.append(l.value());
                    }
                }
                if (!sb.isEmpty()) return sb.toString();
            }

            return "frame";
        }

        /** Extract literal tokens from a FrameKey as qualifier context, skipping hash-like values. */
        private static String literalQualifiers(FrameKey key) {
            if (key == null) return null;
            StringBuilder sb = new StringBuilder();
            for (FrameKey.FrameToken token : key.tokens()) {
                if (token instanceof FrameKey.Literal l) {
                    String v = l.value();
                    // Skip literals that are or contain raw hashes
                    if (v.startsWith("iid:") || v.startsWith("cid:") || v.startsWith("vid:")) continue;
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(v);
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }

        static String resolvePredicate(Frame frame, Item item) {
            if (frame.body() != null && frame.body().predicate() != null) {
                String r = item.resolveDisplayToken(frame.body().predicate());
                if (r != null) return r;
            }
            FrameKey key = frame.frameKey();
            if (key != null && !key.tokens().isEmpty()) {
                FrameKey.FrameToken head = key.tokens().get(0);
                if (head instanceof FrameKey.Sememe s) {
                    String r = item.resolveDisplayToken(s.id());
                    if (r != null) return r;
                } else if (head instanceof FrameKey.Literal l) {
                    return l.value();
                }
            }
            // Try frame type as last resort before giving up
            if (frame.type() != null) {
                String r = item.resolveDisplayToken(frame.type());
                if (r != null) return r;
            }
            logger.debug("FrameNode: unresolved predicate on frame key={}", frame.frameKey());
            return "frame";
        }

        static String fmtTarget(Binding b, Item item) {
            ItemID tid = b.targetId();
            if (tid != null) {
                String r = item.resolveDisplayToken(tid);
                if (r != null) return r;
                logger.debug("FrameNode: unresolved binding target {}", tid::encodeText);
                return null;
            }
            if (b.target() instanceof dev.everydaythings.graph.item.Literal lit) {
                if (dev.everydaythings.graph.item.Literal.TYPE_TEXT.equals(lit.valueType())) {
                    try {
                        String t = lit.asText();
                        if (t != null) return t.length() > 30
                                ? "\"" + t.substring(0, 27) + "...\""
                                : "\"" + t + "\"";
                    } catch (Exception ignored) {}
                }
                // Non-text literal with unresolvable type — skip
                return null;
            }
            return null;
        }
    }
}
