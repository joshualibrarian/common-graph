# Common Graph

**A unified meaning-space for content, identity, and trust.**

> **Fair warning:** This is an active construction site. The architecture is real, the code runs, but everything is changing constantly. If that bothers you, check back later.

---

## The Problem

Every layer of the computing stack is semantically inert.

A filesystem sees bytes at paths. An operating system sees processes and file descriptors. HTTP sees bytes at URLs. A database sees rows or documents. None of them know what anything *means*. The entire world's information infrastructure has zero native ability to answer the most basic question about any piece of data: *what is this about?*

The consequence is everywhere, and so pervasive it's invisible. Search engines exist because the web can't describe itself — so third parties crawl billions of pages, guess at meaning from word frequency and link structure, and sell access to their guesses. Every API integration is a bespoke translation between systems that can't describe their own contents to each other. Every application reinvents its own vocabulary — one system's `author` is another's `creator`, another's `created_by`, another's `writtenBy` — and no layer of infrastructure connects them.

The key-value pair is computing's most ubiquitous pattern. But because keys are application-defined strings, they fracture the moment they leave the application that defined them. What's missing isn't a better search engine or a smarter metadata standard. What's missing is a *layer* — a base layer where meaning is structural, not decorative. Where creating data *is* creating semantic structure. Where the vocabulary is shared, grounded, and universal.

For the full argument — why retrofitting semantics onto existing layers can't work, what a semantic base layer requires, and why now — see [**The Case for a Semantic Base Layer**](docs/the-case.md).

---

## The Approach

