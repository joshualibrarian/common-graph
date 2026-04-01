package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.language.ThematicRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a frame on an item — a structured assertion with a predicate and role bindings.
 *
 * <p>Two forms are supported:
 *
 * <h3>Simple form</h3>
 * <p>Creates two bindings automatically: one for the owning item ({@code classAs})
 * and one for the field value ({@code fieldAs}). Good defaults mean most frames
 * need only the predicate:
 *
 * <pre>{@code
 * // Simplest — THEME + NAME defaults
 * @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
 * static final String symbol = "+";
 *
 * // With qualifiers on the field binding
 * @ItemFrame(predicate = SememeGloss.KEY,
 *            fieldAs = @Bind(role = ThematicRole.Name.KEY,
 *                            qualifiers = {Language.ENGLISH_KEY}))
 * static final String gloss = "add two values";
 *
 * // Array value — multiple bindings with the same key
 * @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
 *            fieldAs = @Bind(role = ThematicRole.Name.KEY,
 *                            qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY,
 *                                          GrammaticalFeature.Lemma.KEY}))
 * static final String[] words = {"create", "new", "make"};
 *
 * // Different field role + flags
 * @ItemFrame(predicate = "cg.core:vault",
 *            fieldAs = @Bind(role = ThematicRole.Topic.KEY, identity = false),
 *            endorsement = @Endorsed(mounts = {".vault"}))
 * private Vault vault;
 * }</pre>
 *
 * <h3>Full form</h3>
 * <p>Specifies arbitrary bindings via {@code bindings}. Overrides {@code classAs}
 * and {@code fieldAs} when present:
 *
 * <pre>{@code
 * @ItemFrame(predicate = "cg.sememe:chess", bindings = {
 *     @Bind(role = ThematicRole.Location.KEY),
 *     @Bind(role = "cg.role:player", qualifiers = {"cg.chess:white"}, index = true),
 *     @Bind(role = "cg.role:player", qualifiers = {"cg.chess:black"}, index = true),
 *     @Bind(role = "cg.role:moves", identity = false)
 * })
 * private ChessState state;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ItemFrame {

    /** Empty sentinel for string defaults. */
    String EMPTY = "";

    // ==================================================================================
    // Core
    // ==================================================================================

    /**
     * The frame's predicate — what kind of assertion this is.
     * An ItemID canonical string (e.g., "cg.core:lexeme").
     * Required. An ItemID canonical string (e.g., "cg.core:lexeme").
     */
    String predicate() default EMPTY;

    // ==================================================================================
    // Simple form — two bindings with defaults
    // ==================================================================================

    /**
     * The binding for the owning item/class.
     * Default: THEME ("this item is what this frame is about").
     */
    Bind classAs() default @Bind(role = ThematicRole.Theme.KEY);

    /**
     * The binding for the field's value.
     * Default: NAME ("the field provides the name/text").
     */
    Bind fieldAs() default @Bind(role = ThematicRole.Name.KEY);

    // ==================================================================================
    // Full form — overrides classAs/fieldAs when present
    // ==================================================================================

    /**
     * Explicit bindings. When non-empty, {@code classAs} and {@code fieldAs} are ignored.
     * Each {@code @Bind} specifies a role, optional qualifiers, optional target, and flags.
     */
    Bind[] bindings() default {};

    // ==================================================================================
    // Endorsement
    // ==================================================================================

    /**
     * Endorsement configuration — whether this frame is in the manifest and how it's mounted.
     */
    Endorsed endorsement() default @Endorsed();

    // ==================================================================================
    // Nested annotation types
    // ==================================================================================

    /**
     * A binding specification — role + qualifiers + target + flags.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Bind {
        /** The semantic role (required). An ItemID canonical string. */
        String role();

        /** Qualifiers — narrowing + constraints. ItemID canonical strings. */
        String[] qualifiers() default {};

        /** Target IID string. Empty = contextual (this item for classAs, field value for fieldAs). */
        String target() default "";

        /** Does this binding contribute to the body hash? */
        boolean identity() default true;

        /** Does this binding create a reverse-lookup index entry? */
        boolean index() default false;
    }

    /**
     * Endorsement configuration — whether this frame is in the manifest and mount paths.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Endorsed {
        /** Whether this frame is endorsed (in the manifest). */
        boolean value() default true;

        /** Mount paths — where this frame appears in the item's presentation tree. */
        String[] mounts() default {};
    }

    // ==================================================================================
    // Legacy storage attributes — to be migrated to @Bind flags
    // ==================================================================================

    /** @deprecated Use TOPIC:[LOCAL] binding instead. */
    @Deprecated
    boolean localOnly() default false;
}
