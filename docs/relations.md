# Relations

**Relations** are signed semantic assertions about Items. They form the queryable graph that connects everything in Common Graph. The triple structure (Subject -> Predicate -> Object) follows the RDF data model from the Semantic Web (see [references/Berners-Lee et al 2001](references/Berners-Lee%2C%20Hendler%2C%20Lassila%202001%20-%20The%20Semantic%20Web.pdf)), but with key differences: predicates are sememes (not URIs), identity is content-addressed (not location-based), and every assertion is cryptographically signed.

## Relation Structure

A relation is a triple (plus optional qualifiers):

```
Subject (IID) → Predicate (Sememe) → Object (Item or Literal)
                                   + Qualifiers (optional)
```

| Field | Type | Description |
|-------|------|-------------|
| `subject` | ItemID | The item this relation is about |
| `predicate` | ItemID | A sememe defining the relationship meaning |
| `object` | ItemRef or Literal | Target item or literal value |
| `qualifiers` | Map | Optional key-value pairs adding context |
| `createdAt` | Instant | When this relation was created |

## Relation ID (RID)

The **RID** is computed from a canonical encoding of the relation's identity-bearing parts:

```
RID = Hash( CBOR_canonical([
    predicateIID,
    objectCanonical,       # Item reference or literal encoding
    qualsIdentityCanonical # Only identity-bearing qualifiers
]) )
```

This means:
- Same semantic content = same RID (deduplication)
- Non-identity qualifiers (timestamps, confidence) don't affect RID
- Relations can be verified and compared by hash

## Object Types

The object of a relation can be:

### Item Reference

Points to another Item by IID. Optionally pinned to a specific version:

```
book:Hobbit → author → person:Tolkien
game:Chess123 → hasPlayer → user:Alice
```

### Literal Value

A typed scalar value:

```
book:Hobbit → title → "The Hobbit"
sensor:temp01 → reading → 72.5°F
event:launch → date → 2024-01-15T10:30:00Z
```

## Literal Types

Literals are typed scalar values wrapped with their type:

```
Literal {
    valueType: ItemID       # Points to scalar type definition
    payload: bytes          # Canonical encoding of value
}
```

| Type | Example | Encoding |
|------|---------|----------|
| Text | `"Hello"` | UTF-8 bytes |
| Boolean | `true` | Single byte |
| Integer | `42` | CBOR integer |
| Decimal | `3.14159` | Tag 4 `[mantissa, exponent]` |
| Instant | `2024-01-15T10:30:00Z` | Epoch milliseconds |
| IP Address | `192.168.1.1` | 4 or 16 raw bytes |
| Quantity | `12 mm` | Tag 9 `[magnitude, unit-iid]` |

See [CG-CBOR](cg-cbor.md) for encoding details.

## Qualifiers

Qualifiers add context to a relation without changing its core identity:

```
photo:vacation → depicts → person:Alice
    { confidence: 0.95, region: "face:120,80,200,200" }
```

**Identity-bearing qualifiers** affect the RID — they're part of "what" is being asserted.

**Non-identity qualifiers** don't affect the RID — they carry metadata like timestamps, provenance, or confidence scores.

## Signing Relations

Relations are signed independently of the Items they describe:

```
Relation.sign(signer) → Signed Relation {
    body: [subject, predicate, object, qualifiers, createdAt]
    signature: Sig {
        algorithm: Ed25519
        keyId: signer's key hash
        timestamp: signing time
        signature: bytes
    }
}
```

This means:
- Anyone can assert a relation about any item
- The signer's identity and authority are verifiable
- Trust policies determine which assertions you believe (see [Trust](trust.md))

## Predicates Are Sememes

Every predicate is a **sememe** — an Item that anchors a specific meaning:

```
# Don't:
predicate: "title"          # Ambiguous string — which "title"?

# Do:
predicate: TITLE.iid        # Reference to the "title" sememe
```

This ensures:
- Consistent meaning across languages and communities
- Queryable by concept, not string
- Type constraints (domain/range validation)

See [Sememes](sememes.md) for the full semantic backbone.

## Examples

**Metadata:**
```
book:Hobbit → title → "The Hobbit"
book:Hobbit → author → person:Tolkien
book:Hobbit → published → 1937-09-21
```

**Trust:**
```
user:Alice → trustsFor → user:Bob { scope: "code-review", level: "full" }
user:Alice → disavows → key:compromised123
```

**Social:**
```
post:123 → respondingTo → post:122 { type: "agreement" }
user:Alice → follows → user:Bob
```

**Network (created automatically by the CG Protocol — see [Network Architecture](network.md)):**
```
librarian:A → peersWith → librarian:B
librarian:B → reachableAt → Endpoint("cg", 192.168.1.1, 7432)
```

Network relations double as the routing layer — predicates like `peersWith` and `reachableAt` are indexed, so querying them gives you the peer list and topology. See [Network: Predicates ARE Indexes](network.md#predicates-are-indexes).

**Comments go THROUGH sememes** — you don't just "comment", you specify how your comment relates:
```
comment:456 → about → doc:789 { sentiment: "question" }
comment:457 → about → doc:789 { sentiment: "clarification" }
```

## Indexing

Relations are indexed for fast queries:

- **By subject** — "All relations about this item"
- **By predicate** — "All 'author' relations"
- **By object** — "All items pointing to this person"
- **Combined** — "All 'author' relations where object is person:Tolkien"

Indexes use composite binary keys with fixed-width hash fields and separator bytes, enabling efficient prefix scans and range queries. See [Library](library.md) for the storage architecture.

Indexes are derived and rebuildable — canonical truth lives in the signed relations themselves.

## References

**Academic foundations:**
- [Berners-Lee et al 2001 — The Semantic Web](references/Berners-Lee%2C%20Hendler%2C%20Lassila%202001%20-%20The%20Semantic%20Web.pdf) — The original vision for machine-readable meaning on the web
- [Bizer et al 2009 — Linked Data](references/Bizer%2C%20Heath%2C%20Berners-Lee%202009%20-%20Linked%20Data%20The%20Story%20So%20Far.pdf) — Linked data principles and practice
- [Hogan et al 2021 — Knowledge Graphs](references/Hogan%20et%20al%202021%20-%20Knowledge%20Graphs.pdf) — Comprehensive survey of graph data models, query languages, and knowledge extraction
- [Gruber 1993 — Ontology Design](references/Gruber%201993%20-%20Toward%20Principles%20for%20the%20Design%20of%20Ontologies.pdf) — "An ontology is an explicit specification of a conceptualization"
