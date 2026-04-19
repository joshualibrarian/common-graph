# Below the Application, Above the Bytes

## A Base Layer for Meaning and Ownership

**DEF CON 34 Talk Outline -- 45 minutes**
**Speaker: Joshua Chambers**

---

### Talk Summary

Platform ownership is not a policy problem.  It is a structural property of a computing stack where no layer stores meaning and the software that interprets your data runs on someone else's machines.  This talk presents a base layer that closes both gaps with a single primitive: the semantic frame, drawn from computational linguistics, deployed on a local-first peer-to-peer substrate where cryptographic identity, content-addressed storage, and trust-based routing replace accounts, servers, and platforms.  The result is infrastructure where data is self-describing, applications are interchangeable, moderation is social rather than corporate, onion routing is a per-frame policy rather than a separate network, and a phone call is just a frame.

A full paper accompanies this talk and will be available in print at the session.

---

## I. The Two Gaps (5 minutes)

The opening compresses the paper's diagnosis into a framing this audience already lives inside.  The goal is not to convince them the problem exists but to name the structural cause precisely enough that the solution feels inevitable.

### A. The semantic void

   1. No layer of the computing stack stores meaning.  Filesystem sees bytes at paths.  OS sees processes and memory pages.  Network sees packets.  Database sees rows.  The web sees pages at URLs.
   2. Meaning lives exclusively in application code.  The application's schema is the only route from bytes to anything a user cares about.
   3. Consequence: your data is meaningless without the application that wrote it.  Export gives you bytes and a proprietary schema.  You can leave nominally but not practically.
   4. The key-value pair, the most fundamental composable pattern in computing, is fractured beyond repair: `author`, `creator`, `created_by`, `dc:creator`, `writtenBy` all mean the same thing.  Nothing in the infrastructure connects them.
   5. LLMs are the most expensive compensation yet: statistical models trained on human text to recover meaning that could have been recorded directly.

### B. The SaaS migration

   1. Consumer hardware has never been more powerful.  Phones in 2026 exceed 1970s mainframes in every dimension.  Nearly all of that power is spent rendering what a server farm computes.
   2. Applications migrated from local programs to hosted services not because of a technical limit but because of a business model: subscriptions, network effects, and centralized data created lock-in.
   3. Running the computation is how you own the decisions.  Recommendation, curation, moderation, feed ranking, price discrimination, A/B testing: all happen on servers, inside code users cannot inspect, on data users cannot access.

### C. The compound problem

   1. Semantic data trapped on servers is no more portable than opaque data on servers.  Local compute on opaque data is no more useful than remote compute on opaque data.
   2. Both gaps must close in the same layer, or neither closure is effective.
   3. Transition: "What's missing is not a better search engine, a different platform, or a smarter parser.  What's missing is a layer where meaning is the fundamental unit, and that layer lives on hardware you control."

## II. Why Retrofits Fail (5 minutes)

A quick pass through the pattern of failure, not a detailed history.  The audience knows these systems.  The point is the *structural lesson*, not the individual stories.  Hard copies of the paper will have the full treatment for anyone who wants the details.

### A. The semantic retrofits (2 minutes)

   1. **The Semantic Web**: rigorous, powerful, adopted in narrow domains.  Three structural problems kept it from becoming general-purpose: it annotates existing resources (optional, so absent in practice), it requires ontology expertise from every author, and the annotation is disconnected from the content it describes.
   2. **Schema.org, Dublin Core, EXIF, ID3, OpenGraph**: each solves a narrow problem.  They do not compose.  A photograph with EXIF and a document with Dublin Core cannot be queried together.
   3. **The structural lesson**: you cannot make a semantically inert layer semantic by annotating it.  The annotation is always optional, always disconnected, always maintained by a separate process.

### B. The locality retrofits (2 minutes)

   1. **Federation** (Fediverse, Solid, AT Protocol, Matrix): distributes the servers but preserves the client-server boundary.  Data still lives on servers.  The instance operator is still a gatekeeper.  The problem was never the number of servers; it was the architectural privilege of being one.
   2. **P2P transport** (FreeNet, BitTorrent, IPFS, SSB): solves the real problem of moving bytes between peers without a central coordinator.  Each treats all data as homogeneous.  A chess move, a medical record, a photograph, and a movie are the same kind of thing: opaque bytes to store and retrieve.
   3. **Git**: the most instructive case.  Technically fully distributed; culturally centralized around GitHub.  Technical decentralization is necessary but not sufficient.
   4. **The structural lesson**: federation does not remove the client-server boundary.  P2P transport does not carry meaning.

