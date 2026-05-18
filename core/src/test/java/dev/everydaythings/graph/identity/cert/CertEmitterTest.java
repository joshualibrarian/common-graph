package dev.everydaythings.graph.identity.cert;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.Attestations;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.jca.VaultProvider;
import dev.everydaythings.graph.identity.vault.InMemoryVault;
import dev.everydaythings.graph.identity.vault.Vault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CertEmitter} — the Attestation Frame → X.509 wire-format
 * projection.  The round-trip test is the headline: Attestation → X.509 →
 * back-to-Attestation via {@link CertIngester} preserves the meaningful
 * fields.
 */
class CertEmitterTest {

    @BeforeAll
    static void installProvider() {
        VaultProvider.install();
    }

    @AfterAll
    static void uninstallProvider() {
        VaultProvider.uninstall();
    }

    @Nested
    @DisplayName("X.509 emission")
    class Emission {

        @Test
        @DisplayName("self-signed Attestation emits a valid X.509 cert whose signature verifies")
        void selfSignedRoundTrip() throws Exception {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now();
            Instant until = from.plus(365, ChronoUnit.DAYS);

            Frame attestation = Attestations.selfSign(vault, from, until);
            X509Certificate cert = CertEmitter.toX509(attestation, vault);

            // verify() throws if the signature is invalid against the supplied pubkey.
            // For a self-signed cert, the cert's pubkey is its own issuer's pubkey.
            cert.verify(cert.getPublicKey());

            assertThat(cert.getSubjectX500Principal().getName())
                    .as("subject DN encodes the IID")
                    .contains(vault.identity().encodeText());
            assertThat(cert.getIssuerX500Principal())
                    .as("self-signed: issuer == subject")
                    .isEqualTo(cert.getSubjectX500Principal());
        }

        @Test
        @DisplayName("cert's notBefore/notAfter match the Attestation's validity window")
        void validityWindowProjected() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant until = from.plus(30, ChronoUnit.DAYS);

            Frame attestation = Attestations.selfSign(vault, from, until);
            X509Certificate cert = CertEmitter.toX509(attestation, vault);

            assertThat(cert.getNotBefore()).isEqualTo(Date.from(from));
            assertThat(cert.getNotAfter()).isEqualTo(Date.from(until));
        }

        @Test
        @DisplayName("cert's public key matches the Attestation's INSTRUMENT pubkey")
        void publicKeyProjected() {
            Vault vault = InMemoryVault.generate();
            Frame attestation = Attestations.selfSign(vault,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
            X509Certificate cert = CertEmitter.toX509(attestation, vault);

            MultiKey vaultKey = vault.signingPublicKey().orElseThrow();
            assertThat(cert.getPublicKey().getEncoded())
                    .as("cert's pubkey is the Vault's signing pubkey")
                    .containsExactly(vaultKey.publicKey().getEncoded());
        }

        @Test
        @DisplayName("cert uses Ed25519 signature algorithm")
        void signatureAlgorithm() {
            Vault vault = InMemoryVault.generate();
            Frame attestation = Attestations.selfSign(vault,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
            X509Certificate cert = CertEmitter.toX509(attestation, vault);

            assertThat(cert.getSigAlgName()).containsIgnoringCase("Ed25519");
        }

        @Test
        @DisplayName("serial number is non-zero and stable across re-emissions of the same Frame")
        void serialDerivedFromDatumId() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant until = from.plus(1, ChronoUnit.HOURS);
            Frame attestation = Attestations.selfSign(vault, from, until);

            X509Certificate first = CertEmitter.toX509(attestation, vault);
            X509Certificate second = CertEmitter.toX509(attestation, vault);

            assertThat(first.getSerialNumber()).isNotZero();
            assertThat(first.getSerialNumber()).isEqualTo(second.getSerialNumber());
        }
    }

    @Nested
    @DisplayName("Round-trip through CertIngester")
    class RoundTrip {

        @Test
        @DisplayName("Attestation → X.509 → Attestation preserves subject, pubkey, validity, purpose")
        void roundTripPreservesContent() {
            Vault vault = InMemoryVault.generate();
            Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant until = from.plus(90, ChronoUnit.DAYS);

            Frame original = Attestations.selfSign(vault, from, until);
            X509Certificate cert = CertEmitter.toX509(original, vault);
            Frame reingested = CertIngester.ingest(cert, vault);

            // Subject and attester should be identical (self-signed both directions).
            assertThat(Attestations.subject(reingested.body()))
                    .isEqualTo(Attestations.subject(original.body()));
            assertThat(Attestations.attester(reingested.body()))
                    .isEqualTo(Attestations.attester(original.body()));

            // Subject pubkey survives the multikey ↔ X.509 ↔ multikey round-trip.
            assertThat(Attestations.subjectPubkey(reingested.body()).orElseThrow().encoded())
                    .containsExactly(Attestations.subjectPubkey(original.body()).orElseThrow().encoded());

            // Validity window matches at second precision (X.509 truncates).
            assertThat(Attestations.validFrom(reingested.body())).contains(from);
            assertThat(Attestations.validUntil(reingested.body())).contains(until);

            // Purpose preserved.
            assertThat(Attestations.purpose(reingested.body()))
                    .isEqualTo(Attestations.purpose(original.body()));
        }
    }

    @Nested
    @DisplayName("Failure modes")
    class Failures {

        @Test
        @DisplayName("emitting a Frame whose body is not an Attestation throws")
        void nonAttestationBodyRejected() {
            Vault vault = InMemoryVault.generate();
            // Build an Inception frame instead — different head.
            Frame inception = vault.incept(ItemRef.iid(IdentityVocabulary.Signing.KEY));

            // Inception body has no INSTRUMENT-without-qualifier (its INSTRUMENT
            // bindings use Multikey/Next qualifiers), so subjectPubkey reader
            // returns empty and CertEmitter throws.
            assertThatThrownBy(() -> CertEmitter.toX509(inception, vault))
                    .isInstanceOf(CertEmitter.CertEmitterException.class)
                    .hasMessageContaining("INSTRUMENT");
        }
    }
}
