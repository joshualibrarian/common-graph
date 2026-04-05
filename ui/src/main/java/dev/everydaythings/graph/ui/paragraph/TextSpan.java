package dev.everydaythings.graph.ui.paragraph;

import java.util.List;

/**
 * A styled run of text within a paragraph.
 * Carries the text content and a list of style classes.
 */
public record TextSpan(String text, List<String> styles) {
    public TextSpan(String text) {
        this(text, List.of());
    }
}
