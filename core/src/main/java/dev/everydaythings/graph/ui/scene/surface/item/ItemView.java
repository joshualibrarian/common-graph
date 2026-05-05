package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.item.HandleResolver;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.TreeLink;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.parse.CompletionEntry;
import dev.everydaythings.graph.parse.ExpressionToken;
import dev.everydaythings.graph.parse.InputSnapshot;
import dev.everydaythings.graph.runtime.LibrarianOld;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneNode;

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
@Scene.Style(when = ".header", background = "#313244")
@Scene.Style(when = ".mode-btn", opacity = "dim")
@Scene.Style(when = ".mode-btn-active", background = "#3C3C4E", opacity = "bright")
@Scene.Style(when = ".muted", opacity = "dim")
@Scene.Style(when = ".bold", fontWeight = "bold")
@Scene.Style(when = ".mono", fontFamily = "monospace")
@Scene.Style(when = ".feedback-error", color = "#F38BA8")
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

        // Presence strip — shows when others are present
        @Scene.If("value.hasPresence")
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.3em", style = {"muted"})
        static class PresenceStrip {
            @Scene.Text.Literal(bind = "value.presenceSummary", style = {"muted"})
            static class PresenceText {}
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
        ItemOld ctx = item();
        return ctx != null && ctx.emoji() != null ? ctx.emoji() : "";
    }

    /** Type display name (resolved from @Implements key). */
    public String typeName() {
        ItemOld ctx = item();
        if (ctx == null) return "";
        ItemID typeId = typeIdOf(ctx);
        if (typeId == null) return ctx.displayToken();
        String resolved = ctx.resolveDisplayToken(typeId);
        return resolved != null ? resolved : ctx.displayToken();
    }

    /** Distinguishing label from HandleResolver (null if none). */
    public String handleLabel() {
        ItemOld ctx = item();
        if (ctx == null) return null;
        Collection<ItemOld> siblings = siblingsProvider != null
                ? siblingsProvider.get() : List.of();
        return HandleResolver.resolve(ctx, siblings);
    }

    /** Whether the help panel (F1) is visible. */
    public boolean helpVisible() { return detailMode == DetailMode.HELP; }

    /** Whether the meta panel is visible. */
    public boolean metaVisible() { return detailMode == DetailMode.META; }

    /** Current detail mode. */
    public DetailMode detailMode() { return detailMode; }

    /** Whether the detail panel is showing the item's actual scene (not help/meta/frame detail). */
    public boolean isShowingItemScene() {
        return detailMode == DetailMode.PRESENTATION && selectedTreeNodeId == null;
    }

    /** Whether any tree panel is visible. */
    public boolean treeVisible() { return activeTreeView != null; }

    /** Whether the mounts tree (F2) is active. */
    public boolean mountsActive() { return activeTreeView == TreeView.MOUNTS; }

    /** Whether the frames tree (F3) is active. */
    public boolean framesActive() { return activeTreeView == TreeView.FRAMES; }

    /** Whether the versions tree (F4) is active. */
    public boolean versionsActive() { return activeTreeView == TreeView.VERSIONS; }

    /** Whether there are presence frames (durable PRESENT or ephemeral state) on this item. */
    public boolean hasPresence() {
        return !presenceFrames().isEmpty() || !ephemeralFrames().isEmpty();
    }

    /** Summary of who is present — e.g., "👤 2 present". */
    public String presenceSummary() {
        List<FrameBodyOld> present = presenceFrames();
        if (present.isEmpty()) {
            // Fall back to counting ephemeral frames
            List<FrameBodyOld> ephemeral = ephemeralFrames();
            if (ephemeral.isEmpty()) return "";
            return "\uD83D\uDC64 " + ephemeral.size() + " active";
        }
        return "\uD83D\uDC64 " + present.size() + " present";
    }

    /** Get durable PRESENT frames on the current item. */
    private List<FrameBodyOld> presenceFrames() {
        ItemOld resolved = item();
        if (resolved == null || resolved.frames() == null) return List.of();
        ItemID presentPredicate = ItemID.fromString(
                dev.everydaythings.graph.language.PresenceVocabulary.Present.KEY);
        List<FrameBodyOld> result = new ArrayList<>();
        for (FrameOld f : resolved.frames()) {
            if (f.body() != null && presentPredicate.equals(f.body().predicate())) {
                result.add(f.body());
            }
        }
        return result;
    }

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
    public SceneNode treeContent() { return treeContentNode; }

    /**
     * Detail panel content — routes to help, frame detail, or item scene.
     *
     * <p>The routing logic is inherently conditional (which mode are we in?
     * what's selected in the tree?). Each branch produces a Node.
     */
    public SceneNode detailContent() {
        if (detailMode == DetailMode.HELP) return helpContent();
        if (detailMode == DetailMode.META) return metaContent();
        if (selectedTreeNodeId != null && activeTreeView != null) return selectedNodeContent();
        return itemContent();
    }

    /**
     * Input node — the prompt/input area built from InputSnapshot.
     *
     * <p>Token chips, cursor positioning, and completion lists are genuinely
     * dynamic and require procedural construction.
     */
    public SceneNode inputNode() {
        if (inputSnapshot != null && renderInputInSurface) {
            return inputFromSnapshot(inputSnapshot);
        }
        if (inputSnapshot != null) return null;

        // Empty prompt placeholder
        ItemOld ctx = item();
        String p = ctx != null
                ? (ctx.emoji() != null ? ctx.emoji() + " " : "") + ctx.displayToken() + "> "
                : "> ";
        SceneNode empty = SceneNode.vertical().classes("input-surface");
        SceneNode row = SceneNode.horizontal().gap("0.25em").classes("input-row");
        row.add(SceneNode.ofText(p).classes("prompt"));
        empty.add(row);
        return empty;
    }

    // ==================================================================================
    // State
    // ==================================================================================

    private Ref root;
    private Ref context;
    private final List<Ref> history = new ArrayList<>();
    private final Function<ItemID, Optional<ItemOld>> resolver;
    private Supplier<Collection<ItemOld>> siblingsProvider;

    /**
     * Provider for ephemeral frames on the current item (presence, cursors, etc.).
     * Set by the Session after construction. When non-null, ephemeral frame changes
     * trigger tree rebuilds alongside normal frame changes.
     */
    private EphemeralFrameProvider ephemeralProvider;
    private Runnable ephemeralListener;
    private ItemID watchedEphemeralItem;

    /** Functional interface for ephemeral frame access — avoids coupling to LibrarianHandle. */
    public interface EphemeralFrameProvider {
        List<FrameBodyOld> ephemeralFrames(ItemID itemId);
        void onEphemeralChanged(ItemID itemId, Runnable listener);
        void removeEphemeralListener(ItemID itemId, Runnable listener);
    }

    public void setEphemeralProvider(EphemeralFrameProvider provider) {
        this.ephemeralProvider = provider;
        watchEphemeralFrames();
    }

    /** Get ephemeral frames for the currently viewed item. */
    public List<FrameBodyOld> ephemeralFrames() {
        if (ephemeralProvider == null) return List.of();
        ItemOld current = item();
        if (current == null) return List.of();
        return ephemeralProvider.ephemeralFrames(current.iid());
    }

    /** Detail panel mode — what the right panel shows. */
    public enum DetailMode { PRESENTATION, HELP, META }

    private TreeView activeTreeView = null;
    private DetailMode detailMode = DetailMode.PRESENTATION;
    // TODO: Restore tree navigation on SceneNode
    private Object treeNav;
    private SceneNode treeContentNode;

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

    public ItemView(ItemOld item, Function<ItemID, Optional<ItemOld>> resolver) {
        this.root = Ref.of(item.iid());
        this.context = this.root;
        this.resolver = resolver;
        watchFrames();
    }

    /** Currently watched item (for unsubscribing on navigation). */
    private transient ItemOld watchedItem;

    /**
     * Subscribe to the current item's frame changes.
     * When frames are added/removed, the tree rebuilds and the UI re-renders.
     */
    private void watchFrames() {
        // Unsubscribe from old item
        if (watchedItem != null && watchedItem.frames() != null) {
            watchedItem.frames().onChanged(null);
        }

        ItemOld current = item();
        watchedItem = current;

        if (current != null && current.frames() != null) {
            current.frames().onChanged(() -> {
                rebuildTree();
                changed();
            });
        }

        // Also subscribe to ephemeral frame changes for this item
        watchEphemeralFrames();
    }

    /**
     * Subscribe to ephemeral frame changes for the current item.
     * When ephemeral frames are added/replaced/cleared, trigger a re-render.
     */
    private void watchEphemeralFrames() {
        // Unsubscribe from old item
        if (ephemeralProvider != null && watchedEphemeralItem != null && ephemeralListener != null) {
            ephemeralProvider.removeEphemeralListener(watchedEphemeralItem, ephemeralListener);
        }

        ItemOld current = item();
        if (current == null || ephemeralProvider == null) {
            watchedEphemeralItem = null;
            ephemeralListener = null;
            return;
        }

        watchedEphemeralItem = current.iid();
        ephemeralListener = () -> {
            // Ephemeral frame changed — re-render but don't rebuild the whole tree
            changed();
        };
        ephemeralProvider.onEphemeralChanged(watchedEphemeralItem, ephemeralListener);
    }

    // ==================================================================================
    // Compiled Node Tree
    // ==================================================================================

    public SceneNode toNode() {
        return SceneCompiler.compile(this);
    }

    public SceneNode toSceneNode() {
        return SceneCompiler.compileToSceneNode(this);
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    public Ref root() { return root; }
    public Ref context() { return context; }

    public ItemOld item() {
        return resolver.apply(context.target()).orElse(null);
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
        // TODO: restore tree navigation on SceneNode
        changed();
    }

    public void select(ItemOld item) {
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
    public void setSiblingsProvider(Supplier<Collection<ItemOld>> provider) {
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

    public void toggleDetailMode() {
        detailMode = switch (detailMode) {
            case PRESENTATION -> DetailMode.HELP;
            case HELP -> DetailMode.META;
            case META -> DetailMode.PRESENTATION;
        };
        changed();
    }
    public void toggleTreeView(TreeView view) {
        activeTreeView = (activeTreeView == view) ? null : view;
        rebuildTree(); changed();
    }
    public TreeView activeTreeView() { return activeTreeView; }

    // ==================================================================================
    // Detail Content (procedural — routing logic)
    // ==================================================================================

    private SceneNode selectedNodeContent() {
        ItemOld resolved = item();
        if (resolved == null) return SceneNode.ofText("");

        // Try as an ItemID — shows the item's scene
        try {
            ItemID iid = ItemID.fromString(selectedTreeNodeId);
            Optional<ItemOld> selected = resolver.apply(iid);
            if (selected.isPresent()) return itemContent(selected.get());
        } catch (Exception ignored) {}

        // Try as a frame reference — shows the frame's bindings
        if (resolved.frames() != null) {
            for (FrameOld frame : resolved.frames()) {
                String id = "frame:" + (frame.bodyHash() != null
                        ? frame.bodyHash().displayAtWidth(12) : frame.frameKey().toString());
                if (id.equals(selectedTreeNodeId)) {
                    return buildFrameDetail(frame, resolved);
                }
            }
        }

        return itemContent();
    }

    private SceneNode buildFrameDetail(FrameOld frame, ItemOld resolved) {
        SceneNode detail = SceneNode.vertical().gap("0.5em");

        // Heading — resolved predicate name
        String pred = FrameNode.resolvePredicate(frame, resolved);
        detail.add(SceneNode.ofText(pred).fontWeight("bold").classes("heading"));

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
        ItemID typeId = frame.type() != null ? frame.type() : FrameBodyOld.TYPE_ID;
        if (frame.body() != null) {
            detail.add(SceneNode.ofText("(inspector TBD)"));
        } else if (frame.instance() instanceof Canonical canonical) {
            detail.add(SceneNode.ofText("(inspector TBD)"));
        }

        // Hash footer
        if (frame.bodyHash() != null) {
            detail.add(SceneNode.ofText("Hash: " + frame.bodyHash().fullDisplay()).classes("mono", "muted"));
        }

        return detail;
    }

    /**
     * Meta content — manifest body + record rendered via CborInspector.
     */
    private SceneNode metaContent() {
        ItemOld resolved = item();
        if (resolved == null) return SceneNode.ofText("No item").classes("muted");

        SceneNode meta = SceneNode.vertical().gap("0.5em");
        meta.add(SceneNode.ofText("meta").fontWeight("bold").classes("heading"));

        // Item IID and type
        meta.add(SceneNode.ofText("IID: " + resolved.iid().encodeText()).classes("mono", "muted"));
        String typeName = resolved.getClass().getSimpleName();
        meta.add(SceneNode.ofText("type: " + typeName).classes("muted"));

        // Frame count
        int frameCount = 0;
        if (resolved.frames() != null) {
            for (var f : resolved.frames()) frameCount++;
        }
        meta.add(SceneNode.ofText("frames: " + frameCount).classes("muted"));

        // Item-level bindings (pending, not yet in manifest)
        List<Binding> pending = resolved.itemBindings();
        if (!pending.isEmpty()) {
            meta.add(SceneNode.ofText("item bindings:").fontWeight("bold"));
            Function<ItemID, String> labelResolver = resolved::resolveDisplayToken;
            for (Binding b : pending) {
                meta.add(renderBinding(b, labelResolver));
            }
        }

        // Manifest
        ManifestOld mf = resolved.current();
        if (mf != null) {
            // VID
            if (mf.vid() != null) {
                meta.add(SceneNode.ofText("VID: " + mf.vid().displayAtWidth(20)).classes("mono", "muted"));
            }

            // Implementation
            if (mf.implementationName() != null) {
                meta.add(SceneNode.ofText("impl: " + mf.implementationName()).classes("mono", "muted"));
            }

            // Identity bindings
            List<Binding> identity = mf.identityBindings();
            if (!identity.isEmpty()) {
                meta.add(SceneNode.ofText("identity bindings:").fontWeight("bold"));
                Function<ItemID, String> labelResolver = iid ->
                        resolved.resolveDisplayToken(iid);
                for (Binding b : identity) {
                    meta.add(renderBinding(b, labelResolver));
                }
            }

            // Non-identity bindings
            List<Binding> nonIdentity = mf.nonIdentityBindings();
            if (!nonIdentity.isEmpty()) {
                meta.add(SceneNode.ofText("non-identity bindings:").fontWeight("bold"));
                Function<ItemID, String> labelResolver = iid ->
                        resolved.resolveDisplayToken(iid);
                for (Binding b : nonIdentity) {
                    meta.add(renderBinding(b, labelResolver));
                }
            }

            // Render full manifest via CborInspector
            Function<ItemID, String> resolver = iid -> {
                if (resolved.itemLibrarian() == null) return null;
                return resolved.itemLibrarian().get(iid)
                        .map(ItemOld::displayToken).orElse(null);
            };
            meta.add(SceneNode.ofText("raw manifest:").fontWeight("bold").classes("muted"));
            meta.add(SceneNode.ofText("(inspector TBD)"));
        } else {
            meta.add(SceneNode.ofText("(uncommitted — no manifest yet)").classes("muted"));
        }

        return meta;
    }

    /**
     * Render a single binding as a Node for the meta view.
     */
    private static SceneNode renderBinding(Binding b, Function<ItemID, String> resolver) {
        String roleName = resolver.apply(b.role());
        String role = roleName != null ? roleName : b.role().displayAtWidth(12);

        String value;
        if (b.targetId() != null) {
            String resolved = resolver.apply(b.targetId());
            value = resolved != null ? resolved : b.targetId().displayAtWidth(16);
        } else if (b.target() != null) {
            value = b.target().toString();
        } else {
            value = "(null)";
        }

        SceneNode row = SceneNode.horizontal().gap("0.5em");
        row.add(SceneNode.ofText(role + ":").classes("muted"));
        row.add(SceneNode.ofText(value));
        if (b.identity()) row.add(SceneNode.ofText("[id]").classes("mono", "muted"));
        return row;
    }

    private SceneNode itemContent(ItemOld resolved) {
        Class<?> clazz = resolved.getClass();
        if (clazz != ItemOld.class && SceneCompiler.has2DAnnotation(clazz)) {
            try {
                SceneNode content = SceneCompiler.compile(resolved);
                if (content != null) return content;
            } catch (Exception ignored) {}
        }
        return defaultItemSummary(resolved);
    }

    private SceneNode itemContent() {
        ItemOld resolved = item();
        if (resolved == null) return SceneNode.ofText("");
        return itemContent(resolved);
    }

    private SceneNode defaultItemSummary(ItemOld resolved) {
        SceneNode s = SceneNode.vertical().gap("0.5em");
        s.add(SceneNode.ofSememe(resolved.iid()).fontWeight("bold").classes("heading"));
        if (resolved.iid() != null)
            s.add(SceneNode.ofText("IID: " + resolved.iid().displayAtWidth(20)).classes("mono", "muted"));
        int frames = 0;
        if (resolved.frames() != null) for (var f : resolved.frames()) frames++;
        s.add(SceneNode.ofText(frames + " frames").classes("muted"));
        s.add(SceneNode.ofText(resolved.vocabulary().localTokenCount() + " local tokens").classes("muted"));
        return s;
    }

    // ==================================================================================
    // Help Content (procedural — vocabulary iteration)
    // ==================================================================================

    private SceneNode helpContent() {
        ItemOld ctx = item();
        LibrarianOld lib = ctx != null ? ctx.itemLibrarian() : null;
        SceneNode help = SceneNode.vertical().gap("0.5em");
        if (ctx != null) help.add(scopeSection(ctx.iid(), ctx.vocabulary()));
        if (lib != null) help.add(scopeSection(lib.iid(), lib.vocabulary()));
        return help;
    }

    private SceneNode scopeSection(ItemID nameId, Vocabulary vocab) {
        SceneNode s = SceneNode.vertical().gap("0.25em");
        s.add(SceneNode.ofSememe(nameId).fontWeight("bold"));
        if (vocab != null) {
            List<Posting> tokens = vocab.prefixMatch("");
            if (!tokens.isEmpty()) {
                SceneNode tl = SceneNode.vertical().gap("0.0625em");
                for (Posting p : tokens) {
                    SceneNode row = SceneNode.horizontal().gap("0.5em");
                    row.add(SceneNode.ofText(p.token()).fontWeight("bold"));
                    row.add(SceneNode.ofText("\u2192").classes("muted"));
                    row.add(SceneNode.ofText(p.target() != null ? p.target().displayAtWidth(16) : "\u2014").classes("muted"));
                    tl.add(row);
                }
                s.add(tl);
            }
        }
        return s;
    }

    // ==================================================================================
    // Input Rendering (procedural — tokens, cursor, completions)
    // ==================================================================================

    private SceneNode inputFromSnapshot(InputSnapshot snap) {
        SceneNode outer = SceneNode.vertical().gap("0.25em").classes("input-surface");
        SceneNode row = SceneNode.horizontal().gap("0.25em").classes("input-row");
        if (snap.prompt() != null && !snap.prompt().isEmpty())
            row.add(SceneNode.ofText(snap.prompt()).classes("prompt"));
        for (ExpressionToken token : snap.tokens()) row.add(tokenChip(token));
        String pending = snap.pendingText() != null ? snap.pendingText() : "";
        boolean hasContent = !snap.tokens().isEmpty() || !pending.isEmpty();
        if (!pending.isEmpty()) {
            row.add(SceneNode.ofText(insertCursor(pending, snap.cursor())).editable(true).classes("pending"));
        } else if (!hasContent && snap.hint() != null && !snap.hint().isEmpty()) {
            row.add(SceneNode.ofText(snap.hint()).classes("hint", "muted"));
        } else {
            row.add(SceneNode.ofText("").editable(true));
        }
        outer.add(row);
        if (snap.error() != null && !snap.error().isEmpty())
            outer.add(SceneNode.ofText(snap.error()).classes("error"));
        if (snap.showCompletions() && snap.completionEntries() != null && !snap.completionEntries().isEmpty())
            outer.add(completionsList(snap.completionEntries(), snap.selectedCompletion()));
        return outer;
    }

    private SceneNode tokenChip(ExpressionToken token) {
        boolean resolved = token instanceof ExpressionToken.RefToken;
        String emoji = null;
        if (token instanceof ExpressionToken.RefToken ref && resolver != null) {
            try {
                Optional<ItemOld> it = resolver.apply(ref.target());
                if (it.isPresent()) emoji = it.get().emoji();
            } catch (Exception ignored) {}
        }
        if (resolved) {
            SceneNode c = SceneNode.horizontal().classes("token-chip", "token-chip-resolved")
                    .padding("0.1em 0.4em");
            if (emoji != null && !emoji.isEmpty()) c.add(SceneNode.ofText(emoji));
            c.add(SceneNode.ofText(token.displayText()));
            return c;
        }
        SceneNode c = SceneNode.horizontal().classes("token-chip");
        c.add(SceneNode.ofText(token.displayText()));
        return c;
    }

    private SceneNode completionsList(List<CompletionEntry> entries, int selected) {
        SceneNode list = SceneNode.vertical().gap("0.125em").classes("completions");
        for (int i = 0; i < entries.size(); i++) {
            CompletionEntry e = entries.get(i);
            boolean sel = (i == selected);
            SceneNode row = SceneNode.horizontal().gap("0.5em")
                    .classes(sel ? "completion completion-selected" : "completion");
            row.add(SceneNode.ofText(sel ? "\u25B8 " : "  ").classes(sel ? "completion-indicator" : "completion-spacer"));
            if (e.emoji() != null && !e.emoji().isEmpty()) row.add(SceneNode.ofText(e.emoji()));
            row.add(SceneNode.ofText(e.token()));
            if (e.typeName() != null && !e.typeName().isEmpty()) row.add(SceneNode.ofText(e.typeName()).classes("muted"));
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
        treeContentNode = SceneNode.ofText("(tree view TBD)"); // TODO: reimplement tree building on SceneNode
        treeNav = null; // TODO: reimplement tree navigation on SceneNode
    }

    private void buildFramesTree() {
        ItemOld resolved = item();
        if (resolved == null) { treeNav = null; treeContentNode = null; return; }

        // Durable frames from the item's EndorsementsTable
        List<FrameNode> durableNodes = new ArrayList<>();
        if (resolved.frames() != null) {
            for (FrameOld f : resolved.frames()) durableNodes.add(new FrameNode(f, resolved));
        }

        // Ephemeral frames from the Librarian's in-memory store
        List<FrameNode> ephemeralNodes = new ArrayList<>();
        List<FrameBodyOld> ephemeral = ephemeralFrames();
        for (FrameBodyOld body : ephemeral) {
            ephemeralNodes.add(new FrameNode(body, resolved));
        }

        // Combine into tree — ephemeral frames grouped under their own header if present
        List<FrameNode> allNodes = new ArrayList<>(durableNodes);
        if (!ephemeralNodes.isEmpty()) {
            allNodes.add(new FrameNode("\u26A1 Ephemeral (" + ephemeralNodes.size() + ")",
                    "\u26A1", "group:ephemeral", ephemeralNodes));
        }

        int totalCount = durableNodes.size() + ephemeralNodes.size();
        FrameNode root = new FrameNode("Frames (" + totalCount + ")",
                "\uD83D\uDCC2", "group:frames", allNodes);
        treeContentNode = SceneNode.ofText("(tree view TBD)"); // TODO: reimplement tree building on SceneNode
        treeNav = null; // TODO: reimplement tree navigation on SceneNode
    }

    private void buildVersionsTree() {
        ItemOld resolved = item();
        String vid = resolved != null && resolved.base() != null
                ? resolved.base().displayAtWidth(16) : "?";
        FrameNode r = new FrameNode("Versions", "\uD83D\uDCC2", "group:versions",
                List.of(new FrameNode(vid, "\uD83D\uDCCB", "version:" + vid, List.of())));
        treeContentNode = SceneNode.ofText("(tree view TBD)"); // TODO: reimplement tree building on SceneNode
        treeNav = null; // TODO: reimplement tree navigation on SceneNode
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    static ItemID typeIdOf(ItemOld item) {
        try {
            return ItemOld.idOf(item.getClass());
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
        if (chord.isKey(SpecialKey.F1)) return handleEvent("toggle:detail", null);
        if (chord.isKey(SpecialKey.F2)) return handleEvent("toggle:mounts", null);
        if (chord.isKey(SpecialKey.F3)) return handleEvent("toggle:frames", null);
        if (chord.isKey(SpecialKey.F4)) return handleEvent("toggle:versions", null);
        // TODO: restore tree navigation keyboard handling on SceneNode
        if (false && chord.alt() && !chord.ctrl() && !chord.shift()) {
        }
        return false;
    }

    public boolean handleEvent(String action, String target) {
        if (action == null) return false;
        if (action.startsWith("toggle:")) {
            return switch (action.substring("toggle:".length())) {
                case "help", "detail" -> { toggleDetailMode(); yield true; }
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
                Optional<ItemOld> item = resolver.apply(iid);
                if (item.isPresent()) {
                    select(Ref.of(iid));
                    return true;
                }
            } catch (Exception ignored) {}
            // TODO: restore tree nav select
            changed();
            return true;
        }
        return false;
    }

    private void syncTreeNav() {
        // TODO: restore tree nav sync
    }

    // ==================================================================================
    // FrameNode (data record for frame tree)
    // ==================================================================================

    @Log4j2
    static class FrameNode {
        private final String label, emoji, id;
        private final FrameOld frame;
        private final List<FrameNode> children;

        FrameNode(String label, String emoji, String id, List<FrameNode> children) {
            this.label = label; this.emoji = emoji; this.id = id;
            this.frame = null; this.children = children;
        }

        FrameNode(FrameOld frame, ItemOld item) {
            this.frame = frame;
            this.emoji = "\uD83D\uDCCB";
            this.id = "frame:" + (frame.bodyHash() != null
                    ? frame.bodyHash().displayAtWidth(12)
                    : frame.frameKey().toString());
            this.label = buildSummaryLabel(frame, item);
            this.children = List.of();
        }

        /** Construct from an ephemeral FrameBody (no Frame wrapper, no body hash). */
        FrameNode(FrameBodyOld body, ItemOld item) {
            this.frame = null;
            this.emoji = "\u26A1";  // ⚡ lightning bolt for ephemeral
            this.id = "ephemeral:" + body.predicate().encodeText();
            LibrarianOld lib = item.itemLibrarian();
            if (lib != null) {
                this.label = "\u26A1 " + HandleResolver.labelForFrame(body, item.iid(), lib);
            } else {
                this.label = "\u26A1 ephemeral";
            }
            this.children = List.of();
        }

        String label() { return label; }
        String emoji() { return emoji; }
        String id() { return id; }
        List<FrameNode> children() { return children; }
        FrameOld frame() { return frame; }

        private static final int MAX_LABEL_LENGTH = 50;

        private static String buildSummaryLabel(FrameOld frame, ItemOld item) {
            FrameBodyOld body = frame.body();
            if (body == null) {
                return truncate(resolveFrameKeyLabel(frame, item));
            }

            LibrarianOld lib = item.itemLibrarian();
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
        private static String resolveFrameKeyLabel(FrameOld frame, ItemOld item) {
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
            CompoundKey key = frame.frameKey();
            if (key != null && !key.tokens().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (CompoundKey.FrameToken token : key.tokens()) {
                    if (token instanceof CompoundKey.Sememe s) {
                        String r = item.resolveDisplayToken(s.id());
                        if (r != null) {
                            if (!sb.isEmpty()) sb.append(" \u2014 ");
                            sb.append(r);
                        }
                        // Skip unresolvable sememe tokens — don't show hashes
                    } else if (token instanceof CompoundKey.Literal l) {
                        if (!sb.isEmpty()) sb.append(" \u2014 ");
                        sb.append(l.value());
                    }
                }
                if (!sb.isEmpty()) return sb.toString();
            }

            return "frame";
        }

        /** Extract literal tokens from a FrameKey as qualifier context, skipping hash-like values. */
        private static String literalQualifiers(CompoundKey key) {
            if (key == null) return null;
            StringBuilder sb = new StringBuilder();
            for (CompoundKey.FrameToken token : key.tokens()) {
                if (token instanceof CompoundKey.Literal l) {
                    String v = l.value();
                    // Skip literals that are or contain raw hashes
                    if (v.startsWith("iid:") || v.startsWith("cid:") || v.startsWith("vid:")) continue;
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(v);
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }

        static String resolvePredicate(FrameOld frame, ItemOld item) {
            if (frame.body() != null && frame.body().predicate() != null) {
                String r = item.resolveDisplayToken(frame.body().predicate());
                if (r != null) return r;
            }
            CompoundKey key = frame.frameKey();
            if (key != null && !key.tokens().isEmpty()) {
                CompoundKey.FrameToken head = key.tokens().get(0);
                if (head instanceof CompoundKey.Sememe s) {
                    String r = item.resolveDisplayToken(s.id());
                    if (r != null) return r;
                } else if (head instanceof CompoundKey.Literal l) {
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

        static String fmtTarget(Binding b, ItemOld item) {
            ItemID tid = b.targetId();
            if (tid != null) {
                String r = item.resolveDisplayToken(tid);
                if (r != null) return r;
                logger.debug("FrameNode: unresolved binding target {}", tid::encodeText);
                return null;
            }
            if (b.target() instanceof dev.everydaythings.graph.item.Literal lit) {
                // Use type-aware formatting (handles text, instant, integer, etc.)
                try {
                    String formatted = lit.toString();
                    if (formatted != null && formatted.length() > 40) {
                        formatted = formatted.substring(0, 37) + "...";
                    }
                    return formatted;
                } catch (Exception ignored) {}
                return null;
            }
            return null;
        }
    }
}
