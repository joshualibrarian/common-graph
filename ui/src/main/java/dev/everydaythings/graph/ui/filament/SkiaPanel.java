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
 * Bridges Skia 2D rendering into a Filament 3D scene.
 *
 * <p>Renders 2D UI content using Skia's CPU raster backend into a bitmap,
 * then uploads those pixels to a Filament texture displayed on a quad
 * in the 3D scene.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>SPATIAL</b> — 3x supersampled, mipmapped, anisotropic filtering (for 3D scene)</li>
 *   <li><b>FLAT</b> — 1:1 pixel mapping, NEAREST filtering (for orthographic 2D view)</li>
 * </ul>
 */
public class SkiaPanel {

    private static final Logger log = LogManager.getLogger(SkiaPanel.class);
    private static final int BG_COLOR = 0xFF1E1E2E; // Dark background

    private static final float SPATIAL_SCALE = 3.0f;
    private static final float FLAT_SCALE = 1.0f;

    private final Engine engine;
    private final SkiaPainter painter;

    /** Current scale factor (mutable — changes with mode). */
    private float scaleFactor = SPATIAL_SCALE;

    /** Logical size (window pixels). */
    private int width;
    private int height;

    /** Physical size (Skia bitmap pixels = logical * scaleFactor). */
    private int texWidth;
    private int texHeight;

    // Skia CPU raster surface
    private Bitmap bitmap;
    private Canvas canvas;

    // Filament objects
    private Texture texture;
    private Material material;
    private MaterialInstance materialInstance;
    private PrimitiveMeshes.Mesh quadMesh;
    private int quadEntity;

    // Pixel transfer buffer (reused between frames)
    private ByteBuffer pixelBuffer;

    /**
     * Create a SkiaPanel that renders 2D content as a textured quad.
     *
     * @param engine    Filament engine
     * @param painter   Skia painter for 2D rendering
     * @param width     panel width in logical pixels
     * @param height    panel height in logical pixels
     */
    public SkiaPanel(Engine engine, SkiaPainter painter, int width, int height) {
        this.engine = engine;
        this.painter = painter;
        this.width = width;
        this.height = height;
        this.texWidth = Math.round(width * scaleFactor);
        this.texHeight = Math.round(height * scaleFactor);

        createSkiaSurface();
        createFilamentResources();
    }

    /**
     * Update the panel with new layout content.
     * Re-renders Skia content and uploads to the Filament texture.
     */
    public void update(LayoutNode.BoxNode layout) {
        // Clear and render at current scale
        canvas.clear(BG_COLOR);
        if (layout != null) {
            canvas.save();
            canvas.scale(scaleFactor, scaleFactor);
            painter.paint(canvas, layout);
            canvas.restore();
        }

        // Upload pixels to Filament texture
        uploadPixels();
    }

    /**
     * Get the Filament entity for this panel (add to scene).
     */
    public int entity() {
        return quadEntity;
    }

    /**
     * Resize the panel. Recreates Skia surface and Filament texture.
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        width = newWidth;
        height = newHeight;
        texWidth = Math.round(newWidth * scaleFactor);
        texHeight = Math.round(newHeight * scaleFactor);

        destroySkiaSurface();
        destroyFilamentTexture();

        createSkiaSurface();
        createFilamentTexture();
        rebindTexture();
    }

    /**
     * Configure texture parameters for FLAT or SPATIAL mode.
     *
     * <p>FLAT: scaleFactor=1.0, NEAREST filtering, no mipmaps.
     * Texels map 1:1 to window pixels for pixel-perfect rendering.
     *
     * <p>SPATIAL: scaleFactor=3.0, NEAREST mag + LINEAR_MIPMAP_LINEAR min,
     * 8x anisotropy, mipmaps generated.
     */
    public void configureForMode(boolean flat) {
        float newScale = flat ? FLAT_SCALE : SPATIAL_SCALE;
        boolean scaleChanged = (newScale != scaleFactor);
        scaleFactor = newScale;

        if (scaleChanged) {
            // Scale changed — recreate Skia surface and Filament texture at new resolution
            texWidth = Math.round(width * scaleFactor);
            texHeight = Math.round(height * scaleFactor);

            destroySkiaSurface();
            destroyFilamentTexture();

            createSkiaSurface();
            createFilamentTexture();
        }

        // Rebind with mode-appropriate sampler
        rebindTexture();
    }

