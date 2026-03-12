package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A verb sememe — an action or process.
 *
 * <p>Verbs are the primary dispatch targets in the evaluator. They declare
 * what actions are available on items. Examples: create, get, move, resign.
 *
 * <p>Each verb declares its expected roles via {@link #slot(String)} or
 * {@link #slot(Sememe)}, populating the transient {@link #slots()} list.
 * The assembler matches preposition-tagged and bare-noun arguments against
 * these slots to build a SemanticFrame.
 */
@Type(value = VerbSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class VerbSememe extends Sememe {

    public static final String KEY = "cg:type/verb-sememe";

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

    /** Fluent seed constructor — use with chained .gloss(), .token(), .cili(), etc. */
    public VerbSememe(String canonicalKey) {
        super(canonicalKey, PartOfSpeech.VERB);
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
    // SLOT ROLES
    // ==================================================================================

    /**
     * Returns the role IIDs this verb expects as arguments (null-safe).
     *
     * <p>Derived from the transient {@link #slots()} field populated during
     * seed construction. Since all verbs with slots are seeds (code-defined),
     * transient-only is fine — no persistence needed.
     */
    public List<ItemID> slotRoles() {
        List<ItemID> s = slots();
        return s != null ? s : List.of();
    }

    // ==================================================================================
    // COVARIANT OVERRIDES (fluent chaining returns VerbSememe)
    // ==================================================================================

    @Override public VerbSememe gloss(String lang, String text) { super.gloss(lang, text); return this; }
    @Override public VerbSememe word(Sememe form, String lang, String surface) { super.word(form, lang, surface); return this; }
    @Override public VerbSememe cili(String id) { super.cili(id); return this; }
    @Override public VerbSememe symbol(String s) { super.symbol(s); return this; }
    @Override public VerbSememe slot(Sememe role) { super.slot(role); return this; }
    @Override public VerbSememe slot(String roleKey) { super.slot(roleKey); return this; }
    @Override public VerbSememe indexWeight(int weight) { super.indexWeight(weight); return this; }
}
