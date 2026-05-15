# Query

In Common Graph, a query is not a separate kind of thing.  A query is a **frame** — the same primitive used for every other assertion in the system — distinguished only by the fact that at least one of its bindings is *set-returning* rather than a specific value.  The same data structure, the same vocabulary, the same input pipeline, the same persistence.  What distinguishes a query from an assertion is entirely a property of the bindings: if every binding resolves to a single value, the frame is an assertion; if any binding resolves to a set of values, the frame is a query.

This document is an architectural reference for the query model.  Parts of it are settled; parts are still being worked out.  Flagged as such where relevant.

> Queries are also input.  A user typing a question, a SPARQL expression, a miniKanren run-form, an SQL `SELECT` — all flow through the same `eval()` pipeline that handles every other input event in the system, and produce query frames as their output.  See [Input](input.md) for the unified-input architecture and the multi-language input/query angle.

## The core claim

A binding determines which values match it.  A specific value matches only itself.  A range matches anything in the range.  A comparison matches anything satisfying the comparison.  `ANY` matches anything.  Each of these is the same kind of thing, differing only in how many values it admits.  A frame becomes a query when at least one of its bindings admits more than one matching value.

Concretely, a spectrum of specificity:

- `AUTHORED { (AGENT) = Tolkien, (THEME) = the-hobbit }` — every binding is a specific value.  An assertion.
- `AUTHORED { (AGENT) = Tolkien, (THEME) = ANY }` — THEME is set-returning (matches any value).  A query.
- `HARVEST { (PRODUCT) = tomato, (VALUE) > 5kg }` — no `ANY` appears, but `> 5kg` resolves to a sub-frame whose evaluation is set-returning.  Also a query.
- `?Book { (AUTHOR) = Tolkien }` — head is a TypeRef (`?`-prefixed reference); the body matches anything in the Book archetype hierarchy with the given binding.  A query.
- `ANY { (ANY) = Tolkien, (ANY) = BOOK }` — predicate and roles are also `ANY`.  A very loose "bag of terms" case.

All five are frames.  The first is persisted as an assertion.  The others are run as queries against the index.

## `ANY`

`ANY` is a sememe in the core vocabulary (seeded as `cg.meta:any` or similar).  Its meaning is "the trivial matcher: accepts any value."  It can appear in any structural position in a frame — as the predicate, as a role in a binding, as a qualifier in a compound key, or as a binding's target.  In every such position, it means the same thing: this position admits any value.

`ANY` should be considered distinct from `UNKNOWN`.  `UNKNOWN` has a quite separate semantic meaning.  It is a data-level placeholder for "this value is genuinely not known" — an assertion that a binding's value is missing.  `ANY` is a query-level matcher for "match any value here."  The two might compose: a query for "frames where the author is explicitly marked unknown" would use `ANY` at the query level and `UNKNOWN` at the assertion level:

```
AUTHORED { (AGENT) = UNKNOWN, (THEME) = ANY }
```

## The `?` prefix: TypeRef patterns

`ANY` covers wildcards over the *value space* — any value, of any kind, including literals.  But it can't compose with a specific type in a single reference slot.  The head of a body is one reference; you can put a specific predicate there (`AUTHORED`) or `ANY` (any predicate), but you can't say "any frame whose head is in the BOOK archetype family" because that needs two pieces of semantics — the kind AND the pattern intent — in one ref slot.

For this case, the reference system carries a fourth prefix: **`?`** — the *TypeRef*.  A TypeRef encodes "match any body whose head is in this IID's archetype/predicate hierarchy" as a single reference.

The four reference prefixes:

- `@<iid>` — exact item reference
- `#<did>` — exact body reference (by DatumID)
- `~<cid>` — exact content reference (by wire-byte hash)
- `?<iid>` — type pattern reference (TypeRef)

A TypeRef in any structural position (head, role, qualifier, target) declares "this position holds bodies of this kind, match anything in the kind's hierarchy."  Compose with constraints in other positions to narrow.

Examples:

```
?Book                            — any Book (item-level match)
?Book { (AUTHOR) = Tolkien }     — any Book written by Tolkien
?Move { (AGENT) = Fischer }      — any Move frame by Fischer (frame-level match)
?Fruit                           — any Fruit (anything in the Fruit hierarchy)
?<Archetype-root>                — any item/frame in the universe (degenerate case)
```

