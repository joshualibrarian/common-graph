# Frame Unification Roadmap

The central insight: **relations, verb dispatch, and queries are the same thing** — a frame
filled to different degrees, used in different modes.

- **Fully filled + code** → dispatch (verb execution)
- **Fully filled + no code** → assert (store as signed relation)
- **Partially filled** → query (search for completions)

One structure. Three modes determined by context.

## Current State

- **Relations**: `Relation(predicate, bindings)` with Role sememes as keys, content-addressed storage by RECORD CID, two fan-out indexes (REL_BY_ITEM, REL_BY_PRED). Frame-based model fully implemented.
- **Grammatical features**: `GrammaticalFeature.java` seeds all feature sememes (tense, aspect, mood, voice, person, number, case, politeness, evidentiality, form type). Registered in SeedVocabulary.
- **Lexemes**: `Lexeme` stores `forms: List<FormEntry>`, each with `Set<ItemID>` feature tags mapped to a surface form string. Irregular form overrides supported.
- **English morphology**: `English.java` provides `regularInflection()`, `addS()`, `addEd()`, `addIng()`, `addEr()`, `addEst()`, `shouldDoubleConsonant()`, etc.
- **UniMorph import**: `UniMorphReader` (core, universal TSV parser) + `EnglishImporter` loads `/unimorph/eng`, simplifies features via `Language.simplifyFeatures()`, detects irregular overrides by comparing UniMorph forms with regular algorithm output.
- **Verb dispatch**: `@Verb` on component methods, `@Param(role=...)` for arguments
- **Queries**: `PatternExpression(S, P, O)` and `QueryComponent` — triple patterns only
- **Sememes**: Language-agnostic meaning units, seeded from WordNet/CILI
- **TokenDictionary**: Maps tokens → sememes, with inflected forms registered
- **Expression parser**: Pratt parser for math/expressions, FrameAssembler for verbs

## Phase 1: Frame-Based Relations -- DONE

**Goal**: Replace `Relation(S, P, O, qualifiers)` with `Relation(predicate, roles)`.

**Status**: Completed. The relation model is now `Relation(predicate, bindings)` with Role sememes as binding keys, content-addressed storage by RECORD CID, and two fan-out indexes (REL_BY_ITEM, REL_BY_PRED).

### 1.1 Frame Schema on Predicates

Predicate sememes declare their role slots:

```
WROTE {
    agent:   required, type=Person    (who wrote)
    patient: required, type=Work      (what was written)
    time:    optional, type=Instant   (when)
    place:   optional, type=Location  (where)
}
```

- New component or field on Sememe: `FrameSchema` — list of `RoleSlot(roleSememe, required, typeConstraint)`
- Seed predicates get schemas (WROTE, CREATED, FOLLOWS, TAGGED, TITLED, etc.)
- `@Param(role=...)` on verb methods references roles from the predicate's schema
- Role sememes are themselves seeded: AGENT, PATIENT, THEME, INSTRUMENT, LOCATION, TIME, SOURCE, DESTINATION, BENEFICIARY, etc.

### 1.2 Relation Data Model

```java
Relation {
    predicate: ItemID                    // Sememe defining frame type
    bindings: Map<ItemID, Target>        // RoleSememe → value (IidTarget or Literal)
    createdAt: Instant
    rid: RelationID                      // Content-addressed by RECORD CID
    signing: Signing
}
```

- No more `subject`, `object`, `qualifiers` fields
- Agent and patient are just conventional role names for the two most common roles
- Qualifiers become regular roles (time, place, confidence, etc.)
- Content-addressed by RECORD CID with two fan-out indexes: REL_BY_ITEM and REL_BY_PRED

### 1.3 Migration

- `Relation(S, P, O)` → `Relation(P, {AGENT: S, PATIENT: O})`
- Qualifier entries → additional role bindings
- RelationQuery fluent API updated: `.from(x)` → `.role(AGENT, x)`, `.to(y)` → `.role(PATIENT, y)`
- Convenience: `.from()` and `.to()` remain as aliases for agent/patient

### 1.4 Index Redesign

Current indexes:
- **REL_BY_ITEM**: `ITEM → [RID...]` — "everything involving this item in any role"
- **REL_BY_PRED**: `PRED → [RID...]` — "all relations with this predicate"

### 1.5 Files Changed

- `Relation.java` — new data model with predicate + bindings
- `RelationTable.java` — updated storage
- `RelationEntry.java` — updated entry type
- `LibraryIndex.java` — REL_BY_ITEM and REL_BY_PRED column families
- `RelationQuery.java` — role-based fluent API
- `PatternExpression.java` — frame pattern matching
- `QueryComponent.java` — stored frame queries
- `Sememe.java` — add FrameSchema

## Phase 2: Grammatical Feature Sememes -- DONE

