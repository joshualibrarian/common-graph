# Items

An item is a stable cryptographic identity around which a lineage of manifest bodies accumulates. Documents, users, hosts, conversations, games, codebases, photographs, sensors, contracts, applications — anything that needs to persist across versions, to be referenced from outside, to accumulate history, is an item. The work an item does that a single body cannot is *endure*: be the thing that survives every edit to itself.

Items are *continuants* — what persists through change. A particular manifest body is a snapshot at one moment; the item is what the sequence of those snapshots is *of*. The same way a person is what persists through their sequence of physical states, an item is what persists through its sequence of versions.

This document defines items, their identity, the lineage they carry, and how they relate to the bodies, frames, and references in the rest of the system.

This document assumes familiarity with [the datum primitive](datum.md), [the reference scheme](ref-scheme.md), [frames](frames.md), and [manifests](manifest.md).

## Identity

An item has exactly one identifier: its **IID** (item identity). The IID is 32 bytes — a multihash. It is stable across every version of the item, never changes, and uniquely identifies *this item* across all of Common Graph.

Most IIDs are random — generated when the item is first created, with no coordination required. Two items minted independently get different IIDs by birthright; no central registry adjudicates.

Some IIDs are deterministic — computed by hashing a canonical key string. The Add predicate's IID is `hash("cg.predicate:add")`. The English Language item's IID is `hash("cg.language:eng")`. Two nodes starting independently arrive at the same IID for "the concept of addition" without ever talking to each other. This is how the bootstrap vocabulary works: agreement by construction, not by handshake.

Items are referenced by IID through the `@` prefix: `@<iid>`. The dereferencing path — "given this IID, materialize the item" — runs through the librarian, which fetches the current manifest, reads its bindings, and produces the runtime form of the item.

## The lineage

An item is the lineage of its manifest bodies — a sequence (or DAG) of versioned snapshots linked by parent references. The lineage *is* the item, in the same sense that a person's life is the unfolding of their states. An item with no manifests is identity without content; an item with one manifest is a freshly-minted snapshot; an item with many manifests is a history.

Every manifest body in the lineage carries the same `@ITEM_ID → <iid>` binding. The IID is the thread; the manifests are the beads. The `@FOLLOWS` bindings on each manifest declare which prior manifest(s) it follows from. The structure is the version history.

- **Inception** — a manifest with no `@FOLLOWS` binding. The first version. Items have exactly one inception manifest, ever.
- **Sequential** — a manifest with one `@FOLLOWS` target. The common case.
- **Merge** — a manifest with multiple `@FOLLOWS` targets, unifying branches. Permitted but rare for ordinary items.
- **Branch** — multiple manifests sharing the same `@FOLLOWS` target. Different futures from the same past, both valid simultaneously.

The lineage as a whole is a DAG, not a chain. Every node has zero or more parents, no node is its own ancestor, and many nodes can share parents (branches) or merge them (merges). What constitutes "the current version" of an item is a separate question — answered by *channels*, per-principal pointers that name which node in the DAG that principal considers the head.

## Items aren't bodies

Items are an organizing concept, not a stored structure. The item itself is *the IID*, and the IID is referenced from the manifest bodies that constitute it. There is no separate "item object" anywhere — no file, no database row, no header containing the item's metadata. The item is wholly described by the manifests bearing its IID.

This is the same reason a person doesn't have a separate "person object" distinct from their physical states — the person *is* the persisting subject of those states, and looking for a separate person-thing alongside the states is a category error. The IID is the persisting subject; the manifests are the states.

In storage, items are organized by index: given an IID, the librarian can find all manifests carrying `@ITEM_ID → <iid>`. The lineage is computed from those manifests' `@FOLLOWS` bindings. The current head, per principal, is recorded in a separate channel index. No item-shaped structure is ever stored; the item is the accumulation of its parts.

## Items as actors

When a frame addresses an item — through any binding with an `@`-prefixed reference to the item's IID — the item is potentially reactive. A frame referencing an item by `@AGENT` says "this item did this"; a frame referencing it by `@THEME` says "this item is the subject of this"; a frame with the item's IID in *any* role-binding is, in some sense, addressed to the item.

The runtime materializes the item when needed: fetches its current manifest, loads its implementation (if it has one), and lets the item observe the frame. Items declare which frames they actively process through HANDLES bindings on their manifests — the predicates whose head matches a HANDLES declaration get dispatched into the item's behavior. The rest are observed as data: the item knows it was mentioned, but takes no action.

The frames-as-messages model — items receive frames, react to those they handle, optionally reply with new frames — is how every interaction in Common Graph is structured. There is no separate event system, no RPC, no command bus. Frames in, frames out, items as the addressable receivers.

The full mechanics of HANDLES, IMPLEMENTS, and dispatch live in [`api.md`](api.md).

## The predicate-or-archetype question

An item's *role* in the system depends on the shape of its manifest, not on its structural type. Two important roles, both common:

**Items whose instances are themselves items** are **archetypes**. A ChessGame archetype's manifest carries schema-prefixed bindings declaring what a chess-game instance should look like — players, turn marker, the HANDLES set. Instances of the archetype are real items with their own IIDs, their own manifests, their own lineages. The archetype is a template; instances inherit its expectations.

**Items whose instances are frames** are **predicates**. The Add predicate's manifest carries schema-prefixed bindings declaring what an Add frame should look like — the two THEME operands. Instances of the predicate are *not* items; they're frames headed by the predicate, with no IIDs of their own.

The distinguishing detail is one binding on the schema.  If the archetype's schema declares an `!ITEM_ID` slot, its instances are items; if it doesn't, its instances are frames.  One hierarchy, two usage patterns, distinguished by what their instances need.

