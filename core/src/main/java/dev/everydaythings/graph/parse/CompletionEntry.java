package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.item.id.ItemID;

/**
 * Enriched completion entry for display — carries token text plus resolved metadata.
 *
 * <p>Built from a {@link dev.everydaythings.graph.language.Posting} by resolving
 * the target Item to extract emoji and type name. Renderers use this to show
 * rich completions (e.g., "♟️ chess  Chess" instead of just "chess").
 *
 * @param token    the completion text (e.g., "chess")
 * @param emoji    the target item's emoji, or null
 * @param typeName the target item's type name (e.g., "Chess"), or null
 * @param target   the target ItemID
 */
public record CompletionEntry(
        String token,
        String emoji,
        String typeName,
        ItemID target
) {
    /** Create a plain entry when the target can't be resolved. */
    public static CompletionEntry plain(String token, ItemID target) {
        return new CompletionEntry(token, null, null, target);
    }
}