### C. The common diagnosis (1 minute)

   1. Additions cannot compensate for a substrate whose shape is wrong.  Annotation, federation, and transport each fails in the specific way the underlying architecture leaves no room for it to succeed.
   2. The solution must be a *layer* where creating data simultaneously creates semantic structure and places it on a user-controlled peer.
   3. Transition: "So what does such a layer actually look like?"

## III. What a Base Layer Requires (3 minutes)

A rapid enumeration of the eight properties, four semantic and four locality, serving as the checklist the rest of the talk fills in.  Each gets one sentence.

### A. Semantic requirements

   1. **Grounded predicates**: keys that refer to meanings, not strings.  Drawn from empirically validated computational linguistics (WordNet, CILI, FrameNet, VerbNet, ISO 24617-4).
   2. **Structured assertions**: not flat key-value pairs but predicate-role structures that capture who did what to whom.
   3. **Write-time resolution**: meaning resolved at the moment of creation, when the creator knows what they mean, not guessed at later by crawlers and NLP pipelines.
   4. **Cross-lingual stability**: meanings are language-independent sememes; words are language-specific lexemes that point at them.  The concept DOG exists independently of "dog," "perro," and "犬."

### B. Locality requirements

   1. **Cryptographic identity**: a keypair, not an account.  PGP's web-of-trust generalized to all assertions.
   2. **Content-addressed data**: named by what it is (a hash), not where it lives (a path or URL).  Move or copy it; it's still recognizably the same thing.
   3. **Social-graph routing**: data lives with peers who care about it, travels along trust relationships, surfaces through shared connections.  Routing policy attaches at any granularity (frame, item, session) and composes naturally, from direct connection to multi-hop onion routing.
   4. **Local execution**: the runtime lives on your machine.  Remote computation is an explicit delegation, not the default.

   Transition: "Those are the constraints.  Here is the primitive that satisfies all eight."

## IV. The Frame Primitive (8 minutes)

This is the core of the talk.  The single data structure from which everything else is built.

### A. What a frame is (2 minutes)

   1. A **predicate** (a grounded meaning: what kind of assertion) and **bindings** (compound-key to value pairs: the semantic content).  Nothing else is structurally required.
   2. The predicate is a sememe, a unit of meaning from the shared vocabulary, acting in a structural role.  It declares what bindings the frame expects.
   3. Each binding key is a sequence of one or more grounded meanings.  `(VALUE, ENGLISH)` vs. `(VALUE, RUSSIAN)` distinguishes translations through the language qualifier.  Every element is a sememe, not an arbitrary string.
   4. Each binding carries independent flags: **identity** (does it affect the content hash?) and **index** (should it be discoverable via reverse lookup?).  The content hash of the frame body is computed from identity-flagged bindings only, which means multiple signers attesting the same assertion produce the same body hash with different signing envelopes.

### B. Live examples (3 minutes)

   Walk through concrete frames, showing structural identity across wildly different domains:

   1. **A title**: `TITLE { (THEME) = the-book, (VALUE, ENGLISH) = "The Hobbit" }`.  A separate frame carries the Russian title: `TITLE { (THEME) = the-book, (VALUE, RUSSIAN) = "Хоббит" }`.  Each is independently signed because a translator should not need the original author's key.
   2. **A chess move**: `MOVE { (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4 }`.  Location (which game), Agent (who moved), Theme (what piece), Source (from where), Goal (to where).
   3. **An authorship assertion**: `AUTHORED { (THEME) = The Hobbit, (AGENT) = Tolkien }`.
   4. **A mathematical expression**: `ADD { (THEME) = 3, (INSTRUMENT) = 5 }` evaluates to 8.  `INTEGRATE { (THEME) = x², (SOURCE) = 0, (GOAL) = 1, (INSTRUMENT) = dx }` evaluates to 1/3.  The thematic roles (Theme, Instrument, Source, Goal) are the same ones used for natural language; they map onto math because they are cognitive structuring principles, not linguistic artifacts.
   5. **A phone call**: `CALL { (AGENT) = caller, (RECIPIENT) = callee, (VALUE, AUDIO) = <stream>, (CONFIG, RETENTION) = PRESENCE }`.  Same primitive.  The call is a signed assertion; the audio is content in a VALUE binding.
   6. Emphasize: all structurally identical.  A predicate and role bindings.  The same primitive describes a book title, a chess move, a definite integral, and a phone call.

