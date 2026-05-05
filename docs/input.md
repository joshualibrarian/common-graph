# Input

Every way data enters Common Graph — a user typing into a prompt, a programmatic command from a script, a query expression, a Slack message arriving via bridge, an email translated by an SMTP gateway, an AI agent producing output, an HTTP form submission — runs through **the same pipeline**.  There is no separate import path, no separate command parser, no separate query parser, no separate bridge handler.  There is one parser, one token resolution stage, one frame assembler.  Different input contexts differ only in **policy configuration** — how interactive to be, how to handle ambiguity, what confidence threshold to require, whether to queue uncertain cases for review.

This document describes the unified input architecture, why it's structured this way, and what falls out of it.

> For the resolution pipeline implementation details, see [Vocabulary](vocabulary.md).  For sememes, lexemes, and language items, see [Language](language.md).  For external-system integration, see [Bridges](bridges.md).  For queries (which are also input), see [Query](query.md).

## The unifying claim

**Every input event is `eval(input, language, policy) → frame(s)`.**

- `input` is whatever surface form arrived: a string the user typed, a Slack message body, a CLI command, a SPARQL query, an HTTP form payload, a Lisp s-expression, a Python function call.
- `language` is the Language item whose parser interprets that surface form.  English, Spanish, Mandarin, Chess Notation, Lisp, miniKanren, SPARQL, SQL — all are Language items.  Each has its own `parse()` implementation that tokenizes and structures input according to its own grammar.
- `policy` is a configuration that governs decisions the parser has to make: how to handle ambiguity, whether to prompt, what confidence threshold to require, what to do on failure.
- The output is one or more frames — semantically resolved, ready to be persisted, signed, or evaluated further.

This is a single function with a single implementation.  The differences between an interactive editor, a Slack bridge, and a one-shot CLI command are differences in `policy`, not differences in code path.

## Why this works

It works because **frames are the universal data primitive**, and any process that produces frames can plug into the rest of the architecture identically.  A Slack message that arrives via bridge becomes one or more frames; once they exist, they're indistinguishable from frames a user typed.  Trust evaluation, indexing, signing, querying, presentation — all operate on frames, regardless of how those frames came into being.

The pipeline that produces frames from surface text is itself well-defined:

```
surface input
   ↓
Language.parse() — tokenize, resolve sememes, structure
   ↓
PredicateBehavior.contribute() — each resolved sememe contributes parsing behavior
   ↓
FrameAssembler — accumulate evidence, build frame structure
   ↓
disambiguation (per policy: interactive prompt, fail, fallback, queue)
   ↓
resolved frame(s)
```

This pipeline is the same whether the surface input came from a person, a Slack import, a CLI invocation, or another machine.  See [Vocabulary](vocabulary.md) for the implementation details.

## Policy configurations

What differs across input contexts is **policy** — a configuration object passed to `eval()`.  Plausible policy fields:

```
policy:
    interactivity         : PROMPT_USER | BATCH_SILENT | FAIL_FAST
    ambiguity_resolution  : USER_DISAMBIGUATE | FALLBACK_TO_MESSAGE | RETURN_CANDIDATES
    confidence_threshold  : 0.0 .. 1.0
    review_queue          : NONE | QUEUE_FOR_HUMAN | QUEUE_FOR_AI
    visual_feedback       : LIVE | NONE
    on_partial_resolution : COMMIT_PARTIAL | REJECT | RETURN_INCOMPLETE
    signing_principal     : (which signer commits the resulting frames)
```

The exact field set is design-flexible; the principle is that all the contextual behavior of an input mode is captured here, separated from the parsing core.  Some example configurations:

**Interactive composition (a user typing in CG):**
```
interactivity:        PROMPT_USER
ambiguity_resolution: USER_DISAMBIGUATE
confidence_threshold: low (resolve liberally, prompt if needed)
visual_feedback:      LIVE
on_partial_resolution: COMMIT_PARTIAL  (frame assembles incrementally as user types)
```

**Slack bridge inbound:**
```
interactivity:        BATCH_SILENT
ambiguity_resolution: FALLBACK_TO_MESSAGE  (cannot prompt; fallback to coarse predicate)
confidence_threshold: high (only commit semantic frames when very confident)
review_queue:         QUEUE_FOR_AI  (review uncertain ones with AI assistance later)
```

**CLI one-shot command:**
```
interactivity:        FAIL_FAST
ambiguity_resolution: USER_DISAMBIGUATE  (interactive shell can prompt)
on_partial_resolution: REJECT  (don't commit half-parsed)
```

**Programmatic API (machine caller):**
```
interactivity:        BATCH_SILENT
ambiguity_resolution: RETURN_CANDIDATES  (let the caller decide)
on_partial_resolution: RETURN_INCOMPLETE
```

**Bulk import / re-curation:**
```
interactivity:        BATCH_SILENT
review_queue:         QUEUE_FOR_HUMAN  (sample for spot-checking)
confidence_threshold: tunable (start conservative, relax as confidence builds)
```

