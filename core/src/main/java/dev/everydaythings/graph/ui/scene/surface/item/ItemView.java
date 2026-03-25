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
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneModel;
import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Embedded;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.NodeRenderer;
import dev.everydaythings.graph.ui.scene.node.Text;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.scene.surface.tree.TreeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The chrome wrapper for viewing an Item — built from Node primitives.
 *
 * <p>ItemView owns the universal UI frame: handle (title bar), tree pane,
 * detail panel, and prompt. The CONTENT of the detail panel comes from
 * the item's own {@code @Scene} declaration — item authors control that.
 * ItemView just wraps it.
 *
 * <p>Layout (built from Container/Text/Body nodes):
 * <pre>
 * Container(vertical, id="item-view")
 * ├── Container(horizontal, id="header")    — icon + name + mode buttons
 * ├── Container(horizontal, id="body")      — tree (optional) + detail
 * │   ├── Container(vertical, id="tree")    — tree panel (F2/F3/F4)
 * │   └── Container(vertical, id="detail")  — item content or help
 * └── Container(vertical, id="prompt")      — input + feedback
 * </pre>
 *
 * <p>F-key toggles:
 * <ul>
 *   <li>F1 — help/vocabulary overlay in detail panel</li>
 *   <li>F2 — toggle mounts tree</li>
 *   <li>F3 — toggle frames tree</li>
 *   <li>F4 — toggle versions tree</li>
 * </ul>
 */
public class ItemView extends SceneModel<SurfaceSchema> {

    // ==================================================================================
    // State
    // ==================================================================================

    private Ref root;
    private Ref context;
    private final List<Ref> history = new ArrayList<>();
    private final Function<ItemID, Optional<Item>> resolver;

    private TreeView activeTreeView = null;
    private boolean helpVisible = false;
    private TreeModel<?> treeModel;

    private ViewHandle activeView;
    private InputSnapshot inputSnapshot;
    private boolean renderInputInSurface = true;
    private String feedbackText;
    private boolean feedbackIsError;

    public enum TreeView {
        MOUNTS, FRAMES, VERSIONS
    }

    // ==================================================================================
    // Constructor
    // ==================================================================================

    public ItemView(Item item, Function<ItemID, Optional<Item>> resolver) {
        this.root = Ref.of(item.iid());
        this.context = this.root;
        this.resolver = resolver;
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
        root = target;
        context = target;
        rebuildTree();
        changed();
    }

    public void navigateInto(Item item) {
        if (item == null) return;
        navigateInto(Ref.of(item.iid()));
    }

    public boolean goBack() {
        if (history.isEmpty()) return false;
        root = history.removeLast();
        context = root;
        rebuildTree();
        changed();
        return true;
    }

    public boolean canGoBack() {
        return !history.isEmpty();
    }

    public void select(Ref target) {
        if (target == null) return;
        this.context = target;
        if (treeModel != null && target.target() != null) {
            String treeId = target.target().encodeText();
            treeModel.select(treeId);
        }
        changed();
    }

    public void select(Item item) {
        if (item == null) return;
        select(Ref.of(item.iid()));
    }

    public void refresh() {
        rebuildTree();
        changed();
    }

    // ==================================================================================
    // Input State
    // ==================================================================================

    public void updateInput(InputSnapshot snapshot) {
        this.inputSnapshot = snapshot;
        changed();
    }

    public void setFeedback(String text, boolean isError) {
        this.feedbackText = text;
        this.feedbackIsError = isError;
        changed();
    }

    public void clearFeedback() {
        this.feedbackText = null;
        this.feedbackIsError = false;
    }

    public void setRenderInputInSurface(boolean render) {
        this.renderInputInSurface = render;
    }

    public InputSnapshot inputSnapshot() { return inputSnapshot; }

    // ==================================================================================
    // View Handle
    // ==================================================================================

    public void setActiveView(ViewHandle view) {
        this.activeView = view;
        changed();
    }

    public void clearActiveView() {
        this.activeView = null;
        changed();
    }

    public boolean hasActiveView() {
        return activeView != null;
    }

