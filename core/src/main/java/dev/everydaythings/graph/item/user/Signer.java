package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * The new Signer — an Item that can sign things on its own behalf.
 *
 * <p>Carries a keyring (private keys for signing, encryption, key agreement) and
 * exposes signing methods. Subclasses extend Signer to add specific capabilities;
 * notably, {@link dev.everydaythings.graph.runtime.Librarian} extends Signer to
 * be the runtime context that signs activity, channel heads, and its own
 * manifest versions.
 *
 * <p>Minimal first cut: just identity, inherited from {@link Item}. Signing
 * capability (key vault, sign methods, key history, public-key derivation) will
 * be migrated piece by piece from {@link SignerOld} as they're needed.
 */
public class Signer extends Item {

    /** Canonical key for Signer-the-archetype. */
    public static final String KEY = "cg.archetype:signer";

    public Signer(ItemID iid) {
        super(iid);
    }
}