### C. Queries are incomplete frames (1.5 minutes)

   1. A query is a frame with unfilled bindings.  `AUTHORED { (AGENT) = Tolkien }` with no THEME asks "what did Tolkien author?"  `MOVE { (LOCATION) = the-game }` asks "what moves in this game?"
   2. Expressions as sub-frames: `LISTING { (THEME) = book, (VALUE, PRICE, USD) = LESS_THAN { (VALUE) = 20 } }` filters by price.
   3. No separate query language.  No SQL, no SPARQL, no GraphQL.  The frame IS the query, the shared vocabulary IS the schema, and the compound-key index IS the query engine.

### D. Compound keys, indexing, and CONFIG (1.5 minutes)

   1. Every meaning in a compound key is an indexing opportunity.  "Show me all videos" is a lookup on frames with VIDEO in their keys.  "All UHD videos" narrows to VIDEO and UHD.  No separate tagging system, no search facets.  The key *is* the index.
   2. There is no data/metadata distinction.  A title's text, a video's file, a chess move's destination square, provenance, signatures, and timestamps are all role bindings.  The distinction is conventional, not structural.
   3. **CONFIG** is a cross-cutting role that any frame can carry.  It governs policy: retention (keep all, keep only the latest, keep the chain), routing (direct, relayed, onion-routed), replication, encryption.  A chess move is retained permanently; an avatar position is discarded when a newer one arrives; a video feed binds to a content stream whose blocks are consumed and released.  The difference is CONFIG, not a separate mechanism.

## V. Items: What Frames Cohere Around (4 minutes)

### A. The anchor (1.5 minutes)

   1. A single frame is rarely the whole story.  A book has TITLE frames, AUTHORED frames, TEXT frames, COVER_ART frames, PUBLICATION frames.  They are all about the same thing.
   2. An **item** is a stable anchor that frames reference.  The book is an item.  Tolkien is an item.  A chess game is an item.  A phone call is an item.
   3. **Item ID (IID)**: stable, location-independent, not assigned by a registry.  The same IID is recognized by any peer without coordination.
   4. **Content ID**: SHA-256 hash of content.  Used for version IDs and content addressing.

### B. Versions and manifests (1 minute)

   1. The set of frames an item endorses is recorded in its **manifest**: a signed list of frame endorsements.  The manifest's hash is the **version ID (VID)**.
   2. The manifest endorses frame bodies (content hashes), not specific signing envelopes.  This means two people attesting the same fact produce the same body hash, and the manifest can endorse the assertion without caring who signed it.
   3. New frames, new manifest, new VID.  IID stays stable.  Version history is a directed graph of manifests, structurally similar to Git's commit graph.  Fork and merge work the same way.

### C. Archetypes (1.5 minutes)

   1. What makes a book a book?  BOOK is a sememe in the shared vocabulary.  As an **archetype**, it declares what frames an item of its kind is expected to endorse.
   2. The declaration is *open*.  BOOK says "expect TITLE, AUTHORED, TEXT."  Nothing prevents someone from signing a LIKE, a review, a citation, a fact-check, or a translation that binds to the same book.  The archetype defines the identity; it does not gatekeep what others may say.
   3. Example: a chess game.  CHESS is the archetype.  Players register with signed PLAYER frames.  Moves are signed MOVE frames linked by FOLLOWS.  The game is played peer-to-peer: each move travels directly from the player who made it.  No referee server.  The game is recorded by being signed.

## VI. The Trust Matrix (5 minutes)

This section is where the security audience should feel most at home.

### A. Assessments are frames (1.5 minutes)

   1. Every reaction, moderation action, and endorsement is a semantic frame signed by an identified party.  A FUNNY frame asserts something is funny.  A SPAM frame asserts something is spam.
   2. Frames can target other frames, not just items.  A SPAM frame whose THEME points at another frame means "I assert this specific assertion is spam."
   3. These semantic reactions cluster naturally in the vocabulary hierarchy: FUNNY, HILARIOUS, AMUSING under HUMOR; SPAM, ASTROTURF, JUNK under a different branch.  The clustering is structural, not engineered.

