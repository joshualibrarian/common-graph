# Semantic Assertions

**Semantic assertions** are signed statements about Items — filled frames based on Fillmore's Case Grammar (1968) and Frame Semantics (1982). A **predicate** names a frame, and **bindings** fill the frame's roles with items or literal values. Assertions are the queryable graph that connects everything in Common Graph.

In the frame model, assertions are frames like any other. Endorsed assertions are included in the item's manifest (the owner asserts them). Unendorsed assertions are attached by others and independently signed. See [Frames](frames.md) for the unified frame primitive.

Early versions of Common Graph used RDF-style triples (Subject → Predicate → Object), following the Semantic Web data model. That structure works for simple binary relationships but becomes awkward when a relation involves more than two participants, or when you need to distinguish *who did something* from *what it was done to*. Frames generalize triples: a two-role frame is isomorphic to a triple, but a frame can also express "Alice sent the book to Bob using FedEx on Tuesday" as a single, signed assertion with five roles filled.

## Assertion Structure

An assertion is a **filled frame** — a predicate with role bindings:

```
Predicate (Sememe)
    THEME  → Item or Literal
    TARGET → Item or Literal
    AGENT  → Item (optional)
    ...additional roles as needed
```

| Field | Type | Scope | Description |
|-------|------|-------|-------------|
| `predicate` | ItemID | BODY | A sememe naming the frame type |
| `bindings` | Map\<ItemID, Target\> | BODY | Role-to-target map filling the frame's slots |
| `theme` | ItemID | BODY | The item this frame is about (REQUIRED) |

**BODY** fields define the semantic content — they are hashed to produce the frame's body hash (its semantic identity).

**Endorsed assertions** inherit the manifest signature — no separate envelope. **Unendorsed assertions** carry their own signer, timestamp, and signature as RECORD fields.

## Roles

Roles are **sememes** — language-agnostic concepts referenced by ItemID. Each role defines the semantic function of a participant in the frame. The `Role` type extends `NounSememe` and provides these seed instances:

| Role | Key | Description |
|------|-----|-------------|
| `AGENT` | `cg.role:agent` | The doer or initiator of an action |
| `PATIENT` | `cg.role:patient` | The entity affected or changed |
| `THEME` | `cg.role:theme` | The content, topic, or subject matter |
| `TARGET` | `cg.role:target` | The destination or target |
| `SOURCE` | `cg.role:source` | The origin or source |
| `INSTRUMENT` | `cg.role:instrument` | The tool or means used |
| `LOCATION` | `cg.role:location` | The place where something is or happens |
| `TIME` | `cg.role:time` | The time when something happens |
| `RECIPIENT` | `cg.role:recipient` | The beneficiary or recipient |
| `CAUSE` | `cg.role:cause` | The reason or cause |
| `COMITATIVE` | `cg.role:comitative` | A companion or co-participant |
| `NAME` | `cg.role:name` | A name, label, or designation being assigned |

Most assertions use just two roles: **THEME** (what the assertion is about) and **TARGET** (what it points to). This is the frame equivalent of a triple's subject and object. Additional roles are filled when the semantics require them.

Roles are not a closed set. New roles can be added as seed vocabulary without changing any code — for example, an evidentiality role for languages that grammaticalize how knowledge was acquired.

## Binding Targets

Each role binding maps to a **Target** — either an item reference or a literal value.

### Item Reference (IidTarget)

Points to another Item by IID. Encodes as a CBOR byte string:

```
HYPERNYM { THEME: animal, TARGET: mammal }
FOLLOWS  { AGENT: user:Alice, TARGET: user:Bob }
```

### Literal Value

