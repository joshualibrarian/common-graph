package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
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
    // SEED INSTANCES (type system predicates)
    // ==================================================================================

    public static class ImplementedBy {
        public static final String KEY = "cg.type:implemented-by";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is implemented by; has its design applied by")
                .cili("i33787");
    }

    // ==================================================================================
    // SEED INSTANCES (semantic relations — WordNet pointer types)
    // ==================================================================================

    public static class Hypernym {
        public static final String KEY = "cg.rel:hypernym";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is a kind of; is a type of; is a subclass of")
                .cili("i69569");
    }

    public static class Hyponym {
        public static final String KEY = "cg.rel:hyponym";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "has subtype; has kind; is a superclass of")
                .cili("i69570");
    }

    public static class InstanceOf {
        public static final String KEY = "cg.rel:instance-of";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is an instance of; has type; is a member of class")
                .cili("i35284");
    }

    public static class Holonym {
        public static final String KEY = "cg.rel:holonym";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is a part of; is contained in")
                .cili("i69567");
    }

    public static class Meronym {
        public static final String KEY = "cg.rel:meronym";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "has as a part; contains")
                .cili("i69575");
    }

    public static class Antonym {
        public static final String KEY = "cg.rel:antonym";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is the opposite of; contrasts with")
                .cili("i69547");
    }

    public static class SimilarTo {
        public static final String KEY = "cg.rel:similar-to";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is similar to; resembles in meaning")
                .cili("i34992");
    }

    public static class Derivation {
        public static final String KEY = "cg.rel:derivation";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "is derivationally related to")
                .cili("i37467");
    }

    public static class Domain {
        public static final String KEY = "cg.rel:domain";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "belongs to domain; is in the category of")
                .cili("i68336");
    }

    public static class Entails {
        public static final String KEY = "cg.rel:entails";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "entails; necessarily implies")
                .cili("i34848");
    }

    public static class Causes {
        public static final String KEY = "cg.rel:causes";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "causes; brings about")
                .cili("i29966");
    }

    public static class SeeAlso {
        public static final String KEY = "cg.rel:see-also";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "see also; is related to")
                .cili("i25271");
    }

    // ==================================================================================
    // SEED INSTANCES (verb primitives — action vocabulary)
    // ==================================================================================

    public static class Create {
        public static final String KEY = "cg.verb:create";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "make or cause to be or to become").cili("i29849")
                .word(LEMMA, ENG, "create").word(LEMMA, ENG, "new").word(LEMMA, ENG, "make")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Target.KEY)
                .slot(ThematicRole.Name.KEY).slot(ThematicRole.Comitative.KEY)
                .slot(ThematicRole.Source.KEY);
    }

    public static class Get {
        public static final String KEY = "cg.verb:get";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "go or come after and bring or take back").cili("i28895")
                .word(LEMMA, ENG, "get").word(LEMMA, ENG, "retrieve")
                .word(LEMMA, ENG, "fetch").word(LEMMA, ENG, "lookup")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Put {
        public static final String KEY = "cg.verb:put";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "find a place for and put away for storage").cili("i33146")
                .word(LEMMA, ENG, "put").word(LEMMA, ENG, "store")
                .word(LEMMA, ENG, "add").word(LEMMA, ENG, "insert")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Target.KEY);
    }

    public static class Remove {
        public static final String KEY = "cg.verb:remove";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "remove something concrete, as by lifting, pushing, or taking off").cili("i22577")
                .word(LEMMA, ENG, "remove").word(LEMMA, ENG, "delete").word(LEMMA, ENG, "drop")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class ListVerb {
        public static final String KEY = "cg.verb:list";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "enumerate; list").cili("i26334")
                .word(LEMMA, ENG, "list").word(LEMMA, ENG, "enumerate")
                .word(LEMMA, ENG, "all").word(LEMMA, ENG, "tail").word(LEMMA, ENG, "latest");
    }

    public static class Import {
        public static final String KEY = "cg.verb:import";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "transfer electronic data into a database or document").cili("i32905")
                .word(LEMMA, ENG, "import").word(LEMMA, ENG, "ingest").word(LEMMA, ENG, "load")
                .slot(ThematicRole.Source.KEY);
    }

    public static class Query {
        public static final String KEY = "cg.verb:query";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "pose a question").cili("i25610")
                .word(LEMMA, ENG, "query").word(LEMMA, ENG, "search")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Find {
        public static final String KEY = "cg.verb:find";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "find items related by a predicate").cili("i33164")
                .word(LEMMA, ENG, "find").word(LEMMA, ENG, "lookup")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Recipient.KEY)
                .slot(ThematicRole.Source.KEY);
    }

    public static class Show {
        public static final String KEY = "cg.verb:show";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "make visible or apparent").cili("i32454")
                .word(LEMMA, ENG, "show").word(LEMMA, ENG, "display").word(LEMMA, ENG, "view")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Help {
        public static final String KEY = "cg.verb:help";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "give help or assistance; be of service").cili("i34433")
                .word(LEMMA, ENG, "help").word(LEMMA, ENG, "assist").word(LEMMA, ENG, "commands");
    }

    public static class Edit {
        public static final String KEY = "cg.verb:edit";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "prepare for publication or presentation by correcting, revising, or adapting").cili("i22726")
                .word(LEMMA, ENG, "edit").word(LEMMA, ENG, "modify").word(LEMMA, ENG, "change")
                .slot(ThematicRole.Patient.KEY);
    }

    public static class Count {
        public static final String KEY = "cg.verb:count";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "determine the number or amount of").cili("i26340")
                .word(LEMMA, ENG, "count").word(LEMMA, ENG, "size");
    }

    public static class Describe {
        public static final String KEY = "cg.verb:describe";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "give an account or representation of in words").cili("i26422")
                .word(LEMMA, ENG, "describe").word(LEMMA, ENG, "status").word(LEMMA, ENG, "info");
    }

    public static class Inspect {
        public static final String KEY = "cg.verb:inspect";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "look over carefully").cili("i32580")
                .word(LEMMA, ENG, "inspect").word(LEMMA, ENG, "examine");
    }

    public static class Cd {
        public static final String KEY = "cg.verb:cd";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "change directory; navigate to")
                .word(LEMMA, ENG, "cd").word(LEMMA, ENG, "go").word(LEMMA, ENG, "enter")
                .slot(ThematicRole.Target.KEY);
    }

    public static class Exit {
        public static final String KEY = "cg.session:exit";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "exit the session").cili("i31816")
                .word(LEMMA, ENG, "exit").word(LEMMA, ENG, "quit").word(LEMMA, ENG, "q");
    }

    public static class Back {
        public static final String KEY = "cg.session:back";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "go back to previous item")
                .symbol("..").word(LEMMA, ENG, "back").word(LEMMA, ENG, "pop");
    }

    // ==================================================================================
    // SEED INSTANCES (general interaction verbs)
    // ==================================================================================

    public static class Serve {
        public static final String KEY = "cg.verb:serve";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "work for or be a servant to").cili("i96785")
                .word(LEMMA, ENG, "serve").word(LEMMA, ENG, "use")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Invite {
        public static final String KEY = "cg.verb:invite";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "request someone's participation").cili("i32987")
                .word(LEMMA, ENG, "invite");
    }

    public static class Authenticate {
        public static final String KEY = "cg.session:authenticate";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "prove identity by demonstrating possession of private key").cili("i25047")
                .word(LEMMA, ENG, "authenticate").word(LEMMA, ENG, "auth").word(LEMMA, ENG, "login")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Switch {
        public static final String KEY = "cg.session:switch";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "change the active user for the current view").cili("i22420")
                .word(LEMMA, ENG, "switch").word(LEMMA, ENG, "as")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Rename {
        public static final String KEY = "cg.verb:rename";
        @Seed public static final VerbSememe SEED = new VerbSememe(KEY)
                .gloss(ENG, "assign a new name to").cili("i25424")
                .word(LEMMA, ENG, "rename").word(LEMMA, ENG, "name")
                .slot(ThematicRole.Theme.KEY);
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
