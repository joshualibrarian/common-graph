# Bridges on Common Graph

Common Graph is not an island.  Decades of computing infrastructure already exists, and billions of users live inside it: email, the web, federated social networks, identity standards, content distribution systems, real-time communication protocols.  CG cannot replace any of these by force, and should not try.  What it can do is *bridge* — speak existing protocols at the boundary, translate between them and frames, and let users move at their own pace.

This document lays out the bridge architecture, the ecosystems worth bridging to, and the mappings.  The unifying principle: a bridge is an item.

## Bridges are items

A bridge is not infrastructure outside the graph.  It is a **service item** — an item whose archetype declares it as a service running an external protocol on behalf of one or more users or items.

A bridge item carries frames describing what it does, what protocol it speaks, what endpoints it exposes, who operates it, who it serves, and what policies govern its translations.

```
SMTP_BRIDGE  (item, archetype: BRIDGE_SERVICE)

    SERVES       { THEME → bridge,  VALUE → SMTP_PROTOCOL }
    LISTENS      { THEME → bridge,  VALUE:[ENDPOINT] → "mail.example.com:25" }
    LISTENS      { THEME → bridge,  VALUE:[ENDPOINT] → "mail.example.com:587" }
    BRIDGES_FOR  { THEME → bridge,  AGENT → alice }
    OPERATED_BY  { THEME → bridge,  AGENT → bridge-operator }
    POLICY:[CONFIG] → translation-rules-item
    IMPLEMENTATION { THEME → bridge, VALUE:[JAVA] → <bridge-impl-cid> }
```

Because a bridge is an item, it inherits everything other items get:

- **Discoverability.**  Query the graph for "all SMTP bridges" or "all bridges Alice trusts."
- **Trust evaluation.**  The trust matrix decides whether to accept frames produced by a bridge.  A bridge that mistranslates loses trust the same way any other contributor does.
- **Auditability.**  Every translation can produce a TRANSLATED frame, recording what came in, what went out, when, and by which bridge.  The translation record is itself a signed assertion.
- **Replaceability.**  The bridge implementation is itself an item.  A better implementation can be published and pointed at without changing the bridge's identity, endpoints, or operational policy.
- **Composability.**  A user can have multiple bridges per protocol — personal mail and work mail, with different policies, different operators, different trust profiles.
- **Polyglot.**  A bridge can be implemented in any language the runtime supports (Java, Python, Rust via WASM, Clojure, JavaScript).  The IMPLEMENTATION frame's language qualifier tells the runtime what it needs.

Bridges are not special.  They are services running on Librarians, exposing themselves to external systems, producing and consuming frames at the boundary.  The graph doesn't care that the frames came from a bridge; it cares about who signed them and whether the trust matrix says to weight them.

## Bridge sandboxing

A bridge runs untrusted code by definition — it speaks external protocols and processes external input.  Bridges run in sandboxed contexts (see `runtime-polyglot.md`), with capability restrictions appropriate to their role.  An SMTP bridge needs network access on its listening ports but does not need filesystem access; an HTTP bridge needs to fetch remote URLs but does not need to spawn subprocesses; a Git bridge needs to run hash computations and respond to clone requests but does not need to access other items' data.

The sandbox policy is a CONFIG binding on the bridge item:

```
CONFIG:[SANDBOX] → policy-item

policy-item declares:
    network access (allow listed endpoints)
    filesystem access (none, or read-only specific paths)
    CPU/memory limits
    accessible host APIs (which Librarian functions the bridge can call)
```

The trust matrix can drive sandbox tightness: a bridge from a trusted operator with a long history can have looser restrictions; a new bridge from an unknown author runs with maximum isolation.

## Inbound and outbound translation

Every bridge has two directions:

**Inbound.**  External protocol → frames.  An SMTP message arrives; the bridge parses it and produces a MESSAGE frame on the recipient's inbox item.  An HTTP request arrives; the bridge produces a corresponding query or DISPATCH frame.

**Outbound.**  Frames → external protocol.  A MESSAGE frame is created targeting an external email address; the bridge translates it to an SMTP message and delivers it.  A query needs to fetch external data; the bridge issues the HTTP request and returns the result as frames.

A bridge typically supports both directions, though some are inherently one-way (an RSS bridge might only consume external feeds, never publish to them).

## The ecosystems

What follows is a survey of ecosystems worth bridging to, organized by domain.  Order is roughly by leverage and priority, not by technical difficulty.

### Foundational

These are universal — every internet user touches them.

#### HTTP / HTTPS

The web.  Already partially built into CG: a Librarian can expose a web gateway that renders items as HTML pages, accepts form submissions as frame creation, and serves content addressable resources.

