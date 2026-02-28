package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A pronoun sememe — a reference or variable placeholder.
 *
 * <p>Used for query pattern elements like ANY (wildcard) and WHAT (variable).
 */
@Type(value = PronounSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class PronounSememe extends Sememe {

    public static final String KEY = "cg:type/pronoun-sememe";

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected PronounSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected PronounSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public PronounSememe(String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.PRONOUN, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public PronounSememe(String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources,
                         List<String> tokens) {
        super(canonicalKey, PartOfSpeech.PRONOUN, glosses, sources, tokens);
    }

    /** Seed constructor (with symbols and tokens). */
    public PronounSememe(String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources,
                         List<String> symbols, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.PRONOUN, glosses, sources, symbols, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected PronounSememe(Librarian librarian, String canonicalKey,
                            Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.PRONOUN, glosses, sources);
    }
}
