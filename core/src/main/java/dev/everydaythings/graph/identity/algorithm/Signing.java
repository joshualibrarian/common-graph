package dev.everydaythings.graph.identity.algorithm;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.AlgorithmVocabulary;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.X509EncodedKeySpec;

/**
 * Signing — the sub-archetype for signature-producing cryptographic algorithms.
 *
 * <p>Supplies {@code verify} and {@code decodePublicKey} that all concrete
 * signing leaves (Ed25519, ES256, ES256K, PS256) inherit without overrides.
 * No private keys flow through this class — signing operations live in
 * {@link dev.everydaythings.graph.identity.vault.Vault Vault}.
 *
 * <p>Concrete leaves are pure data declarations: static {@code @Seed.Property}
 * fields naming the algorithm's COSE id, varsig/multikey codes, JCA names,
 * key-family, SPKI prefix, and byte sizes.  At hydration time
 * {@link dev.everydaythings.graph.item.BodyBinder BodyBinder} copies those
 * values into the inherited instance fields on this class, and the operations
 * read them directly.
 */
@Seed.Item(key = Signing.KEY, head = Algorithm.KEY)
public abstract class Signing extends Algorithm implements PublicKeyAlgorithm {

    /** Canonical key for the signing-algorithm sub-archetype. */
    public static final String KEY = "cg.archetype:signing-algorithm";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String signingGloss =
            "an algorithm that produces signatures over messages using a private key "
                    + "and verifies them using the corresponding public key";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String signingLemma = "signing algorithm";

    // -- Instance fields populated from the leaf's manifest by BodyBinder --

    @Seed.Property(role = AlgorithmVocabulary.SignatureName.KEY)
    protected String signatureName;

    @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
    protected String keyFactoryName;

    @Seed.Property(role = AlgorithmVocabulary.SpkiPrefix.KEY)
    protected byte[] spkiPrefix;

    @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
    protected long coseId;

    @Seed.Property(role = AlgorithmVocabulary.VarsigCode.KEY)
    protected long varsigCode;

    @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
    protected long multikeyCode;

    @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
    protected long rawKeyBytes;

    @Seed.Property(role = AlgorithmVocabulary.SigBytes.KEY)
    protected long sigBytes;

    protected Signing(ItemRef iid) {
        super(iid);
    }

