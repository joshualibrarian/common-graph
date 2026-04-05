package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.TickRegistry;
import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.parse.InputController;
import dev.everydaythings.graph.ui.Stage;
import dev.everydaythings.graph.ui.WindowDragController;
import dev.everydaythings.graph.ui.WindowResizeController;
import dev.everydaythings.graph.ui.filament.*;
import dev.everydaythings.graph.ui.scene.AnimationState;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.skia.*;
import dev.everydaythings.graph.ui.style.Stylesheet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Per-window controller that bridges a {@link ViewHandle} to an OS window.
 *
 * <p>Each ViewWindow owns its own:
 * <ul>
 *   <li>{@link Stage} — the OS window (Skia or Filament)</li>
 *   <li>{@link ItemModel} — navigation, tree, selection state</li>
 *   <li>{@link InputController} — prompt, completions, history</li>
 *   <li>Layout and renderer state</li>
 * </ul>
 *
 * <p>Shared resources (fonts, layout engine, painter) come from
 * {@link SharedResources} which the session owns.
 */
public class ViewWindow {

    private static final Logger log = LogManager.getLogger(ViewWindow.class);

    // ==================== Constants ====================

    private static final float DEFAULT_FONT_SIZE = 15f;
    private static final float SCROLL_LINE_HEIGHT = 40f;
    private static final long DOUBLE_CLICK_THRESHOLD_NS = 400_000_000L;

    // ==================== Identity ====================

    private final FrameKey frameKey;
    private ViewHandle viewHandle;
    private final Session session;
    private final SharedResources shared;
    private final FilamentContext filamentContext;
    private final GraphicalSession.CoordinateMapper coordinateMapper;

    // ==================== Renderer ====================

    private ViewConfig.RendererType rendererType;
    private boolean filamentAvailable;
    private Stage stage;

    // ==================== Filament Path ====================

    private FilamentWindow filamentWindow;
    private FilamentPane uiPane;
    private FilamentPane detailPane;
    private CameraController cameraController;
    private MsdfFontManager msdfFontManager;
    private FilamentSpatialRenderer sceneRenderer;
    private FilamentSurfacePainter detailPainter;
    private PanelPainter panelPainter;
    private dev.everydaythings.graph.ui.audio.OpenALAudio openAL;
    private Ref sceneContextRef;
    private boolean sceneDirty;
    private float sceneFocusExtent = Float.NaN;
    private boolean flatMode = true;
    private SkiaPanel skiaPanel;
    private boolean filamentLayoutDirty;

    // ==================== Skia Path ====================

    private SkiaWindow skiaWindow;
    private LayoutNode.BoxNode currentLayout;

    // ==================== Window Chrome ====================

    private WindowDragController dragController;
    private WindowResizeController resizeController;
    private FilamentSurfacePainter gripPainter;
    private double lastCursorX, lastCursorY;

    // ==================== Layout ====================

    private LayoutNode.BoxNode lastLayoutRoot;

    // ==================== Per-Window UI State ====================

    private dev.everydaythings.graph.ui.scene.surface.item.ItemView itemView;
    private dev.everydaythings.graph.ui.skia.NodeLayoutCompiler nodeRenderer;
    private dev.everydaythings.graph.ui.scene.node.Node lastNodeTree;
    private InputController inputController;
    private final AnimationState animationState = new AnimationState();
    private volatile String pendingExpression;
    private volatile ItemID pendingNavigation;
    private final dev.everydaythings.graph.ui.skia.ScrollState scrollState =
            new dev.everydaythings.graph.ui.skia.ScrollState();
    private ScheduledExecutorService liveTimer;
    private final TickRegistry tickRegistry = new TickRegistry();

    // ==================== Double-Click ====================

    private long lastClickTimeNanos;
    private double lastClickX, lastClickY;

    // ==================== Constructor ====================

    public ViewWindow(FrameKey frameKey, ViewHandle viewHandle,
                      Session session, SharedResources shared,
                      FilamentContext filamentContext) {
        this(frameKey, viewHandle, session, shared, filamentContext, null);
    }

    public ViewWindow(FrameKey frameKey, ViewHandle viewHandle,
                      Session session, SharedResources shared,
                      FilamentContext filamentContext,
                      GraphicalSession.CoordinateMapper coordinateMapper) {
        this.frameKey = frameKey;
        this.viewHandle = viewHandle;
        this.session = session;
        this.shared = shared;
        this.filamentContext = filamentContext;
        this.coordinateMapper = coordinateMapper != null ? coordinateMapper
                : new GraphicalSession.CoordinateMapper() {
            @Override public int[] sessionToOs(int sx, int sy) { return new int[]{sx, sy}; }
            @Override public int[] osToSession(int ox, int oy) { return new int[]{ox, oy}; }
        };
        this.rendererType = viewHandle.config().renderer() != null
                ? viewHandle.config().renderer()
                : ViewConfig.RendererType.FILAMENT;
        this.filamentAvailable = checkFilamentAvailable();
    }

