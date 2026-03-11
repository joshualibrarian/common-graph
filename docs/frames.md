# Frames

Everything in Common Graph is a **frame** — a filled semantic structure based on Fillmore's frame semantics. Frames are the single primitive. Items, components, relations, properties, glosses, content, streams, vaults, annotations, reactions, trust attestations — all frames.

## The Frame Primitive

A frame has a **body** and a **record**. The body is the semantic assertion — what is being said. The record is the envelope — who said it and when.

```
Frame {
    // BODY — the assertion (hashed for identity)
    predicate:  ItemID              // what kind of assertion (REQUIRED)
    theme:      ItemID              // what it's about (REQUIRED)
    bindings:   [FrameKey → Target] // additional role bindings (optional)

    // RECORD — the envelope (not part of body hash)
    config:     Config?             // scene + policy (optional, usually inherited)
    signer:     SigningKey?         // who vouches for this (optional)
    timestamp:  Instant?            // when this record was created
    bodyHash:   ContentID           // hash of the body (computed, not stored in body)
}
```

**Predicate** names the frame — it's a sememe that defines the kind of assertion. TITLE, AUTHOR, CONTENT, LIKED_BY, GLOSS, CILI, CHAT — all predicates.

**Theme** is the item this frame is about and where the frame lives. Every frame has a theme. No theme means no home — it's not a frame, it's a query.

**Bindings** fill additional roles beyond the theme. A GLOSS frame binds a language: `GLOSS { theme: sememe, language: ENG, target: "definition" }`. A LIKED_BY frame binds an agent: `LIKED_BY { theme: post, agent: alice }`. Many frames need only predicate + theme + target, with no additional bindings.

