# Pitches

This document collects sales and partnership pitches for Common Graph, organized by target audience.  Each pitch is written as text a vendor could actually deliver, with enough substance to handle the initial questions and objections from someone evaluating the platform.

These are working drafts, intended to evolve as the system matures and feedback from actual pitches comes in.

## Studios and entertainment rights-holders

**The question they will ask.**  "How does your platform prevent users from copying our content?"

**The honest answer.**

We do not prevent copying.  Neither does DRM, despite decades of marketing to the contrary; every major DRM scheme has been broken, usually within months of deployment.  What we offer instead is a licensing infrastructure that delivers what DRM actually delivers, without the brittleness.

On Common Graph, your licenses become verifiable, portable, collectible objects your users actually want to have.  A license is a signed frame issued by your official key, attached to the user's graph, stating exactly what they have purchased, when, and under what terms.  Any benefit you currently hang off a DRM check can hang off a license check instead: streaming access, bonus content, director commentary, community spaces, post-release updates.

The users who want your content enough to pay will pay, because the license gives them status and access.  The users who pirate will pirate, as they always have.  You stop paying for DRM theater and start selling something consumers actually value: provable, portable ownership of their relationship to your catalog.

**What changes for the customer.**

They get their DVD shelf back.  The social signaling of "I own this" returns, in digital form, without the NFT speculation baggage.  Their licenses persist even if your service goes down.  If your terms permit, they can transfer licenses to others, opening up a digital used-goods market that has never existed because no substrate supported it.

**What changes for you.**

You stop paying for DRM vendors whose product everyone knows is theater.  Your licenses become first-class, verifiable commercial instruments.  Legal enforcement against piracy is unchanged and, if anything, cleaner: the license is provable evidence of what was agreed to, who agreed, and when.  You gain a platform where customer loyalty is visible (users display their collections) and where you can build direct relationships with customers rather than renting them from an intermediary streaming service.

**See also:** [`licensing.md`](licensing.md) for the technical pattern and deeper rationale.

## Enterprise content operations (CMS buyers)

**The question they will ask.**  "We've already invested millions in our current CMS.  Why would we risk touching that?"

**The honest answer.**

You have already paid the cost we are talking about avoiding next time.

If your organization has been through a CMS migration in the past ten years (Magnolia to AEM, Sitecore to Contentful, Drupal to anything, or the inverse of any of these) you know the real cost.  It was not the licenses.  It was the bespoke re-mapping of every piece of content, every custom field, every workflow, every integration with your DAM, your personalization engine, your analytics, your commerce system, your translation memory, your search.  It was the months or years of parallel operation.  It was the author retraining and the stakeholder hand-holding.  Those costs are the real price of the enterprise CMS industry, and no vendor quotes them upfront because they are what the lock-in produces.

Common Graph does not replace your CMS.  It replaces what your CMS currently insists on owning: the schema your content is structured around.  With CG as your content substrate, the CMS becomes an authoring and workflow tool over the substrate, not a proprietary prison your content lives inside.  The content itself lives in a shared semantic layer where every field describes itself, portable across any tool that speaks the vocabulary.

**What that means in practice.**

Your next migration is not a migration.  It is a new authoring tool connected to the same content.  Your translators plug in without bespoke integration.  Your personalization engine, search, analytics, commerce, CRM, translation memory, image DAM, and video platform all read the same content directly, because the content is not opaque bytes in one vendor's database but structured, self-describing frames any tool can understand.

**What changes for your content authors.**

Visually, almost nothing.  A CG-backed CMS (built by you, by us, or by a third party) looks and feels like a CMS.  The difference is underneath: every field maps to a grounded meaning rather than a vendor-defined string.  What they author is immediately queryable and immediately portable.

**What changes for your IT organization.**

The integration tax drops toward zero.  Every new tool that speaks CG reads your content directly; every new content type is a vocabulary extension rather than a database migration.  Your content-ops team stops spending its cycles on systems integration and starts spending them on what the content actually needs.

**What changes for your budget.**