The same parser, same tokenizer, same sememe resolution, same frame assembly — different policy.

## Languages are input modes

Because parsing is `Language.parse()` and Language is itself an item, **any parser that produces frames is a valid input language**.  This is not a special-case provision; it's the natural consequence of the Language abstraction.

The table of available input languages grows organically.  At minimum:

- **Natural languages** (English, Spanish, Mandarin, Swahili, ...).  Each is a Language item with its own grammar parser, lexemes pointing at shared sememes, morphology rules.  Same sememes, different surface decorations.
- **Domain-specific notations** (Chess notation, music notation, chemical formulas, mathematical expressions).  Each is a Language item whose `parse()` understands its grammar and produces frames.
- **Programming-flavored** (Lisp/s-expression, Python-style function calls, JSON-shaped frame literals).  Trivial parsers for users who think in code.  Map directly to frame structure.
- **Logic and query languages** (miniKanren, SPARQL, SQL with conventions).  Especially natural for queries; miniKanren in particular maps almost identically to CG's relational frame structure.

A user writes in their preferred language; the frame produced is the same.  A reader views it in their preferred language; the lexeme rendering is selected from what's available on the relevant sememes.  The data is language-neutral; the surface forms are skin.

This means a Spanish-speaking biologist can author frames in Spanish, their Mandarin-speaking colleague can query them in miniKanren, and both see results rendered in their preferred language — all reading from and writing to the same corpus, with no translation layer between them other than what the lexeme renderer does at presentation time.

**Three layers, cleanly separated:**
- **Language** — the input UX layer.  How surface forms look.
- **Frame** — the data layer.  The structured assertion.
- **Sememe** — the meaning layer.  Language-neutral identity.

The cross product is enormous: every language times every input context times every policy.  Adding a new language adds it to all contexts at once.  Adding a new context (a new bridge, a new CLI mode, a new editor surface) adds it for all languages at once.

## The three-tier resolution model

Within any input context, parsing produces one of three outcomes:

**1. Clean semantic resolution.**  The parser accumulates enough evidence to commit a fully-structured semantic frame.  Multiple markers reinforce: "would you please review this PR?" — `please`, `would you`, `?` all contribute REQUEST-flavored evidence; the parser builds a REQUEST frame with appropriate role bindings.  Most intentional, well-formed input lands here.

**2. Disambiguation needed.**  The parser sees ambiguity that can't be resolved structurally (e.g., a token that resolves to multiple sememes with different EXPECTS).  In interactive contexts, the UI surfaces the choice and the user picks.  In batch contexts, fallback or queue policies engage.

**3. Soft fallback.**  Resolution doesn't reach the confidence threshold for a structured predicate.  The input lands as a MESSAGE (or DECLARATION — same sememe, alternative lexemes for "saying something") with the surface text preserved.  This is **not a failure mode** — it's the appropriate predicate for casual content that resists or doesn't need further semantic carving.  IRC-era banter, social bonding, off-topic chatter, "lol" — these are legitimate communicative acts that belong here.  MESSAGE is a first-class predicate, not a degraded one.

A frame committed at tier 3 can be promoted later: a user, an AI agent, or a curator can author a SUPERSEDES or AMENDS frame that carries richer semantic structure.  Re-curation is just authoring; nothing seals coarse data permanently into coarseness.

## No input friction as design principle

A guiding constraint on the input architecture: **the user just types**.  Frames assemble in real time; visual feedback shows what's being captured; sememes light up as they resolve; the predicate emerges from accumulated evidence.  If interpretation is clear, accept-on-Enter (or platform-equivalent gesture) commits a semantic frame.  If interpretation is ambiguous, the disambiguation UI appears inline.  If interpretation falls below threshold, MESSAGE captures the casual content.

The user is never asked to *choose* a predicate up-front, never asked to fill in role labels by hand, never asked to learn a structured authoring discipline.  They express themselves naturally; the system captures structure when structure is present.  Casualness is not punished; clarity is rewarded with richer queryability.

This is the inverse of structured-data systems that demand schema-first authoring.  The schema is implicit in the predicate vocabulary; the user opts into it gradient-by-gradient as their expression admits more structure.

## Bridges as policy configurations

Bridges from external systems (Slack, JIRA, email, HTTP, RSS, ActivityPub, Matrix, IRC, etc.) are not parallel parsers.  They are **service items that invoke the unified `eval()` pipeline** with bridge-appropriate policies.  Specifically:

- An inbound message from the external system → `eval(message_text, language=English (typically), policy=bridge_inbound)` → frames committed under the bridge's signing principal (or a delegated signer per the bridge's policy).
- An outbound frame destined for an external system → the bridge serializes the frame to the external surface form and delivers it via the external protocol.

The inbound-translation work the bridge does is *exactly* the work the parser already does for interactive input.  No duplicate parsing logic; no "Slack-aware text understanding" separate from the main parser.  Whatever intelligence the parser has — natural-language disambiguation, sememe resolution, token-window matching — applies to bridged content automatically.  Improvements to the parser benefit every bridge instantly.