A typed scalar value (see [Literal Types](#literal-types) below):

```
TITLE    { THEME: book:Hobbit, NAME: "The Hobbit" }
READING  { THEME: sensor:temp01, TARGET: 72.5°F }
```

## Body Hash (Semantic Identity)

The body hash is the hash of the assertion's BODY bytes — the semantic identity:

```
BodyHash = Hash( CBOR_canonical( predicate, theme, bindings ) )
```

Because all BODY fields contribute to the hash, two structurally identical assertions produce the same body hash. This is the key design property:

- **Same assertion, different signers:** "The Hobbit is titled 'The Hobbit'" has the same body hash regardless of who asserts it. Multiple signed records for the same body hash represent independent attestations of the same fact.
- **Different assertions:** "Alice likes Post" and "Bob likes Post" have *different* body hashes because AGENT is a BODY field in the bindings.
- **Signer is not in the body:** Who *asserts* a fact is metadata about the assertion, not part of the fact itself.

## Signing Assertions

Assertions are signed independently of the Items they describe:

```java
Relation relation = Relation.builder()
    .predicate(Sememe.HYPERNYM.iid())
    .bind(Role.THEME.iid(), Relation.iid(animalIid))
    .bind(Role.TARGET.iid(), Relation.iid(mammalIid))
    .build()
    .sign(signer);
```

For endorsed assertions, the manifest signature covers all endorsed frame body hashes — no separate envelope needed. For unendorsed assertions, each carries its own signature binding the body hash to a signer key and timestamp.

This means:
- Anyone can assert a relation about any item
- The signer's identity and authority are verifiable
- Trust policies determine which assertions you believe (see [Trust](trust.md))

## Convenience API

The `Item.relate()` method creates the most common pattern — this item as THEME, another as TARGET:

```java
// "animal IS-A mammal"
animal.relate(Sememe.HYPERNYM.iid(), mammal);
// Creates: predicate=HYPERNYM, { THEME: animal, TARGET: mammal }

// With a literal target
book.relate(Sememe.TITLE.iid(), Literal.ofText("The Hobbit"));
// Creates: predicate=TITLE, { THEME: book, TARGET: "The Hobbit" }
```

If the item is a `Signer`, the assertion is automatically signed. If the item has a `Librarian`, the assertion is automatically stored.

For assertions that need more roles, use the builder directly:

```java
Relation.builder()
    .predicate(Sememe.SENT.iid())
    .bind(Role.AGENT.iid(), Relation.iid(alice.iid()))
    .bind(Role.PATIENT.iid(), Relation.iid(book.iid()))
    .bind(Role.RECIPIENT.iid(), Relation.iid(bob.iid()))
    .bind(Role.INSTRUMENT.iid(), Literal.ofText("FedEx"))
    .build()
    .sign(alice);
```

## Predicates Are Sememes

Every predicate is a **sememe** — an Item that anchors a specific meaning:

```
# Don't:
predicate: "title"          # Ambiguous string — which "title"?

# Do:
predicate: TITLE.iid()      # Reference to the "title" sememe
```

This ensures:
- Consistent meaning across languages and communities
- Queryable by concept, not string
- Type constraints (domain/range validation)

See [Sememes](sememes.md) for the full semantic backbone.

## Frames Unify Assertions, Dispatch, and Queries

Assertions, verb dispatch, and queries are the same thing — a frame filled to different degrees:

- **Fully filled + code:** A verb dispatch. The predicate names the action, the bindings are the arguments, and a `@Verb` method executes it.
- **Fully filled + no code:** An assertion. The predicate names the relationship, the bindings state the fact, and the frame is stored and signed.
- **Partially filled:** A query. The predicate constrains the search, filled roles are filters, and empty roles are the unknowns to be resolved.

This unification is why predicates, roles, and the vocabulary system are shared across all three. "Create a chess game" and "animal IS-A mammal" use the same frame machinery — one dispatches, the other asserts.

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

## Storage and Indexing

Assertion frames are stored in the unified object store alongside all other content. See [Storage Architecture](storage.md) for the full storage design.

**Endorsed assertions** are referenced by the manifest's frame table (body hash in the FrameEntry). The manifest signature is the record — no separate envelope stored.

**Unendorsed assertions** are stored as independent records in the object store, linked to items via the FRAME_BY_ITEM index. Each record is self-contained: body hash + signer + signature.

### FRAME_BY_ITEM Index

Indexes every ItemID that participates in any frame binding:

```
Key:   ItemID | Predicate | BodyHash
Value: CID (pointing to body or record in OBJECTS)
```

Because predicates are ItemIDs, querying by predicate is just a prefix scan where the ItemID happens to be a predicate:

| Query | Scan |
|-------|------|
| All frames involving item X | Prefix `[X]` |
| Frames involving X via predicate P | Prefix `[X | P]` |
| All frames with predicate P | Prefix `[P]` (P is an ItemID too) |

### RECORD_BY_BODY Index

Indexes records by body hash:

```
Key:   BodyHash | SignerKeyID
Value: CID (pointing to record in OBJECTS)
```

Enables "who attests to this assertion?" and "how many independent attestations does this fact have?"

### Index Properties

Indexes use composite binary keys with fixed-width hash fields, enabling efficient prefix scans and range queries. See [Library](library.md) for the storage architecture.

Indexes are derived and rebuildable — canonical truth lives in the signed frames themselves.

## Examples

**Taxonomy:**
```
HYPERNYM { THEME: animal, TARGET: mammal }
HYPERNYM { THEME: mammal, TARGET: vertebrate }
```

**Metadata:**
```
TITLE     { THEME: book:Hobbit, NAME: "The Hobbit" }
AUTHOR    { THEME: book:Hobbit, TARGET: person:Tolkien }
PUBLISHED { THEME: book:Hobbit, TIME: 1937-09-21 }
```

**Trust:**
```
TRUSTS_FOR { AGENT: user:Alice, TARGET: user:Bob, THEME: "code-review" }
DISAVOWS   { AGENT: user:Alice, TARGET: key:compromised123 }
```

**Social:**
```
RESPONDS_TO { THEME: post:123, TARGET: post:122 }
FOLLOWS     { AGENT: user:Alice, TARGET: user:Bob }
LIKES       { AGENT: user:Alice, TARGET: post:456 }
```

**Network (created automatically by the CG Protocol — see [Network Architecture](network.md)):**
```
PEERS_WITH   { THEME: librarian:A, TARGET: librarian:B }
REACHABLE_AT { THEME: librarian:B, TARGET: Endpoint("cg", 192.168.1.1, 7432) }
```

Network assertions double as the routing layer — predicates like PEERS_WITH and REACHABLE_AT are indexed, so querying them gives you the peer list and topology. See [Network: Predicates ARE Indexes](network.md#predicates-are-indexes).

**Rich assertions with multiple roles:**
```
SENT { AGENT: user:Alice, PATIENT: book:Hobbit, RECIPIENT: user:Bob,
       INSTRUMENT: "FedEx", TIME: 2024-01-15T10:30:00Z }
```

**Comments go THROUGH sememes** — you don't just "comment", you specify how your comment relates:
```
ABOUT { THEME: comment:456, TARGET: doc:789, CAUSE: "question" }
ABOUT { THEME: comment:457, TARGET: doc:789, CAUSE: "clarification" }
```

## References

**Frame Semantics:**
- Fillmore, Charles J. (1968) "The Case for Case" — The foundational argument for case grammar, proposing deep semantic roles underlying surface syntax
- Fillmore, Charles J. (1982) "Frame Semantics" — Extends case grammar into a theory of meaning based on conceptual frames that predicates evoke

**Knowledge representation:**
- [Berners-Lee et al 2001 — The Semantic Web](references/Berners-Lee%2C%20Hendler%2C%20Lassila%202001%20-%20The%20Semantic%20Web.pdf) — The original vision for machine-readable meaning on the web. RDF triples were the starting point for CG's assertion model
- [Bizer et al 2009 — Linked Data](references/Bizer%2C%20Heath%2C%20Berners-Lee%202009%20-%20Linked%20Data%20The%20Story%20So%20Far.pdf) — Linked data principles and practice
- [Hogan et al 2021 — Knowledge Graphs](references/Hogan%20et%20al%202021%20-%20Knowledge%20Graphs.pdf) — Comprehensive survey of graph data models, query languages, and knowledge extraction
- [Gruber 1993 — Ontology Design](references/Gruber%201993%20-%20Toward%20Principles%20for%20the%20Design%20of%20Ontologies.pdf) — "An ontology is an explicit specification of a conceptualization"