    /**
     * Clean up all resources.
     */
    public void destroy() {
        destroySkiaSurface();

        // Entity must be destroyed before material/texture (Filament ownership rules)
        if (quadEntity != 0) { engine.destroyEntity(quadEntity); quadEntity = 0; }
        if (quadMesh != null) { quadMesh.destroy(engine); quadMesh = null; }
        if (materialInstance != null) { engine.destroyMaterialInstance(materialInstance); materialInstance = null; }
        if (material != null) { engine.destroyMaterial(material); material = null; }
        if (texture != null) { engine.destroyTexture(texture); texture = null; }
    }

    // ==================== Private: Skia ====================

    private void createSkiaSurface() {
        ImageInfo info = new ImageInfo(texWidth, texHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        bitmap = new Bitmap();
        bitmap.allocPixels(info);
        canvas = new Canvas(bitmap);
        pixelBuffer = ByteBuffer.allocateDirect(texWidth * texHeight * 4)
                .order(ByteOrder.nativeOrder());
        log.debug("Skia raster surface created: {}x{} (logical {}x{}, scale {}x)",
                texWidth, texHeight, width, height, scaleFactor);
    }

    private void destroySkiaSurface() {
        if (canvas != null) {
            canvas.close();
            canvas = null;
        }
        if (bitmap != null) {
            bitmap.close();
            bitmap = null;
        }
        pixelBuffer = null;
    }

    // ==================== Private: Filament ====================

    private void createFilamentResources() {
        // Load textured_unlit material
        material = loadMaterial("materials/textured_unlit.filamat");

        // Create texture
        createFilamentTexture();

        // Create material instance and bind texture
        materialInstance = material.createInstance();
        rebindTexture();

        // Create quad mesh
        // Normalize to Filament coordinates: 2 units wide, proportional height
        float aspect = (float) height / width;
        quadMesh = PrimitiveMeshes.createQuad(engine, 2.0f, 2.0f * aspect);

        // Create renderable entity
        quadEntity = EntityManager.get().create();
        new RenderableManager.Builder(1)
                .boundingBox(quadMesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        quadMesh.vertexBuffer(), quadMesh.indexBuffer(), 0, quadMesh.indexCount())
                .material(0, materialInstance)
                .culling(false)
                .build(engine, quadEntity);
    }

    private void createFilamentTexture() {
        var builder = new Texture.Builder()
                .width(texWidth)
                .height(texHeight)
                .format(Texture.InternalFormat.SRGB8_A8)
                .sampler(Texture.Sampler.SAMPLER_2D);

        if (scaleFactor > 1.0f) {
            // SPATIAL: full mip chain for distance viewing
            builder.levels(0xFF)
                   .usage(Texture.Usage.DEFAULT | Texture.Usage.GEN_MIPMAPPABLE);
        } else {
            // FLAT: single level, no mipmaps needed
            builder.levels(1)
                   .usage(Texture.Usage.DEFAULT);
        }

        texture = builder.build(engine);
    }

    private void destroyFilamentTexture() {
        if (texture != null) {
            engine.destroyTexture(texture);
            texture = null;
        }
    }

    private void rebindTexture() {
        TextureSampler sampler = new TextureSampler();
        sampler.setMagFilter(TextureSampler.MagFilter.NEAREST);

        if (scaleFactor > 1.0f) {
            // SPATIAL: smooth minification with mipmaps
            sampler.setMinFilter(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR);
            sampler.setAnisotropy(8.0f);
        } else {
            // FLAT: pixel-perfect, no interpolation
            sampler.setMinFilter(TextureSampler.MinFilter.NEAREST);
        }

        materialInstance.setParameter("baseColorMap", texture, sampler);
    }

    private void uploadPixels() {
        // Read pixels from Skia bitmap as byte array
        byte[] pixels = bitmap.readPixels();
        if (pixels == null || pixels.length == 0) return;

        // Copy into direct ByteBuffer for Filament
        if (pixelBuffer.capacity() < pixels.length) {
            pixelBuffer = ByteBuffer.allocateDirect(pixels.length)
                    .order(ByteOrder.nativeOrder());
        }
        pixelBuffer.clear();
        pixelBuffer.put(pixels);
        pixelBuffer.flip();

        // Upload base level to Filament texture
        texture.setImage(engine, 0,
                new Texture.PixelBufferDescriptor(
                        pixelBuffer,
                        Texture.Format.RGBA,
                        Texture.Type.UBYTE
                ));

        // Generate mipmap chain only in SPATIAL mode
        if (scaleFactor > 1.0f) {
            texture.generateMipmaps(engine);
        }
    }

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
