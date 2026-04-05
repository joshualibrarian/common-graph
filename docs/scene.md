# The Scene Model

The scene model describes how items present themselves visually, spatially, and textually. One unified structure covers every dimension — from 3D environments to terminal text. The same CBOR-serialized scene tree renders on every platform at whatever fidelity the renderer supports.

## Philosophy

**Meaning first.** Scene elements preferably carry sememe references rather than literal strings. The renderer resolves sememes to the user's language — a label references a concept, and the language system produces the word. "Checkmate" in English becomes "echec et mat" in French, from the same scene tree. Grammatical features can further specify the desired word form — noun vs. verb, singular vs. plural, past tense, nominative case — so the same sememe produces "move" (noun, lemma), "moves" (noun, plural), "moved" (verb, past), or "mueve" (verb, present, Spanish) depending on context. This is Common Graph's answer to i18n: rather than maintaining parallel string tables per locale, you reference meaning once and the language system produces the correct form in any language. Literal text is still available when it makes sense — user-entered content, code snippets, debug output, or any case where the text IS the data rather than a reference to meaning.

**One system, every dimension.** A body carries representations at multiple fidelities — a 3D model, a 2D image, a geometric shape, a Unicode glyph. The renderer uses what it can. Nothing is "3D-only" — every element expresses itself at whatever dimension the platform supports.

**Scenes are CONFIG.** A scene is metadata about how to present content. Scenes are CBOR-serializable, content-addressed canonical values stored as CONFIG bindings. Seed defaults come from the type author — whether declared via annotations, hand-authored CBOR, a visual editor, or punch cards. Users override them by storing different CONFIG at the instance or session level.

**Progressive mutation.** Every property is a single value that starts as a declared expression and gets mutated in place through the pipeline stages: declared, resolved, laid out. No intermediate copies, one single class hierarchy.

## The Three Primitives

Everything composes from three structural primitives. No other node types exist.

### Container

The structural primitive. Has children and layout. Containers arrange other nodes — vertically, horizontally, in grids, or as overlapping stacks.

### Text

The content primitive. Two modes:

**Semantic text** renders a value. A sememe resolves through the lexeme system into the user's language. A quantity formats its number and resolves its unit's symbol. Any value type handles its own display. The same scene tree renders in any language.

**Literal text** displays a string — documents, code, log entries. An optional format reference (a MIME type sememe) tells the renderer how to interpret the content.

### Body

The visual primitive. Carries representations at multiple fidelities:

- **Model** — 3D mesh (GLB, glTF)
- **Image** — 2D image (SVG, PNG, JPEG, WebP, GIF)
- **Shape** — geometric primitive (line, circle, rectangle, sphere, box, cylinder, cone)
- **Glyph** — Unicode character
- **Alt** — text description

The renderer uses the highest fidelity it supports: model in spatial, image in graphical, glyph in text.

## The Fidelity Spectrum

Every scene element exists across the dimensionality spectrum. The renderer expresses what it can.

| Element | Spatial (3D) | Graphical (2D) | Text |
|---------|-------------|----------------|------|
| Body | 3D mesh with materials | 2D image or shape | Unicode glyph or alt text |
| Light | Positioned light source | Drop shadow, depth cue | (metadata only) |
| Elevation | Perpendicular displacement | Shadow weight, z-ordering | Indentation depth |
| Camera | Viewpoint, projection, FOV | Viewport bounds | (implicit) |
| Environment | Skybox, IBL, fog | Background color | Terminal background |
| Audio | Positional 3D audio | Stereo playback | (silent) |
| Transform | Full 3D transform | Canvas rotation, scale | Indentation, visual hints |
| Surfaces | Containers mapped to faces on geometry | (ignored, render inline) | (ignored) |

## Node Properties

All three primitives share a universal base. Every property applies to every node type — a text node can have a background color, a body node can be rotated, a container can have opacity.

