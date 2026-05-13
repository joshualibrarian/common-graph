package dev.everydaythings.graph.library_old.dictionary;

import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;

import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Index for mapping tokens to item references.
 *
 * <p>Storage format:
 * <pre>
 *   Key:   [token_utf8][0x00][body_hash_multihash][binding_index_2bytes]
 *   Value: [weight_4bytes]
 * </pre>
 *
 * <p>The body_hash is a self-delimiting multihash (algorithm + length + digest),
 * so its length is determined by the multihash header rather than fixed.
 *
 * <p>The body hash is the {@link ContentID} of the source {@link FrameBodyOld}.
 * The binding index identifies which binding within the body produced this token.
 * At query time, a body resolver reconstructs the full {@link Posting} — scope,
 * target, and features are all derived from the resolved body + binding.
 *
 * <p>Implementations must also implement a ByteStore with {@link Column}.
 * All operations are provided as default methods.
 *
 * <p>TokenDictionary extends {@link Service} for lifecycle management and UI presentation.
 * Service methods are provided by the underlying ByteStore implementation.
 */
public interface TokenDictionary extends AutoCloseable {

    // ==================================================================================
    // Constants
    // ==================================================================================

    byte NULL_TERMINATOR = 0x00;
    int BINDING_INDEX_SIZE = 2;
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
     * Look up a token, resolving frame bodies to produce full postings.
     *
     * <p>When scopes are provided, only postings matching those scopes are returned.
     * When no scopes are provided, returns ALL postings for that token.
     *
     * @param token        the token to look up
     * @param bodyResolver resolves body hash → FrameBody (entries skip when unresolvable)
     * @param scopes       scope filter (empty = all scopes)
     */
    default Stream<Posting> lookup(String token,
                                   Function<ContentID, Optional<FrameBodyOld>> bodyResolver,
                                   ItemID... scopes) {
        String normalized = Posting.normalize(token);
        byte[] prefix = tokenOnlyPrefix(normalized);

        Stream<Posting> results = streamPostingsWithPrefix(prefix, bodyResolver)
                .filter(p -> p.token().equals(normalized));

        if (scopes != null && scopes.length > 0) {
            if (scopes.length == 1) {
                ItemID scope = scopes[0];
                results = results.filter(p -> scopeMatches(p.scope(), scope));
            } else {
                Set<ItemID> scopeSet = new HashSet<>(Arrays.asList(scopes));
                results = results.filter(p -> {
                    if (p.scope() == null) return scopeSet.contains(null);
                    return scopeSet.contains(p.scope());
                });
            }
        }

        return results.sorted(Comparator.comparing(Posting::weight).reversed());
    }

    /**
     * Look up without body resolution — returns postings with token and weight only.
     * Scope filtering and features are unavailable in this mode.
     */
    default Stream<Posting> lookup(String token, ItemID... scopes) {
        return lookup(token, cid -> Optional.empty(), scopes);
    }

    /**
     * Prefix search for autocomplete.
     *
     * @param tokenPrefix  the prefix to search for
     * @param limit        maximum results
     * @param bodyResolver resolves body hash → FrameBody
     * @param scopes       scope filter (empty = all scopes)
     */
    default Stream<Posting> prefix(String tokenPrefix, int limit,
                                   Function<ContentID, Optional<FrameBodyOld>> bodyResolver,
                                   ItemID... scopes) {
        String normalized = Posting.normalize(tokenPrefix);
        byte[] prefix = tokenOnlyPrefix(normalized);

        Stream<Posting> results = streamPostingsWithPrefix(prefix, bodyResolver);

        if (scopes != null && scopes.length > 0) {
            if (scopes.length == 1) {
                ItemID scope = scopes[0];
                results = results.filter(p -> scopeMatches(p.scope(), scope));
            } else {
                Set<ItemID> scopeSet = new HashSet<>(Arrays.asList(scopes));
                results = results.filter(p -> {
                    if (p.scope() == null) return scopeSet.contains(null);
                    return scopeSet.contains(p.scope());
                });
            }
        }

        return results
                .sorted(Comparator.comparing(Posting::weight).reversed())
                .limit(limit);
    }

