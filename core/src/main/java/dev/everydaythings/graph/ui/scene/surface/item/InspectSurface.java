package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
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
 *   <li>List of frames: predicate, key, body hash, binding count, identity flag</li>
 *   <li>Config section per frame (if config bindings exist)</li>
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

        // Collect frame info
        surface.frames = new ArrayList<>();
        for (Frame frame : item.frames()) {
            FrameKey key = frame.frameKey();
            String predicate = frame.type() != null ? frame.type().displayAtWidth(16) : "—";
            String bodyHash = frame.bodyHash() != null ? frame.bodyHash().displayAtWidth(12) : "—";
            int bindingCount = frame.body() != null ? frame.body().frameBindings().size() : 0;
            boolean identity = frame.identity();
            boolean stream = frame.body() != null && frame.body().isStream();

            surface.frames.add(new FrameInfo(
                    key.toCanonicalString(),
                    predicate,
                    bodyHash,
                    bindingCount,
                    identity,
                    stream));
        }

        return surface;
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
                out.beginBox(Scene.Direction.VERTICAL, List.of("inspect-frame-entry"));

                // Frame key + predicate
                out.beginBox(Scene.Direction.HORIZONTAL, List.of("inspect-frame-header"));
                out.text(frame.key(), List.of("inspect-frame-key", "mono"));
                out.text(" → ", List.of("muted"));
                out.text(frame.predicate(), List.of("inspect-frame-predicate"));
                out.endBox();

                // Details line
                out.beginBox(Scene.Direction.HORIZONTAL, List.of("inspect-frame-details"));
                out.text("body=" + frame.bodyHash(), List.of("mono", "muted"));
                out.text(" bindings=" + frame.bindingCount(), List.of("muted"));
                if (frame.identity()) {
                    out.text(" [identity]", List.of("badge"));
                }
                if (frame.stream()) {
                    out.text(" [stream]", List.of("badge"));
                }
                out.endBox();

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
     */
    public record FrameInfo(
            String key,
            String predicate,
            String bodyHash,
            int bindingCount,
            boolean identity,
            boolean stream
    ) {}
}
