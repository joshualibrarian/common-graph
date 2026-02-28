package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.filament.gltfio.Gltfio;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;

import dev.everydaythings.graph.ui.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeCocoa.*;
import static org.lwjgl.glfw.GLFWNativeX11.*;
import static org.lwjgl.system.Platform.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window with Filament GPU-accelerated rendering supporting multiple panes.
 *
 * <p>Each pane is a separate Filament {@link View} with its own {@link Scene}
 * and {@link Camera}. Panes are rendered in order between
 * {@code beginFrame()} and {@code endFrame()}.
 *
 * <p>The render loop:
 * <ol>
 *   <li>Poll GLFW events</li>
 *   <li>beginFrame → render(pane1.view) → render(pane2.view) → ... → endFrame</li>
 * </ol>
 *
 * <h3>Coordinate spaces</h3>
 *
 * <p>Two coordinate spaces are tracked:
 * <ul>
 *   <li><b>Logical</b> — window points from {@code glfwGetWindowSize}. Used for layout
 *       and camera projection. Stable across HiDPI scale factors.</li>
 *   <li><b>Viewport</b> — backend-appropriate pixel coordinates for Filament viewports.
 *       On macOS/Metal this equals logical (Metal handles contentsScale internally).
 *       On Vulkan/OpenGL this equals framebuffer pixels.</li>
 * </ul>
 *
 * <p>Both are updated exclusively in GLFW callbacks during {@code glfwPollEvents()}.
 * Accessors ({@link #width()}, {@link #height()}) return cached values — they never
 * re-query GLFW. This guarantees all code within a single frame sees consistent dimensions.
 *
 * @see FilamentPane
 */
public class FilamentWindow implements Stage {

    private static final Logger log = LogManager.getLogger(FilamentWindow.class);

    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 700;

    // Swapchain recreation throttle (avoids Vulkan swapchain leak — google/filament#8185)
    private static final long SWAPCHAIN_MIN_INTERVAL_NS = 100_000_000L; // 100ms

    private long window;

    // Logical window size — updated only in GLFW callbacks
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;

    // Viewport size — backend-appropriate coordinates (see class javadoc)
    private int vpWidth;
    private int vpHeight;

    private boolean dirty = true;
    private boolean sizeChanged = true; // triggers syncSizes() in render loop

    // Filament core objects
    private Engine engine;
    private Renderer renderer;
    private long nativeWindow;
    private SwapChain swapChain;
    private long lastSwapChainRecreateNanos;

    // Multi-view panes
    private final List<FilamentPane> panes = new ArrayList<>();

    // Callbacks
    private BiConsumer<Integer, Integer> resizeCallback;
    private Stage.KeyCallback keyCallback;
    private Stage.CharCallback charCallback;
    private Stage.MouseButtonCallback mouseButtonCallback;
    private Stage.CursorPosCallback cursorPosCallback;
    private Stage.ScrollCallback scrollCallback;
    private Runnable beforeRenderCallback;

    // Frame timing
    private long lastFrameNanos = System.nanoTime();

    /**
     * Query the backend-appropriate viewport dimensions from GLFW.
     *
     * <p>On macOS/Metal, returns logical window points (Metal's CAMetalLayer handles
     * contentsScale internally). On Vulkan/OpenGL, returns framebuffer pixels.
     */
    private int[] queryViewportSize() {
        if (get() == MACOSX && engine != null && engine.getBackend() == Engine.Backend.METAL) {
            int[] w = new int[1], h = new int[1];
            glfwGetWindowSize(window, w, h);
            return new int[]{Math.max(1, w[0]), Math.max(1, h[0])};
        }
        int[] fbW = new int[1], fbH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        return new int[]{Math.max(1, fbW[0]), Math.max(1, fbH[0])};
    }

    /**
     * Initialize the window and Filament engine.
     * Must be called from the main thread.
     */
    public void init(String title) {
        GLFWErrorCallback.createPrint(System.err).set();

        if (get() == LINUX) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        } else if (get() == MACOSX) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_COCOA);
        }

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new RuntimeException("Failed to create GLFW window");
        }

        nativeWindow = nativeWindowHandle(window);

        Filament.init();
        Gltfio.init();

        Engine.Config config = new Engine.Config();
        config.driverHandleArenaSizeMB = 256;

        if (get() == MACOSX) {
            boolean forceOpenGL = Boolean.getBoolean("graph.filament.opengl");
            if (forceOpenGL) {
                engine = new Engine.Builder().backend(Engine.Backend.OPENGL).config(config).build();
                if (engine == null) {
                    log.info("OpenGL unavailable; falling back to Metal");
                    engine = new Engine.Builder().backend(Engine.Backend.METAL).config(config).build();
                }
            } else {
                engine = new Engine.Builder().backend(Engine.Backend.METAL).config(config).build();
                if (engine == null) {
                    log.info("Metal unavailable; falling back to OpenGL");
                    engine = new Engine.Builder().backend(Engine.Backend.OPENGL).config(config).build();
                }
            }
        } else {
            engine = new Engine.Builder().backend(Engine.Backend.VULKAN).config(config).build();
            if (engine == null) {
                log.info("Vulkan not available, falling back to OpenGL");
                engine = new Engine.Builder().backend(Engine.Backend.OPENGL).config(config).build();
            }
        }
        if (engine == null) {
            throw new RuntimeException("Failed to create Filament engine (no supported backend)");
        }
        log.info("Filament engine created with backend: {}", engine.getBackend());

        renderer = engine.createRenderer();
        swapChain = engine.createSwapChainFromRawPointer(nativeWindow, 0);

        // Dark background — Catppuccin Mocha Base #1E1E2E
        Renderer.ClearOptions clearOptions = new Renderer.ClearOptions();
        clearOptions.clearColor = new float[]{0x1E / 255f, 0x1E / 255f, 0x2E / 255f, 1.0f};
        clearOptions.clearStencil = 0;
        clearOptions.clear = true;
        clearOptions.discard = true;
        renderer.setClearOptions(clearOptions);

        // GLFW callbacks — single source of truth for cached dimensions.
        // Both callbacks may fire during resize; we update sizes from whichever
        // arrives and notify the listener exactly once per glfwPollEvents cycle
        // via the dirty flag (listener is invoked in the render loop).
        // Don't query sizes inside callbacks — on macOS, glfwGetFramebufferSize
        // returns stale values before the backing layer updates. Instead, set a flag
        // and query in the render loop after glfwPollEvents() returns.
        glfwSetFramebufferSizeCallback(window, (win, newFbW, newFbH) -> {
            sizeChanged = true;
        });
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            sizeChanged = true;
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

        glfwSetCursorPosCallback(window, (win, x, y) -> {
            if (cursorPosCallback != null) {
                cursorPosCallback.onCursorPos(x, y);
            }
        });

        glfwSetScrollCallback(window, (win, xOffset, yOffset) -> {
            if (scrollCallback != null) {
                scrollCallback.onScroll(xOffset, yOffset);
            }
            dirty = true;
        });

        log.info("FilamentWindow initialized: {}x{} (hidden until show())", width, height);
    }

    /**
     * Sync cached sizes from GLFW and update all full-window pane viewports.
     * Called from the render loop after glfwPollEvents(), NOT from callbacks.
     * On macOS, calling glfwGetFramebufferSize inside a callback returns stale values.
     */
    private void syncSizes() {
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        int[] vp = queryViewportSize();

        boolean changed = (w[0] != width) || (h[0] != height)
                || (vp[0] != vpWidth) || (vp[1] != vpHeight);
        if (!changed) return;

        width = w[0];
        height = h[0];
        vpWidth = vp[0];
        vpHeight = vp[1];

        // Update all full-window panes to the new viewport size
        for (FilamentPane pane : panes) {
            if (pane.isFullWindow()) {
                pane.setViewport(0, 0, vpWidth, vpHeight);
            }
        }

        dirty = true;
        if (resizeCallback != null) {
            resizeCallback.accept(width, height);
        }
    }

    private static long nativeWindowHandle(long glfwWindow) {
        if (get() == LINUX) {
            return glfwGetX11Window(glfwWindow);
        }
        if (get() == MACOSX) {
            return glfwGetCocoaWindow(glfwWindow);
        }
        throw new IllegalStateException("Unsupported OS for native window handle: " + get());
    }

    /**
     * Show the window. Call after setting position/size to avoid a flash
     * at the default location.
     */
    public void show() {
        glfwShowWindow(window);

        // Some window managers delay callbacks until interaction.
        // Force an immediate sync so the first frame has correct dimensions.
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(window, w, h);
        width = w[0];
        height = h[0];
        int[] vp = queryViewportSize();
        vpWidth = vp[0];
        vpHeight = vp[1];

        for (FilamentPane pane : panes) {
            if (pane.isFullWindow()) {
                pane.setViewport(0, 0, vpWidth, vpHeight);
            }
        }

        dirty = true;
        if (resizeCallback != null) {
            resizeCallback.accept(width, height);
        }
    }

    // ==================== Pane Management ====================

    /**
     * Create a new rendering pane.
     *
     * <p>The pane's viewport defaults to the full window. Call
     * {@link FilamentPane#setViewport} to restrict it to a subregion.
     *
     * @param perspective true for perspective 3D, false for orthographic 2D
     * @return the new pane
     */
    public FilamentPane createPane(boolean perspective) {
        FilamentPane pane = new FilamentPane(engine, perspective);

        int[] vp = queryViewportSize();
        vpWidth = vp[0];
        vpHeight = vp[1];
        pane.setViewport(0, 0, vpWidth, vpHeight);
        pane.markFullWindow();

        panes.add(pane);
        return pane;
    }

    /**
     * Remove and destroy a pane.
     */
    public void destroyPane(FilamentPane pane) {
        panes.remove(pane);
        pane.destroy(engine);
    }

    /**
     * Get an unmodifiable view of the current panes.
     */
    public List<FilamentPane> panes() {
        return Collections.unmodifiableList(panes);
    }

    /**
     * Run the main event loop. Blocks until the window is closed.
     */
    public void runLoop() {
        lastFrameNanos = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();

            // Sync dimensions after events settle — never inside callbacks
            // where macOS returns stale framebuffer sizes.
            if (sizeChanged) {
                sizeChanged = false;
                syncSizes();
                // Recreate swapchain so the JNI layer updates the CAMetalLayer's
                // drawableSize to match the new window dimensions.
                recreateSwapChain();
            }

            long now = System.nanoTime();

            // Skip when minimized
            if (width <= 0 || height <= 0) continue;

            // Per-frame update (camera, animation, layout rebuild, etc.)
            if (beforeRenderCallback != null) {
                beforeRenderCallback.run();
            }
            lastFrameNanos = now;

            // Render all panes
            try {
                if (swapChain == null) continue;

                var activeViews = panes.stream()
                        .filter(p -> p.viewportWidth() > 0 && p.viewportHeight() > 0)
                        .map(FilamentPane::view)
                        .toList();

                boolean began = renderer.beginFrame(swapChain, now);

                // On macOS/Metal, beginFrame fails transiently during live resize.
                // Recreate the swapchain (throttled) and retry once.
                if (!began) {
                    if (maybeRecreateSwapChain(now)) {
                        began = renderer.beginFrame(swapChain, now);
                    }
                }

                if (began) {
                    for (View view : activeViews) {
                        renderer.render(view);
                    }
                    renderer.endFrame();
                }
            } catch (Exception e) {
                log.warn("Frame render failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Recreate the swapchain unconditionally.
     * Required on macOS/Metal when the window resizes — the CAMetalLayer's
     * drawable size changes but Filament's swapchain caches the original size.
     */
    private void recreateSwapChain() {
        destroySwapChain();
        swapChain = engine.createSwapChainFromRawPointer(nativeWindow, 0);
    }

    /**
     * Recreate the swapchain if enough time has passed since the last recreation.
     * Throttled to avoid GPU memory leaks from rapid recreation (google/filament#8185).
     *
     * @return true if the swapchain was recreated
     */
    private boolean maybeRecreateSwapChain(long nowNanos) {
        if ((nowNanos - lastSwapChainRecreateNanos) < SWAPCHAIN_MIN_INTERVAL_NS) {
            return false;
        }
        recreateSwapChain();
        lastSwapChainRecreateNanos = nowNanos;
        return true;
    }

    private void destroySwapChain() {
        if (swapChain == null) return;
        engine.flushAndWait();
        engine.destroySwapChain(swapChain);
        swapChain = null;
    }

    /**
     * Get the time elapsed since the last frame, in seconds.
     */
    public double deltaTime() {
        return (System.nanoTime() - lastFrameNanos) / 1_000_000_000.0;
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
     * Clean up all resources.
     * Filament resources must be destroyed in the correct order.
     */
    public void destroy() {
        if (engine != null) {
            for (FilamentPane pane : panes) {
                pane.destroy(engine);
            }
            panes.clear();

            engine.flushAndWait();
            if (renderer != null) engine.destroyRenderer(renderer);
            destroySwapChain();

            engine.destroy();
            engine = null;
        }

        if (window != NULL) {
            Callbacks.glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            window = NULL;
        }

        glfwTerminate();
        var cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();

        log.info("FilamentWindow destroyed");
    }

    // ==================== Callbacks ====================

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

    public void onBeforeRender(Runnable callback) {
        this.beforeRenderCallback = callback;
    }

    // ==================== Window Position ====================

    public int[] getWindowPos() {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var px = stack.mallocInt(1);
            var py = stack.mallocInt(1);
            glfwGetWindowPos(window, px, py);
            return new int[]{px.get(0), py.get(0)};
        }
    }

    public void setWindowPos(int x, int y) {
        glfwSetWindowPos(window, x, y);
    }

    public void setWindowSize(int w, int h) {
        glfwSetWindowSize(window, w, h);
    }

    // ==================== Accessors ====================

    public Engine engine() { return engine; }
    public Renderer renderer() { return renderer; }

    /** Logical window width — stable within a frame. */
    public int width() { return width; }

    /** Logical window height — stable within a frame. */
    public int height() { return height; }

    /** Viewport width in backend-appropriate coordinates. */
    public int viewportWidth() { return vpWidth; }

    /** Viewport height in backend-appropriate coordinates. */
    public int viewportHeight() { return vpHeight; }

    public long handle() { return window; }

    /**
     * Set the background clear color for the renderer.
     */
    public void setClearColor(float r, float g, float b, float a) {
        Renderer.ClearOptions clearOptions = new Renderer.ClearOptions();
        clearOptions.clearColor = new float[]{r, g, b, a};
        clearOptions.clearStencil = 0;
        clearOptions.clear = true;
        clearOptions.discard = true;
        renderer.setClearOptions(clearOptions);
    }
}
