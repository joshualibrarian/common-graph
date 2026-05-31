package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.operator.math.Multiply;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.Quantity;
import dev.everydaythings.graph.value.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code qualities} package — verify the seed
 * vocabularies bootstrap correctly with their declared property bindings.
 */
class QualitiesVocabularyTest {

    static Librarian lib;

    @BeforeAll
    static void bootstrap() {
        lib = Librarian.inMemory();
        lib.bootstrap();
    }

    // ==================================================================================
    // DimensionVocabulary
    // ==================================================================================

    @Nested
    @DisplayName("DimensionVocabulary")
    class Dimensions {

        @Test
        @DisplayName("Dimension archetype is persisted with head = Archetype")
        void dimensionMetaArchetype() {
            Manifest m = manifestFor(ItemRef.iid(DimensionVocabulary.Dimension.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(CoreVocabulary.Archetype.KEY)));
        }

        @Test
        @DisplayName("each SI base dimension has head = Dimension")
        void siBaseDimensionsHeadDimension() {
            ItemRef expectedHead = ItemRef.of(ItemRef.iid(DimensionVocabulary.Dimension.KEY));
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.Length.KEY)).body().head())
                    .isEqualTo(expectedHead);
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.Time.KEY)).body().head())
                    .isEqualTo(expectedHead);
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.Mass.KEY)).body().head())
                    .isEqualTo(expectedHead);
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.ElectricCurrent.KEY)).body().head())
                    .isEqualTo(expectedHead);
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.Temperature.KEY)).body().head())
                    .isEqualTo(expectedHead);
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.Amount.KEY)).body().head())
                    .isEqualTo(expectedHead);
            assertThat(manifestFor(ItemRef.iid(DimensionVocabulary.LuminousIntensity.KEY)).body().head())
                    .isEqualTo(expectedHead);
        }
    }

    // ==================================================================================
    // UnitVocabulary
    // ==================================================================================

    @Nested
    @DisplayName("UnitVocabulary")
    class Units {

        @Test
        @DisplayName("Unit archetype is persisted with head = Archetype")
        void unitMetaArchetype() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Unit.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(CoreVocabulary.Archetype.KEY)));
        }

        @Test
        @DisplayName("Meter manifest carries Symbol=\"m\" via @Seed.Property")
        void meterSymbol() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Meter.KEY));
            Optional<Binding> sym = m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.Symbol.KEY)));
            assertThat(sym).isPresent();
            assertThat(sym.get().target()).isEqualTo("m");
        }

        @Test
        @DisplayName("Meter has Dimension:[Length] → 1")
        void meterDimensionLength() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Meter.KEY));
            // The role-key for a unit's dimensional formula entry is the Dimension
            // archetype itself (DimensionVocabulary.Dimension), with the specific
            // dimension sememe (Length, Time, …) as a qualifier.
            CompoundKey key = CompoundKey.of(
                    ItemRef.iid(DimensionVocabulary.Dimension.KEY),
                    ItemRef.iid(DimensionVocabulary.Length.KEY));
            Optional<Binding> dim = m.binding(key);
            assertThat(dim).isPresent();
            assertThat(dim.get().target()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Centimeter has ScaleDenominator → 100")
        void centimeterScale() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Centimeter.KEY));
            Optional<Binding> denom = m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.ScaleDenominator.KEY)));
            assertThat(denom).isPresent();
            assertThat(denom.get().target()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Millimeter has ScaleDenominator → 1000")
        void millimeterScale() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Millimeter.KEY));
            Optional<Binding> denom = m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.ScaleDenominator.KEY)));
            assertThat(denom).isPresent();
            assertThat(denom.get().target()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("Second is dimensioned on Time, not Length")
        void secondDimensionTime() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Second.KEY));
            CompoundKey timeKey = CompoundKey.of(
                    ItemRef.iid(DimensionVocabulary.Dimension.KEY),
                    ItemRef.iid(DimensionVocabulary.Time.KEY));
            CompoundKey lengthKey = CompoundKey.of(
                    ItemRef.iid(DimensionVocabulary.Dimension.KEY),
                    ItemRef.iid(DimensionVocabulary.Length.KEY));
            assertThat(m.binding(timeKey)).isPresent();
            assertThat(m.binding(lengthKey)).isEmpty();
        }

        @Test
        @DisplayName("Kilogram is dimensioned on Mass and has symbol \"kg\"")
        void kilogramShape() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Kilogram.KEY));
            assertThat(m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.Symbol.KEY)))
                    .orElseThrow().target()).isEqualTo("kg");
            CompoundKey massKey = CompoundKey.of(
                    ItemRef.iid(DimensionVocabulary.Dimension.KEY),
                    ItemRef.iid(DimensionVocabulary.Mass.KEY));
            assertThat(m.binding(massKey)).isPresent();
        }

        @Test
        @DisplayName("Gram has scale 1/1000 of Kilogram")
        void gramScale() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Gram.KEY));
            assertThat(m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.ScaleNumerator.KEY)))
                    .orElseThrow().target()).isEqualTo(1L);
            assertThat(m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.ScaleDenominator.KEY)))
                    .orElseThrow().target()).isEqualTo(1000L);
        }
    }

    // ==================================================================================
    // Variable + SpatialVocabulary + TypographyVocabulary
    // ==================================================================================

    @Nested
    @DisplayName("Variable archetype and concrete Variable seeds")
    class Variables {

        @Test
        @DisplayName("Variable archetype exists with head = Archetype")
        void variableMetaArchetype() {
            Manifest m = manifestFor(ItemRef.iid(CoreVocabulary.Variable.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(CoreVocabulary.Archetype.KEY)));
        }

        @Test
        @DisplayName("DevicePixelSize is an instance of Variable")
        void devicePixelSizeHeadVariable() {
            Manifest m = manifestFor(ItemRef.iid(SpatialVocabulary.DevicePixelSize.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(CoreVocabulary.Variable.KEY)));
        }

        @Test
        @DisplayName("BaseFontSize is an instance of Variable")
        void baseFontSizeHeadVariable() {
            Manifest m = manifestFor(ItemRef.iid(TypographyVocabulary.BaseFontSize.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(CoreVocabulary.Variable.KEY)));
        }
    }

    // ==================================================================================
    // Variable-bearing units (Pixel's inline scale expression, Em's direct ref)
    // ==================================================================================

    @Nested
    @DisplayName("Quantity archetype")
    class Quantities {

        @Test
        @DisplayName("Quantity archetype exists with head = Value")
        void quantityMetaArchetype() {
            Manifest m = manifestFor(ItemRef.iid(Quantity.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(Value.KEY)));
        }
    }

    @Nested
    @DisplayName("Variable-bearing units: DevicePixel (inline expression) and Em (direct ref)")
    class VariableUnits {

        @Test
        @DisplayName("DevicePixel has Symbol=\"dpx\" and dimension Length")
        void devicePixelBasics() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.DevicePixel.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(UnitVocabulary.Unit.KEY)));
            assertThat(m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.Symbol.KEY)))
                    .orElseThrow().target()).isEqualTo("dpx");
            CompoundKey lengthKey = CompoundKey.of(
                    ItemRef.iid(DimensionVocabulary.Dimension.KEY),
                    ItemRef.iid(DimensionVocabulary.Length.KEY));
            assertThat(m.binding(lengthKey).orElseThrow().target()).isEqualTo(1L);
        }

        @Test
        @DisplayName("DevicePixel.EquivalentInBase is an inline Multiply body")
        void devicePixelEquivalentIsInlineMultiply() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.DevicePixel.KEY));
            Object target = m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.EquivalentInBase.KEY)))
                    .orElseThrow().target();
            assertThat(target).isInstanceOf(Body.class);
            Body multiply = (Body) target;
            assertThat(multiply.head()).isEqualTo(ItemRef.of(ItemRef.iid(Multiply.KEY)));
        }

        @Test
        @DisplayName("DevicePixel's Multiply expression has DevicePixelSize and a Quantity(1, Meter) as operands")
        void devicePixelMultiplyOperands() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.DevicePixel.KEY));
            Body multiply = (Body) m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.EquivalentInBase.KEY)))
                    .orElseThrow().target();

            List<Binding> themes = multiply.bindings().stream()
                    .filter(b -> b.role().equals(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .toList();
            assertThat(themes).hasSize(2);

            // Operand at index 0: DevicePixelSize reference
            Optional<Binding> first = themes.stream()
                    .filter(b -> Long.valueOf(0L).equals(b.index())).findFirst();
            assertThat(first).isPresent();
            assertThat(first.get().target()).isEqualTo(ItemRef.iid(SpatialVocabulary.DevicePixelSize.KEY));

            // Operand at index 1: an inline Quantity{Value=1, @Meter=1} body (tiny-shape)
            Optional<Binding> second = themes.stream()
                    .filter(b -> Long.valueOf(1L).equals(b.index())).findFirst();
            assertThat(second).isPresent();
            assertThat(second.get().target()).isInstanceOf(Body.class);
            Body quantity = (Body) second.get().target();
            assertThat(quantity.head()).isEqualTo(
                    ItemRef.of(ItemRef.iid(Quantity.KEY)));
            // Magnitude in the Value role
            assertThat(quantity.binding(CompoundKey.of(ItemRef.iid(ThematicRole.Value.KEY)))
                    .orElseThrow().target()).isEqualTo(1L);
            // Tiny-shape: @Meter as binding-role, exponent (1) as target
            assertThat(quantity.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.Meter.KEY)))
                    .orElseThrow().target()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Em has Symbol=\"em\" and EquivalentInBase=@BaseFontSize")
        void emShape() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Em.KEY));
            assertThat(m.body().head()).isEqualTo(ItemRef.of(ItemRef.iid(UnitVocabulary.Unit.KEY)));
            assertThat(m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.Symbol.KEY)))
                    .orElseThrow().target()).isEqualTo("em");
            assertThat(m.binding(CompoundKey.of(ItemRef.iid(UnitVocabulary.EquivalentInBase.KEY)))
                    .orElseThrow().target()).isEqualTo(ItemRef.iid(TypographyVocabulary.BaseFontSize.KEY));
        }

        @Test
        @DisplayName("Em is dimensioned on Length")
        void emDimensioned() {
            Manifest m = manifestFor(ItemRef.iid(UnitVocabulary.Em.KEY));
            CompoundKey lengthKey = CompoundKey.of(
                    ItemRef.iid(DimensionVocabulary.Dimension.KEY),
                    ItemRef.iid(DimensionVocabulary.Length.KEY));
            assertThat(m.binding(lengthKey).orElseThrow().target()).isEqualTo(1L);
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private Manifest manifestFor(ItemRef iid) {
        List<DatumRef> cids = lib.library().manifestCidsForItem(iid);
        assertThat(cids).as("no manifest persisted for %s", iid).isNotEmpty();
        return lib.fetchManifest(cids.get(0)).orElseThrow();
    }
}
