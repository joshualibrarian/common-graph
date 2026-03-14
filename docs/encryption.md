# Encryption Architecture

Encryption in Common Graph is **progressive** and **layered** -- it can be applied incrementally, per-frame, per-item, or per-stream-entry, without requiring an all-or-nothing commitment. Three independent encryption layers compose cleanly: content encryption (Tag 10 envelopes on frames), transport encryption (Noise-based protocol-level), and at-rest encryption (library-level). Stream-based components (chat, collaborative documents) use the full Signal Double Ratchet protocol for forward secrecy and break-in recovery.

This document covers the complete encryption architecture: the envelope format (Tag 10), key management, content addressing interactions, access control and encryption policy, transport encryption, the Double Ratchet protocol for streams, group chat via Sender Keys, at-rest encryption, and integration with the trust model.

---

## Design Principles

**Progressive.** Any frame on any item can be encrypted independently. An item can have a mix of cleartext and encrypted frames. Encryption can be added to an existing frame without changing the item's structure -- only the frame's content block changes. A stream can transition from cleartext to encrypted mid-history.

**Composable with signing.** Encryption and signing are orthogonal operations that compose cleanly. A frame can be: unsigned cleartext, signed cleartext, unsigned ciphertext, or signed ciphertext. The signature covers the manifest (which references the content by CID), not the plaintext directly. This means signatures survive re-encryption to different recipients without invalidation.

**Local-first.** Encryption/decryption happens locally in the Vault. Private keys (including decryption keys) never leave the device. The Vault API gains `decrypt` and `deriveSharedSecret` operations alongside the existing `sign`.

**Content-addressing preserved.** Ciphertext is content-addressed like any other block -- `CID = hash(ciphertext_bytes)`. The CID of encrypted content is different from the CID of the plaintext. The mapping between plaintext CID and ciphertext CID is maintained in a local-only index (never synced, since it reveals what you can decrypt). The encrypted envelope carries the plaintext CID inside, recoverable only by authorized decryptors.

**Key separation.** Signing keys (Ed25519) and encryption keys (X25519) are separate. This follows cryptographic best practice -- different keys for different purposes, even though Ed25519 and X25519 share the same curve (Curve25519). The existing `Purpose` enum already has `SIGN`, `ENCRYPT`, and `AUTHENTICATE` -- encryption keys are registered in the KeyLog with `Purpose.ENCRYPT`.

---

## Key Infrastructure

### Encryption Keys

Encryption uses X25519 (Curve25519 Diffie-Hellman) for key agreement. This parallels how signing uses Ed25519.

```
EncryptionPublicKey extends GraphPublicKey {
    algorithm: Algorithm.KeyMgmt    // ECDH_ES_HKDF_256
    spki: bytes                      // X25519 SubjectPublicKeyInfo (DER)
    owner: ItemID                    // The signer item this key belongs to
    keyId: bytes                     // Hash(SPKI) for lookup
}
```

`EncryptionPublicKey` is the encryption counterpart to `SigningPublicKey`. Both extend `GraphPublicKey`, sharing the same CBOR encoding, key ID computation, and owner tracking. The only difference is the algorithm field: `Algorithm.KeyMgmt.ECDH_ES_HKDF_256` vs `Algorithm.Sign.ED25519`.

### KeyLog Integration

The existing `KeyLog` already supports multi-purpose keys via `Purpose.ENCRYPT`. Encryption keys are published to the KeyLog alongside signing keys:

```
KeyLog ops:
  AddKey(EncryptionPublicKey)             // Add an encryption key
  SetCurrent(keyCid, ENCRYPT, true)       // Mark as current for encryption
  TombstoneKey(keyCid, reason)            // Revoke (compromised/retired)
```

A Signer's KeyLog thus tracks both signing and encryption key history. Query helpers parallel the existing `currentSigningKey()`:

```
keyLog.currentEncryptionKey()             // Current X25519 public key
keyLog.currentEncryptionKeys()            // All current encryption keys (multi-device)
```

### Vault Extensions

The `Vault` abstract class already has a `decrypt(alias, ciphertext)` stub. The full encryption API adds:

```
Vault (new abstract methods):
    generateKey(alias, KeyType.X25519)     // Generate X25519 keypair
    deriveSharedSecret(alias, peerSpki)    // ECDH key agreement (in-place)
    decrypt(alias, ciphertext)             // Decrypt with private key
```

All operations are sign-in-place / decrypt-in-place: the private key never leaves the Vault. For hardware-backed vaults (TPM, Secure Enclave, YubiKey), the ECDH computation happens entirely within the secure hardware.

### KeyType Extension

```
Vault.KeyType (new entry):
    X25519("X25519", null)    // No signature algorithm -- this is for key agreement
```

### Bootstrap: Signer Gets Encryption Keys

On first boot, alongside the Ed25519 signing key, the Signer generates an X25519 encryption key:

```
Signer.initializeKeys():
    vault.generateSigningKey()              // Ed25519 (existing)
    vault.generateKey("encryption", X25519) // X25519 (new)
    publishKeyToLog()                       // Publish both to KeyLog
```

Both keys are published to the KeyLog with appropriate purpose masks. External signers (referenced, no vault) have encryption public keys available from their KeyLog for encrypting TO them.

---

## Tag 10: CG-ENCRYPTED (Encrypted Envelope)

### Wire Format

Tag 10 wraps an encrypted payload with key-agreement metadata and per-recipient key material. It is a CBOR-tagged array:

```
Tag(10, [
    <header>,           // CBOR map: algorithm parameters
    <recipients>,       // CBOR array of recipient entries
    <ciphertext>        // CBOR byte string: AEAD output (ciphertext || tag)
])
```

This is a 3-element CBOR array tagged with CG tag 10.

### Header

