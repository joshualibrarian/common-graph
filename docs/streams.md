# Streams and Logs

**Streams** are append-only frame types used for logs, chat, activity feeds, key history, and other growing data. Unlike snapshot frames (immutable content replaced on each version), stream frames grow over time while maintaining integrity through hash-linked entries.

## Stream Structure

A stream is:
- **Root** — Genesis block with stream metadata
- **Entries** — Append-only sequence of hash-linked entries
- **Head(s)** — Current tip CID(s)

```
Root (CID: genesis123)
  └── Entry 1 (CID: entry1, parent: genesis123)
        └── Entry 2 (CID: entry2, parent: entry1)
              └── Entry 3 (CID: entry3, parent: entry2)  ← HEAD
```

Each entry is content-addressed: its CID is the hash of its bytes. Each entry references its parent by CID, forming a hash chain that's tamper-evident — modifying any entry changes its CID, breaking the chain.

## In the Manifest

Stream frames record their current state in the manifest's Frame:

```
Frame {
    key: (CHAT)
    type: ChatLog type IID
    bodyHash: <hash of FrameBody with stream binding>
}
```

The stream heads are stored as bindings on the FrameBody (with a compound key like `(TOPIC, STREAM)`), not as top-level fields.

## Core Stream Types

### KeyLog

Tracks key lifecycle events for Signers (see [Trust](trust.md)):

```
ADD key123 at time T1
ROTATE key123 → key456 at time T2
REVOKE key456 at time T3
```

This is the authoritative record of a Signer's key history. Verification walks the KeyLog to check if a key was valid at a given time.

### CertLog

Certificate and attestation history:

```
ISSUE cert for subject X at time T1
REVOKE cert for subject X at time T2
```

### ChatLog

Message stream for chat and discussion:

```
Message from user:Alice at T1: "Hello!"
Message from user:Bob at T2: "Hi there!"
```

### Activity Log

Generic event stream for audit trails:

```
Event: item:123 modified at T1 by user:Alice
Event: item:456 created at T2 by user:Bob
```

### Roster

Participant list tracking membership changes:

```
ADD user:Alice as "member" at T1
ADD user:Bob as "admin" at T2
REMOVE user:Alice at T3
```

The current membership is computed by replaying the stream from root to head. This provides both current state and full history of who was a member and when.

## Checkpoints

Streams can be **checkpointed** into item versions:

1. Stream advances independently (entries append between commits)
2. At commit time, the current head CID is recorded in the manifest
3. The manifest now references that specific stream state

This lets you:
- Have streams evolve continuously between commits
- Pin specific stream states in versions
- Query historical stream states via VID
- Roll back to a previous checkpoint

## Authority: SNAPSHOT vs STREAM

Frame types can be either SNAPSHOT-authoritative or STREAM-authoritative:

| Authority | Truth | Other |
|-----------|-------|-------|
| **SNAPSHOT** | `snapshotCid` is canonical | Stream is optional history |
| **STREAM** | Stream entries are canonical | Snapshot is derived/materialized |

A CRDT document might use STREAM authority — edits go to the stream, and the snapshot is a materialized view computed from the stream entries.

## Multi-Head Streams

Streams can have multiple heads (branches/forks), which arise when two writers append concurrently without seeing each other's entries:

```
      Entry 1
      /     \
  Entry 2a  Entry 2b
     |         |
  Entry 3a  Entry 3b
     ↑         ↑
   HEAD1     HEAD2
```

The manifest records all heads. How forks are resolved depends on the stream type:
- **KeyLog**: Forks indicate conflicting key operations — requires manual resolution
- **ChatLog**: Forks can be merged by interleaving entries by timestamp
- **Roster**: Forks can be merged by applying all operations

This is related to the broader field of [Merkle-CRDTs](https://arxiv.org/abs/2004.00107) — conflict-free replicated data structures built on Merkle DAGs.

## Streams vs. Ephemeral Frames

Streams (Chains) and ephemeral frames are both temporal data, but with different retention semantics:

| | Streams (Chains) | Ephemeral Frames |
|---|---|---|
| **Retention** | ALL — every entry is kept, history matters | LATEST — only the most recent value per key |
| **Persistence** | Content-addressed chunks in the object store | In-memory only, never persisted |
| **Consumption** | Replay from root, or catch up from checkpoint | Read current value, ignore history |
| **Lifetime** | Permanent (or until garbage collected) | Tied to signer's PRESENT frame or connection |
| **Examples** | Chat log, key history, audio/video feed, activity log | Avatar position, typing indicator, cursor, focus |
| **Content model** | TOPIC:[STREAM] binding → Chain root → linked chunks | Normal frame with LATEST retention predicate |

Both can coexist on the same item. A PRESENT frame (durable) carries TOPIC stream bindings for video/audio Chains, while AVATAR_STATE (ephemeral, LATEST) carries position data that replaces every tick. The predicate's lifecycle policy — declared as part of the predicate's schema alongside EXPECTS — determines which mode applies.

## Related Work

- [Secure Scuttlebutt](https://scuttlebutt.nz/) — Append-only log replication for social networks
- [Merkle-CRDTs](https://arxiv.org/abs/2004.00107) — CRDTs over Merkle DAGs
- [Certificate Transparency](https://certificate.transparency.dev/) — Append-only logs for PKI auditing
- [Smith, Kay, Raab, Reed 2003 — Croquet](references/Smith%2C%20Kay%2C%20Raab%2C%20Reed%202003%20-%20Croquet%20A%20Collaboration%20System%20Architecture.pdf) — TeaTime protocol for replicated, versioned objects with coordinated timebase. CG achieves similar real-time collaboration through lifecycle policies on the frame primitive rather than a separate collaboration protocol.
- [Reed 1978 — Naming and Synchronization](references/Reed%201978%20-%20Naming%20and%20Synchronization%20in%20a%20Decentralized%20Computer%20System.pdf) — Decentralized naming, versioned objects, and synchronization without central authority. Intellectual ancestor of both TeaTime and CG's per-principal versioned manifests.
