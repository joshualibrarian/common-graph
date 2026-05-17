# Working Trees

A **working tree** presents an item as an editable filesystem surface. The item's content — text, source code, images, structured data — appears as files and directories the user can open, edit, and save with any tool. Underneath, those files are projections of frames endorsed by the item's manifest; saving syncs the file's bytes back into the item.

The pattern is borrowed from Git: a repository's `.git/` directory holds the canonical content (objects, refs, packs) while the surrounding working tree is the editable surface. Common Graph generalizes it — *any* item can be materialized as a working tree, not just code repositories. Documents, photo collections, datasets, configuration items, anything whose content is filesystem-shaped works the same way.

This document defines the working-tree layout, how files map to frames, the commit flow, and where it fits in the broader architecture.

This document assumes familiarity with [items](item.md), [manifests](manifest.md), [content](content.md), and [storage](storage.md).

## Layout

A working tree is a directory. The user's editable content lives at the top level; a hidden `.item/` directory holds the item's identity, version history, and supporting metadata:

```
my-document/
├── README.md            ← editable: a Markdown frame
├── data/
│   └── readings.csv     ← editable: a CSV frame
├── images/
│   └── chart.png        ← editable: an image frame
└── .item/               ← the item's bookkeeping
    ├── iid              ← the item's identity (the IID, as bytes)
    ├── head/            ← the current working state
    ├── manifests/       ← immutable version snapshots
    ├── channels/        ← named branches (per-signer heads)
    └── objects/         ← content-addressed bytes
```

The visible files are *projections* of frames endorsed by the manifest. Their on-disk paths come from the frames' path-mount bindings; their bytes are the frames' content. Edit the file, save it, and the bytes are written back into the item's content-addressed object store and the manifest is updated to point at the new content.

## Files as frame projections

Each visible file corresponds to a frame whose manifest binding designates a path:

```
README.md projects:
  {@markdown-document, [
    @LOCATION → @<item-iid>,
    @PATH → "README.md",
    @CONTENT → ~<bytes-cid>
  ]}

data/readings.csv projects:
  {@csv-data, [
    @LOCATION → @<item-iid>,
    @PATH → "data/readings.csv",
    @CONTENT → ~<bytes-cid>
  ]}
```

The PATH binding declares where the file appears in the working tree's directory structure. The CONTENT binding's target is a content-addressed reference to the file's bytes. When the working tree materializes, the librarian fetches the bytes by CID and writes them to the path.

When the user edits the file and saves, the working tree:

1. Hashes the new bytes; gets a new ContentID.
2. Stores the new bytes in the object store.
3. Updates the frame body to point at the new ContentID.
4. Adds the new frame body to the item's working state (uncommitted).

On commit, the working state becomes a new manifest version. The old version is preserved; the new manifest is signed.

## The `.item/` directory

`.item/` is the working tree's bookkeeping — analogous to Git's `.git/`. Contents:

- **`iid`** — the raw IID bytes that identify this item.
- **`head/`** — the current uncommitted state. Includes the frames currently materialized and any pending edits.
- **`manifests/`** — every committed version's manifest body, keyed by VID. Versions accumulate; they're never overwritten.
- **`channels/`** — named references to manifest VIDs. Each channel points at a current head (the manifest considered "current" for that channel). Channels are per-signer, allowing forking and disagreement.
- **`objects/`** — the local object store for this item. Content-addressed bytes, including the bytes of edited files.

In some setups, `.item/objects/` is a private store for this item alone; in others, it's a symlink or mount into a librarian's shared object store. Either works — the working tree's contract is the same.

## Channels

The `channels/` directory holds named pointers to manifest VIDs. The default channel is conventionally `main`, but channels can have any name. Each channel is per-signer: Alice's `main` and Bob's `main` are different pointers, even if they currently coincide.

```
.item/channels/
├── main           → manifests/<vid-1>
├── draft          → manifests/<vid-2>
└── alice-edit     → manifests/<vid-3>
```

Switching channels updates the working tree's visible files. Pulling a peer's channel head updates the working tree to that peer's view. Forking is creating a new channel; merging is constructing a manifest with multiple FOLLOWS bindings pointing at multiple parent VIDs.

This is the same model Git uses for branches, generalized to per-signer ownership. Two signers' channels coexist; the working tree shows whichever the user is currently on.

## Editable surfaces

A working tree has two editable surfaces:

**The root tree** — the user's visible content. Edit any file with any tool; save; the working tree picks up the changes. This is the dominant editing path for content the user works with directly.

**The `.item/head/` overlay** — direct edits to the item's metadata. Adding a frame, removing a frame, changing a binding's qualifiers, changing path mounts. Useful for power users and tooling; not the normal path for content editing.

In most workflows, users only touch the root tree. Tooling that needs to manipulate the item's structure (renaming files, restructuring directories, adding tags) does so through commands that update `.item/head/` indirectly.

## The commit flow

