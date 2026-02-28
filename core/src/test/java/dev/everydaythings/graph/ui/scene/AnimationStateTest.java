package dev.everydaythings.graph.ui.scene;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AnimationState} — the animation runtime tracker.
 */
class AnimationStateTest {

    private AnimationState state;

    @BeforeEach
    void setup() {
        state = new AnimationState();
    }

    // ==================== Registration ====================

    @Test
    void unregisteredElement_returnsFallback() {
        assertThat(state.getValue("unknown", "x", 42.0)).isEqualTo(42.0);
    }

    @Test
    void registerTransitions_nullOrEmpty_ignored() {
        state.registerTransitions("elem", null);
        state.registerTransitions("elem", List.of());
        assertThat(state.getValue("elem", "x", 10.0)).isEqualTo(10.0);
    }

    // ==================== Target Setting ====================

    @Test
    void firstSetTarget_noAnimation() {
        // First time setting a target — should jump to it, not animate
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.3, Easing.EASE_OUT, 0)
        ));

        state.setTarget("thumb", "x", 100);
        assertThat(state.getValue("thumb", "x", 0)).isEqualTo(100);
        assertThat(state.isAnimating()).isFalse();
    }

    @Test
    void sameTarget_noAnimation() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.3, Easing.EASE_OUT, 0)
        ));

        state.setTarget("thumb", "x", 100);
        state.setTarget("thumb", "x", 100); // same value
        assertThat(state.isAnimating()).isFalse();
    }

    @Test
    void changedTarget_startsAnimation() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.3, Easing.LINEAR, 0)
        ));

        state.setTarget("thumb", "x", 0);   // first set
        state.setTarget("thumb", "x", 100);  // change → starts animation

        assertThat(state.isAnimating()).isTrue();
        assertThat(state.activeTransitionCount()).isEqualTo(1);
    }

    @Test
    void noMatchingTransition_jumpInstantly() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.3, Easing.LINEAR, 0)
        ));

        state.setTarget("thumb", "y", 0);
        state.setTarget("thumb", "y", 100); // "y" has no transition spec

        // Should not be animating — no spec for "y"
        assertThat(state.isAnimating()).isFalse();
    }

    @Test
    void allProperty_matchesEverything() {
        state.registerTransitions("elem", List.of(
                new TransitionSpec(List.of("all"), 0.5, Easing.LINEAR, 0)
        ));

        state.setTarget("elem", "x", 0);
        state.setTarget("elem", "x", 50);

        assertThat(state.isAnimating()).isTrue();
    }

    // ==================== Interpolation ====================

    @Test
    void linearInterpolation_midway() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)
        ));

        state.setTarget("thumb", "x", 0);
        state.setTarget("thumb", "x", 100);

        // At t=0.5 (halfway through 1.0s duration), should be at 50
        state.update(0.5);
        assertThat(state.getValue("thumb", "x", 0)).isCloseTo(50.0, within(0.01));
    }

    @Test
    void linearInterpolation_complete() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.5, Easing.LINEAR, 0)
        ));

        state.setTarget("thumb", "x", 0);
        state.setTarget("thumb", "x", 100);

        state.update(0.5); // exactly at duration
        assertThat(state.getValue("thumb", "x", 0)).isEqualTo(100.0);
        assertThat(state.isAnimating()).isFalse();
    }

    @Test
    void linearInterpolation_overshootTime() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.3, Easing.LINEAR, 0)
        ));

        state.setTarget("thumb", "x", 0);
        state.setTarget("thumb", "x", 100);

        state.update(1.0); // well past duration
        assertThat(state.getValue("thumb", "x", 0)).isEqualTo(100.0);
        assertThat(state.isAnimating()).isFalse();
    }

    @Test
    void easeOut_startsAheadOfLinear() {
        AnimationState linear = new AnimationState();
        AnimationState eased = new AnimationState();

        linear.registerTransitions("e", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)));
        eased.registerTransitions("e", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.EASE_OUT, 0)));

        linear.setTarget("e", "x", 0);
        eased.setTarget("e", "x", 0);
        linear.setTarget("e", "x", 100);
        eased.setTarget("e", "x", 100);

        linear.update(0.25);
        eased.update(0.25);

        // Ease-out should be ahead of linear at t=0.25
        assertThat(eased.getValue("e", "x", 0))
                .isGreaterThan(linear.getValue("e", "x", 0));
    }

    // ==================== Delay ====================

    @Test
    void delay_holdsDuringDelayPeriod() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 0.5, Easing.LINEAR, 0.2) // 0.2s delay
        ));

        state.setTarget("thumb", "x", 0);
        state.setTarget("thumb", "x", 100);

        // During delay period — should still be at start
        state.update(0.1);
        assertThat(state.getValue("thumb", "x", 0)).isCloseTo(0.0, within(0.01));
        assertThat(state.isAnimating()).isTrue();
    }

    @Test
    void delay_animatesAfterDelay() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0.5) // 0.5s delay
        ));

        state.setTarget("thumb", "x", 0);
        state.setTarget("thumb", "x", 100);

        // 0.5s delay + 0.5s into animation = halfway
        state.update(1.0);
        assertThat(state.getValue("thumb", "x", 0)).isCloseTo(50.0, within(0.01));
    }

    // ==================== Interruptibility ====================

    @Test
    void interrupt_restartsFromCurrentPosition() {
        state.registerTransitions("thumb", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)
        ));

        // Start moving from 0 to 100
        state.setTarget("thumb", "x", 0);
        state.setTarget("thumb", "x", 100);

        // Advance to 50% → current = 50
        state.update(0.5);
        assertThat(state.getValue("thumb", "x", 0)).isCloseTo(50.0, within(0.01));

        // Interrupt: now go back to 0
        state.setTarget("thumb", "x", 0);
        assertThat(state.isAnimating()).isTrue();

        // Current should still be ~50 (just redirected)
        assertThat(state.getValue("thumb", "x", 0)).isCloseTo(50.0, within(0.01));

        // Advance halfway through new animation → should be at ~25 (midpoint between 50 and 0)
        state.update(0.5);
        assertThat(state.getValue("thumb", "x", 0)).isCloseTo(25.0, within(0.01));
    }

    // ==================== Multiple Properties ====================

    @Test
    void multipleProperties_independentAnimation() {
        state.registerTransitions("elem", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0),
                new TransitionSpec(List.of("opacity"), 0.5, Easing.LINEAR, 0)
        ));

        state.setTarget("elem", "x", 0);
        state.setTarget("elem", "opacity", 1.0);
        state.setTarget("elem", "x", 100);
        state.setTarget("elem", "opacity", 0.0);

        state.update(0.5);

        // x at 50% of 1.0s = 50
        assertThat(state.getValue("elem", "x", 0)).isCloseTo(50.0, within(0.01));
        // opacity at 100% of 0.5s = 0.0 (complete)
        assertThat(state.getValue("elem", "opacity", 1.0)).isCloseTo(0.0, within(0.01));
    }

    // ==================== Multiple Elements ====================

    @Test
    void multipleElements_independent() {
        state.registerTransitions("a", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)));
        state.registerTransitions("b", List.of(
                new TransitionSpec(List.of("x"), 0.5, Easing.LINEAR, 0)));

        state.setTarget("a", "x", 0);
        state.setTarget("b", "x", 0);
        state.setTarget("a", "x", 100);
        state.setTarget("b", "x", 200);

        state.update(0.5);

        assertThat(state.getValue("a", "x", 0)).isCloseTo(50.0, within(0.01));
        assertThat(state.getValue("b", "x", 0)).isCloseTo(200.0, within(0.01)); // complete
    }

    // ==================== Lifecycle ====================

    @Test
    void clear_removesAllState() {
        state.registerTransitions("elem", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)));
        state.setTarget("elem", "x", 0);
        state.setTarget("elem", "x", 100);

        state.clear();
        assertThat(state.isAnimating()).isFalse();
        assertThat(state.getValue("elem", "x", 42)).isEqualTo(42);
    }

    @Test
    void remove_removesOneElement() {
        state.registerTransitions("a", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)));
        state.registerTransitions("b", List.of(
                new TransitionSpec(List.of("x"), 1.0, Easing.LINEAR, 0)));

        state.setTarget("a", "x", 0);
        state.setTarget("b", "x", 0);
        state.setTarget("a", "x", 100);
        state.setTarget("b", "x", 100);

        state.remove("a");
        assertThat(state.activeTransitionCount()).isEqualTo(1);
    }

    // ==================== TransitionSpec ====================

    @Test
    void transitionSpec_covers() {
        TransitionSpec allSpec = new TransitionSpec(List.of("all"), 0.3, Easing.LINEAR, 0);
        assertThat(allSpec.covers("x")).isTrue();
        assertThat(allSpec.covers("rotation")).isTrue();

        TransitionSpec xSpec = new TransitionSpec(List.of("x", "y"), 0.3, Easing.LINEAR, 0);
        assertThat(xSpec.covers("x")).isTrue();
        assertThat(xSpec.covers("y")).isTrue();
        assertThat(xSpec.covers("z")).isFalse();
    }

    @Test
    void transitionSpec_effectiveDuration_spring() {
        TransitionSpec springSpec = new TransitionSpec(
                List.of("x"), 0.3, new Easing.Spring(), 0);

        // Spring duration should be computed, not 0.3
        assertThat(springSpec.effectiveDuration())
                .isNotEqualTo(0.3)
                .isPositive();
    }

    @Test
    void transitionSpec_effectiveDuration_bezier() {
        TransitionSpec bezierSpec = new TransitionSpec(
                List.of("x"), 0.5, Easing.EASE_OUT, 0);

        assertThat(bezierSpec.effectiveDuration()).isEqualTo(0.5);
    }

    // ==================== TransitionSpec.fromClass ====================

    @Transition(property = "x", duration = 0.2, easing = "ease-out")
    @Transition(property = "opacity", duration = 0.15, easing = "linear")
    static class MultiTransitionElement {}

    static class NoTransitionElement {}

    @Test
    void fromClass_compilesAnnotations() {
        List<TransitionSpec> specs = TransitionSpec.fromClass(MultiTransitionElement.class);

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).properties()).containsExactly("x");
        assertThat(specs.get(0).duration()).isEqualTo(0.2);
        assertThat(specs.get(0).easing()).isInstanceOf(Easing.CubicBezier.class);

        assertThat(specs.get(1).properties()).containsExactly("opacity");
        assertThat(specs.get(1).duration()).isEqualTo(0.15);
        assertThat(specs.get(1).easing()).isInstanceOf(Easing.Linear.class);
    }

    @Test
    void fromClass_noAnnotations_emptyList() {
        List<TransitionSpec> specs = TransitionSpec.fromClass(NoTransitionElement.class);
        assertThat(specs).isEmpty();
    }

    // ==================== Spring with AnimationState ====================

    @Test
    void springAnimation_overshoots() {
        state.registerTransitions("elem", List.of(
                new TransitionSpec(List.of("x"), 0, new Easing.Spring(300, 10, 1), 0)
        ));

        state.setTarget("elem", "x", 0);
        state.setTarget("elem", "x", 100);

        // Sample at many points to find overshoot
        boolean overshot = false;
        for (int i = 1; i <= 200; i++) {
            state.update(0.005); // 5ms steps
            double val = state.getValue("elem", "x", 0);
            if (val > 100) {
                overshot = true;
                break;
            }
        }
        assertThat(overshot).as("Spring animation should overshoot target").isTrue();
    }

    @Test
    void springAnimation_eventuallySettles() {
        state.registerTransitions("elem", List.of(
                new TransitionSpec(List.of("x"), 0, new Easing.Spring(), 0)
        ));

        state.setTarget("elem", "x", 0);
        state.setTarget("elem", "x", 100);

        // Run for plenty of time
        for (int i = 0; i < 500; i++) {
            state.update(0.01);
        }

        assertThat(state.getValue("elem", "x", 0)).isCloseTo(100.0, within(0.5));
        assertThat(state.isAnimating()).isFalse();
    }
}
