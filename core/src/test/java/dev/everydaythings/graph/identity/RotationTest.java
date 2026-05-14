package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.value.Literal;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary.Multikey;
import dev.everydaythings.graph.identity.IdentityVocabulary.Next;
import dev.everydaythings.graph.identity.IdentityVocabulary.Rotation;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.CoreVocabulary.Sequence;
import dev.everydaythings.graph.semantics.ThematicRole;
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

        ContentID priorEventCid = ContentID.of(new byte[]{1, 2, 3, 4});

        Body body = Body.of(
                ItemRef.of(Rotation.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, lib.iid()),
                        Binding.ref(ThematicRole.Purpose.IID, IdentityVocabulary.Signing.IID),
                        new Binding(
                                ThematicRole.Follows.IID,
                                List.of(),
                                BindingTarget.ref(priorEventCid)
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
                ItemRef.of(Rotation.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, lib.iid()),
                        new Binding(
                                ThematicRole.Attribute.IID,
                                List.of(new CompoundKey.Sememe(Sequence.IID)),
                                Literal.ofInteger(3)
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

        ContentID nextDigest = ContentID.of(new byte[]{9, 9, 9});
        MultiKey currentKey = lib.signingPublicKey().orElseThrow();

        Body body = Body.of(
                ItemRef.of(Rotation.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, lib.iid()),
                        new Binding(
                                ThematicRole.Instrument.IID,
                                List.of(new CompoundKey.Sememe(Multikey.IID)),
                                Literal.ofMultiKey(currentKey)
                        ),
                        new Binding(
                                ThematicRole.Instrument.IID,
                                List.of(new CompoundKey.Sememe(Next.IID)),
                                BindingTarget.ref(nextDigest)
                        )
                )
        );

        // currentKeys should NOT include the NEXT-qualified one
        assertThat(Signer.committedKeys(body)).hasSize(1);
        // nextKeyDigests should return only the NEXT-qualified ones
        assertThat(Signer.nextKeyDigests(body)).containsExactly(nextDigest);
    }
}
