package dev.everydaythings.graph.ui.scene.spatial;

/**
 * A 3D axis gizmo that draws three colored lines from the origin:
 * red (X = right), green (Y = forward), blue (Z = up).
 *
 * <p>Z-up convention matching Blender/Unreal: X=right, Y=forward, Z=up.
 *
 * <p>This is a utility overlay, not a scene — it has no {@code @Space}
 * annotations. Use it by calling {@link #render(SpatialRenderer)} directly.
 */
public class AxisGizmo extends SpatialSchema<Void> {

    private double length = 0.5;  // 50cm per axis arm
    private double width = 0.003; // 3mm line width

    public AxisGizmo length(double length) {
        this.length = length;
        return this;
    }

    public AxisGizmo width(double width) {
        this.width = width;
        return this;
    }

    @Override
    public void render(SpatialRenderer out) {
        out.line(0, 0, 0, length, 0, 0, 0xFF0000, width); // X red   (right)
        out.line(0, 0, 0, 0, length, 0, 0x00FF00, width); // Y green (forward)
        out.line(0, 0, 0, 0, 0, length, 0x0000FF, width); // Z blue  (up)
    }
}
