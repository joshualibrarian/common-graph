package dev.everydaythings.graph.datum;


import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.language.ThematicRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder shared by Frame, Manifest, and (future) Query — anything whose body
 * is a propositional or archetypal Datum with optional attached records.
 *
 * <p>Adds {@link #record(Signer)} for attaching attestations and {@link #build()}
 * for materializing the final aggregate. The orchestration order is:
 *
 * <ol>
 *   <li>Close any open binding sub-builder, materializing it into the body bindings.</li>
 *   <li>Close any open record sub-builder, materializing it into the record-intent list.</li>
 *   <li>Compute the body (head + bindings).</li>
 *   <li>For each record-intent, sign the body and produce a Record.</li>
 *   <li>Return the final wrapper via {@link #finishBuild(Body, List)}.</li>
 * </ol>
 */
public abstract class BodyBuilder<SELF extends BodyBuilder<SELF, R>, R> extends DatumBuilder<SELF> {

    /** Records pending signature (materialized at build time). */
    protected final List<RecordIntent> recordIntents = new ArrayList<>();

    /** Currently-open record sub-builder, or {@code null}. */
    protected RecordBuilder<SELF, R> openRecord;

    /**
     * Open a record sub-builder with the given signer. Any open binding and any
     * prior open record is closed first.
     */
    public RecordBuilder<SELF, R> record(Signer signer) {
        closeOpenBinding();
        closeOpenRecord();
        openRecord = new RecordBuilder<>(self(), signer);
        return openRecord;
    }

    /** Close any open record sub-builder, registering its intent. Idempotent. */
    void closeOpenRecord() {
        if (openRecord != null) {
            RecordBuilder<SELF, R> rb = openRecord;
            openRecord = null;
            recordIntents.add(rb.toIntent());
        }
    }

    /**
     * Build the final wrapper. Closes any open binding and record sub-builder,
     * computes the body, materializes records against the body's signing payload,
     * and delegates to {@link #finishBuild}.
     */
    public R build() {
        closeOpenBinding();
        closeOpenRecord();

        Body body = buildBody();
        List<Record> records = new ArrayList<>(recordIntents.size());
        for (RecordIntent intent : recordIntents) {
            records.add(intent.materialize(body));
        }
        return finishBuild(body, records);
    }

    /** Subclass hook: produce the body from the accumulated bindings. */
    protected abstract Body buildBody();

    /** Subclass hook: wrap body + records into the concrete return type. */
    protected abstract R finishBuild(Body body, List<Record> records);

    // ==================================================================================
    // RecordIntent — lazy holder for a signer + record-level bindings
    // ==================================================================================

    /**
     * A pending record. Holds the signer and the bindings the user wants on the
     * record; materializes against a Body at build time to produce the final
     * signed Record.
     */
    static final class RecordIntent {
        private final Signer signer;
        private final List<Binding> bindings;

        RecordIntent(Signer signer, List<Binding> bindings) {
            this.signer = signer;
            this.bindings = bindings;
        }

        Record materialize(Body body) {
            List<Binding> finalBindings = new ArrayList<>(bindings);

            // Auto-add AGENT if not explicitly set.
            if (!hasRole(finalBindings, ItemRef.iid(ThematicRole.Agent.KEY))) {
                finalBindings.add(Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), signer.iid()));
            }
            // Auto-add TIME if not explicitly set.
            if (!hasRole(finalBindings, ItemRef.iid(ThematicRole.Time.KEY))) {
                finalBindings.add(new Binding(
                        ItemRef.iid(ThematicRole.Time.KEY),
                        Instant.now()));
            }

            VarSig sig = signer.sign(HashTree.signingPayload(body));
            return Record.of(DatumRef.of(body.datumId()), finalBindings, sig);
        }

        private static boolean hasRole(List<Binding> bindings, ItemRef role) {
            for (Binding b : bindings) {
                if (b.role().equals(role) && b.qualifiers().isEmpty()) return true;
            }
            return false;
        }
    }
}
