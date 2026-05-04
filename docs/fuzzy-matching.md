# Fuzzy Matching

In Common Graph, exact matching falls out of content addressing for free: two frames with the same encoded bytes have the same CID, full stop.  Equality is settled by hash.  But many of the operations users actually want are not equality — they're *similarity*.  Find frames "like this one."  Find sememes "close in meaning."  Find duplicates that aren't bytewise duplicates.  Suggest the next likely binding given a partial frame.  Cluster a result set into structural neighborhoods.  All of these are *fuzzy* — they require ranking candidates by graded resemblance rather than partitioning them into match-or-no-match.

This document is an architectural reference for the fuzzy-matching layer.  It complements `query.md` — exact and structured queries belong there; the techniques here extend the query model with similarity-driven selection where exact matching is too brittle.

Parts of this are settled in principle; the implementation choices are deliberately plural and downstream.  Flagged where relevant.

## Discrete identity, continuous similarity

A guiding principle: **identity in CG is discrete; similarity is continuous.**

- Sememes have IIDs.  Frames have CIDs.  Two of the same thing are the same item; two different things are different items.  This is non-negotiable — the entire trust/attribution/version-anchoring layer depends on stable discrete reference.
- *Similarity* between two discrete things is a continuous quantity, computable in many ways, with multiple valid scoring methods coexisting for different purposes.

Fuzzy matching is the family of techniques that compute continuous similarity scores over discretely-identified frames.  It does not introduce continuous *identity* (which would dissolve attribution).  It introduces continuous *distance* between identities, computed on demand or precomputed opportunistically.

This is a foundational architectural commitment.  All fuzzy methods discussed below are downstream of the discrete primitive.  The Datum, the indexes, the trust matrix — all stay exactly the same.  Fuzzy matching is added as a query-layer module, not as a redefinition of the foundation.

## Use cases

A non-exhaustive list, with the kind of question each answers:

- **Frame similarity search.** "Find frames structurally similar to this one, regardless of authorship."  Useful for browsing, deduplication discovery, and cross-vocabulary alignment.
- **Pattern matching with partial templates.** "Find frames matching this template, where some bindings are wildcards."  Extends the `ANY`-based query model with graded-relevance ranking when many candidates match the template.
- **Near-duplicate detection.** "Multiple signers asserted nearly the same MOVE in this chess game — surface them as a candidate cluster."  Useful for merge/consolidation flows.
- **Sememe proximity.** "Find sememes close in meaning to this one without an exact EQUALS chain."  Useful for cross-vocabulary mapping, for query relaxation, and for AI/human-curated vocabulary alignment.
- **Query relaxation.** "An exact query returned nothing; expand to similar candidates and rank them."  Useful for tolerant search UIs.
- **Auto-suggestion during authoring.** "Given the partial frame the user is composing, suggest likely next bindings based on similar completed frames."  Useful for interactive editors.
- **Cross-vocabulary alignment.** "Find sememes in vocabulary B that look like this sememe from vocabulary A."  Critical for trust-graph composition across communities.
- **Clustering and structural browsing.** "Group these 10,000 frames into structural neighborhoods so the user can navigate by shape rather than by identity."

These all share a shape: given a query frame (or sememe, or partial template) and a candidate set, return candidates ranked by similarity score, optionally thresholded.

## Approaches

Multiple similarity methods are valid for different purposes, and CG should accommodate several rather than committing to one.  This is informed by Tauser's quantitative finding [1]: across multiple programming languages and embedding methods, no single embedding scheme reliably preserves structural similarity in vector space.  Different methods win on different problems.  The architecture should reflect that.

### A. Compositional similarity (frame-by-frame, recursive)

A frame is a structured thing — predicate, role-keyed bindings, qualifiers, targets that may themselves be frames.  Similarity between two frames can be computed compositionally:

```
sim(f1, f2) = combine(
    sim_predicate(f1.head, f2.head),
    sim_bindings(f1.bindings, f2.bindings),
    ...
)
```

Where:

- `sim_predicate` is sememe-graph similarity (see below) between the two head sememes.
- `sim_bindings` aligns bindings by role + qualifier, then computes per-binding similarity recursively (target-vs-target).
- `combine` is some weighted aggregation, possibly tunable per query type.

Compositional similarity is interpretable and tractable to debug.  It also handles the type-asymmetric cases naturally: a string target is compared by string distance, an item-reference target by sememe similarity, a sub-frame target by recursive frame similarity, a numeric target by numeric distance.

Drawback: alignment of bindings is non-trivial when both frames have multiple bindings of the same role with different qualifier sets.  The general problem is a weighted bipartite matching.

### B. Weisfeiler-Lehman graph kernel (structural fingerprinting)

The WL kernel [2] produces a multi-set of subgraph fingerprints by iterative neighborhood hashing.  Two graphs are structurally similar to the extent their fingerprint multi-sets overlap.  Tauser's thesis re-implements this in Rust as the basis for graph2vec-style program embeddings [1].

