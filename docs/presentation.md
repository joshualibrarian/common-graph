# Presentation

The presentation system renders meaning as visual, spatial, and textual output. One unified scene model covers every dimension — from spatial 3D environments to terminal text. The same scene description renders on every platform at whatever fidelity the renderer supports.

## Philosophy

**Meaning first.** Scene elements carry sememe references, not strings. The renderer resolves them to the user's language at paint time. No human-readable text appears in code. A label references a concept. The language system produces the word.

**One system, every dimension.** A Body is a Body — it carries representations at multiple fidelities. A 3D model, a 2D image, a geometric shape, a Unicode glyph. The renderer uses what it can. Light casts shadows in 3D and produces depth cues in 2D. Elevation displaces geometry in 3D and renders as visual weight in 2D. Nothing is "3D-only" — every element expresses itself at whatever dimension the platform supports.

**Scenes are CONFIG.** A scene is not content — it is metadata about how to present content. Scenes are canonical value types (CBOR-serializable, content-addressed) stored as CONFIG bindings on predicates or frame records. Annotations in code provide seed defaults. Users override them by storing different CONFIG. A visual editor modifies CONFIG, not code.

**Expressions everywhere.** Every property value in a scene is an expression that resolves in context. Width, color, visibility, content — all evaluated by the expression pipeline against item data, state, environment, theme, and user preferences. The scene tree is a template of expressions. The renderer evaluates and paints the results.

## The Three Primitives

Everything composes from three structural primitives. No other node types exist.

### Container

The structural primitive. Has children and layout. Containers arrange other nodes — vertically, horizontally, in grids, or as overlapping stacks.

Properties: layout mode, gap, alignment, justification, wrap, overflow. Children: any nodes.

### Text

The content primitive. Two modes:

**Label mode** renders a value. The value renders itself in the user's locale and language context. A sememe resolves through the lexeme system. A quantity formats its number and resolves its unit's symbol. A date formats per locale convention. Any value type handles its own display.

**Content mode** displays literal text — documents, code, log entries, raw data. An optional format reference (a MIME type sememe) tells the renderer how to interpret the content. Markdown, Code, JSON are sememes with rendering implementations. New formats are new sememes.

### Body

The visual primitive. Carries representations at multiple fidelities:

- **Model** — 3D mesh (GLB, glTF)
- **Image** — 2D image (SVG, PNG)
- **Shape** — geometric primitive (line, circle, rectangle, sphere, box)
- **Glyph** — Unicode character
- **Alt** — text description

The renderer uses the highest fidelity it supports: model in spatial, image in graphical, glyph in text. Shape parameters (fill, stroke, radius) and material references (PBR properties) provide visual detail at each level.

### Universal Base

All three primitives share a universal base of properties:

- **Identity** — id, classes
- **Box model** — width, height, margin, padding, border, corner, background
- **Typography** — font family, font size, font weight, foreground color, opacity
- **Transform** — rotation, scale, elevation, position (x, y, z)
- **Interaction** — events, captures focus, cursor, editable
- **Data binding** — bind expression (content source), visible expression (show/hide)
- **State** — declared state keys with defaults (renderer-managed)
- **Conditionals** — `when` blocks: property overrides keyed by condition

All nodes are CBOR-serializable. A scene tree is a canonical value type — deterministic serialization, content-addressed, storable as a frame binding.

## The Fidelity Spectrum

Every scene element exists across the dimensionality spectrum. The renderer expresses what it can.

| Element | Spatial | Graphical | Text |
|---------|---------|-----------|------|
| **Body** | 3D mesh with materials | 2D image or shape | Unicode glyph or alt text |
| **Light** | Positioned light source | Drop shadow, depth cue | (metadata only) |
| **Elevation** | Perpendicular displacement | Shadow weight, z-ordering | Indentation depth |
| **Camera** | Viewpoint, projection, FOV | Viewport bounds, zoom | (implicit) |
| **Environment** | Skybox, IBL, fog | Background color/gradient | Terminal background |
| **Audio** | Positional 3D audio | Stereo playback | (silent, metadata) |
| **Transform** | Full 3D transform | Canvas rotation, scale | Indentation |
| **Surfaces** | Containers mapped to named faces on geometry | (ignored — containers render inline) | (ignored) |

