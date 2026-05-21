package dev.everydaythings.graph.cryptography.vault;

/**
 * Thrown when an operation requiring private-key access is attempted on a locked
 * {@link Vault}.
 *
 * <p>Locking blocks signing and new-event creation (incept/rotate/delegate/revoke).
 * It does <i>not</i> block public-key lookup on the published frame chain — that
 * works regardless of vault state, because the keys live in the graph, not
 * exclusively in the vault. State queries on a locked vault return empty
 * {@link java.util.Optional}s rather than throwing.
 */
public class VaultLockedException extends RuntimeException {
    public VaultLockedException() {
        super("Vault is locked");
    }

    public VaultLockedException(String message) {
        super(message);
    }
}
