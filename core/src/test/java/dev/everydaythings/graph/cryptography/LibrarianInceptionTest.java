package dev.everydaythings.graph.cryptography;


import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.cryptography.IdentityVocabulary.Inception;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.ThematicRole;
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
        List<DatumRef> frameCids = lib.library()
                .bodyCidsForReferenceBinding(ItemRef.iid(ThematicRole.Theme.KEY), lib.iid());

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
        assertThat(Signer.readPurpose(inception.body())).contains(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        assertThat(Signer.committedKeys(inception.body()))
                .containsExactly(lib.signingPublicKey().orElseThrow());
    }

    @Test
    @DisplayName("auto-published INCEPTION includes the pre-rotation next-key digest")
    void inceptionIncludesPreRotationDigest() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame inception = findInception(lib);
        ContentRef expectedDigest = lib.signingNextKeyDigest().orElseThrow();
        assertThat(Signer.nextKeyDigests(inception.body()))
                .containsExactly(expectedDigest);
    }

    @Test
    @DisplayName("currentKeys returns the librarian's incepted signing keys")
    void signingKeysFromKel() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        assertThat(lib.currentKeys(lib.iid(), ItemRef.iid(IdentityVocabulary.Signing.KEY)))
                .containsExactly(lib.signingPublicKey().orElseThrow());
    }

    @Test
    @DisplayName("currentKeys returns empty for an un-incepted identity")
    void signingKeysForUnknownIdentity() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        assertThat(lib.currentKeys(
                ItemRef.fromString("some-stranger"),
                ItemRef.iid(IdentityVocabulary.Signing.KEY)))
                .isEmpty();
    }

    private static Frame findInception(Librarian lib) {
        return lib.library()
                .bodyCidsForReferenceBinding(ItemRef.iid(ThematicRole.Theme.KEY), lib.iid())
                .stream()
                .map(cid -> lib.fetchFrame(cid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(LibrarianInceptionTest::isInceptionFrame)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no INCEPTION frame found"));
    }

    private static boolean isInceptionFrame(Frame frame) {
        return frame.body().head() instanceof ItemRef ref
                && ItemRef.iid(Inception.KEY).equals(ref.iid());
    }
}