The TypeRef target IID is whatever sememe sits at the head position of the bodies you want.  For items, that's the archetype.  For frames, it's the predicate.  Hierarchical matching does the rest: `?Fruit` matches Tomato items because Tomato's archetype chain reaches Fruit.

### TypeRef and ANY are complementary

The two abstractions sit at different levels:

- **`?<iid>` (TypeRef)** is over the *type hierarchy*.  It matches bodies whose head is in this IID's lineage.  Doesn't apply to literals — literals have no head, no archetype.
- **`ANY`** is over the *value space*.  It matches any value at all — bodies, refs, literals (numbers, strings, bytes), anything.

Neither replaces the other.  Position-by-position guidance:

| Want | Use |
|---|---|
| Any Book (typed item match) | head = `?Book` |
| Any item/frame at all | head = `?<Archetype-root>` |
| Any value here, including literals | target = `ANY` |
| Any value here that's a Fruit (ref-typed) | target = `?Fruit` |
| Any role | role = `ANY` |
| Any qualifier | qualifier = `ANY` |

Use `?<iid>` for "any of this type."  Use `ANY` for "any value at all" — especially when the position might hold literals.

### Notation

The `?` glyph is a common surface-form token for TypeRef, but it isn't *the* syntax.  As with everything else in CG, queries have no native textual syntax — only the structural reference type.  Any input pipeline can produce a TypeRef from any surface form: a question word in natural language ("what books..."), an empty slot in a form UI, a `?` typed into the prompt, a SPARQL-style variable, a Prolog-style uppercase identifier.  The resolved data carries the TypeRef; how it was typed in is the parser's concern.

## Expression sub-frames

Set-returning bindings can also be produced by **expression sub-frames** — nested frames in binding-target positions whose evaluation produces a matcher-over-values rather than a specific value.  Each such frame can be input in any surface form that resolves to the appropriate tokens: English prose, mathematical notation, function-call notation, any natural language for which a TokenDictionary exists.  The frame is what matters; the surface is interchangeable.  Examples of frames and a few surface forms that would resolve to each:

```
BETWEEN { (SOURCE) = 1929, (GOAL) = 1951 }
  — "between 1929 and 1951", "from 1929 to 1951", "zwischen 1929 und 1951", etc.

LESS_THAN { (THEME) = DISTANCE { (THEME) = Paris }, (VALUE) = 10km }
  — "less than 10km from Paris", "within 10km of Paris", "closer than 10km to Paris", "距巴黎10公里以内", etc.

GREATER_THAN { (VALUE) = 5kg }
  — "greater than 5kg", "more than 5 kilograms", "> 5kg", "มากกว่า 5 กิโลกรัม", etc.
```

These are frames like any other.  Their distinguishing property is that their `PredicateBehavior.evaluate` can return a matcher (a function from candidate value to boolean) when the frame is partially applied.  In a binding-target position with an implicit candidate, the missing operand (conventionally `THEME`) is filled by the candidate at evaluation time, and the result is "accept this candidate if it satisfies the rest of the operands."

So the query `HARVEST { (PRODUCT) = tomato, (VALUE) > 5kg }` fully expanded is:

```
HARVEST { (PRODUCT) = tomato, (VALUE) = GREATER_THAN { (VALUE) = 5kg } }
```

The `VALUE` binding's target is a nested frame.  The matcher, when processing candidate `HARVEST` frames, evaluates the sub-frame to get a predicate, applies it to each candidate's `VALUE` binding, keeps passing candidates.

This is the same sub-frame nesting used for computations like `sqrt(9) + 5`.  The evaluation model is uniform; what differs is what evaluation returns (a specific value in assertion contexts, a matcher in partial-application query contexts).

## The `RETURNS` meta-predicate

Each sememe with evaluation behavior declares what its evaluation produces via a meta-predicate frame on itself:

```
RETURNS { (THEME) = ADD,          (VALUE) = NUMBER }
RETURNS { (THEME) = GREATER_THAN, (VALUE) = BOOLEAN }
RETURNS { (THEME) = ANY,          (VALUE) = MATCHER }
RETURNS { (THEME) = BETWEEN,      (VALUE) = BOOLEAN }
```

`RETURNS` declares the **fully-applied** return type.  Partial-application-to-matcher is a general rule (a sub-frame in a binding-target with a missing operand becomes a matcher), not a per-predicate declaration.

