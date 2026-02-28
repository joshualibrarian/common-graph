package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * 3D object representation of an item (the "holdable" form).
 *
 * <p>Model is one of the four core container components:
 * <ul>
 *   <li><b>Roster</b> - principals present here</li>
 *   <li><b>Surface</b> - 2D layout of contents</li>
 *   <li><b>Space</b> - 3D environment for contents</li>
 *   <li><b>Model</b> - 3D object representation (this)</li>
 * </ul>
 *
 * <p>Containment itself is expressed through reference entries in the
 * ComponentTable, not a separate component.
 *
 * <p>A Model describes how an item looks as a 3D object:
 * <ul>
 *   <li>When viewed in a 3D space (as an icon you can pick up)</li>
 *   <li>When rendered as a 3D preview</li>
 *   <li>Falls back to 2D icon if no 3D support</li>
 * </ul>
 *
 * <p>This is the 3D equivalent of an icon. While Space is what
 * you see when INSIDE an item, Model is what you see when
 * looking AT an item from outside.
 *
 * <p>Examples:
 * <ul>
 *   <li>A document might have a scroll or book model</li>
 *   <li>A music file might have a vinyl record or speaker model</li>
 *   <li>A project folder might have a box or filing cabinet model</li>
 *   <li>A person might have an avatar model</li>
 * </ul>
 */
@Type(value = Model.KEY, glyph = "🎭")
public final class Model implements Component {

    public static final String KEY = "cg:type/model";

    /** The type of model */
    private ModelType type;

    /** Primitive shape parameters (for PRIMITIVE type) */
    private PrimitiveShape primitive;

    /** Reference to mesh data (for MESH type) */
    private ContentID meshCid;

    /** Reference to GLTF asset (for GLTF type) */
    private ContentID gltfCid;

    /** The model's base color */
    private Space.Color color;

    /** Optional texture reference */
    private ContentID textureCid;

    /** Scale factor (1.0 = default size) */
    private double scale;

    /**
     * Create a default model (sphere primitive).
     */
    public Model() {
        this.type = ModelType.PRIMITIVE;
        this.primitive = PrimitiveShape.sphere(0.5);
        this.color = new Space.Color(0.5, 0.5, 0.6, 1.0);
        this.scale = 1.0;
    }

    /**
     * Factory method for default model.
     */
    public static Model create() {
        return new Model();
    }

    /**
     * Factory method for a colored sphere.
     */
    public static Model sphere(double radius, Space.Color color) {
        Model model = new Model();
        model.primitive = PrimitiveShape.sphere(radius);
        model.color = color;
        return model;
    }

    /**
     * Factory method for a colored box.
     */
    public static Model box(double width, double height, double depth, Space.Color color) {
        Model model = new Model();
        model.primitive = PrimitiveShape.box(width, height, depth);
        model.color = color;
        return model;
    }

    /**
     * Factory method for a mesh model.
     */
    public static Model mesh(ContentID meshCid) {
        Model model = new Model();
        model.type = ModelType.MESH;
        model.meshCid = meshCid;
        return model;
    }

    /**
     * Factory method for a GLTF model.
     */
    public static Model gltf(ContentID gltfCid) {
        Model model = new Model();
        model.type = ModelType.GLTF;
        model.gltfCid = gltfCid;
        return model;
    }

    // ==================================================================================
    // Model Type
    // ==================================================================================

    /**
     * Type of 3D model representation.
     */
    public enum ModelType {
        /** Built from primitive shapes */
        PRIMITIVE,
        /** Custom mesh data */
        MESH,
        /** GLTF/GLB asset */
        GLTF,
        /** Procedurally generated */
        PROCEDURAL
    }

    // ==================================================================================
    // Primitive Shapes
    // ==================================================================================

    /**
     * A primitive 3D shape.
     */
    public sealed interface PrimitiveShape {

        static PrimitiveShape sphere(double radius) {
            return new Sphere(radius);
        }

        static PrimitiveShape box(double w, double h, double d) {
            return new Box(w, h, d);
        }

        static PrimitiveShape cylinder(double radius, double height) {
            return new Cylinder(radius, height);
        }

        static PrimitiveShape cone(double radius, double height) {
            return new Cone(radius, height);
        }

        static PrimitiveShape torus(double majorRadius, double minorRadius) {
            return new Torus(majorRadius, minorRadius);
        }

        // --- Implementations ---

        final class Sphere implements PrimitiveShape {
            private final double radius;
            public Sphere(double radius) { this.radius = radius; }
            public double radius() { return radius; }
        }

        final class Box implements PrimitiveShape {
            private final double width;
            private final double height;
            private final double depth;
            public Box(double width, double height, double depth) {
                this.width = width;
                this.height = height;
                this.depth = depth;
            }
            public double width() { return width; }
            public double height() { return height; }
            public double depth() { return depth; }
        }

        final class Cylinder implements PrimitiveShape {
            private final double radius;
            private final double height;
            public Cylinder(double radius, double height) {
                this.radius = radius;
                this.height = height;
            }
            public double radius() { return radius; }
            public double height() { return height; }
        }

        final class Cone implements PrimitiveShape {
            private final double radius;
            private final double height;
            public Cone(double radius, double height) {
                this.radius = radius;
                this.height = height;
            }
            public double radius() { return radius; }
            public double height() { return height; }
        }

        final class Torus implements PrimitiveShape {
            private final double majorRadius;
            private final double minorRadius;
            public Torus(double majorRadius, double minorRadius) {
                this.majorRadius = majorRadius;
                this.minorRadius = minorRadius;
            }
            public double majorRadius() { return majorRadius; }
            public double minorRadius() { return minorRadius; }
        }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public ModelType type() { return type; }
    public PrimitiveShape primitive() { return primitive; }
    public ContentID meshCid() { return meshCid; }
    public ContentID gltfCid() { return gltfCid; }
    public Space.Color color() { return color; }
    public ContentID textureCid() { return textureCid; }
    public double scale() { return scale; }

    // ==================================================================================
    // Mutations
    // ==================================================================================

    public void setType(ModelType type) { this.type = type; }
    public void setPrimitive(PrimitiveShape primitive) {
        this.type = ModelType.PRIMITIVE;
        this.primitive = primitive;
    }
    public void setMesh(ContentID meshCid) {
        this.type = ModelType.MESH;
        this.meshCid = meshCid;
    }
    public void setGltf(ContentID gltfCid) {
        this.type = ModelType.GLTF;
        this.gltfCid = gltfCid;
    }
    public void setColor(Space.Color color) { this.color = color; }
    public void setTexture(ContentID textureCid) { this.textureCid = textureCid; }
    public void setScale(double scale) { this.scale = scale; }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Check if this model has custom geometry (not just a primitive).
     */
    public boolean hasCustomGeometry() {
        return type == ModelType.MESH || type == ModelType.GLTF;
    }

    /**
     * Check if this model has a texture.
     */
    public boolean hasTexture() {
        return textureCid != null;
    }
}
