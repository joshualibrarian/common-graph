# Network

Common Graph is a peer-to-peer network where every librarian is a sovereign node. There are no dedicated servers, no central authorities, no global indexes. Content lives where users put it. Discovery happens through trust relationships. The network scales because most interactions are local, and the ones that aren't propagate through paths that converge fast.

The architecture treats networking as a transport detail above an otherwise self-contained data model. Two librarians don't need a shared encoding format, a shared schema registry, or a shared coordinator — they just need to exchange datums by structural hash, verify each others' signatures, and route messages through their respective trust matrices. Everything else falls out.

This document defines the network topology, how librarians discover and connect, how trust drives routing and replication, and how transports underneath the protocol stay swappable.

This document assumes familiarity with [items](item.md), [the canonical walker](canonical.md), [content addressing](content.md), [trust](trust.md), and [the Parley protocol](protocol.md).

## Librarians as nodes

Each librarian is a node — a sovereign process holding storage, an identity, a vocabulary, and a set of items. Nodes communicate as peers; there's no client/server distinction at the architectural level (though specific transports may have asymmetric handshake roles).

A node carries:

- **Its own identity** — an Ed25519 (or compatible) signing key, established at first launch, used to sign everything the librarian itself attests.
- **Its local storage** — the object store, indexes, item directory, token dictionary. Everything the librarian knows.
- **Its trust matrix** — the policy that decides whose assertions count, whose code can run, what propagates where.
- **Its connections** — open Parley channels to peer librarians and to client sessions.

A user might run one librarian (a single device) or several (one per device, all linked to the user as a higher-level identity). Each librarian operates independently; cross-device coordination happens through ordinary network exchange of signed frames.

There's no logical limit on the number of librarians in the network. They federate by speaking the same protocol, recognizing the same reference scheme, and trusting (or not) each others' signatures.

## The social graph IS the routing layer

The network's topology emerges from trust. A librarian's peers are the librarians it has explicit relationships with — established through signed introduction frames, recorded in the graph itself. The structure is honest about what it represents: nodes connected to nodes they trust.

A librarian fetching a datum it doesn't have locally asks its peers. The peers ask their peers. Trust attenuates with distance: the librarian's direct peers have full weight; their peers have less; the third hop less still. Queries with low confidence at distant hops stop propagating.

This isn't routing in the IP sense — there's no global topology, no routing tables, no DHT. The librarian's *direct neighborhood* is its routing surface; the rest of the network is whatever's reachable through asking, and the answer to "is this thing reachable?" is "ask and find out."

The properties this gives the system:

- **No central index.** No coordinator decides what exists.
- **Trust-bounded discovery.** Information propagates along trust paths; spam and untrusted assertions don't broadcast.
- **Locality wins.** Most things are close in trust-graph distance; queries usually resolve in a few hops.
- **Resilience to censorship.** No single node's absence disconnects the network from itself.
- **Different views, same data.** Each librarian's neighborhood is its own; views of the graph differ by trust topology, not by the data itself.

## Discovery

Discovery is how a librarian comes to know other librarians exist. Several mechanisms compose:

**Explicit introduction.** A user shares a peer's identity (an IID, a public key, a transport address) with their librarian. The librarian opens a connection; the two exchange `@HELLO` frames; trust relationships start accumulating from there.

**Social reference.** A frame fetched from a peer mentions other peers' IIDs. The receiving librarian, if it cares, can ask its current peers about those mentioned identities and possibly establish connections to them.

**Same-host detection.** Multiple librarians on the same host can discover each other through local mechanisms (Unix sockets, mDNS, well-known socket paths). Useful for development and for multi-process setups on a single machine.

**Out-of-band exchange.** QR codes, NFC, business cards, social-media handles — anything that conveys an identity and a way to reach it. Discovery doesn't have to happen over the network.

There's no global lookup. There's no "search for users named Alice." Discovery is intentional, based on trust relationships the user is already building outside the network.

## Trust drives every routing decision

Once peers are known, *which* peers a librarian talks to, *which* peers it forwards queries to, *which* peers' content it accepts — all are policy decisions the trust matrix makes. The same matrix that decides "should I run this code?" decides "should I propagate this query to that peer?"

A few specific decisions trust drives:

- **Connection acceptance.** Should this incoming connection be honored? Whose connections do I accept by default?
- **Query forwarding.** When my peers ask me for content, do I forward unfound queries to my own peers? To whom, with what attribution, at what depth?
- **Content acceptance.** When I fetch content from a peer, do I accept it into my storage? Verify its signature, but also trust its provenance?
- **Reaction propagation.** A "like" or "spam" frame from a peer — does my view reflect it?

These aren't blanket allow/deny lists. Trust scores fall on a continuum; policies combine multiple dimensions (the signer's reputation, the content's provenance, the user's preferences, the application context). Two librarians with different trust policies see the same network differently. That's a feature.

