package dev.everydaythings.graph.language.importer;

import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameRecord;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.importer.WordNetImporter.*;
import dev.everydaythings.graph.runtime.Librarian;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base for importing a language from WordNet + UniMorph data.
 *
 * <p>3-pass import, producing pure frames:
 * <ol>
 *   <li><b>Pass 1: Synsets → Sememes</b> — each synset becomes a Sememe item,
 *       with semantic relation frames (hypernym, hyponym, etc.).</li>
 *   <li><b>Pass 2: Lexical entries → LEXEME frames</b> — each word-sense pairing
 *       becomes a LEXEME frame: {@code NAME:[LANGUAGE, POS, LEMMA] → "word"}
 *       with {@code THEME → sememe}.</li>
 *   <li><b>Pass 3: Inflected forms → LEXEME frames</b> — the morphology engine
 *       generates all inflected surface forms (regular + irregular) and stores
 *       each as a LEXEME frame with appropriate feature qualifiers.</li>
 * </ol>
 *
 * <p><b>Note on pre-generation:</b> Regular inflected forms ("running", "cats")
 * are pre-generated and stored as frames rather than computed at query time.
 * This trades storage for lookup speed and correctness — reverse morphology
 * (stemming/lemmatization) is error-prone for irregular forms. A stemmer
 * could be added as a fallback later if storage becomes a concern.
 */
public abstract class LanguageImporter {

    protected final Librarian librarian;

    protected LanguageImporter(Librarian librarian) {
        this.librarian = librarian;
    }

    // ==================================================================================
    // SUBCLASS HOOKS
    // ==================================================================================

    /** Classpath path to the GWN-LMF XML file. */
    protected abstract String wordnetResourcePath();

    /** Classpath path to the UniMorph TSV file, or null if unavailable. */
    protected abstract String unimorphResourcePath();

    /** The ItemID for this language. */
    protected abstract ItemID languageId();

    // ==================================================================================
    // IMPORT STATS
    // ==================================================================================

    public record ImportStats(
            int synsetCount,
            int lexemeCount,
            int relationCount,
            int inflectedFormCount,
            long durationMs,
            String source
    ) {}

    // ==================================================================================
    // THE 3-PASS IMPORT
    // ==================================================================================

