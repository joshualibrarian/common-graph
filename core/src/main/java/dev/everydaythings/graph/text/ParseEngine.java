package dev.everydaythings.graph.text;

import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.library.index.TokenPosting;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.AnchorTable.TokenAnchor;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The consensus parse engine.
 *
 * <p>Given an orchestrator item, raw input, and operational parameters, runs the
 * full pipeline: tokenize → resolve anchors → iterate consensus rounds → return
 * the settled FrameMap.
 *
 * <h3>High-level flow</h3>
 * <pre>
 * 1. Tokenize input via {@link TokenLattice} using the active Language's locale
 *    and the librarian's token dictionary.
 *
 * 2. Build the initial {@link AnchorTable}: for each Posting in the lattice's
 *    best path, fetch the item from the librarian; if its class overrides
 *    {@link Item#parse(ParseContext)} (i.e., it's an active participant rather
 *    than pure data), wrap it as a TokenAnchor.
 *
 * 3. Initialize the draft FrameMap with the input text.
 *
 * 4. Round loop: build {@link ParseContext}, call {@code parse(ctx)} on every
 *    participant (orchestrator + Languages-from-params + token-anchored), pass
 *    deltas to {@code orchestrator.merge}, check fixpoint.
 *
 * 5. Return the settled FrameMap (or current best if {@link #MAX_ROUNDS} hit).
 * </pre>
 *
 * <h3>v1 simplifications</h3>
 * <ul>
 *   <li>No cross-keystroke session state — each call is independent. User
 *       clarifications don't survive across calls. (Wired with interactive UI later.)</li>
 *   <li>No User-as-participant — same reason.</li>
 *   <li>No type-filtered consultation — orchestrator can't broadcast outward when
 *       underdetermined. (Future engine feature.)</li>
 *   <li>Participant set computed once per call, not per round (anchors are fixed
 *       within a single parse).</li>
 *   <li>Fixpoint = exact equality of consecutive drafts (rather than the
 *       "two rounds running with no change" pattern from the design — close
 *       enough for v1, refinement when needed).</li>
 * </ul>
 */
public final class ParseEngine {

    /** Hard cap on consensus rounds — safety against runaway oscillation. */
    public static final int MAX_ROUNDS = 16;

    /** Class → does this class override {@link Item#parse(ParseContext)}? Cached for hot paths. */
    private static final Map<Class<?>, Boolean> OVERRIDES_PARSE_CACHE = new ConcurrentHashMap<>();

    private ParseEngine() {}

    /**
     * Run the parse engine.
     *
     * @param orchestrator the item that owns the prompt receiving {@code input}
     * @param input        raw text input
     * @param params       operational parameters (language stack, mode, verbosity, etc.)
     * @return the settled FrameMap
     */
    public static FrameMap run(Item orchestrator, String input, ParseParams params) {
        if (input == null || input.isEmpty()) {
            return FrameMap.empty();
        }

        Librarian librarian = orchestrator.librarian();
        ULocale locale = activeLocale(params, librarian);

        Function<String, List<TokenPosting>> lookup = librarian != null
                ? librarian::lookupToken
                : (s -> List.of());
        TokenLattice lattice = TokenLattice.build(input, locale, lookup);

        AnchorTable anchors = buildInitialAnchors(lattice, librarian);
        FrameMap draft = FrameMap.empty().withText(input);
        List<Item> participants = gatherParticipants(orchestrator, params, anchors, librarian);
        List<TokenSpan> tokens = lattice.bestPath();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            ParseContext ctx = new ParseContext(draft, anchors, orchestrator, tokens);

            List<FrameMap> deltas = new ArrayList<>(participants.size());
            for (Item participant : participants) {
                FrameMap delta = participant.parse(ctx);
                if (delta != null) deltas.add(delta);
            }

            FrameMap newDraft = orchestrator.merge(draft, deltas);
            if (newDraft.equals(draft)) {
                return newDraft;
            }
            draft = newDraft;
        }

        return draft;
    }

    /**
     * Locale of the active Language at the top of the stack, falling back to
     * {@link ULocale#ROOT} when no Language is in scope or it isn't fetchable.
     */
    private static ULocale activeLocale(ParseParams params, Librarian librarian) {
        if (params == null || params.languageStack() == null || params.languageStack().isEmpty()) {
            return ULocale.ROOT;
        }
        if (librarian == null) {
            return ULocale.ROOT;
        }
        ItemRef topRef = params.languageStack().get(0);
        return librarian.fetchItem(topRef.iid())
                .filter(item -> item instanceof Language)
                .map(item -> ((Language) item).locale())
                .orElse(ULocale.ROOT);
    }

    /**
     * Walk the lattice's best path; for each posting whose target is an active
     * participant (class overrides {@code parse(ParseContext)}), record the spans
     * where it appeared and wrap it as a TokenAnchor.
     */
    private static AnchorTable buildInitialAnchors(TokenLattice lattice, Librarian librarian) {
        if (librarian == null) return AnchorTable.empty();

        Map<ItemRef, List<TextSpan>> spansByItem = new LinkedHashMap<>();
        for (TokenSpan ts : lattice.bestPath()) {
            for (TokenPosting p : ts.postings()) {
                ItemRef iid = p.target();
                if (iid == null) continue;
                spansByItem.computeIfAbsent(iid, k -> new ArrayList<>()).add(ts.span());
            }
        }

        List<TokenAnchor> tokenAnchors = new ArrayList<>();
        for (Map.Entry<ItemRef, List<TextSpan>> entry : spansByItem.entrySet()) {
            Optional<Item> opt = librarian.fetchItem(entry.getKey());
            if (opt.isEmpty()) continue;
            Item item = opt.get();
            if (!classOverridesParse(item.getClass())) continue;
            tokenAnchors.add(new TokenAnchor(List.copyOf(entry.getValue()), item));
        }

        return new AnchorTable(List.copyOf(tokenAnchors), List.of());
    }

    /**
     * Build the participant list: orchestrator, Languages from params, token-anchored
     * sememes. Deduplicated; insertion-order preserved.
     */
    private static List<Item> gatherParticipants(Item orchestrator, ParseParams params,
                                                 AnchorTable anchors, Librarian librarian) {
        Set<Item> set = new LinkedHashSet<>();
        set.add(orchestrator);

        if (librarian != null && params != null && params.languageStack() != null) {
            for (ItemRef ref : params.languageStack()) {
                librarian.fetchItem(ref.iid()).ifPresent(set::add);
            }
        }

        for (TokenAnchor ta : anchors.tokenAnchors()) {
            set.add(ta.participant());
        }

        return List.copyOf(set);
    }

    private static boolean classOverridesParse(Class<?> clazz) {
        return OVERRIDES_PARSE_CACHE.computeIfAbsent(clazz, ParseEngine::computeOverridesParse);
    }

    private static boolean computeOverridesParse(Class<?> clazz) {
        try {
            Method m = clazz.getMethod("parse", ParseContext.class);
            return m.getDeclaringClass() != Item.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
