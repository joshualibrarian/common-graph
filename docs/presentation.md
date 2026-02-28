# Presentation Pipeline

> **See also:** [`scene.md`](scene.md) — the Scene data model specification (code-agnostic).

Everything is an Item. Items declare how they look. This document specifies the presentation pipeline.

The `@Scene.*` annotation namespace covers 2D structure and 3D geometry in a single DSL. `SceneSchema` is the base class for all visual types, `SceneRenderer` is the renderer interface, and `SceneCompiler` handles compilation. All annotations produce CBOR-serializable schemas: stored as components, sent over the wire, rendered by any platform.

Annotations are developer ergonomics; a WYSIWYG editor, a text tool, or a remote peer could produce the same CBOR. The pipeline is:

```
DATA + SCHEMA  →  VIEW  →  RENDER
```

An Item's schema is data. A model holds state and produces schema snapshots. A renderer consumes those snapshots and draws pixels (or characters).

---

## Rendering Tiers

Three tiers, in order of capability:

| Tier | Engine | When |
|------|--------|------|
| **3D** (primary) | Filament (Metal/Vulkan/OpenGL) | Default when GPU available |
| **2D graphical** (fallback) | Skia | Flat rendering without GPU scene graph |
| **Text** (always available) | CLI (plain text), TUI (ANSI + Unicode box-drawing) | Terminal, headless, accessibility |

**Degradation rules:**
- Any Scene can render at any tier. A 2D layout degrades to text naturally.
- A 3D Body falls back to a Face — an embedded 2D Surface rendered as a textured quad. If no 3D renderer is available, the Face's Surface renders directly at the 2D or text tier.
- A single Item can provide both 2D and 3D views from the same model state. The renderer picks the best available.

---

## Surface Schema (2D)

A Surface is a tree of elements. Every element extends a common base with shared fields, then specializes into one of three primitives or a composite pattern built from them.

### Common Fields

Present on every surface element:

| Field | Type | Default | Canon | Description |
|-------|------|---------|-------|-------------|
| `value` | generic `T` | null | -1 | Bound data value |
| `id` | string | null | 0 | Element identifier for referencing |
| `style` | string list | [] | 1 | Style classes (CSS-like) |
| `visible` | boolean | true | 2 | Whether element is rendered |
| `editable` | boolean | false | 3 | Whether element accepts input |
| `tabbable` | boolean | null | 4 | Tab-order participation (null = default behavior) |
| `label` | string | null | 5 | Accessibility label text |
| `labeledBy` | string | null | 5 | ID of another element that labels this one |
| `size` | string | null | 6 | Size constraint ("auto", "1fr", "300px") |
| `margin` | string | null | 7 | Space outside the element ("4px", "8px 16px") |
| `padding` | string | null | 8 | Space inside the element |
| `events` | event list | [] | 9 | Event handlers |
| `scaleX` | double | 1.0 | 10 | Horizontal scale factor |
| `scaleY` | double | 1.0 | 11 | Vertical scale factor |
| `boxBorder` | BoxBorder | null | — | Border specification |
| `boxBackground` | string | null | — | Background color/style |

### Three Primitives

Every 2D surface is built from exactly three primitive elements plus audio:

#### Text

Plain or formatted text content, with optional rich text spans.

| Field | Type | Default | Canon | Description |
|-------|------|---------|-------|-------------|
| `content` | string | null | 10 | The text to display |
| `format` | string | "plain" | 11 | Format hint |
| `spans` | TextSpan list | null | — | Rich text with per-span styles |

Format values: `"plain"`, `"markdown"`, `"code"`, `"date"`, `"json"`.

**Rich text** is expressed through **TextSpan** — each span carries a text fragment and a list of style classes:

```
TextSpan {
    text: string            # The text fragment
    styles: [string]        # Style classes ("bold", "italic", "code", "heading", etc.)
}
```

When `spans` is present, it takes precedence over `content`. This enables mixed-style text within a single Text element (e.g., a sentence with bold and italic words).

#### Image

Visual content with a four-level fallback chain: 3D model → 2D image → resource icon (for example OpenMoji SVG) → text/emoji.

| Field | Type | Default | Canon | Description |
|-------|------|---------|-------|-------------|
| `alt` | string | (required) | 10 | Text/emoji fallback (always present) |
| `image` | ContentID | null | 11 | Content-addressed 2D image |
| `solid` | ContentID | null | 12 | Content-addressed 3D geometry |
| `resource` | string | null | — | Classpath/bundled icon resource path |
| `size` | string | "medium" | 13 | "small", "medium", "large", or explicit ("32px") |
| `fit` | string | "contain" | 14 | "contain", "cover", "fill", "none" |
| `shape` | string | null | — | "circle", "rounded-rect" — clip mask |
| `backgroundColor` | string | null | — | Background color behind the image |
| `modelResource` | string | null | — | 3D model file (GLB/glTF) for 3D renderers |
| `modelColor` | integer | null | — | Tint color for 3D model (hex RGB) |

Renderers resolve the best available representation: `modelResource`/`solid` if 3D is available, `image` if 2D, `resource` icon if bundled, `alt` text otherwise.

#### Box

Directional container with children. The universal structural element.

| Field | Type | Default | Canon | Description |
|-------|------|---------|-------|-------------|
| `direction` | Direction | VERTICAL | 10 | VERTICAL, HORIZONTAL, or GRID |
| `gap` | string | null | 11 | Space between children |
| `capturesFocus` | boolean | false | 12 | Whether this box captures keyboard focus |
| `boxWidth` | string | null | 13 | Explicit width |
| `boxHeight` | string | null | 14 | Explicit height |
| `children` | element list | [] | 100 | Child surface elements |

Style classes on boxes signal semantic intent to the renderer:

| Style Class | Meaning |
|-------------|---------|
| `"list"` | Scrollable item list |
| `"list-item"` | Individual list entry |
| `"tree"` | Expandable hierarchy |
| `"tree-node"` | Individual tree node |
| `"expanded"` | Currently expanded node |
| `"collapsed"` | Currently collapsed node |
| `"button"` | Clickable action |
| `"primary"`, `"danger"` | Button variants |
| `"chip"`, `"badge"` | Inline tag/indicator |
| `"split"`, `"region"` | Layout regions |
| `"panel"`, `"scrollable"` | Scrollable content areas |
| `"chrome"`, `"input"` | UI chrome / input areas |

