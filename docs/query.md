# Query

In Common Graph, a query is not a separate kind of thing. A query is a **frame** — the same primitive used for every other assertion in the system — distinguished only by the fact that at least one of its bindings is *set-returning* rather than a specific value. The same data structure, the same vocabulary, the same input pipeline, the same persistence. What distinguishes a query from an assertion is entirely a property of the bindings: if every reference and every value in the frame resolves to a single thing, the frame is an assertion; if any reference is a pattern or any value is a set, the frame is a query.

This document defines query frames, the patterns they carry, how the runtime detects them, and how they evaluate against the indexed graph.

This document assumes familiarity with [the reference scheme](ref-scheme.md), [frames](frames.md), [items](item.md), and [the API model](api.md).

## The core claim

A binding determines which values match it. A specific value matches only itself. A range matches anything in the range. A comparison matches anything satisfying the comparison. A type pattern matches anything in the type. `Any` matches anything. Each of these is the same kind of thing, differing only in how many values it admits.

A frame becomes a query when at least one of its references or values admits more than one match. A spectrum of specificity:

```
{@authored, [@AGENT → @tolkien, @THEME → @hobbit]}
    Every position is a specific value.  An assertion.

{@authored, [@AGENT → @tolkien, @THEME → @any]}
    THEME points at the universal-matcher sememe @any.  A query.

{@harvest, [@PRODUCT → @tomato, @VALUE → {@gt, [@THEME → {@quantity, [@VALUE → 5, @UNIT → @kilogram]}]}]}
    No @any appears, but the GT sub-frame is partially applied (missing one
    operand) and Bool-returning, so it functions as a matcher.  Also a query.

{?book, [@AUTHOR → @tolkien]}
    Head is a TypeRef (single `?` prefix on the Book item-IID); the frame
    matches anything in the Book archetype hierarchy with that AUTHOR
    binding.  A query.
```

All five are frames. The first is persisted as an assertion. The others are run as queries against the index.

## The `?` query prefix

The `?` prefix marks a reference as a type pattern.  Where `@<iid>` says "this exact item," `?<iid>` says "anything in this item's archetype hierarchy" — anything whose head chain transitively reaches the named archetype.

```
?book       — matches any item whose archetype chain includes Book.
?piece      — matches any chess piece (instances of Piece's sub-archetypes).
?number     — matches any numeric value.
?user       — matches any user item.
```

(Throughout this document, names like `book`, `tolkien`, `any` stand for the canonical-keyed IIDs of the corresponding sememes.  The single `?` or `@` prefix selects the reference variant; prefixes never combine.  See [`ref-scheme.md`](ref-scheme.md).)

`?` references can appear anywhere a reference is valid: at a frame's head (matching frames of that predicate or archetype), at a binding's role (matching bindings with that role-class), at a binding's target (matching values of that type).

## `Any`

`@any` is a sememe in the core vocabulary. Its meaning is "the trivial matcher: accepts any value." It appears in any structural position — as the head, as a role, as a qualifier, or as a target — and in every position it means the same thing: this slot admits any value.

`Any` is distinct from `Unknown`. `Unknown` is a data-level placeholder for "this value is genuinely not known" — an assertion that a binding's target is missing. `Any` is a query-level matcher for "match any value here." The two compose: a query for "frames where the author is explicitly marked unknown" pairs them:

```
{@authored, [
  @AGENT → @unknown,
  @THEME → @any
]}
```

The query asks: "find AUTHORED frames whose AGENT is the literal Unknown sememe, regardless of THEME."

## Sub-frames as set-returning expressions

When a binding's target is itself a sub-frame headed by a Bool-returning operator with one or more operands missing, the sub-frame is a *matcher*. The runtime evaluates it as a predicate over candidate values.

```
{@harvest, [
  @VALUE → {@gt, [@THEME → {@quantity, [@VALUE → 5, @UNIT → @kilogram]}]}
]}
```

The `@gt` sub-frame has only one operand — the threshold. The missing operand is the candidate value being tested. The whole sub-frame reads: "for any harvest, accept if its VALUE binding's target is greater than 5 kg."

The same pattern handles `@between`, `@equal-to`, `@contains`, `@in`, `@matches`, and other Bool-returning operators with arity > 1. Partial application turns them into matchers. Composition is uniform; the runtime detects "this is a query slot" by recognizing the partial-application shape.

This is what makes range queries, comparison queries, and pattern queries fall out of the same data model as concrete frames — no separate query language, no separate evaluator.

