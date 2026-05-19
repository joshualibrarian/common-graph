package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.algorithm.Algorithm;
import dev.everydaythings.graph.identity.algorithm.Signing;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test for the algorithm cache: bootstrap a librarian,
 * verify each lookup path returns a hydrated {@link Signing}
 * for Ed25519.
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

        Signing byCose     = librarian.algorithmByCoseId(Signing.Ed25519.COSE_ID);
        Signing byVarsig   = librarian.algorithmByVarsigCode(Signing.Ed25519.VARSIG_CODE);
        Signing byMultikey = librarian.algorithmByMultikeyCode(Signing.Ed25519.MULTIKEY_CODE);
        Signing byIid      = librarian.algorithmByIid(ItemRef.iid(Signing.Ed25519.KEY));

        assertThat(byCose).isNotNull();
        assertThat(byVarsig).isNotNull();
        assertThat(byMultikey).isNotNull();
        assertThat(byIid).isNotNull();

        // All four paths resolve to the same algorithm instance.
        assertThat(byCose).isSameAs(byVarsig).isSameAs(byMultikey).isSameAs(byIid);

        // The algorithm carries the metadata read off the seed manifest.
        assertThat(byCose.coseId()).isEqualTo(Signing.Ed25519.COSE_ID);
        assertThat(byCose.varsigCode()).isEqualTo(Signing.Ed25519.VARSIG_CODE);
        assertThat(byCose.multikeyCode()).isEqualTo(Signing.Ed25519.MULTIKEY_CODE);
        assertThat(byCose.iid()).isEqualTo(ItemRef.iid(Signing.Ed25519.KEY));
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
    @DisplayName("end-to-end: sign with the librarian's vault, verify through the algorithm-wrapped VarSig")
    void signAndVerifyThroughAlgorithm() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        byte[] message = "hello, common graph".getBytes();
        VarSig rawSignature = librarian.sign(message);

        // Re-decode the signature through the librarian — this is the wire-boundary
        // resolution that attaches the algorithm to the VarSig.
        VarSig wrapped = VarSig.decode(rawSignature.encoded(), librarian);
        assertThat(wrapped.algorithm()).isNotNull();

        // Similarly wrap the librarian's public key as a MultiKey with resolved algorithm.
        MultiKey publicKey = librarian.signingPublicKey().orElseThrow();
        MultiKey wrappedKey = MultiKey.decode(publicKey.encoded(), librarian);
        assertThat(wrappedKey.algorithm()).isNotNull();

        // Verify entirely through the wrapper — zero further lookups.
        assertThat(wrapped.verify(message, wrappedKey)).isTrue();
        assertThat(wrapped.verify("tampered".getBytes(), wrappedKey)).isFalse();
    }
}