#### Audio

Playback control for audio content.

| Field | Type | Default | Canon | Description |
|-------|------|---------|-------|-------------|
| `src` | string | null | 20 | Content reference to audio asset |
| `volume` | double | 1.0 | 21 | Volume level (0.0 silent — 1.0 full) |
| `loop` | boolean | false | 22 | Whether to loop playback |

Emits play/pause/seek events.

### Composite Patterns

Built from the three primitives using nesting, style classes, and event handlers:

#### Handle

The collapsed representation of any item: icon + label + optional subtitle + optional badges.

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `icon` | Image | 20 | Item glyph/icon |
| `label` | string | 21 | Primary text |
| `subtitle` | string | 22 | Secondary text (count, type, status) |
| `badges` | Image list | 23 | Status indicators |

Structure: horizontal box containing `[icon, label, subtitle, badges-box]`.

Contextual variants apply style classes:
- Default: `"handle"`
- Node: `"handle"`, `"node-handle"` (for tree nodes)
- Header: `"handle"`, `"header-handle"` (for section headers)
- Prompt: `"handle"`, `"prompt-handle"` (for input prompts, appends "> " to label)

#### List

Scrollable list with optional selection.

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `items` | element list | 10 | List entries |
| `itemMode` | SceneMode | 11 | Rendering mode for items (default: COMPACT) |
| `selectable` | boolean | 12 | Whether items are selectable |
| `selectedIndex` | int | 13 | Currently selected index (-1 = none) |

Structure: vertical box with style `"list"`. Each item wrapped in a horizontal box with style `"list-item"`. Selected item additionally gets `"selected"`.

#### Tree

Expandable hierarchy with expand/collapse and selection.

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `roots` | Node list | 10 | Root-level nodes |
| `showExpandIndicators` | boolean | 11 | Show expand/collapse arrows (default: true) |
| `selectable` | boolean | 12 | Enable selection (default: true) |
| `showRoot` | boolean | 13 | Show root node or start from children (default: true) |

**Node** structure:

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `id` | string | 0 | Unique node identifier |
| `expanded` | boolean | 1 | Whether children are visible |
| `selected` | boolean | 2 | Whether this node is selected |
| `children` | Node list | 3 | Child nodes |
| `content` | element | 4 | Node's visual content (typically a Handle) |

Expand indicators: `"▼ "` when expanded, `"▶ "` when collapsed. Clicking the indicator dispatches `"expand"` or `"collapse"` events targeting the node ID.

#### Item Surface

Multi-mode presentation of an Item at different scales.

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `icon` | Image | 10 | Item icon |
| `name` | string | 11 | Display name |
| `typeName` | string | 12 | Type category |
| `subtitle` | string | 13 | Secondary text |
| `mode` | SceneMode | 14 | Presentation mode |
| `content` | element list | 15 | Child content surfaces |

### Rendering Modes

Items render at four scales:

| Mode | Description | Structure |
|------|-------------|-----------|
| **CHIP** | Icon + token, inline reference | Horizontal: `[icon, name]` with style `"chip"` |
| **COMPACT** | Icon + token + subtitle, list item | Horizontal: `[icon, name]` with style `"compact"` |
| **PREVIEW** | Header + key properties | Vertical: `[header, subtitle, detail]` |
| **FULL** | Header + all content, expandable | Vertical: `[header, content-list]` |

Non-FULL modes attach a click event: `onClick("navigate", "iid:<encoded-iid>")`.

### Events

Events bind user interaction to verb dispatch.

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `on` | string | 0 | Event type |
| `action` | string | 1 | Action verb to dispatch |
| `target` | string | 2 | Target reference |

**Event types:** `"click"`, `"doubleClick"`, `"rightClick"`, `"drag"`, `"drop"`, `"hover"`, `"focus"`, `"expand"`, `"collapse"`, `"select"`, `"scrollUp"`, `"scrollDown"`, `"play"`, `"pause"`, `"seek"`.

**Target references:** `""` (self), `"self"`, `"parent"`, `"$index"` (repeat context), `"iid:<encoded>"` (specific item).

**Shorthand factories:** `click(action)`, `doubleClick(action)`, `drag(action)`, `drop(action)`, `hover(action)`, `navigate()`, `navigate(targetIid)`, `toggleStyle(styleName)`, `invoke(actionName)`.

### Context Menus

Right-click menus are declared with `@Scene.ContextMenu` annotations (repeatable). Each entry specifies:

| Field | Type | Description |
|-------|------|-------------|
| `label` | string | Menu item text |
| `action` | string | Verb to dispatch on click |
| `target` | string | Target reference |
| `when` | string | Condition for visibility |
| `icon` | string | Optional icon/emoji |
| `group` | string | Grouping label (separators between groups) |
| `order` | int | Sort order within group |

Context menu items compile to `ContextMenuItem` objects stored on the `ViewNode`. Renderers present them as floating menus on right-click, with conditional items filtered by `when` expressions.

### Borders

Borders are specified per-side with style, width, color, and radius:

**BorderSide:**

| Field | Type | Description |
|-------|------|-------------|
| `style` | string | "solid", "dashed", "dotted", "none" |
| `width` | string | "1px", "2px", etc. |
| `color` | string | Color value |

**BoxBorder:**

| Field | Type | Description |
|-------|------|-------------|
| `top` | BorderSide | Top edge |
| `right` | BorderSide | Right edge |
| `bottom` | BorderSide | Bottom edge |
| `left` | BorderSide | Left edge |
| `radius` | string | Corner radius ("small", "medium", "large", "pill", or explicit) |

Shorthand parsing: `"1px solid #333"` → all four sides with that style.

### Shape Properties

Shapes can be applied to containers:

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | "rectangle", "circle", "ellipse", "pill", "line", "path", "polygon", "point", "cone", "capsule" |
| `cornerRadius` | string | "small", "medium", "large", "pill", or explicit |
| `fill` | string | Fill color |
| `stroke` | string | Stroke color |
| `strokeWidth` | string | Stroke width |
| `d` | string | SVG path data (for "path" type) |

