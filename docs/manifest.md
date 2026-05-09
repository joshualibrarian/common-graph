# Manifests

A **Manifest** is a signed declaration of everything in a specific version of an Item. It frames identity, content, and authority together — the "commit object" of Common Graph.

A Manifest is a runtime container around a body Datum (with archetype as head reference) and zero or more record Datums (the signed attestations). It uses the same unified Datum primitive as frames; see `datum.md` for the structural foundation.

## Structure

A Manifest is a **serialized wrapper** that packages a Datum with the item's identity.  Unlike a Frame (which is just a runtime aggregate), a Manifest is itself a stored, content-addressed structure with its own CID — the **VID** (Version ID).

```
Manifest (serialized, content-addressed):
    iid:      IID              # the item's identity (structural anchor)
    body:     Body Datum       # the version's content
    records:  [Record Datum]   # zero or more signed attestations of the body
}

Manifest CID = VID
```

Frames and Manifests differ in nature:

- **Frame** is a runtime aggregate.  Body and records are stored independently (each by its own CID); the Frame is just the in-memory grouping when you fetch them together.  Frames are not serialized as a single object.
- **Manifest** is a serialized wrapper.  It IS a stored object with its own VID.  It wraps a Datum and packages the IID at the wrapper level.

The IID lives on the wrapper, not on the Datum inside.  The Datum itself is just `head reference + bindings` — clean, uniform with frame bodies.  The wrapper provides the long-lived item identity that successive versions share.

The body Datum inside a Manifest is a normal Datum — head reference + bindings — with no special slots:

```
Manifest's body Datum {
    head-reference: @<archetype-IID>     # what kind of item — the IS_A
    bindings: [
        FOLLOWS         → previous-VID                # zero or more parents
        ENDORSES        → [body-CID, ...]             # endorsed frame bodies
        IMPLEMENTATION  → @<impl-item>[#<vid>]        # which implementation created this version
        CONFIG:[QUERY]  → strategy-item               # query strategy override
        CONFIG:[RETENTION] → policy                   # retention policy
        ...                                           # other archetype-specific bindings
    ]
}
```

### Why the IID is on the container, not the Datum

The IID is the item's identity, not data describing the manifest body.  Multiple versions of an item share the same IID; only their body CIDs (the VIDs) differ.  The IID is what makes successive manifests "versions of the same item" rather than separate signed things.

If the IID lived as a binding, changing it would produce a different VID — but nothing structural would distinguish "next version of item X" from "different item Y entirely."  Placing the IID on the Manifest container ensures that a manifest is always anchored to a specific item; forking into a new item is an explicit, structural action.

The Datum primitive itself stays clean — head reference + bindings, optionally + signature.  The container handles the long-lived identity that makes a manifest specifically a manifest.

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
