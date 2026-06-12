package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.cryptography.vault.JwksFileVault;
import dev.everydaythings.graph.cryptography.vault.Vault;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads or mints the on-disk artifacts of a session at a resolved location.
 *
 * <p>A session's materialization root (as resolved by {@link SessionResolver})
 * contains, by convention:
 *
 * <ul>
 *   <li>{@code .item/} — item metadata (iid, head, codec, objects).  Marker
 *       that this directory is a materialized session, per
 *       [[project_materialization_pattern_2026_06_05]].</li>
 *   <li>{@code .vault/} — the session's signing + key-agreement vault as
 *       {@link JwksFileVault} JWKS storage.</li>
 * </ul>
 *
 * <p>This helper handles the load-vs-mint decision and the filesystem IO that
 * goes with it.  It does NOT bootstrap the session against a librarian (that
 * is RemoteLibrarian.connect's job) and does NOT touch the user vault — the
 * caller supplies that when needed for delegation during minting.
 *
 * <p>For v1 this only mints the vault; the {@code .item/} metadata is left
 * for the session-item materialization machinery to populate when it lands.
 * The directory existing with a {@code .vault/} inside is sufficient to mark
 * "session here" for resolution purposes.
 */
@Log4j2
public final class SessionStore {

    /** Vault subdirectory name inside the session materialization root. */
    public static final String VAULT_DIR_NAME = ".vault";

    /** Item-metadata subdirectory name (created lazily; not used by this helper yet). */
    public static final String ITEM_DIR_NAME = ".item";

    private SessionStore() {}

    /**
     * Load the session vault at the resolved location.  Throws if the
     * materialization root does not have a vault yet — caller should check
     * {@link Resolved#hasVault()} first or call {@link #mintSessionVault}.
     */
    public static Vault loadSessionVault(SessionResolver.Resolved location) {
        Path vaultDir = location.path().resolve(VAULT_DIR_NAME);
        if (!Files.isDirectory(vaultDir)) {
            throw new IllegalStateException(
                    "No session vault at " + vaultDir + " (mint first)");
        }
        return JwksFileVault.load(vaultDir);
    }

    /**
     * Mint a fresh session vault at the resolved location, creating the
     * materialization root and the {@code .vault/} subdirectory.  Throws if
     * a vault already exists at the path — callers should check with
     * {@link #hasVault(SessionResolver.Resolved)} first.
     *
     * <p>The minted vault has signing + key-agreement keypairs locally; it
     * does NOT yet have a DELEGATION from a User vault.  The caller (typically
     * RemoteLibrarian's bootstrap dance) signs the DELEGATION when connecting.
     */
    public static Vault mintSessionVault(SessionResolver.Resolved location) {
        Path root = location.path();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to create session materialization root at " + root, e);
        }
        Path vaultDir = root.resolve(VAULT_DIR_NAME);
        if (Files.exists(vaultDir.resolve(JwksFileVault.KEYS_FILE_NAME))) {
            throw new IllegalStateException(
                    "Session vault already exists at " + vaultDir + " (use load instead)");
        }
        logger.info("Minting fresh session vault at {}", vaultDir);
        return JwksFileVault.create(vaultDir);
    }

    /**
     * Convenience: load if a vault is present, mint if not.  Common entry
     * point for tools that want either-or behavior with the default
     * auto-mint policy (per [[project_session_workspace_model_2026_06_01]]).
     */
    public static Vault loadOrMintSessionVault(SessionResolver.Resolved location) {
        if (hasVault(location)) {
            return loadSessionVault(location);
        }
        return mintSessionVault(location);
    }

    /** True if a session vault already exists at this resolved location. */
    public static boolean hasVault(SessionResolver.Resolved location) {
        Path keysFile = location.path()
                .resolve(VAULT_DIR_NAME)
                .resolve(JwksFileVault.KEYS_FILE_NAME);
        return Files.isRegularFile(keysFile);
    }

    /**
     * Helper carrying the same information {@link SessionResolver.Resolved}
     * does plus the vault-presence probe, for callers that want to make
     * load-vs-mint decisions without a separate filesystem call.
     */
    public static final class Resolved {
        private final SessionResolver.Resolved location;
        private final boolean vaultPresent;

        Resolved(SessionResolver.Resolved location) {
            this.location = location;
            this.vaultPresent = SessionStore.hasVault(location);
        }

        public SessionResolver.Resolved location() { return location; }
        public boolean hasVault()                  { return vaultPresent; }
    }

    /** Wrap a {@link SessionResolver.Resolved} with vault-presence info. */
    public static Resolved probe(SessionResolver.Resolved location) {
        return new Resolved(location);
    }
}
