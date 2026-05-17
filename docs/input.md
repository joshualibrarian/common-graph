# Input

Every way data enters Common Graph runs through the same pipeline. A user typing into a prompt, a programmatic command from a script, a bridge translating an email or a Slack message, an AI agent producing output — all produce frames through one tokenizer, one token dictionary, one consensus circle, one frame assembler. Different input contexts differ only in *policy* (how interactive, what confidence threshold to require, whether ambiguity gets queued for review), not in mechanism.

The unified pipeline is what makes Common Graph extensible at the surface. New input contexts — voice, gesture, AI, new bridges — plug in by configuring policy, not by adding parsers.

This document defines the input pipeline's stages, how composable notations contribute parse interpretations, and how the same machinery serves interactive, scripted, and bridge-driven input.

This document assumes familiarity with [vocabulary](vocabulary.md), [sememes](sememes.md), [language](language.md), [frames](frames.md), and [text](text.md).

## The pipeline

```
text in
  ↓
[1] tokenize
  ↓
[2] resolve
  ↓
[3] parse
  ↓
[4] assemble
  ↓
[5] dispatch
  ↓
frame submitted, dispatched, observable result
```

Five stages, each well-defined, each consuming the previous stage's output.

**[1] Tokenize.** Break input into candidate tokens via the **TokenLattice**. The lattice considers multiple tokenization strategies — whitespace boundaries, character-class transitions, multi-word lookup windows, structural-symbol isolation, literal detection (numbers, quoted strings, booleans). Each token is a candidate span in the input; ambiguous spans (the same letters could be one long token or two short tokens) leave both alternatives in the lattice for later disambiguation.

**[2] Resolve.** Each candidate token looks up against the **TokenDictionary** with the current scope chain. The dictionary returns matching postings — `(token, sememe, scope, weight)` tuples. A token that resolves to multiple sememes (different scopes, different parts of speech) carries all candidates forward; deferred resolution waits for context. A token that resolves to a single sememe locks immediately.

**[3] Parse.** Composable **notations** contribute parse interpretations to a **consensus circle**. Each notation handles one syntactic phenomenon (infix operators, function calls, property access, …) and emits a FrameMap delta — a partial picture of the assembling frame with confidence weights on each part. The orchestrating item (typically the focused item) merges deltas across rounds until the picture stabilizes.

**[4] Assemble.** When the consensus settles, the FrameMap's frame structure is extracted as a body and signed (in interactive contexts, on user confirmation; in scripted contexts, immediately; in bridge contexts, by the bridge's identity). The result is a complete, signed frame ready to submit.

**[5] Dispatch.** The frame is submitted to the librarian. The librarian routes it: items referenced in the frame's bindings get `onFrameAssembled` callbacks; items whose archetypes have matching HANDLES bindings get dispatched. The frame's handler runs; reply frames produced re-enter the pipeline.

Stages 1–4 are the parsing pipeline (detailed in [`text.md`](text.md)). Stage 5 is the dispatch path (detailed in [`api.md`](api.md)). This document covers how the pipeline serves *different input contexts*.

## Composable notations

Parsing isn't driven by a single grammar; it's driven by a *set of notations* — small focused parse participants, each handling one syntactic phenomenon. The default notation set includes:

- **OperatorNotation** — handles infix/prefix/postfix operators with declared precedence and associativity. Covers arithmetic (+, -, *, /), comparison (==, !=, <, >), logical (&&, ||, !), set (∪, ∩), pipe (|>), and anything else operator-shaped.
- **FunctionNotation** — handles `f(args)`-shaped fragments. Owns argument grouping and separator handling.
- **PropertyAccessNotation** — handles `a.b.c` dot-chains. Useful inside English, math, code-like prompts, chess.
- **AssignmentNotation** — handles `=` with target/source asymmetry.
- **IndexNotation** — handles `a[i]` bracket indexing.
- **IdentifierNotation** — handles bare token-resolved sememes that fill binding slots.

