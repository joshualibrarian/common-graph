package dev.everydaythings.graph.ui;

/**
 * View modes for displaying collections - like macOS Finder's view switcher.
 *
 * <p>All collection displays support switching between these modes:
 * <ul>
 *   <li>TABLE - Traditional table with columns, sortable</li>
 *   <li>TILES - Grid of cards, wraps to fit</li>
 *   <li>LIST - Simple vertical list with icons</li>
 *   <li>GALLERY - Large previews in a grid</li>
 * </ul>
 *
 * <p>Each mode has a keyboard shortcut (Cmd/Ctrl + number):
 * <ul>
 *   <li>Cmd+1: Table</li>
 *   <li>Cmd+2: Tiles</li>
 *   <li>Cmd+3: List</li>
 *   <li>Cmd+4: Gallery</li>
 * </ul>
 */
public enum CollectionViewMode {
    /** Table view with sortable columns */
    TABLE("Table", "table", "⊞", '1'),

    /** Tile/card grid view */
    TILES("Tiles", "tiles", "⊟", '2'),

    /** Simple list view */
    LIST("List", "list", "☰", '3'),

    /** Gallery view with large previews */
    GALLERY("Gallery", "gallery", "⊡", '4');

    private final String label;
    private final String cssClass;
    private final String icon;
    private final char shortcut;

    CollectionViewMode(String label, String cssClass, String icon, char shortcut) {
        this.label = label;
        this.cssClass = cssClass;
        this.icon = icon;
        this.shortcut = shortcut;
    }

    public String label() { return label; }
    public String cssClass() { return cssClass; }
    public String icon() { return icon; }
    public char shortcut() { return shortcut; }

    /**
     * Get the next mode in cycle order.
     */
    public CollectionViewMode next() {
        CollectionViewMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /**
     * Find mode by shortcut key.
     */
    public static CollectionViewMode fromShortcut(char key) {
        for (CollectionViewMode mode : values()) {
            if (mode.shortcut == key) {
                return mode;
            }
        }
        return null;
    }
}
