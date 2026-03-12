package dev.everydaythings.graph.ui.scene;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("SizeValue")
@Tag("slow")
class SizeValueTest {

    // ==================================================================================
    // Parsing
    // ==================================================================================

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        @DisplayName("parses physical units: in, cm, mm, pt")
        void parsesPhysicalUnits() {
            assertThat(SizeValue.parse("1in")).isEqualTo(new SizeValue(1, "in"));
            assertThat(SizeValue.parse("2.54cm")).isEqualTo(new SizeValue(2.54, "cm"));
            assertThat(SizeValue.parse("10mm")).isEqualTo(new SizeValue(10, "mm"));
            assertThat(SizeValue.parse("12pt")).isEqualTo(new SizeValue(12, "pt"));
        }

        @Test
        @DisplayName("parses viewport units: vw, vh")
        void parsesViewportUnits() {
            assertThat(SizeValue.parse("50vw")).isEqualTo(new SizeValue(50, "vw"));
            assertThat(SizeValue.parse("100vh")).isEqualTo(new SizeValue(100, "vh"));
        }

        @Test
        @DisplayName("parses existing units: px, em, ch, rem, ln, %")
        void parsesExistingUnits() {
            assertThat(SizeValue.parse("200px")).isEqualTo(new SizeValue(200, "px"));
            assertThat(SizeValue.parse("2em")).isEqualTo(new SizeValue(2, "em"));
            assertThat(SizeValue.parse("40ch")).isEqualTo(new SizeValue(40, "ch"));
            assertThat(SizeValue.parse("1.5rem")).isEqualTo(new SizeValue(1.5, "rem"));
            assertThat(SizeValue.parse("3ln")).isEqualTo(new SizeValue(3, "ln"));
            assertThat(SizeValue.parse("50%")).isEqualTo(new SizeValue(50, "%"));
        }

        @Test
        @DisplayName("plain number treated as px")
        void plainNumberAsPx() {
            assertThat(SizeValue.parse("42")).isEqualTo(new SizeValue(42, "px"));
        }

        @Test
        @DisplayName("null and blank return null")
        void nullAndBlank() {
            assertThat(SizeValue.parse(null)).isNull();
            assertThat(SizeValue.parse("")).isNull();
            assertThat(SizeValue.parse("   ")).isNull();
        }

        @Test
        @DisplayName("unsupported unit returns null")
        void unsupportedUnit() {
            assertThat(SizeValue.parse("10foo")).isNull();
        }

