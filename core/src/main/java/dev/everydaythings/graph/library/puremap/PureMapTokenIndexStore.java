package dev.everydaythings.graph.library.puremap;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import dev.everydaythings.graph.library.index.TokenPosting;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Pure-in-memory {@link TokenIndexStore} — minimal no-op-on-write,
 * empty-on-lookup implementation. Suitable for pure-map mode where token
 * lookup isn't typically used; tests that need real token resolution should
 * compose a Library with a byte-backed token store instead.
 *
 * <p>A richer pure-map implementation could scan a live Datum map for text
 * bindings on each lookup; that's a future refinement.
 */
public final class PureMapTokenIndexStore implements TokenIndexStore {

    PureMapTokenIndexStore() {}

    public static PureMapTokenIndexStore create() {
        return new PureMapTokenIndexStore();
    }

    @Override
    public void index(Datum datum, DatumRef datumId) {
        // no-op
    }

    @Override
    public void unindex(Datum datum, DatumRef datumId) {
        // no-op
    }

    @Override
    public Stream<TokenPosting> lookup(String token,
                                       Function<DatumRef, Optional<Datum>> datumResolver) {
        return Stream.empty();
    }

    @Override
    public Stream<TokenPosting> prefix(String tokenPrefix, int limit,
                                       Function<DatumRef, Optional<Datum>> datumResolver) {
        return Stream.empty();
    }

    @Override
    public void close() {
        // no-op: no state to release
    }
}
