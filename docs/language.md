# Language

This document describes the language system — how human languages, words, and meanings are represented, imported, and connected in Common Graph.

> For how sememes anchor meaning, see [Sememes](sememes.md). For the expression input and dispatch system, see [Vocabulary](vocabulary.md). For the frame primitive, see [Frames](frames.md).

## Overview

The language system solves a fundamental problem: human knowledge is expressed in words, but words are ambiguous, language-specific, and ephemeral. Common Graph separates **meaning** (sememes) from **expression** (lexemes) and connects them through **languages** — items that serve as living dictionaries.

```
Sememe (AUTHOR)            Language Item (English)
(language-agnostic         (language-specific
 meaning anchor)            word repository)
     |                          |
     |   +-- Lexeme frame --+  |
     |   |                  |  |
     +---| REFERENT: AUTHOR |  |
         | THEME: ENGLISH   |--+
         | FORM: LEMMA      |
         | word: "author"   |
         +------------------+
```

The core chain:

1. **Sememes** are meaning units — Items with stable IIDs, no words attached
2. **Languages** are Items that hold lexeme frames — one per word↔meaning mapping
3. **Lexemes** are frames on Language items — each one connects a word to a sememe with a grammatical form
4. **The TokenDictionary** indexes all of this for O(1) lookup at runtime

## Languages as Items

Every human language is an Item with a deterministic IID derived from its ISO 639-3 code:

```
Language Item: English
    iid:  ItemID.fromString("cg:language/eng")
    type: cg:type/language
    code: "eng"
```

ISO 639-3 covers ~7,000 languages — living, extinct, constructed, and signed. The resource file `iso-639-3.tsv` (bundled in `:core`) provides all codes. `SeedVocabulary.loadLanguageCodes()` reads it during bootstrap.

At bootstrap, only English is seeded as a full Language Item. Other languages are created during import when their WordNet data is processed. A language doesn't need a WordNet to exist — any Language Item can acquire lexemes through any means (manual entry, corpus import, translation tools).

### The Language Class

`Language` (in `core`) is the base class. It extends `Item` and provides:

- **Language code** — ISO 639-3 (3-letter)
- **Lexicon** — the collection of word↔meaning mappings
- **Morphology hooks** — abstract methods for inflection:
  - `regularInflection(lemma, pos, features)` — compute regular inflected form
  - `inflectionFeatures(pos)` — what feature sets this language distinguishes for a POS
  - `simplifyFeatures(rawFeatures)` — reduce Universal Morphology tags to CG features

`English` (in `:english`) is the concrete subclass. It implements English morphology rules: verb inflection (-ed, -ing, -s), noun pluralization (-s/-es/-ies), adjective comparison (-er/-est). Other languages will follow the same pattern — `Spanish extends Language`, `Japanese extends Language`, etc.

## Sememes: The Meaning Layer

A **sememe** is an Item that represents a specific, language-agnostic meaning. Sememes carry no words — they carry identity (IID), part of speech, and structural metadata (symbols, thematic roles, dimensional data).

> Full detail on sememes in [Sememes](sememes.md). This section covers the parts relevant to the language system.

### The Sememe Hierarchy

`Sememe` is the abstract base class. Every sememe has a **part of speech**, and each POS has its own subclass:

| Subclass | POS | Purpose |
|----------|-----|---------|
| `VerbSememe` | VERB | Dispatchable actions (create, move, exit) |
| `NounSememe` | NOUN | Entities, predicates, concepts (author, title, item) |
| `PrepositionSememe` | PREPOSITION | Thematic role carriers (to, from, with) |
| `PronounSememe` | PRONOUN | References and variables (it, this, any) |
| `AdjectiveSememe` | ADJECTIVE | Properties (recent, active) |
| `AdverbSememe` | ADVERB | Modifiers |
| `ConjunctionSememe` | CONJUNCTION | Connectors (and, or) |
| `InterjectionSememe` | INTERJECTION | Exclamations |

`NounSememe` is the primary extension point for domain-specific types that carry meaning beyond a plain noun:

| Extension | What It Adds |
|-----------|-------------|
| `ThematicRole` | Semantic role identity (AGENT, THEME, TARGET, ...) |
| `GrammaticalFeature` | Inflectional property identity (PAST, PLURAL, LEMMA, ...) |
| `Operator` | Symbol, precedence, associativity, evaluation |
| `Function` | Arity, category, evaluation |
| `Unit` | Dimension, conversion factors, symbol |
| `Dimension` | Base unit |

All of these inherit glosses, tokens, symbols, and dictionary registration from `Sememe`, making them discoverable through the same vocabulary pipeline as any other sememe.

### Seed Declaration Pattern

Sememe seeds use an **inner class pattern** with fluent configuration:

```java
public static class Author {
    public static final String KEY = "cg.core:author";
    @Seed public static final NounSememe SEED = new NounSememe(KEY)
            .gloss(ENG, "the creator or originator of a work")
            .word(LEMMA, ENG, "author")
            .cili("i90183")
            .slot(ThematicRole.Theme.SEED)
            .slot(ThematicRole.Target.SEED)
            .indexWeight(1000);
}
```

The inner class provides:
- `KEY` — a compile-time constant string, usable in annotations and switch statements
- `SEED` — the seed instance, annotated `@Seed` for `SeedVocabulary` discovery

The fluent methods on `Sememe`:

| Method | What It Does |
|--------|-------------|
| `.gloss(lang, text)` | Add a definition for a language (transient — migrates to `SememeGloss` components) |
| `.word(form, lang, surface)` | Declare a word form (transient — flows into Language Lexicon during bootstrap) |
| `.cili(id)` | Set the CILI identifier (stored in `sources` map) |
| `.symbol(s)` | Add a language-neutral symbol ("m", "+", "kg") |
| `.slot(role)` | Declare a thematic role this predicate expects |
| `.indexWeight(w)` | Set predicate text-indexing weight (1000 = 1.0) |

The `.word()` method creates a `LexemeDeclaration` — a transient record that captures enough to create a proper Lexeme during bootstrap:

```java
public record LexemeDeclaration(Sememe form, String lang, String surface) {}
```

During bootstrap, `SeedVocabulary` processes these declarations and feeds them into the appropriate Language's Lexicon. Words don't live on sememes — they live in languages.

### Covariant Return Types

Java doesn't have a `Self` return type. Each POS subclass provides covariant overrides so fluent chaining preserves the subclass type:

```java
// On NounSememe:
@Override public NounSememe gloss(String lang, String text) {
    super.gloss(lang, text); return this;
}
```

~6 one-liner overrides per subclass. This lets `new NounSememe(KEY).gloss(...).symbol(...)` return `NounSememe` all the way through, so subclass-specific methods (like `withArguments()` on `VerbSememe`) remain accessible.

## Lexemes as Frames

Each word↔meaning mapping is a **frame** on the Language item. The Language item IS the dictionary. This is the natural consequence of the frame unification — if everything is a frame, then lexemes are frames too.

### Frame Key Structure

A lexeme frame on a Language item has the key:

```
(LEXEME, <sememe>, <form>, "<word>")
```

Where:
- `LEXEME` — the predicate (a seed NounSememe: `Sememe.Lexeme.SEED`)
- `<sememe>` — the meaning being expressed (fills the REFERENT role)
- `<form>` — the grammatical form (LEMMA, PAST, PLURAL, etc.)
- `"<word>"` — the surface string (a literal key component)

**Example**: The English word "author" as a lemma of the AUTHOR sememe:

```
Language: English (cg:language/eng)
Frame key: (LEXEME, AUTHOR, LEMMA, "author")
    BODY:
        predicate: LEXEME
        theme: English (the home item)
        REFERENT: Author.SEED (the sememe)
        FORM: LEMMA
        word: "author"
    RECORD:
        FREQUENCY: 847        // from corpus data
        PROVENANCE: OEWN      // Open English WordNet
```

### Why Frames?

This design has several advantages:

1. **No special Lexeme type needed** — lexemes are just frames, using the same machinery as everything else
2. **O(1) lookup** — the frame key gives direct access by any combination of components (prefix scan by sememe, by form, by word)
3. **Qualifiers in the RECORD** — frequency, provenance, confidence are semantically keyed (by FREQUENCY, PROVENANCE sememes) but don't change frame identity
4. **Attestation** — multiple signers can independently attest to the same lexeme. Alice says "author" means AUTHOR; Bob agrees. Same body hash, two records.
5. **Forward lookup** — given a sememe, prefix-scan the Language item's frames with `(LEXEME, sememe, ...)` to find all words
6. **Reverse lookup** — the TokenDictionary indexes surface strings for O(1) word→sememe resolution

