package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A natural language with its lexicon.
 *
 * <p>Language items contain:
 * <ul>
 *   <li>Language code (ISO 639-3, 3 letters)</li>
 *   <li>Lexicon - word→sememe mappings for this language</li>
 * </ul>
 *
 * <p>All ~7,000 ISO 639-3 languages are seeded at bootstrap as Language items
 * with deterministic IIDs derived from {@code "cg:language/<code>"}. Subclasses
 * (e.g., English) can add language-specific import logic.
 */
@Type(value = Language.KEY, glyph = "🗣️", color = 0xE08050)
public class Language extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/language";

    /** The English Language Item IID — used to scope seed English lexemes. */
    public static final ItemID ENGLISH = ItemID.fromString("cg:language/eng");

    // ==================================================================================
    // FIELDS
    // ==================================================================================

    /** ISO 639 language code (2 or 3 letter). */
    @Getter
    @Frame(handle = "code")
    protected String languageCode;

    /** The lexicon for this language. */
    @Getter
    @Frame
    protected Lexicon lexicon;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a language from a Java Locale.
     *
     * @param librarian The librarian for storage
     * @param locale    The Java locale for this language
     */
    public Language(Librarian librarian, Locale locale) {
        super(librarian);
        this.languageCode = locale.getISO3Language();  // Use ISO 639-3 (3-letter)
        this.lexicon = Lexicon.forLanguage(this.iid());
    }

    /**
     * Type seed constructor - creates a minimal Language for use as type seed.
     *
     * <p>Used by SeedStore to create type items. Does NOT create a Lexicon
     * since type seeds are just markers, not functional language instances.
     *
     * @param iid The type's ItemID
     */
    public Language(ItemID iid) {
        super(iid);
        // Type seeds don't have content - just the type marker
    }

    /**
     * Create a language with specific IID and language code.
     *
     * <p>Used for seeding language items from ISO 639-3 codes.
     * Does NOT create a Lexicon — seed items are minimal markers.
     * The Lexicon is created when the language is hydrated or used functionally.
     *
     * @param iid          The item ID (e.g., ItemID.fromString("cg:language/eng"))
     * @param languageCode The ISO 639-3 code (e.g., "eng")
     */
    public Language(ItemID iid, String languageCode) {
        super(iid);
        this.languageCode = languageCode;
        // No lexicon for seed items — avoids encoding cost and IllegalStateException
        // since Lexicon is not @Type/Canonical/simple-serializable
    }

    /**
     * Create a language with specific IID (for seed items with content).
     *
     * @param iid    The item ID
     * @param locale The Java locale
     */
    public Language(ItemID iid, Locale locale) {
        super(iid);
        this.languageCode = locale.getISO3Language();
        this.lexicon = Lexicon.forLanguage(iid);
    }

    /**
     * Hydration constructor - reconstructs a Language from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected Language(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
    }

    // ==================================================================================
    // MORPHOLOGY
    // ==================================================================================

    /**
     * Inflect a lexeme for the given grammatical features.
     *
     * <p>Checks the lexeme's irregular form overrides first. If no override
     * exists for the feature set, falls back to {@link #regularInflection}
     * which applies the language's algorithmic rules.
     *
     * @param lexeme   The lexeme (carries the lemma, POS, and irregular overrides)
     * @param features Set of grammatical feature IIDs (e.g., {PAST}, {PLURAL})
     * @return The inflected surface form
     */
    public String inflect(Lexeme lexeme, Set<ItemID> features) {
        if (features == null || features.isEmpty()) return lexeme.word();
        String override = lexeme.lookupForm(features);
        if (override != null) return override;
        return regularInflection(lexeme.word(), lexeme.partOfSpeech(), features);
    }

    /**
     * Inflect a bare word with no override data (pure algorithm).
     *
     * <p>Useful for generating forms of new/unknown words where no
     * lexeme with overrides is available.
     *
     * @param lemma    The base form of the word
     * @param pos      Part of speech
     * @param features Set of grammatical feature IIDs
     * @return The inflected surface form
     */
    public String inflect(String lemma, PartOfSpeech pos, Set<ItemID> features) {
        if (features == null || features.isEmpty()) return lemma;
        return regularInflection(lemma, pos, features);
    }

    /**
     * Produce the regular inflected form for a given feature set.
     *
     * <p>Default returns the lemma unchanged. Language subclasses override
     * this with their specific regular morphology rules.
     *
     * @param lemma    The base form
     * @param pos      Part of speech
     * @param features Grammatical features driving inflection
     * @return The regular inflected form
     */
    protected String regularInflection(String lemma, PartOfSpeech pos, Set<ItemID> features) {
        return lemma;
    }

    /**
     * Get the grammatical feature sets that this language's morphology distinguishes.
     *
     * <p>Returns the feature combinations that the morphology engine can produce
     * inflected forms for. For example, English verbs distinguish:
     * {PAST}, {PARTICIPLE,PRESENT}, {PARTICIPLE,PAST}, {THIRD_PERSON}.
     *
     * <p>Used by import to know which inflected forms to generate and register
     * as TokenDictionary postings, and by UniMorph simplification to map
     * raw feature sets to the minimal sets this language actually uses.
     *
     * <p>Default returns empty — no inflection. Language subclasses override.
     *
     * @param pos Part of speech
     * @return The feature sets this language distinguishes for the given POS
     */
    public List<Set<ItemID>> inflectionFeatures(PartOfSpeech pos) {
        return List.of();
    }

    /**
     * Simplify a raw UniMorph feature set to the minimal set this language uses.
     *
     * <p>UniMorph provides rich feature sets (e.g., {THIRD_PERSON, SINGULAR,
     * PRESENT} for English 3rd person singular). This method reduces them to
     * the minimal set the morphology engine actually keys on (e.g., just
     * {THIRD_PERSON} for English, since that's sufficient to disambiguate).
     *
     * <p>Default implementation finds the largest known feature set (from
     * {@link #inflectionFeatures}) that is a subset of the raw features.
     * Language subclasses can override for special cases (e.g., English
     * maps standalone {PARTICIPLE} to {PARTICIPLE, PRESENT}).
     *
     * @param rawFeatures The raw mapped features from UniMorph
     * @param pos         Part of speech
     * @return The simplified feature set, or empty if no match
     */
    public Set<ItemID> simplifyFeatures(Set<ItemID> rawFeatures, PartOfSpeech pos) {
        List<Set<ItemID>> known = inflectionFeatures(pos);
        Set<ItemID> best = Set.of();
        for (Set<ItemID> candidate : known) {
            if (rawFeatures.containsAll(candidate) && candidate.size() > best.size()) {
                best = candidate;
            }
        }
        return best;
    }

    // ==================================================================================
    // STATIC HELPERS
    // ==================================================================================

    /**
     * Compute the deterministic IID for a language from its ISO 639-3 code.
     *
     * @param iso639_3 The 3-letter language code (e.g., "eng", "spa", "jpn")
     * @return The deterministic ItemID
     */
    public static ItemID iidFor(String iso639_3) {
        return ItemID.fromString("cg:language/" + iso639_3);
    }
}
