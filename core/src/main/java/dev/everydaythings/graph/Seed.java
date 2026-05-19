package dev.everydaythings.graph;


import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.lang.annotation.*;

/**
 * Holder for the bootstrap-time annotation family. All members live nested
 * inside {@code Seed} so they share an unambiguous prefix, autocomplete together,
 * and don't pollute the package namespace:
 *
 * <ul>
 *   <li>{@link Item} — class-level: declares a seed item with a canonical key</li>
 *   <li>{@link Frame} — field-level: declares a frame body endorsed by the seed</li>
 *   <li>{@link Property} — field-level: declares a binding directly on the seed's manifest</li>
 *   <li>{@link Binding} — annotation-level: declares one role-spec inside {@code @Seed.Frame}, {@code @Seed.Item}, or {@code @Seed.Property}</li>
 *   <li>{@link Embodies} — class-level: pairs with {@code @Seed.Item} to declare
 *       "this class IS the seed item itself" (singleton)</li>
 *   <li>{@link Mints} — class-level: declares "this class is the runtime form of
 *       instances of K" (instance class)</li>
 *   <li>{@link Handler} — method-level: marks a method as the handler for a predicate</li>
 * </ul>
 *
 * <p><b>Nested-body targets:</b> Java's annotation rules forbid mutually recursive
 * annotation types (JLS §9.6.1). A binding whose target is itself a structured
 * body cannot be declared inline. Such cases must extract the nested body to a
 * separate {@code @Seed.Item} (a named expression) and reference it via
 * {@code ref = ...}. Most non-trivial scalar expressions (px-to-meter, em-to-px,
 * etc.) want their own canonical names anyway.
 *
 * <p>The {@code Seed} class itself is never instantiated.
 */
public class Seed {

    private Seed() {}


    /**
     * Declares that this Java class is a seed item with the given canonical key.
     *
     * <p>At {@link Librarian#bootstrap bootstrap},
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
     *   <li>{@code dev.everydaythings.graph.id.ItemRef} → IidTarget</li>
     *   <li>{@code dev.everydaythings.graph.id.ItemRef[]} → multiple bindings</li>
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
     *                                          qualifiers = {Operator.Infix.KEY}),
     *             bindings = {
     *                 @Seed.Binding(role = ThematicRole.Attribute.KEY,
     *                               qualifiers = {Operator.Precedence.KEY},
     *                               integer = 10),
     *                 @Seed.Binding(role = ThematicRole.Attribute.KEY,
     *                               qualifiers = {Operator.Associativity.KEY},
     *                               ref = Operator.Left.KEY)
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
     * Declares a single binding within a frame generated by {@link Frame}, a manifest
     * generated by {@link Item}, or a direct-manifest entry from {@link Property}.
     *
     * <p>{@code @Seed.Binding} appears in several positional contexts:
     *
     * <ul>
     *   <li><b>{@code classBinding}</b> (on {@code @Seed.Frame}) — the back-link binding
     *       from the generated frame to the enclosing seed item. Target is implicitly
     *       the enclosing seed's IID; the value fields below are silently ignored.
     *       Default role: {@link ThematicRole.Theme}.</li>
     *   <li><b>{@code fieldBinding}</b> (on {@code @Seed.Frame}) — the binding carrying
     *       the field's value as its target. Target is implicitly the field value;
     *       the value fields below are silently ignored. Default role:
     *       {@link ThematicRole.Value}.</li>
     *   <li><b>{@code bindings[]}</b> (on {@code @Seed.Frame} or {@code @Seed.Item}) —
     *       explicit-target bindings. <b>Exactly one</b> of {@link #text},
     *       {@link #integer}, {@link #bool}, or {@link #ref} must be non-default.</li>
     * </ul>
     *
     * <p>The "non-default value = set" convention avoids needing a discriminator enum:
     * <ul>
     *   <li>Empty string {@code ""} on {@code text} or {@code ref} = unset</li>
     *   <li>Empty array {@code {}} on {@code integer}, {@code bool}, or {@code index} = unset</li>
     *   <li>Single-value primitive usage (e.g. {@code integer = 10}) is auto-promoted
     *       by Java to a single-element array</li>
     * </ul>
     *
     * <p>The {@link #index} field is structural — when set, the resulting Binding
     * carries an explicit ordinal position. Two bindings sharing the same compound
     * key AND the same non-null index are malformed.
     *
     * <p><b>Nested-body targets:</b> Java's annotation rules forbid mutually recursive
     * annotation types, so a binding whose target is itself a structured body must
     * extract that body to its own {@code @Seed.Item} and reference it via
     * {@link #ref}. Use this pattern for named expressions like unit-conversion
     * scales (px-to-meter, em-to-px) that benefit from canonical names anyway.
     *
     * <p>Setting {@code role = ""} on {@code classBinding} or {@code fieldBinding} skips
     * that binding entirely (the resulting frame has fewer bindings).
     */
    @Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Binding {

        /**
         * Canonical key of the binding's role sememe, used as a literal
         * {@code @<iid>} role.  Empty string means "skip this binding" (in
         * classBinding/fieldBinding slots).  Exactly one of {@code role},
         * {@code schemaRole}, or {@code typeRole} must be set for a real
         * binding.
         */
        String role() default "";

        /**
         * Canonical key of the binding's role sememe, used as a schema-mode
         * {@code !<iid>} role — the binding is an EXPECTS declaration.
         */
        String schemaRole() default "";

        /**
         * Canonical key of the binding's role sememe, used as a query-mode
         * {@code ?<iid>} role — the binding is a query-pattern position.
         */
        String typeRole() default "";

        /** Canonical keys of qualifier sememes attached to this binding. */
        String[] qualifiers() default {};

        /**
         * Text literal target. Non-empty value indicates "set" — this binding's target is
         * a text literal. Used in {@code bindings[]} only.
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

        /**
         * Ordinal position within an ordered group of same-compound-key siblings.
         * Non-empty array indicates "set" — single-value usage ({@code index = 0}) is
         * auto-promoted by Java to {@code {0}}. Optional and orthogonal to the
         * binding's target type; can be combined with any of text/integer/bool/ref.
         */
        long[] index() default {};
    }