### B. Trust is computed, not declared (1.5 minutes)

   1. Trust emerges from accumulated assertions.  If Alice consistently reacts to Bob's content with INSIGHTFUL and AGREE, her Librarian computes trust in Bob's judgment from the pattern.
   2. Trust is not a single number but a **matrix**: multi-dimensional, per-domain.  Trust Alice's music taste without trusting her political judgment.  Trust Bob's relay infrastructure without trusting Bob's content.
   3. Each Librarian computes its own trust matrix **locally**.  There is no global reputation score.  Two users viewing the same content may see different things because their trust matrices differ.

### C. Moderation without moderators (1.5 minutes)

   1. Mark several posts as SPAM.  Your trust in that poster decreases.  James disagrees, marks your SPAM frames with DISAGREE.  Others weigh in.
   2. For users who trust your moderation judgment, the posts disappear.  For users who trust James, they survive.  No moderator appointed, no appeals board, no single outcome imposed.
   3. **Transitive trust**: strongest form requires no explicit endorsement.  If Alice, Bob, and I independently react positively to the same restaurants, our Librarians compute overlapping taste from convergent independent assertions.  Cannot be faked without faking the underlying reactions.
   4. This is Szabo's (1997) vision: formalizing relationships on public networks as overlapping views, not a single view imposed by a platform.

### D. Bootstrapping trust (0.5 minutes)

   1. A trust model that requires trust raises a bootstrapping question: how does a new user get started?
   2. The same way any social system works.  You arrive through someone you know: a friend who peers with you, a community node that accepts newcomers, or a public gateway that offers initial connectivity.  Trust starts small and grows through interaction.

## VII. Routing, Privacy, and Code Trust (10 minutes)

The security-specific section.  This is where the talk goes beyond the paper's emphasis and into territory this audience cares about most.

### A. Routing policy at any granularity (3 minutes)

   1. Because data travels between peers through chosen trust relationships, routing privacy is a natural property of the social graph.
   2. Routing policy attaches at whatever granularity fits: a single frame, an item, a user's entire Librarian, or a particular session.  These compose naturally: a frame inherits its item's posture unless it carries its own, an item inherits its Librarian's default unless it specifies otherwise.
   3. Your public blog post, your private medical records, and a single sensitive assertion within an otherwise-public item can each carry fundamentally different routing postures.
   4. **Direct connection**: the default for most traffic.  Peer to peer, no intermediary.
   5. **Relay through a trusted peer**: structurally identical to a VPN.  The VPN service is just a Librarian that relays messages.  One primitive; the $50B VPN industry becomes a commodity relay market.
   6. **Multi-hop with layered encryption**: onion routing.  Each intermediate peer decrypts enough to know where to forward next but sees neither origin nor destination.  Tor's capability as a configuration, not a separate network.
   7. **Mix networks**: same mechanism with batching and timing policies at each hop for traffic-analysis resistance.
   8. The key insight: these are not different systems.  They are configurations of one routing primitive, expressed in the same substrate, governed by the same trust relationships.

### B. Cryptographic identity and signed assertions (3 minutes)

   1. Identity is a keypair.  Not an account, not a username, not a row in a database.  You generate it locally.  No authority grants it; no authority can revoke it.
   2. Every assertion is signed.  Provenance and integrity are properties of the primitive, not features added later.
   3. Content-addressing means the same data from any source produces the same fingerprint.  Integrity is verifiable locally.  No certificate authority, no HTTPS chain, no trust in the transport.
   4. The web-of-trust model, generalized beyond PGP's email-and-keys scope to cover all assertions.  Trust is not binary (trusted/untrusted) but a multi-dimensional matrix computed from observed behavior.
   5. Peers can stop trusting a key (social decision), but no one can revoke it architecturally.  The distinction matters.
   6. Key compromise remains the hardest unsolved problem.  A sister project, Keymaster (open hardware), addresses device compromise and coercion at the hardware level.

