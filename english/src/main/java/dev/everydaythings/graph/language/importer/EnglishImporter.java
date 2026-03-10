package dev.everydaythings.graph.language.importer;

import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.importer.WordNetImporter.*;
import dev.everydaythings.graph.runtime.Librarian;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Imports English language data from Open English WordNet (OEWN) and UniMorph.
 *
 * <p>This class encapsulates the English-specific import logic:
 * <ul>
 *   <li>Pass 1: OEWN synsets → Sememes + semantic Relations</li>
 *   <li>Pass 2: OEWN lexical entries → Lexemes (with UniMorph irregular overrides)</li>
 *   <li>Pass 3: Generate and register all inflected form postings</li>
 * </ul>
 *
 * <p>UniMorph data provides irregular form overrides (e.g., "run"→"ran").
 * If UniMorph data is not present on the classpath, import proceeds with
 * regular morphology rules only. Pass 3 uses the Language's morphology engine
 * to generate all inflected forms (regular + irregular) and registers them as
 * TokenDictionary postings so that inflected forms resolve directly to their
 * sememes (e.g., "ran" → the same sememe as "run").
 *
 * <p>Usage (from English.generate):
 * <pre>{@code
 * EnglishImporter importer = new EnglishImporter(librarian);
 * EnglishImporter.ImportStats stats = importer.importInto(lexicon, language, signer, 100);
 * }</pre>
 */
public class EnglishImporter {

    /** Path to OEWN LMF XML within resources. */
    private static final String OEWN_PATH = "/english-wordnet-2025.xml";

    /** Path to UniMorph English data within resources. */
    private static final String UNIMORPH_PATH = "/unimorph/eng";

    /** The English language ItemID (for lexeme language reference). */
    private static final ItemID ENGLISH_ID = ItemID.fromString("cg.lang:english");

    private final Librarian librarian;

    // ==================================================================================
    // CONSTRUCTOR
    // ==================================================================================

    public EnglishImporter(Librarian librarian) {
        this.librarian = librarian;
    }

    // ==================================================================================
    // IMPORT STATS
    // ==================================================================================

    /**
     * Statistics from an import run.
     */
    public record ImportStats(
            int synsetCount,
            int lexemeCount,
            int relationCount,
            int formPostingCount,
            long durationMs,
            String source
    ) {}

    // ==================================================================================
    // IMPORT
    // ==================================================================================

