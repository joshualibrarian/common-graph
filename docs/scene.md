# Scene

This document specifies the **Scene** data model — Common Graph's unified presentation format. A Scene is a tree of nodes that describes how an Item looks. It is CBOR-serializable, content-addressed, and renderable by any platform.

Scenes are data. They can be produced by annotation compilers, WYSIWYG editors, text tools, or remote peers. They can be stored as Item components, sent over the wire, diffed, and versioned. This document specifies the data model itself — not any particular way of producing it.

> **Status**: Implemented. `Scene.java`, `SceneRenderer`, `SceneSchema`, and `SceneCompiler` are in `core/src/main/java/.../ui/scene/`.

---

## 1. Philosophy

### One scene, many renderers

A Scene is a single source of truth for presentation. Renderers project it into 3D, 2D, or text as appropriate. There is no "2D version" and "3D version" of an Item — there is one scene tree, and renderers show it at their tier of capability.

### 2D is always available

Every scene must degrade to a proper 2D rendering. The degradation chain:

```
3D  →  2D graphical  →  TUI  →  CLI
```

A scene that only works in 3D is a bug. Pure 2D scenes require zero ceremony — flat nodes with no depth properties.

### 3D is first-class

Depth is not decorative. The model is powerful enough to compose real geometry: primitives (box, sphere, cylinder), meshes (GLB/glTF), surfaces projected onto body faces, lights, audio, and transforms. Complex geometry comes in as models; the scene tree handles composition, placement, and interaction.

### Depth is a spectrum

All points on this spectrum are first-class:

| Depth | Meaning | Example |
|-------|---------|---------|
| 0 (or omitted) | Flat | Standard UI container |
| 3mm | Slab — extruded by 3mm | Minesweeper tile, clock tick |
| 1cm | Elevated platform | Chess board square |
| Body with shape | 3D primitive | Tree visualization node |
| Body with mesh | Imported geometry | Chess piece |

There is no cliff between "2D with a hint" and "real 3D". Depth is a continuous parameter.

### Coordinate system: Z-up

The DSL uses a **Z-up** right-handed coordinate system:

| Axis | Direction | Mnemonic |
|------|-----------|----------|
| **X** | Right | Screen/page horizontal |
| **Y** | Forward | Into the screen / away from camera |
| **Z** | Up | Vertical height |

This matches Blender, Unreal, and CAD conventions for physical-space tools. It's natural because:
- 2D surfaces live on the **XY plane** (like screen/paper)
- `depth = "1cm"` literally adds **Z height**
- Board games are XY grids; pieces stand along +Z

Default scene setup: camera at `(0, 5m, 1.5m)` looking toward origin, directional light at `(0, 0, 5m)` pointing down (`dirZ = -1`).

**Renderer boundary**: The Filament 3D engine uses Y-up internally. Conversion happens at the `FilamentSpatialRenderer` boundary — DSL `(x, y, z)` maps to Filament `(x, z, -y)`. DSL code never sees Y-up coordinates.

### Interaction is mode-independent

Events and actions work identically whether the scene is rendered in 2D or 3D. A click on a chess square dispatches the same action in both modes. A right-click on a minesweeper tile flags it whether you're looking at a flat grid or an elevated 3D board.

---

## 2. Node Types

A Scene is a tree of typed nodes. Every node has a **type** (Container, Text, Image, Shape, Body, etc.) and **properties** that control its behavior.

### Common Properties

These properties are available on multiple node types. They are not repeated in each node's table below.

