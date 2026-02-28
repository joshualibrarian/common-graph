# Authentication

Authentication in Common Graph answers one question: **"Is this key valid for this identity?"** It's the objective, cryptographic foundation that trust is built on. Authentication is binary — either the signature verifies or it doesn't. Trust (see [trust.md](trust.md)) is subjective — built from relations, computed locally, different for every user.

This document covers keys, signatures, and identity verification. For the trust model, moderation, and the trust matrix, see [Trust](trust.md).

## The Separation

Two concerns that must stay separate:

| Concern | Where It Lives |
|---------|----------------|
| **Graph truth / ownership** | Public keys, authorization, identity, history — in the graph |
| **Machine custody** | Private keys, how they're unlocked, how signing happens — local runtime |

**Public keys** are content blocks in the graph — fetchable, verifiable, shareable.
**Private keys** stay local, encrypted at rest, never in content stores.

Common Graph uses Ed25519 signatures (see [references/Bernstein et al 2012](references/Bernstein%20et%20al%202012%20-%20High-Speed%20High-Security%20Signatures%20Ed25519.pdf)) — chosen for their speed, small key/signature sizes, and resistance to side-channel attacks.

## Signers

Every entity that can sign (users, hosts, devices) is a **Signer** — an Item with:

- **Vault** — Local-only private key storage (never synced — see [Components](components.md))
- **KeyLog** — Append-only history of key events (add, revoke, rotate)
- **CertLog** — Certificates issued by this signer
- **Public key** — Current signing public key (derived from Vault)

Signers sign manifests and relations. The signature binds the signer's current key to the content being signed.

## Key Model

### Public Keys

Public keys are immutable content blocks:

```
SigningPublicKey {
    algorithm: Algorithm    # Ed25519, ES256, PS256
    spki: bytes             # X.509 SubjectPublicKeyInfo encoding
    owner: ItemID           # The signer item this key belongs to
    keyId: bytes            # Hash(SPKI) for quick lookup
}
```

Keys are tracked via **KeyLog** — an append-only stream component:
- `ADD` — Key is now valid for signing
- `REVOKE` — Key is no longer valid
- `ROTATE` — Old key signs transition to new key

### Private Keys

Private keys:
- Never part of the graph
- Stored locally on disk, encrypted at rest
- Loaded/unlocked by the Librarian runtime
- Never transmitted (keys generate locally)

## Device-Centric Identity

1. **Devices generate keys** — Your laptop creates its own keypair
2. **Principals authorize devices** — A user (Principal) adds device keys to their KeyLog
3. **No key copying** — Private keys never leave their origin device
4. **Clean revocation** — Removing a device key from KeyLog = revoked

This aligns with modern security practice (SSH, Signal) while avoiding account-first models, cloud key escrow, and key import rituals.

## Librarian Bootstrap

1. **Librarian is born** — Creates a new graph root
2. **Device key generated** — Librarian creates its own Ed25519 keypair
3. **Librarian can act** — Signs its own internal operations
4. **Principal arrives later** — User claims the device by adding its public key to their KeyLog

Until claimed, a Librarian is an "unclaimed device" that can still function locally.

## Signatures

A signature binds:

```
Sig {
    algorithm: Algorithm         # COSE algorithm ID (e.g., -8 for Ed25519)
    keyId: bytes                 # Hash(SPKI) — which key signed
    roleId: ItemID?              # Optional role sememe
    aad: bytes?                  # Additional authenticated data
    claimedAtEpochMillis: integer # When signed (epoch milliseconds)
    signature: bytes             # The actual signature bytes
}
```

To verify:
1. Find the signer's KeyLog
2. Locate the key by `keyId`
3. Verify the key was valid at `claimedAtEpochMillis`
4. Check the cryptographic signature

## Transparency Logs (Optional)

For high-stakes operations, cert CIDs can be included in a transparency log:

- Relations carry inclusion proofs
- Policy can require "logged" for certain operations
- Provides public auditability

## PGP Parallels

Common Graph matches the spirit of PGP's web-of-trust while avoiding its baggage:

- **Multiple introducers**: many `certifiedBy` edges on a key
- **Local policy**: a TrustPolicy item declares roots, roles, thresholds, decay, bonuses
- **Path building**: graph walk issuer → subject via introducers
- **No monolithic packet format**: keys and certs are clean CBOR bodies with deterministic CIDs
- **Cleaner identity**: relations like `(key) → atDomain → "alice@example.org"` vs PGP's free-text UIDs
- **Separate KeyCerts as Items**: not embedded in key blobs
- **Per-issuer introducer rules**: depth limits, scopes, time-decay in policy

See [Trust](trust.md) for how authentication feeds into the trust matrix and content moderation.
