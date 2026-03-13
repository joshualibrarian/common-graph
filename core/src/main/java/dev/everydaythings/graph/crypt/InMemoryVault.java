package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.crypt.Algorithm;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * In-memory vault for testing and early development.
 *
 * <p>Keys are stored in memory only - they are lost when the JVM exits.
 * This is intentional: use this for testing, demos, and development where
 * you don't need persistent keys.
 *
 * <p>For production use:
 * <ul>
 *   <li>{@link SoftwareVault} - Persistent PKCS12 file</li>
 *   <li>{@link KeychainVault} - macOS Keychain (future)</li>
 *   <li>{@link TpmVault} - TPM hardware (future)</li>
 *   <li>{@link Pkcs11Vault} - Hardware tokens (future)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a new in-memory vault with default signing key
 * Vault vault = InMemoryVault.create();
 *
 * // Or create empty and add keys manually
 * Vault vault = InMemoryVault.createEmpty();
 * vault.generateKey("mykey", Algorithm.Sign.ED25519);
 *
 * // Use for signing
 * byte[] sig = vault.sign("mykey", data);
 * }</pre>
 */
public final class InMemoryVault extends Vault {

    private final Map<String, KeyEntry> keys = new LinkedHashMap<>();

    private InMemoryVault() {}

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a new in-memory vault with a default signing key.
     */
    public static InMemoryVault create() {
        InMemoryVault vault = new InMemoryVault();
        vault.generateKey(SIGNING_KEY_ALIAS, Algorithm.Sign.ED25519);
        return vault;
    }

    /**
     * Create an empty in-memory vault (no keys).
     */
    public static InMemoryVault createEmpty() {
        return new InMemoryVault();
    }

    /**
     * Factory method for path-based opening (required by Component system).
     *
     * <p>Note: The path is ignored - this is always in-memory. This factory
     * exists for compatibility with the component system.
     */
    @Factory(label = "In-Memory (Testing)", glyph = "\uD83E\uDDEA", primary = true,
            doc = "Ephemeral in-memory vault. Keys are lost on exit. For testing only.")
    public static Vault open(@SuppressWarnings("unused") Path path) {
        return create();
    }

    /**
     * No-arg factory for component createDefault.
     *
     * <p>The primary factory requires a Path parameter, which prevents
     * createDefault() from instantiating InMemoryVault. This no-arg factory
     * enables in-memory Signer creation without a filesystem path.
     */
    @Factory(label = "In-Memory Default", glyph = "\uD83E\uDDEA",
            doc = "Create an in-memory vault with default signing key.")
    public static InMemoryVault createInMemory() {
        return create();
    }

    // ==================================================================================
    // Key Management
    // ==================================================================================

    @Override
    public void generateKey(String alias, Algorithm.Asymmetric algorithm) {
        if (containsKey(alias)) {
            throw new IllegalStateException("Key already exists: " + alias);
        }

        try {
            KeyPair keyPair = generateKeyPair(algorithm);
            X509Certificate cert;
            if (algorithm.canSign()) {
                cert = generateSelfSignedCertificate(keyPair, (Algorithm.Sign) algorithm);
            } else {
                // Non-signing key: cross-sign with the signing key
                KeyEntry signingEntry = keys.get(SIGNING_KEY_ALIAS);
                if (signingEntry == null) {
                    throw new IllegalStateException(
                            "Signing key must exist before generating non-signing keys");
                }
                cert = issueCertificate(keyPair.getPublic(),
                        signingEntry.keyPair.getPrivate(),
                        signingEntry.certificate.getSubjectX500Principal(),
                        (Algorithm.Sign) signingEntry.algorithm);
            }
            keys.put(alias, new KeyEntry(keyPair, cert, algorithm));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key: " + alias, e);
        }
    }

    @Override
    public boolean containsKey(String alias) {
        return keys.containsKey(alias);
    }

    @Override
    public Set<String> aliases() {
        return new LinkedHashSet<>(keys.keySet());
    }

    @Override
    public void deleteKey(String alias) {
        if (!containsKey(alias)) {
            throw new IllegalStateException("Key doesn't exist: " + alias);
        }
        keys.remove(alias);
    }

