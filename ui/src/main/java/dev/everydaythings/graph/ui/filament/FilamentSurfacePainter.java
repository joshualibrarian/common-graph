package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.filament.text.ColrGlyphInfo;
import dev.everydaythings.filament.text.MsdfAtlas;
import dev.everydaythings.filament.text.MsdfTextRenderer;
import dev.everydaythings.graph.ui.paragraph.MsdfParagraphFactory;
import dev.everydaythings.graph.ui.paragraph.Paragraph;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.text.EmojiIconResolver;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.ParagraphBuilder;
import io.github.humbleui.skija.paragraph.ParagraphStyle;
import io.github.humbleui.skija.paragraph.TextStyle;
import io.github.humbleui.skija.svg.SVGDOM;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints a {@link LayoutNode} tree directly as Filament 3D geometry.
 *
 * <p>Alternative to the {@link SkiaPanel} pipeline. Instead of CPU-rasterizing
 * to a bitmap and uploading as a texture, this creates individual Filament
 * entities for each UI element:
 * <ul>
 *   <li>{@link LayoutNode.BoxNode} → colored quads (flat_color material)</li>
 *   <li>{@link LayoutNode.TextNode} → per-glyph MSDF quads (msdf_text material)</li>
 *   <li>{@link LayoutNode.ShapeNode} → colored geometry (flat_color material)</li>
 *   <li>{@link LayoutNode.ImageNode} → emoji text fallback via MSDF</li>
 * </ul>
 *
 * <p>Benefits: resolution-independent text at any 3D distance/angle,
 * GPU-native rendering, no CPU rasterization bottleneck.
 *
 * @see SkiaPanel
 * @see MsdfFontManager
 * @see MsdfAtlas
 */
public class FilamentSurfacePainter implements SurfacePainter {

    /**
     * A deferred GLB model placement request collected during elevated painting.
     * The caller dispatches these to the spatial renderer after painting completes.
     */
    public static class ModelPlacement {
        private final String resource;
        private final int color;
        private final float x, y, z, scale;

        public ModelPlacement(String resource, int color,
                              float x, float y, float z, float scale) {
            this.resource = resource;
            this.color = color;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
        }

        public String resource() { return resource; }
        public int color() { return color; }
        public float x() { return x; }
        public float y() { return y; }
        public float z() { return z; }
        public float scale() { return scale; }
    }

    private static final Logger log = LogManager.getLogger(FilamentSurfacePainter.class);

    // Default colors for unresolved nodes (ARGB format)
    private static final int COLOR_BG = 0xFF1E1E2E;          // Dark background
    private static final int COLOR_TEXT = 0xFFCDD6F4;         // Light text
    private static final int COLOR_ACCENT = 0xFF89B4FA;       // Shape fill default
    private static final int COLOR_MUTED = 0xFF6C7086;        // Line/separator default
    // Canonical front-face UV order for plane vertices [0..3] in local winding:
    // 0=(minX,minY), 1=(maxX,minY), 2=(maxX,maxY), 3=(minX,maxY)
    // We use top-left texture origin convention to match Skia raster output.
    private static final float[] QUAD_UVS_FRONT = {
            0f, 1f,
            1f, 1f,
            1f, 0f,
            0f, 0f
    };
    // Canonical top-facing XZ plane winding (+Y normal).
    private static final short[] XZ_QUAD_UP_INDICES = {0, 1, 2, 0, 2, 3};
    // Top-view front-facing winding for text quads in this coordinate mapping.
    // Used by both raster-text fallback and MSDF glyph quads to keep orientation consistent.
    private static final short[] XZ_TEXT_QUAD_TOP_FRONT_INDICES = {0, 2, 1, 0, 3, 2};

    private final Engine engine;
    private Scene scene;
    private final MsdfFontManager fontManager;
    private final MsdfTextRenderer textRenderer;

    // Resource tracking for cleanup between repaints
    private final List<Integer> entities = new ArrayList<>();
    private final List<PrimitiveMeshes.Mesh> meshes = new ArrayList<>();
    private final List<MaterialInstance> materialInstances = new ArrayList<>();

    // Model placements collected during paintElevated(), dispatched by caller to spatial renderer
    private final List<ModelPlacement> modelPlacements = new ArrayList<>();
    private int elevatedTextNodeCount;
    private int elevatedImageNodeCount;
    private int elevatedFallbackTextQuadCount;
    private int elevatedFallbackImageQuadCount;

    // Conversion factor: pixels to world units
    // The UI tree is laid out in pixels; we map to Filament world units.
    // 1 world unit = pixelsPerUnit pixels.
    private float pixelsPerUnit = 1.0f;

    // Origin offset in world units (centers the UI and positions at eye level)
    private float originX = 0f;
    private float originY = 0f;
    private float originZ = 0f;  // Z-axis offset for elevated (XZ plane) rendering

    // Base Z for the UI plane (entities layer with small Z offsets)
    private float baseZ = 0f;

    // Active 2D rotation state (pushed by paintBox for rotated containers)
    // When active, all pixel coordinates are rotated around (rotCX, rotCY)
    // before conversion to world space.
    private boolean rotationActive;
    private float rotCX, rotCY;     // rotation center in pixel coords
    private float rotCos, rotSin;   // precomputed cos/sin of rotation angle

    // Clip rectangle stack for nested box clipping (mirrors Skia's canvas.clipRect).
    // Each entry is [left, top, right, bottom] in pixel coordinates.
    // Nested clips intersect with their parent.
    private final ArrayDeque<float[]> clipStack = new ArrayDeque<>();

    // When set, skip the entire subtree rooted at the BoxNode with this id.
    // Used to avoid painting 2D content in the detail region when 3D is active there.
    private String skipId;

    // Batched geometry accumulators — collect quads during paint, flush as single entities.
    // Dramatically reduces Filament handle count (from hundreds of entities to ~5).
    private final BatchAccumulator flatColorBatch = new BatchAccumulator();
    private final Map<MsdfAtlas, MsdfBatchAccumulator> msdfBatches = new HashMap<>();

    // Image rendering: Skia rasterizes SVG/raster images → Filament textures on quads.
    // Textures persist across clear() (expensive to recreate), destroyed in destroy().
    private Material texturedMaterial;  // lazy-loaded textured_unlit material
    private final Map<String, Texture> imageTextureCache = new HashMap<>();
    private final Map<String, Texture> textTextureCache = new HashMap<>();
    private final Map<String, SVGDOM> svgCache = new HashMap<>();
    private final Map<String, Image> rasterImageCache = new HashMap<>();
    private final FontCollection fallbackFontCollection = new FontCollection();
    // Pixel upload buffers must be retained until the GPU consumes them (setImage is async).
    // Cleared on clear() which happens before the next paint.
    private final List<ByteBuffer> pendingPixelBuffers = new ArrayList<>();
    public FilamentSurfacePainter(Engine engine, Scene scene, MsdfFontManager fontManager) {
        this.engine = engine;
        this.scene = scene;
        this.fontManager = fontManager;
        this.textRenderer = new MsdfTextRenderer(engine);
        fallbackFontCollection.setDefaultFontManager(FontMgr.getDefault());
        fallbackFontCollection.setEnableFallback(true);
    }

    /**
     * Set the target scene for entity creation.
     * Call this when switching the painter to a different pane.
     */
    public void scene(Scene scene) {
        this.scene = scene;
    }

    /**
     * Set an element ID to skip during painting.
     * When set, the BoxNode with this id and its entire subtree are not painted.
     * Used to avoid rendering 2D content in a region that will be covered by 3D.
     *
     * @param id the element id to skip, or null to paint everything
     */
    @Override
    public void skipId(String id) {
        this.skipId = id;
    }

    /** Number of entities currently tracked (for diagnostics). */
    public int entityCount() {
        return entities.size();
    }

    /** Model placements collected during the last {@link #paintElevated} call. */
    public List<ModelPlacement> modelPlacements() {
        return modelPlacements;
    }

    /**
     * Set the pixels-per-unit conversion factor.
     * E.g., if the UI is 800px wide and should span 2 world units, set to 400.
     */
    public void pixelsPerUnit(float ppu) {
        this.pixelsPerUnit = ppu;
    }

    /**
     * Configure the layout dimensions so the UI is centered and positioned
     * at the given Y height in world space — matching SkiaPanel placement.
     *
     * @param widthPx  layout width in pixels
     * @param heightPx layout height in pixels
     * @param worldWidth desired width in world units (e.g. 2.0)
     * @param centerY   world Y position for the center of the panel
     */
    @Override
    public void configureForLayout(float widthPx, float heightPx,
                                    float worldWidth, float centerY) {
        this.pixelsPerUnit = widthPx / worldWidth;
        // Center horizontally: shift left by half the world width
        this.originX = -worldWidth / 2f;
        // Center vertically at the given Y: top of UI at centerY + half height
        float worldHeight = heightPx / pixelsPerUnit;
        this.originY = centerY + worldHeight / 2f;
    }

    /**
     * Configure for elevated (XZ plane) rendering on a body face.
     *
     * <p>Centers the layout on the body's top face. Pixel X maps to world X,
     * pixel Y maps to world Z. Both axes are centered at the world origin.
     *
     * @param widthPx    layout width in pixels
     * @param heightPx   layout height in pixels
     * @param worldWidth body width in world units
     * @param worldDepth body depth in world units
     */
    public void configureForElevated(float widthPx, float heightPx,
                                      float worldWidth, float worldDepth) {
        this.pixelsPerUnit = widthPx / worldWidth;
        this.originX = -worldWidth / 2f;
        this.originZ = -worldDepth / 2f;
    }

    /**
     * Configure for panel-local XY coordinate mapping.
     *
     * <p>Centers the layout at the local origin, suitable for parenting
     * under a transform entity positioned in world space. Pixel X maps to
     * world X (centered), pixel Y maps to world Y (top-down).
     *
     * @param widthPx      layout width in pixels
     * @param heightPx     layout height in pixels
     * @param widthMeters  panel width in meters
     * @param heightMeters panel height in meters
     */
    public void configureForPanel(float widthPx, float heightPx,
                                   float widthMeters, float heightMeters) {
        this.pixelsPerUnit = widthPx / widthMeters;
        this.originX = -widthMeters / 2f;    // center horizontally
        this.originY = heightMeters / 2f;     // top at +Y, Y-down for layout
        this.originZ = 0f;
    }

    /**
     * Reparent all tracked entities under the given parent entity.
     *
     * <p>After painting, entities have panel-local transforms. Parenting them
     * to a positioned entity makes those transforms relative to the parent's
     * world position.
     *
     * @param parentEntity Filament entity t
     *                     o parent all painted entities under
     */
    public void reparentAll(int parentEntity) {
        TransformManager tm = engine.getTransformManager();
        int parentInstance = tm.getInstance(parentEntity);
        if (parentInstance == 0) {
            parentInstance = tm.create(parentEntity);
        }
        for (int entity : entities) {
            int inst = tm.getInstance(entity);
            if (inst == 0) {
                inst = tm.create(entity);
            }
            tm.setParent(inst, parentInstance);
        }
    }

    /**
     * Adjust origin offsets (for centering content on a body face).
     *
     * @param dx world-unit offset to add to originX
     * @param dz world-unit offset to add to originZ
     */
    public void adjustOrigin(float dx, float dz) {
        this.originX += dx;
        this.originZ += dz;
    }

    /**
     * Adjust the Y origin (for vertical scrolling).
     *
     * @param dy world-unit offset to add to originY (positive = shift content up)
     */
    public void adjustOriginY(float dy) {
        this.originY += dy;
    }

    /**
     * Paint the full layout tree as Filament geometry.
     *
     * <p>Pre-ensures all text glyphs across the fallback chain BEFORE creating
     * any geometry. This prevents atlas texture invalidation mid-paint
     * (ensureGlyphs destroys and recreates the texture when new glyphs are added).
     *
     * @param root the root BoxNode (already laid out by LayoutEngine)
     * @param z    base Z depth in world space
     */
    @Override
    public void paint(LayoutNode.BoxNode root, float z) {
        // Defensive reset of transient per-frame state. If a previous paint
        // aborted before stack unwind, stale clip/rotation must not leak.
        clipStack.clear();
        rotationActive = false;

        this.baseZ = z;

        // Phase 1: collect all text and ensure glyphs in all atlases
        collectAndEnsureGlyphs(root);

        // Phase 2: emit geometry into batch accumulators (atlas textures are now stable)
        paintNode(root, z);

        // Phase 3: flush accumulated geometry into batched entities
        flushBatches();

        if (log.isTraceEnabled()) {
            log.trace("FilamentSurfacePainter: {} entities, {} meshes, {} MIs",
                    entities.size(), meshes.size(), materialInstances.size());
        }
    }

    /**
     * Paint the layout tree as 3D geometry on a body face.
     *
     * <p>Like {@link #paint}, but interprets elevation and model fields.
     * Elevated boxes become 3D slabs; images with modelResource become GLB meshes.
     * Used for rendering surface-on-body composition in the detail pane.
     *
     * @param root     the root BoxNode (already laid out by LayoutEngine)
     * @param baseY    base Y height in world space (top of the body)
     */
    public void paintElevated(LayoutNode.BoxNode root, float baseY) {
        this.baseZ = 0f; // Not used for elevated rendering
        modelPlacements.clear();
        elevatedTextNodeCount = 0;
        elevatedImageNodeCount = 0;
        elevatedFallbackTextQuadCount = 0;
        elevatedFallbackImageQuadCount = 0;

        // Phase 1: collect all text and ensure glyphs
        collectAndEnsureGlyphs(root);

        // Phase 2: emit geometry with elevation awareness
        paintNodeElevated(root, baseY);
    }