    public ViewHandle activeView() { return activeView; }

    // ==================================================================================
    // Node Tree Construction
    // ==================================================================================

    /**
     * Build the complete Node tree for this view.
     *
     * <p>This is the primary rendering path. ViewWindow calls this directly
     * and feeds the result to {@link NodeRenderer}.
     */
    public Node toNode() {
        Container root = Container.vertical();
        root.id("item-view");
        root.height("100%");

        root.add(buildHeader());

        // Body: tree (optional) + detail
        Container body = Container.horizontal();
        body.id("body");
        body.height("1fr");

        Node treePanel = buildTreePanel();
        if (treePanel != null) body.add(treePanel);
        body.add(buildDetailPanel());
        root.add(body);

        Node promptNode = buildPrompt();
        if (promptNode != null) root.add(promptNode);

        return root;
    }

    /**
     * Legacy bridge — wraps the Node tree in a SurfaceSchema for callers
     * that still expect one.
     */
    @Override
    public SurfaceSchema toSurface() {
        return new SurfaceSchema<Void>() {
            @Override
            public void render(SurfaceRenderer out) {
                NodeRenderer.render(toNode(), out);
            }
        };
    }

    // ==================================================================================
    // Header
    // ==================================================================================

    private Node buildHeader() {
        Container header = Container.horizontal();
        header.id("header");
        header.gap("0.5em");
        header.classes("panel", "header", "view-header");

        Item ctx = item();
        String emoji = ctx != null ? ctx.emoji() : "\uD83D\uDCE6";
        String label = ctx != null ? ctx.displayToken() : "\u2014";
        String typeName = ctx != null ? ctx.getClass().getSimpleName() : "Item";

        // Handle: icon + label + subtitle
        Container handle = Container.horizontal();
        handle.gap("0.5em");
        handle.classes("header-handle");
        handle.add(Body.ofGlyph(emoji)
                .classes("icon")
                .background("#3C3C4E")
                .corner("50%"));
        handle.add(Text.of(label).fontWeight("bold").classes("handle-label"));
        handle.add(Text.of(typeName).classes("handle-subtitle", "muted"));
        header.add(handle);

        // Spacer
        header.add(Text.of("").classes("spacer"));

        // Mode bar
        header.add(buildModeBar());

        return header;
    }

    private Container buildModeBar() {
        Container bar = Container.horizontal();
        bar.gap("0.2em");
        bar.classes("mode-bar");

        bar.add(modeButton("\uD83D\uDCD6", "toggle:help", helpVisible, "F1 Help"));
        bar.add(modeButton("\uD83D\uDDC2", "toggle:mounts",
                activeTreeView == TreeView.MOUNTS, "F2 Mounts"));
        bar.add(modeButton("\uD83D\uDCCB", "toggle:frames",
                activeTreeView == TreeView.FRAMES, "F3 Frames"));
        bar.add(modeButton("\uD83D\uDCDC", "toggle:versions",
                activeTreeView == TreeView.VERSIONS, "F4 Versions"));

        return bar;
    }

    private Node modeButton(String glyph, String action, boolean active, String hint) {
        Container btn = Container.horizontal();
        btn.classes(active ? "mode-button-active" : "mode-button");
        btn.on("click", action);
        btn.on("mouseenter", "treeHint", hint);
        btn.on("mouseleave", "treeHint", "");
        btn.add(Body.ofGlyph(glyph));
        return btn;
    }

    // ==================================================================================
    // Tree Panel
    // ==================================================================================

    private Node buildTreePanel() {
        if (activeTreeView == null) return null;

        Container panel = Container.vertical();
        panel.id("tree");
        panel.gap("0.25em");
        panel.classes("panel", "tree-panel", "tree-pane");
        panel.overflow("auto");

        if (treeModel != null) {
            // TreeModel still produces SurfaceSchema — embed it
            panel.add(Embedded.of(treeModel.toSurface()));
        }

        return panel;
    }

    // ==================================================================================
    // Detail Panel
    // ==================================================================================

