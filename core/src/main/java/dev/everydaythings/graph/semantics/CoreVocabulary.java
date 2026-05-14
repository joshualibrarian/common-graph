package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;

/**
 * General-purpose small sememes — units of meaning that don't belong to any
 * single domain. Used as binding qualifiers and value targets across many
 * predicate frames.
 *
 * <p>Each entry here is a general English concept. Their use in specific
 * domains (e.g., {@code Next} as the pre-rotation qualifier in identity
 * frames; {@code Threshold} as m-of-n in cryptographic events) is one
 * application of a sememe whose meaning is broader.
 *
 * <p>Domain-specific narrowings live in their own vocabularies (e.g.,
 * {@code identity.IdentityVocabulary} for {@code Signing} / {@code Encryption}
 * which are specifically key-track purposes, not general English meanings).
 *
 * <p>Each entry carries an English gloss and lemma lexemes via {@code @Bind}
 * annotations so the bootstrap produces queryable token-dictionary entries.
 */
public final class CoreVocabulary {

    private CoreVocabulary() {}

    // ==================================================================================
    // Sequence / chain mechanics
    // ==================================================================================

    /** Sequence — explicit ordinal position in a chain (defense-in-depth alongside hash chain). */
    @Seed.Item(key = Sequence.KEY)
    public static final class Sequence {
        public static final String KEY = "cg.sememe:sequence";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Sequence() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an ordered series; an ordinal position within it";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "sequence";
    }

    // ==================================================================================
    // Numeric / quorum
    // ==================================================================================

    /** Numeric threshold — m-of-n quorum, voting cutoff, attestation count, etc. */
    @Seed.Item(key = Threshold.KEY)
    public static final class Threshold {
        public static final String KEY = "cg.sememe:threshold";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Threshold() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a numeric cutoff or quorum; the minimum count required";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "threshold";
    }

    // ==================================================================================
    // Time-bounded / lifecycle
    // ==================================================================================

    /** Expiry timestamp — when an authorization, claim, or assertion ceases. */
    @Seed.Item(key = Expires.KEY)
    public static final class Expires {
        public static final String KEY = "cg.sememe:expires";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Expires() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the moment something ceases to be valid; an expiration time";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "expire";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] englishNounLemmas = {"expiry", "expiration"};
    }

    // ==================================================================================
    // Roles / participants (general concepts; used in many domains)
    // ==================================================================================

    /** Witness — a co-participant who attests to the truth of an assertion. */
    @Seed.Item(key = Witness.KEY)
    public static final class Witness {
        public static final String KEY = "cg.sememe:witness";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Witness() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one who attests to the truth of an assertion or the occurrence of an event";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "witness";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "witness";
    }

    /** Delegator — one who delegates authority, responsibility, or a task. */
    @Seed.Item(key = Delegator.KEY)
    public static final class Delegator {
        public static final String KEY = "cg.sememe:delegator";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Delegator() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "one who delegates authority, responsibility, or a task";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "delegator";
    }

}
