package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.Log;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Activity log — a persistent stream of activity entries.
 *
 * <p>Extends {@link Log} to get append-only stream semantics with
 * content-addressed storage. Each entry is a {@link ActivityEntry}
 * encoded via Canonical.
 *
 * <p>Used on both Session (user interactions) and Librarian (infrastructure
 * events). The {@code identity = false} frame binding means appending
 * entries doesn't churn the owner's VID.
 *
 * <p>Adds an in-memory context index for fast "last entry for this view"
 * queries (the feedback panel).
 *
 * @see ActivityEntry
 */
@Type(value = ActivityLog.KEY, glyph = "📋", color = 0x6699CC)
public class ActivityLog extends Log<ActivityEntry> {

    public static final String KEY = "cg:type/activity-log";
    public static final String HANDLE = "activity";

    /** In-memory index: context IID → most recent entry for that context. */
    private final transient Map<ItemID, ActivityEntry> lastByContext = new LinkedHashMap<>();

    /** In-memory cache of recent entries for fast queries without ActionContext. */
    private final transient List<ActivityEntry> recentEntries = new ArrayList<>();
    private static final int MAX_RECENT = 100;

    // ==================================================================================
    // Simple Append (no ActionContext needed)
    // ==================================================================================

    /**
     * Append an entry to the activity log without an ActionContext.
     *
     * <p>This stores the entry in-memory only (no content-addressed storage).
     * Use the inherited {@link Log#append} with an ActionContext for
     * persistent storage.
     *
     * @param entry The entry to append
     */
    public void append(ActivityEntry entry) {
        recentEntries.add(entry);
        if (recentEntries.size() > MAX_RECENT) {
            recentEntries.removeFirst();
        }
        if (entry.contextIid() != null) {
            lastByContext.put(entry.contextIid(), entry);
        }
    }

    // ==================================================================================
    // Query Helpers
    // ==================================================================================

    /**
     * Get the most recent entry for a specific context.
     *
     * <p>This is the query the feedback panel evaluates:
     * "last activity where context matches this view."
     */
    public Optional<ActivityEntry> lastForContext(ItemID contextIid) {
        if (contextIid == null) return last();
        return Optional.ofNullable(lastByContext.get(contextIid));
    }

    /**
     * Get the most recent entry (any context).
     */
    public Optional<ActivityEntry> last() {
        if (recentEntries.isEmpty()) return Optional.empty();
        return Optional.of(recentEntries.getLast());
    }

    /**
     * Get the N most recent entries (newest first).
     */
    public List<ActivityEntry> recent(int count) {
        int size = recentEntries.size();
        int start = Math.max(0, size - count);
        List<ActivityEntry> result = new ArrayList<>(recentEntries.subList(start, size));
        Collections.reverse(result);
        return result;
    }

    /**
     * Total number of cached entries.
     */
    public int size() {
        return recentEntries.size();
    }

    // ==================================================================================
    // Display Overrides
    // ==================================================================================

    @Override
    public String displayToken() {
        long n = recentEntries.size();
        return "activity" + (n > 0 ? " (" + n + ")" : "");
    }

    @Override
    protected String entryLabel(long seq, ActivityEntry payload) {
        return payload.toString();
    }

    @Override
    protected String entryEmoji(long seq, ActivityEntry payload) {
        return payload.isSuccess() ? "✓" : "✗";
    }

    // ==================================================================================
    // Verbs
    // ==================================================================================

    @Verb(value = CoreVocabulary.Remove.KEY, doc = "Clear activity log")
    public ActionResult clear() {
        recentEntries.clear();
        lastByContext.clear();
        return ActionResult.success("Activity log cleared.");
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        if (recentEntries.isEmpty()) return "No activity yet.";
        StringBuilder sb = new StringBuilder();
        int size = recentEntries.size();
        sb.append(size).append(size == 1 ? " entry" : " entries").append("\n\n");
        List<ActivityEntry> recent = recent(10);
        for (ActivityEntry entry : recent) {
            sb.append(entry.isSuccess() ? "  ✓ " : "  ✗ ").append(entry).append("\n");
        }
        if (size > 10) {
            sb.append("  … and ").append(size - 10).append(" more\n");
        }
        return sb.toString().stripTrailing();
    }
}
