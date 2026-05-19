package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.identity.AlgorithmVocabulary;
import dev.everydaythings.graph.identity.algorithm.Algorithm;
import dev.everydaythings.graph.identity.algorithm.Signing;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignerTest {

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("Signer extends Item")
        void extendsItem() {
            Signer s = new Signer(ItemRef.random());
            assertThat(s).isInstanceOf(Item.class);
        }

        @Test
        @DisplayName("Signer carries an iid")
        void carriesIid() {
            ItemRef iid = ItemRef.fromString("test-signer");
            Signer s = new Signer(iid);
            assertThat(s.iid()).isEqualTo(iid);
        }

        @Test
        @DisplayName("Signer KEY is the archetype canonical key")
        void keyMatches() {
            assertThat(Signer.KEY).isEqualTo("cg.archetype:signer");
        }
    }

    @Nested
    @DisplayName("Identity-only construction")
    class IdentityOnly {

        @Test
        @DisplayName("identity-only Signer cannot sign")
        void cannotSign() {
            Signer s = new Signer(ItemRef.random());
            assertThat(s.canSign()).isFalse();
            assertThat(s.signingAlgorithm()).isEmpty();
            assertThat(s.signingPublicKey()).isEmpty();
        }

        @Test
        @DisplayName("sign() throws on identity-only Signer")
        void signThrows() {
            Signer s = new Signer(ItemRef.random());
            assertThatThrownBy(() -> s.sign("hello".getBytes()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("In-memory signing")
    class InMemorySigning {

        @Test
        @DisplayName("inMemory() produces a signing-capable Signer with Ed25519 keypair")
        void inMemoryHasSigningCapability() {
            Signer s = Signer.inMemory();
            assertThat(s.canSign()).isTrue();
            assertThat(s.signingAlgorithm()).contains(ItemRef.iid(Signing.Ed25519.KEY));
            assertThat(s.signingPublicKey()).isPresent();
        }

        @Test
        @DisplayName("publicKey() returns a 32-byte Ed25519 multikey")
        void publicKeyShape() {
            Signer s = Signer.inMemory();
            MultiKey pk = s.signingPublicKey().orElseThrow();
            assertThat(pk.code()).isEqualTo((int) Signing.Ed25519.MULTIKEY_CODE);
            assertThat(pk.rawKey()).hasSize(32);
        }

        @Test
        @DisplayName("sign() returns a varsig with Ed25519 codec and 64-byte signature")
        void signProducesVarsig() {
            Signer s = Signer.inMemory();
            VarSig sig = s.sign("hello world".getBytes());
            assertThat(sig.code()).isEqualTo((int) Signing.Ed25519.VARSIG_CODE);
            assertThat(sig.rawSig()).hasSize(64);
        }

        @Test
        @DisplayName("sign + verify round-trip succeeds for the original message")
        void signVerifyRoundtrip() {
            Signer s = Signer.inMemory();
            byte[] message = "the quick brown fox".getBytes();

            VarSig sig = s.sign(message);
            MultiKey pk = s.signingPublicKey().orElseThrow();

            assertThat(s.verify(pk, message, sig)).isTrue();
        }

        @Test
        @DisplayName("verify fails on a tampered message")
        void verifyFailsOnTampered() {
            Signer s = Signer.inMemory();
            byte[] message = "the quick brown fox".getBytes();
            byte[] tampered = "the quick brown FOX".getBytes();

            VarSig sig = s.sign(message);
            MultiKey pk = s.signingPublicKey().orElseThrow();

            assertThat(s.verify(pk, tampered, sig)).isFalse();
        }

        @Test
        @DisplayName("verify fails with a different signer's public key")
        void verifyFailsWithWrongKey() {
            Signer alice = Signer.inMemory();
            Signer bob = Signer.inMemory();
            byte[] message = "alice's message".getBytes();

            VarSig sig = alice.sign(message);
            MultiKey bobsKey = bob.signingPublicKey().orElseThrow();

            assertThat(alice.verify(bobsKey, message, sig)).isFalse();
        }

        @Test
        @DisplayName("each inMemory() Signer has a fresh independent keypair")
        void freshKeysEachTime() {
            Signer a = Signer.inMemory();
            Signer b = Signer.inMemory();
            assertThat(a.iid()).isNotEqualTo(b.iid());
            assertThat(a.signingPublicKey().orElseThrow().rawKey())
                    .isNotEqualTo(b.signingPublicKey().orElseThrow().rawKey());
        }

        @Test
        @DisplayName("Signer's IID is derived from its initial signing public key")
        void iidDerivedFromKey() {
            Signer s = Signer.inMemory();
            MultiKey pk = s.signingPublicKey().orElseThrow();
            // Recomputing the IID from the multikey-encoded bytes must match
            // the Signer's actual IID — this is the cryptographic binding
            // that closes the IID-preemption gap.
            assertThat(s.iid())
                    .isEqualTo(ItemRef.fromMultikeyBytes(pk.encoded()));
        }

        @Test
        @DisplayName("MultiKey from Signer round-trips via encode/decode")
        void multiKeyRoundtrip() {
            Signer s = Signer.inMemory();
            MultiKey original = s.signingPublicKey().orElseThrow();
            MultiKey decoded = MultiKey.decode(original.encoded());
            assertThat(decoded).isEqualTo(original);

            // Verify works with the round-tripped key.  The Signer's bound librarian
            // resolves the algorithm handle for the handle-less decoded MultiKey.
            byte[] message = "round-trip test".getBytes();
            VarSig sig = s.sign(message);
            assertThat(s.verify(decoded, message, sig)).isTrue();
        }

        @Test
        @DisplayName("VarSig from Signer round-trips via encode/decode")
        void varSigRoundtrip() {
            Signer s = Signer.inMemory();
            byte[] message = "round-trip test".getBytes();

            VarSig original = s.sign(message);
            VarSig decoded = VarSig.decode(original.encoded());
            assertThat(decoded).isEqualTo(original);

            assertThat(s.verify(s.signingPublicKey().orElseThrow(), message, decoded)).isTrue();
        }
    }
}
