package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;

/**
 * A populated Surface tree - the result of applying a Surface to Data.
 *
 * <p>View is the third stage in the rendering pipeline:
 * <pre>
 * DATA + SURFACE → VIEW → RENDER
 * </pre>
 *
 * <p>A View is CBOR-serializable, meaning it can be:
 * <ul>
 *   <li>Sent over the wire to a remote renderer</li>
 *   <li>Cached for efficient re-rendering</li>
 *   <li>Diffed for incremental updates</li>
 *   <li>Inspected/debugged as data</li>
 * </ul>
 *
 * <p>The View contains a tree of {@link SurfaceSchema} objects,
 * each populated with actual data values. Platform-specific renderers
 * traverse this tree to produce their output.
 *
 * <h2>Example</h2>
 * <pre>
 * // Create a view programmatically
 * View view = View.of(
 *     ContainerSurface.vertical()
 *         .add(TextSurface.of("My Project").style("heading"))
 *         .add(TextSurface.of("## Description\n...").format("markdown"))
 *         .add(ChipsSurface.of(List.of(
 *             Chip.of("urgent", "🔴"),
 *             Chip.of("feature", "✨")
 *         )))
 * );
 *
 * // Or compile from data + surface
 * View view = SceneCompiler.compile(myItem);
 * </pre>
 *
 * @see SurfaceSchema
 * @see SceneCompiler
 */
public class View implements Canonical {

    /**
     * The root surface of the view tree.
     */
    @Canon(order = 0)
    private SurfaceSchema<?> root;

    /**
     * Optional title for the view (e.g., window title).
     */
    @Canon(order = 1)
    private String title;

    /**
     * Optional icon for the view.
     */
    @Canon(order = 2)
    private String icon;

    /**
     * The mode this view was rendered in.
     */
    @Canon(order = 3)
    private SceneMode mode = SceneMode.FULL;

    public View() {}

    public View(SurfaceSchema<?> root) {
        this.root = root;
    }

    /**
     * Create a View with the given root surface.
     */
    public static View of(SurfaceSchema<?> root) {
        return new View(root);
    }

    /**
     * Create an empty box view.
     */
    public static View empty() {
        return new View(ContainerSurface.vertical());
    }

    public View root(SurfaceSchema<?> root) {
        this.root = root;
        return this;
    }

    public View title(String title) {
        this.title = title;
        return this;
    }

    public View icon(String icon) {
        this.icon = icon;
        return this;
    }

    public View mode(SceneMode mode) {
        this.mode = mode;
        return this;
    }

    public SurfaceSchema<?> root() {
        return root;
    }

    public String title() {
        return title;
    }

    public String icon() {
        return icon;
    }

    public SceneMode mode() {
        return mode;
    }

    /**
     * Check if this view has content.
     */
    public boolean isEmpty() {
        return root == null;
    }
}
