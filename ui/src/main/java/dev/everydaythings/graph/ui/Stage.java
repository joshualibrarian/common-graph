package dev.everydaythings.graph.ui;

import java.util.function.BiConsumer;

/**
 * Platform-agnostic window abstraction.
 *
 * <p>Both rendering backends — Skia (2D) and Filament (3D) — implement
 * this interface, providing a common API for window lifecycle, geometry,
 * input callbacks, and repaint control.
 *
 * <p>Backend-specific features (e.g., Filament's pane management, Skia's
 * canvas painting) remain on the concrete classes.
 *
 * <p>Extends {@link WindowDragController.WindowOps} so any Stage can be
 * passed directly to the drag controller for borderless window movement.
 */
public interface Stage extends WindowDragController.WindowOps {

    // ==================== Lifecycle ====================

    /** Initialize the window. Must be called from the main thread. */
    void init(String title);

    /** Show the window. Call after setting position/size to avoid a flash. */
    void show();

    /** Run the main event loop. Blocks until the window is closed. */
    void runLoop();

    /**
     * Process one frame of work (non-blocking).
     *
     * <p>Handles size changes, renders if dirty, advances animations.
     * Does NOT call {@code glfwPollEvents()} — the caller is responsible
     * for polling (enables a single poll to drive multiple windows).
     *
     * @return true if the window is still alive, false if it wants to close
     */
    boolean tick();

    /**
     * Whether the window is requesting to close.
     */
    boolean shouldClose();

    /** Clean up all resources. */
    void destroy();

    /** Request a repaint on the next frame. */
    void requestPaint();

    /** Request the window to close. The render loop will exit after the current frame. */
    void requestClose();

    // ==================== Geometry ====================

    int width();
    int height();
    long handle();

    // getWindowPos() and setWindowPos() inherited from WindowDragController.WindowOps

    void setWindowSize(int w, int h);

    // ==================== Timing ====================

    /** Time elapsed since the last frame, in seconds. */
    double deltaTime();

    // ==================== Input Callbacks ====================

    @FunctionalInterface
    interface KeyCallback {
        void onKey(int key, int scancode, int action, int mods);
    }

    @FunctionalInterface
    interface CharCallback {
        void onChar(int codepoint);
    }

    @FunctionalInterface
    interface MouseButtonCallback {
        void onMouseButton(int button, int action, int mods);
    }

    @FunctionalInterface
    interface CursorPosCallback {
        void onCursorPos(double x, double y);
    }

    @FunctionalInterface
    interface ScrollCallback {
        void onScroll(double xOffset, double yOffset);
    }

    void onResize(BiConsumer<Integer, Integer> callback);
    void onKey(KeyCallback callback);
    void onChar(CharCallback callback);
    void onMouseButton(MouseButtonCallback callback);
    void onCursorPos(CursorPosCallback callback);
    void onScroll(ScrollCallback callback);
}
