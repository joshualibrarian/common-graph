package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Optional;

/**
 * Server-side concrete {@link ParleyDispatcher}: incoming bodies become
 * frames submitted to a local {@link Librarian}; response frames are wrapped
 * with SEQUENCE + RESPONSE_TO records and sent back; bare-ref / bare-text
 * shorthands resolve via the librarian's fetch / lookup methods.
 *
 * <p>This is the dispatcher used by {@link Parley#listen} / {@link Parley#accept}
 * and by any role that has a real local Librarian to serve.  For the
 * client-side dispatcher (RemoteLibrarian's end of a Parley conversation),
 * see {@link ClientParleyDispatcher}.
 */
@Log4j2
public final class ServerParleyDispatcher extends ParleyDispatcher {

    private final Librarian librarian;

    public ServerParleyDispatcher(Librarian librarian, RemoteConnection connection) {
        super(connection);
        this.librarian = librarian;
    }

    @Override
    protected ItemRef ourAgent() {
        return librarian.iid();
    }

    @Override
    protected void onPeerAdmitted(Body helloBody, ItemRef peerIid) {
        // Persist the HELLO as a normal frame.  It is the durable record
        // that "at time T, this peer claimed this identity with these
        // proofs."  Future reconnects look this up to skip identity-chain
        // resends (optimistic-minimal-send).
        try {
            librarian.submit(Frame.of(helloBody));
        } catch (Exception e) {
            logger.warn("Persisting peer HELLO failed for AGENT={}: {}", peerIid, e.toString());
        }
    }

    @Override
    protected void observeIncomingRecord(Record record, ItemRef peerIid) {
        Long seq = extractSequence(List.of(record));
        if (seq != null) {
            librarian.peerSequences().observeIncoming(peerIid, seq);
        }
    }

    // ==================================================================================
    // Body / fetch / lookup semantics
    // ==================================================================================

    @Override
    protected void onBody(Body body, List<Record> pairedRecords) {
        // Request's SEQUENCE (if any) becomes RESPONSE_TO on our responses.
        Long requestSequence = extractSequence(pairedRecords);
        try {
            SubmitResult result = librarian.submit(Frame.of(body, pairedRecords));
            for (Frame response : result.responses()) {
                sendResponseBody(response.body(), requestSequence);
            }
        } catch (Exception e) {
            logger.warn("Submit failed for body {}: {}", body.headRef(), e.toString());
        }
    }

    @Override
    protected void onRefValue(HashID ref) {
        logger.debug("Parley onRef (fetch): {}", ref);
        // Bare-ref fetches arrive without a record; no request SEQUENCE to
        // echo as RESPONSE_TO.  Responses go bare — receiver correlates by
        // out-of-band knowledge (it knows it just sent this ref).
        Long requestSeq = null;
        try {
            if (ref instanceof DatumRef did) {
                Optional<Frame> frame = librarian.fetchFrame(did);
                if (frame.isPresent()) {
                    sendFrame(frame.get());
                    return;
                }
                Optional<Record> record = librarian.fetchRecord(did);
                if (record.isPresent()) {
                    connection.send(record.get());
                    return;
                }
                sendNotFound(ref, requestSeq);
                return;
            }
            if (ref instanceof ItemRef iid) {
                sendNotFound(ref, requestSeq);
                return;
            }
            if (ref instanceof ContentRef cid) {
                Optional<byte[]> bytes = librarian.fetch(cid);
                if (bytes.isEmpty()) {
                    sendNotFound(ref, requestSeq);
                    return;
                }
                logger.warn("Parley content fetch hit but byte delivery not wired yet: {}", cid);
                sendNotFound(ref, requestSeq);
                return;
            }
            // TypeRef / SchemaRef are not fetch targets.
            sendNotFound(ref, requestSeq);
        } catch (Exception e) {
            logger.warn("Fetch failed for ref {}: {}", ref, e.toString());
        }
    }

    @Override
    protected void onTextValue(String text) {
        logger.debug("Parley onText (lookup): {}", text);
        Long requestSeq = null;
        try {
            List<Frame> responses = librarian.lookupAsFrames(text);
            if (responses.isEmpty()) {
                sendNotFound(text, requestSeq);
                return;
            }
            for (Frame response : responses) {
                sendResponseBody(response.body(), requestSeq);
            }
        } catch (Exception e) {
            logger.warn("Token lookup failed for \"{}\": {}", text, e.toString());
        }
    }

    // ==================================================================================
    // Wire-out helpers
    // ==================================================================================

    /**
     * Send a Frame on the wire as records-then-body, per the Parley records-
     * precede-bodies convention.  Receivers pair the records to the body by
     * DatumRef once the body arrives.
     */
    private void sendFrame(Frame frame) {
        for (Record record : frame.records()) {
            connection.send(record);
        }
        connection.send(frame.body());
    }

    /**
     * Send a body as a response to a known request sequence.  If the peer is
     * known (admission complete) and we have a request sequence, wrap the
     * body in a fresh librarian-signed record carrying SEQUENCE + RESPONSE_TO.
     * Otherwise send the body bare.
     */
    private void sendResponseBody(Body body, Long requestSequence) {
        ItemRef peer = peerAgent().orElse(null);
        if (peer != null && requestSequence != null) {
            Record wrapper = buildResponseRecord(body, requestSequence, peer);
            connection.send(wrapper);
        }
        connection.send(body);
    }

    private void sendNotFound(HashID missingRef, Long requestSequence) {
        Body body = Frame.compose(ItemRef.iid(LibrarianVocabulary.NotFound.KEY))
                .with(ItemRef.iid(ThematicRole.Theme.KEY), missingRef)
                .build()
                .body();
        sendResponseBody(body, requestSequence);
    }

    private void sendNotFound(String missingText, Long requestSequence) {
        Body body = Frame.compose(ItemRef.iid(LibrarianVocabulary.NotFound.KEY))
                .with(ItemRef.iid(ThematicRole.Theme.KEY), missingText)
                .build()
                .body();
        sendResponseBody(body, requestSequence);
    }

    /**
     * Build a librarian-signed wrapper record for an outgoing response body.
     * Carries SEQUENCE (our next outgoing to this peer) and RESPONSE_TO
     * (the request's sequence).  Per [[project_parley_multiplexing_2026_06_02]].
     */
    private Record buildResponseRecord(Body body, long requestSequence, ItemRef peer) {
        long ourSequence = librarian.peerSequences().nextOutgoingFor(peer);
        List<Binding> bindings = List.of(
                new Binding(ItemRef.iid(ParleyVocabulary.Sequence.KEY), ourSequence),
                new Binding(ItemRef.iid(ParleyVocabulary.ResponseTo.KEY), requestSequence));
        VarSig sig = librarian.sign(HashTree.signingPayload(body));
        return Record.of(DatumRef.of(body.datumId()), bindings, sig);
    }
}
