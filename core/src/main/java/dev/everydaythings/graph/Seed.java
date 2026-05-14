package dev.everydaythings.graph;

import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.value.Literal;

import java.lang.annotation.*;

/**
 * Holder for the bootstrap-time annotation family. All five members live nested
 * inside {@code Seed} so they share an unambiguous prefix, autocomplete together,
 * and don't pollute the package namespace:
 *
 * <ul>
 *   <li>{@link Item} — class-level: declares a seed item with a canonical key</li>
 *   <li>{@link Frame} — field-level: declares a frame body endorsed by the seed</li>
 *   <li>{@link Binding} — annotation-level: declares one role-spec inside {@code @Seed.Frame}</li>
 *   <li>{@link Embodies} — class-level: pairs with {@code @Seed.Item} to declare
 *       "this class IS the seed item itself" (singleton)</li>
 *   <li>{@link Mints} — class-level: declares "this class is the runtime form of
 *       instances of K" (instance class)</li>
 * </ul>
 *
 * <p>The {@code Seed} class itself is never instantiated.
 */
public class Seed {

    private Seed() {}


    /**
     * Declares that this Java class is a seed item with the given canonical key.
     *
     * <p>At {@link dev.everydaythings.graph.runtime.Librarian#bootstrap bootstrap},
     * the {@link SeedProcessor} discovers every {@code @Seed.Item}-annotated class
     * and persists a manifest body for it (unsigned), with {@code ITEM_ID → key.IID}.
     * Any {@link Frame @Seed.Frame}-annotated static fields contribute endorsed frames.
     *
     * <p><b>{@code @Seed.Item} alone is pure-data:</b> no IMPLEMENTATION binding is
     * added, and the resulting seed item hydrates as a bare
     * {@link dev.everydaythings.graph.item.Item Item}. Most thematic roles,
     * grammatical features, and language identifiers are pure-data seeds.
     *
     * <p><b>Behavior-bearing seeds combine {@code @Seed.Item} and {@link Embodies}</b>
     * with the same key on the same class. The combination tells the bootstrap
     * "this class IS this seed item AND its Java implementation," producing an
     * IMPLEMENTATION binding on the seed manifest.
     *
     * <p>Convention: the {@code key} value should reference the class's own
     * {@code public static final String KEY} constant — single source of truth.
     *
     * <pre>{@code
     * @Seed.Item(key = MyConcept.KEY)
     * public class MyConcept {
     *     public static final String KEY = "cg.sememe:my-concept";
     *     public static final ItemID IID = ItemID.fromString(KEY);
     * }
     * }</pre>
     *
     * <p>The {@link #head} declares which archetype this seed is an instance of —
     * the IID that becomes the head of the seed's manifest body. Defaults to
     * {@code "cg.archetype:archetype"} — the meta-root — appropriate for top-level
     * seeds that are themselves archetypes (declare their own kinds of things).
     * Override for instance seeds (e.g., thematic roles) and predicate seeds.
     *
     * <p>The default is a string literal rather than {@code Item.Archetype.KEY}
     * because this annotation lives at the foundation, beneath the {@code item/}
     * package; importing {@code Item} here would re-create the cycle this layout
     * was designed to avoid. The canonical key is the source of truth either way.
     *
     * <pre>{@code
     * @Seed.Item(key = Agent.KEY, head = ThematicRole.KEY)
     * public static final class Agent { ... }
     *
     * @Seed.Item(key = Inception.KEY, head = Item.Predicate.KEY)
     * public final class Inception { ... }
     * }</pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Item {

        /** Canonical key for this seed sememe. */
        String key();

        /**
         * Canonical key of the archetype this seed is an instance of — used as the
         * head of the seed's manifest body. Defaults to the meta-root Archetype,
         * appropriate for top-level seeds that are themselves archetypes.
         */
        String head() default "cg.archetype:archetype";

