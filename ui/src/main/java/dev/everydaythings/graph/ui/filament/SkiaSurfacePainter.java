package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.graph.ui.skia.FontCache;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.skia.SkiaPainter;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ImageInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link SurfacePainter} that uses Skia for CPU rasterization, then uploads
 * the result as a Filament texture on a full-viewport quad.
 *
 * <p>Provides pixel-perfect rendering with full Skia capabilities (font
 * hinting, subpixel AA, SVG, image formats) at the cost of a CPU→GPU
 * texture upload each frame.
 *
 * @see FilamentSurfacePainter
 * @see SkiaPanel
 */
public class SkiaSurfacePainter implements SurfacePainter {

    private static final Logger log = LogManager.getLogger(SkiaSurfacePainter.class);
    private static final int BG_COLOR = 0xFF1E1E2E; // Dark background

    private final Engine engine;
    private final Scene scene;
    private final SkiaPainter painter;
    private final FontCache fontCache;

    // Coordinate mapping (mirrors FilamentSurfacePainter)
    private float widthPx, heightPx;
    private float worldWidth, centerY;
    private String skipId;

    // Skia CPU raster surface
    private Bitmap bitmap;
    private Canvas canvas;
    private ByteBuffer pixelBuffer;

    // Filament objects
    private Texture texture;
    private Material material;
    private MaterialInstance materialInstance;
    private PrimitiveMeshes.Mesh quadMesh;
    private int quadEntity;

    public SkiaSurfacePainter(Engine engine, Scene scene,
                               SkiaPainter painter, FontCache fontCache) {
        this.engine = engine;
        this.scene = scene;
        this.painter = painter;
        this.fontCache = fontCache;

        // Load the textured_unlit material
        material = loadMaterial("materials/textured_unlit.filamat");
    }

    @Override
    public void configureForLayout(float widthPx, float heightPx,
                                    float worldWidth, float centerY) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.worldWidth = worldWidth;
        this.centerY = centerY;

        int w = Math.max(1, Math.round(widthPx));
        int h = Math.max(1, Math.round(heightPx));

        // Recreate Skia surface and Filament texture if size changed
        boolean sizeChanged = (bitmap == null)
                || (bitmap.getWidth() != w)
                || (bitmap.getHeight() != h);

