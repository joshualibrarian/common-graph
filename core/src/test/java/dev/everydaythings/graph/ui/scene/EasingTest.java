package dev.everydaythings.graph.ui.scene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Easing} timing functions.
 */
class EasingTest {

    // ==================== Boundary Values ====================

    @Test
    void linear_boundaries() {
        assertThat(Easing.LINEAR.apply(0)).isEqualTo(0);
        assertThat(Easing.LINEAR.apply(1)).isEqualTo(1);
        assertThat(Easing.LINEAR.apply(0.5)).isEqualTo(0.5);
    }

    @Test
    void cubicBezier_boundaries() {
        assertThat(Easing.EASE.apply(0)).isEqualTo(0);
        assertThat(Easing.EASE.apply(1)).isEqualTo(1);
    }

    @Test
    void spring_boundaries() {
        assertThat(Easing.SPRING.apply(0)).isEqualTo(0);
        assertThat(Easing.SPRING.apply(1)).isEqualTo(1);
    }

    @Test
    void steps_boundaries() {
        Easing steps = new Easing.Steps(4, true);
        assertThat(steps.apply(0)).isEqualTo(0);
        assertThat(steps.apply(1)).isEqualTo(1);
    }

    // ==================== Linear ====================

    @Test
    void linear_isIdentity() {
        for (double t = 0; t <= 1.0; t += 0.1) {
            assertThat(Easing.LINEAR.apply(t)).isCloseTo(t, within(1e-10));
        }
    }

    // ==================== CubicBezier ====================

    @Test
    void easeOut_startsAboveLinear() {
        // Ease-out starts fast — at t=0.25, progress should be ahead of linear
        double progress = Easing.EASE_OUT.apply(0.25);
        assertThat(progress).isGreaterThan(0.25);
    }

    @Test
    void easeIn_startsBelowLinear() {
        // Ease-in starts slow — at t=0.25, progress should be behind linear
        double progress = Easing.EASE_IN.apply(0.25);
        assertThat(progress).isLessThan(0.25);
    }

    @Test
    void easeInOut_symmetric() {
        // Ease-in-out should be roughly symmetric around 0.5
        double early = Easing.EASE_IN_OUT.apply(0.25);
        double late = Easing.EASE_IN_OUT.apply(0.75);
        assertThat(early + late).isCloseTo(1.0, within(0.05));
    }

    @Test
    void cubicBezier_monotonic() {
        // Standard easings should be monotonically increasing
        Easing[] monotonicEasings = {Easing.EASE, Easing.EASE_IN, Easing.EASE_OUT, Easing.EASE_IN_OUT};
        for (Easing easing : monotonicEasings) {
            double prev = 0;
            for (double t = 0.01; t <= 1.0; t += 0.01) {
                double current = easing.apply(t);
                assertThat(current).as("Easing %s at t=%f", easing, t)
                        .isGreaterThanOrEqualTo(prev - 1e-6);
                prev = current;
            }
        }
    }

    @Test
    void overshoot_exceedsOne() {
        // Overshoot easing should exceed 1.0 before settling
        boolean exceeded = false;
        for (double t = 0; t <= 1.0; t += 0.01) {
            if (Easing.OVERSHOOT.apply(t) > 1.0) {
                exceeded = true;
                break;
            }
        }
        assertThat(exceeded).as("OVERSHOOT should exceed 1.0 at some point").isTrue();
    }

    // ==================== Spring ====================

    @Test
    void spring_settlesAtOne() {
        // At t=1.0 (end of settling period), spring should be at 1.0
        assertThat(Easing.SPRING.apply(1.0)).isEqualTo(1.0);
    }

    @Test
    void spring_overshoots() {
        // Default spring (underdamped) should overshoot
        boolean exceeded = false;
        for (double t = 0; t <= 1.0; t += 0.005) {
            if (Easing.SPRING.apply(t) > 1.0) {
                exceeded = true;
                break;
            }
        }
        assertThat(exceeded).as("Default spring should overshoot").isTrue();
    }

