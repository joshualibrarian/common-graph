package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
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
    // SEED INSTANCES (core sememes used throughout the system)
    // ==================================================================================

    /** Predicate: the creator/author of something */
    @Seed
    public static final NounSememe AUTHOR = new NounSememe(
            "cg.core:author",
            Map.of("en", "the creator or originator of a work"),
            Map.of("cili", "i90183")
    );

    /** Predicate: creation timestamp */
    @Seed
    public static final NounSememe CREATED_AT = new NounSememe(
            "cg.core:created-at",
            Map.of("en", "the time at which something was created"),
            Map.of("cili", "i36666")
    );

    /** Predicate: modification timestamp */
    @Seed
    public static final NounSememe MODIFIED_AT = new NounSememe(
            "cg.core:modified-at",
            Map.of("en", "the time at which something was last modified"),
            Map.of("cili", "i22389")
    );

    /** Predicate: title or name */
    @Seed
    @SuppressWarnings("unchecked")
    public static final NounSememe TITLE = ((NounSememe) new NounSememe(
            "cg.core:title",
            Map.of("en", "the name or title of something"),
            Map.of("cili", "i69816")
    ).indexWeight(1000));

    /** Predicate: description */
    @Seed
    @SuppressWarnings("unchecked")
    public static final NounSememe DESCRIPTION = ((NounSememe) new NounSememe(
            "cg.core:description",
            Map.of("en", "a textual description of something"),
            Map.of("cili", "i71841")
    ).indexWeight(500));

    // ==================================================================================
    // SEED INSTANCES (type system predicates)
    // ==================================================================================

    /**
     * Predicate: implemented by (type → implementation)
     *
     * <p>Used to link type Items to their implementations:
     * <pre>{@code
     * (cg:type/dimension) —[implementedBy]→ "java:dev.everydaythings.graph.value.Dimension"
     * }</pre>
     *
     * <p>Based on CILI i33787: "apply in a manner consistent with its purpose or design"
     */
    @Seed
    public static final VerbSememe IMPLEMENTED_BY = new VerbSememe(
            "cg.type:implemented-by",
            Map.of("en", "is implemented by; has its design applied by"),
            Map.of("cili", "i33787")
    );

    // ==================================================================================
    // SEED INSTANCES (semantic relations - WordNet pointer types)
    // ==================================================================================

    /**
     * Hypernym relation: X is a kind of Y (cat → mammal).
     * WordNet pointer: @
     */
    @Seed
    public static final VerbSememe HYPERNYM = new VerbSememe(
            "cg.rel:hypernym",
            Map.of("en", "is a kind of; is a type of; is a subclass of"),
            Map.of("cili", "i69569")
    );

    /**
     * Hyponym relation: Y is a kind of X (mammal → cat).
     * WordNet pointer: ~
     * Inverse of hypernym.
     */
    @Seed
    public static final VerbSememe HYPONYM = new VerbSememe(
            "cg.rel:hyponym",
            Map.of("en", "has subtype; has kind; is a superclass of"),
            Map.of("cili", "i69570")
    );

    /**
     * Instance-of relation: X is an instance of type Y.
     * Similar to rdf:type. Used for seed instances pointing to their type.
     * Example: Unit.METER instance-of ItemID.fromString(Unit.KEY)
     */
    @Seed
    public static final VerbSememe INSTANCE_OF = new VerbSememe(
            "cg.rel:instance-of",
            Map.of("en", "is an instance of; has type; is a member of class"),
            Map.of("cili", "i35284")
    );

    /**
     * Holonym relation: X is a part of Y (wheel → car).
     * WordNet pointer: #p (part), #m (member), #s (substance)
     */
    @Seed
    public static final VerbSememe HOLONYM = new VerbSememe(
            "cg.rel:holonym",
            Map.of("en", "is a part of; is contained in"),
            Map.of("cili", "i69567")
    );

    /**
     * Meronym relation: Y has X as a part (car → wheel).
     * WordNet pointer: %p (part), %m (member), %s (substance)
     * Inverse of holonym.
     */
    @Seed
    public static final VerbSememe MERONYM = new VerbSememe(
            "cg.rel:meronym",
            Map.of("en", "has as a part; contains"),
            Map.of("cili", "i69575")
    );

    /**
     * Antonym relation: opposite meaning (hot ↔ cold).
     * WordNet pointer: !
     */
    @Seed
    public static final VerbSememe ANTONYM = new VerbSememe(
            "cg.rel:antonym",
            Map.of("en", "is the opposite of; contrasts with"),
            Map.of("cili", "i69547")
    );

    /**
     * Similar-to relation: similar meaning (beautiful ~ pretty).
     * WordNet pointer: &
     */
    @Seed
    public static final VerbSememe SIMILAR_TO = new VerbSememe(
            "cg.rel:similar-to",
            Map.of("en", "is similar to; resembles in meaning"),
            Map.of("cili", "i34992")
    );

    /**
     * Derivationally related: morphological derivation (create → creation).
     * WordNet pointer: +
     */
    @Seed
    public static final VerbSememe DERIVATION = new VerbSememe(
            "cg.rel:derivation",
            Map.of("en", "is derivationally related to"),
            Map.of("cili", "i37467")
    );

    /**
     * Domain category: subject domain (mathematics ;c equation).
     * WordNet pointer: ;c
     */
    @Seed
    public static final VerbSememe DOMAIN = new VerbSememe(
            "cg.rel:domain",
            Map.of("en", "belongs to domain; is in the category of"),
            Map.of("cili", "i68336")
    );

    /**
     * Entailment: verb X entails verb Y (snore → sleep).
     * WordNet pointer: *
     */
    @Seed
    public static final VerbSememe ENTAILS = new VerbSememe(
            "cg.rel:entails",
            Map.of("en", "entails; necessarily implies"),
            Map.of("cili", "i34848")
    );

    /**
     * Cause: verb X causes verb Y (kill → die).
     * WordNet pointer: >
     */
    @Seed
    public static final VerbSememe CAUSES = new VerbSememe(
            "cg.rel:causes",
            Map.of("en", "causes; brings about"),
            Map.of("cili", "i29966")
    );

    /**
     * Also-see: related concepts worth exploring.
     * WordNet pointer: ^
     */
    @Seed
    public static final VerbSememe SEE_ALSO = new VerbSememe(
            "cg.rel:see-also",
            Map.of("en", "see also; is related to"),
            Map.of("cili", "i25271")
    );

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
    ).withArguments(
            ArgumentSlot.optional(Role.THEME.iid(), "what to create"),
            ArgumentSlot.optional(Role.TARGET.iid(), "where to place the result"),
            ArgumentSlot.optional(Role.NAME.iid(), "name for the new item"),
            ArgumentSlot.optional(Role.COMITATIVE.iid(), "participants or companions"),
            ArgumentSlot.optional(Role.SOURCE.iid(), "source to import from")
    );

    public static final String GET = "cg.verb:get";

    @Seed
    public static final VerbSememe get = new VerbSememe(
            GET,
            Map.of("en", "go or come after and bring or take back"),
            Map.of("cili", "i28895"),
            List.of("get", "retrieve", "fetch", "lookup")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "what to retrieve")
    );

    public static final String PUT = "cg.verb:put";

    @Seed
    public static final VerbSememe put = new VerbSememe(
            PUT,
            Map.of("en", "find a place for and put away for storage"),
            Map.of("cili", "i33146"),
            List.of("put", "store", "add", "insert")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "what to store"),
            ArgumentSlot.optional(Role.TARGET.iid(), "where to store it")
    );

    public static final String REMOVE = "cg.verb:remove";

    @Seed
    public static final VerbSememe remove = new VerbSememe(
            REMOVE,
            Map.of("en", "remove something concrete, as by lifting, pushing, or taking off"),
            Map.of("cili", "i22577"),
            List.of("remove", "delete", "drop")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "what to remove")
    );

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
    ).withArguments(
            ArgumentSlot.required(Role.SOURCE.iid(), "what to import from")
    );

    public static final String QUERY = "cg.verb:query";

    @Seed
    public static final VerbSememe query = new VerbSememe(
            QUERY,
            Map.of("en", "pose a question"),
            Map.of("cili", "i25610"),
            List.of("query", "search")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "what to search for")
    );

    public static final String FIND = "cg.verb:find";

    @Seed
    public static final VerbSememe find = new VerbSememe(
            FIND,
            Map.of("en", "find items related by a predicate"),
            Map.of("cili", "i33164"),
            List.of("find", "lookup")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "predicate/relation to search by"),
            ArgumentSlot.optional(Role.RECIPIENT.iid(), "object constraint (e.g. for chess)"),
            ArgumentSlot.optional(Role.SOURCE.iid(), "subject constraint (e.g. from chess)")
    );

    public static final String SHOW = "cg.verb:show";

    @Seed
    public static final VerbSememe show = new VerbSememe(
            SHOW,
            Map.of("en", "make visible or apparent"),
            Map.of("cili", "i32454"),
            List.of("show", "display", "view")
    ).withArguments(
            ArgumentSlot.optional(Role.THEME.iid(), "what to display")
    );

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
    ).withArguments(
            ArgumentSlot.required(Role.PATIENT.iid(), "what to edit")
    );

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
    ).withArguments(
            ArgumentSlot.optional(Role.TARGET.iid(), "where to navigate")
    );

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
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "who to serve")
    );

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
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "user to authenticate as")
    );

    public static final String SWITCH = "cg.session:switch";

    @Seed
    public static final VerbSememe switchUser = new VerbSememe(
            SWITCH,
            Map.of("en", "change the active user for the current view"),
            Map.of(),
            List.of("switch", "as")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "user to switch to")
    );

    public static final String RENAME = "cg.verb:rename";

    @Seed
    public static final VerbSememe rename = new VerbSememe(
            RENAME,
            Map.of("en", "assign a new name to"),
            Map.of("cili", "i25424"),
            List.of("rename", "name")
    ).withArguments(
            ArgumentSlot.required(Role.THEME.iid(), "new name")
    );

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
            Role.TARGET.iid()
    );

    public static final String WITH = "cg.prep:with";

    @Seed
    public static final PrepositionSememe with = new PrepositionSememe(
            WITH,
            Map.of("en", "indicating tool or means"),
            Map.of(),
            List.of("with", "using"),
            Role.INSTRUMENT.iid()
    );

    public static final String FROM = "cg.prep:from";

    @Seed
    public static final PrepositionSememe from = new PrepositionSememe(
            FROM,
            Map.of("en", "indicating origin or source"),
            Map.of(),
            List.of("from"),
            Role.SOURCE.iid()
    );

    public static final String FOR = "cg.prep:for";

    @Seed
    public static final PrepositionSememe forPrep = new PrepositionSememe(
            FOR,
            Map.of("en", "indicating beneficiary or recipient"),
            Map.of(),
            List.of("for"),
            Role.RECIPIENT.iid()
    );

    public static final String BETWEEN = "cg.prep:between";

    @Seed
    public static final PrepositionSememe between = new PrepositionSememe(
            BETWEEN,
            Map.of("en", "indicating companions or participants"),
            Map.of(),
            List.of("between"),
            Role.COMITATIVE.iid()
    );

    public static final String NAMED = "cg.prep:named";

    @Seed
    public static final PrepositionSememe named = new PrepositionSememe(
            NAMED,
            Map.of("en", "indicating designation or label"),
            Map.of(),
            List.of("named", "called"),
            Role.NAME.iid()
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
    // INSTANCE FIELDS (value object role)
    // ==================================================================================

    /** The canonical key (e.g., "cg.core:author") */
    @Getter
    @ContentField(handleKey = "key")
    private String canonicalKey;

    /** Part of speech */
    @Getter
    @ContentField
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
    @ContentField
    private Map<String, String> sources;

    /** Predicate facets (for complex predicates) */
    @ContentField
    private List<PredicateFacet> facets;

    /** Language-neutral symbols for universal lookup (e.g., "*", "?", "+", "m", "kg"). */
    @Getter
    @ContentField
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
    @ContentField
    private int indexWeight;

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
    // FLUENT CONFIGURATION
    // ==================================================================================

    /**
     * Set the index weight for this predicate's string targets.
     *
     * <p>When > 0, relations using this Sememe as predicate will have their
     * text literal targets indexed in the TokenDictionary at this weight.
     * Scaled int: 1000 = 1.0f.
     *
     * @param weight index weight (1000 = 1.0, 500 = 0.5, 0 = don't index)
     * @return this (for chaining on seed declarations)
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
                    dev.everydaythings.graph.item.id.HandleID.of(
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
