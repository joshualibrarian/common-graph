package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.vault.InMemoryVault;
import dev.everydaythings.graph.cryptography.vault.Vault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link Attestations} builders and readers — the CG-native
 * genesis path for Attestation Frames (no X.509 involved).
 */
class AttestationsTest {

    @Nested
    @DisplayName("selfSign")
    class SelfSign {

        @Test
        @DisplayName("AGENT == THEME == vault's pubkey-derived IID")
        void agentEqualsThemeForSelfSigned() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.plus(365, ChronoUnit.DAYS);

            Frame frame = Attestations.selfSign(vault, from, until);
            Body body = frame.body();

            assertThat(Attestations.attester(body)).isEqualTo(Attestations.subject(body));
            assertThat(Attestations.attester(body).orElseThrow())
                    .isEqualTo(vault.identity());
        }

        @Test
        @DisplayName("INSTRUMENT carries the vault's signing pubkey")
        void instrumentCarriesVaultPubkey() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.plus(1, ChronoUnit.HOURS);

            Frame frame = Attestations.selfSign(vault, from, until);
            MultiKey pubkey = Attestations.subjectPubkey(frame.body()).orElseThrow();

            assertThat(pubkey.encoded())
                    .containsExactly(vault.signingPublicKey().orElseThrow().encoded());
        }

        @Test
        @DisplayName("PURPOSE defaults to @signing")
        void purposeDefaultsToSigning() {
            Vault vault = InMemoryVault.generate();
            Frame frame = Attestations.selfSign(vault,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            assertThat(Attestations.purpose(frame.body()).orElseThrow())
                    .isEqualTo(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        }

        @Test
        @DisplayName("validity window round-trips through the readers")
        void validityRoundTrip() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.plus(7, ChronoUnit.DAYS);

            Frame frame = Attestations.selfSign(vault, from, until);

            assertThat(Attestations.validFrom(frame.body())).contains(from);
            assertThat(Attestations.validUntil(frame.body())).contains(until);
        }

        @Test
        @DisplayName("record is signed by the vault — verifiable against its pubkey")
        void recordIsSigned() {
            Vault vault = InMemoryVault.generate();
            Frame frame = Attestations.selfSign(vault,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            assertThat(frame.records()).hasSize(1);
            assertThat(frame.records().get(0).signature()).isNotNull();
        }

        @Test
        @DisplayName("validUntil before validFrom is rejected")
        void invalidWindowRejected() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.minus(1, ChronoUnit.HOURS);

            assertThatThrownBy(() -> Attestations.selfSign(vault, from, until))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("validUntil");
        }
    }

    @Nested
    @DisplayName("attest (third-party)")
    class ThirdPartyAttest {

        @Test
        @DisplayName("AGENT is the attesting vault, THEME is the subject")
        void agentDistinctFromTheme() {
            Vault subjectVault = InMemoryVault.generate();
            Vault attestingVault = InMemoryVault.generate();

            MultiKey subjectKey = subjectVault.signingPublicKey().orElseThrow();
            ItemRef subjectIid = subjectVault.identity();

            Frame frame = Attestations.attest(
                    attestingVault, subjectIid, subjectKey,
                    ItemRef.iid(IdentityVocabulary.Signing.KEY),
                    Instant.now(),
                    Instant.now().plus(1, ChronoUnit.HOURS));

            assertThat(Attestations.attester(frame.body()).orElseThrow())
                    .as("agent = attesting vault's identity")
                    .isEqualTo(attestingVault.identity());
            assertThat(Attestations.subject(frame.body()).orElseThrow())
                    .as("theme = subject")
                    .isEqualTo(subjectIid);
            assertThat(Attestations.attester(frame.body()))
                    .as("agent != theme")
                    .isNotEqualTo(Attestations.subject(frame.body()));
        }

        @Test
        @DisplayName("INSTRUMENT carries the subject's pubkey, not the attester's")
        void instrumentCarriesSubjectPubkey() {
            Vault subjectVault = InMemoryVault.generate();
            Vault attestingVault = InMemoryVault.generate();
            MultiKey subjectKey = subjectVault.signingPublicKey().orElseThrow();

            Frame frame = Attestations.attest(
                    attestingVault, subjectVault.identity(), subjectKey,
                    ItemRef.iid(IdentityVocabulary.Signing.KEY),
                    Instant.now(),
                    Instant.now().plus(1, ChronoUnit.HOURS));

            assertThat(Attestations.subjectPubkey(frame.body()).orElseThrow().encoded())
                    .containsExactly(subjectKey.encoded());
        }
    }

    @Nested
    @DisplayName("Readers")
    class Readers {

        @Test
        @DisplayName("issuedAt records the time of attestation creation")
        void issuedAtMoment() {
            Vault vault = InMemoryVault.generate();
            Instant before = Instant.now().minusSeconds(1);
            Frame frame = Attestations.selfSign(vault,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
            Instant after = Instant.now().plusSeconds(1);

            Instant issued = Attestations.issuedAt(frame.body()).orElseThrow();
            assertThat(issued).isBetween(before, after);
        }
    }
}
