package dev.everydaythings.graph.ui.scene.surface.select;

import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.scene.SceneModel;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model for single or multi-selection from a list of options.
 *
 * <p>SelectionModel holds a list of options and tracks which are selected.
 * It can be rendered as different surface types:
 * <ul>
 *   <li>{@link RadioGroupSurface} - Radio buttons (single select, all visible)</li>
 *   <li>{@link DropdownSurface} - Dropdown/select (single select, collapsed)</li>
 * </ul>
 *
 * <p>For multi-select, use checkbox groups instead.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create with options
 * SelectionModel model = SelectionModel.of(
 *     Option.of("sm", "Small"),
 *     Option.of("md", "Medium"),
 *     Option.of("lg", "Large")
 * ).select("md");
 *
 * model.onChange(this::render);
 *
 * // Render as radio group
 * RadioGroupSurface surface = model.toRadioGroup();
 *
 * // Or as dropdown
 * DropdownSurface surface = model.toDropdown();
 * }</pre>
 *
 * @param <T> The value type for options (often String)
 */
public class SelectionModel<T> extends SceneModel<SurfaceSchema> {

    /** The available options. */
    private final List<Option<T>> options = new ArrayList<>();

    /** The selected option ID (for single-select). */
    private String selectedId;

    /** Selected option IDs (for multi-select). */
    private final Set<String> selectedIds = new HashSet<>();

    /** Whether multiple selection is allowed. */
    private boolean multiSelect = false;

    /** Optional label for the group. */
    private String label;

    /** Optional ID for the control. */
    private String id;

    /** Whether the control is enabled. */
    private boolean enabled = true;

    // ==================== Option Class ====================

    /**
     * An option in the selection.
     */
    @Getter @AllArgsConstructor
    public static class Option<T> {
        private final String id;
        private final String label;
        private final ImageSurface icon;
        private final T value;

        public static <T> Option<T> of(String id, String label) {
            return new Option<>(id, label, null, null);
        }

        public static <T> Option<T> of(String id, String label, String icon) {
            return new Option<>(id, label, ImageSurface.of(icon), null);
        }

        public static <T> Option<T> of(String id, String label, T value) {
            return new Option<>(id, label, null, value);
        }

        public static <T> Option<T> of(String id, String label, String icon, T value) {
            return new Option<>(id, label, ImageSurface.of(icon), value);
        }
    }

    // ==================== Construction ====================

    public SelectionModel() {}

    @SafeVarargs
    public static <T> SelectionModel<T> of(Option<T>... options) {
        SelectionModel<T> model = new SelectionModel<>();
        for (Option<T> opt : options) {
            model.options.add(opt);
        }
        return model;
    }

    public static SelectionModel<String> ofStrings(String... labels) {
        SelectionModel<String> model = new SelectionModel<>();
        for (String label : labels) {
            model.options.add(Option.of(label, label, label));
        }
        return model;
    }

    // ==================== Fluent Configuration ====================

    public SelectionModel<T> add(Option<T> option) {
        this.options.add(option);
        return this;
    }

    public SelectionModel<T> add(String id, String label) {
        this.options.add(Option.of(id, label));
        return this;
    }

    public SelectionModel<T> label(String label) {
        this.label = label;
        return this;
    }

    public SelectionModel<T> id(String id) {
        this.id = id;
        return this;
    }

    public SelectionModel<T> enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public SelectionModel<T> multiSelect(boolean multi) {
        this.multiSelect = multi;
        return this;
    }

    // ==================== State Access ====================

    public List<Option<T>> options() {
        return options;
    }

    public String selectedId() {
        return selectedId;
    }

    public Set<String> selectedIds() {
        return selectedIds;
    }

    public Option<T> selectedOption() {
        if (selectedId == null) return null;
        return options.stream()
                .filter(o -> o.id().equals(selectedId))
                .findFirst()
                .orElse(null);
    }

    public T selectedValue() {
        Option<T> opt = selectedOption();
        return opt != null ? opt.value() : null;
    }

    public boolean isSelected(String id) {
        if (multiSelect) {
            return selectedIds.contains(id);
        }
        return id != null && id.equals(selectedId);
    }

    public String label() { return label; }
    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public boolean multiSelect() { return multiSelect; }

    // ==================== State Modification ====================

    /**
     * Select an option by ID.
     */
    public SelectionModel<T> select(String id) {
        if (!enabled) return this;

        if (multiSelect) {
            if (!selectedIds.contains(id)) {
                selectedIds.add(id);
                changed();
            }
        } else {
            if (!id.equals(selectedId)) {
                selectedId = id;
                changed();
            }
        }
        return this;
    }

    /**
     * Deselect an option by ID (multi-select only).
     */
    public SelectionModel<T> deselect(String id) {
        if (!enabled) return this;

        if (multiSelect && selectedIds.contains(id)) {
            selectedIds.remove(id);
            changed();
        }
        return this;
    }

    /**
     * Toggle selection of an option (multi-select only).
     */
    public SelectionModel<T> toggle(String id) {
        if (!enabled) return this;

        if (multiSelect) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
            } else {
                selectedIds.add(id);
            }
            changed();
        } else {
            select(id);
        }
        return this;
    }

    /**
     * Select next option.
     */
    public void selectNext() {
        if (!enabled || options.isEmpty()) return;

        int idx = indexOfSelected();
        if (idx < options.size() - 1) {
            select(options.get(idx + 1).id());
        }
    }

    /**
     * Select previous option.
     */
    public void selectPrevious() {
        if (!enabled || options.isEmpty()) return;

        int idx = indexOfSelected();
        if (idx > 0) {
            select(options.get(idx - 1).id());
        } else if (idx < 0 && !options.isEmpty()) {
            select(options.get(0).id());
        }
    }

    private int indexOfSelected() {
        if (selectedId == null) return -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(selectedId)) {
                return i;
            }
        }
        return -1;
    }

    // ==================== Surface Generation ====================

    /**
     * Render as radio button group.
     */
    public RadioGroupSurface toRadioGroup() {
        return RadioGroupSurface.of(this);
    }

    /**
     * Render as dropdown/select.
     */
    public DropdownSurface toDropdown() {
        return DropdownSurface.of(this);
    }

    @Override
    public SurfaceSchema toSurface() {
        // Default to radio group
        return toRadioGroup();
    }

    // ==================== Event Handling ====================

    @Override
    public boolean handleEvent(String action, String target) {
        if ("select".equals(action) && target != null) {
            select(target);
            return true;
        }
        if ("toggle".equals(action) && target != null) {
            toggle(target);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleKey(KeyChord chord) {
        if (chord.isKey(SpecialKey.UP)) {
            selectPrevious();
            return true;
        }
        if (chord.isKey(SpecialKey.DOWN)) {
            selectNext();
            return true;
        }
        return false;
    }
}
