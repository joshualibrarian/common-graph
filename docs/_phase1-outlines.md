# Phase 1 — Outlines

Working document. One outline per doc in Phase 1, plus the two new docs (`ref-scheme.md`, `api.md`). Section headings with one- or two-sentence summaries. Review/redirect here before any full rewriting.

Phase 1 covers the foundational primitives that every other doc references. The whole rewrite stands or falls on how clean these come out.

The seven docs in Phase 1, in **proposed writing order** (each builds on the previous):

1. [`ref-scheme.md`](#1-ref-schememd-new) — the prefix lattice (must come first; every other doc uses it)
2. [`datum.md`](#2-datummd-refresh) — the unified primitive
3. [`frames.md`](#3-framesmd-heavy-rewrite) — frames as predicate-headed datums
4. [`manifest.md`](#4-manifestmd-refresh) — manifests as archetype-headed datums
5. [`item.md`](#5-itemmd-rewrite) — items as identities that carry manifests
6. [`api.md`](#6-apimd-new) — HANDLES + IMPLEMENTS, dispatch, polyglot
7. [`types.md`](#7-typesmd-refresh) — the meta-archetype tree

Style: present-tense vision-as-reality, no Java, abstract data-shape notation throughout, length target 5–15 KB.

---

## 1. `ref-scheme.md` (NEW)

Target length: 6–8 KB. Establishes the five reference prefixes as a first-class primitive.

**Opening.** A reference in Common Graph carries not just *what* it points at but *how* it points. The five prefixes are the typing system for links.

**The five prefixes.**

- `@` — concrete reference (inherit). Use this exact item; runtime materialization follows.
- `?` — query / pattern. Match anything fitting this shape.
- `!` — schema / template. This slot's expected shape, for declaration purposes.
- `~` — content hash. Bytes addressed by their digest.
- `#` — datum hash. A structural datum identified by its merkle hash.

**Item prefixes vs content prefixes.** `@` `?` `!` work on items (named, identity-bearing). `~` `#` work on content (bytes/structure). The two halves of the lattice serve different layers.

**Shape vs semantics.** `?` and `!` describe structure only — what shape something has, not what it means. Semantic relationships (HANDLES, IMPLEMENTS, CONTAINS) live in the role of the binding, never in the prefix.

**Composability.**

- A schema-prefixed binding can constrain its target with a query prefix: `!@LHS → ?@number` ("expect an LHS binding whose target matches the Number pattern").
- A query can match across schema-references when validating instances against a template.
- The five prefixes never stack on the same reference; each reference has exactly one prefix.

**Worked examples.** Color schema and Color instance shown side by side, illustrating the prefix difference. The Add predicate as schema vs an Add frame as instance. A pattern-match query for "any move by white."

**Encoding.** Each prefix maps to a CBOR tag; the wire format is fixed. (Brief — full encoding details live in `cg-cbor.md`.)

**Connection to prior art.** What IPLD's content-addressed links did, what they didn't reach (semantic typing on the link itself), and how the prefix lattice completes the picture. One short subsection.

---

## 2. `datum.md` (REFRESH)

Target length: 8–12 KB. The current doc is mostly right; the rewrite tightens the voice and threads the five prefixes through.

**Opening.** Common Graph is built from one structural primitive: the datum. Every body, every record, every frame, every manifest is a configuration of this primitive.

**Anatomy.** Head + bindings (+ signature when attributed). The head names what the datum is *of*; bindings name what the datum *carries*; signatures attest. One shape, three positions.

**Body vs Record vs Frame.**
- **Body** — two-element form: head + bindings. Pure data, never self-signed, content-addressed.
- **Record** — three-element form: head + bindings + signature. An external attestation over a body.
- **Frame** — runtime aggregate of a body and any number of records.

**Manifest as a special body shape.** A manifest is a body whose head is an archetype and which carries an `ITEM_ID` binding. The wrapper concept dissolves — the structural identity is just "body with these particular bindings."

**Identity and addressing.** Each datum has a DatumID (structural Merkle hash) and a ContentID (canonical bytes hash). The two coincide on simple datums and diverge under transformations like redaction. (Brief, reference `cg-cbor.md` for the encoding mechanics.)

**Why one primitive.** Same encoding, same indexing, same protocol, same handler dispatch. Adding capability to the system is adding vocabulary, not adding kinds of structure.

**The prefix lattice on bindings.** Bindings hold targets; targets carry references; references take one of five prefixes. The same datum shape serves data, schema, and query depending on the prefixes in its bindings.

**Worked examples.** Color body / Color schema (head identical, prefixes differ). Tolkien-authored-Hobbit body. A redacted body.

---

## 3. `frames.md` (HEAVY REWRITE)

Target length: 10–14 KB. The current doc is huge (44 KB) and predates the schema-IS-the-thing principle. Most of it gets thrown out; the parts that survive are the frame-as-message framing and the worked examples.

**Opening.** A frame is a datum whose head is a predicate. Predicates are the message types; their bindings are the parameters. Items receive frames the way actors receive messages.

**The predicate IS the schema.** A predicate's own manifest carries the shape of frames that use it as schema-prefixed bindings (`!@LHS`, `!@RHS`, etc.). There is no separate EXPECTS predicate; the predicate's own bindings describe its instance shape.

**Frame mechanics.**
- Head names the predicate.
- Bindings supply the predicate's expected slots, plus optional contextual bindings beyond the schema.
- Targets may be references (any of the five prefixes), literals, or nested datums.

**Frame lifecycle.**
- Assembled — built from input (user typing, network arrival, programmatic construction).
- Submitted — handed to the librarian for routing.
- Routed — referenced items receive `onFrameAssembled` notifications; archetypes whose HANDLES match get dispatched.
- Endorsed (optionally) — pinned to an item's manifest via a binding referencing the frame's datum hash.

**Roles and qualifiers.** A binding's role is a sememe naming the semantic function (THEME, AGENT, GOAL, NAME, LOCATION). Qualifiers are sememes narrowing or distinguishing same-role bindings (`NAME:[ENGLISH, LEMMA]`). One mechanism, two layers.

**Concrete vs schema frames.** Instance frames carry concrete bindings (`@LHS→5`). Schema-flavor uses of the same predicate appear in other items' manifests as schema-prefixed references that pull in the predicate's structural template.

**Endorsement.** Items can endorse frames by binding them on their own manifest. The binding's role names *why* the endorsement exists (HANDLES, ENDORSES-as-content, ENDORSES-as-attestation, etc.). The same frame may be endorsed by many items, each with their own meaning.

**Worked examples.** Add frame (operator). Tolkien-authored-Hobbit (relationship). A chess Move. A LOOKUP frame (ephemeral).

**What this doc replaces.** The current frames.md mixes too many concerns — the new one strictly covers what a frame *is*. Vocabulary, parsing, dispatch, and the runtime details move to their own docs.

---

## 4. `manifest.md` (REFRESH)

Target length: 6–10 KB. The current doc is short and mostly right; the refresh aligns it with the new implementation-binding shape and the prefix lattice.

**Opening.** A manifest is a body that represents a specific version of an item. It carries the item's identity, structure, and the references that point at content this version endorses.

**Anatomy of a manifest.**
- Head — the archetype this is an instance of.
- ITEM_ID binding — the item's stable identity.
- FOLLOWS bindings — parent versions (zero for inception, multiple for merges).
- Endorsement bindings — references to frames this version carries as content.
- Implementation bindings — references to the code that runs this item.
- Other bindings — config, presentation, anything else relevant to this version.

**Archetype manifests vs instance manifests.** An archetype's own manifest carries schema-prefixed bindings declaring what its instances should look like. An instance's manifest carries concrete bindings filling in those slots.

**Implementation bindings.** The role is the language sememe (Java, Python, Lisp). The qualifier is the form (ClassName, SourceCode). The target is the actual reference (text, content hash). The full data tells the runtime which language, which form of code, and where to find it.

**Identity, version, content.**
- ItemID — stable across versions.
- VersionID — the manifest body's structural hash.
- ContentID — for the body's canonical bytes (used in storage).

**Signing.** A manifest body is hashed, the hash is signed, the signature lands in a record alongside the body. The Frame containing both is the canonical version representation.

**Multiple implementations on one manifest.** An item may declare implementations in multiple languages; the trust matrix picks one at runtime. The Stage materializes whichever is selected.

**Worked example.** A ChessGame archetype's manifest (with schema bindings + HANDLES). A specific chess-game instance's manifest. A code item's manifest (with concrete implementation binding).

---

## 5. `item.md` (REWRITE)

Target length: 10–14 KB. The current doc is mostly correct but predates the schema-IS-the-thing model and the new API surface. This is the central architectural doc; it needs to land cleanly.

**Opening.** An Item is a stable cryptographic identity around which frames cohere. Documents, users, hosts, conversations, games, codebases — all items. Items don't live at paths or URLs; they exist by identity and are found by meaning.

**Item identity.** A 32-byte multihash. Persists across all versions of the item. Most items have random IIDs; bootstrap concepts use deterministic IIDs derived from canonical keys.

**The item is the accumulation of its frames.** A chess game is an item whose accumulated MOVE frames make it a game-in-progress. A book is an item whose AUTHORED, TITLE, and TEXT frames make it a book. The frames are the data; the item is the identity they cohere around.

**Versions.** Each commit produces a new manifest body, hashed, signed. The VersionID points at that specific snapshot. History is the chain of manifest bodies linked by FOLLOWS bindings. (Detail in `manifest.md`.)

**Predicate, archetype, or both?** The same item-shape plays multiple roles depending on its EXPECTS-style declarations. Items whose schema declares an ITEM_ID slot are archetypes (their instances are items). Items whose schema describes binding-shape only are predicates (their instances are frames). One hierarchy, usage-based labeling. (Carried forward from current doc.)

**Items as actors.** Frames are messages; items are actors; predicates are message types. An item declares the messages it accepts via HANDLES bindings on its manifest. (Brief — full mechanics in `api.md`.)

**Items as implementations.** An item with concrete implementation bindings (Java/Python/Lisp + ClassName/SourceCode) realizes some predicate or archetype's behavior. Its manifest declares which one via IMPLEMENTS. (Brief — full mechanics in `api.md`.)

**Lifecycle.**
- Created — initial manifest minted, signed, persisted.
- Loaded — manifest fetched from storage, instance materialized on demand.
- Edited — frame additions, modifications, removals.
- Committed — new manifest body built, signed, becomes the new head.

**Composition.** Items compose behavior from frames. No special "chat room" or "shared folder" type — everything is assembled from predicates and bindings. The set of EXPECTS-shaped bindings on an archetype defines what its instances *are*.

**Worked examples.** A ChessGame instance. A code item. A simple document.

**Cross-references.** Pointers to `manifest.md`, `frames.md`, `api.md`, `ref-scheme.md` for the layers below.

---

## 6. `api.md` (NEW)

Target length: 8–12 KB. Consolidates what's currently scattered across `item.md`, `vocabulary.md`, and `scripting.md`. The unified API surface story.

**Opening.** An item exposes its behavior through exactly two role-bindings on its manifest: HANDLES (what messages it processes) and IMPLEMENTS (what concept it realizes). Together these describe the entire API contract.

**HANDLES.** A binding whose role is HANDLES and whose target is a predicate item: "I receive frames whose head is this predicate." Multiple HANDLES bindings give an item a multi-message API.

**IMPLEMENTS.** A binding whose role is IMPLEMENTS and whose target is an archetype or predicate item: "I am a realization of this concept." Code items use IMPLEMENTS to claim "this code runs *this* archetype."

**Dispatch flow.**
1. A frame is assembled and submitted to the librarian.
2. The librarian inspects the frame's head (a predicate).
3. The librarian finds items that HANDLES this predicate, either directly or by archetype inheritance.
4. The trust matrix scores candidate handlers; one is selected.
5. The Stage materializes the chosen item (using its IMPLEMENTS-linked code item).
6. The item's behavior runs, possibly producing reply frames.

**Polyglot.** HANDLES and IMPLEMENTS are language-neutral declarations. The code that actually runs lives on the code item; its implementation binding names the language and form. Java handlers run via the JVM; Python handlers run via GraalVM; future languages plug in through the same Stage interface. (Detail in `runtime.md` and `scripting.md`.)

**Convention vs. configuration.** A code item's manifest declares the implementation form (Java + ClassName, Python + SourceCode). The handler-method names are derived by convention from the predicate identities. Items that want unusual dispatch can add explicit handler bindings to override the convention.

**Archetype inheritance.** A ChessGame archetype declares HANDLES; specific game instances inherit those declarations. An item that IMPLEMENTS ChessGame doesn't need to redeclare HANDLES — the contract flows through.

**Worked example.** The Add predicate's bindings. AddJava and AddPython as sibling code items, each IMPLEMENTS Add. A frame arrives, the trust matrix picks one, the Stage runs it. Both produce the same answer through entirely different language runtimes.

**Why two roles, no more.** Earlier designs had EXPECTS, EXTENDS, INSTRUMENT, and other API-related predicates. All dissolved. EXPECTS is now schema bindings on the predicate's own manifest (the schema IS the thing). EXTENDS is now archetype-of-archetype hierarchy (an item's head). INSTRUMENT is now a convention on the implementation. The API surface needs only two relationships: "I handle these" and "I am one of those."

---

## 7. `types.md` (REFRESH)

Target length: 6–10 KB. Aligns the meta-archetype hierarchy with the now-locked schema model.

**Opening.** Common Graph's type system has one structural primitive (datum) and a small set of meta-archetypes that establish the universal rules. Every item is an instance of exactly one archetype; archetypes are themselves instances of meta-archetypes; the chain bottoms out at Archetype itself.

**The meta-archetype tree.**
```
Datum
  └── Body
        └── Value (cg.archetype:value)
              ├── Color
              ├── Quantity
              │     ├── Length, Mass, Time, …
              ├── Point
              └── …
```
Bodies whose head names a Value-derived archetype carry the typed slots that archetype expects.

**Archetype, Predicate, Sememe.**
- **Archetype** — its instances are items; its schema describes instance manifests.
- **Predicate** — its instances are frames; its schema describes frame bindings.
- **Sememe** — a meaning-anchor; a base class that both archetypes and predicates derive from.

The structural distinction is usage-driven: ITEM_ID in the schema → archetype; no ITEM_ID → predicate. One hierarchy, two roles.

**Schema declaration via the `!` prefix.** Schemas live on the archetype's or predicate's own manifest as `!`-prefixed bindings. No separate EXPECTS frame. (Detail in `ref-scheme.md`.)

**Validation.** When an item is committed or a frame is assembled, its bindings can be cross-checked against its head's schema. Validation reports conformance, not legality — even non-conforming items can be stored; the system surfaces the mismatch rather than rejecting.

**Inheritance.** An archetype can declare its parent archetype via its head. ChessGame's head is Game; Game's head is Activity; etc. Schema bindings can be added at each level; instances of ChessGame are also (structurally) instances of Game and Activity.

**Values vs entities.** Values (Color, Quantity, Length) are bodies that *are* the data — their head names their type, their bindings are their components. Entities (items) are bodies that *refer to* the data via their identity; their frames are the data.

**Worked examples.** Color as a Value. ChessGame as an Archetype. Add as a Predicate. Item as the universal-parent Archetype.

---

## After Phase 1

With these seven docs landed, every other doc in the system has a stable foundation to reference. Phase 2 (encoding) is light and short. Phase 3 (linguistic) is the next substantive consolidation pass.

The white paper (`the-case.md`) stays untouched until Phase 9 — by then we know what to pull together.