```
header: {
    1: <kemAlg>,        // int: COSE KEM algorithm ID (-25 for ECDH-ES+HKDF-256)
    2: <aeadAlg>,       // int: COSE AEAD algorithm ID (3 for AES-GCM-256, 24 for ChaCha20-Poly1305)
    3: <nonce>,         // bytes(12): AEAD nonce
    4: <aad>,           // bytes: additional authenticated data (nullable)
    5: <plaintextCid>,  // bytes: CID of the original plaintext (for content-address recovery)
    6: <senderKid>,     // bytes: sha256(SPKI) of sender's signing key (nullable for anonymous)
    7: <senderSig>,     // bytes: sender signature over the header body (nullable for anonymous)
}
```

The `plaintextCid` is included inside the envelope (encrypted alongside the content, or authenticated via AAD) so that decryptors can recover the content-address mapping. More on this below.

### Recipient Entries

Each recipient entry provides enough information for that recipient to recover the Content Encryption Key (CEK):

```
recipient: {
    1: <kid>,           // bytes: recipient's encryption key ID (sha256(SPKI))
    2: <epk>,           // bytes: sender's ephemeral X25519 public key (SPKI), per-recipient
    3: <wrappedCek>,    // bytes: key-wrapped CEK (null for ECDH-ES direct)
}
```

### Encryption Modes

Two modes, matching `Algorithm.KeyMgmt`:

**ECDH-ES Direct (single recipient or per-recipient derivation):**
- Generate ephemeral X25519 keypair per recipient
- ECDH(ephemeral_private, recipient_public) -> shared_secret
- HKDF-SHA256(shared_secret, context_info) -> CEK
- AEAD-encrypt(CEK, nonce, aad, plaintext)
- Store ephemeral public key in recipient entry, no wrappedCek

**ECDH-ES + Key Wrap (multi-recipient efficiency):**
- Generate random CEK
- For each recipient: ECDH -> derived key -> AES-Key-Wrap(derived_key, CEK)
- AEAD-encrypt(CEK, nonce, aad, plaintext)
- Store ephemeral public key and wrapped CEK per recipient
- One AEAD encryption, N key wraps (much more efficient for large payloads with many recipients)

For single-recipient encryption, ECDH-ES Direct is preferred (no key-wrap overhead). For multi-recipient, ECDH-ES + Key Wrap avoids re-encrypting the payload N times.

### Sender Authentication

Encrypted envelopes can optionally include sender authentication:

- `senderKid` identifies who encrypted the content (by their signing key ID)
- `senderSig` is an Ed25519 signature over the envelope header body (everything except the signature itself)

This binds the encryption to a specific identity. Anonymous encryption (no sender identification) is supported by omitting these fields -- useful for anonymous drops, whistleblowing scenarios, or when the sender's identity should be protected.

### Relation to Existing EncryptedEnvelope

The existing `EncryptedEnvelope.java` in `crypt/` is a prototype that aligns with this design. The key changes:

1. Use Tag 10 CBOR wrapping (currently plain MAP canonization)
2. Separate sender signing from the envelope structure (the envelope is the Tag 10 wrapper; signing is Tag 8 wrapping the envelope if needed)
3. Add `plaintextCid` for content-address recovery
4. Rename to clarify the role: `EncryptedEnvelope` becomes the canonical Tag 10 implementation

---

## Content Addressing and Encryption

### The Dual-CID Problem

Content addressing fundamentally depends on deterministic hashing: same content -> same CID. Encryption breaks this because:

- Different encryption keys -> different ciphertext -> different CID
- Re-encrypting the same content to different recipients produces different CIDs
- Nonces ensure even the same key+content produces different ciphertext each time

This is by design -- if identical content always produced identical ciphertext, an attacker could confirm content identity by re-encrypting and comparing.

### The Solution: Plaintext CID Inside the Envelope

Every encrypted envelope carries the **plaintext CID** inside the authenticated (AEAD) payload or in the authenticated additional data (AAD). After decryption, the recipient can:

1. Decrypt to recover plaintext bytes
2. Hash the plaintext to verify `plaintextCid == hash(plaintext)`
3. Store the plaintext locally under its true CID
4. Maintain a local `ciphertextCID -> plaintextCID` mapping index

This means the manifest's `FrameEntry.payload.snapshotCid` can reference EITHER:
- The plaintext CID (when the content is stored cleartext)
- The ciphertext CID (when the content is stored encrypted)

Which one is used depends on the frame's encryption state, tracked in a new field on the entry.

### FrameEntry Encryption Metadata

```
FrameEntry (additions to EntryPayload):
    encryptedCid: ContentID?         // CID of the encrypted envelope (null = cleartext)
```

