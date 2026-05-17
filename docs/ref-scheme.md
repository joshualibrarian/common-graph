# The Reference Scheme

A reference in Common Graph identifies a target — an item, a piece of content, a structural datum, a shape, a pattern. References are how the graph's nodes link to each other, and they carry more information than a bare pointer. The reference itself is typed: a leading prefix declares not just *what* the reference points at but *how* it points.

The reference scheme is the bedrock primitive every other layer rests on. Frames carry references in their bindings. Manifests carry references to endorsed frames. Schemas declare expected shapes as references with a particular prefix. Queries express patterns as references with another prefix. The same shape — a target with a leading typing byte — serves every linking need in the system.

This document defines the five prefixes, how they compose, and what each enables.

## The five prefixes

| Prefix | Name | Refers to |
|---|---|---|
| `@` | inherit | An item by identity. Use this exact item; materialize the runtime form on demand. |
| `?` | query | A pattern. Match anything fitting this shape. |
| `!` | schema | A template. The expected shape for whatever fills this slot. |
| `~` | content | Bytes addressed by their digest. |
| `#` | datum | A structural datum addressed by its merkle hash. |

`@`, `?`, and `!` operate on *items* — named, identity-bearing entities that have manifests and behavior. `~` and `#` operate on *content* — bytes and structures addressed by hash. The lattice splits cleanly along that line.

A reference carries exactly one prefix. The prefix is the first byte of the reference's payload; everything after it identifies the specific target.

## Item references: `@`, `?`, `!`

The three item prefixes name three distinct relationships to an item.

**`@` (inherit) — concrete reference.** "Use this exact item." When a frame's binding has target `@hobbit`, it refers to the specific item with that identity. The runtime materializes the item on demand: fetches its current manifest, loads its implementation, makes it available for whatever needs to read from it or send messages to it.

**`?` (query) — pattern match.** "Match anything fitting this shape." When a frame's binding has target `?piece`, it doesn't name a specific piece — it specifies that the slot accepts anything whose archetype is or descends from Piece. Used in schemas to constrain target shapes, and in queries to express search patterns.

**`!` (schema) — template marker.** "This slot's expected shape." When an archetype's manifest carries a binding `!PLAYER:[WHITE]`, it declares that instances of the archetype should carry a binding with that role-and-qualifier shape. The `!` prefix marks the binding as descriptive (a slot to be filled) rather than concrete (a value already present).

The three are not interchangeable. `@hobbit` says "this book"; `?book` says "any book"; `!book` says "expects a book here." Same item-target, three relationships, three distinct prefixes — never combined.

## Content references: `~`, `#`

The two content prefixes name two distinct addressings of bytes.

**`~` (content) — content hash.** "These exact bytes." The reference resolves to a content-addressed blob: image data, source code, an audio chunk, the result of a hash computation. The target is opaque; the reference says "fetch the bytes that hash to this." This is the addressing IPFS made standard.

**`#` (datum) — structural hash.** "This exact structural datum." Where `~` addresses raw bytes, `#` addresses a datum's *structure* — its merkle hash over head and bindings. Two datums with identical structure but different encoding forms share the same `#` identity; two datums that encode to the same bytes but have different structures (rare, but possible under redaction or transformation) do not.

`#` is what you use to point at a frame's body, an attestation record, a schema-frame, or any other structured datum. `~` is what you use to point at raw bytes that aren't themselves datums.

## Shape vs semantics

A critical discipline: the prefix describes *structure*, never *meaning*.

`?` says "match this pattern." It does not say "I handle frames of this kind" — that's a semantic relationship, and it lives in the *role* of the binding (HANDLES), not in the prefix of the target.

`!` says "expects this shape." It does not say "this is a constraint on my behavior" — that's a behavioral relationship, again expressed by the role.

Roles carry semantic vocabulary: HANDLES, IMPLEMENTS, CONTAINS, FOLLOWS, ENDORSES, NAME, TITLE, AGENT, THEME. Each role is itself an item — a sememe whose meaning is anchored in the linguistic backbone. The binding's role tells you *why* this reference is here; the prefix on the target tells you *how* to interpret it.

