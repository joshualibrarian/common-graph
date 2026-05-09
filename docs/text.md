# Text

Common Graph operates on **frames** — predicate plus role-keyed bindings. But humans operate on **text** — sentences, words, voice, gesture turning into characters. The system needs to translate cleanly in both directions:

- **Parse**: text → frame (many surface forms collapse to one frame)
- **Render**: frame → text (one frame becomes one canonical sentence per language)

Both directions share the same primitive (`FrameMap`), the same vocabulary (sememes carry behavior for both), and the same orchestration hierarchy. Only the active engine differs: parsing runs a consensus circle to resolve ambiguity in input; rendering runs a parameterized walk to produce text from a known frame.

There is no grammar table. There is no parser-generator. There is no LLM. Behavior lives on sememes — the same vocabulary items that carry meaning everywhere else. A new domain becomes parseable and renderable by being installed as new vocabulary; the engine itself doesn't change.

The same machinery handles every parse and every render: interactive UI input on every keystroke, one-shot eval from a CLI, automated production from network requests, multilingual rendering for distinct viewers. The differences are in *who participates*, *what parameters apply*, and *whether the user is available to clarify* — not in the algorithm.

## Core Principles

1. **Frames are the only structured shape.** A predicate plus role-keyed bindings. Parsing produces them; rendering consumes them.
2. **Intermediate state is frame-shaped.** During parsing, contributions are partial frame *pictures*. During rendering, the working FrameMap accumulates spans against frame parts. Same shape both directions.
3. **No native syntax.** Even tokens like `+`, `=`, or `(` are sememes. The parser doesn't know what addition or equality is — the ADD and EQUALS sememes do, and they participate just like any other sememe. (Whitespace is the one exception: it lives at the tokenization layer, separating tokens, not in the vocabulary.)
4. **Vocabulary is everything.** Each meaning lives in exactly one item, sovereign over its own implementation. Anyone who needs that functionality looks it up by canonical key. This is what prevents the system from collapsing into monoliths.
5. **Items are sovereign over their own parsing.** When a chess game is the orchestrator, *the chess game's code* runs the consensus. Item developers shape the parsing experience for their domain by declaring vocabulary and contributing during consensus — not by hooking into a framework.
6. **Languages own rendering.** A Language is the item that knows the syntax, agreement, and lexeme selection rules for its language. Render is a method on Language; everything else just provides data.
7. **Parser and renderer are inverses.** A frame rendered to text in a language must, when parsed in that language, produce the original frame. Parser is many-to-one (many surface forms → one frame); renderer is one-to-many in principle but picks one canonical form per language.
8. **The round-trip law:** `parse(render(frame, params), params).frame == frame`. The text might differ (parser accepts variants; renderer picks a canonical form), but the frame structure round-trips faithfully.

## The Orchestration Hierarchy

**All items run in the Librarian.** The Session has no execution capacity — it is a UI intermediary that arranges views of running items on screen, routes input from the user to the corresponding item, and displays output back. An item being "viewed" in a session just means a view of that item is currently rendered; the item itself runs in the Librarian whether any session views it or not.

```
Librarian              ← runtime: hosts and runs ALL items
                         resource tracking, policy, device-to-device trust, peer access
  │
  ├─ Running items     local items + possibly guest items with budgets
  │   ├─ Item A
  │   ├─ Item B
  │   └─ ...           (each runs in the Librarian regardless of whether viewed)
  │
  └─ Session(s)        UI intermediary; no execution capacity
                       arranges views of running items on screen
                       mediates between the (real human) user and the items
```

For **parsing**, every prompt belongs to exactly one item — possibly the Session item itself (top-level prompt), possibly any item the user has open in a view. **The item that owns the prompt being typed into is the orchestrator.** That item is running in the Librarian; the Session just routes the input to it.

For **rendering**, the orchestrator is the **active Language** — the item that holds the syntactic and lexical knowledge needed to walk a frame and produce text. The presenting item (which has the frame to display) selects the active Language stack and parameters; the Language drives the walk.

## API: parse() and render()

Two methods on the Item base class plus one on Language:

```
class Item {
  // Orchestrator role: input arrived at this item's prompt.
  //   Default: run the standard consensus engine, with this item as ambient context.
  //   Subclasses override to customize orchestration / merge.
  FrameMap parse(String input, ParseParams params);

  // Participant role: this item's contribution to a parse round.
  //   Default: empty (no opinion). Most data items use this default.
  //   Languages, predicate sememes, operator sememes, structural sememes override.
  FrameMap parse(RoundContext ctx);
}

class Language extends Item {
  @Override FrameMap parse(RoundContext ctx);   // language-specific grammar contribution

  // Render method, unique to Languages.
  //   Walks the frame, populates the FrameMap's text and spans, returns it.
  FrameMap render(FrameMap framemap, RenderParams params);
}
```

Two `parse()` methods distinguished by signature: orchestrator entry takes raw input; participant entry takes a round context. Most items inherit both defaults — they don't actively orchestrate anything custom, and they have no opinion to contribute as participants. They just exist as data.

`render()` is Language-only. The Language's render walk queries other items' data via the librarian; it does not call `render()` on them. Custom self-presentation for items (a thermostat showing its reading, a chess game showing a board mini) belongs to the UI/scene pipeline (`presentation.md`), not text rendering.

## Items, Instantiation, and Lookup

Items get instantiated when actively interacted with — opened in a view, running headless (per their running policy), being the orchestrator of a parse, being the active Language. Items that are merely *referenced* (a binding target like `@France` in a frame, a sememe resolution like `chess.square:e4`) do not need to be instantiated. The Librarian provides data lookup without instantiation.

| Trigger | Instantiated? |
|---|---|
| Item is the prompt-owner / orchestrator | Yes |
| Item is the active Language for a parse round or render walk | Yes |
| Item is currently running headless (per running policy) | Yes |
| Item appears as binding target in a frame being rendered | **No** — queried from librarian |
| Item resolves from a token during parsing | **No** — queried from librarian |
| Item provides identifier data (NAME, SERIAL_NUMBER, …) | **No** — queried from librarian |

This means the participant set per parse round is small: orchestrator + active Languages + the handful of sememes whose code actively contributes (predicate sememes with custom behavior, operator sememes, structural sememes). Pure-data items don't participate as code; their data is queried.

## The State Primitive: FrameMap

A `FrameMap` is a frame-shaped structure that pairs text with a frame, with confidence weights on each frame part and span references mapping each part back to the text:

```
FrameMap {
  text:        String                             // input (parse) or output (render)

  predicate:   { value: ItemID,
                 confidence: Decimal,             // typically 1.0 in render; varies in parse
                 spans: [TextSpan] }              // text positions; multi for split forms

  bindings:    [
    {
      role:       { value, confidence, spans }
      qualifiers: [ { value, confidence, spans }, ... ]
      target:     { value, confidence, spans }
    },
    ...
  ]

  languages:   [ LanguageItemID, ... ]            // participating Languages (1+ for mixed-language)
}
```

A `TextSpan` is `(start, end)` half-open into the text — a single shared class used by FrameMap parts, AnchorTable anchors, and TokenLattice token spans alike. **TextSpan offsets are grapheme cluster boundaries per UAX #29** — not bytes, not code points. A grapheme cluster is what a user perceives as one character: an ASCII letter, a CJK ideograph, an emoji ZWJ sequence (👨‍👩‍👧‍👦 is one grapheme), a base letter with combining marks, a regional flag pair, etc. This unit aligns with cursor positioning, click selection, and chip highlighting in the UI, which is what spans exist to support. Use ICU's `BreakIterator.getCharacterInstance(locale)` to walk boundaries.

A FrameMap part can have multiple spans because a single sememe may surface in multiple non-contiguous text positions (German separable verbs, infixes, agreement markers, clitics). Each span carries the source provenance — that's the whole UI chip mechanism for free.

The same shape serves multiple roles:
- **Draft** in parsing — orchestrator's running consensus, regenerated each round
- **Delta** in parsing — each participant's contribution per round (mostly empty for most participants)
- **Output** in rendering — Language's accumulated text + provenance after the walk

