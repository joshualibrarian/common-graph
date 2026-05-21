package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.ConversationState;
import dev.everydaythings.graph.ref.ItemRef;

/**
 * Conversation — a vault entry holding the serialized Double-Ratchet state
 * for an active session with a specific peer.
 *
 * <p>One entry per peer.  The entry's {@code theme} is the peer's IID.
 *
 * <h2>Binding schema</h2>
 *
 * <pre>
 * (CONVERSATION_STATE) → byte[]     # DR snapshot: root key, chain keys,
 *                                    # ratchet keypair, counters, skipped-key map
 * </pre>
 *
 * <p>Mutates in place — the DR snapshot is overwritten on every message
 * (each send/receive advances the ratchet).  Versioning via FOLLOWS is not
 * useful here: DR's whole point is forward secrecy and key destruction,
 * so historical states would defeat the protocol.
 *
 * <p>Software-state by convention.  The serialized snapshot can be held in
 * a software-encrypted file even when the rest of the vault is
 * hardware-protected — the threat-model mismatch (DR sessions are ephemeral
 * and forward-secret; hardware backs long-term identity) makes the
 * performance cost of token-backed storage unfavorable.  See
 * {@link dev.everydaythings.graph.cryptography.EncryptionVocabulary.ConversationState
 * ConversationState} for the full reasoning.
 */
@Seed.Item(key = Conversation.KEY)
@Seed.Gloss(english =
        "the serialized Double-Ratchet state for an active session with a peer; "
                + "mutates in place on every message")
public class Conversation extends VaultEntry {

    /** Canonical key for the Conversation vault-entry archetype. */
    public static final String KEY = "cg.vault:conversation";

    /**
     * The Double-Ratchet session state — opaque snapshot bytes produced by
     * {@code DoubleRatchet.snapshot()} and consumed by
     * {@code DoubleRatchet.restoreFrom(bytes)}.
     */
    @Seed.Property(role = ConversationState.KEY)
    public byte[] state;

    /**
     * Construct a Conversation entry.
     *
     * @param id   vault-assigned stable identifier
     * @param peer the peer's IID — the other party in this session
     */
    public Conversation(EntryId id, ItemRef peer) {
        super(id, peer);
    }

    /** The peer's IID — what this conversation is with. */
    public ItemRef peer() {
        return theme;
    }

    @Override
    public ItemRef archetype() {
        return ItemRef.iid(KEY);
    }
}
