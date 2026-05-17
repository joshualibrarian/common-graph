# Storage

Every Common Graph librarian persists its data locally. The **Library** is the storage engine: one content-addressed object store, several derived indexes, an item-location directory, a token dictionary. Everything the librarian holds — manifests, frames, records, content blobs, keys, vocabulary — lives here.

The architecture rests on the encoding-agnostic identity story. Datums are addressed by structural hash (DatumID); bytes are addressed by encoded-bytes hash (ContentID); indexes derive from the datums and can be rebuilt at any time. There's no schema migration: the schema lives on the archetype manifests in the graph itself, not in the storage layer.

This document defines the object store, the index layers, the directory and dictionary, and the principle that keeps storage simple: one source of truth, many derived views.

This document assumes familiarity with [the datum primitive](datum.md), [the canonical walker](canonical.md), [content addressing](content.md), and [items](item.md).

## One source of truth

All bytes that need to survive a process restart live in one place: the **object store**. Every datum, every content blob, every signature, every key — addressed by hash, stored as bytes, fetched by hash. The store is content-agnostic; it doesn't interpret what it holds.

Everything else the librarian needs is *derived*:

- **Indexes** — `IID → manifest CIDs`, `predicate → frame CIDs`, `binding-target → frame CIDs`, etc. Built by walking the object store.
- **Item directory** — `IID → which store holds it`. For multi-backend setups.
- **Token dictionary** — `surface form → sememe references`. Built by walking lexeme frames endorsed by Language items.

Indexes can be rebuilt from the object store. Lose an index, rebuild it. Want a new index, walk the store and produce it. The bytes are the truth; the indexes are accelerators.

This is what keeps the storage layer small. The librarian doesn't manage schemas, doesn't migrate data, doesn't worry about consistency between layers. There's one layer that matters; everything else falls out.

## The object store

The object store has three operations:

- **Put** — given bytes, return the ContentID. Idempotent: putting the same bytes twice produces the same ContentID and stores them once.
- **Get** — given a ContentID, fetch the bytes. Verify the hash on read; reject mismatches.
- **Has** — given a ContentID, check existence without fetching.

The store doesn't interpret. CG-CBOR datums, JPEG images, Python source files, compiled bytecode, audio samples — all sit side by side, identified by their hashes, none privileged over another. The store is the bottom of the architecture; it knows about bytes and hashes, nothing more.

For datums specifically, the librarian stores by DatumID (in addition to ContentID) — the structural hash that's encoding-independent. Two datums with the same DatumID share storage by virtue of being structurally identical, even if their encoded bytes drifted across encoding changes.

The store has a small set of backends:

- **In-memory** — fast, ephemeral. For tests, transient librarians, anonymous sessions.
- **File-backed** — disk-resident, durable. Small blocks live in a key-value database; large blocks (multi-megabyte images, audio, video chunks) live as individual files in a content-addressed directory.
- **Composite** — multiple backends behind a single interface. Reads check each in order; writes route by size or by some other policy.

The interface is the same across backends. The librarian doesn't care which is in use; calling code doesn't either.

## Derived indexes

Indexes turn "find me everything matching X" into a directly-readable lookup. The librarian maintains four primary indexes, each derived from the object store:

**IID → manifests.** For every item, the list of manifest CIDs that constitute its lineage. Given an IID, the librarian can immediately locate the inception manifest, the current head, and every version in between.

**Predicate → frames.** For every predicate sememe, the list of frame CIDs whose head is that predicate. Useful for finding "all AUTHORED frames" or "all MOVE frames in this game."

**Binding-target → frames.** For every reference target appearing as a binding's target value, the list of frame CIDs that point at it. Useful for reverse-binding queries: "what frames mention @tolkien?"

**Archetype-hierarchy → items.** For every archetype, the list of item IIDs whose archetype chain transitively includes it. Useful for type-pattern queries: "all books," "all chess pieces."

