package dev.everydaythings.graph.crypt;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.trust.Algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CG Tag 10: Encrypted envelope.
 *
 * <p>Wire format:
 * <pre>
 * Tag(10, [
 *     header,         // CBOR map: algorithm parameters + metadata
 *     recipients,     // CBOR array of recipient entries
 *     ciphertext      // CBOR byte string: AEAD output (ciphertext || auth-tag)
 * ])
 * </pre>
 *
 * <p>This is the canonical encrypted payload format for Common Graph.
 * It supports both single-recipient (ECDH-ES direct) and multi-recipient
 * (ECDH-ES + AES Key Wrap) modes.
 */
public final class EncryptedEnvelope implements Canonical {

    // === Header fields (CBOR map keys) ===
    private final Algorithm.KeyMgmt kemAlg;
    private final Algorithm.Aead aeadAlg;
    private final byte[] nonce;           // 12 bytes
    private final byte[] aad;             // additional authenticated data (nullable)
    private final byte[] plaintextCid;    // CID of the original plaintext (for content-address recovery)
    private final byte[] senderKid;       // sha256(SPKI) of sender's signing key (nullable for anonymous)
    private final byte[] senderSig;       // sender signature over header body (nullable for anonymous)

    // === Recipient entries ===
    private final List<Recipient> recipients;

    // === Ciphertext ===
    private final byte[] ciphertext;      // AEAD output (ciphertext || auth-tag)

    public EncryptedEnvelope(Algorithm.KeyMgmt kemAlg, Algorithm.Aead aeadAlg,
                             byte[] nonce, byte[] aad, byte[] plaintextCid,
                             byte[] senderKid, byte[] senderSig,
                             List<Recipient> recipients, byte[] ciphertext) {
        this.kemAlg = kemAlg;
        this.aeadAlg = aeadAlg;
        this.nonce = nonce;
        this.aad = aad;
        this.plaintextCid = plaintextCid;
        this.senderKid = senderKid;
        this.senderSig = senderSig;
        this.recipients = List.copyOf(recipients);
        this.ciphertext = ciphertext;
    }

    // === Accessors ===

    public Algorithm.KeyMgmt kemAlg() { return kemAlg; }
    public Algorithm.Aead aeadAlg() { return aeadAlg; }
    public byte[] nonce() { return nonce; }
    public byte[] aad() { return aad; }
    public byte[] plaintextCid() { return plaintextCid; }
    public byte[] senderKid() { return senderKid; }
    public byte[] senderSig() { return senderSig; }
    public List<Recipient> recipients() { return recipients; }
    public byte[] ciphertext() { return ciphertext; }

    /** The CID of this envelope (hash of the encoded bytes). */
    public ContentID contentId() {
        return new ContentID(Hash.DEFAULT.digest(encodeBinary(Scope.RECORD)));
    }

    /** The plaintext CID as a ContentID. */
    public ContentID plaintextContentId() {
        return plaintextCid != null ? new ContentID(plaintextCid) : null;
    }

    // === Recipient entry ===

    /**
     * Per-recipient key material for recovering the CEK.
     *
     * @param kid         Recipient's encryption key ID (sha256(SPKI))
     * @param epk         Sender's ephemeral X25519 public key (SPKI DER bytes)
     * @param wrappedCek  Key-wrapped CEK (null for ECDH-ES direct mode)
     */
    public record Recipient(byte[] kid, byte[] epk, byte[] wrappedCek) {

        CBORObject toCbor() {
            CBORObject arr = CBORObject.NewArray();
            arr.Add(CBORObject.FromByteArray(kid));
            arr.Add(CBORObject.FromByteArray(epk));
            arr.Add(wrappedCek != null ? CBORObject.FromByteArray(wrappedCek) : CBORObject.Null);
            return arr;
        }

        static Recipient fromCbor(CBORObject arr) {
            byte[] kid = arr.get(0).GetByteString();
            byte[] epk = arr.get(1).GetByteString();
            byte[] wrappedCek = arr.get(2).isNull() ? null : arr.get(2).GetByteString();
            return new Recipient(kid, epk, wrappedCek);
        }
    }

    // === CBOR encoding: Tag(10, [header, recipients, ciphertext]) ===

