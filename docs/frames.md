# Frames

A frame is an immutable, semantically-shaped statement about a relationship in meaning. "Tolkien authored The Hobbit," "Alice moved her king's pawn from e2 to e4," "this image is named 'sunset.jpg' in English," "5 + 3" — each is one frame, one body with a predicate as its head and bindings supplying the slots that predicate names. Frames are the glue between everything else in the graph: the assertions that say *what is so*, the messages that say *do this*, the responses that say *here is the result*. If items are the persistent things, frames are the meanings being passed between them.

A frame is *occurrent*, not *continuant*. It happens; it doesn't endure. Frames are not versioned and never edited. When the meaning of a relationship needs to change, a new frame is created — a different body, with a different identity, signed in its own time. The old frame remains exactly what it was the moment it was committed.

This document defines frames, their semantic shape, their lifecycle, and how items reach for them.

This document assumes familiarity with [the datum primitive](datum.md) and [the reference scheme](ref-scheme.md).

## A frame is a datum whose head is a predicate

Structurally, a frame body is the simplest kind of datum: a head plus bindings, no other distinguishing structure. What makes it a frame is that its head names a predicate — a sememe that declares what relationship the frame asserts.

```
{<predicate>, [
  <role>:[<qualifiers>] → <target>,
  ...
]}
```

The predicate is the *kind* of meaning-relationship; the bindings supply the participants in that relationship.

The head determines what kind of body this is. Many body shapes share the same structural form — frames, manifests, value bodies (Color, Quantity, Length, …), schema bodies, query bodies, code items — and they're distinguished by what their head names and which bindings they carry. A frame is specifically a body whose *head is a predicate*. A body whose head is an archetype is something else: a manifest if it carries `@ITEM_ID → <iid>` (anchoring it in an item's lineage), a value body if it doesn't (a typed value like a Color), or another kind depending on the archetype's intent. Structure alone doesn't tell you which; head and bindings do, together.

This document is about frames specifically — the predicate-headed family. The other body shapes have their own docs: [`manifest.md`](manifest.md) for manifests, [`values.md`](values.md) for typed values, [`ref-scheme.md`](ref-scheme.md) for schema and query forms.

Frames may carry records attesting them, just as manifests do. An unsigned frame body is data that someone *could* assert; a signed frame body is data that someone *has* asserted. The same body can accumulate multiple records over time, each from a different signer, each constituting an independent attestation of the same semantic claim.

## The predicate is the schema

A predicate is itself an item — a sememe in the linguistic backbone with its own manifest. That manifest carries the predicate's expected shape directly: schema-prefixed bindings declaring which roles a frame using this predicate is expected to fill.

```
{@predicate, [
  @ITEM_ID → <add-iid>,
  !THEME → ?number,
  !THEME → ?number
]}
```

The Add predicate's manifest declares that frames headed by Add carry two THEME bindings, each with a target matching the Number pattern. Both operands play the same semantic role — Add is commutative, so there's no need to distinguish a "left" from a "right" — and the multiset shape of bindings carries the two-ness naturally. There is no separate EXPECTS frame; the predicate's own manifest *is* the schema, with the `!` prefix marking the slots a frame is expected to fill.

Validating a frame against its predicate is straightforward: walk the predicate's `!`-prefixed bindings and check the frame has matching concrete bindings whose targets match the constraints expressed with `?`. Frames that conform are valid uses of the predicate; frames that don't can still exist as data but are flagged as non-conforming when read.

The schema-IS-the-thing principle keeps the system small. Predicates don't need a parallel schema language; the data model that describes a predicate is the same data model used everywhere else.

## Thematic roles: the vocabulary of meaning

Most frames lean heavily on the thematic role inventory — a small standardized set of sememes naming the universal slots that meaning-relationships fill:

- **AGENT** — who's doing the acting.
- **THEME** — what's being acted upon, located, or asserted.
- **GOAL** — the abstract endpoint or target.
- **SOURCE** — the origin or starting point.
- **INSTRUMENT** — the tool or means used.
- **RECIPIENT** — the entity receiving a transfer.
- **TIME** — when something happened.
- **LOCATION** — where something happened.
- **MANNER** — how something was done.
- **CAUSE** — what brought it about.
- **PARTNER** — a co-participating agent.
- **VALUE** — a measurement, score, count.
- **NAME** — a human-readable label.
- **ATTRIBUTE** — a generic structured property.

These come from Fillmore's frame semantics and the empirical work that followed — VerbNet, FrameNet, ISO 24617-4. Common Graph treats them as the universal vocabulary for the *participant slots* in any meaning-relationship, applicable equally to natural language, code, math, games, and protocols.

Roles aren't required — a predicate may declare any binding it likes — but they're the default, because the same handful of slots cover an extraordinary range of semantic relationships. Two frames using the same thematic roles are structurally comparable even when their predicates are entirely unrelated.

Qualifiers narrow roles. A frame may carry `@NAME:[ENGLISH, LEMMA] → "create"` and `@NAME:[SPANISH, LEMMA] → "crear"` — same role, different qualifiers, both bindings present without conflict. Qualifiers are how compositional precision is expressed without expanding the role inventory.

## Frame lifecycle

Frames flow through a small set of states, none of which are mutations of the frame itself.

**Assembly.** A frame body is built — from text entering through the input pipeline, from network arrival, from a programmatic call. The assembly process produces a body whose head is a predicate and whose bindings match (or partially match) the predicate's schema.

**Attestation.** A signer hashes the body, signs the hash, and produces a record whose head is the body's DatumID. Multiple signers may produce records independently; each constitutes its own attestation. An unsigned frame body is valid data but carries no claim of authorship.

