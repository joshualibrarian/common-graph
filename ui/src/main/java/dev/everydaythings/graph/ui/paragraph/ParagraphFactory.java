package dev.everydaythings.graph.ui.paragraph;

import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.List;

/**
 * Creates {@link ParagraphBuilder} instances for the active backend.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code SkiaParagraphFactory} — wraps Skia's Paragraph API</li>
 *   <li>{@code MsdfParagraphFactory} — custom layout with ICU4J + MSDF metrics</li>
 * </ul>
 *
 * @see ParagraphBuilder
 * @see Paragraph
 */
public interface ParagraphFactory {

    /**
     * Create a new paragraph builder with the given paragraph-level styles.
     *
     * @param paragraphStyles styles applying to the entire paragraph (e.g., "heading", "code")
     */
    ParagraphBuilder builder(List<String> paragraphStyles);

    /**
     * Convenience: build a paragraph from a list of styled spans.
     */
    default Paragraph fromSpans(List<TextSpan> spans, List<String> paragraphStyles, float maxWidth) {
        ParagraphBuilder b = builder(paragraphStyles);
        for (TextSpan span : spans) {
            b.pushStyle(span.styles());
            b.addText(span.text());
            b.popStyle();
        }
        Paragraph p = b.build();
        p.layout(maxWidth);
        return p;
    }

    /**
     * Convenience: build a paragraph from plain text.
     */
    default Paragraph fromText(String text, List<String> paragraphStyles, float maxWidth) {
        return fromSpans(List.of(new TextSpan(text, paragraphStyles)), paragraphStyles, maxWidth);
    }
}
