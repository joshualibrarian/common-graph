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

```
Sememe {
    iid:            ItemID          # Stable identity
    partOfSpeech:   PartOfSpeech    # VERB, NOUN, ADJECTIVE, etc.
    thematicRoles:  [Role]          # For verbs: argument structure
    domain:         [ItemID]        # For predicates: valid subject types
    range:          [ItemID]        # For predicates: valid target types
    symbols:        [string]        # Language-neutral symbols ("m", "kg", "+")
    dimension:      Dimension?      # For units: physical dimension
    conversions:    [Conversion]    # For units: conversion factors
}
```

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

## Parts of Speech

Every sememe has a **part of speech** — its grammatical category. This is metadata on the sememe itself, aligned with WordNet's linguistic classification.

| Part of Speech | Role in Common Graph | Examples |
|----------------|---------------------|----------|
| **Verb** | Dispatchable action | create, move, edit, exit, search |
| **Noun** | Type reference, navigation target, argument | item, document, log, roster |
| **Adjective** | Modifier, query filter | recent, unread, active, public |
| **Adverb** | Verb modifier | recursively, quietly, forcefully |
| **Preposition** | Thematic role filler, structures expressions | to, from, with, in, for |
| **Conjunction** | Coordinates clauses and expressions | and, or, then |

The part of speech tells the expression parser what role each token plays in an expression. See [Vocabulary](vocabulary.md) for how the parser uses this information.

### Verbs