The value-type slot holds type sememes: `NUMBER`, `BOOLEAN`, `MATCHER`, and similar.  These are core-vocabulary sememes, the same kind of thing as any other sememe.  They happen to be used in a typing role.

For routing queries, the system walks a frame recursively and checks whether any sememe appearing in any position has `RETURNS { (VALUE) = MATCHER }`, OR whether a `BOOLEAN`-returning sub-frame sits in a binding-target position with a missing operand (partial application turns it into a matcher).  If any are found, the frame is a query.

Routing is declarative.  It requires two pieces of information: (1) the `RETURNS` tag on each sememe, and (2) the partial-application rule that a `BOOLEAN`-returning sub-frame in a binding-target slot with a missing operand behaves as a matcher.  The first is per-sememe data; the second is a single general rule applied uniformly.  No per-predicate behavior hooks are needed.

## Query detection and routing

When the frame-processing pipeline receives a frame, it decides whether to persist as assertion or run as query.  The decision is a **recursive walk** over all positions (predicate, roles, qualifiers, and binding targets, including into sub-frames):

1. If any reference in any position is a TypeRef (`?`-prefixed), the frame is a query.
2. If any sememe in any position has `RETURNS { (VALUE) = MATCHER }`, the frame is a query.
3. If any sub-frame's predicate has a return type that becomes a matcher under partial application in its current position, the frame is a query.
4. Otherwise, the frame is an assertion.

Cases:

- `AUTHORED { (AGENT) = Tolkien, (THEME) = ANY }` — recursive walk finds `ANY` (which returns `MATCHER`).  Query.
- `?Book { (AUTHOR) = Tolkien }` — recursive walk finds a TypeRef at the head.  Query.
- `ADD { (THEME) = 5, (INSTRUMENT) = 2 }` — recursive walk finds `ADD` (returns `NUMBER`), `5`, `2`.  None set-returning.  Assertion.  Evaluates to `7`.
- `SUM { (VALUE) = ADD { (THEME) = ANY, (INSTRUMENT) = 2 } }` — recursive walk descends into the sub-frame, finds `ANY` inside.  Query.

The routing decision is data-driven.  Adding a new matcher-producing predicate is purely a vocabulary extension: seedItem the sememe, declare `RETURNS { (VALUE) = MATCHER }`.  No framework changes.  TypeRef matching is a structural feature of the reference system itself — no per-predicate declaration needed; the prefix carries the semantics.

## Posing a query

A query is posed the same way any other frame is created: by typing into the input prompt.  Tokens resolve through the same token-to-sememe pipeline.  The same composition assembles a frame.  The only difference is what the user fills in.  Complete, singleton bindings produce an assertion.  Any set-returning binding produces a query.

There is no separate query UI, no separate syntax, no mode switch.  What distinguishes assertion from query is purely whether every binding resolves to one value or at least one resolves to a set.

## Surface forms

There is no canonical text syntax for queries.  As with all frames, the canonical form is the CBOR-encoded structure.

**All text tokens resolve to sememes, except literals.**  This includes operators (`>`, `<`, `=`), grouping characters (`{`, `}`, `(`, `)` are sememes such as `OPEN_GROUP` and `CLOSE_GROUP`), keywords, and natural-language words.  Numeric and string literals are the only exception.  Everything else is a sememe with parsing behavior.

Several surface forms coexist, all resolving through the same pipeline:

- **Formal bracket form**: `AUTHORED { AGENT = Tolkien, THEME = ANY }`, or with compound keys: `VIDEO { THEME = the-movie, (VALUE, MKV, UHD) = master-file }`.  Precise, language-neutral, used in documentation.  Parentheses are required around compound binding keys (to group multiple sememes into one key), optional when the key is a single role.
- **Natural language**: "books authored by Tolkien".  Prepositions map to thematic roles; word order and types guide role assignment.
- **Bag of tokens**: `Tolkien book author`.  Unordered semantic tokens, composed into a frame by type-driven role assignment.
- **Question form**: "what did Tolkien write?" in English, or "Was hat Tolkien geschrieben?" in German.  Interrogatives resolve to `ANY` in appropriate positions.  Different languages' question structures (English's auxiliary-do construction, German's verb-second with sentence-bracket) resolve to the same frame; word order differences don't matter once tokens are resolved.
- **Graphical composition**: drag-and-drop of chips in a UI.
- **Voice input**: transcribed and resolved through the same token pipeline.

None is privileged.  The system accepts any form that provides enough semantic tokens, properly resolved, to compose an unambiguous frame.

