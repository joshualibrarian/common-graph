# CG-CBOR Specification

CG-CBOR is Common Graph's CBOR profile — **the** encoding format for all graph data structures. It defines conventions and tags for encoding data efficiently, deterministically, and unambiguously.

The encoding is grounded in the **Datum** primitive (see `datum.md`). Bodies and records both encode as Datums; the same encoding rules apply throughout.

## Philosophy

CG-CBOR is a **profile** of CBOR tailored for Common Graph's needs:

- **Self-describing**: Tagged values can be decoded without external schema
- **Compact**: Uses 1-byte tags from the efficient 0-23 range
- **Deterministic**: Canonical encoding for content addressing (sorted keys, minimal integer encoding)
- **Self-describing prefixes**: Multiformats throughout — multihash, multibase, multikey, varsig
- **Exact numerics**: No IEEE 754 floats; use Rational, Decimal, or Quantity for precision
- **Shorthand conventions**: Bare CBOR primitives map to common CG value types

## Tag Allocations

CG-CBOR uses unassigned tags in the 1-byte range (6-23):

| Tag | Name | Description |
|-----|------|-------------|
| 6 | `CG-REF` | Universal reference (item, content, or frame, with optional sub-parts) |
| 7 | `CG-VALUE` | Explicitly typed value (when shorthand won't do) |
| 9 | `CG-QTY` | Quantity (magnitude + unit IID) |
| 10 | `CG-ENCRYPTED` | Encrypted envelope — *reserved* |
| 11 | `CG-REQUEST` | Peer protocol: request for data |
| 12 | `CG-DELIVERY` | Peer protocol: delivery of data |
| 13 | `CG-AUTH` | Session protocol: authentication exchange |
| 14 | `CG-CONTEXT` | Session protocol: get/set focused item |
| 15 | `CG-DISPATCH` | Session protocol: verb invocation |
| 16 | `CG-LOOKUP` | Session protocol: token search/completion |
| 17 | `CG-SUBSCRIBE` | Session protocol: subscribe/unsubscribe |
| 18 | `CG-EVENT` | Session protocol: push notification |
| 19 | `CG-STREAM` | Session protocol: chunked data |
| 20 | `CG-HEARTBEAT` | Shared: keep-alive signal |
| 21 | `CG-ACK` | Shared: acknowledgment |
| 22 | `CG-ERROR` | Shared: error response |
| 23 | `CG-FRAME` | Inline nested frame (frame as binding target) |

---

## Datum Encoding

A Datum is encoded as a CBOR array. The array length distinguishes body (2) from record (3):

```
Body Datum:
  [
    Tag-6( <reference-bytes> ),       ; head reference
    [ <binding>, <binding>, ... ]     ; bindings
  ]

Record Datum:
  [
    Tag-6( <reference-bytes> ),       ; head reference
    [ <binding>, <binding>, ... ],    ; bindings
    h'<varsig-bytes>'                  ; signature (CBOR byte string)
  ]
```

Decoder dispatch: 2-element array → body; 3-element array → record.

The body's CID is the hash of the full encoded body. The record's CID is the hash of the full encoded record (including signature). The signing payload (what the signature signs over) is the hash of the record's content **excluding** the signature — that is, the encoded form of the first two elements.

### Bindings

Each binding is a CBOR array:

```
Binding:
  [
    <role-iid-bytes>,         ; multihash IID of the role sememe (CBOR byte string)
    [<qualifier>, ...],       ; CBOR array; each qualifier is a sememe IID or literal
    <target>,                 ; the value (Tag 6 reference, Tag 23 inline frame, or literal)
    <index-flag>              ; CBOR boolean
  ]
```

The target may be:
- A Tag 6 reference (see below) for item, content, or frame references
- A Tag 23 inline frame (see below) for nested expression trees
- A literal value: bare CBOR primitive (shorthand) or Tag 7 typed value

---

## Tag 6: CG-REF (Universal Reference)

A reference identifies a target — an item, a piece of content, or a frame (with optional path into it). Tag 6 is used uniformly for all references: head references on Datums and binding targets that are references.

### Encoding

```
Tag 6: bytes(<prefix><multihash>[\<additional-parts>])
```

The payload is a byte string. The first byte is the **structural prefix** indicating the reference type:

| Prefix | ASCII | Meaning |
|--------|-------|---------|
| `@` | 0x40 | Item reference (an IID, optionally version-pinned) |
| `~` | 0x7E | Raw content reference (an opaque content CID) |
| `#` | 0x23 | Frame reference (a frame body CID, optionally with binding-key and portion-spec) |

A single **separator byte** is used internally between sub-parts:

| Separator | ASCII | Meaning |
|-----------|-------|---------|
| `\` | 0x5C | Separates sub-parts of a reference |

### Reference Forms

```
@<IID>                                              item
@<IID>\<VID>                                        item with version pin
~<CID>                                              raw content
#<CID>                                              whole frame
#<CID>\<binding-key>                                specific binding within frame
#<CID>\<binding-key>\<portion-spec>                 portion of binding's content
```

All multihashes (IID, VID, CID) are self-describing for both algorithm and length. Parsers must use the multihash length field — never assume a fixed length. Different hash algorithms produce different lengths (SHA-256: 34 bytes total, SHA-512: 66 bytes total, etc.).

The portion-spec is opaque bytes interpreted by the predicate of the frame whose CID is referenced. The predicate's defined selector grammar specifies how the portion-spec is structured. Resolving a portion-spec requires fetching the frame body and consulting its predicate.

### Parsing

1. Read the prefix byte (`@`, `~`, or `#`)
2. Read a multihash (varint algorithm code, varint length, that many digest bytes)
3. If end of buffer, done
4. Else, read separator `\` and:
   - For `@` prefix: a second multihash (the VID)
   - For `#` prefix: a binding-key (CBOR-encoded structured value), then optionally another `\` and portion-spec bytes (run to end of buffer)

### Reference Type Constraints

- **Item references** (`@`): may be version-pinned but cannot have selectors. Drilling into an item means going to a specific frame, which is a frame reference.
- **Raw content references** (`~`): opaque bytes. Selectors don't apply — there's no type context for portion targeting.
- **Frame references** (`#`): may have a binding-key (identifying a specific binding) and a portion-spec (identifying a portion of that binding's content). The binding-key must be present if portion-spec is.

### Text Representation

References have a round-trippable text form. The structural characters (`@`, `~`, `#`, `\`) appear literally. Multihash bytes are encoded using **multibase** — a producer-chosen encoding indicated by a single-character prefix:

| Prefix | Encoding |
|--------|----------|
| `b` | base32 lowercase (no padding) |
| `B` | base32 uppercase |
| `m` | base64 (no padding) |
| `M` | base64 with padding |
| `u` | base64url |
| `z` | base58btc |
| `f` | base16 (hex) lowercase |
| `F` | base16 uppercase |

The text form is **never canonical** — the binary form is what gets hashed and signed. The text form exists for human consumption, debugging, audit trails, and round-trippable serialization in legal/textual contexts.

#### Examples

```
@bafkreigh2akiscaildc7vh...                              item, base32
@bafkreigh2akiscaildc7vh...\bGHKQXQ4MZ7BAVF...           item with version pin
~mIA7gxyzPN+VK...                                         raw content, base64
#zQmNS6MqvB...                                            whole frame, base58btc
#zQmNS6MqvB...\bROLE3HASH7Q...                            frame with binding-key
#zQmNS6MqvB...\bROLE3HASH7Q...\bPORTIONBYTES4M...         frame's binding portion
```

---

## Signatures: varsig

A record's signature is encoded as a **CBOR byte string** containing varsig-formatted bytes. Varsig is part of the multiformats family — a self-describing format for cryptographic signatures.

```
varsig-bytes = <varint algorithm-code><signature-data>
```

The algorithm code comes from the multicodec table. Common codes:

| Code | Algorithm |
|------|-----------|
| `0xed` | Ed25519 |
| `0xe7` | ECDSA secp256k1 |
| `0x1200` | RSA |

The signature byte string occupies the third element of a record's encoded form. It is structurally distinct from bindings — verifiers identify it by position, not by role.

### Signing Payload

The signature signs over the hash of the record's encoded form **excluding the signature itself**:

```
signing-payload = hash( CBOR-encoding( [head-reference, bindings] ) )
signature = sign( signer-private-key, signing-payload )
```

The signature signs the head reference (which identifies the body via `#<body-CID>`) plus the bindings (signer, timestamp, per-record CONFIG, etc.). Tampering with any of these invalidates verification.

The record's CID, by contrast, is the hash of the full three-element form including the signature. The two operations — signing and content-addressing — are separate and independent.

---

## Public Keys: multikey

A signer's public key — typically the target of an `AGENT:[SIGNER]` binding — is encoded as **multikey-formatted bytes** in a CBOR byte string. Multikey is part of the multiformats family.

```
multikey-bytes = <varint key-type-code><key-data>
```

Same multicodec table as varsig:

| Code | Key Type |
|------|----------|
| `0xed` | Ed25519 public key |
| `0xec` | X25519 public key |
| `0xe7` | secp256k1 public key |

The key bytes self-describe their algorithm. Verifiers read both the signature's varsig prefix and the public key's multikey prefix and verify the algorithms are compatible.

---

## Multihash

All content identifiers (IIDs, CIDs, VIDs) use the **multihash** format:

```
multihash = <varint algorithm-code><varint length><digest-bytes>
```

Examples:

| Algorithm | Code | Length | Total |
|-----------|------|--------|-------|
| SHA-256 | `0x12` | `0x20` (32) | 34 bytes |
| BLAKE3-256 | `0x1e` | `0x20` (32) | 34 bytes |
| SHA-512 | `0x13` | `0x40` (64) | 66 bytes |
| SHA3-512 | `0x14` | `0x40` (64) | 66 bytes |

The format is self-describing for both algorithm and length. New algorithms can be adopted without changing the encoding format. Parsers must read the length byte to determine how many digest bytes follow — never assume a fixed multihash length.

---

## Shorthand Conventions

Bare CBOR primitives are **shorthand** for common CG value types. This avoids the explicit type IID overhead.

| CBOR Encoding | Implicit CG Type | Notes |
|---------------|------------------|-------|
| text string | `cg.value:text` | UTF-8, no language tag |
| integer | `cg.value:integer` | Arbitrary precision |
| boolean | `cg.value:boolean` | true/false |
| byte string | `cg.value:bytes` | Raw binary |
| Tag 4 `[m,e]` | `cg.value:decimal` | Standard CBOR decimal fraction |
| Tag 1 (epoch) | `cg.value:instant` | Standard CBOR timestamp |
| `[num, den]` | `cg.value:rational` | 2-element integer array |

**Note on text:** Bare text strings have no language tag. When language matters (titles, descriptions, multilingual content), use Tag 7 with a language code: `Tag(7, ["eng", "Hello"])`.

### When to Use Explicit Typing (Tag 7)

Use `CG-VALUE` (Tag 7) only when:
1. The type cannot be inferred from the CBOR primitive
2. You need a custom/semantic type (e.g., `cg.unit:mm`)
3. Disambiguation is required in a polymorphic context

---

## Tag 7: CG-VALUE (Namespaced/Typed Value)

A value qualified by a namespace — either a **language code** for text or a **type IID** for other values.

### Encoding

```
Tag 7: [<namespace>, <payload>]
```

A 2-element CBOR array where the namespace determines interpretation:

| Namespace | Format | Meaning |
|-----------|--------|---------|
| 3-char text string | ISO 639-3 language code | Language-tagged text |
| Multihash byte string | ItemID (multihash) | ValueType or AddressSpace |

### Language-Tagged Text

Text content tagged with its language using ISO 639-3 codes (3 letters):

```
Tag(7, ["eng", "Hello World"])     // English
Tag(7, ["fra", "Bonjour Monde"])   // French
Tag(7, ["spa", "Hola Mundo"])      // Spanish
```

ISO 639-3 covers ~7,000 languages (including minority, extinct, constructed). The 3-byte encoding is consistent and the language IID can be derived from the code when needed.

### Explicitly Typed Values

Values with a full IID namespace for custom types or address spaces:

```
Tag(7, [bytes(<my-custom-type-iid>), {...custom structure...}])
Tag(7, [bytes(<cg.unit:mm>), 25])                              // 25 millimeters
Tag(7, [bytes(<AtDomain-iid>), "alice@example.com"])           // standalone email
```

### Address Space Optimization

When an **AddressSpace is the predicate** of a binding's parent frame, the binding target can be bare text (the predicate already identifies the type):

```
// In a frame whose predicate is AT_DOMAIN — bare text suffices
AT_DOMAIN { THEME → alice, VALUE → "alice@example.com" }

// Standalone (no predicate context) — Tag 7 needed
Tag(7, [bytes(<AtDomain-iid>), "alice@example.com"])
```

---

## Tag 9: CG-QTY (Quantity)

A quantity combining a magnitude with a unit. The primary way to encode measurements.

### Encoding

```
Tag 9: [<magnitude>, bytes(<unit-iid>)]
```

A 2-element CBOR array:

1. **magnitude**: numeric value (CBOR integer, Tag 4 decimal, or `[num, den]` rational)
2. **unit-iid**: multihash bytes referencing the unit definition

### Examples

```
Tag(9, [Tag(4, [254, -1]), bytes(<cg.unit:mm>)])     // 25.4 millimeters
Tag(9, [[3, 4], bytes(<cg.unit:cup>)])               // 3/4 cup
Tag(9, [100, bytes(<cg.unit:m>)])                    // 100 meters
```

---

## Tag 23: CG-FRAME (Inline Nested Frame)

An inline nested frame, used as a binding target within another frame. Enables expression trees and compositional frames.

### Encoding

The same as a body Datum — Tag 23 simply indicates that this CBOR array is an inline frame rather than something to be stored as a top-level Datum.

```
Tag 23: [
  Tag-6(@<predicate-IID>),       ; head reference
  [<binding>, <binding>, ...]    ; bindings
]
```

### Use Cases

- **Expression trees**: `MUL { THEME → ADD { THEME→3, GOAL→5 }, GOAL → 2 }`
- **Parametric modeling**: Mathematical expressions with coordinate bindings
- **CSG operations**: Boolean operations composing compound structures

Nesting is recursive — an inline frame can itself contain inline frames. The nested frame has no independent CID — it's part of the enclosing frame's body hash.

---

## Canonical Encoding Rules

For content addressing, CG-CBOR follows deterministic encoding:

1. **Map keys**: Sorted lexicographically by encoded bytes
2. **Integers**: Minimal encoding (no leading zeros)
3. **No floats**: CBOR float types (major type 7, additional info 25-27) are **forbidden**
4. **No indefinite lengths**: Always use definite-length encoding
5. **No duplicate map keys**: Each key appears exactly once

These rules ensure identical content produces identical bytes, enabling content-addressed storage.

### Why No Floats?

IEEE 754 floating-point numbers are problematic for content addressing:

- **Non-deterministic encoding**: The same logical value can have multiple bit representations
- **Platform variance**: NaN, signed zero, denormals behave differently across systems
- **Precision loss**: 0.1 + 0.2 != 0.3 in IEEE 754

CG-CBOR uses exact numeric types instead:

| Need | CG-CBOR Solution |
|------|------------------|
| Fractions | Rational `[numerator, denominator]` |
| Decimal numbers | Tag 4 `[mantissa, exponent]` |
| Measurements | Tag 9 CG-QTY `[magnitude, unit-iid]` |
| IEEE 754 bits | Tag 7 CG-VALUE with `ieee754-double` type (escape hatch) |

The escape hatch exists for interop scenarios where IEEE 754 bit patterns must be preserved exactly, but the float is wrapped in a typed value — never a raw CBOR float.

---

## Comparison with DAG-CBOR

| Aspect | DAG-CBOR (IPLD) | CG-CBOR |
|--------|-----------------|---------|
| Custom tags | Only tag 42 (CID) | Tags 6-23 (data types + protocol messages) |
| Floats | Forbidden | Forbidden (use Rational/Decimal) |
| Links | CID only | Universal reference (item/content/frame, with sub-parts) |
| Typed values | Implicit | Shorthand + explicit CG-VALUE |
| Exact numerics | Integers only | Integers, Rational, Decimal, Quantity |
| Cryptographic primitives | None | Multihash, Multikey, Varsig (multiformats family) |
| Philosophy | Universal interchange | Graph-native semantics |

CG-CBOR shares DAG-CBOR's commitment to deterministic encoding but adds richer graph-native semantics and full multiformats integration.

---

## References

**External resources:**
- [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) — Current CBOR specification
- [CBOR Tags Registry](https://www.iana.org/assignments/cbor-tags/cbor-tags.xhtml) — IANA tag allocations
- [IPLD DAG-CBOR Spec](https://ipld.io/specs/codecs/dag-cbor/spec/) — Content-addressed CBOR (IPLD)
- [Multiformats](https://multiformats.io/) — Multihash, multibase, multikey, varsig family
- [Multicodec table](https://github.com/multiformats/multicodec/blob/master/table.csv) — Algorithm code registry

**Related Common Graph documents:**
- [datum.md](datum.md) — The unified Datum primitive (foundation for this encoding)
- [frames.md](frames.md) — How frames use Body and Record Datums
- [manifest.md](manifest.md) — How manifests use the same primitive

**Academic foundations:**
- [Bormann, Hoffman 2020 — CBOR RFC 8949](references/Bormann%2C%20Hoffman%202020%20-%20CBOR%20RFC%208949.txt) — Current CBOR specification that CG-CBOR extends
