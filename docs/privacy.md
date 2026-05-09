# Privacy

This document examines how privacy works under Common Graph: what threats it addresses, what layers of privacy it provides, what configurations are possible at the extreme, and what remains outside the substrate's ability to enforce.  It is reference material for pitches, design discussions, and implementation decisions, not a formal specification.  Some mechanisms described are design intent rather than current implementation; where that distinction matters, it is flagged.

Privacy is always privacy-from-whom.  A document that begins without a threat model drifts into hand-waving; one that claims general-purpose privacy protection promises more than any system can deliver.  This document begins with the question of what CG is protecting against, then walks the mechanisms by which it does so, then is honest about the limits.

## Threat models

The protections CG offers depend substantially on what you are protecting against.  The same configuration that is strong privacy against casual surveillance may be inadequate against a determined attacker, and vice versa.  CG is a toolkit from which users configure protection appropriate to their situation.

Five threat models worth distinguishing:

**Casual observer.**  Someone with curiosity, no specific target, limited resources.  Co-workers glancing at your screen.  Friends of friends reading your posts.  Random peers on the network.  This is the default case, and the one most user-facing privacy software addresses.

**Commercial surveillance.**  Platforms, advertisers, data brokers, analytics companies.  Not individually malicious, but structurally invested in extracting patterns from user behavior.  The most pervasive threat in current computing and the primary one addressed by CG's overall design.

**Coerced peer.**  Someone in your trust graph compelled to share what they have received from you.  An ex-partner with your private messages.  A former friend subpoenaed.  A business partner now in a dispute.  This is a social failure mode that technology cannot solve; the best mitigation is careful initial sharing.

**Determined attacker.**  Individual or organization with specific interest in compromising your privacy, with time and some resources.  Targeted harassment, competitor espionage, stalker-level persistence.  Requires stronger configurations; some threats in this class cannot be addressed by a local-first substrate alone.

**State-level adversary.**  Traffic analysis over global networks, legal compulsion, targeted exploitation, mass surveillance.  The hardest threat.  CG provides primitives useful against this adversary (transport anonymity, encryption, minimal metadata) but cannot guarantee protection against the full capabilities of a state actor.

The appropriate question for any CG user is not "is this secure?" but "secure against whom, under what configuration, with what trust assumptions?"  Different answers follow from different threat models.  The rest of this document describes what CG provides; matching those provisions to a threat model is the user's job, the implementer's job, or the pitcher's job.

## The four axes of privacy

Privacy in CG operates on four mostly-independent axes.  A user might want strong protection on one and minimal on another: pseudonymous posting with publicly-addressed content, or strongly-encrypted content with openly-visible participation, or anonymous reading with attributed authoring.

### Relational privacy: who can see your content

This is the axis CG addresses most natively.  Every frame can carry policy bindings that determine who can receive it.  The trust graph is the access control mechanism: data flows only to peers you have chosen to trust, at the granularity you have chosen to trust them.

Mechanisms:

- **Trust graph routing.**  Your Librarian relays frames only to peers you have authorized, and relays to each peer only what your policies permit them to see.
- **Frame policies.**  Any frame can carry CONFIG bindings specifying visibility (public, private to specific recipients, encrypted to specific keys, time-limited, etc.).
- **Per-peer granularity.**  Trust is not all-or-nothing.  You might trust Alice for your photos but not for your financial records; your Librarian enforces the distinction.
- **Granular revocation.**  Trust can be revoked; future sharing stops.  Past sharing cannot be undone (see Limits, below).

The default under CG is that frames are not visible to anyone until you deliberately share them.  This inverts the current web's default (public-unless-you-lock-it-down) to the more conservative private-unless-you-share.  Users explicitly choose exposure per-item, not per-account.

### Content confidentiality: what's in what they see

Separate from whether someone receives a frame is what they can read once they do.  Frames can be encrypted such that only specific recipients can decrypt them.

Mechanisms:

- **End-to-end encryption to specific recipients.**  A frame encrypted to Alice's key is unreadable to any peer that relays it, including your own relays along the trust path.
- **Multi-recipient encryption.**  A frame encrypted to a group (Alice, Bob, Carol) is readable by each but not by peers who relay to them.
- **Separate encryption for separate audiences.**  A frame can exist in multiple encrypted versions for different audiences, each readable only by its intended recipients.
- **Content-addressed encrypted payloads.**  The encrypted bytes are content-addressed like anything else; verifying that the encrypted form has not been tampered with does not require decrypting it.