**Submission.** The frame is handed to a librarian — either local or remote. The librarian persists the body, persists any records, and routes the frame to the items it concerns.

**Routing.** Items referenced in the frame's bindings (via `@`-prefixed targets) are notified that a frame concerning them has been assembled. Items whose archetype's HANDLES declarations match the frame's predicate get a dispatch call. The receiving items may produce reply frames in response.

**Endorsement (optional).** An item may *endorse* a frame by adding a binding on its own manifest referencing the frame's datum hash. The role of that binding names the relationship — `ENDORSES` for content the item commits to as part of its version, `HANDLES` for protocol declarations, `MENTIONS` for citation, anything else the item's vocabulary supports. Endorsement is how frames become part of an item's persistent history; unendorsed frames remain free-floating, independently signed, but not pinned to any item's lineage.

**Storage.** Body and records both live in the content-addressed object store. Frames are not stored as monolithic units; they're stored as their constituent body and record datums, and the runtime aggregates them as needed.

Nothing in this lifecycle mutates the frame. Each state transition either creates new data (records, endorsement bindings on manifests) or computes something downstream from the frame (dispatch). The frame body, once committed, is immutable.

## Frames as messages

When an item processes incoming frames, the model is Smalltalk-flavored message passing. Frames are messages; items are receivers; predicates classify message types. An item declares its API via HANDLES bindings on its manifest: each HANDLES binding names a predicate the item processes, and the dispatch flow finds the receiving item by its identity and the predicate by the frame's head.

This is the same machinery used everywhere: there's no separate RPC, no separate event bus, no separate command system. A user types "move pawn to e4" into a chess game's prompt; the input pipeline assembles a `MOVE` frame referencing the game item; the librarian routes the frame to the game; the game's MOVE handler updates its state. The same flow handles network-arriving frames, programmatic-API frames, and bridge-translated frames from external systems. One pipeline, one shape.

Reply frames are themselves frames — same structure, same submission path, headed by whatever response predicate fits. A `LOOKUP` frame produces `POSTING` reply frames; a `CREATE` frame produces no reply but mints a new item whose construction the original frame implicitly authorized.

The detailed mechanics of HANDLES, IMPLEMENTS, and dispatch live in [`api.md`](api.md). The frame's job in this story is just to *be a message* — semantic, signed, routable, complete.

## Worked examples

**A simple authorship assertion.**

```
{@authored, [
  @AGENT → @tolkien,
  @THEME → @hobbit
]}
```

Two thematic roles. The AGENT (Tolkien) did the action; the THEME (The Hobbit) is what was acted upon. The predicate AUTHORED supplies the meaning of the action itself.

**An arithmetic frame.**

```
{@add, [
  @THEME → 5,
  @THEME → 3
]}
```

Both operands carry the role THEME — Add is commutative, so they're semantically interchangeable. The result of the operation flows back as a reply frame or a returned value, not as a binding on the input frame itself. Non-commutative operations like Subtract break the symmetry by using *different* roles for the two operands (a SOURCE being reduced, a THEME being subtracted) rather than positional indexing.

**A chess move.**

```
{@move, [
  @AGENT → @alice,
  @THEME → @king-pawn,
  @SOURCE → @e2,
  @GOAL → @e4,
  @LOCATION → @game-iid-7f3a
]}
```

Five bindings. AGENT is who moved; THEME is which piece; SOURCE and GOAL are the start and end squares; LOCATION names the game the move belongs to. The thematic vocabulary covers chess moves with the same roles that cover authorship and transfer.

**An ephemeral query.**

```
{@lookup, [
  @THEME → "tolkien",
  @ATTRIBUTE:[LIMIT] → 10
]}
```

THEME carries the query token; an ATTRIBUTE binding with LIMIT qualifier caps the result count. LOOKUP frames are ephemeral by predicate declaration — their bodies are not retained after dispatch. The reply frames are normal frames carrying postings.

**A semantic relationship between items.** When an archetype's manifest declares "I accept MOVE frames," it does so with a HANDLES binding:

```
@HANDLES → @move
```

No `!` or `?` on the target. The semantic relationship (HANDLES) lives in the role; the target is a concrete reference to the MOVE predicate item; the MOVE predicate's own manifest carries the schema for what MOVE frames look like.

## Frames vs manifests, side by side

| Frame body | Manifest body |
|---|---|
| Head is a predicate | Head is an archetype |
| No `@ITEM_ID` binding | Carries `@ITEM_ID → <iid>` |
| Free-standing semantic statement | One version in a lineage |
| Immutable; never edited | Immutable; new versions replace it via FOLLOWS |
| Routes through dispatch | Records the structured state of an item |
| About *meaning* | About *persistence* |

Structurally identical bodies, functionally distinct because of what their heads name and which bindings they carry. The two are the most-used pair, but they aren't the only kinds of body in the system — value bodies, schemas, queries, and code items are equally first-class, equally datum-shaped, and equally distinguished by their own combinations of head and bindings.

## Relations

- [`datum.md`](datum.md) — the structural primitive frames are built from.
- [`ref-scheme.md`](ref-scheme.md) — the five reference prefixes used in bindings.
- [`manifest.md`](manifest.md) — bodies that anchor frames into item lineages.
- [`item.md`](item.md) — what gets versioned; what frames refer to.
- [`api.md`](api.md) — HANDLES, IMPLEMENTS, and the dispatch flow for frame-as-message.
- [`vocabulary.md`](vocabulary.md) — the sememes used as roles and qualifiers.
- [`language.md`](language.md) — thematic roles in the linguistic backbone.
