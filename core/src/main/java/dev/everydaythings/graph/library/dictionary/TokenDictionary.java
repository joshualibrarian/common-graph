package dev.everydaythings.graph.library.dictionary;

import dev.everydaythings.graph.item.component.FrameBody;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.library.Service;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Index for mapping tokens to item references.
 *
 * <p>Implementations must also implement a ByteStore with {@link Column}.
 * All operations are provided as default methods.
 *
 * <p>TokenDictionary extends {@link Service} for lifecycle management and UI presentation.
 * Service methods are provided by the underlying ByteStore implementation.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class SkipListTokenDictionary implements TokenDictionary, SkipListStore<TokenDictionary.Column> {
 *     @Getter private final SkipListStore.Opened<Column> opened;
 *     // That's it!
 * }
 * }</pre>
 */
public interface TokenDictionary extends Service {

    // ==================================================================================
    // Constants
    // ==================================================================================

    byte NULL_TERMINATOR = 0x00;
    byte SCOPE_GLOBAL = 0x00;
    byte SCOPE_ITEM = 0x01;
    int IID_SIZE = 34;
    int WEIGHT_SCALE = 1000;

    // ==================================================================================
    // ByteStore Access
    // ==================================================================================

    @SuppressWarnings("unchecked")
    private ByteStore<Column> store() {
        return (ByteStore<Column>) this;
    }

    // ==================================================================================
    // Query API
    // ==================================================================================

    /**
     * Look up a token. When scopes are provided, returns postings matching those
     * scopes. When no scopes are provided, returns ALL postings for that token
     * regardless of scope (universal + every language + every item).
     */
    default Stream<Posting> lookup(String token, ItemID... scopes) {
        String normalized = Posting.normalize(token);

        if (scopes == null || scopes.length == 0) {
            // No scope filter — return all postings for this token
            byte[] prefix = tokenOnlyPrefix(normalized);
            return streamPostingsWithPrefix(prefix)
                    .filter(p -> p.token().equals(normalized))
                    .sorted(Comparator.comparing(Posting::weight).reversed());
        }

        if (scopes.length == 1) {
            return lookupInScope(normalized, scopes[0])
                    .sorted(Comparator.comparing(Posting::weight).reversed());
        }

        Stream<Posting> result = Stream.empty();
        for (ItemID scope : scopes) {
            result = Stream.concat(result, lookupInScope(normalized, scope));
        }
        return result.sorted(Comparator.comparing(Posting::weight).reversed());
    }

    private Stream<Posting> lookupInScope(String normalizedToken, ItemID scope) {
        byte[] prefix = tokenScopePrefix(normalizedToken, scope);
        return streamPostingsWithPrefix(prefix);
    }

    /**
     * Prefix search for autocomplete. When scopes are provided, filters to those
     * scopes. When no scopes are provided, returns all matching postings.
     */
    default Stream<Posting> prefix(String tokenPrefix, int limit, ItemID... scopes) {
        String normalized = Posting.normalize(tokenPrefix);
        byte[] prefix = tokenOnlyPrefix(normalized);

        if (scopes == null || scopes.length == 0) {
            // No scope filter — return all postings matching the prefix
            return streamPostingsWithPrefix(prefix)
                    .sorted(Comparator.comparing(Posting::weight).reversed())
                    .limit(limit);
        }

        if (scopes.length == 1) {
            ItemID scope = scopes[0];
            return streamPostingsWithPrefix(prefix)
                    .filter(p -> scopeMatches(p.scope(), scope))
                    .sorted(Comparator.comparing(Posting::weight).reversed())
                    .limit(limit);
        }

        Set<ItemID> scopeSet = new HashSet<>(Arrays.asList(scopes));
        return streamPostingsWithPrefix(prefix)
                .filter(p -> scopeSet.contains(p.scope()))
                .sorted(Comparator.comparing(Posting::weight).reversed())
                .limit(limit);
    }

    // ==================================================================================
    // Write API
    // ==================================================================================

    /**
     * Index a posting.
     */
    default void index(Posting posting, WriteTransaction tx) {
        Objects.requireNonNull(posting, "posting");

        byte[] key = postingKey(posting);
        byte[] value = encodeWeight(posting.weight());

        if (tx != null) {
            store().put(Column.BY_TOKEN, key, value, tx);
        } else {
            store().put(Column.BY_TOKEN, key, value);
        }
    }

    /**
     * Remove a posting.
     */
    default void remove(Posting posting, WriteTransaction tx) {
        Objects.requireNonNull(posting, "posting");

        byte[] key = postingKey(posting);

        if (tx != null) {
            store().delete(Column.BY_TOKEN, key, tx);
        } else {
            store().delete(Column.BY_TOKEN, key);
        }
    }

    /**
     * Remove all given postings.
     */
    default void removeAll(Stream<Posting> postings, WriteTransaction tx) {
        postings.forEach(p -> remove(p, tx));
    }

    // ==================================================================================
    // Bulk Ingestion
    // ==================================================================================

    /**
     * Extract and index postings from a frame body.
     *
     * @param body the frame body to index
     * @param predicateWeightResolver resolves predicate IID → index weight (0 = don't index)
     * @param tx write transaction
     */
    default void indexFromFrameBody(FrameBody body,
                                    java.util.function.Function<ItemID, Float> predicateWeightResolver,
                                    WriteTransaction tx) {
        List<Posting> postings = TokenExtractor.fromFrameBody(body, predicateWeightResolver);
        for (Posting p : postings) {
            index(p, tx);
        }
    }

