# Seed Vocabulary and Application Bundles

This document describes how Common Graph's seed vocabulary is structured, how application developers extend it, and how the canonical-key pattern provides a stable developer-facing API while the IID-based identity provides a stable runtime contract.

> For sememes themselves, see [Sememes](sememes.md). For how lexemes attach to sememes, see [Language](language.md). For how vocabulary participates in input parsing, see [Input](input.md). For the trust-graph commitment that the seed vocabulary is a *choice* rather than a foundation of truth, see the philosophy notes in the design memory.

## Two layers of seed

When a Librarian boots, it loads a seed vocabulary — a curated collection of sememes, archetypes, predicates, and supporting items. The seed has two distinct layers, with different commitments:

### Layer 1: Codebase-referenced sememes

Any sememe that runtime code references by literal — directly, via canonical key in source — must exist in the graph at startup with a stable, deterministic IID. This is the **bootstrap contract**. Replacing or moving these sememes means changing source code.

By the time core implementation matures, this layer comprises ~100+ sememes:

- **Truly load-bearing:** ITEM_ID, FOLLOWS, ENDORSES, IMPLEMENTATION, CONFIG, ANY, EQUALS, CANONICAL_KEY, SOURCE.
- **Thematic roles** (~25 from ISO 24617-4): THEME, AGENT, GOAL, SOURCE (location), RESULT, BENEFICIARY, RECIPIENT, INSTRUMENT, EXPERIENCER, STIMULUS, MANNER, CAUSE, PURPOSE, TIME, DURATION, FREQUENCY, ATTRIBUTE, MEASURE, LOCATION, etc.
- **Structural archetypes:** Librarian, Session, Bridge, Channel, Application, etc. — wherever core code recognizes "this is a thing of *this* kind."
- **Network/protocol vocabulary:** PEERS_WITH, BRIDGES_FOR, LISTENS, ENDPOINT, SERVES, OPERATED_BY, format and encoding sememes.
- **Channel-name sememes:** MAIN, DRAFT, RELEASE, ARCHIVE, LIVE.
- **Editorial relationships:** SUPERSEDES, EQUALS / SAME_AS, MERGE_REQUEST, REVOKE, AMENDS, DEPRECATES, FORKS_FROM, DERIVED_FROM.
- **Common runtime predicates:** HEAD, IMPLEMENTS, PRESENT, PERMITS, TRUSTS.
- **Operators and functions:** PLUS, MINUS, AND, OR, NOT, GREATER_THAN, LESS_THAN, etc.
- **Pronouns and structural-vocabulary tokens:** ANY, IT, THIS, LAST, comma, semicolon, pipe, parens.
- **Quantity vocabulary:** unit sememes (METER, KILOGRAM, SECOND, etc.).

These are not optional. The runtime calls them by IID. The user can author trust-weighted alternatives via EQUALS frames in their own vocabulary, but the core code's references are stable and required for the system to function at all.

### Layer 2: Bulk semantic vocabulary

WordNet (~120K sememes), VerbNet alignments, FrameNet frames, and similar imported semantic resources. This is bigger than Layer 1 by orders of magnitude. **The runtime never references Layer 2 sememes by IID literal.** They exist for users to think with, not for the system to depend on.

Layer 2 is where the trust-graph philosophy applies in full: communities can fork freely, override, replace, augment. The Librarian's choice of which Layer 2 seeds to load (and how heavily to trust the curator's signature) is a deployment configuration, not an architectural commitment. A Librarian could ship with WordNet at high default trust, with WordNet at zero trust, or with a community alternative entirely. All are valid deployments.

The seed download bundles both layers, but they are philosophically different. Layer 1 is the bootstrap contract; Layer 2 is a generous starting point that's optional in principle and replaceable in practice.

## Canonical keys: the developer API

Sememes have IIDs. IIDs are bytes — content-addressed hashes. They're not human-readable. They're not stable in the sense of being something a developer can write in source code by hand.

The bridge from "human-readable handle" to "machine identity" is the **canonical key**. Each Layer 1 sememe is identified by a canonical key like:

```
cg.role:theme
cg.structural:item-id
cg.predicate:authored
cg.action:follows
cg.archetype:application
expense-app.action:approve
chess.predicate:check
```

