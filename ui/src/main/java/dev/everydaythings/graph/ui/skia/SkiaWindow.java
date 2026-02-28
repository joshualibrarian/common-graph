package dev.everydaythings.graph.ui.skia;

import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.SurfaceOrigin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import dev.everydaythings.graph.ui.Stage;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window with Skia GPU-accelerated rendering.
 *
 * <p>Creates a GLFW window, initializes an OpenGL context, and sets up
 * Skia's DirectContext for hardware-accelerated 2D rendering.
 *
 * <p>The render loop:
 * <ol>
 *   <li>Poll GLFW events</li>
 *   <li>Call the paint callback to render content</li>
 *   <li>Flush Skia and swap buffers</li>
 * </ol>
 */
public class SkiaWindow implements Stage {

    private static final Logger log = LogManager.getLogger(SkiaWindow.class);

    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 700;
    private static final int BG_COLOR = 0xFF1E1E2E;  // Dark background

    private long window;
    private DirectContext context;
    private io.github.humbleui.skija.Surface skiaSurface;
    private BackendRenderTarget renderTarget;

    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private int fbWidth, fbHeight;
    private float dpi = 1.0f;
    private boolean dirty = true;
    private long lastFrameNanos;
    private double deltaTime;
    private boolean canPosition = true; // false on Wayland (no window positioning)

    // Callbacks
    private Consumer<Canvas> paintCallback;
    private BiConsumer<Integer, Integer> resizeCallback;
    private Stage.KeyCallback keyCallback;
    private Stage.CharCallback charCallback;
    private Stage.MouseButtonCallback mouseButtonCallback;
    private Stage.CursorPosCallback cursorPosCallback;
    private Stage.ScrollCallback scrollCallback;

    /**
     * Initialize the window. Must be called from the main thread.
     */
    public void init(String title) {
        // Set up error callback
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Detect Wayland — window positioning is unsupported
        try {
            canPosition = glfwGetPlatform() != GLFW_PLATFORM_WAYLAND;
        } catch (Throwable t) {
            canPosition = true; // GLFW < 3.4, assume X11
        }
        if (!canPosition) {
            log.info("Wayland detected — window drag disabled (compositor manages positioning)");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // Borderless — we render our own title bar via Surface system
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);

        // OpenGL hints for Skia
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_STENCIL_BITS, 8);
        glfwWindowHint(GLFW_SAMPLES, 0);

        // Create window
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // vsync
        GL.createCapabilities();

