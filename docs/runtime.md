# Runtime

The Common Graph runtime is built from three layered concepts: **ItemStage**, **Librarian**, and **Session**. They aren't peers — the Stage is the substrate; the Librarian and the Session are items hosted on it. Every running process has exactly one Stage and at least one Librarian.

This document defines the three layers, the bootstrap order that brings them up, the universal handler contract that crosses language boundaries, and the discipline that keeps the runtime small.

This document assumes familiarity with [items](item.md), [the API model](api.md), [manifests](manifest.md), and [the reference scheme](ref-scheme.md).

## ItemStage — the substrate

The **ItemStage** is the execution substrate. It owns:

- **The polyglot environment.** Language runtimes — Java, Python, Lisp, JavaScript, and any future language with a GraalVM polyglot adapter. One process has one Stage; one Stage hosts many language contexts. Hosts without polyglot support run the Java-only subset; the Stage degrades silently rather than failing.
- **The run primitive.** Given a handler reference and a frame, the Stage invokes the handler in the appropriate language context and returns the result. The primitive is uniform across languages: **frame in, value-or-frame out**. This is the cross-language contract.
- **Capability enforcement.** The Librarian's trust matrix decides *which* implementation runs; the Stage applies the resulting capability constraints at run time — sandboxing, resource limits, refusal of privileged operations from non-privileged callers. The Stage doesn't decide policy; it enforces it.

What the Stage doesn't own:

- **The handler registry.** That's graph data — HANDLES bindings endorsed on archetype manifests — held in storage and queried via the Librarian.
- **Trust-matrix selection.** Which implementation to run for a given predicate is policy, owned by the Librarian.
- **Storage, identity, signing.** All the Librarian's domain.
- **User context, presence, device bindings.** All the Session's domain.

The Stage exists *before* any item. Items receive it at construction. Both privileged Librarian operations and sandboxed user-item code go through the same capability path — the capability check trivially passes for the Librarian and constrains other callers based on the trust web.

## Librarian — the runtime

The **Librarian** is the backbone item. It hosts every active item in the process and owns:

- **Storage.** The Library — a content-addressed object store, derived indexes (by-IID, by-predicate, by-binding-target), a token dictionary. All persistent state lives here.
- **Signing.** The Librarian is itself a Signer; it holds a vault and a key history. Items the Librarian commits to (its own manifests, its endorsed frames, its dispatched actions) are signed with its key.
- **Trust matrix.** Trust-driven selection of which implementation to run for a given predicate, given who's asking and what they're trying to do. The matrix is pluggable — different trust policies plug in by implementing the same interface.
- **Handler dispatch.** Given an incoming frame, walks endorsed HANDLES bindings on the target item's archetype, applies trust-matrix selection, hands the chosen implementation to the Stage for execution.
- **Network.** Owns the Parley listener. Inbound connections from peer librarians and remote sessions route through here.
- **Bootstrap vocabulary.** Seed items (sememes, archetypes, languages, operators) are persisted at startup and live in the Library as ordinary items.

The Librarian is what UI and client code call "the backend" or "the server." It runs every item that's currently active, regardless of whether any session is viewing them.

## Session — the UI intermediary

The **Session** is an item that arranges views of running items for a user. It has no execution capacity of its own — every item, including the Session, runs through the Stage at the Librarian's invitation. "Viewing an item in a session" means a view of that item is currently rendered; the item runs whether viewed or not.

A session has three runtime embodiments sharing one IID and the same handlers; only the transport between the client face and the Librarian differs:

- **In-process** — the Session and the Librarian live in the same process. Method calls are direct.
- **Local-bridge** — separate processes on the same host; calls become frames over a local transport.
- **Remote** — separate hosts; calls become frames over Parley.

Local-first by default; remote when the path crosses a process boundary. Switching local↔remote never changes behavior, only latency. The Session's identity, manifest, and handlers are the same in every case — the network is transport, not architecture.

## Substrate, not peers

The three layers are nested, not parallel:

```
ItemStage (substrate)
 └── Librarian (item — hosts and runs all other items)
       └── Session (item — UI intermediary for the user)
       └── …other items the Librarian hosts
```

The Stage hosts the Librarian. The Librarian hosts the Session and every other item. The Session is just another item from the runtime's perspective — its role is intermediation between the user and the Librarian's hosted items, but its lifecycle and dispatch path are no different.

A non-Java Librarian (Rust, Lisp, embedded C, …) is a clean possibility under this layout: any Librarian is just an item running on an ItemStage. As long as the implementing code item declares HANDLES for the librarian-management predicates and runs in a runtime the Stage supports, a different-language Librarian works.

## Bootstrap order

The runtime comes up in a fixed order:

1. **Stage construction.** The entry point constructs the Stage. The Stage probes its environment for polyglot capability; what's available determines which language strategies are wired.
2. **Librarian construction.** The entry point constructs a Librarian, passing the Stage in. The Librarian becomes the first item hosted on the Stage.
3. **Session attachment (optional).** If the process needs a UI face, a Session is constructed and attached to the Librarian.
4. **Item activity.** From here on, every frame submission, every handler invocation, every dispatch goes through Librarian → Stage. Items come and go as user input and network traffic require.

