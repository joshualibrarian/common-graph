package dev.everydaythings.graph;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import static dev.everydaythings.graph.Seed.*;

/**
 * Core structural vocabulary — the sememes the system itself uses for its own
 * structural bindings, plus universal qualifiers for declaring expectations.
 *
 * <p>These differ from general-meaning sememes (which live in
 * {@link dev.everydaythings.graph.CoreVocabulary} and elsewhere): the
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private ItemId() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is an item's stable identity (IID)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Endorses() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the manifest binding head whose target is the body of a frame "
                        + "this item version endorses";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "endorse";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Follows() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the manifest binding head whose target is a parent version this "
                        + "manifest follows in the version history";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Config() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the binding head whose target is configuration, policy, or preference "
                        + "data; qualifiers narrow the config dimension";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Handles() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate declaring an item's API — frames endorsed by an item "
                        + "to declare which message types it handles and how";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "handle";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Archetype() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the root archetype — the kind-of-thing every item's manifest is, "
                        + "the meta-root every head chain terminates at";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "archetype";

        /**
         * The universal item-hood rule: every instance — every item in the system —
         * must carry an {@code ITEM_ID} binding on its manifest body. Propagates to
         * every descendant via the head chain. Archetype itself is the bootstrap
         * exception (no ITEM_ID binding on its own manifest).
         */
        @Frame(predicate = SchemaVocabulary.Expects.KEY,
              field = @Binding(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
        static final ItemRef expectItemId = Manifest.ITEM_ID;
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Predicate() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of all predicates — items used as the head of frame bodies; "
                        + "predicates declare role-keyed EXPECTS for their frame-instances";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "predicate";
    }

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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
        public static final ItemRef IID = ItemRef.fromString(KEY);

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
            public static final ItemRef IID = ItemRef.fromString(KEY);
            private Oewn() {}
        }

        /** Collaborative Interlingual Index — language-neutral concept identifiers. */
        @Seed.Item(key = Cili.KEY)
        public static final class Cili {
            public static final String KEY = "cg.source:cili";
            public static final ItemRef IID = ItemRef.fromString(KEY);
            private Cili() {}
        }
    }

    /** Witness — a co-participant who attests to the truth of an assertion. */
    @Seed.Item(key = Witness.KEY)
    public static final class Witness {
        public static final String KEY = "cg.sememe:witness";
        public static final ItemRef IID = ItemRef.fromString(KEY);
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
}
