package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.tree.TreeSurface;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D botanical tree — renders a tree hierarchy as a literal tree you walk around.
 *
 * <p>Nodes are 3D primitives (spheres, cubes, discs) colored by type.
 * Connections between parent-child nodes are thin brown cylinders (branches).
 * The layout uses a radial cone: root at the bottom, children spread upward
 * along +Z in circles that narrow as you go higher — like a real tree.
 *
 * <p>Z-up convention: tree grows along +Z, children spread in the X-Y plane.
 *
 * <p>TreeBody consumes the same data model as the 2D {@link TreeSurface}
 * (a list of {@link TreeSurface.Node} roots), enabling all three rendering
 * tiers from a single {@link TreeModel}:
 * <ul>
 *   <li>Text (TUI/CLI) — via {@link TreeSurface}</li>
 *   <li>2D (Skia) — via {@link TreeSurface} with graphical renderer</li>
 *   <li>3D (Filament) — via this class</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TreeBody scene = TreeBody.from(treeSurface.roots(), TreeBody.defaultAppearance());
 * scene.render(filamentRenderer);
 * }</pre>
 *
 * @see TreeSurface
 * @see TreeModel
 * @see SpatialRenderer
 */
@Scene.Environment(background = 0xF2F2F2, ambient = 0x808080)
@Scene.Light(type = "directional", z = "10m", dirZ = -1, intensity = 0.8)
@Scene.Camera(y = "6m", z = "3m", targetZ = "2m")
public class TreeBody extends SpatialSchema<Void> {

    // ==================== Layout Constants ====================

    private static final double LEVEL_HEIGHT = 1.5;
    private static final double BASE_RADIUS = 1.5;
    private static final double RADIUS_DECAY = 0.7;
    private static final double BASE_NODE_SIZE = 0.2;
    private static final double NODE_SIZE_DECAY = 0.85;
    private static final double SELECTED_SCALE = 1.3;
    private static final int BRANCH_COLOR = 0x8B6914;
    private static final double BRANCH_RADIUS = 0.03;

    // ==================== Pre-computed Layout ====================

    private List<SceneNode> nodes = new ArrayList<>();
    private List<SceneEdge> edges = new ArrayList<>();

    /**
     * A positioned node with 3D appearance properties.
     */
    public record SceneNode(String id,
                            double x, double y, double z,
                            String shape, int color, double size,
                            boolean selected, boolean expanded) {}

    /**
     * A connection (branch) between parent and child node centers.
     */
    public record SceneEdge(double x1, double y1, double z1,
                            double x2, double y2, double z2) {}

    // ==================== Node Appearance ====================

    /**
     * Maps a tree node to its 3D appearance (shape, color).
     */
    @FunctionalInterface
    public interface NodeAppearance {
        void describe(TreeSurface.Node node, NodeDescriptor desc);
    }

    /**
     * Mutable descriptor filled by {@link NodeAppearance}.
     */
    public static class NodeDescriptor {
        public String shape = "sphere";
        public int color = 0x78788C;
    }

    /**
     * Default appearance: spheres colored by a hash of the node label.
     * Falls back to the default gray-blue (0x78788C) if no label.
     */
    public static NodeAppearance defaultAppearance() {
        return (node, desc) -> {
            desc.shape = "sphere";
            if (node.content() instanceof HandleSurface handle) {
                String label = handle.label();
                if (label != null && !label.isEmpty()) {
                    desc.color = labelToColor(label);
                }
            }
        };
    }

    // ==================== Factory ====================

    public TreeBody() {}

