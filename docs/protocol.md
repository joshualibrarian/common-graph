# Parley

**Parley** is Common Graph's wire protocol. It runs over any byte-stream transport (Unix sockets, TCP, TLS, Bluetooth, anything that delivers ordered bytes), establishes which encoding the two parties will speak, and then carries an open-ended stream of datums in that encoding. That's the whole protocol.

The radical move Parley makes is to collapse the control plane into the data plane. After the codec handshake, every message — auth attempts, subscriptions, sync requests, capability negotiations, application traffic — is just another frame. There's no separate "control message" type, no request/response framework, no protocol state machine. Auth happens because someone sends an `@AUTHENTICATE` frame that the counterparty handles, the same way the counterparty handles any other frame. Sync happens because someone sends a query and the counterparty answers with results. Everything is data; the protocol gets out of the way.

This document defines the two phases of Parley, the codec handshake, the HELLO frame each side sends, and what travels over an open connection.

This document assumes familiarity with [the datum primitive](datum.md), [the reference scheme](ref-scheme.md), [the network architecture](network.md), and [encoding](cg-cbor.md).

## Two phases

```
[1] codec point-and-grunt    — negotiate which encoding both sides speak
[2] stream of anything       — exchange datums in that encoding
```

That's the entire protocol structure. Phase 1 is a brief, terse handshake using *zero presumed shared vocabulary beyond the reference-prefix primitives*. Phase 2 is an open-ended exchange of self-describing bytes.

There's no phase 3. Connections stay in phase 2 until one side disconnects.

## Phase 1: point-and-grunt

When two parties open a Parley connection, neither side yet knows what encoding the other speaks. CG-CBOR is the most likely; CG-JSON, a future flat-binary format, a domain-specific codec — any might be present on either side, possibly different sets on each. The handshake establishes which they'll use.

The handshake protocol is built on the only thing both sides are guaranteed to understand: the **reference byte layout** (see [`ref-scheme.md`](ref-scheme.md)). A reference's bytes — prefix character + multihash — are protocol-defined and identical across every CG implementation. Codecs identify themselves by IID; an IID is a reference; references are bytes both sides can parse without needing a codec.

The handshake:

1. **Initiator sends raw bytes:** `@<codec-iid>` — the bytes of an `@`-prefixed reference to the codec the initiator wants to use. No envelope, no tag, no length prefix. Just the raw 33-byte reference. The receiver knows how to read this because the reference-byte layout is part of the protocol's foundation.

2. **Receiver responds in that codec:** if the receiver supports the proposed codec, it responds with a HELLO datum *encoded in that codec*. The act of speaking the codec is the confirmation — no separate "accepted" message. The bytes of the HELLO confirm both *that the codec was understood* and *what the receiver wants to say first*.

3. **Mismatch path:** if the receiver doesn't support the proposed codec, it responds with another raw `@<codec-iid>` — a counter-grunt — proposing a codec it does support. The initiator can accept (sending a HELLO in that codec) or counter-propose again, and so on.

4. **Failure path:** if no overlap exists, the connection closes. No party is wrong; they just don't share a wire format. (In practice every librarian supports CG-CBOR as the canonical default, so this is rare.)

The handshake's elegance comes from using `@`-references as the bootstrap vocabulary. The system already has a one-byte-prefix typed-reference primitive; the handshake exploits it before anything higher-level exists. No second layer is needed to bootstrap a codec; the codec's identity is itself a reference, and references are universal.

The handshake's robustness comes from "speaking a codec to confirm it." A peer that claims support for a codec but can't actually emit it fails at the HELLO step; the initiator sees garbage and the connection fails. No version-mismatch surprises lurking in the data plane.

## Phase 2: stream of anything

After the codec handshake, the connection carries an open-ended sequence of **self-describing datums** in the agreed codec. Each datum is framed by the codec's own framing rules (in CG-CBOR, that's CBOR's natural framing — each tagged value is self-delimiting). The receiver decodes one datum at a time, dispatches it, decodes the next.

What travels over Phase 2:

