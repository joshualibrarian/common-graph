# Frames

The fundamental primitive in Common Graph is a **frame** — a filled semantic structure inspired by Fillmore's frame semantics (1968/1982), extended to carry data. Everything an item contains is a frame. A title, a gloss, a chess game, a vault, a video, a like, a trust attestation — all frames. Every frame relates a predicate to a theme, filling semantic roles with bindings. The difference between "Tolkien authored The Hobbit" and "the title of The Hobbit is 'The Hobbit'" is only which predicate and which bindings — structurally they are the same thing.

**Frames are semantic keys, not annotations.** Every concept a frame touches — its predicate, its theme, every binding role, every target — is a globally-anchored semantic reference resolved at write time. The person or code creating the frame performs the disambiguation at creation, when intent is unambiguous. What gets stored is a structure of semantic references, not text to be interpreted later. This is why the graph is queryable by meaning without a search engine — the data is pre-indexed by meaning at the moment of creation, and the UI makes this both easy and inevitable.

## The Frame Primitive

The entire data model is two types:

```
Frame {
    predicate:  ItemID              // what kind of frame (REQUIRED)
    theme:      Ref                 // what it's about (REQUIRED)
    bindings:   [Binding...]        // role bindings filling the predicate's slots
}

Binding {
    key:        [ItemID...]         // compound role key (semantic role + qualifiers)
    target:     Target              // CID, stream ref, inline value, item ref
    identity:   boolean             // in body hash? (default from predicate/role)
    index:      boolean             // in frame index? (default from predicate/role)
    instance:   Object              // live decoded value (transient, runtime only)
}
```

The **theme** is a Ref — a reference that can address an item, a frame, or content within a frame:

```
Ref = ItemID                           // whole item
    | ItemID / FrameKey                // a frame on an item
    | ItemID / FrameKey / Selector     // content within a frame
```

This allows frames to be about anything at any granularity:

```
TITLE(theme=book, ...)                              // about an item
COMMENT(theme=book/(TEXT, ENGLISH), ...)             // about a frame
ANNOTATION(theme=book/(TEXT, ENGLISH)/p[3], ...)     // about a paragraph
CORRECTION(theme=book/(AUTHOR)/(NAME), ...)          // about a binding
```

That's it. Content, provenance, format variants, cached transcodes, signatures — all just bindings with compound keys, an identity flag, and an index flag.

Frames are **self-contained** within the meaning-space. They reference every concept relevant to what they assert — predicate, theme, binding roles, targets — all as semantic references into the graph. You don't need a manifest or a separate envelope to interpret a frame; it names every meaning it touches. The manifest merely endorses them.

## Bindings: Data as Roles

Fillmore's frames describe situations with role-playing participants. CG extends this: frames also **carry data**, and that data is expressed as bindings. Every piece of content on a frame — a title string, a video file, a move log, a signature — is a binding with a semantic role.

### Compound Binding Keys

Binding keys are compound — a sequence of sememe ItemIDs that together identify what the binding IS:

| Binding Key | What it carries |
|-------------|-----------------|
| `(NAME)` | The name/title text |
| `(MKV, UHD)` | A UHD Matroska video file |
| `(MKV, HD)` | An HD transcode |
| `(ODT)` | An ODT document |
| `(PDF)` | A PDF rendering |
| `(WHITE)` | The white player in chess |
| `(MOVES)` | The move log stream |
| `(SIGNATURE, alice)` | Alice's attestation |

The first element is the **semantic role** — what this value means in the frame. Additional elements are **qualifiers** — content type, format, resolution, signer, variant. All are sememes in the vocabulary — discoverable, resolvable across languages.

For simple bindings, the key is a single element: `(NAME)`, `(WHITE)`, `(AUTHOR)`. For content with format or variant distinctions, the key is compound: `(MKV, UHD)`, `(PDF)`.

### Identity Bindings

Every binding is either **identity** (contributes to the body hash) or **non-identity** (does not):

