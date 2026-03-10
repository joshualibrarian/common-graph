# Trust

Common Graph *is* a social network. Not in the debased sense of a feed-scrolling advertisement platform — but in the literal sense: a network of social relationships, modeled explicitly as signed data. Every connection between people, every endorsement, every reaction, every moderation decision is a first-class relation in the graph. The "social" isn't a feature built on top of infrastructure. It *is* the infrastructure.

Trust is the organizing principle. It determines what you see, who you sync with, whose assertions you accept, how far your queries propagate, and whose reactions matter. There is no separate "moderation system" — trust *is* moderation. There is no separate "reputation system" — trust *is* reputation. There is no separate "content filtering algorithm" — trust policies, applied to signed relations, produce different views for different people from the same underlying data.

The trust model draws from capability-based security (see [references/Miller 2006](references/Miller%202006%20-%20Robust%20Composition.pdf)), the formalization of relationships on public networks (see [references/Szabo 1997](references/Szabo%201997%20-%20Formalizing%20and%20Securing%20Relationships%20on%20Public%20Networks.pdf)), and the PGP web-of-trust — but with cleaner primitives and without PGP's monolithic packet format.

## Trust vs Authentication

These are separate concerns:

| Layer | Question | Mechanism | Document |
|-------|----------|-----------|----------|
| **Authentication** | "Is this key valid for this identity?" | Keys, signatures, KeyLogs, certs | [authentication.md](authentication.md) |
| **Trust** | "Do I believe this person about this topic?" | Relations, attestations, the trust matrix | This document |

Authentication is objective — either the signature verifies or it doesn't. Trust is subjective — my trust matrix is mine, yours is yours, and we can disagree about who to trust without either of us being "wrong."

---

## The Trust Matrix

Authentication answers "who signed this?" Trust answers "do I care?"

The **trust matrix** is each user's local, derived view of how much they trust every other entity in their graph, broken down by domain. It is not canonical graph data — it's *computed* from relations, attestations, and certs, then cached locally for performance. Think of it like a database index: derived from source data, rebuilt when needed, but essential for fast decisions.

### Where It Lives

The trust matrix is a cached component on the user's own item — a table of `(subject, domain) → score` entries. It is:

- **Local** — never synced, never shared (your trust is your business)
- **Derived** — recomputed from attestation relations, cert data, interaction history, and policy
- **Cached** — persisted for performance, invalidated when source relations change
- **Per-user** — Alice's trust matrix and Bob's trust matrix are entirely independent views of the same underlying graph

### Attestation Edges

Trust is built from **attestation relations** — signed assertions about how much you trust someone in a specific domain:

```
(alice) → trusts → (bob)
  weight: 0.82        # strength (0.0 to 1.0)
  confidence: 0.76    # how certain you are of this weight
  scope: oaklandItem   # geographic/organizational boundary
  domain: "auto.repair" # what this trust is about
  expiresAt: 2026-08-10T11:22:33Z
  signed by: alice
```

This is an ordinary relation — `subject → predicate → object` — signed by its author. The signer often *is* the subject (you assert your own trust), but the signature is separate from the relation shape. These are first-class graph data: queryable, versionable, revocable. The trust matrix is computed by walking these edges.

### Trust Dimensions

Trust isn't a single number. The trust matrix tracks multiple dimensions per subject:

| Dimension | What It Measures |
|-----------|-----------------|
| **Identity** | Cryptographic binding strength — key verification, cert chains |
| **Competence** | Domain-specific: do I trust this person's judgment about *this topic*? |
| **Moderation** | How often their content verdicts align with mine |
| **Reciprocity** | Net balance of favors — storage, bandwidth, compute |
| **Availability** | Uptime, connection success rate |
| **Safety** | Risk assessment — spam history, abuse reports from trusted peers |

A single composite score can be derived from these dimensions via policy-defined weights, but the dimensions are tracked separately so policy can reason about them independently. You might trust someone's taste in music (high competence/music) but not their political moderation judgments (low moderation alignment).

### Trust Path Computation

Direct attestations give you first-hop trust. For entities you haven't directly attested, trust is computed by walking paths through the attestation graph:

```
alice —trusts(0.9)→ dana —trusts(0.8)→ bob
```

**Path scoring:**

```
score = weight₁ × weight₂ × ... × weightₙ × hop_decay(n) × time_decay(age)
```

Where:
- **Hop decay** (λ = 0.7 per hop): trust attenuates with social distance
- **Time decay**: exponential half-life (e.g., 6 months) — older attestations count less
- **Scope intersection**: each hop's scope must overlap; path trust is limited to the intersection
- **Top-K paths**: Dijkstra-like search finds the strongest N paths, not just one
- **Diversity bonus**: paths through independent neighborhoods score higher (defeats collusion)

**Example**: "You trust Dana 0.9; Dana trusts Bob 0.8 (scope: Oakland, expires Dec 2025) → 0.9 × 0.8 × 0.7 hop decay = **0.504**"

The trust matrix caches these computed scores. When attestation relations change, affected entries are invalidated and lazily recomputed.