    @Test
    void springBouncy_oscillates() {
        // Bouncy spring should cross 1.0 multiple times
        int crossings = 0;
        boolean above = false;
        for (double t = 0.01; t <= 0.99; t += 0.005) {
            double val = Easing.SPRING_BOUNCY.apply(t);
            boolean nowAbove = val > 1.0;
            if (nowAbove != above) {
                crossings++;
                above = nowAbove;
            }
        }
        assertThat(crossings).as("Bouncy spring should cross 1.0 multiple times")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void spring_settlingDuration_positive() {
        Easing.Spring spring = new Easing.Spring();
        assertThat(spring.settlingDuration()).isPositive();
    }

    @Test
    void spring_customParameters() {
        Easing.Spring gentle = new Easing.Spring(120, 14, 1);
        Easing.Spring snappy = new Easing.Spring(400, 30, 1);

        // Snappy should settle faster than gentle
        assertThat(snappy.settlingDuration()).isLessThan(gentle.settlingDuration());
    }

    @Test
    void spring_criticallyDamped() {
        // Critically damped: zeta = 1 → c / (2*sqrt(k*m)) = 1 → c = 2*sqrt(k*m)
        double k = 200;
        double m = 1;
        double c = 2 * Math.sqrt(k * m);
        Easing.Spring critical = new Easing.Spring(k, c, m);

        // Should not overshoot
        for (double t = 0; t <= 1.0; t += 0.01) {
            assertThat(critical.apply(t)).isLessThanOrEqualTo(1.001);
        }
    }

    // ==================== Steps ====================

    @Test
    void steps_jumpEnd_fourSteps() {
        Easing steps = new Easing.Steps(4, true);
        assertThat(steps.apply(0.1)).isEqualTo(0.0);   // first quarter → 0
        assertThat(steps.apply(0.3)).isEqualTo(0.25);   // second quarter → 0.25
        assertThat(steps.apply(0.6)).isEqualTo(0.5);    // third quarter → 0.5
        assertThat(steps.apply(0.9)).isEqualTo(0.75);   // fourth quarter → 0.75
    }

    @Test
    void steps_jumpStart_fourSteps() {
        Easing steps = new Easing.Steps(4, false);
        assertThat(steps.apply(0.1)).isEqualTo(0.25);   // immediately jumps
        assertThat(steps.apply(0.3)).isEqualTo(0.5);
    }

    @Test
    void steps_invalidCount_throws() {
        assertThatThrownBy(() -> new Easing.Steps(0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Parsing ====================

    @Test
    void parse_namedPresets() {
        assertThat(Easing.parse("linear")).isEqualTo(Easing.LINEAR);
        assertThat(Easing.parse("ease")).isEqualTo(Easing.EASE);
        assertThat(Easing.parse("ease-in")).isEqualTo(Easing.EASE_IN);
        assertThat(Easing.parse("ease-out")).isEqualTo(Easing.EASE_OUT);
        assertThat(Easing.parse("ease-in-out")).isEqualTo(Easing.EASE_IN_OUT);
        assertThat(Easing.parse("spring")).isEqualTo(Easing.SPRING);
        assertThat(Easing.parse("spring-gentle")).isEqualTo(Easing.SPRING_GENTLE);
        assertThat(Easing.parse("spring-snappy")).isEqualTo(Easing.SPRING_SNAPPY);
        assertThat(Easing.parse("spring-bouncy")).isEqualTo(Easing.SPRING_BOUNCY);
    }

    @Test
    void parse_cubicBezier() {
        Easing parsed = Easing.parse("cubic-bezier(0.4, 0, 0.2, 1)");
        assertThat(parsed).isInstanceOf(Easing.CubicBezier.class);
        Easing.CubicBezier cb = (Easing.CubicBezier) parsed;
        assertThat(cb.x1()).isEqualTo(0.4);
        assertThat(cb.y1()).isEqualTo(0);
        assertThat(cb.x2()).isEqualTo(0.2);
        assertThat(cb.y2()).isEqualTo(1);
    }

    @Test
    void parse_springWithArgs() {
        Easing twoArg = Easing.parse("spring(300, 15)");
        assertThat(twoArg).isInstanceOf(Easing.Spring.class);
        Easing.Spring s2 = (Easing.Spring) twoArg;
        assertThat(s2.stiffness()).isEqualTo(300);
        assertThat(s2.damping()).isEqualTo(15);
        assertThat(s2.mass()).isEqualTo(1); // default

        Easing threeArg = Easing.parse("spring(300, 15, 2)");
        Easing.Spring s3 = (Easing.Spring) threeArg;
        assertThat(s3.mass()).isEqualTo(2);
    }

    @Test
    void parse_steps() {
        Easing steps = Easing.parse("steps(5)");
        assertThat(steps).isInstanceOf(Easing.Steps.class);
        assertThat(((Easing.Steps) steps).count()).isEqualTo(5);
        assertThat(((Easing.Steps) steps).jumpEnd()).isTrue(); // default

        Easing jumpStart = Easing.parse("steps(3, jump-start)");
        assertThat(((Easing.Steps) jumpStart).jumpEnd()).isFalse();
    }

    @Test
    void parse_nullOrEmpty_defaultsToEase() {
        assertThat(Easing.parse(null)).isEqualTo(Easing.EASE);
        assertThat(Easing.parse("")).isEqualTo(Easing.EASE);
    }

    @Test
    void parse_unknown_throws() {
        assertThatThrownBy(() -> Easing.parse("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_caseInsensitive() {
        assertThat(Easing.parse("EASE-OUT")).isEqualTo(Easing.EASE_OUT);
        assertThat(Easing.parse("Linear")).isEqualTo(Easing.LINEAR);
    }

    // ==================== General Properties ====================

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -0.1})
    void allEasings_clampNegativeToZero(double t) {
        assertThat(Easing.LINEAR.apply(t)).isEqualTo(0);
        assertThat(Easing.EASE.apply(t)).isEqualTo(0);
        assertThat(Easing.SPRING.apply(t)).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.1, 2.0})
    void allEasings_clampAboveOneToOne(double t) {
        assertThat(Easing.LINEAR.apply(t)).isEqualTo(1);
        assertThat(Easing.EASE.apply(t)).isEqualTo(1);
        assertThat(Easing.SPRING.apply(t)).isEqualTo(1);
    }
}