    private Node buildDetailPanel() {
        Container detail = Container.vertical();
        detail.id("detail");
        detail.classes("panel");
        detail.width("1fr");
        detail.overflow("auto");

        if (helpVisible) {
            detail.add(buildHelpContent());
        } else {
            detail.add(resolveItemContent());
        }

        return detail;
    }

    /**
     * Resolve the item's own content.
     *
     * <p>Tries the item's @Scene annotations first (produces SurfaceSchema,
     * wrapped in Embedded), then falls back to a Node-native summary.
     */
    private Node resolveItemContent() {
        Item resolved = item();
        if (resolved == null) {
            return Text.of("No item").classes("muted");
        }

        // Try the item's own @Scene annotations
        Class<?> clazz = resolved.getClass();
        if (clazz != Item.class && SceneCompiler.has2DAnnotation(clazz)) {
            try {
                SurfaceSchema surface = SceneCompiler.compile(resolved).root();
                if (surface != null) return Embedded.of(surface);
            } catch (Exception ignored) {}
        }

        // Fallback: basic summary built from nodes
        Container summary = Container.vertical();
        summary.gap("0.5em");
        summary.classes("item-summary");
        summary.add(Text.of(resolved.displayToken()).classes("heading"));
        summary.add(Text.of(resolved.getClass().getSimpleName()).classes("muted"));
        if (resolved.iid() != null) {
            summary.add(Text.of("IID: " + resolved.iid().displayAtWidth(20))
                    .classes("mono", "muted"));
        }

        int frameCount = 0;
        if (resolved.frames() != null) {
            for (var f : resolved.frames()) frameCount++;
        }
        summary.add(Text.of(frameCount + " frames").classes("muted"));
        summary.add(Text.of(resolved.vocabulary().size() + " verbs").classes("muted"));

        return summary;
    }

    // ==================================================================================
    // Help Content
    // ==================================================================================

    /**
     * Build the help overlay showing the vocabulary scope stack.
     *
     * <p>Shows item scope → system scope, each listing verbs (with params)
     * and local tokens.
     */
    private Node buildHelpContent() {
        Item helpItem = item();
        Librarian librarian = helpItem != null ? helpItem.itemLibrarian() : null;

        Container help = Container.vertical();
        help.gap("0.5em");
        help.classes("help-surface");
        help.add(Text.of("Help (F1 to dismiss)").classes("heading", "small"));

        if (helpItem != null) {
            help.add(buildScopeSection(helpItem.displayToken(), helpItem.vocabulary()));
        }
        if (librarian != null) {
            help.add(buildScopeSection("System", librarian.vocabulary()));
        }

        return help;
    }

    private Node buildScopeSection(String name, Vocabulary vocab) {
        Container section = Container.vertical();
        section.gap("0.25em");
        section.add(Text.of(name).classes("scope-title"));

        boolean hasContent = false;

        if (vocab != null) {
            // Verbs
            List<VerbEntry> verbs = new ArrayList<>();
            for (VerbEntry v : vocab) verbs.add(v);

            if (!verbs.isEmpty()) {
                hasContent = true;
                Container verbList = Container.vertical();
                verbList.gap("0.125em");
                for (VerbEntry verb : verbs) {
                    verbList.add(buildVerbRow(verb));
                }
                section.add(verbList);
            }

            // Local tokens
            List<Posting> tokens = vocab.prefixMatch("");
            if (!tokens.isEmpty()) {
                hasContent = true;
                Container tokenList = Container.vertical();
                tokenList.gap("0.0625em");
                tokenList.add(Text.of("Local tokens:").classes("scope-title", "small"));
                for (Posting posting : tokens) {
                    Container row = Container.horizontal();
                    row.gap("0.5em");
                    row.add(Text.of(posting.token()).classes("token-name"));
                    row.add(Text.of("\u2192").classes("muted"));
                    String target = posting.target() != null
                            ? posting.target().displayAtWidth(16) : "\u2014";
                    row.add(Text.of(target).classes("token-target"));
                    tokenList.add(row);
                }
                section.add(tokenList);
            }
        }

        if (!hasContent) {
            section.add(Text.of("  (none)").classes("empty"));
        }

        return section;
    }

