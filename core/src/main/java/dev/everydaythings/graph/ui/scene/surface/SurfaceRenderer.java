package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.TransitionSpec;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.List;

/**
 * Platform-agnostic rendering interface for the three primitives.
 *
 * <p>Surfaces describe themselves by emitting render instructions to this interface.
 * Platform-specific renderers implement this interface to build their native output.
 *
 * <p>There are exactly THREE primitive types:
 * <ul>
 *   <li>{@link #text} - Text content</li>
 *   <li>{@link #image} - Visual content (emoji/image/3D with fallback chain)</li>
 *   <li>{@link #beginBox}/{@link #endBox} - Container with direction and children</li>
 * </ul>
 *
 * <p>All composite surfaces (buttons, lists, trees, etc.) are built from these
 * primitives. Style classes convey semantic meaning to the renderer.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Surface describes itself using primitives
 * public void render(SurfaceRenderer out) {
 *     out.beginBox(Direction.VERTICAL, List.of("card"));
 *     out.text("Hello", List.of("heading"));
 *     out.endBox();
 * }
 *
 * // Platform implements SurfaceRenderer
 * class SkiaSurfaceRenderer implements SurfaceRenderer {
 *     public void text(String content, List&lt;String&gt; styles) {
 *         var text = new LayoutNode.TextNode(content, styles);
 *         addToCurrentContainer(text);
 *     }
 * }
 * </pre>
 */
public interface SurfaceRenderer {

    // ==================== Text Primitive ====================

    /**
     * Emit text content.
     *
     * @param content The text to display
     * @param styles  Style classes (e.g., "heading", "muted", "code")
     */
    void text(String content, List<String> styles);

    /**
     * Emit formatted text (markdown, code, date, etc.).
     *
     * @param content The text content
     * @param format  Format hint: "markdown", "code", "date", "json", etc.
     * @param styles  Style classes
     */
    void formattedText(String content, String format, List<String> styles);

    /**
     * Emit rich text with mixed styles.
     *
     * <p>Each {@link TextSpan} carries its own style classes, allowing bold,
     * italic, code, and other formatting within a single paragraph.
     *
     * <p>Default implementation concatenates span text and emits as plain text,
     * so CLI/TUI renderers work unchanged.
     *
     * @param spans           Styled text spans
     * @param paragraphStyles Styles applying to the paragraph as a whole
     */
    default void richText(List<TextSpan> spans, List<String> paragraphStyles) {
        StringBuilder sb = new StringBuilder();
        for (TextSpan span : spans) sb.append(span.text());
        text(sb.toString(), paragraphStyles);
    }

    // ==================== Image Primitive ====================

    /**
     * Emit visual content with fallback chain: solid → image → resource → alt.
     *
     * <p>Renderers use the highest-fidelity option available:
     * <ul>
     *   <li>3D renderers use solid if available</li>
     *   <li>GUI renderers use resource or image if available, else alt</li>
     *   <li>Text renderers always use alt</li>
     * </ul>
     *
     * @param alt      Text/emoji fallback (required) - always works
     * @param image    Content-addressed 2D image (optional)
     * @param solid    Content-addressed 3D geometry (optional)
     * @param resource Classpath resource path for 2D icon (optional)
     * @param size     Size hint: "small", "medium", "large", or explicit like "32px"
     * @param fit      How to fit: "contain", "cover", "fill", "none"
     * @param styles   Style classes
     */
    default void image(String alt, ContentID image, ContentID solid, String resource,
                       String size, String fit, List<String> styles) {
        image(alt, image, solid, size, fit, styles);
    }

    /**
     * Emit visual content with fallback chain: solid → image → alt.
     *
     * @param alt    Text/emoji fallback (required) - always works
     * @param image  Content-addressed 2D image (optional)
     * @param solid  Content-addressed 3D geometry (optional)
     * @param size   Size hint: "small", "medium", "large", or explicit like "32px"
     * @param fit    How to fit: "contain", "cover", "fill", "none"
     * @param styles Style classes
     */
    void image(String alt, ContentID image, ContentID solid, String size, String fit, List<String> styles);

    // ==================== Container Primitive ====================

    /**
     * Begin a box container.
     *
     * <p>Boxes are the universal container. Style classes indicate semantics:
     * <ul>
     *   <li>"list", "list-item" - List semantics</li>
     *   <li>"tree", "tree-node", "expanded", "collapsed" - Tree semantics</li>
     *   <li>"button", "primary", "danger" - Button semantics</li>
     *   <li>"chip", "badge" - Compact token semantics</li>
     *   <li>"tabs", "tab", "tab-active" - Tab semantics</li>
     *   <li>"split", "region" - Split pane semantics</li>
     * </ul>
     *
     * @param direction Layout direction (HORIZONTAL or VERTICAL)
     * @param styles    Style classes for semantic meaning
     */
    void beginBox(Scene.Direction direction, List<String> styles);

