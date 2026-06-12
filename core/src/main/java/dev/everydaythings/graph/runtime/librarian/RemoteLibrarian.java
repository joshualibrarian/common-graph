package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.cryptography.SignerHandle;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.cryptography.vault.DelegationConditions;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.network.Endpoint;
import dev.everydaythings.graph.network.noise.NoiseTunnel;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.network.parley.ParleyVocabulary;
import dev.everydaythings.graph.network.parley.RemoteConnection;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.SubmitResult;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Client-side {@link LibrarianHandle} that proxies calls over a Parley
 * connection to a remote {@link Librarian}.  The "client side" of any
 * Parley conversation — used by remote UI sessions and EvalSessions to
 * reach a librarian that lives in a different process (or on a different
 * machine).
 *
 * <h2>What it owns</h2>
 *
 * <ul>
 *   <li>A <b>session vault</b> — the session's own signing + key-agreement
 *       keypair.  Persisted on disk at the session's path
 *       ({@code .session/.vault} by convention).  Used as Noise static and
 *       as the signing identity for frames originating from this session.</li>
 *   <li>A <b>user vault</b> — the User whose authority backs this session.
 *       Touched only at bootstrap to sign the DELEGATION; may be locked
 *       afterwards.  May be {@code null} for reconnection cases where the
 *       session keypair already has a valid DELEGATION in the remote
 *       librarian's records.</li>
 *   <li>A <b>Parley</b> instance and (after {@link #connect}) a
 *       {@link RemoteConnection} carrying the codec-handshake-completed
 *       channel to the remote librarian.</li>
 * </ul>
 *
 * <h2>SignerHandle</h2>
 *
 * <p>{@link #iid()} returns the session's iid; {@link #sign(byte[])}
 * signs with the session's signing-track key.  Items constructed in this
 * process holding RemoteLibrarian as their handle will commit frames
 * signed by the session.
 *
 * <h2>LibrarianHandle — thin-client surface</h2>
 *
 * <p>RemoteLibrarian implements LibrarianHandle because callers that hold
 * an Item with this as its handle need that interface.  But the thin-client
 * model (see [[project_thin_client_model_2026_06_01]]) means most of the
 * LibrarianHandle methods are <i>not used by client code by design</i>:
 *
 * <ul>
 *   <li><b>{@link #submit}</b> — the load-bearing client→server operation.
 *       Fire-and-forget; effects flow back as server pushes through the
 *       configured push handler.  Wired.</li>
 *   <li><b>SignerHandle methods</b> ({@link #iid}, {@link #sign}, {@link #canSign})
 *       — real and used; back the session's signing identity.</li>
 *   <li><b>{@link #fetchFrame}, {@link #fetchItem}, {@link #fetchManifest},
 *       {@link #fetchRecord}, {@link #persist}, {@link #register},
 *       {@link #submitFrom}, {@link #assembleFrame}, manifest-/binding-CID
 *       queries</b> — throw by DESIGN.  A thin client has no graph state to
 *       satisfy these from; it never asks "what is the manifest for item X"
 *       because the server owns all of that.  If an explicit "download from
 *       server to local materialization" use case arises, it gets its own
 *       method (probably a DOWNLOAD predicate), not transparent fetch.</li>
 * </ul>
 *
 * <h2>Bootstrap dance</h2>
 *
 * <p>{@link #connect(Transport, Endpoint)} performs the one-time bring-up:
 *
 * <ol>
 *   <li>Open a {@link Tunnel} via the given Transport.</li>
 *   <li>Run the {@link NoiseTunnel} XX handshake using the session vault's
 *       X25519 static key.</li>
 *   <li>Run the Parley codec handshake.</li>
 *   <li>On first contact (no prior identity-chain frames on the remote):
 *       mint INCEPTION (signing + key-agreement) and DELEGATION (from User
 *       to Session), send their records then bodies.</li>
 *   <li>Send our HELLO; await the librarian's HELLO in return.</li>
 *   <li>The returned future completes once both sides have exchanged HELLOs.</li>
 * </ol>
 *
 * <h2>Where this lives</h2>
 *
 * <p>{@code :core/runtime/librarian} alongside {@link Librarian}.  Sessions
 * ({@code UiSession} / {@code EvalSession}) compose RemoteLibrarian as
 * their LibrarianHandle when their librarian is remote.  Local sessions
 * compose a concrete {@link Librarian} directly.
 */
@Log4j2
public final class RemoteLibrarian implements LibrarianHandle, AutoCloseable {

    private final Vault sessionVault;
    private final Vault userVault;
    private final Consumer<Body> pushHandler;
    private final Parley parley;
    private volatile RemoteConnection connection;

    /**
     * Outgoing-record sequence counter to the connected peer.  Single-peer
     * (one librarian on the other end), so a single AtomicLong suffices.
     * Increments on every signed request record this RemoteLibrarian
     * produces.
     */
    private final AtomicLong outgoingSequence = new AtomicLong(0);

    /**
     * Build a RemoteLibrarian holding the session's signing identity and
     * (optionally) the User vault that will delegate to it.  No network
     * activity until {@link #connect(Transport, Endpoint)} is called.
     *
     * @param sessionVault  session's own vault (signing + key-agreement keys).
     * @param userVault     User vault used at bootstrap to sign DELEGATION; may
     *                      be null for reconnect cases where DELEGATION already
     *                      exists on the remote librarian.
     * @param pushHandler   callback for unsolicited server bodies (scene graphs,
     *                      presence updates, etc.); may be null for headless /
     *                      eval-mode clients that ignore pushes.
     */
    public RemoteLibrarian(Vault sessionVault, Vault userVault,
                           Consumer<Body> pushHandler) {
        this.sessionVault = Objects.requireNonNull(sessionVault, "sessionVault");
        this.userVault = userVault;
        this.pushHandler = pushHandler;
        this.parley = Parley.client(sessionVault.identity(), pushHandler);
    }

    public Vault sessionVault()                  { return sessionVault; }
    public Optional<Vault> userVault()           { return Optional.ofNullable(userVault); }
    public Optional<RemoteConnection> connection() { return Optional.ofNullable(connection); }

    // ==================================================================================
    // Bootstrap — open Tunnel, NoiseTunnel handshake, codec, identity-chain, HELLO
    // ==================================================================================

    /**
     * Open the connection and complete the bootstrap dance.  See class javadoc
     * for the sequence.  The returned future completes when the codec
     * handshake succeeds and our HELLO has been sent; the librarian's HELLO
     * may still be in flight at that point (its arrival flips the dispatcher's
     * peerAgent and admits us fully).
     */
    public CompletableFuture<Void> connect(Transport transport, Endpoint endpoint) {
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(endpoint, "endpoint");

        return transport.connect(endpoint)
                .thenCompose(raw -> NoiseTunnel.handshake(
                        raw, sessionVault, NoiseTunnel.Role.INITIATOR))
                .thenCompose(noise -> parley.connect(noise,
                        ItemRef.iid(Encoding.CgCborV1.KEY),
                        Set.of(ItemRef.iid(Encoding.CgCborV1.KEY))))
                .thenAccept(conn -> {
                    this.connection = conn;
                    bootstrap();
                });
    }

    /**
     * After codec handshake completes: emit the identity chain (first contact
     * only — reconnects skip this) and our HELLO.
     */
    private void bootstrap() {
        ItemRef sessionIid = sessionVault.identity();
        try {
            // First-contact identity chain.  For now, always send (no per-peer
            // history check yet — that grows from the persisted HELLO record).
            if (userVault != null) {
                emitIdentityChain(sessionIid);
            }
            // Send HELLO.  Dispatcher's sendHello marks sentHello so the peer's
            // HELLO reply does not bounce another one back.
            connection.dispatcher().sendHello(sessionIid);
        } catch (Exception e) {
            logger.warn("RemoteLibrarian bootstrap failed: {}", e.toString());
            throw new RuntimeException("bootstrap failed", e);
        }
    }

    /**
     * Mint INCEPTION (signing + key-agreement) on the session vault and
     * DELEGATION from the User to the session, send all three over the wire
     * (records first, then bodies, per [[project_wire_shorthand_2026_06_02]]).
     */
    private void emitIdentityChain(ItemRef sessionIid) {
        ItemRef signingPurpose = ItemRef.iid(IdentityVocabulary.Signing.KEY);
        ItemRef kaPurpose      = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

        Frame signingInception = sessionVault.incept(signingPurpose);
        Frame kaInception      = sessionVault.incept(kaPurpose);
        Frame delegation       = userVault.delegate(
                sessionIid, signingPurpose, DelegationConditions.unlimited());

        sendFrame(signingInception);
        sendFrame(kaInception);
        sendFrame(delegation);
    }

    /**
     * Records-then-body emission, mirroring the dispatcher's incoming
     * convention.  Receivers can pair records to their body once it arrives.
     */
    private void sendFrame(Frame frame) {
        for (Record r : frame.records()) {
            connection.send((Datum) r);
        }
        connection.send((Datum) frame.body());
    }

    /**
     * Build a session-signed envelope record for an outgoing request.
     * Carries SEQUENCE (this RemoteLibrarian's outgoing counter) so the
     * server's responses can RESPONSE_TO our request.
     *
     * <p>The record signs the body's payload (consistent with other CG
     * record-signing patterns).  Body identity stays clean — the record is
     * the conversation envelope around it.
     */
    private Record buildOutgoingRecord(Body body, long sequence) {
        List<Binding> bindings = List.of(
                new Binding(ItemRef.iid(ParleyVocabulary.Sequence.KEY), sequence));
        VarSig sig = sessionVault.sign(HashTree.signingPayload(body));
        return Record.of(DatumRef.of(body.datumId()), bindings, sig);
    }

    // ==================================================================================
    // SignerHandle (the session's signing identity)
    // ==================================================================================

    @Override
    public ItemRef iid() {
        return sessionVault.identity();
    }

    @Override
    public VarSig sign(byte[] message) {
        return sessionVault.sign(message);
    }

    @Override
    public boolean canSign() {
        return sessionVault.canSign();
    }

    // ==================================================================================
    // LibrarianHandle — thin-client surface.
    //
    // RemoteLibrarian is a CLIENT-side handle.  Per the thin-client model
    // (see [[project_thin_client_model_2026_06_01]]), a client has no
    // graph state to satisfy fetches from — there is no local librarian on
    // the client.  The methods below that try to read or persist graph
    // state therefore throw by DESIGN, not because of incomplete wiring.
    //
    // What the client DOES have is the session vault, the session item
    // (materialized to disk at .session/), and the User on disk.  Those are
    // local concerns, not RemoteLibrarian's surface.
    //
    // The load-bearing client→server method is {@link #submit}: fire-and-
    // forget delivery of frames the client wants to assert, with effects
    // flowing back as server pushes (scene updates, etc.).  See class
    // javadoc for the lifecycle.
    //
    // If a real "download this from the server into local materialization"
    // use case arises, it gets its own explicit method (and probably its
    // own DOWNLOAD predicate) — distinct from transparent fetch.
    // ==================================================================================

    private static final String NOT_USED_BY_THIN_CLIENT =
            "Thin client does not have a local graph to satisfy this — see "
                    + "[[project_thin_client_model_2026_06_01]].  If you need a download "
                    + "primitive, it would be its own explicit operation, not transparent fetch.";

    @Override
    public DatumRef persist(Datum datum) {
        throw new UnsupportedOperationException("RemoteLibrarian.persist: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public Optional<Frame> fetchFrame(DatumRef bodyId) {
        throw new UnsupportedOperationException("RemoteLibrarian.fetchFrame: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public Optional<Manifest> fetchManifest(DatumRef bodyId) {
        throw new UnsupportedOperationException("RemoteLibrarian.fetchManifest: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public Optional<Record> fetchRecord(DatumRef datumId) {
        throw new UnsupportedOperationException("RemoteLibrarian.fetchRecord: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public Optional<Item> fetchItem(ItemRef iid) {
        throw new UnsupportedOperationException("RemoteLibrarian.fetchItem: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public List<DatumRef> manifestCidsForType(ItemRef archetypeIid) {
        throw new UnsupportedOperationException("RemoteLibrarian.manifestCidsForType: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public List<DatumRef> manifestCidsForItem(ItemRef itemIid) {
        throw new UnsupportedOperationException("RemoteLibrarian.manifestCidsForItem: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public List<DatumRef> bodyCidsForReferenceBinding(ItemRef role, ItemRef target) {
        throw new UnsupportedOperationException("RemoteLibrarian.bodyCidsForReferenceBinding: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public void register(Item item) {
        throw new UnsupportedOperationException("RemoteLibrarian.register: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public SubmitResult submit(Frame frame) {
        if (connection == null) {
            throw new IllegalStateException(
                    "RemoteLibrarian not connected; call connect(transport, endpoint) first");
        }
        // Fire-and-forget for v1 per [[project_parley_multiplexing_2026_06_02]]:
        // submit ships records-then-body with a session-signed envelope
        // record carrying SEQUENCE.  Any server response effects flow back as
        // pushes through the dispatcher's pushHandler.  SubmitResult is
        // returned empty — callers that need server-effect frames have to
        // observe them on the push side (e.g., subscribe to scene updates).
        long mySeq = outgoingSequence.incrementAndGet();
        try {
            // Emit any records the frame already has (e.g., the original
            // signer's attestation), then our envelope record carrying
            // SEQUENCE, then the body.  Receivers pair all records that
            // arrived before the body to it.
            for (Record r : frame.records()) {
                connection.send(r);
            }
            connection.send(buildOutgoingRecord(frame.body(), mySeq));
            connection.send(frame.body());
        } catch (Exception e) {
            logger.warn("RemoteLibrarian.submit failed for body {}: {}",
                    frame.body().headRef(), e.toString());
            throw new RuntimeException("submit failed", e);
        }
        return SubmitResult.of(frame);
    }

    @Override
    public SubmitResult submitFrom(Item orchestrator, Frame frame) {
        throw new UnsupportedOperationException("RemoteLibrarian.submitFrom: " + NOT_USED_BY_THIN_CLIENT);
    }

    @Override
    public Frame assembleFrame(Body body, SignerHandle signer) {
        throw new UnsupportedOperationException("RemoteLibrarian.assembleFrame: " + NOT_USED_BY_THIN_CLIENT);
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        if (connection != null) {
            try { connection.close(); } catch (RuntimeException ignored) {}
            connection = null;
        }
    }
}
