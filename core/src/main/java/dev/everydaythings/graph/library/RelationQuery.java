package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Sememe;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Fluent builder for querying relations.
 *
 * <p>Reads like constructing a sentence pattern:
 * <pre>{@code
 * // "author WROTE ?"
 * library.find()
 *     .from(author)
 *     .via(Sememe.WROTE)
 *     .relations();
 *
 * // "? AGREES_WITH ?" - all agreements
 * library.find()
 *     .via(Sememe.AGREES_WITH)
 *     .relations();
 *
 * // "? ? book" - all relations TO book
 * library.find()
 *     .to(book)
 *     .relations();
 * }</pre>
 *
 * <p>At least one constraint (from, via, or to) must be specified.
 */
public final class RelationQuery {

    private final Library library;
    private ItemID subject;
    private ItemID predicate;
    private ItemID object;

    RelationQuery(Library library) {
        this.library = library;
    }

    // ==================================================================================
    // Subject (from)
    // ==================================================================================

    /**
     * Constrain by subject (the "from" in subject → predicate → object).
     */
    public RelationQuery from(Item subject) {
        return from(subject.iid());
    }

    /**
     * Constrain by subject ID.
     */
    public RelationQuery from(ItemID subject) {
        this.subject = subject;
        return this;
    }

    // ==================================================================================
    // Predicate (via)
    // ==================================================================================

    /**
     * Constrain by predicate (the relationship type).
     */
    public RelationQuery via(Sememe predicate) {
        return via(predicate.iid());
    }

    /**
     * Constrain by predicate (any Item can be a predicate).
     */
    public RelationQuery via(Item predicate) {
        return via(predicate.iid());
    }

    /**
     * Constrain by predicate ID.
     */
    public RelationQuery via(ItemID predicate) {
        this.predicate = predicate;
        return this;
    }

    // ==================================================================================
    // Object (to)
    // ==================================================================================

    /**
     * Constrain by object (the "to" in subject → predicate → object).
     */
    public RelationQuery to(Item object) {
        return to(object.iid());
    }

    /**
     * Constrain by object ID.
     */
    public RelationQuery to(ItemID object) {
        this.object = object;
        return this;
    }

    // ==================================================================================
    // Terminal Operations
    // ==================================================================================

    /**
     * Execute the query and return matching relations.
     *
     * @return Stream of relations matching the constraints
     * @throws IllegalArgumentException if no constraints specified
     */
    public Stream<Relation> relations() {
        return library.executeQuery(subject, predicate, object);
    }

    /**
     * Execute the query and return the objects of matching relations.
     *
     * <p>Convenience for traversing: "author WROTE ?" → returns the books.
     *
     * @return Stream of Items that are objects in matching relations
     */
    public Stream<Item> objects() {
        return relations()
                .map(Relation::object)
                .filter(t -> t instanceof Relation.IidTarget)
                .map(t -> ((Relation.IidTarget) t).iid())
                .map(library::get)
                .flatMap(Optional::stream);
    }

    /**
     * Execute the query and return the object IDs of matching relations.
     *
     * <p>Like objects() but returns IDs instead of hydrated Items.
     *
     * @return Stream of ItemIDs that are objects in matching relations
     */
    public Stream<ItemID> objectIds() {
        return relations()
                .map(Relation::object)
                .filter(t -> t instanceof Relation.IidTarget)
                .map(t -> ((Relation.IidTarget) t).iid());
    }

    /**
     * Execute the query and return the subjects of matching relations.
     *
     * <p>Convenience for reverse traversal: "? WROTE book" → returns the authors.
     *
     * @return Stream of Items that are subjects in matching relations
     */
    public Stream<Item> subjects() {
        return relations()
                .map(Relation::subject)
                .map(library::get)
                .flatMap(Optional::stream);
    }

    /**
     * Execute the query and return the subject IDs of matching relations.
     *
     * <p>Like subjects() but returns IDs instead of hydrated Items.
     *
     * @return Stream of ItemIDs that are subjects in matching relations
     */
    public Stream<ItemID> subjectIds() {
        return relations()
                .map(Relation::subject);
    }

    /**
     * Check if any relations match the constraints.
     *
     * @return true if at least one relation matches
     */
    public boolean exists() {
        return relations().findAny().isPresent();
    }

    /**
     * Count matching relations.
     *
     * @return number of relations matching the constraints
     */
    public long count() {
        return relations().count();
    }

    /**
     * Get the first matching relation, if any.
     *
     * @return first matching relation, or empty
     */
    public Optional<Relation> first() {
        return relations().findFirst();
    }
}
