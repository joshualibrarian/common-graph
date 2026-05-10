# Frames

The fundamental primitive in Common Graph is the **semantic frame** — a predicate with role-keyed bindings, grounded in a shared vocabulary of meanings. Everything is a frame. A title, a gloss, a chess move, a vault, a video, a like, a harvest record, a sensor reading — all frames. The difference between "Tolkien authored The Hobbit" and "Alice harvested 5kg of tomatoes from bed 3" is only which predicate and which bindings — structurally they are the same thing.

**Frames are structured assertions, not annotations.** The structural elements of a frame — its predicate, every binding role, and in the vast majority of cases every qualifier and target — are globally-anchored semantic references resolved at write time. Some qualifiers and targets are literals: a title string, a math variable name, a file path, a quantity. But the scaffolding that gives those literals meaning — the predicate, the roles, the qualifying sememes around them — is always semantic. The person or code creating the frame performs disambiguation at creation, when intent is unambiguous. What gets stored is a structure of grounded meanings carrying literal values where appropriate, not text to be interpreted later. The data is pre-indexed by meaning at the moment of creation.

## The Frame Primitive

A frame is a **predicate** and a list of **bindings**. That's it.

A frame's body is a **Datum** — the unified structural primitive of Common Graph. See `datum.md` for the full specification of the Datum primitive and its encoding. The short version:

```
Body Datum {
    head-reference:  Tag-6( @<predicate-IID> )    // what kind of assertion (a meaning)
    bindings:        [Binding...]                  // role-keyed values filling the predicate's slots
}
```

The head reference uses the `@` prefix because predicates are items (referenced by IID). For a manifest body, the head reference uses `@<archetype-IID>` instead.

Each binding has three parts:

```
Binding {
    role:        ItemID              // semantic function (NAME, THEME, AGENT, RESULT, ...)
    qualifiers:  [FrameToken]        // narrowing + constraints (sememes or literal values)
    target:      BindingTarget       // the bound value (item ref, literal, content CID)
    index:       boolean             // should this binding be indexed for reverse lookup?
}
```

- **Role**: what KIND of binding — the semantic function this value plays (always a sememe)
- **Qualifiers**: WHICH variant of that role — narrows the binding and constrains valid inputs. Can be sememes (ENGLISH, VERB, QUANTITY) or literals ("x", "tavern") for developer/math identifiers.
- **Target**: what's actually bound — the data
- **Index**: whether this binding creates a reverse-lookup entry. The index behavior depends on the target type: string targets → TokenDictionary posting; ItemID targets → FRAME_BY_ITEM entry. These are mutually exclusive by target type.

The body Datum's CID is computed from its full encoded form — head reference plus all bindings. Bodies do not carry signatures; signatures live on Record Datums (see *Records* below).

### Thematic Roles

The roles in a binding are **thematic roles** (also called theta roles or semantic roles) — a concept from linguistics, originating in Fillmore's Case Grammar (1968) and later Frame Semantics, where participants in any event or relation play identifiable semantic functions. In "Shakespeare wrote Hamlet in London," Shakespeare is the AGENT (the doer), Hamlet is the PATIENT (the thing affected), and London is the LOCATION (where it happened). These functions are universal — every language has ways to express who did what to whom, where, and when.

Common Graph adopts a core vocabulary of thematic roles, aligned with VerbNet and ISO 24617-4 (LIRICS/SemAF-SR). Each role is itself a sememe — a meaning unit with its own IID, glosses, and lexemes. The core set:

| Role | Meaning |
|------|---------|
| AGENT | The intentional initiator of an action |
| PATIENT | The entity affected, changed, or consumed |
| THEME | The entity being described or moved (without change of state) |
| EXPERIENCER | The entity perceiving or feeling |
| STIMULUS | What triggers a perception or feeling |
| CAUSE | A non-intentional force bringing about an event |
| GOAL | The end state or purpose |
| DESTINATION | Where something moves to |
| SOURCE | Where something comes from |
| PATH | The route or trajectory |
| RESULT | The outcome or product |
| RECIPIENT | Who receives a transfer |
| BENEFICIARY | Who benefits from an action |
| PARTNER | A co-participant |
| INSTRUMENT | The tool or means used |
| LOCATION | Where something is or happens |
| TIME | When something happens |
| TOPIC | What something is about (content reference) |
| NAME | A name, label, or identifier |

There are more (MANNER, EXTENT, ATTRIBUTE, PURPOSE, CONFIG, FOLLOWS, etc.), and the set is extensible — new roles can be added as seedItem vocabulary without structural changes. But these cover the vast majority of frames.

