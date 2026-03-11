# Frame Types

**Frame types** are the typed building blocks inside Items. Each frame in an item's FrameTable has a type that defines its encoding, behavior, and capabilities. Frame types range from simple data holders (text, config) to complex interactive objects (game boards, chat streams, key stores).

## Frame Entry Structure

Every frame in a manifest is described by a **FrameEntry** with a faceted structure:

```
FrameEntry {
    handle:    HandleID     # Stable identifier within the item
    type:      ItemID       # Frame type (defines codec/behavior)
    identity:  boolean      # Does this contribute to item identity?

    payload: {
        snapshotCid:     ContentID?      # Hash of immutable content bytes
        streamHeads:     [ContentID]     # Heads for append-only logs
        streamBased:     boolean         # Explicit stream flag
        referenceTarget: ItemID?         # Containment reference target
    }

    config: {
        settings: [ScopedSetting]        # Context-scoped knobs/overrides
        policy:   PolicySet?             # Per-entry policy metadata
    }

    presentation: {
        layout: { mounts: [Mount] }      # Where this appears (path/surface/spatial)
        skin:   { sceneOverride: ViewNode? } # Per-entry scene override
    }

    vocabulary: {
        contributions: [VocabularyContribution]  # Vocabulary terms this frame provides
    }
}
```

At runtime, the FrameEntry is metadata. The actual typed value is decoded from the store and held as a **live instance** alongside the entry.

## Handles (HID)

A **HandleID** is the frame's stable name within its Item:

- Persists across versions (content changes, handle stays)
- Human-readable text or hash-based
- Scoped to the item (not globally unique)
- Used in mount paths, verb dispatch, and frame references

## Types

Every frame has a **type** — an ItemID pointing to a type definition. The type determines:

| Aspect | What It Defines |
|--------|-----------------|
| **Encoding** | How to serialize/deserialize content |
| **Capabilities** | Snapshot, stream, or both |
| **Display** | Glyph, color, shape for rendering |
| **Verbs** | Actions available on this frame type |
| **Local-only** | Whether this frame is ever synced |

Types are themselves Items (sememes), so they participate in the graph: they can be queried, related, and extended.

## Snapshot vs Stream

Frames hold content in one of two modes:

### Snapshot Frames

Immutable content addressed by CID:
- Documents, images, configuration, models
- Replace entirely on update (new CID each time)
- `snapshotCid` points to the content block

### Stream Frames

Append-only logs addressed by head CIDs:
- Chat messages, key history, activity logs, roster changes
- New entries append; existing entries are immutable
- `streamHeads` lists current tip(s)
- Can have multiple heads (forks) — see [Streams](streams.md)

### Authority: Snapshot vs Stream

Some frame types support both modes. The **authority** determines which is the source of truth:

| Authority | Truth | Other |
|-----------|-------|-------|
| **SNAPSHOT** | `snapshotCid` is canonical | Stream is optional history |
| **STREAM** | Stream entries are canonical | Snapshot is derived/materialized |

A CRDT document might use STREAM authority — edits append to the stream, and the snapshot is a materialized view.

## Identity Frames

Frames marked `identity: true` contribute to the Item's semantic identity:
- Changes to identity frames are meaningful changes to "what this Item IS"
- The manifest hash includes all frames, but identity flags signal intent

Frames marked `identity: false` don't affect identity:
- Caches, indexes, local state, derived views
- Can change without changing the Item's core meaning

## Core Frame Types

### Data Types

| Type | Description | Mode |
|------|-------------|------|
| **Log** | Generic append-only log for linear history | Snapshot or Stream |
| **Dag** | Directed acyclic graph for sync/merge scenarios (multi-writer) | Stream |
| **Model** | Generic key-value model | Snapshot |
| **QueryComponent** | Stored queries and views | Snapshot |
| **ExpressionComponent** | Evaluable expressions | Snapshot |
| **ScriptComponent** | General-purpose code in external languages (Groovy, JS, WASM) | Snapshot |
| **BytecodeComponent** | Compiled JVM bytecode, loaded via GraphClassLoader | Snapshot |
| **SurfaceTemplateComponent** | Display metadata (glyph, color, shape) + compiled scene template | Snapshot |