### Conditional Styling

Styles can be applied conditionally based on state, platform, or viewport:

**State conditions:**
- `:selected` — element is selected
- `:hover` — pointer is over element
- `:focused` — element has keyboard focus

**Platform conditions:**
- `!gui` — apply only in graphical renderers
- `!tui` — apply only in terminal renderers

**Responsive conditions:**
- `@narrow` — viewport is narrow
- `@wide` — viewport is wide

**Value conditions (in `@Scene.State`):**
- `"value"` — truthy check
- `"!value"` — falsy check
- `"value.selected"` — property path check
- `"$editable"` — schema property check
- `"$index == selectedIndex"` — equality comparison

Conditional style properties:

| Property | Values |
|----------|--------|
| `display` | show/hide |
| `opacity` | 0.0—1.0 |
| `color` | color value |
| `background` | color value |
| `font` | "normal", "bold" |
| `decoration` | "none", "underline", "strikethrough" |
| `padding` | spacing value |
| `margin` | spacing value |
| `border` | border shorthand |
| `radius` | corner radius |
| `content` | text override |
| `icon` | icon override |

### Binding Expressions

Surfaces can bind to data values rather than hardcoding content:

| Expression | Meaning |
|------------|---------|
| `"name"` | Property path on bound value |
| `"address.city"` | Nested property path |
| `"tags[0]"` | Indexed access |
| `"$label"` | Schema property |
| `"$id"` | Schema property |
| `"$item"` | Current item in repeat context |
| `"$index"` | Current index in repeat context |
| `"icon != null"` | Conditional expression |
| `"expanded ? '▼ ' : '▶ '"` | Ternary expression |

### Layout Systems

Three layout systems position elements within containers:

#### Constraint Layout

Edge-based positioning (like iOS Auto Layout). Each element declares its edges relative to the container or siblings.

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Element identifier for sibling references |
| `top` | string | Distance from container top ("0", "50%") |
| `bottom` | string | Distance from container bottom |
| `left` | string | Distance from container left |
| `right` | string | Distance from container right |
| `topTo` | string | Attach to sibling edge ("header.bottom") |
| `bottomTo` | string | Attach to sibling edge ("prompt.top") |
| `leftTo` | string | Attach to sibling edge ("tree.right") |
| `rightTo` | string | Attach to sibling edge |
| `width` | string | Explicit width ("auto", "300px", "50%") |
| `height` | string | Explicit height ("fit", "300px") |
| `minWidth` | string | Minimum width |
| `minHeight` | string | Minimum height |
| `maxWidth` | string | Maximum width |
| `maxHeight` | string | Maximum height |
| `alignX` | string | Horizontal alignment within bounds |
| `alignY` | string | Vertical alignment within bounds |
| `zIndex` | int | Stacking order (default: 0) |

Example: a four-region layout:

```
header:  top="0", left="0", right="100%", height="fit"
tree:    topTo="header.bottom", bottomTo="prompt.top", left="0", width="auto"
detail:  topTo="header.bottom", bottomTo="prompt.top", leftTo="tree.right", right="100%"
prompt:  bottom="0", left="0", right="100%", height="fit"
```

#### Grid Layout

CSS Grid-style template areas.

| Field | Type | Description |
|-------|------|-------------|
| `areas` | string | Template: `"header header / tree detail / prompt prompt"` |
| `columns` | string | Column sizes: `"250px 1fr"` |
| `rows` | string | Row sizes: `"auto 1fr auto"` |
| `gap` | string | Gap between cells |

Each element declares its grid placement:

| Field | Type | Description |
|-------|------|-------------|
| `area` | string | Named area from template |
| `column` | string | Column position |
| `row` | string | Row position |
| `justify` | string | Horizontal alignment in cell |
| `align` | string | Vertical alignment in cell |

#### Flex Layout

CSS Flexbox-style flow layout.

Container properties:

| Field | Type | Description |
|-------|------|-------------|
| `direction` | Direction | VERTICAL or HORIZONTAL |
| `justify` | string | Main-axis alignment |
| `align` | string | Cross-axis alignment |
| `gap` | string | Space between items |
| `wrap` | boolean | Whether items wrap to new lines |

Item properties:

| Field | Type | Description |
|-------|------|-------------|
| `size` | string | Fixed size |
| `grow` | int | Flex grow factor |
| `shrink` | int | Flex shrink factor |
| `basis` | string | Initial size before grow/shrink |
| `order` | int | Display order |
| `alignSelf` | string | Per-item cross-axis override |

---

## Body and Space (3D)

The 3D layer consists of Bodies (visible geometry) and Spaces (spatial environments).

### Common Fields

Present on every 3D element:

| Field | Type | Default | Canon | Description |
|-------|------|---------|-------|-------------|
| `value` | generic `T` | null | -1 | Bound data value |
| `id` | string | null | 0 | Element identifier |
| `style` | string list | [] | 1 | Style classes |
| `visible` | boolean | true | 2 | Whether element is rendered |
| `scaleX` | double | 1.0 | 10 | Scale along X axis |
| `scaleY` | double | 1.0 | 11 | Scale along Y axis |
| `scaleZ` | double | 1.0 | 12 | Scale along Z axis |

### 3D Elements

#### Body

A visible 3D shape — either a primitive or a mesh reference.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `shape` | string | — | Primitive: "box", "sphere", "cylinder", "plane", "none" |
| `width` | string | — | Width (e.g., "44cm", "1m") |
| `height` | string | — | Height |
| `depth` | string | — | Depth |
| `radius` | string | — | Radius for sphere/cylinder |
| `mesh` | string | "" | ContentID reference to mesh asset (overrides shape) |
| `color` | int | 0x808080 | Hex RGB color (0xRRGGBB) |
| `opacity` | double | 1.0 | 0.0 = transparent, 1.0 = opaque |
| `shading` | string | "lit" | "lit", "unlit", "wireframe" |

Renderer call: `body(shape, w, h, d, color, opacity, shading, styles)` for primitives, `meshBody(meshRef, color, opacity, shading, styles)` for meshes.