    /** Walk the tree and ensure all text codepoints are in the font atlas(es). */
    private void collectAndEnsureGlyphs(LayoutNode node) {
        if (!fontManager.hasUsableFonts()) {
            return;
        }
        if (node.hidden()) return;
        // Skip subtree for regions handled by another pane (e.g., 3D detail)
        if (skipId != null && node instanceof LayoutNode.BoxNode box
                && skipId.equals(box.id())) {
            return;
        }
        switch (node) {
            case LayoutNode.BoxNode box -> {
                for (LayoutNode child : box.children()) {
                    collectAndEnsureGlyphs(child);
                }
            }
            case LayoutNode.TextNode text -> {
                if (!text.content().isEmpty()) {
                    fontManager.ensureGlyphs(text.content());
                }
            }
            case LayoutNode.ImageNode image -> {
                if (image.alt() != null && !image.alt().isEmpty()) {
                    fontManager.ensureGlyphs(image.alt());
                }
            }
            case LayoutNode.ShapeNode ignored -> {}
        }
    }

    /**
     * Remove all entities created by the previous paint pass.
     * Call before each repaint to avoid accumulation.
     */
    @Override
    public void clear() {
        // Defensive reset of transient per-frame state.
        clipStack.clear();
        rotationActive = false;

        for (int entity : entities) {
            scene.removeEntity(entity);
            engine.destroyEntity(entity);
        }
        entities.clear();

        for (PrimitiveMeshes.Mesh mesh : meshes) {
            mesh.destroy(engine);
        }
        meshes.clear();

        for (MaterialInstance mi : materialInstances) {
            engine.destroyMaterialInstance(mi);
        }
        materialInstances.clear();

        modelPlacements.clear();

        // Release pixel upload buffers (safe now — previous frame consumed them)
        pendingPixelBuffers.clear();

        // Clear batch accumulators
        flatColorBatch.clear();
        msdfBatches.values().forEach(MsdfBatchAccumulator::clear);
        msdfBatches.clear();
    }

    /**
     * Destroy all resources including shared materials.
     */
    @Override
    public void destroy() {
        clear();
        textRenderer.destroy();
        // Clean up image rendering resources
        for (Texture tex : imageTextureCache.values()) {
            engine.destroyTexture(tex);
        }
        imageTextureCache.clear();
        for (Texture tex : textTextureCache.values()) {
            engine.destroyTexture(tex);
        }
        textTextureCache.clear();
        for (SVGDOM svg : svgCache.values()) {
            svg.close();
        }
        svgCache.clear();
        for (Image img : rasterImageCache.values()) {
            img.close();
        }
        rasterImageCache.clear();
        if (texturedMaterial != null) {
            engine.destroyMaterial(texturedMaterial);
            texturedMaterial = null;
        }
        fallbackFontCollection.close();
    }

    @Override
    public LayoutEngine.TextMeasurer textMeasurer() {
        return fontManager;
    }

    // ==================================================================================
    // Image Rendering (Skia rasterization → Filament texture)
    // ==================================================================================

