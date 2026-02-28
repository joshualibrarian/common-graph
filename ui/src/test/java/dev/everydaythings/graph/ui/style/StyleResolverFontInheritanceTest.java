package dev.everydaythings.graph.ui.style;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StyleResolver font inheritance")
class StyleResolverFontInheritanceTest {

    @Test
    @DisplayName("container font family percolates to descendant text")
    void containerFontPercolates() {
        LayoutNode.BoxNode root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("root"));
        root.fontFamily("Symbols Nerd Font Mono");

        LayoutNode.TextNode child = new LayoutNode.TextNode("abc", List.of("child"));
        root.addChild(child);

        StyleResolver resolver = new StyleResolver(Stylesheet.EMPTY, RenderContext.gui(), 15f);
        resolver.resolve(root);

        assertThat(child.fontFamily()).isEqualTo("Symbols Nerd Font Mono");
    }

    @Test
    @DisplayName("text node font family overrides inherited container family")
    void textOverrideWins() {
        LayoutNode.BoxNode root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("root"));
        root.fontFamily("Symbols Nerd Font Mono");

        LayoutNode.TextNode child = new LayoutNode.TextNode("abc", List.of("child"));
        child.fontFamily("SF Pro");
        root.addChild(child);

        StyleResolver resolver = new StyleResolver(Stylesheet.EMPTY, RenderContext.gui(), 15f);
        resolver.resolve(root);

        assertThat(child.fontFamily()).isEqualTo("SF Pro");
    }

    @Test
    @DisplayName("container font size percolates to descendant text")
    void containerFontSizePercolates() {
        LayoutNode.BoxNode root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("root"));
        root.fontSizeSpec("80%");

        LayoutNode.TextNode child = new LayoutNode.TextNode("abc", List.of("child"));
        root.addChild(child);

        StyleResolver resolver = new StyleResolver(Stylesheet.EMPTY, RenderContext.gui(), 15f);
        resolver.resolve(root);

        assertThat(child.fontSizeSpec()).isEqualTo("80%");
    }

    @Test
    @DisplayName("text node font size overrides inherited container size")
    void textFontSizeOverrideWins() {
        LayoutNode.BoxNode root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("root"));
        root.fontSizeSpec("80%");

        LayoutNode.TextNode child = new LayoutNode.TextNode("abc", List.of("child"));
        child.fontSizeSpec("24px");
        root.addChild(child);

        StyleResolver resolver = new StyleResolver(Stylesheet.EMPTY, RenderContext.gui(), 15f);
        resolver.resolve(root);

        assertThat(child.fontSizeSpec()).isEqualTo("24px");
    }
}
