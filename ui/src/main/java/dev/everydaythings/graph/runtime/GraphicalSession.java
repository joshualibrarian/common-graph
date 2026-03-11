package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.ui.Stage;
import dev.everydaythings.graph.ui.WindowDragController;
import dev.everydaythings.graph.ui.WindowResizeController;
import dev.everydaythings.graph.ui.audio.OpenALAudio;
import dev.everydaythings.graph.ui.filament.CameraController;
import dev.everydaythings.graph.ui.filament.FilamentPane;
import dev.everydaythings.graph.ui.filament.FilamentPanelPainter;
import dev.everydaythings.graph.ui.filament.FilamentSurfacePainter;
import dev.everydaythings.graph.ui.filament.FilamentSpatialRenderer;
import dev.everydaythings.graph.ui.filament.FilamentWindow;
import dev.everydaythings.graph.ui.filament.SkiaPanel;
import dev.everydaythings.graph.ui.filament.MsdfFontManager;
import dev.everydaythings.graph.ui.filament.SkiaPanelPainter;
import dev.everydaythings.graph.ui.filament.SkiaSurfacePainter;
import dev.everydaythings.graph.ui.text.FontRegistry;
import dev.everydaythings.graph.expression.EvalInputSnapshot;
import dev.everydaythings.graph.ui.input.InputBindings;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.ui.scene.AnimationState;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.scene.spatial.CompiledBody;
import dev.everydaythings.graph.ui.scene.spatial.ItemSpace;
import dev.everydaythings.graph.ui.scene.spatial.SpatialCompiler;
import dev.everydaythings.graph.ui.scene.spatial.SpatialSchema;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.skia.FontCache;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.skia.PanelPainter;
import dev.everydaythings.graph.ui.skia.SkiaKeyAdapter;
import dev.everydaythings.graph.ui.skia.SkiaPainter;
import dev.everydaythings.graph.ui.skia.SkiaSurfaceRenderer;
import dev.everydaythings.graph.ui.skia.SkiaWindow;
import dev.everydaythings.graph.ui.style.Stylesheet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.everydaythings.graph.item.component.TickRegistry;

import java.util.List;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Unified graphical session supporting two renderer backends:
 * <ul>
 *   <li><b>Filament</b> — multi-pane rendering: orthographic UI pane (handle, tree, prompt)
 *       plus a perspective detail pane for 3D content</li>
 *   <li><b>Skia</b> — GPU-accelerated 2D rendering (fallback when Filament unavailable)</li>
 * </ul>
 *
 * <p>F9 switches between Filament and Skia windows at runtime.
 * Both renderers consume the same {@code @Surface} annotations via the shared
 * layout pipeline: {@code ItemModel.toSurface() → SkiaSurfaceRenderer → LayoutEngine → LayoutNode tree}.
 */
public class GraphicalSession extends Session {

    private static final Logger log = LogManager.getLogger(GraphicalSession.class);

    // ==================== Renderer Type ====================

    enum RendererType { FILAMENT, SKIA }

    private RendererType activeRenderer;
    private boolean filamentAvailable;
    private boolean pendingSwitch;

    // ==================== Active Stage ====================

    private Stage stage;

    // ==================== Filament Path ====================

    private FilamentWindow filamentWindow;
    private FilamentPane uiPane;    // ortho, full window — handle + tree + prompt + detail 2D
    private FilamentPane detailPane;    // perspective, detail region — 3D bodies when present
    private CameraController cameraController;
    private MsdfFontManager msdfFontManager;
    private FilamentSpatialRenderer sceneRenderer;
    private FilamentSurfacePainter detailPainter;  // for body+surface composition (elevated rendering)
    private PanelPainter panelPainter;   // for 2D panels in 3D space
    private OpenALAudio openAL;
    private Item sceneContextItem;
    private Link sceneContextLink;  // tracks which Link the 3D scene was built for
    private boolean sceneDirty;     // forces 3D scene rebuild on next rebuildScene()
    private float sceneFocusExtent = Float.NaN; // optional camera-fit extent (meters), e.g. chess board size
    private boolean flatMode;       // F10: show 2D Surface in detail region instead of 3D Body
    private SkiaPanel skiaPanel;     // Skia→texture→quad for flat mode (proper image/font rendering)

    // ==================== Skia Path ====================

    private SkiaWindow skiaWindow;
    private LayoutNode.BoxNode currentLayout;

    // ==================== Window Decoration ====================

    private WindowDragController dragController;
    private WindowResizeController resizeController;
    private FilamentSurfacePainter gripPainter;  // lightweight overlay for resize grip in Filament
    private double lastCursorX, lastCursorY;

    // ==================== Layout Root (for hit testing) ====================

    private LayoutNode.BoxNode lastLayoutRoot;
    private boolean filamentLayoutDirty;

    // ==================== Zoom ====================

    private static final float DEFAULT_FONT_SIZE = 15f;
    private static final float MIN_FONT_SIZE = 8f;
    private static final float MAX_FONT_SIZE = 48f;
    private static final float ZOOM_STEP = 1f;

    // ==================== Shared Resources (persist across window switches) ====================

    private FontRegistry fontRegistry;
    private FontCache fontCache;
    private LayoutEngine layoutEngine;
    private SkiaPainter painter;
    private SkiaKeyAdapter keyAdapter;
    private final AnimationState animationState = new AnimationState();
    private final dev.everydaythings.graph.ui.skia.ScrollState scrollState = new dev.everydaythings.graph.ui.skia.ScrollState();
    private ScheduledExecutorService liveTimer; // periodic repaint for live widgets (clock, etc.)
    private final TickRegistry tickRegistry = new TickRegistry();

    // ==================== Input ====================

    // ==================== Constructor ====================

    public GraphicalSession(LibrarianHandle librarian, Link context, RenderMode initialMode) {
        super(librarian, context);
        this.activeRenderer = (initialMode == RenderMode.SPATIAL)
                ? RendererType.FILAMENT : RendererType.SKIA;
    }

    // ==================== Lifecycle ====================

    @Override
    public int run() {
        log.info("Starting GraphicalSession (initial renderer: {})", activeRenderer);

        Item ctx = contextItem()
                .or(() -> librarian.get(librarian.iid()))
                .orElse(null);
        if (ctx == null) {
            System.err.println("No context item available.");
            return 1;
        }
        sceneContextItem = ctx;

        try {
            // Initialize shared resources (persist across window switches)
            fontRegistry = FontRegistry.shared();
            fontCache = new FontCache(fontRegistry);
            layoutEngine = new LayoutEngine(fontCache);
            painter = new SkiaPainter(fontCache);
            keyAdapter = new SkiaKeyAdapter();
            resizeController = new WindowResizeController(20f);

            // Check Filament availability
            filamentAvailable = checkFilamentAvailable();

            // Start the requested renderer (fallback to Skia if Filament unavailable)
            if (activeRenderer == RendererType.FILAMENT && filamentAvailable) {
                initFilament(ctx);
                // MSDF renders smaller than Skia at the same nominal size — apply
                // the Filament default so both renderers produce visually similar text
                applyFontSize(DEFAULT_FONT_SIZE);
            } else {
                if (activeRenderer == RendererType.FILAMENT) {
                    log.warn("Filament unavailable, falling back to Skia");
                }
                activeRenderer = RendererType.SKIA;
                initSkia(ctx);
            }
            stage.show();

            // Initialize input
            initializeEvalInput();

            // Initial layout
            rebuildLayout();

            // Run the event loop — loops when F9 triggers a window switch
            runEventLoop();

            return 0;
        } catch (Exception e) {
            log.error("Graphical session failed", e);
            return 1;
        } finally {
            cleanup();
        }
    }

    /**
     * Run the event loop, handling F9 window switches.
     * Each window's runLoop() blocks until the window closes.
     * On F9, we close the current window, create the other, and loop.
     */
    private void runEventLoop() {
        while (stage != null) {
            stage.runLoop();

            if (pendingSwitch) {
                pendingSwitch = false;
                performSwitch();
                rebuildLayout();
            } else {
                break; // Normal window close
            }
        }
    }

