package dev.everydaythings.graph.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFWErrorCallback;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.Platform.*;

/**
 * Manages the GLFW lifecycle across multiple windows.
 *
 * <p>GLFW is process-global state — {@code glfwInit()} must be called once before
 * creating any windows, and {@code glfwTerminate()} invalidates ALL GLFW objects
 * (windows, cursors, monitors). With multiple windows, individual windows cannot
 * call init/terminate independently.
 *
 * <p>This class provides ref-counted acquire/release. The first acquire initializes
 * GLFW (with platform hints); the last release terminates it.
 *
 * <p>All methods must be called from the main thread (GLFW requirement).
 */
public final class GlfwLifecycle {

    private static final Logger log = LogManager.getLogger(GlfwLifecycle.class);

    private static int refCount = 0;
    private static GLFWErrorCallback errorCallback;

    private GlfwLifecycle() {}

    /**
     * Acquire a GLFW reference. Initializes GLFW on first call.
     *
     * @throws IllegalStateException if GLFW initialization fails
     */
    public static synchronized void acquire() {
        if (refCount == 0) {
            errorCallback = GLFWErrorCallback.createPrint(System.err);
            errorCallback.set();

            if (get() == LINUX) {
                glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
            } else if (get() == MACOSX) {
                glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_COCOA);
            }

            if (!glfwInit()) {
                throw new IllegalStateException("Unable to initialize GLFW");
            }
            log.debug("GLFW initialized");
        }
        refCount++;
    }

    /**
     * Release a GLFW reference. Terminates GLFW when the last reference is released.
     */
    public static synchronized void release() {
        if (refCount <= 0) {
            log.warn("GlfwLifecycle.release() called with refCount={}", refCount);
            return;
        }
        refCount--;
        if (refCount == 0) {
            glfwTerminate();
            if (errorCallback != null) {
                errorCallback.free();
                errorCallback = null;
            }
            log.debug("GLFW terminated");
        }
    }

    /**
     * Whether GLFW is currently initialized.
     */
    public static synchronized boolean isInitialized() {
        return refCount > 0;
    }
}
