package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Datum;
import dev.everydaythings.graph.frame.Record;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.CompoundKey.FrameToken;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.library.skiplist.SkipListDataStore;
import dev.everydaythings.graph.library.skiplist.SkipListIndexStore;
import dev.everydaythings.graph.library.tokens.Posting;
import dev.everydaythings.graph.library.tokens.SkipListTokenDictionary;
import dev.everydaythings.graph.library.tokens.TokenDictionary;
import dev.everydaythings.graph.value.Decimal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The new Library — composes a {@link DataStore} (primary truth) with an
 * {@link IndexStore} (derived, rebuildable). Owned by a {@code Librarian} as the
 * local-storage backing for a Common Graph node.
 *
 * <p>Library's job is to:
 * <ul>
 *   <li>Persist Datums (and content blobs) to the DataStore's OBJECTS column.</li>
 *   <li>Walk each newly-stored Datum's bindings and write index entries to the
 *       IndexStore's three indexes (forward bindings, reverse bindings, type).</li>
 *   <li>Expose query methods backed by those indexes.</li>
 *   <li>Allow indexes to be dropped and rebuilt by walking OBJECTS.</li>
 * </ul>
 *
 * <p>Minimal first cut: just persist + retrieve, with the indexing logic and
 * query methods to be added piece by piece. The Librarian uses Library through
 * a clean facade — callers (Items) never reach past the Librarian to Library
 * directly.
 *
 * <p>For the storage architecture, see
 * <a href="../../../../../../../../../docs/storage.md">storage.md</a>.
 */
public class Library implements AutoCloseable {

    private final DataStore dataStore;
    private final IndexStore indexStore;
    private final TokenDictionary tokenDictionary;

    private Library(DataStore dataStore, IndexStore indexStore, TokenDictionary tokenDictionary) {
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.indexStore = Objects.requireNonNull(indexStore, "indexStore");
        this.tokenDictionary = Objects.requireNonNull(tokenDictionary, "tokenDictionary");
    }

    /**
     * Create an in-memory Library backed by SkipList stores. Zero-dependency,
     * pure Java; suitable for tests, ephemeral runs, and demos.
     *
     * <p>When persistent storage lands, parallel factories
     * ({@code Library.atPath(Path)}, etc.) will provide the same self-managed
     * pattern — the caller never assembles stores by hand.
     */
    public static Library inMemory() {
        return new Library(
                SkipListDataStore.create(),
                SkipListIndexStore.create(),
                SkipListTokenDictionary.create());
    }

    /**
     * Close the Library and any backing stores that hold resources. Safe to
     * call multiple times; in-memory backings are no-ops.
     */
    @Override
    public void close() {
        closeQuietly(dataStore);
        closeQuietly(indexStore);
        closeQuietly(tokenDictionary);
    }