Different surface forms may be more or less ambiguous.  `AUTHORED` as a sememe name is precise; "wrote" could resolve to `AUTHORED`, `COMPOSED`, `TRANSCRIBED`, or similar, requiring disambiguation.  Proper nouns like "The Hobbit" may require disambiguation regardless of surface form (which Hobbit — the book, the animated film, the trilogy?).

Ambiguity is resolved at input time, not later:

1. **Token-level** dropdowns surface candidate sememes for each ambiguous token; the user picks.
2. **Context-based** narrowing: additional tokens act as constraints on earlier ambiguous ones ("The Hobbit book" narrows "The Hobbit" to the `BOOK`-archetype instance).
3. **Composition-level** prompts surface candidate frame interpretations when token assignment to roles is ambiguous.
4. **Clean failure** when ambiguity is too great to enumerate.

The system never guesses.  It either resolves unambiguously, prompts the user, or fails.

## Variables and cross-references

Most queries can be expressed as a single frame.  A variable is needed only when the same value must be referenced in more than one position across the query's frames.

For single-use constraints, inline the matcher as a sub-frame.  "movies directed by Peter Jackson longer than 120 minutes" becomes:

```
MOVIE { (DIRECTOR) = PeterJackson, (RUNTIME) = GREATER_THAN { (VALUE) = 120min } }
```

No variable.  `RUNTIME` > 120min is a constraint on the candidate's `RUNTIME` binding directly.

For cross-references — where a value appears both as a binding target to be found AND in another position as input to an expression — declare a variable:

```
movie = ANY
mkv_content = ANY

MOVIE { (THEME) = movie, (DIRECTOR) = PeterJackson, (VIDEO, MKV) = mkv_content }
GREATER_THAN { (THEME) = SIZE { (THEME) = mkv_content }, (VALUE) = 20GB }
```

`mkv_content` appears twice — once in the `MOVIE` frame's `(VIDEO, MKV)` binding (where the matcher frames it to concrete content references) and once inside the `SIZE` sub-frame (where it feeds the size computation).  Without a variable, there's no way to say "the content referenced here AND whose size we're measuring there are the same thing."

### Declaration syntax

Variables are declared using the same assignment form as any other local binding:

```
movie = ANY
year  = 2026
threshold = 20GB
```

The surface form `name = expression` resolves through the `EQUALS` predicate:

```
EQUALS { (THEME) = movie-item, (VALUE) = ANY }
```

where `movie-item` is a local item created to represent the variable.  References to `movie` throughout the query resolve to the same item; identity unifies the references without any special `?variable` syntax.

`EQUALS` is unification.  A fresh unknown on one side and a value on the other establishes the binding.  Two already-bound values that match succeed as comparison.  Two already-bound values that differ fail.  Assignment and comparison are the same sememe; which one happens depends on what's already bound.  A variable whose value is `ANY` (or any other set-returning sememe) is recognized by the matcher as an unknown to be bound.  A variable bound to a specific value is a named constant.

### Rule of thumb

- Value appears in one position only → use an inline matcher sub-frame.  No variable.
- Value must appear in multiple positions (cross-reference) → declare a variable.
- Value must appear in the query's output (named result) → declare a variable.

## Compound queries

A query can consist of multiple pattern frames.  **The item holding them is the query scope.**  All query frames on the item are implicitly conjoined, and shared variable names unify across them.  There is no separate grouping construct, no containing frame, no `QUERY` predicate.  The item already provides the membrane.

Each pattern is a claim that must be satisfied; the variables constrain the patterns to be about the same entities.

```
movie = ANY
mkv_content = ANY

MOVIE { (THEME) = movie, (DIRECTOR) = PeterJackson }
VIDEO { (THEME) = movie, (TOPIC, VIDEO, MKV) = mkv_content }
GREATER_THAN { (THEME) = SIZE { (THEME) = mkv_content }, (VALUE) = 20GB }
```

The matcher finds assignments to `movie` and `mkv_content` such that all three frames are satisfied simultaneously.  The result set is the intersection.

A more elaborate example combining several patterns: "movies based on works by Tolkien, longer than 120 minutes, yielding their MKV file sizes":

