package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
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
    // LANGUAGE CONSTANTS (for fluent seed declarations)
    // ==================================================================================

    /** English language code for gloss/word declarations. */
    protected static final String ENG = "en";

    /** Convenience alias for lemma form declarations. */
    protected static final GrammaticalFeature LEMMA = GrammaticalFeature.Lemma.SEED;

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
        // Also populate transient tokens list for bootstrap indexing compatibility
        this.tokens.add(surface);
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
        return List.of(
                VerbSememe.Create.SEED, VerbSememe.Get.SEED, VerbSememe.Put.SEED,
                VerbSememe.Remove.SEED, VerbSememe.ListVerb.SEED, VerbSememe.Import.SEED,
                VerbSememe.Query.SEED, VerbSememe.Find.SEED,
                VerbSememe.Show.SEED, VerbSememe.Help.SEED, VerbSememe.Edit.SEED,
                VerbSememe.Count.SEED, VerbSememe.Describe.SEED, VerbSememe.Inspect.SEED,
                VerbSememe.Rename.SEED, VerbSememe.Invite.SEED, VerbSememe.Serve.SEED);
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
                VerbSememe.Create.SEED, VerbSememe.Get.SEED, VerbSememe.Put.SEED,
                VerbSememe.Remove.SEED, VerbSememe.ListVerb.SEED, VerbSememe.Import.SEED,
                VerbSememe.Query.SEED, VerbSememe.Find.SEED,
                VerbSememe.Show.SEED, VerbSememe.Help.SEED, VerbSememe.Edit.SEED,
                VerbSememe.Count.SEED, VerbSememe.Describe.SEED, VerbSememe.Inspect.SEED,
                VerbSememe.Rename.SEED, VerbSememe.Invite.SEED, VerbSememe.Serve.SEED,
                // Prepositions
                PrepositionSememe.On.SEED, PrepositionSememe.With.SEED,
                PrepositionSememe.From.SEED, PrepositionSememe.For.SEED,
                PrepositionSememe.Between.SEED, PrepositionSememe.Named.SEED,
                // Conjunctions
                ConjunctionSememe.And.SEED, ConjunctionSememe.Or.SEED,
                // Query patterns
                PronounSememe.Any.SEED, PronounSememe.What.SEED
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