        @Test
        @DisplayName("parses 'auto' keyword")
        void parsesAuto() {
            assertThat(SizeValue.parse("auto")).isNotNull();
            assertThat(SizeValue.parse("auto")).isSameAs(SizeValue.AUTO);
            assertThat(SizeValue.parse("AUTO")).isSameAs(SizeValue.AUTO);
            assertThat(SizeValue.parse("  auto  ")).isSameAs(SizeValue.AUTO);
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    @Nested
    @DisplayName("Helpers")
    class Helpers {

        @Test
        @DisplayName("isPercentage returns true for %")
        void isPercentageTrue() {
            assertThat(SizeValue.parse("50%").isPercentage()).isTrue();
        }

        @Test
        @DisplayName("isPercentage returns false for other units")
        void isPercentageFalse() {
            assertThat(SizeValue.parse("50px").isPercentage()).isFalse();
            assertThat(SizeValue.parse("10vw").isPercentage()).isFalse();
        }

        @Test
        @DisplayName("isViewportRelative returns true for vw and vh")
        void isViewportRelativeTrue() {
            assertThat(SizeValue.parse("10vw").isViewportRelative()).isTrue();
            assertThat(SizeValue.parse("50vh").isViewportRelative()).isTrue();
        }

        @Test
        @DisplayName("isViewportRelative returns false for other units")
        void isViewportRelativeFalse() {
            assertThat(SizeValue.parse("50%").isViewportRelative()).isFalse();
            assertThat(SizeValue.parse("100px").isViewportRelative()).isFalse();
        }

        @Test
        @DisplayName("isAuto returns true for AUTO, false for others")
        void isAuto() {
            assertThat(SizeValue.AUTO.isAuto()).isTrue();
            assertThat(SizeValue.parse("auto").isAuto()).isTrue();
            assertThat(SizeValue.parse("200px").isAuto()).isFalse();
            assertThat(SizeValue.parse("50%").isAuto()).isFalse();
        }

        @Test
        @DisplayName("isPercentage returns false for auto")
        void isPercentageFalseForAuto() {
            assertThat(SizeValue.AUTO.isPercentage()).isFalse();
        }

        @Test
        @DisplayName("isAutoSpec checks raw spec strings")
        void isAutoSpec() {
            assertThat(SizeValue.isAutoSpec("auto")).isTrue();
            assertThat(SizeValue.isAutoSpec("AUTO")).isTrue();
            assertThat(SizeValue.isAutoSpec("  auto  ")).isTrue();
            assertThat(SizeValue.isAutoSpec("200px")).isFalse();
            assertThat(SizeValue.isAutoSpec(null)).isFalse();
            assertThat(SizeValue.isAutoSpec("")).isFalse();
        }
    }

    // ==================================================================================
    // toPixels
    // ==================================================================================

    @Nested
    @DisplayName("toPixels")
    class ToPixels {

        private RenderContext ctx(float viewportW, float viewportH, float dpi) {
            return RenderContext.builder()
                    .renderer(RenderContext.RENDERER_SKIA)
                    .viewportWidth(viewportW)
                    .viewportHeight(viewportH)
                    .dpi(dpi)
                    .build();
        }

        private RenderContext ctxWithFontSize(float fontSize) {
            return RenderContext.builder()
                    .renderer(RenderContext.RENDERER_SKIA)
                    .viewportWidth(800)
                    .viewportHeight(600)
                    .dpi(96)
                    .baseFontSize(fontSize)
                    .librarian(dev.everydaythings.graph.runtime.LibrarianHandle.inMemory())
                    .renderMetrics(RenderMetrics.gui(fontSize, fontSize * 0.55, fontSize * 1.2, fontSize))
                    .build();
        }

        @Test
        @DisplayName("percentage is a first-class unit (identity pass-through)")
        void percentagePassesThrough() {
            // % resolves like any other unit — the layout engine handles
            // context-dependent resolution against parent dimensions
            var sv = SizeValue.parse("50%");
            assertThat(sv.isPercentage()).isTrue();
        }

        @Test
        @DisplayName("resolves viewport units: vw")
        void resolvesVw() {
            var sv = SizeValue.parse("10vw");
            var result = sv.toPixels(ctx(800, 600, 96));
            assertThat(result).isCloseTo(80.0, within(0.01));
        }

        @Test
        @DisplayName("resolves viewport units: vh")
        void resolvesVh() {
            var sv = SizeValue.parse("50vh");
            var result = sv.toPixels(ctx(800, 600, 96));
            assertThat(result).isCloseTo(300.0, within(0.01));
        }

        @Test
        @DisplayName("resolves physical unit: in")
        void resolvesInch() {
            var sv = SizeValue.parse("1in");
            var result = sv.toPixels(ctx(800, 600, 96));
            assertThat(result).isCloseTo(96.0, within(0.01));
        }

        @Test
        @DisplayName("resolves physical unit: cm")
        void resolvesCm() {
            var sv = SizeValue.parse("2.54cm");
            var result = sv.toPixels(ctx(800, 600, 96));
            assertThat(result).isCloseTo(96.0, within(0.1));
        }

        @Test
        @DisplayName("resolves physical unit: mm")
        void resolvesMm() {
            var sv = SizeValue.parse("25.4mm");
            var result = sv.toPixels(ctx(800, 600, 96));
            assertThat(result).isCloseTo(96.0, within(0.1));
        }

        @Test
        @DisplayName("resolves physical unit: pt")
        void resolvesPt() {
            var sv = SizeValue.parse("72pt");
            var result = sv.toPixels(ctx(800, 600, 96));
            assertThat(result).isCloseTo(96.0, within(0.01));
        }

        @Test
        @DisplayName("DPI scales physical units")
        void dpiScalesPhysical() {
            var sv = SizeValue.parse("1in");
            // At 192 DPI (2x retina)
            var result = sv.toPixels(ctx(800, 600, 192));
            assertThat(result).isCloseTo(192.0, within(0.01));
        }

        @Test
        @DisplayName("em resolves to baseFontSize=16")
        void emResolvesAtDefault() {
            var sv = SizeValue.parse("0.5em");
            var result = sv.toPixels(ctxWithFontSize(16));
            assertThat(result).isCloseTo(8.0, within(0.01));
        }

        @Test
        @DisplayName("em resolves to baseFontSize=15")
        void emResolvesAtFifteen() {
            var sv = SizeValue.parse("0.5em");
            var result = sv.toPixels(ctxWithFontSize(15));
            assertThat(result).isCloseTo(7.5, within(0.01));
        }

        @Test
        @DisplayName("em resolves to baseFontSize=20")
        void emResolvesAtTwenty() {
            var sv = SizeValue.parse("0.5em");
            var result = sv.toPixels(ctxWithFontSize(20));
            assertThat(result).isCloseTo(10.0, within(0.01));
        }

        @Test
        @DisplayName("1em equals baseFontSize at various sizes")
        void oneEmEqualsFontSize() {
            for (float fs : new float[]{12, 16, 20, 24}) {
                var sv = SizeValue.parse("1em");
                var result = sv.toPixels(ctxWithFontSize(fs));
                assertThat(result).as("1em at fontSize=%s", fs)
                        .isCloseTo((double) fs, within(0.01));
            }
        }

        @Test
        @DisplayName("RenderMetrics.gui produces correct ch and ln mappings")
        void guiMetricsChAndLn() {
            var metrics = RenderMetrics.gui(20, 11, 24, 20);
            var ctx = RenderContext.builder()
                    .renderer(RenderContext.RENDERER_SKIA)
                    .librarian(dev.everydaythings.graph.runtime.LibrarianHandle.inMemory())
                    .renderMetrics(metrics)
                    .baseFontSize(20)
                    .build();
            assertThat(SizeValue.parse("1ch").toPixels(ctx)).isCloseTo(11.0, within(0.01));
            assertThat(SizeValue.parse("1ln").toPixels(ctx)).isCloseTo(24.0, within(0.01));
            assertThat(SizeValue.parse("1em").toPixels(ctx)).isCloseTo(20.0, within(0.01));
        }
    }
}
