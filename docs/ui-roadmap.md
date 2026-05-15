# UI Roadmap — Primer

The codebase has been through several large architectural shifts in the past two weeks that aren't yet fully reflected in the older `docs/*.md` files. This document captures the current shape so UI design starts from the right model rather than the historical one.

Read this **once**, then use it as the navigation anchor. Treat the older docs as authoritative for the topics they cover *unless* this primer or `text.md` / `datum.md` / `frames.md` says otherwise.

---

## What's been locked since this primer was first written

A handful of design points have settled since the rest of this doc was drafted. Treat these as authoritative; the corresponding paragraphs later in the document and in older docs trail behind.

- **One hierarchy, usage-based.** There is no structural distinction between "predicate items" and "archetype items." The same item-shape carries both roles. The only structural marker is whether the item's `EXPECTS` declares an `ITEM_ID` binding — that's what makes it usable as an archetype (a template for *instances*). A predicate's EXPECTS, by contrast, declares the shape of *frames* using it. Fuzziness here is intentional: some items legitimately play both roles, and the system should not pretend otherwise. See `docs/item.md` "Predicate, archetype, or both?" for the worked-out version.

- **The API trio is `EXPECTS` / `IMPLEMENTATION` / `HANDLES`.**
  - **EXPECTS** lives on a predicate or archetype and describes the shape of its instances (frames for a predicate, items for an archetype).
  - **IMPLEMENTATION** is on each individual item — the manifest binding pointing at the code (Java class name, or `@<code-item-ref>` for distributed/polyglot implementations) that *runs* this item.
  - **HANDLES** is endorsed on archetypes — "items of this archetype process these predicates" — and inherited by their instances. Predicates don't carry HANDLES; predicates declare *what frames look like*, not *what receives them*. The exceptional case is self-handling operator predicates (ADD, MULTIPLY) where the predicate's behavior is a pure function of its bindings.
  - See `docs/vocabulary.md` "The EXPECTS / IMPLEMENTATION / HANDLES trio" for the canonical write-up.

- **Composable notations, not a Language inheritance hierarchy.** Parsing participants are small, focused notation objects (`OperatorNotation`, `FunctionNotation`, etc.), not subclasses of a single `Language` superclass. A Language is composed by picking which notations it brings into the consensus circle, plus its own lexical rules. Literals (numbers, strings) are handled by the base notation as a universal fallback; they don't need their own participant. This replaces the older "Language as base class with overridable parse()" model — Languages still exist as items, but their parsing surface comes from composing notations rather than being baked into the class. See `docs/text.md` "Composable notations" for the model.

