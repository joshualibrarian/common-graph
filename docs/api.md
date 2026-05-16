# API: HANDLES and IMPLEMENTS

An item exposes its behavior through exactly two role-bindings on its manifest: HANDLES (what messages it processes) and IMPLEMENTS (what concept it realizes). Together these describe everything one item needs to know about another to interact with it. They cover both the static contract — what shape does this item have, what messages does it receive — and the dynamic resolution — which code actually runs when those messages arrive.

This document defines those two roles, the dispatch flow they participate in, and how polyglot implementations plug into the same surface.

This document assumes familiarity with [items](item.md), [frames](frames.md), [manifests](manifest.md), and [the reference scheme](ref-scheme.md).

## HANDLES

A binding whose role is HANDLES declares "I receive frames whose head is this predicate." The target is `@`-prefixed — a concrete reference to a predicate item.

```
@HANDLES → @move
@HANDLES → @resign
@HANDLES → @offer-draw
```

Three HANDLES bindings declare an item processes three different message types. The predicate's own manifest carries the schema for what frames using that predicate look like; the HANDLES declaration says "I'm one of the items that responds to those frames."

HANDLES bindings live on **archetype manifests** typically, not on individual instance manifests — the archetype declares the API surface of *its kind*, and instances inherit. A specific chess game doesn't need to re-declare that it handles MOVE frames; the ChessGame archetype already declared that, and the game instance inherits the contract.

Inherited HANDLES can be overridden or extended on an individual instance. A game with special variant rules might add an extra `@HANDLES → @en-passant-variant` binding without redeclaring the standard ones.

HANDLES is the *protocol declaration* — it says what messages are accepted. It doesn't say *which code* handles them; that's the implementation's job.

## IMPLEMENTS

A binding whose role is IMPLEMENTS declares "I am a realization of this concept." The target is `@`-prefixed — a concrete reference to an archetype or predicate item.

```
@IMPLEMENTS → @chess-game
```

An item carrying this binding asserts "I run code that realizes the ChessGame archetype." The runtime uses this binding to find code when it needs to instantiate or dispatch to an item.

IMPLEMENTS bindings live on **code items** — items whose head is the Code archetype, whose purpose is to carry runtime form for some other item or concept. The realization itself lives in the code item's other bindings: language declarations, source references, class names.

A typical code item's manifest:

```
{@code, [
  @ITEM_ID → <code-iid>,
  @IMPLEMENTS → @chess-game,
  @JAVA:[ClassName] → "dev.everydaythings.graph.game.ChessGame"
]}
```

It's a code item; it implements ChessGame; its runtime form is the named Java class. A sibling code item could implement the same archetype in Python:

```
{@code, [
  @ITEM_ID → <code-py-iid>,
  @IMPLEMENTS → @chess-game,
  @PYTHON:[SourceCode] → "class ChessGame: ..."
]}
```

Different languages, different code items, same IMPLEMENTS target. The trust matrix picks one at runtime based on what runtimes the host supports and what the user trusts.

## The two-role split

HANDLES and IMPLEMENTS are two distinct relationships, and the split matters.

**HANDLES** lives on the archetype (or instance) — it answers "what messages do *I* process?" An item with HANDLES has API surface; it can receive frames and react.

**IMPLEMENTS** lives on the code item — it answers "what concept does *this code* realize?" A code item with IMPLEMENTS is a runtime form, ready to be loaded and executed on behalf of its target.

