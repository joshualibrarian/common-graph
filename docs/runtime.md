# Runtime

The Common Graph runtime is built from three layered concepts: **ItemStage**, **Librarian**, and **Session**. They are not equals — Stage is the substrate; Librarian and Session are items hosted on it. Every running process has exactly one Stage and at least one Librarian.

## ItemStage — the substrate

The **ItemStage** is the execution substrate. It owns:

- **The polyglot environment.** GraalVM contexts (Java, Python, JS, future Wasm/Rust via FFI). One process has one ItemStage; one ItemStage hosts many language contexts. The constructor probes for GraalVM eagerly. If polyglot isn't available (plain HotSpot, non-GraalVM JVM), the stage stays in Java-only mode — handlers written in Java still run; guest languages are simply unavailable. No exceptions are thrown.
- **The run primitive.** Given a handler reference and a frame, the Stage invokes the handler in the appropriate language context and returns the result. The primitive is uniform across languages: **Frame in, value-or-Frame out.** This is the cross-language contract.
- **Capability enforcement.** The Librarian's trust matrix decides *which* handler should run; the Stage applies the resulting capability constraints at run time — sandboxing, resource limits, refusal of privileged operations from non-privileged callers. Stage doesn't decide policy; it enforces it.

What the Stage does **not** own:

- The handler registry. That's graph data (HANDLES frames endorsed by items), held in storage and queried via the Librarian.
- Trust-matrix-driven selection of which implementation to run for a given predicate. That's policy, owned by the Librarian.
- Storage, identity, signing. All Librarian's domain.
- User context, presence, device bindings. Session's domain.
- Application orchestration (`main`, CLI options, daemon protocol). Those live on the entry classes (`Librarian.main`, `LibrarianDaemon`, `RemoteSession.main`) — ItemStage is the substrate, not the entry.

The Stage exists **before any item**. Items receive it at construction. Both privileged Librarian operations and sandboxed user-item code go through the same capability path — the capability check trivially passes for the Librarian and constrains other callers based on the trust web.

## Substrate, not peer

Conceptually:

```
ItemStage (substrate)
 ├── Librarian (item — hosts and runs all other items)
 └── Session (item — UI intermediary for the user)
```

Librarian and Session are not parallel siblings to the Stage; they are items the Stage hosts. The Librarian is the **first item** instantiated on a Stage, and it then hosts every other item in the process. A non-Java Librarian (Rust, embedded, etc.) is a clean possibility under this layout: any Librarian is just an item running on an ItemStage.

## Bootstrap order

1. Entry-class `main()` (or service-manager `Daemon.start()`) runs.
2. The entry constructs an **ItemStage**. Its constructor probes Graal once, degrades gracefully to Java-only if absent.
3. The entry constructs a **Librarian**, passing the Stage in. The Librarian becomes the first item hosted on this stage.
4. (For combined-mode) The entry constructs a **Session** and attaches it to the Librarian.
5. Subsequent frame evaluations: Librarian *selects* a handler via the trust matrix, then asks the Stage to *run* it.

The chicken-and-egg this avoids: the Stage cannot be created **by** the Librarian, because the Librarian wouldn't be hosted by the Stage that runs it. The Stage must precede the Librarian. This is why `main()` creates the Stage and *then* the Librarian, rather than the Librarian self-constructing its substrate.

## Entry classes

A Librarian can be brought up several ways. The differences are about lifecycle, not architecture:

- **`Librarian.main(String[])`** — direct CLI invocation. Parses options with picocli, builds a Stage, builds a Librarian, blocks on `Thread.join()` until the JVM exits. Useful for `java -cp ... dev.everydaythings.graph.runtime.librarian.Librarian`.
- **`LibrarianDaemon`** — Apache Commons Daemon adapter. Same flow as `main()` split across `init` / `start` / `stop` / `destroy` for jsvc, Procrun, launchd-style service managers. The service manager drives the lifecycle.
- **Embedded** — call `Librarian.ephemeral(stage)` or `Librarian.fresh(stage, path)` directly from another JVM application. Same factories, no entry-class wrapper.

