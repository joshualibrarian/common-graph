# Roadmap: Frame Unification

Migration plan from the current component/relation/mount architecture to the unified Frame model described in `frames.md`.

## Prior Work (Complete)

Phases 1-5 of the original vocabulary roadmap are done:
- Frame-based relations (predicate + bindings, RECORD CID, fan-out indexes)
- Grammatical feature sememes (tense, aspect, mood, voice, person, number, case)
- Lexeme model (form table with feature tags)
- English morphology engine (regular inflection rules)
- UniMorph import (irregular forms, paradigm completion)

This roadmap picks up from there with the full structural unification.

## Current State

The system is closer to frames than it looks:

| Current | Frame Equivalent | Gap |
|---------|-----------------|-----|
| `ComponentEntry` | `FrameEntry` | Missing: semantic keys, body/record split |
| `ComponentTable` | `FrameTable` | Missing: unified relation storage |
| `HandleID` | `FrameKey` | Missing: compound keys, sememe tokens |
| `Relation` | `Frame` (unendorsed) | Missing: unification with component model |
| `LibraryIndex` | `FrameIndex` | Missing: indexes all frames, not just relations |
| `@ContentField` | `@Frame` | Missing: semantic key declaration |
| `@RelationField` | (absorbed) | Separate annotation no longer needed |
| `EntryPayload` | Frame target + mode | Already handles snapshot/stream/local |
| `EntryConfig` | `Config` (scene + policy) | Already has settings + policy |
| `Mount` | `FrameEntry.mount` | Already stored on entries |

The strategy: **rename and expand from the inside out**, keeping the system working at each phase boundary.

---

## Phase 0: FrameKey

**Goal**: Replace HandleID with compound semantic keys.

**Why first**: FrameKey is the foundational addressing primitive. Everything downstream depends on it. It's also the smallest, most self-contained change.

### Steps

1. **Create `FrameKey` class** in `item/id/`
   - Immutable sequence of tokens: `List<FrameToken>`
   - `FrameToken` sealed interface: `Sememe(ItemID)` | `Literal(String)`
   - Factory: `FrameKey.of(ItemID...)`, `FrameKey.literal(String)`, `FrameKey.of(ItemID, String)`
   - Single-literal constructor wraps existing HandleID semantics
   - Canonical encoding: CBOR array of tokens (sememe as REF tag, literal as string)
   - `toHandleID()` — backward compat for single-literal keys
   - `isLiteral()`, `isSemantic()`, `head()`, `qualifier()` accessors

2. **Add `FrameKey` to `ComponentEntry`** alongside existing `HandleID`
   - New field: `FrameKey frameKey` (Canon order after existing fields or computed)
   - If `frameKey` is null, derive from `handle` + `aliasRef` (backward compat)
   - If `frameKey` is set, `handle` is derived from it (forward path)
   - Existing code that reads `entry.handle()` keeps working

3. **Update `ComponentTable` to accept `FrameKey` lookups**
   - Add: `get(FrameKey)`, `put(FrameKey, ComponentEntry)`
   - Keep existing `get(HandleID)` as delegation to FrameKey path
   - Internal map transitions from `Map<HandleID, ComponentEntry>` to `Map<FrameKey, ComponentEntry>`

4. **Update `@ContentField` annotation**
   - Add optional `key` attribute: `@ContentField(key = {GLOSS, ENG}, handle = "gloss/eng")`
   - `handle` remains for backward compat (generates single-literal FrameKey)
   - `key` takes precedence when present
   - `ComponentFieldSpec` gains `FrameKey frameKey` field

5. **Update `ItemScanner`** to extract FrameKey from annotations
   - `ComponentFieldSpec` updated: stores `FrameKey` alongside `HandleID`
   - `bindFieldsFromTable()` resolves by FrameKey first, HandleID fallback

### Key files
- New: `item/id/FrameKey.java`, `item/id/FrameToken.java`
- Modified: `ComponentEntry.java`, `ComponentTable.java`, `Item.java` (annotation), `ItemScanner.java`, `ComponentFieldSpec.java`

