package dev.everydaythings.graph.identity;


import dev.everydaythings.graph.identity.IdentityVocabulary.Inception;
import dev.everydaythings.graph.identity.EncryptionVocabulary.Multikey;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the INCEPTION predicate: founding key-state declaration for an identity's
 * key-track, with self-attestation as the cryptographic anchor.
 */
class InceptionTest {

    @Nested
    @DisplayName("Body readers")
    class Readers {

        @Test
        @DisplayName("readTheme returns the identity being incepted")
        void readsTheme() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body body = inceptionBody(lib, lib.signingPublicKey().orElseThrow());

            assertThat(Signer.readTheme(body)).contains(lib.iid());
        }

        @Test
        @DisplayName("readPurpose returns the key-track sememe")
        void readsPurpose() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body body = inceptionBody(lib, lib.signingPublicKey().orElseThrow());

            assertThat(Signer.readPurpose(body)).contains(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        }

        @Test
        @DisplayName("currentKeys extracts the committed multikeys")
        void extractsCurrentKeys() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            MultiKey libKey = lib.signingPublicKey().orElseThrow();
            Body body = inceptionBody(lib, libKey);

            List<MultiKey> keys = Signer.committedKeys(body);
            assertThat(keys).hasSize(1);
            assertThat(keys.get(0)).isEqualTo(libKey);
        }
    }

    @Nested
    @DisplayName("Self-attestation")
    class SelfAttestation {

        @Test
        @DisplayName("inception signed by the committed key passes self-attestation")
        void selfSignedPasses() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            Body body = inceptionBody(lib, lib.signingPublicKey().orElseThrow());
            Frame frame = lib.assembleFrame(body, lib);

            assertThat(Signer.isSelfAttested(frame)).isTrue();
        }

        @Test
        @DisplayName("inception signed by a different key fails self-attestation")
        void otherSignedFails() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            // Body commits lib's keys, but is signed by alice (a separate signer)
            Signer alice = Signer.inMemory();
            Body body = inceptionBody(lib, lib.signingPublicKey().orElseThrow());
            Frame frame = lib.assembleFrame(body, alice);

            assertThat(Signer.isSelfAttested(frame)).isFalse();
        }

        @Test
        @DisplayName("inception with no INSTRUMENT bindings is not self-attested")
        void noInstrumentFails() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            // Body has THEME and PURPOSE but no INSTRUMENT keys committed.
            Body body = Body.of(
                    ItemRef.of(ItemRef.iid(Inception.KEY)),
                    List.of(
                            Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), lib.iid()),
                            Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), ItemRef.iid(IdentityVocabulary.Signing.KEY))
                    )
            );
            Frame frame = lib.assembleFrame(body, lib);

            assertThat(Signer.isSelfAttested(frame)).isFalse();
        }
    }

    /**
     * Build a minimal INCEPTION body for the given identity, committing the
     * given key on the signing track.
     */
    private static Body inceptionBody(Librarian identity, MultiKey key) {
        return Body.of(
                ItemRef.of(ItemRef.iid(Inception.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), identity.iid()),
                        Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), ItemRef.iid(IdentityVocabulary.Signing.KEY)),
                        new Binding(
                                ItemRef.iid(ThematicRole.Instrument.KEY),
                                List.of(new CompoundKey.Sememe(ItemRef.iid(Multikey.KEY))),
                                key.encoded()
                        )
                )
        );
    }
}