    protected Signing(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // -- Operations --

    /** Verify a signature against the given public key and message. */
    public boolean verify(byte[] message, byte[] signature, PublicKey publicKey) {
        try {
            Signature s = Signature.getInstance(signatureName);
            s.initVerify(publicKey);
            s.update(message);
            return s.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sign a message with the given private key.  Returns the raw signature
     * bytes (no algorithm framing — see {@link VarSig} for the self-describing
     * envelope, produced by {@link VarSig#of(Signing, byte[])} with this
     * algorithm).
     */
    public byte[] sign(byte[] message, PrivateKey privateKey) {
        try {
            Signature s = Signature.getInstance(signatureName);
            s.initSign(privateKey);
            s.update(message);
            return s.sign();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Signing failed for " + iid().encodeText(), e);
        }
    }

    /**
     * Generate a fresh JCA {@link KeyPair} for this algorithm.  Default
     * implementation uses {@link #keyFactoryName} as the
     * {@link KeyPairGenerator} algorithm name — sufficient for algorithms
     * where the factory name and generator name coincide (Ed25519).  Leaves
     * whose generator needs explicit parameters (ECDSA curves) override.
     */
    public KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(keyFactoryName).generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "Key generation unavailable for " + iid().encodeText()
                            + " (JCA name: " + keyFactoryName + ")", e);
        }
    }

    /**
     * Decode raw public-key bytes (multikey payload minus the codec prefix)
     * into a JCA {@link PublicKey} suitable for
     * {@link #verify(byte[], byte[], PublicKey)}.  Prepends the
     * algorithm-specific SPKI prefix and hands the result to JCA's
     * {@link X509EncodedKeySpec} flow.
     */
    public PublicKey decodePublicKey(byte[] rawBytes) {
        try {
            byte[] spki = new byte[spkiPrefix.length + rawBytes.length];
            System.arraycopy(spkiPrefix, 0, spki, 0, spkiPrefix.length);
            System.arraycopy(rawBytes,   0, spki, spkiPrefix.length, rawBytes.length);
            return KeyFactory.getInstance(keyFactoryName)
                             .generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to decode public key for " + iid().encodeText(), e);
        }
    }

    /**
     * Extract this algorithm's raw public-key bytes from a JCA
     * {@link PublicKey}.  The inverse of {@link #decodePublicKey(byte[])}:
     * given a JCA-shaped key, produce the raw form this algorithm uses on the
     * wire (the byte sequence that, with the algorithm's multikey codec, is a
     * complete {@link MultiKey}).
     *
     * <p>Each {@link Signing} subclass implements its own algorithm-specific
     * extraction (Ed25519's RFC-8032 encoding, ECDSA's compressed point form,
     * RSA's SubjectPublicKeyInfo blob, etc.).
     */
    public abstract byte[] publicKeyToRaw(PublicKey jcaKey);

    // -- Accessor methods for metadata (instance-field reads) --

    public String signatureName()  { return signatureName; }
    public String keyFactoryName() { return keyFactoryName; }
    public long   coseId()         { return coseId; }
    public long   varsigCode()     { return varsigCode; }
    public long   multikeyCode()   { return multikeyCode; }
    public long   rawKeyBytes()    { return rawKeyBytes; }
    public long   sigBytes()       { return sigBytes; }

    // ==================================================================================
    // Built-in lookup — librarian-less fallback for algorithms backed by a Java
    // class with a {@code builtin()} factory.  Add a branch here per new signing
    // algorithm that needs librarian-less usage (today: just Ed25519).
    // ==================================================================================

    /** Built-in algorithm instance by varsig code, or {@code null}. */
    public static Signing builtinByVarsigCode(int code) {
        if (code == (int) Ed25519.VARSIG_CODE) return Ed25519.builtin();
        return null;
    }

    /** Built-in algorithm instance by multikey code, or {@code null}. */
    public static Signing builtinByMultikeyCode(int code) {
        if (code == (int) Ed25519.MULTIKEY_CODE) return Ed25519.builtin();
        return null;
    }

    /**
     * Convert a JCA {@link PublicKey} into a {@link MultiKey} by dispatching
     * on the algorithm name to a Signing leaf that knows the raw-bytes
     * encoding.  Today: Ed25519 / EdDSA only; other key families land as
     * their leaves grow {@code publicKeyToRaw} implementations.
     *
     * @throws IllegalArgumentException if no built-in Signing leaf claims the
     *         JCA algorithm name
     */
    public static MultiKey toMultiKey(PublicKey jcaKey) {
        String alg = jcaKey.getAlgorithm();
        if ("Ed25519".equals(alg) || "EdDSA".equals(alg)) {
            Ed25519 ed = Ed25519.builtin();
            return MultiKey.of(ed, ed.publicKeyToRaw(jcaKey));
        }
        throw new IllegalArgumentException(
                "No Signing leaf handles JCA algorithm: " + alg);
    }

    // ==================================================================================
    // Concrete signing algorithms — pure data declarations.  No method overrides.
    // ==================================================================================

    /** Ed25519 — EdDSA over the edwards25519 curve. */
    @Seed.Item(key = Ed25519.KEY, head = Signing.KEY)
    @Seed.Embodies(key = Ed25519.KEY)
    public static final class Ed25519 extends Signing {

        public static final String KEY = "cg.algorithm:ed25519";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -8;

        @Seed.Property(role = AlgorithmVocabulary.VarsigCode.KEY)
        public static final long VARSIG_CODE = 0xed;

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xed;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "Ed25519";

        @Seed.Property(role = AlgorithmVocabulary.SignatureName.KEY)
        public static final String SIGNATURE_NAME = "Ed25519";

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Okp.KEY);

        @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 32;

        @Seed.Property(role = AlgorithmVocabulary.SigBytes.KEY)
        public static final long SIG_BYTES = 64;

        @Seed.Property(role = AlgorithmVocabulary.Asn1Oid.KEY)
        public static final String ASN1_OID = "1.3.101.112";

        /**
         * SubjectPublicKeyInfo DER prefix for Ed25519.  Twelve bytes:
         * SEQUENCE(42) + SEQUENCE(5)(OID 1.3.101.112) + BIT STRING(33)(0x00).
         */
        @Seed.Property(role = AlgorithmVocabulary.SpkiPrefix.KEY)
        public static final byte[] SPKI_PREFIX = {
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        };

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String ed25519Gloss =
                "Ed25519 — EdDSA signature scheme over the edwards25519 curve";

        public Ed25519()                         { super(ItemRef.iid(KEY)); }
        public Ed25519(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Ed25519(ItemRef iid, Librarian l) { super(iid, l); }

        /**
         * Extract the 32-byte raw form of an Ed25519 public key — little-endian
         * y-coordinate with the x-coordinate sign bit packed into bit 7 of the
         * last byte.  Matches RFC 8032 and the multikey 0xed encoding.
         */
        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            if (!(jcaKey instanceof EdECPublicKey ed)) {
                throw new IllegalArgumentException(
                        "Expected EdECPublicKey for Ed25519, got " + jcaKey.getClass().getName());
            }
            EdECPoint point = ed.getPoint();
            BigInteger y = point.getY();
            byte[] yBE = y.toByteArray();
            byte[] raw = new byte[32];
            int copyLen = Math.min(yBE.length, 32);
            for (int i = 0; i < copyLen; i++) {
                raw[i] = yBE[yBE.length - 1 - i];
            }
            if (point.isXOdd()) {
                raw[31] |= (byte) 0x80;
            }
            return raw;
        }

        /**
         * Built-in instance for librarian-less use (tests, bare
         * {@code Signer.inMemory()}).  Has its instance fields populated
         * directly from the static {@code @Seed.Property} constants —
         * BodyBinder isn't available without a librarian, so this is the
         * librarian-less initialization path.
         */
        public static Ed25519 builtin() {
            Ed25519 a = new Ed25519();
            a.signatureName  = SIGNATURE_NAME;
            a.keyFactoryName = KEY_FACTORY;
            a.spkiPrefix     = SPKI_PREFIX.clone();
            a.coseId         = COSE_ID;
            a.varsigCode     = VARSIG_CODE;
            a.multikeyCode   = MULTIKEY_CODE;
            a.rawKeyBytes    = RAW_KEY_BYTES;
            a.sigBytes       = SIG_BYTES;
            return a;
        }
    }

