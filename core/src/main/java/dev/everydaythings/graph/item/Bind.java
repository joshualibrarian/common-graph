package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the value of this static field becomes a binding on a frame
 * endorsed by the enclosing seed item's manifest.
 *
 * <p>When the {@link SeedProcessor} processes a {@code @Seed}-annotated class,
 * it walks the class's static fields. For each {@code @Bind}-annotated field, it
 * builds a small frame body whose {@code head} is the {@code predicate} and
 * whose binding is derived from {@code role} + {@code qualifiers} with the
 * field's value as the target. The frame body is persisted (unsigned) and
 * referenced via an ENDORSES binding on the seed manifest.
 *
 * <p>Field-type → BindingTarget mapping:
 * <ul>
 *   <li>{@code String} → text Literal</li>
 *   <li>{@code String[]} → multiple bindings (one per array element), same role and qualifiers</li>
 *   <li>{@code dev.everydaythings.graph.item.id.ItemID} → IidTarget</li>
 *   <li>{@code dev.everydaythings.graph.item.id.ItemID[]} → multiple bindings</li>
 *   <li>{@code Class<?>} → Java-class Literal</li>
 *   <li>{@code byte[]} → binary Literal (raw bytes, untyped)</li>
 *   <li>{@code Boolean} / {@code boolean} → boolean Literal</li>
 *   <li>{@code Long} / {@code long} / {@code Integer} / {@code int} → integer Literal</li>
 *   <li>{@code java.time.Instant} → instant Literal</li>
 * </ul>
 *
 * <p>Other types throw {@link IllegalArgumentException} at bootstrap time.
 *
 * <p>The annotation is repeatable — a single field may carry multiple {@code @Bind}
 * annotations, each producing its own endorsed frame. Useful for cross-cutting
 * declarations (the same value bound under multiple predicates).
 *
 * <pre>{@code
 * @Seed(key = MyConcept.KEY)
 * public class MyConcept {
 *     public static final String KEY = "cg.sememe:my-concept";
 *
 *     @Bind(predicate = Gloss.KEY, role = Value.KEY, qualifiers = {Languages.English.KEY})
 *     static final String gloss = "what this concept means";
 *
 *     @Bind(predicate = Lexeme.KEY, role = Value.KEY,
 *           qualifiers = {Languages.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
 *     static final String[] englishVerbs = {"to do the thing", "doing"};
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Bind.List.class)
public @interface Bind {

    /** Canonical key of the predicate sememe (the head of the generated frame body). */
    String predicate();

    /** Canonical key of the binding's role sememe. */
    String role();

    /**
     * Canonical keys of qualifier sememes (sememes only — for literal qualifiers,
     * use programmatic seed creation). Empty for simple-key bindings.
     */
    String[] qualifiers() default {};

    /** Container for repeated {@code @Bind} annotations on the same field. */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        Bind[] value();
    }
}
