package dev.everydaythings.graph.vault;

import dev.everydaythings.graph.item.component.Factory;
import dev.everydaythings.graph.trust.Algorithm;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.crypto.KeyAgreement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Software-based vault using PKCS12 keystore.
 *
 * <p>This is the default vault implementation that works everywhere.
 * Keys are stored in a PKCS12 file with password protection.
 *
 * <p>While this implementation technically has access to private keys in memory,
 * it follows the sign-in-place contract - callers should use {@link #sign} rather
 * than extracting keys. This makes it easy to swap in hardware-backed implementations.
 */
public final class SoftwareVault extends Vault {

    private static final String KEYSTORE_TYPE = "PKCS12";

    // TODO: Proper password management - derive from user credentials or OS keychain
    private static final char[] DEFAULT_PASSWORD = "vault-password".toCharArray();

    private Path storagePath;
    private final KeyStore keyStore;
    private final char[] password;
    private final Map<String, Algorithm.Asymmetric> algorithms = new HashMap<>();

    private SoftwareVault(KeyStore keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a new empty software vault.
     */
    public static SoftwareVault create() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(null, DEFAULT_PASSWORD);

            SoftwareVault vault = new SoftwareVault(ks, DEFAULT_PASSWORD);

            // Generate default signing key
            vault.generateKey(SIGNING_KEY_ALIAS, Algorithm.Sign.ED25519);

            return vault;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Failed to create software vault", e);
        }
    }

    /**
     * Open or create a vault at the given path.
     */
    @Factory(label = "Software Vault (PKCS12)", glyph = "\uD83D\uDDDD\uFE0F",
            doc = "Software-based vault using PKCS12 keystore file.")
    public static SoftwareVault open(Path path) {
        if (Files.exists(path)) {
            return load(path);
        } else {
            SoftwareVault vault = create();
            vault.storagePath = path;
            vault.persist();
            return vault;
        }
    }

    /**
     * Load an existing vault from a path.
     */
    public static SoftwareVault load(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            SoftwareVault vault = decode(bytes);
            vault.storagePath = path;
            return vault;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load vault from " + path, e);
        }
    }

    /**
     * Decode a vault from PKCS12 bytes.
     */
    public static SoftwareVault decode(byte[] bytes) {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(new ByteArrayInputStream(bytes), DEFAULT_PASSWORD);

            SoftwareVault vault = new SoftwareVault(ks, DEFAULT_PASSWORD);

            // Infer algorithms from stored keys
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.isKeyEntry(alias)) {
                    vault.inferAlgorithm(alias);
                }
            }

            return vault;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Failed to decode vault", e);
        }
    }

    // ==================================================================================
    // Vault Implementation - Key Management
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
                cert = crossSignCertificate(keyPair);
            }

            keyStore.setKeyEntry(alias, keyPair.getPrivate(), password, new Certificate[]{cert});
            algorithms.put(alias, algorithm);

            persist();
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to generate key: " + alias, e);
        }
    }

    @Override
    public boolean containsKey(String alias) {
        try {
            return keyStore.containsAlias(alias) && keyStore.isKeyEntry(alias);
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to check key: " + alias, e);
        }
    }

    @Override
    public Set<String> aliases() {
        try {
            Set<String> result = new LinkedHashSet<>();
            for (String alias : Collections.list(keyStore.aliases())) {
                if (keyStore.isKeyEntry(alias)) {
                    result.add(alias);
                }
            }
            return result;
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to list aliases", e);
        }
    }

    @Override
    public void deleteKey(String alias) {
        if (!containsKey(alias)) {
            throw new IllegalStateException("Key doesn't exist: " + alias);
        }

        try {
            keyStore.deleteEntry(alias);
            algorithms.remove(alias);
            persist();
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to delete key: " + alias, e);
        }
    }

    @Override
    public Optional<Algorithm.Asymmetric> algorithm(String alias) {
        if (!containsKey(alias)) {
            return Optional.empty();
        }

        Algorithm.Asymmetric alg = algorithms.get(alias);
        if (alg == null) {
            inferAlgorithm(alias);
            alg = algorithms.get(alias);
        }
        return Optional.ofNullable(alg);
    }

    // ==================================================================================
    // Vault Implementation - Public Key Access
    // ==================================================================================

    @Override
    public Optional<PublicKey> publicKey(String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                return Optional.empty();
            }
            return Optional.of(cert.getPublicKey());
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to get public key: " + alias, e);
        }
    }

    // ==================================================================================
    // Vault Implementation - Sign-in-Place
    // ==================================================================================

    @Override
    public byte[] sign(String alias, byte[] data) {
        if (!containsKey(alias)) {
            throw new IllegalStateException("Key doesn't exist: " + alias);
        }

        Algorithm.Asymmetric alg = algorithm(alias).orElse(Algorithm.Sign.ED25519);
        if (!alg.canSign()) {
            throw new IllegalStateException(
                    "Key '" + alias + "' (" + alg + ") is not a signing key");
        }

        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            Algorithm.Sign signAlg = (Algorithm.Sign) alg;

            Signature sig = Signature.getInstance(signAlg.signatureName());
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();

        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException |
                 InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Failed to sign with key: " + alias, e);
        }
    }

    @Override
    public boolean verify(String alias, byte[] data, byte[] signature) {
        Optional<PublicKey> pubKey = publicKey(alias);
        if (pubKey.isEmpty()) {
            return false;
        }

        Algorithm.Asymmetric alg = algorithm(alias).orElse(Algorithm.Sign.ED25519);
        if (!alg.canSign()) {
            return false;
        }

        try {
            Algorithm.Sign signAlg = (Algorithm.Sign) alg;

            Signature sig = Signature.getInstance(signAlg.signatureName());
            sig.initVerify(pubKey.get());
            sig.update(data);
            return sig.verify(signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Failed to verify signature", e);
        }
    }

    // ==================================================================================
    // Vault Implementation - Key Agreement
    // ==================================================================================

    @Override
    public byte[] deriveSharedSecret(String alias, PublicKey peerPublicKey) {
        if (!containsKey(alias)) {
            throw new IllegalStateException("Key doesn't exist: " + alias);
        }

        Algorithm.Asymmetric alg = algorithm(alias)
                .orElseThrow(() -> new IllegalStateException("Unknown algorithm for key: " + alias));
        if (!(alg instanceof Algorithm.KeyMgmt keyMgmt)) {
            throw new IllegalStateException(
                    "Key '" + alias + "' (" + alg + ") is not a key-agreement key");
        }
        if (keyMgmt.agreementName() == null) {
            throw new IllegalStateException(
                    "Algorithm " + keyMgmt + " does not support key agreement");
        }

        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            KeyAgreement ka = KeyAgreement.getInstance(keyMgmt.agreementName());
            ka.init(privateKey);
            ka.doPhase(peerPublicKey, true);
            return ka.generateSecret();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException |
                 InvalidKeyException e) {
            throw new RuntimeException("Failed to derive shared secret with key: " + alias, e);
        }
    }

    // ==================================================================================
    // Vault Implementation - Persistence
    // ==================================================================================

    @Override
    public byte[] encode() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            keyStore.store(baos, password);
            return baos.toByteArray();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Failed to encode vault", e);
        }
    }

    private void persist() {
        if (storagePath == null) return;

        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, encode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist vault", e);
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
            Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000 * 100); // 100 years

            X500Name issuer = new X500Name("CN=CommonGraph Vault");
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
     * Cross-sign a non-signing key's certificate with the existing signing key.
     */
    private X509Certificate crossSignCertificate(KeyPair subjectKeyPair) {
        try {
            PrivateKey signingKey = (PrivateKey) keyStore.getKey(SIGNING_KEY_ALIAS, password);
            Algorithm.Asymmetric signingAlg = algorithms.get(SIGNING_KEY_ALIAS);
            Certificate signingCert = keyStore.getCertificate(SIGNING_KEY_ALIAS);

            if (signingKey == null || signingAlg == null || signingCert == null) {
                throw new IllegalStateException(
                        "Signing key must exist before generating non-signing keys");
            }
            if (!signingAlg.canSign()) {
                throw new IllegalStateException("Signing key algorithm cannot sign: " + signingAlg);
            }

            Algorithm.Sign signAlg = (Algorithm.Sign) signingAlg;

            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);
            Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000 * 100);

            X500Name issuer = new X500Name("CN=CommonGraph Vault");
            X500Name subject = new X500Name("CN=CG Key");
            BigInteger serial = BigInteger.valueOf(now);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter,
                    subject,
                    subjectKeyPair.getPublic()
            );

            ContentSigner signer = new JcaContentSignerBuilder(signAlg.signatureName())
                    .build(signingKey);

            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

        } catch (CertificateException | OperatorCreationException | KeyStoreException |
                 NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new RuntimeException("Failed to cross-sign certificate", e);
        }
    }

    private void inferAlgorithm(String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) return;

            String jcaAlgName = cert.getPublicKey().getAlgorithm();
            Algorithm.Asymmetric alg = switch (jcaAlgName) {
                case "Ed25519", "EdDSA" -> Algorithm.Sign.ED25519;
                case "EC"               -> Algorithm.Sign.ES256;
                case "RSA"              -> Algorithm.Sign.PS256;
                case "XDH", "X25519"    -> Algorithm.KeyMgmt.ECDH_ES_HKDF_256;
                default                 -> null;
            };

            if (alg != null) {
                algorithms.put(alias, alg);
            }
        } catch (KeyStoreException e) {
            // Ignore - algorithm will remain unknown
        }
    }

    // ==================================================================================
    // TLS Support
    // ==================================================================================

    @Override
    public Optional<X509Certificate> certificate(String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate x509) {
                return Optional.of(x509);
            }
            return Optional.empty();
        } catch (KeyStoreException e) {
            return Optional.empty();
        }
    }

    @Override
    public javax.net.ssl.SSLContext sslContext() {
        try {
            // Create KeyManagerFactory with our keystore
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);

            // Create TrustManagerFactory that trusts all certificates for now
            // In production, this should use a proper trust store
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                            // Accept all for now - trust verification happens at CG layer
                        }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                            // Accept all for now - trust verification happens at CG layer
                        }
                    }
            };

            // Create SSLContext
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), trustAllCerts, new SecureRandom());

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    @Override
    public io.netty.handler.ssl.SslContext serverSslContext() {
        throw new UnsupportedOperationException("SoftwareVault Netty SslContext not yet implemented");
    }

    @Override
    public io.netty.handler.ssl.SslContext clientSslContext() {
        throw new UnsupportedOperationException("SoftwareVault Netty SslContext not yet implemented");
    }
}
