package dev.everydaythings.graph.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Exact rational number {@code p/q}, stored as a pair of {@link BigInteger}.
 *
 * <p>Canonicalized at construction:
 * <ul>
 *   <li>{@code gcd(|p|, q) == 1}</li>
 *   <li>{@code q > 0}</li>
 *   <li>Zero is {@code 0/1}</li>
 * </ul>
 *
 * <p>Extends {@link Number} so Rational composes with the rest of Java's
 * numeric tower. The exact representation is preserved across arithmetic;
 * {@link #doubleValue()} / {@link #toBigDecimal(int)} may lose precision
 * for non-terminating decimals.
 *
 * <p>Wire form: CG-CBOR Tag 6 ({@code RATIONAL}) wrapping a 2-element CBOR
 * array {@code [numerator, denominator]}. Each is a CBOR integer for small
 * values or Tag-2/Tag-3 bignum for large ones.
 */
public final class Rational extends Number implements Comparable<Rational> {

    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE  = new Rational(BigInteger.ONE,  BigInteger.ONE);

    private final BigInteger numerator;
    private final BigInteger denominator;

    public Rational(BigInteger numerator, BigInteger denominator) {
        Objects.requireNonNull(numerator,   "numerator");
        Objects.requireNonNull(denominator, "denominator");
        if (denominator.signum() == 0) {
            throw new ArithmeticException("Rational denominator must be non-zero");
        }
        BigInteger n = numerator;
        BigInteger d = denominator;
        if (d.signum() < 0) {
            n = n.negate();
            d = d.negate();
        }
        if (n.signum() == 0) {
            this.numerator   = BigInteger.ZERO;
            this.denominator = BigInteger.ONE;
            return;
        }
        BigInteger g = n.abs().gcd(d);
        if (!g.equals(BigInteger.ONE)) {
            n = n.divide(g);
            d = d.divide(g);
        }
        this.numerator   = n;
        this.denominator = d;
    }

    // ---- Factories ----

    public static Rational of(long num, long den) {
        return new Rational(BigInteger.valueOf(num), BigInteger.valueOf(den));
    }

    public static Rational of(BigInteger num, BigInteger den) {
        return new Rational(num, den);
    }

    public static Rational ofLong(long v) {
        return new Rational(BigInteger.valueOf(v), BigInteger.ONE);
    }

    public static Rational ofInt(int v) {
        return new Rational(BigInteger.valueOf(v), BigInteger.ONE);
    }

    /** Parse {@code "3/4"}, {@code "-22/7"}, or an integer like {@code "5"}. */
    public static Rational parse(String s) {
        s = s.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Empty rational string");
        int slash = s.indexOf('/');
        if (slash < 0) {
            return new Rational(new BigInteger(s), BigInteger.ONE);
        }
        BigInteger num = new BigInteger(s.substring(0, slash).trim());
        BigInteger den = new BigInteger(s.substring(slash + 1).trim());
        return new Rational(num, den);
    }

    // ---- Accessors ----

    public BigInteger numerator()   { return numerator; }
    public BigInteger denominator() { return denominator; }

    public int signum() {
        return numerator.signum();
    }

    // ---- Math ----

    public Rational negate() {
        return new Rational(numerator.negate(), denominator);
    }

    public Rational reciprocal() {
        if (numerator.signum() == 0) throw new ArithmeticException("Reciprocal of zero");
        return new Rational(denominator, numerator);
    }

    public Rational add(Rational other) {
        return new Rational(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Rational subtract(Rational other) {
        return add(other.negate());
    }

    public Rational multiply(Rational other) {
        return new Rational(numerator.multiply(other.numerator),
                            denominator.multiply(other.denominator));
    }

    public Rational divide(Rational other) {
        return multiply(other.reciprocal());
    }

    // ---- Conversions ----

    /**
     * Convert to a {@link BigDecimal} at the given scale; rounds HALF_UP when
     * the rational is non-terminating in base 10.
     */
    public BigDecimal toBigDecimal(int scale) {
        return new BigDecimal(numerator)
                .divide(new BigDecimal(denominator),
                        new MathContext(scale + 1, RoundingMode.HALF_UP));
    }

    // ---- Number contract ----

    @Override public int    intValue()    { return numerator.divide(denominator).intValue(); }
    @Override public long   longValue()   { return numerator.divide(denominator).longValue(); }
    @Override public float  floatValue()  { return (float) doubleValue(); }
    @Override public double doubleValue() {
        return numerator.doubleValue() / denominator.doubleValue();
    }

    // ---- Comparable ----

    @Override
    public int compareTo(Rational other) {
        // a/b vs c/d  →  a*d vs c*b   (denominators positive by canonicalization)
        return numerator.multiply(other.denominator)
                .compareTo(other.numerator.multiply(denominator));
    }

    // ---- Equality / display ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rational other)) return false;
        return numerator.equals(other.numerator) && denominator.equals(other.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    @Override
    public String toString() {
        if (denominator.equals(BigInteger.ONE)) return numerator.toString();
        return numerator + "/" + denominator;
    }
}
