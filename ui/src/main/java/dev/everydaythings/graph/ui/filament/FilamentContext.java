package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.filament.gltfio.Gltfio;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.system.Platform.*;

/**
 * Shared Filament rendering context across all Filament-backed ViewWindows.
 *
 * <p>A Filament {@link Engine} is heavyweight (GPU state, driver arena, command
 * buffers). One Engine can render to multiple windows by using different
 * {@link SwapChain}s per window. The {@link Renderer} calls
 * {@code beginFrame(swapChain)} / {@code render(view)} / {@code endFrame()}
 * for each window in sequence.
 *
 * <p>Initialization is lazy — the Engine is created on the first call to
 * {@link #engine()}, not at construction time. This means a session that
 * only opens Skia windows never pays the cost of Filament initialization.
 *
 * <p>All methods must be called from the main thread.
 */
public class FilamentContext {

    private static final Logger log = LogManager.getLogger(FilamentContext.class);

    private Engine engine;
    private int windowCount;
    private final Map<String, Material> materialCache = new ConcurrentHashMap<>();

    /**
     * Get the shared Filament Engine, initializing lazily on first call.
     */
    public Engine engine() {
        if (engine == null) {
            initFilament();
        }
        return engine;
    }

    /**
     * Whether the Filament Engine has been initialized.
     */
    public boolean isInitialized() {
        return engine != null;
    }


    /**
     * Register a new Filament window. Call when creating a Filament-backed ViewWindow.
     */
    public void acquireWindow() {
        windowCount++;
    }

    /**
     * Create a SwapChain for a native window handle.
     *
     * @param nativeWindow the platform-specific native window pointer
     *                     (from {@code glfwGetCocoaWindow()} or {@code glfwGetX11Window()})
     */
    public SwapChain createSwapChain(long nativeWindow) {
        return engine().createSwapChainFromRawPointer(nativeWindow, 0);
    }

    /**
     * Destroy a SwapChain when a Filament window closes.
     * If this was the last Filament window, shuts down the Engine.
     */
    public void releaseWindow(SwapChain swapChain) {
        if (engine == null) return;

        if (swapChain != null) {
            engine.destroySwapChain(swapChain);
        }
        windowCount--;

        if (windowCount <= 0) {
            shutdown();
        }
    }

    /**
     * Force shutdown regardless of window count. Call at session end.
     */
    public void destroy() {
        if (engine != null) {
            shutdown();
        }
    }

    /**
     * Get or load a Material from the classpath, cached per resource path.
     * Materials are engine-level GPU resources and safe to share across windows.
     */
    public Material material(String resourcePath) {
        return materialCache.computeIfAbsent(resourcePath, path -> {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) throw new RuntimeException("Material not found: " + path);
                byte[] data = in.readAllBytes();
                ByteBuffer buf = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
                buf.put(data).flip();
                Material mat = new Material.Builder().payload(buf, buf.remaining()).build(engine());
                log.debug("Loaded material '{}': {} bytes", path, data.length);
                return mat;
            } catch (Exception e) {
                throw new RuntimeException("Failed to load material: " + path, e);
            }
        });
    }

    private void initFilament() {
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
    }

    private void shutdown() {
        for (Material mat : materialCache.values()) {
            engine.destroyMaterial(mat);
        }
        materialCache.clear();
        engine.flushAndWait();
        engine.destroy();
        engine = null;
        windowCount = 0;
        log.info("Filament context shut down");
    }
}
