package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkiaSurfaceRenderer")
class SkiaSurfaceRendererTest {

    private SkiaSurfaceRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new SkiaSurfaceRenderer();
    }

    // ==================================================================================
    // Text
    // ==================================================================================

    @Nested
    @DisplayName("Text")
    class TextTests {

        @Test
        @DisplayName("text() creates TextNode in root")
        void textCreatesNode() {
            renderer.text("Hello", List.of("heading"));

            var root = renderer.result();
            assertThat(root.children()).hasSize(1);
            assertThat(root.children().get(0)).isInstanceOf(LayoutNode.TextNode.class);

            var text = (LayoutNode.TextNode) root.children().get(0);
            assertThat(text.content()).isEqualTo("Hello");
            assertThat(text.styles()).contains("heading");
        }

        @Test
        @DisplayName("formattedText() creates TextNode with format")
        void formattedTextCreatesNode() {
            renderer.formattedText("code here", "code", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.content()).isEqualTo("code here");
            assertThat(text.format()).isEqualTo("code");
        }

        @Test
        @DisplayName("null content becomes empty string")
        void nullContentBecomesEmpty() {
            renderer.text(null, List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.content()).isEmpty();
        }

        @Test
        @DisplayName("textFontFamily() applies to next text node")
        void textFontFamilyAppliesToText() {
            renderer.textFontFamily("Symbols Nerd Font Mono");
            renderer.text("icons", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.fontFamily()).isEqualTo("Symbols Nerd Font Mono");
        }
    }

    // ==================================================================================
    // Boxes
    // ==================================================================================

    @Nested
    @DisplayName("Boxes")
    class BoxTests {

        @Test
        @DisplayName("beginBox/endBox creates nested BoxNode")
        void nestedBox() {
            renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("row"));
            renderer.text("Child", List.of());
            renderer.endBox();

            var root = renderer.result();
            assertThat(root.children()).hasSize(1);
            assertThat(root.children().get(0)).isInstanceOf(LayoutNode.BoxNode.class);

            var box = (LayoutNode.BoxNode) root.children().get(0);
            assertThat(box.direction()).isEqualTo(Scene.Direction.HORIZONTAL);
            assertThat(box.styles()).contains("row");
            assertThat(box.children()).hasSize(1);
        }

        @Test
        @DisplayName("deeply nested boxes work correctly")
        void deeplyNested() {
            renderer.beginBox(Scene.Direction.VERTICAL, List.of("outer"));
            renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("inner"));
            renderer.text("Deep", List.of());
            renderer.endBox();
            renderer.endBox();

            var outer = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(outer.styles()).contains("outer");

            var inner = (LayoutNode.BoxNode) outer.children().get(0);
            assertThat(inner.styles()).contains("inner");

            var text = (LayoutNode.TextNode) inner.children().get(0);
            assertThat(text.content()).isEqualTo("Deep");
        }

        @Test
        @DisplayName("multiple children at same level")
        void multipleChildren() {
            renderer.text("First", List.of());
            renderer.text("Second", List.of());
            renderer.text("Third", List.of());

            assertThat(renderer.result().children()).hasSize(3);
        }

        @Test
        @DisplayName("textFontFamily() before beginBox applies to container")
        void textFontFamilyAppliesToContainer() {
            renderer.textFontFamily("monospace");
            renderer.beginBox(Scene.Direction.VERTICAL, List.of("panel"));
            renderer.text("inside", List.of());
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.fontFamily()).isEqualTo("monospace");
        }

        @Test
        @DisplayName("textFontSize() before beginBox applies to container")
        void textFontSizeAppliesToContainer() {
            renderer.textFontSize("80%");
            renderer.beginBox(Scene.Direction.VERTICAL, List.of("panel"));
            renderer.text("inside", List.of());
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.fontSizeSpec()).isEqualTo("80%");
        }
    }

    // ==================================================================================
    // Metadata
    // ==================================================================================

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("type() sets type on next element")
        void typeOnNext() {
            renderer.type("TreeNode");
            renderer.text("Node", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.type()).isEqualTo("TreeNode");
            assertThat(text.styles()).contains("TreeNode");
        }

        @Test
        @DisplayName("id() sets id on next element")
        void idOnNext() {
            renderer.id("node-1");
            renderer.text("Node", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.id()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("metadata is consumed after one element")
        void metadataConsumed() {
            renderer.type("Special");
            renderer.text("First", List.of());
            renderer.text("Second", List.of());

            var first = (LayoutNode.TextNode) renderer.result().children().get(0);
            var second = (LayoutNode.TextNode) renderer.result().children().get(1);

            assertThat(first.type()).isEqualTo("Special");
            assertThat(second.type()).isNull();
        }

        @Test
        @DisplayName("editable() adds style class")
        void editableAddsStyle() {
            renderer.editable(true);
            renderer.text("Edit me", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.styles()).contains("editable");
        }
    }

    // ==================================================================================
    // Events
    // ==================================================================================

    @Nested
    @DisplayName("Events")
    class EventTests {

        @Test
        @DisplayName("event() attaches to next element")
        void eventAttaches() {
            renderer.event("click", "select", "item-1");
            renderer.text("Clickable", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.events()).hasSize(1);
            assertThat(text.events().get(0).on()).isEqualTo("click");
            assertThat(text.events().get(0).action()).isEqualTo("select");
            assertThat(text.events().get(0).target()).isEqualTo("item-1");
        }

        @Test
        @DisplayName("multiple events on same element")
        void multipleEvents() {
            renderer.event("click", "select", "");
            renderer.event("doubleClick", "open", "");
            renderer.text("Multi", List.of());

            var text = (LayoutNode.TextNode) renderer.result().children().get(0);
            assertThat(text.events()).hasSize(2);
        }

        @Test
        @DisplayName("events are consumed after one element")
        void eventsConsumed() {
            renderer.event("click", "act", "");
            renderer.text("First", List.of());
            renderer.text("Second", List.of());

            var first = (LayoutNode.TextNode) renderer.result().children().get(0);
            var second = (LayoutNode.TextNode) renderer.result().children().get(1);

            assertThat(first.events()).hasSize(1);
            assertThat(second.events()).isEmpty();
        }
    }

    // ==================================================================================
    // Image
    // ==================================================================================

    @Nested
    @DisplayName("Image")
    class ImageTests {

        @Test
        @DisplayName("image() creates ImageNode")
        void imageCreatesNode() {
            renderer.image("icon", null, null, "small", "contain", List.of("glyph"));

            var root = renderer.result();
            assertThat(root.children()).hasSize(1);
            assertThat(root.children().get(0)).isInstanceOf(LayoutNode.ImageNode.class);

            var image = (LayoutNode.ImageNode) root.children().get(0);
            assertThat(image.alt()).isEqualTo("icon");
            assertThat(image.size()).isEqualTo("small");
            assertThat(image.fit()).isEqualTo("contain");
        }
    }

    // ==================================================================================
    // Raw Spec Storage
    // ==================================================================================

    @Nested
    @DisplayName("Raw Spec Storage")
    class RawSpecStorage {

        @Test
        @DisplayName("width stored as raw spec string")
        void widthStoredAsRawSpec() {
            renderer.beginBox(Scene.Direction.VERTICAL, List.of(),
                    null, "", "200px", "", "");
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.widthSpec()).isEqualTo("200px");
            // Should NOT be pre-resolved
            assertThat(box.explicitWidth()).isEqualTo(-1);
        }

        @Test
        @DisplayName("percent width stored as raw spec")
        void percentWidthStoredAsRawSpec() {
            renderer.beginBox(Scene.Direction.VERTICAL, List.of(),
                    null, "", "50%", "", "");
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.widthSpec()).isEqualTo("50%");
        }

        @Test
        @DisplayName("height stored as raw spec string")
        void heightStoredAsRawSpec() {
            renderer.beginBox(Scene.Direction.VERTICAL, List.of(),
                    null, "", "", "100px", "");
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.heightSpec()).isEqualTo("100px");
        }
    }

    // ==================================================================================
    // Gap
    // ==================================================================================

    @Nested
    @DisplayName("Gap")
    class GapTests {

        @Test
        @DisplayName("gap stored as spec on next box")
        void gapStoredAsSpec() {
            renderer.gap("8px");
            renderer.beginBox(Scene.Direction.VERTICAL, List.of());
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.gapSpec()).isEqualTo("8px");
        }

        @Test
        @DisplayName("gap consumed after one box")
        void gapConsumedAfterOneBox() {
            renderer.gap("8px");
            renderer.beginBox(Scene.Direction.VERTICAL, List.of());
            renderer.endBox();
            renderer.beginBox(Scene.Direction.VERTICAL, List.of());
            renderer.endBox();

            var first = (LayoutNode.BoxNode) renderer.result().children().get(0);
            var second = (LayoutNode.BoxNode) renderer.result().children().get(1);
            assertThat(first.gapSpec()).isEqualTo("8px");
            assertThat(second.gapSpec()).isNull();
        }

        @Test
        @DisplayName("gap works with extended beginBox")
        void gapWithExtendedBeginBox() {
            renderer.gap("16px");
            renderer.beginBox(Scene.Direction.HORIZONTAL, List.of(),
                    null, "", "", "", "");
            renderer.endBox();

            var box = (LayoutNode.BoxNode) renderer.result().children().get(0);
            assertThat(box.gapSpec()).isEqualTo("16px");
        }
    }

    // ==================================================================================
    // Integration: Renderer + LayoutEngine
    // ==================================================================================

    @Nested
    @DisplayName("Integration")
    class Integration {

        @Test
        @DisplayName("renderer output feeds into layout engine")
        void rendererToLayout() {
            renderer.beginBox(Scene.Direction.VERTICAL, List.of("container"));
            renderer.text("Line 1", List.of());
            renderer.text("Line 2", List.of());
            renderer.endBox();

            var root = renderer.result();

            // Use fixed measurer for layout
            var engine = new LayoutEngine((node, maxWidth) ->
                node.measuredSize(node.content().length() * 8f, 16f));

            engine.layout(root, 800, 600);

            // Root should have computed bounds
            assertThat(root.height()).isGreaterThan(0);

            // Child box should have two text children
            var box = (LayoutNode.BoxNode) root.children().get(0);
            assertThat(box.children()).hasSize(2);
            assertThat(box.children().get(0).y()).isLessThan(box.children().get(1).y());
        }
    }
}
