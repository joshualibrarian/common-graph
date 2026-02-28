package dev.everydaythings.graph.ui;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles window resize from a grip region in the bottom-right corner.
 *
 * <p>Designed for borderless windows ({@code GLFW_DECORATED = FALSE}) that
 * need a visible, interactive resize affordance.
 *
 * <p>Decoupled from rendering — tracks interaction state and cursor shape.
 * The visual grip indicator is rendered via {@link #paintGrip} with a
 * caller-supplied {@link DotEmitter} callback.
 *
 * <p>Usage: call {@link #onMouseButton} and {@link #onCursorPos} from
 * the window's mouse callbacks. Both return {@code true} if the event
 * was consumed by the resize controller.
 */
public class WindowResizeController {

    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 300;

    /** GLFW 3.4+ diagonal resize cursor (NW↔SE). */
    private static final int GLFW_RESIZE_NWSE = 0x00036009;

    // Catppuccin Mocha text color
    private static final int GRIP_COLOR_RGB = 0xCDD6F4;

    private final float gripSize;
    private Stage stage;

    private boolean resizing;
    private boolean hovering;
    private double edgeOffsetX, edgeOffsetY;

    // Cursor
    private long resizeCursor;

    // Hover change notification
    private Runnable onHoverChanged;

    public WindowResizeController(float gripSize) {
        this.gripSize = gripSize;
    }

    /**
     * Update the stage this controller operates on.
     * Called after window initialization when GLFW is ready.
     *
     * <p>Recreates the cursor resource because {@code glfwTerminate()} in the
     * previous window's {@code destroy()} invalidates all GLFW objects.
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        this.hovering = false;
        this.resizing = false;
        // Previous cursor was destroyed by glfwTerminate — recreate
        resizeCursor = 0;
        initCursor();
    }

    /**
     * Set a callback invoked when the hover state changes (enter/leave).
     * Used to trigger a visual update of the grip indicator.
     */
    public void onHoverChanged(Runnable callback) {
        this.onHoverChanged = callback;
    }

    /**
     * Handle a mouse button event. Returns true if consumed.
     */
    public boolean onMouseButton(int button, int action, double cursorX, double cursorY) {
        if (stage == null) return false;
        if (button == 0) { // left button
            if (action == 1 && isInGripRegion(cursorX, cursorY)) {
                resizing = true;
                // Record the distance from cursor to bottom-right edge.
                // During resize, new size = cursor + edgeOffset.
                edgeOffsetX = stage.width() - cursorX;
                edgeOffsetY = stage.height() - cursorY;
                return true;
            }
            if (action == 0 && resizing) {
                resizing = false;
                return true;
            }
        }
        return false;
    }

    /**
     * Handle a cursor position event. Returns true if consumed (during resize).
     *
     * <p>Always updates hover state for cursor shape and grip visual,
     * but only consumes the event when actively resizing.
     */
    public boolean onCursorPos(double cursorX, double cursorY) {
        if (stage == null) return false;

        boolean wasHovering = hovering;
        hovering = isInGripRegion(cursorX, cursorY) || resizing;

        if (hovering != wasHovering) {
            updateCursor();
            if (onHoverChanged != null) {
                onHoverChanged.run();
            }
        }

        if (resizing) {
            int newW = Math.max(MIN_WIDTH, (int) (cursorX + edgeOffsetX));
            int newH = Math.max(MIN_HEIGHT, (int) (cursorY + edgeOffsetY));
            stage.setWindowSize(newW, newH);
            return true;
        }
        return false;
    }

    /** Whether the cursor is currently over the grip region. */
    public boolean isHovering() { return hovering; }

    /** Whether a resize drag is in progress. */
    public boolean isResizing() { return resizing; }

    /** The grip region size in pixels. */
    public float gripSize() { return gripSize; }

    // ==================== Visual ====================

    /**
     * Emit the grip indicator dots via the supplied callback.
     *
     * <p>Renders a diagonal dot pattern in the bottom-right corner:
     * <pre>
     *         ●
     *       ● ●
     *     ● ● ●
     * </pre>
     *
     * <p>Dots are brighter when hovering, subtle when idle.
     *
     * @param stageWidth  current stage width in pixels
     * @param stageHeight current stage height in pixels
     * @param emitter     callback to render each dot
     */
    public void paintGrip(float stageWidth, float stageHeight, DotEmitter emitter) {
        if (stageWidth <= 0 || stageHeight <= 0) return;

        int alpha = hovering || resizing ? 153 : 51;  // 0.6 or 0.2
        int color = (alpha << 24) | GRIP_COLOR_RGB;

        float dot = 2f;
        float gap = 5f;
        float margin = 7f;

        // Anchor: bottom-right corner of the dot grid
        float bx = stageWidth - margin;
        float by = stageHeight - margin;

        // Bottom row: 3 dots
        emitter.emit(bx - dot, by - dot, dot, color);
        emitter.emit(bx - dot - gap, by - dot, dot, color);
        emitter.emit(bx - dot - 2 * gap, by - dot, dot, color);

        // Middle row: 2 dots
        emitter.emit(bx - dot, by - dot - gap, dot, color);
        emitter.emit(bx - dot - gap, by - dot - gap, dot, color);

        // Top row: 1 dot
        emitter.emit(bx - dot, by - dot - 2 * gap, dot, color);
    }

    /**
     * Callback for rendering a single grip dot.
     *
     * @see #paintGrip
     */
    @FunctionalInterface
    public interface DotEmitter {
        /**
         * Render a dot at (x, y) with the given size and ARGB color.
         */
        void emit(float x, float y, float size, int argbColor);
    }

    // ==================== Cleanup ====================

    /**
     * Release cursor resources.
     */
    public void destroy() {
        if (resizeCursor != 0) {
            glfwDestroyCursor(resizeCursor);
            resizeCursor = 0;
        }
    }

    // ==================== Internal ====================

    private boolean isInGripRegion(double x, double y) {
        return x >= stage.width() - gripSize
            && y >= stage.height() - gripSize;
    }

    private void updateCursor() {
        if (stage == null) return;
        if (hovering && resizeCursor != 0) {
            glfwSetCursor(stage.handle(), resizeCursor);
        } else {
            glfwSetCursor(stage.handle(), 0); // restore default
        }
    }

    private void initCursor() {
        try {
            resizeCursor = glfwCreateStandardCursor(GLFW_RESIZE_NWSE);
        } catch (Throwable t) {
            resizeCursor = 0;
        }
    }
}
