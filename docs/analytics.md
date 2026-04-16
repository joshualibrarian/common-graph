# Analytics

This document examines what happens to the "analytics" industry under Common Graph: what kinds of data become unobservable, what kinds become newly observable, and what the structural shift means for the businesses built on analytics today.  It is reference material for pitches and design discussions, not a formal specification.

The central observation is that most of what is currently called "analytics" is actually surveillance: data harvested without user awareness because the server sat between the user and everything they did.  CG removes the server from that position, and the surveillance goes with it.  What remains, and what becomes newly available, is different in kind from what the analytics industry currently sells.

## What disappears

The following categories of data, currently observed as a passive consequence of web architecture, become unobservable under CG.

**Passive viewing signals.**  The email tracking pixel dies.  An image that fires a server request when an email is opened only works because the image loads from a remote server.  In CG, content is served from the local runtime or from peers the user already trusts.  Rendering a frame generates no network event visible to the author.  The author does not know whether the user opened the email, saw the article, or loaded the page.

**Time-on-page, scroll depth, attention metrics.**  The user's client knows.  Nobody else does unless the user explicitly publishes a signed "I spent ten minutes on this" frame, which by default does not exist.

**User-agent fingerprinting and IP tracking.**  The content author is not in the request path.  Device, browser, OS, and IP are not visible to them.  Trusted peers see whatever relaying they perform, but they are peers the user chose.

**Third-party cookies and cross-site tracking.**  Literally impossible.  There is no server running the cross-site script and no cookie jar for it to write to.  The ad-tech industry's primary data source stops existing.

**Clickstream and user-journey mapping.**  What the user did in what order is unobservable unless explicitly published.

**A/B testing as currently practiced.**  Still possible, but only by explicitly sending different variants as signed frames.  Users can see which variant they received and compare with others who got the alternative.  Surreptitious variant assignment ends.

**Conversion tracking.**  An advertiser cannot secretly tie an ad impression to a purchase.  Both are events in the user's local context.  Unless the user explicitly publishes a "bought this because of that" frame, the link is invisible.

**Search queries.**  The user's queries run against their local graph or their trusted peers.  Search providers do not see them.  This is the largest single data stream that disappears.

**Content consumption metrics.**  Did the user watch the video, how far, did they skim or read: all private by default.

## What becomes available that was not before

This is the part that is often missed.  CG makes new categories of analytics available by turning what was previously passive observation into signed, structured assertion.

**Provenance as a first-class query.**  Every frame has a signed author.  Walking back through the graph to find where a claim originated is trivial, and cryptographically verifiable at each step.  This was hard or impossible on the current web.

**Aggregate public engagement, honestly measured.**  How many people reacted to a piece of content is the count of reaction frames targeting it.  How many shared it is the count of SHARE frames.  These are public signed assertions; counting them requires no special instrumentation, analytics property, or ad account.  The analytics are inherent to the substrate.

Worth elaborating on what "reaction frames" means here, because this is where CG departs from every social platform that came before.  Platforms like Twitter or Facebook have a handful of generic containers (COMMENT, REPLY, POST) that carry no semantic meaning on their own; the text of the comment is where any meaning lives, and the platform can only count the containers.  CG has no COMMENT or REPLY or POST predicate because those words describe containers, not meaning.  Instead, reactions use specific semantic predicates as the frame's predicate: AGREE, DISAGREE, CLARIFICATION, COUNTERPOINT, CRITIQUE, SUPPORT, DISGUSTING, DELIGHTFUL, CONFUSED, and so on; the text of the reaction fills the frame's VALUE binding.

Because the predicates are sememes in a structured vocabulary, they cluster semantically without any analytics tooling having to teach them to.  DISGUSTING, REVOLTING, and GROSS sit in the same region of the WordNet hierarchy under some common sememe for revulsion, which itself sits under something like negative-evaluation.  A query for "all negative reactions to this content" walks the vocabulary's inheritance hierarchy and finds every frame whose predicate is a hyponym of the target concept, regardless of which specific word the reacting user chose.  This is genuinely richer than counting replies: the user expresses what they actually mean, and the analytics aggregate across synonyms and related meanings because the vocabulary already knows they are related.

