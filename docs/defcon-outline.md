# Below the Application, Above the Bytes

## A Base Layer for Meaning and Ownership

**DEF CON 34 Talk Outline -- 45 minutes**
**Speaker: Joshua Chambers**

---

### Talk Summary

Meaning is absent from every layer of the modern computing stack, and the software that interprets your data has migrated onto machines you do not control.  This talk presents a base layer that closes both gaps: a shared semantic commons and a local-first peer-to-peer substrate, unified in a single architecture.  The semantic primitive is the frame, drawn from computational linguistics, where keys are grounded meanings rather than strings.  The substrate is a peer network of local runtimes holding content-addressed, cryptographically signed assertions, finally realizing and vastly expanding on the web of trust that PGP envisioned thirty years ago that never really took hold.  In the resulting architecture, applications become interchangeable interfaces, moderation is social rather than corporate, onion routing is a per-frame policy rather than a separate network, a phone call is just a frame, and the spam arms race dissolves.

A full paper (~30 pages) accompanies this talk and will be available in print at the session.

---

## I. The Two Gaps (5 minutes)

The opening names the structural cause of platform lock-in.  This audience already lives inside the problem; the goal is to frame it precisely enough that the solution feels inevitable.

### A. The semantic void (2 minutes)

   1. No layer of the computing stack has any concept of what data means.
      a. Filesystem: bytes at paths, maybe an few characters of extension.
      b. OS: processes and memory pages.
      c. Network: packets with addresses.  HTTP adds content-type headers — *format*, never *meaning*.
      d. Database: rows and columns, schema local to the application.
      e. Web: pages at URLs.  Search engines exist because the web cannot answer "what is this about?"
      f. Each layer achieves generality the same way: treat data as opaque, leave interpretation to the layer above.
   2. Meaning lives exclusively in application code.
      a. The application's schema is the only route from bytes to anything a user cares about.
      b. Whoever holds the code that interprets the data holds the thing users actually value.
      c. Data without the application is bytes without value.
   3. The cost is everywhere and attributed nowhere.
      a. Every API integration: bespoke translation between systems that cannot describe themselves to each other.
      b. Every search engine: probabilistic compensation for the fact that data doesn't know what it means.
      c. Every data migration: reconstructing meaning that was in the creator's head but never in the infrastructure.
      d. LLMs are the most expensive compensation yet: statistical models trained on human text to recover meaning that could have been recorded directly.
   4. The key-value fragmentation.
      a. `author`, `creator`, `created_by`, `dc:creator`, `writtenBy` — all the same thing, nothing connects them.
      b. And one of the great unsolved problems of the digital age: how to store a physical address.  [pause]  `address1`, `address2`, `city`, `state`, `zip` — falls apart for Japan (prefecture, district, block), Germany (number after the name), rural areas (no street numbers at all).  Decades of software engineering.  Still unsolved.  [knowing laughter]  The problem isn't the schema.  The problem is that each component of an address is a *meaning*, and no layer can represent meanings.

### B. The SaaS migration (2 minutes)

   1. The hardware is not the problem.
      a. The phone in your pocket exceeds 1970s mainframes in every dimension.
      b. Nearly all of that power is spent rendering what a server farm computes.
      c. The most capable consumer hardware ever built serves as little more than rendering terminals.
   2. The business model is the problem.
      a. Applications migrated to hosted services not because of a technical limit but because subscriptions, network effects, and centralized data created lock-in.
      b. A generation of developers grew up with SaaS as the default mental model rather than the historical anomaly it is.
   3. Running the computation is how you own the decisions.
      a. Recommendation order, feed curation, content moderation, search ranking, fraud detection, A/B testing, price discrimination — all happen on servers.
      b. Inside code users cannot inspect, on data users cannot access, subject to policies users did not agree to.
      c. Platforms own not just the meaning of data but the agency over it.

### C. The compound problem (1 minute)

   1. Fixing only one gap is insufficient.
      a. Semantic data trapped on servers is no more portable than opaque data on servers.
      b. Local compute on opaque data is no more useful than remote compute on opaque data.
   2. Both gaps must close in the same layer, or neither closure is effective.
   3. Transition: "What's missing is not a better search engine, a different platform, or a smarter parser.  What's missing is a layer where meaning is the fundamental unit, and that layer lives on hardware you control."

## II. Why Retrofits Fail (4 minutes)

Quick pass through the pattern of failure.  This audience knows these systems.  The point is the *structural lesson*, not the individual stories.  Hard copies of the paper have the full treatment.

