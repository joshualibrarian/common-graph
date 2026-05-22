package dev.everydaythings.graph;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.scene.SceneNode;
import dev.everydaythings.graph.scene.SceneText;

import static dev.everydaythings.graph.Seed.*;

/**
 * Core structural vocabulary — the sememes the system itself uses for its own
 * structural bindings, plus universal qualifiers for declaring expectations.
 *
 * <p>These differ from general-meaning sememes (which live in
 * {@link dev.everydaythings.graph.CoreVocabulary} and elsewhere): the
 * entries here are intrinsic to the type system's machinery — binding heads on
 * manifest bodies ({@link ItemId}, {@link Endorses}, {@link Follows},
 * {@link Config}) and qualifier markers used in EXPECTS
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
        private ItemId() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is an item's stable identity (IID)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
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
        private Endorses() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the manifest binding head whose target is the body of a frame "
                        + "this item version endorses";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "endorse";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
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
        private Follows() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the manifest binding head whose target is a parent version this "
                        + "manifest follows in the version history";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
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
        private Config() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is configuration, policy, or preference "
                        + "data; qualifiers narrow the config dimension";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishNounLemmas = {"config", "configuration"};
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
        private Handles() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate declaring an item's API — frames endorsed by an item "
                        + "to declare which message types it handles and how";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "handle";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "handler";
    }

    // ==================================================================================
    // Universal qualifiers — Required, Arity, Retention, Ephemeral, Limit — moved
    // to SchemaVocabulary, alongside the Expects and Implements predicates.
    // ==================================================================================

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
        private Archetype() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the root archetype — the kind-of-thing every item's manifest is, "
                        + "the meta-root every head chain terminates at";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "archetype";

        /**
         * Terminal default scene at the root of the {@code CONFIG[Presentation]}
         * cascade.  Every other archetype's manifest body has head = Archetype
         * (directly or via a chain); when nothing more specific is declared
         * along the chain, the cascade falls through to here.
         *
         * <p>The literal "Common Graph item" is a deliberate placeholder.  If
         * a user sees it in their UI, the archetype chain for that item
         * neglected to declare a more specific scene.  Loud-but-rendering:
         * tells you exactly which archetypes are missing declarations without
         * breaking the render path.
         */
        @Seed.RecordBinding(role = Config.KEY,
                            qualifiers = {SchemaVocabulary.Presentation.KEY})
        static final SceneNode defaultScene = new SceneText("Common Graph item");

        // The previous "universal item-hood rule" lived here as an endorsed
        // EXPECTS frame, claiming every instance must carry ITEM_ID.  That's
        // not actually universal — Value instances (Color bodies, Quantity
        // bodies) and frame bodies don't carry ITEM_ID.  The rule belongs on
        // a more specific meta-archetype (ItemKind, when seeded) that mints
        // item-shaped instances.  Pending; tracked separately.
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
        private Predicate() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of all predicates — items used as the head of frame bodies; "
                        + "predicates declare role-keyed EXPECTS for their frame-instances";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "predicate";
    }

    /**
     * The archetype of free variables — sememes that stand in for values supplied
     * by the resolution context at evaluation time, rather than carrying a fixed
     * value of their own.
     *
     * <p>Examples: {@code DevicePixelSize} (bound by the session at layout time),
     * {@code Viewport}, {@code BaseFontSize}, {@code ExchangeRate}, {@code CurrentTime}.
     * Each is an instance of Variable; each appears as a target reference in
     * expressions that the resolver will substitute when it can fetch the binding
     * from the current context.
     *
     * <p>Distinct from Predicate: a predicate is the head of a frame body asserting
     * a relation; a variable is a leaf reference standing for a future value.
     * Variables typically appear in {@code ref}-target binding positions inside
     * scale expressions, layout templates, and other contexts that depend on
     * runtime parameters.
     */
    @Seed.Item(key = Variable.KEY)
    public static final class Variable {
        public static final String KEY = "cg.archetype:variable";
        private Variable() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of free variables — sememes whose values come from the "
                        + "resolution context (session, layout, conversation) rather than "
                        + "being declared statically";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "variable";
    }

    /**
     * The archetype of qualities — sememes used in binding-role positions to
     * predicate properties of things.
     *
     * <p>A Quality names a property: Width, Height, Color, FontSize, Padding,
     * Background, Foreground, Opacity. When a body has a binding whose role is
     * a Quality, the binding asserts "this thing has property X with value Y."
     *
     * <p>Many Qualities pair naturally with Values — their targets are typically
     * structured Value bodies. Width's targets are Length-quantities (a kind of
     * Value). Color's targets are Color-values. The pairing is declared via
     * EXPECTS on each Quality archetype.
     *
     * <p>The Quality/Value distinction is positional, not exclusive: some
     * sememes (Color) are themselves Qualities AND their instances are Values.
     * Color, used as a binding-role, predicates a property; specific colors
     * (Body[head=Color, R=…, G=…, B=…]) are Value-shaped data. The dual is
     * intentional — language uses Color both ways.
     *
     * <p>Pairs with {@link dev.everydaythings.graph.value.Value Value};
     * siblings under {@link Archetype}.
     */
    @Seed.Item(key = Quality.KEY)
    public static final class Quality {
        public static final String KEY = "cg.archetype:quality";
        private Quality() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of qualities — sememes used in binding-role positions to "
                        + "predicate properties of things (Width, Color, FontSize, …)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "quality";
    }

    // The Value meta-archetype's seed declaration lives on its Java mirror at
    // dev.everydaythings.graph.value.Value — an abstract Body subclass whose
    // concrete subclasses (Color, Quantity, ...) are the typed value-classes.
    // Reference its IID via {@code value.Value.KEY}.

    // ==================================================================================
    // General-purpose sememes — units of meaning that don't belong to any single
    // domain. Used as binding qualifiers and value targets across many frames.
    //
    // (Domain-specific narrowings live in their own vocabularies — e.g.,
    //  IdentityVocabulary for Signing / Encryption purposes.)
    // ==================================================================================

    /** Sequence — explicit ordinal position in a chain (defense-in-depth alongside hash chain). */
    @Seed.Item(key = Sequence.KEY)
    public static final class Sequence {
        public static final String KEY = "cg.sememe:sequence";
        private Sequence() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an ordered series; an ordinal position within it";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "sequence";
    }

    /** Numeric threshold — m-of-n quorum, voting cutoff, attestation count, etc. */
    @Seed.Item(key = Threshold.KEY)
    public static final class Threshold {
        public static final String KEY = "cg.sememe:threshold";
        private Threshold() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a numeric cutoff or quorum; the minimum count required";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "threshold";
    }

    /** Expiry timestamp — when an authorization, claim, or assertion ceases. */
    @Seed.Item(key = Expires.KEY)
    public static final class Expires {
        public static final String KEY = "cg.sememe:expires";
        private Expires() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the moment something ceases to be valid; an expiration time";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "expire";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishNounLemmas = {"expiry", "expiration"};
    }

    /**
     * Result — the predicate the matcher orchestrator uses to wrap each
     * query match.  A RESULT frame carries a {@code THEME → @<matched-iid>}
     * binding pointing at the item that satisfied the query, along with
     * any other metadata the matcher cares to emit (match confidence,
     * variable bindings, etc., later).
     *
     * <p>RESULT frames are ephemeral by convention — they're the matcher's
     * answer to a particular query, not durable assertions about the world.
     */
    @Seed.Item(key = Result.KEY, head = Predicate.KEY)
    public static final class Result {
        public static final String KEY = "cg.predicate:result";
        private Result() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate the matcher emits to wrap each item that satisfied a query";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "result";
    }

    /**
     * Salt — random bytes mixed into a value's bindings to randomize its
     * structural hash, defeating brute-force enumeration of elided low-entropy
     * values.
     *
     * <p>Use is by composition, not protocol: wrap the value to be salted in
     * a body (a value archetype that admits a SALT binding, or the generic
     * {@link dev.everydaythings.graph.value.Salted} wrapper), include the
     * SALT binding with random bytes, hash normally.  When the wrapper is
     * later elided via {@code RedactedTarget}, the salted hash is preserved
     * and the salt is discarded with the value — an attacker who knows the
     * value space can't enumerate hashes without also guessing the salt.
     *
     * <p>Salts are part of the identity from the moment the binding is
     * composed; they cannot be added or removed later without changing the
     * hash.  Choose at compose time.
     */
    @Seed.Item(key = Salt.KEY)
    public static final class Salt {
        public static final String KEY = "cg.sememe:salt";
        private Salt() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "random bytes mixed into a value to randomize its structural hash; "
                        + "defeats brute-force enumeration of low-entropy elided values";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "salt";
    }

    /**
     * The source-attribution predicate — names the dataset / vocabulary a sememe
     * was imported from.
     *
     * <p>Convention: hand-written CG-native sememes carry NO source frame; their
     * absence means "implicitly part of CG core." Imported sememes (from WordNet,
     * CILI, VerbNet, etc.) get a SOURCE frame via {@code @Bind} attribution.
     *
     * <p>Body shape:
     * <pre>
     * SOURCE
     *     VALUE              → @vocabulary-sememe (e.g., Oewn, Cili)
     *     ATTRIBUTE [VERSION] → "2025"             # optional version literal
     * </pre>
     *
     * <p>Specific identifier predicates ({@link WordnetSynsetId}, {@link CiliId})
     * carry the source's own ID for the sememe — alongside SOURCE, they pin the
     * imported sememe to its origin's identifier system for cross-vocabulary merge.
     *
     * <p>Source-vocabulary sememes are inner classes here for proximity (small
     * pure-data targets, no behavior of their own).
     */
    @Seed.Item(key = Source.KEY)
    @Embodies(key = Source.KEY)
    public static class Source extends Item {

        /** Canonical key for the source-attribution sememe. */
        public static final String KEY = "cg.sememe:source";

        /** The deterministic IID for the source-attribution sememe. */

        public Source(ItemRef iid, Librarian librarian) {
            super(iid, librarian);
        }

        // ==================================================================================
        // Source-vocabulary sememes (targets of SOURCE → VALUE bindings)
        // ==================================================================================

        /** Open English WordNet — the OEWN project (any release; version on the binding). */
        @Seed.Item(key = Oewn.KEY)
        public static final class Oewn {
            public static final String KEY = "cg.source:oewn";
            private Oewn() {}
        }

        /** Collaborative Interlingual Index — language-neutral concept identifiers. */
        @Seed.Item(key = Cili.KEY)
        public static final class Cili {
            public static final String KEY = "cg.source:cili";
            private Cili() {}
        }
    }

    /**
     * Preferred — marks something as chosen in preference to alternatives.
     * Generic qualifier; appears wherever the data offers multiple options
     * and one is to be picked first (preferred email, preferred address,
     * preferred witness).
     *
     * <p>Grounded in OEWN synset oewn-02130856-a (CILI {@code i11628}):
     * "chosen in preference to another" (preferred, selected).
     */
    @Seed.Item(key = Preferred.KEY)
    @Seed.Cili("i11628")
    public static final class Preferred {
        public static final String KEY = "cg.sememe:preferred";
        private Preferred() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "chosen in preference to another";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishAdjLemmas = {"preferred", "selected"};
    }

    /**
     * CurrentTime — the resolution-context variable carrying the current
     * wall-clock time.  An instance of {@link Variable}; its runtime value
     * is supplied by the session (typically as an {@code Instant} or
     * milliseconds-since-epoch) and re-read at each presenter pass.
     *
     * <p>Scenes that reference CurrentTime (a clock display, a freshness
     * indicator, a countdown) declare the dependency simply by carrying
     * the reference; the session's tick driver re-presents the scene at
     * the painter's native cadence and CurrentTime resolves to the
     * current value each time.  Wire-side: the resolver leaves CurrentTime
     * references unresolved so remote clients re-read locally.
     */
    @Seed.Item(key = CurrentTime.KEY, head = Variable.KEY)
    public static final class CurrentTime {
        public static final String KEY = "cg.variable:current-time";
        private CurrentTime() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the resolution-context variable carrying the current wall-clock time";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishNounLemmas = {"current time", "now"};
    }

    /** Witness — a co-participant who attests to the truth of an assertion. */
    @Seed.Item(key = Witness.KEY)
    public static final class Witness {
        public static final String KEY = "cg.sememe:witness";
        private Witness() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one who attests to the truth of an assertion or the occurrence of an event";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "witness";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "witness";
    }

    // ==================================================================================
    // Reason sememes — formal causes for revocation / retraction / repudiation.
    // Generic enough to apply to non-identity retractions too (a fraudulent claim,
    // a mistaken assertion), so they live here rather than in IdentityVocabulary
    // even though their first use site is REVOCATION.
    // ==================================================================================

    /** Compromise — an exposure or breach (cryptographic, structural, or social). */
    @Seed.Item(key = Compromise.KEY)
    public static final class Compromise {
        public static final String KEY = "cg.sememe:compromise";
        private Compromise() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an exposure or breach — cryptographic, structural, or social";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "compromise";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "compromise";
    }

    /** Retirement — routine cessation of use; no incident. */
    @Seed.Item(key = Retirement.KEY)
    public static final class Retirement {
        public static final String KEY = "cg.sememe:retirement";
        private Retirement() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "routine cessation of use or service; no incident, just no longer active";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "retirement";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "retire";
    }

    /** Fraud — deceit, intentional misrepresentation. */
    @Seed.Item(key = Fraud.KEY)
    public static final class Fraud {
        public static final String KEY = "cg.sememe:fraud";
        private Fraud() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "deceit; intentional misrepresentation";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fraud";
    }

    /** Mistake — an honest error, no malice. */
    @Seed.Item(key = Mistake.KEY)
    public static final class Mistake {
        public static final String KEY = "cg.sememe:mistake";
        private Mistake() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an honest error; no malice intended";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "mistake";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "mistake";
    }
}
