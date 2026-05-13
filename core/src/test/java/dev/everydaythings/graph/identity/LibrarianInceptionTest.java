package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary.Inception;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the auto-publish-INCEPTION-on-bootstrap behavior. After {@code bootstrap()},
 * a librarian should have its own self-attested INCEPTION on the signing track.
 */
class LibrarianInceptionTest {

    @Test
    @DisplayName("bootstrap() publishes a self-INCEPTION targeting the librarian's IID")
    void bootstrapPublishesInception() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Query for frames where THEME → lib.iid()
        List<DatumID> frameCids = lib.library()
                .bodyCidsForReferenceBinding(ThematicRole.Theme.IID, lib.iid());

        // Find the INCEPTION among them
        Optional<Frame> inceptionFrame = frameCids.stream()
                .map(cid -> lib.fetchFrame(cid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(LibrarianInceptionTest::isInceptionFrame)
                .findFirst();

        assertThat(inceptionFrame).isPresent();
    }

    @Test
    @DisplayName("auto-published INCEPTION is self-attested")
    void inceptionIsSelfAttested() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame inception = findInception(lib);
        assertThat(Signer.isSelfAttested(inception)).isTrue();
    }

    @Test
    @DisplayName("auto-published INCEPTION has correct THEME, PURPOSE, and committed key")
    void inceptionShape() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame inception = findInception(lib);
        assertThat(Signer.readTheme(inception.body())).contains(lib.iid());
        assertThat(Signer.readPurpose(inception.body())).contains(IdentityVocabulary.Signing.IID);
        assertThat(Signer.committedKeys(inception.body()))
                .containsExactly(lib.signingPublicKey().orElseThrow());
    }

    @Test
    @DisplayName("auto-published INCEPTION includes the pre-rotation next-key digest")
    void inceptionIncludesPreRotationDigest() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame inception = findInception(lib);
        ContentID expectedDigest = lib.signingNextKeyDigest().orElseThrow();
        assertThat(Signer.nextKeyDigests(inception.body()))
                .containsExactly(expectedDigest);
    }

    @Test
    @DisplayName("currentKeys returns the librarian's incepted signing keys")
    void signingKeysFromKel() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        assertThat(lib.currentKeys(lib.iid(), IdentityVocabulary.Signing.IID))
                .containsExactly(lib.signingPublicKey().orElseThrow());
    }

    @Test
    @DisplayName("currentKeys returns empty for an un-incepted identity")
    void signingKeysForUnknownIdentity() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        assertThat(lib.currentKeys(
                dev.everydaythings.graph.item.id.ItemID.fromString("some-stranger"),
                IdentityVocabulary.Signing.IID))
                .isEmpty();
    }

    private static Frame findInception(Librarian lib) {
        return lib.library()
                .bodyCidsForReferenceBinding(ThematicRole.Theme.IID, lib.iid())
                .stream()
                .map(cid -> lib.fetchFrame(cid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(LibrarianInceptionTest::isInceptionFrame)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no INCEPTION frame found"));
    }

    private static boolean isInceptionFrame(Frame frame) {
        return frame.body().head() instanceof ItemRef ref
                && Inception.IID.equals(ref.iid());
    }
}