```
book = ANY
movie = ANY
mkv_content = ANY
file_size = SIZE { (THEME) = mkv_content }

AUTHORED     { (AGENT) = Tolkien, (THEME) = book }
ADAPTED_FROM { (THEME) = movie, (SOURCE) = book }
MOVIE        { (THEME) = movie, (RUNTIME) = GREATER_THAN { (VALUE) = 120min } }
VIDEO        { (THEME) = movie, (TOPIC, VIDEO, MKV) = mkv_content }
```

Three structural patterns join via three shared variables (`book`, `movie`, `mkv_content`).  The matcher finds `(book, movie, mkv_content)` triples where Tolkien authored the book, the movie was adapted from it, the movie's runtime exceeds 120 minutes (the inline matcher in the `MOVIE` pattern), and the movie has MKV content.  `file_size` is bound to the `SIZE` of each match's `mkv_content`.  It is not a constraint but a named output, computed per match and available alongside the bound variables in the result.

One implication of item-as-scope: adding a pattern frame to an existing query item further constrains the query.  Removing a pattern frame relaxes the constraints.  Query items become persistent workspaces of composable constraints and results, not frozen one-shot requests.

## Query results

A query yields zero or more **matches**.  Each match is one assignment to all declared variables that satisfies every claim simultaneously.  The collection of matches is the result set.

Each match becomes a `RESULT` frame on the query's containing item.  The frame's bindings carry the bound values of the query's variables for that match.  For the elaborate Tolkien example above (variables `book`, `movie`, `mkv_content`, `file_size`), the result set might look like:

```
RESULT { (book) = fellowship-book, (movie) = fellowship-movie, (mkv_content) = mkv-1, (file_size) = 22GB }
RESULT { (book) = two-towers-book, (movie) = two-towers-movie, (mkv_content) = mkv-2, (file_size) = 25GB }
RESULT { (book) = king-book,       (movie) = king-movie,       (mkv_content) = mkv-3, (file_size) = 28GB }
```

Each frame is one "row" in SQL terms; each binding is one "column".  The shape parallels a SQL result set or a SPARQL bindings table, but represented as frames rather than as a separate table object.  The result set *is* the collection of `RESULT` frames on the query item.

Because each `RESULT` frame is a regular frame, it carries everything any frame does: identity, signing, indexing, persistence.  Result frames can be displayed, exported, fed into downstream queries, shared.  Nothing about results is special-cased.

Properties:

- Zero matches → no result frames produced; the query exists with patterns but nothing matched.
- N matches → N result frames accumulate.
- Re-running the query updates the frames; identical matches deduplicate via content-addressing.
- Result frames are themselves queryable; downstream queries can pattern-match against them.

The precise structure of `RESULT` frame binding keys (local variable names vs. mapped thematic roles) is listed under "Open design questions" below.

## Claims and the matcher

Each frame on a query "item" (or in a query context) is a **claim** that must be satisfied by the final binding assignment.  The matcher's job is to find assignments to the unknowns such that every claim is simultaneously true.

Claims are satisfied through one of two operational pathways:

- **Lookup (structural)**: the claim has the shape of an assertion with unknowns.  The matcher consults the index to find stored frames matching the claim's shape, binding unknowns to the values found.
- **Evaluation (validator)**: the claim is an expression frame whose evaluation, given current bindings, returns a boolean.  The matcher evaluates and requires the result to be true.

The distinction is operational, not semantic.  Both are constraints on the binding assignment.  The matcher uses indexes where it can (cheap narrowing), and evaluation where it must (per-candidate filtering).  Which pathway a given claim takes is determined by the claim's `RETURNS` declaration: if the claim's predicate has `RETURNS { (VALUE) = BOOLEAN }`, it's a validator; if it has a value-typed return, it's structural (a stored frame to be found).

### The matcher architecture

The matcher is **universal in structure, per-predicate in semantics**:

- **One orchestrator.**  A central component (likely a dedicated `FrameMatcher` class) walks the query's claims, probes the index for structural narrowing, evaluates sub-frames for matcher-producing values, applies matchers to candidates, tracks unifications across shared variables.
- **Per-predicate semantics.**  The matcher doesn't know anything specific about `GREATER_THAN`, `BETWEEN`, `ANY`, or any other predicate.  It evaluates whatever sub-frames it encounters and applies their results (matcher, value, or boolean) uniformly.  Predicate-specific matching logic lives in each predicate's `PredicateBehavior.evaluate`.

Adding a new matcher-producing predicate requires: (1) seedItem the sememe, (2) implement its `evaluate` to return a matcher, (3) declare `RETURNS { (VALUE) = MATCHER }`.  The orchestrator picks it up automatically.

