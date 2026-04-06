package dev.everydaythings.graph.ui.scene;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives keyframe animations for scene nodes.
 *
 * <p>Each frame, computes the current position in the animation timeline,
 * finds the two surrounding keyframes, interpolates between them, and
 * provides the current value for each animated property.
 *
 * <p>Supports looping, alternating direction, delay, fill modes, and
 * pause/resume via animationPlayState.
 *
 * @see SceneNode.Keyframe
 * @see Easing
 */
public class KeyframeAnimator {

    private final Map<String, ElementAnimation> elements = new HashMap<>();

    /**
     * Register or update a keyframe animation for a node.
     * Call each paint cycle with the node's current animation fields.
     */
    public void register(String elementId, List<SceneNode.Keyframe> keyframes,
                          float duration, String iterationCount, String direction,
                          String easingSpec, float delay, String fillMode, String playState) {
        if (keyframes == null || keyframes.isEmpty() || duration <= 0) {
            elements.remove(elementId);
            return;
        }

        ElementAnimation anim = elements.get(elementId);
        if (anim == null) {
            anim = new ElementAnimation();
            elements.put(elementId, anim);
        }
        anim.keyframes = keyframes;
        anim.duration = duration;
        anim.iterations = "infinite".equals(iterationCount) ? -1 : parseIterations(iterationCount);
        anim.alternate = "alternate".equals(direction) || "alternate-reverse".equals(direction);
        anim.reverse = "reverse".equals(direction) || "alternate-reverse".equals(direction);
        anim.easing = easingSpec != null ? Easing.parse(easingSpec) : Easing.LINEAR;
        anim.delay = delay;
        anim.fillMode = fillMode != null ? fillMode : "none";
        anim.paused = "paused".equals(playState);
    }

    /**
     * Advance all active animations by the given time delta.
     *
     * @return true if any animations are still running
     */
    public boolean update(double deltaTime) {
        boolean anyActive = false;
        for (ElementAnimation anim : elements.values()) {
            if (anim.paused) { anyActive = true; continue; }
            anim.elapsed += deltaTime;
            if (anim.isActive()) anyActive = true;
        }
        return anyActive;
    }

    /**
     * Get the current animated value of a property for an element.
     *
     * @return the interpolated value, or the fallback if no animation affects this property
     */
    public double getValue(String elementId, String property, double fallback) {
        ElementAnimation anim = elements.get(elementId);
        if (anim == null || anim.keyframes == null) return fallback;

        double t = anim.currentProgress();
        if (Double.isNaN(t)) {
            // Animation not yet started or finished — check fill mode
            if (anim.elapsed < anim.delay) {
                if ("backwards".equals(anim.fillMode) || "both".equals(anim.fillMode)) {
                    t = 0;
                } else {
                    return fallback;
                }
            } else {
                if ("forwards".equals(anim.fillMode) || "both".equals(anim.fillMode)) {
                    t = 1;
                } else {
                    return fallback;
                }
            }
        }

        return interpolateKeyframes(anim.keyframes, property, t, anim.easing, fallback);
    }

    /**
     * Whether any animations are currently running.
     */
    public boolean isAnimating() {
        for (ElementAnimation anim : elements.values()) {
            if (anim.isActive()) return true;
        }
        return false;
    }

    public void clear() {
        elements.clear();
    }

    // =================================================================================
    // Interpolation
    // =================================================================================

    private static double interpolateKeyframes(List<SceneNode.Keyframe> keyframes,
                                                String property, double t,
                                                Easing easing, double fallback) {
        // Find the two keyframes surrounding t (as percentage 0-100)
        float pct = (float) (t * 100);

        SceneNode.Keyframe before = null;
        SceneNode.Keyframe after = null;

        for (SceneNode.Keyframe kf : keyframes) {
            if (kf.at() <= pct) {
                before = kf;
            }
            if (kf.at() >= pct && after == null) {
                after = kf;
            }
        }

        if (before == null && after == null) return fallback;
        if (before == null) before = after;
        if (after == null) after = before;

        String beforeVal = before.properties().get(property);
        String afterVal = after.properties().get(property);

        if (beforeVal == null && afterVal == null) return fallback;
        if (beforeVal == null) beforeVal = afterVal;
        if (afterVal == null) afterVal = beforeVal;

        double fromVal = parseNumericValue(beforeVal, fallback);
        double toVal = parseNumericValue(afterVal, fallback);

        if (before == after || before.at() == after.at()) return toVal;

        // Local t within this keyframe segment
        double segmentT = (pct - before.at()) / (after.at() - before.at());
        double easedT = easing.apply(Math.max(0, Math.min(1, segmentT)));

        return fromVal + (toVal - fromVal) * easedT;
    }

    private static double parseNumericValue(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Could be a color hex — parse as ARGB int
            if (value.startsWith("#")) {
                try {
                    return 0xFF000000L | Long.parseLong(value.substring(1), 16);
                } catch (NumberFormatException e2) {
                    return fallback;
                }
            }
            return fallback;
        }
    }

    private static int parseIterations(String spec) {
        if (spec == null || spec.isEmpty()) return 1;
        try { return Integer.parseInt(spec); } catch (NumberFormatException e) { return 1; }
    }

    // =================================================================================
    // Internal State
    // =================================================================================

    private static class ElementAnimation {
        List<SceneNode.Keyframe> keyframes;
        double duration;
        int iterations = 1;  // -1 = infinite
        boolean alternate;
        boolean reverse;
        Easing easing = Easing.LINEAR;
        double delay;
        String fillMode = "none";
        boolean paused;
        double elapsed;

        boolean isActive() {
            if (paused) return true;  // paused but still registered
            double animTime = elapsed - delay;
            if (animTime < 0) return true;  // still in delay
            if (iterations < 0) return true;  // infinite
            return animTime < duration * iterations;
        }

        /**
         * Current progress through the animation as 0-1.
         * Returns NaN if the animation is not currently in its active window.
         */
        double currentProgress() {
            double animTime = elapsed - delay;
            if (animTime < 0) return Double.NaN;

            double totalDuration = iterations < 0 ? Double.MAX_VALUE : duration * iterations;
            if (animTime >= totalDuration && iterations >= 0) return Double.NaN;

            // Which iteration are we in?
            int currentIteration = (int) (animTime / duration);
            double withinIteration = (animTime % duration) / duration;

            // Clamp for final iteration
            if (iterations >= 0 && currentIteration >= iterations) {
                withinIteration = 1.0;
                currentIteration = iterations - 1;
            }

            // Apply direction
            boolean forward;
            if (alternate) {
                forward = (currentIteration % 2 == 0) != reverse;
            } else {
                forward = !reverse;
            }

            return forward ? withinIteration : 1.0 - withinIteration;
        }
    }
}