### Deliverable
FrameKey exists, ComponentEntry carries it, ComponentTable indexes by it, annotations support it. All existing code works unchanged.

---

## Phase 1: FrameEntry and FrameTable

**Goal**: Rename ComponentEntry → FrameEntry, ComponentTable → FrameTable. Make the frame vocabulary native.

### Steps

1. **Rename `ComponentEntry` → `FrameEntry`**
   - Primary key is now `FrameKey` (not HandleID)
   - Drop `alias` and `aliasRef` — subsumed by FrameKey's semantic tokens
   - Retain: type, identity, payload, config, presentation, vocabulary
   - Add: `bodyHash` field (ContentID, computed from body encoding)

2. **Rename `ComponentTable` → `FrameTable`**
   - Primary map: `Map<FrameKey, FrameEntry>`
   - Mount navigation methods stay (they operate on FrameEntry now)

3. **Update `ItemState`**
   - Field rename: `ComponentTable content` → `FrameTable frames`
   - Backward-compat: `componentSnapshot()` delegates to `frameSnapshot()`

4. **Update `Item` API**
   - `Item.content()` → `Item.frames()` (keep `content()` as deprecated delegate)
   - `Item.addComponent()` → `Item.addFrame()` (keep old as delegate)

5. **Update `ItemScanner` / `ItemSchema`**
   - `ComponentFieldSpec` → `FrameFieldSpec`
   - Components utility class: update encoding to reference FrameEntry

### Key files
- Renamed: `ComponentEntry.java` → `FrameEntry.java`, `ComponentTable.java` → `FrameTable.java`
- Modified: `ItemState.java`, `Manifest.java`, `Item.java`, `ItemScanner.java`, `ItemSchema.java`, `Components.java`

### Deliverable
Internal vocabulary is "frames" everywhere. Deprecated shims for old names. No behavioral change.

---

## Phase 2: Body/Record Split

**Goal**: Implement body/record separation. Body is the hashable assertion; record is the signed envelope.

**Why third**: The key structural change that enables dedup, multi-attestation, and cosigning. Requires FrameEntry to exist first.

### Steps

1. **Define `FrameBody`** value type
   - Fields: `predicate` (from FrameKey head), `theme` (owner ItemID), `bindings` (qualifiers + target)
   - `FrameBody.hash()` → ContentID (deterministic CBOR of body fields)
   - Two identical assertions from different signers produce the same body hash

2. **Add body hash to `FrameEntry`**
   - `FrameEntry.bodyHash` — computed from FrameBody
   - For endorsed frames: stored in manifest's frame table
   - Replaces/supplements `payload.snapshotCid` for identity purposes

3. **Create `FrameRecord`** for unendorsed frames
   - `bodyHash` — reference to the assertion
   - `signer` — who attests
   - `timestamp` — when
   - `signature` — cryptographic proof
   - `recordCid` — ContentID of the record encoding

4. **Update storage**
   - Body bytes stored in PAYLOAD column (content-addressed, deduped)
   - Record bytes stored in FRAME column (or reuse RELATION)
   - Key: recordCid → record bytes

5. **Update `Relation` to produce `FrameBody` + `FrameRecord`**
   - `Relation.toFrameBody()` → extracts predicate + theme + bindings
   - `Relation.toFrameRecord()` → wraps with signer + signature
   - Existing relation flows produce Frame records

### Key files
- New: `FrameBody.java`, `FrameRecord.java`
- Modified: `FrameEntry.java`, `ItemStore.java` (column), `Relation.java`

### Deliverable
Body/record split exists. Frames stored with body dedup. Same assertion from different signers shares body hash.

---

## Phase 3: Index Expansion

**Goal**: Expand LibraryIndex to index ALL frames (not just relations), add RECORD_BY_BODY.

### Steps

1. **Add `RECORD_BY_BODY` column** to LibraryIndex
   - Key: `bodyHash | signerKey` → recordCid bytes
   - Enables: "who attests this?" and attestation counting

