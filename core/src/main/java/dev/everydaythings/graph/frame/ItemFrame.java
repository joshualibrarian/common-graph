package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.language.ThematicRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a frame on an item.
 *
 * <p>This is the ONE annotation for declaring frames — glosses, symbols,
 * lexemes, IMPLEMENTED_BY, component data, everything. A frame is a
 * freestanding semantic assertion with a predicate and role bindings.
 *
 * <p>Every frame has three parts:
 * <ul>
 *   <li>{@code key} — the predicate and qualifiers (what kind of assertion)</li>
 *   <li>{@code as} — how THIS item participates (default: THEME)</li>
 *   <li>{@code value} — how the field value participates (default: NAME)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Gloss — this item is THEME, the text is NAME (both defaults)
 * @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
 * static final String gloss = "add two values";
 *
 * // Symbol — same defaults
 * @ItemFrame(key = {CoreVocabulary.Symbol.KEY})
 * static final String symbol = "+";
 *
 * // Lexeme — same defaults, compound key carries language + POS
 * @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY})
 * static final String word = "add";
 *
 * // IMPLEMENTED_BY — this item is THEME, the sememe is GOAL
 * @ItemFrame(key = {CoreVocabulary.ImplementedBy.KEY}, value = ThematicRole.Goal.KEY)
 * static final String implementedBy = "cg.sememe:chess";
 *
 * // Component — this item is THEME, vault data is TOPIC
 * @ItemFrame(key = {"cg.core:vault"}, value = ThematicRole.Topic.KEY, path = ".vault", localOnly = true)
 * private Vault vault;
 *
 * // Unendorsed frame
 * @ItemFrame(key = {"cg.core:author"}, endorsed = false)
 * private ItemID author;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ItemFrame {

    /** Empty sentinel for string defaults. */
    String EMPTY = "";

    /** Semantic FrameKey tokens (ItemID canonical strings). Required. */
    String[] key() default {};

    /**
     * The thematic role binding THIS item to this frame.
     * Default: THEME (the item this frame is about).
     */
    String as() default ThematicRole.Theme.KEY;

    /**
     * The thematic role binding the field VALUE to this frame.
     * Default: NAME (human-addressable text — indexed as a token).
     */
    String value() default ThematicRole.Name.KEY;

    /** Mount path relative to item root. Required for localOnly. */
    String path() default EMPTY;

    /** Store as snapshot content. Default true. */
    boolean snapshot() default true;

    /** Store as stream content. Default false. */
    boolean stream() default false;

    /** Local-only (no sync). Default false. */
    boolean localOnly() default false;

    /** Contributes to version identity (VID). Default true. */
    boolean identity() default true;

    /** Endorsed = in manifest. False = unendorsed (relation-style). Default true. */
    boolean endorsed() default true;
}
