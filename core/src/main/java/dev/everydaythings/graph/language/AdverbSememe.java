package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * An adverb sememe — modifies a verb, adjective, or other adverb.
 */
@Type(value = AdverbSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class AdverbSememe extends Sememe {

    public static final String KEY = "cg:type/adverb-sememe";

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected AdverbSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected AdverbSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public AdverbSememe(String canonicalKey,
                        Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.ADVERB, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public AdverbSememe(String canonicalKey,
                        Map<String, String> glosses, Map<String, String> sources,
                        List<String> tokens) {
        super(canonicalKey, PartOfSpeech.ADVERB, glosses, sources, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected AdverbSememe(Librarian librarian, String canonicalKey,
                           Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.ADVERB, glosses, sources);
    }
}
