package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.mount.Mount;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * An endorsement of a frame in an item's manifest.
 *
 * <p>This is the manifest's statement: "I endorse this frame body,
 * and here's how I present it." Three fields, nothing more.
 *
 * <p>The frame body itself ({@link FrameBody}) carries the semantic
 * assertion: predicate, theme, and bindings. The bindings carry all
 * data — content CIDs, stream heads, local paths, config, vocabulary.
 * Each binding controls its own identity (body hash contribution)
 * and index flags.
 *
 * <p>The endorsement adds only what's specific to this item's
 * relationship to the frame:
 * <ul>
 *   <li>{@code key} — the semantic address within this item</li>
 *   <li>{@code bodyHash} — which version of the frame body is endorsed</li>
 *   <li>{@code mounts} — how this item presents/positions this frame</li>
 * </ul>
 *
 * <p>Everything else (config, presentation, vocabulary, display info)
 * is either on the frame itself (as bindings) or as separate frames
 * on the item.
 *
 * @see FrameBody
 * @see FrameRecord
 */
@Getter
public final class FrameEndorsement implements Canonical {

    /** Semantic address within the item (e.g., (TITLE), (TEXT, ENGLISH)). */
    @Canon(order = 0)
    private final FrameKey key;

    /** Hash of the endorsed frame body. */
    @Canon(order = 1)
    private final ContentID bodyHash;

    /** How this item presents this frame (filesystem paths, display positions). */
    @Canon(order = 2)
    private final List<Mount> mounts;

    public FrameEndorsement(FrameKey key, ContentID bodyHash, List<Mount> mounts) {
        this.key = Objects.requireNonNull(key, "key");
        this.bodyHash = Objects.requireNonNull(bodyHash, "bodyHash");
        this.mounts = mounts != null ? List.copyOf(mounts) : List.of();
    }

    public FrameEndorsement(FrameKey key, ContentID bodyHash) {
        this(key, bodyHash, List.of());
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameEndorsement() {
        this.key = null;
        this.bodyHash = null;
        this.mounts = null;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create an endorsement from a frame body (computes body hash).
     */
    public static FrameEndorsement of(FrameKey key, FrameBody body, List<Mount> mounts) {
        return new FrameEndorsement(key, body.hash(), mounts);
    }

    /**
     * Create an endorsement from a frame body with no mounts.
     */
    public static FrameEndorsement of(FrameKey key, FrameBody body) {
        return new FrameEndorsement(key, body.hash());
    }

    // ==================================================================================
    // Mount Queries
    // ==================================================================================

    /**
     * Does this endorsement have any path mounts?
     */
    public boolean hasPathMount() {
        return mounts.stream().anyMatch(m -> m instanceof Mount.PathMount);
    }

    /**
     * Get the primary path mount (first path mount), or null if none.
     */
    public Mount.PathMount primaryPathMount() {
        return mounts.stream()
                .filter(m -> m instanceof Mount.PathMount)
                .map(m -> (Mount.PathMount) m)
                .findFirst()
                .orElse(null);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        return "FrameEndorsement{" + key.toCanonicalString()
                + " -> " + bodyHash.displayAtWidth(12) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameEndorsement other)) return false;
        return Objects.equals(key, other.key)
                && Objects.equals(bodyHash, other.bodyHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, bodyHash);
    }
}
