package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.frame.InspectEntry;
import dev.everydaythings.graph.frame.Inspectable;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Activity log — an in-memory stream of activity entries.
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
@Implements(ActivityLog.KEY)
@ItemSeed(key = ActivityLog.KEY)
public class ActivityLog implements Canonical, Inspectable {

    public static final String KEY = "cg.sememe:activity-log";

    @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
    static final String seedGloss = "audit trail of item operations";

    @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
    static final String seedNoun = "activity-log";

    public static final String HANDLE = "activity";

    /** In-memory index: context IID → most recent entry for that context. */
    private final transient Map<ItemID, ActivityEntry> lastByContext = new LinkedHashMap<>();

    /** In-memory cache of recent entries for fast queries. */
    private final transient List<ActivityEntry> recentEntries = new ArrayList<>();
    private static final int MAX_RECENT = 100;

    // ==================================================================================
    // Append
    // ==================================================================================

    /**
     * Append an entry to the activity log.
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

    /**
     * Check if the log is empty.
     */
    public boolean isEmpty() {
        return recentEntries.isEmpty();
    }

    // ==================================================================================
    // Inspectable
    // ==================================================================================

    @Override
    public List<InspectEntry> inspectEntries() {
        List<InspectEntry> result = new ArrayList<>(recentEntries.size());
        for (int i = recentEntries.size() - 1; i >= 0; i--) {
            ActivityEntry e = recentEntries.get(i);
            result.add(new InspectEntry(
                    String.valueOf(i),
                    e.toString(),
                    e.isSuccess() ? "✓" : "✗",
                    e));
        }
        return result;
    }

    public boolean isExpandable() {
        return !isEmpty();
    }

    public String displayToken() {
        long n = recentEntries.size();
        return "activity" + (n > 0 ? " (" + n + ")" : "");
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
