package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.dispatch.ParamSpec;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.ViewHandle;
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
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.Text;
import dev.everydaythings.graph.ui.scene.node.TreeNav;
import dev.everydaythings.graph.ui.scene.node.TreeNodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The universal item chrome — header, tree, detail, prompt.
 *
 * <p>Declarative layout via {@code @Scene} annotations on methods.
 * Dynamic content provided by method return values. The scene compiler
 * calls each annotated method and wraps the result per its annotation.
 *
 * <p>{@code @Scene.On} declares event bindings — F1 toggles help,
 * F2-F4 toggle tree views. The renderer dispatches these automatically.
 */
@Scene.Root
@Scene.Container(direction = Scene.Direction.VERTICAL, id = "item-view", height = "100%")
public class ItemView {

    // ==================================================================================
    // Handle — compact identity (tree nodes, header bar, breadcrumbs)
    // ==================================================================================

    @Scene.Handle
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.5em")
    public Node handle() {
        Item ctx = item();
        if (ctx == null) return Text.of("");
        Container h = Container.horizontal().gap("0.5em");
        h.add(glyph(ctx.emoji() != null ? ctx.emoji() : ""));
        h.add(Text.ofSememe(ctx.iid()));
        return h;
    }

    // ==================================================================================
    // Layout — annotated methods, compiled by SceneCompiler
    // ==================================================================================

    @Scene.Container(order = 0, direction = Scene.Direction.HORIZONTAL, id = "header",
            gap = "0.5em", background = "#1E1E2E", padding = "0.3em")
    public Node header() {
        Item ctx = item();
        Container h = Container.horizontal().gap("0.5em");

        // Icon + name + type
        Container identity = Container.horizontal().gap("0.5em");
        if (ctx != null) {
            Node icon = glyph(ctx.emoji() != null ? ctx.emoji() : "");
            icon.background("#3C3C4E");
            icon.corner("50%");
            identity.add(icon);
            identity.add(Text.ofSememe(ctx.iid()).fontWeight("bold"));
        }
        h.add(identity);
        h.add(Text.of("").classes("spacer"));
        h.add(modeBar());
        return h;
    }

    @Scene.Container(order = 1, direction = Scene.Direction.HORIZONTAL, id = "body", height = "1fr")
    public Node body() {
        Container b = Container.horizontal().height("1fr");
        if (treeVisible()) b.add(treePanel());
        b.add(detailPanel());
        return b;
    }

    @Scene.Container(order = 2, direction = Scene.Direction.VERTICAL, id = "prompt")
    public Node prompt() {
        Node input;
        if (inputSnapshot != null && renderInputInSurface) {
            input = inputFromSnapshot(inputSnapshot);
        } else if (inputSnapshot != null) {
            input = null;
        } else {
            Item ctx = item();
            String p = ctx != null
                    ? (ctx.emoji() != null ? ctx.emoji() + " " : "") + ctx.displayToken() + "> "
                    : "> ";
            Container empty = Container.vertical().classes("input-surface");
            Container row = Container.horizontal().gap("0.25em");
            row.classes("input-row");
            row.add(Text.of(p).classes("prompt"));
            empty.add(row);
            input = empty;
        }
        if (feedbackText != null && !feedbackText.isBlank()) {
            Container pr = Container.vertical();
            pr.add(Text.of((feedbackIsError ? "! " : "  \u2192 ") + feedbackText)
                    .classes(feedbackIsError ? "feedback-error" : "feedback"));
            if (input != null) pr.add(input);
            return pr;
        }
        return input;
    }

    // ==================================================================================
    // Events — @Scene.On declarations (renderer dispatches automatically)
    // ==================================================================================

    @Scene.On(event = "F1", action = "toggle:help")
    @Scene.On(event = "F2", action = "toggle:mounts")
    @Scene.On(event = "F3", action = "toggle:frames")
    @Scene.On(event = "F4", action = "toggle:versions")
    private void keyBindings() {} // marker method for key declarations

    // ==================================================================================
    // Change Notification
    // ==================================================================================

    private Runnable onChanged;
    public void onChange(Runnable listener) { this.onChanged = listener; }
    protected void changed() { if (onChanged != null) onChanged.run(); }

    // ==================================================================================
    // State
    // ==================================================================================

