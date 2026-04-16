# Advertising

This document examines how advertising works under Common Graph: what stays essentially unchanged, what fundamentally changes, and what new mechanisms become available.  It is reference material for pitches and design discussions, not a specification.  It complements [`analytics.md`](analytics.md), which covers the broader surveillance-to-testimony shift that these advertising specifics sit inside.

The central claim is that advertising as an economic activity survives CG without much trouble.  What disappears is the surveillance-based ad-tech middle layer that currently captures most of the ad budget.  The direct relationships between brands, creators, and audiences not only survive, they are strengthened.

## What doesn't change

A surprising amount of advertising works just as well under CG as it does today, because it never needed surveillance to begin with.  Recognizing this is important for pitch conversations: brands whose advertising already works through these mechanisms experience little disruption.

### Content-embedded advertising

The clearest case.  A YouTube creator says "this video is sponsored by Brand X" and talks about the brand for sixty to ninety seconds, often with genuine enthusiasm and a personal story about why they like the product.  This form of advertising works entirely through the content itself.  There is no tracking pixel, no ad network, no behavioral targeting.  The ad is simply part of what the creator made.  Users watch it, skip it, or turn it off, but the creator was already paid before the video was published.

Podcasts have the same form: host-read ads that are literally part of the audio stream.  Radio spots.  Television product placement.  Print ads in magazines.  All of these exist as content, not as infrastructure overlays, and all of them work without observing user behavior.

Under CG, this entire category continues unchanged.  The video is an item.  The creator's in-content call-out is part of the item's content.  A SPONSORED_BY frame attached to the video makes the commercial relationship structurally legible.  The economic engine (brand pays creator, creator talks to audience, audience encounters brand) is structurally identical to what works today.

The relevant observation for pitches: if your advertising already works through content-embedded mechanisms, CG does not disrupt you.  You may even benefit from the transparency of signed sponsorship frames, which make the commercial relationship legible rather than buried in legal disclosure footnotes.

### Sponsor recognition in shared contexts

Public radio listeners hearing "this program is brought to you by..." at the bottom of each hour.  Conference attendees seeing sponsor logos on the stage banner.  Race participants seeing sponsor names on bibs and signage.  Event sponsorship plaques at concerts and film festivals.

All of this is advertising, and all of it works without surveillance.  The sponsor's identity is present in the context the audience has chosen to engage with.  CG's SPONSORS frame is a signed, verifiable attribution of the sponsorship relationship, but it does not change what the sponsor pays for or what the audience experiences.

### Direct brand presence

A store has a sign.  A brand has a logo.  A product has packaging.  The physical world is full of advertising that works because the brand is visible where the customer is already looking.  CG's equivalent is brand items in the graph, visible in store listings, product frames, community presence, and trusted aggregations.  Nothing about how a brand manifests in the world requires tracking infrastructure.

## What fundamentally changes

The part of advertising that depends on cross-site tracking and behavioral inference ends, cleanly.

**Behavioral targeting.**  "This user looked at shoes, so show them shoe ads everywhere they go."  Depends on cross-site tracking.  Gone.

**Retargeting.**  "This user abandoned a cart; chase them with ads for the same product for two weeks."  Same mechanism.  Gone.

**Lookalike audiences.**  "Find people who behave like your existing customers."  Requires behavioral fingerprints of both populations.  Substantially degraded.

**Programmatic real-time bidding based on user profiles.**  The infrastructure ships bid requests carrying user profile data to thousands of potential buyers in milliseconds.  This entire system depends on the tracking substrate CG eliminates.  Can be rebuilt on contextual signals (what is the content about) but the shape changes substantially.

**Multi-touch attribution.**  "What combination of ad exposures led to this purchase?"  Requires observing the user journey across sites.  Gone in the current form.  Replaced by direct attribution when users explicitly publish "bought because of" frames.

**Ad fraud based on fake impressions.**  Cryptographic verification makes real delivery provable.  Inventing signed engagement requires identities with reputation cost.

The combined market capitalization of the businesses built on these specific mechanisms is in the trillions.  Google's ad business, Meta's ad business, The Trade Desk, most of the programmatic stack, dozens of DSP/SSP/DMP vendors, the entire tracking and verification layer: all of this is the primary adversary to CG adoption in advertising markets.

## The concrete mechanisms

Setting up a scenario: an outdoor gear manufacturer with a StoreItem, product listings, and a brand identity wants to drive business.  Here are ten approaches, each with its CG realization.

### 1. Direct announcement frames

The simplest: the brand publishes signed announcements on its own store item.

```
ANNOUNCEMENT {
  (AGENT) = outdoor-gear-co
  (THEME) = store:outdoor-gear-co
  (TOPIC) = summer-sale-2026
  (VALUE) = "30% off tents through July 4"
  (TIME)  = 2026-05-15
}
```

