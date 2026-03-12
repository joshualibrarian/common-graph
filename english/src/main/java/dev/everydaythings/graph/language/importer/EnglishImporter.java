package dev.everydaythings.graph.language.importer;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * Imports English language data from Open English WordNet (OEWN) and UniMorph.
 *
 * <p>English is the first language imported into a fresh graph. It creates the
 * sememe space that later language imports merge into. The 3-pass import
 * (synsets → lexemes → inflected forms) is handled by {@link LanguageImporter};
 * this class provides only the English-specific resource paths and source prefix.
 *
 * <p>UniMorph data provides irregular form overrides (e.g., "run"→"ran").
 * If UniMorph data is not present on the classpath, import proceeds with
 * regular morphology rules only.
 *
 * <p>Usage (from English.generate):
 * <pre>{@code
 * EnglishImporter importer = new EnglishImporter(librarian);
 * LanguageImporter.ImportStats stats = importer.importInto(lexicon, language, signer, 100);
 * }</pre>
 */
public class EnglishImporter extends LanguageImporter {

    /** Path to OEWN LMF XML within resources. */
    private static final String OEWN_PATH = "/english-wordnet-2025.xml";

    /** Path to UniMorph English data within resources. */
    private static final String UNIMORPH_PATH = "/unimorph/eng";

    /** The English language ItemID. */
    private static final ItemID ENGLISH_ID = ItemID.fromString("cg.lang:english");

    public EnglishImporter(Librarian librarian) {
        super(librarian);
    }

    @Override
    protected String wordnetResourcePath() {
        return OEWN_PATH;
    }

    @Override
    protected String unimorphResourcePath() {
        return UNIMORPH_PATH;
    }

    @Override
    protected ItemID languageId() {
        return ENGLISH_ID;
    }

    @Override
    protected String sourcePrefix() {
        return "oewn";
    }
}
