package dev.everydaythings.graph.text;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.library.tokens.Posting;
import dev.everydaythings.graph.value.Decimal;
import lombok.Getter;
import lombok.Value;
import lombok.With;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Tokenization of input text into a lattice of candidate spans, with multi-word
 * windows and best-path selection.
 *
 * <p>The lattice is the foundation of the text-parsing pipeline. Given raw input,
 * it generates every plausible tokenization candidate (single words, multi-word
 * combinations like "Buenos Aires", numeric literals, quoted strings) and selects
 * the highest-scoring path through the graph via Viterbi dynamic programming.
 *
 * <p>All offsets in {@link TokenSpan#span} are <b>grapheme cluster boundaries per
 * UAX #29</b>, computed via ICU's {@link BreakIterator}. Word boundaries also come
 * from ICU, locale-aware. Surface text is preserved verbatim from the input.
 *
 * <h3>Span generation</h3>
 * For each grapheme range, the lattice may emit multiple competing spans:
 * <ul>
 *   <li><b>WORD</b> — the normalized text resolves to one or more {@link Posting}s
 *       in the dictionary. Multiple postings = ambiguous resolution.</li>
 *   <li><b>LITERAL</b> — the text parses as a number ({@link Decimal#parse}) or a
 *       quoted string. Literals always have high score regardless of dictionary.</li>
 *   <li><b>UNRESOLVED</b> — neither a dictionary hit nor a recognized literal. Low
 *       score but still in the lattice so coverage holds.</li>
 * </ul>
 *
 * <h3>Multi-word windows</h3>
 * For every starting word position, the lattice tries combinations of N adjacent
 * words (N up to {@link #MAX_WINDOW}) as a single token. Each combination is
 * normalized and looked up in the dictionary. Hits get a small bonus over their
 * single-word equivalents, so "Buenos Aires" wins as one token over two when both
 * resolve. The actual surface text is preserved; the dictionary handles whitespace
 * normalization internally.
 *
 * <h3>Best path</h3>
 * {@link #bestPath()} runs Viterbi DP over grapheme positions. The optimal sequence
 * of non-overlapping spans covering the input is returned. Whitespace between
 * spans is free (no score contribution).
 *
 * <h3>Ambiguity</h3>
 * {@link #topPaths(int)} returns the top-K highest-scoring paths for surfacing
 * disambiguation choices to the user. {@link #spansStartingAt(int)} returns all
 * candidate spans starting at a given grapheme position, useful for showing
 * inline alternatives.
 */
public final class TokenLattice {

    /** Maximum number of words combined into a single multi-word window. */
    public static final int MAX_WINDOW = 5;

    /** Default scores for different span kinds. */
    private static final Decimal LITERAL_SCORE = Decimal.parse("0.9");
    private static final Decimal UNRESOLVED_SCORE = Decimal.parse("0.1");
    /** Score added per extra word when a multi-word window resolves. */
    private static final double MULTI_WORD_BONUS_PER_WORD = 0.1;

    @Getter
    private final String rawText;

    @Getter
    private final ULocale locale;

    /** All candidate spans, indexed by start grapheme position. */
    private final Map<Integer, List<TokenSpan>> spansByStart;

    /** Total number of grapheme clusters in the input. */
    @Getter
    private final int graphemeLength;

    /** Cached best path. */
    private List<TokenSpan> cachedBestPath;

    private TokenLattice(String rawText, ULocale locale,
                         Map<Integer, List<TokenSpan>> spansByStart, int graphemeLength) {
        this.rawText = rawText;
        this.locale = locale;
        this.spansByStart = spansByStart;
        this.graphemeLength = graphemeLength;
    }

    // ==================================================================================
    // Build
    // ==================================================================================

    /**
     * Tokenize raw text into a lattice using locale-aware ICU boundaries.
     *
     * @param rawText input text
     * @param locale  ULocale for grapheme + word break iteration
     * @param lookup  dictionary lookup function (typically {@code librarian::lookupToken})
     * @return the built lattice
     */
    public static TokenLattice build(String rawText, ULocale locale,
                                     Function<String, List<Posting>> lookup) {
        if (rawText == null || rawText.isEmpty()) {
            return new TokenLattice(rawText == null ? "" : rawText, locale, Map.of(), 0);
        }

        int[] graphemeBoundaries = computeGraphemeBoundaries(rawText, locale);
        int graphemeLength = graphemeBoundaries.length - 1;

        List<WordSegment> words = computeWordSegments(rawText, locale, graphemeBoundaries);

        Map<Integer, List<TokenSpan>> spansByStart = new HashMap<>();

        // Phase 1: single-word spans (each non-whitespace word tried against dictionary;
        // failed words tested as literal/unresolved). Whitespace segments are silently
        // discarded — whitespace plays no semantic role in any language we model, and the
        // Viterbi DP carries scores forward across gaps where no span starts.
        for (WordSegment word : words) {
            if (word.isSeparator) continue;
            generateSpansForRange(rawText, word.graphemeStart, word.graphemeEnd,
                    word.surfaceText, lookup, spansByStart, /* multiWordCount */ 1);
        }

        // Phase 2: multi-word windows (N=2..MAX_WINDOW combining adjacent content words)
        List<WordSegment> contentWords = new ArrayList<>();
        for (WordSegment w : words) {
            if (!w.isSeparator) contentWords.add(w);
        }
        for (int i = 0; i < contentWords.size(); i++) {
            for (int n = 2; n <= MAX_WINDOW && i + n - 1 < contentWords.size(); n++) {
                WordSegment first = contentWords.get(i);
                WordSegment last = contentWords.get(i + n - 1);
                String surface = rawText.substring(
                        graphemeBoundaries[first.graphemeStart],
                        graphemeBoundaries[last.graphemeEnd]);
                generateSpansForRange(rawText, first.graphemeStart, last.graphemeEnd,
                        surface, lookup, spansByStart, n);
            }
        }

        return new TokenLattice(rawText, locale, spansByStart, graphemeLength);
    }

    private static void generateSpansForRange(
            String rawText, int graphemeStart, int graphemeEnd, String surfaceText,
            Function<String, List<Posting>> lookup,
            Map<Integer, List<TokenSpan>> spansByStart, int wordCount) {

        TextSpan span = new TextSpan(graphemeStart, graphemeEnd);

        // Dictionary lookup
        String normalized = normalize(surfaceText);
        List<Posting> postings = lookup.apply(normalized);
        if (postings != null && !postings.isEmpty()) {
            List<Posting> sorted = sortedByWeight(postings);
            Decimal baseScore = sorted.get(0).weight();
            Decimal score = wordCount > 1
                    ? plusBonus(baseScore, MULTI_WORD_BONUS_PER_WORD * (wordCount - 1))
                    : baseScore;
            addSpan(spansByStart, new TokenSpan(span, surfaceText, sorted, Kind.WORD, score));
        }

        // Single-word spans also get literal/unresolved fallback when dictionary missed.
        // Multi-word windows skip this — multi-word literals make no sense.
        if (wordCount == 1 && (postings == null || postings.isEmpty())) {
            if (isNumericLiteral(surfaceText)) {
                addSpan(spansByStart, new TokenSpan(span, surfaceText, List.of(), Kind.LITERAL, LITERAL_SCORE));
            } else if (isQuotedStringLiteral(surfaceText)) {
                addSpan(spansByStart, new TokenSpan(span, surfaceText, List.of(), Kind.LITERAL, LITERAL_SCORE));
            } else {
                addSpan(spansByStart, new TokenSpan(span, surfaceText, List.of(), Kind.UNRESOLVED, UNRESOLVED_SCORE));
            }
        }
    }

    private static void addSpan(Map<Integer, List<TokenSpan>> spansByStart, TokenSpan span) {
        spansByStart.computeIfAbsent(span.span().start(), k -> new ArrayList<>()).add(span);
    }

    // ==================================================================================
    // Best path (Viterbi)
    // ==================================================================================

    /**
     * Best path through the lattice via Viterbi: highest-scoring sequence of
     * non-overlapping spans covering the input.
     *
     * @return ordered list of spans; empty for empty input
     */
    public List<TokenSpan> bestPath() {
        if (cachedBestPath != null) return cachedBestPath;
        if (graphemeLength == 0) {
            cachedBestPath = List.of();
            return cachedBestPath;
        }

        double[] bestScore = new double[graphemeLength + 1];
        TokenSpan[] bestPrev = new TokenSpan[graphemeLength + 1];
        for (int i = 1; i <= graphemeLength; i++) bestScore[i] = Double.NEGATIVE_INFINITY;

        for (int pos = 0; pos <= graphemeLength; pos++) {
            // No-op grapheme advance (covers gaps where no span starts but path can still progress)
            if (pos < graphemeLength && bestScore[pos] > bestScore[pos + 1]) {
                bestScore[pos + 1] = bestScore[pos];
                bestPrev[pos + 1] = bestPrev[pos];
            }
            List<TokenSpan> spans = spansByStart.get(pos);
            if (spans == null) continue;
            for (TokenSpan span : spans) {
                double candidate = bestScore[pos] + span.score().toDouble();
                int endPos = span.span().end();
                if (candidate > bestScore[endPos]) {
                    bestScore[endPos] = candidate;
                    bestPrev[endPos] = span;
                }
            }
        }

        // Reconstruct path
        List<TokenSpan> path = new ArrayList<>();
        int pos = graphemeLength;
        while (pos > 0) {
            TokenSpan span = bestPrev[pos];
            if (span == null) {
                pos--;
                continue;
            }
            path.add(span);
            pos = span.span().start();
        }
        Collections.reverse(path);
        cachedBestPath = List.copyOf(path);
        return cachedBestPath;
    }

    // ==================================================================================
    // Top-K paths (K-best Viterbi)
    // ==================================================================================

    /**
     * Top K highest-scoring paths through the lattice for ambiguity surfacing.
     *
     * <p>Implementation: at each grapheme position, maintain a priority queue of
     * the K best partial paths reaching that position. For each span starting at
     * the position, extend each partial path. K paths reaching the end are returned
     * in score-descending order.
     *
     * @param k maximum number of paths to return
     * @return paths ordered by score (best first); fewer than K if the lattice doesn't admit that many
     */
    public List<List<TokenSpan>> topPaths(int k) {
        if (k <= 0 || graphemeLength == 0) return List.of();

        @SuppressWarnings("unchecked")
        List<PartialPath>[] paths = new List[graphemeLength + 1];
        for (int i = 0; i <= graphemeLength; i++) paths[i] = new ArrayList<>();
        paths[0].add(new PartialPath(0.0, null, null));

        for (int pos = 0; pos <= graphemeLength; pos++) {
            if (paths[pos].isEmpty()) continue;
            // No-op grapheme advance: paths can carry forward through positions with no span
            if (pos < graphemeLength) {
                for (PartialPath p : paths[pos]) {
                    paths[pos + 1].add(p);
                }
                trimToTopK(paths[pos + 1], k);
            }
            List<TokenSpan> startingSpans = spansByStart.get(pos);
            if (startingSpans == null) continue;
            for (TokenSpan span : startingSpans) {
                double scoreDelta = span.score().toDouble();
                int endPos = span.span().end();
                for (PartialPath p : paths[pos]) {
                    paths[endPos].add(new PartialPath(p.score + scoreDelta, span, p));
                }
                trimToTopK(paths[endPos], k);
            }
        }

        List<PartialPath> finals = paths[graphemeLength];
        finals.sort((a, b) -> Double.compare(b.score, a.score));
        List<List<TokenSpan>> result = new ArrayList<>(finals.size());
        for (PartialPath p : finals) {
            result.add(p.toPath());
            if (result.size() == k) break;
        }
        return result;
    }

    private static void trimToTopK(List<PartialPath> list, int k) {
        if (list.size() <= k) return;
        list.sort((a, b) -> Double.compare(b.score, a.score));
        while (list.size() > k) list.remove(list.size() - 1);
    }

    private static final class PartialPath {
        final double score;
        final TokenSpan span;
        final PartialPath prev;

        PartialPath(double score, TokenSpan span, PartialPath prev) {
            this.score = score;
            this.span = span;
            this.prev = prev;
        }

        List<TokenSpan> toPath() {
            List<TokenSpan> out = new ArrayList<>();
            for (PartialPath p = this; p != null; p = p.prev) {
                if (p.span != null) out.add(p.span);
            }
            Collections.reverse(out);
            return out;
        }
    }

    // ==================================================================================
    // Position queries
    // ==================================================================================

    /** All candidate spans starting at the given grapheme position. */
    public List<TokenSpan> spansStartingAt(int graphemeIndex) {
        List<TokenSpan> spans = spansByStart.get(graphemeIndex);
        return spans == null ? List.of() : List.copyOf(spans);
    }

    // ==================================================================================
    // Helpers — boundaries, normalization, literal detection
    // ==================================================================================

    private static int[] computeGraphemeBoundaries(String text, ULocale locale) {
        BreakIterator iter = BreakIterator.getCharacterInstance(locale);
        iter.setText(text);
        List<Integer> bounds = new ArrayList<>();
        bounds.add(iter.first());
        int next;
        while ((next = iter.next()) != BreakIterator.DONE) bounds.add(next);
        int[] out = new int[bounds.size()];
        for (int i = 0; i < out.length; i++) out[i] = bounds.get(i);
        return out;
    }

    private static List<WordSegment> computeWordSegments(String text, ULocale locale, int[] graphemeBoundaries) {
        BreakIterator iter = BreakIterator.getWordInstance(locale);
        iter.setText(text);
        List<WordSegment> segments = new ArrayList<>();
        int prev = iter.first();
        int cur;
        while ((cur = iter.next()) != BreakIterator.DONE) {
            String segText = text.substring(prev, cur);
            int graphemeStart = charToGrapheme(prev, graphemeBoundaries);
            int graphemeEnd = charToGrapheme(cur, graphemeBoundaries);
            boolean isSep = isSeparator(segText);
            segments.add(new WordSegment(graphemeStart, graphemeEnd, segText, isSep));
            prev = cur;
        }
        return segments;
    }

    private static int charToGrapheme(int charPos, int[] graphemeBoundaries) {
        // Binary search for charPos in graphemeBoundaries; result is the grapheme index
        int lo = 0;
        int hi = graphemeBoundaries.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = graphemeBoundaries[mid];
            if (v == charPos) return mid;
            if (v < charPos) lo = mid + 1;
            else hi = mid - 1;
        }
        return lo; // closest grapheme index >= charPos
    }

    private static boolean isSeparator(String text) {
        if (text.isEmpty()) return true;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    /**
     * Normalize a token for dictionary lookup: lowercase via locale-insensitive
     * {@link Locale#ROOT} (case-insensitive matching for ASCII) plus trim.
     * Future: NFC normalization for combined characters.
     */
    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).strip();
    }

    private static List<Posting> sortedByWeight(List<Posting> postings) {
        List<Posting> sorted = new ArrayList<>(postings);
        sorted.sort((a, b) -> Double.compare(b.weight().toDouble(), a.weight().toDouble()));
        return List.copyOf(sorted);
    }

    private static Decimal plusBonus(Decimal base, double bonus) {
        // Convert via double — adequate for ranking; Decimal precision preserved on the original posting
        return Decimal.parse(Double.toString(base.toDouble() + bonus));
    }

    private static boolean isNumericLiteral(String text) {
        String stripped = text.strip();
        if (stripped.isEmpty()) return false;
        try {
            Decimal.parse(stripped);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isQuotedStringLiteral(String text) {
        String stripped = text.strip();
        return stripped.length() >= 2
                && (stripped.charAt(0) == '"' && stripped.charAt(stripped.length() - 1) == '"'
                || stripped.charAt(0) == '\'' && stripped.charAt(stripped.length() - 1) == '\'');
    }

    // ==================================================================================
    // Inner types
    // ==================================================================================

    /**
     * A candidate token span in the lattice.
     *
     * <p>Carries grapheme-cluster offsets, the verbatim surface text, dictionary
     * postings (sorted by weight descending; empty for non-dictionary spans), the
     * span kind, and a score used by Viterbi.
     */
    @Value @With
    public static class TokenSpan {
        TextSpan span;
        String surfaceText;
        List<Posting> postings;
        Kind kind;
        Decimal score;

        public boolean isResolved() {
            return !postings.isEmpty();
        }

        public boolean isAmbiguous() {
            if (postings.size() <= 1) return false;
            double best = postings.get(0).weight().toDouble();
            double second = postings.get(1).weight().toDouble();
            return second >= best * 0.8;
        }

        public Posting bestPosting() {
            return postings.isEmpty() ? null : postings.get(0);
        }
    }

    /** Classification of a token span. */
    public enum Kind {
        /** Resolved via dictionary lookup. */
        WORD,
        /** Recognized as a number, quoted string, or other literal. */
        LITERAL,
        /** Neither a dictionary hit nor a recognized literal. */
        UNRESOLVED
    }

    /** Internal type for word segment iteration. */
    private static final class WordSegment {
        final int graphemeStart;
        final int graphemeEnd;
        final String surfaceText;
        final boolean isSeparator;

        WordSegment(int graphemeStart, int graphemeEnd, String surfaceText, boolean isSeparator) {
            this.graphemeStart = graphemeStart;
            this.graphemeEnd = graphemeEnd;
            this.surfaceText = surfaceText;
            this.isSeparator = isSeparator;
        }
    }
}