### A. The semantic retrofits (1.5 minutes)

   1. **The Semantic Web** (Berners-Lee, 2001).
      a. Rigorous, powerful, adopted in narrow domains.
      b. Three structural problems:
         i. Annotates existing resources — optional, so absent in practice.
         ii. Requires ontology expertise from every author.
         iii. Annotation is disconnected from the content it describes.
      c. The incentive is misaligned: cost falls on the producer, benefit accrues to the consumer.
   2. **Schema.org, Dublin Core, EXIF, ID3, OpenGraph**: each solves a narrow problem, they do not compose.
   3. **The lesson**: you cannot make a semantically inert layer semantic by annotating it.

### B. The locality retrofits (1.5 minutes)

   1. **Federation** (Fediverse, Solid, AT Protocol, Matrix).
      a. Distributes the servers but preserves the client-server boundary.
      b. The instance operator is still a gatekeeper.
      c. The problem was never the number of servers; it was the architectural privilege of being one.
   2. **P2P transport** (FreeNet, BitTorrent, IPFS, SSB).
      a. Solves the real problem of moving data without a central coordinator.  Genuinely well.
      b. BitTorrent's DHT has operated with tens of millions of nodes.  Foundational contributions: content addressing, DHTs, incentive-compatible chunk exchange, append-only signed logs.
      c. But: hash-distance routing treats all data as homogeneous and all peers as interchangeable.
         i. A peer holds data because of a mathematical property of its ID, not because it cares.
         ii. Costs compound at scale: O(log N) lookup latency (30 round-trips for a billion nodes), nodes storing data they have no interest in, constant churn maintenance, phones and servers treated as equivalent.
         iii. More fundamentally: hash-distance routing consumes the flexibility you'd need to route different data to different people based on who cares about it.
      d. A chess move matters to the players.  A photograph matters to the people depicted and those that know them.  A medical record matters to the patient and their doctor.
   3. **Git**: technically fully distributed, culturally centralized around GitHub.  Technical decentralization is necessary but not sufficient.
   4. **The lesson**: federation does not remove the client-server boundary.  P2P transport routes all data as though it is the same and all peers as though they are interchangeable.

### C. The common diagnosis (1 minute)

   1. Additions cannot compensate for a substrate whose shape is wrong.
   2. The solution must be a *layer* where creating data simultaneously creates semantic structure and places it on a user-controlled peer.
   3. Transition: "So let me show you the primitive, and then the substrate that carries it."

## III. The Semantic Frame Primitive (10 minutes)

This is the core of the talk.  The single data structure from which everything else is built.

### A. What a frame is (3 minutes)

   1. The semantic void exists because keys are strings.  The fix requires keys that refer to *meanings*.
      a. Decades of computational linguistics have produced the raw material: WordNet (120,000 catalogued meanings), the Collaborative Interlingual Index (cross-lingual links), FrameNet and VerbNet (structured role declarations), ISO 24617-4 (standardized role inventory).
      b. These resources were not available when earlier attempts were made.
   2. The frame primitive.
      a. A **predicate** (a grounded meaning: what kind of assertion) and **bindings** (compound-key → value pairs: the semantic content).  Nothing else is structurally required.
      b. The predicate is a sememe — a unit of meaning from the shared vocabulary — acting in a structural role.  It declares what bindings the frame expects.
      c. This is NOT a key-value pair with extra structure.  It is a coherent assertion whose roles are determined by its predicate.  A title requires something being titled and the title itself.  An authorship assertion requires a work and an author.  A chess move requires a game, a player, a piece, an origin, and a destination.  Remove any of them and the assertion does not make sense — the way "John gave" does not make sense without knowing what and to whom.  The predicate determines what roles are needed; the roles are not arbitrary.
      c. The same vocabulary supplies everything: predicates (AUTHORED, TITLE, MOVE), archetypes (BOOK, CHESS_GAME, PERSON), and binding values (TOLKIEN, ENGLISH, CELSIUS).  They are all meanings.
   3. Compound keys.
      a. Each binding key is a sequence of one or more grounded meanings.
      b. `(VALUE, ENGLISH)` vs. `(VALUE, RUSSIAN)` — the language qualifier distinguishes translations.
      c. `(VALUE, TEMPERATURE, CELSIUS)` — a temperature reading.  Every element is a sememe.
   4. Cross-lingual stability is built in, not bolted on.
      a. Meanings are language-independent (sememes).  Words are language-specific (lexemes) that point at them.
      b. The concept DOG exists independently of "dog," "perro," and "犬" (inu).
      c. The sememe AUTHORED encompasses all forms of the meaning: "author," "authored," "authoring," "authorship" — those are all lexemes pointing at one sememe.  And that same sememe exists across languages: "escrito" in Spanish, "verfasst" in German.  One meaning, many words.
      d. A Spanish speaker sees the same data through Spanish lexemes.  No translation occurs.
   5. Write-time resolution.
      a. Meaning is resolved at the moment of creation, when the creator knows what they mean.
      b. The disambiguation that search engines and NLP pipelines struggle with after the fact is effortless at write time.
      c. The hardest problem in NLP becomes trivial when you ask the person who knows what they mean.