Beyond these, secondary indexes are added as query patterns warrant — by binding role and target combined, by signer, by time, by qualifier set. New indexes are cheap: they're derivable, replaceable, and don't constrain anything else.

## Item directory

In a multi-backend setup (e.g., the librarian has its own local store plus mounted stores from other librarians or applications), the **item directory** answers "given an IID, which store holds it?" The directory is itself a small index, keyed by IID.

For single-backend librarians, the directory degenerates — every IID is in the one store. The interface stays the same regardless.

## Token dictionary

The **token dictionary** is the surface-form-to-sememe lookup that drives the input pipeline. Given a token (a string) and a scope chain (a list of item IIDs), the dictionary returns matching postings — sememe references with their scope and weight.

The dictionary is derived from the lexeme frames endorsed by Language items, plus sememes' own universal symbols, plus user aliases, plus item-specific named entities. Every endorsement that names a sememe in a language contributes one or more postings; the dictionary indexes them by `(scope, token)`.

Like every other index, the dictionary can be rebuilt by walking the object store. New lexeme frames endorsed by a Language item flow into the dictionary as soon as the endorsement is observed.

See [`vocabulary.md`](vocabulary.md) for how the dictionary is queried; this doc covers how it's populated.

## Datum hashing and dedup

The librarian addresses datums by DatumID (structural hash) rather than ContentID (byte hash) when the question is "do we have this datum?" rather than "do we have these bytes?" The distinction matters when encodings change or when data is shipped across implementations using different wire formats: the DatumID stays stable while the ContentID may shift.

Two librarians using different encoding formats arrive at the same DatumIDs for the same datums and can deduplicate, share, and verify each other's data through structural identity. Each maintains its own ContentID-indexed byte storage; they don't need to share encoding format to interoperate.

This is what the canonical-walker / encoding split (see [`canonical.md`](canonical.md)) enables in storage terms: stable identity above the encoding layer, swappable byte representations below.

## Persistence semantics

The librarian's persistence is **content-only**, **append-mostly**, **strong-consistency**.

**Content-only.** Bytes get stored by hash. There's no overwriting; there's no in-place editing. A new version of an item is a new manifest with its own CID; the old manifest stays put. Mutating a frame is not a meaningful operation — to "change" a frame, you create a new frame and update whichever pointers reference it.

**Append-mostly.** Most operations add. Deletes (DELETE frames, subject to the trust matrix) remove specific bytes; channel-head advancement updates a pointer; index rebuild rewrites derived data. The store as a whole grows; the operations that shrink it are explicit and policy-gated.

**Strong consistency.** A datum, once stored, is durable. A read after a successful put always returns the stored bytes. The librarian's own state never lies about what's persisted.

Within a process, all writes are serialized through the librarian — there's no concurrent-write contention to manage. Cross-librarian consistency is a network concern (see [`network.md`](network.md)) and goes through Parley.

## Rebuildability

Every index can be rebuilt from the object store. This isn't a maintenance procedure run rarely; it's an architectural commitment that makes the indexes free to swap, free to redesign, free to add or remove.

Want a new query pattern that needs a new index? Walk the store, build the index. Want to migrate from one index implementation to another? Walk the store, populate the new one, swap. Corrupted index? Drop it, rebuild.

The librarian's startup typically verifies indexes against the store. A mismatch triggers a rebuild for the affected indexes. The cost is one pass over storage; the benefit is that the librarian never gets stuck with a broken index it can't recover from.

## Garbage collection

Most stored content stays. Deletes are explicit (via DELETE frames signed by an authorized signer); channel-head changes leave prior versions in place. The natural mode is accumulation.

Periodically, the librarian may garbage-collect:

- **Orphan content** — bytes no manifest references, no index points at, no other librarian fetches.
- **Pruned versions** — old manifest versions beyond a retention window the user has configured.
- **Ephemeral frames** — frames published with `CONFIG:[RETENTION] → @ephemeral` are not kept after dispatch.

Garbage collection is policy-driven. The default is "keep everything you have"; tighter policies require explicit configuration.

## Cross-librarian reads

