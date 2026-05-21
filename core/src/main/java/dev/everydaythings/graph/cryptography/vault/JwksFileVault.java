package dev.everydaythings.graph.cryptography.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.everydaythings.graph.cryptography.DoubleRatchet;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JwksFileVault — a {@link Vault} that persists its private-key material to
 * disk as a JWKS (JSON Web Key Set) file.
 *
 * <h2>File layout</h2>
 *
 * <pre>
 * &lt;vault-dir&gt;/
 *   keys.jwks    JSON object holding all keypairs as JWKs:
 *                {
 *                  "signing":         { "current": {...JWK...}, "next": {...JWK...} },
 *                  "keyAgreement":    { "current": {...JWK...}, "next": {...JWK...} },
 *                  "signedPreKey":    {...JWK...},
 *                  "oneTimePreKey":   {...JWK...}
 *                }
 * </pre>
 *
 * <p>The file uses JWK (RFC 7517 / RFC 8037) for individual keypairs.  See
 * {@link JwkSerializer} for the encoding details.
 *
 * <h2>Security posture</h2>
 *
 * <p>Phase 1: NOT encrypted at rest.  Plaintext private keys on disk,
 * relying on filesystem permissions ({@code 0600}) for protection.  Suitable
 * for development use only.  Hardware-backed vaults
 * ({@code Pkcs11Vault}, future Keymaster integration) replace this for
 * production deployments.
 *
 * <p>Phase 2 (deferred): JWE-wrap the JWKS file with a passphrase-derived
 * KEK, or delegate the file to OS keychain integration.
 *
 * <h2>Operation delegation</h2>
 *
 * <p>Internally backs operations with an {@link InMemoryVault} constructed
 * from the loaded keypairs.  All {@link Vault} interface methods forward
 * to the delegate.  Mutations that should change persisted state (SPK
 * rotation, future KEL rotation) are not yet wired to auto-persist; users
 * need to re-persist explicitly or accept ephemeral mutations.  Auto-persist
 * is Phase 2 work.
 */
public final class JwksFileVault implements Vault {

    private static final String KEYS_FILE_NAME = "keys.jwks";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path vaultDir;
    private final InMemoryVault delegate;

    // Cached references to the keypairs the vault was constructed with;
    // used to re-serialize without needing to read them back out of the
    // (private-bytes-hiding) typed handles.
    private final KeyPair signingCurrent;
    private final KeyPair signingNext;
    private final KeyPair kaCurrent;
    private final KeyPair kaNext;
    private final KeyPair signedPreKey;
    private final KeyPair oneTimePreKey;