### B. Body and record (1 minute)

   1. Each binding carries independent flags:
      a. **Identity**: does it affect the content hash?
      b. **Index**: should it create a reverse-lookup entry?
   2. A frame has two layers:
      a. **Body**: the assertion itself — predicate and identity-flagged bindings.  Its content hash is the frame's identity.  The same assertion always produces the same hash, regardless of who signs it.
      b. **Record**: the signing envelope — signature, signer's key ID, timestamp, and any non-identity bindings (CONFIG policies, presentation hints, per-signer data).
   3. This separation matters: the same fact attested by ten people is recognizably the same assertion, signed ten ways.  Why that matters will be clear when we get to items.

### C. Live examples (3 minutes)

   Show each on screen.  The audience should feel the structural identity across domains:

   1. **A title**: `TITLE { (THEME) = the-book, (VALUE, ENGLISH) = "The Hobbit" }`.
      a. A separate frame carries the Russian title: `TITLE { (THEME) = the-book, (VALUE, RUSSIAN) = "Хоббит" }`.
      b. Each is independently signed — a translator does not need the original author's key.
   2. **A chess move**: `MOVE { (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4 }`.
      a. Location (which game), Agent (who moved), Theme (what piece), Source (from where), Goal (to where).
      b. Signed by the player who made it.
   3. **A mathematical expression**: `ADD { (THEME) = 3, (INSTRUMENT) = 5 }` evaluates to 8.
      a. `INTEGRATE { (THEME) = x², (SOURCE) = 0, (GOAL) = 1, (INSTRUMENT) = dx }` evaluates to 1/3.
      b. Theme, Instrument, Source, Goal — the same roles used for natural language.
      c. They map onto math because they are cognitive structuring principles, not linguistic artifacts.  "Add 5 *to* 3" — the preposition tells you which is which.
   4. **A phone call**: `CALL { (AGENT) = caller, (RECIPIENT) = callee, (VALUE, AUDIO) = <stream>, (CONFIG, RETENTION) = PRESENCE }`.
      a. Same primitive.  The call is a signed assertion; the audio is content in a VALUE binding.
      b. When the call ends, PRESENCE retention discards it — unless both parties agreed to record.
   5. **An address** [callback to the earlier laugh]:
      a. US: `ADDRESS { (VALUE, STREET_NUMBER) = "742", (VALUE, STREET_NAME) = "Evergreen Terrace", (VALUE, CITY) = Springfield }`.
      b. Japan: `ADDRESS { (VALUE, PREFECTURE) = Tokyo, (VALUE, DISTRICT) = "神宮前", (VALUE, BLOCK) = "1" }`.
      c. Same predicate, different bindings.  Each component is a meaning.  The great unsolved problem: solved by compound keys.
   6. Emphasize: all structurally identical.  A predicate and role bindings.  The same primitive describes a book title, a chess move, a definite integral, a phone call, and a physical address.

### D. Queries, indexing, and CONFIG (3 minutes)

   1. **Queries are incomplete frames.**
      a. `AUTHORED { (AGENT) = Tolkien }` with no THEME asks "what did Tolkien author?"
      b. `MOVE { (LOCATION) = the-game }` asks "what moves in this game?"
      c. `DEPICTS { (ANY) = Alice }` asks "what depicts Alice?" — ANY is a sememe meaning "any role."  Photographs, drawings, movies, anything with a DEPICTS frame that references Alice in any binding.  You don't need to know which role she fills; you just ask for her.
      d. Bindings can hold expressions: `CAPTURED { (TIME) = BEFORE { (VALUE) = 2020 } }` asks "what was captured before 2020?"  The sub-frame BEFORE is itself a frame with its own predicate and bindings.
      e. No SQL, no SPARQL, no GraphQL.  The frame IS the query, the shared vocabulary IS the schema, and the compound-key index IS the query engine.
   2. **Compound keys are the index.**
      a. Every meaning in a compound key is an indexing opportunity.
      b. "All videos" = lookup on frames with VIDEO in their keys.  "All UHD videos" = narrow to VIDEO and UHD.
      c. No tagging system, no search facets.  The key *is* the index.
      d. Not every binding is indexed — image bytes, pixel coordinates, policy settings carry no useful reverse-lookup value.  The archetype provides defaults.
   3. **No data/metadata distinction.**
      a. A title's text, a video's file, a chess move's destination square, provenance, signatures, timestamps: all role bindings.
      b. The distinction is conventional, not structural.
   4. **CONFIG is cross-cutting policy.**
      a. Any frame can carry CONFIG bindings governing how it is handled.
      b. Retention: keep all, keep only the latest, keep the chain.
      c. Routing: direct, relayed, onion-routed.
      d. Replication, encryption.
      e. A chess move is retained permanently.  An avatar position is discarded when a newer one arrives.  A video feed frames to a content stream whose blocks are consumed and released.
      f. The difference is a CONFIG binding, not a separate mechanism.

