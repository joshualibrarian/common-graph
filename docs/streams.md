# Streams

Some data accumulates over time rather than existing as a single snapshot. Chat messages, activity logs, sensor readings, key-rotation histories, audit trails, video and audio chunks, document edit histories — each grows by appending new entries, not by replacing the whole. A **stream** is the shape this kind of data takes in Common Graph: an append-only sequence of frames linked by predecessor references, each entry signed independently, the whole structure verifiable end-to-end.

Streams are not a separate data type. They're a *pattern* — frames carrying FOLLOWS bindings that point at their predecessors, accumulating into chains. The same data model that describes a single MOVE frame in a chess game describes a chat-room's message history; the only structural difference is the predecessor links.

This document defines the stream pattern, how streams differ from versioned items, how they're stored and verified, and how they handle large content like media.

This document assumes familiarity with [frames](frames.md), [items](item.md), [manifests](manifest.md), and [content addressing](content.md).

## The pattern

A stream is a sequence of frames, each pointing at the previous frame via a `@FOLLOWS` binding. The first frame in the stream has no FOLLOWS (or a FOLLOWS pointing at a stream-root frame); each subsequent frame's FOLLOWS target is the prior frame's DatumID.

```
Message 1: {@message, [
  @AGENT → @alice,
  @CONTENT → "Hello",
  @LOCATION → @<room-iid>
]}

Message 2: {@message, [
  @AGENT → @bob,
  @CONTENT → "Hi Alice",
  @LOCATION → @<room-iid>,
  @FOLLOWS → #<message-1-cid>
]}

Message 3: {@message, [
  @AGENT → @alice,
  @CONTENT → "How are you?",
  @LOCATION → @<room-iid>,
  @FOLLOWS → #<message-2-cid>
]}
```

Each message is a complete frame in its own right — signed, content-addressed, verifiable. The chain emerges from the FOLLOWS bindings. To replay the stream, start at any known entry and walk the chain backward (toward the root) or forward (newer entries point at this one through their FOLLOWS).

