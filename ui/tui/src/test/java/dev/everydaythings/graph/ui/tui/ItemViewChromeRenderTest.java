package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.scene.SceneCascade;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.VariableResolver;
import dev.everydaythings.graph.scene.Viewport;
import dev.everydaythings.graph.ui.Presenter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: take the chrome body the cascade returns for ItemView,
 * run it through Presenter + TuiPainter, and assert the literal "[ItemView]"
 * lands in the painter's output.  Covers the path the live RenderLoop uses
 * at every tick, without needing the gradle :ui:tui:run smoke.
 */
@DisplayName("ItemView chrome renders through Presenter + TuiPainter")
class ItemViewChromeRenderTest {

    @Test
    @DisplayName("Chrome SceneText emits '[ItemView]' to terminal output")
    void chromeRendersText() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        Body chrome = SceneCascade.sceneFor(
                ItemRef.iid(SessionVocabulary.ItemView.KEY), lib);

        Viewport viewport = new Viewport(80, 24);
        Presenter presenter = new Presenter(viewport, VariableResolver.NONE);
        SceneNode presented = presenter.presentNode(chrome);

        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), viewport)) {
            painter.paint(presented);
        }
        assertThat(buffer.toString()).contains("[ItemView]");
    }
}
