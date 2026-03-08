# Common Graph

**A unified substrate for content, identity, meaning, and trust.**

---

## The Problem

We interact with computers through layers that were never designed to work together. Files are opaque byte streams named by path — your OS doesn't know that a JPEG is a photo of your daughter or that two documents are versions of the same report. The web promised to link everything, but links rot, data lives on someone else's server, and "semantic" means whatever the platform's ad model needs it to mean. Your identity is a username and password on fifty different services, each with its own notion of who you are and none of them yours. Your messages live in one silo, your documents in another, your photos in a third, your code in a fourth — each with its own sync model, its own search, its own sharing mechanism, its own idea of what "delete" means.

Meanwhile, the concepts that should be foundational — authorship, trust, versioning, meaning, relationships between things — are either missing entirely or reimplemented from scratch by every application. Your email client doesn't know that the attachment you received is the same document you edited last week. Your note-taking app can't express that two notes are related except by putting them in the same folder. Your chat app can't verify who actually sent a message without trusting a corporation's server.

These aren't separate problems. They're symptoms of a missing foundation.

## The Vision

Common Graph is that foundation: a unified substrate where **content, identity, meaning, and computation** live together in a single coherent model.

An **Item** is the fundamental unit. Items can represent anything — documents, people, conversations, machines, datasets, games, communities, devices, languages, even *meanings* themselves. Every item has cryptographic identity, immutable version history, typed content, signed semantic relationships, and a vocabulary that makes it interactive. Think of a "little Git repository" that also knows what it means and can respond to natural language.

