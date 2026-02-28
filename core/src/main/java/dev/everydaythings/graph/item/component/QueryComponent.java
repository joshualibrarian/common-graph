package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A relation pattern query component.
 *
 * <p>QueryComponent represents a validated S → P → O pattern that can be
 * evaluated against the graph. Each position can be:
 * <ul>
 *   <li>{@link Sememe#ANY} - matches anything (wildcard)</li>
 *   <li>{@link Sememe#WHAT} - the result we're solving for (variable)</li>
 *   <li>A concrete ItemID - a filter constraint</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>{@code
 * // All types: what is implemented by anything?
 * // * → IMPLEMENTED_BY → ?
 * QueryComponent.pattern("types", ANY, IMPLEMENTED_BY, WHAT);
 *
 * // All items of type T: what implements T?
 * // ? → IMPLEMENTED_BY → T
 * QueryComponent.pattern("instances", WHAT, IMPLEMENTED_BY, typeId);
 *
 * // All predicates from item X: how does X relate?
 * // X → ? → *
 * QueryComponent.pattern("predicates", itemId, WHAT, ANY);
 * }</pre>
 *
 * <p>This is one validated shape of expression. The general expression
 * language can contain many shapes, but this S → P → O pattern can be
 * validated and executed efficiently against the relation index.
 */
@Log4j2
@Type(value = "cg:type/relation-query", glyph = "🔍")
public class QueryComponent implements Component, Canonical {

    // Convenience references
    private static final ItemID ANY = Sememe.ANY.iid();
    private static final ItemID WHAT = Sememe.WHAT.iid();

    // ==================================================================================
    // Pattern Fields
    // ==================================================================================

    /** Display name for this query. */
    private transient String queryName;

    @Getter
    @Canonical.Canon(order = 0)
    private final ItemID subjectPattern;

    @Getter
    @Canonical.Canon(order = 1)
    private final ItemID predicatePattern;

    @Getter
    @Canonical.Canon(order = 2)
    private final ItemID objectPattern;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    private QueryComponent(String handle, ItemID subject, ItemID predicate, ItemID object) {
        this.queryName = handle;
        this.subjectPattern = subject;
        this.predicatePattern = predicate;
        this.objectPattern = object;
    }

    /**
     * Create a relation pattern query.
     *
     * <p>Use {@link Sememe#ANY}.iid() for wildcard positions,
     * {@link Sememe#WHAT}.iid() for result positions,
     * and concrete ItemIDs for filter constraints.
     *
     * @param handle    Component handle
     * @param subject   Subject pattern (ANY, WHAT, or concrete)
     * @param predicate Predicate pattern (ANY, WHAT, or concrete)
     * @param object    Object pattern (ANY, WHAT, or concrete)
     */
    public static QueryComponent pattern(String handle, ItemID subject, ItemID predicate, ItemID object) {
        return new QueryComponent(handle, subject, predicate, object);
    }

    // ==================================================================================
    // Convenience Factories (using Sememe constants)
    // ==================================================================================

    /**
     * Create a default empty query component (for Components.createDefault).
     * The pattern can be set later via mutation or replaced entirely.
     */
    public static QueryComponent createDefault() {
        return new QueryComponent("query", ANY, ANY, ANY);
    }

    /**
     * Find all subjects with a given predicate: ? → P → *
     */
    public static QueryComponent subjects(String handle, ItemID predicate) {
        return pattern(handle, WHAT, predicate, ANY);
    }

    /**
     * Find all objects with a given predicate: * → P → ?
     */
    public static QueryComponent objects(String handle, ItemID predicate) {
        return pattern(handle, ANY, predicate, WHAT);
    }

    /**
     * Find all predicates from a subject: S → ? → *
     */
    public static QueryComponent predicatesFrom(String handle, ItemID subject) {
        return pattern(handle, subject, WHAT, ANY);
    }

    /**
     * Find all subjects related to a specific object: ? → * → O
     */
    public static QueryComponent subjectsTo(String handle, ItemID object) {
        return pattern(handle, WHAT, ANY, object);
    }

    /**
     * Find subjects with predicate pointing to specific object: ? → P → O
     */
    public static QueryComponent subjectsWithPredicateTo(String handle, ItemID predicate, ItemID object) {
        return pattern(handle, WHAT, predicate, object);
    }

    // ==================================================================================
    // Evaluation
    // ==================================================================================

    /**
     * Evaluate this pattern against the librarian's graph.
     *
     * @param librarian The librarian providing graph access
     * @return Stream of matching ItemIDs (from WHAT positions)
     */
    public Stream<ItemID> evaluate(Librarian librarian) {
        if (librarian == null) {
            logger.warn("Cannot evaluate query without librarian");
            return Stream.empty();
        }

        Library library = librarian.library();
        return evaluatePattern(library);
    }

    private Stream<ItemID> evaluatePattern(Library library) {
        // Determine which positions have WHAT (result variable)
        boolean wantSubject = WHAT.equals(subjectPattern);
        boolean wantPredicate = WHAT.equals(predicatePattern);
        boolean wantObject = WHAT.equals(objectPattern);

        logger.info(">>> evaluatePattern: wantSubject={}, wantPredicate={}, wantObject={}",
                wantSubject, wantPredicate, wantObject);

        // Determine constraints (non-ANY, non-WHAT values)
        ItemID subjectConstraint = isConstraint(subjectPattern) ? subjectPattern : null;
        ItemID predicateConstraint = isConstraint(predicatePattern) ? predicatePattern : null;
        ItemID objectConstraint = isConstraint(objectPattern) ? objectPattern : null;

        logger.info(">>> constraints: subject={}, predicate={}, object={}",
                subjectConstraint, predicateConstraint, objectConstraint);

        // Choose the most efficient query strategy based on constraints
        Stream<Relation> relations = queryRelations(library, subjectConstraint, predicateConstraint, objectConstraint);

        // Extract and return the WHAT position(s)
        return extractResults(relations, wantSubject, wantPredicate, wantObject);
    }

    private boolean isConstraint(ItemID pattern) {
        return pattern != null && !ANY.equals(pattern) && !WHAT.equals(pattern);
    }

    private Stream<Relation> queryRelations(Library library,
                                            ItemID subject, ItemID predicate, ItemID object) {
        // Choose query strategy based on what constraints we have
        if (subject != null && predicate != null) {
            logger.info(">>> queryRelations: bySubjectPredicate({}, {})", subject, predicate);
            return library.bySubjectPredicate(subject, predicate);
        } else if (object != null && predicate != null) {
            logger.info(">>> queryRelations: byObjectPredicate({}, {})", object, predicate);
            return library.byObjectPredicate(object, predicate);
        } else if (subject != null) {
            logger.info(">>> queryRelations: bySubject({})", subject);
            return library.bySubject(subject);
        } else if (predicate != null) {
            logger.info(">>> queryRelations: byPredicate({})", predicate);
            List<Relation> rels = library.byPredicate(predicate).toList();
            logger.info(">>> byPredicate returned {} relations", rels.size());
            return rels.stream();
        } else if (object != null) {
            logger.info(">>> queryRelations: byObject({})", object);
            return library.byObject(object);
        } else {
            // No constraints - would need full scan (not ideal)
            logger.warn("Query pattern has no constraints - cannot evaluate efficiently");
            return Stream.empty();
        }
    }

    private Stream<ItemID> extractResults(Stream<Relation> relations,
                                          boolean wantSubject, boolean wantPredicate, boolean wantObject) {
        // Apply any remaining filters and extract results
        return relations
                .flatMap(r -> {
                    List<ItemID> results = new ArrayList<>(3);
                    if (wantSubject) {
                        results.add(r.subject());
                    }
                    if (wantPredicate) {
                        results.add(r.predicate());
                    }
                    if (wantObject) {
                        Relation.Target obj = r.object();
                        if (obj instanceof Relation.IidTarget iidTarget) {
                            results.add(iidTarget.iid());
                        }
                    }
                    return results.stream();
                })
                .distinct();
    }

    /**
     * Evaluate and resolve to actual Items.
     */
    public Stream<Item> evaluateAsItems(Librarian librarian) {
        return evaluate(librarian)
                .flatMap(id -> librarian.get(id, Item.class).stream());
    }

    /**
     * Evaluate and collect to a list of ItemIDs.
     */
    public List<ItemID> evaluateToList(Librarian librarian) {
        return evaluate(librarian).toList();
    }

    // ==================================================================================
    // Pattern Display
    // ==================================================================================

    /**
     * Format the pattern as a human-readable expression.
     */
    public String toPatternString() {
        return formatPosition(subjectPattern) + " → " +
               formatPosition(predicatePattern) + " → " +
               formatPosition(objectPattern);
    }

    private String formatPosition(ItemID pattern) {
        if (ANY.equals(pattern)) return "*";
        if (WHAT.equals(pattern)) return "?";
        return pattern.encodeText();
    }

    // ==================================================================================
    // Display Implementation
    // ==================================================================================

    @Override
    public String displayToken() {
        return queryName != null ? queryName : "Query";
    }

    @Override
    public String displaySubtitle() {
        return toPatternString();
    }

    // ==================================================================================
    // Canonical Support
    // ==================================================================================

    /**
     * Encode this query component to bytes.
     */
    public byte[] encode() {
        return encodeBinary(dev.everydaythings.graph.Canonical.Scope.RECORD);
    }

    /**
     * Decode a query component from bytes.
     */
    public static QueryComponent decode(byte[] bytes) {
        return dev.everydaythings.graph.Canonical.decodeBinary(bytes, QueryComponent.class,
                dev.everydaythings.graph.Canonical.Scope.RECORD);
    }

    @SuppressWarnings("unused")
    private QueryComponent() {
        this.subjectPattern = null;
        this.predicatePattern = null;
        this.objectPattern = null;
    }
}
