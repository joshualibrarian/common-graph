# Common Graph

**A unified substrate for content, identity, meaning, and trust.**

> **Fair warning:** This is an active construction site. The architecture is real, the code runs, but everything is changing constantly. If that bothers you, check back later. If that excites you, read on.

---

## The Problem

We interact with computers through layers that were never designed to work together.

Files are opaque byte streams named by path — your OS doesn't know that a JPEG is a photo of your daughter or that two documents are versions of the same report. The web promised to link everything, but links rot, data lives on someone else's server, and "semantic" means whatever the platform's ad model needs it to mean. Your identity is a username and password on fifty different services, each with its own notion of who you are and none of them yours. Your messages live in one silo, your documents in another, your photos in a third, your code in a fourth — each with its own sync model, its own search, its own sharing mechanism, its own idea of what "delete" means.

Meanwhile, the concepts that should be foundational — authorship, trust, versioning, meaning, relationships between things — are either missing entirely or reimplemented from scratch by every application. Your email client doesn't know that the attachment you received is the same document you edited last week. Your note-taking app can't express that two notes are related except by putting them in the same folder. Your chat app can't verify who actually sent a message without trusting a corporation's server.

These aren't separate problems. They're symptoms of a missing foundation.

---

## Frames and Items

Common Graph replaces files and folders with two primitives: **frames** and **items**.

