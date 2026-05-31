package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link LocalSession#mint(Librarian)} performs the three things
 * a fresh in-VM session bring-up needs: a random iid, registration with the
 * librarian's cache (so dispatch's {@code liveInstanceOf} finds it), and a
 * librarian-signed {@code ITEM_VIEW(self)} bootstrap frame in storage.
 */
@DisplayName("LocalSession.mint — session bootstrap at mint time")
class LocalSessionMintTest {

    @Test
    @DisplayName("mint registers the session in the librarian cache")
    void mintRegistersWithLibrarian() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession session = LocalSession.mint(lib);

        assertThat(lib.liveInstanceOf(ItemRef.iid(Session.KEY), session.iid()))
                .as("Librarian should find the minted LocalSession by archetype + iid")
                .containsSame(session);
    }

    @Test
    @DisplayName("mint publishes an ITEM_VIEW(self) bootstrap frame to the index")
    void mintPublishesBootstrapItemView() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession session = LocalSession.mint(lib);

        // The bootstrap frame is addressed to the session via Location =
        // sessionIid.  Query the index for bodies referencing the session
        // through Location and confirm one of them has the ITEM_VIEW head.
        List<Body> located = lib.bodiesByReferenceBinding(
                ItemRef.iid(ThematicRole.Location.KEY), session.iid());
        ItemRef itemViewHead = ItemRef.iid(SessionVocabulary.ItemView.KEY);
        assertThat(located)
                .as("Library should index at least one ITEM_VIEW body for the session")
                .anyMatch(body -> itemViewHead.equals(body.headRef()));
    }

    @Test
    @DisplayName("mint allocates a fresh iid per call")
    void mintAllocatesDistinctIids() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        LocalSession a = LocalSession.mint(lib);
        LocalSession b = LocalSession.mint(lib);

        assertThat(a.iid()).isNotEqualTo(b.iid());
    }

    @Test
    @DisplayName("ITEM_VIEW.KEY is the predicate the test contract depends on")
    void itemViewKeyIsStable() {
        // Locks the contract: this test class will need updating if the
        // bootstrap predicate or its key ever changes.
        assertThat(SessionVocabulary.ItemView.KEY).isEqualTo("cg.predicate:item-view");
    }
}
