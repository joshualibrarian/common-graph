package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.importer.EnglishImporter;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.Locale;

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
    private transient EnglishImporter.ImportStats stats;

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
        this.stats = importer.importInto(lexicon, signer, maxSynsets);
        return this;
    }

    // ==================================================================================
    // ACCESSORS
    // ==================================================================================

    /**
     * Get generation statistics (null if not yet generated).
     */
    public EnglishImporter.ImportStats stats() {
        return stats;
    }
}
