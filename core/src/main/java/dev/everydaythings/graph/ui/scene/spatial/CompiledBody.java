package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.SizeValue;
import dev.everydaythings.graph.ui.scene.TransitionSpec;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiled snapshot of {@link Scene.Body} annotations for a component class.
 *
 * <p>Captures {@link Scene.Body}, {@link Scene.Transform}, {@link Scene.Face},
 * {@link Scene.Light}, and {@link Scene.Audio} annotation data in a single
 * immutable record. Annotations are read once by {@link SpatialCompiler}
 * and cached — renderers consume this record instead of calling
 * {@code getAnnotation()} per frame.
 *
 * <p>Dimension fields are stored as strings (e.g., "2m", "50cm") and
 * resolved to meters at emit time via {@link RenderContext}.
 *
 * @see SpatialCompiler
 * @see Scene.Body
 */
public record CompiledBody(
        // @Body
        boolean hasBody,
        Class<? extends SpatialSchema> as,
        String shape, String mesh,
        String width, String height, String depth, String radius,
        int color, double opacity, String shading, String fontSize,

        // @Body.Placement
        boolean hasPlacement,
        String placementX, String placementY, String placementZ,
        double placementYaw, double placementPitch, double placementRoll,
        double placementAxisX, double placementAxisY, double placementAxisZ, double placementAngle,
        double scaleX, double scaleY, double scaleZ,

        // @Body.Panel
        boolean hasPanel,
        String panelWidth, String panelHeight, double panelPpm,

        // @Body.Light
        boolean hasBodyLight,
        String bodyLightType, int bodyLightColor, double bodyLightIntensity,
        String bodyLightX, String bodyLightY, String bodyLightZ,
        double bodyLightDirX, double bodyLightDirY, double bodyLightDirZ,

        // @Body.Audio
        boolean hasAudio,
        String audioSrc,
        String audioX, String audioY, String audioZ,
        double audioVolume, double audioPitch, boolean audioLoop,
        boolean audioSpatial, String audioRefDistance, String audioMaxDistance,
        boolean audioAutoplay,

        // @Transition
        List<TransitionSpec> transitions,

        // Face panels (from nested static classes with @Body.Panel(face = "..."))
        List<CompiledFacePanel> facePanels
) {

    /** Empty compiled body — no annotations present. */
    public static final CompiledBody EMPTY = new CompiledBody(
            // Body
            false, SpatialSchema.class, "", "", "1m", "1m", "1m", "0.5m", 0x808080, 1.0, "lit", "",
            // Placement
            false, "0", "0", "0", 0, 0, 0, 0, 1, 0, 0, 1, 1, 1,
            // Panel
            false, "1m", "0.75m", 512,
            // Body.Light (Z-up: y=forward, z=up)
            false, "directional", 0xFFFFFF, 1.0, "0", "0", "5m", 0, 0, -1,
            // Audio
            false, "", "0", "0", "0", 1.0, 1.0, false, true, "1m", "50m", false,
            // Transitions
            List.of(),
            // Face panels
            List.of()
    );

    /**
     * Resolve a dimension string to meters via the graph's Unit system.
     *
     * @param spec dimension string (e.g., "2m", "50cm", "0")
     * @param ctx  render context for unit resolution (may be null for tests)
     * @return value in meters, or 0 if unparseable
     */
    public static double dim(String spec, RenderContext ctx) {
        if (spec == null || spec.isEmpty()) return 0;
        SizeValue sv = SizeValue.parse(spec, "m");
        if (sv == null) return 0;
        if (ctx != null) {
            return sv.toMeters(ctx);
        }
        // Fallback for null context (tests): bare number defaults to meters
        return sv.value();
    }

    /**
     * Compile all @Scene.Body/Transform/Face/Light/Audio annotations from a class.
     */
    static CompiledBody from(Class<?> componentClass) {
        Scene.Body sceneBody = componentClass.getAnnotation(Scene.Body.class);
        boolean hasBody = sceneBody != null;

        Scene.Transform transform = componentClass.getAnnotation(Scene.Transform.class);
        boolean hasPlacement = transform != null;

        // Class-level @Scene.Face = standalone panel (no geometry, just a 2D panel in 3D)
        Scene.Face classLevelFace = componentClass.getAnnotation(Scene.Face.class);
        boolean hasPanel = classLevelFace != null;

        Scene.Light sceneLight = componentClass.getAnnotation(Scene.Light.class);
        boolean hasLight = sceneLight != null;

        Scene.Audio sceneAudio = componentClass.getAnnotation(Scene.Audio.class);
        boolean hasAudio = sceneAudio != null;

        // Scan nested static classes for @Scene.Face (face panels on body geometry)
        List<CompiledFacePanel> facePanels = new ArrayList<>();
        for (Class<?> nested : componentClass.getDeclaredClasses()) {
            if (!Modifier.isStatic(nested.getModifiers())) continue;
            Scene.Face sceneFace = nested.getAnnotation(Scene.Face.class);
            if (sceneFace != null) {
                String fw, fh;
                if (sceneBody != null) {
                    fw = deriveFaceWidth(sceneFace.value(), sceneBody.width(), sceneBody.depth());
                    fh = deriveFaceHeight(sceneFace.value(), sceneBody.height(), sceneBody.depth());
                } else {
                    fw = "1m"; fh = "0.75m";
                }
                facePanels.add(new CompiledFacePanel(
                        sceneFace.value(), fw, fh,
                        sceneFace.ppm(), nested));
            }
        }

        if (!hasBody && !hasPlacement && !hasPanel
                && !hasLight && !hasAudio && facePanels.isEmpty()) {
            return EMPTY;
        }

        // Resolve body attributes from @Scene.Body
        @SuppressWarnings("unchecked")
        Class<? extends SpatialSchema> asClass = SpatialSchema.class;
        if (sceneBody != null && sceneBody.as() != SceneSchema.class) {
            asClass = (Class<? extends SpatialSchema>) (Class<?>) sceneBody.as();
        }

        // For standalone panel, derive width/height from @Scene.Body if present, else defaults
        String panelW = "1m", panelH = "0.75m";
        double panelPpm = 512;
        if (hasPanel) {
            panelPpm = classLevelFace.ppm();
            if (sceneBody != null) {
                panelW = sceneBody.width();
                panelH = sceneBody.height();
            }
        }

        return new CompiledBody(
                // Body
                hasBody,
                asClass,
                sceneBody != null ? sceneBody.shape() : "",
                sceneBody != null ? sceneBody.mesh() : "",
                sceneBody != null ? sceneBody.width() : "1m",
                sceneBody != null ? sceneBody.height() : "1m",
                sceneBody != null ? sceneBody.depth() : "1m",
                sceneBody != null ? sceneBody.radius() : "0.5m",
                sceneBody != null ? sceneBody.color() : 0x808080,
                sceneBody != null ? sceneBody.opacity() : 1.0,
                sceneBody != null ? sceneBody.shading() : "lit",
                sceneBody != null ? sceneBody.fontSize() : "",

                // Placement from @Scene.Transform
                hasPlacement,
                transform != null ? transform.x() : "0",
                transform != null ? transform.y() : "0",
                transform != null ? transform.z() : "0",
                transform != null ? transform.yaw() : 0,
                transform != null ? transform.pitch() : 0,
                transform != null ? transform.roll() : 0,
                transform != null ? transform.axisX() : 0,
                transform != null ? transform.axisY() : 1,
                transform != null ? transform.axisZ() : 0,
                transform != null ? transform.angle() : 0,
                transform != null ? transform.scaleX() : 1,
                transform != null ? transform.scaleY() : 1,
                transform != null ? transform.scaleZ() : 1,

                // Panel from class-level @Scene.Face + @Scene.Body dimensions
                hasPanel,
                panelW, panelH, panelPpm,

                // Light from @Scene.Light
                hasLight,
                sceneLight != null ? sceneLight.type() : "directional",
                sceneLight != null ? sceneLight.color() : 0xFFFFFF,
                sceneLight != null ? sceneLight.intensity() : 1.0,
                sceneLight != null ? sceneLight.x() : "0",
                sceneLight != null ? sceneLight.y() : "5m",
                sceneLight != null ? sceneLight.z() : "0",
                sceneLight != null ? sceneLight.dirX() : 0,
                sceneLight != null ? sceneLight.dirY() : -1,
                sceneLight != null ? sceneLight.dirZ() : 0,

                // Audio from @Scene.Audio
                hasAudio,
                sceneAudio != null ? sceneAudio.src() : "",
                sceneAudio != null ? sceneAudio.x() : "0",
                sceneAudio != null ? sceneAudio.y() : "0",
                sceneAudio != null ? sceneAudio.z() : "0",
                sceneAudio != null ? sceneAudio.volume() : 1.0,
                sceneAudio != null ? sceneAudio.pitch() : 1.0,
                sceneAudio != null && sceneAudio.loop(),
                sceneAudio == null || sceneAudio.spatial(),
                sceneAudio != null ? sceneAudio.refDistance() : "1m",
                sceneAudio != null ? sceneAudio.maxDistance() : "50m",
                sceneAudio != null && sceneAudio.autoplay(),

                // Transitions
                TransitionSpec.fromClass(componentClass),

                // Face panels
                facePanels
        );
    }

    /** Derive face width from body dimensions and face name. */
    private static String deriveFaceWidth(String face, String bodyWidth, String bodyDepth) {
        return switch (face) {
            case "left", "right" -> bodyDepth;
            default -> bodyWidth; // front, back, top, bottom
        };
    }

    /** Derive face height from body dimensions and face name. */
    private static String deriveFaceHeight(String face, String bodyHeight, String bodyDepth) {
        return switch (face) {
            case "top", "bottom" -> bodyDepth;
            default -> bodyHeight; // front, back, left, right
        };
    }

    /**
     * Whether this has a compound body (delegates to a SpatialSchema subclass).
     */
    public boolean isCompound() {
        return hasBody && as != SpatialSchema.class;
    }

    /**
     * Whether this has inline geometry (shape or mesh).
     */
    public boolean hasGeometry() {
        return hasBody && !shape.equals("none") && (!shape.isEmpty() || !mesh.isEmpty());
    }

    /**
     * Whether body dimensions should be derived from the layout's elevated bounds.
     * True when a physical fontSize is declared, establishing the em-to-meters mapping.
     */
    public boolean isDerivedSize() {
        return fontSize != null && !fontSize.isEmpty();
    }

    /**
     * Emit the body light to a renderer (if present).
     */
    public void emitBodyLight(SpatialRenderer out, RenderContext ctx) {
        if (hasBodyLight) {
            out.light(bodyLightType, bodyLightColor, bodyLightIntensity,
                    dim(bodyLightX, ctx), dim(bodyLightY, ctx), dim(bodyLightZ, ctx),
                    bodyLightDirX, bodyLightDirY, bodyLightDirZ);
        }
    }

    /**
     * Emit the body audio to a renderer (if present).
     */
    public void emitAudio(SpatialRenderer out, RenderContext ctx) {
        if (hasAudio && !audioSrc.isEmpty()) {
            out.audio(audioSrc,
                    dim(audioX, ctx), dim(audioY, ctx), dim(audioZ, ctx),
                    audioVolume, audioPitch, audioLoop,
                    audioSpatial, dim(audioRefDistance, ctx), dim(audioMaxDistance, ctx),
                    audioAutoplay);
        }
    }

    /**
     * Emit the body geometry (shape or mesh) to a renderer.
     */
    public void emitGeometry(SpatialRenderer out, RenderContext ctx) {
        if (!mesh.isEmpty()) {
            out.meshBody(mesh, color, opacity, shading, List.of());
        } else if (!shape.isEmpty()) {
            out.body(shape, dim(width, ctx), dim(height, ctx), dim(depth, ctx),
                    color, opacity, shading, List.of());
        }
    }

    /**
     * Whether this body has face panels (nested classes with @Body.Panel(face = "...")).
     */
    public boolean hasFacePanels() {
        return !facePanels.isEmpty();
    }

    /**
     * Emit face panels onto the body's geometry faces.
     *
     * <p>For each face panel, pushes a transform to position/orient the panel
     * on the named face of the box, opens a panel, renders the nested class's
     * {@code @Surface} annotations via a wrapper SurfaceSchema, and closes.
     *
     * @param out       The spatial renderer
     * @param component The component instance (used as the value for binding expressions)
     * @param ctx       Render context for unit resolution
     */
    @SuppressWarnings("unchecked")
    public void emitFacePanels(SpatialRenderer out, Object component, RenderContext ctx) {
        double w = dim(width, ctx);
        double h = dim(height, ctx);
        double d = dim(depth, ctx);
        for (CompiledFacePanel fp : facePanels) {
            double[] t = fp.faceTranslation(w, h, d);
            double[] r = fp.faceRotation();

            out.pushTransform(t[0], t[1], t[2], r[0], r[1], r[2], r[3], 1, 1, 1);
            out.beginPanel(dim(fp.width(), ctx), dim(fp.height(), ctx), fp.ppm());

            // Use a plain SurfaceSchema wrapper so render() compiles from structureClass.
            // ContainerSurface overrides render() and ignores structureClass, which
            // silently emits empty face panels.
            SurfaceSchema<Object> wrapper = new SurfaceSchema<>() {};
            wrapper.structureClass(fp.structureClass());
            wrapper.value(component);
            wrapper.render(out.panelRenderer());

            out.endPanel();
            out.popTransform();
        }
    }

    /**
     * Emit everything: geometry + face panels + light + audio.
     *
     * <p>Convenience method that calls all emit methods in order.
     * Face panels are only emitted when geometry is present (faces need a body).
     *
     * @param out       The spatial renderer
     * @param component The component instance (for face panel binding)
     * @param ctx       Render context for unit resolution
     */
    public void emitAll(SpatialRenderer out, Object component, RenderContext ctx) {
        emitGeometry(out, ctx);
        if (hasFacePanels()) {
            emitFacePanels(out, component, ctx);
        }
        emitBodyLight(out, ctx);
        emitAudio(out, ctx);
    }
}
