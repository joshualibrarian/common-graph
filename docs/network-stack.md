# Network Stack Architecture

This document captures the architectural decisions for Common Graph's network layer: how byte channels, security wrappers, protocols, and bridges compose; where Netty fits; how TLS integrates with CG's identity substrate; and the module layout that falls out.  It supersedes the implementation-detail portions of [`network.md`](network.md) and complements [`bridges.md`](bridges.md), which covers the conceptual model of bridges-as-items.

The decisions here are working decisions made before the corresponding refactor lands.  They guide the upcoming work and exist so the architecture can be iterated on cold rather than rederived each session.

## Why this exists

When the network surface was first built, two things were true that no longer are:

1. **`:core` was meant to contain everything that wasn't platform-specific.**  Networking went in `:core` because Parley and the Tunnel abstractions felt foundational.  At the time, `:core` had no third-party byte-channel deps.
2. **HTTP was an afterthought.**  We didn't have any HTTP-mediated foreign protocols on the near roadmap, so HTTP was something to build "when we needed it."

Both assumptions have shifted.  KERI is on the near roadmap and lives on HTTPS.  TLS is non-negotiable for anything serious.  TCP, TLS, HTTP, WebSocket, Noise, QUIC, Reticulum-sidecar are all going to be present in any real deployment, and they all interact with the network in similar ways.  Reimplementing the byte-channel and protocol-handler machinery in three different styles (raw Tunnel impl, Netty-pipeline, library-specific) creates duplication that gets worse as more bridges land.

At the same time, the decision to bring HTTPS up surfaced a unification question that the original architecture didn't address: **TLS is not HTTP-shaped.**  TLS wraps any byte stream.  If TLS is added to HTTP as an HTTP feature, then anything else wanting TLS (Parley over TLS, raw CESR over TLS, future custom protocols over TLS) duplicates the TLS plumbing.  The TLS-as-Tunnel-wrapper model that Noise already follows is the only design where TLS lives in exactly one place.

This memo records the resulting architectural decisions and the reasoning behind them.

## The layering

Four levels, bottom-up:

```
Application      Librarian, Session, Item handlers, user code
                 ───────────────────────────────────────────────
Protocol         Parley, HTTP, KERI, ActivityPub, SMTP, ...
                 ───────────────────────────────────────────────
Tunnel           byte stream with declared security properties
  + wrappers     TlsTunnel, NoiseTunnel, CompressedTunnel, ...
                 ───────────────────────────────────────────────
Transport        TCP, Unix sockets, Loopback, Reticulum, ...
                 produces base Tunnels
```

Each level depends only on the one below, and each level is replaceable without disturbing the others.

**Transports** produce base Tunnels from a real medium.  `TcpTransport.connect` and `TcpTransport.listen` are the canonical shape.  A Transport speaks one medium and produces the Tunnels that ride it.

**Tunnels** are byte streams between two parties.  The Tunnel SPI carries declared security properties: `isConfidential()`, `isAuthenticated()`, `counterparty()`.  Base Tunnels come from Transports; wrapper Tunnels (TLS, Noise, compression) decorate any underlying Tunnel to add cross-cutting properties.  Wrappers are themselves Tunnels, so they compose: `NoiseTunnel(TcpTunnel(...))` is a confidentiality-and-authentication wrapper over raw TCP, and Parley running on it doesn't have to know.

**Protocols** consume Tunnels and speak some application-level conversation over them.  Parley speaks self-describing CG values.  HTTP speaks request/response.  KERI speaks KEL events over HTTP.  The protocol layer doesn't care whether the underlying Tunnel is TCP, TLS-over-TCP, Noise-over-TCP, or Loopback.  It only cares about bytes plus the declared security guarantees, which it uses to decide whether the conversation is acceptable.

**Applications** use protocols.  The Librarian uses Parley to talk to peer librarians.  A KERI consumer uses HTTP to fetch KEL streams from witnesses.  Application code never touches Tunnels or Transports directly; it talks to protocol services.

