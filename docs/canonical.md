# The Canonical Walker

Common Graph fingerprints **data**, not **content**. A datum's identity comes from what it *says* — its head, its bindings, its leaf values, arranged as structure — not from how those things happen to be encoded into bytes. Two implementations using different encoding formats produce identical fingerprints for identical data, because both walk the same structure to the same hash. The encoding is how you transport, store, and exchange the bytes; the canonical walker is how every implementation agrees on what those bytes *mean*.

This separation is what frees Common Graph from being married to any single wire format. The walker is the protocol; encodings are choices.

This document defines the canonical walker, the structural hash tree it produces, and what every implementation must agree on for the property to hold.

This document assumes familiarity with [the datum primitive](datum.md) and [the reference scheme](ref-scheme.md).

## Fingerprinting data vs. fingerprinting content

A normal hash function answers "do these bytes match?" Give it the same bytes, it gives you the same hash. Give it different bytes — even bytes that mean the same thing — it gives you different hashes. Hash functions don't know what data means; they know whether bytes are identical.

The canonical walker answers a different question: "do these datums say the same thing?" It walks the data model — head, bindings, qualifiers, targets — and computes a hash from the *structure*, ignoring whatever byte layout that structure happens to be wrapped in. Two implementations encoding "Tolkien authored The Hobbit" into different formats produce different bytes; their byte-hashes differ; but their structural hashes match, because both walkers visit the same head (`@authored`), the same bindings (AGENT and THEME), the same target IIDs.

The walker is one-way, like any hash: you go from data to hash, never the reverse. But every implementation, every encoding, every transport produces the same hash for the same data. That's what makes the hash a fingerprint of *meaning*, not bytes.

Both fingerprints are useful:
- **DatumID** (structural hash) — identifies what a datum says. Stable across encodings.
- **ContentID** (byte hash) — identifies exact bytes. Specific to one encoding.

You need both. ContentID lets you verify that the bytes you received are the bytes someone sent. DatumID lets you verify that those bytes mean what you expect, regardless of whether the sender used your wire format.

## The structural walk

The walker visits every position in a datum's structure and produces a tree of hashes. The visit order is fixed; the same datum always produces the same tree.

The walk starts at the datum's head and proceeds through its bindings in order. For each binding, the walker visits the role, then each qualifier, then the target. When a target is itself a nested datum, the walker recurses; when it's a leaf value (a number, a string, a reference), the walker hashes that leaf directly.

The output of the walk is a **Node tree** — a hierarchical structure mirroring the datum's shape, with hashes at the leaves and combined hashes at each level. The root of this tree is the DatumID.

A datum's structure becomes hashable not because the walker imposes an order on something inherently unordered, but because the data model itself has a fixed shape. A datum is *not* an unordered bag of fields; it is `head + ordered-list-of-bindings`. A binding is *not* an unordered triple; it is `(role, qualifiers-in-canonical-order, target)`. The walker just visits each position in the shape it already has.

For positions that are multisets (multiple same-key bindings, for example), the walker imposes a canonical ordering by hash. Two implementations producing the same multiset land on the same ordered list before hashing, by sorting on the hashes of the elements themselves. There's no central registry of ordering rules; the data orders itself.

## Leaf-type discrimination

The walker has to hash leaf values — the numbers, strings, references, timestamps, and bytes at the bottom of the structure — in a way that distinguishes their *kinds*. Without that, `5` (the number) and `"5"` (the string) and `b"5"` (the byte `0x35`) would hash identically, because they all hash the same one byte. The system would have no way to tell them apart by fingerprint.

The walker prepends a one-byte discriminator to every leaf before hashing it. The discriminator names the leaf's kind: boolean, integer, string, bytes, instant, item-id, reference (any HashID variant), big-integer. Each gets one byte, fixed forever; the discriminator bytes are part of the protocol that all implementations must agree on.

