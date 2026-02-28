package dev.everydaythings.graph.ui.scene.surface.select;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Dropdown/select surface - compact single-selection.
 *
 * <p>Renders as a collapsed dropdown that expands on click.
 * Shows the currently selected option with a dropdown indicator.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // From model (preferred)
 * SelectionModel model = SelectionModel.of(
 *     Option.of("a", "Option A"),
 *     Option.of("b", "Option B")
 * ).select("a");
 * DropdownSurface surface = model.toDropdown();
 * }</pre>
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌─────────────────────────┐
 * │ [selected value]    ▼  │  ← click toggles open
 * ├─────────────────────────┤
 * │ [icon] Option A        │  ← visible when open
 * │ [icon] Option B ✓      │
 * │ [icon] Option C        │
 * └─────────────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"dropdown"})
@Scene.State(when = "open", style = {"open"})
@Scene.State(when = "!enabled", style = {"disabled"})
public class DropdownSurface extends ContainerSurface {

    // ==================== Declarative Structure ====================

    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"dropdown-header"})
    @Scene.On(event = "click", action = "toggleOpen")
    static class Header {
        @Scene.Text(bind = "selectedLabel", style = {"dropdown-value"})
        @Scene.If("selectedLabel != null")
        static class SelectedValue {}

        @Scene.Text(bind = "placeholder", style = {"dropdown-value", "placeholder"})
        @Scene.If("selectedLabel == null")
        static class PlaceholderValue {}

        @Scene.Image(alt = "▲", size = "small", style = {"dropdown-indicator"})
        @Scene.If("open")
        static class IndicatorOpen {}

        @Scene.Image(alt = "▼", size = "small", style = {"dropdown-indicator"})
        @Scene.If("!open")
        static class IndicatorClosed {}
    }

    @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"dropdown-options"})
    @Scene.If("open")
    static class Options {
        @Scene.Repeat(bind = "options")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"dropdown-option"})
        @Scene.State(when = "$item.selected", style = {"selected"})
        @Scene.On(event = "click", action = "select", target = "$item.id")
        static class OptionTemplate {
            @Scene.Image(bind = "$item.icon", size = "small", style = {"dropdown-option-icon"})
            @Scene.If("$item.icon != null")
            static class Icon {}

            @Scene.Text(bind = "$item.label", style = {"dropdown-option-label"})
            static class Label {}
        }
    }

    /**
     * The dropdown options.
     */
    @Canon(order = 20)
    private List<DropdownOption> options = new ArrayList<>();

    /**
     * ID of the selected option.
     */
    @Canon(order = 21)
    private String selectedId;

    /**
     * Label of the selected option (for display).
     */
    @Canon(order = 22)
    private String selectedLabel;

    /**
     * Optional placeholder when nothing selected.
     */
    @Canon(order = 23)
    private String placeholder = "Select...";

    /**
     * Whether the dropdown is currently open.
     */
    @Canon(order = 24)
    private boolean open = false;

    /**
     * Whether the dropdown is enabled.
     */
    @Canon(order = 25)
    private boolean enabled = true;

    /**
     * A dropdown option.
     */
    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class DropdownOption {
        @Canon(order = 0) private String id;
        @Canon(order = 1) private String label;
        @Canon(order = 2) private String icon;
        @Canon(order = 3) private boolean selected;
    }

    public DropdownSurface() {
        this.direction = Scene.Direction.VERTICAL;
    }

    // ==================== Factory Methods ====================

    /**
     * Create from a SelectionModel.
     */
    public static <T> DropdownSurface of(SelectionModel<T> model) {
        DropdownSurface s = new DropdownSurface();
        s.enabled = model.enabled();
        if (model.id() != null) {
            s.id(model.id());
        }

        SelectionModel.Option<T> selected = model.selectedOption();
        if (selected != null) {
            s.selectedId = selected.id();
            s.selectedLabel = selected.label();
        }

        for (SelectionModel.Option<T> opt : model.options()) {
            String iconGlyph = opt.icon() != null ? opt.icon().alt() : null;
            s.options.add(new DropdownOption(
                    opt.id(),
                    opt.label(),
                    iconGlyph,
                    model.isSelected(opt.id())
            ));
        }
        return s;
    }

    // ==================== Fluent Setters ====================

    public DropdownSurface placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public DropdownSurface open(boolean open) {
        this.open = open;
        return this;
    }

    public DropdownSurface enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    // ==================== Getters ====================

    public List<DropdownOption> options() { return options; }
    public String selectedId() { return selectedId; }
    public String selectedLabel() { return selectedLabel; }
    public String placeholder() { return placeholder; }
    public boolean open() { return open; }
    public boolean enabled() { return enabled; }

    // Render is now handled declaratively via @Surface.Container + @Surface.Repeat
}
