# Storage Architecture

The storage layer for Common Graph. One source of truth, four derived indexes.

## Design Principles

- **One object store**: All content-addressed data lives in a single store. Frame bodies, frame records, manifest bytes, component snapshots, media files — all stored as `CID → bytes`. The store doesn't interpret what's inside.
- **Indexes are derived**: Every index can be rebuilt by walking the object store. Indexes are projections, not sources of truth. Corruption or schema changes are recoverable.
- **Content addressing**: Every object is stored by the hash of its bytes. Deduplication is automatic. Integrity is verifiable at any time. See [Content](content.md).
- **Transparent large object support**: Small objects are inline in the database. Large objects live on disk as files. The boundary is invisible to callers.

## Object Store

The single source of truth:

```
OBJECTS: CID → bytes
```

`persist(bytes) → CID`, `fetch(CID) → Optional<bytes>`. That's the entire API.

The store holds four kinds of objects, distinguished by their content (not by separate columns):

| Object Kind | What It Is | Size |
|-------------|-----------|------|
| **Manifest** | Signed inventory of an item's endorsed frames | Small (kilobytes) |
| **Frame body** | Semantic assertion: predicate + theme + bindings | Small (tens of bytes) |
| **Frame record** | Signed envelope: body hash + signer + timestamp + signature | Small (hundreds of bytes) |
| **Content blob** | Encoded data: component state, media, documents | Any size (bytes to gigabytes) |

The store doesn't need to know which kind an object is. Callers always know what they're fetching because they followed a typed reference to get the CID — a manifest entry, an index lookup, a FrameRecord's bodyHash field.

### Why One Store

Like git's object store, a CID is a CID. Separating objects by type adds routing logic on every persist/fetch, complicates index rebuilds (which store do I scan?), and doesn't enable meaningful dedup across types (a manifest and a frame body will never have the same bytes).

Per-type tuning (bloom filters, compression, cache priority) can be layered below the API as a transparent backend optimization if profiling ever demands it. The `persist`/`fetch` interface doesn't change.

## Indexes

Four derived indexes. All rebuildable from the object store.

### ITEMS — Version History

```
Key:   IID | VID
Value: timestamp (8 bytes)
```

Maps item identity to all known versions. Prefix scan by IID returns the full version history. The timestamp in the value enables finding the latest version without decoding manifests.

To hydrate an item: find latest VID in ITEMS → fetch manifest from OBJECTS by VID → walk frame CIDs → fetch each from OBJECTS.

**Rebuildable**: Walk all objects, decode manifests, extract IID + VID + timestamp.

### HEADS — Current Version per Principal

```
Key:   Principal | IID
Value: VID (34 bytes)
```

Tracks the current version of each item as seen by each principal (signer). Supports multi-device scenarios where different signers may have different heads for the same item.

**Rebuildable**: From ITEMS index (latest VID per IID per signer).

### FRAME_BY_ITEM — Unified Frame Index

```
Key:   ItemID | Predicate | BodyHash
Value: CID (34 bytes, pointing to body or record in OBJECTS)
```

The single frame lookup index. Every ItemID that participates in a frame gets an entry. This includes:

- The **theme** (the item the frame is about)
- The **target** (if the target is an ItemID)
- The **predicate itself** (predicates are items too)
- Any other ItemID in the bindings

Because predicates are ItemIDs, this one index subsumes what was previously two separate indexes (FRAME_BY_ITEM and FRAME_BY_PRED). Querying by predicate is just a prefix scan where the ItemID happens to be a predicate:

| Query | Scan |
|-------|------|
| All frames involving item X | Prefix `[X]` |
| Frames involving X via predicate P | Prefix `[X \| P]` |
| All frames with predicate P | Prefix `[P]` (P is an ItemID too) |

**Rebuildable**: Walk all objects, decode frame bodies, fan out by predicate + all ItemID bindings.

### RECORD_BY_BODY — Attestation Index

```
Key:   BodyHash | SignerKeyID
Value: CID (34 bytes, pointing to record in OBJECTS)
```

Tracks who has attested a given frame body. Enables:
- "Who signed this assertion?" — prefix scan by BodyHash
- "Has signer X attested body Y?" — exact key lookup
- Attestation counting — count entries for a BodyHash prefix

**Rebuildable**: Walk all objects, decode frame records, extract bodyHash + signer.

## Large Object Storage

The object store transparently handles objects of any size through three storage modes:

### Inline (Default)

Small objects (below a configurable threshold) are stored directly in the database column:

```
Column value: 0x00 | raw bytes
```

This is the common case. Frame bodies, records, manifests, and small component snapshots all fit inline.

### Store-Managed External

Large objects are written to disk at a path derived from the CID:

```
Column value: 0x01 | path length (u32) | path bytes (UTF-8)

Filesystem: objects/<prefix>/<cid-hex>
```

The store controls the layout. The caller sees no difference — `fetch(CID)` reads from disk transparently. The database entry is small (just a pointer).

### User-Named External

Objects can be registered at a user-specified path:

```
Column value: 0x02 | path length (u32) | path bytes (UTF-8)
```

This enables files to live at human-friendly locations while still being addressable by CID. A media library can keep movies at `/media/movies/inception.mkv` — the file stays where it is, named what it's named, usable by media players and other tools. The store just knows where to find it.

On fetch, the store verifies integrity by hashing the file and comparing to the CID. If the file was modified outside CG, the hash mismatch is a detectable event (triggers re-indexing, new CID, new version).

### Streaming Access

For large objects, the store provides streaming access alongside the byte-array API:

```
fetchStream(CID) → Optional<InputStream>
```

This avoids loading multi-gigabyte content into memory. The inline/external distinction is invisible — both modes support streaming.

