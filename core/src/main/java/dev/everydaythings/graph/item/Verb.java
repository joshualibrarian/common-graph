package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a verb (semantic action) on an Item or component class.
 *
 * <p>Verbs are actions identified by Sememe predicates. This enables
 * language-agnostic dispatch: the same verb can be invoked via any token
 * that maps to the referenced Sememe (e.g., "create", "crear", "新建").
 *
 * <p>The predicate connects to the verb's semantic definition — an
 * {@code @ItemSeed} with {@code slots} declaring the expected thematic roles.
 * Method parameters annotated with {@code @Param(role = ...)} map to those slots.
 *
 * <p>Usage:
 * <pre>{@code
 * @Verb(predicate = ViewVocabulary.View.KEY, doc = "Open a view of an item")
 * public ActionResult actionView(
 *         @Param(role = ThematicRole.Theme.KEY, doc = "item to view") ItemID target) {
 *     // ...
 * }
 * }</pre>
 *
 * @see Param
 * @see dev.everydaythings.graph.language.Sememe
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Verb {
    /**
     * The predicate sememe that defines this verb's meaning.
     * A canonical key (e.g., "cg.verb:create") that resolves to a Sememe.
     */
    String predicate() default "";

    /**
     * Implementation-specific documentation.
     * The Sememe provides universal meaning; this describes this specific implementation.
     */
    String doc() default "";

    /**
     * Legacy alias for predicate.
     * @deprecated Use {@code predicate} instead.
     */
    @Deprecated
    String value() default "";
}