Verb sememes define:
- What the action means (via glosses in each language's lexicon)
- What arguments it takes (thematic roles: THEME, TARGET, SOURCE, etc.)

```
create (verb sememe) {
    iid: cg.verb:create
    partOfSpeech: VERB
    thematicRoles: [
        { role: THEME, doc: "what to create" }
    ]
}
```

The words "create" (English), "crear" (Spanish), etc. live in their respective language lexicons, not here.

Items respond to verb sememes by registering VerbEntries in their vocabulary. See [Vocabulary](vocabulary.md) for the dispatch mechanism.

### Nouns

Noun sememes represent things and concepts. Type definitions are noun sememes:

```
document (noun sememe) {
    iid: cg:type/document
    partOfSpeech: NOUN
    broader: [artifact]
}
```

In expressions, nouns serve as arguments to verbs ("create **document**"), navigation targets ("open **notes**"), and query subjects ("search **documents**").

### Units Are Nouns

Units of measurement are **noun sememes** with dimensional metadata and language-neutral symbols. They are units of meaning that happen to describe measurement — which is exactly the project's thesis.

```
meter (noun sememe) {
    iid: cg:unit/meter
    partOfSpeech: NOUN
    symbols: ["m"]
    broader: [lengthUnit]
    dimension: LENGTH
    conversions: [
        { to: foot, factor: 3.28084 },
        { to: centimeter, factor: 100 }
    ]
}
```

The symbol "m" is on the sememe because it's universal. The words "meter" (English), "metre" (British English, French), "metro" (Spanish) live in their language lexicons.

In the expression parser, when a numeral precedes a unit noun, they combine into a **Quantity**:

```
"5 meters"    --> Quantity(5, meter-sememe)
"3 kg"        --> Quantity(3, kilogram-sememe)
"72.5 F"      --> Quantity(72.5, fahrenheit-sememe)
```

The parser recognizes the pattern because the noun-sememe carries dimensional metadata. The same resolution mechanism that handles "create document" handles "5 meters": token to sememe, then the sememe's metadata drives interpretation.

### Prepositions

Preposition sememes structure expressions by filling thematic roles:

```
to (preposition sememe) {
    iid: cg:preposition/to
    partOfSpeech: PREPOSITION
    impliedRole: TARGET
}
```

When the expression parser encounters `[verb] [noun] [preposition] [noun]`, the preposition determines which thematic role the following noun fills:

```
"move pawn to e4"
    --> move(THEME=pawn, TARGET=e4)    # "to" implies TARGET role

"copy document from archive"
    --> copy(THEME=document, SOURCE=archive)    # "from" implies SOURCE role
```

### Modifiers

Adjective and adverb sememes qualify other sememes:

```
recent (adjective sememe) {
    iid: cg:adjective/recent
    partOfSpeech: ADJECTIVE
}
```

In queries: "search recent documents" — "recent" narrows the results. In future expression parsing, modifiers attach to the nearest compatible noun or verb.

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
hypertension (noun sememe) {
    iid: med:concept/hypertension
    partOfSpeech: NOUN
    broader: [medicalCondition]
}

# English lexicon adds:
hypertension --> ["hypertension", "high blood pressure"]
```

### Project-Specific Sememes

Organizations can define their own concepts:

```
sprintReview (noun sememe) {
    iid: org:concept/sprintReview
    partOfSpeech: NOUN
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

Verb sememes declare **thematic roles** — the semantic slots that their arguments fill. These roles descend from Fillmore's case grammar and frame semantics (see [references/Fillmore 1982](references/Fillmore%201982%20-%20Frame%20Semantics.pdf)), computationally realized in FrameNet's frame elements (see [references/Ruppenhofer et al 2006](references/Ruppenhofer%20et%20al%202006%20-%20FrameNet%20II%20Extended%20Theory%20and%20Practice.pdf)). Gildea and Jurafsky (2002) showed that automatic semantic role labeling is feasible using FrameNet data (see [references/Gildea, Jurafsky 2002](references/Gildea%2C%20Jurafsky%202002%20-%20Automatic%20Labeling%20of%20Semantic%20Roles.pdf)) — Common Graph takes the deterministic path, using curated roles rather than statistical ones. The roles are themselves sememes:

| Role | Meaning | Example |
|------|---------|---------|
| **THEME** | The thing acted upon | "delete **this item**" |
| **TARGET** | The destination or goal | "move to **e4**" |
| **SOURCE** | The origin | "copy from **archive**" |
| **INSTRUMENT** | The tool or means | "encrypt with **this key**" |
| **BENEFICIARY** | Who benefits | "share with **Alice**" |
| **AGENT** | Who performs the action | (usually the caller, implicit) |
| **PATIENT** | Who is affected | "notify **Bob**" |
| **EXPERIENCER** | Who perceives | "show **Alice**" |

Thematic roles enable:
- Order-independent expression parsing (see [Vocabulary](vocabulary.md))
- Automatic generation of help text ("create WHAT?")
- Semantic validation (does this argument fit this role?)

## Constraints on Predicates

Sememe predicates can declare domain and range constraints:

```
author (predicate sememe) {
    iid: cg:predicate/author
    partOfSpeech: VERB
    domain: [writtenWork]       # Subject must be a written work
    range: [person]             # Object must be a person
}
```

The runtime can validate relations against these constraints.

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
| **Verbs** | create, edit, commit, delete, describe, open, search, exit |
| **Types** | item, signer, host, sememe, document, log, roster |
| **Predicates** | title, author, created, modified, describes |
| **Trust** | trusts, disavows, endorses, vouches |
| **Structure** | contains, partOf, references, replaces |
| **Time** | before, after, during, overlaps |
| **Space** | locatedAt, near, inside |
| **Roles** | hypernym, hyponym, instanceOf, holonym, meronym, antonym |
| **Prepositions** | to, from, with, in, for, as |
| **Dimensions** | length, mass, time, temperature, currency |
| **Units** | meter, kilogram, second, kelvin, dollar, euro |
| **Modifiers** | all, recent, unread, active |

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
- [Frame Types](components.md) — EntryVocabulary and frame vocabulary contributions

**External resources:**
- [WordNet](https://wordnet.princeton.edu/) — Lexical database of English
- [CILI (Collaborative Interlingual Index)](https://github.com/globalwordnet/cili) — Cross-lingual concept mapping
- [Open Multilingual Wordnet](http://compling.hss.ntu.edu.sg/omw/) — WordNet extensions for many languages
- [ISO 639-3](https://iso639-3.sil.org/) — Language code standard (~7,000 languages)

**Academic foundations:**
- [Miller et al 1993 — Introduction to WordNet](references/Miller%20et%20al%201993%20-%20Introduction%20to%20WordNet.pdf) — The original five WordNet papers
- [Bond et al 2016 — CILI](references/Bond%2C%20Vossen%20et%20al%202016%20-%20CILI%20the%20Collaborative%20Interlingual%20Index.pdf) — Language-neutral concept identifiers
- [Bond, Foster 2013 — Open Multilingual Wordnet](references/Bond%2C%20Foster%202013%20-%20Linking%20and%20Extending%20an%20Open%20Multilingual%20Wordnet.pdf) — 26+ language wordnet linking
- [Vossen 1998 — EuroWordNet](references/Vossen%201998%20-%20EuroWordNet%20Multilingual%20Lexical%20Semantic%20Networks.pdf) — The inter-lingual index that preceded CILI
- [Youn et al 2016 — Universal Structure of Human Lexical Semantics](references/Youn%20et%20al%202016%20-%20Universal%20Structure%20of%20Human%20Lexical%20Semantics.pdf) — Empirical evidence for universal semantic structure
- [Fillmore 1982 — Frame Semantics](references/Fillmore%201982%20-%20Frame%20Semantics.pdf) — Thematic roles and frame semantics
- [Ruppenhofer et al 2006 — FrameNet II](references/Ruppenhofer%20et%20al%202006%20-%20FrameNet%20II%20Extended%20Theory%20and%20Practice.pdf) — Comprehensive FrameNet theory
- [Pustejovsky 1991 — The Generative Lexicon](references/Pustejovsky%201991%20-%20The%20Generative%20Lexicon.pdf) — Compositional word meaning and polysemy
- [Navigli, Ponzetto 2010 — BabelNet](references/Navigli%2C%20Ponzetto%202010%20-%20BabelNet.pdf) — Multilingual semantic network construction