See [`trust.md`](trust.md) for the trust matrix in detail.

## Content replicates organically

Content addressing makes replication a natural side effect of usage, not a deliberate strategy. Any datum a librarian fetches by ContentID can be re-served to other librarians asking for the same ContentID; verification is by re-hashing.

A popular item — a widely-read document, a chat-room with active members, a video stream with many subscribers — replicates along interest paths through the social graph. Each subscriber's librarian fetches what its user wants; once fetched, the librarian can be a source for others; the content spreads to wherever it's wanted.

A rare item — a personal document of mine that only my close friends care about — stays close. My friends' librarians fetch it; they don't propagate it further unless their users care; the content stays local to the social neighborhood that wants it.

The user's retention policy decides what their librarian keeps. Some librarians keep everything they've ever fetched (storage-rich, replication-friendly); others retain only their own content and recent fetches (privacy-leaning, less network-citizen). Both are valid; the protocol works either way.

## Replication is per-datum

The unit of replication is the datum, not the item or the stream or the application. A frame requested by ContentID is fetched, verified, stored; nothing larger or smaller travels.

This means:

- **Partial replication is the norm.** A chat-room's older messages might not be on a new subscriber's librarian. They're fetched on demand. The chat room doesn't have to be "fully synced" before it's usable.
- **Sparse traversal is cheap.** A query that walks a chain of references fetches each link as it's encountered. No bulk-sync step.
- **Garbage collection is local.** A librarian can prune datums its user no longer cares about, without affecting others who still have them.

The architecture treats *data* as the unit of consistency, not collections of data. Items, streams, channels — these are organizing concepts that emerge from collections of datums; their replication is whatever replication the constituent datums achieve.

## Encoding-agnostic across the network

Two librarians using different encoding formats — CG-CBOR, a hypothetical CG-JSON, anything — can still exchange datums. The structural hash (DatumID) is encoding-independent; both librarians compute the same DatumIDs for the same data by walking the same structure.

The Parley codec handshake (see [`protocol.md`](protocol.md)) lets two peers negotiate which encoding they'll use for *this connection*. The choice is per-connection; a librarian can speak CG-CBOR to one peer and CG-JSON to another simultaneously. Each peer encodes its outgoing datums in the chosen codec; each peer decodes incoming bytes with the matching codec.

The DatumIDs match across encodings. Two librarians using different codecs can deduplicate by DatumID, can verify each others' content by structural hash, can route by reference. The encoding is purely a transport detail; it doesn't affect the *meaning* of what crosses the wire.

This is what protects the network from being locked to one wire format. New codecs can be added without breaking the protocol; older librarians can still talk to newer ones if both support a common codec; experiments in encoding (smaller, faster, domain-specific) can run in parallel with the canonical CG-CBOR without fragmenting the data model.

## Transports

The protocol is transport-agnostic. Anything that delivers bytes in order, with framing, will do. Common transports:

- **Unix sockets** — same-host, inter-process. Fast, secure (filesystem-permission-gated), the default for local-bridge sessions.
- **TCP** — across-host, IPv4/IPv6. The dominant remote transport.
- **TLS over TCP** — TCP with transport-layer encryption.
- **Bluetooth, LoRa, custom radios** — short-range or low-bandwidth use cases.
- **Tor / I2P** — privacy-preserving overlays.
- **HTTP/2 streams** — for environments where firewalls only allow HTTP.

Each transport is wrapped in a `Transport` adapter exposing the byte-stream interface Parley expects. Parley itself doesn't know which transport is underneath; it sees a bidirectional bytestream and runs its codec handshake on it.

A librarian typically supports multiple transports. Inbound connections come in on whichever transport's listener accepted them; outbound connections pick the transport based on the peer's address. The transport detector resolves a peer's address (a URL, a Unix socket path, a Bluetooth identifier) to the right transport adapter.

See [`protocol.md`](protocol.md) for Parley itself; transports are below it in the stack.

## Local-first

Most operations are local. A query against the librarian's own storage doesn't touch the network; a frame submitted locally is dispatched without network round-trips; an item's manifest version advances entirely on the local device. Network is the fallback path, not the default.

This is what makes the system usable offline. A laptop on an airplane keeps editing documents, sending messages to other items, reacting to frames — all locally. When the network is available again, the local changes flow out and remote changes flow in. The system doesn't degrade gracefully when offline; it just *works* offline, because nothing was happening online to begin with.

The same architectural property holds at small scale. A friend group of five users on five devices doesn't need a server; their librarians peer with each other directly. A team running its own infrastructure doesn't need a cloud; their librarians coordinate over the LAN. Scaling up to a global network doesn't change the model — it just adds more peers.

