package dev.everydaythings.graph.library.index;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.id.DatumRef;

import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

import java.text.Normalizer;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Token → ranked-Postings lookup. Domain-shaped — methods speak in Datums and
 * Postings, not bytes. Implementations may be byte-backed
 * ({@link TokenIndexByteStore} family) or store-aware (e.g., a pure-map impl
 * walking live Datums on demand).
 *
 * <p>The store walks a Datum's text-typed bindings on {@link #index} and writes
 * the appropriate posting entries. On {@link #lookup}, a datum resolver
 * function ({@code DatumRef → Optional<Datum>}) is supplied by the caller so
 * that rich Postings can be assembled from the indexed entries without
 * coupling this store to a particular DataStore impl.
 *
 * @see TokenIndexByteStore
 */
public interface TokenIndexStore extends AutoCloseable {

    /** Weight is stored as fixed-point: actual = stored / WEIGHT_SCALE. */
    int WEIGHT_SCALE = 1000;

    /** Decimal scale corresponding to {@link #WEIGHT_SCALE} (10^3). */
    int WEIGHT_SCALE_DIGITS = 3;

    // ==================================================================================
    // Write API
    // ==================================================================================

    /**
     * Walk the Datum's text-typed bindings and index each one for token lookup.
     * The source-identity stored with each entry is {@code datumId} so callers
     * can resolve back to the originating Datum via a resolver passed to
     * {@link #lookup}.
     */
    void index(Datum datum, DatumRef datumId);

    /** Reverse of {@link #index} — remove the entries this Datum's indexing wrote. */
    void unindex(Datum datum, DatumRef datumId);

    // ==================================================================================
    // Query API
    // ==================================================================================

    /**
     * Look up postings for an exact token. Returns ranked Postings ordered by
     * descending weight. {@code datumResolver} fetches the source Datum for
     * each indexed entry so a rich Posting can be assembled.
     */
    Stream<TokenPosting> lookup(String token,
                                Function<DatumRef, Optional<Datum>> datumResolver);

    /** Prefix search for autocomplete. Returns up to {@code limit} ranked Postings. */
    Stream<TokenPosting> prefix(String tokenPrefix, int limit,
                                Function<DatumRef, Optional<Datum>> datumResolver);

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /** Normalize a token: NFC unicode, lowercase, trim, collapse whitespace. */
    static String normalize(String token) {
        if (token == null) return null;
        String normalized = Normalizer.normalize(token, Normalizer.Form.NFC);
        return normalized.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    // ==================================================================================
    // Column schema (for byte-backed backends)
    // ==================================================================================

    @Getter
    enum Column implements ColumnSchema {
        DEFAULT("default", null, null, KeyEncoder.RAW),
        BY_TOKEN("token.index", null, 10, KeyEncoder.RAW);

        private final String schemaName;
        private final Integer prefixLen;
        private final Integer bloomBits;
        private final KeyEncoder[] keyComposition;

        Column(String schemaName, Integer prefixLen, Integer bloomBits, KeyEncoder... keyComposition) {
            this.schemaName = schemaName;
            this.prefixLen = prefixLen;
            this.bloomBits = bloomBits;
            this.keyComposition = keyComposition;
        }
    }
}