The practical consequence: you can publish frames that travel through peers you do not fully trust, confident that those peers cannot read what they relay.  This is the end-to-end encryption model applied not only to messages but to any frame in the substrate.

### Transport-layer privacy: who knows you're communicating

Even when content is encrypted, the fact of communication is information.  Who sends frames to whom, when, how often, and of what size reveals patterns.  Transport-layer privacy addresses this axis: not what is said, but that communication is happening.

Mechanisms (planned; routing policy is part of the design but not yet fully implemented):

- **Routing policy with HOPS.**  Any delivery can be configured to pass through intermediate peers.  HOPS=0 is a direct connection (the default).  HOPS=1 through a specific peer is what VPNs provide.  HOPS=N with layered encryption is equivalent to onion routing.
- **Peer rotation.**  Different deliveries can use different routes, preventing long-term correlation.
- **Timing and volume policies.**  Deliveries can be batched, delayed, or padded to resist traffic-analysis attacks.

The trade-off is latency: each hop adds round-trip time, and anonymity-grade configurations are meaningfully slower than direct connections.  Users and applications choose per-context rather than globally; most interactions use direct routing, while sensitive activity invokes higher HOPS values.

This axis is where CG subsumes the roles currently played by Tor, VPN services, and various proxy infrastructures.  A single primitive (routing policy) expresses the full range from direct to maximally-anonymous.  A VPN service, in this framing, becomes a Librarian with an arrangement to relay at HOPS=1; the industry built around proprietary relay networks collapses into commodity participation.

### Identity privacy: who you are

Identity in CG is a keypair, not a registered account.  This enables strong pseudonymity by default, and several configurations that go further.

Mechanisms:

- **Pseudonymous by default.**  An identity is a public key.  Unless the user frames real-world identifying information to it (name, email, photo, social links), the identity reveals nothing about the person behind it.
- **Multiple identities.**  A user can operate multiple distinct identities simultaneously, using different keys for different contexts: one for professional work, another for political activity, another for anonymous publishing.
- **Unlinkability between identities.**  Identities maintained on separate devices, or through separate Librarians, with separate trust graphs and separate relay patterns, are structurally hard to link.  Linkage requires correlation signals the user chose to leak.
- **Identity rotation.**  Keys can be rotated on a schedule, generating fresh identities with no cryptographic linkage to prior ones.
- **Attribution vs anonymity.**  CG provides strong pseudonymity (consistent identity without real-world linkage).  True single-use anonymity (publishing without any persistent identity) is possible through ephemeral keypairs but less native; most use cases are better served by pseudonymous identity with appropriate separation.

The practical consequence: users do not have to be themselves, cannot be forced to be themselves by the substrate, and can maintain as many separate identities as their threat model warrants.  This is a stronger default than any platform-mediated system provides.

## Defaults and explicit choices

CG's privacy design intent is that privacy is **deliberate, not default-public**.  Defaults lean conservative; explicit choices open exposure.

Default behaviors:

- New frames are not shared automatically; sharing requires explicit action
- New items are not discoverable automatically; discoverability requires explicit policy
- New identities are pseudonymous; real-world identification is explicitly added, not assumed
- Routing is direct by default; anonymity-grade routing is explicitly configured per-context

Explicit choices:

- Public frames are marked as such
- Shared frames specify recipients
- Discoverable items specify discoverability scope
- Identities gain real-world bindings only when the user adds them
- Anonymity-grade routing is opted into per-session or per-context

This inversion of the platform-era default (public-unless-locked-down) is not only a privacy choice but a usability one: users have a chance to understand what they are sharing before they share it, rather than having to audit and retract what has already been published without their intention.

## Maximum privacy configuration

CG is a toolkit, and its toolkit allows configurations at the extreme end of the privacy spectrum.  What does a "maximum privacy" configuration look like?