## IV. Items, Identity, and the Social Graph (6 minutes)

The locality requirements (cryptographic identity, content-addressing, social-graph routing, local execution) are introduced here as they are satisfied.

### A. What frames cohere around (2 minutes)

   1. A single frame is rarely the whole story.
      a. A book has TITLE, AUTHORED, TEXT, COVER_ART, PUBLICATION frames.  All about the same thing.
      b. An **item** is a stable anchor that frames reference.  The book is an item.  Tolkien is an item.  A chess game is an item.  A conversation between you and a friend is an item — your shared space where calls, messages, and reactions happen.
   2. **Item ID (IID)**: stable, location-independent, not assigned by any registry.
      a. The same IID is recognized by any peer, on any device, without coordination.
      b. This is content-addressing applied to identity: the item is named by what it is, not where it lives.
   3. **Manifests and versions.**
      a. The set of frames an item endorses is recorded in its **manifest** — a signed list.
      b. The manifest's hash is the **version ID (VID)**.
      c. A manifest endorsement always includes the frame body (content hash).  It CAN also pin a specific record (signing envelope), but the body is the required part.
      d. This is where body/record separation pays off: the manifest endorses *what was said* without necessarily caring *who said it*.
      e. New frames → new manifest → new VID.  IID stays stable.  Version history is a directed graph, structurally similar to Git commits.  Fork and merge work the same way.
   4. **Archetypes.**
      a. What makes a book a book?  BOOK is a sememe acting as an **archetype** — it declares what frames an item of its kind is expected to endorse.
      b. The declaration is *open*: BOOK says "expect TITLE, AUTHORED, TEXT."  Nothing prevents someone from signing a LIKE, a review, a citation, a fact-check.
      c. The archetype defines the identity; it does not gatekeep what others may say.

### B. Cryptographic identity and the social graph (2 minutes)

   1. **A user is an item.**
      a. Not an account, not a username, not a row in a database.  A user is an item, the same primitive as a book or a chess game, described by frames (NAME, AVATAR, PUBLIC_KEY), referenced by other frames (every AGENT binding that names you), versioned through manifests.
      b. Identity is a keypair.  You generate it locally.  No authority grants it; no authority can revoke it.
      c. Your Librarian — the local runtime that stores your items, signs your frames, and manages your peer connections — is itself an item.
      d. PGP established keypair identity for email thirty years ago.  This layer takes the core insight — identity without a central authority, trust as a web of signed endorsements — and builds it into the foundation of the entire architecture, where trust drives not just key verification but routing, moderation, code execution, discovery, and replication.
   2. **Every assertion is signed.**
      a. Provenance and integrity are properties of the primitive, not features added later.
      b. Content-addressing means integrity is verifiable locally — you check the hash, not the transport.
   3. **The social graph organizes the network.**
      a. Data lives with peers who care about it, travels along trust relationships, surfaces through shared connections.
      b. Contrast with hash-distance P2P: you hold data because you *chose to*, not because a hash function said you should.
      c. Human social networks exhibit small-world properties (Milgram, Watts & Strogatz): most pairs connected through a handful of hops.  The efficiency is empirical, not algorithmic.

### C. Routing privacy at any granularity (2 minutes)

   1. Because data travels through chosen trust relationships, routing privacy is a natural property.
   2. Routing policy attaches at whatever granularity fits:
      a. A single frame, an item, a session, a user's entire Librarian.
      b. These compose: a frame inherits its item's posture unless it carries its own.
   3. Configurations of one primitive:
      a. **Direct connection**: the default.  Peer to peer, no intermediary.
      b. **Relay through a trusted peer**: structurally identical to a VPN.  The VPN service is just a Librarian that relays messages.  One primitive; the $50 billion VPN industry becomes a commodity relay market.  [let that sink in]
      c. **Multi-hop with layered encryption**: onion routing.  Each intermediate peer decrypts enough to know where to forward, sees neither origin nor destination.  Tor's capability, as a configuration, not a separate network.
      d. **Mix networks**: batching and timing policies at each hop for traffic-analysis resistance.
   4. VPN, Tor, proxy, mix network — not different systems.  Configurations of one routing primitive, governed by the same trust relationships.
   5. Local execution.
      a. The runtime lives on your machine.  Your device is a full participant, not a renderer.
      b. Remote computation is an explicit delegation, not the default.

