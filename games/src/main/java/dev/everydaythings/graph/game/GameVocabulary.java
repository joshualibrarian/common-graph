package dev.everydaythings.graph.game;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
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


    // ==================================================================================
    // Game predicates (nouns — used in EXPECTS frames and as frame predicates)
    // ==================================================================================

    @ItemSeed(key = Player.KEY)
    public static class Player {
        public static final String KEY = "cg.game:player";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a participant in a game, assigned to a side or seat";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String noun1 = "player";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    // ==================================================================================
    // Board and spatial game verbs
    // ==================================================================================

    @ItemSeed(key = Move.KEY)
    public static class Move {
        public static final String KEY = "cg.verb:move";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "cause to move or shift into a new position or place";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i30960";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "move";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "play";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "go";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Resign.KEY)
    public static class Resign {
        public static final String KEY = "cg.verb:resign";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "leave voluntarily; give up a position or contest";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33602";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "resign";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "concede";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "surrender";
    }

    @ItemSeed(key = Offer.KEY)
    public static class Offer {
        public static final String KEY = "cg.verb:offer";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "present for acceptance or rejection";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33216";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "offer";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "propose";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Accept.KEY)
    public static class Accept {
        public static final String KEY = "cg.verb:accept";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "receive willingly something given or offered";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32922";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "accept";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "agree";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Decline.KEY)
    public static class Decline {
        public static final String KEY = "cg.verb:decline";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "refuse to accept";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32930";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "decline";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "refuse";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "reject";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Select.KEY)
    public static class Select {
        public static final String KEY = "cg.verb:select";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "choose or pick something from available options";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i30780";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "select";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "pick";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "choose";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Place.KEY)
    public static class Place {
        public static final String KEY = "cg.verb:place";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "put something at a specific position or location";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33107";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "place";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "drop";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "put-down";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    // ==================================================================================
    // Multiplayer verbs
    // ==================================================================================

    @ItemSeed(key = Join.KEY)
    public static class Join {
        public static final String KEY = "cg.verb:join";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "become a participant in";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i28912";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "join";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "sit";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Leave.KEY)
    public static class Leave {
        public static final String KEY = "cg.verb:leave";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "go away from a place or group";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i30118";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "leave";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "stand";
    }

    // ==================================================================================
    // Dice game verbs
    // ==================================================================================

    @ItemSeed(key = Roll.KEY)
    public static class Roll {
        public static final String KEY = "cg.verb:roll";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "cast or throw dice to generate a random outcome";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32050";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "roll";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "throw";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "toss";
    }

    @ItemSeed(key = Keep.KEY)
    public static class Keep {
        public static final String KEY = "cg.verb:keep";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "retain selected items while discarding or re-rolling others";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33367";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "keep";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "hold";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "retain";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Score.KEY)
    public static class Score {
        public static final String KEY = "cg.verb:score";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "assign a result to a scoring category or tally points";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i35455";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "score";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    // ==================================================================================
    // Card/betting game verbs
    // ==================================================================================

    @ItemSeed(key = Bet.KEY)
    public static class Bet {
        public static final String KEY = "cg.verb:bet";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "stake money or chips on an uncertain outcome";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33273";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "bet";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "wager";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Raise.KEY)
    public static class Raise {
        public static final String KEY = "cg.verb:raise";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "increase the current wager in a betting round";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i27093";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "raise";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Fold.KEY)
    public static class Fold {
        public static final String KEY = "cg.verb:fold";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "withdraw from the current round, forfeiting any stake";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32929";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "fold";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "muck";
    }

    @ItemSeed(key = Check.KEY)
    public static class Check {
        public static final String KEY = "cg.verb:check";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "decline to bet while remaining in the hand";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32931";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "check";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "knock";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb3 = "tap";
    }

    @ItemSeed(key = Deal.KEY)
    public static class Deal {
        public static final String KEY = "cg.verb:deal";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "distribute cards, tiles, or other game pieces to players";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33154";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "deal";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "distribute";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Bid.KEY)
    public static class Bid {
        public static final String KEY = "cg.verb:bid";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "declare how many tricks or points one expects to win";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33212";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "bid";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Play.KEY)
    public static class Play {
        public static final String KEY = "cg.verb:play";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "play a card, tile, or piece from one's hand";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i29858";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "play";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Pass.KEY)
    public static class Pass {
        public static final String KEY = "cg.verb:pass";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "skip one's turn or decline to act";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33604";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "pass";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "skip";
    }

    @ItemSeed(key = Draw.KEY)
    public static class Draw {
        public static final String KEY = "cg.verb:draw";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "take a card, tile, or piece from a shared supply";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33329";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "draw";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "pick";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Call.KEY)
    public static class Call {
        public static final String KEY = "cg.verb:call";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "match the current bet or declare a valid combination";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33271";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "call";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    // ==================================================================================
    // Minesweeper verbs
    // ==================================================================================

    @ItemSeed(key = Reveal.KEY)
    public static class Reveal {
        public static final String KEY = "cg.verb:reveal";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "uncover a hidden cell revealing its state";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32454";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "reveal";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "uncover";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Flag.KEY)
    public static class Flag {
        public static final String KEY = "cg.verb:flag";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "mark a position or item as suspicious or noteworthy";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32186";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "flag";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb2 = "mark";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Chord.KEY)
    public static class Chord {
        public static final String KEY = "cg.verb:chord";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "simultaneously reveal all unflagged neighbors of a satisfied cell";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33515";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String verb1 = "chord";

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

}
