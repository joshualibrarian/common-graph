package dev.everydaythings.graph.item.relation;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ContentID;
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

/**
 * A semantic relation — a filled frame connecting items by meaning.
 *
 * <p>Based on Fillmore's Case Grammar / Frame Semantics. The predicate names
 * the frame (e.g., HYPERNYM, LIKES, TITLE). The bindings fill the roles
 * (e.g., THEME → subject, TARGET → object, NAME → literal value).
 *
 * <p>BODY fields (contribute to RID — semantic identity):
 * <ul>
 *   <li>{@code version} — format version</li>
 *   <li>{@code predicate} — the frame type (a Sememe IID)</li>
 *   <li>{@code bindings} — role→target map filling the frame's slots</li>
 *   <li>{@code createdAt} — timestamp</li>
 * </ul>
 *
 * @deprecated Relations are frames. Use {@link dev.everydaythings.graph.item.component.FrameBody}
 * and {@link dev.everydaythings.graph.item.component.FrameRecord} instead.
 * This class is retained as a helper for constructing frames with relational
 * semantics and for backward compatibility during migration.
 *
 * <p>RECORD-only fields (don't affect RID):
 * <ul>
 *   <li>{@code rid} — hash of BODY bytes (semantic identity)</li>
 *   <li>{@code authorKey} — who asserts this claim</li>
 *   <li>{@code signing} — cryptographic signature</li>
 *   <li>{@code inputText} — raw input text that produced this relation (debugging/audit)</li>
 * </ul>
 *
 * <p>The same assertion by different signers produces the same RID. Multiple
 * signed records for the same RID represent independent attestations of the
 * same fact. Storage is keyed by RECORD CID (content-addressed).
 */
@Deprecated
@Getter
public final class Relation implements Signing.Target {

    /** Canonical type key for relations as component entries. */
    public static final String TYPE_KEY = "cg:type/relation";

    /** Deterministic ItemID for the relation type. */
    public static final ItemID TYPE_ID = ItemID.fromString(TYPE_KEY);

    // ==================================================================================
    // BODY fields — contribute to RID (semantic identity)
    // ==================================================================================

    @Canon(order = 0)
    private final int version = 1;

    @Canon(order = 1)
    private final ItemID predicate;

    @Canon(order = 2)
    private final Map<ItemID, dev.everydaythings.graph.item.component.BindingTarget> bindings;

    @Canon(order = 3)
    private final Instant createdAt;

    // ==================================================================================
    // RECORD-only fields — do not affect RID
    // ==================================================================================

    @Canon(order = 4, isBody = false)
    private final ContentID rid;

    @Canon(order = 5, isBody = false)
    private SigningPublicKey authorKey;

    @Canon(order = 6, isBody = false)
    private Signing signing;

    /** Raw input text that produced this relation (nullable, for debugging/auditing). */
    @Canon(order = 7, isBody = false)
    private final String inputText;

    private final transient byte[] body;

    @Override
    public HashID targetId() {
        return rid;
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * No-arg constructor for Canonical decoding.
     * Fields are populated via reflection.
     */
    private Relation() {
        this.predicate = null;
        this.bindings = null;
        this.createdAt = null;
        this.rid = null;
        this.inputText = null;
        this.body = null;
    }

    @Builder(builderClassName = "RelationBuilder")
    private Relation(@NonNull ItemID predicate,
                     @Singular("bind") Map<ItemID, dev.everydaythings.graph.item.component.BindingTarget> bindings,
                     Clock clock,
                     String inputText) {
        this.predicate = predicate;
        this.bindings = bindings;
        this.createdAt = clock != null ? clock.instant() : Instant.now();
        this.inputText = inputText;

        body = encodeBinary(Scope.BODY);
        rid = new ContentID(Hash.DEFAULT.digestToMultihash(body).toBytes());
    }

    // ==================================================================================
    // Signing
    // ==================================================================================

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
    // Binding accessors (vocabulary-agnostic — no dependency on Role)
    // ==================================================================================

    /**
     * Get the target bound to a specific role.
     *
     * @param role The role IID (e.g., ThematicRole.Theme.SEED.iid())
     * @return The target, or null if role not filled
     */
    public dev.everydaythings.graph.item.component.BindingTarget binding(ItemID role) {
        return bindings != null ? bindings.get(role) : null;
    }

    /**
     * Get the ItemID bound to a specific role (convenience for IidTarget bindings).
     *
     * @param role The role IID
     * @return The bound item's IID, or null if role not filled or not an IidTarget
     */
    public ItemID bindingId(ItemID role) {
        dev.everydaythings.graph.item.component.BindingTarget target = binding(role);
        return target instanceof dev.everydaythings.graph.item.component.BindingTarget.IidTarget iidTarget ? iidTarget.iid() : null;
    }

    // ==================================================================================
    // Frame Bridge — Phase 2 of Frame Unification
    // ==================================================================================

    /**
     * Convert this Relation to a FrameBody.
     *
     * <p>Extracts the predicate and bindings to produce the body/record
     * split's assertion half. The theme must be supplied — it's the item
     * this assertion is about (typically the THEME binding's target).
     *
     * @param theme the item this assertion is about
     * @return a FrameBody representing the same semantic assertion
     */
    public dev.everydaythings.graph.item.component.FrameBody toFrameBody(ItemID theme) {
        return dev.everydaythings.graph.item.component.FrameBody.fromRelation(this, theme);
    }

    /**
     * Convert this Relation to a FrameBody, using the THEME binding as the theme.
     *
     * @return a FrameBody, or null if no THEME binding exists
     */
    public dev.everydaythings.graph.item.component.FrameBody toFrameBody() {
        ItemID theme = subject();  // subject() reads THEME binding
        if (theme == null) return null;
        return toFrameBody(theme);
    }

    // ==================================================================================
    // Backwards compatibility — bridging helpers during migration
    // ==================================================================================

    /**
     * Get the THEME binding as a subject-like accessor.
     *
     * <p>During migration from subject/object to bindings, this provides
     * the most common binding (THEME) via the familiar name.
     *
     * @return The THEME binding's ItemID, or null
     * @deprecated Use {@code bindingId(ThematicRole.Theme.SEED.iid())} directly
     */
    @Deprecated
    public ItemID subject() {
        // ThematicRole.Theme.SEED.iid() — but we don't want to depend on Role here.
        // The canonical key is "cg.role:theme" → deterministic ItemID.
        return bindingId(ItemID.fromString("cg.role:theme"));
    }

    /**
     * Get the TARGET binding as an object-like accessor.
     *
     * @return The TARGET binding as a Target, or null
     * @deprecated Use {@code binding(ThematicRole.Target.SEED.iid())} directly
     */
    @Deprecated
    public dev.everydaythings.graph.item.component.BindingTarget object() {
        return binding(ItemID.fromString("cg.role:target"));
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
