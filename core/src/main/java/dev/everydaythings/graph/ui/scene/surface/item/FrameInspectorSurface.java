package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative inspector surface showing an item's frames and bindings.
 *
 * <p>Built entirely from {@code @Scene.*} annotations — no procedural
 * {@code render()} method. The surface binds to a flat data model
 * populated from the item's frames at construction time.
 *
 * <p>Structure:
 * <pre>
 * ┌──────────────────────────────────┐
 * │ IID: abc123...                   │
 * │ VID: def456...  *                │
 * ├──────────────────────────────────┤
 * │ Frames (12)                      │
 * │ ▸ LEXEME [ENGLISH, VERB] 3 bind  │
 * │ ▸ INSTANCE_OF             2 bind │
 * │ ▸ TITLE                   2 bind │
 * └──────────────────────────────────┘
 * </pre>
 */
@Getter
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"inspect-surface", "inspect-frames"})
@Scene.Rule(match = ".inspect-label", opacity = "dim")
@Scene.Rule(match = ".inspect-value", fontFamily = "monospace")
@Scene.Rule(match = ".binding-role", fontWeight = "bold")
@Scene.Rule(match = ".binding-target", fontFamily = "monospace")
@Scene.Rule(match = ".badge", opacity = "dim", fontFamily = "monospace")
@Scene.Rule(match = ".section-title", fontWeight = "bold")
public class FrameInspectorSurface extends SurfaceSchema<Item> {

    // ==================================================================================
    // Declarative Structure
    // ==================================================================================