In every case the architecture is the same: Stage first, then Librarian, then optional Session. The entry class is just the boilerplate that constructs them.

## Librarian — the runtime

The **Librarian** is the backbone item. It owns:

- **Storage.** The Library (DataStore + IndexStore). Item cache, frame index, token dictionary.
- **Signing.** It is a Signer; it has a Vault. Its key log is its own item history.
- **The trust matrix.** Trust-driven selection of which IMPLEMENTATION to run for a given predicate. The matrix is plugged in via IMPLEMENTS — there's no halfway, no hardcoded trust policy.
- **Handler dispatch.** Given an incoming frame, walks endorsed HANDLES frames on the target item to find the handler, applies trust-matrix selection, hands the chosen handler to the Stage for execution.
- **Network.** Owns the Parley listener. Inbound connections route through here.
- **Bootstrap vocabulary.** Seed items (sememes, archetypes, languages) are minted at startup from `@Seed.*` annotations and persist in the Library as ordinary items.

The Librarian is the **runtime backend** — what UI work calls "the backend" or "the server." It runs every item that's currently active, whether or not a Session is viewing them.

## Session — the UI intermediary

The **Session** is an item that arranges views of running items for a user. It has no execution capacity of its own — every item, including the Session, runs in the Librarian. "Viewing an item in a session" just means a view of that item is currently rendered; the item runs whether viewed or not.

Three runtime embodiments share one IID and the same handlers; only the transport between client face and Librarian differs:

- **`Session`** — server-side embodiment running inside the Librarian.
- **`LocalSession`** — in-VM client view; method calls dispatch directly to a `Librarian` reference.
- **`RemoteSession`** — out-of-process client view; method calls become frames over Parley.

Local-first by default; remote when the path crosses a process boundary. Switching local↔remote should never change behavior, only latency.

## Polyglot handler contract

The universal handler shape is:

```
handler(frame) → value-or-frame
```

A Rust ChessGame, a Java ChessGame, and a Python ChessGame all endorse the same HANDLES frames declaring the same predicates. Each implements `applyMove` / `handleResign` / `offerDraw` in its own language. The wire format is the contract; the language-specific implementation is private to the code item.

The Stage's job is to take the chosen handler reference (a Java method, a Python function in a GraalPython context, a Wasm export, …) and a frame, invoke it in the right context, and return what comes back. Whether it returns a primitive value, a `Frame`, or a stream of frames, the Stage handles uniformly.

## Bootstrap-via-annotation, runtime-via-graph-data

A guiding principle for the runtime: **annotations are only read once, at startup.** They produce graph data — handle frames, expects frames, implements frames — which is what the runtime consults afterward. The two paths converge:

| At startup | At runtime |
|---|---|
| `@Seed.Item` annotation on a Java class | An archetype item in the Library |
| `@Seed.Handler(predicate = X)` annotation on a method | A HANDLES frame endorsed on the archetype |
| `@Seed.Frame` annotations on static fields | EXPECTS frames endorsed on the archetype |

This means anything declarable via annotation is **also** declarable via running graph operations. Endorsing a new HANDLES frame at runtime adds a handler exactly as if it had been declared by annotation. Polyglot handlers (Python, Clojure, Wasm) bypass the Java annotation step entirely — they publish HANDLES frames directly and the runtime treats them identically.

## Phasing — where the implementation is today

1. **Now.** ItemStage exists and probes polyglot in the constructor. Entry classes (`Librarian.main`, `LibrarianDaemon`) create it first; Librarian receives it. Java handlers run today via reflection on `@Seed.Handler`.
2. **Next.** The `run(handler, frame)` primitive lands on the Stage. Librarian dispatch goes through it instead of invoking handlers directly. Capability enforcement starts here.
3. **After.** GraalPython context comes up; first non-Java handler round-trips end-to-end. Proves the cross-language bridge.
4. **Beyond.** First bridge (read: external-protocol adapter — Matrix, IPFS, ActivityPub) is implemented as polyglot handlers on an archetype that translates foreign messages to/from frames.

The Stage class's javadoc tracks this phasing alongside the code; this doc is the conceptual reference.