The chicken-and-egg this avoids: the Stage can't be created *by* the Librarian, because the Librarian wouldn't be hosted by the Stage that runs it. The Stage must precede the Librarian. The entry point creates the Stage first; the Librarian's bootstrap takes the Stage as input.

## The universal handler contract

Every implementation across every language presents the same shape at the Stage's boundary:

```
handler(frame) → value-or-frame
```

A Rust ChessGame, a Java ChessGame, a Python ChessGame all endorse the same HANDLES bindings declaring the same predicates. Each implements its handlers in its own language. The wire format is the contract; the language-specific code is private to each code item.

The Stage's job is to take the chosen implementation (a Java method reference, a Python function in a GraalPython context, a WebAssembly export, …) and a frame, invoke it in the right context, marshal the result back into a value or frame, and return. Whether the implementation returns a primitive, a body, a stream, or nothing — the Stage handles uniformly.

This is what makes polyglot implementations interchangeable. The same archetype can be realized by code items in any supported language; the Stage runs whichever is selected; the calling code never knows or cares.

See [`api.md`](api.md) for the HANDLES + IMPLEMENTS shape that names what runs; this doc covers how the named code is invoked.

## Bootstrap-via-annotation, runtime-via-graph-data

A core principle: **annotations are read only once, at startup.** They produce graph data — handle bindings, schema bindings, implements relationships — which is what the runtime consults afterward.

The two paths converge:

| At bootstrap | At runtime |
|---|---|
| Annotation declaring "this class is the seed item with key K" | An item in the Library with IID derived from K |
| Annotation declaring "this method handles predicate X" | A HANDLES binding endorsed by the relevant archetype |
| Annotation declaring "this field carries schema slot S" | An `!`-prefixed binding on the archetype's manifest |

This means anything declarable via annotation is *also* declarable via running graph operations. Endorsing a new HANDLES binding at runtime adds a handler exactly as if it had been declared by annotation. A polyglot implementation skips the annotation step entirely — its code item publishes HANDLES bindings directly, and the runtime treats them the same.

The runtime *never* consults annotations directly. It walks graph data. Annotations are a convenience for bootstrap, not a parallel mechanism.

## Capability enforcement and the trust web

When a frame arrives, dispatch goes through several layers:

1. **The Librarian finds candidate handlers** — items whose archetypes declare HANDLES for the frame's predicate.
2. **The trust matrix scores candidates** — given the frame's signer, the receiving item, and the environment, which candidates are trustworthy enough to run? Strict policies accept few candidates; permissive policies accept many.
3. **The Stage receives the chosen handler** and a capability bundle describing what it may do (file access, network access, CPU budget, memory budget, ability to call other items).
4. **The Stage invokes the handler** under the capability bundle. The polyglot runtime enforces the bundle for non-trusted code via the underlying sandboxing primitives.

Privileged code (the Librarian itself, certain bootstrap items) runs with full capabilities — the trust matrix grants those by default. User-supplied or network-received code runs with narrowed capabilities; what's denied depends on policy.

This is what lets the system run untrusted code safely. A polyglot handler from a user-installed application bundle runs in the same Stage as the Librarian, but the Stage's capability enforcement keeps the two isolated. The Librarian can do anything; the user-installed handler can do only what its bundle's manifest permits and the trust matrix allows.

## Local vs. remote: just transport

Local-first is a design principle: most operations are local; the network is fallback when the path crosses a boundary. The runtime makes this concrete by routing local calls and remote calls through the same dispatch logic.

A local-session call to "create a document" arrives at the Librarian as a frame, exactly the same shape as a remote-session call to the same operation. The Librarian processes both identically; the Session's network transport (or lack thereof) is invisible to the dispatch path. Migrating from local to remote (or back) doesn't require any change to handler code — only the transport between Session and Librarian changes.

This is what allows the same archetype's code to run unmodified across in-process, local-bridge, and remote configurations. Behavior is in the items; transport is in Parley; the runtime stitches them together without privileging any topology.

## Relations

- [`item.md`](item.md) — items as the entities the runtime hosts.
- [`api.md`](api.md) — HANDLES and IMPLEMENTS; what gets dispatched.
- [`frames.md`](frames.md) — frames as the messages the Stage runs handlers against.
- [`scripting.md`](scripting.md) — code items, polyglot implementations, sandboxing.
- [`trust.md`](trust.md) — the trust matrix that selects handlers.
- [`manifest.md`](manifest.md) — where HANDLES, IMPLEMENTS, and language-binding metadata live.
- [`storage.md`](storage.md) — the Library the Librarian owns.
- [`network.md`](network.md) — Parley as the transport for remote-runtime interactions.
- [`vocabulary.md`](vocabulary.md) — the bootstrap vocabulary the Librarian persists.
