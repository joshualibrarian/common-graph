package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.CoreVocabulary;
import dev.everydaythings.graph.semantics.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises DELEGATION's body-reader utilities — schema-level checks that
 * delegator, delegate, purposes, and expiry are correctly extracted.
 */
class DelegationTest {

    @Test
    @DisplayName("readDelegator returns the AGENT (parent identity)")
    void readsDelegator() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ItemID parent = ItemID.fromString("parent");
        ItemID child = ItemID.fromString("child");

        Body body = Body.of(
                ItemRef.of(Delegation.IID),
                List.of(
                        Binding.ref(ThematicRole.Agent.IID, parent),
                        Binding.ref(ThematicRole.Theme.IID, child)
                )
        );

        assertThat(Delegation.readDelegator(body)).contains(parent);
    }

    @Test
    @DisplayName("readDelegate returns the THEME (child identity)")
    void readsDelegate() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ItemID parent = ItemID.fromString("parent");
        ItemID child = ItemID.fromString("child");

        Body body = Body.of(
                ItemRef.of(Delegation.IID),
                List.of(
                        Binding.ref(ThematicRole.Agent.IID, parent),
                        Binding.ref(ThematicRole.Theme.IID, child)
                )
        );

        assertThat(Delegation.readDelegate(body)).contains(child);
    }

    @Test
    @DisplayName("readPurposes returns the multiset of scope sememes")
    void readsPurposesMultiset() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(Delegation.IID),
                List.of(
                        Binding.ref(ThematicRole.Agent.IID, ItemID.fromString("parent")),
                        Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("child")),
                        Binding.ref(ThematicRole.Purpose.IID, IdentityVocabulary.Signing.IID),
                        Binding.ref(ThematicRole.Purpose.IID, IdentityVocabulary.Encryption.IID)
                )
        );

        assertThat(Delegation.readPurposes(body))
                .containsExactlyInAnyOrder(IdentityVocabulary.Signing.IID, IdentityVocabulary.Encryption.IID);
    }

    @Test
    @DisplayName("readExpires returns the expiry timestamp when present")
    void readsExpires() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Instant expires = Instant.parse("2030-01-01T00:00:00Z");

        Body body = Body.of(
                ItemRef.of(Delegation.IID),
                List.of(
                        Binding.ref(ThematicRole.Agent.IID, ItemID.fromString("parent")),
                        Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("child")),
                        new Binding(
                                ThematicRole.Attribute.IID,
                                List.of(new CompoundKey.Sememe(CoreVocabulary.Expires.IID)),
                                Literal.ofInstant(expires)
                        )
                )
        );

        assertThat(Delegation.readExpires(body)).contains(expires);
    }

    @Test
    @DisplayName("readPurposes returns empty list for fully-general delegation")
    void readsPurposesEmpty() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(Delegation.IID),
                List.of(
                        Binding.ref(ThematicRole.Agent.IID, ItemID.fromString("parent")),
                        Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("child"))
                )
        );

        assertThat(Delegation.readPurposes(body)).isEmpty();
    }
}