2. **Rename index columns**
   - `REL_BY_ITEM` → `FRAME_BY_ITEM` (same key format: `itemIID | predicate | bodyHash`)
   - `REL_BY_PRED` → `FRAME_BY_PRED` (same: `predicate | bodyHash`)

3. **Expand `indexRelation()` → `indexFrame()`**
   - Accepts FrameBody + FrameRecord (or FrameEntry for endorsed)
   - Fans out by predicate and all ItemID bindings

4. **Index endorsed frames at commit time**
   - Currently only relations are indexed
   - Now: every FrameEntry in the manifest gets indexed
   - ALL endorsed frames become queryable

5. **Update query methods**
   - `byItem()` returns `Stream<FrameRef>` (was `Stream<RelationRef>`)
   - New: `byBody(ContentID bodyHash)` → all records for a body
   - New: `attestationCount(ContentID bodyHash)`

### Key files
- Modified: `LibraryIndex.java`, all three backends (`RocksLibraryIndex`, `MapDBLibraryIndex`, `SkipListLibraryIndex`)
- Modified: `Library.java` (commit path), `Item.java` (commit indexes all frames)

### Deliverable
Full frame indexing. Every frame discoverable via index. Record-by-body enables attestation queries.

---

## Phase 4: Relation Absorption

**Goal**: Eliminate `Relation` as a separate storage type. Relations are just frames.

### Steps

1. **Endorsed relations → endorsed FrameEntry in manifest**
   - Relations are regular FrameEntry with semantic FrameKey
   - Predicate becomes FrameKey head; bindings become FrameEntry bindings

2. **Unendorsed relations → FrameRecord in storage**
   - Stored as FrameRecord (body in PAYLOAD, record in FRAME column)
   - Indexed via FRAME_BY_ITEM and FRAME_BY_PRED

3. **Update `Library` query API**
   - `Library.queryFrames(predicate, theme?, bindings?)` replaces relation-specific queries
   - Keep deprecated relation query methods as delegates

4. **Update `PatternExpression`**
   - Pattern becomes incomplete frame (predicate + theme? + bindings with holes)
   - Evaluates against frame index

5. **Deprecate `Relation` class**
   - Keep as helper for constructing frames with relational semantics, or remove
   - `RelationID` → body hash of the frame

6. **Remove `@RelationField`**
   - Replace with `@Frame` using appropriate key and endorsement flag

### Key files
- Modified: `Relation.java` (deprecated), `Library.java`, `PatternExpression.java`, `ItemScanner.java`
- Removed: `RelationFieldSpec.java`, `RelationTable.java` (if separate)

### Deliverable
Relations are frames. One storage model, one index, one query mechanism.

---

## Phase 5: Annotation Unification

**Goal**: Single `@Frame` annotation replaces `@ContentField` and `@RelationField`.

### Steps

1. **Create `@Frame` annotation**
   ```java
   @Target(ElementType.FIELD)
   public @interface Frame {
       String[] key() default {};        // semantic FrameKey tokens (ItemID strings)
       String handle() default "";       // literal key (backward compat)
       String path() default "";         // mount path
       boolean localOnly() default false;
       boolean stream() default false;
       boolean identity() default true;
       boolean endorsed() default true;  // false = unendorsed
   }
   ```

2. **Update `ItemScanner`** to process `@Frame`
   - Produces `FrameFieldSpec` (unified)
   - FrameKey from `key` (semantic) or `handle` (literal)
   - `endorsed` flag determines manifest inclusion

3. **Migrate existing annotations** across codebase
   - `@ContentField(handle="vault")` → `@Frame(handle="vault")`
   - `@ContentField(handle="gloss/eng")` → `@Frame(key={"cg:pred/gloss", "cg:language/eng"})`
   - `@RelationField(predicate="cg:pred/author")` → `@Frame(key={"cg:pred/author"})`
   - Keep old annotations as deprecated delegates during transition

4. **Update type classes**
   - Every `@ContentField` → `@Frame`
   - Every `@RelationField` → `@Frame(endorsed=false)` or `@Frame`