The IID is a deterministic hash of the canonical key:

```
IID(sememe) = hash(canonical_key.bytes)
```

So the canonical key is the developer-facing handle. Source code references it; the runtime computes the IID once at class-load and caches it. The IID is stable forever (the canonical key is the source of truth); the canonical key is human-readable in source.

```java
public static final ItemID THEME = ItemID.fromCanonicalKey("cg.role:theme");
public static final ItemID FOLLOWS = ItemID.fromCanonicalKey("cg.structural:follows");
public static final ItemID APPROVE_EXPENSE =
    ItemID.fromCanonicalKey("expense-app.action:approve");
```

This is the primary mechanism by which code names sememes. **Code never holds raw IID byte literals.** It always derives IIDs from canonical keys. This gives:

- Self-documenting source — the canonical key tells the reader what the sememe represents.
- Stability — the canonical key never changes; the IID never changes.
- Namespacing — prefixes (`cg.*`, `chess.*`, `expense-app.*`) give clean separation.
- Verifiability — anyone can hash the canonical key and confirm the resulting IID matches the sememe item's identity.

## The CANONICAL_KEY binding

When the seed loader mints a sememe item from a canonical key, the canonical key is recorded as a binding on the resulting item:

```
some-sememe-item:
    CANONICAL_KEY → "cg.role:theme"      (literal string)
    SOURCE → cg-core-seed-2026             (where this came from)
    LEXEME → "theme"                        (English lexeme via LEXEME frame)
    LEXEME → "テーマ"                       (Japanese lexeme)
    EXPECTS → ...                           (role expectations if predicate-shaped)
    ...
```

This makes the canonical key first-class data, queryable like any other binding. Properties:

- **Reverse lookup**: query "find the sememe with canonical key X" — answered by the standard binding index on CANONICAL_KEY.
- **Verifiability**: the IID of any item with a CANONICAL_KEY binding can be checked against the hash of that key. Tampering or migration mistakes fail this check.
- **Provenance in data**: items carry their own derivation history. SOURCE shows where the data came from; CANONICAL_KEY shows what conceptual handle was used to mint the item.
- **Browsability**: tools that show items can display the canonical key alongside, making vocabulary self-documenting.

CANONICAL_KEY itself is a Layer 1 structural sememe — the seed loader needs to recognize it during bootstrap.

## Namespace convention

Canonical keys follow a hierarchical namespace:

- **`cg.*`** — reserved for Common Graph core. Defined and shipped by the project itself. Subdivisions: `cg.role:*`, `cg.structural:*`, `cg.predicate:*`, `cg.archetype:*`, `cg.action:*`, etc.
- **`<domain>.*`** — application-specific or community-specific. Each app or community picks a prefix and uses it for any sememes it defines.

Examples:

- `chess.predicate:check` — chess module's check predicate.
- `slack-bridge.format:thread` — Slack bridge's thread format declaration.
- `expense-app.action:approve` — enterprise expense tracker's approve action.
- `gtd.archetype:next-action` — Getting-Things-Done community's next-action concept.
- `medicine.predicate:diagnoses` — medical informatics community's diagnosis predicate.

This is the same pattern as Java packages, Rust crates, NPM scopes, Go modules. Apps cannot collide because each has its own prefix. CG core cannot collide with apps because `cg.*` is reserved.

Communities can converge: widely-adopted app-specific sememes can be promoted to CG core or to shared community namespaces (`gtd.*`, `iso.*`, etc.). Promotion is just authorial — someone signs the elevation, the trust matrix evaluates it, the broader ecosystem either accepts or doesn't.

## Reuse over create: the cultural norm

Most application developers think they need to define new sememes for their domain concepts. **Most of the time, they don't.** Existing sememes from CG core or WordNet usually cover the meaning; what the application needs to add is much smaller than expected.

Example — chess. A chess application might naively think it needs:

- A "chess game" sememe.
- A "move" sememe.
- A "piece" sememe.
- A "check" sememe.
- A "checkmate" sememe.

In reality:

- **Chess** is already a WordNet sememe (the game). The chess application's "ChessItem" is an *implementation* attached to this sememe — code that knows how to be a chess-game instance. Not a new sememe.
- **Move** is already a WordNet sememe ("to take a turn in a game"). The chess application's "ChessMove" is an *implementation* of Move in the chess context — code that validates moves, updates board state, recognizes checkmate. Not a new sememe.
- **Piece** is already a WordNet sememe. Pawn, Knight, Bishop, Rook, Queen, King are all WordNet sememes. The chess implementation just attaches behavior.
- **Check** is already a WordNet sememe (in this game-related sense). Implementation attaches behavior.
- **Checkmate** is already a WordNet sememe. Implementation attaches behavior.

What the chess application *actually* contributes:

- An implementation item that targets the Chess sememe — Java/whatever code that handles Chess-archetype frames at runtime.
- An implementation item that targets the Move sememe — code that interprets Move frames in the context of a chess game.
- Possibly a few genuinely chess-specific predicates that don't have natural-language equivalents (en-passant, castling-availability) — *these* would need new canonical keys under `chess.*`.
- Default scenes and UI for rendering chess boards and pieces.

The bulk of the vocabulary is reuse. The application is mostly **implementations and archetypes**, not new sememes.

The same pattern applies broadly:

- A todo app: tasks reuse the existing action vocabulary; "task," "remind," "due," "complete" are all WordNet sememes. The app contributes a TaskItem archetype implementation and a few specific predicates.
- An expense tracker: transactions, amounts, dates, categories, vendors are existing concepts. The app contributes an ExpenseReport archetype, an approve action implementation, and maybe a few specific predicates around approval workflows.
- A code review tool: review, comment, approve, request-changes, merge are existing concepts. The app contributes a CodeReview archetype implementation and a few VCS-specific predicates.

**Behavior is contextualized through implementations, not through forking the meaning vocabulary.** This keeps the semantic graph from exploding into parallel near-synonymous app-specific vocabularies, and it lets cross-app queries work because everyone is using the same core sememes.

## Sememes vs implementations vs archetypes

Three distinct kinds of items participate in the application architecture, and confusion between them is easy. Brief clarification:

- **Sememe** — a unit of meaning. An item with a stable IID, a canonical key, lexemes in various languages, and (if predicate-shaped) an EXPECTS schema declaring what bindings instances can carry. *Universal*; same sememe across all apps that reference it. Most come from WordNet (Layer 2) or CG core (Layer 1). Apps create new ones rarely, when truly novel concepts have no existing equivalent.

- **Archetype** — a sememe whose EXPECTS includes ITEM_ID. Marks the sememe as kind-denoting: instances of it are anchored items with their own version history. Document, ChessGame, ExpenseReport, BugTicket. May come from CG core (Application, Librarian, Session) or from app definitions.

- **Implementation** — code that handles a sememe in a runtime context. An item that points at a sememe (or archetype) and declares "I provide the executable behavior for this in such-and-such language and runtime." Apps contribute implementations to give existing sememes new behavior. Multiple implementations of the same sememe can coexist; the trust matrix + user configuration selects which is active.

So the chess application:
- Reuses WordNet sememes for Chess, Move, Pawn, Knight, etc.
- Reuses CG-core sememes for ITEM_ID, FOLLOWS, AGENT, THEME, GOAL, etc.
- Creates a few app-specific sememes only where genuinely needed (novel concepts).
- Provides a ChessImplementation for the Chess archetype.
- Provides a ChessMoveImplementation for the Move sememe.
- Provides ChessNotationLanguage as a Language item with its own parser.
- Provides default scenes for board rendering.
- Bundles all of these as a signed application item.

## Applications as bundles

An **application** is just an item with the Application archetype. Its contents are other items (sememes, predicates, archetypes, implementations, scenes, seed data). The application item INCLUDES (or COMPOSES) the whole bundle:

```
EXPENSE_APP (item, archetype: Application)
    NAME → "Expense Tracker"
    AUTHOR → developer-signer
    VERSION → "1.0.0"
    NAMESPACE → "expense-app"
    INCLUDES → [
        ExpenseReport-archetype-item,
        expense-app.action:approve-sememe,
        expense-app.action:submit-sememe,
        ApproveImplementation-item,
        SubmitImplementation-item,
        default-scene-items,
        ...
    ]
    DEPENDS_ON → [ cg.core@1.0, gtd-vocabulary@2.3 ]
    LICENSE → MIT-sememe
    CONFIG:[SANDBOX] → policy-item
    CONFIG:[PERMISSIONS] → required-permissions-item
```

