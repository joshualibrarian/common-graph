package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.Record;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
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

    /** The archetype IID for generic Items. Subclasses override {@link #archetype()}. */
    public static final ItemID ARCHETYPE = ItemID.fromString(KEY);

    /**
     * The archetype this item is an instance of — the sememe IID that goes in the
     * head of a manifest body produced by {@link #commit}.
     *
     * <p>Subclasses override to return their own archetype IID (Signer, Librarian,
     * application-specific item types). The default returns {@link #ARCHETYPE}.
     */
    public ItemID archetype() {
        return ARCHETYPE;
    }

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

    // ==================================================================================
    // Frame-assembled hook (behavior model)
    // ==================================================================================

    /**
     * Hook called by the librarian when a frame referencing this item is assembled.
     *
     * <p>This is the "frame-creation-as-action" model: instead of dispatching to
     * verbs, items react to frames. When {@link Librarian#assembleFrame} publishes
     * a propositional frame, every item referenced in the frame's body bindings
     * (via reference targets) gets a single call to this method.
     *
     * <p>Default implementation is a no-op. Subclasses override to react:
     * a {@code ChessGame} updates its board on a {@code MOVE} frame; an activity
     * log appends an entry on any frame mentioning it; etc.
     *
     * <p>Calls are synchronous and single-threaded. An exception thrown here is
     * caught by the librarian's routing loop and does not prevent other items
     * from being notified. Implementations should still aim to be defensive.
     */
    public void onFrameAssembled(Frame frame) {
        // default: no-op. Override in subclasses.
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

    // ==================================================================================
    // Commit
    // ==================================================================================

    /**
     * Commit a new version of this item using the bound librarian as the signer.
     *
     * @see #commit(Signer, List)
     */
    public Manifest commit(List<Binding> bindings) {
        if (librarian == null) {
            throw new IllegalStateException("Item has no librarian; cannot commit");
        }
        return commit(librarian, bindings);
    }

    /**
     * Commit a new version of this item.
     *
     * <p>Builds a manifest body whose head is this item's {@link #archetype()} and
     * whose bindings include:
     * <ul>
     *   <li>{@code ITEM_ID → this.iid} (always)</li>
     *   <li>{@code FOLLOWS → previous-VID} (if a previous manifest is loaded)</li>
     *   <li>everything in the caller-supplied {@code bindings} list</li>
     * </ul>
     *
     * <p>The body is persisted via the librarian, the signer signs the body's
     * encoded bytes, the resulting record is persisted, and {@link #current} is
     * advanced to the new manifest.
     *
     * <p>The bindings list is "additional" content bindings — ENDORSES, CONFIG,
     * IMPLEMENTATION, etc. ITEM_ID and FOLLOWS are added automatically; callers
     * should not include them.
     *
     * @return the newly-committed Manifest (also bound as {@link #current})
     * @throws IllegalStateException if no librarian is bound
     * @throws IllegalArgumentException if signer is null
     */
    public Manifest commit(Signer signer, List<Binding> bindings) {
        if (librarian == null) {
            throw new IllegalStateException("Item has no librarian; cannot commit");
        }
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(bindings, "bindings");

        List<Binding> manifestBindings = new ArrayList<>();
        manifestBindings.add(Binding.ref(Manifest.ITEM_ID, iid));
        if (current != null) {
            manifestBindings.add(new Binding(
                    Manifest.FOLLOWS,
                    BindingTarget.ref(current.versionId())));
        }
        // Auto-inject IMPLEMENTATION for non-bare-Item subclasses so future
        // hydration of this item's manifest can dispatch to the right Java class.
        if (this.getClass() != Item.class) {
            manifestBindings.add(Manifest.javaImplementation(this.getClass()));
        }
        manifestBindings.addAll(bindings);

        Body body = Body.of(ItemRef.of(archetype()), manifestBindings);
        ContentID bodyCid = librarian.persist(body);

        VarSig signature = signer.sign(body.encodeBinary(Canonical.Scope.BODY));
        Record record = Record.of(FrameRef.of(bodyCid), List.of(), signature);
        librarian.persist(record);

        Manifest committed = Manifest.of(body, List.of(record));
        bindManifest(committed);
        // Once committed, this item is canonical for its IID — register so any
        // future fetchItem returns this same instance.
        librarian.register(this);
        return committed;
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
