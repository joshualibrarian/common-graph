package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.ref.HashID;

import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for a bare {@link Body} — no signed records attached.  Returned
 * by {@link Body#compose(HashID)}.
 *
 * <p>Used for inline propositional bodies (expressions, nested-target bodies,
 * scene-graph nodes) where the body is data within a larger Datum, not itself
 * an attested artifact.  For signed bodies, use {@code Frame.compose(...)};
 * for identity-bearing item bodies, use {@code Manifest.compose(...)}.
 *
 * <p>This builder inherits {@link BodyBuilder}'s record machinery but rejects
 * any actual record use at build time — bare bodies don't attest. The
 * inheritance is convenience-only, sharing the binding-accumulation surface.
 */
public final class BodyComposer extends BodyBuilder<BodyComposer, Body> {

    private final HashID head;

    BodyComposer(HashID head) {
        this.head = Objects.requireNonNull(head, "head");
    }

    @Override
    protected Body buildBody() {
        return Body.of(head, bindings);
    }

    @Override
    protected Body finishBuild(Body body, List<Record> records) {
        if (!records.isEmpty()) {
            throw new IllegalStateException(
                    "Body.compose() does not produce signed bodies — use Frame.compose() "
                            + "if records are needed");
        }
        return body;
    }
}
