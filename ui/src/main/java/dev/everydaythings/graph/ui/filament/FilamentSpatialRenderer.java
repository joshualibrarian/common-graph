package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.*;
import dev.everydaythings.filament.gltfio.AssetLoader;
import dev.everydaythings.filament.gltfio.FilamentAsset;
import dev.everydaythings.filament.gltfio.ResourceLoader;
import dev.everydaythings.filament.gltfio.UbershaderProvider;
import dev.everydaythings.graph.ui.audio.OpenALAudio;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.spatial.SpatialRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.skia.PanelPainter;
import dev.everydaythings.graph.ui.skia.SkiaSurfaceRenderer;
import dev.everydaythings.graph.ui.style.Stylesheet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link SpatialRenderer} by translating scene commands into Filament API calls.
 *
 * <p>This is the bridge between the declarative {@code @Body}/{@code @Space} system in {@code :core}
 * and the Filament rendering engine. It uses:
 * <ul>
 *   <li>{@code baked_color.filamat} — lit PBR material with vertex color as baseColor</li>
 *   <li>{@link PrimitiveMeshes} — box and sphere geometry generation</li>
 *   <li>{@link LightManager} — directional, point, and spot lights</li>
 *   <li>{@link IndirectLight} — ambient image-based lighting via spherical harmonics</li>
 *   <li>{@link TransformManager} — entity positioning in 3D space</li>
 * </ul>
 */
public class FilamentSpatialRenderer implements SpatialRenderer {

    private static final Logger log = LogManager.getLogger(FilamentSpatialRenderer.class);

    private final Engine engine;
    private final Scene scene;
    private final OpenALAudio openAL;

    // Shared materials (loaded once)
    private Material bakedColorMaterial;
    private Material flatColorMaterial;

    // glTF/GLB asset loading pipeline
    private final UbershaderProvider ubershaderProvider;
    private final AssetLoader assetLoader;
    private final ResourceLoader resourceLoader;

    // GLB data cache: resource path → raw bytes. Avoids redundant classpath I/O
    // when the same model appears multiple times (e.g., 8 pawns).
    private final Map<String, byte[]> glbDataCache = new HashMap<>();

    // Track created resources for cleanup
    private final List<Integer> entities = new ArrayList<>();
    private final List<PrimitiveMeshes.Mesh> meshes = new ArrayList<>();
    private final List<MaterialInstance> materialInstances = new ArrayList<>();
    private final List<FilamentAsset> loadedAssets = new ArrayList<>();
    private IndirectLight indirectLight;
    private Skybox skybox;

    // Transform stack (4x4 column-major matrices)
    private final Deque<float[]> transformStack = new ArrayDeque<>();

    // Panel API state
    private PanelPainter panelPainter;
    private SkiaSurfaceRenderer activePanelBuilder;
    private float panelPixelW, panelPixelH, panelWidthM, panelHeightM;

    // Camera defaults extracted from scene (used by CameraController)
    private double cameraFov = 60, cameraNear = 0.1, cameraFar = 1000;
    private double cameraX = 0, cameraY = 2, cameraZ = 5;
    private double cameraTargetX = 0, cameraTargetY = 0.5, cameraTargetZ = 0;
    private boolean hasSpace = false;

    public FilamentSpatialRenderer(Engine engine, Scene scene) {
        this(engine, scene, null);
    }

    public FilamentSpatialRenderer(Engine engine, Scene scene, OpenALAudio openAL) {
        this.engine = engine;
        this.scene = scene;
        this.openAL = openAL;
        this.bakedColorMaterial = loadMaterial("materials/baked_color.filamat");
        this.flatColorMaterial = loadMaterial("materials/flat_color.filamat");

        // Initialize glTF/GLB asset loading pipeline
        this.ubershaderProvider = new UbershaderProvider(engine);
        this.assetLoader = new AssetLoader(engine, ubershaderProvider, EntityManager.get());
        this.resourceLoader = new ResourceLoader(engine);
    }

    // ==================== Bodies ====================

