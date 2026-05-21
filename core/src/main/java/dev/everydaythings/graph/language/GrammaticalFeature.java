package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Seed;

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
@Seed.Item(key = GrammaticalFeature.KEY)
public final class GrammaticalFeature {

    /** Canonical key for the grammatical-feature meta-sememe. */
    public static final String KEY = "cg.sememe:grammatical-feature";

    private GrammaticalFeature() {}

    // ==================================================================================
    // Lemma marker
    // ==================================================================================

    /** Lemma — the canonical / dictionary form (vs an inflected form). */
    @Seed.Item(key = Lemma.KEY)
    public static final class Lemma {
        public static final String KEY = "cg.feat:lemma";
        private Lemma() {}
    }

    // ==================================================================================
    // Tense
    // ==================================================================================

    /** Past tense. */
    @Seed.Item(key = Past.KEY)
    public static final class Past {
        public static final String KEY = "cg.feat:past";
        private Past() {}
    }

    /** Present tense. */
    @Seed.Item(key = Present.KEY)
    public static final class Present {
        public static final String KEY = "cg.feat:present";
        private Present() {}
    }

    /** Future tense. */
    @Seed.Item(key = Future.KEY)
    public static final class Future {
        public static final String KEY = "cg.feat:future";
        private Future() {}
    }

    // ==================================================================================
    // Number
    // ==================================================================================

    /** Singular. */
    @Seed.Item(key = Singular.KEY)
    public static final class Singular {
        public static final String KEY = "cg.feat:singular";
        private Singular() {}
    }

    /** Plural. */
    @Seed.Item(key = Plural.KEY)
    public static final class Plural {
        public static final String KEY = "cg.feat:plural";
        private Plural() {}
    }

    // ==================================================================================
    // Person
    // ==================================================================================

    /** First person — speaker(s). */
    @Seed.Item(key = FirstPerson.KEY)
    public static final class FirstPerson {
        public static final String KEY = "cg.feat:first-person";
        private FirstPerson() {}
    }

    /** Second person — addressee(s). */
    @Seed.Item(key = SecondPerson.KEY)
    public static final class SecondPerson {
        public static final String KEY = "cg.feat:second-person";
        private SecondPerson() {}
    }

    /** Third person — referent(s) other than speaker or addressee. */
    @Seed.Item(key = ThirdPerson.KEY)
    public static final class ThirdPerson {
        public static final String KEY = "cg.feat:third-person";
        private ThirdPerson() {}
    }

    // ==================================================================================
    // Aspect / participle / infinitive
    // ==================================================================================

    /** Participle (e.g., "running", "broken"). */
    @Seed.Item(key = Participle.KEY)
    public static final class Participle {
        public static final String KEY = "cg.feat:participle";
        private Participle() {}
    }

    /** Progressive aspect ("is running"). */
    @Seed.Item(key = Progressive.KEY)
    public static final class Progressive {
        public static final String KEY = "cg.feat:progressive";
        private Progressive() {}
    }

    /** Perfect aspect ("has run"). */
    @Seed.Item(key = Perfect.KEY)
    public static final class Perfect {
        public static final String KEY = "cg.feat:perfect";
        private Perfect() {}
    }

    /** Infinitive ("to run"). */
    @Seed.Item(key = Infinitive.KEY)
    public static final class Infinitive {
        public static final String KEY = "cg.feat:infinitive";
        private Infinitive() {}
    }

    // ==================================================================================
    // Mood
    // ==================================================================================

    /** Imperative mood ("Run!"). */
    @Seed.Item(key = Imperative.KEY)
    public static final class Imperative {
        public static final String KEY = "cg.feat:imperative";
        private Imperative() {}
    }

    /** Indicative mood (statements of fact). */
    @Seed.Item(key = Indicative.KEY)
    public static final class Indicative {
        public static final String KEY = "cg.feat:indicative";
        private Indicative() {}
    }

    /** Subjunctive mood (counterfactuals, hypotheticals). */
    @Seed.Item(key = Subjunctive.KEY)
    public static final class Subjunctive {
        public static final String KEY = "cg.feat:subjunctive";
        private Subjunctive() {}
    }

    // ==================================================================================
    // Comparative / superlative (for adjectives & adverbs)
    // ==================================================================================

    /** Comparative form ("bigger", "more carefully"). */
    @Seed.Item(key = Comparative.KEY)
    public static final class Comparative {
        public static final String KEY = "cg.feat:comparative";
        private Comparative() {}
    }

    /** Superlative form ("biggest", "most carefully"). */
    @Seed.Item(key = Superlative.KEY)
    public static final class Superlative {
        public static final String KEY = "cg.feat:superlative";
        private Superlative() {}
    }

    // ==================================================================================
    // Voice
    // ==================================================================================

    /** Passive voice. */
    @Seed.Item(key = Passive.KEY)
    public static final class Passive {
        public static final String KEY = "cg.feat:passive";
        private Passive() {}
    }
}
