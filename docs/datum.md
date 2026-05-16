# The Datum

Common Graph is built from one structural primitive. Every body, every record, every frame, every manifest, every schema, every query is a configuration of the same shape: a head followed by bindings, optionally attested by a signature. The primitive is the **datum**, and the rest of the system is what you can build by composing datums with care.

The single-primitive choice is what keeps Common Graph small. One encoding, one indexing strategy, one wire format, one dispatch path. Adding capability to the system means adding vocabulary — new sememes, new predicates, new archetypes — never adding new kinds of structure.

This document defines the datum, its two flavors (body and record), how frames and manifests are built from them, and how the primitive's identity is computed.

This document assumes familiarity with [the reference scheme](ref-scheme.md) — the five prefixes a binding's target can carry.

## Anatomy

A datum has three positions:

```
{<head>, [<binding>, <binding>, …], <signature>?}
```

- **Head** — an item reference naming what the datum is *of*. Always an `@`-prefixed reference (a concrete item). For a frame, the head is a predicate; for a manifest, an archetype; for a schema-frame, the predicate or archetype the schema describes.
- **Bindings** — an ordered list of role-keyed values. Each binding has a role (a sememe naming a semantic function), zero or more qualifiers (sememes narrowing the role), and a target. Bindings carry what the datum *says*.
- **Signature** — an optional cryptographic attestation. Present on records, absent on bodies. The signer's key, time of signing, and the signature bytes themselves.

The head names the kind; the bindings carry the content; the signature attests. Three positions, one shape, two flavors.

## Body and record

A datum without a signature is a **body**:

```
{<head>, [<binding>, …]}
```

A body is pure data. It carries no signer, no time, no provenance. Its identity is purely structural: two bodies with the same head and the same bindings *are the same body*. They share a hash. They deduplicate. They are interchangeable.

A datum with a signature is a **record**:

```
{<head>, [<binding>, …], <signature>}
```

A record attests something. The head is the reference to the body being attested. The bindings carry attestation metadata (the signer, the time, any conditions or qualifications on the attestation). The signature is the cryptographic proof.

The split matters. If bodies carried signatures, two signers attesting the same content would produce different bodies (different hashes) — destroying content-addressed deduplication. Records solve this: one body, many records pointing at it, each independently signed. The body's identity is stable; the attestations accumulate.

## Frame

A **frame** is the runtime aggregate of a body and the records that attest it:

```
Frame = Body + [Record, Record, …]
```

A frame is not a serialized structure. It has no hash of its own. The body lives in the object store under its own hash; each record lives in the object store under its own hash; the frame is what you get when the runtime fetches them together.

The records may be zero (an unsigned assertion), one (a single signer), or many (multiple parties attesting the same body). The body and its records are independently addressable, independently fetchable, independently propagated. The frame is just a convenient runtime grouping.

This asymmetry is intentional. Bodies and records are stored things; frames are how you work with them.

## Manifest

A **manifest** is a body whose head is an archetype and which carries an `ITEM_ID` binding. That's the entire definition. There is no wrapper, no envelope, no extra structure — a manifest is just a body shape with particular bindings.

```
{<archetype>, [
  @ITEM_ID → <iid>,
  @FOLLOWS → <parent-vid>?,
  <other bindings…>
]}
```

The item's identity lives as a binding (`@ITEM_ID → <iid>` — a literal, not a reference; the manifest declares its identity, doesn't point at it). Version history lives as bindings (`@FOLLOWS → #<parent-vid>`, datum-hash references to prior versions). Endorsed frames live as bindings (`@ENDORSES → #<frame-cid>`). Implementation declarations live as bindings (`@JAVA:[ClassName] → "..."`). Everything a manifest "carries" is a binding on the same body shape every other body uses.

A manifest, like any body, can be attested by records. The records are the signatures that authorize a particular version of an item.

This unification — manifest is just a body — is what lets the same encoding, the same indexing, and the same dispatch path serve identity-bearing items and content-carrying frames without distinction.

## Identity and addressing

Every datum has two hashes:

**DatumID** — the structural merkle hash. Computed by walking the datum's tree shape: hash each binding's role, qualifiers, and target, combine those with the head, produce a single value. The DatumID is invariant under transformations that preserve structure but change encoding bytes — most importantly under redaction, where a binding's target is replaced with the hash that target would have contributed.

**ContentID** — the canonical bytes hash. Computed over the datum's CBOR-encoded form. Identifies the exact wire bytes.