Every property value is an expression. Width, color, visibility, content, transition duration — all evaluated by the pipeline against item data, state, environment, and user preferences. A background color can be a literal `"#1E1E2E"`, a binding expression `"bind:theme.background"`, or a conditional override via `when` blocks. The scene tree is a template of expressions. The pipeline evaluates them progressively.

### Box Model

| Property | Values | Notes |
|----------|--------|-------|
| width, height | Dimensional | Explicit size |
| minWidth, maxWidth | Dimensional | Constraints |
| minHeight, maxHeight | Dimensional | Constraints |
| margin | 1-4 values | Outer spacing |
| padding | 1-4 values | Inner spacing |
| overflow | visible, hidden, scroll, auto | Content overflow behavior |

### Background

| Property | Values | Notes |
|----------|--------|-------|
| backgroundColor | Color | Fill color |
| backgroundImage | Resource path | SVG or raster image behind content |
| backgroundSize | fill, cover, contain, (natural) | How the image fills the bounds |

The shorthand `background` sets `backgroundColor`.

### Border

Per-side longhand — each side independently addressable through the cascade:

| Property | Values | Notes |
|----------|--------|-------|
| borderTopWidth, borderRightWidth, borderBottomWidth, borderLeftWidth | Dimensional | Per-side width |
| borderTopStyle, borderRightStyle, borderBottomStyle, borderLeftStyle | solid, dashed, dotted, none | Per-side style |
| borderTopColor, borderRightColor, borderBottomColor, borderLeftColor | Color | Per-side color |
| corner | Dimensional | Border radius (all corners) |

The shorthand `border: "2px solid #333"` decomposes to all 12 per-side fields. Per-property shorthands (`borderWidth: "2px 1px"`) expand 1-4 values following the CSS pattern (top, right, bottom, left).

### Typography

| Property | Values | Notes |
|----------|--------|-------|
| fontFamily | Font name or category | "monospace", "sans-serif", specific name |
| fontSize | Dimensional | em, px, % |
| fontWeight | normal, bold | |
| fontStyle | normal, italic | |
| textDecoration | underline, line-through, overline | Space-separated for combinations |
| textAlign | left, center, right, justify | |
| lineHeight | Dimensional or multiplier | |
| letterSpacing | Dimensional | |
| textOverflow | ellipsis, clip | Truncation behavior |
| whiteSpace | normal, nowrap, pre, pre-wrap | Whitespace handling |
| foreground | Color | Text color |

### Visual

| Property | Values | Notes        |
|----------|--------|--------------|
| opacity | 0.0 - 1.0 | Transparency |
| visible | Boolean | Show/hide    |

### Transform

| Property | Values | Notes |
|----------|--------|-------|
| rotationX | Degrees | Rotation around X axis (tilt forward/back) |
| rotationY | Degrees | Rotation around Y axis (turn left/right) |
| rotationZ | Degrees | Rotation around Z axis (spin in plane) |
| scaleX | Number | Horizontal scale |
| scaleY | Number | Vertical scale |
| scaleZ | Number | Depth scale (3D) |
| elevation | Dimensional | Z-depth for 3D, shadow in 2D |
| position (x, y, z) | Numbers | 3D position |
| transformOrigin | Position | Point around which rotation and scale apply |

The shorthand `rotation` decomposes to `rotationZ` — the common 2D case. `rotation: "45deg"` is equivalent to `rotationZ: "45deg"`.

The shorthand `scale` decomposes to uniform scaling across all axes. `scale: "1.5"` is equivalent to `scaleX: "1.5"`, `scaleY: "1.5"`, `scaleZ: "1.5"`.

Transform origin defaults to center. Accepts keyword pairs ("top left", "center", "bottom right"), percentage pairs ("50% 0%"), or three values for 3D ("50% 50% 20px"). The third value is the Z offset, defaulting to 0.

### Transition

