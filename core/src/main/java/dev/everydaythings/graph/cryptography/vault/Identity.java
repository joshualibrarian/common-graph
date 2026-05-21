package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Current;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.KeyAgreement;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Next;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Retained;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.SignedPreKey;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Signing;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.OneTimePreKey;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Identity — the vault entry holding a Signer's long-term cryptographic
 * identity: signing keypair, key-agreement keypair, and the pre-keys that
 * make asynchronous session opening possible.
 *
 * <p>A vault has exactly one root Identity entry (the one whose keys derive
 * the Signer's IID).  Other Identity entries may exist as historical
 * versions linked by FOLLOWS bindings when the vault tracks rotation
 * history, but exactly one is current at any time and is reachable via
 * {@code vault.rootIdentity()}.
 *
 * <p>The entry's {@code theme} is always {@code null} — an Identity entry
 * IS the subject, not about something else.
 *
 * <h2>Binding schema</h2>
 *
 * <pre>
 * (SIGNING, CURRENT)         → SigningKey      # active signing keypair
 * (SIGNING, NEXT)            → SigningKey      # pre-committed next signing keypair
 * (KEY_AGREEMENT, CURRENT)   → KeyAgreementKey # active key-agreement keypair
 * (KEY_AGREEMENT, NEXT)      → KeyAgreementKey # pre-committed next key-agreement keypair
 * (SIGNED_PRE_KEY, CURRENT)  → KeyAgreementKey # current published SPK for X3DH
 * (SIGNED_PRE_KEY, RETAINED) → KeyAgreementKey # retained older SPKs (multiset)
 * (ONE_TIME_PRE_KEY)         → KeyAgreementKey # OTPK pool (multiset)
 * </pre>
 *
 * <p>Beyond the declared fields the entry can carry arbitrary free-form
 * bindings: notes, custom metadata, etc.  Read via {@link #binding} on the
 * inherited {@link VaultEntry} API.
 *
 * <h2>Rotation</h2>
 *
 * <p>Rotation events are KEL frames signed by both the old current key and
 * the just-revealed next key.  After a rotation commits successfully, the
 * vault advances the entry: {@code current} discarded, {@code next}
 * promoted to {@code current}, fresh {@code next} generated.  When the
 * vault is configured for version-chained Identity, the prior version
 * stays reachable via {@code FOLLOWS}.
 */
@Seed.Item(key = Identity.KEY)
@Seed.Gloss(english =
        "the vault entry holding a Signer's long-term cryptographic identity: "
                + "signing keypair, key-agreement keypair, and the pre-keys that "
                + "make asynchronous session opening possible")
public class Identity extends VaultEntry {

    /** Canonical key for the Identity vault-entry archetype. */
    public static final String KEY = "cg.vault:identity";

    // ==================================================================================
    // Identity keypairs (current + next per track)
    // ==================================================================================

    @Seed.Property(role = Signing.KEY, qualifiers = {Current.KEY})
    public SigningKey currentSigning;

    @Seed.Property(role = Signing.KEY, qualifiers = {Next.KEY})
    public SigningKey nextSigning;

    @Seed.Property(role = KeyAgreement.KEY, qualifiers = {Current.KEY})
    public KeyAgreementKey currentKeyAgreement;

    @Seed.Property(role = KeyAgreement.KEY, qualifiers = {Next.KEY})
    public KeyAgreementKey nextKeyAgreement;

    // ==================================================================================
    // Pre-keys for asynchronous X3DH session opening
    // ==================================================================================

    /** The currently-published signed pre-key. */
    @Seed.Property(role = SignedPreKey.KEY, qualifiers = {Current.KEY})
    public KeyAgreementKey currentSignedPreKey;

    /**
     * Retained older signed pre-keys, kept for a holding window after
     * rotation so in-flight bootstraps against them still decrypt.
     * Populated by the vault from {@code (SIGNED_PRE_KEY, RETAINED)}
     * bindings.
     */
    public List<KeyAgreementKey> retainedSignedPreKeys = new ArrayList<>();

    /**
     * The pool of one-time pre-keys.  Each entry is consumed on first use
     * (the X3DH initiator references one in CONSUMED_PRE_KEY; the responder
     * vault destroys the matching private key on bootstrap).  Populated by
     * the vault from {@code (ONE_TIME_PRE_KEY)} bindings.
     */
    public List<KeyAgreementKey> oneTimePreKeys = new ArrayList<>();

    // ==================================================================================
    // Construction
    // ==================================================================================

    /**
     * Construct an Identity entry.  Always themeless — an Identity IS its
     * subject, not about something else.
     */
    public Identity(EntryId id) {
        super(id, null);
    }

    @Override
    public ItemRef archetype() {
        return ItemRef.iid(KEY);
    }
}