    private Node buildVerbRow(VerbEntry verb) {
        Container row = Container.vertical();

        // Name + doc line
        Container nameRow = Container.horizontal();
        nameRow.gap("0.5em");
        String methodName = verb.methodName();
        String displayName = methodName.startsWith("action")
                ? methodName.substring(6, 7).toLowerCase() + methodName.substring(7)
                : methodName;
        nameRow.add(Text.of(displayName).classes("verb-name"));
        if (verb.doc() != null) {
            nameRow.add(Text.of(verb.doc()).classes("verb-doc"));
        }
        row.add(nameRow);

        // Parameters
        if (verb.params() != null && !verb.params().isEmpty()) {
            Container params = Container.vertical();
            params.classes("params");
            for (ParamSpec param : verb.params()) {
                Container paramRow = Container.horizontal();
                paramRow.gap("0.25em");
                paramRow.add(Text.of("  "));
                paramRow.add(Text.of(param.name()).classes("param-name"));
                paramRow.add(Text.of(": " + param.type().getSimpleName()).classes("param-type"));
                if (param.required()) {
                    paramRow.add(Text.of("(required)").classes("param-type"));
                }
                params.add(paramRow);
            }
            row.add(params);
        }

        return row;
    }

    // ==================================================================================
    // Prompt
    // ==================================================================================

    private Node buildPrompt() {
        Node inputNode;

        if (inputSnapshot != null && renderInputInSurface) {
            inputNode = buildInputFromSnapshot(inputSnapshot);
        } else if (inputSnapshot != null) {
            inputNode = null;  // Platform renderer handles prompt
        } else {
            Item ctx = item();
            String promptText = ctx != null
                    ? (ctx.emoji() != null ? ctx.emoji() + " " : "") + ctx.displayToken() + "> "
                    : "graph> ";
            inputNode = buildEmptyInput(promptText, "");
        }

        if (feedbackText != null && !feedbackText.isBlank()) {
            Container prompt = Container.vertical();
            prompt.id("prompt");
            prompt.classes("input");
            prompt.border("0.15ln solid #B04A9D");
            prompt.corner("0.4ch");

            Text feedback = Text.of((feedbackIsError ? "error: " : "  \u2192 ") + feedbackText);
            feedback.classes(feedbackIsError ? "feedback-error" : "feedback");
            prompt.add(feedback);
            if (inputNode != null) prompt.add(inputNode);
            return prompt;
        }

        if (inputNode == null) return null;

        Container prompt = Container.vertical();
        prompt.id("prompt");
        prompt.classes("input");
        prompt.border("0.15ln solid #B04A9D");
        prompt.corner("0.4ch");
        prompt.add(inputNode);
        return prompt;
    }

    // ==================================================================================
    // Input Construction
    // ==================================================================================

    private Node buildEmptyInput(String prompt, String hint) {
        Container outer = Container.vertical();
        outer.gap("0.25em");
        outer.classes("input-surface");

        Container row = Container.horizontal();
        row.gap("0.25em");
        row.classes("input-row");
        if (prompt != null && !prompt.isEmpty()) {
            row.add(Text.of(prompt).classes("prompt"));
        }
        if (hint != null && !hint.isEmpty()) {
            row.add(Text.of(hint).classes("hint", "muted"));
        }
        outer.add(row);
        return outer;
    }