## V. The Trust Matrix (5 minutes)

Where the security audience should feel most at home.

### A. Assessments are frames (1.5 minutes)

   1. Every reaction, moderation action, and endorsement is a semantic frame signed by an identified party.
      a. A FUNNY frame asserts something is funny.  A SPAM frame asserts something is spam.
      b. The predicate carries the meaning — these are not generic "reactions."
   2. Frames can target other frames, not just items.
      a. A SPAM frame targeting another frame: "I assert this assertion is spam."
      b. A DISAGREE frame targeting a SPAM frame: "I disagree with that moderation call."
   3. Semantic reactions cluster naturally in the vocabulary hierarchy.
      a. FUNNY, HILARIOUS, AMUSING under HUMOR.
      b. SPAM, ASTROTURF, JUNK under another branch.
      c. The clustering is structural, not engineered.

### B. Trust is computed, not declared (1.5 minutes)

   1. Trust emerges from accumulated assertions.
      a. Alice consistently reacts to Bob's content with INSIGHTFUL and AGREE → her Librarian computes trust in Bob's judgment from the pattern.
      b. Carol's Librarian reliably relays messages → Alice computes infrastructure trust from operational history.
      c. The trust is the pattern; the pattern is the data.
   2. Trust is a **matrix**: multi-dimensional, per-domain.
      a. Trust Alice's music taste without trusting her political judgment.
      b. Trust Bob's relay infrastructure without trusting Bob's content.
      c. Even identity verification is one dimension among many.
   3. Each Librarian computes its own matrix **locally**.
      a. No global reputation score.  No universal ranking.
      b. Two users viewing the same content may see different things.
      c. Szabo's (1997) vision: overlapping views, not a single view imposed by a platform.
      d. The vision has never lacked advocates — the Rebooting the Web of Trust community has spent a decade on decentralized identity — but it has lacked a substrate where trust is the organizing principle rather than a bolt-on.

### C. Moderation without moderators (1.5 minutes)

   1. Scenario:
      a. I mark your posts as SPAM.  My trust in your content decreases.
      b. James disagrees, marks my SPAM frames with DISAGREE.
      c. Others weigh in.
   2. Outcome:
      a. Users who trust my moderation: your posts disappear.
      b. Users who trust James: they survive.
      c. No moderator appointed, no appeals board, no single outcome imposed.
   3. **Transitive trust**: the strongest form.
      a. Alice, Bob, and I independently rate the same restaurants positively → our Librarians compute overlapping taste from convergent assertions about the same targets.
      b. Cannot be faked without faking the underlying reactions.
   4. Trust algorithms are themselves items that can be replaced with alternatives.

### D. Bootstrapping (0.5 minutes)

   1. How does a new user get started if trust is required?
   2. The same way any social system works: you arrive through someone you know.
      a. A friend who peers with you.
      b. A community node that accepts newcomers.
      c. A public gateway.
   3. Trust starts small and grows through interaction.

## VI. Code Trust and Supply Chains (4 minutes)

### A. Code is an item (1.5 minutes)

   1. If data is frames and frames are signed, what about code?
   2. Code is an item carrying executable form alongside frames declaring:
      a. Which contract it satisfies — a predicate's contract (how to evaluate it) or an archetype's contract (how to render and interact with items of that type).
      b. Who signed it.
      c. What runtime is needed.
   3. A predicate carries a *contract* — what it expects, what it produces.
      a. The code that satisfies the contract is a separate item.
      b. The link between them is a frame.
      c. A predicate can have many implementations: different runtimes, different trade-offs, different authors.
   4. A runtime evaluating a frame picks an implementation it can execute *and whose author it trusts*.

