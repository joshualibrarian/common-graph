package dev.everydaythings.graph.ui.style;

import dev.everydaythings.graph.ui.scene.RenderContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the CSS-like style system.
 */
class StylesheetTest {

    @Test
    void testSelectorParsing() {
        // Type only
        Selector sel = Selector.parse("TreeNode");
        assertThat(sel.type()).isEqualTo("TreeNode");
        assertThat(sel.hasClasses()).isFalse();

        // Class only
        sel = Selector.parse(".chrome");
        assertThat(sel.hasType()).isFalse();
        assertThat(sel.classes()).containsExactly("chrome");

        // ID only
        sel = Selector.parse("#root");
        assertThat(sel.id()).isEqualTo("root");

        // Renderer only
        sel = Selector.parse("!tui");
        assertThat(sel.renderer()).isEqualTo("tui");

        // Breakpoint only
        sel = Selector.parse("@sm");
        assertThat(sel.breakpoint()).isEqualTo("sm");

        // State only
        sel = Selector.parse(":hover");
        assertThat(sel.states()).containsExactly("hover");

        // Combined
        sel = Selector.parse("TreeNode.chrome.selected#root!tui@md:hover:expanded");
        assertThat(sel.type()).isEqualTo("TreeNode");
        assertThat(sel.classes()).containsExactlyInAnyOrder("chrome", "selected");
        assertThat(sel.id()).isEqualTo("root");
        assertThat(sel.renderer()).isEqualTo("tui");
        assertThat(sel.breakpoint()).isEqualTo("md");
        assertThat(sel.states()).containsExactlyInAnyOrder("hover", "expanded");
    }

    @Test
    void testSelectorToString() {
        Selector sel = Selector.builder()
                .type("TreeNode")
                .addClass("chrome")
                .id("root")
                .renderer("tui")
                .addState("selected")
                .build();

        String str = sel.toString();
        assertThat(str).contains("TreeNode");
        assertThat(str).contains(".chrome");
        assertThat(str).contains("#root");
        assertThat(str).contains("!tui");
        assertThat(str).contains(":selected");
    }

    @Test
    void testSelectorMatching() {
        // Rule selector
        Selector rule = Selector.parse("TreeNode.chrome!tui");

        // Element selector
        Selector element = Selector.element("TreeNode", List.of("chrome", "other"), "node-1");

        // Contexts
        RenderContext tuiContext = RenderContext.tui();
        RenderContext guiContext = RenderContext.gui();

        // Should match in TUI
        assertThat(rule.matches(element, tuiContext)).isTrue();

        // Should NOT match in GUI (wrong renderer)
        assertThat(rule.matches(element, guiContext)).isFalse();
    }

    @Test
    void testSelectorMatchingWithState() {
        Selector rule = Selector.parse("TreeNode:selected");
        Selector element = Selector.element("TreeNode", List.of(), "node-1");

        RenderContext notSelected = RenderContext.tui();
        RenderContext selected = RenderContext.tui().withState("selected");

        assertThat(rule.matches(element, notSelected)).isFalse();
        assertThat(rule.matches(element, selected)).isTrue();
    }

    @Test
    void testSpecificity() {
        // ID is most specific
        assertThat(Selector.parse("#root").specificity()).isEqualTo(100);

        // Classes, states, renderer, breakpoint are 10 each
        assertThat(Selector.parse(".chrome").specificity()).isEqualTo(10);
        assertThat(Selector.parse(":hover").specificity()).isEqualTo(10);
        assertThat(Selector.parse("!tui").specificity()).isEqualTo(10);
        assertThat(Selector.parse("@sm").specificity()).isEqualTo(10);

        // Type is 1
        assertThat(Selector.parse("TreeNode").specificity()).isEqualTo(1);

        // Combined
        assertThat(Selector.parse("TreeNode.chrome#root").specificity()).isEqualTo(111);
    }

    @Test
    void testStylePropertiesMerge() {
        StyleProperties base = StyleProperties.builder()
                .dim()
                .color("blue")
                .build();

        StyleProperties overlay = StyleProperties.builder()
                .bold()
                .color("red")  // overrides blue
                .build();

        StyleProperties merged = base.merge(overlay);

        assertThat(merged.isDim()).isTrue();  // from base
        assertThat(merged.isBold()).isTrue();  // from overlay
        assertThat(merged.getColor()).hasValue("red");  // overlay wins
    }

