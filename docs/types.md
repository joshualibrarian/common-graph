# Types

The type system in Common Graph rests on one structural primitive — the **datum** — and a small set of meta-archetypes that establish the universal rules. This document defines those rules.

## Datum: the structural primitive

Everything in the system is **datum-shaped**:

```
datum   = head + bindings
binding = head + qualifiers + target
```

The **head** is a reference to whatever defines the datum's shape: an archetype (for manifest bodies) or a predicate (for frame bodies).

A **binding's head** is just a sememe. By convention, frame body bindings use thematic roles (THEME, AGENT, GOAL, TOPIC, ...) — semantic functions the value plays in the assertion. Manifest body bindings use whatever sememe categorizes the binding (IMAGE, RUNTIME, ITEM_ID, ENDORSEMENT, CONTENT, ...). Functionally, manifest binding heads are closer to qualifiers in spirit — they categorize what kind of content the binding holds — than to predicates, which assert relations. Both encodings are identical; the convention differs by context.

**Qualifiers** narrow or constrain the binding. Sememes (ENGLISH, JPEG, FULL, REQUIRED) or literal values (math variable names, developer identifiers).

**Targets** are either:
- **References** to items, bodies, or other content, encoded with a prefix and optionally pinned: `@iid` for items (with optional `\vid` and `\vid\cid` pins), `#cid` for content/bodies.
- **Literals** — bytes, strings, numbers, raw ItemIDs, etc., inlined directly.

## Two specializations of datum

- **Manifest body** — the body of an item. Head points at the item's archetype; bindings carry the item's identity, constitutive content, and endorsements (all uniformly bindings).
- **Frame body** — the body of a frame. Head points at the frame's predicate; bindings carry the role-keyed assertions the predicate declared via EXPECTS.

Both share the same encoding shape. They differ only in semantic role: manifest bindings carry an item's substance; frame bindings carry an assertion's content.

## Three functional categories of sememe

Sememes fall into three disjoint categories, distinguished by the shape of their EXPECTS:

### Archetypes

Have EXPECTS that govern *instances*. Instantiable.

Examples: `Photograph`, `Chess`, `Code`, `Language`, `ThematicRole`, `Runtime`, `Item`.

EXPECTS on an archetype declares what the manifest body of an instance must contain — required/permitted bindings and required/permitted endorsed frames.

### Predicates

Have role-keyed EXPECTS that govern *frame bindings*. Used as the head of frame bodies. Their instances are frames, not items.

Examples: `AUTHORED`, `TITLE`, `MOVE`, `IMPLEMENTATION`, `EXPECTS` itself.

EXPECTS on a predicate declares what role-keyed bindings frames-with-this-predicate-as-head must carry.

### Pure values

No EXPECTS. Referenceable concepts that exist to be named in bindings.

Examples: `English` (instance of `Language`), `AGENT` (instance of `ThematicRole`), `cg.runtime:java` (instance of `Runtime`), `narrow-wins` (instance of `CascadeRule`), `cg.platform:linux-x86_64` (instance of `Platform`).

Every pure value is itself an instance of some archetype. The archetype defines the structural shape; the value is one specific filling.

The three categories are **disjoint**. No sememe is both predicate and archetype. The functional shape — what its EXPECTS looks like — determines its category.

## The meta-hierarchy

Two self-typing closures bootstrap the universe.

```
Archetype  (head → @Archetype, self-typed via IID-only reference)
│
├── Item            (the blank archetype, no further EXPECTS)
├── Predicate       (head → @Archetype; the archetype that types predicates)
├── Photograph      (head → @Archetype)
├── Code            (head → @Archetype)
├── Language        (head → @Archetype)
├── ThematicRole    (head → @Archetype)
├── Runtime         (head → @Archetype)
└── ...

Predicates (head → @Predicate)
├── AUTHORED
├── TITLE
├── IMPLEMENTATION
├── EXPECTS
└── ...

Pure values (head → some specific archetype)
├── English         (head → @Language)
├── AGENT           (head → @ThematicRole)
├── my-cat-photo    (head → @Photograph)
└── ...
```

### Archetype

