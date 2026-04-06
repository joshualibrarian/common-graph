# Scene System — Outstanding Work

Live TODO list for the scene model and pipeline. The definitive design reference is `docs/scene.md`.

## High Priority

### 1. SceneNode Nesting Refactor

The biggest deferred item. SceneNode currently has 100+ flat fields. Most nodes use fewer than 10. With the MAP serialization we added, null/default fields are already omitted, but the Java model is still a giant flat class.

**Goal:** Group related properties into nested objects.

**Proposed structure** (~50 flat fields collapse into 6 nested objects):

| Nested class | Fields | Current flat names |
|--------------|--------|--------------------|
| `Border` | 4 `BorderSide` objects (top/right/bottom/left) | `borderTopWidth`, `borderTopStyle`, `borderTopColor`, ... × 4 sides |
| `Transform` | rotation (3 axes), scale (3 axes), transformOrigin, elevation, position | `rotationX/Y/Z`, `scaleX/Y/Z`, `transformOrigin`, `elevation`, `posX/Y/Z` |
| `Typography` | fontFamily, fontSize, fontWeight, fontStyle, textDecoration, textAlign, lineHeight, letterSpacing, textOverflow, whiteSpace, foreground | all the font/text properties |
| `Background` | color, image, size, gradient | `backgroundColor`, `backgroundImage`, `backgroundSize`, `backgroundGradient` |
| `Transition` | property, duration, easing, delay | `transitionProperty`, `transitionDuration`, `transitionEasing`, `transitionDelay` |
| `Animation` | duration, iterationCount, direction, easing, delay, fillMode, playState, keyframes | all the `animation*` fields + `keyframes` |

**Stay flat** (~20 fields): id, classes, type, width, height, minWidth/maxWidth/minHeight/maxHeight, padding, margin, overflow, visible, corner, opacity, cursor, editable, events, state, when, children, repeat, childTemplate, text, format, tokens, shape, image, model, glyph, alt, fill, strokeColor, strokeWidth, radius, pathData, material, surfaces, anchorTop/Right/Bottom/Left, bind, capturesFocus.

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

### 4. Mounts and the Swarm (design deferred — HIGH PRIORITY before CALL and other pending-frame features)

The mount system — how endorsed frames weave into item scenes — is still being designed. Related and equally important: the **swarm** mechanism for pending (unendorsed) frames.

**What's settled** (captured in `docs/scene.md` § Pending Frames and the Swarm):
- Items accumulate pending (unendorsed) frames in addition to endorsed content
- Every item has an implicit "swarm" of pending frames — by default rendered as a cloud of transient notifications around the item
- Pending frames fade over time if not interacted with
- Item scene authors can OPT to render pending frames inside the scene rather than in the swarm (e.g., a chat room rendering CALL frames as prominent banners)
- Interaction outcomes: endorse (becomes canonical), dismiss, or ignore (times out)
- CALL, LIKE, FLAG, COMMENT are all examples of pending frames

**What's open:**
- How do mounts actually work? The current scene compiler has some mount-like concepts but the full design isn't settled.
- How does a scene bind to its item's endorsed frames? Its pending frames?
- Is the swarm part of the scene tree or a separate rendering layer?
- How does the session-level fallback work when an item's scene doesn't handle a pending frame type?
- What's the visibility/permission model for pending frames? (Owner sees all pending; contributors see their own? etc.)
- How do pending frames expire? Per-type timeouts? Manual dismissal only?
- How does endorsement transition a pending frame to endorsed content?

**This blocks real implementation of CALL and other notification-like features.** The video/audio body mechanics are independent and can ship separately, but the "incoming call" UI pattern depends on resolving mounts + swarm + pending frames.

When tackling this, check:
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

## Notes for the Next Session

The current codebase state after today's work:

- **Done:** border longhand, transitions (wired), keyframe animations, gradients, anchor positioning, border styles (dashed/dotted), SVG paths, per-axis rotation/scale, transformOrigin, background images, typography (alignment/line-height/letter-spacing/overflow/whitespace), min/max constraints, visible as Boolean, MAP serialization, all records converted to classes.

- **Documented but not implemented:** elevation+shadow (full design in scene.md), rotationX/Y behavior in 3D painters (Filament), scaleX/Y/Z behavior in 3D painters.

- **Not started:** nesting refactor, video, form inputs, Filament painters, web painter.

Recommended order:
1. Nesting refactor first (prevents churn)
2. Elevation+shadow (clean design, good smoke test for the nested model)
3. Filament painters (mechanical port of reference files)
4. Web painter (mechanical port of reference file)
5. Video/forms (design + implementation — larger architectural work)
