package dev.everydaythings.graph.ui;

/**
 * Handles window drag-to-move from a title bar region.
 *
 * <p>Decoupled from GLFW via the {@link WindowOps} interface so it can
 * be unit-tested without a window system.
 *
 * <p>Usage: call {@link #onMouseButton} and {@link #onCursorPos} from
 * the window's mouse callbacks. Both return {@code true} if the event
 * was consumed by the drag controller.
 */
public class WindowDragController {

    private float titleBarHeight;
    private boolean dragging;
    private double grabOffsetX, grabOffsetY;

    private final WindowOps windowOps;

    /**
     * Abstraction over window-system calls for positioning.
     */
    public interface WindowOps {
        int[] getWindowPos();
        void setWindowPos(int x, int y);
    }

    public WindowDragController(float titleBarHeight, WindowOps windowOps) {
        this.titleBarHeight = titleBarHeight;
        this.windowOps = windowOps;
    }

    /**
     * Update the title bar height (e.g., after a layout pass).
     */
    public void titleBarHeight(float height) {
        this.titleBarHeight = height;
    }

    public float titleBarHeight() {
        return titleBarHeight;
    }

    /**
     * Handle a mouse button event. Returns true if consumed.
     *
     * @param button  GLFW button (0 = left)
     * @param action  GLFW action (1 = press, 0 = release)
     * @param cursorX cursor X in window coordinates
     * @param cursorY cursor Y in window coordinates
     */
    public boolean onMouseButton(int button, int action, double cursorX, double cursorY) {
        if (button == 0) { // left button
            if (action == 1 && cursorY <= titleBarHeight) { // press in title bar
                dragging = true;
                grabOffsetX = cursorX;
                grabOffsetY = cursorY;
                return true;
            }
            if (action == 0) { // release
                if (dragging) {
                    dragging = false;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handle a cursor position event. Returns true if consumed.
     *
     * <p>GLFW cursor positions are window-relative. When we move the window,
     * the cursor's window-relative position resets back to the grab offset.
     * So each frame we compute the delta from the grab point and apply it
     * to the current window position — not a stale starting position.
     */
    public boolean onCursorPos(double cursorX, double cursorY) {
        if (dragging) {
            int[] pos = windowOps.getWindowPos();
            int newX = pos[0] + (int) (cursorX - grabOffsetX);
            int newY = pos[1] + (int) (cursorY - grabOffsetY);
            windowOps.setWindowPos(newX, newY);
            return true;
        }
        return false;
    }

    public boolean isDragging() {
        return dragging;
    }
}
