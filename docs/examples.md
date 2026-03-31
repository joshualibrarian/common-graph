# Use Case Examples

How Common Graph handles common application patterns.

## Chat Room

A chat room is an Item with:
- **Roster frame** — Participant list (stream)
- **Chat frame** — Message log (stream)
- **Title assertion** — Room name
- **Presence** — Who is here right now

```
chatRoom {
    assertions:
        TITLE { THEME:[]→chatRoom, NAME:[]→"Project Discussion" }

    durable frames:
        MESSAGE { AGENT:[]→Alice, LOCATION:[]→chatRoom, TOPIC:[]→"Hello!" }

    ephemeral frames (LATEST retention, in-memory only):
        PRESENT { AGENT:[]→Alice, LOCATION:[]→chatRoom,
                  TOPIC:[VIDEO]→video_chain, TOPIC:[AUDIO]→audio_chain }
        PRESENT { AGENT:[]→Bob, LOCATION:[]→chatRoom }
        TYPING  { AGENT:[]→Bob, LOCATION:[]→chatRoom, CONTENT:[]→"Hey th..." }
}
```

Messages are signed frames on the chat room item. PRESENT frames indicate who is currently in the room — rendered as an avatar in 3D, a participant list in 2D, or `[Alice, Bob]` in text. TYPING frames (LATEST retention, not persisted) show live typing indicators. When Alice disconnects, her PRESENT frame is revoked and others see her leave. PRESENT frames can carry TOPIC stream bindings for video/audio feeds.

There is no separate "online status" system. Presence is per-item: Alice can be present in this chat room and absent from another. Her availability to Bob is the set of shared items where she has active PRESENT frames.

## Group / Organization

A group is an Item with:
- **Roster frame** — Members with roles
- **Trust policy frame** — Who can do what
- **Owned items** — Via assertions

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

Moves are signed frames. State can be recomputed from move history.

Presence in a game is the same mechanism as presence in a chat room:

```
PRESENT      { AGENT:[]→Alice, LOCATION:[]→game:Chess123 }
PRESENT      { AGENT:[]→Bob, LOCATION:[]→game:Chess123 }
FOCUS        { AGENT:[]→Alice, LOCATION:[]→game:Chess123, THEME:[]→square:e4 }
CURSOR       { AGENT:[]→Bob, LOCATION:[]→game:Chess123, POSITION:[X]→3, POSITION:[Y]→4 }
```

FOCUS and CURSOR are ephemeral (LATEST retention) — Alice can see Bob's cursor hovering over a piece in real-time, whether rendered as a 3D hand, a 2D highlight, or `[Bob is looking at e4]` in text.

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
- **Options frame** — Available choices
- **Votes frame** — Cast votes (stream)
- **Eligibility assertion** — Who can vote

```
poll:Budget2024 → hasOption → "Option A: Increase spending"
poll:Budget2024 → hasOption → "Option B: Maintain current"
poll:Budget2024 → eligibleVoter → group:BoardMembers
```

Votes are signed assertions: `user:Alice → votesFor → "Option A" { poll: poll:Budget2024 }`

## Commerce

A product listing is an Item with:
- **Description frame** — Product details
- **Price assertion** — Current price
- **Inventory assertion** — Stock level
- **Images** — Mounted media frames

```
product:Widget123 → hasPrice → quantity(29.99, USD)
product:Widget123 → inStock → 42
product:Widget123 → soldBy → merchant:AcmeStore
```

Orders are Items linking buyer, seller, products, and payment.

## File Sharing

A shared folder is an Item with:
- **Mounted frames** — Files at paths
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

Moderation is expressed through signed assertions:
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

## Shared Spaces (Real-Time Collaboration)

Any item can be a shared space — a place where multiple users are simultaneously present and interacting in real time.

### The PRESENT Frame

Entering a space means creating a PRESENT frame:

```
PRESENT {
    AGENT:[]           → me              (who — identity)
    LOCATION:[]        → space_item      (where — identity)
    TOPIC:[VIDEO]      → video_chain     (optional video feed — stream)
    TOPIC:[AUDIO]      → audio_chain     (optional audio feed — stream)
}
```

PRESENT is a normal durable signed frame. It's the anchor for your entire participation.

### Ephemeral State

High-frequency state uses predicates with LATEST retention (replaced on each update, never persisted, discarded on disconnect):

```
AVATAR_STATE  { AGENT:→me, LOCATION:→space, POSITION:[X]→3.2, POSITION:[Y]→1.0, POSITION:[Z]→-5.7 }
TYPING        { AGENT:→me, LOCATION:→space, CONTENT:→"partial text..." }
CURSOR        { AGENT:→me, LOCATION:→space, POSITION:[X]→120, POSITION:[Y]→340 }
FOCUS         { AGENT:→me, LOCATION:→space, THEME:→some_item }
```

These are still frames — same vocabulary, same roles, same subscription delivery, same scene rendering. The Library handles them differently based on the predicate's lifecycle policy.

### Three Temporal Modes

| Mode | Retention | Examples |
|---|---|---|
| **Durable** | ALL (persisted, signed, endorsed) | PRESENT, MOVE, MESSAGE, TITLE |
| **Ephemeral** | LATEST (in-memory only, replaced per key) | AVATAR_STATE, TYPING, CURSOR, FOCUS |
| **Streaming** | CHAIN (accumulating content-addressed chunks) | Video feed, audio feed, screen share |

### Cross-Fidelity

The same PRESENT + AVATAR_STATE frames render differently per platform:

- **3D (Filament)**: Avatar model at position/orientation in a spatial environment
- **2D (Skia)**: Icon in participant sidebar, cursor highlight on shared content
- **Text (TUI)**: `[Alice entered]`, `[Bob is typing...]`

The renderer reads frame bindings. It doesn't know or care about lifecycle policy.

### Multiple Presence

A user can be PRESENT in multiple items simultaneously — each with independent presence state. Present in a group chat AND a chess game AND a shared document, each showing different participants, different ephemeral state, different renderings.

### No Global Status

There is no "online/offline" system. Availability is contextual: you are present *in* specific items. Your visibility to another user is the set of shared items where you both have active PRESENT frames. A "buddy list" is just a query across shared items for PRESENT frames.

## Code Distribution

Code is content. A developer publishes a new frame type (a Kanban board, a tax calculator, a game) as an Item carrying `BytecodeComponent` or `ScriptComponent`. Users discover it through the social graph, and their Librarian loads it — trust-gated, content-addressed, hot-swappable.

```
codeItem:KanbanBoard
    signer: carol
    frames:
        BytecodeComponent:
            mainClass: dev.carol.kanban.KanbanBoard
            targetVersion: 21
        SurfaceTemplate: (board UI)
    assertions:
        PROVIDES_TYPE → cg:type/kanban-board
        HAS_VERB → cg.verb:create, cg.verb:move
```

No package manager. No app store. No install step. The social graph curates what code you trust. See [Scripting](scripting.md) for the full model including `GraphClassLoader`, trust thresholds, and sandboxing.

## Accounting / Ledger

A ledger is an Item with:
- **Transaction log** — Append-only stream frame
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
