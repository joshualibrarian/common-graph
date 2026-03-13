package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Builder;
import lombok.Singular;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A compact signature envelope for a target object (VID/RID/CID/etc).
 *
 * Design goals:
 *  - Single authoritative target binding at Signing level (targetId + targetBodyHash).
 *  - Each Sig signs (SigningPayloadHash + per-sig metadata), NOT the target BODY directly.
 *  - Optional claimed time (claimedAtEpochMillis) is "signer-asserted", not trustworthy globally.
 *  - Avoid per-sig duplication of target binding (saves bytes at scale).
 */
public final class Signing implements Canonical {

    /** Label/domain separation. Helps prevent cross-protocol signature replay. */
    public static final String LABEL = "CG-Signing";

    /** Payload version for SigningPayload and SigToBeSigned structures. */
    public static final int VERSION = 1;

    /**
     * Construct a Signing from one-or-more signatures.
     *
     * NOTE: "zero signatures" should be represented as absence (nullable Signing field),
     * not an empty Signing instance.
     */
    public static Signing of(HashID targetId, byte[] targetBodyHash, Sig... sigs) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetBodyHash, "targetBodyHash");

        if (sigs == null || sigs.length == 0) {
            throw new IllegalArgumentException("at least one signature required");
        }
        return new Signing(targetId, targetBodyHash, List.of(sigs));
    }

    /**
     * Primary constructor used by factory/builder. Enforces invariants.
     */
    public Signing(HashID targetId, byte[] targetBodyHash, List<Sig> signatures) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetBodyHash = Objects.requireNonNull(targetBodyHash, "targetBodyHash").clone();
        this.signatures = List.copyOf(Objects.requireNonNull(signatures, "signatures"));
        if (this.signatures.isEmpty()) {
            throw new IllegalArgumentException("at least one signature required");
        }

        // Validate sigs, but no per-sig target binding to check anymore.
        for (int i = 0; i < this.signatures.size(); i++) {
            Sig s = Objects.requireNonNull(this.signatures.get(i), "signatures[" + i + "]");
            s.validate();
        }
    }

    @Builder
    public Signing(Target target, @Singular List<Sig> signatures) {
        Objects.requireNonNull(target, "target");
        this.targetId = Objects.requireNonNull(target.targetId(), "target.targetId()");
        this.targetBodyHash = Objects.requireNonNull(target.targetBodyHash(), "target.targetBodyHash()").clone();
        this.signatures = List.copyOf(Objects.requireNonNull(signatures, "signatures"));
        if (this.signatures.isEmpty()) {
            throw new IllegalArgumentException("at least one signature required");
        }

        for (int i = 0; i < this.signatures.size(); i++) {
            Sig s = Objects.requireNonNull(this.signatures.get(i), "signatures[" + i + "]");
            s.validate();
        }
    }

    // --- Canonical fields (non-final for decode support) ---

    @Canon(order = 1)
    private String label = LABEL;

    @Canon(order = 2)
    private int version = VERSION;

    /** Address of what is being signed (VID/RID/CID/etc). */
    @Canon(order = 3)
    private HashID targetId;

    /** Hash of target "BODY bytes" (Hash.DEFAULT.digest(target.bodyToSign())). */
    @Canon(order = 4)
    private byte[] targetBodyHash;

    /** One or more signatures over this Signing's payload hash. */
    @Canon(order = 5)
    private List<Sig> signatures;

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private Signing() {
    }

    // --- Accessors ---

    public HashID targetId() {
        return targetId;
    }

    public byte[] targetBodyHash() {
        return targetBodyHash.clone();
    }

    public List<Sig> signatures() {
        return signatures;
    }

    /**
     * The canonical bytes of the Signing "to-be-signed" payload (excluding signatures).
     * This is what all Sig entries bind to (via its hash).
     */
    public byte[] payloadToSign() {
        return new SigningPayload(targetId, targetBodyHash).encodeBinary(Canonical.Scope.BODY);
    }

    /** Hash of {@link #payloadToSign()} used as compact binding material for each Sig. */
    public byte[] payloadHash() {
        return Hash.DEFAULT.digest(payloadToSign());
    }

    // --- Verification ---

    /**
     * Verify this Signing against a target.
     *
     * @param target what is purportedly signed
     * @param keys resolves keyId -> SPKI bytes
     */
    public boolean verify(Target target, KeyResolver keys) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(keys, "keys");

        if (!Objects.equals(this.targetId, target.targetId())) return false;

        byte[] computed = Hash.DEFAULT.digest(target.bodyToSign());
        if (!Arrays.equals(this.targetBodyHash, computed)) return false;

        return verifyAgainst(keys, payloadHash());
    }

    /**
     * Verify that this Signing correctly binds the provided (targetId, targetBodyHash),
     * without re-hashing the target BODY bytes (useful if you already have the hash).
     */
    public boolean verify(HashID targetId, byte[] targetBodyHash, KeyResolver keys) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetBodyHash, "targetBodyHash");
        Objects.requireNonNull(keys, "keys");

        if (!Objects.equals(this.targetId, targetId)) return false;
        if (!Arrays.equals(this.targetBodyHash, targetBodyHash)) return false;

        return verifyAgainst(keys, payloadHash());
    }

    private boolean verifyAgainst(KeyResolver keys, byte[] signingPayloadHash) {
        for (Sig s : signatures) {
            if (s == null) return false;

            // Resolve public key material (SPKI) from keyId.
            byte[] spki = keys.resolveSpki(s.keyId);
            if (spki == null || spki.length == 0) return false;

            // Verify signature over the Sig-specific "to-be-signed" payload.
            byte[] sigPayload = s.toBeSigned(signingPayloadHash);

            if (!s.verifyWithSpki(spki, sigPayload)) return false;
        }
        return true;
    }

    // --- Nested structures ---

    /**
     * The canonical Signing-level payload (no signatures).
     * All signatures ultimately bind to this (via its hash).
     */
    static final class SigningPayload implements Canonical {
        @Canon(order = 1) final String label = LABEL + "-Payload";
        @Canon(order = 2) final int version = VERSION;
        @Canon(order = 3) final HashID targetId;
        @Canon(order = 4) final byte[] targetBodyHash;

        SigningPayload(HashID targetId, byte[] targetBodyHash) {
            this.targetId = Objects.requireNonNull(targetId, "targetId");
            this.targetBodyHash = Objects.requireNonNull(targetBodyHash, "targetBodyHash").clone();
        }
    }

    /**
     * Key lookup interface: resolve keyId -> SPKI bytes.
     * (Later: you can resolve via KeyLog, trust policy, remote fetch, etc.)
     */
    public interface KeyResolver {
        byte[] resolveSpki(byte[] keyId);
    }

    // ==================================================================================
    // Signer Interface
    // ==================================================================================

    /**
     * Something that can sign data.
     *
     * <p>This is the unified signing interface used throughout Common Graph.
     * Implementations include:
     * <ul>
     *   <li>{@link dev.everydaythings.graph.item.user.Signer} - Item that owns a keypair</li>
     *   <li>Hardware-backed signers (TPM, Secure Enclave, YubiKey)</li>
     * </ul>
     *
     * <p>The key principle is <b>sign-in-place</b>: the private key never leaves
     * the signer. All signing happens inside the implementation.
     */
    public interface Signer {
        /**
         * The key reference (content ID of the public key).
         *
         * <p>This is typically the hash of the SPKI-encoded public key,
         * used to identify which key produced a signature.
         */
        byte[] keyRef();

        /**
         * Sign raw data.
         *
         * <p>The private key never leaves the signer - signing happens in-place.
         *
         * @param data The bytes to sign
         * @return The signature bytes
         */
        byte[] signRaw(byte[] data);
    }

    // ==================================================================================
    // Hasher Interface
    // ==================================================================================

    /**
     * Something that computes content IDs (hashes).
     *
     * <p>Used for content addressing throughout Common Graph.
     */
    public interface Hasher {
        /**
         * Compute the content ID for the given bytes.
         *
         * @param data The bytes to hash
         * @return The content ID (typically a multihash)
         */
        byte[] cid(byte[] data);
    }

    /**
     * Default hasher using SHA-256 multihash.
     */
    public static Hasher defaultHasher() {
        return data -> Hash.DEFAULT.digestToMultihash(data).toBytes();
    }

    public static final class Sig implements Canonical {

        @Builder
        public Sig(Algorithm.Sign algorithm,
                   byte[] keyId,
                   ItemID roleId,
                   byte[] aad,
                   Long claimedAtEpochMillis,
                   byte[] signature) {

            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            this.keyId = Objects.requireNonNull(keyId, "keyId").clone();
            this.roleId = roleId; // nullable
            this.aad = (aad == null ? null : aad.clone());
            this.claimedAtEpochMillis = claimedAtEpochMillis; // nullable
            this.signature = Objects.requireNonNull(signature, "signature").clone();
        }

        /**
         * No-arg constructor for Canonical decode support.
         */
        @SuppressWarnings("unused")
        private Sig() {
        }

        @Canon(order = 1)
        String label = "CG-Sig1";

        @Canon(order = 2)
        int version = 1;

        @Canon(order = 3)
        Algorithm.Sign algorithm;     // e.g. ED25519

        @Canon(order = 4)
        byte[] keyId;                 // H(SPKI) (or other key identifier)

        @Canon(order = 5)
        ItemID roleId;                // optional semantic role (sememe item id)

        @Canon(order = 6)
        byte[] aad;                   // optional additional authenticated data

        @Canon(order = 7)
        Long claimedAtEpochMillis;    // optional signer-claimed time (NOT globally trustworthy)

        // signature bytes are NOT part of the signed BODY for this object
        @Canon(order = 10, isBody = false)
        byte[] signature;

        void validate() {
            if (algorithm == null) throw new IllegalArgumentException("sig.algorithm is null");
            if (keyId == null || keyId.length == 0) throw new IllegalArgumentException("sig.keyId missing");
            if (signature == null || signature.length == 0) throw new IllegalArgumentException("sig.signature missing");
            // roleId/aad/claimedAt are optional
        }

        /**
         * Canonical bytes that are actually signed/verified for this Sig.
         * This binds Sig metadata to the Signing-level payload hash.
         */
        byte[] toBeSigned(byte[] signingPayloadHash) {
            Objects.requireNonNull(signingPayloadHash, "signingPayloadHash");
            return new SigToBeSigned(signingPayloadHash, this).encodeBinary(Canonical.Scope.BODY);
        }

        boolean verifyWithSpki(byte[] spki, byte[] payload) {
            try {
                return switch (algorithm) {
                    case ED25519 -> verifyEd25519Spki(spki, payload, signature);
                    default -> false; // fail closed
                };
            } catch (Exception e) {
                return false;
            }
        }

        static final class SigToBeSigned implements Canonical {
            @Canon(order = 1) final String label = "CG-Sig1-ToBeSigned";
            @Canon(order = 2) final int version = 1;

            @Canon(order = 3) final byte[] signingPayloadHash;

            @Canon(order = 4) final Algorithm.Sign algorithm;
            @Canon(order = 5) final byte[] keyId;
            @Canon(order = 6) final ItemID roleId;
            @Canon(order = 7) final byte[] aad;
            @Canon(order = 8) final Long claimedAtEpochMillis;

            SigToBeSigned(byte[] signingPayloadHash, Sig sig) {
                this.signingPayloadHash = Objects.requireNonNull(signingPayloadHash, "signingPayloadHash").clone();
                this.algorithm = sig.algorithm;
                this.keyId = sig.keyId.clone();
                this.roleId = sig.roleId;
                this.aad = (sig.aad == null ? null : sig.aad.clone());
                this.claimedAtEpochMillis = sig.claimedAtEpochMillis;
            }
        }

        private static boolean verifyEd25519Spki(byte[] spki, byte[] payload, byte[] sigBytes) throws Exception {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PublicKey pk = kf.generatePublic(new X509EncodedKeySpec(spki));
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pk);
            sig.update(payload);
            return sig.verify(sigBytes);
        }
    }

    /**
     * A target whose (targetId, bodyToSign) are being asserted by signatures.
     * targetBodyHash defaults to Hash.DEFAULT(bodyToSign()).
     */
    public interface Target extends Canonical {
        HashID targetId();

        default byte[] bodyToSign() {
            return encodeBinary(Canonical.Scope.BODY);
        }

        default byte[] targetBodyHash() {
            return Hash.DEFAULT.digest(bodyToSign());
        }
    }
}