**Goal**: Seed sememes for grammatical features so lexeme forms can reference them.

**Status**: Completed. `GrammaticalFeature.java` contains all seed feature sememes, registered in SeedVocabulary.

### 2.1 Feature Categories

Not all universal — each language uses a subset:

**Tense**: PAST, PRESENT, FUTURE, NONPAST
**Aspect**: PERFECTIVE, IMPERFECTIVE, PROGRESSIVE, PERFECT
**Mood**: INDICATIVE, IMPERATIVE, SUBJUNCTIVE, CONDITIONAL
**Voice**: ACTIVE, PASSIVE
**Person**: FIRST_PERSON, SECOND_PERSON, THIRD_PERSON
**Number**: SINGULAR, PLURAL, DUAL
**Case**: NOMINATIVE, ACCUSATIVE, GENITIVE, DATIVE, etc.
**Politeness**: PLAIN, POLITE, HONORIFIC (Japanese, Korean)
**Evidentiality**: DIRECT, REPORTED, INFERRED (Quechua, Turkish)
**Form type**: BASE, INFINITIVE, PARTICIPLE, GERUND

### 2.2 Seeding

- Seed sememes created under `cg:grammar/` namespace
- Each is a NounSememe (they're concepts)
- UniMorph feature tags mapped to these sememes for import
- Languages reference whichever features they use

### 2.3 Files Created/Changed

- `GrammaticalFeature.java` — all seed grammatical feature sememes
- `SeedVocabulary` — registers grammar feature seeds
- Mapping table: UniMorph tag → CG grammatical feature sememe (in GrammaticalFeature)

## Phase 3: Lexeme Data Model -- DONE

**Goal**: Lexemes store all inflected forms, each tagged with grammatical features.

**Status**: Completed. Lexeme stores `forms: List<FormEntry>`, each mapping a `Set<ItemID>` of grammatical feature IDs to a surface form string. Irregular form overrides supported.

### 3.1 Form Table

```java
Lexeme {
    sememe: ItemID                              // The meaning
    language: ItemID                            // Which language
    pos: PartOfSpeech
    forms: List<FormEntry>                      // Each: Set<ItemID> features → surface form
    // Irregular form overrides supported
}
```

Example for English WRITE:
```
{BASE}                              → "write"
{THIRD_PERSON, SINGULAR, PRESENT}   → "writes"
{PAST}                              → "wrote"
{PAST_PARTICIPLE}                   → "written"
{PRESENT_PARTICIPLE}                → "writing"
```

### 3.2 TokenDictionary Registration

Every form gets its own posting with feature metadata:
- "wrote" → (sememe: WRITE, features: {PAST})
- "written" → (sememe: WRITE, features: {PAST_PARTICIPLE})
- "writing" → (sememe: WRITE, features: {PRESENT_PARTICIPLE})

Token resolution returns the sememe + grammatical context.

### 3.3 Files Changed

- `Lexeme.java` — redesigned with `List<FormEntry>` form table
- `TokenDictionary` — postings carry feature metadata
- `Posting.java` (or equivalent) — grammatical features field added

## Phase 4: English Morphology Engine -- DONE

**Goal**: Generate regular inflected forms automatically from base + POS.

**Status**: Completed. `English.java` implements `regularInflection()` plus individual rule methods: `addS()`, `addEd()`, `addIng()`, `addEr()`, `addEst()`, `shouldDoubleConsonant()`, etc.

### 4.1 Regular Rules

**Verbs**:
- 3rd person singular: +s (play→plays), +es after sibilants (watch→watches), y→ies (carry→carries)
- Past tense: +ed (play→played), e→ed (create→created), y→ied (carry→carried), double consonant (stop→stopped)
- Present participle: +ing (play→playing), drop e (create→creating), double consonant (stop→stopping)
- Past participle: same as past tense for regular verbs

**Nouns**:
- Plural: +s, +es after sibilants, y→ies, f→ves (some)

**Adjectives**:
- Comparative: +er or "more X"
- Superlative: +est or "most X"

### 4.2 Implementation

- `English.java` with `regularInflection()` and individual methods (`addS()`, `addEd()`, `addIng()`, `addEr()`, `addEst()`, `shouldDoubleConsonant()`)
- Input: (lemma, POS, inflectionClass)
- Output: generated forms
- Regular verbs auto-generated; irregulars stored explicitly via UniMorph import
- Called during seed vocabulary population and language import

### 4.3 Files Created

- `English.java` in english module — morphology engine
- Integration with `SeedVocabulary` for seed lexemes
- Integration with `EnglishImporter` for imported lexemes

## Phase 5: UniMorph Import -- DONE

**Goal**: Import irregular forms and complete paradigms from UniMorph data.

**Status**: Completed. `UniMorphReader` in core provides a universal TSV parser. `EnglishImporter` loads `/unimorph/eng`, simplifies features via `Language.simplifyFeatures()`, and detects irregular overrides by comparing UniMorph forms against the regular morphology algorithm output.

### 5.1 Data Source

UniMorph English data: tab-separated (lemma, form, features)
```
write   writes    V;3;SG;PRS
write   wrote     V;PST
write   written   V;V.PTCP;PST
write   writing   V;V.PTCP;PRS
```

### 5.2 Import Pipeline

1. `UniMorphReader` parses UniMorph TSV (universal, works for any language)
2. `Language.simplifyFeatures()` maps UniMorph feature tags to CG grammatical feature sememes
3. Match lemma to existing sememe (via WordNet lemma → sememe mapping)
4. Create/update Lexeme with form entries
5. Detect irregular overrides by comparing UniMorph forms with `English.regularInflection()` output
6. Post all forms to TokenDictionary

### 5.3 Feature Tag Mapping

UniMorph uses a standardized schema (UniMorph Schema):
- `PST` → PAST
- `PRS` → PRESENT
- `V.PTCP` → PARTICIPLE
- `3` → THIRD_PERSON
- `SG` → SINGULAR
- `PL` → PLURAL
- etc.

### 5.4 Files Created

- `UniMorphReader.java` in core — universal TSV parser for any language's UniMorph data
- `EnglishImporter.java` in english module — loads `/unimorph/eng`, drives import pipeline
- `Language.simplifyFeatures()` — feature tag mapping and simplification
- UniMorph data file bundled as resource

## Phase 6: Unified Frame Pipeline -- IN PROGRESS

**Goal**: Input → frame assembly → dispatch/assert/query, driven by grammatical context.

**Status**: In progress. FrameAssembler exists but needs updating for the new Relation model (predicate + bindings).

### 6.1 Frame Assembly

The expression input pipeline builds a frame progressively:

1. User types tokens
2. Each token resolves to (sememe, grammatical features)
3. The predicate token identifies the frame schema
4. Subsequent tokens fill roles based on:
   - Preposition mapping (by→AGENT, in→LOCATION/TIME, from→SOURCE)
   - Type compatibility (Person fills AGENT, Location fills LOCATION)
   - Position (first noun before verb = agent, first noun after = patient)

### 6.2 Intent Detection from Grammatical Form

The inflected form of the predicate signals intent:
- **Base/imperative** ("title", "follow", "create") → dispatch
- **Past participle** ("titled", "followed", "created") → relation/query
- **Gerund/progressive** ("titling", "following") → filter for ongoing
- **3rd person** ("titles", "follows") → assertion about someone else
- **Noun form** ("songs", "books") → type constraint for query

### 6.3 Query as Progressive Narrowing

Every input is progressively refined description:
- "bob" → everything named bob
- "bob dylan" → narrows to Bob Dylan
- "songs by bob dylan" → type=Song, PERFORMED_BY agent=Bob Dylan
- "songs by bob dylan from the 70s" → adds TIME constraint

No special query mode. The system always shows what matches the current partial frame.

### 6.4 Files to Change

- `FrameAssembler.java` — generalized frame building (update for new Relation model)
- `EvalInput.java` — intent detection from grammatical features
- `ExpressionParser.java` — frame expressions alongside math expressions
- New: `FrameExpression.java` — AST node for frame (replacing PatternExpression)

## Phase 7: Result Presentation -- FUTURE

**Goal**: Query results shown naturally in the UI.

- Partial frame results shown as live-updating list in detail pane
- Progressive narrowing: results update as each token is added
- Results are items that match the frame constraints
- Selecting a result fills it as context (same navigation model)
- Empty roles shown as prompts ("who?", "when?")

## Implementation Order

```
Phase 1 (Frame Relations)     [DONE]
   ↓
Phase 2 (Grammar Sememes)     [DONE]
   ↓
Phase 3 (Lexeme Model)        [DONE]
   ↓
Phase 4 (English Morphology)  [DONE]
   ↓
Phase 5 (UniMorph Import)     [DONE]
   ↓
Phase 6 (Unified Pipeline)    [IN PROGRESS] ← current focus
   ↓
Phase 7 (Result Presentation) [FUTURE]
```

Phases 1-5 are complete. Phase 6 is next — updating FrameAssembler for the new
frame-based Relation model. Phase 7 depends on Phase 6.

## Design Principles

- **One frame structure** for relations, dispatch, and queries
- **Predicate owns the schema** — roles defined on the sememe, not scattered across implementations
- **Grammatical features are sememes** — not a fixed enum, languages define their own
- **Form table, not fixed slots** — `Map<Set<Feature>, String>` allows any language's morphology
- **Progressive refinement** — every input is a description being narrowed
- **No query syntax** — natural language IS the query language
- **Inflection carries intent** — the word form tells the system what you mean to do
