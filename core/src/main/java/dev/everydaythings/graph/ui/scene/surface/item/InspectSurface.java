package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector surface — developer view of an item's internals.
 *
 * <p>Two sub-modes driven by {@link ViewConfig.InspectMode}:
 *
 * <h3>FRAMES mode</h3>
 * <ul>
 *   <li>Item IID and current VID</li>
 *   <li>List of frames: resolved predicate name, qualifier, binding count, flags</li>
 * </ul>
 *
 * <h3>VERSIONS mode</h3>
 * <ul>
 *   <li>Current VID, parent VIDs</li>
 *   <li>Dirty indicator</li>
 *   <li>Placeholder for version DAG visualization</li>
 * </ul>
 */
public class InspectSurface extends SurfaceSchema<Void> {

    private ViewConfig.InspectMode inspectMode;
    private List<FrameInfo> frames;
    private String iid;
    private String vid;
    private boolean dirty;

    public InspectSurface() {}

    /**
     * Build an InspectSurface from an Item.
     *
     * @param item        the item to inspect
     * @param inspectMode FRAMES or VERSIONS
     */
    public static InspectSurface of(Item item, ViewConfig.InspectMode inspectMode) {
        InspectSurface surface = new InspectSurface();
        surface.inspectMode = inspectMode != null ? inspectMode : ViewConfig.InspectMode.FRAMES;
        surface.iid = item.iid() != null ? item.iid().displayAtWidth(16) : "—";
        surface.vid = item.base() != null ? item.base().displayAtWidth(16) : "—";
        surface.dirty = item.dirty();

        // Collect frame info with resolved names
        surface.frames = new ArrayList<>();
        for (Frame frame : item.frames()) {
            FrameKey key = frame.frameKey();

            int bindingCount = frame.body() != null ? frame.body().frameBindings().size() : 0;
            boolean identity = frame.identity();
            boolean stream = frame.body() != null && frame.body().isStream();

            String name = resolveKeyHead(item, key);
            String qualifier = resolveQualifiers(item, key);

            surface.frames.add(new FrameInfo(name, qualifier, bindingCount, identity, stream));
        }

        return surface;
    }

    /**
     * Resolve the head token of a FrameKey to a human-readable name.
     */
    private static String resolveKeyHead(Item item, FrameKey key) {
        FrameKey.FrameToken head = key.head();
        if (head instanceof FrameKey.Literal lit) {
            return lit.value();
        }
        if (head instanceof FrameKey.Sememe sem) {
            String resolved = item.resolveDisplayToken(sem.id());
            if (resolved != null) return resolved;
            // Fallback: truncated hash
            return sem.id().displayAtWidth(12);
        }
        return "?";
    }

    /**
     * Resolve qualifier tokens to a display string.
     *
     * <p>Returns null if no qualifiers. Literal qualifiers that look like
     * IIDs are resolved to item display names. Sememe qualifiers are resolved
     * to their display tokens.
     */
    private static String resolveQualifiers(Item item, FrameKey key) {
        List<FrameKey.FrameToken> quals = key.qualifiers();
        if (quals.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < quals.size(); i++) {
            if (i > 0) sb.append(", ");
            FrameKey.FrameToken token = quals.get(i);
            if (token instanceof FrameKey.Literal lit) {
                sb.append('"').append(resolveLiteralQualifier(item, lit.value())).append('"');
            } else if (token instanceof FrameKey.Sememe sem) {
                String resolved = item.resolveDisplayToken(sem.id());
                sb.append(resolved != null ? resolved : sem.id().displayAtWidth(12));
            }
        }
        return sb.toString();
    }

