# Network Architecture

Common Graph is a peer-to-peer network where every Librarian is a sovereign node. There are no dedicated servers, no central authorities, and no global indexes. Content lives where users put it. Discovery happens through social topology. The network scales because most interactions are local, and the ones that aren't propagate through trust paths that converge fast.

This document covers the high-level network architecture: how Librarians find each other, discover content, replicate data, and route queries. For wire-level protocol details, see [Protocol](protocol.md). For trust and signing, see [Trust](trust.md).

---

## Principles

**Local-first.** All data lives on the user's devices. Networking is explicit — nothing leaves your Librarian without a reason. Most interactions (reading, editing, navigating, dispatching verbs) are entirely local and never touch the network.

**Social topology.** The network's shape is the social graph. Your Librarian's peers are the Librarians of people and organizations you interact with. Discovery fans out through these trust paths. There is no separate "network layer" — the graph IS the network.

**Content-addressed.** Every piece of content has a CID (hash of bytes). Every item has an IID (stable identity). Every version has a VID (hash of manifest). These are universal, location-independent identifiers. Any Librarian that has the content can serve it. There is no origin server.

**Signed everything.** Manifests, relations, and protocol messages are cryptographically signed. You can verify the provenance of anything without trusting the node that delivered it. This makes untrusted intermediaries safe — a relay can't tamper with what it forwards.

**No special nodes.** A phone running a Librarian and a datacenter running a Librarian speak the same protocol, hold the same kinds of items, and participate as equals. Some Librarians are better-connected, more available, or have more storage — but that's a quantitative difference, not a qualitative one.

---

## The Social Graph as Routing Layer

### Librarian Items ARE the Routing Table

Every Librarian is an Item (a Signer with its own Ed25519 key). When two Librarians connect, they exchange manifests and create signed relations:

```
myLibrarian  --> PEERS_WITH   --> theirLibrarian
theirLibrarian --> REACHABLE_AT --> Endpoint("cg", 192.168.1.1, 7432)
```

These relations are first-class graph data — queryable, auditable, signed. Your Librarian can also add private relations to peer Items:

```
theirLibrarian --> [private: trustScore]    --> 0.92
theirLibrarian --> [private: lastSeen]      --> 2026-03-07T14:22:00Z
theirLibrarian --> [private: avgLatencyMs]  --> 12
theirLibrarian --> [private: reliability]   --> 0.99
```

There is no separate routing table format. The graph of Librarian Items, their relations, and your private annotations IS the routing table.

### Predicates ARE Indexes

Every relation predicate (a Sememe) is a natural index. When `PEERS_WITH` has its index flag set, querying that predicate returns all peers. When `HAS_CONTENT` is indexed, querying it returns all content a Librarian has announced. When `SUBSCRIBES_TO` is indexed, it maps topics to interested parties.

This means:

- **Peer list** = query `PEERS_WITH` on your Librarian
- **Content directory** = query `HAS_CONTENT` across known peers
- **Topic subscribers** = query `SUBSCRIBES_TO` on a topic Sememe
- **Domain claims** = query `CLAIMS_DOMAIN` for DNS-like resolution
- **Type instances** = query `INSTANCE_OF` for finding items by type

No new mechanisms needed. The relation index that already exists for semantic queries is the same index used for network topology, content location, and peer discovery.

---

## Discovery

### Where Content Lives

Content lives where users put it. If you browse something interesting, it gets cached on your device. If you explicitly save something, you choose which of your devices store it. Your Librarian handles replication between your own devices automatically.

This means storage placement is a *human/social choice*, not a hash-metric assignment. A company puts its content on its Librarian cluster. A user's photos live on their phone and laptop. A chess club's game history lives on members' devices. Content gravitates toward the people who care about it.

### Concentric Ripple Discovery

When your Librarian needs to find something it doesn't have, discovery fans out in concentric ripples:

```
                    +---------------------------+
                    |  4. Background propagation |
                    |  +---------------------+  |
                    |  | 3. Predicate gossip  |  |
                    |  |  +---------------+   |  |
                    |  |  | 2. Peer query  |  |  |
                    |  |  |  +---------+   |  |  |
                    |  |  |  | 1. Local |   |  |  |
                    |  |  |  +---------+   |  |  |
                    |  |  +---------------+   |  |
                    |  +---------------------+  |
                    +---------------------------+
```

**1. Local.** Check your own Library. This resolves the vast majority of queries instantly — you usually interact with content you already have.

