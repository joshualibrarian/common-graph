package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.spatial.SpatialRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.spatial.TreeBody;
import dev.everydaythings.graph.ui.scene.surface.tree.TreeSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TreeBody — the 3D botanical tree layout and rendering.
 */
@DisplayName("TreeBody")
class TreeBodyTest {

    // ==================================================================================
    // Layout Algorithm
    // ==================================================================================

    @Nested
    @DisplayName("Layout")
    class LayoutTest {

        @Test
        @DisplayName("single root node at origin")
        void singleRoot() {
            var root = node("root", "📦", "Root");
            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            assertThat(scene.nodes()).hasSize(1);
            assertThat(scene.edges()).isEmpty();

            var n = scene.nodes().get(0);
            assertThat(n.id()).isEqualTo("root");
            assertThat(n.x()).isEqualTo(0);
            assertThat(n.y()).isEqualTo(0);
            assertThat(n.z()).isEqualTo(0);
        }

        @Test
        @DisplayName("root with three children spreads them in a ring")
        void rootWithChildren() {
            var root = node("root", "📦", "Root")
                    .expanded(true)
                    .addChild(node("a", "📄", "Alpha"))
                    .addChild(node("b", "📄", "Beta"))
                    .addChild(node("c", "📄", "Gamma"));

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            assertThat(scene.nodes()).hasSize(4); // root + 3 children
            assertThat(scene.edges()).hasSize(3); // 3 parent→child connections

            // Root at origin
            var rootNode = findNode(scene, "root");
            assertThat(rootNode.y()).isEqualTo(0);

            // Children at level 1 (Z > 0, tree grows along +Z)
            var a = findNode(scene, "a");
            var b = findNode(scene, "b");
            var c = findNode(scene, "c");
            assertThat(a.z()).isGreaterThan(0);
            assertThat(b.z()).isGreaterThan(0);
            assertThat(c.z()).isGreaterThan(0);

            // Children spread in XY plane (not all at same position)
            assertThat(distXY(a, b)).isGreaterThan(0.1);
            assertThat(distXY(b, c)).isGreaterThan(0.1);
            assertThat(distXY(a, c)).isGreaterThan(0.1);
        }

        @Test
        @DisplayName("collapsed node does not lay out children")
        void collapsedNode() {
            var root = node("root", "📦", "Root")
                    .expanded(false)
                    .addChild(node("a", "📄", "Alpha"))
                    .addChild(node("b", "📄", "Beta"));

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            // Only root is laid out
            assertThat(scene.nodes()).hasSize(1);
            assertThat(scene.edges()).isEmpty();
        }

        @Test
        @DisplayName("deep tree has increasing Z positions")
        void deepTree() {
            var leaf = node("d3", "📄", "Leaf");
            var mid = node("d2", "📁", "Mid").expanded(true).addChild(leaf);
            var child = node("d1", "📁", "Child").expanded(true).addChild(mid);
            var root = node("root", "📦", "Root").expanded(true).addChild(child);

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            assertThat(scene.nodes()).hasSize(4);

            double z0 = findNode(scene, "root").z();
            double z1 = findNode(scene, "d1").z();
            double z2 = findNode(scene, "d2").z();
            double z3 = findNode(scene, "d3").z();

            assertThat(z1).isGreaterThan(z0);
            assertThat(z2).isGreaterThan(z1);
            assertThat(z3).isGreaterThan(z2);
        }

        @Test
        @DisplayName("node sizes decrease with depth")
        void sizesDecreaseWithDepth() {
            var leaf = node("d2", "📄", "Leaf");
            var child = node("d1", "📁", "Child").expanded(true).addChild(leaf);
            var root = node("root", "📦", "Root").expanded(true).addChild(child);

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            double s0 = findNode(scene, "root").size();
            double s1 = findNode(scene, "d1").size();
            double s2 = findNode(scene, "d2").size();

            assertThat(s0).isGreaterThan(s1);
            assertThat(s1).isGreaterThan(s2);
        }

        @Test
        @DisplayName("selected node is marked")
        void selectedNode() {
            var root = node("root", "📦", "Root").selected(true);
            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            assertThat(findNode(scene, "root").selected()).isTrue();
        }

        @Test
        @DisplayName("multiple roots are spread in a ring")
        void multipleRoots() {
            var r1 = node("r1", "📦", "One");
            var r2 = node("r2", "📦", "Two");
            var r3 = node("r3", "📦", "Three");

            var scene = TreeBody.from(List.of(r1, r2, r3), TreeBody.defaultAppearance());

            assertThat(scene.nodes()).hasSize(3);

            // All at Z=0 (tree root level)
            assertThat(findNode(scene, "r1").z()).isEqualTo(0);
            assertThat(findNode(scene, "r2").z()).isEqualTo(0);
            assertThat(findNode(scene, "r3").z()).isEqualTo(0);

            // Spread in XY plane
            var n1 = findNode(scene, "r1");
            var n2 = findNode(scene, "r2");
            var n3 = findNode(scene, "r3");
            assertThat(distXY(n1, n2)).isGreaterThan(0.1);
        }

