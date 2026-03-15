package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface that layers content, showing one layer at a time.
 *
 * <p>StackSurface is for card stacks or any content that shows one of several
 * layers. Navigation between layers is handled by the accordion model
 * (expand/collapse) rather than tabs.
 *
 * <h2>Structure</h2>
 * <pre>
 * ┌─────────────────────────────────┐
 * │                                 │
 * │        active content           │  ← activeLayer().content
 * │                                 │
 * └─────────────────────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"stack"})
public class StackSurface extends SceneSchema {

    @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"stack-content"})
    static class Content {
        // Renders activeLayer().content
    }

    @Canon(order = 10)
    private List<Layer> layers = new ArrayList<>();

    @Canon(order = 11)
    private int activeIndex = 0;

    public StackSurface() {}

    public static StackSurface of(List<Layer> layers) {
        StackSurface surface = new StackSurface();
        surface.layers.addAll(layers);
        return surface;
    }

    public StackSurface addLayer(String label, SurfaceSchema content) {
        layers.add(new Layer(label, content));
        return this;
    }

    public StackSurface addLayer(Layer layer) {
        layers.add(layer);
        return this;
    }

    public StackSurface activeIndex(int index) {
        this.activeIndex = index;
        return this;
    }

    public List<Layer> layers() {
        return layers;
    }

    public int activeIndex() {
        return activeIndex;
    }

    public Layer activeLayer() {
        if (activeIndex >= 0 && activeIndex < layers.size()) {
            return layers.get(activeIndex);
        }
        return null;
    }

    /**
     * A layer in the stack.
     */
    @Getter @NoArgsConstructor
    public static class Layer implements dev.everydaythings.graph.Canonical {
        @Canon(order = 0) private String id;
        @Canon(order = 1) private String label;
        @Canon(order = 2) private ImageSurface icon;
        @Canon(order = 3) private SurfaceSchema content;

        public Layer(String label, SurfaceSchema content) {
            this.label = label;
            this.content = content;
        }

        public static Layer of(String label, SurfaceSchema content) {
            return new Layer(label, content);
        }

        public Layer id(String id) { this.id = id; return this; }
        public Layer label(String label) { this.label = label; return this; }
        public Layer icon(ImageSurface icon) { this.icon = icon; return this; }
        public Layer icon(String glyph) { this.icon = ImageSurface.of(glyph); return this; }
        public Layer content(SurfaceSchema content) { this.content = content; return this; }
    }

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        List<String> stackStyles = new ArrayList<>(style());
        stackStyles.add("stack");

        out.beginBox(Scene.Direction.VERTICAL, stackStyles);

        // Active content
        out.beginBox(Scene.Direction.VERTICAL, List.of("stack-content"));
        Layer active = activeLayer();
        if (active != null && active.content() != null) {
            active.content().render(out);
        }
        out.endBox();

        out.endBox();
    }
}
