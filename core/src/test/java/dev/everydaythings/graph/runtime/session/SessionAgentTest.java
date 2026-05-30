package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the ITEM_VIEW.AGENT binding — per-view "acting as" attribution.
 *
 * <p>A Session.openView call with an actingAs User stamps an Agent binding
 * on the ITEM_VIEW frame pointing at the user's iid.  This is how multi-user
 * workspaces disambiguate which authenticated User's authority a given
 * window's actions run under.
 */
@DisplayName("ITEM_VIEW.AGENT — per-view acting-as")
class SessionAgentTest {

    private Session newSession(Librarian lib) {
        return new Session(lib);
    }

    private List<Body> itemViewsFor(Librarian lib, ItemRef sessionIid) {
        ItemRef locationRole = ItemRef.iid(ThematicRole.Location.KEY);
        ItemRef itemViewHead = ItemRef.iid(SessionVocabulary.ItemView.KEY);
        return lib.bodiesByReferenceBinding(locationRole, sessionIid).stream()
                .filter(b -> itemViewHead.equals(b.headRef()))
                .toList();
    }

    @Nested
    @DisplayName("openView(itemIid)")
    class OpenViewNoAgent {

        @Test
        @DisplayName("publishes an ITEM_VIEW frame with no Agent binding")
        void noAgentByDefault() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            ItemRef itemIid = ItemRef.iid("cg.test:some-item");

            session.openView(itemIid);

            List<Body> views = itemViewsFor(lib, session.iid());
            assertThat(views).hasSize(1);
            assertThat(Session.agentOf(views.get(0))).isEmpty();
        }
    }

    @Nested
    @DisplayName("openView(itemIid, actingAs)")
    class OpenViewWithAgent {

        @Test
        @DisplayName("stamps an Agent binding pointing at the user's iid")
        void stampsAgent() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            session.authenticate(alice);
            ItemRef itemIid = ItemRef.iid("cg.test:some-item");

            session.openView(itemIid, alice);

            List<Body> views = itemViewsFor(lib, session.iid());
            assertThat(views).hasSize(1);
            Optional<ItemRef> agent = Session.agentOf(views.get(0));
            assertThat(agent).contains(alice.iid());
        }

        @Test
        @DisplayName("refuses to open a view acting as a non-authenticated user")
        void rejectsNonAuthenticated() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            User bob = User.create(lib, "bob");
            session.authenticate(alice);
            ItemRef itemIid = ItemRef.iid("cg.test:some-item");

            assertThatThrownBy(() -> session.openView(itemIid, bob))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not authenticated");

            assertThat(itemViewsFor(lib, session.iid())).isEmpty();
        }

        @Test
        @DisplayName("null actingAs behaves like the no-arg form")
        void nullActingAsBehavesAsNoArg() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            ItemRef itemIid = ItemRef.iid("cg.test:some-item");

            session.openView(itemIid, null);

            List<Body> views = itemViewsFor(lib, session.iid());
            assertThat(views).hasSize(1);
            assertThat(Session.agentOf(views.get(0))).isEmpty();
        }

        @Test
        @DisplayName("Theme and Location bindings still present alongside Agent")
        void coreBindingsPresent() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            session.authenticate(alice);
            ItemRef itemIid = ItemRef.iid("cg.test:some-item");

            session.openView(itemIid, alice);

            Body view = itemViewsFor(lib, session.iid()).get(0);
            Object theme = view.binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                    .map(Binding::target).orElse(null);
            Object location = view.binding(CompoundKey.of(ItemRef.iid(ThematicRole.Location.KEY)))
                    .map(Binding::target).orElse(null);
            assertThat(theme).isEqualTo(itemIid);
            assertThat(location).isEqualTo(session.iid());
        }

        @Test
        @DisplayName("Multi-user workspace: each window attributes to its own User")
        void multiUserPerWindow() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            User bob = User.create(lib, "bob");
            session.authenticate(alice).authenticate(bob);

            ItemRef aliceItem = ItemRef.iid("cg.test:alice-item");
            ItemRef bobItem = ItemRef.iid("cg.test:bob-item");
            session.openView(aliceItem, alice);
            session.openView(bobItem, bob);

            List<Body> views = itemViewsFor(lib, session.iid());
            assertThat(views).hasSize(2);

            // Pair each view's Theme with its Agent.
            for (Body view : views) {
                ItemRef theme = (ItemRef) view.binding(
                        CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                        .map(Binding::target).orElse(null);
                Optional<ItemRef> agent = Session.agentOf(view);
                if (aliceItem.equals(theme)) {
                    assertThat(agent).contains(alice.iid());
                } else if (bobItem.equals(theme)) {
                    assertThat(agent).contains(bob.iid());
                }
            }
        }
    }

    @Nested
    @DisplayName("agentOf(body)")
    class AgentOf {

        @Test
        @DisplayName("returns empty for null body")
        void nullBody() {
            assertThat(Session.agentOf(null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty when no Agent binding present")
        void noAgentBinding() {
            Body body = Body.of(
                    ItemRef.iid(SessionVocabulary.ItemView.KEY),
                    List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY),
                            ItemRef.iid("cg.test:item"))));
            assertThat(Session.agentOf(body)).isEmpty();
        }
    }
}
