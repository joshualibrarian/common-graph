package dev.everydaythings.graph.linguistics;

import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Grammatical feature sememes — narrowing qualifiers on lexemes and other
 * morphologically-aware frames. Distinguish lemmas from inflected forms,
 * tenses, numbers, persons, cases, voices, moods, etc.
 *
 * <p>The outer class is the grammatical-feature meta-sememe
 * ({@code cg.sememe:grammatical-feature}). Inner classes are specific features.
 *
 * <p>Canonical-key prefix: {@code cg.feat:}.
 *
 * <p>The set seeded here covers what English commonly needs (lemma, tense,
 * number, person, voice, basic mood, participle/infinitive). Cases and gender
 * are included for languages that use them. Additional features (aspect-specific,
 * evidentials, etc.) can be added when imports demand them.
 *
 * <p>Pure-data seeds.
 */
@Seed(key = GrammaticalFeature.KEY)
public final class GrammaticalFeature {

    /** Canonical key for the grammatical-feature meta-sememe. */
    public static final String KEY = "cg.sememe:grammatical-feature";

    /** The deterministic IID for the grammatical-feature meta-sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    private GrammaticalFeature() {}

    // ==================================================================================
    // Lemma marker
    // ==================================================================================

    /** Lemma — the canonical / dictionary form (vs an inflected form). */
    @Seed(key = Lemma.KEY)
    public static final class Lemma {
        public static final String KEY = "cg.feat:lemma";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Lemma() {}
    }

    // ==================================================================================
    // Tense
    // ==================================================================================

    /** Past tense. */
    @Seed(key = Past.KEY)
    public static final class Past {
        public static final String KEY = "cg.feat:past";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Past() {}
    }

    /** Present tense. */
    @Seed(key = Present.KEY)
    public static final class Present {
        public static final String KEY = "cg.feat:present";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Present() {}
    }

    /** Future tense. */
    @Seed(key = Future.KEY)
    public static final class Future {
        public static final String KEY = "cg.feat:future";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Future() {}
    }

    // ==================================================================================
    // Number
    // ==================================================================================

    /** Singular. */
    @Seed(key = Singular.KEY)
    public static final class Singular {
        public static final String KEY = "cg.feat:singular";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Singular() {}
    }

    /** Plural. */
    @Seed(key = Plural.KEY)
    public static final class Plural {
        public static final String KEY = "cg.feat:plural";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Plural() {}
    }

    // ==================================================================================
    // Person
    // ==================================================================================

    /** First person — speaker(s). */
    @Seed(key = FirstPerson.KEY)
    public static final class FirstPerson {
        public static final String KEY = "cg.feat:first-person";
        public static final ItemID IID = ItemID.fromString(KEY);
        private FirstPerson() {}
    }

    /** Second person — addressee(s). */
    @Seed(key = SecondPerson.KEY)
    public static final class SecondPerson {
        public static final String KEY = "cg.feat:second-person";
        public static final ItemID IID = ItemID.fromString(KEY);
        private SecondPerson() {}
    }

    /** Third person — referent(s) other than speaker or addressee. */
    @Seed(key = ThirdPerson.KEY)
    public static final class ThirdPerson {
        public static final String KEY = "cg.feat:third-person";
        public static final ItemID IID = ItemID.fromString(KEY);
        private ThirdPerson() {}
    }

    // ==================================================================================
    // Aspect / participle / infinitive
    // ==================================================================================

    /** Participle (e.g., "running", "broken"). */
    @Seed(key = Participle.KEY)
    public static final class Participle {
        public static final String KEY = "cg.feat:participle";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Participle() {}
    }

    /** Progressive aspect ("is running"). */
    @Seed(key = Progressive.KEY)
    public static final class Progressive {
        public static final String KEY = "cg.feat:progressive";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Progressive() {}
    }

    /** Perfect aspect ("has run"). */
    @Seed(key = Perfect.KEY)
    public static final class Perfect {
        public static final String KEY = "cg.feat:perfect";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Perfect() {}
    }

    /** Infinitive ("to run"). */
    @Seed(key = Infinitive.KEY)
    public static final class Infinitive {
        public static final String KEY = "cg.feat:infinitive";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Infinitive() {}
    }

    // ==================================================================================
    // Mood
    // ==================================================================================

    /** Imperative mood ("Run!"). */
    @Seed(key = Imperative.KEY)
    public static final class Imperative {
        public static final String KEY = "cg.feat:imperative";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Imperative() {}
    }

    /** Indicative mood (statements of fact). */
    @Seed(key = Indicative.KEY)
    public static final class Indicative {
        public static final String KEY = "cg.feat:indicative";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Indicative() {}
    }

    /** Subjunctive mood (counterfactuals, hypotheticals). */
    @Seed(key = Subjunctive.KEY)
    public static final class Subjunctive {
        public static final String KEY = "cg.feat:subjunctive";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Subjunctive() {}
    }

    // ==================================================================================
    // Comparative / superlative (for adjectives & adverbs)
    // ==================================================================================

    /** Comparative form ("bigger", "more carefully"). */
    @Seed(key = Comparative.KEY)
    public static final class Comparative {
        public static final String KEY = "cg.feat:comparative";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Comparative() {}
    }

    /** Superlative form ("biggest", "most carefully"). */
    @Seed(key = Superlative.KEY)
    public static final class Superlative {
        public static final String KEY = "cg.feat:superlative";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Superlative() {}
    }

    // ==================================================================================
    // Voice
    // ==================================================================================

    /** Passive voice. */
    @Seed(key = Passive.KEY)
    public static final class Passive {
        public static final String KEY = "cg.feat:passive";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Passive() {}
    }
}