    private Material texturedMaterial() {
        if (texturedMaterial == null) {
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("materials/textured_unlit.filamat")) {
                if (in == null) throw new RuntimeException("textured_unlit.filamat not found");
                byte[] data = in.readAllBytes();
                ByteBuffer buf = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
                buf.put(data);
                buf.flip();
                texturedMaterial = new Material.Builder().payload(buf, buf.remaining()).build(engine);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load textured_unlit material", e);
            }
        }
        return texturedMaterial;
    }

    /**
     * Try to paint an image from a classpath resource (SVG or raster).
     * Returns true if the image was rendered, false to fall back to emoji.
     */
    private boolean paintImageResource(LayoutNode.ImageNode image, float z) {
        String resource = EmojiIconResolver.resolveResource(
                image.hasResource() ? image.resource() : null, image.alt());
        if (resource == null || resource.isBlank()) return false;

        boolean shaped = image.shape() != null;
        float contentScale = shaped ? 0.6f : 1.0f;
        float contentSize = Math.min(image.width(), image.height()) * contentScale;
        float contentX = shaped ? image.x() + (image.width() - contentSize) / 2f : image.x();
        float contentY = shaped ? image.y() + (image.height() - contentSize) / 2f : image.y();

        // Determine rasterization size (in pixels)
        float targetW, targetH;
        if (shaped) {
            targetW = contentSize;
            targetH = contentSize;
        } else {
            targetW = image.width();
            targetH = image.height() > 0 ? image.height() : 40;
        }
        int rasterW = Math.max(1, Math.round(targetW));
        int rasterH = Math.max(1, Math.round(targetH));

        // Build cache key including size (SVGs need re-rasterization at different sizes)
        String cacheKey = resource + ":" + rasterW + "x" + rasterH;
        Texture tex = imageTextureCache.get(cacheKey);

        if (tex == null) {
            byte[] pixels = rasterizeImage(resource, rasterW, rasterH);
            if (pixels == null) {
                log.warn("Failed to rasterize image resource: {} ({}x{})", resource, rasterW, rasterH);
                return false;
            }

            tex = new Texture.Builder()
                    .width(rasterW)
                    .height(rasterH)
                    .levels(1)
                    .format(Texture.InternalFormat.RGBA8)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .usage(Texture.Usage.DEFAULT)
                    .build(engine);

            ByteBuffer pixelBuf = ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
            pixelBuf.put(pixels);
            pixelBuf.flip();
            // Retain buffer — setImage is async, buffer must survive until GPU consumes it
            pendingPixelBuffers.add(pixelBuf);
            tex.setImage(engine, 0, new Texture.PixelBufferDescriptor(
                    pixelBuf, Texture.Format.RGBA, Texture.Type.UBYTE));

            imageTextureCache.put(cacheKey, tex);
        }

        float drawX = shaped ? contentX : image.x();
        float drawY = shaped ? contentY : image.y();
        emitTexturedQuad(drawX, drawY, targetW, targetH, tex, z + 0.01f);
        return true;
    }

    /**
     * Rasterize an image resource (SVG or raster) to RGBA pixels using Skia.
     */
    private byte[] rasterizeImage(String resource, int w, int h) {
        if (resource.endsWith(".svg")) {
            return rasterizeSvg(resource, w, h);
        } else {
            return rasterizeRaster(resource, w, h);
        }
    }

    private byte[] rasterizeSvg(String resource, int w, int h) {
        SVGDOM svg = svgCache.get(resource);
        if (svg == null) {
            String path = resource.startsWith("/") ? resource.substring(1) : resource;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    log.warn("SVG resource not found on classpath: {}", path);
                    return null;
                }
                byte[] data = in.readAllBytes();
                log.debug("Loaded SVG resource: {} ({} bytes)", path, data.length);
                svg = new SVGDOM(Data.makeFromBytes(data));
                svgCache.put(resource, svg);
            } catch (IOException e) {
                log.warn("Failed to load SVG resource: {}", path, e);
                return null;
            }
        }

        ImageInfo info = new ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        try (Bitmap bmp = new Bitmap()) {
            bmp.allocPixels(info);
            try (Canvas c = new Canvas(bmp)) {
                c.clear(0x00000000); // transparent
                svg.setContainerSize(w, h);
                svg.render(c);
            }
            return bmp.readPixels();
        }
    }

    private byte[] rasterizeRaster(String resource, int w, int h) {
        Image img = rasterImageCache.get(resource);
        if (img == null) {
            String path = resource.startsWith("/") ? resource.substring(1) : resource;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) return null;
                img = Image.makeDeferredFromEncodedBytes(in.readAllBytes());
                rasterImageCache.put(resource, img);
            } catch (IOException e) {
                return null;
            }
        }

        ImageInfo info = new ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        try (Bitmap bmp = new Bitmap()) {
            bmp.allocPixels(info);
            try (Canvas c = new Canvas(bmp)) {
                c.clear(0x00000000);
                float scale = Math.min((float) w / img.getWidth(), (float) h / img.getHeight());
                c.scale(scale, scale);
                c.drawImage(img, 0, 0);
            }
            return bmp.readPixels();
        }
    }

    /**
     * Emit a textured quad at the given pixel position.
     */
    private void emitTexturedQuad(float px, float py, float pw, float ph,
                                   Texture tex, float z) {
        if (pw <= 0 || ph <= 0) return;

        // Convert pixel center to world coords
        float cx = px + pw / 2f;
        float cy = py + ph / 2f;
        if (rotationActive) {
            float[] r = rotatePoint(cx, cy);
            cx = r[0]; cy = r[1];
        }
        float wcx = cx / pixelsPerUnit + originX;
        float wcy = -cy / pixelsPerUnit + originY;
        float ww = pw / pixelsPerUnit;
        float wh = ph / pixelsPerUnit;

        // Use PrimitiveMeshes.createQuad (proven to work with textured_unlit in SkiaPanel)
        PrimitiveMeshes.Mesh mesh = PrimitiveMeshes.createQuad(engine, ww, wh);
        meshes.add(mesh);

        MaterialInstance mi = texturedMaterial().createInstance();
        TextureSampler sampler = new TextureSampler();
        sampler.setMagFilter(TextureSampler.MagFilter.LINEAR);
        sampler.setMinFilter(TextureSampler.MinFilter.LINEAR);
        mi.setParameter("baseColorMap", tex, sampler);
        materialInstances.add(mi);

        int entity = EntityManager.get().create();
        entities.add(entity);

        new RenderableManager.Builder(1)
                .boundingBox(mesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        mesh.vertexBuffer(), mesh.indexBuffer(), 0, mesh.indexCount())
                .material(0, mi)
                .culling(false)
                .castShadows(false)
                .receiveShadows(false)
                .build(engine, entity);

        // Position the quad via TransformManager
        var tcm = engine.getTransformManager();
        int ti = tcm.getInstance(entity);
        if (ti == 0) {
            tcm.create(entity);
            ti = tcm.getInstance(entity);
        }
        // Column-major 4x4: translate to world position
        float[] mat = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                wcx, wcy, z, 1
        };
        tcm.setTransform(ti, mat);

        scene.addEntity(entity);
    }

    // ==================================================================================
    // Node Dispatch
    // ==================================================================================

    private void paintNode(LayoutNode node, float z) {
        if (node.hidden()) return;

        // Skip subtree for regions handled by another pane (e.g., 3D detail)
        if (skipId != null && node instanceof LayoutNode.BoxNode box
                && skipId.equals(box.id())) {
            return;
        }

        // For non-box nodes, push rotation state if rotated.
        // BoxNode handles its own rotation (since it propagates to children).
        boolean leafRotated = !(node instanceof LayoutNode.BoxNode) && node.rotation() != 0;
        boolean prevRotationActive = rotationActive;
        float prevRotCX = rotCX, prevRotCY = rotCY, prevRotCos = rotCos, prevRotSin = rotSin;
        if (leafRotated) {
            double rad = Math.toRadians(node.rotation());
            rotCX = node.x() + node.width() * node.transformOriginX();
            rotCY = node.y() + node.height() * node.transformOriginY();
            rotCos = (float) Math.cos(rad);
            rotSin = (float) Math.sin(rad);
            rotationActive = true;
        }

        switch (node) {
            case LayoutNode.BoxNode box -> paintBox(box, z);
            case LayoutNode.TextNode text -> paintText(text, z);
            case LayoutNode.ShapeNode shape -> paintShape(shape, z);
            case LayoutNode.ImageNode image -> paintImage(image, z);
        }

        if (leafRotated) {
            rotationActive = prevRotationActive;
            rotCX = prevRotCX; rotCY = prevRotCY;
            rotCos = prevRotCos; rotSin = prevRotSin;
        }
    }

    // ==================================================================================
    // Box Rendering
    // ==================================================================================

    private void paintBox(LayoutNode.BoxNode box, float z) {
        // Apply elevation: offset this box's Z position by its elevation value.
        // Higher elevation = closer to camera (positive Z in our coordinate system).
        float boxZ = z;
        if (box.isElevated()) {
            boxZ = z + (float) box.elevation();
        }

        float bt = box.borderTopWidth(), br = box.borderRightWidth();
        float bb = box.borderBottomWidth(), bl = box.borderLeftWidth();

        // Push rotation state if this box is rotated
        boolean rotated = box.rotation() != 0;
        boolean prevRotationActive = rotationActive;
        float prevRotCX = rotCX, prevRotCY = rotCY, prevRotCos = rotCos, prevRotSin = rotSin;
        if (rotated) {
            double rad = Math.toRadians(box.rotation());
            rotCX = box.x() + box.width() * box.transformOriginX();
            rotCY = box.y() + box.height() * box.transformOriginY();
            rotCos = (float) Math.cos(rad);
            rotSin = (float) Math.sin(rad);
            rotationActive = true;
        }

        // Background — use flat quads at elevated Z (no 3D slabs for now)
        if (box.background() != null || box.backgroundColor() != -1) {
            int bgColor = resolveBackgroundColor(box);
            float bgX = box.x() + bl;
            float bgY = box.y() + bt;
            float bgW = box.width() - bl - br;
            float bgH = box.height() - bt - bb;
            if (bgW > 0 && bgH > 0) {
                if ("circle".equals(box.shapeType())) {
                    float cx = bgX + bgW / 2f;
                    float cy = bgY + bgH / 2f;
                    float r = Math.min(bgW, bgH) / 2f;
                    emitCircleFan(cx, cy, r, r, bgColor, boxZ, 32);
                } else if (box.borderRadius() > 0) {
                    float r = Math.min(box.borderRadius(), Math.min(bgW, bgH) / 2f);
                    emitRoundedRect(bgX, bgY, bgW, bgH, r, bgColor, boxZ, 8);
                } else {
                    emitColoredQuad(bgX, bgY, bgW, bgH, bgColor, boxZ);
                }
            }
        }

        // Borders
        if ("circle".equals(box.shapeType())) {
            paintCircleBorder(box, boxZ + 0.001f);
        } else {
            paintBorders(box, boxZ + 0.001f);
        }

        // Children (layered above, each at increasing Z to prevent Z-fighting
        // in overlapping layouts like STACK where all children share the same position)
        float contentX = box.x() + bl;
        float contentY = box.y() + bt;
        float contentW = box.width() - bl - br;
        float contentH = box.height() - bt - bb;
        pushClip(contentX, contentY, contentW, contentH);

        // Apply scroll offset: shift entire subtree in pixel space so clip checks work
        float scrollOffset = box.isScrollContainer() ? box.scrollOffsetY() : 0;
        float childZ = boxZ + 0.002f;
        if (scrollOffset != 0) {
            for (LayoutNode child : box.children()) {
                offsetSubtree(child, 0, -scrollOffset);
                paintNode(child, childZ);
                offsetSubtree(child, 0, scrollOffset);
                childZ += 0.001f;
            }
        } else {
            for (LayoutNode child : box.children()) {
                paintNode(child, childZ);
                childZ += 0.001f;
            }
        }

        // Paint overlays (scrollbar shapes) without scroll offset, still clipped
        for (LayoutNode overlay : box.overlays()) {
            paintNode(overlay, childZ);
            childZ += 0.001f;
        }

        popClip();

        // Pop rotation state
        if (rotated) {
            rotationActive = prevRotationActive;
            rotCX = prevRotCX;
            rotCY = prevRotCY;
            rotCos = prevRotCos;
            rotSin = prevRotSin;
        }
    }

    /**
     * Paint a circular border (stroke) for a container with shapeType = "circle".
     */
    private void paintCircleBorder(LayoutNode.BoxNode box, float z) {
        float bt = box.borderTopWidth();
        if (bt <= 0) return;
        int borderColor = COLOR_TEXT;
        if (box.border() != null && box.border().top() != null && box.border().top().color() != null) {
            borderColor = parseColor(box.border().top().color(), COLOR_TEXT);
        }
        float cx = box.x() + box.width() / 2f;
        float cy = box.y() + box.height() / 2f;
        float outerR = Math.min(box.width(), box.height()) / 2f;
        float innerR = outerR - bt;
        emitCircleRing(cx, cy, innerR, outerR, borderColor, z, 32);
    }

    private void paintBorders(LayoutNode.BoxNode box, float z) {
        float x = box.x(), y = box.y(), w = box.width(), h = box.height();
        float bt = box.borderTopWidth(), br = box.borderRightWidth();
        float bb = box.borderBottomWidth(), bl = box.borderLeftWidth();

        if (bt == 0 && br == 0 && bb == 0 && bl == 0) return;

        int borderColor = COLOR_TEXT; // default border color
        if (box.border() != null && box.border().top() != null) {
            borderColor = parseColor(box.border().top().color(), COLOR_TEXT);
        }

        // Top
        if (bt > 0) emitColoredQuad(x, y, w, bt, borderColor, z);
        // Bottom
        if (bb > 0) emitColoredQuad(x, y + h - bb, w, bb, borderColor, z);
        // Left
        if (bl > 0) emitColoredQuad(x, y + bt, bl, h - bt - bb, borderColor, z);
        // Right
        if (br > 0) emitColoredQuad(x + w - br, y + bt, br, h - bt - bb, borderColor, z);
    }

    // ==================================================================================
    // Text Rendering
    // ==================================================================================

    private void paintText(LayoutNode.TextNode text, float z) {
        if (text.content().isEmpty()) return;
        if (fontManager.defaultAtlas() == null) return;

        int color = resolveTextColor(text);

        float fontSize = fontManager.fontSizeFor(text);

        // Code background
        if (text.backgroundColor() != -1) {
            emitColoredQuad(text.x() - 2, text.y(),
                    text.width() + 4, text.height(), text.backgroundColor(), z);
        }

        float textZ = z + 0.01f;

        // Use pre-built paragraph if available (from ParagraphFactory during measurement)
        Paragraph para = text.paragraph();
        if (para != null) {
            var painter = new GlyphPaintContext(color, textZ);
            para.paint(painter, text.x(), text.y());
            return;
        }

        // Fallback: emit glyphs directly (no paragraph)
        MsdfAtlas primaryAtlas = fontManager.defaultAtlas();
        float ascent = primaryAtlas != null ? (float) primaryAtlas.ascent() : 0.8f;
        float baselineY = text.y() + ascent * fontSize;

        float cursorX = text.x();

        for (int i = 0; i < text.content().length(); ) {
            int cp = text.content().codePointAt(i);
            dev.everydaythings.filament.text.MsdfFontManager.ResolvedGlyph rg = fontManager.resolveGlyph(cp);

            if (rg != null && rg.metrics().advance() > 0) {
                var g = rg.metrics();
                if (rg.isColor()) {
                    // COLRv0: base glyph has empty outline, emit color layers directly
                    emitColorLayers(rg.colorInfo(), rg.atlas(),
                            fontSize, baselineY, cursorX, color, textZ);
                } else if (g.uvRight() > g.uvLeft() && g.uvBottom() > g.uvTop()) {
                    float quadX = cursorX + (float)(g.planeLeft() * fontSize);
                    float quadY = baselineY - (float)(g.planeTop() * fontSize);
                    float quadW = (float)((g.planeRight() - g.planeLeft()) * fontSize);
                    float quadH = (float)((g.planeTop() - g.planeBottom()) * fontSize);

                    emitGlyphQuad(quadX, quadY, quadW, quadH, g, color, rg.atlas(), textZ);
                }
                cursorX += (float)(g.advance() * fontSize);
            } else if (rg != null) {
                cursorX += (float)(rg.metrics().advance() * fontSize);
            }

            i += Character.charCount(cp);
        }
    }

    /**
     * Context object passed as the "canvas" to {@link Paragraph#paint}.
     * Captures the current text color and Z depth for glyph emission.
     */
    private class GlyphPaintContext implements MsdfParagraphFactory.GlyphPainter {
        private final int argbColor;
        private final float z;

        GlyphPaintContext(int argbColor, float z) {
            this.argbColor = argbColor;
            this.z = z;
        }

        @Override
        public void emitGlyph(float x, float y, float w, float h,
                              MsdfAtlas.GlyphMetrics metrics, MsdfAtlas atlas) {
            emitGlyphQuad(x, y, w, h, metrics, argbColor, atlas, z);
        }

        @Override
        public void emitColorGlyph(float x, float y, float w, float h,
                                   ColrGlyphInfo colorInfo, MsdfAtlas atlas,
                                   float fontSize, float baselineY, float cursorX) {
            emitColorLayers(colorInfo, atlas, fontSize, baselineY, cursorX, argbColor, z);
        }
    }

    /**
     * Glyph painter for paragraphs rendered on the XZ plane.
     */
    private class GlyphPaintContextXZ implements MsdfParagraphFactory.GlyphPainter {
        private final int argbColor;
        private final float y;

        GlyphPaintContextXZ(int argbColor, float y) {
            this.argbColor = argbColor;
            this.y = y;
        }

        @Override
        public void emitGlyph(float x, float z, float w, float h,
                              MsdfAtlas.GlyphMetrics metrics, MsdfAtlas atlas) {
            emitGlyphQuadXZ(x, z, w, h, metrics, argbColor, atlas, y);
        }

        @Override
        public void emitColorGlyph(float x, float z, float w, float h,
                                   ColrGlyphInfo colorInfo, MsdfAtlas atlas,
                                   float fontSize, float baselineZ, float cursorX) {
            emitColorLayersXZ(colorInfo, atlas, fontSize, baselineZ, cursorX, argbColor, y);
        }
    }

    // ==================================================================================
    // COLRv0 Color Layer Rendering
    // ==================================================================================

    /**
     * Emit stacked MSDF quads for each COLRv0 color layer.
     * Each layer gets its own palette color and a micro Z-offset for correct compositing.
     */
    private void emitColorLayers(ColrGlyphInfo colorInfo, MsdfAtlas atlas,
                                 float fontSize, float baselineY, float cursorX,
                                 int foregroundArgb, float baseZ) {
        float layerZ = baseZ;
        for (ColrGlyphInfo.Layer layer : colorInfo.layers()) {
            MsdfAtlas.GlyphMetrics layerMetrics = atlas.glyphByIndex(layer.glyphIndex());
            if (layerMetrics == null) continue;
            if (layerMetrics.uvRight() <= layerMetrics.uvLeft()) continue;

            int color = (layer.argbColor() == ColrGlyphInfo.FOREGROUND_COLOR)
                    ? foregroundArgb : layer.argbColor();

            float quadX = cursorX + (float)(layerMetrics.planeLeft() * fontSize);
            float quadY = baselineY - (float)(layerMetrics.planeTop() * fontSize);
            float quadW = (float)((layerMetrics.planeRight() - layerMetrics.planeLeft()) * fontSize);
            float quadH = (float)((layerMetrics.planeTop() - layerMetrics.planeBottom()) * fontSize);

            emitGlyphQuad(quadX, quadY, quadW, quadH, layerMetrics, color, atlas, layerZ);
            layerZ += 0.001f;
        }
    }

    // ==================================================================================
    // Shape Rendering
    // ==================================================================================

    private void paintShape(LayoutNode.ShapeNode shape, float z) {
        switch (shape.shapeType()) {
            case "rectangle", "plane" -> paintRectangleShape(shape, z);
            case "circle", "sphere" -> paintCircleShape(shape, z);
            case "ellipse" -> paintEllipseShape(shape, z);
            case "point" -> paintPointShape(shape, z);
            case "polygon" -> paintPolygonShape(shape, z);
            case "line" -> paintLineShape(shape, z);
            case "path" -> paintPathShape(shape, z);
            case "cone" -> paintConeShape(shape, z);
            case "capsule" -> paintCapsuleShape(shape, z);
            default -> {} // unknown — skip
        }
    }

    private void paintRectangleShape(LayoutNode.ShapeNode shape, float z) {
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            emitColoredQuad(shape.x(), shape.y(), shape.width(), shape.height(), color, z);
        }
        if (!shape.stroke().isEmpty()) {
            int color = parseColor(shape.stroke(), COLOR_TEXT);
            float sw = parseStrokeWidth(shape.strokeWidth());
            float x = shape.x(), y = shape.y(), w = shape.width(), h = shape.height();
            // Stroke as 4 thin quads
            emitColoredQuad(x, y, w, sw, color, z + 0.001f);             // top
            emitColoredQuad(x, y + h - sw, w, sw, color, z + 0.001f);    // bottom
            emitColoredQuad(x, y + sw, sw, h - 2 * sw, color, z + 0.001f); // left
            emitColoredQuad(x + w - sw, y + sw, sw, h - 2 * sw, color, z + 0.001f); // right
        }
    }

    private void paintCircleShape(LayoutNode.ShapeNode shape, float z) {
        float cx = shape.x() + shape.width() / 2f;
        float cy = shape.y() + shape.height() / 2f;
        float rx = shape.width() / 2f;
        float ry = shape.height() / 2f;
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            emitCircleFan(cx, cy, rx, ry, color, z, 32);
        }
        if (!shape.stroke().isEmpty()) {
            int color = parseColor(shape.stroke(), COLOR_TEXT);
            float sw = parseStrokeWidth(shape.strokeWidth());
            emitCircleRing(cx, cy, Math.min(rx, ry) - sw, Math.min(rx, ry), color, z + 0.001f, 32);
        }
    }

    private void paintEllipseShape(LayoutNode.ShapeNode shape, float z) {
        float cx = shape.x() + shape.width() / 2f;
        float cy = shape.y() + shape.height() / 2f;
        float rx = shape.width() / 2f;
        float ry = shape.height() / 2f;
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            emitCircleFan(cx, cy, rx, ry, color, z, 32);
        }
        if (!shape.stroke().isEmpty()) {
            int color = parseColor(shape.stroke(), COLOR_TEXT);
            float sw = parseStrokeWidth(shape.strokeWidth());
            emitEllipseRing(cx, cy, rx - sw, ry - sw, rx, ry, color, z + 0.001f, 32);
        }
    }

    private void paintPointShape(LayoutNode.ShapeNode shape, float z) {
        float cx = shape.x() + shape.width() / 2f;
        float cy = shape.y() + shape.height() / 2f;
        float sw = parseStrokeWidth(shape.strokeWidth());
        float radius = Math.max(sw / 2f, 1.5f);
        String colorStr = !shape.fill().isEmpty() ? shape.fill()
                : !shape.stroke().isEmpty() ? shape.stroke() : "gray";
        int color = parseColor(colorStr, COLOR_TEXT);
        emitCircleFan(cx, cy, radius, radius, color, z, 8);
    }

    private void paintPolygonShape(LayoutNode.ShapeNode shape, float z) {
        int sides = 6;
        String d = shape.path();
        if (!d.isEmpty()) {
            try { sides = Integer.parseInt(d.trim()); } catch (NumberFormatException ignored) {}
        }
        if (sides < 3) sides = 3;

        float cx = shape.x() + shape.width() / 2f;
        float cy = shape.y() + shape.height() / 2f;
        float rx = shape.width() / 2f;
        float ry = shape.height() / 2f;
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            emitCircleFan(cx, cy, rx, ry, color, z, sides);
        }
        if (!shape.stroke().isEmpty()) {
            int color = parseColor(shape.stroke(), COLOR_TEXT);
            float sw = parseStrokeWidth(shape.strokeWidth());
            emitEllipseRing(cx, cy, rx - sw, ry - sw, rx, ry, color, z + 0.001f, sides);
        }
    }

    private void paintLineShape(LayoutNode.ShapeNode shape, float z) {
        String colorStr = !shape.stroke().isEmpty() ? shape.stroke()
                : !shape.fill().isEmpty() ? shape.fill() : "gray";
        int color = parseColor(colorStr, COLOR_MUTED);
        float sw = parseStrokeWidth(shape.strokeWidth());

        // Parse d attribute for endpoints: "x1,y1 x2,y2"
        String d = shape.path();
        if (!d.isEmpty() && d.contains(",")) {
            String[] points = d.trim().split("\\s+");
            if (points.length >= 2) {
                String[] p1 = points[0].split(",");
                String[] p2 = points[1].split(",");
                if (p1.length == 2 && p2.length == 2) {
                    float bx = shape.x(), by = shape.y();
                    float bw = shape.width(), bh = shape.height();
                    float x1 = bx + Float.parseFloat(p1[0]) * bw / 100f;
                    float y1 = by + Float.parseFloat(p1[1]) * bh / 100f;
                    float x2 = bx + Float.parseFloat(p2[0]) * bw / 100f;
                    float y2 = by + Float.parseFloat(p2[1]) * bh / 100f;
                    // Approximate line as axis-aligned thin quad
                    float dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
                    if (dy > dx) {
                        // Mostly vertical: tall thin quad
                        float top = Math.min(y1, y2);
                        float cx = (x1 + x2) / 2f;
                        emitColoredQuad(cx - sw / 2f, top, sw, dy, color, z);
                    } else {
                        // Mostly horizontal: wide thin quad
                        float left = Math.min(x1, x2);
                        float cy = (y1 + y2) / 2f;
                        emitColoredQuad(left, cy - sw / 2f, dx, sw, color, z);
                    }
                    return;
                }
            }
        }

        float midY = shape.y() + shape.height() / 2f - sw / 2f;
        emitColoredQuad(shape.x(), midY, shape.width(), sw, color, z);
    }

    private void paintPathShape(LayoutNode.ShapeNode shape, float z) {
        // SVG path: placeholder — fill with solid color quad
        if (shape.path().isEmpty()) return;
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            emitColoredQuad(shape.x(), shape.y(), shape.width(), shape.height(), color, z);
        }
    }

    private void paintConeShape(LayoutNode.ShapeNode shape, float z) {
        float cx = shape.x() + shape.width() / 2f;
        float cy = shape.y() + shape.height() / 2f;
        float rx = shape.width() / 2f;
        float ry = shape.height() / 2f;
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            emitCircleFan(cx, cy, rx, ry, color, z, 3);
        }
    }

    private void paintCapsuleShape(LayoutNode.ShapeNode shape, float z) {
        float x = shape.x(), y = shape.y(), w = shape.width(), h = shape.height();
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            float radius = Math.min(w, h) / 2f;
            if (w >= h) {
                emitColoredQuad(x + radius, y, w - 2 * radius, h, color, z);
                emitCircleFan(x + radius, y + h / 2f, radius, radius, color, z, 16);
                emitCircleFan(x + w - radius, y + h / 2f, radius, radius, color, z, 16);
            } else {
                emitColoredQuad(x, y + radius, w, h - 2 * radius, color, z);
                emitCircleFan(x + w / 2f, y + radius, radius, radius, color, z, 16);
                emitCircleFan(x + w / 2f, y + h - radius, radius, radius, color, z, 16);
            }
        }
    }

    /** Emit an elliptical ring (annulus with independent x/y radii). */
    private void emitEllipseRing(float cx, float cy,
                                 float innerRx, float innerRy,
                                 float outerRx, float outerRy,
                                 int color, float z, int segments) {
        for (int i = 0; i < segments; i++) {
            double a1 = 2.0 * Math.PI * i / segments - Math.PI / 2.0;
            double a2 = 2.0 * Math.PI * (i + 1) / segments - Math.PI / 2.0;
            float ox1 = cx + outerRx * (float) Math.cos(a1);
            float oy1 = cy + outerRy * (float) Math.sin(a1);
            float ox2 = cx + outerRx * (float) Math.cos(a2);
            float oy2 = cy + outerRy * (float) Math.sin(a2);
            float ix1 = cx + innerRx * (float) Math.cos(a1);
            float iy1 = cy + innerRy * (float) Math.sin(a1);
            float ix2 = cx + innerRx * (float) Math.cos(a2);
            float iy2 = cy + innerRy * (float) Math.sin(a2);
            float qx = Math.min(Math.min(ox1, ox2), Math.min(ix1, ix2));
            float qy = Math.min(Math.min(oy1, oy2), Math.min(iy1, iy2));
            float qw = Math.max(Math.max(ox1, ox2), Math.max(ix1, ix2)) - qx;
            float qh = Math.max(Math.max(oy1, oy2), Math.max(iy1, iy2)) - qy;
            if (qw > 0.1f || qh > 0.1f) {
                emitColoredQuad(qx, qy, Math.max(qw, 0.5f), Math.max(qh, 0.5f), color, z);
            }
        }
    }

    // ==================================================================================
    // Image Rendering
    // ==================================================================================

    private void paintImage(LayoutNode.ImageNode image, float z) {
        // Draw sphere-shaded circular background if shape is "circle"
        if ("circle".equals(image.shape())) {
            int bgColor = parseColor(
                    image.shapeBackground() != null ? image.shapeBackground() : "#3C3C4E",
                    0xFF3C3C4E);
            float cx = image.x() + image.width() / 2f;
            float cy = image.y() + image.height() / 2f;
            float rx = image.width() / 2f;
            float ry = image.height() / 2f;
            emitSphereDisc(cx, cy, rx, ry, bgColor, z, 48);
        }

        // Try actual image resource first (SVG or raster via Skia)
        if (paintImageResource(image, z)) return;

        // Fall back to emoji/alt text as MSDF glyphs
        if (image.alt() != null && !image.alt().isEmpty()) {
            String alt = image.alt();
            if (alt.isEmpty()) return;

            // Scale font size to fill the icon bounds (leaving ~25% padding for shaped icons)
            float fontSize;
            if (image.shape() != null) {
                // Inside a circle: use ~60% of the icon diameter for the glyph
                fontSize = Math.min(image.width(), image.height()) * 0.6f;
            } else {
                fontSize = Math.min(image.width(), image.height());
            }
            if (fontSize <= 0) fontSize = 15f;

            // Use image tint color if set, otherwise default text color
            int glyphColor = image.hasTintColor() ? image.tintColor() : COLOR_TEXT;

            try (Paragraph para = fontManager.paragraphFactory()
                    .fromText(alt, fontSize, Float.MAX_VALUE)) {
                float textX = image.x();
                float textY = image.y();
                if (image.shape() != null) {
                    ParagraphBounds b = paragraphBounds(para, alt);
                    float centeredX = centeredXForBounds(
                            image.x(), image.width(), para.maxIntrinsicWidth(), b.minX(), b.maxX(), fontSize);
                    textX = centeredX;
                    textY = image.y() + (image.height() - b.height()) / 2f - b.minY();
                }
                para.paint(new GlyphPaintContext(glyphColor, z + 0.01f), textX, textY);
            }
        }
    }

    // ==================================================================================
    // Elevated Rendering (3D body-face composition)
    // ==================================================================================

    private void paintNodeElevated(LayoutNode node, float baseY) {
        if (node.hidden()) return;
        switch (node) {
            case LayoutNode.BoxNode box -> paintBoxElevated(box, baseY);
            case LayoutNode.TextNode text -> paintTextOnPlane(text, baseY);
            case LayoutNode.ShapeNode shape -> paintShapeOnPlane(shape, baseY);
            case LayoutNode.ImageNode image -> paintImageElevated(image, baseY);
        }
    }

    private void paintBoxElevated(LayoutNode.BoxNode box, float baseY) {
        float childY = baseY;

        if (box.isElevated() && box.elevationSolid()) {
            // Generic elevated behavior: extrude "down" into the body so
            // surface-plane text and overlays remain visible above the slab.
            float wh = (float) box.elevation();
            emitColoredBox(box.x(), box.y(), box.width(), box.height(), wh, baseY - wh,
                    resolveBackgroundColor(box));
            childY = baseY + 0.001f;
        } else if (box.background() != null || box.backgroundColor() != -1) {
            // Flat colored quad on the XZ plane at baseY
            int bgColor = resolveBackgroundColor(box);
            emitColoredQuadXZ(box.x(), box.y(), box.width(), box.height(), bgColor, baseY);
        }

        // Children render on top
        for (LayoutNode child : box.children()) {
            paintNodeElevated(child, childY);
        }
    }

    private void paintImageElevated(LayoutNode.ImageNode image, float baseY) {
        if (image.hasModelResource()) {
            // Defer GLB model loading — collect placement for the spatial renderer
            float wx = (image.x() + image.width() / 2f) / pixelsPerUnit + originX;
            float wz = (image.y() + image.height() / 2f) / pixelsPerUnit + originZ;
            // Scale GLB models from the current layout cell size so responsive 2D layout
            // keeps 3D model fit consistent with the same scene geometry.
            float cellPx = Math.max(1f, Math.min(image.width(), image.height()));
            float scale = 0.001f * (cellPx / 34f); // 34px ~= previous chess-piece baseline
            modelPlacements.add(new ModelPlacement(
                    image.modelResource(), image.modelColor(), wx, baseY, wz, scale));
        } else {
            // Fall back to text/emoji rendering on the XZ plane
            paintImageOnPlane(image, baseY);
        }
    }

    /**
     * Render text on the XZ plane (horizontal surface) at the given Y height.
     */
    private void paintTextOnPlane(LayoutNode.TextNode text, float baseY) {
        if (text.content().isEmpty()) return;
        elevatedTextNodeCount++;
        if (!fontManager.hasUsableFonts()) {
            paintTextOnPlaneFallback(text, baseY);
            return;
        }

        int color = resolveTextColor(text);
        float fontSize = fontManager.fontSizeFor(text);

        MsdfAtlas primaryAtlas = fontManager.defaultAtlas();
        float ascent = primaryAtlas != null ? (float) primaryAtlas.ascent() : 0.8f;
        float baselineZ = text.y() + ascent * fontSize;
        float cursorX = text.x();

        for (int i = 0; i < text.content().length(); ) {
            int cp = text.content().codePointAt(i);
            var rg = fontManager.resolveGlyph(cp);
            if (rg != null && rg.metrics().advance() > 0) {
                var g = rg.metrics();
                if (rg.isColor()) {
                    emitColorLayersXZ(rg.colorInfo(), rg.atlas(),
                            fontSize, baselineZ, cursorX, color, baseY + 0.001f);
                } else if (g.uvRight() > g.uvLeft() && g.uvBottom() > g.uvTop()) {
                    float quadX = cursorX + (float)(g.planeLeft() * fontSize);
                    float quadZ = baselineZ - (float)(g.planeTop() * fontSize);
                    float quadW = (float)((g.planeRight() - g.planeLeft()) * fontSize);
                    float quadH = (float)((g.planeTop() - g.planeBottom()) * fontSize);
                    emitGlyphQuadXZ(quadX, quadZ, quadW, quadH, g, color, rg.atlas(), baseY + 0.001f);
                }
                cursorX += (float)(g.advance() * fontSize);
            } else if (rg != null) {
                cursorX += (float)(rg.metrics().advance() * fontSize);
            }
            i += Character.charCount(cp);
        }
    }

    /**
     * Render shape on the XZ plane at the given Y height.
     */
    private void paintShapeOnPlane(LayoutNode.ShapeNode shape, float baseY) {
        if (!shape.fill().isEmpty()) {
            int color = parseColor(shape.fill(), COLOR_ACCENT);
            // All shapes on XZ plane degrade to colored quads
            emitColoredQuadXZ(shape.x(), shape.y(), shape.width(), shape.height(), color, baseY);
        }
    }

    /**
     * Render an image (emoji fallback) on the XZ plane.
     */
    private void paintImageOnPlane(LayoutNode.ImageNode image, float baseY) {
        if (image.alt() == null || image.alt().isEmpty()) return;
        elevatedImageNodeCount++;
        if (!fontManager.hasUsableFonts()) {
            paintImageOnPlaneFallback(image, baseY);
            return;
        }

        String alt = image.alt();
        if (alt.isEmpty()) return;

        float fontSize = Math.min(image.width(), image.height()) * 0.6f;
        if (fontSize <= 0) fontSize = 15f;

        // Use image tint color if set, otherwise default text color
        int glyphColor = image.hasTintColor() ? image.tintColor() : COLOR_TEXT;

        try (Paragraph para = fontManager.paragraphFactory()
                .fromText(alt, fontSize, Float.MAX_VALUE)) {
            ParagraphBounds b = paragraphBounds(para, alt);
            float textX = centeredXForBounds(
                    image.x(), image.width(), para.maxIntrinsicWidth(), b.minX(), b.maxX(), fontSize);
            float textZ = image.y() + (image.height() - b.height()) / 2f - b.minY();
            para.paint(new GlyphPaintContextXZ(glyphColor, baseY + 0.002f), textX, textZ);
        }
    }

    private record ParagraphBounds(float minX, float minY, float maxX, float maxY) {
        float width() { return Math.max(0f, maxX - minX); }
        float height() { return Math.max(0f, maxY - minY); }
    }

    private static float centeredXForBounds(float boxX, float boxWidth, float advance,
                                            float minX, float maxX, float fontSize) {
        float visualCenterX = (minX + maxX) * 0.5f;
        float logicalCenterX = Math.max(0f, advance) * 0.5f;
        float correction = clamp(visualCenterX - logicalCenterX,
                -fontSize * 0.18f, fontSize * 0.18f);
        return boxX + boxWidth * 0.5f - logicalCenterX - correction;
    }

    private static ParagraphBounds paragraphBounds(Paragraph paragraph, String text) {
        if (text == null || text.isEmpty()) {
            return new ParagraphBounds(0f, 0f, paragraph.maxIntrinsicWidth(), paragraph.height());
        }
        List<Paragraph.Rect> rects = paragraph.rectsForRange(0, text.length());
        if (rects == null || rects.isEmpty()) {
            return new ParagraphBounds(0f, 0f, paragraph.maxIntrinsicWidth(), paragraph.height());
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Paragraph.Rect r : rects) {
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.width());
            maxY = Math.max(maxY, r.y() + r.height());
        }
        return new ParagraphBounds(minX, minY, maxX, maxY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Emit stacked MSDF quads on the XZ plane for COLRv0 color layers.
     */
    private void emitColorLayersXZ(ColrGlyphInfo colorInfo, MsdfAtlas atlas,
                                   float fontSize, float baselineZ, float cursorX,
                                   int foregroundArgb, float baseY) {
        float layerY = baseY;
        for (ColrGlyphInfo.Layer layer : colorInfo.layers()) {
            MsdfAtlas.GlyphMetrics layerMetrics = atlas.glyphByIndex(layer.glyphIndex());
            if (layerMetrics == null) continue;
            if (layerMetrics.uvRight() <= layerMetrics.uvLeft()) continue;

            int color = (layer.argbColor() == ColrGlyphInfo.FOREGROUND_COLOR)
                    ? foregroundArgb : layer.argbColor();

            float quadX = cursorX + (float)(layerMetrics.planeLeft() * fontSize);
            float quadZ = baselineZ - (float)(layerMetrics.planeTop() * fontSize);
            float quadW = (float)((layerMetrics.planeRight() - layerMetrics.planeLeft()) * fontSize);
            float quadH = (float)((layerMetrics.planeTop() - layerMetrics.planeBottom()) * fontSize);

            emitGlyphQuadXZ(quadX, quadZ, quadW, quadH, layerMetrics, color, atlas, layerY);
            layerY += 0.001f;
        }
    }

    /**
     * Elevated text fallback path when MSDF atlases are unavailable.
     * Rasterizes text with Skia paragraph API and places it as a textured XZ quad.
     */
    private void paintTextOnPlaneFallback(LayoutNode.TextNode text, float baseY) {
        int color = resolveTextColor(text);
        float fontSize = Math.max(8f, fontManager.fontSizeFor(text));

        int rasterW = Math.max(1, Math.round(text.width() + 4f));
        int rasterH = Math.max(1, Math.round(text.height() + 2f));
        String[] families = fontManager.familiesFor(text.fontFamily());
        String familiesKey = String.join(",", families);
        String key = "txt:" + text.content() + "|" + rasterW + "x" + rasterH
                + "|" + Integer.toHexString(color) + "|" + fontSize + "|" + familiesKey;

        Texture tex = textTextureCache.get(key);
        if (tex == null) {
            byte[] pixels = rasterizeText(text.content(), rasterW, rasterH, color, fontSize, families);
            if (pixels == null) return;

            tex = new Texture.Builder()
                    .width(rasterW)
                    .height(rasterH)
                    .levels(1)
                    .format(Texture.InternalFormat.SRGB8_A8)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .usage(Texture.Usage.DEFAULT)
                    .build(engine);

            ByteBuffer pixelBuf = ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
            pixelBuf.put(pixels);
            pixelBuf.flip();
            pendingPixelBuffers.add(pixelBuf);
            tex.setImage(engine, 0, new Texture.PixelBufferDescriptor(
                    pixelBuf, Texture.Format.RGBA, Texture.Type.UBYTE));
            textTextureCache.put(key, tex);
        }

        emitTexturedQuadXZ(text.x() - 2f, text.y(), rasterW, rasterH, tex, baseY + 0.002f);
        elevatedFallbackTextQuadCount++;
    }

    /**
     * Elevated emoji/image-alt fallback when MSDF atlases are unavailable.
     */
    private void paintImageOnPlaneFallback(LayoutNode.ImageNode image, float baseY) {
        String alt = image.alt();
        if (alt.isEmpty()) return;

        float fontSize = Math.max(8f, Math.min(image.width(), image.height()) * 0.65f);
        float targetW = Math.max(1f, image.width());
        float targetH = Math.max(1f, image.height());
        int color = image.hasTintColor() ? image.tintColor() : COLOR_TEXT;

        int rasterW = Math.max(1, Math.round(targetW));
        int rasterH = Math.max(1, Math.round(targetH));
        String[] families = fontManager.familiesFor(null);
        String key = "imgtxt:" + alt + "|" + rasterW + "x" + rasterH
                + "|" + Integer.toHexString(color) + "|" + fontSize + "|" + String.join(",", families);

        Texture tex = textTextureCache.get(key);
        if (tex == null) {
            byte[] pixels = rasterizeText(alt, rasterW, rasterH, color, fontSize, families);
            if (pixels == null) return;
            tex = new Texture.Builder()
                    .width(rasterW)
                    .height(rasterH)
                    .levels(1)
                    .format(Texture.InternalFormat.SRGB8_A8)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .usage(Texture.Usage.DEFAULT)
                    .build(engine);
            ByteBuffer pixelBuf = ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
            pixelBuf.put(pixels);
            pixelBuf.flip();
            pendingPixelBuffers.add(pixelBuf);
            tex.setImage(engine, 0, new Texture.PixelBufferDescriptor(
                    pixelBuf, Texture.Format.RGBA, Texture.Type.UBYTE));
            textTextureCache.put(key, tex);
        }

        emitTexturedQuadXZ(image.x(), image.y(), targetW, targetH, tex, baseY + 0.002f);
        elevatedFallbackImageQuadCount++;
    }

    private byte[] rasterizeText(String text, int w, int h, int color, float fontSize, String[] families) {
        ImageInfo info = new ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        try (Bitmap bmp = new Bitmap()) {
            bmp.allocPixels(info);
            try (Canvas c = new Canvas(bmp);
                 TextStyle textStyle = new TextStyle();
                 ParagraphStyle paraStyle = new ParagraphStyle()) {
                c.clear(0x00000000);
                textStyle.setFontFamilies(families)
                        .setFontSize(fontSize)
                        .setColor(color);
                try (ParagraphBuilder builder = new ParagraphBuilder(paraStyle, fallbackFontCollection)) {
                    builder.pushStyle(textStyle);
                    builder.addText(text);
                    try (io.github.humbleui.skija.paragraph.Paragraph para = builder.build()) {
                        para.layout(w);
                        para.paint(c, 0, 0);
                    }
                }
            }
            return bmp.readPixels();
        } catch (Throwable t) {
            log.debug("Text rasterization fallback failed for '{}'", text, t);
            return null;
        }
    }

    // ==================================================================================
    // 3D Geometry for Elevated Rendering
    // ==================================================================================

    /**
     * Emit a colored 3D box (slab) in XZ plane space.
     *
     * <p>Maps pixel coordinates to world XZ: X axis = pixel X, Z axis = pixel Y.
     * The box rises from baseY to baseY + height along the world Y axis.
     */
    private void emitColoredBox(float px, float py, float pw, float ph,
                                 float boxHeight, float baseY, int argbColor) {
        if (pw <= 0 || ph <= 0 || boxHeight <= 0) return;

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);
        float wx = px / pixelsPerUnit + originX;
        float wz = py / pixelsPerUnit + originZ;  // pixel Y → world Z
        float ww = pw / pixelsPerUnit;
        float wd = ph / pixelsPerUnit;

        // 8 vertices of a box, Y-up
        float x0 = wx, x1 = wx + ww;
        float y0 = baseY, y1 = baseY + boxHeight;
        float z0 = wz, z1 = wz + wd;

        float[] positions = {
            // bottom face (y0)
            x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1,
            // top face (y1)
            x0, y1, z0,  x1, y1, z0,  x1, y1, z1,  x0, y1, z1,
        };
        int[] colors = new int[8];
        Arrays.fill(colors, abgr);

        short[] indices = {
            // bottom
            0, 2, 1,  0, 3, 2,
            // top
            4, 5, 6,  4, 6, 7,
            // front (z0)
            0, 1, 5,  0, 5, 4,
            // back (z1)
            2, 3, 7,  2, 7, 6,
            // left (x0)
            0, 4, 7,  0, 7, 3,
            // right (x1)
            1, 2, 6,  1, 6, 5,
        };

        VertexBuffer vb = buildColoredVB(positions, colors);
        IndexBuffer ib = buildIB(indices);
        float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f, cz = (z0 + z1) / 2f;
        Box bbox = new Box(cx, cy, cz, ww / 2f, boxHeight / 2f, wd / 2f);
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, 36, bbox, mi, true, true);
    }

    /**
     * Emit a colored quad on the XZ plane at the given Y height.
     * Maps pixel X → world X, pixel Y → world Z.
     */
    private void emitColoredQuadXZ(float px, float py, float pw, float ph,
                                     int argbColor, float y) {
        if (pw <= 0 || ph <= 0) return;

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);
        float wx = px / pixelsPerUnit + originX;
        float wz = py / pixelsPerUnit + originZ;
        float ww = pw / pixelsPerUnit;
        float wd = ph / pixelsPerUnit;

        float[] positions = {
            wx,      y, wz,
            wx + ww, y, wz,
            wx + ww, y, wz + wd,
            wx,      y, wz + wd,
        };
        int[] colors = {abgr, abgr, abgr, abgr};
        short[] indices = XZ_QUAD_UP_INDICES;

        VertexBuffer vb = buildColoredVB(positions, colors);
        IndexBuffer ib = buildIB(indices);
        Box bbox = new Box(wx + ww / 2, y, wz + wd / 2, ww / 2, 0.01f, wd / 2);
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, 6, bbox, mi, false, true);
    }

    /**
     * Emit a textured quad on the XZ plane at the given Y height.
     */
    private void emitTexturedQuadXZ(float px, float py, float pw, float ph,
                                    Texture tex, float y) {
        if (pw <= 0 || ph <= 0) return;

        float wx = px / pixelsPerUnit + originX;
        float wz = py / pixelsPerUnit + originZ;
        float ww = pw / pixelsPerUnit;
        float wd = ph / pixelsPerUnit;

        float[] positions = {
                wx,      y, wz,
                wx + ww, y, wz,
                wx + ww, y, wz + wd,
                wx,      y, wz + wd,
        };
        int[] colors = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        VertexBuffer vb = buildMsdfVB(positions, QUAD_UVS_FRONT, colors);
        IndexBuffer ib = buildIB(XZ_TEXT_QUAD_TOP_FRONT_INDICES);
        Box bbox = new Box(wx + ww / 2f, y, wz + wd / 2f, ww / 2f, 0.01f, wd / 2f);
        MaterialInstance mi = texturedMaterial().createInstance();
        TextureSampler sampler = new TextureSampler();
        sampler.setMagFilter(TextureSampler.MagFilter.NEAREST);
        sampler.setMinFilter(TextureSampler.MinFilter.NEAREST);
        mi.setParameter("baseColorMap", tex, sampler);
        emitEntity(vb, ib, 6, bbox, mi, false, false);
    }

    /**
     * Emit a glyph quad on the XZ plane (horizontal surface).
     * Maps pixel X → world X, pixel Y → world Z. Glyph faces upward (+Y).
     */
    private void emitGlyphQuadXZ(float px, float pz, float pw, float ph,
                                   MsdfAtlas.GlyphMetrics glyph, int argbColor,
                                   MsdfAtlas atlas, float y) {
        if (pw <= 0 || ph <= 0) return;

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);
        float wx = px / pixelsPerUnit + originX;
        float wz = pz / pixelsPerUnit + originZ;
        float ww = pw / pixelsPerUnit;
        float wd = ph / pixelsPerUnit;

        float[] positions = {
            wx,      y, wz,
            wx + ww, y, wz,
            wx + ww, y, wz + wd,
            wx,      y, wz + wd,
        };

        float uvL = (float) glyph.uvLeft(), uvR = (float) glyph.uvRight();
        float uvTopV = 1.0f - (float) glyph.uvTop();
        float uvBotV = 1.0f - (float) glyph.uvBottom();

        float[] uvs = {
            uvL, uvTopV,
            uvR, uvTopV,
            uvR, uvBotV,
            uvL, uvBotV,
        };
        int[] colors = {abgr, abgr, abgr, abgr};
        short[] indices = XZ_TEXT_QUAD_TOP_FRONT_INDICES;

        VertexBuffer vb = buildMsdfVB(positions, uvs, colors);
        IndexBuffer ib = buildIB(indices);
        Box bbox = new Box(wx + ww / 2, y, wz + wd / 2, ww / 2, 0.01f, wd / 2);

        MaterialInstance mi = textRenderer.msdfTextMaterial().createInstance();
        TextureSampler sampler = new TextureSampler();
        sampler.setMagFilter(TextureSampler.MagFilter.LINEAR);
        sampler.setMinFilter(TextureSampler.MinFilter.LINEAR);
        mi.setParameter("msdfAtlas", atlas.texture(), sampler);
        mi.setParameter("pxRange", (float) atlas.pxRange());
        mi.setParameter("atlasSize", (float) atlas.atlasWidth(), (float) atlas.atlasHeight());

        float uvW = (float)(glyph.uvRight() - glyph.uvLeft());
        float uvH = Math.abs((float)(glyph.uvBottom() - glyph.uvTop()));
        float glyphTexels = Math.max(uvW * atlas.atlasWidth(), uvH * atlas.atlasHeight());
        float screenPxRange = glyphTexels > 0
                ? (float)(atlas.pxRange() * Math.max(pw, ph) / glyphTexels) : 1.0f;
        mi.setParameter("screenPxRange", Math.max(screenPxRange, 1.0f));

        emitEntity(vb, ib, 6, bbox, mi, false, false);
    }

    // ==================================================================================
    // Geometry Helpers (shared VB/IB/Entity wiring)
    // ==================================================================================

    /**
     * Create a Filament entity from raw geometry and add it to the scene.
     * This is the shared tail of every emit* method.
     */
    private void emitEntity(VertexBuffer vb, IndexBuffer ib, int indexCount,
                            Box boundingBox, MaterialInstance mi,
                            boolean castShadows, boolean receiveShadows) {
        PrimitiveMeshes.Mesh mesh = new PrimitiveMeshes.Mesh(vb, ib, indexCount, boundingBox);
        meshes.add(mesh);
        materialInstances.add(mi);

        int entity = EntityManager.get().create();
        entities.add(entity);

        new RenderableManager.Builder(1)
                .boundingBox(boundingBox)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, indexCount)
                .material(0, mi)
                .culling(false)
                .castShadows(castShadows)
                .receiveShadows(receiveShadows)
                .build(engine, entity);

        scene.addEntity(entity);
    }

    /**
     * Build a VertexBuffer with POSITION (float3) + COLOR (ubyte4) attributes.
     */
    private VertexBuffer buildColoredVB(float[] positions, int[] colors) {
        int vertexCount = positions.length / 3;
        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, allocateFloat(positions));
        vb.setBufferAt(engine, 1, allocateInt(colors));
        return vb;
    }

    /**
     * Build a VertexBuffer with POSITION (float3) + UV0 (float2) + COLOR (ubyte4).
     */
    private VertexBuffer buildMsdfVB(float[] positions, float[] uvs, int[] colors) {
        int vertexCount = positions.length / 3;
        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(3)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.UV0, 1,
                        VertexBuffer.AttributeType.FLOAT2, 0, 8)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 2,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, allocateFloat(positions));
        vb.setBufferAt(engine, 1, allocateFloat(uvs));
        vb.setBufferAt(engine, 2, allocateInt(colors));
        return vb;
    }

    /**
     * Build an IndexBuffer from short indices.
     */
    private IndexBuffer buildIB(short[] indices) {
        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indices.length)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, allocateShort(indices));
        return ib;
    }

    // Package-private for renderer-orientation tests.
    static float[] canonicalQuadUvsTopLeft() {
        return QUAD_UVS_FRONT.clone();
    }

    // Package-private for renderer-orientation tests.
    static short[] canonicalXzQuadUpIndices() {
        return XZ_QUAD_UP_INDICES.clone();
    }

    // Package-private for renderer-orientation tests.
    static short[] canonicalXzQuadTopFrontIndices() {
        return XZ_TEXT_QUAD_TOP_FRONT_INDICES.clone();
    }

    /**
     * Compute a bounding box encompassing 4 rotated quad corners.
     */
    private Box computeQuadBBox(float wx0, float wy0, float wx1, float wy1,
                                float wx2, float wy2, float wx3, float wy3, float z) {
        float minX = Math.min(Math.min(wx0, wx1), Math.min(wx2, wx3));
        float maxX = Math.max(Math.max(wx0, wx1), Math.max(wx2, wx3));
        float minY = Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3));
        float maxY = Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3));
        return new Box((minX + maxX) / 2f, (minY + maxY) / 2f, z,
                       (maxX - minX) / 2f, (maxY - minY) / 2f, 0.01f);
    }

    // ==================================================================================
    // Geometry Emission
    // ==================================================================================

    /**
     * Emit a colored quad using the flat_color material.
     * Coordinates are in pixel space (top-left origin, Y-down).
     */
    public void emitColoredQuad(float px, float py, float pw, float ph,
                                 int argbColor, float z) {
        if (pw <= 0 || ph <= 0) return;
        if (isClipped(px, py, pw, ph)) return;

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);

        // Compute the 4 corner positions in pixel space
        float x0 = px, y0 = py;               // top-left
        float x1 = px + pw, y1 = py;          // top-right
        float x2 = px + pw, y2 = py + ph;     // bottom-right
        float x3 = px, y3 = py + ph;          // bottom-left

        // Apply 2D rotation if active (rotate pixel coords around rotation center)
        if (rotationActive) {
            float[] r0 = rotatePoint(x0, y0);  x0 = r0[0]; y0 = r0[1];
            float[] r1 = rotatePoint(x1, y1);  x1 = r1[0]; y1 = r1[1];
            float[] r2 = rotatePoint(x2, y2);  x2 = r2[0]; y2 = r2[1];
            float[] r3 = rotatePoint(x3, y3);  x3 = r3[0]; y3 = r3[1];
        }

        // Convert each corner from pixel to world coords
        float wx0 = x0 / pixelsPerUnit + originX, wy0 = -y0 / pixelsPerUnit + originY;
        float wx1 = x1 / pixelsPerUnit + originX, wy1 = -y1 / pixelsPerUnit + originY;
        float wx2 = x2 / pixelsPerUnit + originX, wy2 = -y2 / pixelsPerUnit + originY;
        float wx3 = x3 / pixelsPerUnit + originX, wy3 = -y3 / pixelsPerUnit + originY;

        // Flush if batch is nearing USHORT index limit
        if (flatColorBatch.isFull()) flushFlatColorBatch();

        // Batch into flat-color accumulator (flushed at end of paint)
        flatColorBatch.addQuad(wx0, wy0, wx1, wy1, wx2, wy2, wx3, wy3, z, abgr);
    }

    /**
     * Emit a 3D rectangular slab on the XY plane with Z-axis depth.
     * Front face at z+depth (toward camera), side edges connect to z (back).
     * Sides use a darkened color for subtle 3D appearance.
     */
    private void emitColoredSlabXY(float px, float py, float pw, float ph,
                                     int argbColor, float z, float depth) {
        if (pw <= 0 || ph <= 0 || depth <= 0.0001f) {
            emitColoredQuad(px, py, pw, ph, argbColor, z + depth);
            return;
        }

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);
        int sideAbgr = MsdfTextRenderer.argbToAbgr(darkenColor(argbColor, 0.6f));
        float zBack = z;
        float zFront = z + depth;

        // 4 corners in pixel space
        float x0 = px, y0 = py;
        float x1 = px + pw, y1 = py;
        float x2 = px + pw, y2 = py + ph;
        float x3 = px, y3 = py + ph;

        if (rotationActive) {
            float[] r0 = rotatePoint(x0, y0); x0 = r0[0]; y0 = r0[1];
            float[] r1 = rotatePoint(x1, y1); x1 = r1[0]; y1 = r1[1];
            float[] r2 = rotatePoint(x2, y2); x2 = r2[0]; y2 = r2[1];
            float[] r3 = rotatePoint(x3, y3); x3 = r3[0]; y3 = r3[1];
        }

        // Convert to world coords
        float wx0 = x0 / pixelsPerUnit + originX, wy0 = -y0 / pixelsPerUnit + originY;
        float wx1 = x1 / pixelsPerUnit + originX, wy1 = -y1 / pixelsPerUnit + originY;
        float wx2 = x2 / pixelsPerUnit + originX, wy2 = -y2 / pixelsPerUnit + originY;
        float wx3 = x3 / pixelsPerUnit + originX, wy3 = -y3 / pixelsPerUnit + originY;

        float[] positions = {
            // Back face (v0-v3)
            wx0, wy0, zBack,   wx1, wy1, zBack,   wx2, wy2, zBack,   wx3, wy3, zBack,
            // Front face (v4-v7)
            wx0, wy0, zFront,  wx1, wy1, zFront,  wx2, wy2, zFront,  wx3, wy3, zFront,
        };
        int[] colors = {
            sideAbgr, sideAbgr, sideAbgr, sideAbgr,  // back
            abgr, abgr, abgr, abgr,                    // front
        };
        short[] indices = {
            // Front face (toward camera, CCW from front)
            4, 5, 6,  4, 6, 7,
            // Back face (away from camera)
            0, 2, 1,  0, 3, 2,
            // Top side
            0, 1, 5,  0, 5, 4,
            // Bottom side
            2, 3, 7,  2, 7, 6,
            // Left side
            0, 4, 7,  0, 7, 3,
            // Right side
            1, 2, 6,  1, 6, 5,
        };

        VertexBuffer vb = buildColoredVB(positions, colors);
        IndexBuffer ib = buildIB(indices);
        float minWx = Math.min(Math.min(wx0, wx1), Math.min(wx2, wx3));
        float maxWx = Math.max(Math.max(wx0, wx1), Math.max(wx2, wx3));
        float minWy = Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3));
        float maxWy = Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3));
        Box bbox = new Box((minWx + maxWx) / 2f, (minWy + maxWy) / 2f, (zBack + zFront) / 2f,
                (maxWx - minWx) / 2f, (maxWy - minWy) / 2f, depth / 2f);
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, 36, bbox, mi, false, false);
    }

    /**
     * Darken an ARGB color by a factor (0.0 = black, 1.0 = unchanged).
     */
    private static int darkenColor(int argb, float factor) {
        int a = (argb >> 24) & 0xFF;
        int r = (int)(((argb >> 16) & 0xFF) * factor);
        int g = (int)(((argb >> 8) & 0xFF) * factor);
        int b = (int)((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Emit a single MSDF glyph quad using the msdf_text material.
     */
    private void emitGlyphQuad(float px, float py, float pw, float ph,
                                MsdfAtlas.GlyphMetrics glyph, int argbColor,
                                MsdfAtlas atlas, float z) {
        if (pw <= 0 || ph <= 0) return;
        if (isClipped(px, py, pw, ph)) return;

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);

        // Compute 4 corners in pixel space
        float x0 = px, y0 = py;
        float x1 = px + pw, y1 = py;
        float x2 = px + pw, y2 = py + ph;
        float x3 = px, y3 = py + ph;

        // Apply rotation if active
        if (rotationActive) {
            float[] r0 = rotatePoint(x0, y0);  x0 = r0[0]; y0 = r0[1];
            float[] r1 = rotatePoint(x1, y1);  x1 = r1[0]; y1 = r1[1];
            float[] r2 = rotatePoint(x2, y2);  x2 = r2[0]; y2 = r2[1];
            float[] r3 = rotatePoint(x3, y3);  x3 = r3[0]; y3 = r3[1];
        }

        // Convert to world coords
        float wx0 = x0 / pixelsPerUnit + originX, wy0 = -y0 / pixelsPerUnit + originY;
        float wx1 = x1 / pixelsPerUnit + originX, wy1 = -y1 / pixelsPerUnit + originY;
        float wx2 = x2 / pixelsPerUnit + originX, wy2 = -y2 / pixelsPerUnit + originY;
        float wx3 = x3 / pixelsPerUnit + originX, wy3 = -y3 / pixelsPerUnit + originY;

        float uvL = (float) glyph.uvLeft();
        float uvR = (float) glyph.uvRight();
        // V axis: stored UVs are image-space (V=0 at top of atlas).
        // Filament uses OpenGL convention (V=0 at bottom). Flip V.
        float uvTopV = 1.0f - (float) glyph.uvTop();
        float uvBotV = 1.0f - (float) glyph.uvBottom();

        // Compute screenPxRange on the Java side (bypasses fwidth() in shader).
        float uvW = (float)(glyph.uvRight() - glyph.uvLeft());
        float uvH = Math.abs((float)(glyph.uvBottom() - glyph.uvTop()));
        float glyphTexels = Math.max(uvW * atlas.atlasWidth(), uvH * atlas.atlasHeight());
        float screenPxRange = glyphTexels > 0
                ? (float)(atlas.pxRange() * Math.max(pw, ph) / glyphTexels)
                : 1.0f;

        // Batch into per-atlas MSDF accumulator (flushed at end of paint)
        MsdfBatchAccumulator batch = msdfBatches.computeIfAbsent(atlas, k -> new MsdfBatchAccumulator());
        if (batch.vertexCount >= MsdfBatchAccumulator.MAX_VERTICES) {
            flushMsdfBatches();
            batch = msdfBatches.computeIfAbsent(atlas, k -> new MsdfBatchAccumulator());
        }
        batch.addQuad(wx0, wy0, wx1, wy1, wx2, wy2, wx3, wy3, z,
                uvL, uvTopV, uvR, uvBotV, abgr, Math.max(screenPxRange, 1.0f));
    }

    /**
     * Emit a filled circle as a triangle fan (flat color).
     * Applies 2D rotation if active.
     */
    private void emitCircleFan(float cx, float cy, float rx, float ry,
                                int argbColor, float z, int segments) {
        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);

        int vertexCount = segments + 2; // center + rim + closing vertex
        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];

        // Center — rotate pixel point, then convert to world
        float pcx = cx, pcy = cy;
        if (rotationActive) {
            float[] rc = rotatePoint(pcx, pcy);
            pcx = rc[0]; pcy = rc[1];
        }
        float wcx = pcx / pixelsPerUnit + originX;
        float wcy = -pcy / pixelsPerUnit + originY;

        positions[0] = wcx;
        positions[1] = wcy;
        positions[2] = z;
        colors[0] = abgr;

        // World-space radii (for bounding box)
        float wrx = rx / pixelsPerUnit;
        float wry = ry / pixelsPerUnit;

        // Rim vertices — compute pixel position, rotate, convert to world
        for (int i = 0; i <= segments; i++) {
            float theta = (float)(2 * Math.PI * i / segments);
            float rimPx = cx + rx * (float) Math.cos(theta);
            float rimPy = cy + ry * (float) Math.sin(theta);
            if (rotationActive) {
                float[] rr = rotatePoint(rimPx, rimPy);
                rimPx = rr[0]; rimPy = rr[1];
            }
            int vi = (i + 1) * 3;
            positions[vi] = rimPx / pixelsPerUnit + originX;
            positions[vi + 1] = -rimPy / pixelsPerUnit + originY;
            positions[vi + 2] = z;
            colors[i + 1] = abgr;
        }

        emitTriangleFan(wcx, wcy, wrx, wry, z, positions, colors, segments);
    }

    /**
     * Emit a rounded rectangle as a triangle fan.
     *
     * <p>Traces the rounded rectangle outline (four corner arcs connected
     * by straight edges) and fans from the center. Mirrors Skia's RRect
     * rendering for pill-shaped progress bars and other rounded containers.
     *
     * @param px             pixel X of rectangle
     * @param py             pixel Y of rectangle
     * @param pw             pixel width
     * @param ph             pixel height
     * @param r              corner radius in pixels (clamped to min(w,h)/2)
     * @param argbColor      fill color in ARGB
     * @param z              depth
     * @param segsPerCorner  arc segments per corner (e.g. 8)
     */
    private void emitRoundedRect(float px, float py, float pw, float ph,
                                  float r, int argbColor, float z, int segsPerCorner) {
        if (pw <= 0 || ph <= 0) return;
        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);

        r = Math.min(r, Math.min(pw, ph) / 2f);

        // Total perimeter vertices: 4 corners × (segsPerCorner + 1), minus 4 shared endpoints
        int perimeterCount = 4 * segsPerCorner + 4;
        int vertexCount = perimeterCount + 2; // +1 center, +1 closing
        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];

        // Center point
        float cx = px + pw / 2f, cy = py + ph / 2f;
        float pcx = cx, pcy = cy;
        if (rotationActive) {
            float[] rc = rotatePoint(pcx, pcy);
            pcx = rc[0]; pcy = rc[1];
        }
        float wcx = pcx / pixelsPerUnit + originX;
        float wcy = -pcy / pixelsPerUnit + originY;
        positions[0] = wcx;
        positions[1] = wcy;
        positions[2] = z;
        colors[0] = abgr;

        // Corner centers
        float[][] corners = {
            {px + pw - r, py + r},         // top-right
            {px + pw - r, py + ph - r},    // bottom-right
            {px + r, py + ph - r},         // bottom-left
            {px + r, py + r},              // top-left
        };
        // Start angles for each corner arc (radians)
        float[] startAngles = {
            (float)(-Math.PI / 2),  // top-right: -90° to 0°
            0f,                      // bottom-right: 0° to 90°
            (float)(Math.PI / 2),   // bottom-left: 90° to 180°
            (float)(Math.PI),       // top-left: 180° to 270°
        };

        int vi = 1; // vertex index (0 is center)
        for (int corner = 0; corner < 4; corner++) {
            float ccx = corners[corner][0];
            float ccy = corners[corner][1];
            float startAngle = startAngles[corner];

            for (int s = 0; s <= segsPerCorner; s++) {
                float theta = startAngle + (float)(Math.PI / 2) * s / segsPerCorner;
                float rimPx = ccx + r * (float) Math.cos(theta);
                float rimPy = ccy + r * (float) Math.sin(theta);
                if (rotationActive) {
                    float[] rr = rotatePoint(rimPx, rimPy);
                    rimPx = rr[0]; rimPy = rr[1];
                }
                int idx = vi * 3;
                positions[idx] = rimPx / pixelsPerUnit + originX;
                positions[idx + 1] = -rimPy / pixelsPerUnit + originY;
                positions[idx + 2] = z;
                colors[vi] = abgr;
                vi++;
            }
        }

        // Closing vertex = copy of first perimeter vertex (index 1)
        positions[vi * 3] = positions[3];
        positions[vi * 3 + 1] = positions[4];
        positions[vi * 3 + 2] = positions[5];
        colors[vi] = abgr;

        float wrx = pw / 2f / pixelsPerUnit;
        float wry = ph / 2f / pixelsPerUnit;
        emitTriangleFan(wcx, wcy, wrx, wry, z, positions, colors, perimeterCount);
    }

    /**
     * Emit a sphere-shaded disc using hemisphere Blinn-Phong lighting.
     *
     * <p>Uses concentric rings with per-vertex colors computed from the
     * hemisphere normal at each point. Creates a convincing 3D sphere
     * appearance from a flat disc.
     *
     * <p>Light comes from the upper-left toward the viewer, giving a
     * specular highlight offset from center and natural rim darkening.
     */
    private void emitSphereDisc(float cx, float cy, float rx, float ry,
                                 int argbBaseColor, float z, int segments) {
        float wcx = cx / pixelsPerUnit + originX;
        float wcy = -cy / pixelsPerUnit + originY;
        float wrx = rx / pixelsPerUnit;
        float wry = ry / pixelsPerUnit;

        // Base color components
        float baseR = ((argbBaseColor >> 16) & 0xFF) / 255f;
        float baseG = ((argbBaseColor >> 8) & 0xFF) / 255f;
        float baseB = (argbBaseColor & 0xFF) / 255f;

        // Light direction (upper-left, toward viewer) in screen-space Y-down
        float lx = -0.4f, ly = -0.4f, lz = 0.84f;
        float lLen = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= lLen; ly /= lLen; lz /= lLen;

        // Half-vector for specular (view = straight toward viewer)
        float hx = lx, hy = ly, hz = lz + 1f;
        float hLen = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        hx /= hLen; hy /= hLen; hz /= hLen;

        // Use 3 concentric rings for smooth shading
        int rings = 3;
        float[] ringRadii = {0f, 0.5f, 1.0f};

        // Vertices: 1 center + rings[1..] * (segments+1)
        int rimVertices = (rings - 1) * (segments + 1);
        int vertexCount = 1 + rimVertices;
        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];

        // Center vertex: normal (0, 0, 1)
        positions[0] = wcx;
        positions[1] = wcy;
        positions[2] = z;
        colors[0] = sphereShade(0, 0, 1, lx, ly, lz, hx, hy, hz,
                                baseR, baseG, baseB);

        // Ring vertices
        int vi = 1;
        for (int r = 1; r < rings; r++) {
            float radius = ringRadii[r];
            for (int s = 0; s <= segments; s++) {
                float theta = (float)(2 * Math.PI * s / segments);
                float dx = (float) Math.cos(theta) * radius;
                float dy = (float) Math.sin(theta) * radius;

                positions[vi * 3]     = wcx + wrx * dx;
                positions[vi * 3 + 1] = wcy + wry * dy;
                positions[vi * 3 + 2] = z;

                // Hemisphere normal at (dx, dy)
                float nz = (float) Math.sqrt(Math.max(0, 1 - dx * dx - dy * dy));
                colors[vi] = sphereShade(dx, dy, nz, lx, ly, lz, hx, hy, hz,
                                         baseR, baseG, baseB);
                vi++;
            }
        }

        // Indices: fan from center to ring 1, then strips between rings
        int fanTriangles = segments;
        int stripTriangles = (rings - 2) * segments * 2;
        int indexCount = (fanTriangles + stripTriangles) * 3;
        short[] indices = new short[indexCount];
        int ii = 0;

        // Fan: center (0) to ring 1 vertices (1..segments+1)
        for (int s = 0; s < segments; s++) {
            indices[ii++] = 0;
            indices[ii++] = (short)(1 + s);
            indices[ii++] = (short)(1 + s + 1);
        }

        // Strips: ring r to ring r+1
        for (int r = 1; r < rings - 1; r++) {
            int innerStart = 1 + (r - 1) * (segments + 1);
            int outerStart = 1 + r * (segments + 1);
            for (int s = 0; s < segments; s++) {
                // Triangle 1
                indices[ii++] = (short)(innerStart + s);
                indices[ii++] = (short)(outerStart + s);
                indices[ii++] = (short)(outerStart + s + 1);
                // Triangle 2
                indices[ii++] = (short)(innerStart + s);
                indices[ii++] = (short)(outerStart + s + 1);
                indices[ii++] = (short)(innerStart + s + 1);
            }
        }

        VertexBuffer vb = buildColoredVB(positions, colors);
        IndexBuffer ib = buildIB(indices);
        Box bbox = new Box(wcx, wcy, z, wrx, wry, 0.01f);
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, indexCount, bbox, mi, false, false);
    }

    /**
     * Compute sphere-shaded ABGR color for a point on a hemisphere.
     *
     * <p>Uses Blinn-Phong shading with ambient, diffuse, and specular terms.
     *
     * @param nx, ny, nz  surface normal at the point
     * @param lx, ly, lz  light direction (normalized)
     * @param hx, hy, hz  half-vector for specular (normalized)
     * @param baseR/G/B   base color components [0..1]
     */
    private static int sphereShade(float nx, float ny, float nz,
                                    float lx, float ly, float lz,
                                    float hx, float hy, float hz,
                                    float baseR, float baseG, float baseB) {
        float ambient = 0.30f;
        float diffuse = Math.max(0, nx * lx + ny * ly + nz * lz);
        float specDot = Math.max(0, nx * hx + ny * hy + nz * hz);
        float specular = (float) Math.pow(specDot, 40);

        float r = Math.min(1, baseR * (ambient + diffuse * 0.7f) + specular * 0.35f);
        float g = Math.min(1, baseG * (ambient + diffuse * 0.7f) + specular * 0.35f);
        float b = Math.min(1, baseB * (ambient + diffuse * 0.7f) + specular * 0.35f);

        int ir = (int)(r * 255) & 0xFF;
        int ig = (int)(g * 255) & 0xFF;
        int ib = (int)(b * 255) & 0xFF;
        // ABGR format for Filament vertex colors
        return 0xFF000000 | (ib << 16) | (ig << 8) | ir;
    }

    /**
     * Shared triangle fan geometry emission (used by emitCircleFan).
     */
    private void emitTriangleFan(float wcx, float wcy, float wrx, float wry,
                                  float z, float[] positions, int[] colors, int segments) {
        int indexCount = segments * 3;
        short[] indices = new short[indexCount];
        for (int i = 0; i < segments; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = (short)(i + 1);
            indices[i * 3 + 2] = (short)(i + 2);
        }

        VertexBuffer vb = buildColoredVB(positions, colors);
        IndexBuffer ib = buildIB(indices);
        Box bbox = new Box(wcx, wcy, z, wrx, wry, 0.01f);
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, indexCount, bbox, mi, false, false);
    }

    // ==================================================================================
    // 2D Rotation Helpers
    // ==================================================================================

    /**
     * Rotate a pixel-space point around (rotCX, rotCY) using the precomputed
     * cos/sin values. Returns [rotatedX, rotatedY].
     */
    // ==================================================================================
    // Scroll offset helper
    // ==================================================================================

    /**
     * Flush accumulated batch geometry into Filament entities.
     * Creates one entity per batch (flat-color, and one per MSDF atlas).
     */
    public void flushBatches() {
        flushFlatColorBatch();
        flushMsdfBatches();
    }

    private void flushFlatColorBatch() {
        if (flatColorBatch.isEmpty()) return;
        VertexBuffer vb = buildColoredVB(flatColorBatch.positionArray(), flatColorBatch.colorArray());
        IndexBuffer ib = buildIB(flatColorBatch.indexArray());
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, flatColorBatch.indices.size(), flatColorBatch.boundingBox(), mi, false, false);
        flatColorBatch.clear();
    }

    private void flushMsdfBatches() {

        // Flush MSDF batches (one per atlas)
        for (var entry : msdfBatches.entrySet()) {
            MsdfBatchAccumulator batch = entry.getValue();
            if (batch.isEmpty()) continue;
            MsdfAtlas atlas = entry.getKey();

            VertexBuffer vb = buildMsdfVB(batch.positionArray(), batch.uvArray(), batch.colorArray());
            IndexBuffer ib = buildIB(batch.indexArray());

            MaterialInstance mi = textRenderer.msdfTextMaterial().createInstance();
            TextureSampler sampler = new TextureSampler();
            sampler.setMagFilter(TextureSampler.MagFilter.LINEAR);
            sampler.setMinFilter(TextureSampler.MinFilter.LINEAR);
            mi.setParameter("msdfAtlas", atlas.texture(), sampler);
            mi.setParameter("pxRange", (float) atlas.pxRange());
            mi.setParameter("atlasSize", (float) atlas.atlasWidth(), (float) atlas.atlasHeight());
            mi.setParameter("screenPxRange", batch.screenPxRangeMax);

            emitEntity(vb, ib, batch.indices.size(), batch.boundingBox(), mi, false, false);
        }
        msdfBatches.values().forEach(MsdfBatchAccumulator::clear);
        msdfBatches.clear();
    }

    /**
     * Recursively offset all nodes in a subtree by (dx, dy) in pixel space.
     * Used to shift scroll container children so clip checks and position
     * calculations work correctly with absolute coordinates.
     */
    private void offsetSubtree(LayoutNode node, float dx, float dy) {
        node.setBounds(node.x() + dx, node.y() + dy, node.width(), node.height());
        if (node instanceof LayoutNode.BoxNode box) {
            for (LayoutNode child : box.children()) {
                offsetSubtree(child, dx, dy);
            }
            for (LayoutNode overlay : box.overlays()) {
                offsetSubtree(overlay, dx, dy);
            }
        }
    }

    // ==================================================================================
    // Clip Stack
    // ==================================================================================

    private void pushClip(float left, float top, float width, float height) {
        float right = left + width;
        float bottom = top + height;
        if (!clipStack.isEmpty()) {
            // Intersect with parent clip
            float[] parent = clipStack.peek();
            left = Math.max(left, parent[0]);
            top = Math.max(top, parent[1]);
            right = Math.min(right, parent[2]);
            bottom = Math.min(bottom, parent[3]);
        }
        clipStack.push(new float[]{left, top, right, bottom});
    }

    private void popClip() {
        if (!clipStack.isEmpty()) clipStack.pop();
    }

    /** Check if a quad should be clipped by the current clip rectangle. */
    private boolean isClipped(float px, float py, float pw, float ph) {
        if (clipStack.isEmpty()) return false;
        float[] clip = clipStack.peek();
        // Fully outside any edge: skip the entire glyph
        if (px + pw <= clip[0] || px >= clip[2]) return true;
        if (py + ph <= clip[1] || py >= clip[3]) return true;
        return false;
    }

    private float[] rotatePoint(float px, float py) {
        float dx = px - rotCX;
        float dy = py - rotCY;
        return new float[] {
            rotCX + dx * rotCos - dy * rotSin,
            rotCY + dx * rotSin + dy * rotCos
        };
    }

    /**
     * Emit a circle ring (annulus) as a triangle strip between inner and outer radii.
     * Used for circular borders. Applies 2D rotation if active.
     */
    private void emitCircleRing(float cx, float cy, float innerR, float outerR,
                                 int argbColor, float z, int segments) {
        if (innerR < 0) innerR = 0;
        if (outerR <= innerR) return;

        int abgr = MsdfTextRenderer.argbToAbgr(argbColor);

        int vertexCount = (segments + 1) * 2; // inner + outer ring
        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];

        for (int i = 0; i <= segments; i++) {
            float theta = (float)(2 * Math.PI * i / segments);
            float cos = (float) Math.cos(theta);
            float sin = (float) Math.sin(theta);

            // Inner vertex
            float iPx = cx + innerR * cos;
            float iPy = cy + innerR * sin;
            if (rotationActive) {
                float[] rr = rotatePoint(iPx, iPy);
                iPx = rr[0]; iPy = rr[1];
            }
            int iv = i * 2;
            positions[iv * 3]     = iPx / pixelsPerUnit + originX;
            positions[iv * 3 + 1] = -iPy / pixelsPerUnit + originY;
            positions[iv * 3 + 2] = z;
            colors[iv] = abgr;

            // Outer vertex
            float oPx = cx + outerR * cos;
            float oPy = cy + outerR * sin;
            if (rotationActive) {
                float[] rr = rotatePoint(oPx, oPy);
                oPx = rr[0]; oPy = rr[1];
            }
            int ov = iv + 1;
            positions[ov * 3]     = oPx / pixelsPerUnit + originX;
            positions[ov * 3 + 1] = -oPy / pixelsPerUnit + originY;
            positions[ov * 3 + 2] = z;
            colors[ov] = abgr;
        }

        // Triangle strip → explicit triangles
        int indexCount = segments * 6;
        short[] indices = new short[indexCount];
        for (int i = 0; i < segments; i++) {
            int base = i * 2;
            int ii = i * 6;
            indices[ii]     = (short) base;       // inner current
            indices[ii + 1] = (short)(base + 1);  // outer current
            indices[ii + 2] = (short)(base + 3);  // outer next
            indices[ii + 3] = (short) base;       // inner current
            indices[ii + 4] = (short)(base + 3);  // outer next
            indices[ii + 5] = (short)(base + 2);  // inner next
        }

        VertexBuffer vb = buildColoredVB(positions, colors);
        IndexBuffer ib = buildIB(indices);
        float wcx = cx / pixelsPerUnit + originX;
        float wcy = -cy / pixelsPerUnit + originY;
        float wr = outerR / pixelsPerUnit;
        Box bbox = new Box(wcx, wcy, z, wr, wr, 0.01f);
        MaterialInstance mi = textRenderer.flatColorMaterial().createInstance();
        emitEntity(vb, ib, indexCount, bbox, mi, false, false);
    }

    // ==================================================================================
    // Color Resolution (reads resolved fields set by StyleResolver)
    // ==================================================================================

    private int resolveTextColor(LayoutNode node) {
        return node.color() != -1 ? node.color() : COLOR_TEXT;
    }

    private int resolveBackgroundColor(LayoutNode node) {
        if (node.backgroundColor() != -1) return node.backgroundColor();
        if (node instanceof LayoutNode.BoxNode box) {
            // Chess square state blending (selected/legal-target modifies base bg)
            if (box.backgroundColor() != -1 && hasStyle(box, "selected")) {
                return blendColors(box.backgroundColor(), 0x6646B964);
            }
            if (box.backgroundColor() != -1 && hasStyle(box, "legal-target")) {
                return blendColors(box.backgroundColor(), 0x3346B964);
            }
            if (box.background() != null) return parseColor(box.background(), COLOR_BG);
        }
        return COLOR_BG;
    }

    private static int blendColors(int base, int overlay) {
        int oa = (overlay >>> 24) & 0xFF;
        int or_ = (overlay >>> 16) & 0xFF;
        int og = (overlay >>> 8) & 0xFF;
        int ob = overlay & 0xFF;

        int ba = (base >>> 24) & 0xFF;
        int br_ = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;

        float alpha = oa / 255f;
        int ra = Math.min(255, ba + (int)(oa * (1 - ba / 255f)));
        int rr = (int)(or_ * alpha + br_ * (1 - alpha));
        int rg = (int)(og * alpha + bg * (1 - alpha));
        int rb = (int)(ob * alpha + bb * (1 - alpha));

        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private boolean hasStyle(LayoutNode node, String style) {
        return node.styles().contains(style);
    }

    private int parseColor(String color, int fallback) {
        if (color == null || color.isEmpty()) return fallback;
        if (color.startsWith("#")) {
            try {
                String hex = color.substring(1);
                if (hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
                if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return switch (color.toLowerCase()) {
            case "black" -> 0xFF000000;
            case "white" -> 0xFFFFFFFF;
            case "red" -> 0xFFFF0000;
            case "green" -> 0xFF00FF00;
            case "blue" -> 0xFF0000FF;
            case "gray", "grey" -> 0xFF808080;
            case "transparent" -> 0x00000000;
            default -> fallback;
        };
    }

    private float parseStrokeWidth(String strokeWidth) {
        if (strokeWidth == null || strokeWidth.isEmpty()) return 1;
        try {
            return Float.parseFloat(strokeWidth.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // ==================================================================================
    // Buffer Helpers
    // ==================================================================================

    private static ByteBuffer allocateFloat(float[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder());
        for (float f : data) buf.putFloat(f);
        buf.flip();
        return buf;
    }

    private static ByteBuffer allocateInt(int[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder());
        for (int i : data) buf.putInt(i);
        buf.flip();
        return buf;
    }

    private static ByteBuffer allocateShort(short[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 2)
                .order(ByteOrder.nativeOrder());
        for (short s : data) buf.putShort(s);
        buf.flip();
        return buf;
    }

    // ==================================================================================
    // Batch Accumulators — collect quad geometry during paint, flush as single entities
    // ==================================================================================

    /**
     * Accumulates flat-color quad geometry (positions + colors) for batch emission.
     * All quads share the flat_color material — per-vertex color provides variation.
     */
    static class BatchAccumulator {
        // USHORT indices: max vertex index is 65535, reserve headroom for 4 vertices per quad
        static final int MAX_VERTICES = 65532;

        final List<Float> positions = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        final List<Short> indices = new ArrayList<>();
        int vertexCount = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        boolean isFull() { return vertexCount >= MAX_VERTICES; }

        void addQuad(float wx0, float wy0, float wx1, float wy1,
                     float wx2, float wy2, float wx3, float wy3,
                     float z, int abgr) {
            int base = vertexCount;
            positions.add(wx0); positions.add(wy0); positions.add(z);
            positions.add(wx1); positions.add(wy1); positions.add(z);
            positions.add(wx2); positions.add(wy2); positions.add(z);
            positions.add(wx3); positions.add(wy3); positions.add(z);
            colors.add(abgr); colors.add(abgr); colors.add(abgr); colors.add(abgr);
            indices.add((short) base); indices.add((short) (base + 1)); indices.add((short) (base + 2));
            indices.add((short) base); indices.add((short) (base + 2)); indices.add((short) (base + 3));
            vertexCount += 4;
            updateBounds(wx0, wy0, z); updateBounds(wx1, wy1, z);
            updateBounds(wx2, wy2, z); updateBounds(wx3, wy3, z);
        }

        private void updateBounds(float x, float y, float z) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }

        boolean isEmpty() { return vertexCount == 0; }

        void clear() {
            positions.clear(); colors.clear(); indices.clear();
            vertexCount = 0;
            minX = Float.MAX_VALUE; minY = Float.MAX_VALUE; minZ = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE; maxY = -Float.MAX_VALUE; maxZ = -Float.MAX_VALUE;
        }

        float[] positionArray() {
            float[] arr = new float[positions.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = positions.get(i);
            return arr;
        }

        int[] colorArray() {
            int[] arr = new int[colors.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = colors.get(i);
            return arr;
        }

        short[] indexArray() {
            short[] arr = new short[indices.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = indices.get(i);
            return arr;
        }

        Box boundingBox() {
            float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f, cz = (minZ + maxZ) / 2f;
            float hx = (maxX - minX) / 2f, hy = (maxY - minY) / 2f, hz = (maxZ - minZ) / 2f;
            return new Box(cx, cy, cz, Math.max(hx, 0.001f), Math.max(hy, 0.001f), Math.max(hz, 0.001f));
        }
    }

    /**
     * Accumulates MSDF glyph quad geometry (positions + UVs + colors) for batch emission.
     * All quads in one batch share the same atlas texture.
     */
    static class MsdfBatchAccumulator {
        static final int MAX_VERTICES = 65532;

        final List<Float> positions = new ArrayList<>();
        final List<Float> uvs = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        final List<Short> indices = new ArrayList<>();
        int vertexCount = 0;
        float screenPxRangeMax = 1.0f;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        void addQuad(float wx0, float wy0, float wx1, float wy1,
                     float wx2, float wy2, float wx3, float wy3,
                     float z, float uvL, float uvTopV, float uvR, float uvBotV,
                     int abgr, float screenPxRange) {
            int base = vertexCount;
            positions.add(wx0); positions.add(wy0); positions.add(z);
            positions.add(wx1); positions.add(wy1); positions.add(z);
            positions.add(wx2); positions.add(wy2); positions.add(z);
            positions.add(wx3); positions.add(wy3); positions.add(z);
            uvs.add(uvL); uvs.add(uvTopV);
            uvs.add(uvR); uvs.add(uvTopV);
            uvs.add(uvR); uvs.add(uvBotV);
            uvs.add(uvL); uvs.add(uvBotV);
            colors.add(abgr); colors.add(abgr); colors.add(abgr); colors.add(abgr);
            indices.add((short) base); indices.add((short) (base + 1)); indices.add((short) (base + 2));
            indices.add((short) base); indices.add((short) (base + 2)); indices.add((short) (base + 3));
            vertexCount += 4;
            screenPxRangeMax = Math.max(screenPxRangeMax, screenPxRange);
            updateBounds(wx0, wy0, z); updateBounds(wx1, wy1, z);
            updateBounds(wx2, wy2, z); updateBounds(wx3, wy3, z);
        }

        private void updateBounds(float x, float y, float z) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }

        boolean isEmpty() { return vertexCount == 0; }

        void clear() {
            positions.clear(); uvs.clear(); colors.clear(); indices.clear();
            vertexCount = 0; screenPxRangeMax = 1.0f;
            minX = Float.MAX_VALUE; minY = Float.MAX_VALUE; minZ = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE; maxY = -Float.MAX_VALUE; maxZ = -Float.MAX_VALUE;
        }

        float[] positionArray() {
            float[] arr = new float[positions.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = positions.get(i);
            return arr;
        }

        float[] uvArray() {
            float[] arr = new float[uvs.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = uvs.get(i);
            return arr;
        }

        int[] colorArray() {
            int[] arr = new int[colors.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = colors.get(i);
            return arr;
        }

        short[] indexArray() {
            short[] arr = new short[indices.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = indices.get(i);
            return arr;
        }

        Box boundingBox() {
            float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f, cz = (minZ + maxZ) / 2f;
            float hx = (maxX - minX) / 2f, hy = (maxY - minY) / 2f, hz = (maxZ - minZ) / 2f;
            return new Box(cx, cy, cz, Math.max(hx, 0.001f), Math.max(hy, 0.001f), Math.max(hz, 0.001f));
        }
    }
}