    /**
     * Import wordnet data as pure frames.
     *
     * @param language   The Language instance (for morphology engine)
     * @param signer     The signer for created items
     * @param maxSynsets Maximum synsets to process (0 = unlimited)
     * @return Import statistics
     */
    public ImportStats importLanguage(Language language, Signer signer, int maxSynsets) {
        long startTime = System.currentTimeMillis();

        Map<String, List<UniMorphReader.Entry>> uniMorphData =
                unimorphResourcePath() != null ? UniMorphReader.load(unimorphResourcePath()) : Map.of();

        try (LmfImporter importer = LmfImporter.fromResource(wordnetResourcePath())) {
            Map<String, Sememe> synsetIdToSememe = new HashMap<>();
            AtomicInteger relationCount = new AtomicInteger(0);
            AtomicInteger synsetCount = new AtomicInteger(0);

            // ---- Pass 1: synsets → sememes + semantic relations ----
            importer.synsets()
                    .limit(maxSynsets > 0 ? maxSynsets : Long.MAX_VALUE)
                    .forEach(synset -> {
                        Sememe sememe = resolveSememe(synset, signer);
                        synsetIdToSememe.put(synset.id(), sememe);
                        synsetCount.incrementAndGet();

                        int rels = createRelations(synset, sememe, synsetIdToSememe, signer);
                        relationCount.addAndGet(rels);
                    });

            // ---- Pass 2: lexical entries → LEXEME frames (lemmas) ----
            // ---- Pass 3: inflected forms → LEXEME frames ----
            try (LmfImporter entryImporter = LmfImporter.fromResource(wordnetResourcePath())) {
                AtomicInteger lexemeCount = new AtomicInteger(0);
                AtomicInteger formCount = new AtomicInteger(0);

                entryImporter.lexicalEntries().forEach(entry -> {
                    for (SenseRecord sense : entry.senses()) {
                        Sememe sememe = synsetIdToSememe.get(sense.synsetId());
                        if (sememe == null) continue;

                        String lemma = entry.lemma();
                        ItemID pos = mapPOS(entry.pos());
                        int senseIndex = entry.senses().indexOf(sense);
                        float frequency = 1.0f / (senseIndex + 1);

                        // Pass 2: store lemma as LEXEME frame
                        storeLexemeFrame(sememe.iid(), lemma, pos,
                                GrammaticalFeature.Lemma.IID, frequency);
                        lexemeCount.incrementAndGet();

                        // Pass 3: store inflected forms as LEXEME frames
                        // Pre-generated for lookup speed — see class javadoc
                        int forms = storeInflectedForms(
                                sememe.iid(), lemma, pos, language, uniMorphData);
                        formCount.addAndGet(forms);
                    }
                });

                long duration = System.currentTimeMillis() - startTime;
                return new ImportStats(
                        synsetCount.get(),
                        lexemeCount.get(),
                        relationCount.get(),
                        formCount.get(),
                        duration,
                        sourceLabel(uniMorphData)
                );
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to import " + languageId() + " from " + wordnetResourcePath(), e);
        }
    }

    /** Import all data (no synset limit). */
    public ImportStats importLanguage(Language language, Signer signer) {
        return importLanguage(language, signer, 0);
    }

    // ==================================================================================
    // LEXEME FRAME CREATION
    // ==================================================================================

    /**
     * Store a LEXEME frame: NAME:[language, POS, feature] → word, THEME → sememe.
     */
    protected void storeLexemeFrame(ItemID sememeId, String word, ItemID pos,
                                     ItemID feature, float weight) {
        FrameBody body = FrameBody.builder(CoreVocabulary.Lexeme.IID)
                .bind(ThematicRole.Theme.IID, sememeId)
                .bind(ThematicRole.Value.IID)
                    .qualified(languageId(), pos, feature)
                    .to(word)
                    .identity(true).index(true)
                .build();

        librarian.storeFrame(body);
    }

    /**
     * Generate and store inflected forms for a lemma.
     *
     * <p>For each feature set the language distinguishes (e.g., {PAST}, {PLURAL}),
     * generates the surface form — checking UniMorph for irregulars first, then
     * falling back to regular morphology rules. Skips forms identical to the lemma.
     *
     * @return the number of inflected form frames stored
     */
    protected int storeInflectedForms(ItemID sememeId, String lemma, ItemID pos,
                                       Language language,
                                       Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        int count = 0;

        // Collect irregular overrides from UniMorph
        Map<Set<ItemID>, String> irregulars = findIrregulars(lemma, pos, language, uniMorphData);

        for (Set<ItemID> features : language.inflectionFeatures(pos)) {
            // Check irregular override first, fall back to regular morphology
            String form = irregulars.getOrDefault(features,
                    language.inflect(lemma, pos, features));

            // Skip if same as lemma (already indexed) or empty
            if (form == null || form.equals(lemma)) continue;

            librarian.storeFrame(buildInflectedLexemeFrame(sememeId, form, pos, features));
            count++;
        }

        return count;
    }

    /**
     * Build a LEXEME frame for an inflected form.
     * Qualifiers: [language, POS, feature1, feature2, ...]
     */
    private FrameBody buildInflectedLexemeFrame(ItemID sememeId, String form,
                                                 ItemID pos, Set<ItemID> features) {
        List<ItemID> quals = new ArrayList<>();
        quals.add(languageId());
        quals.add(pos);
        quals.addAll(features);

        return FrameBody.builder(CoreVocabulary.Lexeme.IID)
                .bind(ThematicRole.Theme.IID, sememeId)
                .bind(ThematicRole.Value.IID)
                    .qualified(quals.toArray(new ItemID[0]))
                    .to(form)
                    .identity(true).index(true)
                .build();
    }

    /**
     * Find UniMorph forms that differ from the regular algorithm — irregulars.
     */
    protected Map<Set<ItemID>, String> findIrregulars(String lemma, ItemID pos,
                                                       Language language,
                                                       Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        List<UniMorphReader.Entry> entries = uniMorphData.get(lemma.toLowerCase());
        if (entries == null || entries.isEmpty()) return Map.of();

        Map<Set<ItemID>, String> irregulars = new HashMap<>();

        for (UniMorphReader.Entry entry : entries) {
            if (!pos.equals(entry.pos())) continue;

            Set<ItemID> simplified = language.simplifyFeatures(entry.features(), pos);
            if (simplified.isEmpty()) continue;
            if (irregulars.containsKey(simplified)) continue;

            String regularForm = language.inflect(lemma, pos, simplified);
            if (!entry.form().equals(regularForm)) {
                irregulars.put(simplified, entry.form());
            }
        }

        return irregulars;
    }

    // ==================================================================================
    // SEMEME RESOLUTION
    // ==================================================================================

    protected Sememe resolveSememe(SynsetRecord synset, Signer signer) {
        Sememe existing = findExistingSememe(synset);
        if (existing != null) return existing;
        return createSememe(synset, signer);
    }

    protected Sememe findExistingSememe(SynsetRecord synset) {
        return null;
    }

    protected Sememe createSememe(SynsetRecord synset, Signer signer) {
        String canonicalKey = synset.ili() != null && !synset.ili().isEmpty()
                ? "ili:" + synset.ili()
                : sourcePrefix() + ":" + synset.id();

        Map<String, String> sources = new HashMap<>();
        sources.put(sourcePrefix(), synset.id());
        if (synset.ili() != null && !synset.ili().isEmpty()) {
            sources.put("ili", synset.ili());
        }

        String definition = synset.definition() != null ? synset.definition() : "";

        return Sememe.create(librarian, signer, canonicalKey,
                Map.of("en", definition), sources);
    }

    // ==================================================================================
    // RELATION CREATION
    // ==================================================================================

    protected int createRelations(SynsetRecord synset, Sememe source,
                                  Map<String, Sememe> synsetMap, Signer signer) {
        int count = 0;

        for (RelationRecord rel : synset.relations()) {
            ItemID predicate = mapRelationType(rel.type());
            if (predicate == null) continue;

            Sememe target = synsetMap.get(rel.target());
            if (target == null) continue;

            FrameBody body = FrameBody.builder(predicate)
                    .bind(ThematicRole.Theme.IID, source.iid())
                    .bind(ThematicRole.Goal.IID, target.iid())
                    .build();
            FrameRecord record = FrameRecord.create(body, signer);

            if (librarian != null) {
                librarian.library().storeFrame(body, record);
            }
            count++;
        }

        return count;
    }

    // ==================================================================================
    // UNIVERSAL MAPPINGS
    // ==================================================================================

    protected static ItemID mapPOS(String pos) {
        if (pos == null) return PartOfSpeech.NOUN;
        return switch (pos) {
            case "n" -> PartOfSpeech.NOUN;
            case "v" -> PartOfSpeech.VERB;
            case "a", "s" -> PartOfSpeech.ADJECTIVE;
            case "r" -> PartOfSpeech.ADVERB;
            default -> PartOfSpeech.NOUN;
        };
    }

    protected static ItemID mapRelationType(String relType) {
        return switch (relType) {
            case "hypernym", "instance_hypernym" -> LexicalVocabulary.Hypernym.IID;
            case "hyponym", "instance_hyponym" -> LexicalVocabulary.Hyponym.IID;
            case "holo_part", "holo_member", "holo_substance" -> LexicalVocabulary.Holonym.IID;
            case "mero_part", "mero_member", "mero_substance" -> LexicalVocabulary.Meronym.IID;
            case "antonym" -> LexicalVocabulary.Antonym.IID;
            case "similar" -> LexicalVocabulary.SimilarTo.IID;
            case "derivation" -> LexicalVocabulary.Derivation.IID;
            case "domain_topic", "domain_region", "domain_usage" -> LexicalVocabulary.Domain.IID;
            case "entails" -> LexicalVocabulary.Entails.IID;
            case "causes" -> LexicalVocabulary.Causes.IID;
            case "also" -> LexicalVocabulary.SeeAlso.IID;
            default -> null;
        };
    }

    protected String sourcePrefix() { return "wn"; }

    protected String sourceLabel(Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        return sourcePrefix() + (uniMorphData.isEmpty() ? "" : "+unimorph");
    }
}
