# Vocabulary System Implementation Roadmap

This roadmap transforms the vocabulary system from its current state to the documented vision. Phases are ordered by dependency; some can proceed in parallel.

## Phase 1: Posting Scope Model ✅

Align the Posting model so that language words are scoped to their Language Item, symbols are universal, and `global()` is retired.

### 1.1 — Retire `Posting.global()`

The `scoped(token, scope, target)` factory already does everything needed. `global()` is only appropriate for universal symbols (null scope). Rename or restrict:

- `Posting.universal(token, target)` — null scope, for symbols/operators only
- `Posting.scoped(token, scope, target)` — scope is any ItemID (language, item, user)
- `Posting.local(token, target)` — scope = target (convenience)
- Remove `Posting.global()` or make it delegate to `universal()`

Update all call sites.

### 1.2 — Seed English Language Item

Only English is seeded at bootstrap (needed for scoping seed tokens). Other languages are created during the English import — their names are English words.

```
ItemID.fromString("cg:language/eng")  →  32-byte IID
ItemID.fromString("cg:language/jpn")  →  32-byte IID
ItemID.fromString("cg:language/spa")  →  32-byte IID
...
```

Each is a tiny seed item (type + IID + code). Register the three-letter codes as **universal postings** (null scope): "eng" → `cg:language/eng`, "jpn" → `cg:language/jpn`. These resolve for everyone.

The English name for each language ("English", "Japanese") is a lexeme scoped to `cg:language/eng`. Other languages' names added when those languages are imported.

Language Items must be seeded **before** other seed items register their English lexemes.

### 1.3 — Scope seed tokens to the English Language Item

Where `SeedVocabulary` currently registers tokens as `Posting.global(...)`, change to `Posting.scoped(..., ENGLISH_LANGUAGE_IID)`. These are English words — they belong to English.

Symbols from sememe `symbols` fields (Phase 2) register as `Posting.universal(...)`.

---

## Phase 2: Sememe Alignment

Align the Sememe class with the documented structure.

### 2.1 — Add `symbols` field ✅

New `@ContentField List<String> symbols` on Sememe.

- Unit sememes: "m", "kg", "s", "K"
- Operator sememes: "+", "-", "=", ">", "<"
- Currency sememes: "USD", "EUR", "JPY"

During seed/index, symbols register as universal postings (null scope).

### 2.2 — Glosses as SememeGloss components ✅

Glosses describe the sememe itself in a given language. They stay on the sememe — but as **versioned, per-language components**, not a flat `Map<String, String>`.

A `SememeGloss` component carries:
- Language IID (which language this gloss is in)
- Gloss text (the definition)

Each language import adds a SememeGloss to each sememe it covers → new version of that sememe. English seeds add English glosses. A Japanese import adds Japanese glosses. Revertable. Ignorable.

Migrate the existing `glosses` map into SememeGloss components on each seed sememe.

### 2.3 — Remove `tokens` field from Sememe ✅

The `List<String> tokens` field holds English words — these are lexemes, not intrinsic to the sememe. Made transient (not persisted). Tokens are indexed as English-scoped postings during bootstrap but not stored on the sememe. The sememe retains only `symbols` (language-neutral) and `canonicalKey` as persistent fields.

### 2.4 — CILI-based canonical keys

Seed sememes that correspond to WordNet synsets should use CILI identifiers as their canonical key:

```
ItemID.fromString("cili:i78432")     # "create" — same IID everywhere
ItemID.fromString("cili:i54321")     # "document" — same IID everywhere
ItemID.fromString("cg:type/item")    # CG-internal, no CILI equivalent
```

This ensures that when a language import processes CILI synsets, it computes the same IID and merges into the existing sememe (adds lexemes, glosses) rather than creating a duplicate.

CG-specific concepts without CILI equivalents (type system internals, protocol primitives) keep `cg:` keys.

Each sememe's CILI ID is also recorded as a relation: `sememe --CILI_ID--> "i78432"` (literal). The predicate `CILI_ID` is itself a seed sememe. Same pattern applies for other external alignments (Wikidata Q-numbers, etc.).

**Prerequisite:** Curate a mapping of current seed sememes to their CILI IDs. This is a one-time research task against the CILI database.

---

## Phase 3: Lexicon Integration

Wire Language Items and their Lexicons into the live token resolution pipeline.

### 3.1 — Lexeme.toPosting() uses language scope ✅

Done in Phase 1. `Lexeme.toPosting()` now uses the `language` field as scope.

### 3.2 — Lexicon indexing scopes to its Language Item ✅

`Lexicon.add()` and `Lexicon.indexAll()` already scope postings via `Lexeme.toPosting()` which uses the language field.

### 3.3 — Pass language scopes through the resolution chain ✅

Callers of `TokenDictionary.lookup()` — Eval, Vocabulary.lookupToken, completers — must pass the user's active language IIDs in the scope chain. The TokenDictionary already supports varargs scopes; callers just need to provide them.

The scope chain is assembled from context:
- User's active languages (always)
- null (always — universal symbols)
- Focused item (for proper nouns/aliases)
- Session, user item, ancestors (as relevant)

---

## Phase 4: EntryVocabulary Activation

Wire the existing EntryVocabulary scaffolding on ComponentEntry into live dispatch.

### 4.1 — VocabularyContribution: single trigger field ✅

Renamed `ScopedVocabularyTerm` → `VocabularyContribution`. Dropped `description` field, renamed `scopePath` → `scope`. CBOR-tag-distinguished single-field encoding deferred to encoding refinement pass.

Refine to match the documented `VocabularyContribution`:

```
VocabularyContribution {
    trigger:  SememeRef | LiteralToken    # ONE field, CBOR-tag-distinguished
    target:   SememeID  | Expression      # ONE field, CBOR-tag-distinguished
    scope:    string                      # Scope path within component ("/" = root)
}
```

The trigger is one field: CBOR Tag 6 (REF) for a sememe reference, raw string for a literal token (with optional language tag). Same for target: Tag 6 for a sememe ID, raw string for an expression. No separate `sememeRef` / `literalToken` fields.

Apply this single-field-with-CBOR-tags pattern consistently wherever similar "this or that" fields appear.

### 4.2 — Item.buildVocabulary() reads EntryVocabulary ✅

After loading code-layer verbs from ItemSchema:
1. Iterate all ComponentEntry vocabulary contributions
2. For sememe-ref triggers: register in the item's Vocabulary (verb capability, noun registration)
3. For literal-token triggers: register as item-scoped postings in TokenDictionary
4. User layer wins on conflict with code layer

### 4.3 — Component proper nouns via EntryVocabulary

Migrate `alias` and `aliasRef` on ComponentEntry into EntryVocabulary contributions:

```
VocabularyContribution {
    trigger: "notes"              # Literal proper noun
    target:  <this component>     # Resolves to the component
    scope:   "/"
}
```

Remove `alias`/`aliasRef` fields once migration is complete.

---

## Phase 5: Dispatch Refinement

Inner-to-outer dispatch, expression scripting, Session as Item.

### 5.1 — Inner-to-outer dispatch ✅

Restructured Eval dispatch order:
1. Focused component's verbs (via `focusedComponent` handle on Eval)
2. Bound items from input (explicit user intent: "create CHESS")
3. Context item's vocabulary
4. Session item's vocabulary (via `session` Item on Eval)
5. Librarian's vocabulary (system-level)

First match wins. `headVerbScore()` mirrors the same priority. Token resolution uses scoped lookup with fallback to unscoped for proper nouns.

If multiple matches at the same level → disambiguation (dropdown or additional tokens).

### 5.2 — Scripted expressions ✅

VocabularyContribution now has an `expression` field. When a contribution has `isExpression() == true`, `populateVocabulary()` registers it via `Vocabulary.addExpression()`. Eval checks `lookupExpressionInChain()` (context → session → librarian) before token resolution. Matching expressions are expanded into tokens and re-evaluated through a child Eval with `depth + 1`, capped at `MAX_EXPRESSION_DEPTH = 8`.

### 5.3 — Session as Item ✅

`Session` extends Item directly with `@Verb` for exit and back. It wires callbacks (`onExit` → `running = false`, `onBack` → `goBack()`) and passes itself to EvalInput/Eval as the session scope. Session verbs dispatch naturally through the inner-to-outer chain without special-casing.

---

## Phase 6: Language Import Pipeline

Idempotent, merge-based import for any language.

### 6.1 — Import merge semantics

For each synset in a language database (WordNet, CILI-aligned):

```
1. Compute IID from canonical key (cili:iXXXXX or language-specific key)
2. Load existing sememe if it exists (it usually will)
3. Add SememeGloss component in this language → commit new version
4. Add lexemes to this Language Item's Lexicon
5. Index lexemes in TokenDictionary scoped to the Language Item
```

Most sememes will already exist from prior imports. The import adds language-specific data (glosses, lexemes) and commits new versions.

### 6.2 — Language-specific sememes

Some concepts exist in one language but not others (culturally specific terms without CILI entries). These get language-prefixed canonical keys:

```
ItemID.fromString("jpn:concept/wabi-sabi")
ItemID.fromString("deu:concept/schadenfreude")
```

They're full sememes — can be linked to CILI later if an alignment is established. Other languages can add lexemes for them (English might add "schadenfreude" as a loanword).

### 6.3 — Idempotency guarantees

- Re-running an import produces no duplicates (same IIDs from same canonical keys)
- Lexemes that already exist in the Lexicon are skipped or weight-updated
- Glosses that already exist for a language are updated, not duplicated
- TokenDictionary postings are upserted (idempotent by `<scope><token>` key)

---

## Dependency Graph

```
Phase 1 (Posting scope + Language seeds)
    |
    +--→ Phase 2 (Sememe cleanup)
    |        |
    |        +--→ Phase 2.4 (CILI keys — research task)
    |
    +--→ Phase 3 (Lexicon integration)
    |
    +--→ Phase 4 (EntryVocabulary activation)
              |
              +--→ Phase 5 (Dispatch refinement)
                       |
                       +--→ Phase 6 (Language import pipeline)
```

Phases 2, 3, and 4 can proceed in parallel after Phase 1. Phase 5 depends on Phase 4. Phase 6 depends on Phases 2.4 and 3.

## What Already Exists

- TokenDictionary with `<scope><token>` DB keys and scope support
- Language, Lexicon, Lexeme classes with correct shape
- ComponentEntry with EntryVocabulary facet and `addVocabularyContribution()`
- Sememe sealed hierarchy with all parts of speech
- ThematicRole enum, SemanticFrame, FrameAssembler
- Posting with scope field and normalization
- Eval with JLine completion, token resolution, and frame-based dispatch

## Biggest Lifts

- **Phase 2.4**: Curating CILI mappings for seed sememes (research, not code)
- **Phase 4.2**: Wiring EntryVocabulary into buildVocabulary() — this is where the new model comes alive
- **Phase 1.2**: Seeding ~7,000 language items (mechanical but touches bootstrap)
- **Phase 5.1**: Restructuring dispatch order (Eval refactor)