### Explainability

Trust decisions must be explainable. When the system hides content or rejects a sync request, the user can ask *why* and see the 1–3 strongest trust paths that produced the score. This is not a black-box algorithm — it's a graph walk over signed relations that the user can inspect, dispute, or override.

---

## Reactions and Moderation

Social media platforms have "likes," "reports," and "moderation queues" as separate subsystems, each with their own databases and review workflows. In Common Graph, all of these are just **signed relations** — and they compose naturally because relations can target other relations.

### Reactions as Relations

A "like" is a signed relation:

```
(post) → liked_by → (alice)     signed by: alice
```

So is a "funny," an "insightful," a "misleading" — any sememe can be a reaction predicate. The relation is `subject → predicate [→ object]`, and the signature tells you who asserted it. These are first-class graph data: queryable by anyone who can see them, filterable by trust policy.

**Per-author uniqueness**: a sememe facet (`author_in_identity: true`) can enforce that each author gets at most one reaction of a given type per target — preventing spam-likes without a central rate limiter.

**Claim deduplication**: when many users react to the same item with the same predicate, the system can represent this as one claim item with many annotation relations, rather than N independent relations. 100 likes = 1 claim + 100 annotations. High-cardinality reactions can roll up into aggregate counts.

### Relations on Relations

The key architectural insight: **relations can target the RIDs of other relations.** This enables moderation without any special moderation subsystem.

```
R1: (picture) → has_comment → (tony's comment)     signed by: tony
R2: R1 → labeled → spam                            signed by: jane   { reason: "obvious scam" }
R3: R2 → endorsed_by → (susan)                     signed by: susan  { note: "agreed, clear spam" }
R4: R2 → disputed_by → (bob)                       signed by: bob    { note: "it's a real comment" }
```

Jane marks Tony's comment as spam. Susan endorses Jane's moderation. Bob disputes it. All of these are ordinary `subject → predicate [→ object]` relations — the signature tells you who asserted each one. Attributable, auditable, and subject to trust evaluation. There is no "moderation queue." There is no "appeals process." There is the graph, and everyone's local trust matrix producing a different view.

This is how you "mark someone's like as spam." If a bot farm floods a post with likes, a handful of trusted community members can mark those likes (by targeting their RIDs) as spam — and anyone whose trust matrix scores those moderators highly will stop seeing the bot likes in their counts.

### Policy-Driven Views

Moderation outcomes are **computed, not stored**. A trust policy might say:

```
hide if ≥ 2 spam labels from users with trust ≥ 0.7
```

When you view an item, the policy engine:
1. Loads moderation relations targeting that item's content
2. Scores each moderator's label by their trust in *your* matrix
3. Applies the threshold rule
4. Shows, hides, or grays the content accordingly

Different users see different results from the same underlying data. A community of researchers and a community of conspiracy theorists can coexist in the same graph — each seeing the reactions and endorsements of the people they trust, without a platform making editorial decisions for either of them.

This is fundamentally different from centralized moderation. Twitter/X decides what's "misinformation" for 500 million people. Common Graph lets 500 million people each decide for themselves, informed by the people they actually trust. The graph doesn't have opinions. You do.

### Counter-Attestations

Negative attestations (marking something as spam, disavowing a relation) have a higher evidence bar by convention. Trust policies can require stronger confidence or more endorsements for negative assertions than positive ones, preventing trivial censorship while still allowing communities to self-moderate.

### Moderation Labels

Moderation uses sememe predicates — not a hardcoded set of labels:

- `spam`, `abuse`, `offtopic`, `nsfw`, `harassment`, `misleading`, ...

Any community can define new moderation sememes. A cooking forum might have `off-recipe`. A code review community might have `untested`. These are just sememes — vocabulary, not platform features.

---

## Trust Policies as Items

Trust policies are themselves Items — inspectable, shareable, forkable, versionable:

```
TrustPolicy {
    thresholds: [ThresholdRule]    # n-of-m quorum rules
    introducers: [IntroducerRule]  # who can vouch for whom
    scopes: [ScopeConstraint]     # domain/geographic limits
    decay: DecayFunction           # time and hop decay parameters
    dimensions: [DimensionWeight]  # how to combine trust dimensions
    moderation: [ModerationRule]   # content filtering thresholds
}
```

You can:
- Share your policy with others ("use my moderation settings")
- Fork and modify someone else's policy
- Version and track changes to your policy
- A community can publish a recommended policy that members adopt

### Thresholds (n-of-m)

```
require { scope: "atDomain", purpose: sign, quorum: { marginal: 2, full: 1 } }
```

Like PGP's "2 marginal or 1 full" pattern, but expressed as policy data, not hardcoded.

### Introducer Roles

Some signers can introduce others:

```
<issuerKey> → isIntroducerFor → literal(true) { scope: "atDomain", depth: 2 }
```

Your policy accepts paths where each hop is permitted by the issuer's introducer scope and depth.

### Scopes

Trust is scoped to purposes:

```
scope: "atDomain:example.org"
scope: "group:RainbowOps"
scope: "host:~host/sisko"
scope: "domain:auto.repair"
```

