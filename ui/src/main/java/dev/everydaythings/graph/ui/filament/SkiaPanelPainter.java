package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.skia.FontCache;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.skia.PanelPainter;
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
 * Panel painter that rasterizes 2D surfaces via Skia and displays them
 * as textured quads in 3D space.
 *
 * <p>Pipeline: LayoutNode tree → {@link SkiaPainter} → Skia bitmap →
 * Filament texture → textured quad entity positioned at world transform.
 *
 * <p>Uses 3x supersampling with mipmaps and 8x anisotropic filtering
 * for quality at varying 3D distances and angles.
 *
 * @see PanelPainter
 * @see SkiaPanel
 */
public class SkiaPanelPainter implements PanelPainter {

    private static final Logger log = LogManager.getLogger(SkiaPanelPainter.class);
    private static final int BG_COLOR = 0xFF1E1E2E;
    private static final float SUPERSAMPLE = 3.0f;

    private final Engine engine;
    private final Scene scene;
    private final SkiaPainter painter;
    private final FontCache fontCache;

    // Shared material (loaded once, reused across panels)
    private Material material;

    // Per-panel resources (created fresh each paintPanel, destroyed on clear)
    private Texture texture;
    private MaterialInstance materialInstance;
    private PrimitiveMeshes.Mesh quadMesh;
    private int quadEntity;

    public SkiaPanelPainter(Engine engine, Scene scene,
                             SkiaPainter painter, FontCache fontCache) {
        this.engine = engine;
        this.scene = scene;
        this.painter = painter;
        this.fontCache = fontCache;
        this.material = loadMaterial("materials/textured_unlit.filamat");
    }

    @Override
    public RenderContext buildContext(float pixelWidth, float pixelHeight) {
        RenderMetrics metrics = fontCache.buildMetrics();
        return RenderContext.builder()
                .renderer(RenderContext.RENDERER_SKIA)
                .breakpoint(RenderContext.BREAKPOINT_LG)
                .viewportWidth(pixelWidth)
                .viewportHeight(pixelHeight)
                .addCapability("color")
                .addCapability("mouse")
                .addCapability("images")
                .renderMetrics(metrics)
                .baseFontSize(fontCache.baseFontSize())
                .build();
    }

    @Override
    public LayoutEngine.TextMeasurer textMeasurer() {
        return fontCache;
    }

    @Override
    public void paintPanel(LayoutNode.BoxNode tree,
                           float pixelW, float pixelH,
                           float panelWidthM, float panelHeightM,
                           float[] worldTransform) {
        clear();

        // 1. Rasterize with Skia at supersampled resolution
        int texW = Math.round(pixelW * SUPERSAMPLE);
        int texH = Math.round(pixelH * SUPERSAMPLE);
        if (texW <= 0 || texH <= 0) return;

        ImageInfo info = new ImageInfo(texW, texH, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        Bitmap bitmap = new Bitmap();
        bitmap.allocPixels(info);
        Canvas canvas = new Canvas(bitmap);

        canvas.clear(BG_COLOR);
        canvas.save();
        canvas.scale(SUPERSAMPLE, SUPERSAMPLE);
        painter.paint(canvas, tree);
        canvas.restore();

        // 2. Read pixels and upload to Filament texture
        byte[] pixels = bitmap.readPixels();
        canvas.close();
        bitmap.close();

        if (pixels == null || pixels.length == 0) return;

        texture = new Texture.Builder()
                .width(texW)
                .height(texH)
                .format(Texture.InternalFormat.SRGB8_A8)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .levels(0xFF)
                .usage(Texture.Usage.DEFAULT | Texture.Usage.GEN_MIPMAPPABLE)
                .build(engine);

        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(pixels.length)
                .order(ByteOrder.nativeOrder());
        pixelBuffer.put(pixels);
        pixelBuffer.flip();

        texture.setImage(engine, 0,
                new Texture.PixelBufferDescriptor(
                        pixelBuffer,
                        Texture.Format.RGBA,
                        Texture.Type.UBYTE));
        texture.generateMipmaps(engine);

        // 3. Create material instance and bind texture with spatial sampler
        materialInstance = material.createInstance();
        TextureSampler sampler = new TextureSampler();
        sampler.setMagFilter(TextureSampler.MagFilter.NEAREST);
        sampler.setMinFilter(TextureSampler.MinFilter.NEAREST);
        materialInstance.setParameter("baseColorMap", texture, sampler);

        // 4. Create textured quad at panel meter dimensions
        float aspect = panelHeightM / panelWidthM;
        quadMesh = PrimitiveMeshes.createQuad(engine, panelWidthM, panelWidthM * aspect);

        quadEntity = EntityManager.get().create();
        new RenderableManager.Builder(1)
                .boundingBox(quadMesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        quadMesh.vertexBuffer(), quadMesh.indexBuffer(), 0, quadMesh.indexCount())
                .material(0, materialInstance)
                .culling(false)
                .build(engine, quadEntity);

        // 5. Apply world transform to position the quad
        TransformManager tm = engine.getTransformManager();
        tm.create(quadEntity);
        tm.setTransform(tm.getInstance(quadEntity), worldTransform);

        scene.addEntity(quadEntity);

        log.debug("Skia panel painted: {}x{}px (tex {}x{}) on {}x{}m",
                pixelW, pixelH, texW, texH, panelWidthM, panelHeightM);
    }

    @Override
    public void clear() {
        if (quadEntity != 0) {
            scene.removeEntity(quadEntity);
            engine.destroyEntity(quadEntity);
            quadEntity = 0;
        }
        if (quadMesh != null) {
            quadMesh.destroy(engine);
            quadMesh = null;
        }
        if (materialInstance != null) {
            engine.destroyMaterialInstance(materialInstance);
            materialInstance = null;
        }
        if (texture != null) {
            engine.destroyTexture(texture);
            texture = null;
        }
    }

    @Override
    public void destroy() {
        clear();
        if (material != null) {
            engine.destroyMaterial(material);
            material = null;
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
