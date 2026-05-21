package dev.everydaythings.graph.value.location;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.LocationVocabulary;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.Rational;
import dev.everydaythings.graph.value.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates — a compound Value carrying a geographic point as latitude /
 * longitude (and optional altitude).
 *
 * <p>Stored as decimal-degree {@link Rational} values per CG's no-IEEE-754
 * policy.  Latitude is in [-90, +90]; longitude in [-180, +180]; altitude
 * is in meters above mean sea level (no enforced range).
 *
 * <p>Content-addressed: two Coordinates with identical bindings produce
 * identical CIDs.
 *
 * <p>This is a structured Value, not an Identifier.  Rendering coordinates
 * to text varies (DD, DMS, MGRS, OLC / Plus Codes, ...) — no single
 * canonical text form, so Coordinates extends Value directly.
 */
@Seed.Item(key = Coordinates.KEY, head = Value.KEY)
@Seed.Gloss(english = "a geographic point as latitude / longitude (and optional altitude) "
                   + "on the Earth's surface, in decimal degrees")
@Seed.Lexeme(english = {"coordinates", "geographic point"}, pos = PartOfSpeech.Noun.KEY)
public final class Coordinates extends Value {

    public static final String KEY = "cg.archetype:coordinates";

    private Coordinates(List<Binding> bindings) {
        super(ItemRef.iid(KEY), bindings);
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    /** Fluent builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory taking latitude + longitude as decimal-degree
     * doubles.  Doubles are converted to {@link Rational} via
     * {@link BigDecimal#valueOf(double)} so the rational matches the
     * displayed decimal exactly.
     */
    public static Coordinates of(double latitude, double longitude) {
        return builder()
                .latitude(rationalFromDouble(latitude))
                .longitude(rationalFromDouble(longitude))
                .build();
    }

    /**
     * Convenience factory with altitude.
     */
    public static Coordinates of(double latitude, double longitude, double altitudeMeters) {
        return builder()
                .latitude(rationalFromDouble(latitude))
                .longitude(rationalFromDouble(longitude))
                .altitude(rationalFromDouble(altitudeMeters))
                .build();
    }

    /**
     * Typed view over an existing Body whose head is the Coordinates archetype.
     */
    public static Coordinates from(dev.everydaythings.graph.datum.Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Coordinates c) return c;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the Coordinates archetype: " + body.headRef());
        }
        return new Coordinates(new ArrayList<>(body.bindings()));
    }

    private static Rational rationalFromDouble(double d) {
        BigDecimal bd = BigDecimal.valueOf(d);
        BigDecimal scaled = bd.movePointRight(bd.scale());
        return Rational.of(scaled.toBigInteger(), java.math.BigInteger.TEN.pow(bd.scale()));
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** Latitude in decimal degrees, if present. */
    public Optional<Rational> latitude()  { return part(LocationVocabulary.Latitude.KEY); }

    /** Longitude in decimal degrees, if present. */
    public Optional<Rational> longitude() { return part(LocationVocabulary.Longitude.KEY); }

    /** Altitude in meters above mean sea level, if present. */
    public Optional<Rational> altitude()  { return part(LocationVocabulary.Altitude.KEY); }

    private Optional<Rational> part(String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : bindings()) {
            if (role.equals(b.role())
                    && b.qualifiers().isEmpty()
                    && b.target() instanceof Rational r) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    /** Fluent builder.  Null parts are silently dropped. */
    public static final class Builder {
        private final List<Binding> bindings = new ArrayList<>();

        public Builder latitude(Rational lat) {
            return part(LocationVocabulary.Latitude.KEY, validateLat(lat));
        }

        public Builder longitude(Rational lng) {
            return part(LocationVocabulary.Longitude.KEY, validateLng(lng));
        }

        public Builder altitude(Rational alt) {
            return part(LocationVocabulary.Altitude.KEY, alt);
        }

        private Builder part(String roleKey, Rational value) {
            if (value == null) return this;
            bindings.add(Binding.literal(ItemRef.iid(roleKey), value));
            return this;
        }

        private static Rational validateLat(Rational r) {
            if (r == null) return null;
            BigDecimal degrees = new BigDecimal(r.numerator())
                    .divide(new BigDecimal(r.denominator()),
                            10, java.math.RoundingMode.HALF_EVEN);
            if (degrees.compareTo(new BigDecimal("-90")) < 0
                    || degrees.compareTo(new BigDecimal("90")) > 0) {
                throw new IllegalArgumentException(
                        "Latitude must be in [-90, +90]; got " + degrees);
            }
            return r;
        }

        private static Rational validateLng(Rational r) {
            if (r == null) return null;
            BigDecimal degrees = new BigDecimal(r.numerator())
                    .divide(new BigDecimal(r.denominator()),
                            10, java.math.RoundingMode.HALF_EVEN);
            if (degrees.compareTo(new BigDecimal("-180")) < 0
                    || degrees.compareTo(new BigDecimal("180")) > 0) {
                throw new IllegalArgumentException(
                        "Longitude must be in [-180, +180]; got " + degrees);
            }
            return r;
        }

        public Coordinates build() {
            return new Coordinates(new ArrayList<>(bindings));
        }
    }
}