## Operator return types: the `Returns` declaration

Operators declare what their evaluation produces via a `@returns` binding on the operator's manifest:

```
@between's manifest:
  head: @operator
  bindings:
    @ITEM_ID → <between-iid>
    !THEME → ?number
    !SOURCE → ?number
    !GOAL → ?number
    @returns → !bool
```

The `!bool` target says "Between returns a value matching the Bool schema."  Operators returning Bool are candidates for partial-application matching.  Operators returning other types (Add returns Numeric, Concat returns String) are arithmetic / data-construction operations whose partial applications are different.

The Returns declaration lives on the operator's own manifest as a single binding; the runtime queries it to decide whether partial application creates a query-matcher.

## Query detection

The runtime detects whether a frame is a query by walking its structure looking for any of:

1. Any reference position carries a `?` prefix (TypeRef pattern).
2. Any reference position is `@any` (the universal-matcher sememe).
3. Any binding target is a sub-frame whose head is a Bool-returning operator with fewer operands than its declared arity (partial application → matcher).

If any of these conditions is true at any position, the frame is a query.  If none are, the frame is an assertion.  Detection is structural — no metadata, no separate type tag, no parallel categorization.  The frame shape determines the routing.

Today's implementation (`QueryWalker`) handles case 1.  Cases 2 and 3 are live design but not yet implemented — they require looking up a sememe's `@returns` to know whether it's Bool-typed, which means consulting the Librarian during the walk.  The structure-only first cut covers TypeRef detection and falls back to the assertion path otherwise.

A frame that detects as an assertion goes through the normal persistence and dispatch path. A frame that detects as a query routes to the query evaluator, which matches the frame's pattern against indexes and returns the matching frames or items.

## Posing a query

A user poses a query by writing it the same way they write any other frame: through the input pipeline.  The composable notations that parse "Tolkien authored The Hobbit" into an assertion also parse "Tolkien authored ?" into a query — the difference is that "?" in the input resolves to `@any` (or to a typed pattern like `?book` if the user types `book?`).

The pipeline produces a frame. The runtime detects it as a query. The query evaluator runs. Results come back as a stream of frames or items.

No separate query syntax is required. The same notations handle both — the difference is in whether any position is a pattern or matcher.

## Query results

A query's result is a stream of frames or items that match the pattern. The stream:

- Returns each matching item or frame in turn.
- Carries match metadata (how the match was made, which patterns matched which positions).
- Supports cursor-based continuation (large result sets paginate naturally).
- Surfaces uncertainty (matches with confidence below a threshold are flagged).

For simple queries, the result is a stream of matching items (e.g., "all books Tolkien authored" returns book items). For complex queries with multiple result variables, each item in the stream is a tuple-shaped frame carrying the bound values.

## Matching against indexes

The librarian maintains (or is designed to maintain) indexes that make queries fast:

- **By IID** — given an IID, return the item's current manifest.  (Built.)
- **By predicate** — given a predicate, return all frames headed by it.  (Built.)
- **By binding role + target** — given a role and a target, return all frames whose binding-with-that-role points at that target.  (Built.)
- **By compound key** — given a role + qualifier set, return all bindings carrying that compound key.  (Built.)
- **By archetype hierarchy** — given an archetype, return all items whose head chain includes it.  (Design; the fifth index, planned but not yet implemented.)

Most queries reduce to one or two index walks.  A pattern query like `{?book, [@AUTHOR → @tolkien]}` would walk the archetype-hierarchy index for Book, intersect with the binding-target index for AUTHOR → @tolkien, return the matches.  More complex patterns combine index walks; the query evaluator decomposes the pattern into index queries it can execute.

The librarian's storage architecture details these indexes; see [`storage.md`](storage.md).

## Variables and cross-references

A query can introduce *variables* — parser-level placeholders that link positions across one or more frames.  To avoid colliding with the `?`-as-type-pattern notation, variables use a `$`-prefixed identifier:

```
{@knows, [
  @AGENT → $person-a,
  @THEME → $person-b
]}
{@knows, [
  @AGENT → $person-b,
  @THEME → $person-a
]}
```

Two query frames; the variables `$person-a` and `$person-b` link them.  Together they ask "find pairs of people who know each other in both directions" — a transitive bidirectional pattern.

Variables aren't stored on the wire — they're parser-level identifiers that bind during query evaluation.  Multiple query frames sharing variables form a compound query; the evaluator finds joint solutions across all frames.  The variable syntax is design intent at this point; the underlying frame model already supports the structural patterns, but the variable-binding evaluator is not yet implemented.