    private Node buildInputFromSnapshot(InputSnapshot snapshot) {
        String prompt = snapshot.prompt();
        String pending = snapshot.pendingText() != null ? snapshot.pendingText() : "";
        String hint = snapshot.hint();
        String error = snapshot.error();
        int cursor = snapshot.cursor();

        Container outer = Container.vertical();
        outer.gap("0.25em");
        outer.classes("input-surface");

        // Input row: prompt + token chips + pending text
        Container row = Container.horizontal();
        row.gap("0.25em");
        row.classes("input-row");

        if (prompt != null && !prompt.isEmpty()) {
            row.add(Text.of(prompt).classes("prompt"));
        }

        // Token chips
        for (ExpressionToken token : snapshot.tokens()) {
            row.add(buildTokenChip(token));
        }

        // Pending text with cursor, or hint
        boolean hasContent = !snapshot.tokens().isEmpty() || !pending.isEmpty();
        if (!pending.isEmpty()) {
            Text pendingNode = Text.of(insertCursor(pending, cursor));
            pendingNode.editable(true);
            pendingNode.classes("pending");
            row.add(pendingNode);
        } else if (!hasContent && hint != null && !hint.isEmpty()) {
            row.add(Text.of(hint).classes("hint", "muted"));
        } else {
            // Empty pending — editable marker for platform cursor
            Text empty = Text.of("");
            empty.editable(true);
            row.add(empty);
        }

        outer.add(row);

        // Error
        if (error != null && !error.isEmpty()) {
            outer.add(Text.of(error).classes("error"));
        }

        // Completions dropdown
        if (snapshot.showCompletions() && snapshot.completionEntries() != null
                && !snapshot.completionEntries().isEmpty()) {
            outer.add(buildCompletions(snapshot.completionEntries(),
                    snapshot.selectedCompletion()));
        }

        return outer;
    }

    private Node buildTokenChip(ExpressionToken token) {
        boolean resolved = token instanceof ExpressionToken.RefToken;
        String emoji = null;

        if (token instanceof ExpressionToken.RefToken ref && resolver != null) {
            try {
                Optional<Item> item = resolver.apply(ref.target());
                if (item.isPresent()) emoji = item.get().emoji();
            } catch (Exception ignored) {}
        }

        if (resolved) {
            Container chip = Container.horizontal();
            chip.classes("token-chip", "resolved");
            chip.border("0.1em solid #4A5568");
            chip.corner("0.6em");
            chip.background("#2D3748");
            chip.padding("0.1em 0.4em");
            if (emoji != null && !emoji.isEmpty()) {
                chip.add(Text.of(emoji).classes("token-emoji"));
            }
            chip.add(Text.of(token.displayText()).classes("token"));
            return chip;
        } else {
            Container chip = Container.horizontal();
            chip.classes("token-chip");
            chip.add(Text.of(token.displayText()).classes("token"));
            return chip;
        }
    }

    private Node buildCompletions(List<CompletionEntry> completions, int selected) {
        Container list = Container.vertical();
        list.gap("0.125em");
        list.classes("completions");

        for (int i = 0; i < completions.size(); i++) {
            CompletionEntry entry = completions.get(i);
            boolean isSelected = (i == selected);

            Container row = Container.horizontal();
            row.gap("0.5em");
            if (isSelected) {
                row.classes("completion", "completion-selected");
            } else {
                row.classes("completion");
            }

            row.add(Text.of(isSelected ? "\u25B8 " : "  ")
                    .classes(isSelected ? "completion-indicator" : "completion-spacer"));
            if (entry.emoji() != null && !entry.emoji().isEmpty()) {
                row.add(Text.of(entry.emoji()).classes("completion-emoji"));
            }
            row.add(Text.of(entry.token()).classes("completion-token"));
            if (entry.typeName() != null && !entry.typeName().isEmpty()) {
                row.add(Text.of(entry.typeName()).classes("completion-type", "muted"));
            }
            list.add(row);
        }

        return list;
    }

    private static String insertCursor(String text, int pos) {
        if (text == null || text.isEmpty()) return "\u258F";
        int clamped = Math.max(0, Math.min(pos, text.length()));
        if (clamped >= text.length()) return text + "\u258F";
        return text.substring(0, clamped) + "\u258F" + text.substring(clamped);
    }

    // ==================================================================================
    // Toggle Actions
    // ==================================================================================

    public void toggleHelp() {
        helpVisible = !helpVisible;
        changed();
    }

    public void toggleTreeView(TreeView view) {
        if (activeTreeView == view) {
            activeTreeView = null;
        } else {
            activeTreeView = view;
        }
        rebuildTree();
        changed();
    }

