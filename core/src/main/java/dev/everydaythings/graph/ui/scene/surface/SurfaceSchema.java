package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.ui.scene.SceneEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all Surface patterns.
 *
 * <p>A SurfaceSchema defines how a piece of data is transformed into
 * a visual representation. This is the "formula" in the pipeline:
 *
 * <pre>
 * DATA + SURFACE → VIEW → RENDER
 * </pre>
 *
 * <p>All SurfaceSchema subclasses are CBOR-serializable via {@link Canonical}.
 * They can be:
 * <ul>
 *   <li>Derived from {@link Surface} annotations at compile time</li>
 *   <li>Stored as components on Items (runtime override)</li>
 *   <li>Sent over the wire to remote renderers</li>
 * </ul>
 *
 * <h2>Surface Types</h2>
 *
 * <p><b>Procedural surfaces</b> - extend SurfaceSchema and override render():
 * <ul>
 *   <li>{@code TextSurface} - Text content (plain or formatted)</li>
 *   <li>{@code ImageSurface} - Binary images</li>
 *   <li>{@code ContainerSurface} - Container with children</li>
 *   <li>{@code TreeSurface} - Expandable hierarchy</li>
 * </ul>
 *
 * <p><b>Declarative surfaces</b> - use {@link Surface.Container}, {@link Surface.Text},
 * {@link Surface.Shape} annotations to define visual structure:
 * <pre>{@code
 * public class ToggleSurface extends SurfaceSchema<Boolean> {
 *
 *     @Surface.Container(style = "toggle", gap = "0.5em")
 *     @Surface.On(event = "click", action = "toggle")
 *     static class Frame {
 *
 *         @Surface.Container(style = "thumb", size = "1em")
 *         @Surface.Shape(type = "circle")
 *         @Surface.State(style = "on", when = "value")
 *         static class Thumb { }
 *     }
 * }
 * }</pre>
 *
 * @param <T> The type of value this surface displays/edits. Use {@code Void}
 *            for surfaces that don't bind to a specific value type.
 *
 * @see Surface
 * @see Surface.Container
 * @see Surface.Text
 * @see Surface.Shape
 * @see View
 */
@SuppressWarnings("unchecked")
public abstract class SurfaceSchema<T> implements Canonical {

    /**
     * The value being displayed/edited by this surface.
     *
     * <p>For declarative surfaces, this is the data that @State conditions
     * and @Bind expressions reference.
     */
    @Canon(order = -1)
    protected T value;

    /**
     * Optional ID for referencing this surface element.
     */
    @Canon(order = 0)
    protected String id;

    /**
     * Style classes applied to this surface.
     */
    @Canon(order = 1)
    protected List<String> style;

    /**
     * Whether this surface is visible.
     */
    @Canon(order = 2)
    protected boolean visible = true;

    /**
     * Whether this surface is editable.
     */
    @Canon(order = 3)
    protected boolean editable = false;

    /**
     * Whether this element participates in tab order.
     *
     * <p>Default is null (unset), meaning the element uses default behavior:
     * <ul>
     *   <li>Interactive elements (buttons, inputs) are tabbable</li>
     *   <li>Static elements (text, images) are not tabbable</li>
     * </ul>
     *
     * <p>Set explicitly to override:
     * <ul>
     *   <li>true - force tabbable (e.g., clickable text)</li>
     *   <li>false - skip in tab order (e.g., disabled button)</li>
     * </ul>
     */
    @Canon(order = 4)
    protected Boolean tabbable;

    /**
     * Label text for this surface element.
     */
    @Canon(order = 5)
    protected String label;

    /**
     * ID of another surface element that labels this one.
     */
    @Canon(order = 5)
    protected String labeledBy;

    /**
     * Size constraint (e.g., "auto", "1fr", "300px").
     */
    @Canon(order = 6)
    protected String size;

    /**
     * Margin - space outside the element (e.g., "4px", "1em", "8px 16px").
     */
    @Canon(order = 7)
    protected String margin;

    /**
     * Padding - space inside the element around its content (e.g., "4px", "1em").
     */
    @Canon(order = 8)
    protected String padding;

    /**
     * Event handlers for this element.
     */
    @Canon(order = 9)
    protected List<SceneEvent> events;

    /**
     * Horizontal scale factor (default 1.0 = no scaling).
     */
    @Canon(order = 10)
    protected double scaleX = 1.0;

    /**
     * Vertical scale factor (default 1.0 = no scaling).
     */
    @Canon(order = 11)
    protected double scaleY = 1.0;

    /**
     * Perpendicular displacement from the body face in meters.
     *
     * <p>In 2D: ignored. In 3D: when this surface is rendered on a body face,
     * the element is displaced perpendicular to the face by this amount.
     * For example, chess squares with elevation 0.1 become 0.1m thick slabs.
     */
    @Canon(order = 12)
    protected double elevation = 0.0;

