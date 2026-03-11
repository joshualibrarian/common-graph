# Workspace and Session

How users see and interact with items on screen.

## Core Principle: Text Is Primary

The expression input is the **primary** interaction mode. All spatial operations (drag, drop, resize, rearrange) map to semantic frames — they're syntactic sugar for typed commands. When stored (in an activity log, undo history, macro), actions are stored **resolved** as semantic frames, unambiguous regardless of how they were triggered.

```
# Typed:
place chess in main

# Dragged:
(user drags chess handle to "main" region → same resolved frame)
```

Both produce the same `SemanticFrame(PLACE, {THEME: chess, TARGET: main})`.

## Views Are Mounts

A **view** is an on-screen presence of an item. Under the hood, a view is a **reference stub** frame on the session item, mounted in a display or in another item's surface region. No special "view" data structure — it's the existing frame + mount system.

When you `view chess-club`:
1. The session creates a **reference frame** pointing to chess-club
2. That reference gets **mounted** on the current display (or in a workspace region)
3. The reference frame carries session-local state: internal focus, actor, view preferences

The same item can have multiple reference stubs → multiple views on screen. Each is an independent mount.

## Hosts and Displays

Displays belong to **host items** (librarians/devices). A host enumerates its hardware — displays, input devices, and eventually services, containers, system resources:

```
Host Item (macbook-pro)               Host Item (iphone)
├── display/builtin   ← laptop screen ├── display/builtin ← phone screen
├── display/external-1 ← monitor      ├── vault
├── display/external-2 ← monitor      └── key-log
├── display/tv
├── vault
└── key-log
```

When a host participates in a session, its displays become available as mount targets. Each display frame has a surface representing its screen real estate.

### Multiple Librarians Per Machine

A machine can run multiple librarians:

```
Machine
├── system librarian         ← always-on daemon, system-level
│   └── sessions...
├── alice's librarian        ← user-space, alice controls
│   └── sessions...
└── bob's librarian          ← user-space, bob controls
    └── sessions...
```

Each librarian is its own process, its own keys, its own library. They peer locally using the same protocol used across the internet — just over a Unix socket instead of TCP. The system librarian can receive deliveries for alice while she's away and hand them over when her librarian comes up. No special local-only communication — it's all normal peer sync.

Most casual users: one librarian. Privacy-conscious users or multi-user machines: one per user, plus optionally a system librarian.

## Session Structure

The session item composes hosts, reference stubs, and session state:

```
Session Item
├── ref/macbook-pro       ← host reference (brings displays)
├── ref/iphone            ← host reference (brings display)
├── ref/chess-club        ← view reference stub, mounted on a display
├── ref/notes             ← view reference stub, mounted on a display
├── activity-log          ← session interactions
└── roster                ← authenticated users
```

The session's surface composes the available displays from its hosts. Reference stubs are mounted into display surfaces (at positions or in named regions). All of this persists — restart the session and your views reappear where you left them.

## Two Modes of Screen Use

### Float Mode (Default)

Reference stubs mounted at **free positions** on a display surface. Items appear as floating handles — no OS window chrome, no title bars. Just the item's handle hovering on the background, with its scene extending below when expanded.

- `view chess-club` → reference stub mounted at a free position on the active display
- `view notes` → another reference stub alongside it
- Nothing replaces anything. There is no "back."
- Drag handles to reposition. Resize them. Dismiss them when done.
- Each view is independent — its own focus, its own actor.

### Nested Layout

An item mounted on a display can have OTHER items mounted in ITS surface regions. This is how you get organized layouts:

```
display/external-1
└── ref/my-dashboard                ← an item with surface regions
    ├── main → ref/chess-club
    ├── sidebar → ref/notes
    └── footer → ref/activity-log
```

This is what "workspace" was in earlier iterations — but it doesn't need a special name or flag. It's just nesting mounts. Any item with surface regions can have other items placed in its regions. The `place` verb handles both:

```
place chess-club in main           # mount in a region of the focused item
place chess-club on external-1     # mount directly on a display
```

Same verb, same mount system, different target.

### Mixing Modes

