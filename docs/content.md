# Content

Some of what Common Graph holds is structured datums; the rest is raw bytes — image pixels, audio samples, video chunks, source code, compiled bytecode, blob payloads of every other kind. **Content** is the term for that raw-bytes side. Content lives in the same content-addressed object store as datums but is opaque to the system above: the bytes are just bytes, identified by their hash, fetched when needed.

This document defines content addressing, the `~` reference that points at content, and how content relates to the encoding-agnostic identity that datums get from their structural hash.

This document assumes familiarity with [the datum primitive](datum.md), [the canonical walker](canonical.md), and [the reference scheme](ref-scheme.md).

## ContentID

A **ContentID** is a multihash of a sequence of bytes:

```
ContentID = multihash(<algorithm>, <byte-sequence>)
```

Properties that follow from the construction:

- **Deterministic.** Same bytes → same ContentID, always.
- **Tamper-evident.** Any change to the bytes changes the ContentID. A reader fetching by ContentID can rehash the result and confirm it matches.
- **Self-describing.** The multihash carries the algorithm identifier in its leading bytes; readers know how the hash was computed without external context.

ContentIDs are referenced through the `~` prefix: `~<contentid>`. The reference scheme treats this as one of the five primitive reference variants (see [`ref-scheme.md`](ref-scheme.md)). When you see `~<cid>` anywhere in a frame's bindings, it means "the bytes whose hash is this."

## ContentID vs DatumID

The two hashes answer different questions:

- **DatumID** — "what does this datum *say*?" Computed from structure, independent of encoding. Same DatumID across every encoding format.
- **ContentID** — "what are *these exact bytes*?" Computed from the actual byte sequence. Encoding-specific by definition.

A datum encoded as CG-CBOR bytes has a ContentID for those bytes. The same datum encoded as CG-JSON would have a *different* ContentID (different bytes), but the same DatumID. Both ContentIDs identify byte sequences that decode to the same datum; the DatumID identifies the datum itself.

Most internal references go through DatumID — the system cares about the datum, not the bytes. ContentID matters for:

- **Raw content** — image bytes, audio samples, blob payloads, anything whose meaning isn't a datum structure.
- **Transport verification** — proving that the bytes you received are the bytes someone sent.
- **Local storage indexing** — the byte-level address for fetch operations.
- **Byte-level deduplication** — identifying identical encoded forms regardless of higher-level meaning.

Inside Common Graph, ContentIDs primarily appear for raw content (image data, source code, compiled artifacts) and as the per-encoding hash of stored datum bytes. For semantic references between graph items, DatumID (`#`) and item references (`@`) carry the load.

## The object store

All content-addressed bytes live in a single object store, addressed by their ContentID. The store has a small surface:

- **Put** — write a byte sequence; receive its ContentID.
- **Get** — given a ContentID, fetch the bytes.
- **Has** — given a ContentID, check existence without fetching.

The store is content-agnostic — it doesn't interpret what's in the bytes. CG-CBOR datums, JPEG photos, audio buffers, compiled WebAssembly modules — all sit side by side, each identified by its hash, none privileged over another.

See [`storage.md`](storage.md) for the layered storage architecture that wraps this primitive (large-object thresholds, multi-backend routing, the index layers that sit above the object store).

## Verification

A byte sequence fetched by ContentID can always be verified:

1. Receive bytes.
2. Compute their hash with the algorithm named in the ContentID.
3. Compare with the ContentID's digest. Mismatch means corruption, tampering, or a wrong fetch.

This verification is automatic on every fetch. Bytes that fail verification are rejected. The verification chain extends upward through the layers:

- A frame body's ContentID verifies the bytes that decode to the body.
- The decoded body's DatumID verifies the datum's structure.
- The body's records verify who attests the datum.
- The records' signatures verify against signers' published keys.

Every layer rests on cryptographic verification; nothing relies on trust in a registry.

## Deduplication

Content addressing makes deduplication a mathematical consequence of the design, not a feature added on top. Two parties uploading the same image produce the same ContentID; the second upload silently confirms the existing one. Two items sharing a configuration block, two projects sharing a dependency artifact, two attestations referencing the same body — all share storage by virtue of sharing bytes.

The deduplication is *trustless* — no party has to be told that the content is the same; the hashes make the equivalence obvious. This compounds across the network. A blob downloaded once can be shared by any librarian that has it; downstream receivers verify by rehashing.

## Large content

Small content sits inline in the object store. Large content (multi-megabyte files, hour-long videos, archival datasets) is handled as a tree of chunks:

```
{@blob-root, [
  @TOTAL_SIZE → 1_337_421_056,
  @CHUNK_SIZE → 1_048_576,
  @CHUNK → ~<chunk-1-cid>,
  @CHUNK → ~<chunk-2-cid>,
  ...
]}
```

The root is a small datum that names the chunks; each chunk has its own ContentID. The blob's *identity* is the DatumID of the root. Readers fetch the root, then fetch chunks on demand. Verification is per-chunk: a corrupted chunk fails its own hash check and can be re-fetched without re-downloading the whole blob.

This mirrors IPFS's UnixFS approach and Git's blob/tree split. The differences: the chunk tree itself is a normal CG datum (not a special-cased structure), and large blobs interoperate with the rest of the system through the same reference primitives as any other content.

Streams (append-only logs, chat messages, sensor feeds) are a related but distinct shape — see [`streams.md`](streams.md).

## Privacy considerations

Content hashes are one-way functions. A ContentID reveals nothing about the bytes it identifies — you cannot recover content from its hash. But:

- **Possessing a ContentID is evidence the content was once known.** Two parties holding the same ContentID can prove they're talking about the same bytes without revealing what those bytes are.
- **ContentIDs are stable.** If you publish content and later regret it, the ContentID remains a working address for anyone who fetched it before deletion.
- **Confirmation attacks are possible.** An adversary who suspects you have specific content can ask if you have its ContentID; a "yes" confirms the suspicion.

For sensitive content:

- **Encrypt before hashing.** The ContentID of the ciphertext reveals only that some encrypted byte sequence exists; the plaintext stays unaddressable until decryption. Different encryptions of the same plaintext produce different ContentIDs.
- **Use access control.** Item-level and frame-level access policies determine who can fetch by a given ContentID, even if they hold it.
- **Don't share ContentIDs of private content.** A ContentID functions as a capability for anyone who has the bytes; treat it as such.

The encryption layer ([`encryption.md`](encryption.md)) wraps these patterns into structured envelopes.

## Content on the network

Content addressing makes Common Graph naturally peer-to-peer-friendly. A ContentID is a universal cache key — any librarian that has the content can serve it; the receiver verifies integrity by rehashing. Content flows along trust and interest paths through the social graph; popular content replicates organically; rare content is fetched on demand.

The detailed routing model, replication strategy, and gossip patterns live in [`network.md`](network.md).

## Relations

- [`datum.md`](datum.md) — datums and their structural identity.
- [`canonical.md`](canonical.md) — the structural walker that produces DatumIDs (the encoding-agnostic counterpart to ContentID).
- [`ref-scheme.md`](ref-scheme.md) — the `~` reference prefix and its byte layout.
- [`cg-cbor.md`](cg-cbor.md) — how CG-CBOR bytes get a ContentID under one specific encoding.
- [`storage.md`](storage.md) — the object store, backends, and the index layers above it.
- [`streams.md`](streams.md) — append-only stream content as a related but distinct shape.
- [`network.md`](network.md) — content replication and discovery across librarians.
- [`encryption.md`](encryption.md) — encrypting bytes before hashing for sensitive content.