#### Light

A light source in the scene.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `type` | string | "directional" | "directional", "point", "spot" |
| `color` | int | 0xFFFFFF | Light color (hex RGB) |
| `intensity` | double | 1.0 | Brightness (1.0 = standard) |
| `x` | string | "" | Position X (for point/spot) |
| `y` | string | "" | Position Y (for point/spot) |
| `z` | string | "" | Position Z (for point/spot) |
| `dirX` | double | 0 | Direction X (for directional/spot) |
| `dirY` | double | -1 | Direction Y (for directional/spot) |
| `dirZ` | double | 0 | Direction Z (for directional/spot) |

#### Camera

The viewpoint into the scene.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `projection` | string | "perspective" | "perspective" or "orthographic" |
| `fov` | double | 60 | Field of view in degrees (perspective only) |
| `near` | string | "0.1m" | Near clipping plane distance |
| `far` | string | "1000m" | Far clipping plane distance |
| `x` | string | "" | Camera position X |
| `y` | string | "" | Camera position Y |
| `z` | string | "" | Camera position Z |
| `targetX` | string | "" | Look-at target X |
| `targetY` | string | "" | Look-at target Y |
| `targetZ` | string | "" | Look-at target Z |

#### Environment

Global scene properties.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `background` | int | 0xF2F2F2 | Background color (hex RGB) |
| `ambient` | int | 0x808080 | Ambient light color (hex RGB) |
| `fogNear` | string | "" | Fog start distance (empty = no fog) |
| `fogFar` | string | "" | Fog end distance (empty = no fog) |
| `fogColor` | int | 0x808080 | Fog color (hex RGB) |

#### Audio

Positional sound source in 3D space.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `src` | string | "" | Content reference (handle name or ContentID) |
| `x` | string | "" | Position X |
| `y` | string | "" | Position Y |
| `z` | string | "" | Position Z |
| `volume` | double | 1.0 | Volume (0.0 silent — 1.0 full) |
| `pitch` | double | 1.0 | Playback speed multiplier |
| `loop` | boolean | false | Whether to loop |
| `spatial` | boolean | true | Apply 3D spatialization (false = stereo, no falloff) |
| `refDistance` | string | "1m" | Distance where attenuation begins |
| `maxDistance` | string | "50m" | Maximum audible distance |
| `autoplay` | boolean | false | Start playing immediately |

**Distance attenuation model:** inverse distance clamped.

```
gain = refDistance / max(distance, refDistance)
gain = clamp(gain, 0.0, 1.0)
```

At `refDistance`, volume is full. Beyond `maxDistance`, the source is silent. Between them, volume falls off inversely with distance.

Non-spatial sources (`spatial=false`) play at constant volume regardless of listener position — use for background music or UI sounds.

#### Face

A 2D Surface rendered on a specific face of a parent Body.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `value` | string | "top" | Face name: "front", "back", "top", "bottom", "left", "right" |
| `ppm` | int | 512 | Pixels per meter (rendering resolution) |

`beginFace()` opens a 2D rendering context inside a 3D scene. The caller renders 2D content into the face's texture. `endFace()` commits the texture and applies it to the appropriate face of the parent body. This is how 2D content gets embedded in 3D space.

Without a Face, children default to the top face. In 2D, face assignments are ignored — children render in source order.

#### Transform

Explicit 3D positioning, rotation, and scale.

**Position:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `x` | string | "" | Position X (physical units, e.g., "1m") |
| `y` | string | "" | Position Y |
| `z` | string | "" | Position Z |

**Rotation** (Euler angles, degrees):

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `yaw` | double | 0 | Rotation around Y axis |
| `pitch` | double | 0 | Rotation around X axis |
| `roll` | double | 0 | Rotation around Z axis |

**Rotation** (axis-angle, alternative):

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `axisX` | double | 0 | Rotation axis X |
| `axisY` | double | 1 | Rotation axis Y |
| `axisZ` | double | 0 | Rotation axis Z |
| `angle` | double | 0 | Rotation angle in degrees |

**Scale:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `scaleX` | double | 1 | Scale along X |
| `scaleY` | double | 1 | Scale along Y |
| `scaleZ` | double | 1 | Scale along Z |

### Transform Stack

3D rendering uses a hierarchical transform stack. Transforms compose by matrix multiplication:

```
pushTransform(x, y, z, qx, qy, qz, qw, sx, sy, sz)
  // all rendering here is relative to the pushed transform
  pushTransform(...)  // nested: multiplied with parent
    // ...
  popTransform()
popTransform()
```

Each transform is specified as position (translation) + rotation (unit quaternion) + scale (per-axis). Internally this builds a column-major 4x4 matrix:

```
M = Translation * Rotation * Scale
```

Parent transforms multiply with children: `M_world = M_parent * M_child`.

**Quaternion conventions:**
- `(qx, qy, qz, qw)` where `qw` is the scalar component
- Identity rotation: `(0, 0, 0, 1)`
- Must be unit magnitude: `sqrt(qx² + qy² + qz² + qw²) ≈ 1.0`
- 90 degrees around Y axis: `(0, 0.7071, 0, 0.7071)`

**Euler-to-quaternion conversion:** ZYX order (yaw first, then pitch, then roll). Angles in degrees.

**Axis-angle-to-quaternion conversion:** axis is normalized, angle in degrees. `q = (axis * sin(angle/2), cos(angle/2))`.

---

## The Mount System

Components within an Item are positioned using **mounts** — presentation descriptors that say *where* a component appears within the Scene. A single component can have multiple mounts of different types, appearing simultaneously in tree views, 2D Surfaces, and 3D Spaces.

### Mount Types

#### PathMount

Tree hierarchy position. Filesystem-like path strings.

| Field | Type | Description |
|-------|------|-------------|
| `path` | string | Canonical path (e.g., "/documents/notes") |

CBOR encoding: `["path", <path-string>]`

Paths are canonicalized: leading slash ensured, trailing slash removed (except root "/"), multiple slashes collapsed, `.` and `..` resolved.

