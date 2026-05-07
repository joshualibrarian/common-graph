package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that this Java class IS the seed item identified by the given canonical key.
 *
 * <p>{@code @Embodies(K)} is the <i>singleton case</i>: it asserts that this Java
 * class is the runtime form of THE K item itself. It must be paired with
 * {@code @Seed(K)} on the same class — that pairing is what makes the relationship
 * meaningful. Bootstrap rejects {@code @Embodies} without a matching {@code @Seed}.
 *
 * <p><b>Effect on data:</b> when both annotations are present with the same key,
 * bootstrap adds an {@code IMPLEMENTATION} binding to the seed manifest pointing at
 * this class. Future {@code fetchItem(K.IID)} calls hydrate as an instance of this
 * class. No IMPLEMENTS frame is published for this annotation alone.
 *
 * <p>The class must extend {@link Item} and have a public
 * {@code (ItemID, Librarian)} constructor — that's the contract for hydration.
 *
 * <p>Distinct from {@link Implements}: {@code @Embodies(K)} declares "I AM the K
 * seed item" (singleton); {@code @Implements(K)} declares "I AM the runtime form of
 * any instance of K" (instance-class). They can coexist for the same key on
 * different Java classes — a concept can have both its own Java embodiment AND a
 * separate class for its instances.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Embodies {

    /** Canonical key of the seed item this class embodies. Must match a {@code @Seed} key on the same class. */
    String key();
}
