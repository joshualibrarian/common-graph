package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.Viewport;
import dev.everydaythings.graph.ui.Presenter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test: Session binds CurrentTime, Presenter substitutes
 * the Variable reference, TuiPainter renders the resolved tree to a
 * buffer.  Proves Session + Presenter + Painter compose.
 */
@DisplayName("Clock scene — end-to-end through Session + Presenter + TuiPainter")
class ClockSmokeTest {

    private final ItemRef currentTimeRef = ItemRef.iid(CoreVocabulary.CurrentTime.KEY);
    private final Viewport viewport = new Viewport(80, 24);

    /** The unresolved clock scene: SceneText with text → CurrentTime. */
    private Body clockScene() {
        return Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(
                        ItemRef.iid(SceneVocabulary.Text.KEY),
                        currentTimeRef)));
    }

    @Test
    @DisplayName("Renders the time the Session has bound for CurrentTime")
    void rendersBoundTime() {
        Session session = new Session(
                ItemRef.iid("cg.test:clock-session"),
                Librarian.anonymous());
        session.bindVariable(currentTimeRef, () -> "12:34:56");

        Presenter presenter = new Presenter(viewport, session.variableResolver());
        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), viewport)) {
            SceneNode resolved = presenter.presentNode(clockScene());
            painter.paint(resolved);
        }

        assertThat(buffer.toString()).contains("12:34:56");
    }

    @Test
    @DisplayName("Re-rendering after the supplier ticks shows the new value")
    void renderTicksWithSupplier() {
        Session session = new Session(
                ItemRef.iid("cg.test:clock-session"),
                Librarian.anonymous());
        AtomicReference<String> nowRef = new AtomicReference<>("00:00:00");
        session.bindVariable(currentTimeRef, nowRef::get);

        Presenter presenter = new Presenter(viewport, session.variableResolver());
        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), viewport)) {
            painter.paint(presenter.presentNode(clockScene()));
            nowRef.set("00:00:01");
            painter.paint(presenter.presentNode(clockScene()));
            nowRef.set("00:00:02");
            painter.paint(presenter.presentNode(clockScene()));
        }

        String output = buffer.toString();
        assertThat(output).contains("00:00:00");
        assertThat(output).contains("00:00:01");
        assertThat(output).contains("00:00:02");

        int first  = output.indexOf("00:00:00");
        int second = output.indexOf("00:00:01");
        int third  = output.indexOf("00:00:02");
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
    }

    @Test
    @DisplayName("Unbound CurrentTime leaves the ItemRef in place; TuiPainter prints no text")
    void unboundVariableProducesNoTextOutput() {
        Session session = new Session(
                ItemRef.iid("cg.test:clock-session"),
                Librarian.anonymous());
        // Deliberately do NOT bind CurrentTime.

        Presenter presenter = new Presenter(viewport, session.variableResolver());
        StringWriter buffer = new StringWriter();
        try (TuiPainter painter = new TuiPainter(new PrintWriter(buffer), viewport)) {
            // The presenter leaves the ItemRef in place; the painter's
            // SceneText reader rejects non-String targets and emits nothing.
            painter.paint(presenter.presentNode(clockScene()));
        }

        assertThat(buffer.toString().trim()).isEmpty();
    }
}