| Property | Type | Default | On | Description |
|----------|------|---------|-----|-------------|
| `id` | string | `""` | Container, Body, Constraint | Element identifier for targeting and accessibility |
| `style` | string list | `[]` | Container, Text, Image, Shape | Style classes for semantic styling |
| `size` | string | `""` | Container, Shape, Image | Width and height shorthand (e.g., `"2em"`) |
| `width` | string | `""` | Container, Shape | Explicit width (e.g., `"40ch"`, `"200px"`, `"100%"`) |
| `height` | string | `""` | Container, Shape | Explicit height |
| `depth` | string | `""` | Container, Shape | Z-axis thickness (e.g., `"1cm"`, `"3mm"`). Creates solid slab in 3D, ignored in 2D |
| `rotation` | string | `""` | Container, Shape | Rotation angle in degrees. Supports binding expressions |
| `transformOrigin` | string | `""` | Container, Shape | Rotation pivot: `"center"`, `"bottom"`, `"top left"` |
| `cornerRadius` | string | `""` | Container, Shape | Corner rounding |
| `bind` | string | `""` | Text, Image, Embed, Repeat | Property path to data source |

### Structural Primitives

#### Container

Directional box that holds and arranges children. Supports all common properties.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `direction` | enum | `vertical` | Child layout: `vertical`, `horizontal`, `grid`, `stack` |
| `gap` | string | `""` | Space between children (e.g., `"1em"`, `"8px"`) |
| `align` | string | `""` | Cross-axis alignment: `"center"`, `"start"`, `"end"` |
| `padding` | string | `""` | Inner spacing (e.g., `"0.5em"`, `"8px 16px"`) |
| `background` | string | `""` | Background color (e.g., `"#1E1E2E"`) |
| `shape` | string | `""` | Background shape type (e.g., `"circle"`, `"pill"`) |

When `depth` is present, 3D renderers extrude the container into a slab with that thickness. Children render on the top face. 2D renderers may add a drop shadow or ignore depth entirely.

#### Text

Text content — literal or data-bound. Common: `style`, `bind`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `content` | string | `""` | Literal text content |
| `format` | string | `""` | Format hint: `"plain"`, `"markdown"`, `"code"`, `"date"`, `"json"` |
| `spans` | span list | `[]` | Rich text: each span has text + style classes |

#### Image

Visual content with multi-fidelity fallback chain: model (3D) → image (2D) → resource (icon) → alt (text/emoji). Common: `style`, `bind`, `size`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `alt` | string | `""` | Text/emoji fallback (always works) |
| `image` | content-id | null | Content-addressed 2D image |
| `resource` | string | `""` | Classpath resource path for icon |
| `fit` | string | `""` | Fit mode: `"contain"`, `"cover"`, `"fill"`, `"none"` |
| `modelResource` | string | `""` | 3D model path (GLB/glTF) |
| `modelColor` | int | -1 | Material color override for model |

#### Shape

Vector shape — standalone or as container background. Supports all common properties.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | string | `"rectangle"` | `"rectangle"`, `"circle"`, `"ellipse"`, `"pill"`, `"line"`, `"path"`, `"polygon"` |
| `fill` | string | `""` | Fill color. Supports binding expressions |
| `stroke` | string | `""` | Stroke color |
| `strokeWidth` | string | `""` | Stroke width |
| `d` | string | `""` | SVG path data (for `type="path"`) |

When `depth` is specified, 3D renderers extrude the shape into a solid. A rectangle with `depth="3mm"` becomes a 3mm-thick slab. A circle with `depth="6mm"` becomes a cylinder.

#### Border

Border specification — applies to any element.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `all` | string | `""` | Shorthand: `"1px solid #333"` |
| `top`, `right`, `bottom`, `left` | string | `""` | Per-side border |
| `width` | string | `""` | Border width |
| `style` | string | `""` | `"solid"`, `"dashed"`, `"dotted"`, `"none"` |
| `color` | string | `""` | Border color |
| `radius` | string | `""` | Corner radius |

#### Audio

Audio playback control.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `src` | string | `""` | Content reference to audio asset |
| `volume` | number | 1.0 | Volume (0.0–1.0) |
| `loop` | boolean | false | Whether to loop |

### Layout

#### Layout (type-level)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `direction` | enum | `vertical` | Default layout direction |
| `regions` | region list | `[]` | Named layout regions |

#### Region

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | string | `""` | Region identifier |
| `size`, `minSize`, `maxSize` | string | `""` | Size constraints |