The key insight: roles are not just labels. Each role carries semantic expectations about what kind of value it holds and how that value relates to the predicate. AGENT implies intentionality. PATIENT implies change of state. THEME implies reference without change. These distinctions matter for querying, inference, and UI generation.

The compound key `[role, qualifier₁, qualifier₂, ...]` is the binding's **key**. The target is the binding's **value**. Every binding is a key→value pair.

Qualifiers are sememes in the vast majority of cases — queryable by meaning, resolvable across languages. Literal qualifiers are the escape hatch for math variables (`THEME:["x"]→5`), developer identifiers, and cases where a concept doesn't need a vocabulary entry. The choice is meaningful: `NAME:[TAVERN]` is discoverable across languages; `NAME:["tavern"]` is an opaque string.

### Extending the Vocabulary

If a concept doesn't exist in the shared vocabulary, create it. The vocabulary is an open commons:

1. Create a sememe item (e.g., `cg.sememe:acme-product-id`)
2. Add glosses: "Acme Inc.'s internal product identifier"
3. Add lexemes to relevant languages
4. Use it immediately in frames

Now it's a real sememe — queryable, discoverable, cross-lingual. Publish your vocabulary additions for others to use. The system grows from the edges, not the center.

This handles the universal integration problem: every company has their own ID system. Instead of mapping between opaque string keys, you create a sememe for each external ID scheme and use it as a predicate:

```
ACME_PRODUCT_ID  { THEME:[]→our_widget,   NAME:[]→"ACM-7742" }
SAP_MATERIAL_ID  { THEME:[]→our_widget,   NAME:[]→"MAT-001-A" }
CILI_ID          { THEME:[]→dog_sememe,    NAME:[]→"i46360" }
```

Same pattern for enterprise integrations, scientific identifiers, government codes — any external ID system becomes a sememe, and the mapping is a frame. Queryable in both directions: "what's the AcmeID for this widget?" and "what item has AcmeID ACM-7742?"

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

The predicate is itself a sememe — a meaning unit, with an IID. Predicates have two facets: a **definition** (declarative, data) and a **type** (operation vs attestation — see below).

The **definition** is the sememe itself — language-agnostic, declarative, stored as frames endorsed by the predicate:

1. **EXPECTS frames** — what binding roles the frame expects (its "columns") and what qualifies them
2. **Glosses + lexemes** — multilingual surface forms

A predicate is mostly *data*. Behavior lives in the **items** that handle frames carrying that predicate (see *Actor Model* below). The predicate itself does no work in the common case.

#### Operation vs Attestation Predicates

Predicates split into two orthogonal vocabularies:

- **Operation predicates** name what's being done. `ADD`, `MULTIPLY`, `LOOKUP`, `FETCH`, `RENDER`, `INPUT`, `CHESS_MOVE`, `MESSAGE`. The "API call" or "content" shape.
- **Attestation predicates** name a speech act about an operation frame. `AUTHORED`, `ASK`, `TELL`, `DELIVER`, `ACK`, `ENDORSE`, `REVOKE`, `REDACT`. They typically carry a `body→<operation-CID>` binding plus signer, timestamp, recipient, context.

These are different axes:

| Same operation, different attestations | Same attestation, different operations |
|---|---|
| `AUTHORED { body→<chess-move> }` | `DELIVER { body→<chess-move>, recipient→Bob }` |
| `DELIVER { body→<chess-move>, recipient→Bob }` | `DELIVER { body→<scene>, recipient→renderer }` |
| `ACK { body→<delivery-record> }` | `DELIVER { body→<input-event>, recipient→view }` |

When a frame travels — across a wire, into persistence, into an attestation chain — it's commonly wrapped in an attestation frame whose `body→` binding points at the operation frame's CID. The receiver dispatches in two stages: outer attestation predicate (verifying signatures, checking trust, resolving routing/context), then inner operation predicate (running the actual call).

#### Pure-Operator Exception

A small class of predicates carry their own behavior — the "self-handling" exception:

- Pure operators like `ADD`, `MULTIPLY`, `NEGATE` whose behavior is a function of the bindings alone, with no contextual state.
- These compute on frame assembly without consulting any other item.

Use sparingly. The default is *behavior in items, not in predicates*.

A predicate can have a definition with no implementation — it's just a schema, a pure data template. A predicate with an implementation gets active behavior: the ADD operator knows how to evaluate arithmetic; the VIEW predicate knows how to open a window. The definition is universal; the implementation is runtime-specific. Multiple implementations can exist for the same predicate.