    @Override
    public Optional<Algorithm.Asymmetric> algorithm(String alias) {
        KeyEntry entry = keys.get(alias);
        return entry != null ? Optional.of(entry.algorithm) : Optional.empty();
    }

    // ==================================================================================
    // Public Key Access
    // ==================================================================================

    @Override
    public Optional<PublicKey> publicKey(String alias) {
        KeyEntry entry = keys.get(alias);
        return entry != null ? Optional.of(entry.keyPair.getPublic()) : Optional.empty();
    }

    // ==================================================================================
    // Sign-in-Place Operations
    // ==================================================================================

    @Override
    public byte[] sign(String alias, byte[] data) {
        KeyEntry entry = keys.get(alias);
        if (entry == null) {
            throw new IllegalStateException("Key doesn't exist: " + alias);
        }
        if (!entry.algorithm.canSign()) {
            throw new IllegalStateException(
                    "Key '" + alias + "' (" + entry.algorithm + ") is not a signing key");
        }

        try {
            Algorithm.Sign signAlg = (Algorithm.Sign) entry.algorithm;
            Signature sig = Signature.getInstance(signAlg.signatureName());
            sig.initSign(entry.keyPair.getPrivate());
            sig.update(data);
            return sig.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Failed to sign with key: " + alias, e);
        }
    }

    @Override
    public boolean verify(String alias, byte[] data, byte[] signature) {
        KeyEntry entry = keys.get(alias);
        if (entry == null) {
            return false;
        }
        if (!entry.algorithm.canSign()) {
            return false;
        }

        try {
            Algorithm.Sign signAlg = (Algorithm.Sign) entry.algorithm;
            Signature sig = Signature.getInstance(signAlg.signatureName());
            sig.initVerify(entry.keyPair.getPublic());
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Failed to verify signature", e);
        }
    }

    // ==================================================================================
    // Key Agreement Operations
    // ==================================================================================

    @Override
    public byte[] deriveSharedSecret(String alias, PublicKey peerPublicKey) {
        KeyEntry entry = keys.get(alias);
        if (entry == null) {
            throw new IllegalStateException("Key doesn't exist: " + alias);
        }
        if (!(entry.algorithm instanceof Algorithm.KeyMgmt keyMgmt)) {
            throw new IllegalStateException(
                    "Key '" + alias + "' (" + entry.algorithm + ") is not a key-agreement key");
        }
        if (keyMgmt.agreementName() == null) {
            throw new IllegalStateException(
                    "Algorithm " + keyMgmt + " does not support key agreement");
        }

        try {
            KeyAgreement ka = KeyAgreement.getInstance(keyMgmt.agreementName());
            ka.init(entry.keyPair.getPrivate());
            ka.doPhase(peerPublicKey, true);
            return ka.generateSecret();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to derive shared secret with key: " + alias, e);
        }
    }

    // ==================================================================================
    // Persistence (no-op for in-memory)
    // ==================================================================================

    @Override
    public byte[] encode() {
        // In-memory vault doesn't persist - return empty
        return new byte[0];
    }

    // ==================================================================================
    // TLS Support
    // ==================================================================================

    @Override
    public Optional<X509Certificate> certificate(String alias) {
        KeyEntry entry = keys.get(alias);
        return entry != null ? Optional.of(entry.certificate) : Optional.empty();
    }

