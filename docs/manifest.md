# Manifests

A **Manifest** is a signed declaration of everything in a specific version of an Item. It's the "commit object" of the Common Graph — an immutable snapshot that binds identity, content, and authority together.

## Manifest Structure

```
Manifest {
    version: integer            # Format version (currently 1)
    iid: ItemID                 # Item identity
    parents: [VersionID]        # Parent version(s) — null for initial
    type: ItemID                # Item type reference
    state: ItemState            # All frames (the FrameTable)
    authorKey: SigningPublicKey  # Signer's public key (outside body hash)
    signature: Signing          # Cryptographic signature (outside body hash)
}
```

## Version ID (VID)

The **VID** is the hash of the manifest's **body** — the fields that make up the item's identity (version, iid, parents, type, state). This means:
- Identical content produces identical VIDs (deterministic)
- The VID is a commitment to exactly this version
- Signatures are **separate** from identity — the author key and signature are outside the body hash

This separation is intentional: the same logical version has the same VID regardless of who signed it or when.

## Body vs Record Scope

Manifest fields have two encoding scopes:

| Scope | Includes | Purpose |
|-------|----------|---------|
| **BODY** | version, iid, parents, type, state | Hashed to produce VID |
| **RECORD** | Everything (BODY + authorKey + signature) | Complete manifest for storage/transmission |

BODY is what you hash. RECORD is what you store. This lets you verify integrity (VID matches BODY hash) separately from verifying authority (signature matches VID).

## ItemState in the Manifest

The manifest's state is the unified container for all versioned content:

```
ItemState {
    frames: FrameTable     # All frame entries (handles, types, CIDs)
}
```

Each FrameEntry in the frame table records:

| Field | Description |
|-------|-------------|
| `handle` | Stable HID within this item |
| `type` | Frame type (ItemID) |
| `identity` | Does this frame contribute to item identity? |
| `snapshotCid` | Content hash (for snapshot frames) |
| `streamHeads` | Head hashes (for stream frames) |
| `mounts` | Presentation positions (path, surface, spatial) |

## Signing

Manifests are signed by a Signer (user, host, or device). The signature binds:
- The manifest body (hashed to produce VID)
- The signer's current public key
- A timestamp

The signature structure follows the [Trust](trust.md) model:

```
Signing {
    targetId: HashID            # The VID being signed
    targetBodyHash: bytes       # Hash of the BODY bytes
    signatures: [Sig]           # One or more signature records
}
```

## Canonical Encoding

Manifests use **canonical CG-CBOR** encoding — deterministic byte ordering that ensures:
- Same logical content → same bytes → same hash
- Cross-platform consistency (any implementation produces identical encoding)
- Verifiable integrity

Field order is explicit and fixed (see [CG-CBOR](cg-cbor.md) for the encoding rules). This determinism is what makes content addressing work: the VID is a commitment to exact bytes.

## Version History

Manifests link via `parents`, forming a history:

```
V1 (parents: [])
 └── V2 (parents: [V1])
      └── V3 (parents: [V2])
```

Multiple parents indicate a merge:

```
V2a (parents: [V1])      V2b (parents: [V1])
         └──── V3 (parents: [V2a, V2b]) ────┘
```

This creates an immutable, content-addressed history DAG — like Git commits, but for Items.

## Branches (Channels)

Items can have named branches (channels) pointing to different version heads:

```
.item/
├── channels/
│   ├── main -> ../manifests/<vid1>
│   └── draft -> ../manifests/<vid2>
```

The working tree's `head/base` symlink indicates which channel is checked out. See [Working Trees](working-tree.md) for how this maps to filesystem representation.