This means **bridges are remarkably thin code**.  They handle protocol specifics (SMTP delivery, Slack API calls, HTTP request parsing) but not semantic interpretation.  Semantic interpretation is delegated to the core pipeline.

A typical bridge implementation:

```
when external_message arrives:
    text = extract_text(external_message)
    language = detect_language(text)  // or per-bridge config
    policy = bridge.config.input_policy
    frames = eval(text, language, policy)
    persist_with_signing(frames, bridge.delegated_signer)
    optional: queue for review if review_queue policy is set
```

That's the full inbound shape, modulo protocol-specific extraction.  Outbound is the symmetric: serialize frame to surface text, deliver via protocol.  Everything else — interpretation, disambiguation, fallback to MESSAGE — is the unified pipeline.

For more on bridge architecture, sandboxing, and the ecosystems worth bridging to, see [Bridges](bridges.md).

## Queries are input too

A query is a frame with set-returning bindings (see [Query](query.md)).  The same input pipeline produces query frames as well as assertion frames.  A user typing a question into the prompt, a SPARQL expression in a query box, a miniKanren run-form in a Lisp REPL — all produce query frames via the same `eval()` invocation.  Languages with query-flavored grammars (SPARQL, miniKanren, SQL with conventions) are just additional Language items whose `parse()` produces frames with set-returning bindings.

Query language for the user is therefore an open set, expanding with the Language registry.  Add miniKanren as a Language; users who want logic-programming queries get them.  Add SPARQL as a Language; semantic-web users feel at home.  None of this requires changes to the query subsystem itself — query behavior is already in the frame model; what's changing is the parser that turns surface syntax into query frames.

## The architectural payoff

A short list of consequences that fall out of the unified-input model:

**One parser to maintain.**  Bug fixes, performance improvements, new sememes, new languages — all benefit every input context immediately.  No drift between "the editor's parser" and "the bridge's parser" because they are the same parser.

**New input modes are cheap.**  Adding a new bridge is policy + protocol shim.  Adding a new input language is a Language item with its own `parse()`.  Adding a new editor surface (TUI, GUI, mobile, voice) is a UI shell that calls `eval()` with appropriate policy.  None of these touch the parsing core.

**Cross-language interop is automatic.**  Frames produced from English input are identical in shape to frames produced from miniKanren input or Lisp input or Slack-bridge input.  All can be queried together, mixed in the same datasets, rendered for different audiences.

**AI participation slots in cleanly.**  An AI proposing frames is just a signer running `eval()` against text it generated, with appropriate policy and signing principal.  An AI reviewing a queued tier-3 fallback is an `eval()` invocation with a review-flavored policy.  AI is not a special subsystem; it's another participant in the existing pipeline.

**Semantic richness compounds across all contexts.**  As parser intelligence grows, every context — interactive, bridge, programmatic, query — gets richer simultaneously.  Embeddings, learned heuristics, expanded vocabularies all benefit every input mode.

**Vendor-shape predicates (MESSAGE, COMMENT, etc.) are bridge accommodations, not native concepts.**  When external input arrives without semantic markers, the parser falls back to MESSAGE.  When a user types in CG with intent, structured frames emerge naturally.  Over time, native authoring drives the corpus toward semantic richness while bridges handle the legacy plumbing.  The bridge is a compatibility scaffold, not part of the destination architecture.

## Implementation status

Parts of this architecture are already implemented:

- TokenLattice, the unified tokenizer.
- `Language.parse()` as the per-language entry point.
- `PredicateBehavior.contribute()` for sememe-driven parsing contributions.
- `FrameAssembler` for accumulating evidence into frame structures.
- English language with rich parser; Chess Notation as a working DSL Language proof of concept.
- `Eval.evaluateRaw(String)` as the single entry point.

Parts are designed but not yet implemented:

- Explicit policy-configuration object passed through `eval()`.  Currently policies are scattered across calling sites; consolidating into a `Policy` object is part of the foundation refactor.
- Bridge framework using `eval()` with bridge-shaped policies.  Currently no bridges are implemented; this design will guide the first ones (likely Slack and JIRA per the early-targets thinking).
- Logic-programming and query languages (miniKanren, SPARQL) as Language items.  Designed; not yet implemented.
- Lisp/Python/JSON-shaped frame literal Language items for programmatic authoring.  Trivial to implement; awaiting demand.

## Why this matters strategically

Beyond the engineering elegance, the unified input model is what makes Common Graph's adoption story coherent.  Users don't have to learn a new authoring discipline; they type as they always have.  Bridges make existing data flows ride alongside.  Cross-system queries become possible because all input lands in the same shape regardless of origin.  Semantic richness accumulates wherever users care to express it, without being forced where they don't.

The pipeline is the front door.  Everything that arrives — typed, bridged, scripted, queried, generated — comes through the same door, and what's inside operates on a single uniform substrate.  That uniformity is what lets the rest of the architecture compose: trust matrix, federation, indexing, embeddings, queries, presentation.  Every layer above can assume "frames are frames, regardless of how they got here," because every input genuinely produced them the same way.
