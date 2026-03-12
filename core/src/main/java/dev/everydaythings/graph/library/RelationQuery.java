package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.VerbSememe;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Fluent builder for querying relations.
 *
 * <p>With frame-based relations, queries are by participating item and/or predicate:
 * <pre>{@code
 * // All relations involving 'author'
 * library.find()
 *     .involving(author)
 *     .relations();
 *
 * // All HYPERNYM relations involving 'animal'
 * library.find()
 *     .involving(animal)
 *     .via(VerbSememe.Hypernym.SEED)
 *     .relations();
 *
 * // All AGREES_WITH relations
 * library.find()
 *     .via(VerbSememe.AgreesWithSememe)
 *     .relations();
 * }</pre>
 *
 * <p>At least one constraint (involving or via) must be specified.
 */
public final class RelationQuery {

    private final Library library;
    private ItemID item;
    private ItemID predicate;

    RelationQuery(Library library) {
        this.library = library;
    }

    // ==================================================================================
    // Item constraint (involving)
    // ==================================================================================

    /**
     * Constrain to relations involving this item (in any role).
     */
    public RelationQuery involving(Item item) {
        return involving(item.iid());
    }

    /**
     * Constrain to relations involving this item ID (in any role).
     */
    public RelationQuery involving(ItemID item) {
        this.item = item;
        return this;
    }

    /**
     * Alias for {@link #involving(ItemID)} — reads well for subject-like queries.
     * @deprecated Use {@link #involving(ItemID)} for clarity
     */
    @Deprecated
    public RelationQuery from(ItemID item) {
        return involving(item);
    }

    /**
     * Alias for {@link #involving(Item)}.
     * @deprecated Use {@link #involving(Item)} for clarity
     */
    @Deprecated
    public RelationQuery from(Item item) {
        return involving(item);
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
    // Terminal Operations
    // ==================================================================================

    /**
     * Execute the query and return matching relations.
     *
     * @return Stream of relations matching the constraints
     * @throws IllegalArgumentException if no constraints specified
     */
    public Stream<Relation> relations() {
        return library.executeQuery(item, predicate);
    }

    /**
     * Get a specific role's binding across all matching relations.
     *
     * <p>Convenience for extracting a particular role's value:
     * <pre>{@code
     * // Get all items in the TARGET role of HYPERNYM relations involving 'animal'
     * library.find().involving(animal).via(VerbSememe.Hypernym.SEED)
     *     .bindingIds(ThematicRole.Target.SEED.iid())
     * }</pre>
     *
     * @param role The role to extract
     * @return Stream of ItemIDs bound to that role in matching relations
     */
    public Stream<ItemID> bindingIds(ItemID role) {
        return relations()
                .map(r -> r.bindingId(role))
                .filter(id -> id != null);
    }

    /**
     * Get a specific role's binding as hydrated Items.
     *
     * @param role The role to extract
     * @return Stream of Items bound to that role in matching relations
     */
    public Stream<Item> bindings(ItemID role) {
        return bindingIds(role)
                .map(library::get)
                .flatMap(Optional::stream);
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
