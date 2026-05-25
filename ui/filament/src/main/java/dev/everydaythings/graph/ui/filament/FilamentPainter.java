package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.Camera;
import dev.everydaythings.filament.Engine;
import dev.everydaythings.filament.Filament;
import dev.everydaythings.filament.Renderer;
import dev.everydaythings.filament.Scene;
import dev.everydaythings.filament.SwapChain;
import dev.everydaythings.filament.View;
import dev.everydaythings.filament.Viewport;
import dev.everydaythings.filament.text.FlatTextSurface;
import dev.everydaythings.filament.text.MsdfFontManager;
import dev.everydaythings.filament.text.MsdfTextRenderer;
import dev.everydaythings.filament.text.TextMesh;
import dev.everydaythings.filament.text.TextMeshBuilder;
import dev.everydaythings.graph.quality.VisualVocabulary;
import dev.everydaythings.graph.scene.Bounds;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.value.Color;
import lombok.extern.log4j.Log4j2;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_DECORATED;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_X11;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwInitHint;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * FilamentPainter — RASTER_2D painter backed by Google Filament (via our
 * {@code filament-java} bindings) on a GLFW-managed X11 window.
 *
 * <h2>Why native Filament 2D</h2>
 *
 * <p>Two raster paths exist on this codebase: this one and {@code SkiaPainter}.
 * The plan calls for both to produce pixel-identical output for 2D content.
 * This implementation draws directly with Filament primitives (MSDF text via
 * {@code filament-java-text}; rectangles as native meshes when those come in
 * a later slice) rather than the simpler "Skia draws into an offscreen buffer
 * that Filament composites" delegation.  The native path costs more code up
 * front but sets up the same context Filament 3D will use, and the MSDF text
 * renderer is reusable for spatial text once 3D lands.
 *
 * <h2>What's wired today</h2>
 *
 * <ul>
 *   <li><b>Window + backend</b>: GLFW NO_API window (Filament manages its own
 *       graphics context), X11 platform forced so the Vulkan backend works
 *       under Wayland via XWayland.  Engine prefers Vulkan, falls back to
 *       OpenGL.</li>
 *   <li><b>Ortho camera</b>: pixel-coordinate ortho with Y pointing downward
 *       so {@link Bounds} from the Presenter's layout pass place directly,
 *       matching SkiaPainter's coordinate convention.</li>
 *   <li><b>Text</b>: {@link MsdfTextRenderer} + {@link MsdfFontManager}'s
 *       default font; each render tick rebuilds {@link TextMesh}es from the
 *       scene's SceneText nodes.</li>
 *   <li><b>Container / Body</b>: structural recursion only — no visible
 *       primitive yet.  Container outlines and body placeholders need a
 *       hand-built rectangle mesh (vertex/index buffers + material); landing
 *       once the text path is proven.</li>
 *   <li><b>Resize</b>: fixed-size window for now.  GLFW_RESIZABLE=FALSE.
 *       Variable-viewport handling lands with the rectangle work.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p>Same shape as {@code SkiaPainter}: constructor opens the window on the
 * caller's thread; Filament's own threading is internal (Vulkan/GL backend
 * threads).  The {@link #paint(SceneNode)} call is invoked from the surface's
 * render thread; Filament's Engine API is documented as safe to call from a
 * single dedicated thread.
 */
@Log4j2
public final class FilamentPainter implements Painter {

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final float DEFAULT_FONT_SIZE = 16f;

    // Fallback colors used when the scene declares no Background / Foreground.
    // Real scenes set these via @Scene.Property on Background / Foreground;
    // values flow through here and override.
    private static final int COLOR_TEXT_DEFAULT = 0xFF000000; // ARGB: opaque black
    private static final float[] CLEAR_COLOR_DEFAULT = { 1f, 1f, 1f, 1f }; // white

    // ------ GLFW init guard (process-wide) ------

    private static final Object INIT_LOCK = new Object();
    private static boolean glfwInitialized = false;

    private static void ensureGlfwInit() {
        synchronized (INIT_LOCK) {
            if (glfwInitialized) return;
            GLFWErrorCallback.createPrint(System.err).set();
            // Filament's Vulkan backend on Linux only supports X11 surfaces;
            // force X11 (works under Wayland via XWayland).
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
            if (!glfwInit()) {
                throw new IllegalStateException("Failed to initialize GLFW");
            }
            glfwInitialized = true;
        }
    }

    // ------ Per-painter state ------

    private final long window;
    private final long nativeWindow;
    private final int width;
    private final int height;
    private final dev.everydaythings.graph.scene.Viewport viewport;

    // Created lazily on the render thread (first paint).  Filament's Engine
    // API requires every call to come from the thread that created the
    // Engine; constructing here on the caller's thread and then painting on
    // the render thread trips "this thread has not been adopted".
    private Engine engine;
    private SwapChain swapChain;
    private Renderer renderer;
    private Scene scene;
    private View view;
    private Camera camera;
    private MsdfTextRenderer textRenderer;
    private MsdfFontManager fontManager;
    private FilamentFontMetrics fontMetrics;
    private Renderer.ClearOptions clearOptions;

    // FontMetrics is reported pre-engine-init by the Presenter ctor; this
    // placeholder returns degenerate values until the real engine spins up,
    // at which point fontMetricsRef gets replaced.  In practice the placeholder
    // is read once before the first paint(), and never seen again — the
    // Presenter's layout pass runs per-tick, so the second tick gets the real
    // metrics.  Documented as a small known imperfection.
    private final FontMetrics placeholderFontMetrics = FontMetrics.NONE;

    /** Text meshes alive in the scene from the previous paint; torn down on next paint. */
    private final List<TextMesh> activeTextMeshes = new ArrayList<>();

    private volatile boolean closed = false;
    private volatile boolean engineReady = false;

    public FilamentPainter() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT, "Common Graph");
    }

    public FilamentPainter(int width, int height, String title) {
        ensureGlfwInit();
        this.width = width;
        this.height = height;
        this.viewport = new dev.everydaythings.graph.scene.Viewport(width, height);

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);

        long w = glfwCreateWindow(width, height, title, NULL, NULL);
        if (w == NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }
        this.window = w;
        this.nativeWindow = glfwGetX11Window(window);

        logger.info("FilamentPainter window created ({}x{}); Filament engine init deferred to render thread", width, height);
    }

    /**
     * Runs once, on the render thread, the first time {@link #paint(SceneNode)}
     * fires.  All Filament objects (Engine, SwapChain, Renderer, View, Scene,
     * Camera, MSDF text infra) are created here so they're owned by the
     * thread that uses them.
     */
    private void initEngineOnRenderThread() {
        Filament.init();
        Engine eng = new Engine.Builder().backend(Engine.Backend.VULKAN).build();
        if (eng == null) {
            eng = new Engine.Builder().backend(Engine.Backend.OPENGL).build();
            logger.info("FilamentPainter: Vulkan unavailable, using OpenGL backend.");
        } else {
            logger.info("FilamentPainter: using Vulkan backend.");
        }
        this.engine = eng;

        this.swapChain = engine.createSwapChainFromRawPointer(nativeWindow, 0);
        this.renderer  = engine.createRenderer();
        this.scene     = engine.createScene();
        this.view      = engine.createView();
        this.camera    = engine.createCamera(engine.getEntityManager().create());

        view.setScene(scene);
        view.setCamera(camera);
        view.setViewport(new Viewport(0, 0, width, height));

        // Pixel-coordinate ortho with Y pointing downward, matching Bounds /
        // Skia conventions: (0, 0) is top-left, (width, height) is bottom-right.
        camera.setProjection(Camera.Projection.ORTHO,
                /* left */   0.0,
                /* right */  width,
                /* bottom */ height,
                /* top */    0.0,
                /* near */  -1.0,
                /* far */    1.0);

        this.clearOptions = new Renderer.ClearOptions();
        clearOptions.clearColor = CLEAR_COLOR_DEFAULT.clone();
        clearOptions.clear = true;
        clearOptions.discard = true;
        renderer.setClearOptions(clearOptions);

        this.textRenderer = new MsdfTextRenderer(engine);
        this.fontManager  = new MsdfFontManager(engine);
        this.fontManager.loadDefaultFont();
        this.fontMetrics  = new FilamentFontMetrics(fontManager);
        this.engineReady = true;
    }

    // ==================================================================================
    // Painter SPI
    // ==================================================================================

    @Override
    public void paint(SceneNode tree) {
        if (closed) throw new IllegalStateException("FilamentPainter is closed");
        if (!engineReady) initEngineOnRenderThread();

        clearTextMeshes();
        if (tree != null) {
            // Pick up the root scene's Background as the clear color and
            // the root's Foreground as the inherited text color for the
            // entity walk.
            float[] bg = tree.readColor(VisualVocabulary.Background.KEY)
                    .map(FilamentPainter::rgbaFloats)
                    .orElse(CLEAR_COLOR_DEFAULT);
            clearOptions.clearColor = bg;
            renderer.setClearOptions(clearOptions);

            int textColor = tree.readColor(VisualVocabulary.Foreground.KEY)
                    .map(Color::toArgb)
                    .orElse(COLOR_TEXT_DEFAULT);
            buildEntities(tree, textColor);
        }

        if (renderer.beginFrame(swapChain, System.nanoTime())) {
            renderer.render(view);
            renderer.endFrame();
        }
        glfwPollEvents();
    }

    @Override
    public dev.everydaythings.graph.scene.Viewport viewport() {
        return viewport;
    }

    @Override
    public FontMetrics fontMetrics() {
        // Before the first paint() (when Presenter ctor reads this), the
        // engine hasn't been built yet, so we hand out the no-op sentinel.
        // The Presenter's layout pass runs per-tick, so by the time the
        // engine is ready the next tick reads the real metrics.
        return engineReady ? fontMetrics : placeholderFontMetrics;
    }

    @Override
    public Fidelity fidelity() {
        return Fidelity.RASTER_2D;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (engineReady) {
            clearTextMeshes();
            fontManager.destroy();
            textRenderer.destroy();
            engine.destroyRenderer(renderer);
            engine.destroyView(view);
            engine.destroyScene(scene);
            engine.destroyCameraComponent(camera.getEntity());
            engine.destroySwapChain(swapChain);
            engine.destroy();
        }
        glfwDestroyWindow(window);
        logger.info("FilamentPainter closed.");
        // Deliberately not glfwTerminate(): other painters may still run.
    }

    // ==================================================================================
    // Tree walk — produce Filament entities
    // ==================================================================================

    private void buildEntities(SceneNode node, int inheritedTextColor) {
        int textColor = node.readColor(VisualVocabulary.Foreground.KEY)
                .map(Color::toArgb)
                .orElse(inheritedTextColor);
        switch (node) {
            case SceneText      text      -> buildTextEntity(text, textColor);
            case SceneContainer container ->
                    container.children().forEach(c -> buildEntities(c, textColor));
            case SceneBody      body      -> { /* rectangle mesh — later slice */ }
            default                       -> { /* unknown node kind — skip */ }
        }
    }

    private void buildTextEntity(SceneText text, int colorArgb) {
        String content = text.readLiteral(SceneVocabulary.Text.KEY, String.class).orElse(null);
        if (content == null || content.isEmpty()) return;

        Bounds b = text.bounds();
        float fontSize = DEFAULT_FONT_SIZE;
        // FlatTextSurface places glyphs starting at origin along xAxis, with
        // yAxis as the "up" direction of the glyph (so positive yAxis is the
        // top of the letter).  Our ortho is Y-down, so to render right-side-up
        // we point yAxis at world -Y.  Origin is the baseline of the text:
        // for a SceneText whose bounds top-left is (b.x, b.y) and whose height
        // matches the line height, the baseline sits at (b.x, b.y + ascent).
        float ascent = (float) fontManager.defaultAtlas().ascent() * fontSize;
        float[] origin = { b.x(), b.y() + ascent, 0f };
        float[] xAxis  = { 1f,  0f, 0f };
        float[] yAxis  = { 0f, -1f, 0f };

        TextMesh mesh = new TextMeshBuilder(engine, textRenderer)
                .text(content)
                .fontManager(fontManager)
                .fontSize(fontSize)
                .color(colorArgb)
                .surface(new FlatTextSurface(origin, xAxis, yAxis))
                .build();
        scene.addEntity(mesh.entity());
        activeTextMeshes.add(mesh);
    }

    /** Convert a {@link Color} value into RGBA floats in 0..1 — Filament's clear-color shape. */
    private static float[] rgbaFloats(Color color) {
        return new float[] {
                (float) color.redDouble(),
                (float) color.greenDouble(),
                (float) color.blueDouble(),
                (float) color.alphaDouble()
        };
    }

    private void clearTextMeshes() {
        for (TextMesh mesh : activeTextMeshes) {
            scene.removeEntity(mesh.entity());
            mesh.destroy(engine);
        }
        activeTextMeshes.clear();
    }
}