The singular root. Its manifest head references its own IID (`@cg.archetype:archetype`, IID-only — pinning to its own version or content would create a hash paradox).

Archetype is the **bootstrap exception**: it does not carry an ITEM_ID binding on its own manifest. Every other item does.

Archetype declares exactly one universal EXPECTS:

```
EXPECTS { TOPIC[ROLE] → @ITEM_ID }
```

This rule propagates down to every descendant. Every item below Archetype carries an ITEM_ID binding on its manifest. **This defines item-hood**: an item is anything whose head chain terminates at Archetype, and which carries an ITEM_ID binding by Archetype's universal rule.

### Predicate

An instance of Archetype, with head → @Archetype. Predicate is itself an archetype — it types items that are predicates. It declares one additional EXPECTS:

```
EXPECTS { TOPIC[FRAME] → @EXPECTS }
```

Predicate items endorse EXPECTS frames declaring what frames using them as head must carry. AUTHORED, TITLE, MOVE — each is an instance of Predicate, each carries its own role-keyed EXPECTS frames declaring its frame-instances' shape.

### Item

An instance of Archetype, with head → @Archetype. The blank archetype — no EXPECTS beyond what Archetype gives it. Useful for minting items with no archetype-specific constraints.

## EXPECTS

EXPECTS is the predicate used to declare structural expectations. Its frames are endorsed by archetype and predicate manifests.

Each EXPECTS frame body carries a single binding:

```
EXPECTS { TOPIC[ROLE]  → @<expected-head> }     // expects a binding with this head
EXPECTS { TOPIC[FRAME] → @<expected-predicate> } // expects an endorsed frame using this predicate
```

- **TOPIC** — EXPECTS's own thematic role for "the thing being talked about" (the role/predicate being expected).
- **[ROLE]** qualifier — the expectation is about a binding. Its location depends on where the EXPECTS lives: on an archetype's manifest, the binding is expected on instances' manifest bodies; on a predicate's manifest, the binding is expected on frames using that predicate as head.
- **[FRAME]** qualifier — the expectation is about an endorsed frame. Used by archetypes to declare that instances should/must endorse a frame with the named predicate.
- **Target** — the role or predicate being expected.

Optional additional qualifiers extend the expectation: `[ROLE, REQUIRED]` for required slots, `[FRAME, REQUIRED]` for required frames, etc. Without REQUIRED the slot is permissible but optional.

Examples:

```
// Move (predicate) — frames using MOVE carry THEME and GOAL bindings
@cg.verb:move endorses:
  EXPECTS { TOPIC[ROLE, REQUIRED] → @THEME }
  EXPECTS { TOPIC[ROLE, REQUIRED] → @GOAL  }

// Photograph (archetype) — instances need IMAGE manifest binding(s);
// may endorse DEPICTS, AUTHORED, CAPTURED frames
@cg.archetype:photograph endorses:
  EXPECTS { TOPIC[ROLE, REQUIRED] → @IMAGE    }
  EXPECTS { TOPIC[FRAME]          → @DEPICTS  }
  EXPECTS { TOPIC[FRAME]          → @AUTHORED }
  EXPECTS { TOPIC[FRAME]          → @CAPTURED }

// Archetype (the meta-root)
@cg.archetype:archetype endorses:
  EXPECTS { TOPIC[ROLE, REQUIRED] → @ITEM_ID }

// Predicate (the meta of predicates)
@cg.archetype:predicate endorses:
  EXPECTS { TOPIC[FRAME, REQUIRED] → @EXPECTS }
```

EXPECTS is itself a predicate — it has its own EXPECTS frames declaring its frame-bindings' shape. The frames it produces look like the frames it describes. Reflexively defined.

## Self-application closes

Two reflexive closures bottom out the meta-recursion:

- **Archetype heads at itself.** The fixpoint at the top. IID-only self-reference; the canonical-key-derived IID is computable without hashing the manifest body, so no paradox.
- **EXPECTS expects EXPECTS.** EXPECTS-the-predicate has its own EXPECTS frames declaring its frame-bindings' shape. Self-applicative.

After these two closures, everything is regular instance-and-frame machinery. No infinite tower; no special cases beyond the two roots.