Streams are typically associated with an item — the room the messages belong to, the sensor the readings come from, the document being edited. The item's manifest endorses the stream's frames either selectively (a curated subset is part of the item's content) or wholesale (a separate index tracks which frames belong to this stream).

## How streams differ from versioned items

Items and streams both have history, but they record different things.

**An item's history** is a sequence of *snapshots* of its state. Each manifest is the whole item at one moment; FOLLOWS on a manifest names the prior snapshot. A chess game's history is the sequence of game-state manifests, not the sequence of moves.

**A stream's history** is a sequence of *events*. Each frame is one event; FOLLOWS on a frame names the prior event. The chess game's MOVE frames form a stream — each frame is one move, the sequence is the game's play.

Both patterns coexist on the same item. A chess game item's manifest evolves through versions (board state at each turn); the move frames the game endorses form a stream (each move as it happened). Either gives you the game's history, viewed differently.

The distinction matters for queries:

- "What's the current state of the chess game?" → the current manifest, traversed for board position.
- "What was Alice's third move?" → the third MOVE frame in the stream.

The two views answer different questions. Streams are the right shape when each event matters in its own right; manifest versioning is the right shape when the *state* is what matters and individual edits are details.

## Stream entries are independent frames

Every stream entry is a normal frame — body plus records. Each is signed by its signer; each is hashable; each is independently verifiable. The chain doesn't bundle them into a single structure; they remain separate datums in storage, linked by reference.

This independence means:

- **Multi-signer streams work natively.** A chat room receives messages from many signers; each message's records carry the appropriate signer's signature. The chain holds messages from different signers in sequence without any special handling.
- **Entries can be redacted, deleted, or encrypted independently.** Removing one message from a chat room doesn't require touching others. Encrypting some messages for specific recipients doesn't affect others.
- **Replication is per-entry.** A librarian fetching a stream pulls each entry as it needs; missing entries can be requested individually; verification is per-entry.

The same content addressing that handles every other frame handles stream entries. No special storage path, no special protocol.

## Verification

A stream's integrity comes from the FOLLOWS chain. Each entry's FOLLOWS target is the previous entry's DatumID; the previous entry's DatumID is the structural hash of its content. To verify a stream:

1. Walk from the latest entry backward toward the root.
2. At each step, fetch the FOLLOWS target.
3. Verify the fetched frame's DatumID matches the FOLLOWS reference.
4. Verify each frame's records — signatures, signer authority, timestamp.

Any break in the chain — a frame missing, a hash mismatch, a forged FOLLOWS — fails verification. Splicing fakes into an existing stream requires regenerating every subsequent frame's FOLLOWS reference, which requires re-signing them, which requires the signers' keys.

This is the same chain-of-hashes verification Git uses for commits and that Bitcoin uses for blocks. Streams in Common Graph are one application of the pattern.

## Stream membership

How does the librarian know which frames belong to a particular stream? Several mechanisms work; they compose:

**By location binding.** A stream's frames carry a `@LOCATION` binding pointing at the item the stream belongs to. The reverse-binding index then lets the librarian find "all frames with LOCATION → @<room-iid>." The chain order comes from FOLLOWS; the membership comes from LOCATION.

**By predicate filter.** Stream entries headed by a specific predicate (e.g., `@message`) can be filtered from the predicate index. "All messages in this room" is a join: predicate is @message, LOCATION is the room.

**By manifest endorsement.** An item's manifest can explicitly endorse stream entries through `@ENDORSES` bindings. Useful when the item's curator wants a specific subset of the stream as the item's canonical content (e.g., a thread of selected messages, a featured subset of an activity log).

The default approach for most streams is (1) + (2) — frames carry their location and the predicate identifies the stream type. Endorsement is for curation, not membership.

## Stream-of-stream and forks

Streams can fork. Two frames can both point at the same predecessor (both have `@FOLLOWS → #<entry-N-cid>`), branching the chain. This is a feature, not a flaw — collaborative documents fork during concurrent edits; chat rooms might have parallel topics; sensor streams might split for parallel processing pipelines.

Resolving a fork happens at consumption time, not at the stream layer. A query for "the latest entry in this chat" might return multiple frames if the chain forked; the UI presents alternatives or the application-layer logic picks. The stream itself doesn't enforce linearity; the consumers decide what linear ordering, if any, they want.

This is the same dynamic that runs through Common Graph: the data model carries the facts; policy decides how to interpret them. Forks are facts; resolution is policy.

## Streams and large content

A stream is a natural container for large content broken into chunks. A video stream's frames each carry one chunk:

```
{@video-chunk, [
  @AGENT → @camera,
  @LOCATION → @<feed-iid>,
  @CHUNK → ~<chunk-bytes-cid>,
  @TIMESTAMP → 2026-05-16T14:23:00Z,
  @FOLLOWS → #<previous-chunk-cid>
]}
```

Each frame contains a CONTENT-by-CID reference to the actual bytes (a `~`-prefixed reference to a content blob); the frame itself carries metadata (timestamp, agent, sequence position). The librarian stores the chunks as content-addressed blobs in the object store; the chain of frames is the index that orders them.

Consumers fetching a video stream see two layers: the metadata chain (which they walk to get sequence, timestamps, gaps) and the content blobs (which they fetch on demand for actual playback). Either layer is independently fetchable; missing chunks don't break the chain; verification works at both layers.

The pattern works for audio streams, sensor readings, log entries, time-series data — anything where individual entries have their own identity and the chain provides order.

## Streams and retention

Stream entries are subject to the same retention policy as any other frame. Some streams retain everything indefinitely (audit logs, financial records, legal evidence); others retain only a window (transient chat history beyond N days, sensor readings beyond N samples); others are explicitly ephemeral (live cursor positions, presence indicators).

Retention is declared per-predicate (most streams of one kind share a policy) or per-frame (a specific frame can request shorter or longer retention than its predicate's default). The librarian honors the policy at garbage-collection time.

Ephemeral streams don't enter the FOLLOWS chain in a durable sense — they're dispatched and dropped. Live cursor updates in a collaborative document are streams in the sense that they're a sequence of timestamped events, but they're not retained, so verification of past entries isn't possible. This is intentional: the events are interesting at the moment they happen and not afterward.

## Streams in transport

When two librarians share a stream, the network carries the entries as ordinary frames. Subscribing librarians fetch entries as they're produced; the FOLLOWS chain handles sequencing; the chain breaks (a missing entry) are detectable and recoverable by requesting the missing entry from a peer.

Streams are well-suited to peer-to-peer distribution. A popular chat room's messages fan out to subscribers naturally; a video stream replicates by interested viewers fetching chunks as they need; sensor data flows from the producer to whoever's subscribed. The unit of replication is the frame, not the stream — partial replication is the norm and works naturally.

The full network model lives in [`network.md`](network.md). Streams as such don't require special protocol support beyond what frames already have.

## Worked examples

**A chat room as a stream of messages.**

```
Each message is a frame:
  {@message, [
    @AGENT → @<sender>,
    @LOCATION → @<room>,
    @CONTENT → "...",
    @TIMESTAMP → <ISO 8601>,
    @FOLLOWS → #<previous-message-cid>
  ]}

The chain is built by FOLLOWS; membership comes from LOCATION;
the room item endorses some messages (pinned ones, possibly all);
unendorsed messages exist in the librarian's store but aren't part
of the room's canonical content.
```

**An audit log on a banking item.**

```
Each transaction is a frame:
  {@transaction, [
    @AGENT → @<initiator>,
    @LOCATION → @<account>,
    @AMOUNT → {@quantity, [@VALUE → 100, @UNIT → @usd]},
    @TIMESTAMP → <ISO 8601>,
    @FOLLOWS → #<previous-transaction-cid>
  ]}

Every transaction is signed by its initiator. The chain is unbroken;
any gap or modification is detectable. The bank's audit query walks
the chain backward; regulatory reads verify each entry's signatures.
```

**A sensor's reading stream.**

```
Each reading is a frame:
  {@reading, [
    @AGENT → @<sensor>,
    @LOCATION → @<facility>,
    @VALUE → {@quantity, [@VALUE → 23.5, @UNIT → @celsius]},
    @TIMESTAMP → <ISO 8601>,
    @FOLLOWS → #<previous-reading-cid>
  ]}

Readings flow at 1 Hz. The chain accumulates indefinitely;
retention is policy-driven. Downstream consumers (alerting,
historical analysis) fetch slices of the chain as needed.
```

**Video chunks as a media stream.**

```
Each chunk-frame is a frame:
  {@video-chunk, [
    @AGENT → @<camera>,
    @LOCATION → @<feed>,
    @CHUNK → ~<chunk-bytes-cid>,
    @SEQUENCE → <integer>,
    @TIMESTAMP → <ISO 8601>,
    @FOLLOWS → #<previous-chunk-frame-cid>
  ]}

The frames form the index; the chunks (content blobs) are the data.
Playback fetches the frames in order, fetches the chunk bytes for each,
streams to the player.
```

## Why streams are just frames

A common temptation in storage systems is to special-case append-only data — a dedicated log structure, a different storage path, a separate query interface. The benefits look like efficiency (logs are write-heavy; specialization speeds them up); the costs are complexity (two storage layers to maintain, two query interfaces to learn, edge cases at the boundary).

Common Graph treats streams as frames. The FOLLOWS chain is just a binding; the content is just a frame body; the signers are just records. Streams reuse the entire frame infrastructure — storage, indexes, verification, network, dispatch — without special-casing anything. New stream types are vocabulary additions (a new predicate, possibly a new archetype); they're not engineering additions.

The cost is per-frame overhead — each entry is a full frame with its own signature, its own DatumID, its own storage entry. For very high-throughput cases (millions of entries per second) this would matter; for the vast majority of cases (human-pace chat, business-pace logs, sub-MB-per-second sensor streams, media at chunk granularity), the overhead is negligible and the architectural simplicity is worth far more.

## Relations

- [`frames.md`](frames.md) — frames as the primitive streams are built from.
- [`manifest.md`](manifest.md) — versioning vs streaming as two history patterns.
- [`item.md`](item.md) — items as the things streams typically belong to.
- [`content.md`](content.md) — content blobs referenced by `~` from stream frames.
- [`storage.md`](storage.md) — how stream entries are indexed and fetched.
- [`network.md`](network.md) — peer-to-peer distribution of stream entries.
- [`encryption.md`](encryption.md) — encrypting individual stream entries.