    /**
     * Whether this element has 3D elevation (set automatically by elevation setter).
     */
    @Canon(order = 13)
    protected boolean elevated = false;

    /**
     * Preferred font family for text in this surface subtree.
     */
    @Canon(order = 14)
    protected String fontFamily;

    /**
     * Preferred font size for text in this surface subtree.
     */
    @Canon(order = 15)
    protected String fontSize;

    /**
     * Border specification for this element.
     *
     * <p>Like CSS, any element can have a border — not just containers.
     */
    @Canon(order = -2)
    protected BoxBorder boxBorder;

    /**
     * Background color/style for this element.
     */
    @Canon(order = -2)
    protected String boxBackground;

    /**
     * Binding expression that controls visibility dynamically.
     *
     * <p>When set, the expression is evaluated against the root value at render time.
     * If the result is truthy (non-null, non-empty, non-false), the element is visible.
     * If falsy, the element is hidden.
     *
     * <p>Examples: {@code "value.state.pieces.a1"} (visible if square occupied),
     * {@code "value.isGameOver"} (visible when game ends).
     */
    @Canon(order = -3)
    protected String visibleBind;

    /**
     * Override class for structure compilation.
     *
     * <p>When set, {@code renderFromStructure()} compiles from this class instead
     * of {@code this.getClass()}. This enables model classes (plain records/POJOs)
     * to carry surface annotations directly — a wrapper SurfaceSchema sets
     * {@code structureClass} to the model class and the compiler reads annotations
     * from there.
     */
    private transient Class<?> structureClass;

    // ===== Fluent Setters (return self type) =====

    @SuppressWarnings("unchecked")
    protected <S extends SurfaceSchema<?>> S self() {
        return (S) this;
    }

    public <S extends SurfaceSchema<?>> S id(String id) {
        this.id = id;
        return self();
    }

    public <S extends SurfaceSchema<?>> S style(String... styles) {
        this.style = List.of(styles);
        return self();
    }

    public <S extends SurfaceSchema<?>> S style(List<String> styles) {
        this.style = styles;
        return self();
    }

    public <S extends SurfaceSchema<?>> S visible(boolean visible) {
        this.visible = visible;
        return self();
    }

    /**
     * Set a binding expression that controls visibility dynamically.
     *
     * <p>At render time, the expression is resolved against the root value.
     * Truthy result = visible, falsy = hidden. Overrides the static {@code visible} flag.
     *
     * @param bindExpr Property path expression (e.g., "value.state.pieces.a1")
     */
    public <S extends SurfaceSchema<?>> S visibleWhen(String bindExpr) {
        this.visibleBind = bindExpr;
        return self();
    }

    public <S extends SurfaceSchema<?>> S editable(boolean editable) {
        this.editable = editable;
        return self();
    }

    public <S extends SurfaceSchema<?>> S label(String label) {
        this.label = label;
        return self();
    }

    public <S extends SurfaceSchema<?>> S labeledBy(String labeledBy) {
        this.labeledBy = labeledBy;
        return self();
    }

    public <S extends SurfaceSchema<?>> S size(String size) {
        this.size = size;
        return self();
    }

    public <S extends SurfaceSchema<?>> S margin(String margin) {
        this.margin = margin;
        return self();
    }

    public <S extends SurfaceSchema<?>> S tabbable(Boolean tabbable) {
        this.tabbable = tabbable;
        return self();
    }

    public <S extends SurfaceSchema<?>> S padding(String padding) {
        this.padding = padding;
        return self();
    }

    public <S extends SurfaceSchema<?>> S events(List<SceneEvent> events) {
        this.events = events;
        return self();
    }

    public <S extends SurfaceSchema<?>> S events(SceneEvent... events) {
        this.events = List.of(events);
        return self();
    }