    private Ref root;
    private Ref context;
    private final List<Ref> history = new ArrayList<>();
    private final Function<ItemID, Optional<Item>> resolver;

    private TreeView activeTreeView = null;
    private boolean helpVisible = false;
    private TreeNav<?> treeNav;
    private Node treeContent;

    private Map<String, Map<String, Object>> stateStore;
    private String selectedTreeNodeId;
    private ViewHandle activeView;
    private InputSnapshot inputSnapshot;
    private boolean renderInputInSurface = true;
    private String feedbackText;
    private boolean feedbackIsError;

    public enum TreeView { MOUNTS, FRAMES, VERSIONS }

    // ==================================================================================
    // Constructor
    // ==================================================================================

    public ItemView(Item item, Function<ItemID, Optional<Item>> resolver) {
        this.root = Ref.of(item.iid());
        this.context = this.root;
        this.resolver = resolver;
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
        rebuildTree(); changed();
    }

    public void navigateInto(Item item) {
        if (item == null) return;
        navigateInto(Ref.of(item.iid()));
    }

    public boolean goBack() {
        if (history.isEmpty()) return false;
        root = history.removeLast(); context = root;
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
    public boolean helpVisible() { return helpVisible; }
    public boolean treeVisible() { return activeTreeView != null; }
    public TreeView activeTreeView() { return activeTreeView; }

    // ==================================================================================
    // Detail Panel
    // ==================================================================================

    private Node detailPanel() {
        Container detail = Container.vertical().width("1fr").overflow("auto").padding("0.5em");
        detail.id("detail");
        if (helpVisible) {
            detail.add(helpContent());
        } else if (selectedTreeNodeId != null && activeTreeView != null) {
            detail.add(selectedNodeContent());
        } else {
            detail.add(itemContent());
        }
        return detail;
    }

    /** Content for the selected tree node — works for any tree type. */
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

        // Fallback — show root item
        return itemContent();
    }

    /** Build a detail view for a single frame. */
    private Node buildFrameDetail(Frame frame, Item resolved) {
        Container detail = Container.vertical().gap("0.5em");

        // Predicate
        FrameBody body = frame.body();
        if (body != null && body.predicate() != null) {
            String pred = resolved.resolveDisplayToken(body.predicate());
            if (pred == null) pred = body.predicate().displayAtWidth(20);
            detail.add(Text.of(pred).fontWeight("bold").classes("heading"));
        }

        // Bindings
        if (body != null) {
            for (Binding b : body.frameBindings()) {
                Container row = Container.horizontal().gap("0.5em");
                String role = resolved.resolveDisplayToken(b.role());
                if (role == null) role = b.role().displayAtWidth(12);
                row.add(Text.of(role).fontWeight("bold"));
                row.add(Text.of("\u2192").classes("muted"));
                row.add(Text.of(FrameNode.fmtTarget(b, resolved)).classes("muted"));
                detail.add(row);
            }
        }

        // Body hash
        if (frame.bodyHash() != null) {
            detail.add(Text.of("Hash: " + frame.bodyHash().displayAtWidth(20)).classes("mono", "muted"));
        }

        return detail;
    }

