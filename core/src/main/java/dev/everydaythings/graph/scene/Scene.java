package dev.everydaythings.graph.scene;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation namespace for declaring scene-tree structure as static class
 * hierarchies inside a {@code @Seed.Item}-annotated archetype.
 *
 * <h2>Why a separate annotation set</h2>
 *
 * <p>{@code @Seed.Property} declares bindings on the seed's <i>manifest body</i>
 * — the archetype's identity-bearing data.  {@code @Seed.RecordBinding}
 * declares bindings on the seed's <i>signing record</i> — per-attestation
 * metadata (CONFIG, etc.).  Both are about the archetype manifest.
 *
 * <p>{@code @Scene.*} annotations declare a separate, nested Body — the scene
 * tree.  The processor walks the class hierarchy and synthesizes scene Datums
 * that get attached to the archetype's record as
 * {@code CONFIG[Presentation]}.  Different processing path, different target,
 * different annotation set.
 *
 * <h2>How to use</h2>
 *
 * <pre>{@code
 * @Seed.Item(key = MyArchetype.KEY)
 * @Scene.Container
 * public class MyArchetype extends Item {
 *
 *     @Scene.Property(role = LayoutMode.KEY) static ItemRef layout =
 *             ItemRef.iid(Vertical.KEY);
 *
 *     @Scene.Text
 *     public static class Title {
 *         @Scene.Property(role = Text.KEY) static String text = "hello";
 *     }
 *
 *     @Scene.ClassSelector(value = "muted")
 *     public static class MutedStyle {
 *         @Scene.Property(role = Color.KEY)   static Color c = ...;
 *         @Scene.Property(role = Opacity.KEY) static Numeric o = ...;
 *     }
 * }
 * }</pre>
 *
 * <h2>Resolution rules</h2>
 *
 * <ol>
 *   <li>If the {@code @Seed.Item} class itself carries a {@code @Scene.*}
 *       type annotation, that class IS the scene root node — wear two hats.</li>
 *   <li>Otherwise, exactly one static nested class with a {@code @Scene.*}
 *       type annotation directly inside the {@code @Seed.Item} class is the
 *       scene root.</li>
 *   <li>Multiple top-level scene-typed nested classes require disambiguation
 *       (e.g., handle-presentation vs detail-presentation).  Mechanism TBD;
 *       Phase 1 ships single-root only.</li>
 * </ol>
 *
 * <h2>Children ordering</h2>
 *
 * <p>Children are nested static classes carrying their own {@code @Scene.*}
 * type annotation.  Default ordering is source-declaration order (HotSpot
 * preserves {@code getDeclaredClasses()} order in practice).  An explicit
 * {@code order=} override on the child's type annotation pins the index when
 * declaration order isn't reliable or wanted.
 */
public final class Scene {

    private Scene() {}

    // ==================================================================================
    // Structure types
    // ==================================================================================

    /** This class is a scene-tree {@link SceneContainer} (a structural parent of children). */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Container {
        /**
         * Explicit child-ordinal when this class is itself a child of another
         * scene-typed class.  {@link Long#MIN_VALUE} means "use declaration
         * order"; any other value pins this child to that index.
         */
        long order() default Long.MIN_VALUE;

        /**
         * Compound-key qualifiers selecting which Scene-form this declaration
         * fills.  Empty (default) means the default/main scene; common
         * non-default qualifiers are {@link SceneVocabulary.Handle Handle} (the
         * compact form used in chains, lists, swarms) and
         * {@link SceneVocabulary.Aura Aura} (the per-item overlay framework).
         * Multiple roots with distinct qualifier sets coexist on the same
         * archetype; two roots with the same qualifier set are an error.
         */
        String[] qualifiers() default {};

        /**
         * The role this declaration attaches under, as a concrete
         * ({@code @}-mode) reference.  Default-empty.  When all three of
         * {@link #role}, {@link #schemaRole}, {@link #typeRole} are empty,
         * the processor uses {@link SceneVocabulary.Scene#KEY Scene} as a
         * concrete role — the historic implicit own-scene declaration.
         * Exactly one of the three may be set; mutually exclusive.
         */
        String role() default "";

        /**
         * The role this declaration attaches under, as a schema
         * ({@code !}-mode) reference.  Use {@code schemaRole = Scene.KEY}
         * to declare a TEMPLATE-for-instances of the enclosing archetype.
         * The cascade walks instances upward looking for {@code !Scene}
         * to render them.  Mutually exclusive with {@link #role} and
         * {@link #typeRole}.
         */
        String schemaRole() default "";

        /**
         * The role this declaration attaches under, as a type/query
         * ({@code ?}-mode) reference.  Rare on scene-tree roots; included
         * for symmetry with {@link Property} and {@code @Seed.Property}.
         * Mutually exclusive with {@link #role} and {@link #schemaRole}.
         */
        String typeRole() default "";
    }

