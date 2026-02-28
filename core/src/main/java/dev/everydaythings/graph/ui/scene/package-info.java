/**
 * Unified Scene presentation system.
 *
 * <p>This package defines Common Graph's unified presentation pipeline,
 * merging the 2D Surface and 3D Spatial systems into a single model:
 *
 * <pre>
 * DATA + SCENE  →  VIEW  →  RENDER
 * (any)  (CBOR)    (CBOR)   (Platform)
 * </pre>
 *
 * <p>The Scene data model is specified in {@code docs/scene.md} — a
 * code-agnostic description of node types, properties, and behaviors.
 * This document covers the <b>Java implementation</b>: annotations,
 * classes, compiler pipeline, and migration from the current system.
 *
 * <h2>Key Principle</h2>
 *
 * <p>Scenes are <b>data</b>. The annotations described here are convenience —
 * they compile to CBOR-serializable scene trees that can equally be produced
 * by a WYSIWYG editor, a text tool, or a remote peer. The annotations are not
 * the scene — they're one way to define one.
 *
 * <h2>Annotation Namespace</h2>
 *
 * <p>All annotations live in the {@code Scene} interface under the
 * {@code @Scene} prefix. This is a clean break from the current split
 * between {@code @Surface.*} and {@code @Body}/@code @Space}.
 *
 * <h3>Structural Primitives</h3>
 * <ul>
 *   <li>{@code @Scene.Container(direction, gap, align, padding, background, depth, ...)}
 *       — directional box with children</li>
 *   <li>{@code @Scene.Text(content, bind, format, style)}
 *       — text content</li>
 *   <li>{@code @Scene.Image(alt, bind, image, size, fit, style)}
 *       — visual content with fallback chain</li>
 *   <li>{@code @Scene.Shape(type, fill, stroke, strokeWidth, d, depth, style)}
 *       — vector shapes</li>
 *   <li>{@code @Scene.Border(all, top, right, bottom, left, ...)}
 *       — border on any element</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <ul>
 *   <li>{@code @Scene.Layout(direction, regions)} — type-level</li>
 *   <li>{@code @Scene.Constraint(id, top, left, width, ...)} — edge-based</li>
 *   <li>{@code @Scene.Repeat(bind, columns)} for grid layouts</li>
 *   <li>{@code @Scene.FlexContainer(direction, justify, align)} + {@code @Scene.Flex(grow, shrink, basis)}</li>
 * </ul>
 *
 * <h3>Data Binding and Conditionals</h3>
 * <ul>
 *   <li>{@code @Scene.State(when, style)} — conditional styling</li>
 *   <li>{@code @Scene.On(event, action, target)} — event handlers</li>
 *   <li>{@code @Scene.If(value)} — visibility condition</li>
 *   <li>{@code @Scene.Query(value)} — renderer capability / container size query</li>
 *   <li>{@code @Scene.Bind(value)} — property path binding</li>
 *   <li>{@code @Scene.Repeat(bind, itemVar, indexVar)} — collection iteration</li>
 *   <li>{@code @Scene.Embed(bind)} — embed sub-schema</li>
 * </ul>
 *
 * <h3>3D Geometry</h3>
 * <ul>
 *   <li>{@code @Scene.Body(shape, width, height, depth, mesh, color, ...)}
 *       — 3D shape. Children project onto faces</li>
 *   <li>{@code @Scene.Face(value, ppm)} — target child to a body face</li>
 *   <li>{@code @Scene.Transform(x, y, z, yaw, pitch, roll, ...)}
 *       — 3D positioning/rotation/scale on any element</li>
 *   <li>{@code @Scene.Light(type, color, intensity, x, y, z, ...)}
 *       — light source</li>
 *   <li>{@code @Scene.Audio(src, x, y, z, volume, spatial, ...)}
 *       — positional 3D audio</li>
 * </ul>
 *
 * <h3>Environment</h3>
 * <ul>
 *   <li>{@code @Scene.Environment(background, ambient, fogNear, fogFar)}
 *       — scene-level environment</li>
 *   <li>{@code @Scene.Camera(projection, fov, near, far, x, y, z, ...)}
 *       — viewing perspective</li>
 * </ul>
 *
 * <h2>Core Classes</h2>
 *
 * <table>
 *   <tr><th>Class</th><th>Role</th></tr>
 *   <tr><td>{@code SceneSchema<T>}</td>
 *       <td>Base class for all scene patterns. Merges the current
 *       {@code SurfaceSchema} and {@code SpatialSchema}. CBOR-serializable.
 *       Subclasses override {@code render(SceneRenderer)} or use nested
 *       annotation classes.</td></tr>
 *   <tr><td>{@code SceneRenderer}</td>
 *       <td>Unified push-based renderer interface. Merges the current
 *       {@code SurfaceRenderer} and {@code SpatialRenderer}. 2D methods
 *       are abstract; 3D methods have default no-op implementations so
 *       text/2D renderers work unchanged.</td></tr>
 *   <tr><td>{@code SceneCompiler}</td>
 *       <td>Compiles annotations to scene trees. Replaces both
 *       the former {@code SurfaceCompiler} and {@code SpatialCompiler}. Single
 *       annotation scan, unified cache.</td></tr>
 *   <tr><td>{@code SceneModel}</td>
 *       <td>Stateful scene controller. Produces schema snapshots,
 *       handles events and key input, notifies on state changes.</td></tr>
 *   <tr><td>{@code View}</td>
 *       <td>Populated scene tree — the CBOR-serializable result of
 *       applying a schema to data.</td></tr>
 * </table>
 *
 * <h2>SceneSchema Fields</h2>
 *
 * <p>{@code SceneSchema<T>} carries these CBOR-serialized fields
 * (Canon order shown):
 *
 * <pre>
 * -1  T value            — bound data
 *  0  String id          — element identifier
 *  1  List style         — style classes
 *  2  boolean visible    — visibility (default true)
 *  3  boolean editable   — accepts input (default false)
 *  4  Boolean tabbable   — tab-order (null = default)
 *  5  String label       — accessibility label
 *  6  String labeledBy   — labeling element reference
 *  7  String size        — width+height shorthand
 *  8  String margin      — outer spacing
 *  9  String padding     — inner spacing
 * 10  List events        — event handlers
 * 11  double scaleX      — horizontal scale (default 1.0)
 * 12  double scaleY      — vertical scale (default 1.0)
 * 13  double scaleZ      — depth scale (default 1.0)
 * 14  double depth       — Z-axis extrusion in meters (default 0)
 * 15  boolean hasDepth   — whether depth is active
 * 16  BoxBorder boxBorder — border specification
 * 17  String boxBackground — background color
 * </pre>
 *
 * <h2>SceneRenderer Interface</h2>
 *
 * <p>The unified renderer merges 2D and 3D into one interface.
 * Methods are grouped by category:
 *
 * <h3>2D Primitives (abstract — all renderers must implement)</h3>
 * <pre>{@code
 * void text(String content, List<String> styles);
 * void formattedText(String content, String format, List<String> styles);
 * void image(String alt, ContentID image, ContentID solid, String resource,
 *            String size, String fit, List<String> styles);
 * void beginBox(Direction direction, List<String> styles);
 * void beginBox(Direction direction, List<String> styles, BoxBorder border,
 *               String background, String width, String height, String padding);
 * void endBox();
 * void type(String type);
 * void id(String id);
 * void editable(boolean editable);
 * void event(String on, String action, String target);
 * }</pre>
 *
 * <h3>2D Extras (default no-op)</h3>
 * <pre>{@code
 * void richText(List<TextSpan> spans, List<String> paragraphStyles);
 * void audio(String src, double volume, boolean loop, List<String> styles);
 * void gap(String gap);
 * void maxWidth(String maxWidth);
 * void rotation(double degrees);
 * void transformOrigin(float xFrac, float yFrac);
 * void shape(String type, String cornerRadius, String fill,
 *            String stroke, String strokeWidth, String path);
 * void shapeSize(String width, String height);
 * void model(String modelResource, int modelColor);
 * void tint(int argb);
 * void transitions(List<TransitionSpec> specs);
 * }</pre>
 *
 * <h3>Depth</h3>
 * <pre>{@code
 * void depth(double meters, boolean solid);  // replaces elevation()
 * }</pre>
 *
 * <h3>3D Geometry (default no-op — only Filament implements)</h3>
 * <pre>{@code
 * void body(String shape, double w, double h, double d,
 *           int color, double opacity, String shading, List<String> styles);
 * void meshBody(String meshRef, int color, double opacity,
 *               String shading, List<String> styles);
 * void line(double x1, double y1, double z1, double x2, double y2, double z2,
 *           int color, double width);
 * void pushTransform(double x, double y, double z,
 *                    double qx, double qy, double qz, double qw,
 *                    double sx, double sy, double sz);
 * void popTransform();
 * }</pre>
 *
 * <h3>Environment (default no-op)</h3>
 * <pre>{@code
 * void light(String type, int color, double intensity,
 *            double x, double y, double z,
 *            double dirX, double dirY, double dirZ);
 * void camera(String projection, double fov, double near, double far,
 *             double x, double y, double z,
 *             double tx, double ty, double tz);
 * void environment(int background, int ambient,
 *                  double fogNear, double fogFar, int fogColor);
 * void audio3d(String src, double x, double y, double z,
 *              double volume, double pitch, boolean loop,
 *              boolean spatial, double refDistance, double maxDistance,
 *              boolean autoplay);
 * }</pre>
 *
 * <h3>Queries</h3>
 * <pre>{@code
 * boolean beginQuery(String condition);  // returns false to skip subtree
 * void endQuery();
 * boolean supportsDepth();               // renderer capability probe
 * }</pre>
 *
 * <h2>Compiler Pipeline</h2>
 *
 * <p>{@code SceneCompiler} handles three compilation contexts:
 *
 * <h3>1. Schema classes (nested static classes with annotations)</h3>
 * <pre>{@code
 * @Scene.Container(direction = VERTICAL, gap = "0.5em")
 * public class ContactScene extends SceneSchema<Contact> {
 *
 *     @Scene.Text(bind = "value.name", style = "heading")
 *     static class Name {}
 *
 *     @Scene.Text(bind = "value.email", style = "muted")
 *     static class Email {}
 * }
 * }</pre>
 *
 * <h3>2. Model classes (annotated methods returning schemas)</h3>
 * <pre>{@code
 * @Scene(as = ConstraintScene.class)
 * class ItemModel extends SceneModel {
 *
 *     @Scene.Constraint(top = "0", height = "fit")
 *     SceneSchema header() { ... }
 *
 *     @Scene.Constraint(topTo = "header.bottom", bottom = "0")
 *     SceneSchema body() { ... }
 * }
 * }</pre>
 *
 * <h3>3. Data models (annotated fields on plain objects)</h3>
 * <p>Fields with {@code @Scene.Text} or {@code @Canon} annotations
 * are auto-compiled to scene trees based on type inference.
 *
 * <h3>Caching</h3>
 * <p>Per-class compilation results are cached in a
 * {@code ConcurrentHashMap}. Annotation scanning happens once per class.
 *
 * <h3>Binding Resolution</h3>
 * <p>Binding expressions resolve against live data via reflection:
 * <ol>
 *   <li>Fluent accessor: {@code obj.propertyName()}</li>
 *   <li>JavaBean getter: {@code obj.getPropertyName()}</li>
 *   <li>Boolean getter: {@code obj.isPropertyName()}</li>
 *   <li>Map key: for {@code Map} objects</li>
 * </ol>
 *
 * <p>Expression syntax:
 * <ul>
 *   <li>{@code "value.property.nested"} — nested property path</li>
 *   <li>{@code "$item"}, {@code "$index"} — repeat iteration variables</li>
 *   <li>{@code "$label"}, {@code "$id"}, {@code "$editable"} — schema properties</li>
 *   <li>{@code "bind:value.hourAngle"} — computed binding prefix</li>
 *   <li>{@code "$index == selectedIndex"}, {@code "value != null"} — comparisons</li>
 *   <li>{@code "!value"}, {@code "!$editable"} — negation</li>
 * </ul>
 *
 * <h2>Migration from Current System</h2>
 *
 * <h3>Annotation Renames</h3>
 * <table>
 *   <tr><th>Before</th><th>After</th></tr>
 *   <tr><td>{@code @Surface.Container}</td><td>{@code @Scene.Container}</td></tr>
 *   <tr><td>{@code @Surface.Text}</td><td>{@code @Scene.Text}</td></tr>
 *   <tr><td>{@code @Surface.Image}</td><td>{@code @Scene.Image}</td></tr>
 *   <tr><td>{@code @Surface.Shape}</td><td>{@code @Scene.Shape}</td></tr>
 *   <tr><td>{@code @Surface.Border}</td><td>{@code @Scene.Border}</td></tr>
 *   <tr><td>{@code @Surface.State}</td><td>{@code @Scene.State}</td></tr>
 *   <tr><td>{@code @Surface.On}</td><td>{@code @Scene.On}</td></tr>
 *   <tr><td>{@code @Surface.If}</td><td>{@code @Scene.If}</td></tr>
 *   <tr><td>{@code @Surface.Query}</td><td>{@code @Scene.Query}</td></tr>
 *   <tr><td>{@code @Surface.Repeat}</td><td>{@code @Scene.Repeat}</td></tr>
 *   <tr><td>{@code @Surface.Embed}</td><td>{@code @Scene.Embed}</td></tr>
 *   <tr><td>{@code @Body(...)}</td><td>{@code @Scene.Body(...)}</td></tr>
 *   <tr><td>{@code @Body.Placement(...)}</td><td>{@code @Scene.Transform(...)}</td></tr>
 *   <tr><td>{@code @Body.Light(...)}</td><td>{@code @Scene.Light(...)}</td></tr>
 *   <tr><td>{@code @Body.Audio(...)}</td><td>{@code @Scene.Audio(...)}</td></tr>
 *   <tr><td>{@code @Space.Environment(...)}</td><td>{@code @Scene.Environment(...)}</td></tr>
 *   <tr><td>{@code @Space.Camera(...)}</td><td>{@code @Scene.Camera(...)}</td></tr>
 * </table>
 *
 * <h3>Semantic Changes</h3>
 * <table>
 *   <tr><th>Before</th><th>After</th></tr>
 *   <tr><td>{@code @Surface.Elevation(height="1cm")}</td>
 *       <td>{@code @Scene.Container(depth="1cm")} or
 *       {@code @Scene.Shape(depth="1cm")}</td></tr>
 *   <tr><td>{@code @Body.Panel(face="front", ppm=512)}</td>
 *       <td>{@code @Scene.Face("front")} on child element</td></tr>
 *   <tr><td>{@code @Surface(as=MySurface.class)}</td>
 *       <td>{@code @Scene(as=MyScene.class)}</td></tr>
 *   <tr><td>{@code @Type(scene=MySpatialSchema.class)}</td>
 *       <td>{@code @Type(scene=MySceneSchema.class)}</td></tr>
 * </table>
 *
 * <h3>Class Renames</h3>
 * <table>
 *   <tr><th>Before</th><th>After</th></tr>
 *   <tr><td>{@code SurfaceSchema<T>}</td><td>{@code SceneSchema<T>}</td></tr>
 *   <tr><td>{@code SpatialSchema<T>}</td><td>merged into {@code SceneSchema<T>}</td></tr>
 *   <tr><td>{@code SurfaceRenderer}</td><td>{@code SceneRenderer}</td></tr>
 *   <tr><td>{@code SpatialRenderer}</td><td>merged into {@code SceneRenderer}</td></tr>
 *   <tr><td>{@code SurfaceCompiler}</td><td>{@code SceneCompiler}</td></tr>
 *   <tr><td>{@code SpatialCompiler}</td><td>merged into {@code SceneCompiler}</td></tr>
 *   <tr><td>{@code CompiledBody}, {@code CompiledSpace}</td>
 *       <td>internal to {@code SceneCompiler}</td></tr>
 * </table>
 *
 * <h3>What Disappears</h3>
 * <ul>
 *   <li>{@code @Body.Panel} — replaced by {@code @Scene.Face} on children</li>
 *   <li>{@code @Surface.Elevation} — replaced by {@code depth} attribute
 *       on Container/Shape</li>
 *   <li>{@code @Space} root annotation — replaced by
 *       {@code @Scene.Environment} + {@code @Scene.Camera} + {@code @Scene.Light}</li>
 *   <li>{@code composeSurfaceOnBody()} — becomes internal to the compiler</li>
 * </ul>
 *
 * <h3>What's New</h3>
 * <ul>
 *   <li>{@code depth} attribute on Container and Shape</li>
 *   <li>{@code @Scene.Face} for targeting body faces</li>
 *   <li>{@code @Scene.Query("depth")} for renderer capability queries</li>
 *   <li>{@code line()} primitive on SceneRenderer</li>
 *   <li>{@code supportsDepth()} on SceneRenderer</li>
 *   <li>{@code scaleZ} on SceneSchema</li>
 * </ul>
 *
 * <h2>Example: Chess Board (Unified)</h2>
 *
 * <pre>{@code
 * @Scene.Body(shape = "box", width = "44cm", height = "0.1m",
 *             depth = "44cm", color = 0x8B4513)
 * @Scene.Container(direction = Direction.VERTICAL)
 * public class ChessBoard {
 *
 *     @Scene.Repeat(bind = "value.ranks")
 *     @Scene.Container(direction = Direction.HORIZONTAL, style = "rank")
 *     static class Rank {
 *
 *         @Scene.Repeat(bind = "$item.squares")
 *         @Scene.Container(id = "bind:$item.id",
 *                 width = "2.5em", height = "2.5em", depth = "1cm")
 *         @Scene.State(when = "$item.light", style = {"light"})
 *         @Scene.State(when = "!$item.light", style = {"dark"})
 *         static class Square {
 *             @Scene.If("$item.piece")
 *             @Scene.Image(bind = "$item.piece", size = "2.25em")
 *             static class PieceImg {}
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Example: Procedural Geometry</h2>
 *
 * <pre>{@code
 * public class AxisGizmo extends SceneSchema<Void> {
 *     private double length = 0.5;
 *     private double width = 0.003;
 *
 *     @Override
 *     public void render(SceneRenderer out) {
 *         out.line(0, 0, 0, length, 0, 0, 0xFF0000, width);
 *         out.line(0, 0, 0, 0, length, 0, 0x00FF00, width);
 *         out.line(0, 0, 0, 0, 0, length, 0x0000FF, width);
 *     }
 * }
 * }</pre>
 *
 * @see dev.everydaythings.graph.ui.scene.surface.Surface
 * @see dev.everydaythings.graph.ui.scene.surface.SurfaceSchema
 * @see dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer
 * @see dev.everydaythings.graph.ui.scene.spatial.SpatialSchema
 * @see dev.everydaythings.graph.ui.scene.spatial.SpatialRenderer
 * @see dev.everydaythings.graph.ui.scene.SceneModel
 */
package dev.everydaythings.graph.ui.scene;