This is the discipline the refactor enforces.  Everything that crosses these boundaries does so through the SPIs above.

## Bridges are protocols

A bridge in CG is a protocol implementation that talks to other parties.  Parley is a bridge (to another CG instance).  KERI is a bridge (to KERI-speaking parties).  ActivityPub is a bridge (to the fediverse).  SMTP is a bridge (to the mail system).

This is a clarifying reframe.  The original `bridges.md` framing was "foreign-protocol bridges" implying CG-native communication was somehow architecturally different.  It's not.  Parley happens to be the protocol we designed and signed off on, but mechanically it sits at the same level as KERI.  Both are protocols that consume Tunnels and produce frames for the Librarian.

Consequence: **Parley moves to `:bridges:parley`.**  `:core` no longer contains network code.  The Tunnel SPI itself stays in `:core` because it's a value type referenced by other SPIs (Endpoint, Transport), but Tunnel implementations live in bridge modules.

## Netty is the network fabric

For the JVM-side runtime, Netty is the implementation foundation across every bridge that touches bytes.  Specifically:

**Will use Netty:**

- TCP, Unix sockets, TLS, Noise (a Netty handler wrapping a non-Netty Noise impl), HTTP/1.1, HTTP/2, WebSocket, QUIC.
- All protocols that ride HTTP: KERI, ActivityPub, OAuth, webhooks, REST APIs.
- Anything that talks to a sidecar process (Reticulum daemon, Tor, etc.).  Our side of that local socket is Netty.

**Won't use Netty:**

- Wrappers around external libraries that own their I/O: JavaMail (SMTP/IMAP), libp2p-jvm (IPFS), native Bluetooth Low Energy stacks, LoRa direct hardware, serial port comms.
- Hardware-touching transports where Netty has nothing to offer (BLE, LoRa, serial → native libraries via JNI).

For Reticulum specifically, three paths exist with different Netty stories:

1. **Sidecar** — run the Python RNS daemon separately, connect over a local socket.  Our side is Netty.  Most likely first move.
2. **Embedded Python** — pull RNS into the JVM via GraalPython.  No sidecar.  Larger startup cost.
3. **Native Java RNS** — reimplement as Netty handlers.  Substantial ongoing work.

The reasons for committing to Netty:

- **It's the JVM's de facto network library.**  Java's network library landscape converged on Netty for excellent reasons.  No contender on the horizon.
- **The pipeline model is exactly right** for stacked protocol processing.  TLS handlers, framing handlers, codec handlers, app handlers compose by addition.  Pipeline composition is finer-grained than tunnel composition and lets us assemble protocols by handler chains.
- **Maintained primitives exist for everything we need** — `LengthFieldBasedFrameDecoder` for framing, `HttpServerCodec` / `HttpClientCodec` for HTTP, `WebSocketServerProtocolHandler` for WebSocket, `SslHandler` for TLS, `HttpObjectAggregator` for body aggregation.  Each one would be weeks of work to write from scratch.
- **Event loop, allocator, and buffer machinery are battle-tested.**  Threading correctness in async I/O is genuinely hard; Netty has paid that cost.
- **We're going to end up Netty-everywhere anyway** for any serious deployment.  TcpTunnel is Netty.  HTTP is Netty.  TlsTunnel will be Netty (SSLEngine without SslHandler is masochism).  Pretending Netty is contained to a corner of the codebase creates duplicate glue without any actual independence benefit.

The reason to NOT abstract Netty further:

- **Tunnel is the abstraction at the boundary; ChannelPipeline is the tool inside.**  Two different jobs.  Wrapping ChannelPipeline in a CG-flavored DSL would be a leaky abstraction with no offsetting benefit.  Anyone joining the project who knows Netty can read our pipeline assembly directly.
- **Implementation independence is a fake benefit.**  We're not going to swap Netty out.  Paying ongoing complexity cost for hypothetical portability is a bad trade.