A binding `@HANDLES → @move` reads as "I handle MOVE-headed frames" — concrete role-reference, concrete target. A binding `!PLAYER:[WHITE] → ?user` reads as "I expect a WHITE player binding whose target is some user" — the role-reference takes the `!` prefix because it's a schema slot; the target takes `?` because it's a pattern.

Each reference in the binding carries exactly one prefix. Prefixes never stack — a binding's role is *either* concrete (`@`), *or* schema (`!`), *or* a query (`?`); a target is *either* concrete, *or* schema, *or* query, *or* a content/datum hash. There is no notation that combines them, because there is no need: the prefix typing is one-dimensional.

## Composability

The five prefixes are mutually exclusive on any given reference. A reference is `@iid` *or* `?iid` *or* `!iid` *or* `~cid` *or* `#cid` — never any combination. The prefix lattice is a choice of typing, not a tower of modifiers. The `@` prefix is the default concrete-item form; the other prefixes *replace* it rather than layer on top.

Within a single body, prefixes mix freely between bindings. The same manifest can carry literal bindings (`@ITEM_ID → <iid>`) alongside concrete references (`@IMPLEMENTS → @add`) alongside schema bindings (`!THEME → ?number`) alongside endorsements (`@ENDORSES → #<frame-cid>`). Each binding's role and target make independent prefix choices — or, in the case of literal targets, no prefix at all.

Within a single binding, prefixes mix between role and target. The role is `@`-prefixed (it names a sememe); the target takes whichever prefix the binding's purpose calls for.

When a binding's target is itself a datum reference (`#<cid>`), the referenced datum's bindings have their own prefix choices independent of the parent. The lattice nests cleanly through arbitrary depths.

## Worked examples

**A concrete Color value.**

```
{@color, [
  @R → 255,
  @G → 0,
  @B → 0
]}
```

Three concrete bindings, integer literals as targets.

**A Color schema.**

```
{@color, [
  !R,
  !G,
  !B
]}
```

Same head, but the bindings are `!`-prefixed — declaring "instances of color carry R, G, B bindings." This is the schema. The instance and the schema share structural shape; the prefixes differ.

**The Add predicate.**

```
{@predicate, [
  @ITEM_ID → <add-iid>,
  !THEME → ?number,
  !THEME → ?number
]}
```

The `@ITEM_ID` binding's target is a literal — the raw identity bytes — not a reference. The manifest *declares* its identity; it doesn't point at it.

The predicate's manifest declares its own schema. `!THEME → ?number` reads as "expects a THEME binding whose target matches the Number pattern." Two `!THEME` bindings declare arity-two: Add takes two THEME operands, both of which must match the Number pattern. The predicate *is* its own schema — no separate declaration needed.

**An Add frame using that predicate.**

```
{@add, [
  @THEME → 5,
  @THEME → 3
]}
```

Concrete head, two THEME bindings, literal numeric targets. Both operands carry the same role — Add is commutative, so they're semantically interchangeable. The frame can be validated against `@add`'s schema by walking the parent's `!`-bindings and confirming the frame supplies a matching multiset of concrete bindings.

**A query for any pawn move by white.**

```
{?move, [
  ?AGENT → ?white-player,
  ?THEME → ?pawn
]}
```

Every prefix is `?`. The query matches frames whose head is `@move` with an AGENT slot referencing some white player and a THEME slot referencing some pawn. No specific item is named; the pattern is the assertion.

## Encoding

A reference's byte layout is the reference protocol's contract, independent of any wrapping encoding format. The layout:

```
<prefix-byte> <target-bytes> [ \\ <sub-part> [ \\ <sub-part> ] ]
```

