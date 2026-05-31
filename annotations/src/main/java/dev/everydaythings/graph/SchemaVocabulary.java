package dev.everydaythings.graph;

import static dev.everydaythings.graph.Seed.*;

/**
 * Schema vocabulary — the sememes used to declare expectations on predicates
 * and archetypes, plus the predicates that carry those declarations.
 *
 * <p>Two kinds of entries live here:
 * <ul>
 *   <li><b>Schema predicates</b> ({@link Implements}) — predicates whose
 *       endorsed frames describe which artifacts implement a sememe.</li>
 *   <li><b>Universal qualifiers</b> ({@link Required}, {@link Arity},
 *       {@link Retention}, {@link Ephemeral}, {@link Limit}) — qualifier sememes
 *       used inside schema/metadata frames to refine declarations.</li>
 * </ul>
 *
 * <p>Each is a pure-data {@code @Seed}. They don't carry behavior; they're
 * categorical labels that the framework references by IID. The seed declarations
 * register their manifest bodies at bootstrap so they exist as real items in the
 * graph (queryable, gloss-able, lexeme-able), not just framework-internal IIDs.
 *
 * <h2>What about EXPECTS?</h2>
 *
 * <p>"Expectations" on archetypes are NOT a separate predicate.  They're just
 * bindings on the archetype's manifest whose role is wrapped in the schema
 * variant ({@code !<iid>} / {@link dev.everydaythings.graph.ref.SchemaRef
 * SchemaRef}).  The presence of a {@code !Role → constraint} binding on an
 * archetype's manifest declares "instances of me are expected to have a
 * Role-position binding satisfying this constraint."  No separate
 * {@code EXPECTS} sememe exists; the SchemaRef variant IS the mechanism.
 *
 * <p>Generate these via {@link Seed.Property @Seed.Property} with the
 * {@code schemaRole} parameter set instead of {@code role}.  See
 * {@code dev.everydaythings.graph.value.Color}'s channel-range expectations
 * and {@code dev.everydaythings.graph.value.Quantity}'s magnitude expectation
 * for examples.
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

        @Frame(predicate = Language.Gloss.KEY,
          field = @Binding(role = dev.everydaythings.graph.ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "predicate declaring that an artifact realizes a concept — its frames "
                        + "carry THEME → concept and AGENT → implementation reference "
                        + "(typically a class name or source code), letting CREATE find "
                        + "runnable forms and items declare what they implement";

        @Frame(predicate = Language.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "implement";

        @Frame(predicate = Language.Lexeme.KEY,
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

        @Frame(predicate = Language.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking an EXPECTS declaration as mandatory rather than "
                        + "merely permitted";

        @Frame(predicate = Language.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "required";
    }

    /**
     * Retention — record-binding role declaring how the system should treat
     * frames of the carrying body for persistence.
     *
     * <p>Used as {@code Retention → @Ephemeral} on predicate manifests'
     * records to mark a predicate's frames as non-persisted (queries,
     * transient UI events, scene updates, keystrokes, etc.).  Absence of any
     * Retention binding is the default — the frame is retained.
     *
     * <p>(Earlier this was {@code CONFIG[Retention]} with Retention as a
     * qualifier on a CONFIG-headed binding.  Direct-role is simpler: same
     * data, one less wrapper.)
     */
    @Seed.Item(key = Retention.KEY)
    public static final class Retention {
        public static final String KEY = "cg.role:retention";
        private Retention() {}

        @Frame(predicate = Language.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "record-binding role declaring a predicate's frame-persistence policy";

        @Frame(predicate = Language.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "retention";
    }

    /**
     * Ephemeral — value sememe for the {@code Retention} record binding,
     * indicating that frames of
     * the marked predicate are not persisted by the librarian (no body or record
     * written to storage). Handlers still fire and response frames flow back to
     * the submitter; nothing is durably recorded.
     *
     * <p>Used as the target of {@code Retention → @Ephemeral} on a
     * predicate's manifest record. Default retention (no binding) is durable.
     */
    @Seed.Item(key = Ephemeral.KEY)
    public static final class Ephemeral {
        public static final String KEY = "cg.value:ephemeral";
        private Ephemeral() {}

        @Frame(predicate = Language.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "value marking a predicate's frames as non-persisted — handler fires, "
                        + "response flows back, nothing stored";

        @Frame(predicate = Language.Lexeme.KEY,
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

        @Frame(predicate = Language.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding role on an operator's manifest naming the type of value the "
                        + "operator produces when its frame is evaluated";

        @Frame(predicate = Language.Lexeme.KEY,
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

        @Frame(predicate = Language.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier on ATTRIBUTE bindings declaring a result-count cap "
                        + "for set-returning queries";

        @Frame(predicate = Language.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "limit";
    }
}
