package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Window — the runtime mirror of an {@code ITEM_VIEW} frame.
 *
 * <p>Each Window represents one open view of one item on a session's
 * {@link Surface}.  The session's collection of {@code ITEM_VIEW} frames
 * is the declarative source of truth; the Surface's list of Windows is the
 * runtime cache that enacts those frames as on-screen real estate.
 *
 * <h2>State (mirrors the frame's bindings)</h2>
 * <ul>
 *   <li>{@link #itemRef()} — which item this window views ({@code THEME}).</li>
 *   <li>{@link #position()} — anchor point of the handle in the surface's
 *       coordinate space (0,0 = top-left).</li>
 *   <li>{@link #expanded()} — when true, the window also draws the item's
 *       content scene below the handle; when false, only the handle is
 *       shown.</li>
 *   <li>{@link #size()} — expanded box dimensions; ignored when collapsed.</li>
 *   <li>{@link #sceneSupplier()} — non-blocking source of the item's current
 *       scene snapshot (the body to render below the handle when expanded).
 *       The handle itself is derived elsewhere (typically from the item's
 *       archetype's default presentation).</li>
 * </ul>
 *
 * <h2>What lives where</h2>
 * <ul>
 *   <li>The <b>data</b> (which items are open, where, how big) lives in
 *       {@code ITEM_VIEW} frames on the session item.  Survives restart.</li>
 *   <li>The <b>runtime</b> instance is this class.  Built when the Surface
 *       enumerates frames at startup or reacts to frame changes.</li>
 *   <li>The <b>scene</b> the window paints is derived from the item itself
 *       (its archetype's presentation, with per-instance overrides).</li>
 * </ul>
 *
 * <p>For slice 1 (single-Window TUI, no frame-driven construction yet),
 * Windows are constructed directly by the session.  Slice 2 wires
 * {@code ITEM_VIEW} frames as the source.
 *
 * <p>Window is mutable: position, expanded, and size can change at runtime
 * as the underlying frame is updated.  Mutation is single-threaded by the
 * surface that holds it.  The scene supplier is immutable per window.
 */
public final class Window {

    private final ItemRef itemRef;
    private final Supplier<Body> sceneSupplier;

    private Position position;
    private boolean expanded;
    private Size size;

    public Window(ItemRef itemRef, Supplier<Body> sceneSupplier) {
        this(itemRef, sceneSupplier, Position.ORIGIN, true, Size.UNBOUNDED);
    }

    public Window(ItemRef itemRef,
                  Supplier<Body> sceneSupplier,
                  Position position,
                  boolean expanded,
                  Size size) {
        this.itemRef       = Objects.requireNonNull(itemRef, "itemRef");
        this.sceneSupplier = Objects.requireNonNull(sceneSupplier, "sceneSupplier");
        this.position      = Objects.requireNonNull(position, "position");
        this.expanded      = expanded;
        this.size          = Objects.requireNonNull(size, "size");
    }

    public ItemRef itemRef()              { return itemRef; }
    public Supplier<Body> sceneSupplier() { return sceneSupplier; }

    public Position position()  { return position; }
    public boolean  expanded()  { return expanded; }
    public Size     size()      { return size; }

    public void position(Position p) { this.position = Objects.requireNonNull(p); }
    public void expanded(boolean e)  { this.expanded = e; }
    public void size(Size s)         { this.size = Objects.requireNonNull(s); }

    /**
     * 2D position in the surface's coordinate space.  Units are surface-
     * specific: cells for TUI, pixels for desktop painters.  Origin is
     * top-left; positive Y goes down (screen convention).
     */
    public static final class Position {
        public static final Position ORIGIN = new Position(0, 0);

        private final int x;
        private final int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() { return x; }
        public int y() { return y; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Position other)) return false;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y); }

        @Override
        public String toString() { return "(" + x + "," + y + ")"; }
    }

    /**
     * Expanded-window dimensions.  Use {@link #UNBOUNDED} to mean "fill
     * the available surface" — surfaces are free to interpret that as
     * "the whole terminal" / "the whole overlay" / etc.
     */
    public static final class Size {
        public static final Size UNBOUNDED = new Size(-1, -1);

        private final int width;
        private final int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width()  { return width; }
        public int height() { return height; }
        public boolean isUnbounded() { return width < 0 || height < 0; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Size other)) return false;
            return width == other.width && height == other.height;
        }

        @Override
        public int hashCode() { return Objects.hash(width, height); }

        @Override
        public String toString() {
            return isUnbounded() ? "Size.UNBOUNDED" : width + "x" + height;
        }
    }
}
