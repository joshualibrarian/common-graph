package dev.everydaythings.graph;

import dev.everydaythings.graph.id.ItemRef;
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
     * The EXPECTS predicate sememe — head of frames declaring the schema a sememe expects.
     *
     * <p>An EXPECTS frame endorsed by a sememe's manifest declares "this sememe's
     * use should include this expectation." The qualifier on the binding within the
     * EXPECTS frame disambiguates what kind of expectation it is:
     *
     * <ul>
     *   <li>{@code TOPIC[ROLE] → role-IID} — expects a binding with this role.
     *       For predicate sememes (e.g., AUTHORED), the binding is on the FRAME body;
     *       for archetype sememes (e.g., Chess), it's on the INSTANCE'S MANIFEST body.</li>
     *   <li>{@code TOPIC[FRAME] → predicate-IID} — expects an endorsed frame with this
     *       predicate. (Archetype context only; deferred until we have a use case.)</li>
     * </ul>
     *
     * <p>EXPECTS is one mechanism, used contextually. The qualifier carries the meaning;
     * the consumer (validation, UI generation, CREATE-time instantiability checks)
     * interprets per use.
     *
     * <p>The presence of any EXPECTS endorsement is also the data-side signal that a
     * concept is INSTANTIABLE — the kind of thing that has instances. {@code @Mints(K)}
     * cross-validates against this at bootstrap: a class declaring "I implement instances
     * of K" requires K to declare what its instances should look like.
     */
    @Seed.Item(key = Expects.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Expects {
        public static final String KEY = "cg.sememe:expects";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Expects() {}
    }

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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Implements() {}
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
