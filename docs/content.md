# Content Addressing

Common Graph uses **content addressing** — content is identified by its cryptographic hash, ensuring integrity and enabling deduplication. This approach descends from Merkle's hash trees (see [references/Merkle 1979](references/Merkle%201979%20-%20Secrecy%20Authentication%20and%20Public%20Key%20Systems.pdf), [references/Merkle 1988](references/Merkle%201988%20-%20A%20Digital%20Signature%20Based%20on%20Conventional%20Encryption.pdf)) and is shared by modern systems like IPFS (see [references/Benet 2014](references/Benet%202014%20-%20IPFS%20Content%20Addressed%20Versioned%20P2P%20File%20System.pdf)).

## Content ID (CID)

A **CID** (Content ID) is the hash of content bytes:

```
CID = multihash(SHA2-256, content_bytes)
```

Properties:
- **Deterministic** — Same content = same CID, always
- **Tamper-evident** — Any change produces a different CID
- **Self-verifying** — Fetch content, hash it, compare to CID

CIDs use the [multihash](https://multiformats.io/multihash/) format: a self-describing hash that encodes both the algorithm and the digest. This future-proofs the system — if SHA-256 is ever deprecated, new content can use a different algorithm without breaking existing references.

## Block Storage

Content blocks are stored and retrieved by CID. The store is content-agnostic — it doesn't interpret what's in the bytes:

```
put(bytes) → CID          # Store content, return its hash
get(CID) → bytes          # Retrieve by hash
exists(CID) → boolean     # Check existence
```

See [Library](library.md) for the storage backends (RocksDB, MapDB, in-memory).

## Encoding

Content is encoded through **component types**. Different types encode differently:
- Plain text → UTF-8 bytes
- Structured data → Canonical CG-CBOR
- Binary → Raw bytes
- Quantities → CG-CBOR with `[mantissa, exponent, unit]`

The encoding is deterministic: same logical value → same bytes → same CID. This is enforced by the canonical encoding rules in [CG-CBOR](cg-cbor.md).

## Selectors

**Selectors** address fragments within content:

```
iid:<hex>\<cid>[selector]
```

| Selector | Format | Example |
|----------|--------|---------|
| Byte span | `[start-end]` | `[120-180]` — bytes 120-179 |
| JSON path (future) | `[$.path]` | `[$.users[0].name]` |
| Line/column (future) | `[line:col-line:col]` | `[10:5-15:20]` |
| Time range (future) | `[t0-t1]` | `[00:30-01:45]` |

Selectors are type-specific — the component type determines which selectors are valid and how they're interpreted. See [Syntax](syntax.md) for the full reference syntax.

## Large Content

Small content (< 256 KB) is stored directly in the database. Large content is stored on the filesystem:

```
store.rocks/           # Small blocks (database)
content.files/         # Large blocks (filesystem)
    <cid-hex>          # Raw bytes
```

### Chunking (Future)

Very large content (multi-gigabyte files) will be chunked:

```
BlobRoot {
    totalSize: integer
    chunkSize: integer
    chunkCids: [ContentID]
}
```

The CID of the file is the CID of the BlobRoot. Chunks can be fetched and verified independently. This is similar to how [IPFS](https://ipfs.tech/) handles large files via UnixFS.

## Deduplication

Content addressing enables automatic deduplication:
- Same photo uploaded twice → one copy stored
- Same configuration across items → shared block
- Same dependency across projects → single storage

Deduplication is effortless and trustless — it's a mathematical consequence of content addressing, not a policy decision.

## Verification

Any content can be verified at any time:

```
1. Fetch bytes by CID
2. Recompute hash(bytes)
3. Compare with CID — must match exactly
```

This happens automatically on fetch. Corrupted or tampered content fails verification. The chain extends upward:

```
Content bytes → CID (verified by hash)
    → ComponentEntry → Manifest → VID (verified by hash)
        → Signature (verified by public key)
```

See [Trust](trust.md) for the full verification chain.

## Object Store

All content-addressed data — frame bodies, frame records, manifests, content blobs — lives in a single object store. See [Storage Architecture](storage.md) for the unified storage design.

## Privacy Considerations

Content hashes reveal nothing about content (one-way function), but:
- Possessing a CID proves you know the content exists
- CIDs can be used to check if someone has specific content (confirmation attack)

For sensitive content:
- Encrypt before hashing (produces a different CID each encryption)
- Use access control at the item/component level
- Don't share CIDs of private content

## Content on the Network

Content addressing makes CG naturally suited to peer-to-peer distribution. A CID is a universal cache key — any Librarian that has the content can serve it, and the requester can verify integrity by re-hashing. Content replicates along interest paths through the social graph. See [Network Architecture](network.md) for how content flows between Librarians.

## References

**External resources:**
- [IPFS](https://ipfs.tech/) — Content-addressed block storage with P2P distribution
- [Git](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects) — Content-addressed objects for version control
- [Multihash](https://multiformats.io/multihash/) — Self-describing hash format
- [DAG-CBOR](https://ipld.io/specs/codecs/dag-cbor/spec/) — Content-addressed CBOR (IPLD)

**Academic foundations:**
- [Merkle 1979 — Secrecy, Authentication, and Public Key Systems](references/Merkle%201979%20-%20Secrecy%20Authentication%20and%20Public%20Key%20Systems.pdf) — Introduces Merkle trees
- [Merkle 1988 — Digital Signature Based on Conventional Encryption](references/Merkle%201988%20-%20A%20Digital%20Signature%20Based%20on%20Conventional%20Encryption.pdf) — Compact Merkle tree presentation
- [Benet 2014 — IPFS](references/Benet%202014%20-%20IPFS%20Content%20Addressed%20Versioned%20P2P%20File%20System.pdf) — Content-addressed storage with Merkle DAGs
