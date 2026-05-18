# Common Graph

**A semantic base layer for computing.**

> Active construction site. The architecture is real, the code runs, but everything is changing constantly. If that bothers you, check back later.

---

## The Problem

Every layer of the computing stack is semantically inert.

A filesystem sees bytes at paths. An operating system sees processes and file descriptors. HTTP sees bytes at URLs. A database sees rows or documents. None of them know what anything *means*. The entire world's information infrastructure has zero native ability to answer the most basic question about any piece of data: *what is this about?*

The consequence is everywhere, and so pervasive it's invisible. Search engines exist because the web can't describe itself — third parties crawl billions of pages, guess at meaning from word frequency and link structure, and sell access to their guesses. Every API integration is a bespoke translation between systems that can't describe their own contents to each other. Every application reinvents its own vocabulary — one system's `author` is another's `creator`, another's `created_by`, another's `writtenBy` — and no layer of infrastructure connects them.

The key-value pair is computing's most ubiquitous pattern. But because keys are application-defined strings, they fracture the moment they leave the application that defined them. What's missing isn't a better search engine or a smarter metadata standard. What's missing is a *layer* — a base layer where meaning is structural, not decorative. Where creating data *is* creating semantic structure. Where the vocabulary is shared, grounded, and universal.

For the full argument — why retrofitting semantics onto existing layers can't work, what a semantic base layer requires, and why now — see [**The Case for a Semantic Base Layer**](docs/the-case.md).

---

## The Approach