    private static void closeQuietly(Object o) {
        if (o instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best-effort close
            }
        }
    }

    // ==================================================================================
    // Object persistence
    // ==================================================================================

    /**
     * Persist a Datum's encoded bytes into OBJECTS, returning the CID. Side-effects
     * the IndexStore with any applicable index entries.
     *
     * <p>Currently wired indexes:
     * <ul>
     *   <li>RECORDS_BY_BODY — for Records, keyed on (body-CID, record-CID).</li>
     *   <li>TYPE_INDEX — for archetypal Bodies (those with an ITEM_ID binding),
     *       keyed on (head-IID, vid-slot, item-IID, body-CID).</li>
     *   <li>FORWARD_BINDINGS — for any binding (on Body or Record) whose target is
     *       a reference, keyed on (role, qual-count, qualifiers..., target-multihash, datum-CID).</li>
     * </ul>
     *
     * <p>TODO: REVERSE_BINDINGS, plus literal/inline-frame target encoding for FORWARD_BINDINGS.
     */
    public ContentID put(Datum datum) {
        Objects.requireNonNull(datum, "datum");
        ContentID cid = datum.cid();
        byte[] bytes = datum.encodeBinary(Canonical.Scope.BODY);
        dataStore.db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        index(datum, cid);
        return cid;
    }

    /**
     * Persist arbitrary content bytes (a content blob, not a Datum) into OBJECTS,
     * returning the CID computed from the bytes.
     */
    public ContentID putContent(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        ContentID cid = ContentID.of(bytes);
        dataStore.db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        return cid;
    }

    /**
     * Retrieve raw bytes from OBJECTS by CID.
     *
     * @return the encoded bytes, or empty if not found locally
     */
    public Optional<byte[]> get(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        byte[] bytes = dataStore.db(DataStore.Column.OBJECTS).key(cid).get();
        return Optional.ofNullable(bytes);
    }

    /**
     * Whether the local OBJECTS store has bytes for the given CID.
     */
    public boolean has(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        return dataStore.db(DataStore.Column.OBJECTS).key(cid).exists();
    }

    /**
     * Delete a Datum from local storage, tearing down its index entries first.
     *
     * <p>Symmetric inverse of {@link #put}: fetches the Datum at {@code cid},
     * computes the index entries it would have written, removes them, then removes
     * the OBJECTS entry. Idempotent — returns {@code false} if the CID isn't
     * present locally; otherwise {@code true} after deletion.
     *
     * <p>This deletes only the named Datum. Cascading (e.g., deleting an item's
     * manifests + their records) is the caller's responsibility — the caller
     * decides what's a logical group and how to tear it down.
     */
    public boolean delete(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        Optional<byte[]> bytes = get(cid);
        if (bytes.isEmpty()) return false;

        Datum datum = decodeDatum(bytes.get());
        if (datum != null) {
            unindex(datum, cid);
        }

        dataStore.db(DataStore.Column.OBJECTS).key(cid).delete();
        return true;
    }

    private static Datum decodeDatum(byte[] bytes) {
        try {
            com.upokecenter.cbor.CBORObject node = com.upokecenter.cbor.CBORObject.DecodeFromBytes(bytes);
            if (node.getType() != com.upokecenter.cbor.CBORType.Array) return null;
            int size = node.size();
            if (size == 2) return Body.fromCborTree(node);
            if (size == 3) return Record.fromCborTree(node);
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void unindex(Datum datum, ContentID cid) {
        if (datum instanceof Record record) {
            ContentID bodyCid = record.headRef().bodyCid();
            byte[] key = concat(bodyCid.encodeBinary(), cid.encodeBinary());
            indexStore.db(IndexStore.Column.RECORDS_BY_BODY).key(key).delete();
        } else if (datum instanceof Body body) {
            body.binding(CompoundKey.of(Manifest.ITEM_ID)).ifPresent(itemIdBinding -> {
                byte[] key = composeTypeIndexKey(body, itemIdBinding, cid);
                indexStore.db(IndexStore.Column.TYPE_INDEX).key(key).delete();
            });
        }
        unindexBindings(datum, cid);
        unindexTokens(datum, cid);
    }

    private void unindexBindings(Datum datum, ContentID datumCid) {
        for (Binding b : datum.bindings()) {
            composeForwardKey(b, datumCid).ifPresent(key ->
                    indexStore.db(IndexStore.Column.FORWARD_BINDINGS).key(key).delete()
            );
        }
    }

    // ==================================================================================
    // Index queries
    // ==================================================================================

    /**
     * Find the CIDs of all records that attest the given body, by prefix-scanning
     * the RECORDS_BY_BODY index.
     *
     * <p>Returns an empty list if no records are indexed against the body — either
     * because none exist or because none have been persisted to this Library.
     */
    public List<ContentID> recordCidsForBody(ContentID bodyCid) {
        Objects.requireNonNull(bodyCid, "bodyCid");
        byte[] prefix = bodyCid.encodeBinary();
        List<ContentID> recordCids = new ArrayList<>();
        indexStore.forEach(IndexStore.Column.RECORDS_BY_BODY, prefix, (key, value) -> {
            byte[] suffix = Arrays.copyOfRange(key, prefix.length, key.length);
            recordCids.add(new ContentID(suffix));
        });
        return List.copyOf(recordCids);
    }

    /**
     * Find the body-CIDs of all archetypal manifests claiming the given item-IID, by
     * prefix-scanning FORWARD_BINDINGS for the {@code ITEM_ID} role with that target.
     *
     * <p>Returns one entry per manifest version persisted locally for the item.
     * The caller picks the "current" one per its policy (HEAD logic is not yet
     * wired; for now, expect at most one entry in single-version-per-item tests).
     */
    public List<ContentID> manifestCidsForItem(ItemID itemIid) {
        return bodyCidsForReferenceBinding(Manifest.ITEM_ID, itemIid);
    }

    /**
     * Find the body-CIDs of all bodies that have a simple-key (no qualifiers) binding
     * with the given role pointing at the given reference target.
     *
     * <p>Generalizes {@link #manifestCidsForItem} — the manifest-by-iid lookup is just
     * the special case where role = ITEM_ID. Other useful queries:
     * <ul>
     *   <li>{@code bodyCidsForReferenceBinding(THEME, X)} — frames where the THEME role
     *       points at X. Used for things like "which IMPLEMENTS frames target this
     *       concept?" combined with a head-filter.</li>
     *   <li>{@code bodyCidsForReferenceBinding(AGENT, X)} — frames where AGENT points
     *       at X.</li>
     * </ul>
     *
     * <p>This is a Phase 1 query; doesn't yet support qualified-key matches. Caller
     * fetches each result body to apply additional filters (head, qualifiers, etc.).
     */
    public List<ContentID> bodyCidsForReferenceBinding(ItemID role, ItemID target) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(target, "target");
        byte[] roleBytes = role.encodeBinary();
        byte[] targetBytes = target.encodeBinary();
        byte[] prefix = concat(roleBytes, new byte[]{0}, targetBytes);
        List<ContentID> bodyCids = new ArrayList<>();
        indexStore.forEach(IndexStore.Column.FORWARD_BINDINGS, prefix, (key, value) -> {
            byte[] bodyCidBytes = Arrays.copyOfRange(key, prefix.length, key.length);
            bodyCids.add(new ContentID(bodyCidBytes));
        });
        return List.copyOf(bodyCids);
    }

    /**
     * Find the body-CIDs of all archetypal manifests whose head references the given
     * type sememe, by prefix-scanning TYPE_INDEX.
     *
     * <p>Returns matches regardless of whether the body's head was version-pinned;
     * the prefix scan covers both pinned and unpinned manifests of the type. Returns
     * an empty list if no manifests of that type have been persisted locally.
     */
    public List<ContentID> manifestCidsForType(ItemID typeIid) {
        Objects.requireNonNull(typeIid, "typeIid");
        byte[] prefix = typeIid.encodeBinary();
        List<ContentID> bodyCids = new ArrayList<>();
        indexStore.forEach(IndexStore.Column.TYPE_INDEX, prefix, (key, value) -> {
            // Layout: head-IID | vid-slot | item-IID | body-CID
            int off = prefix.length;
            int vidLen = key[off] & 0xFF;
            off += 1 + vidLen;
            HashID.Slice itemIidSlice = HashID.splitLeadingMultihashFromByteArray(key, off);
            off = itemIidSlice.next();
            byte[] bodyCidBytes = Arrays.copyOfRange(key, off, key.length);
            bodyCids.add(new ContentID(bodyCidBytes));
        });
        return List.copyOf(bodyCids);
    }

    // ==================================================================================
    // Token Dictionary
    // ==================================================================================

    /**
     * Look up postings for an exact token. Returns ranked Postings ordered by
     * descending weight; empty if the token has no entries or if all entries'
     * datums fail to resolve locally.
     */
    public List<Posting> lookupToken(String token) {
        return tokenDictionary.lookup(token, this::fetchDatum).toList();
    }

    /**
     * Prefix search for autocomplete. Returns up to {@code limit} ranked
     * Postings whose tokens begin with {@code tokenPrefix}.
     */
    public List<Posting> lookupTokenPrefix(String tokenPrefix, int limit) {
        return tokenDictionary.prefix(tokenPrefix, limit, this::fetchDatum).toList();
    }

    /**
     * Index a single token-bearing binding. Token-indexing is currently
     * driven explicitly by callers; auto-indexing on {@link #put} will be
     * added when per-predicate weight policy is settled.
     */
    public void indexToken(String token, ContentID datum, CompoundKey bindingKey, Decimal weight) {
        tokenDictionary.index(token, datum, bindingKey, weight, null);
    }

    /**
     * Resolve a CID to its decoded Datum (Body or Record). Used as the
     * datum-resolver passed to {@link TokenDictionary#lookup} so the dictionary
     * can assemble rich Postings from minimal stored entries.
     */
    private Optional<Datum> fetchDatum(ContentID cid) {
        return get(cid).map(Library::decodeDatum);
    }

    // ==================================================================================
    // Indexing (write-side)
    // ==================================================================================

    private static final byte[] EMPTY_VALUE = new byte[0];

    private void index(Datum datum, ContentID cid) {
        if (datum instanceof Record record) {
            ContentID bodyCid = record.headRef().bodyCid();
            byte[] key = concat(bodyCid.encodeBinary(), cid.encodeBinary());
            indexStore.db(IndexStore.Column.RECORDS_BY_BODY).key(key).put(EMPTY_VALUE);
        } else if (datum instanceof Body body) {
            body.binding(CompoundKey.of(Manifest.ITEM_ID)).ifPresent(itemIdBinding -> {
                byte[] key = composeTypeIndexKey(body, itemIdBinding, cid);
                indexStore.db(IndexStore.Column.TYPE_INDEX).key(key).put(EMPTY_VALUE);
            });
        }
        indexBindings(datum, cid);
        indexTokens(datum, cid);
    }

    /**
     * Walk every binding whose target is a text Literal and add a token-dictionary
     * entry for its surface text. Per-predicate weight policy is not yet wired —
     * everything indexes at weight 1.0.
     */
    private void indexTokens(Datum datum, ContentID datumCid) {
        for (Binding b : datum.bindings()) {
            extractIndexableText(b.target()).ifPresent(text -> {
                CompoundKey bindingKey = CompoundKey.of(b.role(), b.qualifiers());
                tokenDictionary.index(text, datumCid, bindingKey, Decimal.ofInt(1), null);
            });
        }
    }

    private void unindexTokens(Datum datum, ContentID datumCid) {
        for (Binding b : datum.bindings()) {
            extractIndexableText(b.target()).ifPresent(text -> {
                CompoundKey bindingKey = CompoundKey.of(b.role(), b.qualifiers());
                tokenDictionary.remove(text, datumCid, bindingKey, null);
            });
        }
    }

    private static Optional<String> extractIndexableText(BindingTarget target) {
        if (target instanceof Literal lit && Literal.TYPE_TEXT.equals(lit.valueType())) {
            String text = lit.asText();
            if (text == null || text.isBlank()) return Optional.empty();
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private void indexBindings(Datum datum, ContentID datumCid) {
        for (Binding b : datum.bindings()) {
            composeForwardKey(b, datumCid).ifPresent(key ->
                    indexStore.db(IndexStore.Column.FORWARD_BINDINGS).key(key).put(EMPTY_VALUE)
            );
        }
    }

    private static Optional<byte[]> composeForwardKey(Binding binding, ContentID datumCid) {
        Optional<byte[]> targetBytes = extractReferenceMultihash(binding.target());
        if (targetBytes.isEmpty()) return Optional.empty();
        int qualCount = binding.qualifiers().size();
        if (qualCount > 0xFF) {
            throw new IllegalStateException("Binding has more than 255 qualifiers; key layout assumes 1-byte count");
        }
        byte[] roleBytes = binding.role().encodeBinary();
        byte[] qualBytes = encodeQualifiers(binding.qualifiers());
        byte[] cidBytes = datumCid.encodeBinary();
        return Optional.of(concat(
                roleBytes,
                new byte[]{(byte) qualCount},
                qualBytes,
                targetBytes.get(),
                cidBytes
        ));
    }

    private static Optional<byte[]> extractReferenceMultihash(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) {
            return Optional.of(iid.iid().encodeBinary());
        }
        if (target instanceof BindingTarget.RefTarget refTarget) {
            // Phase 1: only handle simple references (no frame-key drill-down).
            // TODO: compound RefTargets (with frame key or portion) deferred.
            if (refTarget.isCompound()) return Optional.empty();
            return Optional.of(refTarget.asItemId().encodeBinary());
        }
        // TODO: Literal targets and FrameTarget deferred — encoding scheme not settled.
        return Optional.empty();
    }

    private static byte[] encodeQualifiers(List<FrameToken> qualifiers) {
        if (qualifiers.isEmpty()) return new byte[0];
        List<byte[]> parts = new ArrayList<>(qualifiers.size());
        int total = 0;
        for (FrameToken q : qualifiers) {
            byte[] qBytes = q.toCbor().EncodeToBytes();
            parts.add(qBytes);
            total += qBytes.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, result, offset, p.length);
            offset += p.length;
        }
        return result;
    }

    private static byte[] composeTypeIndexKey(Body body, Binding itemIdBinding, ContentID bodyCid) {
        ItemRef head = body.headRef();
        byte[] headIid = head.iid().encodeBinary();
        byte[] vidSlot = encodeVidSlot(head.version());
        byte[] itemIid = extractItemId(itemIdBinding.target()).encodeBinary();
        byte[] bodyCidBytes = bodyCid.encodeBinary();
        return concat(headIid, vidSlot, itemIid, bodyCidBytes);
    }

    private static byte[] encodeVidSlot(Optional<ContentID> vid) {
        if (vid.isEmpty()) return new byte[]{0};
        byte[] vidBytes = vid.get().encodeBinary();
        if (vidBytes.length > 0xFF) {
            throw new IllegalStateException("VID exceeds 255 bytes; layout assumes 1-byte length prefix");
        }
        byte[] result = new byte[1 + vidBytes.length];
        result[0] = (byte) vidBytes.length;
        System.arraycopy(vidBytes, 0, result, 1, vidBytes.length);
        return result;
    }

    private static ItemID extractItemId(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) return iid.iid();
        if (target instanceof BindingTarget.RefTarget ref) return ref.asItemId();
        throw new IllegalStateException(
                "ITEM_ID target must be a reference, got " + target.getClass().getSimpleName());
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }
}