- **Frame bodies** — propositional assertions, queries, commands.
- **Records** — signed attestations.
- **Content blobs** — bytes addressed by ContentID, requested by reference.
- **Encrypted envelopes** — opaque payloads with metadata for recipient resolution.
- **Raw values** — literals, numbers, strings, anything the codec can carry standalone.
- **HELLO**, **AUTHENTICATE**, **SUBSCRIBE**, **REQUEST**, anything else — all frames whose head is some predicate. No special-cased.

Parley itself doesn't distinguish among these. To the protocol, the connection is just a sequence of decoded datums; what each datum *means* is the receiver's problem, handled by the receiver's normal dispatch machinery (HANDLES bindings, the trust matrix, the Stage).

This is what "the only protocols are social" means. After phase 1, the wire is just a vehicle for frames; the rules for what to do with each frame are the rules the graph itself defines.

## The HELLO frame

The first frame each side sends after the codec handshake is a HELLO. The HELLO introduces the sender — who they are, what keys they sign with, possibly some gossip about other peers worth knowing about.

```
{@hello, [
  @AGENT → @<my-iid>,
  @SIGNING_PUBLIC_KEY → <multikey bytes>,
  @KEY_UPDATES → #<recent-rotation-frame-cid>,
  @PEER_GOSSIP → ... 
]}
```

The HELLO is a normal frame headed by the HELLO predicate. The receiver's HELLO handler (declared by the librarian's archetype's HANDLES) processes it: verifies the signature, records the connection as belonging to the named identity, updates its knowledge of the peer's key history, possibly notes any peer-gossip references for future routing decisions.

A connection without a HELLO is anonymous — the receiver doesn't know who's on the other end. Anonymous connections are permitted; some interactions need no identity (a pure-content-fetch probe, for example). The trust matrix decides what anonymous peers may do.

