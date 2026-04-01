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
 * Sememe items with frames from the annotated static fields.
 *
 * <p>Frame data is declared via {@link dev.everydaythings.graph.frame.ItemFrame}
 * on static fields.
 *
 * <pre>{@code
 * @ItemSeed(key = Create.KEY)
 * public static class Create {
 *     public static final String KEY = "cg.verb:create";
 *
 *     @ItemFrame(predicate = SememeGloss.KEY,
 *                fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
 *                                          qualifiers = {Language.ENGLISH_KEY}))
 *     static final String gloss = "create a new item";
 *
 *     @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
 *                fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
 *                                          qualifiers = {Language.ENGLISH_KEY,
 *                                                        PartOfSpeech.Verb.KEY,
 *                                                        GrammaticalFeature.Lemma.KEY}))
 *     static final String verb = "create";
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ItemSeed {

    /** The canonical key for this seed sememe. */
    String key();
}
