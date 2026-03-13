package dev.everydaythings.graph.game;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.frame.Dag;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class GameComponentTest {

    /**
     * Minimal concrete GameComponent for testing the base class behavior.
     */
    @Type(value = "cg:type/test-game", glyph = "🎲")
    static class TestGame extends GameComponent<TestGame.Op> {

        sealed interface Op permits MoveOp {}
        record MoveOp(String value) implements Op {}

        private int turn = 0;
        private boolean gameOver = false;
        private Integer winnerSeat = null;

        @Override public int minPlayers() { return 2; }
        @Override public int maxPlayers() { return 4; }

        @Override
        public Set<Integer> activePlayers() {
            if (gameOver) return Set.of();
            return Set.of(turn % seatedCount());
        }

        @Override public boolean isGameOver() { return gameOver; }
        @Override public Optional<Integer> winner() { return Optional.ofNullable(winnerSeat); }

        void advanceTurn() { turn++; }
        void endGame(int winner) { gameOver = true; winnerSeat = winner; }
        void endDraw() { gameOver = true; }

        @Override protected CBORObject encodeOp(Op op) {
            return switch (op) {
                case MoveOp m -> CBORObject.FromString(m.value());
            };
        }

        @Override protected Op decodeOp(CBORObject cbor) {
            return new MoveOp(cbor.AsString());
        }

        @Override protected void fold(Op op, Dag.Event ev) {
            // no-op for testing
        }
    }

    static ItemID pid(String name) {
        return ItemID.fromString("test:player/" + name);
    }

    // ==================================================================================
    // Player Range
    // ==================================================================================

    @Test
    void minMaxPlayers_reportCorrectRange() {
        TestGame game = new TestGame();
        assertThat(game.minPlayers()).isEqualTo(2);
        assertThat(game.maxPlayers()).isEqualTo(4);
    }

    // ==================================================================================
    // Player Management
    // ==================================================================================

    @Test
    void joinAs_assignsSpecificSeat() {
        TestGame game = new TestGame();
        game.joinAs(0, pid("alice"));
        game.joinAs(2, pid("charlie"));

        assertThat(game.playerAt(0)).hasValue(pid("alice"));
        assertThat(game.playerAt(1)).isEmpty();
        assertThat(game.playerAt(2)).hasValue(pid("charlie"));
    }

    @Test
    void joinAs_rejectsDuplicateSeat() {
        TestGame game = new TestGame();
        game.joinAs(0, pid("alice"));

        assertThatIllegalStateException()
                .isThrownBy(() -> game.joinAs(0, pid("bob")))
                .withMessageContaining("already taken");
    }

    @Test
    void joinAs_rejectsOutOfRange() {
        TestGame game = new TestGame();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> game.joinAs(4, pid("alice"))) // max is 4, so seat 4 is out
                .withMessageContaining("Invalid seat");
    }

    @Test
    void join_assignsFirstAvailableSeat() {
        TestGame game = new TestGame();
        assertThat(game.join(pid("alice"))).hasValue(0);
        assertThat(game.join(pid("bob"))).hasValue(1);
        assertThat(game.join(pid("charlie"))).hasValue(2);
    }

    @Test
    void join_returnsEmptyWhenFull() {
        TestGame game = new TestGame();
        game.join(pid("a"));
        game.join(pid("b"));
        game.join(pid("c"));
        game.join(pid("d"));

        assertThat(game.join(pid("e"))).isEmpty();
    }

    @Test
    void seatOf_findsPlayer() {
        TestGame game = new TestGame();
        game.joinAs(1, pid("bob"));

        assertThat(game.seatOf(pid("bob"))).hasValue(1);
        assertThat(game.seatOf(pid("alice"))).isEmpty();
    }

    @Test
    void seatedCount_countsNonNullSeats() {
        TestGame game = new TestGame();
        assertThat(game.seatedCount()).isEqualTo(0);

        game.joinAs(0, pid("alice"));
        assertThat(game.seatedCount()).isEqualTo(1);

        game.joinAs(3, pid("diane"));
        assertThat(game.seatedCount()).isEqualTo(2);
    }

    @Test
    void isFull_usesMaxPlayers() {
        TestGame game = new TestGame();
        game.join(pid("a"));
        game.join(pid("b"));
        assertThat(game.isFull()).isFalse(); // max is 4

        game.join(pid("c"));
        game.join(pid("d"));
        assertThat(game.isFull()).isTrue();
    }

    @Test
    void isReadyToStart_usesMinPlayers() {
        TestGame game = new TestGame();
        game.join(pid("a"));
        assertThat(game.isReadyToStart()).isFalse(); // min is 2

        game.join(pid("b"));
        assertThat(game.isReadyToStart()).isTrue(); // min reached, no moves
    }

    // ==================================================================================
    // Turn Control
    // ==================================================================================

    @Test
    void activePlayers_returnsSingletonForTurnBased() {
        TestGame game = new TestGame();
        game.join(pid("alice"));
        game.join(pid("bob"));

        assertThat(game.activePlayers()).isEqualTo(Set.of(0));

        game.advanceTurn();
        assertThat(game.activePlayers()).isEqualTo(Set.of(1));
    }

    @Test
    void activePlayers_emptyWhenGameOver() {
        TestGame game = new TestGame();
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.endGame(0);

        assertThat(game.activePlayers()).isEmpty();
    }

    @Test
    void canAct_bySeat() {
        TestGame game = new TestGame();
        game.join(pid("alice"));
        game.join(pid("bob"));

        assertThat(game.canAct(0)).isTrue();
        assertThat(game.canAct(1)).isFalse();
    }

    @Test
    void canAct_byPlayerId() {
        TestGame game = new TestGame();
        game.join(pid("alice"));
        game.join(pid("bob"));

        assertThat(game.canAct(pid("alice"))).isTrue();
        assertThat(game.canAct(pid("bob"))).isFalse();
        assertThat(game.canAct(pid("charlie"))).isFalse(); // not in game
    }

    // ==================================================================================
    // Game Lifecycle
    // ==================================================================================

    @Test
    void isDraw_trueWhenGameOverWithNoWinner() {
        TestGame game = new TestGame();
        game.endDraw();
        assertThat(game.isDraw()).isTrue();
    }

    @Test
    void isDraw_falseWhenWinnerExists() {
        TestGame game = new TestGame();
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.endGame(0);
        assertThat(game.isDraw()).isFalse();
    }

    @Test
    void isDraw_falseWhenInProgress() {
        TestGame game = new TestGame();
        assertThat(game.isDraw()).isFalse();
    }

    @Test
    void players_returnsDefensiveCopy() {
        TestGame game = new TestGame();
        game.join(pid("alice"));
        var players = game.players();
        assertThat(players).hasSize(1);
        assertThatThrownBy(() -> players.add(pid("bob")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================================================================================
    // Leave (data method)
    // ==================================================================================

    @Test
    void leave_removesPlayer() {
        TestGame game = new TestGame();
        game.joinAs(0, pid("alice"));
        game.joinAs(1, pid("bob"));

        Optional<Integer> vacated = game.leave(pid("alice"));
        assertThat(vacated).hasValue(0);
        assertThat(game.playerAt(0)).isEmpty();
        assertThat(game.playerAt(1)).hasValue(pid("bob"));
        assertThat(game.seatedCount()).isEqualTo(1);
    }

    @Test
    void leave_returnsEmptyForAbsentPlayer() {
        TestGame game = new TestGame();
        assertThat(game.leave(pid("alice"))).isEmpty();
    }

    // ==================================================================================
    // Join/Leave Verb Tests
    // ==================================================================================

    static ActionContext ctxFor(ItemID caller) {
        return ActionContext.of(caller, null, null, null);
    }

    @Test
    void joinVerb_assignsFirstAvailable() {
        TestGame game = new TestGame();
        String result = game.joinVerb(ctxFor(pid("alice")), null);
        assertThat(result).isEqualTo("Joined at seat 0");
        assertThat(game.seatOf(pid("alice"))).hasValue(0);
    }

    @Test
    void joinVerb_assignsSpecificSeat() {
        TestGame game = new TestGame();
        String result = game.joinVerb(ctxFor(pid("alice")), 2);
        assertThat(result).isEqualTo("Joined at seat 2");
        assertThat(game.seatOf(pid("alice"))).hasValue(2);
    }

    @Test
    void joinVerb_rejectsAlreadySeated() {
        TestGame game = new TestGame();
        game.setMode(GameMode.AUTHENTICATED);
        game.joinVerb(ctxFor(pid("alice")), null);

        assertThatIllegalStateException()
                .isThrownBy(() -> game.joinVerb(ctxFor(pid("alice")), null))
                .withMessageContaining("Already in the game");
    }

    @Test
    void leaveVerb_removesPlayer() {
        TestGame game = new TestGame();
        game.joinVerb(ctxFor(pid("alice")), null);

        String result = game.leaveVerb(ctxFor(pid("alice")));
        assertThat(result).isEqualTo("Left seat 0");
        assertThat(game.seatOf(pid("alice"))).isEmpty();
    }

    @Test
    void leaveVerb_notInGame() {
        TestGame game = new TestGame();

        assertThatIllegalStateException()
                .isThrownBy(() -> game.leaveVerb(ctxFor(pid("alice"))))
                .withMessageContaining("Not in the game");
    }
}
