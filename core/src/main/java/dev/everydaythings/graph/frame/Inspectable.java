package dev.everydaythings.graph.frame;

import java.util.List;

/**
 * Frame instances that expose browsable sub-entries for inspection.
 *
 * <p>Implement this on frame value classes that have internal structure
 * worth browsing — log entries, move history, key rotations, etc.
 * The entries appear as expandable children in INSPECT mode tree views.
 */
public interface Inspectable {

    /**
     * Sub-entries visible in INSPECT mode tree.
     *
     * @return list of browsable entries, empty if none
     */
    List<InspectEntry> inspectEntries();
}