## Tunnel vs. Channel

Tunnel and Netty's Channel look similar on the surface.  They're not.  The distinction is meaningful and load-bearing.

**Netty's `Channel`** is a low-level byte-I/O mechanism.  It models "I have a pipeline of handlers attached to an event loop and bytes flow through it."  It's powerful and general.  Its security properties live INSIDE the pipeline as handlers; if you want to know whether a Channel is confidential, you walk its pipeline looking for an SslHandler and inspect its session.  The API surface is large.

**CG's `Tunnel`** is a semantic contract.  It models "a post-handshake byte stream between two parties, with declared security properties."  It exposes `counterparty()`, `isConfidential()`, `isAuthenticated()` as first-class accessors.  It has six methods.  It says nothing about pipelines, event loops, or how the bytes get there.

The distinction matters in three places:

1. **The trust matrix asks `counterparty()` and gets a `MultiKey` back.**  It shouldn't have to walk a Netty pipeline looking for an SSL handler, extract the session, pull the peer principal, convert to a key.  Tunnel's job is to surface that one fact in CG terms.
2. **Not every Tunnel is Netty-backed.**  `LoopbackTunnel` for tests, JavaMail-wrapped bridges, future hardware-direct bridges.  They all satisfy the Tunnel contract without going through Channel.
3. **Tunnel deliberately narrows the surface.**  Half of what Channel exposes is bookkeeping the Librarian shouldn't see (allocators, autoRead, write water marks).  Tunnel's small API is intentional.

Channel is the mechanism layer.  Tunnel is the contract layer.  Most Tunnel implementations end up being thin facades over Channels, but the facade IS the abstraction.

## TLS lives at the Tunnel layer

TLS is not HTTP-shaped.  TLS wraps any byte stream.  HTTPS, SMTPS, IMAPS, postgres-over-TLS, raw-CESR-over-TLS, Parley-over-TLS.  Anything that exchanges bytes can be wrapped in TLS for confidentiality and authentication.

So architecturally, TLS belongs as a **Tunnel wrapper**: `TlsTunnel(underlyingTunnel)`.  This is the same pattern Noise follows.  TLS in exactly one place; anything that wants TLS gets it by composing with TlsTunnel.

**Implementation:** TlsTunnel uses Netty's `SslHandler` under the hood, via a Netty `EmbeddedChannel` pipeline running inside the Tunnel impl.  Bytes from the underlying Tunnel feed into the embedded channel's inbound; SslHandler decrypts; cleartext comes out to the consumer registered via `TlsTunnel.onReceive`.  Outbound bytes get written into the embedded channel; SslHandler encrypts; ciphertext drains to `underlyingTunnel.send`.  Module home: `:bridges:tls`.

**TlsTunnel.counterparty()** returns the verified peer's public key as a MultiKey.  Same contract as NoiseTunnel.  The trust matrix doesn't need to know which one produced the result.

The implication for HTTP: HTTPS is composition, not a separate concept.  The caller wires `TcpTransport.connect → TlsTunnel(tcpTunnel) → HttpClient.send(request, tlsTunnel)`.  HttpClient has no knowledge of TLS.  It just sees a Tunnel handing it bytes.

## Cert-as-record unifies PKI and CG-native modes

A TLS server presents an X.509 certificate.  The certificate contains a subject name, a subject public key, an issuer, a validity period, and a signature by the issuer's key.  All of that is signed data.  All of it is expressible as a CG body with bindings.

Once a certificate is ingested as a CG record, the certificate chain becomes a chain of CG records.  Trust becomes a graph property: "given this public key, is there a chain of cert records ending at a record I anchor as trusted?"  Anchors are either trusted-root certs (PKI roots, ingested once) or direct identity assertions ("IID X is trusted").

This collapses the apparent duality between PKI mode and CG-native mode:

- **PKI mode**: server presents a cert chain rooted at a public CA.  The cert chain is a chain of CG records.  Anchors are the public CA certs (ingested into the graph as well-known trusted roots).  Trust resolution is a graph traversal.
- **CG-native mode**: server presents a self-signed cert whose subject public key IS the Vault key for a CG identity (typically an AID).  The cert is a single CG record, signed by itself.  Trust resolution is "does the cert's pubkey hash match the IID I expected?"  Also a graph query.

Both paths reduce to the same operation: "show me records that anchor this public key to something I trust."

Sketch of the cert sememe (working name, subject to refinement during implementation):

```
@Type("cg.archetype:x509-cert")
class X509Cert {
  @Frame(SUBJECT_PUBKEY)    MultiKey subjectKey;
  @Frame(SUBJECT_NAME)      String subjectName;
  @Frame(ISSUER_KEY_REF)    @ItemRef issuerCertRef;   // points to parent cert record; self-ref for self-signed
  @Frame(VALIDITY_FROM)     Instant notBefore;
  @Frame(VALIDITY_UNTIL)    Instant notAfter;
  // The record-level signature is the issuer's signature over the cert body.
}
```

A KERI AID's TLS cert is the degenerate case: subject pubkey is the AID's verifying key, issuer-ref points at itself, lifetime is governed by the AID's rotation policy.

The cert-ingestion step is the foreign-bridge act: take an X.509 cert from the wire (or from a CA), parse it, emit a signed CG record representing it.  Once ingested, downstream is all CG.  The TLS layer's verification, the trust matrix's decision, the Parley HELLO check — they all query the same graph.

## Vault delegation via JCA Provider

