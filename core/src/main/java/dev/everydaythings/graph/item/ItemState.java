package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.component.FrameTable;
import lombok.Getter;

import java.util.List;

/**
 * Unified state for an Item version.
 *
 * <p>ItemState wraps a {@link FrameTable} that holds all of an Item's
 * versioned state: frames (components, relations, vocabulary, policy).
 */
@Getter
@Canonical.Canonization
public class ItemState implements Canonical {

    @Canon(order = 0)
    private final FrameTable frames;

    public ItemState() {
        this.frames = new FrameTable();
    }

    public ItemState(FrameTable frames) {
        this.frames = frames != null ? frames : new FrameTable();
    }

    public void setOwner(Item owner) {
        frames.setOwner(owner);
    }

    /** @deprecated Use {@link #frameSnapshot()} */
    @Deprecated
    public List<FrameEntry> componentSnapshot() {
        return frameSnapshot();
    }

    public List<FrameEntry> frameSnapshot() {
        return frames.stream().toList();
    }

    /** @deprecated Use {@link #loadFrames(List)} */
    @Deprecated
    public void loadComponents(List<FrameEntry> entries) {
        loadFrames(entries);
    }

    public void loadFrames(List<FrameEntry> entries) {
        if (entries == null) return;
        for (FrameEntry entry : entries) {
            frames.add(entry);
        }
    }

    /** @deprecated Use {@link #frames()} */
    @Deprecated
    public FrameTable content() {
        return frames;
    }

    public int totalEntries() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    @Override
    public String toString() {
        return "ItemState[frames=" + frames.size() + "]";
    }
}
