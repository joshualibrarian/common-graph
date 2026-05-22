package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link LocalSession#mint(Librarian)} performs the three things
 * a fresh in-VM session bring-up needs: a random iid, registration with the
 * librarian's cache (so dispatch's {@code liveInstanceOf} finds it), and a
 * librarian-signed {@code ITEM_VIEW(self)} bootstrap frame in storage.
 */
@DisplayName("LocalSession.mint — session bootstrap at mint time")
class LocalSessionMintTest {

    @BeforeEach
    void resetCounter() {
        Session.resetItemViewHandlerInvocations();
    }

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
    @DisplayName("mint publishes a bootstrap ITEM_VIEW frame routed to the Session handler")
    void mintPublishesBootstrapItemView() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        long before = Session.itemViewHandlerInvocations();
        LocalSession.mint(lib);
        long after = Session.itemViewHandlerInvocations();

        // assembleFrame persists then dispatches; if the handler counter
        // moved, the bootstrap ITEM_VIEW was both persisted and routed.
        assertThat(after - before)
                .as("mint should publish exactly one ITEM_VIEW(self) bootstrap frame")
                .isEqualTo(1);
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
