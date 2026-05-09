# Items

In the Common Graph, **frames** hold all the data, indexed with semantic keys. However, an **Item** is the fundamental unit of coherent meaning. Everything — documents, users, hosts, conversations, games, applications, and even compiled code — is an Item. Anything that only makes sense **as a whole**, which frames coalesce into.

An Item is a **versioned, signed container of frames with stable identity**. Every Item carries its own identity, its own history, and its own trust chain. Items don't live at paths or URLs — they exist by identity, and you find them by meaning.

The Item model draws from several traditions: Smalltalk's "everything is an object" with message-passing dispatch ([Kay 1993](references/Kay%201993%20-%20The%20Early%20History%20of%20Smalltalk.pdf)), the Actor model's independent entities communicating through messages ([Hewitt et al 1973](references/Hewitt%2C%20Bishop%2C%20Steiger%201973%20-%20A%20Universal%20Modular%20ACTOR%20Formalism.pdf)), and Engelbart's vision of augmenting human intellect through integrated artifact-language-methodology systems ([Engelbart 1962](references/Engelbart%201962%20-%20Augmenting%20Human%20Intellect.pdf)). Like Bush's memex ([Bush 1945](references/Bush%201945%20-%20As%20We%20May%20Think.pdf)), items are found by meaning and association rather than hierarchical location.

## Anatomy of an Item

An **Item** is a persistent identity — an **IID** (Item ID) — with a history of **Manifests**, each a signed, immutable snapshot of a specific version. The IID persists across all versions. You find an item by its IID; you read a specific version by its manifest. An item may have multiple heads — you choose which version to work from, fork from, or build upon.

A manifest contains:

| Manifest field | What it holds |
|----------------|---------------|
| **IID** | Which item this version belongs to |
| **Endorsements** | The item's frames — every endorsed assertion, keyed by FrameKey |
| **Bindings** | Item-level role-keyed values, each carrying an identity flag |
| **Implementation** | Platform + type name (e.g., Java + `ChessItem`) — a distinguished binding |
| **Parents** | Version history chain (list of prior VIDs) |
| **Author + Signature** | Who signed this version and the cryptographic proof |

Everything — text, metadata, streams, policy — is either an endorsed frame or an item-level binding. Vocabulary is derived at runtime by scanning the item's frames for indexed string bindings.

See [Frames](frames.md) for the frame primitive itself — the single data model unit that unifies all content, assertions, properties, streams, and more.

## Item Identity (IID)

The **IID** (Item ID) is a 32-byte multihash identifier that:

- **Persists across all versions** — edit the content, the IID stays the same
- **Is usually random** — UUID-like uniqueness, no coordination needed
- **Can be deterministic** — computed by hashing a canonical string like `"cg.sememe:item"`

Deterministic IIDs are how bootstrap vocabulary works. Two independently started nodes compute the same IID for "the concept of an Item" by hashing the same canonical string. No genesis block, no central authority.

```
ItemID.fromString("cg.sememe:item")     ->  always the same 32 bytes
ItemID.fromString("cg.sememe:chess")    ->  always the same 32 bytes
ItemID.random()                         ->  unique every time
```

## Versions

Each committed version of an Item is identified by the **content hash of the manifest body** — a ContentID computed from the BODY fields.

- **Deterministic** — same content + same metadata = same version hash
- **Immutable** — a version hash always refers to exactly one version
- **Verifiable** — re-hash the body and compare

Versions form a history chain (or DAG, if branches exist):

```
V1 (parent: null)
 +-- V2 (parent: V1)
      +-- V3 (parent: V2)
```

The version hash covers only BODY fields (content), not the full manifest. Signatures are non-BODY fields — the hash is computed first, then signed. BODY scope = content identity. RECORD scope = everything including signatures.

## The Manifest

A Manifest is the **signed, immutable declaration** of an Item version. Like frames, manifests split into **body** (content identity) and **record** (attestation envelope):

```
Manifest {
    --- BODY (hashed to produce the VID) ---
    version:          int                     -- manifest format version (currently 1)
    iid:              ItemID                  -- which item this is
    parents:          List<ContentID>         -- parent version hashes (history chain)
    implementation:   Binding                 -- platform + type name (e.g., Java + "ChessItem")
    endorsements:     List<FrameEndorsement>  -- the item's endorsed frames
    bindings:         List<Binding>           -- identity bindings (affect version identity)

    --- RECORD (attestation envelope, excluded from VID) ---
    bindings:         List<Binding>           -- non-identity bindings (config, presentation)
    authorKey:        SigningPublicKey         -- who signed this
    signature:        Signing                 -- the signature itself
}
```

