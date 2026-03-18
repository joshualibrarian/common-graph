package dev.everydaythings.graph.game;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Seed vocabulary for game verbs.
 *
 * <p>Contains all Sememe seeds for game-specific actions: moves, draws,
 * betting, dice, cards, scoring, and multiplayer join/leave. These are
 * discovered by {@link dev.everydaythings.graph.library.SeedVocabulary}
 * via classpath scanning of {@code @Seed} fields.
 *
 * <p>Core system verbs (create, get, list, edit, etc.) remain in
 * {@link dev.everydaythings.graph.language.Sememe}.
 */
public final class GameVocabulary {

    private GameVocabulary() {}

    /** Convenience alias for lemma form declarations. */
    private static final Sememe LEMMA = GrammaticalFeature.Lemma.SEED;

    // ==================================================================================
    // Game predicates (nouns — used in EXPECTS frames and as frame predicates)
    // ==================================================================================

    @ItemSeed(key = Player.KEY, slots = {ThematicRole.Agent.KEY, ThematicRole.Theme.KEY})
    public static class Player {
        public static final String KEY = "cg.game:player";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a participant in a game, assigned to a side or seat")
                .word(PartOfSpeech.NOUN, LEMMA, "en", "player")
                .slot(ThematicRole.Agent.KEY).slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a participant in a game, assigned to a side or seat";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "player";
    }

    // ==================================================================================
    // Board and spatial game verbs
    // ==================================================================================

    @ItemSeed(key = Move.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Move {
        public static final String KEY = "cg.verb:move";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "cause to move or shift into a new position or place").cili("i30960")
                .word(PartOfSpeech.VERB, LEMMA, "en", "move").word(PartOfSpeech.VERB, LEMMA, "en", "play").word(PartOfSpeech.VERB, LEMMA, "en", "go")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "cause to move or shift into a new position or place";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i30960";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "move";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "play";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "go";
    }

