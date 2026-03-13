package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.Selectable;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.dictionary.TokenDictionary;

import java.util.*;
import java.util.stream.Stream;

/**
 * A collection of lexemes (word→sememe mappings) for a language.
 *
 * <p>Lexicon provides:
 * <ul>
 *   <li>Storage of lexemes for a specific language</li>
 *   <li>Integration with TokenDictionary for word→sememe lookup</li>
 *   <li>Support for importing lexeme data (e.g., from WordNet)</li>
 * </ul>
 *
 * <p>When connected to a TokenDictionary, lexemes are automatically indexed
 * so that word lookups return the appropriate sememes.
 */
public class Lexicon implements Selectable {

    private final ItemID languageId;
    private final List<Lexeme> lexemes = new ArrayList<>();
    private TokenDictionary tokenIndex;

    public Lexicon(ItemID languageId) {
        this.languageId = Objects.requireNonNull(languageId, "languageId");
    }

    /**
     * Create a Lexicon for the specified language.
     */
    public static Lexicon forLanguage(ItemID languageId) {
        return new Lexicon(languageId);
    }

    /**
     * Set the token index for automatic posting generation.
     *
     * <p>When set, adding lexemes will automatically index them
     * in the token index for word→sememe lookup.
     *
     * @param tokenIndex The token index to use
     * @return this, for chaining
     */
    public Lexicon withTokenDictionary(TokenDictionary tokenIndex) {
        this.tokenIndex = tokenIndex;
        return this;
    }

    /**
     * Add a lexeme to this lexicon.
     *
     * <p>If a token index is set, the lexeme is automatically indexed.
     *
     * @param lexeme The lexeme to add
     */
    public void add(Lexeme lexeme) {
        Objects.requireNonNull(lexeme, "lexeme");
        lexemes.add(lexeme);

        // Index in token index if available
        if (tokenIndex != null) {
            tokenIndex.runInWriteTransaction(tx -> {
                Posting posting = lexeme.toPosting();
                tokenIndex.index(posting, tx);
            });
        }
    }

    /**
     * Add multiple lexemes at once.
     *
     * <p>More efficient than adding one at a time when using TokenDictionary
     * because it uses a single write transaction.
     *
     * @param lexemesToAdd The lexemes to add
     */
    public void addAll(Collection<Lexeme> lexemesToAdd) {
        Objects.requireNonNull(lexemesToAdd, "lexemesToAdd");
        lexemes.addAll(lexemesToAdd);

        // Index in token index if available
        if (tokenIndex != null) {
            tokenIndex.runInWriteTransaction(tx -> {
                for (Lexeme lexeme : lexemesToAdd) {
                    Posting posting = lexeme.toPosting();
                    tokenIndex.index(posting, tx);
                }
            });
        }
    }

    /**
     * Index all lexemes in this lexicon to the token index.
     *
     * <p>Use this to reindex after setting a token index on an
     * already-populated lexicon.
     */
    public void indexAll() {
        if (tokenIndex == null) {
            throw new IllegalStateException("No token index set");
        }

        tokenIndex.runInWriteTransaction(tx -> {
            for (Lexeme lexeme : lexemes) {
                Posting posting = lexeme.toPosting();
                tokenIndex.index(posting, tx);
            }
        });
    }

    /**
     * Index additional postings (e.g., inflected forms) in the token dictionary.
     *
     * <p>Used during import to register inflected surface forms that all resolve
     * to the same sememe as their base form. For example, "ran" → run's sememe.
     *
     * @param postings The postings to index
     */
    public void indexForms(Collection<Posting> postings) {
        if (tokenIndex == null || postings.isEmpty()) return;
        tokenIndex.runInWriteTransaction(tx -> {
            for (Posting posting : postings) {
                tokenIndex.index(posting, tx);
            }
        });
    }

    /**
     * Generate and register all inflected form postings for every lexeme.
     *
     * <p>For each lexeme, generates inflected forms for every feature set the
     * language distinguishes (via {@link Language#inflectionFeatures}), using
     * the language's morphology engine (which checks irregular overrides first,
     * then applies regular rules). Each inflected form is registered as a
     * TokenDictionary posting that resolves to the same sememe as the base form.
     *
     * <p>This enables direct lookup of inflected forms: "ran" → run's sememe,
     * "cats" → cat's sememe, "biggest" → big's sememe.
     *
     * @param language The language (provides morphology engine and feature sets)
     * @return The number of form postings registered
     */
    public int registerInflectedForms(Language language) {
        List<Posting> postings = new ArrayList<>();

        for (Lexeme lexeme : lexemes) {
            List<Set<ItemID>> featureSets = language.inflectionFeatures(lexeme.partOfSpeech());

            for (Set<ItemID> features : featureSets) {
                String form = language.inflect(lexeme, features);
                // Skip if form equals the base word (already indexed by add())
                if (form != null && !form.equals(lexeme.word())) {
                    postings.add(Posting.scoped(form, languageId, lexeme.sememe(), lexeme.frequency()));
                }
            }
        }

        indexForms(postings);
        return postings.size();
    }

    /**
     * Look up lexemes by word.
     *
     * @param word The word to look up
     * @return Stream of lexemes with this word
     */
    public Stream<Lexeme> lookup(String word) {
        String normalized = Posting.normalize(word);
        return lexemes.stream()
                .filter(l -> Posting.normalize(l.word()).equals(normalized));
    }

    /**
     * Look up lexemes by word and part of speech.
     *
     * @param word The word to look up
     * @param pos  The part of speech
     * @return Stream of matching lexemes
     */
    public Stream<Lexeme> lookup(String word, PartOfSpeech pos) {
        String normalized = Posting.normalize(word);
        return lexemes.stream()
                .filter(l -> Posting.normalize(l.word()).equals(normalized))
                .filter(l -> l.partOfSpeech() == pos);
    }

    /**
     * Get all lexemes in this lexicon.
     */
    public List<Lexeme> lexemes() {
        return List.copyOf(lexemes);
    }

    /**
     * Get the language this lexicon is for.
     */
    public ItemID languageId() {
        return languageId;
    }

    /**
     * Get the number of lexemes in this lexicon.
     */
    public int size() {
        return lexemes.size();
    }

    @Override
    public Optional<Selectable> select(Query q) {
        // Lexicons don't support sub-selection
        return Optional.empty();
    }
}