    @Override
    protected void render() {
        rebuildLayout();
        requestRepaint();
    }

    @Override
    protected void output(String message) {
        log.info(message);
    }

    @Override
    public void close() {
        cleanup();
        super.close();
    }

    // ==================== Filament Initialization ====================

    private boolean checkFilamentAvailable() {
        try {
            Class.forName("dev.everydaythings.filament.Filament");
            return true;
        } catch (Throwable t) {
            log.info("Filament not available: {}", t.getMessage());
            return false;
        }
    }

    private void initFilament(Item ctx) {
        cameraController = new CameraController();

        // Initialize spatial audio
        openAL = new OpenALAudio();
        if (!openAL.init()) {
            log.warn("Audio unavailable — continuing without sound");
        }

        // Create Filament window
        filamentWindow = new FilamentWindow();
        filamentWindow.init("Common Graph - " + ctx.displayToken());
        stage = filamentWindow;

        // Create panes
        uiPane = filamentWindow.createPane(false); // ortho
        uiPane.configureOrtho(uiPane.viewportAspect());

        detailPane = filamentWindow.createPane(true);  // perspective
        detailPane.clearFullWindow(); // viewport managed by updateDetailPaneViewport
        detailPane.configurePerspective();

        String msdfMode = System.getProperty("graph.msdf.mode", "auto");
        log.info("MSDF init mode: {}", msdfMode);

        // Create UI + panel painters. Prefer MSDF, fall back to Skia-in-Filament
        // when MSDF fonts are unavailable on this platform.
        try {
            msdfFontManager = new MsdfFontManager(filamentWindow.engine(), fontRegistry);
            boolean msdfReady = msdfFontManager.hasUsableFonts();
            if (msdfReady) {
                FilamentSurfacePainter uiPainter = new FilamentSurfacePainter(
                        filamentWindow.engine(), uiPane.scene(), msdfFontManager);
                uiPane.painter(uiPainter);

                // Panel painter for 2D surfaces in 3D space (uses detail pane's scene)
                panelPainter = new FilamentPanelPainter(
                        filamentWindow.engine(), detailPane.scene(), msdfFontManager, librarian);
                log.info("MSDF direct rendering initialized");
            } else {
                log.warn("MSDF unavailable (registered fonts: {}); using Skia texture fallback for Filament UI panels",
                        msdfFontManager.registeredFontCount());
                msdfFontManager = null;
                uiPane.painter(new SkiaSurfacePainter(
                        filamentWindow.engine(), uiPane.scene(), painter, fontCache));
                panelPainter = new SkiaPanelPainter(
                        filamentWindow.engine(), detailPane.scene(), painter, fontCache);
            }
        } catch (Throwable t) {
            log.warn("MSDF initialization failed; using Skia texture fallback for Filament UI panels", t);
            msdfFontManager = null;
            uiPane.painter(new SkiaSurfacePainter(
                    filamentWindow.engine(), uiPane.scene(), painter, fontCache));
            panelPainter = new SkiaPanelPainter(
                    filamentWindow.engine(), detailPane.scene(), painter, fontCache);
        }

        // Window chrome controllers (borderless window — Stage IS WindowOps)
        dragController = new WindowDragController(32f, stage);
        resizeController.setStage(stage);
        resizeController.onHoverChanged(this::repaintGrip);

        // Lightweight overlay painter for resize grip (only available with MSDF path)
        if (msdfFontManager != null) {
            gripPainter = new FilamentSurfacePainter(
                    filamentWindow.engine(), uiPane.scene(), msdfFontManager);
        } else {
            gripPainter = null;
        }

        // Set up input callbacks
        setupFilamentInput();

        // Per-frame update: deferred layout rebuild + camera + audio
        filamentWindow.onBeforeRender(() -> {
            try {
                // Rebuild layout if the resize callback marked it dirty.
                // This runs after glfwPollEvents() has settled, so stage.width()/height()
                // are stable cached values — no mid-frame drift.
                if (filamentLayoutDirty) {
                    uiPane.configureOrtho(uiPane.viewportAspect());
                    rebuildLayout();
                    filamentLayoutDirty = false;
                }

                if (!flatMode) {
                    double dt = filamentWindow.deltaTime();
                    cameraController.update(dt);
                    double aspect = detailPane != null
                            ? detailPane.viewportAspect()
                            : (double) filamentWindow.width() / filamentWindow.height();
                    cameraController.applyToCamera(detailPane.camera(), aspect);

                    // Sync OpenAL listener to camera position
                    if (openAL != null && openAL.isInitialized() && !cameraController.isOrthographic()) {
                        double[] eye = cameraController.eyePosition();
                        double[] look = cameraController.lookDirection();
                        openAL.updateListener(eye[0], eye[1], eye[2],
                                look[0], look[1], look[2], 0, 1, 0);
                    }
                }
            } catch (Exception e) {
                log.error("onBeforeRender failed", e);
            }
        });

        filamentWindow.onResize((w, h) -> {
            filamentLayoutDirty = true;
        });

        log.info("Filament initialized with UI + detail panes");

        startLiveTimer();
    }


    // ==================== Skia Initialization ====================

    private void initSkia(Item ctx) {
        skiaWindow = new SkiaWindow();
        skiaWindow.init("Common Graph - " + ctx.displayToken());
        stage = skiaWindow;

        // Window chrome controllers (borderless window — Stage IS WindowOps)
        dragController = new WindowDragController(32f, stage);
        resizeController.setStage(stage);
        resizeController.onHoverChanged(() -> requestRepaint());

        skiaWindow.onPaint(canvas -> {
            // Advance animations
            double dt = skiaWindow.deltaTime();
            if (dt > 0 && animationState.isAnimating()) {
                animationState.update(dt);
                if (currentLayout != null) {
                    applyAnimations(currentLayout);
                }
            }

            if (currentLayout != null) {
                painter.paint(canvas, currentLayout);
            }

            // Resize grip overlay
            paintResizeGripSkia(canvas);

            if (animationState.isAnimating()) {
                skiaWindow.requestPaint();
            }
        });

        skiaWindow.onKey((key, scancode, action, mods) -> {
            KeyChord chord = keyAdapter.fromNative(key, scancode, action, mods);
            if (chord != null) {
                handleKeyChord(chord);
            }
        });

        skiaWindow.onChar(codepoint -> {
            KeyChord chord = keyAdapter.fromChar(codepoint);
            if (chord != null) {
                handleKeyChord(chord);
            }
        });

        skiaWindow.onMouseButton((button, action, mods) -> {
            if (resizeController.onMouseButton(button, action, lastCursorX, lastCursorY)) {
                return;
            }
            if (dragController.onMouseButton(button, action, lastCursorX, lastCursorY)) {
                return;
            }
            if (action == GLFW_RELEASE) {
                // Cursor and layout are both in logical pixels; no DPI scaling needed for hit-testing.
                handleMouseButtonRelease(button, lastCursorX, lastCursorY, 1.0f);
            }
        });

        skiaWindow.onCursorPos((x, y) -> {
            lastCursorX = x;
            lastCursorY = y;
            resizeController.onCursorPos(x, y);
            dragController.onCursorPos(x, y);
            handleMouseEvent("hover", x, y, 1.0f);
        });

        skiaWindow.onScroll((xOffset, yOffset) -> {
            handleMouseScroll(lastCursorX, lastCursorY, 1.0f, xOffset, yOffset);
        });

        skiaWindow.onResize((w, h) -> rebuildLayout());

        startLiveTimer();
    }

    // ==================== F9: Window Switching ====================

    /**
     * Request a switch between Filament and Skia renderers.
     * Sets a flag and closes the current window; the switch is performed
     * after the render loop exits.
     */
    private void requestSwitch() {
        pendingSwitch = true;
        if (stage != null) {
            stage.requestClose();
        }
    }

