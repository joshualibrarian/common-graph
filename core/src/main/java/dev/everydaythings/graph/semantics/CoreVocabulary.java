package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.item.Bind;
import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;
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

    /** Forward reference — the "next" of something committed by digest before reveal. */
    @Seed(key = Next.KEY)
    public static final class Next {
        public static final String KEY = "cg.sememe:next";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Next() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss = "the one immediately following in sequence";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishAdjectiveLemma = "next";
    }

    /** Sequence — explicit ordinal position in a chain (defense-in-depth alongside hash chain). */
    @Seed(key = Sequence.KEY)
    public static final class Sequence {
        public static final String KEY = "cg.sememe:sequence";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Sequence() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss = "an ordered series; an ordinal position within it";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "sequence";
    }

    // ==================================================================================
    // Numeric / quorum
    // ==================================================================================

    /** Numeric threshold — m-of-n quorum, voting cutoff, attestation count, etc. */
    @Seed(key = Threshold.KEY)
    public static final class Threshold {
        public static final String KEY = "cg.sememe:threshold";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Threshold() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss = "a numeric cutoff or quorum; the minimum count required";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "threshold";
    }

    // ==================================================================================
    // Time-bounded / lifecycle
    // ==================================================================================

    /** Expiry timestamp — when an authorization, claim, or assertion ceases. */
    @Seed(key = Expires.KEY)
    public static final class Expires {
        public static final String KEY = "cg.sememe:expires";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Expires() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss =
                "the moment something ceases to be valid; an expiration time";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishVerbLemma = "expire";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String[] englishNounLemmas = {"expiry", "expiration"};
    }

    // ==================================================================================
    // Roles / participants (general concepts; used in many domains)
    // ==================================================================================

    /** Witness — a co-participant who attests to the truth of an assertion. */
    @Seed(key = Witness.KEY)
    public static final class Witness {
        public static final String KEY = "cg.sememe:witness";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Witness() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss =
                "one who attests to the truth of an assertion or the occurrence of an event";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "witness";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishVerbLemma = "witness";
    }

    /** Delegator — one who delegates authority, responsibility, or a task. */
    @Seed(key = Delegator.KEY)
    public static final class Delegator {
        public static final String KEY = "cg.sememe:delegator";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Delegator() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss = "one who delegates authority, responsibility, or a task";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "delegator";
    }

    // ==================================================================================
    // General concepts often used as reasons (for revocation, retraction, withdrawal)
    // ==================================================================================

    /** Compromise — an exposure or breach (cryptographic, structural, or social). */
    @Seed(key = Compromise.KEY)
    public static final class Compromise {
        public static final String KEY = "cg.sememe:compromise";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Compromise() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss =
                "an exposure or breach — cryptographic, structural, or social";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "compromise";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishVerbLemma = "compromise";
    }

    /** Retirement — routine cessation of use; no incident. */
    @Seed(key = Retirement.KEY)
    public static final class Retirement {
        public static final String KEY = "cg.sememe:retirement";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Retirement() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss =
                "routine cessation of use or service; no incident, just no longer active";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "retirement";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishVerbLemma = "retire";
    }

    /** Fraud — deceit, intentional misrepresentation. */
    @Seed(key = Fraud.KEY)
    public static final class Fraud {
        public static final String KEY = "cg.sememe:fraud";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Fraud() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss = "deceit; intentional misrepresentation";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "fraud";
    }

    /** Mistake — an honest error, no malice. */
    @Seed(key = Mistake.KEY)
    public static final class Mistake {
        public static final String KEY = "cg.sememe:mistake";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Mistake() {}

        @Bind(predicate = Gloss.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY})
        static final String englishGloss = "an honest error; no malice intended";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishNounLemma = "mistake";

        @Bind(predicate = Lexeme.KEY,
              role = ThematicRole.Value.KEY,
              qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String englishVerbLemma = "mistake";
    }
}
