# Working Trees

A **Working Tree** presents an Item as an editable filesystem surface — like a Git working directory, but for any Item.

## Structure

```
my-item/
├── README.md              # Mounted content (editable)
├── src/
│   └── main.java
├── data/
│   └── config.json
└── .item/
    ├── iid                # Item identity
    ├── head/
    │   ├── base           # Symlink to current version
    │   ├── components/    # Component descriptors (JSON)
    │   ├── mounts/        # Mount descriptors (JSON)
    │   └── actions/       # Action descriptors (JSON)
    ├── manifests/         # Immutable version snapshots
    │   └── <vid>
    ├── channels/          # Named branches
    │   ├── main -> ../manifests/<vid>
    │   └── draft -> ../manifests/<vid>
    ├── content/           # Content blocks
    │   └── <cid>
    └── relations/         # Relation data
        └── <rid>
```

## Two Editable Surfaces

### 1. Root Directory (Mounted Content)

The visible "files" are mount projections of components:
- `README.md` → component with handle "readme"
- `src/main.java` → component with handle "src/main"

Edit these files with any tool, then commit.

### 2. `.item/head/` (Metadata)

The editable metadata overlay:
- `components/*.json` — Add/modify component definitions
- `mounts/*.json` — Change path mappings
- `actions/*.json` — Declare item actions

Edit these JSON files to modify item structure, then commit.

## Base Selection

The `.item/head/base` symlink points to the current version:

```
base -> ../manifests/<vid>     # Specific version
base -> ../channels/main       # Following a channel
```

This determines what's "checked out" in the working tree.

## Component Descriptors

`.item/head/components/<hid>.json`:
```json
{
  "handle": "readme",
  "type": "cg:type/plainText",
  "identity": true,
  "snapshotCid": "abc123..."
}
```

## Mount Descriptors

`.item/head/mounts/<mid>.json`:
```json
{
  "path": "README.md",
  "component": "readme",
  "readonly": false
}
```

## Commit Flow

1. Edit files in root and/or `.item/head/`
2. Run `cg status` to see changes
3. Run `cg commit` to mint new version
4. New manifest created, content blocks stored
5. `base` updated to new version

## THIN vs FULL

Working trees can be:

### FULL
Contains all versions, content, and relations locally.

### THIN
Contains current head and working edits; falls back to main store for historical content.

The index tracks this via `thinWorkingTree` flag.

## Mental Model

Think of it like Git:
- `.item/` = `.git/`
- Root files = working directory
- Manifests = commits (see [Manifests](manifest.md))
- Channels = branches
- `base` = HEAD

But unlike Git:
- Components have types and behaviors (see [Components](components.md))
- Relations are first-class semantic assertions (see [Relations](relations.md))
- Content is globally content-addressed and deduplicated (see [Content](content.md))
- Everything is cryptographically signed (see [Trust](trust.md))
- Items can be presented in 2D and 3D (see [Presentation](presentation.md))