    /**
     * Try to resolve a literal qualifier to a human-readable string.
     *
     * <p>Handles several patterns:
     * <ul>
     *   <li>Pure IID ("iid:abc...") → look up item display name</li>
     *   <li>Compound "{iid}:{suffix}" → show just the suffix</li>
     *   <li>Short strings → as-is</li>
     *   <li>Long strings → truncated</li>
     * </ul>
     */
    private static String resolveLiteralQualifier(Item item, String value) {
        if (value == null) return "?";

        if (value.startsWith("iid:") || value.startsWith("\\")) {
            // Try to parse as a pure IID first
            if (item.itemLibrarian() != null) {
                try {
                    ItemID targetId = ItemID.fromString(value);
                    var targetOpt = item.itemLibrarian().get(targetId, Item.class);
                    if (targetOpt.isPresent()) {
                        return targetOpt.get().displayToken();
                    }
                } catch (Exception ignored) {
                    // Not a pure IID — might be compound
                }
            }

            // Compound pattern: "{iid}:{human-readable-suffix}"
            // IIDs are base32-encoded so they only contain [a-z2-7], plus "iid:" prefix.
            // Find where the IID ends and the suffix begins.
            String withoutPrefix = value.startsWith("iid:") ? value.substring(4) : value.substring(1);
            int suffixStart = -1;
            for (int i = 0; i < withoutPrefix.length(); i++) {
                char c = withoutPrefix.charAt(i);
                if (c == ':') {
                    // Check if what follows looks like a human name (contains uppercase or space)
                    String rest = withoutPrefix.substring(i + 1);
                    if (!rest.isEmpty() && (rest.contains(" ") || !rest.equals(rest.toLowerCase()))) {
                        suffixStart = i + 1;
                        break;
                    }
                }
            }
            if (suffixStart >= 0) {
                return withoutPrefix.substring(suffixStart);
            }

            // Pure IID we couldn't resolve — truncate
            return value.substring(0, Math.min(value.length(), 16)) + "...";
        }

        // Truncate very long values
        if (value.length() > 40) {
            return value.substring(0, 37) + "...";
        }

        return value;
    }

    // ==================== Accessors ====================

    public ViewConfig.InspectMode inspectMode() { return inspectMode; }
    public List<FrameInfo> frames() { return frames; }
    public String iid() { return iid; }
    public String vid() { return vid; }
    public boolean dirty() { return dirty; }

    // ==================== Rendering ====================

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        if (inspectMode == ViewConfig.InspectMode.VERSIONS) {
            renderVersions(out);
        } else {
            renderFrames(out);
        }
    }

    private void renderFrames(SurfaceRenderer out) {
        out.beginBox(Scene.Direction.VERTICAL, List.of("inspect-surface", "inspect-frames"));

        // Identity header
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("inspect-header"));
        out.text("IID: ", List.of("inspect-label"));
        out.text(iid, List.of("inspect-value", "mono"));
        out.endBox();

        out.beginBox(Scene.Direction.HORIZONTAL, List.of("inspect-header"));
        out.text("VID: ", List.of("inspect-label"));
        out.text(vid, List.of("inspect-value", "mono"));
        if (dirty) {
            out.text(" *", List.of("dirty-indicator"));
        }
        out.endBox();

        // Frame listing
        if (frames == null || frames.isEmpty()) {
            out.text("No frames", List.of("muted"));
        } else {
            out.text("Frames (" + frames.size() + ")", List.of("inspect-section-title"));

            for (FrameInfo frame : frames) {
                out.beginBox(Scene.Direction.HORIZONTAL, List.of("inspect-frame-entry"));

                // Predicate name
                out.text(frame.name(), List.of("inspect-frame-name"));

                // Qualifier (if any)
                if (frame.qualifier() != null) {
                    out.text("  " + frame.qualifier(), List.of("inspect-frame-qualifier", "mono", "muted"));
                }

                // Metadata
                if (frame.bindingCount() > 0) {
                    String label = frame.bindingCount() == 1 ? "1 binding" : frame.bindingCount() + " bindings";
                    out.text("  " + label, List.of("muted"));
                }
                if (frame.identity()) {
                    out.text("  [identity]", List.of("badge"));
                }
                if (frame.stream()) {
                    out.text("  [stream]", List.of("badge"));
                }

                out.endBox();
            }
        }

        out.endBox();
    }

    private void renderVersions(SurfaceRenderer out) {
        out.beginBox(Scene.Direction.VERTICAL, List.of("inspect-surface", "inspect-versions"));

        // Current version
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("inspect-header"));
        out.text("VID: ", List.of("inspect-label"));
        out.text(vid, List.of("inspect-value", "mono"));
        if (dirty) {
            out.text(" (uncommitted changes)", List.of("dirty-indicator"));
        }
        out.endBox();

        // Placeholder for version DAG
        out.text("Version history", List.of("inspect-section-title"));
        out.text("(version DAG visualization — future work)", List.of("muted"));

        out.endBox();
    }

    // ==================== Frame Info ====================

    /**
     * Summary information about a single frame for display.
     *
     * @param name         resolved predicate name ("policy", "display-layout")
     * @param qualifier    qualifier text if any (quoted literals, resolved sememes), or null
     * @param bindingCount number of bindings in the frame body
     * @param identity     whether this frame contributes to version identity
     * @param stream       whether this is an append-only stream frame
     */
    public record FrameInfo(
            String name,
            String qualifier,
            int bindingCount,
            boolean identity,
            boolean stream
    ) {}
}
