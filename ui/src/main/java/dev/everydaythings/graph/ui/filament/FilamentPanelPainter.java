package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.filament.Engine;
import dev.everydaythings.filament.EntityManager;
import dev.everydaythings.filament.Scene;
import dev.everydaythings.filament.TransformManager;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.RenderMetrics;
import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;
import dev.everydaythings.graph.ui.skia.PanelPainter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel painter that renders 2D surfaces as direct MSDF geometry in 3D space.
 *
 * <p>Wraps {@link FilamentSurfacePainter} and creates a parent transform entity
 * to position the painted geometry at the correct world location. All entities
 * created by the plane painter are reparented under this transform entity, making
 * their panel-local coordinates relative to the world position.
 *
 * @see PanelPainter
 * @see FilamentSurfacePainter
 */
public class FilamentPanelPainter implements PanelPainter {

    private static final Logger log = LogManager.getLogger(FilamentPanelPainter.class);

    private final Engine engine;
    private final Scene scene;
    private final MsdfFontManager fontManager;
    private final LibrarianHandle librarian;
    private final List<FilamentSurfacePainter> panelPainters = new ArrayList<>();
    private final List<Integer> panelEntities = new ArrayList<>();

    public FilamentPanelPainter(Engine engine, Scene scene, MsdfFontManager fontManager,
                                LibrarianHandle librarian) {
        this.engine = engine;
        this.scene = scene;
        this.fontManager = fontManager;
        this.librarian = librarian;
    }

    @Override
    public RenderContext buildContext(float pixelWidth, float pixelHeight) {
        RenderMetrics metrics = fontManager.buildMetrics();
        return RenderContext.builder()
                .renderer(RenderContext.RENDERER_SKIA)
                .breakpoint(RenderContext.BREAKPOINT_LG)
                .viewportWidth(pixelWidth)
                .viewportHeight(pixelHeight)
                .addCapability("color")
                .addCapability("mouse")
                .addCapability("images")
                .librarian(librarian)
                .renderMetrics(metrics)
                .baseFontSize(fontManager.baseFontSize())
                .build();
    }

    @Override
    public LayoutEngine.TextMeasurer textMeasurer() {
        return fontManager;
    }

    @Override
    public void paintPanel(LayoutNode.BoxNode tree,
                           float pixelW, float pixelH,
                           float panelWidthM, float panelHeightM,
                           float[] worldTransform) {
        // 1. Create parent entity positioned at the world transform
        int panelEntity = EntityManager.get().create();
        TransformManager tm = engine.getTransformManager();
        tm.create(panelEntity);
        tm.setTransform(tm.getInstance(panelEntity), worldTransform);
        scene.addEntity(panelEntity);
        panelEntities.add(panelEntity);

        // 2. Create a dedicated painter for this panel so multiple face panels
        // can coexist (front/top/back/etc.) within the same body render pass.
        FilamentSurfacePainter planePainter = new FilamentSurfacePainter(engine, scene, fontManager);
        panelPainters.add(planePainter);

        // 3. Configure plane painter for panel-local coordinates
        planePainter.configureForPanel(pixelW, pixelH, panelWidthM, panelHeightM);

        // 4. Paint (entities created in panel-local space)
        planePainter.paint(tree, 0f);

        // 5. Reparent all painted entities under the panel transform
        planePainter.reparentAll(panelEntity);

        log.debug("Panel painted: {}x{}px on {}x{}m, {} entities",
                pixelW, pixelH, panelWidthM, panelHeightM, planePainter.entityCount());
    }

    @Override
    public void clear() {
        for (FilamentSurfacePainter p : panelPainters) {
            p.destroy();
        }
        panelPainters.clear();

        for (int panelEntity : panelEntities) {
            scene.removeEntity(panelEntity);
            engine.destroyEntity(panelEntity);
        }
        panelEntities.clear();
    }

    @Override
    public void destroy() {
        clear();
    }
}
