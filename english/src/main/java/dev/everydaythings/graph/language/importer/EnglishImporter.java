package dev.everydaythings.graph.language.importer;

import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.Lexeme;
import dev.everydaythings.graph.language.Lexicon;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.importer.WordNetImporter.*;
import dev.everydaythings.graph.runtime.Librarian;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Imports English language data from Open English WordNet (OEWN).
 *
 * <p>This class encapsulates the OEWN-specific import logic:
 * <ul>
 *   <li>POS tag mapping (LMF → Common Graph)</li>
 *   <li>Semantic relation type mapping</li>
 *   <li>Sememe creation from synsets</li>
 *   <li>Lexeme creation from lexical entries</li>
 *   <li>Relation creation between sememes</li>
 * </ul>
 *
 * <p>Usage (from English.generate):
 * <pre>{@code
 * EnglishImporter importer = new EnglishImporter(librarian);
 * EnglishImporter.ImportStats stats = importer.importInto(lexicon, signer, 100);
 * }</pre>
 */
public class EnglishImporter {

    /** Path to OEWN LMF XML within resources. */
    private static final String OEWN_PATH = "/wordnet/oewn2025/english-wordnet-2025.xml";

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
            long durationMs,
            String source
    ) {}

    // ==================================================================================
    // IMPORT
    // ==================================================================================

    /**
     * Import OEWN data into a lexicon.
     *
     * @param lexicon    The lexicon to populate with lexemes
     * @param signer     The signer for created items
     * @param maxSynsets Maximum synsets to process (0 = unlimited)
     * @return Import statistics
     */
    public ImportStats importInto(Lexicon lexicon, Signer signer, int maxSynsets) {
        long startTime = System.currentTimeMillis();

        try (LmfImporter importer = LmfImporter.fromResource(OEWN_PATH)) {
            // Track what we create
            Map<String, Sememe> synsetIdToSememe = new HashMap<>();
            AtomicInteger relationCount = new AtomicInteger(0);
            AtomicInteger synsetCount = new AtomicInteger(0);

            // First pass: create Sememes from synsets
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

            // Second pass: create Lexemes from lexical entries
            // Need a fresh importer since we consumed the first stream
            try (LmfImporter entryImporter = LmfImporter.fromResource(OEWN_PATH)) {
                AtomicInteger lexemeCount = new AtomicInteger(0);

                entryImporter.lexicalEntries().forEach(entry -> {
                    for (SenseRecord sense : entry.senses()) {
                        Sememe sememe = synsetIdToSememe.get(sense.synsetId());
                        if (sememe == null) continue;  // Synset not in our set (due to limit)

                        Lexeme lexeme = createLexeme(entry, sense, sememe);
                        lexicon.add(lexeme);
                        lexemeCount.incrementAndGet();
                    }
                });

                long duration = System.currentTimeMillis() - startTime;
                return new ImportStats(
                        synsetCount.get(),
                        lexemeCount.get(),
                        relationCount.get(),
                        duration,
                        "oewn2025"
                );
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to import English from OEWN", e);
        }
    }

    /**
     * Import all OEWN data (no limit).
     */
    public ImportStats importInto(Lexicon lexicon, Signer signer) {
        return importInto(lexicon, signer, 0);
    }

    // ==================================================================================
    // SEMEME CREATION
    // ==================================================================================

    private Sememe createSememe(SynsetRecord synset, Signer signer) {
        // Use ILI as canonical key if available, otherwise use synset ID
        String canonicalKey = synset.ili() != null && !synset.ili().isEmpty()
                ? "ili:" + synset.ili()
                : "oewn:" + synset.id();

        // Map LMF POS to our POS
        PartOfSpeech pos = mapPOS(synset.pos());

        // Build sources map
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
            if (target == null) continue;  // Target not in our set

            Relation relation = Relation.builder()
                    .subject(source.iid())
                    .predicate(predicate)
                    .object(Relation.iid(target.iid()))
                    .build()
                    .sign(signer);

            // Store relation in librarian
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

    private Lexeme createLexeme(LexicalEntryRecord entry, SenseRecord sense, Sememe sememe) {
        String lemma = entry.lemma();
        PartOfSpeech pos = mapPOS(entry.pos());

        // Calculate frequency based on sense order (first = most common)
        int senseIndex = entry.senses().indexOf(sense);
        float frequency = 1.0f / (senseIndex + 1);

        return Lexeme.builder()
                .word(lemma)
                .language(ENGLISH_ID)
                .sememe(sememe.iid())
                .partOfSpeech(pos)
                .frequency(frequency)
                .build();
    }

    // ==================================================================================
    // MAPPING HELPERS
    // ==================================================================================

    /**
     * Map LMF part-of-speech tags to Common Graph PartOfSpeech.
     */
    private PartOfSpeech mapPOS(String pos) {
        if (pos == null) return PartOfSpeech.NOUN;
        return switch (pos) {
            case "n" -> PartOfSpeech.NOUN;
            case "v" -> PartOfSpeech.VERB;
            case "a", "s" -> PartOfSpeech.ADJECTIVE;  // 's' is satellite adjective
            case "r" -> PartOfSpeech.ADVERB;
            default -> PartOfSpeech.NOUN;
        };
    }

    /**
     * Map LMF relation types to Common Graph semantic relation Sememes.
     *
     * @return The predicate ItemID, or null if the relation type should be skipped
     */
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
            default -> null;  // Skip unknown relation types
        };
    }
}