#### Constraint (edge-based positioning)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `top`, `bottom`, `left`, `right` | string | `""` | Edge offsets |
| `topTo`, `bottomTo`, `leftTo`, `rightTo` | string | `""` | Edge-to-edge anchoring |
| `width`, `height` | string | `""` | Explicit dimensions (or `"fit"`) |
| `minWidth`, `minHeight`, `maxWidth`, `maxHeight` | string | `""` | Size constraints |
| `alignX`, `alignY` | string | `""` | Alignment within container |
| `zIndex` | int | 0 | Stacking order |

Common: `id` (used for edge references).

#### Grid Template (type-level)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `areas` | string | `""` | Named areas: `"header header / tree detail / prompt prompt"` |
| `columns` | string | `""` | Column sizing: `"1fr 3fr"` |
| `rows` | string | `""` | Row sizing: `"auto 1fr auto"` |
| `gap` | string | `""` | Grid gap |

#### Grid (per-item placement)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `area` | string | `""` | Named area from template |
| `column`, `row` | string | `""` | Column/row index or span |
| `justify`, `align` | string | `""` | Alignment within cell |

#### Flex Container (type-level)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `direction` | enum | `horizontal` | Flex direction |
| `justify` | string | `""` | Main axis alignment |
| `align` | string | `""` | Cross axis alignment |
| `gap` | string | `""` | Gap between items |
| `wrap` | boolean | false | Whether to wrap |

#### Flex (per-item sizing)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `size` | string | `""` | Shorthand for fixed size |
| `grow` | number | 0 | Flex grow factor |
| `shrink` | number | 1 | Flex shrink factor |
| `basis` | string | `""` | Initial main size |
| `order` | int | 0 | Display order |
| `alignSelf` | string | `""` | Override cross-axis alignment |

### Data Binding and Conditionals

#### State (repeatable)

Conditional styling based on data state.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `when` | string | `""` | Condition: `"value"`, `"!value"`, `":hover"`, `":focused"`, `"!gui"` |
| `style` | string list | `[]` | Style classes to apply when condition matches |

#### Style

Style property overrides within State conditions.

| Property | Description |
|----------|-------------|
| `display` | `"none"` to hide |
| `opacity` | 0.0 to 1.0 |
| `color` | Text color |
| `background` | Background color |
| `font` | Font family/weight |
| `decoration` | `"underline"`, `"strikethrough"` |
| `padding`, `margin` | Spacing overrides |
| `border`, `radius` | Border overrides |
| `content`, `icon` | Content replacement |

#### Event Handler (repeatable)

Dispatches actions (verbs) to Items.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `event` | string | `""` | `"click"`, `"doubleClick"`, `"rightClick"`, `"hover"`, `"focus"`, `"expand"`, `"collapse"`, `"select"`, `"drag"`, `"drop"` |
| `action` | string | `""` | Verb to dispatch (or `"toggle:style"`, `"set:style"`, `"unset:style"`) |
| `target` | string | `""` | Target reference (`""` = self, `"parent"`, or element ID) |
| `when` | string | `""` | Condition for handler activation |

#### Visibility Condition

Element hidden entirely when condition is falsy.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `value` | string | `""` | Condition expression |

#### Query

Conditionally render based on renderer capabilities or container size.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `value` | string | `""` | Query condition |

Standard queries:
- `"depth"` — true when 3D depth rendering is available
- `"!depth"` — true when rendering flat
- `"width >= 30ch"` — container size query (evaluated at paint time)
- `"@narrow"`, `"@wide"` — responsive breakpoints

Most depth differences degrade automatically. A shape with `depth = "6mm"` already does the right thing — 3D renderers extrude it, 2D renderers draw it flat. Use `"depth"` queries only when the 2D and 3D presentations are **structurally different** — different nodes, different layout, not just the same thing with/without thickness.

Multiple queries on the same element are AND-combined.

#### Repeat