### Collaboration Types

| Type | Description | Mode |
|------|-------------|------|
| **Roster** | Set of members with join/leave tracking | Stream |
| **Space** | Spatial coordinate map (positions in 2D/3D) | Stream |

### Security Types

| Type | Description | Mode | Local-Only |
|------|-------------|------|------------|
| **Vault** | Private key storage | N/A | **Yes** (never synced) |
| **KeyLog** | Public key history (add, revoke, rotate) | Stream | No |
| **CertLog** | Certificate/attestation history | Stream | No |

### How Vault Differs

The Vault is a special case: a **local-only** frame that never leaves the device. Private keys are generated inside the Vault and all cryptographic operations (signing, decryption) happen in-place — the key material never leaves.

Vault backends provide different security guarantees:

| Backend | Security Model |
|---------|---------------|
| **In-memory** | Ephemeral (testing only) |
| **Software (PKCS12)** | File-based, encrypted at rest |
| **Keychain** | OS-managed secure storage (macOS Keychain, etc.) |
| **TPM** | Hardware-bound keys (Trusted Platform Module) |
| **PKCS#11** | Hardware security tokens |

The choice of vault backend is a local runtime decision — it doesn't affect the graph protocol or how the Signer appears to other nodes.

## Mounts

Frames can declare **mounts** — presentation positions that determine where a frame appears in different views. A single frame can have multiple mounts of different types, appearing simultaneously in tree views, 2D layouts, and 3D spaces.

### Mount Types

| Type | Fields | Description |
|------|--------|-------------|
| **PathMount** | `path` | Tree hierarchy position (`"/documents/notes"`) |
| **SurfaceMount** | `region`, `order` | Named 2D region placement (`"sidebar"`, order 3) |
| **SpatialMount** | `x, y, z, qx, qy, qz, qw` | 3D position and rotation (meters, quaternion) |

PathMounts canonicalize paths: leading slash ensured, trailing slash removed, `.` and `..` resolved, multiple slashes collapsed.

SpatialMounts use quaternion rotation (identity: `0, 0, 0, 1`). A 4-element form `[x, y, z]` assumes identity rotation.

See [Presentation](presentation.md) for how mounts drive the rendering pipeline.

## Frame Vocabulary

Each frame has a **vocabulary facet** (`EntryVocabulary`) that holds persistent vocabulary contributions. This is how frames customize an item's linguistic surface.

### VocabularyContribution

```
VocabularyContribution {
    trigger:  SememeRef OR LiteralToken   # What activates this entry
    target:   SememeID OR Expression      # What it resolves to
    scope:    string                      # Scope within the frame ("/" = root)
}
```

The trigger is **either** a sememe reference (pointing to an existing sememe that has its own language tokens) **or** a literal token string (a proper name, optionally with a language tag). It cannot be both — if you want to add tokens to a sememe, you do so on the sememe itself.

### Frame Names as Vocabulary

A frame's **proper noun** — what it's called in the UI and in expressions — is a vocabulary contribution. The frame's name is a `VocabularyContribution` with scope "/" and the appropriate trigger:

```
# Name via literal token (specific name):
VocabularyContribution {
    trigger: "notes"             # Literal proper noun
    target:  <this frame>        # Registers this name
    scope:   "/"
}

# Name via sememe (language-agnostic):
VocabularyContribution {
    trigger: Sememe(cg:concept/notes)   # Has tokens in every language
    target:  <this frame>
    scope:   "/"
}
```

Proper nouns can carry a language tag ("France" in English, "Frankreich" in German) or be language-neutral (a username, a code like "note-423").

### Verb Registration

Frames register which verb sememes they handle:

```
VocabularyContribution {
    trigger: Sememe(cg.verb:send)    # "I handle the send verb"
    target:  Sememe(cg.verb:send)    # Simple capability registration
    scope:   "/"
}
```

### Custom Aliases

Users can add aliases that map tokens to existing verbs:

```
VocabularyContribution {
    trigger: "post"                 # Literal token
    target:  Sememe(cg.verb:create) # Maps "post" to "create" in this frame
    scope:   "/"
}
```

