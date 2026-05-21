package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.cryptography.Signer;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Fluent sub-builder for a record's bindings, nested inside a {@link BodyBuilder}.
 *
 * <p>Returned by {@link BodyBuilder#record(Signer)}. Inherits the role helpers
 * from {@link DatumBuilder} — they operate on this builder's own bindings list
 * (the record's bindings, not the body's). At parent build time, the record's
 * bindings are augmented with default AGENT (from the signer) and TIME (now)
 * if not explicitly set, the body's signing payload is signed, and the final
 * Record is produced.
 *
 * <p>Calling {@link #record(Signer)} or {@link #build()} forwards to the parent,
 * auto-closing this record's intent. Multi-sig flows chain naturally:
 * {@code .record(alice).record(bob).build()}.
 */
public final class RecordBuilder<P extends BodyBuilder<P, R>, R> extends DatumBuilder<RecordBuilder<P, R>> {

    private final P parent;
    private final Signer signer;

    RecordBuilder(P parent, Signer signer) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    /**
     * Materialize this record's pending state as an intent that the parent's
     * build() will use to produce the actual Record.
     */
    BodyBuilder.RecordIntent toIntent() {
        // Close any open binding sub-builder inside this record builder.
        closeOpenBinding();
        return new BodyBuilder.RecordIntent(signer, new ArrayList<>(bindings));
    }

    /**
     * Open a new record (and close this one). Forwards to the parent's
     * {@link BodyBuilder#record(Signer)} after closing self.
     */
    public RecordBuilder<P, R> record(Signer next) {
        // The parent will see openRecord==this; closeOpenRecord materializes us.
        return parent.record(next);
    }

    /**
     * Build the parent's wrapper (Frame or Manifest). Closes this record first.
     */
    public R build() {
        return parent.build();
    }
}