    private JwksFileVault(Path vaultDir,
                          KeyPair signingCurrent, KeyPair signingNext,
                          KeyPair kaCurrent,      KeyPair kaNext,
                          KeyPair signedPreKey,   KeyPair oneTimePreKey) {
        this.vaultDir       = vaultDir;
        this.signingCurrent = signingCurrent;
        this.signingNext    = signingNext;
        this.kaCurrent      = kaCurrent;
        this.kaNext         = kaNext;
        this.signedPreKey   = signedPreKey;
        this.oneTimePreKey  = oneTimePreKey;
        this.delegate = InMemoryVault.fromIdentityKeyPairs(
                signingCurrent, signingNext, kaCurrent, kaNext, signedPreKey, oneTimePreKey);
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    /**
     * Mint a fresh JwksFileVault at the given directory.  Generates new
     * keypairs and writes them to {@code <vaultDir>/keys.jwks}.  Throws if
     * the file already exists — use {@link #load(Path)} to reuse an
     * existing vault.
     */
    public static JwksFileVault create(Path vaultDir) {
        Objects.requireNonNull(vaultDir, "vaultDir");
        Path keysFile = vaultDir.resolve(KEYS_FILE_NAME);
        if (Files.exists(keysFile)) {
            throw new IllegalStateException(
                    "Vault already exists at " + keysFile + " (use load() instead)");
        }
        try {
            Files.createDirectories(vaultDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create vault directory " + vaultDir, e);
        }

        Signing ed25519 = Signing.Ed25519.builtin();
        KeyAgreement x25519 = KeyAgreement.X25519.builtin();

        JwksFileVault vault = new JwksFileVault(
                vaultDir,
                ed25519.generateKeyPair(), ed25519.generateKeyPair(),
                x25519.generateKeyPair(),  x25519.generateKeyPair(),
                x25519.generateKeyPair(),  x25519.generateKeyPair());
        vault.persist();
        return vault;
    }

    /**
     * Load an existing JwksFileVault from {@code <vaultDir>/keys.jwks}.
     * Throws if the file is missing or malformed.
     */
    public static JwksFileVault load(Path vaultDir) {
        Objects.requireNonNull(vaultDir, "vaultDir");
        Path keysFile = vaultDir.resolve(KEYS_FILE_NAME);
        if (!Files.exists(keysFile)) {
            throw new IllegalStateException("No vault file at " + keysFile);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = MAPPER.readValue(keysFile.toFile(), Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> signing = (Map<String, Object>) root.get("signing");
            @SuppressWarnings("unchecked")
            Map<String, Object> ka      = (Map<String, Object>) root.get("keyAgreement");

            KeyPair signingCurrent = decode(signing, "current");
            KeyPair signingNext    = decode(signing, "next");
            KeyPair kaCurrent      = decode(ka, "current");
            KeyPair kaNext         = decode(ka, "next");

            @SuppressWarnings("unchecked")
            Map<String, Object> spkJwk = (Map<String, Object>) root.get("signedPreKey");
            KeyPair signedPreKey = JwkSerializer.decode(spkJwk);

            @SuppressWarnings("unchecked")
            Map<String, Object> otpkJwk = (Map<String, Object>) root.get("oneTimePreKey");
            KeyPair oneTimePreKey = otpkJwk != null ? JwkSerializer.decode(otpkJwk) : null;

            return new JwksFileVault(vaultDir,
                    signingCurrent, signingNext, kaCurrent, kaNext, signedPreKey, oneTimePreKey);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read vault at " + keysFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static KeyPair decode(Map<String, Object> parent, String key) {
        Map<String, Object> jwk = (Map<String, Object>) parent.get(key);
        if (jwk == null) {
            throw new IllegalArgumentException("Missing JWK field: " + key);
        }
        return JwkSerializer.decode(jwk);
    }

    /**
     * Re-write the vault file with the current keypair state.  Called
     * automatically on {@link #create(Path)}; future mutations (rotation,
     * SPK rotate) will gain auto-persist hooks in Phase 2.
     */
    public void persist() {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> signing = new LinkedHashMap<>();
        signing.put("current", JwkSerializer.encodeEd25519(signingCurrent));
        signing.put("next",    JwkSerializer.encodeEd25519(signingNext));
        root.put("signing", signing);

        Map<String, Object> ka = new LinkedHashMap<>();
        ka.put("current", JwkSerializer.encodeX25519(kaCurrent));
        ka.put("next",    JwkSerializer.encodeX25519(kaNext));
        root.put("keyAgreement", ka);

        root.put("signedPreKey", JwkSerializer.encodeX25519(signedPreKey));
        if (oneTimePreKey != null) {
            root.put("oneTimePreKey", JwkSerializer.encodeX25519(oneTimePreKey));
        }

        Path keysFile = vaultDir.resolve(KEYS_FILE_NAME);
        Path temp = vaultDir.resolve(KEYS_FILE_NAME + ".tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), root);
            Files.move(temp, keysFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist vault to " + keysFile, e);
        }
    }

    /** Directory holding this vault's persistent state. */
    public Path vaultDir() {
        return vaultDir;
    }

    // ==================================================================================
    // Vault interface — forwarding to the in-memory delegate
    // ==================================================================================

    @Override public ItemRef identity()                                  { return delegate.identity(); }
    @Override public Identity rootIdentity()                             { return delegate.rootIdentity(); }
    @Override public VarSig sign(byte[] message)                         { return delegate.sign(message); }
    @Override public VarSig sign(byte[] message, ItemRef purpose)        { return delegate.sign(message, purpose); }
    @Override public Optional<ItemRef> signingAlgorithm()                { return delegate.signingAlgorithm(); }
    @Override public Optional<MultiKey> signingPublicKey()               { return delegate.signingPublicKey(); }
    @Override public Optional<ContentRef> signingNextKeyDigest()         { return delegate.signingNextKeyDigest(); }
    @Override public Optional<ItemRef> keyAgreementAlgorithm()           { return delegate.keyAgreementAlgorithm(); }
    @Override public Optional<MultiKey> keyAgreementPublicKey()          { return delegate.keyAgreementPublicKey(); }
    @Override public Optional<ContentRef> keyAgreementNextKeyDigest()    { return delegate.keyAgreementNextKeyDigest(); }
    @Override public byte[] agree(java.security.PublicKey peerPublicKey) { return delegate.agree(peerPublicKey); }
    @Override public Optional<MultiKey> publicKey(ItemRef purpose)       { return delegate.publicKey(purpose); }
    @Override public Optional<ContentRef> nextKeyDigest(ItemRef purpose) { return delegate.nextKeyDigest(purpose); }
    @Override public Optional<DatumRef> chainHead(ItemRef purpose)       { return delegate.chainHead(purpose); }
    @Override public long sequence(ItemRef purpose)                      { return delegate.sequence(purpose); }
    @Override public Frame incept(ItemRef purpose)                       { return delegate.incept(purpose); }
    @Override public Frame rotate(ItemRef purpose)                       { return delegate.rotate(purpose); }
    @Override public Frame delegate(ItemRef delegateId, ItemRef purpose, DelegationConditions conditions) {
        return delegate.delegate(delegateId, purpose, conditions);
    }
    @Override public Frame revoke(Object target, ItemRef reason)         { return delegate.revoke(target, reason); }
    @Override public Optional<MultiKey> signedPreKeyPublicKey()          { return delegate.signedPreKeyPublicKey(); }
    @Override public Frame signedPreKeyFrame()                           { return delegate.signedPreKeyFrame(); }
    @Override public Optional<MultiKey> rotateSignedPreKey()             { return delegate.rotateSignedPreKey(); }
    @Override public void destroyOldSignedPreKeys()                      { delegate.destroyOldSignedPreKeys(); }
    @Override public Optional<KeyPair> consumeSignedPreKey(byte[] rawPubKey) {
        return delegate.consumeSignedPreKey(rawPubKey);
    }
    @Override public Optional<MultiKey> oneTimePreKeyPublicKey()         { return delegate.oneTimePreKeyPublicKey(); }
    @Override public Optional<Frame> oneTimePreKeyFrame()                { return delegate.oneTimePreKeyFrame(); }
    @Override public Optional<KeyPair> consumeOneTimePreKey(byte[] rawPubKey) {
        return delegate.consumeOneTimePreKey(rawPubKey);
    }
    @Override public void openSessionTo(ItemRef peerIid, MultiKey peerIkPub, MultiKey peerSpkPub, MultiKey peerOtpkPub) {
        delegate.openSessionTo(peerIid, peerIkPub, peerSpkPub, peerOtpkPub);
    }
    @Override public DoubleRatchet.EncryptedMessage encryptInSession(ItemRef peerIid, byte[] plaintext) {
        return delegate.encryptInSession(peerIid, plaintext);
    }
    @Override public byte[] decryptInSession(ItemRef peerIid, DoubleRatchet.EncryptedMessage message) {
        return delegate.decryptInSession(peerIid, message);
    }
    @Override public boolean hasSessionWith(ItemRef peerIid)             { return delegate.hasSessionWith(peerIid); }
    @Override public void closeSession(ItemRef peerIid)                  { delegate.closeSession(peerIid); }
    @Override public boolean isLocked()                                  { return delegate.isLocked(); }
    @Override public void lock()                                         { delegate.lock(); }
    @Override public boolean canSign()                                   { return delegate.canSign(); }
    @Override public boolean canKeyAgree()                               { return delegate.canKeyAgree(); }
}
