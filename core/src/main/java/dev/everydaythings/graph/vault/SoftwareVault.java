package dev.everydaythings.graph.vault;

import dev.everydaythings.graph.item.component.Factory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

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
    private final Map<String, KeyType> keyTypes = new HashMap<>();

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
            vault.generateKey(SIGNING_KEY_ALIAS, KeyType.ED25519);

            return vault;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Failed to create software vault", e);
        }
    }

    /**
     * Open or create a vault at the given path.
     */
    @Factory(label = "Software Vault (PKCS12)", glyph = "🗝️",
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

            // Infer key types from stored keys
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.isKeyEntry(alias)) {
                    vault.inferKeyType(alias);
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
    public void generateKey(String alias, KeyType type) {
        if (containsKey(alias)) {
            throw new IllegalStateException("Key already exists: " + alias);
        }

        try {
            KeyPair keyPair = generateKeyPair(type);
            X509Certificate cert = generateSelfSignedCertificate(keyPair, type);

            keyStore.setKeyEntry(alias, keyPair.getPrivate(), password, new Certificate[]{cert});
            keyTypes.put(alias, type);

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
            keyTypes.remove(alias);
            persist();
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to delete key: " + alias, e);
        }
    }

    @Override
    public Optional<KeyType> keyType(String alias) {
        if (!containsKey(alias)) {
            return Optional.empty();
        }

        KeyType type = keyTypes.get(alias);
        if (type == null) {
            inferKeyType(alias);
            type = keyTypes.get(alias);
        }
        return Optional.ofNullable(type);
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

        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            KeyType type = keyType(alias).orElse(KeyType.ED25519);

            Signature sig = Signature.getInstance(type.signatureAlgorithm());
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

        try {
            KeyType type = keyType(alias).orElse(KeyType.ED25519);

            Signature sig = Signature.getInstance(type.signatureAlgorithm());
            sig.initVerify(pubKey.get());
            sig.update(data);
            return sig.verify(signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Failed to verify signature", e);
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

    private static KeyPair generateKeyPair(KeyType type) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(type.algorithm());

            // Configure key size for RSA and EC
            if (type == KeyType.RSA_4096) {
                keyGen.initialize(4096);
            } else if (type == KeyType.EC_P256) {
                keyGen.initialize(256);
            }
            // Ed25519 doesn't need explicit size

            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate " + type + " keypair", e);
        }
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, KeyType type) {
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

            // Use appropriate signature algorithm
            String sigAlg = switch (type) {
                case ED25519 -> "Ed25519";
                case EC_P256 -> "SHA256withECDSA";
                case RSA_4096 -> "SHA256withRSA";
            };

            ContentSigner signer = new JcaContentSignerBuilder(sigAlg)
                    .build(keyPair.getPrivate());

            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

        } catch (CertificateException | OperatorCreationException e) {
            throw new RuntimeException("Failed to generate self-signed certificate", e);
        }
    }

    private void inferKeyType(String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) return;

            String algorithm = cert.getPublicKey().getAlgorithm();
            KeyType type = switch (algorithm) {
                case "Ed25519", "EdDSA" -> KeyType.ED25519;
                case "EC" -> KeyType.EC_P256;
                case "RSA" -> KeyType.RSA_4096;
                default -> null;
            };

            if (type != null) {
                keyTypes.put(alias, type);
            }
        } catch (KeyStoreException e) {
            // Ignore - key type will remain unknown
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
