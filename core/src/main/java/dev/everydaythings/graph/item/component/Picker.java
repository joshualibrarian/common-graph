package dev.everydaythings.graph.item.component;

/**
 * UI widget types for factory and verb parameters.
 *
 * <p>Used with {@link Param#picker()} to specify how a parameter
 * should be rendered in creation forms.
 */
public enum Picker {
    /** Auto-detect based on parameter type */
    AUTO,
    /** Text input field */
    TEXT,
    /** Numeric spinner/input */
    NUMBER,
    /** Checkbox */
    BOOLEAN,
    /** Dropdown (for enums or constrained types) */
    DROPDOWN,
    /** File picker dialog */
    FILE,
    /** Directory picker dialog */
    DIRECTORY,
    /** Item reference picker (navigable) */
    ITEM_REF
}