### C. Code distribution and supply-chain trust (4 minutes)

   1. Code is an item.  An implementation of ADD is an item carrying executable form alongside frames declaring which contract it satisfies, who signed it, and what runtime is needed.
   2. The link between an implementation and the predicate it satisfies is a frame.  A predicate can have many implementations: different runtimes, different trade-offs, different authors.
   3. A runtime evaluating a frame picks an implementation it can execute *and whose author it trusts*.  Trust, not a gatekeeper, determines what runs.
   4. **The supply-chain argument**: all code distribution already relies on social trust.  Google Play Store: you trust Google's review.  apt: you trust Debian maintainers.  App Store: you trust Apple.  SolarWinds and the xz backdoor demonstrate that centralized trust intermediaries are not immune to compromise.
   5. What changes: not *whether* code execution depends on trust, but *who* is being trusted.  A personally chosen network of peers whose reputations are visible and whose endorsements are signed, rather than an opaque corporate process.
   6. Nothing prevents Google or Apple from publishing signed code items.  They stand alongside every other reviewer rather than occupying a privileged gatekeeping position.
   7. **Sandboxing**: running code from arbitrary peers requires proper isolation.  Same kind of hard as sandboxing untrusted JavaScript in browsers.  Capability-based interfaces, isolated execution, formal verification of restricted languages.
   8. The structural consequence: the main rationale for SaaS (operator holds both code and data, running the code requires their infrastructure) dissolves when both travel through the same peer substrate.

## VIII. Sanity Check (5 minutes)

Directly addressing the two objections any systems person will raise.  This section is what separates a hand-wavy proposal from an engineering argument.

### A. Does it scale? (3 minutes)

   1. Every frame creates index entries for each indexed binding (136 bytes per entry).  Not every binding is indexed — literals, payloads, and policy bindings are skipped.  Indexing cost is linear in the number of semantic assertions, not in content size.  A 1 MB photo and a 10 GB movie with the same six descriptive frames produce the same index cost.
   2. **The power law is your friend.**  Research on 52M+ posts: median engagement is ~4 interactions.  90%+ of items have trivial indexing cost (a few frames, under a kilobyte of index).  The extreme tail (millions of reactions) is a handful of posts per year globally.
   3. **Social-graph sharding for the extreme case.**  A viral post with 5M reactions totals ~4.5 GB across the entire network.  No single node holds it all.  A casual viewer holds ~150 KB (reactions from friends); a popular creator holds ~40 MB (reactions from followers); a full aggregator holds all ~4.5 GB.  The social graph is a natural shard boundary.
   4. **Distributed counting via HyperLogLog.**  For items popular enough to need aggregation (a small fraction), nodes maintain mergeable probabilistic sketches (~16 KB each) that deduplicate across overlapping social graphs with ~2% error.  The sketch is just another frame, gossiped like everything else.
   5. **Text search scales linearly.**  Vocabulary is bounded by linguistic resources (~35 MB for English, ~4 MB for Mandarin).  User content (titles, proper names) grows linearly with items indexed.  10 million titles (IMDB scale) costs roughly 2 GB of token dictionary.  Function words are excluded from individual indexing at the direction of the language item itself.
   6. **Ed25519 signing**: ~50K signs/sec, ~15K verifies/sec on commodity hardware.  Not a bottleneck even at extreme scale.
   7. All estimates are uncompressed.  Real storage backends add compression that only improves them.

### B. What about attacks? (2 minutes)

   1. **No single point of failure.**  Each user runs an independent Librarian.  Taking one down affects one user.  Institutional nodes are higher-value targets, but their data is already replicated across peers.  Time-to-impact stretches from seconds (centralized) to hours (distributed).
   2. **The social graph is the firewall.**  No trust path, no connection.  Not a firewall rule; a structural property.
   3. **Sybil-resistant by default.**  New identities start at zero trust, must build relationships through sustained behavior.  Mass-creating identities doesn't help because none have trust.
   4. **Eclipse-resistant.**  Peers are chosen through trust relationships, not assigned by hash distance.  Compromising someone's peers means compromising their actual social relationships.
   5. **Frame flooding is self-limiting.**  Every frame is signed and attributable.  Junk frames degrade the sender's trust score.  The attack burns the trust relationship it depends on.
   6. **Key compromise is the hard problem.**  No software substrate fully solves device-level threats.  The Keymaster open-hardware project (github.com/joshualibrarian/keymaster) addresses this directly.
   7. **The economic shift**: generating traffic is cheap; building fake social trust is expensive, slow, and self-defeating.

## IX. What Changes (3 minutes)

Concrete consequences, stated plainly.

### A. Platform subsumption

   1. A product listing, a community forum, a social feed, a review, a citation graph: each is currently a proprietary database.  Each is expressible as frames in the shared vocabulary.
   2. Applications become interchangeable runtimes over the same data.  The user picks the client; the data does not belong to it.
   3. The economics of the internet do not disappear.  Businesses still want customers; hosting remains relevant.  What disappears is the ability to monetize user entrapment.