- **Identity.**  Pseudonymous keypair with no real-world bindings.  No name, email, or other personally-identifying information associated.  Multiple identities for different contexts, maintained on separate devices to resist correlation.  Key rotation on a schedule appropriate to the threat.
- **Content.**  All frames encrypted to specific recipients.  No public frames at all.  Multi-layer encryption where appropriate (e.g., encrypting both the content and the metadata describing the content).
- **Transport.**  All deliveries routed through N hops (N chosen based on threat model; 3-7 is typical for onion-style anonymity).  Peer rotation between deliveries.  Timing and volume policies active.
- **Trust graph.**  Minimal, intentional, carefully vetted.  Each trusted peer is a deliberate choice, not inherited from a social network.  Private annotations on trust (trust Alice for X, not Y) enforce granular boundaries.
- **Metadata hygiene.**  No unencrypted metadata leaks.  Content-type and size obfuscation where possible.  Conservative posting schedule to avoid timing correlation.
- **Device hygiene.**  Physical security of devices.  Encrypted local storage.  Authenticated boot.  Careful handling of key material.  Separation of sensitive identities onto isolated hardware when the threat warrants.

With this configuration, a user can participate in CG (creating items, sharing frames, communicating with chosen peers) without a casual observer, a commercial surveillance system, or most determined attackers being able to identify them or correlate their activities.  A state-level adversary with sufficient resources could potentially defeat parts of this through exotic attacks (traffic analysis at global scale, side-channel exploitation, legal compulsion of endpoints), but the configuration is substantially stronger than what any current platform-mediated system offers.

The cost of maximum privacy is latency, inconvenience, and the discipline required to maintain the configuration.  Most users, most of the time, want something lighter.  The substrate supports the extreme case, however, for users whose situations require it: dissidents, journalists protecting sources, survivors of abuse, whistleblowers, activists operating in hostile jurisdictions.  These use cases are first-class, not afterthoughts.

## What is technically enforceable vs what is social

A persistent theme across privacy discussions: the substrate enforces some things cleanly and cannot enforce others.  Being precise about which are which prevents overclaiming.

**Technically enforceable:**

- Integrity of signed frames (any modification breaks the signature)
- Confidentiality of encrypted content (without the key, the content is not readable)
- Routing through specified hops (each hop only knows the next, by construction)
- Policy on which peers receive a frame (your Librarian relays only to specified recipients)
- Pseudonymity of identities (no mechanism reveals real-world identity unless the user provides it)

**Not technically enforceable:**

- What recipients do with content they have received
- Whether recipients keep, forward, publish, or destroy what they receive
- Re-sharing of content by authorized parties
- Inferences from observed patterns (writing style, posting times, topical interests)
- Coerced disclosure of keys or content
- Correlation of behavior across identities the user imperfectly separates
- Metadata correlations above the transport layer

The substrate's job is to provide the technical layer.  The social layer (trust, reputation, norms, legal recourse) handles the rest.  No system that preserves human agency can enforce what recipients do with what they legitimately received; the partial answer is to choose recipients carefully, and to maintain the social and legal structures that punish betrayal of trust when it occurs.

## Composability with legal frameworks

CG does not displace any legal framework around privacy.  Everything that applies today still applies.  What CG provides is primitives that make compliance cleaner and enforcement more effective.

**GDPR.**  Right to access, right to deletion, right to portability, right to know.  CG supports each structurally: users hold their own data by default (portability), can audit what they have shared and with whom (access, transparency), and can technically revoke future sharing (deletion from future flow, though not past copies).  Compliance becomes a property of the substrate rather than a bolted-on obligation.

**HIPAA.**  Minimum necessary disclosure, auditable access, local custody.  All three align with CG's design.  See [`pitches.md`](pitches.md) healthcare section for the detailed argument.

**Attorney-client privilege.**  End-to-end encryption of frames between client and counsel is the native configuration.  Metadata protection for the existence of the relationship is a transport-layer configuration.  CG strengthens privilege protection without changing its legal nature.