A TLS server's handshake involves signing operations with the cert's private key.  If that private key lives in the Vault (and per CG's security invariant, it must — see [`project_private_keys_in_vault`](../memory/project_private_keys_in_vault.md)), then the handshake signing must happen IN the Vault.  The cleartext key never appears in the JVM heap.

The right pattern is a **JCA (Java Cryptography Architecture) Provider** that exposes Vault keys as `java.security.PrivateKey` objects whose actual `Signature` operations delegate to the Vault.  This is the same mechanism PKCS#11 / HSM integrations use.  SSLEngine asks the Provider to sign; the Provider asks the Vault; the Vault signs and returns the signature.

Concretely, the Provider exposes:

- A `KeyStore` implementation that lists the Vault's certificate-bearing identities.
- `PrivateKey` instances that hold an opaque handle, not key material.
- `Signature` and `KeyAgreement` SPIs whose `engineSign` / `engineDoPhase` route through the Vault.

Once registered with `java.security.Security.addProvider(new VaultProvider(vault))`, SSLEngine and any other Java security API can use Vault keys transparently.

The Provider is independent of TLS.  It's a reusable Vault-as-keystore mechanism, useful for any Java security operation: JWT signing, JOSE, code signing, mTLS client auth, future custom signers.  Building it once unlocks all of those.

Module home: `:core` if it depends only on `java.security`, or a new `:bridges:vault-jca` if we want to keep `:core` minimal.  Provisional placement: `:core`, since it's a pure-JDK concern.

## The KERI shape

This whole stack converges on a particularly clean shape for KERI.  KERI uses Ed25519 keys.  TLS 1.3 supports Ed25519 in certs and handshakes (RFC 8446).  Therefore:

- An AID's verifying key can BE the TLS cert's public key.
- The witness presents a self-signed cert; the client verifies the cert's pubkey hash matches the AID it's trying to talk to.
- No CA needed.  No PKI chain validation.  No third-party trust anchor.
- The TLS handshake authenticates the AID directly.

The same Vault key that signs KERI KEL events also serves the TLS handshake.  One identity, one key, two surface uses.

Mechanically:

1. The Vault holds an Ed25519 key.  The corresponding AID is derived from it.
2. A self-signed X.509 cert is generated whose Subject Public Key is the AID's verifying key.  The AID is encoded in the SAN (or implicit via pubkey hash).
3. The cert is ingested as a CG record (so it lives in the graph and the trust matrix can find it).
4. The witness HTTPS server uses TlsTunnel with `SslContext` configured to present the cert and use the JCA Provider for handshake signing.
5. Clients connecting verify: TlsTunnel.counterparty() returns the cert's pubkey; the trust matrix queries the graph; does the pubkey correspond to the AID we expected?
6. Authenticated.  No middleware.

The same pattern works in reverse: a CG node's Parley-over-TLS server presents a cert whose key is the node's signing identity; peer librarians verify against their trust matrix.  HTTPS-with-AID-cert and Parley-over-Noise become the same architectural pattern in different wire dressings — both "use the medium's handshake to authenticate the identity directly, skip the PKI middleware."

## Module layout

```
:core
  Tunnel (SPI), Endpoint (value), Transport (SPI),
  Item, Frame, Vocabulary, Librarian, ...
  + (provisional) VaultProvider for JCA delegation
  no Netty dependency

:bridges
  Container.  Future shared scaffolding lives here.

:bridges:tcp
  TcpTransport, TcpTunnel (Netty NioSocketChannel)

:bridges:unix
  UnixTransport, UnixTunnel (Netty EpollDomainSocketChannel / KQueue) (planned)

:bridges:tls
  TlsTunnel (Netty SslHandler in EmbeddedChannel)

:bridges:noise
  NoiseTunnel (Netty handler wrapping Noise impl)
  Currently lives in :core/network/parley/NoiseTunnel.java — moves here.

:bridges:parley
  ParleyService (Netty pipeline: framer, codec handshake, dispatcher)
  CodecHandshakeHandler, ParleyFramer, ParleyDispatcher
  Currently lives in :core/network/parley/ — moves here.

:bridges:http
  HttpServer, HttpClient, HttpRouter, HttpRequest, HttpResponse, ...
  (Netty HttpServerCodec / HttpClientCodec)
  Already exists.  Refactor to compose at pipeline level rather than
  owning Netty Channels directly — see "Open questions" below.

:bridges:keri
  KeriService.  Depends on :bridges:http and :bridges:tls.
  KEL event types as CG sememes.  AID ↔ IID mapping.  (planned)

:bridges:activitypub
  Depends on :bridges:http.  (planned)

:bridges:smtp
  JavaMail-wrapped.  No Netty.  (planned)

:bridges:ipfs
  libp2p-jvm wrapped.  No Netty.  (planned)

:bridges:reticulum
  Sidecar to Python RNS, connects via Unix socket.
  Depends on :bridges:unix.  (planned)
```

Dependency rules:

- `:core` depends on nothing else in the project.
- Every `:bridges:*` module depends on `:core`.
- `:bridges:*` modules may depend on each other only in one direction:  `:bridges:keri` may depend on `:bridges:http` and `:bridges:tls`; the reverse is forbidden.
- No cycle.  No `:bridges:*` ever depends on `:bridges:parley` except things explicitly bridging Parley to another protocol.

## Open questions

These need to be decided before the refactor starts, or as part of it.

**HTTP-Tunnel relationship.**  In a Netty-everywhere world, the original plan to refactor HttpServer/HttpClient to ride on `Tunnel` (via EmbeddedChannel adapter) is one option.  The other option is to let HTTP own its own Netty pipeline directly and compose at the pipeline level: `pipeline.addLast(sslHandler); pipeline.addLast(httpCodec); pipeline.addLast(router)`.  In the pipeline-composition model, Tunnel is still real and still cross-bridge useful, but HTTP wouldn't go through it because pipelines compose at a finer grain than tunnels.  Both are defensible.  My lean: pipeline composition for HTTP (it's already there in `:bridges:http`), Tunnel-wrapping for byte-channel protocols like Parley.  Decide before refactoring HTTP.

