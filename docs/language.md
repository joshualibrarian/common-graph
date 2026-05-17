# Language

Common Graph holds meaning at the sememe layer; it expresses meaning at the **language layer**. The same Create sememe is "create" in English, "crear" in Spanish, "作る" in Japanese. The mapping from sememe to surface form goes through Language items — each natural or notational language is itself an item in the graph, endorsing the lexeme frames that name sememes in that language.

This document defines language items, lexemes, the cross-language anchor pattern, and how all of it feeds the runtime vocabulary that drives parsing and rendering.

This document assumes familiarity with [sememes](sememes.md), [frames](frames.md), and [items](item.md).

## Languages are items

Each language Common Graph supports is an item — `@english`, `@spanish`, `@japanese`, `@german`, `@mandarin`. ISO 639-3 codes provide canonical-key strings:

```
@english     ← multihash(SHA-256, "cg.lang:eng")
@spanish     ← multihash(SHA-256, "cg.lang:spa")
@japanese    ← multihash(SHA-256, "cg.lang:jpn")
@german      ← multihash(SHA-256, "cg.lang:deu")
@mandarin    ← multihash(SHA-256, "cg.lang:cmn")
```

Two librarians compute the same IID for English by hashing the same ISO 639-3 canonical key. No coordination, no registry; the language identities align by birthright across implementations.

A Language item's manifest carries its identifying bindings (ITEM_ID, gloss frames in various meta-languages, reading direction, default grammatical features) and endorses its lexemes — possibly thousands or millions of them, each frame asserting "this surface form names this sememe in this language."

## Lexemes

A **lexeme** is a frame asserting "this surface form is a name for this sememe in this language":

```
{@lexeme, [
  @THEME → @create,
  @NAME:[@english, @verb, @lemma] → "create"
]}
```

The THEME binding points at the sememe being named; the NAME binding carries the surface form, qualified by language (`@english`), part of speech (`@verb`), and grammatical feature (`@lemma`). Each lexeme is a complete frame — signed by its endorser, hashable, queryable.

The English Language item's manifest endorses thousands of lexeme frames. The Spanish, Japanese, German, and every other language's manifest endorses their own. Each frame is independent; the same sememe gets independent lexeme frames in each language pointing at the same target.

Surface forms beyond the lemma — plural forms, inflected forms, conjugated forms — are additional lexeme frames with different qualifier combinations:

```
{@lexeme, [
  @THEME → @create,
  @NAME:[@english, @verb, @past-tense] → "created"
]}

{@lexeme, [
  @THEME → @create,
  @NAME:[@english, @verb, @third-person, @singular, @present] → "creates"
]}
```

Each inflection is its own frame. The system can look up "creates" and find the Create sememe directly; it doesn't need to do morphological analysis at runtime if the inflection is already endorsed.

For languages with rich inflectional morphology, large corpora of lexeme frames exist as data. UniMorph, the OEWN, OpenWordnet, and similar resources provide the imports.

## Grammatical features as sememes

The qualifiers on a NAME binding — `@english`, `@verb`, `@lemma`, `@plural`, `@past-tense`, `@accusative-case`, `@masculine-gender` — are themselves sememes. They live in `LexicalVocabulary` and `GrammaticalFeature` and similar seeded vocabularies; their canonical keys derive deterministic IIDs.

The inventory grows as new languages are added. A language with case marking introduces case-feature sememes; a language with classifiers introduces classifier-feature sememes. The framework doesn't enumerate features ahead of time; the relevant features for a language emerge from that language's import.

Parts of speech (`@noun`, `@verb`, `@adjective`, `@adverb`, `@preposition`, `@determiner`, `@pronoun`, `@conjunction`, `@interjection`) are also sememes, used as qualifiers throughout.

## Cross-language linking via CILI

When the same concept needs to be named in many languages, each language's lexeme frames point at the *same sememe IID*. The shared identity is what makes the lexemes interoperate:

```
{@lexeme, [
  @THEME → @create,
  @NAME:[@english, @verb, @lemma] → "create"
]}

{@lexeme, [
  @THEME → @create,
  @NAME:[@spanish, @verb, @lemma] → "crear"
]}

{@lexeme, [
  @THEME → @create,
  @NAME:[@japanese, @verb, @lemma] → "作る"
]}
```

All three name the same Create sememe. The THEME target is identical across the three frames; the surface forms differ. A user typing "crear" in Spanish or "作る" in Japanese resolves to the same sememe a user typing "create" in English resolves to.

**CILI** (the Cross-Lingual Index) is the empirical work that makes this convergence possible at scale. CILI assigns language-neutral identifiers to concepts shared across many wordnets. When Common Graph imports a wordnet for some language, the CILI mapping tells the import which existing sememe each concept aligns with. The wordnet's lexemes get endorsed pointing at the already-existing sememe IID; no duplication, no manual reconciliation.

For concepts that don't have CILI alignment (newly minted concepts, domain-specific terminology, language-particular phenomena), the import mints a new sememe and the lexemes point at the new IID. Cross-language linking is a default, not a requirement.

## Thematic roles in the linguistic backbone

The role inventory used as binding roles throughout the system (AGENT, THEME, GOAL, SOURCE, INSTRUMENT, RECIPIENT, TIME, LOCATION, MANNER, CAUSE, PARTNER, VALUE, NAME, ATTRIBUTE, …) is itself part of the linguistic backbone. Each role is a sememe; the universal ~25-role set comes from Fillmore frame semantics and ISO 24617-4 (SemAF-SR).

Roles aren't English-specific. They're language-neutral semantic relationships that any natural language's grammar maps onto. English marks the AGENT with subject position; Latin marks it with nominative case; Tagalog marks it with a focus particle; all three converge on the same AGENT sememe. The role identity is shared; the linguistic surface that signals it is language-specific.

The thematic-role vocabulary is small (~25 sememes) and stable. New domain vocabularies add new predicates and new archetypes, but rarely new thematic roles. The role inventory is empirical — the result of decades of cross-linguistic work — and Common Graph defers to that work rather than reinventing.

## Notations as language fragments

A language as Common Graph models it isn't strictly a natural language. The chess notation system is a "language" — its lexemes name chess concepts ("Nf3" → @knight-to-f3, "0-0" → @kingside-castle), its parsing rules know SAN syntax. SQL is a language — its lexemes name SQL keywords and the operators its parser understands. A programming language could be a CG Language.

The model is general; natural languages are the most common case. What makes something a Language in Common Graph is:

- An item with `@language` (or a sub-archetype) in its head chain.
- Endorsed lexeme frames that map surface forms to sememes.
- A composition of parse notations (declared as data; see [`input.md`](input.md) and [`text.md`](text.md)) that the language brings into scope.

Chess notation is `@chess-notation`; its lexemes include "Nf3", "exd5", "0-0-0". SQL is `@sql`; its lexemes include "SELECT", "WHERE", "JOIN". The framework treats all of them uniformly; they're each a different composition of notations and lexemes around a common runtime.

A single input scope can include multiple languages simultaneously. A user typing in a chess game with English active would have `[@chess-notation, @english]` in their language scope; "move pawn to e4" resolves through English lexemes, "Nf3" resolves through chess-notation lexemes, both interpretable in the same prompt.

## Composition with the runtime

Language items inform the runtime through the **token dictionary** — the scoped lookup from surface forms to sememes. Each language's lexemes contribute postings to the dictionary; the scope chain at input time determines which languages' postings are in play.

The token dictionary is global by index but scoped by query: a single dictionary indexes lexemes from every language, but a lookup with `[@english, @spanish]` in scope only returns postings under those scopes. Lookups walking through `[@chess-notation, @english]` find the chess vocabulary first, fall back to English.