Declares how property changes animate. When a property changes (due to state or interaction), the renderer interpolates smoothly rather than snapping.

| Property | Values | Notes |
|----------|--------|-------|
| transitionProperty | Property name(s) | "all", "background", "opacity, background" |
| transitionDuration | Duration | "0.3s", "300ms" |
| transitionEasing | Timing function | See Easing below |
| transitionDelay | Duration | Delay before animation starts |

The shorthand `transition: "background 0.3s ease-out"` decomposes to the four longhand fields.

**Easing functions:**

| Name | Behavior |
|------|----------|
| linear | Constant speed |
| ease | Slow start, fast middle, slow end (CSS default) |
| ease-in | Slow start, fast end |
| ease-out | Fast start, slow end |
| ease-in-out | Slow start and end |
| overshoot | Slight overshoot at end |
| spring | Default spring (responsive, slight overshoot) |
| spring-gentle | Slow, smooth, minimal overshoot |
| spring-snappy | Fast with quick settle |
| spring-bouncy | Lots of overshoot and oscillation |
| cubic-bezier(x1, y1, x2, y2) | Custom CSS cubic-bezier |
| spring(stiffness, damping) | Custom spring physics |
| steps(count) | Discrete steps |

Spring easings compute their own duration from physical parameters. The declared duration is ignored.

### Body Properties

| Property | Values | Notes |
|----------|--------|-------|
| shape | line, circle, rect, sphere, box, cylinder, cone | Geometric primitive |
| image | Resource path | 2D image (SVG, PNG, JPEG, WebP, GIF) |
| model | Resource path | 3D model (GLB) |
| glyph | Unicode | Text fallback |
| alt | String | Accessibility description |
| fill | Color | Shape fill |
| strokeColor | Color | Shape outline color |
| strokeWidth | Dimensional | Outline thickness |
| radius | Dimensional | For circle/sphere |
| material | Reference | PBR material |
| surfaces | Named map | Containers bound to faces on geometry |

### Container Properties

| Property | Values | Notes |
|----------|--------|-------|
| layout | vertical, horizontal, grid, stack | Child arrangement |
| gap | Dimensional | Spacing between children |
| align | start, center, end, stretch | Cross-axis alignment |
| justify | start, center, end, space-between, space-around | Main-axis distribution |
| wrap | Boolean | Flow to next line |
| columns, rows | Integer | Grid dimensions |
| aspectRatio | Number | Enforced aspect ratio |
| repeat | Binding expression | Iterate data into children |

### Interaction

| Property | Values | Notes |
|----------|--------|-------|
| events | Event list | click, hover, doubleClick handlers |
| capturesFocus | Boolean | Receives keyboard focus |
| cursor | Keyword | Cursor style (pointer, text, etc.) |
| editable | Boolean | Content editing enabled |

### Identity and Conditionals

| Property | Values | Notes |
|----------|--------|-------|
| id | String | Stable element identifier |
| classes | String list | Style class selectors |
| bind | Expression | Data binding source |
| state | Key-value pairs | Declared state with defaults |
| when | Condition map | Property overrides keyed by condition |

## Dimensional Values

Properties that accept dimensional values resolve progressively through the pipeline:

| Unit | Category | Resolution |
|------|----------|------------|
| px | Logical pixel | 1/96 inch, DPI-independent |
| dpx | Device pixel | One hardware pixel. Rarely needed — prefer px for layout |
| em | Contextual | Current font size |
| ch | Contextual | Width of "0" in current font |
| rem | Contextual | Root font size |
| ln | Contextual | Line height |
| % | Relative | Percentage of parent dimension |
| fr | Relative | Fraction of remaining space |
| vw, vh | Viewport | Percentage of viewport |
| cm, mm, in, pt | Physical | Fixed conversion ratios |
| m, km, ft | Physical | For spatial scenes |