    /**
     * Build a TreeBody from a list of root nodes with the given appearance.
     */
    public static TreeBody from(List<TreeSurface.Node> roots, NodeAppearance appearance) {
        TreeBody scene = new TreeBody();
        scene.nodes = new ArrayList<>();
        scene.edges = new ArrayList<>();

        if (roots.isEmpty()) return scene;

        if (roots.size() == 1) {
            // Single root: place at origin
            layoutSubtree(roots.get(0), appearance,
                    0, 0, 0,
                    0, Math.PI * 2, 0,
                    scene);
        } else {
            // Multiple roots: arrange in a ring at level 0 (X-Y plane)
            double angleStep = (Math.PI * 2) / roots.size();
            for (int i = 0; i < roots.size(); i++) {
                double angle = angleStep * i;
                double rx = BASE_RADIUS * 0.5 * Math.cos(angle);
                double ry = BASE_RADIUS * 0.5 * Math.sin(angle);
                double sectorStart = angle - angleStep / 2;
                double sectorEnd = angle + angleStep / 2;
                layoutSubtree(roots.get(i), appearance,
                        rx, ry, 0,
                        sectorStart, sectorEnd, 0,
                        scene);
            }
        }

        return scene;
    }

    // ==================== Layout Algorithm ====================

    /**
     * Recursively lay out a node and its expanded children.
     *
     * @param node        the tree node
     * @param appearance  maps nodes to shape/color
     * @param x           this node's X position
     * @param y           this node's Y position
     * @param z           this node's Z position
     * @param sectorStart angular start of this node's sector (radians)
     * @param sectorEnd   angular end of this node's sector (radians)
     * @param depth       depth in tree (0 = root)
     * @param scene       accumulates nodes and edges
     */
    private static void layoutSubtree(TreeSurface.Node node,
                                       NodeAppearance appearance,
                                       double x, double y, double z,
                                       double sectorStart, double sectorEnd,
                                       int depth,
                                       TreeBody scene) {
        // Determine appearance
        NodeDescriptor desc = new NodeDescriptor();
        appearance.describe(node, desc);

        double nodeSize = BASE_NODE_SIZE * Math.pow(NODE_SIZE_DECAY, depth);

        scene.nodes.add(new SceneNode(
                node.id(), x, y, z,
                desc.shape, desc.color, nodeSize,
                node.selected(), node.expanded()));

        // Lay out children if expanded (Z-up: grow along +Z, spread in X-Y plane)
        if (node.expanded() && node.hasChildren()) {
            List<TreeSurface.Node> children = node.children();
            int count = children.size();
            double childZ = z + LEVEL_HEIGHT;
            double childRadius = BASE_RADIUS * Math.pow(RADIUS_DECAY, depth + 1);

            if (count == 1) {
                // Single child: place directly above, slightly offset in X-Y
                double midAngle = (sectorStart + sectorEnd) / 2;
                double cx = x + childRadius * 0.3 * Math.cos(midAngle);
                double cy = y + childRadius * 0.3 * Math.sin(midAngle);

                scene.edges.add(new SceneEdge(x, y, z, cx, cy, childZ));
                layoutSubtree(children.get(0), appearance,
                        cx, cy, childZ,
                        sectorStart, sectorEnd, depth + 1, scene);
            } else {
                // Multiple children: distribute evenly in the angular sector (X-Y plane)
                double sectorSize = sectorEnd - sectorStart;
                double angleStep = sectorSize / count;

                for (int i = 0; i < count; i++) {
                    double angle = sectorStart + angleStep * (i + 0.5);
                    double cx = x + childRadius * Math.cos(angle);
                    double cy = y + childRadius * Math.sin(angle);

                    scene.edges.add(new SceneEdge(x, y, z, cx, cy, childZ));

                    double childSectorStart = sectorStart + angleStep * i;
                    double childSectorEnd = childSectorStart + angleStep;
                    layoutSubtree(children.get(i), appearance,
                            cx, cy, childZ,
                            childSectorStart, childSectorEnd, depth + 1, scene);
                }
            }
        }
    }

    // ==================== Rendering ====================

    @Override
    public void render(SpatialRenderer out) {
        emitCommonProperties(out);

        // Emit space properties from compiled annotation data
        SpatialCompiler.compileSpace(getClass()).emit(out);

        // Render edges (branches) first so nodes draw on top
        for (SceneEdge edge : edges) {
            renderEdge(out, edge);
        }

        // Render nodes
        for (SceneNode node : nodes) {
            renderNode(out, node);
        }
    }

