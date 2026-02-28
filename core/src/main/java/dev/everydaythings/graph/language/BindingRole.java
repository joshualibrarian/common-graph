package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.Eval;

/**
 * Runtime binding role for parser terms.
 *
 * <p>Unlike part-of-speech (lexical class), BindingRole captures how a term
 * behaves in an evaluated frame.
 */
public enum BindingRole {
    /** Imperative action term (typically a verb sememe used for dispatch). */
    ACTION,
    /** Entity/value term (items, nouns, literals, pronouns). */
    REFERENCE,
    /** Modifier/linking term (adjectives, adverbs, prepositions, conjunctions). */
    QUALIFIER;

    /**
     * Classify a resolved token into a runtime binding role.
     */
    public static BindingRole classify(Item item, Eval.ResolvedToken token) {
        if (item instanceof Sememe sememe) {
            return switch (sememe.pos()) {
                case VERB -> ACTION;
                case ADJECTIVE, ADVERB, PREPOSITION, CONJUNCTION, INTERJECTION -> QUALIFIER;
                case NOUN, PRONOUN -> REFERENCE;
            };
        }
        // Non-sememe items and literals behave as references in frame bindings.
        return REFERENCE;
    }
}