Some items have HANDLES but no IMPLEMENTS (archetypes — they define the contract but don't carry the code). Some have IMPLEMENTS but no HANDLES (code items — they realize a concept, but their HANDLES set is determined by the archetype they implement). Some have both (rare but valid — an item that both defines an API and ships its own implementation inline).

This separation is what enables polyglot implementations to coexist cleanly. The archetype declares one HANDLES set, in one place, with no language commitment. Multiple code items implement that archetype, each in their own language, each with their own runtime form. The contract and the realization are independently authored, independently signed, independently versioned.

## Dispatch flow

When a frame arrives at the librarian, the dispatch flow runs:

1. **Inspect the frame's head.** It's a predicate item identity.
2. **Find HANDLES targets.** Walk the bindings of items the frame *concerns* (items referenced in the frame's bindings via `@`) and check which of those items' archetypes have HANDLES bindings pointing at this predicate.
3. **Resolve to candidate items.** Each match is an item that wants to receive this frame.
4. **Find implementations.** For each candidate, look up its archetype's `@IMPLEMENTS` chain — find the code items that implement it. Often multiple, one per supported language.
5. **Trust-filter.** The trust matrix scores candidate implementations. Hosts in stricter modes accept fewer; in permissive modes, more. The selection is policy, not data.
6. **Materialize.** The Stage loads the chosen code item according to its language declaration — Java reflection on the named class, GraalVM execution of the source, etc.
7. **Dispatch.** The frame is handed to the materialized item. The item's behavior runs. It may produce reply frames; those re-enter the dispatch flow.

Nothing in this flow is hardcoded to a particular language or runtime. The data describes everything — what's being asked, who can answer, what code is available, what the trust policy allows. The Stage's job is to wire the data to the actual execution.

## Polyglot implementations

Code items are language-neutral at the contract layer. Each carries a language-and-form binding declaring its runtime form: `@JAVA:[ClassName]`, `@PYTHON:[SourceCode]`, `@LISP:[SourceCode]`, `@JAVASCRIPT:[SourceCode]`, etc. The Stage chooses among them at materialization time.

For one archetype to be implemented in multiple languages, each implementation lives in its own code item. Same archetype target, different language declarations, different content. The trust matrix and host capabilities together determine which is selected.

Handler dispatch within a materialized item follows language-native conventions:

- **Java** — reflection finds methods on the class, matched against predicate identities by convention (a method named for the predicate handles its frames). Annotation-driven discovery accelerates this.
- **Python** — module-level functions, or class methods, similarly matched by convention.
- **Lisp / Clojure** — functions in the namespace, looked up by symbol.
- **Other GraalVM-hosted languages** — analogous; each follows its own conventions.

Convention beats configuration. The default is "name your method for the predicate it handles, and the dispatcher finds it." An item that wants unusual routing can carry explicit `@HANDLES` bindings pairing predicates with method-name strings, but this is opt-in complexity.

For details on how the Stage hosts the polyglot runtime, see [`runtime.md`](runtime.md) and [`scripting.md`](scripting.md).

## Inheritance through archetypes

An archetype's HANDLES set is inherited by all its instances and by any sub-archetypes. A specific chess game instance doesn't need to redeclare HANDLES; the ChessGame archetype already declared them.

The inheritance walk is straightforward: when looking up an item's HANDLES, the runtime consults the item's own manifest first, then walks up through its archetype's manifest, and that archetype's archetype, until it bottoms out at the universal-parent archetype. Every HANDLES binding encountered along the way is part of the item's effective API surface.

This is how the universal API surface emerges — every item's archetype eventually links back to the Item meta-archetype, which itself declares HANDLES for the universal predicates (LOOKUP, DELETE, and whatever else is part of the base contract). Every item, by virtue of inheritance, accepts those.

Override and extension work the same way. A sub-archetype can redeclare a HANDLES that its parent already has — the sub-archetype's version wins by salience. A sub-archetype can add new HANDLES that the parent doesn't have — those are additive.

## Self-handling predicates

For pure operators like ADD, MULTIPLY, NEGATE — predicates whose behavior is a function of their bindings alone, with no contextual state — the predicate itself can carry an implementation. Such predicates are *self-handling*: the predicate's own manifest carries an `@IMPLEMENTS` binding pointing at itself, and a code item for the predicate provides the implementation directly.

This is the rare case where a predicate is also an actor. Most predicates are pure schema (they describe the shape of frames without prescribing behavior); self-handling is the exception, used sparingly for operations that don't depend on receiving-item context.

For everything else, the rule is **behavior lives on items, not predicates**. The predicate describes the message; the item that HANDLES the predicate determines the response.

## Worked example: Add

The complete picture for Add as a polyglot operation.

```
@add (the predicate):
  head: @predicate
  bindings:
    @ITEM_ID → <add-iid>
    !THEME → ?number
    !THEME → ?number
    @IMPLEMENTS → @add                    (self-handling)

@add-java (a code item):
  head: @code
  bindings:
    @ITEM_ID → <add-java-iid>
    @IMPLEMENTS → @add
    @JAVA:[ClassName] → "dev.everydaythings.graph.operator.math.Add"

@add-python (a sibling code item):
  head: @code
  bindings:
    @ITEM_ID → <add-py-iid>
    @IMPLEMENTS → @add
    @PYTHON:[SourceCode] → "def evaluate(frame): return frame.theme[0] + frame.theme[1]"

An Add frame arriving for evaluation:
  {@add, [
    @THEME → 5,
    @THEME → 3
  ]}
```

The flow: the librarian receives the Add frame, sees `@add` as the head, finds the predicate's `@IMPLEMENTS → @add` (the self-handling marker), looks for code items with `@IMPLEMENTS → @add`. Two are available; the trust matrix and host capabilities pick one. The Stage materializes the chosen code item, the implementation evaluates, the result flows back. Both code paths produce 8; the user never sees which one ran.

## What this replaces

Earlier designs had EXPECTS, EXTENDS, INSTRUMENT, and other API-related predicates. All dissolved.

- **EXPECTS** — schema declarations were once their own predicate, with EXPECTS frames pinned to archetypes describing instance shape. Replaced by `!`-prefixed bindings directly on the archetype's manifest. The schema IS the thing.
- **EXTENDS** — archetype inheritance was once expressed via dedicated bindings or frames. Replaced by the archetype's own head — an archetype that says its head is some other archetype IS extending it. The hierarchy lives in the head chain.
- **INSTRUMENT** — handler-method names were once explicit bindings on HANDLES frames. Replaced by reflection-by-convention; explicit instrument bindings are opt-in for unusual routing.

The API surface needs only two relationships: "I handle these" and "I am one of those." HANDLES and IMPLEMENTS. Nothing else.

## Relations

- [`item.md`](item.md) — what carries HANDLES and IMPLEMENTS bindings.
- [`frames.md`](frames.md) — the messages dispatched through this surface.
- [`manifest.md`](manifest.md) — where HANDLES and IMPLEMENTS bindings live.
- [`ref-scheme.md`](ref-scheme.md) — the prefixes used in the bindings.
- [`runtime.md`](runtime.md) — the Stage that hosts polyglot implementations.
- [`scripting.md`](scripting.md) — how code items load, run, and integrate with the trust matrix.
- [`trust.md`](trust.md) — the trust matrix that selects among candidate implementations.
