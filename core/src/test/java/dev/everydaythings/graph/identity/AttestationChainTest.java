package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.vault.InMemoryVault;
import dev.everydaythings.graph.identity.vault.Vault;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the mechanical attestation chain walker.
 *
 * <p>The setup: three identities (Alice, Bob, Carol) with separate vaults.
 * Carol attests Bob's key.  Bob attests Alice's key.  Walking from Alice
 * should produce the chain Carol→Bob→Alice (or, depending on what we ask
 * for: starting at Alice, we find Bob's attestation; from Bob, we find
 * Carol's attestation).
 */
class AttestationChainTest {

    @Nested
    @DisplayName("Empty graph")
    class EmptyGraph {

        @Test
        @DisplayName("walking an IID with no Attestations returns an empty chain")
        void noAttestations() {
            Librarian librarian = Librarian.inMemory();
            ItemRef someIid = ItemRef.iid("cg.test:no-such-identity");

            List<Frame> chain = AttestationChain.walk(librarian, someIid);

            assertThat(chain).isEmpty();
        }
    }

    @Nested
    @DisplayName("Linear chain walking")
    class LinearChain {

        @Test
        @DisplayName("two-hop chain: Bob attests Alice; walking from Alice finds Bob's attestation")
        void twoHopChain() {
            Librarian librarian = Librarian.inMemory();
            Vault aliceVault = InMemoryVault.generate();
            Vault bobVault = InMemoryVault.generate();

            // Bob attests Alice's key binding
            Frame bobAttestsAlice = Attestations.attest(
                    bobVault,
                    aliceVault.identity(),
                    aliceVault.signingPublicKey().orElseThrow(),
                    ItemRef.iid(IdentityVocabulary.Signing.KEY),
                    Instant.now(),
                    Instant.now().plus(1, ChronoUnit.HOURS));
            librarian.submit(bobAttestsAlice);

            List<Frame> chain = AttestationChain.walk(librarian, aliceVault.identity());

            assertThat(chain)
                    .as("chain contains Bob's attestation about Alice")
                    .hasSize(1);
            assertThat(Attestations.attester(chain.get(0).body()).orElseThrow())
                    .isEqualTo(bobVault.identity());
            assertThat(Attestations.subject(chain.get(0).body()).orElseThrow())
                    .isEqualTo(aliceVault.identity());
        }

        @Test
        @DisplayName("three-hop chain: Carol → Bob → Alice")
        void threeHopChain() {
            Librarian librarian = Librarian.inMemory();
            Vault aliceVault = InMemoryVault.generate();
            Vault bobVault = InMemoryVault.generate();
            Vault carolVault = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.plus(1, ChronoUnit.HOURS);

            // Bob attests Alice; Carol attests Bob
            librarian.submit(Attestations.attest(
                    bobVault, aliceVault.identity(),
                    aliceVault.signingPublicKey().orElseThrow(),
                    ItemRef.iid(IdentityVocabulary.Signing.KEY), from, until));
            librarian.submit(Attestations.attest(
                    carolVault, bobVault.identity(),
                    bobVault.signingPublicKey().orElseThrow(),
                    ItemRef.iid(IdentityVocabulary.Signing.KEY), from, until));

            List<Frame> chain = AttestationChain.walk(librarian, aliceVault.identity());

            assertThat(chain)
                    .as("chain contains both Bob's and Carol's attestations")
                    .hasSize(2);
            // First hop: Bob's attestation about Alice (entered first via DFS)
            assertThat(Attestations.attester(chain.get(0).body()).orElseThrow())
                    .isEqualTo(bobVault.identity());
            // Second hop: Carol's attestation about Bob
            assertThat(Attestations.attester(chain.get(1).body()).orElseThrow())
                    .isEqualTo(carolVault.identity());
            assertThat(Attestations.subject(chain.get(1).body()).orElseThrow())
                    .isEqualTo(bobVault.identity());
        }
    }

    @Nested
    @DisplayName("Cycle and depth handling")
    class CycleAndDepth {

        @Test
        @DisplayName("self-attestation (cycle) doesn't loop forever")
        void selfAttestationCycle() {
            Librarian librarian = Librarian.inMemory();
            Vault vault = InMemoryVault.generate();

            // selfSign — AGENT == THEME, walker would recurse on AGENT and revisit THEME
            librarian.submit(Attestations.selfSign(vault,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS)));

            List<Frame> chain = AttestationChain.walk(librarian, vault.identity());

            // The self-attestation is in the chain exactly once; recursion stops at
            // the visited-set check.
            assertThat(chain).hasSize(1);
        }

        @Test
        @DisplayName("depth limit stops descent")
        void depthLimit() {
            Librarian librarian = Librarian.inMemory();
            Vault a = InMemoryVault.generate();
            Vault b = InMemoryVault.generate();
            Vault c = InMemoryVault.generate();
            Vault d = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.plus(1, ChronoUnit.HOURS);

            // Chain: D → C → B → A
            librarian.submit(Attestations.attest(b, a.identity(),
                    a.signingPublicKey().orElseThrow(),
                    ItemRef.iid(IdentityVocabulary.Signing.KEY), from, until));
            librarian.submit(Attestations.attest(c, b.identity(),
                    b.signingPublicKey().orElseThrow(),
                    ItemRef.iid(IdentityVocabulary.Signing.KEY), from, until));
            librarian.submit(Attestations.attest(d, c.identity(),
                    c.signingPublicKey().orElseThrow(),
                    ItemRef.iid(IdentityVocabulary.Signing.KEY), from, until));

            // Walking from A with depth 1: only B's attestation is visited (depth 0
            // stops descent on B).
            List<Frame> shallow = AttestationChain.walk(librarian, a.identity(), 1);
            assertThat(shallow).hasSize(1);
            assertThat(Attestations.attester(shallow.get(0).body()).orElseThrow())
                    .isEqualTo(b.identity());

            // Walking with depth 8 reaches all three.
            List<Frame> deep = AttestationChain.walk(librarian, a.identity(), 8);
            assertThat(deep).hasSize(3);
        }

        @Test
        @DisplayName("negative depth rejected")
        void negativeDepthRejected() {
            Librarian librarian = Librarian.inMemory();
            assertThatThrownBy(() ->
                    AttestationChain.walk(librarian, ItemRef.iid("cg.test:x"), -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxDepth");
        }
    }

    @Nested
    @DisplayName("Frame filtering")
    class FrameFiltering {

        @Test
        @DisplayName("non-Attestation frames about the same IID are ignored")
        void nonAttestationsIgnored() {
            Librarian librarian = Librarian.inMemory();
            Vault aliceVault = InMemoryVault.generate();

            // Submit Alice's INCEPTION (different predicate, also has THEME = Alice).
            // The walker should NOT include it in the chain — only Attestation-headed
            // frames are followed.
            librarian.submit(aliceVault.incept(ItemRef.iid(IdentityVocabulary.Signing.KEY)));

            List<Frame> chain = AttestationChain.walk(librarian, aliceVault.identity());

            assertThat(chain).isEmpty();
        }
    }
}
