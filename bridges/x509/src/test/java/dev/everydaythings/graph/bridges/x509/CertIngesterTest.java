package dev.everydaythings.graph.bridges.x509;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.vault.InMemoryVault;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.language.ThematicRole;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CertIngester}.  Generates fresh Ed25519 self-signed
 * certs in-test via BouncyCastle so the fixtures are deterministic per
 * run and don't need to be checked in.
 */
class CertIngesterTest {

    private KeyPair subjectKeyPair;
    private X509Certificate selfSignedCert;
    private Instant notBefore;
    private Instant notAfter;

    @BeforeEach
    void setUp() throws Exception {
        subjectKeyPair = generateEd25519KeyPair();
        notBefore = Instant.now().minus(1, ChronoUnit.HOURS);
        notAfter = notBefore.plus(365, ChronoUnit.DAYS);
        selfSignedCert = buildSelfSignedCert(subjectKeyPair, "CN=test-subject",
                notBefore, notAfter);
    }

    @Nested
    @DisplayName("Body shape")
    class BodyShape {

        @Test
        @DisplayName("self-signed cert produces an Attestation body with subject = issuer")
        void selfSignedShape() {
            Body body = CertIngester.parseToBody(selfSignedCert);

            assertThat(body.headRef())
                    .isEqualTo(ItemRef.iid(IdentityVocabulary.Attestation.KEY));

            // AGENT and THEME should both be the subject IID (self-signed)
            ItemRef agent = bindingTargetAs(body, ThematicRole.Agent.KEY, ItemRef.class).orElseThrow();
            ItemRef theme = bindingTargetAs(body, ThematicRole.Theme.KEY, ItemRef.class).orElseThrow();
            assertThat(agent).isEqualTo(theme);
        }

        @Test
        @DisplayName("INSTRUMENT carries the subject pubkey as multikey-encoded bytes")
        void instrumentCarriesMultikey() {
            Body body = CertIngester.parseToBody(selfSignedCert);

            byte[] instrumentBytes = bindingTargetAs(body, ThematicRole.Instrument.KEY, byte[].class)
                    .orElseThrow();
            // The bytes should decode as a MultiKey with the Ed25519 codec.
            MultiKey decoded = MultiKey.decode(instrumentBytes);
            assertThat(decoded.code())
                    .as("multikey codec for Ed25519 is 0xed")
                    .isEqualTo(0xed);
            assertThat(decoded.rawKey().length)
                    .as("Ed25519 raw pubkey is 32 bytes")
                    .isEqualTo(32);
        }

