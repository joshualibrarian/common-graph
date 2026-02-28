package dev.everydaythings.graph.ui.scene.surface.layout;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Container surface that positions children using constraints.
 *
 * <p>ConstraintSurface uses edge-based positioning similar to iOS Auto Layout
 * or Android ConstraintLayout. Children declare which edges they attach to,
 * either absolutely or relative to other elements.
 *
 * <h2>Constraint Syntax</h2>
 * <ul>
 *   <li>{@code "0"} - Absolute position from edge</li>
 *   <li>{@code "100%"} - Percentage of container</li>
 *   <li>{@code "header.bottom"} - Relative to another element's edge</li>
 *   <li>{@code "fit"} - Size to content</li>
 *   <li>{@code "fill"} - Fill available space</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * {@literal @}Surface(as = ConstraintSurface.class)
 * public class MyModel {
 *
 *     {@literal @}Surface(id = "header")
 *     {@literal @}Scene.Constraint(top = "0", height = "fit")
 *     HeaderModel header;
 *
 *     {@literal @}Surface(id = "body")
 *     {@literal @}Scene.Constraint(topTo = "header.bottom", bottomTo = "footer.top")
 *     BodyModel body;
 *
 *     {@literal @}Surface(id = "footer")
 *     {@literal @}Scene.Constraint(bottom = "0", height = "fit")
 *     FooterModel footer;
 * }
 * </pre>
 *
 * @see Scene.Constraint
 */
public class ConstraintSurface extends SurfaceSchema {

    // ===== Instance Fields =====

    @Canon(order = 10)
    private List<ConstrainedChild> children = new ArrayList<>();

    public ConstraintSurface() {}

    // ===== Child Management =====

    /**
     * Add a child with constraint values.
     */
    public ConstraintSurface add(String id, SurfaceSchema child, ConstraintValues constraints) {
        children.add(new ConstrainedChild(id, child, constraints, null));
        return this;
    }

    /**
     * Add a child with constraint values and a border.
     */
    public ConstraintSurface add(String id, SurfaceSchema child, ConstraintValues constraints, BoxBorder border) {
        children.add(new ConstrainedChild(id, child, constraints, border));
        return this;
    }

    public List<ConstrainedChild> children() {
        return children;
    }

    // ===== Rendering =====

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        out.type("ConstraintSurface");

        // Outer container — layout engine gives root node the full available space
        // Pass own border/background if set (supports outer border around the whole layout)
        BoxBorder outerBorder = boxBorder() != null && boxBorder().isVisible() ? boxBorder() : null;
        String outerPadding = outerBorder != null ? "6" : "0";
        out.beginBox(Scene.Direction.VERTICAL, List.of("constraint-container", "fill"),
                outerBorder, boxBackground(), null, null, outerPadding);
        // Gap between children — makes individual panel borders visible with space between them
        if (outerBorder != null) {
            out.gap("4");
        }

        // Sort children: top-pinned first, then middle (fill), then bottom-pinned
        List<ConstrainedChild> topChildren = new ArrayList<>();
        List<ConstrainedChild> middleChildren = new ArrayList<>();
        List<ConstrainedChild> bottomChildren = new ArrayList<>();

        for (ConstrainedChild child : children) {
            if (child.constraints() != null && isBottomPinned(child.constraints())) {
                bottomChildren.add(child);
            } else if (child.constraints() != null && isTopPinned(child.constraints())) {
                // If only top-pinned (no bottomTo), it's a header
                if (child.constraints().bottomTo().isEmpty() && child.constraints().bottom().isEmpty()) {
                    topChildren.add(child);
                } else {
                    // Has both top and bottom constraints — it's a middle/fill element
                    middleChildren.add(child);
                }
            } else {
                middleChildren.add(child);
            }
        }

        // Emit top-pinned children (fit content)
        for (ConstrainedChild child : topChildren) {
            renderChild(child, List.of("constrained", "top"), out);
        }

        // Emit middle children (fill remaining space)
        // Check if middle children need horizontal layout (side-by-side panes)
        boolean horizontalMiddle = middleChildren.size() > 1 && middleChildren.stream()
                .anyMatch(c -> c.constraints() != null && hasHorizontalConstraint(c.constraints()));