See [`item.md`](item.md) for items' role as the persistent unit, and [`storage.md`](storage.md) for the local persistence layer.

## Sessions: peer-or-client

A session is, in network terms, a connection to a librarian. The session may be in-process (no network), on the same host (Unix socket), or remote (TCP). What makes it a *session* rather than a peer is one detail: the connection has a Session item attached on the client side, with a user's identity, presence state, and view state.

A peer-to-peer connection between two librarians is identical at the protocol layer — same Parley handshake, same frame stream, same encoding. The distinguishing factor is whether the connecting side identifies itself as a session (running on behalf of a specific user, holding view state) or as a librarian (representing a node's whole presence).

The session-or-librarian distinction emerges from the HELLO frame each side sends after the codec handshake. A connection identifying as a session triggers the librarian to allocate session-specific state (focused item, prompt state, presence); a connection identifying as a peer librarian doesn't.

See [`runtime.md`](runtime.md) for the runtime's perspective on sessions, and [`protocol.md`](protocol.md) for the HELLO frame's contents.

## Bridges

Some "peers" aren't other Common Graph librarians — they're external systems Common Graph wants to interoperate with. Email servers, ActivityPub instances, IPFS gateways, WebDAV stores, traditional REST APIs, Matrix homeservers. A **bridge** is a code item that translates between an external protocol and the CG frame stream.

A bridge appears as a normal Common Graph item; its code item implements an archetype representing the external system. When the librarian needs to send something through the bridge, it dispatches the appropriate frame; the bridge's handler translates to the external protocol and forwards. Incoming external messages flow back through the bridge as frames.

Bridges are how Common Graph integrates with existing systems. Adoption is not zero-sum: a user can be part of the Common Graph network while still emailing colleagues who aren't, posting to social media that isn't, accessing services that aren't. The bridge does the translation; the user works in their native interface.

See [`bridges.md`](bridges.md) for the bridge model in detail.

## What this isn't

Common Graph's network is **not**:

- **A DHT.** No global routing table is maintained; there's no Kademlia-style overlay. Discovery is trust-driven, not structured-overlay-driven.
- **A blockchain.** No consensus on a shared global state; no canonical ordering of events; no proof-of-work or proof-of-stake. Different librarians can hold conflicting beliefs about the same item simultaneously; trust resolves them per-viewer.
- **A federation.** No "homeservers" with privileged routing roles. Every librarian is structurally equivalent; a librarian running on a phone is equal in protocol terms to one running on a datacenter cluster.
- **A CDN.** Content replicates by interest, not by deliberate caching. Popular content travels; rare content stays local.
- **A directory service.** No "find people named Alice" API. Discovery is intentional; reputations and relationships are observable from the data, but the network doesn't enumerate them.

What it *is*: a substrate for sovereign nodes to exchange signed datums, verify each others' work, propagate interest along trust paths, and let each user see the network through their own subjective view.

## Worked example: two librarians, one frame

A and B are two librarians belonging to two users who trust each other. A's user composes a chat message in a room they both belong to.

1. **A's librarian** assembles the frame body, signs it, stores it locally, indexes it.
2. **Routing.** The frame's binding `@LOCATION → @<room>` mentions the chat room. A's librarian finds B in its peer list and knows B is also subscribed to the room.
3. **A opens a connection to B** (or uses an existing one) over whichever transport is configured between them.
4. **The Parley handshake** runs: A sends `@<codec-iid>` raw bytes; B responds with a HELLO frame in that codec.
5. **A sends the chat-message frame.** B receives it, decodes it (codec is now established), verifies the signature.
6. **B's librarian** stores the body and the record, updates its indexes, dispatches the frame to local items (the chat room subscribed to that LOCATION receives the message).
7. **B's user** sees the message appear in their view of the room.

No central server, no broker, no relay. Just A and B exchanging bytes representing a signed frame, both verifying its integrity, both updating their local views. The same exchange scales — to a room with 1000 subscribers, A's librarian opens connections to whichever peers are subscribers and reachable; the message propagates along trust paths.

## Relations

- [`protocol.md`](protocol.md) — Parley, the protocol that runs over any transport.
- [`item.md`](item.md) — items as the unit of identity and storage.
- [`trust.md`](trust.md) — the trust matrix that drives routing and replication decisions.
- [`storage.md`](storage.md) — local storage; what gets replicated.
- [`content.md`](content.md) — ContentID and the addressing scheme.
- [`canonical.md`](canonical.md) — DatumID as the encoding-independent identity.
- [`runtime.md`](runtime.md) — sessions vs. peer librarians.
- [`bridges.md`](bridges.md) — interop with external systems.
- [`encryption.md`](encryption.md) — encryption at-rest and in-transit.
- [`privacy.md`](privacy.md) — privacy properties of the network.
