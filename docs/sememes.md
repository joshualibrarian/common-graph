# Sememes

Common Graph builds meaning out of **sememes** — items whose purpose is to anchor distinct concepts. "Create," "author," "move," "kilogram," "language," "agent," "the Hobbit," "Tolkien" — each is a sememe, a globally addressable identity that other items reference when they need to carry that concept. Sememes are the vocabulary the system speaks; everything else assembles from them.

Two librarians referring to the same sememe agree on what that reference *means* by virtue of agreeing on the IID. There's no central registry, no negotiation protocol, no shared schema document. The vocabulary anchors meaning the same way content addressing anchors data: mathematically.

This document defines what a sememe is, how meaning gets globally anchored without coordination, how the bootstrap vocabulary is grounded in existing linguistic research, and how sememes relate to language, frames, and items.

This document assumes familiarity with [items](item.md), [frames](frames.md), and [the reference scheme](ref-scheme.md).

## What a sememe is

A sememe is an item — full stop, no parallel construct. It has an IID, a manifest, can be versioned, can carry endorsed frames, can be referenced through any of the prefix variants. What distinguishes a sememe from other items is purpose: a sememe exists to *be referenced as a unit of meaning*. Predicates are sememes; archetypes are sememes; thematic roles, qualifiers, units, dimensions, languages, and concepts at every level are sememes.

When a frame's binding carries `@THEME → @hobbit`, both the role (`@THEME`) and the target (`@hobbit`) are references to sememes. The role names a semantic function (the THEME slot in a relation); the target names a specific concept (the book). The frame's meaning emerges from the composition of these references.