Dimensional values stay as strings through the resolver (no viewport needed). The presenter resolves them to pixel floats using viewport dimensions, DPI, and font metrics.

## Layout

### Vertical and Horizontal

Children arranged along the main axis. `gap` controls spacing. `fr` units on children split remaining space after fixed children are measured.

### Grid

Children placed in `columns` x `rows` cells. `repeat` iterates a data collection into templated cells. Gap applies between cells in both directions.

### Stack

Children overlap at the same position. Later children render on top. For overlays, badges, background layers.

## The Pipeline

The scene tree passes through three runtime stages, mutating in place. Each stage reads and mutates the same tree — no intermediate copies, no separate representations.

```
Declared tree  ->  SceneResolver  ->  ScenePresenter  ->  ScenePainter  ->  output
                   (librarian)        (window)            (window)
```

### Resolver (librarian side)

Evaluates the tree against live state. Runs on the librarian — no screen dimensions, no viewport, no font metrics needed.

- **Binding expressions** evaluated: `"bind:value.typeName"` becomes a resolved value
- **Semantic tokens** resolved to the user's language: a sememe reference becomes a display string ("chess", "echecs", "Schach")
- **Visibility conditions** evaluated: `"$item.piece"` becomes `true` or `false`
- **Repeat bindings** expanded: a data collection becomes concrete children from the template
- **When-block conditions** evaluated against item state and environment
- **Style cascade** applied in order:
  1. Collect style defaults from the type definition
  2. Overlay CONFIG:PRESENTATION bindings from the item instance
  3. Overlay CONFIG:PRESENTATION bindings from the session/user
  4. Evaluate `when` conditions against item state
  5. For each node, match classes/ID against cascaded styles
  6. Set resolved property values on the node

Dimensional values (`"50%"`, `"1fr"`) are NOT resolved here — they need viewport context. Interaction state (hover, selected, expanded) is NOT available here — that's a window concern.

### Presenter (window side)

Takes the resolved tree, applies interaction state, resolves all remaining values to primitives, and computes pixel geometry.

- **Interaction state** applied: evaluates `when` conditions against hover, selected, expanded, focused
- **Colors** resolved: `"#B58863"` becomes `0xFFB58863` (integer ARGB)
- **Dimensional units** resolved to pixels: `"50%"` becomes `450.0f`, `"1cm"` becomes `7.5f`, `"1fr"` becomes `300.0f`
- **Border shorthand** decomposed and resolved: per-side widths to floats, per-side colors to ints
- **Transition durations** resolved: `"0.3s"` becomes `0.3f`
- **Rotation** resolved: `"45deg"` becomes `45.0f`
- **Text measured**: font metrics determine text node dimensions. One text measurer implementation — font metrics are consistent regardless of which painter will be used.
- **Layout solved**: two-phase measure + position algorithm places children within parents
- **Min/max constraints** applied after measuring
- **Bounds assigned**: every node gets `(x, y, width, height)` in pixels

After the presenter, every property is a resolved primitive — floats, ints, booleans. No strings left to parse.

### Painter (window side)

Traverses the positioned tree and produces output. One interface, six implementations:

| Painter | Output | Representation |
|---------|--------|----------------|
| Skia | CPU 2D canvas | 2D (SVG, raster) |
| Filament Surface | GPU 2D ortho | 2D (SVG, raster) |
| Filament Spatial | GPU 3D perspective | 3D (GLB), falls back to 2D |
| Web | Browser DOM | 2D (CSS, images) |
| ANSI | Terminal text | Text (glyphs, box drawing) |
| Plain Text | Plain Unicode | Text (glyphs, indentation) |

The three graphical surface painters (Skia, Filament, Web) produce **pixel-identical** 2D output. The spatial painter selects 3D assets where available. The text painters gracefully degrade — containers become indentation, images become alt text, depth becomes visual hints.