**Inbound:** HTTP requests become frame creation events (form data → frame bindings) or query dispatches (URL paths → frame patterns).
**Outbound:** Frames render to HTML/JSON/CBOR depending on Accept headers; items are served at stable URLs derived from their IIDs.

Mapping highlights:

```
HTTP request                    →  frame
  Method (GET/POST/PUT/DELETE)  →  predicate (READ / CREATE / UPDATE / DELETE)
  URL path                      →  THEME (the targeted item)
  Headers                       →  CONFIG bindings
  Body                          →  VALUE binding (parsed by content type)

CG frame                        →  HTTP response
  Predicate                     →  resource type
  Bindings                      →  rendered fields
  Body content                  →  response body (HTML, JSON, etc.)
```

#### DNS

Names and discovery.  A DNS bridge translates between domain names and item identifiers, enabling `alice.example.com` to resolve to a CG item with a specific IID.

**Inbound:** DNS queries map to lookups on items with NAME bindings carrying the domain qualifier.
**Outbound:** Item bindings can publish DNS records (TXT records carrying IIDs, MX records pointing at email bridges, SRV records advertising services).

#### Email (SMTP, IMAP, POP3, MSA)

Universal communication.  Every adult has an email address.  A complete email bridge plays four roles:

```
MUA  — Mail User Agent          (Thunderbird, Outlook, Apple Mail)
MSA  — Message Submission Agent (where users submit outgoing mail, port 587)
MTA  — Mail Transfer Agent      (server-to-server delivery, port 25)
MDA  — Mail Delivery Agent      (final delivery to mailbox)
```

For full interop, a bridge needs to handle all four:

- **MUA-side (IMAP / POP3):** Speak IMAP and POP3 to existing mail clients.  Frames in the user's inbox are presented as IMAP folders; MESSAGE frames become email messages.  The user reads CG-bridged mail in Thunderbird without the client knowing it's not a regular IMAP server.
- **MSA-side:** Accept outgoing mail on port 587 with SMTP AUTH.  The user's MUA submits messages; the bridge translates them to MESSAGE frames and routes them — either to other CG items (if the recipient is in the graph) or out via SMTP to external addresses.
- **MTA-side:** Receive SMTP on port 25 from external senders.  Incoming messages become MESSAGE frames on the recipient's item.  Spam filtering, DKIM verification, and SPF checks are performed; the results become CONFIG bindings on the resulting frame.
- **MDA-side:** In CG, "delivery" is just creating the MESSAGE frame on the recipient's item.  No separate delivery system is needed.

Mapping:

```
Email message                   →  MESSAGE frame
  From                          →  AGENT
  To, Cc                        →  RECIPIENT (multiple bindings)
  Subject                       →  VALUE:[SUBJECT]
  Body (text/plain or HTML)     →  VALUE:[BODY] with format qualifier
  Headers                       →  CONFIG bindings
  Attachments                   →  VALUE bindings with content references
  Message-ID                    →  SOURCE:[EMAIL_MESSAGE_ID]
  In-Reply-To                   →  FOLLOWS (linking the reply chain)
  Date                          →  TIME
```

The reply chain (`In-Reply-To` and `References` headers) maps directly to FOLLOWS bindings, giving you natural threading.  Spam economics shift: incoming mail is signed and attributable through the trust matrix.  A message from an unknown sender with no trust path scores low; the user's threshold determines whether it reaches their attention.

#### Git

Version control.  Frames already have Git-like properties: signed, content-addressed, hash-linked via FOLLOWS, with manifest VIDs functioning as commits.  A Git bridge exposes CG repositories to git clients via the standard git protocols (HTTP, SSH, native).

**Inbound:** A `git push` becomes a manifest update; commit objects map to manifest versions; tree objects map to item structures.
**Outbound:** A `git clone` against a CG repository serves up the manifest history as git commits; the working tree is reconstructed from the current item's frames.

Mapping:

```
Git commit                      →  manifest version
  SHA                           →  VID
  Parent commit(s)              →  FOLLOWS bindings (multiple = merge)
  Author                        →  AGENT (signer)
  Message                       →  VALUE:[COMMIT_MESSAGE]
  Tree                          →  the item's frame structure at that version

Git tree                        →  item with file frames
Git blob                        →  content (referenced by CID)
Git tag                         →  named version (a frame)
Git branch                      →  a head reference (per-principal latest VID)
```

The structural similarity is so strong that this bridge is largely a translation layer; the underlying semantics already match.

#### WebFinger

Lightweight identity discovery.  Used by ActivityPub, Mastodon, and other federated systems to resolve `user@domain.tld` to an actor URI.  A WebFinger bridge maps these queries to lookups on CG user items, returning the item's IID and discovery endpoints.

### Federated communication

Real users, real networks, real-world relevance.

#### ActivityPub

