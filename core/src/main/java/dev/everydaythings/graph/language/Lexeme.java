package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/**
 * A word in a specific language that maps to a sememe (meaning unit).
 *
 * <p>A Lexeme represents the connection between:
 * <ul>
 *   <li>A surface form (the word/phrase as written/spoken)</li>
 *   <li>A language (which language this word belongs to)</li>
 *   <li>A sememe (what meaning this word represents)</li>
 *   <li>A part of speech (grammatical category)</li>
 * </ul>
 *
 * <p>Lexemes are indexed in the TokenDictionary to enable word→sememe lookups.
 * Multiple words can map to the same sememe (synonyms), and the same word
 * can map to multiple sememes (polysemy - handled via multiple Lexemes).
 *
 * <p>Example:
 * <pre>
 * Lexeme("run", English, MOVE_FAST, VERB)    // "run" as in "run quickly"
 * Lexeme("run", English, OPERATE, VERB)      // "run" as in "run a program"
 * Lexeme("sprint", English, MOVE_FAST, VERB) // synonym
 * </pre>
 */
@Getter
@Builder
public final class Lexeme implements Canonical {

    /**
     * The surface form - the word/phrase as written.
     *
     * <p>This is normalized (lowercase, NFC unicode) for indexing.
     */
    @Canon(order = 0)
    private final String word;

    /**
     * The language this word belongs to.
     *
     * <p>References a Language item (e.g., cg.lang:en for English).
     */
    @Canon(order = 1)
    private final ItemID language;

    /**
     * The meaning unit this word represents.
     *
     * <p>References a Sememe item - the language-independent meaning.
     */
    @Canon(order = 2)
    private final ItemID sememe;

    /**
     * The grammatical category of this word usage.
     */
    @Canon(order = 3)
    private final PartOfSpeech partOfSpeech;

    /**
     * Optional: frequency weight (0.0-1.0) for ranking common usages.
     */
    @Canon(order = 4)
    private final float frequency;

    public Lexeme(String word, ItemID language, ItemID sememe,
                  PartOfSpeech partOfSpeech, float frequency) {
        this.word = Objects.requireNonNull(word, "word");
        this.language = Objects.requireNonNull(language, "language");
        this.sememe = Objects.requireNonNull(sememe, "sememe");
        this.partOfSpeech = Objects.requireNonNull(partOfSpeech, "partOfSpeech");
        this.frequency = frequency;
    }

    /**
     * Create a Lexeme with default frequency (1.0).
     */
    public static Lexeme of(String word, ItemID language, ItemID sememe, PartOfSpeech pos) {
        return new Lexeme(word, language, sememe, pos, 1.0f);
    }

    /**
     * Convert this Lexeme to a Posting for token indexing.
     *
     * <p>The posting enables word→sememe lookup in the token index.
     * Lexemes are scoped to their Language Item.
     *
     * @return A posting that maps this word to its sememe, scoped to the language
     */
    public Posting toPosting() {
        return Posting.scoped(word, language, sememe, frequency);
    }

    // No-arg constructor for Canonical decoding
    @SuppressWarnings("unused")
    private Lexeme() {
        this.word = null;
        this.language = null;
        this.sememe = null;
        this.partOfSpeech = null;
        this.frequency = 0;
    }
}
