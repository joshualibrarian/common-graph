package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A Sememe is a unit of meaning, like "meters" are a unit of measure.
 *
 * <p>Abstract base for all meaning-carrying types. Each part of speech
 * has its own subclass carrying POS-specific data:
 * <ul>
 *   <li>{@link VerbSememe} — actions (create, get, move)</li>
 *   <li>{@link NounSememe} — entities, predicates, and domain-specific nouns
 *       (author, title, operators, functions, units, dimensions)</li>
 *   <li>{@link PrepositionSememe} — thematic role carriers (on, from, with)</li>
 *   <li>{@link PronounSememe} — references, variables (any, what)</li>
 *   <li>{@link AdjectiveSememe} — properties (reachable-at)</li>
 *   <li>{@link AdverbSememe} — modifiers</li>
 *   <li>{@link ConjunctionSememe} — connectors</li>
 *   <li>{@link InterjectionSememe} — exclamations</li>
 * </ul>
 *
 * <p>{@link NounSememe} is the primary extension point for domain-specific
 * types that carry meaning: operators (+, -, *), functions (sqrt, abs),
 * units (meter, kilogram), dimensions (length, mass), etc. These are all
 * nouns with extra metadata — they inherit glosses, tokens, symbols, and
 * dictionary registration from this class.
 *
 * <p>Shared fields (canonicalKey, pos, glosses, sources, tokens, indexWeight)
 * live here. POS-specific fields live in the subclass (e.g.,
 * {@link PrepositionSememe#assignedRole()}).
 *
 * <p>Sememes are anchored globally and used as predicates in relations.
 * Their IIDs are deterministically derived from their canonical key,
 * enabling compile-time references.
 */
@Type(value = Sememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public abstract class Sememe extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/sememe";

    // ==================================================================================
    // SEED INSTANCES (core predicates)
    // ==================================================================================

    public static class Author {
        public static final String KEY = "cg.core:author";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the creator or originator of a work")
                .cili("i90183");
    }

    public static class CreatedAt {
        public static final String KEY = "cg.core:created-at";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the time at which something was created")
                .cili("i36666");
    }

    public static class ModifiedAt {
        public static final String KEY = "cg.core:modified-at";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the time at which something was last modified")
                .cili("i22389");
    }

    public static class Title {
        public static final String KEY = "cg.core:title";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the name or title of something")
                .cili("i69816")
                .indexWeight(1000);
    }

    public static class Description {
        public static final String KEY = "cg.core:description";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "a textual description of something")
                .cili("i71841")
                .indexWeight(500);
    }

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

    /** A position in a frame that expects a particular thematic role. */
    public static class Slot {
        public static final String KEY = "cg.core:slot";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "a position in a frame that expects a particular role")
                .word(LEMMA, ENG, "slot");
    }

    /** A word↔meaning mapping in a language's lexicon. */
    public static class Lexeme {
        public static final String KEY = "cg.core:lexeme";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "a word-meaning mapping in a language's lexicon")
                .word(LEMMA, ENG, "lexeme");
    }

    /** How often something occurs (e.g., word frequency in a corpus). */
    public static class Frequency {
        public static final String KEY = "cg.core:frequency";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "how often something occurs")
                .word(LEMMA, ENG, "frequency")
                .cili("i73785");
    }

    /** The origin or source of information. */
    public static class Provenance {
        public static final String KEY = "cg.core:provenance";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the origin or source of information")
                .word(LEMMA, ENG, "provenance")
                .cili("i77490");
    }

    // --- Backward-compatible aliases (deprecated) ---

    /** @deprecated Use {@link Author#SEED} */
    @Deprecated public static final NounSememe AUTHOR = Author.SEED;
    /** @deprecated Use {@link CreatedAt#SEED} */
    @Deprecated public static final NounSememe CREATED_AT = CreatedAt.SEED;
    /** @deprecated Use {@link ModifiedAt#SEED} */
    @Deprecated public static final NounSememe MODIFIED_AT = ModifiedAt.SEED;
    /** @deprecated Use {@link Title#SEED} */
    @Deprecated public static final NounSememe TITLE = Title.SEED;
    /** @deprecated Use {@link Description#SEED} */
    @Deprecated public static final NounSememe DESCRIPTION = Description.SEED;
    /** @deprecated Use {@link ImplementedBy#SEED} */
    @Deprecated public static final VerbSememe IMPLEMENTED_BY = ImplementedBy.SEED;
    /** @deprecated Use {@link Hypernym#SEED} */
    @Deprecated public static final VerbSememe HYPERNYM = Hypernym.SEED;
    /** @deprecated Use {@link Hyponym#SEED} */
    @Deprecated public static final VerbSememe HYPONYM = Hyponym.SEED;
    /** @deprecated Use {@link InstanceOf#SEED} */
    @Deprecated public static final VerbSememe INSTANCE_OF = InstanceOf.SEED;
    /** @deprecated Use {@link Holonym#SEED} */
    @Deprecated public static final VerbSememe HOLONYM = Holonym.SEED;
    /** @deprecated Use {@link Meronym#SEED} */
    @Deprecated public static final VerbSememe MERONYM = Meronym.SEED;
    /** @deprecated Use {@link Antonym#SEED} */
    @Deprecated public static final VerbSememe ANTONYM = Antonym.SEED;
    /** @deprecated Use {@link SimilarTo#SEED} */
    @Deprecated public static final VerbSememe SIMILAR_TO = SimilarTo.SEED;
    /** @deprecated Use {@link Derivation#SEED} */
    @Deprecated public static final VerbSememe DERIVATION = Derivation.SEED;
    /** @deprecated Use {@link Domain#SEED} */
    @Deprecated public static final VerbSememe DOMAIN = Domain.SEED;
    /** @deprecated Use {@link Entails#SEED} */
    @Deprecated public static final VerbSememe ENTAILS = Entails.SEED;
    /** @deprecated Use {@link Causes#SEED} */
    @Deprecated public static final VerbSememe CAUSES = Causes.SEED;
    /** @deprecated Use {@link SeeAlso#SEED} */
    @Deprecated public static final VerbSememe SEE_ALSO = SeeAlso.SEED;


    // ==================================================================================
    // SEED INSTANCES (verb primitives - for action vocabulary)
    // ==================================================================================

    public static final String CREATE = "cg.verb:create";

    @Seed
    public static final VerbSememe create = new VerbSememe(
            CREATE,
            Map.of("en", "make or cause to be or to become"),
            Map.of("cili", "i29849"),
            List.of("create", "new", "make")
    ).slot(ThematicRole.Theme.KEY)
     .slot(ThematicRole.Target.KEY)
     .slot(ThematicRole.Name.KEY)
     .slot(ThematicRole.Comitative.KEY)
     .slot(ThematicRole.Source.KEY);

    public static final String GET = "cg.verb:get";

    @Seed
    public static final VerbSememe get = new VerbSememe(
            GET,
            Map.of("en", "go or come after and bring or take back"),
            Map.of("cili", "i28895"),
            List.of("get", "retrieve", "fetch", "lookup")
    ).slot(ThematicRole.Theme.KEY);

    public static final String PUT = "cg.verb:put";

    @Seed
    public static final VerbSememe put = new VerbSememe(
            PUT,
            Map.of("en", "find a place for and put away for storage"),
            Map.of("cili", "i33146"),
            List.of("put", "store", "add", "insert")
    ).slot(ThematicRole.Theme.KEY)
     .slot(ThematicRole.Target.KEY);

    public static final String REMOVE = "cg.verb:remove";

    @Seed
    public static final VerbSememe remove = new VerbSememe(
            REMOVE,
            Map.of("en", "remove something concrete, as by lifting, pushing, or taking off"),
            Map.of("cili", "i22577"),
            List.of("remove", "delete", "drop")
    ).slot(ThematicRole.Theme.KEY);

    public static final String LIST = "cg.verb:list";

    @Seed
    public static final VerbSememe list = new VerbSememe(
            LIST,
            Map.of("en", "enumerate; list"),
            Map.of("cili", "i26334"),
            List.of("list", "enumerate", "all", "tail", "latest")
    );

    public static final String IMPORT = "cg.verb:import";

    @Seed
    public static final VerbSememe importSeed = new VerbSememe(
            IMPORT,
            Map.of("en", "transfer electronic data into a database or document"),
            Map.of("cili", "i32905"),
            List.of("import", "ingest", "load")
    ).slot(ThematicRole.Source.KEY);

    public static final String QUERY = "cg.verb:query";

    @Seed
    public static final VerbSememe query = new VerbSememe(
            QUERY,
            Map.of("en", "pose a question"),
            Map.of("cili", "i25610"),
            List.of("query", "search")
    ).slot(ThematicRole.Theme.KEY);

    public static final String FIND = "cg.verb:find";

    @Seed
    public static final VerbSememe find = new VerbSememe(
            FIND,
            Map.of("en", "find items related by a predicate"),
            Map.of("cili", "i33164"),
            List.of("find", "lookup")
    ).slot(ThematicRole.Theme.KEY)
     .slot(ThematicRole.Recipient.KEY)
     .slot(ThematicRole.Source.KEY);

    public static final String SHOW = "cg.verb:show";

    @Seed
    public static final VerbSememe show = new VerbSememe(
            SHOW,
            Map.of("en", "make visible or apparent"),
            Map.of("cili", "i32454"),
            List.of("show", "display", "view")
    ).slot(ThematicRole.Theme.KEY);

    public static final String HELP = "cg.verb:help";

    @Seed
    public static final VerbSememe help = new VerbSememe(
            HELP,
            Map.of("en", "give help or assistance; be of service"),
            Map.of("cili", "i34433"),
            List.of("help", "assist", "commands")
    );

    public static final String EDIT = "cg.verb:edit";

    @Seed
    public static final VerbSememe edit = new VerbSememe(
            EDIT,
            Map.of("en", "prepare for publication or presentation by correcting, revising, or adapting"),
            Map.of("cili", "i22726"),
            List.of("edit", "modify", "change")
    ).slot(ThematicRole.Patient.KEY);

    public static final String COUNT = "cg.verb:count";

    @Seed
    public static final VerbSememe count = new VerbSememe(
            COUNT,
            Map.of("en", "determine the number or amount of"),
            Map.of("cili", "i26340"),
            List.of("count", "size")
    );

    public static final String DESCRIBE = "cg.verb:describe";

    @Seed
    public static final VerbSememe describe = new VerbSememe(
            DESCRIBE,
            Map.of("en", "give an account or representation of in words"),
            Map.of("cili", "i26422"),
            List.of("describe", "status", "info")
    );

    public static final String INSPECT = "cg.verb:inspect";

    @Seed
    public static final VerbSememe inspect = new VerbSememe(
            INSPECT,
            Map.of("en", "look over carefully"),
            Map.of("cili", "i32580"),
            List.of("inspect", "examine")
    );

    public static final String CD = "cg.verb:cd";

    @Seed
    public static final VerbSememe cd = new VerbSememe(
            CD,
            Map.of("en", "change directory; navigate to"),
            Map.of(),
            List.of("cd", "go", "enter")
    ).slot(ThematicRole.Target.KEY);

    public static final String EXIT = "cg.session:exit";

    @Seed
    public static final VerbSememe exit = new VerbSememe(
            EXIT,
            Map.of("en", "exit the session"),
            Map.of(),
            List.of("exit", "quit", "q")
    );

    public static final String BACK = "cg.session:back";

    @Seed
    public static final VerbSememe back = new VerbSememe(
            BACK,
            Map.of("en", "go back to previous item"),
            Map.of(),
            List.of(".."),
            List.of("back", "pop")
    );

    // ==================================================================================
    // SEED INSTANCES (general interaction verbs)
    // ==================================================================================

    public static final String SERVE = "cg.verb:serve";

    @Seed
    public static final VerbSememe serve = new VerbSememe(
            SERVE,
            Map.of("en", "work for or be a servant to"),
            Map.of("cili", "i96785"),
            List.of("serve", "use")
    ).slot(ThematicRole.Theme.KEY);

    public static final String INVITE = "cg.verb:invite";

    @Seed
    public static final VerbSememe invite = new VerbSememe(
            INVITE,
            Map.of("en", "request someone's participation"),
            Map.of("cili", "i32987"),
            List.of("invite")
    );

    public static final String AUTHENTICATE = "cg.session:authenticate";

    @Seed
    public static final VerbSememe authenticate = new VerbSememe(
            AUTHENTICATE,
            Map.of("en", "prove identity by demonstrating possession of private key"),
            Map.of(),
            List.of("authenticate", "auth", "login")
    ).slot(ThematicRole.Theme.KEY);

    public static final String SWITCH = "cg.session:switch";

    @Seed
    public static final VerbSememe switchUser = new VerbSememe(
            SWITCH,
            Map.of("en", "change the active user for the current view"),
            Map.of(),
            List.of("switch", "as")
    ).slot(ThematicRole.Theme.KEY);

    public static final String RENAME = "cg.verb:rename";

    @Seed
    public static final VerbSememe rename = new VerbSememe(
            RENAME,
            Map.of("en", "assign a new name to"),
            Map.of("cili", "i25424"),
            List.of("rename", "name")
    ).slot(ThematicRole.Theme.KEY);

    // ==================================================================================
    // SEED INSTANCES (query pattern primitives)
    // ==================================================================================

    /**
     * Pattern element: wildcard / any.
     *
     * <p>Matches anything in a query pattern. Used in expressions like:
     * <pre>{@code
     * * → IMPLEMENTED_BY → ?   // "anything that implements what?"
     * }</pre>
     *
     * <p>Based on CILI i61150: "a playing card whose value can be determined
     * by the person who holds it" (wild card)
     */
    @Seed
    public static final PronounSememe ANY = new PronounSememe(
            "cg.query:any",
            Map.of("en", "matches anything; wildcard; any value"),
            Map.of("cili", "i61150"),
            List.of("*"),
            List.of("wildcard", "anything")
    );

    /**
     * Pattern element: variable / what.
     *
     * <p>The unknown we're solving for in a query pattern. Used in expressions like:
     * <pre>{@code
     * * → IMPLEMENTED_BY → ?   // "what is implemented by anything?"
     * ? → IMPLEMENTED_BY → *   // "what implements anything?"
     * }</pre>
     *
     * <p>Based on CILI i74896: "a symbol (like x or y) that is used in mathematical
     * or logical expressions to represent a variable quantity"
     */
    @Seed
    public static final PronounSememe WHAT = new PronounSememe(
            "cg.query:what",
            Map.of("en", "the result being queried for; variable; unknown"),
            Map.of("cili", "i74896"),
            List.of("?"),
            List.of("variable", "result")
    );

    // ==================================================================================
    // SEED INSTANCES (pronouns — discourse references)
    // ==================================================================================

    /** "it" / "that" — refers to the most recently mentioned item. */
    @Seed
    public static final PronounSememe IT = new PronounSememe(
            "cg.pronoun:it",
            Map.of("en", "the most recently mentioned or created item"),
            Map.of(),
            List.of("it", "that")
    );

    /** "this" — refers to the currently focused item. */
    @Seed
    public static final PronounSememe THIS = new PronounSememe(
            "cg.pronoun:this",
            Map.of("en", "the currently focused item"),
            Map.of(),
            List.of("this")
    );

    /** "last" — refers to the previously mentioned item (before most recent). */
    @Seed
    public static final PronounSememe LAST = new PronounSememe(
            "cg.pronoun:last",
            Map.of("en", "the previously mentioned item"),
            Map.of(),
            List.of("last", "previous")
    );

    // ==================================================================================
    // SEED INSTANCES (prepositions — thematic role carriers)
    // ==================================================================================

    public static final String ON = "cg.prep:on";

    @Seed
    public static final PrepositionSememe on = new PrepositionSememe(
            ON,
            Map.of("en", "indicating target or destination"),
            Map.of(),
            List.of("on", "to", "into"),
            ItemID.fromString(ThematicRole.Target.KEY)
    );

    public static final String WITH = "cg.prep:with";

    @Seed
    public static final PrepositionSememe with = new PrepositionSememe(
            WITH,
            Map.of("en", "indicating tool or means"),
            Map.of(),
            List.of("with", "using"),
            ItemID.fromString(ThematicRole.Instrument.KEY)
    );

    public static final String FROM = "cg.prep:from";

    @Seed
    public static final PrepositionSememe from = new PrepositionSememe(
            FROM,
            Map.of("en", "indicating origin or source"),
            Map.of(),
            List.of("from"),
            ItemID.fromString(ThematicRole.Source.KEY)
    );

    public static final String FOR = "cg.prep:for";

    @Seed
    public static final PrepositionSememe forPrep = new PrepositionSememe(
            FOR,
            Map.of("en", "indicating beneficiary or recipient"),
            Map.of(),
            List.of("for"),
            ItemID.fromString(ThematicRole.Recipient.KEY)
    );

    public static final String BETWEEN = "cg.prep:between";

    @Seed
    public static final PrepositionSememe between = new PrepositionSememe(
            BETWEEN,
            Map.of("en", "indicating companions or participants"),
            Map.of(),
            List.of("between"),
            ItemID.fromString(ThematicRole.Comitative.KEY)
    );

    public static final String NAMED = "cg.prep:named";

    @Seed
    public static final PrepositionSememe named = new PrepositionSememe(
            NAMED,
            Map.of("en", "indicating designation or label"),
            Map.of(),
            List.of("named", "called"),
            ItemID.fromString(ThematicRole.Name.KEY)
    );

    // ==================================================================================
    // SEED INSTANCES (conjunctions)
    // ==================================================================================

    public static final String AND = "cg.conj:and";

    @Seed
    public static final ConjunctionSememe and = new ConjunctionSememe(
            AND,
            Map.of("en", "coordinating conjunction; connects elements"),
            Map.of(),
            List.of("and")
    );

    public static final String OR = "cg.conj:or";

    @Seed
    public static final ConjunctionSememe or = new ConjunctionSememe(
            OR,
            Map.of("en", "coordinating disjunction; alternative elements"),
            Map.of(),
            List.of("or")
    );

    // ==================================================================================
    // LANGUAGE CONSTANTS (for fluent seed declarations)
    // ==================================================================================

    /** English language code for gloss/word declarations. */
    protected static final String ENG = "en";

    /** Convenience alias for lemma form declarations. */
    protected static final GrammaticalFeature LEMMA = GrammaticalFeature.LEMMA;

    // ==================================================================================
    // INSTANCE FIELDS (value object role)
    // ==================================================================================

    /** The canonical key (e.g., "cg.core:author") */
    @Getter
    @Frame(handle = "key")
    private String canonicalKey;

    /** Part of speech */
    @Getter
    @Frame
    private PartOfSpeech pos;

    /**
     * Glosses by language for bootstrap (e.g., {"en": "the creator..."}).
     *
     * <p>Transient — NOT persisted as a flat map. Glosses are migrated to
     * {@link SememeGloss} components (one per language) during bootstrap.
     * After bootstrap, glosses live as versioned, per-language components
     * on the sememe Item.
     */
    @Getter
    private transient Map<String, String> glosses;

    /** External source references (e.g., {"cili": "i25412"}) */
    @Getter
    @Frame
    private Map<String, String> sources;

    /** Predicate facets (for complex predicates) */
    @Frame
    private List<PredicateFacet> facets;

    /** Language-neutral symbols for universal lookup (e.g., "*", "?", "+", "m", "kg"). */
    @Getter
    @Frame
    private List<String> symbols;

    /**
     * English word aliases for bootstrap indexing (e.g., "create", "new", "make").
     *
     * <p>Transient — NOT persisted as a component. These are English lexemes,
     * not intrinsic to the sememe's meaning. They are indexed as English-scoped
     * postings during bootstrap via {@link TokenExtractor#fromSememe}, then
     * discarded. The canonical source of English words will be the English
     * Language Item's Lexicon (populated during the English import).
     */
    @Getter
    private transient List<String> tokens;

    /**
     * Index weight for relation targets (scaled int: 1000 = 1.0f).
     *
     * <p>When this Sememe is used as a predicate in a relation, and the relation's
     * object is a text literal, the text is indexed in the TokenDictionary at this
     * weight. Zero means "don't index the target."
     *
     * <p>Examples: TITLE = 1000 (1.0), DESCRIPTION = 500 (0.5), NAME = 1000 (1.0).
     */
    @Getter
    @Frame
    private int indexWeight;

    /**
     * Slot expectations for this predicate (e.g., AUTHOR expects THEME, TARGET).
     *
     * <p>Transient — populated by fluent {@link #slot(Sememe)} or
     * {@link #slot(String)} during seed declaration. Consumed by
     * {@link VerbSememe#slotRoles()} for frame assembly.
     */
    @Getter
    private transient List<ItemID> slots;

    /**
     * Lexeme declarations for bootstrap (e.g., LEMMA "author" in English).
     *
     * <p>Transient — during bootstrap, these flow into the appropriate
     * Language's Lexicon as proper Lexemes, not onto the sememe itself.
     * Populated by fluent {@link #word(Sememe, String, String)} during
     * seed declaration; consumed by SeedVocabulary during bootstrap.
     */
    @Getter
    private transient List<LexemeDeclaration> lexemeDeclarations;

    // ==================================================================================
    // CONSTRUCTORS (protected for subclass access)
    // ==================================================================================

    /**
     * Create a seed sememe (no librarian, deterministic IID).
     *
     * <p>This constructor is for defining sememes as static constants.
     * The IID is derived from the canonical key.
     *
     * @param canonicalKey The canonical key (e.g., "cg.core:author")
     * @param pos          Part of speech
     * @param glosses      Glosses by language
     * @param sources      External source references
     */
    protected Sememe(String canonicalKey, PartOfSpeech pos,
                     Map<String, String> glosses, Map<String, String> sources) {
        this(canonicalKey, pos, glosses, sources, List.of(), List.of());
    }

    /**
     * Create a seed sememe with token aliases (English words).
     *
     * @param canonicalKey The canonical key (e.g., "cg.verb:create")
     * @param pos          Part of speech
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @param tokens       English word aliases (e.g., "create", "new", "make")
     */
    protected Sememe(String canonicalKey, PartOfSpeech pos,
                     Map<String, String> glosses, Map<String, String> sources,
                     List<String> tokens) {
        this(canonicalKey, pos, glosses, sources, List.of(), tokens);
    }

    /**
     * Create a seed sememe with both symbols and tokens.
     *
     * @param canonicalKey The canonical key
     * @param pos          Part of speech
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @param symbols      Language-neutral symbols (universal scope)
     * @param tokens       English word aliases (language-scoped)
     */
    protected Sememe(String canonicalKey, PartOfSpeech pos,
                     Map<String, String> glosses, Map<String, String> sources,
                     List<String> symbols, List<String> tokens) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.pos = pos;
        this.glosses = Map.copyOf(glosses);
        this.sources = Map.copyOf(sources);
        this.symbols = List.copyOf(symbols);
        this.tokens = List.copyOf(tokens);
    }

    /**
     * Fluent seed constructor — creates a seed with mutable collections
     * for use with chained {@link #gloss}, {@link #token}, {@link #cili}, etc.
     *
     * @param canonicalKey The canonical key (e.g., "cg.core:author")
     * @param pos          Part of speech
     */
    protected Sememe(String canonicalKey, PartOfSpeech pos) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.pos = pos;
        this.glosses = new HashMap<>();
        this.sources = new HashMap<>();
        this.symbols = new ArrayList<>();
        this.tokens = new ArrayList<>();
    }

    /**
     * Create a sememe with a librarian (for runtime creation and persistence).
     *
     * @param librarian    The librarian for storage
     * @param canonicalKey The canonical key
     * @param pos          Part of speech
     * @param glosses      Glosses by language
     * @param sources      External source references
     */
    protected Sememe(Librarian librarian, String canonicalKey, PartOfSpeech pos,
                     Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.pos = pos;
        this.glosses = Map.copyOf(glosses);
        this.sources = Map.copyOf(sources);
        this.symbols = List.of();
        this.tokens = List.of();
    }

    /**
     * Type seed constructor - creates a minimal Sememe for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg:type/sememe" type item.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected Sememe(ItemID typeId) {
        super(typeId);
    }

    /**
     * Hydration constructor - reconstructs a Sememe from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected Sememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    /**
     * Create and commit a sememe, dispatching to the correct subclass based on POS.
     *
     * @param librarian    The librarian for storage
     * @param signer       The signer to sign with
     * @param canonicalKey The canonical key
     * @param pos          Part of speech
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @return The created and committed sememe
     */
    public static Sememe create(Librarian librarian, Signer signer,
                                String canonicalKey, PartOfSpeech pos,
                                Map<String, String> glosses, Map<String, String> sources) {
        Sememe sememe = switch (pos) {
            case VERB -> new VerbSememe(librarian, canonicalKey, glosses, sources);
            case NOUN -> new NounSememe(librarian, canonicalKey, glosses, sources);
            case PREPOSITION -> new PrepositionSememe(librarian, canonicalKey, glosses, sources);
            case PRONOUN -> new PronounSememe(librarian, canonicalKey, glosses, sources);
            case ADJECTIVE -> new AdjectiveSememe(librarian, canonicalKey, glosses, sources);
            case ADVERB -> new AdverbSememe(librarian, canonicalKey, glosses, sources);
            case CONJUNCTION -> new ConjunctionSememe(librarian, canonicalKey, glosses, sources);
            case INTERJECTION -> new InterjectionSememe(librarian, canonicalKey, glosses, sources);
        };
        sememe.commit(signer);
        return sememe;
    }

    // ==================================================================================
    // FLUENT CONFIGURATION (for seed declarations)
    // ==================================================================================

    /** Add a gloss (definition) for a language. */
    public Sememe gloss(String lang, String text) {
        this.glosses.put(lang, text);
        return this;
    }

    /**
     * Declare a word form for this sememe in a language.
     *
     * <p>During bootstrap, this becomes a proper {@link Lexeme} in the
     * target Language's Lexicon. The form parameter specifies what kind
     * of word form this is (e.g., {@link GrammaticalFeature#LEMMA LEMMA},
     * {@link GrammaticalFeature#PAST PAST}).
     *
     * @param form    the grammatical form (LEMMA, PAST, PLURAL, etc.)
     * @param lang    language code (e.g., ENG)
     * @param surface the written word
     */
    public Sememe word(Sememe form, String lang, String surface) {
        if (this.lexemeDeclarations == null) this.lexemeDeclarations = new ArrayList<>();
        this.lexemeDeclarations.add(new LexemeDeclaration(form, lang, surface));
        return this;
    }

    /** Set the CILI (Collaborative Interlingual Index) identifier. */
    public Sememe cili(String id) {
        this.sources.put("cili", id);
        return this;
    }

    /** Add a language-neutral symbol (e.g., "+", "*", "m"). */
    public Sememe symbol(String s) {
        this.symbols.add(s);
        return this;
    }

    /** Declare that this predicate expects a slot filled by the given role. */
    public Sememe slot(Sememe role) {
        if (this.slots == null) this.slots = new ArrayList<>();
        this.slots.add(role.iid());
        return this;
    }

    /** Declare a slot via canonical key string (avoids circular static init). */
    public Sememe slot(String roleKey) {
        if (this.slots == null) this.slots = new ArrayList<>();
        this.slots.add(ItemID.fromString(roleKey));
        return this;
    }

    /**
     * Set the index weight for this predicate's string targets.
     *
     * <p>Scaled int: 1000 = 1.0f. When &gt; 0, relations using this Sememe
     * as predicate will have their text literal targets indexed.
     */
    public Sememe indexWeight(int weight) {
        this.indexWeight = weight;
        return this;
    }

    // ==================================================================================
    // CONVENIENCE METHODS
    // ==================================================================================

    /**
     * Get all verb Sememes (for vocabulary indexing).
     *
     * <p>Returns the verb primitives used in the vocabulary system.
     *
     * @return List of verb Sememes
     */
    public static List<VerbSememe> verbSememes() {
        return List.of(create, get, put, remove, list, importSeed, query, find,
                show, help, edit, count, describe, inspect,
                rename, invite, serve);
    }

    /**
     * Get all seed Sememes that have tokens (for indexing).
     *
     * <p>Returns all Sememes that have explicit token aliases defined.
     * This includes verb Sememes and query pattern Sememes (ANY, WHAT).
     *
     * @return List of Sememes with tokens
     */
    public static List<Sememe> sememesWithTokens() {
        return List.of(
                // Verbs
                create, get, put, remove, list, importSeed, query, find,
                show, help, edit, count, describe, inspect,
                rename, invite, serve,
                // Prepositions
                on, with, from, forPrep, between, named,
                // Conjunctions
                and, or,
                // Query patterns
                ANY, WHAT
        );
    }

    /**
     * Get gloss for a specific language.
     *
     * <p>Checks SememeGloss components first (hydrated sememes),
     * falls back to transient glosses map (seed sememes at bootstrap).
     */
    public String gloss(String lang) {
        // Map 2-letter to 3-letter for component lookup
        String iso3 = lang.equals("en") ? "eng" : lang;

        // Try SememeGloss component
        if (content() != null) {
            var live = content().getLive(
                    dev.everydaythings.graph.item.id.FrameKey.literal(
                            SememeGloss.handleKeyFor(iso3)));
            if (live.isPresent() && live.get() instanceof SememeGloss sg) {
                return sg.text();
            }
        }

        // Fall back to transient glosses (seed sememes)
        return glosses != null ? glosses.get(lang) : null;
    }

    /**
     * Get English gloss (convenience).
     */
    public String glossEn() {
        return gloss("en");
    }

    /**
     * Get external source code (e.g., CILI ID).
     */
    public String source(String scheme) {
        return sources.get(scheme);
    }

    // ==================================================================================
    // Display / Indexing
    // ==================================================================================

    @Override
    public String displayToken() {
        // Extract the name part from canonical key, e.g., "cg.core:author" -> "author"
        if (canonicalKey != null) {
            int colonIdx = canonicalKey.lastIndexOf(':');
            if (colonIdx >= 0 && colonIdx < canonicalKey.length() - 1) {
                return canonicalKey.substring(colonIdx + 1);
            }
        }
        return canonicalKey != null ? canonicalKey : getClass().getSimpleName();
    }

    @Override
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> allTokens = new ArrayList<>();

        // Primary: the canonical key (e.g., "cg.core:author")
        if (canonicalKey != null && !canonicalKey.isBlank()) {
            allTokens.add(new TokenEntry(canonicalKey, 1.0f));
            // Also index the short name part
            int colonIdx = canonicalKey.lastIndexOf(':');
            if (colonIdx >= 0 && colonIdx < canonicalKey.length() - 1) {
                allTokens.add(new TokenEntry(canonicalKey.substring(colonIdx + 1), 1.0f));
            }
        }

        // Symbols (language-neutral, universal)
        if (symbols != null) {
            for (String symbol : symbols) {
                if (symbol != null && !symbol.isBlank()) {
                    allTokens.add(new TokenEntry(symbol, 1.0f));
                }
            }
        }

        // Tokens (English words)
        if (tokens != null) {
            for (String token : tokens) {
                if (token != null && !token.isBlank()) {
                    allTokens.add(new TokenEntry(token, 1.0f));
                }
            }
        }

        // Glosses (lower weight since they're descriptions)
        if (glosses != null) {
            for (String gloss : glosses.values()) {
                if (gloss != null && !gloss.isBlank() && gloss.length() <= 50) {
                    allTokens.add(new TokenEntry(gloss, 0.5f));
                }
            }
        }

        return allTokens.stream();
    }

    // ==================================================================================
    // PREDICATE FACET (for complex predicates)
    // ==================================================================================

    /**
     * A word form declaration for bootstrap lexeme creation.
     *
     * <p>Captures enough data to create a {@link Lexeme} in a Language's
     * Lexicon during bootstrap. The sememe IID and POS come from the
     * declaring Sememe.
     *
     * @param form    the grammatical form (LEMMA, PAST, PLURAL, etc.)
     * @param lang    language code (e.g., "en")
     * @param surface the written word
     */
    public record LexemeDeclaration(Sememe form, String lang, String surface) {}

    /**
     * Describes facets of a predicate (domain, range, cardinality, etc.)
     */
    public record PredicateFacet(
            String key,                 // "addr/at-domain"
            String canonicalDir,        // "SUBJECT_TO_OBJECT"
            boolean valueIsLiteral,     // true for email, tilde; false if you promote to Address item
            List<String> domainKeys,    // e.g., ["core/Item","sememe/noun/person"]
            List<String> rangeKeys,     // ["core/String"] or ["addr/Address"] when object-mode
            boolean multiple,           // true if allows multiple values
            String regex,               // scheme-specific validation
            String normalizer,          // "lowercaseEmail","tildeNorm","noop"
            List<String> requiredQuals  // e.g., ["sememe/domain"] for at-domain
    ) {}
}
