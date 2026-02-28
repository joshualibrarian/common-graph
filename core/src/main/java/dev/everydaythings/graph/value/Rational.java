package dev.everydaythings.graph.value;

import lombok.Getter;

/**
 * Normalized rational number p/q (exact fractions).
 *
 * <p>Normalization invariants:
 * <ul>
 *   <li>gcd(|p|, |q|) == 1</li>
 *   <li>q > 0</li>
 *   <li>0 is represented as (0, 1)</li>
 * </ul>
 *
 * <p>Use Rational when you need exact fractions that don't lose precision,
 * such as "1/3" or "22/7".
 */
@Getter
@Value.Type("cg.value:rational")
public final class Rational implements Numeric {

    @Canon(order = 1)
    private final long p;  // numerator

    @Canon(order = 2)
    private final long q;  // denominator (always > 0)

    public Rational(long p, long q) {
        if (q == 0) throw new IllegalArgumentException("Denominator must be non-zero");

        if (p == 0) {
            this.p = 0;
            this.q = 1;
            return;
        }

        long pp = p;
        long qq = q;

        // Ensure denominator is positive
        if (qq < 0) {
            pp = -pp;
            qq = -qq;
        }

        // Reduce to lowest terms
        long g = gcd(Math.abs(pp), qq);
        this.p = pp / g;
        this.q = qq / g;
    }

    public static Rational of(long p, long q) {
        return new Rational(p, q);
    }

    public static Rational ofLong(long v) {
        return new Rational(v, 1);
    }

    public static Rational ofInt(int v) {
        return new Rational(v, 1);
    }

    /**
     * Parse a rational string like "3/4" or "-22/7" or just "5".
     */
    public static Rational parse(String s) {
        s = s.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Empty rational string");

        int slashIdx = s.indexOf('/');
        if (slashIdx < 0) {
            // Just an integer
            return new Rational(Long.parseLong(s), 1);
        }

        long p = Long.parseLong(s.substring(0, slashIdx).trim());
        long q = Long.parseLong(s.substring(slashIdx + 1).trim());
        return new Rational(p, q);
    }

    @Override
    public String token() {
        if (q == 1) {
            return String.valueOf(p);
        }
        return p + "/" + q;
    }

    @Override
    public String toString() {
        return token();
    }

    /**
     * Convert to double (may lose precision).
     */
    public double toDouble() {
        return (double) p / (double) q;
    }

    /**
     * Convert to Decimal (may lose precision for non-terminating decimals).
     */
    public Decimal toDecimal(int maxScale) {
        // Simple conversion: compute p/q to maxScale decimal places
        long scale = 1;
        for (int i = 0; i < maxScale; i++) {
            scale *= 10;
        }
        long unscaled = (p * scale) / q;
        return new Decimal(unscaled, maxScale);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }

    // No-arg constructor for Canonical decoding
    @SuppressWarnings("unused")
    private Rational() {
        this.p = 0;
        this.q = 1;
    }
}
