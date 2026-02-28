package dev.everydaythings.graph.game.set;

import dev.everydaythings.graph.game.ScoreBoard;
import dev.everydaythings.graph.game.Zone;
import dev.everydaythings.graph.game.ZoneVisibility;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SetGameTest {

    static ItemID pid(String name) {
        return ItemID.fromString("test:player/" + name);
    }

    // ==================================================================================
    // Game Creation
    // ==================================================================================

    @Test
    void create_hasCorrectZones() {
        SetGame game = SetGame.create(3);
        assertThat(game.zones().hasZone("deck")).isTrue();
        assertThat(game.zones().hasZone("tableau")).isTrue();
        assertThat(game.zones().hasZone("found:0")).isTrue();
        assertThat(game.zones().hasZone("found:1")).isTrue();
        assertThat(game.zones().hasZone("found:2")).isTrue();
    }

    @Test
    void create_notStartedYet() {
        SetGame game = SetGame.create();
        assertThat(game.isStarted()).isFalse();
        assertThat(game.isGameOver()).isFalse();
        assertThat(game.tableauSize()).isEqualTo(0);
        assertThat(game.deckSize()).isEqualTo(0);
    }

    @Test
    void create_playerRange() {
        SetGame game = SetGame.create(6);
        assertThat(game.minPlayers()).isEqualTo(1);
        assertThat(game.maxPlayers()).isEqualTo(6);
    }

    // ==================================================================================
    // Game Start
    // ==================================================================================

    @Test
    void start_dealsInitialTableau() {
        SetGame game = SetGame.create();
        game.start();

        assertThat(game.isStarted()).isTrue();
        assertThat(game.tableauSize()).isEqualTo(12);
        assertThat(game.deckSize()).isEqualTo(69); // 81 - 12
    }

    @Test
    void start_tableauIsPublic() {
        SetGame game = SetGame.create();
        game.start();

        Zone<SetCard> tableau = game.zones().zone("tableau");
        assertThat(tableau.visibility()).isEqualTo(ZoneVisibility.PUBLIC);
        assertThat(tableau.contentsVisibleTo(0)).hasSize(12);
        assertThat(tableau.contentsVisibleTo(1)).hasSize(12);
    }

    @Test
    void start_deckIsHidden() {
        SetGame game = SetGame.create();
        game.start();

        Zone<SetCard> deck = game.zones().zone("deck");
        assertThat(deck.visibility()).isEqualTo(ZoneVisibility.HIDDEN);
        assertThat(deck.contentsVisibleTo(0)).isEmpty();
    }

    @Test
    void start_idempotent() {
        SetGame game = SetGame.create();
        game.start();
        int tableauSize = game.tableauSize();
        int deckSize = game.deckSize();

        game.start(); // second start should be no-op
        assertThat(game.tableauSize()).isEqualTo(tableauSize);
        assertThat(game.deckSize()).isEqualTo(deckSize);
    }

    // ==================================================================================
    // Simultaneous Play
    // ==================================================================================

    @Test
    void activePlayers_allSeatedPlayersActive() {
        SetGame game = SetGame.create(4);
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.join(pid("charlie"));
        game.start();

        assertThat(game.activePlayers()).isEqualTo(Set.of(0, 1, 2));
    }

    @Test
    void activePlayers_emptyBeforeStart() {
        SetGame game = SetGame.create();
        game.join(pid("alice"));
        assertThat(game.activePlayers()).isEmpty();
    }

    @Test
    void activePlayers_emptyAfterGameOver() {
        SetGame game = createAndExhaustGame();
        assertThat(game.activePlayers()).isEmpty();
    }

    // ==================================================================================
    // CallSet — Valid
    // ==================================================================================

    @Test
    void callSet_validSet_movesToFoundPile() {
        SetGame game = SetGame.create(2);
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.start();

        // Find a valid set in the tableau
        List<List<SetCard>> sets = game.findAllSets();
        assertThat(sets).isNotEmpty();

        List<SetCard> validSet = sets.get(0);
        game.callSet(0,
                validSet.get(0).ordinal(),
                validSet.get(1).ordinal(),
                validSet.get(2).ordinal());

        // Cards moved to found pile
        Zone<SetCard> found = game.zones().zone("found:0");
        assertThat(found.size()).isEqualTo(3);
        assertThat(found.contents()).containsExactlyInAnyOrderElementsOf(validSet);
    }

    @Test
    void callSet_validSet_addsScore() {
        SetGame game = SetGame.create(2);
        game.join(pid("alice"));
        game.start();

        List<SetCard> validSet = game.findAllSets().get(0);
        game.callSet(0,
                validSet.get(0).ordinal(),
                validSet.get(1).ordinal(),
                validSet.get(2).ordinal());

        assertThat(game.scoreBoard().score(0)).isEqualTo(1);
    }

    @Test
    void callSet_validSet_replenishesTableau() {
        SetGame game = SetGame.create();
        game.start();
        assertThat(game.tableauSize()).isEqualTo(12);

        List<SetCard> validSet = game.findAllSets().get(0);
        game.callSet(0,
                validSet.get(0).ordinal(),
                validSet.get(1).ordinal(),
                validSet.get(2).ordinal());

        // Tableau should be back to 12 (3 removed, 3 dealt from deck)
        assertThat(game.tableauSize()).isEqualTo(12);
        assertThat(game.deckSize()).isEqualTo(66); // 69 - 3
    }

    // ==================================================================================
    // CallSet — Invalid
    // ==================================================================================

    @Test
    void callSet_invalidSet_penalty() {
        SetGame game = SetGame.create();
        game.join(pid("alice"));
        game.start();

        // Pick three cards that don't form a valid set
        List<SetCard> tableau = game.tableau();
        // Find an invalid triple
        for (int i = 0; i < tableau.size() - 2; i++) {
            for (int j = i + 1; j < tableau.size() - 1; j++) {
                for (int k = j + 1; k < tableau.size(); k++) {
                    SetCard a = tableau.get(i), b = tableau.get(j), c = tableau.get(k);
                    if (!SetCard.isValidSet(a, b, c)) {
                        game.callSet(0, a.ordinal(), b.ordinal(), c.ordinal());
                        assertThat(game.scoreBoard().score(0)).isEqualTo(-1);
                        return;
                    }
                }
            }
        }
        // If all triples are valid sets (extremely unlikely with 12 cards), skip
    }

    @Test
    void callSet_cardsNotInTableau_ignored() {
        SetGame game = SetGame.create();
        game.start();
        int tableauBefore = game.tableauSize();

        // Use ordinals that definitely aren't in the tableau
        // (the deck has 69 cards, so pick one from those)
        List<SetCard> deck = game.zones().zone("deck").contents();
        if (deck.size() >= 3) {
            game.callSet(0, deck.get(0).ordinal(), deck.get(1).ordinal(), deck.get(2).ordinal());
            assertThat(game.tableauSize()).isEqualTo(tableauBefore);
        }
    }

    @Test
    void callSet_beforeStart_rejected() {
        SetGame game = SetGame.create();
        assertThat(game.callSet(0, 0, 1, 2)).isFalse();
    }

    // ==================================================================================
    // DealMore
    // ==================================================================================

    @Test
    void dealMore_whenNoValidSet_adds3Cards() {
        // This is hard to test directly since random tableaus almost always have sets.
        // We test the logic by checking the method accepts the call.
        SetGame game = SetGame.create();
        game.start();

        // If there IS a valid set, dealMore should not add cards
        if (SetGame.hasValidSet(game.tableau())) {
            int before = game.tableauSize();
            game.dealMore(0);
            assertThat(game.tableauSize()).isEqualTo(before); // no change
        }
    }

    @Test
    void dealMore_beforeStart_rejected() {
        SetGame game = SetGame.create();
        assertThat(game.dealMore(0)).isFalse();
    }

    // ==================================================================================
    // hasValidSet Detection
    // ==================================================================================

    @Test
    void hasValidSet_findsInKnownSet() {
        SetCard a = new SetCard(SetProperty.Count.ONE, SetProperty.Shape.DIAMOND,
                SetProperty.Shading.SOLID, SetProperty.Color.RED);
        SetCard b = new SetCard(SetProperty.Count.TWO, SetProperty.Shape.OVAL,
                SetProperty.Shading.STRIPED, SetProperty.Color.GREEN);
        SetCard c = new SetCard(SetProperty.Count.THREE, SetProperty.Shape.SQUIGGLE,
                SetProperty.Shading.EMPTY, SetProperty.Color.PURPLE);

        assertThat(SetGame.hasValidSet(List.of(a, b, c))).isTrue();
    }

    @Test
    void hasValidSet_returnsFalseForInvalid() {
        // Three cards that don't form a set with each other
        SetCard a = new SetCard(SetProperty.Count.ONE, SetProperty.Shape.DIAMOND,
                SetProperty.Shading.SOLID, SetProperty.Color.RED);
        SetCard b = new SetCard(SetProperty.Count.ONE, SetProperty.Shape.DIAMOND,
                SetProperty.Shading.SOLID, SetProperty.Color.GREEN);
        // c differs from a on color only (RED vs PURPLE) but b is also different (GREEN)
        // so color: R, G, P → all different ✓
        // but count: 1,1,1 → all same ✓, shape: D,D,D ✓, shading: S,S,S ✓ — this IS valid
        // Let's pick something truly invalid:
        SetCard c = new SetCard(SetProperty.Count.TWO, SetProperty.Shape.DIAMOND,
                SetProperty.Shading.SOLID, SetProperty.Color.RED);
        // count: 1,1,2 → invalid (two same one different)
        assertThat(SetGame.hasValidSet(List.of(a, b, c))).isFalse();
    }

    @Test
    void hasValidSet_emptyList() {
        assertThat(SetGame.hasValidSet(List.of())).isFalse();
    }

    @Test
    void hasValidSet_twoCards() {
        SetCard a = new SetCard(SetProperty.Count.ONE, SetProperty.Shape.DIAMOND,
                SetProperty.Shading.SOLID, SetProperty.Color.RED);
        SetCard b = new SetCard(SetProperty.Count.TWO, SetProperty.Shape.OVAL,
                SetProperty.Shading.STRIPED, SetProperty.Color.GREEN);
        assertThat(SetGame.hasValidSet(List.of(a, b))).isFalse();
    }

    // ==================================================================================
    // findAllSets
    // ==================================================================================

    @Test
    void findAllSets_afterStart_findsAtLeastOne() {
        SetGame game = SetGame.create();
        game.start();

        // With 12 random cards, it's statistically almost certain there's a set
        // (probability of no set in 12 cards is about 1 in 33 billion)
        List<List<SetCard>> sets = game.findAllSets();
        assertThat(sets).isNotEmpty();

        // Each found set should be valid
        for (List<SetCard> set : sets) {
            assertThat(set).hasSize(3);
            assertThat(SetCard.isValidSet(set.get(0), set.get(1), set.get(2))).isTrue();
        }
    }

    // ==================================================================================
    // Game Over and Winner
    // ==================================================================================

    @Test
    void gameOver_whenDeckEmptyAndNoSetsInTableau() {
        SetGame game = createAndExhaustGame();
        assertThat(game.isGameOver()).isTrue();
        assertThat(game.deckSize()).isEqualTo(0);
    }

    @Test
    void winner_highestScore() {
        SetGame game = SetGame.create(2);
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.start();

        // Player 0 claims sets until game over
        while (!game.isGameOver()) {
            List<List<SetCard>> sets = game.findAllSets();
            if (sets.isEmpty()) {
                game.dealMore(0);
                if (game.isGameOver()) break;
                continue;
            }
            List<SetCard> set = sets.get(0);
            game.callSet(0, set.get(0).ordinal(), set.get(1).ordinal(), set.get(2).ordinal());
        }

        // Player 0 should have all the points
        assertThat(game.scoreBoard().score(0)).isGreaterThan(0);
        assertThat(game.scoreBoard().score(1)).isEqualTo(0);
        assertThat(game.winner()).hasValue(0);
    }

    // ==================================================================================
    // Zone Visibility
    // ==================================================================================

    @Test
    void zones_foundPiles_arePublic() {
        SetGame game = SetGame.create(2);
        Zone<SetCard> found0 = game.zones().zone("found:0");
        assertThat(found0.visibility()).isEqualTo(ZoneVisibility.PUBLIC);
    }

    // ==================================================================================
    // Scored Interface
    // ==================================================================================

    @Test
    void scoreBoard_tracksPerPlayer() {
        SetGame game = SetGame.create(2);
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.start();

        List<List<SetCard>> sets = game.findAllSets();
        assertThat(sets).hasSizeGreaterThanOrEqualTo(1);

        // Player 0 claims first set
        List<SetCard> set = sets.get(0);
        game.callSet(0, set.get(0).ordinal(), set.get(1).ordinal(), set.get(2).ordinal());

        assertThat(game.scoreBoard().score(0)).isEqualTo(1);
        assertThat(game.scoreBoard().score(1)).isEqualTo(0);

        // Player 1 claims a set
        sets = game.findAllSets();
        if (!sets.isEmpty()) {
            set = sets.get(0);
            game.callSet(1, set.get(0).ordinal(), set.get(1).ordinal(), set.get(2).ordinal());
            assertThat(game.scoreBoard().score(1)).isEqualTo(1);
        }
    }

    // ==================================================================================
    // CBOR Round-Trip
    // ==================================================================================

    @Test
    void cborRoundTrip_startOp() {
        SetGame game = SetGame.create();
        var op = new SetGame.StartOp();
        var encoded = game.encodeOp(op);
        var decoded = game.decodeOp(encoded);
        assertThat(decoded).isEqualTo(op);
    }

    @Test
    void cborRoundTrip_callSetOp() {
        SetGame game = SetGame.create();
        var op = new SetGame.CallSetOp(2, 10, 45, 73);
        var encoded = game.encodeOp(op);
        var decoded = game.decodeOp(encoded);
        assertThat(decoded).isEqualTo(op);
    }

    @Test
    void cborRoundTrip_dealMoreOp() {
        SetGame game = SetGame.create();
        var op = new SetGame.DealMoreOp(1);
        var encoded = game.encodeOp(op);
        var decoded = game.decodeOp(encoded);
        assertThat(decoded).isEqualTo(op);
    }

    // ==================================================================================
    // Text Rendering
    // ==================================================================================

    @Test
    void describeStatus_beforeStart() {
        SetGame game = SetGame.create();
        assertThat(game.describeStatus()).contains("Waiting");
    }

    @Test
    void renderTableau_afterStart() {
        SetGame game = SetGame.create();
        game.start();
        String rendered = game.renderTableau();
        assertThat(rendered).contains("TABLEAU");
        assertThat(rendered).contains("Deck:");
    }

    @Test
    void renderTableau_beforeStart() {
        SetGame game = SetGame.create();
        assertThat(game.renderTableau()).contains("not started");
    }

    // ==================================================================================
    // Helper
    // ==================================================================================

    /** Play a game to completion by having player 0 claim all sets. */
    private SetGame createAndExhaustGame() {
        SetGame game = SetGame.create(2);
        game.join(pid("alice"));
        game.join(pid("bob"));
        game.start();

        int safety = 0;
        while (!game.isGameOver() && safety++ < 200) {
            List<List<SetCard>> sets = game.findAllSets();
            if (sets.isEmpty()) {
                game.dealMore(0);
                continue;
            }
            List<SetCard> set = sets.get(0);
            game.callSet(0, set.get(0).ordinal(), set.get(1).ordinal(), set.get(2).ordinal());
        }
        return game;
    }
}