Bindings are conceptually one set — each binding carries an identity flag. At serialization time, they split across the body/record boundary: identity bindings are BODY (they affect the VID), non-identity bindings are RECORD (config, presentation overrides — they don't change the version).

### Endorsed Frames

The manifest's endorsements reference the item's **endorsed frames** — each a `FrameEndorsement` carrying a FrameKey, bodyHash, and mounts. An endorsement always covers the frame body; it may optionally also endorse a specific frame record — pinning a particular presentation or config alongside the content ("I endorse THIS rendering of this frame"). At runtime, endorsements are expanded into a frame table (`Map<FrameKey, Frame>`) with a parallel mount map. Mounts live on the table, not on individual frames.

Only endorsed frames appear in the manifest and affect the version. But every item may also accumulate **unendorsed frames** — annotations, comments, reactions, moderation actions, third-party metadata — created by anyone, about the item, without the item author's involvement. Unendorsed frames are free-floating, independent assertions: they reference the item but live outside its version history. They don't alter any version unless the item's author chooses to endorse them into a future manifest.

See [Frames](frames.md) for the Frame/FrameBody/FrameRecord/Endorsement layering, the identity and index flags, content modes, and the endorsed/unendorsed distinction.

### Implementation

The **implementation** binding tells the runtime how to instantiate this item. The binding's role is the platform (e.g., Java, Rust, Python), and the target identifies the code:

| Target | Meaning                                                                                       |
|--------|-----------------------------------------------------------------------------------------------|
| **Literal** (type name) | A built-in implementation on the local runtime — a platform-native identifier (e.g., a Java class, a Rust struct, a Python class, etc) |
| **ItemID** | A distributed implementation — an item carrying CODE frames with the actual source or bytecode|

The literal form is the common case today: the platform ships with the implementation, and the manifest just names it. The ItemID form enables distributing new implementations as items — someone writes a new chess variant, packages it as an item with code frames, and any node that trusts the author can instantiate it. The code item can carry source, bytecode, or compiled native binaries for multiple architectures (x86, ARM, etc.) — whatever the target platform needs. Same binding structure, same manifest field, but the implementation travels with the data instead of being pre-installed.

The semantic relationship between an implementation and the concept it implements (e.g., "this code implements chess") lives in an IMPLEMENTS frame on the item, not on the manifest. The manifest only records which code to run.

The body/record split:

1. Compute the version hash by hashing the BODY fields (iid, parents, implementation, endorsements, identity bindings)
2. Sign the hash with the author's key
3. Attach the signature as a RECORD field

The version hash is deterministic from content. The signature proves who authored that content. No circular dependency.

### Item-Level Bindings

Manifests carry **item-level bindings** — role-keyed values that describe the item as a whole (not a specific frame). These are split by identity flag:

- **Identity bindings** — contribute to the VID. Changing them creates a new version.
- **Non-identity bindings** — record-scope only. Don't affect the VID.

The identity flag is a per-binding choice by the author, not a structural constraint. Config and presentation bindings are conventionally non-identity, but an author can mark any binding as identity if they want changes to it to produce a new version.

Config bindings participate in the [config cascade](#config-cascade): when resolving config for a frame, the item's manifest bindings are checked before falling back to the predicate's defaults.

See [Manifests](manifest.md) for the full manifest structure, signing, and canonical encoding.

## Mounts

Frames can have **mounts** — presentation descriptors that control where a frame appears in different views:

| Mount type | Purpose |
|-----------|---------|
| `PathMount` | Filesystem-like path (`/documents/readme.md`) — tree structure |
| `SurfaceMount` | 2D UI placement (named region + ordering) |
| `SpatialMount` | 3D placement (position + rotation quaternion) |

A frame can have multiple mounts (like hard links). Frames with no mounts are internal entries — they exist in the table but don't appear in navigation.

Mounts are part of the endorsement — each `FrameEndorsement` carries a FrameKey, bodyHash, and a list of mounts. In the manifest, they're serialized together. At runtime, the endorsements table holds mounts in a parallel map alongside the frames, rather than on the Frame objects themselves — a frame is pure content, and its placement is a separate concern.

## Item Types

An item's type is declared through frames, not through any platform-specific mechanism. Two kinds of frames establish type:

### IMPLEMENTS

An item declares what concept it implements via an **IMPLEMENTS** frame — linking the item to a sememe (a universal meaning unit). A chess game item carries an IMPLEMENTS frame pointing to the Chess sememe. The manifest's implementation binding records which platform code to run; the IMPLEMENTS frame records *what concept that code is an implementation of*.

### Seed Concepts

Bootstrap concepts — the foundational sememes that the system needs before any data exists — have **deterministic IIDs** computed from a canonical key string (e.g., `ItemID.fromString("cg.sememe:chess")`). Two independently started nodes arrive at the same IID for "chess" without coordination.

A seedItem concept is itself an item, carrying frames that define it: glosses (human-readable descriptions), lexemes (words in various languages), and EXPECTS declarations (see below). Seed items have no signature and no timestamp — they are axioms, not assertions.

### EXPECTS: Schema as Frames

A concept declares its expected shape via **EXPECTS** frames. These say "instances of this concept should carry these frames." For example, the Chess concept carries EXPECTS frames declaring that a chess game should have a PLAYER frame qualified with WHITE and a PLAYER frame qualified with BLACK.

The UI generates creation forms from EXPECTS declarations. EXPECTS also enables duck typing — if an item structurally carries the expected frames, it IS that type, regardless of what its IMPLEMENTS frame says.

See [Frames: EXPECTS](frames.md#expects-schema-as-frames) for the full explanation.

## Item State

An Item's versioned state is its list of endorsed frames. The manifest serializes these as `FrameEndorsement` objects. At runtime, endorsements are expanded into a frame table for efficient lookup. At commit time, the table is snapshotted back into endorsements for serialization.

## ID Types

All IDs are multihash values — self-describing hashes that include the algorithm used. 256-bit (32 bytes) everywhere.

| ID | Derived from | Purpose |
|----|-------------|---------|
| **ItemID** | Random or `hash(canonical_string)` | Stable identity across versions |
| **ContentID** | `hash(content_bytes)` | Content-addresses a block of bytes. Also used as the version identifier (hash of manifest body). |
| **FrameKey** | Sequence of Sememe/Literal tokens | Compound semantic address for a frame within an item |
| **Ref** | `target [@version] [\frameKey]* [[selector]]` | Unified reference — can drill into a specific version, frame, and range |

ItemID and ContentID inherit from `HashID`. FrameKey is not a hash — it's a structured key composed of semantic tokens (`Sememe(ItemID)` or `Literal(String)`). It implements `Canonical` and `Comparable` for deterministic encoding and ordering.

## Item Lifecycle

### Creation

A new item starts with a fresh random IID and an empty frame table. The runtime populates default frames based on the item's type (driven by its EXPECTS declarations), then derives the item's vocabulary by scanning those frames for indexed string bindings.

### Loading

An existing item is reconstituted from a manifest. The endorsements are expanded into a frame table, and each frame's content is fetched from the store by CID and decoded. The vocabulary is then rebuilt from the loaded frames.

### Editing

An item enters edit mode, after which its frames can be added, removed, or modified. Edits mutate the frame table directly — there is no copy-on-write. The item tracks whether uncommitted changes exist.

### Commit

Committing snapshots the current frame table into a list of endorsements, assembles a new manifest (IID, parents, implementation, endorsements, bindings), hashes the BODY fields to produce the VID, and signs the hash. The signed manifest is then stored. The new VID becomes a head for this item.

## Config Cascade

Config is resolved by walking three levels:

```
Frame config binding       "This specific frame has custom styling"
  | overridden by
Item manifest binding      "This item's frames use a custom chart"
  | overridden by
Predicate frame            "Harvest records render as tables by default"
```

Most frames carry no config — they inherit from item and predicate. Config bindings are non-identity, so changing config never creates a new version of the frame body. See [Frames: Config](frames.md#config-just-bindings) for how config is expressed as bindings.

## Composable Items

Items compose behavior from typed frames. There are no special "chat room" or "shared folder" types baked into the system — everything is assembled from frames:

| Want | Compose |
|------|---------|
| Chat room | Item + Roster + Log (stream) |
| Game | Item + Player frames + Move frames |
| User profile | Item + KeyLog (stream) + Vault (local) |
| Document | Item + TITLE frame + AUTHORED frame |

The same manifest holds all of these. A "chess game" is an item whose EXPECTS declarations say it needs PLAYER and MOVE frames. A "document" is an item that expects TITLE, AUTHOR, and DESCRIPTION frames. The type IS the expected frames.

## Vocabulary

Every Item has a vocabulary — the tokens (words) it recognizes, derived at runtime by scanning its frames for indexed string bindings. When a frame has a binding like `NAME:[ENGLISH, VERB, LEMMA]->"create"`, that posts `"create"` to the item's token index.

This is fully automatic — the vocabulary is rebuilt from frame content, not stored separately. See [Vocabulary](vocabulary.md) for the full resolution pipeline.

## Working Tree Representation

An Item can be materialized as a filesystem working tree — see [Working Trees](working-tree.md):

```
my-item/
+-- README.md              # Mounted content (editable)
+-- data/
|   +-- config.json
+-- .item/
    +-- iid                # Item identity
    +-- head/              # Working state
    +-- manifests/         # Immutable version snapshots
    +-- channels/          # Named branches
    +-- content/           # Content blocks (by CID)
```

The working tree is a view of the manifest's endorsed frames — path mounts determine what appears where. Edit the mounted content, then `commit()` to mint a new version.
