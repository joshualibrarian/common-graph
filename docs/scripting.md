# Code Items

Items carry code. A chess game ships with the rules that enforce legal moves. A financial ledger ships with the formulas that compute balances. A chat room ships with the bot that greets new members. A Markdown editor ships with the parsing and rendering routines that turn its frames into displayable structure. The code is just another part of the graph — signed, versioned, content-addressed — referenced by manifests the same way any other content is.

This document defines code items (items whose role is to carry executable code), how implementations from multiple languages coexist for the same archetype, how the runtime selects among them, and how sandboxing keeps untrusted code from doing harm.

This document assumes familiarity with [items](item.md), [the API model](api.md), [the runtime](runtime.md), [manifests](manifest.md), and [the reference scheme](ref-scheme.md).

## A code item is an item

A code item is an item whose manifest's head is the **Code** archetype. Its bindings carry:

- **`@ITEM_ID → <iid>`** — the code item's own identity.
- **`@IMPLEMENTS → @<archetype-or-predicate>`** — the concept this code realizes.
- **One or more language bindings** — what language the code is in and where to find the actual code.

```
{@code, [
  @ITEM_ID → <chess-game-java-iid>,
  @IMPLEMENTS → @chess-game,
  @JAVA:[ClassName] → "dev.everydaythings.graph.game.ChessGame"
]}
```

Code items live in the graph the same way every other item does. Their manifests are signed; their identities are stable; their versions are linked through FOLLOWS bindings. The runtime fetches them, materializes them, and dispatches frames to them like any other item.

## Language bindings

The language binding shape pairs *what language the code is in* with *what form the code reference takes*:

```
@<language>:[<form>] → <reference>
```

The **role** is the language sememe — `@JAVA`, `@PYTHON`, `@LISP`, `@JAVASCRIPT`, and so on. The **qualifier** is the form of code reference — `ClassName` for a class identifier, `SourceCode` for inline source text, `Bytecode` for compiled bytes addressable by content hash. The **target** is the actual reference, whatever the form expects.

Common examples:

```
@JAVA:[ClassName] → "dev.everydaythings.graph.game.ChessGame"
@PYTHON:[SourceCode] → "class ChessGame: ..."
@LISP:[SourceCode] → "(defun chess-game-handle-move ...)"
@JAVASCRIPT:[SourceCode] → "class ChessGame { ... }"
@JAVA:[Bytecode] → ~<bytecode-cid>
```

A single code item carries one such binding (the canonical case). The Stage reads it, picks the right language runtime, loads the code, and the item is ready to handle frames.

## Sibling code items for the same archetype

The same archetype can have multiple implementations, each in its own language, each in its own code item:

```
@chess-game-java's manifest:
  head: @code
  bindings:
    @ITEM_ID → <java-iid>
    @IMPLEMENTS → @chess-game
    @JAVA:[ClassName] → "dev.everydaythings.graph.game.ChessGame"

@chess-game-python's manifest:
  head: @code
  bindings:
    @ITEM_ID → <python-iid>
    @IMPLEMENTS → @chess-game
    @PYTHON:[SourceCode] → "class ChessGame: ..."

@chess-game-lisp's manifest:
  head: @code
  bindings:
    @ITEM_ID → <lisp-iid>
    @IMPLEMENTS → @chess-game
    @LISP:[SourceCode] → "(defstruct chess-game ...)"
```

Three independent code items, three different IIDs, three different signers possibly, three different versions independently. All point at the same archetype through their IMPLEMENTS bindings. The archetype itself has no idea how many implementations exist; it just declares what it expects (its schema and HANDLES).

The choice of which to run isn't baked into the data. The trust matrix, the host's available runtimes, and the user's preferences combine to select one at runtime. The selection is policy, not declaration.

## How the runtime finds and runs code

When a frame arrives that the Librarian needs to dispatch:

1. **Find candidate archetypes** — items the frame concerns, walked via their archetype chains until a matching `@HANDLES → <frame-predicate>` is found.
2. **Find code items** — for each candidate archetype, query the graph for items with `@IMPLEMENTS → <archetype>`. Multiple matches are normal.
3. **Filter by available runtimes** — only code items whose language is available on this host can run. A Python implementation is excluded if no Python runtime is loaded.
4. **Score with the trust matrix** — among the surviving candidates, score by trust (who signed the code item, what permissions does the runtime need, what policy applies to that combination). Strict policies eliminate many; permissive ones admit them all.
5. **Pick one** — typically the highest-scored, possibly with user prompt if the difference is borderline.
6. **Stage materializes** — the chosen code item's manifest tells the Stage which language and which reference; the Stage loads the code (instantiating a Java class, executing the Python source, evaluating the Lisp expressions, etc.) and produces a runtime handler.
7. **Stage runs the handler** — `handler(frame) → value-or-frame`, under the capability bundle the trust matrix granted.