        @Test
        @DisplayName("edges connect correct pairs")
        void edgesMatchParentChild() {
            var root = node("root", "📦", "Root")
                    .expanded(true)
                    .addChild(node("a", "📄", "A"))
                    .addChild(node("b", "📄", "B"));

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            assertThat(scene.edges()).hasSize(2);

            var rootNode = findNode(scene, "root");
            for (TreeBody.SceneEdge edge : scene.edges()) {
                // Each edge starts at root's position
                assertThat(edge.x1()).isEqualTo(rootNode.x());
                assertThat(edge.y1()).isEqualTo(rootNode.y());
                assertThat(edge.z1()).isEqualTo(rootNode.z());

                // And ends at a child's position
                var child = scene.nodes().stream()
                        .filter(n -> n.x() == edge.x2() && n.y() == edge.y2() && n.z() == edge.z2())
                        .findFirst();
                assertThat(child).isPresent();
            }
        }
    }

    // ==================================================================================
    // Quaternion Helper
    // ==================================================================================

    @Nested
    @DisplayName("rotationFromZAxis")
    class RotationTest {

        @Test
        @DisplayName("Z-axis direction gives identity quaternion")
        void zAxisIdentity() {
            double[] q = TreeBody.rotationFromZAxis(0, 0, 1);
            assertThat(q[0]).isCloseTo(0, offset(1e-6));
            assertThat(q[1]).isCloseTo(0, offset(1e-6));
            assertThat(q[2]).isCloseTo(0, offset(1e-6));
            assertThat(q[3]).isCloseTo(1, offset(1e-6));
        }

        @Test
        @DisplayName("negative Z-axis gives 180 degree rotation")
        void negativeZ() {
            double[] q = TreeBody.rotationFromZAxis(0, 0, -1);
            // Should be a 180-degree rotation (qw ≈ 0, magnitude of xyz ≈ 1)
            double magnitude = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
            assertThat(magnitude).isCloseTo(1.0, offset(1e-6));
            assertThat(q[3]).isCloseTo(0, offset(1e-3)); // qw ≈ 0 for 180 degrees
        }

        @Test
        @DisplayName("X-axis direction gives 90 degree rotation")
        void xAxisRotation() {
            double[] q = TreeBody.rotationFromZAxis(1, 0, 0);
            // Rotating Z→X is a -90 degree rotation around Y
            double magnitude = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
            assertThat(magnitude).isCloseTo(1.0, offset(1e-6));
        }

        @Test
        @DisplayName("arbitrary direction produces unit quaternion")
        void arbitraryDirection() {
            double len = Math.sqrt(1 + 4 + 9);
            double[] q = TreeBody.rotationFromZAxis(1/len, 2/len, 3/len);
            double magnitude = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
            assertThat(magnitude).isCloseTo(1.0, offset(1e-6));
        }
    }

    // ==================================================================================
    // Rendering
    // ==================================================================================

    @Nested
    @DisplayName("Rendering")
    class RenderTest {

        @Test
        @DisplayName("renders environment, light, camera, then bodies")
        void renderOrder() {
            var root = node("root", "📦", "Root")
                    .expanded(true)
                    .addChild(node("a", "📄", "Child"));

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());
            var recorder = new RecordingSpatialRenderer();
            scene.render(recorder);

            // Environment, light, camera come first
            assertThat(recorder.calls.get(0)).startsWith("environment(");
            assertThat(recorder.calls.get(1)).startsWith("light(");
            assertThat(recorder.calls.get(2)).startsWith("camera(");

            // Followed by edge (pushTransform + body + popTransform)
            // and nodes (pushTransform + body + popTransform)
            long bodyCount = recorder.calls.stream()
                    .filter(c -> c.startsWith("body("))
                    .count();
            // 1 edge cylinder + 2 node bodies
            assertThat(bodyCount).isEqualTo(3);

            long pushCount = recorder.calls.stream()
                    .filter(c -> c.startsWith("pushTransform("))
                    .count();
            long popCount = recorder.calls.stream()
                    .filter(c -> c.equals("popTransform()"))
                    .count();
            assertThat(pushCount).isEqualTo(popCount);
        }

        @Test
        @DisplayName("single node emits one body, no edges")
        void singleNodeRendering() {
            var root = node("root", "📦", "Root");
            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());
            var recorder = new RecordingSpatialRenderer();
            scene.render(recorder);

            long bodyCount = recorder.calls.stream()
                    .filter(c -> c.startsWith("body("))
                    .count();
            // 1 node, 0 edges
            assertThat(bodyCount).isEqualTo(1);

