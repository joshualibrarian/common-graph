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
    }

    /** This class is a scene-tree {@link SceneText} (a leaf displaying text content). */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Text {
        long order() default Long.MIN_VALUE;
    }

    /** This class is a scene-tree {@link SceneBody} (a visual primitive — shape, image, model, glyph, alt). */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Body {
        long order() default Long.MIN_VALUE;
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