**Journalistic source protection.**  Pseudonymous identities, transport anonymity, and encrypted communication are all native.  Sources communicating with journalists via CG with appropriate configuration face substantially less surveillance exposure than email or current messaging systems provide.  Legal protections (shield laws, reporter's privilege) still apply and compose.

**Right to be forgotten.**  The hardest case.  CG can technically stop future sharing but cannot compel deletion of past copies.  This is a structural property of copyable information, not a CG-specific limitation.  The legal framework's "right to be forgotten" applies to the parties holding copies; CG provides the audit trail showing who received what, which supports enforcement against parties subject to the legal framework.

## Limits and failure modes

Worth naming explicitly so readers do not come away with overconfidence.

**You cannot unshare.**  Once you have sent a frame to someone, they have it.  You can stop sharing future frames, but the copies they hold persist.  This is a property of copyable information and applies to every system.  CG does not pretend otherwise.

**You cannot prevent legal compulsion.**  A subpoena or court order served on a peer who holds copies of your frames will, in most jurisdictions, produce those copies.  Jurisdiction-shopping (choosing peers in friendly jurisdictions) is a partial mitigation, not a cure.

**You cannot prevent coercion.**  A trusted peer compelled by force, social pressure, or financial incentive to share what they have will do so.  The substrate does not enforce loyalty.

**Key loss means identity loss.**  If you lose your signing keys, you lose the ability to author as that identity.  If a backup does not exist, the identity is gone.  Key management is the single most important operational security concern.

**Key compromise is catastrophic.**  If your keys are stolen, the attacker can sign as you, decrypt content meant for you, and impersonate you to your trust graph.  Detection, revocation, and recovery protocols matter greatly; this is an area where CG's design must be deliberate and careful.

**Metadata leaks are hard to eliminate.**  Even with strong content encryption and transport anonymity, your writing style, posting times, topics of interest, and other high-level patterns can identify you.  True unlinkability between identities requires discipline that exceeds most users' patience.

**Device compromise defeats most protections.**  If an attacker has access to your device (physical, malware, legal), they have access to the keys and to whatever is decrypted locally.  Device security is outside CG's scope but is the weakest link for most users.

**Social engineering defeats technical controls.**  Users can be tricked into sharing things they meant to keep private, into trusting peers they should not, or into revealing identity information that links pseudonyms.  The technical layer does not defend against this; education and cautious defaults are the mitigations.

## Comparisons to existing privacy stacks

How does CG relate to the privacy tools people already use?

**Signal and end-to-end encrypted messengers.**  CG's frame-level encryption subsumes the E2EE messaging pattern.  A messaging app built on CG is a client that sends encrypted frames between participants.  The capabilities Signal provides (forward secrecy, deniability, sealed sender) are architectural choices that can be expressed in CG's encryption design.

**PGP and email encryption.**  PGP solves the problem CG solves (end-to-end encrypted assertions between parties with cryptographic identities) but with historically terrible usability.  CG inherits PGP's insight and pairs it with better identity management and substrate-level support.  The "nobody uses PGP" problem is largely a UX and key-management problem; CG's design targets both.

**Tor and mix networks.**  Subsumed via routing policy with HOPS.  Tor is one specific configuration of what CG expresses generally.  Users with Tor-level anonymity requirements configure CG routing accordingly; no separate overlay is required.

**VPN services.**  Subsumed via HOPS=1 routing.  A VPN becomes a Librarian with a relay arrangement.  The VPN industry, currently a specialized infrastructure layer, collapses into commodity relay participation.

**Anonymous publishing systems (FreeNet, SSB, others).**  Covered by the combination of pseudonymous identity, encrypted content, and anonymity-grade routing.  The functionality of these systems is achievable in CG as configurations rather than as separate platforms.

**Hardware security modules and secure enclaves.**  Orthogonal.  CG benefits from running on devices with hardware-backed key storage but does not require it.  Users with high-assurance requirements use whatever hardware security their threat model demands; CG's design composes with it rather than reimplementing it.

**Self-sovereign identity (SSI) systems (DIDs, Verifiable Credentials).**  CG's key-based identity is a superset.  DID-style identifiers and VC-style signed claims are expressible as CG items and frames.  The SSI movement's goals align with CG's; its W3C standards can be expressed in CG's vocabulary.

## Privacy in specific domains

Brief treatment of domain-specific applications.

**Healthcare.**  HIPAA's requirements for local custody, minimum-necessary disclosure, and auditable access align with CG's design.  Granular, revocable consent for research access replaces the current bulk-DUA model.  See [`pitches.md`](pitches.md) healthcare section for the detailed argument.

**Financial.**  Auditability (regulatory requirement) and confidentiality (customer expectation) are both supported.  Signed transaction frames provide auditable history; encryption to counterparties provides confidentiality.  Regulatory auditors can be granted time-limited access to specific frames rather than bulk access to databases.

**Legal.**  Attorney-client privilege strengthened by native E2EE.  Work product protection via trust-graph boundaries.  Discovery compliance via audit trails.  Matter-specific trust graphs allow strict separation of representations.

**Journalism.**  Source protection via pseudonymous identity and anonymity-grade routing.  Communication confidentiality via frame encryption.  Publication via signed frames with whatever attribution the source authorizes.  Newsroom collaboration on sensitive stories via scoped trust graphs.

**Personal.**  The most common case.  Default-private frames with explicit sharing to specific peers.  The risqué-photograph scenario from the white paper.  Medical, political, sexual, and relationship information handled by user-chosen sharing rather than platform-default exposure.

**Activism and dissent.**  Maximum-privacy configuration applies.  Pseudonymous identities, onion-grade routing, encrypted content, minimal trust graphs.  Compatible with operating in hostile jurisdictions provided device and key hygiene are maintained.

## UX considerations

Privacy that users cannot understand or audit is not privacy.  Any client built over CG must present the privacy state clearly.

Principles for privacy-respecting UX:

- **Visibility before sharing.**  Users see, in plain language, who will receive a frame before they publish it.
- **Auditable history.**  Users can inspect what they have shared, with whom, and when.
- **Meaningful revocation.**  When users revoke sharing, they see clearly what future flow stops and what past copies persist (the latter is a limitation of the medium, not of the client).
- **Default conservatism.**  New frames are private until shared.  New identities are pseudonymous until linked.  Discoverability requires explicit opt-in.
- **No dark patterns.**  The interface should not nudge users toward exposure.  Privacy-by-default includes interface-by-default.
- **Threat-model literacy.**  The client helps users understand what kind of privacy their current configuration provides and against whom.  Configuration presets (everyday, careful, maximum) with clear explanations of trade-offs.

Implementation is per-client, but the principles should be observed by any serious CG client.

## Notes for pitches

For privacy advocates and journalists:

> CG is not a privacy tool in the sense that a VPN or encrypted messenger is.  It is a substrate on which privacy-respecting applications become the default, not the exception.  Transport-layer anonymity, end-to-end encryption, pseudonymous identity, and minimal-disclosure sharing are all native configurations.  Users who need maximum privacy can configure for it; users who do not still benefit from a stack that does not assume their data should be public.

For healthcare and regulated industries:

> Privacy compliance becomes a property of the substrate rather than an ongoing audit obligation.  Minimum necessary disclosure, local custody, auditable access, and granular consent are native.  See pitches.md for domain-specific framings.

For enterprise security:

> Data loss through exfiltration becomes structurally harder when there is no central database to exfiltrate from.  Authentication via keypairs eliminates credential stuffing and phishing as attack vectors at the substrate level.  Insider threats remain (coerced peers, compromised accounts) but are bounded by trust-graph granularity.

For skeptics:

> We do not claim to solve all privacy problems.  We are honest about what the substrate enforces (integrity, confidentiality, routing, pseudonymity) and what it does not (coerced disclosure, inference from patterns, social compromise).  The legal and social layers remain necessary; CG strengthens them rather than displacing them.

## Relationship to other documents

- [`the-case.md`](the-case.md) Section 10 (Authorship, Not Ownership) makes the broader argument about what a substrate can and cannot enforce; this document applies that distinction specifically to privacy.
- [`analytics.md`](analytics.md) describes what kinds of data become observable or invisible under CG; this document addresses the user's privacy from those observations.
- [`encryption.md`](encryption.md) covers the cryptographic mechanisms in technical detail.
- [`trust.md`](trust.md) covers the trust graph, which is the access-control mechanism referenced throughout.
- [`network.md`](network.md) covers networking; routing policy lives there when fully specified and built.
- [`licensing.md`](licensing.md) covers commercial licensing, which composes with but is distinct from privacy.
- [`advertising.md`](advertising.md) covers the advertising industry's relationship to CG; the privacy properties described here are what enable the consent-based advertising model discussed there.
