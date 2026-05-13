package dev.everydaythings.graph;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.Expects;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * Core structural vocabulary — the sememes the system itself uses for its own
 * structural bindings, plus universal qualifiers for declaring expectations.
 *
 * <p>These differ from general-meaning sememes (which live in
 * {@link dev.everydaythings.graph.semantics.CoreVocabulary} and elsewhere): the
 * entries here are intrinsic to the type system's machinery — binding heads on
 * manifest bodies ({@link ItemId}, {@link Endorses}, {@link Follows},
 * {@link Config}, {@link Implementation}) and qualifier markers used in EXPECTS
 * declarations ({@link Required}).
 *
 * <p>Each is a pure-data {@code @Seed}. They don't carry behavior; they're
 * categorical labels that the framework references by IID. The seed declarations
 * register their manifest bodies at bootstrap so they exist as real items in the
 * graph (queryable, gloss-able, lexeme-able), not just framework-internal IIDs.
 */
public final class CoreVocabulary {

    private CoreVocabulary() {}

    // ==================================================================================
    // Manifest binding heads — structural roles intrinsic to every item's manifest
    // ==================================================================================

    /**
     * The item's identity — a literal IID stored on the manifest body.
     *
     * <p>Required on every item by Archetype's universal EXPECTS rule (Archetype
     * itself is the bootstrap exception). The binding's target is raw IID bytes,
     * not a {@code @iid} reference — declaring identity isn't pinnable.
     */
    @Seed.Item(key = ItemId.KEY)
    public static final class ItemId {
        public static final String KEY = "cg.structural:item-id";
        public static final ItemID IID = ItemID.fromString(KEY);
        private ItemId() {}

        @Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is an item's stable identity (IID)";

        @Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishNounLemmas = {"identity", "item id"};
    }

    /**
     * Endorsement — the manifest binding declaring that this item version
     * endorses a given frame body as part of its content.
     *
     * <p>The binding's target is a frame body CID. Multiple Endorses bindings
     * accumulate the manifest's endorsed-frame set; the manifest's signature
     * covers all of them transitively.
     */
    @Seed.Item(key = Endorses.KEY)
    public static final class Endorses {
        public static final String KEY = "cg.structural:endorses";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Endorses() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the manifest binding head whose target is the body of a frame "
                        + "this item version endorses";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "endorse";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "endorsement";
    }

    /**
     * Follows — the manifest binding declaring this version's parent (or parents).
     *
     * <p>Empty on inception manifests; one entry on a sequential commit; multiple
     * entries on a merge. The binding's target is a parent body CID (VID).
     */
    @Seed.Item(key = Follows.KEY)
    public static final class Follows {
        public static final String KEY = "cg.structural:follows";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Follows() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the manifest binding head whose target is a parent version this "
                        + "manifest follows in the version history";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "follow";
    }

    /**
     * Config — the binding head for configuration / policy / preference data
     * carried alongside an item's substantive content.
     *
     * <p>Lives on manifest bodies (item-level config), frame body bindings
     * (per-instance config), or record bindings (per-attestation config).
     * The qualifier list narrows the config dimension (e.g.,
     * {@code CONFIG[PRESENTATION]}, {@code CONFIG[REPLICATION]}).
     */
    @Seed.Item(key = Config.KEY)
    public static final class Config {
        public static final String KEY = "cg.structural:config";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Config() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is configuration, policy, or preference "
                        + "data; qualifiers narrow the config dimension";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishNounLemmas = {"config", "configuration"};
    }

    /**
     * Implementation — the manifest binding declaring the runtime form (Java
     * class, WASM module, code reference) that realizes this item.
     *
     * <p>Used on Code Item manifests and on seed manifests for items whose Java
     * class is paired with the seed via {@code @Embodies}. The runtime adapter
     * loads the implementation when the item is hydrated.
     */
    @Seed.Item(key = Implementation.KEY)
    public static final class Implementation {
        public static final String KEY = "cg.structural:implementation";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Implementation() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is a runtime form (Java class, "
                        + "WASM module, code reference) that realizes an item";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "implementation";
    }

