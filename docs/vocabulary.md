# Vocabulary

**Vocabulary** is the linguistic surface of Common Graph. Every interaction — creating, navigating, querying, computing, customizing — flows through a unified language system where tokens resolve to sememes, and sememes drive action. This system is holistic: it handles all parts of speech, works in any human language, and uses the same resolution mechanism for verbs, nouns, proper names, units, operators, functions, and prepositions.

> This document covers the vocabulary system, token resolution, dispatch, the expression input, and how items expose their linguistic surface. For the semantic backbone (what sememes are, how they're anchored), see [Sememes](sememes.md). For how components declare vocabulary contributions, see [Components](components.md).

## Core Principle: Everything Is Language

Traditional systems separate "commands" from "data" from "names" from "math." Common Graph doesn't. Every meaningful interaction is expressed through language — an approach with deep roots in formal semantics and speech act theory. Montague showed that natural language can be given the same formal treatment as programming languages (see [references/Montague 1973](references/Montague%201973%20-%20The%20Proper%20Treatment%20of%20Quantification.pdf)). Austin and Searle showed that utterances are *actions*, not just descriptions (see [references/Austin 1962](references/Austin%201962%20-%20How%20to%20Do%20Things%20with%20Words.pdf), [references/Searle 1969](references/Searle%201969%20-%20Speech%20Acts.pdf)). Common Graph takes both ideas literally: every token resolves to a meaning, and every expression dispatches an action or evaluates to a value.

- **Verbs**: "create", "move", "exit" — dispatch to methods
- **Nouns**: "item", "log", "roster" — type references, navigation targets
- **Proper nouns**: "My Shopping List", "notes", "chat" — specific items or components
- **Units**: "meters", "kilograms", "seconds" — nouns with dimensional metadata
- **Operators**: "+", "-", "=", ">", "|>" — arithmetic, comparison, composition
- **Functions**: "sqrt", "sin", "max" — pure computation, applied to arguments
- **Prepositions**: "in", "with", "to", "from" — structure the expression, fill thematic roles
- **Modifiers**: "all", "recent", "unread" — qualify queries

All of these resolve through the same pipeline: token to sememe to action or value.

## The Resolution Pipeline

```
Token (any language)
    |
TokenDictionary (scoped lexicon)
    |
Sememe (universal meaning unit)
    |
What kind of expression?
    |
    +-- Command frame  --> verb + roles --> dispatch to method
    +-- Math/logic     --> operators + operands --> evaluate via Pratt parser
    +-- Function call  --> function + arguments --> apply function
    +-- Quantity       --> numeral + unit --> construct Quantity value
    +-- Navigation     --> bare noun/proper noun --> resolve and navigate
    +-- Query          --> modifier + noun --> filter and list
```

The expression input handles all of these uniformly. Whether you type `create document`, `5 + 3`, `sqrt(144)`, or `notes`, the same token resolution pipeline processes your input — it just routes to different evaluation paths based on what the tokens resolve to.

### Example: Command Frame

```
alice@project> create document
```

A user types "create document" into an Item's prompt. The tokens resolve:

```
"create"   --> Sememe(cg.verb:create, VERB)
"document" --> Sememe(cg:type/document, NOUN)
```

The FrameAssembler sees a verb + noun and builds a semantic frame: `verb=create, THEME=document`. Dispatch follows the inner-to-outer chain.

### Example: Mathematical Expression

```
alice@project> (3 + 4) * 2
```

The tokens resolve:

```
"("  --> structural (open paren)
"3"  --> literal number
"+"  --> Sememe(cg:operator/add, OPERATOR)
"4"  --> literal number
")"  --> structural (close paren)
"*"  --> Sememe(cg:operator/multiply, OPERATOR)
"2"  --> literal number
```

The presence of operators and literals triggers the Pratt parser, which builds an AST respecting precedence and associativity. The result evaluates to `14`. No verb, no dispatch — just evaluation.

### Example: Function Application

```
alice@project> sqrt(144)
```

```
"sqrt" --> Sememe(cg:function/sqrt, FUNCTION)
"("    --> structural
"144"  --> literal number
")"    --> structural
```

The parser recognizes function call syntax: an identifier followed by parenthesized arguments. The function sememe carries its evaluation logic. Result: `12`.

### Example: Mixed

```
alice@project> move pawn to e4
alice@project> 5 meters + 3 meters
alice@project> sin(pi / 4)
alice@project> notes
```

All four use the same input system. The first builds a command frame. The second constructs quantities and adds them. The third evaluates a mathematical function. The fourth navigates to a proper noun.

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

The scope can be null for universal postings (language-neutral symbols like "m", "kg", "+", "USD", "sin", "sqrt") that resolve for everyone. Everything else has a scope:

```
Scope: cg:language/eng     Token: "create"     → cg.verb:create
Scope: cg:language/spa     Token: "crear"      → cg.verb:create
Scope: cg:language/eng     Token: "meter"      → cg:unit/meter
Scope: cg:language/spa     Token: "metro"      → cg:unit/meter
Scope: <item X>            Token: "notes"      → <component on X>
Scope: <user Y>            Token: "deploy"     → <user Y's alias>
Scope: null                Token: "m"          → cg:unit/meter
Scope: null                Token: "+"          → cg:operator/add
Scope: null                Token: "sqrt"       → cg:function/sqrt
```

### Scope Chain Resolution

When a user types a token, the TokenDictionary resolves it against a **scope chain** — a list of ItemIDs assembled from context. The caller decides what's in the chain:

- The focused item, its ancestors, the session
- The user's active languages (`cg:language/eng`, `cg:language/spa`)
- The user's own item (personal aliases)
- null (always included — universal symbols and operators)

The dictionary looks up `<scope><token>` for each scope in the chain and returns all matching postings. One mechanism, one lookup path.

### Token Sources

Postings come from:
- **Sememe symbols**: Language-neutral shorthand declared on sememes — indexed with null scope
- **Seed vocabulary**: Bootstrap English tokens indexed under `cg:language/eng`
- **Lexicon imports**: WordNet, CILI — each language's lexemes scoped to its Language Item, merged idempotently
- **Component vocabulary**: EntryVocabulary contributions scoped to their item
- **Relations**: Named relations (title, alias) scoped to their item
- **User customization**: Custom aliases and scripted expressions scoped to the user or item

## Operators and Functions

Operators and functions are first-class vocabulary — they resolve through the same TokenDictionary and are represented as sememes. Their precedence, associativity, and behavior are data-driven (carried by the Operator or Function item), not hardcoded syntax.

### Operators

Operator sememes carry metadata: precedence level, associativity (left/right), fixity (prefix/infix/postfix). The ExpressionParser reads this metadata to build correct ASTs without hardcoding any operator.

```
Operator {
    symbol:        "+"           # Universal token (null scope)
    precedence:    6             # Higher binds tighter
    associativity: LEFT          # Left-to-right grouping
    fixity:        INFIX         # Between operands
}
```

Standard arithmetic (`+`, `-`, `*`, `/`, `^`), comparison (`==`, `!=`, `<`, `>`, `<=`, `>=`), logical (`&&`, `||`, `!`), and composition (`|>`) operators are seed vocabulary. Users and items can define additional operators.

### Functions

Functions are sememes with evaluation semantics — pure computation applied to arguments. Like operators, they resolve through the TokenDictionary:

```
alice@project> sqrt(144)        # Built-in function
alice@project> max(a, b, c)     # Multi-argument
alice@project> f(x) = x^2 + 1  # User-defined function (assignment)
alice@project> data |> filter(active) |> sort(name)  # Pipe composition
```

Function definition (`f(x) = expr`) creates a VocabularyContribution: the function name becomes a scoped posting, and the body is stored as an Expression. Subsequent calls to `f(5)` evaluate the stored expression with `x` bound to `5`.

### Parentheses

Parentheses are the **only structural syntax** — they're the sole reserved characters. Everything else (operators, functions, verbs, nouns) is vocabulary that resolves through the TokenDictionary. This means:

- No reserved words. "exit" is vocabulary, not syntax.
- No escape characters. Quoting uses matched delimiters.
- Operator precedence is data, not grammar rules.

## Item Vocabulary

Every Item has a **vocabulary** — its linguistic surface. An item's vocabulary is the **merged union of its components' vocabularies**, combined with its type's code-defined verbs.

### Two Layers

```
Code Layer (transient, declared in type definitions)
    |
    +-- @Verb methods on the Item type --> base verbs all items of this type share
    +-- @Verb methods on component types --> component-specific verbs
    |
User Layer (persistent, from EntryVocabulary)
    |
    +-- Custom aliases ("deploy" --> cg.verb:commit)
    +-- Scripted expressions ("deploy" --> "commit then push to production")
    +-- Function definitions (f(x) = x^2 + 1)
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

The result is a live dispatch map: sememeId to VerbEntry, plus all proper nouns, aliases, operators, and functions registered as scoped postings.

## Dispatch: Inner to Outer

When a command expression is evaluated, the system checks vocabularies from most specific to most general:

```
Component vocabulary   (most specific — focused component's verbs)
    |
Item vocabulary        (type-level verbs)
    |
Session vocabulary     (session-level verbs like "exit", "back")
    |
Librarian vocabulary   (system-level)
```

This means a component's local meaning wins over the item's general meaning, which wins over the session's global meaning.

Mathematical expressions and function calls don't follow this chain — operators and functions resolve directly from the TokenDictionary (typically at universal/null scope) and evaluate without verb dispatch.

### Disambiguation Through Language

There are no reserved words. No escape hatches. No special prefixes. When a token is ambiguous — it matches entries in multiple scopes — the system presents a **dropdown** of possibilities, and the user refines with more language:

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
- A type (`cg:type/session`) with verb definitions (`exit`, `back`, `switch`, `authenticate`)
- Components (activity log, preferences, authenticated users)
- Relations (session to current user, session to focused item)

This means session verbs participate in the same vocabulary system as everything else. "Exit" resolves through the TokenDictionary to `cg.verb:exit`, and the Session's vocabulary has a VerbEntry for it. No special-case string matching.

Sessions can be ephemeral (in-memory, never committed) or persistent (committed, versioned, shareable — like sharing your shell configuration or editor setup).

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

Every verb invocation returns an **ActionResult**:

```
ActionResult {
    success: boolean    # Whether the action succeeded
    value:   any        # Return value (may be null)
    error:   Error?     # Error details if failed
}
```

This is the universal return type for all dispatch — CLI, GUI, remote session, or inter-item invocation.

## The Expression Input System

The vocabulary system connects to the user through the **expression input** — a tokenizing prompt on every Item. This is not a command line. It's a **semi-structured language interface** that handles commands, mathematical expressions, function application, and navigation through a single unified input.

### Every Item Has a Prompt

Each Item has its own prompt. The prompt always shows `actor@context>`:

```
alice@session>                  # Session level (the default context)
alice@chess>                    # Focused on a chess game
alice@chess/board>              # Navigated into a component
bob@project>                    # Different user, different item
```

You are always *somewhere* (the context) and always *someone* (the actor). Typing into the prompt dispatches through that Item's vocabulary, or evaluates an expression. Different Items have different vocabularies — a chess game offers "move" and "resign", a document offers "edit" and "commit", a roster offers "add" and "remove". But mathematical expressions and universal operators work everywhere.

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
| **Ref** | Resolved item reference | `[create]` | Verb, noun, function, or proper noun resolved from token |
| **Literal** | Typed value | `42`, `"hello"`, `true` | Numbers, booleans, quoted strings |
| **Operator** | Infix/prefix operator | `+`, `*`, `\|>` | Arithmetic, logic, comparison, pipe |
| **Paren** | Grouping | `(`, `)` | Expression structure |

Ref tokens are the key — they're resolved references to sememes or items in the graph. When you type "move" and press Tab, it resolves to the `cg.verb:move` sememe. When you type "sqrt" and it's followed by `(`, it resolves to the `cg:function/sqrt` sememe. The expression is a sequence of graph references, not text to be parsed.

### Expression Routing

The expression input handles multiple expression types through a single pipeline. After tokens are resolved, the system determines which evaluation path to use:

1. **Mathematical expressions** — tokens contain operators, all-numeric operands, or function calls with no verb present. Routed to the Pratt parser, which builds an AST from operator precedence metadata and evaluates it.

2. **Command frames** — tokens contain a verb sememe. Routed to the FrameAssembler, which maps arguments to thematic roles and dispatches via the inner-to-outer vocabulary chain.

3. **Navigation** — a single noun or proper noun with no verb. Resolves the target and navigates.

4. **Mixed** — verbs can take expression arguments: `set width to (3 + 4) * 2`. The Pratt parser handles the parenthesized sub-expression, and its result fills a parameter slot in the command frame.

### Completion and Semantic Narrowing

Pressing Tab triggers lookup via the TokenDictionary with the current scope chain. Completions are **semantically narrowed** based on what's already typed.

The **ExpressionContext** analyzes the current token list:

1. Find the verb (if any) — the first token whose sememe has part-of-speech VERB
2. Identify prepositional phrases — a preposition followed by an object fills a thematic role
3. Match bare nouns to unfilled argument slots
4. Detect operator context — after an operator, complete with operands
5. Compute which roles are still unfilled

Then it **filters completions**:

| Current State | Filter Rule |
|---------------|-------------|
| Empty | Show everything (verbs, nouns, functions, operators) |
| Verb selected | Exclude other verbs; show nouns, prepositions, literals |
| After operator | Show operands: nouns, functions, literals |
| After open paren | Show everything that can start a sub-expression |
| Last token is a preposition | Show nouns and proper nouns |
| All required roles filled | Show optional roles and prepositions |

**Example**: At `alice@chess>`:

```
alice@chess> m                         # Completions: move, max, min...
alice@chess> move                      # Tab --> resolves to [move] verb
alice@chess> [move] p                  # Completions: pawn, piece... (no verbs)
alice@chess> [move] [pawn] to          # Tab --> resolves [to] preposition
alice@chess> [move] [pawn] [to] e      # Completions: e4, e5... (positions)
alice@chess> [move] [pawn] [to] [e4]   # Enter --> dispatches move(THEME=pawn, TARGET=e4)
```

**Example**: Mathematical expression:

```
alice@project> 2 * (                   # After open paren: show operands
alice@project> 2 * (3 + sq             # Completions: sqrt
alice@project> 2 * (3 + sqrt(9))       # Enter --> evaluates to 12
```

### Semantic Frame Assembly

When Enter is pressed on a command expression, the token list is assembled into a **semantic frame** — an order-agnostic structure that maps tokens to verb parameters by thematic role:

```
Tokens:  [move] [pawn] [to] [e4]
Frame:   verb=move, THEME=pawn, TARGET=e4
```

The assembly process:

1. Find the verb (first token with VERB part-of-speech)
2. Scan for prepositional phrases: `preposition + object` fills the role that preposition implies
3. Match remaining bare items to argument slots by first-fit
4. Evaluate any sub-expressions (parenthesized math, function calls)
5. Build the frame

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

The parser knows to form a Quantity because the noun-sememe carries dimensional metadata (it's a unit). No special syntax — just language. Quantities support arithmetic with dimensional analysis:

```
alice@project> 5 meters + 3 meters     # --> 8 meters
alice@project> 10 km / 2 hours         # --> 5 km/h
alice@project> 5 meters + 3 kg         # --> error: incompatible dimensions
```

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

1. If the pending text matches a known operator (AND, OR) --> commit as operator token
2. If it's a recognized literal (number, boolean, quoted string) --> commit as literal token
3. If it's a parenthesis --> commit as structural token
4. Otherwise --> insert a space (for multi-word lookups)

### Dispatch

When Enter is pressed:

1. Any remaining pending text is committed (resolved where possible)
2. The token list is analyzed: does it contain operators/functions (expression) or a verb (command)?
3. **Expression path**: the Pratt parser builds an AST, evaluates it, returns a value
4. **Command path**: the FrameAssembler builds a semantic frame, the verb is located via the scope chain (component --> item --> session --> librarian), and invoked with the frame's bindings
5. **Navigation path**: a single resolved noun/proper noun navigates to that item
6. An ActionResult or value is returned

Results trigger appropriate actions:
- **Item result** --> navigate into the item
- **Value result** --> display the value
- **Created result** --> show in activity log and tree
- **Error result** --> show error message

## Deferred Resolution

Token resolution doesn't have to be all-or-nothing. When a token is ambiguous — matching multiple postings across different parts of speech or scopes — the system carries all candidates forward rather than forcing an immediate choice. Later tokens prune earlier candidates, and the expression resolves progressively as context accumulates.

### The Problem with Eager Resolution

Consider: a user types `create python`. At the space boundary after "python", the TokenDictionary returns multiple postings:

```
"python" --> [
    Posting(target=cg:language/python, scope=cg:language/eng, weight=0.8),   # Programming language
    Posting(target=cg:taxon/python,    scope=cg:language/eng, weight=0.6),   # Snake genus
    Posting(target=cg:mythology/python, scope=cg:language/eng, weight=0.4),  # Greek mythology
]
```

Eager resolution would either pick the highest-weight candidate (often wrong) or pop a disambiguation dropdown (interrupting flow). But the user isn't done typing. The *next* token — "script", "image", "document" — determines which "python" was meant.

### Candidate Sets

When a token matches multiple postings, the system creates a **CandidateToken** instead of a resolved RefToken. A CandidateToken carries the full set of postings:

```
CandidateToken {
    text:        "python"
    candidates:  [Posting...]    # All matching postings
    resolved:    Posting?        # null until disambiguation succeeds
}
```

The expression input displays CandidateTokens differently from resolved RefTokens — visually marked as pending (e.g., a chip outline without the filled background, or with a small dropdown indicator).

When only one posting matches, the token resolves immediately to a RefToken — the common case. Deferred resolution only kicks in when there's genuine ambiguity.

### Progressive Pruning

As subsequent tokens are resolved, the system prunes earlier candidate sets by checking semantic compatibility:

```
User types: create python script

Token 1: [create]  → RefToken(cg.verb:create)           # Unambiguous verb
Token 2: "python"  → CandidateToken([language, taxon, mythology])  # Ambiguous
Token 3: "script"  → resolves to Sememe(cg:type/script)  # Noun

Frame assembly tries all combinations:
  create(THEME=python-language, ???=script)  → python-language IS-A language, script IS-A type → "create python script" ✓
  create(THEME=python-taxon, ???=script)     → python-taxon IS-A taxon, script doesn't fit → ✗
  create(THEME=python-mythology, ???=script) → same problem → ✗

Result: "python" auto-resolves to cg:language/python
```

The pruning uses **semantic fit scoring**:

1. **Role compatibility** — does this candidate make sense in the thematic role it would fill? A programming language can be a THEME of "create script"; a snake genus can't.
2. **Modifier-noun agreement** — if the candidate acts as a modifier (adjective-like), does it semantically compose with the noun it modifies? "Python script" composes; "python mathematics" doesn't.
3. **Predicate selectional restrictions** — the verb's `@Param` metadata may declare type constraints on its arguments. If `create` expects a type noun as THEME, candidates that aren't types score lower.
4. **Relational evidence** — if the candidate item has relations connecting it to other tokens in the expression (e.g., `python-language RELATED-TO script`), that raises its score.

### Auto-Resolution and User Disambiguation

After pruning:

- **One candidate survives** → auto-resolve. The CandidateToken silently becomes a RefToken. The chip fills in. No user action needed.
- **Multiple candidates survive** → the token stays as a CandidateToken. Its dropdown shows the surviving candidates. The user can click one, or keep typing to provide more context.
- **Zero candidates survive** → the token reverts to unresolved text. Something doesn't fit; the user sees it hasn't resolved and can retype.

### Visual Model

In the GUI, the expression input renders token states distinctly:

```
Resolved:     [create]  [script]     — filled chip, icon + label
Candidates:   [python ▾]             — outlined chip, dropdown indicator
Unresolved:   python                 — plain text, no chip
```

The candidate dropdown appears inline, under the token — the same dropdown used for completions, just showing the surviving candidates rather than prefix matches. Selecting a candidate locks it in. If the user keeps typing and a later token auto-resolves the candidate, the dropdown closes and the chip fills.

This means disambiguation is **visual, not verbal**. The system doesn't ask "did you mean X or Y?" — it shows you that "python" is still open, and you can see the options. If you keep typing and the system figures it out, it just resolves. If it can't, the pending candidates stay visible until you choose one.

### Interaction with Frame Assembly

The FrameAssembler already builds `SemanticFrame` objects from resolved tokens. With deferred resolution, it tries all **candidate combinations** and scores each resulting frame:

```
Given: [create] [python ▾{lang, taxon}] [script]

Candidate frames:
  Frame A: verb=create, THEME=python-lang + script   → score 0.95 (modifier+noun compose)
  Frame B: verb=create, THEME=python-taxon + script   → score 0.10 (no composition)

If top score >> second score → auto-resolve python to lang
If scores are close → leave ambiguous, show candidates
```

The scoring threshold is tunable — a wide gap auto-resolves; close scores defer to the user. The system errs on the side of showing candidates rather than guessing wrong.

### Interaction with Enter (Submit)

When the user presses Enter with unresolved CandidateTokens still in the expression:

1. Run one final pruning pass with the complete token list
2. Any tokens that auto-resolve after this pass → resolve them
3. If CandidateTokens still remain → **do not dispatch**. Instead, highlight the unresolved tokens and focus the first one's dropdown. The user must resolve all ambiguity before dispatch.

This is the same principle as form validation: you can't submit until all fields are filled. But the system tries hard to fill them for you first.

### Scope Chain and Weight Interaction

Deferred resolution interacts with the existing scope chain. When candidates come from different scopes, scope proximity acts as a tiebreaker:

```
"notes" in context of a Project item:
  Posting(target=<project's notes component>, scope=<project>, weight=0.9)  # Item scope
  Posting(target=cg:type/note, scope=cg:language/eng, weight=0.7)          # Language scope

Item scope > Language scope → auto-resolve to the component
```

Weight already encodes relevance, and scope chain order encodes proximity. Together they handle most ambiguity without needing the full beam-search path. Deferred resolution is the fallback for cases where these signals aren't enough.

### Implementation Sequence

1. **CandidateToken type** — new ExpressionToken subclass carrying `List<Posting>` candidates
2. **EvalInput changes** — `tokenBoundary()` creates CandidateToken when multiple postings match, instead of taking the first one
3. **Progressive pruning** — after each new token is accepted, run pruning on all prior CandidateTokens
4. **Frame scoring** — FrameAssembler generates candidate frames for each combination; score by semantic fit
5. **Auto-resolution** — when pruning leaves one candidate, promote CandidateToken → RefToken
6. **Visual feedback** — Surface rendering for CandidateToken (outlined chip + dropdown)
7. **Submit guard** — Enter with unresolved candidates highlights them instead of dispatching

## Vocabulary Customization and Scripting

Vocabulary is persistent and customizable. Users can:
- Add custom aliases for existing verbs
- Create scripted expressions that chain actions
- Define functions (stored as expressions)
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

### User-Defined Functions

Functions defined through the input (`f(x) = x^2 + 1`) become VocabularyContributions:

```
VocabularyContribution {
    trigger: "f"
    target:  Expression("x^2 + 1", params=["x"])
}
```

Subsequent calls to `f(5)` evaluate the stored expression with `x = 5`, yielding `26`. Functions compose naturally: `g(x) = f(x) + f(x-1)`.

## Why Semi-Natural Language?

Traditional interfaces force rigid syntax: `git commit -m "message"`, `docker run -it --name foo image`. Common Graph's expression input is different:

1. **Tokens are references, not strings** — "move" resolves to a sememe, not parsed as text
2. **Order is flexible** — semantic frame assembly doesn't care about word order
3. **Completion narrows semantically** — the system knows what makes sense next
4. **Multilingual** — the same verb works in any language
5. **Context-aware** — the focused Item determines available vocabulary
6. **Customizable** — users add their own vocabulary through components
7. **Composable** — expressions chain through scripted aliases, functions, and pipes
8. **Mathematical** — arithmetic, functions, and dimensional analysis work everywhere

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
| `cg.verb:view` | Focus or create a view of an item |
| `cg.verb:place` | Mount an item in a region |

### Operators

| Symbol | Sememe ID | Precedence | Description |
|--------|-----------|------------|-------------|
| `+` | `cg:operator/add` | 6 | Addition |
| `-` | `cg:operator/subtract` | 6 | Subtraction |
| `*` | `cg:operator/multiply` | 7 | Multiplication |
| `/` | `cg:operator/divide` | 7 | Division |
| `^` | `cg:operator/power` | 8 | Exponentiation |
| `==` | `cg:operator/equals` | 4 | Equality |
| `!=` | `cg:operator/not-equals` | 4 | Inequality |
| `<` | `cg:operator/less-than` | 5 | Less than |
| `>` | `cg:operator/greater-than` | 5 | Greater than |
| `&&` | `cg:operator/and` | 2 | Logical AND |
| `\|\|` | `cg:operator/or` | 1 | Logical OR |
| `\|>` | `cg:operator/pipe` | 0 | Pipe (composition) |

### Functions

| Symbol | Sememe ID | Description |
|--------|-----------|-------------|
| `sqrt` | `cg:function/sqrt` | Square root |
| `sin` | `cg:function/sin` | Sine |
| `cos` | `cg:function/cos` | Cosine |
| `abs` | `cg:function/abs` | Absolute value |
| `max` | `cg:function/max` | Maximum |
| `min` | `cg:function/min` | Minimum |
| `log` | `cg:function/log` | Natural logarithm |

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
- [Workspace](workspace.md) — Session, views, navigation, prompt format

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
