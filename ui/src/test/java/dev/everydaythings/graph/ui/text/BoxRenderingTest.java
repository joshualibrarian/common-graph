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
 * <p>Borders use line-drawing characters with complete matching sets:
 * <ul>
 *   <li>LIGHT (≤2.5px): ─ │ ┌ ┐ └ ┘ (thin lines, rounded: ╭╮╰╯)</li>
 *   <li>HEAVY (&gt;2.5px): ━ ┃ ┏ ┓ ┗ ┛ (thick lines)</li>
 *   <li>DOUBLE: ═ ║ ╔ ╗ ╚ ╝ (double lines)</li>
 * </ul>
 */
@DisplayName("Box Rendering (End-to-End)")
class BoxRenderingTest {

    private static final String ANSI_PATTERN = "\u001B\\[[0-9;]*m";

    private static String stripAnsi(String s) {
        return s.replaceAll(ANSI_PATTERN, "");
    }

    // ==================================================================================
    // Light Border (1px → LIGHT)
    // ==================================================================================

    @Nested
    @DisplayName("Light Border (1px solid)")
    class LightBorder {

        @Test
        @DisplayName("container with 1px solid border produces light line-drawing chars")
        void lightBorderChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Hello World");
            container.border("1px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1px → LIGHT: ┌─┐ top, │ sides, └─┘ bottom
            assertThat(output).contains("─");  // horizontal
            assertThat(output).contains("│");  // vertical sides
            assertThat(output).contains("┌");  // top-left corner
            assertThat(output).contains("┘");  // bottom-right corner
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

            String[] lines = output.split("\n");
            boolean foundContentLine = false;
            for (String line : lines) {
                if (line.contains("Content Inside")) {
                    assertThat(line).contains("│");
                    foundContentLine = true;
                    break;
                }
            }
            assertThat(foundContentLine)
                    .as("Content line should be bounded by │ borders")
                    .isTrue();
        }
    }

    // ==================================================================================
    // Heavy Border (>2.5px → HEAVY)
    // ==================================================================================

    @Nested
    @DisplayName("Heavy Border (3px+ solid)")
    class HeavyBorder {

        @Test
        @DisplayName("container with 3px solid border produces heavy line-drawing chars")
        void heavyBorderChars() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Box");
            container.border("3px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 3px → HEAVY: ┏━┓ top, ┃ sides, ┗━┛ bottom
            assertThat(output).contains("━");  // heavy horizontal
            assertThat(output).contains("┃");  // heavy vertical
            assertThat(output).contains("┏");  // heavy top-left
        }

        @Test
        @DisplayName("2px solid border still uses LIGHT (≤2.5px threshold)")
        void twoPxIsLight() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Test");
            container.border("2px solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 2px → LIGHT
            assertThat(output).contains("│");
            assertThat(output).contains("─");
        }
    }

    // ==================================================================================
    // Rounded Border
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

