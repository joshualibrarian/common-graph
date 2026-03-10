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
 * A preposition sememe — assigns a thematic role to its object.
 *
 * <p>Prepositions are the bridge between predicate argument slots and noun arguments.
 * Each preposition declares what {@link Role} it assigns. For example,
 * "on" assigns TARGET — meaning its object is where the result goes.
 *
 * <p>This is the shared currency between predicate frame schemas and
 * expression parsing: predicates declare what roles they need (via ArgumentSlot),
 * prepositions declare what roles they assign, and the assembler matches them.
 */
@Type(value = PrepositionSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class PrepositionSememe extends Sememe {

    public static final String KEY = "cg:type/preposition-sememe";

    /**
     * The role sememe this preposition assigns to its object.
     *
     * <p>For example, ON has role TARGET — in "create chess on myItem",
     * the preposition "on" tells the evaluator that "myItem" fills the
     * TARGET role of the predicate "create".
     */
    @Getter
    @ContentField
    private ItemID assignedRole;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected PrepositionSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected PrepositionSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /**
     * Seed constructor with role.
     *
     * @param canonicalKey The canonical key (e.g., "cg.prep:on")
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @param tokens       Token aliases for lookup
     * @param assignedRole The role sememe this preposition assigns
     */
    public PrepositionSememe(String canonicalKey,
                             Map<String, String> glosses, Map<String, String> sources,
                             List<String> tokens, ItemID assignedRole) {
        super(canonicalKey, PartOfSpeech.PREPOSITION, glosses, sources, tokens);
        this.assignedRole = assignedRole;
    }

    /** Runtime constructor (with librarian). */
    protected PrepositionSememe(Librarian librarian, String canonicalKey,
                                Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.PREPOSITION, glosses, sources);
    }
}
