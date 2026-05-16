package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.id.ItemRef;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;

/**
 * JCA-backed {@link AlgorithmHandle} — wraps an algorithm's metadata with
 * Java Cryptography Architecture calls to satisfy {@code verify} and
 * {@code decodePublicKey}.
 *
 * <p>One instance per algorithm sememe.  Parameterized by the metadata read
 * from the algorithm seed manifest (or, equivalently, from the static
 * constants on {@link AlgorithmVocabulary}'s nested classes).
 *
 * <p>For now this handle implements verification generically (one JCA path
 * works for Ed25519, ECDSA, RSASSA-PSS) and public-key decoding for Ed25519
 * specifically.  Other algorithms' raw-key decoding is a TODO — extend
 * {@link #decodePublicKey} with per-key-family branches as more algorithms
 * come online.
 */
public final class JcaAlgorithmHandle implements AlgorithmHandle {

    private final ItemRef sememeIid;
    private final long coseId;
    private final long varsigCode;
    private final long multikeyCode;
    private final String signatureName;
    private final String keyFactoryName;
    private final int rawKeyBytes;

    /**
     * Construct from already-extracted metadata.  The librarian's
     * algorithm cache pulls these fields off the algorithm sememe's
     * {@code @Seed.Property} bindings at warmup time.
     *
     * @param signatureName JCA {@link Signature} algorithm transformation
     *                      (e.g., {@code "Ed25519"}, {@code "SHA256withECDSA"});
     *                      may be {@code null} for non-signing algorithms
     * @param keyFactoryName JCA {@link KeyFactory} algorithm name (e.g.,
     *                       {@code "Ed25519"}, {@code "EC"}, {@code "RSA"})
     * @param rawKeyBytes expected raw-key length, 0 for variable-length
     */
    public JcaAlgorithmHandle(ItemRef sememeIid, long coseId, long varsigCode,
                              long multikeyCode, String signatureName,
                              String keyFactoryName, int rawKeyBytes) {
        this.sememeIid = sememeIid;
        this.coseId = coseId;
        this.varsigCode = varsigCode;
        this.multikeyCode = multikeyCode;
        this.signatureName = signatureName;
        this.keyFactoryName = keyFactoryName;
        this.rawKeyBytes = rawKeyBytes;
    }

    @Override public ItemRef sememeIid()   { return sememeIid; }
    @Override public long    coseId()       { return coseId; }
    @Override public long    varsigCode()   { return varsigCode; }
    @Override public long    multikeyCode() { return multikeyCode; }

    @Override
    public boolean verify(byte[] message, byte[] signature, PublicKey publicKey) {
        if (signatureName == null) {
            return false;   // non-signing algorithm — can't verify
        }
        try {
            Signature sig = Signature.getInstance(signatureName);
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public PublicKey decodePublicKey(byte[] rawBytes) {
        // First-cut: Ed25519 only.  Other algorithms throw until per-family
        // decode logic lands.  Dispatched by sememe IID for clarity; key-family
        // would also work and is the right axis once we generalize.
        if (ItemRef.iid(AlgorithmVocabulary.Ed25519.KEY).equals(sememeIid)) {
            return decodeEd25519(rawBytes);
        }
        throw new UnsupportedOperationException(
                "Raw public-key decoding not yet implemented for " + sememeIid.encodeText());
    }

    /**
     * Decode an Ed25519 public key from its 32-byte raw form
     * (little-endian y-coordinate with the x-coordinate sign bit packed
     * into bit 7 of the last byte).  This is the same encoding used in
     * RFC 8032 and accepted by the multikey codec for {@code 0xed}.
     */
    private static PublicKey decodeEd25519(byte[] raw) {
        if (raw.length != 32) {
            throw new IllegalArgumentException(
                    "Ed25519 raw key must be 32 bytes, got " + raw.length);
        }
        boolean xOdd = (raw[31] & 0x80) != 0;
        byte[] yLE = raw.clone();
        yLE[31] &= 0x7F;
        byte[] yBE = new byte[32];
        for (int i = 0; i < 32; i++) {
            yBE[i] = yLE[31 - i];
        }
        BigInteger y = new BigInteger(1, yBE);
        try {
            EdECPoint point = new EdECPoint(xOdd, y);
            EdECPublicKeySpec spec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, point);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode Ed25519 public key", e);
        }
    }

    /**
     * Convenience factory: build a handle straight from an
     * {@link AlgorithmVocabulary} algorithm class's static constants.
     * Used at cache warmup time when the algorithm is built-in (a Java
     * class is available).  For graph-deployed algorithms we'd read the
     * metadata off the seed manifest directly instead.
     */
    public static JcaAlgorithmHandle ofEd25519() {
        return new JcaAlgorithmHandle(
                ItemRef.iid(AlgorithmVocabulary.Ed25519.KEY),
                AlgorithmVocabulary.Ed25519.COSE_ID,
                AlgorithmVocabulary.Ed25519.VARSIG_CODE,
                AlgorithmVocabulary.Ed25519.MULTIKEY_CODE,
                AlgorithmVocabulary.Ed25519.SIGNATURE_NAME,
                AlgorithmVocabulary.Ed25519.KEY_FACTORY,
                (int) AlgorithmVocabulary.Ed25519.RAW_KEY_BYTES);
    }
}