    /** ES256 — ECDSA with P-256 (secp256r1) and SHA-256. */
    @Seed.Item(key = Es256.KEY, head = Signing.KEY)
    public static final class Es256 extends Signing {

        public static final String KEY = "cg.algorithm:es256";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -7;

        @Seed.Property(role = AlgorithmVocabulary.VarsigCode.KEY)
        public static final long VARSIG_CODE = 0x1200;

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0x1200;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "EC";

        @Seed.Property(role = AlgorithmVocabulary.SignatureName.KEY)
        public static final String SIGNATURE_NAME = "SHA256withECDSA";

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Ec.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CurveName.KEY)
        public static final String CURVE_NAME = "secp256r1";

        @Seed.Property(role = AlgorithmVocabulary.KeyBits.KEY)
        public static final long KEY_BITS = 256;

        @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 33;

        @Seed.Property(role = AlgorithmVocabulary.SigBytes.KEY)
        public static final long SIG_BYTES = 64;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String es256Gloss =
                "ES256 — ECDSA with the P-256 (secp256r1) curve and SHA-256";

        public Es256()                         { super(ItemRef.iid(KEY)); }
        public Es256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Es256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            throw new UnsupportedOperationException(
                    "ES256 publicKeyToRaw not yet implemented (compressed-point encoding)");
        }
    }

    /** ES256K — ECDSA with secp256k1 (Bitcoin curve) and SHA-256. */
    @Seed.Item(key = Es256k.KEY, head = Signing.KEY)
    public static final class Es256k extends Signing {

        public static final String KEY = "cg.algorithm:es256k";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -47;

        @Seed.Property(role = AlgorithmVocabulary.VarsigCode.KEY)
        public static final long VARSIG_CODE = 0xe7;

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xe7;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "EC";

        @Seed.Property(role = AlgorithmVocabulary.SignatureName.KEY)
        public static final String SIGNATURE_NAME = "SHA256withECDSA";

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Ec.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CurveName.KEY)
        public static final String CURVE_NAME = "secp256k1";

        @Seed.Property(role = AlgorithmVocabulary.KeyBits.KEY)
        public static final long KEY_BITS = 256;

        @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 33;

        @Seed.Property(role = AlgorithmVocabulary.SigBytes.KEY)
        public static final long SIG_BYTES = 64;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String es256kGloss =
                "ES256K — ECDSA with the secp256k1 curve and SHA-256 (Bitcoin curve)";

        public Es256k()                         { super(ItemRef.iid(KEY)); }
        public Es256k(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Es256k(ItemRef iid, Librarian l) { super(iid, l); }

        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            throw new UnsupportedOperationException(
                    "ES256K publicKeyToRaw not yet implemented (compressed-point encoding)");
        }
    }

    /** PS256 — RSASSA-PSS with SHA-256 and MGF1, 4096-bit default. */
    @Seed.Item(key = Ps256.KEY, head = Signing.KEY)
    public static final class Ps256 extends Signing {

        public static final String KEY = "cg.algorithm:ps256";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -37;

        @Seed.Property(role = AlgorithmVocabulary.VarsigCode.KEY)
        public static final long VARSIG_CODE = 0x1205;

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0x1205;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "RSA";

        @Seed.Property(role = AlgorithmVocabulary.SignatureName.KEY)
        public static final String SIGNATURE_NAME = "RSASSA-PSS";

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Rsa.KEY);

        @Seed.Property(role = AlgorithmVocabulary.KeyBits.KEY)
        public static final long KEY_BITS = 4096;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String ps256Gloss =
                "PS256 — RSASSA-PSS with SHA-256 and MGF1, 4096-bit default modulus";

        public Ps256()                         { super(ItemRef.iid(KEY)); }
        public Ps256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Ps256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            throw new UnsupportedOperationException(
                    "PS256 publicKeyToRaw not yet implemented (SubjectPublicKeyInfo encoding)");
        }
    }
}
