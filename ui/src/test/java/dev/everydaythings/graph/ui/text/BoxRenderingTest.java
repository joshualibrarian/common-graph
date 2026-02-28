package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end TUI box rendering tests.
 *
 * <p>These tests exercise the full pipeline: ContainerSurface with a BoxBorder
 * renders through TuiSurfaceRenderer, which buffers child content and wraps
 * it with BoxDrawing unicode characters.
 *
 * <p>With block-character-only rendering (no LIGHT/HEAVY line-drawing),
 * all border weights map to Unicode block elements:
 * <ul>
 *   <li>1px → BLOCK_1_8: ▏▔▕▁ (thinnest block)</li>
 *   <li>2px → BLOCK_1_4: ▎▀▕▂</li>
 *   <li>0.5ch = 5px → BLOCK_1_2: ▌▀▐▄</li>
 *   <li>1ch = 10px → FULL: ████</li>
 * </ul>
 */
@DisplayName("Box Rendering (End-to-End)")
class BoxRenderingTest {

    private static final String ANSI_PATTERN = "\u001B\\[[0-9;]*m";

    private static String stripAnsi(String s) {
        return s.replaceAll(ANSI_PATTERN, "");
    }

    // ==================================================================================
    // Thin Border (1px → BLOCK_1_8)
    // ==================================================================================

    @Nested
    @DisplayName("Light Border (1px solid)")
    class LightBorder {

        @Test
        @DisplayName("container with 1px solid border produces light box-drawing chars")
        void lightBorderChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Hello World");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1px → BLOCK_1_8: top=▔, bottom=▁, left=▏, right=▕, corners=█
            assertThat(output).contains("▔");  // top
            assertThat(output).contains("▁");  // bottom
            assertThat(output).contains("▏");  // left
            assertThat(output).contains("▕");  // right
        }

        @Test
        @DisplayName("child text appears inside the border")
        void childTextInsideBorder() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Content Inside");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            assertThat(output).contains("Content Inside");

