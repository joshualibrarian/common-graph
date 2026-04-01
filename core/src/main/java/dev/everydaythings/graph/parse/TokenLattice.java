package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Unified tokenizer that produces a lattice of candidate token spans.
 *
 * <p>Replaces all prior tokenization strategies with a single lattice-based approach.
 * Multiple tokenization strategies run concurrently, generating candidate spans.
 * The best path through the lattice is selected via Viterbi scoring.
 *
 * <p>ONE pipeline for both interactive and one-shot modes:
 * <ul>
 *   <li><b>Interactive</b>: lattice rebuilds incrementally as user types.
 *       {@link #spansAt} provides completion candidates. Ambiguous spans
 *       are shown in the UI for user selection.</li>
 *   <li><b>One-shot</b>: lattice builds from complete text. {@link #bestPath()}
 *       selects the optimal path. Ambiguous spans become errors.</li>
 * </ul>
 *
 * <p>Same code, different consumption of ambiguity.
 *
 * <p>Tokenization strategies:
 * <ol>
 *   <li><b>Whitespace boundaries</b> — standard word splitting</li>
 *   <li><b>Character class boundaries</b> — alpha/digit/symbol transitions
 *       (e.g., "5+2" → "5", "+", "2")</li>
 *   <li><b>Multi-word windows</b> — 2-word and 3-word spans tried against
 *       dictionary (handles "Bob Marley", "set game")</li>
 *   <li><b>Structural isolation</b> — parens, commas always split</li>
 *   <li><b>Literal detection</b> — numbers, quoted strings, booleans</li>
 * </ol>
 */
public class TokenLattice {

    /** The raw input text. */
    @Getter
    private final String rawText;

    /** All candidate spans, indexed by start character position. */
    private final Map<Integer, List<Span>> spansByStart;

    /** The total length in characters. */
    private final int length;

    /** Cached best path (computed lazily). */
    private List<Span> cachedBestPath;

    // ==================================================================================
    // Span — a candidate token in the lattice
    // ==================================================================================

    /**
     * A candidate token span in the raw text.
     *
     * <p>Each span covers a contiguous range of characters and may have
     * been resolved against the dictionary (postings) or identified as
     * a literal/structural token.
     */
    @Getter
    public static class Span {
        private final int startChar;
        private final int endChar;
        private final String text;
        private final List<Posting> postings;
        private final SpanType type;
        private final float score;

        Span(int startChar, int endChar, String text,
             List<Posting> postings, SpanType type, float score) {
            this.startChar = startChar;
            this.endChar = endChar;
            this.text = text;
            this.postings = postings != null ? List.copyOf(postings) : List.of();
            this.type = type;
            this.score = score;
        }

        /** Whether this span resolved to at least one posting. */
        public boolean isResolved() {
            return !postings.isEmpty();
        }

        /** Whether this span has multiple competing resolutions. */
        public boolean isAmbiguous() {
            if (postings.size() <= 1) return false;
            // Ambiguous if top two postings have similar weight
            float best = postings.getFirst().weight();
            float second = postings.get(1).weight();
            return second >= best * 0.8f;
        }

        /** The best posting (highest weight), or null if unresolved. */
        public Posting bestPosting() {
            return postings.isEmpty() ? null : postings.getFirst();
        }

        @Override
        public String toString() {
            return "Span[" + startChar + ":" + endChar + " \"" + text + "\" "
                    + type + " score=" + score
                    + (postings.isEmpty() ? "" : " postings=" + postings.size())
                    + "]";
        }
    }

    /** Classification of a span. */
    public enum SpanType {
        /** A word resolved via dictionary lookup. */
        WORD,
        /** A numeric or string literal. */
        LITERAL,
        /** An unresolved word (no dictionary match, not a literal). */
        UNRESOLVED
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    private TokenLattice(String rawText, Map<Integer, List<Span>> spansByStart) {
        this.rawText = rawText;
        this.spansByStart = spansByStart;
        this.length = rawText.length();
    }

    /**
     * Build a token lattice from raw text using multiple tokenization strategies.
     *
     * @param rawText the raw input text
     * @param lookup  resolves a normalized token string to postings
     * @return the built lattice
     */
    public static TokenLattice build(String rawText,
                                     Function<String, List<Posting>> lookup) {
        if (rawText == null || rawText.isEmpty()) {
            return new TokenLattice("", Map.of());
        }

        Map<Integer, List<Span>> spansByStart = new HashMap<>();

        // Phase 1: Parse raw text into positioned word segments
        List<WordSegment> words = splitIntoSegments(rawText);

        // Phase 2: For each word segment, generate spans
        for (WordSegment word : words) {
            // Try the whole word against the dictionary (including symbols like (, ), etc.)
            List<Posting> wordPostings = lookup.apply(Posting.normalize(word.text));
            if (!wordPostings.isEmpty()) {
                addSpan(spansByStart, new Span(
                        word.start, word.end, word.text,
                        sortByWeight(wordPostings), SpanType.WORD,
                        wordPostings.stream().map(Posting::weight).max(Float::compare).orElse(0f)));
            }

            // Check if it's a literal
            if (isLiteral(word.text)) {
                float literalScore = wordPostings.isEmpty() ? 0.9f : 0.5f;
                addSpan(spansByStart, new Span(
                        word.start, word.end, word.text,
                        List.of(), SpanType.LITERAL, literalScore));
            }

            // If neither resolved nor literal, add as unresolved
            if (wordPostings.isEmpty() && !isLiteral(word.text)) {
                addSpan(spansByStart, new Span(
                        word.start, word.end, word.text,
                        List.of(), SpanType.UNRESOLVED, 0.1f));
            }
        }

        // Phase 3: Multi-word windows (2-word and 3-word)
        List<WordSegment> contentWords = words.stream()
                .filter(w -> w.type != SegmentType.STRUCTURAL)
                .toList();

        for (int i = 0; i < contentWords.size() - 1; i++) {
            // 2-word window
            WordSegment w1 = contentWords.get(i);
            WordSegment w2 = contentWords.get(i + 1);
            String combined2 = w1.text + " " + w2.text;
            List<Posting> combined2Postings = lookup.apply(Posting.normalize(combined2));
            if (!combined2Postings.isEmpty()) {
                float score = combined2Postings.stream()
                        .map(Posting::weight).max(Float::compare).orElse(0f);
                addSpan(spansByStart, new Span(
                        w1.start, w2.end, combined2,
                        sortByWeight(combined2Postings), SpanType.WORD,
                        score + 0.1f)); // slight boost for multi-word match

                // 3-word window
                if (i + 2 < contentWords.size()) {
                    WordSegment w3 = contentWords.get(i + 2);
                    String combined3 = combined2 + " " + w3.text;
                    List<Posting> combined3Postings = lookup.apply(Posting.normalize(combined3));
                    if (!combined3Postings.isEmpty()) {
                        float score3 = combined3Postings.stream()
                                .map(Posting::weight).max(Float::compare).orElse(0f);
                        addSpan(spansByStart, new Span(
                                w1.start, w3.end, combined3,
                                sortByWeight(combined3Postings), SpanType.WORD,
                                score3 + 0.2f)); // stronger boost for 3-word match
                    }
                }
            }
        }

        return new TokenLattice(rawText, spansByStart);
    }

    // ==================================================================================
    // Path Finding
    // ==================================================================================

    /**
     * Find the best path through the lattice using Viterbi dynamic programming.
     *
     * <p>Returns the sequence of non-overlapping spans that maximizes
     * total score while covering the entire input.
     *
     * @return optimal span sequence, or empty list for empty input
     */
    public List<Span> bestPath() {
        if (cachedBestPath != null) return cachedBestPath;
        if (length == 0) {
            cachedBestPath = List.of();
            return cachedBestPath;
        }

        // Viterbi: bestScore[pos] = best total score for paths ending at pos
        float[] bestScore = new float[length + 1];
        Span[] bestPrev = new Span[length + 1];
        for (int i = 1; i <= length; i++) {
            bestScore[i] = Float.NEGATIVE_INFINITY;
        }

        // Skip whitespace at each position — whitespace between spans is free
        for (int pos = 0; pos <= length; pos++) {
            // Try advancing past whitespace
            int nextNonWs = skipWhitespace(pos);
            if (nextNonWs > pos && bestScore[pos] > bestScore[nextNonWs]) {
                bestScore[nextNonWs] = bestScore[pos];
                bestPrev[nextNonWs] = bestPrev[pos]; // carry forward (whitespace gap)
            }

            List<Span> spans = spansByStart.get(pos);
            if (spans == null) continue;

            for (Span span : spans) {
                float candidateScore = bestScore[pos] + span.score;
                // Skip whitespace after span end
                int endPos = skipWhitespace(span.endChar);
                if (candidateScore > bestScore[endPos]) {
                    bestScore[endPos] = candidateScore;
                    bestPrev[endPos] = span;
                }
            }
        }

        // Backtrack from end to build the path.
        // The forward pass stores at skipWhitespace(endChar), so backtracking
        // uses span.startChar directly — whitespace is already accounted for.
        List<Span> path = new ArrayList<>();
        int pos = length;
        while (pos > 0) {
            Span span = bestPrev[pos];
            if (span == null) {
                // No span reaches this position — skip back one char
                pos--;
                continue;
            }
            path.add(span);
            pos = span.startChar;
        }

        Collections.reverse(path);
        cachedBestPath = List.copyOf(path);
        return cachedBestPath;
    }

    /**
     * Get the top K paths through the lattice for ambiguity detection.
     *
     * <p>Currently returns only the best path. K-best Viterbi can be
     * implemented later when needed for interactive disambiguation.
     *
     * @param k maximum number of paths to return
     * @return list of paths, sorted by score (best first)
     */
    public List<List<Span>> topPaths(int k) {
        // For now, just return the best path
        List<Span> best = bestPath();
        return best.isEmpty() ? List.of() : List.of(best);
    }

    /**
     * Infer the active language at a character position.
     *
     * <p>Looks at the best path's span covering the given position and
     * returns the scope of its highest-weight posting. This drives the
     * language indicator icon in the prompt UI.
     *
     * @param charPosition character offset in the raw text
     * @return the language scope ItemID, or null if unknown
     */
    public ItemID languageAt(int charPosition) {
        for (Span span : bestPath()) {
            if (charPosition >= span.startChar && charPosition < span.endChar) {
                Posting best = span.bestPosting();
                return best != null ? best.scope() : null;
            }
        }
        return null;
    }

    /**
     * Get all candidate spans starting at or covering a character position.
     *
     * <p>Used by interactive mode to show completion candidates at the
     * cursor position.
     *
     * @param charPosition character offset in the raw text
     * @return candidate spans at this position
     */
    public List<Span> spansAt(int charPosition) {
        List<Span> result = spansByStart.get(charPosition);
        return result != null ? List.copyOf(result) : List.of();
    }

    /**
     * Whether any span in the best path is ambiguous.
     */
    public boolean hasAmbiguity() {
        return bestPath().stream().anyMatch(Span::isAmbiguous);
    }

    /**
     * Get all ambiguous spans in the best path.
     */
    public List<Span> ambiguousSpans() {
        return bestPath().stream().filter(Span::isAmbiguous).toList();
    }

    /**
     * Infer the dominant language from the best path's posting scopes.
     *
     * <p>Counts how many resolved spans came from each language scope.
     * The language with the most spans wins. Null-scoped postings
     * (universal symbols) are excluded from the count.
     *
     * @return the dominant language scope, or null if undetermined
     */
    public ItemID inferLanguage() {
        Map<ItemID, Integer> scopeCounts = new HashMap<>();
        for (Span span : bestPath()) {
            Posting best = span.bestPosting();
            if (best != null && best.scope() != null) {
                scopeCounts.merge(best.scope(), 1, Integer::sum);
            }
        }
        return scopeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ==================================================================================
    // Internal — Segment Parsing
    // ==================================================================================

    /** A raw word segment with character offsets. */
    private record WordSegment(int start, int end, String text, SegmentType type) {}
    private enum SegmentType { CONTENT, STRUCTURAL }

    /**
     * Split raw text into positioned word segments using character-class boundaries.
     *
     * <p>Reuses the same logic as InputController.splitRawTokens():
     * <ul>
     *   <li>Whitespace → boundary</li>
     *   <li>Structural chars (parens, commas) → isolated tokens</li>
     *   <li>Character class transitions (alpha↔digit↔symbol) → boundary</li>
     *   <li>Decimal points in number context stay together ("3.14")</li>
     *   <li>Digits after letters stay together ("x2")</li>
     * </ul>
     */
    static List<WordSegment> splitIntoSegments(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<WordSegment> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int cls = -1; // 0=digit, 1=letter, 2=symbol
        int segStart = -1;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // Whitespace — flush and skip
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    result.add(new WordSegment(segStart, i, current.toString(), SegmentType.CONTENT));
                    current.setLength(0);
                }
                cls = -1;
                segStart = -1;
                continue;
            }

            // Structural characters — always their own token
            if (ch == '(' || ch == ')' || ch == ',' || ch == ';') {
                if (!current.isEmpty()) {
                    result.add(new WordSegment(segStart, i, current.toString(), SegmentType.CONTENT));
                    current.setLength(0);
                }
                result.add(new WordSegment(i, i + 1, String.valueOf(ch), SegmentType.STRUCTURAL));
                cls = -1;
                segStart = -1;
                continue;
            }

            // Decimal point in number context: "3.14" stays together
            if (ch == '.' && cls == 0 && i + 1 < text.length()
                    && Character.isDigit(text.charAt(i + 1))) {
                current.append(ch);
                continue;
            }

            int newCls;
            if (Character.isDigit(ch)) newCls = 0;
            else if (Character.isLetter(ch) || ch == '_') newCls = 1;
            else newCls = 2; // symbol

            // Digit continuation in word context: "x2" stays as "x2"
            if (newCls == 0 && cls == 1) newCls = 1;

            if (cls != -1 && newCls != cls) {
                // Class boundary — flush
                result.add(new WordSegment(segStart, i, current.toString(), SegmentType.CONTENT));
                current.setLength(0);
                segStart = i;
            }

            if (current.isEmpty()) segStart = i;
            current.append(ch);
            cls = newCls;
        }
        if (!current.isEmpty()) {
            result.add(new WordSegment(segStart, text.length(), current.toString(), SegmentType.CONTENT));
        }

        return result;
    }

    // ==================================================================================
    // Internal — Helpers
    // ==================================================================================

    private static void addSpan(Map<Integer, List<Span>> spansByStart, Span span) {
        spansByStart.computeIfAbsent(span.startChar, k -> new ArrayList<>()).add(span);
    }

    private static boolean isLiteral(String text) {
        if (text == null || text.isEmpty()) return false;

        // Quoted string
        if ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"))) {
            return true;
        }

        // Boolean
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return true;
        }

        // Number (integer or decimal)
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<Posting> sortByWeight(List<Posting> postings) {
        if (postings.size() <= 1) return postings;
        List<Posting> sorted = new ArrayList<>(postings);
        sorted.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return sorted;
    }

    private int skipWhitespace(int pos) {
        while (pos < length && Character.isWhitespace(rawText.charAt(pos))) {
            pos++;
        }
        return pos;
    }
}