    /**
     * Declares that a static field carries a binding directly on the enclosing
     * {@link Item}-annotated class's manifest body (as opposed to {@link Frame},
     * which generates a separate endorsed frame body).
     *
     * <p>Mirrors the shape of {@link Frame}: an annotation on a field carrying
     * a value. The field's value becomes the binding's target.
     *
     * <p>Field-type → binding-target mapping:
     * <ul>
     *   <li>{@code String} → text literal</li>
     *   <li>{@code Long} / {@code long} / {@code Integer} / {@code int} → integer literal</li>
     *   <li>{@code Boolean} / {@code boolean} → boolean literal</li>
     *   <li>{@code byte[]} → binary literal</li>
     *   <li>{@code java.time.Instant} → instant literal</li>
     *   <li>{@code dev.everydaythings.graph.id.ItemRef} → reference target</li>
     *   <li>{@code Class<?>} → text literal (the FQCN)</li>
     * </ul>
     *
     * <p>For body-shaped targets (structured expressions like unit-conversion scales),
     * extract the body to its own {@code @Seed.Item} and reference it via an
     * {@code ItemRef} field value. The named seed item carries the body's head and
     * bindings; this property binding just points at it.
     *
     * <p>The annotation is repeatable — a single field may carry multiple
     * {@code @Seed.Property} annotations to declare bindings under multiple compound
     * keys for the same target value.
     *
     * <pre>{@code
     * @Seed.Property(role = Symbol.KEY)
     * static final String symbol = "m";
     *
     * @Seed.Property(role = Dimension.KEY, qualifiers = {Length.KEY})
     * static final long lengthExponent = 1;
     *
     * @Seed.Property(role = EquivalentInBase.KEY)
     * static final ItemRef equivalent = ItemRef.iid(PxToMeterScale.KEY);  // ref to a named seed
     * }</pre>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(Property.List.class)
    public static @interface Property {

        /**
         * Canonical key of the binding's role sememe, used as a literal
         * {@code @<iid>} role.  Exactly one of {@code role}, {@code schemaRole},
         * or {@code typeRole} must be set.
         */
        String role() default "";