Scope intersection along a trust path means trust doesn't leak across domains. High trust in someone's cooking expertise doesn't translate to high trust in their medical advice.

---

## Sybil and Collusion Resistance

Any trust system must deal with fake identities (Sybils) and coordinated manipulation. Common Graph uses several defenses:

- **Diversity bonus**: endorsements from independent neighborhoods (few shared peers) score higher than endorsements from tightly clustered nodes. Five likes from five people who all know each other are worth less than five likes from five independent communities.

- **Social-distance penalty**: trust decays faster beyond hop 2 unless edges come from high-reputation anchors. This limits the blast radius of Sybil farms — creating 1000 fake nodes doesn't help if none of them are within 2 hops of anyone real.

- **Stake/friction**: communities can require proof-of-work, time delays, or deposits to mint attestations. This raises the cost of automated spam without penalizing genuine participants.

- **Counter-attestation**: marking spam as spam is itself a signed, trust-weighted relation. A Sybil farm's likes can be collectively marked as spam by a handful of trusted moderators, and the marking propagates through trust paths.

- **Local evaluation**: because each user computes their own trust matrix, there is no single point to attack. Fooling Alice doesn't fool Bob unless Bob trusts the same nodes Alice does.

---

## The Favor Economy

Trust isn't only about belief — it's also about reciprocity. Nodes that store data, relay messages, and serve queries for each other build up a ledger of favors that feeds back into trust.

### Service Proofs

When node A does work for node B (stores a block, relays a message, answers a query), A issues a **ServiceProof** — a signed record of the work performed:

```
ServiceProof {
    provider: ItemID     # who did the work
    beneficiary: ItemID  # who benefited
    service: Sememe      # what kind (storage, relay, query)
    amount: Quantity     # how much (bytes, messages, queries)
    timestamp: instant
}
```

### ThanksCerts

The beneficiary periodically issues a **ThanksCert** — an aggregated, windowed acknowledgment of favors received:

- **One per window** per issuer→recipient (prevents spam)
- Aggregates many ServiceProofs via Merkle root
- Both parties can present their side if disputed
- Optional **spot checks**: any peer can request evidence for a random subset

### Reciprocity Score

The trust matrix tracks a `reciprocity` dimension derived from the net balance of service proofs and thanks certs over time. This feeds peering decisions:

- **Generous peers** get priority routing and higher courtesy limits
- **Freeloading peers** get deprioritized — not blocked, but served last
- **New peers** get a small "courtesy lane" derived from their broader reputation

This isn't a cryptocurrency or a token economy. There's no global ledger. It's a local accounting of "who has helped me and who hasn't" that naturally incentivizes cooperation without requiring it.  This intended to model real social structures.

---

## Trust Layers

The trust network isn't one monolithic thing — it's layered:

| Layer | What It Tracks | Feeds Into |
|-------|----------------|------------|
| **Key trust** | KeyLog, certs — "Is this key valid for this identity?" | Authentication ([authentication.md](authentication.md)) |
| **Competence trust** | Domain-specific attestations — "Do I trust their judgment about X?" | Content evaluation |
| **Service trust** | Reciprocity — "Have they been a good peer?" | Routing, peering |
| **Moderation trust** | Alignment — "Do their moderation verdicts match my values?" | Content filtering |
| **Attention trust** | Whose feeds I follow, whose reactions I weight | Discovery, ranking |

Each layer is built from the same primitives:
- **Relations** (signed assertions)
- **Policy** (how to evaluate those assertions)
- **The trust matrix** (cached result of that evaluation)

---

## Practical Trust Decisions

**"Do I trust this relation?"**
1. Who signed it?
2. Do I trust their key (via KeyLog + policy)? → [authentication.md](authentication.md)
3. What's their trust score in my matrix for this domain?
4. Is the scope appropriate?
5. How old is the attestation?

**"Do I show this content?"**
1. Who created it?
2. Are there moderation labels from people I trust?
3. Does the aggregate score cross my policy threshold?
4. Show / gray / hide accordingly

**"Should I run this code?"**
1. Who signed the Code Item (ScriptComponent or BytecodeComponent)?
2. What is their trust score in the relevant domain?
3. What kind of code is it? (sandboxed script = medium threshold, compiled bytecode = high threshold)
4. Does my policy permit this language/runtime from this trust level?
5. Apply resource limits proportional to trust: higher trust → broader capabilities
6. Run / sandbox / refuse

See [Scripting](scripting.md) for the full code execution trust model, including `GraphClassLoader` for bytecode delivery.

**"Should I sync with this peer?"**
1. Service trust: have they been reliable?
2. Reciprocity: are they freeloading?
3. Safety: any abuse flags from my trusted network?
4. Accept / deprioritize / refuse

**"Whose reactions do I weight?"**
1. Walk trust paths from me to the reactor
2. Score by competence in the relevant domain
3. Apply diversity bonus (independent endorsements worth more)
4. Aggregate: show weighted counts, not raw counts

See [Network Architecture](network.md) for how trust integrates with discovery and routing.