    /**
     * Toggle flat mode: 2D Surface in the detail region instead of 3D Body.
     * When flat, the uiPane renders the full 2D layout and the detailPane is hidden.
     */
    private void toggleFlatMode() {
        flatMode = !flatMode;
        if (flatMode) {
            // Tear down 3D scene — uiPane's 2D rendering shows through
            if (sceneRenderer != null) {
                sceneRenderer.destroy();
                sceneRenderer = null;
            }
            if (panelPainter != null) panelPainter.clear();
            if (detailPainter != null) detailPainter.clear();
            hideDetailPane();
            sceneContextLink = null; // force rebuild when returning to 3D
        } else {
            sceneDirty = true; // force 3D scene rebuild on next layout
        }
        filamentLayoutDirty = true;
        log.info("Flat mode {}", flatMode ? "enabled" : "disabled");
    }

    /**
     * Perform the actual window switch after the render loop exits.
     */
    private void performSwitch() {
        if (activeRenderer == RendererType.FILAMENT) {
            switchToSkia();
        } else {
            switchToFilament();
        }
    }

    private void switchToSkia() {
        // Save window geometry from current stage
        int[] pos = stage.getWindowPos();
        int w = stage.width();
        int h = stage.height();

        // Destroy Filament
        cleanupFilament();

        // Clear stale layout — Skia will rebuild on first paint
        lastLayoutRoot = null;
        currentLayout = null;

        // Create Skia (window starts hidden)
        activeRenderer = RendererType.SKIA;
        initSkia(sceneContextItem);
        applyFontSize(DEFAULT_FONT_SIZE);
        stage.setWindowPos(pos[0], pos[1]);
        stage.setWindowSize(w, h);
        stage.show();

        // Force immediate layout rebuild for the new Skia window
        rebuildLayout();

        log.info("Switched to Skia renderer");
    }

    private void switchToFilament() {
        if (!filamentAvailable) {
            log.warn("Cannot switch to Filament — not available");
            // Re-create Skia window since we closed the old one
            activeRenderer = RendererType.SKIA;
            initSkia(sceneContextItem);
            stage.show();
            return;
        }

        // Save window geometry from current stage
        int[] pos = stage.getWindowPos();
        int w = stage.width();
        int h = stage.height();

        // Destroy Skia
        skiaWindow.destroy();
        skiaWindow = null;

        // Create Filament (window starts hidden)
        activeRenderer = RendererType.FILAMENT;
        initFilament(sceneContextItem);

        applyFontSize(DEFAULT_FONT_SIZE);
        stage.setWindowPos(pos[0], pos[1]);
        stage.setWindowSize(w, h);
        stage.show();

        // Force full rebuild immediately — new engine, panes, and painters need fresh content
        sceneDirty = true;
        sceneContextLink = null;
        lastLayoutRoot = null;
        rebuildLayout();
        requestRepaint();

        log.info("Switched to Filament renderer");
    }

    // ==================== Filament Input Setup ====================

    private void setupFilamentInput() {
        filamentWindow.onKey((key, scancode, action, mods) -> {
            // Track raw key state for camera movement in detail pane.
            // Tab toggles ORBIT↔FLY but only when cursor is over the detail pane.
            boolean cursorInDetail = isCursorInDetailPane(lastCursorX, lastCursorY);
            if (key != GLFW_KEY_TAB || cursorInDetail) {
                if (cameraController.onKeyRaw(key, action)) {
                    return; // consumed (mode toggle)
                }
            }

            // F9: switch renderer
            if (key == GLFW_KEY_F9 && action == GLFW_PRESS) {
                requestSwitch();
                return;
            }

            // F10: toggle flat mode (2D Surface in detail region instead of 3D Body)
            if (key == GLFW_KEY_F10 && action == GLFW_PRESS) {
                toggleFlatMode();
                return;
            }

            // All keys to session input
            KeyChord chord = keyAdapter.fromNative(key, scancode, action, mods);
            if (chord != null) {
                handleKeyChord(chord);
            }
        });

        filamentWindow.onChar(codepoint -> {
            KeyChord chord = keyAdapter.fromChar(codepoint);
            if (chord != null) {
                handleKeyChord(chord);
            }
        });

        // Mouse: resize → drag → hit-test/camera
        filamentWindow.onMouseButton((button, action, mods) -> {
            if (resizeController.onMouseButton(button, action, lastCursorX, lastCursorY)) {
                return;
            }
            if (dragController != null && dragController.onMouseButton(button, action, lastCursorX, lastCursorY)) {
                return;
            }

            if (isCursorInDetailPane(lastCursorX, lastCursorY)) {
                // Detail pane: camera control
                cameraController.onMouseButton(button, action, mods);
            } else if (action == GLFW_RELEASE) {
                // UI region: hit-test on release
                handleMouseButtonRelease(button, lastCursorX, lastCursorY, filamentInputScale());
            }
        });

        filamentWindow.onCursorPos((x, y) -> {
            lastCursorX = x;
            lastCursorY = y;
            if (resizeController.onCursorPos(x, y)) {
                return;
            }
            if (dragController != null && dragController.onCursorPos(x, y)) {
                return;
            }
            if (isCursorInDetailPane(x, y)) {
                cameraController.onCursorPos(x, y);
            } else {
                handleMouseEvent("hover", x, y, filamentInputScale());
            }
        });

        filamentWindow.onScroll((xOffset, yOffset) -> {
            if (isCursorInDetailPane(lastCursorX, lastCursorY)) {
                cameraController.onScroll(xOffset, yOffset);
            } else {
                handleMouseScroll(lastCursorX, lastCursorY, filamentInputScale(), xOffset, yOffset);
            }
        });
    }

    private float filamentInputScale() {
        if (activeRenderer == RendererType.FILAMENT
                && uiPane != null
                && uiPane.painter() instanceof SkiaSurfacePainter) {
            return 1.0f;
        }
        return filamentUiDpr();
    }

    private float filamentUiDpr() {
        if (activeRenderer != RendererType.FILAMENT || stage == null || uiPane == null) {
            return 1.0f;
        }
        float logicalW = Math.max(1f, stage.width());
        float logicalH = Math.max(1f, stage.height());
        float sx = uiPane.viewportWidth() > 0 ? uiPane.viewportWidth() / logicalW : 1.0f;
        float sy = uiPane.viewportHeight() > 0 ? uiPane.viewportHeight() / logicalH : 1.0f;
        return Math.max(1.0f, Math.max(sx, sy));
    }

    /**
     * Check if the cursor is within the detail pane's viewport region.
     */
    private boolean isCursorInDetailPane(double cursorX, double cursorY) {
        // In flat mode, all input goes to 2D hit-testing, not camera
        if (flatMode) return false;
        // Detail pane viewport is stored in window coordinates (top-left origin)
        // For now, if there's no 3D content, the detail pane is inactive
        if (detailPane == null || sceneRenderer == null) return false;

        // Use the layout tree to determine the detail region bounds
        if (lastLayoutRoot == null) return false;

        LayoutNode.BoxNode detailNode = findNodeById(lastLayoutRoot, "detail");
        if (detailNode == null) return false;

        float scale = filamentInputScale();
        float x = (float) (cursorX * scale);
        float y = (float) (cursorY * scale);
        return x >= detailNode.x() && x < detailNode.x() + detailNode.width()
                && y >= detailNode.y() && y < detailNode.y() + detailNode.height();
    }

    // ==================== Key Handling ====================

    private void handleKeyChord(KeyChord chord) {
        // F9 in Skia mode — switch to Filament
        if (activeRenderer == RendererType.SKIA && chord.isKey(SpecialKey.F9)) {
            requestSwitch();
            return;
        }

        // When completion popup is visible, plain input navigation keys should
        // be handled by EvalInput before tree/session navigation.
        if (evalInput != null
                && evalInput.snapshot().hasVisibleCompletions()
                && !chord.alt() && !chord.ctrl() && !chord.shift()
                && (chord.isKey(SpecialKey.UP)
                    || chord.isKey(SpecialKey.DOWN)
                    || chord.isKey(SpecialKey.TAB)
                    || chord.isKey(SpecialKey.ENTER)
                    || chord.isKey(SpecialKey.ESCAPE))) {
            dispatchToEvalInput(chord);
            rebuildLayout();
            requestRepaint();
            return;
        }

        // Zoom: Ctrl+= (zoom in), Ctrl+- (zoom out), Ctrl+0 (reset)
        if (chord.ctrl() && !chord.alt() && !chord.shift()) {
            if (chord.isChar('=')) { zoomIn(); return; }
            if (chord.isChar('-')) { zoomOut(); return; }
            if (chord.isChar('0')) { zoomReset(); return; }
        }

        if (itemModel() != null && itemModel().handleKey(chord)) {
            rebuildLayout();
            requestRepaint();
            return;
        }
        if (handleKey(chord)) {
            rebuildLayout();
            requestRepaint();
            return;
        }
        dispatchToEvalInput(chord);
    }

