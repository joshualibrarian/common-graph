package dev.everydaythings.graph.ui.scene;

import java.lang.annotation.*;

/**
 * Unified presentation annotation for Common Graph — covers both 2D surfaces
 * and 3D spatial scenes in a single namespace.
 *
 * <p>Scene unifies the functionality of {@code @Surface}, {@code @Body}, and
 * {@code @Space} into one annotation interface. Items declare their visual
 * structure using {@code @Scene.*} annotations, and the system renders them
 * in 2D or 3D depending on the renderer's capabilities.
 *
 * <h2>The Pipeline</h2>
 * <pre>
 * DATA + SCENE → VIEW → RENDER
 * (CBOR)  (CBOR)   (CBOR)  (Platform)
 * </pre>
 *
 * <h2>Usage on Fields</h2>
 * <pre>{@code
 * @Canon(0)
 * @Scene(style = {"heading", "primary"})
 * private String title;
 *
 * @Canon(1)
 * @Scene(format = "markdown", label = "Description")
 * private String body;
 * }</pre>
 *
 * <h2>Usage on Types (2D)</h2>
 * <pre>{@code
 * @Scene.Layout(direction = Direction.HORIZONTAL)
 * public class PersonSurface extends SceneSchema<Person> {
 *
 *     @Scene.Text(bind = "value.name", style = "heading")
 *     static class Name {}
 *
 *     @Scene.Text(bind = "value.email", style = "muted")
 *     static class Email {}
 * }
 * }</pre>
 *
 * <h2>Usage on Types (3D)</h2>
 * <pre>{@code
 * @Scene.Body(shape = "box", width = "44cm", height = "0", depth = "44cm")
 * @Scene.Light(type = "directional", z = "10m", dirZ = -1, intensity = 0.8)
 * @Scene.Camera(y = "1.5m", z = "1m", targetZ = "0")
 * public class ChessGame extends Item { ... }
 * }</pre>
 *
 * <h2>Mixed 2D + 3D</h2>
 * <pre>{@code
 * @Scene.Body(shape = "box", width = "44cm", depth = "44cm")
 * @Scene.Container(direction = Direction.VERTICAL)
 * public class ChessBoard extends SceneSchema<ChessState> {
 *
 *     @Scene.Repeat(bind = "value.rows")
 *     @Scene.Container(direction = Direction.HORIZONTAL)
 *     static class Row {
 *
 *         @Scene.Repeat(bind = "$item")
 *         @Scene.Container(shape = "rectangle", depth = "1cm")
 *         static class Square { ... }
 *     }
 * }
 * }</pre>
 *
 * @see SceneSchema
 * @see SceneRenderer
 * @see SceneCompiler
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
public @interface Scene {

    // ===== Identity =====

    /** ID for referencing this element in layout constraints and styles. */
    String id() default "";

    /** Style classes (like CSS classes). */
    String[] classes() default {};

    // ===== What to Render As =====

    /**
     * Schema class to use for rendering this field.
     *
     * <p>If not specified, the system infers from the field type.
     * Can point to a SceneSchema subclass or a model class with
     * {@code @Scene.*} annotations.
     */
    Class<?> as() default SceneSchema.class;

    /** Rendering mode for nested items. */
    SceneMode mode() default SceneMode.FULL;

    // ===== Value Interpretation =====

    /** Format for interpreting/rendering the value: "markdown", "date", "code", etc. */
    String format() default "";

    // ===== Conditional Styles =====

    /** Conditional style rules. */
    Style[] styles() default {};

    // ===== Labeling =====

    /** Direct label text for this field. */
    String label() default "";

    /** Reference to another field's ID that acts as the label for this field. */
    String labeledBy() default "";

    // ===== Layout Hints =====

    /**
     * Target region name for this field.
     *
     * @deprecated Use container IDs + {@link Attach}/{@link Constraint}
     * relationships instead of separate region naming.
     */
    @Deprecated
    String region() default "";

    /** Grouping key for auto-layout. */
    String group() default "";

    /** Display order within the region (-1 = use @Canon order). */
    int order() default -1;

    /** Size constraint: "auto", "1fr", "300px", "50%". */
    String size() default "";

    // ===== Behavior =====

    /** Whether this field is visible by default. */
    boolean visible() default true;

    /** Whether this field can be edited (renders input controls). */
    boolean editable() default false;

    // ===== Events =====

    /** Event handlers for this element. */
    Event[] events() default {};

    // ===================================================================
    // Style Rules
    // ===================================================================

    /**
     * Declares a style rule on the class that owns the styled elements.
     *
     * <p>Style rules are scanned from the classpath at startup and assembled
     * into a {@link Stylesheet}. This replaces centralized style defaults
     * with data-on-the-type declarations.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * @Scene.Rule(match = ".heading", color = "#89B4FA", fontSize = "1.33")
     * @Scene.Rule(match = ".square.light", background = "#F0D9B5")
     * @Scene.Rule(match = ".chrome!tui", display = "visible", opacity = "dim")
     * @Type(value = "cg:type/chess-board", glyph = "♟")
     * public class ChessBoard { ... }
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Rules.class)
    @interface Rule {

        /** CSS-like selector: ".heading", ".square.light", ":selected". */
        String match();

        /** Foreground color as hex: "#89B4FA". */
        String color() default "";

        /** Background color as hex "#313244" or keyword "reverse". */
        String background() default "";

        /** Font size ratio relative to base: "1.33", "0.87". */
        String fontSize() default "";

        /** Font family: "monospace". */
        String fontFamily() default "";

        /** Font weight: "bold". */
        String fontWeight() default "";

        /** Display mode: "hidden", "visible". */
        String display() default "";

        /** Opacity: "dim", "bright". */
        String opacity() default "";

        /** Rotation: "90deg". */
        String rotation() default "";
    }

    /** Container for multiple {@link Rule} annotations on a single type. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Rules {
        Rule[] value();
    }

    // ===================================================================
    // Layout Direction
    // ===================================================================

    /** Layout direction for arranging regions/fields. */
    enum Direction {
        VERTICAL,
        HORIZONTAL,
        /**
         * Stack layout — children overlap at the same position.
         *
         * <p>Like CSS {@code position: absolute} within a relative container.
         * All children are positioned at the same origin, painted in order
         * (last child on top in 2D). Container size = max(child widths) x max(child heights).
         *
         * <p>In 3D mode, gap controls Z-separation between layers.
         */
        STACK
    }

    // ===================================================================
    // Layout Structure
    // ===================================================================

    /** Defines the overall layout structure for a type's surface. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Layout {

        /** Layout direction for auto-arranged fields. */
        Direction direction() default Direction.VERTICAL;

        /** Explicit region definitions. */
        Region[] regions() default {};
    }

    /** Defines a named region within a layout. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Region {

        /** Region name (referenced by field's region attribute). */
        String name();

        /** Size specification: "auto", "1fr", "300px", "50%". */
        String size() default "1fr";

        /** Minimum size constraint. */
        String minSize() default "";

        /** Maximum size constraint. */
        String maxSize() default "";
    }

    // ===================================================================
    // Events
    // ===================================================================

    /** Defines an event handler for a scene element. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Event {

        /** Event type: "click", "doubleClick", "drag", "drop", "hover", "focus". */
        String on();

        /** Action verb/sememe to dispatch, or special prefix like "toggle:expanded". */
        String action();

        /** Target for the action ("", "self", "parent", "iid:xxx"). */
        String target() default "";
    }

    // ===================================================================
    // Conditional Styles
    // ===================================================================

    /**
     * Conditional style properties.
     *
     * <p>Condition syntax:
     * <ul>
     *   <li>{@code ":selected"}, {@code ":hover"} — state conditions</li>
     *   <li>{@code "!gui"}, {@code "!tui"} — platform negation</li>
     *   <li>{@code "@narrow"}, {@code "@wide"} — responsive breakpoints</li>
     * </ul>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Style {

        /** Condition for when this style applies (empty = always). */
        String when() default "";

        /** Display mode: "visible", "hidden". */
        String display() default "";

        /** Opacity: "dim", "normal", "bright", or "0.0"-"1.0". */
        String opacity() default "";

        /** Foreground color. */
        String color() default "";

        /** Background color, or "reverse" for inverted. */
        String background() default "";

        /** Font weight: "normal", "bold". */
        String font() default "";

        /** Text decoration: "none", "underline", "strikethrough". */
        String decoration() default "";

        /** Padding inside element: "4px", "1em", "8px 16px". */
        String padding() default "";

        /** Margin outside element: "4px", "1em", "8px 16px". */
        String margin() default "";

        /** Border style: "none", "solid", "dashed". */
        String border() default "";

        /** Border radius: "4px", "50%". */
        String radius() default "";

        /** Content rendering hint: "box-drawing", "ascii". */
        String content() default "";

        /** Icon/emoji override. */
        String icon() default "";
    }

    // ===================================================================
    // Structural Primitives
    // ===================================================================

    /**
     * Defines a container that holds and arranges children.
     *
     * <p>Extends the Surface.Container concept with a {@code depth} attribute
     * for 3D extrusion. When {@code depth} is set (e.g., "1cm"), the container
     * becomes a solid slab in 3D rendering.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Container {

        /**
         * Element ID for referencing in styles, hit-testing, event targets, and
         * as an addressable layout frame ("place role") for attachments.
         */
        String id() default "";

        /** Layout direction for children. */
        Direction direction() default Direction.HORIZONTAL;

        /** Style class(es) applied to this container. */
        String[] style() default {};

        /** Shorthand for shape type: "rectangle", "pill", "circle", or empty. */
        String shape() default "";

        /** Corner radius: "none", "small", "medium", "large", "pill", "circle", or explicit. */
        String cornerRadius() default "";

        /** Background color or style. */
        String background() default "";

        /** Inner spacing (padding). */
        String padding() default "";

        /** Gap between children. */
        String gap() default "";

        /** Size constraint: "auto", "fit", "fill", "1em", "100px", etc. */
        String size() default "";

        /** Explicit width: "40ch", "200px", "20em". */
        String width() default "";

        /** Explicit height: "10ln", "100px", "5em". */
        String height() default "";

        /** Cross-axis alignment: "start", "center", "end", "stretch". */
        String align() default "";

        /** Rotation in degrees. Static or data-bound ("bind:value.angle"). */
        String rotation() default "";

        /** Transform origin for rotation pivot. CSS keywords or percentages. */
        String transformOrigin() default "";

        /**
         * Aspect ratio (width/height): "1" (square), "16/9", "4/3".
         *
         * <p>When set, the container enforces this ratio during layout.
         * If only width is known, height is computed. If only height, width is computed.
         * If both are explicit, aspectRatio is ignored.
         */
        String aspectRatio() default "";

        /**
         * 3D depth (extrusion height). When set, the container becomes a solid slab.
         *
         * <p>Examples: "1cm", "3mm", "0.1m". Empty string means no depth (flat).
         * Replaces {@code @Surface.Elevation}.
         */
        String depth() default "";

        /**
         * Overflow behavior when content exceeds container bounds.
         *
         * <ul>
         *   <li>"visible" (default) — content overflows, no clipping</li>
         *   <li>"hidden" — content is clipped, no scrolling</li>
         *   <li>"scroll" — content is clipped, scrollbar always shown</li>
         *   <li>"auto" — scrollbar shown only when content overflows</li>
         * </ul>
         */
        String overflow() default "visible";

        /**
         * Preferred font family for descendant text in this container.
         *
         * <p>Examples: "monospace", "sans-serif", "SF Pro", "Symbols Nerd Font Mono".
         * Text nodes can override this locally via {@link Text#fontFamily()}.
         */
        String fontFamily() default "";

        /**
         * Preferred font size for descendant text in this container.
         *
         * <p>Examples: "1em", "14px", "80%". Text nodes can override locally
         * via {@link Text#fontSize()}.
         */
        String fontSize() default "";
    }

    /**
     * Defines text content.
     *
     * <p>Text can be literal or bound to a value property.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Text {

        /** Literal text content. */
        String content() default "";

        /** Binding expression for dynamic text: "name", "$label", "$id". */
        String bind() default "";

        /** Text format: "plain", "markdown", "code", etc. */
        String format() default "plain";

        /** Style class(es) for the text. */
        String[] style() default {};

        /**
         * Font size: "1.5em", "80%", "20px".
         *
         * <p>Percentage resolves against parent container height,
         * enabling text that scales with its tile/cell.
         */
        String fontSize() default "";

        /**
         * Preferred font family for this text node.
         *
         * <p>Examples: "monospace", "sans-serif", "SF Pro", "Symbols Nerd Font Mono".
         */
        String fontFamily() default "";
    }

    /**
     * Defines image/icon content with fallback chain.
     *
     * <p>Extended with {@code modelResource} and {@code modelColor} for 3D model hints.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Image {

        /** Alt text / emoji fallback. */
        String alt() default "";

        /** Binding expression for dynamic image. */
        String bind() default "";

        /** Content ID of 2D image (optional). */
        String image() default "";

        /** Size hint: "small", "medium", "large", or explicit like "1em", "24px". */
        String size() default "medium";

        /** How to fit: "contain", "cover", "fill", "none". */
        String fit() default "contain";

        /** Style class(es) for the image. */
        String[] style() default {};

        /** Classpath path to GLB/glTF 3D model for this image. */
        String modelResource() default "";

        /** Material color override for the 3D model (-1 = use model default). */
        int modelColor() default -1;
    }

    /**
     * Defines vector shape geometry.
     *
     * <p>All shapes accept {@code @Scene.Transform} for full 3D positioning.
     * Shapes with {@code depth} extrude into 3D. 2D renderers gracefully
     * degrade (project to 2D, ignore Z); 3D renderers apply full transforms.
     *
     * <p>Extended with a {@code depth} attribute for 3D extrusion.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Shape {

        /**
         * Shape type. Flat shapes (2D-native, extrudable via {@code depth}):
         * "rectangle", "circle", "ellipse", "line", "path", "polygon", "point".
         * Volumetric shapes (3D-native, project to 2D):
         * "sphere", "cone", "capsule", "plane".
         */
        String type();

        /** Corner radius for rectangles. */
        String cornerRadius() default "";

        /** Fill color/style. */
        String fill() default "";

        /** Stroke color/style. */
        String stroke() default "";

        /** Stroke width. */
        String strokeWidth() default "";

        /** SVG path data (for type = "path"). */
        String d() default "";

        /** Explicit width: "2px", "4px", "1em", etc. */
        String width() default "";

        /** Explicit height: "12px", "50px", "1em", etc. */
        String height() default "";

        /** Size shorthand (sets both width and height). */
        String size() default "";

        /** Style class(es) for the shape. */
        String[] style() default {};

        /** Rotation in degrees. Static or data-bound ("bind:..."). */
        String rotation() default "";

        /** Transform origin for rotation pivot. */
        String transformOrigin() default "";

        /**
         * 3D depth (extrusion height). Replaces {@code @Surface.Elevation}.
         *
         * <p>Examples: "1cm", "3mm", "0.1m". Empty string means flat.
         */
        String depth() default "";
    }

    // ===================================================================
    // Border
    // ===================================================================

    /** Defines border properties for any scene element. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Border {

        /** Shorthand for all sides: "1px solid blue". */
        String all() default "";

        /** Top side override. */
        String top() default "";

        /** Right side override. */
        String right() default "";

        /** Bottom side override. */
        String bottom() default "";

        /** Left side override. */
        String left() default "";

        /** Border width shorthand (all sides). */
        String width() default "";

        /** Border style shorthand (all sides): "solid", "dashed", "double", "dotted". */
        String style() default "";

        /** Border color shorthand (all sides). */
        String color() default "";

        /** Border radius: "4px", "0.5em", "none". */
        String radius() default "";
    }

    // ===================================================================
    // State & Behavior
    // ===================================================================

    /**
     * Maps value state to style classes.
     *
     * <p>Condition syntax:
     * <ul>
     *   <li>{@code "value"} — value is truthy</li>
     *   <li>{@code "!value"} — value is falsy</li>
     *   <li>{@code "value.selected"} — nested property check</li>
     *   <li>{@code "$editable"} — schema property check</li>
     * </ul>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Repeatable(States.class)
    @interface State {

        /** Style class(es) to add when condition is true. */
        String[] style();

        /** Condition expression. */
        String when();
    }

    /** Container for multiple @State annotations. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface States {
        State[] value();
    }

    /**
     * Binds an event to an action.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Repeatable(On.Events.class)
    @interface On {

        /** Event type: "click", "doubleClick", "hover", "focus", etc. */
        String event();

        /** Action to dispatch. */
        String action();

        /** Target for action (empty = self). */
        String target() default "";

        /** Condition for when this handler is active. */
        String when() default "";

        /** Container for multiple @On annotations. */
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE, ElementType.METHOD})
        @interface Events {
            On[] value();
        }
    }

    /**
     * Declares a context menu item for right-click interaction.
     *
     * <p>Context menu items are collected from nested static classes annotated with
     * {@code @Scene.ContextMenu}. On right-click, the renderer walks up the scene
     * tree collecting items from each level, evaluates {@code when} conditions,
     * and displays a floating menu.
     *
     * <p>Actions route through the same dispatch path as {@code @Scene.On} actions.
     * For embedded objects (e.g., ClockFace via {@code @Scene.Embed}), the compiler
     * resolves the action to the embedded value's method.
     *
     * <h2>Usage</h2>
     * <pre>{@code
     * @Scene.Container(direction = Direction.STACK)
     * public class ClockFace implements Canonical {
     *
     *     @Scene.ContextMenu(label = "Digital Mode", action = "toggleMode",
     *             when = "value.analog", icon = "🔢")
     *     @Scene.ContextMenu(label = "Analog Mode", action = "toggleMode",
     *             when = "!value.analog", icon = "🕐")
     *     static class Menu {}
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(ContextMenu.Items.class)
    @interface ContextMenu {

        /** Display label for the menu item. */
        String label();

        /** Action to dispatch when selected. */
        String action();

        /** Target for the action (empty = self). Supports binding: "$item.id". */
        String target() default "";

        /** Condition expression — item shown only when this evaluates to true. */
        String when() default "";

        /** Optional icon glyph. */
        String icon() default "";

        /** Group name — items in different groups get visual separators. */
        String group() default "";

        /** Sort order within group. */
        int order() default 0;

        /** Container for multiple @ContextMenu annotations. */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        @interface Items {
            ContextMenu[] value();
        }
    }

    /**
     * Controls visibility based on condition.
     *
     * <p>When condition is false, the element is not rendered at all.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface If {

        /** Condition for visibility. */
        String value();
    }

    /**
     * Container query — conditionally renders based on runtime container size.
     *
     * <p>Also supports {@code "depth"} for rendering different structures
     * based on whether the renderer supports 3D.
     *
     * <p>Condition syntax: {@code <property> <op> <value>}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Query {

        /** Container query condition (e.g., "width >= 30ch", "depth"). */
        String value();
    }

    /**
     * Binds a child element to a property of the value.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Bind {

        /** Property path on the value: "name", "address.city", "tags[0]". */
        String value();
    }

    /**
     * Repeats the annotated element for each item in a collection.
     *
     * <p>The iteration variable is available as "$item" or "$index" in bindings.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Repeat {

        /** Expression for the collection to iterate over. */
        String bind();

        /** Variable name for the current item (default: "item"). */
        String itemVar() default "item";

        /** Variable name for the current index (default: "index"). */
        String indexVar() default "index";

        /** Optional schema class to use for each item. */
        Class<?> as() default SceneSchema.class;

        /**
         * Columns for grid arrangement. Items auto-wrap into rows of N.
         *
         * <p>Literal int or "bind:value.cols" for dynamic binding.
         * Empty string means no grid wrapping (items render inline).
         */
        String columns() default "";
    }

    /**
     * Embeds another SceneSchema at this position.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Embed {

        /** Binding expression for the SceneSchema to embed: "$item", "content". */
        String bind();
    }

    // ===================================================================
    // Layout Positioning
    // ===================================================================

    /**
     * Constraint-based positioning (like iOS Auto Layout).
     *
     * @deprecated Prefer {@link Place}. This remains supported for backward
     * compatibility and is normalized to the same runtime model.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
    @Deprecated
    @interface Constraint {

        /** ID for referencing this element in other constraints. */
        String id() default "";

        /** Top edge position: "0", "10px", "5%". */
        String top() default "";

        /** Bottom edge position. */
        String bottom() default "";

        /** Left edge position. */
        String left() default "";

        /** Right edge position. */
        String right() default "";

        /** Attach top edge to another element: "header.bottom". */
        String topTo() default "";

        /** Attach bottom edge to another element. */
        String bottomTo() default "";

        /** Attach left edge to another element. */
        String leftTo() default "";

        /** Attach right edge to another element. */
        String rightTo() default "";

        /** Width: "auto", "fit", "fill", "100px", "50%", "1fr". */
        String width() default "";

        /** Height: "auto", "fit", "fill", "100px", "50%", "1fr". */
        String height() default "";

        /** Minimum width. */
        String minWidth() default "";

        /** Minimum height. */
        String minHeight() default "";

        /** Maximum width. */
        String maxWidth() default "";

        /** Maximum height. */
        String maxHeight() default "";

        /** Horizontal alignment within bounds: "start", "center", "end", "stretch". */
        String alignX() default "";

        /** Vertical alignment within bounds. */
        String alignY() default "";

        /** Stack order (higher = on top). */
        int zIndex() default 0;

        /** Overflow behavior: "visible" (default), "hidden", "scroll", "auto". */
        String overflow() default "visible";
    }

    /**
     * Fine-grained placement inside a named area or against sibling anchors.
     *
     * <p>{@code @Scene.Place} is the preferred placement primitive for both
     * root and nested nodes. It unifies region-style placement ({@code in})
     * with explicit edge constraints ({@code top}, {@code leftTo}, etc).
     *
     * <p>{@link Constraint} remains supported for backward compatibility and
     * is normalized into the same runtime constraint model.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
    @interface Place {

        /** Optional ID for referencing this element in sibling placements. */
        String id() default "";

        /** Named placement area / zone (region-like semantic target). */
        String in() default "";

        /** Anchor hint inside its placement bounds: "start", "center", "end", "stretch". */
        String anchor() default "";

        /** Top edge position: "0", "10px", "5%". */
        String top() default "";

        /** Bottom edge position. */
        String bottom() default "";

        /** Left edge position. */
        String left() default "";

        /** Right edge position. */
        String right() default "";

        /** Attach top edge to another element: "header.bottom". */
        String topTo() default "";

        /** Attach bottom edge to another element. */
        String bottomTo() default "";

        /** Attach left edge to another element. */
        String leftTo() default "";

        /** Attach right edge to another element. */
        String rightTo() default "";

        /** Width: "auto", "fit", "fill", "100px", "50%", "1fr". */
        String width() default "";

        /** Height: "auto", "fit", "fill", "100px", "50%", "1fr". */
        String height() default "";

        /** Minimum width. */
        String minWidth() default "";

        /** Minimum height. */
        String minHeight() default "";

        /** Maximum width. */
        String maxWidth() default "";

        /** Maximum height. */
        String maxHeight() default "";

        /** Horizontal alignment within bounds: "start", "center", "end", "stretch". */
        String alignX() default "";

        /** Vertical alignment within bounds. */
        String alignY() default "";

        /** Stack order (higher = on top). */
        int zIndex() default 0;

        /** Overflow behavior: "visible" (default), "hidden", "scroll", "auto". */
        String overflow() default "visible";
    }

    /**
     * Flex container configuration.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface FlexContainer {

        /** Layout direction: HORIZONTAL (row) or VERTICAL (column). */
        Direction direction() default Direction.VERTICAL;

        /** Main axis alignment. */
        String justify() default "";

        /** Cross axis alignment. */
        String align() default "";

        /** Gap between children. */
        String gap() default "";

        /** Whether to wrap children. */
        boolean wrap() default false;
    }

    /**
     * Flex child sizing for an element.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
    @interface Flex {

        /** Size mode: "fit", "fill", or explicit "100px". */
        String size() default "";

        /** Flex grow factor (0 = don't grow). */
        int grow() default -1;

        /** Flex shrink factor (0 = don't shrink). */
        int shrink() default -1;

        /** Flex basis: "auto", "100px", "50%". */
        String basis() default "";

        /** Order in the flex layout (lower = first). */
        int order() default 0;

        /** Self alignment override. */
        String alignSelf() default "";
    }

    // ===================================================================
    // 3D Geometry (from @Body)
    // ===================================================================

    /**
     * Declares the 3D body geometry for this element.
     *
     * <p>Body defines the external 3D representation — what you see looking AT an item.
     * Can specify a primitive shape or reference a mesh asset.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Primitive box
     * @Scene.Body(shape = "box", width = "44cm", height = "0", depth = "44cm", color = 0x8B4513)
     *
     * // Mesh-based body
     * @Scene.Body(mesh = "chess/models/w_king.glb", color = 0xFFFFFF)
     * }</pre>
     */
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Body {

        /** SceneSchema class that defines 3D body presentation. */
        Class<?> as() default SceneSchema.class;

        /** ID for referencing this body element. */
        String id() default "";

        /** Primitive shape: "box", "sphere", "cylinder", "plane", "none". */
        String shape() default "";

        /** Width for primitive shapes (e.g., "1m", "50cm"). */
        String width() default "1m";

        /** Height for primitive shapes (e.g., "1m", "50cm"). */
        String height() default "1m";

        /** Depth for primitive shapes (e.g., "1m", "50cm"). */
        String depth() default "1m";

        /** Radius for sphere/cylinder shapes (e.g., "0.5m", "30cm"). */
        String radius() default "0.5m";

        /** ContentID reference or classpath path to mesh asset (overrides shape). */
        String mesh() default "";

        /** Material color as hex int (e.g., 0xFF0000 for red). */
        int color() default 0x808080;

        /** Material opacity (0.0 = transparent, 1.0 = opaque). */
        double opacity() default 1.0;

        /** Material shading mode: "lit", "unlit", "wireframe". */
        String shading() default "lit";

        /** Physical font size for this body's layout context (e.g., "2.2cm").
         *  Establishes the em-to-meters mapping for 3D rendering. When set,
         *  body width/depth are derived from the layout's elevated bounds.
         *  When empty, explicit width/depth are used; if those are also
         *  absent, falls back to DPI-based physical em size. */
        String fontSize() default "";
    }

    /**
     * Declares a 2D Surface rendered on a face of a 3D body.
     *
     * <p>Replaces {@code @Body.Panel}. The face name identifies which side
     * of the parent body's geometry to render on.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Surface on the top face of a chess board body
     * @Scene.Face("top")
     * @Scene.Container(direction = Direction.VERTICAL)
     * static class BoardFace { ... }
     *
     * // High-res display panel on the front
     * @Scene.Face(value = "front", ppm = 1024)
     * @Scene.Container(direction = Direction.VERTICAL)
     * static class Display { ... }
     * }</pre>
     */
    @Target({ElementType.TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Face {

        /** Face name: "top", "front", "back", "bottom", "left", "right". */
        String value() default "top";

        /** Resolution: pixels per meter for the embedded 2D surface. */
        int ppm() default 512;
    }

    /**
     * Transform (position, rotation, scale) for 3D elements.
     *
     * <p>Replaces {@code @Body.Placement}. Position is in meters. Rotation can be
     * euler angles (yaw/pitch/roll) or axis+angle.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Position only
     * @Scene.Transform(x = "1m", y = "0.5m", z = "-2m")
     *
     * // Position + euler rotation
     * @Scene.Transform(x = "1m", yaw = 45, pitch = 10)
     *
     * // Position + axis-angle rotation
     * @Scene.Transform(x = "1m", axisY = 1, angle = 90)
     *
     * // Non-uniform scale
     * @Scene.Transform(scaleX = 2.0, scaleY = 0.5, scaleZ = 2.0)
     * }</pre>
     */
    @Target({ElementType.TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Transform {

        // Position (e.g., "1m", "50cm", "0")
        String x() default "0";
        String y() default "0";
        String z() default "0";

        // Rotation: euler angles in degrees (used if any non-zero)
        double yaw() default 0;
        double pitch() default 0;
        double roll() default 0;

        // Rotation: axis+angle in degrees (used if angle non-zero)
        double axisX() default 0;
        double axisY() default 1;
        double axisZ() default 0;
        double angle() default 0;

        // Scale (per-axis)
        double scaleX() default 1;
        double scaleY() default 1;
        double scaleZ() default 1;
    }

    // ===================================================================
    // Lighting
    // ===================================================================

    /**
     * Light source — unified from {@code @Body.Light} and {@code @Space.Light}.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Warm point light above
     * @Scene.Light(type = "point", x = "0", z = "5m", color = 0xFFE4B5, intensity = 2.0)
     *
     * // Directional sunlight
     * @Scene.Light(type = "directional", dirZ = -1, intensity = 0.8)
     * }</pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Light {

        /** Light type: "directional", "point", "spot". */
        String type() default "directional";

        /** Light color as hex int. */
        int color() default 0xFFFFFF;

        /** Light intensity (1.0 = standard). */
        double intensity() default 1.0;

        // Position (for point and spot lights)
        String x() default "0";
        String y() default "0";
        String z() default "5m";

        // Direction (for directional and spot lights)
        double dirX() default 0;
        double dirY() default 0;
        double dirZ() default -1;
    }

    // ===================================================================
    // Audio
    // ===================================================================

    /**
     * Audio source — unified from {@code @Body.Audio} and {@code @Space.Audio}.
     *
     * <p>Supports both positional 3D audio and stereo playback.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Spatial sound at a position
     * @Scene.Audio(src = "waterfall.wav", x = "5m", z = "-3m", loop = true, autoplay = true)
     *
     * // Non-spatial background music
     * @Scene.Audio(src = "music.ogg", spatial = false, loop = true, autoplay = true)
     * }</pre>
     */
    @Target({ElementType.TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Audio {

        /** Content reference to audio asset (handle name or ContentID). */
        String src() default "";

        /** Position X (e.g., "5m"). */
        String x() default "0";

        /** Position Y. */
        String y() default "0";

        /** Position Z. */
        String z() default "0";

        /** Volume (0.0 = silent, 1.0 = full). */
        double volume() default 1.0;

        /** Playback pitch/speed multiplier. */
        double pitch() default 1.0;

        /** Whether to loop. */
        boolean loop() default false;

        /** Whether to apply 3D spatialization. */
        boolean spatial() default true;

        /** Distance at which volume is 100% (e.g., "1m"). */
        String refDistance() default "1m";

        /** Maximum audible distance (e.g., "50m"). */
        String maxDistance() default "50m";

        /** Whether to start playing immediately. */
        boolean autoplay() default false;
    }

    // ===================================================================
    // Environment (from @Space)
    // ===================================================================

    /**
     * Environment settings for 3D scenes — background, ambient light, fog.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Dark space
     * @Scene.Environment(background = 0x1A1A2E, ambient = 0x404040)
     *
     * // Foggy outdoor scene
     * @Scene.Environment(background = 0x87CEEB, ambient = 0x808080,
     *                    fogNear = "10m", fogFar = "100m", fogColor = 0xC0C0C0)
     * }</pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Environment {

        /** Background color as hex int. */
        int background() default 0x1A1A2E;

        /** Ambient light color as hex int. */
        int ambient() default 0x404040;

        /** Fog start distance (empty = no fog, e.g., "10m"). */
        String fogNear() default "";

        /** Fog end distance (empty = no fog, e.g., "100m"). */
        String fogFar() default "";

        /** Fog color as hex int. */
        int fogColor() default 0x808080;
    }

    // ===================================================================
    // Camera (from @Space)
    // ===================================================================

    /**
     * Camera defaults for viewing a 3D scene.
     *
     * <h2>Examples</h2>
     * <pre>{@code
     * // Standard perspective camera (5m forward, 2m up)
     * @Scene.Camera(y = "5m", z = "2m")
     *
     * // Top-down orthographic
     * @Scene.Camera(projection = "orthographic", z = "10m")
     *
     * // Close-up with narrow FOV
     * @Scene.Camera(fov = 30, y = "2m", z = "1m", targetZ = "0.5m")
     * }</pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Camera {

        /** Projection type: "perspective", "orthographic". */
        String projection() default "perspective";

        /** Field of view in degrees (perspective only). */
        double fov() default 60;

        /** Near clipping plane distance (e.g., "0.1m"). */
        String near() default "0.1m";

        /** Far clipping plane distance (e.g., "1000m"). */
        String far() default "1000m";

        // Initial camera position (Z-up: y=forward, z=up)
        String x() default "0";
        String y() default "5m";
        String z() default "1.5m";

        // Look-at target position
        String targetX() default "0";
        String targetY() default "0";
        String targetZ() default "0";
    }
}