        if (sizeChanged) {
            destroySkiaSurface();
            destroyFilamentQuad();
            destroyFilamentTexture();

            createSkiaSurface(w, h);
            createFilamentTexture(w, h);
            createFilamentQuad(widthPx, heightPx, worldWidth, centerY);
        }
    }

    @Override
    public void skipId(String id) {
        this.skipId = id;
        // SkiaPainter doesn't support skipId natively — the layout tree
        // should already be pruned by the caller if needed.
        // For now this is a no-op; the uiPane uses skipId to avoid
        // painting under 3D, but SkiaSurfacePainter is only used in flat mode.
    }

    @Override
    public void paint(LayoutNode.BoxNode root, float z) {
        if (canvas == null || texture == null) return;

        // Clear and render via Skia
        canvas.clear(BG_COLOR);
        painter.paint(canvas, root);

        // Upload pixels to Filament texture
        uploadPixels();

        // Ensure quad is in scene
        if (quadEntity != 0) {
            scene.addEntity(quadEntity);
        }
    }

    @Override
    public void clear() {
        // Remove quad from scene (but keep resources for reuse)
        if (quadEntity != 0) {
            scene.removeEntity(quadEntity);
        }
    }

    @Override
    public void destroy() {
        clear();
        destroyFilamentQuad();
        destroyFilamentTexture();
        destroySkiaSurface();

        if (material != null) {
            engine.destroyMaterial(material);
            material = null;
        }
    }

    @Override
    public LayoutEngine.TextMeasurer textMeasurer() {
        return fontCache;
    }

    // ==================== Private: Skia ====================

    private void createSkiaSurface(int w, int h) {
        ImageInfo info = new ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        bitmap = new Bitmap();
        bitmap.allocPixels(info);
        canvas = new Canvas(bitmap);
        pixelBuffer = ByteBuffer.allocateDirect(w * h * 4)
                .order(ByteOrder.nativeOrder());
        log.debug("SkiaSurfacePainter: raster surface created {}x{}", w, h);
    }

    private void destroySkiaSurface() {
        if (canvas != null) { canvas.close(); canvas = null; }
        if (bitmap != null) { bitmap.close(); bitmap = null; }
        pixelBuffer = null;
    }

    // ==================== Private: Filament Texture ====================

    private void createFilamentTexture(int w, int h) {
        texture = new Texture.Builder()
                .width(w)
                .height(h)
                .format(Texture.InternalFormat.RGBA8)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .levels(1)
                .usage(Texture.Usage.DEFAULT)
                .build(engine);

        // Create/recreate material instance with texture binding
        if (materialInstance != null) {
            engine.destroyMaterialInstance(materialInstance);
        }
        materialInstance = material.createInstance();

        TextureSampler sampler = new TextureSampler();
        sampler.setMagFilter(TextureSampler.MagFilter.NEAREST);
        sampler.setMinFilter(TextureSampler.MinFilter.NEAREST);
        materialInstance.setParameter("baseColorMap", texture, sampler);
    }

    private void destroyFilamentTexture() {
        if (materialInstance != null) {
            engine.destroyMaterialInstance(materialInstance);
            materialInstance = null;
        }
        if (texture != null) {
            engine.destroyTexture(texture);
            texture = null;
        }
    }

    // ==================== Private: Filament Quad ====================

    private void createFilamentQuad(float widthPx, float heightPx,
                                     float worldWidth, float centerY) {
        float aspect = heightPx / widthPx;
        float quadW = worldWidth;
        float quadH = worldWidth * aspect;

        quadMesh = PrimitiveMeshes.createQuad(engine, quadW, quadH);

        quadEntity = EntityManager.get().create();
        new RenderableManager.Builder(1)
                .boundingBox(quadMesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        quadMesh.vertexBuffer(), quadMesh.indexBuffer(),
                        0, quadMesh.indexCount())
                .material(0, materialInstance)
                .culling(false)
                .build(engine, quadEntity);

        // Position the quad: center at (0, centerY, 0)
        // Transparent material requires TransformManager
        TransformManager tm = engine.getTransformManager();
        tm.create(quadEntity);
        tm.setTransform(tm.getInstance(quadEntity), new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, centerY, 0, 1
        });
    }

    private void destroyFilamentQuad() {
        if (quadEntity != 0) {
            scene.removeEntity(quadEntity);
            engine.destroyEntity(quadEntity);
            quadEntity = 0;
        }
        if (quadMesh != null) {
            quadMesh.destroy(engine);
            quadMesh = null;
        }
    }

    // ==================== Private: Pixel Upload ====================

    private void uploadPixels() {
        byte[] pixels = bitmap.readPixels();
        if (pixels == null || pixels.length == 0) return;

        if (pixelBuffer.capacity() < pixels.length) {
            pixelBuffer = ByteBuffer.allocateDirect(pixels.length)
                    .order(ByteOrder.nativeOrder());
        }
        pixelBuffer.clear();
        pixelBuffer.put(pixels);
        pixelBuffer.flip();

        texture.setImage(engine, 0,
                new Texture.PixelBufferDescriptor(
                        pixelBuffer,
                        Texture.Format.RGBA,
                        Texture.Type.UBYTE
                ));
    }

    // ==================== Private: Material Loading ====================

    private Material loadMaterial(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Material resource not found: " + resourcePath);
            }
            byte[] data = in.readAllBytes();
            ByteBuffer buf = ByteBuffer.allocateDirect(data.length)
                    .order(ByteOrder.nativeOrder());
            buf.put(data);
            buf.flip();
            return new Material.Builder()
                    .payload(buf, buf.remaining())
                    .build(engine);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load material: " + resourcePath, e);
        }
    }
}