    /**
     * Begin a box container with explicit visual properties.
     *
     * <p>This extended form passes border, background, sizing, and padding
     * directly to the renderer. The renderer draws exactly what it's told.
     *
     * <p>Renderers that don't support visual properties can fall back to
     * the simple 2-arg form (this default implementation does that).
     *
     * @param direction  Layout direction (HORIZONTAL or VERTICAL)
     * @param styles     Style classes for semantic meaning
     * @param border     Per-side border specification
     * @param background Background color/style
     * @param width      Explicit width (e.g., "40ch", "200px")
     * @param height     Explicit height (e.g., "10ln", "100px")
     * @param padding    Padding inside the box (e.g., "1ch", "8px 16px")
     */
    default void beginBox(Scene.Direction direction, List<String> styles,
                          BoxBorder border, String background,
                          String width, String height, String padding) {
        beginBox(direction, styles);
    }

    /**
     * End the current box container.
     */
    void endBox();

    // ==================== Metadata ====================

    /**
     * Set the type for the next element.
     *
     * <p>Type is used for style matching (e.g., "TreeNode", "Librarian").
     * Combined with style classes, this forms the element's identity for
     * stylesheet resolution.
     *
     * @param type The element type (usually the Item or component type name)
     */
    void type(String type);

    /**
     * Set the ID for the next element.
     *
     * <p>IDs are used for event targeting, accessibility, and style matching.
     */
    void id(String id);

    /**
     * Mark the next element as editable.
     */
    void editable(boolean editable);

    // ==================== Events ====================

    /**
     * Add an event handler for the next element.
     *
     * <p>Events are attached to the next rendered element (text, image, or box).
     *
     * @param on     Event type: "click", "doubleClick", "expand", "collapse", "select"
     * @param action Action verb to dispatch
     * @param target Target reference (empty string = self)
     */
    void event(String on, String action, String target);

    // ==================== Audio ====================

    /**
     * Emit an audio control.
     *
     * <p>Rendered as a play/pause control with progress. The actual audio
     * playback is handled by the platform renderer. Events: "play", "pause",
     * "seek".
     *
     * @param src     Content reference to audio asset
     * @param volume  Volume (0.0-1.0)
     * @param loop    Whether to loop
     * @param styles  Style classes
     */
    default void audio(String src, double volume, boolean loop, List<String> styles) {}

    // ==================== Transitions ====================

    /**
     * Set transition specs for the next element.
     *
     * <p>Transition specs declare which properties should animate when they change.
     * Renderers that support animation use these to drive interpolation.
     *
     * @param specs The transition specifications
     */
    default void transitions(java.util.List<TransitionSpec> specs) {}

    // ==================== Rotation ====================

    /**
     * Set rotation for the next box container.
     *
     * <p>The container rotates around its center point by the given angle.
     * In 2D, this is a canvas rotation. In 3D, this rotates around the Z axis.
     *
     * @param degrees Rotation angle in degrees (clockwise)
     */
    default void rotation(double degrees) {}

    /**
     * Set the transform origin (rotation pivot) for the next box container.
     * Called before {@link #rotation(double)} when a non-default origin is specified.
     *
     * @param xFrac Horizontal fraction (0.0 = left, 0.5 = center, 1.0 = right)
     * @param yFrac Vertical fraction (0.0 = top, 0.5 = center, 1.0 = bottom)
     */
    default void transformOrigin(float xFrac, float yFrac) {}

    // ==================== Gap ====================

    /**
     * Set the gap (spacing between children) for the next box container.
     *
     * @param gap Size string like "8px", "1em"
     */
    default void gap(String gap) {}

    /**
     * Set the maximum width for the next box container.
     *
     * @param maxWidth Size string like "45%", "300px"
     */
    default void maxWidth(String maxWidth) {}

    /**
     * Set the overflow behavior for the next box container.
     *
     * @param overflow "visible", "hidden", "scroll", or "auto"
     */
    default void overflow(String overflow) {}

    /**
     * Set the maximum height for the next box container (used with overflow).
     *
     * @param maxHeight Size string like "300px", "50%"
     */
    default void maxHeight(String maxHeight) {}

    // ==================== Text Font Size ====================

    /**
     * Set the font size for the next text element.
     *
     * <p>Supports: "1.5em", "80%", "20px". Percentage resolves against
     * parent container height, enabling text that scales with its cell.
     *
     * @param spec font size specification
     */
    default void textFontSize(String spec) {}