            // Content should appear on a line bounded by block chars
            String[] lines = output.split("\n");
            boolean foundContentLine = false;
            for (String line : lines) {
                if (line.contains("Content Inside")) {
                    // Left border is ▏ (BLOCK_1_8 left)
                    assertThat(line).contains("▏");
                    foundContentLine = true;
                    break;
                }
            }
            assertThat(foundContentLine)
                    .as("Content line should be bounded by block borders")
                    .isTrue();
        }
    }

    // ==================================================================================
    // Quarter Block Border (2px → BLOCK_1_4)
    // ==================================================================================

    @Nested
    @DisplayName("Heavy Border (2px solid)")
    class HeavyBorder {

        @Test
        @DisplayName("container with 2px solid border produces heavy box-drawing chars")
        void heavyBorderChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Box");
            container.border("2px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 2px → BLOCK_1_4: top=▀, bottom=▂, left=▎, right=▕, corners=█
            assertThat(output).contains("▀");  // top half block
            assertThat(output).contains("▂");  // bottom 1/4 block
            assertThat(output).contains("▎");  // left 1/4 block
        }
    }

    // ==================================================================================
    // Rounded Border — block weights don't support rounded corners
    // ==================================================================================

    @Nested
    @DisplayName("Rounded Border")
    class RoundedBorder {

        @Test
        @DisplayName("border with radius produces rounded corners")
        void roundedCorners() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Rounded");
            container.border(BoxBorder.parse("1px solid", "4px"));

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // Block weights use light rounded corners (╭╮╰╯)
            assertThat(output).contains("╭");
            assertThat(output).contains("Rounded");
        }

        @Test
        @DisplayName("rounded border still uses regular horizontal and vertical lines")
        void roundedBorderLinesAreRegular() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Rounded");
            container.border(BoxBorder.parse("1px solid", "4px"));

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // Block weight uses block chars, not line-drawing
            assertThat(output).contains("▔");  // top block
            assertThat(output).contains("▏");  // left block
        }
    }

    // ==================================================================================
    // Border with Text Child
    // ==================================================================================

    @Nested
    @DisplayName("Border with Children")
    class BorderWithChildren {

        @Test
        @DisplayName("multiple text children inside border")
        void multipleChildren() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Line One")
                    .add("Line Two")
                    .add("Line Three");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            assertThat(output).contains("Line One");
            assertThat(output).contains("Line Two");
            assertThat(output).contains("Line Three");

            // Count lines with left block border containing content
            String[] lines = output.split("\n");
            int contentLines = 0;
            for (String line : lines) {
                if (line.contains("▏") && (line.contains("Line One")
                        || line.contains("Line Two")
                        || line.contains("Line Three"))) {
                    contentLines++;
                }
            }
            assertThat(contentLines).isEqualTo(3);
        }

        @Test
        @DisplayName("empty container with border renders border only")
        void emptyContainerWithBorder() {
            ContainerSurface container = ContainerSurface.vertical();
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // Should have top and bottom border with light corners
            assertThat(output).contains("┌");  // light corners for block weights
        }
    }

    // ==================================================================================
    // Explicit Width
    // ==================================================================================

    @Nested
    @DisplayName("Explicit Width")
    class ExplicitWidth {

        @Test
        @DisplayName("container with width and border has correct box width")
        void widthControlsBoxWidth() {
            ContainerSurface container = ContainerSurface.vertical()
                    .width("20ch")
                    .add("Short");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // Find the top border line (block chars: █ corner + ▔ fill + █ corner)
            String[] lines = output.split("\n");
            String topLine = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("▔") || (trimmed.startsWith("█") && trimmed.length() > 2)) {
                    topLine = trimmed;
                    break;
                }
            }
            assertThat(topLine).isNotNull();

            // Top line should be: █ + 20 top chars + █ = 22 total
            assertThat(topLine.length()).isEqualTo(22);
        }
    }

    // ==================================================================================
    // No Border
    // ==================================================================================

    @Nested
    @DisplayName("No Border")
    class NoBorder {

        @Test
        @DisplayName("container without border has no box-drawing chars")
        void noBorderNoBoxChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Plain Content");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            assertThat(output).doesNotContain("▏");
            assertThat(output).doesNotContain("▔");
            assertThat(output).doesNotContain("▕");
            assertThat(output).doesNotContain("▁");
            assertThat(output).contains("Plain Content");
        }

        @Test
        @DisplayName("border set to NONE produces no box-drawing chars")
        void borderNoneNoBoxChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("No Border");
            container.border(BoxBorder.NONE);

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            assertThat(output).doesNotContain("▏");
            assertThat(output).doesNotContain("▕");
            assertThat(output).contains("No Border");
        }
    }

    // ==================================================================================
    // Multi-Unit Borders
    // ==================================================================================

    @Nested
    @DisplayName("Multi-Unit Borders")
    class MultiUnitBorders {

        @Test
        @DisplayName("1ch solid border produces block-weight characters")
        void chBorderProducesBlockChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Block Border");
            container.border("1ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1ch = 10px → FULL weight → █ everywhere
            assertThat(output).contains("█");
            assertThat(output).contains("Block Border");
        }

        @Test
        @DisplayName("1em solid border produces full block characters")
        void emBorderProducesFullBlock() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Full Block");
            container.border("1em solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1em = 10px → FULL weight → █ everywhere
            assertThat(output).contains("█");
            assertThat(output).contains("Full Block");
        }

        @Test
        @DisplayName("0.1ch solid border produces light line characters")
        void tenthChBorderProducesLight() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Thin Ch");
            container.border("0.1ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.1ch = 1px → BLOCK_1_8: ▏▔▕▁
            assertThat(output).contains("▔");  // top
            assertThat(output).contains("▏");  // left
            assertThat(output).contains("▁");  // bottom
        }

        @Test
        @DisplayName("0.25ch solid border produces heavy line characters")
        void quarterChBorderProducesHeavy() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Ch");
            container.border("0.25ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.25ch = 2.5px → BLOCK_1_4: ▎▀▕▂
            assertThat(output).contains("▀");  // top (upper half block)
            assertThat(output).contains("▎");  // left 1/4 block
            assertThat(output).contains("▂");  // bottom 1/4 block
        }

        @Test
        @DisplayName("0.1em solid border produces BLOCK_1_8 characters")
        void tenthEmBorderProducesBlock18() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Em");
            container.border("0.1em solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.1em = 1px → BLOCK_1_8: ▏▔▕▁
            assertThat(output).contains("▔");  // top
            assertThat(output).contains("▏");  // left
        }

        @Test
        @DisplayName("0.5em solid border matches 0.5ch solid border (both 5px → BLOCK_1_2)")
        void halfEmMatchesHalfCh() {
            ContainerSurface containerEm = ContainerSurface.vertical()
                    .add("Same");
            containerEm.border("0.5em solid");

            ContainerSurface containerCh = ContainerSurface.vertical()
                    .add("Same");
            containerCh.border("0.5ch solid");

            TuiSurfaceRenderer rendererEm = new TuiSurfaceRenderer();
            containerEm.render(rendererEm);

            TuiSurfaceRenderer rendererCh = new TuiSurfaceRenderer();
            containerCh.render(rendererCh);

            // Both 0.5em and 0.5ch = 5px → BLOCK_1_2
            assertThat(stripAnsi(rendererEm.result()))
                    .isEqualTo(stripAnsi(rendererCh.result()));
        }

        @Test
        @DisplayName("0.5ch solid border (5px → BLOCK_1_2) uses block chars")
        void halfChBorderUsesBlock12() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Half Block");
            container.border("0.5ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.5ch = 5px → BLOCK_1_2: left=▌
            assertThat(output).contains("▌");
            assertThat(output).contains("Half Block");
        }

        @Test
        @DisplayName("1rem solid border produces full block (same as 1em)")
        void remBorderProducesFullBlock() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Rem Block");
            container.border("1rem solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1rem = 10px → FULL
            assertThat(output).contains("█");
            assertThat(output).contains("Rem Block");
        }

        @Test
        @DisplayName("1ln solid border produces full block (10px → FULL)")
        void lnBorderProducesFullBlock() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Line Block");
            container.border("1ln solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1ln = 10px → FULL
            assertThat(output).contains("█");
            assertThat(output).contains("Line Block");
        }
    }

    // ==================================================================================
    // Multi-Unit Width
    // ==================================================================================

    @Nested
    @DisplayName("Multi-Unit Width")
    class MultiUnitWidth {

        @Test
        @DisplayName("width in em: 10em = 20 columns")
        void widthInEm() {
            ContainerSurface container = ContainerSurface.vertical()
                    .width("10em")
                    .add("Hi");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            String[] lines = output.split("\n");
            String topLine = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("▔")) {
                    topLine = trimmed;
                    break;
                }
            }
            assertThat(topLine).isNotNull();
            // 10em = 20 columns inner + 2 border chars = 22
            assertThat(topLine.length()).isEqualTo(22);
        }

        @Test
        @DisplayName("width in rem: 5rem = 10 columns")
        void widthInRem() {
            ContainerSurface container = ContainerSurface.vertical()
                    .width("5rem")
                    .add("Hi");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            String[] lines = output.split("\n");
            String topLine = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("▔")) {
                    topLine = trimmed;
                    break;
                }
            }
            assertThat(topLine).isNotNull();
            // 5rem = 10 columns inner + 2 border = 12
            assertThat(topLine.length()).isEqualTo(12);
        }

        @Test
        @DisplayName("width in px: 80px = 10 columns (÷8)")
        void widthInPx() {
            ContainerSurface container = ContainerSurface.vertical()
                    .width("80px")
                    .add("Hi");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            String[] lines = output.split("\n");
            String topLine = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("▔")) {
                    topLine = trimmed;
                    break;
                }
            }
            assertThat(topLine).isNotNull();
            // 80px = 10 columns inner + 2 border = 12
            assertThat(topLine.length()).isEqualTo(12);
        }

        @Test
        @DisplayName("width 1em = width 2ch (both 2 columns)")
        void emAndChWidthEquivalent() {
            ContainerSurface containerEm = ContainerSurface.vertical()
                    .width("1em").add("X");
            containerEm.border("1px solid");

            ContainerSurface containerCh = ContainerSurface.vertical()
                    .width("2ch").add("X");
            containerCh.border("1px solid");

            TuiSurfaceRenderer rendererEm = new TuiSurfaceRenderer();
            containerEm.render(rendererEm);

            TuiSurfaceRenderer rendererCh = new TuiSurfaceRenderer();
            containerCh.render(rendererCh);

            // Both should produce same-width boxes
            String emOut = stripAnsi(rendererEm.result());
            String chOut = stripAnsi(rendererCh.result());

            String emTopLine = emOut.lines().filter(l -> l.contains("▔")).findFirst().orElse("").trim();
            String chTopLine = chOut.lines().filter(l -> l.contains("▔")).findFirst().orElse("").trim();

            assertThat(emTopLine.length()).isEqualTo(chTopLine.length());
        }
    }

    // ==================================================================================
    // Mixed Unit Border + Width
    // ==================================================================================

    @Nested
    @DisplayName("Mixed Unit Border + Width")
    class MixedUnitBorderAndWidth {

        @Test
        @DisplayName("em width with ch border")
        void emWidthChBorder() {
            ContainerSurface container = ContainerSurface.vertical()
                    .width("10em")
                    .add("Mixed Units");
            container.border("0.25ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.25ch = 2.5px → BLOCK_1_4 border
            assertThat(output).contains("▎");   // left 1/4 block
            assertThat(output).contains("▀");   // top half block
            assertThat(output).contains("Mixed Units");

            // 10em = 20 columns + 2 border chars = 22
            String topLine = output.lines().filter(l -> l.contains("▀")).findFirst().orElse("").trim();
            assertThat(topLine.length()).isEqualTo(22);
        }

        @Test
        @DisplayName("px width with em border")
        void pxWidthEmBorder() {
            ContainerSurface container = ContainerSurface.vertical()
                    .width("160px")
                    .add("Content");
            container.border("0.1em solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.1em = 1px → BLOCK_1_8 border
            assertThat(output).contains("▏");  // left 1/8 block
            // 160px = 20 columns + 2 border = 22
            String topLine = output.lines().filter(l -> l.contains("▔")).findFirst().orElse("").trim();
            assertThat(topLine.length()).isEqualTo(22);
        }
    }

    // ==================================================================================
    // Border with Padding
    // ==================================================================================

    @Nested
    @DisplayName("Border with Padding")
    class BorderWithPadding {

        @Test
        @DisplayName("border with padding adds space around content")
        void borderWithPaddingAddsSpace() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Padded");
            container.padding("1ch");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // Should contain the content
            assertThat(output).contains("Padded");

            String[] lines = output.split("\n");
            // With padding, there should be more lines than just
            // top border + content + bottom border (i.e., > 3)
            assertThat(lines.length).isGreaterThan(3);
        }
    }

    // ==================================================================================
    // ANSI Color in Borders
    // ==================================================================================

    @Nested
    @DisplayName("Border Color")
    class BorderColor {

        @Test
        @DisplayName("border with color adds ANSI codes to output")
        void borderWithColor() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Colored Border");
            container.border("1px solid blue");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String rawOutput = renderer.result();

            // Raw output should contain ANSI codes (blue = \033[34m)
            assertThat(rawOutput).contains("\u001B[");

            // Stripped output should still have block chars
            String stripped = stripAnsi(rawOutput);
            assertThat(stripped).contains("▏");  // left block border
            assertThat(stripped).contains("Colored Border");
        }
    }

    // ==================================================================================
    // Border Rendering Structure
    // ==================================================================================

    @Nested
    @DisplayName("Rendering Structure")
    class RenderingStructure {

        @Test
        @DisplayName("bordered output has expected line structure")
        void borderedOutputStructure() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("ABC");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            String[] lines = output.split("\n");

            // Filter out any empty lines
            java.util.List<String> nonEmptyLines = new java.util.ArrayList<>();
            for (String line : lines) {
                if (!line.isEmpty()) {
                    nonEmptyLines.add(line);
                }
            }

            assertThat(nonEmptyLines).hasSizeGreaterThanOrEqualTo(3);

            // First non-empty line starts with ┌ (light corner for block weight)
            assertThat(nonEmptyLines.get(0).trim().charAt(0)).isEqualTo('┌');

            // Last non-empty line starts with └ (light corner for block weight)
            String lastLine = nonEmptyLines.get(nonEmptyLines.size() - 1).trim();
            assertThat(lastLine.charAt(0)).isEqualTo('└');

            // Middle lines have left block border ▏
            for (int i = 1; i < nonEmptyLines.size() - 1; i++) {
                String line = nonEmptyLines.get(i).trim();
                assertThat(line.charAt(0)).isEqualTo('▏');
            }
        }

        @Test
        @DisplayName("all border rows have consistent width")
        void consistentWidth() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Same Width");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            String[] lines = output.split("\n");
            java.util.List<String> nonEmptyLines = new java.util.ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    nonEmptyLines.add(trimmed);
                }
            }

            // All lines should have the same width
            int expectedWidth = nonEmptyLines.get(0).length();
            for (String line : nonEmptyLines) {
                assertThat(line.length())
                        .as("Line '%s' should match width %d", line, expectedWidth)
                        .isEqualTo(expectedWidth);
            }
        }
    }
}