        /**
         * Canonical key of the binding's role sememe, used as a schema-mode
         * {@code !<iid>} role — the binding is an EXPECTS declaration.
         * Exactly one of {@code role}, {@code schemaRole}, or {@code typeRole}
         * must be set.
         */
        String schemaRole() default "";

        /**
         * Canonical key of the binding's role sememe, used as a query-mode
         * {@code ?<iid>} role — the binding is a query-pattern position.
         * Exactly one of {@code role}, {@code schemaRole}, or {@code typeRole}
         * must be set.
         */
        String typeRole() default "";

        /** Canonical keys of qualifier sememes attached to this binding. */
        String[] qualifiers() default {};

        /**
         * Optional ordinal position within an ordered group of same-compound-key
         * siblings. Single-value usage ({@code index = 0}) auto-promotes to {@code {0}}.
         */
        long[] index() default {};

        /** Container for repeated {@code @Seed.Property} annotations on the same field. */
        @Target(ElementType.FIELD)
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            Property[] value();
        }
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
     * class. Bootstrap adds a {@code JAVA:[ClassName] → "<fqcn>"} binding to K's
     * seed manifest; future {@code fetchItem(ItemRef.iid(K.KEY))} hydrates as an
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
     *   <li>{@code JAVA:[ClassName] → "<fqcn>"} — the runtime form</li>
     *   <li>{@code ENDORSES → <each HANDLES frame>} — one per {@code @Handler}
     *       method on the class, exposing the dispatch surface as data</li>
     * </ul>
     *
     * <p>Bootstrap ALSO adds an implementation binding pointing at {@code @CK}
     * to the archetype's manifest, so {@code resolveImplementationClass} on the
     * archetype walks archetype → CodeItem → class-literal.
     *
     * <p>The two-level mode is what makes the polyglot story work: a Clojure
     * Librarian (different class, same archetype) gets its OWN CodeItem at its
     * own CK with its own method-name HANDLES, while the archetype contract
     * stays shared.
     *
     * <p>In both modes the class must extend {@code Item} and have a public
     * {@code (ItemRef, Librarian)} constructor — that's the contract for hydration.
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
     * IMPLEMENTS { THEME → ItemRef.iid(K.KEY), AGENT:[Java, ClassName] → "<fqcn>" }
     * </pre>
     *
     * <p>The frame becomes data, indexed in FORWARD_BINDINGS by its {@code THEME→K}
     * binding. The {@link dev.everydaythings.graph.semantics.Create} sememe consults
     * IMPLEMENTS frames to find runnable mint targets when CREATE frames target K.
     *
     * <p>For <b>Item-class mints</b> (the class extends {@link dev.everydaythings.graph.item.Item}):
     * the class must have a public {@code (ItemRef, Librarian)} constructor — that's
     * the contract for hydration. CREATE produces a fresh Item.
     *
     * <p>For <b>value-class mints</b> (the class does not extend Item, e.g.,
     * {@code value/Color.java}): the class represents a structured-data shape (Body),
     * not an Item. Construction happens via static factories on the value-class
     * (e.g., {@code Color.rgb(255, 0, 0)}); CREATE produces a fresh Body, not an Item.
     * No constructor contract is enforced.
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

    /**
     * Marks a method as the handler for frames whose head is a given predicate.
     *
     * <p>The {@link Librarian} dispatch layer scans the receiving item's class at
     * invocation time, locates the method whose {@code @Handler(predicate=...)}
     * matches the incoming frame's head IID, decomposes the frame's bindings into
     * method parameters, and invokes the method. Return values become response
     * frames.
     *
     * <p>Eventual seed-time work: the scan also produces a HANDLES frame endorsed
     * by the embodying archetype, so the dispatch table is queryable as data and
     * inheritable across language runtimes. For Phase 1, only the Java-reflection
     * path is implemented; the HANDLES frames are pending.
     *
     * <p>The Java method named here is the truth — direct in-VM callers can invoke
     * it without constructing a frame. The annotation marks it as <i>also</i>
     * reachable via frame dispatch.
     *
     * <p>Phase 1 parameter mapping (will be refined):
     * <ul>
     *   <li>String parameter ↔ THEME binding's text literal</li>
     *   <li>Integer parameter ↔ ATTRIBUTE[LIMIT] binding's integer literal (may be null)</li>
     * </ul>
     * This is enough for LOOKUP; a more general role→position scheme will land
     * when more handlers exist.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Handler {

        /** Canonical key of the predicate whose frames this method handles. */
        String predicate();
    }

    /**
     * Class-level shortcut for declaring an English gloss on the enclosing
     * {@link Item @Seed.Item}.  Expands to a {@link Frame @Seed.Frame} whose
     * predicate is {@code cg.predicate:gloss}, with a back-link THEME binding
     * to the seed and a {@code VALUE[Language.English] → "<text>"} binding.
     *
     * <p>Equivalent long-form:
     * <pre>{@code
     * @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
     *             field = @Seed.Binding(role = ThematicRole.Value.KEY,
     *                                   qualifiers = {Language.English.KEY}))
     * static final String englishGloss = "the operation of summing two quantities";
     * }</pre>
     *
     * <p>Short form:
     * <pre>{@code
     * @Seed.Gloss(english = "the operation of summing two quantities")
     * public class Add { ... }
     * }</pre>
     *
     * <p>For glosses in other languages, continue to use {@link Frame @Seed.Frame}
     * with an explicit language qualifier until a similar shortcut is needed.
     *
     * <p>Repeatable so a single class may carry multiple glosses (different
     * registers, dialects, etc.).
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(Gloss.List.class)
    public static @interface Gloss {

        /** English gloss text. */
        String english();

        /** Container for repeated {@code @Seed.Gloss} annotations on the same class. */
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            Gloss[] value();
        }
    }

    /**
     * Class-level shortcut for declaring English lexemes on the enclosing
     * {@link Item @Seed.Item}.  Expands to one {@link Frame @Seed.Frame} per
     * supplied lemma, whose predicate is {@code cg.predicate:lexeme}, with
     * back-link THEME → seed, and {@code VALUE[Language.English, <pos>, <feature>]
     * → "<lemma>"} binding.
     *
     * <p>Equivalent long-form:
     * <pre>{@code
     * @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
     *             field = @Seed.Binding(role = ThematicRole.Value.KEY,
     *                                   qualifiers = {Language.English.KEY,
     *                                                 PartOfSpeech.Verb.KEY,
     *                                                 GrammaticalFeature.Lemma.KEY}))
     * static final String[] englishVerbLemmas = {"add", "sum"};
     * }</pre>
     *
     * <p>Short form:
     * <pre>{@code
     * @Seed.Lexeme(english = {"add", "sum"}, pos = PartOfSpeech.Verb.KEY)
     * public class Add { ... }
     * }</pre>
     *
     * <p>{@link #feature} defaults to {@link GrammaticalFeature.Lemma} since that's
     * the overwhelming majority of cases — only inflected forms (past tense,
     * plural, etc.) need to override.
     *
     * <p>Multiple lemmas with the same POS go in one annotation as an array;
     * different POS go in separate {@code @Seed.Lexeme} annotations (it's
     * {@link Repeatable}).
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(Lexeme.List.class)
    public static @interface Lexeme {

        /**
         * English lemma(s). Java's single-element array shorthand lets you write
         * {@code english = "add"} for one lemma and {@code english = {"add", "sum"}}
         * for several. Each element produces its own endorsed Lexeme frame.
         */
        String[] english();

        /** Canonical key of the part-of-speech sememe. E.g., {@code PartOfSpeech.Verb.KEY}. */
        String pos();

        /**
         * Canonical key of the grammatical feature. Defaults to
         * {@link GrammaticalFeature.Lemma} — the canonical citation form.
         * Override for inflected lexemes (past, plural, participle, etc.).
         */
        String feature() default GrammaticalFeature.Lemma.KEY;

        /** Container for repeated {@code @Seed.Lexeme} annotations on the same class. */
        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface List {
            Lexeme[] value();
        }
    }
}
