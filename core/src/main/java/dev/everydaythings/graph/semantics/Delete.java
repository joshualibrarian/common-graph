package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Optional;

/**
 * The abstract concept of deletion, embodied as a runtime item.
 *
 * <p>A DELETE frame asks the librarian to remove an item from local storage.
 * The librarian honors it only when it can verify that the request was authored
 * by ITSELF — i.e., one of the frame's records carries a signature that verifies
 * with this librarian's public key.
 *
 * <p>This is the implicit-authorization rule: "if I created this DELETE, I'm
 * implicitly endorsing it; honor it." DELETE frames signed by other principals
 * are stored as data but NOT auto-honored — they require an explicit endorsement
 * (a record signing the DELETE frame) from this librarian before deletion happens.
 * That endorsement flow is a future round when authentication/trust matrices land.
 *
 * <p>When honored, DELETE removes:
 * <ul>
 *   <li>All manifest body-CIDs for the targeted item</li>
 *   <li>Each manifest's records (via the RECORDS_BY_BODY index)</li>
 *   <li>The item's entry in the librarian's cache</li>
 * </ul>
 *
 * <p>Endorsed frames and other items pointing at this item are NOT cascaded —
 * they may be referenced elsewhere (shared content). Dangling references are
 * the accepted cost; a future GC/sweep can reconcile orphans.
 *
 * <p>The DELETE frame itself remains in storage as audit data — its existence
 * records "this item was deleted by this signer at this time."
 */
@Seed.Item(key = Delete.KEY)
@Seed.Embodies(key = Delete.KEY)
public class Delete extends Item {

    /** Canonical key for the delete sememe. POS-agnostic — this is a unit of meaning. */
    public static final String KEY = "cg.sememe:delete";

    /** The deterministic IID for the delete sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    public Delete(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    @Override
    public void onFrameAssembled(Frame frame) {
        Optional<Binding> themeBinding =
                frame.body().binding(CompoundKey.of(ThematicRole.Theme.IID));
        if (themeBinding.isEmpty()) return;

        ItemID targetIid = extractIid(themeBinding.get().target());
        if (targetIid == null) return;

        // Authorization: only honor DELETEs we ourselves authored. Verify at
        // least one of the frame's records was signed by this librarian's key.
        if (!isAuthorizedByThisLibrarian(frame)) return;

        // Cascade: tear down the item's manifests and their records.
        Library library = librarian.library();
        List<ContentID> manifestCids = library.manifestCidsForItem(targetIid);
        for (ContentID manifestCid : manifestCids) {
            for (ContentID recordCid : library.recordCidsForBody(manifestCid)) {
                library.delete(recordCid);
            }
            library.delete(manifestCid);
        }

        librarian.evict(targetIid);
    }

    /**
     * True if at least one of the frame's records carries a signature verifiable
     * against the librarian's INCEPTED signing keys — i.e., this librarian (per
     * its published KEL) attested at least one record on the frame.
     *
     * <p>Phase 1: consults {@link Librarian#verifySignedAsIdentity}, which derives
     * keys from the librarian's own INCEPTION. When ROTATION lands, the same call
     * walks the KEL chain to determine the currently-committed keys. The result
     * is the same in trivial cases but the conceptual path goes through real
     * key-state, not in-memory shortcuts.
     */
    private boolean isAuthorizedByThisLibrarian(Frame frame) {
        byte[] signedBytes = frame.body().encodeBinary(Canonical.Scope.BODY);
        for (Record record : frame.records()) {
            if (librarian.verifySignedAsIdentity(librarian.iid(), signedBytes, record.varsig())) {
                return true;
            }
        }
        return false;
    }

    private static ItemID extractIid(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) return iid.iid();
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            return ref.asItemId();
        }
        return null;
    }
}
