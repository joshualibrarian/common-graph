package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.quality.MediaVocabulary;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.Viewport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TuiPainter — TEXT-fidelity painter walks scene tree and emits cells")
class TuiPainterTest {

    @Test
    @DisplayName("SceneText emits its literal text")
    void rendersSceneText() {
        SceneText hello = SceneText.from(
                Body.of(ItemRef.iid(SceneText.KEY),
                        List.of(Binding.literal(
                                ItemRef.iid(SceneVocabulary.Text.KEY),
                                "hello, world"))));

        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), new Viewport(80, 24))) {
            painter.paint(hello);
        }
        assertThat(buffer.toString()).contains("hello, world");
    }

    @Test
    @DisplayName("SceneBody falls back glyph → alt → placeholder")
    void rendersSceneBodyFallback() {
        SceneBody glyphBody = SceneBody.from(
                Body.of(ItemRef.iid(SceneBody.KEY),
                        List.of(Binding.literal(
                                ItemRef.iid(MediaVocabulary.Glyph.KEY),
                                "*"))));
        SceneBody altBody = SceneBody.from(
                Body.of(ItemRef.iid(SceneBody.KEY),
                        List.of(Binding.literal(
                                ItemRef.iid(MediaVocabulary.Alt.KEY),
                                "moon"))));

        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), new Viewport(80, 24))) {
            painter.paint(glyphBody);
            painter.paint(altBody);
        }
        String out = buffer.toString();
        assertThat(out).contains("*");
        assertThat(out).contains("[moon]");
    }

    @Test
    @DisplayName("SceneContainer recurses through children in binding-index order")
    void rendersContainerWithChildren() {
        Body firstChild = Body.of(ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(ItemRef.iid(SceneVocabulary.Text.KEY), "one")));
        Body secondChild = Body.of(ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(ItemRef.iid(SceneVocabulary.Text.KEY), "two")));

        ItemRef childrenRole = ItemRef.iid(SceneVocabulary.Children.KEY);
        SceneContainer container = SceneContainer.from(
                Body.of(ItemRef.iid(SceneContainer.KEY),
                        List.of(
                                new Binding(childrenRole, List.of(), firstChild,  0L),
                                new Binding(childrenRole, List.of(), secondChild, 1L))));

        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), new Viewport(80, 24))) {
            painter.paint(container);
        }
        String out = buffer.toString();
        int firstAt  = out.indexOf("one");
        int secondAt = out.indexOf("two");
        assertThat(firstAt).isGreaterThanOrEqualTo(0);
        assertThat(secondAt).isGreaterThan(firstAt);
    }

    @Test
    @DisplayName("Painter reports its fidelity and viewport")
    void exposesMetadata() {
        try (TuiPainter painter = new TuiPainter(new PrintWriter(new StringWriter()), new Viewport(120, 40))) {
            assertThat(painter.fidelity()).isEqualTo(Painter.Fidelity.TEXT);
            assertThat(painter.viewport().width()).isEqualTo(120f);
            assertThat(painter.viewport().height()).isEqualTo(40f);
            assertThat(painter.fontMetrics().measureWidth("hello", 12f)).isEqualTo(5f);
            assertThat(painter.fontMetrics().lineHeight(12f)).isEqualTo(1f);
        }
    }
}
