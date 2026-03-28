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
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SemanticFrame;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A query item — an incomplete frame pattern with accumulated results.
 *
 * <p>A QueryItem stores:
 * <ul>
 *   <li>A QUERY frame holding the search pattern (terms or structured predicate+roles)</li>
 *   <li>QUERY_RESULT frames for each matched item (added by {@link #run})</li>
 * </ul>
 *
 * <p>Two query strategies:
 * <ul>
 *   <li><b>Unstructured</b> — bag of term IDs, intersection via {@link Library#queryItems}</li>
 *   <li><b>Structured</b> — predicate + filled role bindings, targeted index query
 *       via {@link Library#byItemPredicate} with role filtering, extracting items
 *       from unbound role positions</li>
 * </ul>
 *
 * <p>Results naturally deduplicate: the same match on two runs produces the
 * same FrameBody hash, so no duplicate frames accumulate. Commit a version
 * to snapshot results, clear and re-run for a fresh set.
 */
@Implements(QueryItem.KEY)
@ItemSeed(key = QueryItem.KEY)
@Scene.Root
public class QueryItem extends Item {

    public static final String KEY = "cg.sememe:query";
    public static final ItemID IID = ItemID.fromString(KEY);

    // ==================================================================================
    // Type seed — the query sememe
    // ==================================================================================

    @ItemSeed(key = QueryType.KEY)
    public static class QueryType {
        public static final String KEY = QueryItem.KEY;

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a query; a search for items matching a pattern";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY,
                                                 PartOfSpeech.Noun.KEY,
                                                 GrammaticalFeature.Lemma.KEY}))
        static final String word = "query";

        // EXPECTS — declaration order is salience order for handle disambiguation.
        // QUERY frame is most identifying (what was searched for).
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {Query.KEY}))
        static final ItemID expectQuery = ItemID.fromString(Query.KEY);

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {Result.KEY}))
        static final ItemID expectResult = ItemID.fromString(Result.KEY);
    }

    // ==================================================================================
    // Seed predicates
    // ==================================================================================

    /** The QUERY predicate — the pattern itself. */
    @ItemSeed(key = Query.KEY)
    public static class Query {
        public static final String KEY = "cg.predicate:query";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a pattern of items to search for by frame co-occurrence";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {Term.KEY}))
        static final ItemID expectTerm = Term.IID;
    }

    /** The QUERY_RESULT predicate — a single result. */
    @ItemSeed(key = Result.KEY)
    public static class Result {
        public static final String KEY = "cg.predicate:query-result";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a single item matched by a query pattern";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.Result.KEY}))
        static final ItemID expectResult = ThematicRole.Result.IID;
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
    // State
    // ==================================================================================

    /** Structured query frame (transient — only set when constructed with a SemanticFrame). */
    private transient SemanticFrame semanticFrame;

    /** Cached pattern terms for immediate execution (transient — set at construction). */
    private transient Set<ItemID> patternTerms;

    /** Cached result items for rendering (transient — populated by {@link #run}). */
    private transient List<Item> resultItems = List.of();

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /** Seed constructor. */
    public QueryItem(ItemID iid) {
        super(iid);
    }

    /** Fresh query from resolved terms (unstructured intersection). */
    public QueryItem(Librarian librarian, List<Eval.ResolvedToken> terms) {
        super(librarian);
        this.patternTerms = new LinkedHashSet<>();
        for (Eval.ResolvedToken token : terms) {
            if (token instanceof Eval.ResolvedToken.Link link) {
                patternTerms.add(link.iid());
            }
        }
        storeQueryFrame(terms);
    }

    /** Fresh query from an incomplete semantic frame (structured query). */
    public QueryItem(Librarian librarian, SemanticFrame frame) {
        super(librarian);
        this.semanticFrame = frame;
        this.patternTerms = new LinkedHashSet<>();
        patternTerms.add(frame.verb().iid());
        for (var entry : frame.bindings().entrySet()) {
            ItemID valueId = extractItemId(entry.getValue());
            if (valueId != null) patternTerms.add(valueId);
        }
        storeStructuredQueryFrame(frame);
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
     * <p>If constructed with a {@link SemanticFrame}, runs a structured query
     * using predicate+role index lookups. Otherwise falls back to unstructured
     * intersection via {@link Library#queryItems}.
     *
     * @return the set of matched ItemIDs
     */
    public Set<ItemID> run() {
        if (semanticFrame != null && semanticFrame.verb() != null) {
            return runStructured();
        }
        return runUnstructured();
    }

    /**
     * Run a structured query — predicate + filled roles → targeted index query.
     *
     * <p>For each filled binding, queries frames via {@link Library#byItemPredicate}
     * and filters to frames where the binding actually fills the expected role.
     * Then extracts items from the unbound role positions as results.
     */
    private Set<ItemID> runStructured() {
        Library library = librarian.library();
        ItemID predicateId = semanticFrame.verb().iid();
        Map<ItemID, Object> filledBindings = semanticFrame.bindings();
        List<ItemID> unboundRoles = semanticFrame.unboundRoles();

        Set<ItemID> results;

        if (filledBindings.isEmpty()) {
            // No filled bindings — query by predicate only, extract from unbound roles
            results = library.byPredicate(predicateId)
                    .flatMap(body -> extractUnboundRoleTargets(body, unboundRoles))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            // Use filled bindings for targeted index queries
            results = null;
            for (var entry : filledBindings.entrySet()) {
                ItemID role = entry.getKey();
                Object value = entry.getValue();
                ItemID valueId = extractItemId(value);
                if (valueId == null) continue;

                // Query: frames involving this item under this predicate
                Set<ItemID> roleResults = library.byItemPredicate(valueId, predicateId)
                        .filter(body -> bindingMatchesRole(body, valueId, role))
                        .flatMap(body -> extractUnboundRoleTargets(body, unboundRoles))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (results == null) {
                    results = roleResults;
                } else {
                    results.retainAll(roleResults);
                }
                if (results.isEmpty()) return storeResults(Set.of());
            }
        }

        if (results == null) results = Set.of();
        return storeResults(results);
    }

    /**
     * Run an unstructured query — flat intersection of term co-occurrence.
     */
    private Set<ItemID> runUnstructured() {
        Set<ItemID> pattern = extractPattern();
        if (pattern.isEmpty()) return Set.of();
        Set<ItemID> results = librarian.library().queryItems(pattern);
        return storeResults(results);
    }

    /**
     * Run a union of multiple structured queries (for sibling predicates).
     *
     * @param queries the individual query items to union
     * @return the merged set of matched ItemIDs
     */
    public static Set<ItemID> runUnion(List<QueryItem> queries) {
        Set<ItemID> merged = new LinkedHashSet<>();
        for (QueryItem q : queries) {
            merged.addAll(q.run());
        }
        return merged;
    }

    // ==================================================================================
    // Pattern Extraction
    // ==================================================================================

    /**
     * Extract the pattern ItemIDs — from transient state if available,
     * otherwise from stored QUERY frame TERM bindings.
     */
    public Set<ItemID> extractPattern() {
        // Use transient pattern if available (freshly constructed query)
        if (patternTerms != null && !patternTerms.isEmpty()) {
            return patternTerms;
        }
        // Fall back to reading from stored frames (hydrated from manifest)
        Set<ItemID> pattern = new LinkedHashSet<>();
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
     * Store QUERY_RESULT frames for each matched item.
     */
    private Set<ItemID> storeResults(Set<ItemID> results) {
        for (ItemID resultId : results) {
            FrameBody resultFrame = new FrameBody(Result.IID, List.of(
                    FrameBody.homeBinding(iid()),
                    new Binding(ThematicRole.Result.IID, BindingTarget.iid(resultId), true, true)
            ));
            endorseFrame(resultFrame);
        }
        return results;
    }

    /**
     * Build and store the QUERY frame from resolved tokens (unstructured).
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
        }

        FrameBody queryFrame = new FrameBody(Query.IID, bindings);
        endorseFrame(queryFrame);
    }

    /**
     * Build and store the QUERY frame from a structured semantic frame.
     *
     * <p>Stores the predicate as a TERM binding plus each filled role's value.
     */
    private void storeStructuredQueryFrame(SemanticFrame frame) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(FrameBody.homeBinding(iid()));

        // The predicate itself is a term
        bindings.add(new Binding(Term.IID, BindingTarget.iid(frame.verb().iid()), true, true));

        // Each filled binding value is a term
        for (var entry : frame.bindings().entrySet()) {
            ItemID valueId = extractItemId(entry.getValue());
            if (valueId != null) {
                bindings.add(new Binding(Term.IID, BindingTarget.iid(valueId), true, true));
            }
        }

        FrameBody queryFrame = new FrameBody(Query.IID, bindings);
        endorseFrame(queryFrame);
    }

    /**
     * Check if a specific item fills a specific role in a frame body.
     */
    private static boolean bindingMatchesRole(FrameBody body, ItemID itemId, ItemID role) {
        for (Binding b : body.frameBindings()) {
            if (role.equals(b.role()) && itemId.equals(b.targetId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract items from unbound role positions in a frame body.
     */
    private static Stream<ItemID> extractUnboundRoleTargets(FrameBody body, List<ItemID> unboundRoles) {
        if (unboundRoles.isEmpty()) {
            // No specific unbound roles — return the home item
            ItemID home = body.homeId();
            return home != null ? Stream.of(home) : Stream.empty();
        }
        return body.frameBindings().stream()
                .filter(b -> unboundRoles.contains(b.role()))
                .map(Binding::targetId)
                .filter(Objects::nonNull);
    }

    /**
     * Extract an ItemID from a binding value (Item or ItemID).
     */
    private static ItemID extractItemId(Object value) {
        if (value instanceof Item item) return item.iid();
        if (value instanceof ItemID iid) return iid;
        return null;
    }

    /**
     * Set the cached result items for rendering.
     */
    public void resultItems(List<Item> items) {
        this.resultItems = items != null ? items : List.of();
    }

    /**
     * Get the cached result items.
     */
    public List<Item> resultItems() {
        return resultItems;
    }

    /**
     * Extract result ItemIDs from stored QUERY_RESULT frames.
     */
    public Set<ItemID> extractResults() {
        Set<ItemID> results = new LinkedHashSet<>();
        if (frames() != null) {
            for (var frame : frames()) {
                if (frame.body() != null && Result.IID.equals(frame.body().predicate())) {
                    for (Binding b : frame.body().frameBindings()) {
                        if (ThematicRole.Result.IID.equals(b.role())) {
                            ItemID resultId = b.targetId();
                            if (resultId != null) results.add(resultId);
                        }
                    }
                }
            }
        }
        return results;
    }

    // ==================================================================================
    // Scene — visual representation
    // ==================================================================================

    @Scene.Handle
    public Node handle() {
        int count = resultItems.size();
        Container h = Container.horizontal().gap("0.5em");
        h.add(Body.ofGlyph("\uD83D\uDD0D")); // magnifying glass
        h.add(Text.of(count > 0 ? count + " results" : "query"));
        return h;
    }

    @Scene.Container(order = 0, direction = Scene.Direction.VERTICAL, id = "query-results",
            gap = "0.3em", padding = "0.5em")
    public Node content() {
        Container root = Container.vertical().gap("0.3em");

        // Header
        int count = resultItems.size();
        Text header = Text.of(count + (count == 1 ? " result" : " results"));
        header.fontWeight("bold");
        root.add(header);

        if (resultItems.isEmpty()) {
            root.add(Text.of("No matches found.").foreground("#888888"));
            return root;
        }

        // Result list — one handle per result item
        for (Item item : resultItems) {
            Container row = Container.horizontal().gap("0.5em");
            String emoji = item.emoji();
            row.add(Body.ofGlyph(emoji != null ? emoji : ""));
            row.add(Text.ofSememe(item.iid()));
            root.add(row);
        }

        return root;
    }

    @Override
    public String emoji() {
        return "\uD83D\uDD0D"; // magnifying glass
    }

    @Override
    public String displayToken() {
        int count = resultItems.size();
        if (count > 0) return "query (" + count + " results)";
        Set<ItemID> pattern = extractPattern();
        if (pattern.isEmpty()) return "query";
        return "query (" + pattern.size() + " terms)";
    }
}