### B. The supply-chain argument (2.5 minutes)

   [This will resonate with this audience.]

   1. All code distribution already relies on social trust.
      a. Google Play Store: you trust Google's review.
      b. `apt install`: you trust the Debian maintainers.
      c. App Store: you trust Apple.
      d. All of this is "social" in a very real sense.  But it is *implicit* social trust.  You never consciously chose to trust Google's review process or Debian's maintainer vetting.  You can't inspect those trust relationships, can't see their track records, can't choose to trust some links in the chain and not others.  The trust is invisible and all-or-nothing.
   2. SolarWinds and the xz backdoor demonstrated that centralized, implicit trust intermediaries are not immune.  SolarWinds: attackers compromised the build system and shipped a backdoor through the official, signed update channel.  xz: an attacker spent two years earning maintainer trust, then inserted a backdoor weeks from shipping in every major Linux distribution.
   3. What changes: not *whether* execution depends on trust, but whether that trust is implicit or explicit.
      a. In CG, every link in the chain is a signed, visible, assessable relationship.  Who signed the code, who reviewed it, who endorsed the reviewer, what their track record looks like across the trust matrix.
      b. The trust is still social — it has to be — but it is explicit, inspectable, and granular rather than invisible, opaque, and binary.
   4. Nothing prevents Google or Apple from publishing signed code items.
      a. They stand alongside every other reviewer rather than occupying a privileged gatekeeping position.
   5. **Sandboxing** is required.
      a. Same kind of hard as browser JavaScript sandboxing.
      b. Capability-based interfaces, isolated execution, formal verification.
   6. **Key compromise** remains the hardest unsolved problem.
      a. No software substrate fully solves device-level threats.
      b. **Keymaster** (github.com/joshualibrarian/keymaster): open hardware for key storage, designed to resist device compromise and coercion.
   7. The structural consequence: most software became a service because the operator held both code and data.  When both travel through the same peer substrate, that rationale dissolves.

## VII. Sanity Check (5 minutes)

"I know what you're thinking.  Does this actually work at scale?  And what does the attack surface look like?"

### A. Does it scale? (3 minutes)

   1. **The data is still the data.**  A photograph is the same megabytes whether it's stored as a file or as a frame binding.  CG doesn't make data bigger; it makes it meaningful.  The overhead of wrapping content in frames and items is trivial — a few hundred bytes of structure around content that may be megabytes or gigabytes.  The scaling question is really about the *indexing*, not the data itself.
   2. **Indexing is compact and linear.**
      a. Each indexed binding: 136 bytes (102-byte key + 34-byte value).
      b. Not every binding is indexed — literals, payloads, policy bindings are skipped.
      c. Cost is linear in semantic assertions, not in content size.
      d. A 1 MB photo and a 10 GB movie with the same six descriptive frames produce the same index cost.
      e. 10,000 photos + 500 books + 1,000 posts = ~170,000 index entries, ~23 MB.
   3. **The power law is your friend.**
      a. Research on 52M+ posts (Buffer, 2026): median engagement is ~4 interactions.
      b. 90%+ of items: trivial cost (a few frames, under a kilobyte of index).
      c. The extreme tail (millions of reactions): a handful of posts per year globally.
   4. **Social-graph sharding for the extreme case.**
      a. Viral post with 5M reactions: ~4.5 GB total across the entire network.
      b. Casual viewer: ~150 KB.  Popular creator: ~40 MB.  Full aggregator: ~4.5 GB.
      c. Each node only holds what reached it through its own trust relationships.  The data distributes itself across the network without anyone designing a sharding strategy.
      d. 4.5 GB is less than a USB stick you'd lose in your couch cushions.
   5. **Distributed counting: HyperLogLog.**
      a. Most items never need aggregation.
      b. For the small fraction that do: mergeable sketches, ~16 KB each, ~2% error, deduplicate across overlapping social graphs.
      c. The sketch is just another frame.
   6. **Text indexing scales linearly.**
      a. Two kinds of text in the token dictionary.  Vocabulary (lexemes from language imports, mapping words to meanings) is effectively bounded — a language has a finite number of words.  ~35 MB for all of English including inflected forms, ~4 MB for Mandarin (no inflections at all).  A polyglot loading five languages: ~100 MB.  Loading every language for which resources exist: single-digit gigabytes.  All routine.
      b. User content: 10M titles (IMDB scale) ≈ ~2 GB of token dictionary.
      c. One tokenizer handles all languages, including CJK, through windowed resolution.
   7. **Signing**: ~50K signs/sec, ~15K verifies/sec.  Not a bottleneck.
   8. **Serialization is binary, not text.**  Frames are encoded in CBOR (Concise Binary Object Representation), not JSON.  No repeated key strings, no whitespace, no quoting overhead.  A frame that would be 500 bytes as JSON might be 150 as CBOR.  At millions of frames, this matters.
   9. All estimates uncompressed.  Storage backends add compression on top of that.

