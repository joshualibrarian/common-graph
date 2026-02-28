package dev.everydaythings.graph.surface.harness.assertions;

import dev.everydaythings.graph.surface.harness.RenderResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TUI-specific assertions for ANSI output.
 *
 * <p>Provides helper methods for asserting ANSI escape codes,
 * styled text, and terminal-specific formatting.
 */
public final class TuiAssertions {

    private TuiAssertions() {}

    // ==================== ANSI Code Constants ====================

    // Text styles
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String BLINK = "\u001B[5m";
    public static final String REVERSE = "\u001B[7m";
    public static final String HIDDEN = "\u001B[8m";
    public static final String STRIKETHROUGH = "\u001B[9m";

    // Foreground colors (standard)
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    // Bright foreground colors
    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    // ==================== Assertions ====================

    /**
     * Assert that the output contains any ANSI escape codes.
     */
    public static void assertHasAnsi(RenderResult.TextResult result) {
        assertThat(result.output())
            .as("TUI output should contain ANSI escape codes")
            .contains("\u001B[");
    }

    /**
     * Assert that the output contains a specific ANSI code.
     */
    public static void assertContainsAnsi(RenderResult.TextResult result, String ansiCode) {
        assertThat(result.output())
            .as("Should contain ANSI code %s", escapeAnsi(ansiCode))
            .contains(ansiCode);
    }

    /**
     * Assert that the output does NOT contain a specific ANSI code.
     */
    public static void assertNotContainsAnsi(RenderResult.TextResult result, String ansiCode) {
        assertThat(result.output())
            .as("Should NOT contain ANSI code %s", escapeAnsi(ansiCode))
            .doesNotContain(ansiCode);
    }

    /**
     * Assert that text appears with a specific ANSI style.
     *
     * <p>This checks that the ANSI code appears before the text
     * in the output, indicating the text is styled.
     */
    public static void assertStyledText(RenderResult.TextResult result, String text, String ansiCode) {
        String output = result.output();
        assertThat(output)
            .as("Output should contain text '%s'", text)
            .contains(text);
        assertThat(output)
            .as("Output should contain ANSI code %s", escapeAnsi(ansiCode))
            .contains(ansiCode);

        // Verify the ANSI code appears before the text
        int ansiPos = output.indexOf(ansiCode);
        int textPos = output.indexOf(text);
        assertThat(ansiPos)
            .as("ANSI code should appear before text '%s'", text)
            .isLessThan(textPos);
    }

    /**
     * Assert that output contains bold text.
     */
    public static void assertBoldText(RenderResult.TextResult result, String text) {
        assertStyledText(result, text, BOLD);
    }

    /**
     * Assert that output contains colored text.
     */
    public static void assertColoredText(RenderResult.TextResult result, String text, String colorCode) {
        assertStyledText(result, text, colorCode);
    }

    /**
     * Assert the line count is within a range.
     */
    public static void assertLineCount(RenderResult.TextResult result, int min, int max) {
        int lines = result.lineCount();
        assertThat(lines)
            .as("Line count should be between %d and %d", min, max)
            .isBetween(min, max);
    }

    /**
     * Assert the output ends with a reset code (clean terminal state).
     */
    public static void assertEndsWithReset(RenderResult.TextResult result) {
        assertThat(result.output())
            .as("Output should end with ANSI reset")
            .endsWith(RESET);
    }

    // ==================== Helpers ====================

    /**
     * Escape ANSI codes for readable error messages.
     */
    public static String escapeAnsi(String ansi) {
        return ansi.replace("\u001B", "\\e");
    }

    /**
     * Format ANSI code for display.
     */
    public static String describAnsi(String ansiCode) {
        if (ansiCode.equals(BOLD)) return "BOLD";
        if (ansiCode.equals(DIM)) return "DIM";
        if (ansiCode.equals(UNDERLINE)) return "UNDERLINE";
        if (ansiCode.equals(REVERSE)) return "REVERSE";
        if (ansiCode.equals(RED)) return "RED";
        if (ansiCode.equals(GREEN)) return "GREEN";
        if (ansiCode.equals(YELLOW)) return "YELLOW";
        if (ansiCode.equals(BLUE)) return "BLUE";
        if (ansiCode.equals(CYAN)) return "CYAN";
        if (ansiCode.equals(MAGENTA)) return "MAGENTA";
        if (ansiCode.equals(RESET)) return "RESET";
        return escapeAnsi(ansiCode);
    }
}