When `encryptedCid` is set:
- `snapshotCid` still holds the **plaintext** CID (for identity/VID purposes -- the item's version identity is based on semantic content, not encryption artifacts)
- `encryptedCid` holds the **ciphertext** CID (what's actually in the object store on disk)
- The object store contains the Tag 10 envelope at `encryptedCid`
- Decryption recovers the plaintext and verifies it matches `snapshotCid`

This design preserves a critical property: **the VID does not change when you re-encrypt content for different recipients.** The manifest body hashes over `snapshotCid` (plaintext identity), not `encryptedCid` (encryption artifact). Two Librarians with the same item content but different encryption envelopes produce the same VID.

### Encrypted Streams

For stream components (Log, Dag, ChatLog), encryption applies per-entry:

```
Stream entry (encrypted):
    Tag(10, [header, recipients, AEAD(entry_cbor)])
```

Each stream entry is independently encrypted. This supports:
- **Mixed encryption**: some entries cleartext, some encrypted
- **Recipient changes**: new entries can have different recipient lists (members join/leave)
- **Per-entry forward secrecy**: each entry can use fresh ephemeral keys
- **Selective disclosure**: reveal specific entries without revealing the whole stream

The stream's hash chain still works: each entry's CID (of the encrypted envelope) links to its parent's CID (also of an encrypted envelope). The chain is verifiable without decryption -- you can confirm structural integrity without reading content.

---

## Progressive Encryption

### What "Progressive" Means

Progressive encryption means encryption is not an inherent property of an item or frame -- it is a layer applied to content blocks. The same frame can transition between states:

```
State transitions:
    cleartext  --encrypt-->  encrypted
    encrypted  --re-encrypt--> encrypted (different recipients)
    encrypted  --decrypt+store--> cleartext (if policy allows)
```

### Per-Frame Granularity

An item with 5 frames might have:

```
Item "medical-record":
    frame "demographics"   -> cleartext      (name, DOB -- shared with admin)
    frame "insurance"      -> encrypted(insurer, patient)
    frame "diagnosis"      -> encrypted(doctor, patient)
    frame "treatment-plan" -> encrypted(doctor, patient, pharmacist)
    frame "billing"        -> encrypted(insurer, billing-dept)
```

Each frame independently controls who can read it. The manifest is signed cleartext (so anyone can verify it exists and see its structure), but individual frame content is encrypted to specific recipients.

### Encrypt After the Fact

An existing cleartext frame can be encrypted by:

1. Fetch plaintext bytes from store by `snapshotCid`
2. Build Tag 10 envelope for desired recipients
3. Store envelope, get `encryptedCid`
4. Update `FrameEntry` to record `encryptedCid`
5. Optionally delete the cleartext block from local store
6. Commit new manifest version (VID unchanged since `snapshotCid` stays the same)

This is a local operation -- the Librarian encrypts its own stored content. When syncing to peers, it sends the encrypted envelope instead of the plaintext block.

### Visibility Metadata

The manifest itself (the frame table listing handles, types, and CIDs) remains cleartext and signed. This means:

- Anyone can see that an item EXISTS and what TYPES of frames it has
- They can see frame handles ("medical-notes", "billing") and types
- They CANNOT read the content without the decryption key
- The signed manifest proves integrity even for frames you cannot read

This is intentional: it allows the social graph and discovery mechanisms to work (you can find items, know their type, see their structure) while protecting actual content. If even the existence of a frame should be hidden, the entire manifest can be wrapped in a Tag 10 envelope -- but this is a separate, more extreme measure.

---

## Access Control and Encryption Policy

### Two Separate but Related Concerns

Access control and encryption are **separate policy dimensions**, both living on the frame's `EntryConfig.policy`:

- **Access policy** (`PolicySet.AccessPolicy`) controls **distribution** — the Librarian decides who receives the bytes. A private frame (no READ rules) simply isn't replicated. This is trust-based: you trust the Librarian to enforce it.

- **Encryption policy** (`PolicySet.EncryptionPolicy`) controls **cryptographic protection** — the bytes are mathematically unreadable without the key, even if someone obtains them through a compromised Librarian or network sniffing.

Four combinations exist:

| Access | Encryption | Meaning |
|--------|-----------|---------|
| Shared | Cleartext | Public frame |
| Shared | Encrypted | E2E encrypted (replicated, only key holders read) |
| Private | Cleartext | Trust-based privacy (not shared, but bytes readable if leaked) |
| Private | Encrypted | Maximum protection |

### EncryptionPolicy on PolicySet

Each frame carries an `EncryptionPolicy` in its config facet (`EntryConfig.policy.encryption`):

```
EncryptionPolicy {
    enabled: boolean                  // Master switch
    encryptToReaders: boolean         // Derive recipients from AccessPolicy READ rules
    recipients: List<ItemID>          // Explicit recipients (when not using encryptToReaders)
    algorithm: String                 // AEAD algorithm override (null = AES-256-GCM)
}
```

Common patterns:

```java
// Encrypt to whoever has READ access (most common)
EncryptionPolicy.toReaders()

// Encrypt to specific principals
EncryptionPolicy.toRecipients(List.of(aliceId, bobId))

// No encryption
EncryptionPolicy.none()
```

The `encryptToReaders` shorthand covers the common case where encryption recipients = access policy readers. For the less common case (encrypt to backup services not in reader list, or encrypt to a subset), use explicit `recipients`.

### EncryptionContext: Commit-Time Resolution

During commit, encryption decisions are resolved through `EncryptionContext`, which bridges per-frame policies to the actual encryption operation:

1. **Explicit `EncryptionContext`** passed to `commit()` overrides per-frame policies (item-level encryption)
2. **Per-frame `EncryptionPolicy`** on `EntryConfig.policy` drives encryption when no explicit context is given
3. **Config carry-forward**: Frame configs (including encryption policy) survive across commit cycles

The `EncryptionContext.fromEncryptionPolicy()` factory creates a context from a per-frame policy with pre-resolved `EncryptionPublicKey` objects.

### Group Key Pattern

For items shared with many recipients (chat rooms, shared documents, team spaces), per-recipient encryption of every content block is wasteful. Instead, use a **group key**:

```
Group encryption:
    1. Generate a random symmetric group key (AES-256)
    2. Encrypt content with the group key (AEAD)
    3. Encrypt the group key to each member's encryption public key
    4. Wrap the group key to each member via EnvelopeOps.wrapKeyForRecipient()
    5. New content uses the same group key until rotation
```

This is the standard "key envelope" pattern: one AEAD encryption for content, N asymmetric encryptions for the key. Adding a member means wrapping the group key to their public key (one asymmetric operation). Removing a member means rotating the group key (see Key Rotation below).

### Member Lifecycle

When a new member is added:

```
1. Fetch their EncryptionPublicKey from their KeyLog
2. Wrap the current group key to their public key
3. Update the frame's EncryptionPolicy recipients
4. On next commit, the frame is encrypted to the updated recipient set
```

When a member is removed:

```
1. Generate a new group key
2. Re-wrap the new group key to all remaining members
3. Update the EncryptionPolicy recipients
4. All new content uses the new group key
5. Old content remains readable with the old key (forward access)
   OR old content is re-encrypted with the new key (full revocation)
```

The choice between forward-access and full-revocation is a policy decision on the item. Most chat rooms allow reading history (forward access). Highly sensitive items may require full re-encryption on member removal.

---

## Key Rotation and Forward Secrecy

### Periodic Key Rotation

Encryption keys should rotate periodically. The KeyLog already supports this pattern:

```
KeyLog:
    AddKey(encKey1)                              // epoch 0
    SetCurrent(encKey1, ENCRYPT, true)
    ... time passes ...
    AddKey(encKey2)                              // rotation
    SetCurrent(encKey2, ENCRYPT, true)
    SetCurrent(encKey1, ENCRYPT, false)          // old key still valid for decryption
```

Old encryption keys are NOT tombstoned on rotation (unlike compromised keys) -- they remain in the KeyLog so old content can still be decrypted. Only compromised keys are tombstoned, which signals that content encrypted to them should be treated as potentially exposed.

### Forward Secrecy via Ephemeral Keys (Snapshots)

For snapshot frames (non-stream content), per-frame forward secrecy is achieved through ephemeral X25519 keys:

```
For each encrypted snapshot frame:
    1. Generate ephemeral X25519 keypair
    2. ECDH(ephemeral_private, recipient_static_public) -> shared_secret
    3. HKDF(shared_secret) -> CEK
    4. AEAD-encrypt(CEK, nonce, content)
    5. Include ephemeral PUBLIC key in recipient entry
    6. DESTROY ephemeral PRIVATE key immediately
```

This is what Tag 10 envelopes already do. Because the ephemeral private key is destroyed after use, compromising the recipient's long-term key in the future cannot decrypt past messages.

---

## Double Ratchet Protocol (Streams)

For long-lived encrypted streams (chat, collaborative documents, any append-only log), Common Graph uses the full Signal Double Ratchet protocol. This provides:

- **Forward secrecy**: Compromising the current state doesn't reveal past messages
- **Break-in recovery**: After a compromise, new DH exchanges restore secrecy
- **Per-message keys**: Each stream entry has its own unique key, deleted after use

### X3DH: Initial Key Agreement

Before two parties can exchange ratcheted messages, they perform an Extended Triple Diffie-Hellman (X3DH) handshake to establish the initial shared secret.

**Key bundles published in the Signer's KeyLog:**

```
Pre-key bundle (published per-Signer):
    IK:  X25519 identity key (the Signer's long-term encryption key, already in KeyLog)
    SPK: X25519 signed pre-key (medium-term, rotated periodically, signed by Ed25519)
    OPK: X25519 one-time pre-keys (consumed on first use, replenished)
```

The signed pre-key (SPK) and one-time pre-keys (OPK) are published as frames on the Signer item, available to anyone who wants to initiate a ratcheted session. The SPK is signed by the Signer's Ed25519 key to prove authenticity.

**Handshake (initiator → responder):**

```
X3DH key agreement:
    Initiator knows: own IK_a, responder's IK_b, SPK_b, OPK_b (from KeyLog)

    1. Generate ephemeral key EK_a
    2. DH1 = ECDH(IK_a, SPK_b)          // identity → signed pre-key
    3. DH2 = ECDH(EK_a, IK_b)           // ephemeral → identity
    4. DH3 = ECDH(EK_a, SPK_b)          // ephemeral → signed pre-key
    5. DH4 = ECDH(EK_a, OPK_b)          // ephemeral → one-time pre-key (if available)
    6. SK  = HKDF(DH1 || DH2 || DH3 || DH4)  // initial shared secret

    Initiator sends: IK_a, EK_a, OPK_b_id (which OPK was used)
    Responder deletes: OPK_b (one-time, never reused)
```

The initial shared secret SK becomes the root key for the Double Ratchet.

**Integration with CG:**
- IK is the existing `EncryptionPublicKey` in the KeyLog (`Purpose.ENCRYPT`)
- SPK is a new frame on the Signer item (`handle="spk"`, rotated periodically)
- OPKs are a stream frame on the Signer item (`handle="opk"`, entries consumed and deleted)
- The X3DH initial message is sent as the first entry in the encrypted stream

### The Double Ratchet

After X3DH establishes the initial shared secret, the Double Ratchet derives per-message keys through two interlocking ratchets:

**1. DH Ratchet (asymmetric, per-turn):**

```
On each turn change (Alice → Bob, Bob → Alice):
    Sender generates new ratchet keypair: rk_new
    Performs ECDH(rk_new_private, peer_rk_public) → dh_output
    HKDF(root_key, dh_output) → new_root_key, new_chain_key
    Sends rk_new_public with the message
    Deletes rk_new_private after deriving chain key
```

The DH ratchet provides **break-in recovery**: even if an attacker compromises the current state, the next DH exchange produces a new shared secret they can't predict.

**2. Symmetric Ratchet (KDF chain, per-message):**

```
For each message within a turn:
    chain_key, message_key = HKDF(chain_key, constant)
    AEAD-encrypt(message_key, nonce, message)
    Delete message_key after use
    Advance chain_key (old chain_key deleted)
```

The symmetric ratchet provides **forward secrecy within a turn**: each message key is derived and immediately deleted, so compromising a later chain key doesn't reveal earlier messages.

### Ratchet State

```
RatchetState (per sender-recipient pair, local-only):
    rootKey:        bytes           // Current root key
    sendChainKey:   bytes           // Current sending chain key
    recvChainKey:   bytes           // Current receiving chain key
    sendRatchetKey: X25519 keypair  // Our current DH ratchet keypair
    recvRatchetPub: X25519 public   // Peer's current DH ratchet public key
    sendMsgNum:     int             // Messages sent in current chain
    recvMsgNum:     int             // Messages received in current chain
    prevChainLen:   int             // Length of previous sending chain
    skippedKeys:    Map<(ratchetPub, msgNum), messageKey>  // For out-of-order messages
```

**Critical: ratchet state is local-only.** It is stored in the Vault (or a local-only frame) and NEVER synced. Each device maintains its own ratchet state per conversation peer. Multi-device requires separate ratchet sessions per device (each device has its own identity key).

### Stream Entry Format (Encrypted)

Each encrypted stream entry carries the ratchet metadata alongside the ciphertext:

```
Encrypted stream entry:
    Tag(10, [
        {
            1: <kemAlg>,
            2: <aeadAlg>,
            3: <nonce>,
            5: <plaintextCid>,
            10: <senderRatchetPub>,     // Sender's current DH ratchet public key
            11: <previousChainLength>,  // Number of messages in previous sending chain
            12: <messageNumber>,        // Message number within current chain
        },
        [],                             // No per-recipient entries (key derived from ratchet)
        <ciphertext>
    ])
```

The recipient uses `senderRatchetPub` to detect DH ratchet steps, `previousChainLength` to know how many skipped keys to derive from the old chain, and `messageNumber` to derive the correct message key from the current chain.

### Out-of-Order Messages

Stream entries may arrive out of order (network delays, concurrent appends). The receiver handles this by:

1. If `senderRatchetPub` matches current: derive forward from `recvChainKey` to `messageNumber`, caching any skipped keys
2. If `senderRatchetPub` is new: perform DH ratchet step first, then derive from the new chain
3. If `senderRatchetPub` is OLD and we have a cached skipped key for that `(ratchetPub, msgNum)`: use the cached key
4. Skipped keys are held for a configurable window (default: 1000 messages) then discarded

### Group Chat: Sender Keys

For group streams (3+ participants), per-pair Double Ratchet is O(n^2) in state. Instead, group chat uses the **Sender Key** pattern (as in Signal's group messaging):

```
Sender Key protocol:
    1. Each group member maintains ONE sending chain (symmetric ratchet)
    2. On join, member distributes their Sender Key to all other members via 1:1 ratcheted channels
    3. Messages to the group are encrypted with the sender's chain key
    4. All recipients who have the Sender Key can decrypt
    5. On member removal: all remaining members rotate their Sender Keys and redistribute
```

Properties:
- **O(n) state** instead of O(n^2) -- each member has n-1 receiving chains (one per peer)
- **Forward secrecy per sender**: each sender's chain ratchets independently
- **Removal requires rotation**: removing a member means all remaining members must generate new Sender Keys (the removed member had everyone's old Sender Keys)
- **Sender Key distribution uses 1:1 ratcheted channels**: the Sender Key itself is encrypted end-to-end to each group member

**Integration with CG group items:**
- The Roster component tracks membership
- On `Roster.join`: new member receives all current Sender Keys via 1:1 ratcheted delivery
- On `Roster.leave`/`Roster.remove`: all remaining members rotate Sender Keys
- Sender Key distribution messages are standard encrypted stream entries on the 1:1 pair's ratchet
- The group stream itself stores entries encrypted with Sender Keys, not Tag 10 per-recipient envelopes

### Pre-Key Management

**Signed Pre-Keys (SPK):**
- Rotated on a schedule (e.g., weekly) or when the old one has been used enough times
- Old SPKs are kept briefly (for in-flight X3DH handshakes) then deleted
- Published as a snapshot frame on the Signer item, signed by Ed25519

**One-Time Pre-Keys (OPK):**
- Generated in batches, published as entries in a stream frame on the Signer
- Consumed (deleted) after a single X3DH handshake
- If no OPKs are available, X3DH proceeds without DH4 (slightly weaker forward secrecy on the first message, but still secure)
- Librarian replenishes OPKs when the pool drops below a threshold

---

## At-Rest Encryption

### Library-Level Encryption

The Library (local storage) can optionally encrypt all content at rest using a device-local key:

```
Library at-rest encryption:
    device_key = Vault.deriveKey("library-at-rest", HKDF)
    store.put(cid, AEAD-encrypt(device_key, nonce, cid_bytes, content))
    store.get(cid) -> AEAD-decrypt(device_key, nonce, cid_bytes, ciphertext)
```

This protects against physical device theft. The device key is derived from the Vault's master key, which is unlocked at runtime (by password, biometric, or hardware token). When the Librarian shuts down, the device key is zeroized from memory.

At-rest encryption is SEPARATE from item-level encryption. Even if at-rest encryption is enabled, content that is item-level encrypted remains double-encrypted (item-level envelope inside library-level encryption). This is fine -- the item-level encryption protects against authorized Librarian users who should not see certain content; the library-level encryption protects against unauthorized access to the storage medium.

### RocksDB Integration

For the RocksDB backend, at-rest encryption can use RocksDB's built-in encryption support (via the Env encryption layer) rather than encrypting at the application level. This is more efficient because RocksDB handles block-level encryption with proper key management.

For the SkipList (in-memory) backend, at-rest encryption is irrelevant (memory is volatile). For MapDB, application-level encryption wraps the serialized bytes.

---

## Transport Encryption (Protocol-Level)

Transport encryption protects **all traffic** between two Librarians, regardless of whether individual frames are content-encrypted. This is a separate layer from content encryption (Tag 10 envelopes) -- the two compose independently.

### Three Encryption Layers

```
┌──────────────────────────────────────────────────────────┐
│  Layer 3: Content Encryption (Tag 10 envelopes)          │
│  Per-frame. Protects content at rest and across relays.  │
│  Only recipients with the right key can read.            │
├──────────────────────────────────────────────────────────┤
│  Layer 2: Transport Encryption (protocol-level)          │
│  Per-connection. Protects ALL traffic between peers.     │
│  Session keys negotiated on connect, destroyed on close. │
├──────────────────────────────────────────────────────────┤
│  Layer 1: Wire (TCP, Unix socket, etc.)                  │
│  Physical transport. No inherent protection.             │
└──────────────────────────────────────────────────────────┘
```

Content-encrypted frames get **double-encrypted** on the wire (content envelope inside transport encryption). This is intentional -- defense in depth. A network observer sees only transport ciphertext. A compromised relay sees transport plaintext (protocol messages) but NOT content plaintext (still Tag 10 envelopes). Only the designated recipient can read the actual content.

### The Four Quadrants

| | Cleartext transport | Encrypted transport |
|---|---|---|
| **Cleartext frame** | Fully exposed (local/trusted only) | Transit-protected |
| **Encrypted frame** | Content-protected, metadata visible | Defense in depth |

### Noise-Based Handshake

Transport encryption uses a Noise protocol handshake (NK or XX pattern) with each Librarian's X25519 key:

```
Handshake (Noise_XX pattern):
    1. Initiator: e                          → ephemeral public key
    2. Responder: e, ee, s, es               → ephemeral + static keys, DH results
    3. Initiator: s, se                      → static key + DH result
    → Both derive symmetric session keys (send_key, recv_key)
```

Properties:
- **Mutual authentication**: Both peers prove they hold the private key for their published X25519 key
- **Forward secrecy**: Ephemeral keys are destroyed after handshake -- compromising long-term keys later doesn't reveal past sessions
- **Identity hiding**: Responder's identity is encrypted under the initiator's ephemeral key (protects against passive observers)

The static keys are the same X25519 keys from the Signer's KeyLog -- no separate transport key infrastructure needed.

### Session Keys

After handshake, all messages are encrypted with session keys:

```
Session encryption:
    send: AEAD(send_key, nonce++, message)
    recv: AEAD(recv_key, nonce++, message)
```

Session keys are:
- **Ephemeral**: Derived from the handshake, never stored persistently
- **Directional**: Separate keys for send and receive
- **Ratcheted**: Optionally re-keyed periodically within a long session

### Optional Transport Encryption

Transport encryption is **optional per connection**:
- **Local connections** (Unix sockets between Librarians on the same machine): may skip transport encryption if the OS provides sufficient isolation
- **Trusted networks**: can be disabled by policy (e.g., internal cluster)
- **Public networks**: always enabled (default)

The decision is per-connection policy, negotiated during handshake. A Librarian can refuse unencrypted connections via its own policy.

### Content-Encrypted Frame Transit

When syncing content-encrypted frames (Tag 10 envelopes), the Librarian sends the envelope as-is inside the transport channel:

```
Transit of encrypted content:
    Sender Librarian                 Relay Peer                 Recipient Librarian
         |                               |                              |
         |== transport-encrypted =======>|                              |
         |   Delivery(cid, Tag10(...))   |                              |
         |                               |== transport-encrypted ======>|
         |                               |   Delivery(cid, Tag10(...))  |
         |                               |                              |
         | relay sees protocol messages  | relay sees Tag10 envelopes  | decrypts Tag10
         | but no content               | but not content              | with Vault
```

- Relay peers can see that a Delivery message carries a Tag 10 envelope (after decrypting the transport layer), but cannot read the content
- Relay peers CAN verify manifest signatures and route based on metadata
- The relay simply forwards the Tag 10 envelope opaquely

### Envelope Forwarding

When a Librarian syncs an encrypted frame to a peer:

- If the peer IS a recipient (their key ID is in the recipient list): send the envelope as-is
- If the peer is NOT a recipient but should have access: the sender must re-encrypt for the new recipient (add a recipient entry or create a new envelope)
- If the peer is a relay only: send the envelope as-is; they can forward without reading

Encryption decisions happen at the sender, not the relay. The sender's policy determines who gets the content and in what form.

---

## Trust Model Integration

### Policy-Driven Encryption

The existing `PolicySet` on items and frames gains encryption-related predicates:

```
PolicySet (new predicates):
    REQUIRE_ENCRYPTION           // All frames must be encrypted
    REQUIRE_SENDER_AUTH          // Encrypted envelopes must identify sender
    REQUIRE_FORWARD_SECRECY      // Must use ephemeral keys
    ENCRYPTION_ALGORITHM         // Minimum algorithm strength
    MAX_RECIPIENTS               // Limit recipient count (for secrecy)
    ALLOW_ANONYMOUS_ENCRYPT      // Permit envelopes without sender identification
```

Policy is evaluated at commit time. If a frame violates its item's encryption policy, the commit is rejected.

### Trust-Based Access Decisions

The trust matrix feeds into encryption decisions:

- **Who to encrypt to**: Determined by the frame's `EncryptionPolicy` (explicit recipients or derived from `AccessPolicy` READ rules)
- **Whether to accept encrypted content**: Trust policy on the receiving side
- **Key verification**: The recipient verifies the sender's encryption key via their KeyLog and trust path
- **Unsigned encrypted content**: Policy can reject or accept encrypted content without sender authentication (the `ALLOW_ANONYMOUS_ENCRYPT` predicate)

### Encryption and Moderation

Encrypted content presents a challenge for moderation. Moderators can:

1. See that encrypted content exists (manifest is cleartext)
2. See who has access (access policy and encryption policy are readable if not themselves encrypted)
3. See metadata (frame types, handles, timestamps)
4. NOT read the content unless they are recipients

This is intentional. Moderation of encrypted content works through social mechanisms:
- Moderators can moderate based on metadata and reporter accounts
- Recipients who see problematic content can create moderation relations (signed reports)
- The trust matrix weighs these reports like any other moderation relation
- Communities can require moderator access as a condition of participation (encryption policy includes moderators as recipients)

---

## Implementation Plan

### Phase 1: Key Infrastructure

1. `EncryptionPublicKey extends GraphPublicKey` -- parallel to `SigningPublicKey`
2. `Vault.KeyType.X25519` -- new key type
3. `Vault.deriveSharedSecret(alias, peerSpki)` -- abstract + implementations
4. `Vault.decrypt(alias, ciphertext)` -- flesh out the existing stub
5. `KeyLog` gains `currentEncryptionKey()` / `currentEncryptionKeys()` query helpers
6. `Signer.initializeKeys()` generates X25519 alongside Ed25519
7. Tests: key generation, ECDH agreement, encrypt/decrypt round-trip

### Phase 2: Tag 10 Envelope

1. `EncryptedEnvelope` rewrite to use Tag 10 CBOR wrapping
2. `EnvelopeOps.encrypt()` -- uncomment and complete the prototype
3. `EnvelopeOps.decrypt()` -- recipient-side decryption
4. Tag 10 registration in `Canonical.CgTag`
5. Tag 10 encoding/decoding in the CBOR pipeline
6. Tests: single-recipient, multi-recipient, anonymous sender, round-trip

### Phase 3: Frame-Level Encryption

1. `FrameEntry.EntryPayload.encryptedCid` field
2. Commit flow: encrypt frames per policy before storing
3. Hydration flow: detect encrypted frames, attempt decryption via Vault
4. Local index: `ciphertextCID -> plaintextCID` mapping
5. Working tree integration: `.item/` directory handles encrypted frames
6. Tests: mixed encrypted/cleartext frames, progressive encryption

### Phase 4: Policy-Driven Encryption

1. `EncryptionPolicy` on `PolicySet` (per-frame encryption configuration)
2. `AccessPolicy` on `PolicySet` (per-frame distribution control — separate from encryption)
3. Config carry-forward: frame policies survive across commit cycles
4. `EncryptionContext.fromEncryptionPolicy()` factory for policy-driven encryption
5. `encryptToReaders` resolution: derive encryption recipients from AccessPolicy READ rules
6. Group key wrapping via `EnvelopeOps.wrapKeyForRecipient()` / `unwrapKeyForRecipient()`
7. Integration with `Roster` (membership changes trigger encryption policy updates)
8. Re-encryption on member removal (policy-driven)
9. Tests: policy carry-forward, explicit context override, group key lifecycle

### Phase 5: Transport Encryption

1. Noise handshake implementation (XX pattern) using existing X25519 keys
2. Session key derivation and AEAD transport wrapping
3. Integration with `PeerConnection` — encrypt/decrypt all Peer Protocol messages
4. Handshake during `PeerProtocol` connection establishment
5. Optional encryption policy (per-connection, configurable)
6. Session Protocol (client-to-Librarian) transport encryption
7. Tests: handshake, session key derivation, encrypted message round-trip, relay forwarding of double-encrypted content

### Phase 6: At-Rest Encryption

1. Library-level encryption option (device-local key from Vault master key)
2. RocksDB encryption backend integration (Env encryption layer)
3. Application-level encryption for MapDB backend
4. Device key derivation: `HKDF(vault_master_key, "library-at-rest")` → device key
5. Key zeroization on Librarian shutdown
6. Tests: encrypted storage round-trip, key zeroization, double-encryption (content + at-rest)

### Phase 7: X3DH and Pre-Key Infrastructure

1. Signed Pre-Key (SPK) generation, rotation, and publication as Signer frame
2. One-Time Pre-Key (OPK) pool: generation, publication as Signer stream, consumption tracking
3. X3DH handshake implementation (4-DH key agreement)
4. X3DH integration with Vault (all DH operations in-place, keys never exported)
5. Pre-key replenishment policy (threshold-based)
6. Tests: X3DH handshake round-trip, OPK consumption, SPK rotation

### Phase 8: Double Ratchet (1:1 Streams)

1. `RatchetState` local-only data structure (root key, chain keys, ratchet keypair, skipped keys)
2. Symmetric ratchet: KDF chain advancement, per-message key derivation
3. DH ratchet: ratchet key generation, ECDH step, root key update
4. Encrypted stream entry format (header fields 10-12: ratchet pub, chain length, message number)
5. Out-of-order message handling (skipped key cache with configurable window)
6. Ratchet state persistence in Vault (local-only, never synced)
7. Integration with Log/stream component commit flow
8. Tests: message encrypt/decrypt round-trip, DH ratchet step, out-of-order messages, forward secrecy verification, break-in recovery

### Phase 9: Group Chat (Sender Keys)

1. Sender Key generation and symmetric ratchet per-sender
2. Sender Key distribution via 1:1 ratcheted channels (Phase 8)
3. Group message encryption with Sender Key chains
4. Integration with Roster component (join triggers Sender Key distribution, leave triggers rotation)
5. Sender Key rotation on member removal
6. Tests: group message round-trip, member join/leave, Sender Key rotation, forward secrecy in groups

---

## Tag 10 Encoding Examples

### Single Recipient, ECDH-ES Direct

```cbor
Tag(10, [
    {                                   // header
        1: -25,                         // ECDH-ES+HKDF-256
        2: 3,                           // AES-GCM-256
        3: h'a1b2c3d4e5f6a7b8c9d0e1f2', // 12-byte nonce
        5: h'1220abcdef...',            // plaintext CID (multihash)
        6: h'sha256(sender_spki)',      // sender key ID
        7: h'ed25519_signature...'      // sender signature
    },
    [                                   // recipients (1 entry)
        {
            1: h'sha256(recipient_spki)',   // recipient key ID
            2: h'ephemeral_x25519_spki',    // ephemeral public key
        }
    ],
    h'aead_ciphertext_with_tag...'      // encrypted content
])
```

### Multi-Recipient, ECDH-ES + Key Wrap

```cbor
Tag(10, [
    {
        1: -25,
        2: 3,
        3: h'nonce_12_bytes',
        5: h'plaintext_cid'
    },
    [
        {
            1: h'recipient_1_kid',
            2: h'ephemeral_1_spki',
            3: h'aes_keywrap(derived_key_1, cek)'
        },
        {
            1: h'recipient_2_kid',
            2: h'ephemeral_2_spki',
            3: h'aes_keywrap(derived_key_2, cek)'
        }
    ],
    h'aead_ciphertext'
])
```

### Anonymous Sender

```cbor
Tag(10, [
    {
        1: -25,
        2: 3,
        3: h'nonce',
        5: h'plaintext_cid'
        // no senderKid (6) or senderSig (7)
    },
    [ { 1: h'recipient_kid', 2: h'epk' } ],
    h'ciphertext'
])
```

---

## Algorithm Choices

### Default Algorithms

| Purpose | Algorithm | Rationale |
|---------|-----------|-----------|
| Key agreement | X25519 + HKDF-SHA256 | Same curve family as Ed25519, fast, 32-byte keys |
| Content encryption | AES-256-GCM | Hardware-accelerated on most platforms, NIST standard |
| Content encryption (alt) | ChaCha20-Poly1305 | Software-fast on platforms without AES-NI |
| Key wrapping | AES-256-KW (RFC 3394) | Standard key-wrap for multi-recipient |

### Algorithm Agility

All algorithm choices are encoded as COSE integer IDs in the envelope header. New algorithms can be added by extending `Algorithm.KeyMgmt` and `Algorithm.Aead` without changing the envelope format. Old envelopes remain decodable as long as the algorithm implementations are available.

### COSE Alignment

The algorithm IDs are COSE-standard where possible:

| Algorithm | COSE ID | CG Enum |
|-----------|---------|---------|
| ECDH-ES + HKDF-256 | -25 | `Algorithm.KeyMgmt.ECDH_ES_HKDF_256` |
| AES-GCM-128 | 1 | `Algorithm.Aead.AES_GCM_128` |
| AES-GCM-256 | 3 | `Algorithm.Aead.AES_GCM_256` |
| ChaCha20-Poly1305 | 24 | `Algorithm.Aead.CHACHA20_POLY1305` |

These are already defined in the existing `Algorithm` sealed interface.

---

## Interaction with Existing Systems

### Manifest and VID Stability

The VID is computed from the manifest BODY, which includes `snapshotCid` (plaintext CID), NOT `encryptedCid`. This means:

- Re-encrypting to different recipients does NOT change the VID
- Two Librarians with the same content but different encryption produce the same VID
- Signature verification works regardless of encryption state
- Version history is encryption-agnostic

### Content Deduplication

Encryption defeats content deduplication (different ciphertext for same plaintext). This is unavoidable and correct -- deduplication of encrypted content would leak information about content identity.

However, within a single Librarian's storage, the local `ciphertextCID -> plaintextCID` index enables local deduplication awareness: the Librarian knows it already has the plaintext even if it receives a differently-encrypted copy.

### Working Tree (Filesystem Representation)

Encrypted frames in the working tree store the Tag 10 envelope in the `.item/` directory. The Librarian decrypts on access (read) and encrypts on commit (write). Cleartext is ephemeral in memory during editing; the on-disk representation is always encrypted for encrypted frames.

### Seed Vocabulary

Seed items (deterministic IIDs, no signature, code-defined) are never encrypted. They are public bootstrap vocabulary. Encryption applies only to user-created content.

---

## Open Questions

1. **Manifest encryption**: Should the manifest itself ever be encrypted (hiding item structure, not just content)? This would break discovery and routing. Proposed answer: no, but a "sealed item" mode could encrypt the ItemState portion while leaving identity fields (iid, parents, type) cleartext.

2. **Encrypted relations**: Relations are currently always cleartext (signed assertions). Should encrypted relations be supported? Proposed answer: yes, using the same Tag 10 envelope. An encrypted relation is opaque to anyone not in the recipient list. The relation's RID is computed from the cleartext body (for deduplication), but the stored bytes are the envelope.

3. **Key escrow / recovery**: If a device is lost and its Vault is gone, can encrypted content be recovered? Proposed answer: this is a policy decision. Users can designate recovery contacts (whose encryption keys are added as recipients to a "recovery envelope" of the user's master key). Common Graph does not provide centralized key escrow.

4. **Encrypted search**: Can you search encrypted content? Only locally (after decryption). The Librarian can maintain local full-text indexes of decrypted content. Remote search across encrypted content requires the content owner to participate (they decrypt, evaluate the query, and return results). This is a natural fit for the query propagation model.

5. **Post-quantum**: X25519 is vulnerable to quantum computers. The envelope format supports algorithm agility, so a post-quantum KEM (like ML-KEM/Kyber) can be added as a new `Algorithm.KeyMgmt` variant without changing the envelope structure. Hybrid mode (X25519 + ML-KEM) provides defense-in-depth during the transition.

6. **Multi-device ratchet**: Each device has its own identity key and own ratchet state. When a user has multiple devices, each device has separate 1:1 ratchet sessions with each peer. Messages sent from one device are NOT automatically available on other devices. Options: (a) fan-out (sender encrypts to all of the recipient's devices), (b) sender-device-to-all-recipient-devices (MLS-style tree), (c) device sync via a local encrypted channel. Sender Key groups partially address this since each device can be a separate group member.

7. **Noise pattern selection**: The XX pattern provides mutual authentication and identity hiding. The NK pattern (initiator knows responder's static key) would be simpler for cases where the Librarian's key is already known (e.g., from a PEERS_WITH relation). The IK pattern provides zero-round-trip encryption when both keys are known. Should we support multiple patterns or standardize on XX?

8. **Ratchet state backup**: If a device's Vault is lost, all ratchet states are lost. Past messages remain encrypted (forward secrecy means we can't re-derive old keys). New sessions must be re-established via X3DH. Should there be a mechanism for encrypted ratchet state backup to a recovery contact?

---

## References

**External resources:**
- [COSE (RFC 9052)](https://www.rfc-editor.org/rfc/rfc9052.html) -- CBOR Object Signing and Encryption
- [HPKE (RFC 9180)](https://www.rfc-editor.org/rfc/rfc9180.html) -- Hybrid Public Key Encryption
- [Signal Protocol](https://signal.org/docs/) -- Double Ratchet, X3DH key agreement, Sender Keys
- [Noise Protocol Framework](https://noiseprotocol.org/noise.html) -- Transport encryption handshake patterns
- [Age encryption](https://age-encryption.org/) -- Simple modern file encryption
- [MLS (RFC 9420)](https://www.rfc-editor.org/rfc/rfc9420.html) -- Messaging Layer Security for group encryption
- [NaCl/libsodium](https://nacl.cr.yp.to/) -- Crypto_box (X25519 + XSalsa20-Poly1305)

**Academic foundations:**
- [Bernstein 2006 -- Curve25519](https://cr.yp.to/ecdh/curve25519-20060209.pdf) -- X25519 key agreement
- [Bernstein et al 2012 -- Ed25519](references/Bernstein%20et%20al%202012%20-%20High-Speed%20High-Security%20Signatures%20Ed25519.pdf) -- Signing on the same curve
- [Perrin, Marlinspike 2016 -- The Double Ratchet Algorithm](https://signal.org/docs/specifications/doubleratchet/) -- Formal specification
- [Marlinspike, Perrin 2016 -- The X3DH Key Agreement Protocol](https://signal.org/docs/specifications/x3dh/) -- Initial key agreement
- [Cohn-Gordon et al 2020 -- Formal Security Analysis of the Signal Protocol](https://eprint.iacr.org/2016/1013.pdf) -- Double Ratchet security proofs
- [McGrew, Viega 2004 -- GCM](https://csrc.nist.gov/publications/detail/sp/800-38d/final) -- AES-GCM specification