    /** IID header row */
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"inspect-header"}, gap = "0.25em")
    static class IidRow {
        @Scene.Text(bind = "'IID: '", style = {"inspect-label"})
        static class Label {}

        @Scene.Text(bind = "iidDisplay", style = {"inspect-value"})
        static class Value {}
    }

    /** VID header row */
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"inspect-header"}, gap = "0.25em")
    static class VidRow {
        @Scene.Text(bind = "'VID: '", style = {"inspect-label"})
        static class Label {}

        @Scene.Text(bind = "vidDisplay", style = {"inspect-value"})
        static class Value {}

        @Scene.Text(bind = "' *'", style = {"dirty-indicator"})
        @Scene.If("dirty")
        static class DirtyFlag {}
    }

    /** Frame count header */
    @Scene.Text(bind = "frameCountLabel", style = {"section-title"})
    static class FrameCountHeader {}

    /** No frames placeholder */
    @Scene.Text(bind = "'No frames'", style = {"muted"})
    @Scene.If("empty")
    static class EmptyMessage {}

    /** Frame list */
    @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.125em")
    @Scene.If("!empty")
    static class FrameList {
        @Scene.Repeat(bind = "frameEntries")
        static class FrameEntry {
            /** Frame summary row */
            @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"frame-entry"}, gap = "0.5em")
            static class Summary {
                @Scene.Text(bind = "$item.predicateName", style = {"frame-name"})
                static class PredicateName {}

                @Scene.Text(bind = "$item.qualifierDisplay", style = {"mono", "muted"})
                @Scene.If("$item.qualifierDisplay != null")
                static class Qualifier {}

                @Scene.Text(bind = "$item.bindingCountLabel", style = {"muted"})
                static class BindingCount {}

                @Scene.Text(bind = "'[identity]'", style = {"badge"})
                @Scene.If("$item.identity")
                static class IdentityBadge {}

                @Scene.Text(bind = "'[stream]'", style = {"badge"})
                @Scene.If("$item.stream")
                static class StreamBadge {}

                @Scene.Text(bind = "'[endorsed]'", style = {"badge"})
                @Scene.If("$item.endorsed")
                static class EndorsedBadge {}
            }

            /** Binding details (shown for each frame) */
            @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"binding-list"}, gap = "0.0625em")
            @Scene.If("$item.hasBindings")
            static class Bindings {
                @Scene.Repeat(bind = "$item.bindingEntries")
                static class BindingEntry {
                    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"binding-row"}, gap = "0.5em")
                    static class Row {
                        @Scene.Text(bind = "$item.roleDisplay", style = {"binding-role"})
                        static class Role {}

                        @Scene.Text(bind = "$item.qualifiersDisplay", style = {"mono", "muted"})
                        @Scene.If("$item.qualifiersDisplay != null")
                        static class Qualifiers {}

                        @Scene.Text(bind = "'→'", style = {"muted"})
                        static class Arrow {}

                        @Scene.Text(bind = "$item.targetDisplay", style = {"binding-target"})
                        static class Target {}

                        @Scene.Text(bind = "'[id]'", style = {"badge"})
                        @Scene.If("$item.identity")
                        static class IdFlag {}

                        @Scene.Text(bind = "'[idx]'", style = {"badge"})
                        @Scene.If("$item.indexed")
                        static class IndexFlag {}
                    }
                }
            }
        }
    }

    // ==================================================================================
    // Data Model
    // ==================================================================================

    private String iidDisplay;
    private String vidDisplay;
    private boolean dirty;
    private boolean empty;
    private String frameCountLabel;
    private List<FrameEntryData> frameEntries;

    public FrameInspectorSurface() {}

    /**
     * Build from an Item.
     */
    public static FrameInspectorSurface of(Item item) {
        FrameInspectorSurface surface = new FrameInspectorSurface();
        surface.iidDisplay = item.iid() != null ? item.iid().displayAtWidth(16) : "—";
        surface.vidDisplay = item.base() != null ? item.base().displayAtWidth(16) : "—";
        surface.dirty = item.dirty();

        surface.frameEntries = new ArrayList<>();
        if (item.frames() != null) {
            for (Frame frame : item.frames()) {
                surface.frameEntries.add(FrameEntryData.from(item, frame));
            }
        }

        surface.empty = surface.frameEntries.isEmpty();
        surface.frameCountLabel = "Frames (" + surface.frameEntries.size() + ")";

        return surface;
    }

    // ==================================================================================
    // Data Records
    // ==================================================================================

    /**
     * Data for one frame in the inspector.
     */
    @Getter
    public static class FrameEntryData {
        private String predicateName;
        private String qualifierDisplay;
        private String bindingCountLabel;
        private boolean identity;
        private boolean stream;
        private boolean endorsed;
        private boolean hasBindings;
        private List<BindingEntryData> bindingEntries;

        static FrameEntryData from(Item item, Frame frame) {
            FrameEntryData data = new FrameEntryData();
            FrameKey key = frame.frameKey();
            FrameBody body = frame.body();

            data.predicateName = resolveToken(item, key.head());
            data.qualifierDisplay = resolveQualifiers(item, key.qualifiers());
            data.identity = frame.identity();
            data.stream = body != null && body.isStream();
            data.endorsed = true; // frames from EndorsementsTable are endorsed

            data.bindingEntries = new ArrayList<>();
            if (body != null) {
                for (Binding b : body.frameBindings()) {
                    data.bindingEntries.add(BindingEntryData.from(item, b));
                }
            }
            data.hasBindings = !data.bindingEntries.isEmpty();
            data.bindingCountLabel = data.bindingEntries.size() + " bind";

            return data;
        }
    }

    /**
     * Data for one binding in the inspector.
     */
    @Getter
    public static class BindingEntryData {
        private String roleDisplay;
        private String qualifiersDisplay;
        private String targetDisplay;
        private boolean identity;
        private boolean indexed;

        static BindingEntryData from(Item item, Binding b) {
            BindingEntryData data = new BindingEntryData();
            data.roleDisplay = resolveId(item, b.role());
            data.qualifiersDisplay = resolveQualifiers(item, b.qualifiers());
            data.identity = b.identity();
            data.indexed = b.index();

            if (b.target() instanceof Literal lit) {
                try {
                    String text = lit.asText();
                    data.targetDisplay = text.length() > 40 ? text.substring(0, 37) + "..." : text;
                } catch (Exception e) {
                    data.targetDisplay = lit.valueType().displayAtWidth(12);
                }
            } else {
                ItemID targetId = b.targetId();
                data.targetDisplay = targetId != null ? resolveId(item, targetId) : "—";
            }

            return data;
        }
    }

    // ==================================================================================
    // Resolution Helpers
    // ==================================================================================

    private static String resolveToken(Item item, FrameKey.FrameToken token) {
        if (token instanceof FrameKey.Literal lit) return lit.value();
        if (token instanceof FrameKey.Sememe sem) return resolveId(item, sem.id());
        return "?";
    }

    private static String resolveId(Item item, ItemID id) {
        if (id == null) return "—";
        if (item.itemLibrarian() != null) {
            var opt = item.itemLibrarian().get(id, Item.class);
            if (opt.isPresent()) return opt.get().displayToken();
        }
        return id.displayAtWidth(12);
    }

    private static String resolveQualifiers(Item item, List<FrameKey.FrameToken> quals) {
        if (quals == null || quals.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < quals.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(resolveToken(item, quals.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}
