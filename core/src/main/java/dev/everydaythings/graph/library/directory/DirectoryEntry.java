package dev.everydaythings.graph.library.directory;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ContentID;
import lombok.Value;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * Directory entry tracking an Item's location and head hints.
 */
@Value
class DirectoryEntry {
    ItemID iid;

    // --- Location hints ---
    Path workingTreePath;       // null if none
    boolean preferWorkingTree;  // for writes / HEAD resolution
    boolean thinWorkingTree;    // allow fallback to main store

    ContentID selectedHead;

    // --- Cached head info (HINTS, not truth) ---
    Map<String, ContentID> observedHeads;

    // --- Bookkeeping ---
    Instant firstSeen;
    Instant lastSeen;
    Instant lastUpdated;
}
