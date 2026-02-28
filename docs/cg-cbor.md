# CG-CBOR Specification

CG-CBOR is Common Graph's CBOR profile — **the** encoding format for all graph data structures. It defines conventions and tags for encoding data efficiently, deterministically, and unambiguously.

## Philosophy

Like IPLD's DAG-CBOR, CG-CBOR is a **profile** of CBOR tailored for Common Graph's needs:

- **Self-describing**: Tagged values can be decoded without external schema
- **Compact**: Uses 1-byte tags from the efficient 0-23 range
- **Deterministic**: Canonical encoding for content addressing (sorted keys, minimal integer encoding)
- **Rich references**: First-class support for item references with optional paths
- **Exact numerics**: No IEEE 754 floats; use Rational or Decimal for precision
- **Shorthand conventions**: Bare CBOR primitives map to common CG value types

## Tag Allocations

CG-CBOR uses unassigned tags in the 1-byte range (6-15, 20):

| Tag | Name | Description |
|-----|------|-------------|
| 6 | `CG-REF` | Item reference (IID with optional version/component/selector) |
| 7 | `CG-VALUE` | Explicitly typed value (when shorthand won't do) |
| 8 | `CG-SIG` | Signed envelope |
| 9 | `CG-QTY` | Quantity (magnitude + unit) |
| 10 | `CG-ENCRYPTED` | Encrypted envelope — *reserved* |
| 11-15 | — | Reserved for future CG use |
| 20 | — | Reserved for future CG use |

---

## Shorthand Conventions

Bare CBOR primitives are **shorthand** for common CG value types. This saves ~36 bytes per value by avoiding the explicit type IID.

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

### When to use explicit typing (Tag 7)

Use `CG-VALUE` (Tag 7) only when:
1. The type cannot be inferred from the CBOR primitive
2. You need a custom/semantic type (e.g., `cg.unit:mm`)
3. Disambiguation is required in a polymorphic context

**Example savings:**

```
// Explicit (wasteful for common types):
Tag(7, [<34-byte type IID>, "hello"])  // ~40 bytes

// Shorthand (preferred):
"hello"                                 // 6 bytes
```

---

## Tag 6: CG-REF (Item Reference)

A reference to an item in the graph, optionally pinned to a version and/or addressing a specific component and selector path.

### Encoding

```
Tag 6: bytes(<iid>[@<vid>][~<hid>][/<selector>])
```

The payload is a byte string containing concatenated components:

| Component | Prefix | Format | Required |
|-----------|--------|--------|----------|
| Item ID | *(none)* | Multihash bytes | Yes |
| Version ID | `@` (0x40) | Multihash bytes | No |
| Handle ID | `~` (0x7E) | Multihash bytes | No |
| Selector | `/` (0x2F) | UTF-8 bytes | No |

### Parsing

Since multihashes are self-delimiting (they encode their own length in the second byte), parsing is unambiguous:

1. Read the ItemID multihash (required)
2. If next byte is `@` (0x40): skip it, read VersionID multihash
3. If next byte is `~` (0x7E): skip it, read HandleID multihash
4. If next byte is `/` (0x2F): skip it, remaining bytes are selector (UTF-8)

### Examples

**Simple item reference (just IID):**
```
Tag(6, bytes(0x1220...))  // 34 bytes for SHA-256 ItemID
```

**Item at specific version:**
```
Tag(6, bytes(0x1220...<iid>  0x40  0x1220...<vid>))
                              ^
                              @ prefix
```

**Item's component:**
```
Tag(6, bytes(0x1220...<iid>  0x7E  0x1220...<hid>))
                              ^
                              ~ prefix
```

**Full path (item@version~component/selector):**
```
Tag(6, bytes(<iid> @ <vid> ~ <hid> / "key[0].name"))
```

### Text Representation

For human-readable formats (logs, debugging), CG-REF can be rendered as:

```
iid:z4m...abc@vid:z4m...def~vault/keys[0]
```

---

## Tag 7: CG-VALUE (Namespaced/Typed Value)

A value qualified by a namespace — either a **language code** for text or a **type IID** for other values.

### Encoding

```
Tag 7: [<namespace>, <payload>]
```

A 2-element CBOR array where the namespace determines interpretation:

| namespace | Format | Meaning |
|-----------|--------|---------|
| 3-char text string | ISO 639-3 language code | Language-tagged text |
| 34-byte byte string | ItemID (multihash) | ValueType or AddressSpace |

### Language-Tagged Text

Text content tagged with its language using ISO 639-3 codes (3 letters):

```
Tag(7, ["eng", "Hello World"])     // English
Tag(7, ["fra", "Bonjour Monde"])   // French
Tag(7, ["spa", "Hola Mundo"])      // Spanish
Tag(7, ["tlh", "nuqneH"])          // Klingon (yes, it has a code)
Tag(7, ["egy", "Ancient text..."])  // Ancient Egyptian
```

**Why 3-letter codes?**
- ISO 639-3 covers ~7,000 languages (including minority, extinct, constructed)
- 2-letter ISO 639-1 only covers ~180 major languages
- Consistent 3-byte encoding simplifies parsing
- Language IID can be derived from code when needed

**When to use language tags:**
- Text content where language matters (titles, descriptions, names)
- Multilingual values on the same relation predicate
- Not needed for language-agnostic content (identifiers, codes)

### Explicitly Typed Values

Values with a full IID namespace for custom types or address spaces:

```
Tag(7, [bytes(<valueType-iid>), <payload>])
```

**Custom typed value:**
```
Tag(7, [bytes(<my-custom-type-iid>), {...custom structure...}])
```

**Semantic unit (when not using CG-QTY):**
```
Tag(7, [bytes(<cg.unit:mm>), 25])  // 25 millimeters
```

**Address (standalone, outside relation context):**
```
Tag(7, [bytes(<AtDomain-iid>), "alice@example.com"])
Tag(7, [bytes(<E164Phone-iid>), "+1-555-123-4567"])
```

### Namespace Discrimination

Parsers distinguish by the first element:

| First element | Detection | Interpretation |
|---------------|-----------|----------------|
| CBOR text, 3 chars | `type == TextString && length == 3` | Language code |
| CBOR bytes, 34 bytes | `type == ByteString && length == 34` | ItemID |

### Address Space Optimization

When an **AddressSpace is the relation predicate**, the object can be bare text:

```
// In relation with AddressSpace predicate — bare text is fine
alice -> AtDomain -> "alice@example.com"
alice -> E164Phone -> "+1-555-123-4567"

// Standalone (no predicate context) — use Tag 7
Tag(7, [bytes(<AtDomain-iid>), "alice@example.com"])
```

This avoids redundancy: the predicate already identifies the address space.

### When to Use Tag 7

| Scenario | Use Tag 7? | Reason |
|----------|------------|--------|
| Plain text "hello" | No | Use bare text string (shorthand) |
| Text with language "Hello"@eng | **Yes** | Language qualification needed |
| Integer 42 | No | Use bare integer (shorthand) |
| Decimal 3.14 | No | Use Tag 4 (standard CBOR) |
| Custom type `cg.my:thing` | **Yes** | No shorthand exists |
| Address in AddressSpace relation | No | Predicate implies type |
| Address standalone | **Yes** | Need type context |

---

## Tag 9: CG-QTY (Quantity)

A quantity combining a magnitude with a unit. This is the primary way to encode measurements.

### Encoding

```
Tag 9: [<magnitude>, bytes(<unit-iid>)]
```

A 2-element CBOR array:

1. **magnitude**: The numeric value, encoded as:
   - CBOR integer (for whole numbers)
   - Tag 4 `[mantissa, exponent]` (for decimals)
   - `[numerator, denominator]` (for rationals)
2. **unit-iid**: ItemID (multihash bytes) referencing the unit definition

### Examples

**25.4 millimeters:**
```
Tag(9, [
  Tag(4, [254, -1]),           // 25.4 as decimal
  bytes(<cg.unit:mm>)          // millimeter unit IID
])
```

**3/4 cup:**
```
Tag(9, [
  [3, 4],                      // 3/4 as rational
  bytes(<cg.unit:cup>)         // cup unit IID
])
```

**100 meters:**
```
Tag(9, [
  100,                         // plain integer
  bytes(<cg.unit:m>)           // meter unit IID
])
```

---

## Tag 8: CG-SIG (Signed Envelope)

A signed payload with attached signature metadata.

### Encoding

```
Tag 8: [<body>, <signature-block>]
```

A 2-element CBOR array:

1. **body**: The signed content (any CBOR value)
2. **signature-block**: Signature metadata (see below)

### Signature Block Structure

```cbor
{
  "alg": <cose-algorithm-id>,  // e.g., -8 for EdDSA
  "kid": bytes(<key-id>),      // Signer's key ID
  "sig": bytes(<signature>),   // Raw signature bytes
  "ts":  <epoch-millis>,       // Claimed signing time (optional)
  "aad": bytes(<extra-data>)   // Additional authenticated data (optional)
}
```

### Notes

- The body is signed in its canonical CBOR encoding
- Algorithm IDs are COSE integers (e.g., -8 for ED25519)
- Verification requires the signer's public key (resolved via `kid`)
- Nested signatures are possible (sign a CG-SIG to countersign)

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

## Operand Types

An **Operand** is any value that can appear as a relation object, qualifier value, or expression result. There are two kinds:

### Item Reference (encodes as CG-REF, Tag 6)

```
ItemRef {
    iid: ItemID                 # Required: target item
    vid: VersionID?             # Optional: specific version
    component: HandleID?        # Optional: specific component
    selector: string?           # Optional: fragment within component
}
```

### Typed Value (encodes as CG-VALUE, Tag 7, or shorthand)

```
TypedValue {
    valueType: ItemID           # Type definition (sememe)
    payload: any                # Encoded value
}
```

Common values use [shorthand conventions](#shorthand-conventions) (bare CBOR primitives) instead of explicit Tag 7 wrapping. Tag 7 is only needed when the type cannot be inferred from the CBOR primitive.

---

## Comparison with DAG-CBOR

| Aspect | DAG-CBOR (IPLD) | CG-CBOR |
|--------|-----------------|---------|
| Custom tags | Only tag 42 (CID) | Tags 6-9 (ref, value, sig, qty) |
| Floats | Forbidden | Forbidden (use Rational/Decimal) |
| Links | CID only | ItemRef with optional path |
| Typed values | Implicit | Shorthand + explicit CG-VALUE |
| Exact numerics | Integers only | Integers, Rational, Decimal, Quantity |
| Philosophy | Universal interchange | Graph-native semantics |

CG-CBOR shares DAG-CBOR's commitment to deterministic encoding but adds richer graph-native semantics.

---

## References

**External resources:**
- [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) — Current CBOR specification
- [CBOR Tags Registry](https://www.iana.org/assignments/cbor-tags/cbor-tags.xhtml) — IANA tag allocations
- [IPLD DAG-CBOR Spec](https://ipld.io/specs/codecs/dag-cbor/spec/) — Content-addressed CBOR (IPLD)
- [COSE Algorithms (RFC 9053)](https://www.rfc-editor.org/rfc/rfc9053.html) — Algorithm identifiers for signatures

**Academic foundations:**
- [Bormann, Hoffman 2013 — CBOR RFC 7049](references/Bormann%2C%20Hoffman%202013%20-%20CBOR%20Concise%20Binary%20Object%20Representation%20RFC%207049.txt) — The original CBOR specification that CG-CBOR extends