Committing a working tree mints a new manifest version. The steps:

1. **Compute the working state.** Scan the root tree for changed files; check `.item/head/` for any pending frame edits. The result is the set of frames the new manifest will endorse, plus any new/modified content blobs.
2. **Store the content.** Write all new/modified content blobs to `.item/objects/` (or the shared object store). Their ContentIDs are computed; the working state's frame bodies are updated to reference them.
3. **Build the manifest.** Construct a new manifest body. Head is the item's archetype. ITEM_ID is the existing IID. FOLLOWS is the prior VID (or multiple, for merges). ENDORSES bindings point at the frame DatumIDs.
4. **Sign.** The user's signer signs the manifest body; the result is a record. Both body and record are stored.
5. **Update the channel head.** The current channel's pointer advances to the new VID.

The old version remains in `.item/manifests/` forever (subject to retention policy). The user can switch back, diff, or fork from it at any time.

## Thin vs. full

A working tree can be **thin** or **full**.

**Full** — `.item/objects/` contains every blob the item has ever referenced. The working tree is self-contained; no fallback fetch is needed for any operation.

**Thin** — `.item/objects/` contains only the current head's blobs and any edits in progress. Historical versions or sub-items referenced from the current state are fetched on demand from the librarian's main store (or, ultimately, from peer librarians).

Thin is the default for most working trees because items can hold large historical content the user doesn't actively need. Full is for working trees that need to be portable (taken offline, archived) or distributed (a peer can clone the whole item).

## Multi-item working trees

A working tree usually corresponds to one item, but the pattern composes. A `.workspace/` directory can hold many items' working trees side by side; tools that need to operate across items work in the workspace.

A specific architectural choice (which `.item/` layout, where shared object stores live, how channels per-item compose) is local to the workspace and not part of the protocol.

## Why working trees

Working trees solve the "I want to edit my content with the tools I already use" problem. A Markdown document I'm authoring should open in my editor. Images I'm curating should appear in my file browser. Code I'm writing should compile with my build tools.

Without working trees, every interaction with Common Graph's content goes through CG-specific interfaces: a CG-aware editor, a CG-aware file picker, a CG-aware build system. That's a lot of new tooling to build before users can use the system at all.

With working trees, the existing tool stack works. Edit a Markdown file with VSCode; save; the working tree picks up the change; commit. The user doesn't have to know CG is underneath; the file just behaves like a file.

This is what makes Common Graph approachable for users coming from filesystem-based workflows. They don't have to abandon their tools; they just point those tools at a working tree, and the system meets them where they are.

## Working trees vs. mounts

A working tree is one form of materialization. Other forms exist:

- **Item view in a UI** — the item rendered as a scene, not as files. The user interacts through CG-native widgets.
- **Webview** — the item rendered as HTML for browser display.
- **API surface** — programmatic access to the item's frames without filesystem projection.

Working trees are filesystem-shaped because filesystem-shaped tools are what users already have. The same item can present through any of these surfaces simultaneously; the working tree is just one option, suited to file-oriented content.

## Worked examples

**A document working tree.**

```
my-essay/
├── essay.md             ← the document's content frame
├── images/
│   └── figure1.png      ← an image frame referenced by the document
└── .item/
    ├── iid
    ├── head/
    ├── manifests/<vid-1>
    └── channels/main → manifests/<vid-1>

The user edits essay.md in their editor. Saves. The working tree
detects the change, hashes the new bytes, stages a new content blob.
On `cg commit`, the manifest version advances; main moves to <vid-2>.
```

**A code repository working tree.**

```
my-project/
├── src/main.java
├── pom.xml
├── README.md
└── .item/
    ├── iid
    ├── manifests/<vid-N>
    ├── channels/
    │   ├── main → manifests/<vid-N>
    │   └── feature → manifests/<vid-M>
    └── objects/
        └── ...

Identical to a Git repository in feel. The user runs their build tools
against the working tree; edits sources; commits. The history accumulates
in .item/manifests/; channels track parallel lines of work.
```

**A photo collection working tree.**

```
my-photos/
├── 2026/
│   ├── 01-january/
│   │   ├── IMG_1001.jpg
│   │   └── IMG_1002.jpg
│   └── 02-february/
│       └── IMG_2001.jpg
└── .item/
    ├── iid
    └── ...

The user organizes photos by drag-drop in their file browser. Saves the
arrangement; commits. The item's manifest records the new structure;
each photo is a frame with a path-mount binding pointing at its location.
```

## Relations

- [`item.md`](item.md) — the items working trees materialize.
- [`manifest.md`](manifest.md) — what each working-tree commit produces.
- [`content.md`](content.md) — content-addressed bytes of working-tree files.
- [`storage.md`](storage.md) — the object store working trees use.
- [`frames.md`](frames.md) — the frames that project as files.
- [`trust.md`](trust.md) — signing working-tree commits.
- [`network.md`](network.md) — fetching blobs from peers for thin working trees.