The Fediverse: Mastodon, Pleroma, Lemmy, PixelFed, Peertube.  Millions of users on a federated social network.  ActivityPub messages are JSON-LD activities — signed assertions with subject-verb-object structure.

Mapping is direct:

```
ActivityPub Activity            →  frame
  type (Create, Follow, Like)   →  predicate
  actor                         →  AGENT
  object                        →  THEME
  target                        →  RECIPIENT or GOAL
  content                       →  VALUE
  inReplyTo                     →  FOLLOWS

ActivityPub Actor               →  user item
  inbox / outbox                →  endpoints exposed by the bridge
  publicKey                     →  PUBLIC_KEY frame
  followers / following         →  FOLLOW frames

ActivityPub Object              →  item or content reference
  Note, Article                 →  text content
  Image, Audio, Video           →  media content
```

A bridge speaks HTTP signatures for inbound activity verification and translates incoming activities to frames.  Outbound: frames become activities, signed with the user's key, posted to recipients' inboxes.

#### Matrix

Decentralized chat with optional E2E encryption.  Rooms become items; messages become MESSAGE frames; membership becomes MEMBER frames.

Mapping:

```
Matrix room                     →  item (archetype: ROOM)
Matrix event                    →  frame
  m.room.message                →  MESSAGE
  m.room.member                 →  MEMBER (state transitions: join/leave)
  m.room.create                 →  the item's inception
  m.room.power_levels           →  CONFIG:[POWER_LEVELS]
Matrix user                     →  user item with Matrix ID frame
```

The Megolm session keys for E2E encryption can be CONFIG bindings on the room.  Federation between Matrix homeservers and CG Librarians becomes another routing path.

#### AT Protocol

Bluesky's stack.  Posts, follows, likes — similar to ActivityPub but with a different data model and stronger account portability.  AT Protocol's repositories are Merkle trees of signed records; CG's items are signed frame collections.  The structures map.

```
AT Protocol record              →  frame
AT Protocol commit              →  manifest version
AT Protocol DID                 →  user item IID (with did:plc or did:web SOURCE)
```

#### XMPP

Older but still deployed.  An XMPP bridge translates between XMPP stanzas (presence, message, IQ) and frame events.  Less leverage than ActivityPub but useful for specific communities.

### Identity standards

#### W3C DIDs and Verifiable Credentials

Already covered in `iiw-presentation.md`.  A CG item carries DID frames for any number of methods (`did:web`, `did:key`, `did:plc`).  A `did:cg` method can be defined where the IID is the method-specific identifier and the peer network is the resolver.  W3C VCs map to signed CERTIFIED frames with selective disclosure achieved through binding-level elision.

#### KERI / ACDC / vLEI

Sam Smith's stack.  AIDs map to CG IIDs (with the KERI AID as a SOURCE binding).  KELs become FOLLOWS chains of key event frames.  The pre-rotation scheme (commit to next-key digest, reveal on rotation) is worth implementing natively.  ACDCs map to signed frames with FOLLOWS edges.  IPEX grant/admit messages become OFFER/ENDORSED frames.  vLEI provides regulatory-grade legal entity identity that can be cross-attested in CG via VERIFIED frames.

A bridge Librarian can serve KERI clients via OOBI URLs while storing everything internally as frames.  CESR encoding is used at the wire boundary; CBOR + short codes is used internally.

#### OpenID Connect

Federated web authentication.  Bridge enables SSO into existing services using a CG-issued authentication frame.  An OIDC bridge accepts authorization requests, redirects users for consent (which becomes a CONSENT frame), and issues ID tokens derived from user item frames.

#### SAML

Enterprise SSO.  Less elegant than OIDC, more widely deployed in enterprise.  Bridge translates SAML assertions to CERTIFIED frames and back.

#### OAuth 2.0

Delegated access.  An OAuth bridge lets external services request scoped access to a user's items.  The authorization grant is a DELEGATED frame with scope and expiry bindings.

#### WebAuthn / Passkeys

Modern device-based authentication.  A WebAuthn bridge maps platform passkeys to CG signing keys, allowing CG users to log into web services using their device's secure enclave.

### Content and storage

#### IPFS / IPLD

Content addressing.  A CG body hash and an IPFS CID are both content addresses; with a multihash translation layer, they are interoperable.  Items can be replicated through IPFS as a transport layer; IPFS content can be addressed in CG VALUE bindings.

```
CG ContentID (multihash)        ↔  IPFS CID (also multihash, different default codec)
CG Manifest (signed list)       ↔  IPFS DAG-PB or DAG-CBOR object
CG peer replication             ↔  IPFS bitswap / graphsync
```

The bridge is mostly a CID translation layer.

#### WebDAV / CalDAV / CardDAV

File sync, calendars, contacts.  WebDAV exposes items as a virtual filesystem.  CalDAV exposes calendar event frames as iCalendar.  CardDAV exposes contact items as vCard.  Useful for legacy clients that expect these protocols.

