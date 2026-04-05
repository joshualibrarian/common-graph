package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.DisplayConfig;
import dev.everydaythings.graph.frame.DisplayLayoutConfig;
import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.ui.GlfwLifecycle;
import dev.everydaythings.graph.ui.filament.FilamentContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVidMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-window graphical session.
 *
 * <p>Each open view ({@code ITEM_VIEW} frame on the session) maps to a
 * {@link ViewWindow} — an independent OS window with its own renderer,
 * {@link dev.everydaythings.graph.ui.scene.surface.item.ItemModel ItemModel},
 * and {@link dev.everydaythings.graph.parse.InputController InputController}.
 *
 * <p>Windows can use different renderers (Skia 2D or Filament 3D) and the
 * user switches per-window with F9. Renderer resources are initialized lazily —
 * if all windows are Skia, the Filament engine never starts.
 *
 * <p>The central event loop calls {@code glfwPollEvents()} once per frame
 * (which dispatches to all GLFW windows), then ticks each ViewWindow.
 * The session survives closing all windows.
 */
public class GraphicalSession extends Session {

    private static final Logger log = LogManager.getLogger(GraphicalSession.class);

    // ==================== Multi-Window State ====================

    private SharedResources shared;
    private FilamentContext filamentContext;
    private final Map<FrameKey, ViewWindow> windows = new LinkedHashMap<>();
    private boolean sessionAlive = true;

    /** Default renderer for new windows (from constructor arg). */
    private final ViewConfig.RendererType defaultRenderer;

    /** Local display layouts for view routing. */
    private List<DisplayLayoutConfig> localDisplays = List.of();

    /** Primary display Ref — compound ref to the first enumerated display on the host. */
    private Ref primaryDisplayRef;

    // ==================== Constructor ====================

    public GraphicalSession(LibrarianHandle librarian, Ref context, RenderMode initialMode) {
        super(librarian, context);
        this.defaultRenderer = (initialMode == RenderMode.SPATIAL)
                ? ViewConfig.RendererType.FILAMENT
                : ViewConfig.RendererType.SKIA;
    }

    // ==================== Lifecycle ====================

    @Override
    public int run() {
        log.info("Starting GraphicalSession (default renderer: {})", defaultRenderer);

        try {
            shared = new SharedResources();
            filamentContext = new FilamentContext();

            // Resolve initial context item
            Item ctx = contextItem()
                    .or(() -> librarian.get(librarian.iid()))
                    .orElse(null);
            if (ctx == null) {
                System.err.println("No context item available.");
                return 1;
            }

            // Enumerate monitors and register display frames
            GlfwLifecycle.acquire();
            enumerateAndRegisterDisplays();

            // Open initial view — openView creates the ITEM_VIEW frame,
            // which automatically triggers onViewOpened → openWindowForView
            openView(ctx.iid());
            try {
                runEventLoop();
            } finally {
                destroyAllWindows();
                GlfwLifecycle.release();
            }

            return 0;
        } catch (Exception e) {
            log.error("Graphical session failed", e);
            return 1;
        } finally {
            if (shared != null) shared.destroy();
            if (filamentContext != null) filamentContext.destroy();
        }
    }

    /**
     * Central event loop — drives all ViewWindows from a single glfwPollEvents.
     *
     * <p>Each iteration: poll GLFW events (dispatches to all windows),
     * tick each window (render if dirty), remove closed windows.
     * Session survives closing all windows — idles until new views open.
     */
    private void runEventLoop() {
        while (sessionAlive) {
            org.lwjgl.glfw.GLFW.glfwPollEvents();

            // Create any deferred windows before ticking
            FrameKey pendingKey;
            while ((pendingKey = pendingWindowCreations.poll()) != null) {
                openWindowForView(pendingKey);
            }

            var toRemove = new ArrayList<FrameKey>();
            for (var entry : windows.entrySet()) {
                if (!entry.getValue().tick()) {
                    toRemove.add(entry.getKey());
                }
            }

            for (FrameKey key : toRemove) {
                closeWindow(key);
            }

            if (windows.isEmpty()) {
                org.lwjgl.glfw.GLFW.glfwWaitEventsTimeout(0.1);
            } else if (!pendingWindowCreations.isEmpty()) {
                // Pending work — don't wait
            } else {
                // No pending work — wait for events instead of busy-spinning.
                // Short timeout ensures timers and animations still tick.
                org.lwjgl.glfw.GLFW.glfwWaitEventsTimeout(1.0 / 60.0);
            }
        }
    }

