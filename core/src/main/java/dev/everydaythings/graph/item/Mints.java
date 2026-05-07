package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that this Java class is the runtime form of <i>instances of</i> the
 * concept identified by the given key. When CREATE mints a new instance of K,
 * this is the class that gets instantiated.
 *
 * <p>Distinct from {@link Embodies}: {@code @Embodies(K)} declares "I AM the K
 * seed item itself" (singleton case); {@code @Mints(K)} declares "I AM the runtime
 * form of any instance of K" (instance-class case). Both can coexist for the same
 * key on different Java classes — a concept can have both its own Java embodiment
 * AND a separate class for its instances.
 *
 * <p><b>Effect on data:</b> bootstrap publishes an unsigned IMPLEMENTS frame:
 *
 * <pre>
 * IMPLEMENTS { THEME → K.IID, AGENT[runtime=java] → Literal.ofJavaClass(this) }
 * </pre>
 *
 * <p>The frame becomes data, indexed in FORWARD_BINDINGS by its {@code THEME→K}
 * binding. The {@link dev.everydaythings.graph.semantics.Create} sememe consults
 * IMPLEMENTS frames to find runnable mint targets when CREATE frames target K.
 *
 * <p>The class must extend {@link Item} and have a public {@code (ItemID, Librarian)}
 * constructor — that's the contract for instantiation.
 *
 * <p>Conceptual instantiability — whether K is the kind of concept that has
 * instances at all — comes from data on K's manifest (typically EXPECTS bindings).
 * When EXPECTS is wired, bootstrap can cross-validate {@code @Mints} against
 * EXPECTS presence and throw on mismatch. Until then, the developer's declaration
 * is trusted.
 *
 * <p>Future expansion: when polyglot runtimes are wired (Clojure, WASM, etc.),
 * {@code @Mints} remains JVM-flavored. Other runtimes get equivalent declaration
 * mechanisms publishing IMPLEMENTS frames with appropriate {@code AGENT[runtime]}
 * qualifiers; CREATE filters by what runtimes are available locally and orders
 * them by trust.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mints {

    /** Canonical key of the concept whose instances this class mints. */
    String key();
}
