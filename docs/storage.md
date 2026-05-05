# Storage Architecture

The storage layer for Common Graph after the Datum unification. One object store as the source of truth, three derived indexes, an item-location directory, all mediated by the Librarian.

> For the Datum primitive itself (Body, Record, Frame, Manifest), see [Datum](datum.md). For how queries operate on storage, see [Query](query.md). For how content is referenced (CIDs, multibase, multihash), see [Content](content.md).

## Layer separation

```
Item                     ← items use librarian; never touch storage directly
   ↓
Librarian                ← runtime; mediates access; routes local/network/mounted
   ↓
Library                  ← storage manager; owns ObjectStore + indexes
   ↓
ObjectStore              ← byte storage primitive; CID → bytes
```

Plus, separately:

```
ItemDirectory            ← location registry for items NOT in our local store
                           (peers, mounted WorkingTreeStores, mentioned-but-not-fetched)
```

ItemDirectory sits beside Library as primary data — not derivable from objects, so it's not an index.

## Two column-family sets

Storage is organized into two conceptually distinct sets of column families:

**Data CFs** — primary truth, never derivable from anything else:

| Column | Maps | Purpose |
|--------|------|---------|
| `objects` | CID → bytes | The single content-addressed object store. Holds Datums (bodies + records) and content blobs uniformly. |
| `item_directory` | IID → directory entry | Locations of items not in the local store. |

**Index CFs** — derived, rebuildable from `objects` by walking it:

| Column | Maps | Purpose |
|--------|------|---------|
| `forward_bindings` | (role-IID, qualifiers..., target-bytes) → CID | Role-and-qualifier-driven queries: "frames whose binding matches this pattern." |
| `reverse_bindings` | (target-bytes, role-IID, qualifiers...) → CID | Target-driven queries: "everything referencing this thing." Subsumes token resolution and time-range queries. |
| `type_index` | (head-IID, head-VID-or-empty, item-IID) → body-CID | Items by archetype: "all Documents", "all Chess games". Only archetypal/manifest bodies are entered here. |

The two sets are conceptually independent; in practice, a single backing store (RocksDB or similar) hosts both as separate column families to enable atomic transactions across object-insert + index-update.

## Why two index orderings (forward and reverse)

Both forward and reverse indexes encode the same per-binding information, just in different key orderings:

- **Forward**: `(role, qualifiers..., target)` — useful when the role is known and the target is queried by value or range. *"All English lemma verbs spelled 'create'"* (`role=VALUE, qualifier=ENGLISH/VERB/LEMMA, target="create"`).
- **Reverse**: `(target, role, qualifiers...)` — useful when the target is known. *"Everything pointing at this meme"* (`target=meme-IID, role=any`).

Most queries hit one or the other naturally. The two-index pattern is standard database technique.

The reverse index also enables several access patterns that would otherwise need dedicated indexes:

- **Token resolution and tab completion** — text-typed targets cluster contiguously by their CBOR encoding; prefix scan on text bytes returns all lexemes matching a string prefix.
- **Time-range queries** — timestamp-typed targets cluster together (CBOR Tag 1 prefix); range scan on the timestamp portion returns all assertions within a temporal range.
- **Reference reverse lookup** — IID-typed targets cluster together; exact-match returns all frames pointing at that item.

These all fall out of one index because the **encoding is discriminated by type**.

## Index key encoding

Index keys use CBOR's existing type discrimination. The leading bytes of a CBOR-encoded value already identify its type, and these prefixes naturally cluster same-type values in lexicographic order:

```
Index target portion encoding (the CBOR-tag-encoded target):

  CBOR major-type 2 (0x40+):  byte-string targets       — IIDs, content blobs (exact-match)
  CBOR major-type 3 (0x60+):  text-string targets       — text/lexemes (range-scannable)
  CBOR Tag 1 (0xc1):          epoch timestamps          — fixed-width 9-byte (range-scannable)
  CBOR Tag 6 (0xc6):          Tag-6 references          — ItemRef/ContentRef/FrameRef (exact-match)
  CBOR Tag 7 (0xc7):          explicitly-typed values   — exact-match
  CBOR Tag 9 (0xc9):          quantities                — clustered by unit; exact-match within
```

Within each type's prefix range, byte-lex ordering reflects the type's natural ordering when the encoding preserves it. Timestamps and integers do (with fixed-width sortable encoding). Text does naturally. References and quantities don't have a natural cross-instance ordering beyond unit-clustering for quantities.

### Timestamp encoding for index keys

Timestamps appear in many bindings (`TIME:[]`, `TIME:[SIGNED]`, `TIME:[ENDS]`, etc.) and need to be range-scannable to support temporal queries. The encoding inside index keys (NOT the wire format of stored Datums, which uses standard CBOR Tag 1) is:

```
0xc1 | <8 bytes nanoseconds since UTC epoch (signed, big-endian)>
     | <1 byte precision>

Total: 10 bytes including the Tag-1 prefix.

Precision byte:
  0 = year only (just the year is meaningful)
  1 = year + month
  2 = year + month + day
  3 = + hour
  4 = + minute
  5 = + second
  6 = + millisecond
  7 = + microsecond
  8 = + nanosecond
```

For partial dates, the unspecified portions of the nanoseconds value are zero-padded. Sort order is by the timestamp bytes first (temporal), with precision following. A "2024" year-only entry and a "2024-01-01T00:00:00.000000000Z" entry sort to the same byte position; the precision byte distinguishes them but doesn't change ordering.

Range queries are precise: `[T1, T2)` translates to a byte range scan with appropriately filled endpoints.

This 9-byte (post-tag) format is an internal index detail. The stored Datum uses standard CBOR Tag 1. If we ever need higher resolution or a different scheme, indexes can be rebuilt — no migration of stored data required.

### Text-string encoding caveat

Standard canonical CBOR encodes text strings with variable-length length prefixes (`0x60`-`0x77` for short strings, `0x78 + 1 byte length`, `0x79 + 2 byte length`, etc.). This breaks naive byte-prefix scanning across strings of different lengths.

For index keys specifically, text targets may use a slightly modified encoding — typically the major-type byte followed by the raw UTF-8 bytes of the string, terminated by a zero byte (since UTF-8 never contains 0x00 except in NUL characters, which can be banned in text targets) or a similar separator scheme. This preserves the type-discrimination property and enables prefix scans across arbitrary string lengths.

Wire format (in `objects`) uses canonical CBOR. Index format is internal.

### Quantity indexing

Quantities (`Tag 9 [magnitude, unit-IID]`) are encoded for index keys as:

```
0xc9 | <unit-IID multihash bytes> | <canonical-CBOR magnitude bytes>
```

Same-unit quantities cluster together (prefix `0xc9 + <unit-IID>`). Within a unit, exact-match queries work; magnitude range scans require a fixed-width sortable magnitude encoding and are deferred. When magnitude range becomes a real access pattern, a dedicated quantity range index can be added — index re-buildable.

## Type index

Indexes a Datum's *head* sememe (the predicate or archetype) — but only for archetypal bodies, where it's useful.

```
type_index keys: (head-IID, head-VID-or-empty, item-IID) → body-CID
```

