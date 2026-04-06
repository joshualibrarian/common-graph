package dev.everydaythings.graph.ui.scene;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Compiled transition specification — the runtime data for animated properties.
 *
 * <p>Built from the resolved transition longhand fields on {@link SceneNode}
 * ({@code transitionProperty}, {@code transitionDuration}, {@code transitionEasing},
 * {@code transitionDelay}). Consumed by {@link AnimationState} to drive interpolation.
 *
 * @see Easing
 * @see AnimationState
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class TransitionSpec {

    /** No transition — instant property changes. */
    public static final TransitionSpec NONE = new TransitionSpec()
            .properties(List.of())
            .duration(0)
            .easing(Easing.LINEAR)
            .delay(0);

    /** Property names this transition applies to (e.g., ["background", "opacity"] or ["all"]). */
    private List<String> properties;

    /** Duration in seconds. */
    private double duration;

    /** The timing function. */
    private Easing easing;

    /** Delay before animation starts, in seconds. */
    private double delay;

    public TransitionSpec() {}

    public TransitionSpec properties(List<String> properties) {
        this.properties = properties;
        return this;
    }

    public TransitionSpec duration(double duration) {
        this.duration = duration;
        return this;
    }

    public TransitionSpec easing(Easing easing) {
        this.easing = easing;
        return this;
    }

    public TransitionSpec delay(double delay) {
        this.delay = delay;
        return this;
    }

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