Depth is the number of path segments: "/" = 0, "/documents" = 1, "/documents/notes" = 2.

#### SurfaceMount

Named 2D region placement.

| Field | Type | Description |
|-------|------|-------------|
| `region` | string | Named UI region (e.g., "sidebar", "header", "footer") |
| `order` | int | Display order within region |

CBOR encoding: `["surface", <region>, <order>]`

#### SpatialMount

3D position and rotation.

| Field | Type | Description |
|-------|------|-------------|
| `x` | double | Position X (meters) |
| `y` | double | Position Y (meters) |
| `z` | double | Position Z (meters) |
| `qx` | double | Quaternion X |
| `qy` | double | Quaternion Y |
| `qz` | double | Quaternion Z |
| `qw` | double | Quaternion W (scalar) |

CBOR encoding: `["spatial", <x>, <y>, <z>, <qx>, <qy>, <qz>, <qw>]`

Backward compatible: a 4-element array `["spatial", x, y, z]` assumes identity rotation `(0, 0, 0, 1)`.

Construction helpers:
- **Position-only:** `SpatialMount(x, y, z)` — identity rotation
- **Euler angles:** `fromEuler(x, y, z, yaw, pitch, roll)` — degrees, Z-up (X=right, Y=forward, Z=up), ZYX order
- **Axis-angle:** `fromAxisAngle(x, y, z, axisX, axisY, axisZ, angle)` — degrees

### Mount Resolution

Mounts live on `ContentEntry` — the metadata record for each component in an Item's content table:

| Field | Type | Canon | Description |
|-------|------|-------|-------------|
| `handle` | HandleID | 0 | Local handle (unique within item) |
| `type` | ItemID | 1 | Component type reference |
| `identity` | boolean | 2 | Contributes to version identity? |
| `snapshotCid` | ContentID | 3 | Content hash for snapshot |
| `streamHeads` | ContentID list | 4 | Stream head hashes |
| `streamBased` | boolean | 5 | Is stream-based? |
| `handleText` | string | 6 | Display name |
| `referenceTarget` | ItemID | 7 | Target for reference components |
| `mounts` | Mount list | 8 | Presentation positions |

When a component is spatially mounted, the renderer reads its type annotations and emits:
- **Body** (geometry) at the mount position
- **Light** (if the component type declares one) offset from mount position
- **Audio** (if the component type declares one) offset from mount position
- **Face fallback** (if no body is declared) at the mount position

This is how mounting a "light sconce" component 5 times at different positions gives you 5 sconces with 5 lights — the component type defines the appearance once, and each mount instantiates it.

### Tree Navigation via PathMounts

The content table supports tree navigation through path mounts:
- `roots()` — entries with depth-1 paths (immediate children of "/")
- `atPath(path)` — entry at an exact path
- `children(parentPath)` — entries one level below a path
- `descendants(path)` — all entries recursively below a path
- `hasChildren(path)` — whether a path has children
- `pathForHandle(handle)` — reverse lookup: handle → path

An entry with multiple PathMounts appears at each location in tree navigation, but is stored once.

---

## Renderer Interfaces

Two platform-agnostic interfaces define the instruction sets that renderers implement. They are push-based: the schema calls methods sequentially, the renderer translates to platform API calls.

### SceneRenderer

`SceneRenderer extends SurfaceRenderer` — inherits all 2D methods and adds 3D methods as **default no-ops**. This is the renderer interface.

```
// Inherited from SurfaceRenderer: text, image, beginBox, endBox, etc.

// 3D additions (all default no-op):
depth(meters, solid)
body(shape, w, h, d, color, opacity, shading, styles)
meshBody(meshRef, color, opacity, shading, styles)
line(x1, y1, z1, x2, y2, z2, color, width)
pushTransform(x, y, z, qx, qy, qz, qw, sx, sy, sz)
popTransform()
beginFace(face, ppm)
endFace()
light(type, color, intensity, x, y, z, dirX, dirY, dirZ)
camera(projection, fov, near, far, x, y, z, tx, ty, tz)
environment(background, ambient, fogNear, fogFar, fogColor)
audio3d(src, x, y, z, volume, pitch, loop, spatial, refDist, maxDist, autoplay)
supportsDepth() → boolean
renderContext() → RenderContext
```

2D renderers (Skia, CLI, TUI) implement `SceneRenderer` — the default no-ops mean they only need to implement the 2D methods. 3D-capable renderers (Filament, WebGL) override the 3D methods.

### SurfaceRenderer (2D)

The base 2D rendering interface:

```
text(content, styles)
formattedText(content, format, styles)
richText(spans, paragraphStyles)
image(alt, image, solid, resource, size, fit, styles)
beginBox(direction, styles)
beginBox(direction, styles, border, background, width, height, padding)
endBox()
audio(src, volume, loop, styles)
shape(type, cornerRadius, fill, stroke, strokeWidth, path)
model(modelResource, modelColor)
transitions(specs)
type(type)
id(id)
editable(editable)
event(on, action, target)
gap(gap)
```

Rendering flow:
1. `type()` / `id()` / `editable()` set metadata for the next element
2. `event()` adds handlers for the next element
3. Primitive call (`text`, `richText`, `image`, `beginBox`, `audio`) emits the element
4. `endBox()` closes a container
5. `shape()` sets shape properties for the next container
6. `model()` sets 3D hints for the next element (used by 3D renderers)
7. `gap()` sets spacing for the current container
8. `transitions()` declares animation specs for the next element

Rendering order for a typical 3D scene:
1. `environment()` — set background, ambient, fog
2. `light()` — add light sources
3. `camera()` — set viewpoint
4. For each element: `pushTransform()` → `body()` / `beginFace()` → `popTransform()`

---

## The Model Layer

A `SceneModel` holds state and produces schema snapshots for rendering. It bridges user interaction with declarative rendering.

### Lifecycle

A `SceneSchema` subclass with `@Scene.*` annotations renders directly via `schema.render(SceneRenderer)`. The SceneCompiler handles both 2D and 3D output in a single pass.

```
   ┌─────────────────────────────────────────────────┐
   │                                                 │
   ▼                                                 │
schema.render(out)  →  draw  →  user input  →  handleEvent() / handleKey()
                                                    │
                                                    ▼
                                                 state change → changed() → re-render
```