Languages compose notations. English brings `[Operator, Function, PropertyAccess, Assignment, Index, Identifier]`. Chess notation brings `[ChessMove, Square, Promotion]`. SQL brings `[SqlNotation, Identifier, OperatorNotation-subset]`. A new language is a new composition; a new syntactic phenomenon is a new notation.

The parser's job is to compose the active notations and let them contribute. No single grammar binds them; the consensus circle merges contributions by weight. Details in [`text.md`](text.md).

## Scope chain

The active **scope chain** determines which vocabulary, which notations, and which languages contribute at parse time. The chain is built from:

- **The focused item's archetype's vocabulary** — what does this item's type know about?
- **The focused item's own vocabulary** — what named entities live in this item?
- **The session's vocabulary** — general system commands.
- **The user's vocabulary** — personal aliases, scripts.
- **The active languages** — natural and notational, in priority order.
- **Universal scope** — operators, units, structural symbols.

Different chains produce different parse interpretations of the same input. "move" in a chess game's chain resolves to the chess MOVE predicate; "move" in a file-system context resolves to a file-rename operation. Same surface form, different sememes by scope.

## Tentative frames

Mid-parse, the assembling frame is **tentative** — interpretable but incomplete. The system surfaces tentative frames to the UI for inspection in interactive mode; users can confirm, correct, or wait for more input.

A tentative frame might have:
- A predicate locked but several binding slots empty.
- A predicate and bindings present but some target values ambiguous.
- Two competing predicates with similar weights.

The UI renders tentative frames as **chips** — visual fragments showing what's resolved so far. The user sees the parser's state directly, expressed as the data being assembled. There's no parser internal that's hidden from inspection.

If the user keeps typing, the chips update. If the user submits, ambiguity blocks submission until resolved (or, in lenient policies, the most-likely interpretation wins). If the user explicitly disambiguates by clicking or selecting, the chosen interpretation feeds back into the next round with maximum weight.

## Input contexts

The pipeline serves several distinct contexts. Each differs in *policy*, not mechanism.

### Interactive prompt

A user types; the pipeline runs incrementally on every keystroke. The current draft FrameMap is surfaced as chips; ambiguity is rendered as visible candidate sets; the user can resolve by clicking, by typing more, or by pressing Enter to submit (which forces a final resolution pass).

Policy:
- High interactivity — the parser runs per-keystroke.
- Tolerant of ambiguity — multiple interpretations stay alive until disambiguated.
- Explicit user disambiguation when stuck.

### Scripted invocation

A program submits a complete text expression; the pipeline runs once. No incremental rendering, no per-keystroke updates; the parser consumes the whole input, produces a frame, submits.

Policy:
- One-shot — no rendering.
- Strict on ambiguity — multiple interpretations is an error.
- No user fallback — the script either parses unambiguously or fails.

### Bridge translation

A bridge (email, Slack, SMS, HTTP form) translates an external message into a frame. The bridge owns the input adapter — it knows how to extract relevant fields from the external format and feed them to the pipeline.

Policy:
- One-shot per message — no interactivity.
- Bridge-specific scope chain — the bridge's adapter declares which vocabulary applies.
- Bridge identity as the signer — the bridge signs the frame on the user's behalf (with explicit user authorization for that bridge to do so).

### AI agent output

An AI agent produces structured or semi-structured output. The output runs through the pipeline like any other input. Agents don't get a privileged path; their output is parsed the same way a human's typing is.

Policy:
- One-shot.
- Tolerant or strict based on context.
- Agent identity as the signer.

### Other contexts

Voice input transcribes to text and feeds the pipeline. Gesture-driven UIs build text representations of gestures. Bar-code and QR-code scanners produce input strings. Anything that can produce text-shaped input can be plugged in by writing an adapter that produces tokens and a scope chain.

## Ambiguity handling