Iterate a collection, rendering each element.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bind` | string | `""` | Property path to collection |
| `itemVar` | string | `"$item"` | Loop variable name |
| `indexVar` | string | `"$index"` | Index variable name |

#### Embed

Embed another scene schema at this position.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bind` | string | `""` | Property path to schema or data |

### 3D Geometry

#### Body

Declares that the element IS a 3D shape. Children are surfaces that project onto its faces.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `shape` | string | `"none"` | Primitive: `"box"`, `"sphere"`, `"cylinder"`, `"plane"`, `"none"` |
| `width`, `height`, `depth` | string | `""` | Dimensions in physical units (e.g., `"44cm"`, `"8m"`) |
| `radius` | string | `""` | Radius for sphere/cylinder |
| `mesh` | string | `""` | Content reference to GLB/glTF mesh (overrides shape) |
| `color` | int | 0 | Material color (0xRRGGBB) |
| `opacity` | number | 1.0 | Material opacity (0.0–1.0) |
| `shading` | string | `"lit"` | `"lit"`, `"unlit"`, `"wireframe"` |

Rendering behavior:
- **3D**: renders the specified geometry with material properties. Children project onto faces.
- **2D**: Body is invisible. Its children render in normal 2D layout flow.

#### Face

Targets a child surface to a specific face of a parent Body.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `value` | string | `"top"` | `"front"`, `"back"`, `"top"`, `"bottom"`, `"left"`, `"right"` |
| `ppm` | int | 512 | Pixels per meter for surface-to-texture conversion |

When no Face is specified, children default to the top face. In 2D, face assignments are ignored — children render in source order.

#### Transform