Both modes coexist freely. A layout item filling one monitor, floating items on another. Multiple layout items side by side. The session tracks all of it — every mount persists across restarts.

Whether floating items and layouts are implemented as actual OS windows or as simulated windows inside one large OS surface is an implementation detail. The experience is identical either way.

## Vocabulary

| Expression | What it does |
|---|---|
| `view X` | Focus existing view of X, or create reference stub + mount if none |
| `view X again` | Always create a new reference stub + mount |
| `create view of X` | Explicit form: create reference stub + mount |
| `create view of X in main` | Create + mount in "main" region |
| `focus X` | Shift input to existing view. Fails if not on screen. |
| `dismiss X` | Unmount reference stub from display |
| `dismiss` | Unmount the currently focused view |
| `place X in main` | Move mount to "main" region |
| `place X on external-1` | Move mount to a display |
| `fullscreen X` | Expand to fill current display |
| `create X` | Create frame on current item |
| `move X to Y` | Move frame between items |

All reuse existing verbs: `create`, `place`, `move`, `focus`, `dismiss`, `view`. Only `view` and `dismiss` are new sememes. `view` is a real verb on the session (`@Verb(Sememe.VIEW)`) — it parses normally as `SemanticFrame(VIEW, {THEME: chess-club})` with smart focus-or-create behavior in the verb implementation.

### Within a View

When you're looking at a view, you can browse its item's frames via the tree or keyboard. Selecting a frame shows its scene in the view's detail area. This is **intra-view navigation** — you never leave the view. The on-screen presence stays put, only its internal focus changes.

```
# Looking at chess-club view:
# - Browse games in the tree
# - Select a game → see the board in the detail area
# - Select the roster → see members in the detail area
# All within the chess-club's view
```

### Gestures

- **Click** a handle → `focus` (shift input there)
- **Expand** a handle → show the item's scene
- **Collapse** → back to just the handle
- **Drag** a handle → reposition (float) or move between regions (layout)
- **Drag out** of a region → detach to floating on display

### Disambiguation

When an item has multiple views on screen, bare references are ambiguous. Resolution:

1. If only one view → use it
2. If multiple → prompt the user to pick (or use hints: `focus chess-club left`, `focus chess-club in main`)
3. Auto-complete shows view count badges when ambiguous

## Session Item

The session item (`cg:type/session`) is the outermost scope of user interaction. It is **not** necessarily visible — it's always present conceptually (for dispatch, auth, activity logging) but only on screen if the user views it. Viewing the session item shows its surface: a bird's-eye layout of all displays and mounted views.

The session manages:

- **Host references** — which devices participate (and thus which displays are available)
- **View reference stubs** — on-screen items with their mount locations and session-local state
- **Authenticated users** — who has proven key possession (challenge-response)
- **Activity log** — a Log frame recording all session interactions

### Multi-User

A session can have multiple authenticated users (like browser profiles). Different views can have different active actors — alice plays chess in one view while bob browses notes in another. The prompt shows `actor@context>` per-view.

### Multi-Device

A session references multiple hosts. Each host brings its displays. All hosts share the same session item and coordinate on what's shown where. Each host renders the views mounted on its displays.

Two hosts sharing a session doesn't mean they share a library. They're independent librarians, peers, coordinating on **presentation**. Content replicates as needed through the normal [discovery](network.md) mechanism.

Dragging a view from your monitor to your phone = remounting the reference stub from one display to another. Both librarians see the session update.

## System Tray

The system tray is owned by the **host**, not any librarian or session. One machine = one host = one tray icon. All librarians running on the host appear under it:

```
🖥 macbook-pro                          ← the host (one per machine)
├── 📚 alice's librarian                ← librarian 1
│   ├── 12,450 items · 2.3 GB
│   ├── 🔗 peers: bob-desktop
│   ├── ▶ work (session)
│   │   ├── 👤 alice
│   │   ├── 📋 project-dashboard
│   │   └── ♟ chess
│   └── ▶ personal (session)
│       ├── 👤 alice
│       └── 📺 startrek-shitposting
├── 📚 system librarian                 ← librarian 2
│   ├── 340 items · 120 MB
│   └── (no active sessions)
├── ──────────
├── New Session...
└── Quit
```

