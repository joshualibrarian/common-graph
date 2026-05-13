package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * The EXPECTS predicate sememe — head of frames declaring the schema a sememe expects.
 *
 * <p>An EXPECTS frame endorsed by a sememe's manifest declares "this sememe's
 * use should include this expectation." The qualifier on the binding within the
 * EXPECTS frame disambiguates what kind of expectation it is:
 *
 * <ul>
 *   <li>{@code TOPIC[ROLE] → role-IID} — expects a binding with this role.
 *       For predicate sememes (e.g., AUTHORED), the binding is on the FRAME body;
 *       for archetype sememes (e.g., Chess), it's on the INSTANCE'S MANIFEST body.</li>
 *   <li>{@code TOPIC[FRAME] → predicate-IID} — expects an endorsed frame with this
 *       predicate. (Archetype context only; deferred until we have a use case.)</li>
 * </ul>
 *
 * <p>EXPECTS is one mechanism, used contextually. The qualifier carries the meaning;
 * the consumer (validation, UI generation, CREATE-time instantiability checks)
 * interprets per use.
 *
 * <p>The presence of any EXPECTS endorsement is also the data-side signal that a
 * concept is INSTANTIABLE — the kind of thing that has instances. {@code @Mints(K)}
 * cross-validates against this at bootstrap: a class declaring "I implement instances
 * of K" requires K to declare what its instances should look like.
 */
@Seed.Item(key = Expects.KEY, head = CoreVocabulary.Predicate.KEY)
public final class Expects {

    public static final String KEY = "cg.sememe:expects";
    public static final ItemID IID = ItemID.fromString(KEY);

    private Expects() {}
}