Explicit 3D positioning, rotation, and scale — applies to any element.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x`, `y`, `z` | string | `""` | Position in physical units |
| `yaw` | number | 0 | Rotation around Y axis (degrees) |
| `pitch` | number | 0 | Rotation around X axis (degrees) |
| `roll` | number | 0 | Rotation around Z axis (degrees) |
| `axisX`, `axisY`, `axisZ` | number | 0 | Axis-angle rotation axis |
| `angle` | number | 0 | Axis-angle rotation (degrees) |
| `scaleX`, `scaleY`, `scaleZ` | number | 1.0 | Per-axis scale |

Euler angles use ZYX order (yaw → pitch → roll). Either euler angles or axis-angle, not both. In 2D, Transform is ignored.

#### Light

Light source — attachable to bodies or scene-level.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | string | `"directional"` | `"directional"`, `"point"`, `"spot"` |
| `color` | int | 0xFFFFFF | Light color |
| `intensity` | number | 1.0 | Intensity multiplier |
| `x`, `y`, `z` | string | `""` | Position (for point/spot) |
| `dirX`, `dirY`, `dirZ` | number | 0 | Direction (for directional/spot) |

Light on a Body → attached to that body (moves with it). Light at scene root → ambient.

#### 3D Audio

Positional 3D audio source.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `src` | string | `""` | Content reference to audio asset |
| `x`, `y`, `z` | string | `""` | Position in physical units |
| `volume` | number | 1.0 | Volume (0.0–1.0) |
| `pitch` | number | 1.0 | Playback speed multiplier |
| `loop` | boolean | false | Whether to loop |
| `spatial` | boolean | true | 3D spatialization |
| `refDistance` | string | `"1m"` | Reference distance for attenuation |
| `maxDistance` | string | `"100m"` | Maximum audible distance |
| `autoplay` | boolean | false | Start playing immediately |

Distance attenuation: `gain = clamp(refDistance / max(distance, refDistance), 0.0, 1.0)`.

### Environment

#### Environment

Scene-level environment properties.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `background` | int | 0xF2F2F2 | Background/clear color |
| `ambient` | int | 0x808080 | Ambient light color |
| `fogNear`, `fogFar` | string | `""` | Fog distance range (empty = no fog) |
| `fogColor` | int | 0 | Fog color |

When not specified, defaults produce a neutral "white room".

#### Camera

Viewing perspective.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `projection` | string | `"perspective"` | `"perspective"` or `"orthographic"` |
| `fov` | number | 60 | Field of view (degrees, perspective only) |
| `near`, `far` | string | `"0.1m"`, `"1000m"` | Clipping planes |
| `x`, `y`, `z` | string | `""` | Camera position |
| `targetX`, `targetY`, `targetZ` | string | `""` | Look-at target |

---

## 3. Depth and Geometry

### Default: flat

Omitting depth produces flat 2D. A vertical Container with no depth is a plain layout box. No ceremony.

### Slab depth

Adding `depth = "1cm"` to a Container or Shape tells 3D renderers to extrude it:

- **3D**: Creates a solid 1cm-thick slab. Children render on the top face. Background color becomes material color.
- **2D**: Ignores depth. Renders flat. May optionally add a drop shadow.
- **Text**: Ignores depth entirely.

### Body geometry

A Body node declares the element IS a 3D shape with physical dimensions. In 3D, it creates geometry. In 2D, it's invisible — its children render in normal layout flow.

### Compound bodies

A Body can delegate to a procedural schema that builds geometry programmatically — for complex dynamic scenes (botanical trees, axis gizmos, custom visualizations).

### Imported meshes

A Body with a `mesh` reference loads complex geometry from GLB/glTF. The scene tree handles placement and interaction; the model provides geometry.

### Geometry composition

```
Container (depth=0)      → flat container (default)
Container (depth=3mm)    → extruded slab
Shape (depth=6mm)        → extruded shape
Body (shape=box)         → full 3D primitive
Body (mesh=model.glb)    → imported mesh
Body (as=procedural)     → programmatic geometry
```

All levels compose. A Body can contain elevated Containers that become platforms on its faces.

---

## 4. Layout and Body Composition

### Layout resolves X/Y; depth is orthogonal

Layout systems (flex, grid, constraint) resolve X and Y positions. Depth (Z) is orthogonal — it doesn't affect layout flow. A container with `depth="1cm"` takes exactly the same layout space as one without.

### Physical dimensions and PPU

A Body provides physical dimensions in real-world units (meters, centimeters). 2D children lay out within those dimensions using layout units (px, em, ch, %), scaled by PPU (pixels-per-unit).

PPU is derived dynamically: `ppu = surfacePixelWidth / bodyPhysicalWidth`.

### Face mapping

When a Body has multiple faces (a box has 6), children target specific faces via the Face node. Without Face, children default to the top face.

In 2D, all face content renders flat in source order. Face assignments are ignored.

---

## 5. Rendering Queries

### Automatic depth degradation

Most depth differences degrade automatically. A shape with `depth = "6mm"` already does the right thing — 3D renderers extrude it, 2D renderers draw it flat. No query needed.

### Structural queries

Use `"depth"` queries only when the 2D and 3D presentations are **structurally different** — different nodes, different layout, not just the same thing with/without thickness:

- A rotating 3D carousel vs. a horizontal scrolling list
- A spatial arrangement of objects vs. a flat grid

### Size queries

Container size queries (`"width >= 30ch"`) are evaluated at paint time against actual container dimensions. The tree contains both branches; the renderer picks which to render.

### Query mechanics

1. The renderer encounters a Query node and evaluates the condition
2. `"depth"` → true when 3D depth is available
3. `"width >= 30ch"` → compares against measured container width
4. If false, the subtree is skipped
5. Multiple queries on the same element are AND-combined

---

## 6. Scene Environment

### Defaults

When no environment is declared, the scene uses sensible defaults:

| Property | Value |
|----------|-------|
| Background | #F2F2F2 (light gray) |
| Ambient | #808080 (medium gray) |
| Light | Directional from above, intensity 0.8 |
| Camera | Y=1.0, Z=1.5, looking at Y=1.0 |

### Light attachment

Light at the scene root is ambient/scene-level. Light on a Body is attached to that body and moves with it.

### Environment in 2D

2D renderers use `background` as the clear color. Lighting, fog, and camera are ignored.

---

## 7. Renderer Responsibilities

A renderer consumes a scene tree and produces output. The renderer interface is push-based: it receives a stream of instructions as the scene tree is walked.

### Required (all renderers)

Every renderer must handle 2D primitives:

- **Text**: render text content with style classes
- **Image**: render visual content (highest-fidelity format available to that renderer)
- **Container**: manage directional layout of children
- **Metadata**: element ID, type, editability, event handlers

### Optional (3D renderers)

3D-capable renderers additionally handle:

- **Depth**: extrude containers/shapes into solid slabs
- **Body geometry**: create 3D primitives (box, sphere, cylinder) or load meshes
- **Line**: render line segments as oriented quads
- **Transform stack**: hierarchical 3D positioning (position + quaternion rotation + scale)
- **Lights**: directional, point, and spot lights
- **Camera**: perspective/orthographic viewing
- **Environment**: background, ambient, fog
- **3D audio**: positional sound with distance attenuation

When a 2D renderer encounters depth, body, light, camera, or transform nodes, it ignores them (or in the case of depth, optionally applies a drop shadow).

### Degradation chain

```
Filament (3D)  →  Skia (2D graphical)  →  TUI (ANSI)  →  CLI (plain text)
```

| Node | 3D | 2D | TUI | CLI |
|------|----|----|-----|-----|
| Text | Rendered | Rendered | ANSI styled | Plain text |
| Image | Model/image/alt | Image/alt | Alt text | Alt text |
| Container | Layout + optional slab | Layout | Indented lines | Indented text |
| Shape | Extruded if depth | 2D vector | — | — |
| Body | Full geometry | Invisible (children render flat) | — | — |
| Light/Camera | Applied | Ignored | — | — |
| Audio | 3D positional | Stereo playback | — | — |

---

## 8. Binding Expressions

Scene trees support data binding — property values that resolve against live data at render time.

### Property paths

- `"name"` — root property
- `"address.city"` — nested property
- `"$item"` — current iteration element (in Repeat)
- `"$index"` — current iteration index
- `"$label"`, `"$id"`, `"$editable"` — schema properties

### Dynamic values

- `"bind:value.hourAngle"` — computed binding (re-evaluated each render)
- `"bind:$item.color"` — per-iteration computed value

### Conditionals

- `"value"`, `"!value"` — truthiness / falsiness
- `"$index == selectedIndex"` — comparison
- `"icon != null"` — null check

---

## 9. Units

### Layout units (relative to rendering context)

| Unit | Description |
|------|-------------|
| `px` | Device pixels (96px = 1 inch) |
| `em` | Font size of current element |
| `ch` | Width of "0" character |
| `rem` | Font size of root element |
| `ln` | Line height |
| `%` | Percentage of parent dimension |
| `vw`, `vh` | Viewport width/height percentage |

### Physical units (for 3D geometry and depth)

| Unit | Description |
|------|-------------|
| `m` | Meters |
| `cm` | Centimeters |
| `mm` | Millimeters |
| `km` | Kilometers |
| `in` | Inches |
| `ft` | Feet |
| `pt` | Points (1/72 inch) |

Bare numbers default to `px` in layout contexts and `m` in physical contexts.

---

## 10. CBOR Serialization

Scene trees are CBOR-serializable. The encoding rules from `cg-cbor.md` apply:

1. Field order deterministic (ascending by canonical order)
2. No IEEE 754 floats — use CBOR integers or Tag 4 (decimal fraction)
3. Deterministic map key ordering
4. No indefinite-length encoding
5. CG-CBOR tags: 6=REF, 7=VALUE, 8=SIG, 9=QTY

The schema IS the encoding. Any tool that produces valid CBOR scene trees — annotation compilers, WYSIWYG editors, text tools, remote peers — produces equivalent output.

---

## 11. Examples

### Simple 2D layout

A contact card. Pure 2D — no depth, no bodies, no ceremony.

```
Container (vertical, gap="0.5em", padding="1em")
  ├─ Text (bind="value.name", style=["heading"])
  ├─ Text (bind="value.email", style=["muted"])
  └─ Text (bind="value.phone", style=["muted"])
