package dev.everydaythings.graph.bridges.keri;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.algorithm.Signing;
import io.ipfs.multihash.Multihash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KERI AID ↔ CG ItemRef compatibility.
 *
 * <p>The two identifier namespaces are byte-isomorphic: a KERI basic prefix
 * carrying a 32-byte Ed25519 public key has the same content as a CG ItemRef
 * built with multihash type {@code id} over those same bytes.  Self-addressing
 * AIDs (digest-based prefixes) map to ItemRefs under the matching hash
 * multihash type.
 */
@DisplayName("AID ↔ ItemRef mapping")
class AidMappingTest {

    @Test
    @DisplayName("Ed25519 basic prefix round-trips through ItemRef")
    void ed25519BasicPrefixRoundTrip() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair kp = ed.generateKeyPair();
        byte[] rawPub = ed.publicKeyToRaw(kp.getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        ItemRef ref = AidMapping.aidToItemRef(aid);

        assertThat(ref.hashType()).isEqualTo(Multihash.Type.id);
        assertThat(Multihash.deserialize(ref.multihash()).getHash()).isEqualTo(rawPub);

        String roundTripped = AidMapping.itemRefToAid(ref, MatterCode.ED25519);
        assertThat(roundTripped).isEqualTo(aid);
    }

    @Test
    @DisplayName("SHA2-256 self-addressing AID maps to sha2_256-typed ItemRef")
    void sha2SelfAddressingRoundTrip() {
        byte[] digest = new byte[32];
        for (int i = 0; i < 32; i++) digest[i] = (byte) (i * 7);

        String aid = Cesr.encodePrimitive(MatterCode.SHA2_256, digest);
        ItemRef ref = AidMapping.aidToItemRef(aid);

        assertThat(ref.hashType()).isEqualTo(Multihash.Type.sha2_256);
        assertThat(Multihash.deserialize(ref.multihash()).getHash()).isEqualTo(digest);

        String roundTripped = AidMapping.itemRefToAid(ref, MatterCode.SHA2_256);
        assertThat(roundTripped).isEqualTo(aid);
    }

    @Test
    @DisplayName("Blake3-256 AID maps to blake3-typed ItemRef")
    void blake3RoundTrip() {
        byte[] digest = new byte[32];
        for (int i = 0; i < 32; i++) digest[i] = (byte) i;

        String aid = Cesr.encodePrimitive(MatterCode.BLAKE3_256, digest);
        ItemRef ref = AidMapping.aidToItemRef(aid);

        assertThat(ref.hashType()).isEqualTo(Multihash.Type.blake3);
        assertThat(AidMapping.itemRefToAid(ref, MatterCode.BLAKE3_256)).isEqualTo(aid);
    }

    @Test
    @DisplayName("non-transferable prefix B also maps to id-multihash")
    void nonTransferableRoundTrip() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        byte[] rawPub = ed.publicKeyToRaw(ed.generateKeyPair().getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519_NT, rawPub);
        ItemRef ref = AidMapping.aidToItemRef(aid);

        assertThat(ref.hashType()).isEqualTo(Multihash.Type.id);
        // Round-tripping back through D vs B is lossy at the multihash level —
        // both share id-multihash.  Callers wanting to preserve the transfer
        // semantics must track them via a Frame binding, not the AID alone.
        String backAsD = AidMapping.itemRefToAid(ref, MatterCode.ED25519);
        assertThat(backAsD).startsWith("D");
    }

    @Test
    @DisplayName("unsupported matter code is rejected")
    void unsupportedCode() {
        byte[] sig = new byte[64];
        String sigQb64 = Cesr.encodePrimitive(MatterCode.ED25519_SIG, sig);
        assertThatThrownBy(() -> AidMapping.aidToItemRef(sigQb64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0B");
    }

    @Test
    @DisplayName("ItemRef text form stays available on AID-derived refs")
    void itemRefTextEncodingWorks() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        byte[] rawPub = ed.publicKeyToRaw(ed.generateKeyPair().getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        ItemRef ref = AidMapping.aidToItemRef(aid);

        String text = ref.encodeText();
        assertThat(text).isNotEmpty();
    }
}
