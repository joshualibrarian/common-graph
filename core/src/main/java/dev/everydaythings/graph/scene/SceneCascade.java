package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.HashSet;
import java.util.Set;

/**
 * Walks the archetype chain looking up an item's declared scene.
 *
 * <p>The cascade reads {@link SceneVocabulary.Scene Scene} role bindings from
 * the <i>records</i> attesting each manifest in the chain.  Record bindings —
 * rather than manifest-body bindings — keep presentation out of the
 * manifest's identity hash: different attestations of the same manifest can
 * carry different default scenes without rotating the VID.  See
 * {@link Scene} for the seed-time declaration mechanism.
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
 *   <li><b>A match</b> — return the first {@code Scene} role target found.</li>
 *   <li><b>Self-referential head</b> — when a manifest body's head equals
 *       the current iid (Archetype's self-typing), the chain root has been
 *       reached and the walk stops.</li>
 *   <li><b>Missing item or manifest</b> — if any link can't be fetched, the
 *       walk stops and throws.</li>
 * </ul>
 *
 * <p>Throws {@link IllegalStateException} when no scene declaration is found
 * anywhere in the chain.  This is a loud failure by design — the root
 * Archetype carries a terminal placeholder scene that every walk falls
 * through to.  Reaching this exception means the seed declaration on
 * Archetype was lost or never ran.
 *
 * <p>Signer selection (when a manifest has multiple records from different
 * signers): currently "first record wins."  Slice-2 manifests have one
 * record each (the unsigned bootstrap one); the policy hook will sharpen
 * once multi-signer presentation overrides become a real case.
 */
public final class SceneCascade {

    private SceneCascade() {}

    private static final ItemRef SCENE_ROLE = ItemRef.iid(SceneVocabulary.Scene.KEY);

    /**
     * Walk the archetype chain starting at {@code iid} and return the first
     * declared default Scene found in the cascade.  Convenience for the
     * no-qualifier (main / primary presentation) case; equivalent to calling
     * {@link #sceneFor(ItemRef, Librarian, String...)} with no qualifier
     * keys.  See class doc for full semantics.
     *
     * @throws IllegalStateException if no scene declaration is found in the chain
     */
    public static Body sceneFor(ItemRef iid, Librarian librarian) {
        return sceneFor(iid, librarian, new String[0]);
    }

    /**
     * Walk the archetype chain starting at {@code iid} and return the first
     * {@code Scene[qualifiers...]} binding found in the cascade.  Qualifiers
     * select alternate presentations: {@link SceneVocabulary.Handle Handle}
     * for the compact form, {@link SceneVocabulary.Aura Aura} for the
     * per-item overlay framework, etc.  Empty qualifier list is the default
     * Scene (item's primary presentation).
     *
     * <p>The cascade terminates at the same conditions as the default-Scene
     * cascade: a match, a self-referential manifest head, or a missing
     * item / manifest.  Throws when nothing matches anywhere in the chain
     * — the root {@code Archetype} carries terminal defaults for the
     * common qualifier sets (default, Handle, Aura) so every walk for those
     * always lands somewhere.
     *
     * @throws IllegalStateException if no matching binding is found in the chain
     */
    public static Body sceneFor(ItemRef iid, Librarian librarian, String... qualifierKeys) {
        CompoundKey ownKey = compoundKey(SCENE_ROLE, qualifierKeys);
        CompoundKey templateKey = compoundKey(SchemaRef.iid(SceneVocabulary.Scene.KEY), qualifierKeys);
        ItemRef current = iid;
        boolean atStartingIid = true;
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
                atStartingIid = false;
                continue;
            }
            // At the starting iid: an own-Scene (concrete, ItemRef-keyed)
            // binding represents a per-instance render override; check it
            // first.  At higher levels, own-Scene is irrelevant (it'd
            // describe how to render the archetype as itself, not how to
            // render instances of it).
            if (atStartingIid) {
                Body own = readPresentation(manifest, ownKey);
                if (own != null) return own;
            }
            // At every level: a schema-Scene (SchemaRef-keyed) binding is
            // the template-for-instances declaration we want.  Most-
            // specific level wins.
            Body template = readPresentation(manifest, templateKey);
            if (template != null) return template;

            ItemRef nextHead = manifest.body().headRef();
            if (nextHead == null || nextHead.equals(current)) break;
            current = nextHead;
            atStartingIid = false;
        }
        throw new IllegalStateException(
                "No " + describe(qualifierKeys) + " binding declared in archetype chain for " + iid
                        + " — the cascade should always terminate at the root "
                        + "Archetype's default; check that Archetype carries a "
                        + "@Scene declaration (schemaRole = Scene.KEY) with the "
                        + "matching qualifier and the librarian was bootstrapped.");
    }

    /**
     * Read the matching Scene binding's target from the manifest's records.
     * Throws when a single record carries more than one Scene binding for
     * the same compound key — that's an authoring error (only one Scene
     * per qualifier set is meaningful; multiples would silently lose all
     * but the first).  Returns null when nothing is found (caller continues
     * the cascade walk).
     */
    private static Body readPresentation(Manifest manifest, CompoundKey key) {
        if (manifest == null) return null;
        Body found = null;
        for (Record record : manifest.records()) {
            for (Binding b : record.bindings(key)) {
                if (b.target() instanceof Body body) {
                    if (found != null) {
                        throw new IllegalStateException(
                                "Multiple Scene bindings for the same qualifier set "
                                        + key + " on manifest "
                                        + manifest.itemId()
                                        + ".  At most one Scene per qualifier set is "
                                        + "meaningful; check the seed declaration for "
                                        + "duplicate @Scene.* annotations with the "
                                        + "same qualifiers.");
                    }
                    found = body;
                }
            }
        }
        return found;
    }

    /** Build the CompoundKey for a Scene-role lookup with the given role HashID and qualifier sememe keys. */
    private static CompoundKey compoundKey(HashID roleHashId, String[] qualifierKeys) {
        if (qualifierKeys == null || qualifierKeys.length == 0) {
            return CompoundKey.of(roleHashId);
        }
        Object[] qualifiers = new Object[qualifierKeys.length];
        for (int i = 0; i < qualifierKeys.length; i++) {
            qualifiers[i] = ItemRef.fromString(qualifierKeys[i]);
        }
        return CompoundKey.of(roleHashId, qualifiers);
    }

    private static String describe(String[] qualifierKeys) {
        if (qualifierKeys == null || qualifierKeys.length == 0) return "Scene / !Scene";
        return "Scene[" + String.join(", ", qualifierKeys) + "] / !Scene[" + String.join(", ", qualifierKeys) + "]";
    }
}
