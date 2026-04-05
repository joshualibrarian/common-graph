package dev.everydaythings.graph.ui.scene;

import java.util.List;

/**
 * Compiled transition specification — the runtime data for animated properties.
 *
 * <p>Built from the resolved transition longhand fields on {@link SceneNode}
 * ({@code transitionProperty}, {@code transitionDuration}, {@code transitionEasing},
 * {@code transitionDelay}). Consumed by {@link AnimationState} to drive interpolation.
 *
 * @param properties Property names this transition applies to (e.g., ["background", "opacity"] or ["all"])
 * @param duration   Duration in seconds
 * @param easing     The timing function
 * @param delay      Delay before animation starts, in seconds
 *
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
     * @param property Property name (e.g., "background", "opacity")
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
}