    @Override
    public void body(String shape, double w, double h, double d,
                     int color, double opacity, String shading, List<String> styles) {
        // Convert hex RGB (0xRRGGBB) to ABGR for Filament vertex color
        int abgr = rgbToAbgr(color, opacity);

        PrimitiveMeshes.Mesh mesh;
        switch (shape != null ? shape : "box") {
            case "sphere" -> mesh = PrimitiveMeshes.createSphere(
                    engine, (float)(w / 2.0), 32, abgr);
            case "cylinder" -> mesh = PrimitiveMeshes.createCylinder(
                    engine, (float)(w / 2.0), (float)h, 16, abgr);
            case "plane" -> mesh = PrimitiveMeshes.createPlane(
                    engine, (float)w, (float)d, abgr);
            case "cone" -> mesh = PrimitiveMeshes.createCone(
                    engine, (float)(w / 2.0), (float)h, 16, abgr);
            case "capsule" -> mesh = PrimitiveMeshes.createCapsule(
                    engine, (float)(w / 2.0), (float)h, 16, abgr);
            default -> mesh = PrimitiveMeshes.createBox(
                    engine, (float)w, (float)h, (float)d, abgr);
        }
        meshes.add(mesh);

        // Create entity with renderable
        int entity = EntityManager.get().create();
        entities.add(entity);

        MaterialInstance mi = bakedColorMaterial.createInstance();
        materialInstances.add(mi);

        new RenderableManager.Builder(1)
                .boundingBox(mesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        mesh.vertexBuffer(), mesh.indexBuffer(), 0, mesh.indexCount())
                .material(0, mi)
                .castShadows(true)
                .receiveShadows(true)
                .culling(false)
                .build(engine, entity);

        // Apply current transform
        applyTransform(entity);

        scene.addEntity(entity);
    }

    @Override
    public void meshBody(String meshRef, int color, double opacity, String shading, List<String> styles) {
        byte[] data = glbDataCache.computeIfAbsent(meshRef, this::loadResource);
        if (data == null) {
            log.warn("Mesh resource not found: {}", meshRef);
            return;
        }

        FilamentAsset asset = createAndLoadAsset(data);
        if (asset == null) {
            log.warn("Failed to load glTF asset: {}", meshRef);
            return;
        }

        // Apply current transform stack to the root entity
        int root = asset.getRoot();
        if (!transformStack.isEmpty()) {
            TransformManager tm = engine.getTransformManager();
            int tmInstance = tm.getInstance(root);
            if (tmInstance == 0) {
                tmInstance = tm.create(root);
            }
            tm.setTransform(tmInstance, transformStack.peek());
        }

        // Add all entities to scene (renderables, lights, structural nodes)
        for (int entity : asset.getEntities()) {
            scene.addEntity(entity);
        }

        log.info("Loaded mesh: {} ({} entities)", meshRef, asset.getEntities().length);
    }

    /**
     * Place a GLB model at an absolute position in Filament Y-up space.
     * Aligns the model's base (minY) to the given Y coordinate.
     * Used by composeSurfaceOnBody to place models at layout-determined positions.
     */
    public void placeModelAt(String meshRef, int color,
                              float x, float y, float z, float scale) {
        byte[] data = glbDataCache.computeIfAbsent(meshRef, this::loadResource);
        if (data == null) {
            log.warn("Model resource not found: {}", meshRef);
            return;
        }

        FilamentAsset asset = createAndLoadAsset(data);
        if (asset == null) {
            log.warn("Failed to load glTF asset: {}", meshRef);
            return;
        }

        // Offset so the model's bottom sits on the Y elevation plane
        Box bounds = asset.getBoundingBox();
        float[] center = bounds.getCenter();
        float[] halfExtent = bounds.getHalfExtent();
        float minY = center[1] - halfExtent[1];
        float placementY = y - scale * minY;

        float[] mat = {
            scale, 0,     0,     0,
            0,     scale, 0,     0,
            0,     0,     scale, 0,
            x,     placementY, z, 1
        };

        TransformManager tm = engine.getTransformManager();
        int root = asset.getRoot();
        int tmInstance = tm.getInstance(root);
        if (tmInstance == 0) {
            tmInstance = tm.create(root);
        }
        tm.setTransform(tmInstance, mat);

        for (int entity : asset.getEntities()) {
            scene.addEntity(entity);
        }

        log.debug("Placed GLB model: {} at ({}, {}, {})", meshRef, x, y, z);
    }

    @Override
    public void line(double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     int color, double width) {
        // Convert DSL Z-up → Filament Y-up: (x, y, z) → (x, z, -y)
        float fx1 = toFilamentX(x1), fy1 = toFilamentY(z1), fz1 = toFilamentZ(y1);
        float fx2 = toFilamentX(x2), fy2 = toFilamentY(z2), fz2 = toFilamentZ(y2);
        float hw = (float) (width / 2.0);

        // Direction vector
        float dx = fx2 - fx1, dy = fy2 - fy1, dz = fz2 - fz1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9f) return;
        dx /= len; dy /= len; dz /= len;