        /**
         * Additional bindings to attach directly to the seed's manifest body, alongside
         * the bootstrap-injected {@code ITEM_ID}, optional {@code IMPLEMENTATION}, and
         * {@code ENDORSES} bindings derived from {@code @Seed.Frame} fields.
         *
         * <p>Each entry must specify exactly one target type ({@code text},
         * {@code integer}, {@code bool}, or {@code ref}) the same way as entries in
         * {@link Frame#bindings}.
         *
         * <p>Use this for one-off manifest-body bindings that don't warrant their own
         * static field (e.g., {@code FOLLOWS} pointers, custom metadata).
         */
        Binding[] bindings() default {};
    }

    /**
     * Declares that a static field's value contributes to a frame body endorsed by the
     * enclosing {@link Item}-annotated class's manifest.
     *
     * <p>When the {@link SeedProcessor} processes a {@code @Seed.Item} class, it
     * walks the static fields. For each {@code @Seed.Frame}-annotated field, it builds
     * a frame body whose head is {@link #predicate} and whose bindings are constructed
     * from {@link #clazz}, {@link #field}, and any extras in
     * {@link #bindings}. The body is persisted (unsigned) and — if {@link #endorse} is
     * true — referenced via an {@code ENDORSES} binding on the seed manifest.
     *
     * <p>The generated body has up to (1 + 1 + N) bindings:
     * <ul>
     *   <li>{@code classBinding} — back-link to the enclosing seed item. Default role:
     *       {@link ThematicRole.Theme}. Target is always the enclosing seed's IID. Set
     *       {@code role = ""} to suppress.</li>
     *   <li>{@code fieldBinding} — the binding whose target is this {@code @Seed.Frame}
     *       field's value. Default role: {@link ThematicRole.Value}. Set
     *       {@code role = ""} to suppress (rare — typically only when the frame is
     *       purely a back-link statement).</li>
     *   <li>{@code bindings} — additional bindings with explicit literal or reference
     *       targets (text, integer, boolean, or sememe ref). Each entry must specify
     *       exactly one target type.</li>
     * </ul>
     *
     * <p>Field-type → BindingTarget mapping for {@code fieldBinding}:
     * <ul>
     *   <li>{@code String} → text Literal</li>
     *   <li>{@code String[]} → multiple bindings (one per array element) on multiple frames</li>
     *   <li>{@code dev.everydaythings.graph.id.ItemID} → IidTarget</li>
     *   <li>{@code dev.everydaythings.graph.id.ItemID[]} → multiple bindings</li>
     *   <li>{@code Class<?>} → Java-class Literal</li>
     *   <li>{@code byte[]} → binary Literal (raw bytes, untyped)</li>
     *   <li>{@code Boolean} / {@code boolean} → boolean Literal</li>
     *   <li>{@code Long} / {@code long} / {@code Integer} / {@code int} → integer Literal</li>
     *   <li>{@code java.time.Instant} → instant Literal</li>
     * </ul>
     *
     * <p>The annotation is repeatable — a single field may carry multiple
     * {@code @Seed.Frame} annotations, each producing its own endorsed frame.
     *
     * <pre>{@code
     * // Simple form: gloss with English-language qualifier on the field-value binding.
     * @Seed.Frame(predicate = Gloss.KEY,
     *             fieldBinding = @Seed.Binding(role = ThematicRole.Value.KEY,
     *                                          qualifiers = {Language.English.KEY}))
     * static final String gloss = "what this concept means";
     *
     * // Full form: multi-binding frame for an operator-form Lexeme.
     * @Seed.Frame(predicate = Lexeme.KEY,
     *             fieldBinding = @Seed.Binding(role = ThematicRole.Value.KEY,
     *                                          qualifiers = {NotationVocabulary.Infix.KEY}),
     *             bindings = {
     *                 @Seed.Binding(role = ThematicRole.Attribute.KEY,
     *                               qualifiers = {NotationVocabulary.Precedence.KEY},
     *                               integer = 10),
     *                 @Seed.Binding(role = ThematicRole.Attribute.KEY,
     *                               qualifiers = {NotationVocabulary.Associativity.KEY},
     *                               ref = NotationVocabulary.Left.KEY)
     *             })
     * static final String symbol = "+";
     * }</pre>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(Frame.List.class)
    public static @interface Frame {

        /** Canonical key of the predicate sememe (the head of the generated frame body). */
        String predicate();

        /**
         * The back-link binding from the generated frame to the enclosing seed item.
         * Target is always the enclosing seed's IID (regardless of any value fields on
         * the {@link Binding}). Default role is {@link ThematicRole.Theme}; set
         * {@code role = ""} to suppress this binding entirely.
         */
        Binding clazz() default @Binding(role = ThematicRole.Theme.KEY);

        /**
         * The binding carrying the field's value as its target. Default role is
         * {@link ThematicRole.Value}; set {@code role = ""} to suppress. Override
         * to attach qualifiers (e.g., {@code @Binding(role = Value.KEY, qualifiers = {English.KEY})}).
         */
        Binding field() default @Binding(role = ThematicRole.Value.KEY);

        /**
         * Additional bindings on the same frame, with explicit literal or reference
         * targets. Each {@link Binding} entry must specify exactly one of
         * {@link Binding#text}, {@link Binding#integer}, {@link Binding#bool},
         * {@link Binding#ref}.
         */
        Binding[] bindings() default {};

        /**
         * Whether the generated frame body should be added to the enclosing seed
         * manifest's {@code ENDORSES} bindings. Defaults to {@code true}. Setting
         * {@code false} persists the body but excludes it from ENDORSES.
         */
        boolean endorse() default true;

        /** Container for repeated {@code @Seed.Frame} annotations on the same field. */
        @Target(ElementType.FIELD)
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            Frame[] value();
        }
    }

    /**
     * Declares a single binding within a frame generated by {@link Frame}.
     *
     * <p>{@code @Seed.Binding} appears in three positional contexts on
     * {@code @Seed.Frame}:
     *
     * <ul>
     *   <li><b>{@code classBinding}</b> — the back-link binding from the generated frame
     *       to the enclosing seed item. Target is implicitly the enclosing seed's IID;
     *       the value fields below are silently ignored. Default role:
     *       {@link ThematicRole.Theme}.</li>
     *   <li><b>{@code fieldBinding}</b> — the binding carrying the {@code @Seed.Frame}
     *       field's value as its target. Target is implicitly the field value; the
     *       value fields below are silently ignored. Default role:
     *       {@link ThematicRole.Value}.</li>
     *   <li><b>{@code bindings[]}</b> — additional bindings with explicit literal or
     *       reference targets. <b>Exactly one</b> of {@link #text}, {@link #integer},
     *       {@link #bool}, {@link #ref} must be non-default in this context.</li>
     * </ul>
     *
     * <p>The "non-default value = set" convention avoids needing a discriminator enum:
     * <ul>
     *   <li>Empty string {@code ""} on {@code text} or {@code ref} = unset</li>
     *   <li>Empty array {@code {}} on {@code integer} or {@code bool} = unset</li>
     *   <li>Setting a single primitive value (e.g. {@code integer = 10}) is auto-promoted
     *       by Java to a single-element array, so detection works as
     *       {@code integer.length > 0}</li>
     * </ul>
     *
     * <p>Setting {@code role = ""} on {@code classBinding} or {@code fieldBinding} skips
     * that binding entirely (the resulting frame has fewer bindings).
     */
    @Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Binding {

        /** Canonical key of the binding's role sememe. Empty string means "skip this binding" (in classBinding/fieldBinding slots). */
        String role();

        /** Canonical keys of qualifier sememes attached to this binding. */
        String[] qualifiers() default {};

        /**
         * Text literal target. Non-empty value indicates "set" — this binding's target is
         * a text {@link Literal}. Used in {@code bindings[]} only.
         */
        String text() default "";

        /**
         * Integer literal target. Non-empty array indicates "set" — single-value usage
         * ({@code integer = 10}) is auto-promoted by Java to {@code {10}}. Used in
         * {@code bindings[]} only.
         */
        long[] integer() default {};

        /**
         * Boolean literal target. Non-empty array indicates "set"; single-value usage
         * ({@code bool = true}) is auto-promoted. Used in {@code bindings[]} only.
         */
        boolean[] bool() default {};

        /**
         * Sememe reference target — canonical key of an item. Non-empty string indicates
         * "set" — this binding's target is an IID reference to that item. Used in
         * {@code bindings[]} only.
         */
        String ref() default "";
    }

    /**
     * Declares that this Java class IS the item identified by the given canonical key.
     *
     * <p>Two modes:
     *
     * <h4>Single-level (default — {@code archetype} empty)</h4>
     *
     * <p>{@code @Seed.Embodies(K)} asserts "this Java class is the runtime form of
     * THE K item itself." Must be paired with {@code @Seed.Item(K)} on the same
     * class. Bootstrap adds an {@code IMPLEMENTATION → ofJavaClass(this)} binding
     * to K's seed manifest; future {@code fetchItem(K.IID)} hydrates as an
     * instance of this class.
     *
     * <p>Used for the typical pattern where a Java class IS a sememe/predicate
     * (Inception, Create, English, etc.). The seed and its embodiment share one IID.
     *
     * <h4>Two-level ({@code archetype} set)</h4>
     *
     * <p>{@code @Seed.Embodies(key=CK, archetype=AK)} asserts "this Java class
     * embodies a <i>code-item</i> at IID {@code CK} that implements archetype
     * {@code AK}." Must be paired with {@code @Seed.Item(AK)} on the same class
     * — the {@code @Seed.Item} creates the archetype seed; this annotation
     * creates a separate CodeItem at {@code CK}.
     *
     * <p>Bootstrap mints a CodeItem manifest (head = {@code cg.archetype:code})
     * carrying:
     * <ul>
     *   <li>{@code ITEM_ID → CK}</li>
     *   <li>{@code IMPLEMENTATION → ofJavaClass(this)} — the runtime form</li>
     *   <li>{@code ENDORSES → <each HANDLES frame>} — one per {@code @Handler}
     *       method on the class, exposing the dispatch surface as data</li>
     * </ul>
     *
     * <p>Bootstrap ALSO adds {@code IMPLEMENTATION → @CK} to the archetype's
     * manifest, so {@code resolveImplementationClass} on the archetype walks
     * archetype → CodeItem → class-literal.
     *
     * <p>The two-level mode is what makes the polyglot story work: a Clojure
     * Librarian (different class, same archetype) gets its OWN CodeItem at its
     * own CK with its own method-name HANDLES, while the archetype contract
     * stays shared.
     *
     * <p>In both modes the class must extend {@code Item} and have a public
     * {@code (ItemID, Librarian)} constructor — that's the contract for hydration.
     *
     * <p>Distinct from {@link Mints}: {@code @Seed.Embodies} declares "I AM
     * this specific item" (singleton); {@code @Seed.Mints(K)} declares "I AM the
     * runtime form of any instance of K" (instance-class). They can coexist on
     * different classes for the same key.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Embodies {

        /**
         * Canonical key of the item this class embodies. In single-level mode this
         * must match a {@code @Seed.Item} key on the same class. In two-level mode
         * (when {@link #archetype} is set) this is the CodeItem's key — distinct
         * from the archetype key declared in {@code @Seed.Item}.
         */
        String key();

        /**
         * Canonical key of the archetype this code-item implements. When empty
         * (default), the annotation runs in single-level mode and {@link #key}
         * must match the {@code @Seed.Item} key on the same class. When set,
         * two-level mode kicks in: {@link #key} names a separate CodeItem and
         * this value names the archetype it implements (which must have a
         * {@code @Seed.Item} declaration on the same class).
         */
        String archetype() default "";
    }

    /**
     * Declares that this Java class is the runtime form of <i>instances of</i> the
     * concept identified by the given key. When CREATE mints a new instance of K,
     * this is the class that gets instantiated.
     *
     * <p>Distinct from {@link Embodies}: {@code @Seed.Embodies(K)} declares "I AM the K
     * seed item itself" (singleton case); {@code @Seed.Mints(K)} declares "I AM the
     * runtime form of any instance of K" (instance-class case). Both can coexist for
     * the same key on different Java classes — a concept can have both its own Java
     * embodiment AND a separate class for its instances.
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
     * <p>The class must extend {@code Item} and have a public {@code (ItemID, Librarian)}
     * constructor — that's the contract for instantiation.
     *
     * <p>Conceptual instantiability — whether K is the kind of concept that has
     * instances at all — comes from data on K's manifest (typically EXPECTS bindings).
     * When EXPECTS is wired, bootstrap can cross-validate {@code @Seed.Mints} against
     * EXPECTS presence and throw on mismatch. Until then, the developer's declaration
     * is trusted.
     *
     * <p>Future expansion: when polyglot runtimes are wired (Clojure, WASM, etc.),
     * {@code @Seed.Mints} remains JVM-flavored. Other runtimes get equivalent
     * declaration mechanisms publishing IMPLEMENTS frames with appropriate
     * {@code AGENT[runtime]} qualifiers; CREATE filters by what runtimes are available
     * locally and orders them by trust.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Mints {

        /** Canonical key of the concept whose instances this class mints. */
        String key();
    }
}