**Explicit consented data streams.**  A user who wants personalized recommendations can publish a signed PREFERENCE frame to a recommendation service they chose.  The service gets exactly what the user shared, for as long as the user permits, revocable.  The data is cleaner, the consent is auditable, the user knows what they are giving up.  This is how GDPR-style consent was meant to work; current cookie banners are a satire of that intent.

**Social graph structure on public relationships.**  Where users have chosen to make their FOLLOW frames public, who follows whom is directly readable.  Who cites whom, who collaborates with whom, who participates in which communities: all readable from the same public signed frames.  What remains invisible is the passive side (who *reads* whom without interacting), which is also what should be invisible.

**Content derivation and citation networks.**  "This piece was derived from that piece" is a frame.  Provenance chains, citation graphs, remix histories are directly queryable.  Academia, journalism, scientific research, and open source all benefit.  This is currently built as expensive overlays (Semantic Scholar, CrossRef, GitHub dependency graphs); in CG it falls out of the primitive.

**Vocabulary and domain usage.**  Which concepts are in active use across a community, which predicates are most frequently instantiated, which archetypes are growing: all directly queryable because the vocabulary is itself data.  A kind of cultural-analytics-on-meaning that does not exist today.

**Trust graph dynamics.**  Who trusts whom, for what, is a deeper social graph than "followers."  Changes in trust (revocation after an incident, for example) are observable as signed events.  This is a new kind of data that platform-era architecture specifically prevented from existing.

**Structural content analytics.**  Types of frames being created, compound-key patterns, cross-references between items, vocabulary adoption rates.  All directly queryable without special instrumentation.

## Private vs public reactions

Reactions in CG are not automatically public.  Any frame, including a LIKE, FOLLOW, REVIEW, or any other signaling assertion, can carry policy bindings that determine its visibility.

- A LIKE with a CONFIG policy limiting replication to the target party is a "private like": only the person being liked sees it.
- A LIKE encrypted to the target party's key is visible only to them, cryptographically.
- A FOLLOW can be public (community visibility) or private (the followed party sees it but the wider network does not).

This does not prevent the recipient from choosing to share the reaction further.  That is a social decision, not a technical one, and no substrate can prevent it.  But the user chooses the default, and the default is deliberate rather than incidental.  Current platforms make every reaction public by default because visibility is what they monetize; CG makes the choice the user's.

For analytics purposes, this means:

- Public reactions aggregate cleanly (count the public frames)
- Private reactions between parties are visible only to the parties
- Encrypted reactions are visible only to the recipient
- Aggregate private-reaction counts are not knowable except through the recipient's own counts

## Transport-layer privacy

A separate axis of privacy sits below the content-policy layer described above.  CG's planned routing policy allows any delivery to specify a number of HOPS: intermediate peers who pass the encrypted payload along without knowing the original source or the final destination.  HOPS=0 is a direct connection, the default.  HOPS=1 through a specific trusted peer is what today's VPN services provide; the VPN becomes a Librarian with an arrangement to relay.  HOPS=N with layered encryption is equivalent to what Tor offers.  A single primitive expresses the full range from direct to anonymous.

For analytics purposes, this means a user who chooses transport-layer anonymity cannot have their IP-level activity correlated with their content activity by a casual observer.  Peers along the route see only the bytes they need to forward.  The trade-off is latency (each hop adds round-trip time), so users make the choice per-context rather than globally.  Most traffic runs direct; sensitive activity can be configured to route through whatever depth of anonymity the user's threat model requires.

## The structural shift: surveillance to testimony

The current web's analytics are **surveillance**: observation without the observed party's awareness or consent.  This is not a policy choice of particular companies, it is a structural property of server-centric architecture.  If the server sits between users and everything they do, the server sees everything.  Whether it uses that information ethically is then a policy choice, which is why privacy policies matter so much and why they are routinely violated.

