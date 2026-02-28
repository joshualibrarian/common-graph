# Items

An **Item** is the fundamental unit of the Common Graph. Everything — documents, users, hosts, conversations, games, applications — is an Item.

An Item is a **versioned, signed, typed container with stable identity**. Every Item carries its own identity, its own history, its own type definition, and its own trust chain. Items don't live at paths or URLs — they exist by identity, and you find them by meaning.

The Item model draws from several traditions: Smalltalk's "everything is an object" with message-passing dispatch (see [references/Kay 1993](references/Kay%201993%20-%20The%20Early%20History%20of%20Smalltalk.pdf)), the Actor model's independent entities communicating through messages (see [references/Hewitt et al 1973](references/Hewitt%2C%20Bishop%2C%20Steiger%201973%20-%20A%20Universal%20Modular%20ACTOR%20Formalism.pdf)), and Engelbart's vision of augmenting human intellect through integrated artifact-language-methodology systems (see [references/Engelbart 1962](references/Engelbart%201962%20-%20Augmenting%20Human%20Intellect.pdf)). Like Bush's memex (see [references/Bush 1945](references/Bush%201945%20-%20As%20We%20May%20Think.pdf)), items are found by meaning and association rather than hierarchical location.

## Anatomy of an Item

An Item has exactly four parts:

| Part | What it is |
|------|-----------|
| **IID** | Stable 32-byte identity that persists across all versions |
| **Manifest** | Signed, immutable snapshot of a specific version |
| **ComponentTable** | Unified table of all content — components, relations, policy |
| **Vocabulary** | Linguistic surface — merged from component vocabularies at runtime |

Everything is in the ComponentTable. Components, relations, policy — all stored as entries in one table, serialized as one CBOR array in the manifest, versioned together. Vocabulary is derived at runtime from component verb definitions and persistent EntryVocabulary contributions (see [Vocabulary](vocabulary.md)).

## Item Identity (IID)

The **IID** (Item ID) is a 32-byte multihash identifier that:

- **Persists across all versions** — edit the content, the IID stays the same
- **Is usually random** — UUID-like uniqueness, no coordination needed
- **Can be deterministic** — computed by hashing a canonical string like `"cg:type/item"`

Deterministic IIDs are how bootstrap vocabulary works. Two independently started nodes compute the same IID for "the concept of an Item" by hashing the same canonical string. No genesis block, no central authority.

```
ItemID.fromString("cg:type/item")     →  always the same 32 bytes
ItemID.fromString("cg:type/relation") →  always the same 32 bytes
ItemID.random()                        →  unique every time
```

## Versions (VID)

Each committed version of an Item is identified by a **VID** (Version ID) — the hash of the manifest's BODY bytes.

- **Deterministic** — same content + same metadata = same VID
- **Immutable** — a VID always refers to exactly one version
- **Verifiable** — re-hash the body and compare

Versions form a history chain (or DAG, if branches exist):

```
V1 (parent: null)
 └── V2 (parent: V1)
      └── V3 (parent: V2)
```

The VID hashes only BODY fields (content), not the full manifest. Signatures are non-BODY fields — the VID is computed first, then signed. BODY scope = content identity. RECORD scope = everything including signatures.

## The ComponentTable

The ComponentTable is the **single source of truth** for what an Item contains. Every piece of content, every relation, every policy — all stored as entries in one table. Relations are just components whose type happens to be `cg:type/relation`.

### Structure

The ComponentTable holds two parallel maps:

```
entries:    HandleID → ComponentEntry    (metadata: type, CID, mounts, identity flag)
live:       HandleID → Object            (decoded instance: the actual Roster, Log, Relation, etc.)
aliasIndex: String   → HandleID          (human names: "vault" → HID, "chat" → HID)
```

**Entries** are the serialized truth — they go into the manifest, get content-addressed, and sync over the network. **Live instances** are the in-memory decoded forms — they exist only at runtime. The alias index is transient convenience for resolving human-friendly names to handles.

### Component Entries

A `ComponentEntry` is the metadata record for one component. New entries are
written in **facet form**:

```
ComponentEntry {
    handle:          HandleID       — local identifier (unique within the item)
    type:            ItemID         — what kind of thing this is (defines codec)
    identity:        boolean        — does this contribute to the VID?
    alias:           String         — human-facing name ("vault", "chat", "roster")

    payload: {
        snapshotCid:     ContentID       — hash of immutable content bytes (nullable)
        streamHeads:     List<ContentID> — heads for append-only logs (nullable)
        streamBased:     boolean         — explicit stream flag
        referenceTarget: ItemID          — containment reference target (nullable)
    }

    config: {
        settings: List<ScopedSetting>    — context-scoped knobs/overrides
        policy:   PolicySet              — per-entry policy metadata
    }

    presentation: {
        layout: { mounts: List<Mount> }  — where this appears (path/surface/spatial)
        skin:   { sceneOverride: ViewNode } — per-entry scene override
    }

    vocabulary: {
        contributions: List<VocabularyContribution> — context-scoped vocab terms
    }
}
```

Legacy flat fields (`snapshotCid`, `streamHeads`, `streamBased`,
`referenceTarget`, `mounts`) have been retired; facet fields are now the
authoritative shape and write path.

This single structure covers every mode of content:

| Mode | What's set | Used for |
|------|-----------|----------|
| **Snapshot** | `payload.snapshotCid` | Immutable, content-addressed. Documents, config, images. |
| **Stream** | `payload.streamHeads`, `payload.streamBased` | Append-only logs. Chat messages, key history, activity feeds. Multiple heads enable branching. |
| **Local-only** | nothing (no CID, not stream, no reference) | Never syncs. Private keys, caches, device-specific state. The absence of content references implies locality. |
| **Reference** | `payload.referenceTarget` | Points to another item by IID. The containment primitive — items "inside" a container are references. |
| **Relation** | `payload.snapshotCid`, `type = Relation.TYPE_ID` | Semantic assertions. Stored like snapshots, typed as relations for query indexing. |

### The identity flag

The `identity` flag on each entry controls whether that component's content contributes to the VID. A vault (local private keys) or a cache doesn't create a new VID when updated — it's registered in the table but its content doesn't affect version identity.

### Mounts

Components can have **mounts** — presentation descriptors that control where and how a component appears:

| Mount type | Purpose |
|-----------|---------|
| `PathMount` | Filesystem-like path (`/documents/readme.md`) |
| `SurfaceMount` | 2D UI placement (region in a surface layout) |
| `SpatialMount` | 3D placement (position/rotation in a space) |

A component can have multiple mounts (like hard links). Components with no mounts are internal entries — they exist in the table but don't appear in navigation.

### Relations in the ComponentTable

Relations are stored as regular component entries with `type = Relation.TYPE_ID`. The handle is derived from the relation's content hash (`"rel:" + cid`), making it unique even when multiple relations share a predicate.

```
ComponentEntry.forRelation(predicate, contentCid, identity)
→ handle:      HandleID.of("rel:" + cid.encodeText())
→ type:        Relation.TYPE_ID
→ alias:       formatted predicate (e.g., "author")
→ snapshotCid: the relation's encoded bytes
```

Relations are additionally stored in the library's relation index during commit for efficient fan-out queries. The ComponentTable remains the source of truth.

## Relations

A `Relation` is a signed semantic assertion — an RDF-like triple with qualifiers:

```
subject → predicate → object
```

- **Subject**: an ItemID (the item making the assertion)
- **Predicate**: an ItemID pointing to a sememe (the meaning of the assertion)
- **Object**: either an ItemID (linking to another item) or a Literal (text, number, date)
- **Qualifiers**: optional map of predicate→value pairs (structured metadata on the assertion)

```
item:Book → AUTHOR    → person:Tolkien
item:Book → TITLE     → "The Hobbit"
item:Book → PUBLISHED → 1937-09-21
```

Relations are **signable** — they bind a body hash to a signer's key and timestamp, so you can verify who asserted what, and when.

### Relation fields on Item types

Item types can declare relation fields that are automatically managed:

```java
@Item.RelationField(predicate = "cg:predicate/author")
private ItemID author;
```

During hydration, the field is populated from the relation in the ComponentTable. During commit, the field value is encoded as a Relation, stored in the library index, and added to the ComponentTable as a component entry.

## Item Types

Every Item has a **type** — itself an Item identified by a sememe. The type is declared with `@Type`:

```java
@Type(value = "cg:type/document", glyph = "📄", color = 0x4488CC)
public class Document extends Item { ... }
```

The type defines:

| Aspect | How |
|--------|-----|
| **Identity** | `@Type(value = "cg:type/...")` — deterministic IID for the type item |
| **Display** | `glyph`, `color`, `shape` — visual identity |
| **Components** | `@Item.ContentField` on fields — what content this type includes |
| **Relations** | `@Item.RelationField` on fields — what semantic links this type makes |
| **Verbs** | `@Verb` on methods — what actions this type supports |
| **Scene** | `@Scene.*` annotations — unified 2D + 3D rendering |

The class is the schema. The type definition IS the Item — no separate handler, no descriptor file, no schema registry.

## Item State

An Item's versioned state is encapsulated in `ItemState`:

```
ItemState {
    content: ComponentTable    — everything the item contains
}
```

ItemState wraps the ComponentTable so the Manifest has a named field for "the state of this version." ItemState is shared between the live Item and the Manifest — the Item mutates its state during editing, and the Manifest snapshots it at commit time.

## The Manifest

A Manifest is the **signed, immutable declaration** of an Item version:

```
Manifest {
    version:    int               — format version (currently 1)
    iid:        ItemID            — which item this is
    parents:    List<VersionID>   — parent versions (history chain)
    type:       ItemID            — item type
    state:      ItemState         — all content (the ComponentTable)
    ─── non-BODY fields (excluded from VID hash) ───
    authorKey:  SigningPublicKey   — who signed this
    signature:  Signing           — the signature itself
}
```

The BODY/non-BODY split: `authorKey` and `signature` are excluded from the body hash that produces the VID.

1. Compute the VID by hashing the BODY fields
2. Sign the VID with the author's key
3. Attach the signature as a non-BODY field

The VID is deterministic from content. The signature proves who authored that content. No circular dependency.

## ID Types

All IDs are multihash values — self-describing hashes that include the algorithm used. 256-bit (32 bytes) everywhere.

| ID | Derived from | Purpose |
|----|-------------|---------|
| **ItemID** | Random or `hash(canonical_string)` | Stable identity across versions |
| **VersionID** | `hash(manifest.BODY)` | Identifies a specific version |
| **ContentID** | `hash(content_bytes)` | Content-addresses a block of bytes |
| **HandleID** | `hash(handle_string)` | Local identifier within an item |
| **RelationID** | `hash(relation.BODY)` | Identifies a specific relation |

All inherit from `HashID`, which implements `Canonical` for serialization. `VersionID`, `ContentID`, and `RelationID` also implement `BlockID` for use as keys in block storage.

## Item Lifecycle

### Creation

```
new Item(librarian)
 → random IID generated
 → ItemState created with empty ComponentTable
 → initializeFreshComponents():
     for each @Item.ContentField:
       1. Create default instance via Components.createDefault()
       2. Build ComponentEntry (snapshot/stream/local-only)
       3. Add entry + live instance to ComponentTable
 → onFullyInitialized():
     1. initBuiltinComponents() — add PolicySet
     2. buildVocabulary() — collect verb definitions, merge EntryVocabulary contributions
     3. populateRelationTable() — add @Item.RelationField values as entries
     4. syncFieldValuesToTable() — sync subclass field initializers
```

### Hydration (Loading)

```
Item loaded from Manifest
 → ComponentEntries extracted from Manifest.state.content
 → hydrate():
     Phase 1: For each ComponentEntry:
       1. Fetch content by CID from the store
       2. Decode via Components.decode() or Canonical.decodeBinary()
       3. Store live instance in ComponentTable
     Phase 2: Bind @ContentField fields from table
     Phase 3: Invoke initComponent() on all Component instances
 → onFullyInitialized()
```

### Editing

```
item.edit()                    — enter edit mode
item.addComponent(...)         — modify the ComponentTable
item.component("chat").add()   — modify a live component
```

Edit mode is a flag — it doesn't create a copy. You mutate the item's state directly, and `dirty` tracks that changes exist.

### Commit

```
item.commit(signer)
 → scanAndBindFields():
     1. For each @ContentField: encode value → CID → update ComponentEntry
     2. For each @RelationField: build Relation → store in library index → add to ComponentTable
 → Build Manifest (iid, type, parents, state)
 → manifest.sign(signer) — sign BODY bytes with signer's key
 → storeManifest() — serialize and store via librarian
 → base = manifest.vid(), dirty = false
```

### Persist (non-versioned save)

