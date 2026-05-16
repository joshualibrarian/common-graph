# Documentation Style

These rules govern every doc in `docs/`. The goal is a documentation set that reads as a coherent architectural description, written as if the system is what it aims to be. Implementation lives in code and Javadocs; this directory describes the *idea*.

## Voice

**Present-tense, declarative, vision-as-reality.** Write as if the architecture exists in its final form. The reader should be able to learn what Common Graph *is* without needing to know what it's currently *building toward*.

| Don't | Do |
|---|---|
| "Items will be stored as datums." | "Items are stored as datums." |
| "We plan to support polyglot handlers." | "Handlers run in any supported language." |
| "Currently, the runtime degrades to Java-only." | "On hosts without a polyglot environment, only Java handlers run." |
| "Phase 1 implements X. Phase 2 will add Y." | "X is the supported path; Y extends it for the multi-host case." |
| "TODO: this section needs more thought." | (omit until it's ready to say something definite) |

Hedging belongs in design memos in the user's memory, not in `docs/`. If a section can't yet be written declaratively, leave it out.

## No code

These docs describe **the architecture**, not **this implementation**. Java class names, method signatures, package paths, and code snippets belong in Javadocs and inline source comments — not here.

**Permitted exceptions:**

- The abstract data-shape notation (see below) — it isn't code, it's a structural diagram.
- A short pseudocode fragment to illustrate a flow when prose alone would obscure it — kept under five lines, language-agnostic, no specific framework or library.
- A textual sketch of a wire format or encoding — bytes, tags, lengths.

If a passage starts to look like Java, rewrite it as prose or as the abstract shape notation.

## The abstract data-shape notation

The canonical way to show a datum's structure:

```
{<head>, [<binding>, <binding>, …]}
```

- `<head>` is a reference. Every reference begins with exactly one prefix byte: `@`, `?`, `!`, `~`, or `#`. Prefixes never stack — `!iid` means "the item with this IID, used as schema"; you don't write `!@iid`. The `@` is the *default* concrete-reference prefix; `?`, `!`, `~`, `#` replace it.
- Each `<binding>` is either a bare prefixed reference (`!THEME`, `?piece`) or a full `<role> → <target>` triple. Qualifiers appear in square brackets: `@PLAYER:[WHITE] → @Alice`.
- A binding's target may be a **reference** (one of the five prefixes) or a **literal** (no prefix). `@R → 255` and `@NAME → "Alice"` are literal targets. `@ITEM_ID → <iid>` is also a literal — an item's IID is identity *itself*, raw bytes, not a reference to something elsewhere. Writing `@ITEM_ID → @<iid>` is a mistake: there is nothing to dereference.
- Whitespace and line breaks are insignificant — multi-line shapes are fine for readability.

This notation is the working diagram language throughout `docs/`. It is language-neutral, encoding-neutral, and survives every refactor of the implementation.

## Canonical examples

Use these examples across docs whenever an illustration is needed. Reusing the same examples makes the documentation set feel cohesive and lets readers track concepts through layers.

- **Color** — the canonical value-type example. `{@color, [@R→255, @G→0, @B→0]}`.
- **Add** — the canonical operator-predicate example. `{@add, [@THEME → 5, @THEME → 3]}`. Both operands are THEMEs; Add is commutative, so the two bindings share a role without ambiguity.
- **ChessGame** — the canonical archetype example. Has players, turns, and handles moves.
- **Tolkien authored The Hobbit** — the canonical relationship-frame example. `{@authored, [@AGENT→@tolkien, @THEME→@hobbit]}`.

Introduce new examples only when none of the above fit. When in doubt, prefer one of these.

## Length discipline

**Target: 5–15 KB per doc.** Longer docs become hard to navigate, hard to cross-reference, and hard to keep coherent under edits.

If a doc grows past ~20 KB, look for a split:
- A primitive / a specific subsystem / a particular application may each warrant their own doc.
- Internal numbered sections that have grown into substantial content often belong in separate docs.

The white paper (`the-case.md`) is the explicit exception — it is the long-form vision document and can be as long as it needs to be.

## Cross-references

Link generously between docs. Each doc covers one concept area; the connections between concepts live in the link graph.

Use relative markdown links: `[frames](frames.md)`, `[the ref scheme](ref-scheme.md)`. Don't link to specific anchors unless the linked section is stable — anchors break under section renames.

When a doc *depends* on another doc's concepts, name the dependency early: "This document assumes familiarity with [datum](datum.md) and [the ref scheme](ref-scheme.md)."

## Section structure

Most docs follow this rough shape:

1. **One-paragraph opening.** What this doc covers, and why it matters. No headers.
2. **Core concept.** The main thing the doc is describing, in its simplest form.
3. **Mechanics.** How the concept works in detail — the substance of the doc.
4. **Examples.** Concrete illustrations, using the canonical examples where they fit.
5. **Relations.** How the concept connects to others — cross-references to related docs.

Not every doc needs all five. Short docs may collapse mechanics + examples. Some docs (the white paper, the longer subsystem docs) need additional structure.

## What goes where

| Goes in `docs/` | Goes in Javadocs |
|---|---|
| The data shape an item carries | The Java class that wraps that shape |
| The semantics of a predicate | The method that registers a handler for it |
| The dispatch flow | The specific dispatch routing logic |
| The architectural rationale | The current implementation status |
| The user-visible behavior | The internal data structures |

When in doubt: if it would still be true after a from-scratch reimplementation in a different language, it belongs here. If it's specific to this codebase, it belongs in Javadocs.

## What doesn't go in `docs/`

- TODO lists and task trackers (use the task system or commit messages).
- Internal primers for "the current state of the codebase" (these date instantly).
- Conversation summaries or design memos that pre-date a decision (these belong in the user's memory).
- Speculation. Future-looking discussion belongs in design memos until it stabilizes.

If a writeup doesn't fit the architectural-description voice but is too useful to discard, find a home for it outside `docs/`.
