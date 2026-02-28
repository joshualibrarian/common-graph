package dev.everydaythings.graph.ui.paragraph;

import java.util.List;

/**
 * Builds a {@link Paragraph} from styled text runs.
 *
 * <p>Style classes are pushed and popped around text additions,
 * allowing mixed-style paragraphs:
 * <pre>{@code
 * Paragraph p = builder.pushStyle(List.of("bold"))
 *                      .addText("Hello ")
 *                      .popStyle()
 *                      .addText("world")
 *                      .build();
 * }</pre>
 *
 * @see ParagraphFactory
 */
public interface ParagraphBuilder {

    /**
     * Push style classes for subsequent text.
     * Styles accumulate until popped.
     */
    ParagraphBuilder pushStyle(List<String> styles);

    /** Pop the most recently pushed style. */
    ParagraphBuilder popStyle();

    /** Add text content with the current style. */
    ParagraphBuilder addText(String text);

    /** Build the paragraph. Caller must close the result. */
    Paragraph build();
}
