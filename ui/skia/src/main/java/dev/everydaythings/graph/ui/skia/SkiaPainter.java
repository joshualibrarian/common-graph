package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.quality.VisualVocabulary;
import dev.everydaythings.graph.scene.Bounds;
import dev.everydaythings.graph.scene.FontMetrics;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneBody;
import dev.everydaythings.graph.scene.SceneContainer;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;
import dev.everydaythings.graph.scene.SceneVocabulary;
import dev.everydaythings.graph.scene.Viewport;
import dev.everydaythings.graph.value.Color;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;
import lombok.extern.log4j.Log4j2;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_DECORATED;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * SkiaPainter — RASTER_2D painter backed by Skia (via Skija) on an
 * OpenGL-context GLFW window (via LWJGL).
 *
 * <h2>Threading</h2>
 *
 * <p>GLFW init + window creation run on the calling thread (whoever
 * constructs the painter — typically the thread that calls
 * {@link dev.everydaythings.graph.ui.AbstractSurface#open()}).  The GL
 * context is intentionally <i>not</i> made current at construction; the
 * first call to {@link #paint(SceneNode)} (which happens on the surface's
 * render thread) makes the context current there and constructs the
 * Skia {@link DirectContext} bound to that thread.  All subsequent GL +
 * Skia work runs on the render thread; the window creation thread no
 * longer touches GL.
 *
 * <p>This works fine on Linux and Windows; macOS additionally requires
 * GLFW to be initialized and polled from the main thread, which would
 * need a small restructuring (a dedicated GLFW thread, with paint
 * commands posted to it).  Not in scope for the first cut.
 *
 * <h2>Resize</h2>
 *
 * <p>A GLFW framebuffer-size callback writes new dimensions into an
 * {@link AtomicReference} viewport; the next paint observes the change
 * and recreates the Skia {@link Surface} from a fresh
 * {@link BackendRenderTarget}.  No event subscriptions outside this
 * painter; the surface SPI is unaware of resize.
 *
 * <h2>Drawing</h2>
 *
 * <p>Walks the positioned scene tree (every node carries a
 * {@link Bounds} from the {@link dev.everydaythings.graph.ui.Presenter
 * Presenter}'s layout pass) and emits Skia draw calls:
 *
 * <ul>
 *   <li>{@link SceneText}: text drawn at the bounds origin with baseline
 *       offset by the font's ascent, using the painter's
 *       {@link SkiaFontMetrics} font.</li>
 *   <li>{@link SceneContainer}: thin debug outline around its bounds;
 *       children painted recursively via the container's cached
 *       {@code children()} list (the same instances the layout pass
 *       populated bounds on).</li>
 *   <li>{@link SceneBody}: placeholder filled rectangle until the
 *       glyph / alt / image / svg / model fallback chain lands.</li>
 * </ul>
 *
 * <p>Colors and typography are first-cut defaults: black text on white,
 * light-gray container outlines.  Real values land when scene bindings
 * for {@code foreground} / {@code background} / {@code fontSize} feed
 * into the painter.
 */
@Log4j2
public final class SkiaPainter implements Painter {

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final float DEFAULT_FONT_SIZE = 16f;

    // Fallback colors used when the scene declares no Background / Foreground
    // binding.  Real scenes thread their own values through @Scene.Property
    // on Background / Foreground roles, which take precedence here.
    private static final int COLOR_BACKGROUND_DEFAULT = 0xFFFFFFFF; // white
    private static final int COLOR_TEXT_DEFAULT       = 0xFF000000; // black
    private static final int COLOR_CONTAINER_BORDER   = 0xFFD0D0D0; // light gray
    private static final int COLOR_BODY_PLACEHOLDER   = 0xFFE0E0E0; // lighter gray

    // ------ GLFW init guard ------

    private static final Object INIT_LOCK = new Object();
    private static boolean glfwInitialized = false;

    private static void ensureGlfwInit() {
        synchronized (INIT_LOCK) {
            if (glfwInitialized) return;
            GLFWErrorCallback.createPrint(System.err).set();
            if (!glfwInit()) {
                throw new IllegalStateException("Failed to initialize GLFW");
            }
            glfwInitialized = true;
        }
    }

    // ------ Per-painter state ------

    private final long window;
    private final SkiaFontMetrics fontMetrics;
    private final AtomicReference<Viewport> viewportRef;

    // Lazily initialized on the render thread inside the first paint().
    private DirectContext directContext;
    private BackendRenderTarget renderTarget;
    private Surface skiaSurface;

    private volatile boolean closed = false;

    /** Default-sized window titled "Common Graph". */
    public SkiaPainter() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT, "Common Graph");
    }

    public SkiaPainter(int initialWidth, int initialHeight, String title) {
        ensureGlfwInit();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);

        long w = glfwCreateWindow(initialWidth, initialHeight, title, NULL, NULL);
        if (w == NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }
        this.window = w;

        // Framebuffer size differs from window size on HiDPI displays; use it.
        int fbW, fbH;
        try (var stack = stackPush()) {
            IntBuffer wb = stack.mallocInt(1);
            IntBuffer hb = stack.mallocInt(1);
            glfwGetFramebufferSize(window, wb, hb);
            fbW = wb.get(0);
            fbH = hb.get(0);
        }
        this.viewportRef = new AtomicReference<>(new Viewport(fbW, fbH));

        // Async resize signal — written on the polling thread, observed on
        // the render thread at the next paint() boundary.
        glfwSetFramebufferSizeCallback(window, (win, newW, newH) -> {
            if (newW > 0 && newH > 0) {
                viewportRef.set(new Viewport(newW, newH));
            }
        });

        this.fontMetrics = new SkiaFontMetrics();
        logger.info("SkiaPainter window created ({}x{}, framebuffer {}x{})",
                initialWidth, initialHeight, fbW, fbH);
    }

    // ==================================================================================
    // Painter SPI
    // ==================================================================================

    @Override
    public void paint(SceneNode tree) {
        if (closed) throw new IllegalStateException("SkiaPainter is closed");
        if (skiaSurface == null) initRenderOnThread();

        // Resize observation: if the framebuffer changed since last paint,
        // rebuild the Skia surface against the new size.
        Viewport current = viewportRef.get();
        if (skiaSurface.getWidth() != (int) current.width()
                || skiaSurface.getHeight() != (int) current.height()) {
            recreateSkiaSurface(current);
        }

        Canvas canvas = skiaSurface.getCanvas();
        if (tree == null) {
            canvas.clear(COLOR_BACKGROUND_DEFAULT);
        } else {
            int bgColor   = readArgb(tree, VisualVocabulary.Background.KEY, COLOR_BACKGROUND_DEFAULT);
            int textColor = readArgb(tree, VisualVocabulary.Foreground.KEY, COLOR_TEXT_DEFAULT);
            canvas.clear(bgColor);
            paintNode(tree, canvas, textColor);
        }
        directContext.flush();
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    @Override
    public Viewport viewport() {
        return viewportRef.get();
    }

    @Override
    public FontMetrics fontMetrics() {
        return fontMetrics;
    }

    @Override
    public Fidelity fidelity() {
        return Fidelity.RASTER_2D;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (skiaSurface  != null) { skiaSurface.close();  skiaSurface  = null; }
        if (renderTarget != null) { renderTarget.close(); renderTarget = null; }
        if (directContext != null) { directContext.close(); directContext = null; }
        fontMetrics.close();
        glfwDestroyWindow(window);
        logger.info("SkiaPainter closed.");
        // Deliberately not calling glfwTerminate(): other Skia painters in
        // the same process may still be running.  GLFW stays alive for the
        // process lifetime; the JVM shutdown reclaims its resources.
    }

    // ==================================================================================
    // Render thread setup
    // ==================================================================================

    private void initRenderOnThread() {
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // vsync
        GL.createCapabilities();
        directContext = DirectContext.makeGL();
        recreateSkiaSurface(viewportRef.get());
    }

    private void recreateSkiaSurface(Viewport vp) {
        if (skiaSurface  != null) skiaSurface.close();
        if (renderTarget != null) renderTarget.close();

        int width  = Math.max(1, (int) vp.width());
        int height = Math.max(1, (int) vp.height());

        renderTarget = BackendRenderTarget.makeGL(
                width, height,
                /* samples */ 0,
                /* stencilBits */ 8,
                /* fbId — 0 = default framebuffer */ 0,
                GL_RGBA8);
        skiaSurface = Surface.makeFromBackendRenderTarget(
                directContext, renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorSpace.getSRGB());
    }

    // ==================================================================================
    // Tree walk
    // ==================================================================================

    private void paintNode(SceneNode node, Canvas canvas, int inheritedTextColor) {
        // Foreground cascades down: a node without its own Foreground binding
        // uses the inherited value (from the root scene, ultimately).
        int textColor = readArgb(node, VisualVocabulary.Foreground.KEY, inheritedTextColor);
        switch (node) {
            case SceneText      text      -> paintText(text, canvas, textColor);
            case SceneContainer container -> paintContainer(container, canvas, textColor);
            case SceneBody      body      -> paintBody(body, canvas);
            default                       -> { /* unknown node kind — skip */ }
        }
    }

    private void paintText(SceneText text, Canvas canvas, int colorArgb) {
        String content = text.readLiteral(SceneVocabulary.Text.KEY, String.class).orElse(null);
        if (content == null || content.isEmpty()) return;

        Bounds b = text.bounds();
        Font font = fontMetrics.fontFor(DEFAULT_FONT_SIZE);
        // Skia text origin is the baseline; the layout pass produced top-left
        // bounds, so add the font's negative-up ascent to land at baseline.
        float baseline = b.y() + (-font.getMetrics().getAscent());
        try (Paint paint = new Paint().setColor(colorArgb)) {
            canvas.drawString(content, b.x(), baseline, font, paint);
        }
    }

    private void paintContainer(SceneContainer container, Canvas canvas, int textColor) {
        Bounds b = container.bounds();
        // Debug outline.  Will become opt-in or controlled by background /
        // border bindings once those are wired.
        try (Paint outline = new Paint()
                .setColor(COLOR_CONTAINER_BORDER)
                .setMode(io.github.humbleui.skija.PaintMode.STROKE)
                .setStrokeWidth(1f)) {
            canvas.drawRect(Rect.makeXYWH(b.x(), b.y(), b.width(), b.height()), outline);
        }
        for (SceneNode child : container.children()) {
            paintNode(child, canvas, textColor);
        }
    }

    private void paintBody(SceneBody body, Canvas canvas) {
        Bounds b = body.bounds();
        try (Paint fill = new Paint().setColor(COLOR_BODY_PLACEHOLDER)) {
            canvas.drawRect(Rect.makeXYWH(b.x(), b.y(), b.width(), b.height()), fill);
        }
        // Real fallback chain (glyph / alt / image / svg / model) lands when
        // SceneBody declares those bindings consistently.
    }

    private static int readArgb(SceneNode node, String roleKey, int fallback) {
        return node.readColor(roleKey).map(Color::toArgb).orElse(fallback);
    }
}
