package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A noun sememe — an entity, concept, or thing.
 *
 * <p>Nouns serve as predicates in relations (e.g., AUTHOR, TITLE) and
 * as arguments to verbs (e.g., the thing being created).
 *
 * <p>This is the primary extension point for domain-specific noun types
 * that carry meaning beyond a plain noun:
 * <ul>
 *   <li>{@link dev.everydaythings.graph.value.Operator} — symbol, precedence, evaluation</li>
 *   <li>Function — arity, parameters, evaluation</li>
 *   <li>{@link dev.everydaythings.graph.value.Unit} — dimension, conversion factor</li>
 *   <li>{@link dev.everydaythings.graph.value.Dimension} — base unit</li>
 * </ul>
 *
 * <p>All domain nouns inherit glosses, tokens, symbols, and dictionary
 * registration from {@link Sememe}, making them discoverable through
 * the same vocabulary pipeline as any other sememe.
 */
@Type(value = NounSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public class NounSememe extends Sememe {

    public static final String KEY = "cg:type/noun-sememe";

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected NounSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected NounSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public NounSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public NounSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources,
                      List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources, tokens);
    }

    /** Seed constructor (with symbols and tokens). */
    public NounSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources,
                      List<String> symbols, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources, symbols, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected NounSememe(Librarian librarian, String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.NOUN, glosses, sources);
    }
}
