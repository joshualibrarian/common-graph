package dev.everydaythings.graph.ui.style;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.skia.LayoutNode;

import java.util.List;
import java.util.OptionalInt;

/**
 * Walks a {@link LayoutNode} tree and resolves each node's style classes
 * via a {@link Stylesheet}, setting concrete field values (color,
 * backgroundColor, fontSize, fontFamily, bold) on each node.
 *
 * <p>This decouples style-name → value resolution from the painters.
 * Painters consume only the resolved concrete values.
 *
 * <p>Must run <b>before</b> layout, since font size affects text measurement.
 */
public class StyleResolver {

    private final Stylesheet stylesheet;
    private final RenderContext context;
    private final float baseFontSize;

    public StyleResolver(Stylesheet stylesheet, RenderContext context, float baseFontSize) {
        this.stylesheet = stylesheet;
        this.context = context;
        this.baseFontSize = baseFontSize;
    }

    /**
     * Resolve styles for every node in the tree.
     */
    public void resolve(LayoutNode root) {
        resolveNode(root, null, null);
    }

    private void resolveNode(LayoutNode node,
                             String inheritedFontFamily,
                             String inheritedFontSizeSpec) {
        List<String> classes = node.styles();
        Selector.Builder sb = Selector.builder();
        if (classes != null && !classes.isEmpty()) {
            sb.classes(classes);
        }
        if (node.type() != null) {
            sb.type(node.type());
        }
        if (node.id() != null) {
            sb.id(node.id());
        }
        Selector element = sb.build();

        // Inherit container-level font family by default.
        if (inheritedFontFamily != null && !inheritedFontFamily.isEmpty()
                && (node.fontFamily() == null || node.fontFamily().isEmpty())) {
            node.fontFamily(inheritedFontFamily);
        }
        if (node instanceof LayoutNode.TextNode text
                && inheritedFontSizeSpec != null
                && !inheritedFontSizeSpec.isEmpty()
                && (text.fontSizeSpec() == null || text.fontSizeSpec().isEmpty())) {
            text.fontSizeSpec(inheritedFontSizeSpec);
        }

        StyleProperties props = stylesheet.resolve(element, context);

        if (!props.isEmpty()) {
            // Display: hidden
            if (props.isHidden()) {
                node.hidden(true);
            }

            // Color
            OptionalInt color = props.getColorInt();
            if (color.isPresent()) {
                node.color(color.getAsInt());
            }

            // Background color
            OptionalInt bgColor = props.getBackgroundColorInt();
            if (bgColor.isPresent()) {
                node.backgroundColor(bgColor.getAsInt());
            }

            // Font size
            float ratio = props.getFontSizeRatio();
            if (ratio != 1.0f) {
                node.fontSize(baseFontSize * ratio);
            }

            // Font family
            props.getFontFamily().ifPresent(node::fontFamily);

            // Bold
            if (props.isBold()) {
                node.bold(true);
            }
        }

        // Also resolve "format" field on TextNode as a style hint
        if (node instanceof LayoutNode.TextNode text) {
            if ("code".equals(text.format()) && node.color() == -1) {
                // Code format acts like .code style class if no color already resolved
                StyleProperties codeProps = stylesheet.resolve(
                        Selector.ofClasses("code"), context);
                OptionalInt codeColor = codeProps.getColorInt();
                if (codeColor.isPresent()) {
                    node.color(codeColor.getAsInt());
                }
                OptionalInt codeBg = codeProps.getBackgroundColorInt();
                if (codeBg.isPresent()) {
                    node.backgroundColor(codeBg.getAsInt());
                }
                codeProps.getFontFamily().ifPresent(node::fontFamily);
            }
        }

        String nextInheritedFontFamily = node.fontFamily();
        if (nextInheritedFontFamily == null || nextInheritedFontFamily.isEmpty()) {
            nextInheritedFontFamily = inheritedFontFamily;
        }
        String nextInheritedFontSizeSpec = inheritedFontSizeSpec;
        if (node instanceof LayoutNode.BoxNode box
                && box.fontSizeSpec() != null && !box.fontSizeSpec().isEmpty()) {
            nextInheritedFontSizeSpec = box.fontSizeSpec();
        }

        // Recurse into children
        if (node instanceof LayoutNode.BoxNode box) {
            for (LayoutNode child : box.children()) {
                resolveNode(child, nextInheritedFontFamily, nextInheritedFontSizeSpec);
            }
        }
    }
}
