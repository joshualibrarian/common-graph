package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.id.ItemID;

import java.util.*;

/**
 * 3D environment where items exist in volumetric space.
 *
 * <p>Space is one of the four core container components:
 * <ul>
 *   <li><b>Roster</b> - principals present here</li>
 *   <li><b>Surface</b> - 2D layout of contents</li>
 *   <li><b>Space</b> - 3D environment for contents (this)</li>
 *   <li><b>Model</b> - 3D object representation (icon)</li>
 * </ul>
 *
 * <p>Containment itself is expressed through reference entries in the
 * ComponentTable, not a separate component.
 *
 * <p>A Space describes a 3D environment you can "enter":
 * <ul>
 *   <li>Items placed at 3D positions</li>
 *   <li>Environmental properties (lighting, background)</li>
 *   <li>Geometry (decoration, structure)</li>
 * </ul>
 *
 * <p>When you "enter" an item with a Space (in a 3D context),
 * you're inside the Space - you can walk around, see items
 * positioned in 3D, interact with them.
 *
 * <p>This is the 3D equivalent of Surface. Both describe
 * "what you see when inside an item" - Surface in 2D, Space in 3D.
 */
@Type(value = Space.KEY, glyph = "🌌")
public final class Space implements Component {

    public static final String KEY = "cg:type/space";

    /** Items placed in this space */
    private final Map<ItemID, Placement> placements;

    /** Environmental properties */
    private Environment environment;

    /**
     * Create an empty space with default environment.
     */
    public Space() {
        this.placements = new LinkedHashMap<>();
        this.environment = new Environment();
    }

    /**
     * Factory method to create an empty space.
     */
    public static Space create() {
        return new Space();
    }

    // ==================================================================================
    // 3D Primitives
    // ==================================================================================

    /**
     * A 3D vector (position, scale, etc).
     */
    public static class Vec3 {
        public static final Vec3 ZERO = new Vec3(0, 0, 0);
        public static final Vec3 ONE = new Vec3(1, 1, 1);

        private double x;
        private double y;
        private double z;

        public Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }

        public void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 scale(double factor) {
            return new Vec3(x * factor, y * factor, z * factor);
        }

        public double distanceTo(Vec3 other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * A quaternion rotation.
     */
    public static class Quaternion {
        public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

        private double x;
        private double y;
        private double z;
        private double w;

        public Quaternion(double x, double y, double z, double w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public double w() { return w; }

        public static Quaternion fromEuler(double pitch, double yaw, double roll) {
            double cy = Math.cos(yaw * 0.5);
            double sy = Math.sin(yaw * 0.5);
            double cp = Math.cos(pitch * 0.5);
            double sp = Math.sin(pitch * 0.5);
            double cr = Math.cos(roll * 0.5);
            double sr = Math.sin(roll * 0.5);

            return new Quaternion(
                    sr * cp * cy - cr * sp * sy,
                    cr * sp * cy + sr * cp * sy,
                    cr * cp * sy - sr * sp * cy,
                    cr * cp * cy + sr * sp * sy
            );
        }
    }

    /**
     * An RGBA color.
     */
    public static class Color {
        public static final Color WHITE = new Color(1, 1, 1, 1);
        public static final Color BLACK = new Color(0, 0, 0, 1);
        public static final Color GRAY = new Color(0.5, 0.5, 0.5, 1);

        private double r;
        private double g;
        private double b;
        private double a;

        public Color(double r, double g, double b, double a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        public double r() { return r; }
        public double g() { return g; }
        public double b() { return b; }
        public double a() { return a; }
    }

    // ==================================================================================
    // Placement
    // ==================================================================================

    /**
     * An item placed in this space.
     */
    public static class Placement {
        private final ItemID itemId;
        private Vec3 position;
        private Quaternion rotation;
        private Vec3 scale;

        public Placement(ItemID itemId) {
            this.itemId = itemId;
            this.position = Vec3.ZERO;
            this.rotation = Quaternion.IDENTITY;
            this.scale = Vec3.ONE;
        }

        public Placement(ItemID itemId, Vec3 position) {
            this.itemId = itemId;
            this.position = position;
            this.rotation = Quaternion.IDENTITY;
            this.scale = Vec3.ONE;
        }

        public ItemID itemId() { return itemId; }
        public Vec3 position() { return position; }
        public Quaternion rotation() { return rotation; }
        public Vec3 scale() { return scale; }

        public void setPosition(Vec3 position) { this.position = position; }
        public void setRotation(Quaternion rotation) { this.rotation = rotation; }
        public void setScale(Vec3 scale) { this.scale = scale; }
    }

    // ==================================================================================
    // Environment
    // ==================================================================================

    /**
     * Environmental properties of the space.
     */
    public static class Environment {
        private Color ambient;
        private Color background;
        private double fogNear;
        private double fogFar;
        private Color fogColor;

        public Environment() {
            this.ambient = new Color(0.3, 0.3, 0.3, 1.0);
            this.background = new Color(0.1, 0.1, 0.15, 1.0);
            this.fogNear = 100;
            this.fogFar = 1000;
            this.fogColor = null;  // No fog by default
        }

        public Color ambient() { return ambient; }
        public Color background() { return background; }
        public double fogNear() { return fogNear; }
        public double fogFar() { return fogFar; }
        public Color fogColor() { return fogColor; }

        public void setAmbient(Color ambient) { this.ambient = ambient; }
        public void setBackground(Color background) { this.background = background; }
        public void setFog(Color color, double near, double far) {
            this.fogColor = color;
            this.fogNear = near;
            this.fogFar = far;
        }
        public void clearFog() { this.fogColor = null; }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /**
     * Get all placements.
     */
    public Collection<Placement> placements() {
        return Collections.unmodifiableCollection(placements.values());
    }

    /**
     * Get placement for a specific item.
     */
    public Optional<Placement> getPlacement(ItemID itemId) {
        return Optional.ofNullable(placements.get(itemId));
    }

    /**
     * Get the environment.
     */
    public Environment environment() {
        return environment;
    }

    /**
     * Check if an item is placed in this space.
     */
    public boolean contains(ItemID itemId) {
        return placements.containsKey(itemId);
    }

    /**
     * Get the number of placed items.
     */
    public int size() {
        return placements.size();
    }

    // ==================================================================================
    // Mutations
    // ==================================================================================

    /**
     * Place an item at a position.
     */
    public Placement place(ItemID itemId, Vec3 position) {
        Placement placement = new Placement(itemId, position);
        placements.put(itemId, placement);
        return placement;
    }

    /**
     * Place an item at origin.
     */
    public Placement place(ItemID itemId) {
        return place(itemId, Vec3.ZERO);
    }

    /**
     * Remove an item from the space.
     */
    public boolean remove(ItemID itemId) {
        return placements.remove(itemId) != null;
    }

    /**
     * Move an item to a new position.
     */
    public boolean moveTo(ItemID itemId, Vec3 position) {
        Placement placement = placements.get(itemId);
        if (placement == null) {
            return false;
        }
        placement.setPosition(position);
        return true;
    }

    /**
     * Set the environment.
     */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * Clear all placements.
     */
    public void clear() {
        placements.clear();
    }

    // ==================================================================================
    // Query
    // ==================================================================================

    /**
     * Find items within a radius of a point.
     */
    public List<ItemID> itemsNear(Vec3 center, double radius) {
        return placements.values().stream()
                .filter(p -> p.position().distanceTo(center) <= radius)
                .map(Placement::itemId)
                .toList();
    }
}