### Key files
- New: `@Frame` annotation on `Item` class
- Modified: `ItemScanner.java`, `ItemSchema.java`, `FrameFieldSpec.java`
- Modified: every `@Type` class (annotation migration)

### Deliverable
One annotation for frame fields. Clean, semantic type declarations.

---

## Phase 6: Query Unification

**Goal**: Queries become incomplete frames. One query model.

### Steps

1. **Create `FrameQuery`** (replaces PatternExpression)
   - Fields: `predicate?`, `theme?`, `bindings with holes`
   - Holes marked with sentinel (e.g., `Sememe.WHAT`)
   - Evaluation: search frame index for matching frames

2. **Update prompt syntax**
   - `? author TheHobbit` → AUTHOR { theme: TheHobbit, target: ? }
   - `author ?` → AUTHOR { theme: focused, target: ? }
   - Progressive narrowing: each token adds a constraint

3. **Update `ExpressionComponent`** patterns
   - Factory methods produce FrameQuery
   - Backward compat: `ExpressionComponent.pattern(S, P, O)` converts to FrameQuery

4. **Update `FrameAssembler`**
   - Builds FrameQuery from resolved tokens
   - Intent detection from grammatical form (imperative → dispatch, participle → query)

### Key files
- New: `FrameQuery.java`
- Modified: `PatternExpression.java` (deprecated), `ExpressionComponent.java`, `FrameAssembler.java`, `EvalInput.java`

### Deliverable
One query mechanism. Queries are frames with holes.

---

## Phase 7: Cleanup

**Goal**: Remove deprecated shims, old types, dead code.

1. ~~Remove `ComponentEntry` → `FrameEntry`~~ (done in Phase 1)
2. ~~Remove `ComponentTable` → `FrameTable`~~ (done in Phase 1)
3. ~~Remove `HandleID` usage → `FrameKey`~~ (HandleID still used internally by FrameKey)
4. ~~Remove `RelationID` → body hash~~ (done in Phase 4)
5. Remove `@ContentField` / `@RelationField` annotation definitions ✅
6. Remove `ComponentFieldSpec.java` / `RelationFieldSpec.java` files ✅
7. Remove backward-compat generation in `ItemScanner` (no more dual-spec creation) ✅
8. Update `ItemSchema` constructor: 4-param (frameFields only, no componentFields/relationFields) ✅
9. Update all `Item.java` callers: `componentFields()` → `endorsedFrameFields()`, `getComponentField()` → `getFrameField()` ✅
10. Update `FrameAnnotationTest` to use frame-only API ✅
11. Remove `toComponentFieldSpec()` / `toRelationFieldSpec()` from `FrameFieldSpec` ✅

### Deliverable
Clean codebase. One primitive. Frames all the way down.

---

## Phase Order

```
Phase 0: FrameKey           ✅ DONE
Phase 1: FrameEntry/Table   ✅ DONE
Phase 2: Body/Record        ✅ DONE
Phase 3: Index Expansion    ✅ DONE
Phase 4: Relation Absorption✅ DONE
Phase 5: Annotation         ✅ DONE
Phase 6: Query              ✅ DONE
Phase 7: Cleanup            ✅ DONE
```

Each phase produces a working system. Tests pass at every boundary. No big-bang migration.

## Risk Notes

- **Manifest format**: Existing stored manifests won't decode after Phase 1. Per project policy, stored data is irrelevant — breaking changes are fine.
- **CBOR encoding**: FrameKey and FrameEntry need new canonical encodings. Must be deterministic for hashing.
- **Index migration**: New column families in RocksDB. Old indexes can coexist.
- **Annotation migration**: Hundreds of `@ContentField` annotations. Could be scripted.
- **WorkingTreeStore**: `.item/` directory structure changes — component metadata files become frame metadata files.

## Non-Goals (for now)

- Remote protocol changes (CG Protocol and Session Protocol adapt later)
- UI rendering changes (Surface renders from frame data the same way)
- Vocabulary system changes (already works with frames conceptually)
- Performance optimization (correct first, fast later)