        @Test
        @DisplayName("PURPOSE defaults to @signing for Ed25519 certs")
        void purposeDefaultsToSigning() {
            Body body = CertIngester.parseToBody(selfSignedCert);

            ItemRef purpose = bindingTargetAs(body, ThematicRole.Purpose.KEY, ItemRef.class)
                    .orElseThrow();
            assertThat(purpose).isEqualTo(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        }

        @Test
        @DisplayName("ATTRIBUTE[ValidityFrom/Until] carry the cert's notBefore/notAfter")
        void validityWindow() {
            Body body = CertIngester.parseToBody(selfSignedCert);

            Instant validFrom = compoundBindingTarget(body, ThematicRole.Attribute.KEY,
                    IdentityVocabulary.ValidityFrom.KEY, Instant.class);
            Instant validUntil = compoundBindingTarget(body, ThematicRole.Attribute.KEY,
                    IdentityVocabulary.ValidityUntil.KEY, Instant.class);

            // X.509 has second-level resolution; allow a 1s window.
            assertThat(validFrom).isCloseTo(notBefore, within1Second());
            assertThat(validUntil).isCloseTo(notAfter, within1Second());
        }

        @Test
        @DisplayName("TIME records when ingestion happened")
        void timeRecordsIngestionMoment() {
            Instant before = Instant.now().minusSeconds(1);
            Body body = CertIngester.parseToBody(selfSignedCert);
            Instant after = Instant.now().plusSeconds(1);

            Instant time = bindingTargetAs(body, ThematicRole.Time.KEY, Instant.class)
                    .orElseThrow();
            assertThat(time).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("Issuer resolution")
    class IssuerResolution {

        @Test
        @DisplayName("CA-signed cert without issuerCert omits AGENT (unanchored)")
        void caSignedNoIssuerOmitsAgent() throws Exception {
            // Generate a CA cert and a leaf cert signed by it.
            KeyPair caKeyPair = generateEd25519KeyPair();
            X509Certificate caCert = buildSelfSignedCert(caKeyPair, "CN=test-ca",
                    notBefore, notAfter);

            KeyPair leafKeyPair = generateEd25519KeyPair();
            X509Certificate leafCert = buildSignedCert(
                    leafKeyPair, "CN=test-leaf",
                    caKeyPair, "CN=test-ca",
                    notBefore, notAfter);

            Body body = CertIngester.parseToBody(leafCert);   // no issuer context

            // AGENT should be absent.
            Optional<ItemRef> agent = bindingTargetAs(body, ThematicRole.Agent.KEY, ItemRef.class);
            assertThat(agent).as("unanchored attestation").isEmpty();
        }

        @Test
        @DisplayName("CA-signed cert with issuerCert resolves AGENT to issuer's pubkey-derived IID")
        void caSignedWithIssuerResolves() throws Exception {
            KeyPair caKeyPair = generateEd25519KeyPair();
            X509Certificate caCert = buildSelfSignedCert(caKeyPair, "CN=test-ca",
                    notBefore, notAfter);

            KeyPair leafKeyPair = generateEd25519KeyPair();
            X509Certificate leafCert = buildSignedCert(
                    leafKeyPair, "CN=test-leaf",
                    caKeyPair, "CN=test-ca",
                    notBefore, notAfter);

            Body body = CertIngester.parseToBody(leafCert, caCert);

            ItemRef agent = bindingTargetAs(body, ThematicRole.Agent.KEY, ItemRef.class).orElseThrow();
            ItemRef theme = bindingTargetAs(body, ThematicRole.Theme.KEY, ItemRef.class).orElseThrow();
            assertThat(agent).as("agent is the issuer, not the subject").isNotEqualTo(theme);

            // Agent should equal the CA's pubkey-derived IID.  Compute it the same way the
            // ingester does and compare.
            Body caBody = CertIngester.parseToBody(caCert);
            ItemRef caSubject = bindingTargetAs(caBody, ThematicRole.Theme.KEY, ItemRef.class)
                    .orElseThrow();
            assertThat(agent).isEqualTo(caSubject);
        }
    }

    @Nested
    @DisplayName("Full ingestion (signed Frame)")
    class FullIngestion {

        @Test
        @DisplayName("ingest produces a Frame whose record is signed by the local Vault")
        void ingestProducesSignedFrame() {
            Vault localVault = InMemoryVault.generate();
            Frame frame = CertIngester.ingest(selfSignedCert, localVault);

            assertThat(frame.body().headRef())
                    .isEqualTo(ItemRef.iid(IdentityVocabulary.Attestation.KEY));
            assertThat(frame.records()).hasSize(1);
            assertThat(frame.records().get(0).signature()).isNotNull();
        }
    }

    @Nested
    @DisplayName("PEM parsing")
    class PemParsing {

        @Test
        @DisplayName("PEM bundle round-trips through parsePemBundle")
        void pemBundleRoundTrip() throws Exception {
            // Encode the existing self-signed cert as PEM and re-parse.
            String pem = "-----BEGIN CERTIFICATE-----\n"
                    + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                            .encodeToString(selfSignedCert.getEncoded())
                    + "\n-----END CERTIFICATE-----\n";
            java.util.List<X509Certificate> parsed =
                    CertIngester.parsePemBundle(pem.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThat(parsed).hasSize(1);
            assertThat(parsed.get(0).getSubjectX500Principal())
                    .isEqualTo(selfSignedCert.getSubjectX500Principal());
        }

        @Test
        @DisplayName("DER bytes parse directly via parse()")
        void derParsesDirectly() throws Exception {
            X509Certificate reparsed = CertIngester.parse(selfSignedCert.getEncoded());
            assertThat(reparsed.getSubjectX500Principal())
                    .isEqualTo(selfSignedCert.getSubjectX500Principal());
        }
    }

    @Nested
    @DisplayName("Algorithm support boundary")
    class AlgorithmSupport {

        @Test
        @DisplayName("non-Ed25519 cert throws UnsupportedKeyAlgorithmException")
        void rsaCertThrows() throws Exception {
            // Generate an RSA self-signed cert and try to ingest it.
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair rsaKeyPair = gen.generateKeyPair();

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    new X500Principal("CN=rsa-test"),
                    BigInteger.ONE,
                    Date.from(notBefore),
                    Date.from(notAfter),
                    new X500Principal("CN=rsa-test"),
                    rsaKeyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .build(rsaKeyPair.getPrivate());
            X509Certificate rsaCert = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

            assertThatThrownBy(() -> CertIngester.parseToBody(rsaCert))
                    .isInstanceOf(CertIngester.UnsupportedKeyAlgorithmException.class)
                    .hasMessageContaining("RSA");
        }
    }

    // ==================================================================================
    // Helpers — keypair + cert generation
    // ==================================================================================

    private static KeyPair generateEd25519KeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        return gen.generateKeyPair();
    }

    private static X509Certificate buildSelfSignedCert(KeyPair keyPair, String dn,
                                                       Instant notBefore, Instant notAfter)
            throws Exception {
        return buildSignedCert(keyPair, dn, keyPair, dn, notBefore, notAfter);
    }

    private static X509Certificate buildSignedCert(KeyPair subjectKeyPair, String subjectDn,
                                                   KeyPair issuerKeyPair, String issuerDn,
                                                   Instant notBefore, Instant notAfter)
            throws Exception {
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Principal(issuerDn),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(notBefore),
                Date.from(notAfter),
                new X500Principal(subjectDn),
                subjectKeyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("Ed25519")
                .build(issuerKeyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));
    }

    // ==================================================================================
    // Helpers — body inspection
    // ==================================================================================

    private static <T> Optional<T> bindingTargetAs(Body body, String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Object entry : body.entries()) {
            if (!(entry instanceof Binding b)) continue;
            if (!role.equals(b.role())) continue;
            if (!b.qualifiers().isEmpty()) continue;
            Object target = b.target();
            if (target != null && type.isInstance(target)) {
                return Optional.of(type.cast(target));
            }
        }
        return Optional.empty();
    }

    private static <T> T compoundBindingTarget(Body body, String roleKey, String qualifierKey,
                                               Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        ItemRef qual = ItemRef.iid(qualifierKey);
        for (Object entry : body.entries()) {
            if (!(entry instanceof Binding b)) continue;
            if (!role.equals(b.role())) continue;
            boolean matches = b.qualifiers().stream()
                    .anyMatch(q -> q instanceof CompoundKey.Sememe s && qual.equals(s.id()));
            if (!matches) continue;
            Object target = b.target();
            if (target != null && type.isInstance(target)) {
                return type.cast(target);
            }
        }
        throw new AssertionError("No " + roleKey + "[" + qualifierKey + "] binding found");
    }

    private static org.assertj.core.data.TemporalUnitOffset within1Second() {
        return org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS);
    }
}
