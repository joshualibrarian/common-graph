# Frames

The fundamental primitive in Common Graph is a **frame** — a filled semantic structure inspired by Fillmore's frame semantics. Everything an item contains is a frame. A title, a gloss, a chat stream, a vault, a like, a spam label, a trust attestation — all frames.  Every frame relates a predicate to a theme, filling semantic roles. The difference between "Tolkien authored The Hobbit" and "the title of The Hobbit is 'The Hobbit'" is only which predicate and which bindings — structurally they are the same thing.

## The Frame Primitive

A frame has a **body** and a **record**. The body is the semantic assertion — what is being said. The record is the envelope — who said it and when, and how it should present itself.

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

**Predicate** names the frame — a sememe that defines the kind of assertion. TITLE, AUTHOR, TEXT, GLOSS, SOURCE, LIKED_BY, CILI, CHAT — all predicates. The predicate carries real meaning about *what role the data plays*, not just "there's stuff here."

**Theme** is the item this frame is about and where the frame lives. Every frame has exactly one home — the item identified by its theme. No theme means no home — it's not a frame, it's a query.

**Bindings** fill additional roles beyond the theme. A GLOSS frame binds a language: `GLOSS { theme: sememe, LANGUAGE: ENG, target: "definition" }`. A LIKED_FanBY frame binds an agent: `LIKED_BY { theme: post, AGENT: alice }`. Many frames need only predicate + theme + target.

