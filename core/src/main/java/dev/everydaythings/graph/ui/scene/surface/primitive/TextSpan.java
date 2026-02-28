package dev.everydaythings.graph.ui.scene.surface.primitive;

import java.util.List;

/**
 * A styled run of text within a rich text paragraph.
 *
 * <p>TextSpan pairs content with style classes. Multiple spans compose
 * a rich text paragraph where each run can have different formatting.
 *
 * <p>Style classes follow the Surface system convention:
 * <ul>
 *   <li>"bold", "italic" — font weight/style</li>
 *   <li>"code", "mono" — monospace font</li>
 *   <li>"heading", "small" — font size</li>
 *   <li>"muted", "accent" — color variations</li>
 * </ul>
 *
 * @param text   The text content of this span
 * @param styles Style classes applied to this span
 */
public record TextSpan(String text, List<String> styles) {

    public TextSpan {
        if (text == null) text = "";
        if (styles == null) styles = List.of();
    }

    public static TextSpan of(String text) {
        return new TextSpan(text, List.of());
    }

    public static TextSpan of(String text, String... styles) {
        return new TextSpan(text, List.of(styles));
    }
}
