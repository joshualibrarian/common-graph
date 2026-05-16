package dev.everydaythings.graph.identity;


import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary.Multikey;
import dev.everydaythings.graph.identity.IdentityVocabulary.Next;
import dev.everydaythings.graph.identity.IdentityVocabulary.Rotation;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.CoreVocabulary.Sequence;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ROTATION's body-reader utilities. Schema-level tests; full chain-replay
 * verification (preimage match, sequence enforcement, OLD/NEW signature thresholds)
 * is deferred to the verification phase.
 */
class RotationTest {

    @Test
    @DisplayName("readFollows returns the prior-event content reference")
    void readsFollows() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ContentRef priorEventCid = ContentRef.of(new byte[]{1, 2, 3, 4});

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Rotation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), lib.iid()),
                        Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), ItemRef.iid(IdentityVocabulary.Signing.KEY)),
                        new Binding(
                                ItemRef.iid(ThematicRole.Follows.KEY),
                                List.of(),
                                priorEventCid
                        )
                )
        );

        assertThat(Signer.readFollows(body)).contains(priorEventCid);
    }

    @Test
    @DisplayName("readSequence returns the rotation ordinal")
    void readsSequence() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Rotation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), lib.iid()),
                        new Binding(
                                ItemRef.iid(ThematicRole.Attribute.KEY),
                                List.of(new CompoundKey.Sememe(ItemRef.iid(Sequence.KEY))),
                                (long) (3)
                        )
                )
        );

        assertThat(Signer.readSequence(body)).contains(3L);
    }

    @Test
    @DisplayName("nextKeyDigests extracts INSTRUMENT [NEXT] commitments")
    void readsNextDigests() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ContentRef nextDigest = ContentRef.of(new byte[]{9, 9, 9});
        MultiKey currentKey = lib.signingPublicKey().orElseThrow();

        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Rotation.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), lib.iid()),
                        new Binding(
                                ItemRef.iid(ThematicRole.Instrument.KEY),
                                List.of(new CompoundKey.Sememe(ItemRef.iid(Multikey.KEY))),
                                currentKey.encoded()
                        ),
                        new Binding(
                                ItemRef.iid(ThematicRole.Instrument.KEY),
                                List.of(new CompoundKey.Sememe(ItemRef.iid(Next.KEY))),
                                nextDigest
                        )
                )
        );

        // currentKeys should NOT include the NEXT-qualified one
        assertThat(Signer.committedKeys(body)).hasSize(1);
        // nextKeyDigests should return only the NEXT-qualified ones
        assertThat(Signer.nextKeyDigests(body)).containsExactly(nextDigest);
    }
}