Because the predicate is an item, it carries its own frames — glosses, lexemes, and crucially, **EXPECTS** frames. These make it discoverable, nameable in any language, and self-describing.

### EXPECTS: Schema as Frames

How does a predicate declare its shape? With frames. EXPECTS is a second-order predicate — a frame about what frames a type's **instances** should carry. The EXPECTS predicate uses TOPIC roles to identify the expected predicate, and additional role bindings to constrain the expectation further.

A book type declares its shape entirely through EXPECTS frames on its own sememe:

```
sememe:book {
    GLOSS     { NAME:[ENGLISH]→"a written work" }
    EXPECTS   { TOPIC:[]→AUTHOR }
    EXPECTS   { TOPIC:[]→TITLE }
    EXPECTS   { TOPIC:[]→DESCRIPTION }
}
```

This says: "instances of Book should carry AUTHOR, TITLE, and DESCRIPTION frames." No special schema language — the schema IS frames, using the same primitive as everything else.

For richer types, EXPECTS frames can constrain specific roles within the expected predicate:

```
sememe:chess {
    GLOSS     { NAME:[ENGLISH]→"the game of chess" }
    EXPECTS   { TOPIC:[]→PLAYER,  THEME:[]→WHITE }
    EXPECTS   { TOPIC:[]→PLAYER,  THEME:[]→BLACK }
    EXPECTS   { TOPIC:[]→MOVE }
    EXPECTS   { TOPIC:[]→RESIGN }
}
```

Chess expects two PLAYER frames (distinguished by THEME — one White, one Black), plus MOVE and RESIGN frames. The constraints are semantic bindings, not a special constraint syntax.

This serves double duty:

- **Forward (creation guidance)** — when creating a chess item, the system knows it needs players, moves, and resignation capability. The UI can generate a creation form directly from EXPECTS.
- **Backward (duck typing)** — if an item carries PLAYER(White), PLAYER(Black), and MOVE frames, it structurally matches "chess" whether or not anyone explicitly tagged it as such.

The pattern is fully recursive: EXPECTS is itself a predicate with its own shape (it expects a TOPIC binding). Types describe their schemas using the same primitive they describe everything else with.

## Bindings: Data as Roles

Fillmore's frames describe situations with role-playing participants. Common Graph extends this: frames also **carry data**, and that data is expressed as bindings. Every piece of content — a title string, a video file, a sensor reading, a chess player — is a binding with a semantic role.

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

### The Item Binding

Every frame references the item(s) it relates to. Each predicate assigns the item binding to a **semantically appropriate role**:

- **Property predicates** (TITLE, AUTHORED, GLOSS) use `THEME:[]` — "this frame is *about* this item"
- **Event predicates** (MOVE, MESSAGE, BID) use `LOCATION:[]` — "this event happens *at* this item"
- **Binding predicates** (EQUALS) use `LOCATION:[]` — "this binding *lives on* this item"

The predicate's EXPECTS declaration specifies which role the item fills. Context filling automatically populates it from the focused item — the user never types the item reference explicitly.

```
TITLE      { THEME:→book,     NAME:→"The Hobbit" }    — THEME is the item
MOVE       { LOCATION:→game,  AGENT:→Fischer, ... }   — LOCATION is the item
EQUALS     { LOCATION:→item,  NAME:→"x", THEME:→expr} — LOCATION is the item
AUTHORED   { THEME:→book,     AGENT:→Tolkien }         — THEME is the item
```

The indexer indexes all indexed bindings that reference items in FRAME_BY_ITEM, regardless of role — indexing is controlled by each binding's index flag.

### Index: Per Binding

Every binding is either **indexed** or **non-indexed**:

- **Indexed** — creates a reverse-lookup entry in the FRAME_BY_ITEM index. If `AGENT:[]→Tolkien` is indexed, querying "frames involving Tolkien" finds this frame.
- **Non-indexed** — only reachable through the frame itself.

| index | Example |
|-------|---------|
| true | `AGENT:[]→Tolkien` — discoverable from Tolkien |
| true | `AGENT:[PLAYER, WHITE]→Fischer` — game state, indexed so you find games by player |
| false | `NAME:[]→"The Hobbit"` — title text, not indexed by string |
| false | `NAME:[MKV, SD]→transcode_CID` — derived content, not indexed |

## Body, Record, Endorsement, Frame

Bodies and Records are configurations of the unified **Datum** primitive (see `datum.md`). A Frame is a runtime container holding a body Datum and zero or more record Datums attesting it.

### Body — The Assertion