    @ItemSeed(key = Resign.KEY)
    public static class Resign {
        public static final String KEY = "cg.verb:resign";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "leave voluntarily; give up a position or contest").cili("i33602")
                .word(PartOfSpeech.VERB, LEMMA, "en", "resign").word(PartOfSpeech.VERB, LEMMA, "en", "concede").word(PartOfSpeech.VERB, LEMMA, "en", "surrender");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "leave voluntarily; give up a position or contest";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33602";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "resign";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "concede";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "surrender";
    }

    @ItemSeed(key = Offer.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Offer {
        public static final String KEY = "cg.verb:offer";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "present for acceptance or rejection").cili("i33216")
                .word(PartOfSpeech.VERB, LEMMA, "en", "offer").word(PartOfSpeech.VERB, LEMMA, "en", "propose")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "present for acceptance or rejection";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33216";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "offer";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "propose";
    }

    @ItemSeed(key = Accept.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Accept {
        public static final String KEY = "cg.verb:accept";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "receive willingly something given or offered").cili("i32922")
                .word(PartOfSpeech.VERB, LEMMA, "en", "accept").word(PartOfSpeech.VERB, LEMMA, "en", "agree")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "receive willingly something given or offered";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32922";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "accept";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "agree";
    }

    @ItemSeed(key = Decline.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Decline {
        public static final String KEY = "cg.verb:decline";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "refuse to accept").cili("i32930")
                .word(PartOfSpeech.VERB, LEMMA, "en", "decline").word(PartOfSpeech.VERB, LEMMA, "en", "refuse").word(PartOfSpeech.VERB, LEMMA, "en", "reject")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "refuse to accept";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32930";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "decline";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "refuse";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "reject";
    }

    @ItemSeed(key = Select.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Select {
        public static final String KEY = "cg.verb:select";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "choose or pick something from available options").cili("i30780")
                .word(PartOfSpeech.VERB, LEMMA, "en", "select").word(PartOfSpeech.VERB, LEMMA, "en", "pick").word(PartOfSpeech.VERB, LEMMA, "en", "choose")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "choose or pick something from available options";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i30780";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "select";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "pick";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "choose";
    }

    @ItemSeed(key = Place.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Place {
        public static final String KEY = "cg.verb:place";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "put something at a specific position or location").cili("i33107")
                .word(PartOfSpeech.VERB, LEMMA, "en", "place").word(PartOfSpeech.VERB, LEMMA, "en", "drop").word(PartOfSpeech.VERB, LEMMA, "en", "put-down")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "put something at a specific position or location";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33107";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "place";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "drop";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "put-down";
    }

    // ==================================================================================
    // Multiplayer verbs
    // ==================================================================================

    @ItemSeed(key = Join.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Join {
        public static final String KEY = "cg.verb:join";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "become a participant in").cili("i28912")
                .word(PartOfSpeech.VERB, LEMMA, "en", "join").word(PartOfSpeech.VERB, LEMMA, "en", "sit")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "become a participant in";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i28912";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "join";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "sit";
    }

    @ItemSeed(key = Leave.KEY)
    public static class Leave {
        public static final String KEY = "cg.verb:leave";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "go away from a place or group").cili("i30118")
                .word(PartOfSpeech.VERB, LEMMA, "en", "leave").word(PartOfSpeech.VERB, LEMMA, "en", "stand");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "go away from a place or group";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i30118";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "leave";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "stand";
    }

    // ==================================================================================
    // Dice game verbs
    // ==================================================================================

    @ItemSeed(key = Roll.KEY)
    public static class Roll {
        public static final String KEY = "cg.verb:roll";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "cast or throw dice to generate a random outcome").cili("i32050")
                .word(PartOfSpeech.VERB, LEMMA, "en", "roll").word(PartOfSpeech.VERB, LEMMA, "en", "throw").word(PartOfSpeech.VERB, LEMMA, "en", "toss");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "cast or throw dice to generate a random outcome";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32050";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "roll";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "throw";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "toss";
    }

    @ItemSeed(key = Keep.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Keep {
        public static final String KEY = "cg.verb:keep";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "retain selected items while discarding or re-rolling others").cili("i33367")
                .word(PartOfSpeech.VERB, LEMMA, "en", "keep").word(PartOfSpeech.VERB, LEMMA, "en", "hold").word(PartOfSpeech.VERB, LEMMA, "en", "retain")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "retain selected items while discarding or re-rolling others";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33367";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "keep";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "hold";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "retain";
    }

    @ItemSeed(key = Score.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Score {
        public static final String KEY = "cg.verb:score";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "assign a result to a scoring category or tally points").cili("i35455")
                .word(PartOfSpeech.VERB, LEMMA, "en", "score")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "assign a result to a scoring category or tally points";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i35455";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "score";
    }

    // ==================================================================================
    // Card/betting game verbs
    // ==================================================================================

    @ItemSeed(key = Bet.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Bet {
        public static final String KEY = "cg.verb:bet";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "stake money or chips on an uncertain outcome").cili("i33273")
                .word(PartOfSpeech.VERB, LEMMA, "en", "bet").word(PartOfSpeech.VERB, LEMMA, "en", "wager")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "stake money or chips on an uncertain outcome";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33273";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "bet";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "wager";
    }

    @ItemSeed(key = Raise.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Raise {
        public static final String KEY = "cg.verb:raise";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "increase the current wager in a betting round").cili("i27093")
                .word(PartOfSpeech.VERB, LEMMA, "en", "raise")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "increase the current wager in a betting round";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i27093";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "raise";
    }

    @ItemSeed(key = Fold.KEY)
    public static class Fold {
        public static final String KEY = "cg.verb:fold";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "withdraw from the current round, forfeiting any stake").cili("i32929")
                .word(PartOfSpeech.VERB, LEMMA, "en", "fold").word(PartOfSpeech.VERB, LEMMA, "en", "muck");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "withdraw from the current round, forfeiting any stake";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32929";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "fold";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "muck";
    }

    @ItemSeed(key = Check.KEY)
    public static class Check {
        public static final String KEY = "cg.verb:check";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "decline to bet while remaining in the hand").cili("i32931")
                .word(PartOfSpeech.VERB, LEMMA, "en", "check").word(PartOfSpeech.VERB, LEMMA, "en", "knock").word(PartOfSpeech.VERB, LEMMA, "en", "tap");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "decline to bet while remaining in the hand";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32931";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "check";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "knock";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb3 = "tap";
    }

    @ItemSeed(key = Deal.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Deal {
        public static final String KEY = "cg.verb:deal";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "distribute cards, tiles, or other game pieces to players").cili("i33154")
                .word(PartOfSpeech.VERB, LEMMA, "en", "deal").word(PartOfSpeech.VERB, LEMMA, "en", "distribute")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "distribute cards, tiles, or other game pieces to players";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33154";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "deal";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "distribute";
    }

    @ItemSeed(key = Bid.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Bid {
        public static final String KEY = "cg.verb:bid";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "declare how many tricks or points one expects to win").cili("i33212")
                .word(PartOfSpeech.VERB, LEMMA, "en", "bid")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "declare how many tricks or points one expects to win";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33212";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "bid";
    }

    @ItemSeed(key = Play.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Play {
        public static final String KEY = "cg.verb:play";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "play a card, tile, or piece from one's hand").cili("i29858")
                .word(PartOfSpeech.VERB, LEMMA, "en", "play")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "play a card, tile, or piece from one's hand";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i29858";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "play";
    }

    @ItemSeed(key = Pass.KEY)
    public static class Pass {
        public static final String KEY = "cg.verb:pass";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "skip one's turn or decline to act").cili("i33604")
                .word(PartOfSpeech.VERB, LEMMA, "en", "pass").word(PartOfSpeech.VERB, LEMMA, "en", "skip");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "skip one's turn or decline to act";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33604";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "pass";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "skip";
    }

    @ItemSeed(key = Draw.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Draw {
        public static final String KEY = "cg.verb:draw";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "take a card, tile, or piece from a shared supply").cili("i33329")
                .word(PartOfSpeech.VERB, LEMMA, "en", "draw").word(PartOfSpeech.VERB, LEMMA, "en", "pick")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "take a card, tile, or piece from a shared supply";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33329";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "draw";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "pick";
    }

    @ItemSeed(key = Call.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Call {
        public static final String KEY = "cg.verb:call";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "match the current bet or declare a valid combination").cili("i33271")
                .word(PartOfSpeech.VERB, LEMMA, "en", "call")
                .slot(ThematicRole.Theme.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "match the current bet or declare a valid combination";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33271";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "call";
    }

    // ==================================================================================
    // Minesweeper verbs
    // ==================================================================================

    @ItemSeed(key = Reveal.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Reveal {
        public static final String KEY = "cg.verb:reveal";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "uncover a hidden cell revealing its state").cili("i32454")
                .word(PartOfSpeech.VERB, LEMMA, "en", "reveal").word(PartOfSpeech.VERB, LEMMA, "en", "uncover")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "uncover a hidden cell revealing its state";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32454";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "reveal";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "uncover";
    }

    @ItemSeed(key = Flag.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Flag {
        public static final String KEY = "cg.verb:flag";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "mark a position or item as suspicious or noteworthy").cili("i32186")
                .word(PartOfSpeech.VERB, LEMMA, "en", "flag").word(PartOfSpeech.VERB, LEMMA, "en", "mark")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "mark a position or item as suspicious or noteworthy";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i32186";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "flag";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb2 = "mark";
    }

    @ItemSeed(key = Chord.KEY, slots = {ThematicRole.Goal.KEY})
    public static class Chord {
        public static final String KEY = "cg.verb:chord";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "simultaneously reveal all unflagged neighbors of a satisfied cell").cili("i33515")
                .word(PartOfSpeech.VERB, LEMMA, "en", "chord")
                .slot(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "simultaneously reveal all unflagged neighbors of a satisfied cell";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i33515";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "chord";
    }

}
