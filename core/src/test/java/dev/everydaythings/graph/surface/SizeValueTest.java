package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.value.Unit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for SizeValue — parsing, unit conversions via
 * RenderContext, and weight mapping across all supported units.
 */
@DisplayName("SizeValue")
class SizeValueTest {

    /** Shared in-memory librarian for unit resolution. */
    private static final LibrarianHandle LIB = LibrarianHandle.inMemory();

    /** TUI context for conversion tests. */
    private static final RenderContext TUI = RenderContext.tui(LIB);

    /** FX/GUI context for pixel conversion tests. */
    private static final RenderContext GUI = RenderContext.gui(LIB);

    @AfterAll
    static void tearDown() {
        LIB.close();
    }

    // ==================================================================================
    // Parsing (no RenderContext needed)
    // ==================================================================================

    @Nested
    @DisplayName("parse()")
    class Parsing {

        @ParameterizedTest(name = "\"{0}\" → value={1}, unit={2}")
        @CsvSource({
            "1px,    1.0, px",
            "200px,  200.0, px",
            "0.5px,  0.5, px",
            "1em,    1.0, em",
            "2.5em,  2.5, em",
            "16em,   16.0, em",
            "1ch,    1.0, ch",
            "40ch,   40.0, ch",
            "0.5ch,  0.5, ch",
            "1rem,   1.0, rem",
            "2rem,   2.0, rem",
            "1ln,    1.0, ln",
            "10ln,   10.0, ln",
            "50%,    50.0, %",
            "100%,   100.0, %",
        })
        @DisplayName("parses valid size strings")
        void parsesValidSizes(String input, double expectedValue, String expectedUnit) {
            SizeValue sv = SizeValue.parse(input);

            assertThat(sv).isNotNull();
            assertThat(sv.value()).isCloseTo(expectedValue, within(0.001));
            assertThat(sv.unit()).isEqualTo(expectedUnit);
        }

        @Test
        @DisplayName("plain number defaults to px")
        void plainNumberDefaultsToPx() {
            SizeValue sv = SizeValue.parse("42");

            assertThat(sv).isNotNull();
            assertThat(sv.value()).isCloseTo(42.0, within(0.001));
            assertThat(sv.unit()).isEqualTo("px");
        }

        @Test
        @DisplayName("decimal plain number defaults to px")
        void decimalPlainNumber() {
            SizeValue sv = SizeValue.parse("3.5");

            assertThat(sv).isNotNull();
            assertThat(sv.value()).isCloseTo(3.5, within(0.001));
            assertThat(sv.unit()).isEqualTo("px");
        }

        @Test
        @DisplayName("negative value parses correctly")
        void negativeValue() {
            SizeValue sv = SizeValue.parse("-2px");

            assertThat(sv).isNotNull();
            assertThat(sv.value()).isCloseTo(-2.0, within(0.001));
            assertThat(sv.unit()).isEqualTo("px");
        }

        @Test
        @DisplayName("case-insensitive units")
        void caseInsensitive() {
            SizeValue sv = SizeValue.parse("10PX");

            assertThat(sv).isNotNull();
            assertThat(sv.unit()).isEqualTo("px");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "abc", "pxem", "px10"})
        @DisplayName("invalid inputs return null")
        void invalidInputs(String input) {
            assertThat(SizeValue.parse(input)).isNull();
        }

