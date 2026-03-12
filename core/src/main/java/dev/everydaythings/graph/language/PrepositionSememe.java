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
 * Each preposition declares what {@link ThematicRole} it assigns. For example,
 * "on" assigns TARGET — meaning its object is where the result goes.
 *
 * <p>This is the shared currency between predicate frame schemas and
 * expression parsing: predicates declare what roles they need (via slot declarations),
 * prepositions declare what roles they assign, and the assembler matches them.
 */
@Type(value = PrepositionSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class PrepositionSememe extends Sememe {

    public static final String KEY = "cg:type/preposition-sememe";

    // ==================================================================================
    // SEED INSTANCES
    // ==================================================================================

    public static class On {
        public static final String KEY = "cg.prep:on";
        @Seed public static final PrepositionSememe SEED = new PrepositionSememe(KEY)
                .gloss(ENG, "indicating target or destination")
                .word(LEMMA, ENG, "on").word(LEMMA, ENG, "to").word(LEMMA, ENG, "into")
                .role(ThematicRole.Target.KEY);
    }

    public static class With {
        public static final String KEY = "cg.prep:with";
        @Seed public static final PrepositionSememe SEED = new PrepositionSememe(KEY)
                .gloss(ENG, "indicating tool or means")
                .word(LEMMA, ENG, "with").word(LEMMA, ENG, "using")
                .role(ThematicRole.Instrument.KEY);
    }

    public static class From {
        public static final String KEY = "cg.prep:from";
        @Seed public static final PrepositionSememe SEED = new PrepositionSememe(KEY)
                .gloss(ENG, "indicating origin or source")
                .word(LEMMA, ENG, "from")
                .role(ThematicRole.Source.KEY);
    }

    public static class For {
        public static final String KEY = "cg.prep:for";
        @Seed public static final PrepositionSememe SEED = new PrepositionSememe(KEY)
                .gloss(ENG, "indicating beneficiary or recipient")
                .word(LEMMA, ENG, "for")
                .role(ThematicRole.Recipient.KEY);
    }

    public static class Between {
        public static final String KEY = "cg.prep:between";
        @Seed public static final PrepositionSememe SEED = new PrepositionSememe(KEY)
                .gloss(ENG, "indicating companions or participants")
                .word(LEMMA, ENG, "between")
                .role(ThematicRole.Comitative.KEY);
    }

    public static class Named {
        public static final String KEY = "cg.prep:named";
        @Seed public static final PrepositionSememe SEED = new PrepositionSememe(KEY)
                .gloss(ENG, "indicating designation or label")
                .word(LEMMA, ENG, "named").word(LEMMA, ENG, "called")
                .role(ThematicRole.Name.KEY);
    }

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /**
     * The role sememe this preposition assigns to its object.
     *
     * <p>For example, ON has role TARGET — in "create chess on myItem",
     * the preposition "on" tells the evaluator that "myItem" fills the
     * TARGET role of the predicate "create".
     */
    @Getter
    @Frame
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

    /** Fluent seed constructor. */
    public PrepositionSememe(String canonicalKey) {
        super(canonicalKey, PartOfSpeech.PREPOSITION);
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

    // ==================================================================================
    // COVARIANT OVERRIDES (fluent chaining returns PrepositionSememe)
    // ==================================================================================

    /** Set the thematic role this preposition assigns, by canonical key. */
    public PrepositionSememe role(String roleKey) {
        this.assignedRole = ItemID.fromString(roleKey);
        return this;
    }

    @Override public PrepositionSememe gloss(String lang, String text) { super.gloss(lang, text); return this; }
    @Override public PrepositionSememe word(Sememe form, String lang, String surface) { super.word(form, lang, surface); return this; }
    @Override public PrepositionSememe cili(String id) { super.cili(id); return this; }
    @Override public PrepositionSememe symbol(String s) { super.symbol(s); return this; }
    @Override public PrepositionSememe indexWeight(int weight) { super.indexWeight(weight); return this; }
}