    /**
     * Render a branch as a thin oriented cylinder between two points.
     */
    private void renderEdge(SpatialRenderer out, SceneEdge edge) {
        double dx = edge.x2 - edge.x1;
        double dy = edge.y2 - edge.y1;
        double dz = edge.z2 - edge.z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (length < 1e-6) return;

        // Midpoint
        double mx = (edge.x1 + edge.x2) / 2;
        double my = (edge.y1 + edge.y2) / 2;
        double mz = (edge.z1 + edge.z2) / 2;

        // Rotation quaternion: rotate Z-axis to direction vector (Z-up convention)
        double[] q = rotationFromZAxis(dx / length, dy / length, dz / length);

        out.pushTransform(mx, my, mz, q[0], q[1], q[2], q[3], 1, 1, 1);
        // Cylinder: width = diameter, height = length
        out.body("cylinder", BRANCH_RADIUS * 2, length, BRANCH_RADIUS * 2,
                BRANCH_COLOR, 1.0, "lit", List.of("branch"));
        out.popTransform();
    }

    /**
     * Render a node as a shaped 3D primitive.
     */
    private void renderNode(SpatialRenderer out, SceneNode node) {
        double s = node.selected ? node.size * SELECTED_SCALE : node.size;

        out.pushTransform(node.x, node.y, node.z, 0, 0, 0, 1, 1, 1, 1);
        out.body(node.shape, s, s, s, node.color, 1.0, "lit",
                node.selected ? List.of("node", "selected") : List.of("node"));
        out.popTransform();
    }

    // ==================== Quaternion Helper ====================

    /**
     * Compute a quaternion that rotates the Z-axis (0,0,1) to the given
     * normalized direction vector.
     *
     * <p>Z-up convention: tree branches grow along +Z, so cylinders
     * (which are Y-aligned in Filament) need to be rotated to the
     * branch direction in DSL Z-up coordinates.
     *
     * @return {qx, qy, qz, qw}
     */
    public static double[] rotationFromZAxis(double dx, double dy, double dz) {
        // Z-axis = (0, 0, 1)
        // If direction is already Z-axis, return identity
        double dot = dz; // dot product of (0,0,1) and (dx,dy,dz)

        if (dot > 0.9999) {
            // Nearly aligned with Z — identity quaternion
            return new double[]{0, 0, 0, 1};
        }

        if (dot < -0.9999) {
            // Nearly opposite to Z — 180 degree rotation around X
            return new double[]{1, 0, 0, 0};
        }

        // Cross product: (0,0,1) x (dx,dy,dz) = (-dy, dx, 0)
        double cx = -dy;
        double cy = dx;
        double cz = 0;

        // Quaternion from axis-angle: q = (axis * sin(half), cos(half))
        // Using the half-angle formula:
        //   qw = sqrt((1 + dot) / 2)
        //   (qx, qy, qz) = cross / (2 * qw)
        double qw = Math.sqrt((1.0 + dot) / 2.0);
        double scale = 1.0 / (2.0 * qw);

        return new double[]{cx * scale, cy * scale, cz * scale, qw};
    }

    // ==================== Color Helper ====================

    /**
     * Generate a pleasant color from a label string.
     * Uses a hash to distribute hues, with moderate saturation and brightness.
     */
    public static int labelToColor(String label) {
        int hash = label.hashCode();
        // Map to hue in [0, 360), saturation ~0.5, value ~0.7
        float hue = ((hash & 0x7FFFFFFF) % 360);
        float sat = 0.45f + (((hash >> 8) & 0xFF) / 255f) * 0.2f;
        float val = 0.6f + (((hash >> 16) & 0xFF) / 255f) * 0.2f;

        return hsvToRgb(hue, sat, val);
    }

    /**
     * Convert HSV to packed RGB int (0xRRGGBB).
     */
    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = v - c;

        float r, g, b;
        if (h < 60)       { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }

        int ri = (int) ((r + m) * 255);
        int gi = (int) ((g + m) * 255);
        int bi = (int) ((b + m) * 255);

        return (ri << 16) | (gi << 8) | bi;
    }

    // ==================== Accessors (for testing) ====================

    public List<SceneNode> nodes() {
        return nodes;
    }

    public List<SceneEdge> edges() {
        return edges;
    }
}