Common Graph makes meaning structural. **Semantics are resolved at write time, not read time.** When you create or relate anything, the system resolves your intent to globally-anchored meaning *before the data is stored*. Every assertion, every relationship is grounded in **sememes**: universal units of meaning with stable identities derived from decades of computational linguistics ([WordNet](https://wordnet.princeton.edu/), [FrameNet](https://framenet.icsi.berkeley.edu/), [VerbNet](https://verbs.colorado.edu/verbnet/), [CILI](https://github.com/globalwordnet/cili)). The meaning isn't guessed later by a search engine — it's declared at the moment of creation, by the person who knows what they mean.

When you query "red shirt," you're not searching for the *words* "red" and "shirt" — you're searching for the *meaning* "a garment worn on the torso with color attribute red." Star Trek memes are a different sememe entirely. They simply don't match.

---

## How It Works

### Frames: The Single Primitive

The entire data model is built from one structure: the **semantic frame** — a structured assertion grounded in shared meaning.

A frame has a **predicate** — what kind of assertion this is — and **bindings** that fill the predicate's semantic slots. Each binding maps a **role** (the semantic function: NAME, THEME, AGENT, GOAL...) through optional **qualifiers** (narrowing constraints: a language, a format, a unit) to a **target** value. Two flags — **identity** and **index** — control whether the binding affects the body hash and whether it's indexed for queries.

A predicate declares the roles it expects — the semantic slots that must be filled to make the assertion complete. Qualifiers both **distinguish** multiple bindings of the same role and **constrain** valid inputs:

```
TITLE frame:
  NAME:[] → "The Hobbit"                              [identity]

PLAYER frame (on a chess game):
  AGENT:[] → fischer                                   [identity]
  ROLE:[]  → WHITE                                     [identity]

MOVE frame (on the same game):
  AGENT:[]  → fischer                                  [identity]
  THEME:[]  → king-pawn                                [identity]
  SOURCE:[] → e2                                       [identity]
  GOAL:[]   → e4                                       [identity]

VIDEO frame:
  NAME:[MKV, UHD] → cid:master-4k                     [identity]
  NAME:[MKV, HD]  → cid:hd-transcode                  [non-identity]
```

Every meaning in a binding is an opportunity for indexing. Query "all videos" — index lookup on the VIDEO predicate. Query "all UHD videos" — narrow with qualifiers. The structure *is* the index.

**Identity bindings control versioning.** The body hash is computed from predicate + identity bindings only. Non-identity bindings (cached transcodes, configuration, presentation) live on the frame without affecting its hash. Replace an HD transcode tomorrow — body hash unchanged.

**Four objects carry a frame through its lifecycle:**

- **FrameBody** — the semantic assertion itself. Identity bindings only. Content-addressed by hash. Immutable.
- **FrameRecord** — a signed attestation envelope. Signer, timestamp, signature, plus non-identity bindings (configuration, presentation). Points at a body by hash. Multiple records can attest the same body.
- **Endorsement** — what manifests hold. Body hash plus optional record reference.
- **Frame** — runtime container. Body, record(s), and live instance. In-memory only.

Provenance flows through FrameRecords — signed envelopes that attest a FrameBody. The same assertion can be independently attested by multiple signers, each with their own record.

See [`frames.md`](docs/frames.md) for the full model, and [**The Case**](docs/the-case.md) for the theoretical foundations.

### Items: What Frames Cohere Around

A single frame is rarely the whole story. A book is a TITLE frame, an AUTHORED frame, TEXT frames, a COVER_ART frame — all about the same thing. The thing they cohere around is an **item**: a signed, versioned collection of frame endorsements with stable cryptographic identity.

Items can represent anything: documents, people, groups, conversations, games, devices, languages, meanings themselves. Every item carries its own identity (IID), version history, and a manifest — a signed list of endorsements pointing to frames by body hash.

**Types are sememes.** The concept "Book" is a meaning in the graph — a sememe with its own IID, the same "book" that exists in WordNet. The type system and the semantic system are unified.

> *"Item" is a working name. The right word will come.*

See [`item.md`](docs/item.md) for item structure, identity, lifecycle, and composition.

### Why This Replaces Files and Folders

| Files & Folders | Frames & Items |
|---|---|
| Opaque byte stream — the OS can't interpret content | Typed frames — the system knows what everything means |
| Named by path in a tree — one location per file | Discoverable by meaning — items exist in a semantic graph, not a hierarchy |
| No built-in authorship, versioning, or integrity | Every item is signed, versioned, and content-addressed |
| Metadata is a sidecar (xattr, .DS_Store, EXIF) | Metadata IS bindings — first-class, queryable, signed, same as content |
| "Relatedness" means same folder or a hyperlink | Semantic frames: typed, signed, indexed, traversable |
| Application decides how to open it | Item carries its own vocabulary and presentation |
| Search by filename or full-text keyword | Query by meaning across the graph |

A folder is one way to group things — by containment in a hierarchy. Common Graph gives you every way: by authorship, by topic, by type, by time, by trust, by any semantic assertion anyone has made. And those groupings are themselves frames — signed, queryable, and extensible by anyone.

---

## Semantic Discoverability

The web is a document dump with external indexing bolted on. Common Graph is a **semantic index by construction**.

Every item is typed with a sememe. Every frame has a predicate that is a sememe. Every binding has a role that is a sememe. The graph IS the index.

### Write-Time Resolution

**Meaning is resolved at the moment of creation.** When you create a frame — whether by typing "move pawn to e4," clicking a button, or calling an API — the system resolves every concept to a globally-anchored sememe *before storage*. "Move" resolves to the MOVE sememe. "Pawn" resolves to the chess piece item. "To" maps to the GOAL thematic role. "E4" resolves to a board position. What gets stored is a structure of semantic references: `MOVE { THEME:[] → pawn, GOAL:[] → e4 }`.

The person creating the data does the disambiguation, because they know what they mean. This is trivial at write time — you know you meant chess, not a political metaphor. It's nearly impossible at read time. This is why Common Graph doesn't need a search engine, a crawler, or a ranking algorithm.

### Sememes

**Sememes are universal meaning units** — language-agnostic items that anchor meaning globally. Grounded in [WordNet](https://wordnet.princeton.edu/) (~120,000 synsets) and cross-linked via [CILI](https://github.com/globalwordnet/cili) (Collaborative Interlingual Index), each sememe has:

- A **stable cryptographic IID** — deterministic from a canonical key, identical on every node
- **Symbols** for language-neutral notation ("+", "m", "kg", "USD")
- For predicates: **declared roles** (EXPECTS) defining what bindings their frames require
- **Glosses** per language (each a frame)

Words belong to **languages**. Each language is itself an item, and its **lexemes** — the words that express sememes — are frames on that item, carrying their own grammatical features: part of speech, inflection, and morphology. "Create" (English verb), "crear" (Spanish verb), and "erstellen" (German verb) are all lexemes pointing at the same sememe. A sememe's IID stays stable forever — words in any language can be added, changed, or removed without touching it.

There are no reserved words. No escape characters. Disambiguation happens through more language — the same way humans do it.

**Predicates ARE indexes.** When you assert `AUTHORED { THEME:[] → TheHobbit, AGENT:[] → Tolkien }`, the frame is indexed on TheHobbit (by AUTHORED predicate) and on Tolkien (by AGENT role). Querying "what did Tolkien author?" is a prefix scan — no full-text search, no crawling, no ranking algorithm.

**Discovery fans out through the social graph.** Your librarian answers queries from its local store first. If it doesn't have the answer, it asks peers. Peers ask their peers. Trust metrics control propagation depth. Global discoverability without a global index.

---

## What You Can Do

**Find things by meaning, not keywords.**

- *"All red shirts for sale within 50km"* — resolves SHIRT (garment sememe) + RED (color sememe) + FOR_SALE (commercial predicate) + spatial constraint. Star Trek references have a different sememe. They don't appear.
- *"Papers that cite this paper"* — CITES is a predicate. Every citation is a signed frame. The graph IS the citation index.
- *"Everything Tolkien authored"* — AUTHORED is a predicate, Tolkien is an item. Prefix scan on the frame index.

**Publish without a platform.** Your content is a signed item on your device. Your identity is a cryptographic key, not an account.

**Trust without a moderator.** A "like" is a signed frame. A spam label is a signed frame. Everyone's trust policies produce different views of the same data — no appeals board, no opaque algorithm.

**Converse across languages.** "Create" in English, "crear" in Spanish, "erstellen" in German — same sememe, same action. The interface is semantic, not syntactic.

**Compute with real quantities.** `5m + 3ft` → `5.9144 m`. Units are sememes with dimensional metadata. Quantities are first-class values, not strings.

---

## Interaction: Language as Interface

Every item has a prompt. You type into it, and the system resolves your words into semantic structure — through resolution against the TokenDictionary, not through keyword matching or regex parsing.

```
alice@chess> move pawn to e4           # verb + noun + preposition + noun
alice@home> create document            # verb + type noun
alice@chat> send "hello" to Bob        # verb + literal + preposition + proper noun
alice@home> 5m + 3ft                   # quantity expression with unit conversion
alice@home> sqrt(144) * 2              # function + operator expression
```

The pipeline:

```
Token (any language)
  → TokenDictionary (scoped lookup: language, item, user)
    → Sememe (language-neutral meaning)
      → Language parsing (grammar-aware assembly into semantic frames)
        → Frame creation (the action IS the frame — items react to new frames)
```

Words resolve to sememes. Sememes assemble into frames. Creating a frame IS the action — items observe new frames and react accordingly. "Move pawn to e4" assembles a MOVE frame; the chess game receives it and updates its board state.

Word order is flexible because resolution is semantic, not positional. "Move pawn to e4" and "move to e4 pawn" produce the same result — prepositions bind arguments by thematic role, not by position.

**But you don't have to type.** Items declare their own visual presentation. A chess game renders a board you can click on. A document renders editable text. A chat room shows messages with a compose area. Clicking "reply" creates the same frame as typing "reply."

---

## Identity: Keys, Not Accounts

Your identity is a cryptographic key pair that lives on your device. No server needed. No account to create. No password to forget.

When a Librarian (the local runtime node) boots for the first time, it generates an Ed25519 signing key. This key is the device's identity — it can sign manifests, assert frames, and prove authorship without asking anyone's permission. The private key never leaves the device.

**Devices and people are separate identities.** Your laptop has a key. Your phone has a key. *You* are a Principal — a higher-level identity that authorizes devices by adding their public keys to your KeyLog, an append-only stream in the graph. Lose a device? Revoke its key. Your identity survives because it's not tied to any one machine.

---

## Trust: The Social Fabric

Trust isn't a security feature bolted on top — it's the organizing principle of the entire system.

Every manifest and frame is signed. Trust isn't binary — it's policy-driven with thresholds, scopes, decay, and revocation. Trust policies live on items as configuration, inspectable and adjustable.

Trust determines who you sync with, whose assertions you accept, how far your queries propagate, and whose content appears in your graph at all. There is no separate "moderation" system because trust *is* moderation.

**Reactions replace algorithms.** A "like" is a signed frame. If Alice likes a post and Bob thinks Alice's like is astroturfing, Bob signs a frame targeting Alice's frame — because a frame can be about another frame. Everyone who trusts Bob more than Alice sees that signal. Everyone who trusts Alice more than Bob ignores it. No appeals process, no review board — just overlapping trust graphs producing different views of the same data.

---

## Networking: Relationships, Not Routes

Your Librarian connects to other Librarians the way you connect to other people — explicitly, with signed attestations recorded in the graph. Network topology IS the social graph.

- **Trust drives routing.** You ask nodes you have relationships with, and they ask nodes they have relationships with.
- **Local-first by default.** All data lives on your devices. Sync is explicit, merge-based, to peers you choose.
- **The protocol is minimal.** Two message types: Request and Delivery. Everything else — discovery, replication, conflict resolution — is convention built on signed frames and content-addressed data.
- **Network topology emerges from community.** A research group's nodes cluster naturally. A family's devices find each other through shared frames.

---

## Storage: One Object Store, Four Indexes

All data lives in a single content-addressed object store: `persist(bytes) → CID`, `fetch(CID) → bytes`. Manifests, frame bodies, content blobs — all stored as objects keyed by their cryptographic hash.

Four derived indexes make the objects queryable:

| Index | Key → Value | Purpose |
|---|---|---|
| **ITEMS** | IID \| VID → timestamp | Version history per item |
| **FRAME_BY_ITEM** | ItemID \| Predicate \| BodyHash → CID | Frame lookup by participant and predicate |
| **RECORD_BY_BODY** | BodyHash \| SignerKeyID → CID | Who attested this assertion? |
| **HEADS** | Principal \| IID → VID | Current version per principal per item |

Every index is rebuildable from the object store. Indexes are projections, not sources of truth.

Three storage backends: **RocksDB** (production), **MapDB** (lightweight), **SkipList** (in-memory/testing).

---

## Presentation: One Scene, Every Surface

Items declare their presentation through **scenes** — declarative, CBOR-serializable structures built from three primitives:

- **Container** — structural: children and layout
- **Text** — content: carries sememe references, resolved to the user's language at render time
- **Body** — visual: model, image, shape, or glyph, with a fidelity chain from full 3D down to a Unicode symbol

The same scene renders as perspective 3D with physically-based lighting on a GPU, as flat 2D through Skia, or as text art in a terminal. Same items, same scene, different projections.

Text nodes carry meaning references, not hardcoded strings. A label referencing the Checkmate sememe renders as "Checkmate" in English, "将杀" in Mandarin, "Schachmatt" in German — same scene, same hash.

---

## Encoding: CG-CBOR

All data uses **CG-CBOR** — a profile of [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) with custom tags and strict deterministic encoding:

- **Self-describing tags** in the 1-byte range: item references (Tag 6), typed values (Tag 7), signed envelopes (Tag 8), quantities with units (Tag 9)
- **No IEEE 754 floats** — non-deterministic across platforms. CG-CBOR uses exact types: rationals, decimals, quantities with unit references
- **Deterministic encoding** — sorted keys, minimal integer encoding, no indefinite lengths. Identical content always produces identical bytes.

---

## Linguistic Foundation

Common Graph doesn't invent its linguistic backbone from scratch — it builds on decades of computational semantics research:

1. **[WordNet](https://wordnet.princeton.edu/)** — ~120,000 synsets (synonym sets) with definitions, hierarchical relationships. Each synset becomes a sememe.
2. **[CILI (Collaborative Interlingual Index)](https://github.com/globalwordnet/cili)** — Cross-lingual concept mapping. English "dog," Spanish "perro," Japanese "犬" map to the same concept.
3. **[FrameNet](https://framenet.icsi.berkeley.edu/)** — ~1,200 semantic frames with frame elements and roles. The direct computational realization of Fillmore's frame semantics (1968/1982) — the theoretical foundation for Common Graph's frame model.
4. **[VerbNet](https://verbs.colorado.edu/verbnet/)** — ~300 verb classes with thematic role declarations. VerbNet's role inventory, unified with LIRICS by [Bonial et al (2011)](https://verbs.colorado.edu/~mpalmer/Ling7800/SACL-ICSC2011.pdf), provides the empirical basis for Common Graph's ~25 thematic roles.
5. **[ISO 24617-4 (SemAF-SR)](https://www.iso.org/standard/56866.html)** — The international standard for semantic role annotation.
6. **[SemLink](https://verbs.colorado.edu/semlink/)** — Cross-resource mappings between VerbNet, FrameNet, PropBank, and WordNet.
7. **[UniMorph](https://unimorph.github.io/)** — Morphological database for 100+ languages. "run/ran/running" all resolve to the same sememe.

---

## Standing on Shoulders

Common Graph integrates decades of prior work:

- **Content addressing** (Merkle 1979, Git, IPFS) — all content identified by cryptographic hash
- **Frame semantics** (Fillmore 1968/1982, FrameNet) — assertions as filled predicate structures with thematic roles
- **Thematic role theory** (VerbNet, LIRICS/ISO 24617-4, Dowty 1991) — semantic participant roles grounded in established standards
- **Computational linguistics** (WordNet, CILI, UniMorph, BabelNet, SemLink) — meaning as computable, multilingual structure
- **Speech act theory** (Austin 1962, Searle 1969) — utterances are actions, not just descriptions
- **Actor model** (Hewitt 1973) and **message passing** (Kay/Smalltalk) — independent entities communicating through messages
- **Capability-based security** (Dennis & Van Horn 1966, Miller 2006) — access as unforgeable tokens
- **Public-key cryptography** (Diffie & Hellman 1976, Bernstein/Ed25519) — identity without authority
- **DHT and P2P systems** (Freenet, Chord, Kademlia, Secure Scuttlebutt) — decentralized routing and storage
- **CRDTs** (Shapiro 2011) and **Merkle-CRDTs** (Tschudin 2019) — convergence without coordination
- **Local-first software** (Kleppmann 2019) — user-owned data, offline capability, collaboration without servers

Each solved a piece of the puzzle. Common Graph's contribution — if it works — is the integration: a single model where content addressing, frame semantics, cryptographic identity, multilingual vocabulary, and local-first storage reinforce each other rather than existing as separate systems.

See [`docs/references/`](docs/references/) for the full academic bibliography with 65+ papers across 20 topic areas. See [**The Case for a Semantic Base Layer**](docs/the-case.md) for the theoretical argument.

---

## Project Status

This is an early-stage research project. It functions, but it is not ready for production use.

**What works today:**
- Full item lifecycle: create, sign, commit, store, retrieve, verify
- Semantic frame model with role-qualified bindings, identity-controlled hashing, and signed attestation via FrameRecords
- Content-addressed storage with unified object store and four derived indexes
- TokenDictionary with scoped resolution, grammar-aware frame assembly, and unit conversion
- Quantity expressions with dimensional analysis (e.g., `5m - 2ft`)
- CG-CBOR canonical encoding with deterministic serialization
- Ed25519 signing and verification with KeyLog-based key history
- 3D rendering via Filament (Metal/Vulkan), 2D via Skia, text via JLine/ANSI
- Unified scene system with three composable primitives and constraint/flex layout
- Working games: Chess (3D Staunton pieces), Set, Minesweeper
- P2P and Session protocols with subscriptions and relay forwarding
- English and German WordNet import via LMF pipeline
- English morphology engine with regular inflection + UniMorph irregular forms
- Encryption at rest and in transit

**What's next:**
- Expanding the multilingual import pipeline beyond English and German
- Performance optimization for large libraries
- Bridging to the existing web

**The cautionary context:** Projects with this level of ambition have a history of not shipping. Xanadu, Cyc, Croquet, Plan 9 — the lessons are taken seriously (see [`docs/references/README.md`](docs/references/README.md#visionary-projects-and-cautionary-tales)). The difference, hopefully, is shipping incrementally and in public rather than waiting for completeness.

---

## Building

```bash
./gradlew build          # Build the project
./gradlew test           # Run all tests (JUnit 5)
./gradlew run            # Run interactive shell
./gradlew fresh          # Run with fresh scratch dir (cleaned each run)
./gradlew scratch        # Run with persistent scratch dir
```

Requires **Java 21** (via Gradle toolchain).

---

## Repository Structure

```
core/               # Domain model
  item/             #   Item, IDs, Manifest
  frame/            #   Frame, FrameBody, FrameRecord, Binding
  library/          #   Object store, indexes, TokenDictionary, seed vocabulary
  runtime/          #   Graph entry point, Librarian, Session
  network/          #   Peer Protocol, Session Protocol, transports
  language/         #   Sememe, Lexeme, Language, ThematicRole
  value/            #   Typed values, units, quantities, operators, functions
  policy/           #   PolicySet, PolicyEngine, AuthorityPolicy

english/            # English language support
  importer/         #   WordNet/LMF import, UniMorph import
  morphology/       #   English inflection engine

games/              # Game implementations
  chess/            #   Chess with 3D Staunton pieces
  set/              #   Set card game
  minesweeper/      #   Minesweeper
  poker, spades, yahtzee, dominoes...

ui/                 # Platform rendering
  filament/         #   Filament 3D (Metal/Vulkan/OpenGL), MSDF text
  skia/             #   Skia 2D, layout engine
  text/             #   CLI/TUI (JLine, ANSI)
  scene/            #   Scene model, three primitives, spatial system

docs/               # Design documentation and academic references
```

---

## Documentation

Detailed specifications live in `docs/`:

| Document | Covers |
|----------|--------|
| [**`the-case.md`**](docs/the-case.md) | **The theoretical argument for a semantic base layer** |
| [`frames.md`](docs/frames.md) | The frame primitive, bindings, compound keys, identity, endorsement |
| [`item.md`](docs/item.md) | Item structure, identity, lifecycle, composition |
| [`vocabulary.md`](docs/vocabulary.md) | Vocabulary system, dispatch, expression input |
| [`sememes.md`](docs/sememes.md) | Meaning units, WordNet/CILI anchoring |
| [`language.md`](docs/language.md) | Languages, lexemes, thematic roles, morphology, import pipeline |
| [`storage.md`](docs/storage.md) | Unified object store, indexes, content lifecycle |
| [`library.md`](docs/library.md) | Library architecture, backends, bootstrap |
| [`presentation.md`](docs/presentation.md) | Scene system, three primitives, language resolution, rendering |
| [`trust.md`](docs/trust.md) | Trust matrix, moderation, reactions, policy-driven views |
| [`authentication.md`](docs/authentication.md) | Keys, signatures, signers, device-centric identity |
| [`protocol.md`](docs/protocol.md) | Peer Protocol and Session Protocol |
| [`network.md`](docs/network.md) | Network architecture, discovery, routing, replication |
| [`cg-cbor.md`](docs/cg-cbor.md) | CG-CBOR encoding specification |
| [`content.md`](docs/content.md) | Content addressing, storage, deduplication |
| [`manifest.md`](docs/manifest.md) | Versioning, manifest format, signing |
| [`references/`](docs/references/) | Academic bibliography (65+ papers, 20+ topics) |

---

## Contributing

The architecture is stabilizing but the surface area is large. Design critiques are as valuable as code — possibly more so at this stage. If any of this resonates, open an issue or start a discussion.

---

## License

License will be formalized as the project matures. The intent is permissive open source.

---

*Common Graph is a twenty-year vision of Joshua Chambers. Built with [Claude Code](https://claude.ai/code). Intellectual lineage documented in [`docs/references/`](docs/references/).*