**Where does `LoopbackTunnel` live?**  It's currently in `:core/network/tunnel/LoopbackTunnel.java`.  Options: (a) stays in `:core` because it's pure-Java with no deps and is useful for `:core`-side tests, (b) moves to `:bridges:loopback` for consistency with other Tunnel implementations.  Lean: stays in `:core`, since it has no deps and is broadly useful.

**Cert sememe shape.**  The sketch above is provisional.  Real design needs: what's the role for the issuer reference (a cert ItemRef, or a key DatumRef)?  How does revocation surface?  How are CRLs / OCSP responses represented (probably as separate revocation-record sememes anchored to a cert)?  What's the relationship between a `cg.archetype:x509-cert` (foreign format) and a `cg.archetype:cg-cert` (CG-native, no X.509 baggage)?  May want both.

**Trust matrix query API.**  Today the trust matrix is mostly conceptual; the actual graph-query API for "do I trust this pubkey?" doesn't exist as a callable thing.  Needs design once the cert sememe is settled.

**JCA Provider location.**  Provisional `:core`.  If it ends up pulling in non-JDK crypto deps (Bouncy Castle for algorithms JDK doesn't have), it moves to `:bridges:vault-jca`.

**Noise impl choice.**  Java has a few Noise implementations of varying quality.  Need to evaluate and pick one before building NoiseTunnel as a Netty handler.

## Migration plan

Rough ordering, refinable as we go:

1. **This memo lands first.**  Iterate on it cold; settle any disagreements.
2. **Move Parley out of `:core`** into `:bridges:parley`.  This is the architectural commitment.  `:core` loses its network code.  Update tests and dependencies.  Build green.
3. **Move NoiseTunnel** out of `:core/network/parley/` into `:bridges:noise`.  Or hold off if we're rewriting it as a Netty handler anyway.
4. **Build `:bridges:tls` with `TlsTunnel`** using Netty SslHandler in EmbeddedChannel.  Stock cert config (system trust store, caller-provided SslContext).  No Vault integration yet.  Tests using self-signed certs generated by Netty's `SelfSignedCertificate` helper.
5. **Design the cert sememe.**  Write it up.  Implement the type.  Add CESR-like ingestion (parse X.509, emit CG record).  Add the trust-resolution query.
6. **Build the JCA Provider** for Vault-delegated signing.  Test against the Vault.  Plug into `SslContext` for the server cert case.
7. **Rebuild Parley as a Netty pipeline.**  `ParleyFramer` (CBOR streaming parse), `CodecHandshakeHandler`, `ParleyDispatcher`.  Wire to the Librarian's submit/fetch API.  Tests over LoopbackTunnel (EmbeddedChannel pair), TcpTunnel, and TlsTunnel.
8. **Refactor `:bridges:http`** for whichever Tunnel-relationship model we settle on.  Add HTTPS support via the TLS path.
9. **Start KERI** on top of the now-finished foundation.  See [`bridges.md`](bridges.md) and a future KERI-specific design doc.

Steps 2-8 are the refactor.  Estimated rough order of magnitude: two to three weeks of focused work, longer if cert sememe design surfaces subtle issues (it might).

## Decisions, in one place

For quick reference when the work starts:

- ✅ Full Netty as the network fabric on the JVM.
- ✅ Parley moves to `:bridges:parley`.
- ✅ Tunnel stays as the bridge-boundary contract; ChannelPipeline is the in-bridge tool.
- ✅ TLS is a Tunnel wrapper (`TlsTunnel`), not an HTTP feature.
- ✅ Cert chains are CG records; trust is a graph query.
- ✅ Vault delegation via JCA Provider.
- ✅ AID-as-cert-key for KERI (self-signed, no CA).
- ✅ `:core` stops containing network code.
- ⏳ HTTP-Tunnel relationship — pipeline composition vs. Tunnel composition.
- ⏳ Cert sememe shape — design needed.
- ⏳ JCA Provider location — `:core` provisionally.
- ⏳ Noise impl choice.
