package dev.everydaythings.graph.identity.cert;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.Attestations;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.jca.VaultPrivateKey;
import dev.everydaythings.graph.identity.jca.VaultProvider;
import dev.everydaythings.graph.identity.vault.Vault;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Render an {@link IdentityVocabulary.Attestation Attestation} Frame as an
 * X.509 certificate for PKI-shaped consumers (TLS, foreign cert
 * exchanges).  This is the wire-format projection direction;
 * {@link CertIngester} goes the other way.
 *
 * <p>Why a separate signature: the Frame's record signs the CG body bytes;
 * the X.509 cert needs a signature over the X.509 {@code tbsCertificate}
 * bytes — different bytes, same Vault key.  The emitter requests a fresh
 * signature through the JCA Provider's {@link VaultProvider}, so the
 * Vault's cleartext key still never leaves its custody.
 *
 * <h2>What X.509 fields come from where</h2>
 *
 * <ul>
 *   <li>Subject Public Key — from {@code INSTRUMENT} (the multikey-encoded
 *       bytes, decoded via {@link MultiKey#publicKey}).</li>
 *   <li>Subject DN — derived from THEME IID (rendered as
 *       {@code CN=<iid-text>}); the IID is the stable identifier, the DN
 *       is a wire-format necessity.</li>
 *   <li>Issuer DN — derived from AGENT IID; for self-signed
 *       (AGENT == THEME) this equals the subject DN.</li>
 *   <li>notBefore / notAfter — from
 *       {@code ATTRIBUTE [VALIDITY_FROM/UNTIL]}.</li>
 *   <li>Serial number — derived from the body's datum ID (stable, unique
 *       per attestation).</li>
 *   <li>Signature — fresh JCA signature over the tbsCertificate bytes via
 *       {@link VaultProvider}.</li>
 * </ul>
 *
 * <p>v1 supports Ed25519 only — the JCA Provider's only registered
 * algorithm.  Extending to ECDSA / RSA tracks alongside extending
 * VaultProvider and {@link MultiKey#publicKey} for the new algorithm
 * family.
 */
public final class CertEmitter {

    private CertEmitter() {}

    /**
     * Emit the Attestation Frame as X.509 DER bytes.  The Vault signs the
     * tbsCertificate via the JCA Provider; the Vault's purpose is implied
     * by the Attestation's PURPOSE binding (signing, usually).
     *
     * @throws CertEmitterException if the body shape is incomplete or the
     *                              underlying JCA operations fail
     */
    public static byte[] toDer(Frame attestation, Vault vault) {
        Objects.requireNonNull(attestation, "attestation");
        Objects.requireNonNull(vault, "vault");
        VaultProvider.install();

        Body body = attestation.body();
        MultiKey subjectKey = Attestations.subjectPubkey(body)
                .orElseThrow(() -> new CertEmitterException(
                        "Attestation body has no INSTRUMENT pubkey"));
        PublicKey subjectPublicKey;
        try {
            subjectPublicKey = subjectKey.publicKey();
        } catch (RuntimeException e) {
            throw new CertEmitterException(
                    "Attestation pubkey could not be reconstructed as a JCA PublicKey", e);
        }
        ItemRef subjectIid = Attestations.subject(body)
                .orElseThrow(() -> new CertEmitterException(
                        "Attestation body has no THEME (subject)"));
        ItemRef attesterIid = Attestations.attester(body)
                .orElse(subjectIid);   // unanchored attestations get a self-DN issuer
        Instant validFrom = Attestations.validFrom(body)
                .orElseThrow(() -> new CertEmitterException(
                        "Attestation body has no VALIDITY_FROM"));
        Instant validUntil = Attestations.validUntil(body)
                .orElseThrow(() -> new CertEmitterException(
                        "Attestation body has no VALIDITY_UNTIL"));
        ItemRef purpose = Attestations.purpose(body)
                .orElse(ItemRef.iid(IdentityVocabulary.Signing.KEY));

        X500Principal subjectDn = new X500Principal("CN=" + subjectIid.encodeText());
        X500Principal issuerDn = new X500Principal("CN=" + attesterIid.encodeText());

        // Serial number derived from the body's datum ID's multihash — stable
        // and unique per attestation.  X.509 serials must be positive
        // BigIntegers; we mask the sign bit by taking the unsigned
        // interpretation.
        byte[] datumIdBytes = body.datumId().multihash();
        BigInteger serial = new BigInteger(1, datumIdBytes);
        if (serial.signum() == 0) serial = BigInteger.ONE;   // X.509 rejects zero

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerDn,
                serial,
                Date.from(validFrom),
                Date.from(validUntil),
                subjectDn,
                subjectPublicKey);

        // Sign the tbsCertificate via the Vault.  JcaContentSignerBuilder
        // looks up Signature.getInstance(algorithm, providerName); pointing it
        // at our provider routes the signing call through VaultSignatureSpi.
        String jcaAlgorithm = jcaAlgorithmFor(purpose, subjectKey);
        VaultPrivateKey vaultPrivKey = new VaultPrivateKey(vault, purpose, jcaAlgorithm);
        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder(jcaAlgorithm)
                    .setProvider(VaultProvider.NAME)
                    .build(vaultPrivKey);
        } catch (OperatorCreationException e) {
            throw new CertEmitterException("Failed to build Vault-backed cert signer", e);
        }

        try {
            return builder.build(signer).getEncoded();
        } catch (java.io.IOException e) {
            throw new CertEmitterException("Failed to encode X.509 cert", e);
        }
    }

    /**
     * Convenience: emit + parse back into a JDK {@link X509Certificate}.
     * Equivalent to {@code CertIngester.parse(toDer(attestation, vault))}.
     */
    public static X509Certificate toX509(Frame attestation, Vault vault) {
        byte[] der = toDer(attestation, vault);
        return CertIngester.parse(der);
    }

    private static String jcaAlgorithmFor(ItemRef purpose, MultiKey subjectKey) {
        // v1: Ed25519 only.  Match by the multikey codec, which is the
        // algorithm-agnostic identifier the rest of the codebase uses.
        if (subjectKey.code() == 0xed) {
            return "Ed25519";
        }
        throw new CertEmitterException(
                "No JCA signing algorithm wired for multikey codec 0x"
                        + Integer.toHexString(subjectKey.code()));
    }

    /** Thrown when emitting an X.509 cert fails. */
    public static final class CertEmitterException extends RuntimeException {
        public CertEmitterException(String message) {
            super(message);
        }

        public CertEmitterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
