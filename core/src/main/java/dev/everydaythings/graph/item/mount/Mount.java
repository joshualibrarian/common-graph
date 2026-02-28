package dev.everydaythings.graph.item.mount;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Factory;

/**
 * A presentation descriptor — where/how a component appears.
 *
 * <p>Mounts are the answer to "where does this component show up?"
 * A component can have multiple mounts (like hard links in a filesystem),
 * and mounts of different types simultaneously.
 *
 * <p>Variants:
 * <ul>
 *   <li>{@link PathMount} — tree location (e.g., "/documents/notes")</li>
 *   <li>{@link SurfaceMount} — 2D surface region placement</li>
 *   <li>{@link SpatialMount} — 3D spatial position</li>
 * </ul>
 *
 * <p>CBOR format: discriminated array — first element is the mount kind string,
 * remaining elements are variant-specific.
 */
public sealed interface Mount extends Canonical
        permits Mount.PathMount, Mount.SurfaceMount, Mount.SpatialMount {

    /**
     * Deserialize a Mount from CBOR.
     *
     * <p>Format: ["path", "/documents/notes"] or ["surface", "sidebar", 0] etc.
     */
    @Factory
    static Mount fromCborTree(CBORObject node) {
        if (node == null || node.getType() != CBORType.Array || node.size() < 2) {
            return null;
        }

        String kind = node.get(0).AsString();
        return switch (kind) {
            case "path" -> new PathMount(node.get(1).AsString());
            case "surface" -> new SurfaceMount(node.get(1).AsString(), node.get(2).AsInt32());
            case "spatial" -> node.size() >= 8
                    ? new SpatialMount(
                            node.get(1).AsDouble(), node.get(2).AsDouble(), node.get(3).AsDouble(),
                            node.get(4).AsDouble(), node.get(5).AsDouble(), node.get(6).AsDouble(),
                            node.get(7).AsDouble())
                    : new SpatialMount(
                            node.get(1).AsDouble(), node.get(2).AsDouble(), node.get(3).AsDouble());
            default -> throw new IllegalArgumentException("Unknown Mount kind: " + kind);
        };
    }

    /**
     * Tree location mount — places a component in the item's path hierarchy.
     *
     * <p>The path provides structure (nesting) but NOT the display name.
     * The component's own type provides its visual identity.
     */
    @Canonical.Canonization
    record PathMount(String path) implements Mount {

        public PathMount {
            path = PathUtil.canonicalize(path);
        }

        /**
         * Get the parent path.
         *
         * @return parent path, or null if this is a root-level mount ("/")
         */
        public String parentPath() {
            if ("/".equals(path)) return null;
            int last = path.lastIndexOf('/');
            return last <= 0 ? "/" : path.substring(0, last);
        }

        /**
         * Get the depth of this mount ("/" = 0, "/foo" = 1, "/foo/bar" = 2).
         */
        public int depth() {
            return PathUtil.depth(path);
        }

        /**
         * Check if this mount is a direct child of the given parent path.
         */
        public boolean isChildOf(String parentPath) {
            String canonParent = PathUtil.canonicalize(parentPath);
            String thisParent = parentPath();
            return canonParent.equals(thisParent);
        }

        @Override
        public CBORObject toCborTree(Scope scope) {
            CBORObject arr = CBORObject.NewArray();
            arr.Add("path");
            arr.Add(path);
            return arr;
        }
    }

    /**
     * 2D surface region placement — places a component in a named UI region.
     *
     * <p>E.g., sidebar, header, footer, status bar.
     */
    @Canonical.Canonization
    record SurfaceMount(String region, int order) implements Mount {

        @Override
        public CBORObject toCborTree(Scope scope) {
            CBORObject arr = CBORObject.NewArray();
            arr.Add("surface");
            arr.Add(region);
            arr.Add(order);
            return arr;
        }
    }

    /**
     * 3D spatial placement — places a component at a position and rotation in space.
     *
     * <p>Position is in meters. Rotation is stored as a quaternion (qx, qy, qz, qw).
     *
     * <p>CBOR format: {@code ["spatial", x, y, z, qx, qy, qz, qw]}.
     * Backward compatible: 4-element arrays (position-only) assume identity rotation.
     */
    @Canonical.Canonization
    record SpatialMount(
            double x, double y, double z,
            double qx, double qy, double qz, double qw
    ) implements Mount {

        /**
         * Position-only convenience constructor (identity rotation).
         */
        public SpatialMount(double x, double y, double z) {
            this(x, y, z, 0, 0, 0, 1);
        }

        /**
         * Create from position and euler angles (degrees).
         *
         * <p>Y-up convention: yaw rotates around Y, pitch around X, roll around Z.
         * Applied in YXZ order (intrinsic rotations).
         */
        public static SpatialMount fromEuler(double x, double y, double z,
                                              double yaw, double pitch, double roll) {
            double cy = Math.cos(Math.toRadians(yaw) * 0.5);
            double sy = Math.sin(Math.toRadians(yaw) * 0.5);
            double cp = Math.cos(Math.toRadians(pitch) * 0.5);
            double sp = Math.sin(Math.toRadians(pitch) * 0.5);
            double cr = Math.cos(Math.toRadians(roll) * 0.5);
            double sr = Math.sin(Math.toRadians(roll) * 0.5);

            // YXZ rotation order: Q = Q_yaw * Q_pitch * Q_roll
            double qw = cy * cp * cr + sy * sp * sr;
            double qx = cy * sp * cr + sy * cp * sr;
            double qy = sy * cp * cr - cy * sp * sr;
            double qz = cy * cp * sr - sy * sp * cr;

            return new SpatialMount(x, y, z, qx, qy, qz, qw);
        }

        /**
         * Create from position and axis-angle rotation (degrees).
         *
         * <p>The axis (axisX, axisY, axisZ) is normalized internally.
         */
        public static SpatialMount fromAxisAngle(double x, double y, double z,
                                                  double axisX, double axisY, double axisZ,
                                                  double angle) {
            double len = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            if (len < 1e-10) {
                return new SpatialMount(x, y, z); // degenerate axis → identity
            }
            double nx = axisX / len, ny = axisY / len, nz = axisZ / len;
            double halfRad = Math.toRadians(angle) * 0.5;
            double s = Math.sin(halfRad);
            return new SpatialMount(x, y, z, nx * s, ny * s, nz * s, Math.cos(halfRad));
        }

        @Override
        public CBORObject toCborTree(Scope scope) {
            CBORObject arr = CBORObject.NewArray();
            arr.Add("spatial");
            arr.Add(x);
            arr.Add(y);
            arr.Add(z);
            arr.Add(qx);
            arr.Add(qy);
            arr.Add(qz);
            arr.Add(qw);
            return arr;
        }
    }
}
