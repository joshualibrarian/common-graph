# CG-CBOR

CG-CBOR is the first CG-capable encoding format. It's a CBOR profile — deterministic, exhaustively specified, and just rich enough to give Common Graph's semantic primitives recognizable shapes on the wire. CG-CBOR is what Common Graph implementations exchange today, store on disk, and feed to the canonical walker for hashing.

CG-CBOR is *an* encoding, not *the* encoding. Common Graph's data model lives above the encoding layer (see [`datum.md`](datum.md)); structural identity comes from the canonical walker (see [`canonical.md`](canonical.md)); the prefix-typed reference scheme is independent of any wire format (see [`ref-scheme.md`](ref-scheme.md)). CG-CBOR's job is just to lay those primitives out as bytes in a way every implementation agrees on.

A future CG-JSON, CG-flat-binary, or domain-specific encoding can exist alongside CG-CBOR. Each produces its own bytes for the same datum, its own ContentIDs, but the same DatumIDs — interoperable by structural identity rather than by wire format.

This document specifies the CBOR profile: which tags identify which primitives, how the bytes lay out for each primitive, and the deterministic-encoding rules that make hashes stable.

## What CG-CBOR distinguishes

The eight CG semantic primitives, each with a CBOR tag:

| Tag | Primitive | Wire shape |
|---|---|---|
| 6 | **RATIONAL** | 2-element array `[numerator, denominator]` |
| 7 | **REF** | Bytestring carrying the reference byte layout (see [`ref-scheme.md`](ref-scheme.md)) |
| 8 | **BODY** | 2-element array `[head, [bindings]]` |
| 9 | **RECORD** | 3-element array `[head, [bindings], signature]` |
| 10 | **SIG** | Bytestring carrying varsig-encoded signature bytes |
| 11 | **KEY** | Bytestring carrying multikey-encoded key bytes |
| 12 | **REDACTED** | Bytestring carrying the preserved structural hash |
| 13 | **ENCRYPTED** | Encrypted-envelope marker around an inner datum |

