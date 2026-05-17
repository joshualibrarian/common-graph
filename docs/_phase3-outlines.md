# Phase 3 — Outlines

Working document. One outline per doc. The linguistic layer — substantial consolidation pass; existing docs total ~185 KB, target ~80–100 KB across 7 docs.

**Writing order** (dependency-driven):

1. [`sememes.md`](#1-sememesmd) — semantic backbone; foundation for everything else
2. [`values.md`](#2-valuesmd-new) — typed value bodies (Value, Quantity, Color, dimensional types)
3. [`language.md`](#3-languagemd) — multilingual model; languages and lexemes
4. [`vocabulary.md`](#4-vocabularymd) — runtime vocabulary, token dictionary
5. [`seed-vocabulary.md`](#5-seed-vocabularymd) — bootstrap pattern, canonical-key API
6. [`input.md`](#6-inputmd) — input pipeline; aligned with composable notations
7. [`text.md`](#7-textmd) — already mostly current; light cross-reference pass

---

## 1. `sememes.md`

Target length: 10–14 KB. The semantic backbone — what sememes are, why they matter, how they're anchored to existing linguistic resources.

**Opening.** Common Graph builds meaning out of *sememes* — items that anchor distinct concepts globally, so that "create" in English and "crear" in Spanish point at the same underlying meaning, and so that two systems referring to the same concept don't have to negotiate.

**What a sememe is.** A sememe is an item whose role is "an anchor for meaning." It has the usual item shape — an IID, a manifest — but its purpose is to be referenced by other items as the meaning they carry. Predicates are sememes; archetypes are sememes; roles, qualifiers, units, dimensions, and concepts at every level are sememes.

**Why globally anchored.** A sememe's identity is its IID. Two librarians referring to the same sememe agree on what that reference *means* by virtue of agreeing on the IID, not by negotiating a definition. The vocabulary anchors meaning in the same way content addressing anchors data.

**Empirical grounding.** Sememes aren't invented from whole cloth. The bootstrap set comes from existing linguistic resources:
- **WordNet** — ~120,000 concepts organized hierarchically (English).
- **CILI (Cross-Lingual Index)** — language-neutral concept identifiers shared across many wordnets.
- **VerbNet** — verb classes with thematic role declarations.
- **ISO 24617-4** — standardized ~25 thematic roles (Agent, Theme, Goal, etc.).
- **WordNet's friends** — synset hierarchies in many languages, all linked through CILI.

These aren't *the* foundation; they're a *trust-weighted starting point*. A community that prefers a different ontology can use a different seed vocabulary. The protocol doesn't privilege one source.

**Hierarchy and inheritance.** Sememes form a head chain — each sememe is an instance of some archetype (or another sememe). Add is a kind of Operator is a kind of Predicate is a kind of Archetype. ChessGame is a kind of Game is a kind of Activity is a kind of Item. The hierarchy is data; it's queryable, walkable, extendable.

**Deterministic IIDs for bootstrap.** Bootstrap sememes use deterministic IIDs derived from canonical-key strings ("cg.predicate:add", "cg.role:theme", "cg.lang:eng"). Two independently started nodes derive the same IIDs without coordination. Random IIDs are for user-minted items; deterministic IIDs are for the shared vocabulary everyone needs to agree on by birthright.

**Sememes as items.** Each sememe is an ordinary item with the full item lifecycle. It has a manifest (carrying its schema declarations, HANDLES, IMPLEMENTS if applicable); it can be versioned (rare but possible); it can be endorsed, signed, traveled across the network. Sememes aren't a parallel construct.

**Linguistic surface.** A sememe is named in human language(s) through lexeme frames endorsed by Language items. The English Language item endorses "create" as a lemma pointing at the Create sememe; the Spanish Language item endorses "crear" pointing at the same sememe. Tokens in input resolve to sememes via the token dictionary. (Full detail in `language.md` and `vocabulary.md`.)

**Worked examples.** The Create sememe — its IID, its lexemes across languages, what frames headed by it look like. The Theme sememe — a thematic role used as a binding role across many predicates. The Hobbit sememe — a specific named entity.

**Sememe vs literal.** A binding's target can be a sememe reference (`@<sememe-iid>`) or a literal (a number, string, bytes). Sememes carry shared meaning; literals carry per-frame values. The two coexist throughout the system.

**Relations.** Forward to language.md, vocabulary.md, values.md, frames.md, item.md.

---

## 2. `values.md` (NEW)

Target length: 8–12 KB. The Value meta-archetype and its descendants — typed value bodies including Quantity, Color, and the seven SI dimensional types.

**Opening.** A *value* in Common Graph is a body whose head names a value-typed archetype. Color, Quantity, Length, Point, and similar typed scalars are values; they encode a structured value the way a number encodes a count or a UUID encodes an identity. Values are immutable, content-addressed, and stand-alone — they have no IIDs, no lineages, no need to be items. They *are* their data.

**The Value archetype.** `@value` (head: `@archetype`) is the meta-archetype for typed values. Its instances are *archetypes whose own instances are typed value bodies*. Color extends Value; Quantity extends Value; Length extends Quantity extends Value. The hierarchy lives in head pointers.

**Body-shaped values vs primitive-shaped values.** Two flavors:
- **Body-shaped values** — Color (`{@color, [@R → 255, @G → 0, @B → 0]}`), Quantity (`{@quantity, [@VALUE → 5, @UNIT → @meter]}`), Point. Their bindings are typed components.
- **Primitive-shaped values** — Bool, Numeric. No body; the instance IS a wire literal (a Java `Boolean`, a `Long`, a `BigDecimal`). The archetype exists so other archetypes can declare `?@bool` or `?@numeric` as a target pattern.

Both are first-class value archetypes; they differ only in what their instances structurally look like. Body-shaped values dominate; primitive-shaped values exist for the cases where structural shape would be overkill.

**Schemas on value archetypes.** A Value archetype's manifest carries `!`-prefixed bindings declaring the slots its instances should fill. Color's schema declares `!R`, `!G`, `!B`. Quantity's schema declares `!VALUE` and `!UNIT`. Instance value bodies fill in those slots with concrete bindings.

**Quantity and the SI dimensional types.** Quantity is the generic typed scalar — magnitude plus unit. The seven SI base dimensions get their own subarchetypes:
- **Length** — units of distance (meter, foot, parsec, …).
- **Mass** — units of mass (kilogram, pound, solar mass, …).
- **Time** — units of duration (second, hour, year, …).
- **ElectricCurrent** — units of current (ampere, …).
- **Temperature** — units of temperature (kelvin, celsius, fahrenheit, …).
- **Amount** — units of amount of substance (mole, …).
- **LuminousIntensity** — units of luminous intensity (candela, …).

Each subarchetype's schema constrains the UNIT slot to its dimension's units. A Length body's UNIT must be a length-unit; the runtime validates against the archetype chain.

Derived dimensions (velocity, energy, etc.) emerge by composition — they're products and quotients of base dimensions. The system doesn't enumerate every derived dimension; it computes them from the bases.

**Color and the presentation values.** Color is a value archetype whose instances are RGB(A) tuples. The presentation vocabularies (LayoutVocabulary, TypographyVocabulary, SpatialVocabulary, VisualVocabulary) build on Color and the dimensional types for their typed slots.

**No IID, no lineage.** Values have no item identity. Two color bodies with identical RGB components hash to the same ContentID and are interchangeable. They don't accumulate history; they don't have manifests; they're not the target of `@`-references in the item sense. References to values use `~` (their byte hash) or appear inline as a binding target (the value body sits in the binding directly).

**Worked examples.** A red Color body. A 5-meter Length body. A 6.022 × 10²³ Amount body (Avogadro's number). A typed Point body. A Color schema, side by side with a Color instance.

**Why values are first-class.** Earlier systems treat typed scalars as primitives (CSS rgba, SQL numeric types) bolted on top of a string-or-int data model. Common Graph treats them as bodies — same machinery as everything else. A custom value type (Rational, Polynomial, Color in a different color space) is the same shape as Color, just a different head and bindings. New value types are vocabulary additions, not language extensions.

**Relations.** datum.md, frames.md, types.md, sememes.md.

---

## 3. `language.md`

Target length: 10–14 KB. Down from 40 KB. The multilingual model: languages as items, lexemes as language-specific lemmas, the connection from text to sememes.

**Opening.** Common Graph holds meaning at the sememe layer; it expresses meaning at the language layer. The same Create sememe is "create" in English, "crear" in Spanish, "作る" in Japanese. The mapping from sememe to surface form goes through Language items.

**Language as an item.** Each language Common Graph supports is an item — `@english`, `@spanish`, `@japanese`, `@german`, `@mandarin`. ISO 639-3 codes get deterministic IIDs: `Language.iid("eng")` is stable across implementations.

**Lexemes.** A lexeme is a frame asserting "this surface form is a name for this sememe in this language." Lexeme bodies carry:
- **THEME** — the sememe being named.
- **NAME** — the surface form, qualified by language and grammatical features (lemma, plural, comparative, …).

A lexeme is endorsed by the Language item whose manifest it belongs to. `@english`'s manifest endorses thousands of lexeme frames, each pointing some surface form at some sememe.

**Cross-language linking via CILI.** When the same concept needs to be named in many languages, CILI (Cross-Lingual Index) provides a shared concept identifier. Each language's lexeme frames point at the CILI-anchored sememe; the surface forms are language-specific but converge on one meaning.

**Grammatical features.** A lexeme's NAME binding can carry qualifiers naming the grammatical features that distinguish its inflections: lemma, plural, past tense, comparative, accusative case, masculine gender. Each feature is itself a sememe; the inventory is open and grows as new languages are added.

**Thematic roles in the linguistic backbone.** The role inventory (AGENT, THEME, GOAL, SOURCE, INSTRUMENT, …) is itself part of the linguistic backbone. Each role is a sememe; the universal ~25-role set comes from Fillmore frame semantics and ISO 24617-4. Roles aren't English-specific — they're language-neutral semantic relationships that any natural language's grammar maps onto.

**Notations as language fragments.** A language as Common Graph models it isn't strictly a natural language. The chess notation system is a "language" too — its lexemes name chess concepts (`Nf3` → @knight + @f3), its parsing rules know SAN. A programming language could be a CG Language. The model is general; natural languages are the most common case.

**The token dictionary.** Lexemes inform the token dictionary — the runtime lookup that maps "create" to the Create sememe. Each language's lexemes contribute postings; lookup is scope-aware (the user's active languages dominate, universal symbols are always available). Full details in `vocabulary.md`.

**Languages and Parley.** When two librarians communicate, they share language items by IID; their lexemes interoperate naturally. The protocol doesn't need a "current language" negotiation — references are by IID, and rendering happens at the receiver's preferred language.

**Worked examples.** "Create" / "Crear" / "作る" as lexemes for the same sememe. "Move" as a chess-notation token versus an English verb (different language items, same surface form, different sememes). German separable verbs and how lexemes handle them.

**Relations.** sememes.md, vocabulary.md, text.md, input.md.

---

## 4. `vocabulary.md`

Target length: 12–16 KB. Down from 48 KB. The runtime vocabulary surface: the token dictionary, scope resolution, the relationship between sememes / lexemes / surface forms / dispatch.

**Opening.** Common Graph's vocabulary is the linguistic surface every interaction passes through. Users type text into a prompt; the text resolves to sememes via the token dictionary; sememes drive parsing, dispatch, and frame assembly. Everything is language; everything goes through this lookup path.

**The TokenDictionary.** A scoped lookup from surface forms to sememe references. `lookup("create", [user-scope, en-scope])` returns the postings that resolve "create" in the given scope chain. Lookup is scoped because the same surface form may name different sememes in different contexts.

**Scopes.** Scopes are item IIDs. A scope chain is an ordered list of scopes, from most-specific to most-general. A token dictionary lookup walks the chain, gathering matches at each level. Typical chain: focused-item, ancestors, session, user, active languages, universal (null scope).

**Postings.** A posting is `(token, target, scope, weight)`. The dictionary indexes postings by `(scope, token)`. Postings come from: lexeme frames (language-scoped), sememes' own symbols (universal-scoped), user-added aliases (user-scoped), item-specific names (item-scoped).

**Universal vs scoped.** Some symbols are universal — `+`, `-`, `*`, `kg`, `m` — they resolve everywhere. These get null-scope postings. Other symbols are language-scoped — "create" is English, "crear" is Spanish. The lookup combines scoped and universal matches.

**Ambiguity and deferred resolution.** A token can resolve to multiple sememes. The lookup returns all matches; the parser carries ambiguity forward as candidate tokens until later context disambiguates. (Details in `input.md` and `text.md`.)

**Dispatch surface.** Beyond mere lookup, vocabulary feeds the dispatch path. An item's HANDLES bindings declare which predicates the item processes; the dispatch flow uses the same vocabulary that drives parsing — the sememes are the link between user input and item behavior.

**Sememes drive everything.** Verbs ("create"), nouns ("notes"), operators ("+"), functions ("sqrt"), structural symbols ("(", ")"), prepositions ("to") — all are sememes, all flow through the same lookup, all participate in the same parse + dispatch flow. There's no separate command parser, separate operator table, separate noun lookup. One pipeline.

**Item vocabulary.** An item brings its own vocabulary into scope when it's focused: its archetype's vocabulary, its own named bindings (which become item-scoped postings), its language stack. A chess game's vocabulary includes chess-notation tokens; a document's vocabulary includes its frame-specific names.

**Composition.** Vocabulary is open. Users add aliases, scripts, custom functions. Items add their own named frames. New sememes get new postings. The dictionary grows by accumulation, not by central registration.

**Worked examples.** Resolving "create" in a session with English active. Resolving "Nf3" in a chess-game context. Resolving "exit" — multiple meanings, scope chain narrows. Adding a personal alias.

**Relations.** sememes.md, language.md, seed-vocabulary.md, input.md, text.md.

---

## 5. `seed-vocabulary.md`

Target length: 8–12 KB. The bootstrap vocabulary pattern: how concepts come into existence at startup, the canonical-key API, application bundles.

**Opening.** Common Graph bootstraps with a vocabulary of well-known sememes — operators, thematic roles, languages, base archetypes, primitive value types. These need to exist in every librarian without explicit creation, and every librarian needs to agree on which item is which sememe. The seed vocabulary mechanism solves this without central registration.

**Canonical-key IIDs.** A bootstrap sememe's IID is computed as `multihash(SHA-256, "<canonical-key>")` where the canonical key is a stable string like `"cg.predicate:add"`, `"cg.role:theme"`, `"cg.lang:eng"`. Independent librarians compute the same IID by hashing the same string. No coordination, no registry, no negotiation.

**The seed pattern.** The bootstrap vocabulary is published at startup as a set of seed items. Each seed has:
- A canonical key (the string that derives its IID).
- A manifest body (head, ITEM_ID, schema bindings, lexemes, glosses).
- Endorsed frames (gloss frames in various languages, lexeme frames, IMPLEMENTS frames if the sememe is a code item, etc.).

Seed manifests are unsigned — they're not assertions by any signer, they're protocol-given. (Or, for application-bundle seeds, they're signed by the application author; see below.)

**Two layers of seed vocabulary.** Common Graph has both a foundation layer (the structural and linguistic concepts every implementation needs) and a domain layer (concepts specific to chess, photography, finance, healthcare, etc.). The foundation layer is small (~100 sememes); the domain layer is unbounded and grows by community curation.

**Application bundles.** An application — a chess game, a document editor, a financial-records system — ships its own vocabulary as a signed bundle. The application archetype's manifest endorses the relevant predicate, archetype, role, and qualifier sememes. Installing the application is loading the bundle's signed seeds; trust in the application is trust in the signer.

**Reuse over invention.** The norm is to reuse existing sememes whenever possible. Building a chess application means using WordNet's existing concepts for "chess," "game," "move," "piece" rather than inventing fresh ones. New sememes are introduced only when the existing vocabulary doesn't cover the concept.

**Sememes are seeds, not foundations.** WordNet, CILI, VerbNet, and ISO 24617-4 are trust-weighted starting points. A community using different empirical work is free to seed its own vocabulary; the protocol doesn't privilege any source. The default seed is provided because most users won't want to rebuild the linguistic backbone, but it's not architecturally privileged.

**Worked examples.** A foundation sememe (Add). An application bundle (a Chess application's seed manifest endorsing chess-specific sememes). A reused sememe (using WordNet's "chess" concept rather than inventing one).

**Relations.** sememes.md, vocabulary.md, item.md, api.md.

---

## 6. `input.md`

Target length: 8–12 KB. Down from 17 KB. The input pipeline — how text becomes frames, the composable-notations model, the unified pipeline across input contexts.

**Opening.** Every way data enters Common Graph runs through the same pipeline. A user typing into a prompt, a programmatic command from a script, a bridge translating an email or a Slack message, an AI agent producing output — all produce frames through one parser, one token dictionary, one frame assembler. Different input contexts differ only in *policy* (how interactive, what confidence threshold, whether ambiguity gets queued for review), not in mechanism.

**The pipeline shape.**
1. **Tokenize.** Break input into candidate tokens via the TokenLattice — whitespace, character-class boundaries, multi-word windows, structural isolation, literal detection.
2. **Resolve.** Each candidate token looks up against the TokenDictionary with the current scope chain; the dictionary returns matching postings (sememe references with weights).
3. **Parse.** Notations contribute parse interpretations to a consensus circle; the orchestrating item merges contributions into a draft FrameMap.
4. **Assemble.** When the consensus settles, the draft is committed as a frame body, signed (if appropriate), and submitted to the librarian.
5. **Dispatch.** The librarian routes the frame to the items it concerns via HANDLES matching; their handlers run.

(Steps 3 and 4 are detailed in `text.md`.)

**Composable notations.** Parsing isn't a single grammar; it's a set of *notations* — small focused parse participants that handle one syntactic phenomenon each. OperatorNotation handles infix/prefix/postfix operators with precedence. FunctionNotation handles `f(args)`. PropertyAccessNotation handles `a.b.c` chains. AssignmentNotation handles `=`. IndexNotation handles `a[i]`. New notation, new parse capability; new Language, new composition of notations.

**Languages as compositions.** A Language item declares which notations it brings into scope. English brings (Operator, Function, PropertyAccess, Identifier, Assignment, …). Chess notation brings (ChessMove, Square, Promotion). SQL brings (SqlNotation, Identifier, a restricted Operator set). Languages compose notations; the parser composes Languages.

**Scope chain.** The active scope chain determines which vocabulary, which notations, and which Languages contribute. The chain is built from the focused item's archetype, its active Languages, the session, and the universal scope. Different chains produce different parse interpretations of the same input.

**Input contexts.** All these enter through the same pipeline:
- **Interactive prompt.** User types; the parser runs incrementally on every keystroke; ambiguity surfaces as chips to disambiguate.
- **Script.** Programmatic input; the parser runs in one shot; ambiguity is an error.
- **Bridge.** An email or message from outside the graph; the bridge's adapter chooses scope and policy.
- **AI agent.** Produced output runs through the same parser; agents don't get a privileged path.

The differences are policy choices: interactive vs. one-shot, confidence threshold, ambiguity handling.

**Tentative frames.** Mid-parse, the assembling frame is *tentative* — interpretable but incomplete. The system surfaces tentative frames to the UI for inspection; users can confirm or correct.

**Worked examples.** A user typing "move pawn to e4" into a chess game. A script submitting "create document". A bridge translating "from:alice@example.com subject:Hello" into a SENT frame. An AI agent producing a structured response.

**Relations.** text.md, vocabulary.md, sememes.md, language.md, frames.md.

---

## 7. `text.md`

Already current from earlier this session. **Light pass only:**

- Update cross-references to point at new docs (canonical.md, values.md, the new layered structure).
- Reconfirm composable-notations language is aligned.
- Update LHS/RHS-style example survivors (already handled in Phase 1, but recheck).
- Drop any stale references to EXPECTS as a predicate.
- Single read-through for tonal consistency with Phase 1 + 2.

Should remain ~34 KB; the structural rewrite isn't needed.

---

## After Phase 3

The linguistic layer is coherent. Phase 4 (runtime) is comparatively small.
