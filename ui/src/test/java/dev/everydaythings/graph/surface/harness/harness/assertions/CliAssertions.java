package dev.everydaythings.graph.surface.harness.assertions;

import dev.everydaythings.graph.surface.harness.RenderResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI-specific assertions for plain text output.
 *
 * <p>Provides helper methods for asserting plain text output
 * without ANSI codes, Unicode characters, and text formatting.
 */
public final class CliAssertions {

    private CliAssertions() {}

    // ==================== Unicode Constants ====================

    // Box drawing characters
    public static final String BOX_VERTICAL = "│";
    public static final String BOX_HORIZONTAL = "─";
    public static final String BOX_DOWN_RIGHT = "┌";
    public static final String BOX_DOWN_LEFT = "┐";
    public static final String BOX_UP_RIGHT = "└";
    public static final String BOX_UP_LEFT = "┘";
    public static final String BOX_VERTICAL_RIGHT = "├";
    public static final String BOX_VERTICAL_LEFT = "┤";

    // Tree drawing characters
    public static final String TREE_BRANCH = "├──";
    public static final String TREE_LAST = "└──";
    public static final String TREE_VERTICAL = "│";
    public static final String TREE_SPACE = "   ";

    // Bullets and markers
    public static final String BULLET = "•";
    public static final String ARROW_RIGHT = "▶";
    public static final String ARROW_DOWN = "▼";
    public static final String ARROW_RIGHT_HOLLOW = "▷";
    public static final String CHECKMARK = "✓";
    public static final String CROSSMARK = "✗";

    // ==================== Assertions ====================

    /**
     * Assert that output contains NO ANSI escape codes.
     *
     * <p>CLI output should be plain text without terminal formatting.
     */
    public static void assertNoAnsi(RenderResult.TextResult result) {
        assertThat(result.output())
            .as("CLI output should not contain ANSI escape codes")
            .doesNotContain("\u001B[");
    }

    /**
     * Assert that output contains a specific Unicode character.
     */
    public static void assertContainsUnicode(RenderResult.TextResult result, String unicode) {
        assertThat(result.output())
            .as("Should contain unicode character '%s'", unicode)
            .contains(unicode);
    }

    /**
     * Assert that output contains text wrapped in backticks (code style).
     */
    public static void assertBacktickWrapped(RenderResult.TextResult result, String text) {
        assertThat(result.output())
            .as("Text '%s' should be wrapped in backticks", text)
            .contains("`" + text + "`");
    }

    /**
     * Assert that output contains a tree branch structure.
     */
    public static void assertTreeBranch(RenderResult.TextResult result, String nodeText) {
        String output = result.output();
        assertThat(output)
            .as("Should contain tree node '%s'", nodeText)
            .contains(nodeText);

        // Should have tree structure characters
        boolean hasTreeChars = output.contains(TREE_BRANCH) ||
                               output.contains(TREE_LAST) ||
                               output.contains(TREE_VERTICAL);
        assertThat(hasTreeChars)
            .as("Should contain tree structure characters")
            .isTrue();
    }

    /**
     * Assert proper indentation (spaces at line start).
     */
    public static void assertIndented(RenderResult.TextResult result, String text, int minSpaces) {
        String output = result.output();
        String[] lines = output.split("\n");

        boolean found = false;
        for (String line : lines) {
            if (line.contains(text)) {
                int leadingSpaces = line.length() - line.stripLeading().length();
                assertThat(leadingSpaces)
                    .as("Text '%s' should have at least %d leading spaces", text, minSpaces)
                    .isGreaterThanOrEqualTo(minSpaces);
                found = true;
                break;
            }
        }
        assertThat(found)
            .as("Text '%s' should be present in output", text)
            .isTrue();
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
     * Assert output is a single line (no newlines).
     */
    public static void assertSingleLine(RenderResult.TextResult result) {
        assertThat(result.lineCount())
            .as("Output should be a single line")
            .isEqualTo(1);
    }

    /**
     * Assert output is empty or whitespace only.
     */
    public static void assertEmpty(RenderResult.TextResult result) {
        assertThat(result.stripAnsi().trim())
            .as("Output should be empty")
            .isEmpty();
    }

    /**
     * Assert output matches a pattern (without ANSI codes).
     */
    public static void assertMatchesPattern(RenderResult.TextResult result, String regex) {
        assertThat(result.stripAnsi())
            .as("Output should match pattern: %s", regex)
            .matches(regex);
    }
}