    @Override
    public CBORObject toCborTree(Scope scope) {
        // Header map
        CBORObject header = CBORObject.NewMap();
        header.set(CBORObject.FromInt32(1), CBORObject.FromInt32(kemAlg.coseId()));
        header.set(CBORObject.FromInt32(2), CBORObject.FromInt32(aeadAlg.coseId()));
        header.set(CBORObject.FromInt32(3), CBORObject.FromByteArray(nonce));
        if (aad != null) {
            header.set(CBORObject.FromInt32(4), CBORObject.FromByteArray(aad));
        }
        if (plaintextCid != null) {
            header.set(CBORObject.FromInt32(5), CBORObject.FromByteArray(plaintextCid));
        }
        if (senderKid != null) {
            header.set(CBORObject.FromInt32(6), CBORObject.FromByteArray(senderKid));
        }
        if (senderSig != null) {
            header.set(CBORObject.FromInt32(7), CBORObject.FromByteArray(senderSig));
        }

        // Recipients array
        CBORObject recs = CBORObject.NewArray();
        for (Recipient r : recipients) {
            recs.Add(r.toCbor());
        }

        // Outer array: [header, recipients, ciphertext]
        CBORObject envelope = CBORObject.NewArray();
        envelope.Add(header);
        envelope.Add(recs);
        envelope.Add(CBORObject.FromByteArray(ciphertext));

        // Wrap in Tag 10
        return CBORObject.FromCBORObjectAndTag(envelope, CgTag.ENCRYPTED);
    }

    /**
     * Decode a Tag 10 envelope from CBOR.
     */
    public static EncryptedEnvelope fromCborTree(CBORObject tagged) {
        if (!tagged.HasMostOuterTag(CgTag.ENCRYPTED)) {
            throw new IllegalArgumentException("Expected Tag 10, got: " + tagged.getMostOuterTag());
        }
        CBORObject envelope = tagged.UntagOne();

        CBORObject header = envelope.get(0);
        CBORObject recs = envelope.get(1);
        byte[] ciphertext = envelope.get(2).GetByteString();

        // Parse header
        Algorithm.KeyMgmt kemAlg = (Algorithm.KeyMgmt) Algorithm.fromCose(header.get(CBORObject.FromInt32(1)).AsInt32());
        Algorithm.Aead aeadAlg = (Algorithm.Aead) Algorithm.fromCose(header.get(CBORObject.FromInt32(2)).AsInt32());
        byte[] nonce = header.get(CBORObject.FromInt32(3)).GetByteString();
        byte[] aad = header.ContainsKey(CBORObject.FromInt32(4)) ? header.get(CBORObject.FromInt32(4)).GetByteString() : null;
        byte[] plaintextCid = header.ContainsKey(CBORObject.FromInt32(5)) ? header.get(CBORObject.FromInt32(5)).GetByteString() : null;
        byte[] senderKid = header.ContainsKey(CBORObject.FromInt32(6)) ? header.get(CBORObject.FromInt32(6)).GetByteString() : null;
        byte[] senderSig = header.ContainsKey(CBORObject.FromInt32(7)) ? header.get(CBORObject.FromInt32(7)).GetByteString() : null;

        // Parse recipients
        List<Recipient> recipients = new ArrayList<>(recs.size());
        for (int i = 0; i < recs.size(); i++) {
            recipients.add(Recipient.fromCbor(recs.get(i)));
        }

        return new EncryptedEnvelope(kemAlg, aeadAlg, nonce, aad, plaintextCid,
                senderKid, senderSig, recipients, ciphertext);
    }

    /**
     * Build the header bytes for sender signing.
     * Covers header fields 1-6 (everything except the signature itself).
     */
    public byte[] headerBodyForSigning() {
        CBORObject header = CBORObject.NewMap();
        header.set(CBORObject.FromInt32(1), CBORObject.FromInt32(kemAlg.coseId()));
        header.set(CBORObject.FromInt32(2), CBORObject.FromInt32(aeadAlg.coseId()));
        header.set(CBORObject.FromInt32(3), CBORObject.FromByteArray(nonce));
        if (aad != null) header.set(CBORObject.FromInt32(4), CBORObject.FromByteArray(aad));
        if (plaintextCid != null) header.set(CBORObject.FromInt32(5), CBORObject.FromByteArray(plaintextCid));
        if (senderKid != null) header.set(CBORObject.FromInt32(6), CBORObject.FromByteArray(senderKid));
        return header.EncodeToBytes();
    }

    /**
     * Create an unsigned copy with the given signature attached.
     */
    public EncryptedEnvelope withSenderSig(byte[] sig) {
        return new EncryptedEnvelope(kemAlg, aeadAlg, nonce, aad, plaintextCid,
                senderKid, sig, recipients, ciphertext);
    }
}
