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
 * <p>StackSurface can be used for tabbed interfaces, card stacks,
 * or any content that shows one of several options.
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌─────────────────────────────────┐
 * │ [tab1] [tab2] [tab3]           │  ← tabs (if showTabs)
 * ├─────────────────────────────────┤
 * │                                 │
 * │        active content           │  ← activeLayer().content
 * │                                 │
 * └─────────────────────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"stack"})
public class StackSurface extends SceneSchema {

    // ==================== Declarative Structure ====================
    // TODO: Enable once @Scene.Repeat is implemented

    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"tabs"})
    @Scene.If("showTabs")
    static class Tabs {
        // @Scene.Repeat(bind = "layers")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"tab"})
        @Scene.On(event = "click", action = "selectTab")
        static class Tab {
            @Scene.Image(bind = "icon")
            @Scene.If("icon != null")
            static class Icon {}

            @Scene.Text(bind = "label", style = {"tab-label"})
            static class Label {}
        }
    }

    @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"stack-content"})
    static class Content {
        // Renders activeLayer().content
    }

    @Canon(order = 10)
    private List<Layer> layers = new ArrayList<>();

    @Canon(order = 11)
    private int activeIndex = 0;

    @Canon(order = 12)
    private boolean showTabs = true;

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

    public StackSurface showTabs(boolean show) {
        this.showTabs = show;
        return this;
    }

    public List<Layer> layers() {
        return layers;
    }

    public int activeIndex() {
        return activeIndex;
    }

    public boolean showTabs() {
        return showTabs;
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

    // TODO: Remove procedural render() after @Scene.Repeat is implemented
    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        List<String> stackStyles = new ArrayList<>(style());
        stackStyles.add("stack");

        out.beginBox(Scene.Direction.VERTICAL, stackStyles);

        // Tab bar (if showing tabs)
        if (showTabs) {
            out.beginBox(Scene.Direction.HORIZONTAL, List.of("tabs"));
            for (int i = 0; i < layers.size(); i++) {
                Layer layer = layers.get(i);
                boolean isActive = (i == activeIndex);

                List<String> tabStyles = new ArrayList<>();
                tabStyles.add("tab");
                if (isActive) tabStyles.add("active");

                // Click to select this tab
                out.event("click", "selectTab", String.valueOf(i));

                out.beginBox(Scene.Direction.HORIZONTAL, tabStyles);
                if (layer.icon() != null) {
                    layer.icon().render(out);
                }
                if (layer.label() != null) {
                    out.text(layer.label(), List.of("tab-label"));
                }
                out.endBox();
            }
            out.endBox();
        }

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