        // Get framebuffer size (may differ from window size on HiDPI)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pFbWidth = stack.mallocInt(1);
            var pFbHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pFbWidth, pFbHeight);
            fbWidth = pFbWidth.get(0);
            fbHeight = pFbHeight.get(0);
            dpi = (float) fbWidth / width;
        }

        // Initialize Skia
        context = DirectContext.makeGL();
        createSkiaSurface();

        // Set up GLFW callbacks
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            width = w;
            height = h;
            updateFramebufferSize();
            recreateSkiaSurface();
            dirty = true;
            if (resizeCallback != null) {
                resizeCallback.accept(w, h);
            }
        });

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            fbWidth = w;
            fbHeight = h;
            dpi = width > 0 ? (float) fbWidth / width : 1.0f;
            recreateSkiaSurface();
            dirty = true;
        });

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (keyCallback != null) {
                keyCallback.onKey(key, scancode, action, mods);
            }
            dirty = true;
        });

        glfwSetCharCallback(window, (win, codepoint) -> {
            if (charCallback != null) {
                charCallback.onChar(codepoint);
            }
            dirty = true;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (mouseButtonCallback != null) {
                mouseButtonCallback.onMouseButton(button, action, mods);
            }
            dirty = true;
        });

        glfwSetCursorPosCallback(window, (win, cursorX, cursorY) -> {
            if (cursorPosCallback != null) {
                cursorPosCallback.onCursorPos(cursorX, cursorY);
            }
        });

        glfwSetScrollCallback(window, (win, xOff, yOff) -> {
            if (scrollCallback != null) {
                scrollCallback.onScroll(xOff, yOff);
            }
            dirty = true;
        });

        log.info("SkiaWindow initialized: {}x{} (fb: {}x{}, dpi: {}) (hidden until show())", width, height, fbWidth, fbHeight, dpi);
    }

    /**
     * Run the main event loop. Blocks until the window is closed.
     */
    public void runLoop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();

            if (dirty) {
                render();
                dirty = false;
            } else {
                // Wait for events when idle (saves CPU)
                glfwWaitEventsTimeout(0.016); // ~60fps max
            }
        }
    }

    /**
     * Request a repaint on the next frame.
     */
    public void requestPaint() {
        dirty = true;
        glfwPostEmptyEvent();
    }

    /**
     * Request the window to close. The render loop will exit after the current frame.
     */
    public void requestClose() {
        glfwSetWindowShouldClose(window, true);
        glfwPostEmptyEvent();
    }

    /**
     * Render one frame.
     */
    private void render() {
        if (skiaSurface == null) return;

        // Track frame timing
        long now = System.nanoTime();
        if (lastFrameNanos > 0) {
            deltaTime = (now - lastFrameNanos) / 1_000_000_000.0;
        }
        lastFrameNanos = now;

        Canvas canvas = skiaSurface.getCanvas();
        canvas.clear(BG_COLOR);

        if (paintCallback != null) {
            canvas.save();
            canvas.scale(dpi, dpi);
            paintCallback.accept(canvas);
            canvas.restore();
        }

        context.flush();
        glfwSwapBuffers(window);
    }

    /**
     * Clean up all resources.
     */
    public void destroy() {
        if (skiaSurface != null) {
            skiaSurface.close();
            skiaSurface = null;
        }
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }

        if (window != NULL) {
            Callbacks.glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }

        glfwTerminate();
        var cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();

        log.info("SkiaWindow destroyed");
    }

    /**
     * Show the window. Call after setting position/size to avoid a flash
     * at the default location.
     */
    public void show() {
        glfwShowWindow(window);
    }

    // ==================== Callbacks ====================

    public void onPaint(Consumer<Canvas> callback) {
        this.paintCallback = callback;
    }

    public void onResize(BiConsumer<Integer, Integer> callback) {
        this.resizeCallback = callback;
    }

    @Override
    public void onKey(Stage.KeyCallback callback) {
        this.keyCallback = callback;
    }

    @Override
    public void onChar(Stage.CharCallback callback) {
        this.charCallback = callback;
    }

    @Override
    public void onMouseButton(Stage.MouseButtonCallback callback) {
        this.mouseButtonCallback = callback;
    }

    @Override
    public void onCursorPos(Stage.CursorPosCallback callback) {
        this.cursorPosCallback = callback;
    }

    @Override
    public void onScroll(Stage.ScrollCallback callback) {
        this.scrollCallback = callback;
    }

    // ==================== Window Position ====================

    public int[] getWindowPos() {
        if (!canPosition) return new int[]{0, 0};
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var px = stack.mallocInt(1);
            var py = stack.mallocInt(1);
            glfwGetWindowPos(window, px, py);
            return new int[]{px.get(0), py.get(0)};
        }
    }

    public void setWindowPos(int x, int y) {
        if (!canPosition) return;
        glfwSetWindowPos(window, x, y);
    }

    public void setWindowSize(int w, int h) {
        glfwSetWindowSize(window, w, h);
    }

    // ==================== Accessors ====================

    public int width() { return width; }
    public int height() { return height; }
    public float dpi() { return dpi; }
    public long handle() { return window; }

    /** Time elapsed since the last frame, in seconds. */
    public double deltaTime() { return deltaTime; }

    // ==================== Helpers ====================

    private void updateFramebufferSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pFbWidth = stack.mallocInt(1);
            var pFbHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pFbWidth, pFbHeight);
            fbWidth = pFbWidth.get(0);
            fbHeight = pFbHeight.get(0);
            dpi = width > 0 ? (float) fbWidth / width : 1.0f;
        }
    }

    private void createSkiaSurface() {
        // Get the current OpenGL framebuffer ID
        int fbId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int stencilBits = GL11.glGetInteger(GL11.GL_STENCIL_BITS);

        renderTarget = BackendRenderTarget.makeGL(
                fbWidth, fbHeight,
                0,           // samples
                stencilBits, // stencil bits
                fbId,        // framebuffer object ID
                FramebufferFormat.GR_GL_RGBA8
        );

        skiaSurface = io.github.humbleui.skija.Surface.makeFromBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorSpace.getSRGB()
        );

        if (skiaSurface == null) {
            throw new RuntimeException("Failed to create Skia surface from OpenGL render target");
        }
    }

    private void recreateSkiaSurface() {
        if (skiaSurface != null) {
            skiaSurface.close();
        }
        if (renderTarget != null) {
            renderTarget.close();
        }
        createSkiaSurface();
    }
}