Deferred resolution is the system's main tool for handling ambiguity. Rather than resolve every token at first encounter, the pipeline carries multiple candidates forward:

- A token resolves to multiple sememes → keep all candidates; let context narrow.
- A binding has multiple valid sources → keep alternatives weighted; let the merge settle.
- A frame's predicate is uncertain → multiple competing FrameMap drafts; surface alternatives.

Resolution happens as late as possible. In interactive mode, the user can intervene at any point. In scripted mode, the parser commits when forced (at submission); if still ambiguous, it errors out.

The mechanism is the same in both cases. Policy decides whether to wait for the user or to error.

## Worked examples

**A user typing "move pawn to e4" into a chess-game prompt.**

```
[1] tokenize:  ["move", "pawn", "to", "e4"]
[2] resolve in [@chess-game, @chess-notation, @english, null]:
       move → @cg-verb-move (English)
       pawn → @chess-pawn-piece (chess vocab)
       to   → @to-preposition (English)
       e4   → @e4-square (chess vocab)
[3] parse: composable notations contribute:
       OperatorNotation: no contribution (no operators).
       FunctionNotation: no contribution (no parens).
       IdentifierNotation: maps each token to a binding slot.
       English's prepositional notation: "to <X>" → GOAL → X.
   Consensus FrameMap (after merge):
       predicate: @chess-move
       bindings: AGENT (inferred from session signer),
                  THEME → @chess-pawn-piece,
                  GOAL → @e4-square,
                  LOCATION → @<game-iid>
[4] assemble: build frame body, sign with user's key.
[5] dispatch: @<chess-game> has @HANDLES → @chess-move; route to its move handler.
```

**A script submitting `create document {title: "Notes"}`.**

```
[1] tokenize:  ["create", "document", "{", "title", ":", "\"Notes\"", "}"]
[2] resolve: create → @create, document → @document-archetype,
              title → @title-binding-role, "Notes" → literal string.
[3] parse: tokens form a CREATE frame with THEME (document archetype)
           and a nested binding map.
[4] assemble: frame body, signed with the script's key.
[5] dispatch: @<librarian> handles CREATE; mints a new document item.
```

**A bridge translating an email.**

```
Incoming email:
  From: alice@example.com
  Subject: Re: project status
  Body: ...

Bridge adapter produces tokens:
  ["sent", "by", "@alice", "to", "@me", "subject", "...", "body", "..."]

Pipeline runs:
  predicate: @sent
  AGENT → @alice
  RECIPIENT → @user
  SUBJECT → "Re: project status"
  CONTENT → "..."

Bridge signs with its delegated key, submits.
```

## Why one pipeline

Earlier systems separate input paths by source — a command parser for the CLI, a query parser for the database layer, a parser for each bridge, special-cased AI input handling. Each parser carries its own grammar; each bridge carries its own translation logic; the total complexity is the union of all paths.

Common Graph picks one pipeline at the architectural layer. New input sources plug in by writing an adapter that emits tokens and a scope chain — that's the only integration surface. Vocabulary, parsing, dispatch all stay the same. Adding voice input doesn't require a new parser; adding a new bridge doesn't require new frame-assembly logic; adding AI agents doesn't carve out a privileged path.

This is what lets the surface evolve without rewriting the substrate. The pipeline is small enough to keep stable; the adapters above it are where novelty lives.

## Relations

- [`text.md`](text.md) — the consensus circle, FrameMap, merge mechanics in detail.
- [`vocabulary.md`](vocabulary.md) — the token dictionary the pipeline consumes.
- [`sememes.md`](sememes.md) — what tokens resolve to.
- [`language.md`](language.md) — language items and their notation compositions.
- [`api.md`](api.md) — the dispatch path the pipeline feeds.
- [`frames.md`](frames.md) — the frames the pipeline produces.
- [`bridges.md`](bridges.md) — bridge adapters as input contexts.
