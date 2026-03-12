# Common Graph

**A unified substrate for content, identity, meaning, and trust.**

> **Fair warning:** This is an active construction site. The architecture is real, the code runs, but everything is changing constantly. If that bothers you, check back later. If that excites you, read on.

---

## The Problem

Search engines exist because the web has no idea what anything means.

An HTML page is text with layout hints. A JPEG is opaque bytes with a filename. A product listing, a research paper, a restaurant menu, a medical record — to the infrastructure, they're all the same: blobs at URLs. The web has no mechanism for semantic indexing. None. Every query you make goes to a company that crawled billions of pages, guessed what they're about from word frequency and link structure, and built a proprietary index that you rent access to.

This is so normal that it's invisible. But think about what it means: the entire world's information infrastructure has *zero native ability* to answer "what is this about?" Every search result is a probabilistic guess by a third party. "Red shirt" gives you Star Trek memes and fashion and political commentary and soccer jerseys, and the engine does its best to guess which you meant from your click history and location and what ads it can sell against the ambiguity.

The deeper problem is that files, web pages, emails, messages, and documents are all **semantically inert**. They don't know what they mean. They can't describe their relationships to other things. They can't assert their authorship or verify their integrity. They can't express that this JPEG is a photo of a specific person, that this PDF is version 3 of a contract between two specific parties, or that this code file implements a specific algorithm. All of that meaning lives in human heads, in proprietary databases, in app-specific metadata formats that don't talk to each other.

Common Graph makes meaning structural. Everything in the system — every item, every assertion, every relationship — is grounded in **sememes**: universal units of meaning with globally anchored identities. When you query "red shirt," you're not searching for the *words* "red" and "shirt" — you're searching for the *meaning* "a garment of type shirt with color attribute red." Star Trek Red Shirt memes are a different sememe entirely. They simply don't match.

---

## What You Can Do

**Find things by meaning, not keywords.**

Every item in Common Graph is semantically typed, and every assertion is a frame with a grounded predicate. This means queries resolve against meaning, not text:

- *"All red shirts for sale within 50km"* — resolves SHIRT (garment sememe) + RED (color sememe) + FOR_SALE (commercial predicate) + spatial constraint. Star Trek references, political metaphors, and soccer team merchandise have different sememes. They don't appear.
- *"Papers that cite this paper"* — CITES is a predicate. Every citation is a signed frame. The graph IS the citation index.
- *"All games Alice and I both play"* — traverse the graph: items where both Alice and I have PLAYER frames. No platform needed to track this — it's in the data.
- *"Everything Tolkien authored"* — AUTHORED is a predicate, Tolkien is an item. Prefix scan on the frame index. Every librarian that has Tolkien-related data can answer this locally.

**No crawling. No proprietary index. No intermediary.** The data describes itself. Queries resolve locally or propagate through the social graph to peers who have relevant data.

**Publish without a platform.**

Your content is a signed item on your device. It's discoverable through the graph — through predicates, through your social connections, through anyone who has asserted something about it. You don't need a website, a hosting provider, or a platform's permission. Your identity is a cryptographic key, not an account. Your content is signed by you, stored where you choose, and verifiable by anyone without contacting a server.

**Trust without a moderator.**

A "like" is a signed frame. A spam label is a signed frame. A fact-check is a signed frame. Everyone's trust policies produce different views of the same data — no appeals board, no opaque algorithm, no single point of content control. A research community requires three independent endorsements for factual claims. A family trusts everything from family devices. These are policy frames in the graph, not features of a platform.

**Converse across languages.**

"Create" in English, "crear" in Spanish, "erstellen" in German — same sememe, same verb, same action. The vocabulary system resolves words to meanings, not to command strings. An English speaker and a Spanish speaker can interact with the same item in their own languages, because the interface is semantic, not syntactic.

**Compute with real quantities.**

`5m + 3ft` → `5.9144 m`. Units are sememes with dimensional metadata. The system understands that meters and feet are both lengths, knows the conversion factor, and produces a dimensionally correct result. `$50 + 30 EUR` works the same way (with an exchange rate frame). Quantities are first-class — not strings, not floats, not library calls.

---

## How It Works

### Frames: The Single Primitive

Common Graph replaces files and folders with two primitives: **frames** and **items**. A frame is a filled semantic assertion with a predicate, a subject (theme), and named role bindings. An item is a signed, versioned collection of frames with stable identity. Content, metadata, authorship declarations, glosses, chat messages, trust attestations, policy settings — all frames, all living on items:

```
TITLE        { theme: TheHobbit, target: "The Hobbit" }
AUTHORED     { theme: TheHobbit, AGENT: Tolkien }
GLOSS        { theme: sememe:create, LANGUAGE: ENG, target: "bring into existence" }
LIKED_BY     { theme: post, EXPERIENCER: alice }
```

Frames are inspired by [Fillmore's frame semantics](https://en.wikipedia.org/wiki/Frame_semantics_(linguistics)): structured meaning with thematic roles, not flat key-value pairs.

The predicate carries real meaning about *what role the data plays*. A book's English text is `(TEXT, ENGLISH)`. An audiobook narration is `(AUDIOBOOK, ENGLISH)`. An algorithm's implementation is `(IMPLEMENTATION)` — the fact that it's written in Python is representation metadata, not semantic identity. The predicate tells you what the data *is for*, not what format it's in.

See [`frames.md`](docs/frames.md) for the full frame model — body/record split, FrameKeys, endorsement, representations, queries.

### Items: Signed Collections of Frames

An **item** is a signed collection of frames with stable cryptographic identity. Items can represent anything: documents, people, groups, conversations, machines, games, communities, devices, languages, meanings themselves. Every item carries its own identity (IID), immutable version history, and a manifest listing its endorsed frames with content hashes.

**Types are sememes.** The concept "Book" is a noun sememe — a unit of meaning in the graph with its own IID and version history. It's the same "book" that exists in WordNet. When the English import runs, the WordNet synset for "book" merges idempotently with the type item — same concept, one item. Its glosses are frames: `(GLOSS, ENGLISH) → "a written work"`. Its hypernyms are frames: `(HYPERNYM) → publication`. English itself is an item in the graph, and lexemes (the word "book", its plural "books", its verb form "to book") live as frames on the English item, pointing back to this sememe. The type definition adds structural expectations — what frames a Book expects, what verbs it handles, how it presents — on top of that semantic foundation. Types aren't separate from meanings. They ARE meanings.

In the current Java implementation, a type is declared as an annotated class.  That class is loaded into the graph at runtime as a seed item:

```java
@Type("cg:type/book")
public class Book extends NounSememe {
    @Frame(key = {TITLE})                String title;
    @Frame(key = {AUTHOR}, endorsed=false) ItemID author;
    @Frame(key = {TEXT, ENGLISH})         byte[] englishText;
    @Frame(key = {COVER_ART})            byte[] cover;
}
```

But the class is just the host-language representation of a type item that lives in the graph. `"cg:type/book"` is a deterministic IID — the same on every node, computed from the canonical string, just like sememe IDs. The annotations declare what frames a Book expects, what verbs it handles, how it presents itself. The type item carries all of that as semantic data.

> *"Item" is a working name. The right word will come.*

See [`item.md`](docs/item.md) for item structure, identity, lifecycle, and composition.

### Why This Replaces Files and Folders

| Files & Folders | Frames & Items |
|---|---|
| Opaque byte stream — the OS can't interpret content | Typed frames — the system knows what everything means |
| Named by path in a tree — one location per file | Discoverable by meaning — items exist in a semantic graph, not a hierarchy |
| No built-in authorship, versioning, or integrity | Every item is signed, versioned, and content-addressed |
| Metadata is a sidecar (xattr, .DS_Store, EXIF) | Metadata IS frames — first-class, queryable, signed, same as content |
| "Relatedness" means same folder or a hyperlink | Semantic frames: typed, signed, indexed, traversable |
| Copy a file to share it, hope nothing changes | Content-addressed: share by hash, verify on receipt, dedup automatically |
| Permissions are rwx bits on a path | Trust policies are items — scoped, weighted, revocable, inspectable |
| Application decides how to open it | Item carries its own vocabulary and presentation |
| Search by filename or full-text keyword | Query by meaning across the graph |

A folder is one way to group things — by containment in a hierarchy. Common Graph gives you every way: by authorship, by topic, by type, by time, by trust, by any semantic assertion anyone has made. And those groupings are themselves frames — signed, queryable, and extensible by anyone.

---

## Semantic Discoverability

This is the core difference. The web is a document dump with external indexing bolted on. Common Graph is a **semantic index by construction**.

Every item is typed with a sememe. Every frame has a predicate that is a sememe. Every assertion has role bindings to other sememes or items. This means the graph IS the index. There is no separate crawl-and-index step because the data already describes what it means.

**Sememes are universal meaning units.** Grounded in [WordNet](https://wordnet.princeton.edu/) (~120,000 synsets) and cross-linked via [CILI](https://github.com/globalwordnet/cili) (Collaborative Interlingual Index), sememes carry:

- A part of speech (verb, noun, preposition, etc.)
- Thematic roles (agent, theme, instrument, patient, etc.)
- Glosses per language (each a frame: `(GLOSS, ENG) → "definition text"`)
- Symbols for universal notation ("+", "m", "kg", "USD")
- Tokens for language-specific resolution ("create", "crear")

There are no reserved words. No escape characters. Disambiguation happens through more language — the same way humans do it.

**Predicates ARE indexes.** When you assert `AUTHORED { theme: TheHobbit, AGENT: Tolkien }`, the frame is indexed on TheHobbit (by AUTHORED predicate) and on Tolkien (by AGENT role). Querying "what did Tolkien author?" is a prefix scan on Tolkien's frame index filtered to AUTHORED — no full-text search, no crawling, no ranking algorithm.

**Discovery fans out through the social graph.** Your librarian answers queries from its local store first. If it doesn't have the answer, it asks peers. Peers ask their peers. Trust metrics control propagation depth. The result: global discoverability without a global index. Communities that share interests naturally cluster, and queries resolve faster within clusters.

This means:
- **A marketplace doesn't need a search engine.** Products are items with semantic frames (PRICED_AT, CATEGORIZED_AS, LOCATED_AT). Queries resolve against meaning.
- **An academic community doesn't need Google Scholar.** Citations are signed frames. Publication metadata is semantic. Impact is countable attestations.
- **A music library doesn't need Shazam.** Audio fingerprints are frames. Genre, artist, album, track — all semantic, all signed, all queryable.
- **A recipe collection doesn't need a recipe website.** Ingredients are items. Recipes assert frames about ingredients, quantities, and techniques. "Recipes using chicken and lemon that take under 30 minutes" is a frame query with constraints.

The web tried to solve this with microdata, RDFa, Schema.org, JSON-LD — bolting structured data onto unstructured documents. It didn't work because it's opt-in, unenforced, and disconnected from the content it describes. In Common Graph, the structure IS the content. You can't create a thing without it being semantically typed, because the thing IS a typed, signed frame.

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
  -> TokenDictionary (scoped lookup: language, item, user)
    -> Sememe (language-neutral meaning, with part of speech)
      -> Item Vocabulary (does this item handle this sememe?)
        -> Action (dispatch verb, navigate noun, form quantity, evaluate expression...)
```

Word order is flexible because resolution is semantic, not positional. "Move pawn to e4" and "move to e4 pawn" produce the same result — prepositions bind arguments by thematic role, not by position.

**But you don't have to type.** The text interface is primary in the sense that it's the most expressive. But items declare their own visual presentation, and most users will interact by clicking, dragging, and selecting most of the time. A chess game renders a board you can click on. A document renders editable text. A chat room shows messages with a compose area. The vocabulary system drives both: clicking "reply" dispatches the same sememe as typing "reply."

---

## Identity: Keys, Not Accounts

Your identity is a cryptographic key pair that lives on your device. No server needed. No account to create. No password to forget.

When a Librarian (the local runtime node) boots for the first time, it generates an Ed25519 signing key. This key is the device's identity — it can sign manifests, assert frames, and prove authorship without asking anyone's permission. The private key never leaves the device.

**Devices and people are separate identities.** Your laptop has a key. Your phone has a key. *You* are a Principal — a higher-level identity that authorizes devices by adding their public keys to your KeyLog, an append-only stream in the graph. Lose a device? Revoke its key. Your identity survives because it's not tied to any one machine.

The Librarian itself is an Item — a Signer with its own identity in the graph.

---

## Trust: The Social Fabric

Trust isn't a security feature bolted on top — it's the organizing principle of the entire system.

Every manifest and frame record is signed. Trust isn't binary — it's policy-driven with thresholds, scopes, decay, and revocation. Trust policies live on items as configuration, inspectable and adjustable.

Trust determines who you sync with, whose assertions you accept, how far your queries propagate, and whose content appears in your graph at all. There is no separate "moderation" system because trust *is* moderation.

**Reactions replace algorithms.** A "like" is a signed frame. If Alice likes a post and Bob thinks Alice's like is astroturfing, Bob signs a frame targeting Alice's frame. Everyone who trusts Bob more than Alice sees that signal. Everyone who trusts Alice more than Bob ignores it. No appeals process, no review board — just overlapping trust graphs producing different views of the same data.

---

## Networking: Relationships, Not Routes

Your Librarian connects to other Librarians the way you connect to other people — explicitly, with signed attestations recorded in the graph. Network topology IS the social graph.

- **Trust drives routing.** You ask nodes you have relationships with, and they ask nodes they have relationships with.
- **Local-first by default.** All data lives on your devices. Sync is explicit, merge-based, to peers you choose.
- **The protocol is minimal.** Two message types: Request and Delivery. Everything else — discovery, replication, conflict resolution — is convention built on signed frames and content-addressed data.
- **Network topology emerges from community.** A research group's nodes cluster naturally. A family's devices find each other through shared frames.

---

## Storage: One Object Store, Four Indexes

All data lives in a single content-addressed object store: `persist(bytes) -> CID`, `fetch(CID) -> bytes`. Manifests, frame bodies, frame records, content blobs — all stored as objects keyed by their cryptographic hash.

Four derived indexes make the objects queryable:

| Index | Key -> Value | Purpose |
|---|---|---|
| **ITEMS** | IID \| VID -> timestamp | Version history per item |
| **FRAME_BY_ITEM** | ItemID \| Predicate \| BodyHash -> CID | Frame lookup by participant and predicate |
| **RECORD_BY_BODY** | BodyHash \| SignerKeyID -> CID | Who attested this assertion? |
| **HEADS** | Principal \| IID -> VID | Current version per principal per item |

Every index is rebuildable from the object store. Indexes are projections, not sources of truth.

Three storage backends: **RocksDB** (production), **MapDB** (lightweight), **SkipList** (in-memory/testing).

---

## Presentation: One Scene, Every Surface

Items declare their presentation through **scenes** — declarative, CBOR-serializable structures that renderers project onto screen. The same declaration renders as perspective 3D with physically-based lighting on a GPU, as flat 2D through Skia, or as text art in a terminal. Same items, same scene, different projections.

---

## Encoding: CG-CBOR

All data uses **CG-CBOR** — a profile of [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) with custom tags and strict deterministic encoding:

- **Self-describing tags** in the 1-byte range: item references (Tag 6), typed values (Tag 7), signed envelopes (Tag 8), quantities with units (Tag 9)
- **No IEEE 754 floats** — non-deterministic across platforms. CG-CBOR uses exact types: rationals, decimals, quantities with unit references
- **Deterministic encoding** — sorted keys, minimal integer encoding, no indefinite lengths. Identical content always produces identical bytes.

---

## Types All the Way Down

**Types are items.** The type that describes a "document" is itself an item — with an IID, a version history, and frames. The type that describes what a "type" is? Also an item. The predicate "author"? An item (a sememe). The unit "meter"? An item.

- **Types are versioned** — their history is preserved in the same content-addressed chain as any other item.
- **Types are discoverable** — query the graph for "all types with a text frame" or "all predicates used by this community."
- **Types are extensible** — anyone can create new types, predicates, units. No central registry.
- **Types carry vocabulary** — a type's verb annotations and frame definitions contribute to every item of that type.

---

## Linguistic Foundation

Common Graph doesn't invent its linguistic backbone from scratch:

1. **[WordNet](https://wordnet.princeton.edu/)** — ~120,000 synsets (synonym sets) with definitions, hierarchical relationships. Each synset becomes a sememe.
2. **[CILI (Collaborative Interlingual Index)](https://github.com/globalwordnet/cili)** — Cross-lingual concept mapping. English "dog," Spanish "perro," Japanese "犬" map to the same concept.
3. **[UniMorph](https://unimorph.github.io/)** — Morphological database for 100+ languages. "run/ran/running" all resolve to the same sememe.
4. **[FrameNet](https://framenet.icsi.berkeley.edu/)** — ~1,200 semantic frames with frame elements and roles. The empirical basis for Common Graph's frame model.
5. **English morphology engine** — Rule-based inflection for regular forms, UniMorph for irregular.

---

## Standing on Shoulders

Common Graph integrates decades of prior work:

- **Content addressing** (Merkle 1979, Git, IPFS) — all content identified by cryptographic hash
- **Frame semantics** (Fillmore 1982, FrameNet) — assertions as filled predicate structures with thematic roles
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
- Unified frame model with FrameKey-based addressing
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
  item/             #   Item, IDs, Manifest, Frames, FrameTable
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
| [`frames.md`](docs/frames.md) | The frame primitive, body/record split, FrameKey, representations, endorsement |
| [`item.md`](docs/item.md) | Item structure, identity, lifecycle, composition |
| [`vocabulary.md`](docs/vocabulary.md) | Vocabulary system, dispatch, expression input |
| [`sememes.md`](docs/sememes.md) | Meaning units, parts of speech, WordNet/CILI anchoring |
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