    /**
     * Prefix search without body resolution.
     */
    default Stream<Posting> prefix(String tokenPrefix, int limit, ItemID... scopes) {
        return prefix(tokenPrefix, limit, cid -> Optional.empty(), scopes);
    }

    // ==================================================================================
    // Write API
    // ==================================================================================

    /**
     * Index a frame-backed posting.
     *
     * <p>The posting must be frame-backed ({@link Posting#isFrameBacked()} = true).
     * The key is constructed from the body hash and binding index.
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
    default void indexFromFrameBody(FrameBodyOld body,
                                    java.util.function.Function<ItemID, Float> predicateWeightResolver,
                                    WriteTransaction tx) {
        List<Posting> postings = TokenExtractor.fromFrameBody(body, predicateWeightResolver);
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

    /**
     * Build the storage key for a posting.
     *
     * <p>Frame-backed: {@code [token_utf8][0x00][body_hash_34bytes][binding_index_2bytes]}
     * <p>Direct (fallback): {@code [token_utf8][0x00][target_34bytes]}
     */
    private byte[] postingKey(Posting posting) {
        byte[] tokenBytes = posting.token().getBytes(StandardCharsets.UTF_8);
        byte[] bodyHash = posting.bodyHash().encodeBinary();
        int idx = posting.bindingIndex();
        return KeyEncoder.cat(
                tokenBytes,
                new byte[]{NULL_TERMINATOR},
                bodyHash,
                new byte[]{(byte) (idx >> 8), (byte) idx}
        );
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
        int hashStart = nullPos + 1;
        int remaining = key.length - hashStart;

        // Frame-backed format: [body_hash_multihash][binding_index_2]
        if (remaining > BINDING_INDEX_SIZE) {
            try {
                HashID.Slice slice = HashID.splitLeadingMultihashFromByteArray(key, hashStart);
                int afterHash = slice.next();
                if (key.length - afterHash == BINDING_INDEX_SIZE) {
                    ContentID bodyHash = new ContentID(slice.bytes());
                    int bindingIndex = ((key[afterHash] & 0xFF) << 8)
                                     | (key[afterHash + 1] & 0xFF);
                    return new ParsedKey(token, bodyHash, bindingIndex);
                }
            } catch (IllegalArgumentException ignored) {
                // Falls through to token-only return below
            }
        }

        // Unrecognized format — return token-only
        return new ParsedKey(token, null, -1);
    }

    record ParsedKey(String token, ContentID bodyHash, int bindingIndex) {}

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

    /**
     * Stream postings matching a key prefix, resolving bodies on the fly.
     *
     * <p>Entries whose body cannot be resolved are silently skipped.
     */
    private Stream<Posting> streamPostingsWithPrefix(
            byte[] prefix,
            Function<ContentID, Optional<FrameBodyOld>> bodyResolver) {
        List<Posting> results = new ArrayList<>();

        try (var it = store().iterate(Column.BY_TOKEN, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                try {
                    ParsedKey parsed = parseKey(kv.key());
                    float weight = decodeWeight(kv.value());

                    if (parsed.bodyHash() != null && bodyResolver != null) {
                        Optional<FrameBodyOld> bodyOpt = bodyResolver.apply(parsed.bodyHash());
                        if (bodyOpt.isPresent()) {
                            FrameBodyOld body = bodyOpt.get();
                            if (parsed.bindingIndex() >= 0
                                    && parsed.bindingIndex() < body.frameBindings().size()) {
                                results.add(Posting.fromFrame(body, parsed.bindingIndex(), weight));
                            }
                        }
                        // If body not found, skip this entry
                    }
                    // No body resolver or body not found — skip entry
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
