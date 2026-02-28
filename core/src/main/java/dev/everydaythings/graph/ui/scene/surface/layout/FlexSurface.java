package dev.everydaythings.graph.ui.scene.surface.layout;

import dev.everydaythings.graph.ui.scene.Scene;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Container surface that positions children using flexbox-style layout.
 *
 * <p>FlexSurface arranges children in a row or column, with flexible
 * sizing based on grow/shrink factors. Similar to CSS Flexbox.
 *
 * <h2>Usage</h2>
 * <pre>
 * {@literal @}Scene(as = FlexSurface.class)
 * {@literal @}Scene.FlexContainer(direction = Direction.VERTICAL)
 * public class MyModel {
 *
 *     {@literal @}Scene
 *     {@literal @}Scene.Flex(size = "fit")
 *     HeaderModel header;
 *
 *     {@literal @}Scene
 *     {@literal @}Scene.Flex(size = "fill")
 *     BodyModel body;
 *
 *     {@literal @}Scene
 *     {@literal @}Scene.Flex(size = "fit")
 *     FooterModel footer;
 * }
 * </pre>
 *
 * @see Scene.FlexContainer
 * @see Scene.Flex
 */
public class FlexSurface extends SurfaceSchema {

    // ===== Instance Fields =====

    @Canon(order = 10)
    private Scene.Direction direction = Scene.Direction.VERTICAL;

    @Canon(order = 11)
    private String justify;

    @Canon(order = 12)
    private String align;

    @Canon(order = 13)
    private String gap;

    @Canon(order = 14)
    private boolean wrap;

    @Canon(order = 15)
    private List<FlexChild> children = new ArrayList<>();

    public FlexSurface() {}

    public FlexSurface(Scene.Direction direction) {
        this.direction = direction;
    }

    // ===== Factory Methods =====

    public static FlexSurface row() {
        return new FlexSurface(Scene.Direction.HORIZONTAL);
    }

    public static FlexSurface column() {
        return new FlexSurface(Scene.Direction.VERTICAL);
    }

    // ===== Configuration =====

    /**
     * Configure container from @Scene.FlexContainer annotation.
     */
    public FlexSurface container(Scene.FlexContainer config) {
        this.direction = Scene.Direction.valueOf(config.direction().name());
        this.justify = config.justify();
        this.align = config.align();
        this.gap = config.gap();
        this.wrap = config.wrap();
        return this;
    }

    public FlexSurface direction(Scene.Direction direction) {
        this.direction = direction;
        return this;
    }

    public FlexSurface justify(String justify) {
        this.justify = justify;
        return this;
    }

    public FlexSurface align(String align) {
        this.align = align;
        return this;
    }

    public FlexSurface gap(String gap) {
        this.gap = gap;
        return this;
    }

    public FlexSurface wrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    // ===== Child Management =====

    /**
     * Add a child with @Scene.Flex sizing.
     */
    public FlexSurface add(SurfaceSchema child, Scene.Flex flex) {
        children.add(new FlexChild(child, flex));
        return this;
    }

    public FlexSurface add(SurfaceSchema child, String size) {
        children.add(new FlexChild(child, size));
        return this;
    }

    public FlexSurface add(SurfaceSchema child) {
        children.add(new FlexChild(child, ""));
        return this;
    }

    public List<FlexChild> children() {
        return children;
    }

    // ===== Rendering =====

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        out.type("FlexSurface");

        List<String> styles = new ArrayList<>(style());
        styles.add("flex-container");
        styles.add(direction == Scene.Direction.HORIZONTAL ? "flex-row" : "flex-column");

        out.beginBox(direction, styles);

        for (FlexChild child : children) {
            List<String> childStyles = new ArrayList<>();
            childStyles.add("flex-child");
            if ("fit".equals(child.size())) {
                childStyles.add("flex-fit");
            } else if ("fill".equals(child.size())) {
                childStyles.add("flex-fill");
            }

            out.beginBox(Scene.Direction.VERTICAL, childStyles);
            if (child.surface() != null) {
                child.surface().render(out);
            }
            out.endBox();
        }

        out.endBox();
    }

    // ===== Supporting Types =====

    public record FlexChild(
            SurfaceSchema surface,
            String size,
            int grow,
            int shrink,
            String basis,
            int order,
            String alignSelf
    ) {
        public FlexChild(SurfaceSchema surface, Scene.Flex flex) {
            this(surface, flex.size(), flex.grow(), flex.shrink(),
                 flex.basis(), flex.order(), flex.alignSelf());
        }

        public FlexChild(SurfaceSchema surface, String size) {
            this(surface, size, -1, -1, "", 0, "");
        }
    }
}
