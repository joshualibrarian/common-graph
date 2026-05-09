package dev.everydaythings.graph.library.tokens;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Datum;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.library.Service;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.value.Decimal;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Token → ranked-Postings lookup, backed by a {@link ByteStore}.
 *
 * <p>Internal-only — consumers reach token lookup through the librarian's
 * lookup methods, never directly through this interface. The dictionary itself
 * is a pure index over byte storage.
 *
 * <p>Storage key format (single column {@link Column#BY_TOKEN}):
 * <pre>
 *   Key:   [token_utf8][0x00][datum_cid_multihash][compound_key_cbor]
 *   Value: [weight_4bytes_scaled_int]
 * </pre>
 *
 * <p>The datum CID is a self-delimiting multihash. Whatever bytes follow are the
 * compound key's CBOR encoding, extending to the end of the key.
 *
 * <p>Lookup takes a datum resolver: for each indexed entry, the dictionary
 * fetches the source datum (typically a {@link dev.everydaythings.graph.frame.Body Body})
 * and extracts the {@link Posting}'s rich fields — predicate, target, scope,
 * features. The body world (FrameBodyOld vs Body) is invisible to consumers; the
 * resolver is responsible for returning whatever Datum the index points at.
 *
 * <p>Implementations also implement {@code ByteStore<Column>}; default methods
 * here delegate. The {@link Service} extension provides lifecycle.
 */
public interface TokenDictionary extends Service {

    // ==================================================================================
    // Constants
    // ==================================================================================

    byte NULL_TERMINATOR = 0x00;

    /** Weight is stored as fixed-point: actual = stored / WEIGHT_SCALE. */
    int WEIGHT_SCALE = 1000;

    /** Decimal scale corresponding to {@link #WEIGHT_SCALE} (10^3). */
    int WEIGHT_SCALE_DIGITS = 3;

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
     * Look up postings for an exact token. Returns ranked Postings ordered by
     * descending weight; empty if the token has no entries or if all entries'
     * datums fail to resolve.
     *
     * @param token         the token to look up (normalized internally)
     * @param datumResolver resolves a datum CID to its in-memory Datum
     */
    default Stream<Posting> lookup(String token,
                                   Function<ContentID, Optional<Datum>> datumResolver) {
        String normalized = normalize(token);
        byte[] prefix = exactTokenPrefix(normalized);
        return streamPostings(prefix, datumResolver)
                .sorted(Comparator.comparing((Posting p) -> p.weight().toDouble()).reversed());
    }

    /**
     * Prefix search for autocomplete. Returns up to {@code limit} ranked
     * Postings whose tokens begin with {@code tokenPrefix}.
     */
    default Stream<Posting> prefix(String tokenPrefix, int limit,
                                   Function<ContentID, Optional<Datum>> datumResolver) {
        String normalized = normalize(tokenPrefix);
        byte[] prefix = normalized.getBytes(StandardCharsets.UTF_8);
        return streamPostings(prefix, datumResolver)
                .sorted(Comparator.comparing((Posting p) -> p.weight().toDouble()).reversed())
                .limit(limit);
    }

    // ==================================================================================
    // Write API
    // ==================================================================================

    /**
     * Index a single (token, datum-binding, weight) entry.
     *
     * @param token       the token surface form (normalized internally)
     * @param datum       CID of the source datum (body or record) that produced this token
     * @param bindingKey  compound key (role + qualifiers) of the indexed binding
     * @param weight      ranking score
     * @param tx          write transaction (may be null for auto-commit)
     */
    default void index(String token, ContentID datum, CompoundKey bindingKey,
                       Decimal weight, WriteTransaction tx) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(bindingKey, "bindingKey");
        Objects.requireNonNull(weight, "weight");
        String normalized = normalize(token);
        byte[] key = entryKey(normalized, datum, bindingKey);
        byte[] value = encodeWeight(weight);
        if (tx != null) {
            store().put(Column.BY_TOKEN, key, value, tx);
        } else {
            store().put(Column.BY_TOKEN, key, value);
        }
    }

    /** Remove an indexed entry. */
    default void remove(String token, ContentID datum, CompoundKey bindingKey,
                        WriteTransaction tx) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(bindingKey, "bindingKey");
        String normalized = normalize(token);
        byte[] key = entryKey(normalized, datum, bindingKey);
        if (tx != null) {
            store().delete(Column.BY_TOKEN, key, tx);
        } else {
            store().delete(Column.BY_TOKEN, key);
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

    // ==================================================================================
    // Token Normalization
    // ==================================================================================

    /** Normalize a token: NFC unicode, lowercase, trim, collapse whitespace. */
    static String normalize(String token) {
        if (token == null) return null;
        String normalized = Normalizer.normalize(token, Normalizer.Form.NFC);
        return normalized.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    // ==================================================================================
    // Key Encoding
    // ==================================================================================

    private byte[] entryKey(String normalizedToken, ContentID datum, CompoundKey bindingKey) {
        byte[] tokenBytes = normalizedToken.getBytes(StandardCharsets.UTF_8);
        byte[] datumBytes = datum.encodeBinary();
        byte[] compoundKeyBytes = bindingKey.toCborTree(Canonical.Scope.BODY).EncodeToBytes();
        return KeyEncoder.cat(
                tokenBytes,
                new byte[]{NULL_TERMINATOR},
                datumBytes,
                compoundKeyBytes);
    }

    private byte[] exactTokenPrefix(String normalizedToken) {
        byte[] tokenBytes = normalizedToken.getBytes(StandardCharsets.UTF_8);
        return KeyEncoder.cat(tokenBytes, new byte[]{NULL_TERMINATOR});
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
        if (nullPos < 0) return null;

        String token = new String(key, 0, nullPos, StandardCharsets.UTF_8);
        int datumStart = nullPos + 1;
        if (datumStart >= key.length) return null;

        try {
            HashID.Slice slice = HashID.splitLeadingMultihashFromByteArray(key, datumStart);
            ContentID datum = new ContentID(slice.bytes());
            int afterDatum = slice.next();
            if (afterDatum > key.length) return null;

            byte[] compoundKeyBytes = Arrays.copyOfRange(key, afterDatum, key.length);
            CompoundKey bindingKey = CompoundKey.fromCborTree(
                    CBORObject.DecodeFromBytes(compoundKeyBytes));
            return new ParsedKey(token, datum, bindingKey);
        } catch (Exception e) {
            return null;
        }
    }

    record ParsedKey(String token, ContentID datum, CompoundKey bindingKey) {}

    // ==================================================================================
    // Value Encoding
    // ==================================================================================

    private byte[] encodeWeight(Decimal weight) {
        int scaled = (int) Math.round(weight.toDouble() * WEIGHT_SCALE);
        return new byte[]{
                (byte) (scaled >> 24),
                (byte) (scaled >> 16),
                (byte) (scaled >> 8),
                (byte) scaled
        };
    }

    private Decimal decodeWeight(byte[] value) {
        if (value == null || value.length < 4) return Decimal.ofInt(1);
        int scaled = ((value[0] & 0xFF) << 24)
                | ((value[1] & 0xFF) << 16)
                | ((value[2] & 0xFF) << 8)
                | (value[3] & 0xFF);
        return Decimal.of(scaled, WEIGHT_SCALE_DIGITS);
    }

    // ==================================================================================
    // Iteration & Posting Assembly
    // ==================================================================================

    private Stream<Posting> streamPostings(byte[] prefix,
                                           Function<ContentID, Optional<Datum>> datumResolver) {
        List<Posting> results = new ArrayList<>();
        try (var it = store().iterate(Column.BY_TOKEN, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                ParsedKey parsed = parseKey(kv.key());
                if (parsed == null) continue;
                Decimal weight = decodeWeight(kv.value());
                Optional<Datum> datumOpt = datumResolver.apply(parsed.datum());
                if (datumOpt.isEmpty()) continue;
                Posting posting = buildPosting(parsed.token(), parsed.datum(),
                        parsed.bindingKey(), weight, datumOpt.get());
                if (posting != null) results.add(posting);
            }
        }
        return results.stream();
    }

    /**
     * Assemble a rich Posting from an indexed entry plus the resolved datum.
     *
     * <p>Extraction assumes a body-shaped datum where the head is the predicate
     * (LEXEME, TITLE, NAME, SYMBOL, ...), the indexed binding's qualifiers are
     * {@code [scope, ...features]}, and a THEME binding (if present) gives the
     * target. Records or other shapes return null and are skipped.
     */
    private Posting buildPosting(String token, ContentID source, CompoundKey bindingKey,
                                 Decimal weight, Datum datum) {
        if (!(datum.head() instanceof ItemRef itemRef)) return null;
        ItemID predicate = itemRef.iid();

        Binding indexed = datum.binding(bindingKey).orElse(null);
        if (indexed == null) return null;

        List<CompoundKey.FrameToken> quals = indexed.qualifiers();
        ItemID scope = null;
        if (!quals.isEmpty() && quals.get(0) instanceof CompoundKey.Sememe s) {
            scope = s.id();
        }
        Set<ItemID> features = new HashSet<>();
        for (int i = 1; i < quals.size(); i++) {
            if (quals.get(i) instanceof CompoundKey.Sememe s) features.add(s.id());
        }

        ItemID target = null;
        List<Binding> themeBindings = datum.bindingsByRole(ThematicRole.Theme.IID);
        if (!themeBindings.isEmpty()) {
            BindingTarget t = themeBindings.get(0).target();
            if (t instanceof BindingTarget.IidTarget iid) {
                target = iid.iid();
            } else if (t instanceof BindingTarget.RefTarget ref) {
                target = ref.asItemId();
            }
        }

        return new Posting(token, target, predicate, scope, features, weight, source);
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
