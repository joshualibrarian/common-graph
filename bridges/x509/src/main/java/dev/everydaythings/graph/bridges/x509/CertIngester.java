package dev.everydaythings.graph.bridges.x509;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.ThematicRole;
import org.bouncycastle.openssl.PEMParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parse X.509 certificates into {@link IdentityVocabulary.Attestation
 * Attestation} bodies, optionally producing signed Frames with a local
 * Vault as the attesting signer.
 *
 * <p>The ingester is the wire-format bridge: X.509 in, CG-shaped record
 * out.  The cryptographic chain validation that PKI relies on (PKIX path
 * building, root trust store consultation, signature verification) is
 * <i>not</i> done here — it's the caller's responsibility, typically via
 * JSSE at TLS handshake time.  This class extracts the meaningful content
 * (subject pubkey, validity, purpose) and represents it in CG terms.
 *
 * <h2>Algorithm support</h2>
 *
 * <p>v1 supports Ed25519 subject keys only.  Certs whose public key is in a
 * family the rest of the codebase doesn't yet handle (ECDSA, RSA, ...)
 * throw a clean {@link UnsupportedKeyAlgorithmException} with the offending
 * algorithm name.  As {@link JcaAlgorithmHandle#decodePublicKey} extends to
 * other algorithms, the ingester extends with it.
 *
 * <h2>Attester resolution</h2>
 *
 * <p>The Attestation body's AGENT binding identifies who signed the cert
 * in X.509 terms (CA, peer, or the subject itself for self-signed certs).
 * Resolving the attester to a CG IID needs the issuer's public key:
 *
 * <ul>
 *   <li><b>Self-signed</b> (subject DN == issuer DN): attester IID =
 *       subject IID = the canonical IID derived from the subject's
 *       multikey-encoded pubkey.  The most common AID-cert shape.</li>
 *   <li><b>CA-signed, issuer cert provided</b>: attester IID derived from
 *       the issuer cert's pubkey.  Use the
 *       {@code ingest(cert, issuerCert, vault)} overload.</li>
 *   <li><b>CA-signed, issuer cert not provided</b>: AGENT binding is
 *       omitted.  Trust resolution won't have an issuer anchor in this
 *       record; the cert is recorded but unanchored.  Add the issuer cert
 *       in a subsequent ingestion call to link the chain.</li>
 * </ul>
 *
 * <h2>Future-compatible v1</h2>
 *
 * <p>The body shape leaves room for richer ingestion modes (see
 * {@link IdentityVocabulary.Attestation} docstring):
 *
 * <ul>
 *   <li>Optional preservation of the original X.509 DER bytes (as a SOURCE
 *       binding pointing at an opaque payload) for byte-exact round-trip.</li>
 *   <li>Optional second Record on the Frame carrying the original issuer's
 *       X.509 signature over the preserved bytes, for cryptographic
 *       verification independent of the local Librarian.</li>
 * </ul>
 *
 * <p>Neither is implemented in v1; both are additive — adding them later
 * doesn't change the body shape produced today.
 */
public final class CertIngester {

    private CertIngester() {}

    // ==================================================================================
    // Parsing — DER and PEM forms
    // ==================================================================================

    /**
     * Parse a single X.509 certificate from DER or single-cert PEM bytes.
     * Auto-detects format.
     */
    public static X509Certificate parse(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
        } catch (CertificateException e) {
            throw new CertIngestException("Failed to parse X.509 certificate", e);
        }
    }

    /**
     * Parse a PEM bundle (one or more {@code -----BEGIN CERTIFICATE-----}
     * blocks concatenated) into a list of certificates in document order.
     * BouncyCastle's {@link PEMParser} does the heavy lifting; this method
     * just iterates and converts to JDK {@link X509Certificate} for
     * consistency with the single-cert API.
     */
    public static List<X509Certificate> parsePemBundle(byte[] pemBytes) {
        Objects.requireNonNull(pemBytes, "pemBytes");
        List<X509Certificate> out = new ArrayList<>();
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(pemBytes), StandardCharsets.UTF_8);
             PEMParser parser = new PEMParser(reader)) {
            Object obj;
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            while ((obj = parser.readObject()) != null) {
                if (obj instanceof org.bouncycastle.cert.X509CertificateHolder holder) {
                    byte[] der = holder.getEncoded();
                    out.add((X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(der)));
                }
            }
        } catch (IOException | CertificateException e) {
            throw new CertIngestException("Failed to parse PEM bundle", e);
        }
        return out;
    }

    // ==================================================================================
    // Body extraction
    // ==================================================================================

    /**
     * Produce an {@link IdentityVocabulary.Attestation Attestation} body
     * from a parsed X.509 certificate.  Self-signed certs get
     * AGENT=subject; CA-signed certs get AGENT omitted (use the overload
     * with {@code issuerCert} to resolve it).
     */
    public static Body parseToBody(X509Certificate cert) {
        return parseToBody(cert, null);
    }

    /**
     * Produce an Attestation body, resolving AGENT from {@code issuerCert}
     * when the cert is CA-signed.  Passing {@code null} for issuerCert
     * means "do what you can without it" — same as {@link #parseToBody(X509Certificate)}.
     */
    public static Body parseToBody(X509Certificate cert, X509Certificate issuerCert) {
        Objects.requireNonNull(cert, "cert");

        MultiKey subjectKey = subjectMultiKey(cert.getPublicKey());
        ItemRef subjectIid = ItemRef.fromMultikeyBytes(subjectKey.encoded());

        List<Binding> bindings = new ArrayList<>();

        // AGENT — the attester (issuer of the cert in X.509 terms)
        boolean selfSigned = cert.getSubjectX500Principal()
                .equals(cert.getIssuerX500Principal());
        if (selfSigned) {
            bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), subjectIid));
        } else if (issuerCert != null) {
            MultiKey issuerKey = subjectMultiKey(issuerCert.getPublicKey());
            ItemRef issuerIid = ItemRef.fromMultikeyBytes(issuerKey.encoded());
            bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), issuerIid));
        }
        // else: AGENT omitted — unanchored attestation, trust resolver knows what that means.

        // THEME — the subject identity
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), subjectIid));

        // INSTRUMENT — the subject's pubkey (multikey-encoded, self-describing)
        bindings.add(new Binding(ItemRef.iid(ThematicRole.Instrument.KEY), subjectKey.encoded()));

        // PURPOSE — the key's cryptographic purpose (signing for Ed25519 in v1)
        ItemRef purpose = inferPurpose(cert);
        bindings.add(Binding.ref(ItemRef.iid(ThematicRole.Purpose.KEY), purpose));

        // ATTRIBUTE[VALIDITY_FROM] → notBefore
        bindings.add(Binding.qualified(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(IdentityVocabulary.ValidityFrom.KEY))),
                cert.getNotBefore().toInstant()));

        // ATTRIBUTE[VALIDITY_UNTIL] → notAfter
        bindings.add(Binding.qualified(
                ItemRef.iid(ThematicRole.Attribute.KEY),
                List.of(new CompoundKey.Sememe(ItemRef.iid(IdentityVocabulary.ValidityUntil.KEY))),
                cert.getNotAfter().toInstant()));

        // TIME — when we ingested
        bindings.add(new Binding(ItemRef.iid(ThematicRole.Time.KEY), Instant.now()));

        return Body.of(ItemRef.of(ItemRef.iid(IdentityVocabulary.Attestation.KEY)), bindings);
    }

    // ==================================================================================
    // Full ingestion — body + locally-signed record
    // ==================================================================================

    /**
     * Parse + sign: produces a complete Frame whose record is signed by
     * {@code attestingVault} (the local Librarian's "I ingested this at
     * time T" attestation).  See class docstring for the v1 trust model.
     */
    public static Frame ingest(X509Certificate cert, Vault attestingVault) {
        return ingest(cert, null, attestingVault);
    }

    /**
     * Parse + sign with an issuer cert provided for AGENT resolution.
     */
    public static Frame ingest(X509Certificate cert, X509Certificate issuerCert,
                               Vault attestingVault) {
        Objects.requireNonNull(attestingVault, "attestingVault");
        Body body = parseToBody(cert, issuerCert);
        byte[] payload = HashTree.signingPayload(body);
        VarSig sig = attestingVault.sign(payload);
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), sig);
        return Frame.of(body, List.of(record));
    }

    // ==================================================================================
    // Helpers — pubkey extraction, purpose inference
    // ==================================================================================

    private static MultiKey subjectMultiKey(PublicKey pubKey) {
        try {
            return Signing.toMultiKey(pubKey);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedKeyAlgorithmException(e.getMessage());
        }
    }

    /**
     * Infer the key's purpose from the cert's KeyUsage extension.  Today
     * everything maps to Signing (Ed25519's only practical use here);
     * proper KeyUsage interpretation lands when ECDSA / RSA support brings
     * keys that legitimately serve different purposes.
     */
    private static ItemRef inferPurpose(X509Certificate cert) {
        // KeyUsage bit 0 = digitalSignature
        // KeyUsage bit 2 = keyEncipherment
        // KeyUsage bit 4 = keyAgreement
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            if (keyUsage.length > 4 && keyUsage[4]) {
                return ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);
            }
            if (keyUsage.length > 2 && keyUsage[2]) {
                return ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);
            }
        }
        // No KeyUsage extension, or only digitalSignature set — default Signing.
        return ItemRef.iid(IdentityVocabulary.Signing.KEY);
    }

    // ==================================================================================
    // Errors
    // ==================================================================================

    /** Thrown when cert parsing or ingestion fails. */
    public static final class CertIngestException extends RuntimeException {
        public CertIngestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown when the cert's public key uses an algorithm we don't yet handle. */
    public static final class UnsupportedKeyAlgorithmException extends RuntimeException {
        public UnsupportedKeyAlgorithmException(String message) {
            super(message);
        }
    }
}
