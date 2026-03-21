package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.VocabularySurface;

import java.util.*;
import java.util.stream.Stream;

/**
 * An item's local vocabulary — derived from its frames at hydration time.
 *
 * <p>Two kinds of entries:
 * <ul>
 *   <li><b>Actions</b> — predicate dispatch entries from {@code @Verb} annotations.
 *       Maps predicate IIDs to executable methods.</li>
 *   <li><b>Local tokens</b> — indexed string bindings scanned from the item's
 *       frames (endorsed + unendorsed). Bindings where {@code index == true}
 *       and target is a string Literal.</li>
 * </ul>
 *
 * <p>Vocabulary is entirely <b>derived</b> — transient, rebuilt at hydration,
 * not persisted. The frames are the source of truth.
 *
 * <p>Drives the "help" command — shows what's available on this item.
 */
@Implements(Vocabulary.KEY)
@Scene(as = VocabularySurface.class)
public class Vocabulary implements Iterable<VerbEntry> {

    public static final String KEY = "cg.sememe:vocabulary";

    // ==================================================================================
    // Action Dispatch (from @Verb annotations)
    // ==================================================================================

    /** Actions indexed by predicate Sememe ID. */
    private transient final Map<ItemID, VerbEntry> actions = new LinkedHashMap<>();

    /** Actions indexed by component handle (for component actions). */
    private transient final Map<String, List<VerbEntry>> byComponentHandle = new LinkedHashMap<>();

    // ==================================================================================
    // Local Tokens (scanned from frames)
    // ==================================================================================

    /**
     * Local token index — normalized token text → frame-backed Posting.
     *
     * <p>Built by scanning all frames on the item for bindings where
     * {@code index == true} and the target is a string Literal.
     * When duplicate tokens exist, the latest (by insertion order) wins.
     */
    private transient final Map<String, Posting> localTokens = new LinkedHashMap<>();

    // ==================================================================================
    // Action Registration
    // ==================================================================================

    public void add(VerbEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ItemID sememeId = entry.sememeId();

        if (actions.containsKey(sememeId)) {
            VerbEntry existing = actions.get(sememeId);
            if (existing.source() == entry.source()) {
                // Same source — allow override
            } else if (entry.source() == VerbSpec.VerbSource.COMPONENT) {
                // Component action overrides item action
            } else {
                throw new IllegalArgumentException(
                        "Duplicate action sememe: " + sememeId + " (existing: " + existing.source() +
                        ", new: " + entry.source() + ")");
            }
        }

        actions.put(sememeId, entry);

        if (entry.componentHandle() != null) {
            byComponentHandle
                    .computeIfAbsent(entry.componentHandle(), k -> new ArrayList<>())
                    .add(entry);
        }
    }

    public void addAll(List<VerbSpec> specs, Item owner) {
        for (VerbSpec spec : specs) {
            add(VerbEntry.itemVerb(spec, owner));
        }
    }

    // ==================================================================================
    // Frame Scanning — builds local token index
    // ==================================================================================

    /**
     * Scan an item's frames and populate the local token index.
     *
     * <p>Call this at hydration time after frames are decoded.
     * Scans ALL frames (endorsed + unendorsed) for bindings where
     * {@code index == true} and the target is a string Literal.
     *
     * @param frames the item's frames (endorsed + unendorsed)
     */
    public void scanFrames(EndorsementsTable frames) {
        localTokens.clear();
        if (frames == null) return;

        for (Frame frame : frames) {
            FrameBody body = frame.body();
            if (body == null) continue;

            List<Binding> bindings = body.frameBindings();
            for (int i = 0; i < bindings.size(); i++) {
                Binding b = bindings.get(i);
                if (!b.index()) continue;
                if (!(b.target() instanceof Literal lit)) continue;
                if (!Literal.TYPE_TEXT.equals(lit.valueType())) continue;

                String text;
                try {
                    text = lit.asText();
                } catch (Exception e) {
                    continue;
                }
                if (text == null || text.isBlank()) continue;

                String normalized = Posting.normalize(text);
                localTokens.put(normalized, Posting.fromFrame(body, i, 1.0f));
            }
        }
    }

    // ==================================================================================
    // Action Lookup
    // ==================================================================================

    public Optional<VerbEntry> lookup(ItemID sememeId) {
        return Optional.ofNullable(actions.get(sememeId));
    }

    public boolean hasVerb(ItemID sememeId) {
        return actions.containsKey(sememeId);
    }

    public Stream<VerbEntry> all() {
        return actions.values().stream();
    }

    public Stream<VerbEntry> itemVerbs() {
        return all().filter(v -> v.source() == VerbSpec.VerbSource.ITEM);
    }

    public Stream<VerbEntry> componentVerbs() {
        return all().filter(v -> v.source() == VerbSpec.VerbSource.COMPONENT);
    }

    public Stream<VerbEntry> verbsFor(String componentHandle) {
        List<VerbEntry> entries = byComponentHandle.get(componentHandle);
        return entries != null ? entries.stream() : Stream.empty();
    }

    // ==================================================================================
    // Local Token Lookup
    // ==================================================================================

    /**
     * Exact match in local token index.
     */
    public Optional<Posting> resolveLocal(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(localTokens.get(Posting.normalize(token)));
    }

    /**
     * Prefix match in local token index (for completions).
     */
    public List<Posting> prefixMatch(String prefix) {
        if (prefix == null) return List.of();
        String normalized = Posting.normalize(prefix);
        return localTokens.entrySet().stream()
                .filter(e -> e.getKey().startsWith(normalized))
                .map(Map.Entry::getValue)
                .toList();
    }

    // ==================================================================================
    // Utility
    // ==================================================================================

    @Override
    public Iterator<VerbEntry> iterator() {
        return actions.values().iterator();
    }

    public int size() {
        return actions.size();
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public int localTokenCount() {
        return localTokens.size();
    }

    public void clear() {
        actions.clear();
        byComponentHandle.clear();
        localTokens.clear();
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    public String emoji() { return "📖"; }
    public String displayToken() { return "Vocabulary"; }
    public boolean isExpandable() { return !isEmpty() || !localTokens.isEmpty(); }
    public String colorCategory() { return "vocabulary"; }

    public String displaySubtitle() {
        int verbs = actions.size();
        int tokens = localTokens.size();
        if (tokens > 0) {
            return verbs + " actions, " + tokens + " local tokens";
        }
        return verbs + " action" + (verbs == 1 ? "" : "s");
    }

    @Override
    public String toString() {
        return "Vocabulary[" + actions.size() + " actions, " + localTokens.size() + " local tokens]";
    }
}
