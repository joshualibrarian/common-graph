package dev.everydaythings.graph.ui.scene;

import java.lang.annotation.*;

/**
 * Declares that properties on this element should animate when they change.
 *
 * <p>This is the unified animation annotation for both 2D ({@link Surface}) and
 * 3D ({@link Body}) elements. When a property changes due to a state transition,
 * the renderer interpolates from old→new over the specified duration using the
 * specified easing function.
 *
 * <p>This is the Common Graph equivalent of CSS's {@code transition} property,
 * but unified — one annotation works for both surface elements and spatial bodies.
 *
 * <h2>On Surface Elements (2D)</h2>
 * <pre>{@code
 * @Surface.Container(style = "toggle-thumb", size = "1em")
 * @Surface.Shape(type = "circle")
 * @Transition(property = "x", duration = 0.2, easing = "ease-out")
 * static class Thumb {}
 * }</pre>
 *
 * <h2>On Body Elements (3D)</h2>
 * <pre>{@code
 * @Body(mesh = "knife-switch-lever.glb")
 * @Body.Placement(yaw = 0)
 * @Transition(property = "rotation", duration = 0.3, easing = "spring")
 * static class Lever {}
 * }</pre>
 *
 * <h2>Multiple Transitions</h2>
 * <pre>{@code
 * @Transition(property = "x", duration = 0.2, easing = "ease-out")
 * @Transition(property = "opacity", duration = 0.15, easing = "linear")
 * static class FadingSlider {}
 * }</pre>
 *
 * <h2>Easing Specification</h2>
 * <p>The {@link #easing()} string is parsed by {@link Easing#parse(String)}.
 * Named presets: "linear", "ease", "ease-in", "ease-out", "ease-in-out",
 * "spring", "spring-gentle", "spring-snappy", "spring-bouncy".
 * Functions: "cubic-bezier(x1,y1,x2,y2)", "spring(stiffness,damping)",
 * "steps(count)".
 *
 * <h2>Design Philosophy</h2>
 * <p>All motion is state-driven. Annotations declare WHICH properties animate
 * and HOW — the WHEN is determined by state changes ({@code @Surface.State},
 * {@code SceneModel.changed()}, etc.). There are no imperative "play" commands.
 *
 * @see Easing
 * @see TransitionSpec
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Transition.Group.class)
public @interface Transition {

    /**
     * Property name(s) to animate when they change.
     *
     * <p>Property names are renderer-interpreted. Common values:
     * <ul>
     *   <li>{@code "x"}, {@code "y"}, {@code "z"} — position</li>
     *   <li>{@code "rotation"} — rotation (2D: angle, 3D: quaternion)</li>
     *   <li>{@code "scale"}, {@code "scaleX"}, {@code "scaleY"}, {@code "scaleZ"} — scale</li>
     *   <li>{@code "opacity"} — transparency</li>
     *   <li>{@code "color"}, {@code "background"} — colors</li>
     *   <li>{@code "width"}, {@code "height"} — dimensions</li>
     *   <li>{@code "all"} — all animatable properties</li>
     * </ul>
     */
    String[] property() default {"all"};

    /**
     * Duration in seconds.
     *
     * <p>For {@link Easing.Spring} easings, this is ignored — the spring
     * computes its own duration from its physical parameters.
     */
    double duration() default 0.3;

    /**
     * Easing function specification.
     *
     * <p>Parsed by {@link Easing#parse(String)}. Examples:
     * "ease-out", "spring", "cubic-bezier(0.4, 0, 0.2, 1)".
     */
    String easing() default "ease";

    /**
     * Delay before the transition starts, in seconds.
     */
    double delay() default 0;

    /**
     * Container for multiple @Transition annotations on a single element.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Group {
        Transition[] value();
    }
}
