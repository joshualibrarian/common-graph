package dev.everydaythings.graph.item;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.encoding.Digest;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.frame.FrameEndorsement;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.item.user.SignerOld;
import dev.everydaythings.graph.language.RuntimeVocabulary;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.crypt.GraphPublicKey;
import dev.everydaythings.graph.crypt.SigningPublicKey;
import dev.everydaythings.graph.crypt.Signing;
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
public final class ManifestOld implements Signing.Target {

    // --- IDENTITY ---
    @Canon(order = 0)
    private int version = 1;

    @Canon(order = 1)
    private ItemID iid;

    @Canon(order = 2)
    private List<ContentID> parents;

    // PHASE 6: was `ItemID type` (sememe IID). Now carries the creating
    // implementation as (platform, class-name). The sememe relationship
    // lives in the item's IMPLEMENTS frame, not on the manifest.
    @Getter(AccessLevel.NONE)
    @Canon(order = 3)
    private Binding implementation;

    // --- STATE (the five tables) ---
    @Canon(order = 4)
    private ItemState state;

    // --- SIGNING (non-BODY) ---
    @Canon(order = 5, isBody = false)
    private SigningPublicKey authorKey;

    @Canon(order = 6, isBody = false)
    private Signing signature;

    // --- ITEM BINDINGS ---
    @Getter(AccessLevel.NONE)
    @Canon(order = 7)
    private List<Binding> identityBindingsList;

    @Getter(AccessLevel.NONE)
    @Canon(order = 8, isBody = false)
    private List<Binding> nonIdentityBindingsList;

    // --- DERIVED CACHES (NOT serialized) ---
    @Getter(AccessLevel.NONE)
    private transient volatile byte[] bodyBytes;