Items that aren't sememes — a specific chess game, a personal document, a sensor reading — *use* sememes to express their content. They reference sememes in their bindings; they don't typically *become* sememes themselves. (They can, if the community decides they're significant enough to anchor as concepts.)

## Why globally anchored

A sememe's identity is its IID. That's it. There's no fallback registry of "official meanings," no validation server, no naming authority. Two librarians refer to the same sememe by IID, and they agree about what it means because they agree about the IID.

This is the same trick content addressing pulls: take a thing that would normally need a registry (here, a shared vocabulary; there, a shared file naming system) and make it self-anchoring by giving it a mathematical identity. Once the IID is fixed, agreement is automatic.

The mechanism scales because adding a sememe doesn't require permission. A user introduces a new concept by minting a new sememe item; it has an IID; anyone who wants to refer to it does so by that IID. Whether the rest of the network *uses* the new sememe is a social question (does anyone find it useful, does it propagate, do other items endorse frames pointing at it), but the *introduction* is a unilateral act.

## Empirical grounding

Sememes aren't invented from whole cloth. The bootstrap vocabulary comes from existing linguistic and conceptual resources that have done the empirical work of cataloguing meaning:

- **WordNet** — ~120,000 English concepts organized hierarchically as synsets, each grouping words that share a meaning. Forty years of curated lexicography. Concepts are linked by hypernymy, hyponymy, antonymy, meronymy.
- **CILI (Cross-Lingual Index)** — language-neutral concept identifiers that link wordnets across many languages. A concept in WordNet (English) and its counterpart in WOLF (French) point at the same CILI identifier; surface forms differ but meaning aligns.
- **VerbNet** — ~300 verb classes organized by syntactic and semantic behavior, each with declared thematic roles. The empirical mapping between "verbs in natural language" and "roles their arguments fill."
- **ISO 24617-4 (SemAF-SR)** — the standardized inventory of ~25 thematic roles (Agent, Theme, Goal, Source, Instrument, Recipient, …) used across frame semantics.
- **WordNet's friends** — synset hierarchies in many languages (Princeton WordNet for English, OpenWordnet for Brazilian Portuguese, Polish WordNet, German WordNet, etc.), all linkable through CILI.

These sources are imported as seed vocabulary. The Common Graph foundation starts populated: the universal thematic roles, the structural sememes (head, ITEM_ID, FOLLOWS, ENDORSES, HANDLES, IMPLEMENTS, …), the basic language items, the SI units and dimensions.

**Empirical, not foundational.** WordNet, CILI, VerbNet, and ISO 24617-4 are *trust-weighted starting points*. They're chosen because they're the best empirical work available in their domains, because they're freely licensed, and because they're widely respected. They're not architecturally privileged. A community building Common Graph from a different ontology — a domain-specific knowledge base, a different language family's lexical resources, a custom philosophical framework — can seed its own vocabulary. The protocol doesn't favor any particular source.

The Wittgensteinian framing: meaning emerges from use, not from decree. The seed vocabulary is what the protocol *defaults to* providing; what propagates and what doesn't is determined by which sememes get referenced in actual frames.

## Hierarchy and inheritance

Sememes form a head chain. Each sememe is an instance of some archetype (or another sememe, recursively); the chain bottoms out at Archetype itself. Add is an Operator is a Predicate is an Archetype. ChessGame is a Game is an Activity is an Item is an Archetype. Color is a Value is an Archetype.

The hierarchy is data. It lives in head pointers (a sememe's manifest's head names its parent). Walking the head chain is just walking references. Schemas inherit through it: a sub-archetype's effective schema is the union of its own `!`-bindings, its parent's `!`-bindings, and so on up to the meta-root. HANDLES inherit through it: an archetype's API surface includes its parent's HANDLES.

No separate inheritance graph, no parallel type system. The hierarchy is in the data model; it's queryable, walkable, and extendable by any user minting a new sememe whose head is an existing one.

See [`types.md`](types.md) for the meta-archetype tree and the structural roles different sememes play.

## Deterministic IIDs for bootstrap


Most IIDs are random — when a user mints a new item, the system generates 32 random bytes. But bootstrap sememes need to be agreed upon across implementations, so they use *deterministic* IIDs:

```
IID = multihash(SHA-256, "<canonical-key>")
```

Where the canonical key is a stable string. The Add predicate's IID comes from hashing `"cg.predicate:add"`; the Theme thematic role's IID comes from hashing `"cg.role:theme"`; the English Language item's IID comes from hashing `"cg.lang:eng"`.

Two librarians starting from scratch, with no shared state, both compute `IID = SHA-256("cg.predicate:add")`. They agree by construction. No registry, no handshake. The canonical-key strings are themselves shared by convention (published in seed vocabulary documentation, hashed independently by each librarian), but the agreement is mathematical once the strings are agreed.

This is what makes the seed vocabulary work across independently developed implementations. Different languages, different codebases, different network topologies — all converge on the same IIDs for the same concepts because they hash the same canonical keys.

Random IIDs are for user-minted items (specific chess games, personal documents, anything where global agreement isn't needed). Deterministic IIDs are for sememes that need universal recognition.

See [`seed-vocabulary.md`](seed-vocabulary.md) for the bootstrap mechanism and the canonical-key conventions.

## Sememes have the full item lifecycle

A sememe is an ordinary item. Its manifest carries its schema (`!`-bindings declaring what its instances look like), its HANDLES (if it's an archetype whose instances handle frames), its IMPLEMENTS (if it's a code item realizing some concept), its English / German / Japanese lexemes endorsed as frames.

Versioning works the same as for any item: edit the sememe's schema (rare but possible — a domain might refine what "Document" expects over time), commit a new manifest, the IID stays, the VID advances. Older versions remain accessible.

Endorsement works the same: another item can endorse a sememe's lexeme frames, claim a specific gloss as authoritative, or carry related frames in its own manifest. The sememe's manifest collects its own canonical content; other items can extend or relate.

The sememe can be a binding target the same as any item. It can travel across the network through Parley. It can be deleted (via DELETE frames, subject to the trust matrix). It's not a special structural construct — it's an item whose *role* in the system is to anchor meaning.

## Linguistic surface

A sememe is named in human language(s) through **lexeme frames** endorsed by Language items. A lexeme frame asserts "this surface form is a name for this sememe in this language":

```
{@lexeme, [
  @THEME → @create,
  @NAME:[@english, @lemma] → "create"
]}
```

The English Language item's manifest endorses thousands of such lexeme frames. The Spanish Language item's manifest endorses its own, pointing the same `@create` sememe at "crear". The Japanese Language item endorses "作る". The sememe is named in each language without itself being language-specific.

Surface forms resolve to sememes via the **TokenDictionary** — the runtime lookup that walks scope chains and returns matching postings. A user types "create"; the dictionary, given the user's active-language chain, returns the Create sememe; the parser proceeds.

Full mechanics of lexemes, the token dictionary, and language items live in [`language.md`](language.md) and [`vocabulary.md`](vocabulary.md). This doc just notes that the surface naming exists; the sememe itself is language-neutral.

## Sememes vs. literals

A binding's target is either a *reference* (one of the five prefix variants, often `@<sememe-iid>`) or a *literal* (a number, string, byte sequence, boolean, instant, decimal, rational). The two coexist throughout the system:

```
{@quantity, [
  @VALUE → 5,            ; literal target
  @UNIT → @meter         ; sememe reference
]}
```

Sememes carry shared meaning the system reasons about. Literals carry per-frame values whose interpretation comes from the surrounding bindings. The system uses both freely; literals don't need to be promoted to sememes to participate.

A new sememe is introduced only when a value *needs* to be referenced by name elsewhere. The number 5 doesn't get a sememe; the unit "meter" does, because other frames refer to meters. The English language gets a sememe (other items reference it as a context); your laptop's serial number might not (no one else refers to it by name).

## Worked examples

**The Create sememe.**

```
@create's manifest:
  head: @predicate
  bindings:
    @ITEM_ID → <create-iid>
    !THEME → ?archetype          ; expects: what to create
    !AGENT → ?signer              ; expects: who created
    @ENDORSES → #<en-create-lexeme>    ; "create"
    @ENDORSES → #<es-crear-lexeme>     ; "crear"
    @ENDORSES → #<ja-tsukuru-lexeme>   ; "作る"
    @ENDORSES → #<en-gloss>
    @ENDORSES → #<es-gloss>
```

Create is a predicate; its schema declares the slots Create frames carry; its surface forms in various languages are endorsed lexemes.

**The Theme thematic role.**

```
@theme's manifest:
  head: @role
  bindings:
    @ITEM_ID → <theme-iid>
    @ENDORSES → #<gloss-en>
    @ENDORSES → #<lexeme-en>
    @ENDORSES → #<lexeme-es>
```

A simpler sememe — no schema (roles don't have instance shape), just the identity and its linguistic surface. Used as a binding role in countless frames across countless predicates.

**The Hobbit (a specific entity).**

```
@hobbit's manifest:
  head: @book
  bindings:
    @ITEM_ID → <hobbit-iid>
    @AUTHORED_BY → @tolkien
    @TITLE → "The Hobbit"
    @PUBLICATION_YEAR → 1937
```

A specific named entity is a sememe in the sense that other items reference it (a citation, a review, a lending record). Its head is the Book archetype; its bindings are concrete.

## Relations

- [`item.md`](item.md) — what items are; sememes are items.
- [`frames.md`](frames.md) — how frames reference sememes through their bindings.
- [`types.md`](types.md) — the meta-archetype tree sememes form.
- [`language.md`](language.md) — language items and lexemes.
- [`vocabulary.md`](vocabulary.md) — the runtime vocabulary system, token dictionary, dispatch.
- [`seed-vocabulary.md`](seed-vocabulary.md) — the bootstrap pattern for sememes.
- [`ref-scheme.md`](ref-scheme.md) — how sememes are referenced (`@`/`?`/`!`).