A "lock" is just confidence `1.0`. There is no separate lock state. Anything weighted at the maximum cannot be outweighed.

**On structural text** (articles, prepositions, copulas, particles inserted by Language rules but not corresponding to frame bindings): the implicit rule is — *any text span not attributed to a frame part is a lexical artifact of one of the participating Languages.* No per-span attribution to specific Language rules; the `languages` list is enough. UI doesn't benefit from finer granularity, and debugging can re-run the render with logging if needed.

---

# Parsing (text → frame)

The parser takes input and runs a **consensus circle**: every participant that has a stake in interpreting the input emits a candidate FrameMap delta with confidences. The orchestrator merges these deltas by weight until the picture stops changing. That settled picture is the frame.

## Participants

The participant set is computed per round from anchors:

| Participant | Anchored to |
|---|---|
| Token-resolved sememes whose code actively contributes | the token(s) that produced this sememe in resolution |
| Vocabulary sememes (Languages, structural sememes, lineage) | the orchestrator's existence |
| Session, Librarian | their own lifetimes |
| User clarifications | the input token-span the user was clarifying |

When an anchor dies, the participant leaves. Anchors are the **only state maintained between rounds and between edits.** Everything else is fresh per round.

Pure-data sememes (countries, plain nouns, ordinary references) do not need to participate as code — their data is queried by Languages and other participants directly via the librarian.

## The Consensus Round

```
keystroke arrives
  → orchestrator computes participants from current anchors
  → orchestrator builds roundContext: text + draft (initially empty for round 1)
  → for each participant: delta_i = participant.parse(roundContext)
  → orchestrator merges {draft, delta_1, delta_2, ...} → new draft
  → if new draft == prior draft (fixpoint): DONE
  → else: next round (using new draft as prior)
```

Rounds continue until fixpoint. The convention for "stable" is **two rounds running with no proposed change** — simple to implement, tunable later.

## Merge

