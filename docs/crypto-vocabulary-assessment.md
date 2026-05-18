# Cryptography Vocabulary Assessment (2026-05-17)

A survey of existing cryptography-related sememes (algorithms, identity, certs) before adding the new cert/trust pieces from [`identity-layer-survey.md`](identity-layer-survey.md).  Goal: make sure the existing organization is coherent enough to extend cleanly — and reorganize where it isn't.

## Current state

Two main files, ~1160 lines combined.

### `identity/AlgorithmVocabulary.java` (659 lines) — well-organized

Sections, top to bottom:

1. **Algorithm root archetype** — `cg.archetype:algorithm`
2. **17 metadata role sememes** — `cg.algorithm:{purpose, cose-id, varsig-code, multikey-code, key-factory, key-generator, signature-name, key-family, curve-name, key-bits, raw-key-bytes, sig-bytes, agreement-name, kdf-or-wrap, transformation, key-bytes, nonce-bytes, tag-bits}`
3. **3 key-family value sememes** — `cg.algorithm.family:{okp, ec, rsa}`
4. **4 signing algorithms** — Ed25519, ES256, ES256K, PS256
5. **2 key-agreement algorithms** — ECDH-ES-HKDF-256, RSA-OAEP-256
6. **3 AEAD content ciphers** — AES-GCM-128, AES-GCM-256, ChaCha20-Poly1305

Each algorithm carries its metadata as `@Seed.Property` bindings + matching `public static final` constants.  This part is genuinely good.

### `identity/IdentityVocabulary.java` (501 lines) — mixes concerns

Sections, top to bottom:

1. **3 key-track purposes** — `cg.purpose:{signing, encryption, key-agreement}` ✓ identity
2. **5 key-event predicates** — `cg.sememe:{inception, rotation, delegation, revocation, encrypt}` ✓✓✓✓✗ (last one is encryption-flow, not identity)
3. **4 key-material qualifiers** — `cg.value:multikey`, `cg.sememe:next`, `cg.value:keywrap`, `cg.value:ephemeral-pubkey` (only Next is identity-shaped; others are crypto-flow)
4. **4 reason sememes** — Compromise, Retirement, Fraud, Mistake ✓ identity (used by REVOCATION) but generic enough they belong in `CoreVocabulary`
5. **1 ciphersuite** — `cg.algo:x25519-aes256gcm-hkdf-sha256` ✗ algorithm, wrong namespace prefix
6. **1 role** — Delegator ✓ identity

## Issues found

### 1. Namespace inconsistency

Mix of `cg.algorithm:`, `cg.algorithm.family:`, `cg.algo:`, `cg.purpose:`, `cg.sememe:`, `cg.value:` across closely-related sememes.  Specifically:

- **`cg.algo:` is a typo / drift**.  Only used by `X25519_AES256GCM_HKDF`.  Everything else algorithm-related is `cg.algorithm:`.  Should be unified.
- **Qualifiers split between `cg.value:` and `cg.sememe:`** with no clear rule (`Multikey` is `cg.value`, `Next` is `cg.sememe`, both are binding qualifiers).  Looks accidental.

### 2. Concerns living in the wrong file

`IdentityVocabulary` contains several pieces that aren't really about identity:

- **`Encrypt` predicate** — concerns "wrapped DEK", "ephemeral X25519 pubkey", "cipher bytes".  Per-event encryption flow, not identity.
- **`Multikey`, `Keywrap`, `EphemeralPubkey` qualifiers** — crypto-data qualifiers used by `Encrypt` bodies.  They reference key material but they're flow concerns.
- **`X25519_AES256GCM_HKDF` ciphersuite** — algorithm bundling, belongs with other algorithms.

The author already flagged this in the file's docstring ("Migration into this file is a separate future step if/when that scoping decision is revisited").  Time to act on it.

### 3. Missing pieces for cert work

To add the cert sememe per [`identity-layer-survey.md`](identity-layer-survey.md), we need at least:

- **Hash algorithm sememes** — SHA-256, SHA-384, SHA-512.  Used as the digest component of cert signature algorithms ("RSA-PKCS1-SHA256", "ECDSA-SHA384", etc.).  None exist today.
- **Optional: ASN.1 OID metadata role** — `cg.algorithm:asn1-oid` would let cert ingestion code map cert signature OIDs to our algorithm sememes.  Without it, the cert importer needs a hard-coded mapping table.
- **Cert archetype + bindings** — the new sememe, plus its supporting role sememes (subject-pubkey, subject-name, issuer-cert-ref, validity-from, validity-until, signature-algorithm, serial-number, subject-alt-names).

### 4. Algorithm coverage gaps

Beyond hash algos, for the TLS/cert work we may want:

- **Ed25519ph** — pre-hashed Ed25519 variant.  Some cert/JWT contexts require it.
- **ML-DSA / ML-KEM** — post-quantum signing + KEM.  Long-horizon; not blocking, but worth flagging.

## Proposed reorganization

The minimum-pain split that produces coherent files:

### File: `identity/AlgorithmVocabulary.java` — algorithms only

Add:
- All current contents stay.
- Move `X25519_AES256GCM_HKDF` here under a new "Ciphersuites" section.  Rename key to `cg.algorithm:x25519-aes256gcm-hkdf-sha256` (fix the `cg.algo:` drift).
- Add **Hash algorithms** section: SHA-256, SHA-384, SHA-512.  Properties: `cose-id`, JCA `MessageDigest` name (probably a new metadata role `cg.algorithm:digest-name`).
- Add **`asn1-oid` metadata role**: optional ASN.1 OID for cert interop.  Populate on Ed25519, ES256, RSA variants, hash algos.

Net change: +~100 lines.  Existing structure preserved.

### File: `identity/IdentityVocabulary.java` — identity-only

Keep:
- Purposes (Signing, Encryption, KeyAgreement)
- Identity event predicates (Inception, Rotation, Delegation, Revocation)
- Identity-shaped qualifier: `Next` (pre-rotation commitment)
- Delegator role
- Witness (when added)

Move out:
- `Encrypt` predicate → `identity/EncryptionVocabulary.java` (new file)
- `Multikey`, `Keywrap`, `EphemeralPubkey` qualifiers → `identity/EncryptionVocabulary.java`
- `X25519_AES256GCM_HKDF` → `AlgorithmVocabulary` (above)
- Reason sememes (Compromise, Retirement, Fraud, Mistake) → `CoreVocabulary` (the author flagged this; they're generic-purpose, not identity-specific)

Net change: ~501 → ~200 lines.  Internal coherence: every entry is now genuinely identity-related.

### File: `identity/EncryptionVocabulary.java` — new, encryption-flow concerns

Contents:
- `Encrypt` predicate (moved from IdentityVocabulary)
- `Multikey`, `Keywrap`, `EphemeralPubkey` qualifiers (moved from IdentityVocabulary)
- Future: any encryption-flow sememes (e.g., per-recipient KEM wrap variations)

Net change: ~250 lines new (from moved content).

### File: `identity/CertVocabulary.java` — new, cert sememe and roles

Contents:
- `Cert` archetype — cert-as-record sememe
- Cert-binding role sememes: `subject-pubkey`, `subject-name`, `subject-alt-names`, `issuer-cert-ref`, `validity-from`, `validity-until`, `signature-algorithm`, `serial-number`
- Trust assertion sememe: `TrustedIdentity` (anchors a pubkey/IID as trusted)
- Optionally: `cert-chain-of` qualifier for grouping certs into chains

Net change: ~300 lines new.

### Naming standardization

- Algorithm-related: always `cg.algorithm:*` (or `cg.algorithm.family:*` for families).  Fix the one `cg.algo:` drift.
- Identity-related: stay `cg.purpose:*`, `cg.sememe:*` (event predicates), `cg.sememe:*` (reason sememes if they stay).
- Encryption-flow qualifiers: `cg.encryption:*` (new prefix; cleaner than the current `cg.value:` / `cg.sememe:` mix).  Migrate `Multikey → cg.encryption:multikey`, `Keywrap → cg.encryption:keywrap`, etc.
- Cert-related: `cg.cert:*` for the archetype + roles.

## Estimated effort

- Hash-algorithm + ciphersuite additions to AlgorithmVocabulary: ~1 hour
- Split IdentityVocabulary → IdentityVocabulary + EncryptionVocabulary + (move pieces to CoreVocabulary): ~2 hours including all caller updates
- Cert sememe (CertVocabulary): ~2 hours including the sememe + role declarations
- Naming standardization (the `cg.algo:` → `cg.algorithm:` fix + qualifier prefix migration): ~1 hour with `grep` + `sed`
- Tests / build green: ~1 hour

Total: roughly half a day.  The split is mostly mechanical because IdentityVocabulary's mixed content is already cleanly separable.

## Recommendation

Do the reorganization first, then the cert sememe.  Two reasons:

1. The mixed concerns in IdentityVocabulary make the cert work harder to land cleanly — every new role I'd add would force a decision about which file it goes in.  Better to settle the partitioning before adding more.
2. The naming drift (`cg.algo:` vs `cg.algorithm:`, mixed qualifier prefixes) will only get harder to fix as more callers reference the existing keys.  Fix it now while the surface is small.

Sequence:
1. **Pass 1 — split** — Move pieces out of IdentityVocabulary into the right homes.  No new sememes yet.  All callers updated.  Build green.
2. **Pass 2 — additions** — Hash algorithms in AlgorithmVocabulary.  Naming standardization (`cg.algo:` → `cg.algorithm:`, qualifier prefixes).  Build green.
3. **Pass 3 — cert sememe** — Add CertVocabulary with the cert archetype + role sememes.  No ingestion code yet; that's the next session.  Build green.

After Pass 3, the surface is ready for the JCA Provider work and the cert ingestion / generation / trust-resolver work from `identity-layer-survey.md`.
