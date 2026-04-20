# Blockchains on Common Graph

A blockchain is a hash-linked chain of signed assertions, replicated among peers, with application-layer rules about which chain is authoritative.  Every one of those properties is native to Common Graph.  The infrastructure that blockchain projects typically build from scratch — peer-to-peer networking, content-addressed storage, signing and verification, hash-linked history — is what CG already provides for every item in the graph.  What remains for a blockchain application to supply is its own consensus rule and its own domain-specific validation logic.

The consequence: any existing blockchain can, in principle, be ported to CG as an application; any new ledger can be designed with CG's primitives carrying most of the weight.  Many independent ledgers coexist in the same graph, each replicated only to the peers who care about it, each interpretable by anyone who holds its rules.  "Running a blockchain" becomes a matter of writing the interpretation code and letting the substrate carry the rest.

This document sketches how.

## What CG already provides

A blockchain project typically needs:

| Need | CG primitive |
|---|---|
| Content-addressed transactions and blocks | Frames and items identified by CID |
| Cryptographic signatures on assertions | Every frame is signed by its author |
| Peer-to-peer transaction and block propagation | The peer network and its trust-based routing |
| Hash-linked chain of blocks | The `FOLLOWS` role, native to the vocabulary |
| Append-only history per participant | Manifest versioning via VIDs |
| Replication to interested parties | Frame and item sync between trusting peers |
| Verifiable integrity | Content-addressing, with hashes verified locally |

The listed items cover most of what a typical blockchain white paper spends pages on.  They are already present, already implemented, and reused by every other item in the graph.  A ledger application inherits them by being an item.

## Porting an existing blockchain: Bitcoin as worked example

Bitcoin is the canonical case.  It is well-specified, widely understood, and uses exactly the kind of primitives CG provides.  The mapping is direct.

### Transactions as frames

A Bitcoin transaction is a signed assertion that some previous unspent output should now be owned by a new address:

```
TRANSACTION {
  (AGENT)             = sender-address,          # an item (pubkey hash)
  (RECIPIENT)         = recipient-address,       # an item (pubkey hash)
  (VALUE, BTC)        = 0.5,                     # amount with unit
  (INSTRUMENT)        = [utxo-ref-1, utxo-ref-2] # spent inputs (frame CIDs)
  (VALUE, SCRIPT_SIG) = <bytes>                  # authorizing script
}
```

The frame is signed by the sender.  The `INSTRUMENT` bindings reference previous transaction outputs by CID — structurally identical to Bitcoin's `prev_out` references, because CG's content-addressing gives every transaction output a stable hash identifier.  Addresses are items; multiple transactions involving the same address accumulate around it through CG's normal indexing.

### Blocks as frames with chain structure

A Bitcoin block references the previous block and contains a Merkle tree of transactions:

```
BLOCK {
  (THEME)             = bitcoin-chain,             # the ledger item
  (FOLLOWS)           = previous-block-cid,        # parent in the chain
  (VALUE, TXS)        = [tx-cid-1, tx-cid-2, ...],
  (VALUE, MERKLE)     = merkle-root,
  (VALUE, NONCE)      = nonce,
  (VALUE, TIMESTAMP)  = 2026-04-20T14:22:00Z,
  (VALUE, DIFFICULTY) = target
}
```

The `FOLLOWS` binding is Bitcoin's `prev_hash`, structurally.  This is the same hash-linked chain Bitcoin uses; CG contributes the primitive rather than re-inventing it.  The block frame is also signed by the miner who produced it — Bitcoin doesn't require this, but CG makes it essentially free, and it improves attribution without changing consensus semantics.

### The ledger item

The Bitcoin chain is an item with a stable IID.  Its manifest grows over time as new blocks are endorsed.  Multiple peers each hold their own view of this item; the mechanism by which they converge on a canonical chain is the application's consensus rule, not a substrate property.

### Proof-of-work at the application layer

Bitcoin's consensus rule — "longest valid chain weighted by accumulated proof-of-work" — is an interpretation rule the Bitcoin-on-CG runtime applies.  When a new `BLOCK` frame arrives, the runtime:

1. Verifies the block's CID (CG does this natively).
2. Verifies the miner's signature (CG does this natively).
3. Computes the block's hash and checks it against the difficulty target (application-specific).
4. Verifies each transaction's signatures, inputs, and script semantics (application-specific).
5. Walks the `FOLLOWS` chain and determines whether this block extends the currently-preferred fork (application-specific).
6. Updates the UTXO set accordingly (application-specific).

Steps 1-2 are substrate-provided; 3-6 are the Bitcoin consensus logic, which lives in the application.  A user running Bitcoin-on-CG runs this logic over the frames they hold.

### UTXO state

The UTXO set is not stored as CG state.  It is derived by walking the chain.  A Bitcoin runtime maintains a local UTXO index (exactly as Bitcoin nodes do today) computed from the frames it has accepted.  This is an optimization, not a requirement: the authoritative data is the block chain; the UTXO set is a view.

### What a Bitcoin user actually does on CG

1. Replicates the Bitcoin chain item, including the blocks and any pending transactions they choose to accept.
2. Holds their own addresses as items with keys.
3. Broadcasts transactions as signed frames through the peer network.
4. Runs the Bitcoin runtime to validate incoming blocks and maintain the UTXO view.
5. Interacts through a wallet application that reads from the runtime and composes new transactions.

None of this is unusual; it is what Bitcoin users do today, without the dedicated node software.  The node's responsibilities are absorbed: storage by CG, networking by CG, identity by CG; Bitcoin-specific validation by the runtime.

## Writing new ledgers

The real leverage shows up when designing ledgers that don't have to match Bitcoin's assumptions.  CG's primitives support several families of ledger design, each with different trade-offs and threat models.

### Proof-of-work ledgers

As in Bitcoin: blocks validated by computational expenditure, chain selection by accumulated work.  Suited to permissionless, anonymous participation where no trust assumptions hold.  The application carries the PoW-verification and chain-selection logic.

### Proof-of-stake ledgers

Validators are identified by an economic stake.  CG's signing identity is already keypair-based, so a stake-bound identity is just an item whose `STAKE` frame pledges some value.  Block production is validated against stake ratios; slashing rules are enforced by the application when validators misbehave.

### Byzantine-fault-tolerant ledgers

A fixed committee of validators each signs blocks; the ledger accepts a block once a supermajority of committee signatures accumulate.  This maps directly onto CG: a `BLOCK` frame accrues `ENDORSE` frames from committee members (frames targeting frames), and the application considers a block final once enough endorsements are present.  Hashgraph-style virtual-voting protocols can be run over the gossip pattern CG already provides, without requiring a separate BFT transport.

### Trust-matrix finality

Within a community whose participants already trust each other through CG's trust matrix, a transaction can be considered final once enough trust-weighted endorsements accumulate.  This is not BFT in the formal sense, but it suits social-scale ledgers where the economic threat model is different — informal IOUs in a family, tickets in a club, internal accounting in a cooperative, shared expenses in a household.

### Probabilistic-aggregate ledgers

Some ledger-like systems don't need per-transaction finality; they need running totals that are approximately correct.  A donation tracker, a community-funded budget, a crowdsourced measurement system — all can use `TALLY` frames backed by HyperLogLog sketches (already first-class in CG) to estimate aggregates without requiring every participant to see every transaction, with known error bounds.

### Permissioned supply-chain ledgers

A closed set of participants signs frames describing state transitions — a product moving between custodians, a document moving through approval steps, a signature chain on a contract.  Because every frame is signed and every item carries an authorship trail, the ledger is auditable without blockchain-specific infrastructure.  No consensus is needed if only one party is authoritative for each step; the ledger is simply the ordered history of endorsements, verifiable by any auditor who holds the participants' keys.

## Multiple ledgers, one graph

The substrate does not privilege any ledger.  A user who participates in Bitcoin, in a local community currency, and in a DAO's governance log holds three different items, each replicated according to who they share them with, each interpreted by its own runtime.  A single Librarian hosts all three; a single identity signs into all three if the user chooses; a single trust graph routes all three.

This collapses what today are separate infrastructures.  Ethereum has its own clients, its own relays, its own explorers; so does every other chain.  In CG, the clients are interpretation libraries, the relays are the ordinary peer network, and the explorers are frame queries against the ledger item.  The per-ledger infrastructure burden is the interpretation code; everything else is shared.

