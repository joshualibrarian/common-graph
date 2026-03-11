# Items

An **Item** is the fundamental unit of the Common Graph. Everything — documents, users, hosts, conversations, games, applications, and even compiled code — is an Item.

An Item is a **versioned, signed, typed collection of frames with stable identity**. Every Item carries its own identity, its own history, its own type definition, and its own trust chain. Items don't live at paths or URLs — they exist by identity, and you find them by meaning.

The Item model draws from several traditions: Smalltalk's "everything is an object" with message-passing dispatch (see [references/Kay 1993](references/Kay%201993%20-%20The%20Early%20History%20of%20Smalltalk.pdf)), the Actor model's independent entities communicating through messages (see [references/Hewitt et al 1973](references/Hewitt%2C%20Bishop%2C%20Steiger%201973%20-%20A%20Universal%20Modular%20ACTOR%20Formalism.pdf)), and Engelbart's vision of augmenting human intellect through integrated artifact-language-methodology systems (see [references/Engelbart 1962](references/Engelbart%201962%20-%20Augmenting%20Human%20Intellect.pdf)). Like Bush's memex (see [references/Bush 1945](references/Bush%201945%20-%20As%20We%20May%20Think.pdf)), items are found by meaning and association rather than hierarchical location.

## Anatomy of an Item

An Item has exactly three parts:

| Part | What it is |
|------|-----------|
| **IID** | Stable 32-byte identity that persists across all versions |
| **Manifest** | Signed, immutable snapshot of a specific version |
| **FrameTable** | All content — every frame the item contains |

Everything is in the FrameTable. Content, metadata, streams, policy — all stored as frame entries in one table, serialized as one CBOR array in the manifest, versioned together. Vocabulary is derived at runtime from frame type verb definitions and persistent EntryVocabulary contributions (see [Vocabulary](vocabulary.md)).

See [Frames](frames.md) for the frame primitive itself — the single data model unit that unifies content, assertions, properties, streams, and more.

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

## The FrameTable

The FrameTable is the **single source of truth** for what an Item contains. Every frame — content, streams, properties, policy — stored as entries in one table.

### Structure

The FrameTable holds two parallel maps:

```
entries:    HandleID → FrameEntry     (metadata: type, CID, mounts, identity flag)
live:       HandleID → Object         (decoded instance: the actual Roster, Log, etc.)
aliasIndex: String   → HandleID       (human names: "vault" → HID, "chat" → HID)
```

**Entries** are the serialized truth — they go into the manifest, get content-addressed, and sync over the network. **Live instances** are the in-memory decoded forms — they exist only at runtime. The alias index is transient convenience for resolving human-friendly names to handles.

### Frame Entries

A `FrameEntry` is the metadata record for one frame. Entries use a **faceted structure**:

```
FrameEntry {
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

This single structure covers every mode of content:

| Mode | What's set | Used for |
|------|-----------|----------|
| **Snapshot** | `payload.snapshotCid` | Immutable, content-addressed. Documents, config, images. |
| **Stream** | `payload.streamHeads`, `payload.streamBased` | Append-only logs. Chat messages, key history, activity feeds. Multiple heads enable branching. |
| **Local-only** | nothing (no CID, not stream, no reference) | Never syncs. Private keys, caches, device-specific state. |
| **Reference** | `payload.referenceTarget` | Points to another item by IID. The containment primitive. |

### The identity flag

The `identity` flag on each entry controls whether that frame's content contributes to the VID. A vault (local private keys) or a cache doesn't create a new VID when updated — it's registered in the table but its content doesn't affect version identity.

### Mounts

Frames can have **mounts** — presentation descriptors that control where a frame appears in different views:

| Mount type | Purpose |
|-----------|---------|
| `PathMount` | Filesystem-like path (`/documents/readme.md`) |
| `SurfaceMount` | 2D UI placement (region in a surface layout) |
| `SpatialMount` | 3D placement (position/rotation in a space) |

A frame can have multiple mounts (like hard links). Frames with no mounts are internal entries — they exist in the table but don't appear in navigation.

## Frames

A **frame** is the single primitive of Common Graph. Everything an item contains is a frame — content, metadata, streams, assertions. See [Frames](frames.md) for the complete frame model, including:

- The body/record split (assertion vs attestation)
- FrameKeys (compound semantic addresses)
- Endorsed vs unendorsed frames
- Queries as incomplete frames
- Config cascade (scene + policy)

### Endorsed and Unendorsed

**Endorsed frames** are included in the item's manifest — the owner commits and signs them. Title, content, roster — these are endorsed.

**Unendorsed frames** are attached by others, independently signed. Likes, comments, spam labels, trust attestations — these are unendorsed.

The structural difference is only manifest inclusion. Same frame format, same hash mechanism, same signing mechanism.

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
| **Frames** | `@Item.Frame` on fields — what frames this type includes |
| **Verbs** | `@Verb` on methods — what actions this type supports |
| **Scene** | `@Scene.*` annotations — unified 2D + 3D rendering |

The class is the schema. The type definition IS the Item — no separate handler, no descriptor file, no schema registry.

## Item State

An Item's versioned state is encapsulated in `ItemState`:

```
ItemState {
    frames: FrameTable    — everything the item contains
}
```

ItemState wraps the FrameTable so the Manifest has a named field for "the state of this version." ItemState is shared between the live Item and the Manifest — the Item mutates its state during editing, and the Manifest snapshots it at commit time.

## The Manifest

A Manifest is the **signed, immutable declaration** of an Item version:

```
Manifest {
    version:    int               — format version (currently 1)
    iid:        ItemID            — which item this is
    parents:    List<VersionID>   — parent versions (history chain)
    type:       ItemID            — item type
    state:      ItemState         — all content (the FrameTable)
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

All inherit from `HashID`, which implements `Canonical` for serialization. `VersionID` and `ContentID` also implement `BlockID` for use as keys in block storage.

## Item Lifecycle

### Creation

```
new Item(librarian)
 → random IID generated
 → ItemState created with empty FrameTable
 → initializeFreshComponents():
     for each @Item.Frame field:
       1. Create default instance via Components.createDefault()
       2. Build FrameEntry (snapshot/stream/local-only)
       3. Add entry + live instance to FrameTable
 → onFullyInitialized():
     1. initBuiltinComponents() — add PolicySet
     2. buildVocabulary() — collect verb definitions, merge EntryVocabulary contributions
```

### Hydration (Loading)

```
Item loaded from Manifest
 → FrameEntries extracted from Manifest.state.frames
 → hydrate():
     Phase 1: For each FrameEntry:
       1. Fetch content by CID from the store
       2. Decode via Components.decode() or Canonical.decodeBinary()
       3. Store live instance in FrameTable
     Phase 2: Bind @Item.Frame fields from table
     Phase 3: Invoke initComponent() on all Component instances
 → onFullyInitialized()
```

### Editing

```
item.edit()                    — enter edit mode
item.addComponent(...)         — modify the FrameTable
item.component("chat").add()   — modify a live component
```

Edit mode is a flag — it doesn't create a copy. You mutate the item's state directly, and `dirty` tracks that changes exist.

### Commit

```
item.commit(signer)
 → scanAndBindFields():
     For each @Item.Frame field: encode value → CID → update FrameEntry
 → Build Manifest (iid, type, parents, state)
 → manifest.sign(signer) — sign BODY bytes with signer's key
 → storeManifest() — serialize and store via librarian
 → base = manifest.vid(), dirty = false
```

### Persist (non-versioned save)

```
item.persist()
 → For each FrameEntry:
     1. Encode content
     2. Store bytes in the item's store
     3. Update entry CID
 → Save metadata (no manifest, no VID, no signature)
 → dirty = false
```

Persist saves content without creating a version — for auto-save and work-in-progress.

## Frame Types

Frames can hold **any typed object**. The `Component` interface is optional, providing lifecycle hooks and display methods:

```java
public interface Component {
    default void initComponent(Item owner) {}     // Called after hydration
    default String displayToken() { ... }          // Tree label
    default String emoji() { ... }                 // Icon
    default boolean isExpandable() { ... }         // Has children?
}
```

The `@Type` annotation provides type identity. `Components.encode()/decode()` handles serialization. The `Component` interface is only for frame types that need lifecycle hooks or display customization.

### Built-in Frame

Every Item always has one built-in frame, added during `initBuiltinComponents()`:

| Frame | Handle | Purpose |
|-------|--------|---------|
| **PolicySet** | `"policy"` | Per-item authorization rules |

PolicySet is always `identity=true` — changing who can do what changes the version.

**Vocabulary is derived at runtime.** An item's vocabulary — its linguistic surface (verbs it handles, nouns it recognizes, proper names for its frames) — is built by merging:
1. Verb definitions on the item type (code layer)
2. Verb definitions on each frame's type (code layer)
3. `EntryVocabulary` contributions on each FrameEntry (persistent user layer)

See [Vocabulary](vocabulary.md) for the full vocabulary system.

## Field Annotations

### @Item.Frame

Declares a frame field on an Item type:

```java
@Item.Frame(handle = "chat", stream = true)
private Log chatLog;

@Item.Frame(key = {"cg.core:name"}, endorsed = false)
private String name;
```

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `key` | `{}` | Semantic FrameKey tokens (sememe canonical keys) |
| `handle` | `""` (derived from field name) | Literal handle for HandleID computation |
| `path` | `""` | Mount path for presentation |
| `snapshot` | `true` | Store as immutable snapshot? |
| `stream` | `false` | Store as append-only stream? |
| `localOnly` | `false` | Path-based, never synced? |
| `identity` | `true` | Contributes to VID? |
| `endorsed` | `true` | Include in manifest? (false = independently signed) |

### @Item.Seed

Marks a static field as a seed instance for bootstrap vocabulary:

```java
@Item.Seed
public static final Item ITEM_TYPE = new Item(ItemID.fromString("cg:type/item"));
```

Seed items have deterministic IIDs, timestamp 0, and no signature. They're imported into the Library on first boot via classpath scanning.

## Composable Items

Items compose behavior from typed frames. There are no special "chat room" or "shared folder" types baked into the system — everything is assembled from primitives:

| Want | Compose |
|------|---------|
| Chat room | Item + Roster (stream) + Log (stream) |
| Shared folder | Item + mounted content frames |
| Game | Item + GameComponent (Dag stream) + Roster |
| User profile | Item + KeyLog (stream) + Vault (local) |
| Document | Item + content snapshot + assertion frames (author, title) |

The same FrameTable holds all of these. A "chat room" is just an item where one of the frames happens to be a stream-based Log.

## Vocabulary

Every Item has a vocabulary — its linguistic surface. Verbs, nouns, proper names for frames — all resolved through sememes:

```
"create" (English)  --+
"crear"  (Spanish)  --+--> same sememe --> same verb --> ActionResult
```

The vocabulary is derived at runtime from:
- Verb definitions on the item type and its frame types (code layer)
- `EntryVocabulary` contributions on frame entries (persistent user layer)

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

The working tree is a view of the FrameTable — path mounts determine what appears where. Edit the mounted content, then `commit()` to mint a new version.