            // LIGHT + rounded: ╭─╮ top, ╰─╯ bottom
            assertThat(output).contains("╭");
            assertThat(output).contains("╮");
            assertThat(output).contains("╰");
            assertThat(output).contains("╯");
            assertThat(output).contains("Rounded");
        }

        @Test
        @DisplayName("rounded border uses light line-drawing horizontals and verticals")
        void roundedBorderLinesAreLight() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Rounded");
            container.border(BoxBorder.parse("1px solid", "4px"));

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            assertThat(output).contains("─");  // horizontal
            assertThat(output).contains("│");  // vertical
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

            // Count lines with │ border containing content
            String[] lines = output.split("\n");
            int contentLines = 0;
            for (String line : lines) {
                if (line.contains("│") && (line.contains("Line One")
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

            // Should have top and bottom border with matching corners
            assertThat(output).contains("┌");
            assertThat(output).contains("┘");
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

            // Find the top border line
            String[] lines = output.split("\n");
            String topLine = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains("─") && trimmed.contains("┌")) {
                    topLine = trimmed;
                    break;
                }
            }
            assertThat(topLine).isNotNull();

            // Top line should be: ┌ + 20 ─ chars + ┐ = 22 total
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

            assertThat(output).doesNotContain("│");
            assertThat(output).doesNotContain("─");
            assertThat(output).doesNotContain("┌");
            assertThat(output).doesNotContain("┘");
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

            assertThat(output).doesNotContain("│");
            assertThat(output).doesNotContain("┌");
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
        @DisplayName("1ch solid border (10px) uses HEAVY line-drawing")
        void chBorderUsesHeavy() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Border");
            container.border("1ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1ch = 10px → HEAVY: ┏━┓ top, ┃ sides
            assertThat(output).contains("━");
            assertThat(output).contains("┃");
            assertThat(output).contains("Heavy Border");
        }

        @Test
        @DisplayName("1em solid border (10px) uses HEAVY line-drawing")
        void emBorderUsesHeavy() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Em");
            container.border("1em solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1em = 10px → HEAVY
            assertThat(output).contains("━");
            assertThat(output).contains("┃");
            assertThat(output).contains("Heavy Em");
        }

        @Test
        @DisplayName("0.1ch solid border (1px) uses LIGHT line-drawing")
        void tenthChBorderUsesLight() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Thin Ch");
            container.border("0.1ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.1ch = 1px → LIGHT: ─ │ ┌ ┐ └ ┘
            assertThat(output).contains("─");
            assertThat(output).contains("│");
        }

        @Test
        @DisplayName("0.25ch solid border (2.5px) uses LIGHT line-drawing")
        void quarterChBorderUsesLight() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Light Ch");
            container.border("0.25ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.25ch = 2.5px → LIGHT
            assertThat(output).contains("─");
            assertThat(output).contains("│");
        }

        @Test
        @DisplayName("0.1em solid border (1px) uses LIGHT line-drawing")
        void tenthEmBorderUsesLight() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Light Em");
            container.border("0.1em solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.1em = 1px → LIGHT
            assertThat(output).contains("─");
            assertThat(output).contains("│");
        }

        @Test
        @DisplayName("0.5em solid border (5px) matches 0.5ch solid border (both → HEAVY)")
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

            // Both 0.5em and 0.5ch = 5px → HEAVY
            assertThat(stripAnsi(rendererEm.result()))
                    .isEqualTo(stripAnsi(rendererCh.result()));
        }

        @Test
        @DisplayName("0.5ch solid border (5px → HEAVY) uses heavy chars")
        void halfChBorderUsesHeavy() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Heavy Half");
            container.border("0.5ch solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 0.5ch = 5px → HEAVY: ┃ sides, ━ horizontal
            assertThat(output).contains("┃");
            assertThat(output).contains("Heavy Half");
        }

        @Test
        @DisplayName("1rem solid border uses HEAVY (same as 1em)")
        void remBorderUsesHeavy() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Rem Heavy");
            container.border("1rem solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1rem = 10px → HEAVY
            assertThat(output).contains("━");
            assertThat(output).contains("Rem Heavy");
        }

        @Test
        @DisplayName("1ln solid border uses HEAVY (10px)")
        void lnBorderUsesHeavy() {
            ContainerSurface container = ContainerSurface.vertical()
                    .add("Ln Heavy");
            container.border("1ln solid");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = stripAnsi(renderer.result());

            // 1ln = 10px → HEAVY
            assertThat(output).contains("━");
            assertThat(output).contains("Ln Heavy");
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
                if (trimmed.contains("─")) {
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
                if (trimmed.contains("─")) {
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
                if (trimmed.contains("─")) {
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

            String emOut = stripAnsi(rendererEm.result());
            String chOut = stripAnsi(rendererCh.result());

            String emTopLine = emOut.lines().filter(l -> l.contains("─")).findFirst().orElse("").trim();
            String chTopLine = chOut.lines().filter(l -> l.contains("─")).findFirst().orElse("").trim();

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

            // 0.25ch = 2.5px → LIGHT border
            assertThat(output).contains("│");  // light vertical
            assertThat(output).contains("─");  // light horizontal
            assertThat(output).contains("Mixed Units");

            // 10em = 20 columns + 2 border chars = 22
            String topLine = output.lines().filter(l -> l.contains("─")).findFirst().orElse("").trim();
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

            // 0.1em = 1px → LIGHT border
            assertThat(output).contains("│");  // light vertical
            // 160px = 20 columns + 2 border = 22
            String topLine = output.lines().filter(l -> l.contains("─")).findFirst().orElse("").trim();
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

            // Stripped output should still have line-drawing chars
            String stripped = stripAnsi(rawOutput);
            assertThat(stripped).contains("│");  // light vertical border
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

            java.util.List<String> nonEmptyLines = new java.util.ArrayList<>();
            for (String line : lines) {
                if (!line.isEmpty()) {
                    nonEmptyLines.add(line);
                }
            }

            assertThat(nonEmptyLines).hasSizeGreaterThanOrEqualTo(3);

            // First non-empty line starts with ┌ (light corner)
            assertThat(nonEmptyLines.get(0).trim().charAt(0)).isEqualTo('┌');

            // Last non-empty line starts with └ (light corner)
            String lastLine = nonEmptyLines.get(nonEmptyLines.size() - 1).trim();
            assertThat(lastLine.charAt(0)).isEqualTo('└');

            // Middle lines have │ border
            for (int i = 1; i < nonEmptyLines.size() - 1; i++) {
                String line = nonEmptyLines.get(i).trim();
                assertThat(line.charAt(0)).isEqualTo('│');
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
