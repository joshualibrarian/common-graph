package dev.everydaythings.graph.frame;

/**
 * A browsable entry within a frame, visible in INSPECT mode.
 *
 * <p>Frame instances that expose sub-entries (log entries, move history,
 * key history, etc.) return lists of these for tree navigation.
 *
 * @param id    unique identifier within the frame (used in tree link paths)
 * @param label human-readable label for the tree node
 * @param emoji glyph/emoji for the tree node icon
 * @param value the data object to render in the detail pane
 */
public record InspectEntry(String id, String label, String emoji, Object value) {}
