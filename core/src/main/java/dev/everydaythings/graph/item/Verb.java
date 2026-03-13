package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a verb (semantic action) on an Item or component class.
 *
 * <p>Verbs are actions identified by Sememe references rather than string handles.
 * This enables language-agnostic dispatch: the same verb can be invoked via
 * any token that maps to the referenced Sememe (e.g., "create", "crear", "新建").
 *
 * <p>Usage:
 * <pre>{@code
 * @Verb("cg.verb:create")
 * public Item create(ActionContext ctx) { ... }
 *
 * @Verb(value = "cg.verb:move", doc = "Make a chess move")
 * public void move(ActionContext ctx, String notation) { ... }
 * }</pre>
 *
 * @see dev.everydaythings.graph.language.Sememe
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Verb {
    /**
     * Reference to the Sememe that defines this verb.
     * Must be a canonical key like "cg.verb:create" that resolves
     * to a Sememe in the vocabulary.
     */
    String value();

    /**
     * Implementation-specific documentation.
     * The Sememe provides universal meaning; this describes this specific implementation.
     */
    String doc() default "";
}
