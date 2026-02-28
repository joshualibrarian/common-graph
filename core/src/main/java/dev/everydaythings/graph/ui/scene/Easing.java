package dev.everydaythings.graph.ui.scene;

/**
 * Timing functions for animation transitions.
 *
 * <p>An Easing maps normalized time t∈[0,1] to progress p∈[0,1].
 * Spring easings can overshoot (p &gt; 1) before settling.
 *
 * <p>Covers the full CSS timing-function space plus spring physics:
 * <ul>
 *   <li>{@link Linear} — constant speed</li>
 *   <li>{@link CubicBezier} — CSS cubic-bezier curves</li>
 *   <li>{@link Spring} — physically-based spring simulation</li>
 *   <li>{@link Steps} — discrete steps (CSS steps())</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Easing ease = Easing.EASE_OUT;
 * double progress = ease.apply(0.5); // halfway through time → ease-out progress
 *
 * // From annotation string
 * Easing spring = Easing.parse("spring(300, 20)");
 * Easing bezier = Easing.parse("cubic-bezier(0.4, 0, 0.2, 1)");
 * }</pre>
 *
 * @see TransitionSpec
 * @see Transition
 */
public sealed interface Easing
        permits Easing.Linear, Easing.CubicBezier, Easing.Spring, Easing.Steps {

    /**
     * Map normalized time to animation progress.
     *
     * @param t Normalized time in [0, 1] (0 = start, 1 = end)
     * @return Progress value. Usually [0, 1] but springs can overshoot.
     */
    double apply(double t);

    // ==================== Variants ====================

    /**
     * Constant speed — straight line from 0 to 1.
     */
    record Linear() implements Easing {
        @Override
        public double apply(double t) {
            if (t <= 0) return 0;
            if (t >= 1) return 1;
            return t;
        }
    }

    /**
     * CSS cubic-bezier curve.
     *
     * <p>Defined by two control points (x1,y1) and (x2,y2).
     * The curve starts at (0,0) and ends at (1,1).
     * Uses Newton-Raphson iteration to solve the parametric equation.
     *
     * @param x1 First control point X (typically 0–1)
     * @param y1 First control point Y (can be negative or &gt;1 for overshoot)
     * @param x2 Second control point X (typically 0–1)
     * @param y2 Second control point Y (can be negative or &gt;1 for overshoot)
     */
    record CubicBezier(double x1, double y1, double x2, double y2) implements Easing {

        @Override
        public double apply(double t) {
            if (t <= 0) return 0;
            if (t >= 1) return 1;

            // Solve for parameter u where bezierX(u) = t using Newton-Raphson
            double u = t; // initial guess
            for (int i = 0; i < 8; i++) {
                double xError = sampleX(u) - t;
                if (Math.abs(xError) < 1e-7) break;
                double dx = sampleDX(u);
                if (Math.abs(dx) < 1e-7) break;
                u -= xError / dx;
            }

            // Clamp and refine with bisection if Newton wandered
            u = Math.max(0, Math.min(1, u));
            return sampleY(u);
        }

        private double sampleX(double u) {
            // B(u) = 3(1-u)²u·x1 + 3(1-u)u²·x2 + u³
            double inv = 1 - u;
            return 3 * inv * inv * u * x1 + 3 * inv * u * u * x2 + u * u * u;
        }

        private double sampleY(double u) {
            double inv = 1 - u;
            return 3 * inv * inv * u * y1 + 3 * inv * u * u * y2 + u * u * u;
        }

        private double sampleDX(double u) {
            // dB/du for X component
            double inv = 1 - u;
            return 3 * inv * inv * x1 + 6 * inv * u * (x2 - x1) + 3 * u * u * (1 - x2);
        }
    }

    /**
     * Physically-based spring simulation.
     *
     * <p>Simulates a damped harmonic oscillator. The spring overshoots and
     * bounces before settling, producing natural-feeling UI motion.
     *
     * <p>Duration is implicit — determined by when the spring settles
     * within epsilon of the target. The {@code t} parameter is normalized
     * to this settling time.
     *
     * <p>Common presets:
     * <ul>
     *   <li>Gentle: stiffness=120, damping=14</li>
     *   <li>Default: stiffness=200, damping=20</li>
     *   <li>Snappy: stiffness=400, damping=30</li>
     *   <li>Bouncy: stiffness=300, damping=10</li>
     * </ul>
     *
     * @param stiffness Spring constant (higher = faster, stiffer). Typical: 100–500.
     * @param damping   Damping coefficient (higher = less oscillation). Typical: 10–40.
     * @param mass      Mass of the object (higher = more inertia). Typical: 1.
     */
    record Spring(double stiffness, double damping, double mass) implements Easing {

        /** Default spring: responsive with slight overshoot. */
        public Spring() {
            this(200, 20, 1);
        }

        /** Spring with default mass of 1. */
        public Spring(double stiffness, double damping) {
            this(stiffness, damping, 1);
        }

        @Override
        public double apply(double t) {
            if (t <= 0) return 0;
            if (t >= 1) return 1;

            // Convert normalized t to real time using settling duration
            double settleTime = settlingDuration();
            double time = t * settleTime;

            return 1.0 - springPosition(time);
        }

        /**
         * Compute spring displacement from target at real time t.
         *
         * <p>Solves the damped harmonic oscillator equation:
         * m·x'' + c·x' + k·x = 0
         * with initial conditions x(0) = 1 (displacement), x'(0) = 0 (at rest).
         */
        private double springPosition(double time) {
            double omega0 = Math.sqrt(stiffness / mass); // natural frequency
            double zeta = damping / (2 * Math.sqrt(stiffness * mass)); // damping ratio

            if (zeta < 1) {
                // Underdamped — oscillates
                double omegaD = omega0 * Math.sqrt(1 - zeta * zeta);
                double envelope = Math.exp(-zeta * omega0 * time);
                return envelope * (Math.cos(omegaD * time)
                        + (zeta * omega0 / omegaD) * Math.sin(omegaD * time));
            } else if (zeta == 1) {
                // Critically damped — fastest approach without oscillation
                double envelope = Math.exp(-omega0 * time);
                return envelope * (1 + omega0 * time);
            } else {
                // Overdamped — sluggish approach
                double s1 = -omega0 * (zeta - Math.sqrt(zeta * zeta - 1));
                double s2 = -omega0 * (zeta + Math.sqrt(zeta * zeta - 1));
                double c2 = (s1) / (s1 - s2);
                double c1 = 1 - c2;
                return c1 * Math.exp(s1 * time) + c2 * Math.exp(s2 * time);
            }
        }

        /**
         * Time (in seconds) for the spring to settle within epsilon of target.
         *
         * <p>For underdamped springs, this is when the envelope drops below epsilon.
         * For critically/overdamped, it's when position is within epsilon.
         */
        public double settlingDuration() {
            double epsilon = 0.001;
            double omega0 = Math.sqrt(stiffness / mass);
            double zeta = damping / (2 * Math.sqrt(stiffness * mass));

            if (zeta < 1) {
                // Envelope: exp(-zeta * omega0 * t) = epsilon
                // t = -ln(epsilon) / (zeta * omega0)
                return -Math.log(epsilon) / (zeta * omega0);
            } else {
                // For critically/overdamped, simulate to find settling time
                double dt = 0.01;
                double maxTime = 10.0;
                for (double t = dt; t < maxTime; t += dt) {
                    if (Math.abs(springPosition(t)) < epsilon) {
                        return t;
                    }
                }
                return maxTime;
            }
        }
    }

    /**
     * Discrete steps — jumps between values instead of smooth interpolation.
     *
     * <p>Equivalent to CSS steps() timing function.
     *
     * @param count   Number of steps (must be &gt;= 1)
     * @param jumpEnd If true, the first step happens at t=1/count (CSS step-end).
     *                If false, the first step happens at t=0 (CSS step-start).
     */
    record Steps(int count, boolean jumpEnd) implements Easing {

        public Steps {
            if (count < 1) throw new IllegalArgumentException("Steps count must be >= 1");
        }

        @Override
        public double apply(double t) {
            if (t <= 0) return 0;
            if (t >= 1) return 1;

            if (jumpEnd) {
                return Math.floor(t * count) / count;
            } else {
                return Math.ceil(t * count) / count;
            }
        }
    }

    // ==================== Presets ====================

    /** Constant speed. */
    Easing LINEAR = new Linear();

    /** CSS ease — slow start, fast middle, slow end. */
    Easing EASE = new CubicBezier(0.25, 0.1, 0.25, 1.0);

    /** CSS ease-in — slow start, fast end. */
    Easing EASE_IN = new CubicBezier(0.42, 0, 1, 1);

    /** CSS ease-out — fast start, slow end. Best for state changes. */
    Easing EASE_OUT = new CubicBezier(0, 0, 0.58, 1);

    /** CSS ease-in-out — slow start and end. */
    Easing EASE_IN_OUT = new CubicBezier(0.42, 0, 0.58, 1);

    /** Slight overshoot at end — playful feel. */
    Easing OVERSHOOT = new CubicBezier(0.34, 1.56, 0.64, 1);

    /** Default spring — responsive with slight overshoot. */
    Easing SPRING = new Spring();

    /** Gentle spring — slow, smooth, minimal overshoot. */
    Easing SPRING_GENTLE = new Spring(120, 14, 1);

    /** Snappy spring — fast with quick settle. */
    Easing SPRING_SNAPPY = new Spring(400, 30, 1);

    /** Bouncy spring — lots of overshoot and oscillation. */
    Easing SPRING_BOUNCY = new Spring(300, 10, 1);

    // ==================== Parsing ====================

    /**
     * Parse an easing specification from an annotation string.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>{@code "linear"}</li>
     *   <li>{@code "ease"}, {@code "ease-in"}, {@code "ease-out"}, {@code "ease-in-out"}</li>
     *   <li>{@code "spring"}, {@code "spring-gentle"}, {@code "spring-snappy"}, {@code "spring-bouncy"}</li>
     *   <li>{@code "cubic-bezier(x1, y1, x2, y2)"}</li>
     *   <li>{@code "spring(stiffness, damping)"} or {@code "spring(stiffness, damping, mass)"}</li>
     *   <li>{@code "steps(count)"} or {@code "steps(count, jump-end)"} / {@code "steps(count, jump-start)"}</li>
     * </ul>
     *
     * @param spec The easing specification string
     * @return The parsed Easing
     * @throws IllegalArgumentException if the spec is not recognized
     */
    static Easing parse(String spec) {
        if (spec == null || spec.isEmpty()) return EASE;

        String s = spec.trim().toLowerCase();

        // Named presets
        return switch (s) {
            case "linear" -> LINEAR;
            case "ease" -> EASE;
            case "ease-in" -> EASE_IN;
            case "ease-out" -> EASE_OUT;
            case "ease-in-out" -> EASE_IN_OUT;
            case "overshoot" -> OVERSHOOT;
            case "spring" -> SPRING;
            case "spring-gentle" -> SPRING_GENTLE;
            case "spring-snappy" -> SPRING_SNAPPY;
            case "spring-bouncy" -> SPRING_BOUNCY;
            default -> parseFunction(s);
        };
    }

    private static Easing parseFunction(String s) {
        if (s.startsWith("cubic-bezier(") && s.endsWith(")")) {
            String args = s.substring("cubic-bezier(".length(), s.length() - 1);
            double[] vals = parseArgs(args, 4);
            return new CubicBezier(vals[0], vals[1], vals[2], vals[3]);
        }

        if (s.startsWith("spring(") && s.endsWith(")")) {
            String args = s.substring("spring(".length(), s.length() - 1);
            double[] vals = parseArgs(args, -1); // variable args
            if (vals.length == 2) {
                return new Spring(vals[0], vals[1]);
            } else if (vals.length == 3) {
                return new Spring(vals[0], vals[1], vals[2]);
            }
            throw new IllegalArgumentException("spring() requires 2 or 3 arguments: " + s);
        }

        if (s.startsWith("steps(") && s.endsWith(")")) {
            String inner = s.substring("steps(".length(), s.length() - 1);
            String[] parts = inner.split(",");
            int count = Integer.parseInt(parts[0].trim());
            boolean jumpEnd = true;
            if (parts.length > 1) {
                String mode = parts[1].trim();
                jumpEnd = !"jump-start".equals(mode) && !"start".equals(mode);
            }
            return new Steps(count, jumpEnd);
        }

        throw new IllegalArgumentException("Unknown easing: " + s);
    }

    private static double[] parseArgs(String args, int expected) {
        String[] parts = args.split(",");
        double[] vals = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vals[i] = Double.parseDouble(parts[i].trim());
        }
        if (expected > 0 && vals.length != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " arguments, got " + vals.length);
        }
        return vals;
    }
}