This is a *usage* distinction, not a *structural* one. The same item-shape can play both roles: some items are archetypes for some things and predicates for others. Some are clearly one or the other (ChessGame as archetype, Add as predicate). The world is fuzzy, and so is the ontology.

For other roles items can play — schema, query, code, value-type — see [`types.md`](types.md).

## Lifecycle

An item exists through a small set of transitions, all expressed as data operations.

**Creation.** A new IID is minted (random or deterministic). An initial manifest body is built — head set to the appropriate archetype, `@ITEM_ID` set to the new IID, whatever other bindings the archetype's schema calls for. The body is hashed, signed by some signer, persisted. The item exists.

**Loading.** Given an IID, the librarian finds the manifest(s) carrying that IID, picks the current head per the channel, returns the runtime form. The item is now materialized — ready to be referenced, dispatched to, queried.

**Editing.** A new manifest body is built — same `@ITEM_ID`, a `@FOLLOWS` binding pointing at the prior version's VID, and whatever bindings the new version carries. The body is hashed, signed, persisted. The channel advances. The old manifest remains exactly as it was.

**Branching.** Multiple new manifest bodies can be built from the same prior version. Each is a distinct version; each can be the head of its own channel. The lineage forks. No coordination needed.

**Merging.** A new manifest body's `@FOLLOWS` binding references multiple prior versions. The merge reconciles content; the lineage rejoins. Merges are rare for most items and common for shared documents.

None of these transitions mutate existing data. Every state change is *new data added* — a new manifest, a new record, a new channel head pointer. The history is fully preserved.

## Composition

Items compose behavior from frames, not from special types. There is no "chat room" type, no "shared folder" type, no "kanban board" type — these are all just items whose archetypes declare the relevant schemas and HANDLES.

A chat room is an item whose archetype's schema declares it carries member bindings, whose HANDLES includes MESSAGE and JOIN and LEAVE. A shared folder is an item whose schema declares CHILD bindings, whose HANDLES includes ADD-CHILD and REMOVE-CHILD. A kanban board is an item whose schema declares COLUMN and CARD bindings, whose HANDLES includes the verbs for moving cards.

Items aren't *built* from these archetypes the way OOP objects are built from classes — they're *instances* in the sense that they conform to the schema and respond to the handlers. Two items with the same archetype share their type's contract; their actual content is whatever frames have accumulated in their lineage.

## Items in references

When a binding's target is `@<iid>`, the binding *refers to the item itself* — its identity, its lineage, its current version. The dereference path is: look up the IID, find its current manifest, materialize. References don't pin to a particular version; they pin to the *item*, and the item presents its current self.

For cases where pinning to a specific version matters — citing a quote from a specific revision of a document, referring to a chess game at a specific move — a version-pinned reference adds the VID: `@<iid>\<vid>`. The IID identifies the item; the VID identifies which version.

Manifests are referenced by their VIDs through the `#` prefix: `#<vid>`. The VID is a datum hash, not an item identity. References to specific manifest bodies (in `@FOLLOWS` bindings, in `@ENDORSES` bindings, etc.) use `#`. References to *items as continuants* use `@`. Two different question shapes, two different prefixes.

## Worked example

**The Alice/Bob chess game.** Items in play: Alice, Bob, the ChessGame archetype, the game itself, a sequence of move frames.

```
@alice's manifest:           @bob's manifest:
  head: @signer                head: @signer
  bindings:                    bindings:
    @ITEM_ID → <alice-iid>      @ITEM_ID → <bob-iid>
    @SIGNING_PUBLIC_KEY → ...   @SIGNING_PUBLIC_KEY → ...
    @NAME → "Alice"             @NAME → "Bob"

@chess-game archetype's manifest:
  head: @archetype
  bindings:
    @ITEM_ID → <chess-archetype-iid>
    !PLAYER:[WHITE] → ?user
    !PLAYER:[BLACK] → ?user
    !TURN → ?color
    @HANDLES → @move
    @HANDLES → @resign

The game's inception manifest:
  head: @chess-game
  bindings:
    @ITEM_ID → <game-iid>
    @PLAYER:[WHITE] → @alice
    @PLAYER:[BLACK] → @bob
    @TURN → @white

Alice's first move (a frame, not part of any item's manifest):
  {@move, [
    @AGENT → @alice,
    @THEME → @king-pawn,
    @SOURCE → @e2,
    @GOAL → @e4,
    @LOCATION → @<game-iid>
  ]}

The game's second manifest, after Alice's move:
  head: @chess-game
  bindings:
    @ITEM_ID → <game-iid>
    @FOLLOWS → #<inception-vid>
    @PLAYER:[WHITE] → @alice
    @PLAYER:[BLACK] → @bob
    @TURN → @black
    @ENDORSES → #<move-1-frame-id>
```

Five items in this trace — Alice, Bob, the archetype, the game, the move's pawn — and one frame floating between them. The game is the continuant; its manifests are the snapshots; the move frame is the meaning-glue that triggered a new snapshot. The lineage links inception to current head via `@FOLLOWS`.

## Relations

- [`datum.md`](datum.md) — the structural primitive items' manifests are made from.
- [`ref-scheme.md`](ref-scheme.md) — how items are referenced (`@`, `?`, `!`) and versions (`#`).
- [`frames.md`](frames.md) — the messages items receive and produce.
- [`manifest.md`](manifest.md) — the body shape of a single version in an item's lineage.
- [`api.md`](api.md) — HANDLES, IMPLEMENTS, and dispatch.
- [`types.md`](types.md) — the meta-archetype tree and the roles items can play.
- [`storage.md`](storage.md) — how the librarian indexes manifests for fast IID lookup.
- [`authentication.md`](authentication.md) — how items that are signers manage their keys.
