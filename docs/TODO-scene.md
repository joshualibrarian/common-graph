# Scene System — Outstanding Work

Live TODO list for the scene model and pipeline. The definitive design reference is `docs/scene.md`.

## High Priority

### 1. SceneNode Nesting Refactor — **DONE**

**Status: Complete.** SceneNode now has 7 nested static inner classes (`Border`, `Transform`, `Typography`, `Background`, `Transition`, `Animation`, `Layout`) with the lazy-init pattern: getters return null when unset; setters create the nested object inline. Cascade keys are dotted (`border.topWidth`, `transform.rotationZ`, `typography.fontSize`, `layout.mode`, etc.) — translated from camelCase annotation parameter names by `SceneCompiler.applyStyles`. The full Java fluent API was preserved as delegators on SceneNode, so all existing readers in `ScenePresenter`, `SkiaSurfacePainter`, `AnsiSurfacePainter`, `SkiaFontManager` continue to work without modification. Build is clean, all tests pass.

**Original goal (kept for historical record):** Group related properties into nested objects.

**Proposed structure** (~50 flat fields collapse into 6 nested objects):

| Nested class | Fields | Current flat names |
|--------------|--------|--------------------|
| `Border` | 4 `BorderSide` objects (top/right/bottom/left) | `borderTopWidth`, `borderTopStyle`, `borderTopColor`, ... × 4 sides |
| `Transform` | rotation (3 axes), scale (3 axes), transformOrigin, elevation, position | `rotationX/Y/Z`, `scaleX/Y/Z`, `transformOrigin`, `elevation`, `posX/Y/Z` |
| `Typography` | fontFamily, fontSize, fontWeight, fontStyle, textDecoration, textAlign, lineHeight, letterSpacing, textOverflow, whiteSpace, foreground | all the font/text properties |
| `Background` | color, image, size, gradient | `backgroundColor`, `backgroundImage`, `backgroundSize`, `backgroundGradient` |
| `Transition` | property, duration, easing, delay | `transitionProperty`, `transitionDuration`, `transitionEasing`, `transitionDelay` |
| `Animation` | duration, iterationCount, direction, easing, delay, fillMode, playState, keyframes | all the `animation*` fields + `keyframes` |

**Stay flat** (~20 fields): id, classes, type, width, height, minWidth/maxWidth/minHeight/maxHeight, padding, margin, overflow, visible, corner, opacity, cursor, editable, events, state, when, children, repeat, childTemplate, text, format, tokens, shape, image, model, glyph, alt, fill, strokeColor, strokeWidth, radius, pathData, material, surfaces, anchorTop/Right/Bottom/Left, frame, capturesFocus.

**Rules for the nested classes:**
- Regular classes, NOT records (project policy)
- `@Getter @Accessors(fluent = true)` from Lombok
- `@Canonical.Canonization(classType = MAP)` — same as SceneNode
- Fluent setters returning `this`
- No-arg constructor

**Cascade addressing:** When-block keys use dotted notation:
- `"border.top.width"` instead of `"borderTopWidth"`
- `"transform.rotationZ"` instead of `"rotationZ"`
- `"background.color"` instead of `"backgroundColor"`

The `SceneResolver` and `ScenePresenter` when-block handlers need to parse these dotted paths and dispatch to the nested objects.

**Files touched** (expect significant changes):
- `core/.../scene/SceneNode.java` — replace flat fields with nested object references
- `core/.../scene/Border.java`, `Transform.java`, `Typography.java`, `Background.java`, `Transition.java`, `Animation.java` — new files
- `core/.../scene/Scene.java` — `@Scene.Style` annotation attributes
- `core/.../scene/SceneCompiler.java` — reads annotation attributes, writes to nested objects; supports dotted keys in when-blocks
- `core/.../scene/SceneResolver.java` — dotted key dispatch in when-block handler
- `ui/.../scene/ScenePresenter.java` — reads nested properties during resolution and layout
- `ui/.../skia/SkiaSurfacePainter.java` — reads nested properties during painting
- Tests — update property access patterns

