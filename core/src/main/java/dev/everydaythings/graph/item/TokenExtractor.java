package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.Item.TokenEntry;
import dev.everydaythings.graph.item.mount.Mount;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Extracts search tokens from Items for indexing.
 *
 * <p>Extracted from Item.java to keep Item focused on core semantics.
 * The {@link Item.TokenEntry} record remains on Item for backward compatibility
 * with subclass overrides.
 */
public final class TokenExtractor {

    private TokenExtractor() {}

    /**
     * Extract tokens for indexing an item.
     *
     * <p>Scans the actual content and relations stored in this item, not just
     * class metadata. Classes define the schema; items hold the data.
     *
     * <p>Token sources:
     * <ul>
     *   <li>Content components - string values from handles like "name", "symbol", "label"</li>
     *   <li>Frame bodies - can contribute tokens from predicates/objects</li>
     *   <li>DisplayInfo - the computed display name and type</li>
     *   <li>Path mounts - mounted component paths (highest weight, always first in lookups)</li>
     * </ul>
     *
     * @param item the item to extract tokens from
     * @return stream of tokens for this item
     */
    public static Stream<TokenEntry> extractTokens(Item item) {
        List<TokenEntry> tokens = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Helper to add token only if not already seen
        BiConsumer<String, Float> addToken = (token, weight) -> {
            if (token != null && !token.isBlank()) {
                String normalized = token.toLowerCase().trim();
                if (seen.add(normalized)) {
                    tokens.add(new TokenEntry(token, weight));
                }
            }
        };

        // 1. Scan content table for string values
        EndorsementsTable content = item.frames();
        if (content != null) {
            for (Frame frame : content) {
                var frameKey = frame.frameKey();
                var opt = content.getLive(frameKey);
                if (opt.isPresent()) {
                    Object value = opt.get();
                    extractTokensFromValue(value, frameKey.toString(), addToken);
                }
            }
        }

        // 2. Add display info tokens (may overlap with content, but addToken dedupes)
        var info = item.displayInfo();
        if (info != null) {
            addToken.accept(info.name(), 1.0f);
            addToken.accept(info.typeName(), 0.5f);
        }

        // 3. Add displayToken if different from just class name
        String label = item.displayToken();
        if (label != null && !label.equals(item.getClass().getSimpleName())) {
            addToken.accept(label, 0.9f);
        }

        // 4. Scan path mounts — mounted components get high-weight tokens
        if (content != null) {
            for (Frame frame : content) {
                for (Mount.PathMount pm : content.pathMountsFor(frame.frameKey())) {
                    String mountPath = pm.path();
                    if (mountPath != null && !mountPath.isBlank()) {
                        // Leaf segment of the mount path
                        String clean = mountPath.startsWith("/") ? mountPath.substring(1) : mountPath;
                        String[] segments = clean.split("/");
                        if (segments.length > 0) {
                            String leaf = segments[segments.length - 1];
                            if (!leaf.isBlank()) {
                                addToken.accept(leaf, 1.5f);
                            }
                        }
                    }
                }
            }
        }

        return tokens.stream();
    }

    /**
     * Extract tokens from a component value.
     *
     * <p>Handles common patterns: String, Map&lt;String,String&gt; for multilingual names, etc.
     */
    private static void extractTokensFromValue(Object value, String handle,
            BiConsumer<String, Float> addToken) {
        // High-value handles get higher weight
        float weight = switch (handle.toLowerCase()) {
            case "name", "symbol", "label", "title" -> 1.0f;
            case "names", "labels", "aliases" -> 0.9f;
            case "description", "descriptions" -> 0.3f;
            default -> 0.5f;
        };

        if (value instanceof String s) {
            // Don't index very long strings (descriptions, etc.)
            if (s.length() <= 100) {
                addToken.accept(s, weight);
            }
        } else if (value instanceof Map<?, ?> map) {
            // Multilingual maps like names = {en: "meter", de: "Meter"}
            for (Object v : map.values()) {
                if (v instanceof String s && s.length() <= 100) {
                    addToken.accept(s, weight * 0.9f);
                }
            }
        } else if (value instanceof Iterable<?> iter) {
            // Lists of aliases, tokens, etc.
            for (Object v : iter) {
                if (v instanceof String s && s.length() <= 100) {
                    addToken.accept(s, weight * 0.8f);
                }
            }
        }
        // Components themselves might have their own tokens - we could recurse
        // but for now, we're scanning the raw data stored in the content table
    }
}
