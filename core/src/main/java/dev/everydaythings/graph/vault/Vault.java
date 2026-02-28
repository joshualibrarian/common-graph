package dev.everydaythings.graph.vault;

import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.component.Factory;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Picker;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;

import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Optional;
import java.util.Set;

/**
 * A Vault stores cryptographic keys securely with sign-in-place semantics.
 *
 * <p>This is the abstract base for all vault implementations. The key principle
 * is that <b>private keys never leave the vault</b> - all cryptographic operations
 * happen inside the vault itself.
 *
 * <p>Available backends:
 * <ul>
 *   <li>{@link InMemoryVault} - Ephemeral (testing/development, current default)</li>
 *   <li>{@link SoftwareVault} - PKCS12 file (persistent, works everywhere)</li>
 *   <li>{@link KeychainVault} - macOS Keychain + Secure Enclave (stub)</li>
 *   <li>{@link TpmVault} - TPM on Windows/Linux (stub)</li>
 *   <li>{@link Pkcs11Vault} - Hardware tokens via PKCS#11 (stub)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * Vault vault = Vault.open(path);
 *
 * // Generate a key (if needed)
 * if (!vault.containsKey("signing")) {
 *     vault.generateKey("signing", KeyType.ED25519);
 * }
 *
 * // Get public key (this CAN leave the vault)
 * PublicKey pub = vault.publicKey("signing").orElseThrow();
 *
 * // Sign data (private key NEVER leaves the vault)
 * byte[] signature = vault.sign("signing", dataToSign);
 * }</pre>
 */
@Type(value = Vault.KEY, glyph = "🔐")
public abstract class Vault implements Component {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/vault";

    /** Default alias for the primary signing key */
    public static final String SIGNING_KEY_ALIAS = "signing";

    /**
     * Supported key types.
     */
    public enum KeyType {
        /** Ed25519 - fast, secure, small signatures (recommended) */
        ED25519("Ed25519", "Ed25519"),

        /** ECDSA with P-256 - widely supported, good for interop */
        EC_P256("EC", "SHA256withECDSA"),

        /** RSA 4096-bit - legacy compatibility */
        RSA_4096("RSA", "SHA256withRSA");

        private final String algorithm;
        private final String signatureAlgorithm;

        KeyType(String algorithm, String signatureAlgorithm) {
            this.algorithm = algorithm;
            this.signatureAlgorithm = signatureAlgorithm;
        }