The merge is item-implemented (sovereign per orchestrator, on `Item.parse(input, params)`'s default path). The default is **weighted reconciliation per part:**

- For each part of the frame (predicate, each binding's role/qualifiers/target):
  - Collect all proposals from this round's deltas + the prior draft
  - Highest-weighted proposal wins; ties broken consistently (e.g., by participant priority)
  - Compatible proposals (same value) consolidate; their weights combine (sum or bounded combinator)
- Bindings whose enclosing predicate didn't win are discarded (they don't apply to a different predicate's frame)
- Binding-only contributions (no predicate opinion) apply to the winning predicate's frame
- Locked parts (confidence `1.0`) cannot be outweighed; they pass through

A bad item can implement a bad merge. That's life — items are sovereign, and bad parsing behavior is the item's responsibility, not the framework's.

## The User as Special Participant

The User has the same `parse(roundContext)` interface as every other participant, but a different policy:

- Most rounds, contributes empty FrameMap (no opinion)
- When the user explicitly clarifies — selects from a dropdown, picks a completion, confirms an alternative — the clarification is recorded as `(token-span, FrameMap-with-max-weights)` in the User's anchored history
- Each round, the User-participant walks its history, drops entries whose anchor tokens no longer exist in the input, and returns the surviving entries as max-weighted contributions

User-anchored choices persist across keystrokes as long as their anchor tokens remain. If the user deletes the relevant text, the anchor dies and the clarification is forgotten.

## Cross-Keystroke Behavior

Each keystroke starts a **fresh consensus process.** The previous process's draft is gone. What survives:

- Sememes whose anchor tokens are still in the input
- User clarifications whose anchor token-spans are still in the input
- Standing participants (orchestrator, Session, Librarian, vocabulary scope, active Languages)

Every keystroke recomputes from current input + surviving anchors.

## Convergence and Finalization

Fixpoint reached:
- **Complete** — all required roles filled, frame valid → ready to commit
- **Underdetermined** — required roles unfilled, or competing predicates within margin → tentative

For a complete fixpoint, the user pressing `Enter` (or the eval call returning) commits the frame. A `LOCATION` binding tying the frame to its origin (the orchestrating item, the time of creation) is auto-attached on commit. The signing manifest endorses it.

For an underdetermined fixpoint:
- **Interactive mode** — surface the partial frame to the UI as chips (rendered via the Language's `render`), with unfilled required roles displayed as completion slots and competing alternatives shown as a dropdown. The user's selection feeds back as a max-weight delta in the next round.
- **One-shot mode** — error. The input was insufficient to produce a unique frame.

## Tentative Frames as UI Primitive

The draft FrameMap *is* the tentative frame. The Language's render walk turns it into chips for display. Each span in the FrameMap carries source provenance, so chips know what sememe they came from. Competitors (alternative drafts that lost the merge) are derived from this round's high-weight-but-not-winning deltas; they are surfaced as the dropdown alternatives.

This means the user is always looking at the parser's actual current state, expressed as a frame, rendered into their language. There is no parser internal that they can't inspect.

## Consultation Across Items

When an orchestrator's parse is underdetermined and the missing information might live in *another* item, the orchestrator can ask its outer scope: "I have a partial resolution; does anyone here have stake in this?"

For example: typing `e4` into a Session prompt with no specific item context. The Square sememe resolves cleanly, but no item is contributing "I expect a MOVE." The Session asks Librarian: "any items running that take Square bindings?" Librarian's reverse-binding index returns the user's three open chess games. Each is consulted — gets a chance to contribute a candidate frame with a validity score. The dropdown ranks them.

Consultation is type-filtered (driven by the resolved sememe's EXPECTS), so it doesn't broadcast to every item in the session. The librarian's existing index covers the lookup.

## Type Expectations Are Just Contributions

When a chess game contributes "I expect a MOVE here," it's not a special kind of contribution. It's a partial FrameMap — predicate filled, no bindings — with a confidence weight. The merge treats it the same as any other contribution: it influences the winning predicate by being weighty, and any later contribution that aligns with the chess MOVE predicate consolidates with it.

This collapses what would otherwise be a separate "type expectation" mechanism into the same pattern. There is one shape, one merge.

## Vocabulary Scope

Each item brings a vocabulary stack — its own vocabulary plus its IMPLEMENTATION lineage's vocabularies. When an item is the orchestrator, its full stack is in scope:

- The item's own declared vocabulary
- Its archetype's vocabulary (e.g., the Chess archetype's MOVE, Piece, Square sememes)
- Languages declared by the archetype (e.g., chess notation Language)
- Outer layers: Session vocabulary (general), Librarian's universal vocabulary

Token resolution walks the stack inner-to-outer, with inner scopes preferred. Multiple sememes can resolve from the same token; all become candidates whose claims are resolved by the consensus circle.

There is no "switch language" mechanism. Multi-language scenarios are emergent: if a user types something that draws on multiple Languages in scope, all contribute, the merge picks. SQL-and-English mixed in a prompt simply works because both are vocabulary scopes.

## Worked Examples

### Example 1 — `e4` in a chess game prompt

Setup: chess game item (instance "rematch", Alice to move) is the orchestrator. User types `e4`.

Participants: chess game (orchestrator), chess archetype's vocabulary stack, chess notation Language, English Language, Session, Librarian, User (no anchored clarifications), `chess.square:e4` sememe (data — queried, not instantiated).

Round 1 deltas:

```
chess game:
  { predicate: { chess.predicate:move, 0.95 } }   -- declares predicate-of-interest

chess notation Language:
  { predicate: { chess.predicate:move, 0.85 },
    bindings:  [ { role: GOAL, target: e4-square, 0.9 } ] }   -- bare square in MOVE → GOAL (SAN)

Session, Librarian, User: empty
```

Merge:
- Predicate: chess.predicate:move agrees → wins, locks
- GOAL binding: e4-square consistent → fills, locks

Draft: `chess.MOVE { GOAL → e4-square }`. Context-fill (a separate phase) populates AGENT, THEME, SOURCE from chess game state. Fixpoint reached. Language's render produces chip display; user presses Enter to commit.

### Example 2 — `e4=42` cross-keystroke

After `e4` settled to a chess MOVE, the user types `=`. New process.

Tokens: `["e4", "="]`. New participant: EQUALS operator sememe (anchored to `"="`).

Round 1 deltas:

```
EQUALS sememe:
  { predicate: { cg.predicate:equals, 0.95 },
    bindings:  [ { role: LHS, target: <previous-token>, 0.9 } ] }

chess game:
  { predicate: { chess.predicate:move, 0.7 } }   -- still expects MOVE, lower confidence

chess notation Language:
  empty   -- e4= isn't a SAN pattern that fits
```

Merge:
- Predicate: EQUALS (0.95) > MOVE (0.7) → EQUALS wins
- LHS binding: e4-square (consistent with EQUALS' "previous token")

Draft: `EQUALS { LHS → e4-square, RHS → ? }`. Underdetermined.

User types `4`, then `2` — tokens become `["e4", "=", "4"]`, then `["e4", "=", "42"]`. Each keystroke is a fresh process. The "42" sememe resolves to literal Decimal 42. EQUALS' delta updates to fill RHS. Final draft: `EQUALS { LHS → e4-square, RHS → 42 }`. User presses Enter; frame commits.

What the chess MOVE machinery did: kept bidding at moderate confidence, kept losing. No special "withdraw MOVE" logic. Withdrawals are absences.

### Example 3 — `5+3*2` operator precedence

Tokens: `["5", "+", "3", "*", "2"]`. Each digit-token resolves to a literal sememe (data lookup). Each operator-token resolves to an active operator sememe (participant).

Operator sememes contribute frames whose weights encode precedence:

```
ADD sememe:
  { predicate: { cg.predicate:add, 0.7 },        -- weight encodes precedence
    bindings: [ { role: LHS, target: 5, 0.8 },
                { role: RHS, target: 3, 0.8 } ] }

MULTIPLY sememe:
  { predicate: { cg.predicate:multiply, 0.85 },  -- higher precedence = higher weight
    bindings: [ { role: LHS, target: 3, 0.8 },
                { role: RHS, target: 2, 0.8 } ] }
```

Both ADD and MULTIPLY claim `3`. The merge resolves: MULTIPLY (higher weight) keeps `3`. ADD's `RHS → 3` re-resolves to `RHS → MULTIPLY{...}` — the loser nests inside the winner's parent.

Result: `ADD { LHS → 5, RHS → MULTIPLY { LHS → 3, RHS → 2 } }`. Operator precedence falls out of weighted merge — no special precedence machinery.

### Example 4 — polysemy and consultation

User types `queen` into the bare Session prompt.

Token `queen` resolves to multiple sememes via the TokenDictionary: `chess.piece:queen`, the band `Queen`, `Queen Elizabeth`, `bee.queen`, etc. None are instantiated; they're just data candidates.

No item is contributing a strong predicate-of-interest. Session contributes broadly. Result: highly underdetermined draft. UI surfaces a disambiguation menu: "queen" → [chess piece, band, monarch, bee role, ...]. User picks. Their selection is recorded as a max-weight User contribution anchored to the `queen` token. Next round, that contribution dominates; the chosen sememe wins.

Compare: the same `queen` typed into a chess game prompt resolves cleanly because the chess game contributes "I expect MOVE" with chess vocabulary boost — only `chess.piece:queen` makes structural sense.

### Example 5 — local domain shadowing universal

User types `move` into a chess game prompt. Multiple MOVE sememes in scope: `chess.predicate:move`, `files.predicate:move`, `ui.predicate:move`.

The chess game contributes "I expect chess.predicate:move" with high weight. Other MOVE sememes are technically reachable but no item is expecting them. The chess MOVE wins by salience — but the others remain in lower-ranked competitors, accessible to the user if they really wanted a file move.

Shadowing is emergent from contribution weights. There is no `if (in_chess_game)` switch anywhere.

---

# Rendering (frame → text)

The Language takes a frame and walks it, populating the FrameMap's text and spans. Parameters from the presenter shape every choice: which Language stack, which mode, what verbosity, what locale, what register. The walk is deterministic given (frame, parameters).

## Language as Orchestrator

Where parsing is orchestrated by the item-with-the-prompt, **rendering is orchestrated by the active Language.** It owns the syntactic knowledge — word order, agreement, lexeme selection, structural insertion, agreement morphology. The presenting item just sets parameters and invokes:

```
languageStack.primary().render(framemap, params) → framemap-with-text-populated
```

The Language is already running (it's been a parse participant, or it's loaded as the user's preferred language). Rendering is just another method call on it.

## Render Parameters

Set by the presenter, flow down through the walk, sometimes change for nested contexts (embedding mode in particular):

- **Language stack** — list of Languages with priority order (e.g., `[chess-notation, English]` inside a chess game; `[Japanese, English]` for a polyglot user)
- **Locale** — number formats, dates, plural rules (en-US, en-GB, ja-JP, de-DE)
- **Mode** — chip / flat-text / voice / accessible / ...
- **Verbosity** — terse / normal / verbose
- **Register / tone** — formal / casual / technical
- **Salient referents** — for pronoun selection, ellipsis, definite-vs-indefinite article
- **Embedding mode** — top-level clause vs noun phrase vs adverbial; flows down for sub-frames

## Language Stacking

Same pattern as vocabulary stacking in parsing. The presenter declares an ordered list of Languages; the renderer walks the stack inner-to-outer when looking up a rule:

- Inside the chess game: `[chess-notation, English]` — chess-notation wins for chess-specific predicates; English fills the rest
- In Session prose: `[English]` only
- In a polyglot context: `[Japanese, English]` — Japanese wins where it has rules; English fills

When the active Language doesn't have a rule for some predicate or sememe, it delegates to the next Language in the stack. Mixed-language outputs are emergent — a chess move might render as English prose with the move itself in SAN.

## What Languages Do

A Language is a sovereign code item. Its `render` implementation can use any combination of declared frame data and code:

**Frame-shaped data the Language declares:**
- Word-order skeletons per predicate-shape category (declarative, question, command)
- Default role-to-position mappings (AGENT → SUBJECT in nominative-accusative languages, AGENT → ergative in ergative languages)
- Default prepositions per role (English: SOURCE → "from", GOAL → "to")
- Default article rules (English: countable-singular indefinite → "a/an")
- Reading direction (`READING_DIRECTION { THEME → English, DIRECTION → ltr }`; `rtl` for Arabic; `vertical-rtl` for traditional Japanese)
- Predicate-specific overrides (chess-notation Language declaring "for chess.MOVE, just emit GOAL")

**Things that stay as code per-Language:**
- Allomorphy (English "a/an" by phonological context)
- Irregular morphology (queried via lexeme frames, but assembled by code)
- Verb conjugation with edge cases
- Agreement across long distances (German Mittelfeld, polysynthetic constructions)
- V2 mechanics with separable verbs (German)
- Topic-comment promotion (Mandarin, Japanese)
- Classifier selection (Mandarin, Japanese)
- Broken plurals and root morphology (Arabic)

The pragmatic split: simple rules as data, complex rules as code, mix per Language. New Languages = new sememes. The framework's contract is just `render(framemap, params) → framemap`.

Languages are typically `@Embodies` singletons — one canonical English item, one canonical German, one canonical chess-notation, etc. Forking is possible via the trust matrix; if you don't trust someone's English fork, you don't run it.

## Identifier Lookup via Hypernym Tree

When the render walk encounters an item-target and needs its surface form, it queries the item's identifier predicates. Identifier predicates form a hypernym tree:

```
IDENTIFIER (root)
  ├─ NAME           — human-readable label
  ├─ TITLE          — formal name
  ├─ SURNAME        — family name
  ├─ GIVEN_NAME
  ├─ SERIAL_NUMBER
  ├─ MODEL_NUMBER
  ├─ MAC_ADDRESS
  ├─ ITEM_ID
  ├─ CILI_ID
  └─ ...
```

The Language traverses the subtree appropriate for the params:
- Friendly mode → prefer NAME
- Technical mode → prefer SERIAL_NUMBER, MODEL_NUMBER
- Network introspection → prefer MAC_ADDRESS
- Polite/formal contexts → prefer TITLE + SURNAME

The item declares whichever identifier predicates it has data for; the Language picks the right one given context. No method call on the item — pure data lookup via the librarian.

## Multi-Span Attribution (German Separable Verbs)

A single sememe can contribute multiple non-contiguous spans. German `abholen` (pick up) in present tense splits across the sentence:

```
Frame:  PICK_UP { AGENT → ich, THEME → das-paket }   in German, present tense

FrameMap.text: "Ich hole das Paket ab"

predicate.spans: [(4..8), (19..21)]     // "hole" at V2 position; "ab" at sentence end
predicate.value: cg.verb:pick-up        // the same sememe, one source

bindings:
  AGENT.target.spans:  [(0..3)]          // "Ich"
  THEME.target.spans:  [(13..18)]        // "Paket"
```

Both "hole" and "ab" have the same source pointer (the abholen sememe). Chip UI highlights them together on hover. The Language's word-order rule placed each part at its required position.

This pattern handles infixes, clitics, distant agreement, separable verbs, and any other case where a sememe surfaces in multiple text positions.

## Worked Examples — Rendering

Frame: `chess.MOVE { AGENT → Alice, THEME → king-pawn, SOURCE → e2, GOAL → e4 }`. Same frame, different render contexts:

| Presenter | Language stack | Mode | Verbosity | Output |
|---|---|---|---|---|
| chess game (move list) | `[chess-notation, English]` | chip | terse | `1.` `e4` chips |
| chess game (move list) | `[chess-notation, English]` | flat | terse | `1. e4` |
| Session command history | `[English]` | flat | normal | `Alice moved her king's pawn from e2 to e4` |
| Chess-naive friend's view | `[English]` | flat | normal | `Alice played pawn to e4` |
| Japanese learner's view | `[Japanese, English]` | flat | verbose | `アリスはe2のキングポーンをe4に動かした` |
| Voice-mode for blind user | `[English]` | speech | normal | (audio): "Alice moves king's pawn to e four" |

Same frame. Different parameters from the presenter. Different outputs from the Language's `render`. Round-trip law holds for each output: parsing it in the same Language stack would reproduce the same frame.

## Round-Trip Discipline

```
text₀ → [parse] → FrameMap₁ (text₀, frame derived)
           → [render with same params] → FrameMap₂ (text₂, derived from frame)

parse(text₂, params) should produce a FrameMap whose frame structure equals FrameMap₁'s
```

The text might differ (`text₂ ≠ text₀`) because the parser accepts variants and the renderer picks a canonical form, but the *frame* round-trips. Easy to verify in tests.

This is the discipline that keeps parser and renderer aligned: every renderer output must be valid input to the parser; every parser-accepted form should round-trip its frame through render.

---

# What's NOT in This Design

- **No special "lock" state.** Locks are just max weight (1.0).
- **No meta-frames.** Parser/renderer state is in-memory data structures, not graph frames.
- **No grammar tables, no parser-generators.** Behavior lives on sememes.
- **No hardcoded special cases.** New parse/render behavior = new sememes contributing.
- **No LLM.** Both directions are deterministic given inputs. Flexibility comes from richness of declared meaning, not probabilistic guessing.
- **No probabilistic guessing.** Confidences are declared by participants, not learned.
- **No "modes" or "context switching."** Same machinery; different vocabulary scopes / parameters produce different outputs.
- **No per-span Language-rule attribution in the FrameMap.** The implicit rule "non-frame-part text is from a participating Language" is sufficient.
- **No `render()` on data items.** Languages do all rendering work via librarian lookups; data items contribute identifier predicates and other declared frames.

---

# Connection to Presentation

The text rendering described here produces a `FrameMap` with text + spans + provenance. The UI consumes that as chips, plain text, voice, or whatever the rendering mode dictates.

Visual presentation of items beyond their text rendering — scenes, layouts, fidelity chains, interactive widgets, body/container/text scene primitives — belongs to `presentation.md`. UI labels in scenes can carry sememe references (or full FrameMaps) and resolve to text at render time via this pipeline. That's the i18n bridge: scene labels are language-neutral until the user's parameters render them.
