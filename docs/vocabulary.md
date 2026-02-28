# Vocabulary

**Vocabulary** is the linguistic surface of Common Graph. Every interaction — creating, navigating, querying, customizing — flows through a unified language system where tokens resolve to sememes, and sememes drive action. This system is holistic: it handles all parts of speech, works in any human language, and uses the same resolution mechanism for verbs, nouns, proper names, units, and prepositions.

> This document covers the vocabulary system, token resolution, dispatch, the expression input, and how items expose their linguistic surface. For the semantic backbone (what sememes are, how they're anchored), see [Sememes](sememes.md). For how components declare vocabulary contributions, see [Components](components.md).

## Core Principle: Everything Is Language

Traditional systems separate "commands" from "data" from "names." Common Graph doesn't. Every meaningful interaction is expressed through language — an approach with deep roots in formal semantics and speech act theory. Montague showed that natural language can be given the same formal treatment as programming languages (see [references/Montague 1973](references/Montague%201973%20-%20The%20Proper%20Treatment%20of%20Quantification.pdf)). Austin and Searle showed that utterances are *actions*, not just descriptions (see [references/Austin 1962](references/Austin%201962%20-%20How%20to%20Do%20Things%20with%20Words.pdf), [references/Searle 1969](references/Searle%201969%20-%20Speech%20Acts.pdf)). Common Graph takes both ideas literally: every token resolves to a meaning, and every expression dispatches an action.

- **Verbs**: "create", "move", "exit" — dispatch to methods
- **Nouns**: "item", "log", "roster" — type references, navigation targets
- **Proper nouns**: "My Shopping List", "notes", "chat" — specific items or components
- **Units**: "meters", "kilograms", "seconds" — nouns with dimensional metadata
- **Prepositions**: "in", "with", "to", "from" — structure the expression, fill thematic roles
- **Modifiers**: "all", "recent", "unread" — qualify queries

All of these resolve through the same pipeline: token to sememe to action.

## The Resolution Pipeline

```
Token (any language)
    |
TokenDictionary (global lexicon)
    |
Sememe (universal meaning unit)
    |
Part of speech?
    |
    +-- Verb     --> Dispatch to method via VerbEntry
    +-- Noun     --> Navigate, reference, or fill argument slot
    +-- Unit     --> Combine with numeral to form Quantity
    +-- Preposition --> Fill thematic roles (to, from, with, in)
    +-- Modifier  --> Qualify or filter
```

### Example Flow

A user types "create document" into an Item's prompt. This triggers progressive narrowing:

```
graph> create                        # Verb resolved
graph> create doc...                 # Completion dropdown:
                                     #   markdown document
                                     #   plain text document
                                     #   JSON document
graph> create markdown document      # Type concept resolved
                                     # But multiple implementations exist --
                                     # dropdown:
                                     #   BasicMarkdown
                                     #   CollaborativeMarkdown
graph> create markdown document BasicMarkdown
                                     # Fully resolved, dispatch
```

Each step narrows the expression by filling a thematic role:

```
verb   = create
THEME  = markdown document   (narrowed from "document" via sememe hierarchy)
IMPL   = BasicMarkdown       (narrowed from available implementations)
```

The final "implemented-by" step is implicit — the evaluator knows that after a fully-resolved type concept, the only unfilled slot is which implementation. The user doesn't need to type "implemented-by"; the dropdown presents implementations directly. But explicit phrasing works too: "create markdown document implemented-by BasicMarkdown".

A Spanish-speaking user typing "crear documento markdown" follows the same narrowing from the sememe stage onward — "crear" and "create" resolve to the same verb sememe, "documento" and "document" to the same noun sememe.

## TokenDictionary: The Global Lexicon

The **TokenDictionary** is the single source of truth for all token-to-meaning resolution. It maps human-readable strings to sememe postings, with support for scoping and weighted relevance.

```
TokenDictionary {
    lookup(token, scopes...) --> [Posting]     # Exact match with scope chain
    prefix(prefix, limit, scopes...) --> [Posting]  # Completion candidates
    index(posting)                              # Register a mapping
}
```

A **Posting** connects a token to its target:

```
Posting {
    token:  string      # The matched text ("create", "crear", "m", "+")
    target: ItemID      # The sememe or item this token refers to
    scope:  ItemID?     # null = universal, or scoped to a language/item
    weight: number      # Relevance ranking (higher = more relevant)
}
```

### Scoping

Every posting has one **scope** — an ItemID that says where this mapping is meaningful. The scope is just an ItemID; the TokenDictionary doesn't know or care what *kind* of item it is. The DB key is `<scope><token>`.

The scope can be null for universal postings (language-neutral symbols like "m", "kg", "+", "USD") that resolve for everyone. Everything else has a scope:

```
Scope: cg:language/eng     Token: "create"     → cg.verb:create
Scope: cg:language/spa     Token: "crear"      → cg.verb:create
Scope: cg:language/eng     Token: "meter"      → cg:unit/meter
Scope: cg:language/spa     Token: "metro"      → cg:unit/meter
Scope: <item X>            Token: "notes"      → <component on X>
Scope: <user Y>            Token: "deploy"     → <user Y's alias>
Scope: null                Token: "m"          → cg:unit/meter
Scope: null                Token: "+"          → cg:operator/add
```

### Scope Chain Resolution

When a user types a token, the TokenDictionary resolves it against a **scope chain** — a list of ItemIDs assembled from context. The caller decides what's in the chain:

- The user's active languages (`cg:language/eng`, `cg:language/spa`)
- The focused item, its ancestors, the session
- The user's own item (personal aliases)
- null (always included — universal symbols)

The dictionary looks up `<scope><token>` for each scope in the chain and returns all matching postings. One mechanism, one lookup path.

### Token Sources

Postings come from:
- **Sememe symbols**: Language-neutral shorthand declared on sememes — indexed with null scope
- **Seed vocabulary**: Bootstrap English tokens indexed under `cg:language/eng`
- **Lexicon imports**: WordNet, CILI — each language's lexemes scoped to its Language Item, merged idempotently
- **Component vocabulary**: EntryVocabulary contributions scoped to their item
- **Relations**: Named relations (title, alias) scoped to their item
- **User customization**: Custom aliases and scripted expressions scoped to the user or item

## Item Vocabulary

Every Item has a **vocabulary** — its linguistic surface. An item's vocabulary is the **merged union of its components' vocabularies**, combined with its type's code-defined verbs.

### Three Layers

```
Code Layer (transient, declared in type definitions)
    |
    +-- Verb methods on the Item type --> base verbs all items of this type share
    +-- Verb methods on component types --> component-specific verbs
    |
User Layer (persistent, from EntryVocabulary)
    |
    +-- Custom aliases ("deploy" --> cg.verb:commit)
    +-- Scripted expressions ("deploy" --> "commit then push to production")
    +-- Component proper nouns ("notes", "chat")
    |
Runtime Merge (on item open/hydrate)
    |
    +-- Code layer + User layer = live dispatch surface
    +-- User layer wins on conflict
```

### Vocabulary Is Derived

An item's runtime vocabulary is rebuilt each time the item is opened — merged from its type's code-defined verbs and its components' persistent EntryVocabulary contributions.

All persistent vocabulary customization happens through **components**. Adding components is how you customize items. Writing a new type is how you define new behavior.

### Hydration: Synchronizing Persistent and Runtime State

When an Item is opened (hydrated from storage):

```
1. Collect verb definitions from the item type --> base verb entries
2. For each component:
   a. Collect verb definitions from the component type --> component verb entries
   b. Load component's EntryVocabulary contributions --> persistent customizations
   c. Merge into runtime dispatch surface
3. User-layer entries overlay code-layer entries (user wins on conflict)
4. Scoped tokens indexed in TokenDictionary for discoverability
```

The result is a live dispatch map: sememeId to VerbEntry, plus all proper nouns and aliases registered as scoped postings.

## Dispatch: Inner to Outer

When a token resolves to a verb sememe, the system checks vocabularies from most specific to most general:

```
Component vocabulary   (most specific)
    |
Item vocabulary        (type-level verbs)
    |
Session vocabulary     (session-level verbs like "exit")
```

This means a component's local meaning wins over the item's general meaning, which wins over the session's global meaning.

### Disambiguation Through Language

There are no reserved words. No escape hatches. No special prefixes. When a token is ambiguous — it matches verb entries in multiple scopes — the system presents a **dropdown** of possibilities, and the user refines with more language:

```
User types: "exit"
    --> resolves to Sememe(cg.verb:exit, VERB)
    --> matches: Session.exit(), GameComponent.exit()
    --> ambiguous! Present dropdown:
        exit session
        exit game
    --> user continues typing: "exit ses..."
    --> autocomplete: "exit session"
    --> dispatch: session.exit()
```

This works because:
- Session knows it's a `cg:type/session` (its type sememe, which has the token "session")
- The game component knows it's a `cg:type/game` (its type sememe, which has the token "game")
- The expression parser sees `verb + noun` and uses the noun to scope the verb

No special cases. Just more language. Automatically multilingual — "salir de sesion" works the same way once Spanish lexemes are loaded.

## Session as an Item

A Session is an Item. It has:
- A type (`cg:type/session`) with verb definitions (`exit`, `back`, `switch`)
- Components (command history, preferences, active context stack)
- Relations (session to current user, session to focused item)

This means session verbs participate in the same vocabulary system as everything else. "Exit" resolves through the TokenDictionary to `cg.verb:exit`, and the Session's vocabulary has a VerbEntry for it. No special-case string matching.

Sessions can be ephemeral (in-memory, never committed) or persistent (committed, versioned, shareable — like sharing your shell configuration or editor setup).

## VerbEntry and Dispatch

A **VerbEntry** is the runtime binding between a sememe and a callable method:

```
VerbEntry {
    sememeId:        ItemID          # The verb sememe this entry handles
    method:          method ref      # The bound method to invoke
    doc:             string          # Human-readable description
    params:          [ParamSpec]     # Parameter metadata
    source:          ITEM | COMPONENT  # Where this verb was declared
    componentHandle: string?        # Which component (if source = COMPONENT)
    target:          object          # The live instance to invoke on
}
```

### Verb Sources

**Item-level verbs** — actions on the Item itself:

```
create    --> Create a new instance
edit      --> Begin editing
commit    --> Commit changes
delete    --> Delete this item
describe  --> Show item details
open      --> Open/navigate into
```

**Component-level verbs** — actions on a specific component:

```
move      --> On a Space component: move an element
send      --> On a Chat component: send a message
add       --> On a Roster component: add a member
append    --> On a Log component: append an entry
flag      --> On a Minesweeper component: flag a tile
```

If multiple components define the same verb sememe, disambiguation follows the same language-based pattern: the user adds a noun to specify which component.

## Parameters and Thematic Roles

Verb parameters carry metadata for validation, help text, and semantic framing. The thematic role system descends from Fillmore's frame semantics (see [references/Fillmore 1982](references/Fillmore%201982%20-%20Frame%20Semantics.pdf)) and its computational realization in FrameNet (see [references/Baker et al 1998](references/Baker%2C%20Fillmore%2C%20Lowe%201998%20-%20The%20Berkeley%20FrameNet%20Project.pdf)). Where FrameNet uses frames for *annotation*, Common Graph uses them for *dispatch* — the semantic frame assembled from an expression is the structure that drives verb invocation.

```
ParamSpec {
    name:         string    # Parameter name/label
    doc:          string    # Description
    required:     boolean   # Must be provided?
    defaultValue: string    # Default if omitted
    role:         Role      # Thematic role (see below)
}
```

### Thematic Roles

Parameters declare roles from linguistic case grammar. These are the same thematic roles defined by the sememe system — they're sememes themselves:

| Role | Meaning | Example |
|------|---------|---------|
| **THEME** | The thing being acted upon | "delete **this item**" |
| **TARGET** | The destination or goal | "move to **that folder**" |
| **SOURCE** | The origin | "copy from **here**" |
| **INSTRUMENT** | The tool or means | "encrypt with **this key**" |
| **BENEFICIARY** | Who benefits | "share with **Alice**" |

Roles enable order-independent expression parsing. A verb's parameters can be filled positionally or by role — "move pawn to e4" and "move to e4 pawn" produce the same semantic frame.

## ActionResult

Every verb invocation returns an **ActionResult**:

```
ActionResult {
    success: boolean    # Whether the action succeeded
    value:   any        # Return value (may be null)
    error:   Error?     # Error details if failed
}
```

This is the universal return type for all dispatch — CLI, GUI, remote session, or inter-item invocation.

## ActionContext

Verbs receive an **ActionContext** providing the invocation environment:

```
ActionContext {
    caller:    ItemID       # Who invoked this action
    item:      Item         # The item being acted upon
    librarian: Librarian?   # Runtime context
}
```

## The Expression Input System

The vocabulary system connects to the user through the **expression input** — a tokenizing prompt on every Item. This is not a command line. It's a **semi-natural language interface** where tokens are progressively resolved into semantic references, and the system narrows completions based on grammar and context.

### Every Item Has a Prompt

Each Item has its own prompt. The prompt shows where you are:

```
graph>                          # Default (session level)
chess>                          # Focused on a chess game
alice@chess>                    # With principal identity
alice@chess/board>              # Navigated into a component
```

Typing into the prompt dispatches through that Item's vocabulary. Different Items have different vocabularies — a chess game offers "move" and "resign", a document offers "edit" and "commit", a roster offers "add" and "remove".

### Input State

```
ExpressionInput {
    tokens:              [ExpressionToken]   # Resolved references so far
    pendingText:         string              # Currently-typing text buffer
    cursor:              integer             # Position within buffer
    completions:         [Posting]           # Active completion candidates
    selectedCompletion:  integer             # Highlighted choice
    showCompletions:     boolean             # Dropdown visibility
    history:             [string]            # Previous expressions
    expressionContext:   ExpressionContext    # Semantic narrowing state
}
```

### Token Types

As the user types, input is progressively resolved:

| Token Type | Description | Visual | Example |
|------------|-------------|--------|---------|
| **Ref** | Resolved item reference | `[create]` | Verb, noun, or proper noun resolved from token |
| **Literal** | Typed value | `42`, `"hello"`, `true` | Numbers, booleans, quoted strings |
| **Operator** | Logical operator | `AND`, `OR` | Conjunctions |
| **Paren** | Grouping | `(`, `)` | Expression structure |

Ref tokens are the key — they're resolved references to sememes or items in the graph. When you type "move" and press Tab, it resolves to the `cg.verb:move` sememe. When you type "pawn" and press Tab, it resolves to the pawn item. The expression is a sequence of graph references, not text to be parsed.

### Completion and Semantic Narrowing

Pressing Tab triggers lookup via the TokenDictionary with the current scope chain. Completions are **semantically narrowed** based on what's already typed.

The **ExpressionContext** analyzes the current token list:

1. Find the verb (if any) — the first token whose sememe has part-of-speech VERB
2. Identify prepositional phrases — a preposition followed by an object fills a thematic role
3. Match bare nouns to unfilled argument slots
4. Compute which roles are still unfilled

Then it **filters completions**:

| Current State | Filter Rule |
|---------------|-------------|
| No verb yet | Show everything |
| Verb selected | Exclude other verbs |
| Last token is a preposition | Show nouns and proper nouns |
| All required roles filled | Show optional roles and prepositions |

**Example**: At `chess>`:

```
chess> m                        # Completions: move, modify, merge...
chess> move                     # Tab --> resolves to [move] verb
chess> [move] p                 # Completions: pawn, piece... (no verbs shown)
chess> [move] [pawn] to         # Tab --> resolves [to] preposition
chess> [move] [pawn] [to] e     # Completions: e4, e5... (positions only)
chess> [move] [pawn] [to] [e4]  # Enter --> dispatches move(theme=pawn, target=e4)
```

### Semantic Frame Assembly

When Enter is pressed, the token list is assembled into a **semantic frame** — an order-agnostic structure that maps tokens to verb parameters by thematic role:

```
Tokens:  [move] [pawn] [to] [e4]
Frame:   verb=move, THEME=pawn, TARGET=e4
```

The assembly process:

1. Find the verb (first token with VERB part-of-speech)
2. Scan for prepositional phrases: `preposition + object` fills the role that preposition implies
3. Match remaining bare items to argument slots by first-fit
4. Build the frame

**Word order doesn't matter** (within limits):

```
move pawn to e4
move to e4 pawn
pawn move to e4
```

All produce the same frame. The preposition "to" binds "e4" to TARGET regardless of position. "pawn" fills THEME as a bare argument. This is closer to natural language than positional command syntax.

### Quantities in Expressions

When a numeral precedes a unit noun, the expression parser recognizes the combination as a **Quantity**:

```
"5 meters"  --> numeral(5) + noun(meter) --> Quantity(5, meter-sememe)
"3 kg of flour" --> Quantity(3, kg) + preposition(of) + noun(flour)
```

The parser knows to form a Quantity because the noun-sememe carries dimensional metadata (it's a unit). No special syntax — just language.

### Key Bindings

| Key | Action |
|-----|--------|
| Characters | Insert into pending text |
| Space | Token boundary — resolves recognized words |
| Tab | Accept selected completion, or show completions |
| Enter | Dispatch the expression |
| Escape | Cancel / clear |
| Backspace | Delete character; if empty, pop last token |
| Ctrl+W | Delete word before cursor |
| Up/Down | Navigate history (or completion list) |
| Left/Right | Cursor movement |
| Home/End | Jump to start/end |

### Space as Token Boundary

Space is a **token boundary**, not just whitespace. When you press Space:

1. If the pending text matches a known operator (`AND`, `OR`) --> commit as operator token
2. If it's a recognized literal (number, boolean, quoted string) --> commit as literal token
3. If it's a parenthesis --> commit as structural token
4. Otherwise --> insert a space (for multi-word lookups)

### Dispatch

When Enter is pressed:

1. Any remaining pending text is committed (resolved where possible)
2. The token list is converted to resolved references
3. The FrameAssembler builds a semantic frame
4. The verb is located via the scope chain (component --> item --> session)
5. The verb is invoked with the frame's bindings as parameters
6. An ActionResult is returned

Results trigger appropriate actions:
- **Item result** --> navigate into the item
- **Value result** --> display the value
- **Error result** --> show error message

## Vocabulary Customization and Scripting

Vocabulary is persistent and customizable. Users can:
- Add custom aliases for existing verbs
- Create scripted expressions that chain actions
- Name components with proper nouns
- All stored in EntryVocabulary contributions on components

### Custom Aliases

A user adds an alias to a component's EntryVocabulary:

```
VocabularyContribution {
    trigger: Sememe(cg.verb:commit)     # "I respond to the commit sememe"
    target:  Sememe(cg.verb:commit)     # Simple: same sememe, registers capability
}
```

Or a literal proper noun:

```
VocabularyContribution {
    trigger: "shopping list"            # Literal token, optionally with language tag
    target:  <this component>           # Registers a name
}
```

### Scripted Expressions

An alias can target an **expression** instead of a single sememe:

```
VocabularyContribution {
    trigger: "deploy"                           # Literal token
    target:  Expression("commit then push to production")  # Parsed recursively
}
```

When "deploy" is typed, the target expression is parsed through the same vocabulary system:

```
"deploy"
    --> resolves to Expression("commit then push to production")
    --> parse: [commit] [then] [push] [to] [production]
    --> resolve each token through TokenDictionary
    --> execute: commit(); push(target=production)
```

Language resolving to language resolving to action. Expressions compose through the same mechanism that handles single verbs.

## Why Semi-Natural Language?

Traditional interfaces force rigid syntax: `git commit -m "message"`, `docker run -it --name foo image`. Common Graph's expression input is different:

1. **Tokens are references, not strings** — "move" resolves to a sememe, not parsed as text
2. **Order is flexible** — semantic frame assembly doesn't care about word order
3. **Completion narrows semantically** — the system knows what makes sense next
4. **Multilingual** — the same verb works in any language
5. **Context-aware** — the focused Item determines available vocabulary
6. **Customizable** — users add their own vocabulary through components
7. **Composable** — expressions can chain through scripted aliases

This isn't trying to understand arbitrary natural language. It's a **structured-but-flexible** input system where the graph's semantic structure guides the user toward valid expressions. The approach is closest to what the NLP literature calls *executable semantic parsing* — mapping language directly to actions on a knowledge graph (see [references/Liang 2016](references/Liang%202016%20-%20Learning%20Executable%20Semantic%20Parsers.pdf), [references/Berant et al 2013](references/Berant%20et%20al%202013%20-%20Semantic%20Parsing%20on%20Freebase.pdf)). The key difference: those systems learn statistical mappings from data, while Common Graph uses deterministic vocabulary resolution through a curated sememe backbone.

## Core Vocabulary

### Core Verbs

| Sememe ID | Description |
|-----------|-------------|
| `cg.verb:create` | Create a new instance |
| `cg.verb:edit` | Begin editing |
| `cg.verb:commit` | Commit changes |
| `cg.verb:delete` | Delete an item |
| `cg.verb:describe` | Show details |
| `cg.verb:open` | Open / navigate into |
| `cg.verb:search` | Search / query |
| `cg.verb:import` | Import data |
| `cg.verb:export` | Export data |
| `cg.verb:exit` | Exit current context |
| `cg.verb:back` | Navigate back |

### Game Verbs

| Sememe ID | Description |
|-----------|-------------|
| `cg.verb:move` | Move a piece/element |
| `cg.verb:resign` | Resign from game |
| `cg.verb:select` | Select element |
| `cg.verb:place` | Place element |
| `cg.verb:flag` | Flag tile |
| `cg.verb:join` | Join game |
| `cg.verb:leave` | Leave game |
| `cg.verb:roll` | Roll dice |
| `cg.verb:deal` | Deal cards |
| `cg.verb:play` | Play card |
| `cg.verb:pass` | Pass turn |
| `cg.verb:draw` | Draw card |
| `cg.verb:bid` | Place bid |
| `cg.verb:bet` | Place bet |
| `cg.verb:fold` | Fold hand |
| `cg.verb:score` | Score category |

## References

**Internal:**
- [Sememes](sememes.md) — The semantic backbone (what sememes are, hierarchies, anchoring)
- [Components](components.md) — EntryVocabulary contributions, component proper nouns
- [Library](library.md) — TokenDictionary storage architecture
- [Protocol](protocol.md) — DISPATCH and LOOKUP messages

**Academic foundations:**
- [Fillmore 1982 — Frame Semantics](references/Fillmore%201982%20-%20Frame%20Semantics.pdf) — The frame semantics that CG's SemanticFrame + ThematicRole descend from
- [Baker, Fillmore, Lowe 1998 — FrameNet](references/Baker%2C%20Fillmore%2C%20Lowe%201998%20-%20The%20Berkeley%20FrameNet%20Project.pdf) — FrameNet as a computational resource for frame semantics
- [Austin 1962 — How to Do Things with Words](references/Austin%201962%20-%20How%20to%20Do%20Things%20with%20Words.pdf) — Performative utterances: saying IS doing
- [Searle 1969 — Speech Acts](references/Searle%201969%20-%20Speech%20Acts.pdf) — Formalizing illocutionary force
- [Montague 1973 — Proper Treatment of Quantification](references/Montague%201973%20-%20The%20Proper%20Treatment%20of%20Quantification.pdf) — Natural language given formal semantics
- [Liang 2016 — Executable Semantic Parsers](references/Liang%202016%20-%20Learning%20Executable%20Semantic%20Parsers.pdf) — The NLP field closest to CG's dispatch model
- [Kay 1993 — The Early History of Smalltalk](references/Kay%201993%20-%20The%20Early%20History%20of%20Smalltalk.pdf) — "The big idea is messaging"
- [Hewitt et al 1973 — Actor Model](references/Hewitt%2C%20Bishop%2C%20Steiger%201973%20-%20A%20Universal%20Modular%20ACTOR%20Formalism.pdf) — Items as independent message-processing agents
- [Bocklisch et al 2017 — Rasa](references/Bocklisch%20et%20al%202017%20-%20Rasa%20Open%20Source%20Language%20Understanding.pdf) — The closest industrial system (intent + entity dispatch)
