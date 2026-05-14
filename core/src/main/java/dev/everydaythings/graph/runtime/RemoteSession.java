package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.identity.vault.Vault;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.network.parley.RemoteConnection;
import lombok.extern.log4j.Log4j2;

/**
 * The remote client embodiment of a {@link Session} — typically the entry point
 * of the frontend process.
 *
 * <h2>What it owns</h2>
 * <ul>
 *   <li>A reference to the user's identity {@link Vault} (persisted on this
 *       device, or accessed via remote key custody). Only touched at session
 *       startup to sign the initial DELEGATION; can be re-locked afterwards.</li>
 *   <li>An ephemeral session keypair (Ed25519) generated at startup. Doubles
 *       as the Noise tunnel keypair via X25519 derivation. Used to sign every
 *       wire frame this session emits. Lives only in memory; dies with the
 *       session.</li>
 *   <li>A {@link Parley} instance — this session's end of the conversation
 *       protocol.</li>
 *   <li>A {@link RemoteConnection} to the remote {@link Librarian} that owns
 *       the Session item.</li>
 * </ul>
 *
 * <h2>Startup sequence (TBD)</h2>
 * <ol>
 *   <li>Generate ephemeral Ed25519 keypair.</li>
 *   <li>Unlock user vault; sign DELEGATION granting the ephemeral key authority
 *       to act as the user, time-bounded.</li>
 *   <li>Publish self-signed INCEPTION (with DELEGATION as evidence).</li>
 *   <li>Establish Noise tunnel to the remote librarian (X25519 derived from
 *       the same Ed25519 keypair).</li>
 *   <li>Parley point-and-grunt over the tunnel; agree on codec.</li>
 *   <li>Exchange HELLO frames; remote librarian acknowledges the session.</li>
 *   <li>Bind to the Session item (existing or newly-minted); start receiving
 *       scene-graph frames; start sending input frames.</li>
 * </ol>
 *
 * <p>This is the same security shape as OAuth — the ephemeral key is the
 * "access token," the DELEGATION is its issuance, the user vault never leaves
 * the device — but cryptographically stronger (key possession, not bearer
 * tokens) and architecturally simpler (one keypair serves tunnel + signing).
 *
 * <h2>The "librarian" field</h2>
 * Inherited as {@link dev.everydaythings.graph.item.Item#librarian}. For a
 * RemoteSession this is typically a thin anonymous local Librarian
 * ({@link Librarian#anonymous()}) acting as a local cache — fetches fall
 * through to the remote librarian via {@link #parley} on cache miss. The
 * <i>real</i> data lives on the remote librarian on the other end of the
 * {@link RemoteConnection}.
 *
 * <p>STUB — structure only; the keypair-generation / DELEGATION / INCEPTION
 * dance, the tunnel establishment, and the parley wiring are all TBD.
 */
@Log4j2
public class RemoteSession extends Session {

    /** The user's identity vault. Touched at startup to sign the DELEGATION. */
    private final Vault userVault;

    /** Ephemeral session vault — holds the Ed25519 keypair generated at startup. */
    private final Vault sessionVault;

    /** This session's Parley instance — its end of the protocol. */
    private final Parley parley;

    /** The connection to the remote librarian that owns the Session item. */
    private final RemoteConnection connection;

    public RemoteSession(ItemID iid,
                         Librarian localCacheLibrarian,
                         Vault userVault,
                         Vault sessionVault,
                         Parley parley,
                         RemoteConnection connection) {
        super(iid, localCacheLibrarian);
        this.userVault = userVault;
        this.sessionVault = sessionVault;
        this.parley = parley;
        this.connection = connection;
    }

    public Vault userVault()       { return userVault; }
    public Vault sessionVault()    { return sessionVault; }
    public Parley parley()         { return parley; }
    public RemoteConnection connection() { return connection; }

    // TODO: static factory method that does the full startup dance
    //       (generate keypair → sign DELEGATION → publish INCEPTION →
    //        establish tunnel → point-and-grunt → HELLO → bind to Session item)
    // TODO: main(String[] args) — the frontend entry point
    // TODO: scene-graph receive path
    // TODO: input frame send path
}
