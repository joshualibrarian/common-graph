# Items

In the Common Graph, **frames** hold all the data, indexed with semantic keys. However, an **Item** is the fundamental unit of coherent meaning. Everything — documents, users, hosts, conversations, games, applications, and even compiled code — is an Item. Anything that makes sense **as a whole**.

An Item is a **versioned, signed container of frames with stable identity**. Every Item carries its own identity, its own history, and its own trust chain. Items don't live at paths or URLs — they exist by identity, and you find them by meaning.

The Item model draws from several traditions: Smalltalk's "everything is an object" with message-passing dispatch ([Kay 1993](references/Kay%201993%20-%20The%20Early%20History%20of%20Smalltalk.pdf)), the Actor model's independent entities communicating through messages ([Hewitt et al 1973](references/Hewitt%2C%20Bishop%2C%20Steiger%201973%20-%20A%20Universal%20Modular%20ACTOR%20Formalism.pdf)), and Engelbart's vision of augmenting human intellect through integrated artifact-language-methodology systems ([Engelbart 1962](references/Engelbart%201962%20-%20Augmenting%20Human%20Intellect.pdf)). Like Bush's memex ([Bush 1945](references/Bush%201945%20-%20As%20We%20May%20Think.pdf)), items are found by meaning and association rather than hierarchical location.

## Anatomy of an Item

An Item has two parts: an **IID** and a **Manifest**.

| Part | What it is |
|------|-----------|
| **IID** | Stable 32-byte identity that persists across all versions |
| **Manifest** | Signed, immutable snapshot of a specific version |

The manifest IS the item at a point in time. It contains:

| Manifest field | What it holds |
|----------------|---------------|
| **Endorsements** | The item's frames — every endorsed assertion, keyed by FrameKey |
| **Bindings** | Item-level properties (identity bindings affect VID; non-identity don't) |
| **Implementation** | Platform + class name (e.g., Java + `ChessItem`) |
| **Signature** | Author key + cryptographic signature |

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

A Manifest is the **signed, immutable declaration** of an Item version — the item at a point in time:

```
Manifest {
    version:        int                 -- format version (currently 1)
    iid:            ItemID              -- which item this is
    parents:        List<ContentID>     -- parent version hashes (history chain)
    implementation: Binding             -- platform + class name (e.g., Java + "ChessItem")
    state:          ItemState           -- endorsed frames (List<FrameEndorsement>)
    bindings:       List<Binding>       -- item-level bindings (identity + non-identity)
    --- non-BODY fields (excluded from version hash) ---
    authorKey:      SigningPublicKey     -- who signed this
    signature:      Signing             -- the signature itself
}
```

### Endorsed Frames

The manifest's state holds the item's **endorsed frames** — each a `FrameEndorsement` carrying a FrameKey, bodyHash, and mounts. At runtime, endorsements are expanded into a frame table (`Map<FrameKey, Frame>`) with a parallel mount map. Mounts live on the table, not on individual frames.

See [Frames](frames.md) for the Frame/FrameBody/FrameRecord/Endorsement layering, the identity and index flags, content modes, and the endorsed/unendorsed distinction.

### Implementation

The **implementation** binding records the creating platform and class name — the platform IID (e.g., Java) as the binding's role, and the class name as a literal target. The semantic relationship between a Java class and its concept lives in an IMPLEMENTS frame on the item, not on the manifest.

The BODY/non-BODY split:

1. Compute the version hash by hashing the BODY fields (version, iid, parents, implementation, state, identity bindings)
2. Sign the hash with the author's key
3. Attach the signature as a non-BODY field

The version hash is deterministic from content. The signature proves who authored that content. No circular dependency.

### Item-Level Bindings

Manifests carry **item-level bindings** — role-keyed values that describe the item as a whole (not a specific frame). These are split by identity flag:

- **Identity bindings** — contribute to the VID. Changing them creates a new version.
- **Non-identity bindings** — record-scope only. Config, presentation overrides, vocabulary customization. Don't affect the VID.

Non-identity bindings participate in the [config cascade](#config-cascade): when resolving config for a frame, the item's manifest bindings are checked before falling back to the predicate's defaults.

See [Manifests](manifest.md) for the full manifest structure, signing, and canonical encoding.

## Mounts

Frames can have **mounts** — presentation descriptors that control where a frame appears in different views:

| Mount type | Purpose |
|-----------|---------|
| `PathMount` | Filesystem-like path (`/documents/readme.md`) — tree structure |
| `SurfaceMount` | 2D UI placement (named region + ordering) |
| `SpatialMount` | 3D placement (position + rotation quaternion) |

A frame can have multiple mounts (like hard links). Frames with no mounts are internal entries — they exist in the table but don't appear in navigation.

Mounts are stored on the frame table, not on individual Frames. They are serialized alongside each frame's endorsement in the manifest.

## Item Types

Items declare their type via two annotations:

### @Implements

Links a Java class to the semantic concept it implements:

```java
@Implements(ChessItem.Chess.KEY)
public class ChessItem extends Item { ... }
```

The value is the concept's canonical key string (e.g., `"cg.sememe:chess"`). This links the class to a Sememe with that key. At runtime, `@Implements` is synthesized into an IMPLEMENTED_BY frame — the manifest records the implementation binding, and the runtime resolves the implementing class from it.

### @ItemSeed

Declares a seed sememe — a bootstrap concept with deterministic IID. Placed on a class (outer or inner) to define the concept that an item type implements:

```java
@ItemSeed(key = Chess.KEY)
public static class Chess {
    public static final String KEY = "cg.sememe:chess";

    @ItemFrame(predicate = SememeGloss.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                          qualifiers = {Language.ENGLISH_KEY}))
    static final String gloss = "the game of chess";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                          qualifiers = {Language.ENGLISH_KEY,
                                                        PartOfSpeech.Noun.KEY,
                                                        GrammaticalFeature.Lemma.KEY}))
    static final String word = "chess";
}
```

Seed items have deterministic IIDs (from `ItemID.fromString(key)`), timestamp 0, and no signature. They're imported into the Library on first boot via classpath scanning.

The static `@ItemFrame` fields on a seed class declare the concept's frames — glosses, lexemes, EXPECTS declarations — using the same frame annotation as instance fields.

### @ItemFrame

Declares a frame on an Item type. Used on both seed static fields and instance fields:

```java
// Simple: a title string
@ItemFrame(predicate = CoreVocabulary.Title.KEY)
private String title;

// With binding metadata: field value bound with role + qualifiers
@ItemFrame(predicate = SememeGloss.KEY,
           fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                      qualifiers = {Language.ENGLISH_KEY}))
private String gloss;
```

The annotation specifies:
- **`predicate`** — the frame's predicate (canonical key string)
- **`classAs`** — binding for the owning item/class (default: THEME)
- **`fieldAs`** — binding for the field value (role + qualifiers via `@Bind`)
- **`endorsement`** — whether the frame is endorsed and its mount paths

### EXPECTS: Schema as Frames

Predicates declare their expected shape via EXPECTS frames on the seed. See [Frames: EXPECTS](frames.md#expects-schema-as-frames) for the full explanation. A chess game declares:

```java
@ItemFrame(predicate = CoreVocabulary.Expects.KEY,
           fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY,
                   qualifiers = {FrameBody.TYPE_KEY, GameVocabulary.Player.KEY,
                                 ColorVocabulary.White.KEY}))
static final ItemID expectWhitePlayer = ItemID.fromString(GameVocabulary.Player.KEY);
```

This says: "instances of Chess should carry a PLAYER frame qualified with WHITE." The UI generates creation forms from EXPECTS declarations. EXPECTS also enables duck typing — if an item structurally matches, it IS that type.

## Item State

An Item's versioned state is encapsulated in `ItemState`, which the manifest serializes as a list of `FrameEndorsement` objects. At runtime, endorsements are expanded into a frame table for efficient lookup. At commit time, the table is snapshotted back into endorsements for serialization.

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

```
new ChessItem(librarian)
 -> random IID generated
 -> ItemState created with empty frame table
 -> initializeFreshComponents():
     for each @ItemFrame field with @Implements type:
       1. Create default instance
       2. Build Frame (snapshot/stream/local-only)
       3. Add frame + live instance to frame table
       4. Add mounts if declared
 -> hydrate():
     Bind @ItemFrame fields from table
 -> onFullyInitialized():
     1. populateVocabulary() -- scan frames for indexed string bindings
     2. populateUnendorsedFrames() -- load from index
     3. syncFieldValuesToTable() -- handle subclass field initializers
```

### Hydration (Loading)

```
Item loaded from Manifest
 -> Frames extracted from manifest's ItemState endorsements
 -> hydrate():
     Phase 1: For each Frame:
       1. Fetch content by CID from the store
       2. Decode via Canonical
       3. Store live instance on Frame
     Phase 2: Bind @ItemFrame fields from table
 -> populateVocabulary()
```

### Editing

```
item.edit()                    -- enter edit mode
item.endorseFrame(body)        -- endorse a frame body (store + add to table)
```

Edit mode is a flag — it doesn't create a copy. You mutate the item's state directly, and `dirty` tracks that changes exist.

### Commit

```
item.commit(signer)
 -> scanAndBindFields():
     For each @ItemFrame field: encode value -> CID -> update Frame bodyHash
 -> state.buildEndorsements():
     Snapshot frame table into List<FrameEndorsement>
 -> Build Manifest:
     iid, implementation(Java + className), parents, state, bindings
 -> manifest.sign(signer) -- sign BODY bytes
 -> storeManifest() -- serialize and store via librarian
 -> base = manifest.vid(), dirty = false
```

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
