package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a seed sememe — a bootstrap concept with deterministic IID.
 *
 * <p>Placed on a class (outer or inner) to declare that this class defines
 * a seed concept. SeedVocabulary scans for these at bootstrap and creates
 * Sememe items with frames from the annotated fields and methods.
 *
 * <p>Frame data is declared via {@link Frame} on static fields or methods.
 * Word forms are declared via {@link Word} on static String fields.
 *
 * <pre>{@code
 * @Seed(key = Create.KEY, slots = {ThematicRole.Theme.KEY})
 * public static class Create {
 *     public static final String KEY = "cg.verb:create";
 *
 *     @Seed.Frame(key = {SememeGloss.KEY, English.KEY})
 *     static final String gloss = "create a new item";
 *
 *     @Seed.Word(lang = English.KEY, pos = PartOfSpeech.Verb.KEY,
 *                features = {GrammaticalFeature.Lemma.KEY})
 *     static final String verb = "create";
 *
 *     @Seed.Word(lang = English.KEY, pos = PartOfSpeech.Verb.KEY,
 *                features = {GrammaticalFeature.Lemma.KEY})
 *     static final String verb2 = "new";
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ItemSeed {

    /** The canonical key for this seed sememe. */
    String key();

    /** Expected thematic role slots (KEY strings). */
    String[] slots() default {};

    /**
     * Declares a frame on the seed item.
     *
     * <p>The key array maps to a compound FrameKey: first element is the
     * head (predicate), rest are qualifiers. All elements are KEY strings
     * resolved to ItemIDs at bootstrap.
     *
     * <p>Placed on static fields (value = field value) or static methods
     * (value = return value). SeedVocabulary reads the value and encodes
     * it as frame content.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface Frame {
        /** Compound key — KEY strings. First is head, rest are qualifiers. */
        String[] key();
    }

    /**
     * Declares a word form (lexeme) for this seed sememe.
     *
     * <p>Creates a LEXEME frame on the Language item identified by {@code lang}.
     * The annotated field's String value is the surface form.
     *
     * <p>Placed on static String fields.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Word {
        /** Language KEY string (e.g., English.KEY). */
        String lang();

        /** Part of speech KEY string (e.g., PartOfSpeech.Verb.KEY). */
        String pos();

        /** Grammatical feature KEY strings (e.g., GrammaticalFeature.Lemma.KEY). */
        String[] features();
    }
}
