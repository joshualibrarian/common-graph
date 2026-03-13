package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.frame.FrameEntry;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;

/**
 * Default 3D presentation for any Item.
 *
 * <p>ItemSpace assembles component SpatialMounts into a scene. Components
 * with {@link Body} declarations render as 3D geometry; others
 * render their Surface as a flat {@link Body.Panel}.
 *
 * <p>This is the 3D counterpart to {@link dev.everydaythings.graph.ui.scene.surface.ItemSurface}.
 *
 * <h2>Default Scene Setup</h2>
 * <ul>
 *   <li>White room background (0xF2F2F2) with moderate ambient light</li>
 *   <li>Single directional light from above</li>
 *   <li>Perspective camera at (0, 1.5, 1) looking at (0, 0, 1) — straight-on,
 *       close enough that the panel fills the screen and looks flat/2D</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Items get ItemSpace by default
 * @Item.Type(value = "cg:type/item", glyph = "📦")
 * public class Item { ... }
 *
 * // Custom scene for a specific type
 * @Item.Type(value = "cg:type/chess", glyph = "♟", scene = ChessScene.class)
 * public class ChessGame extends Item { ... }
 * }</pre>
 */
@Scene.Environment(background = 0xF2F2F2, ambient = 0x808080)
@Scene.Light(type = "directional", z = "10m", dirZ = -1, intensity = 0.8)
@Scene.Camera(y = "1.5m", z = "1m", targetZ = "1m")
public class ItemSpace extends SpatialSchema<Item> {

    public ItemSpace() {}

    /**
     * Build an ItemSpace from an Item.
     *
     * <p>Iterates all content entries, rendering components with
     * {@link Body} annotations as 3D geometry and others as 2D panel fallbacks.
     */
    public static ItemSpace from(Item item) {
        ItemSpace scene = new ItemSpace();
        scene.value(item);
        return scene;
    }

    @Override
    public void render(SpatialRenderer out) {
        emitCommonProperties(out);

        // Emit space properties from compiled annotation data
        SpatialCompiler.compileSpace(getClass()).emit(out);

        // Render components with spatial mounts
        if (value() != null) {
            renderComponents(out);
        }
    }

    /**
     * Render every component on the item.
     *
     * <p>All content entries are rendered — components with {@link Body} annotations
     * become 3D geometry, others become 2D panel fallbacks. Positioning uses
     * {@link Body.Placement} if present, otherwise components are auto-placed
     * along the X axis.
     */
    private void renderComponents(SpatialRenderer out) {
        RenderContext ctx = out.renderContext();
        double autoX = 0;
        for (FrameEntry entry : value().content()) {
            Object component = value().content()
                    .getLive(entry.frameKey()).orElse(null);
            if (component == null) continue;

            // Position: use compiled @Body.Placement if present, otherwise auto-arrange
            CompiledBody body = SpatialCompiler.compileBody(component.getClass());
            double px = body.hasPlacement() ? CompiledBody.dim(body.placementX(), ctx) : autoX;
            double py = body.hasPlacement() ? CompiledBody.dim(body.placementY(), ctx) : 0;
            double pz = body.hasPlacement() ? CompiledBody.dim(body.placementZ(), ctx) : 0;
            double sx = body.hasPlacement() ? body.scaleX() : scaleX();
            double sy = body.hasPlacement() ? body.scaleY() : scaleY();
            double sz = body.hasPlacement() ? body.scaleZ() : scaleZ();

            out.pushTransform(px, py, pz, 0, 0, 0, 1, sx, sy, sz);
            renderComponent(out, component, body, ctx);
            out.popTransform();

            if (!body.hasPlacement()) {
                autoX += 1.5;
            }
        }
    }

    /**
     * Render a single component's body (compound schema, mesh, primitive, or panel fallback),
     * plus any attached lights and audio.
     *
     * @param out       The spatial renderer
     * @param component The component instance
     * @param body      Compiled body annotations for the component's class
     */
    @SuppressWarnings("unchecked")
    private void renderComponent(SpatialRenderer out, Object component, CompiledBody body,
                                 RenderContext ctx) {
        if (component == null) return;

        if (body.isCompound()) {
            // Compound body — delegate to a SpatialSchema subclass
            try {
                SpatialSchema<Object> schema = (SpatialSchema<Object>)
                        body.as().getDeclaredConstructor().newInstance();
                schema.value(component);
                schema.render(out);
            } catch (ReflectiveOperationException e) {
                // Fall through to mesh/shape/panel
            }
            return;
        } else if (body.hasGeometry()) {
            // Geometry + face panels + light + audio
            body.emitAll(out, component, ctx);
        } else if (body.hasPanel()) {
            // No geometry — render as 2D panel fallback
            out.beginPanel(CompiledBody.dim(body.panelWidth(), ctx),
                    CompiledBody.dim(body.panelHeight(), ctx), body.panelPpm());
            out.endPanel();
            body.emitBodyLight(out, ctx);
            body.emitAudio(out, ctx);
        } else {
            // Nothing to render
        }
    }
}