**Sememes** are the vocabulary. Seeded from [WordNet](https://wordnet.princeton.edu/) via the [Collaborative Interlingual Index](https://github.com/globalwordnet/cili), sememes anchor concepts globally — "create" in English, "crear" in Spanish, and "创建" in Chinese resolve to the same meaning. Every part of speech participates: verbs dispatch actions, nouns name types, prepositions bind arguments, units carry dimensional metadata. There are no reserved words. Disambiguation happens through more language, not escape characters.

**Relations** make the graph. Signed semantic triples — `book:Hobbit → author → person:Tolkien` — are first-class, queryable, and auditable. Not hidden in platform databases. Not trapped behind APIs. Anyone can assert, dispute, or extend them.

**Trust is explicit — and it's the whole game.** Every manifest and relation is signed with Ed25519 keys. Trust isn't binary — it's policy-driven with thresholds, scopes, decay, and revocation. Trust policies live on items as configuration, inspectable and adjustable.

But trust isn't just a security mechanism — it's the social fabric of the entire system. Trust determines who you sync with, whose assertions you accept, how far your queries propagate through the network, and whose content appears in your graph at all. There is no separate "moderation" system because trust *is* moderation. You don't report abuse to a platform and hope they act. You adjust your trust policies — lower a threshold, narrow a scope, revoke a peer relationship — and the effect is immediate, local, and yours.

Communities form their own trust topologies: a research group might trust each other's type definitions broadly but require multiple endorsements for factual claims. A family might trust everything from family devices unconditionally. These aren't platform settings — they're signed relations in the graph, as inspectable and portable as any other data.

**Local-first by design.** All data lives on your devices. Networking is explicit. Sync is merge-based. You own your graph.

### How It Fits Together

These aren't independent features — they reinforce each other. Content addressing means any node can verify any data without trusting the source. Signed relations mean assertions are attributable and disputable. Sememes mean the same concept can be expressed in any language and still resolve to the same action. Local-first storage means no single point of failure or control. And the vocabulary system means items aren't inert data — they're interactive objects with behavior you can invoke.

A concrete example: you create a document (an item). It gets a cryptographic identity, you sign the manifest with your device key, and its content is hashed for integrity. You add a relation: `myDoc → author → me`. You share it with a colleague by syncing to their node — they can verify your signature, verify the content hasn't been tampered with, and query the relation to see who wrote it. They add their own relation: `myDoc → reviewed_by → them`. Both assertions coexist in the graph, signed by their respective authors, neither requiring a central authority. Their node doesn't need to speak English — the sememe for "author" is language-neutral.

---

## What Changes for the User

Files and folders don't disappear — but they stop being the organizing principle of your digital life. Instead of managing a hierarchy of opaque blobs, you work with items that know what they are, who made them, how they relate to other things, and what you can do with them.

| Instead of... | You get... |
|---------------|-----------|
| Renaming and sorting files into folders | Items that organize themselves by meaning and relationship |
| Remembering which app opens what | Items that carry their own behavior and presentation |
| Signing into platforms to access your own data | Cryptographic identity on your device — your keys, your data |
| Copying text between apps to "link" things | First-class semantic relations: `photo → depicts → Alice` |
| Learning different command syntaxes per tool | One vocabulary that works across everything, in your language |
| Searching by keyword and hoping | Traversing a graph of typed, signed, semantic relationships |
| Trusting platforms with your messages and files | Local-first storage with explicit, auditable sync |

---

## Interaction: Language as Interface

Every item has a prompt. You type into it, and the vocabulary system resolves your words into actions — not through keyword matching or regex parsing, but through semantic resolution against a global dictionary of meanings.

```
chess> move pawn to e4           # verb + noun + preposition + noun
graph> create document           # verb + type noun
alice@chat> send "hello" to Bob  # verb + literal + preposition + proper noun
```

The pipeline is the same regardless of language:

```
Token (any language)
  → TokenDictionary (scoped lookup: language, item, user)
    → Sememe (language-neutral meaning, with part of speech)
      → Item Vocabulary (does this item handle this sememe?)
        → Action (dispatch verb, navigate noun, form quantity...)
```

"Create" in English, "crear" in Spanish, and "创建" in Chinese resolve to the same sememe. "Exit" might match both the session and a game component — the system presents the ambiguity and the user resolves it with more language: "exit session" vs "exit game." No reserved words. No escape characters. Disambiguation through natural refinement.

Word order is flexible because resolution is semantic, not positional. "Move pawn to e4" and "move to e4 pawn" produce the same result — prepositions bind arguments by thematic role, not by where they appear in the string. Tab completion narrows semantically as you type, drawing from the focused item's vocabulary, the active language, and universal symbols.

**But you don't have to type.** The text interface is primary in the sense that it's the most expressive — it's the full power of the system in any language. But items also declare their own visual presentation, and most users will interact by clicking, dragging, and selecting most of the time. A chess game renders a board you can click on. A document renders editable text. A chat room shows messages with a compose area. The vocabulary system drives both: clicking "reply" on a message dispatches the same sememe as typing "reply" into the prompt.

Items declare their presentation through **scenes** — declarative, CBOR-serializable structures that renderers project onto screen. Presentation is not hardcoded — it's data, carried by the item, customizable by the user. You can rearrange, restyle, or replace how any item presents itself, because the scene is just another component. The default presentation comes from the item's type; your overrides layer on top.

This is different from traditional applications where the developer dictates the UI and the user accepts it. In Common Graph, presentation is a suggestion from the item's author. The user has the final word.

---

## Identity: Keys, Not Accounts

Today, "identity" means a username and password on someone else's server. You prove who you are by asking a platform to vouch for you. Lose access to the platform, lose your identity. Get locked out, and your data — your messages, your documents, your relationships — goes with it.

Common Graph inverts this. **Your identity is a cryptographic key pair that lives on your device.** No server needed. No account to create. No password to forget.

When a Librarian boots for the first time, it generates an Ed25519 signing key. This key is the device's identity — it can sign manifests, assert relations, and prove authorship without asking anyone's permission. The private key never leaves the device. It's stored encrypted at rest, in a vault that can use software encryption, a TPM, a hardware security module, or the OS keychain — whatever the platform provides.

**Devices and people are separate identities.** Your laptop has a key. Your phone has a key. *You* are a Principal — a higher-level identity that authorizes devices by adding their public keys to your KeyLog, an append-only stream in the graph. When you sign something, the chain is verifiable: this manifest was signed by this device key, which was authorized by this principal, whose key history is this auditable log.

This means:
- **No single point of failure.** Lose a device? Revoke its key from your Principal's KeyLog. Your identity survives because it's not tied to any one machine.
- **No platform dependency.** Your keys are yours. Your signatures are yours. No one can deplatform your identity because no one hosts it.
- **Verifiable provenance.** Anyone can trace a signature back through the KeyLog to confirm who signed what and when — without contacting a server.

The Librarian itself is an Item — a Signer with its own identity in the graph. It's not a hidden runtime service; it's a participant in the same system it manages, with the same verifiable identity as everything else.

---

## Networking: Relationships, Not Routes

Most distributed systems start with a routing problem: how does node A find data on node B? DHTs like Chord and Kademlia solve this with structured overlay networks — elegant, but mechanical. They route by hash distance, not by meaning or trust.

Common Graph starts from a different place: **people form relationships, and their devices form relationships too.** Your Librarian (the local runtime node) connects to other Librarians the way you connect to other people — explicitly, with signed attestations recorded in the graph itself. When your node peers with another, that's a first-class relation: `myDevice → PEERS_WITH → theirDevice`. When it learns a network address, that's a relation too: `theirDevice → REACHABLE_AT → Endpoint(...)`. Your network topology isn't hidden metadata — it's part of your graph, queryable and auditable like anything else.

This means:

- **Trust drives routing.** You don't broadcast requests to anonymous peers. You ask nodes you have relationships with, and they ask nodes *they* have relationships with. Trust metrics — built from signed relations, endorsement counts, scopes, and decay — determine how far a request propagates and through whom.
- **Storage is configurable.** Where your data lives is a policy decision, not a protocol assumption. Everything local by default. Explicit sync to peers you choose. Future extensions could add community stores, cooperative caching, or cloud backends — all through the same store registry that already layers working-tree, primary, and remote stores by priority.
- **The protocol is minimal.** Two message types: Request ("I want something") and Delivery ("here it is"). Subscriptions for live updates. Envelopes for relay forwarding through trusted intermediaries. That's it. Everything else — discovery, replication, conflict resolution — is convention built on signed relations and content-addressed data.
- **Network topology emerges from community.** Rather than a global DHT with uniform routing, the graph's connectivity reflects actual human and organizational relationships. A research group's nodes cluster naturally. A family's devices find each other through their shared relations. This won't scale like Kademlia for anonymous global lookup — but Common Graph isn't trying to be a CDN. It's trying to be the connective tissue between people and their data.
- **Moderation is trust, not authority.** Centralized platforms solve moderation by hiring humans or training classifiers to make content decisions for billions of people. Common Graph doesn't have a "content moderation layer" because trust policies already do this work. If someone floods your peer network with spam, you lower their trust scope or sever the peer relation — and the effect cascades naturally through the graph. Communities that peer densely with each other form natural trust boundaries. A node that behaves badly loses peer relationships, which means it loses routing, which means it loses reach — the same way a person who behaves badly loses social connections. This isn't a metaphor for social dynamics. It literally *is* social dynamics, modeled in signed relations.

**Reactions replace algorithms.** On social media, a "like" is a row in a corporate database, visible only through the platform's algorithm. In Common Graph, a like is a signed relation — `post → liked_by → alice`, signed by Alice. So is a dislike, a "funny," an "insightful," a "misleading" — any sememe can be a reaction predicate. These are first-class graph data, queryable by anyone who can see them, filterable by your trust policies. The interesting part is what happens next: relations can target other relations. If Alice likes a post and Bob thinks Alice's like is astroturfing, Bob signs a relation targeting Alice's like as spam. Now everyone who trusts Bob more than Alice sees that signal. Everyone who trusts Alice more than Bob ignores it. There's no appeals process, no review board, no algorithm deciding what's "really" spam — just overlapping trust graphs producing different views of the same underlying data. A community of cryptographers and a community of flat-earthers can coexist in the same graph, each seeing the reactions and endorsements of the people they trust, without a platform making editorial decisions for either of them.

The protocol is designed to be extended without changing the wire format. New relation predicates can express new routing policies, new storage strategies, new trust models — all as graph data, not protocol upgrades.

See [`docs/protocol.md`](docs/protocol.md) and [`docs/trust.md`](docs/trust.md) for the full specifications.

---

## Presentation: One Scene, Every Surface

Building a UI system from scratch was not the plan. Nobody wants to do that. But every existing option comes with a cost.

Web technologies (HTML/CSS/JS) bring decades of accumulated complexity, a document model that never quite fit application UI, and an entire browser runtime as a dependency. Native toolkits (SwiftUI, Jetpack Compose, GTK, Qt) lock you to a platform or demand per-platform rewrites. Cross-platform frameworks (Flutter, React Native, Electron) bridge the gap but carry their own runtime assumptions, layout models, and rendering pipelines — and none of them treat 3D as a first-class citizen alongside 2D.

Common Graph needs something none of these provide: a **single declarative scene model** where depth is a continuous parameter, not a cliff between "2D app" and "3D engine." A minesweeper tile is a 3mm slab. A chess board square is a 1cm platform. A chess piece is a full 3D mesh. A text label is flat. These aren't different rendering systems — they're the same scene at different depths. The same declaration renders as perspective 3D with physically-based lighting on a GPU, as flat 2D through Skia on a lighter machine, or as text art in a terminal. Same items, same scene, different projections.

So here we are. The scene system is:

- **Declarative and serializable.** Scenes are CBOR data structures, not code. They can be stored, transmitted, cached, and versioned like any other content. Items declare their presentation through annotations or programmatic schemas.
- **Renderer-agnostic.** The scene model doesn't know about Filament or Skia or ANSI escape codes. Renderers project the same abstract tree into their native medium. Adding a new renderer (WebGL, a game engine, an accessibility layer) means implementing the projection, not rewriting the UI.
- **Dimensionally unified.** Text, images, containers, and 3D geometry coexist in one coordinate system. Depth, elevation, and lighting are properties, not mode switches. A button can have a shadow because it has depth, not because someone implemented a shadow API.
- **Portable.** No browser runtime. No platform SDK. The scene model is pure data; renderers are swappable backends. This code can target a desktop GPU, a phone, a terminal, a VR headset, or a watch — wherever there's a renderer that can project the scene tree.

Is it as mature as HTML/CSS? Obviously not. Is it as polished as SwiftUI? Not yet. But it doesn't carry 30 years of backward compatibility constraints, and it solves a problem none of them even attempt: treating the full spectrum from flat text to spatial 3D as one continuous, declarative, content-addressed scene.

See [`docs/presentation.md`](docs/presentation.md) for the rendering pipeline and scene system details.

---

## Encoding: Why Binary, Why CBOR

The web runs on text. HTML, CSS, JSON, XML — human-readable formats designed for an era when developers needed to view source and debug by eye. That was a reasonable tradeoff in 1993. It's an odd choice for the backbone of global infrastructure in 2026.

Text formats are bulky. A JSON object carrying a 32-byte hash needs 66+ bytes of hex encoding plus quotes and keys. The same value in CBOR is 34 bytes. Multiply that across every item, every relation, every content reference in a graph, and the difference is not academic — it's bandwidth, storage, and parse time at scale. Text formats are also weakly typed: JSON has no distinction between an integer and a float, no binary data type, no way to express "this is a cryptographic hash" versus "this is a UTF-8 string" without out-of-band schema.

Common Graph uses **CG-CBOR** — a profile of [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) with custom tags and strict deterministic encoding rules. CBOR is to JSON what a compiled binary is to source code: structurally equivalent, dramatically more compact, and unambiguous to parse. CG-CBOR adds:

- **Self-describing tags** in the efficient 1-byte range: item references (Tag 6), typed values (Tag 7), signed envelopes (Tag 8), quantities with units (Tag 9)
- **No IEEE 754 floats** — floats are non-deterministic across platforms (NaN, signed zero, precision loss). CG-CBOR uses exact types: rationals, decimals, and quantities with unit references
- **Deterministic encoding** — sorted keys, minimal integer encoding, no indefinite lengths. Identical content always produces identical bytes, which is what makes content addressing work
- **Shorthand conventions** — bare CBOR primitives (strings, integers, booleans) map to common CG types automatically, so simple values stay compact

The result: every item manifest, every relation, every protocol message, every scene declaration is compact, self-describing, deterministic, and hashable. The same encoding serves storage, networking, and content addressing — one format from disk to wire to hash.

See [`docs/cg-cbor.md`](docs/cg-cbor.md) for the full specification.

---

## Types All the Way Down

Most systems draw a hard line between data and the description of data. You have a database, and separately you have a schema that describes what's in the database. You have objects, and separately you have classes defined in source code. The schema and the data live in different worlds, managed by different tools, versioned (if at all) by different mechanisms.

Common Graph erases that line. **Types are items.** The type that describes a "document" is itself an item — with an IID, a version history, a manifest, relations, and components, just like any document it describes. The type that describes what a "type" is? Also an item. The predicate "author" that links a document to a person? An item (a sememe). The unit "meter" that gives meaning to a measurement? An item.

This isn't philosophical navel-gazing — it has practical consequences:

- **Types are versioned.** When a type evolves, its history is preserved in the same content-addressed, signed manifest chain as any other item. You can see what "document" meant at any point in time.
- **Types are discoverable.** Because types are items with relations, you can query the graph for "all types that have a 'content' component" or "all predicates used by this community" — the type system is itself navigable data.
- **Types are extensible by users.** Anyone can create new types, new predicates, new units — they're just items. No central registry, no gatekeeping, no waiting for a standards body. If two communities independently create a "recipe" type, they can later relate them through sememe mappings.
- **Types carry their own vocabulary.** A type's `@Verb` annotations and component definitions contribute to the vocabulary of every item of that type. The type doesn't just describe structure — it defines behavior and language.

The self-describing nature goes all the way to the wire format. A CG-CBOR tagged value carries its type reference inline. A signed envelope carries the signature algorithm and key reference. A quantity carries its unit. Nothing requires external schema to interpret — the data describes itself, and the descriptions are themselves data in the same graph.

See [`docs/item.md`](docs/item.md) for the full item structure and [`docs/components.md`](docs/components.md) for the component type system.

---

## Components: What Items Are Made Of

An item isn't a monolithic blob — it's composed of **components**, each with its own type, content, and stable handle. A person item might have a profile component, a key log, a roster of contacts, and an avatar image. A document item might have a text body, an edit history stream, and attached media. A chess game has a game state DAG, a spatial board model, and player rosters.

Components come in distinct content modes:

| Mode | What it holds | Examples |
|------|---------------|---------|
| **Snapshot** | Immutable content, addressed by hash. Replace entirely on update. | Documents, images, configuration, models |
| **Stream** | Append-only log with one or more heads. Entries are immutable once written. | Chat messages, key history, activity logs |
| **Reference** | Points to another item by IID. Containment without copying. | Embedded items, linked resources |
| **Local-only** | Never leaves the device. Not synced, not shared. | Private keys, caches, local databases |

Each component entry has a **faceted structure** — not just content, but also configuration (settings and per-component policy), presentation (where it appears), and vocabulary (what names and verbs it contributes to the item). This means a component is simultaneously content, behavior, and interface:

```
ComponentEntry
  ├── payload       # The content (snapshot CID, stream heads, or reference)
  ├── config        # Settings and policy for this component
  ├── presentation  # Mounts: where this appears in tree / surface / 3D
  └── vocabulary    # Names, aliases, and verb registrations
```

### Three Mount Types

A single component can appear in multiple places simultaneously through **mounts**:

- **PathMount** — position in a tree hierarchy (`/documents/notes`). This is what makes components navigable as a filesystem-like structure and what drives the working tree.
- **SurfaceMount** — placement in a named 2D region (`sidebar`, `main`, `footer`). This is what drives the visual layout when an item renders its scene.
- **SpatialMount** — position and rotation in 3D space (x, y, z + quaternion). This is what places components in a spatial environment — a chess piece on a board, a node in a visualization.

The same component can have all three mounts: navigable by path, visible in a 2D layout, and positioned in 3D space. The mounts are presentation metadata, not copies of the content.

See [`docs/components.md`](docs/components.md) for the full component system.

---

## Working Trees: The Bridge to POSIX

POSIX defines the fundamental abstractions almost all software is built on: files, directories, processes, permissions. These abstractions are from the 1970s. They assume local storage, single-user or simple multi-user, no semantic typing, no cryptographic identity, no content addressing. Every file is an opaque byte stream with a path name and a permission bitmask. Fifty years later, we're still building on these primitives — and working around their limitations with layers of middleware, databases, authentication services, and version control systems that wouldn't be necessary if the foundation had richer concepts.

Common Graph is, in a sense, a bid to replace POSIX's core abstractions with modern equivalents: typed components instead of byte streams, semantic relations instead of directory hierarchies, cryptographic identity instead of uid/gid, signed trust policies instead of permission bits, content-addressed versioning instead of mutable files. Not by removing the filesystem, but by providing a richer layer that the filesystem can project into.

That projection is the **working tree**. Any item can be materialized as a directory on your filesystem that looks and behaves like a normal folder, editable with any text editor, IDE, or command-line tool.

```
my-project/
├── README.md              # Mounted content — edit with vim, VS Code, whatever
├── src/
│   └── main.java
├── data/
│   └── config.json
└── .item/                 # Item metadata (like .git/)
    ├── iid                # This item's identity
    ├── head/              # Working state: components, mounts, actions
    ├── manifests/         # Immutable version snapshots
    ├── channels/          # Named branches (main, draft)
    ├── content/           # Content blocks by hash
    └── relations/         # Signed relations
```

The visible files (`README.md`, `src/main.java`) are **path mount projections** — each one is a component's content, mounted at a specific path. Edit them with any tool. When you're done, `commit` mints a new signed, content-addressed version — just like `git commit`, but for an item with typed components, semantic relations, and cryptographic identity.

This is the migration bridge. You don't have to choose between Common Graph and your filesystem. Items that need POSIX accessibility get it. Items that don't (a chat stream, a game state, a key log) stay in the graph's native content-addressed storage. And a working tree store sits at the highest priority in the store registry, so local edits always shadow stored versions — your in-progress work is never overwritten by a sync.

The working tree is also the answer to "how do I use Git with this?" — a materialized item is a directory. Put it in a Git repo if you want. The `.item/` directory carries the graph identity alongside whatever version control you're already using.

See [`docs/working-tree.md`](docs/working-tree.md) for the full specification.

---

## Standing on Shoulders

Common Graph does not invent from scratch. It integrates decades of prior work:

- **Content addressing** (Merkle 1979, Git, IPFS) — all content identified by cryptographic hash
- **Semantic web** (Berners-Lee 2001, RDF, linked data) — semantic triples as first-class data, though CG replaces URLs with content-addressed IDs
- **Computational linguistics** (WordNet, CILI, FrameNet, Montague semantics) — meaning as computable structure
- **Speech act theory** (Austin 1962, Searle 1969) — the insight that utterances are actions, not just descriptions
- **Actor model** (Hewitt 1973) and **message passing** (Kay/Smalltalk) — independent entities communicating through messages
- **Capability-based security** (Dennis & Van Horn 1966, Miller 2006) — access as unforgeable tokens
- **Public-key cryptography** (Diffie & Hellman 1976, Bernstein/Ed25519) — identity without authority
- **DHT and P2P systems** (Chord, Kademlia, Freenet, Secure Scuttlebutt) — decentralized routing and storage
- **CRDTs** (Shapiro 2011) and **Merkle-CRDTs** (Tschudin 2019) — convergence without coordination
- **Local-first software** (Kleppmann 2019) — user-owned data, offline capability, collaboration without servers
- **Visionary systems** (Bush's memex, Engelbart's augmentation, Nelson's Xanadu, Kay's Croquet) — the philosophical lineage

Each of these solved a piece of the puzzle. Common Graph's contribution — if it works — is the integration: a single model where content addressing, semantic relations, cryptographic identity, multilingual vocabulary, and local-first storage reinforce each other rather than existing as separate systems stitched together. Whether that integration is elegant synthesis or foolish overreach probably depends on when you ask.

See [`docs/references/`](docs/references/) for the full academic bibliography with 65+ papers across 20 topic areas.

---

## Project Status: Honest Assessment

This is an early-stage, ambitious, in-progress research project. It functions, but it is not ready for production use.

**What works today:**
- Full item lifecycle: create, edit, sign, commit, store, retrieve, verify
- Content-addressed storage with three backends (RocksDB, MapDB, in-memory)
- Sememe-based vocabulary with TokenDictionary, inner-to-outer dispatch, and expression input
- CG-CBOR canonical encoding with deterministic serialization for hashing
- Ed25519 signing and verification with KeyLog-based key history
- Relation indexing and graph queries
- 3D rendering via Filament (Metal/Vulkan), 2D via Skia, text via JLine/ANSI
- Declarative scene system with 15+ widget types and constraint/flex layout
- Working games: Chess (3D Staunton pieces), Set, Minesweeper, and more
- P2P and Session protocols with subscriptions and relay forwarding
- Comprehensive design documentation

**What's still in flux:**
- Data formats are not yet stable — expect breaking changes
- The vocabulary system is undergoing active redesign (see [`docs/roadmap-vocabulary.md`](docs/roadmap-vocabulary.md))
- WordNet/CILI import pipeline is partially implemented
- Networking is functional but not hardened
- No stable public API yet

**What's missing:**
- Encryption (Tag 10 reserved but not implemented)
- Full CILI-based canonical keys for all seed sememes
- Multi-language support beyond English bootstrap
- Comprehensive test coverage for all subsystems
- Performance optimization for large libraries
- Documentation for contributors

**The cautionary context:** Projects with this level of ambition have a history of not shipping. Xanadu envisioned content addressing and bidirectional links in 1963 and is still not finished. Cyc has been hand-encoding common sense for 40 years. Croquet built a beautiful replicated object system that couldn't meet users where they were. Plan 9 was technically superior to Unix and didn't matter. These lessons are taken seriously — see [`docs/references/README.md`](docs/references/README.md#visionary-projects-and-cautionary-tales) for the full cautionary tales section.

The difference, hopefully, is shipping incrementally and in public rather than waiting for completeness.

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
core/               # Domain model (~2,750 files)
  item/             #   Item, IDs, Manifest, Relations, Components
  library/          #   Storage backends, TokenDictionary, indexing, seed vocabulary
  runtime/          #   Graph entry point, Librarian, Session, Scheduler
  network/          #   CG Protocol (P2P), Session Protocol, transports
  trust/            #   Signing, verification, key management
  policy/           #   PolicySet, PolicyEngine, AuthorityPolicy
  value/            #   Typed values, units, quantities, addresses
  language/         #   Sememes, Lexicon, QueryParser

english/            # English language support
  wordnet/          #   WordNet/LMF importers
  lexicon/          #   English lexicon and token resolution

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
| [`item.md`](docs/item.md) | Item structure, identity, lifecycle, composition |
| [`vocabulary.md`](docs/vocabulary.md) | Vocabulary system, dispatch, expression input |
| [`sememes.md`](docs/sememes.md) | Meaning units, parts of speech, WordNet/CILI anchoring |
| [`components.md`](docs/components.md) | Component system, types, modes, vocabulary facet |
| [`relations.md`](docs/relations.md) | Semantic triples, qualifiers, indexing |
| [`library.md`](docs/library.md) | Storage architecture, backends, bootstrap |
| [`presentation.md`](docs/presentation.md) | Rendering pipeline, scene system, style |
| [`trust.md`](docs/trust.md) | Trust matrix, moderation, reactions, policy-driven views |
| [`authentication.md`](docs/authentication.md) | Keys, signatures, signers, device-centric identity |
| [`protocol.md`](docs/protocol.md) | CG Protocol (P2P) and Session Protocol |
| [`cg-cbor.md`](docs/cg-cbor.md) | CG-CBOR encoding specification |
| [`content.md`](docs/content.md) | Content addressing, storage, deduplication |
| [`manifest.md`](docs/manifest.md) | Versioning, manifest format, signing |
| [`references/`](docs/references/) | Academic bibliography (65+ papers, 20+ topics) |

---

## Contributing

This project is in its early public life. The architecture is stabilizing but the surface area is large and there's plenty of room for collaboration — finding holes in the design, improving documentation, writing tests, or building on the component and game frameworks.

If any of this resonates, open an issue or start a discussion. Design critiques are as valuable as code — possibly more so at this stage.

---

## License

License will be formalized as the project matures. The intent is permissive open source.

---

*Common Graph has been a vision of Joshua Chambers for twenty years. This is the attempt that finally stuck. Built with [Claude Code](https://claude.ai/code). Intellectual lineage documented in [`docs/references/`](docs/references/).*
