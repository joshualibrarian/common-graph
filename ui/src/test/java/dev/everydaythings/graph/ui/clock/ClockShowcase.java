package dev.everydaythings.graph.ui.clock;

import dev.everydaythings.graph.ui.scene.surface.primitive.ClockFace;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.skia.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Visual showcase: opens a Skia window demonstrating the declarative clock widget.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>STACK layout — children overlap at the same position</li>
 *   <li>Rotation binding — containers rotate based on data-bound angles</li>
 *   <li>@Transition on rotation — hands sweep smoothly</li>
 *   <li>@Scene.If — conditional digital overlay</li>
 * </ul>
 *
 * <p>Press Escape to close. Click to toggle analog/digital mode.
 */
public class ClockShowcase {

    public static void main(String[] args) {
        FontCache fontCache = new FontCache(new dev.everydaythings.graph.ui.text.FontRegistry());
        LayoutEngine engine = new LayoutEngine(fontCache);
        SkiaPainter painter = new SkiaPainter(fontCache);

        // Mutable clock state — ClockFace is now a regular class
        ClockFace clock = ClockFace.now();

        SkiaWindow window = new SkiaWindow();
        window.init("Clock Widget — Skia");
        window.show();

        // Schedule timer to update clock every second
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clock-tick");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleAtFixedRate(() -> {
            clock.tick();
            window.requestPaint();
        }, 0, 1, TimeUnit.SECONDS);

        window.onPaint(canvas -> {
            // Rebuild the surface tree with current model
            SurfaceSchema<ClockFace> surface = new SurfaceSchema<>() {};
            surface.value(clock);
            surface.structureClass(ClockFace.class);

            SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
            surface.render(renderer);
            LayoutNode.BoxNode layoutRoot = renderer.result();

            // Layout with intrinsic size (not full window — so the clock stays 200x200)
            engine.layout(layoutRoot, 200, 200);

            // Center the clock in the window
            float offsetX = (window.width() - layoutRoot.width()) / 2f;
            float offsetY = (window.height() - layoutRoot.height()) / 2f;

            // Clear background
            canvas.clear(0xFF11111B);

            canvas.save();
            canvas.translate(offsetX, offsetY);
            painter.paint(canvas, layoutRoot);
            canvas.restore();
        });

        window.onKey((key, scancode, action, mods) -> {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(window.handle(), true);
            }
        });

        window.onMouseButton((button, action, mods) -> {
            if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                clock.toggleMode();
                clock.tick();
                window.requestPaint();
            }
        });

        window.runLoop();

        timer.shutdown();
        fontCache.close();
        window.destroy();
    }
}
