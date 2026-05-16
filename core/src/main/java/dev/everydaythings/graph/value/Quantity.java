package dev.everydaythings.graph.value;

import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.UnitVocabulary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Quantity — a numeric magnitude paired with a dimensional formula.
 *
 * <p>Quantity bodies use the tiny-value-body shape:
 * <pre>
 * Body[head=Quantity, Value=N, @Meter=1]              // 5 meters
 * Body[head=Quantity, Value=30, @Second=1]            // 30 seconds
 * Body[head=Quantity, Value=5, @Meter=1, @Second=-1]  // 5 m/s
 * Body[head=Quantity, Value=42]                       // dimensionless 42
 * </pre>
 *
 * <p>The magnitude lives in a {@link ThematicRole.Value Value}-keyed binding.
 * Each dimensional component is its own binding: the role is the unit's IID
 * (Meter, Second, Kilogram, ...) and the target is the integer exponent of
 * that unit in the dimensional formula.  Zero exponents are skipped on
 * construction.
 *
 * <p>Operators consuming Quantity bodies (Add, Multiply, Compare, ...) inspect
 * the unit bindings to do dimensional analysis: matching formulas for
 * addition, combining formulas for multiplication.  Compound units like
 * Newton or Watt are presentation shorthands that normalize to their
 * base-unit dimensional formula before hashing.
 */
@Seed.Item(key = Quantity.KEY, head = Value.KEY)
@Seed.Mints(key = Quantity.KEY)
public class Quantity extends Value {

    public static final String KEY = "cg.value:quantity";

    // ==================================================================================
    // Archetype-level lexical and schema frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a numeric magnitude paired with a dimensional formula — magnitude in the Value "
                    + "binding, dimensional contributions as per-unit exponent bindings";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "quantity";

    /** Quantity values are expected to carry a magnitude in their Value binding. */
    @Seed.Frame(predicate = SchemaVocabulary.Expects.KEY,
      field = @Seed.Binding(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
    static final ItemRef expectMagnitude = ItemRef.iid(ThematicRole.Value.KEY);

    // ==================================================================================
    // Construction — every Quantity IS a Body (head=Quantity, magnitude + unit bindings).
    // ==================================================================================

    /**
     * Build a Quantity from a magnitude and a dimensional formula encoded as
     * per-unit exponent entries.  Entries with exponent zero are dropped.
     */
    public Quantity(long magnitude, Map<ItemRef, Long> unitExponents) {
        super(ItemRef.iid(KEY), buildBindings(magnitude, unitExponents));
    }

    /** Single-unit Quantity (e.g., {@code new Quantity(5, meter, 1)} = 5 m). */
    public Quantity(long magnitude, ItemRef unit, long exponent) {
        this(magnitude, Map.of(unit, exponent));
    }

    /** Dimensionless Quantity. */
    public Quantity(long magnitude) {
        this(magnitude, Map.of());
    }

    /**
     * Subclass entry point — typed dimensional value-classes (Length, Mass, Time,
     * ...) call this from their own constructors with their own archetype IID as
     * the head.  The resulting body has the subclass's head but the same magnitude
     * + unit-exponent binding shape, so dimensional analysis still works
     * uniformly across all Quantity subclasses.
     */
    protected Quantity(ItemRef head, long magnitude, Map<ItemRef, Long> unitExponents) {
        super(head, buildBindings(magnitude, unitExponents));
    }

    private static List<Binding> buildBindings(long magnitude, Map<ItemRef, Long> unitExponents) {
        Objects.requireNonNull(unitExponents, "unitExponents");
        List<Binding> result = new ArrayList<>(1 + unitExponents.size());
        result.add(Binding.literal(ItemRef.iid(ThematicRole.Value.KEY), magnitude));
        for (Map.Entry<ItemRef, Long> entry : unitExponents.entrySet()) {
            long exponent = entry.getValue();
            if (exponent == 0) continue;
            result.add(Binding.literal(entry.getKey(), exponent));
        }
        return result;
    }

    // ==================================================================================
    // Static factories — convenience for the common cases
    // ==================================================================================

    /** Dimensionless quantity. */
    public static Quantity of(long n) {
        return new Quantity(n);
    }

    /** Length in meters. */
    public static Quantity meters(long n) {
        return new Quantity(n, ItemRef.iid(UnitVocabulary.Meter.KEY), 1L);
    }

    /** Duration in seconds. */
    public static Quantity seconds(long n) {
        return new Quantity(n, ItemRef.iid(UnitVocabulary.Second.KEY), 1L);
    }

    /** Mass in kilograms. */
    public static Quantity kilograms(long n) {
        return new Quantity(n, ItemRef.iid(UnitVocabulary.Kilogram.KEY), 1L);
    }

    /**
     * Typed view over an existing Body whose head is the Quantity archetype.
     * Reads the magnitude and unit-exponent bindings off the body and rebuilds
     * a Quantity with those components.  Bindings other than the magnitude or
     * unit exponents are dropped from the view; full-fidelity round-trip
     * arrives with the head-IID dispatch registry tracked in
     * {@link Body#fromCborTree}.
     */
    public static Quantity from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Quantity q) return q;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the Quantity archetype: " + body.headRef());
        }
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        long magnitude = 0L;
        Map<ItemRef, Long> exponents = new LinkedHashMap<>();
        for (Binding b : body.bindings()) {
            if (b.role().equals(valueRole) && b.target() instanceof Long n) {
                magnitude = n;
            } else if (b.target() instanceof Long n) {
                exponents.put(b.role(), n);
            }
        }
        return new Quantity(magnitude, exponents);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The numeric magnitude from the Value binding, or zero if absent. */
    public long magnitude() {
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        for (Binding b : bindings) {
            if (b.role().equals(valueRole) && b.target() instanceof Long n) return n;
        }
        return 0L;
    }