    @Test
    void testStylesheetResolution() {
        Stylesheet stylesheet = Stylesheet.builder()
                // Base rule for all TreeNodes
                .rule("TreeNode", props -> props.color("gray"))
                // More specific rule for chrome in TUI
                .rule("TreeNode.chrome!tui", props -> props.visible().dim())
                // Even more specific for selected
                .rule("TreeNode.chrome!tui:selected", props -> props.reverse())
                .build();

        Selector element = Selector.element("TreeNode", List.of("chrome"), "node-1");
        RenderContext context = RenderContext.tui().withState("selected");

        StyleProperties computed = stylesheet.resolve(element, context);

        // Should have color from base rule
        assertThat(computed.getColor()).hasValue("gray");
        // Should have dim from chrome!tui rule
        assertThat(computed.isDim()).isTrue();
        // Should have reverse from selected rule
        assertThat(computed.isReverse()).isTrue();
    }

    @Test
    void testStylesheetHiddenInGui() {
        Stylesheet stylesheet = Stylesheet.builder()
                .rule(".chrome!tui", props -> props.visible())
                .rule(".chrome!gui", props -> props.hidden())
                .build();

        Selector element = Selector.ofClasses("chrome");

        StyleProperties tuiProps = stylesheet.resolve(element, RenderContext.tui());
        StyleProperties guiProps = stylesheet.resolve(element, RenderContext.gui());

        assertThat(tuiProps.isVisible()).isTrue();
        assertThat(guiProps.isHidden()).isTrue();
    }

    @Test
    void testFromClasspathFindsAnnotatedRules() {
        Stylesheet stylesheet = Stylesheet.fromClasspath();

        // Tree chrome-text rules from TreeSurface (visibility per renderer)
        Selector treeChromeText = Selector.builder()
                .addClass("chrome-text")
                .build();

        StyleProperties tuiProps = stylesheet.resolve(treeChromeText, RenderContext.tui());
        assertThat(tuiProps.isVisible()).isTrue();
        assertThat(tuiProps.isDim()).isTrue();

        StyleProperties guiProps = stylesheet.resolve(treeChromeText, RenderContext.gui());
        assertThat(guiProps.isHidden()).isTrue();

        // Tree chrome color rule from TreeSurface
        Selector treeChrome = Selector.builder()
                .addClass("chrome")
                .build();
        StyleProperties chromeProps = stylesheet.resolve(treeChrome, RenderContext.gui());
        assertThat(chromeProps.getColorInt()).isPresent();

        // Heading rule from Item
        Selector heading = Selector.ofClasses("heading");
        StyleProperties headingProps = stylesheet.resolve(heading, RenderContext.gui());
        assertThat(headingProps.getColorInt()).isPresent();
        assertThat(headingProps.getFontSizeRatio()).isEqualTo(1.33f);

        // Chess square rules from ChessBoard
        Selector lightSquare = Selector.builder()
                .addClass("square")
                .addClass("light")
                .build();
        StyleProperties lightProps = stylesheet.resolve(lightSquare, RenderContext.gui());
        assertThat(lightProps.getBackgroundColorInt()).isPresent();
        assertThat(lightProps.getBackgroundColorInt().getAsInt()).isEqualTo(0xFFF0D9B5);

        // Minesweeper tile rules from Minesweeper
        Selector mineTile = Selector.builder()
                .addClass("tile")
                .addClass("hidden")
                .build();
        StyleProperties tileProps = stylesheet.resolve(mineTile, RenderContext.gui());
        assertThat(tileProps.getBackgroundColorInt()).isPresent();
        assertThat(tileProps.getBackgroundColorInt().getAsInt()).isEqualTo(0xFF585B70);

        // Handle label rule from HandleSurface
        Selector handleLabel = Selector.ofClasses("handle-label");
        StyleProperties handleProps = stylesheet.resolve(handleLabel, RenderContext.gui());
        assertThat(handleProps.isBold()).isTrue();
    }

    @Test
    void testParseHexColor() {
        assertThat(Stylesheet.parseHexColor("#89B4FA")).isEqualTo(0xFF89B4FA);
        assertThat(Stylesheet.parseHexColor("#FF313244")).isEqualTo(0xFF313244);
        assertThat(Stylesheet.parseHexColor("#000000")).isEqualTo(0xFF000000);
        assertThat(Stylesheet.parseHexColor("#FFFFFF")).isEqualTo(0xFFFFFFFF);
    }
}
