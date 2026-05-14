package dev.everydaythings.graph.library.index;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.value.Literal;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumID;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.value.Decimal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Byte-backed {@link TokenIndexStore} — composes the TokenIndexStore domain
 * interface with a {@link ByteStore} keyed on {@link TokenIndexStore.Column}.
 *
 * <p>Storage layout (single column {@link TokenIndexStore.Column#BY_TOKEN}):
 * <pre>
 *   Key:   [token_utf8][0x00][datum_id_multihash][compound_key_cbor]
 *   Value: [weight_4bytes_scaled_int]
 * </pre>
 *
 * <p>Concrete impls only need to provide the ByteStore backing.
 */
public interface TokenIndexByteStore extends TokenIndexStore, ByteStore<TokenIndexStore.Column> {

    byte NULL_TERMINATOR = 0x00;

    // ==================================================================================
    // Write API
    // ==================================================================================

    @Override
    default void index(Datum datum, DatumID datumId) {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(datumId, "datumId");
        for (Binding b : datum.bindings()) {
            extractIndexableText(b.target()).ifPresent(text -> {
                CompoundKey bindingKey = CompoundKey.of(b.role(), b.qualifiers());
                String normalized = TokenIndexStore.normalize(text);
                byte[] key = entryKey(normalized, datumId, bindingKey);
                byte[] value = encodeWeight(Decimal.ofInt(1));
                db(TokenIndexStore.Column.BY_TOKEN).key(key).put(value);
            });
        }
    }

    @Override
    default void unindex(Datum datum, DatumID datumId) {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(datumId, "datumId");
        for (Binding b : datum.bindings()) {
            extractIndexableText(b.target()).ifPresent(text -> {
                CompoundKey bindingKey = CompoundKey.of(b.role(), b.qualifiers());
                String normalized = TokenIndexStore.normalize(text);
                byte[] key = entryKey(normalized, datumId, bindingKey);
                db(TokenIndexStore.Column.BY_TOKEN).key(key).delete();
            });
        }
    }

    // ==================================================================================
    // Query API
    // ==================================================================================

    @Override
    default Stream<TokenPosting> lookup(String token,
                                        Function<DatumID, Optional<Datum>> datumResolver) {
        String normalized = TokenIndexStore.normalize(token);
        byte[] prefix = exactTokenPrefix(normalized);
        return streamPostings(prefix, datumResolver)
                .sorted(Comparator.comparing((TokenPosting p) -> p.weight().toDouble()).reversed());
    }

    @Override
    default Stream<TokenPosting> prefix(String tokenPrefix, int limit,
                                        Function<DatumID, Optional<Datum>> datumResolver) {
        String normalized = TokenIndexStore.normalize(tokenPrefix);
        byte[] prefix = normalized.getBytes(StandardCharsets.UTF_8);
        return streamPostings(prefix, datumResolver)
                .sorted(Comparator.comparing((TokenPosting p) -> p.weight().toDouble()).reversed())
                .limit(limit);
    }

    // ==================================================================================
    // Internal helpers
    // ==================================================================================

    private static Optional<String> extractIndexableText(BindingTarget target) {
        if (target instanceof Literal lit && Literal.TYPE_TEXT.equals(lit.valueType())) {
            String text = lit.asText();
            if (text == null || text.isBlank()) return Optional.empty();
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private byte[] entryKey(String normalizedToken, DatumID datumId, CompoundKey bindingKey) {
        byte[] tokenBytes = normalizedToken.getBytes(StandardCharsets.UTF_8);
        byte[] datumBytes = datumId.encodeBinary();
        byte[] compoundKeyBytes = bindingKey.toCborTree(Scope.BODY).EncodeToBytes();
        return KeyEncoder.cat(tokenBytes, new byte[]{NULL_TERMINATOR}, datumBytes, compoundKeyBytes);
    }

    private byte[] exactTokenPrefix(String normalizedToken) {
        byte[] tokenBytes = normalizedToken.getBytes(StandardCharsets.UTF_8);
        return KeyEncoder.cat(tokenBytes, new byte[]{NULL_TERMINATOR});
    }

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
            DatumID datum = new DatumID(slice.bytes());
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

    record ParsedKey(String token, DatumID datumId, CompoundKey bindingKey) {}

    private static byte[] encodeWeight(Decimal weight) {
        int scaled = (int) Math.round(weight.toDouble() * TokenIndexStore.WEIGHT_SCALE);
        return new byte[]{
                (byte) (scaled >> 24),
                (byte) (scaled >> 16),
                (byte) (scaled >> 8),
                (byte) scaled
        };
    }

    private static Decimal decodeWeight(byte[] value) {
        if (value == null || value.length < 4) return Decimal.ofInt(1);
        int scaled = ((value[0] & 0xFF) << 24)
                | ((value[1] & 0xFF) << 16)
                | ((value[2] & 0xFF) << 8)
                | (value[3] & 0xFF);
        return Decimal.of(scaled, TokenIndexStore.WEIGHT_SCALE_DIGITS);
    }

    private Stream<TokenPosting> streamPostings(byte[] prefix,
                                                Function<DatumID, Optional<Datum>> datumResolver) {
        List<TokenPosting> results = new ArrayList<>();
        try (var it = iterate(TokenIndexStore.Column.BY_TOKEN, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                ParsedKey parsed = parseKey(kv.key());
                if (parsed == null) continue;
                Decimal weight = decodeWeight(kv.value());
                Optional<Datum> datumOpt = datumResolver.apply(parsed.datumId());
                if (datumOpt.isEmpty()) continue;
                TokenPosting posting = buildPosting(parsed.token(), parsed.datumId(),
                        parsed.bindingKey(), weight, datumOpt.get());
                if (posting != null) results.add(posting);
            }
        }
        return results.stream();
    }

    private static TokenPosting buildPosting(String token, DatumID source, CompoundKey bindingKey,
                                             Decimal weight, Datum datum) {
        if (!(datum.head() instanceof ItemRef itemRef)) return null;
        ItemID predicate = itemRef.iid();

        Binding indexed = datum.binding(bindingKey).orElse(null);
        if (indexed == null) return null;

        Set<ItemID> features = new HashSet<>();
        for (CompoundKey.FrameToken q : indexed.qualifiers()) {
            if (q instanceof CompoundKey.Sememe s) features.add(s.id());
        }

        ItemID target = null;
        List<Binding> themeBindings = datum.bindingsByRole(ThematicRole.Theme.IID);
        if (!themeBindings.isEmpty()) {
            BindingTarget t = themeBindings.get(0).target();
            if (t instanceof BindingTarget.RefTarget ref) {
                target = ref.asItemId();
            }
        }

        return new TokenPosting(token, target, predicate, null, features, weight, source);
    }
}
