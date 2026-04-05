package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.frame.TickRegistry;
import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.parse.InputController;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.ui.WindowDragController;
import dev.everydaythings.graph.ui.WindowResizeController;
import dev.everydaythings.graph.ui.filament.*;
import dev.everydaythings.graph.ui.scene.AnimationState;
import dev.everydaythings.graph.ui.scene.InteractionState;
import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.SceneResolver;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.skia.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;
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
    // TODO: restore Filament 3D rendering on SceneNode
    private FilamentSpatialPainter spatialPainter;

    private dev.everydaythings.graph.ui.audio.OpenALAudio openAL;
    private Ref sceneContextRef;
    private boolean sceneDirty;
    private float sceneFocusExtent = Float.NaN;
    private boolean flatMode = true;
    private boolean filamentLayoutDirty;

    // ==================== Skia Path ====================

    private SkiaWindow skiaWindow;
    

    // ==================== Window Chrome ====================

    private WindowDragController dragController;
    private WindowResizeController resizeController;
    
    private double lastCursorX, lastCursorY;

    // ==================== Layout ====================

    

    // ==================== Per-Window UI State ====================

    private dev.everydaythings.graph.ui.scene.surface.item.ItemView itemView;
    
    
    private final InteractionState interactionState = new InteractionState();

    // New pipeline (Skia mode)
    private final SceneResolver sceneResolver = new SceneResolver();
    private final dev.everydaythings.graph.ui.scene.ScenePresenter scenePresenter =
            new dev.everydaythings.graph.ui.scene.ScenePresenter();
    private dev.everydaythings.graph.ui.skia.SkiaSurfacePainter skiaSurfacePainter;
    private SceneNode lastSceneTree;
    private SceneNode currentSceneLayout;
    private InputController inputController;
    private final AnimationState animationState = new AnimationState();
    private long lastPaintNanos;
    private volatile String pendingExpression;
    private volatile ItemID pendingNavigation;
    private final dev.everydaythings.graph.ui.scene.ScrollState scrollState =
            new dev.everydaythings.graph.ui.scene.ScrollState();
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
                : ViewConfig.RendererType.SKIA;
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

        // Pipeline state management
        interactionState.onApplicationAction((nodeId, action, target) -> {
            if (itemView != null) return itemView.handleEvent(action, target);
            return false;
        });

        // Wire ItemView state changes to trigger re-render
        if (itemView != null) {
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
            // Advance animations
            long now = System.nanoTime();
            if (lastPaintNanos > 0) {
                double deltaTime = (now - lastPaintNanos) / 1_000_000_000.0;
                animationState.update(deltaTime);
            }
            lastPaintNanos = now;

            // Paint SceneNode tree
            if (currentSceneLayout != null) {
                if (skiaSurfacePainter == null) {
                    skiaSurfacePainter = new dev.everydaythings.graph.ui.skia.SkiaSurfacePainter(
                            shared.fontCache(), animationState);
                }
                skiaSurfacePainter.canvas(canvas);
                skiaSurfacePainter.paint(currentSceneLayout);
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
        // TODO: restore Filament painters on SceneNode
        uiPane.painter(new FilamentSurfacePainter());
        spatialPainter = new FilamentSpatialPainter();

        dragController = new WindowDragController(32f, stage);
        resizeController.setStage(stage);
        resizeController.onHoverChanged(this::repaintGrip);

        // TODO: restore grip painter on SceneNode

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
        if (itemView == null) {
            return;
        }

        try {
            float w, h, dpr;
            if (stage != null) {
                w = stage.width();
                h = stage.height();
                dpr = (stage instanceof SkiaWindow sw) ? sw.dpi() : 1.0f;
            } else {
                w = 800; h = 600; dpr = 1.0f;
            }

            float baseFontSize = shared.fontCache().baseFontSize();

            // SceneNode pipeline: compile → resolve → present
            SceneNode sceneTree = itemView.toSceneNode();
            if (sceneTree == null) return;

            var resolveCtx = new SceneResolver.ResolveContext(
                    session.librarian(), Set.of(":skia", "color", "mouse", "images"), interactionState);
            sceneResolver.resolve(sceneTree, resolveCtx);

            dev.everydaythings.graph.ui.scene.ScenePresenter.TextMeasurer textMeasurer =
                    shared.fontCache()::measureText;

            var presentCtx = new dev.everydaythings.graph.ui.scene.ScenePresenter.PresentContext(
                    w, h, baseFontSize, 96 * dpr, textMeasurer, interactionState);
            scenePresenter.present(sceneTree, presentCtx);

            lastSceneTree = sceneTree;
            currentSceneLayout = sceneTree;

            // Filament mode: also paint to 2D chrome pane
            if (rendererType == ViewConfig.RendererType.FILAMENT && uiPane != null && uiPane.painter() != null) {
                uiPane.painter().paint(lastSceneTree);
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

        // @Scene.On key dispatch — SceneNode tree
        if (lastSceneTree != null
                && dispatchKeyEventSceneNode(lastSceneTree, chord.toString())) {
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
        float x = (float) (cursorX * dpi);
        float y = (float) (cursorY * dpi);

        // New pipeline: SceneNode hit testing
        if (lastSceneTree != null) {
            SceneNode hit = SceneNode.hitTest(lastSceneTree, x, y);
            if (hit == null || hit.events() == null) return;
            for (var event : hit.events()) {
                if (eventType.equals(event.on())) {
                    String nodeId = hit.id() != null ? hit.id() : "";
                    boolean handled = interactionState.dispatch(nodeId, event.action(), event.target());
                    if (!handled && itemView != null) {
                        handled = itemView.handleEvent(event.action(), event.target());
                    }
                    if (handled) { rebuildLayout(); requestRepaint(); }
                    return;
                }
            }
            return;
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

    /** Dispatch a key event by walking the SceneNode tree for matching @Scene.On declarations. */
    private boolean dispatchKeyEventSceneNode(SceneNode node, String keyChord) {
        if (node == null || keyChord == null) return false;
        if (node.events() != null) {
            for (var event : node.events()) {
                if (keyChord.equals(event.on())) {
                    String nodeId = node.id() != null ? node.id() : "";
                    if (interactionState.dispatch(nodeId, event.action(), event.target())) return true;
                }
            }
        }
        if (node.children() != null) {
            for (SceneNode child : node.children()) {
                if (dispatchKeyEventSceneNode(child, keyChord)) return true;
            }
        }
        return false;
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
        // TODO: implement scroll on SceneNode
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
        
        
        lastSceneTree = null;
        currentSceneLayout = null;

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

    // TODO: restore scroll state and animations on SceneNode

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
        // TODO: restore Filament grip overlay on SceneNode
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
        spatialPainter = null;
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
