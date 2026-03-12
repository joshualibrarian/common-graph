package dev.everydaythings.graph.game;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.VerbSememe;

import java.util.List;
import java.util.Map;

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
    // Board and spatial game verbs
    // ==================================================================================

    public static final String MOVE = "cg.verb:move";

    @Seed
    static final VerbSememe move = new VerbSememe(
            MOVE,
            Map.of("en", "cause to move or shift into a new position or place"),
            Map.of("cili", "i30960"),
            List.of("move", "play", "go")
    ).slot(ThematicRole.Theme.KEY)
     .slot(ThematicRole.Target.KEY);

    public static final String RESIGN = "cg.verb:resign";

    @Seed
    static final VerbSememe resign = new VerbSememe(
            RESIGN,
            Map.of("en", "leave voluntarily; give up a position or contest"),
            Map.of("cili", "i33602"),
            List.of("resign", "concede", "surrender")
    );

    public static final String OFFER = "cg.verb:offer";

    @Seed
    static final VerbSememe offer = new VerbSememe(
            OFFER,
            Map.of("en", "present for acceptance or rejection"),
            Map.of("cili", "i33216"),
            List.of("offer", "propose")
    );

    public static final String ACCEPT = "cg.verb:accept";

    @Seed
    static final VerbSememe accept = new VerbSememe(
            ACCEPT,
            Map.of("en", "receive willingly something given or offered"),
            Map.of("cili", "i32922"),
            List.of("accept", "agree")
    );

    public static final String DECLINE = "cg.verb:decline";

    @Seed
    static final VerbSememe decline = new VerbSememe(
            DECLINE,
            Map.of("en", "refuse to accept"),
            Map.of("cili", "i32930"),
            List.of("decline", "refuse", "reject")
    );

    public static final String SELECT = "cg.verb:select";

    @Seed
    static final VerbSememe select = new VerbSememe(
            SELECT,
            Map.of("en", "choose or pick something from available options"),
            Map.of("cili", "i30780"),
            List.of("select", "pick", "choose")
    ).slot(ThematicRole.Theme.KEY);

    public static final String PLACE = "cg.verb:place";

    @Seed
    static final VerbSememe place = new VerbSememe(
            PLACE,
            Map.of("en", "put something at a specific position or location"),
            Map.of("cili", "i33107"),
            List.of("place", "drop", "put-down")
    ).slot(ThematicRole.Target.KEY);

    // ==================================================================================
    // Multiplayer verbs
    // ==================================================================================

    public static final String JOIN = "cg.verb:join";

    @Seed
    static final VerbSememe join = new VerbSememe(
            JOIN,
            Map.of("en", "become a participant in"),
            Map.of("cili", "i28912"),
            List.of("join", "sit")
    ).slot(ThematicRole.Target.KEY);

    public static final String LEAVE = "cg.verb:leave";

    @Seed
    static final VerbSememe leave = new VerbSememe(
            LEAVE,
            Map.of("en", "go away from a place or group"),
            Map.of("cili", "i30118"),
            List.of("leave", "stand")
    );

    // ==================================================================================
    // Dice game verbs
    // ==================================================================================

    public static final String ROLL = "cg.verb:roll";

    @Seed
    static final VerbSememe roll = new VerbSememe(
            ROLL,
            Map.of("en", "cast or throw dice to generate a random outcome"),
            Map.of("cili", "i32050"),
            List.of("roll", "throw", "toss")
    );

    public static final String KEEP = "cg.verb:keep";

    @Seed
    static final VerbSememe keep = new VerbSememe(
            KEEP,
            Map.of("en", "retain selected items while discarding or re-rolling others"),
            Map.of("cili", "i33367"),
            List.of("keep", "hold", "retain")
    ).slot(ThematicRole.Theme.KEY);

    public static final String SCORE = "cg.verb:score";

    @Seed
    static final VerbSememe score = new VerbSememe(
            SCORE,
            Map.of("en", "assign a result to a scoring category or tally points"),
            Map.of("cili", "i35455"),
            List.of("score")
    ).slot(ThematicRole.Target.KEY);

    // ==================================================================================
    // Card/betting game verbs
    // ==================================================================================

    public static final String BET = "cg.verb:bet";

    @Seed
    static final VerbSememe bet = new VerbSememe(
            BET,
            Map.of("en", "stake money or chips on an uncertain outcome"),
            Map.of("cili", "i33273"),
            List.of("bet", "wager")
    ).slot(ThematicRole.Theme.KEY);

    public static final String RAISE = "cg.verb:raise";

    @Seed
    static final VerbSememe raise = new VerbSememe(
            RAISE,
            Map.of("en", "increase the current wager in a betting round"),
            Map.of("cili", "i27093"),
            List.of("raise")
    ).slot(ThematicRole.Theme.KEY);

    public static final String FOLD = "cg.verb:fold";

    @Seed
    static final VerbSememe fold = new VerbSememe(
            FOLD,
            Map.of("en", "withdraw from the current round, forfeiting any stake"),
            Map.of("cili", "i32929"),
            List.of("fold", "muck")
    );

    public static final String CHECK = "cg.verb:check";

    @Seed
    static final VerbSememe check = new VerbSememe(
            CHECK,
            Map.of("en", "decline to bet while remaining in the hand"),
            Map.of("cili", "i32931"),
            List.of("check", "knock", "tap")
    );

    public static final String DEAL = "cg.verb:deal";

    @Seed
    static final VerbSememe deal = new VerbSememe(
            DEAL,
            Map.of("en", "distribute cards, tiles, or other game pieces to players"),
            Map.of("cili", "i33154"),
            List.of("deal", "distribute")
    );

    public static final String BID = "cg.verb:bid";

    @Seed
    static final VerbSememe bid = new VerbSememe(
            BID,
            Map.of("en", "declare how many tricks or points one expects to win"),
            Map.of("cili", "i33212"),
            List.of("bid")
    ).slot(ThematicRole.Theme.KEY);

    public static final String PLAY = "cg.verb:play";

    @Seed
    static final VerbSememe play = new VerbSememe(
            PLAY,
            Map.of("en", "play a card, tile, or piece from one's hand"),
            Map.of("cili", "i29858"),
            List.of("play")
    ).slot(ThematicRole.Theme.KEY);

    public static final String PASS = "cg.verb:pass";

    @Seed
    static final VerbSememe pass = new VerbSememe(
            PASS,
            Map.of("en", "skip one's turn or decline to act"),
            Map.of("cili", "i33604"),
            List.of("pass", "skip")
    );

    public static final String DRAW = "cg.verb:draw";

    @Seed
    static final VerbSememe draw = new VerbSememe(
            DRAW,
            Map.of("en", "take a card, tile, or piece from a shared supply"),
            Map.of("cili", "i33329"),
            List.of("draw", "pick")
    );

    public static final String CALL = "cg.verb:call";

    @Seed
    static final VerbSememe call = new VerbSememe(
            CALL,
            Map.of("en", "match the current bet or declare a valid combination"),
            Map.of("cili", "i33271"),
            List.of("call")
    );

    // ==================================================================================
    // Minesweeper verbs
    // ==================================================================================

    public static final String REVEAL = "cg.verb:reveal";

    @Seed
    static final VerbSememe reveal = new VerbSememe(
            REVEAL,
            Map.of("en", "uncover a hidden cell revealing its state"),
            Map.of("cili", "i32454"),
            List.of("reveal", "uncover")
    ).slot(ThematicRole.Target.KEY);

    public static final String FLAG = "cg.verb:flag";

    @Seed
    static final VerbSememe flag = new VerbSememe(
            FLAG,
            Map.of("en", "mark a position or item as suspicious or noteworthy"),
            Map.of("cili", "i32186"),
            List.of("flag", "mark")
    ).slot(ThematicRole.Target.KEY);

    public static final String CHORD = "cg.verb:chord";

    @Seed
    static final VerbSememe chord = new VerbSememe(
            CHORD,
            Map.of("en", "simultaneously reveal all unflagged neighbors of a satisfied cell"),
            Map.of("cili", "i33515"),
            List.of("chord")
    ).slot(ThematicRole.Target.KEY);
}