A session connection (a user's UI talking to a librarian) sends a HELLO that includes the session's identity and the user it represents. A peer-librarian connection sends a HELLO identifying the librarian. The receiver distinguishes the two by what the HELLO says (see [Sessions vs. peer librarians](#sessions-vs-peer-librarians) below).

After HELLO is exchanged, both sides know who they're talking to. Further frames flow against that identity context.

## Frames are messages

Once Phase 2 is active, every datum exchanged is a frame (or a related body type) the receiver dispatches. The dispatch follows the normal item-as-actor flow:

1. Decode the frame body.
2. Verify any records (signatures, signer authority, freshness).
3. Look up which local items the frame concerns (via reference targets) or which items handle frames headed by this predicate (via HANDLES).
4. Route to the appropriate items.
5. Items react; possibly produce reply frames.
6. Reply frames flow back through the same connection (or a different one, if the route is asymmetric).

No request/response coupling at the protocol layer. A "request" frame doesn't carry a correlation ID; it doesn't expect a response on the same connection at any particular time. The receiver may respond immediately, may respond later, may not respond at all (legitimate if the predicate doesn't require a reply). Replies that need to be matched to specific requests carry a reference to the original frame's DatumID in their bindings.

This is unusual relative to most wire protocols, which have explicit request/response framing. Parley dispenses with it because the *graph* models response relationships explicitly: a reply frame references the request frame by DatumID; that reference is observable; the relationship is data, not protocol.

## Sessions vs. peer librarians

A Parley connection is either a **session** (a client UI talking to a librarian) or a **peer librarian** (one node talking to another). The discriminator is what's in the HELLO frame:

- **Session HELLO** — identifies a Session item with a particular user identity attached. Triggers the librarian to allocate session-specific state (view state, focused item, presence, prompt state). The librarian treats the connection as serving a user.
- **Peer-librarian HELLO** — identifies a librarian item. No user-session state allocated. The librarian treats the connection as inter-librarian sync traffic.

Both kinds of connection run the same Parley protocol underneath. The HELLO content tells the receiver how to interpret what comes next. A peer-librarian connection might evolve into a session connection later (an additional HELLO frame attaching a session); a session connection cannot generally become peer-librarian (sessions don't have librarian identities).

This "peer-as-spectrum" model is what unifies the old peer/session split. Architecturally, the system has one kind of network connection; the connection's role emerges from what each side declares about itself.

## Transports

Parley runs over any byte-stream transport. The transport's job is delivering bytes in order; Parley's job is everything above that. Specific transports:

- **Unix sockets** — same-host, separate-process. Filesystem permission gating. Fast.
- **TCP** — across-host. Most common remote case.
- **TLS over TCP** — TCP with transport-layer encryption. The receiver's certificate is verified before bytes flow.
- **WebSocket** — for browser-attached sessions or environments where firewalls block raw TCP.
- **Tor / I2P / Yggdrasil** — overlay transports for privacy.
- **Bluetooth, LoRa, custom** — short-range or low-bandwidth.

The transport is established before Parley runs. Whatever produces a bidirectional byte stream (with any transport-layer encryption it needs) is the transport; Parley starts at the codec handshake.

Some transports already provide encryption (TLS, Tor, encrypted Bluetooth). Others don't. A bare TCP connection might layer a **Noise tunnel** below Parley — a Noise-protocol handshake establishes mutual authentication and encryption before Parley starts. The tunnel is transport-flavor, invisible to Parley itself; from Parley's perspective there's just a byte stream.

See [`encryption.md`](encryption.md) for the encryption layer; [`network.md`](network.md) for the transport landscape.

## Open-ended ordering

Datums on a Parley connection are *ordered* in delivery (the transport guarantees this) but their semantic ordering — which frame "happened first" in a global sense — is the application's concern. Two connections to the same librarian can interleave their frames arbitrarily; the librarian sees them in whatever order the transports deliver.

Frames that need explicit ordering (a chat conversation's message sequence, a stream of edits to a document, a sequence of moves in a game) carry their ordering in their *content* — via `@FOLLOWS` bindings naming predecessor DatumIDs. The chain of FOLLOWS bindings is the order; the network's delivery order doesn't have to match.

This means concurrent updates work naturally: two peers each commit a new manifest to the same item; their commits arrive at a third librarian in whatever order; the librarian sees both, observes that both follow the same predecessor, and presents the fork. Resolution (merge, fork, accept one) is policy, applied above Parley.

## What Parley doesn't do

- **No global ordering.** Connection-level ordering only.
- **No QoS, no priority.** All bytes are equal; flow control is the transport's job.
- **No retry semantics.** A datum that fails to send (because the connection died, the receiver rejected it, the codec mismatch surfaced too late) is the sender's problem. The application can re-send via the same or a different connection.
- **No connection-level encryption.** Encryption is a transport-layer concern (TLS, Tor) or a content-layer concern (encrypted envelopes carried as frames). Parley between Phase 1 and Phase 2 is plaintext relative to itself.
- **No version negotiation.** The codec IDs are versioned implicitly (a v2 codec has a different IID); there's no separate "protocol version" field. New protocol behaviors come through new codec IDs or through new predicates the codec carries.
- **No service discovery.** Parley doesn't enumerate what services the peer offers; that's the receiver's manifest's HANDLES set, queryable through Parley once a connection is up but not announced separately.

The protocol is small. Everything that *could* be in the protocol but isn't, is somewhere in the graph instead — discoverable through normal frame dispatch.

## Worked examples

**A session opening to a local librarian.**

```
Session opens Unix-socket connection to librarian's socket.

1. Session sends raw bytes: @<cg-cbor-iid>          (33 bytes, no envelope)
2. Librarian responds in CG-CBOR:
     {@hello, [
       @AGENT → @<session-iid>,
       @THEME → @<user-iid>,           ; the user this session represents
       @SIGNING_PUBLIC_KEY → <user's multikey>
     ]}
3. Both sides know each other; Phase 2 is live.

User types "create document"; session assembles a CREATE frame; sends it.

   {@create, [
     @AGENT → @<user-iid>,
     @THEME → @document-archetype
   ]}

Librarian receives, dispatches, mints a new document item, sends back
a reply frame referencing the new item's IID. User's UI shows it.
```

**Two librarians peering, codec mismatch resolved.**

```
1. Librarian A sends @<cg-json-iid>     ; A speaks CG-JSON
2. Librarian B doesn't know CG-JSON; sends @<cg-cbor-iid> ; B counter-proposes
3. A speaks CG-CBOR too; sends HELLO in CG-CBOR.
4. B responds with its own HELLO in CG-CBOR.
5. Phase 2 active; both encode in CG-CBOR.

Later: A fetches a frame B has. A sends:
   {@request, [@THEME → #<frame-cid>]}
B receives, looks up the frame, responds with the frame body and records.
```

**An anonymous probe.**

```
1. Probe connects; sends @<cg-cbor-iid>.
2. Receiver responds with HELLO identifying itself.
3. Probe doesn't send a HELLO of its own; it just sends a content-fetch
   request:
     {@request, [@THEME → #<some-content-cid>]}
4. Receiver checks trust policy: do anonymous peers get to fetch this
   content? If yes, responds with the content. If no, sends a refusal frame.
5. Probe disconnects.

No identity was exchanged. The receiver did some work for the anonymous
caller, gated by its trust policy. Many networks allow some anonymous
operations (read-only fetches of public content); most allow few.
```

**A bridge translating an email.**

```
The bridge is a code item implementing the SMTP-bridge archetype. It opens
a Parley connection to its host librarian (typically Unix socket).

1. HELLO exchanged; bridge identifies itself.
2. Email arrives at the bridge's SMTP listener. Bridge translates to a frame:
     {@message, [
       @AGENT → @<sender>,
       @LOCATION → @<recipient's-room>,
       @CONTENT → "...",
       @RECEIVED_VIA → @<smtp-bridge>
     ]}
3. Bridge sends the frame to the librarian via Parley.
4. Librarian dispatches; the recipient's room receives the message.

For outbound traffic, the room emits a frame; the bridge's handler picks
it up; bridge translates back to SMTP; email goes out.
```

## Why this shape

Most wire protocols have many concerns: framing, ordering, retries, multiplexing, flow control, versioning, capability negotiation, auth, encryption. Parley pushes all of these elsewhere:

- **Framing:** the codec handles it. Self-describing datums are self-delimiting.
- **Ordering:** transport-level delivery order; semantic order is in the data via FOLLOWS bindings.
- **Retries:** the application's concern. Parley doesn't pretend to be reliable beyond what the transport provides.
- **Multiplexing:** one connection carries one stream; multiple connections can run between the same pair of peers if multiplexing is needed.
- **Flow control:** the transport's concern.
- **Versioning:** the codec carries an IID; new versions are new IIDs; old ones still work for old peers.
- **Capability negotiation:** the graph's HANDLES bindings are observable through normal frame queries; no separate enumerate step.
- **Auth:** AUTHENTICATE is just a predicate; the AUTHENTICATE frame is just a frame.
- **Encryption:** transport-layer (TLS, Tor) or content-layer (encrypted envelopes). Parley itself stays simple.

The protocol's job is to **establish a codec and carry frames**. Every other concern lives in a layer that already exists — the transport below it, the graph above it.

This is what makes Parley both small to specify and capable of doing whatever the graph needs. New use cases don't need new protocol features; they need new predicates the graph already supports.

## Relations

- [`network.md`](network.md) — the network architecture; transports, peers, discovery.
- [`ref-scheme.md`](ref-scheme.md) — the reference byte layout the codec handshake leans on.
- [`canonical.md`](canonical.md) — DatumIDs as the encoding-agnostic identity that survives codec choice.
- [`cg-cbor.md`](cg-cbor.md) — the dominant codec and its tag assignments.
- [`item.md`](item.md) — items as the dispatch targets for incoming frames.
- [`api.md`](api.md) — HANDLES and the dispatch flow.
- [`frames.md`](frames.md) — frames as the message primitive Parley carries.
- [`encryption.md`](encryption.md) — encryption layered below or above Parley.
- [`authentication.md`](authentication.md) — identity, keys, and signatures.
- [`trust.md`](trust.md) — the trust matrix that gates connection acceptance and content propagation.