A corollary: starting a new ledger costs comparatively little.  The minimal thing needed is a predicate vocabulary, a validation rule, and a consensus choice.  The network, replication, storage, and identity layers are inherited.  The bar to experimenting with new ledger designs drops from "build a blockchain" to "declare a vocabulary and write an interpreter."

## Interop via content-addressing

Cross-chain interop is today an industry unto itself, mostly focused on bridges that are structurally adversarial: hold the coin on one chain, mint a wrapped version on another, hope the bridge operator doesn't fail or defect.  The Poly Network, Ronin, Wormhole, and Nomad hacks collectively cost billions of dollars precisely because bridges are independent trust attachments that don't share a substrate with the chains they bridge.

In CG, a transaction on ledger A can reference a transaction on ledger B directly, because both are identified by CIDs in the same namespace.  A community-currency transaction can carry a binding `(EVIDENCE) = bitcoin-tx-cid` proving that a Bitcoin payment was made as consideration.  A supply-chain record can reference a payment on any ledger the parties use.  An escrow arrangement can span two ledgers without requiring either to know about the other, because both are just CIDs to the escrow logic.

This does not automatically make cross-chain *trust* easy — if ledger A's transaction is only valid under ledger A's consensus rules, a participant in ledger B still has to reason about whether to accept that claim.  But the data-layer interop is free.  Cross-ledger arrangements no longer require bridging infrastructure; they require only shared interpretation of what each ledger's CIDs mean.

## What CG does not provide

CG does not supply a consensus protocol.  It does not decide, for any ledger item, which of two divergent chains is "correct."  It does not enforce transaction semantics, validate scripts, or maintain state machines.  It does not pick validators, compute rewards, or adjust difficulty.  All of that is the ledger application's responsibility.

CG also takes no position on economic models.  A ledger can be free-to-participate, fee-metered, proof-of-burn, or anything else; CG's peer network is trust-gated (not market-gated) at the substrate layer, and any economic logic above that is an application concern.

What CG does supply — content-addressing, signatures, peer propagation, hash-linked history, semantic structure, trust-based routing, cross-item referencing — is what every ledger needs as foundations.  The application supplies what makes it *that particular ledger*.

## Implementation notes

A blockchain-on-CG runtime is, structurally, a vocabulary contribution plus an interpreter:

- **Vocabulary**: archetypes (`BITCOIN_CHAIN`, `ETHEREUM_CHAIN`, ...), predicates (`TRANSACTION`, `BLOCK`, `STAKE`, `ENDORSE`, ...), and any role sememes the ledger needs that aren't already in the shared vocabulary.
- **Interpreter**: verification logic per predicate, the chain-selection rule, state-tracking (UTXOs, account balances, nonce tracking), and whatever RPC surface external wallets need.

The interpreter is itself an item in the graph.  Users who wish to participate in a ledger acquire its vocabulary item and its interpreter item, trust them to some degree, and run them.  This is exactly how any other code distribution works on CG — see the discussion of code-as-items in §9 of [the paper](the-case.md).

Existing blockchain codebases can, in many cases, be ported with modest effort.  The cryptographic primitives (ECDSA, SHA-256, Keccak) map to libraries.  The consensus logic ports largely unchanged.  What drops away is the bespoke P2P, the bespoke serialization, the bespoke storage — several layers of infrastructure that every blockchain project re-implements from near-scratch.  The remaining code is the specific validation and consensus logic that makes the ledger *this* ledger rather than some other.

## A note on what this doesn't mean

This document describes a capability, not an endorsement.  CG does not need blockchain applications to justify itself, and the architecture takes no position on whether any particular ledger is a good idea.  Blockchains have real uses (auditable supply chains, closed-community ledgers, disintermediated payment between strangers in some cases) and real costs (energy consumption for PoW, economic capture by large stakers for PoS, the general tendency for cryptocurrency communities to attract speculation and scams).  CG is agnostic about those trade-offs.

The point of this document is narrower: the substrate that CG provides happens to absorb the infrastructure layer most blockchains reinvent, which means the ledger design space opens up considerably once CG exists.  Whether that leads to more good ledgers, more bad ledgers, or neither is a social and economic question, not an architectural one.
