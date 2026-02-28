package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * An interjection sememe — an exclamation or emotional expression.
 */
@Type(value = InterjectionSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class InterjectionSememe extends Sememe {

    public static final String KEY = "cg:type/interjection-sememe";

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected InterjectionSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected InterjectionSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public InterjectionSememe(String canonicalKey,
                              Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.INTERJECTION, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public InterjectionSememe(String canonicalKey,
                              Map<String, String> glosses, Map<String, String> sources,
                              List<String> tokens) {
        super(canonicalKey, PartOfSpeech.INTERJECTION, glosses, sources, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected InterjectionSememe(Librarian librarian, String canonicalKey,
                                 Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.INTERJECTION, glosses, sources);
    }
}
