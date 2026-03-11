package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * A verb sememe — an action or process.
 *
 * <p>Verbs are the primary dispatch targets in the evaluator. They declare
 * what actions are available on items. Examples: create, get, move, resign.
 *
 * <p>Each verb carries an argument structure ({@link #arguments()}) describing
 * what thematic roles it expects. This is the verb's valency frame — the
 * semantic slots that must or may be filled by the user's expression.
 * The assembler matches preposition-tagged and bare-noun arguments against
 * these slots to build a SemanticFrame.
 */
@Type(value = VerbSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class VerbSememe extends Sememe {

    public static final String KEY = "cg:type/verb-sememe";

    // ==================================================================================
    // ARGUMENT STRUCTURE
    // ==================================================================================

    /**
     * The verb's argument slots (valency frame).
     *
     * <p>Describes what thematic roles this verb expects. For example,
     * CREATE has optional THEME ("what to create") and optional TARGET
     * ("where to place the result").
     *
     * <p>Empty for relation verbs (HYPERNYM, etc.) which are predicates,
     * not user-facing actions.
     */
    @Frame
    private List<ArgumentSlot> arguments;

    /**
     * Returns the argument slots, never null.
     *
     * <p>The field is left uninitialized to avoid overwriting hydrated values
     * (Java field initializers run after super constructors). This getter
     * provides a null-safe view.
     */
    public List<ArgumentSlot> arguments() {
        return arguments != null ? arguments : List.of();
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected VerbSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected VerbSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor (no tokens). */
    public VerbSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.VERB, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public VerbSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources,
                      List<String> tokens) {
        super(canonicalKey, PartOfSpeech.VERB, glosses, sources, tokens);
    }

    /** Seed constructor (with symbols and tokens). */
    public VerbSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources,
                      List<String> symbols, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.VERB, glosses, sources, symbols, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected VerbSememe(Librarian librarian, String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.VERB, glosses, sources);
    }

    // ==================================================================================
    // FLUENT CONFIGURATION
    // ==================================================================================

    /**
     * Set the argument slots for this verb (fluent builder for seed declarations).
     *
     * @param slots The argument slots defining this verb's valency frame
     * @return this (for chaining)
     */
    public VerbSememe withArguments(ArgumentSlot... slots) {
        this.arguments = List.of(slots);
        return this;
    }
}
