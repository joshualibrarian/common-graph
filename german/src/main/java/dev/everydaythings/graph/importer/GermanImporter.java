package dev.everydaythings.graph.importer;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * Imports German language data from OdeNet (Open German WordNet) and UniMorph.
 *
 * <p>German is imported AFTER English — its synsets link to existing sememes
 * via CILI. New sememes are created for German-specific concepts that don't
 * exist in English.
 *
 * <p>The 3-pass import (synsets → lexemes → inflected forms) is handled by
 * {@link LanguageImporter}; this class provides only the German-specific
 * resource paths and source prefix.
 */
public class GermanImporter extends LanguageImporter {

    private static final String WORDNET_PATH = "/german-wordnet.xml";
    private static final String UNIMORPH_PATH = "/unimorph/deu";

    public GermanImporter(Librarian librarian) {
        super(librarian);
    }

    @Override
    protected String wordnetResourcePath() {
        return WORDNET_PATH;
    }

    @Override
    protected String unimorphResourcePath() {
        return UNIMORPH_PATH;
    }

    @Override
    protected ItemID languageId() {
        return Language.GERMAN;
    }

    @Override
    protected String sourcePrefix() {
        return "odenet";
    }
}
