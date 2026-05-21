package dev.everydaythings.graph.cryptography;


import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Delegation;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
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

        ItemRef parent = ItemRef.fromString("parent");
        ItemRef child = ItemRef.fromString("child");

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Delegation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), parent),
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), child)
                )
        );

        assertThat(Signer.readAgent(body)).contains(parent);
    }

    @Test
    @DisplayName("readDelegate returns the THEME (child identity)")
    void readsDelegate() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ItemRef parent = ItemRef.fromString("parent");
        ItemRef child = ItemRef.fromString("child");

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Delegation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), parent),
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), child)
                )
        );

        assertThat(Signer.readTheme(body)).contains(child);
    }

    @Test
    @DisplayName("readPurposes returns the multiset of scope sememes")
    void readsPurposesMultiset() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Delegation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), ItemRef.fromString("parent")),
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ItemRef.fromString("child")),
                        Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), ItemRef.iid(IdentityVocabulary.Signing.KEY)),
                        Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY))
                )
        );

        assertThat(Signer.readPurposes(body))
                .containsExactlyInAnyOrder(ItemRef.iid(IdentityVocabulary.Signing.KEY), ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
    }

    @Test
    @DisplayName("readExpires returns the expiry timestamp when present")
    void readsExpires() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Instant expires = Instant.parse("2030-01-01T00:00:00Z");

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Delegation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), ItemRef.fromString("parent")),
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ItemRef.fromString("child")),
                        new Binding(
                                ItemRef.iid(ThematicRole.Attribute.KEY),
                                List.of(new CompoundKey.Sememe(ItemRef.iid(CoreVocabulary.Expires.KEY))),
                                expires
                        )
                )
        );

        assertThat(Signer.readExpires(body)).contains(expires);
    }

    @Test
    @DisplayName("readPurposes returns empty list for fully-general delegation")
    void readsPurposesEmpty() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Delegation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), ItemRef.fromString("parent")),
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ItemRef.fromString("child"))
                )
        );

        assertThat(Signer.readPurposes(body)).isEmpty();
    }
}