The host owns the tray because the host owns the displays. Librarians are tenants on the host's hardware. The host negotiates display access between sessions from different librarians.

Clicking a view entry focuses it. The tray icon badge or color indicates host state: all healthy (green), syncing (blue), offline (gray), error (red).

## Items, Frames, and Mounting

Three distinct concerns:

| Concern | What it means |
|---------|---------------|
| **Composition** | A frame belongs to an item (in its frame table) |
| **Mounting** | A frame is assigned to a region in an item's surface |
| **Viewing** | A reference stub is mounted on a display (making the item visible) |

These are independent:
- A frame can exist without being mounted (in the tree, not in a surface region)
- A mounted frame is visible when you're looking at that item's root view
- An item can exist in the graph without any views (no reference stubs mounted anywhere)

Viewing is just mounting at a different scope. Same mechanism.

### Creation

`create chess between alice and bob` creates a chess component on the **current item** (the item that has focus in the current view). The chess game appears in the item's tree immediately.

To also view it: `create chess between alice and bob and view`

Where new things appear depends on context:
- Focused inside an item with available regions → mount in the default region
- Focused on a bare display → float at a free position

### Mounting

Mounting assigns a component to a named region in an item's surface:

```
place chess in main         # mount chess component at "main" region
place log in sidebar        # mount log at "sidebar" region
unmount chess               # remove from region (component still exists)
```

Region names are vocabulary scoped to the item that defines them. A dashboard item might register "chart-1", "chart-2", "table". Auto-completion resolves them.

### Moving

Components can be moved between items:

```
move chess to club           # transfer chess component from current item to club item
```

## Activity Logs

Two separate logs serve different purposes:

### Session Activity Log

A Log component on the session item. Records **user interactions**:

```
[14:30:01] created chess between alice and bob
[14:30:05] placed chess in main
[14:30:12] alice moved e2e4
[14:31:44] viewed chess-club
```

The latest entry flashes as feedback for any action. The activity log can be viewed like any component (focus on it in the session item's tree, or mount it in a region).

### Librarian Activity Log

A Log component on the librarian item. Records **infrastructure events**:

```
[14:30:01] stored manifest for chess-game-7f3a...
[14:30:02] indexed 3 relations (hasPlayer, hasPlayer, typeOf)
[14:30:45] synced 12 blocks with peer librarian:bob-desktop
```

These overlap — creating something touches both. But they serve different audiences: the session log is for the user, the librarian log is for the system.

## Compound Expressions

The conjunction "and" chains verb clauses. The result of the first clause becomes the implicit THEME of the next:

```
create chess between alice and bob and view
│                                 │   │
│  clause 1: CREATE               │   clause 2: VIEW
│  THEME: chess type               │   THEME: (result of clause 1)
│  COMITATIVE: [alice, bob]        │
└──────────────────────────────────┘
```

This is the same "and" sememe used for noun conjunction ("alice and bob"). The FrameAssembler detects verb-level conjunction and produces a chain of frames.

## Example Layouts

A session item surface composes displays and mounted views:

```
┌──────────────────────────────────┐  ┌──────────────────┐
│ display/external-1               │  │ display/builtin   │
│                                  │  │                   │
│  ┌─ ref/my-dashboard ──────────┐ │  │  ref/chess-club   │
│  │ main: ref/project-board     │ │  │  ref/music        │
│  │ sidebar: ref/team-chat      │ │  │                   │
│  │ footer: ref/activity-log    │ │  │                   │
│  └─────────────────────────────┘ │  │                   │
│                                  │  │                   │
└──────────────────────────────────┘  └──────────────────┘
```

Left monitor: a dashboard item with nested layout. Right monitor: floating items. All managed through the same mount system.

## References

- [Presentation](presentation.md) — Surface DSL, rendering pipeline, annotation system
- [Network Architecture](network.md) — How librarians peer and sync
- [Trust](trust.md) — Identity, signing, authentication
- [Components](components.md) — Component system, types, modes, mounts
- [Vocabulary](vocabulary.md) — Token resolution, dispatch, scope chains
