package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.Seed;

/**
 * Vault-internal vocabulary — sememes used by the vault's own structural
 * bookkeeping that aren't general-purpose enough to live in the
 * shared crypto-domain vocabularies.
 *
 * <p>Currently a single role: {@link VaultId}, an entry's stable identifier
 * within its owning vault.  When more vault-internal structural concepts
 * accumulate (e.g., backend-routing tags, encryption-at-rest envelope
 * markers) they belong here.
 *
 * <p>Vault-entry archetype sememes ({@code Identity}, {@code Authentication},
 * {@code Conversation}) and the user-facing role/qualifier sememes
 * ({@code USERNAME}, {@code PASSWORD}, {@code SIGNED_PRE_KEY}, etc.) live in
 * their own thematic vocabularies (CredentialVocabulary, IdentityVocabulary,
 * EncryptionVocabulary).  This file only holds vault-implementation-internal
 * concepts.
 */
public final class VaultVocabulary {

    private VaultVocabulary() {}

    /**
     * VAULT_ID — an entry's stable identifier within its owning vault.
     *
     * <p>Independent of the entry's body hash (which changes when bindings
     * change).  When an entry is updated by appending a new version that
     * FOLLOWS the prior version, both versions share the same VAULT_ID.
     * For entries that mutate in place (no version chain), the VAULT_ID is
     * just a stable lookup key.
     *
     * <p>Opaque to callers; minted by the vault when an entry is first
     * created.  Typically a 128-bit random value encoded as the binding's
     * target.  Never leaves the vault's data plane (not a CG-store CID, not
     * a derived hash); identity within the vault only.
     */
    @Seed.Item(key = VaultId.KEY)
    @Seed.Gloss(english =
            "an entry's stable identifier within its owning vault; opaque, "
                    + "vault-minted, stable across version-chained updates")
    public static final class VaultId {
        public static final String KEY = "cg.role:vault-id";
        private VaultId() {}
    }
}