CG replaces surveillance with **testimony**: data that the user actively signs and publishes, with explicit knowledge of what they are sharing and with whom.  Passive existence is invisible; active choices are observable and attributable.

This is a fundamental shift in the relationship between users, data, and the businesses built on analyzing it.  It is not a matter of degree; it is a change in kind.

## Implications for advertising

Advertising is a large enough topic to deserve its own treatment.  It is also the category where CG creates the sharpest winners and losers, and where organized resistance is most likely.

### The three actors

**Advertisers** (companies buying ads to reach customers) have ongoing pain under the current system: ad fraud (accounts for an estimated 20-30% of programmatic spend), brand safety failures, attribution uncertainty, and measurement gaps that make ROI hard to calculate.  CG addresses much of this: cryptographic ad verification means real delivery can be proven, and consented audience targeting yields higher-quality engagement than behavioral inference.  But advertisers are also habituated to behavioral targeting they understand, and new mechanisms mean new processes and new unknowns.  Mixed reception likely.

**Publishers** (companies producing content and displaying ads) have ongoing pain with declining CPMs, header bidding complexity, ad blocking, and the erosion of direct reader relationships.  CG offers alternatives that strengthen publishers: license-based subscriptions, direct patronage, contextual advertising that does not require tracking infrastructure, and first-party reader relationships.  Generally positive reception likely, provided the revenue replacement story is credible.

**Ad-tech middlemen** are the primary adversary.  This includes demand-side platforms (DSPs), supply-side platforms (SSPs), data management platforms (DMPs), ad networks, verification vendors, attribution platforms, tracking companies, and the ad businesses of Google and Meta.  Their collective market capitalization runs into the trillions.  Their business model IS the surveillance infrastructure CG eliminates.  Expect organized resistance: regulatory lobbying, FUD campaigns, strategic acquisitions, attempts to coopt or neutralize the technology.

### What remains of advertising under CG

Several forms of advertising remain not just possible but arguably strengthened.

**Contextual advertising.**  Ads matched to the content they appear alongside, based on what the content is about.  Actually easier under CG because content is semantically structured.  An advertiser targeting "outdoor gear" no longer has to guess at page content or rely on keyword heuristics; they can target frames whose predicates and bindings indicate relevance.

**Direct-to-audience marketing.**  A brand builds a relationship with an audience, produces content for that audience, and advertises within its own trusted channel.  The trust graph makes this direct relationship more valuable and more portable.

**Sponsored content.**  A publisher's content carries an attributed SPONSORED_BY binding, or a signed frame from the sponsor asserting the sponsorship.  Transparent, verifiable, still effective.

**Consented audience targeting.**  A user opts into receiving ads about topics they care about, possibly in exchange for something (a subscription discount, access to premium content, or simply because they want to know about relevant products).  The audience is self-selecting, engaged, and honestly consenting.  This is closer to what advertisers actually want than behavioral targeting against proxy data.

**Brand-building.**  Pure brand advertising without immediate conversion tracking is older than the internet and works fine without surveillance.  Measurement is less precise but also less meaningful under current methods than the industry pretends.

### What ends

**Behavioral advertising based on cross-site tracking.**  The surveillance economy's flagship product.  Gone.

**Retargeting.**  "You looked at this product, here's an ad for it everywhere."  Depends on cross-site tracking.  Gone.

**Programmatic real-time bidding based on behavioral profiles.**  The infrastructure that makes this possible (bid requests carrying user profiles to thousands of potential buyers in milliseconds) depends on the surveillance architecture.  Can be rebuilt on contextual and consented signals but the economic shape changes substantially.

**Attribution based on tracking.**  Multi-touch attribution, last-click attribution, view-through attribution: all depend on observing user journeys across sites.  Gone.  Replaced by direct attribution (user publishes a "bought because of this" frame) which is more honest but less commonly available.

**Ad fraud based on fake impressions and click farms.**  Cryptographic verification makes real delivery provable.  Fraud becomes harder because inventing signed engagement frames requires identities with cost and reputation.

### The honest pitch to advertisers