A scene authored with all fidelity levels renders meaningfully on every platform. A spatial renderer uses meshes and lighting. A graphical renderer uses images and shadows. A text renderer uses glyphs and indentation. The same CBOR.

## Scene Elements

### Container

Layout modes:

- **Vertical** — children stack top to bottom. Default.
- **Horizontal** — children flow left to right.
- **Grid** — children placed in columns and rows. `columns` and `rows` specify the grid. `repeat` iterates data into cells.
- **Stack** — children overlap at the same position. Z-order follows child order.
- **Constraint** — children anchored to named edges of siblings or parent. Each child declares which edges it attaches to.

Properties: `gap` (spacing between children), `align` (cross-axis alignment), `justify` (main-axis distribution), `wrap` (flow to next line), `overflow` (visible, hidden, scroll, auto).

### Text

Two modes determined by the annotation:

**`@Scene.Label`** — the method returns a value. The value renders itself:

- `Sememe` or `ItemID` — resolved to a word via the lexeme system in the user's active language, inflected per the specified grammatical features.
- `Quantity` — the number formats per locale, the unit resolves its symbol as a sememe.
- `Decimal`, `Rational` — locale-formatted number.
- Temporal values — locale-formatted date, time, duration.
- Any value type — calls the value's own display method with language context.

Default grammatical features: noun, lemma (the base dictionary form). Override with a list of feature ItemIDs from the seed vocabulary (PartOfSpeech, GrammaticalFeature).

**`@Scene.Text`** — the method returns a string. Displayed verbatim. An optional format reference (a MIME type sememe) tells the renderer how to interpret the content. The format sememe's implementation handles parsing and rendering. Markdown, Code, JSON, HTML are all MIME type sememes seeded from the IANA registry.

### Body

Content sources form a fallback chain. The renderer uses the best available:

- `model` — path or CID to a 3D mesh
- `image` — path or CID to a 2D image
- `shape` — geometric primitive name (line, circle, rectangle, sphere, box, cylinder, cone)
- `glyph` — Unicode character
- `alt` — text description

Shape parameters: `fill`, `stroke`, `strokeWidth`, `radius`. Material: PBR properties referenced by ItemID.

#### Surfaces

A Body's geometry has named surfaces. A cube has six (front, back, left, right, top, bottom). A cylinder has three (side, top, bottom). Custom meshes define their own surface names.

Containers can be bound to named surfaces on a Body. The spatial renderer rasterizes each bound Container to a texture and maps it onto that surface of the geometry. The content exists as normal Containers with normal children — they are completely unaware that they might end up on a 3D surface.

In graphical and text rendering, surface bindings are ignored. The bound Containers render inline as part of the normal layout flow. No duplication — the same Containers serve both purposes.

Example: a chess clock is a box Body with two meaningful surfaces. The "front" surface binds to a Container showing two clock faces. The "top" surface binds to a Container showing two buttons. In 2D, both panels render side by side in normal layout. In 3D, each panel rasterizes to a texture on the corresponding face of the box. Same content. Same declarations. Zero duplication.

### Light

Type (point, directional, spot), color, intensity, position, direction, falloff. In graphical rendering, lights affect shadow rendering — elements with elevation cast shadows based on light position. In text rendering, lights are metadata only.

### Transform

Position (x, y, z), rotation (Euler angles or axis-angle), scale. In graphical rendering, position maps to canvas coordinates, rotation to canvas rotation, z to z-order. In text rendering, position maps to indentation.

### Environment

Background (skybox, color, gradient), ambient light, fog. In graphical rendering, background renders as the canvas background. Ambient affects overall brightness. In text rendering, background maps to terminal background color.

### Camera

Projection (perspective, orthographic), field of view, near/far clipping planes, position, target. In graphical rendering, camera maps to viewport bounds and zoom level. In text rendering, camera is implicit — the viewport is the terminal window.

### Audio

Source reference, volume, loop, spatial parameters (position, attenuation, cone). In graphical rendering, spatial parameters map to stereo panning. In text rendering, audio is silent but the metadata is preserved for other consumers.

## Text and Language Resolution

### The Resolution Chain

A label's value resolves to display text through the language system:

1. The value provides the concept (sememe), quantity, or structured data
2. Grammatical features specify the desired word form (noun, verb, past participle, plural)
3. The active language (from the render environment) selects the lexicon
4. The lexeme system finds the matching NAME binding: sememe + language + features → word
5. The morphology engine inflects if the exact form is not stored
6. The result is a display string in the user's language