You stop paying the migration tax that every CMS vendor has implicitly priced into your total cost of ownership.  Per-seat authoring-tool fees continue, but they start competing on authoring experience rather than on lock-in.  When a better authoring tool comes along, switching becomes a decision about authoring experience, not a multi-million-dollar content-migration project.

**The transition.**

You do not rip and replace.  You run your existing CMS alongside CG as a parallel substrate that mirrors your content.  New content types and integrations move to CG-native first.  When your CMS license renews, switching is a real option.  When a new microsite or product launch would have required a new CMS integration, it can launch on CG without one.

This is not a bet-your-operations-on-a-young-platform proposition.  It is a proposition to start hedging against the next migration, with the substrate doing the hedging for you.

## Healthcare records and patient data

**The question they will ask.**  "How does this fit with FHIR, HIPAA, and our Epic install?"

**The honest answer.**

It coexists with all three, and is specifically designed to make the problems FHIR and HIPAA are still trying to solve actually tractable.

Semantic interoperability has been an open wound in healthcare for thirty years.  HL7 V2 partially solved messaging.  CDA partially solved document structure.  FHIR has made real progress on common resources.  But every cross-vendor integration remains a custom mapping project, every patient's record is still fragmented across providers who cannot share effectively, and every attempt to give patients portable access to their own medical history has run into the same wall: the data is not structured to be portable, regardless of what the law says.

Common Graph is a substrate beneath these standards, not a replacement for them.  FHIR's vocabulary becomes a set of sememes in the shared commons.  FHIR resources become frames.  The semantic work FHIR has already done is reused directly; what changes is that the data using this vocabulary is now signed, content-addressed, and structurally portable.

**What this changes for interoperability.**

Integrations stop being bespoke.  A cardiology device, an EHR, a lab system, a wearable, an insurance system, and a research platform that each speak CG can read each other's signed frames directly.  The FHIR bridge that every health system currently builds and maintains disappears as a category of project.

**What this changes for patients.**

Patients hold their own data.  Not in theory (which HIPAA already grants them) but in practice (which no current system delivers).  Every piece of a patient's record, from a blood-pressure reading to a surgical report, can be replicated to a patient-held node with the same frame structure and the same cryptographic verifiability.  When a patient switches providers, their record goes with them.  When they seek a second opinion, they grant the new provider access to their node rather than requesting a records transfer.  When they participate in research, they grant time-limited access to specific frames rather than signing away bulk rights.

**What this changes for HIPAA compliance.**

HIPAA favors local custody, minimum-necessary disclosure, and auditable access.  CG provides all three natively.  Trust-based routing means data flows only to peers the data-holder has explicitly authorized.  Signed frames mean every disclosure is attributed and auditable.  Content-addressed storage means there is one canonical version of every record, verifiable across all copies.

**What this changes for research.**

The current model is bulk data sharing under a data-use agreement, with custodial responsibility dumped on the receiving institution.  CG enables granular, revocable access to specific frames, with cryptographic audit of who accessed what and when.  The institution's exposure drops.  The patient's consent becomes technically meaningful rather than paper-meaningful.

**The transition.**

You do not rip and replace Epic.  You start with a CG-backed patient-facing layer that mirrors the patient-accessible subset of the EHR.  Patients who opt in have a CG node that subscribes to updates from the Epic system through a FHIR-to-CG adapter.  Over time, inter-provider data exchange moves to CG-native; then integrations with wearables and third-party apps; then research workflows.  The EHR remains the provider-facing authoring system, but the substrate underneath stops being siloed per vendor.

**What changes for your budget.**

The interoperability line item stops being an ongoing tax and starts being an occasional vocabulary-extension cost.  The HIE patchwork, the clearinghouses, the FHIR adapters that every new integration requires: most of it becomes unnecessary for new work.  Your CIO stops pricing interoperability as a recurring operational expense and starts pricing it as a property of the substrate.

---

*Future pitches to draft as target industries come into focus: libraries and archives, academic research / scientific publishing, journalism and content provenance, gaming (item portability), museums and cultural heritage, legal tech, enterprise knowledge management.*