    public boolean helpVisible() { return helpVisible; }
    public TreeView activeTreeView() { return activeTreeView; }

    // ==================================================================================
    // Tree Building
    // ==================================================================================

    private void rebuildTree() {
        if (activeTreeView == null) {
            treeModel = null;
            return;
        }

        switch (activeTreeView) {
            case MOUNTS -> buildMountsTree();
            case FRAMES -> buildFramesTree();
            case VERSIONS -> buildVersionsTree();
        }
    }

    private void buildMountsTree() {
        TreeLink rootTreeLink = TreeLink.of(root,
                TreeLink.ChildMode.PRESENTATION, resolver);
        treeModel = TreeModel.<TreeLink>builder(rootTreeLink)
                .children(TreeLink::children)
                .label(TreeLink::displayToken)
                .iconSurface(link -> ImageSurface.of(link.emoji())
                        .size("medium").circle().backgroundColor("#3C3C4E"))
                .expandable(TreeLink::isExpandable)
                .id(TreeLink::treeId)
                .showRoot(false)
                .build();
        treeModel.onChange(this::changed);
    }

    private void buildFramesTree() {
        Item resolved = item();
        if (resolved == null) { treeModel = null; return; }

        List<FrameNode> frameNodes = new ArrayList<>();
        if (resolved.frames() != null) {
            for (Frame frame : resolved.frames()) {
                frameNodes.add(new FrameNode(frame, resolved));
            }
        }
        FrameNode root = new FrameNode("Frames (" + frameNodes.size() + ")", frameNodes);

        treeModel = TreeModel.<FrameNode>builder(root)
                .children(FrameNode::children)
                .label(FrameNode::label)
                .iconSurface(node -> ImageSurface.of(node.emoji())
                        .size("medium"))
                .expandable(node -> !node.children().isEmpty())
                .id(FrameNode::id)
                .showRoot(false)
                .build();
        treeModel.onChange(this::changed);
    }

    private void buildVersionsTree() {
        Item resolved = item();
        List<FrameNode> nodes = new ArrayList<>();
        String vid = resolved != null && resolved.base() != null
                ? resolved.base().displayAtWidth(16) : "no version";
        nodes.add(new FrameNode("Current: " + vid, List.of()));

        FrameNode root = new FrameNode("Versions", nodes);
        treeModel = TreeModel.<FrameNode>builder(root)
                .children(FrameNode::children)
                .label(FrameNode::label)
                .iconSurface(node -> ImageSurface.of("\uD83D\uDCDC").size("medium"))
                .expandable(node -> !node.children().isEmpty())
                .id(FrameNode::id)
                .showRoot(false)
                .build();
        treeModel.onChange(this::changed);
    }

    // ==================================================================================
    // Frame Tree Node
    // ==================================================================================

    private static class FrameNode {
        private final String label;
        private final String emoji;
        private final String id;
        private final List<FrameNode> children;

        FrameNode(String label, List<FrameNode> children) {
            this.label = label;
            this.emoji = "\uD83D\uDCC2";
            this.id = "group:" + label;
            this.children = children;
        }

        FrameNode(Frame frame, Item item) {
            FrameKey key = frame.frameKey();
            this.label = resolveFrameLabel(frame, item);
            this.emoji = "\uD83D\uDCCB";
            this.id = "frame:" + (frame.bodyHash() != null
                    ? frame.bodyHash().displayAtWidth(12)
                    : key.toString());

            this.children = new ArrayList<>();
            FrameBody body = frame.body();
            if (body != null) {
                for (Binding b : body.frameBindings()) {
                    String role = item.resolveDisplayToken(b.role());
                    if (role == null) role = b.role().displayAtWidth(12);

                    String quals = "";
                    if (!b.qualifiers().isEmpty()) {
                        StringBuilder qb = new StringBuilder("[");
                        for (int i = 0; i < b.qualifiers().size(); i++) {
                            if (i > 0) qb.append(", ");
                            var q = b.qualifiers().get(i);
                            if (q instanceof FrameKey.Sememe s) {
                                String resolved = item.resolveDisplayToken(s.id());
                                qb.append(resolved != null ? resolved : s.id().displayAtWidth(8));
                            } else if (q instanceof FrameKey.Literal l) {
                                qb.append(l.value());
                            }
                        }
                        qb.append("]");
                        quals = qb.toString() + " ";
                    }

                    String target = resolveBindingTarget(b, item);
                    children.add(new FrameNode(role + ":" + quals + " \u2192 " + target, List.of()));
                }
            }
        }