    public <S extends SurfaceSchema<?>> S fontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
        return self();
    }

    public <S extends SurfaceSchema<?>> S fontSize(String fontSize) {
        this.fontSize = fontSize;
        return self();
    }

    public <S extends SurfaceSchema<?>> S on(String eventType, String action) {
        return on(eventType, action, "");
    }

    public <S extends SurfaceSchema<?>> S on(String eventType, String action, String target) {
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        this.events.add(SceneEvent.of(eventType, action, target));
        return self();
    }

    public <S extends SurfaceSchema<?>> S onClick(String action) {
        return on("click", action);
    }

    public <S extends SurfaceSchema<?>> S onClick(String action, String target) {
        return on("click", action, target);
    }

    public <S extends SurfaceSchema<?>> S border(BoxBorder border) {
        this.boxBorder = border;
        return self();
    }

    public <S extends SurfaceSchema<?>> S border(String shorthand) {
        this.boxBorder = BoxBorder.parse(shorthand);
        return self();
    }

    public <S extends SurfaceSchema<?>> S background(String background) {
        this.boxBackground = background;
        return self();
    }

    public <S extends SurfaceSchema<?>> S scaleX(double scaleX) {
        this.scaleX = scaleX;
        return self();
    }

    public <S extends SurfaceSchema<?>> S scaleY(double scaleY) {
        this.scaleY = scaleY;
        return self();
    }

    /**
     * Set uniform scale (both axes).
     */
    public <S extends SurfaceSchema<?>> S scale(double scale) {
        this.scaleX = scale;
        this.scaleY = scale;
        return self();
    }

    /**
     * Set 3D elevation (perpendicular displacement from body face).
     *
     * <p>In 2D: ignored. In 3D: the element becomes a slab with this height.
     *
     * @param elevation Height in meters
     */
    public <S extends SurfaceSchema<?>> S elevation(double elevation) {
        this.elevation = elevation;
        this.elevated = elevation != 0.0;
        return self();
    }

    public BoxBorder boxBorder() {
        return boxBorder;
    }

    public String boxBackground() {
        return boxBackground;
    }

    public String visibleBind() {
        return visibleBind;
    }

    public Class<?> structureClass() {
        return structureClass;
    }

    @SuppressWarnings("unchecked")
    public <S extends SurfaceSchema<?>> S structureClass(Class<?> clazz) {
        this.structureClass = clazz;
        return (S) this;
    }

    // ===== Value Accessors =====

    public T value() {
        return value;
    }

    public <S extends SurfaceSchema<T>> S value(T value) {
        this.value = value;
        return (S) this;
    }

    // ===== Getters =====

    public String id() {
        return id;
    }

    public List<String> style() {
        return style != null ? style : List.of();
    }

    public boolean visible() {
        return visible;
    }

    public boolean editable() {
        return editable;
    }

    public String label() {
        return label;
    }

    public String labeledBy() {
        return labeledBy;
    }

    public String size() {
        return size;
    }

    public String margin() {
        return margin;
    }

    public String padding() {
        return padding;
    }

    public Boolean tabbable() {
        return tabbable;
    }

    public List<SceneEvent> events() {
        return events != null ? events : List.of();
    }

    public double scaleX() {
        return scaleX;
    }

    public double scaleY() {
        return scaleY;
    }

    public double elevation() {
        return elevation;
    }

    public boolean isElevated() {
        return elevated;
    }

    public String fontFamily() {
        return fontFamily;
    }

    public String fontSize() {
        return fontSize;
    }

    // ===== Rendering =====

    /**
     * Render this surface to the given output.
     *
     * <p>Each surface describes itself by emitting render instructions.
     * The platform-specific renderer implements RenderOutput and builds
     * its native output from these instructions.
     *
     * <p>Default implementation checks for structural annotations
     * ({@link Surface.Container}, {@link Surface.Text}, etc.) on
     * nested static classes. If found, uses {@link StructureCompiler} to render.
     * Otherwise, subclasses must override this method.
     *
     * @param out The render output to emit instructions to
     */
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        Class<?> compileFrom = structureClass != null ? structureClass : this.getClass();

        // Try @Scene.* annotations first (unified DSL)
        if (SceneCompiler.canCompile(compileFrom)) {
            SceneCompiler.render(this, out);
            return;
        }

        // Subclass must override if no structural annotations
        throw new UnsupportedOperationException(
            getClass().getName() + " has no structural annotations and doesn't override render()");
    }

    /**
     * Emit common properties (id, editable, events, container setup) before rendering.
     *
     * <p>For schemas with a {@link Surface.Container} annotation, the compiled container
     * properties (gap, align) are emitted here so they apply to the next {@code beginBox()}.
     * This enables procedural surfaces (like TreeSurface) to declare layout via annotation
     * without reading annotations in their render() method.
     */
    public void emitCommonProperties(SurfaceRenderer out) {
        if (id != null && !id.isEmpty()) {
            out.id(id);
        }
        if (editable) {
            out.editable(true);
        }
        if (events != null) {
            for (SceneEvent event : events) {
                out.event(event.on(), event.action(), event.target());
            }
        }
        if (fontFamily != null && !fontFamily.isEmpty()) {
            out.textFontFamily(fontFamily);
        }
        if (fontSize != null && !fontSize.isEmpty()) {
            out.textFontSize(fontSize);
        }

        // Emit class-level container properties from compiled annotation cache
        ViewNode compiled = SceneCompiler.getCompiled(getClass());
        if (compiled != null && compiled.type == ViewNode.NodeType.CONTAINER) {
            if (compiled.gap != null && !compiled.gap.isEmpty()) {
                out.gap(compiled.gap);
            }
            if (compiled.fontFamily != null && !compiled.fontFamily.isEmpty()) {
                out.textFontFamily(compiled.fontFamily);
            }
            if (compiled.textFontSize != null && !compiled.textFontSize.isEmpty()) {
                out.textFontSize(compiled.textFontSize);
            }
        }

        // Emit elevation from procedural field (set via elevation() fluent setter)
        if (elevated && elevation != 0.0) {
            out.elevation(elevation, true);
        }
    }
}
