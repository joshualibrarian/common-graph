package dev.everydaythings.graph.ui.scene;

import java.lang.reflect.AnnotatedElement;
import java.util.List;

/**
 * Compiled transition specification — the runtime data for an animated property.
 *
 * <p>TransitionSpec is the compiled form of {@link Transition} annotations,
 * analogous to how {@link CompiledBody} is the compiled form of {@link Body}.
 * Compiled once per class, cached, and consumed by renderers every frame.
 *
 * <p>TransitionSpec is intentionally a simple data record. The animation
 * runtime ({@link AnimationState}) handles interpolation using these specs.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Compile from annotation
 * TransitionSpec spec = TransitionSpec.from(annotation);
 *
 * // Check if a property is covered
 * if (spec.covers("x")) {
 *     // This element's x property should animate when it changes
 * }
 * }</pre>
 *
 * @param properties Property names this transition applies to (e.g., ["x", "opacity"] or ["all"])
 * @param duration   Duration in seconds (ignored for Spring easing — spring self-times)
 * @param easing     The timing function
 * @param delay      Delay before animation starts, in seconds
 *
 * @see Transition
 * @see Easing
 * @see AnimationState
 */
public record TransitionSpec(
        List<String> properties,
        double duration,
        Easing easing,
        double delay
) {
    /** No transition — instant property changes. */
    public static final TransitionSpec NONE = new TransitionSpec(List.of(), 0, Easing.LINEAR, 0);

    /**
     * Whether this spec covers the given property name.
     *
     * @param property Property name (e.g., "x", "rotation", "opacity")
     * @return true if this transition applies to the property
     */
    public boolean covers(String property) {
        return properties.contains("all") || properties.contains(property);
    }

    /**
     * Effective duration in seconds.
     *
     * <p>For Spring easings, returns the spring's settling duration.
     * For all others, returns the declared duration.
     */
    public double effectiveDuration() {
        if (easing instanceof Easing.Spring spring) {
            return spring.settlingDuration();
        }
        return duration;
    }

    /**
     * Compile a TransitionSpec from a {@link Transition} annotation.
     */
    public static TransitionSpec from(Transition annotation) {
        return new TransitionSpec(
                List.of(annotation.property()),
                annotation.duration(),
                Easing.parse(annotation.easing()),
                annotation.delay()
        );
    }

    /**
     * Compile all TransitionSpecs from any annotated element's @Transition annotations.
     *
     * @param element The annotated element (class, method, field)
     * @return List of compiled specs (empty if no @Transition annotations)
     */
    public static List<TransitionSpec> fromElement(AnnotatedElement element) {
        Transition[] annotations = element.getAnnotationsByType(Transition.class);
        if (annotations.length == 0) {
            return List.of();
        }

        TransitionSpec[] specs = new TransitionSpec[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            specs[i] = from(annotations[i]);
        }
        return List.of(specs);
    }

    /**
     * Compile all TransitionSpecs from a class's @Transition annotations.
     *
     * @param clazz The annotated class
     * @return List of compiled specs (empty if no @Transition annotations)
     */
    public static List<TransitionSpec> fromClass(Class<?> clazz) {
        return fromElement(clazz);
    }
}