1. The schema renders to a SceneRenderer
2. The renderer draws the output
3. User interaction arrives as events (`handleEvent(action, target)`) or key chords (`handleKey(chord)`)
4. The model updates its internal state
5. `changed()` notifies listeners, triggering a re-render
6. Loop continues

### Batching

Multiple state changes can be batched to produce a single re-render:

```
model.beginBatch()
  // multiple state changes...
model.endBatch()
// single changed() notification
```

### Input

Input is platform-agnostic. A `KeyChord` combines a physical key with modifiers:

| Component | Type | Values |
|-----------|------|--------|
| Physical key | enum | Letters, digits, function keys, arrows, etc. |
| Ctrl | boolean | Control modifier |
| Alt | boolean | Alt/Option modifier |
| Shift | boolean | Shift modifier |

Tree navigation example (handled by TreeModel):
- UP/DOWN — select previous/next visible node
- LEFT — collapse current or select parent
- RIGHT — expand current or select first child
- ENTER/SPACE — toggle expansion
- HOME/END — select first/last visible node
- `*` — expand all
- `-` — collapse all

---

## Schema-Driven Editing

The `editing` package provides automatic form generation for any `Canonical` type based on its `@Canon` field schema. This is the foundation for type-carried editing surfaces — every type can declare how its instances are edited.

### EditModel

`EditModel` extends `SceneModel<SurfaceSchema>` and wraps a mutable `Canonical` instance. It provides:

- `target()` — the wrapped Canonical being edited
- `schema()` — the `CanonicalSchema` with field metadata
- `get(fieldName)` — read a field value by name
- `set(fieldName, value)` — write a field value (fires change notification)
- `toSurface()` — produces a `CanonicalEditorSurface`
- `handleEvent(action, target)` — dispatches field-level editing events

**Event convention:** field events are prefixed with the operation type:

| Event | Behavior |
|-------|----------|
| `"toggle:<field>"` | Flip a boolean field |
| `"select:<field>"` | Set an enum field (target = constant name) |
| `"set:<field>"` | Set a string/value field (target = new value) |

### CanonicalEditorSurface

A procedural `SurfaceSchema<Void>` that renders a form with type-appropriate widgets per field:

| Field type | Widget | Editable? |
|-----------|--------|-----------|
| `boolean` | Toggle (ON/OFF text) | Yes — click fires `toggle:<field>` |
| `enum` | Option list (vertical, selected highlighted) | Yes — click fires `select:<field>` |
| `String` | Text display | Display-only |
| `int`/`long`/etc. | Text display | Display-only |
| `Canonical` | Type name | Display-only |
| `List<T>` | Item count "[N items]" | Display-only |
| Other | `toString()` fallback | Display-only |

Layout: vertical container of rows. Non-enum fields use horizontal rows (label + widget). Enum fields use vertical blocks (label above options list).

### CanonicalSchema Enrichment

`CanonicalSchema.FieldSchema` provides type introspection used by the editing system:

| Method | Purpose |
|--------|---------|
| `isBoolean()` | `boolean` or `Boolean` |
| `isString()` | `String` |
| `isNumeric()` | Any numeric primitive/wrapper or `Number` subclass |
| `isEnum()` | Enum type |
| `isCanonical()` | Implements `Canonical` |
| `isCollection()` | `Iterable` or array |
| `enumConstants()` | Array of enum values (or null) |
| `elementType()` | Extract `T` from `List<T>` |
| `displayName()` | camelCase → "Title Case" (e.g., "darkMode" → "Dark Mode") |
| `getValue(obj)` | Reflective field read |
| `setValue(obj, val)` | Reflective field write |

### Round-Trip

The editing flow is fully round-trippable:

```
Canonical object
    → EditModel(object)
    → user mutates via events (toggle, select, set)
    → object.encodeBinary(Scope.RECORD)
    → Canonical.decodeBinary(bytes, Class)
    → values preserved
```

---

## Style System

### Style Rules

Style rules are declared on types using `@Scene.Rule` annotations:

```java
@Scene.Rule(selector = ".heading", color = "#CDD6F4", fontSize = "1.2em", fontWeight = "bold")
@Scene.Rule(selector = ".muted", color = "#6C7086")
@Scene.Rule(selector = ".selected", color = "#CDD6F4", background = "#45475A")
```

Rules support:

| Property | Type | Description |
|----------|------|-------------|
| `selector` | string | CSS-like selector (type, class, state, renderer, breakpoint) |
| `color` | string | Text color |
| `background` | string | Background color |
| `fontSize` | string | Font size |
| `fontFamily` | string | Font family |
| `fontWeight` | string | "normal" or "bold" |
| `display` | string | "none" to hide |
| `opacity` | string | 0.0 to 1.0 |
| `rotation` | string | Rotation in degrees |

Rules are scanned from the classpath at startup via `Stylesheet.fromClasspath()`. Selector specificity: ID=100, class/state/renderer/breakpoint=10, type=1. The `!renderer` qualifier enables platform-specific styling.

### StyleResolver

`StyleResolver` walks the `LayoutNode` tree **before layout**, resolving style classes to concrete visual values. After resolution, each node carries:

- `color` (int) — resolved text color
- `backgroundColor` (int) — resolved background color
- `fontSize` (float) — resolved font size
- `fontFamily` (String) — resolved font family
- `bold` (boolean) — resolved font weight

Painters read these resolved fields directly — they never inspect style class names. This decouples style knowledge from platform-specific rendering code.

---

## CBOR Serialization

All schemas serialize to CG-CBOR for storage and transmission. This section specifies how to produce compatible schemas without Java.

### Encoding Rules

1. **Field order is deterministic.** Each field has a `Canon(order=N)` value. Fields serialize as CBOR arrays in ascending order. This is critical: the same data must always produce the same bytes (for hashing).

2. **No IEEE 754 floats.** Use CBOR integers where possible. Where fractional values are needed, use CBOR Tag 4 (decimal fraction) or integer-scaled values. No NaN, no Infinity, no negative zero.

