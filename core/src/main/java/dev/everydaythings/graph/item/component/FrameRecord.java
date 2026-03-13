package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.trust.SigningPublicKey;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * A signed envelope attesting to a frame body — the "who said it and when."
 *
 * <p>A FrameRecord wraps a {@link FrameBody} hash with signer identity,
 * timestamp, and cryptographic signature. The body itself is stored
 * separately (content-addressed by hash); the record references it.
 *
 * <p>Multiple records can reference the same body hash — this is how
 * attestations accumulate. Alice asserts a fact, Bob verifies it,
 * Carol endorses it. One body, three records.
 *
 * <p>For endorsed frames, the manifest signature IS the record — no
 * separate FrameRecord is needed. FrameRecord is primarily for
 * unendorsed frames (likes, annotations, trust attestations).
 *
 * @see FrameBody
 * @see FrameEntry
 */
@Getter
public final class FrameRecord implements Signing.Target {

    // ==================================================================================
    // BODY fields — contribute to record identity (record CID)
    // ==================================================================================

    @Canon(order = 0)
    private final ContentID bodyHash;

    @Canon(order = 1)
    private final SigningPublicKey signer;

    @Canon(order = 2)
    private final Instant timestamp;

    // ==================================================================================
    // RECORD-only fields — do not affect record CID
    // ==================================================================================

    @Canon(order = 3, isBody = false)
    private Signing signing;

    // ==================================================================================
    // Content references — the "meat" of the frame's current state.
    // Any combination is valid. The predicate defines what to expect.
    // ==================================================================================

    /** Content-addressed snapshot bytes (document draft, cached state, etc.). */
    @Canon(order = 4, isBody = false)
    private ContentID snapshotCid;

    /** Head of append-only stream (move log, chat messages, key rotations). */
    @Canon(order = 5, isBody = false)
    private ContentID streamHead;

    /** Local-only filesystem resource path (vault keys, database files). */
    @Canon(order = 6, isBody = false)
    private String localPath;

    /** Cached record CID. */
    private transient ContentID cachedRecordCid;

    /** Cached body bytes. */
    private transient byte[] cachedBody;

    /**
     * Construct a FrameRecord.
     *
     * @param bodyHash  hash of the FrameBody being attested (required)
     * @param signer    who attests this (required)
     * @param timestamp when this attestation was made (required)
     */
    public FrameRecord(ContentID bodyHash, SigningPublicKey signer, Instant timestamp) {
        this.bodyHash = Objects.requireNonNull(bodyHash, "bodyHash");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    /**
     * Construct a FrameRecord with content references.
     *
     * @param bodyHash    hash of the FrameBody being attested (required)
     * @param signer      who attests this (required)
     * @param timestamp   when this attestation was made (required)
     * @param snapshotCid content-addressed snapshot bytes (nullable)
     * @param streamHead  head of append-only stream (nullable)
     * @param localPath   local-only filesystem resource path (nullable)
     */
    public FrameRecord(ContentID bodyHash, SigningPublicKey signer, Instant timestamp,
                       ContentID snapshotCid, ContentID streamHead, String localPath) {
        this.bodyHash = Objects.requireNonNull(bodyHash, "bodyHash");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.snapshotCid = snapshotCid;
        this.streamHead = streamHead;
        this.localPath = localPath;
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameRecord() {
        this.bodyHash = null;
        this.signer = null;
        this.timestamp = null;
    }

    // ==================================================================================
    // Identity
    // ==================================================================================

    /**
     * The content identity of this record.
     *
     * <p>Computed from the BODY encoding (bodyHash + signer + timestamp).
     * The signing field is excluded — it's a RECORD-only field.
     */
    public ContentID recordCid() {
        if (cachedRecordCid == null) {
            cachedRecordCid = ContentID.of(bodyBytes());
        }
        return cachedRecordCid;
    }

    private byte[] bodyBytes() {
        if (cachedBody == null) {
            cachedBody = encodeBinary(Scope.BODY);
        }
        return cachedBody;
    }

    // ==================================================================================
    // Signing.Target
    // ==================================================================================

    @Override
    public HashID targetId() {
        return recordCid();
    }

    @Override
    public byte[] bodyToSign() {
        return bodyBytes();
    }

    // ==================================================================================
    // Signing
    // ==================================================================================

    /**
     * Sign this record.
     *
     * @param signer the signer (must match this record's signer key)
     * @return this record, with signing populated
     */
    public FrameRecord sign(Signer signer) {
        Objects.requireNonNull(signer, "signer");
        ContentID cid = recordCid();
        byte[] body = bodyBytes();
        Signing.Sig sig = signer.sign(cid, body, null, null);
        this.signing = Signing.of(cid, targetBodyHash(), sig);
        return this;
    }

    /**
     * Whether this record has been signed.
     */
    public boolean isSigned() {
        return signing != null;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a FrameRecord for a body hash, signed by the given signer.
     *
     * @param body   the frame body being attested
     * @param signer who attests
     * @return a signed FrameRecord
     */
    public static FrameRecord create(FrameBody body, Signer signer) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(signer, "signer");
        FrameRecord record = new FrameRecord(body.hash(), signer.publicKey(), Instant.now());
        record.sign(signer);
        return record;
    }

    /**
     * Create an unsigned FrameRecord (for testing or deferred signing).
     */
    public static FrameRecord unsigned(FrameBody body, SigningPublicKey signerKey) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(signerKey, "signerKey");
        return new FrameRecord(body.hash(), signerKey, Instant.now());
    }

    // ==================================================================================
    // Decoding
    // ==================================================================================

    /**
     * Decode a FrameRecord from CBOR bytes.
     */
    public static FrameRecord decode(byte[] bytes) {
        return Canonical.decodeBinary(bytes, FrameRecord.class, Canonical.Scope.RECORD);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        return "FrameRecord{body=" + bodyHash.displayAtWidth(12)
                + ", signer=" + (signer != null ? "present" : "?")
                + ", signed=" + isSigned() + "}";
    }
}