This flow happens whether the chosen code is Java, Python, Lisp, JavaScript, or any other supported language. The Stage's job is to abstract over languages; the Librarian's job is to choose among them.

See [`api.md`](api.md) for the HANDLES + IMPLEMENTS declarations that drive steps 1–2; see [`trust.md`](trust.md) for the trust matrix that drives steps 4–5.

## The polyglot environment

The Stage hosts language runtimes through GraalVM's polyglot machinery (or equivalent for non-JVM hosts). Each supported language gets a context — a sandbox the runtime operates in:

- **Java** — runs in the host JVM directly (or in an Espresso sandbox when capability constraints require it).
- **Python** — runs in a GraalPython context.
- **JavaScript** — runs in a GraalJS context.
- **Lisp / Clojure** — runs in either Clojure-on-JVM or a GraalVM Lisp context, depending on dialect.
- **WebAssembly** — runs in a GraalWasm context.
- **R, Ruby**, etc. — supported wherever the underlying polyglot host provides them.

Each language context is sandboxed by GraalVM's host-access controls. Untrusted code in any language can be denied filesystem access, network access, JVM reflection, and other privileged operations. The host's trust matrix decides what each piece of code is granted.

Languages communicate through GraalVM's interop machinery. A Python handler operating on a Frame (which is, in implementation terms, a Java object) sees it through a language-native surface — Python sees `frame["LHS"]` and dict-like iteration; Lisp sees keyword access; JavaScript sees property access. The Stage marshals the same Frame across languages with no copy; the language-native ergonomics emerge from the underlying interop wrappers.

## Sandboxing and capabilities

Untrusted code runs with **narrowed capabilities** — what it can do is constrained by the trust matrix and enforced by the Stage. The capability surface:

- **Compute budget** — CPU time and memory limits.
- **Host access** — filesystem read, filesystem write, network connections, environment variables, system clock.
- **Process operations** — spawning, signaling, threading.
- **Reflection** — introspection into the host runtime.
- **Inter-item calls** — what other items this code can dispatch to.

A handler in a fully trusted item (the Librarian, certain bootstrap items) runs with all capabilities. A handler in a third-party application bundle runs with the capabilities the user granted when installing the bundle. A handler from a network-received frame runs with whatever the host's default network-source policy allows — usually very narrow.

The capability bundle is computed by the Librarian, applied by the Stage, and enforced by GraalVM. Once the handler is running, it cannot escape its capabilities short of finding a vulnerability in the host. The system relies on the polyglot host's sandboxing primitives; it doesn't reinvent process isolation.

## Distributing code as items

Because code items are items, they distribute through the same machinery as any other content. A code item can be:

- **Bundled with an application** — the application's manifest endorses the code items it provides. Installing the application loads the bundle.
- **Posted to a code repository** — a librarian dedicated to hosting code items lets users browse and import.
- **Linked from frames** — an arbitrary frame can reference a code item via `@IMPLEMENTS` or other bindings. The Librarian fetches it on demand.
- **Updated through version chains** — a code item's new manifest follows its previous one via `@FOLLOWS`; users who trust the signer get the update; users who don't, don't.

This is what makes Common Graph genuinely extensible: new behavior isn't a software install in the traditional sense (binaries deployed to fixed paths). It's an item showing up in the graph, signed by someone, available for the librarian to fetch and (subject to trust policy) load.

A user can write a small Python handler for some predicate, sign it, and publish it to their personal librarian. Anyone the user shares it with can fetch the code item, verify the signature, and (if their trust policy allows) start using it. The handler is just another item; the distribution is just the network.

## Code items as content

A code item's *source* is content. A Python source string, a Java class name, a Wasm bytecode blob — each is a value the binding carries. Source strings sit inline as text literals; bytecode blobs sit by `~`-reference to their content hash; class names sit as plain text identifying code outside the graph.

The bytecode-by-hash case is what enables a code item to ship without depending on out-of-band installation:

```
@compiled-handler's manifest:
  head: @code
  bindings:
    @ITEM_ID → <handler-iid>
    @IMPLEMENTS → @some-predicate
    @JAVA:[Bytecode] → ~<bytecode-cid>
```