    /**
     * Import OEWN data into a lexicon, with morphological form registration.
     *
     * @param lexicon    The lexicon to populate with lexemes
     * @param language   The Language instance (for morphology engine)
     * @param signer     The signer for created items
     * @param maxSynsets Maximum synsets to process (0 = unlimited)
     * @return Import statistics
     */
    public ImportStats importInto(Lexicon lexicon, Language language, Signer signer, int maxSynsets) {
        long startTime = System.currentTimeMillis();

        // Pre-load UniMorph irregular form data
        Map<String, List<UniMorphReader.Entry>> uniMorphData = UniMorphReader.load(UNIMORPH_PATH);

        try (LmfImporter importer = LmfImporter.fromResource(OEWN_PATH)) {
            // Track what we create
            Map<String, Sememe> synsetIdToSememe = new HashMap<>();
            AtomicInteger relationCount = new AtomicInteger(0);
            AtomicInteger synsetCount = new AtomicInteger(0);

            // Pass 1: create Sememes from synsets
            importer.synsets()
                    .limit(maxSynsets > 0 ? maxSynsets : Long.MAX_VALUE)
                    .forEach(synset -> {
                        Sememe sememe = createSememe(synset, signer);
                        synsetIdToSememe.put(synset.id(), sememe);
                        synsetCount.incrementAndGet();

                        // Create relations for this synset
                        int rels = createRelations(synset, sememe, synsetIdToSememe, signer);
                        relationCount.addAndGet(rels);
                    });

            // Pass 2: create Lexemes from lexical entries (with UniMorph overrides)
            try (LmfImporter entryImporter = LmfImporter.fromResource(OEWN_PATH)) {
                AtomicInteger lexemeCount = new AtomicInteger(0);

                entryImporter.lexicalEntries().forEach(entry -> {
                    for (SenseRecord sense : entry.senses()) {
                        Sememe sememe = synsetIdToSememe.get(sense.synsetId());
                        if (sememe == null) continue;

                        Lexeme lexeme = createLexeme(entry, sense, sememe, language, uniMorphData);
                        lexicon.add(lexeme);
                        lexemeCount.incrementAndGet();
                    }
                });

                // Pass 3: register all inflected forms as postings
                int formCount = lexicon.registerInflectedForms(language);

                long duration = System.currentTimeMillis() - startTime;
                return new ImportStats(
                        synsetCount.get(),
                        lexemeCount.get(),
                        relationCount.get(),
                        formCount,
                        duration,
                        "oewn2025" + (uniMorphData.isEmpty() ? "" : "+unimorph")
                );
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to import English from OEWN", e);
        }
    }

    /**
     * Import all OEWN data (no limit).
     */
    public ImportStats importInto(Lexicon lexicon, Language language, Signer signer) {
        return importInto(lexicon, language, signer, 0);
    }

    // ==================================================================================
    // SEMEME CREATION
    // ==================================================================================

    private Sememe createSememe(SynsetRecord synset, Signer signer) {
        String canonicalKey = synset.ili() != null && !synset.ili().isEmpty()
                ? "ili:" + synset.ili()
                : "oewn:" + synset.id();

        PartOfSpeech pos = mapPOS(synset.pos());

        Map<String, String> sources = new HashMap<>();
        sources.put("oewn", synset.id());
        if (synset.ili() != null && !synset.ili().isEmpty()) {
            sources.put("ili", synset.ili());
        }

        String definition = synset.definition() != null ? synset.definition() : "";

        return Sememe.create(
                librarian,
                signer,
                canonicalKey,
                pos,
                Map.of("en", definition),
                sources
        );
    }

    // ==================================================================================
    // RELATION CREATION
    // ==================================================================================

    private int createRelations(SynsetRecord synset, Sememe source,
                                Map<String, Sememe> synsetMap, Signer signer) {
        int count = 0;

        for (RelationRecord rel : synset.relations()) {
            ItemID predicate = mapRelationType(rel.type());
            if (predicate == null) continue;

            Sememe target = synsetMap.get(rel.target());
            if (target == null) continue;

            Relation relation = Relation.builder()
                    .subject(source.iid())
                    .predicate(predicate)
                    .object(Relation.iid(target.iid()))
                    .build()
                    .sign(signer);

            if (librarian != null) {
                librarian.relation(relation);
            }
            count++;
        }

        return count;
    }

    // ==================================================================================
    // LEXEME CREATION
    // ==================================================================================

    private Lexeme createLexeme(LexicalEntryRecord entry, SenseRecord sense, Sememe sememe,
                                Language language, Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        String lemma = entry.lemma();
        PartOfSpeech pos = mapPOS(entry.pos());

        int senseIndex = entry.senses().indexOf(sense);
        float frequency = 1.0f / (senseIndex + 1);

        List<FormEntry> overrides = findIrregularOverrides(lemma, pos, language, uniMorphData);

        return Lexeme.builder()
                .word(lemma)
                .language(ENGLISH_ID)
                .sememe(sememe.iid())
                .partOfSpeech(pos)
                .frequency(frequency)
                .forms(overrides)
                .build();
    }

    /**
     * Find UniMorph forms that differ from the regular algorithm — these are irregular overrides.
     *
     * <p>For each UniMorph entry matching this lemma and POS, simplify the raw
     * feature set using the language's rules, then compare the UniMorph form
     * with what the regular algorithm would produce. If they differ, the form
     * is irregular and needs a FormEntry override on the Lexeme.
     */
    private List<FormEntry> findIrregularOverrides(String lemma, PartOfSpeech pos,
                                                    Language language,
                                                    Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        List<UniMorphReader.Entry> entries = uniMorphData.get(lemma.toLowerCase());
        if (entries == null || entries.isEmpty()) return List.of();

        List<FormEntry> overrides = new ArrayList<>();
        Set<Set<ItemID>> seen = new HashSet<>();

        for (UniMorphReader.Entry entry : entries) {
            if (entry.pos() != pos) continue;

            // Simplify raw UniMorph features to what this language uses
            Set<ItemID> simplified = language.simplifyFeatures(entry.features(), pos);
            if (simplified.isEmpty()) continue;
            if (seen.contains(simplified)) continue;
            seen.add(simplified);

            // Compare UniMorph form with what the regular algorithm produces
            String regularForm = language.inflect(lemma, pos, simplified);
            if (!entry.form().equals(regularForm)) {
                overrides.add(FormEntry.of(simplified, entry.form()));
            }
        }

        return overrides;
    }

    // ==================================================================================
    // MAPPING HELPERS
    // ==================================================================================

    private PartOfSpeech mapPOS(String pos) {
        if (pos == null) return PartOfSpeech.NOUN;
        return switch (pos) {
            case "n" -> PartOfSpeech.NOUN;
            case "v" -> PartOfSpeech.VERB;
            case "a", "s" -> PartOfSpeech.ADJECTIVE;
            case "r" -> PartOfSpeech.ADVERB;
            default -> PartOfSpeech.NOUN;
        };
    }

    private ItemID mapRelationType(String relType) {
        return switch (relType) {
            case "hypernym", "instance_hypernym" -> Sememe.HYPERNYM.iid();
            case "hyponym", "instance_hyponym" -> Sememe.HYPONYM.iid();
            case "holo_part", "holo_member", "holo_substance" -> Sememe.HOLONYM.iid();
            case "mero_part", "mero_member", "mero_substance" -> Sememe.MERONYM.iid();
            case "antonym" -> Sememe.ANTONYM.iid();
            case "similar" -> Sememe.SIMILAR_TO.iid();
            case "derivation" -> Sememe.DERIVATION.iid();
            case "domain_topic", "domain_region", "domain_usage" -> Sememe.DOMAIN.iid();
            case "entails" -> Sememe.ENTAILS.iid();
            case "causes" -> Sememe.CAUSES.iid();
            case "also" -> Sememe.SEE_ALSO.iid();
            default -> null;
        };
    }
}
