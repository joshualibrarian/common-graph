package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.HashSet;
import java.util.Set;

/**
 * Walks the archetype chain looking up an item's declared scene.
 *
 * <p>The cascade reads {@code CONFIG[Presentation]} bindings from the
 * <i>records</i> attesting each manifest in the chain.  Record bindings —
 * rather than manifest-body bindings — keep presentation out of the
 * manifest's identity hash: different attestations of the same manifest can
 * carry different default scenes without rotating the VID.  See
 * {@link dev.everydaythings.graph.Seed.RecordBinding} for the seed-time
 * declaration mechanism.
 *
 * <p>The walk follows each manifest's {@code body.head()} upward: an
 * instance's manifest body has the archetype as its head; the archetype's
 * manifest body has its parent meta-archetype as its head; and so on.  The
 * chain terminates at {@link CoreVocabulary.Archetype} — the meta-root which
 * self-references its own IID as its head.  Every archetype declares
 * Archetype as its head directly or through an intermediate meta-archetype,
 * so every walk reaches Archetype if not satisfied earlier.
 *
 * <p>The returned {@link Body} is the <i>declared</i> scene — the resolver
 * and presenter handle Variable substitution, repeat expansion, style
 * cascade, and dimensional unit translation downstream.  The cascade
 * itself does no resolution.
 *
 * <p>The walk terminates at:
 * <ul>
 *   <li><b>A match</b> — return the first {@code CONFIG[Presentation]} target found.</li>
 *   <li><b>Self-referential head</b> — when a manifest body's head equals
 *       the current iid (Archetype's self-typing), the chain root has been
 *       reached and the walk stops.</li>
 *   <li><b>Missing item or manifest</b> — if any link can't be fetched, the
 *       walk stops and throws.</li>
 * </ul>
 *
 * <p>Throws {@link IllegalStateException} when no scene declaration is found
 * anywhere in the chain.  This is a loud failure by design —
 * {@link CoreVocabulary.Archetype} carries a terminal placeholder scene that
 * every walk falls through to.  Reaching this exception means the seed
 * declaration on Archetype was lost or never ran.
 *
 * <p>Signer selection (when a manifest has multiple records from different
 * signers): currently "first record wins."  Slice-2 manifests have one
 * record each (the unsigned bootstrap one); the policy hook will sharpen
 * once multi-signer presentation overrides become a real case.
 */
public final class SceneCascade {

    private SceneCascade() {}

    private static final CompoundKey CONFIG_PRESENTATION = CompoundKey.of(
            ItemRef.iid(CoreVocabulary.Config.KEY),
            new CompoundKey.Sememe(ItemRef.iid(SchemaVocabulary.Presentation.KEY)));

    /**
     * Walk the archetype chain starting at {@code iid} and return the first
     * declared scene found in the cascade.  See class doc for full semantics.
     *
     * @throws IllegalStateException if no scene declaration is found in the chain
     */
    public static Body sceneFor(ItemRef iid, Librarian librarian) {
        // First check the iid's own manifest (instance-level override case),
        // then walk via the body's head to the next archetype, repeating until
        // a self-reference or a match.
        ItemRef current = iid;
        Set<ItemRef> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            Item item = librarian.fetchItem(current).orElse(null);
            if (item == null) break;
            Manifest manifest = item.current();
            if (manifest == null) {
                // Uncommitted instance — fall through to its archetype using
                // the Java archetype() override, which mirrors what the body's
                // head would have been if it had committed.
                ItemRef archetype = item.archetype();
                if (archetype == null || archetype.equals(current)) break;
                current = archetype;
                continue;
            }
            Body found = readPresentation(manifest);
            if (found != null) return found;
            ItemRef nextHead = manifest.body().headRef();
            if (nextHead == null || nextHead.equals(current)) break;
            current = nextHead;
        }
        throw new IllegalStateException(
                "No CONFIG[Presentation] declared in archetype chain for " + iid
                        + " — the cascade should always terminate at Archetype's "
                        + "default scene; check that Archetype's @Seed.RecordBinding "
                        + "is intact and the librarian was bootstrapped.");
    }

    /**
     * Read the first {@code CONFIG[Presentation]} binding's target from the
     * manifest's records.  Returns null when nothing is found (caller
     * continues the cascade walk).
     */
    private static Body readPresentation(Manifest manifest) {
        if (manifest == null) return null;
        for (Record record : manifest.records()) {
            for (Binding b : record.bindings(CONFIG_PRESENTATION)) {
                if (b.target() instanceof Body body) return body;
            }
        }
        return null;
    }
}
