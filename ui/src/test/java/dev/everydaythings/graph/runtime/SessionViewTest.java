package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.frame.ViewHandle;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ITEM_VIEW frame management on Session.
 */
@DisplayName("Session View Management")
class SessionViewTest {

    private Librarian librarian;
    private TestSession session;

    static class TestSession extends Session {
        TestSession(LibrarianHandle librarian, Ref context) {
            super(librarian, context);
        }
        @Override public int run() { return 0; }
        @Override public Integer call() { return 0; }
        @Override public void close() {}
        @Override protected void render() {}
        @Override protected void output(String message) {}
    }

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
        LocalLibrarian handle = new LocalLibrarian(librarian);
        session = new TestSession(handle, Ref.of(librarian.iid()));
    }

    @Nested
    @DisplayName("openView")
    class OpenView {

        @Test
        @DisplayName("creates ITEM_VIEW frame on session")
        void createsFrame() {
            Item item = Item.create(librarian);
            FrameKey key = session.openView(item.iid());

            assertThat(key).isNotNull();
            assertThat(session.openViews()).hasSize(1);
            assertThat(session.openViews().getFirst().target()).isEqualTo(item.iid());
        }

        @Test
        @DisplayName("returns existing key for duplicate target")
        void returnsExistingForDuplicate() {
            Item item = Item.create(librarian);
            FrameKey key1 = session.openView(item.iid());
            FrameKey key2 = session.openView(item.iid());

            assertThat(key1).isEqualTo(key2);
            assertThat(session.openViews()).hasSize(1);
        }

        @Test
        @DisplayName("can open multiple views")
        void multipleViews() {
            Item item1 = Item.create(librarian);
            Item item2 = Item.create(librarian);

            session.openView(item1.iid());
            session.openView(item2.iid());

            assertThat(session.openViews()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("closeView")
    class CloseView {

        @Test
        @DisplayName("removes ITEM_VIEW frame")
        void removesFrame() {
            Item item = Item.create(librarian);
            session.openView(item.iid());
            assertThat(session.openViews()).hasSize(1);

            session.closeView(item.iid());
            assertThat(session.openViews()).isEmpty();
        }

        @Test
        @DisplayName("no-op for non-existent view")
        void noOpForNonExistent() {
            Item item = Item.create(librarian);
            session.closeView(item.iid());
            assertThat(session.openViews()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findView")
    class FindView {

        @Test
        @DisplayName("finds existing view")
        void findsExisting() {
            Item item = Item.create(librarian);
            session.openView(item.iid());

            ViewHandle handle = session.findView(item.iid());
            assertThat(handle).isNotNull();
            assertThat(handle.target()).isEqualTo(item.iid());
            assertThat(handle.config().mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
        }

        @Test
        @DisplayName("returns null for non-existent")
        void returnsNullForMissing() {
            Item item = Item.create(librarian);
            assertThat(session.findView(item.iid())).isNull();
        }
    }

    @Nested
    @DisplayName("viewConfig")
    class ViewConfigTests {

        @Test
        @DisplayName("default config is PRESENTATION/FRAMES")
        void defaultConfig() {
            Item item = Item.create(librarian);
            FrameKey key = session.openView(item.iid());

            ViewConfig config = session.viewConfig(key);
            assertThat(config).isNotNull();
            assertThat(config.mode()).isEqualTo(ViewConfig.ViewMode.PRESENTATION);
            assertThat(config.inspectMode()).isEqualTo(ViewConfig.InspectMode.FRAMES);
        }

        @Test
        @DisplayName("config can be updated")
        void updateConfig() {
            Item item = Item.create(librarian);
            FrameKey key = session.openView(item.iid());

            ViewConfig updated = ViewConfig.builder()
                    .mode(ViewConfig.ViewMode.INSPECT)
                    .inspectMode(ViewConfig.InspectMode.VERSIONS)
                    .build();
            session.updateViewConfig(key, updated);

            ViewConfig retrieved = session.viewConfig(key);
            assertThat(retrieved.mode()).isEqualTo(ViewConfig.ViewMode.INSPECT);
            assertThat(retrieved.inspectMode()).isEqualTo(ViewConfig.InspectMode.VERSIONS);
        }
    }

    @Nested
    @DisplayName("verb dispatch")
    class VerbDispatch {

        @Test
        @DisplayName("actionView creates frame and sets active view on ItemView")
        void actionViewWiresItemView() {
            Item item = Item.create(librarian);

            session.actionView(item.iid());

            assertThat(session.openViews()).hasSize(1);
            assertThat(session.openViews().getFirst().target()).isEqualTo(item.iid());

            assertThat(session.itemView()).isNotNull();
            assertThat(session.itemView().hasActiveView()).isTrue();
            assertThat(session.itemView().activeView().target()).isEqualTo(item.iid());
        }

        @Test
        @DisplayName("actionClose removes frame and clears active view")
        void actionCloseWiresItemView() {
            Item item = Item.create(librarian);
            session.actionView(item.iid());
            assertThat(session.itemView().hasActiveView()).isTrue();

            session.actionClose(item.iid());

            assertThat(session.openViews()).isEmpty();
            assertThat(session.itemView().hasActiveView()).isFalse();
        }
    }
}