        @Test
        @DisplayName("trims whitespace before parsing")
        void trimsWhitespace() {
            SizeValue sv = SizeValue.parse("  12em  ");

            assertThat(sv).isNotNull();
            assertThat(sv.value()).isCloseTo(12.0, within(0.001));
            assertThat(sv.unit()).isEqualTo("em");
        }
    }

    // ==================================================================================
    // toPixels(RenderContext) — TUI defaults
    // ==================================================================================

    @Nested
    @DisplayName("toPixels(RenderContext) — TUI")
    class ToPixelsTui {

        @Test
        @DisplayName("px is identity")
        void pxIsIdentity() {
            assertThat(SizeValue.parse("100px").toPixels(TUI)).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("1em = 10px (TUI: block-character-aligned)")
        void emToPixels() {
            assertThat(SizeValue.parse("1em").toPixels(TUI)).isCloseTo(10.0, within(0.001));
            assertThat(SizeValue.parse("2em").toPixels(TUI)).isCloseTo(20.0, within(0.001));
            assertThat(SizeValue.parse("0.5em").toPixels(TUI)).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("1ch = 10px (TUI: block-character-aligned)")
        void chToPixels() {
            assertThat(SizeValue.parse("1ch").toPixels(TUI)).isCloseTo(10.0, within(0.001));
            assertThat(SizeValue.parse("2ch").toPixels(TUI)).isCloseTo(20.0, within(0.001));
            assertThat(SizeValue.parse("0.5ch").toPixels(TUI)).isCloseTo(5.0, within(0.001));
        }

        @Test
        @DisplayName("1rem = 10px (TUI: block-character-aligned)")
        void remToPixels() {
            assertThat(SizeValue.parse("1rem").toPixels(TUI)).isCloseTo(10.0, within(0.001));
            assertThat(SizeValue.parse("2rem").toPixels(TUI)).isCloseTo(20.0, within(0.001));
        }

        @Test
        @DisplayName("1ln = 10px (TUI: block-character-aligned)")
        void lnToPixels() {
            assertThat(SizeValue.parse("1ln").toPixels(TUI)).isCloseTo(10.0, within(0.001));
            assertThat(SizeValue.parse("3ln").toPixels(TUI)).isCloseTo(30.0, within(0.001));
        }

        @Test
        @DisplayName("percentage passes through as raw value")
        void percentagePassThrough() {
            assertThat(SizeValue.parse("50%").toPixels(TUI)).isCloseTo(50.0, within(0.001));
        }
    }

    // ==================================================================================
    // toPixels(RenderContext) — FX defaults
    // ==================================================================================

    @Nested
    @DisplayName("toPixels(RenderContext) — FX")
    class ToPixelsFx {

        @Test
        @DisplayName("px is identity")
        void pxIdentity() {
            assertThat(SizeValue.parse("100px").toPixels(GUI)).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("em uses FX font size (16px)")
        void emScalesWithFontSize() {
            assertThat(SizeValue.parse("1em").toPixels(GUI)).isCloseTo(16.0, within(0.001));
            assertThat(SizeValue.parse("2em").toPixels(GUI)).isCloseTo(32.0, within(0.001));
        }

        @Test
        @DisplayName("ch uses FX ratio (8.8px per ch)")
        void chScalesWithFontSize() {
            assertThat(SizeValue.parse("1ch").toPixels(GUI)).isCloseTo(8.8, within(0.001));
            assertThat(SizeValue.parse("10ch").toPixels(GUI)).isCloseTo(88.0, within(0.001));
        }

        @Test
        @DisplayName("rem always uses root font size (16px)")
        void remUsesRootSize() {
            assertThat(SizeValue.parse("1rem").toPixels(GUI)).isCloseTo(16.0, within(0.001));
            assertThat(SizeValue.parse("2rem").toPixels(GUI)).isCloseTo(32.0, within(0.001));
        }

        @Test
        @DisplayName("ln uses FX line height (19.2px)")
        void lnScalesWithFontSize() {
            assertThat(SizeValue.parse("1ln").toPixels(GUI)).isCloseTo(19.2, within(0.001));
        }
    }

    // ==================================================================================
    // toColumns(RenderContext)
    // ==================================================================================

    @Nested
    @DisplayName("toColumns(RenderContext)")
    class ToColumns {

        @Test
        @DisplayName("ch maps 1:1 to columns")
        void chDirectMapping() {
            assertThat(SizeValue.parse("1ch").toColumns(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("40ch").toColumns(TUI)).isEqualTo(40);
            assertThat(SizeValue.parse("80ch").toColumns(TUI)).isEqualTo(80);
        }

        @Test
        @DisplayName("em maps ×2 to columns (font height ≈ 2× char width)")
        void emDoubleMapping() {
            assertThat(SizeValue.parse("1em").toColumns(TUI)).isEqualTo(2);
            assertThat(SizeValue.parse("5em").toColumns(TUI)).isEqualTo(10);
            assertThat(SizeValue.parse("20em").toColumns(TUI)).isEqualTo(40);
        }

        @Test
        @DisplayName("fractional em uses ceiling")
        void fractionalEm() {
            assertThat(SizeValue.parse("0.5em").toColumns(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("1.5em").toColumns(TUI)).isEqualTo(3);
            assertThat(SizeValue.parse("2.5em").toColumns(TUI)).isEqualTo(5);
        }

        @Test
        @DisplayName("px divides by 8 (8px per char cell), ceiling")
        void pxDivBy8() {
            assertThat(SizeValue.parse("8px").toColumns(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("16px").toColumns(TUI)).isEqualTo(2);
            assertThat(SizeValue.parse("24px").toColumns(TUI)).isEqualTo(3);
            assertThat(SizeValue.parse("80px").toColumns(TUI)).isEqualTo(10);
        }

        @Test
        @DisplayName("px uses ceiling for sub-cell values")
        void pxCeiling() {
            assertThat(SizeValue.parse("1px").toColumns(TUI)).isEqualTo(1);   // ceil(1/8) = 1
            assertThat(SizeValue.parse("9px").toColumns(TUI)).isEqualTo(2);   // ceil(9/8) = 2
            assertThat(SizeValue.parse("7px").toColumns(TUI)).isEqualTo(1);   // ceil(7/8) = 1
        }

        @Test
        @DisplayName("rem maps ×2 to columns (same as em)")
        void remDoubleMapping() {
            assertThat(SizeValue.parse("1rem").toColumns(TUI)).isEqualTo(2);
            assertThat(SizeValue.parse("5rem").toColumns(TUI)).isEqualTo(10);
        }

        @Test
        @DisplayName("ln maps 1:1 to columns (cross-axis unit)")
        void lnDirectMapping() {
            assertThat(SizeValue.parse("1ln").toColumns(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("10ln").toColumns(TUI)).isEqualTo(10);
        }

        @Test
        @DisplayName("percentage passes through as ceiling value")
        void percentPassThrough() {
            assertThat(SizeValue.parse("50%").toColumns(TUI)).isEqualTo(50);
        }
    }

    // ==================================================================================
    // toRows(RenderContext)
    // ==================================================================================

    @Nested
    @DisplayName("toRows(RenderContext)")
    class ToRows {

        @Test
        @DisplayName("ln maps 1:1 to rows")
        void lnDirectMapping() {
            assertThat(SizeValue.parse("1ln").toRows(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("5ln").toRows(TUI)).isEqualTo(5);
            assertThat(SizeValue.parse("10ln").toRows(TUI)).isEqualTo(10);
        }

        @Test
        @DisplayName("em maps 1:1 to rows (1em ≈ 1 line height)")
        void emDirectMapping() {
            assertThat(SizeValue.parse("1em").toRows(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("3em").toRows(TUI)).isEqualTo(3);
        }

        @Test
        @DisplayName("ch maps ÷2 to rows, ceiling")
        void chHalfMapping() {
            assertThat(SizeValue.parse("2ch").toRows(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("4ch").toRows(TUI)).isEqualTo(2);
            assertThat(SizeValue.parse("10ch").toRows(TUI)).isEqualTo(5);
        }

        @Test
        @DisplayName("fractional ch uses ceiling")
        void fractionalChCeiling() {
            assertThat(SizeValue.parse("1ch").toRows(TUI)).isEqualTo(1);    // ceil(0.5) = 1
            assertThat(SizeValue.parse("3ch").toRows(TUI)).isEqualTo(2);    // ceil(1.5) = 2
        }

        @Test
        @DisplayName("px divides by 16 (16px per row), ceiling")
        void pxDivBy16() {
            assertThat(SizeValue.parse("16px").toRows(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("32px").toRows(TUI)).isEqualTo(2);
            assertThat(SizeValue.parse("48px").toRows(TUI)).isEqualTo(3);
        }

        @Test
        @DisplayName("px sub-row values use ceiling")
        void pxCeiling() {
            assertThat(SizeValue.parse("1px").toRows(TUI)).isEqualTo(1);    // ceil(1/16) = 1
            assertThat(SizeValue.parse("17px").toRows(TUI)).isEqualTo(2);   // ceil(17/16) = 2
        }

        @Test
        @DisplayName("rem maps 1:1 to rows (same as em)")
        void remDirectMapping() {
            assertThat(SizeValue.parse("1rem").toRows(TUI)).isEqualTo(1);
            assertThat(SizeValue.parse("4rem").toRows(TUI)).isEqualTo(4);
        }
    }

    // NOTE: toWeight tests moved to ui module (BoxDrawing-specific)

    // ==================================================================================
    // toString()
    // ==================================================================================

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("integer value omits decimal point")
        void integerValueNoDecimal() {
            assertThat(SizeValue.parse("10px").toString()).isEqualTo("10px");
            assertThat(SizeValue.parse("1em").toString()).isEqualTo("1em");
            assertThat(SizeValue.parse("40ch").toString()).isEqualTo("40ch");
        }

        @Test
        @DisplayName("decimal value preserves decimal")
        void decimalValuePreservesDecimal() {
            assertThat(SizeValue.parse("0.5em").toString()).isEqualTo("0.5em");
            assertThat(SizeValue.parse("2.5ch").toString()).isEqualTo("2.5ch");
        }
    }

    // ==================================================================================
    // Cross-Unit Equivalences
    // ==================================================================================

    @Nested
    @DisplayName("Cross-Unit Equivalences")
    class CrossUnitEquivalences {

        @Test
        @DisplayName("1em in columns (2) equals 2ch in columns (2)")
        void emAndChColumnsEquivalent() {
            assertThat(SizeValue.parse("1em").toColumns(TUI))
                    .isEqualTo(SizeValue.parse("2ch").toColumns(TUI));
        }

        @Test
        @DisplayName("16px in columns (2) equals 1em in columns (2)")
        void pxAndEmColumnsEquivalent() {
            assertThat(SizeValue.parse("16px").toColumns(TUI))
                    .isEqualTo(SizeValue.parse("1em").toColumns(TUI));
        }

        @Test
        @DisplayName("8px in columns (1) equals 1ch in columns (1)")
        void pxAndChColumnsEquivalent() {
            assertThat(SizeValue.parse("8px").toColumns(TUI))
                    .isEqualTo(SizeValue.parse("1ch").toColumns(TUI));
        }

        @Test
        @DisplayName("1em in rows (1) equals 1ln in rows (1)")
        void emAndLnRowsEquivalent() {
            assertThat(SizeValue.parse("1em").toRows(TUI))
                    .isEqualTo(SizeValue.parse("1ln").toRows(TUI));
        }

        @Test
        @DisplayName("16px in rows (1) equals 1ln in rows (1)")
        void pxAndLnRowsEquivalent() {
            assertThat(SizeValue.parse("16px").toRows(TUI))
                    .isEqualTo(SizeValue.parse("1ln").toRows(TUI));
        }

        @Test
        @DisplayName("rem and em have same pixel values")
        void remAndEmSamePixels() {
            assertThat(SizeValue.parse("1rem").toPixels(TUI))
                    .isCloseTo(SizeValue.parse("1em").toPixels(TUI), within(0.001));
        }

        @Test
        @DisplayName("1ch equals 1em in TUI pixels (both 10px)")
        void oneChEqualsOneEm() {
            assertThat(SizeValue.parse("1ch").toPixels(TUI))
                    .isCloseTo(SizeValue.parse("1em").toPixels(TUI), within(0.001));
        }
    }

    // ==================================================================================
    // Custom RenderMetrics
    // ==================================================================================

    @Nested
    @DisplayName("Custom RenderMetrics")
    class CustomMetrics {

        @Test
        @DisplayName("custom column scale changes conversion result")
        void customColumnScale() {
            RenderMetrics custom = RenderMetrics.builder()
                    .column(Unit.CHARACTER_WIDTH, 2.0)  // 1ch = 2 columns (double-wide)
                    .build();
            RenderContext ctx = RenderContext.builder()
                    .librarian(LIB)
                    .renderMetrics(custom)
                    .build();

            assertThat(SizeValue.parse("10ch").toColumns(ctx)).isEqualTo(20);
        }

        @Test
        @DisplayName("custom pixel scale changes weight mapping")
        void customPixelScale() {
            RenderMetrics custom = RenderMetrics.builder()
                    .pixel(Unit.PIXEL, 1.0)
                    .pixel(Unit.CHARACTER_WIDTH, 4.0)  // 1ch = 4px instead of 8px
                    .build();
            RenderContext ctx = RenderContext.builder()
                    .librarian(LIB)
                    .renderMetrics(custom)
                    .build();

            // With custom metrics: 1ch = 4px instead of default 8px
            assertThat(SizeValue.parse("1ch").toPixels(ctx)).isEqualTo(4.0);
        }
    }

    // ==================================================================================
    // Failure Cases — No Fallbacks
    // ==================================================================================

    @Nested
    @DisplayName("Failure Cases")
    class FailureCases {

        @Test
        @DisplayName("unknown unit symbol throws IllegalArgumentException")
        void unknownUnitThrows() {
            SizeValue sv = new SizeValue(10.0, "zz");

            assertThatThrownBy(() -> sv.toColumns(TUI))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zz");
        }

        @Test
        @DisplayName("unit not in metrics throws IllegalArgumentException")
        void unitNotInMetricsThrows() {
            RenderMetrics empty = RenderMetrics.builder().build();
            RenderContext ctx = RenderContext.builder()
                    .librarian(LIB)
                    .renderMetrics(empty)
                    .build();

            SizeValue sv = SizeValue.parse("10ch");

            assertThatThrownBy(() -> sv.toColumns(ctx))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null context throws NullPointerException")
        void nullContextThrows() {
            SizeValue sv = SizeValue.parse("10px");

            assertThatThrownBy(() -> sv.toColumns(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