    // ==================== Accessors ====================

    public FrameKey frameKey() { return frameKey; }
    public ViewHandle viewHandle() { return viewHandle; }
    public Stage stage() { return stage; }
    public dev.everydaythings.graph.ui.scene.surface.item.ItemView itemView() { return itemView; }
    public ViewConfig.RendererType rendererType() { return rendererType; }

    // ==================== Lifecycle ====================

    /**
     * Initialize the window: create Stage, ItemModel, InputController, set up callbacks.
     */
    public void init() {
        resizeController = new WindowResizeController(20f);

        Item targetItem = session.resolveItem(viewHandle.target()).orElse(null);
        String title = targetItem != null
                ? "Common Graph - " + targetItem.displayToken()
                : "Common Graph";

        if (rendererType == ViewConfig.RendererType.FILAMENT && filamentAvailable) {
            initFilament(title);
        } else {
            rendererType = ViewConfig.RendererType.SKIA;
            initSkia(title);
        }

        // Apply saved geometry from ViewConfig (session-space → OS-space)
        ViewConfig config = viewHandle.config();
        if (config.width() > 0 && config.height() > 0) {
            stage.setWindowSize(config.width(), config.height());
        }
        if (config.x() != 0 || config.y() != 0) {
            int[] osPos = coordinateMapper.sessionToOs(config.x(), config.y());
            stage.setWindowPos(osPos[0], osPos[1]);
        }

        stage.show();

        // Create ItemView wrapping the target item
        Optional<Item> viewTargetItem = session.resolveItem(viewHandle.target());
        if (viewTargetItem.isPresent()) {
            itemView = new dev.everydaythings.graph.ui.scene.surface.item.ItemView(
                    viewTargetItem.get(), iid -> session.resolveItem(iid));
            itemView.setRenderInputInSurface(true);
        }

        // Create per-window SceneRenderer (state store persists across re-renders)
        nodeRenderer = new dev.everydaythings.graph.ui.skia.NodeLayoutCompiler();
        nodeRenderer.onApplicationAction((action, target) -> {
            boolean handled = false;
            if (itemView != null) handled = itemView.handleEvent(action, target);
            return handled;
        });

        // Wire ItemView state changes to trigger re-render
        if (itemView != null) {
            itemView.setStateStore(nodeRenderer.stateStore());
            itemView.onChange(() -> { rebuildLayout(); requestRepaint(); });
        }

        // Create per-window InputController
        initializeInputController();

        // Initial layout
        rebuildLayout();
        startLiveTimer();
    }

    /**
     * Process one frame. Called from the central event loop after glfwPollEvents().
     *
     * @return true if the window is still alive, false if it wants to close
     */
    public boolean tick() {
        if (stage == null) return false;

        // Process deferred view-open from double-click (outside GLFW callback).
        // Double-click = "view <item>" = open a NEW window, never replace the current one.
        ItemID navTarget = pendingNavigation;
        if (navTarget != null) {
            pendingNavigation = null;
            session.openView(navTarget);
        }

        // Process deferred expression from mouse events (outside GLFW callback)
        String expr = pendingExpression;
        if (expr != null) {
            pendingExpression = null;
            if (evaluateExpression(expr)) {
                rebuildLayout();
                requestRepaint();
            }
        }

        return stage.tick();
    }

    /**
     * Save window geometry and mode back to the ITEM_VIEW frame's ViewConfig.
     */
    public void syncConfigToFrame() {
        if (stage == null) return;
        int[] osPos = stage.getWindowPos();
        int[] sessionPos = coordinateMapper.osToSession(osPos[0], osPos[1]);
        ViewConfig updated = ViewConfig.builder()
                .mode(viewHandle.config().mode())
                .inspectMode(viewHandle.config().inspectMode())
                .renderer(rendererType)
                .x(sessionPos[0]).y(sessionPos[1])
                .width(stage.width()).height(stage.height())
                .collapsed(viewHandle.config().collapsed())
                .build();
        session.updateViewConfig(frameKey, updated);
        viewHandle = viewHandle.withConfig(updated);
    }

    /**
     * Tear down all window resources.
     */
    public void destroy() {
        stopLiveTimer();
        cleanupFilament();
        if (skiaWindow != null) {
            skiaWindow.destroy();
            skiaWindow = null;
        }
        if (resizeController != null) {
            resizeController.destroy();
            resizeController = null;
        }
        stage = null;
    }

    // ==================== Skia Initialization ====================