A label returning the Checkmate sememe with features [NOUN, LEMMA] in a Japanese context produces "チェックメイト". The same label in English produces "Checkmate". Same CBOR, same hash, different display.

### Composed Expressions

A label can carry an expression that evaluates to a semantic frame — a structured assertion with a predicate and filled roles. The language system generates a sentence from the frame:

Given: `CHECKMATE { AGENT → Fischer, THEME → player }`

- English: "Fischer checkmated you"
- Spanish: "Fischer te dio jaque mate"

The expression language is the interlingua. You write meaning. The renderer produces language. The predicate's verb lexemes, the thematic roles, and the target language's grammar rules determine word order, morphology, and agreement.

### Quantities and Values

Quantities render themselves. A quantity of 5 millimeters in Arabic renders as "٥ مم" — Arabic-Eastern numerals, Arabic unit abbreviation, right-to-left. In English: "5 mm". In Chinese: "5毫米". The number formats per locale. The unit resolves its symbol as a sememe.

All value types follow this pattern. The renderer asks the value to display itself in context. No formatting logic in the renderer.

### Format as Sememe

For literal text content, the format is a sememe reference — typically a MIME type. `text/markdown`, `application/json`, `text/x-code` are seeded from the IANA MIME registry as sememes. Each carries an implementation that knows how to parse and render its format. New formats are new sememes with new implementations.

## Root and Handle

Every scene has two modes.

### Handle

The compact identity. An item's handle shows in trees, chips, breadcrumbs, header bars, search results, HUD labels, nameplates — anywhere the item is referenced but not focused. A frame's handle shows in the frame tree. A version's handle shows in the version list.

The handle is always available. The default handle comes from the graph: the item's SYMBOL binding provides the icon, the primary NAME lexeme provides the label, the type sememe provides the subtitle. This works for everything without any scene declaration.

Custom handles override the default. A chess game's handle might show the players and move count. A document's handle might show word count and last modified. The handle is a Container like any other — it just serves the compact-identity role.

### Root

The full scene. In 2D it renders as a panel. In 3D it renders as a space you can enter. In VR you are inside it. The renderer decides the modality — the declaration is the same.

`@Scene.Root` marks the scene root and references the handle:

- `@Scene.Root(handle = ChessHandle.class)` — explicit handle class
- `@Scene.Root` with a `@Scene.Handle` annotated method — inline handle
- `@Scene.Root` with no handle — system generates from SYMBOL + NAME

The root and handle are the two artifacts the compiler extracts. Both are stored together in CONFIG:

```
Scene CONFIG: { root: Node, handle: Node }
```

Both are independently overridable. A user can customize their chess handle without touching the board layout.

### Combinations

A scene can have both root and handle, just a handle, or just a root:

- **Root + Handle** — the common case for items with custom views. Chess declares a board (root) and a compact "Fischer vs Spassky" representation (handle).
- **Handle only** — for simple frames and items where the default detail rendering (showing bindings, metadata) works fine. The custom handle provides identity; the system provides the panel.
- **Root only** — the system generates a handle from SYMBOL + NAME. Works when the full scene is custom but the compact form needs nothing special.
- **Neither** — the system generates both from the graph. Works for any item or frame with no scene declaration at all.

## The Annotation System

Scene annotations declare how an item's data renders. Annotations on methods bind implicitly — the method name IS the binding. The method provides the data. The annotation declares the presentation.

### Annotations on Methods

The annotation on a method means: render this method's return value using these properties. The method name is the implicit binding. No separate declaration needed.

Available annotations:

- **`@Scene.Root`** — marks the scene root. Optional `handle` attribute references a handle class. On the class declaration.
- **`@Scene.Handle`** — marks an inline handle method. Alternative to `@Scene.Root(handle = ...)`.
- **`@Scene.Container`** — structural wrapper, children are nested annotated methods
- **`@Scene.Label`** — rendered value (Sememe, Quantity, any displayable value)
- **`@Scene.Text`** — literal content (String, with optional format sememe)
- **`@Scene.Image`** — visual content (resolves to Body with fidelity chain)
- **`@Scene.Repeat`** — iteration (method returns Iterable, children templated)
- **`@Scene.If`** — conditional visibility (references a boolean method)
- **`@Scene.Embed`** — embed a Node subtree returned by the method
- **`@Scene.On`** — event binding (unified: click, keypress, gesture)

