package dev.everydaythings.graph.bridges.keri.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builders for the five KEL event types KERI defines:
 *
 * <ul>
 *   <li>{@code icp} — inception (first event for an AID)</li>
 *   <li>{@code rot} — rotation (replace current signing keys)</li>
 *   <li>{@code ixn} — interaction (anchor external data)</li>
 *   <li>{@code dip} — delegated inception (anchored to a delegator's KEL)</li>
 *   <li>{@code drt} — delegated rotation</li>
 * </ul>
 *
 * <p>Each builder returns a {@link LinkedHashMap} populated in KERI's canonical
 * field order — {@code v} first, then {@code t}, {@code d}, {@code i}, and so
 * on — so {@link KelJson#encode(Map)} can serialize deterministically.  The
 * {@code v} and {@code d} fields hold placeholders that {@code KelJson.encode}
 * back-patches with the size and SAID.
 *
 * <p>v1 keeps witnesses out: {@code b}/{@code bt}/{@code ba}/{@code br} default
 * to empty / zero.  Adding witness support is task #229.
 */
public final class KelEvents {

    private KelEvents() {}

    /**
     * Inception event ({@code icp}).
     *
     * @param identifier this AID's prefix (qb64 of the signing key, or the SAID
     *                   of the inception event itself for multi-sig — caller's
     *                   choice; this builder doesn't recompute it)
     * @param signingKeys current signing keys, qb64 (typically code D / B)
     * @param nextKeyDigests digests of the next-rotation keys, qb64
     * @param signingThreshold simple integer threshold as a string ("1", "2", …)
     * @param nextThreshold same shape for next-rotation
     */
    public static Map<String, Object> inception(
            String identifier,
            List<String> signingKeys,
            List<String> nextKeyDigests,
            String signingThreshold,
            String nextThreshold) {
        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
        e.put("v", KelJson.VERSION_PLACEHOLDER);
        e.put("t", "icp");
        e.put("d", KelJson.SAID_PLACEHOLDER);
        e.put("i", identifier);
        e.put("s", "0");
        e.put("kt", signingThreshold);
        e.put("k", List.copyOf(signingKeys));
        e.put("nt", nextThreshold);
        e.put("n", List.copyOf(nextKeyDigests));
        e.put("bt", "0");
        e.put("b", List.of());
        e.put("c", List.of());
        e.put("a", List.of());
        return e;
    }

    /** Rotation event ({@code rot}). */
    public static Map<String, Object> rotation(
            String identifier,
            long sequence,
            String priorEventDigest,
            List<String> newSigningKeys,
            List<String> nextKeyDigests,
            String signingThreshold,
            String nextThreshold) {
        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
        e.put("v", KelJson.VERSION_PLACEHOLDER);
        e.put("t", "rot");
        e.put("d", KelJson.SAID_PLACEHOLDER);
        e.put("i", identifier);
        e.put("s", Long.toHexString(sequence));
        e.put("p", priorEventDigest);
        e.put("kt", signingThreshold);
        e.put("k", List.copyOf(newSigningKeys));
        e.put("nt", nextThreshold);
        e.put("n", List.copyOf(nextKeyDigests));
        e.put("bt", "0");
        e.put("br", List.of());
        e.put("ba", List.of());
        e.put("a", List.of());
        return e;
    }

    /** Interaction event ({@code ixn}) — anchors arbitrary seals into the KEL. */
    public static Map<String, Object> interaction(
            String identifier,
            long sequence,
            String priorEventDigest,
            List<Map<String, Object>> anchors) {
        LinkedHashMap<String, Object> e = new LinkedHashMap<>();
        e.put("v", KelJson.VERSION_PLACEHOLDER);
        e.put("t", "ixn");
        e.put("d", KelJson.SAID_PLACEHOLDER);
        e.put("i", identifier);
        e.put("s", Long.toHexString(sequence));
        e.put("p", priorEventDigest);
        e.put("a", List.copyOf(anchors));
        return e;
    }

    /** Delegated inception ({@code dip}) — inception anchored under a delegator. */
    public static Map<String, Object> delegatedInception(
            String identifier,
            String delegatorIdentifier,
            List<String> signingKeys,
            List<String> nextKeyDigests,
            String signingThreshold,
            String nextThreshold) {
        LinkedHashMap<String, Object> e = inceptionBase(identifier, signingKeys,
                nextKeyDigests, signingThreshold, nextThreshold);
        e.put("t", "dip");
        e.put("di", delegatorIdentifier);
        return reorderForDip(e);
    }

    /** Delegated rotation ({@code drt}) — rotation under an existing delegation. */
    public static Map<String, Object> delegatedRotation(
            String identifier,
            long sequence,
            String priorEventDigest,
            List<String> newSigningKeys,
            List<String> nextKeyDigests,
            String signingThreshold,
            String nextThreshold) {
        LinkedHashMap<String, Object> e = (LinkedHashMap<String, Object>) rotation(
                identifier, sequence, priorEventDigest, newSigningKeys,
                nextKeyDigests, signingThreshold, nextThreshold);
        e.put("t", "drt");
        return e;
    }

    // ==================================================================================
    // Internals
    // ==================================================================================

    private static LinkedHashMap<String, Object> inceptionBase(
            String identifier, List<String> signingKeys, List<String> nextKeyDigests,
            String signingThreshold, String nextThreshold) {
        return (LinkedHashMap<String, Object>) inception(
                identifier, signingKeys, nextKeyDigests, signingThreshold, nextThreshold);
    }

    private static LinkedHashMap<String, Object> reorderForDip(LinkedHashMap<String, Object> e) {
        LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
        for (String key : List.of("v", "t", "d", "i", "s", "kt", "k", "nt", "n", "bt", "b", "c", "a", "di")) {
            if (e.containsKey(key)) reordered.put(key, e.get(key));
        }
        return reordered;
    }
}
