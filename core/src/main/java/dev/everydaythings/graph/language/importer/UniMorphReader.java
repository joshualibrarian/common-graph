package dev.everydaythings.graph.language.importer;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;

import java.io.*;
import java.util.*;

/**
 * Reads UniMorph TSV morphological data files.
 *
 * <p>UniMorph format: {@code lemma\tform\tfeatures} (tab-separated).
 * Features are semicolon-separated UniMorph dimension tags (e.g., {@code V;PST}, {@code N;PL}).
 *
 * <p>UniMorph uses a universal feature schema across 160+ languages. This reader
 * maps those dimension tags to {@link GrammaticalFeature} IIDs. The tag mapping
 * is language-neutral — the same tags (PST, PL, CMPR, V.PTCP, etc.) appear in
 * every UniMorph language file.
 *
 * <p>After parsing, the raw feature sets need to be simplified to the minimal
 * combinations that a specific language's morphology engine uses. This
 * simplification is language-specific and handled by
 * {@link dev.everydaythings.graph.language.Language#simplifyFeatures}.
 *
 * @see GrammaticalFeature
 * @see dev.everydaythings.graph.language.Language#simplifyFeatures
 */
public class UniMorphReader {

    /**
     * A single UniMorph entry: an inflected form with its raw grammatical features.
     *
     * @param form     The inflected surface form (lowercase)
     * @param features Raw mapped feature IIDs (before language-specific simplification)
     * @param pos      Part of speech
     */
    public record Entry(String form, Set<ItemID> features, ItemID pos) {}

    // ==================================================================================
    // TAG MAPPING (universal across all UniMorph languages)
    // ==================================================================================

    /** UniMorph dimension tag to GrammaticalFeature IID. */
    private static final Map<String, ItemID> TAG_MAP = Map.ofEntries(
            // Tense
            Map.entry("PST", GrammaticalFeature.Past.IID),
            Map.entry("PRS", GrammaticalFeature.Present.IID),
            Map.entry("FUT", GrammaticalFeature.Future.IID),
            // Number
            Map.entry("SG", GrammaticalFeature.Singular.IID),
            Map.entry("PL", GrammaticalFeature.Plural.IID),
            // Person
            Map.entry("1", GrammaticalFeature.FirstPerson.IID),
            Map.entry("2", GrammaticalFeature.SecondPerson.IID),
            Map.entry("3", GrammaticalFeature.ThirdPerson.IID),
            // Form
            Map.entry("V.PTCP", GrammaticalFeature.Participle.IID),
            Map.entry("V.MSDR", GrammaticalFeature.Participle.IID),  // gerund = participle
            Map.entry("NFIN", GrammaticalFeature.Infinitive.IID),
            // Aspect
            Map.entry("PROG", GrammaticalFeature.Progressive.IID),
            Map.entry("PRF", GrammaticalFeature.Perfect.IID),
            // Mood
            Map.entry("IMP", GrammaticalFeature.Imperative.IID),
            Map.entry("SBJV", GrammaticalFeature.Subjunctive.IID),
            // Voice
            Map.entry("PASS", GrammaticalFeature.Passive.IID),
            // Degree
            Map.entry("CMPR", GrammaticalFeature.Comparative.IID),
            Map.entry("SPRL", GrammaticalFeature.Superlative.IID)
    );

    /** Tags that represent POS (extracted separately, not included in feature set). */
    private static final Set<String> POS_TAGS = Set.of("V", "N", "ADJ");

    /** Tags to ignore (indicative mood is the default, not distinctive). */
    private static final Set<String> IGNORE_TAGS = Set.of("IND");

    // ==================================================================================
    // LOADING
    // ==================================================================================

    /**
     * Load UniMorph data from a classpath resource.
     *
     * @param resourcePath Path to the TSV file on the classpath (e.g., "/unimorph/eng")
     * @return Map from lemma to list of entries, or empty map if not found
     */
    public static Map<String, List<Entry>> load(String resourcePath) {
        InputStream is = UniMorphReader.class.getResourceAsStream(resourcePath);
        if (is == null) {
            return Map.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return parse(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read UniMorph data from " + resourcePath, e);
        }
    }

    /**
     * Parse UniMorph TSV data from a reader.
     */
    public static Map<String, List<Entry>> parse(BufferedReader reader) throws IOException {
        Map<String, List<Entry>> result = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\t");
            if (parts.length < 3) continue;

            String lemma = parts[0].trim().toLowerCase();
            String form = parts[1].trim().toLowerCase();
            String tagString = parts[2].trim();

            // Skip base forms (form = lemma)
            if (form.equals(lemma)) continue;

            Entry entry = parseEntry(form, tagString);
            if (entry != null && !entry.features().isEmpty()) {
                result.computeIfAbsent(lemma, k -> new ArrayList<>()).add(entry);
            }
        }
        return result;
    }

    // ==================================================================================
    // TAG PARSING
    // ==================================================================================

    /**
     * Parse a UniMorph tag string into an Entry with raw mapped features.
     */
    private static Entry parseEntry(String form, String tagString) {
        String[] tags = tagString.split(";");
        Set<ItemID> features = new HashSet<>();
        ItemID pos = null;

        for (String tag : tags) {
            tag = tag.trim();
            if (tag.isEmpty()) continue;

            // Extract POS
            if (POS_TAGS.contains(tag)) {
                if ("V".equals(tag)) pos = PartOfSpeech.VERB;
                else if ("N".equals(tag)) pos = PartOfSpeech.NOUN;
                else if ("ADJ".equals(tag)) pos = PartOfSpeech.ADJECTIVE;
                continue;
            }

            // Skip non-distinctive tags
            if (IGNORE_TAGS.contains(tag)) continue;

            // Map to feature IID
            ItemID feature = TAG_MAP.get(tag);
            if (feature != null) {
                features.add(feature);
            }
        }

        if (pos == null) return null;
        return new Entry(form, features, pos);
    }
}
