# Licensing

This document preserves a line of thinking about how Common Graph relates to commercial licensing, DRM, and legal frameworks for data rights.  It is not a formal specification.  It is a set of observations meant to inform both the white paper's framing and any future business or product strategy built on top of CG.

## The honest view of DRM

DRM's real value proposition, stripped of marketing, is:

- Makes casual copying slightly inconvenient
- Triggers legal consequences (DMCA circumvention laws) that apply even when the copy itself would otherwise be legal
- Provides accounting infrastructure for licensees
- Enables revenue models that would otherwise not close

What it does not do is prevent determined copying.  Every major DRM scheme has been broken, usually within months or years of deployment.  DRM is a commercial signaling and friction layer, not actual prevention.  The studios know this.  They continue to use DRM because nothing better exists in their current stack, not because they believe it works.

## The honest view of legal frameworks

Legal frameworks (GDPR, copyright, contract law, trade-secret law) are real and non-technical.  They work when jurisdictions are functional, enforcement is affordable, counterparties are identifiable, and offenders are worth pursuing.  They do not work at scale against individual pirates, which is why decades of piracy have persisted.  They work fine for corporate disputes, licensed redistribution, and commercial partnerships.  None of this is displaced by Common Graph.  All of it composes naturally with CG's primitives.

## License frames

A license in Common Graph is a signed frame from a vendor's key, asserting that a recipient holds some form of permission with respect to a subject.  A minimal example:

```
LICENSE {
  (AGENT)            = Paramount
  (RECIPIENT)        = alice
  (THEME)            = the-matrix
  (CONFIG, DURATION) = PERPETUAL
  (CONFIG, TRANSFER) = TRUE
  (TIME)             = 2026-01-15
}
```

Signed by Paramount's official key.  Anyone who trusts Paramount can verify the license.  The license does not prevent Alice from copying the movie file.  It does, however, provide several useful properties that map closely to what DRM tries to deliver and delivers worse.

**Status display.**  The license is a frame on Alice's item graph.  It can render as a badge, a shelf item, a collection entry in any interface that understands licenses.  DVDs (more lately BluRay and earlier, VHS tapes) used to do this physically.  Streaming erased the signaling.  Common Graph restores it structurally.

**Benefit gating.**  Streaming services, bonus content drops, community spaces, director commentary releases, and post-release updates can all check for the license and gate accordingly.  The license is not a key that decrypts anything; it is a credential that unlocks benefits.

**Legal clarity.**  When Paramount pursues a pirated copy, the license is provable evidence of what was agreed to, who agreed, and when.  Cleaner than shrink-wrap clickwrap and more tamper-evident than database records.

**Portability.**  Alice's license lives in her graph, not in a Paramount account.  If Paramount's servers go down, the license persists.  If Paramount revokes Alice's ability to stream, the license itself is still there, and the record of what she paid for is intact.  The relationship between Paramount and Alice is recorded, not controlled.

**Transferability.**  If the license says so, Alice can sell it to Bob via a signed TRANSFER frame.  The chain of custody is cryptographic.  This is the digital-used-goods market that has never existed because no substrate supported it.

## The collectibility angle

Licenses-as-frames are inherently collectible.  People display what they have purchased.  The DVD shelf was a social signaling object for two decades.  Streaming erased that signaling because services do not let users display their catalogs.  NFT speculation tried to recreate the signaling but got lost in financialization.

Common Graph offers a third path: verifiable licenses that display as status objects users actually want to have.  A studio that participates in this model gives its customers something streaming took away.  The customer gets pride in what they own.  The studio gets social visibility for its catalog.  The commercial model transitions from prevention theater to access-and-status, which is what it has always actually been.

## Why this works with the substrate, not against it

Common Graph's position on data is that ownership-as-property is the wrong frame, and authorship, custody, and consent are the right ones.  Licenses fit perfectly within that frame:

- A license is **authored** by the vendor (signed with its key)
- It is **held in custody** by the recipient (on their device, in their graph)
- It represents **consent** (the vendor has consented to certain uses; the recipient has consented to the terms)

Nothing about the license claims to prevent copying.  Everything about it enables commercial infrastructure that does not rely on prevention.  The substrate supplies exactly what honest commercial licensing needs and no more.

## Relationship to the white paper

The white paper's Section 10 (Authorship, Not Ownership) critiques the "own your data" rhetoric and proposes authorship, custody, and consent as more honest framings.  That section should acknowledge, briefly, that legal and commercial frameworks compose naturally with CG without being displaced by it.  A one-sentence addition would suffice.

The deeper point, which does not need to be in the paper but is worth remembering: Common Graph does not fight the legal or commercial stack.  It makes that stack more honest, more composable, and easier to build on.  A vendor using CG-native licensing can say what DRM vendors cannot: "This works with what you already know works (legal and social enforcement) and drops what you already know does not (technical prevention)."