Common Graph makes meaning structural. **Semantics are resolved at write time, not read time.** When you create or relate anything, the system resolves your intent to globally-anchored meaning *before the data is stored*. Every assertion, every relationship is grounded in **sememes** — universal units of meaning with stable identities derived from decades of computational linguistics ([WordNet](https://wordnet.princeton.edu/), [FrameNet](https://framenet.icsi.berkeley.edu/), [VerbNet](https://verbs.colorado.edu/verbnet/), [CILI](https://github.com/globalwordnet/cili)). The meaning isn't guessed later by a search engine — it's declared at the moment of creation, by the person who knows what they mean.

When you query "red shirt," you're not searching for the *words* "red" and "shirt" — you're searching for the *meaning* "a garment worn on the torso, color attribute red." Star Trek memes are a different sememe entirely. They simply don't match.

---

## The Architecture in One Page

Common Graph is built from one structural primitive and a small set of conventions for using it, and a reference system, where these primitives can each reference each other in meaningful ways.

### The datum

Everything is a **datum**: a head plus bindings, optionally signed.

```
{<head>, [<binding>, <binding>, ...] (, <signature>)}
```

A datum with no signature is a **body** — pure data, content-addressed, immutable. A datum with a signature is a **record** — an attestation over a body. Bodies and records are the only stored structures; frames, manifests, schemas, queries, and code items are all just bodies and records with different head choices.

A datum is mostly made of references.  The vast majority of leaf nodes in a datum are references to other datums, and in the rest of cases, they may be literals, or even nested datums.  This means datums form a graph structure, and the references are the edges.  The head of a datum is also a reference, which means datums are mostly typed by their heads.

A **frame** is a body whose head is a predicate. It makes a *semantic* assertion about meaning in the world.  "Tolkien authored The Hobbit" is a frame:

```
{@authored, [
  @AGENT → @tolkien,
  @THEME → @hobbit
]}
```

A **manifest** is a body whose head is an archetype and which carries an `@ITEM_ID` binding.  The `@ITEM_ID` binding names which item the manifest is for; the rest of the bindings are that item's current state.  The Hobbit's manifest might look like:

```
{@book, [
  @ITEM_ID  → @hobbit,
  @TITLE    → "The Hobbit",
  @AUTHOR   → @tolkien,
  @PUBLISHED → 1937
]}
```

Items persist across changes.  When a new edition ships or an attribution gets corrected, a new manifest is published with the same `@ITEM_ID` but updated bindings.  Each manifest is itself immutable like any datum; the *item* — the IID — is the continuity through versions.

A **value body** is a body whose head names a typed-value archetype and which carries *no* `@ITEM_ID` binding.  It's pure value — no identity, no lineage, no versioning.  A specific shade of red:

```
{@color, [@R → 255, @G → 0, @B → 0]}
```

Two bodies with the same RGB values *are* the same body — same structural hash, same DatumID.  There's no "which red"; values are immutable by virtue of having no identity to mutate.

A **schema** is a body whose bindings carry `!`-prefixed references — declaring what instances of an archetype should look like.  The Color archetype's manifest *is* the Color schema:

```
{@archetype, [
  @ITEM_ID → <color>,
  !@R → BETWEEN { @SOURCE → 0, @GOAL → 255 },
  !@G → BETWEEN { @SOURCE → 0, @GOAL → 255 },
  !@B → BETWEEN { @SOURCE → 0, @GOAL → 255 }
]}
```

The `!`-prefixed bindings are slot declarations.  An instance is valid when its `@`-prefixed bindings satisfy the matchers — here, when each channel's value lies in the `[0, 255]` range.  Same body shape as the value above; different head, different binding-prefix conventions; different role.  One primitive does the work of many.

### Five reference prefixes

References — pointers from one place in the graph to another — carry typing as their leading byte:

| Prefix | Meaning |
|---|---|
| `@` | concrete reference: use this exact item |
| `?` | query pattern: match anything fitting this type |
| `!` | schema slot: this is the expected shape |
| `~` | content hash: these exact bytes |
| `#` | datum hash: this exact structural datum |

`@hobbit` says "this book"; `?book` says "any book"; `!book` says "expects a book here." Same target, three relationships, three distinct prefixes — never combined. The prefix lattice completes what IPLD started: typed links, not just hashed pointers.

### Items as continuants

An **item** is a stable cryptographic identity around which a lineage of manifests accumulates. Documents, users, hosts, chess games, codebases — anything that needs to persist across versions is an item. Items are the *things that endure* through change; manifests are the *snapshots* of their states at moments.

Versioning, history, branching, merging — all expressed through `@FOLLOWS` bindings on each manifest naming its parent(s). No working tree is special; every edit is itself a commit. Two signers' disagreement about the "current version" is just two channels pointing at different VIDs — both valid, neither authoritative.

### Items as actors

When a frame addresses an item, the item is *potentially reactive*. The item declares which messages it accepts through `@HANDLES` bindings on its archetype's manifest:

```
@chess-game's manifest declares:
  @HANDLES → @move
  @HANDLES → @resign
  @HANDLES → @offer-draw
```

When a MOVE frame is submitted, the runtime finds chess game items via HANDLES and dispatches the move to them. Frames are messages; items are actors; predicates are message types. The whole runtime is one giant dispatch loop over frames; there's no separate RPC, event bus, or command system.

### Polyglot implementations

Code is just another body type. A **code item** has the Code archetype and language bindings:

```
{@code, [
  @ITEM_ID → <add-java-iid>,
  @IMPLEMENTS → @add,
  @JAVA:[ClassName] → "AddJava"
]}
```

A different code item implements the same archetype in Python:

```
{@code, [
  @ITEM_ID → <add-python-iid>,
  @IMPLEMENTS → @add,
  @PYTHON:[SourceCode] → "def evaluate(body): ..."
]}
```

Both realize `@add`. The runtime picks one based on what languages the host supports and what the user's trust matrix permits. The wire format is the contract; the implementing language is private to each code item.

### Encoding-agnostic by construction

Identity in Common Graph is *structural*, not encoded.

Two implementations using different encoding formats — CG-CBOR, a hypothetical CG-JSON, anything — produce different bytes for the same datum, but the same **DatumID**, because both compute the structural hash by walking the same data model. This is what makes the system pluggable at the encoding layer: new encodings can be added without breaking identity continuity, and two implementations using different wire formats can verify and deduplicate by structural identity alone.

The **canonical walker** is the protocol that produces DatumIDs. CG-CBOR is the first encoding format above it. Others can follow.

---

## What This Replaces

### Files and folders

| Files & Folders | Common Graph |
|---|---|
| Opaque byte stream — the OS can't interpret content | Typed bodies — the system knows what everything means |
| Named by path in a tree — one location per file | Discoverable by meaning — items exist in a semantic graph |
| No built-in authorship, versioning, or integrity | Every body is signed, versioned, content-addressed |
| Metadata is a sidecar (xattr, .DS_Store, EXIF) | Metadata IS bindings — first-class, queryable, signed |
| "Relatedness" means same folder or a hyperlink | Semantic frames — typed, signed, indexed, traversable |
| Application decides how to open it | Item carries its own vocabulary and presentation |
| Search by filename or full-text keyword | Query by meaning across the graph |

A folder is one way to group things — by containment in a hierarchy. Common Graph gives you every way: by authorship, by topic, by type, by time, by trust, by any semantic assertion anyone has made. And those groupings are themselves frames — signed, queryable, and extensible by anyone.

### The web, email, chat, messaging

A unified messaging substrate covers most of what these systems do separately:

- **Email** is signed frames with thread predicates and routing through trust.
- **Chat rooms** are streams of frames with different predicates (MESSAGE, REACTION, JOIN, LEAVE).
- **The web** is items presenting scenes — semantically self-describing replacements for HTML+CSS+JS.
- **Federated social networks** are the same frames different communities interpret differently.

Existing networks aren't displaced by force; they're **bridged**. CG speaks email at the boundary by translating SMTP/IMAP to frames; speaks ActivityPub to federated peers; speaks HTTP to the existing web. Adoption is gradual, not zero-sum.

See [`bridges.md`](docs/bridges.md) for the interop strategy.

---

## What You Can Do

**Find things by meaning, not keywords.** "All red shirts for sale within 50km" resolves SHIRT (garment sememe) + RED (color sememe) + FOR_SALE (commercial predicate) + spatial constraint. Star Trek references have a different sememe and don't appear.

**Publish without a platform.** Your content is a signed item on your device. Your identity is a cryptographic key, not an account. Your audience finds your work through trust relationships, not through a platform's algorithm.

**Moderate without an authority.** A "like" is a signed frame. A spam label is a signed frame. Everyone's trust policies produce different views of the same data — no appeals board, no opaque algorithm. If you trust someone, their assertions reach your view; if you don't, they don't.

**Converse across languages.** "Create" in English, "crear" in Spanish, "作る" in Japanese — same sememe, same action. The interface is semantic, not syntactic.

**Compute with real quantities.** `5m + 3ft` → `5.9144 m`. Units are sememes with dimensional metadata. Quantities are first-class typed values, not strings.

**Carry your data forward forever.** A document filed in 2026 stays identifiable in 2076 even if the encoding format has migrated. DatumIDs survive encoding changes; the meaning is what's identified, not the bytes.

**Sign forms once, redact for each viewer.** Critical for government, legal, and medical contexts: a document's structural hash stays the same whether or not specific bindings are visible. A FOIA-disclosed document with redactions is still verifiable as authentic; a tax return shared with a bank can have unrelated lines hidden without breaking signatures.

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
  → TokenDictionary (scoped lookup: focused item, session, active languages, universal)
    → Sememe (language-neutral meaning)
      → Composable notations (operators, functions, property access, ...)
        → Frame body (assembled, signed, submitted)
```

Words resolve to sememes. Sememes assemble into frames. Creating a frame IS the action — items observe new frames and react accordingly. "Move pawn to e4" assembles a MOVE frame; the chess game receives it and updates.

Word order is flexible because resolution is semantic, not positional. "Move pawn to e4" and "move to e4 pawn" produce the same frame — prepositions frame arguments by thematic role, not by position.

**But you don't have to type.** Items declare their own visual presentation. A chess game renders a board you click on. A document renders editable text. A chat room shows messages with a compose area. Clicking "reply" creates the same frame as typing "reply."

---

## Identity: Keys, Not Accounts

Your identity is a cryptographic key pair that lives on your device. No server needed. No account to create. No password to forget.

When a Librarian (the local runtime) boots for the first time, it generates an Ed25519 signing key. This key is the device's identity — it can sign manifests, assert frames, and prove authorship without asking anyone's permission. The private key never leaves the device.

Devices and people are separate identities. Your laptop has a key. Your phone has a key. *You* are a higher-level identity that authorizes devices through KERI-style key inception, rotation, delegation, and revocation — an append-only chain of cryptographic events that any verifier can replay independently. Lose a device? Revoke its key. Your identity survives because it's not tied to any one machine.

See [`authentication.md`](docs/authentication.md) for the full identity model.

---

## Trust: The Social Fabric

Trust isn't a security feature bolted on top — it's the organizing principle of the entire system.

Every assertion is signed. Trust isn't binary — it's policy-driven with thresholds, scopes, decay, and revocation. Trust policies live on items as configuration, inspectable and adjustable.

Trust determines who you sync with, whose assertions you accept, how far your queries propagate, and whose content appears in your graph at all. There is no separate "moderation" system because trust *is* moderation.

A "like" is a signed frame. A spam label is a signed frame. If Alice likes a post and Bob thinks Alice's like is astroturfing, Bob signs a frame targeting Alice's frame — because a frame can be about another frame. Everyone who trusts Bob more than Alice sees that signal. Everyone who trusts Alice more than Bob doesn't. No appeals process, no review board — just overlapping trust graphs producing different views of the same data.

See [`trust.md`](docs/trust.md).

---

## Storage: One Source of Truth

All bytes that need to survive a process restart live in one place: a content-addressed object store. Every datum, every content blob, every signature, every key — addressed by hash, stored as bytes, fetched by hash.

Everything else is *derived*:

- **Indexes** — `IID → manifest`, `predicate → frames`, `binding-target → frames`, `archetype-hierarchy → items`. Rebuildable from the object store.
- **Item directory** — IID-to-store lookup for multi-backend setups.
- **Token dictionary** — surface form to sememe, built from lexeme frames.

Indexes can be rebuilt at any time by walking the object store. The bytes are the truth; the indexes are accelerators. Want a new query pattern? Add a new index by walking the store. Corrupted index? Drop it, rebuild.

This is what keeps storage simple. The librarian doesn't manage schemas, doesn't migrate data, doesn't worry about cross-layer consistency. One layer that matters; everything else falls out.

See [`storage.md`](docs/storage.md).

---

## Runtime: Stage, Librarian, Session

The runtime has three layered concepts:

- **ItemStage** — the substrate. Hosts the polyglot environment (Java, Python, Lisp, JavaScript, …); owns the `run(handler, frame)` primitive; enforces capability constraints.
- **Librarian** — the backbone item. Owns storage, signing, the trust matrix, handler dispatch, the network listener. Hosts every active item.
- **Session** — the UI intermediary. Arranges views of running items; mediates user input. Three embodiments share one identity: in-process, local-bridge, remote.

Stage, Librarian, Session — substrate, runtime, UI. Items run on the Stage; the Librarian hosts items; the Session views items. Switching local↔remote never changes behavior, only latency.

See [`runtime.md`](docs/runtime.md) and [`scripting.md`](docs/scripting.md).

---

## Presentation: One Scene, Every Surface

Items declare their presentation through **scenes** — declarative, content-addressed structures built from three primitives:

- **Container** — structural: children and layout.
- **Text** — content: carries sememe references, resolved to the user's language at render time.
- **Body** — visual: model, image, shape, or glyph, with a fidelity chain from full 3D down to a Unicode character.

The same scene renders as perspective 3D with GPU-accelerated lighting, as flat 2D through Skia, or as text art in a terminal. Same items, same scene, different projections.

Text nodes carry meaning references, not hardcoded strings. A label referencing the Checkmate sememe renders as "Checkmate" in English, "将杀" in Mandarin, "Schachmatt" in German — same scene, same hash.

---

## Linguistic Foundation

Common Graph doesn't invent its linguistic backbone from scratch — it builds on decades of computational semantics research:

1. **[WordNet](https://wordnet.princeton.edu/)** — ~120,000 synsets with hierarchical concept relationships. Each synset becomes a sememe.
2. **[CILI](https://github.com/globalwordnet/cili)** — language-neutral concept mapping. English "dog," Spanish "perro," Japanese "犬" map to the same concept.
3. **[FrameNet](https://framenet.icsi.berkeley.edu/)** — ~1,200 semantic frames with frame elements and roles. The direct computational realization of Fillmore's frame semantics.
4. **[VerbNet](https://verbs.colorado.edu/verbnet/)** — ~300 verb classes with thematic role declarations. The empirical basis for CG's ~25 thematic roles.
5. **[ISO 24617-4 (SemAF-SR)](https://www.iso.org/standard/56866.html)** — the international standard for semantic role annotation.
6. **[UniMorph](https://unimorph.github.io/)** — morphological database for 100+ languages. "run/ran/running" all resolve to the same sememe.

These are *trust-weighted starting points*, not architectural foundations. Communities building on different ontologies are free to seed their own vocabularies; the protocol doesn't privilege any source.

See [`seed-vocabulary.md`](docs/seed-vocabulary.md).

---

## Standing on Shoulders

Common Graph integrates decades of prior work:

- **Content addressing** (Merkle 1979, Git, IPFS) — content identified by cryptographic hash
- **Frame semantics** (Fillmore 1968/1982, FrameNet) — assertions as filled predicate structures with thematic roles
- **Thematic role theory** (VerbNet, LIRICS/ISO 24617-4, Dowty 1991) — semantic participant roles grounded in standards
- **Computational linguistics** (WordNet, CILI, UniMorph, SemLink) — meaning as computable, multilingual structure
- **Speech act theory** (Austin 1962, Searle 1969) — utterances are actions, not just descriptions
- **Actor model** (Hewitt 1973) and **message passing** (Kay/Smalltalk) — independent entities communicating through messages
- **Capability security** (Dennis & Van Horn 1966, Miller 2006) — access as unforgeable tokens
- **Public-key cryptography** (Diffie & Hellman 1976, Bernstein/Ed25519) — identity without authority
- **DHT and P2P systems** (Freenet, Chord, Kademlia, Secure Scuttlebutt) — decentralized routing and storage
- **CRDTs** (Shapiro 2011) and **Merkle-CRDTs** (Tschudin 2019) — convergence without coordination
- **Local-first software** (Kleppmann 2019) — user-owned data, offline capability, collaboration without servers
- **KERI** — key event receipt infrastructure for self-sovereign identity
- **Polyglot runtimes** (GraalVM) — multiple languages in one process with shared memory and uniform interop

Each solved a piece of the puzzle. Common Graph's contribution — if it works — is the integration: a single data model where content addressing, frame semantics, cryptographic identity, multilingual vocabulary, encoding-agnostic structural identity, and local-first storage reinforce each other rather than existing as separate systems.

See [`docs/references/`](docs/references/) for the full academic bibliography.

---

## Project Status

This is an early-stage project. The architecture is settling fast; the code runs but isn't ready for production use.

**What works today:**

- Full item lifecycle: identity, manifests, signed records, version chains
- Datum primitive: bodies, records, frames, manifests as configurations of one shape
- Five-prefix reference scheme: concrete, query, schema, content, datum
- Encoding-agnostic structural identity via canonical walker (DatumID) + first encoding (CG-CBOR)
- Content-addressed object store with derived indexes
- TokenDictionary with scoped resolution
- Composable notations for parsing (operators, functions, property access, ...)
- HANDLES/IMPLEMENTS-based dispatch with trust-matrix selection
- ItemStage as polyglot substrate (GraalVM-based, currently Java + Python with more languages coming)
- KERI-parity identity model (inception, rotation, delegation, revocation)
- Ed25519 signing with key history
- Working game implementations (Chess, Set, Minesweeper)
- 3D rendering (Filament), 2D rendering (Skia), terminal rendering (JLine)
- English and German WordNet import

**What's next:**

- Wider polyglot rollout — bringing Python, Lisp, and JavaScript handlers up to first-class status
- The Parley protocol for librarian-to-librarian communication
- Text-pipeline parser fully wired through composable notations
- More substantial bridges to existing systems

**The cautionary context:** Projects with this level of ambition have a history of not shipping. Xanadu, Cyc, Croquet, Plan 9 — the lessons are taken seriously. The difference, hopefully, is shipping incrementally and in public rather than waiting for completeness.

---

## Building

```bash
./gradlew build          # Build the project
./gradlew test           # Run all tests (JUnit 5)
./gradlew run            # Run interactive shell
./gradlew fresh          # Run with fresh scratch dir
./gradlew scratch        # Run with persistent scratch dir
```

Requires **Java 25** with GraalVM polyglot support (via Gradle toolchain). Targets Java 21 bytecode for portability.

---

## Documentation

Detailed specifications live in `docs/`:

### Start here

- [**`the-case.md`**](docs/the-case.md) — the theoretical argument for a semantic base layer.

### Foundations

- [`datum.md`](docs/datum.md) — the unified primitive: body, record, frame, manifest.
- [`ref-scheme.md`](docs/ref-scheme.md) — the five reference prefixes (`@`/`?`/`!`/`~`/`#`).
- [`frames.md`](docs/frames.md) — frames as predicate-headed bodies.
- [`item.md`](docs/item.md) — items as continuants; identity and lineage.
- [`manifest.md`](docs/manifest.md) — versioning, FOLLOWS chains, signing.
- [`api.md`](docs/api.md) — HANDLES + IMPLEMENTS as the API surface.
- [`types.md`](docs/types.md) — the meta-archetype tree.

### Encoding

- [`canonical.md`](docs/canonical.md) — the structural walker that produces DatumIDs.
- [`cg-cbor.md`](docs/cg-cbor.md) — the first CG-capable encoding format.
- [`content.md`](docs/content.md) — content addressing and ContentID.

### Linguistic

- [`sememes.md`](docs/sememes.md) — meaning units; the linguistic backbone.
- [`values.md`](docs/values.md) — typed value bodies (Color, Quantity, dimensional types).
- [`language.md`](docs/language.md) — languages as items; lexemes and CILI.
- [`vocabulary.md`](docs/vocabulary.md) — the runtime token dictionary.
- [`seed-vocabulary.md`](docs/seed-vocabulary.md) — bootstrap pattern; application bundles.
- [`input.md`](docs/input.md) — the unified input pipeline.
- [`text.md`](docs/text.md) — parsing and rendering; composable notations.

### Runtime

- [`runtime.md`](docs/runtime.md) — Stage / Librarian / Session.
- [`scripting.md`](docs/scripting.md) — code items, polyglot, sandboxing.
- [`query.md`](docs/query.md) — queries as frames.

### Storage

- [`storage.md`](docs/storage.md) — object store, indexes, persistence.
- [`streams.md`](docs/streams.md) — append-only patterns for chat, logs, sensors.
- [`working-tree.md`](docs/working-tree.md) — filesystem materialization.

### Identity & network

- [`authentication.md`](docs/authentication.md) — keys and signing (KERI-parity).
- [`trust.md`](docs/trust.md) — trust matrix; subjective views.
- [`encryption.md`](docs/encryption.md) — encryption at rest and in transit.
- [`privacy.md`](docs/privacy.md) — privacy model.
- [`network.md`](docs/network.md) — peer-to-peer topology.
- [`protocol.md`](docs/protocol.md) — Parley protocol.

### Presentation & interop

- [`scene.md`](docs/scene.md) — scene model; three primitives.
- [`bridges.md`](docs/bridges.md) — interop with email, web, federated systems.
- [`examples.md`](docs/examples.md) — worked use cases.

### Style

- [`STYLE.md`](docs/STYLE.md) — documentation conventions; canonical examples.

---

## Repository Structure

```
core/               Domain model
  datum/              Body, Record, Frame, Manifest
  item/               Item base, lifecycle, manifests
  identity/           Signers, vaults, KERI-style key events
  canonical/          Structural walker, hash tree
  encoding/           CG-CBOR codec
  id/                 ItemRef, TypeRef, SchemaRef, ContentRef, DatumRef
  library/            Storage, indexes, walkers
  runtime/            ItemStage, Librarian, Session
  network/            Parley protocol
  language/           Sememes, lexemes, thematic roles
  text/               Parsing pipeline; composable notations
  value/              Typed values (Color, Quantity, dimensional types)
  operator/           Operators (Add, Multiply, comparison, logic, ...)
  quality/            Presentation vocabularies (layout, typography, spatial, ...)

english/            English language support (WordNet import, morphology)
german/             German language support
games/              Chess, Set, Minesweeper, ...
ui/                 Filament (3D), Skia (2D), JLine (TUI), input handling
web/                Web client (WebSocket session handler)
lang-import/        Multilingual import tooling

docs/               Architecture documentation
```

---

## Contributing

The architecture is stabilizing but the surface area is large. Design critiques are as valuable as code at this stage. If any of this resonates, open an issue or start a discussion.

---

## License

License will be formalized as the project matures. The intent is permissive open source.

---

*Common Graph is a twenty-year vision of Joshua Chambers. Built with [Claude Code](https://claude.ai/code). Intellectual lineage documented in [`docs/references/`](docs/references/).*