**2. Peer query.** Ask your direct peers. Your Librarian sends a Request to peers most likely to have the content, based on:
- Content affinity (peers who share similar interests)
- Social relevance (the content creator's Librarian, or their contacts)
- Past success (peers who've answered similar queries before)

This resolves almost all remaining queries. In practice, most content you need comes from people you know or people they know.

**3. Predicate gossip.** If direct peers can't help, the query reaches peers who subscribe to relevant predicates. A query about chess content reaches the chess predicate ring. A query about a specific author reaches peers who follow that author. Topic-scoped gossip means you reach interested parties without flooding the whole network.

**4. Background propagation.** For truly unknown content — something outside your entire social circle — the query can propagate further. Each Librarian that receives the query checks its own index, returns results if found, and optionally forwards to its own peers. The query travels through the graph, running independently on each Librarian it reaches.

Most queries never leave level 1. A small fraction reach level 2. Levels 3 and 4 are rare — they handle the "find something nobody I know has ever seen" case.

### Query Propagation

A query that needs to propagate beyond direct peers becomes a traveling message:

```
You --> PeerA --> PeerA's peers --> ...
  \--> PeerB --> PeerB's peers --> ...
  \--> PeerC --> (found it!) --> result flows back
```

Each Librarian that receives a propagating query:

1. Checks its own index for matches
2. If found: sends results back along the return path
3. If not found and depth budget remains: forwards to its own relevant peers
4. Deduplicates (doesn't re-forward queries it's already seen)

The query runs independently on each Librarian — there's no central coordinator. Results trickle back asynchronously. The depth budget (TTL) prevents unbounded flooding.

The small-world property of social networks makes this converge fast. With average connectivity of ~150 peers (Dunbar's number is a reasonable approximation for active peer connections), depth 2 reaches ~22,000 Librarians, depth 3 reaches ~3 million. Most content is reachable within 3-4 hops.

### Routing Strategies

The decision "which peers should I forward this query to?" can use different strategies. These aren't mutually exclusive — they're a toolkit:

| Strategy | How it works | Best for |
|----------|-------------|----------|
| **Social distance** | Ask close, trusted peers first | General queries, personal content |
| **Content affinity** | Ask peers who tend to have similar content | Topic-specific searches |
| **Predicate subscription** | Ask peers subscribed to relevant topics | Narrow domain queries |
| **Creator proximity** | Route toward the creator's social circle | Finding specific items |
| **Geographic proximity** | Ask nearby peers | Latency-sensitive queries |
| **Hash distance** | Kademlia-style XOR metric | Exhaustive fallback searches |

Hash-distance routing (DHTs like Kademlia, Chord) is one tool among many. It provides a guaranteed-convergent fallback when social routing can't find something, but it's not the primary mechanism. Social routing is faster, more relevant, and produces better-ranked results for the common case.

---

## Replication

### How Content Spreads

Content replicates along interest paths. When you interact with an item — view it, endorse it, relate to it — your Librarian caches it locally. When your peers interact with something you have, they cache it on theirs. Popular content naturally accumulates copies across many Librarians without any centralized coordination.

### Subscription-Driven Sync

The CG Protocol's subscription mechanism (see [Protocol](protocol.md)) provides real-time replication for content you care about:

```
Subscribe to: Relations(subject=chessClub, predicate=*, subscribe=true)
--> Receive all new relations on the chess club item as they're created
```

Your Librarian subscribes to items and predicates relevant to your interests. When peers create new relations or versions, you receive them via push. This is how chat messages arrive, game moves propagate, and shared documents sync.

### Device-to-Device Sync

A user's own devices (phone, laptop, desktop) are just very closely coordinated peers. They share the same principal (user identity) and can replicate aggressively (per policy) — everything you do on one device is available on all your devices. This is the same peer-to-peer protocol used for inter-user communication, just with higher trust and more frequent sync.

### Replication Policies

Librarians can implement replication policies as Items (with vocabulary and relations like everything else):

- **Pin**: "Keep this content available, don't garbage-collect it"
- **Mirror**: "Replicate everything from this peer/topic"
- **Quota**: "Store up to N bytes of cached content, evict LRU"
- **Priority**: "Always keep content from these peers; cache others opportunistically"

These policies are local decisions — each Librarian decides what it stores and for how long. There is no global coordination required.

---

## Peer Economics

### Favors and Reputation

Even without explicit agreements, Librarians naturally do things for each other: relay a message, answer a query, cache content that a peer requested. These are *favors* — small acts of cooperation that the network depends on.

Every favor is observable. When a peer relays your message, you know. When a peer answers your query, you know. When a peer caches your content and serves it to others, those others can report it. Over time, these observations build into a *reputation* — tracked as private relations on peer Items (see [The Social Graph as Routing Layer](#the-social-graph-as-routing-layer)).

A Librarian that consistently relays quickly, answers queries honestly, and stays available builds trust. A Librarian that drops messages, returns garbage, or disappears frequently loses trust. This happens organically through the service trust layer (see [Trust](trust.md)) — no central reputation authority needed.

Reputation influences routing. When your Librarian chooses which peers to forward a query to, it considers past reliability. Trustworthy peers get asked first. Unreliable ones get deprioritized or dropped. The network self-organizes around cooperation.

### Agreements

Peers can formalize their cooperation through **hosting agreements** — Items that describe terms for storage, bandwidth, computation, or availability:

```
agreement:hosting-deal → parties    → [librarian:Alice, librarian:AcmeCloud]
agreement:hosting-deal → terms      → "Store up to 50GB, 99.9% availability"
agreement:hosting-deal → duration   → 2026-01-01..2027-01-01
agreement:hosting-deal → payment    → quantity(10, USD/month)
```

These are signed Items with signed relations — both parties endorse the agreement. The terms are semantic, not just legal text — predicates like `storageQuota`, `availabilityTarget`, and `bandwidthAllowance` are machine-readable.

This is effectively a **smart hosting contract**: an enforceable agreement between peers, recorded in the graph, verifiable by anyone. Unlike blockchain smart contracts, these don't require global consensus — they're bilateral agreements between the parties involved, with the trust system providing accountability.

### What Agreements Can Cover

- **Storage**: "Keep these Items available for N months"
- **Bandwidth**: "Serve my content at up to X requests/second"
- **Computation**: "Run these queries against your index on my behalf"
- **Relay**: "Forward messages to my devices when they're behind NAT"
- **Replication**: "Mirror my content across your geographic regions"
- **Indexing**: "Maintain a specialized index for this predicate domain"

### Payment

Agreements can involve payment — represented as `Quantity` values with currency units (which are themselves Sememes). Payment settlement is outside the protocol, but the agreement, its terms, and fulfillment tracking are all graph-native.

Even without money, peers can trade services: "I'll store your content if you relay my messages." Barter agreements are the same Item structure, just with services on both sides instead of currency.

## Big Librarians

Companies, organizations, and infrastructure providers run Librarians too — just better-connected ones with more storage and higher availability. These are not "servers" in the traditional sense — they're peers in the graph that happen to be always-on, well-connected, and storage-rich.

### What Big Librarians Provide

- **Availability**: Always-on presence for content that needs to be reachable 24/7
- **Storage**: Large-scale content hosting (media, databases, archives)
- **Bandwidth**: High-throughput connections for popular content distribution
- **Indexing**: Specialized indexes for domain-specific discovery (e.g., a music Librarian that indexes audio content by metadata predicates)
- **Relay**: NAT traversal for devices behind firewalls (see [Protocol: Relay Forwarding](protocol.md#relay-forwarding))

### What They Don't Provide

- **Identity authority**: Users own their keys. A company's Librarian doesn't control user identity — it stores and serves content on behalf of users who choose to use it.
- **Data lock-in**: Content is content-addressed and signed by its creator. Users can take their Items anywhere. A Librarian is a custodian, not an owner.
- **Exclusive access**: Content served by one Librarian can be replicated to any other. There's no artificial scarcity of access.

### Economic Model

The business model for big Librarians is infrastructure services, not data harvesting:

- **Storage hosting**: "We'll keep your Items available and well-connected"
- **Bandwidth**: "We'll serve your popular content at scale"
- **Specialized indexing**: "We'll maintain high-quality indexes for your domain"
- **Availability guarantees**: "We'll keep your Librarian running with N nines uptime"

This is closer to a utility model (electricity, water) than an advertising model (attention harvesting). Companies compete on quality of service, not on lock-in. And because the terms are expressed as [peer agreements](#agreements), users can compare, switch, and hold providers accountable through the same trust system that governs all peer interactions.

---

## Bootstrap and Initial Discovery

A new Librarian needs to find at least one peer to join the network. Several bootstrap mechanisms are available:

### DNS Discovery

Organizations can advertise their Librarian endpoints via DNS:

```
_commongraph._tcp.example.com.  IN SRV  0 0 7432 librarian.example.com.
_commongraph._tcp.example.com.  IN TXT  "iid=<base58-encoded IID>"
```

Or via a well-known HTTPS endpoint:

```
https://example.com/.commongraph/librarian.json
{
    "iid": "<base58-encoded IID>",
    "endpoints": ["cg://librarian.example.com:7432"],
    "publicKey": "<base58-encoded SPKI>"
}
```

This is a bootstrap mechanism only — once connected, discovery uses the graph.

### Invitation Links

A user can generate an invitation that contains enough information to connect:

```
cg://invite/<base58-encoded: IID + endpoint + one-time pairing code>
```

The invited Librarian connects, authenticates via the pairing code, and the two become peers. From that point, the new Librarian discovers additional peers through the inviter's social graph.

### Local Network Discovery

Librarians on the same local network can find each other via mDNS/DNS-SD:

```
_commongraph._tcp.local.
```

This is particularly useful for a user's own devices finding each other on a home or office network.

---

## Scaling Properties

### Why It Works

The architecture scales because of several reinforcing properties:

1. **Most queries are local.** Your Librarian has your content, your contacts' shared content, and cached content from your interests. The network is rarely needed.

2. **Discovery follows social topology.** Small-world networks have short average path lengths. Any content is reachable within a bounded number of hops.

3. **Content replicates along interest paths.** Popular content naturally accumulates copies. Obscure content stays at its origin but is findable through the creator's social circle.

4. **Predicate gossip scales with interest, not with network size.** A chess topic ring has thousands of members, not billions. Each Librarian only subscribes to topics it cares about.

5. **Content addressing provides free caching.** A CID is a universal, eternal cache key. Any Librarian that has content can serve it. No origin server needed.

6. **Signing enables untrusted intermediaries.** A relay can't tamper with signed content. This means any peer can help deliver content without being fully trusted.

7. **No central bottleneck.** There is no DNS root, no certificate authority, no search engine that everything depends on. The network is resilient to the failure or compromise of any single node.

### Comparison with the Web

| Property | Web (REST) | Common Graph |
|----------|-----------|--------------|
| **Content location** | Origin server (URL) | Anywhere (CID) |
| **Identity** | Domain-based (TLS certs) | Key-based (Ed25519) |
| **Discovery** | DNS + search engines | Social graph + predicate indexes |
| **Caching** | Heuristic (ETags, max-age) | Structural (content-addressed) |
| **Scaling model** | Horizontal (more servers) | Topological (content flows to interest) |
| **Trust model** | Certificate authorities | Signed relations, trust policies |
| **Data ownership** | Server operator | Content creator |
| **Intermediaries** | Transparent proxies | Signed relay forwarding |

### What "Global" Means

In Common Graph, "global" is not a separate tier with separate infrastructure. It's the same social graph at greater depth. The game "six degrees of Kevin Bacon" illustrates the principle — everyone is only a few relations from everyone else. A query for something truly unknown propagates through the graph, running independently on each Librarian it reaches, until it finds what it's looking for or exhausts its depth budget.

The network doesn't need a global directory to be globally reachable. It needs a connected social graph — which humans naturally create.

---

## References

- [Maymounkov, Mazieres 2002 — Kademlia](references/Maymounkov%2C%20Mazieres%202002%20-%20Kademlia%20DHT.pdf) — XOR-distance DHT routing
- [Stoica et al 2001 — Chord](references/Stoica%20et%20al%202001%20-%20Chord%20Scalable%20Peer-to-Peer%20Lookup.pdf) — Consistent hashing for peer lookup
- [Ratnasamy et al 2001 — CAN](references/Ratnasamy%20et%20al%202001%20-%20A%20Scalable%20Content-Addressable%20Network.pdf) — Content-addressable network
- [Benet 2014 — IPFS](references/Benet%202014%20-%20IPFS%20Content%20Addressed%20Versioned%20P2P%20File%20System.pdf) — Content-addressed P2P file system
- [Kleppmann 2019 — Local-First Software](references/Kleppmann%202019%20-%20Local-First%20Software.pdf) — Offline-first, user-owned data
- [Shapiro 2011 — CRDTs](references/Shapiro%202011%20-%20Conflict-free%20Replicated%20Data%20Types.pdf) — Conflict-free replicated data types
- [Tschudin, Baumann 2019 — Merkle-CRDTs](references/Tschudin%2C%20Baumann%202019%20-%20Merkle-CRDTs.pdf) — Content-addressed CRDTs
- [Fielding 2000 — REST](references/Fielding%202000%20-%20Architectural%20Styles%20and%20Network-Based%20Software%20REST.pdf) — The web's architectural style (for comparison)
- [Clarke et al 2001 — Freenet](references/Clarke%20et%20al%202001%20-%20Freenet%20Distributed%20Anonymous%20Information%20Storage.pdf) — Distributed anonymous information storage
- [Tarr et al 2019 — Secure Scuttlebutt](references/Tarr%20et%20al%202019%20-%20Secure%20Scuttlebutt%20Identity-Centric%20Protocol.pdf) — Identity-centric append-only log replication