    @Override
    public javax.net.ssl.SSLContext sslContext() {
        try {
            // Ed25519 (our signing key type) isn't supported by JSSE's KeyManagerFactory
            // in Java 21 — that requires Java 23+. So we generate a dedicated EC P-256 key
            // for TLS, but sign it with the Ed25519 signing key. This binds the TLS identity
            // to the CG signing identity: peers can verify the cert chain to confirm which
            // Librarian they're talking to.
            KeyEntry signingEntry = keys.get(SIGNING_KEY_ALIAS);
            if (signingEntry == null) {
                throw new IllegalStateException("No signing key — cannot create TLS context");
            }

            KeyPair tlsKeyPair = generateKeyPair(Algorithm.Sign.ES256);
            X509Certificate tlsCert = issueCertificate(
                    tlsKeyPair.getPublic(),
                    signingEntry.keyPair.getPrivate(),
                    signingEntry.certificate.getSubjectX500Principal(),
                    (Algorithm.Sign) signingEntry.algorithm
            );

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            char[] password = "inmemory".toCharArray();
            ks.setKeyEntry("tls", tlsKeyPair.getPrivate(), password,
                    new java.security.cert.Certificate[]{tlsCert, signingEntry.certificate});

            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);

            // Trust all for testing (don't use in production!)
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), trustAll, new SecureRandom());
            return ctx;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    @Override
    public io.netty.handler.ssl.SslContext serverSslContext() {
        try {
            KeyEntry signingEntry = keys.get(SIGNING_KEY_ALIAS);
            if (signingEntry == null) {
                throw new IllegalStateException("No signing key — cannot create TLS context");
            }

            KeyPair tlsKeyPair = generateKeyPair(Algorithm.Sign.ES256);
            X509Certificate tlsCert = issueCertificate(
                    tlsKeyPair.getPublic(),
                    signingEntry.keyPair.getPrivate(),
                    signingEntry.certificate.getSubjectX500Principal(),
                    (Algorithm.Sign) signingEntry.algorithm
            );

            return io.netty.handler.ssl.SslContextBuilder
                    .forServer(tlsKeyPair.getPrivate(), tlsCert, signingEntry.certificate)
                    .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                    .clientAuth(io.netty.handler.ssl.ClientAuth.OPTIONAL)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create server SslContext", e);
        }
    }

    @Override
    public io.netty.handler.ssl.SslContext clientSslContext() {
        try {
            KeyEntry signingEntry = keys.get(SIGNING_KEY_ALIAS);
            if (signingEntry == null) {
                throw new IllegalStateException("No signing key — cannot create TLS context");
            }

            KeyPair tlsKeyPair = generateKeyPair(Algorithm.Sign.ES256);
            X509Certificate tlsCert = issueCertificate(
                    tlsKeyPair.getPublic(),
                    signingEntry.keyPair.getPrivate(),
                    signingEntry.certificate.getSubjectX500Principal(),
                    (Algorithm.Sign) signingEntry.algorithm
            );

            return io.netty.handler.ssl.SslContextBuilder
                    .forClient()
                    .keyManager(tlsKeyPair.getPrivate(), tlsCert, signingEntry.certificate)
                    .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create client SslContext", e);
        }
    }

    // ==================================================================================
    // Key Generation Helpers
    // ==================================================================================

    private static KeyPair generateKeyPair(Algorithm.Asymmetric algorithm) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm.keyGeneratorName());
            if (algorithm.keyBits() > 0) {
                keyGen.initialize(algorithm.keyBits());
            }
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate " + algorithm + " keypair", e);
        }
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, Algorithm.Sign algorithm) {
        try {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);
            Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 year for testing

            X500Name issuer = new X500Name("CN=InMemoryVault Test");
            BigInteger serial = BigInteger.valueOf(now);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter,
                    issuer, // self-signed
                    keyPair.getPublic()
            );

            ContentSigner signer = new JcaContentSignerBuilder(algorithm.signatureName())
                    .build(keyPair.getPrivate());

            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

        } catch (CertificateException | OperatorCreationException e) {
            throw new RuntimeException("Failed to generate self-signed certificate", e);
        }
    }

    /**
     * Issue a certificate for {@code subjectKey}, signed by {@code issuerKey}.
     * Used to create TLS certs signed by the Ed25519 signing key,
     * and to cross-sign non-signing keys (X25519).
     */
    private static X509Certificate issueCertificate(
            PublicKey subjectKey,
            PrivateKey issuerKey,
            javax.security.auth.x500.X500Principal issuerPrincipal,
            Algorithm.Sign issuerAlgorithm) {
        try {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);
            Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000);

            X500Name issuer = new X500Name(issuerPrincipal.getName());
            X500Name subject = new X500Name("CN=CG Key");
            BigInteger serial = BigInteger.valueOf(now);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter,
                    subject,
                    subjectKey
            );

            ContentSigner signer = new JcaContentSignerBuilder(issuerAlgorithm.signatureName())
                    .build(issuerKey);

            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

        } catch (CertificateException | OperatorCreationException e) {
            throw new RuntimeException("Failed to issue certificate", e);
        }
    }

    // ==================================================================================
    // Internal Types
    // ==================================================================================

    private record KeyEntry(KeyPair keyPair, X509Certificate certificate, Algorithm.Asymmetric algorithm) {}
}
