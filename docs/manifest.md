# Manifests

A manifest is the body for one version of an item. It records what the item looks like at a particular moment in its history — identity, structure, content, parent versions, signing — captured as bindings on a body, attested by records, addressable by its structural hash. A new manifest is committed each time the item changes; the chain of manifests, linked by their parent references, is the item's lineage.

Items are the *continuant* — what persists through change. Manifests are the *occurrent* snapshots of that persistence. An item without manifests is identity without history; a manifest without an item it belongs to is just a frame.

This document defines the manifest body, the bindings that constitute it, and how chains of manifests express version history.

This document assumes familiarity with [the datum primitive](datum.md), [the reference scheme](ref-scheme.md), and [frames](frames.md).

## A manifest is a body with `@ITEM_ID`

Structurally, a manifest is a body — head, bindings, optional records attesting it. What makes it a manifest is one binding:

```
@ITEM_ID → <iid>
```

The target is a literal: the item's identity itself, raw bytes. Not a reference (there's nothing to dereference — the identity is right there). Not a hash of something else. The IID *is* the identity, and it appears on the manifest body the same way a name appears on a passport.

With this binding, the body becomes a manifest — one version in some item's history. Without it, the same body shape is something else: a frame if its head is a predicate, a typed value (Color, Quantity, Length, …) if its head is a value archetype, a schema or query body if its bindings are `!`- or `?`-prefixed, a code item if its head is the Code archetype. The `@ITEM_ID` binding is what makes *this particular body shape* a manifest; other body shapes without `@ITEM_ID` are their own kinds of thing.

Everything else a manifest carries — version history, endorsed content, configuration, implementation declarations — sits in additional bindings on the same body. There is no wrapper, no envelope. The manifest is just a body with particular bindings.

```
{<archetype>, [
  @ITEM_ID → <iid>,
  @FOLLOWS → #<parent-vid>,
  @ENDORSES → #<frame-cid>,
  ...
]}
```

The head is the archetype this body is an instance of. The bindings declare identity, parent versions, endorsed frames, and whatever else the archetype's schema calls for.

## Identity, version, and content

An item has three distinct identifiers, each playing a different role.

**IID** — the item's identity. Stable across every version. Lives on every manifest body as the `@ITEM_ID → <iid>` binding's literal target. The IID is what you point at when you mean "the chess game between Alice and Bob," regardless of which move number we're on.

**VID** — a specific version's identity. Each manifest body has its own structural hash (a DatumID), and that hash is the VID for that particular version. Two manifests for the same item have the same IID but different VIDs.

**ContentID** — a manifest body's encoded-bytes hash, specific to whichever encoding produced the bytes. Used when the wire-form bytes specifically matter (storage indexing, transport verification, byte-level deduplication). Distinct from the VID — they're different hashes computed from different inputs (structure vs. bytes). Both reliably identify the version; they just answer slightly different questions.

The IID is identity. The VID is "which version" (structural). The ContentID is "which bytes" (per-encoding). Three layers of address for three kinds of question. See [`datum.md`](datum.md) for the structural-walker / encoding split that makes this work.

## The lineage

Items express version history through `@FOLLOWS` bindings — each manifest declares which prior versions it follows. The targets are `#`-prefixed references to parent manifest bodies' VIDs.

```
@FOLLOWS → #<parent-vid>
```

A manifest with no `@FOLLOWS` is an inception manifest — the first version of the item. A manifest with one `@FOLLOWS` is a sequential commit on a linear history. A manifest with multiple `@FOLLOWS` is a merge — the version unifies multiple parent branches.

The lineage as a whole is a directed acyclic graph: every manifest has zero or more parents; no manifest has itself as an ancestor; many manifests can share parents (branches), and many manifests can merge parents (merges).

A specific manifest is a snapshot. The chain rooted at any manifest is a history.

## Bindings on a manifest

Beyond `@ITEM_ID` and `@FOLLOWS`, a manifest's bindings express what's in this version of the item. The vocabulary is open — any role and qualifier combination is valid — but the standard bindings cover most needs:

- **`@ITEM_ID → <iid>`** — the item's identity (literal, required).
- **`@FOLLOWS → #<vid>`** — parent version (zero or more).
- **`@ENDORSES → #<frame-cid>`** — a frame this version pins as content.
- **`@HANDLES → @<predicate>`** — declares this archetype processes frames headed by this predicate. (Most relevant on archetype manifests, where it declares the API surface of the archetype's instances.)
- **`@IMPLEMENTS → @<archetype-or-predicate>`** — declares this item is a realization of the given concept. (Most relevant on code item manifests.)
- **`@<language>:[<form>] → <code-ref>`** — declares the implementation form. The role is the language sememe (Java, Python, Lisp); the qualifier is the form (ClassName, SourceCode, Bytecode); the target is the actual code reference.
- **`@CONFIG:[<dimension>] → <value>`** — per-dimension configuration, presentation, policy.

Plus whatever bindings the archetype's schema declares — a chess-game archetype expects player bindings; a document archetype expects title and author bindings; a code archetype expects an implementation binding. The archetype's manifest carries the schema; instance manifests fill it in.

Schema-prefixed bindings (`!...`) on an archetype's manifest declare *what its instances should carry*. Concrete bindings (`@...`) on an instance's manifest *satisfy* those declarations.

## Archetype manifests vs instance manifests

A manifest's bindings answer different questions depending on whether the body is an archetype or an instance.

**An archetype manifest** describes what *its instances* should look like. The bindings include the archetype's own identity (`@ITEM_ID → <archetype-iid>`), its schema (`!`-prefixed bindings declaring expected instance bindings), and any API declarations (`@HANDLES → @<predicate>`).

```
{@archetype, [
  @ITEM_ID → <chess-game-archetype-iid>,
  !PLAYER:[WHITE] → ?user,
  !PLAYER:[BLACK] → ?user,
  !TURN → ?color,
  @HANDLES → @move,
  @HANDLES → @resign,
  @HANDLES → @offer-draw
]}
```

**An instance manifest** describes the specific entity. Its bindings fulfill the archetype's schema with concrete values, plus whatever instance-specific bindings the version carries.

```
{@chess-game, [
  @ITEM_ID → <specific-game-iid>,
  @FOLLOWS → #<prior-vid>,
  @PLAYER:[WHITE] → @alice,
  @PLAYER:[BLACK] → @bob,
  @TURN → @white,
  @ENDORSES → #<move-1-frame-id>,
  @ENDORSES → #<move-2-frame-id>
]}
```

Same body shape. Different role: one is the template, one is a filled-in instance, both expressed in the same data model.

## Attestation

A manifest body, like any body, is hashed and signed by a record. The record's head is the body's DatumID; its bindings carry the signer, time, and any attestation metadata; its signature attests.

A manifest body with no records is an unsigned declaration — valid data but uncommitted. A manifest body with one record is a signed version. Multiple records on one body represent multiple parties co-attesting the same version, which is rare for ordinary items but useful for items whose authority is shared (multi-signature accounts, co-edited documents).

The runtime aggregate of a manifest body and its records is conceptually the same as a frame: a body with attestations. Manifests and frames share the body-plus-records pattern; the structural distinction is purely the `@ITEM_ID` binding.

## Implementation bindings

A manifest may declare how the item is realized in code through implementation bindings. The shape:

```
@<language>:[<form>] → <code-ref>
```

The role is the language sememe — `@JAVA`, `@PYTHON`, `@LISP`, `@JAVASCRIPT`, etc. The qualifier is the form of code reference — `ClassName` for a class identifier, `SourceCode` for inline source text, `Bytecode` for compiled bytes. The target is the actual reference, varying by form.

Examples:

```
@JAVA:[ClassName] → "dev.everydaythings.graph.game.ChessGame"
@PYTHON:[SourceCode] → "def evaluate(body): return body['lhs'] + body['rhs']"
@LISP:[SourceCode] → "(defun evaluate (body) ...)"
@JAVA:[Bytecode] → ~<bytecode-cid>
```

A single item may carry implementation bindings for multiple languages — the trust matrix picks one at runtime based on what runtimes the host supports and what the user is willing to run. The runtime materializes whichever is selected.

The bigger story of how implementation bindings interact with HANDLES, IMPLEMENTS, and dispatch lives in [`api.md`](api.md). Manifests just declare; the runtime makes the choices.

## Editing and committing

Editing an item conceptually means producing a new manifest body whose `@FOLLOWS` binding references the prior version's VID. The new body inherits whatever it wants from the prior version's bindings, adds, removes, or changes others, then gets hashed and signed.

The old manifest body remains exactly as it was. Versions are not deleted; they accumulate. A user's edit history is the chain of their manifests; an item's revertible state is "switch the channel head to point at an earlier VID."

This is the same model as Git's commit graph, with two simplifications. First, there's no working tree distinct from the committed history — every edit is itself a commit. Second, "branches" are just channels: per-principal pointers naming which manifest each principal considers the current head. Two principals can disagree about which version is current without either being wrong; they just have different heads on the same item.

## Worked examples

**An inception manifest** for a fresh chess game.

```
{@chess-game, [
  @ITEM_ID → <new-game-iid>,
  @PLAYER:[WHITE] → @alice,
  @PLAYER:[BLACK] → @bob,
  @TURN → @white
]}
```

No `@FOLLOWS` — this is the first version. Players bound; turn set to white; no moves yet.

**A subsequent commit** after Alice plays e4.

```
{@chess-game, [
  @ITEM_ID → <new-game-iid>,
  @FOLLOWS → #<inception-vid>,
  @PLAYER:[WHITE] → @alice,
  @PLAYER:[BLACK] → @bob,
  @TURN → @black,
  @ENDORSES → #<move-1-frame-id>
]}
```

Same IID — same item, different version. New `@FOLLOWS` pointing at the inception manifest. Turn flipped to black. The MOVE frame is endorsed — pinned to this version's content.

**A code item's manifest.**

```
{@code, [
  @ITEM_ID → <chess-java-iid>,
  @IMPLEMENTS → @chess-game,
  @JAVA:[ClassName] → "dev.everydaythings.graph.game.ChessGame"
]}
```

Identity, the concept it implements, the Java class that realizes it. A sibling manifest could carry `@PYTHON:[SourceCode] → "..."` for a Python implementation of the same archetype.

## Relations

- [`datum.md`](datum.md) — the structural primitive a manifest body is.
- [`ref-scheme.md`](ref-scheme.md) — the five reference prefixes used in bindings.
- [`frames.md`](frames.md) — body-with-records pattern; semantic statements that don't carry lineage.
- [`item.md`](item.md) — the continuant a chain of manifests describes.
- [`api.md`](api.md) — HANDLES, IMPLEMENTS, and how implementations are selected at runtime.
- [`storage.md`](storage.md) — how manifest bodies and records are persisted and indexed.
