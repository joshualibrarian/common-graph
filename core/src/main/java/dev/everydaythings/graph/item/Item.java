package dev.everydaythings.graph.item;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.datum.*;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.FrameDraftMerger;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.ParseContext;
import dev.everydaythings.graph.text.ParseEngine;
import dev.everydaythings.graph.text.ParseParams;
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
 * <p>Identity-and-bootstrap, manifest state, the frame-assembled hook, commit,
 * and the text-pipeline entry points are wired. Other concerns (field reflection
 * for declarative frame fields, lifecycle hooks beyond {@code onFrameAssembled})
 * accrue here piece by piece as their requirements become clear.
 */
@Getter
@Seed.Item(key = dev.everydaythings.graph.item.Item.KEY)
@Seed.Embodies(key = dev.everydaythings.graph.item.Item.KEY)
public class Item {

    /** Canonical key for Item-the-concept — the archetype for generic items. */
    public static final String KEY = "cg.sememe:item";

    /** The IID for Item-the-concept. Subclasses override {@link #archetype()} to return their own. */
    public static final ItemRef IID = ItemRef.fromString(KEY);

    /**
     * The archetype this item is an instance of — the sememe IID that goes in the
     * head of a manifest body produced by {@link #commit}.
     *
     * <p>Subclasses override to return their own archetype IID (Signer, Librarian,
     * application-specific item types). The default returns {@link #IID}.
     */
    public ItemRef archetype() {
        return IID;
    }

    /**
     * The item's stable cryptographic identity, or {@code null} for an anonymous
     * item (no identity at all — see {@link Librarian#anonymous()}).
     *
     * <p>Most items have an iid. Anonymous items are the exception: they exist
     * only in memory, never sign anything, never appear as a binding target, and
     * never participate in identity-keyed lookups. Any operation that requires a
     * stable identity (commit, register, signing payload preparation) must guard
     * with {@code Objects.requireNonNull(iid, ...)} at the boundary.
     */
    protected final ItemRef iid;

    /**
     * Runtime context. Null for seed/siloed items; settable via {@link #bindLibrarian}
     * for the narrow bootstrap fix-up case.
     */
    protected Librarian librarian;

    /** Seed/siloed item — no librarian context. */
    public Item(ItemRef iid) {
        this(iid, null);
    }