### B. Real-time communication

   1. A phone call is a CALL frame.  A video meeting is an item with STREAMING frames from each participant.  Signaling that WebRTC handles out-of-band becomes in-band, because the frame IS the signal.
   2. Phone numbers become unnecessary when identity is cryptographic.  Spam calls become structurally impossible: no trust path, no ring.  Users who want cold calls lower their threshold; the trust matrix still provides a gradient that spoofable phone numbers never could.
   3. Video/audio streams can flow direct (HOPS=0, lowest latency) or relayed through peers (HOPS=N, IP hidden).  Same frame, different routing policy.  The participant chooses.
   4. The phone industry's radio infrastructure remains valuable as transport.  A telephony bridge (a device with your SIM card running a small Librarian) connects the legacy phone network to CG during the transition.

### C. Structural consequences

   1. **Offline capability**: trivial when runtime and data are both local.
   2. **Resilience to vendor disappearance**: items live on users' devices and peers.  A company shuts down; the data, the tools, and the peer network remain.
   3. **The vocabulary is the commons**: extensible from the edges, not the center.

## X. Authorship, Not Ownership (2 minutes)

Honesty about what this does and does not deliver.  This audience respects candor.

### A. What you get

   1. **Provable authorship**: you hold your keys, nobody can sign as you.  Every assertion is attributable.
   2. **Local custody**: no vendor can revoke access to work you already have.
   3. **Consent to new copies**: you choose which peers you share with, through deliberate trust relationships.

### B. What you do not get

   1. Ownership of data in the property-law sense.  Once you give someone a copy, you cannot technically revoke it.  This is a property of copyable information, not a failure of any honest substrate.
   2. The partial solution is social, not technical.  A trust paradigm lets you *choose* whom to share with.  If someone violates that trust, you stop trusting them, and the trust graph responds.
   3. "The word 'ownership' borrows from property law what the medium cannot enforce.  A more honest vocabulary is authorship, custody, and consent."

## XI. Honest Reckoning (3 minutes)

### A. Predecessors and their lessons (1.5 minutes)

   1. **Xanadu**: got content addressing and versioning right.  Failed by demanding completeness before shipping anything.  Lesson: incremental delivery.
   2. **CYC**: got the diagnosis right (computers need world knowledge).  Failed because hand-authoring axioms does not scale.  Lesson: anchor in existing empirical resources.
   3. **Plan 9**: technically superior to Unix.  Failed because it required abandoning the Unix ecosystem.  Lesson: provide a bridge.
   4. **The Semantic Web**: got the diagnosis exactly right.  Did not become general-purpose because it was optional.  Lesson: the semantic layer cannot be a separate step.
   5. **Local-first software**: the tradition directly upstream.  Has not displaced SaaS because the centralized path has been easier.  Lesson: make local-first the easier path, not just the more virtuous one.

### B. Why now (1 minute)

   1. The computational linguistics infrastructure matured: WordNet (120,000 synsets), CILI (cross-lingual links), VerbNet (300 verb classes), ISO 24617-4 (standardized role inventory), UniMorph (100+ languages).
   2. The technical infrastructure matured: ed25519 is cheap per-message, content-addressing is ubiquitous (Git), CRDTs ship in production, P2P transport stacks (libp2p, QUIC) are mature.
   3. AI has compressed what was previously decades of solo implementation.  The bottleneck for ambitious projects was always the volume of code required.  That bottleneck has narrowed.

### C. What's built (0.5 minutes)

   1. Brief description of the current state: local runtime (Librarian), frame storage and querying, vocabulary seeded from WordNet/CILI, working applications (chess, other games) as proof-of-concept, Skia 2D and Filament 3D rendering.
   2. Open source.  The paper is available.  Hard copies at the session.

## XII. Close (2 minutes)

   1. Return to the opening: two structural gaps, one primitive that closes both.
   2. "The path forward is incremental: frames as a local data format; a shared vocabulary seeded from WordNet and CILI; a local runtime that stores, queries, and resolves data by meaning; and a peer-to-peer network where that data is exchanged between nodes connected by trust.  Each step independently useful.  Together, the semantic and local-first base layer that computing has been missing since the networked era began."
   3. Questions.

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
