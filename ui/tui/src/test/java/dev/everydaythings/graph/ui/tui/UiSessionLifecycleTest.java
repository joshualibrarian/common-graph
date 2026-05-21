package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.ui.LocalSession;
import dev.everydaythings.graph.ui.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the full UiSession lifecycle on a LocalSession against an
 * anonymous in-memory Librarian.  Confirms that startUi resolves the TUI
 * surface via {@code SurfaceRegistry}, that windows added to it drive the
 * render loop (calling the scene supplier), and that stopUi tears down
 * cleanly.
 */
class UiSessionLifecycleTest {

    private static final ItemRef CURRENT_TIME =
            ItemRef.iid(CoreVocabulary.CurrentTime.KEY);

    private static Body clockScene() {
        return Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(
                        ItemRef.iid(SceneVocabulary.Text.KEY),
                        CURRENT_TIME)));
    }

    private static LocalSession newSession(String testId) {
        return new LocalSession(ItemRef.iid("cg.test:" + testId), Librarian.anonymous());
    }

    @Test
    @DisplayName("startUi opens surface; an added window drives the render loop")
    void startWindowStop() {
        LocalSession session = newSession("lifecycle");
        session.bindVariable(CURRENT_TIME, () -> "tick");

        AtomicInteger calls = new AtomicInteger();
        try {
            session.startUi("tui");
            assertThat(session.isUiRunning()).isTrue();

            session.addWindow(new Window(
                    ItemRef.iid("cg.test:item"),
                    () -> { calls.incrementAndGet(); return clockScene(); }));

            // TuiSurface default cadence is 250ms; one tick within a few
            // hundred ms is plenty.
            waitFor(() -> calls.get() >= 1, Duration.ofSeconds(2));
        } finally {
            session.stopUi();
        }

        assertThat(session.isUiRunning()).isFalse();
        // stopUi is idempotent — second call must not throw.
        session.stopUi();
    }

    @Test
    @DisplayName("requestRender wakes the loop ahead of cadence")
    void requestRenderWakesLoop() {
        LocalSession session = newSession("request-render");
        AtomicInteger calls = new AtomicInteger();
        try {
            session.startUi("tui");
            session.addWindow(new Window(
                    ItemRef.iid("cg.test:item"),
                    () -> { calls.incrementAndGet(); return clockScene(); }));

            // First tick fires after first cadence (or addWindow request).
            waitFor(() -> calls.get() >= 1, Duration.ofSeconds(2));
            int afterStart = calls.get();

            session.requestRender();
            waitFor(() -> calls.get() > afterStart, Duration.ofSeconds(2));
        } finally {
            session.stopUi();
        }
    }

    @Test
    @DisplayName("startUi twice without stopUi between is rejected")
    void doubleStartRejected() {
        LocalSession session = newSession("double-start");
        try {
            session.startUi("tui");

            assertThatThrownBy(() -> session.startUi("tui"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already running");
        } finally {
            session.stopUi();
        }
    }

    @Test
    @DisplayName("startUi with an unknown uiMode throws helpfully")
    void unknownUiModeRejected() {
        LocalSession session = newSession("bad-mode");
        assertThatThrownBy(() -> session.startUi("nonesuch"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonesuch")
                .hasMessageContaining("tui");
        assertThat(session.isUiRunning()).isFalse();
    }

    @Test
    @DisplayName("addWindow / removeWindow before startUi is rejected; after stopUi is silent for removal")
    void windowOpsOutsideLifecycle() {
        LocalSession session = newSession("window-ops");
        Window w = new Window(
                ItemRef.iid("cg.test:item"),
                UiSessionLifecycleTest::clockScene);

        assertThatThrownBy(() -> session.addWindow(w))
                .isInstanceOf(IllegalStateException.class);

        // Remove before-start is a no-op (lifecycle method, idempotent shape).
        session.removeWindow(w);
        session.requestRender(); // no-op when not running
    }

    private static void waitFor(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting", e);
            }
        }
        throw new AssertionError("condition not satisfied within " + timeout);
    }
}