- **ItemStage is the runtime substrate.** Librarian and Session are **items hosted on an `ItemStage`**, not standalone things. The Stage owns the polyglot environment (GraalVM contexts, capability enforcement, the run primitive), probes for GraalVM in its constructor, and degrades gracefully to Java-only when polyglot isn't available. Stage doesn't *select* which handler runs (Librarian's trust matrix does that) — Stage *executes* the chosen one. The bootstrap order is: entry-class `main()` → construct `ItemStage` → construct `Librarian` (passing the stage in) → first frame evaluation. See `docs/runtime.md` for the substrate model.

- **Universal handler contract: Frame in, value-or-Frame out.** All handlers — Java, Python, Clojure, anything else — present the same shape to the Stage. The wire/storage contract is the predicate; the language-specific implementation is private. This is what makes polyglot drop-in work: a Rust ChessGame and a Java ChessGame endorse the same HANDLES frames, and Stage runs whichever the manifest's IMPLEMENTATION binding points at.

- **Bootstrap-via-annotation, runtime-via-graph-data.** `@Seed.*` annotations produce graph data (handle frames, expects frames, etc.) at startup. Once the data is in the graph, the runtime consults it directly — annotations are never read again. This means anything declarable via annotation is also declarable via running graph operations; the two paths converge on the same data shape.

---

## The mental model

Everything in Common Graph is one shape:

```
Datum = head + bindings [+ signature]
```

- A **body** is `head + bindings`. Content-addressed. Immutable.
- A **record** is `head + bindings + signature`. Attestation over a body.
- A **frame** is the runtime aggregate of a body and its records. Frames have no CID of their own — they are not serialized; the body and records are.
- A **manifest** is a body whose head is an archetype and which carries an `ITEM_ID` binding. Bodies are valid manifests iff they carry that binding.

Read `docs/datum.md` and `docs/frames.md` for the canonical definitions.

The frame primitive is **`predicate + role:[qualifiers] → target`**. Everything from "Tolkien authored The Hobbit" to "user pressed Enter" to "this node is a Container with these children" expresses as a frame.

## Items as actors

This is the load-bearing idea for UI architecture:

- **Frames are messages.**
- **Items are actors.**
- **Predicates are message types.**

An item's behavior is the set of predicates it handles. A handler is a method (today Java, eventually polyglot) marked with `@Seed.Handler(predicate = SOME_PREDICATE.KEY)`. When a frame whose head is `SOME_PREDICATE` reaches the item, the handler fires. Return values become response frames.

The handler set is **queryable as data**: items publish `HANDLES` frames endorsed by their manifest declaring `(predicate, handler-method, attributes)`. Today the bootstrap discovers these via reflection on `@Handler` annotations; eventually the canonical form is the HANDLES frames themselves, and polyglot handlers (Python, Clojure, Wasm) publish their own HANDLES without going through reflection. UI dispatch, introspection, completion, and capability discovery all walk HANDLES frames.

## The "second great unification" — what dissolved

In the older docs, you'll see distinct subsystems for: peer protocol, session protocol, scene model, command parser, query parser, input pipeline, bridge handlers. Most of those distinctions have collapsed. The current picture:

- **Protocol = frame stream.** There is no separate control plane. After a one-shot "codec point-and-grunt" handshake, a connection carries **arbitrary Datums** — frames, blobs, encrypted envelopes, raw values. Auth, capability negotiation, session setup, RPC, pub/sub — all just frames. The unified transport is **Parley**. `docs/protocol.md` is stale; treat the Parley package (`network/parley/`) as the truth.

- **Input = frame assembly.** A keypress, a click, a typed expression, an inbound chat message all run through the same parsing pipeline (`text/` package) that produces a `FrameMap`, becomes a frame, and is submitted to the librarian for handler dispatch. There is no separate command parser or input event class hierarchy. `docs/input.md` and `docs/text.md` describe this.

- **Scene = frames.** A scene tree is a tree of frames (datums) carried as `CONFIG` bindings on items, predicates, or archetypes. The Java `SceneNode` class hierarchy that `docs/scene.md` describes is one possible *runtime materialization* of that tree — it's an implementation view, not the canonical model. The renderer's job is to walk frames, resolve sememes to the user's language at paint time, and emit pixels (or terminal glyphs, or 3D geometry). **Treat the scene tree as data, not classes.**

- **Vault produces frames.** Cryptographic events (INCEPTION, ROTATION, DELEGATION, REVOCATION) are emitted from the vault as already-signed `Frame` objects. The Signer is a thin item wrapper that persists them. `docs/authentication.md` is mostly current; the design memo `design-vault-and-signer` in the architecture notes captures the latest.

- **Session ≠ a protocol.** It used to be one. Now Session is an **item** (`runtime/session/Session.java`). Read its class javadoc — it's the multi-principal / multi-device / multi-librarian persistent context where users meet. It has three runtime embodiments sharing one IID:
  - `Session` — the server-side embodiment running inside a Librarian.
  - `LocalSession` — in-VM client view; method calls dispatch directly to a `Librarian` reference.
  - `RemoteSession` — out-of-process client view; method calls become frames over Parley.

  Same item, same IID, same handlers; only the transport between the client face and the librarian differs. **Local-first by default, remote when the path crosses a process boundary.** Switching local↔remote should never change behavior, only latency.

## The Librarian/Session boundary — the key UI architectural fact

The UI exists on the **Session side**. The graph lives on the **Librarian side**. The boundary between them is the contract the UI is being built against.

- **Librarian responsibilities**: storage (Library = DataStore + IndexStore), signing (it's a Signer), handler dispatch, network (owns its Parley), holds the canonical item cache, owns the bootstrap vocabulary. Treat the Librarian as the *backend*.

- **Session responsibilities**: renders, captures input, holds presence state (`PresenceVocabulary`), holds the per-user device bindings, runs the input parser, ships frames to the Librarian, receives response frames, updates its view. Treat the Session as the *frontend*.

- **The wire between them is frames.** No bespoke RPC. Submit a frame. Get response frames back. The actor model again. `RemoteSession.submit(frame)` serializes through Parley; `LocalSession.submit(frame)` calls the librarian directly. Same call, different transport.

This means the UI's view of the world is: hold a `Session`, call methods on it (which under the hood emit frames), receive frames back, react to them. There is no separate "model layer," "controller," "RPC client," or "event bus." There is the session, the librarian, and frames between them.

## What the UI must produce, in shape

For each item that needs a UI:

1. **A scene-frame tree** describing how to render it. Three logical primitives (still useful as a mental shorthand): Container (structural), Text (content), Body (visual). But each is a frame with bindings — not a Java class. The renderer materializes them platform-appropriately.

2. **A handler set** — what predicates the item accepts. Click frames, keystroke frames, drag frames, etc., all addressed by predicate. The item's `@Handler`-annotated methods (or polyglot equivalents) handle them.

3. **A presentation policy** — CONFIG bindings declaring retention, presentation defaults, keybindings (`@Scene.Key` annotations exist today, generate frames eventually), and so on. Per `docs/scene.md`'s "scenes are CONFIG" principle — which is still correct.

## The chat vision (a concrete UI target)

The first major UI deliverable is the **room** — heterogeneous semantic frame streams as the primitive social space.

Key idea: a room is not a "message log." A room is a stream where frames of many predicates (MESSAGE, ROFL, QUESTION, REQUEST, INVITATION, REACTION, MUTE, READ, EDIT, …) arrive in causal order. Each frame renders according to its predicate's scene. Macros are themselves predicates with declarative scene templates. The "just typing words" UX principle: a user types text, the parser figures out which predicate they meant, the assembled frame goes to the room. Higher-confidence interpretations win; ambiguous inputs surface tentative frames in the UI for the user to confirm.

This is the bridge target — the first non-trivial concrete UI need. If you can render a room well, the rest of the system follows.

## Polyglot intent (don't lock to Java)

The runtime is on GraalVM. The current code is Java-only, but the architecture is deliberately polyglot:

- Handlers can be code in any GraalVM language. Today they're discovered via Java reflection on `@Seed.Handler`; tomorrow they're discovered via HANDLES frames pointing at code items in arbitrary runtimes.
- Trust matrix decides what runtimes are allowed to execute under what permissions.
- The UI should never assume handler = Java method. It should assume handler = something that consumes a frame and produces frames.

For UI work this mostly means: don't hardcode Java class names anywhere user-visible; design the scene/input pipelines to work against frame-shaped data, not class-shaped data.

## What's on disk vs. what's in the docs

**Trust this primer plus these docs as authoritative for current truth:**
- `docs/datum.md` — datum primitive
- `docs/frames.md` — frame primitive
- `docs/text.md` — parsing & rendering (the bidirectional FrameMap design)
- `docs/input.md` — input pipeline
- `docs/manifest.md` — manifests
- `docs/authentication.md` — keys and signing
- `docs/storage.md` — library & indexes
- `docs/the-case.md` — the white paper / vision

**Treat as partially stale; read with caution:**
- `docs/scene.md` — three-primitives idea is right; the "Java SceneNode tree" framing is the older implementation view, not the canonical model. The newer view is scene-as-frames stored as CONFIG.
- `docs/protocol.md` — superseded by Parley. Use the Parley package for current shape.
- `docs/network.md` — older topology view; the routing primitives are right, the protocol names are not.
- `docs/working-tree.md`, `docs/streams.md`, `docs/content.md` — mostly fine but some details lag.

**Doesn't exist yet but should, eventually:**
- A canonical `docs/scene.md` rewrite that leads with "scenes are frames."
- A `docs/parley.md` documenting the current transport.
- A `docs/session.md` for the runtime embodiment trichotomy.

If UI work surfaces enough new shape, write those.

## Where to look in code

Anchors for navigation:

- `datum/` — Datum, Body, Record, Frame, Binding, BindingTarget, builders.
- `item/` — Item, Manifest, SeedProcessor (bootstrap).
- `identity/` — Signer, Vault, MultiKey, VarSig, IdentityVocabulary, vault/InMemoryVault.
- `runtime/librarian/` — Librarian, LibrarianVocabulary (Lookup/Delete/Create predicates).
- `runtime/session/` — Session, LocalSession, RemoteSession, SessionOptions, SessionVocabulary, PresenceVocabulary.
- `runtime/host/` — Host (the physical machine context), HostVocabulary.
- `runtime/RuntimeVocabulary.java` — Java/Python/JavaScript/Rust/Clojure as runtime archetypes; Construct, Code.
- `network/parley/` — Parley, RemoteConnection, NoiseTunnel, Tunnel, ParleyVocabulary.
- `text/` — TokenLattice, FrameMap, FrameDraftMerger, ParseEngine, AnchorTable.
- `language/` — Language base class, ThematicRole, LexicalVocabulary, PrepositionVocabulary, PartOfSpeech, GrammaticalFeature.
- `operator/` — Operator base class, NotationVocabulary, math/, logic/, compare/, set/, string/, flow/.
- `canonical/` — Walker, HashTree, Node, Order, Layout, Scope (encoder-agnostic structural primitives).
- `encoding/` — CgCbor, Encoding interface, Digest, TextBase.
- `library/` — Library, DataStore, IndexStore subpackages, RocksDB/MapDB/SkipList backends.
- `ui/scene/` — current scene-model implementation. **Many of these classes are legacy / mid-migration.** Read with skepticism; not all of it survives the scene-as-frames rewrite.

The `:ui` Gradle module hosts the platform renderers (Filament 3D, Skia 2D, JLine TUI). Most of what's there is older work that pre-dates the unification, but the spatial/2D rendering capability is real.

## Constraints and gotchas

- **No tabs.** Accordion model. See the project CLAUDE.md.
- **Every item has its own prompt.** Typing into the prompt invokes the parser → frame → handler pipeline for *that* item.
- **No IEEE 754 floats anywhere.** Use `BigDecimal` or `Rational`. CG-CBOR forbids them for deterministic hashing.
- **No hardcoded text.** Text nodes carry sememe references; language resolution happens at render time.
- **Lombok fluent accessors.** `item.iid()` not `item.getIid()`.
- **Local-first.** The path through `LocalSession` is the default; `RemoteSession` is the same code path with a different transport.
- **Many `*Old.java` and legacy classes still exist** in storage, network, and UI packages. They are scheduled for deletion but currently survive as references during the cutover. Don't add to them; if you touch them, prefer to migrate.

## What NOT to build

- A separate session-protocol message hierarchy. There are frames.
- A separate command-parser. There is the text pipeline.
- A separate scene data model. Scenes are frames.
- A separate input event class tree. Input events are frames.
- A separate RPC layer. There is Parley + frames.
- Tabs. Ever.

## First proof-of-life targets, in rough order

1. **One item in one window, in-VM.** Spin up a `Librarian.inMemory()`, attach a `LocalSession`, render the librarian's own item (it's a Signer, it has a manifest, it can show *something*). Prove the scene-as-frames path end-to-end.

2. **The prompt.** Wire the parser pipeline so typing into a prompt and pressing Enter produces a frame, the librarian dispatches it, and the response renders. The simplest non-trivial dispatch is LOOKUP — type a word, see token postings.

3. **Two items side by side.** Validate the accordion model layout, drag-to-expand, no-tabs constraint.

4. **The room.** A heterogeneous frame stream rendered as a chat-like view. Multiple predicates with different scenes interleaved by time. This is the first real test of scene-as-frames at scale.

5. **Remote session.** Same client code as step 1, but the Librarian is in another process. The wire is Parley. Prove the local↔remote symmetry.

Each of these has design tension to work through with the human. Don't try to design them all up front — get one running, learn, repeat.

---

When in doubt, ask. The architecture has moved fast and the docs lag. The conversational context with the human is often the freshest source of truth.