### Scripted Expressions

Aliases can target expressions instead of single sememes:

```
VocabularyContribution {
    trigger: "deploy"
    target:  Expression("commit then push to production")
    scope:   "/"
}
```

See [Vocabulary](vocabulary.md) for how expressions are parsed and executed recursively through the same resolution pipeline.

### Vocabulary and Item Hydration

When an item is opened, its vocabulary is rebuilt from all frames:

1. Collect verb definitions from the item type (code layer)
2. For each frame:
   a. Collect verb definitions from the frame type (code layer)
   b. Load `EntryVocabulary` contributions (persistent user layer)
3. Merge: user-layer entries overlay code-layer entries (user wins on conflict)
4. Register scoped tokens in the TokenDictionary for discoverability

The item's vocabulary is the **union** of all its frames' vocabularies.

## Local-Only Frames

Some frame types are **local-only** — they are never synced, replicated, or shared:

- Stored in the item's local directory, not the content store
- Not included in sync/replication
- Used for private keys, caches, device-specific state, local databases
- The Vault is the canonical example

## Selectors

**Selectors** address fragments within frame content:

| Selector | Format | Example | Use |
|----------|--------|---------|-----|
| Byte span | `[start-end]` | `[120-180]` | Text/binary ranges |
| JSON path | `[$.path]` | `[$.users[0].name]` | Structured data (future) |
| Time range | `[t0-t1]` | `[00:30-01:45]` | Media content (future) |

Selectors are type-specific — the frame type determines which selectors are valid and how they're interpreted.

## Frame Lifecycle

### Creation

When an Item is created, its type determines which frames are initialized:

1. The Item's type declaration lists required frame fields via `@Item.Frame`
2. Each field specifies a handle, frame type, and mode (snapshot/stream/local-only)
3. Default instances are created and populated
4. Live instances are bound to the Item's fields

### Hydration

When an Item is loaded from a manifest:

1. FrameEntries are read from the manifest
2. For each entry, content bytes are fetched from the store by CID
3. Bytes are decoded via the frame type's decoder
4. Live instances are stored and bound to fields
5. Each Component's initialization callback is invoked

### Commit

When an Item is committed:

1. Live frame values are encoded to bytes via their type's encoder
2. Content bytes are stored, producing CIDs
3. FrameEntries are updated with new CIDs
4. All entries are included in the new Manifest
5. The Manifest is signed and stored

## Creating Frames

### In a Working Tree

Edit `.item/head/frames/<hid>.json`:

```json
{
  "handle": "readme",
  "type": "cg:type/plainText",
  "identity": true
}
```

Then edit the mounted content at the mount path, and commit to mint a new version.

### Programmatically

Frames are typically created by declaring fields on an Item type with `@Item.Frame`. The type system automatically creates, hydrates, and commits frame values as part of the Item lifecycle.

Frames can also be added dynamically at runtime — the FrameTable supports adding and removing entries between commits.

## Schema-Driven Editing

Any Canonical type can be edited through a schema-driven form. The `editing` package in `:core` provides:

- **`EditModel`** — wraps a mutable `Canonical` instance as a `SceneModel`, handles field-level events (`toggle:<field>`, `select:<field>`, `set:<field>`)
- **`CanonicalEditorSurface`** — procedural surface that renders type-appropriate widgets per `@Canon` field (toggles for booleans, option lists for enums, text display for strings/numbers)

`CanonicalSchema.FieldSchema` provides the type introspection (`isBoolean()`, `isEnum()`, `displayName()`, `setValue()`, etc.) that drives widget selection. See [Presentation](presentation.md) §Schema-Driven Editing for full details.

## Policy and Access Control

Items carry a `PolicySet` frame that defines access rules. The `ItemPolicyResolver` resolves policy subjects against live item state:

| Subject | Resolution |
|---------|------------|
| `"owner"` | Checks `PolicySet.authority().ownerId()` |
| `"participants"` | Checks membership in the item's `Roster` frame |
| `"hosts"` | Compares against the local librarian's IID |
| Explicit ID | Direct equality check |

Policy checks: `PolicyEngine.check(accessPolicy, resolver, itemId, callerId, action)`.
