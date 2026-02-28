package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.filament.gltfio.Gltfio;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Visual showcase: opens a Filament 3D window displaying chess piece GLB models.
 *
 * <p>Shows one piece from each type arranged in a row, white in front, black in back.
 * Mouse orbit + WASD camera controls. Escape to close.
 *
 * <p>Run with: {@code ./gradlew :ui:chessPieces3D}
 */
public class ChessPiece3DShowcase {

    private static final String[] PIECE_NAMES = {"king", "queen", "bishop", "knight", "rook", "pawn"};

    public static void main(String[] args) {
        // Initialize Filament
        Filament.init();
        Gltfio.init();

        // Create window (no OpenGL — Filament manages its own context)
        org.lwjgl.glfw.GLFWErrorCallback.createPrint(System.err).set();
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        if (!glfwInit()) throw new IllegalStateException("Failed to init GLFW");

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        int width = 1200, height = 700;
        long window = glfwCreateWindow(width, height, "Chess Pieces — 3D Showcase", 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create window");

        long nativeWindow = org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(window);

        // Create Filament engine
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

        // Camera
        int camEntity = engine.getEntityManager().create();
        Camera camera = engine.createCamera(camEntity);

        view.setScene(scene);
        view.setCamera(camera);
        view.setViewport(new Viewport(0, 0, width, height));
        view.setAntiAliasing(View.AntiAliasing.NONE);
        view.setDithering(View.Dithering.TEMPORAL);

        // Light grey background
        Renderer.ClearOptions clear = new Renderer.ClearOptions();
        clear.clearColor = new float[]{0.85f, 0.85f, 0.88f, 1.0f};
        clear.clear = true;
        clear.discard = true;
        renderer.setClearOptions(clear);

        // Create spatial renderer and load pieces
        FilamentSpatialRenderer spatial = new FilamentSpatialRenderer(engine, scene);

        // Ambient light (soft white room feel)
        spatial.environment(0xD9D9E0, 0xC0C0C8, 0, 0, 0);

        // Key light from above-right
        spatial.light("directional", 0xFFFFFF, 0.8,
                0, 0, 0,    // position (unused for directional)
                -0.5, -1.0, -0.3);  // direction

        // Fill light from left
        spatial.light("directional", 0xE8E0D8, 0.3,
                0, 0, 0,
                0.5, -0.5, 0.5);

        // Floor
        spatial.createFloor(20f, 0.02f, 0xD4C8B0);

        // Place white pieces (front row, Z = -1)
        float spacing = 2.5f;
        float startX = -(PIECE_NAMES.length - 1) * spacing / 2f;
        for (int i = 0; i < PIECE_NAMES.length; i++) {
            String model = "chess/models/w_" + PIECE_NAMES[i] + ".glb";
            float x = startX + i * spacing;
            spatial.pushTransform(x, 0, -1,
                    0, 0, 0, 1,   // no rotation
                    1, 1, 1);      // unit scale
            spatial.meshBody(model, 0xFFFFFF, 1.0, "lit", null);
            spatial.popTransform();
        }

        // Place black pieces (back row, Z = 1.5)
        for (int i = 0; i < PIECE_NAMES.length; i++) {
            String model = "chess/models/b_" + PIECE_NAMES[i] + ".glb";
            float x = startX + i * spacing;
            spatial.pushTransform(x, 0, 1.5,
                    0, 0, 0, 1,
                    1, 1, 1);
            spatial.meshBody(model, 0x000000, 1.0, "lit", null);
            spatial.popTransform();
        }

        // Camera controller for orbit
        CameraController camCtrl = new CameraController();
        camCtrl.setDefaults(45, 0.1, 100,
                0, 4, 10,     // eye: above and back
                0, 0.5, 0);   // look at: center of pieces
        camCtrl.setOrthographic(false);

        // Input
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            camCtrl.onKeyRaw(key, action);
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) ->
                camCtrl.onMouseButton(button, action, mods));

        glfwSetCursorPosCallback(window, (win, x, y) ->
                camCtrl.onCursorPos(x, y));

        glfwSetScrollCallback(window, (win, xOff, yOff) ->
                camCtrl.onScroll(xOff, yOff));

        final int[] currentWidth = {width};
        final int[] currentHeight = {height};
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            currentWidth[0] = w;
            currentHeight[0] = h;
            view.setViewport(new Viewport(0, 0, w, h));
        });

        // Show
        glfwShowWindow(window);

        // Render loop
        long lastFrame = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();

            long now = System.nanoTime();
            double dt = (now - lastFrame) / 1_000_000_000.0;
            lastFrame = now;

            camCtrl.update(dt);
            double aspect = (double) currentWidth[0] / currentHeight[0];
            camCtrl.applyToCamera(camera, aspect);

            if (renderer.beginFrame(swapChain, now)) {
                renderer.render(view);
                renderer.endFrame();
            }
        }

        // Cleanup
        spatial.destroy();
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
