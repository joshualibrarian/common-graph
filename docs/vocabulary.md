# Vocabulary

Common Graph's vocabulary is the linguistic surface every interaction passes through. Users type text into a prompt; the text resolves to sememes via the **token dictionary**; sememes drive parsing, dispatch, frame assembly, and rendering. The vocabulary system is the runtime layer that turns text into structure and structure back into text.

The system is holistic — verbs, nouns, operators, units, prepositions, particles, structural symbols, and even punctuation all resolve through the same mechanism. There's no separate command parser, separate operator table, separate noun lookup. One pipeline, one dictionary, one scope-aware lookup.

This document defines the token dictionary, scope resolution, and how lexemes, sememes, and user-introduced names compose into a working linguistic interface.

This document assumes familiarity with [sememes](sememes.md), [language](language.md), [frames](frames.md), and [items](item.md).

## The TokenDictionary

The token dictionary is a scoped lookup from surface forms to sememe references. Given a token (a string) and a scope chain (an ordered list of scopes), the dictionary returns matching postings:

```
lookup("create", [<focused-item>, <session>, @english, null]) → [Posting, Posting, ...]
```

Each posting names a sememe that "create" resolves to in some scope. The lookup walks the chain from most-specific to most-general, gathering matches at each level.

The dictionary itself is just an index — `(scope, token) → list of postings`. It doesn't interpret anything; it returns candidates. The parser (informed by composable notations and language-specific rules) decides which candidate fits the surrounding context.

## Postings

A **posting** carries:

- **Token** — the surface form. `"create"`, `"crear"`, `"+"`, `"kg"`, `"sqrt"`.
- **Target** — the sememe (or item) the token resolves to.
- **Scope** — the item IID this mapping is scoped under. May be `null` for universal postings.
- **Weight** — a relevance score for ranking when multiple postings match.

Postings come from several sources:

- **Lexeme frames** endorsed by Language items — language-scoped (`@english`, `@spanish`, …). The dominant source of vocabulary; one posting per inflected form per concept per language.
- **Sememe symbols** — universal-scoped (null). Compact symbols that resolve everywhere: `"+"` → @add, `"kg"` → @kilogram, `"sqrt"` → @sqrt.
- **Item-specific names** — item-scoped. A chess game's vocabulary names its pieces; a document item's vocabulary names its sections.
- **User aliases** — user-scoped (the user's signing item). Custom shortcuts, personal terminology, scripted expressions.
- **Application bundles** — application-scoped. An installed application contributes its vocabulary while the application is active.

Each source adds postings under different scopes. The same surface form can have postings in multiple scopes; resolution picks based on which scopes are active.

## Scopes

A scope is an item IID. A scope is "active" for a particular lookup when it appears in that lookup's scope chain.

Typical scope chain at an interactive prompt:

```
[<focused-item>, <focused-item's-archetype>, <session>, <user>, @english, null]
```

From most-specific (the item currently focused, whose vocabulary takes priority) to most-general (the universal scope, always present for protocol-defined symbols). A token's resolution walks the chain; matches at narrower scopes generally outweigh matches at broader scopes.

Scopes are nested by *purpose*, not by hierarchy. A user's active language doesn't "contain" the universal scope; they're peers. The dictionary just queries each scope independently and merges results. The chain's ordering is a *priority* hint, not a containment relationship.

**Universal scope (null).** Some symbols are universal: they're the same everywhere. The operator `+` resolves to `@add` for everyone; the unit `kg` resolves to `@kilogram` for everyone; the function `sqrt` resolves to `@sqrt` for everyone. These get null-scope postings — no scope is needed because the meaning is universal.

**Language scopes.** Each Language item is a scope. `@english`'s scope holds the English lexicon; `@spanish`'s scope holds the Spanish lexicon. A user's active languages contribute their scopes; lookups walk the active-language scopes alongside everything else.

**Item scopes.** An item brings its own vocabulary when focused. A chess game item's manifest endorses frames whose roles, qualifiers, and named entities are in the game's vocabulary. Those entities become item-scoped postings while the game is focused.

**Application scopes.** An installed application contributes its vocabulary when the application is in use. The vocabulary lives on the application's archetype manifest; it activates with the application.

**User scope.** A user's own item carries their personal aliases — custom commands, scripted expressions, named shortcuts. These outweigh universal postings of the same token (a user can override "+" if they really want to, though the system warns).

## Universal vs scoped resolution

Some symbols are universal; some are scoped. The distinction matters for resolution.

**Universal symbols** are unambiguous across contexts. `+` always means addition. `5` always means the integer five. `"hello"` is always a string literal. These get null-scope postings and resolve consistently.

**Scoped symbols** depend on context. "create" in English means @create; "crear" in Spanish means the same sememe. "move" in chess notation context resolves to a chess move; "move" in English verb context resolves to the general motion sememe.

The lookup combines both: walk the scope chain (collecting scoped matches), check universal (collecting universal matches), return them all. The parser disambiguates if needed.

## Ambiguity and deferred resolution

A token can resolve to multiple sememes — in different scopes, or in the same scope. "Python" might be the language, the snake, or a Greek myth. "Move" might be a chess move or a file rename. "Bank" might be a financial institution or a riverbank.

The dictionary returns *all* matching postings. The parser carries the ambiguity forward as a **candidate token** until later context narrows it down. A user typing "create python" leaves "python" ambiguous; the next token ("script") might resolve it to the programming language; or the user might explicitly pick.

This is what makes the input pipeline forgiving without giving up determinism. Ambiguity is acknowledged at lookup; resolution happens later, informed by surrounding tokens or by user choice. (Details in [`input.md`](input.md) and [`text.md`](text.md).)

## The dispatch surface

Beyond mere lookup, vocabulary feeds the dispatch path. An item's HANDLES bindings declare which predicates the item processes; the dispatch flow uses the same vocabulary that drives parsing.

When a user types "move pawn to e4" into a chess game's prompt, the path is:

1. Tokenize: `["move", "pawn", "to", "e4"]`.
2. Resolve each token through the dictionary in chess-game scope: `move` → @move, `pawn` → @pawn, `to` → @to-preposition, `e4` → @e4-square.
3. Parse: the composable notations consume the tokens, build a frame with predicate @move.
4. Submit: the frame body is signed and submitted.
5. Dispatch: the librarian sees the frame's head is @move, finds the chess game's archetype has `@HANDLES → @move` (the API surface is declared on the archetype; instances inherit), routes the frame to the chess game's move handler.

Every step rests on the same sememe vocabulary. The token dictionary resolves the user's surface forms to sememes; the dispatch path matches sememes to handlers; the result is structured behavior driven by structured language.

## Sememes drive everything

Verbs ("create", "exit"), nouns ("notes", "kg"), operators ("+", "=="), functions ("sqrt"), structural symbols ("(", ")"), prepositions ("to", "from"), particles ("the", "a") — all are sememes, all flow through the same lookup, all participate in the same parse and dispatch flow. There is no separate command system, separate operator system, separate preposition system.

This is what makes the vocabulary holistic. Adding a new "command" is adding a new sememe and endorsing lexemes for it. Adding a new operator is adding a new sememe with operator-flavored manifest bindings (precedence, associativity). Adding a new unit, a new noun, a new function — all are vocabulary additions, all the same shape.

There's no architectural privilege between parts of speech. The parser's composable notations handle the syntactic shape (this is infix, this is postfix, this is a prefix function call), but every notation consumes the same dictionary-resolved tokens.

## Item vocabulary

An item brings vocabulary into scope when it's focused. The chess game's archetype includes chess-notation lexemes and chess-specific named entities; while the game is focused, all of those are in scope. The same vocabulary leaves scope when the user navigates away.

```
Chess game's effective vocabulary:
  - Chess-notation lexemes (Nf3, e4, 0-0)
  - Named pieces (@white-pawn, @black-knight)
  - Named squares (@a1 through @h8)
  - Game-specific named entities (this specific game, its move history)
```

This is automatic. The chess game archetype's manifest declares the relevant Language scopes and named entities; the runtime activates them when the game is focused. No explicit "switch vocabulary" action.

Items can also extend their vocabulary at runtime. A user playing a chess variant might endorse new lexemes for variant-specific moves on the game item; those become item-scoped postings immediately.

## Composition

Vocabulary is open. Users add aliases ("deploy" → @commit followed by @push). Applications contribute their vocabularies. Items add their own named entities. New sememes get new postings. The dictionary grows by accumulation, not by central registration.

When a user adds an alias:

```
{@lexeme, [
  @THEME → @commit,
  @NAME:[@english, @verb, @lemma] → "deploy"
]}
```

…endorsed by the user's own item, the posting lands in user-scope. The next time "deploy" is typed in a context where the user's scope is active, it resolves.

Scripts and macros work the same way — vocabulary contributions that point at structured expressions or sequences. The target can be a single sememe or a composite frame; either way, the lookup finds the structured meaning.

## Worked examples

**Resolving "create" in a session with English active.**

Scope chain: `[<focused-item>, <session>, @english, null]`. The token dictionary returns:
- `@english` scope: `("create", @create-sememe, @english, 1.0)` from the English lexeme.
- Universal scope: no match (no universal symbol "create").

The parser sees one candidate; "create" resolves to @create.

**Resolving "Nf3" in a chess-game context.**

Scope chain: `[<chess-game>, @chess-notation, @english, null]`. The token dictionary returns:
- `@chess-notation` scope: `("Nf3", @knight-to-f3, @chess-notation, 1.0)`.
- Other scopes: no match.

The parser sees one candidate; "Nf3" resolves to a knight-move sememe.

**Resolving "exit" — multiple meanings, narrowing scope.**

Scope chain: `[<chess-game>, @english, null]`. The token dictionary returns:
- `@english` scope: `("exit", @exit-verb, @english, 1.0)` from English.
- Possibly `<chess-game>` scope: `("exit", @resign-game, <chess-game>, 1.0)` if the chess archetype aliases "exit" to resigning.

Two candidates. The parser, knowing the chess game's archetype scope is more specific than English, prefers the chess-game posting. The user gets prompted to confirm if confidence is borderline.

**Adding a personal alias.**

A user, frustrated with typing "commit then push to production," endorses:

```
{@lexeme, [
  @THEME → @deploy-script,
  @NAME:[@english, @verb, @lemma] → "deploy"
]}
```

The lexeme points at a script item (`@deploy-script`) the user has minted, whose evaluation chains commit and push. Now typing "deploy" in the user's session resolves to that script, which the runtime executes.

## Relations

- [`sememes.md`](sememes.md) — the meaning anchors lookups resolve to.
- [`language.md`](language.md) — where lexeme postings come from.
- [`seed-vocabulary.md`](seed-vocabulary.md) — the bootstrap vocabulary that ships with the system.
- [`input.md`](input.md) — the parsing pipeline that consumes dictionary lookups.
- [`text.md`](text.md) — parsing and rendering details.
- [`api.md`](api.md) — HANDLES and dispatch; the back half of the vocabulary-driven flow.
- [`item.md`](item.md) — items as scopes; how an item brings vocabulary into play.