### Scale

For a full English import (~157K synsets, ~2.5 words per synset average):

```
~400K lexeme frames (base forms + irregular inflections)
~150 bytes per frame body (key + minimal bindings)
~62 MB total for the English Language item's lexeme frames
```

This is large but manageable. Frames aren't large objects — they're compact semantic statements. The Language item's frame table handles this scale the same way any other item handles many frames.

### Body vs Record

The **body** is the identity of the lexeme — the statement "in English, 'author' is the lemma for the AUTHOR sememe." This is what gets hashed. Two independent importers processing the same WordNet data will produce the same body hash.

The **record** carries metadata that doesn't change what the lexeme IS:

| Qualifier | Keyed By | Example |
|-----------|----------|---------|
| Frequency | `Sememe.Frequency.SEED` | 847 (corpus count) |
| Provenance | `Sememe.Provenance.SEED` | OEWN (data source identifier) |
| Confidence | (future) | 0.95 |

These are stored in the frame RECORD, keyed by sememes — not by arbitrary strings. The qualifiers are themselves meaningful, discoverable concepts.

## Thematic Roles

**Thematic roles** (theta roles) describe what part a participant plays in an event or relation. They descend from Fillmore's Case Grammar and frame semantics, computationally realized in FrameNet.

In Common Graph, roles are **sememes** — NounSememe subclass instances with deterministic IIDs. The class is `ThematicRole` (in `core/src/main/java/dev/everydaythings/graph/language/ThematicRole.java`).

### Seed Roles

| Role | Canonical Key | Meaning | Example |
|------|--------------|---------|---------|
| **AGENT** | `cg.role:agent` | The doer or initiator | Shakespeare (wrote Hamlet) |
| **PATIENT** | `cg.role:patient` | The entity affected or changed | Hamlet (was written) |
| **THEME** | `cg.role:theme` | The content, topic, or subject matter | "The Hobbit" (in TITLE frame) |
| **TARGET** | `cg.role:target` | The destination or goal | e4 (in "move to e4") |
| **SOURCE** | `cg.role:source` | The origin | archive (in "copy from archive") |
| **INSTRUMENT** | `cg.role:instrument` | The tool or means used | key (in "encrypt with key") |
| **LOCATION** | `cg.role:location` | Where something is or happens | London |
| **TIME** | `cg.role:time` | When something happens | Tuesday |
| **RECIPIENT** | `cg.role:recipient` | Who benefits or receives | Alice (in "share with Alice") |
| **CAUSE** | `cg.role:cause` | The reason or cause | outage (in "because of outage") |
| **COMITATIVE** | `cg.role:comitative` | A companion or co-participant | Bob (in "play with Bob") |
| **NAME** | `cg.role:name` | A designation being assigned | "Project Alpha" (in "name it") |
| **REFERENT** | `cg.role:referent` | The concept being referred to | AUTHOR sememe (in LEXEME frame) |

