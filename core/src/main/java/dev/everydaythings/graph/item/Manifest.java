package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.ComponentEntry;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.VersionID;
import dev.everydaythings.graph.trust.GraphPublicKey;
import dev.everydaythings.graph.trust.SigningPublicKey;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.trust.Signing;
import lombok.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Manifest = the version-defining "endorsed set" for an item version.
 *
 * <p>A Manifest contains:
 * <ul>
 *   <li><b>Identity</b>: iid, parents, type - identifies this version</li>
 *   <li><b>State</b>: ItemState containing all tables (content, relations, policy)</li>
 *   <li><b>Signing</b>: authorKey and signature for verification</li>
 * </ul>
 *
 * <p>The {@link ItemState} is shared between Manifest and Item, ensuring consistent
 * handling of the five core tables.
 */
@Getter
@Canonical.Canonization
public final class Manifest implements Signing.Target {

    // --- IDENTITY ---
    @Canon(order = 0)
    private int version = 1;

    @Canon(order = 1)
    private ItemID iid;

    @Canon(order = 2)
    private List<VersionID> parents;

    @Canon(order = 3)
    private ItemID type;

    // --- STATE (the five tables) ---
    @Canon(order = 4)
    private ItemState state;

    // --- SIGNING (non-BODY) ---
    @Canon(order = 5, isBody = false)
    private SigningPublicKey authorKey;

    @Canon(order = 6, isBody = false)
    private Signing signature;

    // --- DERIVED CACHES (NOT serialized) ---
    @Getter(AccessLevel.NONE)
    private transient volatile byte[] bodyBytes;

    @Getter(AccessLevel.NONE)
    private transient volatile VersionID vid;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    @Builder(builderClassName = "ManifestBuilder")
    private Manifest(
            @NonNull ItemID iid,
            @Singular("parent") List<VersionID> parents,
            ItemID type,
            ItemState state
    ) {
        this.iid = Objects.requireNonNull(iid, "iid");
        this.parents = (parents == null) ? List.of() : List.copyOf(parents);
        this.type = type;
        this.state = state != null ? state : new ItemState();

        // Precompute caches for newly-built instances
        this.bodyBytes = encodeBinary(Canonical.Scope.BODY);
        this.vid = new VersionID(Hash.DEFAULT.digest(this.bodyBytes), Hash.DEFAULT);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private Manifest() {
        // Fields set via reflection during decode
    }

    // ==================================================================================
    // State Accessors (convenience delegates)
    // ==================================================================================

    /**
     * Get the component entries.
     */
    public List<ComponentEntry> components() {
        return state != null ? state.componentSnapshot() : List.of();
    }

    // ==================================================================================
    // VID and Body
    // ==================================================================================

    /**
     * Canonical BODY bytes (derived).
     * Returned value is a clone to prevent accidental mutation.
     */
    public byte[] bodyBytes() {
        byte[] local = bodyBytes;
        if (local == null) {
            synchronized (this) {
                local = bodyBytes;
                if (local == null) {
                    local = encodeBinary(Canonical.Scope.BODY);
                    bodyBytes = local;
                }
            }
        }
        return local.clone();
    }

    /**
     * VersionID (derived from BODY bytes).
     */
    public VersionID vid() {
        VersionID local = vid;
        if (local == null) {
            synchronized (this) {
                local = vid;
                if (local == null) {
                    byte[] body = encodeBinary(Canonical.Scope.BODY);
                    local = new VersionID(Hash.DEFAULT.digest(body), Hash.DEFAULT);
                    vid = local;
                }
            }
        }
        return local;
    }

    // ==================================================================================
    // Signing
    // ==================================================================================

    public boolean isSigned() {
        return authorKey != null && signature != null;
    }

    /**
     * Chainable signer; signs BODY bytes (Ed25519-friendly).
     * NOTE: this mutates the inline signature fields.
     */
    public Manifest sign(@NonNull Signer signer) {
        Objects.requireNonNull(signer, "signer");
        if (!verifyBody()) throw new IllegalStateException("BODY verification failed");

        this.authorKey = signer.publicKey();
        Signing.Sig sig = signer.sign(vid(), bodyBytes(), null, null);
        this.signature = Signing.of(vid(), targetBodyHash(), sig);
        return this;
    }

    /**
     * Re-encode and compare with cached BODY to guard against accidental drift.
     */
    public boolean verifyBody() {
        byte[] cached = this.bodyBytes;
        byte[] now = encodeBinary(Canonical.Scope.BODY);
        return cached == null || Arrays.equals(now, cached);
    }

    /**
     * Verify the inline signature, if present.
     */
    public boolean verifySignature() {
        if (!isSigned()) return false;

        try {
            Method m = signature.getClass().getMethod(
                    "verify",
                    GraphPublicKey.class,
                    HashID.class,
                    byte[].class,
                    byte[].class,
                    byte[].class
            );

            Object ok = m.invoke(signature, authorKey, vid(), bodyBytes(), null, null);
            return ok instanceof Boolean b && b;

        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException(
                    "Signing.verify(...) method not found; update Manifest.verifySignature() to match Signing API",
                    e
            );
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Signature verification failed unexpectedly", e);
        }
    }

    @Override
    public HashID targetId() {
        return vid();
    }

    // ==================================================================================
    // Decode
    // ==================================================================================

    /**
     * Decode a Manifest from CBOR bytes.
     */
    public static Manifest decode(byte[] bytes) {
        return Canonical.decodeBinary(bytes, Manifest.class, Canonical.Scope.RECORD);
    }

    /**
     * Decode a Manifest from CBOR bytes with explicit scope.
     */
    public static Manifest decode(byte[] bytes, Canonical.Scope scope) {
        return Canonical.decodeBinary(bytes, Manifest.class, scope);
    }

    // NOTE: Do NOT add a fromCborTree(CBORObject) method here!
    // It would cause infinite recursion because Canonical.fromCborTree()
    // looks for such methods and invokes them, expecting custom decoding.
    // Manifest uses standard field-based decoding, so let Canonical handle it.
}
