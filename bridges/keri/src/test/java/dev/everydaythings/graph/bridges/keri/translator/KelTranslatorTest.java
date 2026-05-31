package dev.everydaythings.graph.bridges.keri.translator;

import dev.everydaythings.graph.bridges.keri.Cesr;
import dev.everydaythings.graph.bridges.keri.MatterCode;
import dev.everydaythings.graph.bridges.keri.event.KelEvents;
import dev.everydaythings.graph.bridges.keri.event.KelJson;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KEL event ↔ CG Body translation tests for inception.
 */
@DisplayName("KEL ↔ Body translator")
class KelTranslatorTest {

    @Test
    @DisplayName("KEL inception → INCEPTION body with canonical bindings")
    void kelToBodyShape() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair kp = ed.generateKeyPair();
        byte[] rawPub = ed.publicKeyToRaw(kp.getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        byte[] nextDigestBytes = new byte[32];
        for (int i = 0; i < 32; i++) nextDigestBytes[i] = (byte) (i * 11);
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, nextDigestBytes);

        Map<String, Object> event = KelEvents.inception(
                aid, List.of(aid), List.of(nextDigest), "1", "1");

        Body body = KelTranslator.bodyFromInception(event);

        assertThat(body.head()).isEqualTo(ItemRef.iid(IdentityVocabulary.Inception.KEY));
        Binding themeBinding = bindingFor(body, ThematicRole.Theme.KEY, null);
        assertThat(themeBinding.target()).isInstanceOf(ItemRef.class);

        Binding purposeBinding = bindingFor(body, ThematicRole.Purpose.KEY, null);
        assertThat(purposeBinding.target())
                .isEqualTo(ItemRef.iid(IdentityVocabulary.Signing.KEY));

        Binding multikey = bindingFor(body, ThematicRole.Instrument.KEY,
                EncryptionVocabulary.Multikey.KEY);
        assertThat(multikey.target()).isInstanceOf(byte[].class);
        MultiKey decoded = MultiKey.decode((byte[]) multikey.target());
        assertThat(decoded.rawKey()).isEqualTo(rawPub);

        Binding next = bindingFor(body, ThematicRole.Instrument.KEY,
                IdentityVocabulary.Next.KEY);
        assertThat(next.target()).isInstanceOf(ContentRef.class);
    }

    @Test
    @DisplayName("body → KEL inception preserves AID, signing key, next digest")
    void bodyToKelRoundTrip() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair kp = ed.generateKeyPair();
        byte[] rawPub = ed.publicKeyToRaw(kp.getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        byte[] nextDigestBytes = new byte[32];
        for (int i = 0; i < 32; i++) nextDigestBytes[i] = (byte) (255 - i);
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, nextDigestBytes);

        Map<String, Object> original = KelEvents.inception(
                aid, List.of(aid), List.of(nextDigest), "1", "1");

        Body body = KelTranslator.bodyFromInception(original);
        Map<String, Object> recovered = KelTranslator.inceptionFromBody(body);

        assertThat(recovered.get("i")).isEqualTo(aid);
        assertThat(recovered.get("k")).isEqualTo(List.of(aid));
        assertThat(recovered.get("n")).isEqualTo(List.of(nextDigest));
        assertThat(recovered.get("t")).isEqualTo("icp");
    }

    @Test
    @DisplayName("full wire round-trip: encoded JSON → Body → encoded JSON")
    void wireRoundTripThroughBody() {
        Signing.Ed25519 ed = Signing.Ed25519.builtin();
        KeyPair kp = ed.generateKeyPair();
        byte[] rawPub = ed.publicKeyToRaw(kp.getPublic());

        String aid = Cesr.encodePrimitive(MatterCode.ED25519, rawPub);
        byte[] nextDigestBytes = new byte[32];
        for (int i = 0; i < 32; i++) nextDigestBytes[i] = (byte) i;
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, nextDigestBytes);

        byte[] originalWire = KelJson.encode(KelEvents.inception(
                aid, List.of(aid), List.of(nextDigest), "1", "1"));

        Body body = KelTranslator.bodyFromInception(KelJson.decode(originalWire));
        byte[] recoveredWire = KelJson.encode(KelTranslator.inceptionFromBody(body));

        assertThat(recoveredWire).isEqualTo(originalWire);
    }

    @Test
    @DisplayName("non-inception event is rejected by bodyFromInception")
    void rejectsNonInception() {
        String aid = Cesr.encodePrimitive(MatterCode.ED25519, new byte[32]);
        String priorD = Cesr.encodePrimitive(MatterCode.SHA2_256, new byte[32]);
        Map<String, Object> rot = KelEvents.rotation(
                aid, 1, priorD, List.of(aid), List.of(priorD), "1", "1");

        assertThatThrownBy(() -> KelTranslator.bodyFromInception(rot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("icp");
    }

    @Test
    @DisplayName("non-INCEPTION body is rejected by inceptionFromBody")
    void rejectsNonInceptionBody() {
        Body wrongHead = Body.of(ItemRef.of(ItemRef.iid("cg:sememe:rotation")), List.of());
        assertThatThrownBy(() -> KelTranslator.inceptionFromBody(wrongHead))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INCEPTION");
    }

    @Test
    @DisplayName("v1 rejects multi-signing-key inceptions")
    void rejectsMultiKey() {
        String aidA = Cesr.encodePrimitive(MatterCode.ED25519, new byte[32]);
        byte[] keyBBytes = new byte[32];
        keyBBytes[0] = 1;
        String aidB = Cesr.encodePrimitive(MatterCode.ED25519, keyBBytes);
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, new byte[32]);

        Map<String, Object> twoKeys = KelEvents.inception(
                aidA, List.of(aidA, aidB), List.of(nextDigest, nextDigest), "2", "2");

        assertThatThrownBy(() -> KelTranslator.bodyFromInception(twoKeys))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one signing key");
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Binding bindingFor(Body body, String roleKey, String qualifierKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : body.bindings()) {
            if (!b.role().equals(role)) continue;
            if (qualifierKey == null) {
                if (b.qualifiers().isEmpty()) return b;
            } else if (b.qualifiers().size() == 1
                    && b.qualifiers().get(0) instanceof CompoundKey.Sememe s
                    && s.id().equals(ItemRef.iid(qualifierKey))) {
                return b;
            }
        }
        throw new AssertionError("no binding found for role=" + roleKey
                + " qualifier=" + qualifierKey);
    }
}