**Do this BEFORE** implementing new visual properties (elevation+shadow, video, form inputs) to avoid churning them through the refactor.

## Medium Priority

### 2. Elevation and Shadow (full implementation)

Design complete in `docs/scene.md` § Elevation and Lighting. Implementation deferred until after nesting refactor.

**What needs to happen:**

1. **Change `elevation` from `double` to `Object`** on SceneNode (or Transform if nested). Presenter resolves `"4px"`, `"1cm"`, `"-2px"` to Float pixels. Same pattern as width/height.

2. **Add a Light model** — the scene needs to carry lighting info accessible to all painters. A `Light` class with:
   - `type`: "directional", "point", "spot"
   - `position`: (x, y, z)
   - `color`: Color (key light)
   - `ambient`: Color (shadow color — what's lighting shadowed areas)
   - `intensity`: Number

3. **Default light** — if no light is declared, provide an implicit default: directional, upper-left position, neutral white, dark neutral ambient.

4. **Shadow derivation in the painter:**
   - Positive elevation → drop shadow via `ImageFilter.makeDropShadow`
     - Direction: opposite the light's position
     - Distance: proportional to elevation
     - Blur sigma: proportional to elevation
     - Color: light's ambient color
     - Alpha: proportional to elevation
   - Negative elevation → inner shadow
     - Same formula, but rendered INSIDE the bounds (inner shadow filter or manual clip+blur)
     - Direction reversed

5. **Stack z-ordering** — within a stack container, sort children by elevation before painting so higher elevation draws on top.

6. **Pressed button pattern** — no code needed, but verify that `@Scene.Style(elevation = "2px")` + `@Scene.Style(when = "$active", elevation = "-1px")` + `transition: "elevation 0.1s ease-out"` produces a working pressed-button animation. This is the smoke test for the whole feature.

### 3. Video and Audio (design complete, implementation deferred)

Design complete in `docs/scene.md` § Video and Audio. Implementation requires:

**Scene fields to add to SceneNode body properties:**
- `video` (String, resource ref or binding) — stream reference
- `audio` (String, resource ref or binding) — independent of video
- `loop` (boolean) — prerecorded restart
- `muted` (boolean) — state-driven audio silence
- `volume` (Object, String → Float after presentation) — state-driven, animatable
- `paused` (boolean) — state-driven

**Runtime additions:**
- `StreamTransport` abstraction with plugin points for CG-native, WebRTC, HTTP, local capture
- Frame delivery subscription lifecycle tied to scene tree membership (subscribe when body enters tree, unsubscribe when it leaves)
- Audio source positional in 3D (Filament spatial painter), stereo in 2D

**Transport protocols to implement:**
- CG-native streaming (piggybacks on existing peer connections; default for CG-to-CG)
- WebRTC (for browser interop, NAT traversal, codec negotiation — could ride CG peer connections for signaling)
- HTTP streaming (for prerecorded content from web servers — MP4, HLS)
- Local capture (`camera:default`, `mic:default`, `display:main` schemes)

**Capture device scheme (host-local):** Cameras, microphones, and displays are physically attached to specific hosts. They're accessed through their host, never directly from a remote host. The `camera:default`-style scheme maps to local hardware via the host's OS APIs. To receive another participant's camera, you subscribe to a stream THEY publish (via a presence record they created), not their hardware directly.

**Painter integration:**
- Skia: texture cache updated from stream subscriptions, drawn in body bounds. Uses Skia `Image` or `Surface` for video frames.
- Filament spatial: video on plane mesh with positional audio source (reuse pattern from reference `FilamentSpatialRenderer.audio()`).
- Filament surface: same as Skia for video, non-positional audio.
- Web: `<video>` and `<audio>` DOM elements bound to stream URLs.
- Text: shows body's `alt` text. Future: investigate terminal image protocols (iTerm2, Kitty, Sixel) for thumbnail display — note that most don't support video.

### 4. Mounts and the Swarm (HIGH PRIORITY before CALL and other pending-frame features)

The mount system — how endorsed frames weave into item scenes — is still being designed. The **swarm** mechanism for pending (unendorsed) frames is now mostly settled. The two are related but separable: swarm semantics can land independently of mounts.

**What's settled** (captured in `docs/scene.md`):

- *Pending Frames and the Swarm:*
  - Items accumulate pending (unendorsed) frames in addition to endorsed content
  - Every item has an implicit "swarm" of pending frames — by default rendered as a cloud of transient notifications around the item
  - Item scene authors can OPT to render pending frames inside the scene rather than in the swarm (e.g., a chat room rendering CALL frames as prominent banners)
  - Interaction outcomes: endorse (becomes canonical), dismiss, or ignore (times out)
  - CALL, LIKE, FLAG, CRITIQUE, SUGGESTION are all examples of pending frames
- *Pending vs durable is a cleanup-policy property of the frame*, not a separate frame type. Policies: time-based (TTL), replacement-based (newer-of-same-kind supersedes), side-effect-based (a related action clears it), never (durable). The scene system observes whatever frames currently exist.
- *Reactions are universal across all items*, handled by the chrome layer. Item implementations don't see them and don't need to know about them.
- *Views and Implementations*: every visible item is an `ITEM_VIEW` frame. The frame frames to a runtime implementation instance, selected at view-create time, lifetime tied to the view. Multiple views of the same item have separate instances. Frames are the shared source of truth; runtime model state is a per-instance projection.
- *Events and Dispatch*: four-layer bubbling chain (scene tree → item implementation → chrome → session). Built-in actions (`toggle:`, `set:`, etc.) handled locally by InteractionState. Application actions dispatched up the chain. The Event shape carries `report` (declared at scene-author time) and `consumed` (set by the renderer at dispatch time).
- *Session actions are the top of the chain*: currently `view <iid>` and `exit`. No back button, no history stack — navigation is structural via creating/closing views.

**What's still open:**

- **Mount mechanics.** Three kinds of mounts are conceptually distinct:
  - **Path mounts** (simplest) — hierarchical/tree-shaped mounting of frames at named paths within an item's scene
  - **Surface mounts** — mounting frames onto named 2D regions of an item's scene
  - **Spatial mounts** — mounting frames into 3D positions within a spatial scene
  All three need design.
- **How a scene frames to its item's endorsed frames.** The frame expression syntax is partly there, but the formal model for "render this set of frames inside this region" needs detail.
- **Visibility/permission model for pending frames.** Owner sees all? Contributors see their own? Public reactions visible to all? This is partly a frame-model question and partly a presentation question.
- **Endorsement transitions.** The exact mechanism for transitioning a pending frame to durable (the frame's policy changes? a new frame replaces it? both?) needs design.

When tackling mounts, check:
- Existing mount concepts in `core/.../scene/SceneCompiler.java` (search for "mount")
- Frame endorsement flow in `core/.../item/` and `core/.../frame/`
- Session notification concepts in `ui/.../Session.java`

### 5. Form Inputs (design + implementation)

Not yet designed. Current state: `editable` boolean on SceneNode. Need a proper form system:
- Text input (single-line, multi-line, password, number)
- Checkbox / toggle
- Select / dropdown
- Radio group
- Slider
- Focus management across multiple inputs
- Form submission semantics

This is a significant architectural piece. Consider whether inputs are:
- A new node type (IEUT alongside CONTAINER/TEXT/BODY)
- A kind of body with `inputType` property
- Something else entirely

### 6. Filament Painters

Stubs in `ui/src/main/java/.../filament/`:
- `FilamentSurfacePainter.java` — GPU 2D orthographic
- `FilamentSpatialPainter.java` — GPU 3D perspective

Reference files preserved:
- `docs/reference-LegacyFilamentSurfacePainter.java.txt`
- `docs/reference-FilamentSpatialRenderer.java.txt`

The Surface painter should produce pixel-identical output to SkiaSurfacePainter. The Spatial painter uses the 3D representations and actual lighting.

### 7. Web Painter

Not yet ported to the new pipeline. Reference file:
- `docs/reference-renderer.js.txt`

This runs in the browser (JavaScript). Receives the resolved SceneNode tree over WebSocket (same session protocol as native). Produces DOM elements with CSS. Should produce pixel-identical output to Skia.

## Small/Minor

### 8. Border Styles Beyond Solid

Dashed and dotted borders work. Fields exist for `style` per side. Any additional styles (double, groove, ridge, inset, outset) could be added later if needed.

### 9. Constraint Layout vs Anchor Positioning

Anchor positioning (anchorTop/Right/Bottom/Left with sibling refs) is implemented and handles most constraint-layout use cases. If more advanced constraint solving is ever needed (chains, guidelines, barriers), it would be an additive feature.

### 10. Shape: Path Expansion

`shape="path"` with `pathData` works for static paths. Future: animated path morphing (would fit naturally into the keyframe system with property-specific interpolation for path data).

## Session Protocol Review (follow-up)

The dispatch model now described in `scene.md` § Events and Dispatch needs to land in the session protocol. Before implementation, audit `docs/protocol.md` to see how it fits the current message types:

- **Event delivery (renderer → librarian).** A scene event with `report=true` (or with no local handler) needs to cross the wire. The current `DISPATCH` message is verb-shaped — does it extend cleanly to scene events, or does this want a new message type? The scene event carries `(itemViewId, nodeId, on, action, target, consumed)`.
- **Scene push (librarian → renderer).** Currently `EVENT` (Tag 18) handles "item changed, here's the update". The dispatch model assumes a view is subscribed to its underlying item; when the item's frame stream changes, the librarian re-resolves and pushes the new resolved scene to the subscribed view. Verify this is what `EVENT` does.
- **ITEM_VIEW lifecycle.** The session protocol probably needs explicit messages for view-create and view-close (or these may already exist via `CONTEXT`). View-create is where the implementation gets instantiated and bound to the new ITEM_VIEW frame.
- **Scoped scene updates** (future, additive). When implemented, the librarian should be able to push "replace region X with this subtree" rather than a full scene replacement. This is purely additive — design the message type now if convenient, defer implementation.

This is a separable audit pass — not a blocker for the items above, but worth doing before any of the dispatch model gets implemented in code.

## Notes for the Next Session

Codebase state as of 2026-04-06:

- **Done:** border longhand, transitions (wired), keyframe animations, gradients, anchor positioning, border styles (dashed/dotted), SVG paths, per-axis rotation/scale, transformOrigin, background images, typography (alignment/line-height/letter-spacing/overflow/whitespace), min/max constraints, visible as Boolean, MAP serialization, all records converted to classes, **SceneNode nesting refactor (item #1)**.

- **Documented but not implemented:** elevation+shadow (full design in scene.md), rotationX/Y behavior in 3D painters (Filament), scaleX/Y/Z behavior in 3D painters, **Events and Dispatch model (scene.md § Events and Dispatch)**, **Views and Implementations (scene.md § Views and Implementations)**.

- **Not started:** elevation+shadow implementation, video, form inputs, Filament painters, web painter, mounts (any of the three kinds), session protocol updates for scene events.

Recommended order:
1. ~~Nesting refactor~~ (DONE)
2. Session protocol audit/review against the new dispatch model (small, unblocks everything else)
3. Elevation+shadow (clean design, good smoke test for the nested model)
4. Mounts (path mounts first — simplest)
5. Filament painters (mechanical port of reference files)
6. Web painter (mechanical port of reference file)
7. Video/forms (design + implementation — larger architectural work)