- **`<prefix-byte>`** — one of five fixed bytes, matching the textual prefix character:

  | Prefix | Byte | Variant |
  |---|---|---|
  | `@` | 0x40 | item (concrete) |
  | `?` | 0x3F | item (query) |
  | `!` | 0x21 | item (schema) |
  | `~` | 0x7E | content hash |
  | `#` | 0x23 | datum hash |

- **`<target-bytes>`** — identifies the target:
  - For `@`/`?`/`!`: a multihash (the IID).
  - For `~`: a multihash (the content hash).
  - For `#`: a multihash (the datum hash).

- **`<sub-part>`** — optional drill-in components, separated by the byte `0x5C` (`\`). Only meaningful for some forms:
  - `@<iid>\<vid>` — an item pinned to a specific version (the VID is a datum hash).
  - `#<frame-cid>\<binding-key>` — a specific binding within a frame.
  - `#<frame-cid>\<binding-key>\<portion-spec>` — a portion within a binding's content.

The single-prefix discipline holds in the byte layout the same way it holds in the textual form: every reference has exactly one prefix byte. The `0x5C` separator is for sub-parts within a single reference, not for combining prefixes.

The textual rendering and the wire encoding are isomorphic — a reference is the same thing in either form, just byte-rendered or character-rendered. Implementations may use either when displaying references to users.

## What an encoding distinguishes

Common Graph is encoding-agnostic at the data-model level. The datum primitive — head plus bindings — is the same whether wrapped in CBOR, JSON, msgpack, or a future binary format. What any encoding *must* be able to distinguish is the set of semantic primitives Common Graph defines:

- **REF** — a typed reference, byte-laid-out as above.
- **BODY** — a 2-position datum: head and bindings.
- **RECORD** — a 3-position datum: head, bindings, and signature.
- **SIG** — a varsig-encoded signature attached to a record.
- **KEY** — a multikey-encoded cryptographic key.
- **RATIONAL** — a 2-element rational number (numerator, denominator).
- **REDACTED** — a Merkle-elision marker carrying the preserved hash.
- **ENCRYPTED** — an encrypted-envelope marker around an inner datum.

An encoding satisfies the contract by giving each of these a recognizable shape — in CBOR, that's a distinguishing tag number; in JSON, it might be a discriminator field; in a flat binary format, a header byte. The specifics belong to the encoding; the *categories* belong to Common Graph.

For the concrete tag-number assignments in CG's primary encoding, see [`cg-cbor.md`](cg-cbor.md). For non-CBOR encodings to interoperate, they need their own discriminator scheme but the same set of distinctions.

## What the prefix lattice completes

Content-addressed linking is not new. IPLD established that data structures with cryptographic hash links to other data structures could serve as a general substrate for linked, verifiable, decentralized data. IPLD's primitive is the CID — a content hash. Two structures linked by CID share the property that the link is verifiable: dereferencing the CID yields exactly the data that produced it, or fails.

What IPLD didn't reach: typing on the link itself. A CID in IPLD says "this points to bytes whose hash is X." It does not say *how* you're pointing — whether for identity, for query, for schema. Those distinctions, when IPLD needed them, lived in a separate layer (the IPLD Schema DSL) bolted on top of links rather than baked into the link primitive.

The five-prefix scheme completes the picture by promoting the link to a typed primitive. The same shape — a leading byte plus a target — serves identity references, query patterns, schema templates, content addressing, and structural addressing. There is no second layer; the typing is in the reference. Schemas, queries, and links all consume the same primitive, distinguished only by prefix.

This is what lets the rest of the system stay simple. A manifest's bindings, a frame's bindings, a schema's bindings, a query's bindings, all use the same encoding, all parse through the same code path. Adding a new way to link — should we ever need one — would mean adding a sixth prefix, not a new layer.

## Relations

- [`datum.md`](datum.md) — the structural primitive that references identify.
- [`frames.md`](frames.md) — how references are used in frame bindings.
- [`item.md`](item.md) — what an item-reference targets.
- [`cg-cbor.md`](cg-cbor.md) — the byte-level encoding of each prefix.