Full mechanics live in [`vocabulary.md`](vocabulary.md). What language items contribute at this level is the *content* of the dictionary; the runtime determines how that content is queried.

## Reading direction and per-language metadata

Beyond lexemes, a Language item's manifest carries metadata about the language's behavior:

- **Reading direction** — left-to-right (English, Spanish), right-to-left (Arabic, Hebrew), top-to-bottom (traditional Japanese), bidirectional (Arabic with embedded English).
- **Default word order** — SVO (English), SOV (Japanese, Korean), VSO (Welsh).
- **Plural-formation rules** — irregular plurals where the lexeme doesn't capture the rule.
- **Pronoun system** — gender, number, formality, evidentiality marking.
- **Default numerals** — base 10 Arabic for English; Chinese characters for Mandarin formal contexts; Devanagari for Hindi.

These show up as bindings on the language's own manifest, queryable from any context that needs them. A renderer assembling output for a user picks up the reading direction and word order from the active language's manifest; a UI presenting numerals does the same.

## Languages and Parley

When two librarians communicate through Parley, they share language items by IID. The two systems agree on which sememe a given English lexeme points at because both reference `@english` by the same canonical-key-derived IID. Lexeme frames travel between librarians the same way any other frames do.

The protocol doesn't need a "current language" negotiation. References to sememes carry their identity; the receiver renders the references in their preferred language at display time. A frame mentioning `@create` arrives at a Spanish-speaking user's session; their renderer queries the token dictionary in `@spanish` scope and presents "crear" on screen. Same data, different language at the surface.

## Worked examples

**"Create" / "Crear" / "作る" as lexemes.**

```
English lexeme:
  {@lexeme, [
    @THEME → @create,
    @NAME:[@english, @verb, @lemma] → "create"
  ]}

Spanish lexeme:
  {@lexeme, [
    @THEME → @create,
    @NAME:[@spanish, @verb, @lemma] → "crear"
  ]}

Japanese lexeme:
  {@lexeme, [
    @THEME → @create,
    @NAME:[@japanese, @verb, @lemma] → "作る"
  ]}
```

Same THEME target (`@create`); language-specific NAME bindings; surface forms diverge while the underlying sememe stays put.

**"Move" as English verb vs. chess-notation token.**

```
English-verb lexeme:
  {@lexeme, [
    @THEME → @cg-verb-move,
    @NAME:[@english, @verb, @lemma] → "move"
  ]}

Chess-notation lexeme (a chess move written as bare notation):
  {@lexeme, [
    @THEME → @nf3-move-instance,
    @NAME:[@chess-notation, @lemma] → "Nf3"
  ]}
```

Two languages, two lexemes, two different sememes. The same surface form ("move") could exist in both languages if needed; the scope chain at parse time determines which lexeme wins.

**German separable verb.**

A German lexeme can mark a verb's prefix-separating behavior as part of its qualifier set:

```
{@lexeme, [
  @THEME → @pick-up,
  @NAME:[@german, @verb, @lemma] → "abholen",
  @ATTRIBUTE:[@separable-prefix] → "ab"
]}
```

The renderer, knowing the separable-prefix metadata, knows to split "abholen" into "hole" (the conjugated stem) and "ab" (sentence-final particle) in present-tense V2 contexts. The lexeme carries the linguistic structure; the renderer composes it correctly. (Detailed in [`text.md`](text.md).)

## Relations

- [`sememes.md`](sememes.md) — what lexemes name; the shared-identity layer.
- [`vocabulary.md`](vocabulary.md) — how lexemes feed the token dictionary; runtime lookup.
- [`text.md`](text.md) — parsing and rendering; how languages contribute notations.
- [`input.md`](input.md) — the input pipeline that consumes language-scoped lookups.
- [`seed-vocabulary.md`](seed-vocabulary.md) — how Language items get into the bootstrap.
- [`frames.md`](frames.md) — lexeme frames as ordinary frames in the system.