> You lose behavioral targeting.  You gain ad fraud elimination, verified engagement, and consented audiences whose interest is real.  Your ROI calculation becomes simpler and more honest: ads that ran, reached real people, got real responses.  The users you reach chose to see your category of content.  The "waste" you've been paying the ad-tech middle for goes away.  The data you do get is higher quality because it is testimonial, not inferred.

### The honest pitch to publishers

> Your direct reader relationship becomes the primary asset rather than an afterthought.  Subscriptions, patronage, and license-based content become easier to operate.  Contextual advertising strengthens because your content is semantically structured.  The programmatic middle that has been capturing most of the revenue from ads on your pages stops doing that.  Your share of ad revenue goes up even if total ad revenue in the system goes down, because you are no longer splitting with a layer of intermediaries.

## Trust and reputation vs review platforms

CG's trust graph is sometimes described as "universal reviews."  The comparison is useful but the mechanism is different enough to be worth drawing out.

Traditional review platforms (Yelp, Amazon, Google Reviews, TripAdvisor) centralize reviews and then decide which to surface through algorithms users do not control.  Reviews have weak identity, fake reviews are a cottage industry, and platform moderation is opaque.  The "rating" of a restaurant is a number the platform computes.

CG does not have universal reviews in that sense.  What it has is:

1. **Review frames** signed by specific identities.  A `REVIEW { (AGENT) = alice, (THEME) = restaurant-x, (RATING) = 4, (VALUE) = "Great food, slow service" }` is a signed assertion.
2. **A trust graph** in which each user designates whose assertions they value, on what topics.
3. **Client-side aggregation** that weights reviews by the user's own trust graph.

The consequences:

- There is no single "rating" of the restaurant.  Different users see different scores, computed from reviews signed by people they trust.
- Fake reviews require having a trusted identity, which has cost (reputation, history, relationships).  Throwaway accounts count for nothing because nobody trusts them.
- The restaurant cannot "game" the rating by manipulating the platform, because no platform is running the aggregation.
- Trust is granular: you might trust Alice for restaurant reviews but not for tech advice, and the client honors that distinction.

This is a stronger model than universal reviews on several dimensions.  It is also less convenient for the restaurant, which cannot point to a single Google rating; for the user who wants to know "what do most people think"; and for marketers who want to stage astroturf campaigns.  Worth noting in pitches as a trade-off, not a pure win.

## Notes for pitches

The analytics question comes up in almost every enterprise pitch.  Canned framing:

> Most of what is currently called analytics is surveillance that users would stop if they could see it clearly.  CG ends that surveillance.  What replaces it is structured engagement analytics that are signed, auditable, and semantically richer than what is available today.  You lose the ability to stalk users across the web.  You gain honest metrics on the content you actually published, the community you actually built, and the relationships users actually chose to have with you.

For publishers specifically, emphasize: direct reader relationships become the primary asset, license-based monetization becomes easier, programmatic middle is disintermediated, their share of revenue increases.

For advertisers, emphasize: ad fraud becomes structurally harder, verified engagement replaces inferred engagement, consented audiences deliver higher-quality responses, the ad-tech middle that has been capturing most of the budget goes away.

For ad-tech middlemen, there is no pitch.  They are the adversary.  Expect resistance from this layer specifically.

For product teams in any industry asking "how do we measure usage?", the answer is: your client phones home explicitly with telemetry the user opted into, or you operate blind.  There is no secret instrumentation.  Users who opt in give you cleaner data than you get today from unconsented tracking.  Users who opt out give you nothing, which is the state you should assume as the default anyway.

## Relationship to the white paper

This document expands on themes present in the white paper but not developed there in depth.  Section 2 (The Age of SaaS) touches on "decisions users did not agree to and cannot amend," which is the surveillance problem named briefly.  Section 9 (What Follows) lists "Trust as data" as a consequence, which this document elaborates.  Section 10 (Authorship, Not Ownership) draws the line between what the substrate can and cannot technically enforce, which the analytics discussion here depends on.

No changes to the white paper are required based on this document.  The material here is pitch-facing and designer-facing rather than argument-facing.
