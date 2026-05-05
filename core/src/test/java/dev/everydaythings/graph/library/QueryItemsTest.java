package dev.everydaythings.graph.library;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QueryItemsTest {

    // Sememes / types
    private static final ItemID CHESS = ItemID.fromString("cg.sememe:chess");
    private static final ItemID PLAYER = ItemID.fromString("cg.role:player");
    private static final ItemID IMPLEMENTED_BY = CoreVocabulary.ImplementedBy.IID;

    // People
    private static final ItemID ALICE = ItemID.random();
    private static final ItemID BOB = ItemID.random();

    // Game instances
    private static final ItemID GAME_1 = ItemID.random();
    private static final ItemID GAME_2 = ItemID.random();
    private static final ItemID GAME_3 = ItemID.random();

    @Test
    void singleTerm_findsRelatedItems() {
        LibraryOld lib = LibraryOld.memory();

        // GAME_1 is a chess game (TYPE frame: home=GAME_1, binding=CHESS)
        storeFrame(lib, IMPLEMENTED_BY, GAME_1, CHESS);
        // GAME_2 is also a chess game
        storeFrame(lib, IMPLEMENTED_BY, GAME_2, CHESS);

        Set<ItemID> results = lib.queryItems(CHESS);

        assertThat(results).containsExactlyInAnyOrder(GAME_1, GAME_2);
        lib.close();
    }

    @Test
    void twoTerms_intersects() {
        LibraryOld lib = LibraryOld.memory();

        // GAME_1: chess, players Alice and Bob
        storeFrame(lib, IMPLEMENTED_BY, GAME_1, CHESS);
        storeFrame(lib, PLAYER, GAME_1, ALICE);
        storeFrame(lib, PLAYER, GAME_1, BOB);

        // GAME_2: chess, player Alice (no Bob)
        storeFrame(lib, IMPLEMENTED_BY, GAME_2, CHESS);
        storeFrame(lib, PLAYER, GAME_2, ALICE);

        // GAME_3: chess, player Bob (no Alice)
        storeFrame(lib, IMPLEMENTED_BY, GAME_3, CHESS);
        storeFrame(lib, PLAYER, GAME_3, BOB);

        // "chess alice" → games that are chess AND involve Alice
        Set<ItemID> chessAlice = lib.queryItems(CHESS, ALICE);
        assertThat(chessAlice).containsExactlyInAnyOrder(GAME_1, GAME_2);

        // "chess bob" → games that are chess AND involve Bob
        Set<ItemID> chessBob = lib.queryItems(CHESS, BOB);
        assertThat(chessBob).containsExactlyInAnyOrder(GAME_1, GAME_3);

        // "alice bob" → games involving both Alice and Bob
        Set<ItemID> aliceBob = lib.queryItems(ALICE, BOB);
        assertThat(aliceBob).containsExactlyInAnyOrder(GAME_1);

        // "chess alice bob" → the one game with all three
        Set<ItemID> all = lib.queryItems(CHESS, ALICE, BOB);
        assertThat(all).containsExactlyInAnyOrder(GAME_1);

        lib.close();
    }

    @Test
    void noMatches_returnsEmpty() {
        LibraryOld lib = LibraryOld.memory();

        storeFrame(lib, IMPLEMENTED_BY, GAME_1, CHESS);
        storeFrame(lib, PLAYER, GAME_1, ALICE);

        // Bob isn't in any chess game
        Set<ItemID> results = lib.queryItems(CHESS, BOB);
        assertThat(results).isEmpty();

        lib.close();
    }

    @Test
    void patternItemsExcludedFromResults() {
        LibraryOld lib = LibraryOld.memory();

        storeFrame(lib, IMPLEMENTED_BY, GAME_1, CHESS);

        // Querying for CHESS should find GAME_1, not CHESS itself
        Set<ItemID> results = lib.queryItems(CHESS);
        assertThat(results).doesNotContain(CHESS);
        assertThat(results).contains(GAME_1);

        lib.close();
    }

    @Test
    void emptyPattern_returnsEmpty() {
        LibraryOld lib = LibraryOld.memory();
        assertThat(lib.queryItems(Set.of())).isEmpty();
        lib.close();
    }

    /**
     * Store a simple frame: predicate, home item (THEME), and one binding target.
     */
    private void storeFrame(LibraryOld lib, ItemID predicate, ItemID home, ItemID relatedItem) {
        FrameBodyOld body = new FrameBodyOld(predicate, List.of(
                FrameBodyOld.homeBinding(home),
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(relatedItem), true, true)
        ));
        lib.storeFrameBody(body);
    }
}
