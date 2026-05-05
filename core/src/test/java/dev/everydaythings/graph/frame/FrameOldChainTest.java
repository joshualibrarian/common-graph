package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.LibrarianOld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrameChain")
class FrameOldChainTest {

    static final ItemID MOVE = ItemID.fromString("cg:pred/move");
    static final ItemID GAME = ItemID.fromString("cg:item/chess-game-1");
    static final ItemID WHITE = ItemID.fromString("cg:player/white");
    static final ItemID BLACK = ItemID.fromString("cg:player/black");
    static final ItemID KNIGHT = ItemID.fromString("cg:piece/knight");
    static final ItemID PAWN = ItemID.fromString("cg:piece/pawn");

    static LibrarianOld signer;
    static LibrarianOld signer2;

    @BeforeAll
    static void setup() {
        signer = LibrarianOld.createInMemory();
        signer2 = LibrarianOld.createInMemory();
    }

    @Nested
    @DisplayName("Empty chain")
    class EmptyChain {

        @Test
        @DisplayName("empty chain has no frames")
        void emptyStream() {
            FrameChain chain = new FrameChain(signer.library(), GAME, MOVE);
            assertThat(chain.stream()).isEmpty();
        }

        @Test
        @DisplayName("empty chain has no heads")
        void emptyHeads() {
            FrameChain chain = new FrameChain(signer.library(), GAME, MOVE);
            assertThat(chain.heads()).isEmpty();
        }

