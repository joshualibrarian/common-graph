# Manifests

A **Manifest** is a signed declaration of everything in a specific version of an Item. It binds identity, content, and authority together — the "commit object" of Common Graph.

A Manifest is a runtime container around a body Datum (with archetype as head reference) and zero or more record Datums (the signed attestations). It uses the same unified Datum primitive as frames; see `datum.md` for the structural foundation.

## Structure

```
Manifest {
    body:     Body Datum       # The version's content — head ref is the archetype IID
    records:  [Record Datum]   # Zero or more signed attestations of the body
}
```

The body Datum's head reference points at the item's archetype (a sememe IID, e.g., `cg.archetype:item` or a more specific archetype like `cg.archetype:chess-game`). The bindings on the body carry the version's content:

```
Body Datum (manifest body) {
    head-reference: @<archetype-IID>
    bindings: [
        THEME    → item-IID            # which item this is a version of
        FOLLOWS  → previous-VID         # zero or more parents
        ENDORSES → [body-CID, ...]      # the endorsed frame bodies
        ...                             # additional archetype-specific bindings
    ]
}
```

Common bindings:

| Role | Purpose |
|------|---------|
| `THEME` | The item's IID — what this manifest is a version of |
| `FOLLOWS` | Parent VIDs (zero for inception, multiple for merges) |
| `ENDORSES` | Body CIDs of the frames this version endorses, with optional record CID and mounts |
| Archetype-specific roles | Whatever the archetype's EXPECTS declares |

## Version ID (VID)

The **VID** is the body Datum's CID — the hash of the body's encoded form. Identical version content produces identical VIDs.

Signatures are independent of the VID. The same body content produces the same VID regardless of who attests it. Multiple signers each produce their own record Datum pointing at the same VID via `#<VID>` head references.

## Records (Signing)

A manifest is signed by creating a record Datum that points at the manifest body's VID:

```
Record Datum (manifest record) {
    head-reference: #<VID>
    bindings: [
        AGENT:[SIGNER] → signer's-public-key (multikey-formatted)
        TIME:[SIGNED]  → timestamp
        ...                # optional per-record CONFIG
    ]
    signature: <varsig-formatted bytes>
}
```

The signature signs over the record's encoded form excluding the signature itself. Multiple signers produce multiple records, all pointing at the same VID — clean multi-signer support without changing the body's identity.

## Encoding

Both body and record Datums use the standard Datum encoding (see `datum.md` and `cg-cbor.md`):

- Body Datum: 2-element CBOR array `[Tag-6(@<archetype-IID>), [bindings]]`
- Record Datum: 3-element CBOR array `[Tag-6(#<VID>), [bindings], signature]`

Field order within bindings is canonical (deterministic). The VID is a commitment to exact bytes, ensuring cross-platform consistency.

## Endorsements

Each body CID in the ENDORSES binding can carry additional information:

```
ENDORSES binding target = an endorsement structure {
    bodyHash:   ContentID       # which frame body (required)
    recordCid:  ContentID?      # which record's per-signer config to honor (optional)
    mounts:     [Mount...]      # presentation layout
}
```

Most endorsements are just a body hash with mounts. The recordCid pin says "I endorse this body, and I specifically honor THIS record's per-signer config" — useful when multiple signers have signed the same body with different presentation choices.

## Version History

Manifests link via FOLLOWS bindings, forming a history:

```
V1 (FOLLOWS: [])
 └── V2 (FOLLOWS: [V1])
      └── V3 (FOLLOWS: [V2])
```

Multiple FOLLOWS bindings indicate a merge:

```
V2a (FOLLOWS: [V1])      V2b (FOLLOWS: [V1])
         └──── V3 (FOLLOWS: [V2a, V2b]) ────┘
```

This creates an immutable, content-addressed history DAG — like Git commits, but for Items.

## Branches (Channels)

Items can have named branches (channels) pointing to different version heads:

```
.item/
├── channels/
│   ├── main  -> ../manifests/<vid1>
│   └── draft -> ../manifests/<vid2>
```

The working tree's `head/base` symlink indicates which channel is checked out. See [Working Trees](working-tree.md) for how this maps to filesystem representation.

## Why Manifests Are Just Datums

A manifest is a body assertion ("this is the state of item X at this version") plus zero or more attestations ("I, signer Y, vouch for this state at time T"). That's exactly what any frame is, structurally. The conventions differ:

- A frame's body has a **predicate** as head reference; a manifest body has an **archetype**.
- A frame's body asserts content (TITLE, AUTHORED, etc.); a manifest body asserts version content (THEME, FOLLOWS, ENDORSES).

But the structural primitive is the same Datum. The same encoding rules apply. The same signing mechanism works. This unification eliminates the need for separate manifest-specific data structures and signing logic.

See [datum.md](datum.md) for the unified primitive and [frames.md](frames.md) for how frames use the same model.
