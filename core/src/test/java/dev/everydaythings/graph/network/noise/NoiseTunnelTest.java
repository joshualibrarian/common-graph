package dev.everydaythings.graph.network.noise;

import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.vault.InMemoryVault;
import dev.everydaythings.graph.network.tunnel.LoopbackTunnel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Noise XX handshake + transport")
class NoiseTunnelTest {

    @Test
    @DisplayName("handshake completes; each side learns the peer's static X25519 key")
    void handshakeExchangesStaticKeys() throws Exception {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();

        LoopbackTunnel.Pair pair = LoopbackTunnel.pair();

        // Responder side wired first so its onReceive is set before the initiator
        // synchronously delivers msg 0 through the LoopbackTunnel.
        CompletableFuture<NoiseTunnel> bobFut = NoiseTunnel.handshake(pair.b(), bob, NoiseTunnel.Role.RESPONDER);
        CompletableFuture<NoiseTunnel> aliceFut = NoiseTunnel.handshake(pair.a(), alice, NoiseTunnel.Role.INITIATOR);

        NoiseTunnel aliceTunnel = aliceFut.get(5, TimeUnit.SECONDS);
        NoiseTunnel bobTunnel = bobFut.get(5, TimeUnit.SECONDS);

        MultiKey alicePub = alice.keyAgreementPublicKey().orElseThrow();
        MultiKey bobPub = bob.keyAgreementPublicKey().orElseThrow();

        assertThat(aliceTunnel.counterparty()).contains(bobPub);
        assertThat(bobTunnel.counterparty()).contains(alicePub);
        assertThat(aliceTunnel.isConfidential()).isTrue();
        assertThat(aliceTunnel.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("bidirectional encrypted transport after handshake")
    void bidirectionalTransport() throws Exception {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();
        LoopbackTunnel.Pair pair = LoopbackTunnel.pair();

        CompletableFuture<NoiseTunnel> bobFut = NoiseTunnel.handshake(pair.b(), bob, NoiseTunnel.Role.RESPONDER);
        CompletableFuture<NoiseTunnel> aliceFut = NoiseTunnel.handshake(pair.a(), alice, NoiseTunnel.Role.INITIATOR);
        NoiseTunnel aliceTunnel = aliceFut.get(5, TimeUnit.SECONDS);
        NoiseTunnel bobTunnel = bobFut.get(5, TimeUnit.SECONDS);

        List<String> bobReceived = new ArrayList<>();
        List<String> aliceReceived = new ArrayList<>();
        bobTunnel.onReceive(b -> bobReceived.add(new String(b, StandardCharsets.UTF_8)));
        aliceTunnel.onReceive(b -> aliceReceived.add(new String(b, StandardCharsets.UTF_8)));

        aliceTunnel.send("hello bob".getBytes(StandardCharsets.UTF_8));
        bobTunnel.send("hi alice".getBytes(StandardCharsets.UTF_8));
        aliceTunnel.send("second message".getBytes(StandardCharsets.UTF_8));

        assertThat(bobReceived).containsExactly("hello bob", "second message");
        assertThat(aliceReceived).containsExactly("hi alice");
    }

    @Test
    @DisplayName("ciphertext on the wire differs from plaintext")
    void wireIsEncrypted() throws Exception {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();
        LoopbackTunnel.Pair pair = LoopbackTunnel.pair();

        // Wiretap the underlying tunnel on bob's side after handshake.
        CompletableFuture<NoiseTunnel> bobFut = NoiseTunnel.handshake(pair.b(), bob, NoiseTunnel.Role.RESPONDER);
        CompletableFuture<NoiseTunnel> aliceFut = NoiseTunnel.handshake(pair.a(), alice, NoiseTunnel.Role.INITIATOR);
        NoiseTunnel aliceTunnel = aliceFut.get(5, TimeUnit.SECONDS);
        NoiseTunnel bobTunnel = bobFut.get(5, TimeUnit.SECONDS);

        List<byte[]> wire = new ArrayList<>();
        // Swap bob's underlying onReceive to capture ciphertext before decryption.
        pair.b().onReceive(wire::add);

        byte[] plaintext = "this should not appear on the wire".getBytes(StandardCharsets.UTF_8);
        aliceTunnel.send(plaintext);

        assertThat(wire).hasSize(1);
        byte[] cipher = wire.get(0);
        assertThat(cipher).isNotEqualTo(plaintext);
        // AES-GCM adds a 16-byte tag.
        assertThat(cipher.length).isEqualTo(plaintext.length + 16);
        assertThat(new String(cipher, StandardCharsets.UTF_8))
                .doesNotContain("this should not appear");
    }
}