    @Override
    protected void render() {
        for (ViewWindow window : windows.values()) {
            window.rebuildLayout();
        }
    }

    @Override
    protected void output(String message) {
        log.info(message);
    }

    @Override
    public void close() {
        sessionAlive = false;
        destroyAllWindows();
        super.close();
    }

    // ==================== Window Management ====================

    /**
     * Create and show a ViewWindow for the given ITEM_VIEW frame.
     */
    void openWindowForView(FrameKey key) {
        if (windows.containsKey(key)) return;

        ViewHandle vh = findViewByKey(key);
        if (vh == null) {
            log.warn("No ViewHandle for frame key {}", key);
            return;
        }

        // Apply default renderer if the ViewConfig doesn't specify one
        if (vh.config().renderer() == null) {
            ViewConfig updated = vh.config().withRenderer(defaultRenderer);
            updateViewConfig(key, updated);
            vh = vh.withConfig(updated);
        }

        try {
            ViewWindow window = new ViewWindow(key, vh, this, shared, filamentContext, coordinateMapper());
            window.init();
            windows.put(key, window);
            log.info("Opened ViewWindow for {} (renderer: {})", vh.target(), window.rendererType());
        } catch (Throwable t) {
            log.error("Failed to open ViewWindow for {}: {}", vh.target(), t.getMessage(), t);
        }
    }

    private void closeWindow(FrameKey key) {
        ViewWindow window = windows.remove(key);
        if (window != null) {
            window.syncConfigToFrame();
            window.destroy();
            log.info("Closed ViewWindow for frame {}", key);
        }
    }

    private void destroyAllWindows() {
        for (ViewWindow window : windows.values()) {
            window.syncConfigToFrame();
            window.destroy();
        }
        windows.clear();
    }

    private ViewHandle findViewByKey(FrameKey key) {
        for (ViewHandle vh : openViews()) {
            if (vh.frameKey().equals(key)) return vh;
        }
        return null;
    }

    // ==================== Display Enumeration ====================

    /**
     * Enumerate connected monitors and register DISPLAY frames on the Host
     * and DISPLAY_LAYOUT frames on this Session.
     *
     * <p>For single-host, session-space = OS-space (identity transform).
     */
    private void enumerateAndRegisterDisplays() {
        Host host = librarian.host().orElse(null);
        if (host == null) {
            log.debug("No local host — skipping display enumeration");
            return;
        }

        ItemID hostId = host.iid();

        // Enumerate OS monitors (ui-layer concern — GLFW)
        List<Host.DisplayUpdate> updates = new ArrayList<>();
        List<DisplayLayoutConfig> layouts = new ArrayList<>();

        PointerBuffer monitors = org.lwjgl.glfw.GLFW.glfwGetMonitors();
        if (monitors == null) {
            log.debug("No monitors detected");
            return;
        }

        for (int i = 0; i < monitors.limit(); i++) {
            long monitor = monitors.get(i);

            String name = org.lwjgl.glfw.GLFW.glfwGetMonitorName(monitor);
            if (name == null) name = "monitor-" + i;

            GLFWVidMode mode = org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor);
            int width = mode != null ? mode.width() : 0;
            int height = mode != null ? mode.height() : 0;
            int refreshRate = mode != null ? mode.refreshRate() : 0;

            int[] posX = new int[1], posY = new int[1];
            org.lwjgl.glfw.GLFW.glfwGetMonitorPos(monitor, posX, posY);

            float[] scaleX = new float[1], scaleY = new float[1];
            org.lwjgl.glfw.GLFW.glfwGetMonitorContentScale(monitor, scaleX, scaleY);

            String displayId = name + "-" + i;

            DisplayConfig displayConfig = DisplayConfig.builder()
                    .name(name)
                    .widthPx(width)
                    .heightPx(height)
                    .refreshRate(refreshRate)
                    .scalePercent(Math.round(scaleX[0] * 100))
                    .osX(posX[0])
                    .osY(posY[0])
                    .build();

            updates.add(new Host.DisplayUpdate(displayId, displayConfig));

            // Single-host: session-space = OS-space (identity)
            layouts.add(DisplayLayoutConfig.builder()
                    .hostId(hostId)
                    .displayId(displayId)
                    .sessionX(posX[0])
                    .sessionY(posY[0])
                    .widthPx(width)
                    .heightPx(height)
                    .build());

            log.info("Detected display: {} ({}x{} @{}Hz, scale={}, pos={},{})",
                    name, width, height, refreshRate, scaleX[0], posX[0], posY[0]);
        }