A **frame** is a filled semantic structure — an assertion with a predicate, a subject, and named role bindings. Everything in the system is a frame: content, metadata, relationships, glosses, reactions, trust attestations, configuration. Frames are inspired by [Fillmore's frame semantics](https://en.wikipedia.org/wiki/Frame_semantics_(linguistics)): structured meaning, not flat key-value pairs.

```
AUTHORED_BY { theme: TheHobbit, agent: Tolkien }
GLOSS       { theme: sememe:create, language: ENG, target: "bring into existence" }
CONTENT     { theme: myDocument, target: <snapshot bytes> }
LIKED_BY    { theme: post, experiencer: alice }
```

An **item** is a signed collection of endorsed frames — a coherent identity that groups related assertions. Items can represent anything: documents, people, conversations, machines, games, communities, devices, languages, even meanings themselves. Every item has cryptographic identity, immutable version history, and a manifest that lists its frames with their content hashes.

> *"Item" is a working name. The right word will come.*

### Why This Replaces Files and Folders

| Files & Folders | Frames & Items |
|---|---|
| Opaque byte stream — the OS can't interpret content | Typed frames — the system knows what everything means |
| Named by path in a tree — one location per file | Discoverable by meaning and relationship — an item exists in a graph, not a hierarchy |
| No built-in authorship, versioning, or integrity | Every item is signed, versioned, and content-addressed |
| Metadata is a sidecar (xattr, .DS_Store, EXIF) | Metadata IS frames — first-class, queryable, signed, same as content |
| "Relatedness" means same folder or a hyperlink | Semantic relations: typed, signed, bidirectional, traversable |
| Copy a file to share it, hope nothing changes | Content-addressed: share by hash, verify on receipt, dedup automatically |
| Permissions are rwx bits on a path | Trust policies are items — scoped, weighted, revocable, inspectable |
| Application decides how to open it | Item carries its own vocabulary and presentation |
| Search by filename or full-text keyword | Query by typed semantic relationships across the graph |

A folder is one way to group things — by containment in a hierarchy. Common Graph gives you every way: by authorship, by topic, by type, by time, by trust, by any semantic relationship anyone has asserted. And those groupings are themselves frames — signed, queryable, and extensible by anyone.

### Frames Are the Single Primitive

The frame unification goes deep. There is no separate "component system" vs "relation system" vs "metadata system." These are all frames, differentiated only by their predicate and whether the item's owner endorses them:

- **Endorsed frames** live in the item's manifest, signed by the owner. These are the item's content and structure — its text body, its edit history, its avatar image, its configuration.
- **Unendorsed frames** are independent assertions by anyone. A like, a review, a spam label, a trust attestation. Each is a signed record wrapping a body. Multiple signers can attest the same body — the body hash IS the assertion's identity.

This means trust accumulates naturally. Alice asserts `AUTHOR { theme: TheHobbit, agent: Tolkien }`. Bob independently asserts the same thing. Same body hash, two records. Policy can count attestations, weight them by signer trust, require thresholds — all without a central authority.

---

## Semantic Structure

Common Graph doesn't invent its linguistic backbone from scratch. It draws from five major sources of robust, academically-vetted language data:

1. **[WordNet](https://wordnet.princeton.edu/)** — Princeton's lexical database provides ~120,000 synsets (synonym sets) with definitions, parts of speech, and hierarchical relationships (hypernym/hyponym, meronym/holonym). Each synset becomes a sememe — a universal meaning unit with a deterministic ID.

2. **[CILI (Collaborative Interlingual Index)](https://github.com/globalwordnet/cili)** — The Global WordNet Association's cross-lingual concept mapping links synsets across languages. English "dog," Spanish "perro," and Japanese "犬" map to the same CILI concept. This gives Common Graph's vocabulary multilingual reach without inventing a translation layer.

3. **[UniMorph](https://unimorph.github.io/)** — A morphological database covering 100+ languages with inflectional paradigms. This populates the lexeme form tables — "run/ran/running," "go/went/gone" — so the system recognizes all forms of a word, not just the citation form.

4. **[FrameNet](https://framenet.icsi.berkeley.edu/)** — Berkeley's frame semantics database catalogs ~1,200 semantic frames with their frame elements (roles), relations between frames, and annotated examples. This grounds Common Graph's frame predicates in Fillmore's empirical research — the same tradition that inspired the frame model in the first place.

5. **English morphology engine** — A rule-based inflection system handles regular morphology (pluralization, verb conjugation, comparatives) and works alongside UniMorph's irregular forms to ensure complete coverage.

These sources feed into a unified **TokenDictionary** — one resolution path for everything. Every token (word, symbol, name) is a posting scoped to a context: a language, an item, a user. The scope chain determines which postings match. "Create" scoped to English resolves to the same sememe as "crear" scoped to Spanish, which dispatches the same verb.

### Sememes Are Universal Meaning Units

A **sememe** is a unit of meaning, anchored globally via CILI. Sememes carry:

- A part of speech (verb, noun, preposition, etc.)
- Thematic roles that define what arguments the predicate takes (agent, theme, instrument, patient, etc.)
- Glosses per language (each a frame: `GLOSS { theme: sememe, language: ENG, target: "definition text" }`)
- Symbols for universal notation ("+", "m", "kg", "USD")
- Tokens for language-specific resolution ("create", "crear", "创建")

There are no reserved words. No escape characters. Disambiguation happens through more language — "exit session" vs "exit game" — the same way humans disambiguate naturally.

### Every Part of Speech Participates

This isn't a command-line with verb-only dispatch. The vocabulary system handles all parts of speech through the same resolution pipeline:

| Part of Speech | Role | Examples |
|---|---|---|
| **Verbs** | Dispatch to methods | create, move, commit, edit, describe |
| **Nouns** | Type references, navigation targets | document, log, roster, chess |
| **Proper nouns** | Specific items or components | "My Shopping List", "notes" |
| **Units** | Nouns with dimensional metadata | meter, kilogram, second, dollar |
| **Operators** | Arithmetic, comparison, composition | +, -, >, =, \|> |
| **Functions** | Pure computation | sqrt, sin, max, length, now |
| **Prepositions** | Bind thematic roles | in, with, to, from, as |
| **Modifiers** | Qualify queries | all, recent, unread, first |

---

## Interaction: Language as Interface

Every item has a prompt. You type into it, and the vocabulary system resolves your words into actions — through semantic resolution against the TokenDictionary, not through keyword matching or regex parsing.

```
chess> move pawn to e4           # verb + noun + preposition + noun
graph> create document           # verb + type noun
alice@chat> send "hello" to Bob  # verb + literal + preposition + proper noun
graph> 5m + 3ft                  # quantity expression with unit conversion
graph> sqrt(144) * 2             # function + operator expression
```

The pipeline:

```
Token (any language)
  → TokenDictionary (scoped lookup: language, item, user)
    → Sememe (language-neutral meaning, with part of speech)
      → Item Vocabulary (does this item handle this sememe?)
        → Action (dispatch verb, navigate noun, form quantity, evaluate expression...)
```

Word order is flexible because resolution is semantic, not positional. "Move pawn to e4" and "move to e4 pawn" produce the same result — prepositions bind arguments by thematic role, not by position. Tab completion narrows semantically as you type, drawing from the focused item's vocabulary, the active language, and universal symbols.

**But you don't have to type.** The text interface is primary in the sense that it's the most expressive — it's the full power of the system in any language. But items also declare their own visual presentation, and most users will interact by clicking, dragging, and selecting most of the time. A chess game renders a board you can click on. A document renders editable text. A chat room shows messages with a compose area. The vocabulary system drives both: clicking "reply" on a message dispatches the same sememe as typing "reply" into the prompt.

---

## Storage: One Object Store, Four Indexes

All data lives in a single content-addressed object store: `persist(bytes) → CID`, `fetch(CID) → bytes`. Manifests, frame bodies, frame records, content blobs — all stored as objects keyed by their cryptographic hash. The store doesn't interpret what's inside; callers know what they're fetching because they followed a typed reference to get the CID.

Four derived indexes make the objects queryable:

| Index | Key → Value | Purpose |
|---|---|---|
| **ITEMS** | IID \| VID → timestamp | Version history per item. Prefix scan by IID returns all versions. |
| **FRAME_BY_ITEM** | ItemID \| Predicate \| BodyHash → CID | Frame lookup by participant. Predicates are ItemIDs, so querying by predicate is the same mechanism. |
| **RECORD_BY_BODY** | BodyHash \| SignerKeyID → CID | Who attested this assertion? Attestation counting. |
| **HEADS** | Principal \| IID → VID | Current version per principal per item. |

Every index is rebuildable from the object store — walk all objects, trial-decode each one, rebuild. Indexes are projections, not sources of truth. Corruption or schema changes are recoverable.

Three storage backends implement the same interface:

| Backend | Characteristics | Use Case |
|---|---|---|
| **RocksDB** | Persistent, LSM-tree, bloom filters | Production |
| **MapDB** | Persistent, B-tree, lightweight | Lighter-weight alternative |
| **SkipList** | In-memory, zero dependencies | Testing and ephemeral use |

---

## Identity: Keys, Not Accounts

Your identity is a cryptographic key pair that lives on your device. No server needed. No account to create. No password to forget.

When a Librarian (the local runtime node) boots for the first time, it generates an Ed25519 signing key. This key is the device's identity — it can sign manifests, assert relations, and prove authorship without asking anyone's permission. The private key never leaves the device.

**Devices and people are separate identities.** Your laptop has a key. Your phone has a key. *You* are a Principal — a higher-level identity that authorizes devices by adding their public keys to your KeyLog, an append-only stream in the graph. When you sign something, the chain is verifiable: this manifest was signed by this device key, which was authorized by this principal, whose key history is this auditable log.

- **No single point of failure.** Lose a device? Revoke its key. Your identity survives because it's not tied to any one machine.
- **No platform dependency.** Your keys are yours. No one can deplatform your identity because no one hosts it.
- **Verifiable provenance.** Anyone can trace a signature back through the KeyLog to confirm who signed what and when — without contacting a server.

The Librarian itself is an Item — a Signer with its own identity in the graph. It's a participant in the same system it manages.

---

## Trust: The Social Fabric

Trust isn't a security feature bolted on top — it's the organizing principle of the entire system.

Every manifest and frame record is signed with Ed25519 keys. Trust isn't binary — it's policy-driven with thresholds, scopes, decay, and revocation. Trust policies live on items as configuration, inspectable and adjustable.

Trust determines who you sync with, whose assertions you accept, how far your queries propagate through the network, and whose content appears in your graph at all. There is no separate "moderation" system because trust *is* moderation. You adjust your trust policies — lower a threshold, narrow a scope, revoke a peer relationship — and the effect is immediate, local, and yours.

**Reactions replace algorithms.** A "like" is a signed frame — `LIKED_BY { theme: post, experiencer: alice }` — not a row in a corporate database. Frames can target other frames. If Alice likes a post and Bob thinks Alice's like is astroturfing, Bob signs a frame targeting Alice's frame. Everyone who trusts Bob more than Alice sees that signal. Everyone who trusts Alice more than Bob ignores it. No appeals process, no review board — just overlapping trust graphs producing different views of the same underlying data.

Communities form their own trust topologies. A research group might require multiple endorsements for factual claims. A family might trust everything from family devices unconditionally. These are signed frames in the graph, as portable as any other data.

---

## Networking: Relationships, Not Routes

Your Librarian connects to other Librarians the way you connect to other people — explicitly, with signed attestations recorded in the graph. When your node peers with another, that's a frame: `PEERS_WITH { agent: myDevice, co-agent: theirDevice }`. Network topology isn't hidden metadata — it's part of your graph, queryable like anything else.

- **Trust drives routing.** You ask nodes you have relationships with, and they ask nodes they have relationships with. Trust metrics determine how far a request propagates and through whom.
- **Local-first by default.** All data lives on your devices. Sync is explicit, merge-based, to peers you choose.
- **The protocol is minimal.** Two message types: Request and Delivery. Subscriptions for live updates. Envelopes for relay forwarding. Everything else — discovery, replication, conflict resolution — is convention built on signed frames and content-addressed data.
- **Network topology emerges from community.** A research group's nodes cluster naturally. A family's devices find each other through shared relations. This won't scale like Kademlia for anonymous global lookup — Common Graph isn't trying to be a CDN. It's the connective tissue between people and their data.

---

## Presentation: One Scene, Every Surface

Items declare their presentation through **scenes** — declarative, CBOR-serializable structures that renderers project onto screen. The scene model treats depth as a continuous parameter: a minesweeper tile is a 3mm slab, a chess piece is a full 3D mesh, a text label is flat. These aren't different rendering systems — they're the same scene at different depths.

The same declaration renders as perspective 3D with physically-based lighting on a GPU, as flat 2D through Skia on a lighter machine, or as text art in a terminal. Same items, same scene, different projections.

- **Declarative and serializable.** Scenes are CBOR data, not code. They can be stored, transmitted, and versioned like any other content.
- **Renderer-agnostic.** The scene model doesn't know about Filament or Skia or ANSI codes. Adding a new renderer means implementing the projection, not rewriting the UI.
- **Dimensionally unified.** Text, images, containers, and 3D geometry coexist in one coordinate system.
- **User-customizable.** Presentation is a suggestion from the item's author. The user has the final word.

---

## Encoding: CG-CBOR

All data uses **CG-CBOR** — a profile of [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) with custom tags and strict deterministic encoding. CG-CBOR adds:

- **Self-describing tags** in the 1-byte range: item references (Tag 6), typed values (Tag 7), signed envelopes (Tag 8), quantities with units (Tag 9)
- **No IEEE 754 floats** — non-deterministic across platforms. CG-CBOR uses exact types: rationals, decimals, quantities with unit references
- **Deterministic encoding** — sorted keys, minimal integer encoding, no indefinite lengths. Identical content always produces identical bytes, which is what makes content addressing work

The same encoding serves storage, networking, and content addressing — one format from disk to wire to hash.

---

## Types All the Way Down

**Types are items.** The type that describes a "document" is itself an item — with an IID, a version history, relations, and frames. The type that describes what a "type" is? Also an item. The predicate "author"? An item (a sememe). The unit "meter"? An item.

- **Types are versioned** — their history is preserved in the same content-addressed chain as any other item.
- **Types are discoverable** — query the graph for "all types with a content frame" or "all predicates used by this community."
- **Types are extensible** — anyone can create new types, predicates, units. No central registry.
- **Types carry vocabulary** — a type's verb annotations and frame definitions contribute to every item of that type.

The self-describing nature goes to the wire format. A tagged value carries its type reference inline. A signed envelope carries the key reference. A quantity carries its unit. Nothing requires external schema to interpret.

---

## Working Trees: The Bridge to POSIX

Any item can be materialized as a directory on your filesystem:

```
my-project/
├── README.md              # Mounted content — edit with vim, VS Code, whatever
├── src/
│   └── main.java
└── .item/                 # Item metadata (like .git/)
    ├── iid                # This item's identity
    ├── head/              # Working state
    ├── versions/          # Immutable version snapshots
    └── content/           # Content blocks by hash
```

The visible files are **path mount projections** — each one is a frame's content, mounted at a specific path. Edit them with any tool. When you're done, `commit` mints a new signed, content-addressed version.

This is the migration bridge. Items that need POSIX accessibility get it. Items that don't (a chat stream, a game state, a key log) stay in native content-addressed storage. And a working tree store sits at the highest priority in the store registry, so local edits always shadow stored versions.

---

## Standing on Shoulders

Common Graph integrates decades of prior work:

- **Content addressing** (Merkle 1979, Git, IPFS) — all content identified by cryptographic hash
- **Frame semantics** (Fillmore 1982, FrameNet) — relations as filled predicate structures with thematic roles
- **Computational linguistics** (WordNet, CILI, UniMorph, BabelNet) — meaning as computable, multilingual structure
- **Speech act theory** (Austin 1962, Searle 1969) — utterances are actions, not just descriptions
- **Actor model** (Hewitt 1973) and **message passing** (Kay/Smalltalk) — independent entities communicating through messages
- **Capability-based security** (Dennis & Van Horn 1966, Miller 2006) — access as unforgeable tokens
- **Public-key cryptography** (Diffie & Hellman 1976, Bernstein/Ed25519) — identity without authority
- **DHT and P2P systems** (Chord, Kademlia, Secure Scuttlebutt) — decentralized routing and storage
- **CRDTs** (Shapiro 2011) and **Merkle-CRDTs** (Tschudin 2019) — convergence without coordination
- **Local-first software** (Kleppmann 2019) — user-owned data, offline capability, collaboration without servers

Each solved a piece of the puzzle. Common Graph's contribution — if it works — is the integration: a single model where content addressing, frame semantics, cryptographic identity, multilingual vocabulary, and local-first storage reinforce each other rather than existing as separate systems.

See [`docs/references/`](docs/references/) for the full academic bibliography with 65+ papers across 20 topic areas.

---

## Project Status

This is an early-stage research project. It functions, but it is not ready for production use.

**What works today:**
- Full item lifecycle: create, edit, sign, commit, store, retrieve, verify
- Unified frame model: one primitive for content, relations, metadata, reactions
- Content-addressed storage with unified object store and four derived indexes
- Sememe-based vocabulary with TokenDictionary, inner-to-outer dispatch, and expression evaluation
- Quantity expressions with unit conversion (e.g., `5m - 2ft`)
- CG-CBOR canonical encoding with deterministic serialization
- Ed25519 signing and verification with KeyLog-based key history
- 3D rendering via Filament (Metal/Vulkan), 2D via Skia, text via JLine/ANSI
- Declarative scene system with 15+ widget types and constraint/flex layout
- Working games: Chess (3D Staunton pieces), Set, Minesweeper
- P2P and Session protocols with subscriptions and relay forwarding
- English morphology engine with regular inflection + UniMorph irregular forms

**What's next:**
- Encryption (Tag 10 reserved)
- Full WordNet/CILI import pipeline (partially implemented)
- Multi-language support beyond English bootstrap
- Performance optimization for large libraries
- Bridging to the existing web (ugly but necessary)

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
  item/             #   Item, IDs, Manifest, Frames, Components
  library/          #   Object store, indexes, TokenDictionary, seed vocabulary
  runtime/          #   Graph entry point, Librarian, Session, Scheduler
  network/          #   CG Protocol (P2P), Session Protocol, transports
  trust/            #   Signing, verification, key management
  policy/           #   PolicySet, PolicyEngine, AuthorityPolicy
  value/            #   Typed values, units, quantities, operators, functions
  language/         #   Sememes, Lexicon, QueryParser

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
  scene/            #   Shared scene model, Surface DSL, spatial system

docs/               # Design documentation and academic references
```

---

## Documentation

Detailed specifications live in `docs/`:

| Document | Covers |
|----------|--------|
| [`frames.md`](docs/frames.md) | The frame primitive, body/record split, FrameKey, endorsement |
| [`item.md`](docs/item.md) | Item structure, identity, lifecycle, composition |
| [`vocabulary.md`](docs/vocabulary.md) | Vocabulary system, dispatch, expression input |
| [`sememes.md`](docs/sememes.md) | Meaning units, parts of speech, WordNet/CILI anchoring |
| [`components.md`](docs/components.md) | Component system, types, modes, vocabulary facet |
| [`storage.md`](docs/storage.md) | Unified object store, indexes, content lifecycle |
| [`library.md`](docs/library.md) | Library architecture, backends, bootstrap |
| [`presentation.md`](docs/presentation.md) | Rendering pipeline, scene system, style |
| [`trust.md`](docs/trust.md) | Trust matrix, moderation, reactions, policy-driven views |
| [`authentication.md`](docs/authentication.md) | Keys, signatures, signers, device-centric identity |
| [`protocol.md`](docs/protocol.md) | CG Protocol (P2P) and Session Protocol |
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
