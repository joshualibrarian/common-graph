package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private final ItemID partOfSpeech;

    /**
     * Optional: frequency weight (0.0-1.0) for ranking common usages.
     */
    @Canon(order = 4)
    private final float frequency;

    /**
     * Irregular inflected forms, keyed by grammatical feature sets.
     *
     * <p>Only irregular forms are stored here. If a feature set has no entry,
     * the {@link Language} subclass computes the regular form algorithmically.
     * For example, "run" stores {PAST}→"ran" but NOT {THIRD_PERSON,SINGULAR,PRESENT}→"runs"
     * because "runs" follows the regular English -s rule.
     */
    @Canon(order = 5)
    private final List<FormEntry> forms;

    public Lexeme(String word, ItemID language, ItemID sememe,
                  ItemID partOfSpeech, float frequency) {
        this(word, language, sememe, partOfSpeech, frequency, List.of());
    }

    public Lexeme(String word, ItemID language, ItemID sememe,
                  ItemID partOfSpeech, float frequency, List<FormEntry> forms) {
        this.word = Objects.requireNonNull(word, "word");
        this.language = Objects.requireNonNull(language, "language");
        this.sememe = Objects.requireNonNull(sememe, "sememe");
        this.partOfSpeech = Objects.requireNonNull(partOfSpeech, "partOfSpeech");
        this.frequency = frequency;
        this.forms = forms != null ? List.copyOf(forms) : List.of();
    }

    /**
     * Create a Lexeme with default frequency (1.0) and no irregular forms.
     */
    public static Lexeme of(String word, ItemID language, ItemID sememe, ItemID pos) {
        return new Lexeme(word, language, sememe, pos, 1.0f);
    }

    /**
     * Look up an irregular form for the given feature set.
     *
     * @param features The grammatical features to look up
     * @return The irregular form, or null if regular rules should apply
     */
    public String lookupForm(Set<ItemID> features) {
        if (forms == null || forms.isEmpty()) return null;
        for (FormEntry entry : forms) {
            if (entry.matches(features)) return entry.form();
        }
        return null;
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
        this.forms = List.of();
    }
}