## Derived properties

Not everything asked about in a query is stored as a binding.  Size of a content blob, duration of a video, pixel dimensions of an image, age of a person — these are properties that can be computed from stored data (or from the content itself) rather than stored redundantly.  The query machinery accesses them through **predicate evaluation**: the sememe `SIZE`, `DURATION`, `AGE`, etc. has a `PredicateBehavior.evaluate` that computes the property on demand, given its `THEME`.

Example: `SIZE { (THEME) = mkv_content }` evaluates to the byte count of whatever `mkv_content` references.  Used inside a query as the `THEME` of a `GREATER_THAN` (see the compound query above), it becomes part of the filter.

**Important consequence: derived properties apply to concrete content, not to abstract entities.**  Asking "size of the movie" is ambiguous — which format, which resolution?  Asking "size of the MKV version of the movie" is concrete.  Queries involving derived properties usually require a variable to connect the abstract-entity pattern to the concrete-content reference that the derived property applies to.

## Negation

A claim can be negated by wrapping it in a `NOT` predicate:

```
NOT { (THEME) = DIRECTED { (AGENT) = PeterJackson, (THEME) = movie } }
```

Like other matcher-producing predicates, `NOT` has `RETURNS { (VALUE) = MATCHER }` when applied with a missing operand, or `BOOLEAN` when fully applied.  It inverts whatever its inner claim evaluates to.

Negation is almost always a filter on an already-narrowed positive pattern, not a standalone query.  Good examples:

- "movies adapted from Tolkien NOT directed by Peter Jackson"
- "spades players I've played with who are NOT in my contacts"
- "frames endorsed by Alice NOT also endorsed by Bob"

Standalone negations with no positive pattern ("books not authored by Tolkien") are structurally valid but rarely useful.  They return almost everything minus a tiny set.  The matcher runs them but offers no special optimization for this shape.

## Query item lifecycle

A query frame, once created, persists on whatever item the user is currently in at the time of creation, the same way any assertion would.  It joins the item's other frames, structurally indistinguishable from an assertion, except that its set-returning bindings route the frame-processing pipeline to the matcher rather than to straight persistence.

As described in "Compound queries," the item is the query scope.  All query frames on the item are implicitly conjoined.  All result frames belong to all queries on the item.

As the matcher runs, each match accumulates as a `RESULT` frame on the same item.  The query frame and its results live together.  Re-running the query updates the results.  Content-addressing on result frames naturally deduplicates: the same match on two runs produces the same result frame, so the item's state converges.

When re-running happens is governed by the item's **update policy**, a per-item configuration.  Some items are reactive: the matcher re-runs whenever a relevant new assertion is indexed.  Some are snapshot: results materialize once at query-posing and stay fixed until manually refreshed.  Some may be periodic.  The policy is set per-item, alongside any other behavior configuration.  This is the same shape as any other per-item behavior policy in CG.

There is no dedicated "query item" archetype.  Any item can carry query frames, the same way it can carry any other frames.  Query-ness is a property of individual frames, not a property of their containing items.

## Gatekeeping boundary

The query machinery enforces **structural validity**, not real-world plausibility:

- Structural validity is enforced: binding keys match predicate expectations, values have compatible types, required bindings are present (outside query mode), frames are well-formed before persistence.
- Real-world plausibility is not enforced: whether the asserted fact is historically possible, whether the entity could plausibly be in this role, whether the result is likely useful.

A query for "books Shakespeare wrote about Tolkien" is structurally valid even though it's chronologically impossible (Shakespeare died in 1616; Tolkien was born in 1892).  The query runs and returns no results.  A user who wants to *assert* that Shakespeare wrote about Tolkien — for fiction, satire, alternate-history work — can do so.  The system accepts the assertion.  Other parties' trust and verification decide whether the assertion is taken seriously.

The system is a substrate.  Truth arbitration belongs to the trust layer above.

## Summary of key settled points

