package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for Session's ephemeral authentication table.  No persistence; the
 * table lives only on the Session instance for as long as the process is
 * up.  These tests verify the registry semantics (add, remove, query,
 * snapshot) and the multi-user shape — several Users can be authenticated
 * to one Session concurrently.
 */
@DisplayName("Session authentication table")
class SessionAuthTest {

    private Session newSession(Librarian lib) {
        return new Session(ItemRef.iid("cg.test:session-instance"), lib);
    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("Newly minted session has an empty auth table")
        void emptyByDefault() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);

            assertThat(session.authenticatedUsers()).isEmpty();
        }

        @Test
        @DisplayName("authenticate(user) adds the user to the table")
        void addsUser() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User user = User.create(lib, "alice");

            session.authenticate(user);

            assertThat(session.isAuthenticated(user)).isTrue();
            assertThat(session.authenticatedUsers()).containsExactly(user);
        }

        @Test
        @DisplayName("authenticate is idempotent")
        void idempotent() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User user = User.create(lib, "alice");

            session.authenticate(user);
            session.authenticate(user);

            assertThat(session.authenticatedUsers()).hasSize(1);
        }

        @Test
        @DisplayName("authenticate returns this Session for chaining")
        void returnsThis() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User user = User.create(lib, "alice");

            assertThat(session.authenticate(user)).isSameAs(session);
        }

        @Test
        @DisplayName("authenticate(null) throws NPE")
        void nullUserThrows() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);

            assertThatNullPointerException().isThrownBy(() -> session.authenticate(null));
        }

        @Test
        @DisplayName("Multiple Users can be authenticated to one Session")
        void multipleUsers() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            User bob = User.create(lib, "bob");
            User carol = User.create(lib, "carol");

            session.authenticate(alice).authenticate(bob).authenticate(carol);

            assertThat(session.authenticatedUsers()).containsExactlyInAnyOrder(alice, bob, carol);
        }
    }

    @Nested
    @DisplayName("signOut")
    class SignOut {

        @Test
        @DisplayName("signOut removes the user")
        void removesUser() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            User bob = User.create(lib, "bob");
            session.authenticate(alice).authenticate(bob);

            session.signOut(alice);

            assertThat(session.isAuthenticated(alice)).isFalse();
            assertThat(session.isAuthenticated(bob)).isTrue();
        }

        @Test
        @DisplayName("signOut of a non-authenticated user is a no-op")
        void signOutUnknownIsNoOp() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            User stranger = User.create(lib, "stranger");
            session.authenticate(alice);

            session.signOut(stranger);

            assertThat(session.authenticatedUsers()).containsExactly(alice);
        }

        @Test
        @DisplayName("signOut(null) is a no-op")
        void signOutNullIsNoOp() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            session.authenticate(alice);

            session.signOut(null);

            assertThat(session.authenticatedUsers()).containsExactly(alice);
        }

        @Test
        @DisplayName("signOut returns this Session for chaining")
        void returnsThis() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User user = User.create(lib, "alice");
            session.authenticate(user);

            assertThat(session.signOut(user)).isSameAs(session);
        }
    }

    @Nested
    @DisplayName("isAuthenticated")
    class IsAuthenticated {

        @Test
        @DisplayName("isAuthenticated(ItemRef) matches by user IID")
        void matchesByIid() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            session.authenticate(alice);

            assertThat(session.isAuthenticated(alice.iid())).isTrue();
        }

        @Test
        @DisplayName("isAuthenticated(unknown IID) is false")
        void unknownIidFalse() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            session.authenticate(alice);

            assertThat(session.isAuthenticated(ItemRef.iid("cg.test:nobody"))).isFalse();
        }

        @Test
        @DisplayName("isAuthenticated((User) null) is false")
        void nullUserFalse() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);

            assertThat(session.isAuthenticated((User) null)).isFalse();
        }

        @Test
        @DisplayName("isAuthenticated((ItemRef) null) is false")
        void nullIidFalse() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);

            assertThat(session.isAuthenticated((ItemRef) null)).isFalse();
        }
    }

    @Nested
    @DisplayName("authenticatedUsers snapshot")
    class Snapshot {

        @Test
        @DisplayName("snapshot is an immutable copy")
        void snapshotImmutable() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            session.authenticate(alice);

            Set<User> snapshot = session.authenticatedUsers();

            assertThat(snapshot).hasSize(1);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshot.add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("snapshot doesn't change when the table changes later")
        void snapshotStable() {
            Librarian lib = Librarian.inMemory();
            Session session = newSession(lib);
            User alice = User.create(lib, "alice");
            User bob = User.create(lib, "bob");
            session.authenticate(alice);

            Set<User> snapshot = session.authenticatedUsers();
            session.authenticate(bob);

            assertThat(snapshot).containsExactly(alice);
            assertThat(session.authenticatedUsers()).containsExactlyInAnyOrder(alice, bob);
        }
    }
}
