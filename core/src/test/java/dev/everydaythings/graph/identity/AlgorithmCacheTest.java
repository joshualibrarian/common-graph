package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test for the algorithm cache: bootstrap a librarian,
 * verify each lookup path returns a handle for Ed25519.
 *
 * <p>This is the only algorithm with full Java backing today; other
 * algorithms are declared in the vocabulary but their JCA decoders aren't
 * wired yet.  Tests for those land as we add per-key-family decode logic.
 */
class AlgorithmCacheTest {

    @Test
    @DisplayName("after bootstrap, Ed25519 is reachable by COSE id, varsig code, multikey code, and IID")
    void ed25519IsCachedAfterBootstrap() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        AlgorithmHandle byCose     = librarian.algorithmByCoseId(AlgorithmVocabulary.Ed25519.COSE_ID);
        AlgorithmHandle byVarsig   = librarian.algorithmByVarsigCode(AlgorithmVocabulary.Ed25519.VARSIG_CODE);
        AlgorithmHandle byMultikey = librarian.algorithmByMultikeyCode(AlgorithmVocabulary.Ed25519.MULTIKEY_CODE);
        AlgorithmHandle byIid      = librarian.algorithmByIid(ItemRef.iid(AlgorithmVocabulary.Ed25519.KEY));

        assertThat(byCose).isNotNull();
        assertThat(byVarsig).isNotNull();
        assertThat(byMultikey).isNotNull();
        assertThat(byIid).isNotNull();

        // All four paths resolve to the same handle.
        assertThat(byCose).isSameAs(byVarsig).isSameAs(byMultikey).isSameAs(byIid);

        // The handle carries the metadata read off the seed manifest.
        assertThat(byCose.coseId()).isEqualTo(AlgorithmVocabulary.Ed25519.COSE_ID);
        assertThat(byCose.varsigCode()).isEqualTo(AlgorithmVocabulary.Ed25519.VARSIG_CODE);
        assertThat(byCose.multikeyCode()).isEqualTo(AlgorithmVocabulary.Ed25519.MULTIKEY_CODE);
        assertThat(byCose.sememeIid()).isEqualTo(ItemRef.iid(AlgorithmVocabulary.Ed25519.KEY));
    }

    @Test
    @DisplayName("unknown codec returns null")
    void unknownCodecMisses() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        assertThat(librarian.algorithmByCoseId(99999)).isNull();
        assertThat(librarian.algorithmByVarsigCode(99999)).isNull();
        assertThat(librarian.algorithmByMultikeyCode(99999)).isNull();
    }

    @Test
    @DisplayName("end-to-end: sign with the librarian's vault, verify through the handle-wrapped VarSig")
    void signAndVerifyThroughHandle() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        byte[] message = "hello, common graph".getBytes();
        VarSig rawSignature = librarian.sign(message);

        // Re-decode the signature through the librarian — this is the wire-boundary
        // resolution that wraps the algorithm handle onto the VarSig.
        VarSig wrapped = VarSig.decode(rawSignature.encoded(), librarian);
        assertThat(wrapped.handle()).isNotNull();

        // Similarly wrap the librarian's public key as a MultiKey with resolved handle.
        MultiKey publicKey = librarian.signingPublicKey().orElseThrow();
        MultiKey wrappedKey = MultiKey.decode(publicKey.encoded(), librarian);
        assertThat(wrappedKey.handle()).isNotNull();

        // Verify entirely through the wrapper — zero further lookups.
        assertThat(wrapped.verify(message, wrappedKey)).isTrue();
        assertThat(wrapped.verify("tampered".getBytes(), wrappedKey)).isFalse();
    }
}
