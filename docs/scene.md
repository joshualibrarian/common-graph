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
| Light | Positioned light source with real shadows | Direction and color reference for derived drop shadows | Depth hints (border weight, ANSI emphasis) |
| Elevation | Perpendicular Z displacement (positive = raised, negative = recessed) | Drop shadow (positive) or inner shadow (negative), derived from light; z-ordering in stacks | Visual hints |
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

The shorthand `background` sets `backgroundColor`, or if given CSS gradient syntax, sets `backgroundGradient`.

Gradients are structured objects with a type, color stops, and geometry:

**Linear gradient:** angle in degrees + color stops
```
background: "linear-gradient(45deg, #000, #fff)"
background: "linear-gradient(to right, red 0%, blue 50%, green 100%)"
```

**Radial gradient:** shape + position + color stops
```
background: "radial-gradient(circle, #000, #fff)"
background: "radial-gradient(ellipse at top left, red, blue)"
```

Gradient paints over `backgroundColor` when both are set. The `backgroundGradient` field carries a structured `Gradient` object in CBOR — not a string.

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
| elevation | Dimensional | Raises (positive) or recesses (negative) the node. Drives drop shadows in 2D, real Z displacement in 3D. See [Elevation and Lighting](#elevation-and-lighting). |
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

### Elevation and Lighting

Elevation is a dimensional property expressing how far a node rises above (or sinks below) its parent surface. The same declaration produces meaningful visuals across every fidelity level.

**Elevation values:**
- **Positive** (e.g., `"4px"`, `"1cm"`) — raised above the surface. In 3D, literal Z displacement. In 2D, drop shadow. In text, represented as visual hints (bold borders, bright colors).
- **Zero** — flat. No shadow.
- **Negative** (e.g., `"-2px"`) — recessed into the surface. In 3D, literal Z depression. In 2D, inner shadow (the classic "pressed button" effect). A `when = "$active"` style can toggle between positive and negative elevation to produce tactile button feedback without any imperative code.

**The scene light:**

Every scene has a light. If one isn't declared, there's an implicit default: directional, positioned upper-left, neutral color, with a neutral dark ambient. Scene authors can declare their own:

| Light property | Values | Notes |
|----------------|--------|-------|
| type | directional, point, spot | How the light radiates |
| position | (x, y, z) | Light source position in scene space |
| color | Color | Key light color (what the light emits) |
| ambient | Color | Ambient/fill light — the color of light reaching shadowed areas |
| intensity | Number | Strength multiplier |

In 3D, the light is a real Filament light source — it casts physical shadows using the spatial renderer's lighting model.

In 2D, the light is a direction and color reference used to derive drop shadows from elevation. No actual lighting math — just shadow parameters computed from light position relative to each elevated node.

In text rendering, light contributes only to depth hints (box drawing weight, ANSI color emphasis).

**Shadow derivation (2D):**

Drop shadows for positive elevation are fully derived from the scene's light and the node's elevation:

- **Direction** — opposite the light's position (light from upper-left → shadow falls to lower-right)
- **Distance** — proportional to elevation (higher = shadow further from node)
- **Blur** — proportional to elevation (higher = softer edges, reflecting how distant shadows diffuse)
- **Color** — the ambient light color (this is what's lighting the shadowed area)
- **Opacity** — proportional to elevation (higher = darker base shadow, attenuated by ambient brightness)

For negative elevation, the same formula applies but inverted — the shadow becomes an inner shadow with direction reversed (as if light is reaching INTO the depression), giving the recessed appearance.

**Why ambient determines shadow color:**

Shadows in reality aren't just the absence of the key light — they're areas lit by something else (sky light, bounced light, ambient illumination). That "something else" is what gives shadows their color. Outdoor shadows on a sunny day are bluish because sunlight is warm but the sky is cool — and the sky is what's lighting the shadows.

In CG's model, the designer declares this directly: the light's `ambient` property IS the shadow color. No magic formulas, no desaturation constants, no derived heuristics. Declare your lighting, get physically consistent shadows across all fidelities.

**Other effects of elevation:**

- **Stack-layout z-ordering:** Within a stack container, higher elevation draws on top of lower elevation, regardless of declaration order.
- **Pressed button pattern:** `@Scene.Style(elevation = "2px")` plus `@Scene.Style(when = "$active", elevation = "-1px")` gives a raised button that depresses when pressed. Combined with a transition on elevation, the press becomes smoothly animated.

### Video and Audio

Video and audio are body representations alongside image, shape, glyph, and model. Unlike static content, they're continuous media — frames or samples arrive over time from a stream source.

**Body fields:**

| Field | Values | Notes |
|-------|--------|-------|
| video | Resource ref or binding | Stream reference — file, live stream, or capture device |
| audio | Resource ref or binding | Independent of video — may exist with or without video |
| loop | Boolean | Restart on end (prerecorded only; meaningless for live streams) |
| muted | Boolean | Audio silenced locally (state-driven, toggleable via when blocks) |
| volume | Float 0-1 | Audio level (state-driven, animatable via keyframes) |
| paused | Boolean | Exists but not playing (state-driven, default unpaused) |

**Independence of audio and video.** Either can exist without the other. A CCTV camera has `video` without `audio`. Voice chat has `audio` without `video`. A phone call with video has both. If both are set and reference the same source, the runtime can optimize (subscribe once, decode once) but the scene model treats them as independent.

**Subscription lifecycle.** A video or audio body subscribes to its stream when it exists in the rendered scene tree, and unsubscribes when it doesn't. Navigating to a different item implicitly stops all stream subscriptions from the old tree.

**Live vs. prerecorded.** Both use the same scene fields. The distinction is in the resource reference and how the runtime fetches it. Live streams are continuous and `loop` is meaningless. Prerecorded content starts from position 0 when the body enters the tree, and may `loop`.

**Capture devices** are direct local references the runtime understands:

- `camera:default`, `camera:front`, `camera:rear` — local cameras
- `mic:default` — local microphone
- `display:main` — local display (screen sharing)

Like physical displays, capture devices are intrinsic to a host machine. You access them through their host, not directly from another host. To see another participant's camera, you reference a stream they are publishing (typically via a presence record they created), never their hardware directly. This gives a consent-by-construction model — each host decides what streams to publish.

**Fidelity spectrum for video:**

- **Spatial (3D):** Video plays on a plane mesh at the body's position. Audio plays positionally — distance attenuation and directional panning from the camera/listener's position. Body transforms (rotation, scale) apply to the video plane.
- **Graphical (2D):** Video plays in the body's bounds. Audio plays non-positionally (stereo, full volume).
- **Text:** Shows the `alt` description.

**Stream transports are a runtime concern.** The scene model doesn't know or care whether a stream is delivered via WebRTC, CG-native peer streaming, HTTP, or local capture. It just resolves a binding to a stream reference. The painter delegates frame delivery to a stream-transport layer that knows how to fetch from the reference type. Multiple transports coexist — you could have CG-native streams, WebRTC for browser interop, and HTTP for prerecorded content all in the same scene.

**Presence, meetings, and calls.** Video bodies combined with repeat bindings over presence data give real-time shared spaces for free. A "meeting room" is not a special scene concept — it's an item whose scene repeats over its presence records, rendering each as a video body with position from the presence data. When someone joins, a presence record is added, the repeat expands, a new body appears. Walking around in 3D is just updating your presence's position. Leaving the room removes the presence record, the body disappears, the stream subscription stops.

"Calling" someone is asking to become present in their item — a frame request (CALL) that, when accepted, becomes a PRESENCE frame. See [Pending Frames and the Swarm](#pending-frames-and-the-swarm) for how incoming calls appear in the UI before acceptance.

### Pending Frames and the Swarm

*(This section describes behavior that depends on the broader mount/endorsement model, which is still being designed. What follows is the intended semantics from the scene system's perspective.)*

Items accumulate incoming frames that haven't yet been endorsed. These are **pending frames** — they target the item but aren't part of its canonical content yet. Examples:

- A CALL frame requesting presence in the item
- A LIKE frame expressing a reaction
- A COMMENT frame awaiting moderation
- A FLAG frame marking something as spam

Pending frames are transient by default — if nobody interacts with them, they fade away over time. They're notifications, reactions, and transient signals rather than persistent content.

**Pending vs durable is a policy, not a frame type.** Whether a frame is "pending" or "durable" is determined by its **cleanup policy**, not by being a separate kind of frame. The scene system observes whatever frames currently exist on an item; their lifetime is governed by per-frame policies the frame model enforces. Common policies:

- **Time-based** — frame expires after a TTL (a TYPING frame disappearing after 30 seconds of no renewal)
- **Replacement-based** — a newer frame of the same kind from the same author replaces older ones (the latest TYPING from a user supersedes the previous one)
- **Side-effect-based** — a related action clears the frame (a MOVE frame in chess clears any in-progress SELECTED frames from the same author)
- **Never** — the frame is durable and persists in the object store indefinitely (a committed move, a published comment, an endorsed reaction)

The same predicate can have different policies in different contexts. The scene system doesn't care which — it renders whatever frames are currently present.

**The swarm.** Every item (even a bare handle) has an implicit "swarm" of pending frames around it. In the default rendering, pending frames appear as a cloud of notification indicators near the item — small visual tokens showing there's incoming activity. Hovering or focusing reveals the individual pending frames. Ignored ones fade away according to their cleanup policy.

**Custom handling.** An item's scene author can opt to render pending frames INSIDE the scene rather than as a swarm. A chat room might handle CALL frames as prominent "incoming call" banners with accept/decline buttons directly in the room view. A gallery might render LIKE frames as heart reactions floating above images. The default swarm is for items whose scene doesn't explicitly handle the incoming frames.

**Interaction outcomes.** Interacting with a pending frame generally has one of these outcomes:

- **Endorse** — the frame becomes part of the item's canonical content (accept the call → becomes a PRESENCE frame; approve the comment → becomes a regular comment).
- **Dismiss** — the frame is removed or marked handled.
- **Ignore** — the frame lingers until it times out and fades.

The scene system treats the swarm as another rendering surface for each item. Item authors can style it, customize its placement, suppress it for certain frame types, or route specific pending frames to prominent scene positions.

### Keyframe Animation

Autonomous timeline-driven property changes — pulsing, spinning, bouncing. Unlike transitions (which react to property changes), keyframe animations run on their own timeline.

**Keyframes** are defined inline on the node as a list of percentage stops with property values:

```
keyframes: [
  { at: 0,   opacity: "1.0" },
  { at: 50,  opacity: "0.5" },
  { at: 100, opacity: "1.0" }
]
```

**Animation control properties:**

| Property | Values | Notes |
|----------|--------|-------|
| animationDuration | Duration | "2s", "500ms" |
| animationIterationCount | Count | "infinite", "3", "1" |
| animationDirection | Keyword | "normal", "reverse", "alternate", "alternate-reverse" |
| animationEasing | Timing function | Same functions as transition easing |
| animationDelay | Duration | Delay before first iteration |
| animationFillMode | Keyword | "none", "forwards", "backwards", "both" |
| animationPlayState | Keyword | "running", "paused" |

Fill mode controls what happens outside the active animation window: "forwards" holds the final keyframe values after completion, "backwards" applies the first keyframe values during the delay period, "both" does both.

Keyframe animations and transitions can coexist on the same node. Keyframe values take priority — if a keyframe animation is driving `opacity`, the transition for `opacity` is bypassed.

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

### Anchor Positioning

Any child in any layout mode can be taken out of flow using anchor properties:

| Property | Values | Notes |
|----------|--------|-------|
| anchorTop | Offset or reference | Position top edge |
| anchorRight | Offset or reference | Position right edge |
| anchorBottom | Offset or reference | Position bottom edge |
| anchorLeft | Offset or reference | Position left edge |

**Values:**
- Dimensional offset from parent edge: `"0"`, `"10px"`, `"50%"`
- Sibling edge reference: `"#sibling-id"` (corresponding edge), `"#sibling-id.bottom"` (specific edge)

Setting opposing anchors stretches the child: `anchorLeft="0"` + `anchorRight="0"` fills the parent width. Setting only one anchor positions the child at that offset while keeping its intrinsic size.

Anchored children do not participate in flow layout — they don't affect the position of non-anchored siblings and are not affected by gap, alignment, or justification.

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

Events flow back across this boundary in the opposite direction. See [Events and Dispatch](#events-and-dispatch) for the event shape and dispatch chain. The `report` and `consumed` fields described there are part of the wire format — they affect what crosses the boundary and what stays renderer-local.

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

**Handles inline in another scene are just shape.** When a Handle appears inside another item's scene tree (a Handle for a user inside a chat message, a Handle for a sememe inside a dictionary entry), the Handle's referenced item has *not* been instantiated as a view — there is no implementation in memory for it. The Handle is a SceneNode subtree owned by the host scene; click events on it bubble through the host's dispatch chain. To act on the handle's referenced item, the renderer fires a session action (`view <iid>`) which creates a new ITEM_VIEW for the referenced item. See [Views and Implementations](#views-and-implementations) and [Events and Dispatch](#events-and-dispatch).

## Views and Implementations

A scene is data. A **view** is a live, rendered instance of an item — what the user actually sees on screen.

Every visible item is represented by an `ITEM_VIEW` frame on the current Session item. The frame records:

- Which item is being viewed (the item reference)
- The view's position, size, and other window-level configuration
- A binding to the **implementation instance** — the runtime code that handles events, computes view-model values, and projects the item's frames into useful state

### Implementations

An implementation is the executable code for an item type. Items declare implementations via `IMPLEMENTS` frames pointing to runtime-specific bindings (a Java class name, a WASM module reference, etc.). Multiple implementations can exist across runtimes; at view-create time, the librarian selects one it can execute.

When a view is created, the librarian:

1. Looks up the item's `IMPLEMENTS` frames
2. Selects an implementation suitable for an available runtime (defaults can be overridden per-user or per-view)
3. Instantiates it
4. Binds the instance to the new `ITEM_VIEW` frame

The instance lives as long as the view does. When the view is closed (or the session ends), the instance is dropped. There is no caching across views — each view gets its own instance.

### Multiple views, separate instances

Two views of the same item create two separate implementation instances. Each holds its own runtime state — typically a model object that's a *projection* of the item's frames (a chess `Game` object built from the move history, a parsed AST built from a source file, etc.).

Authoritative state always lives in the item's frame stream. View A creates a `MOVE` frame; the librarian notifies subscribers; view B's instance sees the new frame and updates its projection. Per-view runtime state (selection, scroll position, in-progress interactions) lives on the implementation instance and dies with the view.

### The chrome

Every view is wrapped by a **chrome** — a universal layer providing item-level affordances: header, swarm, frames panel, mounts panel, versions panel, mode bar, prompt, presence indicators, comment thread. The chrome is identical for every item type. Implementations don't see chrome events and don't know about likes, comments, or reactions — those are handled by the chrome layer.

The chrome is implicit: it's always the same Java class for every view, so it's not bound per-view. Only the item-content implementation varies.

The chrome's scene contains the item's content scene as a subtree. From the renderer's perspective, there's one merged scene tree per view — chrome on the outside, item content embedded inside.

## Events and Dispatch

User input — clicks, scrolls, key presses, hover — flows through a four-layer dispatch chain. Most input is handled instantly on the renderer side via locally-declared state mutations. Anything requiring application logic is dispatched up through the layers until something claims it.

### Event Shape

An event is declared on a SceneNode through its `events` list. Each entry is a tuple:

| Field | Values | Notes |
|-------|--------|-------|
| `on` | `"click"`, `"hover"`, `"scroll"`, `"keyDown:F1"`, ... | The trigger |
| `action` | `"toggle:expanded"`, `"select"`, `"view"`, `"scroll-position"`, ... | What to do |
| `target` | `"iid:abc"`, `"100"`, `"self"`, ... | Action argument |
| `report` | Boolean, default `false` | Also send a copy to the librarian when fired locally |
| `consumed` | Boolean (set by the renderer at dispatch time) | Has a local handler already acted on this? |

`report` is part of the *declared* event — set at scene-author time, in `@Scene.On` or hand-authored CBOR. It expresses dispatch policy: should this event also be observed by the implementation, even if the renderer handled it locally?

`consumed` is set by the *renderer* when forwarding an event to the librarian. It tells the receiver whether the local pipeline has already acted on the event.

### The Dispatch Chain

When the renderer detects user input on a node, it walks the dispatch chain:

**1. Scene-tree bubbling.** The renderer hit-tests to find the deepest node containing the cursor, then walks UP the SceneNode tree looking for the nearest `events` entry whose `on` matches the input type. The first node that has a matching entry claims the event. (DOM-style bubbling.)

**2. Local dispatch.** The renderer dispatches the action through its local pipeline:

- **Built-in actions** — `toggle:`, `set:`, `unset:`, `cycle:` are handled by [InteractionState](#interaction-state) directly. Mutates state, the resolver's interaction-state pass picks up the change, the display updates. No round-trip.
- **Otherwise** — the action is application-level. Continues to the next layer.

**3. Item implementation.** The view's bound implementation receives the action via its handler. It can claim the event (return true) or pass (return false).

**4. Chrome.** If the item implementation didn't claim it, the chrome's handler gets it. The chrome handles universal affordances: F-key toggles, frames-tree clicks, mode bar interactions, swarm gestures.

**5. Session.** If the chrome didn't claim it, the session's handler gets it. The session handles platform-level actions:

- `view <iid>` — open a new view of an item
- `exit` — close the current view

More session actions will be added as the platform grows. Navigation is structural — there is no back button, no history stack. Closing one view and opening another is the only navigation primitive.

### Reporting

When `report=true` is set on an event, the renderer sends a copy of the event to the librarian over the session protocol — fire and forget, in parallel with the local dispatch. The local response is instant; the report is observed asynchronously.

The reported event arrives at the implementation as a normal event (same shape, same dispatch path). The `consumed` field tells the implementation whether the local pipeline already handled the event:

- `consumed=true` — a local handler already acted (this is FYI; no action required, but the implementation may still take action if it wants — for analytics, recording, downstream effects)
- `consumed=false` — nothing was claimed locally; the implementation is expected to handle it

Use cases for `report=true`:

- **Hover analytics** — implementation tracks which elements are hovered without affecting the visual hover (which remains a local pseudo-state)
- **Scroll position recording** — implementation observes scroll position without intervening in scroll smoothness; can use the position to fetch more rows when nearing the end of a window
- **Read receipts** — when a frame becomes visible, fire a "seen" event without blocking display

### Local vs. remote implementations

The same Event message shape works for both local and remote implementations. When the librarian and renderer are co-located, dispatch is a direct method call within the JVM. When they're split (remote librarian, thin renderer), the Event crosses the wire via the session protocol. The implementation doesn't know which case it's in.

For the local case, an event with `consumed=false` and no local handler simply continues up the dispatch chain in-process — the chrome and session layers are also in-process. For the remote case, the renderer sends the Event to the librarian, which walks the dispatch chain on the librarian side and pushes any resulting scene updates back to the renderer.

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
