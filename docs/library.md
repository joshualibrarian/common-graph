# Library

The **Library** is the local storage engine for a Common Graph node. It manages everything a Librarian knows: item manifests, content blocks, frames, and the indexes that make them queryable.

## Architecture

A Library is composed of five cooperating parts:

```
Library
├── Object Store            # Single content-addressed blob store (CID → bytes)
├── Store Registry          # Additional stores in priority order
├── Indexes                 # ITEMS, HEADS, FRAME_BY_ITEM, RECORD_BY_BODY
├── ItemDirectory           # Fast item location (which store has item X?)
└── TokenDictionary         # Human text → item resolution
```

See [Storage Architecture](storage.md) for the full storage design — object store, indexes, large object support, and the migration from the previous multi-column architecture.

### Object Store

A single content-addressed store: `persist(bytes) → CID`, `fetch(CID) → bytes`. All data — manifests, frame bodies, frame records, content blobs — lives here. The store doesn't interpret content; interpretation happens at higher layers. See [Storage Architecture](storage.md) for details including large object support (inline, store-managed external, and user-named external files).

### Store Registry

A Library can have multiple stores in priority order. When looking up an item, stores are consulted from highest to lowest priority:

```
Store Registry
├── [Priority 1] Working tree store (filesystem-backed items being edited)
├── [Priority 2] Primary store (RocksDB or in-memory)
└── [Priority 3] Peer store (network peers via CG Protocol)
```

This layering lets a Librarian have local working copies overlaid on a persistent store, with peer fallback for items it doesn't have locally. When a `get()` call misses in the local stores, the Librarian can query peers through the [discovery mechanism](network.md#discovery) — concentric ripple search through the social graph.

### Indexes

Four derived indexes, all rebuildable from the object store:

| Index | Key | Purpose |
|-------|-----|---------|
| **ITEMS** | IID \| VID → timestamp | Version history per item |
| **HEADS** | Principal \| IID → VID | Current version per principal |
| **FRAME_BY_ITEM** | ItemID \| Pred \| BodyHash → CID | Unified frame lookup (predicates are items too) |
| **RECORD_BY_BODY** | BodyHash \| SignerKeyID → CID | Attestation tracking |

See [Storage Architecture](storage.md) for key formats, query patterns, and rebuild procedure.

### ItemDirectory

Fast lookup of where an item lives:

```
ItemDirectory {
    locate(iid) → StoreLocation     # Which store has this item?
    register(iid, store)            # Record item location
    isLocal(iid) → boolean          # Is this item in a local store?
}
```

The directory avoids scanning all stores on every lookup. When an item is stored or discovered, its location is recorded.

### TokenDictionary

The **global lexicon** — maps human-readable tokens to sememe and item postings. This is the single source of truth for all token resolution in the system. See [Vocabulary](vocabulary.md) for how this feeds into dispatch and the expression input.

```
TokenDictionary {
    lookup(token, scopes...) --> [Posting]          # Exact match with scope chain
    prefix(prefix, limit, scopes...) --> [Posting]  # Completion candidates
    index(posting)                                   # Register a mapping
}
```

Every posting has a **scope** — an ItemID that says where the mapping is meaningful. The DB key is `<scope><token>`. The scope can be any item: a Language Item, a specific content item, a user. The TokenDictionary doesn't distinguish — it's one mechanism.

Scope is null for universal postings (language-neutral symbols like "m", "kg", "+") that resolve for everyone. The caller provides a **scope chain** (assembled from context: active languages, focused item, user, etc.) and gets back all matching postings.

Token sources:
- **Sememe symbols**: Language-neutral shorthand indexed with null scope
- **Seed vocabulary**: English tokens indexed under `cg:language/eng` at bootstrap
- **Lexicon imports**: WordNet, CILI — each language's lexemes scoped to its Language Item, merged idempotently
- **Frame vocabulary**: EntryVocabulary contributions scoped to their item
- **Assertions**: Named assertion frames (title, alias) scoped to their item

## Storage Backends

The Library abstraction supports multiple backends:

| Backend | Characteristics | Use Case |
|---------|----------------|----------|
| **RocksDB** | Persistent, high-performance, LSM-tree | Production storage |
| **MapDB** | Persistent, lightweight, B-tree | Lighter-weight alternative |
| **SkipList** | In-memory, zero dependencies | Testing and ephemeral use |

All backends implement the same ItemStore interface. The choice is transparent to everything above the storage layer.

### RocksDB Backend

The production backend. Uses column families:

| Column Family | Contents |
|---------------|----------|
| **OBJECTS** | All content-addressed blobs (manifests, bodies, records, content) |
| **ITEMS** | Version history index (IID \| VID → timestamp) |
| **HEADS** | Current version per principal (Principal \| IID → VID) |
| **FRAME_BY_ITEM** | Frame lookup index (ItemID \| Pred \| BodyHash → CID) |
| **RECORD_BY_BODY** | Attestation index (BodyHash \| SignerKeyID → CID) |
| **TOKEN** | Token dictionary entries |
| **ITEM_DIR** | Item directory mappings |

See [Storage Architecture](storage.md) for large object support (inline vs external files).

### Transaction Model

Store operations are wrapped in transactions for consistency:

```
store.runInWriteTransaction(tx → {
    tx.putManifest(manifest)
    tx.putBlock(contentBytes)
    tx.putRelation(relation)
    // All succeed or all fail
})
```

Read operations are non-transactional by default (snapshot isolation via RocksDB's MVCC).

## Bootstrap: Seed Vocabulary

On first boot, the Library is populated with **seed items** — deterministic bootstrap vocabulary that every Common Graph node shares. The bootstrap process:

1. **Scan the classpath** for types and seed declarations
2. **Create seed items** with deterministic IIDs (derived from canonical strings like `"cg:type/item"`)
3. **Create Language Items** — at minimum the English Language Item (`cg:language/eng`) with its initial lexicon containing seed lexemes
4. **Store manifests** for each seed item (timestamp 0, no signature — code-defined)
5. **Register tokens** in the TokenDictionary — lexemes scoped to their Language Item, symbols as universal postings

Seed items include:

| Category | Examples |
|----------|----------|
| **Language items** | English (`cg:language/eng`) — with seed lexicon |
| **Core types** | Item, Signer, Host, Sememe |
| **Frame types** | Log, Dag, Roster, Space, Model, Vault, KeyLog |
| **Value types** | Text, Integer, Decimal, Rational, Boolean, Instant, Quantity |
| **Predicates** | Author, Title, Description, Created, Modified |
| **Semantic relations** | Hypernym, Hyponym, Instance-of, Holonym, Meronym |
| **Verbs** | Create, Edit, Commit, Delete, Describe, Move |
| **Dimensions** | Length, Mass, Time, Temperature, Currency |
| **Units** | Meter, Kilogram, Second, Kelvin, Dollar, Euro |

Because seed IIDs are deterministic, two independently bootstrapped Libraries will have identical IIDs for all seed items. This is how nodes agree on vocabulary without prior coordination — the seed vocabulary is the shared language of the graph.

When a lexicon import runs later (e.g., WordNet for English), new lexemes are **merged idempotently** into the existing Language Item's lexicon. The English Language Item created at seed time grows as imports add words, but its IID and existing mappings remain stable.

## Working Tree Store

The **WorkingTreeStore** provides filesystem-backed storage for items being actively edited. It presents items as working trees (see [Working Trees](working-tree.md)):

```
WorkingTreeStore {
    exists(path) → boolean          # Is there an item at this path?
    load(path) → [FrameEntry]       # Load frame entries from .item/
    save(path, entries)             # Write frame entries to .item/
    commit(path, signer) → Manifest # Build and sign manifest from working tree
}
```

Working tree stores sit at the highest priority in the store registry, so local edits always shadow stored versions.

## Content Lifecycle

See [Storage Architecture](storage.md) for the full content lifecycle — storing (commit and unendorsed frames), retrieving (hydration), querying, and the verification chain.

## References

**External resources:**
- [Content-Addressable Storage](https://en.wikipedia.org/wiki/Content-addressable_storage) — The general pattern
- [Git Object Store](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects) — Similar content-addressed design for version control
- [IPFS](https://ipfs.tech/) — Content-addressed block storage with network distribution
- [RocksDB](https://rocksdb.org/) — LSM-tree storage engine used as the production backend

**Academic foundations:**
- [Merkle 1979 — Hash Trees](references/Merkle%201979%20-%20Secrecy%20Authentication%20and%20Public%20Key%20Systems.pdf) — The hash-tree structure underlying all content addressing
- [Benet 2014 — IPFS](references/Benet%202014%20-%20IPFS%20Content%20Addressed%20Versioned%20P2P%20File%20System.pdf) — Content-addressed Merkle DAG storage
- [Tschudin, Baumann 2019 — Merkle-CRDTs](references/Tschudin%2C%20Baumann%202019%20-%20Merkle-CRDTs.pdf) — Merging Merkle DAG integrity with conflict-free replication
- [Kleppmann 2019 — Local-First Software](references/Kleppmann%202019%20-%20Local-First%20Software.pdf) — Seven ideals for data ownership and offline capability