        // Side vector perpendicular to direction.
        // Cross with Y-up (0,1,0); if parallel, fall back to (0,0,1).
        float sx, sy, sz;
        float crossX = dy * 0 - dz * 1;  // d × (0,1,0)
        float crossY = dz * 0 - dx * 0;
        float crossZ = dx * 1 - dy * 0;
        float crossLen = (float) Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
        if (crossLen < 1e-6f) {
            // Direction is parallel to Y, use Z-up fallback
            crossX = dy * 1 - dz * 0;  // d × (0,0,1)
            crossY = dz * 0 - dx * 1;
            crossZ = dx * 0 - dy * 0;
            crossLen = (float) Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ);
        }
        sx = (crossX / crossLen) * hw;
        sy = (crossY / crossLen) * hw;
        sz = (crossZ / crossLen) * hw;

        // 4 vertices: p1 ± side, p2 ± side
        float[] positions = {
                fx1 - sx, fy1 - sy, fz1 - sz,
                fx1 + sx, fy1 + sy, fz1 + sz,
                fx2 + sx, fy2 + sy, fz2 + sz,
                fx2 - sx, fy2 - sy, fz2 - sz,
        };
        short[] indices = {0, 1, 2, 0, 2, 3};

        int abgr = rgbToAbgr(color, 1.0);
        int[] colors = {abgr, abgr, abgr, abgr};

        ByteBuffer posBuf = ByteBuffer.allocateDirect(positions.length * 4)
                .order(ByteOrder.nativeOrder());
        for (float f : positions) posBuf.putFloat(f);
        posBuf.flip();

        ByteBuffer colorBuf = ByteBuffer.allocateDirect(colors.length * 4)
                .order(ByteOrder.nativeOrder());
        for (int c : colors) colorBuf.putInt(c);
        colorBuf.flip();

        ByteBuffer idxBuf = ByteBuffer.allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder());
        for (short s : indices) idxBuf.putShort(s);
        idxBuf.flip();

        VertexBuffer vb = new VertexBuffer.Builder()
                .vertexCount(4)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 1,
                        VertexBuffer.AttributeType.UBYTE4, 0, 4)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);
        vb.setBufferAt(engine, 0, posBuf);
        vb.setBufferAt(engine, 1, colorBuf);

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, idxBuf);

        PrimitiveMeshes.Mesh mesh = new PrimitiveMeshes.Mesh(vb, ib, 6,
                new Box(
                        (fx1 + fx2) / 2f, (fy1 + fy2) / 2f, (fz1 + fz2) / 2f,
                        Math.abs(fx2 - fx1) / 2f + hw,
                        Math.abs(fy2 - fy1) / 2f + hw,
                        Math.abs(fz2 - fz1) / 2f + hw));
        meshes.add(mesh);

        int entity = EntityManager.get().create();
        entities.add(entity);

        MaterialInstance mi = flatColorMaterial.createInstance();
        materialInstances.add(mi);

        new RenderableManager.Builder(1)
                .boundingBox(mesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        vb, ib, 0, 6)
                .material(0, mi)
                .castShadows(false)
                .receiveShadows(false)
                .culling(false)
                .build(engine, entity);

        applyTransform(entity);
        scene.addEntity(entity);
    }

    // ==================== Transform Stack ====================

    @Override
    public void pushTransform(double x, double y, double z,
                              double qx, double qy, double qz, double qw,
                              double sx, double sy, double sz) {
        // Convert DSL Z-up → Filament Y-up
        // Position: (x, y, z) → (x, z, -y)
        // Quaternion: (qx, qy, qz, qw) → (qx, qz, -qy, qw)
        // Scale: (sx, sy, sz) → (sx, sz, sy)
        float[] mat = buildTRSMatrix(
                toFilamentX(x), toFilamentY(z), toFilamentZ(y),
                (float)qx, (float)qz, (float)-qy, (float)qw,
                (float)sx, (float)sz, (float)sy);

        // Multiply with parent transform if exists
        if (!transformStack.isEmpty()) {
            mat = multiply4x4(transformStack.peek(), mat);
        }

        transformStack.push(mat);
    }

    @Override
    public void popTransform() {
        if (!transformStack.isEmpty()) {
            transformStack.pop();
        }
    }

    // ==================== Panels ====================

    /**
     * Inject the panel painter for 2D-in-3D panel rendering.
     * Called by GraphicalSession after creating the spatial renderer.
     */
    public void setPanelPainter(PanelPainter painter) {
        this.panelPainter = painter;
    }

    @Override
    public void beginPanel(double width, double height, double ppm) {
        if (panelPainter == null) return;

        panelWidthM = (float) width;
        panelHeightM = (float) height;
        panelPixelW = (float) (width * ppm);
        panelPixelH = (float) (height * ppm);

        RenderContext ctx = panelPainter.buildContext(panelPixelW, panelPixelH);
        activePanelBuilder = new SkiaSurfaceRenderer(ctx);
    }

    @Override
    public SurfaceRenderer panelRenderer() {
        return activePanelBuilder;
    }

    @Override
    public void endPanel() {
        if (activePanelBuilder == null || panelPainter == null) return;

        LayoutNode.BoxNode tree = activePanelBuilder.result();

        // Same layout step as rebuildLayout() — identical pipeline
        RenderContext panelCtx = activePanelBuilder.renderContext();
        LayoutEngine layoutEngine = new LayoutEngine(
                panelPainter.textMeasurer(), panelCtx,
                Stylesheet.fromClasspath(), 15f);
        layoutEngine.layout(tree, panelPixelW, panelPixelH);

        // Get world transform from the spatial renderer's transform stack
        float[] worldTransform = transformStack.isEmpty()
                ? identity4x4()
                : transformStack.peek().clone();

        // Paint via the backend-specific panel painter
        panelPainter.paintPanel(tree, panelPixelW, panelPixelH,
                panelWidthM, panelHeightM, worldTransform);

        activePanelBuilder = null;
    }

    // ==================== Lighting ====================

    @Override
    public void light(String type, int color, double intensity,
                      double x, double y, double z,
                      double dirX, double dirY, double dirZ) {
        LightManager.Type lightType = switch (type != null ? type : "directional") {
            case "point" -> LightManager.Type.POINT;
            case "spot" -> LightManager.Type.FOCUSED_SPOT;
            case "sun" -> LightManager.Type.SUN;
            default -> LightManager.Type.DIRECTIONAL;
        };

        // Convert hex RGB to linear float
        float r = srgbToLinear(((color >> 16) & 0xFF) / 255f);
        float g = srgbToLinear(((color >> 8) & 0xFF) / 255f);
        float b = srgbToLinear((color & 0xFF) / 255f);

        // Filament uses physical light units (lux for directional, lumens for point).
        // With camera exposure=1.0 and LINEAR tone mapping, physical lux values
        // (e.g. 110,000 for sunlight) would massively overexpose lit materials.
        // Scale to ~PI so a Lambertian surface at full intensity ≈ material color.
        float filamentIntensity = (float)(intensity * Math.PI);

        // Convert DSL Z-up → Filament Y-up
        float fx = toFilamentX(x), fy = toFilamentY(z), fz = toFilamentZ(y);
        float fdx = toFilamentX(dirX), fdy = toFilamentY(dirZ), fdz = toFilamentZ(dirY);

        int lightEntity = EntityManager.get().create();
        entities.add(lightEntity);

        var builder = new LightManager.Builder(lightType)
                .color(r, g, b)
                .intensity(filamentIntensity)
                .castShadows(true);

        if (lightType == LightManager.Type.DIRECTIONAL || lightType == LightManager.Type.SUN) {
            builder.direction(fdx, fdy, fdz);
        }
        if (lightType == LightManager.Type.POINT || lightType == LightManager.Type.FOCUSED_SPOT) {
            builder.position(fx, fy, fz);
            builder.falloff(50.0f);
        }

        builder.build(engine, lightEntity);
        scene.addEntity(lightEntity);
    }

    // ==================== Camera ====================

    @Override
    public void camera(String projection, double fov, double near, double far,
                       double x, double y, double z,
                       double tx, double ty, double tz) {
        this.cameraFov = fov;
        this.cameraNear = near;
        this.cameraFar = far;
        // Convert DSL Z-up → Filament Y-up: (x, y, z) → (x, z, -y)
        this.cameraX = x;
        this.cameraY = z;   // DSL Z (up) → Filament Y (up)
        this.cameraZ = -y;  // DSL Y (forward) → Filament -Z (forward)
        this.cameraTargetX = tx;
        this.cameraTargetY = tz;
        this.cameraTargetZ = -ty;
    }

    // ==================== Environment ====================

    @Override
    public void environment(int background, int ambient,
                            double fogNear, double fogFar, int fogColor) {
        // Set background via per-scene Skybox (NOT global renderer clear color,
        // which would affect all panes including the chrome pane)
        float bgR = srgbToLinear(((background >> 16) & 0xFF) / 255f);
        float bgG = srgbToLinear(((background >> 8) & 0xFF) / 255f);
        float bgB = srgbToLinear((background & 0xFF) / 255f);

        skybox = new Skybox.Builder()
                .color(bgR, bgG, bgB, 1.0f)
                .build(engine);
        scene.setSkybox(skybox);

        // Create ambient IBL from ambient color
        // Use 1-band spherical harmonics (L0 only) for flat ambient
        float ambR = srgbToLinear(((ambient >> 16) & 0xFF) / 255f);
        float ambG = srgbToLinear(((ambient >> 8) & 0xFF) / 255f);
        float ambB = srgbToLinear((ambient & 0xFF) / 255f);

        float[] sh = new float[]{ambR, ambG, ambB};

        indirectLight = new IndirectLight.Builder()
                .irradiance(1, sh)
                .intensity(1.0f)
                .build(engine);
        scene.setIndirectLight(indirectLight);
    }

    // ==================== Audio ====================

    @Override
    public void audio(String src, double x, double y, double z,
                      double volume, double pitch, boolean loop,
                      boolean spatial, double refDistance, double maxDistance,
                      boolean autoplay) {
        if (openAL == null || !openAL.isInitialized()) return;

        // Convert DSL Z-up → Filament/OpenAL Y-up: (x, y, z) → (x, z, -y)
        int sourceId = openAL.createSource(src, x, z, -y,
                volume, pitch, loop, spatial, refDistance, maxDistance);

        if (autoplay && sourceId >= 0) {
            openAL.play(sourceId);
        }
    }

    // ==================== Metadata ====================

    @Override
    public void id(String id) {
        // Metadata — not used for rendering, could be used for picking
    }

    // ==================== Public Helpers ====================

    /**
     * Create a floor entity directly (not through the SpatialRenderer interface).
     * Used by GraphicalSession for the default white room.
     */
    public int createFloor(float size, float thickness, int color) {
        int abgr = rgbToAbgr(color, 1.0);
        PrimitiveMeshes.Mesh mesh = PrimitiveMeshes.createBox(engine, size, thickness, size, abgr);
        meshes.add(mesh);

        int entity = EntityManager.get().create();
        entities.add(entity);

        MaterialInstance mi = bakedColorMaterial.createInstance();
        materialInstances.add(mi);

        new RenderableManager.Builder(1)
                .boundingBox(mesh.boundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        mesh.vertexBuffer(), mesh.indexBuffer(), 0, mesh.indexCount())
                .material(0, mi)
                .castShadows(false)
                .receiveShadows(true)
                .culling(false)
                .build(engine, entity);

        // Position at Y=0 (center of thickness box is at origin, so shift down)
        float[] mat = identity4x4();
        mat[13] = -thickness / 2f; // translate Y
        int tmInstance = engine.getTransformManager().create(entity);
        engine.getTransformManager().setTransform(tmInstance, mat);

        scene.addEntity(entity);
        return entity;
    }

    /**
     * Mark this scene as containing a space environment.
     * When true, the camera controller will default to FLY mode.
     */
    public void setIsSpace(boolean isSpace) {
        this.hasSpace = isSpace;
    }

    @Override
    public RenderContext renderContext() {
        return RenderContext.space();
    }

    /**
     * Apply camera defaults to a CameraController.
     * Sets the content hint (OBJECT vs SPACE) based on whether this scene
     * contains a space environment, then initializes camera parameters.
     */
    public void applyCameraDefaults(CameraController controller) {
        controller.setContentHint(hasSpace
                ? CameraController.ContentHint.SPACE
                : CameraController.ContentHint.OBJECT);
        controller.setDefaults(cameraFov, cameraNear, cameraFar,
                cameraX, cameraY, cameraZ,
                cameraTargetX, cameraTargetY, cameraTargetZ);
    }

    /**
     * Clean up all Filament resources created by this renderer.
     */
    public void destroy() {
        // Destroy glTF assets first (they own their entities)
        for (FilamentAsset asset : loadedAssets) {
            assetLoader.destroyAsset(asset);
        }
        loadedAssets.clear();

        resourceLoader.destroy();
        assetLoader.destroy();
        ubershaderProvider.destroy();

        // Destroy manually-created entities
        for (int entity : entities) {
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

        if (indirectLight != null) {
            scene.setIndirectLight(null);
            engine.destroyIndirectLight(indirectLight);
            indirectLight = null;
        }

        if (skybox != null) {
            engine.destroySkybox(skybox);
            skybox = null;
        }

        if (bakedColorMaterial != null) {
            engine.destroyMaterial(bakedColorMaterial);
            bakedColorMaterial = null;
        }

        if (flatColorMaterial != null) {
            engine.destroyMaterial(flatColorMaterial);
            flatColorMaterial = null;
        }
    }

    // ==================== Private: Transform Helpers ====================

    private void applyTransform(int entity) {
        if (transformStack.isEmpty()) return;

        float[] mat = transformStack.peek();
        int tmInstance = engine.getTransformManager().create(entity);
        engine.getTransformManager().setTransform(tmInstance, mat);
    }

    /**
     * Build a 4x4 column-major TRS matrix from position, quaternion rotation, and scale.
     */
    static float[] buildTRSMatrix(float tx, float ty, float tz,
                                   float qx, float qy, float qz, float qw,
                                   float sx, float sy, float sz) {
        // Rotation matrix from quaternion
        float x2 = qx + qx, y2 = qy + qy, z2 = qz + qz;
        float xx = qx * x2, xy = qx * y2, xz = qx * z2;
        float yy = qy * y2, yz = qy * z2, zz = qz * z2;
        float wx = qw * x2, wy = qw * y2, wz = qw * z2;

        float[] m = new float[16];

        // Column 0
        m[0]  = (1 - (yy + zz)) * sx;
        m[1]  = (xy + wz) * sx;
        m[2]  = (xz - wy) * sx;
        m[3]  = 0;

        // Column 1
        m[4]  = (xy - wz) * sy;
        m[5]  = (1 - (xx + zz)) * sy;
        m[6]  = (yz + wx) * sy;
        m[7]  = 0;

        // Column 2
        m[8]  = (xz + wy) * sz;
        m[9]  = (yz - wx) * sz;
        m[10] = (1 - (xx + yy)) * sz;
        m[11] = 0;

        // Column 3 (translation)
        m[12] = tx;
        m[13] = ty;
        m[14] = tz;
        m[15] = 1;

        return m;
    }

    /**
     * Multiply two 4x4 column-major matrices: result = a * b.
     */
    static float[] multiply4x4(float[] a, float[] b) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                r[col * 4 + row] =
                        a[row]      * b[col * 4]     +
                        a[4 + row]  * b[col * 4 + 1] +
                        a[8 + row]  * b[col * 4 + 2] +
                        a[12 + row] * b[col * 4 + 3];
            }
        }
        return r;
    }

    public static float[] identity4x4() {
        float[] m = new float[16];
        m[0] = 1; m[5] = 1; m[10] = 1; m[15] = 1;
        return m;
    }

    // ==================== Private: Color Conversion ====================

    /**
     * Convert hex RGB (0xRRGGBB) to ABGR int for Filament vertex color.
     */
    private static int rgbToAbgr(int rgb, double opacity) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (int)(opacity * 255);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * Convert sRGB component (0-1) to linear space.
     */
    private static float srgbToLinear(float srgb) {
        return srgb <= 0.04045f
                ? srgb / 12.92f
                : (float)Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    // ==================== Z-up → Y-up Coordinate Conversion ====================

    /**
     * Convert a DSL Z-up position (x, y, z) to Filament Y-up (x, z, -y).
     *
     * <p>DSL convention: X=right, Y=forward, Z=up.
     * Filament convention: X=right, Y=up, Z=backward.
     */
    static float toFilamentX(double x) { return (float) x; }
    static float toFilamentY(double z) { return (float) z; }  // DSL Z (up) → Filament Y (up)
    static float toFilamentZ(double y) { return (float) -y; } // DSL Y (forward) → Filament -Z (forward)

    // ==================== Private: Resource Loading ====================

    /**
     * Load a classpath resource as a byte array.
     * Strips leading "/" to get a classloader-relative path.
     */
    private byte[] loadResource(String resourcePath) {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("Error loading resource: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * Create a FilamentAsset from raw GLB data and load its resources.
     * Shared by {@link #meshBody} and {@link #placeModelAt}.
     */
    private FilamentAsset createAndLoadAsset(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length)
                .order(ByteOrder.nativeOrder());
        buffer.put(data);
        buffer.flip();

        FilamentAsset asset = assetLoader.createAsset(buffer);
        if (asset == null) return null;
        loadedAssets.add(asset);

        resourceLoader.loadResources(asset);
        asset.releaseSourceData();
        return asset;
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
