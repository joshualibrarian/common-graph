package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that this Java class is a seed item with the given canonical key.
 *
 * <p>At {@link dev.everydaythings.graph.runtime.Librarian#bootstrap bootstrap},
 * the {@link SeedProcessor} discovers every {@code @Seed}-annotated class and
 * persists a manifest body for it (unsigned), with {@code ITEM_ID → key.IID}.
 * Any {@code @Bind}-annotated static fields contribute endorsed frames.
 *
 * <p><b>{@code @Seed} alone is pure-data:</b> no IMPLEMENTATION binding is added,
 * and the resulting seed item hydrates as a bare {@link Item}. Most thematic roles,
 * grammatical features, and language identifiers are pure-data seeds.
 *
 * <p><b>Behavior-bearing seeds use both {@code @Seed} and {@code @Embodies}</b>
 * with the same key. The combination tells the bootstrap "this class IS this
 * seed item AND its Java implementation," producing an IMPLEMENTATION binding
 * on the seed manifest in addition to the IMPLEMENTS frame from {@code @Embodies}.
 *
 * <p>Convention: the {@code key} value should reference the class's own
 * {@code public static final String KEY} constant — single source of truth.
 *
 * <pre>{@code
 * @Seed(key = MyConcept.KEY)
 * public class MyConcept {
 *     public static final String KEY = "cg.sememe:my-concept";
 *     public static final ItemID IID = ItemID.fromString(KEY);
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Seed {

    /** Canonical key for this seed sememe. */
    String key();
}
