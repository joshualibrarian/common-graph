package dev.everydaythings.graph.ui.scene.spatial;

import lombok.Getter;

/**
 * Compiled data for a {@link Body.Panel} on a specific face of a body.
 *
 * <p>Produced by scanning nested static classes that carry both
 * {@code @Body.Panel(face = "...")} and {@code @Surface.*} annotations.
 * The {@link #structureClass} holds the nested class whose {@code @Surface}
 * annotations define the 2D content rendered onto that face.
 *
 * <p>Width and height are stored as strings (e.g., "1.5m") and resolved
 * to meters at emit time via {@link CompiledBody#dim}.
 *
 * @see CompiledBody
 * @see Body.Panel
 */
@Getter
public class CompiledFacePanel {

    private final String face;
    private final String width;
    private final String height;
    private final double ppm;
    private final Class<?> structureClass;

    public CompiledFacePanel(String face, String width, String height, double ppm,
                             Class<?> structureClass) {
        this.face = face;
        this.width = width;
        this.height = height;
        this.ppm = ppm;
        this.structureClass = structureClass;
    }

    /**
     * Compute the translation offset for this face on a box body.
     *
     * <p>Z-up convention: X=right, Y=forward, Z=up.
     * Body dimensions are semantic: width=X, height=Z(up), depth=Y(forward).
     *
     * @param bodyWidth  Box width (meters, already resolved)
     * @param bodyHeight Box height (meters, already resolved)
     * @param bodyDepth  Box depth (meters, already resolved)
     * @return [x, y, z] translation to position the panel on this face
     */
    public double[] faceTranslation(double bodyWidth, double bodyHeight, double bodyDepth) {
        // Nudge panels slightly outward from the body surface to avoid z-fighting
        // with the underlying box face (especially visible in Vulkan backends).
        final double eps = 0.001; // 1mm
        return switch (face) {
            // Filament box local axes: X=width, Y=height(up), Z=depth.
            // DSL->Filament mapping uses DSL Y -> -Filament Z, so front/back
            // translate along DSL Y with inverted sign.
            case "front"  -> new double[] { 0,             -bodyDepth / 2 - eps,   0 };
            case "back"   -> new double[] { 0,              bodyDepth / 2 + eps,   0 };
            case "top"    -> new double[] { 0,              0,               bodyHeight / 2 + eps }; // +Z (up)
            case "bottom" -> new double[] { 0,              0,              -bodyHeight / 2 - eps }; // -Z (down)
            case "left"   -> new double[] {-bodyWidth / 2 - eps,  0,         0 };                    // -X (left)
            case "right"  -> new double[] { bodyWidth / 2 + eps,  0,         0 };                    // +X (right)
            default       -> new double[] { 0, 0, 0 };
        };
    }

    /**
     * Compute the quaternion rotation for this face on a box body.
     *
     * <p>Z-up convention. The panel geometry is created in Filament space facing +Z_Filament,
     * which corresponds to -Y in DSL Z-up coordinates. These rotations orient the panel
     * from its default facing (-Y_DSL) to face outward on each body face.
     *
     * @return [qx, qy, qz, qw] quaternion to orient the panel on this face
     */
    public double[] faceRotation() {
        return switch (face) {
            case "front"  -> new double[] { 0,       0,       0,       1      };  // identity
            case "back"   -> new double[] { 0,       0,       1,       0      };  // 180° around Z
            case "top"    -> new double[] {-0.7071,  0,       0,       0.7071 };  // -90° around X → faces +Z (up)
            case "bottom" -> new double[] { 0.7071,  0,       0,       0.7071 };  //  90° around X → faces -Z (down)
            case "left"   -> new double[] { 0,       0,      -0.7071,  0.7071 };  // -90° around Z → faces -X (left)
            case "right"  -> new double[] { 0,       0,       0.7071,  0.7071 };  //  90° around Z → faces +X (right)
            default       -> new double[] { 0,       0,       0,       1      };
        };
    }
}
