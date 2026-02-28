package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AxisGizmoTest {

    @Test
    void render_emitsThreeAxisLines() {
        var gizmo = new AxisGizmo();
        var recorder = new LineRecorder();

        gizmo.render(recorder);

        assertThat(recorder.lines).hasSize(3);
    }

    @Test
    void render_xAxisIsRed() {
        var gizmo = new AxisGizmo();
        var recorder = new LineRecorder();

        gizmo.render(recorder);

        LineCall x = recorder.lines.get(0);
        assertThat(x.x1).isEqualTo(0);
        assertThat(x.y1).isEqualTo(0);
        assertThat(x.z1).isEqualTo(0);
        assertThat(x.x2).isEqualTo(0.5);
        assertThat(x.y2).isEqualTo(0);
        assertThat(x.z2).isEqualTo(0);
        assertThat(x.color).isEqualTo(0xFF0000);
    }

    @Test
    void render_yAxisIsGreen() {
        var gizmo = new AxisGizmo();
        var recorder = new LineRecorder();

        gizmo.render(recorder);

        LineCall y = recorder.lines.get(1);
        assertThat(y.x2).isEqualTo(0);
        assertThat(y.y2).isEqualTo(0.5);
        assertThat(y.z2).isEqualTo(0);
        assertThat(y.color).isEqualTo(0x00FF00);
    }

    @Test
    void render_zAxisIsBlue() {
        var gizmo = new AxisGizmo();
        var recorder = new LineRecorder();

        gizmo.render(recorder);

        LineCall z = recorder.lines.get(2);
        assertThat(z.x2).isEqualTo(0);
        assertThat(z.y2).isEqualTo(0);
        assertThat(z.z2).isEqualTo(0.5);
        assertThat(z.color).isEqualTo(0x0000FF);
    }

    @Test
    void render_customLength() {
        var gizmo = new AxisGizmo().length(1.0);
        var recorder = new LineRecorder();

        gizmo.render(recorder);

        assertThat(recorder.lines.get(0).x2).isEqualTo(1.0);
        assertThat(recorder.lines.get(1).y2).isEqualTo(1.0);
        assertThat(recorder.lines.get(2).z2).isEqualTo(1.0);
    }

    @Test
    void render_customWidth() {
        var gizmo = new AxisGizmo().width(0.01);
        var recorder = new LineRecorder();

        gizmo.render(recorder);

        for (LineCall line : recorder.lines) {
            assertThat(line.width).isEqualTo(0.01);
        }
    }

    // ==================== Test Helpers ====================

    static class LineCall {
        double x1, y1, z1, x2, y2, z2;
        int color;
        double width;

        LineCall(double x1, double y1, double z1,
                 double x2, double y2, double z2,
                 int color, double width) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.color = color; this.width = width;
        }
    }

    static class LineRecorder implements SpatialRenderer {
        final List<LineCall> lines = new ArrayList<>();

        @Override
        public void line(double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         int color, double width) {
            lines.add(new LineCall(x1, y1, z1, x2, y2, z2, color, width));
        }

        @Override public void body(String shape, double w, double h, double d, int color, double opacity, String shading, List<String> styles) {}
        @Override public void meshBody(String meshRef, int color, double opacity, String shading, List<String> styles) {}
        @Override public void pushTransform(double x, double y, double z, double qx, double qy, double qz, double qw, double sx, double sy, double sz) {}
        @Override public void popTransform() {}
        @Override public void beginPanel(double w, double h, double ppm) {}
        @Override public void endPanel() {}
        @Override public SurfaceRenderer panelRenderer() { return null; }
        @Override public void light(String type, int color, double intensity, double x, double y, double z, double dirX, double dirY, double dirZ) {}
        @Override public void camera(String proj, double fov, double near, double far, double x, double y, double z, double tx, double ty, double tz) {}
        @Override public void environment(int bg, int ambient, double fogNear, double fogFar, int fogColor) {}
        @Override public void id(String id) {}
    }
}