        public String algorithm() { return algorithm; }
        public String signatureAlgorithm() { return signatureAlgorithm; }
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Open or create a vault at the given path.
     *
     * <p>Currently uses {@link InMemoryVault} as the default for ease of testing.
     * For persistent storage, use {@link SoftwareVault#open(Path)} directly.
     * Future versions may auto-detect available hardware backends.
     */
    @Factory(label = "In-Memory Vault", glyph = "🔐", primary = true,
                              doc = "Ephemeral in-memory vault for testing. Keys lost on exit.")
    public static Vault open(@Param(label = "Vault File",
                                                     doc = "Path (ignored for in-memory)",
                                                     picker = Picker.FILE) Path path) {
        // Use InMemoryVault for now during development
        // TODO: Switch to SoftwareVault.open(path) for production
        return InMemoryVault.create();
    }

    /**
     * Create a new in-memory vault (default for testing/development).
     *
     * <p>Returns an {@link InMemoryVault}. For persistent storage,
     * use {@link SoftwareVault#create()} directly.
     */
    @Factory(label = "In-Memory", glyph = "🔐",
            doc = "Ephemeral in-memory vault. Keys lost on exit.")
    public static Vault create() {
        return InMemoryVault.create();
    }

    // ==================================================================================
    // Key Management
    // ==================================================================================

    /**
     * Generate a new key with the given alias and type.
     *
     * @param alias Unique identifier for the key
     * @param type  The key type to generate
     * @throws IllegalStateException if alias already exists
     */
    public abstract void generateKey(String alias, KeyType type);

    /**
     * Generate a new Ed25519 signing key with the default alias.
     */
    public void generateSigningKey() {
        generateKey(SIGNING_KEY_ALIAS, KeyType.ED25519);
    }

    /**
     * Check if a key exists with the given alias.
     */
    public abstract boolean containsKey(String alias);

    /**
     * List all key aliases in this vault.
     */
    public abstract Set<String> aliases();

    /**
     * Delete a key by alias.
     *
     * @throws IllegalStateException if alias doesn't exist
     */
    public abstract void deleteKey(String alias);

    /**
     * Get the key type for an alias.
     */
    public abstract Optional<KeyType> keyType(String alias);

    // ==================================================================================
    // Public Key Access (public keys CAN leave the vault)
    // ==================================================================================

    /**
     * Get the public key for an alias.
     *
     * <p>Public keys can safely leave the vault - they're meant to be shared.
     */
    public abstract Optional<PublicKey> publicKey(String alias);

    /**
     * Get the default signing public key.
     */
    public Optional<PublicKey> signingPublicKey() {
        return publicKey(SIGNING_KEY_ALIAS);
    }

    // ==================================================================================
    // Sign-in-Place Operations (private key NEVER leaves the vault)
    // ==================================================================================

    /**
     * Sign data using the key at the given alias.
     *
     * <p><b>The private key never leaves the vault.</b> For hardware-backed
     * vaults (Secure Enclave, TPM, YubiKey), the signing operation happens
     * entirely within the secure hardware.
     *
     * @param alias The key alias to sign with
     * @param data  The data to sign
     * @return The signature bytes
     * @throws IllegalStateException if alias doesn't exist or can't sign
     */
    public abstract byte[] sign(String alias, byte[] data);

    /**
     * Sign data using the default signing key.
     */
    public byte[] sign(byte[] data) {
        return sign(SIGNING_KEY_ALIAS, data);
    }

    /**
     * Verify a signature using the key at the given alias.
     *
     * @param alias     The key alias to verify with
     * @param data      The original data
     * @param signature The signature to verify
     * @return true if signature is valid
     */
    public abstract boolean verify(String alias, byte[] data, byte[] signature);

    // ==================================================================================
    // Encryption Operations (for future use)
    // ==================================================================================

    /**
     * Decrypt data using the key at the given alias.
     *
     * <p><b>The private key never leaves the vault.</b>
     *
     * @param alias      The key alias to decrypt with
     * @param ciphertext The encrypted data
     * @return The decrypted plaintext
     */
    public byte[] decrypt(String alias, byte[] ciphertext) {
        throw new UnsupportedOperationException(
                "Decryption not yet implemented for " + getClass().getSimpleName());
    }

    // ==================================================================================
    // Persistence (for Component system)
    // ==================================================================================

    /**
     * Encode this vault to bytes.
     */
    public abstract byte[] encode();

    /**
     * Check if this vault has any keys.
     */
    public boolean isEmpty() {
        return aliases().isEmpty();
    }

    /**
     * Check if this vault can sign (has a signing key).
     */
    public boolean canSign() {
        return containsKey(SIGNING_KEY_ALIAS);
    }

    // ==================================================================================
    // TLS Support
    // ==================================================================================

    /**
     * Get the X.509 certificate for a key alias.
     *
     * <p>Used for TLS where both parties identify via certificates.
     *
     * @param alias The key alias
     * @return The certificate, or empty if not found
     */
    public abstract Optional<java.security.cert.X509Certificate> certificate(String alias);

    /**
     * Get the signing key certificate.
     */
    public Optional<java.security.cert.X509Certificate> signingCertificate() {
        return certificate(SIGNING_KEY_ALIAS);
    }

    /**
     * Create an SSLContext configured with this vault's signing key.
     *
     * <p>The returned context can be used for TLS connections where
     * this vault's identity needs to be proven.
     *
     * @return SSLContext configured for mutual TLS
     * @deprecated Use {@link #serverSslContext()} and {@link #clientSslContext()} instead.
     */
    @Deprecated
    public abstract javax.net.ssl.SSLContext sslContext();

    /**
     * Create a Netty {@link io.netty.handler.ssl.SslContext} for the server side.
     *
     * <p>Generates a dedicated EC P-256 TLS key pair signed by the Ed25519 signing key,
     * binding the TLS identity to the CG signing identity.
     */
    public abstract io.netty.handler.ssl.SslContext serverSslContext();

    /**
     * Create a Netty {@link io.netty.handler.ssl.SslContext} for the client side.
     *
     * <p>Generates a dedicated EC P-256 TLS key pair signed by the Ed25519 signing key,
     * binding the TLS identity to the CG signing identity.
     */
    public abstract io.netty.handler.ssl.SslContext clientSslContext();
}