        // Tell the Host about connected displays (core-layer concern)
        host.updateDisplays(updates);

        // Track primary display as compound Ref (host + device frame key)
        List<Host.DeviceInfo> hostDisplays = host.displays();
        if (!hostDisplays.isEmpty()) {
            primaryDisplayRef = hostDisplays.getFirst().refOn(hostId);
        }

        registerHostDisplays(hostId, layouts);
        localDisplays = List.copyOf(layouts);
    }

    // ==================== View-to-Display Routing ====================

    /**
     * Check whether a view is assigned to a local display.
     *
     * <p>Uses the LOCATION binding on the ITEM_VIEW frame to determine
     * which display the view is on. Falls back to geometry intersection
     * when no LOCATION binding exists (legacy views).
     *
     * <p>Returns true (show locally) when:
     * <ul>
     *   <li>No display info available (single-host, no enumeration)</li>
     *   <li>View has a LOCATION matching a local display</li>
     *   <li>View has no LOCATION — falls back to geometry intersection</li>
     * </ul>
     */
    private boolean isViewOnLocalDisplay(ViewHandle vh) {
        if (localDisplays.isEmpty()) return true;

        // Check LOCATION binding — compound Ref to host + device frame
        if (vh.display() != null && primaryDisplayRef != null) {
            // For single-host, all local displays are ours
            // Multi-host: check if vh.display().target() matches the local host IID
            return true;
        }

        // Legacy fallback: geometry intersection
        ViewConfig config = vh.config();
        if (config.width() == 0) return true;
        for (DisplayLayoutConfig display : localDisplays) {
            if (display.intersects(config.x(), config.y(), config.width(), config.height())) {
                return true;
            }
        }
        return false;
    }

    // ==================== Coordinate Transform ====================

    /**
     * Functional interface for coordinate mapping between session-space and OS-space.
     */
    public interface CoordinateMapper {
        int[] sessionToOs(int sessionX, int sessionY);
        int[] osToSession(int osX, int osY);
    }

    /**
     * Identity mapper — session-space equals OS-space (single-host case).
     */
    private static final CoordinateMapper IDENTITY_MAPPER = new CoordinateMapper() {
        @Override public int[] sessionToOs(int sessionX, int sessionY) {
            return new int[]{sessionX, sessionY};
        }
        @Override public int[] osToSession(int osX, int osY) {
            return new int[]{osX, osY};
        }
    };

    /**
     * Get the coordinate mapper for this session.
     *
     * <p>For single-host, returns identity. Multi-host would compute
     * offsets from display layout positions.
     */
    CoordinateMapper coordinateMapper() {
        return IDENTITY_MAPPER;
    }

    @Override
    public Ref focusedDisplay() {
        // TODO: track which window has focus and return its display
        // For now, return the primary display
        return primaryDisplayRef;
    }

    // ==================== View Lifecycle Hooks ====================

    /** Pending window creations — deferred to avoid creating windows mid-render. */
    private final java.util.Queue<FrameKey> pendingWindowCreations = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @Override
    protected void onViewOpened(FrameKey key) {
        if (shared != null) {
            ViewHandle vh = findViewByKey(key);
            if (vh != null && !isViewOnLocalDisplay(vh)) {
                log.debug("View {} not on local display — skipping window creation", key);
                return;
            }
            // Defer window creation to the next event loop iteration to avoid
            // creating Filament swap chains during another window's render cycle.
            pendingWindowCreations.add(key);
        }
    }

    @Override
    protected void onViewClosed(FrameKey key) {
        closeWindow(key);
    }

    /**
     * Request session exit — the central event loop will terminate.
     */
    public void requestExit() {
        sessionAlive = false;
    }
}