Properties of treating apps as bundles:

- **One signature, whole bundle.** The developer signs the Application item; the manifest commits the entire collection. Trust on the developer signature transitively applies to bundled items via the trust matrix.
- **Atomic install.** Installing the app means accepting (endorsing, trusting) the bundle's manifest. All included items become available together.
- **Versioning.** The Application item has manifest versions. v1.0 → v1.1 → v2.0 are FOLLOWS-chained. Users choose which version of the bundle they trust.
- **Dependencies are explicit.** DEPENDS_ON lists other application/library items. Loading resolves them via the trust graph.
- **Inspectability.** Querying "what items did this app contribute?" is just a query over INCLUDES. Users see what gets installed before installing.

### Uninstall

Uninstall has two paths, both legitimate:

**Soft uninstall (trust revocation).** The user revokes endorsement of the Application item, or removes their trust in the developer's signature. The included items remain in storage — they're content-addressed, immutable, and may still be referenced by other things — but they fall out of the user's active trust scope. Their queries no longer see them; the app's archetypes no longer dispatch.

**Hard uninstall (deletion).** The user deletes (locally) the bundle items: the application item, its included sememes, archetypes, implementations, scenes. Storage frees up. If the items are referenced by content elsewhere (e.g., a frame somewhere uses this app's archetype), the references become dangling but content-addressed integrity isn't compromised — they still hash the same; they just aren't resolvable locally.

Both paths are user choice. Soft is reversible (re-trust to restore); hard is final but recoverable from any backup or peer that still has the bundle.

## Cross-application sharing

Apps can interoperate through the trust matrix and EQUALS frames:

- **Direct reuse**: App B references `app-a.concept:thing` directly. Requires App B to trust App A's vocabulary.
- **Convergence via EQUALS**: App A and App B each define their own version, and either author or community curators sign EQUALS frames asserting they mean the same thing.
- **Promotion to shared namespace**: a widely-adopted app-specific sememe can be promoted to CG core or to a shared community namespace (`gtd.*`, `iso.*`). The promotion is a community-curatorial act, not a centralized decision.

This mirrors how natural language vocabularies converge across communities — sometimes through borrowing, sometimes through shared coinage, sometimes through translation. The trust matrix handles the social-graph equivalent.

## Implications for development

A few practical norms that should shape how applications get built on CG:

**1. Look before you mint.** Before defining a new sememe under your namespace, search CG core and WordNet (when imported) for an existing sememe that captures the same meaning. The bar for new sememes is "no existing sememe captures this." Tooling will eventually help with discovery; for now, browsing seed vocabulary is the primary tool.

**2. Implementations carry behavior; sememes carry meaning.** When you need app-specific *behavior*, write an implementation, not a new sememe. The same Chess sememe can have many implementations across many chess apps; users and contexts select which is active.

**3. Domain archetypes are usually the right level for new vocabulary.** When you do create new items in your namespace, they're often archetypes (kinds of things your app deals with) rather than action/relation sememes. ExpenseReport is a kind of thing; "to approve an expense" is just AUTHORIZE or APPROVE applied to that kind.

**4. Bundle and sign as a unit.** Don't ship sememes separately from the code that uses them. The application item bundles everything; the developer's signature covers the whole package.

**5. Document your namespace.** A small README listing what sememes your app contributes, what archetypes it defines, what implementations it provides — distributable as part of the bundle. Helps users (and other developers) understand what installing your app brings into their graph.

## References

- [Sememes](sememes.md) — the meaning-unit primitive these all build on.
- [Language](language.md) — how lexemes attach to sememes; multilingual surface forms.
- [Frames](frames.md) — the universal structural primitive.
- [Input](input.md) — how vocabulary serves all input contexts uniformly.
- [Bridges](bridges.md) — bridges as service items, often shipped as their own bundles.
- [Trust](trust.md) — how trust evaluation drives which sememes/implementations are active in a given user's view.
- [Library](library.md) — storage and indexing of seed and runtime vocabulary.
