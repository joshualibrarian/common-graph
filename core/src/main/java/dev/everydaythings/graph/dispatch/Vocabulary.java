package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.language.Posting;

import java.util.*;

/**
 * An item's local vocabulary — derived from its frames at hydration time.
 *
 * <p>Contains local tokens: indexed string bindings scanned from the item's
 * frames (endorsed + unendorsed). Bindings where {@code index == true}
 * and target is a string Literal.
 *
 * <p>Vocabulary is entirely <b>derived</b> — transient, rebuilt at hydration,
 * not persisted. The frames are the source of truth.
 */
@Implements(Vocabulary.KEY)
public class Vocabulary {

    public static final String KEY = "cg.sememe:vocabulary";

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

        for (FrameOld frame : frames) {
            FrameBodyOld body = frame.body();
            if (body == null) continue;

            List<Binding> bindings = body.frameBindings();
            for (int i = 0; i < bindings.size(); i++) {
                Binding b = bindings.get(i);
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

    public int localTokenCount() {
        return localTokens.size();
    }

    public void clear() {
        localTokens.clear();
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    public String emoji() { return "📖"; }
    public String displayToken() { return "Vocabulary"; }
    public boolean isExpandable() { return !localTokens.isEmpty(); }
    public String colorCategory() { return "vocabulary"; }

    public String displaySubtitle() {
        int tokens = localTokens.size();
        return tokens + " local token" + (tokens == 1 ? "" : "s");
    }

    @Override
    public String toString() {
        return "Vocabulary[" + localTokens.size() + " local tokens]";
    }
}
