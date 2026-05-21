package dev.everydaythings.graph;

import dev.everydaythings.graph.language.*;

import static dev.everydaythings.graph.Seed.*;

/**
 * Schema vocabulary — the sememes used to declare expectations on predicates
 * and archetypes, plus the predicates that carry those declarations.
 *
 * <p>Two kinds of entries live here:
 * <ul>
 *   <li><b>Schema predicates</b> ({@link Expects}, {@link Implements}) — predicates
 *       whose endorsed frames describe what a sememe expects (bindings, frames)
 *       and which artifacts implement it.</li>
 *   <li><b>Universal qualifiers</b> ({@link Required}, {@link Arity},
 *       {@link Retention}, {@link Ephemeral}, {@link Limit}) — qualifier sememes
 *       used inside schema/metadata frames to refine declarations.</li>
 * </ul>
 *
 * <p>Each is a pure-data {@code @Seed}. They don't carry behavior; they're
 * categorical labels that the framework references by IID. The seed declarations
 * register their manifest bodies at bootstrap so they exist as real items in the
 * graph (queryable, gloss-able, lexeme-able), not just framework-internal IIDs.
 */
public final class SchemaVocabulary {

    private SchemaVocabulary() {}

    // ==================================================================================
    // Schema predicates — heads of frames that declare expectations / implementations
    // ==================================================================================

    /**
     * The IMPLEMENTS predicate sememe — head of frames declaring "an implementation of
     * this concept exists, embodied in this artifact."
     *
     * <p>The {@link dev.everydaythings.graph.item.SeedProcessor} publishes IMPLEMENTS
     * frames at bootstrap for every {@link Seed.Mints @Mints}-annotated class. The
     * frames look like:
     *
     * <pre>
     * IMPLEMENTS { THEME → concept-IID, AGENT[runtime] → implementation-reference }
     * </pre>
     *
     * <p>CREATE consults these frames to find runnable instance classes when minting.
     * Future work may add trust-weighted ordering when multiple implementations exist
     * for the same concept.
     */
    @Seed.Item(key = Implements.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Implements {
        public static final String KEY = "cg.sememe:implements";
        private Implements() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "predicate declaring that an artifact realizes a concept — its frames "
                        + "carry THEME → concept and AGENT → implementation reference "
                        + "(typically a class name or source code), letting CREATE find "
                        + "runnable forms and items declare what they implement";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "implement";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "implementation";
    }

    // ==================================================================================
    // Universal qualifiers — used in EXPECTS, HANDLES, and other metadata declarations
    // ==================================================================================

    /**
     * Required — qualifier on EXPECTS bindings declaring that the expected
     * binding or frame must be present (not optional).
     *
     * <p>Without {@code Required}, an EXPECTS declaration is a permitted/known
     * shape; with it, instances missing the expectation are structurally
     * invalid. Used as one of the qualifiers on the binding inside an EXPECTS
     * frame body.
     */
    @Seed.Item(key = Required.KEY)
    public static final class Required {
        public static final String KEY = "cg.qualifier:required";
        private Required() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking an EXPECTS declaration as mandatory rather than "
                        + "merely permitted";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "required";
    }

    /**
     * Retention — qualifier on CONFIG bindings declaring how the system should
     * treat the frame for persistence.
     *
     * <p>Used as {@code CONFIG[RETENTION] → @Ephemeral} on predicate manifests
     * to mark a predicate's frames as non-persisted (queries, transient UI
     * events, scene updates, keystrokes, etc.). Absence of any RETENTION binding
     * is the default — the frame is retained.
     */
    @Seed.Item(key = Retention.KEY)
    public static final class Retention {
        public static final String KEY = "cg.qualifier:retention";
        private Retention() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier on CONFIG bindings declaring the frame's persistence policy";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "retention";
    }

    /**
     * Ephemeral — value sememe for CONFIG[RETENTION] indicating that frames of
     * the marked predicate are not persisted by the librarian (no body or record
     * written to storage). Handlers still fire and response frames flow back to
     * the submitter; nothing is durably recorded.
     *
     * <p>Used as the target of {@code CONFIG[RETENTION] → @Ephemeral} on a
     * predicate's manifest body. Default retention (no binding) is durable.
     */
    @Seed.Item(key = Ephemeral.KEY)
    public static final class Ephemeral {
        public static final String KEY = "cg.value:ephemeral";
        private Ephemeral() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "value marking a predicate's frames as non-persisted — handler fires, "
                        + "response flows back, nothing stored";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "ephemeral";
    }

    /**
     * Returns — the output-type half of an operator's contract.  A binding
     * with role {@code Returns} on an operator's manifest declares the type
     * of value the operator produces when its frame is evaluated.
     *
     * <p>Target convention: a {@link dev.everydaythings.graph.ref.SchemaRef
     * SchemaRef} (the {@code !} reference variant) pointing at a Value-
     * archetype — {@code Bool}, {@code Numeric}, {@code Color}, {@code Length},
     * {@code Quantity}, etc.  Reads as "the operator produces something
     * matching this schema."
     *
     * <p>Used at runtime for:
     * <ul>
     *   <li>Query routing — Bool-returning sememes appearing in a binding-
     *       target with a missing operand become matchers (partial application
     *       rule).  TypeRef positions are queries too.</li>
     *   <li>Type inference across composed operators.</li>
     *   <li>Index-driven discovery ("what produces X?" — walk
     *       {@code FRAME_BY_TARGET} on the Returns binding's target).</li>
     *   <li>Cross-runtime contract validation for polyglot implementations.</li>
     * </ul>
     */
    @Seed.Item(key = Returns.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Returns {
        public static final String KEY = "cg.quality:returns";
        private Returns() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding role on an operator's manifest naming the type of value the "
                        + "operator produces when its frame is evaluated";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "return";
    }

    /**
     * Limit — qualifier on ATTRIBUTE bindings declaring a result-count cap.
     *
     * <p>Used on LOOKUP frames as {@code ATTRIBUTE[LIMIT] → integer} to switch
     * the lookup from exact-match (point query) to prefix-match (range scan)
     * with the given upper bound on returned postings.
     *
     * <p>The pattern {@code ATTRIBUTE[<kind>] → value} is generic — Limit is one
     * such kind alongside Arity.
     */
    @Seed.Item(key = Limit.KEY)
    public static final class Limit {
        public static final String KEY = "cg.qualifier:limit";
        private Limit() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier on ATTRIBUTE bindings declaring a result-count cap "
                        + "for set-returning queries";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "limit";
    }
}
