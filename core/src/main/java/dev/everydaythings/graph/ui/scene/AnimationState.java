package dev.everydaythings.graph.ui.scene;

import java.util.*;

/**
 * Tracks animated property values per element and interpolates over time.
 *
 * <p>AnimationState is the animation runtime shared between 2D and 3D renderers.
 * It maintains the "before" state of animated properties and interpolates toward
 * the "after" state using the element's {@link TransitionSpec}.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Renderer registers transition specs for each element: {@link #registerTransitions}</li>
 *   <li>Renderer sets current target values: {@link #setTarget}</li>
 *   <li>If target changed and element has matching transition → animation starts</li>
 *   <li>Each frame: {@link #update(double)} advances active transitions</li>
 *   <li>Renderer queries interpolated values: {@link #getValue}</li>
 *   <li>When all transitions complete: {@link #isAnimating()} returns false</li>
 * </ol>
 *
 * <h2>Interruptibility</h2>
 * <p>When a new target arrives while a transition is active, the transition
 * restarts from the current interpolated position toward the new target.
 * This produces smooth redirections — the element doesn't jump.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AnimationState animation = new AnimationState();
 *
 * // Register (once, from compiled annotations)
 * animation.registerTransitions("thumb", transitionSpecs);
 *
 * // Each render pass
 * animation.setTarget("thumb", "x", newX);
 * double currentX = animation.getValue("thumb", "x", newX);
 *
 * // Each frame
 * boolean needsRedraw = animation.update(deltaTime);
 * }</pre>
 *
 * @see TransitionSpec
 * @see Easing
 */
public class AnimationState {

    private final Map<String, ElementState> elements = new HashMap<>();

    // ==================== Registration ====================

    /**
     * Register transition specs for an element.
     *
     * <p>Call once per element when transition specs are compiled (or when
     * the element first appears). Subsequent calls replace previous specs.
     *
     * @param elementId Stable element identifier (from {@code id()} calls)
     * @param specs     Transition specs for this element's properties
     */
    public void registerTransitions(String elementId, List<TransitionSpec> specs) {
        if (specs == null || specs.isEmpty()) return;
        elements.computeIfAbsent(elementId, id -> new ElementState()).specs = specs;
    }

    // ==================== Target Setting ====================

    /**
     * Set the target value for a property on an element.
     *
     * <p>If the value changed and a matching transition exists, an animation
     * starts from the current (possibly interpolated) value toward the new target.
     *
     * @param elementId Element identifier
     * @param property  Property name (e.g., "x", "rotation", "opacity")
     * @param target    The new target value
     */
    public void setTarget(String elementId, String property, double target) {
        ElementState element = elements.get(elementId);
        if (element == null) return;

        PropertyState prop = element.properties.get(property);
        if (prop != null && prop.target == target) {
            return; // No change
        }

        // Find matching transition spec
        TransitionSpec spec = element.findSpec(property);
        if (spec == null) {
            // No transition for this property — update instantly
            if (prop != null) {
                prop.current = target;
                prop.target = target;
                prop.active = false;
            }
            return;
        }

        if (prop == null) {
            prop = new PropertyState();
            prop.current = target; // First time — no animation, start at target
            prop.target = target;
            element.properties.put(property, prop);
            return;
        }

        // Start animation from current interpolated position
        prop.from = prop.current;
        prop.target = target;
        prop.elapsed = 0;
        prop.delay = spec.delay();
        prop.duration = spec.effectiveDuration();
        prop.easing = spec.easing();
        prop.active = true;
    }

    // ==================== Value Retrieval ====================

    /**
     * Get the current (possibly interpolated) value of a property.
     *
     * <p>If no animation is active, returns the fallback value.
     *
     * @param elementId Element identifier
     * @param property  Property name
     * @param fallback  Value to return if no animation state exists
     * @return The current interpolated value, or fallback if not tracked
     */
    public double getValue(String elementId, String property, double fallback) {
        ElementState element = elements.get(elementId);
        if (element == null) return fallback;

        PropertyState prop = element.properties.get(property);
        if (prop == null) return fallback;

        return prop.current;
    }

    // ==================== Frame Update ====================

    /**
     * Advance all active transitions by the given time delta.
     *
     * @param deltaTime Time elapsed since last update, in seconds
     * @return true if any transitions are still active (caller should request another frame)
     */
    public boolean update(double deltaTime) {
        boolean anyActive = false;

        for (ElementState element : elements.values()) {
            for (PropertyState prop : element.properties.values()) {
                if (!prop.active) continue;

                prop.elapsed += deltaTime;

                if (prop.elapsed < prop.delay) {
                    // Still in delay period
                    anyActive = true;
                    continue;
                }

                double animTime = prop.elapsed - prop.delay;

                if (prop.duration <= 0) {
                    // Instant (shouldn't happen if we got here, but safety)
                    prop.current = prop.target;
                    prop.active = false;
                    continue;
                }

                double t = animTime / prop.duration;

                if (t >= 1.0) {
                    // Animation complete
                    prop.current = prop.target;
                    prop.active = false;
                } else {
                    // Interpolate
                    double progress = prop.easing.apply(t);
                    prop.current = prop.from + (prop.target - prop.from) * progress;
                    anyActive = true;
                }
            }
        }

        return anyActive;
    }

    // ==================== Query ====================

    /**
     * Whether any transitions are currently running.
     */
    public boolean isAnimating() {
        for (ElementState element : elements.values()) {
            for (PropertyState prop : element.properties.values()) {
                if (prop.active) return true;
            }
        }
        return false;
    }

    /**
     * Number of currently active transitions across all elements.
     */
    public int activeTransitionCount() {
        int count = 0;
        for (ElementState element : elements.values()) {
            for (PropertyState prop : element.properties.values()) {
                if (prop.active) count++;
            }
        }
        return count;
    }

    /**
     * Clear all state. Call when the scene is rebuilt from scratch.
     */
    public void clear() {
        elements.clear();
    }

    /**
     * Remove state for a specific element.
     */
    public void remove(String elementId) {
        elements.remove(elementId);
    }

    // ==================== Internal State ====================

    private static class ElementState {
        List<TransitionSpec> specs = List.of();
        final Map<String, PropertyState> properties = new HashMap<>();

        TransitionSpec findSpec(String property) {
            for (TransitionSpec spec : specs) {
                if (spec.covers(property)) {
                    return spec;
                }
            }
            return null;
        }
    }

    private static class PropertyState {
        double from;
        double current;
        double target;
        double elapsed;
        double delay;
        double duration;
        Easing easing = Easing.LINEAR;
        boolean active;
    }
}
