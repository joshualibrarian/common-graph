package dev.everydaythings.graph.library.dictionary;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.ThematicRole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Extracts frame-backed postings from FrameBodies for the global TokenDictionary.
 *
 * <p>Two extraction paths:
 * <ul>
 *   <li>{@link #fromBody(FrameBodyOld)} — VALUE bindings (lexemes, titles, symbols).
 *       Scope and features derived from binding qualifiers. Frames with a LOCATION
 *       binding are skipped (those are locally-scoped bindings, indexed only in the
 *       per-item Vocabulary).</li>
 *   <li>{@link #fromFrameBody(FrameBodyOld, Function)} — GOAL binding text (data-driven
 *       predicate indexing). Scope = theme item.</li>
 * </ul>
 *
 * <p>All returned postings are frame-backed: they carry a reference to the source
 * FrameBody and binding index. The TokenDictionary persists only the body hash
 * and binding index; all other fields are recovered at query time.
 */
public final class TokenExtractor {

    private TokenExtractor() {}

    /**
     * Extract postings from VALUE bindings in a FrameBody for global indexing.
     *
     * <p>A frame may have multiple VALUE bindings with different compound keys
     * (synonyms, different languages). Each produces a separate frame-backed
     * posting. The compound binding key carries scope and features:
     * {@code VALUE:[ENGLISH, VERB, LEMMA] → "create"} produces a posting with
     * scope=ENGLISH, features={VERB, LEMMA}.
     *
     * <p>Frames with a LOCATION binding are <b>skipped</b> — the presence of
     * LOCATION means the VALUE is a locally-scoped binding (e.g., EQUALS variables),
     * not a globally-discoverable vocabulary entry.
     *
     * @param body the FrameBody (self-contained assertion)
     * @return list of frame-backed postings (may be empty)
     */
    public static List<Posting> fromBody(FrameBodyOld body) {
        if (body == null) return List.of();

        // LOCATION binding present → locally scoped (EQUALS variables etc.)
        // These are indexed only in the per-item Vocabulary, not the global dictionary.
        if (body.binding(ThematicRole.Location.IID) != null) return List.of();

        ItemID target = body.homeId();
        if (target == null) return List.of();

        List<Posting> result = new ArrayList<>();
        List<Binding> bindings = body.frameBindings();
        for (int i = 0; i < bindings.size(); i++) {
            Binding b = bindings.get(i);
            if (!b.index()) continue;
            if (!ThematicRole.Value.IID.equals(b.role())) continue;
            if (!(b.target() instanceof Literal lit)) continue;

            String token = extractTextFromLiteral(lit);
            if (token == null || token.isBlank()) continue;

            result.add(Posting.fromFrame(body, i, 1.0f));
        }
        return result;
    }

    /**
     * Extract all VALUE-binding postings from an item's frames for global indexing.
     *
     * @param item the item with live Frame objects
     * @return list of frame-backed postings (may be empty)
     */
    public static List<Posting> fromItemFrames(ItemOld item) {
        if (item == null || item.frames() == null) return List.of();

        List<Posting> result = new ArrayList<>();
        for (FrameOld frame : item.frames()) {
            if (frame.body() != null) {
                result.addAll(fromBody(frame.body()));
            }
        }
        return result;
    }

    /**
     * Extract postings from a frame body using data-driven predicate indexing.
     *
     * <p>The predicate's own definition declares whether its string targets
     * should be indexed, via the index weight. The GOAL binding's text becomes
     * the token, scoped to the theme item.
     *
     * @param body                    the frame body to extract from
     * @param predicateWeightResolver resolves predicate IID → index weight (0 = don't index)
     * @return list of frame-backed postings (may be empty)
     */
    public static List<Posting> fromFrameBody(
            FrameBodyOld body,
            Function<ItemID, Float> predicateWeightResolver) {
        if (body == null) return List.of();

        float weight = predicateWeightResolver.apply(body.predicate());
        if (weight > 0) {
            return extractGoalPostingFromBody(body, weight);
        }
        return List.of();
    }

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    private static List<Posting> extractGoalPostingFromBody(FrameBodyOld body, float weight) {
        ItemID goalRole = ItemID.fromString("cg.role:goal");
        List<Binding> bindings = body.frameBindings();

        for (int i = 0; i < bindings.size(); i++) {
            Binding b = bindings.get(i);
            if (!goalRole.equals(b.role())) continue;
            if (!(b.target() instanceof Literal lit)) continue;

            String text = extractTextFromLiteral(lit);
            if (text != null && !text.isBlank()) {
                return List.of(Posting.fromFrame(body, i, weight));
            }
        }
        return List.of();
    }

    private static String extractTextFromLiteral(Literal literal) {
        if (literal == null) return null;
        if (Literal.TYPE_TEXT.equals(literal.valueType())) {
            try {
                return literal.asText();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