    /**
     * The exponent of {@code unit} in this quantity's dimensional formula.
     * Returns zero when the unit isn't present (i.e., its exponent is implicitly
     * zero).
     */
    public long exponentOf(ItemRef unit) {
        Objects.requireNonNull(unit, "unit");
        for (Binding b : bindings) {
            if (b.role().equals(unit) && b.target() instanceof Long n) return n;
        }
        return 0L;
    }

    /** The full dimensional formula as a {role-IID → exponent} map. */
    public Map<ItemRef, Long> unitExponents() {
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        Map<ItemRef, Long> result = new LinkedHashMap<>();
        for (Binding b : bindings) {
            if (b.role().equals(valueRole)) continue;
            if (b.target() instanceof Long n) result.put(b.role(), n);
        }
        return result;
    }

    /** True when the dimensional formula is empty (no unit bindings). */
    public boolean isDimensionless() {
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        for (Binding b : bindings) {
            if (!b.role().equals(valueRole)) return false;
        }
        return true;
    }

    // ==================================================================================
    // Dimensional analysis
    // ==================================================================================

    /**
     * True when {@code other} has the same dimensional formula as this — i.e.,
     * every unit appears with the same exponent in both.  Required for Add /
     * Subtract / Compare.  Does not check magnitudes.
     */
    public boolean dimensionallyMatches(Quantity other) {
        Objects.requireNonNull(other, "other");
        return unitExponents().equals(other.unitExponents());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(magnitude());
        Map<ItemRef, Long> units = unitExponents();
        if (!units.isEmpty()) {
            sb.append(' ');
            boolean first = true;
            for (Map.Entry<ItemRef, Long> e : units.entrySet()) {
                if (!first) sb.append('·');
                sb.append('@').append(e.getKey().encodeText());
                if (e.getValue() != 1L) sb.append('^').append(e.getValue());
                first = false;
            }
        }
        return sb.toString();
    }
}
