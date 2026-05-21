package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.VariableResolver;
import dev.everydaythings.graph.scene.Viewport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RenderLoop — dedicated render thread, snapshot-driven")
class RenderLoopTest {

    private final Viewport viewport = new Viewport(80, 24);

    private Body simpleScene() {
        return Body.of(
                ItemRef.iid(SceneText.KEY),
                List.of(Binding.literal(
                        ItemRef.iid(SceneVocabulary.Text.KEY),
                        "hello")));
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("Start, observe ticks, stop")
        void startTickStop() throws InterruptedException {
            CountingPainter painter = new CountingPainter();
            RenderLoop loop = new RenderLoop(
                    painter,
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50),
                    "test-loop");

            assertThat(loop.isRunning()).isFalse();
            loop.start();
            assertThat(loop.isRunning()).isTrue();

            painter.awaitPaints(3, Duration.ofSeconds(1));
            loop.stop();
            assertThat(loop.isRunning()).isFalse();

            int afterStop = painter.paintCount();
            Thread.sleep(150);
            assertThat(painter.paintCount()).isEqualTo(afterStop);
        }

        @Test
        @DisplayName("start() is idempotent")
        void startIdempotent() throws InterruptedException {
            CountingPainter painter = new CountingPainter();
            RenderLoop loop = new RenderLoop(
                    painter,
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50),
                    "test-loop");
            try {
                loop.start();
                loop.start();
                assertThat(loop.isRunning()).isTrue();
                painter.awaitPaints(2, Duration.ofSeconds(1));
            } finally {
                loop.stop();
            }
        }

        @Test
        @DisplayName("stop() before start() is a no-op")
        void stopBeforeStartNoop() {
            RenderLoop loop = new RenderLoop(
                    new CountingPainter(),
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50),
                    "test-loop");
            loop.stop();
            assertThat(loop.isRunning()).isFalse();
        }

        @Test
        @DisplayName("close() stops the loop and closes the painter")
        void closeStopsAndClosesPainter() throws InterruptedException {
            CountingPainter painter = new CountingPainter();
            RenderLoop loop = new RenderLoop(
                    painter,
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50),
                    "test-loop");
            loop.start();
            painter.awaitPaints(1, Duration.ofSeconds(1));
            loop.close();

            assertThat(loop.isRunning()).isFalse();
            assertThat(painter.closed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Signals")
    class Signals {

        @Test
        @DisplayName("requestRender() wakes the loop ahead of cadence")
        void requestRenderWakesLoop() throws InterruptedException {
            CountingPainter painter = new CountingPainter();
            RenderLoop loop = new RenderLoop(
                    painter,
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofSeconds(10),
                    "test-loop");
            try {
                loop.start();
                painter.awaitPaints(1, Duration.ofSeconds(1));   // initial frame
                int before = painter.paintCount();

                loop.requestRender();
                painter.awaitPaints(before + 1, Duration.ofMillis(500));
                assertThat(painter.paintCount()).isGreaterThan(before);
            } finally {
                loop.close();
            }
        }
    }

    @Nested
    @DisplayName("Exception isolation")
    class ExceptionIsolation {

        @Test
        @DisplayName("Supplier exception is logged and loop continues")
        void supplierExceptionDoesNotKillLoop() throws InterruptedException {
            AtomicInteger calls = new AtomicInteger();
            CountingPainter painter = new CountingPainter();
            RenderLoop loop = new RenderLoop(
                    painter,
                    new Presenter(viewport, VariableResolver.NONE),
                    () -> {
                        if (calls.incrementAndGet() == 1) {
                            throw new RuntimeException("boom");
                        }
                        return simpleScene();
                    },
                    Duration.ofMillis(50),
                    "test-loop");
            try {
                loop.start();
                painter.awaitPaints(1, Duration.ofSeconds(1));
                assertThat(calls.get()).isGreaterThanOrEqualTo(2);
            } finally {
                loop.close();
            }
        }

        @Test
        @DisplayName("Painter exception is logged and loop continues")
        void painterExceptionDoesNotKillLoop() throws InterruptedException {
            AtomicInteger paintCalls = new AtomicInteger();
            AtomicReference<Throwable> caughtByLoop = new AtomicReference<>();

            Painter throwingPainter = new Painter() {
                @Override public void paint(SceneNode positionedTree) {
                    if (paintCalls.incrementAndGet() == 1) {
                        throw new RuntimeException("paint failed");
                    }
                }
                @Override public Viewport viewport()         { return viewport; }
                @Override public FontMetrics fontMetrics()   { return new FontMetrics() {
                    @Override public float measureWidth(String t, float s) { return 0; }
                    @Override public float lineHeight(float s)             { return 1; }
                }; }
                @Override public Fidelity fidelity()         { return Fidelity.TEXT; }
                @Override public void close() {}
            };

            RenderLoop loop = new RenderLoop(
                    throwingPainter,
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50),
                    "test-loop");
            try {
                loop.start();
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (paintCalls.get() < 3 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertThat(paintCalls.get()).isGreaterThanOrEqualTo(2);
                assertThat(caughtByLoop.get()).isNull();
            } finally {
                loop.stop();
            }
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Constructor rejects null painter / presenter / supplier / cadence")
        void nullArgsRejected() {
            Presenter presenter = new Presenter(viewport, VariableResolver.NONE);
            CountingPainter painter = new CountingPainter();

            assertThatNullPointerException().isThrownBy(() -> new RenderLoop(
                    null, presenter, RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50), "loop"));
            assertThatNullPointerException().isThrownBy(() -> new RenderLoop(
                    painter, null, RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(50), "loop"));
            assertThatNullPointerException().isThrownBy(() -> new RenderLoop(
                    painter, presenter, null,
                    Duration.ofMillis(50), "loop"));
            assertThatNullPointerException().isThrownBy(() -> new RenderLoop(
                    painter, presenter, RenderLoopTest.this::simpleScene,
                    null, "loop"));
        }

        @Test
        @DisplayName("Constructor rejects zero / negative cadence")
        void nonPositiveCadenceRejected() {
            assertThatThrownBy(() -> new RenderLoop(
                    new CountingPainter(),
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ZERO, "loop"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RenderLoop(
                    new CountingPainter(),
                    new Presenter(viewport, VariableResolver.NONE),
                    RenderLoopTest.this::simpleScene,
                    Duration.ofMillis(-1), "loop"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================================================================================
    // Test helpers
    // ==================================================================================

    private final class CountingPainter implements Painter {
        private final AtomicInteger count = new AtomicInteger();
        private volatile boolean closed;
        private final Object lock = new Object();

        @Override public void paint(SceneNode positionedTree) {
            synchronized (lock) {
                count.incrementAndGet();
                lock.notifyAll();
            }
        }
        @Override public Viewport viewport()         { return viewport; }
        @Override public FontMetrics fontMetrics()   { return new FontMetrics() {
            @Override public float measureWidth(String t, float s) { return 0; }
            @Override public float lineHeight(float s)             { return 1; }
        }; }
        @Override public Fidelity fidelity()         { return Fidelity.TEXT; }
        @Override public void close()                { closed = true; }

        int paintCount()    { return count.get(); }
        boolean closed()    { return closed; }

        void awaitPaints(int target, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            synchronized (lock) {
                while (count.get() < target) {
                    long remainNanos = deadline - System.nanoTime();
                    if (remainNanos <= 0) break;
                    lock.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainNanos)));
                }
            }
            assertThat(count.get())
                    .as("paint count after waiting for >= %d", target)
                    .isGreaterThanOrEqualTo(target);
        }
    }
}