        if (horizontalMiddle) {
            // Wrap in a horizontal container for side-by-side layout
            out.beginBox(Scene.Direction.HORIZONTAL, List.of("constrained", "fill"),
                    null, null, null, null, "0");
            if (outerBorder != null) {
                out.gap("4");
            }
            for (ConstrainedChild child : middleChildren) {
                String width = child.constraints() != null ? child.constraints().width() : "";
                String maxWidth = child.constraints() != null ? child.constraints().maxWidth() : "";
                List<String> styles = new ArrayList<>(List.of("constrained"));
                if (width.isEmpty()) {
                    styles.add("fill");
                }
                if (child.id() != null && !child.id().isEmpty()) {
                    styles.add("id-" + child.id());
                }
                out.id(child.id());
                if (maxWidth != null && !maxWidth.isEmpty()) {
                    out.maxWidth(maxWidth);
                }
                // Emit overflow for constrained children that declare it
                String childOverflow = child.constraints() != null ? child.constraints().overflow() : "visible";
                if (childOverflow != null && !"visible".equals(childOverflow)) {
                    out.overflow(childOverflow);
                }
                BoxBorder border = child.border() != null && child.border().isVisible() ? child.border() : null;
                out.beginBox(Scene.Direction.VERTICAL, styles,
                        border, null, width.isEmpty() ? null : width, null, "0.25em 0.5em");
                if (child.surface() != null) {
                    child.surface().render(out);
                }
                out.endBox();
            }
            out.endBox();
        } else {
            for (ConstrainedChild child : middleChildren) {
                renderChild(child, List.of("constrained", "fill"), out);
            }
        }

        // Emit bottom-pinned children (fit content)
        for (ConstrainedChild child : bottomChildren) {
            renderChild(child, List.of("constrained", "bottom"), out);
        }

        out.endBox();
    }

    private void renderChild(ConstrainedChild child, List<String> baseStyles, SurfaceRenderer out) {
        List<String> styles = new ArrayList<>(baseStyles);
        if (child.id() != null && !child.id().isEmpty()) {
            styles.add("id-" + child.id());
        }
        out.id(child.id());
        // Emit overflow for constrained children that declare it
        String overflow = child.constraints() != null ? child.constraints().overflow() : "visible";
        if (overflow != null && !"visible".equals(overflow)) {
            out.overflow(overflow);
        }
        BoxBorder border = child.border() != null && child.border().isVisible() ? child.border() : null;
        out.beginBox(Scene.Direction.VERTICAL, styles,
                border, null, null, null, "0.25em 0.5em");
        if (child.surface() != null) {
            child.surface().render(out);
        }
        out.endBox();
    }

    private static boolean isBottomPinned(ConstraintValues c) {
        return !c.bottom().isEmpty() && c.top().isEmpty() && c.topTo().isEmpty();
    }

    private static boolean isTopPinned(ConstraintValues c) {
        return !c.top().isEmpty() || !c.topTo().isEmpty();
    }

    private static boolean hasHorizontalConstraint(ConstraintValues c) {
        return !c.leftTo().isEmpty() || !c.rightTo().isEmpty();
    }

    // ===== Supporting Types =====

    /**
     * A child with its constraints.
     */
    public record ConstrainedChild(
            String id,
            SurfaceSchema surface,
            ConstraintValues constraints,
            BoxBorder border
    ) {}

    /**
     * Parsed constraint values.
     */
    public record ConstraintValues(
            String top, String bottom, String left, String right,
            String topTo, String bottomTo, String leftTo, String rightTo,
            String width, String height,
            String minWidth, String minHeight, String maxWidth, String maxHeight,
            String alignX, String alignY,
            int zIndex,
            String overflow
    ) {
        /**
         * Create from @Scene.Constraint annotation.
         */
        public static ConstraintValues from(Scene.Constraint constraint) {
            return new ConstraintValues(
                    constraint.top(), constraint.bottom(), constraint.left(), constraint.right(),
                    constraint.topTo(), constraint.bottomTo(), constraint.leftTo(), constraint.rightTo(),
                    constraint.width(), constraint.height(),
                    constraint.minWidth(), constraint.minHeight(), constraint.maxWidth(), constraint.maxHeight(),
                    constraint.alignX(), constraint.alignY(),
                    constraint.zIndex(),
                    constraint.overflow()
            );
        }

        /**
         * Create from @Scene.Place annotation.
         */
        public static ConstraintValues from(Scene.Place place) {
            return new ConstraintValues(
                    place.top(), place.bottom(), place.left(), place.right(),
                    place.topTo(), place.bottomTo(), place.leftTo(), place.rightTo(),
                    place.width(), place.height(),
                    place.minWidth(), place.minHeight(), place.maxWidth(), place.maxHeight(),
                    place.alignX(), place.alignY(),
                    place.zIndex(),
                    place.overflow()
            );
        }

    }
}
