package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.vault.Vault;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.trust.Algorithm;
import dev.everydaythings.graph.trust.CertLog;
import dev.everydaythings.graph.trust.KeyLog;
import dev.everydaythings.graph.trust.Purpose;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.trust.SigningPublicKey;

import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Objects;

/**
 * A Signer is an Item that can cryptographically sign other Items.
 *
 * <p>This is a self-describing type. The class IS the definition.
 *
 * <p>Signers have three key components:
 * <ul>
 *   <li><b>Vault</b> - Local-only, contains private keys (never synced)</li>
 *   <li><b>KeyLog</b> - Syncable stream, public key history</li>
 *   <li><b>CertLog</b> - Syncable stream, certificates issued by this signer</li>
 * </ul>
 *
 * <h2>Construction Patterns</h2>
 *
 * <p><b>Path-based (create or load):</b> A Signer MUST have a path to be CREATED.
 * This ensures private keys have a real place on disk.
 * <pre>{@code
 * // Subclass constructor
 * protected MySigner(Path path, Library library) {
 *     super(path, library);
 * }
 * }</pre>
 *
 * <p><b>Reference (read-only):</b> Referenced signers represent someone else's identity.
 * They have no vault and cannot sign.
 * <pre>{@code
 * protected MySigner(Librarian librarian, Manifest manifest, SigningPublicKey publicKey) {
 *     super(librarian, manifest, publicKey);
 * }
 * }</pre>
 */
@Type(value = Signer.KEY, glyph = "✍️", color = 0xAF644B)
public abstract class Signer extends Item implements Signing.Signer {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/signer";

    public static final Algorithm.Sign ALGORITHM = Algorithm.Sign.ED25519;

    // ==================================================================================
    // Components
    // ==================================================================================

    /**
     * The signer's vault containing the keypair.
     *
     * <p>Local-only component - never synced. Contains private keys.
     * For referenced Signers (others' identities), this is null.
     */
    @Frame(handle = "vault", path = ".vault", localOnly = true)
    private transient Vault vault;

    /**
     * Public key history log.
     *
     * <p>Syncable stream component. Tracks all public keys this signer has used,
     * which keys are current for which purposes, and tombstoned keys.
     */
    @Frame(handle = "keys", path = ".keys", stream = true)
    private KeyLog keyLog;

    /**
     * Certificate log.
     *
     * <p>Syncable stream component. Tracks certificates issued by this signer
     * to attest to other identities, grant trust, etc.
     */
    @Frame(handle = "certs", path = ".certs", stream = true)
    private CertLog certLog;

    /**
     * Human-readable name for this signer.
     *
     * <p>Stored as a relation: (self) --NAME--> Literal.ofText("name").
     * For Hosts this defaults to the hostname; for Librarians it can be
     * set by the user (e.g. "dax", "riker-lib").
     */
    @Frame(key = {"cg.core:name"}, endorsed = false)
    private String name;

    /**
     * The current signing public key.
     *
     * <p>This is cached from the KeyLog for quick access. It's updated on
     * first boot (after key generation) and on reload (from KeyLog state).
     */
    private transient SigningPublicKey publicKey;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Type seed constructor.
     *
     * <p>Creates a minimal Signer instance for use as a type seed.
     * This is NOT a functional Signer - it just represents the type in the graph.
     *
     * @param iid The type's ItemID
     */
    protected Signer(ItemID iid) {
        super(iid);
    }