- **Identity** — this value IS part of the frame's assertion. Changing it changes the body hash. A title's text. A resume's source document. The canonical master of a video.
- **Non-identity** — this value lives alongside the frame but doesn't affect its identity. A cached transcode. An exported PDF. A streaming move log. A signature. Derived, regenerable, accumulating, or metadata.

The default comes from two sources:

1. **The role sememe** — some roles are inherently non-identity. SIGNATURE is never part of the body hash. A role sememe can declare its default.
2. **The predicate** — the predicate declares defaults for its expected roles. CHESS declares MOVES as non-identity. TITLE declares NAME as identity.

The binding creator can always override. But in practice, the defaults from role and predicate handle nearly every case.

### Index Bindings

Every binding is either **indexed** (discoverable via the frame index) or **non-indexed** (private to the frame):

- **Indexed** — this binding appears in the FRAME_BY_ITEM index, keyed on the binding's target. If `(AUTHOR) → Tolkien` is indexed, querying "frames involving Tolkien" finds this frame. Indexed bindings make frames discoverable from both ends — from the theme AND from the binding target.
- **Non-indexed** — this binding is only reachable through the frame itself. A cached transcode, a streaming log, inline content. You can find the frame from the theme, but the binding doesn't create a reverse lookup entry.

Like identity, the default comes from the predicate and role sememe:

1. **The role sememe** — AUTHOR is inherently indexable. SIGNATURE is not. A role sememe can declare its default.
2. **The predicate** — the predicate declares defaults for its roles. CHESS declares PLAYER as indexed (find games by player). TITLE declares NAME as non-indexed (you don't look up titles from the string "The Hobbit").

The two flags are orthogonal. A binding can be:

| identity | index | Example |
|----------|-------|---------|
| true | true | `(AUTHOR) → Tolkien` — IS the assertion, discoverable from Tolkien |
| true | false | `(NAME) → "The Hobbit"` — IS the title, not indexed by the string |
| false | true | `(PLAYER, WHITE) → fischer` on a stateful game — non-identity (game identity is the players at creation time) but indexed so you can find games by player |
| false | false | `(MKV, SD) → cid:transcode` — derived content, not indexed |

### Provenance as Bindings

Signatures are bindings. A signature already contains a timestamp, so no separate timestamp binding is needed:

```
(SIGNATURE, alice)  → sig-bytes     [identity: false]
```

For endorsed frames, the manifest signature covers them — no SIGNATURE binding needed. For unendorsed frames (likes, annotations, trust attestations), the SIGNATURE binding carries the independent attestation.

Multi-attestation is just multiple SIGNATURE bindings with different signer qualifiers:

```
(SIGNATURE, alice)  → sig-bytes     [identity: false]
(SIGNATURE, bob)    → sig-bytes     [identity: false]
```

Other metadata bindings are available if a domain needs them — `(CREATED_AT)`, `(UPDATED_BY)`, etc. — but the framework doesn't mandate them. They're just bindings like anything else.

### Examples

**Title frame:**
```
TITLE(theme=book,
  (NAME) → "The Hobbit"                    [identity])

body_hash = hash(TITLE + book + {NAME="The Hobbit"})
```
Change the title → new body hash.

**Chess frame:**
```
CHESS(theme=game-item,
  (PLAYER, WHITE) → fischer                         [identity]
  (PLAYER, BLACK) → spassky                         [identity]
  (MOVES) → stream:cid-abc                 [non-identity])

body_hash = hash(CHESS + game-item + {(PLAYER,WHITE)=fischer, (PLAYER,BLACK)=spassky})
```
Body hash is stable for the entire game. Moves accumulate in the non-identity stream. New moves never change the body hash.

**Movie frame:**
```
VIDEO(theme=inception,
  (MKV, UHD) → cid:master-4k               [identity]
  (MKV, HD)  → cid:hd-xcode                [non-identity]
  (MKV, SD)  → cid:sd-xcode                [non-identity])

body_hash = hash(VIDEO + inception + {(MKV,UHD)=cid:master-4k})
```
The UHD master IS the movie — identity. The HD and SD transcodes are derived — non-identity. Replace a transcode tomorrow, body hash unchanged.

**Resume frame:**
```
RESUME(theme=person,
  (ODT) → cid:source-doc                   [identity]
  (PDF) → cid:pdf-export                   [non-identity])

body_hash = hash(RESUME + person + {(ODT)=cid:source-doc})
```
Edit the ODT source → new body hash → new VID. Re-export the PDF → body hash unchanged.

**Expression frame:**
```
EXPRESSION(theme=spreadsheet,
  (NAME) → "x"                             [identity]
  (VALUE) → 42                              [identity])

body_hash = hash(EXPRESSION + spreadsheet + {NAME="x", VALUE=42})
```
Change to `x=43` → new body hash, old frame replaced at the same FrameKey.

**Vault frame:**
```
VAULT(theme=signer,
  (KEYS) → path:"/keys/vault.db"           [non-identity]
  (LOG)  → stream:cid-rotations            [non-identity])

body_hash = hash(VAULT + signer)
```
No identity bindings at all — body hash is just predicate + theme. Stable forever. Key material and rotation log change freely.

**Unendorsed frame with provenance:**
```
LIKED_BY(theme=book,
  (LIKER) → alice                           [identity]
  (SIGNATURE, alice) → sig-bytes            [non-identity])

body_hash = hash(LIKED_BY + book + {LIKER=alice})
```
The assertion "alice likes this book" is the identity. The signature is non-identity provenance.

## Body Hash: Identity

The body hash is computed from the **predicate**, **theme**, and **identity bindings only**:

```
body_hash = hash(predicate + theme + bindings.filter(identity).sortBy(key))
```

This means:
- `x=1` and `x=2` → different body hashes (different identity content)
- `x=1` and `y=1` → different body hashes (different key)
- `x=1` on item A and `x=1` on item B → different body hashes (different theme)
- Chess game before move 1 and after move 50 → same body hash (moves are non-identity)

### Semantic Key vs. Body Hash

These are two layers of identity:

**Semantic key** — the stable address. RESUME on my person item. Doesn't change when I rewrite my resume. Used for lookup and addressing. This is the FrameKey.

**Body hash** — the content identity. Changes when identity bindings change. Used for versioning and integrity.

The key identifies the SLOT. The body hash identifies WHAT'S IN the slot. Changing identity content at the same key creates a new frame (new body hash) that replaces the old one. The old frame can be garbage collected.

## Predicates: Shape and Behavior

The predicate is itself an item — a type that defines:

1. **Shape** — what binding roles the frame expects ((PLAYER, WHITE) and (PLAYER, BLACK) for chess; NAME for title; ODT and PDF for resume)
2. **Binding defaults** — which roles are identity or non-identity by default (MOVES defaults to non-identity; NAME defaults to identity)
3. **Behavior** — code (`@Verb` methods on the type class) that operates on the frame's content

```java
@Type("cg:type/chess")
public class ChessGame implements FrameAware {
    // Shape: predicate expects PLAYER, MOVES
    // Defaults: PLAYER identity, MOVES non-identity

    @Verb("cg.verb:move")
    public ActionResult move(ActionContext ctx, String notation) { ... }

    @Verb("cg.verb:resign")
    public ActionResult resign(ActionContext ctx) { ... }
}
```

The predicate carries real semantic meaning about what role the data plays. TITLE, AUTHOR, GLOSS, CHESS, VAULT, VIDEO — each says something specific about the nature of the frame. No generic "content" or "value" predicates needed — predicates define their own meaningful roles.

## FrameKey: Semantic Address

Every frame on an item is addressable by its **key** — the semantic prefix that identifies the slot:

| Key | What it addresses |
|-----|-------------------|
| `(TITLE)` | The item's title |
| `(GLOSS, ENG)` | English gloss of a sememe |
| `(GLOSS, SPA)` | Spanish gloss |
| `(TEXT, ENGLISH)` | English text of a document |
| `(CHESS)` | A chess game |
| `(CHAT, "tavern")` | Tavern chat channel |
| `(EXPRESSION, "x")` | Expression named x |
| `(VIDEO)` | A video frame |
| `(RESUME)` | A resume frame |

The first element is the **predicate** — the primary semantic type. Additional elements are **discriminators** that distinguish multiple frames of the same predicate. `(GLOSS, ENG)` and `(GLOSS, SPA)` are two frames with the same predicate differentiated by a language discriminator.

**Semantic keys** (sememe tokens) are discoverable through the vocabulary system, resolve across languages, and merge cleanly across librarians.

**Literal keys** (string tokens) are opaque — local, fast, not vocabulary-resolvable. The degenerate case for developer scratch, not a shortcut for domain concepts that should be sememes.

Note: FrameKey addresses the frame on the item. Compound binding keys address values within the frame. Different levels — same structure.

## The Manifest: Endorsement

The manifest is the item's signed list of endorsements — what frames the item owner vouches for and how the item presents them:

```
Manifest {
    iid:            ItemID
    type:           ItemID
    endorsements:   [Endorsement...]
    signer:         SigningKey
    vid:            ContentID           // hash of manifest body
    timestamp:      Instant
}
```

Each endorsement is minimal:

```
Endorsement {
    key:        FrameKey            // which frame (semantic address)
    body_hash:  ContentID           // which version of that frame's body
    mounts:     [Mount...]          // how this item presents this frame
}
```

No identity flag. The body hash includes only identity bindings. If you don't want content changes to affect VID, make those bindings non-identity — the body hash stays stable, the manifest never notices. Identity control lives at the binding level, where the decision is made.

**Mounts** — the item's layout decision. Where this frame appears in the item's presentation tree. The frame itself doesn't know its layout position — the item arranges its frames.

**VID computation:**
```
VID = hash(manifest body)
    = hash(iid + type + endorsements + timestamp)
    = hash(... + [key₁+bodyHash₁, key₂+bodyHash₂, ...] + ...)
```

Each body hash only includes identity bindings. Non-identity content is invisible to VID at every level.

The manifest IS the attestation for endorsed frames. The manifest signature covers all endorsements — no separate per-frame signatures needed.

For **unendorsed frames**, each frame carries its own SIGNATURE binding. Likes, spam labels, annotations — independently signed, attached to the item's theme.

## Endorsed and Unendorsed

Every frame on an item is either **endorsed** or **unendorsed**:

**Endorsed frames** are in the item's manifest. The item owner commits them, signs them, arranges them. "These frames are part of me." The title, the video, the chess game — endorsed.

**Unendorsed frames** are attached to the item by others. Independently signed via SIGNATURE bindings. The item owner did not put them there. Likes, spam labels, trust attestations — unendorsed.

```
book:TheHobbit {
    // Endorsed (in manifest, signed by owner)
    (TITLE)         →  (NAME) = "The Hobbit"
    (TEXT)          →  (EPUB) = cid:abc              [identity]
                       (PDF)  = cid:def              [non-identity]
    (AUTHOR)        →  (NAME) = Tolkien

    // Unendorsed (attached by others, self-signed)
    (LIKED_BY)      →  (LIKER) = alice
                       (SIGNATURE, alice) = sig      [non-identity]
    (LIKED_BY)      →  (LIKER) = bob
                       (SIGNATURE, bob) = sig        [non-identity]
    (LABELED, SPAM) →  (VALUE) = true
                       (SIGNATURE, dave) = sig       [non-identity]
}
```

The structural difference is only manifest inclusion. Same frame format, same binding structure. Promotion (owner endorses an unendorsed frame) just adds the body hash to the manifest.

## Mutation: Swapping Immutable Frames

Items act mutable but are really swapping immutable things at stable keys:

```
Time 0:  manifest endorses { TITLE → body_hash_A }     VID = V0
Time 1:  manifest endorses { TITLE → body_hash_B }     VID = V1  (A garbage collected)
Time 2:  manifest endorses { TITLE → body_hash_C }     VID = V2  (B garbage collected)
```

For assertion frames (title, expressions): new value → new body hash → replace at key. The old frame is disposable.

For stateful frames (chess, chat): same body hash (non-identity content doesn't affect it) → new state in the non-identity bindings → endorsement body hash unchanged → VID unchanged. The semantic identity is stable while state evolves.

Either way: the item replaces an immutable frame at a stable key. No frame versioning, no parent chains. Version history is at the item level — the manifest chain captures which frames were endorsed at each point in time.

## Queries: Incomplete Frames

A query is a frame with holes. Where a complete frame fills all its roles, a query leaves one or more as variables:

```
// Complete frame:
AUTHOR { theme: TheHobbit, (NAME) → Tolkien }

// Query — who authored TheHobbit?
AUTHOR { theme: TheHobbit, (NAME) → ? }

// Query — what did Tolkien author?
AUTHOR { theme: ?, (NAME) → Tolkien }

// Query — all authorship frames:
AUTHOR { theme: ?, (NAME) → ? }
```

Evaluation fills the holes by searching the graph. Queries are frames — same structure, just incomplete.

## Storage and Indexing

**Frames** are stored content-addressed by body hash. Two identical assertions (same predicate, theme, and identity bindings) from different signers produce the same body hash — stored once.

**Content** (snapshots, stream entries, local resources) is stored in the object store, referenced by CID from binding targets.

**Signatures** are non-identity bindings on the frame. Multiple SIGNATURE bindings with different signers = multiple attestations of the same body.

### Indexes

All indexes are derived from the object store and can be rebuilt:

| Index | Key | Purpose |
|-------|-----|---------|
| **ITEMS** | `IID \| VID → timestamp` | Version history per item |
| **HEADS** | `Principal \| IID → VID` | Current version per principal |
| **FRAME_BY_ITEM** | `ItemID \| Predicate \| BodyHash → CID` | Frames discoverable via an item — populated by `index: true` bindings |
| **RECORD_BY_BODY** | `BodyHash \| SignerKey → CID` | Who attests to this assertion? |

## Config: Scene + Policy

Config controls how a frame presents and behaves:

```
Config {
    scene:  Scene?      // presentation and interaction
    policy: Policy?     // access, trust, and retention
}
```

**Scene** defines rendering and interaction — how to render the value, whether it's editable, validation constraints, layout hints.

**Policy** defines access and trust — who can read, modify, annotate; trust thresholds; retention rules.

### Config Cascade

Most frames carry no config — they inherit:

```
Type defaults              "Book frames are generally world-readable"
  | overridden by
Item manifest config       "THIS book requires trust > 0.3"
  | overridden by
Per-frame config           "THIS frame is local-only"
```

In practice, the vast majority of frames are bare — predicate, theme, bindings, and nothing else.

## Design Principles

- **Two types**: Frame (predicate + theme + bindings) and Binding (compound key + target + identity + index). That's the entire data model.
- **Everything is a binding**: Content, references, streams, local paths, signatures, metadata — all expressed as bindings with compound semantic keys. No special-purpose fields.
- **Two orthogonal flags**: `identity` controls body hash inclusion. `index` controls frame index discoverability. Both default from predicate and role. No separate flags anywhere else — not on endorsements, not on frames.
- **Compound keys carry type**: The binding key includes the content type and format — `(MKV, UHD)`, `(ODT)`, `(PDF)`. No separate type metadata.
- **Predicates define meaningful roles**: TITLE expects NAME. CHESS expects PLAYER, MOVES. No generic VALUE or CONTENT roles — predicates declare what they need.
- **Provenance is bindings**: Signatures are non-identity SIGNATURE bindings. Timestamps are inside the signature. No separate Record type.
- **Frames are self-contained**: A frame carries everything needed to understand it. No manifest dependency.
- **The manifest is just endorsement**: Key + body hash + mounts. The endorsement is thin — the frame has the substance.
- **Theme is home**: Every frame lives on exactly one item — its theme. No free-floating frames.
- **Mutation is replacement**: Items act mutable by swapping immutable frames at stable keys. Everything underneath is content-addressed and immutable.
- **Bare by default**: Most frames carry no config. Value is in the bindings. Override only when needed.
- **Queries are incomplete frames**: A `?` in a role turns a frame into a query. Evaluation fills the holes.
