package dev.everydaythings.graph.seed;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.mapdb.MapDBItemStore;
import dev.everydaythings.graph.library.SeedVocabulary;
import dev.everydaythings.graph.value.Dimension;
import dev.everydaythings.graph.value.Unit;
import dev.everydaythings.graph.value.ValueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SeedVocabulary and @Item.Seed annotation.
 */
@Tag("slow")
class SeedVocabularyTest {

    private static ItemStore store;

    @BeforeAll
    static void bootstrap() {
        store = MapDBItemStore.memory();
        SeedVocabulary.bootstrap(store);
    }

    // ==================================================================================
    // Helper Methods
    // ==================================================================================

    private boolean hasManifest(ItemID iid) {
        return store.manifests(iid).findFirst().isPresent();
    }

    private boolean hasImplementedByRelation(ItemID typeId) {
        return store.frameBodies()
                .filter(r -> r.predicate().equals(CoreVocabulary.ImplementedBy.IID))
                .anyMatch(r -> typeId.equals(r.bindingId(ItemID.fromString("cg.role:theme"))));
    }

    private List<Manifest> allManifestsOfType(ItemID typeId) {
        // PHASE 6: type is now an implementation binding, not a sememe IID.
        // Match by checking if the implementation class has @Implements pointing to typeId.
        List<Manifest> result = new ArrayList<>();
        for (Manifest m : store.manifests(null).toList()) {
            Class<?> implClass = m.implementationClass();
            if (implClass != null) {
                try {
                    ItemID implTypeId = Item.idOf(implClass);
                    if (typeId.equals(implTypeId)) {
                        result.add(m);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Class doesn't have @Implements
                }
            }
        }
        return result;
    }

    // ==================================================================================
    // Tests
    // ==================================================================================

    @Test
    void bootstrapIncludesDimensions() {
        // Dimension type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(ItemID.fromString(Dimension.KEY))).isTrue();

        // Dimension seed instances should have manifests
        assertThat(hasManifest(Dimension.Length.IID)).isTrue();
        assertThat(hasManifest(Dimension.Time.IID)).isTrue();
        assertThat(hasManifest(Dimension.Mass.IID)).isTrue();
    }

    @Test
    void bootstrapIncludesSememes() {
        // Sememe type gets IMPLEMENTED_BY
        assertThat(hasImplementedByRelation(ItemID.fromString(Sememe.KEY))).isTrue();

        // Core vocabulary seeds should have manifests
        assertThat(hasManifest(CoreVocabulary.Author.IID)).isTrue();
        assertThat(hasManifest(CoreVocabulary.Title.IID)).isTrue();
        assertThat(hasManifest(CoreVocabulary.Description.IID)).isTrue();
    }

    @Test
    void seedItemsAreDiscoverableByType() {
        // All Dimension seeds
        var dimensions = allManifestsOfType(ItemID.fromString(Dimension.KEY));
        assertThat(dimensions).hasSizeGreaterThanOrEqualTo(7); // 7 SI base dimensions

        // Sememe seeds (all vocabulary classes share the Sememe type)
        var sememes = allManifestsOfType(ItemID.fromString(Sememe.KEY));
        assertThat(sememes).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void bootstrapIncludesUnits() {
        // Unit type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(ItemID.fromString(Unit.KEY))).isTrue();

        // Core units should have manifests
        assertThat(hasManifest(Unit.Meter.IID)).isTrue();
        assertThat(hasManifest(Unit.Second.IID)).isTrue();
        assertThat(hasManifest(Unit.Kilogram.IID)).isTrue();
        assertThat(hasManifest(Unit.Inch.IID)).isTrue();

        // Derived units
        assertThat(hasManifest(Unit.Newton.IID)).isTrue();
        assertThat(hasManifest(Unit.Joule.IID)).isTrue();
    }

    @Test
    void unitsHaveCorrectDimensions() {
        // Length units should have LENGTH dimension
        Unit meter = Unit.lookupSeed(Unit.Meter.IID);
        Unit second = Unit.lookupSeed(Unit.Second.IID);
        Unit mps = Unit.lookupSeed(Unit.MeterPerSecond.IID);

        assertThat(meter.hasDimension(Dimension.Length.SEED)).isTrue();
        assertThat(meter.hasDimension(Dimension.Time.SEED)).isFalse();

        // Time units should have TIME dimension
        assertThat(second.hasDimension(Dimension.Time.SEED)).isTrue();
        assertThat(second.hasDimension(Dimension.Length.SEED)).isFalse();

        // Velocity has both LENGTH and TIME
        assertThat(mps.hasDimension(Dimension.Length.SEED)).isTrue();
        assertThat(mps.hasDimension(Dimension.Time.SEED)).isTrue();
        assertThat(mps.exponent(Dimension.Length.SEED)).isEqualTo(1);
        assertThat(mps.exponent(Dimension.Time.SEED)).isEqualTo(-1);
    }

    @Test
    void unitsCanBeConverted() {
        // 1000 mm = 1 m
        Unit mm = Unit.lookupSeed(Unit.Millimeter.IID);
        double meters = mm.convert(1000, Unit.lookupSeed(Unit.Meter.IID));
        assertThat(meters).isEqualTo(1.0);

        // 3600 seconds = 1 hour
        double hours = Unit.lookupSeed(Unit.Second.IID).convert(3600, Unit.lookupSeed(Unit.Hour.IID));
        assertThat(hours).isEqualTo(1.0);
    }

    @Test
    void unitsAreDiscoverableByType() {
        var units = allManifestsOfType(ItemID.fromString(Unit.KEY));
        assertThat(units).hasSizeGreaterThanOrEqualTo(15); // length + time + mass + derived
    }

    @Test
    void bootstrapIncludesValueTypes() {
        // ValueType type should have IMPLEMENTED_BY relation
        assertThat(hasImplementedByRelation(ItemID.fromString(ValueType.KEY))).isTrue();

        // Core value types should have manifests
        assertThat(hasManifest(ValueType.BooleanType.IID)).isTrue();
        assertThat(hasManifest(ValueType.TextType.IID)).isTrue();
        assertThat(hasManifest(ValueType.DecimalType.IID)).isTrue();
        assertThat(hasManifest(ValueType.RationalType.IID)).isTrue();
        assertThat(hasManifest(ValueType.CountType.IID)).isTrue();
        assertThat(hasManifest(ValueType.IntegerType.IID)).isTrue();
        assertThat(hasManifest(ValueType.EndpointType.IID)).isTrue();
        assertThat(hasManifest(ValueType.IpType.IID)).isTrue();
        assertThat(hasManifest(ValueType.QuantityType.IID)).isTrue();
    }

    @Test
    void valueTypesAreDiscoverableByType() {
        var valueTypes = allManifestsOfType(ItemID.fromString(ValueType.KEY));
        assertThat(valueTypes).hasSizeGreaterThanOrEqualTo(12);
    }

    @Test
    void literalTypeConstantsMatchValueTypeSeeds() {
        // Verify that Literal.TYPE_* constants resolve to actual ValueType seeds
        assertThat(Literal.TYPE_TEXT).isEqualTo(ValueType.TextType.IID);
        assertThat(Literal.TYPE_BOOLEAN).isEqualTo(ValueType.BooleanType.IID);
        assertThat(Literal.TYPE_INTEGER).isEqualTo(ValueType.IntegerType.IID);
        assertThat(Literal.TYPE_INSTANT).isEqualTo(ValueType.InstantType.IID);

        // Verify these seeds have manifests in the store
        assertThat(hasManifest(Literal.TYPE_TEXT)).isTrue();
        assertThat(hasManifest(Literal.TYPE_BOOLEAN)).isTrue();
        assertThat(hasManifest(Literal.TYPE_INTEGER)).isTrue();
        assertThat(hasManifest(Literal.TYPE_INSTANT)).isTrue();
    }

}