The return type determines rendering:

| Return Type | Rendering |
|-------------|-----------|
| `Sememe` / `ItemID` | Language resolution (word/phrase in active language) |
| `Quantity` | Locale-formatted number + unit symbol |
| `Decimal` / `Rational` | Locale-formatted number |
| Temporal types | Locale-formatted date/time |
| `String` | Literal content (verbatim) |
| `Node` | Embedded subtree |
| `boolean` | Condition for `@Scene.If` |
| `Iterable` / array | Data for `@Scene.Repeat` |

### Ordering

Methods carry an `order` attribute for deterministic child ordering. Since the scene tree is CBOR-serialized and content-addressed, child order must be deterministic. Default: declaration order. Explicit `order` recommended for anything stored as CONFIG.

### The Developer Pattern

One class declares identity, behavior, and presentation. `@Scene.Root` marks the scene root and optionally references a handle class. The compiler extracts both the root scene and the handle as CONFIG.

- `@Implements` → identity frames (FrameBody, hashed)
- `@Verb` → verb dispatch table (behavior)
- `@Scene.Root` + `@Scene.*` → CONFIG binding: `{ root: Node, handle: Node }` (non-identity, overridable)

The annotation is the seed default. The stored CONFIG takes precedence.

### Compile-Time Validation

An annotation processor validates scene declarations at compile time:

- `@Scene.Label` method returns a displayable value type, not String
- `@Scene.Text` method returns String, not Sememe
- `@Scene.If` references a method returning boolean
- `@Scene.On` event string parses as a valid event identifier
- `@Scene.Repeat` method returns Iterable or array
- `order` values are unique within a container
- Warnings on hardcoded user-facing text in literal strings

## Events

All interactions are events. A click, a keypress, a gesture, a scroll — declared the same way.

`@Scene.On` binds an event to an action:

- `event` — what triggers it: "click", "F1", "Alt+Up", "swipe-left", "scroll"
- `action` — what happens: a state mutation or a verb dispatch
- `when` — condition: platform, state, viewport (same condition language as `@Scene.If`)

Multiple events per method. The renderer collects all declarations, builds a dispatch table, and routes platform events.

### State Mutation Actions

A closed set of built-in actions that modify the renderer's state store:

| Action | Effect |
|--------|--------|
| `toggle:key` | Flip boolean value |
| `set:key=value` | Set to literal |
| `unset:key` | Reset to default |
| `cycle:key=a,b,c` | Advance to next value in list |

### Application Actions

Anything not a state mutation bubbles to the application layer — verb dispatch, navigation, evaluation. The renderer does not interpret these.

### Scoped Events

Events are conditional. The renderer only activates a binding when the `when` condition matches, evaluated against the state store and render environment. A tree navigation key only works when the tree is visible. A spatial control only works on spatial renderers.

### Key Hints

The renderer knows which events are bound to which actions. It can show key hints on buttons when platform convention warrants it. This is a renderer decision — not declared in the scene tree.

## State

### Tier 1: Renderer-Local

Instant, in-memory. The renderer owns this state. It persists across re-renders but not across sessions.

- Declared on nodes with a key and default value
- Mutated by event actions
- Read by bindings (visibility, conditional properties)
- Scoped to nearest ancestor node with an ID — children read parent state

Pseudo-states (hover, focus, active) are renderer-tracked automatically — no declaration needed.

### Tier 2: Frame-Backed

Persistent, debounced. State that matters beyond the current session writes back to frame CONFIG bindings. The renderer debounces writes to avoid thrashing storage.

Examples: scroll position, panel sizes, user preferences for a specific item's presentation.

### No Model Objects

The scene tree IS the declaration. The renderer IS the state machine. Events update state. State drives rendering. Bindings close the loop.

## Conditions and Environment

### Render Environment

The rendering context carries everything conditions might query:

- **Platform** — renderer type (skia, filament, tui, cli, web)
- **Viewport** — width, height, orientation
- **Display** — DPI, device pixel ratio
- **Input** — modality (mouse, touch, keyboard)
- **Capabilities** — color, images, spatial, audio
- **Language** — active language for text resolution
- **Units** — contextual unit measurements (em, ch, ln in renderer-native units)

### Condition Language

Conditions evaluate against the environment and state:

| Form | Examples |
|------|----------|
| Platform tags | `:skia`, `:tui`, `:web`, `:3d`, `:2d` |
| Breakpoints | `@xs`, `@sm`, `@md`, `@lg`, `@xl` |
| Orientation | `landscape`, `portrait` |
| Capabilities | `mouse`, `touch`, `color`, `spatial` |
| Dimensional | `viewport.width >= 768px`, `viewport.width < 40ch` |
| Scalar | `dpi > 192`, `devicePixelRatio >= 2` |
| State | `$state.expanded`, `treeVisible` |

Dimensional queries support any unit. Breakpoint thresholds are configurable — the named breakpoints are convenience aliases for viewport width ranges.

### Units

- **Logical pixel** — `px` or `lpx`. 1/96 inch. DPI-independent. The standard layout unit.
- **Device pixel** — `dpx`. One hardware pixel. `1px = devicePixelRatio × dpx`.
- **Contextual** — `em` (font size), `ch` (character width), `rem` (root font size), `ln` (line height). Resolved from the renderer's measured font metrics.
- **Physical** — `in`, `cm`, `mm`, `m`, `km`, `ft`, `pt`. Fixed conversion ratios. The Unit items (sememes) know their factors.
- **Viewport** — `vw`, `vh`. Percentage of viewport dimensions.
- **Percentage** — `%`. Relative to parent dimension.
- **Fraction** — `fr`. Share of remaining space after fixed children are measured.

Unit resolution flows through the librarian — no static conversion tables. Contextual units resolve via the renderer's measured font metrics provided in the render environment.

## Scene as CONFIG

A scene tree is a canonical value type. It is not a frame — it does not assert anything. It is metadata about how to present the data in frames.

### Storage

A scene CONFIG contains two Node trees: the root (full scene) and the handle (compact identity). Both are canonical, both are independently overridable.

```
Scene CONFIG: { root: Node, handle: Node }
```

Scenes are stored as CONFIG binding targets at different levels:

- **Type-level** — CONFIG on a type sememe (Chess, Person, Document). Provides the item-level scene: what you see when focused on an instance of this type.
- **Predicate-level** — CONFIG on a predicate sememe (AUTHORED, MOVE, TITLE). Provides the frame-level scene: how individual frames with this predicate render.
- **Instance-level** — CONFIG on a specific FrameRecord. This particular item or frame renders differently. Non-identity — does not affect the body hash.
- **User-level** — CONFIG on the user's own FrameRecord. Personal preference. Only affects this user's view.

### Resolution Cascade

When rendering, the system resolves the scene through the cascade:

```
User override → Instance CONFIG → Type/Predicate CONFIG → Seed default (from annotations)
```

Most specific wins. Root and handle resolve independently — a user can customize their chess handle without affecting the board layout.

The handle has an additional fallback: if no handle is declared at any level, the system generates one from the item's SYMBOL and NAME bindings. Everything in the graph has a handle, even with zero scene declarations.

### Authoring

Java annotations are one authoring path. The compiled artifact — a CBOR-serialized Node tree — is the same regardless of authoring tool:

- Java `@Scene` annotations → compiler → Node tree → CBOR
- Visual editor → direct Node tree manipulation → CBOR
- JSON/YAML import → parse → Node tree → CBOR
- Remote protocol → receive CBOR → deserialize → render

The annotation is the seed. The stored CONFIG is the truth.

## Expression Evaluation

All property values in a scene are expressions. The expression pipeline — the same one that evaluates user commands — resolves them in context.

### Binding Expressions

Property paths reference item data, state, and environment:

- `item.displayToken` — property of the viewed item
- `$state.expanded` — value from the renderer's state store
- `viewport.width` — value from the render environment

### When-Conditions

Property overrides keyed by condition expressions. Multiple conditions can match — overrides merge in declaration order.

### Label Expressions

A label carries an expression that evaluates to a value or a semantic frame. Simple cases produce a single sememe or quantity. Complex cases produce frames that the language system renders as sentences.

### Unit Resolution

Dimensional values resolve through the unit system. Physical units convert via fixed ratios on the Unit items. Contextual units resolve via the renderer's font metrics. The librarian resolves unit symbols to Unit items. The Unit items — which are sememes — handle conversion.

## Layout

### Vertical and Horizontal

