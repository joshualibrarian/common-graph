# Use Case Examples

How Common Graph handles common application patterns.

## Chat Room

A chat room is an Item with:
- **Roster component** — Participant list (stream)
- **Chat component** — Message log (stream)
- **Title relation** — Room name

```
chatRoom {
    components:
        roster: Roster (stream)
        chat: ChatLog (stream)
    relations:
        TITLE { theme: chatRoom, target: "Project Discussion" }
}
```

Messages are signed entries in the chat stream. Roster changes are signed entries in the roster stream.

## Group / Organization

A group is an Item with:
- **Roster component** — Members with roles
- **Trust policy component** — Who can do what
- **Owned items** — Via relations

```
HAS_MEMBER { theme: group:RainbowOps, target: user:Alice, role: "admin" }
HAS_MEMBER { theme: group:RainbowOps, target: user:Bob, role: "member" }
OWNS       { theme: group:RainbowOps, target: item:SharedDocs }
```

## Private Messaging

Direct messages between two users:
- **DM Item** — Shared between sender and receiver
- **Roster** — Exactly two participants
- **Chat stream** — Encrypted for participants only

Encryption keys derived from participant public keys.

## Games

A game is an Item with a `GameComponent<Op>` (extends `Dag<Op>`) and composes behavior through trait interfaces:

| Trait | Purpose | Examples |
|-------|---------|---------|
| **Spatial** | Board/grid geometry and coordinates | Chess, Minesweeper |
| **Zoned** | Named regions (hand, deck, discard) | Set, Poker, Spades |
| **Scored** | Point tracking | Set, Minesweeper, Yahtzee |
| **Phased** | Turn/phase management | Poker, Spades |
| **Randomized** | Deterministic RNG | Dice, card shuffles |

```
HAS_PLAYER { theme: game:Chess123, target: user:Alice, color: "white" }
HAS_PLAYER { theme: game:Chess123, target: user:Bob, color: "black" }
USES_RULES { theme: game:Chess123, target: item:ChessRulesV1 }
```

Moves are signed stream entries. State can be recomputed from move history.

### Implemented Games

| Game | Traits | Description |
|------|--------|-------------|
| **Chess** | Spatial | Full chess with 3D pieces (GLB models), board rendering, clock |
| **Minesweeper** | Spatial, Scored | Grid-based mine clearing with flag/chord |
| **Set** | Zoned, Scored | Card pattern matching |
| **Poker** | Zoned, Phased, Scored, Randomized | Texas Hold'em with betting rounds |
| **Spades** | Zoned, Phased, Scored, Randomized | Trick-taking card game with bidding |
| **Yahtzee** | Zoned, Phased, Scored, Randomized | Dice game with scoring categories |
| **Dominoes** | Zoned, Phased, Scored, Randomized | Tile matching game |

### Playing Cards

A `PlayingCard` is a Canonical with rank, suit, and multi-fidelity rendering:
- **3D**: SVG card face from Tek Eye playing card assets
- **2D**: SVG or Unicode card symbols (🂡 🂢 etc.)
- **CLI**: Text representation ("A♠", "K♥")

Card games share a `Deck` abstraction built on the Zoned trait — zones represent hand, deck, discard pile, table, etc.

## Voting / Polls

A poll is an Item with:
- **Options component** — Available choices
- **Votes component** — Cast votes (stream)
- **Eligibility relation** — Who can vote

```
poll:Budget2024 → hasOption → "Option A: Increase spending"
poll:Budget2024 → hasOption → "Option B: Maintain current"
poll:Budget2024 → eligibleVoter → group:BoardMembers
```

Votes are signed relations: `user:Alice → votesFor → "Option A" { poll: poll:Budget2024 }`

## Commerce

A product listing is an Item with:
- **Description component** — Product details
- **Price relation** — Current price
- **Inventory relation** — Stock level
- **Images** — Mounted media components

```
product:Widget123 → hasPrice → quantity(29.99, USD)
product:Widget123 → inStock → 42
product:Widget123 → soldBy → merchant:AcmeStore
```

Orders are Items linking buyer, seller, products, and payment.

## File Sharing

A shared folder is an Item with:
- **Mounted components** — Files at paths
- **Access roster** — Who can read/write

```
folder:SharedDocs/
├── README.md
├── design/
│   └── architecture.pdf
└── .item/
    └── ...
```

Sync is automatic between participants who have the item.

## Moderation

Moderation is expressed through relations:
- Reports: `user:Alice → reports → post:123 { reason: "spam" }`
- Actions: `moderator:Bob → hides → post:123 { reason: "confirmed spam" }`
- Appeals: `user:Charlie → appeals → action:456`

Moderation policies are Items defining thresholds and rules.

## Anonymity

Anonymous posting via **proxy signers**:
- Create a throwaway signer item
- Sign content with throwaway key
- Optionally: later prove ownership by revealing link

Or via **mixing**:
- Content encrypted to group
- Multiple hosts relay
- No single point knows author + content + recipient

## Code Distribution

Code is content. A developer publishes a new component type (a Kanban board, a tax calculator, a game) as an Item carrying `BytecodeComponent` or `ScriptComponent`. Users discover it through the social graph, and their Librarian loads it — trust-gated, content-addressed, hot-swappable.

```
codeItem:KanbanBoard
    signer: carol
    components:
        BytecodeComponent:
            mainClass: dev.carol.kanban.KanbanBoard
            targetVersion: 21
        SurfaceTemplate: (board UI)
    relations:
        PROVIDES_TYPE → cg:type/kanban-board
        HAS_VERB → cg.verb:create, cg.verb:move
```

No package manager. No app store. No install step. The social graph curates what code you trust. See [Scripting](scripting.md) for the full model including `GraphClassLoader`, trust thresholds, and sandboxing.

## Accounting / Ledger

A ledger is an Item with:
- **Transaction log** — Append-only stream
- **Balance computations** — Derived from log

```
ledger:HouseholdBudget
├── transactions (stream)
│   ├── Entry: +500 income
│   ├── Entry: -50 groceries
│   └── Entry: -100 utilities
└── balances (computed snapshot)
```

Entries are signed and timestamped. Balances are materialized views.