    /** This class is a scene-tree {@link SceneText} (a leaf displaying text content). */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Text {
        long order() default Long.MIN_VALUE;
        /** See {@link Container#qualifiers()}. */
        String[] qualifiers() default {};
        /** See {@link Container#role()}. */
        String role() default "";
        /** See {@link Container#schemaRole()}. */
        String schemaRole() default "";
        /** See {@link Container#typeRole()}. */
        String typeRole() default "";
    }

    /** This class is a scene-tree {@link SceneBody} (a visual primitive — shape, image, model, glyph, alt). */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Body {
        long order() default Long.MIN_VALUE;
        /** See {@link Container#qualifiers()}. */
        String[] qualifiers() default {};
        /** See {@link Container#role()}. */
        String role() default "";
        /** See {@link Container#schemaRole()}. */
        String schemaRole() default "";
        /** See {@link Container#typeRole()}. */
        String typeRole() default "";
    }

    /**
     * Modifier annotation: this class's scene body is a per-item TEMPLATE
     * that gets repeated for each item in {@code source} at render time.
     * Combines with one of {@link Container}, {@link Text}, {@link Body} to
     * declare the shape of one iteration's output.  The seed processor
     * wraps the inner body in a Transform-headed operator frame whose THEME
     * is a {@code ?}-ref to {@code source} and whose INSTRUMENT is the
     * inner body; the resolver evaluates per iteration with each source
     * item pushed onto the context chain.
     *
     * <p>Sugar for the recurring "render N things from a collection"
     * pattern; underneath it's a plain {@code Transform} operator frame in
     * the scene tree.  Used on nested static classes inside a container so
     * the wrapped Transform shows up as the parent's Children target.
     *
     * <p>Example:
     * <pre>{@code
     * @Scene.Container
     * static class ReactionList {
     *     @Scene.Text
     *     @Scene.Repeat(source = "cg.var:active-reactions")
     *     static class ReactionDot {
     *         @Scene.Property(role = Text.KEY)
     *         static TypeRef text = TypeRef.iid(NameVocabulary.Name.KEY);
     *     }
     * }
     * }</pre>
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Repeat {
        /**
         * Canonical key of the source-collection variable: at render time
         * this resolves through the context chain to a {@code Collection}
         * of bodies, each becoming one iteration's pushed scope.
         */
        String source();
    }

    // ==================================================================================
    // Style declarations — pattern + properties to apply to matching scene nodes.
    // ==================================================================================

    /**
     * This static class is a style declaration — its {@code @Scene.Property}
     * fields are the properties to merge onto scene nodes matching the
     * pattern specified by this annotation's parameters.  Lives at the same
     * structural level as the structure root inside an
     * {@code @Seed.Item} class; the seed processor emits one
     * {@link SceneVocabulary.Style Style} record binding per
     * {@code @Scene.Style} class.
     *
     * <p>Pattern parameters (use at most one for shorthand cases):
     * <ul>
     *   <li>{@link #matchClass} — matches nodes whose
     *       {@link SceneVocabulary.Classes Classes} binding contains the
     *       given class name.</li>
     *   <li>{@link #matchId} — matches the node whose
     *       {@link SceneVocabulary.Id Id} binding equals the given id.</li>
     *   <li>{@link #matchType} — matches nodes whose archetype IID equals
     *       the given key (e.g. {@code SceneText.KEY}).</li>
     * </ul>
     *
     * <p>For richer queries (compound selectors, ancestor matching,
     * computed-property predicates), declare the pattern via the
     * {@code SceneSelectorNotation} text form once that language item
     * lands, or construct the pattern body explicitly.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Style {
        /** Match nodes whose {@code Classes} binding contains this string. */
        String matchClass() default "";

        /** Match the node whose {@code Id} binding equals this string. */
        String matchId() default "";

        /** Match nodes whose archetype IID equals this canonical key. */
        String matchType() default "";

        /**
         * Precedence index when multiple Style bindings live on the same
         * record.  {@link Long#MIN_VALUE} means "use declaration order."
         */
        long order() default Long.MIN_VALUE;

        /**
         * Compound-key qualifiers selecting which Scene-form this style
         * applies to.  Empty (default) means the default Scene's style
         * cascade.  {@code qualifiers = {SceneVocabulary.Aura.KEY}} means
         * "this style applies when rendering the Aura form of an item."
         * Parallels {@link Container#qualifiers()} on scene-structure roots.
         */
        String[] qualifiers() default {};
    }

    // ==================================================================================
    // Property bindings on a scene node
    // ==================================================================================

    /**
     * Set a property binding on the surrounding scene node.  The static
     * field's value supplies the binding target; this annotation supplies
     * the role + optional qualifiers.
     *
     * <p>Supported field types follow the same mapping as
     * {@code @Seed.RecordBinding}:
     * <ul>
     *   <li>{@link dev.everydaythings.graph.ref.ItemRef ItemRef} → reference binding</li>
     *   <li>{@link dev.everydaythings.graph.value.Value Value} subclasses
     *       (Color, Length, Numeric, Bool, ...) → value binding</li>
     *   <li>{@link String}, {@link Long}, {@link Integer}, {@link Boolean},
     *       {@code byte[]} → literal binding</li>
     * </ul>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Property {
        String role();
        String[] qualifiers() default {};
    }
}