For each node, the painter:
1. Saves canvas/output state
2. Applies transform (scale, rotation around transform origin)
3. Applies opacity
4. Clips if overflow is hidden/scroll
5. Paints background color, background image, border
6. Paints type-specific content (children, text, or body representation)
7. Restores state

For body nodes, the painter selects the best available representation: model (3D) → image (2D) → shape → glyph (text).

If the node declares transitions, the painter consults the animation state for interpolated property values — smoothly animating between old and new values rather than snapping.

### Network Boundary

The pipeline splits at the resolver/presenter boundary. The resolved scene tree is the wire format between librarian and window — CBOR-serialized, fully evaluated semantically, not yet laid out geometrically.

This means the librarian does the expensive work (data resolution, language lookup, style cascade) once, and multiple windows can independently present and paint the same resolved tree with their own viewport dimensions, interaction state, and rendering backend.

## The Style Cascade

Every property can come from two sources:

**Direct values** are structural — fixed by the scene author, not overridable. The chess board's 8x8 grid, the board's depth — these are structural.

**Style values** cascade and can be overridden:

```
Type definition (seed defaults)
    overridden by
Item instance (per-item customization)
    overridden by
Session / User (personal preferences)
```

This uses existing frame model concepts — CONFIG bindings with PRESENTATION qualifiers. No separate style system.

### Conditional Properties

The `when` mechanism applies property overrides conditionally:

- **Class selector** (`.header`) — matches nodes with that class
- **ID selector** (`#board`) — matches the node with that ID
- **Item state** (`$item.selected`) — matches when librarian-side state is truthy
- **Interaction state** (`$hover`, `$expanded`) — matches when window-side state is truthy

The resolver evaluates item state conditions. The presenter evaluates interaction state conditions. The same mechanism, different lifecycles.

## Interaction State

Ephemeral, per-window state: hover, selected, expanded, focused. Lives on the window, persists across re-renders, never stored or sent over the wire.

State is declared on nodes with a key and default value. Mutated by event actions:

| Action | Effect |
|--------|--------|
| toggle:key | Flip boolean |
| set:key=value | Set to literal |
| unset:key | Reset to default |
| cycle:key=a,b,c | Advance to next value |

Pseudo-states (hover, focus, active) are tracked automatically.

## Root and Handle

Every scene has two forms:

**Handle** — compact identity for trees, chips, breadcrumbs, search results. The default comes from the item's SYMBOL and NAME bindings. Custom handles override it.

**Root** — the full scene. In 2D it renders as a panel. In 3D it renders as a space. Both are independently overridable through CONFIG.

Both are stored together: `{ root: Node, handle: Node }`. A user can customize their chess handle without touching the board layout.

## Authoring

The scene model is authoring-agnostic. The CBOR-serialized node tree is the artifact — how it was created doesn't matter:

- Java annotations (compiled by SceneCompiler)
- WYSIWYG editor (direct node tree manipulation)
- Text editor (JSON/YAML import)
- Programmatic generation
- Remote protocol (receive CBOR, deserialize, render)

The annotations are developer convenience. The stored CONFIG is the truth.

## Design Principles

- **One tree type** for all pipeline stages. Progressive mutation in place.
- **Every property is an expression** that resolves progressively: declared, resolved, laid out.
- **Every property applies to every node type.** Background, border, opacity, rotation are not tied to containers or text or bodies.
- **All shorthand decomposes to longhand.** Borders, transitions, padding — shorthand is syntactic sugar. Individual longhand fields are what's serialized, cascaded, and overridden.
- **One style mechanism.** Conditional `when` blocks with class/ID/expression selectors.
- **Cascade via frames.** Presentation overrides use existing CONFIG binding mechanism.
- **Asset selection at paint time.** The node carries all representations; the painter picks.
- **2D parity.** Skia, Filament, and Web surface painters produce identical output.
- **State-driven animation.** All motion is state-driven. Transition properties declare how changes animate. No imperative "play" commands.
