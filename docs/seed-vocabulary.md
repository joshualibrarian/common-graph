# Seed Vocabulary

Common Graph bootstraps with a vocabulary of well-known sememes — operators, thematic roles, languages, base archetypes, primitive value types, structural sememes. These need to exist in every librarian without explicit creation, and every librarian needs to agree on which item is which sememe. The **seed vocabulary** is the mechanism that makes this work without any central registration.

The trick is deterministic IIDs derived from canonical-key strings. Two independently developed librarians, given the same canonical key, hash it to the same IID. Agreement is mathematical, not negotiated. The seed vocabulary is what those agreed-upon IIDs point at when the system starts up.

This document defines the canonical-key pattern, the structure of seed manifests, the two layers of seed vocabulary (foundation and domain), and the application-bundle model for distributing domain vocabulary as signed packages.

This document assumes familiarity with [sememes](sememes.md), [items](item.md), [vocabulary](vocabulary.md), and [the reference scheme](ref-scheme.md).

## Canonical-key IIDs

A bootstrap sememe's IID is computed as:

```
IID = multihash(SHA-256, "<canonical-key>")
```

Where the canonical key is a stable, well-known string like:

```
"cg.predicate:add"           → the Add operator
"cg.role:theme"              → the THEME thematic role
"cg.lang:eng"                → the English Language item
"cg.archetype:item"          → the Item meta-archetype
"cg.archetype:value"         → the Value meta-archetype
"cg.qualifier:required"      → the Required qualifier
"cg.runtime:python"          → the Python runtime sememe
"cg.unit:meter"              → the meter unit
"cg.feature:lemma"           → the lemma grammatical feature
```

Independent librarians compute the same IID by hashing the same string. No coordination, no registry, no negotiation. The canonical-key strings are themselves shared by convention — published in seed vocabulary specifications, hashed independently by each librarian — but the agreement on IIDs is mathematical once the strings are agreed.

This is what makes the seed vocabulary work across independently developed implementations. A Java implementation, a Python implementation, and a Lisp implementation all converge on the same IIDs for the same concepts because they all hash the same canonical keys.

## The seed pattern

The bootstrap vocabulary is published at startup as a set of **seed items**. Each seed has:

- **A canonical key** — the string that derives its IID.
- **A manifest body** — the seed's manifest, with head, ITEM_ID (the canonical-key-derived IID), schema bindings, lexemes, glosses.
- **Endorsed frames** — gloss frames in various languages, lexeme frames, IMPLEMENTS frames if the sememe is a code item.

Seeds are published the moment a librarian starts. The bootstrap process scans the local declarations (in the Java implementation: classpath scanning for `@Seed`-annotated classes), computes each seed's IID, builds its manifest body, persists it. Idempotent: the same canonical keys produce the same IIDs and the same manifest bodies; re-running bootstrap is a no-op.

Seed manifests are typically **unsigned** — they're not assertions by any specific signer; they're protocol-given. A librarian trusts them by virtue of having computed them locally from the canonical keys. Other librarians that trust the same canonical-key convention compute the same seeds; their seeds and yours align by construction.

For sememes that come from outside the foundation — application-specific vocabularies, third-party domain knowledge — the seeds are **signed** by their author. Trust in those seeds is trust in the signer.

## Two layers of seed vocabulary

Common Graph has both a **foundation layer** and a **domain layer**.

### The foundation layer

The foundation is small (~100–200 sememes) and stable. It contains:

- **Meta-archetypes** — Archetype, Item, Predicate, Value, Language, Code.
- **Structural sememes** — ITEM_ID, FOLLOWS, ENDORSES, HANDLES, IMPLEMENTS, CONFIG.
- **Thematic roles** — AGENT, THEME, GOAL, SOURCE, INSTRUMENT, RECIPIENT, TIME, LOCATION, MANNER, CAUSE, PARTNER, VALUE, NAME, ATTRIBUTE, …
- **Universal qualifiers** — Required, Lemma, Plural, Past-tense, Verb, Noun, Adjective, Preposition, etc.
- **Operators** — Add, Subtract, Multiply, Divide, Equal, LessThan, And, Or, Not, …
- **Value archetypes** — Color, Quantity, Length, Mass, Time, Temperature, …
- **Runtime sememes** — Java, Python, Lisp, JavaScript, ClassName, SourceCode.
- **Base language items** — English, Spanish, German, Japanese, Mandarin, with their canonical-key strings derived from ISO 639-3 codes.
- **Primitive units** — meter, kilogram, second, ampere, kelvin, mole, candela.

The foundation is what every Common Graph implementation ships with. It's necessary for the system to do anything — without operators, you can't compute; without thematic roles, you can't write frames; without language items, you can't resolve text.

### The domain layer

The domain layer is unbounded and grows by community curation. Examples:

- **WordNet** — ~120,000 English concept sememes imported as seeds. Each WordNet synset gets a canonical key like `"cg.wn:n#00012345"` (POS prefix plus synset offset). Languages with their own wordnets (Polish, French, Mandarin) follow the same pattern, with CILI linking shared concepts.
- **VerbNet** — verb classes and frame predicates with their thematic-role declarations.
- **Chess vocabulary** — pieces, squares, move types, openings, endgame positions.
- **Medical vocabulary** — diagnoses, treatments, anatomy, pharmacological agents. SNOMED CT is the obvious import target.
- **Legal vocabulary** — jurisdictions, statutes, contract types, case law citations.
- **Financial vocabulary** — currencies, instruments, market identifiers, accounting concepts.
- **Geographic vocabulary** — countries, regions, cities. GeoNames is the obvious import target.

