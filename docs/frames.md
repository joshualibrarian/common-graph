# Frames

The fundamental primitive in Common Graph is the **semantic frame** — a predicate with role-keyed bindings, grounded in a shared vocabulary of meanings. Everything is a frame. A title, a gloss, a chess move, a vault, a video, a like, a harvest record, a sensor reading — all frames. The difference between "Tolkien authored The Hobbit" and "Alice harvested 5kg of tomatoes from bed 3" is only which predicate and which bindings — structurally they are the same thing.

**Frames are semantic keys, not annotations.** Every concept a frame touches — its predicate, every binding role, every qualifier, every target — is a globally-anchored semantic reference resolved at write time. The person or code creating the frame performs disambiguation at creation, when intent is unambiguous. What gets stored is a structure of semantic references, not text to be interpreted later. The data is pre-indexed by meaning at the moment of creation.

## The Frame Primitive

A frame is a **predicate** and a list of **bindings**. That's it.

```
FrameBody {
    predicate:  ItemID          // what kind of assertion
    bindings:   [Binding...]    // role-keyed values filling the predicate's slots
}
```

Each binding has three parts:

```
Binding {
    role:        ItemID         // semantic function (NAME, THEME, AGENT, RESULT, ...)
    qualifiers:  [ItemID...]   // narrowing + constraints (ENGLISH, VERB, LEMMA, QUANTITY, WEIGHT, ...)
    target:      BindingTarget  // the bound value (item ref, literal, content CID)
}
```

- **Role**: what KIND of binding — the semantic function this value plays
- **Qualifiers**: WHICH variant of that role — narrows the binding and constrains valid inputs
- **Target**: what's actually bound — the data

The compound key `[role, qualifier₁, qualifier₂, ...]` is the binding's **key**. The target is the binding's **value**. Every binding is a key→value pair.

## Predicates as Schemas

A predicate is itself an item — a sememe that declares what roles (bindings) the frame expects. This is equivalent to designing a database schema, a form, or a spreadsheet. The predicate IS the data template.

```
HARVEST_RECORD expects:
    LOCATION:[]                → which garden (item picker)
    AGENT:[]                   → who harvested (person picker)
    THEME:[]                   → what crop (item picker)
    SOURCE:[]                  → which bed (item picker)
    TIME:[]                    → when (date/time picker)
    RESULT:[QUANTITY, WEIGHT]  → how much (number + weight unit dropdown)
    RESULT:[QUANTITY, COUNT]   → how many (integer input)
```

The qualifiers `[QUANTITY, WEIGHT]` both **distinguish** this RESULT from other RESULTs and **constrain** valid inputs to quantities with weight units. The UI generates a form directly from these declarations — no separate form builder needed.

Creating a frame IS entering data. Querying frames IS a database query. Piping frames through functions IS a data pipeline. All from one primitive.

### Role Declarations

The predicate declares:

1. **Shape** — what binding roles the frame expects (its "columns")
2. **Defaults** — which roles are identity or non-identity by default
3. **Behavior** — code (`@Verb` methods on the implementing class) that operates on the frame's content

```java
@Implements("cg.sememe:chess")
@ItemSeed(key = "cg.sememe:chess", slots = {ThematicRole.Location.KEY})
public class ChessGame extends Item {
    @Verb(value = "cg.verb:move", doc = "Make a chess move")
    public ActionResult move(ActionContext ctx, String notation) { ... }
}
```

## Bindings: Data as Roles

Fillmore's frames describe situations with role-playing participants. CG extends this: frames also **carry data**, and that data is expressed as bindings. Every piece of content — a title string, a video file, a sensor reading, a chess player — is a binding with a semantic role.

### Compound Binding Keys

The binding key is `[role, qualifier₁, qualifier₂, ...]`. The first element is always the role — the semantic function. Additional elements are qualifiers.

| Binding Key | What it carries |
|-------------|-----------------|
| `NAME:[]` | The name/title text |
| `NAME:[ENGLISH, VERB, LEMMA]` | English verb lemma form |
| `NAME:[MKV, UHD]` | A UHD Matroska video file |
| `NAME:[EMAIL, WORK]` | Work email address |
| `THEME:[]` | What this frame is about |
| `AGENT:[]` | Who performed the action |
| `RESULT:[QUANTITY, WEIGHT]` | A weight measurement result |
| `TOPIC:[STREAM]` | A stream content reference |
| `TOPIC:[LOCAL]` | A local filesystem resource |