For most datums, the two coincide in usefulness — fetch by either yields the same object. They diverge when the wire form has been transformed (redacted, encoding migrated, etc.) but the structural identity is preserved. The DatumID is what you reference when you want to point at the *meaning* of a datum; the ContentID is what you reference when you want the *bytes*.

References use the DatumID when the prefix is `#` (structural datum reference). They use the ContentID when the prefix is `~` (raw content reference).

## Bindings, in detail

Every binding has the same shape: a role, zero or more qualifiers, and a target.

```
<role>:[<qualifiers>] → <target>
```

The **role** is always an `@`-prefixed item reference — a sememe naming the semantic function this binding serves (NAME, THEME, AGENT, GOAL, SOURCE, ENDORSES, HANDLES, IMPLEMENTS, …). Roles come from the linguistic backbone — thematic roles (Fillmore-style) for participant slots in meaning-relationships, structural sememes for graph-internal roles like ENDORSES and FOLLOWS.

The **qualifiers** are zero or more sememes that narrow or distinguish the role. A `NAME:[ENGLISH, LEMMA]` binding declares the target is an English lemma; a `NAME:[FRENCH, INFLECTED]` binding is a different binding distinguished by qualifier even though the role is the same. Qualifiers are a compositional refinement on top of roles.

The **target** is the value the binding carries. It may be:

- A literal — a number, a string, a boolean, a duration, an instant.
- A reference — any of the five prefixed forms (`@`, `?`, `!`, `~`, `#`).
- A nested datum — a body inline, used for embedded structures.

The prefix on the target tells the reader how to interpret it. A `?`-prefixed target makes the binding a pattern; a `!`-prefixed target makes it a schema slot. Same binding structure, different intent.

## Why one primitive

Many systems sketch a similar idea but split it across multiple structural types. RDF distinguishes subjects, predicates, and objects but treats blank nodes and literals as separate categories. JSON-LD layers contexts on top of nested objects. IPLD has a link primitive but separates schemas into a parallel layer. Each split was load-bearing for its system; each costs the system simplicity.

Common Graph collapses these splits. The same datum shape, the same encoding, the same indexing, the same content addressing serves every role:

- **Assertion** — a body with a predicate head.
- **Item snapshot** — a body with an archetype head and an ITEM_ID binding.
- **Attestation** — a record pointing at a body.
- **Schema** — a body whose bindings are `!`-prefixed.
- **Query** — a body whose head or bindings are `?`-prefixed.
- **Code** — a body whose head is the Code archetype, carrying language-specific bindings.

Every one of these is a datum. The system never has to ask "what kind of structure is this?" — there is one kind. It asks "what does this datum say?" by inspecting its head and bindings.

## Worked examples

**A simple frame body.** Tolkien authored The Hobbit.

```
{@authored, [
  @AGENT → @tolkien,
  @THEME → @hobbit
]}
```

Head is the AUTHORED predicate; two concrete-reference bindings.

**A signed attestation of that frame.**

```
{<frame-body-id>, [
  @AGENT → @signer-iid,
  @TIME → 2026-04-30T14:23:00Z
], <signature>}
```

The record's head is the body's DatumID. The signer and time are bindings; the signature attests.

**A value body — a specific color.**

```
{@color, [
  @R → 255,
  @G → 0,
  @B → 0
]}
```

Head names Color; concrete numeric bindings.

**A schema body — Color's expected shape.**

```
{@color, [
  !R,
  !G,
  !B
]}
```

Same head, schema-prefixed bindings. Identifies what a Color instance should carry.

**A manifest body — a specific chess game.**

```
{@chess-game, [
  @ITEM_ID → <random-iid>,
  @FOLLOWS → #<prior-vid>,
  @PLAYER:[WHITE] → @alice,
  @PLAYER:[BLACK] → @bob,
  @TURN → @white,
  @ENDORSES → #<move-1-frame-id>,
  @ENDORSES → #<move-2-frame-id>
]}
```

The same body shape as the frame examples, with bindings declaring identity (a literal IID), parent version (a datum reference), player slots (item references), the turn marker, and the move frames this version endorses (datum references). The manifest is just a body with particular bindings; nothing structural separates it from any other body.

## Relations

- [`ref-scheme.md`](ref-scheme.md) — the five prefixes a binding's target can carry.
- [`frames.md`](frames.md) — frame mechanics, lifecycle, dispatch.
- [`manifest.md`](manifest.md) — manifest bindings, version history, signing.
- [`item.md`](item.md) — items as identities accumulating manifests over time.
- [`cg-cbor.md`](cg-cbor.md) — the canonical encoding of datums on the wire.
