package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface that divides space into multiple regions.
 *
 * <p>SplitSurface creates a layout with named regions, each with
 * its own size constraints. Regions can contain other surfaces.
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌──────────┬──────────┬──────────┐
 * │ region1  │ region2  │ region3  │   (horizontal)
 * └──────────┴──────────┴──────────┘
 * </pre>
 */
@Scene.Container(style = {"split"})
public class SplitSurface extends SurfaceSchema {

    // ==================== Declarative Structure ====================
    // TODO: Enable once dynamic direction binding is supported

    @Scene.Repeat(bind = "regions")
    @Scene.Container(style = {"region"})
    static class RegionTemplate {
        @Scene.Embed(bind = "$item.content")
        @Scene.If("$item.content != null")
        static class Content {}
    }

    @Canon(order = 10)
    private Scene.Direction direction = Scene.Direction.HORIZONTAL;

    @Canon(order = 11)
    private List<Region> regions = new ArrayList<>();

    public SplitSurface() {}

    public SplitSurface(Scene.Direction direction) {
        this.direction = direction;
    }

    public static SplitSurface horizontal() {
        return new SplitSurface(Scene.Direction.HORIZONTAL);
    }

    public static SplitSurface vertical() {
        return new SplitSurface(Scene.Direction.VERTICAL);
    }

    public SplitSurface addRegion(String name, String size, SurfaceSchema content) {
        regions.add(new Region(name, size, content));
        return this;
    }

    public SplitSurface addRegion(Region region) {
        regions.add(region);
        return this;
    }

    public SplitSurface direction(Scene.Direction direction) {
        this.direction = direction;
        return this;
    }

    public Scene.Direction direction() {
        return direction;
    }

    public List<Region> regions() {
        return regions;
    }

    /**
     * A region within the split surface.
     */
    @Getter @NoArgsConstructor
    public static class Region implements dev.everydaythings.graph.Canonical {
        @Canon(order = 0) private String name;
        @Canon(order = 1) private String size = "1fr";
        @Canon(order = 2) private String minSize;
        @Canon(order = 3) private String maxSize;
        @Canon(order = 4) private SurfaceSchema content;

        public Region(String name, String size, SurfaceSchema content) {
            this.name = name;
            this.size = size;
            this.content = content;
        }

        public static Region of(String name, String size) {
            Region r = new Region();
            r.name = name;
            r.size = size;
            return r;
        }

        public Region name(String name) { this.name = name; return this; }
        public Region size(String size) { this.size = size; return this; }
        public Region minSize(String minSize) { this.minSize = minSize; return this; }
        public Region maxSize(String maxSize) { this.maxSize = maxSize; return this; }
        public Region content(SurfaceSchema content) { this.content = content; return this; }
    }

    // TODO: Remove procedural render() after @Surface.Repeat is implemented
    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        List<String> splitStyles = new ArrayList<>(style());
        splitStyles.add("split");

        out.beginBox(direction, splitStyles);

        for (Region region : regions) {
            List<String> regionStyles = new ArrayList<>();
            regionStyles.add("region");
            if (region.name() != null) {
                regionStyles.add("region-" + region.name());
            }
            // Size is conveyed via style - renderer can interpret
            if (region.size() != null) {
                regionStyles.add("size-" + region.size().replace(" ", "-"));
            }

            out.beginBox(Scene.Direction.VERTICAL, regionStyles);
            if (region.content() != null) {
                region.content().render(out);
            }
            out.endBox();
        }

        out.endBox();
    }
}