Users who FOLLOW the store see it in their feed.  Users who follow topics (camping, hiking) via chosen aggregators see it if those aggregators include the brand.  The distribution mechanism is the trust graph, not a platform's algorithm.

### 2. Sponsored creator content with verified attribution

The brand approaches a hiking content creator whose audience overlaps with the target market.  The creator produces a video about a backpacking trip; the sponsorship is a signed frame attached to the content:

```
SPONSORED_BY {
  (THEME)         = video:alice-high-sierra-trip
  (AGENT)         = outdoor-gear-co
  (VALUE)         = $5000
  (CONFIG, TERMS) = "product placement of backpack-y, honest review allowed"
  (TIME)          = 2026-05-01
}
```

The creator's in-video call-out (see "what doesn't change" above) is part of the content itself.  The SPONSORED_BY frame provides the structural disclosure.  Viewers who trust the creator encounter the brand in a context of earned credibility.

### 3. Curation items (content marketing as substrate)

Someone publishes an item called "best-lightweight-backpacks-2026" as a curation: a list of RECOMMENDS frames, each with a rationale.

```
RECOMMENDS { (AGENT) = outdoor-gear-co, (THEME) = backpack-y, (VALUE) = "65L, sub-3lb, waterproof" }
RECOMMENDS { (AGENT) = outdoor-gear-co, (THEME) = competitor-pack-z, (VALUE) = "heavier but better frame for serious loads" }
```

The curation being honest (recommending a competitor when warranted) earns it trust.  Users who follow the brand or encounter the curation through trusted channels discover products in context.

### 4. Trust-graph influencers

Products are given to reviewers who have built audiences around outdoor gear.  Their reviews are signed frames; sponsored reviews carry disclosure frames:

```
REVIEW {
  (AGENT)  = ben-hiker
  (THEME)  = tent-model-x
  (RATING) = 4
  (VALUE)  = "Great 3-season tent, a bit heavy for solo..."
}
DISCLOSURE {
  (THEME)  = the-review
  (AGENT)  = outdoor-gear-co
  (VALUE)  = "free tent provided for review"
}
```

Reviews propagate through the reviewer's followers' trust graphs.  The disclosure travels with the review.  Current influencer marketing's "was this paid?" question becomes a structural query rather than a disclosure compliance concern.

### 5. Opt-in audience (consented ads)

Some users explicitly want to see ads about outdoor gear because they are actively shopping.  They publish an interest frame:

```
INTERESTED_IN {
  (AGENT)         = alice
  (THEME)         = [camping, hiking, backpacking]
  (CONFIG, SCOPE) = PROMOTIONAL_CONTENT
  (TIME)          = 2026-05-01
}
```

The brand (or a matchmaker service the user trusts) queries for interested users whose preferences match.  Alice receives announcements in her "promotions" section.  This is advertising the audience actively wants, which is what the ad industry has been claiming to deliver for years without delivering.

### 6. Community presence

The brand joins outdoor enthusiast communities (items).  Contributes trip reports, gear maintenance tips, repair guides.  Does not advertise in the pushy sense; is present and useful.  The brand identity is visible; users who find the contributions valuable can FOLLOW the brand.

This is the oldest form of marketing, and CG makes it low-friction because communities are items you can participate in directly.  The "content marketing" industry that currently charges enterprises to produce branded content for other platforms' algorithms exists mostly because platforms make community presence hard.  CG removes that friction.

### 7. Cross-brand recommendation

A complementary brand (hiking book publisher) and the gear brand mutually recommend each other.

```
RECOMMENDS { (AGENT) = outdoor-gear-co, (THEME) = mountain-trails-book, (VALUE) = "best pacific crest guide we've used" }
RECOMMENDS { (AGENT) = mountain-trails-press, (THEME) = outdoor-gear-co, (VALUE) = "gear brand we trust for serious hikers" }
```

Mutual endorsements propagate through both audiences.  The cross-recommendation is visible and verifiable.  No intermediary needed.

### 8. Event sponsorship

Sponsor a local hike, race, trail-building day, or film festival:

```
SPONSORS {
  (AGENT)  = outdoor-gear-co
  (THEME)  = event:sierra-trail-days-2026
  (VALUE)  = $10000
  (CONFIG) = [logo-placement, booth-at-event, product-giveaway]
}
```

Event sponsorship has worked forever; CG makes it structurally cleaner with signed attribution that follows event materials wherever they go.

### 9. Referral bounties

The brand signs a public bounty frame:

```
BOUNTY {
  (AGENT)  = outdoor-gear-co
  (TOPIC)  = referral
  (VALUE)  = "$20 store credit for referrer, $20 off for new customer"
  (CONFIG) = [verified-purchase, new-customer-only, expires-2026-12-31]
}
```

Any user can refer friends through the trust graph.  Purchase attribution is cryptographic: the referral chain is signed.  Unlike current referral codes (which require clicky URLs, landing pages, and cookies to track), CG-native referrals work because identity and trust are first-class.

