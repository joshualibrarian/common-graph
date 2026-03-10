package dev.everydaythings.graph.game;

/**
 * Game modes controlling player identity and turn enforcement.
 *
 * <p>The three A's:
 *
 * <ul>
 *   <li><b>ARCHIVE</b> — Imported or historical game. Players are any Items
 *       (Person, etc.). Game is complete. View, step through, analyze, or
 *       fork into ANALYSIS mode.</li>
 *   <li><b>ANALYSIS</b> — Free exploration. Anyone can make moves. No turn
 *       enforcement, no signing required. Used for casual play, puzzles,
 *       study, and sandbox experimentation.</li>
 *   <li><b>AUTHENTICATED</b> — Live play between verified identities. Players
 *       must be authenticated Signers. Turn enforcement active, all moves
 *       signed by the acting player.</li>
 * </ul>
 *
 * <p>The enforcement is effectively binary: AUTHENTICATED enforces caller
 * identity on every action; ARCHIVE and ANALYSIS do not. The difference
 * between ARCHIVE and ANALYSIS is semantic — archive games are complete
 * (no new moves), while analysis games are open for exploration.
 */
public enum GameMode {

    /**
     * Historical or imported game. Players may be Person items, not Signers.
     * No new moves allowed — the game is complete. Fork to analyze.
     */
    ARCHIVE,

    /**
     * Free exploration. Anyone can make moves. No turn enforcement.
     * Default mode for casual play, puzzles, and study.
     */
    ANALYSIS,

    /**
     * Live authenticated play. Players must be Signers. Turn enforcement
     * active. All moves cryptographically signed.
     */
    AUTHENTICATED
}
