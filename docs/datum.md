# The Datum Primitive

This document describes the **Datum** — the unified structural primitive of Common Graph.  Bodies and records, both for frames and manifests, are all configurations of this single primitive.

This design supersedes the prior architecture of separate `FrameBody`, `FrameRecord`, `Manifest`, and similar structures.  The unification eliminates duplicate code paths and clarifies the model: there is one primitive, configured for different conventional uses.

## The primitive

A **Datum** is:

```
Datum = head-reference + bindings [+ signature]
```

- A **head reference** identifies what kind of thing this Datum is.  Always a Tag 6 reference.  For frames the head is the predicate; for manifests the head is the archetype.  Both express the implicit IS_A relation.
- **Bindings** carry the Datum's content.  Each binding has a key (CompoundKey: head sememe + qualifiers) and a target.
- An optional **signature** appears only on records — cryptographic attestation, structurally distinct from bindings.

Two configurations:

- **Body** — head reference + bindings
- **Record** — head reference + bindings + signature

The signature is a structural slot on records, not a binding — it is foundational to what the Record IS (a cryptographic attestation), not data the Record carries.

## How Datums are grouped: Frames and Manifests

Datums don't exist alone — they're grouped with their attestations.  But Frame and Manifest play different roles:

**A Frame is a runtime aggregate.**  It's not a serialized structure; it doesn't have its own CID.  The body Datum is stored independently in the object store (by its CID), each record Datum is stored independently (by its CID), and "the Frame" is just the in-memory aggregate of fetching them together.

```
Frame (runtime only) = body Datum + zero-or-more record Datums
                       (each stored independently in the object store)
```

**A Manifest is a serialized wrapper.**  It IS a stored structure with its own CID — the **VID** (Version ID).  The IID lives at the wrapper level as a structural element.  The wrapper packages the body Datum and any records into a single content-addressed object.

```
Manifest (serialized, content-addressed):
    iid:      IID                # the item's identity (structural)
    body:     Body Datum         # encoded inline
    records:  [Record Datum]     # encoded inline (or referenced by CID)

Manifest CID = VID
```

The IID lives on the **Manifest wrapper**, not inside the Datum.  A Datum is just `head reference + bindings [+ signature]`, period — it doesn't know which item it belongs to.  The manifest wrapper provides that anchor.

This asymmetry is honest: frames are a runtime convenience for grouping independent assertions; manifests are stored versioned things with their own structural identity.

## Bodies and records

A **body Datum** asserts something:

- Its head reference points at a *meaning* — a predicate sememe (for frame bodies) or an archetype sememe (for manifest bodies).  The reference uses the `@` prefix.
- Its bindings carry the assertion content (AGENT, THEME, VALUE, etc.).
- Its content hash (the **body CID**) is computed from the entire encoded form.  Bodies are content-addressed and immutable.
- Bodies do not carry signatures.  Signatures live on records.

A **record Datum** attests something:

- Its head reference points at *content* — specifically, the CID of a body it attests.  The reference uses the `#` prefix.
- Its bindings carry attestation metadata (AGENT for the signer, TIME for when, optional CONFIG, etc.).  These are normal semantic bindings using the same vocabulary as bodies.
- Its **signature** is a cryptographic primitive, structurally distinct from bindings.
- Its content hash (the **record CID**) is computed from the entire encoded form *including* the signature.

A frame is one body Datum plus zero or more record Datums attesting it.  A manifest is the same: one body Datum plus zero or more record Datums.  The conventional names "frame" and "manifest" describe how the body's head reference is used — a predicate (frame) or an archetype (manifest) — not different structural types.

## Why bodies don't carry signatures

Bodies are content-addressed for stable identity.  If signatures lived on bodies, adding a signature would change the body's CID, and two signers signing identical assertion content would produce different bodies (different CIDs).  This breaks deduplication and the "stable assertion identity" property that content-addressing provides.

Records solve this.  The body's CID is computed once, regardless of how many signers attest it.  Each signer creates an independent record Datum whose head reference is the body's CID.  Multiple signatures on the same assertion are multiple records sharing one body.

## References