### 10. Contextual availability

Users in a specific area looking at camping content can get local-availability notifications:

```
AVAILABLE_AT {
  (THEME)    = tent-model-x
  (AGENT)    = retail-partner:truckee-outdoor
  (LOCATION) = [39.3280°N, 120.1833°W]
  (CONFIG)   = [in-stock, same-day-pickup]
}
```

Contextually relevant inventory matching based on what the user is actively engaging with, not on where they have been tracked.

## Brand safety, by design

A chronic problem in programmatic advertising: a brand's ads running next to content the brand would never want to be associated with.  Fortune 500 brands spend substantial budget on brand-safety vendors that try to solve this problem after the fact, with mixed results.

Under CG, brand safety is structural.  Sponsorships are explicit: the brand signs a SPONSORS frame on specific content it chose.  It does not bid into an exchange that distributes impressions across unknown inventory.  The problem of "my ad ran next to extremist content" does not arise because the brand did not place its ad on that content in the first place.

The cost of this is losing the "scale through aggregation" that programmatic promises (and frequently fails to deliver).  The benefit is that brands only show up where they chose to show up, which is what they actually want.

## Measurement

Under CG, advertising measurement is honest but narrower than current claims.

Directly measurable:
- Views of sponsored content, if the viewer publishes engagement frames
- Actual purchases attributed by the buyer (via "bought because of" frames)
- Verified delivery (cryptographic proof that a sponsored frame reached a user's client)
- Engagement signals the user chose to share
- Referral chains (the trust graph through which a purchase propagated)

Not directly measurable:
- Passive views ("impressions" in the ad-industry sense)
- User journeys across sites
- Attention duration on ads
- Unconsented behavioral profiles

The honest pitch: you measure what users chose to report, and that signal is cleaner and more meaningful than today's impression counts and click-through rates.  Advertisers who have been measuring the right things (sales, repeat customers, brand-aware purchase decisions) will find CG measurement adequate.  Advertisers whose campaigns depend on impression counts and behavioral cohorts will experience real loss of measurement surface, and for them the shift will feel like a contraction until they adapt.

## The transition for brands

Brands moving to CG advertising don't start from scratch.  They start with what they already have: existing direct-response channels, existing creator relationships, existing event sponsorships, existing contextual placements.  All of these work under CG immediately.

What transitions more slowly: programmatic ad buying, retargeting campaigns, lookalike-audience targeting, and attribution models that depend on cross-site tracking.  These become unavailable as users migrate to CG-based clients, and brands have to rebuild equivalent reach through the direct mechanisms described above.

The brands best positioned for the transition are those already investing in:

- Direct creator sponsorships and influencer relationships
- Content marketing that works on its own merits
- Community presence and brand building
- First-party data relationships with customers
- Email and owned-channel marketing

The brands most exposed are those that built their growth engines primarily on behavioral targeting and have not developed direct audience relationships.  For them, CG adoption is a forced return to fundamentals that were always going to reassert themselves; the surveillance era was a detour that will end.

## Pitch framings

**To advertisers:**

> You lose behavioral targeting and its claimed precision.  You gain direct, verified relationships with audiences who chose to hear from you.  Your ad budget stops flowing to the ad-tech middle layer that currently captures most of it.  Your measurement becomes honest (what users actually reported) rather than inferred (what tracking pixels observed).  For campaigns that were already working through content-embedded advertising, creator sponsorship, and direct relationships: nothing fundamentally changes.  For campaigns built on cross-site surveillance, you need a new strategy, and the new strategy is the one your brand marketers have been telling you was always the real foundation anyway.

**To publishers and creators:**

> Your relationship with your audience becomes the primary asset, not a rented channel subject to a platform's algorithm.  Direct sponsorships, subscription and patronage revenue, and verified brand partnerships replace programmatic ad-share as your revenue base.  Your cut of the advertising economy increases because the middle layer that captures most of it today is disintermediated.  In-video call-outs, host-read spots, and sponsored content all work without change.

**To ad-tech middlemen:**

> There is no pitch.  This substrate substantially reduces your role, and you will be the primary source of organized opposition to CG's adoption in advertising markets.  Planning the transition honestly means acknowledging that a large segment of the current ad-tech ecosystem does not survive, and that resistance will be correspondingly intense.

## Relationship to analytics.md and the white paper

This document's claims sit inside the broader surveillance-to-testimony shift covered in [`analytics.md`](analytics.md), which describes what kinds of data become available or unavailable under CG.  The advertising specifics here are one concrete domain of that broader shift.

The white paper's Section 2 (The Age of SaaS) and Section 9 (What Follows) touch on advertising implicitly through the platform-ownership critique.  No changes to the white paper are required based on this document; the material here is pitch-facing and strategy-facing rather than argument-facing.
