package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;

/**
 * A single rendering pane within a {@link FilamentWindow}.
 *
 * <p>Each pane owns its own Filament View, Scene, and Camera — enabling
 * multi-view rendering where different regions of the window show different
 * content with independent camera projections.
 *
 * <p>Two pane types:
 * <ul>
 *   <li><b>Orthographic</b> — pixel-perfect 2D rendering (handle, tree, prompt)</li>
 *   <li><b>Perspective</b> — 3D rendering with orbit/fly camera (detail pane)</li>
 * </ul>
 *
 * @see FilamentWindow
 */
public class FilamentPane {

    private final View view;
    private final Scene scene;
    private final Camera camera;
    private final int cameraEntity;
    private final boolean perspective;
    private int viewportLeft;
    private int viewportBottom;
    private int viewportWidth;
    private int viewportHeight;

    private SurfacePainter painter;
    private boolean fullWindow;

    FilamentPane(Engine engine, boolean perspective) {
        this.perspective = perspective;
        this.scene = engine.createScene();
        this.view = engine.createView();
        this.cameraEntity = engine.getEntityManager().create();
        this.camera = engine.createCamera(cameraEntity);

        view.setScene(scene);
        view.setCamera(camera);
    }

    /**
     * Set the viewport rectangle for this pane.
     *
     * @param left   left edge in framebuffer pixels
     * @param bottom bottom edge in framebuffer pixels (OpenGL convention: 0 = bottom)
     * @param width  width in framebuffer pixels
     * @param height height in framebuffer pixels
     */
    public void setViewport(int left, int bottom, int width, int height) {
        this.viewportLeft = left;
        this.viewportBottom = bottom;
        this.viewportWidth = width;
        this.viewportHeight = height;
        view.setViewport(new Viewport(left, bottom, width, height));
    }

    /**
     * Configure this pane for orthographic 2D rendering.
     * Sets up the camera, anti-aliasing, and tone mapping for pixel-perfect UI.
     *
     * <p>The orthographic projection maps to the same coordinate space used by
     * {@link FilamentSurfacePainter}: a 2-unit-wide region centered at (0, 1.0, 0).
     *
     * @param aspect width/height ratio of the viewport
     */
    public void configureOrtho(double aspect) {
        view.setBlendMode(View.BlendMode.OPAQUE);
        view.setAntiAliasing(View.AntiAliasing.NONE);
        view.setDithering(View.Dithering.NONE);
        view.setToneMapping(View.ToneMapping.LINEAR);
        // Disable post-processing for 2D UI: bypasses gamma encoding so sRGB
        // vertex colors pass through directly to the framebuffer, matching Skia.
        view.setPostProcessingEnabled(false);
        camera.setExposure(1.0f);

        double halfW = 1.0;
        double halfH = 1.0 / aspect;
        camera.setProjection(Camera.Projection.ORTHO,
                -halfW, halfW, -halfH, halfH, 0.1, 100.0);
        camera.lookAt(0, 1.0, 5.0,   // eye
                      0, 1.0, 0,      // target
                      0, 1, 0);       // up
    }

    /**
     * Configure this pane for perspective 3D rendering.
     * Uses temporal dithering for better visual quality with 3D content.
     */
    public void configurePerspective() {
        view.setBlendMode(View.BlendMode.OPAQUE);
        view.setAntiAliasing(View.AntiAliasing.NONE);
        view.setDithering(View.Dithering.TEMPORAL);
        view.setToneMapping(View.ToneMapping.LINEAR);
        // Disable post-processing so sRGB vertex colors pass through directly,
        // matching the ortho pane and Skia renderer color output.
        view.setPostProcessingEnabled(false);
        // Critical: depth must be cleared each frame, especially after swapchain / renderer switches,
        // otherwise we get trails and flashing artifacts in the 3D detail pane.
        view.setChannelDepthClearEnabled(0, true);
        camera.setExposure(1.0f);
    }

    /**
     * Destroy all Filament resources owned by this pane.
     * Must be called before the Engine is destroyed.
     */
    public void destroy(Engine engine) {
        if (painter != null) {
            painter.destroy();
            painter = null;
        }
        engine.destroyView(view);
        engine.destroyScene(scene);
        engine.destroyCameraComponent(cameraEntity);
    }

    // ==================== Accessors ====================

    public View view() { return view; }
    public Scene scene() { return scene; }
    public Camera camera() { return camera; }
    public int cameraEntity() { return cameraEntity; }
    public boolean perspective() { return perspective; }
    public SurfacePainter painter() { return painter; }
    public void painter(SurfacePainter painter) { this.painter = painter; }
    public int viewportLeft() { return viewportLeft; }
    public int viewportBottom() { return viewportBottom; }
    public int viewportWidth() { return viewportWidth; }
    public int viewportHeight() { return viewportHeight; }

    /** Mark this pane as tracking the full window size (auto-resized by FilamentWindow). */
    public void markFullWindow() { this.fullWindow = true; }
    /** Clear full-window tracking (e.g., when setting a sub-region viewport). */
    public void clearFullWindow() { this.fullWindow = false; }
    public boolean isFullWindow() { return fullWindow; }

    public double viewportAspect() {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return 1.0;
        }
        return (double) viewportWidth / viewportHeight;
    }
}
