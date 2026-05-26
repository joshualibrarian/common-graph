package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.Bounds;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.VariableResolver;
import dev.everydaythings.graph.scene.Viewport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Presenter — layout pass on scene trees")
class PresenterLayoutTest {

    /** Deterministic metrics: 8 units wide per character, 16 units per line. */
    private static final FontMetrics FIXED = new FontMetrics() {
        @Override public float measureWidth(String text, float fontSize) {
            return text == null ? 0 : text.length() * 8f;
        }
        @Override public float lineHeight(float fontSize) {
            return 16f;
        }
    };

    private final Viewport viewport = new Viewport(800, 600);
    private final Presenter presenter =
            new Presenter(viewport, VariableResolver.NONE, FIXED);

    @Nested
    @DisplayName("SceneText")
    class TextLayout {

        @Test
        @DisplayName("text node gets width = measureWidth and height = lineHeight")
        void textBoundsReflectMeasurement() {
            Body text = textBody("hello");

            SceneNode presented = presenter.presentNode(text);

            Bounds b = presented.bounds();
            assertThat(b.x()).isEqualTo(0f);
            assertThat(b.y()).isEqualTo(0f);
            assertThat(b.width()).isEqualTo(40f);     // 5 chars * 8
            assertThat(b.height()).isEqualTo(16f);
        }

        @Test
        @DisplayName("text wider than available width is clamped to available width")
        void textClampedToAvailableWidth() {
            // 200 chars * 8 = 1600 > viewport.width (800)
            Body text = textBody("x".repeat(200));

            SceneNode presented = presenter.presentNode(text);

            assertThat(presented.bounds().width()).isEqualTo(800f);
            assertThat(presented.bounds().height()).isEqualTo(16f);
        }

        @Test
        @DisplayName("empty text still gets a line-height tall bounds")
        void emptyTextStillHasLineHeight() {
            Body text = textBody("");

            SceneNode presented = presenter.presentNode(text);

            assertThat(presented.bounds().width()).isEqualTo(0f);
            assertThat(presented.bounds().height()).isEqualTo(16f);
        }
    }

    @Nested
    @DisplayName("SceneContainer")
    class ContainerLayout {

        @Test
        @DisplayName("container takes the available width regardless of child widths")
        void containerSpansAvailableWidth() {
            Body container = containerBody(textBody("hi"));

            SceneNode presented = presenter.presentNode(container);

            assertThat(presented).isInstanceOf(SceneContainer.class);
            assertThat(presented.bounds().width()).isEqualTo(800f);
        }

        @Test
        @DisplayName("container height equals the sum of its children's heights")
        void containerHeightSumsChildren() {
            Body container = containerBody(
                    textBody("one"),    // height 16
                    textBody("two"),    // height 16
                    textBody("three")); // height 16

            SceneNode presented = presenter.presentNode(container);

            assertThat(presented.bounds().height()).isEqualTo(48f);
        }

        @Test
        @DisplayName("children stack vertically with running y offsets")
        void childrenStackVertically() {
            Body container = containerBody(
                    textBody("a"),
                    textBody("b"),
                    textBody("c"));

            SceneContainer presented = (SceneContainer) presenter.presentNode(container);
            List<SceneNode> children = presented.children();

            assertThat(children).hasSize(3);
            assertThat(children.get(0).bounds().y()).isEqualTo(0f);
            assertThat(children.get(1).bounds().y()).isEqualTo(16f);
            assertThat(children.get(2).bounds().y()).isEqualTo(32f);
            // All children share x = 0 in the vertical-stack model
            assertThat(children).allMatch(c -> c.bounds().x() == 0f);
        }

        @Test
        @DisplayName("empty container has zero height")
        void emptyContainerIsZeroHeight() {
            Body container = containerBody();

            SceneNode presented = presenter.presentNode(container);

            assertThat(presented.bounds().width()).isEqualTo(800f);
            assertThat(presented.bounds().height()).isEqualTo(0f);
        }

        @Test
        @DisplayName("nested containers carry their own bounds; descendants share the same wrappers")
        void nestedContainersNestBounds() {
            Body inner = containerBody(textBody("inner-text"));     // height 16
            Body outer = containerBody(inner, textBody("sibling")); // 16 + 16 = 32

            SceneContainer outerNode = (SceneContainer) presenter.presentNode(outer);

            assertThat(outerNode.bounds().height()).isEqualTo(32f);

            // Reading children twice returns the same SceneNode instances — this
            // is the layout-then-paint identity guarantee.
            List<SceneNode> firstWalk = outerNode.children();
            List<SceneNode> secondWalk = outerNode.children();
            assertThat(firstWalk).containsExactlyElementsOf(secondWalk);

            SceneContainer innerNode = (SceneContainer) firstWalk.get(0);
            assertThat(innerNode.bounds().y()).isEqualTo(0f);
            assertThat(innerNode.bounds().height()).isEqualTo(16f);

            SceneNode sibling = firstWalk.get(1);
            assertThat(sibling.bounds().y()).isEqualTo(16f);
        }
    }

    @Nested
    @DisplayName("SceneBody")
    class BodyLayout {

        @Test
        @DisplayName("body node gets placeholder intrinsic size until fidelity sizing lands")
        void bodyGetsPlaceholderBounds() {
            Body body = Body.of(ItemRef.iid(SceneBody.KEY), List.of());

            SceneNode presented = presenter.presentNode(body);

            assertThat(presented.bounds().width()).isEqualTo(100f);
            assertThat(presented.bounds().height()).isEqualTo(100f);
        }
    }

    // ==================================================================================
    // Body builders
    // ==================================================================================

    private static Body textBody(String content) {
        return Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(
                        ItemRef.iid(SceneVocabulary.Text.KEY),
                        content)));
    }

    private static Body containerBody(Body... children) {
        List<Binding> bindings = new java.util.ArrayList<>(children.length);
        for (int i = 0; i < children.length; i++) {
            bindings.add(Binding.indexed(
                    ItemRef.iid(SceneVocabulary.Children.KEY),
                    children[i], i));
        }
        return Body.of(ItemRef.iid(SceneContainer.KEY), bindings);
    }
}