    /**
     * Runtime item — bound to a librarian.
     *
     * <p>{@code iid} may be null only for an anonymous item (an item that has
     * no identity and never will). Anonymous items are constructed by the
     * anonymous-Librarian path; they cannot commit, cannot be registered, and
     * cannot be the target of a binding.
     */
    public Item(ItemRef iid, Librarian librarian) {
        this.iid = iid;
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
     * The current version ID — the structural identity of {@link #current}'s body.
     *
     * <p>Empty if no manifest is currently loaded.
     */
    public Optional<DatumRef> versionId() {
        return current != null ? Optional.of(current.versionId()) : Optional.empty();
    }

    /**
     * Parent version IDs for the current manifest (FOLLOWS bindings).
     *
     * <p>Empty list for inception manifests, multiple entries for merge manifests,
     * or empty if no manifest is currently loaded.
     */
    public List<DatumRef> parents() {
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
                .map(Binding::target)
                .filter(t -> t instanceof DatumRef)
                .map(DatumRef.class::cast)
                .flatMap(id -> librarian.fetchFrame(id).stream());
    }

    /**
     * Endorsed frames whose head matches the given predicate.
     *
     * <p>An item often has multiple frames endorsed under the same predicate — e.g.,
     * one {@code Lexeme} frame per (language, POS, feature) combination, or one
     * {@code Gloss} per language. Callers filter the stream further by binding
     * qualifiers when they need a specific instance.
     *
     * <p>For predicates that are typically unique-per-item (Symbol, Fixity,
     * Precedence, Associativity), {@code .findFirst()} on the stream returns the
     * single instance. For predicates with many instances, callers either iterate
     * all matches or apply additional filtering on the binding qualifiers.
     *
     * @param predicateIid the predicate to match against each frame body's head
     * @return frames endorsed by this item's manifest whose head equals {@code predicateIid}
     */
    public Stream<Frame> endorsedFramesByPredicate(ItemRef predicateIid) {
        Objects.requireNonNull(predicateIid, "predicateIid");
        return endorsedFrames()
                .filter(f -> f.body().headRef().iid().equals(predicateIid));
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
        if (iid == null) {
            throw new IllegalStateException("Anonymous item has no identity; cannot commit");
        }
        Objects.requireNonNull(signer, "signer");
        Objects.requireNonNull(bindings, "bindings");

        List<Binding> manifestBindings = new ArrayList<>();
        manifestBindings.add(Binding.ref(Manifest.ITEM_ID, iid));
        if (current != null) {
            manifestBindings.add(new Binding(
                    Manifest.FOLLOWS,
                    current.versionId()));
        }
        // Auto-inject IMPLEMENTATION for non-bare-Item subclasses so future
        // hydration of this item's manifest can dispatch to the right Java class.
        if (this.getClass() != Item.class) {
            manifestBindings.add(Manifest.implementation(this.getClass()));
        }
        manifestBindings.addAll(bindings);

        Body body = Body.of(ItemRef.of(archetype()), manifestBindings);
        librarian.persist(body);

        VarSig signature = signer.sign(HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), signature);
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
        // Anonymous items (null iid) have no shared identity and are equal only
        // to themselves — handled by the `this == o` short-circuit above.
        if (iid == null || other.iid == null) return false;
        return iid.equals(other.iid);
    }

    @Override
    public int hashCode() {
        return iid == null ? System.identityHashCode(this) : iid.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (iid == null ? "anonymous" : iid) + "]";
    }

    // ==================================================================================
    // Evaluate — universal message processing
    // ==================================================================================

    /**
     * Subclass implementation hook for message processing.
     *
     * <p>Items receive bodies as messages and react. The body's head identifies
     * the message type: a predicate-headed body is a frame ("do this action");
     * a {@code ParseContext}-headed body is a parse round; a
     * {@code RenderContext}-headed body is a render request. Subclasses
     * dispatch on the head however they choose — typed switch, annotation
     * table, language-native pattern matching.
     *
     * <p>Return value convention: bare values ({@link Long}, {@code Decimal},
     * {@link String}) for simple scalars where the caller can interpret
     * unambiguously; a {@link Body} when the result is semantically rich
     * (quantities with units, structured replies); {@code null} when the item
     * has no reply.
     *
     * <p><b>Not a public API.</b> This hook is invoked by
     * {@link dev.everydaythings.graph.runtime.stage.ItemStage ItemStage} on
     * behalf of the librarian; external code never calls this method directly.
     * To have an item process a message, callers go through
     * {@code librarian.evaluate(target, body)} (or its eventual equivalent),
     * which applies trust/capability policy and then routes through the Stage.
     * The Stage uses reflection to invoke this hook, so {@code protected}
     * visibility is enforced for Java callers but not for the Stage itself.
     *
     * <p>Polyglot items (Python, Lisp, JS, …) don't override this method —
     * their behavior lives in source bindings on the manifest, and the Stage's
     * polyglot strategy invokes that source directly. Only Java items override.
     *
     * <p>Default implementation returns {@code null}.
     */
    protected Object evaluate(Body body) {
        return null;
    }

    // ==================================================================================
    // Text pipeline — parsing
    // ==================================================================================

    /**
     * Orchestrator entry point — input has arrived at this item's prompt.
     *
     * <p>Delegates to {@link ParseEngine#run}: the standard consensus engine runs with
     * this item as the orchestrator. The engine tokenizes the input, builds an
     * {@link dev.everydaythings.graph.text.AnchorTable} of active participants from
     * the lattice, iterates {@link #parse(ParseContext)} on each participant per
     * round, merges via {@link #merge}, and returns when fixpoint is reached.
     *
     * <p>Subclasses override to customize orchestration entirely (rare). To customize
     * just merge behavior, override {@link #merge} instead — the engine calls it once
     * per round.
     *
     * @param input  raw text from the prompt
     * @param params operational parameters (language stack, mode, verbosity, etc.)
     * @return the final FrameMap after consensus reaches fixpoint
     */
    public FrameMap parse(String input, ParseParams params) {
        return ParseEngine.run(this, input, params);
    }

    /**
     * Participant entry point — contribute this item's view of a parse round.
     *
     * <p>Most items have no opinion and return an empty {@link FrameMap}. Languages,
     * predicate sememes, operator sememes, and structural sememes override this to
     * contribute their parse behavior. The orchestrator collects the deltas across
     * all active participants and merges them.
     *
     * @param ctx the round context (text, current draft, orchestrator reference)
     * @return this item's delta — typically empty
     */
    public FrameMap parse(ParseContext ctx) {
        return FrameMap.empty();
    }

    /**
     * Combine this round's deltas with the prior draft into the next draft.
     *
     * <p>Called by the orchestrator's {@link #parse(String, ParseParams)} loop once per
     * round, after all participants have contributed. The default implementation
     * delegates to {@link FrameDraftMerger#weighted}: per-part weighted reconciliation,
     * highest-weighted proposal wins, locks pass through.
     *
     * <p>Items override to substitute custom merge logic — e.g. domain-specific
     * tie-breaking, additional validation, special handling of certain predicates.
     * Most items use the default.
     *
     * @param priorDraft the running consensus from prior rounds; null in round 1
     * @param deltas     the participant contributions from this round
     * @return the new draft to use as the prior draft of the next round
     */
    public FrameMap merge(FrameMap priorDraft, List<FrameMap> deltas) {
        return FrameDraftMerger.weighted(priorDraft, deltas);
    }

    // ==================================================================================
    // Meta-archetypes (data-only seed declarations)
    // ==================================================================================

}