### B. What about attacks? (2 minutes)

   1. **No single point of failure.**
      a. Each user runs an independent Librarian.
      b. Institutional nodes are higher-value targets, but their data is already replicated.
      c. Time-to-impact stretches from seconds (centralized) to hours (distributed).
   2. **The social graph is the firewall.**
      a. No trust path, no connection.
      b. Not a firewall rule — a structural property.
   3. **Sybil-resistant.**
      a. New identities start at zero trust.
      b. Must build relationships through sustained behavior.
      c. Mass-creating identities doesn't help — none have trust.
   4. **Eclipse-resistant.**
      a. Peers are chosen through trust relationships, not assigned by hash distance.
      b. Compromising peers means compromising actual social relationships.
   5. **Frame flooding is self-limiting.**
      a. Every frame is signed and attributable.
      b. Junk degrades the sender's trust score.
      c. The attack burns the trust it depends on.
   6. **The economic shift** [close with this]:
      a. Generating traffic is cheap.
      b. Building fake social trust is expensive, slow, and self-defeating.

## VIII. What Changes (4 minutes)

Concrete consequences.  This is where the vision gets tangible.

### A. Platform subsumption

   1. Product listings, forums, social feeds, reviews, citation graphs: each expressible as items and frames.
   2. Applications become interchangeable interfaces.  Switching costs approach zero.
   3. Economics don't disappear.  Lock-in disappears.

### B. Szabo's smart contracts, realized

   [The crypto-literate audience will light up here.]

   1. Szabo (1997) described bearer certificates, escrow, smart liens, accounting controls, content rights — each as a separate protocol family.
   2. In this architecture, all collapse into one primitive.
      a. A license: a signed frame from a rights-holder.
      b. An escrow: a Librarian holding one frame until a corresponding frame arrives.
      c. A lien: Szabo envisioned a car whose cryptographic keys revert to the bank if you stop making payments.  Whether you'd want to drive that car is another question — but the substrate supports it.  The bank signs a LIEN frame on the vehicle item; the rest is policy.
      d. A transaction: a chain of signed frames (order, confirmation, payment, shipment, delivery) — each party attesting their own role.
   3. He described the protocols; this is the substrate they share.
   4. [If the Szabo/Satoshi speculation comes up, don't force it, but don't avoid it either.]

### C. Real-time communication

   1. A phone call is a CALL frame.  A video meeting is an item with STREAMING frames.
   2. Phone numbers become unnecessary.  The spam arms race stops: no trust path, no ring.
      a. Users who want cold calls lower their threshold.
      b. The trust matrix provides a gradient spoofable phone numbers never could.
   3. Streams flow direct (HOPS=0) or relayed (HOPS=N, IP hidden).  Same frame, different routing policy.
   4. Telephony bridge: a device with your SIM card, running a small Librarian, connecting legacy phones to CG.
      a. Someone calls your number; the bridge wraps it as a CALL frame and forwards through CG.
      b. Your real phone doesn't need a phone number, just data service.

### D. Structural consequences

   1. **Offline capability**: trivial when runtime and data are both local.
   2. **Resilience to vendor disappearance**: items live on users' devices.  Company shuts down; data and tools remain.
   3. **The vocabulary is the commons**: extensible from the edges, no permission required.

## IX. Authorship, Not Ownership (2 minutes)

Honesty about what this does and does not deliver.  This audience will see through overclaiming.

### A. What you get

   1. **Provable attribution**: every assertion carries a cryptographic link to the key that signed it.  You hold your keys, nobody can sign as you.  Attribution is the technical mechanism; authorship is the human concept it stands in for.
   2. **Local custody**: no vendor can revoke access to work you already have.
   3. **Consent to new copies**: you choose which peers you share with.

### B. What you do not get

   1. Ownership in the property-law sense.  Once you give someone a copy, you cannot technically revoke it.
      a. This is a property of copyable information, not a failure of any honest substrate.
      b. Any system that claims otherwise is lying to you.
   2. The partial solution is social: choose whom to share with, and the trust graph responds when trust is violated.
   3. "A more honest vocabulary is authorship, custody, and consent."

## X. Honest Reckoning (3 minutes)

### A. Predecessors and their lessons (1.5 minutes)

   1. **Xanadu**: got content addressing right.  Demanded completeness before shipping.  Lesson: incremental delivery.
   2. **CYC**: got the diagnosis right.  Hand-authoring axioms doesn't scale.  Lesson: anchor in existing resources.
   3. **Plan 9**: technically superior to Unix.  Required abandoning the ecosystem.  Lesson: provide a bridge.
   4. **The Semantic Web**: got the diagnosis exactly right.  Optional means absent.  Lesson: semantics cannot be a separate step.
   5. **Local-first software**: the tradition directly upstream.  Centralized path has been easier.  Lesson: make local-first the easier path.

