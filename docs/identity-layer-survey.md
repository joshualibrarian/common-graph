# Identity Layer Survey (2026-05-17)

This doc surveys the current state of CG's identity layer (Vault, Signer, MultiKey, AlgorithmHandle, VarSig) with a specific question in mind: **what's required to thread Vault-backed identity through TLS and Noise so that TlsTunnel/NoiseTunnel meaningfully bind to CG identities?**  It complements [`network-stack.md`](network-stack.md) — that doc set the architecture; this one is the readiness check before we build it.

## TL;DR

The Vault SPI is in great shape for JCA Provider integration.  The signing primitive, algorithm metadata, public-key encoding, and the JCA bridge for the verify side are all there and working.  Five new pieces are needed for the full TLS+identity story, plus one Vault extension (encryption-purpose / X25519) for Noise.  Estimate: 4-5 focused sessions, broken into phases A-E below.

The cert-as-record + trust-graph design in `network-stack.md` is sound; this survey doesn't change any of those decisions.  It does sharpen the substep ordering.

## What's solid

### Vault SPI (`identity/vault/Vault.java`, 286 lines)

Clean, multi-purpose, well-designed.  Worth keeping the interface as-is.

- `Vault.sign(byte[]) → VarSig` — the signing primitive.  Returns a self-describing VarSig; raw signature bytes are available via `VarSig.rawSig()`.
- `Vault.sign(byte[], ItemRef purpose)` — per-purpose signing.  Used today only for the signing purpose (Phase 1); the architecture has room for encryption / key-agreement purposes already.
- `Vault.signingAlgorithm() → Optional<ItemRef>` — algorithm sememe IID.  Resolves through the librarian's AlgorithmCache to an AlgorithmHandle (which carries JCA names).
- `Vault.signingPublicKey() → Optional<MultiKey>` — exposed pubkey, codec-tagged.
- `Vault.publicKey(purpose) → Optional<MultiKey>` — per-purpose form.
- `Vault.canSign() / canKeyAgree()` — readiness predicates.
- `Vault.isLocked()` and `Vault.lock()` — encryption-at-rest hook (no-op for InMemoryVault, real for future encrypted-file / OS-keychain / HSM vaults).
- Event-emitting methods (`incept`, `rotate`, `delegate`, `revoke`) all produce signed Frames.

### InMemoryVault (`identity/vault/InMemoryVault.java`, 421 lines)

Phase-1 implementation.  Ed25519 only on the signing purpose.  Key-agreement track returns empty.  Uses standard JCA internally (`Signature.getInstance("Ed25519")`, `KeyPairGenerator.getInstance("Ed25519")`), proving the JCA path works end-to-end.  Per-purpose `PurposeState` holds current keypair + pre-rotation next keypair + chain head + sequence.  Generates fresh current + next keypairs at construction.

### MultiKey + AlgorithmHandle (`identity/MultiKey.java`, 158 lines; `identity/AlgorithmHandle.java`, 66 lines; `identity/JcaAlgorithmHandle.java`, 168 lines)

**This is the part that does most of the JCA bridging work already.**

- `MultiKey.publicKey() → java.security.PublicKey` directly, when the handle is resolved.  Zero further wiring needed.
- `AlgorithmHandle.decodePublicKey(byte[]) → java.security.PublicKey` is the raw-bytes-to-JCA-key path.  Ed25519 is wired; other algorithms throw a clean UnsupportedOperationException with the per-family branch obviously identifiable.
- `JcaAlgorithmHandle` carries `signatureName` ("Ed25519") and `keyFactoryName` exactly matching what `Signature.getInstance(name)` / `KeyFactory.getInstance(name)` want.
- `verify(message, signature, publicKey)` works generically through JCA.
- `JcaAlgorithmHandle.ofEd25519()` static singleton + `builtinByVarsigCode` / `builtinByMultikeyCode` fallback registry — usable in librarian-less contexts.

### VarSig (`identity/VarSig.java`)

Self-describing signature wrapper.  `rawSig()` exposes the underlying signature bytes — that's what a JCA Provider's `Signature.engineSign()` would return after unwrapping.

### Signer (`identity/Signer.java`, 579 lines)

Three construction modes (identity-only, vault-only, vault+librarian).  Auto-incepts on construction when both vault and librarian are present.  IID derived from initial signing pubkey — cryptographically bound to key.

## What's missing for the full TLS+identity integration

### 1. JCA Provider wrapping Vault

The single biggest piece.  A `VaultProvider extends java.security.Provider` that:

- Registers a `VaultPrivateKey implements PrivateKey` — opaque handle carrying a Vault reference + algorithm sememe + purpose.  No actual key material in the JVM heap.
- Registers a `VaultSignatureSpi extends SignatureSpi` — `engineSign()` collects the accumulated message bytes and calls `vault.sign(messageBytes, purpose)`, returns `varSig.rawSig()`.  `engineInitSign(PrivateKey)` validates the key is a VaultPrivateKey.
- Registers a `VaultKeyStore` (optional, nice for SslContextBuilder integration) — lists the Vault's incepted purposes as KeyStore entries.

Once registered with `Security.addProvider(...)`, `Signature.getInstance("Ed25519", "VaultProvider")` returns a signature object that signs through Vault.  SslContextBuilder's `keyManager(privateKey, certChain)` then accepts the VaultPrivateKey and TLS handshake signing routes through Vault transparently.

**Size estimate**: ~300-400 lines.  Self-contained.  No changes to existing Vault SPI required.

**Home**: Provisional `:core/identity/jca/VaultProvider.java`.  Pure JDK deps.

### 2. Cert sememe + X.509 ingestion

A `cg.archetype:x509-cert` (or similar) that represents a parsed X.509 cert as CG bindings.  Plus parsing code that takes a `java.security.cert.X509Certificate` and emits a CG record.

Sketch (per network-stack.md, sharpened):

```
@Type("cg.archetype:x509-cert")
class X509Cert {
  @Frame(SUBJECT_PUBKEY)    MultiKey subjectKey;       // multikey-encoded
  @Frame(SUBJECT_NAME)      String   subjectName;     // RFC 4514 DN string
  @Frame(SUBJECT_ALT_NAMES) List<String> subjectAltNames;
  @Frame(ISSUER_CERT_REF)   ItemRef  issuerCertRef;   // points at parent cert
                                                       // record; self-ref for
                                                       // self-signed
  @Frame(VALIDITY_FROM)     Instant  notBefore;
  @Frame(VALIDITY_UNTIL)    Instant  notAfter;
  @Frame(SERIAL_NUMBER)     byte[]   serialNumber;
  @Frame(SIGNATURE_ALG)     ItemRef  signatureAlgorithm;  // algorithm sememe IID
  // The record-level signature carries the issuer's signature over the cert body.
}
```

Open design questions for this piece:

- Should there be a separate `cg.archetype:cg-cert` for native CG-issued certs (no X.509 baggage)?  Or do CG-native certs reuse the X.509 sememe with all-defaulted fields?  My lean: reuse — X.509 is general enough, the AID-cert case is just "issuer-ref = self, signature alg = Ed25519, SAN = single AID URN."
- Where does the parsed cert chain get stored — same library as other items, or a separate "trust store" item?  My lean: same library; trust comes from anchor declarations, not from where the cert physically lives.

**Size estimate**: ~200-300 lines (sememe declaration + parser + tests).

**Home**: Probably `:core/identity/cert/`.  Could land in `:bridges:tls` if we want to keep `:core` lean.

### 3. Self-signed cert generation from Vault

A `CertBuilder` that takes a Vault + purpose + subject info → generates an X.509 cert whose Subject Public Key is the Vault's current pubkey for that purpose, signed by the Vault (via the JCA Provider).  Used when a CG node wants to present a TLS cert bound to its AID.

Concretely:
1. Build a tbs (to-be-signed) cert structure with the right subject pubkey + SAN containing the AID URN.
2. Encode the tbs to DER.
3. Sign the DER with the Vault (JCA Provider).
4. Wrap the tbs + signature in an X.509 cert structure.

JDK's `java.security.cert` doesn't have built-in cert generation; we'd need BouncyCastle (already a dep via `bcprov`) or write the ASN.1 by hand.  BouncyCastle's `X509v3CertificateBuilder` is the path of least resistance.

**Size estimate**: ~150 lines.

**Home**: Same as cert sememe.

### 4. Trust resolver

A small service that answers "is this pubkey trusted?" by walking the cert / identity records in the local graph from anchors.  Used by:
- TlsTunnel's eventual `counterparty()` post-processor (peer pubkey → IID)
- Parley's trust matrix on accept/connect
- KERI's identity verifier

Simplest form: `TrustResolver.resolve(MultiKey pubkey) → Optional<ItemRef trustedIid>`.  Anchors are explicit "I trust this IID" assertions in the librarian's storage; cert records form the chain.

**Size estimate**: ~150 lines + tests.

**Home**: `:core/identity/trust/`.

### 5. Wire TlsTunnel.counterparty()

Currently returns empty.  After (1)-(4) land, this becomes: extract peer cert from SslSession → convert to MultiKey via the existing `MultiKey.publicKey()` path inverted (pubkey bytes from cert SubjectPublicKeyInfo, wrap as MultiKey with the appropriate handle).

