# Frame Unification Roadmap

The central insight: **relations, verb dispatch, and queries are the same thing** — a frame
filled to different degrees, used in different modes.

- **Fully filled + code** → dispatch (verb execution)
- **Fully filled + no code** → assert (store as signed relation)
- **Partially filled** → query (search for completions)

One structure. Three modes determined by context.

## Current State (What Exists)

- **Relations**: `Relation(subject, predicate, object, qualifiers)` — RDF triple + qualifier hack
- **Verb dispatch**: `@Verb` on component methods, `@Param(role=...)` for arguments
- **Queries**: `PatternExpression(S, P, O)` and `QueryComponent` — triple patterns only
- **Sememes**: Language-agnostic meaning units, seeded from WordNet/CILI
- **Lexemes**: Exist in model but only store lemma (base form), no inflected forms
- **TokenDictionary**: Maps tokens → sememes, but only base forms registered
- **Expression parser**: Pratt parser for math/expressions, FrameAssembler for verbs

## Phase 1: Frame-Based Relations

**Goal**: Replace `Relation(S, P, O, qualifiers)` with `Relation(predicate, roles)`.

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
    roles: Map<ItemID, Target>           // RoleSememe → value (IidTarget or Literal)
    createdAt: Instant
    rid: RelationID                      // Hash of predicate + identity-bearing roles
    signing: Signing
}
```

- No more `subject`, `object`, `qualifiers` fields
- Agent and patient are just conventional role names for the two most common roles
- Qualifiers become regular roles (time, place, confidence, etc.)
- Predicate's FrameSchema defines which roles are identity-bearing (for RID)

### 1.3 Migration

- `Relation(S, P, O)` → `Relation(P, {AGENT: S, PATIENT: O})`
- Qualifier entries → additional role bindings
- RelationQuery fluent API updated: `.from(x)` → `.role(AGENT, x)`, `.to(y)` → `.role(PATIENT, y)`
- Convenience: `.from()` and `.to()` remain as aliases for agent/patient

### 1.4 Index Redesign

Current: 5 column families (by_subj, by_obj, by_pred, by_subj_pred, by_obj_pred)

New:
- **by predicate**: `PRED → [RID...]`
- **by predicate + role + value**: `(PRED, ROLE, VALUE) → [RID...]` — the workhorse
- **by value (any role)**: `VALUE → [(PRED, ROLE, RID)...]` — "everything involving X"

The "by value" index is key — it's what makes "bob" work as a query (find every frame
where "bob" fills any role).

### 1.5 Files to Change

- `Relation.java` — new data model
- `RelationTable.java` — updated storage
- `RelationEntry.java` — updated entry type
- `LibraryIndex.java` — new column families
- `RelationQuery.java` — role-based fluent API
- `PatternExpression.java` — frame pattern matching
- `QueryComponent.java` — stored frame queries
- `Sememe.java` — add FrameSchema

## Phase 2: Grammatical Feature Sememes

**Goal**: Seed sememes for grammatical features so lexeme forms can reference them.

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

- Create seed sememes under `cg:grammar/` namespace
- Each is a NounSememe (they're concepts)
- Map UniMorph feature tags to these sememes for import
- Languages reference whichever features they use

### 2.3 Files to Create/Change

- New: `GrammaticalFeature.java` (or just seed sememes in appropriate place)
- `SeedVocabulary` additions for grammar feature seeds
- Mapping table: UniMorph tag → CG grammatical feature sememe

## Phase 3: Lexeme Data Model

**Goal**: Lexemes store all inflected forms, each tagged with grammatical features.

### 3.1 Form Table

```java
Lexeme {
    sememe: ItemID                              // The meaning
    language: ItemID                            // Which language
    pos: PartOfSpeech
    forms: Map<Set<ItemID>, String>             // Feature set → surface form
    inflectionClass: String                     // "regular", "irregular", pattern name
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

### 3.3 Files to Change

- `Lexeme.java` — redesigned with form table
- `TokenDictionary` — postings carry feature metadata
- `Posting.java` (or equivalent) — add grammatical features field

## Phase 4: English Morphology Engine

**Goal**: Generate regular inflected forms automatically from base + POS.

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

- `EnglishMorphology` class with static methods
- Input: (lemma, POS, inflectionClass)
- Output: Map<Set<FeatureSememe>, String> of generated forms
- Regular verbs auto-generated; irregulars stored explicitly
- Called during seed vocabulary population and WordNet import

### 4.3 Files to Create

- New: `EnglishMorphology.java` in english module
- Integration with `SeedVocabulary` for seed lexemes
- Integration with WordNet/LMF importer for imported lexemes

## Phase 5: UniMorph Import

**Goal**: Import irregular forms and complete paradigms from UniMorph data.

### 5.1 Data Source

UniMorph English data: tab-separated (lemma, form, features)
```
write   writes    V;3;SG;PRS
write   wrote     V;PST
write   written   V;V.PTCP;PST
write   writing   V;V.PTCP;PRS
```

### 5.2 Import Pipeline

1. Parse UniMorph TSV
2. Map UniMorph feature tags to CG grammatical feature sememes
3. Match lemma to existing sememe (via WordNet lemma → sememe mapping)
4. Create/update Lexeme with form entries
5. Post all forms to TokenDictionary

### 5.3 Feature Tag Mapping

UniMorph uses a standardized schema (UniMorph Schema):
- `PST` → PAST
- `PRS` → PRESENT
- `V.PTCP` → PARTICIPLE
- `3` → THIRD_PERSON
- `SG` → SINGULAR
- `PL` → PLURAL
- etc.

### 5.4 Files to Create

- New: `UniMorphImporter.java` in english module
- UniMorph data file bundled as resource (or downloaded on first use)
- Feature tag mapping table

## Phase 6: Unified Frame Pipeline

**Goal**: Input → frame assembly → dispatch/assert/query, driven by grammatical context.

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

- `FrameAssembler.java` — generalized frame building
- `EvalInput.java` — intent detection from grammatical features
- `ExpressionParser.java` — frame expressions alongside math expressions
- New: `FrameExpression.java` — AST node for frame (replacing PatternExpression)

## Phase 7: Result Presentation

**Goal**: Query results shown naturally in the UI.

- Partial frame results shown as live-updating list in detail pane
- Progressive narrowing: results update as each token is added
- Results are items that match the frame constraints
- Selecting a result fills it as context (same navigation model)
- Empty roles shown as prompts ("who?", "when?")

## Implementation Order

```
Phase 1 (Frame Relations)     ← Foundation, everything builds on this
   ↓
Phase 2 (Grammar Sememes)     ← Needed for lexeme forms
   ↓
Phase 3 (Lexeme Model)        ← Needed for morphology
   ↓
Phase 4 (English Morphology)  ← Generate regular forms
   ↓
Phase 5 (UniMorph Import)     ← Fill in irregulars
   ↓
Phase 6 (Unified Pipeline)    ← The big payoff
   ↓
Phase 7 (Result Presentation) ← User-facing polish
```

Phases 2-3 can start in parallel with Phase 1 since they're mostly independent.
Phases 4-5 depend on 2-3.
Phase 6 depends on everything.

## Design Principles

- **One frame structure** for relations, dispatch, and queries
- **Predicate owns the schema** — roles defined on the sememe, not scattered across implementations
- **Grammatical features are sememes** — not a fixed enum, languages define their own
- **Form table, not fixed slots** — `Map<Set<Feature>, String>` allows any language's morphology
- **Progressive refinement** — every input is a description being narrowed
- **No query syntax** — natural language IS the query language
- **Inflection carries intent** — the word form tells the system what you mean to do