    /**
     * Set the font family for subsequent text in the current surface scope.
     *
     * <p>Renderers should apply this to the next text node and to container nodes
     * so descendants can inherit it unless they override locally.
     *
     * @param family font family token or explicit family name
     */
    default void textFontFamily(String family) {}

    // ==================== Aspect Ratio ====================

    /**
     * Set the aspect ratio for the next box container.
     *
     * <p>Parsed by the layout engine: "1" (square), "16/9", "4/3".
     * When set, the container enforces width/height = ratio.
     *
     * @param ratio aspect ratio string
     */
    default void aspectRatio(String ratio) {}

    // ==================== Shape ====================

    // ==================== Model (3D hint) ====================

    /**
     * Set the 3D model resource for the next image element.
     *
     * <p>When a 3D renderer encounters an image with a model resource,
     * it loads the GLB/glTF mesh and places it at the image's layout position.
     * 2D renderers inherit the no-op default.
     *
     * @param modelResource Classpath path to GLB/glTF file
     * @param modelColor    Material color override (-1 = use model default)
     */
    default void model(String modelResource, int modelColor) {}

    /**
     * Set foreground tint color for the next image/glyph.
     *
     * <p>In MSDF (3D) rendering, emojis are monochrome outlines tinted by this color.
     * 2D renderers may use this to tint the alt text glyph.
     *
     * @param argb ARGB color (0 = use renderer default)
     */
    default void tint(int argb) {}

    // ==================== Elevation (3D hint) ====================

    /**
     * Set 3D elevation for the next box container.
     *
     * <p>When a 3D renderer encounters an elevated box, it creates a slab
     * with the specified height. Children render on top of the slab.
     * 2D renderers inherit the no-op default.
     *
     * @param height Height in meters
     * @param solid  Whether to render as a solid 3D slab or just Z offset
     */
    default void elevation(double height, boolean solid) {}

    // ==================== Shape ====================

    /**
     * Emit a shape primitive or set shape properties for the next container.
     *
     * <p>Shapes define visible geometry. When emitted before a {@link #beginBox},
     * the shape acts as the container's visible boundary. When emitted standalone,
     * it is a self-contained visual element (divider line, dot, SVG icon, etc.).
     *
     * <p>Shape types:
     * <ul>
     *   <li>"rectangle" - standard box with optional corner radius</li>
     *   <li>"circle" - perfect circle</li>
     *   <li>"line" - horizontal or vertical line</li>
     *   <li>"path" - SVG-style path (for complex shapes)</li>
     * </ul>
     *
     * @param type         Shape type: "rectangle", "circle", "line", "path"
     * @param cornerRadius Corner radius for rectangles: "small", "medium", "large", "pill", or explicit
     * @param fill         Fill color/style
     * @param stroke       Stroke color/style
     * @param strokeWidth  Stroke width
     * @param path         SVG path data (for type = "path"), or empty
     */
    default void shape(String type, String cornerRadius, String fill,
                       String stroke, String strokeWidth, String path) {
        // Default implementation does nothing - renderers can override
    }

    /**
     * Set explicit size for the next shape element.
     *
     * <p>Called before {@link #shape} to set width/height from {@code @Surface.Shape}
     * annotations with explicit sizing.
     *
     * @param width  Explicit width (e.g., "4px", "2em"), or empty
     * @param height Explicit height (e.g., "50px", "1em"), or empty
     */
    default void shapeSize(String width, String height) {}

    // ==================== Context Menu ====================

    /**
     * Show a floating context menu at the given position.
     *
     * <p>Items are pre-filtered (only visible items are passed). The renderer
     * is responsible for positioning, keyboard navigation, and dismissal.
     *
     * @param x     Screen X coordinate
     * @param y     Screen Y coordinate
     * @param items The menu items to display
     */
    default void showContextMenu(float x, float y,
                                 java.util.List<dev.everydaythings.graph.ui.scene.ContextMenuItem> items) {}

    /**
     * Dismiss the active context menu, if any.
     */
    default void dismissContextMenu() {}

    // ==================== Container Queries ====================

    /**
     * Begin a container query — conditionally render based on container size.
     *
     * <p>Called by the compiler when a {@link Surface.Query} annotation is present.
     * The renderer evaluates the condition against the current container's actual
     * dimensions and returns whether the following subtree should be rendered.
     *
     * <p>If this returns false, the compiler skips all render calls until
     * the matching {@link #endQuery()}.
     *
     * @param condition The query condition (e.g., "width >= 30ch")
     * @return true if the condition matches and the subtree should render
     */
    default boolean beginQuery(String condition) { return true; }

    /**
     * End a container query group.
     *
     * <p>Always called after {@link #beginQuery}, regardless of whether
     * the condition matched.
     */
    default void endQuery() {}
}