```

### Chess board

A board with physical 3D body, elevated squares, and piece images.

```
Body (shape=box, width="44cm", height="0.1m", depth="44cm", color=#8B4513)
  └─ Container (vertical)
       └─ Repeat (bind="value.ranks")
            └─ Container (horizontal, style=["rank"])
                 └─ Repeat (bind="$item.squares")
                      └─ Container (id=bind:$item.id, width="2.5em", height="2.5em",
                                    depth="1cm")
                           State (when="$item.light", style=["light"])
                           State (when="!$item.light", style=["dark"])
                           └─ If ("$item.piece")
                                └─ Image (bind="$item.piece", size="2.25em")
```

In 3D: board renders as a box, squares as 1cm elevated slabs on the top face, pieces as GLB models. In 2D: Body is invisible, squares render flat, pieces render as SVGs or emoji.

### Toggle switch

Depth degrades automatically — no query needed when the structure is the same.

```
Container (horizontal, width="3em", height="1.5em", style=["toggle"])
  Shape (type=pill, fill=bind:value.trackColor)
  Event (click → toggle)
  └─ Container (style=["knob"])
       └─ Shape (type=circle, depth="4mm", fill=bind:value.knobColor, size="1.2em")
```

In 3D: the knob protrudes 4mm. In 2D: flat circle. Same click handler.

### Clock face with depth layering

Tick marks at 3mm, hands at 6/9/12mm — real depth in 3D, flat rotation in 2D.

```
Container (stack, style=["clock"], width="100%", height="100%")
  Shape (type=circle, fill="#1E1E2E", stroke="#CDD6F4")
  ├─ Query ("width >= 30ch")
  │    ├─ Repeat (bind="value.tickAngles")
  │    │    └─ Container (rotation=bind:$item, transformOrigin=bottom, height="50%")
  │    │         └─ Shape (type=rectangle, fill="#CDD6F4", width="0.6%", height="10%",
  │    │                   depth="3mm")
  │    ├─ Container (rotation=bind:value.hourAngle, transformOrigin=bottom, height="50%")
  │    │    └─ Shape (type=rectangle, fill="#CDD6F4", width="2.4%", height="64%",
  │    │              depth="6mm")
  │    ├─ Container (rotation=bind:value.minuteAngle, ...)
  │    │    └─ Shape (..., depth="9mm")
  │    └─ Container (rotation=bind:value.secondAngle, ...)
  │         └─ Shape (fill="#F38BA8", ..., depth="12mm")
  └─ Query ("width < 30ch")
       └─ Text (bind="value.digitalTime", style=["mono"])
```

### Multi-face body

A box with different surfaces on each face.

```
Body (shape=box, width="1.5m", height="0.4m", depth="0.6m", color=#2A2A3E)
  ├─ Face ("front")
  │    └─ Container (horizontal, gap="0.5em", width="100%", height="100%")
  │         ├─ Embed (bind="value.blackFace", width="45%")
  │         └─ Embed (bind="value.whiteFace", width="45%")
  └─ Face ("top", ppm=512)
       └─ Container (horizontal, gap="1em", width="100%", height="100%")
            ├─ Shape (type=circle, fill=bind:value.blackIndicator, size="2em")
            │    Event (click → switchSide)
            └─ Shape (type=circle, fill=bind:value.whiteIndicator, size="2em")
                 Event (click → switchSide)
```

In 3D: physical box with clock displays on front, buttons on top. In 2D: all face content renders flat in source order.

### Structural depth query

Use queries only when 2D and 3D need genuinely different structure.

```
├─ Query ("depth")
│    └─ Body (as=CarouselBody)    ← 3D: rotating ring of items
└─ Query ("!depth")
     └─ Container (horizontal, style=["scrollable"])    ← 2D: horizontal list
          └─ Repeat (bind="value.items")
               └─ Embed (bind="$item")
```

---

## 12. Style Rules

Style rules are declared on types using `@Scene.Rule` annotations. Rules are collected from the classpath at startup via `Stylesheet.fromClasspath()`.

### Rule Declaration

```
@Scene.Rule(selector = ".heading", color = "#CDD6F4", fontSize = "1.2em", fontWeight = "bold")
@Scene.Rule(selector = ".muted", color = "#6C7086")
@Scene.Rule(selector = ".selected", color = "#CDD6F4", background = "#45475A")
```

### Rule Properties

| Property | Type | Description |
|----------|------|-------------|
| `selector` | string | CSS-like selector |
| `color` | string | Text color (hex) |
| `background` | string | Background color (hex) |
| `fontSize` | string | Font size (e.g., `"1.2em"`) |
| `fontFamily` | string | Font family name |
| `fontWeight` | string | `"normal"` or `"bold"` |
| `display` | string | `"none"` to hide |
| `opacity` | string | 0.0 to 1.0 |
| `rotation` | string | Rotation in degrees |

### Selector Syntax

Selectors follow CSS-like specificity rules:

| Component | Prefix | Specificity | Example |
|-----------|--------|-------------|---------|
| Type | (bare) | 1 | `Container` |
| Class | `.` | 10 | `.heading` |
| State | `:` | 10 | `:selected` |
| ID | `#` | 100 | `#header` |
| Renderer | `!` | 10 | `!tui` |
| Breakpoint | `@` | 10 | `@narrow` |

Multiple components combine: `.tree-node.chrome!tui` matches tree-node elements with chrome class in TUI renderers.

### Style Resolution

`StyleResolver` walks the layout node tree **before layout**, resolving style classes to concrete visual values (color, background, fontSize, fontFamily, bold). Painters read resolved values directly — they never inspect style class names. This decouples style knowledge from rendering code.

---

## 13. Context Menus

Context menus are declared with `@Scene.ContextMenu` annotations (repeatable):

```
@Scene.ContextMenu(label = "Delete", action = "delete", icon = "🗑", group = "danger", order = 99)
@Scene.ContextMenu(label = "Copy", action = "copy", icon = "📋", group = "edit", order = 1)
@Scene.ContextMenu(label = "Flag", action = "flag", when = "!flagged", icon = "🚩")
```

### Menu Item Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `label` | string | `""` | Menu item text |
| `action` | string | `""` | Verb to dispatch on click |
| `target` | string | `""` | Target reference |
| `when` | string | `""` | Condition for visibility (e.g., `"!flagged"`, `"editable"`) |
| `icon` | string | `""` | Optional icon/emoji |
| `group` | string | `""` | Grouping (separators between groups) |
| `order` | int | 0 | Sort order within group |

Context menu items compile to `ContextMenuItem` objects stored on `ViewNode`. Renderers present them as floating menus on right-click. Items with `when` conditions are dynamically shown/hidden based on the current state.

---

## 14. Schema-Driven Editing

The `editing` package provides automatic form generation for any `Canonical` type. See [`presentation.md`](presentation.md) §Schema-Driven Editing for the full specification.

Key types:
- **`EditModel`** — `SceneModel` wrapping a mutable Canonical, dispatches field-level editing events
- **`CanonicalEditorSurface`** — procedural surface rendering type-appropriate widgets per `@Canon` field
- **`CanonicalSchema.FieldSchema`** — enriched with type introspection (`isBoolean()`, `isEnum()`, `displayName()`, `setValue()`, etc.)

Event convention: `"toggle:<field>"`, `"select:<field>"`, `"set:<field>"` for field-level mutations.

---

## Appendix: Direction

Four layout directions:

| Direction | Behavior |
|-----------|----------|
| `vertical` | Stack children top-to-bottom |
| `horizontal` | Stack children left-to-right |
| `grid` | Grid layout (rows and columns) |
| `stack` | Children overlap at same position |

In 3D, `vertical` and `horizontal` refer to the face plane of the parent body. `stack` overlaps children with Z-separation.
