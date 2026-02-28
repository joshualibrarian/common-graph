package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A conjunction sememe — connects clauses or coordinates elements.
 */
@Type(value = ConjunctionSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class ConjunctionSememe extends Sememe {

    public static final String KEY = "cg:type/conjunction-sememe";

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected ConjunctionSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected ConjunctionSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public ConjunctionSememe(String canonicalKey,
                             Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.CONJUNCTION, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public ConjunctionSememe(String canonicalKey,
                             Map<String, String> glosses, Map<String, String> sources,
                             List<String> tokens) {
        super(canonicalKey, PartOfSpeech.CONJUNCTION, glosses, sources, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected ConjunctionSememe(Librarian librarian, String canonicalKey,
                                Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.CONJUNCTION, glosses, sources);
    }
}