Children arranged along the main axis. `gap` controls spacing. `1fr` on a child means "take the remaining space after fixed children are measured." Multiple `1fr` children split remaining space equally.

### Grid

Children placed in a grid of `columns` × `rows`. `repeat` iterates a data collection into templated cells. Gap applies between cells in both directions.

### Stack

Children overlap at the same position. Later children render on top. Useful for overlays, badges, background layers.

### Constraint

Children anchored to edges — top, bottom, left, right — of the parent or named siblings. Each child declares which edges it attaches to and at what offset.

### Sizing

Values are expressions that resolve to dimensions:

- **Explicit** — `200px`, `40ch`, `10em`, `2in`, `5cm`
- **Percentage** — `50%` (of parent dimension)
- **Viewport** — `80vw`, `50vh`
- **Content** — `auto` (shrink to fit)
- **Fill** — `1fr` (fraction of remaining space)
- **Expression** — any expression resolving to a dimensional value

### Overflow

- `visible` — content extends beyond bounds (default)
- `hidden` — content clipped at bounds
- `scroll` — scrollable, renderer manages scroll state
- `auto` — scroll if content exceeds bounds

## Rendering

### The SceneRenderer

Each platform implements a SceneRenderer. The interface provides:

- **Abstract paint methods** — platform-specific painting of each primitive
- **State store** — per-node state, persists across re-renders
- **Default state runtime** — tree walking, state initialization, binding evaluation, action dispatch, visibility resolution, property resolution, event routing

The default methods implement the state runtime. Platforms implement only painting.

### Property Resolution

Before painting, properties are fully resolved. Base node properties merge with matching `when` block overrides. The renderer receives concrete values — no style classes to interpret, no conditions to evaluate.

### Platforms

Each platform is a complete rendering path:

- **Filament** — spatial scene graph with meshes, PBR materials, lights, cameras, positional audio OR filament-rendered 2D layout, pixel-identical with Skia
- **Skia** — layout tree with text measurement, positioned elements, GPU-accelerated painting
- **TUI** — ANSI-styled text with Unicode box-drawing, mouse hit regions
- **CLI** — plain text with indentation, no styling
- **Web** — DOM elements with CSS

Each tier renders the same scene tree. The fidelity spectrum determines what each element looks like on each platform.

## Widget Patterns

There are no widget types. Every interactive pattern composes from three primitives plus state declarations.

### Tree

A tree is nested containers with expand/collapse state. Each expandable node declares state. An event toggles it. Children are visible when expanded. Indentation, connecting lines, and expand indicators are renderer decisions based on platform and style.

### Input

A text input is a container with token chips, pending text, and a completions dropdown. Token chips are containers with styled text. Pending text is editable — the renderer manages cursor and selection as primitive capabilities. Completions are a container visible when state says so.

### Custom Patterns

Any interactive pattern follows the same approach: declare state, bind events to actions, bind visibility and properties to state. The renderer handles the interaction loop.

## Examples

### Item Summary

The default scene for any item. No scene declaration needed — the system generates it from graph data. The handle shows SYMBOL + NAME. The root shows a summary: icon, name, type label, frame count as a Quantity, verb count. All text is Labels resolving sememes.

### Chess Game

A chess game declares a custom root and handle.

The handle shows: chess piece glyph, player names, move count. Compact enough for a tree node. The root scene contains an 8×8 grid container for the board, a clock component with two surfaces bound to its Body, a move list, and a status Label. Piece Bodies carry glyph, image, and model representations — the same board renders on every platform.

The chess clock Body is a box with two named surfaces. The "front" surface binds to the clock faces Container. The "top" surface binds to the buttons Container. In 2D, both render inline as panels. In 3D, each rasterizes to a texture on the corresponding face of the box geometry.

### Document

A document's root scene is a Text node in content mode with format set to the Markdown MIME type sememe. The handle shows document icon + title. The root delegates rendering to the Markdown sememe's implementation. In text mode, the content displays with minimal formatting.

### Multilingual Help

A help overlay showing the vocabulary scope stack. Verb names, parameter names, section headers — all Labels resolving sememes. The same scene tree renders in any language. Switch languages, re-render, every label updates. No scene change needed.

### Handle Only

A simple predicate like TITLE declares only a handle: the predicate's glyph + the NAME binding's text. The detail panel uses the system's default frame rendering — showing all bindings with their roles and values. No root scene needed.