### B. Why now (1 minute)

   1. **Linguistic resources matured — and they're open.**
      a. WordNet (120K synsets), CILI (cross-lingual), VerbNet (300 verb classes), ISO 24617-4, UniMorph (100+ languages).
      b. Decades of cumulative scholarly work.  Did not exist when CYC or the Semantic Web were proposed.
      c. Critically, these carry permissive licenses.  The vocabulary that makes the semantic pillar possible is freely available to anyone.
   2. **The open-source ecosystem matured.**
      a. Ed25519 cheap per-message, content-addressing ubiquitous (Git), CRDTs in production, P2P transport stacks (libp2p, QUIC) mature.
      b. The entire CG stack is built on available open-source libraries: RocksDB for storage, ed25519 for signing, Filament for 3D rendering, Skia for 2D, JLine for terminal, CBOR for serialization.  Every layer of the implementation draws on battle-tested open-source infrastructure that did not exist or was not mature a decade ago.
      c. A base layer like this is inherently a commons — it only works if shared, and it can only be shared if open.  The open-source movement created the collaborative environment such a commons requires.
   3. **AI compressed the timeline.**
      a. The bottleneck for ambitious software projects has always been the sheer volume of code required, and that bottleneck has narrowed dramatically.  This does not guarantee success, but it changes the economics of ambition.

### C. What's built (0.5 minutes)

   1. Working local runtime (Librarian), frame storage over RocksDB, vocabulary from WordNet/CILI, English and German language imports, working applications (chess, set, minesweeper), Skia 2D and Filament 3D rendering, JLine terminal.
   2. Open source: github.com/joshualibrarian/common-graph.
   3. Hard copies of the paper at the session.

## XI. Close (2 minutes)

   1. Return to the opening: meaning is absent from every layer of the stack, and the applications that hold it run on machines you don't control.  Two gaps.  One layer.
   2. "The path forward is incremental: frames as a local data format; a shared vocabulary seeded from decades of linguistic research; a local runtime that stores, queries, and resolves data by meaning; and a peer-to-peer network where that data is exchanged between nodes connected by trust.  Each step independently useful.  Together, the base layer that computing has been missing since the networked era began."
   3. "The linguistic resources exist.  The cryptographic tools exist.  The engineering infrastructure exists.  The time to build it is now."
   4. Questions.

---

### Supporting Materials

- Full paper: "Below the Application, Above the Bytes: A Base Layer for Meaning and Ownership" (PDF, ~30 pages)
- Source code: github.com/joshualibrarian/common-graph
- Sister project (open-hardware key vault): github.com/joshualibrarian/keymaster
- Hard copies of the paper will be available at the session

### Speaker Bio

[To be filled in by Joshua]

### References

- Barnes, J. A. (1954). "Class and Committees in a Norwegian Island Parish." *Human Relations*.
- Berners-Lee, T., Hendler, J., & Lassila, O. (2001). "The Semantic Web." *Scientific American*.
- Buffer (2026). "The State of Social Media Engagement in 2026: 52M+ Posts Analyzed."
- Bush, V. (1945). "As We May Think." *The Atlantic Monthly*.
- Fillmore, C. J. (1968). "The Case for Case." In Bach & Harms (Eds.), *Universals in Linguistic Theory*.
- Fillmore, C. J. (1982). "Frame Semantics." In *Linguistics in the Morning Calm*.
- Flajolet, P., Fusy, É., Gandouet, O., & Meunier, F. (2007). "HyperLogLog: the analysis of a near-optimal cardinality estimation algorithm." In *AofA 2007*.
- Gruber, T. R. (1993). "A Translation Approach to Portable Ontology Specifications." *Knowledge Acquisition*, 5(2).
- Kleppmann, M., et al. (2019). "Local-First Software: You Own Your Data, in spite of the Cloud."
- Merkle, R. C. (1979). "Secrecy, Authentication, and Public Key Systems." PhD thesis, Stanford University.
- Milgram, S. (1967). "The Small World Problem." *Psychology Today*.
- Szabo, N. (1997). "Formalizing and Securing Relationships on Public Networks."
- Viterbi, A. J. (1967). "Error Bounds for Convolutional Codes and an Asymptotically Optimum Decoding Algorithm." *IEEE Trans. Information Theory*.
- Watts, D. J., & Strogatz, S. H. (1998). "Collective dynamics of 'small-world' networks." *Nature*, 393.
- Youn, H., et al. (2016). "On the universal structure of human lexical semantics." *PNAS*.
- Zimmermann, P. (1995). *PGP Source Code and Internals.*
