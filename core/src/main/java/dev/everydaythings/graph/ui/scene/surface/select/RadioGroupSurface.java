package dev.everydaythings.graph.ui.scene.surface.select;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Radio button group surface - visual representation of single-selection.
 *
 * <p>Renders as a group of radio buttons (○ unselected, ◉ selected).
 * Only one option can be selected at a time.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // From model (preferred)
 * SelectionModel model = SelectionModel.of(
 *     Option.of("a", "Option A"),
 *     Option.of("b", "Option B")
 * ).select("a");
 * RadioGroupSurface surface = model.toRadioGroup();
 * }</pre>
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * Group Label
 * ◉ Option A (selected)
 * ○ Option B
 * ○ Option C
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"radio-group"})
@Scene.State(when = "!enabled", style = {"disabled"})
public class RadioGroupSurface extends ContainerSurface {

    // ==================== Declarative Structure ====================

    @Scene.Text(bind = "label", style = {"radio-group-label"})
    @Scene.If("label != null")
    static class GroupLabel {}

    @Scene.Repeat(bind = "options")
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"radio-option"})
    @Scene.State(when = "$item.selected", style = {"selected"})
    @Scene.On(event = "click", action = "select", target = "$item.id")
    static class OptionTemplate {
        @Scene.Image(alt = "◉", size = "small", style = {"radio-indicator", "selected"})
        @Scene.If("$item.selected")
        static class IndicatorSelected {}

        @Scene.Image(alt = "○", size = "small", style = {"radio-indicator"})
        @Scene.If("!$item.selected")
        static class IndicatorUnselected {}

        @Scene.Image(bind = "$item.icon", size = "small", style = {"radio-icon"})
        @Scene.If("$item.icon != null")
        static class Icon {}

        @Scene.Text(bind = "$item.label", style = {"radio-label"})
        static class Label {}
    }

    /**
     * The radio options.
     */
    @Canon(order = 20)
    private List<RadioOption> options = new ArrayList<>();

    /**
     * Optional group label.
     */
    @Canon(order = 21)
    private String label;

    /**
     * Whether the group is enabled.
     */
    @Canon(order = 22)
    private boolean enabled = true;

    /**
     * A single radio option.
     */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class RadioOption {
        @Canon(order = 0) private String id;
        @Canon(order = 1) private String label;
        @Canon(order = 2) private String icon;
        @Canon(order = 3) private boolean selected;
    }

    public RadioGroupSurface() {
        this.direction = Scene.Direction.VERTICAL;
    }

    // ==================== Factory Methods ====================

    /**
     * Create from a SelectionModel.
     */
    public static <T> RadioGroupSurface of(SelectionModel<T> model) {
        RadioGroupSurface s = new RadioGroupSurface();
        s.label = model.label();
        s.enabled = model.enabled();
        if (model.id() != null) {
            s.id(model.id());
        }

        for (SelectionModel.Option<T> opt : model.options()) {
            String iconGlyph = opt.icon() != null ? opt.icon().alt() : null;
            s.options.add(new RadioOption(
                    opt.id(),
                    opt.label(),
                    iconGlyph,
                    model.isSelected(opt.id())
            ));
        }
        return s;
    }

    // ==================== Fluent Setters ====================

    public RadioGroupSurface label(String label) {
        this.label = label;
        return this;
    }

    public RadioGroupSurface enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public RadioGroupSurface addOption(String id, String label, boolean selected) {
        this.options.add(new RadioOption(id, label, null, selected));
        return this;
    }

    // ==================== Getters ====================

    public List<RadioOption> options() { return options; }
    public String label() { return label; }
    public boolean enabled() { return enabled; }

    // Render is now handled declaratively via @Scene.Container + @Scene.Repeat
}
