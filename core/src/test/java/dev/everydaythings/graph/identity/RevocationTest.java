package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary.Compromise;
import dev.everydaythings.graph.identity.IdentityVocabulary.Revocation;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises REVOCATION's polymorphic target reading — distinguishing identity
 * revocation (THEME → @item-iid) from frame/event revocation (THEME → #content-cid).
 */
class RevocationTest {

    @Test
    @DisplayName("identity revocation: THEME is an item reference")
    void identityRevocation() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ItemRef retiring = ItemRef.fromString("retiring-identity");

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(Binding.ref(ThematicRole.Theme.IID, retiring))
        );

        // After IidTarget retirement, item refs and content refs share the same
        // RefTarget wire shape (multihash bytes), so readTargetCid will also
        // succeed for an identity-pointing ref — the bytes are interpretable
        // either way. The identity-vs-content distinction now lives at the
        // application/semantic layer (predicate purpose, qualifiers, or lookup
        // context), not at the binding-target's structural type.
        assertThat(Signer.readTheme(body)).contains(retiring);
    }

    @Test
    @DisplayName("frame revocation: THEME is a content reference")
    void frameRevocation() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ContentRef frameCid = ContentRef.of(new byte[]{4, 5, 6, 7, 8});

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(new Binding(
                        ThematicRole.Theme.IID,
                        List.of(),
                        frameCid
                ))
        );

        assertThat(Signer.readThemeCid(body)).contains(frameCid);
    }

    @Test
    @DisplayName("readReason returns the formal reason sememe when present")
    void readsReason() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, ItemRef.fromString("compromised")),
                        Binding.ref(ThematicRole.Purpose.IID, Compromise.IID)
                )
        );

        assertThat(Signer.readPurpose(body)).contains(Compromise.IID);
    }

    @Test
    @DisplayName("readReason is empty when no PURPOSE binding present")
    void noReason() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(Binding.ref(ThematicRole.Theme.IID, ItemRef.fromString("retired")))
        );

        assertThat(Signer.readPurpose(body)).isEmpty();
    }
}