For simple bindings, qualifiers are empty: `THEME:[]`, `AGENT:[]`, `NAME:[]`. For bindings that need to distinguish variants or constrain types, qualifiers narrow the meaning: `NAME:[MKV, UHD]`, `RESULT:[QUANTITY, WEIGHT]`.

All elements — roles AND qualifiers — are sememes in the shared vocabulary. Discoverable, resolvable across languages.

### The Home Binding

Every frame has a binding that anchors it to its subject — the thing the frame is about. Which role this is depends on the predicate:

- **Property predicates** (TITLE, AUTHORED, GLOSS, HYPERNYM) use `THEME:[]` — the entity being described
- **Event predicates** (MOVE, MESSAGE, BID) use `LOCATION:[]` — the context where the event occurs

This is convention, not a special field. THEME is just a regular binding — the predicate defines which role is the "home." When querying from an item's context, the home binding is implicit.

### Identity: Per Binding

Every binding is either **identity** or **non-identity**:

- **Identity** — this value IS part of the assertion. Changing it changes the body hash. A title's text. The players in a chess game. The crop in a harvest record.
- **Non-identity** — this value rides alongside the assertion but doesn't affect its identity. Config, styling, derived content.

The default comes from:
1. **The role sememe** — some roles are inherently non-identity (CONFIG is never part of the body hash)
2. **The predicate** — declares defaults for its expected roles

The binding creator can always override.

### Index: Per Binding

Every binding is either **indexed** or **non-indexed**:

- **Indexed** — creates a reverse-lookup entry in the FRAME_BY_ITEM index. If `AGENT:[]→Tolkien` is indexed, querying "frames involving Tolkien" finds this frame.
- **Non-indexed** — only reachable through the frame itself.

The two flags are orthogonal:

| identity | index | Example |
|----------|-------|---------|
| true | true | `AGENT:[]→Tolkien` — IS the assertion, discoverable from Tolkien |
| true | false | `NAME:[]→"The Hobbit"` — IS the title, not indexed by string |
| false | true | `AGENT:[PLAYER, WHITE]→Fischer` — game state, but indexed so you find games by player |
| false | false | `NAME:[MKV, SD]→transcode_CID` — derived content, not indexed |

## Four Objects: Body, Record, Endorsement, Frame

### FrameBody — The Assertion

The immutable, content-addressed semantic fact. Contains ONLY identity bindings. The body hash = `hash(predicate + identity bindings)`. Two identical assertions from different people produce the same body hash — stored once.

```
FrameBody {
    predicate:  ItemID
    bindings:   [Binding...]    // all identity
}
```

### FrameRecord — The Attestation

Who said it, when, with proof — plus non-identity bindings (config, styling). Points at ONE FrameBody by hash. Multiple records can attest the same body (same fact, different signers, different config).

```
FrameRecord {
    bodyHash:   ContentID       // which fact
    signer:     SigningPublicKey
    timestamp:  Instant
    signature:  bytes
    bindings:   [Binding...]    // all non-identity (config, presentation)
}
```

A frame = body + record(s). The body is the shared fact. The record is everything else. Don't like someone's config? Create your own record pointing at the same body hash with your own bindings. Same fact, your presentation.

### Endorsement — What Manifests Hold

The item's declaration: "I endorse this fact, presented this way."

```
Endorsement {
    bodyHash:   ContentID       // which fact (required)
    recordCid:  ContentID?      // which record's config to honor (optional)
    mounts:     [Mount...]      // presentation layout
}
```

Most endorsements: just bodyHash + mounts. With recordCid: "I pin this record's config."

### Frame — Runtime Container

In-memory only. Holds the body, record(s), and the live decoded instance. Not serialized. The lookup key (selector) is derived from the body.

## Selector: Derived Key

The **selector** identifies a frame — computed from the body, not stored independently. It's the predicate + all qualifier IIDs from compound binding keys:

```
FrameBody:  LEXEME { THEME:[]→sememe, NAME:[ENGLISH, VERB, LEMMA]→"create" }
Selector:   (LEXEME, ENGLISH, VERB, LEMMA)

FrameBody:  TITLE { THEME:[]→book, NAME:[]→"The Hobbit" }
Selector:   (TITLE)

FrameBody:  VAULT { THEME:[]→Alice, LOCATION:[]→laptop, TOPIC:[LOCAL]→"/path" }
Selector:   (VAULT, LOCAL)
```

The home binding's target (THEME→sememe, THEME→book) is NOT part of the selector — it's implicit from the item context.

**Everything is a query.** The selector IS the fetch pattern. `(GLOSS, ENGLISH)` selects the English gloss. That's how you find it.

## The Manifest: Endorsement List

The manifest is the item's signed list of endorsements:

```
Manifest {
    iid:            ItemID
    endorsements:   [Endorsement...]
    signer:         SigningKey
    vid:            ContentID       // hash of manifest body
    timestamp:      Instant
}
```

The manifest IS the attestation for endorsed frames. The manifest signature covers all endorsements. No separate per-frame signatures needed for endorsed frames.

**Mounts** — the item's layout decision. Where this frame appears in the item's presentation tree.

**VID computation:**
```
VID = hash(manifest body)
    = hash(iid + endorsements + timestamp)
    = hash(... + [bodyHash₁, bodyHash₂, ...] + ...)
```

Each body hash only includes identity bindings. Non-identity content is invisible to VID.

## Config: Just Bindings

Config is a binding role, not a separate structure:

```
CONFIG:[]                    → general config
CONFIG:[PRESENTATION]        → styling/display
CONFIG:[REPLICATION]         → sync policy
```