        String label() { return label; }
        String emoji() { return emoji; }
        String id() { return id; }
        List<FrameNode> children() { return children; }

        private static String resolveFrameLabel(Frame frame, Item item) {
            FrameBody body = frame.body();

            if (body != null && body.predicate() != null) {
                String predName = item.resolveDisplayToken(body.predicate());
                if (predName == null) predName = body.predicate().displayAtWidth(12);

                FrameKey key = frame.frameKey();
                if (key.qualifiers() != null && !key.qualifiers().isEmpty()) {
                    StringBuilder quals = new StringBuilder(" [");
                    for (int i = 0; i < Math.min(key.qualifiers().size(), 3); i++) {
                        if (i > 0) quals.append(", ");
                        var q = key.qualifiers().get(i);
                        if (q instanceof FrameKey.Sememe s) {
                            String r = item.resolveDisplayToken(s.id());
                            quals.append(r != null ? r : s.id().displayAtWidth(8));
                        } else if (q instanceof FrameKey.Literal l) {
                            quals.append(l.value());
                        }
                    }
                    quals.append("]");
                    return predName + quals;
                }
                return predName;
            }

            FrameKey key = frame.frameKey();
            FrameKey.FrameToken head = key.head();
            if (head instanceof FrameKey.Literal lit) return lit.value();
            if (head instanceof FrameKey.Sememe sem) {
                String resolved = item.resolveDisplayToken(sem.id());
                return resolved != null ? resolved : sem.id().displayAtWidth(12);
            }
            return "?";
        }

        private static String resolveBindingTarget(Binding b, Item item) {
            ItemID targetId = b.targetId();
            if (targetId != null) {
                String resolved = item.resolveDisplayToken(targetId);
                return resolved != null ? resolved : targetId.displayAtWidth(12);
            }

            if (b.target() instanceof dev.everydaythings.graph.item.Literal lit) {
                if (dev.everydaythings.graph.item.Literal.TYPE_TEXT.equals(lit.valueType())) {
                    try {
                        String text = lit.asText();
                        if (text != null) {
                            return text.length() > 30
                                    ? "\"" + text.substring(0, 27) + "...\""
                                    : "\"" + text + "\"";
                        }
                    } catch (Exception ignored) {}
                }
                return lit.valueType().displayAtWidth(12);
            }

            return "\u2014";
        }
    }

    // ==================================================================================
    // Key Handling
    // ==================================================================================

    @Override
    public boolean handleKey(KeyChord chord) {
        if (chord.isKey(SpecialKey.F1)) return handleEvent("toggle:help", null);
        if (chord.isKey(SpecialKey.F2)) return handleEvent("toggle:mounts", null);
        if (chord.isKey(SpecialKey.F3)) return handleEvent("toggle:frames", null);
        if (chord.isKey(SpecialKey.F4)) return handleEvent("toggle:versions", null);

        if (treeModel != null && chord.alt() && !chord.ctrl() && !chord.shift()) {
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

        return false;
    }

    @Override
    public boolean handleEvent(String action, String target) {
        if (action == null) return false;

        if (action.startsWith("toggle:")) {
            String toggle = action.substring("toggle:".length());
            switch (toggle) {
                case "help" -> toggleHelp();
                case "mounts" -> toggleTreeView(TreeView.MOUNTS);
                case "frames" -> toggleTreeView(TreeView.FRAMES);
                case "versions" -> toggleTreeView(TreeView.VERSIONS);
                default -> { return false; }
            }
            return true;
        }

        if (treeModel != null) {
            return treeModel.handleEvent(action, target);
        }

        return false;
    }
}