## Identity

Item identity (IID) is established at item creation, by mechanism appropriate to the kind of item:

- **Seed items** — IID derived deterministically from canonical key (e.g., `cg.archetype:photograph`)
- **Signers** — IID derived from initial signing public key (closes the preemption gap)
- **Other items** — IID generated at creation (random or otherwise stable)

Every item below Archetype carries its ITEM_ID as a manifest binding:

```
ITEM_ID: <raw IID bytes>
```

The ITEM_ID binding's target is **raw IID bytes — not a `@iid` reference**. References can be version-or-content-pinned (`@iid\vid`, `@iid\vid\cid`); declaring "this is my identity" is not a reference, it's a literal. The IID is just an identifier; there is no version-of-an-IID to pin.

References to items elsewhere — heads of instances pointing at their archetype, frame body bindings naming target items — use the `@` reference form, optionally pinned.

Frames do not have IIDs. A frame's identity is its body hash: content-addressed, not item-addressed.

Archetype is the only item without an ITEM_ID binding — the bootstrap exception that grounds the rule for everything below.

## Three placements for binding-shaped content

Binding-shaped content can live in three places, distinguished by what role they play and who authors them:

### 1. Manifest binding — the item's substance

Constitutive content + intrinsic structural metadata. The bytes/data/identity that *make the item what it is*. Single-authored per version (the manifest's signer).

Examples:
- `ITEM_ID: <raw bytes>` — the item's identity
- `IMAGE [JPEG, FULL]: #cid` on a Photograph — the photo IS this image data
- `RUNTIME: @cg.runtime:java`, `ENTRY: "..."`, `CONTENT [platform=...]: #cid` on a Code Item — the code IS these bytes interpreted this way
- `VIDEO [MASTER, UHD-HDR]: #cid` on a Movie — the movie IS this video data

Manifest bindings carry "what this item *is*."

### 2. Endorsed frame — the owner's official narrative

A binding on the manifest with `ENDORSEMENT` as its head and a frame body CID as its target. Each endorsement adds an endorsed frame to the item's official content. The manifest signature covers them transitively (the endorsement targets are part of what the manifest hashes).

```
ENDORSEMENT: #depicts-body-cid
ENDORSEMENT: #authored-body-cid
ENDORSEMENT: #captured-body-cid
```

Endorsed frames carry assertions the item owner stands behind — relational claims connecting the item to other items, attributes, or values, signed by virtue of being endorsed.

Qualifiers on ENDORSEMENT bindings can categorize endorsements (`ENDORSEMENT [identity]`, `ENDORSEMENT [decoration]`, etc.) when the librarian needs to process them differently. *(Open: the affordance is there; specific categories not yet locked.)*

### 3. Unendorsed frame — anyone's claim or derivative

Frames signed independently by anyone, NOT endorsed by the item's manifest. Each carries its own signed record. Reference the item via bindings without churning the item's VID. Two common cases:

**Third-party assertions** — likes, comments, reviews, trust attestations, annotations. Anyone signs their own claim about an item.

```
@bob's separately-signed frames (reference @my-photo via THEME):
  LIKE     { THEME → @my-photo, AGENT → @bob }
  COMMENT  { THEME → @my-photo, AGENT → @bob, VALUE → "lovely shot!" }
```

**Derivative content** — transcodes, alternative encodings, computed variants, cached transformations. Whoever produces the derivative signs the frame.

```
@some-peer's separately-signed frame:
  TRANSCODE { THEME → @some-movie, FORMAT → @1080p-h265, VALUE → #cid-1080p }
```

Both kinds are unendorsed frames structurally. Both subject to local policy: cache, expire, GC. Indexed via FRAME_BY_ITEM against any items they reference.

### The line

- **Is it the item's substance?** → Manifest binding
- **Is it the owner's official claim about the item?** → Endorsed frame (added via ENDORSEMENT manifest binding)
- **Is it someone (anyone) saying or making something tied to the item?** → Unendorsed frame

The criterion is *content-vs-assertion*. Content is what the item *is*; assertions *relate* the item to other things. Ownership is a separate axis: official narrative (endorsed by the manifest signer) vs. anyone-can-say (independently signed).

## Worked examples

### Photograph

```
@my-photo's manifest body:
  head:                    @cg.archetype:photograph
  ITEM_ID:                 <raw IID bytes>
  IMAGE [JPEG, FULL]:      #cid-full
  IMAGE [JPEG, THUMBNAIL]: #cid-thumb
  ENDORSEMENT:             #depicts-body-cid
  ENDORSEMENT:             #authored-body-cid
  ENDORSEMENT:             #captured-body-cid

Endorsed frames (referenced by ENDORSEMENT bindings, looked up by hash):
  #depicts-body-cid:  DEPICTS  { THEME → @my-photo, AGENT → @alice }
  #authored-body-cid: AUTHORED { THEME → @my-photo, AGENT → @me }
  #captured-body-cid: CAPTURED { THEME → @my-photo, LOCATION → @park,
                                                    TIME → 2026-05-07T17:30 }

Unendorsed frames in the wild (indexed against @my-photo):
  LIKE      { THEME → @my-photo, AGENT → @bob }                    [signed by Bob]
  COMMENT   { THEME → @my-photo, AGENT → @bob,
              VALUE → "lovely shot!" }                              [signed by Bob]
  TRANSCODE { THEME → @my-photo, FORMAT → @webp-512,
              VALUE → #webp-cid }                                   [signed by some peer]
```

### Code Item

```
@add-code's manifest body:
  head:        @cg.archetype:code
  ITEM_ID:     <raw IID bytes>
  RUNTIME:     @cg.runtime:java
  ENTRY:       "dev.everydaythings.graph.ops.Add"
  CONTENT [platform=cg.platform:any]: #cid-bytecode
  ENDORSEMENT: #docs-body-cid

Endorsed frames:
  #docs-body-cid: DOCUMENTATION { THEME → @add-code, VALUE → "..." }
```

The IMPLEMENTATION relation tying `@cg.op:add` to `@add-code` is itself a frame, signed by whichever party is making the assertion (the implementation author signs `IMPLEMENTATION { THEME → @cg.op:add, VALUE → @add-code }`; the predicate author signs the same body if endorsing it as the default; trust matrix arbitrates).

### Movie with transcodes

```
@some-movie's manifest body:
  head:                    @cg.archetype:movie
  ITEM_ID:                 <raw IID bytes>
  VIDEO [MASTER, UHD-HDR]: #cid-canonical-50gb
  ENDORSEMENT:             #title-body-cid
  ENDORSEMENT:             #directed-body-cid
  ENDORSEMENT:             #released-body-cid

Endorsed frames:
  #title-body-cid:    TITLE    { THEME → @some-movie, VALUE → "..." }
  #directed-body-cid: DIRECTED { THEME → @some-movie, AGENT → @director }
  #released-body-cid: RELEASED { THEME → @some-movie, TIME → 2024-... }

Unendorsed frames (cached/indexed; produced by various peers):
  TRANSCODE { THEME → @some-movie, FORMAT → @1080p-h265, VALUE → #cid-1080p }
  TRANSCODE { THEME → @some-movie, FORMAT → @720p-mp4,   VALUE → #cid-720p }
```

The 50GB master is constitutive — the movie *is* that data. Transcodes are derivative, unendorsed, and policy-governed (cached locally as needed, expired/GC'd when not).

## Frames vs. items: summary

|                          | Items                     | Frames                |
|--------------------------|---------------------------|-----------------------|
| Structure                | Manifest body + endorsed/unendorsed frame bodies | Body + record(s) |
| Identity                 | IID (stable across versions) | Body hash (content-addressed) |
| Head points at           | Archetype                 | Predicate             |
| Has ITEM_ID binding      | Yes (except Archetype itself) | No |
| Versioned                | Yes (manifest history)    | No (immutable bodies; new content = new body) |
| ITEM_ID target form      | Raw bytes (literal)       | N/A                   |
| Head reference form      | `@archetype-iid` (optionally pinned) | `@predicate-iid` (optionally pinned) |

Frames are the only persistent shape that isn't an item. Everything else — meta-archetypes, predicates, pure values, concrete instances — is an item.