Config bindings are non-identity — they go on the **FrameRecord** (the asserter's choices about how to present the fact). The assertion itself (FrameBody) is pure.

### Config Cascade

```
Type defaults              "Harvest records render as tables by default"
  | overridden by
Item manifest config       "THIS garden's harvest records use a custom chart"
  | overridden by
Frame record config        "THIS specific record has special highlighting"
```

Most frames carry no config — they inherit from item and type.

## Endorsed and Unendorsed

**Endorsed frames** are in the item's manifest. The item owner commits them. The manifest signature covers them.

**Unendorsed frames** are attached by others. Each carries its own FrameRecord with a signature. Likes, annotations, trust attestations.

```
book:TheHobbit {
    // Endorsed (in manifest, covered by owner's signature)
    TITLE    { THEME:[]→book,  NAME:[]→"The Hobbit" }
    AUTHORED { THEME:[]→book,  AGENT:[]→Tolkien }

    // Unendorsed (independently signed FrameRecords)
    LIKE { THEME:[]→book, AGENT:[]→Alice }     [signed by Alice]
    LIKE { THEME:[]→book, AGENT:[]→Bob }       [signed by Bob]
}
```

The structural difference is only manifest inclusion. Same frame format. Promotion (owner endorses an unendorsed frame) just adds the body hash to the manifest.

## Content Model

Frames reference content via `TOPIC` bindings. Three content structures:

### Blob (small content)
```
TITLE { THEME:[]→book, NAME:[]→"The Hobbit" }
```
Inline literal or single CID.

### Chain (streams — sequential, append-only)
```
AUDIO { AGENT:[]→Alice, LOCATION:[]→conference, TOPIC:[STREAM]→root_CID }
```
Root block (metadata + first chunk ref) → chunk₁ → chunk₂ → ... Each chunk links to its predecessor. Root is an immutable identity binding. Head is derived by walking the chain. Used for: audio, video, sensor data, activity logs.

Individual chunks are NOT frames — they're lightweight linked content blocks with minimal overhead. Graph-routed via the peer protocol, encrypted per item policy.

### Manifest (large files — swarmable)
```
VIDEO { THEME:[]→movie, NAME:[MKV, UHD]→manifest_CID }
```
Manifest block lists all piece CIDs + ordering. Pieces can be fetched from different peers (BitTorrent-style swarming). Used for: large files, completed recordings.

## Mutation: Swapping Immutable Frames

Items act mutable by swapping immutable frames at stable selectors:

```
Time 0:  endorses { (TITLE) → body_hash_A }     VID = V0
Time 1:  endorses { (TITLE) → body_hash_B }     VID = V1
```

New value → new body hash → replace at same selector. The old frame is disposable.

For stateful items (chess, chat): the body hash stays stable (identity bindings don't change), while content evolves in non-identity stream bindings or as new frames accumulating on the item.

## Queries: Incomplete Frames

A query is a frame with holes:

```
// Complete frame:
AUTHORED { THEME:[]→TheHobbit, AGENT:[]→Tolkien }

// Query — who authored The Hobbit?
AUTHORED { THEME:[]→TheHobbit, AGENT:[]→? }

// Query — what did Tolkien author?
AUTHORED { THEME:[]→?, AGENT:[]→Tolkien }

// Query — all harvest records over 5kg:
HARVEST_RECORD { RESULT:[QUANTITY, WEIGHT]→(> 5kg) }
```

Queries are frames — same structure, just incomplete. Evaluation fills the holes by searching the graph.

## Local Resources

Local resources (vaults, libraries) are regular frames with LOCATION and TOPIC bindings:

```
VAULT {
    THEME:[]        → Alice_IID          // whose vault
    LOCATION:[]     → laptop_host_IID    // which device
    TOPIC:[LOCAL]   → "/home/alice/.vault"  // where on disk
}
```

The `[LOCAL]` qualifier on TOPIC signals that the content is host-specific. Different devices have different vault frames (different LOCATION, different path). The item carries all of them; only the local one resolves on each machine.

No special `localOnly` flag needed. The binding structure IS the signal.

## Storage and Indexing

**FrameBodies** are stored content-addressed by hash in the object store. Two identical assertions produce the same hash — stored once.

**Content** (blobs, stream chunks, manifests) is stored by CID, referenced from binding targets.

**FrameRecords** are stored content-addressed, referenced from the RECORD_BY_BODY index.

### Indexes

All derived from the object store, rebuildable:

| Index | Key | Purpose |
|-------|-----|---------|
| **ITEMS** | `IID \| VID → timestamp` | Version history per item |
| **HEADS** | `Principal \| IID → VID` | Current version per principal (subjective choice) |
| **FRAME_BY_ITEM** | `ItemID \| Predicate \| BodyHash → CID` | Frame lookup by item and predicate |
| **RECORD_BY_BODY** | `BodyHash \| SignerKey → CID` | Who attests this fact? |

Token indexing: NAME bindings are indexed with scope and features derived from their compound keys. `NAME:[ENGLISH, VERB, LEMMA]→"create"` produces a posting with token="create", scope=ENGLISH, features={VERB, LEMMA}.

## Design Principles

- **One primitive**: Frame = predicate + role-keyed bindings. That's the entire data model.
- **Predicates are schemas**: Designing a predicate IS designing a database, a form, a spreadsheet. Roles are columns. Qualifiers constrain and distinguish.
- **Everything is a binding**: Content, references, local paths, config — all role-keyed bindings with compound keys.
- **Three parts per binding**: Role (semantic function), qualifiers (narrowing + constraints), target (the value).
- **Identity per binding**: Each binding chooses whether it affects the body hash. Non-identity bindings ride on the FrameRecord.
- **Body is pure assertion**: FrameBody = identity bindings only. Content-addressed. Immutable.
- **Record is attestation + choices**: FrameRecord = signature + non-identity bindings (config, presentation).
- **Endorsement is minimal**: Body hash + optional record CID + mounts.
- **Frame is runtime**: In-memory container for body + record(s) + live instance.
- **Selector is derived**: Computed from the body's predicate + compound key qualifiers. Not stored independently.
- **Frames are freestanding**: A frame exists in the object store whether or not any item endorses it. The home binding is convention.
- **Queries are incomplete frames**: A `?` in a role turns a frame into a query.
- **Config is bindings**: `CONFIG:[PRESENTATION]→styling`. Not a separate structure. Cascades from type → item → frame.
- **Content is CID-addressed**: Blob (small), Chain (streams), Manifest (large/swarmable). Stream roots are immutable. Heads are derived.
