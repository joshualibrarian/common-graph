package dev.everydaythings.graph.item.relation;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.component.Factory;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.RelationID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.trust.SigningPublicKey;
import io.ipfs.multihash.Multihash;
import lombok.Getter;
import lombok.NonNull;
import lombok.Builder;
import lombok.Singular;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Getter
public final class Relation implements Signing.Target {

    /** Canonical type key for relations as component entries. */
    public static final String TYPE_KEY = "cg:type/relation";

    /** Deterministic ItemID for the relation type. */
    public static final ItemID TYPE_ID = ItemID.fromString(TYPE_KEY);

    @Canon(order = 0)
    private final int version = 1;

    @Canon(order = 1, isBody = false)
    private final RelationID rid;                 // set at commit()

    @Canon(order = 2)
    private final ItemID subject;

    @Canon(order = 3)
    private final ItemID predicate;

    @Canon(order = 4)
    private final Target object;       // optional

    @Canon(order = 5)
    private final Map<ItemID, Target> qualifiers;

    @Canon(order = 6)
    private final Instant createdAt;   // filled at sign/commit if null

    // TODO: wrap signatures in an object and return defensively
    @Canon(order = 7, isBody = false)
    private SigningPublicKey authorKey;

    @Canon(order = 8, isBody = false)
    private Signing signing;

    private final transient byte[] body;

    @Override
    public HashID targetId() {
        return rid;
    }

    /**
     * Marker interface for relation objects/qualifiers.
     *
     * <p>Implementations:
     * <ul>
     *   <li>{@link IidTarget} - reference to another Item (encodes as byte string)</li>
     *   <li>{@link Literal} - literal value (encodes as array [valueType, payload])</li>
     * </ul>
     */
    public interface Target extends Canonical {

        /**
         * Decode a Target from CBOR, dispatching to the appropriate implementation.
         */
        @Factory
        static Target fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;

            // IidTarget encodes as byte string (ItemID bytes)
            if (node.getType() == CBORType.ByteString) {
                return IidTarget.fromCborTree(node);
            }

            // Literal encodes as array [valueType, payload]
            if (node.getType() == CBORType.Array) {
                return Canonical.fromCborTree(node, Literal.class, Scope.RECORD);
            }

            throw new IllegalArgumentException("Cannot decode Target from CBOR type: " + node.getType());
        }
    }

    /**
     * Object/qualifier target that references another Item.
     *
     * <p>Encodes as a CBOR byte string containing the ItemID multihash.
     *
     * <p>TODO: This will be replaced by Link (tag 6) in the CG-CBOR redesign.
     */
    public static final class IidTarget implements Target {

        private ItemID iid;

        public IidTarget() {}

        public IidTarget(ItemID iid) {
            this.iid = Objects.requireNonNull(iid, "iid");
        }

        public IidTarget(byte[] bytes) {
            this.iid = new ItemID(bytes);
        }

        public ItemID iid() { return iid; }

        public static IidTarget of(ItemID iid) { return new IidTarget(iid); }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return iid != null ? CBORObject.FromByteArray(iid.encodeBinary()) : CBORObject.Null;
        }

        @Factory
        public static IidTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            return new IidTarget(node.GetByteString());
        }
    }

    /** Convenience factory for item-object relations. */
    public static IidTarget iid(ItemID iid) { return IidTarget.of(iid); }

    /**
     * No-arg constructor for Canonical decoding.
     * Fields are populated via reflection.
     */
    private Relation() {
        this.subject = null;
        this.predicate = null;
        this.object = null;
        this.qualifiers = null;
        this.createdAt = null;
        this.rid = null;
        this.body = null;
    }

    // ---- Builder (subject & predicate required) ----
    @Builder(builderClassName = "RelationBuilder")
    private Relation(@NonNull ItemID subject,
                     @NonNull ItemID predicate,
                     Target object,
                     @Singular("qualify") Map<ItemID, Target> qualifiers,
                     Clock clock) {
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.qualifiers = qualifiers;
        this.createdAt = clock != null ? clock.instant() : Instant.now();

        body = encodeBinary(Scope.BODY);
        // rid is the content hash of the signed body (not the body bytes themselves)
        rid = new RelationID(Hash.DEFAULT.digestToMultihash(body).toBytes());
    }

    public Relation sign(Signer signer) {
        if (!verify()) throw new IllegalStateException("verification failed!");
        Objects.requireNonNull(signer, "signer required for signing");

        this.authorKey = signer.publicKey();
        Signing.Sig sig = signer.sign(rid, body, null, null);
        this.signing = Signing.of(rid, targetBodyHash(), sig);

        return this;
    }

    public boolean verify() {
        byte[] actualBody = encodeBinary(Canonical.Scope.BODY);
        Multihash actualHash = Hash.DEFAULT.digestToMultihash(actualBody);
        Objects.requireNonNull(actualHash, "missing hash");

        return (Arrays.equals(actualBody, body)
                && Arrays.equals(actualHash.toBytes(), rid.encodeBinary()));
    }

    // ==================================================================================
    // Decoding
    // ==================================================================================

    /**
     * Decode a Relation from CBOR bytes.
     */
    public static Relation decode(byte[] bytes) {
        return Canonical.decodeBinary(bytes, Relation.class, Canonical.Scope.RECORD);
    }

    /**
     * Decode a Relation from CBOR bytes with explicit scope.
     */
    public static Relation decode(byte[] bytes, Canonical.Scope scope) {
        return Canonical.decodeBinary(bytes, Relation.class, scope);
    }

    // NOTE: Do NOT add a fromCborTree(CBORObject) method that just delegates to Canonical!
    // It would cause infinite recursion because Canonical.fromCborTree()
    // looks for such methods and invokes them, expecting custom decoding.
    // Relation uses standard field-based decoding, so let Canonical handle it.
}
