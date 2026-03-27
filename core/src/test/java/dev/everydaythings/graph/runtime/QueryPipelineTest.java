package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.Library;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class QueryPipelineTest {

    private static final ItemID CHESS = ItemID.fromString("cg.sememe:chess");

    @Test
    void chess_resolves_in_dictionary() throws Exception {
        try (Librarian lib = Librarian.createInMemory()) {
            LocalLibrarian local = new LocalLibrarian(lib);
            List<Posting> postings = local.lookup("chess").limit(10).toList();
            System.err.println("chess postings: " + postings.size());
            for (Posting p : postings) {
                System.err.println("  target=" + p.target().encodeText()
                    + " scope=" + p.scope()
                    + " features=" + p.features());
            }
            assertThat(postings).as("'chess' should resolve").isNotEmpty();
        }
    }

    @Test
    void instanceOf_frame_is_indexed() throws Exception {
        try (Librarian lib = Librarian.createInMemory()) {
            // Manually store an INSTANCE_OF frame like Sememe.actionCreate does
            ItemID gameIid = ItemID.random();
            FrameBody instanceOf = FrameBody.builder(LexicalVocabulary.InstanceOf.IID)
                    .bind(ThematicRole.Theme.IID, gameIid)
                    .bind(ThematicRole.Goal.IID, CHESS)
                    .build();

            System.err.println("Frame predicate: " + instanceOf.predicate().encodeText());
            System.err.println("Frame bindings: " + instanceOf.bindings());
            System.err.println("Frame homeId: " + instanceOf.homeId());
            System.err.println("Frame frameBindings count: " + instanceOf.frameBindings().size());
            for (Binding b : instanceOf.frameBindings()) {
                System.err.println("  binding role=" + b.role().encodeText()
                    + " targetId=" + b.targetId()
                    + " isSimpleKey=" + b.isSimpleKey());
            }

            lib.storeFrame(instanceOf);

            // Check: can we find frames by chess IID?
            Library library = lib.library();
            Stream<FrameBody> byChess = library.byItem(CHESS);
            List<FrameBody> chessBodies = byChess.toList();
            System.err.println("byItem(CHESS): " + chessBodies.size() + " frames");

            // Check: queryItems
            Set<ItemID> results = library.queryItems(Set.of(CHESS));
            System.err.println("queryItems(CHESS): " + results.size() + " items");
            for (ItemID id : results) {
                System.err.println("  result: " + id.encodeText());
            }

            assertThat(results).as("Should find the game instance via INSTANCE_OF").contains(gameIid);
        }
    }

    @Test
    void queryItems_via_librarian_storeFrame() throws Exception {
        try (Librarian lib = Librarian.createInMemory()) {
            // Use Librarian.storeFrame (the path Sememe.actionCreate uses)
            ItemID gameIid = ItemID.random();
            FrameBody instanceOf = FrameBody.builder(LexicalVocabulary.InstanceOf.IID)
                    .bind(ThematicRole.Theme.IID, gameIid)
                    .bind(ThematicRole.Goal.IID, CHESS)
                    .build();

            lib.storeFrame(instanceOf);

            Set<ItemID> results = lib.library().queryItems(Set.of(CHESS));
            System.err.println("queryItems via librarian: " + results.size());
            for (ItemID id : results) {
                System.err.println("  result: " + id.encodeText());
            }

            assertThat(results).as("Should find game via librarian storeFrame path").contains(gameIid);
        }
    }
}