#### RSS / Atom

Syndication.  An RSS bridge consumes external feeds, translating each entry to an ARTICLE frame on a feed item.  Outbound: any item with a sequence of FOLLOWS-chained content frames can be served as an RSS feed.

#### Solid

Berners-Lee's personal data project.  Conceptually adjacent to CG.  A Solid pod stores data the user controls; CG items live on the user's Librarian.  Bridge translates Linked Data Platform requests to frame queries.

### Real-time communication

#### WebRTC

Browser-based real-time audio, video, and data.  A WebRTC bridge handles signaling (SDP offer/answer exchange) through CG frames, with the actual media streams flowing peer-to-peer over WebRTC's transport.  A CALL frame carries SDP bindings; the bridge negotiates ICE candidates and establishes the connection.

```
WebRTC offer/answer             →  SDP bindings on CALL frame
ICE candidates                  →  CONFIG:[ICE] bindings
DataChannel messages            →  MESSAGE frames over the data channel
Media stream (audio/video)      →  content stream referenced from CALL frame
```

#### SIP

Telephony.  Bridges to legacy VoIP and PSTN.  An incoming SIP INVITE becomes a CALL frame on the recipient's item.  Spam-call economics shift the same way email spam does — every call is signed and attributable.

#### Signal Protocol

Gold standard for end-to-end encrypted messaging.  Useful as inspiration even if not directly bridged.  The Signal Protocol's session key management could be ported to CG for E2E-encrypted frames.

### Anonymity and privacy

#### Tor

Onion routing.  CG can use Tor as one of its transport options for high-privacy frame routing.  A `CONFIG:[ROUTING]` policy of `ONION` causes the Librarian to send frames through Tor circuits.  A Tor bridge item declares the available circuits and the routing policy.

#### I2P

Similar to Tor, less mainstream.  Same bridging approach.

### Crypto / Web3

These are useful for specific applications even if you don't share the broader Web3 worldview.

#### Ethereum / EVM chains

For items that need on-chain anchoring (timestamping a manifest hash on a public blockchain) or smart-contract interaction.  An Ethereum bridge can submit transactions and observe events, translating them to frames.  See `blockchains.md` for the broader story on blockchain interop.

#### IPNS

IPFS's mutable naming layer.  Conceptually parallel to CG's manifest VID system.  An IPNS bridge maps IPNS names to CG items.

## A bridge implementation framework

Most bridges share structure:

```
1. Listen on protocol-specific endpoints
2. Authenticate inbound requests (per-protocol mechanism)
3. Parse incoming protocol messages
4. Translate to frames (apply mapping rules)
5. Sign and commit frames to the user's items
6. For outbound: query frames, translate to protocol format, send

Plus:
7. Maintain protocol-specific state (sessions, sequence numbers, etc.)
8. Handle errors and retries per the protocol's expectations
9. Generate audit frames recording every translation
```

A reusable bridge framework provides steps 1, 2, 5, 6, 8, 9.  Bridge authors implement the protocol-specific bits (3, 4, 7).  This framework can itself be an item — a BRIDGE_FRAMEWORK implementation that other bridges depend on.

## Trust at the boundary

Bridges are the most security-sensitive items in the system.  They process untrusted input and produce frames on users' behalf.  Three trust signals govern bridge use:

1. **Trust in the bridge operator.**  Who runs this bridge?  The trust matrix evaluates them in the BRIDGING domain.
2. **Trust in the bridge implementation.**  Has the code been audited?  Who signed the implementation item?  Code-signing in the same trust matrix that drives content trust.
3. **Trust in the bridged-from source.**  An incoming email's sender is in the trust graph (or not).  An ActivityPub post's actor has a reputation.  The bridge translates the source's identity into the trust matrix.

The user's Librarian decides which bridges to use, which to trust, and how to weight frames they produce.  A bridge that produces low-quality frames degrades in trust automatically — the same way a person who posts spam degrades in trust.

## The audacity, named

The full vision: any data in any existing system can be expressed as frames; any frames can be served via existing protocols.  A user can read CG content in Thunderbird, post to it from Mastodon, fetch it via Git, sign it with a passkey, find it via WebFinger, route it through Tor, anchor it on Ethereum, and verify it via vLEI.

None of this requires changing the existing systems.  It requires writing the bridges and signing them.  Each bridge is one item.  The audacity is in the cumulative effect — when enough bridges exist, CG becomes the substrate that connects them all, and the cost of leaving any single platform approaches zero.

The bridges don't have to be built by one person, one team, or one organization.  Each bridge is an item.  Anyone can write one, sign it, publish it, and let the trust matrix decide whether to use it.  The community builds the bridges; the substrate carries them.
