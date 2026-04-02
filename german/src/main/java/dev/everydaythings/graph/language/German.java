package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.importer.GermanImporter;
import dev.everydaythings.graph.importer.LanguageImporter;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * German language support.
 *
 * <p>German has significant morphological complexity:
 * <ul>
 *   <li>Four cases: Nominative, Genitive, Dative, Accusative</li>
 *   <li>Three genders: Masculine, Feminine, Neuter</li>
 *   <li>Verb conjugation: person, number, tense, mood</li>
 *   <li>Compound nouns: "Donaudampfschifffahrtsgesellschaft"</li>
 *   <li>Verb-second (V2) word order in main clauses, verb-final in subordinate</li>
 * </ul>
 *
 * <p>For now, morphology is handled entirely by UniMorph data (519K pre-computed
 * forms). Language-specific parse/express overrides can be added later for
 * German word order and grammar.
 */
@Implements(Language.GERMAN_KEY)
@ItemSeed(key = Language.GERMAN_KEY)
public class German extends Language {

    private Librarian librarian;
    private LanguageImporter.ImportStats stats;

    /** Seed constructor. */
    public German() {
        super(Language.GERMAN);
    }

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected German(ItemID typeId) {
        super(typeId);
    }

    /** Create German language item. */
    public German(Librarian librarian) {
        super(librarian, Locale.GERMAN);
        this.librarian = librarian;
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    private German(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        this.librarian = librarian;
    }

    // ==================================================================================
    // BOOTSTRAP
    // ==================================================================================

    /**
     * Generate German from OdeNet + UniMorph.
     *
     * @deprecated Bootstrap scaffolding
     */
    @Deprecated
    public German generate(Signer signer) {
        return generate(signer, 0);
    }

    /**
     * Generate with a limit on entries (for testing).
     *
     * @deprecated Bootstrap scaffolding
     */
    @Deprecated
    public German generate(Signer signer, int maxSynsets) {
        GermanImporter importer = new GermanImporter(librarian);
        this.stats = importer.importLanguage(this, signer, maxSynsets);
        return this;
    }

    public LanguageImporter.ImportStats stats() {
        return stats;
    }

    // ==================================================================================
    // MORPHOLOGY
    // ==================================================================================

    /**
     * German morphological features that are linguistically relevant.
     *
     * <p>German distinguishes case (NOM/GEN/DAT/ACC), gender (MASC/FEM/NEUT),
     * number (SG/PL), tense, mood, and person. UniMorph provides the full
     * paradigm; this method filters to the combinations German uses.
     */
    // ==================================================================================
    // FEATURE SETS — the inflected forms German distinguishes
    // ==================================================================================

    private static final ItemID NOM = GrammaticalFeature.Nominative.IID;
    private static final ItemID GEN = GrammaticalFeature.Genitive.IID;
    private static final ItemID DAT = GrammaticalFeature.Dative.IID;
    private static final ItemID ACC = GrammaticalFeature.Accusative.IID;
    private static final ItemID SG = GrammaticalFeature.Singular.IID;
    private static final ItemID PL = GrammaticalFeature.Plural.IID;
    private static final ItemID PAST = GrammaticalFeature.Past.IID;
    private static final ItemID PRESENT = GrammaticalFeature.Present.IID;
    private static final ItemID PARTICIPLE = GrammaticalFeature.Participle.IID;
    private static final ItemID FIRST = GrammaticalFeature.FirstPerson.IID;
    private static final ItemID SECOND = GrammaticalFeature.SecondPerson.IID;
    private static final ItemID THIRD = GrammaticalFeature.ThirdPerson.IID;
    private static final ItemID IND = GrammaticalFeature.Indicative.IID;
    private static final ItemID SBJV = GrammaticalFeature.Subjunctive.IID;
    private static final ItemID IMP = GrammaticalFeature.Imperative.IID;
    private static final ItemID CMPR = GrammaticalFeature.Comparative.IID;
    private static final ItemID SPRL = GrammaticalFeature.Superlative.IID;

    /** Noun: 4 cases × 2 numbers = 8 forms (minus NOM SG which is the lemma). */
    private static final List<Set<ItemID>> NOUN_FEATURES = List.of(
            Set.of(GEN, SG), Set.of(DAT, SG), Set.of(ACC, SG),
            Set.of(NOM, PL), Set.of(GEN, PL), Set.of(DAT, PL), Set.of(ACC, PL)
    );

    /** Verb: person × number × tense + participle + subjunctive + imperative. */
    private static final List<Set<ItemID>> VERB_FEATURES = List.of(
            // Present indicative
            Set.of(FIRST, SG, PRESENT), Set.of(SECOND, SG, PRESENT), Set.of(THIRD, SG, PRESENT),
            Set.of(FIRST, PL, PRESENT), Set.of(SECOND, PL, PRESENT), Set.of(THIRD, PL, PRESENT),
            // Past (Präteritum) indicative
            Set.of(FIRST, SG, PAST), Set.of(SECOND, SG, PAST), Set.of(THIRD, SG, PAST),
            Set.of(FIRST, PL, PAST), Set.of(SECOND, PL, PAST), Set.of(THIRD, PL, PAST),
            // Subjunctive present (Konjunktiv I)
            Set.of(FIRST, SG, SBJV, PRESENT), Set.of(SECOND, SG, SBJV, PRESENT), Set.of(THIRD, SG, SBJV, PRESENT),
            Set.of(FIRST, PL, SBJV, PRESENT), Set.of(SECOND, PL, SBJV, PRESENT), Set.of(THIRD, PL, SBJV, PRESENT),
            // Subjunctive past (Konjunktiv II)
            Set.of(FIRST, SG, SBJV, PAST), Set.of(SECOND, SG, SBJV, PAST), Set.of(THIRD, SG, SBJV, PAST),
            Set.of(FIRST, PL, SBJV, PAST), Set.of(SECOND, PL, SBJV, PAST), Set.of(THIRD, PL, SBJV, PAST),
            // Imperative
            Set.of(IMP, SECOND, SG), Set.of(IMP, SECOND, PL),
            // Participles
            Set.of(PARTICIPLE, PAST), Set.of(PARTICIPLE, PRESENT)
    );

    /** Adjective: comparative + superlative (full case/gender declension via UniMorph). */
    private static final List<Set<ItemID>> ADJ_FEATURES = List.of(
            Set.of(CMPR), Set.of(SPRL)
    );

    @Override
    public List<Set<ItemID>> inflectionFeatures(ItemID pos) {
        if (pos.equals(PartOfSpeech.NOUN)) return NOUN_FEATURES;
        if (pos.equals(PartOfSpeech.VERB)) return VERB_FEATURES;
        if (pos.equals(PartOfSpeech.ADJECTIVE) || pos.equals(PartOfSpeech.ADVERB)) return ADJ_FEATURES;
        return List.of();
    }
}
