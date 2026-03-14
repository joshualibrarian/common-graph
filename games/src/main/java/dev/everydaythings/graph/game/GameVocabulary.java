package dev.everydaythings.graph.game;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
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
    // Board and spatial game verbs
    // ==================================================================================

    public static class Move {
        public static final String KEY = "cg.verb:move";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "cause to move or shift into a new position or place").cili("i30960")
                .word(LEMMA, "en", "move").word(LEMMA, "en", "play").word(LEMMA, "en", "go")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Resign {
        public static final String KEY = "cg.verb:resign";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "leave voluntarily; give up a position or contest").cili("i33602")
                .word(LEMMA, "en", "resign").word(LEMMA, "en", "concede").word(LEMMA, "en", "surrender");
    }

    public static class Offer {
        public static final String KEY = "cg.verb:offer";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "present for acceptance or rejection").cili("i33216")
                .word(LEMMA, "en", "offer").word(LEMMA, "en", "propose")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Accept {
        public static final String KEY = "cg.verb:accept";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "receive willingly something given or offered").cili("i32922")
                .word(LEMMA, "en", "accept").word(LEMMA, "en", "agree")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Decline {
        public static final String KEY = "cg.verb:decline";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "refuse to accept").cili("i32930")
                .word(LEMMA, "en", "decline").word(LEMMA, "en", "refuse").word(LEMMA, "en", "reject")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Select {
        public static final String KEY = "cg.verb:select";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "choose or pick something from available options").cili("i30780")
                .word(LEMMA, "en", "select").word(LEMMA, "en", "pick").word(LEMMA, "en", "choose")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Place {
        public static final String KEY = "cg.verb:place";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "put something at a specific position or location").cili("i33107")
                .word(LEMMA, "en", "place").word(LEMMA, "en", "drop").word(LEMMA, "en", "put-down")
                .slot(ThematicRole.Goal.KEY);
    }

    // ==================================================================================
    // Multiplayer verbs
    // ==================================================================================

    public static class Join {
        public static final String KEY = "cg.verb:join";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "become a participant in").cili("i28912")
                .word(LEMMA, "en", "join").word(LEMMA, "en", "sit")
                .slot(ThematicRole.Goal.KEY);
    }

    public static class Leave {
        public static final String KEY = "cg.verb:leave";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "go away from a place or group").cili("i30118")
                .word(LEMMA, "en", "leave").word(LEMMA, "en", "stand");
    }

    // ==================================================================================
    // Dice game verbs
    // ==================================================================================

    public static class Roll {
        public static final String KEY = "cg.verb:roll";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "cast or throw dice to generate a random outcome").cili("i32050")
                .word(LEMMA, "en", "roll").word(LEMMA, "en", "throw").word(LEMMA, "en", "toss");
    }

    public static class Keep {
        public static final String KEY = "cg.verb:keep";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "retain selected items while discarding or re-rolling others").cili("i33367")
                .word(LEMMA, "en", "keep").word(LEMMA, "en", "hold").word(LEMMA, "en", "retain")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Score {
        public static final String KEY = "cg.verb:score";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "assign a result to a scoring category or tally points").cili("i35455")
                .word(LEMMA, "en", "score")
                .slot(ThematicRole.Goal.KEY);
    }

    // ==================================================================================
    // Card/betting game verbs
    // ==================================================================================

    public static class Bet {
        public static final String KEY = "cg.verb:bet";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "stake money or chips on an uncertain outcome").cili("i33273")
                .word(LEMMA, "en", "bet").word(LEMMA, "en", "wager")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Raise {
        public static final String KEY = "cg.verb:raise";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "increase the current wager in a betting round").cili("i27093")
                .word(LEMMA, "en", "raise")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Fold {
        public static final String KEY = "cg.verb:fold";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "withdraw from the current round, forfeiting any stake").cili("i32929")
                .word(LEMMA, "en", "fold").word(LEMMA, "en", "muck");
    }

    public static class Check {
        public static final String KEY = "cg.verb:check";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "decline to bet while remaining in the hand").cili("i32931")
                .word(LEMMA, "en", "check").word(LEMMA, "en", "knock").word(LEMMA, "en", "tap");
    }

    public static class Deal {
        public static final String KEY = "cg.verb:deal";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "distribute cards, tiles, or other game pieces to players").cili("i33154")
                .word(LEMMA, "en", "deal").word(LEMMA, "en", "distribute")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Bid {
        public static final String KEY = "cg.verb:bid";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "declare how many tricks or points one expects to win").cili("i33212")
                .word(LEMMA, "en", "bid")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Play {
        public static final String KEY = "cg.verb:play";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "play a card, tile, or piece from one's hand").cili("i29858")
                .word(LEMMA, "en", "play")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Pass {
        public static final String KEY = "cg.verb:pass";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "skip one's turn or decline to act").cili("i33604")
                .word(LEMMA, "en", "pass").word(LEMMA, "en", "skip");
    }

    public static class Draw {
        public static final String KEY = "cg.verb:draw";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "take a card, tile, or piece from a shared supply").cili("i33329")
                .word(LEMMA, "en", "draw").word(LEMMA, "en", "pick")
                .slot(ThematicRole.Theme.KEY);
    }

    public static class Call {
        public static final String KEY = "cg.verb:call";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "match the current bet or declare a valid combination").cili("i33271")
                .word(LEMMA, "en", "call")
                .slot(ThematicRole.Theme.KEY);
    }

    // ==================================================================================
    // Minesweeper verbs
    // ==================================================================================

    public static class Reveal {
        public static final String KEY = "cg.verb:reveal";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "uncover a hidden cell revealing its state").cili("i32454")
                .word(LEMMA, "en", "reveal").word(LEMMA, "en", "uncover")
                .slot(ThematicRole.Goal.KEY);
    }

    public static class Flag {
        public static final String KEY = "cg.verb:flag";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "mark a position or item as suspicious or noteworthy").cili("i32186")
                .word(LEMMA, "en", "flag").word(LEMMA, "en", "mark")
                .slot(ThematicRole.Goal.KEY);
    }

    public static class Chord {
        public static final String KEY = "cg.verb:chord";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss("en", "simultaneously reveal all unflagged neighbors of a satisfied cell").cili("i33515")
                .word(LEMMA, "en", "chord")
                .slot(ThematicRole.Goal.KEY);
    }

}