## Compound queries

A compound query is a set of query frames evaluated jointly.  The frames share variables and constraints; matches are joint solutions to all of them.

```
{?book, [@AUTHOR → $author, @THEME → $book]}
{?published, [@THEME → $book, @YEAR → $year]}
{@gt, [@THEME → $year, @THRESHOLD → 1950]}
```

Three frames: find books and their authors, find their publication years, filter to post-1950.  Variables `$author`, `$book`, `$year` connect them.  The joint solution is the set of (author, book, year) triples satisfying all three frames.

The query evaluator decomposes a compound query into a query plan (an order to evaluate the frames, an index strategy for each, a join strategy across them).  Standard join optimization applies.  This is design intent; the planning/joining layer is not yet implemented.

## Where queries live

A query frame can be:

- **Ephemeral** — composed and evaluated immediately, never persisted.  Most interactive queries.
- **Saved** — committed to a manifest as an item with the Query archetype.  Re-runs evaluate against the current state of the graph.
- **Subscribed** — committed and registered for incremental notification when new matches appear.  The librarian notifies the subscriber as the graph changes.

Saved and subscribed queries are first-class graph entities: they have IIDs, they version, they can be shared, they can be endorsed by other items.  A user's "documents I'm tracking" is meant to be just a saved query whose results stream into their UI.  Persisted-query and subscription machinery is design intent; the ephemeral path is what's planned for the matcher orchestrator's first cut.

## Gatekeeping

The intended behavior: queries don't bypass the trust matrix.  A query asks the librarian "show me matching items," but the librarian filters results by what the requester is authorized to see.  Items the requester doesn't have access to are silently excluded; redacted items are returned with the redacted bindings hidden; encrypted items are returned only if the requester can decrypt them.

The trust matrix is intended to apply at *query result time*, not at query *posing time*.  Anyone can pose any query; what comes back depends on what they're entitled to see.  This makes queries safe to share and to compose — no information leaks through asking.  The trust matrix and the result-time filter are both design intent at the time of writing.

## Worked examples

**"Books Tolkien authored."**

```
{@authored, [
  @AGENT → @tolkien,
  @THEME → ?book
]}
```

THEME is a TypeRef — match any Book.  Result: a stream of book items that have a frame `{@authored, [@AGENT → @tolkien, @THEME → @<book>]}` in storage.

**"Harvests over 5 kg."**

```
{@harvest, [
  @RESULT → {@gt, [@THRESHOLD → {@quantity, [@VALUE → 5, @UNIT → @kilogram]}]}
]}
```

The RESULT binding's target is a partially-applied GT operator with only the threshold filled.  Result: all harvest frames whose RESULT binding's target exceeds 5 kg.

**"All authoring relationships."**

```
{@authored, [
  @AGENT → $author,
  @THEME → $work
]}
```

Variables in both positions; no filtering.  Result: a stream of (author, work) tuples covering every AUTHORED frame in scope.

**"People who know each other (both directions)."**

```
{@knows, [@AGENT → $a, @THEME → $b]}
{@knows, [@AGENT → $b, @THEME → $a]}
```

Two frames with shared variables.  Result: pairs of people connected by mutual KNOWS frames.

## Why queries are frames

A separate query language adds complexity that scales with the surface. SQL has its own grammar, its own semantics, its own engine; SPARQL has another set; Cypher has another. Every query language is a learning cost; every translation between query languages and the underlying data model is friction.

Common Graph treats queries as frames. The data model that describes "what is" also describes "what to find." The patterns are the same patterns; the references are the same references; the prefix typing distinguishes asserting from asking. The query engine isn't a parallel construct — it's the indexed-traversal layer that knows how to match a frame's pattern against the graph.

This means anyone who knows the data model knows the query model. New patterns become available the moment the underlying vocabulary supports them. No translation, no impedance mismatch.

## Relations

- [`frames.md`](frames.md) — frames as the primitive queries reuse.
- [`ref-scheme.md`](ref-scheme.md) — the `?` query prefix.
- [`input.md`](input.md) — how query frames come in through the same pipeline as assertions.
- [`vocabulary.md`](vocabulary.md) — the operators (`@any`, `@gt`, `@between`, `@in`) that compose into matchers.
- [`storage.md`](storage.md) — the indexes the query evaluator walks.
- [`trust.md`](trust.md) — gatekeeping at query-result time.
- [`item.md`](item.md) — items as the typical query target.
