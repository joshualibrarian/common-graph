package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.library.Library;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A relation pattern expression (Subject → Predicate → Object).
 *
 * <p>Evaluates against the graph's relation index to find matching items.
 * Each position can be:
 * <ul>
 *   <li>{@link Sememe#ANY} - matches anything (wildcard *)</li>
 *   <li>{@link Sememe#WHAT} - the result we're solving for (variable ?)</li>
 *   <li>A concrete ItemID - a filter constraint</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>{@code
 * // All types: what is implemented by anything?
 * // * → IMPLEMENTED_BY → ?
 * PatternExpression.pattern(ANY, IMPLEMENTED_BY, WHAT);
 *
 * // All items of type T: what implements T?
 * // ? → IMPLEMENTED_BY → T
 * PatternExpression.pattern(WHAT, IMPLEMENTED_BY, typeId);
 *
 * // All predicates from item X: how does X relate?
 * // X → ? → *
 * PatternExpression.pattern(itemId, WHAT, ANY);
 * }</pre>
 *
 * <p>This is the core graph query mechanism - the expression form of
 * what was previously QueryComponent.
 *
 * @param subject   Subject pattern (ANY, WHAT, or concrete ItemID)
 * @param predicate Predicate pattern (ANY, WHAT, or concrete ItemID)
 * @param object    Object pattern (ANY, WHAT, or concrete ItemID)
 */
public record PatternExpression(
        @Canon(order = 0) ItemID subject,
        @Canon(order = 1) ItemID predicate,
        @Canon(order = 2) ItemID object
) implements Expression, Canonical {

    private static final Logger log = LogManager.getLogger(PatternExpression.class);

    // Convenience references
    private static final ItemID ANY = Sememe.ANY.iid();
    private static final ItemID WHAT = Sememe.WHAT.iid();

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a pattern expression with explicit positions.
     */
    public static PatternExpression pattern(ItemID subject, ItemID predicate, ItemID object) {
        return new PatternExpression(subject, predicate, object);
    }

    /**
     * Find all subjects with a given predicate: ? → P → *
     */
    public static PatternExpression subjects(ItemID predicate) {
        return pattern(WHAT, predicate, ANY);
    }

    /**
     * Find all objects with a given predicate: * → P → ?
     */
    public static PatternExpression objects(ItemID predicate) {
        return pattern(ANY, predicate, WHAT);
    }

    /**
     * Find all predicates from a subject: S → ? → *
     */
    public static PatternExpression predicatesFrom(ItemID subject) {
        return pattern(subject, WHAT, ANY);
    }

    /**
     * Find subjects with predicate pointing to specific object: ? → P → O
     */
    public static PatternExpression subjectsWithPredicateTo(ItemID predicate, ItemID object) {
        return pattern(WHAT, predicate, object);
    }

    /**
     * Find objects of a subject with predicate: S → P → ?
     */
    public static PatternExpression objectsOf(ItemID subject, ItemID predicate) {
        return pattern(subject, predicate, WHAT);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        log.info(">>> PatternExpression.evaluate: {}", toExpressionString());
        var libraryOpt = context.library();
        if (libraryOpt.isEmpty()) {
            log.warn(">>> PatternExpression: no library available!");
            return List.of();
        }

        Library library = libraryOpt.get();
        log.info(">>> PatternExpression: using library {}", library.getClass().getSimpleName());
        List<ItemID> results = evaluatePattern(library).toList();
        log.info(">>> PatternExpression: {} results", results.size());
        for (ItemID id : results) {
            log.info(">>>   - {}", id.encodeText());
        }
        return results;
    }

    /**
     * Evaluate and return as a stream (for lazy evaluation).
     */
    public Stream<ItemID> evaluateAsStream(EvaluationContext context) {
        var libraryOpt = context.library();
        if (libraryOpt.isEmpty()) {
            return Stream.empty();
        }
        return evaluatePattern(libraryOpt.get());
    }

    private Stream<ItemID> evaluatePattern(Library library) {
        // Determine which positions have WHAT (result variable)
        boolean wantSubject = WHAT.equals(subject);
        boolean wantPredicate = WHAT.equals(predicate);
        boolean wantObject = WHAT.equals(object);

        log.info(">>> evaluatePattern: subject={}, predicate={}, object={}", subject, predicate, object);
        log.info(">>> want: subject={}, predicate={}, object={}", wantSubject, wantPredicate, wantObject);

        // Determine constraints (non-ANY, non-WHAT values)
        ItemID subjectConstraint = isConstraint(subject) ? subject : null;
        ItemID predicateConstraint = isConstraint(predicate) ? predicate : null;
        ItemID objectConstraint = isConstraint(object) ? object : null;

        log.info(">>> constraints: subject={}, predicate={}, object={}", subjectConstraint, predicateConstraint, objectConstraint);

        // Choose the most efficient query strategy based on constraints
        Stream<Relation> relations = queryRelations(library, subjectConstraint, predicateConstraint, objectConstraint);

        // Extract and return the WHAT position(s)
        return extractResults(relations, wantSubject, wantPredicate, wantObject);
    }

    private boolean isConstraint(ItemID pattern) {
        return pattern != null && !ANY.equals(pattern) && !WHAT.equals(pattern);
    }

    private Stream<Relation> queryRelations(Library library,
                                            ItemID subj, ItemID pred, ItemID obj) {
        // Choose query strategy based on what constraints we have
        if (subj != null && pred != null) {
            return library.byItemPredicate(subj, pred);
        } else if (obj != null && pred != null) {
            return library.byItemPredicate(obj, pred);
        } else if (subj != null) {
            return library.byItem(subj);
        } else if (pred != null) {
            return library.byPredicate(pred);
        } else if (obj != null) {
            return library.byItem(obj);
        } else {
            // No constraints - would need full scan (not ideal)
            return Stream.empty();
        }
    }

    private Stream<ItemID> extractResults(Stream<Relation> relations,
                                          boolean wantSubject, boolean wantPredicate, boolean wantObject) {
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

    @Override
    public ItemID resultType() {
        // Returns a list of ItemIDs
        // TODO: Return proper type ID for List<ItemID>
        return null;
    }

    @Override
    public String toExpressionString() {
        return formatPosition(subject) + " → " +
               formatPosition(predicate) + " → " +
               formatPosition(object);
    }

    private String formatPosition(ItemID pattern) {
        if (ANY.equals(pattern)) return "*";
        if (WHAT.equals(pattern)) return "?";
        return pattern.encodeText();
    }

    @Override
    public boolean hasDependencies() {
        return true; // Always depends on the graph index
    }
}