3. **Deterministic map key ordering.** When maps appear, keys are sorted by their CBOR canonical encoding (shortest first, then lexicographic).

4. **No indefinite-length encoding.** All arrays and strings must specify their length upfront.

5. **CG-CBOR tags:**
   - Tag 6 — REF (reference to another item/component)
   - Tag 7 — VALUE (typed value wrapper)
   - Tag 8 — SIG (signature envelope)
   - Tag 9 — QTY (quantity with unit)

6. **ContentID references** are multihash byte strings (SHA2-256 hash of content bytes).

7. **Mount discriminated arrays:**
   - PathMount: `["path", <string>]`
   - SurfaceMount: `["surface", <string>, <int>]`
   - SpatialMount: `["spatial", <x>, <y>, <z>, <qx>, <qy>, <qz>, <qw>]`

8. **The schema IS the encoding.** There is no separate schema definition language. The CBOR structure of a Surface or Scene element is its complete definition. A Text element is a CBOR array with fields at their Canon-order positions. A Box element is a CBOR array with a direction, gap, focus flag, dimensions, and a nested array of child elements.

### Producing a Surface from Scratch

To create a simple text element without Java:

```
CBOR array [
  null,       // value (Canon -1)
  null,       // id (Canon 0)
  null,       // style (Canon 1)
  true,       // visible (Canon 2)
  false,      // editable (Canon 3)
  null,       // tabbable (Canon 4)
  null,       // label (Canon 5)
  null,       // labeledBy (Canon 5)
  null,       // size (Canon 6)
  null,       // margin (Canon 7)
  null,       // padding (Canon 8)
  null,       // events (Canon 9)
  1.0,        // scaleX (Canon 10) — encoded as integer 1
  1.0,        // scaleY (Canon 11) — encoded as integer 1
  "Hello",    // content (Canon 10, TextSurface-specific)
  "plain"     // format (Canon 11, TextSurface-specific)
]
```

The exact layout depends on the class hierarchy: base fields first, then subclass fields. Implementers should study the Canon order values listed in this document.

### ViewNode Serialization

`ViewNode` uses MAP encoding (not ARRAY) for compactness — omitting default/null fields:

| Range | Fields |
|-------|--------|
| 0–9 | Core: type, id, classes, direction, gap, align, background, padding, shape |
| 10–19 | Container: depth, cornerRadius, rotation, transformOrigin, size, width, height |
| 20–29 | Shape: shapeType, fill, stroke, strokeWidth, pathData |
| 30–34 | Text: content, format, spans |
| 35–39 | Image: alt, imageRef, resource, fit, modelResource, modelColor |
| 40–49 | Binding: bind, repeatBind, itemVar, indexVar, embedBind, ifCondition, queryCondition |
| 50–59 | Border |
| 60–69 | Sizing: minWidth, minHeight, maxWidth, maxHeight |
| 70–100 | Events, states, styles |
| 120 | Context menu items |
| 200+ | 3D: body, face, transform, light, audio, environment, camera |

### Producing a SpatialMount

```
CBOR array [
  "spatial",  // discriminator
  1.0,        // x position (meters)
  2.0,        // y position
  3.0,        // z position
  0.0,        // qx (quaternion)
  0.0,        // qy
  0.0,        // qz
  1.0         // qw (identity rotation)
]
```

---

## Content Addressing for Assets

Meshes, images, and audio are all content-addressed: stored by hash, referenced by ContentID.

- **ContentID** = multihash byte string (SHA2-256 hash of the content bytes)
- Stored in the Library's ItemStore as content blocks
- Looked up by hash at render time
- Image fields use `ContentID` for the `image` and `solid` references
- Body `mesh` field references a ContentID for custom geometry
- Audio `src` references a content handle or ContentID

**Deduplication:** the same mesh used by 100 items is stored once. The same audio clip mounted at 50 positions in a scene is loaded into one buffer and played from 50 sources.

**Supported audio formats:** WAV (PCM: mono/stereo, 8/16-bit), OGG Vorbis.

---

## Concrete Scene Compositions

### ItemSpace (Default for Any Item)

The default 3D presentation for any Item: a white room with the item's components arranged spatially.

| Property | Value | Description |
|----------|-------|-------------|
| Background | 0xF2F2F2 | Light gray |
| Ambient light | 0x808080 | Medium gray |
| Light type | directional | From above |
| Light Z | 5m | High above scene (Z-up) |
| Light dirZ | -1 | Pointing down |
| Light intensity | 0.8 | Slightly below full |
| Camera Y | 5m | Forward distance |
| Camera Z | 1.5m | Eye height |
| Camera target | origin | Looking toward center |

The camera is deliberately close — the scene feels like a 2D view until the user orbits or zooms. This gives a gentle on-ramp to 3D.

**Component rendering:** iterates each content entry with a SpatialMount. For each:
1. `pushTransform()` at the mount's position and rotation
2. If the component type has a `@Scene.Body`: render the body
3. If no body: render a Face fallback (2D surface in 3D space)
4. If the component type has a `@Scene.Light`: add the light
5. If the component type has a `@Scene.Audio`: add the audio source
6. `popTransform()`

### TreeBody (Botanical Tree)

A 3D tree visualization where nodes are spheres and connections are cylinders, arranged in a botanical branching pattern.

**Layout algorithm:** radial cone layout. Root at the bottom, children spread upward and outward.

| Constant | Value | Description |
|----------|-------|-------------|
| Level height | 1.5 meters | Vertical spacing between levels |
| Base radius | 1.5 meters | Horizontal spread at root level |
| Radius decay | 0.7 | Spread reduction per level |
| Base node size | 0.2 meters | Node radius at root level |
| Node size decay | 0.85 | Size reduction per level |
| Selected scale | 1.3x | Scale multiplier for selected nodes |
| Branch color | 0x8B6914 | Brown |
| Branch radius | 0.03 meters | Cylinder thickness |

**Environment:** same as ItemSpace. Camera at Y=6m, Z=3m, looking at Z=2m — farther back to see the tree structure.

**Node coloring:** deterministic hash-based color generation from the node's label text. Hue distributed across [0, 360), saturation 0.45-0.65, value 0.6-0.8. Nodes without labels fall back to 0x78788C (gray-blue).

