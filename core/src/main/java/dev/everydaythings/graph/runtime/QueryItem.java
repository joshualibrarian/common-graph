package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.language.Sememe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A query item — an incomplete frame pattern with accumulated results.
 *
 * <p>A QueryItem stores:
 * <ul>
 *   <li>A QUERY frame holding the search pattern as TERM bindings</li>
 *   <li>QUERY_RESULT frames for each matched item (added by {@link #run})</li>
 * </ul>
 *
 * <p>Results naturally deduplicate: the same match on two runs produces the
 * same FrameBody hash, so no duplicate frames accumulate. Commit a version
 * to snapshot results, clear and re-run for a fresh set.
 */
@Implements(QueryItem.KEY)
public class QueryItem extends Item {

    public static final String KEY = "cg.sememe:query";
    public static final ItemID IID = ItemID.fromString(KEY);

    // ==================================================================================
    // Seed predicates
    // ==================================================================================

    /** The QUERY predicate — the pattern itself. */
    @ItemSeed(key = Query.KEY, slots = {Term.KEY})
    public static class Query {
        public static final String KEY = "cg.predicate:query";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a pattern of items to search for by frame co-occurrence";
    }

    /** The QUERY_RESULT predicate — a single result. */
    @ItemSeed(key = Result.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Result.KEY})
    public static class Result {
        public static final String KEY = "cg.predicate:query-result";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a single item matched by a query pattern";
    }

    /** The TERM role — a search term in a query pattern. */
    @ItemSeed(key = Term.KEY)
    public static class Term {
        public static final String KEY = "cg.role:term";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a search term in a query pattern";
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /** Seed constructor. */
    public QueryItem(ItemID iid) {
        super(iid);
    }

    /** Fresh query from resolved terms. */
    public QueryItem(Librarian librarian, List<Eval.ResolvedToken> terms) {
        super(librarian);
        storeQueryFrame(terms);
    }

    /** Hydrate from manifest. */
    public QueryItem(Librarian librarian, dev.everydaythings.graph.item.Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // Query Execution
    // ==================================================================================

    /**
     * Execute the query — find items matching the pattern and store results.
     *
     * <p>Reads the TERM bindings from the QUERY frame, calls
     * {@link dev.everydaythings.graph.library.Library#queryItems}, and
     * stores a QUERY_RESULT frame for each match. Duplicate results
     * deduplicate naturally via body hash.
     *
     * @return the set of matched ItemIDs
     */
    public Set<ItemID> run() {
        Set<ItemID> pattern = extractPattern();
        if (pattern.isEmpty()) return Set.of();

        Set<ItemID> results = librarian.library().queryItems(pattern);

        // Store a QUERY_RESULT frame for each match
        for (ItemID resultId : results) {
            FrameBody resultFrame = new FrameBody(Result.IID, List.of(
                    FrameBody.homeBinding(iid()),
                    new Binding(ThematicRole.Result.IID, BindingTarget.iid(resultId), true, true)
            ));
            librarian.storeFrame(resultFrame);
        }

        return results;
    }

    /**
     * Extract the pattern ItemIDs from the QUERY frame's TERM bindings.
     */
    public Set<ItemID> extractPattern() {
        Set<ItemID> pattern = new LinkedHashSet<>();
        // Scan frames for the QUERY predicate
        if (frames() != null) {
            for (var frame : frames()) {
                if (frame.body() != null && Query.IID.equals(frame.body().predicate())) {
                    for (Binding b : frame.body().frameBindings()) {
                        if (Term.IID.equals(b.role())) {
                            ItemID termId = b.targetId();
                            if (termId != null) pattern.add(termId);
                        }
                    }
                }
            }
        }
        return pattern;
    }

    // ==================================================================================
    // Internals
    // ==================================================================================

    /**
     * Build and store the QUERY frame from resolved tokens.
     */
    private void storeQueryFrame(List<Eval.ResolvedToken> terms) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(FrameBody.homeBinding(iid()));

        for (Eval.ResolvedToken token : terms) {
            if (token instanceof Eval.ResolvedToken.Link link) {
                bindings.add(new Binding(Term.IID, BindingTarget.iid(link.iid()), true, true));
            } else if (token instanceof Eval.ResolvedToken.Literal lit) {
                bindings.add(new Binding(Term.IID, Literal.ofText(lit.value().toString()), true, true));
            }
            // Unresolved tokens are skipped — they couldn't be resolved to anything
        }

        FrameBody queryFrame = new FrameBody(Query.IID, bindings);
        librarian.storeFrame(queryFrame);
    }

    @Override
    public String displayToken() {
        Set<ItemID> pattern = extractPattern();
        if (pattern.isEmpty()) return "query";
        return "query (" + pattern.size() + " terms)";
    }
}