The immutable, content-addressed semantic fact. Its head reference points at the predicate (a meaning, via `@<predicate-IID>`). Two identical assertions from different people produce the same body CID — stored once.

```
Body Datum {
    head-reference:  Tag-6( @<predicate-IID> )
    bindings:        [Binding...]    // the assertion content
}

Body CID = hash(encoded Datum)
```

Bodies do not carry signatures. They are pure assertions. Anyone fetching by body CID gets bit-identical content.

### Record — The Attestation

Who said it, when, with cryptographic proof. Its head reference points at the body's CID (via `#<body-CID>`). Multiple records can attest the same body (same fact, different signers, different per-record config).

```
Record Datum {
    head-reference:  Tag-6( #<body-CID> )
    bindings:        [Binding...]    // signer (AGENT:[SIGNER]), timestamp (TIME:[SIGNED]),
                                     // optional CONFIG, etc. — semantic bindings using
                                     // the same vocabulary as bodies
    signature:       bytes           // varsig-formatted (algo prefix + sig bytes)
                                     // structurally distinct from bindings
}

Record CID = hash(full encoded Datum, including signature)
Signing payload = hash(encoded form WITHOUT signature)
```

The signature signs over the head reference + bindings (everything except itself). The record's own CID covers the full content including the signature. Because the signature signs the body via the head reference plus the record's bindings, tampering with any of these invalidates verification.

A frame = body + record(s). The body is the shared fact. Records carry everything else: who attests it, when, with what proof, and any per-attestation config or presentation choices.

### Endorsement — A Binding on the Manifest Body

An "endorsement" isn't a separate structure — it's an `ENDORSES` binding on the item's manifest body, with a body CID as target:

```
Manifest body bindings:
  ITEM_ID        → <self-iid>
  FOLLOWS        → <parent-VID>           (one or more)
  ENDORSES       → <frame-body-CID>       (one binding per endorsed frame)
  ENDORSES       → <frame-body-CID>
  IMPLEMENTATION → <code-bundle-CID>      (optional)
  CONFIG         → <config-data>          (qualifiers narrow the dimension)
  HANDLES        → ...                    (one binding per handled predicate; see Actor Model below)
```

Multiple ENDORSES bindings accumulate the manifest's endorsed-frame set. Per-record presentation/config preferences ride on the records attached to those frame bodies, not on the endorsement entry.

### Frame — Runtime Container

In-memory only. Holds the body Datum, record Datum(s), and the live decoded instance. Not serialized as a single unit — bodies and records are stored independently in the object store. The lookup key (selector) is derived from the body.

## Selector: Derived Key

The **selector** identifies a frame — computed from the body, not stored independently. It's the predicate + all qualifier IIDs from compound binding keys:

```
Body Datum:  LEXEME { THEME:[]→sememe, NAME:[ENGLISH, VERB, LEMMA]→"create" }
Selector:   (LEXEME, ENGLISH, VERB, LEMMA)

Body Datum:  TITLE { THEME:[]→book, NAME:[]→"The Hobbit" }
Selector:   (TITLE)

Body Datum:  VAULT { THEME:[]→Alice, LOCATION:[]→laptop, TOPIC:[LOCAL]→"/path" }
Selector:   (VAULT, LOCAL)
```

The home binding's target (THEME→sememe, THEME→book) is NOT part of the selector — it's implicit from the item context.

**Everything is a query.** The selector IS the fetch pattern. `(GLOSS, ENGLISH)` selects the English gloss. That's how you find it.

## The Manifest: A Body + Records, Like Everything Else

The manifest is the item's current-version Datum. It uses the same Body+Record structure as every other frame in the system:

```
Manifest body Datum {
    head-reference:   Tag-6( @<archetype-IID> )    // what kind of item this is
    bindings:         [
                        ITEM_ID        → <self-iid>,
                        FOLLOWS        → <parent-VID>,        // one or more
                        ENDORSES       → <frame-body-CID>,    // one or more
                        ENDORSES       → <frame-body-CID>,
                        IMPLEMENTATION → <code-bundle-CID>,
                        CONFIG         → <config>,
                        HANDLES        → <predicate-IID>,     // see Actor Model
                        ...
                      ]
}

Manifest record Datum {
    head-reference:   Tag-6( #<manifest-body-CID> )
    bindings:         [signer, timestamp, optional CONFIG, ...]
    signature:        bytes
}
```

The manifest body is just a Datum whose head is an *archetype* (the type of thing this item is) rather than a predicate. Its bindings carry the structural metadata of an item version. The manifest record is just a Record attesting that body — same shape as any attestation record.