All HashID variants — concrete item refs (`@`), type patterns (`?`), schema templates (`!`), content hashes (`~`), datum hashes (`#`) — share a single reference-discriminator byte.  The variant is already encoded as the first byte of the reference payload (the prefix), so a separate discriminator per variant would be redundant.  The walker hashes the full reference-bytes (prefix + multihash + sub-parts), and the prefix byte inside those bytes is what distinguishes variants in the hash.

This is what makes the structural hash robust against encoding choices. An integer represented as a CBOR uint, a JSON number, or a flat-binary varint produces the same hash because all three encodings walk to the same leaf-value, the walker prepends the same integer-discriminator byte, and the resulting hash matches.

Discriminator bytes are part of the identity protocol and never change. New leaf types may be added by allocating new discriminator bytes; existing assignments are permanent.

## The hash tree

The Node tree the walker produces is a small algebraic structure with four node types:

- **Array nodes** — ordered sequences of child nodes.  A datum walks to `Array(head-node, Array(binding-nodes...))`.  A binding walks to `Array(key-node, target-node, …)`.  Combining the children's hashes in order produces the node's hash.
- **Map nodes** — keyed collections, used where the data model has named-field structure rather than positional bindings.
- **Leaf nodes** — terminal values.  Hash is the discriminator byte plus the leaf value's bytes.
- **Hashed nodes** — short-circuit nodes carrying a precomputed hash directly.  Used for redaction (a `RedactedTarget` walks to a Hashed node whose value is the preserved structural hash of the original subtree), so the parent's hash matches whether the subtree was present or elided.

The hash function used to combine bytes at each level is fixed by the protocol (currently SHA-256, but the multihash header on every produced ID names the algorithm so this is principled, not hardcoded).

Two properties fall out of this shape:

**Composability.** The DatumID of a frame containing a nested datum is computed from the nested datum's own DatumID. You don't have to re-walk a sub-datum every time it appears as a target; the cached DatumID flows up. This makes incremental updates and verification cheap.

**Redaction-preservation.** A binding's target can be replaced with a `RedactedTarget` carrying the hash that the original target would have contributed. The walker, encountering a redaction, uses the preserved hash directly. The DatumID of the redacted datum equals the DatumID of the original — you've removed the value but preserved the *structural identity*. This lets you publish a datum with portions elided while keeping the identity stable for verification.

## Encoding-agnosticism

The canonical walker is what makes pluggable encodings real. Every encoding format must agree on:

1. The set of semantic primitives (REF, BODY, RECORD, SIG, KEY, RATIONAL, REDACTED, ENCRYPTED — see [`ref-scheme.md`](ref-scheme.md)).
2. The data model (datum, body, record, head, bindings, leaf values).
3. The canonical walker — *this protocol*.

Beyond that, encoding formats are free. CG-CBOR uses CBOR tags to distinguish the primitives; a hypothetical CG-JSON might use discriminator fields on objects; a flat-binary format might use header bytes. The bytes differ, the ContentIDs differ, but the DatumID is the same in every case because every encoder presents the same data model to the same walker.

The walker doesn't see encoded bytes at all. It walks an in-memory representation of the datum — head, bindings, targets — and never knows or cares how those came out of the wire. Encoders produce the bytes; decoders parse the bytes back to the data model; the walker operates on the data model alone.

This is what frees the system from being defined by its wire format. CG-CBOR is the first such format; nothing in the architecture prevents others from existing alongside it, and any datum is interoperable across all of them by virtue of agreeing on structure.

## The protocol contract

For two implementations to compute the same DatumID for the same datum, they must agree on:

- **The shape of the data model** — datum = head + ordered bindings; binding = role + qualifiers + target + optional index; compound key = head sememe + canonically-ordered qualifier multiset.
- **The discriminator bytes** for leaf types — one per leaf kind, fixed permanently.
- **The combiner function** at each tree level — how a node's hash is computed from its children's hashes (concatenation order, hash algorithm).
- **Multiset canonicalization** — how multiple-same-key bindings are sorted before hashing.
- **Reference layout** — how `@`/`?`/`!`/`~`/`#` references contribute their bytes to leaf hashes (the byte layout per [`ref-scheme.md`](ref-scheme.md)).