For CG, frames are small graphs — predicate at the head, role bindings as labeled edges to targets, sub-frames as nested graph structure.  The WL kernel applies directly:

```
labels at iteration 0: predicate IIDs, role IIDs, qualifier compounds, target identities/literals
iteration i+1 of node n:  hash(concat(n.label, sorted(neighbor.labels)))
fingerprint(graph) = multi-set of all labels produced across all iterations
similarity(a, b) ≈ |fingerprint(a) ∩ fingerprint(b)| / |fingerprint(a) ∪ fingerprint(b)|
```

Properties:

- **Cheap and deterministic.**  No training.  Single pass per iteration; linear in graph size with small constant.  CityHash64 or MurmurHash keeps labels at fixed size (avoiding the exponential growth of naive label concatenation).
- **Independent of vocabulary specifics.**  WL doesn't know what predicates "mean"; it captures only structural shape.  Useful precisely *because* it's vocabulary-blind — it surfaces structural patterns that span across semantic similarity.
- **Compositional with hierarchy.**  After `n` iterations, each node's fingerprint reflects structure `n` hops out.  Choose `n` to control the resolution.
- **Suitable for indexing.**  WL fingerprints can be precomputed and stored as frame metadata.  Approximate-nearest-neighbor methods (LSH on fingerprint sets) make similarity queries scale.

This is a strong baseline.  Worth implementing early in the fuzzy-matching layer.

### C. Sememe-graph semantic similarity

Two sememes are *semantically* similar to the extent that the trust-weighted vocabulary graph connects them.  Sources of connection:

- Direct **EQUALS / SAME_AS** frames (signed by trusted parties) — the strongest signal.
- **Hypernymy chains** — shared ancestors, depth-of-divergence as distance.
- **Co-occurrence in similar frames** — sememes that show up in similar predicate positions across the corpus are more likely to be similar.
- **Lexeme overlap** — sememes that share many of the same surface lexemes (across languages) are likely similar concepts.

A trust-weighted shortest-path or random-walk distance over the sememe graph yields a continuous similarity score.  This is fundamentally different from WL — it's *semantic*, not structural.  Two frames with completely different shapes might have semantically similar predicates.  Two frames with identical shapes might have semantically distant predicates.

Both signals are useful; they're complementary.  A composite scorer that combines structural (WL) and semantic (sememe-graph) similarity will outperform either alone for most use cases.

### D. Embedding-based methods (downstream, with caveats)

Vector embeddings — doc2vec, modern transformer text embeddings, custom-trained frame embeddings — are a natural fit for fuzzy matching once trained: cosine similarity over fixed-dimensional vectors is fast, and approximate nearest-neighbor search libraries are mature.

The catch, again from Tauser's findings: embeddings don't reliably preserve structural similarity across all domains.  Doc2vec collapses programs into a small subspace of the embedding space, hurting discrimination.  Modern text embeddings have larger magnitudes but weak correlation with AST structure across the languages tested [1].

Recommendation: treat embeddings as a *plug-in* similarity engine alongside WL and sememe-graph methods, not as a replacement for them.  Different embeddings will win on different use cases (e.g., text-heavy frames may benefit from text embeddings; structurally complex frames will likely do better with WL).  Allow multiple embedding engines to coexist; let the query specify which to use, or compose them.

### E. Type-specific similarity functions

Some bindings carry value-typed targets where domain-appropriate distance functions exist:

- **Strings**: edit distance (Levenshtein), Jaro-Winkler for short strings, n-gram overlap for longer.
- **Numbers**: absolute or relative difference, optionally locale-aware (units!).
- **Quantities with units**: convert to common base unit, then numeric distance.
- **Times**: temporal distance with appropriate scaling.
- **Locations**: geographic distance.
- **Content references** (CIDs): typically just equality, since content is content-addressed.  Optionally, fuzzy-hash similarity for media.

These are leaf-level functions invoked from the compositional similarity recursion at the appropriate target types.

## Composition and configuration

Real fuzzy queries combine signals.  A "find frames like this" query might want:

- Predicate similarity ≥ 0.8 (sememe-graph)
- AND structural similarity ≥ 0.5 (WL)
- AND target-binding similarity weighted by role importance

The query model accommodates this: similarity becomes another category of set-returning binding evaluation, computed per candidate.  Different similarity engines are different `PredicateBehavior` implementations on similarity-flavored predicates (e.g., a `SIMILAR_TO` predicate whose `evaluate` returns a matcher that scores candidates).

Design implication: similarity engines plug into the same evaluation pipeline as exact matchers.  No separate fuzzy-query subsystem; just additional matchers in the query frame's binding tree.

## Indexing and performance

Fuzzy matching is fundamentally more expensive than exact lookup.  Mitigations:

- **Precomputed fingerprints.**  WL fingerprints can be stored alongside each frame as metadata, computed once at frame insertion and reused.
- **LSH on fingerprint sets.**  Locality-sensitive hashing yields approximate nearest neighbors over WL fingerprints in sub-linear time.
- **Sememe-graph caches.**  Trust-weighted distance computations between commonly-queried sememe pairs can be cached.
- **Embedding indexes.**  HNSW, FAISS-style indexes for embedding-based similarity when those engines are in use.
- **Trust-scoped pre-filtering.**  Most fuzzy queries care about a trust-bounded subset of frames, not the entire object store.  Filter by trust/visibility first, compute similarity on the smaller set.

These are all standard techniques; none require novel research.  They sit in the librarian's index layer.

## Integration with CG architecture

Fuzzy matching does not modify the foundation.  Specifically:

- The Datum primitive is unchanged.
- Frame encoding is unchanged.
- The forward and reverse compound-key indexes are unchanged.
- The trust matrix is unchanged.
- The query frame model from `query.md` is unchanged.

Fuzzy matching adds:

- New similarity-flavored predicates (`SIMILAR_TO`, `NEAR`, `STRUCTURALLY_LIKE`, etc.) with `PredicateBehavior` implementations that produce matchers scoring candidates continuously.
- Optional precomputed metadata per frame (WL fingerprint, embeddings if any).
- A pluggable similarity-engine registry on the librarian, configurable per deployment.

A concrete sketch of a fuzzy query frame:

```
SIMILAR_TO {
    THEME → CHESS_MOVE { AGENT → Fischer, SOURCE → e2, GOAL → e4 }
    THRESHOLD → 0.7
    METHOD → WL_KERNEL                  (or composite, or omit for default)
}
```

Evaluation: walk all CHESS_MOVE frames in the trust-bounded scope, compute similarity to the THEME frame using the specified method (or a default composite), return candidates with score ≥ THRESHOLD, ranked by score.

The result is itself a frame stream like any other query result.  Existing presentation, pagination, and trust-filtering all apply.

## What this enables that exact matching can't

A short list of capabilities that emerge once fuzzy matching is in place:

- "Did anyone publish a frame like this one already?" — duplicate detection across signers.
- "Show me the closest matches to my draft" — interactive composition support.
- "Cluster these 10,000 reactions to the meme by structural pattern" — browsing by shape.
- "Suggest sememes from the user's vocabulary that align with WordNet's definition of X" — cross-vocabulary mapping.
- "What frames in this corpus are most similar to a frame this AI just proposed?" — provenance-checking against AI-generated content.
- "Group by similarity, then show me clusters where multiple trusted signers converged" — emergent consensus detection.

None of these require changing the data model.  All of them require *only* a similarity score and a way to filter/rank by it.

## References

[1] Tauser, K. (2025). *Exploring Latent Program Spaces for Program Synthesis*.  MS thesis, University of Oklahoma.  Re-implements graph2vec via WL features for program embeddings; provides the empirical motivation that structural similarity is preserved imperfectly across embedding methods, and that simpler structural fingerprints (WL) are competitive with learned embeddings.  Source of the WL-as-frame-fingerprint idea applied here.

[2] Shervashidze, N., Schweitzer, P., van Leeuwen, E.J., Mehlhorn, K., & Borgwardt, K.M. (2011). "Weisfeiler-Lehman Graph Kernels."  *Journal of Machine Learning Research* 12, 2539–2561.  Original formulation of the WL graph kernel.

[3] Narayanan, A., Chandramohan, M., Venkatesan, R., Chen, L., Liu, Y., & Jaiswal, S. (2017). "graph2vec: Learning Distributed Representations of Graphs."  ACM.  Combines WL features with doc2vec to embed graphs; the basis for Tauser's program-embedding work.

## Further work

Open questions, deliberately unsettled:

- **Default composite scorer.**  When no similarity method is specified, what's the right default combination of WL + sememe-graph + per-binding type-specific functions?  Likely tunable per deployment.
- **Trust-weighted similarity.**  How does the trust matrix participate in similarity computation?  Should similarity scores incorporate trust weighting on the connecting evidence (e.g., EQUALS frames between sememes), or stay vocabulary-blind?  Probably both, exposed as alternative similarity methods.
- **Fuzzy hash for content references.**  For media content (images, audio, video) referenced by CID, perceptual fuzzy hashes would enable "find similar images" queries.  Out of scope for the core fuzzy-matching layer but composable with it via type-specific similarity functions.
- **Similarity-driven frame creation.**  The eventual reverse direction: given a similarity score and a trusted neighborhood, *propose* new frames that fit the pattern.  This is the territory Tauser's RL-based program synthesis covers; CG's analogue would be AI-assisted authoring informed by structural neighborhood.  Future.