```
item.persist()
 → For each ComponentEntry:
     1. Encode content
     2. Store bytes in the item's store
     3. Update entry CID
 → Save metadata (no manifest, no VID, no signature)
 → dirty = false
```

Persist saves content without creating a version — for auto-save and work-in-progress.

## Components

Components can be **any object** — there's no required base class. The `Component` interface is optional, providing lifecycle hooks and display methods:

```java
public interface Component {
    default void initComponent(Item owner) {}     // Called after hydration
    default String displayToken() { ... }          // Tree label
    default String emoji() { ... }                 // Icon
    default boolean isExpandable() { ... }         // Has children?
}
```

The `@Type` annotation provides type identity. `Components.encode()/decode()` handles serialization. The `Component` interface is only for items that need lifecycle hooks or display customization.

### Built-in Components

Every Item always has one built-in component, added during `initBuiltinComponents()`:

| Component | Handle | Purpose |
|-----------|--------|---------|
| **PolicySet** | `"policy"` | Per-item authorization rules |

PolicySet is always `identity=true` — changing who can do what changes the version.

**Vocabulary is derived at runtime.** An item's vocabulary — its linguistic surface (verbs it handles, nouns it recognizes, proper names for its components) — is built by merging:
1. Verb definitions on the item type (code layer)
2. Verb definitions on each component type (code layer)
3. `EntryVocabulary` contributions on each ComponentEntry (persistent user layer)

See [Vocabulary](vocabulary.md) for the full vocabulary system.

## Field Annotations

### @Item.ContentField

Declares a component field on an Item type:

```java
@Item.ContentField(alias = "chat", stream = true)
private Log chatLog;
```

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `handleKey` | `""` (derived from field name) | Seed for HandleID computation |
| `alias` | `""` | Human-facing name in the tree |
| `path` | `""` | Filesystem path for local-only components |
| `snapshot` | `true` | Store as immutable snapshot? |
| `stream` | `false` | Store as append-only stream? |
| `localOnly` | `false` | Path-based, never synced? |
| `identity` | `true` | Contributes to VID? |

### @Item.RelationField

Declares a relation that this item endorses:

```java
@Item.RelationField(predicate = "cg:predicate/author")
private ItemID author;
```

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `predicate` | (required) | The relation's predicate sememe |
| `canonical` | `true` | Include in manifest (for indexing)? |

### @Item.Seed

Marks a static field as a seed instance for bootstrap vocabulary:

```java
@Item.Seed
public static final Item ITEM_TYPE = new Item(ItemID.fromString("cg:type/item"));
```

Seed items have deterministic IIDs, timestamp 0, and no signature. They're imported into the Library on first boot via classpath scanning.

## Composable Items

Items compose behavior from components. There are no special "chat room" or "shared folder" types baked into the system — everything is assembled from primitives:

| Want | Compose |
|------|---------|
| Chat room | Item + Roster + Log (stream) |
| Shared folder | Item + mounted content components |
| Game | Item + GameComponent (Dag stream) + Roster |
| User profile | Item + KeyLog (stream) + Vault (local) |
| Document | Item + content snapshot + relations (author, title) |

The same ComponentTable holds all of these. A "chat room" is just an item where one of the entries happens to be a stream-based Log component.

## Vocabulary

Every Item has a vocabulary — its linguistic surface. Verbs, nouns, proper names for components — all resolved through sememes:

```
"create" (English)  --+
"crear"  (Spanish)  --+--> same sememe --> same verb --> ActionResult
```

The vocabulary is derived at runtime from:
- Verb definitions on the item type and its component types (code layer)
- `EntryVocabulary` contributions on component entries (persistent user layer)

Typing into an Item's prompt dispatches through the vocabulary system. See [Vocabulary](vocabulary.md) for the full dispatch pipeline, expression input, and customization.

## Working Tree Representation

An Item can be presented as a filesystem working tree — see [Working Trees](working-tree.md):

```
my-item/
├── README.md              # Mounted content (editable)
├── data/
│   └── config.json
└── .item/
    ├── iid                # Item identity
    ├── head/              # Working state
    ├── manifests/         # Immutable version snapshots
    ├── channels/          # Named branches
    └── content/           # Content blocks (by CID)
```

The working tree is a view of the ComponentTable — path mounts determine what appears where. Edit the mounted content, then `commit()` to mint a new version.
