# Sememes

**Sememes** are the semantic backbone of Common Graph — Items that anchor meaning globally, enabling precise communication across languages, domains, and time.

> "Sememes are units of meaning, just as meters are units of measure."

## The Problem with Labels

Labels are ambiguous:
- "bank" — Financial institution or river edge?
- "Java" — Island, coffee, or programming language?
- "title" — Name of a work, job position, or form of address?

String-based identifiers lead to:
- Mismatched queries across applications
- Lost relationships when labels change
- No way to translate or relate concepts
- Fragmented vocabularies per platform

This is the same problem that the metric system solved for measurement. Before meters, every region had its own units: cubits, fathoms, leagues. Commerce and science required conversion tables. The metric system created a single, universal vocabulary for physical quantities.

Sememes do the same for meaning. Cross-linguistic research confirms this is feasible — Youn et al. (2016) showed empirically that the structure of human lexical semantics is *universal* across languages, with concepts clustering into the same neighborhoods regardless of language family (see [references/Youn et al 2016](references/Youn%20et%20al%202016%20-%20Universal%20Structure%20of%20Human%20Lexical%20Semantics.pdf)).

## Sememes as Stable Anchors

A **sememe** is an Item that represents a specific meaning. Crucially, sememes are **language-agnostic** — they carry no words. Words belong to languages.

Sememes are resolved at **write time**, not read time. When data is created or related, the system resolves every concept to a globally-anchored sememe before storage. The person or code creating the data performs the disambiguation — they know whether "bank" means a financial institution or a river edge. This is trivial at creation time and nearly impossible after the fact. What gets stored is not text but a structure of semantic references, pre-indexed by meaning.

```
Sememe extends Item {
    iid:            ItemID          # Stable identity
    canonicalKey:   String          # Deterministic key (e.g., "cg.verb:create")
    slots:          [ItemID]        # For predicates: expected argument roles
    assignedRole:   ItemID          # For prepositions: role assigned to object
    symbols:        [String]        # Language-neutral symbols ("m", "kg", "+")
    glosses:        Map             # Transient bootstrap glosses (per language)
}
```

Crucially, sememes carry **no part of speech**. POS is a grammatical category that belongs on **lexemes** — the language-specific words that express the sememe. The English word "create" is a verb; the sememe `cg.verb:create` is the language-agnostic concept of bringing into existence. Different languages might categorize the same concept differently.

Domain-specific subclasses carry specialized behavior — the **class IS the behavior**:
- `Operator` — precedence, associativity, fixity, and arithmetic evaluation (`applyBinary`)
- `Function` — arity, category, and function application (`apply`)
- `StructuralVocabulary` — structural role (grouping, separation, access) for syntax symbols
- `ThematicRole` — role identity for frame bindings
- `GrammaticalFeature` — inflectional identity for morphology
- `Unit` — dimensional metadata and conversion factors
- Pronouns (`It`, `This`, `What`, etc.) — reference resolution behavior
- Conjunctions (`And`, `Or`) — expression grouping behavior

Each subclass carries both metadata (as frames on the item) AND behavior (as Java methods on the class). This is the `PredicateBehavior` pattern: every predicate can declare how it participates in **parsing** (via `contribute()`) and how it **evaluates** (via `evaluate()`).

The IID stays stable forever. Words in any language can be added, changed, or extended without touching the sememe itself.

## Sememes and Lexemes

The relationship between meaning and language is mediated by **lexemes** — the words and phrases that express a sememe in a particular language.

```
Sememe:  the concept of bringing something into existence
         (language-agnostic, IID-stable)
              |
    +---------+---------+----------+
    |         |         |          |
Lexeme:   "create"   "crear"    "creer"   ...
Language:  English    Spanish    French
```

Lexemes live in **Lexicon** components on **Language Items** — not on the sememe. Each language (English, Spanish, Japanese, etc.) is itself an Item, seeded at bootstrap with a deterministic IID. A language item's lexicon maps sememes to the words that express them:

```
Language Item: English (cg:language/eng)
    Lexicon {
        cg.verb:create   --> ["create"]
        cg:type/document --> ["document", "doc"]
        cg:unit/meter    --> ["meter", "metre"]
        ...
    }

Language Item: Spanish (cg:language/spa)
    Lexicon {
        cg.verb:create   --> ["crear"]
        cg:type/document --> ["documento"]
        cg:unit/meter    --> ["metro"]
        ...
    }
```

When a lexicon is imported (from WordNet, CILI, or other databases), the resulting lexeme-to-sememe mappings are indexed in the TokenDictionary, **scoped to their Language Item**:

```
"create"    --> Posting(target=cg.verb:create, scope=cg:language/eng)
"crear"     --> Posting(target=cg.verb:create, scope=cg:language/spa)
"document"  --> Posting(target=cg:type/document, scope=cg:language/eng)
"documento" --> Posting(target=cg:type/document, scope=cg:language/spa)
```

This is how multilingual resolution works: the user's active language preferences determine which language scopes are included in the resolution chain. An English speaker resolves "create"; a Spanish speaker resolves "crear"; both reach the same sememe.

### Language-Neutral Symbols

Some sememes carry **symbols** — language-neutral shorthand that works universally:

- Units: "m" (meter), "kg" (kilogram), "s" (second), "K" (kelvin)
- Operators: "+", "-", "=", ">", "<"
- Currency codes: "USD", "EUR", "JPY"

These are not lexemes — they're part of the sememe itself, because they transcend any particular language. The symbol "m" means meter everywhere.

Symbols are indexed in the TokenDictionary as **universal postings** (scope = null) — they resolve for all users regardless of language preferences. They flow through the same resolution pipeline as lexemes and proper nouns; there is no separate path for symbols.

## Parsing Behavior: The Class IS the Behavior

Sememes don't just carry metadata — they carry **behavior**. Every sememe subclass can declare how it participates in parsing and evaluation through two methods:

- **`contribute(ParseContext)`** → `ParseContribution` — how this sememe influences parsing. Returns metadata (precedence, fixity, expected roles, structural role) and/or active behavior (delegate to a sub-language, chain additional frames).
- **`evaluate(bindings, evaluator, scope)`** → result — how this sememe evaluates with filled bindings.

This is the `PredicateBehavior` interface — the ONE abstraction that unifies parsing and evaluation. It replaces separate handling for operators, functions, verbs, prepositions, and structural symbols. Examples:

| Sememe type | `contribute()` returns | Effect |
|-------------|----------------------|--------|
| Operator (+) | `infix(precedence=10, LEFT)` | Expression parser handles precedence climbing |
| Function (sqrt) | `prefix, grouped=true` | Parser expects parenthesized arguments |
| Preposition (on) | `assignedRole=GOAL` | Next token fills the GOAL role |
| Conjunction (and) | `structural(CONJUNCTION)` | Parser splits/groups at this point |
| Pronoun (it) | `structural(PRONOUN)` | Resolver replaces with referent from context |
| Structural (() | `structural(OPEN_GROUP)` | Parser opens a grouping scope |

The parser doesn't hardcode behavior for specific sememes. It calls `contribute()` on whatever sememe it encounters and reads the result. This means new sememes — a chess notation parser, a regex syntax, a domain-specific operator — participate in parsing by declaring their behavior on the class.

## Parts of Speech

Part of speech is **not a property of the sememe** — it is a grammatical category that belongs on **lexemes** (language-specific words). The English word "create" has POS=VERB. The Spanish word "crear" has POS=VERB. The sememe `cg.verb:create` carries no POS — it is language-agnostic.

POS flows through the system via lexeme features on **Postings** in the TokenDictionary. When "create" resolves, its Posting carries `features={VERB}`. The parser reads these features to know the token's grammatical role.

| Part of Speech | Role in Parsing | Examples |
|----------------|----------------|----------|
| **Verb** | Dispatchable action, becomes the frame predicate | create, move, describe, view |
| **Noun** | Type reference, fills argument slots | item, chess, user |
| **Adjective** | Modifier, attaches to nearest noun | recent, public |
| **Adverb** | Modifier, attaches to verb | recursively, quietly |
| **Preposition** | Assigns a thematic role to the following token | to, from, with, named |
| **Conjunction** | Splits or groups expressions | and, or |
| **Pronoun** | Reference to context (focused item, recent item) | it, this, last |

### Verbs

Verb sememes declare what arguments they expect via **slots** (thematic roles). The `contribute()` method surfaces these as `expectedRoles`:

```
create (verb sememe) {
    iid:   cg.verb:create
    slots: [THEME]          # expects one argument: what to create
}

# Lexemes (in language lexicons):
#   English: "create" (VERB, LEMMA), "created" (VERB, PAST), ...
#   Spanish: "crear" (VERB, LEMMA), "creó" (VERB, PAST), ...
```

Items respond to verb sememes by declaring `@Verb` methods. See [Vocabulary](vocabulary.md).

### Nouns

Noun sememes represent things and concepts. In expressions, nouns serve as arguments to verbs ("create **chess**"), navigation targets ("**notes**"), and query subjects.

```
chess (noun sememe) {
    iid: cg.sememe:chess
    broader: [game]
    # No POS here — "chess" is a noun in English, but that's on the lexeme
}
```

### Units

Units of measurement are **sememes** (`Unit extends Sememe`) with dimensional metadata and language-neutral symbols. They are units of meaning that happen to describe measurement — which is exactly the project's thesis.

```
meter (unit sememe) {
    iid:       cg:unit/meter
    symbols:   ["m"]
    dimension: LENGTH
    scale:     1.0  (base unit)
}
```

The symbol "m" is on the sememe because it's universal. The words "meter" (English), "metre" (British English, French), "metro" (Spanish) live in their language lexicons.

When a numeral precedes a unit, they combine into a **Quantity**:

```
"5 meters"    --> Quantity(5, meter-sememe)
"3 kg"        --> Quantity(3, kilogram-sememe)
```

### Prepositions

Prepositions carry an `assignedRole` — the thematic role they assign to their object. This is declared as a frame on the sememe and surfaced through `contribute()`:

```
on (preposition sememe) {
    iid:          cg.prep:on
    assignedRole: GOAL          # "on" assigns GOAL to the next token
}
```

The parser calls `contribute()` on the preposition sememe, reads `assignedRole`, and frames the following token to that role:

```
"move pawn to e4"
    --> MOVE { THEME=pawn, GOAL=e4 }       # "to" has assignedRole=GOAL

"copy document from archive"
    --> COPY { THEME=document, SOURCE=archive }  # "from" has assignedRole=SOURCE
```

No hardcoded preposition logic — the parser reads the metadata from `contribute()`.

### Structural Symbols

Parentheses, commas, pipes, and other syntax symbols are sememes too (`StructuralVocabulary extends Sememe`). Their `contribute()` returns a structural role:

```
( (structural sememe) {
    iid:    cg.syntax:open-group
    symbol: "("
    contribute() → structural(OPEN_GROUP)
}
```

These resolve through the TokenDictionary like any other symbol. The parser reads their structural role from `contribute()`. They are scoped to the expression language (`cg:language/expr`) — when `(` resolves, the language inference detects "we're in expression mode."

### Conjunctions and Pronouns

Conjunctions (`And`, `Or`) and pronouns (`It`, `This`, `What`, `Any`, `Last`) are proper Sememe subclasses with parsing behavior:

- **Conjunctions**: `contribute()` returns `CONJUNCTION` — the parser splits or groups at this point
- **Pronouns**: `contribute()` returns `PRONOUN` — the resolver replaces them with referents from discourse history ("it" → most recently created item, "this" → focused item)

No hardcoded IID checks. The parser calls `contribute()` and reads the structural role.

### Modifiers

Adjective and adverb sememes qualify other sememes. In expressions, adjectives attach to the nearest noun and adverbs attach to the verb. The FrameAssembler handles this via POS features from the token's Posting.

## Using Sememes as Predicates

Every relation predicate is a sememe:

```
# Unambiguous — references a specific meaning by IID:
book:Hobbit --> TITLE (sememe IID) --> "The Hobbit"

# NOT this — ambiguous string, different "title" meanings collide:
book:Hobbit --> "title" --> "The Hobbit"
```

This ensures:
- Same meaning across all users of the predicate
- Queryable by concept, not string matching
- Automatic translation of predicate labels in UI (via language lexicons)

## CILI: The Global Anchor

Common Graph seeds its sememes from **WordNet** via the **Collaborative Interlingual Index (CILI)**. WordNet (see [references/Miller et al 1993](references/Miller%20et%20al%201993%20-%20Introduction%20to%20WordNet.pdf)) provides the lexical database; CILI (see [references/Bond et al 2016](references/Bond%2C%20Vossen%20et%20al%202016%20-%20CILI%20the%20Collaborative%20Interlingual%20Index.pdf)) provides the language-neutral identifiers that let the same concept be referenced across languages. The Open Multilingual Wordnet (see [references/Bond, Foster 2013](references/Bond%2C%20Foster%202013%20-%20Linking%20and%20Extending%20an%20Open%20Multilingual%20Wordnet.pdf)) extends this to 26+ languages — the model for Common Graph's language import pipeline.

- WordNet provides ~120,000 synsets (synonym sets) with definitions, parts of speech, and hierarchical relationships
- CILI maps synsets across languages (English, Spanish, Japanese, Arabic, and more)
- Each synset becomes a sememe with a deterministic IID
- Each language's words for those synsets become lexemes in that language's lexicon

This gives a massive, academically-vetted semantic backbone — not invented from scratch, but anchored in decades of computational linguistics research. The inter-lingual index concept originated with EuroWordNet (see [references/Vossen 1998](references/Vossen%201998%20-%20EuroWordNet%20Multilingual%20Lexical%20Semantic%20Networks.pdf)), which demonstrated that autonomous language-specific wordnets could be linked through a shared concept index — the same core idea Common Graph uses.

Deterministic IIDs mean two independently bootstrapped nodes agree on meaning without prior coordination:

```
IID for the concept "dog" = Hash("cili:i23456")     # Same everywhere
```

### What Gets Imported

A full WordNet import populates:

- **Sememe items** for every synset, with part-of-speech, hierarchy (broader/narrower), and glosses
- **Lexemes** in the English language item's lexicon, mapping every English word to its sememe(s)
- **Cross-lingual lexemes** via CILI for any language with a WordNet
- **Unit sememes** extended with dimensional data and symbols
- **TokenDictionary postings** for every word in every imported language

This means the TokenDictionary contains the entire English language (and potentially others), all mapped to precise semantic anchors.

## Extending the Vocabulary

Sememes are extensible:

### Domain-Specific Sememes

Medical, legal, engineering, or any specialized domain can define sememes:

```
hypertension (sememe) {
    iid:     med:concept/hypertension
    broader: [medicalCondition]
}

# English lexicon adds:
hypertension --> ["hypertension", "high blood pressure"]
# Spanish lexicon adds:
hypertension --> ["hipertensión"]
```

### Project-Specific Sememes

Organizations can define their own concepts:

```
sprintReview (sememe) {
    iid:     org:concept/sprintReview
    broader: [meeting]
}
```

### Proper Nouns

Proper nouns are not sememes — they're **literal tokens** that name specific items or components. "France", "My Shopping List", "Alice" are names scoped to a context, not universal units of meaning.

Proper nouns are registered as **scoped postings** in the TokenDictionary (see [Vocabulary](vocabulary.md)), optionally with a language tag ("France" in English, "Frankreich" in German). They can also be registered as vocabulary contributions on components.

A proper noun without a language tag is language-neutral (e.g., a username, a product code, an identifier like "note-423").

## Sememe Hierarchies

Sememes relate to each other through semantic relationships. The two most important are **broader** (hypernym) and **narrower** (hyponym), which form a hierarchy:

```
thing
+-- living_thing
|   +-- animal
|   |   +-- mammal
|   |   |   +-- dog
|   |   |   +-- cat
|   |   |   +-- human
|   |   +-- bird
|   +-- plant
+-- artifact
    +-- tool
    +-- document
```

These relationships can be modeled as data on the sememe item or as relations between sememe items. Both approaches have trade-offs:

- **As data** (broader/narrower fields on the sememe): Fast to traverse, self-contained, but changes require new versions of the sememe item
- **As relations** (signed assertions between sememes): Extensible by anyone, auditable, but require relation queries to traverse

The hierarchy enables:
- **Subsumption queries**: "Find all animals" includes dogs, cats, birds
- **Semantic reasoning**: A dog is a mammal is an animal is a living thing
- **Faceted navigation**: Browse by broader/narrower relationships
- **Type compatibility**: Is this frame type compatible with that slot?
- **Progressive narrowing**: "document" narrows to "markdown document" narrows to a specific implementation (see [Vocabulary](vocabulary.md))

## Sememes as Types

Frame types, scalar types, and item types are all sememes. The type system and the semantic system are unified:

```
plainText (noun sememe) {
    iid: cg:type/plainText
    partOfSpeech: NOUN
    broader: [documentType]
}
```

This means type definitions participate in the full semantic infrastructure:
- Hierarchical relationships (for type-based queries and progressive narrowing)
- Multilingual labels (via language lexicons, for internationalized type pickers)
- Machine-readable glosses (in lexicons, for documentation generation)

## Thematic Roles

Verb sememes declare **thematic roles** — the semantic slots that their arguments fill. Common Graph's 25 seedItem roles are aligned with [VerbNet 3.x](https://verbs.colorado.edu/verbnet/) (Martha Palmer, CU Boulder) and [ISO 24617-4 (LIRICS/SemAF-SR)](https://www.iso.org/standard/56866.html), following the hierarchical unification proposed by [Bonial et al (2011)](https://verbs.colorado.edu/~mpalmer/Ling7800/SACL-ICSC2011.pdf). The intellectual lineage traces back to Fillmore's Case Grammar (1968), through Dowty's Proto-Roles (1991), to FrameNet (Fillmore 1997+) and VerbNet (Palmer 2005+).

Roles have **no CILIs** — they are frame-theoretic concepts, not WordNet synsets. The role AGENT is not the WordNet synset for "agent"; it's the participant who intentionally initiates an event.

The roles are themselves sememes (`ThematicRole extends Sememe`):

| Role | Meaning | Example |
|------|---------|---------|
| **AGENT** | Intentional initiator | (usually the caller, implicit) |
| **PATIENT** | Affected or changed | "edit **this item**" |
| **THEME** | Located, moved, or existing | "delete **this item**" |
| **GOAL** | Abstract end-point or target | "move to **e4**" |
| **SOURCE** | Origin or starting point | "copy from **archive**" |
| **INSTRUMENT** | Tool or means | "encrypt with **this key**" |
| **RECIPIENT** | Entity receiving transfer | "send to **Alice**" |
| **PARTNER** | Co-participating agent | "play with **Bob**" |
| **EXPERIENCER** | Perceives or feels | "show **Alice**" |
| **LOCATION** | Where it happens | "store in **vault**" |
| **TIME** | When it happens | "created **Tuesday**" |
| **NAME** | Designation being assigned | "call it **Alpha**" |
| **TOPIC** | Subject of communication | "discuss **the bug**" |

Plus 12 more roles (Destination, Path, Result, Beneficiary, Stimulus, Pivot, Cause, Manner, Extent, Attribute, Purpose, Referent). See [Language](language.md#thematic-roles) for the full inventory.

Thematic roles enable:
- Order-independent expression parsing (see [Vocabulary](vocabulary.md))
- Automatic generation of help text ("create WHAT?")
- Semantic validation (does this argument fit this role?)
- **Import of role expectations from VerbNet**: each verb class declares which roles it expects, and VerbNet entries include WordNet sense keys — giving Common Graph a direct bridge from synset to slot declarations

## Predicates as Schemas

A predicate sememe declares what bindings (roles) its frames expect — this is equivalent to a database schema. The `expects()` declarations on the sememe define the template:

```
HARVEST_RECORD expects:
    LOCATION:[]                → which garden
    AGENT:[]                   → who harvested
    THEME:[]                   → what crop
    TIME:[]                    → when
    RESULT:[QUANTITY, WEIGHT]  → how much
```

See [Frames](frames.md) for the full predicates-as-schemas design.

## Multilingual Support

The sememe/lexeme separation is what makes multilingual support natural:

1. **Sememes** are language-agnostic meaning anchors (shared globally)
2. **Lexemes** are language-specific words (stored in Language Item lexicons)
3. **The TokenDictionary** indexes every token with a **scope** — the Language Item for lexemes, null for universal symbols, or any other item for proper nouns and aliases

The caller provides a **scope chain** assembled from context — active languages, focused item, user. An English speaker's chain includes `cg:language/eng`; a bilingual speaker's includes both languages. Display labels come from the preferred language's lexicon. Resolution reaches the same sememe regardless of which language's word was typed.

```
English user sees:  "create"  (from English lexicon)
Spanish user sees:  "crear"   (from Spanish lexicon)
Both dispatch:      cg.verb:create  (same sememe, same verb)
```

Language items use ISO 639-3 codes (3 letters, covering ~7,000 languages including minority, extinct, and constructed languages). Every language with a WordNet can be imported, giving its speakers full access to the system's vocabulary in their native words.

## Core Vocabulary

Common Graph defines essential sememes for its own operation:

| Category | Examples |
|----------|----------|
| **Verbs** | create, view, help, describe, cd, commit, exit, serve, authenticate |
| **Types** | item, signer, host, sememe, language, user |
| **Predicates** | title, author, created, modified, instance-of |
| **Lexical** | hypernym, hyponym, holonym, meronym, antonym |
| **Prepositions** | on, from, with, for, between, named |
| **Operators** | +, -, *, /, ==, !=, <, >, &&, \|\|, ! |
| **Functions** | sqrt, abs, sin, cos, length, upper, lower, range |
| **Structural** | (, ), comma, semicolon, pipe, dot |
| **Conjunctions** | and, or |
| **Pronouns** | it, this, last, what, any |
| **Dimensions** | length, mass, time, temperature, current, luminosity |
| **Units** | meter, kilogram, second, kelvin, and 20+ more |

These sememes are seeded at bootstrap with deterministic IIDs. Their lexemes (English words) are seeded in the English language item's lexicon. Both are available to all Items from first boot.

## Why This Matters

With sememes and the lexeme separation, you get:
- **Global concept anchoring** — same IID everywhere, independent of any language
- **True multilingual support** — words live in languages, meaning lives in sememes
- **Automatic interoperability** — shared semantic vocabulary across all nodes
- **Extensible** — new languages, domains, and concepts added without changing existing sememes
- **Unified treatment** of verbs, nouns, types, and units
- **Foundation for semantic reasoning** — hierarchy, constraints, thematic roles

This is one of Common Graph's most fundamental design decisions: meaning is a first-class, content-addressed, globally-anchored concept — not an afterthought bolted onto string labels. And words are first-class too, but they belong to their languages.

## References

**Internal:**
- [Vocabulary](vocabulary.md) — Dispatch, expression input, customization
- [Frames](frames.md) — Frame primitive, bindings, vocabulary contributions

**External resources — lexical:**
- [WordNet](https://wordnet.princeton.edu/) — Lexical database of English
- [CILI (Collaborative Interlingual Index)](https://github.com/globalwordnet/cili) — Cross-lingual concept mapping
- [Open Multilingual Wordnet](http://compling.hss.ntu.edu.sg/omw/) — WordNet extensions for many languages
- [ISO 639-3](https://iso639-3.sil.org/) — Language code standard (~7,000 languages)

**External resources — semantic roles and frames:**
- [VerbNet](https://verbs.colorado.edu/verbnet/) — Verb classes with thematic role declarations ([GitHub](https://github.com/cu-clear/verbnet))
- [FrameNet](https://framenet.icsi.berkeley.edu/) — 1,200+ semantic frames with frame-specific elements
- [SemLink](https://verbs.colorado.edu/semlink/) — Cross-resource mappings: VerbNet ↔ FrameNet ↔ PropBank ↔ WordNet
- [VerbAtlas](https://verbatlas.org/) — 466 frames, 26 universal roles, linked to BabelNet
- [ISO 24617-4 (SemAF-SR)](https://www.iso.org/standard/56866.html) — International standard for semantic role annotation

**Academic foundations:**
- [Fillmore 1968 — "The Case for Case"](references/) — The intellectual origin of thematic roles
- [Fillmore 1982 — Frame Semantics](references/Fillmore%201982%20-%20Frame%20Semantics.pdf) — Frame semantics theory
- [Dowty 1991 — Thematic Proto-Roles](references/) — Proto-Agent/Proto-Patient as entailment-based clusters
- [Bonial et al 2011 — Hierarchical Unification of LIRICS and VerbNet](https://verbs.colorado.edu/~mpalmer/Ling7800/SACL-ICSC2011.pdf) — The unification that became ISO 24617-4
- [Miller et al 1993 — Introduction to WordNet](references/Miller%20et%20al%201993%20-%20Introduction%20to%20WordNet.pdf) — The original five WordNet papers
- [Bond et al 2016 — CILI](references/Bond%2C%20Vossen%20et%20al%202016%20-%20CILI%20the%20Collaborative%20Interlingual%20Index.pdf) — Language-neutral concept identifiers
- [Bond, Foster 2013 — Open Multilingual Wordnet](references/Bond%2C%20Foster%202013%20-%20Linking%20and%20Extending%20an%20Open%20Multilingual%20Wordnet.pdf) — 26+ language wordnet linking
- [Vossen 1998 — EuroWordNet](references/Vossen%201998%20-%20EuroWordNet%20Multilingual%20Lexical%20Semantic%20Networks.pdf) — The inter-lingual index that preceded CILI
- [Ruppenhofer et al 2006 — FrameNet II](references/Ruppenhofer%20et%20al%202006%20-%20FrameNet%20II%20Extended%20Theory%20and%20Practice.pdf) — Comprehensive FrameNet theory
- [Pustejovsky 1991 — The Generative Lexicon](references/Pustejovsky%201991%20-%20The%20Generative%20Lexicon.pdf) — Compositional word meaning and polysemy
- [Navigli, Ponzetto 2010 — BabelNet](references/Navigli%2C%20Ponzetto%202010%20-%20BabelNet.pdf) — Multilingual semantic network construction
- [Youn et al 2016 — Universal Structure of Human Lexical Semantics](references/Youn%20et%20al%202016%20-%20Universal%20Structure%20of%20Human%20Lexical%20Semantics.pdf) — Empirical evidence for universal semantic structure
