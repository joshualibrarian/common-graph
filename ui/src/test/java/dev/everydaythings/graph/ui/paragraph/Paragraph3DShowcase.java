package dev.everydaythings.graph.ui.paragraph;

import dev.everydaythings.filament.*;
import dev.everydaythings.filament.gltfio.Gltfio;
import dev.everydaythings.graph.ui.filament.FilamentSurfacePainter;
import dev.everydaythings.graph.ui.filament.MsdfFontManager;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.skia.*;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Visual showcase: opens a Filament 3D window demonstrating paragraph wrapping
 * via the MSDF text rendering path.
 *
 * <p>This is the key demonstration of the Paragraph abstraction — the MSDF path
 * previously had NO wrapping. Text just ran off the edge. Now it wraps correctly
 * using ICU4J line breaking + MSDF glyph metrics.
 *
 * <p>Run with: {@code ./gradlew :ui:paragraphShowcase3D}
 *
 * <p>Press Escape to close. Scroll with mouse wheel.
 */
public class Paragraph3DShowcase {

    public static void main(String[] args) {
        // Build the surface tree (same content as 2D showcase)
        ContainerSurface surface = ParagraphShowcase.buildSurface();

        // Build layout tree via SkiaSurfaceRenderer
        SkiaSurfaceRenderer surfRenderer = new SkiaSurfaceRenderer();
        surface.render(surfRenderer);
        LayoutNode.BoxNode layoutRoot = surfRenderer.result();

        // =====================================================================
        // Initialize Filament
        // =====================================================================
        Filament.init();
        Gltfio.init();

        org.lwjgl.glfw.GLFWErrorCallback.createPrint(System.err).set();
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        if (!glfwInit()) throw new IllegalStateException("Failed to init GLFW");

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        int width = 900, height = 900;
        long window = glfwCreateWindow(width, height, "Paragraph Showcase — Filament/MSDF", 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create window");

        long nativeWindow = org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(window);

        Engine engine = new Engine.Builder().backend(Engine.Backend.VULKAN).build();
        if (engine == null) {
            System.out.println("Vulkan unavailable, trying OpenGL...");
            engine = new Engine.Builder().backend(Engine.Backend.OPENGL).build();
        }
        if (engine == null) throw new RuntimeException("No rendering backend available");
        System.out.println("Filament backend: " + engine.getBackend());

        SwapChain swapChain = engine.createSwapChainFromRawPointer(nativeWindow, 0);
        Renderer renderer = engine.createRenderer();
        Scene scene = engine.createScene();
        View view = engine.createView();

        // Camera — orthographic, pixel-mapped
        int camEntity = engine.getEntityManager().create();
        Camera camera = engine.createCamera(camEntity);

        view.setScene(scene);
        view.setCamera(camera);
        view.setViewport(new Viewport(0, 0, width, height));
        view.setAntiAliasing(View.AntiAliasing.NONE);
        view.setDithering(View.Dithering.NONE);
        view.setToneMapping(View.ToneMapping.LINEAR);
        camera.setExposure(1.0f);

        // Dark background (Catppuccin Mocha Base in linear)
        Renderer.ClearOptions clear = new Renderer.ClearOptions();
        clear.clearColor = new float[]{0.012f, 0.012f, 0.028f, 1.0f};
        clear.clear = true;
        clear.discard = true;
        renderer.setClearOptions(clear);

        // =====================================================================
        // MSDF text + plane painter
        // =====================================================================
        MsdfFontManager fontManager = new MsdfFontManager(engine, new dev.everydaythings.graph.ui.text.FontRegistry());

        FilamentSurfacePainter painter = new FilamentSurfacePainter(engine, scene, fontManager);

        // Layout engine with MSDF measurer
        LayoutEngine layoutEngine = new LayoutEngine(fontManager);

        // =====================================================================
        // Input — scroll support
        // =====================================================================
        final float[] scrollY = {0};
        final boolean[] dirty = {true};

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            scrollY[0] -= (float) yoffset * 40; // 40 pixels per scroll notch
            if (scrollY[0] < 0) scrollY[0] = 0;
            dirty[0] = true;
        });

        final int[] currentWidth = {width};
        final int[] currentHeight = {height};
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            currentWidth[0] = w;
            currentHeight[0] = h;
            view.setViewport(new Viewport(0, 0, w, h));
            dirty[0] = true;
        });

        glfwShowWindow(window);

        // =====================================================================
        // Render loop
        // =====================================================================
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();

            int w = currentWidth[0], h = currentHeight[0];

            // Only rebuild geometry when something changes (resize, scroll).
            // Filament entities persist in the scene between frames —
            // recreating per-glyph entities every frame exhausts the handle arena.
            if (dirty[0]) {
                dirty[0] = false;

                // Layout at current window size
                layoutEngine.layout(layoutRoot, w, h);

                // Clamp scroll to content bounds
                float contentH = layoutRoot.height();
                float maxScroll = Math.max(0, contentH - h);
                if (scrollY[0] > maxScroll) scrollY[0] = maxScroll;

                // Configure camera for pixel-perfect orthographic
                float worldW = 2.0f;
                double aspect = (double) w / h;
                double halfW = 1.0;
                double halfH = 1.0 / aspect;
                camera.setProjection(Camera.Projection.ORTHO,
                        -halfW, halfW, -halfH, halfH, 0.1, 100.0);
                camera.lookAt(0, 1.0, 5.0,
                             0, 1.0, 0,
                             0, 1, 0);

                // Configure painter coordinate mapping with scroll offset
                float pixelsPerUnit = w / worldW;
                float scrollWorldOffset = scrollY[0] / pixelsPerUnit;
                painter.configureForLayout(w, h, worldW, 1.0f);
                painter.adjustOriginY(scrollWorldOffset);

                // Rebuild geometry
                painter.clear();
                painter.paint(layoutRoot, 0f);
            }

            // Render (always — even if geometry didn't change, Filament needs
            // to present the frame for the window to stay responsive)
            long now = System.nanoTime();
            if (renderer.beginFrame(swapChain, now)) {
                renderer.render(view);
                renderer.endFrame();
            }
        }

        // =====================================================================
        // Cleanup
        // =====================================================================
        painter.destroy();
        fontManager.destroy();
        engine.destroyRenderer(renderer);
        engine.destroyView(view);
        engine.destroyScene(scene);
        engine.destroyCameraComponent(camEntity);
        engine.destroySwapChain(swapChain);
        engine.destroy();

        org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