        @Test
        @DisplayName("empty chain isEmpty is true")
        void isEmpty() {
            FrameChain chain = new FrameChain(signer.library(), GAME, MOVE);
            assertThat(chain.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("empty chain count is zero")
        void countIsZero() {
            FrameChain chain = new FrameChain(signer.library(), GAME, MOVE);
            assertThat(chain.count()).isZero();
        }

        @Test
        @DisplayName("fold over empty chain returns initial state")
        void foldEmpty() {
            FrameChain chain = new FrameChain(signer.library(), GAME, MOVE);
            int result = chain.fold(0, (state, body) -> state + 1);
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("Linear chain")
    class LinearChain {

        @Test
        @DisplayName("append 3 events, verify stream order")
        void linearOrder() {
            ItemID gameId = ItemID.fromString("cg:item/linear-test");
            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            // Append 3 moves
            FrameBodyOld move1 = chain.append(List.of(
                    Binding.ref(ThematicRole.Agent.IID, WHITE),
                    Binding.ref(ThematicRole.Theme.IID, PAWN)
            ), signer);

            FrameBodyOld move2 = chain.append(List.of(
                    Binding.ref(ThematicRole.Agent.IID, BLACK),
                    Binding.ref(ThematicRole.Theme.IID, KNIGHT)
            ), signer);

            FrameBodyOld move3 = chain.append(List.of(
                    Binding.ref(ThematicRole.Agent.IID, WHITE),
                    Binding.ref(ThematicRole.Theme.IID, PAWN)
            ), signer);

            // Stream should contain all 3 in order
            List<FrameBodyOld> bodies = chain.stream().toList();
            assertThat(bodies).hasSize(3);

            // First frame has no FOLLOWS
            assertThat(bodies.get(0).getAllBindings(
                    ItemID.fromString(ThematicRole.Follows.KEY))).isEmpty();

            // Second frame FOLLOWS the first
            assertThat(bodies.get(1).getAllBindings(
                    ItemID.fromString(ThematicRole.Follows.KEY))).hasSize(1);

            // Third frame FOLLOWS the second
            assertThat(bodies.get(2).getAllBindings(
                    ItemID.fromString(ThematicRole.Follows.KEY))).hasSize(1);
        }

        @Test
        @DisplayName("single head after linear appends")
        void singleHead() {
            ItemID gameId = ItemID.fromString("cg:item/head-test");
            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            chain.append(List.of(
                    Binding.ref(ThematicRole.Theme.IID, PAWN)
            ), signer);

            chain.append(List.of(
                    Binding.ref(ThematicRole.Theme.IID, KNIGHT)
            ), signer);

            assertThat(chain.heads()).hasSize(1);
        }

        @Test
        @DisplayName("count matches number of appends")
        void countMatches() {
            ItemID gameId = ItemID.fromString("cg:item/count-test");
            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            chain.append(List.of(), signer);
            chain.append(List.of(), signer);
            chain.append(List.of(), signer);

            assertThat(chain.count()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Branching")
    class Branching {

        @Test
        @DisplayName("two appenders create two heads")
        void twoHeads() {
            ItemID gameId = ItemID.fromString("cg:item/branch-test");

            // Both appenders use the same library to store
            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            // First move (root)
            FrameBodyOld root = chain.append(List.of(
                    Binding.ref(ThematicRole.Theme.IID, PAWN)
            ), signer);

            // Now create two branches from the same root.
            // We need to manually create frames that both follow the root.
            // The second appender stores directly into signer's library.
            ContentID rootHash = root.hash();

            // Branch A: signer appends normally (will follow root)
            FrameBodyOld branchA = chain.append(List.of(
                    Binding.ref(ThematicRole.Agent.IID, WHITE)
            ), signer);

            // Branch B: store a frame that also follows only the root
            List<Binding> branchBBindings = List.of(
                    new Binding(ThematicRole.Location.IID, BindingTarget.iid(gameId)),
                    new Binding(ItemID.fromString(ThematicRole.Follows.KEY),
                            BindingTarget.ref(rootHash)),
                    Binding.ref(ThematicRole.Agent.IID, BLACK)
            );
            FrameBodyOld branchBBody = new FrameBodyOld(MOVE, branchBBindings);
            FrameRecordOld branchBRecord = FrameRecordOld.create(branchBBody, signer2);
            signer.library().storeFrame(branchBBody, branchBRecord);

            // Now there should be 2 heads (branchA and branchB)
            assertThat(chain.heads()).hasSize(2);

            // Total frames: root + branchA + branchB = 3
            assertThat(chain.count()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Merge")
    class Merge {

        @Test
        @DisplayName("append after two heads produces single new head")
        void mergeHeads() {
            ItemID gameId = ItemID.fromString("cg:item/merge-test");

            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            // Root
            FrameBodyOld root = chain.append(List.of(
                    Binding.ref(ThematicRole.Theme.IID, PAWN)
            ), signer);

            // Branch A: normal append (follows root)
            FrameBodyOld branchA = chain.append(List.of(
                    Binding.ref(ThematicRole.Agent.IID, WHITE)
            ), signer);

            // Branch B: store a frame that also follows only the root (not branchA)
            ContentID rootHash = root.hash();
            List<Binding> branchBBindings = List.of(
                    new Binding(ThematicRole.Location.IID, BindingTarget.iid(gameId)),
                    new Binding(ItemID.fromString(ThematicRole.Follows.KEY),
                            BindingTarget.ref(rootHash)),
                    Binding.ref(ThematicRole.Agent.IID, BLACK)
            );
            FrameBodyOld branchB = new FrameBodyOld(MOVE, branchBBindings);
            signer.library().storeFrame(branchB, FrameRecordOld.create(branchB, signer2));

            // Verify we have 2 heads (branchA and branchB)
            assertThat(chain.heads()).hasSize(2);

            // Now append — this will automatically follow both heads (merge)
            FrameBodyOld merged = chain.append(List.of(
                    Binding.ref(ThematicRole.Theme.IID, KNIGHT)
            ), signer);

            // Should now have single head
            assertThat(chain.heads()).hasSize(1);
            assertThat(chain.heads().getFirst().hash()).isEqualTo(merged.hash());

            // Merged frame should have 2 FOLLOWS bindings
            assertThat(merged.getAllBindings(
                    ItemID.fromString(ThematicRole.Follows.KEY))).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Fold")
    class FoldTests {

        @Test
        @DisplayName("fold accumulates state over chain")
        void foldAccumulates() {
            ItemID gameId = ItemID.fromString("cg:item/fold-test");
            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            chain.append(List.of(Binding.ref(ThematicRole.Theme.IID, PAWN)), signer);
            chain.append(List.of(Binding.ref(ThematicRole.Theme.IID, KNIGHT)), signer);
            chain.append(List.of(Binding.ref(ThematicRole.Theme.IID, PAWN)), signer);

            // Count how many frames
            int count = chain.fold(0, (state, body) -> state + 1);
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("fold sees frames in causal order")
        void foldOrdering() {
            ItemID gameId = ItemID.fromString("cg:item/fold-order-test");
            FrameChain chain = new FrameChain(signer.library(), gameId, MOVE);

            chain.append(List.of(Binding.ref(ThematicRole.Theme.IID, PAWN)), signer);
            chain.append(List.of(Binding.ref(ThematicRole.Theme.IID, KNIGHT)), signer);

            // Verify order: first frame has no FOLLOWS, second has one
            AtomicInteger idx = new AtomicInteger();
            chain.fold(null, (state, body) -> {
                int i = idx.getAndIncrement();
                if (i == 0) {
                    assertThat(body.getAllBindings(
                            ItemID.fromString(ThematicRole.Follows.KEY))).isEmpty();
                } else {
                    assertThat(body.getAllBindings(
                            ItemID.fromString(ThematicRole.Follows.KEY))).isNotEmpty();
                }
                return null;
            });
        }
    }

    @Nested
    @DisplayName("Isolation")
    class Isolation {

        @Test
        @DisplayName("different predicates don't interfere")
        void differentPredicates() {
            ItemID gameId = ItemID.fromString("cg:item/isolation-test");
            ItemID CHAT = ItemID.fromString("cg:pred/chat");

            FrameChain moves = new FrameChain(signer.library(), gameId, MOVE);
            FrameChain chats = new FrameChain(signer.library(), gameId, CHAT);

            moves.append(List.of(), signer);
            moves.append(List.of(), signer);
            chats.append(List.of(), signer);

            assertThat(moves.count()).isEqualTo(2);
            assertThat(chats.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("different items don't interfere")
        void differentItems() {
            ItemID game1 = ItemID.fromString("cg:item/game-1");
            ItemID game2 = ItemID.fromString("cg:item/game-2");

            FrameChain chain1 = new FrameChain(signer.library(), game1, MOVE);
            FrameChain chain2 = new FrameChain(signer.library(), game2, MOVE);

            chain1.append(List.of(), signer);
            chain1.append(List.of(), signer);
            chain2.append(List.of(), signer);

            assertThat(chain1.count()).isEqualTo(2);
            assertThat(chain2.count()).isEqualTo(1);
        }
    }
}
