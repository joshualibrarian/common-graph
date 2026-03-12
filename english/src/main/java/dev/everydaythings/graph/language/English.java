package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.importer.EnglishImporter;
import dev.everydaythings.graph.language.importer.LanguageImporter;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The English language item, bootstrapped from Open English WordNet.
 *
 * <p>English is a Language with a Lexicon populated from OEWN 2025.
 * It contains lexemes (word→sememe mappings) for English words.
 *
 * <p>The {@link #generate(Signer)} method is bootstrap scaffolding that:
 * <ol>
 *   <li>Parses OEWN using the LMF importer</li>
 *   <li>Creates Sememes for each synset</li>
 *   <li>Creates semantic Relations (hypernym, hyponym, etc.)</li>
 *   <li>Populates the Lexicon with Lexemes</li>
 * </ol>
 *
 * <p>After initial generation, the generate method should be removed.
 */
@Type(value = English.KEY, glyph = "🇬🇧")
public class English extends Language {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:language/eng";


    // ==================================================================================
    // STATISTICS (populated by generate)
    // ==================================================================================

    // Stats are transient - not persisted as a component
    private transient LanguageImporter.ImportStats stats;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal English for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg.lang:english" type item.
     * Does NOT create a Lexicon - type seeds are just markers.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected English(ItemID typeId) {
        super(typeId);
    }

    /**
     * Create English language item.
     *
     * @param librarian The librarian for storage
     */
    public English(Librarian librarian) {
        super(librarian, Locale.ENGLISH);
    }

    /**
     * Hydration constructor - reconstructs an English from a stored manifest.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private English(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // BOOTSTRAP - Remove after initial import
    // ==================================================================================

    /**
     * Generate English from Open English WordNet.
     *
     * <p>This method exists only for initial bootstrap. Once English
     * is generated and committed, this method should be removed.
     *
     * @param signer The signer to sign created items
     * @return this, for chaining
     * @deprecated Bootstrap scaffolding - remove after v1.0
     */
    @Deprecated
    public English generate(Signer signer) {
        return generate(signer, 0);
    }

    /**
     * Generate with a limit on entries (for testing).
     *
     * @param signer     The signer
     * @param maxSynsets Maximum synsets to process (0 = unlimited)
     * @return this
     * @deprecated Bootstrap scaffolding
     */
    @Deprecated
    public English generate(Signer signer, int maxSynsets) {
        EnglishImporter importer = new EnglishImporter(librarian);
        this.stats = importer.importInto(lexicon, this, signer, maxSynsets);
        return this;
    }

    // ==================================================================================
    // ACCESSORS
    // ==================================================================================

    /**
     * Get generation statistics (null if not yet generated).
     */
    public LanguageImporter.ImportStats stats() {
        return stats;
    }

    // ==================================================================================
    // MORPHOLOGY — Regular English inflection rules
    // ==================================================================================

    private static final ItemID PAST = GrammaticalFeature.PAST.iid();
    private static final ItemID PRESENT = GrammaticalFeature.PRESENT.iid();
    private static final ItemID PLURAL = GrammaticalFeature.PLURAL.iid();
    private static final ItemID THIRD_PERSON = GrammaticalFeature.THIRD_PERSON.iid();
    private static final ItemID SINGULAR = GrammaticalFeature.SINGULAR.iid();
    private static final ItemID PARTICIPLE = GrammaticalFeature.PARTICIPLE.iid();
    private static final ItemID COMPARATIVE = GrammaticalFeature.COMPARATIVE.iid();
    private static final ItemID SUPERLATIVE = GrammaticalFeature.SUPERLATIVE.iid();

    // Feature sets that English morphology distinguishes, per POS
    private static final List<Set<ItemID>> VERB_FEATURES = List.of(
            Set.of(PAST),
            Set.of(PARTICIPLE, PRESENT),
            Set.of(PARTICIPLE, PAST),
            Set.of(THIRD_PERSON)
    );
    private static final List<Set<ItemID>> NOUN_FEATURES = List.of(Set.of(PLURAL));
    private static final List<Set<ItemID>> ADJ_FEATURES = List.of(
            Set.of(COMPARATIVE),
            Set.of(SUPERLATIVE)
    );

    @Override
    public List<Set<ItemID>> inflectionFeatures(PartOfSpeech pos) {
        return switch (pos) {
            case VERB -> VERB_FEATURES;
            case NOUN -> NOUN_FEATURES;
            case ADJECTIVE, ADVERB -> ADJ_FEATURES;
            default -> List.of();
        };
    }

    /**
     * Simplify raw UniMorph features to the minimal set English morphology uses.
     *
     * <p>Special case: a standalone PARTICIPLE (from UniMorph's gerund tag V.MSDR)
     * maps to {PARTICIPLE, PRESENT}, because in English a participle without
     * PAST is always the present participle.
     */
    @Override
    public Set<ItemID> simplifyFeatures(Set<ItemID> rawFeatures, PartOfSpeech pos) {
        if (pos == PartOfSpeech.VERB && rawFeatures.contains(PARTICIPLE)
                && !rawFeatures.contains(PAST) && !rawFeatures.contains(PRESENT)) {
            // Gerund / standalone participle → present participle
            return Set.of(PARTICIPLE, PRESENT);
        }
        return super.simplifyFeatures(rawFeatures, pos);
    }

    @Override
    protected String regularInflection(String lemma, PartOfSpeech pos, Set<ItemID> features) {
        if (lemma == null || lemma.isEmpty()) return lemma;

        return switch (pos) {
            case VERB -> inflectVerb(lemma, features);
            case NOUN -> inflectNoun(lemma, features);
            case ADJECTIVE, ADVERB -> inflectAdjective(lemma, features);
            default -> lemma;
        };
    }

    // ----- Verb inflection -----

    private String inflectVerb(String lemma, Set<ItemID> features) {
        // Present participle / gerund: {PARTICIPLE, PRESENT} or {PROGRESSIVE}
        if (features.contains(PARTICIPLE) && features.contains(PRESENT)) {
            return addIng(lemma);
        }

        // Past participle: {PARTICIPLE, PAST}
        if (features.contains(PARTICIPLE) && features.contains(PAST)) {
            return addEd(lemma);
        }

        // Simple past: {PAST}
        if (features.contains(PAST)) {
            return addEd(lemma);
        }

        // 3rd person singular present: {THIRD_PERSON, SINGULAR, PRESENT}
        // or just {THIRD_PERSON} in present context
        if (features.contains(THIRD_PERSON)) {
            return addS(lemma);
        }

        return lemma;
    }

    // ----- Noun inflection -----

    private String inflectNoun(String lemma, Set<ItemID> features) {
        if (features.contains(PLURAL)) {
            return addS(lemma);
        }
        return lemma;
    }

    // ----- Adjective / Adverb inflection -----

    private String inflectAdjective(String lemma, Set<ItemID> features) {
        if (features.contains(COMPARATIVE)) {
            return addEr(lemma);
        }
        if (features.contains(SUPERLATIVE)) {
            return addEst(lemma);
        }
        return lemma;
    }

    // ==================================================================================
    // SUFFIX RULES
    // ==================================================================================

    /**
     * Add -s / -es for 3rd person singular present or noun plural.
     *
     * <p>Rules:
     * <ul>
     *   <li>Ends in s, x, z, sh, ch → +es (passes, boxes, watches)</li>
     *   <li>Ends in consonant+y → replace y with ies (carries, babies)</li>
     *   <li>Otherwise → +s (runs, cats)</li>
     * </ul>
     */
    static String addS(String lemma) {
        if (endsWithSibilant(lemma)) {
            return lemma + "es";
        }
        if (endsWithConsonantY(lemma)) {
            return lemma.substring(0, lemma.length() - 1) + "ies";
        }
        return lemma + "s";
    }

    /**
     * Add -ed for regular past tense and past participle.
     *
     * <p>Rules:
     * <ul>
     *   <li>Ends in e → +d (loved, created)</li>
     *   <li>Ends in consonant+y → replace y with ied (carried, studied)</li>
     *   <li>Short word ending in single-vowel+consonant → double consonant + ed (stopped)</li>
     *   <li>Otherwise → +ed (played, walked)</li>
     * </ul>
     */
    static String addEd(String lemma) {
        if (lemma.endsWith("e")) {
            return lemma + "d";
        }
        if (endsWithConsonantY(lemma)) {
            return lemma.substring(0, lemma.length() - 1) + "ied";
        }
        if (shouldDoubleConsonant(lemma)) {
            return lemma + lemma.charAt(lemma.length() - 1) + "ed";
        }
        return lemma + "ed";
    }

    /**
     * Add -ing for present participle / gerund.
     *
     * <p>Rules:
     * <ul>
     *   <li>Ends in ie → replace ie with ying (die→dying, lie→lying)</li>
     *   <li>Ends in e (not ee) → drop e + ing (love→loving, but see→seeing)</li>
     *   <li>Short word ending in single-vowel+consonant → double + ing (run→running)</li>
     *   <li>Otherwise → +ing (play→playing)</li>
     * </ul>
     */
    static String addIng(String lemma) {
        if (lemma.endsWith("ie")) {
            return lemma.substring(0, lemma.length() - 2) + "ying";
        }
        if (lemma.endsWith("e") && !lemma.endsWith("ee") && !lemma.endsWith("oe")
                && !lemma.endsWith("ye") && lemma.length() > 1) {
            return lemma.substring(0, lemma.length() - 1) + "ing";
        }
        if (shouldDoubleConsonant(lemma)) {
            return lemma + lemma.charAt(lemma.length() - 1) + "ing";
        }
        return lemma + "ing";
    }

    /**
     * Add -er for comparative adjectives.
     *
     * <p>Rules:
     * <ul>
     *   <li>Ends in e → +r (nice→nicer)</li>
     *   <li>Ends in consonant+y → replace y with ier (happy→happier)</li>
     *   <li>Short word ending in single-vowel+consonant → double + er (big→bigger)</li>
     *   <li>Otherwise → +er (tall→taller)</li>
     * </ul>
     */
    static String addEr(String lemma) {
        if (lemma.endsWith("e")) {
            return lemma + "r";
        }
        if (endsWithConsonantY(lemma)) {
            return lemma.substring(0, lemma.length() - 1) + "ier";
        }
        if (shouldDoubleConsonant(lemma)) {
            return lemma + lemma.charAt(lemma.length() - 1) + "er";
        }
        return lemma + "er";
    }

    /**
     * Add -est for superlative adjectives.
     */
    static String addEst(String lemma) {
        if (lemma.endsWith("e")) {
            return lemma + "st";
        }
        if (endsWithConsonantY(lemma)) {
            return lemma.substring(0, lemma.length() - 1) + "iest";
        }
        if (shouldDoubleConsonant(lemma)) {
            return lemma + lemma.charAt(lemma.length() - 1) + "est";
        }
        return lemma + "est";
    }

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
    }

    /**
     * Whether the word ends with a sibilant that requires -es.
     * Covers: s, x, z, sh, ch.
     */
    private static boolean endsWithSibilant(String word) {
        return word.endsWith("s") || word.endsWith("x") || word.endsWith("z")
                || word.endsWith("sh") || word.endsWith("ch");
    }

    /**
     * Whether the word ends with consonant + y (e.g., carry, baby, happy).
     * Not: play (vowel + y), boy (vowel + y).
     */
    private static boolean endsWithConsonantY(String word) {
        if (!word.endsWith("y") || word.length() < 2) return false;
        return !isVowel(word.charAt(word.length() - 2));
    }

    /**
     * Whether the final consonant should be doubled before a suffix.
     *
     * <p>Applies to short (1-syllable) words ending in a single vowel
     * followed by a single consonant, excluding w, x, y.
     * Examples: run, stop, big, hit, plan.
     * Counterexamples: play (ends in y), fix (ends in x), rain (double vowel).
     */
    private static boolean shouldDoubleConsonant(String word) {
        int len = word.length();
        if (len < 3) return false;
        char last = word.charAt(len - 1);
        char secondLast = word.charAt(len - 2);
        // Don't double w, x, y
        if (last == 'w' || last == 'x' || last == 'y') return false;
        // Last must be consonant, second-to-last must be single vowel
        if (isVowel(last) || !isVowel(secondLast)) return false;
        // Third-to-last must NOT be a vowel (that would be a double vowel like "rain")
        if (len >= 3 && isVowel(word.charAt(len - 3))) return false;
        // Only reliably double for short words (1 syllable ≈ ≤4 letters)
        return len <= 4;
    }
}
