package dev.everydaythings.graph.runtime;


import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke tests for {@link Librarian#anonymous()} — the no-identity, no-vault,
 * no-inception startup path. These pin the contract of "what an anonymous
 * Librarian can and cannot do" before Stages 2-4 build the rest of the new
 * startup flow on top.
 */
class LibrarianAnonymousTest {

    @Test
    @DisplayName("anonymous() returns a Librarian with no iid")
    void noIid() {
        Librarian lib = Librarian.anonymous();
        assertThat(lib.iid()).isNull();
    }

    @Test
    @DisplayName("anonymous() has no signing capability")
    void noVault() {
        Librarian lib = Librarian.anonymous();
        assertThat(lib.canSign()).isFalse();
        assertThat(lib.vault()).isEmpty();
        assertThat(lib.signingPublicKey()).isEmpty();
    }

    @Test
    @DisplayName("anonymous() has no filesystem footprint")
    void noRootPath() {
        Librarian lib = Librarian.anonymous();
        assertThat(lib.rootPath()).isEmpty();
    }

    @Test
    @DisplayName("anonymous() carries a Library but has no encoder (PureMapLibrary)")
    void carriesLibrary() {
        Librarian lib = Librarian.anonymous();
        assertThat(lib.library()).isNotNull();
        // PureMapLibrary holds live Datums; no serialization, no encoder.
        assertThat(lib.encoder()).isEmpty();
    }

    @Test
    @DisplayName("anonymous() Library has no encoder (pure-map data store)")
    void backendHasNoEncoder() {
        Librarian lib = Librarian.anonymous();
        // The anonymous-mode Library is composed of a PureMapDataStore (no
        // encoder) + SkipList indexes. Library itself is the same concrete
        // class either way; we identify the mode by the data store's encoder
        // absence.
        assertThat(lib.library().encoder()).isEmpty();
    }

    @Test
    @DisplayName("anonymous() Librarian is its own librarian (self-bound for routing)")
    void selfBound() {
        Librarian lib = Librarian.anonymous();
        assertThat(lib.librarian()).isSameAs(lib);
    }

    @Test
    @DisplayName("sign(...) throws on anonymous Librarian — no vault")
    void signThrows() {
        Librarian lib = Librarian.anonymous();
        assertThatThrownBy(() -> lib.sign(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no signing key");
    }

    @Test
    @DisplayName("commit(...) throws on anonymous Librarian — no identity")
    void commitThrows() {
        Librarian lib = Librarian.anonymous();
        assertThatThrownBy(() -> lib.commit(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Anonymous");
    }

    @Test
    @DisplayName("register(...) refuses an item with no iid")
    void registerRefusesAnonymousItem() {
        Librarian lib = Librarian.anonymous();
        // The anonymous Librarian itself has no iid — attempting to register it
        // would silently corrupt the cache (HashMap permits null keys); we
        // reject explicitly.
        assertThatThrownBy(() -> lib.register(lib))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Anonymous");
    }

    @Test
    @DisplayName("register(...) accepts a normal item with an iid")
    void registerAcceptsNormalItem() {
        Librarian lib = Librarian.anonymous();
        Item item = new Item(ItemRef.fromString("test.anonymous-host:some-item"), lib);
        lib.register(item);
        assertThat(lib.fetchItem(item.iid())).contains(item);
    }

    @Test
    @DisplayName("fetchItem on an unknown iid returns empty (no signing required)")
    void fetchUnknown() {
        Librarian lib = Librarian.anonymous();
        ItemRef iid = ItemRef.fromString("test.anonymous:nothing-here");
        assertThat(lib.fetchItem(iid)).isEmpty();
    }

    @Test
    @DisplayName("submit a propositional frame works even without identity (no signing path)")
    void submitWithoutIdentity() {
        Librarian lib = Librarian.anonymous();
        // Build a propositional body with a head — anonymous Librarian can
        // persist + route this. Notification fires for items that exist in the
        // cache; we don't register anything so the routing loop is a no-op.
        Body body = Body.of(ItemRef.of(ItemRef.iid(LibrarianVocabulary.Create.KEY)), List.of());
        // submit() should not throw — anonymous can route, just can't sign.
        SubmitResult result = lib.submit(dev.everydaythings.graph.datum.Frame.of(body, List.of()));
        assertThat(result).isNotNull();
        assertThat(result.submitted().body()).isEqualTo(body);
    }

    @Test
    @DisplayName("toString reads 'anonymous' instead of an iid")
    void toStringReadsAnonymous() {
        Librarian lib = Librarian.anonymous();
        assertThat(lib.toString()).contains("anonymous");
    }
}
