package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.BoxBorder.BorderSide;
import dev.everydaythings.graph.ui.text.BoxDrawing.Corner;
import dev.everydaythings.graph.ui.text.BoxDrawing.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BoxDrawing character resolution, corner handling,
 * weight mapping, and box wrapping.
 */
@DisplayName("BoxDrawing")
class BoxDrawingTest {

    private static final RenderContext TUI = RenderContext.tui();

    // ==================================================================================
    // Horizontal Characters
    // ==================================================================================

    @Nested
    @DisplayName("Horizontal Characters")
    class HorizontalChars {

        @Test
        @DisplayName("light solid horizontal is thin line")
        void lightSolid() {
            assertThat(BoxDrawing.horizontalChar("solid", Weight.LIGHT)).isEqualTo('─');
        }

        @Test
        @DisplayName("heavy solid horizontal is thick line")
        void heavySolid() {
            assertThat(BoxDrawing.horizontalChar("solid", Weight.HEAVY)).isEqualTo('━');
        }

        @Test
        @DisplayName("double horizontal")
        void doubleHorizontal() {
            assertThat(BoxDrawing.horizontalChar("double", Weight.DOUBLE)).isEqualTo('═');
        }

        @Test
        @DisplayName("light dashed horizontal")
        void lightDashed() {
            assertThat(BoxDrawing.horizontalChar("dashed", Weight.LIGHT)).isEqualTo('╌');
        }

        @Test
        @DisplayName("heavy dashed horizontal")
        void heavyDashed() {
            assertThat(BoxDrawing.horizontalChar("dashed", Weight.HEAVY)).isEqualTo('╍');
        }

        @Test
        @DisplayName("NONE weight returns space")
        void noneWeight() {
            assertThat(BoxDrawing.horizontalChar("solid", Weight.NONE)).isEqualTo(' ');
        }

        @Test
        @DisplayName("block weight returns full block")
        void blockWeight() {
            assertThat(BoxDrawing.horizontalChar("solid", Weight.BLOCK_1_2)).isEqualTo('█');
            assertThat(BoxDrawing.horizontalChar("solid", Weight.FULL)).isEqualTo('█');
        }
    }

    // ==================================================================================
    // Vertical Characters
    // ==================================================================================

    @Nested
    @DisplayName("Vertical Characters")
    class VerticalChars {

        @Test
        @DisplayName("light solid vertical is thin line")
        void lightSolid() {
            assertThat(BoxDrawing.verticalChar("solid", Weight.LIGHT)).isEqualTo('│');
        }

        @Test
        @DisplayName("heavy solid vertical is thick line")
        void heavySolid() {
            assertThat(BoxDrawing.verticalChar("solid", Weight.HEAVY)).isEqualTo('┃');
        }

        @Test
        @DisplayName("double vertical")
        void doubleVertical() {
            assertThat(BoxDrawing.verticalChar("double", Weight.DOUBLE)).isEqualTo('║');
        }

        @Test
        @DisplayName("light dashed vertical")
        void lightDashed() {
            assertThat(BoxDrawing.verticalChar("dashed", Weight.LIGHT)).isEqualTo('╎');
        }

        @Test
        @DisplayName("heavy dashed vertical")
        void heavyDashed() {
            assertThat(BoxDrawing.verticalChar("dashed", Weight.HEAVY)).isEqualTo('╏');
        }

        @Test
        @DisplayName("NONE weight returns space")
        void noneWeight() {
            assertThat(BoxDrawing.verticalChar("solid", Weight.NONE)).isEqualTo(' ');
        }
    }

    // ==================================================================================
    // Block Characters
    // ==================================================================================

    @Nested
    @DisplayName("Block Characters")
    class BlockChars {