    /**
     * Extract and index postings from a manifest.
     */
    default void indexFromManifest(Manifest manifest, WriteTransaction tx) {
        List<Posting> postings = TokenExtractor.fromManifest(manifest);
        for (Posting p : postings) {
            index(p, tx);
        }
    }

    // ==================================================================================
    // Transactions & Lifecycle
    // ==================================================================================

    default WriteTransaction beginWriteTransaction() {
        return store().beginTransaction();
    }

    default void runInWriteTransaction(Consumer<WriteTransaction> work) {
        try (WriteTransaction tx = beginWriteTransaction()) {
            work.accept(tx);
            tx.commit();
        }
    }

    default boolean isWritable() {
        return true;
    }

    // close() provided by ByteStore implementation

    // ==================================================================================
    // Key Encoding
    // ==================================================================================

    private byte[] postingKey(Posting posting) {
        byte[] tokenBytes = posting.token().getBytes(StandardCharsets.UTF_8);
        byte[] targetBytes = KeyEncoder.ID.bytes(posting.target());

        if (posting.scope() != null) {
            byte[] scopeBytes = KeyEncoder.ID.bytes(posting.scope());
            return KeyEncoder.cat(
                    tokenBytes,
                    new byte[]{NULL_TERMINATOR, SCOPE_ITEM},
                    scopeBytes,
                    targetBytes
            );
        } else {
            return KeyEncoder.cat(
                    tokenBytes,
                    new byte[]{NULL_TERMINATOR, SCOPE_GLOBAL},
                    targetBytes
            );
        }
    }

    private byte[] tokenScopePrefix(String token, ItemID scope) {
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);

        if (scope != null) {
            byte[] scopeBytes = KeyEncoder.ID.bytes(scope);
            return KeyEncoder.cat(
                    tokenBytes,
                    new byte[]{NULL_TERMINATOR, SCOPE_ITEM},
                    scopeBytes
            );
        } else {
            return KeyEncoder.cat(
                    tokenBytes,
                    new byte[]{NULL_TERMINATOR, SCOPE_GLOBAL}
            );
        }
    }

    private byte[] tokenOnlyPrefix(String tokenPrefix) {
        return tokenPrefix.getBytes(StandardCharsets.UTF_8);
    }

    // ==================================================================================
    // Key Parsing
    // ==================================================================================

    private ParsedKey parseKey(byte[] key) {
        int nullPos = -1;
        for (int i = 0; i < key.length; i++) {
            if (key[i] == NULL_TERMINATOR) {
                nullPos = i;
                break;
            }
        }
        if (nullPos < 0) {
            throw new IllegalArgumentException("Invalid posting key: no null terminator");
        }

        String token = new String(key, 0, nullPos, StandardCharsets.UTF_8);

        int scopeTypePos = nullPos + 1;
        if (scopeTypePos >= key.length) {
            throw new IllegalArgumentException("Invalid posting key: missing scope type");
        }
        byte scopeType = key[scopeTypePos];

        ItemID scope;
        int targetStart;

        if (scopeType == SCOPE_GLOBAL) {
            scope = null;
            targetStart = scopeTypePos + 1;
        } else if (scopeType == SCOPE_ITEM) {
            int scopeStart = scopeTypePos + 1;
            if (scopeStart + IID_SIZE > key.length) {
                throw new IllegalArgumentException("Invalid posting key: scope truncated");
            }
            byte[] scopeBytes = Arrays.copyOfRange(key, scopeStart, scopeStart + IID_SIZE);
            scope = new ItemID(scopeBytes);
            targetStart = scopeStart + IID_SIZE;
        } else {
            throw new IllegalArgumentException("Invalid posting key: unknown scope type " + scopeType);
        }

        if (targetStart + IID_SIZE != key.length) {
            throw new IllegalArgumentException("Invalid posting key: unexpected length");
        }
        byte[] targetBytes = Arrays.copyOfRange(key, targetStart, targetStart + IID_SIZE);
        ItemID target = new ItemID(targetBytes);

        return new ParsedKey(token, scope, target);
    }

    record ParsedKey(String token, ItemID scope, ItemID target) {}

    // ==================================================================================
    // Value Encoding
    // ==================================================================================

    private byte[] encodeWeight(float weight) {
        int scaled = (int) (weight * WEIGHT_SCALE);
        return new byte[] {
                (byte) (scaled >> 24),
                (byte) (scaled >> 16),
                (byte) (scaled >> 8),
                (byte) scaled
        };
    }

    private float decodeWeight(byte[] value) {
        if (value.length < 4) return 1.0f;
        int scaled = ((value[0] & 0xFF) << 24) |
                     ((value[1] & 0xFF) << 16) |
                     ((value[2] & 0xFF) << 8) |
                     (value[3] & 0xFF);
        return scaled / (float) WEIGHT_SCALE;
    }

    // ==================================================================================
    // Iteration
    // ==================================================================================

    private Stream<Posting> streamPostingsWithPrefix(byte[] prefix) {
        List<Posting> results = new ArrayList<>();

        try (var it = store().iterate(Column.BY_TOKEN, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                try {
                    ParsedKey parsed = parseKey(kv.key());
                    float weight = decodeWeight(kv.value());
                    results.add(new Posting(parsed.token(), parsed.scope(), parsed.target(), weight));
                } catch (Exception ignored) {}
            }
        }

        return results.stream();
    }

    private boolean scopeMatches(ItemID postingScope, ItemID queryScope) {
        if (queryScope == null) {
            return postingScope == null;
        }
        return queryScope.equals(postingScope);
    }

    // ==================================================================================
    // Column Schema
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