    @Getter(AccessLevel.NONE)
    private transient volatile ContentID vid;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    @Builder(builderClassName = "ManifestBuilder")
    private ManifestOld(
            @NonNull ItemID iid,
            @Singular("parent") List<ContentID> parents,
            Binding implementation,
            ItemState state,
            @Singular("binding") List<Binding> bindings
    ) {
        this.iid = Objects.requireNonNull(iid, "iid");
        this.parents = (parents == null) ? List.of() : List.copyOf(parents);
        this.implementation = implementation;
        this.state = state != null ? state : new ItemState();

        // Old code path: identity flag has been removed from Binding. Treat all
        // bindings as identity-bearing — the new model uses Body vs Record membership
        // instead, and ManifestOld is being phased out.
        if (bindings != null && !bindings.isEmpty()) {
            this.identityBindingsList = List.copyOf(bindings);
            this.nonIdentityBindingsList = null;
        }

        // Precompute caches for newly-built instances
        this.bodyBytes = encodeBinary(Canonical.Scope.BODY);
        this.vid = new ContentID(Digest.Sha256.digest(this.bodyBytes), Digest.Sha256.MULTIHASH_TYPE);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private ManifestOld() {
        // Fields set via reflection during decode
    }

    // ==================================================================================
    // State Accessors (convenience delegates)
    // ==================================================================================

    /**
     * Get the endorsed frames (endorsements table snapshot).
     */
    public List<FrameOld> components() {
        if (state == null) return List.of();
        var table = state.frames();
        return table != null ? table.stream().toList() : List.of();
    }

    /**
     * Get the frame endorsements from this manifest's state.
     *
     * <p>Returns endorsements if available, or an empty list
     * for manifests with no endorsed frames.
     */
    public List<FrameEndorsement> endorsements() {
        return state != null ? state.endorsements() : List.of();
    }

    // ==================================================================================
    // Binding Accessors
    // ==================================================================================

    /**
     * All item-level bindings (both identity and non-identity).
     */
    public List<Binding> bindings() {
        if (identityBindingsList == null && nonIdentityBindingsList == null) return List.of();
        List<Binding> all = new java.util.ArrayList<>();
        if (identityBindingsList != null) all.addAll(identityBindingsList);
        if (nonIdentityBindingsList != null) all.addAll(nonIdentityBindingsList);
        return List.copyOf(all);
    }

    /**
     * Look up a binding by role (simple key match, searches all bindings).
     */
    public Binding binding(ItemID role) {
        if (identityBindingsList != null) {
            for (Binding b : identityBindingsList) {
                if (b.isSimpleKey() && role.equals(b.role())) return b;
            }
        }
        if (nonIdentityBindingsList != null) {
            for (Binding b : nonIdentityBindingsList) {
                if (b.isSimpleKey() && role.equals(b.role())) return b;
            }
        }
        return null;
    }

    /**
     * Identity bindings only (contribute to VID).
     */
    public List<Binding> identityBindings() {
        return identityBindingsList != null ? identityBindingsList : List.of();
    }

    /**
     * Non-identity bindings (record-scope, don't affect VID).
     */
    public List<Binding> nonIdentityBindings() {
        return nonIdentityBindingsList != null ? nonIdentityBindingsList : List.of();
    }

    /**
     * Look up a non-identity binding by role (simple key match).
     */
    public Binding nonIdentityBinding(ItemID role) {
        if (nonIdentityBindingsList == null) return null;
        for (Binding b : nonIdentityBindingsList) {
            if (b.isSimpleKey() && role.equals(b.role())) return b;
        }
        return null;
    }

    // ==================================================================================
    // Implementation Accessors
    // ==================================================================================

    /**
     * The creating implementation binding: platform + class/module name.
     *
     * <p>The binding's key is the platform IID (e.g., Java, Python) and
     * its target is a Literal containing the implementation class name.
     */
    public Binding implementation() { return implementation; }

    /**
     * The platform that created this item (language/runtime IID).
     *
     * @return the platform sememe IID (e.g., RuntimeVocabulary.Java), or null
     */
    public ItemID platform() {
        return implementation != null ? implementation.role() : null;
    }

    /**
     * The implementation class/module name as a string.
     *
     * @return the implementation name, or null if not set
     */
    public String implementationName() {
        if (implementation == null) return null;
        if (implementation.target() instanceof Literal lit) return lit.asText();
        return null;
    }

    /**
     * Resolve the Java class for this manifest's implementation.
     *
     * @return the Java class, or null if not a Java implementation or class not found
     */
    public Class<?> implementationClass() {
        if (implementation == null) return null;
        if (implementation.target() instanceof Literal lit) {
            try {
                return lit.asJavaClass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Build an implementation binding for a Java class.
     *
     * <p>Convenience factory for the common case of creating a manifest
     * for a Java item.
     */
    public static Binding javaImplementation(Class<?> clazz) {
        return new Binding(
                RuntimeVocabulary.Java.IID,
                Literal.ofText(clazz.getName()));
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
     * ContentID (derived from BODY bytes).
     */
    public ContentID vid() {
        ContentID local = vid;
        if (local == null) {
            synchronized (this) {
                local = vid;
                if (local == null) {
                    byte[] body = encodeBinary(Canonical.Scope.BODY);
                    local = new ContentID(Digest.Sha256.digest(body), Digest.Sha256.MULTIHASH_TYPE);
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
    public ManifestOld sign(@NonNull SignerOld signer) {
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
    public static ManifestOld decode(byte[] bytes) {
        return Canonical.decodeBinary(bytes, ManifestOld.class, Canonical.Scope.RECORD);
    }

    /**
     * Decode a Manifest from CBOR bytes with explicit scope.
     */
    public static ManifestOld decode(byte[] bytes, Canonical.Scope scope) {
        return Canonical.decodeBinary(bytes, ManifestOld.class, scope);
    }

    // NOTE: Do NOT add a fromCborTree(CBORObject) method here!
    // It would cause infinite recursion because Canonical.fromCborTree()
    // looks for such methods and invokes them, expecting custom decoding.
    // Manifest uses standard field-based decoding, so let Canonical handle it.
}