The bytecode is content-addressed. Fetching the code item, fetching the bytecode bytes by their CID, verifying the bytes match the hash — all the usual machinery. Once loaded, the bytecode runs in the JVM (under the trust-matrix's capability bundle).

The `ClassName` form is the simpler case where the code is already on the host (loaded at startup by the implementation language's normal mechanisms). Convenient for foundational code that ships with the librarian; less useful for distributed extensions.

## Foundational vs. distributed code

Two patterns coexist:

**Foundational code items** are bundled with the librarian itself. The Librarian item's code, the Add operator's code, the parser's code, the renderer's code. These ship with the implementation; their `@JAVA:[ClassName]` targets are present in the runtime by virtue of having been compiled into the librarian's image. Trust is absolute.

**Distributed code items** arrive from the graph at runtime. Application bundles, user-contributed handlers, third-party extensions. Their language bindings might be `@PYTHON:[SourceCode]` (inline source), `@JAVA:[Bytecode] → ~<cid>` (content-addressed bytecode), or anything else the supported runtimes can load. Trust is policy-driven.

Both go through the same dispatch flow. The Stage doesn't know or care which path a code item arrived through; it loads what the binding tells it to load, under the capability bundle the trust matrix produced. Foundational items get full capabilities by default; distributed items get whatever the trust matrix grants them, often much less.

## Worked examples

**A chess game with multiple language implementations.**

```
@chess-game's archetype manifest declares HANDLES:
  @HANDLES → @move
  @HANDLES → @resign
  @HANDLES → @offer-draw

Three sibling code items:
  @chess-game-java   @JAVA:[ClassName] → "ChessGameJava"
  @chess-game-python @PYTHON:[SourceCode] → "class ChessGame: ..."
  @chess-game-lisp   @LISP:[SourceCode] → "(defstruct chess-game ...)"

A MOVE frame arrives. The Librarian finds @chess-game-java, @chess-game-python, @chess-game-lisp as candidates (all IMPLEMENTS @chess-game). The host has Java and Python runtimes; @chess-game-lisp is excluded. The trust matrix gives @chess-game-java a higher score (signed by a more trusted authority). The Stage loads @chess-game-java's class and dispatches the MOVE frame. Result flows back; the chess game updates.
```

**A user-contributed handler.**

```
A user writes a Python script that handles a custom @MOOD_CHECK_IN predicate
for their personal journal items. They sign and publish:

{@code, [
  @ITEM_ID → <handler-iid>,
  @IMPLEMENTS → @journal,
  @PYTHON:[SourceCode] → "def handle_mood_check_in(frame): ..."
]}

Friends who trust this user import the journal application, which references
their handler via @IMPLEMENTS. The friends' librarians fetch the code item,
verify the user's signature, and load the handler under a capability bundle
that grants Python compute time and filesystem access only to the friend's
own journal data. No access to network, no access to other items.
```

**A bridge implemented as a code item.**

```
{@code, [
  @ITEM_ID → <smtp-bridge-iid>,
  @IMPLEMENTS → @smtp-bridge,
  @JAVA:[Bytecode] → ~<bridge-bytecode-cid>
]}
```

A bridge connecting Common Graph to email is a code item whose handlers translate inbound emails into frames and outbound frames into emails. The bytecode is fetched, verified, and loaded under a capability bundle that grants SMTP/IMAP network access but no other privileges.

## Why "scripting" works at all

A traditional scripting story is awkward: users want code to extend behavior, but the system has to load that code somehow, verify it's safe somehow, isolate it somehow, give it a calling convention somehow. Most systems hand-build these layers; the result is fragile, the boundaries are leaky, and every new language requires re-engineering the lot.

Common Graph treats code as a data type. A code item is an item with the Code archetype and a language binding. The graph distributes it the same as everything else; the trust matrix scores it the same as everything else; the Stage runs it the same as everything else. New languages plug in by adding a runtime to the polyglot host; new code distributions emerge as new code items in the graph. None of the layers special-case "executable content."

The cost is one archetype (Code) and one set of language bindings on its manifests. The benefit is a unified extensibility story that scales to arbitrarily many languages and arbitrarily many trust contexts.

## Relations

- [`item.md`](item.md) — items as the entities code items are instances of.
- [`api.md`](api.md) — IMPLEMENTS and HANDLES; how code items connect to archetypes.
- [`runtime.md`](runtime.md) — the Stage and the polyglot host.
- [`trust.md`](trust.md) — the trust matrix that selects among candidate implementations.
- [`manifest.md`](manifest.md) — code item manifests and their language bindings.
- [`ref-scheme.md`](ref-scheme.md) — the `@`/`~` references that carry code references.
- [`vocabulary.md`](vocabulary.md) — language runtimes as sememes; the bootstrap vocabulary they're part of.
- [`bridges.md`](bridges.md) — bridges as code items realizing external-protocol archetypes.
- [`seed-vocabulary.md`](seed-vocabulary.md) — how application bundles ship code items.