What implementations *don't* have to agree on:

- Wire encoding (CBOR, JSON, flat-binary, …) — pluggable.
- Storage layout — local to each librarian.
- Index structure — each librarian's choice.
- The Java/Python/Lisp class names or function signatures used to invoke the walker.

The walker is the small, fixed protocol at the center. Everything else is free to vary.

## A worked example

Consider the datum `{@authored, [@AGENT → @tolkien, @THEME → @hobbit]}`.

The walk:

1. **Head** — hash the leaf `@<authored-iid>` (with reference-discriminator byte). Call this `h_head`.
2. **First binding** — `@AGENT → @tolkien`.
   - Role: hash the leaf `@<agent-iid>`. Call this `h_role_1`.
   - No qualifiers.
   - Target: hash the leaf `@<tolkien-iid>`. Call this `h_target_1`.
   - Binding hash: combine `h_role_1` + `h_target_1` → `h_bind_1`.
3. **Second binding** — `@THEME → @hobbit`. Same shape; produce `h_bind_2`.
4. **Bindings multiset** — canonically order the binding hashes (sort by hash), then combine them → `h_bindings`.
5. **Datum hash** — combine `h_head` + `h_bindings` → `DatumID`.

Now imagine the same datum encoded in CG-CBOR vs. some hypothetical CG-JSON. The CBOR bytes:

```
D8-07 ...   (Tag 7 for the head reference)
A0 ...      (some CBOR-specific encoding of bindings)
```

The JSON bytes:

```json
{"head": "@authored", "bindings": [...]}
```

Completely different bytes; different ContentIDs in each format. But both encoders, when their data is fed to the walker, produce identical `h_head`, `h_role_1`, `h_target_1`, `h_bind_1`, `h_bind_2`, `h_bindings`, and DatumID. The same data fingerprint comes out of both encodings.

## What this is and isn't

The canonical walker is:

- **A protocol** — fixed, shared, defining how every implementation hashes data.
- **The basis of structural identity** — DatumID is what it produces.
- **The reason encodings are pluggable** — encodings live above it, not below.

The canonical walker is *not*:

- **An encoding** — it produces a hash, not bytes you can ship.
- **Reversible** — like any hash, you go from data to hash, not the other way.
- **A serialization format** — you can't read a DatumID back into a datum. To get the data, you fetch the bytes by ContentID, decode them, and walk those.
- **Implementation-specific** — the Java implementation, the Python implementation, and any future implementation all walk identically.

It exists between the data model and the encoding, doing one job well: producing a structural fingerprint that doesn't care about bytes.

## Why this matters

The whole point of content-addressed data is that identity is mathematical, not administrative. No central registry decides what an item *is*; the data itself fingerprints itself, and that fingerprint is the identity.

Bytes-level content addressing — what IPFS, Git, and most existing systems use — pins identity to a specific encoding. Change the encoding, change the identity. This works fine when there's one encoding, but it makes the system *defined* by its wire format. If you ever want to support a second encoding, you've either broken identity continuity or you've layered a translation table over the system.

Common Graph picks a different layer for identity. The structural walk decouples *what the data is* from *how the bytes happen to be laid out*. The encoding is below identity, not the other way around. A new encoding doesn't break anything because it doesn't touch the layer where identity lives.

This is what lets the system be encoding-agnostic for real, rather than aspirationally. The walker is what makes it true.

## Relations

- [`datum.md`](datum.md) — the data model the walker walks.
- [`ref-scheme.md`](ref-scheme.md) — the reference byte layout used at leaf positions.
- [`cg-cbor.md`](cg-cbor.md) — the first encoding format above this layer.
- [`content.md`](content.md) — content-addressed bytes; how ContentIDs are used.
- [`storage.md`](storage.md) — how DatumIDs and ContentIDs index objects in the librarian.