- Queries are frames; no separate data structure.
- A binding is a predicate over values; specific values, ranges, matchers, TypeRefs, and `ANY` are points on a continuum.
- A frame is a query iff any reference is a TypeRef OR any binding resolves to something set-returning (accepts more than one value).
- The `?` prefix is the fourth reference type (TypeRef), alongside `@` (item), `#` (datum body), `~` (content bytes).  A TypeRef matches bodies whose head is in the referenced IID's hierarchy.
- TypeRef and `ANY` are complementary: TypeRef is over the type hierarchy (no literals); `ANY` is over the value space (includes literals).
- `ANY` is a sememe in the core vocabulary, distinct from `UNKNOWN`.
- Expression sub-frames (`BETWEEN`, `GREATER_THAN`, `WITHIN`, etc.) are frames in binding-target positions that evaluate to matchers via partial application.  Comparison thresholds fill the `(VALUE)` role.
- `RETURNS` is a meta-predicate declaring what each predicate's evaluation produces; routing uses it plus the general partial-application rule.
- The matcher is universal in structure; predicates contribute their own evaluation semantics via `PredicateBehavior`.
- Variables fold into the existing `EQUALS` predicate; `EQUALS` is unification (assignment when a side is fresh, comparison when both sides are bound).
- Variables are needed only for cross-references; single-use constraints inline as matcher sub-frames.
- **The item is the query scope.**  All query frames on an item are implicitly conjoined; shared variable names unify across them.  No grouping construct needed.
- Update policy is per-item: reactive, snapshot, periodic, or whatever the item configures.
- `NOT` negates a claim; usually a filter on an already-narrowed pattern.
- All text tokens resolve to sememes except literals: operators, brackets, and keywords are all sememes.
- Surface forms (bracket, natural language, bag of tokens, graphical) all resolve through the same pipeline.
- Disambiguation happens at input time via dropdowns, context-based narrowing, or composition prompts.  The system never guesses.
- Derived properties are accessed via predicate evaluation, not stored as bindings.
- Queries persist on the user's current item; results accumulate as `RESULT` frames there.
- Structural validity is enforced; real-world plausibility is not.

## Open design questions

- Whether `UNKNOWN` as a distinct data-level sememe (for asserted-but-unknown values) should be seeded alongside `ANY`, and how they compose in practice.
- How result-frame binding keys are structured: by local variable names, by mapped thematic roles, or a hybrid.  The shape of one-frame-per-match is settled; the internal key structure is not.
- The orchestrator's query planning strategy.  Which claim to probe first, when to switch from structural lookup to per-candidate evaluation, how cross-variable unification propagates.
- Whether `DISTANCE` and similar inherently-binary derived properties should use `(SOURCE)` / `(GOAL)` or `(THEME)` for their two operands.  The current examples use `(THEME) = reference-point` with the candidate implicitly filling the other slot, which is workable but thematically murky.

## Implementation status

Queries are **barely implemented** in CG as of this writing.  The current `QueryItem` and its surrounding scaffolding reflect an earlier, less-unified design and will need to be revisited given the model described here.  The index machinery (`FRAME_BY_ITEM`, `RECORD_BY_BODY`, etc.) supports the lookups the matcher would need.  `PredicateBehavior` and `ItemFrame` provide the vocabulary plumbing.  `EQUALS` already exists in `CoreVocabulary` with infix parsing support.  What remains to build:

1. The `TypeRef` reference variant in the `HashID` sealed family — fourth prefix byte (`?`), codec encode/decode handling, `BindingTarget` decode pathway, and widening of `Datum.head` from `ItemRef` to `HashID` (since `?Book` can be a head).
2. The `ANY` sememe with `RETURNS { (VALUE) = MATCHER }`.
3. A handful of expression predicates (`GREATER_THAN`, `LESS_THAN`, `BETWEEN`, `WITHIN`, `NOT`) with their evaluation behaviors.
4. The `RETURNS` meta-predicate, and its type-slot sememes (`NUMBER`, `BOOLEAN`, `MATCHER`) if not already seeded.
5. Unification semantics for `EQUALS` in the matcher context (fresh-side frames; bound-side compares).
6. The query-detection routing in the frame-processing pipeline — recursive walk recognizing TypeRefs and matcher-returning sememes.
7. The matcher orchestrator (likely a `FrameMatcher` class rather than a method on `Library`).  Recognizes TypeRefs structurally and uses the archetype/predicate-hierarchy walk against `TYPE_INDEX`; dispatches expression sub-frames to per-predicate `evaluate` for boolean filtering.
8. Per-item update policy configuration (reactive, snapshot, periodic).
9. Input-pipeline parsing to produce query frames from natural and formal surface forms, including resolution of `?`-glyph tokens (and equivalents in other surface forms) to TypeRef references.

The model is the design target.