    private void initSkia(String title) {
        skiaWindow = new SkiaWindow();
        skiaWindow.init(title);
        stage = skiaWindow;

        dragController = new WindowDragController(32f, stage);
        resizeController.setStage(stage);
        resizeController.onHoverChanged(this::requestRepaint);

        skiaWindow.onPaint(canvas -> {
            double dt = skiaWindow.deltaTime();
            if (dt > 0 && animationState.isAnimating()) {
                animationState.update(dt);
                if (currentLayout != null) {
                    applyAnimations(currentLayout);
                }
            }
            if (currentLayout != null) {
                shared.painter().paint(canvas, currentLayout);
            }
            paintResizeGripSkia(canvas);
            if (animationState.isAnimating()) {
                skiaWindow.requestPaint();
            }
        });

        skiaWindow.onKey((key, scancode, action, mods) -> {
            KeyChord chord = shared.keyAdapter().fromNative(key, scancode, action, mods);
            if (chord != null) handleKeyChord(chord);
        });

        skiaWindow.onChar(codepoint -> {
            KeyChord chord = shared.keyAdapter().fromChar(codepoint);
            if (chord != null) handleKeyChord(chord);
        });

        skiaWindow.onMouseButton((button, action, mods) -> {
            if (resizeController.onMouseButton(button, action, lastCursorX, lastCursorY)) return;
            if (dragController.onMouseButton(button, action, lastCursorX, lastCursorY)) return;
            if (action == GLFW_RELEASE) {
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

        skiaWindow.onScroll((xOffset, yOffset) ->
                handleMouseScroll(lastCursorX, lastCursorY, 1.0f, xOffset, yOffset));

        skiaWindow.onResize((w, h) -> rebuildLayout());
    }

    // ==================== Filament Initialization ====================

    private void initFilament(String title) {
        cameraController = new CameraController();

        openAL = new dev.everydaythings.graph.ui.audio.OpenALAudio();
        if (!openAL.init()) {
            log.warn("Audio unavailable — continuing without sound");
        }

        filamentWindow = new FilamentWindow();
        filamentWindow.init(title, filamentContext);
        stage = filamentWindow;

        uiPane = filamentWindow.createPane(false);
        uiPane.configureOrtho(uiPane.viewportAspect());

        detailPane = filamentWindow.createPane(true);
        detailPane.clearFullWindow();
        detailPane.configurePerspective();

        // Painters: prefer MSDF, fall back to Skia-in-Filament.
        // Each window needs its own MsdfFontManager because Filament atlas textures
        // are tied to the painter/renderer lifecycle and can't be shared.
        try {
            msdfFontManager = new MsdfFontManager(filamentWindow.engine(), shared.fontRegistry());
        } catch (Throwable t) {
            log.warn("MSDF initialization failed; using Skia texture fallback", t);
            msdfFontManager = null;
        }
        if (msdfFontManager != null && msdfFontManager.hasUsableFonts()) {
            uiPane.painter(new FilamentSurfacePainter(
                    filamentWindow.engine(), uiPane.scene(), msdfFontManager, filamentContext));
            panelPainter = new FilamentPanelPainter(
                    filamentWindow.engine(), detailPane.scene(), msdfFontManager,
                    session.librarian());
        } else {
            uiPane.painter(new SkiaSurfacePainter(
                    filamentWindow.engine(), uiPane.scene(), shared.painter(), shared.fontCache()));
            panelPainter = new SkiaPanelPainter(
                    filamentWindow.engine(), detailPane.scene(), shared.painter(), shared.fontCache());
        }

        // 3D scene renderer — loads GLB models and emits 3D geometry to detailPane
        sceneRenderer = new FilamentSpatialRenderer(filamentWindow.engine(), detailPane.scene());
        // Elevated surface painter — renders the same layout tree as 3D geometry on detailPane
        if (msdfFontManager != null) {
            detailPainter = new FilamentSurfacePainter(
                    filamentWindow.engine(), detailPane.scene(), msdfFontManager, filamentContext);
        }

        dragController = new WindowDragController(32f, stage);
        resizeController.setStage(stage);
        resizeController.onHoverChanged(this::repaintGrip);

        if (msdfFontManager != null) {
            gripPainter = new FilamentSurfacePainter(
                    filamentWindow.engine(), uiPane.scene(), msdfFontManager, filamentContext);
        }

        setupFilamentInput();

        filamentWindow.onBeforeRender(() -> {
            try {
                if (filamentLayoutDirty) {
                    uiPane.configureOrtho(uiPane.viewportAspect());
                    rebuildLayout();
                    filamentLayoutDirty = false;
                }
                if (!flatMode && cameraController != null) {
                    double dt = filamentWindow.deltaTime();
                    cameraController.update(dt);
                    double aspect = detailPane != null
                            ? detailPane.viewportAspect()
                            : (double) filamentWindow.width() / filamentWindow.height();
                    cameraController.applyToCamera(detailPane.camera(), aspect);
                }
            } catch (Exception e) {
                log.error("onBeforeRender failed", e);
            }
        });

        filamentWindow.onResize((w, h) -> filamentLayoutDirty = true);

        applyFontSize(DEFAULT_FONT_SIZE);
    }

    private void setupFilamentInput() {
        float[] inputScale = {1.0f};
        Runnable updateScale = () -> {
            float vpW = uiPane != null && uiPane.viewportWidth() > 0 ? uiPane.viewportWidth() : stage.width();
            inputScale[0] = stage.width() > 0 ? vpW / (float) stage.width() : 1.0f;
        };

        filamentWindow.onKey((key, scancode, action, mods) -> {
            KeyChord chord = shared.keyAdapter().fromNative(key, scancode, action, mods);
            if (chord != null) {
                if (chord.isKey(SpecialKey.F9)) {
                    switchRenderer(ViewConfig.RendererType.SKIA);
                    return;
                }
                if (chord.isKey(SpecialKey.F10)) {
                    flatMode = !flatMode;
                    sceneDirty = true;
                    rebuildLayout();
                    requestRepaint();
                    return;
                }
                handleKeyChord(chord);
            }
        });

        filamentWindow.onChar(codepoint -> {
            KeyChord chord = shared.keyAdapter().fromChar(codepoint);
            if (chord != null) handleKeyChord(chord);
        });

        filamentWindow.onMouseButton((button, action, mods) -> {
            updateScale.run();
            if (!flatMode && cameraController != null) {
                cameraController.onMouseButton(button, action, mods);
                requestRepaint();
                return;
            }
            double sx = lastCursorX * inputScale[0];
            double sy = lastCursorY * inputScale[0];
            if (resizeController.onMouseButton(button, action, lastCursorX, lastCursorY)) return;
            if (dragController.onMouseButton(button, action, lastCursorX, lastCursorY)) return;
            if (action == GLFW_RELEASE) {
                handleMouseButtonRelease(button, sx, sy, 1.0f);
            }
        });

        filamentWindow.onCursorPos((x, y) -> {
            lastCursorX = x;
            lastCursorY = y;
            if (!flatMode && cameraController != null) {
                cameraController.onCursorPos(x, y);
                requestRepaint();
                return;
            }
            resizeController.onCursorPos(x, y);
            dragController.onCursorPos(x, y);
            updateScale.run();
            handleMouseEvent("hover", x * inputScale[0], y * inputScale[0], 1.0f);
        });

        filamentWindow.onScroll((xOffset, yOffset) -> {
            if (!flatMode && cameraController != null) {
                cameraController.onScroll(xOffset, yOffset);
                requestRepaint();
                return;
            }
            updateScale.run();
            handleMouseScroll(lastCursorX * inputScale[0], lastCursorY * inputScale[0],
                    1.0f, xOffset, yOffset);
        });
    }

    private Optional<Item> contextItem() {
        if (itemView == null) return Optional.empty();
        Ref ctx = itemView.context();
        if (ctx == null || ctx.target() == null) return Optional.empty();
        return session.resolveItem(ctx.target());
    }

    // ==================== Layout ====================

    void rebuildLayout() {
        if (itemView == null) return;

        try {
            boolean filamentSkiaFallback = rendererType == ViewConfig.RendererType.FILAMENT
                    && uiPane != null
                    && uiPane.painter() instanceof SkiaSurfacePainter;
            float w, h, dpr;
            if (rendererType == ViewConfig.RendererType.FILAMENT) {
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
                w = 800; h = 600; dpr = 1.0f;
            }

            boolean useMsdf = rendererType == ViewConfig.RendererType.FILAMENT
                    && msdfFontManager != null && !filamentSkiaFallback;
            RenderMetrics metrics = useMsdf ? msdfFontManager.buildMetrics() : shared.fontCache().buildMetrics();
            float baseFontSize = useMsdf ? msdfFontManager.baseFontSize() : shared.fontCache().baseFontSize();

            var ctx = RenderContext.builder()
                    .renderer(RenderContext.RENDERER_SKIA)
                    .breakpoint(RenderContext.BREAKPOINT_LG)
                    .viewportWidth(w).viewportHeight(h)
                    .devicePixelRatio(dpr).dpi(96 * dpr)
                    .addCapability("color").addCapability("mouse").addCapability("images")
                    .librarian(session.librarian())
                    .renderMetrics(metrics)
                    .baseFontSize(baseFontSize)
                    .build();
            // Node tree → NodeLayoutCompiler → LayoutNode tree
            var env = dev.everydaythings.graph.ui.scene.node.RenderEnvironment.builder()
                    .renderer(dev.everydaythings.graph.ui.scene.node.RenderEnvironment.SKIA)
                    .viewportWidth(w).viewportHeight(h)
                    .dpi(96 * dpr).devicePixelRatio(dpr)
                    .baseFontSize(baseFontSize)
                    .librarian(session.librarian())
                    .capabilities("color", "mouse", "images")
                    .unit(dev.everydaythings.graph.value.Unit.Em.IID, (double) baseFontSize)
                    .unit(dev.everydaythings.graph.value.Unit.CharacterWidth.IID, (double) baseFontSize * 0.6)
                    .unit(dev.everydaythings.graph.value.Unit.LineHeight.IID, (double) baseFontSize * 1.4)
                    .unit(dev.everydaythings.graph.value.Unit.Pixel.IID, 1.0)
                    .build();
            nodeRenderer.environment(env);
            lastNodeTree = itemView.toNode();
            nodeRenderer.renderTree(lastNodeTree);
            LayoutNode.BoxNode tree = nodeRenderer.result();
            if (tree == null) return;

            LayoutEngine.TextMeasurer measurer = useMsdf ? msdfFontManager : shared.fontCache();
            float bfs = useMsdf ? msdfFontManager.baseFontSize() : shared.fontCache().baseFontSize();
            var engine = new LayoutEngine(measurer, ctx, Stylesheet.fromClasspath(), bfs);
            engine.layout(tree, w, h);

            applyScrollState(tree);
            applyAnimations(tree);
            lastLayoutRoot = tree;

            if (rendererType == ViewConfig.RendererType.FILAMENT && uiPane != null && uiPane.painter() != null) {
                uiPane.painter().clear();
                uiPane.painter().configureForLayout(w, h, 2.0f, 1.0f);

                // In 3D mode, skip the detail region in the 2D chrome pane ONLY when
                // it's showing the item's actual scene (not help, meta, or frame detail).
                boolean show3dDetail = !flatMode && itemView != null && itemView.isShowingItemScene();
                uiPane.painter().skipId(show3dDetail ? "detail" : null);

                uiPane.painter().paint(tree, 0f);
                repaintGrip();

                // 3D elevated rendering — same tree, 3D interpretation.
                // Active when showing the item's scene in non-flat mode.
                if (show3dDetail && detailPainter != null) {
                    // Find the detail content subtree — only that goes to 3D
                    LayoutNode.BoxNode detailNode = findNodeById(tree, "detail");
                    if (detailNode == null) detailNode = tree; // fallback

                    // Set detailPane viewport to match the detail node's screen area.
                    int vpScale = filamentWindow.viewportWidth() > 0 && filamentWindow.width() > 0
                            ? filamentWindow.viewportWidth() / filamentWindow.width() : 1;
                    int dvpLeft = (int) (detailNode.x() * vpScale);
                    int dvpBottom = (int) ((h - detailNode.y() - detailNode.height()) * vpScale);
                    int dvpW = (int) (detailNode.width() * vpScale);
                    int dvpH = (int) (detailNode.height() * vpScale);
                    if (dvpW > 0 && dvpH > 0) {
                        detailPane.setViewport(dvpLeft, dvpBottom, dvpW, dvpH);
                        detailPane.configurePerspective();
                    }

                    // Only rebuild the 3D scene when content actually changes (sceneDirty),
                    // not on every layout pass. The scene persists across help/meta toggles.
                    if (sceneDirty) {
                        detailPainter.clear();
                        float detailW = detailNode.width() > 0 ? detailNode.width() : w;
                        float detailH = detailNode.height() > 0 ? detailNode.height() : h;
                        float worldWidth = 0.6f;
                        float worldDepth = worldWidth * (detailH / detailW);
                        detailPainter.configureForElevated(detailW, detailH, worldWidth, worldDepth);
                        // Shift detail subtree to origin (0,0) — the elevated painter
                        // assumes pixel coords start at (0,0), but layout nodes have
                        // absolute window coordinates.
                        float offsetX = detailNode.x();
                        float offsetY = detailNode.y();
                        detailPainter.offsetSubtree(detailNode, -offsetX, -offsetY);
                        detailPainter.paintElevated(detailNode, 0f);
                        detailPainter.offsetSubtree(detailNode, offsetX, offsetY);

                        // Dispatch GLB model placements to spatial renderer
                        if (sceneRenderer != null) {
                            sceneRenderer.clear();
                            sceneRenderer.environment(0x1E1E2E, 0x808080, 0, 0, 0);
                            sceneRenderer.light("sun", 0xFFF5E6, 1.0,
                                    0, 0, 0, -0.5, -1, -0.5);
                            for (var placement : detailPainter.modelPlacements()) {
                                float s = placement.scale();
                                sceneRenderer.pushTransform(
                                        placement.x(), placement.z(), placement.y(),
                                        0, 0, 0, 1,
                                        s, s, s);
                                sceneRenderer.meshBody(placement.resource(), placement.color(),
                                        1.0, "lit", null);
                                sceneRenderer.popTransform();
                            }
                        }

                        // Frame the camera to fit the scene on first render or mode switch
                        if (cameraController != null) {
                            sceneDirty = false;
                            float extent = Math.max(worldWidth, worldDepth);
                            double fov = 60;
                            double dist = extent / (2.0 * Math.tan(Math.toRadians(fov / 2.0)));
                            double angle = Math.toRadians(50);
                            double eyeY = dist * Math.sin(angle);
                            double eyeZ = dist * Math.cos(angle);
                            cameraController.setDefaults(fov, 0.01, 100,
                                    0, eyeY, eyeZ,
                                    0, 0, 0);
                        }
                    }
                } else if (!show3dDetail && detailPane != null) {
                    // Detail is showing help/meta/frame content in 2D —
                    // hide the 3D pane so it doesn't obscure the 2D content.
                    // DON'T clear the 3D scene — it stays built so switching
                    // back to the item scene is instant (just restore viewport).
                    detailPane.setViewport(0, 0, 0, 0);
                }
            } else {
                currentLayout = tree;
            }
        } catch (Exception e) {
            log.error("Layout failed", e);
        }
    }

    // ==================== Input ====================

    private void handleKeyChord(KeyChord chord) {
        // F1-F4 handled by ItemModel toggle actions (help, mounts, frames, versions)

        // Completion navigation takes priority when popup is visible
        if (inputController != null
                && inputController.snapshot().hasVisibleCompletions()
                && !chord.alt() && !chord.ctrl() && !chord.shift()
                && (chord.isKey(SpecialKey.UP) || chord.isKey(SpecialKey.DOWN)
                    || chord.isKey(SpecialKey.TAB) || chord.isKey(SpecialKey.ENTER)
                    || chord.isKey(SpecialKey.ESCAPE))) {
            dispatchToInput(chord);
            rebuildLayout();
            requestRepaint();
            return;
        }

        // @Scene.On key dispatch — matches key chords against event declarations
        if (nodeRenderer != null && lastNodeTree != null
                && nodeRenderer.dispatchKeyEvent(lastNodeTree, chord.toString())) {
            rebuildLayout();
            requestRepaint();
            return;
        }

        // ItemView handles tree navigation (Alt+arrows) — legacy, until fully declarative
        if (itemView != null && itemView.handleKey(chord)) {
            rebuildLayout();
            requestRepaint();
            return;
        }

        // ItemView handles F1-F4 and tree nav via handleKey above
        if (session.handleKey(chord)) {
            rebuildLayout();
            requestRepaint();
            return;
        }
        dispatchToInput(chord);
    }

    private void setViewMode(ViewConfig.ViewMode mode) {
        ViewConfig config = viewHandle.config().withMode(mode);
        session.updateViewConfig(frameKey, config);
        viewHandle = viewHandle.withConfig(config);
        // View mode stored on ViewConfig/viewHandle — no model sync needed
        rebuildLayout();
        requestRepaint();
    }

    private void dispatchToInput(KeyChord chord) {
        if (inputController != null) {
            boolean hasCompletions = inputController.snapshot().hasVisibleCompletions();
            dev.everydaythings.graph.ui.input.InputBindings.defaults()
                    .resolve(chord, hasCompletions)
                    .ifPresent(action -> inputController.handle(action));
            rebuildLayout();
            requestRepaint();
        }
    }

    // ==================== Mouse ====================

    private void handleMouseEvent(String eventType, double cursorX, double cursorY, float dpi) {
        if (lastLayoutRoot == null) return;
        float x = (float) (cursorX * dpi);
        float y = (float) (cursorY * dpi);
        LayoutNode.PendingEvent hit = LayoutNode.hitTest(lastLayoutRoot, x, y, eventType);
        if (hit == null) return;

        // 1. Renderer-internal state mutations (toggle/set/unset/cycle)
        boolean handled = false;
        if (nodeRenderer != null) {
            String nodeId = hit.target() != null && !hit.target().isEmpty()
                    ? hit.target() : findNodeIdAtHit(lastLayoutRoot, x, y);
            handled = nodeRenderer.dispatch(nodeId != null ? nodeId : "", hit.action(), hit.target());
        }

        // 2. ItemView internal events (toggle:help, select, etc.)
        if (!handled && itemView != null) {
            handled = itemView.handleEvent(hit.action(), hit.target());
        }

        // 3. Direct navigation — "view" with an IID target defers navigation
        //    to the next tick (outside GLFW callback).
        if (!handled && "view".equals(hit.action())
                && hit.target() != null && hit.target().startsWith("iid:")) {
            try {
                pendingNavigation = ItemID.parse(hit.target());
            } catch (Exception e) {
                log.debug("Failed to parse IID {}: {}", hit.target(), e.getMessage());
            }
            handled = true;
        }

        // 4. Predicate expressions — action is a predicate, target is the argument.
        //    Compose into an expression and defer evaluation to after the GLFW callback
        //    returns (GLFW doesn't support nested callbacks like window creation).
        if (!handled && hit.action() != null && !hit.action().startsWith("toggle:")
                && !hit.action().equals("select") && !hit.action().equals("hover")) {
            String expression = hit.target() != null && !hit.target().isEmpty()
                    ? hit.action() + " " + hit.target()
                    : hit.action();
            pendingExpression = expression;
            handled = true;
        }

        if (handled) {
            rebuildLayout();
            requestRepaint();
        }
    }

    /**
     * Evaluate an expression through the pipeline (same path as typed input).
     */
    private boolean evaluateExpression(String expression) {
        if (expression == null || expression.isBlank()) return false;
        var lib = session.librarian();
        if (lib == null) return false;

        var eval = dev.everydaythings.graph.runtime.Eval.builder()
                .librarian(lib)
                .context(session.resolveItem(viewHandle.target()).orElse(null))
                .session(session)
                .build();
        var result = eval.evaluateRaw(expression);
        if (result != null && !(result instanceof dev.everydaythings.graph.runtime.Eval.EvalResult.Empty)) {
            session.handleInputResult(result);
            return true;
        }
        return false;
    }

    /**
     * Find the nearest node ID at a hit position by walking the LayoutNode tree.
     * Returns the ID of the deepest node containing the point that has an ID.
     */
    private String findNodeIdAtHit(LayoutNode node, float x, float y) {
        if (node == null) return null;
        if (!(node instanceof LayoutNode.BoxNode box)) {
            return node.id();
        }
        if (x < box.x() || x > box.x() + box.width() ||
            y < box.y() || y > box.y() + box.height()) {
            return null;
        }
        // Check children deepest-first
        float testY = box.isScrollContainer() ? y + box.scrollOffsetY() : y;
        for (int i = box.children().size() - 1; i >= 0; i--) {
            String childId = findNodeIdAtHit(box.children().get(i), x, testY);
            if (childId != null) return childId;
        }
        return box.id(); // this box's ID, or null if no ID
    }

    private void handleMouseButtonRelease(int button, double cursorX, double cursorY, float dpi) {
        String eventType = switch (button) {
            case GLFW_MOUSE_BUTTON_LEFT -> "click";
            case GLFW_MOUSE_BUTTON_RIGHT -> "rightclick";
            case GLFW_MOUSE_BUTTON_MIDDLE -> "middleclick";
            default -> null;
        };
        if (eventType == null) return;

        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            long now = System.nanoTime();
            double dx = cursorX - lastClickX;
            double dy = cursorY - lastClickY;
            boolean closeEnough = dx * dx + dy * dy < 25;
            if (now - lastClickTimeNanos < DOUBLE_CLICK_THRESHOLD_NS && closeEnough) {
                handleMouseEvent("doubleClick", cursorX, cursorY, dpi);
                lastClickTimeNanos = 0;
                return;
            }
            lastClickTimeNanos = now;
            lastClickX = cursorX;
            lastClickY = cursorY;
        }
        handleMouseEvent(eventType, cursorX, cursorY, dpi);
    }

    private void handleMouseScroll(double cursorX, double cursorY, float dpi,
                                    double xOffset, double yOffset) {
        if (lastLayoutRoot == null) return;
        float x = (float) (cursorX * dpi);
        float y = (float) (cursorY * dpi);

        // Explicit scroll handlers first
        LayoutNode.PendingEvent hit = LayoutNode.hitTest(lastLayoutRoot, x, y, "scroll");
        if (hit != null) {
            String scrollTarget = hit.target().isEmpty()
                    ? String.format("%.1f,%.1f", xOffset, yOffset)
                    : hit.target();
            if (itemView != null && itemView.handleEvent(hit.action(), scrollTarget)) {
                rebuildLayout();
                requestRepaint();
            }
            return;
        }

        // Fallback: scroll nearest scroll container
        LayoutNode.BoxNode scrollBox = findScrollContainer(lastLayoutRoot, x, y);
        if (scrollBox != null && scrollBox.id() != null) {
            float delta = (float) (-yOffset * SCROLL_LINE_HEIGHT);
            float maxOffset = Math.max(0, scrollBox.contentHeight() - scrollBox.height());
            scrollState.scrollBy(scrollBox.id(), delta, maxOffset);
            requestRepaint();
        }
    }

    /** Find a BoxNode by ID in the layout tree. */
    private static LayoutNode.BoxNode findNodeById(LayoutNode.BoxNode root, String id) {
        if (id.equals(root.id())) return root;
        for (LayoutNode child : root.children()) {
            if (child instanceof LayoutNode.BoxNode box) {
                LayoutNode.BoxNode found = findNodeById(box, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ==================== Renderer Switching ====================

    /**
     * Switch this window's renderer (F9). Destroys the current Stage and
     * creates a new one, preserving window geometry.
     */
    public void switchRenderer(ViewConfig.RendererType newType) {
        if (newType == rendererType) return;
        if (newType == ViewConfig.RendererType.FILAMENT && !filamentAvailable) {
            log.warn("Cannot switch to Filament — not available");
            return;
        }

        int[] pos = stage.getWindowPos();
        int w = stage.width();
        int h = stage.height();

        stopLiveTimer();
        if (rendererType == ViewConfig.RendererType.FILAMENT) {
            cleanupFilament();
        } else {
            if (skiaWindow != null) {
                skiaWindow.destroy();
                skiaWindow = null;
            }
        }
        lastLayoutRoot = null;
        currentLayout = null;

        rendererType = newType;
        String title = "Common Graph";
        contextItem().ifPresent(item -> {});
        Item target = session.resolveItem(viewHandle.target()).orElse(null);
        if (target != null) title = "Common Graph - " + target.displayToken();

        if (newType == ViewConfig.RendererType.FILAMENT) {
            initFilament(title);
        } else {
            initSkia(title);
        }

        stage.setWindowPos(pos[0], pos[1]);
        stage.setWindowSize(w, h);
        stage.show();
        applyFontSize(DEFAULT_FONT_SIZE);
        rebuildLayout();
        requestRepaint();
        startLiveTimer();
    }

    // ==================== Helpers ====================

    private void requestRepaint() {
        if (stage != null) stage.requestPaint();
    }

    private void applyFontSize(float size) {
        shared.fontCache().setBaseFontSize(size);
        if (msdfFontManager != null) msdfFontManager.setBaseFontSize(size);
    }

    private void initializeInputController() {
        LibrarianHandle lib = session.librarian();
        if (lib == null) return;
        inputController = InputController.builder()
                .lookup(text -> lib.prefix(text, 20).toList())
                .librarian(lib)
                .context(session.resolveItem(viewHandle.target()).orElse(null))
                .session(session)
                .prompt(session.buildPrompt())
                .hint("")
                .onChange(snapshot -> {
                    if (itemView != null) itemView.updateInput(snapshot);
                    rebuildLayout();
                    requestRepaint();
                })
                .onNavigate(item -> {
                    // Open a new view — never replace the current window's item
                    session.openView(item.iid());
                })
                .onResult(result -> {
                    session.setLastDispatchedText(inputController.lastSubmittedText());
                    session.handleInputResult(result);
                    rebuildLayout();
                    requestRepaint();
                })
                .build();
    }

    private void applyScrollState(LayoutNode node) {
        if (node instanceof LayoutNode.BoxNode box) {
            if (box.isScrollContainer() && box.id() != null) {
                float viewportH = box.height();
                float contentH = box.contentHeight();
                float maxOffset = Math.max(0, contentH - viewportH);
                float offset = scrollState.getScrollY(box.id());
                offset = Math.max(0, Math.min(maxOffset, offset));
                scrollState.setScrollY(box.id(), offset);
                box.scrollOffsetY(offset);
            }
            for (LayoutNode child : box.children()) {
                applyScrollState(child);
            }
        }
    }

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

    private LayoutNode.BoxNode findScrollContainer(LayoutNode node, float x, float y) {
        if (!(node instanceof LayoutNode.BoxNode box)) return null;
        if (box.isScrollContainer() && box.id() != null
                && x >= box.x() && x <= box.x() + box.width()
                && y >= box.y() && y <= box.y() + box.height()) {
            return box;
        }
        float testY = y;
        if (box.isScrollContainer()) testY += box.scrollOffsetY();
        for (int i = box.children().size() - 1; i >= 0; i--) {
            LayoutNode.BoxNode found = findScrollContainer(box.children().get(i), x, testY);
            if (found != null) return found;
        }
        return null;
    }

    private void paintResizeGripSkia(io.github.humbleui.skija.Canvas canvas) {
        if (resizeController != null && stage != null) {
            resizeController.paintGrip(stage.width(), stage.height(),
                    (gx, gy, size, argbColor) -> {
                        var paint = new io.github.humbleui.skija.Paint();
                        paint.setColor(argbColor);
                        canvas.drawCircle(gx, gy, size / 2f, paint);
                        paint.close();
                    });
        }
    }

    private void repaintGrip() {
        // Filament grip overlay — only available with MSDF
        if (gripPainter != null && stage != null) {
            gripPainter.clear();
            gripPainter.configureForLayout(stage.width(), stage.height(), 2.0f, 1.0f);
            // The grip is painted by the SkiaPainter during full repaint
        }
        requestRepaint();
    }

    private void startLiveTimer() {
        stopLiveTimer();
        contextItem().ifPresent(item -> tickRegistry.rebuild(item.frames()));
        liveTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "view-window-timer-" + frameKey.toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
        liveTimer.scheduleAtFixedRate(() -> {
            try {
                boolean ticked = tickRegistry.tickAll();
                if (ticked) {
                    if (rendererType == ViewConfig.RendererType.FILAMENT) {
                        filamentLayoutDirty = true;
                    }
                    requestRepaint();
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

    private void cleanupFilament() {
        uiPane = null;
        detailPane = null;
        if (gripPainter != null) { gripPainter.destroy(); gripPainter = null; }
        if (panelPainter != null) { panelPainter.destroy(); panelPainter = null; }
        if (detailPainter != null) { detailPainter.destroy(); detailPainter = null; }
        if (sceneRenderer != null) { sceneRenderer.destroy(); sceneRenderer = null; }
        if (msdfFontManager != null) { msdfFontManager.destroy(); msdfFontManager = null; }
        if (openAL != null) { openAL.close(); openAL = null; }
        if (filamentWindow != null) { filamentWindow.destroy(); filamentWindow = null; }
    }

    private static boolean checkFilamentAvailable() {
        try {
            Class.forName("dev.everydaythings.filament.Filament");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
