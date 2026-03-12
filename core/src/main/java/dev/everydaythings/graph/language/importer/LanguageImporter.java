package dev.everydaythings.graph.language.importer;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
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
 * <p>This class encapsulates the universal 3-pass import pattern shared by all
 * Global WordNet Association (GWA) wordnets:
 * <ol>
 *   <li><b>Pass 1: Synsets → Sememes</b> — each synset is resolved to a Sememe
 *       (created or found by ILI), and semantic relations (hypernym, hyponym, etc.)
 *       are created between them.</li>
 *   <li><b>Pass 2: Lexical entries → Lexemes</b> — each word-sense pairing becomes
 *       a Lexeme in the language's Lexicon, with irregular form overrides from
 *       UniMorph data where available.</li>
 *   <li><b>Pass 3: Inflected form registration</b> — the language's morphology engine
 *       generates all inflected surface forms and registers them as TokenDictionary
 *       postings so that inflected words resolve directly to their sememes.</li>
 * </ol>
 *
 * <h3>English vs. later languages</h3>
 *
 * <p>The first language imported (English) creates all the sememes. Later languages
 * find most sememes already existing via ILI (Interlingual Index) — they just add
 * lexemes pointing to those existing sememes. Override {@link #resolveSememe} to
 * control this find-or-create behavior.
 *
 * <h3>How to add a new language</h3>
 *
 * <ol>
 *   <li>Create a {@link Language} subclass with morphology rules
 *       (see {@code English} for the pattern: override {@code regularInflection},
 *       {@code inflectionFeatures}, and optionally {@code simplifyFeatures})</li>
 *   <li>Create a {@code LanguageImporter} subclass providing:
 *       <ul>
 *         <li>{@link #wordnetResourcePath()} — path to the GWN-LMF XML on the classpath</li>
 *         <li>{@link #unimorphResourcePath()} — path to UniMorph TSV (or null if unavailable)</li>
 *         <li>{@link #languageId()} — the language's ItemID</li>
 *       </ul>
 *   </li>
 *   <li>Call {@link #importInto(Lexicon, Language, Signer, int)} from your language's
 *       bootstrap method</li>
 * </ol>
 *
 * <p>The GWN-LMF XML format and UniMorph TSV format are standardized across all
 * languages — the parsers ({@link LmfImporter}, {@link UniMorphReader}) are universal.
 * POS mapping and relation type mapping are also universal across all LMF wordnets.
 */
public abstract class LanguageImporter {

    protected final Librarian librarian;

    protected LanguageImporter(Librarian librarian) {
        this.librarian = librarian;
    }

    // ==================================================================================
    // SUBCLASS HOOKS — override these to configure your language's import
    // ==================================================================================

    /**
     * Classpath path to the GWN-LMF XML file for this language.
     *
     * <p>Examples: {@code "/english-wordnet-2025.xml"}, {@code "/spa-wordnet.xml"}
     *
     * <p>The file can be gzipped ({@code .xml.gz}) — {@link LmfImporter} handles
     * decompression automatically.
     */
    protected abstract String wordnetResourcePath();

    /**
     * Classpath path to the UniMorph TSV file for this language, or {@code null}
     * if UniMorph data is not available.
     *
     * <p>Examples: {@code "/unimorph/eng"}, {@code "/unimorph/spa"}, {@code null}
     *
     * <p>UniMorph covers 160+ languages. When present, it provides irregular form
     * overrides (e.g., "run"→"ran" for English, "ir"→"fui" for Spanish). When absent,
     * import proceeds with regular morphology rules only.
     */
    protected abstract String unimorphResourcePath();

    /**
     * The ItemID for this language (e.g., {@code ItemID.fromString("cg:language/eng")}).
     *
     * <p>Used as the language reference on created Lexemes.
     */
    protected abstract ItemID languageId();

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
    // THE 3-PASS IMPORT
    // ==================================================================================

    /**
     * Import wordnet data into a lexicon, with morphological form registration.
     *
     * @param lexicon    The lexicon to populate with lexemes
     * @param language   The Language instance (for morphology engine)
     * @param signer     The signer for created items
     * @param maxSynsets Maximum synsets to process (0 = unlimited)
     * @return Import statistics
     */
    public ImportStats importInto(Lexicon lexicon, Language language, Signer signer, int maxSynsets) {
        long startTime = System.currentTimeMillis();

        // Pre-load UniMorph irregular form data (empty map if not available)
        String umPath = unimorphResourcePath();
        Map<String, List<UniMorphReader.Entry>> uniMorphData =
                umPath != null ? UniMorphReader.load(umPath) : Map.of();

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

            // ---- Pass 2: lexical entries → lexemes (with UniMorph overrides) ----
            try (LmfImporter entryImporter = LmfImporter.fromResource(wordnetResourcePath())) {
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

                // ---- Pass 3: register all inflected forms as postings ----
                int formCount = lexicon.registerInflectedForms(language);

                long duration = System.currentTimeMillis() - startTime;
                return new ImportStats(
                        synsetCount.get(),
                        lexemeCount.get(),
                        relationCount.get(),
                        formCount,
                        duration,
                        sourceLabel(uniMorphData)
                );
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to import " + languageId() + " from " + wordnetResourcePath(), e);
        }
    }

    /**
     * Import all data (no synset limit).
     */
    public ImportStats importInto(Lexicon lexicon, Language language, Signer signer) {
        return importInto(lexicon, language, signer, 0);
    }

    // ==================================================================================
    // SEMEME RESOLUTION — find existing or create new
    // ==================================================================================

    /**
     * Resolve a synset to a Sememe: find an existing one, or create a new one.
     *
     * <p>This is the same process for ALL language imports — including the first.
     * Even English must merge with seed sememes (HYPERNYM, HYPONYM, etc.) that
     * already exist before any wordnet import runs. Later languages will find
     * most sememes already existing via ILI (Interlingual Index).
     *
     * <p>The default implementation calls {@link #findExistingSememe} first. If
     * that returns a match, it's used directly. Otherwise, a new sememe is created
     * via {@link #createSememe}.
     *
     * @param synset The wordnet synset
     * @param signer The signer for any newly created sememes
     * @return The resolved Sememe (existing or newly created)
     */
    protected Sememe resolveSememe(SynsetRecord synset, Signer signer) {
        Sememe existing = findExistingSememe(synset);
        if (existing != null) return existing;
        return createSememe(synset, signer);
    }

    /**
     * Try to find a sememe that already represents this synset's concept.
     *
     * <p>The primary match key is the ILI (Interlingual Index), which is the
     * global concept identifier shared across all wordnets. A synset with
     * {@code ili=i35403} in English and {@code ili=i35403} in Spanish represent
     * the same concept and should resolve to the same sememe.
     *
     * <p>The default implementation returns {@code null} (no existing sememe found).
     * Override this once the librarian has lookup-by-canonical-key support. The
     * canonical key for ILI-linked sememes is {@code "ili:<ili-id>"}.
     *
     * <p>Even seed sememes (HYPERNYM, etc.) should be findable once they're indexed
     * by ILI — their ILI mapping is established during the initial English import.
     *
     * @param synset The wordnet synset to find a match for
     * @return The existing Sememe, or {@code null} if none found
     */
    protected Sememe findExistingSememe(SynsetRecord synset) {
        // TODO: look up by canonical key "ili:<ili>" or by source ID once
        //       librarian supports indexed lookup by canonical key.
        //       For now, always falls through to createSememe().
        return null;
    }

    /**
     * Create a new Sememe from a synset record.
     *
     * <p>Uses the ILI (Interlingual Index) as the canonical key when available,
     * falling back to a source-specific key. The canonical key is what enables
     * later imports (even re-imports of the same language) to find and merge
     * into the same sememe.
     */
    protected Sememe createSememe(SynsetRecord synset, Signer signer) {
        String canonicalKey = synset.ili() != null && !synset.ili().isEmpty()
                ? "ili:" + synset.ili()
                : sourcePrefix() + ":" + synset.id();

        PartOfSpeech pos = mapPOS(synset.pos());

        Map<String, String> sources = new HashMap<>();
        sources.put(sourcePrefix(), synset.id());
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

    /**
     * Short prefix identifying the wordnet source.
     *
     * <p>Used in canonical keys for synsets without ILI, and in source metadata.
     * Default is {@code "wn"} (generic wordnet). Override for specific sources
     * (e.g., {@code "oewn"} for Open English WordNet).
     */
    protected String sourcePrefix() {
        return "wn";
    }

    // ==================================================================================
    // RELATION CREATION
    // ==================================================================================

    /**
     * Create semantic relations for a synset.
     *
     * <p>Maps LMF relation types to CG predicate sememes (hypernym, hyponym, etc.)
     * and creates signed Relations. The relation type mapping is universal across
     * all GWN-LMF wordnets.
     */
    protected int createRelations(SynsetRecord synset, Sememe source,
                                  Map<String, Sememe> synsetMap, Signer signer) {
        int count = 0;

        for (RelationRecord rel : synset.relations()) {
            ItemID predicate = mapRelationType(rel.type());
            if (predicate == null) continue;

            Sememe target = synsetMap.get(rel.target());
            if (target == null) continue;

            Relation relation = Relation.builder()
                    .predicate(predicate)
                    .bind(ThematicRole.Theme.SEED.iid(), Relation.iid(source.iid()))
                    .bind(ThematicRole.Target.SEED.iid(), Relation.iid(target.iid()))
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

    /**
     * Create a Lexeme from a lexical entry + sense + resolved sememe.
     *
     * <p>Checks UniMorph data for irregular form overrides. If UniMorph has a form
     * that differs from what the regular morphology algorithm would produce, it's
     * stored as an override on the Lexeme.
     */
    protected Lexeme createLexeme(LexicalEntryRecord entry, SenseRecord sense, Sememe sememe,
                                  Language language, Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        String lemma = entry.lemma();
        PartOfSpeech pos = mapPOS(entry.pos());

        int senseIndex = entry.senses().indexOf(sense);
        float frequency = 1.0f / (senseIndex + 1);

        List<FormEntry> overrides = findIrregularOverrides(lemma, pos, language, uniMorphData);

        return Lexeme.builder()
                .word(lemma)
                .language(languageId())
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
    protected List<FormEntry> findIrregularOverrides(String lemma, PartOfSpeech pos,
                                                     Language language,
                                                     Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        List<UniMorphReader.Entry> entries = uniMorphData.get(lemma.toLowerCase());
        if (entries == null || entries.isEmpty()) return List.of();

        List<FormEntry> overrides = new ArrayList<>();
        Set<Set<ItemID>> seen = new HashSet<>();

        for (UniMorphReader.Entry entry : entries) {
            if (entry.pos() != pos) continue;

            Set<ItemID> simplified = language.simplifyFeatures(entry.features(), pos);
            if (simplified.isEmpty()) continue;
            if (seen.contains(simplified)) continue;
            seen.add(simplified);

            String regularForm = language.inflect(lemma, pos, simplified);
            if (!entry.form().equals(regularForm)) {
                overrides.add(FormEntry.of(simplified, entry.form()));
            }
        }

        return overrides;
    }

    // ==================================================================================
    // UNIVERSAL MAPPINGS — shared by all GWN-LMF wordnets
    // ==================================================================================

    /**
     * Map an LMF part-of-speech code to a CG {@link PartOfSpeech}.
     *
     * <p>The codes (n, v, a, s, r) are standardized in the GWN-LMF schema and
     * are the same across all wordnets.
     */
    protected static PartOfSpeech mapPOS(String pos) {
        if (pos == null) return PartOfSpeech.NOUN;
        return switch (pos) {
            case "n" -> PartOfSpeech.NOUN;
            case "v" -> PartOfSpeech.VERB;
            case "a", "s" -> PartOfSpeech.ADJECTIVE;
            case "r" -> PartOfSpeech.ADVERB;
            default -> PartOfSpeech.NOUN;
        };
    }

    /**
     * Map an LMF relation type to a CG predicate ItemID.
     *
     * <p>These relation types are standardized in the GWN-LMF schema. Returns
     * {@code null} for unrecognized or unmapped types (they are silently skipped).
     */
    protected static ItemID mapRelationType(String relType) {
        return switch (relType) {
            case "hypernym", "instance_hypernym" -> VerbSememe.Hypernym.SEED.iid();
            case "hyponym", "instance_hyponym" -> VerbSememe.Hyponym.SEED.iid();
            case "holo_part", "holo_member", "holo_substance" -> VerbSememe.Holonym.SEED.iid();
            case "mero_part", "mero_member", "mero_substance" -> VerbSememe.Meronym.SEED.iid();
            case "antonym" -> VerbSememe.Antonym.SEED.iid();
            case "similar" -> VerbSememe.SimilarTo.SEED.iid();
            case "derivation" -> VerbSememe.Derivation.SEED.iid();
            case "domain_topic", "domain_region", "domain_usage" -> VerbSememe.Domain.SEED.iid();
            case "entails" -> VerbSememe.Entails.SEED.iid();
            case "causes" -> VerbSememe.Causes.SEED.iid();
            case "also" -> VerbSememe.SeeAlso.SEED.iid();
            default -> null;
        };
    }

    // ==================================================================================
    // SOURCE LABEL
    // ==================================================================================

    /**
     * Build a human-readable source label for import stats.
     */
    protected String sourceLabel(Map<String, List<UniMorphReader.Entry>> uniMorphData) {
        String base = sourcePrefix();
        return base + (uniMorphData.isEmpty() ? "" : "+unimorph");
    }
}