    // ==================================================================================
    // Foundational predicates — declarative item-level metadata via endorsed frames
    // ==================================================================================

    /**
     * Handles — predicate declaring an item's API surface.
     *
     * <p>A HANDLES frame endorsed by an item's manifest declares "I respond to
     * frames whose head is this predicate." Each frame carries:
     * <ul>
     *   <li>{@link ThematicRole.Theme} → predicate-IID — which message type this handler is for</li>
     *   <li>{@link ThematicRole.Instrument} → handler reference (method name or code-item-ref) — the implementation</li>
     *   <li>{@link ThematicRole.Attribute}[{@link Arity}] → integer — optional, expected binding count</li>
     *   <li>{@link ThematicRole.Attribute}[other qualifiers] → values — extensible metadata
     *       (priority, return shape, etc.)</li>
     * </ul>
     *
     * <p>Smalltalk-style dispatch: frames are messages, items are receivers,
     * predicates are selectors, HANDLES frames form the method dictionary. Items
     * dispatch on incoming frames via their endorsed HANDLES list, finding the
     * one whose Theme matches the frame's head, then invoking the Instrument.
     *
     * <p>The handler reference (Instrument) is implementation-layer concern:
     * Java reflection on {@code @Handler}-annotated methods, or polyglot bundle
     * function-table keyed by predicate IID. The data layer stays language-agnostic.
     *
     * <p>API surface lives as endorsed frames (not direct manifest bindings) so
     * that rich metadata fits naturally and HANDLES can be queried, inherited
     * via archetype, and extended at runtime.
     */
    @Seed.Item(key = Handles.KEY, head = Predicate.KEY)
    public static final class Handles {
        public static final String KEY = "cg.sememe:handles";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Handles() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate declaring an item's API — frames endorsed by an item "
                        + "to declare which message types it handles and how";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "handle";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "handler";
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
        public static final ItemID IID = ItemID.fromString(KEY);
        private Required() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking an EXPECTS declaration as mandatory rather than "
                        + "merely permitted";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "required";
    }

    /**
     * Arity — qualifier on {@link ThematicRole.Attribute} bindings declaring an
     * arity (count of expected arguments / bindings).
     *
     * <p>Used in HANDLES frames as {@code ATTRIBUTE[ARITY] → integer} to indicate
     * how many bindings the handler expects on the incoming frame, and in
     * operator/function declarations to declare their argument count.
     *
     * <p>The pattern {@code ATTRIBUTE[<kind>] → value} is generic — Arity is one
     * such kind. Other kinds (Priority, Return-shape, etc.) attach the same way
     * and can be added as needed without inventing new roles.
     */
    @Seed.Item(key = Arity.KEY)
    public static final class Arity {
        public static final String KEY = "cg.qualifier:arity";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Arity() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier on ATTRIBUTE bindings declaring an arity — a count of "
                        + "expected arguments or bindings";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "arity";
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
        public static final ItemID IID = ItemID.fromString(KEY);
        private Retention() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier on CONFIG bindings declaring the frame's persistence policy";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
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
        public static final ItemID IID = ItemID.fromString(KEY);
        private Ephemeral() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "value marking a predicate's frames as non-persisted — handler fires, "
                        + "response flows back, nothing stored";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
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
        public static final ItemID IID = ItemID.fromString(KEY);
        private Limit() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier on ATTRIBUTE bindings declaring a result-count cap "
                        + "for set-returning queries";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "limit";
    }

    /**
     * The root archetype — every item's manifest head transitively reaches here.
     *
     * <p>Self-typing: Archetype's manifest head references its own IID. Every other
     * archetype (Item itself, Photograph, Code, Predicate, etc.) declares Archetype
     * as its head, directly or through a chain.
     */
    @Seed.Item(key = Archetype.KEY)
    public static final class Archetype {
        public static final String KEY = "cg.archetype:archetype";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Archetype() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the root archetype — the kind-of-thing every item's manifest is, "
                        + "the meta-root every head chain terminates at";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "archetype";