**Config** controls how the frame presents and behaves — scene (rendering, interaction) and policy (access, trust, retention). Most frames leave this null and inherit from the item or type. See [Config Cascade](#config-cascade).

**Signer** is who vouches for this record. Endorsed frames (in the item's manifest) inherit the manifest signer. Unendorsed frames carry their own signature.

**Body hash** is the content identity of the assertion, computed from the body fields only — predicate, theme, and bindings. The same assertion from different signers produces the same body hash. Record fields (signer, config, timestamp) are excluded.

## Body and Record

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

Every frame on an item is identified by a **FrameKey** — an immutable sequence of tokens that together form a compound semantic address:

```
FrameKey = [Token...]
Token = Sememe(ItemID) | Literal(String)
```

The FrameKey is the primary key for frames within an item. It is unique per item — one frame per key.

Keys can be:

| Key | Type | Example |
|-----|------|---------|
| `(TITLE)` | Single sememe | The item's title |
| `(GLOSS, ENG)` | Compound sememe | English gloss of a sememe |
| `(GLOSS, SPA)` | Compound sememe | Spanish gloss |
| `(CILI)` | Single sememe | CILI external ID |
| `(TEXT, ENGLISH)` | Compound sememe | English text of a document |
| `(TEXT, ENGLISH, CRITICAL)` | Compound sememe | Critical edition of the English text |
| `(IMPLEMENTATION)` | Single sememe | Code that implements this item's behavior |
| `(CHAT, GENERAL)` | Compound sememe | General chat channel |
| `(CHAT, "tavern")` | Mixed | Tavern chat (literal qualifier) |
| `("x")` | Literal | Developer scratch variable |

The first token is the **head** — the primary predicate. Additional tokens are **qualifiers** that distinguish multiple instances of the same predicate. `(GLOSS, ENG)` and `(GLOSS, SPA)` are two frames with the same head predicate (GLOSS) differentiated by a language qualifier.

**Semantic keys** (sememe tokens) are discoverable through the vocabulary system, resolve across languages, and merge cleanly across librarians. A Spanish-speaking user searching for `(TEXTO, INGLÉS)` finds the same frame as `(TEXT, ENGLISH)` — the sememes resolve to the same ItemIDs.

**Literal keys** (string tokens) are opaque — local, fast, not vocabulary-resolvable. They still merge across versions of the same item (same key = same frame), but they don't participate in semantic discovery. Literal keys are the degenerate case — developer scratch, opaque handles — not a convenient shortcut for domain concepts that should be sememes.

**Compound keys** combine multiple tokens into a path. This is how the same predicate can have multiple instances on an item (one gloss per language, one text per edition, one chat per channel).

## Predicates and Meaning

The frame predicate should carry real semantic meaning about what role the data plays. Consider a Book item:

| Frame key | What it is |
|-----------|-----------|
| `(TEXT, ENGLISH)` | The English text |
| `(TEXT, FRENCH)` | The French translation |
| `(AUDIOBOOK, ENGLISH)` | An English narration |
| `(COVER_ART)` | The cover image |
| `(TITLE)` | The title |
| `(AUTHOR)` | Who wrote it |

Every predicate says something about the *role* the data plays in the item's semantic structure. "Text" tells you it's the written content. "Audiobook" tells you it's a narrated rendition. "Cover art" tells you it's the visual identity.

A generic predicate like "content" says almost nothing — it's a placeholder for "I haven't thought about what this actually is yet." Prefer specific, meaningful predicates. The type system provides them: a Book type declares `(TEXT)`, `(COVER_ART)`, etc. An Application type declares `(SOURCE)`, `(EXECUTABLE)`, etc.

## Representations

A single frame can have **multiple representations** — different encodings of the same semantic content. The frame `(TEXT, ENGLISH)` is the concept "the English text." Whether it's stored as PDF, EPUB, or plain text is an encoding detail, not a semantic distinction.

```
(TEXT, ENGLISH) {
    representations:
        epub    CID: abc123...   format: application/epub+zip
        pdf     CID: def456...   format: application/pdf
        plain   CID: ghi789...   format: text/plain
}
```

Format lives in representation metadata, not in the FrameKey. Changing from PDF to EPUB doesn't change the frame's identity — it's still the same semantic slot. Consumers request `(TEXT, ENGLISH)` and get whichever representation they can handle or prefer. This is content negotiation at the frame level.

**When to use different keys vs. different representations:**
- Same content, different encoding → one frame, multiple representations. The EPUB and PDF of the same text.
- Genuinely different content → different keys. `(TEXT, ENGLISH, CRITICAL)` vs `(TEXT, ENGLISH, POPULAR)` — two semantically distinct editions, each potentially with their own representations.

The distinction: if the bytes were decoded and compared as meaning, would they be the same? If yes, they're representations of one frame. If no, they're different frames.

## Theme: The Home of Every Frame

The theme determines where a frame lives. This is axiomatic:

- A frame with `theme: TheHobbit` lives on the TheHobbit item
- A frame with `theme: alice` lives on the alice item
- Every frame has exactly one home — the item identified by its theme

When a frame references multiple items (e.g., `WROTE { theme: TheHobbit, AGENT: Tolkien }`), it lives on the theme (TheHobbit) and is **indexed** on all other referenced items (Tolkien) for discoverability.

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

But an item is more than a bag — it's a **coherent collection**. A Book item expects certain frames (TITLE, AUTHOR, TEXT) and arranges them into a meaningful whole. A Chat item expects a roster and a message stream. A Game item expects players and a move log.

The item type class (`@Type`) defines the expected frame collection:

```java
@Type("cg:type/book")
public class Book extends Item {
    @Frame(key = {TITLE})                           String title;
    @Frame(key = {AUTHOR}, endorsed = false)        ItemID author;
    @Frame(key = {TEXT, ENGLISH})                    byte[] englishText;
    @Frame(key = {COVER_ART})                       byte[] cover;
}
```

The class says "a Book expects these frames." Individual instances may have more frames (likes, comments, annotations from others) or fewer (a draft with no cover yet). The type is the template; the instance is the reality.

## Endorsed and Unendorsed Frames

Every frame on an item is either **endorsed** or **unendorsed**:

**Endorsed frames** are included in the item's manifest. The item owner commits them, signs them, arranges them. "These frames are part of me." The title, the text, the author declaration, the roster — these are endorsed.

**Unendorsed frames** are attached to the item by others. They are independently signed by their asserter. The item owner did not put them there, does not have to acknowledge them, and can policy them away. Likes, spam labels, trust attestations, third-party annotations — these are unendorsed.

```
book:TheHobbit {
    // Endorsed (in the manifest, signed by owner)
    (TITLE)              → "The Hobbit"
    (TEXT, ENGLISH)      → <epub bytes>   <pdf bytes>
    (AUTHOR)             → Tolkien

    // Unendorsed (attached by others, independently signed)
    (LIKED_BY)           → alice          signed by: alice
    (LIKED_BY)           → bob            signed by: bob
    (LABELED, SPAM)      → true           signed by: dave
    (REVIEWED_BY)        → carol          signed by: carol
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
    vid:        ContentID           // hash of manifest body
    timestamp:  Instant
}
```

The VID (version identifier) is just a ContentID — the hash of the manifest's body bytes. There is no separate VersionID type. A version is identified by the content hash of its manifest body, like everything else is identified by the content hash of its bytes.

Each FrameEntry carries item-level metadata about an endorsed frame:

```
FrameEntry {
    frameKey:   FrameKey            // the frame's semantic address (unique within item)
    type:       ItemID              // what kind of thing this is (defines codec)
    hash:       ContentID           // hash of the frame's content
    mode:       snapshot|stream|local   // storage mode
    mount:      MountPath?          // where this frame sits in the presentation tree
    identity:   boolean             // contributes to version identity?
    alias:      String?             // human-facing shorthand
}
```

**Mode** determines storage behavior:
- **Snapshot**: immutable, content-addressed. The default. Each commit produces a new version.
- **Stream**: append-only log with heads. Chat messages, key history, game moves.
- **Local-only**: stored at a filesystem path, never synced. Vaults, local databases.

**Mount** is the item's arrangement decision — where this frame appears in the item's presentation tree. The frame itself doesn't know its layout position; the item arranges its frames via mounts.

**Identity** marks whether this frame contributes to the version hash. Most frames do. Ephemeral or derived frames may not.

## Config: Scene + Policy

Config controls how a frame presents and behaves:

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

Most frames carry no config — they inherit from the item or type:

```
Type defaults              "Book frames are generally world-readable"
  ↓ overridden by
Item manifest config       "THIS book requires trust > 0.3"
  ↓ overridden by
Per-frame config           "THIS frame is local-only"
```

Type classes define sensible defaults. The manifest can override for the whole item. Individual frames override only when they need to differ. In practice, the vast majority of frames are bare — predicate, theme, bindings, and nothing else.

## Queries: Incomplete Frames

A query is a frame with holes. Where a complete frame fills all its roles, a query leaves one or more roles as variables:

```
// Complete frame:
AUTHOR { theme: TheHobbit, target: Tolkien }

// Query — who authored TheHobbit?
AUTHOR { theme: TheHobbit, target: ? }

// Query — what did Tolkien author?
AUTHOR { theme: ?, target: Tolkien }

// Query — all authorship frames:
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

All indexes are derived from the object store and can be rebuilt:

| Index | Key | Purpose |
|-------|-----|---------|
| **ITEMS** | `IID \| VID → timestamp` | Version history per item |
| **HEADS** | `Principal \| IID → VID` | Current version per principal |
| **FRAME_BY_ITEM** | `ItemID \| Predicate \| BodyHash → CID` | All frames involving an item via a predicate |
| **RECORD_BY_BODY** | `BodyHash \| SignerKey → CID` | Who attests to this assertion? |

FRAME_BY_ITEM is the unified frame index — predicates are items too, so the same index structure covers everything. A query for "all frames on TheHobbit" is a prefix scan by ItemID. A query for "all AUTHOR frames on TheHobbit" narrows with the predicate.

Body indexes cover both endorsed and unendorsed frames. Endorsed frames are indexed at commit time. Unendorsed frames are indexed when received. Record indexes are updated whenever a new record arrives for any body.

## What Frames Cover

Everything in Common Graph is a frame:

| Kind | Frame Form |
|---|---|
| **Text content** | Endorsed frame keyed by `(TEXT, LANG)`, with representations |
| **Media** | Endorsed frame keyed by `(AUDIO, ...)`, `(VIDEO, ...)`, `(IMAGE, ...)` |
| **Code** | Endorsed frame keyed by `(IMPLEMENTATION)`, `(BUILD_SCRIPT)`, `(TEST_SUITE)` — language is representation metadata |
| **Property** | Endorsed frame with semantic key and simple target |
| **Gloss** | Endorsed frame keyed by `(GLOSS, LANG)` |
| **External ID (CILI)** | Endorsed frame keyed by `(CILI)` |
| **Expression / formula** | Endorsed frame whose target is an expression |
| **Stream (chat, log)** | Endorsed frame with mode=stream |
| **Vault** | Endorsed frame with mode=local-only |
| **Policy** | Config on item or individual frames |
| **Like / reaction** | Unendorsed frame signed by reactor |
| **Annotation** | Unendorsed frame signed by annotator |
| **Spam label** | Unendorsed frame signed by moderator |
| **Trust attestation** | Unendorsed frame signed by attester |
| **Verification** | Frame about a frame (cosigning) |
| **Query** | Incomplete frame (frame with holes) |

## The Prompt

Frame creation from the prompt follows naturally:

```
# Focused on book:TheHobbit (implicit theme)
title = "The Hobbit"           →  endorsed frame: TITLE { theme: TheHobbit, target: "The Hobbit" }
title is "The Hobbit"          →  same (copula form)
author = Tolkien               →  endorsed frame: AUTHOR { theme: TheHobbit, target: Tolkien }
Tolkien is author              →  same (inverted copula)

# Explicit cross-item assertion
Tolkien wrote TheHobbit        →  frame: WROTE { theme: TheHobbit, AGENT: Tolkien }
```

Setting a property on the focused item creates an endorsed frame. The `=` operator and `is` copula both fill frames where the focused item is the implicit theme.

## Design Principles

- **Frames all the way down**: One primitive, one structure, one hash mechanism, one signing mechanism.
- **Body is assertion, record is attestation**: The body is what is said. The record is who said it. Same body, many records — trust accumulates.
- **Theme is home**: Every frame lives on exactly one item — its theme. No free-floating frames.
- **Endorsed vs. unendorsed**: The only structural difference is manifest inclusion. Same frame either way.
- **Predicates carry meaning**: The predicate tells you what role the data plays. Prefer specific semantic predicates over generic ones.
- **Format is not identity**: Different encodings of the same content are representations of one frame, not different frames. Format lives in metadata.
- **Qualifiers are semantic**: When distinguishing instances of the same predicate, prefer sememe qualifiers over literal strings. `(TEXT, ENGLISH, CRITICAL)` not `(TEXT, ENGLISH, "critical")`.
- **Bare by default**: Most frames carry no config. Inherit from item, inherit from type. Override only when needed.
- **Semantic keys merge, literal keys persist**: Semantic FrameKeys resolve across languages and merge across librarians. Literal keys are opaque but stable.
- **Queries are incomplete frames**: A `?` in a role turns a frame into a query. Evaluation fills the holes.
