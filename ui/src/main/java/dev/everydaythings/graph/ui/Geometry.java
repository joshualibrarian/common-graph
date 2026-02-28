package dev.everydaythings.graph.ui;

import java.util.List;

/**
 * 3D geometry data - meshes, primitives, materials.
 *
 * <p>This is the actual 3D model data that Items can provide via
 * a Geometry component.
 *
 * <p>Geometry can be:
 * <ul>
 *   <li>Primitives (sphere, box, cylinder)</li>
 *   <li>Custom meshes (vertices, faces)</li>
 *   <li>External references (GLTF, OBJ)</li>
 *   <li>Compounds (multiple geometries combined)</li>
 * </ul>
 *
 * <p>Items provide Geometry via components. The renderer uses
 * the Item's Presentation to know the paradigm, and Geometry (if present)
 * for custom visuals.
 *
 * <p><b>Format TBD:</b> Could be native CBOR, could wrap GLTF/OBJ,
 * could be something custom. CG is non-controlling.
 */
public sealed interface Geometry {

    // ==================================================================================
    // Primitives
    // ==================================================================================

    record Sphere(double radius, Material material) implements Geometry {
        public Sphere(double radius) { this(radius, Material.DEFAULT); }
    }

    record Box(double width, double height, double depth, Material material) implements Geometry {
        public Box(double size) { this(size, size, size, Material.DEFAULT); }
        public Box(double w, double h, double d) { this(w, h, d, Material.DEFAULT); }
    }

    record Cylinder(double radius, double height, Material material) implements Geometry {
        public Cylinder(double radius, double height) { this(radius, height, Material.DEFAULT); }
    }

    record Plane(double width, double height, Material material) implements Geometry {
        public Plane(double width, double height) { this(width, height, Material.DEFAULT); }
    }

    // ==================================================================================
    // Complex Geometry
    // ==================================================================================

    /**
     * Arbitrary mesh geometry.
     */
    record Mesh(
            List<Vertex> vertices,
            List<Face> faces,
            Material material
    ) implements Geometry {
        public Mesh {
            vertices = List.copyOf(vertices);
            faces = List.copyOf(faces);
        }
    }

    /**
     * Composed of multiple geometries with transforms.
     */
    record Compound(List<Positioned> children) implements Geometry {
        public Compound { children = List.copyOf(children); }
    }

    /**
     * Reference to external geometry (GLTF, OBJ, etc).
     */
    record External(String contentId, String format) implements Geometry {}

    // ==================================================================================
    // Supporting Types
    // ==================================================================================

    record Vertex(
            double x, double y, double z,       // position
            double nx, double ny, double nz,    // normal
            double u, double v                  // texture coords
    ) {
        public Vertex(double x, double y, double z) {
            this(x, y, z, 0, 1, 0, 0, 0);
        }
    }

    record Face(int v1, int v2, int v3) {}

    record Positioned(
            Geometry geometry,
            Vec3 position,
            Quaternion rotation,
            Vec3 scale
    ) {
        public Positioned(Geometry geometry) {
            this(geometry, Vec3.ZERO, Quaternion.IDENTITY, Vec3.ONE);
        }
    }

    record Vec3(double x, double y, double z) {
        public static final Vec3 ZERO = new Vec3(0, 0, 0);
        public static final Vec3 ONE = new Vec3(1, 1, 1);
    }

    record Quaternion(double x, double y, double z, double w) {
        public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);
    }

    // ==================================================================================
    // Material
    // ==================================================================================

    record Material(
            Color color,
            double metallic,
            double roughness,
            String texture         // optional ContentID of texture
    ) {
        public static final Material DEFAULT = new Material(
                new Color(0.8, 0.8, 0.8, 1.0), 0.0, 0.5, null
        );

        public static Material color(double r, double g, double b) {
            return new Material(new Color(r, g, b, 1.0), 0.0, 0.5, null);
        }
    }

    record Color(double r, double g, double b, double a) {
        public static final Color WHITE = new Color(1, 1, 1, 1);
        public static final Color BLACK = new Color(0, 0, 0, 1);
        public static final Color RED = new Color(1, 0, 0, 1);
        public static final Color GREEN = new Color(0, 1, 0, 1);
        public static final Color BLUE = new Color(0, 0, 1, 1);
    }
}
