package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Sememe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Session activity log — records user interactions as structured entries.
 *
 * <p>This is a component on the Session item, visible in the component tree.
 * It's the single source of truth for "what happened" in this session.
 *
 * <p>The feedback panel near each item's prompt is an ephemeral expression
 * query into this log — {@code last(session.activity where view == self)} —
 * not a component on each individual item.
 *
 * <p>In-memory for now. When Session has proper librarian-backed storage,
 * this will migrate to a persistent {@code Log<ActivityEntry>} stream.
 */
@Type(value = ActivityLog.KEY, glyph = "📋", color = 0x6699CC)
public class ActivityLog implements Component {

    public static final String KEY = "cg:type/activity-log";
    public static final String HANDLE = "activity";

    private final List<ActivityEntry> entries = new ArrayList<>();
    private final Map<ItemID, ActivityEntry> lastByContext = new LinkedHashMap<>();
    private Item owner;

    // ==================================================================================
    // Component Lifecycle
    // ==================================================================================

    @Override
    public void initComponent(Item owner) {
        this.owner = owner;
    }

    @Override
    public String emoji() {
        return "📋";
    }

    @Override
    public String displayToken() {
        return entries.isEmpty() ? "activity" : "activity (" + entries.size() + ")";
    }

    @Override
    public boolean isExpandable() {
        return !entries.isEmpty();
    }

    @Override
    public List<InspectEntry> inspectEntries() {
        List<InspectEntry> result = new ArrayList<>();
        // Most recent first
        for (int i = entries.size() - 1; i >= 0; i--) {
            ActivityEntry entry = entries.get(i);
            String emoji = entry.isSuccess() ? "✓" : "✗";
            result.add(new InspectEntry(
                    String.valueOf(i),
                    entry.toString(),
                    emoji,
                    entry));
        }
        return result;
    }

    // ==================================================================================
    // Log Operations
    // ==================================================================================

    /**
     * Append an entry to the activity log.
     */
    public void append(ActivityEntry entry) {
        entries.add(entry);
        if (entry.contextIid() != null) {
            lastByContext.put(entry.contextIid(), entry);
        }
    }

    /**
     * Get the most recent entry for a specific context.
     *
     * <p>This is the ephemeral query that the feedback panel evaluates:
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
        if (entries.isEmpty()) return Optional.empty();
        return Optional.of(entries.getLast());
    }

    /**
     * Get the N most recent entries (newest first).
     */
    public List<ActivityEntry> recent(int count) {
        int size = entries.size();
        int start = Math.max(0, size - count);
        List<ActivityEntry> result = new ArrayList<>(entries.subList(start, size));
        Collections.reverse(result);
        return result;
    }

    /**
     * Total number of entries.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Whether the log is empty.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    // ==================================================================================
    // Verbs
    // ==================================================================================

    @Verb(value = Sememe.LIST, doc = "Show recent activity")
    public ActionResult list() {
        if (entries.isEmpty()) {
            return ActionResult.success("No activity yet.");
        }
        StringBuilder sb = new StringBuilder();
        List<ActivityEntry> recent = recent(10);
        for (ActivityEntry entry : recent) {
            sb.append(entry.toString()).append("\n");
        }
        return ActionResult.success(sb.toString().trim());
    }

    @Verb(value = Sememe.COUNT, doc = "Count activity entries")
    public ActionResult count() {
        return ActionResult.success(entries.size() + " entries");
    }

    @Verb(value = Sememe.REMOVE, doc = "Clear activity log")
    public ActionResult clear() {
        entries.clear();
        lastByContext.clear();
        return ActionResult.success("Activity log cleared.");
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        if (entries.isEmpty()) return "No activity yet.";
        StringBuilder sb = new StringBuilder();
        sb.append(entries.size()).append(entries.size() == 1 ? " entry" : " entries").append("\n\n");
        List<ActivityEntry> recent = recent(10);
        for (ActivityEntry entry : recent) {
            sb.append(entry.isSuccess() ? "  ✓ " : "  ✗ ").append(entry).append("\n");
        }
        if (entries.size() > 10) {
            sb.append("  … and ").append(entries.size() - 10).append(" more\n");
        }
        return sb.toString().stripTrailing();
    }
}
