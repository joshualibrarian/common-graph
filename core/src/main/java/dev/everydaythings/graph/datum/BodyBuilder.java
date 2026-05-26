package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.ref.HashID;

import java.util.Objects;

/**
 * Fluent builder for a bare {@link Body} — no signed records.  Returned by
 * {@link Body#compose(HashID)}.
 *
 * <p>Used for inline propositional bodies (expressions, nested-target
 * bodies, scene-graph nodes) where the body is data within a larger Datum,
 * not itself an attested artifact.  For signed bodies use
 * {@link FrameBuilder} (via {@code Frame.compose(...)}); for identity-bearing
 * item bodies use {@code Manifest.compose(...)}.
 *
 * <p>Extends {@link DatumBuilder} for binding accumulation (role helpers,
 * sub-binding builder).  Does NOT extend {@link AttributedBodyBuilder}
 * because that class adds record machinery (signers, attestations) that
 * bare bodies have no use for.
 */
public final class BodyBuilder extends DatumBuilder<BodyBuilder> {

    private final HashID head;

    BodyBuilder(HashID head) {
        this.head = Objects.requireNonNull(head, "head");
    }

    /** Materialize the accumulated bindings as a {@link Body}. */
    public Body build() {
        closeOpenBinding();
        return Body.of(head, bindings);
    }
}
