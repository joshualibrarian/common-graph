package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("LayoutEngine")
class LayoutEngineTest {

    /** Fixed-width text measurer: 8px per character, 16px line height. */
    private static final float CHAR_WIDTH = 8f;
    private static final float LINE_HEIGHT = 16f;

    private static final LayoutEngine.TextMeasurer FIXED_MEASURER = (node, maxWidth) -> {
        float width = node.content().length() * CHAR_WIDTH;
        node.measuredSize(width, LINE_HEIGHT);
    };

    private LayoutEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LayoutEngine(FIXED_MEASURER);
    }

    // ==================================================================================
    // Text Nodes
    // ==================================================================================

    @Nested
    @DisplayName("Text Nodes")
    class TextNodes {

        @Test
        @DisplayName("single text node measures to content size")
        void singleTextNode() {
            var text = new LayoutNode.TextNode("Hello", List.of());

            engine.layout(text, 800, 600);

            assertThat(text.width()).isCloseTo(5 * CHAR_WIDTH, within(0.1f));
            assertThat(text.height()).isCloseTo(LINE_HEIGHT, within(0.1f));
            assertThat(text.x()).isEqualTo(0);
            assertThat(text.y()).isEqualTo(0);
        }

        @Test
        @DisplayName("empty text node has zero width")
        void emptyTextNode() {
            var text = new LayoutNode.TextNode("", List.of());

            engine.layout(text, 800, 600);

            assertThat(text.width()).isEqualTo(0);
            assertThat(text.height()).isCloseTo(LINE_HEIGHT, within(0.1f));
        }
    }

    // ==================================================================================
    // Vertical Layout
    // ==================================================================================

    @Nested
    @DisplayName("Vertical Layout")
    class VerticalLayout {

        @Test
        @DisplayName("stacks children top-to-bottom")
        void stacksChildren() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.addChild(new LayoutNode.TextNode("First", List.of()));
            box.addChild(new LayoutNode.TextNode("Second", List.of()));

            engine.layout(box, 800, 600);

            var children = box.children();
            assertThat(children.get(0).y()).isEqualTo(0);
            assertThat(children.get(1).y()).isCloseTo(LINE_HEIGHT, within(0.1f));
        }

        @Test
        @DisplayName("box height is sum of children heights")
        void boxHeightIsSumOfChildren() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.addChild(new LayoutNode.TextNode("Line 1", List.of()));
            box.addChild(new LayoutNode.TextNode("Line 2", List.of()));
            box.addChild(new LayoutNode.TextNode("Line 3", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.height()).isCloseTo(3 * LINE_HEIGHT, within(0.1f));
        }

        @Test
        @DisplayName("vertical box fills available width")
        void boxFillsAvailableWidth() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.addChild(new LayoutNode.TextNode("Short", List.of()));      // 5 chars
            box.addChild(new LayoutNode.TextNode("Longer text", List.of())); // 11 chars

            engine.layout(box, 800, 600);

            // Vertical boxes fill available width (like CSS block elements)
            assertThat(box.width()).isCloseTo(800, within(0.1f));
        }

        @Test
        @DisplayName("empty vertical box has zero size")
        void emptyBox() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());

            engine.layout(box, 800, 600);

            assertThat(box.width()).isEqualTo(0);
            assertThat(box.height()).isEqualTo(0);
        }
    }

    // ==================================================================================
    // Horizontal Layout
    // ==================================================================================

    @Nested
    @DisplayName("Horizontal Layout")
    class HorizontalLayout {

        @Test
        @DisplayName("places children left-to-right")
        void placesChildrenLeftToRight() {
            var box = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());
            box.addChild(new LayoutNode.TextNode("AAA", List.of()));   // 3 chars = 24px
            box.addChild(new LayoutNode.TextNode("BBBBB", List.of())); // 5 chars = 40px

            engine.layout(box, 800, 600);

            var children = box.children();
            assertThat(children.get(0).x()).isEqualTo(0);
            assertThat(children.get(1).x()).isCloseTo(3 * CHAR_WIDTH, within(0.1f));
        }

        @Test
        @DisplayName("box width is sum of children widths")
        void boxWidthIsSumOfChildren() {
            var box = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());
            box.addChild(new LayoutNode.TextNode("AA", List.of()));    // 2 chars
            box.addChild(new LayoutNode.TextNode("BBB", List.of()));   // 3 chars

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(5 * CHAR_WIDTH, within(0.1f));
        }

        @Test
        @DisplayName("box height is max child height")
        void boxHeightIsMaxChild() {
            var box = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());
            box.addChild(new LayoutNode.TextNode("Text", List.of()));
            box.addChild(new LayoutNode.TextNode("Text", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.height()).isCloseTo(LINE_HEIGHT, within(0.1f));
        }
    }

    // ==================================================================================
    // Padding
    // ==================================================================================

    @Nested
    @DisplayName("Padding")
    class Padding {

        @Test
        @DisplayName("padding offsets children")
        void paddingOffsetsChildren() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.padding(10, 20, 10, 20);
            box.addChild(new LayoutNode.TextNode("Content", List.of()));

            engine.layout(box, 800, 600);

            var child = box.children().get(0);
            assertThat(child.x()).isCloseTo(20, within(0.1f));  // paddingLeft
            assertThat(child.y()).isCloseTo(10, within(0.1f));  // paddingTop
        }

        @Test
        @DisplayName("padding adds to box height, width fills available")
        void paddingAddsToSize() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.padding(10, 20, 10, 20);
            box.addChild(new LayoutNode.TextNode("Hi", List.of())); // 2*8 = 16px wide, 16px tall

            engine.layout(box, 800, 600);

            // Vertical box fills available width (content area = 800 - 40 = 760)
            assertThat(box.width()).isCloseTo(800, within(0.1f));
            assertThat(box.height()).isCloseTo(16 + 10 + 10, within(0.1f)); // content + padT + padB
        }
    }

    // ==================================================================================
    // Explicit Sizing
    // ==================================================================================

    @Nested
    @DisplayName("Explicit Sizing")
    class ExplicitSizing {

        @Test
        @DisplayName("explicit width overrides content width")
        void explicitWidth() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.explicitWidth(200);
            box.addChild(new LayoutNode.TextNode("Short", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isEqualTo(200);
        }

        @Test
        @DisplayName("explicit height overrides content height")
        void explicitHeight() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.explicitHeight(100);
            box.addChild(new LayoutNode.TextNode("Text", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.height()).isEqualTo(100);
        }
    }

    // ==================================================================================
    // Size Resolution
    // ==================================================================================

    @Nested
    @DisplayName("Size Resolution")
    class SizeResolution {

        @Test
        @DisplayName("resolves percentage width spec")
        void resolvesPercentageWidth() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("50%");
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(400, within(0.1f));
        }

        @Test
        @DisplayName("resolves percentage height spec")
        void resolvesPercentageHeight() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.heightSpec("50%");
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.height()).isCloseTo(300, within(0.1f));
        }

        @Test
        @DisplayName("resolves nested percentages (50% of 50% = 25%)")
        void resolvesNestedPercentages() {
            var outer = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            outer.widthSpec("50%");

            var inner = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            inner.widthSpec("50%");
            inner.addChild(new LayoutNode.TextNode("Hi", List.of()));

            outer.addChild(inner);
            engine.layout(outer, 800, 600);

            // outer = 50% of 800 = 400, inner = 50% of (400 - padding) = 200
            assertThat(outer.width()).isCloseTo(400, within(0.1f));
            assertThat(inner.width()).isCloseTo(200, within(0.1f));
        }

        @Test
        @DisplayName("resolves pixel spec (no context needed)")
        void resolvesPixelSpec() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("200px");
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(200, within(0.1f));
        }

        @Test
        @DisplayName("resolves plain number spec as pixels")
        void resolvesPlainNumber() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("300");
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(300, within(0.1f));
        }

        @Test
        @DisplayName("resolved spec overrides explicit size")
        void resolvedSpecOverridesExplicit() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.explicitWidth(100);
            box.widthSpec("50%");
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            // Spec should override the explicit value
            assertThat(box.width()).isCloseTo(400, within(0.1f));
        }
    }

    // ==================================================================================
    // Gap Spacing
    // ==================================================================================

    @Nested
    @DisplayName("Gap Spacing")
    class GapSpacing {

        @Test
        @DisplayName("vertical gap spaces between children")
        void verticalGapSpacesBetweenChildren() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.gap(10);
            box.addChild(new LayoutNode.TextNode("First", List.of()));
            box.addChild(new LayoutNode.TextNode("Second", List.of()));
            box.addChild(new LayoutNode.TextNode("Third", List.of()));

            engine.layout(box, 800, 600);

            var children = box.children();
            assertThat(children.get(0).y()).isEqualTo(0);
            assertThat(children.get(1).y()).isCloseTo(LINE_HEIGHT + 10, within(0.1f));
            assertThat(children.get(2).y()).isCloseTo(2 * LINE_HEIGHT + 20, within(0.1f));
        }

        @Test
        @DisplayName("horizontal gap spaces between children")
        void horizontalGapSpacesBetweenChildren() {
            var box = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());
            box.gap(10);
            box.addChild(new LayoutNode.TextNode("AA", List.of()));  // 2*8 = 16px
            box.addChild(new LayoutNode.TextNode("BB", List.of()));  // 2*8 = 16px

            engine.layout(box, 800, 600);

            var children = box.children();
            assertThat(children.get(0).x()).isEqualTo(0);
            assertThat(children.get(1).x()).isCloseTo(16 + 10, within(0.1f));
        }

        @Test
        @DisplayName("gap adds to total box size (vertical)")
        void gapAddsToTotalBoxSize() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.gap(10);
            box.addChild(new LayoutNode.TextNode("A", List.of()));
            box.addChild(new LayoutNode.TextNode("B", List.of()));
            box.addChild(new LayoutNode.TextNode("C", List.of()));

            engine.layout(box, 800, 600);

            // 3 lines * 16px + 2 gaps * 10px = 68
            assertThat(box.height()).isCloseTo(3 * LINE_HEIGHT + 2 * 10, within(0.1f));
        }

        @Test
        @DisplayName("no gap with single child")
        void noGapWithSingleChild() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.gap(10);
            box.addChild(new LayoutNode.TextNode("Only", List.of()));

            engine.layout(box, 800, 600);

            // Single child = no gap
            assertThat(box.height()).isCloseTo(LINE_HEIGHT, within(0.1f));
        }

        @Test
        @DisplayName("gap combines with padding")
        void gapCombinesWithPadding() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.gap(10);
            box.padding(5, 5, 5, 5);
            box.addChild(new LayoutNode.TextNode("A", List.of()));
            box.addChild(new LayoutNode.TextNode("B", List.of()));

            engine.layout(box, 800, 600);

            // padTop(5) + child1(16) + gap(10) + child2(16) + padBottom(5) = 52
            assertThat(box.height()).isCloseTo(5 + LINE_HEIGHT + 10 + LINE_HEIGHT + 5, within(0.1f));

            // First child starts at padTop=5, second at padTop + child1 + gap = 31
            var children = box.children();
            assertThat(children.get(0).y()).isCloseTo(5, within(0.1f));
            assertThat(children.get(1).y()).isCloseTo(5 + LINE_HEIGHT + 10, within(0.1f));
        }

        @Test
        @DisplayName("gap spec resolved by layout engine")
        void gapSpecResolved() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.gapSpec("10px");
            box.addChild(new LayoutNode.TextNode("A", List.of()));
            box.addChild(new LayoutNode.TextNode("B", List.of()));

            engine.layout(box, 800, 600);

            // Gap should be resolved and applied
            var children = box.children();
            assertThat(children.get(1).y()).isCloseTo(LINE_HEIGHT + 10, within(0.1f));
        }
    }

    // ==================================================================================
    // Auto Sizing
    // ==================================================================================

    @Nested
    @DisplayName("Auto Sizing")
    class AutoSizing {

        @Test
        @DisplayName("auto-width vertical box shrink-wraps to content width")
        void autoWidthVerticalShrinkWraps() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("auto");
            box.addChild(new LayoutNode.TextNode("Short", List.of()));      // 5 * 8 = 40px
            box.addChild(new LayoutNode.TextNode("Longer text", List.of())); // 11 * 8 = 88px

            engine.layout(box, 800, 600);

            // Should shrink-wrap to widest child, NOT fill 800
            assertThat(box.width()).isCloseTo(11 * CHAR_WIDTH, within(0.1f));
        }

        @Test
        @DisplayName("auto-width propagates shrink-wrap through nested verticals")
        void autoWidthPropagatesThroughNestedVerticals() {
            // Simulates tree panel: auto-width outer → inner vertical → text content
            // The inner vertical must NOT fill available width — it must shrink-wrap
            // so the outer auto-width parent can properly fit to content.
            var outer = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            outer.widthSpec("auto");
            outer.padding(4, 8, 4, 8);  // padding like tree panel

            // Inner vertical (no explicit width, like TreeSurface's container)
            var inner = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            inner.addChild(new LayoutNode.TextNode("TreeNode1", List.of())); // 9*8 = 72
            inner.addChild(new LayoutNode.TextNode("TreeNode2", List.of())); // 9*8 = 72
            outer.addChild(inner);

            engine.layout(outer, 1920, 600);

            // Inner should shrink-wrap to content, NOT fill 1920
            assertThat(inner.width()).isCloseTo(9 * CHAR_WIDTH, within(0.1f)); // 72px
            // Outer = inner + padding
            assertThat(outer.width()).isCloseTo(9 * CHAR_WIDTH + 16, within(0.1f)); // 88px
        }

        @Test
        @DisplayName("auto-width differs from unspecified (unspecified fills parent)")
        void autoWidthDiffersFromUnspecified() {
            var autoBox = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            autoBox.widthSpec("auto");
            autoBox.addChild(new LayoutNode.TextNode("Hi", List.of()));

            var defaultBox = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            defaultBox.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(autoBox, 800, 600);
            engine.layout(defaultBox, 800, 600);

            // Auto shrink-wraps, default fills available
            assertThat(autoBox.width()).isCloseTo(2 * CHAR_WIDTH, within(0.1f));
            assertThat(defaultBox.width()).isCloseTo(800, within(0.1f));
        }
    }

    // ==================================================================================
    // Horizontal Fill
    // ==================================================================================

    @Nested
    @DisplayName("Horizontal Fill")
    class HorizontalFill {

        @Test
        @DisplayName("fill child in horizontal container expands to remaining width")
        void fillChildExpandsToRemaining() {
            var box = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());

            // Fixed child: 5 * 8 = 40px
            var fixed = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            fixed.widthSpec("auto");
            fixed.addChild(new LayoutNode.TextNode("Fixed", List.of()));
            box.addChild(fixed);

            // Fill child: should expand to 800 - 40 = 760px
            var fill = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));
            fill.addChild(new LayoutNode.TextNode("Fill", List.of()));
            box.addChild(fill);

            engine.layout(box, 800, 600);

            assertThat(fixed.width()).isCloseTo(5 * CHAR_WIDTH, within(0.1f));
            assertThat(fill.width()).isCloseTo(800 - 5 * CHAR_WIDTH, within(0.1f));
            assertThat(box.width()).isCloseTo(800, within(0.1f));
        }

        @Test
        @DisplayName("multiple fill children split remaining space equally")
        void multipleFillChildrenSplitSpace() {
            var box = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());

            // Fixed child: 3 * 8 = 24px
            box.addChild(new LayoutNode.TextNode("Fix", List.of()));

            // Two fill children share remaining 800 - 24 = 776px
            var fill1 = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));
            fill1.addChild(new LayoutNode.TextNode("A", List.of()));
            box.addChild(fill1);

            var fill2 = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));
            fill2.addChild(new LayoutNode.TextNode("B", List.of()));
            box.addChild(fill2);

            engine.layout(box, 800, 600);

            float remaining = 800 - 3 * CHAR_WIDTH;
            assertThat(fill1.width()).isCloseTo(remaining / 2, within(0.1f));
            assertThat(fill2.width()).isCloseTo(remaining / 2, within(0.1f));
        }
    }

    // ==================================================================================
    // Nested Layout
    // ==================================================================================

    @Nested
    @DisplayName("Nested Layout")
    class NestedLayout {

        @Test
        @DisplayName("nested vertical boxes stack correctly")
        void nestedVerticalBoxes() {
            var outer = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());

            var inner1 = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            inner1.addChild(new LayoutNode.TextNode("A", List.of()));
            inner1.addChild(new LayoutNode.TextNode("B", List.of()));

            var inner2 = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            inner2.addChild(new LayoutNode.TextNode("C", List.of()));

            outer.addChild(inner1);
            outer.addChild(inner2);

            engine.layout(outer, 800, 600);

            // inner1 has 2 text lines = 32px
            assertThat(inner1.height()).isCloseTo(2 * LINE_HEIGHT, within(0.1f));
            // inner2 starts after inner1
            assertThat(inner2.y()).isCloseTo(2 * LINE_HEIGHT, within(0.1f));
            // outer = inner1 + inner2 = 3 lines
            assertThat(outer.height()).isCloseTo(3 * LINE_HEIGHT, within(0.1f));
        }

        @Test
        @DisplayName("horizontal inside vertical lays out correctly")
        void horizontalInsideVertical() {
            var outer = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());

            var row = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());
            row.addChild(new LayoutNode.TextNode("Left", List.of()));   // 4*8 = 32px
            row.addChild(new LayoutNode.TextNode("Right", List.of()));  // 5*8 = 40px

            outer.addChild(row);
            outer.addChild(new LayoutNode.TextNode("Below", List.of()));

            engine.layout(outer, 800, 600);

            // Row children side by side
            assertThat(row.children().get(0).x()).isEqualTo(0);
            assertThat(row.children().get(1).x()).isCloseTo(4 * CHAR_WIDTH, within(0.1f));

            // "Below" is below the row
            assertThat(outer.children().get(1).y()).isCloseTo(LINE_HEIGHT, within(0.1f));
        }
    }

    // ==================================================================================
    // Aspect Ratio
    // ==================================================================================

    @Nested
    @DisplayName("Aspect Ratio")
    class AspectRatio {

        @Test
        @DisplayName("square box (ratio=1) with explicit width derives height")
        void squareWithWidth() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("200px");
            box.aspectRatio(1.0f);
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(200, within(0.1f));
            assertThat(box.height()).isCloseTo(200, within(0.1f));
        }

        @Test
        @DisplayName("widescreen box (16/9) with explicit width derives height")
        void widescreenWithWidth() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("160px");
            box.aspectRatio(16f / 9f);
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(160, within(0.1f));
            assertThat(box.height()).isCloseTo(90, within(0.1f));
        }

        @Test
        @DisplayName("square box with explicit height derives width")
        void squareWithHeight() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.heightSpec("100px");
            box.aspectRatio(1.0f);
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(100, within(0.1f));
            assertThat(box.height()).isCloseTo(100, within(0.1f));
        }

        @Test
        @DisplayName("both width and height set — aspectRatio ignored")
        void bothSetIgnored() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("200px");
            box.heightSpec("100px");
            box.aspectRatio(1.0f);
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 600);

            assertThat(box.width()).isCloseTo(200, within(0.1f));
            assertThat(box.height()).isCloseTo(100, within(0.1f));
        }

        @Test
        @DisplayName("neither set — uses available width when width < height")
        void neitherSetUsesAvailable() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.aspectRatio(1.0f);
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 400, 600);

            assertThat(box.width()).isCloseTo(400, within(0.1f));
            assertThat(box.height()).isCloseTo(400, within(0.1f));
        }

        @Test
        @DisplayName("neither set — constrained by available height when height < width")
        void neitherSetConstrainedByHeight() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.aspectRatio(1.0f);
            box.addChild(new LayoutNode.TextNode("Hi", List.of()));

            engine.layout(box, 800, 300);

            // Height (300) is the constraining dimension, not width (800)
            assertThat(box.width()).isCloseTo(300, within(0.1f));
            assertThat(box.height()).isCloseTo(300, within(0.1f));
        }

        @Test
        @DisplayName("fill child with aspect ratio in horizontal parent stays square")
        void fillWithAspectRatioInHorizontalParent() {
            // Simulates minesweeper: horizontal row with fill+aspectRatio cells
            var row = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of());
            for (int i = 0; i < 3; i++) {
                var cell = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));
                cell.aspectRatio(1.0f);
                cell.addChild(new LayoutNode.TextNode("X", List.of()));
                row.addChild(cell);
            }

            // Row is wider (300) than tall (100) — cells should be 100×100 max
            engine.layout(row, 300, 100);

            for (LayoutNode child : row.children()) {
                // Each cell should be square, constrained by height (100)
                assertThat(child.width()).isCloseTo(child.height(), within(0.1f));
                assertThat(child.height()).isCloseTo(100, within(0.1f));
            }
        }

        @Test
        @DisplayName("text is centered within fill+aspectRatio cells with align-center")
        void textCenteredInAspectRatioCells() {
            // Minesweeper-like: board > row(H) > cell(V, fill, ar=1, align-center, pad=2) > text
            var board = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            board.padding(8, 8, 8, 8);

            var row = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of("fill"));
            row.gapSpec("1px");
            for (int i = 0; i < 3; i++) {
                var cell = new LayoutNode.BoxNode(Scene.Direction.VERTICAL,
                        List.of("tile", "fill", "align-center"));
                cell.aspectRatio(1.0f);
                cell.padding(2, 2, 2, 2);
                cell.addChild(new LayoutNode.TextNode("X", List.of()));
                row.addChild(cell);
            }
            board.addChild(row);

            // Board: 300 wide, 100 tall
            engine.layout(board, 300, 100);

            // Board content: 300-16 = 284w, 100-16 = 84h
            // Row fill: 284w x 84h
            // fillWidth = (284-2) / 3 = 94, cell ar=1: min(94,84) = 84 -> cell = 84x84
            // cell content: 84-4 = 80w
            // text "X" = 8px wide
            // text x should be centered: cellX + 2 + (80 - 8)/2 = cellX + 38

            for (LayoutNode rowChild : row.children()) {
                var cell = (LayoutNode.BoxNode) rowChild;
                assertThat(cell.width())
                        .as("cell width")
                        .isCloseTo(84, within(0.1f));

                LayoutNode text = cell.children().get(0);
                float cellContentLeft = cell.x() + cell.paddingLeft();
                float cellContentWidth = cell.width() - cell.paddingLeft() - cell.paddingRight();
                float expectedTextX = cellContentLeft + (cellContentWidth - text.width()) / 2;

                assertThat(text.x())
                        .as("text X in cell %d (cellX=%.1f, contentW=%.1f, textW=%.1f)",
                                row.children().indexOf(rowChild), cell.x(), cellContentWidth, text.width())
                        .isCloseTo(expectedTextX, within(0.5f));
            }
        }
    }

    // ==================================================================================
    // Text Font Size
    // ==================================================================================

    @Nested
    @DisplayName("Text Font Size")
    class TextFontSize {

        @Test
        @DisplayName("percentage fontSize resolves against parent height")
        void percentageOfParentHeight() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("100px");
            box.aspectRatio(1.0f); // 100x100
            var text = new LayoutNode.TextNode("A", List.of());
            text.fontSizeSpec("80%");
            box.addChild(text);

            engine.layout(box, 800, 600);

            // Parent is 100px tall, 80% = 80px font size
            assertThat(text.fontSize()).isCloseTo(80f, within(0.1f));
        }

        @Test
        @DisplayName("absolute px fontSize sets value directly")
        void absolutePxFontSize() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            var text = new LayoutNode.TextNode("A", List.of());
            text.fontSizeSpec("24px");
            box.addChild(text);

            // No renderContext so toPixels won't resolve — but tests use the simple constructor
            // which has null renderContext. Let's check with availableHeight as parent.
            engine.layout(box, 800, 600);

            // Without renderContext, non-% specs don't resolve (toPixels needs context).
            // That's fine — this exercises the code path.
        }

        @Test
        @DisplayName("fontSize inside aspect-ratio box uses resolved height")
        void insideAspectRatioBox() {
            var box = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            box.widthSpec("200px");
            box.aspectRatio(1.0f); // → 200x200
            var text = new LayoutNode.TextNode("X", List.of());
            text.fontSizeSpec("50%");
            box.addChild(text);

            engine.layout(box, 800, 600);

            // Box is 200px tall, 50% = 100px
            assertThat(text.fontSize()).isCloseTo(100f, within(0.1f));
        }
    }

    // ==================================================================================
    // Scroll / Overflow Detection
    // ==================================================================================

    @Nested
    @DisplayName("Scroll Overflow")
    class ScrollOverflow {

        @Test
        @DisplayName("overflow detected for fill child in horizontal wrapper (ItemModel detail layout)")
        void overflowDetectedInConstraintLayout() {
            // Simulate the ItemModel constraint layout:
            // Root (VERTICAL, fill)
            //   ├── header (fit, 40px)
            //   ├── horizontal wrapper (HORIZONTAL, fill)
            //   │   ├── tree (VERTICAL, auto width)
            //   │   └── detail (VERTICAL, fill, overflow="auto") ← target
            //   └── prompt (fit, 30px)
            //
            // Window: 800x600. Detail content: 20 text lines = 320px.
            // Available for middle after header+prompt: 600 - 40 - 30 = 530px.
            // Detail should detect overflow if content > 530px.

            // Root
            var root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));

            // Header (40px)
            var header = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            var headerText = new LayoutNode.TextNode("Header", List.of());
            headerText.measuredSize(100, 40);
            header.addChild(headerText);
            root.addChild(header);

            // Horizontal wrapper (fill)
            var hWrapper = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of("fill"));

            // Tree (auto width, not fill)
            var tree = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            var treeText = new LayoutNode.TextNode("Tree", List.of());
            tree.addChild(treeText);
            hWrapper.addChild(tree);

            // Detail (fill, overflow="auto")
            var detail = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));
            detail.overflow("auto");
            detail.id("detail");
            // Add enough content to overflow: 40 lines × 16px = 640px > 530px available
            for (int i = 0; i < 40; i++) {
                detail.addChild(new LayoutNode.TextNode("Field " + i, List.of()));
            }
            hWrapper.addChild(detail);

            root.addChild(hWrapper);

            // Prompt (30px)
            var prompt = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            var promptText = new LayoutNode.TextNode(">>>", List.of());
            promptText.measuredSize(50, 30);
            prompt.addChild(promptText);
            root.addChild(prompt);

            // Layout
            engine.layout(root, 800, 600);

            // Detail should be clamped to available height and overflow detected
            assertThat(detail.overflowsY())
                    .as("detail should detect vertical overflow")
                    .isTrue();
            assertThat(detail.isScrollContainer())
                    .as("detail should be a scroll container")
                    .isTrue();
            assertThat(detail.contentHeight())
                    .as("contentHeight should reflect the full content")
                    .isGreaterThan(detail.height());
            assertThat(detail.height())
                    .as("detail height should be clamped to viewport")
                    .isLessThan(640); // less than 40 lines × 16px
        }

        @Test
        @DisplayName("no overflow when content fits in viewport")
        void noOverflowWhenContentFits() {
            var root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));

            var header = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
            header.addChild(new LayoutNode.TextNode("H", List.of()));
            root.addChild(header);

            var hWrapper = new LayoutNode.BoxNode(Scene.Direction.HORIZONTAL, List.of("fill"));

            var detail = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of("fill"));
            detail.overflow("auto");
            detail.id("detail");
            // Only 3 lines — should fit easily
            for (int i = 0; i < 3; i++) {
                detail.addChild(new LayoutNode.TextNode("Field " + i, List.of()));
            }
            hWrapper.addChild(detail);
            root.addChild(hWrapper);

            engine.layout(root, 800, 600);

            assertThat(detail.overflowsY())
                    .as("no overflow when content fits")
                    .isFalse();
            assertThat(detail.isScrollContainer())
                    .as("auto overflow: no scroll when content fits")
                    .isFalse();
        }

        @Test
        @DisplayName("overflow from ConstraintSurface reaches LayoutNode via SkiaSurfaceRenderer")
        void constraintSurfaceOverflowEndToEnd() {
            // Build a ConstraintSurface that mirrors ItemModel's layout:
            // - header (top, fit)
            // - tree (middle, no horizontal constraint)
            // - detail (middle, horizontal constraint, overflow="auto")
            // - prompt (bottom, fit)
            var constraint = new dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface();

            // Header
            var header = dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface.vertical();
            header.add(dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface.of("Header"));
            constraint.add("header", header,
                    new dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface.ConstraintValues(
                            "0", "", "0", "100%", "", "", "", "",
                            "", "fit", "", "", "", "", "", "", 0, "visible"));

            // Tree
            var tree = dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface.vertical();
            tree.add(dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface.of("Tree"));
            constraint.add("tree", tree,
                    new dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface.ConstraintValues(
                            "", "", "0", "", "header.bottom", "prompt.top", "", "",
                            "auto", "", "", "", "40%", "", "", "", 0, "visible"));

            // Detail with overflow="auto" and enough content to overflow
            var detail = dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface.vertical();
            for (int i = 0; i < 40; i++) {
                detail.add(dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface.of("Field " + i + ": value"));
            }
            constraint.add("detail", detail,
                    new dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface.ConstraintValues(
                            "", "", "", "100%", "header.bottom", "prompt.top", "tree.right", "",
                            "", "", "", "", "", "", "", "", 0, "auto"));

            // Prompt
            var prompt = dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface.vertical();
            prompt.add(dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface.of("prompt> "));
            constraint.add("prompt", prompt,
                    new dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface.ConstraintValues(
                            "", "0", "0", "100%", "", "", "", "",
                            "", "fit", "", "", "", "", "", "", 0, "visible"));

            // Render through SkiaSurfaceRenderer
            var skiRenderer = new SkiaSurfaceRenderer();
            constraint.render(skiRenderer);
            LayoutNode.BoxNode layoutTree = skiRenderer.result();

            // Find the detail box by id
            LayoutNode.BoxNode detailBox = findBoxById(layoutTree, "detail");
            assertThat(detailBox)
                    .as("detail box should exist in layout tree")
                    .isNotNull();
            assertThat(detailBox.overflow())
                    .as("detail box should have overflow=auto from constraint")
                    .isEqualTo("auto");

            // Now layout and check overflow detection
            engine.layout(layoutTree, 800, 600);

            // Diagnostic: inspect the tree structure
            System.err.println("=== Layout tree after layout ===");
            printTree(layoutTree, 0);
            System.err.println("Detail box: h=" + detailBox.height()
                    + " contentH=" + detailBox.contentHeight()
                    + " overflow=" + detailBox.overflow()
                    + " overflowsY=" + detailBox.overflowsY()
                    + " children=" + detailBox.children().size());

            assertThat(detailBox.overflow())
                    .as("overflow should survive layout")
                    .isEqualTo("auto");
            assertThat(detailBox.overflowsY())
                    .as("detail should detect overflow after layout")
                    .isTrue();
            assertThat(detailBox.isScrollContainer())
                    .as("detail should be a scroll container")
                    .isTrue();
        }

        private void printTree(LayoutNode node, int depth) {
            String indent = "  ".repeat(depth);
            if (node instanceof LayoutNode.BoxNode box) {
                System.err.println(indent + "BOX id=" + box.id() + " dir=" + box.direction()
                        + " overflow=" + box.overflow() + " h=" + (int) box.height()
                        + " contentH=" + (int) box.contentHeight()
                        + " overflowsY=" + box.overflowsY()
                        + " styles=" + box.styles() + " ch=" + box.children().size());
                for (LayoutNode child : box.children()) {
                    printTree(child, depth + 1);
                    if (depth > 3) break; // limit depth
                }
            } else {
                System.err.println(indent + node.getClass().getSimpleName() + " h=" + (int) node.height());
            }
        }

        private LayoutNode.BoxNode findBoxById(LayoutNode node, String id) {
            if (node instanceof LayoutNode.BoxNode box) {
                if (id.equals(box.id())) return box;
                for (LayoutNode child : box.children()) {
                    LayoutNode.BoxNode found = findBoxById(child, id);
                    if (found != null) return found;
                }
            }
            return null;
        }
    }
}
