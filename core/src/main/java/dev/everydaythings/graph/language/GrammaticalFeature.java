package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A grammatical feature sememe — describes an inflectional property of a word form.
 *
 * <p>Grammatical features (tense, number, person, degree, mood, etc.) are
 * language-agnostic concepts referenced by {@link ItemID}. Lexemes store
 * irregular forms keyed by sets of these features; Language subclasses
 * apply regular inflection rules based on feature sets.
 *
 * <p>Not all features are universal. Most languages distinguish singular/plural,
 * but some have DUAL (Arabic), PAUCAL (some Oceanic languages), or lack
 * number entirely. New features can be added as seed vocabulary without
 * changing any code.
 *
 * <p>Feature sets are minimal — you specify only the distinctive features.
 * For example, English past tense is just {PAST}, not {PAST, INDICATIVE, ACTIVE}.
 *
 * @see Lexeme
 * @see Language#inflect(Lexeme, java.util.Set)
 */
@Type(value = GrammaticalFeature.KEY, glyph = "\uD83D\uDD24", color = 0x70B0D0)
public class GrammaticalFeature extends NounSememe {

    public static final String KEY = "cg:type/grammatical-feature";

    // ==================================================================================
    // TENSE
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature PAST = new GrammaticalFeature(
            "cg.feat:past",
            Map.of("en", "past tense"),
            List.of("past")
    );

    @Seed
    public static final GrammaticalFeature PRESENT = new GrammaticalFeature(
            "cg.feat:present",
            Map.of("en", "present tense"),
            List.of("present")
    );

    @Seed
    public static final GrammaticalFeature FUTURE = new GrammaticalFeature(
            "cg.feat:future",
            Map.of("en", "future tense"),
            List.of("future")
    );

    // ==================================================================================
    // NUMBER
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature SINGULAR = new GrammaticalFeature(
            "cg.feat:singular",
            Map.of("en", "singular number"),
            List.of("singular")
    );

    @Seed
    public static final GrammaticalFeature PLURAL = new GrammaticalFeature(
            "cg.feat:plural",
            Map.of("en", "plural number"),
            List.of("plural")
    );

    // ==================================================================================
    // PERSON
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature FIRST_PERSON = new GrammaticalFeature(
            "cg.feat:first-person",
            Map.of("en", "first person"),
            List.of("first-person")
    );

    @Seed
    public static final GrammaticalFeature SECOND_PERSON = new GrammaticalFeature(
            "cg.feat:second-person",
            Map.of("en", "second person"),
            List.of("second-person")
    );

    @Seed
    public static final GrammaticalFeature THIRD_PERSON = new GrammaticalFeature(
            "cg.feat:third-person",
            Map.of("en", "third person"),
            List.of("third-person")
    );

    // ==================================================================================
    // FORM / ASPECT
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature PARTICIPLE = new GrammaticalFeature(
            "cg.feat:participle",
            Map.of("en", "participle form"),
            List.of("participle")
    );

    @Seed
    public static final GrammaticalFeature PROGRESSIVE = new GrammaticalFeature(
            "cg.feat:progressive",
            Map.of("en", "progressive aspect"),
            List.of("progressive")
    );

    @Seed
    public static final GrammaticalFeature PERFECT = new GrammaticalFeature(
            "cg.feat:perfect",
            Map.of("en", "perfect aspect"),
            List.of("perfect")
    );

    // ==================================================================================
    // MOOD
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature IMPERATIVE = new GrammaticalFeature(
            "cg.feat:imperative",
            Map.of("en", "imperative mood"),
            List.of("imperative")
    );

    @Seed
    public static final GrammaticalFeature SUBJUNCTIVE = new GrammaticalFeature(
            "cg.feat:subjunctive",
            Map.of("en", "subjunctive mood"),
            List.of("subjunctive")
    );

    @Seed
    public static final GrammaticalFeature INFINITIVE = new GrammaticalFeature(
            "cg.feat:infinitive",
            Map.of("en", "infinitive form"),
            List.of("infinitive")
    );

    // ==================================================================================
    // DEGREE (adjectives/adverbs)
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature COMPARATIVE = new GrammaticalFeature(
            "cg.feat:comparative",
            Map.of("en", "comparative degree"),
            List.of("comparative")
    );

    @Seed
    public static final GrammaticalFeature SUPERLATIVE = new GrammaticalFeature(
            "cg.feat:superlative",
            Map.of("en", "superlative degree"),
            List.of("superlative")
    );

    // ==================================================================================
    // VOICE
    // ==================================================================================

    @Seed
    public static final GrammaticalFeature PASSIVE = new GrammaticalFeature(
            "cg.feat:passive",
            Map.of("en", "passive voice"),
            List.of("passive")
    );

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected GrammaticalFeature(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected GrammaticalFeature(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Seed constructor. */
    public GrammaticalFeature(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, glosses, Map.of(), tokens);
    }
}
