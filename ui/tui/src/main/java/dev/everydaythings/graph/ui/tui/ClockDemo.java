package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.Viewport;
import dev.everydaythings.graph.ui.Presenter;
import dev.everydaythings.graph.ui.RenderLoop;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ClockDemo — the first visible end-to-end proof-of-life.
 *
 * <p>Assembles every piece of the rendering pipeline we've built so far:
 *
 * <ul>
 *   <li>A {@link Session} that binds {@link CoreVocabulary.CurrentTime
 *       CurrentTime} to a runtime supplier reading the system clock.</li>
 *   <li>A {@link Presenter} that substitutes the Variable reference with
 *       the supplied value on each render.</li>
 *   <li>A {@link TuiPainter} that emits the substituted scene to the
 *       terminal.</li>
 *   <li>A {@link RenderLoop} that ticks the pipeline at 1Hz.</li>
 * </ul>
 *
 * <p>Run via the {@code graph} launcher or {@code java -cp ...}; pass an
 * optional duration in seconds to control how long the clock runs.  At
 * each tick the current time prints on a new line — primitive, but a
 * legitimate end-to-end proof that Session → Presenter → Painter
 * composes on a real thread and produces visible output.
 *
 * <pre>
 *   $ ./gradlew :ui:tui:runClockDemo
 *   12:34:56
 *   12:34:57
 *   12:34:58
 *   ...
 * </pre>
 */
public final class ClockDemo {

    private ClockDemo() {}

    public static void main(String[] args) throws InterruptedException {
        Duration runFor = parseRunDuration(args);

        // Scene: SceneText whose text binding targets the CurrentTime Variable.
        ItemRef currentTimeRef = ItemRef.iid(CoreVocabulary.CurrentTime.KEY);
        Body clockScene = Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(
                        ItemRef.iid(SceneVocabulary.Text.KEY),
                        currentTimeRef)));

        // Session: bind CurrentTime to the system clock, formatted HH:mm:ss.
        Session session = new Session(
                ItemRef.iid("cg.demo:clock-session"),
                Librarian.anonymous());
        DateTimeFormatter format = DateTimeFormatter
                .ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        session.bindVariable(currentTimeRef, () -> format.format(Instant.now()));

        // Painter + Presenter + RenderLoop.
        Viewport viewport = new Viewport(80, 24);
        TuiPainter painter = new TuiPainter(new PrintWriter(System.out, true), viewport);
        Presenter presenter = new Presenter(viewport, session.variableResolver());
        RenderLoop loop = new RenderLoop(
                painter, presenter, () -> clockScene,
                Duration.ofSeconds(1), "clock-demo");

        Runtime.getRuntime().addShutdownHook(new Thread(loop::close, "clock-demo-shutdown"));

        loop.start();
        Thread.sleep(runFor.toMillis());
        loop.close();
    }

    /**
     * Read run duration from args[0] (in seconds), defaulting to 10s.
     * Negative or unparseable values fall back to the default.
     */
    private static Duration parseRunDuration(String[] args) {
        if (args == null || args.length == 0) return Duration.ofSeconds(10);
        try {
            long seconds = Long.parseLong(args[0]);
            return seconds <= 0 ? Duration.ofSeconds(10) : Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(10);
        }
    }
}
