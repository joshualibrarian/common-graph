package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * An adjective sememe — a property or quality.
 *
 * <p>Used for predicates describing properties (e.g., "reachable-at").
 */
@Type(value = AdjectiveSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class AdjectiveSememe extends Sememe {

    public static final String KEY = "cg:type/adjective-sememe";

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected AdjectiveSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected AdjectiveSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public AdjectiveSememe(String canonicalKey,
                           Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.ADJECTIVE, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public AdjectiveSememe(String canonicalKey,
                           Map<String, String> glosses, Map<String, String> sources,
                           List<String> tokens) {
        super(canonicalKey, PartOfSpeech.ADJECTIVE, glosses, sources, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected AdjectiveSememe(Librarian librarian, String canonicalKey,
                              Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.ADJECTIVE, glosses, sources);
    }
}