Each tag is one byte (tags 0–23 encode as a single byte in CBOR's compact form). The tag numbers are fixed by the protocol; implementations on the wire agree on them.

Plus the standard CBOR tags from RFC 8949 (0 through 5), accepted on decode for interop:

| Tag | Standard meaning | Use in CG-CBOR |
|---|---|---|
| 0 | text date-time string | accepted on decode |
| 1 | epoch-based date-time | emitted for `Instant` values |
| 2 | positive bignum | emitted for large positive integers |
| 3 | negative bignum | emitted for large negative integers |
| 4 | decimal fraction `[exp, mantissa]` | emitted for `BigDecimal` values |
| 5 | bigfloat | accepted on decode |

Plain CBOR primitives (integers, byte strings, text strings, arrays, maps, true, false, null) round-trip without tagging — they're what bindings' literal targets carry.

## Bodies and records

A body encodes as Tag 8 wrapping a 2-element array:

```
Tag(8) [
  <head>,        ; a Tag-7 reference (REF), typed by its prefix byte
  [<binding>, <binding>, ...]
]
```

A record encodes as Tag 9 wrapping a 3-element array — same shape as a body, plus a signature:

```
Tag(9) [
  <head>,        ; a Tag-7 reference (the datum being attested)
  [<binding>, <binding>, ...],
  <signature>    ; a Tag-10 SIG bytestring
]
```

The head is always a typed reference — Tag 7 wrapping the reference's prefix-and-payload bytes. For bodies, the prefix is `@` (concrete), `?` (query), or `!` (schema); for records, it's `#` (datum) pointing at the body being attested.

The 2-vs-3 element distinction is what tells a decoder whether to read this datum as a body or a record. The tag distinguishes them at the outermost layer; the array length confirms.

A frame on the wire is therefore the body, followed by zero or more records. They're independent CBOR values, each addressable by its own ContentID. The frame is the *runtime aggregate* of fetching them together — no enclosing CBOR structure binds them on disk or on the network.

## Bindings

A binding encodes as a 2-element or 3-element CBOR array:

```
[<key>, <target>]               ; ordinary binding
[<key>, <target>, <index>]      ; ordered-group member
```

The **key** is a CompoundKey — the binding's role plus its qualifiers. It encodes as a CBOR array whose first element is a Tag-7 reference (the role sememe) and whose remaining elements are qualifier values in canonical order. A bare-role binding (no qualifiers) encodes its key as a 1-element array.

The **target** is whatever value the binding carries — a Tag-7 reference, a Tag-8 nested body, a primitive (integer / text / boolean / byte string), a Tag-4 decimal, a Tag-1 instant, a Tag-6 rational, a Tag-12 redaction marker, etc. Encoders dispatch on the runtime type to pick the appropriate CBOR shape.

The **index** is an integer present only when the binding belongs to an ordered group of same-key siblings. Absent for unordered bindings (the common case). The two-vs-three element distinction signals presence.

## References

A reference encodes as Tag 7 wrapping a bytestring:

```
Tag(7) <bytestring>
```

The bytestring's layout is the reference protocol's own contract (see [`ref-scheme.md`](ref-scheme.md)): a one-byte prefix (`0x40`/`0x3F`/`0x21`/`0x7E`/`0x23` for `@`/`?`/`!`/`~`/`#`), the target identifier (typically a multihash), and optional sub-parts separated by `0x5C` (e.g., version pin, binding-key drill-in).

CBOR's job ends at "Tag 7 wraps a bytestring." Everything inside the bytestring is the reference protocol's concern, including which variant of reference it is. Decoders parse the bytestring's first byte to pick the right reference type.

## Signatures and keys

Signatures encode as Tag 10 wrapping a bytestring of varsig-encoded signature bytes. The varsig encoding is self-describing — its leading bytes identify the algorithm — so the same Tag 10 carries Ed25519, ECDSA-P256, Schnorr, or future signatures without needing a separate format hint.

Keys encode as Tag 11 wrapping a bytestring of multikey-encoded key bytes. Multikey, like multihash and varsig, is a self-describing prefix scheme — leading bytes name the algorithm and key type.

Both tags are byte-string carriers; the actual structure is one layer below CG-CBOR, in the multiformats world. CG-CBOR just wraps them with their tag so decoders know what to do with the bytes.

## Specialized values

**Rational** (Tag 6) — exact rational numbers as 2-element arrays of numerator and denominator. Common Graph forbids IEEE 754 floats for content-addressed data because they're not deterministic across implementations; rationals (and decimals at Tag 4) cover the use cases that would have wanted floats.

**Redacted** (Tag 12) — a Merkle-elision marker. The bytestring inside is the structural hash the original value would have contributed to the canonical walk. A decoder sees this and knows: "the original target was here but is not available; for hashing purposes, use this preserved hash; for value access, this slot is opaque." See [`canonical.md`](canonical.md) for how redaction preserves DatumID.

**Encrypted** (Tag 13) — an envelope wrapping an encrypted inner datum. The envelope carries metadata for recipient resolution; the inner bytes decrypt to a normal datum when the recipient has the key. The structural walker hashes the envelope's *outer* shape, so the DatumID of an encrypted datum is well-defined even before decryption.

## Deterministic encoding rules

CG-CBOR is **deterministic** — every implementation encoding the same datum produces the same bytes. This is what makes ContentID stable: two parties encoding the same datum can compare ContentIDs and know they have the same wire form.

Determinism comes from a small set of rules every encoder must follow:

- **Smallest integer encoding.** Integers use the shortest CBOR form that fits. 0 is one byte; 17 is one byte (encoded in the argument); 256 is three bytes (one tag byte plus a two-byte unsigned 16-bit). Encoders may not emit integers in longer forms than necessary.
- **Map key ordering.** Maps in deterministic CBOR sort their keys by canonical CBOR byte order — shorter keys before longer keys, then lexicographic within each length.
- **No floating-point numbers.** IEEE 754 floats (CBOR types `float16`/`float32`/`float64`) are forbidden for any data that will be hashed or content-addressed. Rationals (Tag 6) and Decimals (Tag 4) cover the numeric range floats would have served.
- **Tag preference.** When a value can be encoded multiple ways (e.g., a small bignum as Tag 2 or as a plain integer), encoders prefer the most compact representation. The choice is fixed; implementations don't get to pick.
- **Indefinite-length forms forbidden.** CBOR allows arrays and maps to be encoded with indefinite-length prefixes. Deterministic encoding requires definite-length prefixes always.
- **No duplicate map keys.** Each map's keys are unique. Encoders that detect duplicates fail loudly.

These rules are inherited from RFC 8949's "Core Deterministic Encoding" profile, with the float-ban and CG-specific tag conventions layered on top. Implementations that need to emit non-deterministic CBOR (rare; mostly diagnostic) do so outside the wire-format path.

## What CG-CBOR is not responsible for

A few specific responsibilities live in other layers and are not CG-CBOR's:

- **The reference byte layout** — owned by [`ref-scheme.md`](ref-scheme.md). CG-CBOR provides the Tag 7 envelope; the bytes inside are the reference protocol's contract.
- **The structural walk that produces DatumIDs** — owned by [`canonical.md`](canonical.md). The walker operates on the data model in memory, not on CBOR bytes. CG-CBOR's deterministic-encoding rules make ContentIDs stable; they don't compute DatumIDs.
- **The data model itself** — owned by [`datum.md`](datum.md). What a body is, what bindings are, what a frame versus manifest means — all defined above the encoding layer.
- **What a frame says** — owned by [`frames.md`](frames.md), [`vocabulary.md`](vocabulary.md), and the predicates' own manifests. CG-CBOR encodes a frame's bytes; it has nothing to say about what those bytes *mean*.

This is the natural layering: data model on top, structural walker below it, encoding format below that, transport below that. CG-CBOR sits at the encoding layer and stays in its lane.

## Diagnostic notation

For documentation and debugging, CBOR has a standard diagnostic notation. CG-CBOR uses an extended convention that names the tags symbolically:

```
BODY(REF(@authored), [
  [REF(@AGENT), REF(@tolkien)],
  [REF(@THEME), REF(@hobbit)]
])
```

Reads more or less like the abstract data-shape notation in [`STYLE.md`](STYLE.md), with the tag names made explicit. Useful for log output and protocol traces; not a wire format.

## Relations

- [`datum.md`](datum.md) — the data model CG-CBOR encodes.
- [`canonical.md`](canonical.md) — the structural walker that produces DatumIDs, independent of CG-CBOR.
- [`ref-scheme.md`](ref-scheme.md) — the byte layout inside Tag 7.
- [`content.md`](content.md) — content-addressed bytes; how ContentIDs relate to encoded forms.
- [`encryption.md`](encryption.md) — Tag 13 encrypted envelopes in detail.
- [`storage.md`](storage.md) — how CG-CBOR bytes are persisted and indexed.