    /**
     * Hydration constructor for type seeds (no public key required).
     *
     * <p>Type seeds don't have actual signing capability - they're just
     * type markers in the graph. This constructor allows them to be hydrated
     * from the DB for display purposes.
     *
     * @param librarian The librarian performing hydration
     * @param manifest  The manifest to hydrate from
     */
    protected Signer(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    /**
     * Path-based constructor for materialized Signers.
     *
     * <p>Creates or loads a Signer at the given filesystem path.
     * On first boot:
     * <ol>
     *   <li>Vault is created with fresh keypair</li>
     *   <li>KeyLog is created (empty)</li>
     *   <li>CertLog is created (empty)</li>
     *   <li>Public key is published to KeyLog</li>
     * </ol>
     *
     * <p>On reload:
     * <ol>
     *   <li>Vault is loaded from disk</li>
     *   <li>KeyLog is loaded from disk</li>
     *   <li>CertLog is loaded from disk</li>
     *   <li>Current public key is read from KeyLog</li>
     * </ol>
     *
     * @param path    The filesystem path for this signer
     * @param fallbackStore Store to use for fallback queries
     */
    protected Signer(Path path, ItemStore fallbackStore) {
        super(path, fallbackStore);
        // Vault, KeyLog, CertLog are set by Item's hydrate() via FrameTable
        // Key initialization happens in onFullyInitialized()
    }

    /**
     * In-memory constructor for ephemeral Signers.
     *
     * <p>Creates a fresh Signer with in-memory storage. The Signer is fully functional
     * but keys are lost when the JVM exits. Used for testing, demos, and temporary sessions.
     *
     * @param store In-memory store for type lookups and content storage
     * @param inMemoryMarker Marker parameter to distinguish from path-based constructor
     */
    protected Signer(ItemStore store, InMemoryMarker inMemoryMarker) {
        super(store, inMemoryMarker);
        // Vault is created via initializeFreshComponents() → @Component.Field annotation
        // Key initialization happens in onFullyInitialized()
    }

    /**
     * Path-based constructor with librarian reference.
     *
     * <p>Creates a Signer at a filesystem path with a librarian reference.
     * Used for Users with home directories (e.g., {@code <rootPath>/users/alice/}).
     *
     * @param librarian The librarian (provides store access and library)
     * @param path      The filesystem path for this signer's home directory
     */
    protected Signer(Librarian librarian, Path path) {
        super(librarian, path);
        // Vault, KeyLog, CertLog created by initializeFreshComponents()
        // Key initialization happens in onFullyInitialized()
    }

    /**
     * In-memory constructor with librarian reference.
     *
     * <p>Creates an ephemeral Signer with a librarian reference. Used for
     * testing and in-memory user creation when no filesystem path is available.
     *
     * @param librarian The librarian (provides store access and library)
     * @param marker    Marker to distinguish from other constructors
     */
    protected Signer(Librarian librarian, InMemoryMarker marker) {
        super(librarian, marker);
        // Vault is created via initializeFreshComponents()
        // Key initialization happens in onFullyInitialized()
    }

    /**
     * Initialize keys after all components are ready.
     *
     * <p>This override sets up the signing keys from the Vault component.
     * Subclasses MUST call {@code super.onFullyInitialized()} at the START
     * of their override to ensure keys are ready before any signing operations.
     */
    @Override
    protected void onFullyInitialized() {
        super.onFullyInitialized();  // Populate action table

        if (freshBoot) {
            initializeKeys();
        } else {
            loadCurrentKey();
        }
    }

    /**
     * Create a referenced Signer from a manifest (no vault - read-only).
     *
     * <p>Referenced Signers cannot sign ({@link #canSign()} returns false).
     * They represent someone else's identity for verification purposes.
     *
     * @param librarian The librarian (for context)
     * @param manifest  The manifest containing the Signer's public state
     * @param publicKey The Signer's public key (loaded from manifest/KeyLog)
     */
    protected Signer(Librarian librarian, Manifest manifest, SigningPublicKey publicKey) {
        super(librarian, manifest);
        this.vault = null;  // No vault for referenced Signers
        this.keyLog = null; // No local KeyLog
        this.certLog = null; // No local CertLog
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
    }

    // ==================================================================================
    // Key Initialization (First Boot)
    // ==================================================================================

    /**
     * Initialize keys on first boot.
     *
     * <p>Builds SigningPublicKey from Vault's public key, then publishes
     * to KeyLog with AddKey and SetCurrent operations. Also generates and
     * publishes a TLS certificate to CertLog.
     */
    private void initializeKeys() {
        if (vault == null) {
            throw new IllegalStateException("Vault not initialized - cannot initialize keys");
        }

        if (!vault.canSign()) {
            throw new IllegalStateException("Vault has no signing key");
        }

        // Get public key from vault (private key stays in vault)
        PublicKey jcaPublicKey = vault.signingPublicKey()
                .orElseThrow(() -> new IllegalStateException("Vault has no signing public key"));

        // Build SigningPublicKey wrapper
        this.publicKey = SigningPublicKey.builder()
                .jcaPublicKey(jcaPublicKey)
                .owner(this.iid())
                .build();

        // Publish to KeyLog if available
        // Note: KeyLog might not be initialized yet in some bootstrap scenarios
        // (e.g., Librarian which has special initialization order)
        if (keyLog != null) {
            publishKeyToLog();
        }

        // Publish TLS certificate to CertLog if available
        if (certLog != null) {
            publishTlsCertToLog();
        }
    }

    /**
     * Publish the current public key to the KeyLog.
     *
     * <p>This is called on first boot to record the signing key in the public
     * key history. The key CID becomes the reference used for signing events.
     */
    private void publishKeyToLog() {
        if (keyLog == null || publicKey == null) {
            return;
        }

        // Sequence numbers start at 1
        long seq = 1;

        // Add the key to the log (this Signer implements Signing.Signer)
        keyLog.append(new KeyLog.AddKey(publicKey), seq++, this, Signing.defaultHasher());

        // Compute the key CID for SetCurrent
        byte[] keyCid = KeyLog.keyCidBytes(publicKey);

        // Set it as current for signing
        keyLog.append(new KeyLog.SetCurrent(keyCid, Purpose.SIGN, true), seq, this, Signing.defaultHasher());
    }

    /**
     * Publish the TLS certificate to the CertLog.
     *
     * <p>This is called on first boot to record the X.509 TLS certificate
     * generated from the signing key. The certificate enables TLS authentication
     * where peers can verify identity via the CertLog.
     */
    private void publishTlsCertToLog() {
        if (certLog == null || vault == null || publicKey == null) {
            return;
        }

        // Get the X.509 certificate from the vault
        X509Certificate x509 = vault.signingCertificate()
                .orElse(null);
        if (x509 == null) {
            // No certificate available - skip TLS cert publishing
            return;
        }

        // Compute the key CID that this cert is for
        byte[] keyCid = KeyLog.keyCidBytes(publicKey);

        // Create TlsCert record from the X.509 certificate
        CertLog.TlsCert tlsCert = CertLog.TlsCert.fromX509(keyCid, x509);

        // Sequence numbers start at 1
        long seq = 1;

        // Add the TLS cert to the log (this Signer implements Signing.Signer)
        certLog.append(new CertLog.AddTlsCert(tlsCert), seq++, this, Signing.defaultHasher());

        // Compute the cert CID for SetCurrentTls
        byte[] certCid = CertLog.tlsCertCidBytes(tlsCert);

        // Set it as the current TLS certificate
        certLog.append(new CertLog.SetCurrentTls(certCid, true), seq, this, Signing.defaultHasher());
    }

    // ==================================================================================
    // Signing.Signer Implementation
    // ==================================================================================

    /**
     * The key reference (content ID of the public key).
     *
     * <p>This is the hash of the public key, used to identify which key
     * produced a signature.
     */
    @Override
    public byte[] keyRef() {
        if (publicKey == null) {
            throw new IllegalStateException("No public key available");
        }
        return KeyLog.keyCidBytes(publicKey);
    }

    /**
     * Sign raw data.
     *
     * <p>The private key never leaves the vault - signing happens in-place.
     *
     * @param data The bytes to sign
     * @return The signature bytes
     */
    @Override
    public byte[] signRaw(byte[] data) {
        if (!canSign()) {
            throw new IllegalStateException("Cannot sign: no vault available");
        }
        return vault.sign(data);
    }

    // ==================================================================================
    // Key Loading (Reload)
    // ==================================================================================

    /**
     * Load the current signing key on reload.
     *
     * <p>First tries to get the current key from KeyLog. If KeyLog is empty
     * or unavailable, falls back to building from Vault.
     */
    private void loadCurrentKey() {
        // Try to get current key from KeyLog
        if (keyLog != null) {
            keyLog.currentSigningKey().ifPresent(key -> this.publicKey = key);
        }

        // Fallback: build from Vault if KeyLog didn't have it
        if (publicKey == null && vault != null) {
            vault.signingPublicKey().ifPresent(jcaPublicKey -> {
                this.publicKey = SigningPublicKey.builder()
                        .jcaPublicKey(jcaPublicKey)
                        .owner(this.iid())
                        .build();
            });
        }

        if (publicKey == null) {
            throw new IllegalStateException("Cannot load public key: no KeyLog or Vault available");
        }
    }

    // ==================================================================================
    // Signing Operations
    // ==================================================================================

    /**
     * Check if this Signer has a vault and can sign.
     *
     * <p>Materialized Signers (with vault) can sign.
     * Referenced Signers (others' identities) cannot sign.
     *
     * @return true if this Signer can sign, false if it's a reference only
     */
    public boolean canSign() {
        return vault != null && vault.canSign();
    }

    /**
     * Get the human-readable name of this signer.
     */
    public String name() {
        return name;
    }

    /**
     * Set the human-readable name of this signer.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Rename this signer via verb dispatch.
     *
     * @param ctx  the action context
     * @param name the new name
     * @return status message describing the rename
     */
    @Verb(value = Sememe.RENAME, doc = "Rename this signer")
    public String rename(ActionContext ctx,
                         @Param(value = "name", doc = "New name") String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        String oldName = this.name;
        setName(name);
        if (canSign()) commit(this);
        return oldName != null
                ? "Renamed '" + oldName + "' → '" + name + "'"
                : "Named '" + name + "'";
    }

    /**
     * Get the vault (for subclasses).
     */
    protected Vault vault() {
        return vault;
    }

    /**
     * Get the KeyLog (for subclasses).
     */
    protected KeyLog keyLog() {
        return keyLog;
    }

    /**
     * Get the CertLog (for subclasses).
     */
    protected CertLog certLog() {
        return certLog;
    }

    /**
     * Sign a target (Manifest, Relation, etc.).
     *
     * <p>This uses sign-in-place semantics: the private key never leaves the Vault.
     * For hardware-backed vaults (Secure Enclave, TPM, YubiKey), the signing
     * operation happens entirely within the secure hardware.
     *
     * @param targetId  The ID of the thing being signed (vid, rid, etc.)
     * @param rawTarget The raw bytes to sign
     * @param role      Optional role string (null for default)
     * @param bound     Optional additional authenticated data
     * @return The signature
     * @throws IllegalStateException if this is a referenced Signer without a vault
     */
    public Signing.Sig sign(HashID targetId, byte[] rawTarget, String role, byte[] bound) {
        if (!canSign()) {
            throw new IllegalStateException(
                    "Cannot sign: this is a referenced Signer without a vault");
        }

        // Sign-in-place: private key never leaves the vault
        byte[] signatureBytes = vault.sign(rawTarget);

        // Get clock from librarian if available, otherwise use system clock.
        // During construction, librarian.clock() may return null since field initializers
        // haven't run yet when called from super() chain.
        Clock clock = (librarian != null) ? librarian.clock() : null;
        if (clock == null) {
            clock = Clock.systemUTC();
        }

        return Signing.Sig.builder()
                .algorithm(ALGORITHM)
                .keyId(publicKey.keyId())
                .roleId(null)  // TODO: map role string to sememe ItemID
                .aad(bound)
                .claimedAtEpochMillis(clock.instant().toEpochMilli())
                .signature(signatureBytes)
                .build();
    }

    /**
     * Sign a Signing.Target (Manifest, Relation, EncryptedEnvelope).
     */
    public Signing.Sig sign(Signing.Target target, String role, byte[] bound) {
        return sign(target.targetId(), target.bodyToSign(), role, bound);
    }

    /**
     * Get the public key (for Manifest.sign() and Relation.sign()).
     */
    public SigningPublicKey publicKey() {
        return publicKey;
    }
}