        /**
         * The universal item-hood rule: every instance — every item in the system —
         * must carry an {@code ITEM_ID} binding on its manifest body. Propagates to
         * every descendant via the head chain. Archetype itself is the bootstrap
         * exception (no ITEM_ID binding on its own manifest).
         */
        @Frame(predicate = Expects.KEY,
              field = @Binding(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
        static final ItemID expectItemId = Manifest.ITEM_ID;
    }

    /**
     * The archetype of all predicates — items that serve as the head of frame bodies.
     *
     * <p>Predicates have role-keyed EXPECTS declarations specifying what bindings
     * their frame-instances must carry. Each concrete predicate (AUTHORED, TITLE,
     * MOVE, IMPLEMENTATION, EXPECTS itself) is an instance of Predicate, with
     * {@code head = Item.Predicate.KEY} on its {@code @Seed} annotation.
     */
    @Seed.Item(key = Predicate.KEY)
    public static final class Predicate {
        public static final String KEY = "cg.archetype:predicate";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Predicate() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of all predicates — items used as the head of frame bodies; "
                        + "predicates declare role-keyed EXPECTS for their frame-instances";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "predicate";
    }

    /**
     * Qualifier marking a binding's text target as a fully-qualified Java class name.
     *
     * <p>Used on bindings whose role is {@link Implementation} or similar — the
     * target is a plain text literal carrying the FQCN, and the qualifier identifies
     * "this text means a Java class." The binding is the canonical attribution of a
     * code-item's runtime form for the JVM. Later: a sibling qualifier (or a binary
     * literal target with this same qualifier) carries actual bytecode.
     *
     * <p>Lives in CoreVocabulary as part of the post-Literal-cleanup convention:
     * encoding-primitive type (text vs binary) is CBOR's job; semantic narrowing
     * (this text is a JVM class name) is a qualifier sememe.
     */
    @Seed.Item(key = JavaClass.KEY)
    public static final class JavaClass {
        public static final String KEY = "cg.address:java-class";
        public static final ItemID IID = ItemID.fromString(KEY);
        private JavaClass() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a binding's text target as a fully-qualified "
                        + "Java class name";
    }

    /**
     * The archetype of all code-items — items that represent implementations of
     * an archetype's contract (a library, a class, a bundle, a script).
     *
     * <p>Distinct from {@link Item} itself: an item is a domain entity (a chess
     * game, a book, a person, a librarian); a code-item is the artifact that
     * implements an archetype's behavior in some host language or runtime. The
     * two are orthogonal — a chess game (Item) has its rules encoded in some
     * code-item (a Code instance), and that code-item is itself an Item too.
     *
     * <p>Code-items typically carry:
     * <ul>
     *   <li>An {@code ITEM_ID} binding (as every manifest body does)</li>
     *   <li>{@code ENDORSES} bindings pointing at frame bodies the code embodies
     *       — e.g., the {@code HANDLES} frames declaring its dispatch surface</li>
     *   <li>Some marker of the implementation form — class name (text), bytecode
     *       (binary), source (text), etc. Phase 1 leaves this binding off; the
     *       host language is implicit in the IID convention until we need otherwise.</li>
     * </ul>
     *
     * <p>The Java inheritance hierarchy of the implementing class is completely
     * separate from this archetypal hierarchy. Code's head is Archetype (the root);
     * it is not a kind of Item.
     */
    @Seed.Item(key = Code.KEY)
    public static final class Code {
        public static final String KEY = "cg.archetype:code";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Code() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of code-items — items that represent implementations "
                        + "(libraries, classes, scripts, bundles), distinct from the "
                        + "data they operate on";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "code";
    }
}
