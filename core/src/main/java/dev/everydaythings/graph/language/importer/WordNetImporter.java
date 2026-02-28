package dev.everydaythings.graph.language.importer;

import java.io.Closeable;
import java.util.List;
import java.util.stream.Stream;

/**
 * Abstract base for importing WordNet-like data into the graph.
 *
 * <p>This provides a common interface for importing lexical data from
 * various sources (GWN-LMF XML, Princeton WNDB, etc.) into Common Graph's
 * Language/Lexicon/Sememe structure.
 *
 * <p>Implementations should stream data rather than loading everything
 * into memory, as WordNet files can be 100MB+.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var importer = new LmfImporter(path)) {
 *     importer.synsets().forEach(synset -> {
 *         // Create Sememe from synset
 *     });
 *     importer.lexicalEntries().forEach(entry -> {
 *         // Create Lexemes from entry
 *     });
 * }
 * }</pre>
 */
public abstract class WordNetImporter implements Closeable {

    // ==================================================================================
    // ABSTRACT METHODS
    // ==================================================================================

    /**
     * Stream all synsets (meaning units) from the source.
     *
     * <p>Each synset becomes a Sememe in the graph.
     */
    public abstract Stream<SynsetRecord> synsets();

    /**
     * Stream all lexical entries (words) from the source.
     *
     * <p>Each entry's senses become Lexemes in the graph.
     */
    public abstract Stream<LexicalEntryRecord> lexicalEntries();

    /**
     * Get metadata about the source.
     */
    public abstract SourceMetadata metadata();

    // ==================================================================================
    // RECORDS
    // ==================================================================================

    /**
     * A synset (synonym set) - a meaning unit.
     *
     * @param id         Unique ID within the source (e.g., "oewn-00001740-n")
     * @param ili        Interlingual Index reference (e.g., "i35545") - links across languages
     * @param pos        Part of speech: n, v, a, r (noun, verb, adj, adv)
     * @param members    Space-separated list of member word IDs
     * @param definition The gloss/definition text
     * @param examples   Usage examples
     * @param relations  Semantic relations to other synsets
     */
    public record SynsetRecord(
            String id,
            String ili,
            String pos,
            String members,
            String definition,
            List<String> examples,
            List<RelationRecord> relations
    ) {
        /**
         * Extract the numeric offset from the ID (for compatibility with PWN format).
         * e.g., "oewn-00001740-n" -> 1740
         */
        public long offset() {
            // Format: prefix-NNNNNNNN-p
            String[] parts = id.split("-");
            if (parts.length >= 2) {
                try {
                    return Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }
    }

    /**
     * A lexical entry (word form with its senses).
     *
     * @param id     Unique ID (e.g., "oewn-cat-n")
     * @param lemma  The written form (e.g., "cat")
     * @param pos    Part of speech
     * @param senses The word's senses (links to synsets)
     */
    public record LexicalEntryRecord(
            String id,
            String lemma,
            String pos,
            List<SenseRecord> senses
    ) {}

    /**
     * A sense (word-meaning pairing).
     *
     * @param id              Unique sense ID
     * @param synsetId        The synset this sense belongs to
     * @param senseRelations  Relations at the sense level (antonyms, pertainyms, etc.)
     */
    public record SenseRecord(
            String id,
            String synsetId,
            List<RelationRecord> senseRelations
    ) {}

    /**
     * A semantic relation between synsets or senses.
     *
     * @param type   Relation type (hypernym, hyponym, holonym, meronym, etc.)
     * @param target Target synset or sense ID
     */
    public record RelationRecord(
            String type,
            String target
    ) {}

    /**
     * Metadata about the WordNet source.
     */
    public record SourceMetadata(
            String id,
            String label,
            String language,
            String version,
            String license,
            String url
    ) {}
}