**Layout rules:**
- Single root: placed at origin (0, 0, 0)
- Multiple roots: arranged in a ring at Z=0
- Children of expanded nodes spread in angular sectors above their parent
- Single child: offset slightly from parent
- Multiple children: distributed evenly within the parent's angular sector
- Collapsed nodes have no children rendered

**Rendering order:**
1. Environment + light + camera
2. Edges (branches): each rendered as a cylinder oriented between parent and child positions
3. Nodes: each rendered as a sphere (or custom shape via NodeAppearance callback) at its computed position

### Camera Controller

The default camera controller provides two modes:

**Orbit mode** (default): camera orbits around a target point.
- Left-drag: rotate (yaw + pitch)
- Scroll: zoom (change orbit distance)
- Auto-transitions to fly mode when zoomed closer than 0.5 meters

**Fly mode**: free first-person movement.
- WASD: forward/back/left/right (3.0 m/s)
- Q/E: down/up
- Mouse drag: rotate view
- Escape: return to orbit mode

---

## Text Interface (CLI and TUI)

The text tier renders Surfaces to terminal output. Two modes serve different contexts:

### CLI Mode (Scrolling)

Plain-text scrolling output, no screen clearing. Surfaces render as indented text with Unicode tree-drawing characters (`│`, `▼`, `▶`). No colors, no borders. Works everywhere — piped output, screen readers, log files.

### TUI Mode (Full-Screen)

Full-screen ANSI rendering with raw terminal mode:

- **Box-drawing borders**: Unicode line characters (`┌─┐│└─┘`), with rounded corners when the Surface specifies border radius
- **ANSI colors**: Style classes map to terminal colors (red, green, blue, cyan, yellow, magenta, bold, dim, italic, underline, inverse)
- **Padding**: Boxes respect padding values, rendered as empty space within borders
- **Mouse support**: Click events via terminal mouse tracking — hit-testing maps cursor position to Surface elements and dispatches events
- **Full re-render**: The entire screen is cleared and re-drawn on each state change (no partial updates)

Example TUI output:

```
┌─ Project ──────────────────────┐
│ ▼ 📁 Documents                 │
│   ├── 📄 README.md             │
│   ├── 📄 design.md             │
│   └── 📁 src/                  │
│ ▶ 📋 Tasks                     │
│ ▶ 💬 Discussion                │
├────────────────────────────────┤
│ alice@project> _               │
└────────────────────────────────┘
```

### The Expression Prompt

Both CLI and TUI modes share the same expression input system (see [Vocabulary](vocabulary.md) for the full specification). The prompt renders as:

```
♟️ chess> [move] [pawn] to e|
  → e4
    e5
    e3
```

| Element | Rendering |
|---------|-----------|
| **Prompt** | Cyan text with Item's emoji and display name |
| **Resolved tokens** | Yellow `[bracketed]` chips |
| **Pending text** | Plain text with inverse-video cursor |
| **Completions** | Indented list below prompt; `→` marks selection |
| **Selected completion** | Highlighted (white-on-blue in TUI) |
| **Error message** | Red text above the prompt |

The prompt is context-aware — it updates when you navigate into items, showing the current Item's emoji and name. Completions update in real-time as you type, semantically narrowed by the expression context.

### Style Mapping

Surface style classes map to ANSI attributes:

| Style Class | ANSI Rendering |
|-------------|----------------|
| `"heading"` | Bold |
| `"muted"` | Dim |
| `"bold"` | Bold |
| `"italic"` | Italic |
| `"code"`, `"mono"` | (rendered inline, no special formatting) |
| `"primary"` | Cyan |
| `"danger"` | Red |
| `"success"` | Green |
| `"warning"` | Yellow |
| `"selected"` | Inverse video |
| `"disabled"` | Dim |

### Surface-to-Text Degradation

Any Surface degrades to text. The TUI renderer maps Surface primitives to terminal output:

| Surface Primitive | TUI Rendering |
|-------------------|---------------|
| **Text** | Direct text output, styled with ANSI codes |
| **Image** | Alt text (emoji fallback) |
| **Container (vertical)** | Children on separate lines |
| **Container (horizontal)** | Children space-separated on one line |
| **Border** | Box-drawing characters around content |
| **Button** | Bracketed text: `[Click me]` |
| **Tree node** | Indented with `▼`/`▶` expand indicators |
| **List item** | Indented with optional selection highlight |
| **Handle** | `emoji label` on one line |

Conditional styles with `!tui` or `!gui` platform conditions allow surfaces to provide terminal-specific or graphical-specific styling.

---

## Policy and Access Control

Items carry a `PolicySet` component that defines access rules. The `ItemPolicyResolver` resolves policy subjects against live item state:

| Subject | Resolution |
|---------|------------|
| `"owner"` | Checks `PolicySet.authority().ownerId()` |
| `"participants"` | Checks membership in the item's `Roster` component |
| `"hosts"` | Compares against the local librarian's IID |
| Explicit ID | Direct equality check |

Policy checks are performed via `PolicyEngine.check(accessPolicy, resolver, itemId, callerId, action)`.

---

## Design Principles

**NO TABS.** Tabs are forbidden — they're a holdover from paper manila folders.

**Accordion model instead:**
- Items shown collapsed as handles (small icon + minimal text + badges + hover info)
- Click to expand — pushes other items up/down in the container
- Drag (or key combo) to move to "main view" for full interaction
- Same concept applies to TUI, just with fewer items visible

**Every item has its own prompt** — typing into it dispatches the item's verbs. That's the item's API surface.

**Items declare their rendering.** The class IS the definition: `@Scene.*` annotations + component fields + verb methods define everything. No separate handler/descriptor needed. A WYSIWYG editor or text tool could produce the same CBOR schema that the annotations generate.

**Types carry their own editing surfaces.** Every type can declare how its instances are edited. The `editing` package provides the foundation: `EditModel` wraps any `Canonical` for form-based editing, `CanonicalEditorSurface` renders type-appropriate widgets per field. In the future, type Items will carry edit surfaces as components — users can fork/override any type's editing UI.
