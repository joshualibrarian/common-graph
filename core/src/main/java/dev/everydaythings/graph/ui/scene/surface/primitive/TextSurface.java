package dev.everydaythings.graph.ui.scene.surface.primitive;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.util.List;

/**
 * Surface for displaying text content.
 *
 * <p>TextSurface is the default surface for String fields.
 * It renders plain text, optionally with a format applied.
 */
public class TextSurface extends SurfaceSchema<String> {

    /**
     * The text content to display.
     */
    @Canon(order = 10)
    private String content;

    /**
     * Format for interpreting the content.
     * Examples: "plain", "date", "currency", "code", "json"
     */
    @Canon(order = 11)
    private String format;

    /**
     * Binding expression for dynamic text content.
     *
     * <p>When set, the expression is resolved against the root value at render time,
     * and the result is used as the text content. Takes precedence over static {@code content}.
     *
     * <p>Examples: {@code "value.sideToMoveLabel"}, {@code "value.statusLabel"}.
     */
    @Canon(order = 12)
    private String bind;

    /**
     * Rich text spans with per-span styling.
     * When non-empty, rendered via richText() instead of text().
     */
    @Canon(order = 13)
    private List<TextSpan> spans;

    public TextSurface() {}

    public TextSurface(String content) {
        this.content = content;
    }

    public static TextSurface of(String content) {
        return new TextSurface(content);
    }

    public TextSurface content(String content) {
        this.content = content;
        return this;
    }

    public TextSurface format(String format) {
        this.format = format;
        return this;
    }

    public String content() {
        return content;
    }

    public String format() {
        return format;
    }

    public TextSurface bind(String bindExpr) {
        this.bind = bindExpr;
        return this;
    }

    public String bind() {
        return bind;
    }

    public List<TextSpan> spans() {
        return spans;
    }

    public TextSurface spans(List<TextSpan> spans) {
        this.spans = spans;
        return this;
    }

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        if (spans != null && !spans.isEmpty()) {
            out.richText(spans, style());
        } else if (format != null && !format.isEmpty()) {
            out.formattedText(content, format, style());
        } else {
            out.text(content, style());
        }
    }
}
