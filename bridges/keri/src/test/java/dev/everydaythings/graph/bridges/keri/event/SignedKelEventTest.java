package dev.everydaythings.graph.bridges.keri.event;

import dev.everydaythings.graph.bridges.keri.Cesr;
import dev.everydaythings.graph.bridges.keri.MatterCode;
import dev.everydaythings.graph.identity.algorithm.Signing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end sign + verify of a KERI inception event using the project's
 * existing {@link Signing.Ed25519} primitive.  This proves the KEL wire
 * format is wire-compatible with CG's signature surface.
 */
@DisplayName("Signed KEL event sign + verify")
class SignedKelEventTest {

    @Test
    @DisplayName("sign inception, attach, parse back, verify")
    void signAndVerifyInception() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair kp = ed.generateKeyPair();
        byte[] rawPub = ed.publicKeyToRaw(kp.getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        String nextDigest = Cesr.encodePrimitive(
                MatterCode.SHA2_256, new byte[32]);

        byte[] eventBytes = KelJson.encode(KelEvents.inception(
                aid, List.of(aid), List.of(nextDigest), "1", "1"));

        byte[] signature = ed.sign(eventBytes, kp.getPrivate());
        IndexedSignature indexedSig = new IndexedSignature(0, signature);
        SignatureAttachment attachment = new SignatureAttachment(List.of(indexedSig));
        SignedKelEvent signed = new SignedKelEvent(eventBytes, attachment);

        byte[] wire = signed.toWire();
        SignedKelEvent parsed = SignedKelEvent.parseWire(wire);

        assertThat(parsed.eventBytes()).isEqualTo(eventBytes);
        assertThat(parsed.attachment().signatures()).hasSize(1);

        IndexedSignature recoveredSig = parsed.attachment().signatures().get(0);
        assertThat(recoveredSig.index()).isEqualTo(0);
        assertThat(recoveredSig.signature()).isEqualTo(signature);

        Map<String, Object> event = parsed.event();
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) event.get("k");
        Cesr.Primitive signerKey = Cesr.decodePrimitive(keys.get(recoveredSig.index()));
        PublicKey jcaPub = ed.decodePublicKey(signerKey.raw());

        boolean verified = ed.verify(
                parsed.eventBytes(), recoveredSig.signature(), jcaPub);
        assertThat(verified).isTrue();
    }

    @Test
    @DisplayName("tampered event bytes fail verification")
    void tamperedEventFailsVerification() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair kp = ed.generateKeyPair();
        byte[] rawPub = ed.publicKeyToRaw(kp.getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        String nextDigest = Cesr.encodePrimitive(
                MatterCode.SHA2_256, new byte[32]);

        byte[] eventBytes = KelJson.encode(KelEvents.inception(
                aid, List.of(aid), List.of(nextDigest), "1", "1"));
        byte[] signature = ed.sign(eventBytes, kp.getPrivate());

        byte[] tampered = eventBytes.clone();
        // Find a "kt":"1" field and flip threshold to "2" (same length, SAID
        // becomes wrong but signature was over the original bytes).
        for (int i = 0; i < tampered.length - 7; i++) {
            if (tampered[i] == '"' && tampered[i + 1] == 'k' && tampered[i + 2] == 't'
                    && tampered[i + 3] == '"' && tampered[i + 4] == ':'
                    && tampered[i + 5] == '"' && tampered[i + 6] == '1') {
                tampered[i + 6] = '2';
                break;
            }
        }

        boolean verified = ed.verify(tampered, signature, kp.getPublic());
        assertThat(verified).isFalse();
    }

    @Test
    @DisplayName("multiple signatures in one attachment round-trip")
    void multipleSignatures() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair k1 = ed.generateKeyPair();
        KeyPair k2 = ed.generateKeyPair();
        String aid1 = Cesr.encodePrimitive(MatterCode.ED25519, ed.publicKeyToRaw(k1.getPublic()));
        String aid2 = Cesr.encodePrimitive(MatterCode.ED25519, ed.publicKeyToRaw(k2.getPublic()));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, new byte[32]);

        byte[] eventBytes = KelJson.encode(KelEvents.inception(
                aid1, List.of(aid1, aid2), List.of(nextDigest, nextDigest), "2", "2"));

        byte[] sig1 = ed.sign(eventBytes, k1.getPrivate());
        byte[] sig2 = ed.sign(eventBytes, k2.getPrivate());
        SignatureAttachment attachment = new SignatureAttachment(List.of(
                new IndexedSignature(0, sig1),
                new IndexedSignature(1, sig2)));

        SignedKelEvent parsed = SignedKelEvent.parseWire(
                new SignedKelEvent(eventBytes, attachment).toWire());

        assertThat(parsed.attachment().signatures()).hasSize(2);
        assertThat(parsed.attachment().signatures().get(0).index()).isEqualTo(0);
        assertThat(parsed.attachment().signatures().get(1).index()).isEqualTo(1);
        assertThat(ed.verify(parsed.eventBytes(),
                parsed.attachment().signatures().get(0).signature(), k1.getPublic())).isTrue();
        assertThat(ed.verify(parsed.eventBytes(),
                parsed.attachment().signatures().get(1).signature(), k2.getPublic())).isTrue();
    }

    @Test
    @DisplayName("indexed signature qb64 round-trips standalone")
    void indexedSignatureRoundTrip() {
        byte[] sigBytes = new byte[64];
        for (int i = 0; i < 64; i++) sigBytes[i] = (byte) i;
        IndexedSignature sig = new IndexedSignature(5, sigBytes);

        String qb64 = sig.toQb64();
        assertThat(qb64).hasSize(88).startsWith("AF");

        IndexedSignature parsed = IndexedSignature.parse(qb64);
        assertThat(parsed.index()).isEqualTo(5);
        assertThat(parsed.signature()).isEqualTo(sigBytes);
    }

    @Test
    @DisplayName("attachment with maximum-index signature (63) round-trips")
    void maxIndexSignature() {
        byte[] sigBytes = new byte[64];
        IndexedSignature sig = new IndexedSignature(63, sigBytes);
        assertThat(sig.toQb64().charAt(1)).isEqualTo('_');
        assertThat(IndexedSignature.parse(sig.toQb64()).index()).isEqualTo(63);
    }
}