            // No cylinder for edges
            long cylinderCount = recorder.calls.stream()
                    .filter(c -> c.startsWith("body(cylinder"))
                    .count();
            assertThat(cylinderCount).isEqualTo(0);
        }

        @Test
        @DisplayName("edges render as cylinders")
        void edgesAreCylinders() {
            var root = node("root", "📦", "Root")
                    .expanded(true)
                    .addChild(node("a", "📄", "A"));

            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());
            var recorder = new RecordingSpatialRenderer();
            scene.render(recorder);

            long cylinderCount = recorder.calls.stream()
                    .filter(c -> c.startsWith("body(cylinder"))
                    .count();
            assertThat(cylinderCount).isEqualTo(1);
        }
    }

    // ==================================================================================
    // Node Appearance
    // ==================================================================================

    @Nested
    @DisplayName("NodeAppearance")
    class AppearanceTest {

        @Test
        @DisplayName("custom appearance overrides shape and color")
        void customAppearance() {
            var root = node("root", "📦", "Root");

            TreeBody.NodeAppearance custom = (node, desc) -> {
                desc.shape = "cube";
                desc.color = 0xFF0000;
            };

            var scene = TreeBody.from(List.of(root), custom);
            var n = findNode(scene, "root");
            assertThat(n.shape()).isEqualTo("cube");
            assertThat(n.color()).isEqualTo(0xFF0000);
        }

        @Test
        @DisplayName("default appearance uses label hash for color")
        void defaultAppearanceUsesLabelColor() {
            var root = node("root", "📦", "Alpha");
            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            var n = findNode(scene, "root");
            // Color should be deterministic based on label
            assertThat(n.color()).isNotEqualTo(0x78788C); // Not the fallback gray
        }

        @Test
        @DisplayName("default appearance falls back to gray when no label")
        void defaultAppearanceFallback() {
            // Node with no HandleSurface content
            var root = TreeSurface.Node.of("root", null);
            var scene = TreeBody.from(List.of(root), TreeBody.defaultAppearance());

            var n = findNode(scene, "root");
            assertThat(n.color()).isEqualTo(0x78788C);
        }
    }

    // ==================================================================================
    // Color Helper
    // ==================================================================================

    @Nested
    @DisplayName("labelToColor")
    class ColorTest {

        @Test
        @DisplayName("same label produces same color")
        void deterministic() {
            int c1 = TreeBody.labelToColor("hello");
            int c2 = TreeBody.labelToColor("hello");
            assertThat(c1).isEqualTo(c2);
        }

        @Test
        @DisplayName("different labels produce different colors")
        void varied() {
            int c1 = TreeBody.labelToColor("Alpha");
            int c2 = TreeBody.labelToColor("Beta");
            int c3 = TreeBody.labelToColor("Gamma");
            // At least 2 of 3 should be different
            assertThat(c1 != c2 || c2 != c3 || c1 != c3).isTrue();
        }

        @Test
        @DisplayName("color is valid RGB")
        void validRange() {
            int color = TreeBody.labelToColor("Test Label");
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            assertThat(r).isBetween(0, 255);
            assertThat(g).isBetween(0, 255);
            assertThat(b).isBetween(0, 255);
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static TreeSurface.Node node(String id, String icon, String label) {
        return TreeSurface.Node.of(id, HandleSurface.of(icon, label));
    }

    private static TreeBody.SceneNode findNode(TreeBody scene, String id) {
        return scene.nodes().stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Node not found: " + id));
    }

    private static double distXY(TreeBody.SceneNode a, TreeBody.SceneNode b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static org.assertj.core.data.Offset<Double> offset(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }

    // ==================================================================================
    // Recording Renderer
    // ==================================================================================

    static class RecordingSpatialRenderer implements SpatialRenderer {
        final List<String> calls = new ArrayList<>();

        @Override
        public void body(String shape, double w, double h, double d,
                          int color, double opacity, String shading, List<String> styles) {
            calls.add("body(%s,%.2f,%.2f,%.2f,%d,%.1f,%s)".formatted(shape, w, h, d, color, opacity, shading));
        }

        @Override
        public void meshBody(String meshRef, int color, double opacity, String shading, List<String> styles) {
            calls.add("meshBody(%s)".formatted(meshRef));
        }

        @Override
        public void pushTransform(double x, double y, double z,
                                   double qx, double qy, double qz, double qw,
                                   double sx, double sy, double sz) {
            calls.add("pushTransform(%.2f,%.2f,%.2f)".formatted(x, y, z));
        }

        @Override
        public void popTransform() {
            calls.add("popTransform()");
        }

        @Override
        public void beginPanel(double width, double height, double ppm) {
            calls.add("beginPanel()");
        }

        @Override
        public SurfaceRenderer panelRenderer() {
            return null;
        }

        @Override
        public void endPanel() {
            calls.add("endPanel()");
        }

        @Override
        public void light(String type, int color, double intensity,
                           double x, double y, double z,
                           double dirX, double dirY, double dirZ) {
            calls.add("light(%s,%d,%.1f)".formatted(type, color, intensity));
        }

        @Override
        public void camera(String projection, double fov, double near, double far,
                            double x, double y, double z,
                            double tx, double ty, double tz) {
            calls.add("camera(%s,%.1f)".formatted(projection, fov));
        }

        @Override
        public void environment(int background, int ambient,
                                 double fogNear, double fogFar, int fogColor) {
            calls.add("environment(%d,%d)".formatted(background, ambient));
        }

        @Override
        public void id(String id) {
            calls.add("id(%s)".formatted(id));
        }
    }
}