Additional roles planned for batch 2 (from FrameNet's full inventory):

| Role | Meaning |
|------|---------|
| EXPERIENCER | Who perceives or feels |
| STIMULUS | What triggers a perception |
| MANNER | How something is done |
| PURPOSE | Why something is done |
| EXTENT | Degree or amount of change |
| MEDIUM | Channel of communication |
| RESULT | What is produced |
| CONTENT | What is communicated |
| DURATION | How long something takes |
| POSSESSOR | Who owns something |
| MATERIAL | What something is made of |
| ATTRIBUTE | A property being described |
| VALUE | The value of an attribute |

### Roles in Verb Arguments

Verbs declare their **argument structure** as a list of `ArgumentSlot`s, each specifying a role, whether it's required, and a type constraint:

```java
@Seed
public static final VerbSememe create = new VerbSememe(CREATE, ...)
    .withArguments(
        ArgumentSlot.optional(ROLE_THEME, "what to create"),
        ArgumentSlot.optional(ROLE_TARGET, "where to place the result"),
        ArgumentSlot.optional(ROLE_NAME, "name for the new item")
    );
```

### Roles in Frames

Thematic roles are the keys in frame bindings. A frame is essentially a sentence:

```
AUTHOR { theme: TheHobbit, TARGET: Tolkien }
  → "The Hobbit was authored by Tolkien"
  → THEME fills "what was authored", TARGET fills "who authored it"
```

This is Frame Semantics — the structure of meaning. Roles give each participant a defined semantic function, independent of word order or language.

### The REFERENT Role

REFERENT is a metalinguistic role — "the concept being referred to." It's needed for frames that talk *about* sememes rather than using them:

```
LEXEME { theme: English, REFERENT: AUTHOR, FORM: LEMMA, word: "author" }
  → "In English, the lemma for the AUTHOR concept is 'author'"
```

Here THEME is English (the home item), so REFERENT carries the sememe. Without REFERENT, we'd need THEME for the sememe, but THEME is already the Language item.

## Grammatical Features

**Grammatical features** describe inflectional properties of words — tense, number, person, case, mood, degree. Like thematic roles, they are **sememes** (NounSememe subclass: `GrammaticalFeature`).

### Seed Features

| Feature | Canonical Key | What It Marks |
|---------|--------------|---------------|
| **LEMMA** | `cg.feat:lemma` | The base/dictionary form |
| **PAST** | `cg.feat:past` | Past tense (ran, went) |
| **PRESENT** | `cg.feat:present` | Present tense |
| **FUTURE** | `cg.feat:future` | Future tense |
| **SINGULAR** | `cg.feat:singular` | Singular number |
| **PLURAL** | `cg.feat:plural` | Plural number |
| **FIRST_PERSON** | `cg.feat:first-person` | First person |
| **SECOND_PERSON** | `cg.feat:second-person` | Second person |
| **THIRD_PERSON** | `cg.feat:third-person` | Third person |
| **PARTICIPLE** | `cg.feat:participle` | Participial form |
| **GERUND** | `cg.feat:gerund` | Gerund form |
| **COMPARATIVE** | `cg.feat:comparative` | Comparative degree (bigger) |
| **SUPERLATIVE** | `cg.feat:superlative` | Superlative degree (biggest) |
| **INDICATIVE** | `cg.feat:indicative` | Indicative mood |
| **SUBJUNCTIVE** | `cg.feat:subjunctive` | Subjunctive mood |
| **IMPERATIVE** | `cg.feat:imperative` | Imperative mood |

Features combine as sets. The past participle of "run" has features `{PAST, PARTICIPLE}` → "run". The third person singular present has `{THIRD_PERSON, SINGULAR, PRESENT}` → "runs".

### Irregular Form Overrides

Lexemes can carry **form entries** — explicit overrides for irregular inflections:

```
Lexeme: "run" → RUN sememe
  Forms:
    {PAST}                → "ran"
    {PAST, PARTICIPLE}    → "run"
    {PRESENT, PARTICIPLE} → "running"
```

When looking up a form, the system checks overrides first, then falls back to regular morphology rules. `FormEntry` stores each override as a sorted list of feature IIDs + the surface string, enabling deterministic encoding.

## Morphology

The morphology system generates all inflected forms of a word. It combines **regular rules** (language-specific algorithms) with **irregular overrides** (from UniMorph data).

### Regular Rules

Each Language subclass implements `regularInflection(lemma, pos, features)`. For English:

```
Verb inflection:
  {PAST}              → -ed (walk → walked)
  {PRESENT, PARTICIPLE} → -ing (walk → walking)
  {THIRD_PERSON}      → -s (walk → walks)

Noun inflection:
  {PLURAL}            → -s (cat → cats)
                         -es (box → boxes)
                         -ies (city → cities)

Adjective inflection:
  {COMPARATIVE}       → -er (big → bigger)
  {SUPERLATIVE}       → -est (big → biggest)
```

### Irregular Overrides from UniMorph

The [Universal Morphology](https://unimorph.github.io/) project provides irregularity data for 100+ languages. Each language's UniMorph file is a TSV with:

```
lemma    form    features
run      ran     V;PST
run      running V;V.PTCP;PRS
go       went    V;PST
be       am      V;IND;PRS;1;SG
```

During import, `UniMorphReader` parses these files and maps universal tags (V, PST, PL, CMPR) to `GrammaticalFeature` IIDs. The Language's `simplifyFeatures()` reduces the tag set to what that language actually distinguishes. If the irregular form differs from what regular rules would produce, it's stored as a `FormEntry` on the Lexeme.

### Inflected Form Registration

After import, `Lexicon.registerInflectedForms(language)` generates all surface forms:

```
For each lexeme:
  For each feature set the language distinguishes:
    1. Check lexeme overrides → use if present
    2. Otherwise compute regular inflection → use if differs from lemma
    3. Index the surface form as a TokenDictionary posting
       (scoped to the Language item, at the lexeme's frequency weight)
```

This means all of these resolve to the same RUN sememe:
```
"run"     → LEMMA, scope: English
"ran"     → {PAST}, scope: English
"running" → {PRESENT, PARTICIPLE}, scope: English
"runs"    → {THIRD_PERSON}, scope: English
```

## Data Sources

The import pipeline draws from four external data sources:

### 1. ISO 639-3 Language Codes

**File**: `core/src/main/resources/iso-639-3.tsv` (114 KB, ~8,000 entries)
**Format**: Tab-separated — `code \t englishName`
**Loaded by**: `SeedVocabulary.loadLanguageCodes()`
**Purpose**: Create Language items with deterministic IIDs for every known human language

This is the **enumeration of all possible languages**. Even languages without WordNet data get an Item, so lexemes can be added later from any source.

### 2. Global WordNet LMF (Lemon-Based Multilingual Format)

**File**: e.g., `english/src/main/resources/english-wordnet-2025.xml`
**Format**: XML (GWN-LMF standard)
**Parsed by**: `LmfImporter` (StAX streaming, handles gzip)

The primary vocabulary source. Each language with a WordNet provides:

- **Synsets** — synonym sets with glosses and inter-synset relations
- **Lexical entries** — word forms with lemmas, senses, and counts
- **Sense relations** — word-level semantic links

The Open English WordNet (OEWN) 2025 contains ~120K synsets and ~160K lexical entries.

### 3. CILI (Collaborative Interlingual Index)

**File**: `core/src/main/resources/ili.ttl` (16.3 MB)
**Format**: RDF Turtle
**Purpose**: Global concept identifiers that link synsets across languages

CILI provides the bridge. English synset "dog" has CILI identifier `i23456`. Spanish synset "perro" has the same CILI identifier. When both wordnets are imported, both languages' words map to the same sememe — the one with IID derived from `i23456`.

This is how the **translation matrix** emerges without any translation machinery. Import English → English words map to sememes. Import Spanish → Spanish words map to the *same* sememes (via shared CILI IDs). Query any sememe → get words in all imported languages.

### 4. UniMorph (Universal Morphology)

**File**: e.g., `english/src/main/resources/unimorph/eng`
**Format**: TSV — `lemma \t form \t features`
**Parsed by**: `UniMorphReader`
**Purpose**: Irregular inflection data

UniMorph provides morphological paradigms for 100+ languages. The importer uses it to:
1. Identify irregular forms (run→ran, go→went, be→am/is/are/was/were)
2. Store overrides as `FormEntry` objects on lexemes
3. Avoid generating incorrect regular inflections

## The Import Pipeline

The import pipeline is a **3-pass process** implemented in `LanguageImporter`, with language-specific subclasses providing paths and configuration.

### Architecture

```
LanguageImporter (abstract, in :core)
    ├── wordnetResourcePath()    → classpath path to GWN-LMF XML
    ├── unimorphResourcePath()   → classpath path to UniMorph TSV (or null)
    ├── languageId()             → ItemID for the Language item
    └── sourcePrefix()           → identifier for this wordnet (e.g., "oewn")

EnglishImporter (in :english)
    ├── wordnetResourcePath()    → "/english-wordnet-2025.xml"
    ├── unimorphResourcePath()   → "/unimorph/eng"
    ├── languageId()             → ItemID.fromString("cg.lang:english")
    └── sourcePrefix()           → "oewn"
```

Each language module (`:english`, `:spanish`, etc.) provides an importer subclass and bundles its data files as classpath resources.

### Pass 1: Synsets → Sememes

```
LmfImporter.synsets()  →  stream of Synset records
    |
    For each synset:
    |
    ├── Resolve ILI identifier → look up existing sememe
    │   (if CILI maps to a seed sememe, reuse it; otherwise create new)
    |
    ├── Create or find Sememe item
    │   - Deterministic IID from CILI/ILI
    │   - POS from synset (n → NOUN, v → VERB, a/s → ADJECTIVE, r → ADVERB)
    │   - Gloss from synset definition
    |
    └── Create semantic relations
        - hypernym, hyponym, holonym, meronym, antonym, similar_to, etc.
        - Each maps to a CG predicate sememe (Sememe.Hypernym, Sememe.Hyponym, ...)
        - Stored as signed relations: THEME → subject sememe, TARGET → object sememe
```

**Relation type mapping** (universal across all wordnets):

| LMF Relation | CG Predicate |
|-------------|-------------|
| `hypernym` | `Sememe.Hypernym` |
| `hyponym` | `Sememe.Hyponym` |
| `instance_hypernym` | `Sememe.InstanceOf` |
| `holo_*` | `Sememe.Holonym` |
| `mero_*` | `Sememe.Meronym` |
| `antonym` | `Sememe.Antonym` |
| `similar` | `Sememe.SimilarTo` |
| `derivation` | `Sememe.Derivation` |
| `domain_*` | `Sememe.Domain` |
| `entails` | `Sememe.Entails` |
| `causes` | `Sememe.Causes` |
| `also` | `Sememe.SeeAlso` |

### Pass 2: Lexical Entries → Lexemes

```
LmfImporter.lexicalEntries()  →  stream of LexicalEntry records
    |
    For each entry:
    |
    ├── Look up lemma's synset → find the Sememe from Pass 1
    |
    ├── Find irregular overrides (if UniMorph data available)
    │   ├── UniMorphReader.load(path) → Map<lemma, List<Entry>>
    │   ├── For each UniMorph entry matching this lemma:
    │   │   ├── Parse universal features (V;PST → {VERB, PAST})
    │   │   ├── language.simplifyFeatures(raw) → minimal feature set
    │   │   ├── language.regularInflection(lemma, pos, features) → regular form
    │   │   └── If UniMorph form ≠ regular form → create FormEntry override
    │   └── Collect all overrides into forms list
    |
    └── Create Lexeme(word, language, sememe, pos, frequency, forms)
        └── lexicon.add(lexeme)  → auto-indexes in TokenDictionary
```

### Pass 3: Inflected Form Registration

```
lexicon.registerInflectedForms(language)
    |
    For each lexeme in lexicon:
    |
    For each feature set the language distinguishes for this POS:
    |
    ├── Check lexeme.lookupForm(features)  → override if present
    ├── Otherwise language.regularInflection(lemma, pos, features)
    ├── If the inflected form differs from the base word:
    │   └── Create Posting(form, languageScope, sememeTarget, frequency)
    │       └── tokenDictionary.index(posting)
    |
    Result: all surface forms indexed
        "run", "ran", "running", "runs" → RUN sememe (English-scoped)
```

### Import Statistics

`importInto()` returns `ImportStats`:

```
ImportStats {
    synsetCount:      int     // sememes processed
    lexemeCount:      int     // lexemes created
    relationCount:    int     // semantic relations created
    formPostingCount: int     // inflected form postings indexed
    durationMs:       long    // wall clock time
    source:           String  // data source identifier
}
```

## Bootstrap Flow

At system startup, `SeedVocabulary.bootstrap()` orchestrates the initialization:

```
1. Seed English Language item
   └── Create Language("cg:language/eng", "eng")
   └── Store in ItemStore
   └── Required before any English-scoped tokens can be registered

2. Register all @Type classes
   └── Scan classpath for @Type annotation
   └── Create type seed items with deterministic IIDs
   └── Create IMPLEMENTED_BY relations (type → Java class)

3. Scan for @Item.Seed fields
   └── Find all static fields annotated @Seed
   └── For each seed:
       ├── Store manifest in ItemStore
       ├── Extract tokens → TokenDictionary (English-scoped)
       ├── Extract symbols → TokenDictionary (universal scope)
       ├── Glosses → transient (migrate to SememeGloss components later)
       └── LexemeDeclarations → feed into English Lexicon

4. Load ISO 639-3 language codes
   └── Create Language items for all ~7,000 languages

5. Language imports (triggered by user or on first boot)
   └── EnglishImporter.importInto(lexicon, english, signer)
   └── (Future: SpanishImporter, JapaneseImporter, etc.)
```

### Token Indexing During Bootstrap

`TokenExtractor.fromSememe(sememe, language)` extracts postings from seed sememes:

| Source | Scope | Weight | Example |
|--------|-------|--------|---------|
| Canonical key | English | 1.0 | "cg.core:author" → AUTHOR |
| Short name | English | 1.0 | "author" → AUTHOR |
| Symbols | null (universal) | 1.0 | "m" → METER, "+" → ADD |
| Token aliases | English | 1.0 | "create", "new", "make" → CREATE |
| Glosses | English | 0.5 | "the creator or originator..." → AUTHOR |

## The Translation Matrix

One of Common Graph's most powerful emergent properties is the **translation matrix** — a natural consequence of the sememe/lexeme architecture.

```
English Lexicon                  Sememe                  Spanish Lexicon
"author" ──LEMMA──→ ┌─────────────────────┐ ←──LEMMA── "autor"
"authors" ─PLURAL─→ │  AUTHOR (cg.core:   │ ←──PLURAL─ "autores"
                     │    author)           │
                     │  IID: 7f3a...        │
                     │  CILI: i90183        │
                     └─────────────────────┘
```

There is no separate translation machinery. The sememe IS the translation. To translate "author" from English to Spanish:

1. Look up "author" in English scope → resolves to AUTHOR sememe
2. Look up AUTHOR sememe in Spanish scope → finds "autor"

This works for every word in every imported language. Import English and Spanish WordNets (both linked via CILI) and you have a bidirectional English↔Spanish dictionary covering ~120K concepts. Import Japanese too and you now have English↔Japanese and Spanish↔Japanese as well — for free.

The translation matrix is not a feature that was built. It's a structural consequence of separating meaning from expression and anchoring both to shared identifiers.

## References

**Internal:**
- [Sememes](sememes.md) — Meaning units, hierarchies, CILI anchoring, symbols
- [Vocabulary](vocabulary.md) — Dispatch, expression input, token resolution, customization
- [Frames](frames.md) — The frame primitive, body/record split, frame keys

**External resources:**
- [WordNet](https://wordnet.princeton.edu/) — Lexical database of English
- [Open English WordNet](https://en-word.net/) — Community-maintained English WordNet
- [CILI](https://github.com/globalwordnet/cili) — Collaborative Interlingual Index
- [Open Multilingual Wordnet](http://compling.hss.ntu.edu.sg/omw/) — WordNet extensions for 26+ languages
- [Global WordNet LMF](https://globalwordnet.github.io/schemas/) — The XML interchange format
- [UniMorph](https://unimorph.github.io/) — Universal Morphology for 100+ languages
- [ISO 639-3](https://iso639-3.sil.org/) — Language code standard (~7,000 languages)

**Academic foundations:**
- [Miller et al 1993 — Introduction to WordNet](references/Miller%20et%20al%201993%20-%20Introduction%20to%20WordNet.pdf) — The lexical database
- [Bond et al 2016 — CILI](references/Bond%2C%20Vossen%20et%20al%202016%20-%20CILI%20the%20Collaborative%20Interlingual%20Index.pdf) — Cross-lingual concept identifiers
- [Fillmore 1982 — Frame Semantics](references/Fillmore%201982%20-%20Frame%20Semantics.pdf) — Thematic roles and frame semantics
- [Ruppenhofer et al 2006 — FrameNet II](references/Ruppenhofer%20et%20al%202006%20-%20FrameNet%20II%20Extended%20Theory%20and%20Practice.pdf) — Comprehensive FrameNet theory
- [Pustejovsky 1991 — The Generative Lexicon](references/Pustejovsky%201991%20-%20The%20Generative%20Lexicon.pdf) — Compositional word meaning
- [Youn et al 2016 — Universal Structure of Human Lexical Semantics](references/Youn%20et%20al%202016%20-%20Universal%20Structure%20of%20Human%20Lexical%20Semantics.pdf) — Empirical evidence for universal semantic structure
