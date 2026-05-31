package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.cryptography.SignerHandle;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.SubmitResult;

import java.util.List;
import java.util.Optional;

/**
 * The surface {@link Item} (and item-like substrate-shape code) need from a
 * librarian.  {@link Librarian} is the concrete implementation; items that
 * hold a {@code LibrarianHandle} can persist, fetch, query, dispatch, and
 * register without depending on the full Librarian surface (vault, parley,
 * lifecycle, internal codec lookup).
 *
 * <p>Extends {@link SignerHandle} because a Librarian is itself a Signer
 * (every librarian has signing identity), and items may pass their librarian
 * where a SignerHandle is wanted (e.g., the no-arg {@code commit(bindings)}
 * convenience that uses the bound librarian as signer).
 *
 * <p>Future split: this interface lives in :substrate, Librarian-the-class
 * implements it from runtime.
 */
public interface LibrarianHandle extends SignerHandle {

    // ==================================================================================
    // Persistence
    // ==================================================================================

    /** Persist a datum (body or record); returns its content-address. */
    DatumRef persist(Datum datum);

    // ==================================================================================
    // Direct fetches by content address
    // ==================================================================================

    /** Materialize a frame from its body's DatumRef, if locally available. */
    Optional<Frame> fetchFrame(DatumRef bodyId);

    /** Materialize a manifest from a body's DatumRef, if locally available. */
    Optional<Manifest> fetchManifest(DatumRef bodyId);

    /** Fetch a record by its content-address, if locally available. */
    Optional<Record> fetchRecord(DatumRef datumId);

    // ==================================================================================
    // Identity lookups
    // ==================================================================================

    /** Fetch the canonical Item instance for {@code iid}, if known. */
    Optional<Item> fetchItem(ItemRef iid);

    // ==================================================================================
    // Index queries
    // ==================================================================================

    /** All manifest CIDs whose body is an instance of {@code archetypeIid}. */
    List<DatumRef> manifestCidsForType(ItemRef archetypeIid);

    /** All manifest CIDs for the item identified by {@code itemIid}. */
    List<DatumRef> manifestCidsForItem(ItemRef itemIid);

    /** All body CIDs containing a binding with role {@code role} referencing {@code target}. */
    List<DatumRef> bodyCidsForReferenceBinding(ItemRef role, ItemRef target);

    // ==================================================================================
    // Canonicalization
    // ==================================================================================

    /** Register {@code item} as the canonical Java instance for its IID. */
    void register(Item item);

    // ==================================================================================
    // Dispatch
    // ==================================================================================

    /** Submit a frame to the dispatch chain. */
    SubmitResult submit(Frame frame);

    /** Submit a frame on behalf of {@code orchestrator}. */
    SubmitResult submitFrom(Item orchestrator, Frame frame);

    // ==================================================================================
    // Frame assembly
    // ==================================================================================

    /** Assemble a signed frame from a body, signed by {@code signer}. */
    Frame assembleFrame(Body body, SignerHandle signer);

}