**Size estimate**: ~50 lines + test.

### 6. NoiseTunnel real implementation

Bigger.  Three sub-pieces:

- **6a. X25519 in Vault** — Add encryption-purpose support to InMemoryVault.  Either generate fresh X25519 keypairs or derive from Ed25519 (RFC 7748 / RFC 8032 trick: SHA-512(seed) yields X25519 private from Ed25519 seed).  Currently the encryption track returns empty everywhere; this lights it up.
- **6b. Noise XX state machine** — Java has no widely-used Noise library; either pull one in (e.g., `southernstorm/noise-java`) or hand-write XX.  Noise XX is well-specified and not huge.
- **6c. NoiseTunnel** as a Netty `ChannelHandler` (or, more likely given our pattern, an `EmbeddedChannel`-hosted handler — same shape as TlsTunnel) that runs the Noise XX handshake using Vault X25519 keys, then provides AEAD encrypt/decrypt for steady-state traffic.

**Size estimate**: ~500-700 lines total across the three pieces.  Largest unknown is the Noise library choice.

**Home**: 6a is Vault changes (`:core`).  6b + 6c land in `:bridges:noise`.

## Phased plan

Each phase is roughly a session of focused work.  Order matters: later phases consume earlier ones.

### Phase A — Foundation (no protocol integration yet)

- **A1.** Design + implement cert sememe.  X.509 → CG record parser.  Tests.
- **A2.** JCA Provider wrapping Vault.  Tests against `Signature.getInstance("Ed25519", "VaultProvider")`.
- **A3.** Self-signed cert generation from Vault (via JCA Provider).  Tests.
- **A4.** Trust resolver (graph query for pubkey → IID).  Tests.

Phase A produces no user-visible change.  All output is testable in isolation.

### Phase B — TLS-identity wiring

- **B1.** Wire `TlsTunnel.counterparty()` to extract peer cert pubkey, look up via TrustResolver.
- **B2.** End-to-end test: server side presents Vault-backed AID cert; client side verifies via TrustResolver; both report counterparty() correctly.

Phase B is when TLS starts MEANING something for CG identity.

### Phase C — Noise

- **C1.** X25519 keys in Vault (encryption purpose).
- **C2.** Pick a Noise lib (or hand-write XX).
- **C3.** Real NoiseTunnel — Netty handler form.
- **C4.** Same end-to-end test as B2 but with Noise instead of TLS.

Phase C closes the parallel.  TLS-with-AID-cert and Noise-with-static-key become the same architectural pattern in different wire dressings, exactly as `network-stack.md` predicted.

### Phase D — Parley trust matrix

- **D1.** Parley.accept/connect inspect `tunnel.isConfidential()` / `isAuthenticated()` / `counterparty()` against a configurable policy.
- **D2.** Default policy: reject non-authenticated tunnels for cross-host conversations; accept anything for loopback.
- **D3.** Tests: Parley refuses to speak on raw TCP between identities that don't know each other; happily speaks over TLS-AID or Noise-AID.

Phase D is when "production-quality CG networking" becomes real.

### Phase E — KERI on top

KERI is now just another application of the foundation: HTTP (already done) over TLS (now identity-bound) with AID-as-cert-key (now meaningful).  The bridge is what we always thought it would be — a translator between CG records and KERI's KEL format — with no novel infrastructure required.

## Open questions surfaced by the survey

These don't block starting Phase A but should be settled before they bite:

- **Key-agreement-purpose key derivation.**  X25519 from a fresh keypair, or derived from the same Ed25519 seed?  The latter means one identity has both signing and key-agreement capabilities without minting separate keys, but the cryptographic provenance gets subtler.  Probably: separate keys, kept in the same Vault under different purposes, related by being incepted/rotated together.
- **Cert validity periods for AID certs.**  KERI's pre-rotation makes "cert expiry" weird — a CG identity's signing key can rotate, but the AID doesn't.  Probably: cert validity = current keypair's tenure, regenerate cert on each rotation.  Trust resolver checks the current cert against the AID's KEL.
- **Multi-cert per identity.**  An AID might want one cert for HTTPS server (with hostnames in SAN) and another for client mTLS (with AID URN in SAN).  The cert sememe supports this naturally; just multiple cert records pointing at the same subject pubkey.

## Recommendation

Start Phase A.  All four sub-items are well-scoped, the Vault SPI doesn't need changes, and we have a concrete test endpoint for each piece.  A1 (cert sememe) is the right first move because A2-A4 want to use it.

After Phase A lands, we re-evaluate before starting Phase B.  Some of the Phase B/C/D ordering may want to change once we see how the cert sememe actually shapes up.
