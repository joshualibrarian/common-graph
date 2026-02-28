# Protocol

Common Graph defines two protocols: the **CG Protocol** for peer-to-peer communication between Librarians, and the **Session Protocol** for client-to-Librarian interaction. Both use CG-CBOR over length-prefixed frames.

This document covers wire-level protocol details — message types, framing, connection lifecycle. For the high-level network architecture (discovery, routing, replication, scaling), see [Network Architecture](network.md).

## Design Philosophy

The CG Protocol is intentionally minimal. There are exactly two message types — **Request** and **Delivery** — plus a keep-alive heartbeat. Everything else is convention built on top.

This simplicity is deliberate. Traditional protocols tend to accumulate message types as features are added. Common Graph avoids this by keeping the protocol as a thin transport for graph operations. If you can request things and deliver things, you can build any interaction pattern — including query propagation across the social graph, subscription-driven replication, and relay forwarding through trusted intermediaries.

Related work: the CG Protocol shares philosophical DNA with systems like [Freenet](https://freenetproject.org/) (content-addressed P2P storage), [IPFS](https://ipfs.tech/) (content-addressed block exchange), and [Secure Scuttlebutt](https://scuttlebutt.nz/) (append-only log replication). Unlike those systems, Common Graph unifies content addressing with semantic relations and cryptographic identity in a single protocol. The protocol is local-first by design (see [references/Kleppmann 2019](references/Kleppmann%202019%20-%20Local-First%20Software.pdf)) — all data lives locally, networking is explicit, and sync is merge-based. Fielding's REST dissertation (see [references/Fielding 2000](references/Fielding%202000%20-%20Architectural%20Styles%20and%20Network-Based%20Software%20REST.pdf)) defines the dominant network architecture of the web; the CG Protocol departs from REST's stateless client-server model toward signed, content-addressed peer-to-peer exchange.

---

## CG Protocol (Peer-to-Peer)

The CG Protocol connects Librarians — the runtime nodes of the Common Graph network. Each Librarian is itself an Item (a Signer with its own Ed25519 key), so protocol participation is identity-native.

### Transport

- **Framing**: Length-prefixed CBOR messages over TCP or Unix domain sockets
- **TLS**: Optional; when enabled, both sides present certificates
- **Keep-alive**: Heartbeat messages on idle connections

### Message Types

#### Request

"I want something." A Request contains one or more targets:

```
Request {
    requestId: integer          # Correlation ID (monotonic, unique per connection)
    targets: [Target]           # What is being requested
}
```

**Target types:**

| Target | Fields | Description |
|--------|--------|-------------|
| **Item** | `iid`, `vid?` | Request an item's manifest, optionally at a specific version |
| **Content** | `cid` | Request content bytes by hash |
| **Relations** | `subject?`, `predicate?`, `object?`, `subscribe?` | Query relations with optional filters; optionally subscribe to updates |

A single Request can ask for multiple things at once.

#### Delivery

"Here's something." A Delivery contains one or more payloads, correlated to a Request by ID:

```
Delivery {
    requestId: integer          # Echoes the Request ID (0 = unsolicited push)
    payloads: [Payload]         # What is being delivered
}
```

**Payload types:**

| Payload | Fields | Description |
|---------|--------|-------------|
| **Item** | `manifest` | A signed manifest |
| **Content** | `cid`, `data` | Content bytes with their hash |
| **Relations** | `relations` | A list of signed relations |
| **NotFound** | `iid` | "I don't have this item" |
| **Envelope** | `nextHop`, `origin`, `inner` | Wrapped message for relay forwarding |

#### Heartbeat

A keep-alive signal with no payload. Sent on idle connections to prevent timeout.

### Connection Lifecycle

#### Handshake

When two Librarians connect, both sides immediately send an unsolicited Delivery (requestId = 0) containing their own manifest. This is the handshake:

```
Librarian A                         Librarian B
     |                                    |
     |--- connect ----------------------->|
     |                                    |
     |--- Delivery(id=0, my manifest) --->|
     |<-- Delivery(id=0, their manifest) -|
     |                                    |
     [A now knows B's identity]    [B now knows A's identity]
```

After the handshake, both sides know each other's ItemID, public key, and display name. The peer is now **identified**.

#### Graph-Native Network Relations

When a peer identifies, the protocol handler automatically creates signed relations recording the event:

```
local  → PEERS_WITH → remote
remote → REACHABLE_AT → Endpoint(protocol, host, port)
```

These aren't side-channel metadata — they're first-class Items in the graph, queryable and auditable like any other relation. Your network topology is part of your graph.

### Request/Response Flow

Either side can send a Request at any time. The responder looks up the requested data in its Library and sends back a Delivery with the matching requestId:

```
Requester                           Responder
     |                                    |
     |--- Request(id=42, Item X) -------->|
     |                                    |
     |                           [Look up X in Library]
     |                                    |
     |<-- Delivery(id=42, Manifest(X)) ---|
     |                                    |
```

If the item isn't found:

```
     |<-- Delivery(id=42, NotFound(X)) ---|
```

### Subscriptions

A Request with `subscribe: true` on a Relations target establishes a persistent subscription. The responder sends an initial Delivery with current matching relations, then pushes unsolicited Deliveries (requestId = 0) whenever new matching relations appear:

```
Subscriber                          Publisher
     |                                    |
     |--- Request(id=43,                  |
     |    Relations(p=AUTHOR,             |
     |    subscribe=true)) -------------->|
     |                                    |
     |<-- Delivery(id=43, [relations]) ---|
     |                                    |
     ...time passes, new relation added...
     |                                    |
     |<-- Delivery(id=0, [new relation]) -|
     |                                    |
```

Subscription filters support wildcards — a null subject, predicate, or object means "any." Per-connection subscription limits prevent abuse (default: 100 per peer).

### Relay Forwarding

The Envelope payload enables indirect communication through trusted intermediaries. A message can be wrapped and forwarded through peers that are reachable even when the final destination is not directly accessible:

```
Origin                   Relay                    Destination
  |                        |                           |
  |-- Delivery with        |                           |
  |   Envelope(next=Dest,  |                           |
  |   origin=Origin,       |                           |
  |   inner=[Request]) --->|                           |
  |                        |                           |
  |                        |-- Delivery with           |
  |                        |   Envelope(inner) ------->|
  |                        |                           |
  |                        |<-- Delivery with          |
  |                        |   Envelope(response) -----|
  |                        |                           |
  |<-- Delivery with       |                           |
  |   Envelope(response) --|                           |
  |                        |                           |
```

The relay checks if `nextHop` matches its own identity. If yes, it unwraps and processes the inner message. If no, it forwards to the next hop. Each relay records an `ACKNOWLEDGES_RELAY` relation — again, graph-native auditing.

### Pending Request Management

Requests are tracked by a monotonic counter per connection. Each pending request has a timeout (default: 30 seconds). If no Delivery arrives within the timeout, the request is considered failed. The counter avoids collisions that timestamp-based IDs would create under rapid request bursts.

---

## Session Protocol (Client-to-Librarian)

The Session Protocol connects a UI client (text terminal, 2D graphical, 3D spatial) to a Librarian. Where the CG Protocol is about peer-to-peer data exchange, the Session Protocol is about human interaction — dispatching verbs, navigating items, receiving live updates.

### Transport

- **Framing**: Length-prefixed messages: `[4-byte length][1-byte type][CBOR payload]`
- **Connection types**: Local (Unix socket or loopback TCP), remote (TCP with TLS)

### Message Types

| Type | Code | Direction | Description |
|------|------|-----------|-------------|
| **AUTH** | 0x01 | Both | Authentication handshake |
| **CONTEXT** | 0x02 | Both | Get/set the currently focused item |
| **DISPATCH** | 0x03 | Client → Librarian | Execute a verb on an item |
| **LOOKUP** | 0x04 | Client → Librarian | Token completion and search |
| **SUBSCRIBE** | 0x05 | Client → Librarian | Watch for changes to items or relations |
| **EVENT** | 0x06 | Librarian → Client | Push notification of changes |
| **STREAM** | 0x07 | Librarian → Client | Chunked long-running output |
| **ERROR** | 0x08 | Librarian → Client | Error response |
| **OK** | 0x09 | Librarian → Client | Acknowledgment |

### Authentication

The Session Protocol supports multiple authentication methods:

| Method | Use Case |
|--------|----------|
| **Token** | Simple local sessions (same machine) |
| **Principal signature** | Remote sessions (prove identity cryptographically) |
| **Pairing code** | New device setup (one-time code exchange) |

The AUTH message carries the method-specific payload: challenge/response for signature auth, bearer token for token auth, or pairing code for device setup.

### Interaction Flow

A typical session interaction:

```
Client                              Librarian
  |                                      |
  |--- AUTH(token) --------------------->|
  |<-- OK -------------------------------|
  |                                      |
  |--- CONTEXT(get) -------------------->|
  |<-- CONTEXT(item: current focus) -----|
  |                                      |
  |--- DISPATCH("create", params) ------>|
  |<-- OK(result: new item) -------------|
  |                                      |
  |--- SUBSCRIBE(item: X) -------------->|
  |<-- OK -------------------------------|
  |                                      |
  ...item X changes...
  |                                      |
  |<-- EVENT(item: X, changed) ----------|
  |                                      |
```

### DISPATCH

The DISPATCH message is the primary action mechanism. The client sends a verb token (in any language) with parameters, and the Librarian resolves it through the vocabulary system (see [Vocabulary](vocabulary.md)):

```
DISPATCH {
    verb: string            # Token in any language ("create", "crear")
    target: ItemID?         # Target item (null = current context)
    params: map             # Named parameters
}
```

The Librarian resolves the token to a sememe via the TokenDictionary, checks the target item's vocabulary for a matching VerbEntry (inner-to-outer: component, then item, then session), invokes the method, and returns the result as OK or ERROR.

### LOOKUP

Token completion for the expression input system. As the user types, the client sends partial text and receives semantically-narrowed completion candidates from the TokenDictionary:

```
LOOKUP {
    text: string            # Partial input
    context: ItemID?        # Current item context (scope for resolution)
}

--> Response: list of Posting records (matched sememes/items with relevance)
```

The Librarian performs a scoped prefix search: `tokenDictionary.prefix(text, limit, context)`. Results include both global postings (language-level: verbs, nouns, types) and scoped postings (context-specific: component names, custom aliases). See [Vocabulary](vocabulary.md) for how the expression input uses these completions.

### SUBSCRIBE / EVENT

The client subscribes to changes on specific items. When those items are modified, the Librarian pushes EVENT messages. This drives live UI updates — tree views refresh, surfaces re-render, badges update.

---

## Protocol Comparison

| Aspect | CG Protocol (P2P) | Session Protocol (Client) |
|--------|-------------------|---------------------------|
| **Purpose** | Data exchange between Librarians | Human interaction with a Librarian |
| **Participants** | Librarian ↔ Librarian | Session UI ↔ Librarian |
| **Identity** | Both sides are Items (Signers) | Client authenticates to Librarian |
| **Message types** | 2 (Request + Delivery) | 9 (AUTH, CONTEXT, DISPATCH, etc.) |
| **Statefulness** | Minimal (subscriptions only) | Stateful (focused item, auth state) |
| **Data flow** | Symmetric (either side requests) | Asymmetric (client dispatches, Librarian responds) |

---

## Wire Format

Both protocols use CG-CBOR for message encoding. The CG Protocol wraps messages as:

```
[4-byte big-endian length][CBOR message body]
```

The Session Protocol adds a type byte:

```
[4-byte big-endian length][1-byte type code][CBOR message body]
```

All CBOR within protocol messages follows the canonical encoding rules defined in [CG-CBOR](cg-cbor.md): deterministic field order, no floats, no indefinite-length encoding.

## Endpoint Addressing

Librarian endpoints are described as:

```
Endpoint {
    protocol: string        # "cg" for Common Graph protocol
    host: IpAddress          # Binary IP (4 bytes IPv4, 16 bytes IPv6)
    port: integer            # 0-65535
}
```

Text representation: `cg://192.168.1.1:7432` or `cg://[::1]:7432` for IPv6.

Endpoints are stored as relation objects (e.g., `librarian → REACHABLE_AT → Endpoint`), making network topology discoverable through normal graph queries. See [Network Architecture](network.md) for how these relations form the routing layer.

## References

**External resources:**
- [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html) — Base encoding format
- [Freenet](https://freenetproject.org/) — Content-addressed P2P storage
- [IPFS Bitswap](https://docs.ipfs.tech/concepts/bitswap/) — Content-addressed block exchange
- [Secure Scuttlebutt](https://scuttlebutt.nz/) — Append-only log replication

**Academic foundations:**
- [Stoica et al 2001 — Chord](references/Stoica%20et%20al%202001%20-%20Chord%20Scalable%20Peer-to-Peer%20Lookup.pdf) — Consistent hashing ring for scalable peer lookup
- [Maymounkov, Mazieres 2002 — Kademlia](references/Maymounkov%2C%20Mazieres%202002%20-%20Kademlia%20DHT.pdf) — XOR-distance DHT routing (used by BitTorrent, IPFS, Ethereum)
- [Ratnasamy et al 2001 — Content-Addressable Network](references/Ratnasamy%20et%20al%202001%20-%20A%20Scalable%20Content-Addressable%20Network.pdf) — DHT using d-dimensional coordinate spaces
- [Benet 2014 — IPFS](references/Benet%202014%20-%20IPFS%20Content%20Addressed%20Versioned%20P2P%20File%20System.pdf) — Content-addressed P2P file system
- [Lamport 1978 — Time, Clocks, and Ordering](references/Lamport%201978%20-%20Time%20Clocks%20and%20Ordering%20of%20Events.pdf) — Logical clocks and causal ordering in distributed systems
- [Mattern 1989 — Vector Clocks](references/Mattern%201989%20-%20Virtual%20Time%20and%20Global%20States%20of%20Distributed%20Systems.pdf) — Complete causal relationship tracking
- [Baird 2016 — Hashgraph Consensus](references/Baird%202016%20-%20Swirlds%20Hashgraph%20Consensus%20Algorithm.pdf) — Virtual voting on gossip-about-gossip DAG
- [Shapiro 2011 — CRDTs](references/Shapiro%202011%20-%20Conflict-free%20Replicated%20Data%20Types.pdf) — Data structures that converge without coordination
- [Tschudin, Baumann 2019 — Merkle-CRDTs](references/Tschudin%2C%20Baumann%202019%20-%20Merkle-CRDTs.pdf) — Content-addressed CRDTs
- [Kleppmann 2019 — Local-First Software](references/Kleppmann%202019%20-%20Local-First%20Software.pdf) — The manifesto for offline-first, user-owned data
