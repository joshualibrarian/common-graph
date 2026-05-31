package dev.everydaythings.graph.runtime.user;

import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.identifier.EmailAddress;
import dev.everydaythings.graph.value.identifier.Handle;
import dev.everydaythings.graph.value.identifier.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link User} bootstrap and identifier attachment.
 *
 * <p>Each test mints a fresh in-memory librarian and creates Users against
 * it.  The librarian provides the storage substrate for the User's manifest
 * and any Identifier frames the User publishes.
 */
class UserTest {

    @Nested
    @DisplayName("Bootstrap")
    class Bootstrap {

        @Test
        @DisplayName("create(Librarian) produces a User with a vault, IID, and canSign")
        void bareUser() {
            Librarian lib = Librarian.inMemory();
            User user = User.create(lib);

            assertThat(user).isNotNull();
            assertThat(user.iid()).isNotNull();
            assertThat(user.canSign()).isTrue();
            assertThat(user.archetype()).isEqualTo(User.ARCHETYPE);
        }

        @Test
        @DisplayName("create(Librarian, String) attaches a Handle identifier")
        void withUsername() {
            Librarian lib = Librarian.inMemory();
            User user = User.create(lib, "joshua-c");

            List<Body> idFrames = identifierFramesFor(lib, user.iid());
            assertThat(idFrames).hasSize(1);

            Body frame = idFrames.get(0);
            assertThat(frame.headRef()).isEqualTo(ItemRef.iid(Identifier.KEY));

            Object themeTarget = bindingTarget(frame, ItemRef.iid(ThematicRole.Theme.KEY));
            assertThat(themeTarget).isEqualTo(user.iid());

            Object valueTarget = bindingTarget(frame, ItemRef.iid(ThematicRole.Value.KEY));
            assertThat(valueTarget).isInstanceOf(Body.class);
            Body valueBody = (Body) valueTarget;
            assertThat(valueBody.headRef()).isEqualTo(ItemRef.iid(Handle.KEY));
            assertThat(valueBody.atomicContent()).contains("joshua-c");
        }

        @Test
        @DisplayName("two Users with different vaults have different IIDs")
        void twoUsersDifferentIids() {
            Librarian lib = Librarian.inMemory();
            User alice = User.create(lib, "alice");
            User bob = User.create(lib, "bob");

            assertThat(alice.iid()).isNotEqualTo(bob.iid());
        }

        @Test
        @DisplayName("create publishes a Librarian SERVES User frame")
        void publishesServes() {
            Librarian lib = Librarian.inMemory();
            User user = User.create(lib);

            List<Body> servesFrames = servesFramesFor(lib, user.iid());
            assertThat(servesFrames).hasSize(1);

            Body frame = servesFrames.get(0);
            // THEME = the serving Signer (the librarian)
            assertThat(bindingTarget(frame, ItemRef.iid(ThematicRole.Theme.KEY)))
                    .isEqualTo(lib.iid());
            // GOAL = the served Signer (the user)
            assertThat(bindingTarget(frame, ItemRef.iid(ThematicRole.Goal.KEY)))
                    .isEqualTo(user.iid());
        }
    }

    @Nested
    @DisplayName("identifyAs")
    class IdentifyAs {

        @Test
        @DisplayName("publishes a self-signed Identifier frame referencing this user")
        void publishesFrame() {
            Librarian lib = Librarian.inMemory();
            User user = User.create(lib);

            user.identifyAs(Handle.of("joshua-c"));

            List<Body> idFrames = identifierFramesFor(lib, user.iid());
            assertThat(idFrames).hasSize(1);
        }

        @Test
        @DisplayName("multiple identifiers chain naturally")
        void multipleIdentifiers() {
            Librarian lib = Librarian.inMemory();
            User user = User.create(lib);

            user.identifyAs(Handle.of("joshua-c"))
                .identifyAs(EmailAddress.fromText("joshua@example.com"));

            List<Body> idFrames = identifierFramesFor(lib, user.iid());
            assertThat(idFrames).hasSize(2);

            // Different Identifier subtypes wrapped in the same frame shape.
            // After storage round-trip the typed Java subclass is lost; we
            // verify by checking the value body's head IID instead.
            List<ItemRef> valueHeads = idFrames.stream()
                    .map(f -> (Body) bindingTarget(f, ItemRef.iid(ThematicRole.Value.KEY)))
                    .map(Body::headRef)
                    .toList();
            assertThat(valueHeads).contains(ItemRef.iid(Handle.KEY));
            assertThat(valueHeads).contains(ItemRef.iid(EmailAddress.KEY));
        }

        @Test
        @DisplayName("rejects identifyAs on a librarian-less User")
        void rejectsBareUser() {
            User bare = new User(ItemRef.fromString("test:bare-user"));
            assertThatThrownBy(() -> bare.identifyAs(Handle.of("nope")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("librarian");
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Fetch all Identifier-headed frame bodies whose THEME points at the
     * given iid.  Uses the librarian's reverse-lookup index over reference
     * bindings.
     */
    private static List<Body> identifierFramesFor(Librarian lib, ItemRef iid) {
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef identifierHead = ItemRef.iid(Identifier.KEY);
        return lib.bodiesByReferenceBinding(themeRole, iid).stream()
                .filter(b -> identifierHead.equals(b.headRef()))
                .toList();
    }

    /**
     * Fetch SERVES-headed frame bodies whose GOAL points at the given iid
     * (the served signer).
     */
    private static List<Body> servesFramesFor(Librarian lib, ItemRef servedIid) {
        ItemRef goalRole = ItemRef.iid(ThematicRole.Goal.KEY);
        ItemRef servesHead = ItemRef.iid(IdentityVocabulary.Serves.KEY);
        return lib.bodiesByReferenceBinding(goalRole, servedIid).stream()
                .filter(b -> servesHead.equals(b.headRef()))
                .toList();
    }

    /** Read the target of the binding for {@code role} (no qualifiers) from {@code body}. */
    private static Object bindingTarget(Body body, ItemRef role) {
        return body.binding(CompoundKey.of(role))
                .map(Binding::target)
                .orElse(null);
    }
}