**Config** controls how the frame presents and behaves — scene (rendering, interaction) and policy (access, trust, retention). Most frames leave this null and inherit from the item or type. See [Config Cascade](#config-cascade).

**Signer** is who vouches for this record. Endorsed frames (in the item's manifest) inherit the manifest signer. Unendorsed frames carry their own signature.

**Body hash** is the content identity of the assertion. It is computed from the body fields only — predicate, theme, and bindings. The same assertion from different signers produces the same body hash. The record fields (signer, config, timestamp) are excluded.

### Body and Record

The body/record split is fundamental. The **body** is the pure assertion — a fact about the world. The **record** is a signed envelope that says "I attest to this fact."

This means:
- The same assertion can accumulate multiple records. Alice asserts `AUTHOR { theme: TheHobbit, target: Tolkien }`. Bob independently asserts the same thing. One body hash, two records — two attestations of the same fact.
- Storage deduplicates bodies. The body bytes are stored once, content-addressed by hash. Records reference the body by hash.
- Trust accumulates around assertions. More signers on the same body hash = more confidence. Policy can count attestations, weight them by signer trust, require thresholds.

```
AUTHOR { theme: TheHobbit, target: Tolkien }
    body hash: 7f3a...
    records:
        alice   @ 2026-03-01    "I assert this"
        bob     @ 2026-03-05    "I also assert this"
        carol   @ 2026-03-08    "I verify this"
```

For **endorsed frames**, the record is implicit — the manifest signature covers all endorsed body hashes. The manifest IS the record. No separate envelope needed.

For **unendorsed frames**, each record is independent. A like, a spam label, a trust attestation — each is a record wrapping a body. Multiple records can share the same body when multiple parties make the same assertion.

**Verification** is not a special mechanism. A fact-checker verifying a claim simply adds their record to an existing body. They don't create a new assertion — they add their envelope to an existing one. The body hash IS the thing being verified.

**Promotion** — when an item owner endorses an unendorsed assertion — adds the body hash to the manifest. Now the same body has the original asserter's record AND the owner's manifest endorsement. Two trust signals, one assertion.

## FrameKey

Every frame on an item is identified by a **FrameKey** — a sequence of tokens that together form a compound semantic address:

```
FrameKey = [Token...]
Token = Sememe(ItemID) | Literal(String)
```

Keys can be:

| Key | Type | Example |
|-----|------|---------|
| `(TITLE)` | Single sememe | The item's title |
| `(GLOSS, ENG)` | Compound sememe | English gloss of a sememe |
| `(GLOSS, SPA)` | Compound sememe | Spanish gloss |
| `(CILI)` | Single sememe | CILI external ID |
| `(VIDEO, THUMBNAIL)` | Compound sememe | Thumbnail video variant |
| `(CHAT, GENERAL)` | Compound sememe | General chat channel |
| `(CHAT, "tavern")` | Mixed | Tavern chat (literal qualifier) |
| `("x")` | Literal | Developer scratch variable |
| `("player1")` | Literal | Game internal state |

**Semantic keys** (sememe tokens) are discoverable through the vocabulary system, resolve across languages, and merge cleanly across librarians.

**Literal keys** (string tokens) are opaque — local, fast, not vocabulary-resolvable. They still merge across versions of the same item (same key = same frame), but they don't participate in semantic discovery.

**Compound keys** combine multiple tokens into a path. `(GLOSS, ENG)` is a single key with two segments. The first segment is the primary predicate; additional segments qualify it. This is how the same predicate (GLOSS) can have multiple instances on an item (one per language).

The current `HandleID` is a degenerate case — a single-segment literal key. Mount paths (`/segment/segment/...`) are compound keys. Both are subsumed by FrameKey.

## Theme: The Home of Every Frame

The theme determines where a frame lives. This is axiomatic:

- A frame with `theme: TheHobbit` lives on the TheHobbit item
- A frame with `theme: alice` lives on the alice item
- Every frame has exactly one home — the item identified by its theme

When a frame references multiple items (e.g., `WROTE { theme: TheHobbit, agent: Tolkien }`), it lives on the theme (TheHobbit) and is **indexed** on all other referenced items (Tolkien) for discoverability.

Storage is on one item. Discoverability is on all referenced items.

## Items: Coherent Collections of Frames

An item is an IID plus a collection of frames:

```
Item {
    iid:    ItemID              // stable identity
    frames: [Frame...]          // everything else
}
```

That's it. An identity and frames.

But an item is more than a bag — it's a **coherent collection**. A Book item expects certain frames (TITLE, AUTHOR, CONTENT) and arranges them into a meaningful whole. A Chat item expects a roster and a message stream. A Game item expects players and a move log.

The item type class (`@Type`) defines the expected frame collection:

```java
@Type("cg:type/book")
public class Book extends Item {
    @Frame(key = CONTENT)       ContentFrame body;
    @Frame(key = TITLE)         TextFrame title;
    @Frame(key = AUTHOR)        RefFrame author;
    @Frame(key = PUBLISHED)     DateFrame published;
    @Frame(key = COVER_IMAGE)   ImageFrame cover;
}
```

The class says "a Book expects these frames." Individual instances may have more frames (annotations, likes, comments from others) or fewer (a draft with no cover image yet). The type is the template; the instance is the reality.

### Content as a Frame

An item's "content" — its actual bytes, whatever they are — is just a frame. A document's body is `(CONTENT) → <bytes>`. An image is `(CONTENT) → <image bytes>`. A video is `(VIDEO) → <video bytes>`. There is no special content addressing system separate from the frame table. The content's CID is just the hash of a frame target that happens to be large.

## Endorsed and Unendorsed Frames

Every frame on an item is either **endorsed** or **unendorsed**:

**Endorsed frames** are included in the item's manifest. The item owner commits them, signs them, arranges them. "These frames are part of me." The title, the content, the author declaration, the roster — these are endorsed.

**Unendorsed frames** are attached to the item by others. They are independently signed by their asserter. The item owner did not put them there, does not have to acknowledge them, and can policy them away. Likes, comments, spam labels, trust attestations, third-party annotations — these are unendorsed.

```
post:1234 {
    // Endorsed (in the manifest, signed by owner)
    (CONTENT)       → "Here's my hot take..."
    (TITLE)         → "Hot Take"

    // Unendorsed (attached by others, independently signed)
    (LIKED_BY)      → alice        signed by: alice
    (LIKED_BY)      → bob          signed by: bob
    (LABELED, SPAM) → true         signed by: dave
}
```

The structural difference is only manifest inclusion. Same frame format, same hash mechanism, same signing mechanism. The manifest is a signed list of frame hashes that says "I endorse these."

### Cosigning

Cosigning falls out naturally from the body/record split. Adding a record to an existing body IS cosigning — no special mechanism needed. Alice's assertion and Bob's attestation share the same body hash; the records differ only in signer.

For **version-specific** cosigning — "I verify the AUTHOR frame on TheHobbit as of manifest version V3" — the cosigner creates a frame about a frame. The body references the target body hash and the manifest version. This is a meta-assertion, itself a frame, itself accumulating records.

### Promotion and Rejection

An item owner can **promote** an unendorsed frame to endorsed — accepting a contribution into their manifest. They can **reject** unendorsed frames via policy — hiding them from view without deleting them from the graph. The frame still exists; it's just not visible under the item's policy.

## The Manifest

The manifest is the item's signed, versioned snapshot of its endorsed frames:

```
Manifest {
    iid:        ItemID
    type:       ItemID
    frameTable: [FrameEntry...]
    signer:     SigningKey
    version:    VersionID           // hash of manifest body
    timestamp:  Instant
}
```

Each FrameEntry carries item-level metadata about an endorsed frame:

```
FrameEntry {
    key:        FrameKey            // the frame's semantic or literal key
    hash:       ContentID           // hash of the frame's content
    mode:       snapshot|stream|local   // storage mode
    mount:      MountPath?          // where this frame sits in the presentation tree
    identity:   boolean             // contributes to item identity?
}
```

**Mode** determines storage behavior:
- **Snapshot**: immutable, content-addressed. The default. Each commit produces a new version.
- **Stream**: append-only log with heads. Chat messages, key history, game moves.
- **Local-only**: stored at a filesystem path, never synced. Vaults, local databases.

**Mount** is the item's arrangement decision — where this frame appears in the item's presentation tree. The frame itself doesn't know its layout position; the item arranges its frames via mounts.

**Identity** marks whether this frame contributes to the item's identity hash. Most frames do. Ephemeral or derived frames may not.

## Config: Scene + Policy

Config controls how a frame presents and behaves. It has two aspects:

```
Config {
    scene:  Scene?      // presentation and interaction
    policy: Policy?     // access, trust, and retention
}
```

**Scene** defines rendering and interaction:
- How to render the frame's value (text field, slider, color picker, video player)
- Whether it's editable, read-only, or hidden
- Validation constraints (min, max, required, pattern)
- Layout hints (width, grouping)

**Policy** defines access and trust:
- Who can read this frame (visibility)
- Who can modify it (edit rights)
- Who can attach unendorsed frames to it (annotation rights)
- Trust threshold for showing unendorsed frames
- Retention rules for unendorsed frames

### Config Cascade

Most frames carry no config — they inherit from the item or type. The cascade:

```
Type defaults              "Book frames are generally world-readable"
  ↓ overridden by
Item manifest config       "THIS book requires trust > 0.3"
  ↓ overridden by
Per-frame config           "THIS frame is local-only"
```

Type classes define sensible defaults. The manifest can override for the whole item. Individual frames override only when they need to differ. In practice, 99% of frames are bare — predicate, theme, bindings, and nothing else.

## Queries: Incomplete Frames

A query is a frame with holes. Where a complete frame fills all its roles, a query leaves one or more roles as variables:

```
// Complete frame:
AUTHOR { theme: TheHobbit, target: Tolkien }

// Query — who authored TheHobbit?
AUTHOR { theme: TheHobbit, target: ? }

// Query — what did Tolkien author?
AUTHOR { theme: ?, target: Tolkien }

// Query — all authorship assertions:
AUTHOR { theme: ?, target: ? }
```

Evaluation fills the holes by searching the graph. This unifies all query mechanisms — pattern queries, predicate queries, frame lookups — into one model: find frames matching this pattern.

Query execution searches frames across items:
- If theme is specified: search that item's frames
- If theme is `?`: search the index for frames matching the predicate and other bindings
- Multiple `?` fields: return all matching frames

## Storage and Indexing

### Frame Storage

**Bodies** are stored content-addressed by hash. The body includes predicate, theme, and bindings. Two identical assertions from different signers produce the same body hash — the body bytes are stored once.

**Records** reference bodies by hash and add signer, timestamp, and config. Multiple records can reference the same body — this is how attestations accumulate. Records are stored independently, keyed by `bodyHash | signerKey`.

**Endorsed frames** are referenced by the manifest's frame table (body hash in the FrameEntry). The manifest signature is the record — no separate envelope stored.

**Unendorsed frames** are stored as independent records, linked to the item via the index. Each record is self-contained: body hash + signer + signature.

### Indexing

Three indexes enable efficient queries:

**FRAME_BY_ITEM**: indexes every item referenced in any frame binding. Key: `itemIID | predicate | bodyHash`. Enables "all frames involving item X" and "all frames involving item X via predicate P."

**FRAME_BY_PRED**: indexes by predicate alone. Key: `predicate | bodyHash`. Enables "all frames of type P."

**RECORD_BY_BODY**: indexes records by body hash. Key: `bodyHash | signerKey`. Enables "who attests to this assertion?" and "how many independent attestations does this fact have?"

Body indexes cover both endorsed and unendorsed frames. Endorsed frames are indexed at commit time. Unendorsed frames are indexed when received. Record indexes are updated whenever a new record arrives for any body.

## What Frames Cover

Everything in Common Graph is a frame:

| Kind | Frame Form |
|---|---|
| **Content / body** | Endorsed frame keyed by `(CONTENT)`, large target |
| **Property** | Endorsed frame with semantic key and simple target |
| **Gloss** | Endorsed frame keyed by `(GLOSS, LANG)` |
| **External ID (CILI)** | Endorsed frame keyed by `(CILI)` |
| **Expression / formula** | Endorsed frame whose target is an expression |
| **Stream (chat, log)** | Endorsed frame with mode=stream |
| **Vault** | Endorsed frame with mode=local-only |
| **Policy** | Config on item or individual frames |
| **Like / reaction** | Unendorsed frame signed by reactor |
| **Comment** | Unendorsed annotation frame signed by commenter |
| **Spam label** | Unendorsed frame signed by moderator |
| **Trust attestation** | Unendorsed frame signed by attester |
| **Verification** | Frame about a frame (cosigning) |
| **Query** | Incomplete frame (frame with holes) |

## The Prompt

Frame creation from the prompt follows naturally:

```
# Focused on book:TheHobbit (implicit theme)
title = "The Hobbit"           →  endorsed frame: TITLE { theme: TheHobbit, target: "The Hobbit" }
title is "The Hobbit"          →  same
author = Tolkien               →  endorsed frame: AUTHOR { theme: TheHobbit, target: Tolkien }
Tolkien is author              →  same (inverted copula)

# Explicit cross-item assertion
relate Tolkien wrote TheHobbit →  frame: WROTE { theme: TheHobbit, agent: Tolkien }
```

Setting a property on the focused item creates an endorsed frame. The `=` operator and `is` copula both fill frames where the focused item is the implicit theme.

## Design Principles

- **Frames all the way down**: One primitive, one structure, one hash mechanism, one signing mechanism.
- **Body is assertion, record is attestation**: The body is what is said. The record is who said it. Same body, many records — trust accumulates.
- **Theme is home**: Every frame lives on exactly one item — its theme. No free-floating frames.
- **Endorsed vs. unendorsed**: The only structural difference is manifest inclusion. Same frame either way.
- **Bare by default**: Most frames carry no config. Inherit from item, inherit from type. Override only when needed.
- **Semantic keys merge, literal keys persist**: Semantic FrameKeys are vocabulary-resolvable and merge cleanly across librarians. Literal keys are opaque but stable.
- **Queries are incomplete frames**: A `?` in a role turns a frame into a query. Evaluation fills the holes.
