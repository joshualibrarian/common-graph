package dev.everydaythings.graph.ui.paragraph;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * A styled run of text within a paragraph.
 * Carries the text content and a list of style classes.
 */
@Getter @Accessors(fluent = true)
public class TextSpan {
    private String text;
    private List<String> styles;

    public TextSpan() {}

    public TextSpan text(String text) { this.text = text; return this; }
    public TextSpan styles(List<String> styles) { this.styles = styles; return this; }
}