### Chunking

Very large objects can be split into content-addressed chunks:

```
BlobManifest {
    totalSize:  integer
    chunkSize:  integer
    chunkCids:  [CID]
}
```

The CID of the file is the CID of the BlobManifest. Each chunk is a separate object in the store, independently fetchable and verifiable. The BlobManifest is stored as a regular object; chunks are stored as regular objects. No special column or storage mode needed.

## Storage Backends

All backends implement the same interfaces. The choice is transparent to everything above the storage layer.

| Backend | Characteristics | Use Case |
|---------|----------------|----------|
| **RocksDB** | Persistent, LSM-tree, column families, bloom filters | Production |
| **MapDB** | Persistent, B-tree, lightweight | Lighter-weight alternative |
| **SkipList** | In-memory, zero dependencies | Testing and ephemeral use |

### Column Families

Each backend manages five column families (one store + four indexes):

| Column Family | Contents |
|---------------|----------|
| **OBJECTS** | All content-addressed blobs |
| **ITEMS** | IID \| VID → timestamp |
| **HEADS** | Principal \| IID → VID |
| **FRAME_BY_ITEM** | ItemID \| Pred \| BodyHash → CID |
| **RECORD_BY_BODY** | BodyHash \| SignerKeyID → CID |

Plus supporting column families for ItemDirectory and TokenDictionary (unchanged from current architecture — see [Library](library.md)).

### Transaction Model

Write operations use transactions for atomicity across columns:

```
store.runInWriteTransaction(tx → {
    CID cid = tx.persistObject(manifestBytes);
    tx.indexItem(iid, vid, timestamp);
    tx.indexHead(principal, iid, vid);
    // All succeed or all fail
})
```

Read operations use snapshot isolation (RocksDB MVCC).

## Index Rebuild

All indexes are projections of the object store. To rebuild from scratch:

```
1. Walk OBJECTS
2. For each object, trial-decode:
   a. Manifest? → index in ITEMS (IID|VID → timestamp), update HEADS
   b. Frame body? → index in FRAME_BY_ITEM (fan out by predicate + all ItemID bindings)
   c. Frame record? → index in RECORD_BY_BODY (bodyHash + signer → CID)
   d. Content blob? → no index entry needed (referenced from manifests/bodies by CID)
3. Done
```

Classification is cheap — manifests, bodies, and records have distinct CBOR structures. Content blobs are everything else (referenced by CID from other objects, not directly indexed).

## Content Lifecycle

### Storing (Commit)

```
1. Encode each endorsed frame value → content bytes
2. persist(content bytes) → content CID
3. Build manifest: frame entries with content CIDs
4. Encode manifest body → manifest bytes
5. persist(manifest bytes) → VID (= CID of manifest body)
6. Index: ITEMS[IID|VID], HEADS[principal|IID], FRAME_BY_ITEM[...]
```

### Storing (Unendorsed Frame)

```
1. Build FrameBody: predicate + theme + bindings
2. persist(body bytes) → body CID
3. Build FrameRecord: body hash + signer + timestamp + signature
4. persist(record bytes) → record CID
5. Index: FRAME_BY_ITEM[...], RECORD_BY_BODY[bodyHash|signer]
```

### Retrieving (Hydrate)

```
1. ITEMS[IID] → latest VID (by timestamp)
2. OBJECTS[VID] → manifest bytes → decode
3. For each frame entry in manifest:
   a. OBJECTS[entry.contentCID] → content bytes → decode → live instance
4. Bind live instances to @Frame fields
```

### Querying

```
1. FRAME_BY_ITEM[itemID | predicate] → prefix scan → body CIDs
2. OBJECTS[bodyCID] → body bytes → decode FrameBody
3. Optional: RECORD_BY_BODY[bodyHash] → record CIDs → attestation info
```

## Verification

The integrity chain from content to signature:

```
Content bytes → CID (verified by hash)
  → Frame entry → Manifest body → VID (verified by hash)
    → Manifest record → Signature (verified by public key)
```

Any object can be verified at any time: fetch bytes, recompute hash, compare to CID. The store does this automatically for external files on fetch.

## Migration from Previous Architecture

The previous storage used separate columns for MANIFEST, PAYLOAD, RELATION, RECORD, CHUNK, and BUNDLE, plus separate FRAME_BY_ITEM and FRAME_BY_PRED indexes. The new architecture collapses these:

| Previous | New |
|----------|-----|
| MANIFEST column | OBJECTS (manifests are just objects; ITEMS index provides IID→VID lookup) |
| PAYLOAD column | OBJECTS |
| RELATION column | OBJECTS (was already deprecated, delegated to PAYLOAD) |
| RECORD column | OBJECTS |
| CHUNK column | OBJECTS (chunks are regular objects) |
| BUNDLE column | OBJECTS (bundles are regular objects) |
| FRAME_BY_ITEM index | FRAME_BY_ITEM (unchanged) |
| FRAME_BY_PRED index | FRAME_BY_ITEM (predicates are items — same index) |
| RECORD_BY_BODY index | RECORD_BY_BODY (unchanged) |
| ITEMS index | ITEMS (expanded: IID\|VID key instead of IID-only) |
| HEADS index | HEADS (unchanged) |

Seven storage columns and two index columns become one storage column and four index columns. Five total.

## References

- [Git Object Store](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects) — Single content-addressed object store for all object types
- [IPFS](https://ipfs.tech/) — Content-addressed block storage with Merkle DAGs
- [Content Addressing](content.md) — CIDs, hashing, encoding, selectors
- [Library](library.md) — Store registry, ItemDirectory, TokenDictionary, bootstrap