    /** Item content for a specific item (overload). */
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
    // Help Content
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
        boolean hasContent = false;
        if (vocab != null) {
            List<VerbEntry> verbs = new ArrayList<>();
            for (VerbEntry v : vocab) verbs.add(v);
            if (!verbs.isEmpty()) {
                hasContent = true;
                Container vl = Container.vertical().gap("0.125em");
                for (VerbEntry v : verbs) vl.add(verbRow(v));
                s.add(vl);
            }
            List<Posting> tokens = vocab.prefixMatch("");
            if (!tokens.isEmpty()) {
                hasContent = true;
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
    // Tree Panel
    // ==================================================================================

    private Node treePanel() {
        Container panel = Container.vertical().gap("0.25em");
        panel.id("tree");
        panel.border("0.1ln solid #313244");
        panel.padding("0.25em");
        panel.overflow("auto");
        if (treeContent != null) panel.add(treeContent);
        return panel;
    }

    // ==================================================================================
    // Mode Bar
    // ==================================================================================

    private Node modeBar() {
        Container bar = Container.horizontal().gap("0.2em");
        bar.add(modeBtn("\uD83D\uDCD6", "toggle:help", helpVisible));
        bar.add(modeBtn("\uD83D\uDDC2", "toggle:mounts", activeTreeView == TreeView.MOUNTS));
        bar.add(modeBtn("\uD83D\uDCCB", "toggle:frames", activeTreeView == TreeView.FRAMES));
        bar.add(modeBtn("\uD83D\uDCDC", "toggle:versions", activeTreeView == TreeView.VERSIONS));
        return bar;
    }

    private Node modeBtn(String g, String action, boolean active) {
        Container btn = Container.horizontal();
        btn.classes(active ? "mode-button-active" : "mode-button");
        btn.on("click", action);
        btn.add(glyph(g));
        return btn;
    }

    // ==================================================================================
    // Input
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
            outer.add(completions(snap.completionEntries(), snap.selectedCompletion()));
        return outer;
    }

    private Node tokenChip(ExpressionToken token) {
        boolean resolved = token instanceof ExpressionToken.RefToken;
        String emoji = null;
        if (token instanceof ExpressionToken.RefToken ref && resolver != null) {
            try { Optional<Item> it = resolver.apply(ref.target()); if (it.isPresent()) emoji = it.get().emoji(); }
            catch (Exception ignored) {}
        }
        if (resolved) {
            Container c = Container.horizontal().classes("token-chip", "resolved")
                    .border("0.1em solid #4A5568").corner("0.6em")
                    .background("#2D3748").padding("0.1em 0.4em");
            if (emoji != null && !emoji.isEmpty()) c.add(Text.of(emoji));
            c.add(Text.of(token.displayText()));
            return c;
        }
        Container c = Container.horizontal().classes("token-chip");
        c.add(Text.of(token.displayText()));
        return c;
    }

    private Node completions(List<CompletionEntry> entries, int selected) {
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
        if (activeTreeView == null) { treeNav = null; treeContent = null; return; }
        switch (activeTreeView) {
            case MOUNTS -> buildMountsTree();
            case FRAMES -> buildFramesTree();
            case VERSIONS -> buildVersionsTree();
        }
    }

    private void buildMountsTree() {
        TreeLink tl = TreeLink.of(root, TreeLink.ChildMode.PRESENTATION, resolver);
        treeContent = TreeNodes.from(tl).children(TreeLink::children).label(TreeLink::displayToken)
                .icon(TreeLink::emoji).expandable(TreeLink::isExpandable).id(TreeLink::treeId)
                .showRoot(false).build();
        treeNav = TreeNav.from(tl, TreeLink::children, TreeLink::treeId, TreeLink::isExpandable, false);
    }

    private void buildFramesTree() {
        Item resolved = item();
        if (resolved == null) { treeNav = null; treeContent = null; return; }
        List<FrameNode> nodes = new ArrayList<>();
        if (resolved.frames() != null) for (Frame f : resolved.frames()) nodes.add(new FrameNode(f, resolved));
        FrameNode r = new FrameNode("Frames (" + nodes.size() + ")", nodes);
        treeContent = TreeNodes.from(r).children(FrameNode::children).label(FrameNode::label)
                .icon(FrameNode::emoji).expandable(n -> !n.children().isEmpty()).id(FrameNode::id)
                .showRoot(false).build();
        treeNav = TreeNav.from(r, FrameNode::children, FrameNode::id, n -> !n.children().isEmpty(), false);
    }

    private void buildVersionsTree() {
        Item resolved = item();
        String vid = resolved != null && resolved.base() != null
                ? resolved.base().displayAtWidth(16) : "?";
        FrameNode r = new FrameNode("Versions", List.of(new FrameNode(vid, List.of())));
        treeContent = TreeNodes.from(r).children(FrameNode::children).label(FrameNode::label)
                .icon(FrameNode::emoji).expandable(n -> !n.children().isEmpty()).id(FrameNode::id)
                .showRoot(false).build();
        treeNav = TreeNav.from(r, FrameNode::children, FrameNode::id, n -> !n.children().isEmpty(), false);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Node glyph(String g) {
        return dev.everydaythings.graph.ui.scene.node.Body.ofGlyph(g != null ? g : "");
    }

    private static String insertCursor(String text, int pos) {
        if (text == null || text.isEmpty()) return "\u258F";
        int c = Math.max(0, Math.min(pos, text.length()));
        return c >= text.length() ? text + "\u258F" : text.substring(0, c) + "\u258F" + text.substring(c);
    }

    // ==================================================================================
    // Key / Event Handling (legacy — being replaced by @Scene.On)
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
            // Unhighlight previous selection in state store
            if (stateStore != null && selectedTreeNodeId != null) {
                var prev = stateStore.get(selectedTreeNodeId);
                if (prev != null) prev.put("selected", false);
            }
            // Highlight new selection
            selectedTreeNodeId = target;
            if (stateStore != null) {
                stateStore.computeIfAbsent(target, k -> new java.util.HashMap<>())
                        .put("selected", true);
            }
            // Try to resolve as an ItemID and update context
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

    private static class FrameNode {
        private final String label, emoji, id;
        private final List<FrameNode> children;

        FrameNode(String label, List<FrameNode> children) {
            this.label = label; this.emoji = "\uD83D\uDCC2"; this.id = "group:" + label;
            this.children = children;
        }

        FrameNode(Frame frame, Item item) {
            this.label = resolveFrameLabel(frame, item); this.emoji = "\uD83D\uDCCB";
            this.id = "frame:" + (frame.bodyHash() != null ? frame.bodyHash().displayAtWidth(12) : frame.frameKey().toString());
            this.children = new ArrayList<>();
            FrameBody body = frame.body();
            if (body != null) for (Binding b : body.frameBindings()) {
                String role = item.resolveDisplayToken(b.role());
                if (role == null) role = b.role().displayAtWidth(12);
                children.add(new FrameNode(role + ":" + fmtQuals(b, item) + " \u2192 " + fmtTarget(b, item), List.of()));
            }
        }

        String label() { return label; } String emoji() { return emoji; }
        String id() { return id; } List<FrameNode> children() { return children; }

        private static String fmtQuals(Binding b, Item item) {
            if (b.qualifiers().isEmpty()) return "";
            StringBuilder q = new StringBuilder("[");
            for (int i = 0; i < b.qualifiers().size(); i++) {
                if (i > 0) q.append(", ");
                var qv = b.qualifiers().get(i);
                if (qv instanceof FrameKey.Sememe s) { String r = item.resolveDisplayToken(s.id()); q.append(r != null ? r : s.id().displayAtWidth(8)); }
                else if (qv instanceof FrameKey.Literal l) q.append(l.value());
            }
            return q.append("] ").toString();
        }

        private static String resolveFrameLabel(Frame frame, Item item) {
            FrameBody body = frame.body();
            if (body != null && body.predicate() != null) {
                String name = item.resolveDisplayToken(body.predicate());
                if (name == null) name = body.predicate().displayAtWidth(12);
                FrameKey key = frame.frameKey();
                if (key.qualifiers() != null && !key.qualifiers().isEmpty()) {
                    StringBuilder q = new StringBuilder(" [");
                    for (int i = 0; i < Math.min(key.qualifiers().size(), 3); i++) {
                        if (i > 0) q.append(", ");
                        var qv = key.qualifiers().get(i);
                        if (qv instanceof FrameKey.Sememe s) { String r = item.resolveDisplayToken(s.id()); q.append(r != null ? r : s.id().displayAtWidth(8)); }
                        else if (qv instanceof FrameKey.Literal l) q.append(l.value());
                    }
                    return name + q.append("]");
                }
                return name;
            }
            FrameKey.FrameToken head = frame.frameKey().head();
            if (head instanceof FrameKey.Literal l) return l.value();
            if (head instanceof FrameKey.Sememe s) { String r = item.resolveDisplayToken(s.id()); return r != null ? r : s.id().displayAtWidth(12); }
            return "?";
        }

        static String fmtTarget(Binding b, Item item) {
            ItemID tid = b.targetId();
            if (tid != null) { String r = item.resolveDisplayToken(tid); return r != null ? r : tid.displayAtWidth(12); }
            if (b.target() instanceof dev.everydaythings.graph.item.Literal lit) {
                if (dev.everydaythings.graph.item.Literal.TYPE_TEXT.equals(lit.valueType())) {
                    try { String t = lit.asText(); if (t != null) return t.length() > 30 ? "\"" + t.substring(0, 27) + "...\"" : "\"" + t + "\""; } catch (Exception ignored) {}
                }
                return lit.valueType().displayAtWidth(12);
            }
            return "\u2014";
        }
    }
}