The VID is the manifest body CID — the hash of the encoded body Datum. New version = new manifest body = new VID. Records on the manifest body carry signatures and per-signer config but don't change the VID.

The manifest signature covers all bindings on the body, including all ENDORSES entries. No separate per-frame signatures needed for endorsed frames — endorsement IS attestation by way of inclusion.

## Actor Model: Items Handle Frames

The runtime is an **actor model**: frames are messages, items are actors, predicates are message types.

- **Frames are data.** A frame describes something — a chess move, a chat message, a lookup request, a render request. The frame itself is inert.
- **Items are active.** An item has an implementation (a code bundle) that reacts to frames addressed to it.
- **Predicates classify messages.** They name the shape and meaning of a message kind. They generally do not carry behavior themselves.

When a frame is created and reaches an item — by being endorsed in its manifest, by being addressed to it via a recipient/location binding, or by being delivered through subscription — the item dispatches on the frame's predicate to choose how to react. The dispatch table is **declared in data**: see HANDLES below.

The pure-operator exception (predicates like `ADD` that compute from bindings alone) coexists with this model. Operators are *both* a message-shape and a self-handling actor; everything else has its handlers in items.

### HANDLES: APIs Declared as Endorsed Frames

An item declares which message types it processes by endorsing **HANDLES** frames in its manifest. Each HANDLES frame names a predicate the item handles, optionally with metadata (handler reference, arity, priority, etc.):

```
[HANDLES, {
    THEME      → <predicate-IID>,       // which message type
    INSTRUMENT → "applyMove",            // handler reference (method name OR @<code-item-ref>)
    ATTRIBUTE[ARITY] → 3,                // optional metadata
    ATTRIBUTE[PRIORITY] → ...,           // optional, for ordering polymorphic handlers
    ...
}]
```

The pattern `ATTRIBUTE[<kind>] → <value>` is generic — Arity is one such kind; Priority, Return-shape, Visibility, etc. attach the same way without requiring new roles.

A ChessGame's manifest endorses three HANDLES frames:

```
ChessGame manifest body:
  ITEM_ID  → <self>
  ENDORSES → <handles-frame-1-CID>      // [HANDLES {THEME→CHESS_MOVE, INSTRUMENT→"applyMove"}]
  ENDORSES → <handles-frame-2-CID>      // [HANDLES {THEME→RESIGN, INSTRUMENT→"handleResign"}]
  ENDORSES → <handles-frame-3-CID>      // [HANDLES {THEME→OFFER_DRAW, INSTRUMENT→"offerDraw"}]
```

Three properties fall out:

- **APIs are queryable.** Walk endorsed HANDLES frames on any item to introspect its full message-handler interface. Same query mechanism as everything else.
- **APIs inherit via archetype.** Sub-archetypes inherit parent HANDLES; instances inherit the type's. Method-dictionary inheritance, but data-driven.
- **APIs can extend at runtime.** Endorsing a new HANDLES frame adds a handler. No recompile. (Trust/policy controls govern whose HANDLES additions are accepted.)

### Why Endorsed Frames, Not Manifest Bindings

HANDLES frames are *endorsed* (referenced by ENDORSES bindings on the manifest), not direct manifest bindings. Direct manifest bindings stay reserved for **identity/structural** declarations (ITEM_ID, FOLLOWS, IMPLEMENTATION, ENDORSES, CONFIG, ARCHETYPE). API surface lives with claims/capabilities — endorsed frames — because they need rich, extensible metadata (arity, return shape, priority, qualifiers) and benefit from queryable composition. Keeping the manifest slim and reserved for identity, while richer declarations live as endorsed frames, is the structural rule.

### Polyglot Mapping

The `INSTRUMENT` binding can be:

- **A literal string** (Java method name) — for Java implementations using reflection
- **A reference to a code item** (`@<code-item-ref>`) — for swappable polyglot handlers
- **An expression frame** — declarative computed handlers (advanced)

Different language bundles can implement the same archetype's API. Same HANDLES list, same wire/storage format; the bundle provides predicate-keyed handler functions in whatever language. The wire format is the contract; the implementation is private.

## Config: Just Bindings

Config is a binding role, not a separate structure:

```
CONFIG:[]                    → general config
CONFIG:[PRESENTATION]        → styling/display
CONFIG:[REPLICATION]         → sync policy
```

Config bindings can live in three places, depending on what kind of config:

- **Type-level CONFIG** (defaults for all instances of a predicate) — frames on the predicate item itself
- **Per-instance CONFIG that's part of the assertion** — bindings on the body Datum (included in the body's CID)
- **Per-signer CONFIG** — bindings on the record Datum (each signer can specify their own preferences)

In all cases, CONFIG bindings are real semantic bindings, included in their Datum's content hash. There is no separate "non-identity" category — bodies hash all their bindings; records hash all their bindings plus the signature.

### Config Cascade

```
Type defaults              "Harvest records render as tables by default"
  | overridden by
Item manifest config       "THIS garden's harvest records use a custom chart"
  | overridden by
Frame record config        "THIS specific record (signer's choice) has special highlighting"
```

Most frames carry no config — they inherit from item and type.

## Frames Are Independent Entities

A critical principle: frames are **NOT "stored on" items**. Frames are independent, content-addressed objects in the object store. They *reference* items via their bindings, but they don't belong to any item until endorsed.

```
Frame created → stored in object store (independent)
  → record Datum signed by creator
  → Bindings reference items via roles (LOCATION, THEME, AGENT, etc.)
  → Indexed in FRAME_BY_ITEM for ALL item references in bindings
  → NOT "part of" any item until endorsed
```

This means:
- You can create a frame referencing ANY item — even someone else's
- The frame exists whether anyone endorses it or not
- Indexes make it findable by any item it references
- Endorsement is a separate act: the item owner includes the body hash in their manifest

**Creating a frame ≠ endorsing a frame.** These are separate operations with different authorization. Creation requires only a signing key. Endorsement requires ownership of the item (or forking it).

## Endorsed and Unendorsed

**Endorsed frames** are in the item's manifest. The item owner commits them. The manifest signature covers them.

**Unendorsed frames** reference the item but are not in its manifest. Each carries its own record Datum with an independent signature. Likes, annotations, trust attestations, comments from non-owners.

```
book:TheHobbit {
    // Endorsed (in manifest, covered by owner's signature)
    TITLE    { THEME:[]→book,  NAME:[]→"The Hobbit" }
    AUTHORED { THEME:[]→book,  AGENT:[]→Tolkien }

    // Unendorsed (independently signed record Datums, NOT in manifest)
    LIKE { THEME:[]→book, AGENT:[]→Alice }     [signed by Alice]
    LIKE { THEME:[]→book, AGENT:[]→Bob }       [signed by Bob]
}
```

The structural difference is only manifest inclusion. Same frame format. Promotion (owner endorses an unendorsed frame) just adds the body hash to the manifest.

### Commit Flow

Frames accumulate as uncommitted changes. The owner commits to endorse them:

1. User creates frames (via the item's prompt — typing assertions, making moves, entering data)
2. Frames are signed record Datums, stored in the object store, indexed — but not yet in the manifest
3. Uncommitted frames are visible locally; remote visibility is policy-dependent
4. User types `commit` → new manifest version endorses accumulated frames
5. Revert rolls back to the previous manifest version, un-endorsing the frames

This is analogous to git: frames accumulate like staged changes, commit creates a versioned snapshot.

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

## Queries: Just Incomplete Frames (No Wrapper)

A query is literally a frame with holes — *the frame itself IS the query*. **There is no QUERY wrapper predicate.** Don't invent one.

```
// Complete frame (an assertion):
AUTHORED { THEME:[]→TheHobbit, AGENT:[]→Tolkien }

// Query — who authored The Hobbit?
AUTHORED { THEME:[]→TheHobbit, AGENT:[]→? }

// Query — what did Tolkien author?
AUTHORED { THEME:[]→?, AGENT:[]→Tolkien }

// Query — all frames in this session (head=ANY wildcard):
[ANY, {LOCATION:[]→<session-CID>}]

// Query — all harvest records over 5kg:
HARVEST_RECORD { RESULT:[QUANTITY, WEIGHT]→(> 5kg) }
```

Same structure as assertions, just incomplete (or with `ANY` as head for cross-predicate queries). Evaluation fills the holes by matching against frames in the graph.

If a query needs **execution context** — ordering, return-shape, limits, recipient identity — that goes in a separate **attestation record** wrapping the query body, using the same record pattern as anywhere else:

```
Query body (operation):
    [ANY, {LOCATION→<session-CID>}]

Attestation record (request envelope):
    ASK { body→<query-CID>, signer→Alice, recipient→<librarian>,
          order→:by-time, return→:all, time→T }
```

Operation layer = *what to find*. Attestation layer = *who's asking, with what request context*.

### Categories Emerge From Filters — No Container Predicates

When you'd reach for a "container predicate" (INBOX, ACTIVITY_LOG, FEED, FRIENDS_LIST), **don't.** The container is just a query frame with `ANY` (or a more specific predicate) as head, plus binding filters. Different actions inside a session have different predicates; there is no umbrella predicate that wraps them.

| "Container" | Right shape |
|---|---|
| Inbox | `[ANY, {recipient→me, ...}]` (with filters for unhandled attestation records) |
| Activity log | `[ANY, {location→<session>}]` |
| Public feed | `[ANY, {retention→:durable, visibility→:public}]` |
| Friends list | `[ENDORSE-AS-FRIEND, {signer→me}]` |
| Chat history | `[MESSAGE, {location→<room>}]` |
| Work queue | `[ANY, {recipient→me, ack-status→:none}]` |

Categories emerge from filters, not from named containers. Activity log is the *query result*, never a stored thing.

## Expressions and Evaluation

User input at an item's prompt is always evaluated in the context of that item. There is no prompt without context — every prompt belongs to an item. The evaluation taxonomy:

Every frame evaluation has two orthogonal aspects: **action** (side effects) and **persistence** (stored as a fact). A frame can be either, both, or neither. The predicate's implementation — its `onFrameAssembled()` — determines which.

### Pure Actions (Ephemeral)

Some frames exist only for their side effect — they are evaluated, the effect happens, and the frame itself is not persisted:

```
create chess           → CREATE { THEME:→Chess }         → new item created
5 + 2                  → ADD { THEME:→5, GOAL:→2 }       → returns 7
```

The frame is assembled, evaluated, and discarded. The ActivityLog records that it happened.

### Pure Assertions (Persistent, No Side Effect)

Some frames exist purely as stored facts — their purpose is their existence, not any immediate effect:

```
title "The Hobbit"     → TITLE { THEME:→this_book_instance, NAME:→"The Hobbit" }
authored Tolkien       → AUTHORED { THEME:→this_book_instance, AGENT:→Tolkien }
```

The frame is created, signed, stored in the object store, and indexed. It references the context item via its EXPECTS-declared role (LOCATION, THEME, etc.), filled automatically by context filling. On commit, the owner endorses it. On someone else's item, it remains an unendorsed Frame.

### Both: Action + Assertion

Some frames are both — they produce a side effect AND persist as a fact:

```
view item   → VIEW { THEME:→item }                                   → view opens + ITEM_VIEW frame persisted on session
x = 5 + 2   → EQUALS { LOCATION:→item, NAME:→"x", THEME:→ADD(5,2) }  → evaluates to 7 + frame persisted
```

`view item` opens a window (action) and persists an ITEM_VIEW frame on the session — that's how open views are tracked and restored. `x = 5 + 2` evaluates the expression and returns the result (action) while storing the EQUALS frame as a live formula (assertion). Querying `x` later re-evaluates the expression — like a spreadsheet cell.

The `=` / `equals` / `is` / `es` (Spanish) / `ist` (German) tokens are all lexemes for the same EQUALS sememe. EQUALS stores the **expression** (the formula), not the evaluated result.

The distinction is not a property of the frame — it's a property of the predicate's implementation. The `onFrameAssembled()` behavior determines what happens when a frame is created. The same frame structure supports pure computation, pure storage, and everything in between.

### Incomplete Frames (Queries)

Bare sememes or partially-filled frames with no complete predicate:

```
chess                  → incomplete, no predicate → query: "items related to chess"
authored tolkien       → AUTHORED { AGENT:→Tolkien, THEME:→? } → query: "what did Tolkien author?"
```

### Bare Literals (Self-Evaluating)

A literal with no predicate is not a query — literals aren't items in the graph. They simply evaluate to themselves:

```
5                      → returns 5
"hello"                → returns "hello"
```

### Operators and Precedence

Operators (+, -, *, etc.) are sememes that declare parsing metadata via `contribute()` — precedence, fixity (infix/prefix/postfix), and associativity. The FrameAssembler reads this metadata to build correctly-nested expression trees. No separate expression parser is needed.

```
5 + 3 * 2  →  ADD { THEME:→5, GOAL:→MUL { THEME:→3, GOAL:→2 } }
```

Mathematical notation is language-neutral — the same operators, precedence rules, and function application work across all natural languages. The FrameAssembler handles this universally.

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

## Storage and Indexing

**FrameBodies** are stored content-addressed by hash in the object store. Two identical assertions produce the same hash — stored once.

**Content** (blobs, stream chunks, manifests) is stored by CID, referenced from binding targets.

**record Datums** are stored content-addressed, referenced from the RECORD_BY_BODY index.

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
- **One structural primitive**: Body and Record are configurations of the **Datum** primitive (`reference + bindings [+ signature]`). See `datum.md`.
- **Predicates are schemas**: Designing a predicate IS designing a database, a form, a spreadsheet. Roles are columns. Qualifiers constrain and distinguish.
- **Behavior lives in items, not predicates**: Frames are messages, items are actors, predicates classify message types. The receiving item dispatches on incoming-frame predicate via its endorsed HANDLES frames + `onFrameAssembled()`. Pure operators (ADD, MULTIPLY) are the rare exception that self-handle.
- **Item APIs are declared as endorsed frames**: HANDLES frames endorsed by an item's manifest declare which predicates it processes. Queryable, inheritable via archetype, runtime-extensible.
- **Manifest stays slim**: Direct manifest bindings reserved for identity/structural (ITEM_ID, FOLLOWS, ENDORSES, IMPLEMENTATION, CONFIG). Capability/claim metadata (HANDLES, EXPECTS) lives as endorsed frames.
- **Two predicate vocabularies**: Operation predicates name what's being done (ADD, LOOKUP, CHESS_MOVE). Attestation predicates name speech acts about operations (AUTHORED, ASK, DELIVER, ENDORSE, REVOKE, REDACT). Receivers dispatch in two stages: outer attestation, then inner operation.
- **Everything is a binding**: Content, references, local paths, config — all role-keyed bindings with compound keys.
- **Three parts per binding**: Role (always a sememe), qualifiers (sememes or literals — narrowing + constraints), target (the value). One per-binding flag: `index`.
- **Body is pure assertion**: A body Datum's head reference is to a meaning (predicate IID). Body CID = hash of full encoded form. No signature.
- **Record is attestation**: A record Datum's head reference is to content (`#<body-CID>`). Carries signer, timestamp, per-record CONFIG as semantic bindings. Signature is structurally distinct (varsig-formatted bytes), excluded from the signing payload.
- **Bodies are content-addressed**: Same assertion → same body CID, regardless of who attests it. Multiple signers produce multiple records pointing at one body.
- **Endorsement is minimal**: Body hash + optional record CID + mounts.
- **Frame is runtime**: In-memory container for body + record(s) + live instance.
- **Selector is derived**: Computed from the body's predicate + compound key qualifiers. Not stored independently.
- **Frames are independent entities**: A frame's body and records are stored content-addressed, independently of any item. Frames reference items via bindings — they are not "stored on" items.
- **Creating ≠ endorsing**: Anyone can create a frame referencing any item. Only the owner can endorse it (include it in the manifest). Unendorsed frames have records signed by their creator but are not in any item's manifest.
- **Item binding is semantic**: Each predicate declares which role the context item fills, with proper semantic meaning. TITLE uses THEME ("about this item"), MOVE uses LOCATION ("at this item"), EQUALS uses LOCATION ("lives on this item"). The indexer indexes all item references regardless of role.
- **Queries are just incomplete frames** (no wrapper): A `?` in a role, an unfilled binding, or `ANY` as head turns a frame into a query. Bare sememes are queries. Bare literals self-evaluate. Categories emerge from filters — no INBOX/LOG/FEED/ACTIVITY_LOG predicates.
- **Expressions are predicates**: Operators declare precedence/fixity via `contribute()`. The FrameAssembler handles precedence-climbing universally — no separate expression parser. Mathematical notation is language-neutral.
- **Config is bindings**: `CONFIG:[PRESENTATION]→styling`. Not a separate structure. Lives on the predicate item (type defaults), the body (per-instance), or records (per-signer). All are real bindings included in their Datum's hash.
- **Content is CID-addressed**: Blob (small), Chain (streams), Manifest (large/swarmable). Stream roots are immutable. Heads are derived.
- **Lifecycle is per-predicate**: Predicates declare lifecycle policies — retention (ALL, LATEST, CHAIN), persistence (FULL, NONE), lifetime (PERMANENT, PRESENCE, CONNECTION), signing requirement (REQUIRED, CONNECTION_AUTHENTICATED). This enables three temporal modes from one frame model: **durable** frames (persisted, signed, endorsed — a chess move, a message), **ephemeral** frames (LATEST retention, in-memory only, discarded on disconnect — avatar position, typing indicator, cursor), and **streaming** frames (TOPIC bindings pointing to Chains — video, audio, screen share). All use the same vocabulary, roles, subscriptions, and rendering. The Library handles them differently based on the predicate's declared lifecycle.
