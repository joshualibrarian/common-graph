package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The new Item base class.
 *
 * <p>Concrete (not abstract) — a bare {@code Item} can be instantiated for items
 * that don't have a specific archetype implementation. Subclasses extend Item to
 * add archetype-specific fields and behavior.
 *
 * <p>Identity-and-bootstrap is in place. Other concerns (manifest state, frame
 * access, field reflection, vocabulary, lifecycle hooks) will be migrated piece
 * by piece from {@link ItemOld} as their requirements become clear.
 */
@Getter
public class Item {

    /** Canonical key for Item-the-concept — the archetype for generic items. */
    public static final String KEY = "cg.sememe:item";

    /** The item's stable cryptographic identity. */
    protected final ItemID iid;

    /**
     * Runtime context. Null for seed/siloed items; settable via {@link #bindLibrarian}
     * for the narrow bootstrap fix-up case.
     */
    protected Librarian librarian;

    /** Seed/siloed item — no librarian context. */
    public Item(ItemID iid) {
        this(iid, null);
    }

    /** Runtime item — bound to a librarian. */
    public Item(ItemID iid, Librarian librarian) {
        this.iid = Objects.requireNonNull(iid, "iid");
        this.librarian = librarian;
    }

    /**
     * Bind this item to a librarian.
     *
     * <p>Used in two narrow cases:
     * <ul>
     *   <li><b>Bootstrap fix-up:</b> seed items loaded before the Librarian existed
     *       get bound after Librarian construction completes.</li>
     *   <li><b>Librarian self-bootstrap:</b> the Librarian item's librarian becomes
     *       {@code this} after the in-memory bootstrap Librarian has done its job.</li>
     * </ul>
     *
     * <p>Not for general use. Most callers should pass librarian via the constructor.
     */
    public void bindLibrarian(Librarian librarian) {
        this.librarian = librarian;
    }

    // ==================================================================================
    // Manifest state
    // ==================================================================================

    /**
     * Currently loaded manifest for this item, or {@code null} if not yet associated
     * with one (fresh creation before any commit, or seeds without persistence).
     *
     * <p>The manifest is the runtime aggregate of body + records that represents
     * this item's state at a specific version. Switching versions ("git checkout"
     * style) is done via {@link #bindManifest(Manifest)}.
     */
    protected Manifest current;

    /**
     * Replace the currently-loaded manifest.
     *
     * <p>Used during hydration (loading from storage), after commit (the librarian
     * advances the channel head and re-binds), and for explicit version switching
     * (analogous to {@code git checkout}).
     */
    public void bindManifest(Manifest manifest) {
        this.current = manifest;
    }

    /**
     * The current version ID — equivalent to the body's CID for {@link #current}.
     *
     * <p>Empty if no manifest is currently loaded.
     */
    public Optional<ContentID> versionId() {
        return current != null ? Optional.of(current.versionId()) : Optional.empty();
    }

    /**
     * Parent version IDs for the current manifest (FOLLOWS bindings).
     *
     * <p>Empty list for inception manifests, multiple entries for merge manifests,
     * or empty if no manifest is currently loaded.
     */
    public List<ContentID> parents() {
        return current != null ? current.parents() : List.of();
    }

    /**
     * Endorsement bindings on the current manifest body.
     *
     * <p>Each endorsement binding's target is the body CID of a frame that this
     * version endorses as part of its content state. Empty if no manifest is
     * currently loaded.
     */
    public List<Binding> endorses() {
        return current != null ? current.endorses() : List.of();
    }

    /**
     * Frames endorsed by the current manifest, materialized from the Library.
     *
     * <p>Walks the manifest's ENDORSES bindings, extracts the body CID from each,
     * and fetches each body via the librarian, wrapping it in a {@link Frame}.
     *
     * <p>Returns an empty stream if no manifest is currently loaded or no librarian
     * is bound.
     *
     * <p>TODO: bindings whose body bytes aren't locally available are silently
     * dropped. Once network resolution is wired, missing endorsements should
     * either trigger a fetch or surface as a distinct "unresolved" signal rather
     * than disappearing.
     */
    public Stream<Frame> endorsedFrames() {
        if (current == null || librarian == null) return Stream.empty();
        return current.endorses().stream()
                .map(b -> ((BindingTarget.RefTarget) b.target()).asCid())
                .flatMap(cid -> librarian.fetchFrame(cid).stream());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item other)) return false;
        return iid.equals(other.iid);
    }

    @Override
    public int hashCode() {
        return iid.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + iid + "]";
    }
}