A reference is a value identifying a target — an item, a piece of content, or a frame (with optional path into it).  References are encoded uniformly using **CBOR Tag 6**.  Both the head reference of a Datum and the target of any binding (when the binding's value is a reference) use Tag 6.

The content of Tag 6 begins with a **structural prefix byte** indicating which kind of reference it is:

| Prefix | ASCII | Meaning |
|---|---|---|
| `@` | 0x40 | Item reference (an IID, optionally version-pinned) |
| `~` | 0x7E | Raw content reference (an opaque content CID) |
| `#` | 0x23 | Frame reference (a frame body CID, optionally with binding-key and portion-spec) |

A single **separator byte** is used internally:

| Separator | ASCII | Meaning |
|---|---|---|
| `\` | 0x5C | Separates sub-parts of a reference (IID from VID, or frame CID from binding-key, etc.) |

### Reference forms

```
@<IID>                                              item
@<IID>\<VID>                                        item with version pin
~<CID>                                              raw content (opaque bytes)
#<CID>                                              whole frame
#<CID>\<binding-key>                                specific binding within frame
#<CID>\<binding-key>\<portion-spec>                 portion of binding's content
```

All multihashes (IID, VID, CID) are self-describing for both algorithm and length: a varint algorithm code, a varint length, and that many digest bytes.  Parsers must use the length field — never assume a fixed multihash length.

The portion-spec is opaque bytes interpreted by the predicate of the frame whose CID is referenced.  The predicate's defined selector grammar specifies how the portion-spec is structured.  Resolving a portion-spec requires fetching the frame body and consulting its predicate.

### What can be referenced

The reference type matters for what kinds of "drilling in" make sense:

- **Items** can be version-pinned but cannot have selectors — items are not single content blobs.  Drilling into an item means going to a specific frame within it, which is a *frame* reference, not an item reference.
- **Raw content** is opaque bytes.  Selectors don't apply because there's no type context.  If you need to target a portion of structured content, that content should be wrapped in a frame.
- **Frames** can have a binding-key (identifying a specific binding within the frame) and a portion-spec (identifying a portion of that binding's content).  These narrow the reference progressively.

For a reference like `#<frame-CID>\<binding-key>\<portion-spec>`, the binding-key identifies *which* binding holds the content, and the portion-spec identifies *what part* of that binding's content.  The predicate of the referenced frame interprets the portion-spec, with the binding's qualifiers as type context.  For example, an IMAGE frame might have a `VALUE:[JPEG, MASTER]` binding holding raw JPEG bytes; a portion-spec could specify pixel coordinates within that JPEG, with the format determined by the JPEG qualifier.

### Text form

References have a round-trippable text form.  Text encoding is **never canonical** — the binary form is what gets hashed and signed.  The text form exists for human consumption, debugging, audit trails, and round-trippable serialization in legal/textual contexts.

The structural characters (`@`, `~`, `#`, `\`) appear literally in both forms.  Multihash bytes are encoded using **multibase** — a producer-chosen encoding indicated by a single-character prefix:

| Prefix | Encoding |
|---|---|
| `b` | base32 (RFC 4648, no padding, lowercase) |
| `B` | base32 uppercase |
| `m` | base64 (no padding) |
| `M` | base64 with padding |
| `u` | base64url |
| `z` | base58btc |
| `f` | base16 (hex) lowercase |
| `F` | base16 uppercase |

(See the multiformats multibase specification for the full table.)

The producer picks any encoding; the decoder reads the multibase prefix and decodes accordingly.  Round-trip is exact regardless of encoding choice — multibase encoding is byte-preserving.

#### Examples

```
@bafkreigh2akiscaildc7vh...                              item, base32
@bafkreigh2akiscaildc7vh...\bGHKQXQ4MZ7BAVF...           item with version pin
~mIA7gxyzPN+VK...                                         raw content, base64
#zQmNS6MqvB...                                            whole frame, base58btc
#zQmNS6MqvB...\bROLE3HASH7Q...                            frame with binding-key
#zQmNS6MqvB...\bROLE3HASH7Q...\bPORTIONBYTES4M...         frame's binding portion
```

All multibase encodings are permitted.  No encoding is privileged.

## Encoding (CBOR)

A Datum is encoded as a CBOR array:

```
Body Datum:
  [
    Tag-6( <reference-bytes> ),     ; head reference
    [ <binding>, <binding>, ... ]   ; bindings
  ]                                  ; (2-element array)

Record Datum:
  [
    Tag-6( <reference-bytes> ),     ; head reference
    [ <binding>, <binding>, ... ],  ; bindings
    h'<varsig-bytes>'                ; signature
  ]                                  ; (3-element array)
```

The decoder dispatches on array length: 2 elements → body, 3 elements → record.

### Tag 6 (References)

Tag 6 wraps a CBOR byte string whose content is the reference encoding (prefix byte, multihash bytes, optional separators and additional sub-parts).  Tag 6 is used uniformly for all references — head references, binding-target references, anywhere a reference appears.

### Bindings and CompoundKeys

A binding is encoded as a CBOR array:

```
Binding:
  [
    <key>,                  ; CompoundKey: head sememe + qualifiers
    <target>,               ; the value (reference, literal, or inline frame)
    <index-flag>            ; boolean: should this binding be indexed for reverse lookup
  ]

CompoundKey:
  [
    <head-bytes>,           ; multihash IID of the head sememe
                            ; (a thematic role for binding-side use,
                            ;  a predicate for frame-within-item addressing)
    [<qualifier>, ...]      ; qualifiers (multihash IIDs or literals)
  ]
```

The **CompoundKey** is the universal compound semantic address used in two places:

1. **Inside a frame** to identify a binding (head = role: AGENT, THEME, VALUE, etc.; qualifiers narrow it: VALUE:[ENGLISH] vs VALUE:[SPANISH])
2. **Inside an item** to identify a frame (head = predicate: TITLE, GLOSS, MOVE, etc.; qualifiers distinguish multiple frames of the same predicate)

The structure is identical in both uses; only the semantic role of the head differs by context.

The target may be a Tag 6 reference, a Tag 23 inline frame, or a literal value (with appropriate CBOR encoding).

Bindings do not carry an "identity flag."  The identity-vs-non-identity distinction is no longer a per-binding property — bodies have no signature concern (no exclusion needed), and records exclude their signature structurally (it lives in its own slot).  The remaining flag is `index`, which is genuinely per-binding.

### Signatures (varsig)

A record's signature is a CBOR byte string whose content is **varsig-formatted**:

```
varsig-bytes = <varint algorithm-code><signature-data>
```

The algorithm code comes from the multicodec table.  Common codes:

| Code | Algorithm |
|---|---|
| `0xed` | Ed25519 |
| `0xe7` | ECDSA secp256k1 |
| `0x1200` | RSA |

(See the multiformats multicodec specification for the full table.)

This is consistent with how multihash encodes hash algorithms and multikey encodes public-key algorithms.  Self-describing, extensible, no new CBOR tag required.

### Public keys (multikey)

A signer's public key — typically the target of an `AGENT:[SIGNER]` binding — is encoded as multikey-formatted bytes:

```
multikey-bytes = <varint key-type-code><key-data>
```

Same multicodec table as varsig.  An Ed25519 public key uses `0xed` (the same code applies to both keys and signatures of the same algorithm).

### Signing payload

The signature in a record signs over the **first two elements of the record's encoded form** — that is, the head reference and the bindings, but NOT the signature itself.  Specifically:

```
signing-payload = hash( CBOR-encoding( [head-reference, bindings] ) )
signature = sign( signer-private-key, signing-payload )
```

This means the signature authenticates the body being attested (via the head reference) and the attestation context (signer, timestamp, any other bindings on the record).  Tampering with any of these invalidates verification.

The record's CID (its content hash) is computed over the *full* three-element form including the signature.  The signature does not sign over its own bytes (which would be circular).  The two operations — signing and content-addressing — are separate and independent.

## Tag inventory after this redesign

| Tag | Purpose |
|---|---|
| 6 | Reference (universal, for all reference types) |
| 7 | Typed value (for explicitly-typed literals when needed) |
| 8 | Signature semantics — reserved (signatures currently appear as positional byte strings in record Datums; Tag 8 is the explicit form when one is needed) |
| 9 | Quantity (magnitude + unit IID) |
| 10 | Encrypted envelope (Gordian-style multi-recipient ciphertext; see `encryption.md`) |
| 11 | Redacted marker (wraps a multihash representing an elided subtree; Merkle elision, see `encryption.md`) |
| 12–22 | Vacated — formerly protocol tags (REQUEST, DELIVERY, etc.); operations are now vocabulary-driven |
| 23 | Inline Datum (for nested datums as binding targets — expression trees, query patterns, sub-frames) |

Signatures are positional in record Datums (third array element), not tagged — Tag 8 is reserved for cases where a signature byte string appears outside that structural slot.

## Class structure

The Java class hierarchy reflects the unification:

```
Datum (abstract)
  ├── Body        — head reference is a meaning IID, no signature slot
  └── Record      — head reference is a content CID, has signature slot

Frame             — runtime container: Body + List<Record>
Manifest          — runtime container: Body + List<Record>
```

`Frame` and `Manifest` are thin wrappers around `(Body, List<Record>)` providing convention-specific accessors.  `Frame.predicate()` reads from the body's head reference; `Manifest.iid()` reads from the body's THEME binding; etc.

The classes `FrameBody`, `FrameRecord`, and the manifest-specific signing structures are no longer needed.  Their logic moves into `Body` and `Record` (or their wrappers).

## Why this works

**One structural primitive.**  Every signed-or-signable object in the system is a Datum.  No special cases, no parallel hierarchies.

**Self-describing throughout.**  Multihash for hash algorithms, multibase for text encoding, multikey for public keys, varsig for signatures.  Every prefix tells the parser what follows.  Algorithms can evolve without changing the encoding format.

**Stable assertion identity.**  Bodies are content-addressed.  Their CIDs depend only on their assertion content, not on who signed them.  Multiple signers produce multiple records, all referencing one body.

**Round-trippable text form.**  References have a clean URI-like text representation.  Producers choose any multibase encoding for the multihashes; the binary form is canonical.  This satisfies legal/audit requirements for textual attribution while keeping the binary form authoritative for hashing and signing.

**Extensibility through vocabulary.**  Special behaviors (like indexing) live as data on role definitions, not as hardcoded structural features.  New roles, new behaviors, new selector grammars — all without changing the encoding.

## Migration notes

Several files currently reflect the prior architecture and will be updated as implementation proceeds:

- `frames.md` — describes FrameBody and FrameRecord as separate structures.  Update to reflect Body and Record as configurations of Datum.
- `manifest.md` — describes Manifest as its own class.  Update to reflect Manifest as a runtime container around a Body Datum and its Records.
- `cg-cbor.md` — describes the prior tag and encoding scheme.  Update to reflect the unified Datum encoding, the three-prefix reference scheme, and the use of multikey/varsig.

Stored data is not relevant — breaking encoding changes are acceptable until production deployment.  Implementation work for this redesign is foundation-priority and should precede any further work that depends on the encoding format.