        @Test
        @DisplayName("left block characters span hairline to full")
        void leftBlocks() {
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_1_8)).isEqualTo('▏');
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_1_4)).isEqualTo('▎');
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_3_8)).isEqualTo('▍');
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_1_2)).isEqualTo('▌');
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_5_8)).isEqualTo('▋');
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_3_4)).isEqualTo('▊');
            assertThat(BoxDrawing.leftBlockChar(Weight.BLOCK_7_8)).isEqualTo('▉');
            assertThat(BoxDrawing.leftBlockChar(Weight.FULL)).isEqualTo('█');
        }

        @Test
        @DisplayName("right block characters")
        void rightBlocks() {
            assertThat(BoxDrawing.rightBlockChar(Weight.BLOCK_1_8)).isEqualTo('▕');
            assertThat(BoxDrawing.rightBlockChar(Weight.BLOCK_1_2)).isEqualTo('▐');
            assertThat(BoxDrawing.rightBlockChar(Weight.FULL)).isEqualTo('█');
        }

        @Test
        @DisplayName("top block characters")
        void topBlocks() {
            assertThat(BoxDrawing.topBlockChar(Weight.BLOCK_1_8)).isEqualTo('▔');
            assertThat(BoxDrawing.topBlockChar(Weight.BLOCK_1_2)).isEqualTo('▀');
            assertThat(BoxDrawing.topBlockChar(Weight.FULL)).isEqualTo('█');
        }

        @Test
        @DisplayName("bottom block characters span 1/8 to full")
        void bottomBlocks() {
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_1_8)).isEqualTo('▁');
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_1_4)).isEqualTo('▂');
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_3_8)).isEqualTo('▃');
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_1_2)).isEqualTo('▄');
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_5_8)).isEqualTo('▅');
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_3_4)).isEqualTo('▆');
            assertThat(BoxDrawing.bottomBlockChar(Weight.BLOCK_7_8)).isEqualTo('▇');
            assertThat(BoxDrawing.bottomBlockChar(Weight.FULL)).isEqualTo('█');
        }

        @Test
        @DisplayName("non-block weight returns space for block methods")
        void nonBlockReturnsSpace() {
            assertThat(BoxDrawing.leftBlockChar(Weight.LIGHT)).isEqualTo(' ');
            assertThat(BoxDrawing.rightBlockChar(Weight.NONE)).isEqualTo(' ');
            assertThat(BoxDrawing.topBlockChar(Weight.HEAVY)).isEqualTo(' ');
            assertThat(BoxDrawing.bottomBlockChar(Weight.NONE)).isEqualTo(' ');
        }
    }

    // ==================================================================================
    // Corner Resolution - Uniform Weight
    // ==================================================================================

    @Nested
    @DisplayName("Corner Resolution - Uniform Weight")
    class UniformCorners {

        @Test
        @DisplayName("light corners: top-left bottom-right top-right bottom-left")
        void lightCorners() {
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_LEFT, false)).isEqualTo('┌');
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_RIGHT, false)).isEqualTo('┐');
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.BOTTOM_LEFT, false)).isEqualTo('└');
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.BOTTOM_RIGHT, false)).isEqualTo('┘');
        }

        @Test
        @DisplayName("heavy corners")
        void heavyCorners() {
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.HEAVY,
                    "solid", "solid", Corner.TOP_LEFT, false)).isEqualTo('┏');
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.HEAVY,
                    "solid", "solid", Corner.TOP_RIGHT, false)).isEqualTo('┓');
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.HEAVY,
                    "solid", "solid", Corner.BOTTOM_LEFT, false)).isEqualTo('┗');
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.HEAVY,
                    "solid", "solid", Corner.BOTTOM_RIGHT, false)).isEqualTo('┛');
        }

        @Test
        @DisplayName("double corners")
        void doubleCorners() {
            assertThat(BoxDrawing.resolveCorner(Weight.DOUBLE, Weight.DOUBLE,
                    "double", "double", Corner.TOP_LEFT, false)).isEqualTo('╔');
            assertThat(BoxDrawing.resolveCorner(Weight.DOUBLE, Weight.DOUBLE,
                    "double", "double", Corner.TOP_RIGHT, false)).isEqualTo('╗');
            assertThat(BoxDrawing.resolveCorner(Weight.DOUBLE, Weight.DOUBLE,
                    "double", "double", Corner.BOTTOM_LEFT, false)).isEqualTo('╚');
            assertThat(BoxDrawing.resolveCorner(Weight.DOUBLE, Weight.DOUBLE,
                    "double", "double", Corner.BOTTOM_RIGHT, false)).isEqualTo('╝');
        }
    }

    // ==================================================================================
    // Corner Resolution - Rounded
    // ==================================================================================

    @Nested
    @DisplayName("Corner Resolution - Rounded")
    class RoundedCorners {

        @Test
        @DisplayName("rounded light corners")
        void roundedLightCorners() {
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_LEFT, true)).isEqualTo('╭');
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_RIGHT, true)).isEqualTo('╮');
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.BOTTOM_LEFT, true)).isEqualTo('╰');
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", Corner.BOTTOM_RIGHT, true)).isEqualTo('╯');
        }

        @Test
        @DisplayName("rounded is ignored for heavy weight (falls back to sharp)")
        void roundedIgnoredForHeavy() {
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.HEAVY,
                    "solid", "solid", Corner.TOP_LEFT, true)).isEqualTo('┏');
        }

        @Test
        @DisplayName("rounded is ignored for double style")
        void roundedIgnoredForDouble() {
            assertThat(BoxDrawing.resolveCorner(Weight.DOUBLE, Weight.DOUBLE,
                    "double", "double", Corner.TOP_LEFT, true)).isEqualTo('╔');
        }
    }

    // ==================================================================================
    // Corner Resolution - Mixed Weight
    // ==================================================================================

    @Nested
    @DisplayName("Corner Resolution - Mixed Weight")
    class MixedWeightCorners {

        @Test
        @DisplayName("heavy horizontal, light vertical top-left")
        void heavyHorizontalLightVerticalTopLeft() {
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_LEFT, false)).isEqualTo('┍');
        }

        @Test
        @DisplayName("light horizontal, heavy vertical top-left")
        void lightHorizontalHeavyVerticalTopLeft() {
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.HEAVY,
                    "solid", "solid", Corner.TOP_LEFT, false)).isEqualTo('┎');
        }

        @Test
        @DisplayName("heavy horizontal, light vertical top-right")
        void heavyHorizontalLightVerticalTopRight() {
            assertThat(BoxDrawing.resolveCorner(Weight.HEAVY, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_RIGHT, false)).isEqualTo('┑');
        }

        @Test
        @DisplayName("light horizontal, heavy vertical top-right")
        void lightHorizontalHeavyVerticalTopRight() {
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.HEAVY,
                    "solid", "solid", Corner.TOP_RIGHT, false)).isEqualTo('┒');
        }

        @Test
        @DisplayName("double horizontal, light vertical top-left")
        void doubleHorizontalLightVertical() {
            assertThat(BoxDrawing.resolveCorner(Weight.DOUBLE, Weight.LIGHT,
                    "double", "solid", Corner.TOP_LEFT, false)).isEqualTo('╒');
        }

        @Test
        @DisplayName("light horizontal, double vertical top-left")
        void lightHorizontalDoubleVertical() {
            assertThat(BoxDrawing.resolveCorner(Weight.LIGHT, Weight.DOUBLE,
                    "solid", "double", Corner.TOP_LEFT, false)).isEqualTo('╓');
        }

        @Test
        @DisplayName("NONE horizontal and NONE vertical returns space")
        void bothNoneReturnsSpace() {
            assertThat(BoxDrawing.resolveCorner(Weight.NONE, Weight.NONE,
                    "solid", "solid", Corner.TOP_LEFT, false)).isEqualTo(' ');
        }

        @Test
        @DisplayName("block weight at corner returns light line-drawing corner")
        void blockWeightReturnsLightCorner() {
            assertThat(BoxDrawing.resolveCorner(Weight.BLOCK_1_2, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_LEFT, false)).isEqualTo('┌');
        }

        @Test
        @DisplayName("NONE horizontal with light vertical gives half-line")
        void noneHorizontalLightVertical() {
            char c = BoxDrawing.resolveCorner(Weight.NONE, Weight.LIGHT,
                    "solid", "solid", Corner.TOP_LEFT, false);
            assertThat(c).isEqualTo('╷');
        }

        @Test
        @DisplayName("light horizontal with NONE vertical gives half-line")
        void lightHorizontalNoneVertical() {
            char c = BoxDrawing.resolveCorner(Weight.LIGHT, Weight.NONE,
                    "solid", "solid", Corner.TOP_LEFT, false);
            assertThat(c).isEqualTo('╶');
        }
    }

    // ==================================================================================
    // Weight Enum Properties
    // ==================================================================================

    @Nested
    @DisplayName("Weight Enum")
    class WeightEnumTests {

        @Test
        @DisplayName("isBlock is true for block weights")
        void isBlock() {
            assertThat(Weight.BLOCK_1_8.isBlock()).isTrue();
            assertThat(Weight.BLOCK_1_2.isBlock()).isTrue();
            assertThat(Weight.FULL.isBlock()).isTrue();
        }

        @Test
        @DisplayName("isBlock is false for line weights")
        void isBlockFalseForLines() {
            assertThat(Weight.NONE.isBlock()).isFalse();
            assertThat(Weight.LIGHT.isBlock()).isFalse();
            assertThat(Weight.HEAVY.isBlock()).isFalse();
            assertThat(Weight.DOUBLE.isBlock()).isFalse();
        }

        @Test
        @DisplayName("isLine is true for line drawing weights")
        void isLine() {
            assertThat(Weight.LIGHT.isLine()).isTrue();
            assertThat(Weight.HEAVY.isLine()).isTrue();
            assertThat(Weight.DOUBLE.isLine()).isTrue();
        }

        @Test
        @DisplayName("isLine is false for non-line weights")
        void isLineFalseForOthers() {
            assertThat(Weight.NONE.isLine()).isFalse();
            assertThat(Weight.BLOCK_1_2.isLine()).isFalse();
            assertThat(Weight.FULL.isLine()).isFalse();
        }
    }

    // ==================================================================================
    // weightOf(BorderSide)
    // ==================================================================================

    @Nested
    @DisplayName("weightOf(BorderSide)")
    class WeightOfBorderSide {

        @Test
        @DisplayName("NONE side maps to NONE weight")
        void noneSide() {
            assertThat(BoxDrawing.weightOf(BorderSide.NONE, TUI)).isEqualTo(Weight.NONE);
        }

        @Test
        @DisplayName("1px solid maps to BLOCK_1_8")
        void onePxMapsToBlock18() {
            BorderSide side = BorderSide.parse("1px solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        @Test
        @DisplayName("2px solid maps to BLOCK_1_4")
        void twoPxMapsToBlock14() {
            BorderSide side = BorderSide.parse("2px solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_4);
        }

        @Test
        @DisplayName("double style maps to DOUBLE")
        void doubleMapsToDouble() {
            BorderSide side = BorderSide.parse("1px double");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.DOUBLE);
        }

        @Test
        @DisplayName("invisible side maps to NONE")
        void invisibleMapsToNone() {
            BorderSide side = new BorderSide("none", "1px", null);
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.NONE);
        }

        // --- ch unit ---

        @Test
        @DisplayName("1ch solid (10px) maps to FULL")
        void oneChMapsToFull() {
            BorderSide side = BorderSide.parse("1ch solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.FULL);
        }

        @Test
        @DisplayName("0.1ch solid (1px) maps to BLOCK_1_8")
        void tenthChMapsToBlock18() {
            BorderSide side = BorderSide.parse("0.1ch solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        @Test
        @DisplayName("0.25ch solid (2.5px) maps to BLOCK_1_4")
        void quarterChMapsToBlock14() {
            BorderSide side = BorderSide.parse("0.25ch solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_4);
        }

        @Test
        @DisplayName("0.5ch solid (5px) maps to BLOCK_1_2")
        void halfChMapsToBlock12() {
            BorderSide side = BorderSide.parse("0.5ch solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_2);
        }

        @Test
        @DisplayName("2ch solid (20px) maps to FULL")
        void twoChMapsToFull() {
            BorderSide side = BorderSide.parse("2ch solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.FULL);
        }

        // --- em unit ---

        @Test
        @DisplayName("1em solid (10px) maps to FULL")
        void oneEmMapsToFull() {
            BorderSide side = BorderSide.parse("1em solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.FULL);
        }

        @Test
        @DisplayName("0.06em solid (0.6px) maps to BLOCK_1_8")
        void sixHundredthsEmMapsToBlock18() {
            BorderSide side = BorderSide.parse("0.06em solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        @Test
        @DisplayName("0.1em solid (1px) maps to BLOCK_1_8")
        void tenthEmMapsToBlock18() {
            BorderSide side = BorderSide.parse("0.1em solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        @Test
        @DisplayName("0.5em solid (5px) maps to BLOCK_1_2")
        void halfEmMapsToBlock12() {
            BorderSide side = BorderSide.parse("0.5em solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_2);
        }

        @Test
        @DisplayName("0.25em solid (2.5px) maps to BLOCK_1_4")
        void quarterEmMapsToBlock14() {
            BorderSide side = BorderSide.parse("0.25em solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_4);
        }

        // --- rem unit ---

        @Test
        @DisplayName("1rem solid (10px) maps to FULL")
        void oneRemMapsToFull() {
            BorderSide side = BorderSide.parse("1rem solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.FULL);
        }

        @Test
        @DisplayName("0.1rem solid (1px) maps to BLOCK_1_8")
        void tenthRemMapsToBlock18() {
            BorderSide side = BorderSide.parse("0.1rem solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        // --- ln unit ---

        @Test
        @DisplayName("1ln solid (10px) maps to FULL")
        void oneLnMapsToFull() {
            BorderSide side = BorderSide.parse("1ln solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.FULL);
        }

        @Test
        @DisplayName("0.05ln solid (0.5px) maps to BLOCK_1_8")
        void twentiethLnMapsToBlock18() {
            BorderSide side = BorderSide.parse("0.05ln solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        @Test
        @DisplayName("0.1ln solid (1px) maps to BLOCK_1_8")
        void tenthLnMapsToBlock18() {
            BorderSide side = BorderSide.parse("0.1ln solid");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.BLOCK_1_8);
        }

        // --- double style overrides weight ---

        @Test
        @DisplayName("double style with ch width still maps to DOUBLE")
        void doubleStyleOverridesChWeight() {
            BorderSide side = BorderSide.parse("1ch double");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.DOUBLE);
        }

        @Test
        @DisplayName("double style with em width still maps to DOUBLE")
        void doubleStyleOverridesEmWeight() {
            BorderSide side = BorderSide.parse("0.5em double");
            assertThat(BoxDrawing.weightOf(side, TUI)).isEqualTo(Weight.DOUBLE);
        }
    }

    // ==================================================================================
    // wrapInBorder
    // ==================================================================================

    @Nested
    @DisplayName("wrapInBorder")
    class WrapInBorder {

        @Test
        @DisplayName("1px solid border wraps content with block chars")
        void onePxBorder() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of("Hello");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            assertThat(result).hasSize(3); // top border + content + bottom border

            // 1px → BLOCK_1_8: top ▔, bottom ▁, left ▏, right ▕, corners ┌/┐
            assertThat(result.get(0)).startsWith("┌");
            assertThat(result.get(0)).contains("▔");

            // Content row: left block + content + right block
            assertThat(result.get(1)).contains("Hello");
            assertThat(result.get(1).charAt(0)).isEqualTo('▏');

            // Bottom row
            assertThat(result.get(2)).contains("▁");
        }

        @Test
        @DisplayName("2px solid border wraps with BLOCK_1_4 chars")
        void twoPxBorder() {
            BoxBorder border = BoxBorder.parse("2px solid");
            List<String> content = List.of("Test");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // 2px → BLOCK_1_4: left ▎, bottom ▂
            assertThat(result.get(1).charAt(0)).isEqualTo('▎');
            assertThat(result.get(2)).contains("▂");
        }

        @Test
        @DisplayName("rounded border with block chars uses full block corners")
        void roundedBorder() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of("Round");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, true, TUI);

            // Block weights use rounded line-drawing corners
            assertThat(result.get(0)).startsWith("╭");
            assertThat(result.get(0)).contains("▔");
        }

        @Test
        @DisplayName("border with padding adds padding rows and columns")
        void borderWithPadding() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of("Pad");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 1, 2, 1, 2, false, TUI);

            // Should have: top border + 1 pad row + content + 1 pad row + bottom border = 5
            assertThat(result).hasSize(5);

            // Content row should have left padding (2) and right padding (2)
            String contentRow = result.get(2); // top, padTop, then content
            assertThat(contentRow.charAt(0)).isEqualTo('▏');
            // Content should be indented with padding
            assertThat(contentRow).contains("  Pad");
        }

        @Test
        @DisplayName("explicit inner width controls box width")
        void explicitInnerWidth() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of("Hi");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, 10, 0, 0, 0, 0, false, TUI);

            // Top row should be: corner + 10 horizontal chars + corner = 12
            assertThat(result.get(0).length()).isEqualTo(12);
        }

        @Test
        @DisplayName("auto width uses max content width")
        void autoWidth() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of("Short", "A longer line");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // Width should be based on "A longer line" (13 chars) + 2 borders = 15
            assertThat(result.get(0).length()).isEqualTo(15);
        }

        @Test
        @DisplayName("empty content produces border only")
        void emptyContent() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of();

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // Top border + bottom border (no content rows)
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("multi-line content wraps each line")
        void multiLineContent() {
            BoxBorder border = BoxBorder.parse("1px solid");
            List<String> content = List.of("Line 1", "Line 2", "Line 3");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            assertThat(result).hasSize(5); // top + 3 content + bottom
            assertThat(result.get(1)).contains("Line 1");
            assertThat(result.get(2)).contains("Line 2");
            assertThat(result.get(3)).contains("Line 3");
        }

        // --- multi-unit border widths ---

        @Test
        @DisplayName("1ch solid border produces full block chars (10px → FULL)")
        void chBorderUsesFullBlock() {
            BoxBorder border = BoxBorder.parse("1ch solid");
            List<String> content = List.of("Block");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // FULL weight → █ everywhere
            assertThat(result.get(0)).contains("█");
            assertThat(result.get(1)).contains("Block");
            assertThat(result.get(1).charAt(0)).isEqualTo('█');
        }

        @Test
        @DisplayName("1em solid border produces full block chars (10px → FULL)")
        void emBorderUsesFullBlock() {
            BoxBorder border = BoxBorder.parse("1em solid");
            List<String> content = List.of("Full");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // FULL weight → █ everywhere
            assertThat(result.get(0)).contains("█");
            assertThat(result.get(1).charAt(0)).isEqualTo('█');
        }

        @Test
        @DisplayName("0.25ch solid border produces BLOCK_1_4 chars (2.5px)")
        void quarterChBorderUsesBlock14() {
            BoxBorder border = BoxBorder.parse("0.25ch solid");
            List<String> content = List.of("Quarter");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // BLOCK_1_4: left ▎, bottom ▂
            assertThat(result.get(1).charAt(0)).isEqualTo('▎');
            assertThat(result.getLast()).contains("▂");
        }

        @Test
        @DisplayName("0.1ch solid border produces BLOCK_1_8 chars (1px)")
        void tenthChBorderUsesBlock18() {
            BoxBorder border = BoxBorder.parse("0.1ch solid");
            List<String> content = List.of("Thin");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // BLOCK_1_8: left ▏, top ▔, bottom ▁
            assertThat(result.get(1).charAt(0)).isEqualTo('▏');
            assertThat(result.get(0)).contains("▔");
        }

        @Test
        @DisplayName("0.1em solid border produces BLOCK_1_8 chars (1px)")
        void tenthEmBorderUsesBlock18() {
            BoxBorder border = BoxBorder.parse("0.1em solid");
            List<String> content = List.of("Thin");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // BLOCK_1_8: left ▏, top ▔
            assertThat(result.get(1).charAt(0)).isEqualTo('▏');
            assertThat(result.get(0)).contains("▔");
        }

        @Test
        @DisplayName("0.5em solid border produces BLOCK_1_2 (half block)")
        void halfEmBorderUsesBlock12() {
            BoxBorder border = BoxBorder.parse("0.5em solid");
            List<String> content = List.of("Half");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // BLOCK_1_2: left ▌, bottom ▄
            assertThat(result.get(1).charAt(0)).isEqualTo('▌');
            assertThat(result.getLast()).contains("▄");
        }

        @Test
        @DisplayName("mixed per-side units: em top, ch sides, px bottom")
        void mixedUnitPerSide() {
            BoxBorder border = BoxBorder.of(
                    "0.5em solid",   // top: 5px → BLOCK_1_2
                    "0.1ch solid",   // right: 1px → BLOCK_1_8
                    "0.25ch solid",  // bottom: 2.5px → BLOCK_1_4
                    "0.1ch solid",   // left: 1px → BLOCK_1_8
                    null
            );
            List<String> content = List.of("Mix");

            List<String> result = BoxDrawing.wrapInBorder(
                    content, border, -1, 0, 0, 0, 0, false, TUI);

            // Top should use BLOCK_1_2 horizontal: ▀ (upper half block)
            assertThat(result.get(0)).contains("▀");
            // Bottom should use BLOCK_1_4 char: ▂
            assertThat(result.getLast()).contains("▂");
            // Left should use BLOCK_1_8: ▏
            assertThat(result.get(1).charAt(0)).isEqualTo('▏');
        }
    }

    // ==================================================================================
    // stripAnsiLength
    // ==================================================================================

    @Nested
    @DisplayName("stripAnsiLength")
    class StripAnsiLength {

        @Test
        @DisplayName("plain text returns actual length")
        void plainText() {
            assertThat(BoxDrawing.stripAnsiLength("Hello")).isEqualTo(5);
        }

        @Test
        @DisplayName("text with ANSI codes strips codes from length")
        void textWithAnsi() {
            String ansiText = "\u001B[31mRed\u001B[0m";
            assertThat(BoxDrawing.stripAnsiLength(ansiText)).isEqualTo(3);
        }

        @Test
        @DisplayName("empty string returns 0")
        void emptyString() {
            assertThat(BoxDrawing.stripAnsiLength("")).isEqualTo(0);
        }

        @Test
        @DisplayName("null returns 0")
        void nullString() {
            assertThat(BoxDrawing.stripAnsiLength(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("multiple ANSI codes stripped correctly")
        void multipleAnsiCodes() {
            String text = "\u001B[1m\u001B[34mBold Blue\u001B[0m Normal";
            assertThat(BoxDrawing.stripAnsiLength(text)).isEqualTo("Bold Blue Normal".length());
        }
    }
}
