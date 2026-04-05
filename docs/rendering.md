# Rendering Pipeline

The rendering pipeline transforms scene declarations into visual output through three runtime stages, operating on a single mutable tree.

## The Pipeline

```
SceneNode tree (from CBOR, annotations, WYSIWYG editor, or any source)
    -> SceneResolver  -> SceneNode tree (resolved)
    -> ScenePresenter  -> SceneNode tree (laid out)
    -> ScenePainter   -> output (pixels, text, HTML)
```

The pipeline input is a SceneNode tree. How that tree was created doesn't matter — it's the same structure regardless of origin.

### SceneCompiler (authoring tool, not part of the runtime pipeline)

One way to produce a SceneNode tree is from `@Scene` annotations on Java classes. The SceneCompiler reads these annotations and produces the CBOR-serialized SceneNode tree. But this is an **authoring step**, not a runtime step — the same tree could be authored in a WYSIWYG editor, a text editor, or generated programmatically. The pipeline starts from the SceneNode tree, not from annotations specifically.

### SceneResolver

Evaluates the tree against live state. Runs on the **librarian side** — no screen dimensions needed.

- **Binding expressions** evaluated: `"bind:value.typeName"` -> `Ref(chess, NOUN, SINGULAR)`
- **Sememe references** resolved to lexemes in the user's language: `Ref(chess, NOUN, SINGULAR)` -> `"chess"`
- **Conditional properties** evaluated: `visible = "$item.piece"` -> `true`
- **Style conditions** evaluated: `@Scene.Style(when = "$item.selected", ...)` -> classes updated
- **Repeat bindings** expanded: `@Scene.Repeat(bind = "value.ranks")` -> 8 children
- **Style cascade** applied: type defaults + item overrides + session overrides -> resolved property values

Dimensional values like `"50%"` and `"1fr"` are NOT resolved here — they need viewport context. Interaction state (hover, selected, expanded) is also not available here — that's a window concern.

### ScenePresenter

Runs on the **window side**. Takes the resolved tree, applies interaction state, resolves dimensional units, and computes pixel geometry.

- **Interaction state** applied: hover, selected, expanded — evaluates `when` conditions against per-window state, updates classes
- **Units resolved** to pixels: `"50%"` -> `450.0f`, `"1cm"` -> `7.5f`, `"1fr"` -> `300.0f`
- **Text measured**: font metrics determine text node dimensions
- **Layout solved**: children positioned within parents
- **Bounds assigned**: every node gets `(x, y, width, height)` in pixels

One text measurer implementation — font metrics should be consistent regardless of which painter will be used.

### ScenePainter

Traverses the positioned tree and produces output. Five implementations, one interface:

| Painter | Output | Asset Selection |
|---------|--------|-----------------|
| `AnsiSurfacePainter` | ANSI terminal text | Text representation |
| `PlainTextSurfacePainter` | Plain text | Text representation |
| `SkiaSurfacePainter` | Skia canvas (CPU 2D) | 2D representation (SVG, raster) |
| `FilamentSurfacePainter` | Filament ortho scene (GPU 2D) | 2D representation (SVG, raster) |
| `FilamentSpatialPainter` | Filament perspective scene (3D) | 3D representation (GLB), falls back to 2D |

The three graphical surface painters (Skia, Filament, Web) should produce **pixel-identical** 2D output. The spatial painter selects 3D assets where available and paints with perspective projection. The text painters (ANSI, plain text) gracefully degrade — representing containers as indentation, images as alt text, depth as visual hints (box drawing, ANSI color), and 3D bodies as structured text summaries.

```java
interface ScenePainter {
    void paint(SceneNode tree);
    void clear();
}
```

## SceneNode

One node type for all pipeline stages. Every property is an expression that mutates progressively through the pipeline:

```
DECLARED                -> RESOLVED              -> LAID OUT
"50%"                   -> "50%"                 -> 450.0f
"bind:value.name"       -> Ref(chess, NOUN, SG)  -> "chess"
"1cm"                   -> "1cm"                 -> 7.5f
"#B58863"               -> "#B58863"             -> 0xFFB58863
"$item.selected"        -> true                  -> true
```

Each property is a single `Object` field that starts as a declared value (string, expression, sememe reference) and gets mutated in place as it resolves. No separate "declared" vs "resolved" vs "laid out" fields — just one value that progresses.

A SceneNode carries:

- **Structure**: type (container, text, image), children, id, classes
- **Properties**: every visual property — gap, padding, width, height, depth, background, border, visible, etc. Each is an expression that resolves progressively.
- **Representations**: text (sememe ref or literal), 2D (SVG/raster resource), 3D (GLB resource)
- **Events**: click, hover, doubleClick handlers
- **Bounds**: x, y, width, height (filled by ScenePresenter)

The tree is built once, mutated in place (resolve, then layout), then read by the painter. No intermediate tree copies, no separate class hierarchies.

## Representations

Every node can carry up to three representations:

- **Text**: a sememe reference with grammatical features, resolved to a lexeme in the user's language
- **2D**: SVG, raster image, or drawn geometry (colored rectangles, borders, shapes)
- **3D**: GLB model, extruded geometry

The painter selects which to use based on its capabilities. A chess piece node carries all three: `"♟"` (text), piece SVG (2D), piece GLB (3D). The painter picks one.

## Annotations

The `@Scene` annotations are syntactic sugar for declaring SceneNode trees in Java. They compile to CBOR via the SceneCompiler.

### Structural Annotations

Define WHAT a node IS:

- `@Scene.Container(direction, id)` — a container with children
- `@Scene.Text(bind)` / `@Scene.Text.Literal(content)` — a text node
- `@Scene.Image(bind, size)` — an image/model node
- `@Scene.Repeat(bind)` — iteration over a collection
- `@Scene.Embed(bind)` — composition of another scene

Structural annotations can carry **direct property values** — these are fixed by the scene author and are NOT overridable through the style cascade:

```java
@Scene.Container(direction = Direction.VERTICAL, depth = "1cm")
```

The chess board's depth, its 8x8 grid structure, the piece positions — these are structural. Overriding them would break the scene.

### `@Scene.Style`

Declares visual properties, optionally conditional. This is the single mechanism for all presentation:

```java
// Unconditional — always applies to this node
@Scene.Style(background = "#1E1E2E", padding = "0.3em")

// Class-conditional — applies to nodes matching this class
@Scene.Style(when = ".header", background = "#1E1E2E", gap = "0.5em")

// ID-conditional — applies to the node with this ID
@Scene.Style(when = "#board", border = "2px solid #8B4513")

// Expression-conditional — applies when the expression is true
@Scene.Style(when = "$item.selected", background = "#FFD700")
@Scene.Style(when = "$item.hover", opacity = "0.8")
```

The `when` field is the universal condition. It can be:
- **Omitted** — always applies to the declaring node
- **A class selector** (`.header`) — matches nodes with that class
- **An ID selector** (`#board`) — matches the node with that ID
- **A binding expression** (`$item.selected`) — matches when the expression is truthy

This replaces `@Scene.If`, `@Scene.State`, and `@Scene.Rule` with one unified mechanism. Visibility is just `visible` as a property: `@Scene.Style(when = "$item.piece", visible = "true")`. Conditional classes are just styles that add/change properties when conditions are met.

## Styles and the Presentation Cascade

Every property on a SceneNode can come from two sources:

### Direct Values (Structural)

Values set directly on structural annotations. Fixed by the scene author, not overridable. These bypass the cascade entirely:

```java
@Scene.Container(depth = "1cm")  // structural — always 1cm
```

### Style Values (Presentation)

Values set via `@Scene.Style`. These are defaults that can be overridden through the cascade:

```java
@Scene.Style(when = ".square.light", background = "#F0D9B5")  // default light square color
```

### The Cascade

Style values cascade through `CONFIG:[PRESENTATION]` bindings — the same mechanism as all other config in Common Graph:

```
Type definition        @Scene.Style on the type — default presentation
    overridden by
Item instance          CONFIG:[PRESENTATION, ".square.light"]→"background: #4488CC"
    overridden by
Session / User         CONFIG:[PRESENTATION, ".header"]→"padding: 0.5em"
```

This uses existing frame model concepts — no new mechanism. `CONFIG` is a role, `PRESENTATION` is a qualifier, the style selector is a further qualifier, and the binding value is the property override.

### Resolution Order

The SceneResolver applies the full cascade:

**SceneResolver** (librarian side):
1. Collect `@Scene.Style` defaults from the type definition
2. Overlay `CONFIG:[PRESENTATION]` bindings from the item instance
3. Overlay `CONFIG:[PRESENTATION]` bindings from the session/user
4. Evaluate `when` conditions against item state
5. For each node, match its classes/ID against the cascaded styles
6. Set resolved property values on the node

**ScenePresenter** (window side):
7. Evaluate `when` conditions against interaction state (hover, selected, etc.)
8. Update classes and re-resolve affected style properties
9. Resolve dimensional units and compute layout

Direct values on structural annotations are set at compile time and are never touched by the cascade.

## Interaction State

Interaction state tracks ephemeral, per-window user interaction: hover, selected, expanded, focused. It lives on the window, persists across re-renders, and is never stored or sent over the wire.

Interaction state is just another source of truth for `when` conditions in `@Scene.Style`. There is no separate "state system" — a style condition like `when = "$hover"` evaluates against interaction state the same way `when = "$item.selected"` evaluates against item state. The expression mechanism is identical; only the lifecycle differs:

- **Item state** (chess position, player names) — lives on the librarian, persisted in frames. Resolved by the SceneResolver.
- **Interaction state** (hover, expanded) — lives on the window, ephemeral. Applied by the ScenePresenter before computing geometry.

## Network Boundary

The pipeline splits at the resolve/layout boundary:

- **Librarian side**: SceneResolver evaluates bindings, item state, repeats, and the style cascade. No screen dimensions needed.
- **Window side**: ScenePresenter applies interaction state, resolves units, computes geometry. ScenePainter produces output.

The session is an item on the librarian, but the window displaying it lives on the host machine. The resolved SceneNode tree is the wire format between librarian and window — fully evaluated semantically, but not yet laid out geometrically.

## Design Principles

- **One tree type**: SceneNode serves all stages. No Node -> LayoutNode translation. Progressive mutation in place.
- **Every property is an expression**: constants, binding expressions, sememe references — all resolve through the same mechanism.
- **One style mechanism**: `@Scene.Style` with `when` conditions replaces @Scene.If, @Scene.State, and @Scene.Rule.
- **Direct = structural, Style = presentation**: direct values on structural annotations are fixed; style values cascade via CONFIG:[PRESENTATION] bindings.
- **Cascade via frames**: presentation overrides use the existing config binding mechanism — no separate style system.
- **One interface**: ScenePainter. Five implementations, same contract.
- **Asset selection at paint time**: the node carries all representations; the painter picks.
- **2D parity**: Skia, Filament, and Web surface painters produce identical 2D output.
- **No "Renderer"**: these are painters, not renderers. Filament's own Renderer is an internal detail.