Domain vocabularies aren't shipped with the librarian by default; they're loaded as needed. A chess application includes the chess vocabulary; a medical record system includes the medical vocabulary. The librarian doesn't care which domains are loaded — they're all just additional seeds with their own canonical keys.

## Application bundles

An **application bundle** is the unit of vocabulary distribution. A bundle is:

- **An application archetype item** — the application's identity.
- **A set of seed items** — the application's vocabulary (predicates, sub-archetypes, qualifiers, named entities).
- **Code items** — the implementations of the application's archetypes (Java classes, Python modules, etc.).
- **Bundle metadata** — version, author, dependencies, signature.

The bundle is itself a manifest body (of head `@application`), signed by its author. Installing a bundle is verifying the author's signature, fetching the bundle's seed items, and registering them as scoped postings.

Trust in a bundle is trust in its signer. A user who trusts a chess application installs the chess bundle; the chess vocabulary becomes available. A user who doesn't trust the chess application doesn't install it; the vocabulary stays unavailable.

Application bundles compose. A bundle can declare dependencies on other bundles: a chess-variant application depends on a base chess bundle. The dependent bundle's vocabulary is in scope when the dependent application is active.

## Reuse over invention

The strong norm in Common Graph vocabulary work is **reuse over invention**. Building a chess application means using WordNet's existing sememes for "chess," "game," "move," "piece" rather than inventing fresh ones. A new sememe is introduced only when the existing vocabulary doesn't cover the concept.

This reduces duplication, improves cross-application interop, and respects the empirical work that built the existing vocabularies. Two chess applications that both use WordNet's chess sememes interoperate naturally; their frames refer to the same concepts.

When a domain-specific sememe is genuinely new — a novel chess variant's piece type, a domain-specific qualifier, a measurement unit not in the standard inventory — the application bundle mints it under the bundle's own namespace. Conventions: `"cg.<author>.<domain>:<key>"` to keep canonical keys collision-free across third-party bundles.

## The Wittgensteinian framing

WordNet, CILI, VerbNet, ISO 24617-4 are *trust-weighted starting points*. They're chosen because they're the best empirical work available, freely licensed, and widely respected — but they're not architecturally privileged. A community building Common Graph from a different ontology can seed its own vocabulary.

The protocol doesn't favor any source. The default seed is provided because most users won't want to rebuild the linguistic backbone, but it's not architecturally privileged. What propagates and what doesn't is determined by which sememes get referenced in actual frames — meaning emerges from use, not from decree.

A radically different seed vocabulary is permitted. It might use a different upper ontology, anchor different concepts, organize hierarchy differently. As long as its sememes carry canonical keys and derive IIDs the same way, they participate in the same protocol. Common Graph is neutral about which vocabularies thrive.

## Worked examples

**A foundation sememe — Add.**

```
@add's seed:
  canonical key: "cg.predicate:add"
  IID: multihash(SHA-256, "cg.predicate:add")

@add's manifest body:
  {@predicate, [
    @ITEM_ID → <add-iid>,
    !THEME → ?numeric,
    !THEME → ?numeric,
    @ENDORSES → #<en-add-gloss>,
    @ENDORSES → #<en-add-lexeme>,   ; "add"
    @ENDORSES → #<en-plus-symbol>,  ; "+"
    @ENDORSES → #<es-sumar-lexeme>, ; "sumar"
    @IMPLEMENTS → @add              ; self-handling (rare)
  ]}
```

Both schema slots use the same THEME role (Add is commutative); the predicate self-handles. Multiple English surface forms ("add" the verb, "+" the operator) point at the same sememe via different lexeme frames.

**A WordNet synset as a domain seed.**

```
Synset "chess.n.01" (WordNet's noun-1 for chess):
  canonical key: "cg.wn:n#00466111"   ; POS + synset offset
  IID: multihash(SHA-256, "cg.wn:n#00466111")

Manifest body:
  {@archetype, [
    @ITEM_ID → <chess-iid>,
    @ENDORSES → #<en-chess-gloss>,
    @ENDORSES → #<en-chess-lexeme>,   ; "chess"
    @ENDORSES → #<de-schach-lexeme>,  ; "Schach"
    @ENDORSES → #<ja-shogi-lexeme>,   ; "象棋"
    ; further endorsements for hyponymy / hypernymy / antonymy
  ]}
```

The synset is imported as a seed; its IID is derived from a canonical key (POS + WordNet's stable offset). Lexemes in many languages are endorsed via CILI alignment.

**A chess application bundle.**

```
@chess-application's manifest:
  head: @application
  bindings:
    @ITEM_ID → <chess-app-iid>
    @NAME → "Chess"
    @AUTHOR → @chess-application-developer
    @VERSION → "1.0.0"
    @DEPENDS-ON → @wordnet-bundle
    @ENDORSES → #<chess-game-archetype-seed>
    @ENDORSES → #<chess-move-predicate-seed>
    @ENDORSES → #<chess-piece-archetype-seed>
    ; ... more endorsed seeds
```

The bundle item collects all its seed items by endorsement. Installing means fetching the seeds, verifying the bundle's signature, registering the seeds.

## Relations

- [`sememes.md`](sememes.md) — what the seed vocabulary populates.
- [`vocabulary.md`](vocabulary.md) — the runtime layer the seeds feed into.
- [`item.md`](item.md) — items and the bootstrap process.
- [`manifest.md`](manifest.md) — seed manifests' structure.
- [`api.md`](api.md) — HANDLES and IMPLEMENTS, the API surface seed items declare.
- [`language.md`](language.md) — language items and their lexeme imports.