A librarian fetching a datum it doesn't have locally can request it from peer librarians through Parley. The request includes the DatumID; any peer with the datum can respond. The receiving librarian verifies the structural hash on receipt; mismatches are rejected.

The librarian's local store may cache fetched datums (subject to retention policy) or fetch on demand each time. The caching policy is per-librarian; the protocol works either way.

Content fetched from a peer enters local storage indistinguishably from content put locally. The hash verifies; the indexes update; the datum is now available the same as locally-minted content.

## Versioning and channel heads

For items with multiple versions, the librarian tracks which version is the *current head* per signer. The channel-head index maps `(item, signer) → current VID`. A signer can advance their own head (a new commit to an item they author); they can't advance someone else's.

Different signers can disagree about the current head of a shared item. Each librarian sees its own view; when asked, it can present alternative heads from other signers (subject to trust policy). There's no global "the head" of a shared item — there's each signer's view of where they think the head should be.

This is what enables forking without conflict: two signers' disagreement is just two different heads; both are valid; users see whichever they trust (or both, if they want to compare).

## Worked examples

**Storing a new frame.** The user types "Tolkien authored The Hobbit"; the input pipeline produces a frame body; the body is hashed, signed, persisted. The librarian:

1. Computes the body's DatumID and ContentID.
2. Stores the body's bytes by ContentID (or no-ops if already stored).
3. Records the DatumID → ContentID mapping.
4. Updates the predicate index (Authored gets a new entry).
5. Updates the binding-target index (THEME → @hobbit and AGENT → @tolkien each get a new entry).
6. Creates the record (signature attesting the body); stores it.
7. The frame is now visible to queries.

**Fetching by reference.** A frame's binding carries `@THEME → #<hobbit-frame-cid>`. The runtime needs to materialize the referenced frame:

1. Looks up the DatumID in the object store; gets the body's bytes.
2. Looks up records for this body; assembles the Frame runtime aggregate.
3. Returns the frame to the caller.

**Rebuilding an index.** The librarian, on startup, detects that the binding-target index is corrupted. It:

1. Marks the index as unavailable; queries needing it block or fall back.
2. Walks the object store, examining every datum.
3. For each frame, for each reference-target binding, updates the index.
4. When done, marks the index available.
5. Operations resume.

The whole process took one pass over storage. No data was lost; nothing in the graph layer was affected. The librarian just rebuilt one of its derived views.

## Why one source of truth

The temptation in storage systems is to denormalize: keep multiple representations, optimize each for its own access pattern, manage consistency across them. Done well, it speeds queries; done poorly, it produces inconsistent state and migration nightmares.

Common Graph picks the opposite tradeoff. One representation (bytes addressed by hash); many derived views (indexes that can be rebuilt cheaply). The cost is each query walks an index that has to be maintained or rebuilt; the benefit is the storage layer never gets out of sync with itself.

The architecture is also future-proof. New query patterns mean new indexes (built from the existing bytes). New encoding formats mean new ContentID schemes (the DatumIDs survive). New language runtimes mean new ways to fetch and evaluate code items (the object store doesn't care). The storage layer's contract is small enough to stay stable while the layers above it evolve.

## Relations

- [`datum.md`](datum.md) — datums and their structural identity.
- [`canonical.md`](canonical.md) — the structural walker that produces DatumIDs.
- [`content.md`](content.md) — content addressing and the ContentID.
- [`item.md`](item.md) — items and their manifest lineages.
- [`manifest.md`](manifest.md) — what gets stored per version.
- [`frames.md`](frames.md) — frames as stored bodies plus records.
- [`vocabulary.md`](vocabulary.md) — the token dictionary populated from lexemes.
- [`streams.md`](streams.md) — append-only streams as a related but distinct storage shape.
- [`working-tree.md`](working-tree.md) — items materialized as filesystem trees.
- [`network.md`](network.md) — cross-librarian fetches through Parley.
- [`trust.md`](trust.md) — gatekeeping at fetch time.
