package dev.everydaythings.graph.language;

import dev.everydaythings.graph.dispatch.Created;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.eval.FrameAssemblyContext;
import dev.everydaythings.graph.frame.eval.ParseContext;
import dev.everydaythings.graph.frame.eval.ParseContribution;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Sememe is a unit of meaning, like "meters" are a unit of measure.
 *
 * <p>Concrete base for all meaning-carrying types. Part of speech is NOT
 * a property of the sememe — it is a lexical feature on {@link Lexeme},
 * where it linguistically belongs. Any sememe can serve as a predicate,
 * type definition, or vocabulary entry.
 *
 * <p>Seed constants are organized by domain into vocabulary classes:
 * <ul>
 *   <li>{@link CoreVocabulary} — actions, metadata predicates, infrastructure</li>
 *   <li>{@link LexicalVocabulary} — semantic/lexical relations (hypernym, antonym, etc.)</li>
 *   <li>{@link PrepositionVocabulary} — thematic role carriers</li>
 * </ul>
 *
 * <p>Domain-specific subclasses extend Sememe directly for POS-specific data:
 * {@link dev.everydaythings.graph.value.Operator},
 * {@link dev.everydaythings.graph.value.Function},
 * {@link ThematicRole}, {@link GrammaticalFeature}.
 *
 * <p>Sememes with an IMPLEMENTED_BY frame are createable — the CREATE verb
 * resolves the implementing class and instantiates via {@link CreationScanner}.
 *
 * <p>Sememes are anchored globally and used as predicates in relations.
 * Their IIDs are deterministically derived from their canonical key,
 * enabling compile-time references.
 */