A Datum gets a type-index entry **iff it has an `ITEM_ID` binding** (i.e., it's a manifest body — a versioned instance of some archetype). Propositional frames don't get type-indexed; "all AUTHORED frames in the corpus" is rarely a useful query and the volume would dominate the index.

The head-VID slot is empty (zero-byte sentinel) for unpinned references and contains the archetype's specific VID for version-pinned references. Prefix-scan with just the head-IID returns all instances of that archetype regardless of which version of the archetype was used.

Use cases:
- "All my Documents" — `prefix scan (Document-IID, ...)` → list of (item-IID, body-CID).
- "All chess games" — same, with `Chess_Game-IID`.
- "All instances of archetype-X pinned to version Y" — full-key prefix.

## Indexing rules

When a Datum is inserted into `objects`, the Library walks its bindings and writes index entries:

```
For each binding (role, qualifiers, target) in the Datum's bindings list:
  - Write forward_bindings entry: (role, qualifiers..., encoded-target) → CID
  - Write reverse_bindings entry: (encoded-target, role, qualifiers...) → CID

If the Datum has an ITEM_ID binding (it's a manifest body):
  - Write type_index entry:
        (head-IID, head-VID-or-empty, item-IID-from-ITEM_ID-binding) → CID
```

The Datum write to `objects` and the index updates happen in a single transaction so that recovery is straightforward: either everything is present, or the Datum isn't visible.

Records (Datums with signatures) are indexed identically to bodies — their bindings populate the same forward and reverse indexes. A record's bindings include things like the signer's key reference, signing time, etc., all of which become queryable.

## ItemDirectory

A separate primary-data column family for tracking items that aren't in our local `objects`:

```
item_directory keys: ItemID → DirectoryEntry

DirectoryEntry:
  iid:               ItemID
  locations:         List<Location>    — where to find the item
  latestKnownVid:    Optional<CID>     — last version we heard about
  lastSeen:          Optional<Instant> — when we last had contact
```

`Location` variants:
- A peer's network address
- A mounted WorkingTreeStore filesystem path
- "Mentioned by frame X" — referenced but never fetched

ItemDirectory provides routing hints when the local store doesn't have an item but we know where to look. It's primary data (not derivable from `objects`), so it lives alongside `objects` in the data CFs.

## Resolution priority

When the Librarian needs to fetch an item or content:

1. **Local `objects`** — fast path; if the bytes are here, return them.
2. **ItemDirectory hints** — if local doesn't have it, check directory for known locations. Try each in priority order:
   - Mounted WorkingTreeStores (filesystem; usually available)
   - Trusted peers (network; may be unreachable)
3. **Network search** — if directory has no hint, ask peers via the trust graph. May return nothing.

Successful fetches from external sources can be cached locally (write to `objects`) and the directory entry's `lastSeen` updated, but caching is optional — Librarian's policy decides.

## Backends

The `ObjectStore` interface is implementable by multiple backends:

- **In-memory** — for tests, ephemeral runs.
- **RocksDB** — production persistent storage. Column families host the data and index CFs together.
- **MapDB** — lighter-weight persistent or in-memory option.
- **WorkingTreeStore** — filesystem-backed, materialized item layout (`.item/objects/`, etc.). Used for export, USB sync, and as the persistence medium for materialized standalone items.

Library is backend-agnostic; it composes whichever ObjectStore + index machinery is available. The same indexing rules apply regardless of backend.

## Future indexes

Several additional indexes are anticipated as access patterns mature. They're noted here for design awareness; none are part of the foundation:

- **Hypernym index** — for fast hyponymy walks ("anything that's-a Animal" pulls in Dog, Cat, Mammal, etc. transitively).
- **Trust-graph index** — signer-key → frames signed by them; trust relationships.
- **Quantity range index** — fixed-width sortable magnitude encoding within a unit, for "all harvests > 5 kg"-style queries.
- **N-gram index** — for fuzzy text matching beyond byte-prefix.
- **Embedding index** — vector similarity over WL fingerprints (see [Fuzzy Matching](fuzzy-matching.md)).

All would be added without disrupting existing indexes; all would be rebuildable from `objects`.

## Why indexes are rebuildable

The combination of:

- One source-of-truth object store
- Indexes derived purely by walking objects
- A clear set of indexing rules

…gives the system several useful properties:

- **Recovery**: index corruption is non-fatal. Drop and rebuild.
- **Schema evolution**: changing index encoding requires no data migration. Drop, change rules, rebuild.
- **Selective indexing**: a deployment can choose which indexes to maintain. A read-only mounted store might skip indexes entirely.
- **Lazy indexing**: indexes can be built on-demand rather than at insert time, with appropriate query-time fallback to scanning.
- **Transparent backends**: any backend that provides `objects` + transactional column families can host the same Library logic.

## Datum encoding in `objects`

A reminder of what's stored:

```
objects: CID → bytes

Stored bytes are CBOR-encoded Datums:
  - Body Datum:   2-element CBOR array  [Tag-6(head), [bindings]]
  - Record Datum: 3-element CBOR array  [Tag-6(head), [bindings], signature]

Plus content blobs: arbitrary bytes (raw content addressable by CID,
referenced via ~<CID> ContentRefs).
```

Decoder dispatches on array length: 2 elements → Body, 3 elements → Record. Content blobs are not arrays at all; they're whatever the producer wrote. Type confusion is impossible because retrieval is always context-driven (you fetched a CID because some structure pointed at it; that structure tells you what to expect).

For the encoding details, see [Datum](datum.md) and [CG-CBOR](cg-cbor.md).

## What this dissolves from the older model

Several concepts from the pre-Datum-unification storage layer are no longer needed:

- **Separate object kinds** (manifests vs frame bodies vs frame records as different types). Now: one `objects` column, all Datums.
- **Four specific indexes** (ITEMS, HEADS, FRAME_BY_ITEM, RECORD_BY_BODY). Now: forward + reverse + type, more general.
- **TokenDictionary as a separate index**. Now: subsumed by reverse_bindings via type-discriminated encoding.
- **EndorsementsTable** as a runtime structure. Now: the manifest's body bindings ARE the endorsement set, queryable directly.
- **ItemState wrapping**. Now: dissolved with EndorsementsTable.

What persists from the older model:

- One source of truth (`objects`), derived indexes — same principle, refined.
- Transparent large-object support via content references — large blobs live as files or in `objects`; references work the same either way.
- Backend-agnostic Library — RocksDB, MapDB, in-memory, WorkingTreeStore all valid.
- Trust-graph mediation by Librarian — local first, then routed to external sources.

## References

- [Datum](datum.md) — the structural primitive that gets stored
- [Frames](frames.md) — Body/Record/Frame/Manifest semantics
- [Content](content.md) — CIDs, multihash, multibase
- [Query](query.md) — how queries operate over the indexes
- [Fuzzy Matching](fuzzy-matching.md) — downstream similarity layer using WL kernel and embeddings
