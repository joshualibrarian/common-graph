package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.CoreVocabulary;
import dev.everydaythings.graph.semantics.ThematicRole;
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

        ItemID retiring = ItemID.fromString("retiring-identity");

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(Binding.ref(ThematicRole.Theme.IID, retiring))
        );

        assertThat(Revocation.readTargetIid(body)).contains(retiring);
        assertThat(Revocation.readTargetCid(body)).isEmpty();
    }

    @Test
    @DisplayName("frame revocation: THEME is a content reference")
    void frameRevocation() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ContentID frameCid = ContentID.of(new byte[]{4, 5, 6, 7, 8});

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(new Binding(
                        ThematicRole.Theme.IID,
                        List.of(),
                        BindingTarget.ref(frameCid)
                ))
        );

        assertThat(Revocation.readTargetCid(body)).contains(frameCid);
    }

    @Test
    @DisplayName("readReason returns the formal reason sememe when present")
    void readsReason() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("compromised")),
                        Binding.ref(ThematicRole.Purpose.IID, CoreVocabulary.Compromise.IID)
                )
        );

        assertThat(Revocation.readReason(body)).contains(CoreVocabulary.Compromise.IID);
    }

    @Test
    @DisplayName("readReason is empty when no PURPOSE binding present")
    void noReason() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(Revocation.IID),
                List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("retired")))
        );

        assertThat(Revocation.readReason(body)).isEmpty();
    }
}