@Log4j2
@Implements(Sememe.KEY)
@ItemSeed(key = Sememe.KEY)
public class Sememe extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.sememe:sememe";

    // ==================================================================================
    // LANGUAGE CONSTANTS (for fluent seed declarations)
    // ==================================================================================

    /** English language code for gloss/word declarations. */
    protected static final String ENG = "en";

    /** Convenience alias for lemma form declarations in inner-class seed builders. */
    protected static final Sememe LEMMA = new Sememe(GrammaticalFeature.Lemma.KEY);

    // ==================================================================================
    // INSTANCE FIELDS (value object role)
    // ==================================================================================

    /** The canonical key (e.g., "cg.core:author") */
    @Getter
    @ItemFrame(predicate = CoreVocabulary.HashKey.KEY, fieldAs = @Bind(role = ThematicRole.Topic.KEY))
    private String canonicalKey;

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

    /**
     * External source references for bootstrap (e.g., {"cili": "i25412"}).
     *
     * <p>Transient builder state — populated by fluent methods like {@link #cili(String)}
     * during seed declaration. During bootstrap, each entry is written as a properly-keyed
     * frame (e.g., CILI ID → {@link CoreVocabulary.CiliId} frame). The Sememe class
     * doesn't have compile-time fields for external IDs — they're just data on the item.
     */
    @Getter
    private transient Map<String, String> sources;

    /** Predicate facets (for complex predicates) */
    @ItemFrame(predicate = CoreVocabulary.Facet.KEY)
    private List<PredicateFacet> facets;

    /** Language-neutral symbols for universal lookup (e.g., "*", "?", "+", "m", "kg"). */
    @Getter
    @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
    private List<String> symbols;

    /**
     * English word aliases for bootstrap indexing (e.g., "create", "new", "make").
     *
     * <p>Transient — NOT persisted as a component. These are English lexemes,
     * not intrinsic to the sememe's meaning. They are indexed as English-scoped
     * postings during bootstrap via frame-backed token indexing, then
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
    @ItemFrame(predicate = CoreVocabulary.IndexWeight.KEY)
    private int indexWeight;

    /**
     * The thematic role this sememe assigns to its object (prepositions only).
     *
     * <p>For example, "on" has assignedRole = TARGET — in "create chess on myItem",
     * the preposition "on" tells the evaluator that "myItem" fills the TARGET role.
     * Null for non-preposition sememes.
     */
    @Getter
    @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
    private ItemID assignedRole;

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
     * @param canonicalKey The canonical key (e.g., "cg.core:author")
     * @param glosses      Glosses by language
     * @param sources      External source references
     */
    protected Sememe(String canonicalKey,
                     Map<String, String> glosses, Map<String, String> sources) {
        this(canonicalKey, glosses, sources, List.of(), List.of());
    }

    /**
     * Create a seed sememe with token aliases (English words).
     *
     * @param canonicalKey The canonical key (e.g., "cg.verb:create")
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @param tokens       English word aliases (e.g., "create", "new", "make")
     */
    protected Sememe(String canonicalKey,
                     Map<String, String> glosses, Map<String, String> sources,
                     List<String> tokens) {
        this(canonicalKey, glosses, sources, List.of(), tokens);
    }

    /**
     * Create a seed sememe with both symbols and tokens.
     *
     * @param canonicalKey The canonical key
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @param symbols      Language-neutral symbols (universal scope)
     * @param tokens       English word aliases (language-scoped)
     */
    protected Sememe(String canonicalKey,
                     Map<String, String> glosses, Map<String, String> sources,
                     List<String> symbols, List<String> tokens) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
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
     */
    public Sememe(String canonicalKey) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
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
     * @param glosses      Glosses by language
     * @param sources      External source references
     */
    public Sememe(Librarian librarian, String canonicalKey,
                  Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.glosses = Map.copyOf(glosses);
        this.sources = Map.copyOf(sources);
        this.symbols = List.of();
        this.tokens = List.of();
    }

    /**
     * Type seed constructor - creates a minimal Sememe for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg.sememe:sememe" type item.
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
     * Create and commit a sememe.
     *
     * @param librarian    The librarian for storage
     * @param signer       The signer to sign with
     * @param canonicalKey The canonical key
     * @param glosses      Glosses by language
     * @param sources      External source references
     * @return The created and committed sememe
     */
    public static Sememe create(Librarian librarian, Signer signer,
                                String canonicalKey,
                                Map<String, String> glosses, Map<String, String> sources) {
        Sememe sememe = new Sememe(librarian, canonicalKey, glosses, sources);
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
    /**
     * Declare a word form with explicit part of speech.
     *
     * @param pos     the part of speech (e.g., PartOfSpeech.VERB)
     * @param form    the grammatical form (LEMMA, PAST, PLURAL, etc.)
     * @param lang    language code (e.g., ENG)
     * @param surface the written word
     */ // TODO: don't we ALSO need the GramaticalFeatures here?  Perhaps they could go at the end with a `...` syntax?
    public Sememe word(ItemID pos, Sememe form, String lang, String surface) {
        if (this.lexemeDeclarations == null) this.lexemeDeclarations = new ArrayList<>();
        this.lexemeDeclarations.add(new LexemeDeclaration(pos, form, lang, surface));
        // Also populate transient tokens list for bootstrap indexing compatibility
        this.tokens.add(surface);
        return this;
    }

    //TODO: I feel like this method may need some improvement... don't we need to be ABLE to pass in other (multiple) grammatical features.  We should be able to set the PAST or whatever using this method... even though we mostly probably won't, we should be able to.  Perhaps we move it to the end of the method and use varargs?
    public Sememe word(Sememe form, String lang, String surface) {
        return word(PartOfSpeech.NOUN, form, lang, surface);
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

    /** Set the thematic role this preposition assigns, by canonical key. */
    public Sememe role(String roleKey) {
        this.assignedRole = ItemID.fromString(roleKey);
        return this;
    }

    // ==================================================================================
    // CONVENIENCE METHODS
    // ==================================================================================


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
        if (frames() != null) {
            var live = frames().getLive(
                    dev.everydaythings.graph.item.id.FrameKey.of(
                            dev.everydaythings.graph.item.id.ItemID.fromString(SememeGloss.KEY), iso3));
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
        return sources != null ? sources.get(scheme) : null;
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

    // ==================================================================================
    // PARSING CONTRIBUTION — declares how this sememe participates in parsing
    // ==================================================================================

    /**
     * Declare this sememe's parsing metadata from its existing frame data.
     *
     * <p>Surfaces {@link #assignedRole()} (for prepositions) and
     * {@link #slotRoles()} (for predicates with expected arguments)
     * through the unified {@link ParseContribution} interface.
     *
     * <p>Subclasses ({@link dev.everydaythings.graph.value.Operator},
     * {@link dev.everydaythings.graph.value.Function}) override this with
     * richer metadata (precedence, fixity, grouping).
     *
     * @param context the parsing context (may be null during early bootstrap)
     * @return parsing contribution, never null
     */
    public ParseContribution contribute(ParseContext context) {
        // Prepositions assign a role to their object
        if (assignedRole != null) {
            return ParseContribution.assignRole(assignedRole);
        }

        // Predicates with expected argument slots
        List<ItemID> roles = slotRoles();
        if (!roles.isEmpty()) {
            return ParseContribution.builder()
                    .expectedRoles(roles)
                    .build();
        }
        return ParseContribution.NONE;
    }

    // ==================================================================================
    // EXPECTS — role expectations (predicates) and frame expectations (types)
    // ==================================================================================

    /**
     * Returns the thematic role IIDs this predicate expects as bindings.
     *
     * <p>Reads EXPECTS frames whose TOPIC binding is qualified with ROLE
     * ({@code cg.sememe:role}). Falls back to ALL expects if no qualified
     * ones are found (backward compat).
     */
    public List<ItemID> slotRoles() {
        return expectsFiltered(ItemID.fromString(ThematicRole.KEY));
    }

    /**
     * Returns the predicate IIDs this type expects as frames on its instances.
     *
     * <p>Reads EXPECTS frames whose TOPIC binding is qualified with FRAME
     * ({@code cg.sememe:frame}).
     */
    public List<ItemID> expectedFrames() {
        return expectsFiltered(FrameBody.TYPE_ID);
    }

    /**
     * Read EXPECTS frames, optionally filtered by a qualifier.
     *
     * <p>If qualifier is non-null, only returns EXPECTS whose TOPIC binding
     * has that qualifier as its first token. If no qualified EXPECTS are found,
     * falls back to returning ALL EXPECTS (backward compat).
     */
    private List<ItemID> expectsFiltered(ItemID qualifier) {
        if (frames() == null) return List.of();
        List<ItemID> qualified = new ArrayList<>();
        List<ItemID> all = new ArrayList<>();
        for (Frame frame : frames()) {
            if (frame.body() == null) continue;
            if (!CoreVocabulary.Expects.IID.equals(frame.body().predicate())) continue;
            Binding topicBinding = frame.body().getBindingByRole(ThematicRole.Topic.IID);
            if (topicBinding == null || topicBinding.targetId() == null) continue;

            all.add(topicBinding.targetId());

            // Check if first qualifier matches
            if (qualifier != null && !topicBinding.qualifiers().isEmpty()) {
                if (topicBinding.qualifiers().getFirst() instanceof FrameKey.Sememe sem
                        && qualifier.equals(sem.id())) {
                    qualified.add(topicBinding.targetId());
                }
            }
        }
        return qualified;
    }

    // ==================================================================================
    // SEED INSTANCES — Pronouns (proper subclasses with parsing behavior)
    // ==================================================================================

    @Implements(Any.KEY)
    @ItemSeed(key = Any.KEY)
    public static class Any extends Sememe {
        public static final String KEY = "cg.query:any";
        public static final ItemID IID = ItemID.fromString(KEY);

        Any() {
            super(KEY);
            gloss(ENG, "matches anything; wildcard; any value");
            cili("i61150");
            symbol("*");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "wildcard");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "anything");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.PRONOUN);
        }
    }

    @Implements(What.KEY)
    @ItemSeed(key = What.KEY)
    public static class What extends Sememe {
        public static final String KEY = "cg.query:what";
        public static final ItemID IID = ItemID.fromString(KEY);

        What() {
            super(KEY);
            gloss(ENG, "the result being queried for; variable; unknown");
            cili("i74896");
            symbol("?");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "variable");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "result");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.PRONOUN);
        }
    }

    @Implements(Every.KEY)
    @ItemSeed(key = Every.KEY)
    public static class Every extends Sememe {
        public static final String KEY = "cg.query:every";
        public static final ItemID IID = ItemID.fromString(KEY);

        Every() {
            super(KEY);
            gloss(ENG, "universal quantifier; all items of a kind");
            cili("i74328");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "all");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "every");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "everything");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.PRONOUN);
        }
    }

    @Implements(It.KEY)
    @ItemSeed(key = It.KEY)
    public static class It extends Sememe {
        public static final String KEY = "cg.pronoun:it";
        public static final ItemID IID = ItemID.fromString(KEY);

        It() {
            super(KEY);
            gloss(ENG, "the most recently mentioned or created item");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "it");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "that");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.PRONOUN);
        }
    }

    @Implements(This.KEY)
    @ItemSeed(key = This.KEY)
    public static class This extends Sememe {
        public static final String KEY = "cg.pronoun:this";
        public static final ItemID IID = ItemID.fromString(KEY);

        This() {
            super(KEY);
            gloss(ENG, "the currently focused item");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "this");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.PRONOUN);
        }
    }

    @Implements(Last.KEY)
    @ItemSeed(key = Last.KEY)
    public static class Last extends Sememe {
        public static final String KEY = "cg.pronoun:last";
        public static final ItemID IID = ItemID.fromString(KEY);

        Last() {
            super(KEY);
            gloss(ENG, "the previously mentioned item");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "last");
            word(PartOfSpeech.PRONOUN, LEMMA, ENG, "previous");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.PRONOUN);
        }
    }

    // ==================================================================================
    // SEED INSTANCES — Conjunctions (proper subclasses with parsing behavior)
    // ==================================================================================

    @Implements(And.KEY)
    @ItemSeed(key = And.KEY)
    public static class And extends Sememe {
        public static final String KEY = "cg.conj:and";
        public static final ItemID IID = ItemID.fromString(KEY);

        And() {
            super(KEY);
            gloss(ENG, "coordinating conjunction; connects elements");
            word(PartOfSpeech.CONJUNCTION, LEMMA, ENG, "and");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.CONJUNCTION);
        }
    }

    @Implements(Or.KEY)
    @ItemSeed(key = Or.KEY)
    public static class Or extends Sememe {
        public static final String KEY = "cg.conj:or";
        public static final ItemID IID = ItemID.fromString(KEY);

        Or() {
            super(KEY);
            gloss(ENG, "coordinating disjunction; alternative elements");
            word(PartOfSpeech.CONJUNCTION, LEMMA, ENG, "or");
        }

        @Override
        public ParseContribution contribute(ParseContext context) {
            return ParseContribution.structural(ParseContribution.StructuralRole.CONJUNCTION);
        }
    }



    /**
     * Whether this sememe has an implementing class (is createable).
     */
    public boolean hasImplementation() {
        return resolveImplementingClass().isPresent();
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
    public record LexemeDeclaration(ItemID pos, Sememe form, String lang, String surface) {}

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