    // ==================== Zoom ====================

    private void zoomIn() {
        float current = currentBaseFontSize();
        float next = Math.min(current + ZOOM_STEP, MAX_FONT_SIZE);
        applyFontSize(next);
    }

    private void zoomOut() {
        float current = currentBaseFontSize();
        float next = Math.max(current - ZOOM_STEP, MIN_FONT_SIZE);
        applyFontSize(next);
    }

    private void zoomReset() {
        applyFontSize(DEFAULT_FONT_SIZE);
    }

    private float currentBaseFontSize() {
        if (activeRenderer == RendererType.FILAMENT && msdfFontManager != null) {
            return msdfFontManager.baseFontSize();
        }
        return fontCache.baseFontSize();
    }

    private void applyFontSize(float size) {
        fontCache.setBaseFontSize(size);
        if (msdfFontManager != null) {
            msdfFontManager.setBaseFontSize(size);
        }
        rebuildLayout();
        requestRepaint();
    }

    private void requestRepaint() {
        if (stage != null) {
            stage.requestPaint();
        }
    }

    /**
     * Start a 1-second periodic timer that ticks live components and repaints.
     * Enables live widgets (clock, timers, notifications) to update.
     */
    private void startLiveTimer() {
        stopLiveTimer();

        // Rebuild tick registry from current context
        contextItem().ifPresent(item -> tickRegistry.rebuild(item.content()));

        liveTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "live-widget-timer");
            t.setDaemon(true);
            return t;
        });
        liveTimer.scheduleAtFixedRate(() -> {
            try {
                boolean ticked = tickRegistry.tickAll();
                if (ticked) {
                    if (activeRenderer == RendererType.FILAMENT) {
                        filamentLayoutDirty = true;
                        requestRepaint();
                    } else {
                        render();
                    }
                }
            } catch (Exception e) {
                log.trace("Live timer tick failed", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopLiveTimer() {
        if (liveTimer != null) {
            liveTimer.shutdown();
            liveTimer = null;
        }
        tickRegistry.clear();
    }

    @Override
    protected void onContextComponentsChanged(Item item) {
        tickRegistry.rebuild(item.content());
    }

    @Override
    protected void onInputChanged(EvalInputSnapshot snapshot) {
        rebuildLayout();
        requestRepaint();
    }

    @Override
    protected void onInputDispatched(Eval.EvalResult result) {
        sceneDirty = true;
        rebuildLayout();
        requestRepaint();
    }

    // ==================== Mouse / Touchpad → Hit-Test → Event ====================

    private static final long DOUBLE_CLICK_THRESHOLD_NS = 400_000_000L; // 400ms
    private long lastClickTimeNanos;
    private double lastClickX, lastClickY;

    /**
     * Dispatch a mouse event to the 2D layout tree.
     *
     * @param eventType  DSL event name: "click", "rightclick", "middleclick", "doubleClick", "scroll", "hover"
     * @param cursorX    cursor X in window coordinates
     * @param cursorY    cursor Y in window coordinates
     * @param dpi        DPI scale factor (Skia uses window DPI, Filament uses 1.0)
     */
    private void handleMouseEvent(String eventType, double cursorX, double cursorY, float dpi) {
        if (lastLayoutRoot == null) return;

        float x = (float) (cursorX * dpi);
        float y = (float) (cursorY * dpi);

        LayoutNode.PendingEvent hit = LayoutNode.hitTest(lastLayoutRoot, x, y, eventType);
        if (hit == null) return;

        log.debug("Hit: event={} action={} target={}", eventType, hit.action(), hit.target());

        if (itemModel() != null && itemModel().handleEvent(hit.action(), hit.target())) {
            sceneDirty = true;
            rebuildLayout();
            requestRepaint();
            return;
        }

        contextItem().ifPresent(item -> {
            var result = item.dispatch(hit.action(), List.of(hit.target()));
            if (result != null && result.success()) {
                sceneDirty = true;
                rebuildLayout();
                requestRepaint();
            }
        });
    }

    /**
     * Handle a mouse button release — detects double-click, then dispatches the appropriate event.
     */
    private void handleMouseButtonRelease(int button, double cursorX, double cursorY, float dpi) {
        String eventType = switch (button) {
            case GLFW_MOUSE_BUTTON_LEFT -> "click";
            case GLFW_MOUSE_BUTTON_RIGHT -> "rightclick";
            case GLFW_MOUSE_BUTTON_MIDDLE -> "middleclick";
            default -> null;
        };
        if (eventType == null) return;

        // Double-click detection (left button only)
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            long now = System.nanoTime();
            double dx = cursorX - lastClickX;
            double dy = cursorY - lastClickY;
            boolean closeEnough = dx * dx + dy * dy < 25; // 5px tolerance
            if (now - lastClickTimeNanos < DOUBLE_CLICK_THRESHOLD_NS && closeEnough) {
                handleMouseEvent("doubleClick", cursorX, cursorY, dpi);
                lastClickTimeNanos = 0; // reset so triple-click doesn't fire another double
                return;
            }
            lastClickTimeNanos = now;
            lastClickX = cursorX;
            lastClickY = cursorY;
        }

        handleMouseEvent(eventType, cursorX, cursorY, dpi);
    }

    /**
     * Handle scroll input over the 2D layout tree.
     */
    private void handleMouseScroll(double cursorX, double cursorY, float dpi,
                                   double xOffset, double yOffset) {
        if (lastLayoutRoot == null) return;

        float x = (float) (cursorX * dpi);
        float y = (float) (cursorY * dpi);

        // Try explicit @Scene.On(event="scroll") handlers first
        LayoutNode.PendingEvent hit = LayoutNode.hitTest(lastLayoutRoot, x, y, "scroll");
        if (hit != null) {
            log.debug("Scroll hit: action={} target={} offset=({}, {})",
                    hit.action(), hit.target(), xOffset, yOffset);

            String scrollTarget = hit.target().isEmpty()
                    ? String.format("%.1f,%.1f", xOffset, yOffset)
                    : hit.target();

            if (itemModel() != null && itemModel().handleEvent(hit.action(), scrollTarget)) {
                sceneDirty = true;
                rebuildLayout();
                requestRepaint();
                return;
            }

            contextItem().ifPresent(item -> {
                var result = item.dispatch(hit.action(), List.of(scrollTarget));
                if (result != null && result.success()) {
                    sceneDirty = true;
                    rebuildLayout();
                    requestRepaint();
                }
            });
            return;
        }

        // Fallback: scroll the nearest scroll container under the cursor
        LayoutNode.BoxNode scrollBox = findScrollContainer(lastLayoutRoot, x, y);
        if (scrollBox != null && scrollBox.id() != null) {
            float delta = (float) (-yOffset * SCROLL_LINE_HEIGHT);
            float maxOffset = Math.max(0, scrollBox.contentHeight() - scrollBox.height());
            scrollState.scrollBy(scrollBox.id(), delta, maxOffset);
            repaintOnly();
            requestRepaint();
        }
    }

    // ==================== Layout / Rendering ====================

    /**
     * Rebuild the layout tree via the DSL pipeline, then paint to the active renderer.
     *
     * <p>Pipeline: ItemModel.toSurface() → SkiaSurfaceRenderer → LayoutEngine → paint
     */
    private void rebuildLayout() {
        if (itemModel() == null) return;

        try {
            SurfaceSchema itemSurface = itemModel().toSurface();
            if (itemSurface == null) return;

            boolean filamentSkiaFallback = activeRenderer == RendererType.FILAMENT
                    && uiPane != null
                    && uiPane.painter() instanceof SkiaSurfacePainter;
            float w, h, dpr;
            if (activeRenderer == RendererType.FILAMENT) {
                w = stage != null ? stage.width() : 800;
                h = stage != null ? stage.height() : 600;
                if (filamentSkiaFallback && stage != null && stage.width() > 0 && stage.height() > 0) {
                    float vpW = uiPane != null && uiPane.viewportWidth() > 0 ? uiPane.viewportWidth() : w;
                    float vpH = uiPane != null && uiPane.viewportHeight() > 0 ? uiPane.viewportHeight() : h;
                    float sx = vpW / (float) stage.width();
                    float sy = vpH / (float) stage.height();
                    dpr = Math.max(1.0f, (sx + sy) * 0.5f);
                } else {
                    dpr = 1.0f;
                }
            } else if (stage != null) {
                w = stage.width();
                h = stage.height();
                dpr = (stage instanceof SkiaWindow sw) ? sw.dpi() : 1.0f;
            } else {
                w = 800;
                h = 600;
                dpr = 1.0f;
            }

            // Build RenderMetrics from actual font measurements
            boolean useMsdf = activeRenderer == RendererType.FILAMENT
                    && msdfFontManager != null
                    && !filamentSkiaFallback;
            RenderMetrics metrics = useMsdf ? msdfFontManager.buildMetrics() : fontCache.buildMetrics();
            float baseFontSize = useMsdf ? msdfFontManager.baseFontSize() : fontCache.baseFontSize();

            var ctx = RenderContext.builder()
                    .renderer(RenderContext.RENDERER_SKIA)
                    .breakpoint(RenderContext.BREAKPOINT_LG)
                    .viewportWidth(w)
                    .viewportHeight(h)
                    .devicePixelRatio(dpr)
                    .dpi(96 * dpr)
                    .addCapability("color")
                    .addCapability("mouse")
                    .addCapability("images")
                    .librarian(librarian)
                    .renderMetrics(metrics)
                    .baseFontSize(baseFontSize)
                    .build();
            SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer(ctx);
            itemSurface.render(renderer);
            LayoutNode.BoxNode tree = renderer.result();

            LayoutEngine.TextMeasurer measurer = useMsdf ? msdfFontManager : fontCache;
            float bfs = useMsdf ? msdfFontManager.baseFontSize() : fontCache.baseFontSize();
            var engine = new LayoutEngine(measurer, ctx, Stylesheet.fromClasspath(), bfs);
            engine.layout(tree, w, h);

            // Apply scroll state and inject scrollbar overlays
            applyScrollState(tree);

            // Process animations
            applyAnimations(tree);

            // Stash for hit testing
            lastLayoutRoot = tree;

            // Paint to the active renderer
            if (activeRenderer == RendererType.FILAMENT && uiPane != null && uiPane.painter() != null) {
                // Update detail viewport from layout tree (needed by rebuildScene)
                updateDetailPaneViewport((int) w, (int) h);

                // Build 3D scene first to determine if detail region has 3D content.
                // This avoids painting 2D content that would be immediately overwritten by 3D.
                if (!flatMode) {
                    rebuildScene();
                } else {
                    hideDetailPane();
                }
                boolean has3D = !flatMode && sceneRenderer != null;
                boolean skipDetailInUi = has3D && !(uiPane.painter() instanceof SkiaSurfacePainter);

                // UI pane: paint 2D layout, skipping detail region if 3D is active there
                uiPane.painter().clear();
                uiPane.painter().configureForLayout(w, h, 2.0f, 1.0f);
                uiPane.painter().skipId(skipDetailInUi ? "detail" : null);
                uiPane.painter().paint(tree, 0f);
                uiPane.painter().skipId(null);

                // Resize grip overlay
                repaintGrip();
            } else {
                // Skia path
                currentLayout = tree;
            }
        } catch (Exception e) {
            log.error("Layout failed", e);
        }
    }

    /**
     * Repaint the existing layout tree without rebuilding from the DSL pipeline.
     * Used for scroll offset changes where only paint positions change, not layout structure.
     * Avoids the expensive DSL→LayoutNode→LayoutEngine cycle and reduces Filament resource churn.
     */
    private void repaintOnly() {
        if (lastLayoutRoot == null) return;

        try {
            // Re-apply scroll state (updates offsets and scrollbar positions)
            applyScrollState(lastLayoutRoot);

            // Repaint
            if (activeRenderer == RendererType.FILAMENT && uiPane != null && uiPane.painter() != null) {
                float w = stage != null ? stage.width() : 800;
                float h = stage != null ? stage.height() : 600;
                boolean has3D = !flatMode && sceneRenderer != null;
                boolean skipDetailInUi = has3D && !(uiPane.painter() instanceof SkiaSurfacePainter);

                uiPane.painter().clear();
                uiPane.painter().configureForLayout(w, h, 2.0f, 1.0f);
                uiPane.painter().skipId(skipDetailInUi ? "detail" : null);
                uiPane.painter().paint(lastLayoutRoot, 0f);
                uiPane.painter().skipId(null);
                repaintGrip();
            } else {
                // Skia path — just needs a repaint, layout tree is already currentLayout
                currentLayout = lastLayoutRoot;
            }
        } catch (Exception e) {
            log.error("Scroll repaint failed", e);
        }
    }

    // ==================== Scroll Support ====================

    private static final float SCROLL_LINE_HEIGHT = 40f;
    private static final float SCROLLBAR_WIDTH = 8f;
    private static final float SCROLLBAR_MIN_THUMB_HEIGHT = 20f;

    /**
     * Walk the layout tree, apply persisted scroll offsets, and inject scrollbar overlays.
     */
    private void applyScrollState(LayoutNode node) {
        if (node instanceof LayoutNode.BoxNode box) {
            if (box.isScrollContainer() && box.id() != null) {
                float viewportH = box.height();
                float contentH = box.contentHeight();
                float maxOffset = Math.max(0, contentH - viewportH);

                // Apply persisted scroll offset
                float offset = scrollState.getScrollY(box.id());
                offset = Math.max(0, Math.min(maxOffset, offset));
                scrollState.setScrollY(box.id(), offset);
                box.scrollOffsetY(offset);

                // Inject scrollbar overlay shapes
                injectScrollbars(box, viewportH, contentH, offset, maxOffset);
            }
            for (LayoutNode child : box.children()) {
                applyScrollState(child);
            }
        }
    }

    /**
     * Inject scrollbar track and thumb shapes as overlays on a scroll container.
     */
    private void injectScrollbars(LayoutNode.BoxNode box, float viewportH,
                                   float contentH, float scrollOffset, float maxOffset) {
        box.overlays().clear();

        float trackX = box.x() + box.width() - SCROLLBAR_WIDTH - 2;
        float trackY = box.y() + 2;
        float trackH = viewportH - 4;

        // Track — Surface0 semi-transparent (Catppuccin Mocha)
        var track = new LayoutNode.ShapeNode("rectangle", "pill",
                "#585B7033", "", "", null,
                java.util.List.of("scrollbar-track"));
        track.explicitWidth(SCROLLBAR_WIDTH);
        track.explicitHeight(trackH);
        track.setBounds(trackX, trackY, SCROLLBAR_WIDTH, trackH);
        box.overlays().add(track);

        // Thumb — Overlay0 semi-transparent
        float thumbRatio = viewportH / contentH;
        float thumbH = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, trackH * thumbRatio);
        float scrollRange = trackH - thumbH;
        float thumbY = trackY + (maxOffset > 0 ? (scrollOffset / maxOffset) * scrollRange : 0);

        var thumb = new LayoutNode.ShapeNode("rectangle", "pill",
                "#6C708688", "", "", null,
                java.util.List.of("scrollbar-thumb"));
        thumb.explicitWidth(SCROLLBAR_WIDTH);
        thumb.explicitHeight(thumbH);
        thumb.setBounds(trackX, thumbY, SCROLLBAR_WIDTH, thumbH);
        box.overlays().add(thumb);
    }

    /**
     * Find the nearest scroll container ancestor at a given screen position.
     */
    private LayoutNode.BoxNode findScrollContainer(LayoutNode node, float x, float y) {
        if (node instanceof LayoutNode.BoxNode box) {
            if (x < box.x() || x > box.x() + box.width() ||
                y < box.y() || y > box.y() + box.height()) {
                return null;
            }
            // Check children first (depth-first, deepest match wins)
            float testY = y;
            if (box.isScrollContainer()) {
                testY = y + box.scrollOffsetY();
            }
            for (int i = box.children().size() - 1; i >= 0; i--) {
                LayoutNode.BoxNode found = findScrollContainer(box.children().get(i), x, testY);
                if (found != null) return found;
            }
            if (box.isScrollContainer()) return box;
        }
        return null;
    }

    /**
     * Update the detail pane's viewport based on the "detail" region in the layout tree.
     */
    private void updateDetailPaneViewport(int windowWidth, int windowHeight) {
        if (detailPane == null || lastLayoutRoot == null) return;

        LayoutNode.BoxNode detailNode = findNodeById(lastLayoutRoot, "detail");
        if (detailNode == null) {
            // No detail region — hide detail pane by setting zero viewport
            detailPane.setViewport(0, 0, 0, 0);
            return;
        }

        // Convert from layout coordinates (top-left origin, Y-down) to
        // framebuffer coordinates for Filament viewports (bottom-left origin).
        int fbWidth = uiPane != null && uiPane.viewportWidth() > 0
                ? uiPane.viewportWidth() : Math.max(1, windowWidth);
        int fbHeight = uiPane != null && uiPane.viewportHeight() > 0
                ? uiPane.viewportHeight() : Math.max(1, windowHeight);

        float scaleX = windowWidth > 0 ? (float) fbWidth / windowWidth : 1.0f;
        float scaleY = windowHeight > 0 ? (float) fbHeight / windowHeight : 1.0f;

        int left = Math.round(detailNode.x() * scaleX);
        int top = Math.round(detailNode.y() * scaleY);
        int width = Math.round(detailNode.width() * scaleX);
        int height = Math.round(detailNode.height() * scaleY);
        int bottom = fbHeight - top - height;

        detailPane.setViewport(left, Math.max(0, bottom), Math.max(0, width), Math.max(0, height));
    }

    /**
     * Find a direct-child BoxNode by ID in the layout tree.
     */
    private LayoutNode.BoxNode findNodeById(LayoutNode.BoxNode root, String id) {
        if (id.equals(root.id())) return root;
        for (LayoutNode child : root.children()) {
            if (child instanceof LayoutNode.BoxNode box) {
                if (id.equals(box.id())) return box;
                // Recurse one more level for constraint layout wrappers
                LayoutNode.BoxNode found = findNodeById(box, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ==================== 3D Scene Rendering ====================

    /**
     * Rebuild the 3D scene in the detail pane.
     *
     * <p>When a component with {@code @Body} is selected in the tree, renders
     * that component's body alone in a neutral space (gray background, basic lighting).
     *
     * <p>Only rebuilds when the selected context changes to avoid re-creating
     * Filament entities every frame.
     */
    @SuppressWarnings("unchecked")
    private void rebuildScene() {
        if (detailPane == null || filamentWindow == null || itemModel() == null) {
            hideDetailPane();
            return;
        }

        Link context = itemModel().context();
        if (context == null) {
            hideDetailPane();
            return;
        }

        // Only rebuild if context link changed or state was mutated
        if (context.equals(sceneContextLink) && sceneRenderer != null && !sceneDirty) return;
        sceneDirty = false;
        sceneContextLink = context;
        sceneFocusExtent = Float.NaN;

        // Tear down previous scene
        if (panelPainter != null) {
            panelPainter.clear();
        }
        if (detailPainter != null) {
            detailPainter.clear();
        }
        if (sceneRenderer != null) {
            sceneRenderer.destroy();
            sceneRenderer = null;
        }

        // Only render 3D for a specific component selection (not root-level)
        java.util.Optional<String> path = context.path();
        if (path.isEmpty() || path.get().isEmpty()) {
            hideDetailPane();
            return;
        }

        // Resolve the owning item and find the live component
        Item item = resolveItem(context.item()).orElse(null);
        if (item == null) {
            hideDetailPane();
            return;
        }

        String handle = path.get().startsWith("/") ? path.get().substring(1) : path.get();
        FrameKey key = FrameKey.literal(handle);

        Object component = item.content().getLive(key).orElse(null);
        if (component == null) {
            hideDetailPane();
            return;
        }

        // Check if the component has a @Body annotation
        CompiledBody body = SpatialCompiler.compileBody(component.getClass());
        boolean chessLike = component.getClass().getName().contains(".game.chess.ChessGame");
        if (!body.isCompound() && !body.hasGeometry()) {
            hideDetailPane();
            return;
        }

        // Detect whether the compound body's schema defines a Space (environment)
        boolean isSpace = body.isCompound()
                && SpatialCompiler.compileSpace(body.as()).hasEnvironment();

        // Create a new spatial renderer for the detail pane
        sceneRenderer = new FilamentSpatialRenderer(
                filamentWindow.engine(), detailPane.scene(), openAL);
        sceneRenderer.setIsSpace(isSpace);
        if (panelPainter != null) {
            sceneRenderer.setPanelPainter(panelPainter);
        }

        if (!isSpace) {
            // Non-space body: use ItemSpace's default white room environment
            SpatialCompiler.compileSpace(ItemSpace.class).emit(sceneRenderer);
        }
        // Space bodies emit their own environment via schema.render()

        // Render the component's body
        float[] dims = null;
        if (body.isCompound()) {
            try {
                SpatialSchema<Object> schema = (SpatialSchema<Object>)
                        body.as().getDeclaredConstructor().newInstance();
                schema.value(component);
                schema.render(sceneRenderer);
            } catch (ReflectiveOperationException e) {
                log.warn("Failed to instantiate body {} for component {}",
                        body.as().getSimpleName(), component.getClass().getSimpleName(), e);
                return;
            }
        } else {
            // Body+Surface composition: if the component also has @Surface,
            // the surface's elevated elements provide all visual geometry
            // (squares become slabs, images become GLB meshes). The body
            // annotation provides dimensions only — no separate slab needed.
            boolean hasSurface = SceneCompiler.canCompile(component.getClass());
            if (!hasSurface) {
                body.emitGeometry(sceneRenderer, sceneRenderer.renderContext());
            }
            dims = composeSurfaceOnBody(component, body);
        }

        if (!isSpace) {
            // Non-space body: default camera derived from body dimensions.
            // Views from the layout-bottom side (negative DSL Y → positive Filament Z)
            // at ~30° elevation — natural for boards where rank 1 / "home" is at bottom.
            float bw, bd, bh;
            if (dims != null) {
                bw = dims[0]; bd = dims[1]; bh = dims[2];
            } else {
                RenderContext spatialCtx = sceneRenderer.renderContext();
                bw = (float) CompiledBody.dim(body.width(), spatialCtx);
                bd = (float) CompiledBody.dim(body.depth(), spatialCtx);
                bh = (float) CompiledBody.dim(body.height(), spatialCtx);
            }
            float extent = Float.isFinite(sceneFocusExtent) ? sceneFocusExtent : Math.max(bw, bd);
            if (extent < 0.01f) extent = 1f; // fallback

            // Distance to fit content in 60° FOV with margin.
            // Slightly tighter than before so chess starts closer.
            float dist = extent * 1.2f / (2f * (float) Math.tan(Math.toRadians(30)));
            // ~30° elevation: cos(30°)=0.866 for forward, sin(30°)=0.5 for up
            float camY = -dist * 0.866f;                  // negative DSL Y = layout-bottom side
            float camZ = bh / 2f + dist * 0.5f;           // up (DSL Z)
            sceneRenderer.camera("perspective", 60, 0.01, 100,
                    0, camY, camZ,        // eye: behind layout-bottom, 30° above
                    0, 0, bh / 2f);       // target: center of body
        }
        // Space bodies set their own camera via @Space.Camera

        // Apply camera defaults (sets content hint + initial mode)
        sceneRenderer.applyCameraDefaults(cameraController);
        cameraController.setOrthographic(false);

        // Show the detail pane viewport now that there's content
        int layoutW = filamentWindow.width();
        int layoutH = filamentWindow.height();
        updateDetailPaneViewport(layoutW, layoutH);
    }

    /**
     * Hide the detail pane by zeroing its viewport.
     * Prevents an empty 3D view from overwriting 2D UI content (e.g., clock).
     */
    private void hideDetailPane() {
        if (detailPane != null) {
            detailPane.setViewport(0, 0, 0, 0);
        }
    }

    /**
     * Compose a 2D surface onto a 3D body's top face.
     *
     * <p>When a component has both {@code @Body} and {@code @Surface} annotations,
     * the surface layout is rendered as 3D geometry. The {@code @Body} provides
     * real-world physical dimensions; the surface layout (in em, %, px) scales
     * to match. Objects with real-world size (GLB models) render at their actual
     * physical size — the scene is built around them.
     *
     * <p>The ppu (pixels-per-unit) is derived in priority order:
     * <ol>
     *   <li>{@code fontSize} on body → physical em size establishes the mapping</li>
     *   <li>Explicit {@code width}/{@code depth} on body → existing top-down ratio</li>
     *   <li>Neither → DPI fallback (1 layout pixel = 1 physical screen pixel)</li>
     * </ol>
     *
     * @return derived body dimensions (width, depth, height in meters, ppu), or null
     */
    @SuppressWarnings("unchecked")
    private float[] composeSurfaceOnBody(Object component, CompiledBody body) {
        // Check if the component has @Scene(as=...) or inline annotations
        Class<?> sceneAs = null;
        Scene sceneAnno = component.getClass().getAnnotation(Scene.class);
        if (sceneAnno != null && sceneAnno.as() != SceneSchema.class) {
            sceneAs = sceneAnno.as();
        }
        boolean hasInlineSurface = SceneCompiler.canCompile(component.getClass());
        if (sceneAs == null && !hasInlineSurface) return null;

        try {
            // Instantiate the surface and bind the component value
            SurfaceSchema<Object> surface;
            if (sceneAs != null) {
                Class<?> surfaceClass = sceneAs;
                if (SurfaceSchema.class.isAssignableFrom(surfaceClass)) {
                    surface = (SurfaceSchema<Object>) surfaceClass.getDeclaredConstructor().newInstance();
                } else {
                    surface = new SurfaceSchema<>() {};
                    surface.structureClass(surfaceClass);
                }
            } else {
                surface = new SurfaceSchema<>() {};
                surface.structureClass(component.getClass());
            }
            surface.value(component);

            // Run the surface through the 2D pipeline
            var ctx = RenderContext.gui();
            SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer(ctx);
            surface.render(renderer);
            LayoutNode.BoxNode tree = renderer.result();

            // Layout at a generous reference size, then derive actual laid-out
            // root dimensions from the resulting tree.
            float refSize = 1000f;
            boolean useMsdf = msdfFontManager != null && msdfFontManager.hasUsableFonts();
            LayoutEngine.TextMeasurer measurer = useMsdf ? msdfFontManager : fontCache;
            float bfs2 = useMsdf ? msdfFontManager.baseFontSize() : fontCache.baseFontSize();
            var engine = new LayoutEngine(measurer, ctx, Stylesheet.fromClasspath(), bfs2);
            engine.layout(tree, refSize, refSize);

            // Derive ppu and body dimensions.
            // Priority: (1) fontSize → em-to-meters, (2) explicit width/depth, (3) DPI fallback.
            RenderContext spatialCtx = sceneRenderer != null ? sceneRenderer.renderContext() : null;
            float baseFontSize = useMsdf ? msdfFontManager.baseFontSize() : fontCache.baseFontSize();
            float[] elevBounds = computeElevatedBounds(tree);

            float ppu;
            float derivedW, derivedD, derivedH;
            float layoutW = Math.max(tree.width(), 1f);
            float layoutH = Math.max(tree.height(), 1f);

            if (body.isDerivedSize()) {
                // Tier 1: Physical font size → em-to-meters mapping
                float fontMeters = (float) CompiledBody.dim(body.fontSize(), spatialCtx);
                ppu = baseFontSize / fontMeters;
                // Keep body extents tied to laid-out surface size, not elevated
                // content union, to avoid subtle per-instance scale drift.
                derivedW = layoutW / ppu;
                derivedD = layoutH / ppu;
                derivedH = computeMaxElevation(tree);
            } else {
                float bodyW = (float) CompiledBody.dim(body.width(), spatialCtx);
                float bodyD = (float) CompiledBody.dim(body.depth(), spatialCtx);

                if (bodyW > 0.001f && bodyD > 0.001f) {
                    // Tier 2: Explicit body dimensions from full laid-out surface size.
                    ppu = Math.max(layoutW / bodyW, layoutH / bodyD);
                } else if (spatialCtx != null) {
                    // Tier 3: DPI fallback
                    ppu = spatialCtx.dpi() / 0.0254f;
                } else {
                    ppu = 40f; // test fallback
                }
                derivedW = bodyW > 0.001f ? bodyW : layoutW / ppu;
                derivedD = bodyD > 0.001f ? bodyD : layoutH / ppu;
                derivedH = (float) CompiledBody.dim(body.height(), spatialCtx);
            }

            float worldWidth = layoutW / ppu;
            float worldDepth = layoutH / ppu;

            // Create or reuse the detail pane painter for elevated rendering
            if (detailPainter == null && msdfFontManager != null) {
                detailPainter = new FilamentSurfacePainter(
                        filamentWindow.engine(), detailPane.scene(), msdfFontManager);
            }
            if (detailPainter != null) {
                detailPainter.clear();
                detailPainter.configureForElevated(layoutW, layoutH, worldWidth, worldDepth);

                // Center the elevated content in the scene
                float[] centerPx = computeSurfaceCenterPx(component, tree, elevBounds, layoutW, layoutH);
                float layoutCenterX = layoutW / 2f;
                float layoutCenterZ = layoutH / 2f;
                float offsetX = (centerPx[0] - layoutCenterX) / ppu;
                float offsetZ = (centerPx[1] - layoutCenterZ) / ppu;
                detailPainter.adjustOrigin(-offsetX, -offsetZ);

                // Bridge embedded body rendering for chess clock in elevated mode:
                // place the embedded clock component as a true 3D body at its clock-area anchor.
                tryPlaceEmbeddedClockBody(component, tree, ppu, worldWidth, worldDepth, elevBounds);

                // Elevated elements start at ground level (no separate body slab)
                detailPainter.paintElevated(tree, 0f);

                // Dispatch deferred model placements to the spatial renderer
                if (sceneRenderer != null) {
                    for (var m : detailPainter.modelPlacements()) {
                        sceneRenderer.placeModelAt(m.resource(), m.color(),
                                m.x(), m.y(), m.z(), m.scale());
                    }
                }
            }
            maybeSetSceneFocusExtent(component, tree, ppu);
            return new float[]{derivedW, derivedD, derivedH, ppu};
        } catch (ReflectiveOperationException e) {
            log.warn("Failed to compose surface on body for {}",
                    component.getClass().getSimpleName(), e);
            return null;
        }
    }

    /**
     * Compute the union bounds of all elevated boxes in the layout tree.
     *
     * @return {@code [minX, minY, maxX, maxY]} in pixel space, or null if no elevated boxes
     */
    private float[] computeElevatedBounds(LayoutNode.BoxNode root) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};
        boolean found = collectElevatedBounds(root, bounds);
        return found ? bounds : null;
    }

    private boolean collectElevatedBounds(LayoutNode.BoxNode node, float[] bounds) {
        boolean found = false;
        if (node.isElevated()) {
            bounds[0] = Math.min(bounds[0], node.x());
            bounds[1] = Math.min(bounds[1], node.y());
            bounds[2] = Math.max(bounds[2], node.x() + node.width());
            bounds[3] = Math.max(bounds[3], node.y() + node.height());
            found = true;
        }
        for (LayoutNode child : node.children()) {
            if (child instanceof LayoutNode.BoxNode box) {
                if (collectElevatedBounds(box, bounds)) found = true;
            }
        }
        return found;
    }

    /**
     * Compute the maximum elevation (in meters) across all elevated nodes in the tree.
     * Returns 0 if no elevated nodes exist.
     */
    private float computeMaxElevation(LayoutNode.BoxNode root) {
        float[] max = {0f};
        collectMaxElevation(root, max);
        return max[0];
    }

    /**
     * If this component exposes a non-null {@code clock()} and the layout has a
     * {@code clock-area} node, suppress flattened clock rendering there and place
     * the clock object as a real spatial body with face panels.
     */
    private void tryPlaceEmbeddedClockBody(Object component, LayoutNode.BoxNode tree, float ppu,
                                           float worldWidth, float worldDepth, float[] elevBounds) {
        if (sceneRenderer == null || component == null) return;

        try {
            Method clockMethod = component.getClass().getMethod("clock");
            Object clockObj = clockMethod.invoke(component);
            if (clockObj == null) return;

            LayoutNode.BoxNode clockArea = findNodeById(tree, "clock-area");
            if (clockArea == null) return;

            // Prevent duplicate flat clock rendering in the elevated pass.
            clockArea.children().clear();
            clockArea.overlays().clear();

            CompiledBody clockBody = SpatialCompiler.compileBody(clockObj.getClass());
            if (!clockBody.hasGeometry()) return;

            float centerPxX = clockArea.x() + clockArea.width() / 2f;
            float centerPxY = clockArea.y() + clockArea.height() / 2f;

            float layoutCenterX = (worldWidth * ppu) / 2f;
            float layoutCenterZ = (worldDepth * ppu) / 2f;
            float[] centerPx = computeSurfaceCenterPx(component, tree, elevBounds,
                    worldWidth * ppu, worldDepth * ppu);
            float offsetX = (centerPx[0] - layoutCenterX) / ppu;
            float offsetZ = (centerPx[1] - layoutCenterZ) / ppu;

            float originX = -worldWidth / 2f - offsetX;
            float originZ = -worldDepth / 2f - offsetZ;
            float worldX = centerPxX / ppu + originX;
            float worldZ = centerPxY / ppu + originZ;

            RenderContext spatialCtx = sceneRenderer.renderContext();
            double clockHeight = CompiledBody.dim(clockBody.height(), spatialCtx);

            // DSL space: (x right, y forward, z up). Rotate 90° around Z so the
            // clock body stands perpendicular to the board edge (like a real clock).
            sceneRenderer.pushTransform(worldX, -worldZ, clockHeight / 2.0,
                    0, 0, -0.7071, 0.7071, 1, 1, 1);
            try {
                clockBody.emitAll(sceneRenderer, clockObj, spatialCtx);
            } catch (Throwable t) {
                throw t;
            } finally {
                sceneRenderer.popTransform();
            }
        } catch (ReflectiveOperationException ignored) {
            // No accessible clock() method on this component.
        }
    }

    /**
     * Choose the pixel-space center used to align elevated composition.
     *
     * <p>For chess-like surfaces, prefer the board region so perspective remains
     * stable even if surrounding elevated content changes slightly.
     */
    private float[] computeSurfaceCenterPx(Object component, LayoutNode.BoxNode tree,
                                           float[] elevBounds, float layoutW, float layoutH) {
        boolean chessLike = component != null
                && component.getClass().getName().contains(".game.chess.ChessGame");
        if (chessLike) {
            LayoutNode.BoxNode board = findNodeById(tree, "board-root");
            if (board != null) {
                return new float[]{
                        board.x() + board.width() / 2f,
                        board.y() + board.height() / 2f
                };
            }
        }
        if (elevBounds != null) {
            return new float[]{
                    (elevBounds[0] + elevBounds[2]) / 2f,
                    (elevBounds[1] + elevBounds[3]) / 2f
            };
        }
        return new float[]{layoutW / 2f, layoutH / 2f};
    }

    private void collectMaxElevation(LayoutNode.BoxNode node, float[] max) {
        if (node.isElevated()) {
            max[0] = Math.max(max[0], (float) node.elevation());
        }
        for (LayoutNode child : node.children()) {
            if (child instanceof LayoutNode.BoxNode box) {
                collectMaxElevation(box, max);
            }
        }
    }

    private void maybeSetSceneFocusExtent(Object component, LayoutNode.BoxNode tree, float ppu) {
        if (component == null || !component.getClass().getName().contains(".game.chess.ChessGame")) return;
        LayoutNode.BoxNode board = findNodeById(tree, "board-root");
        if (board == null) return;
        float boardWorldW = board.width() / ppu;
        float boardWorldD = board.height() / ppu;
        sceneFocusExtent = Math.max(boardWorldW, boardWorldD);
    }

    // ==================== Animation ====================

    private void applyAnimations(LayoutNode node) {
        if (node instanceof LayoutNode.BoxNode box) {
            String id = box.id();
            if (id != null && !box.transitions().isEmpty()) {
                animationState.registerTransitions(id, box.transitions());
                animationState.setTarget(id, "x", box.x());
                animationState.setTarget(id, "y", box.y());

                float animX = (float) animationState.getValue(id, "x", box.x());
                float animY = (float) animationState.getValue(id, "y", box.y());
                if (animX != box.x() || animY != box.y()) {
                    box.setBounds(animX, animY, box.width(), box.height());
                }
            }

            for (LayoutNode child : box.children()) {
                applyAnimations(child);
            }
        }
    }

    // ==================== Resize Grip Rendering ====================

    /**
     * Repaint the resize grip overlay for Filament.
     * Uses a dedicated lightweight painter so it can update on hover
     * without rebuilding the entire UI layout.
     */
    private void repaintGrip() {
        if (gripPainter == null || stage == null) return;
        float w = stage.width(), h = stage.height();

        gripPainter.clear();
        gripPainter.configureForLayout(w, h, 2.0f, 1.0f);
        resizeController.paintGrip(w, h, (x, y, size, color) ->
                gripPainter.emitColoredQuad(x, y, size, size, color, 0.05f));
        gripPainter.flushBatches();
    }

    /**
     * Paint the resize grip dots directly on a Skia canvas.
     */
    private void paintResizeGripSkia(io.github.humbleui.skija.Canvas canvas) {
        if (stage == null) return;
        float w = stage.width(), h = stage.height();

        try (var dotPaint = new io.github.humbleui.skija.Paint()) {
            dotPaint.setAntiAlias(true);
            resizeController.paintGrip(w, h, (x, y, size, color) -> {
                dotPaint.setColor(color);
                canvas.drawCircle(x + size / 2f, y + size / 2f, size / 2f, dotPaint);
            });
        }
    }

    // ==================== Cleanup ====================

    private void cleanup() {
        stopLiveTimer();
        cleanupFilament();

        if (skiaWindow != null) {
            skiaWindow.destroy();
            skiaWindow = null;
        }
        if (fontCache != null) {
            fontCache.close();
            fontCache = null;
        }
        if (resizeController != null) {
            resizeController.destroy();
            resizeController = null;
        }
    }

    private void cleanupFilament() {
        // Pane painters are destroyed by FilamentWindow.destroy() via pane.destroy()
        uiPane = null;
        detailPane = null;

        if (gripPainter != null) {
            gripPainter.destroy();
            gripPainter = null;
        }
        if (panelPainter != null) {
            panelPainter.destroy();
            panelPainter = null;
        }
        if (detailPainter != null) {
            detailPainter.destroy();
            detailPainter = null;
        }
        if (sceneRenderer != null) {
            sceneRenderer.destroy();
            sceneRenderer = null;
        }
        if (msdfFontManager != null) {
            msdfFontManager.destroy();
            msdfFontManager = null;
        }
        if (openAL != null) {
            openAL.close();
            openAL = null;
        }
        if (filamentWindow != null) {
            filamentWindow.destroy();
            filamentWindow = null;
        }
    }
}
