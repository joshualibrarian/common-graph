# Streams and Logs

**Streams** are append-only components used for logs, chat, activity feeds, key history, and other growing data. Unlike snapshot components (immutable content replaced on each version), stream components grow over time while maintaining integrity through hash-linked entries.

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

Stream components record their current state in the manifest's ComponentEntry:

```
ComponentEntry {
    handle: "chat"
    type: ChatLog type IID
    streamBased: true
    streamHeads: [head_cid]     # Current tip(s)
}
```

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

Components can be either SNAPSHOT-authoritative or STREAM-authoritative:

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

## Related Work

- [Secure Scuttlebutt](https://scuttlebutt.nz/) — Append-only log replication for social networks
- [Merkle-CRDTs](https://arxiv.org/abs/2004.00107) — CRDTs over Merkle DAGs
- [Certificate Transparency](https://certificate.transparency.dev/) — Append-only logs for PKI auditing
