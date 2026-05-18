package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Cryptographic algorithm vocabulary.
 *
 * <p>Each algorithm is a sememe ({@code cg.algorithm:ed25519}, etc.) carrying
 * its full metadata as {@code @Seed.Property} bindings on the seed manifest:
 * cryptographic purpose, COSE id, varsig codec, multikey codec, JCA names,
 * key-family, raw byte sizes, signature byte sizes, etc.
 *
 * <p>For Java callers, each algorithm class also exposes {@code public static
 * final} constants for the same metadata — single source of truth, both
 * annotation-driven (seeds the graph) and Java-accessible (zero-overhead
 * field reads on hot paths).
 *
 * <p>The graph-level identity ({@code @cg.algorithm:ed25519} sememe IID) is
 * how the vocabulary references the algorithm.  The COSE id ({@code -8}) is
 * the cryptographic interop standard used on the wire.  Both travel together;
 * the wire encoding stays COSE-compliant.
 *
 * <p>Adding a new algorithm is adding a new sememe (this file or another) plus
 * a code item that implements it.  The lookup paths (by COSE id, by varsig
 * codec, by multikey codec) find any algorithm sememe registered in the graph;
 * the Stage materializes the implementation when first needed.
 */
public final class AlgorithmVocabulary {

    private AlgorithmVocabulary() {}

    // ==================================================================================
    // Algorithm root archetype
    // ==================================================================================

    /** The root archetype for cryptographic algorithm sememes. */
    @Seed.Item(key = Algorithm.KEY)
    public static final class Algorithm {
        public static final String KEY = "cg.archetype:algorithm";
        private Algorithm() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of cryptographic algorithm sememes — signing, "
                        + "key-agreement, and AEAD primitives";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "algorithm";
    }

    // ==================================================================================
    // Metadata role sememes — used as binding roles on algorithm seed manifests
    // ==================================================================================

    /** Cryptographic purpose: points at {@code @signing}, {@code @encryption}, or {@code @key-agreement}. */
    @Seed.Item(key = Purpose.KEY)
    public static final class Purpose {
        public static final String KEY = "cg.algorithm:purpose";
        private Purpose() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic purpose of an algorithm (signing, encryption, key-agreement)";
    }

    /** COSE algorithm identifier — IANA-registered integer used on the wire. */
    @Seed.Item(key = CoseId.KEY)
    public static final class CoseId {
        public static final String KEY = "cg.algorithm:cose-id";
        private CoseId() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the COSE (RFC 8152) algorithm identifier — an integer the algorithm "
                        + "is known by in wire-level signature and key envelopes";
    }

    /** Multicodec varsig code — identifies the signature shape in varsig-encoded signatures. */
    @Seed.Item(key = VarsigCode.KEY)
    public static final class VarsigCode {
        public static final String KEY = "cg.algorithm:varsig-code";
        private VarsigCode() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the multicodec varsig identifier — distinguishes signature shape on the wire";
    }

    /** Multicodec multikey code — identifies the key shape in multikey-encoded public keys. */
    @Seed.Item(key = MultikeyCode.KEY)
    public static final class MultikeyCode {
        public static final String KEY = "cg.algorithm:multikey-code";
        private MultikeyCode() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the multicodec multikey identifier — distinguishes public-key shape on the wire";
    }

    /** JCA KeyFactory name — used to construct PublicKey/PrivateKey from raw bytes. */
    @Seed.Item(key = KeyFactory.KEY)
    public static final class KeyFactory {
        public static final String KEY = "cg.algorithm:key-factory";
        private KeyFactory() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the JCA KeyFactory algorithm name (e.g., \"Ed25519\", \"EC\", \"RSA\")";
    }

    /** JCA KeyPairGenerator name — sometimes differs from KeyFactory (e.g., X25519 vs XDH). */
    @Seed.Item(key = KeyGenerator.KEY)
    public static final class KeyGenerator {
        public static final String KEY = "cg.algorithm:key-generator";
        private KeyGenerator() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the JCA KeyPairGenerator algorithm name (defaults to key-factory when absent)";
    }

    /** JCA Signature transformation name — for signing algorithms. */
    @Seed.Item(key = SignatureName.KEY)
    public static final class SignatureName {
        public static final String KEY = "cg.algorithm:signature-name";
        private SignatureName() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the JCA Signature algorithm transformation (e.g., \"Ed25519\", \"SHA256withECDSA\")";
    }

    /** JCA MessageDigest name — for hash algorithms. */
    @Seed.Item(key = DigestName.KEY)
    public static final class DigestName {
        public static final String KEY = "cg.algorithm:digest-name";
        private DigestName() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the JCA MessageDigest algorithm name (e.g., \"SHA-256\", \"SHA-384\")";
    }

    /**
     * ASN.1 OID for the algorithm — used in X.509 certs, PKCS#10 CSRs, JWS/JWE,
     * and other ASN.1-encoded crypto formats.  Foreign-format interop only;
     * CG-native traffic uses the algorithm sememe IID directly.
     */
    @Seed.Item(key = Asn1Oid.KEY)
    public static final class Asn1Oid {
        public static final String KEY = "cg.algorithm:asn1-oid";
        private Asn1Oid() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the ASN.1 OID for the algorithm — used in X.509, PKCS#10, JOSE, and "
                        + "other ASN.1-encoded crypto formats";
    }

    /** Cryptographic key family — points at {@code @okp}, {@code @ec}, or {@code @rsa}. */
    @Seed.Item(key = KeyFamily.KEY)
    public static final class KeyFamily {
        public static final String KEY = "cg.algorithm:key-family";
        private KeyFamily() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic key family this algorithm belongs to (OKP, EC, RSA)";
    }

    /** Curve name — for EC-family algorithms (e.g., "secp256r1", "secp256k1"). */
    @Seed.Item(key = CurveName.KEY)
    public static final class CurveName {
        public static final String KEY = "cg.algorithm:curve-name";
        private CurveName() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the named curve for EC-family algorithms (e.g., secp256r1, secp256k1)";
    }

    /** Key size in bits — for parameterized algorithms (RSA, EC); 0 means algorithm default. */
    @Seed.Item(key = KeyBits.KEY)
    public static final class KeyBits {
        public static final String KEY = "cg.algorithm:key-bits";
        private KeyBits() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the key size in bits for parameterized algorithms; 0 means use algorithm default";
    }

    /** Raw public-key byte count — 0 for variable-length keys (RSA). */
    @Seed.Item(key = RawKeyBytes.KEY)
    public static final class RawKeyBytes {
        public static final String KEY = "cg.algorithm:raw-key-bytes";
        private RawKeyBytes() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the expected raw-bytes length of this algorithm's public key (0 = variable-length)";
    }

    /** Raw signature byte count — 0 for variable-length signatures (RSA). */
    @Seed.Item(key = SigBytes.KEY)
    public static final class SigBytes {
        public static final String KEY = "cg.algorithm:sig-bytes";
        private SigBytes() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the expected raw-bytes length of this algorithm's signature (0 = variable-length)";
    }

    /** JCA KeyAgreement name — for key-agreement algorithms (e.g., "XDH"). */
    @Seed.Item(key = AgreementName.KEY)
    public static final class AgreementName {
        public static final String KEY = "cg.algorithm:agreement-name";
        private AgreementName() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the JCA KeyAgreement algorithm name (e.g., \"XDH\" for X25519 ECDH)";
    }

    /** KDF or key-wrap name — for key-management algorithms (e.g., "HKDF-SHA256", "OAEP-SHA256"). */
    @Seed.Item(key = KdfOrWrap.KEY)
    public static final class KdfOrWrap {
        public static final String KEY = "cg.algorithm:kdf-or-wrap";
        private KdfOrWrap() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the KDF or key-wrap scheme used by a key-management algorithm";
    }

    /** JCA Cipher transformation — for AEAD algorithms (e.g., "AES/GCM/NoPadding"). */
    @Seed.Item(key = Transformation.KEY)
    public static final class Transformation {
        public static final String KEY = "cg.algorithm:transformation";
        private Transformation() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the JCA Cipher transformation string for AEAD algorithms";
    }

    /** Symmetric key byte count — for AEAD algorithms. */
    @Seed.Item(key = KeyBytes.KEY)
    public static final class KeyBytes {
        public static final String KEY = "cg.algorithm:key-bytes";
        private KeyBytes() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the AEAD symmetric-key length in bytes";
    }

    /** Nonce byte count — for AEAD algorithms. */
    @Seed.Item(key = NonceBytes.KEY)
    public static final class NonceBytes {
        public static final String KEY = "cg.algorithm:nonce-bytes";
        private NonceBytes() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the AEAD nonce length in bytes";
    }

    /** Authentication tag size in bits — for AEAD algorithms. */
    @Seed.Item(key = TagBits.KEY)
    public static final class TagBits {
        public static final String KEY = "cg.algorithm:tag-bits";
        private TagBits() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the AEAD authentication-tag length in bits";
    }

    // ==================================================================================
    // Key-family value sememes
    // ==================================================================================

    /** Octet-Key-Pair family — Ed25519, X25519, etc.  Curve-specific OKP keys. */
    @Seed.Item(key = Okp.KEY)
    public static final class Okp {
        public static final String KEY = "cg.algorithm.family:okp";
        private Okp() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the Octet-Key-Pair (OKP) family — curve-specific keys like Ed25519, X25519";
    }

    /** Elliptic-Curve family — secp256r1, secp256k1, etc. */
    @Seed.Item(key = Ec.KEY)
    public static final class Ec {
        public static final String KEY = "cg.algorithm.family:ec";
        private Ec() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the Elliptic-Curve (EC) family — named-curve keys like secp256r1, secp256k1";
    }

    /** RSA family — variable-modulus keys. */
    @Seed.Item(key = Rsa.KEY)
    public static final class Rsa {
        public static final String KEY = "cg.algorithm.family:rsa";
        private Rsa() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the RSA family — variable-modulus keys";
    }

    // ==================================================================================
    // Signing algorithms
    // ==================================================================================

    /** Ed25519 — EdDSA signature scheme over the edwards25519 curve. */
    @Seed.Item(key = Ed25519.KEY, head = Algorithm.KEY)
    public static final class Ed25519 {
        public static final String KEY = "cg.algorithm:ed25519";
        private Ed25519() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -8;

        @Seed.Property(role = VarsigCode.KEY)
        public static final long VARSIG_CODE = 0xed;

        @Seed.Property(role = MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xed;

        @Seed.Property(role = KeyFactory.KEY)
        public static final String KEY_FACTORY = "Ed25519";

        @Seed.Property(role = SignatureName.KEY)
        public static final String SIGNATURE_NAME = "Ed25519";

        @Seed.Property(role = KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(Okp.KEY);

        @Seed.Property(role = RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 32;

        @Seed.Property(role = SigBytes.KEY)
        public static final long SIG_BYTES = 64;

        @Seed.Property(role = Asn1Oid.KEY)
        public static final String ASN1_OID = "1.3.101.112";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Ed25519 — EdDSA signature scheme over the edwards25519 curve";
    }

    /** ES256 — ECDSA with P-256 (secp256r1) and SHA-256. */
    @Seed.Item(key = Es256.KEY, head = Algorithm.KEY)
    public static final class Es256 {
        public static final String KEY = "cg.algorithm:es256";
        private Es256() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -7;

        @Seed.Property(role = VarsigCode.KEY)
        public static final long VARSIG_CODE = 0x1200;

        @Seed.Property(role = MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0x1200;

        @Seed.Property(role = KeyFactory.KEY)
        public static final String KEY_FACTORY = "EC";

        @Seed.Property(role = SignatureName.KEY)
        public static final String SIGNATURE_NAME = "SHA256withECDSA";

        @Seed.Property(role = KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(Ec.KEY);

        @Seed.Property(role = CurveName.KEY)
        public static final String CURVE_NAME = "secp256r1";

        @Seed.Property(role = KeyBits.KEY)
        public static final long KEY_BITS = 256;

        @Seed.Property(role = RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 33;

        @Seed.Property(role = SigBytes.KEY)
        public static final long SIG_BYTES = 64;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "ES256 — ECDSA with the P-256 (secp256r1) curve and SHA-256";
    }

    /** ES256K — ECDSA with secp256k1 (Bitcoin curve) and SHA-256. */
    @Seed.Item(key = Es256k.KEY, head = Algorithm.KEY)
    public static final class Es256k {
        public static final String KEY = "cg.algorithm:es256k";
        private Es256k() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -47;

        @Seed.Property(role = VarsigCode.KEY)
        public static final long VARSIG_CODE = 0xe7;

        @Seed.Property(role = MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xe7;

        @Seed.Property(role = KeyFactory.KEY)
        public static final String KEY_FACTORY = "EC";

        @Seed.Property(role = SignatureName.KEY)
        public static final String SIGNATURE_NAME = "SHA256withECDSA";

        @Seed.Property(role = KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(Ec.KEY);

        @Seed.Property(role = CurveName.KEY)
        public static final String CURVE_NAME = "secp256k1";

        @Seed.Property(role = KeyBits.KEY)
        public static final long KEY_BITS = 256;

        @Seed.Property(role = RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 33;

        @Seed.Property(role = SigBytes.KEY)
        public static final long SIG_BYTES = 64;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "ES256K — ECDSA with the secp256k1 curve and SHA-256 (Bitcoin curve)";
    }

    /** PS256 — RSASSA-PSS with SHA-256 and MGF1, 4096-bit default. */
    @Seed.Item(key = Ps256.KEY, head = Algorithm.KEY)
    public static final class Ps256 {
        public static final String KEY = "cg.algorithm:ps256";
        private Ps256() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Signing.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -37;

        @Seed.Property(role = VarsigCode.KEY)
        public static final long VARSIG_CODE = 0x1205;

        @Seed.Property(role = MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0x1205;

        @Seed.Property(role = KeyFactory.KEY)
        public static final String KEY_FACTORY = "RSA";

        @Seed.Property(role = SignatureName.KEY)
        public static final String SIGNATURE_NAME = "RSASSA-PSS";

        @Seed.Property(role = KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(Rsa.KEY);

        @Seed.Property(role = KeyBits.KEY)
        public static final long KEY_BITS = 4096;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "PS256 — RSASSA-PSS with SHA-256 and MGF1, 4096-bit default modulus";
    }

    // ==================================================================================
    // Key-agreement algorithms
    // ==================================================================================

    /** ECDH-ES with HKDF-SHA-256 over X25519. */
    @Seed.Item(key = EcdhEsHkdf256.KEY, head = Algorithm.KEY)
    public static final class EcdhEsHkdf256 {
        public static final String KEY = "cg.algorithm:ecdh-es-hkdf-256";
        private EcdhEsHkdf256() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -25;

        @Seed.Property(role = MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xec;

        @Seed.Property(role = KeyFactory.KEY)
        public static final String KEY_FACTORY = "XDH";

        @Seed.Property(role = KeyGenerator.KEY)
        public static final String KEY_GENERATOR = "X25519";

        @Seed.Property(role = AgreementName.KEY)
        public static final String AGREEMENT_NAME = "XDH";

        @Seed.Property(role = KdfOrWrap.KEY)
        public static final String KDF_OR_WRAP = "HKDF-SHA256";

        @Seed.Property(role = KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(Okp.KEY);

        @Seed.Property(role = RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 32;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "ECDH-ES with HKDF-SHA-256 over X25519 — key agreement for content-key derivation";
    }

    /** RSA-OAEP with SHA-256 — key transport via RSA wrapping. */
    @Seed.Item(key = RsaOaep256.KEY, head = Algorithm.KEY)
    public static final class RsaOaep256 {
        public static final String KEY = "cg.algorithm:rsa-oaep-256";
        private RsaOaep256() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -41;

        @Seed.Property(role = MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0x1205;

        @Seed.Property(role = KeyFactory.KEY)
        public static final String KEY_FACTORY = "RSA";

        @Seed.Property(role = KdfOrWrap.KEY)
        public static final String KDF_OR_WRAP = "OAEP-SHA256";

        @Seed.Property(role = KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(Rsa.KEY);

        @Seed.Property(role = KeyBits.KEY)
        public static final long KEY_BITS = 4096;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "RSA-OAEP with SHA-256 — key transport via RSA wrapping, 4096-bit default";
    }

    // ==================================================================================
    // AEAD content-cipher algorithms
    // ==================================================================================

    /** AES-128-GCM — AES with 128-bit key in GCM mode. */
    @Seed.Item(key = AesGcm128.KEY, head = Algorithm.KEY)
    public static final class AesGcm128 {
        public static final String KEY = "cg.algorithm:aes-gcm-128";
        private AesGcm128() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Encryption.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = 1;

        @Seed.Property(role = Transformation.KEY)
        public static final String TRANSFORMATION = "AES/GCM/NoPadding";

        @Seed.Property(role = KeyBytes.KEY)
        public static final long KEY_BYTES = 16;

        @Seed.Property(role = NonceBytes.KEY)
        public static final long NONCE_BYTES = 12;

        @Seed.Property(role = TagBits.KEY)
        public static final long TAG_BITS = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "AES-128-GCM — AES with 128-bit key in Galois/Counter Mode";
    }

    /** AES-256-GCM — AES with 256-bit key in GCM mode. */
    @Seed.Item(key = AesGcm256.KEY, head = Algorithm.KEY)
    public static final class AesGcm256 {
        public static final String KEY = "cg.algorithm:aes-gcm-256";
        private AesGcm256() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Encryption.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = 3;

        @Seed.Property(role = Transformation.KEY)
        public static final String TRANSFORMATION = "AES/GCM/NoPadding";

        @Seed.Property(role = KeyBytes.KEY)
        public static final long KEY_BYTES = 32;

        @Seed.Property(role = NonceBytes.KEY)
        public static final long NONCE_BYTES = 12;

        @Seed.Property(role = TagBits.KEY)
        public static final long TAG_BITS = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "AES-256-GCM — AES with 256-bit key in Galois/Counter Mode";
    }

    /** ChaCha20-Poly1305 — ChaCha20 stream cipher with Poly1305 MAC. */
    @Seed.Item(key = ChaCha20Poly1305.KEY, head = Algorithm.KEY)
    public static final class ChaCha20Poly1305 {
        public static final String KEY = "cg.algorithm:chacha20-poly1305";
        private ChaCha20Poly1305() {}

        @Seed.Property(role = Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.Encryption.KEY);

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = 24;

        @Seed.Property(role = Transformation.KEY)
        public static final String TRANSFORMATION = "ChaCha20-Poly1305";

        @Seed.Property(role = KeyBytes.KEY)
        public static final long KEY_BYTES = 32;

        @Seed.Property(role = NonceBytes.KEY)
        public static final long NONCE_BYTES = 12;

        @Seed.Property(role = TagBits.KEY)
        public static final long TAG_BITS = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "ChaCha20-Poly1305 — ChaCha20 stream cipher with Poly1305 authentication";
    }

    // ==================================================================================
    // Hash algorithms — cryptographic digests.  Keyless primitives; no purpose
    // binding (the Purpose role describes key-bearing algorithms only).  Used
    // as the digest component of cert signature suites ("ECDSA-SHA256",
    // "RSA-PKCS1-SHA384") and anywhere a content hash needs naming.
    // ==================================================================================

    /** SHA-256 — 256-bit Secure Hash Algorithm 2 (NIST FIPS 180-4). */
    //TODO: this is duplicated and must be merged with digest.Sha254, and others
    @Seed.Item(key = Sha256.KEY, head = Algorithm.KEY)
    public static final class Sha256 {
        public static final String KEY = "cg.algorithm:sha-256";
        private Sha256() {}

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -16;

        @Seed.Property(role = DigestName.KEY)
        public static final String DIGEST_NAME = "SHA-256";

        @Seed.Property(role = Asn1Oid.KEY)
        public static final String ASN1_OID = "2.16.840.1.101.3.4.2.1";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "SHA-256 — 256-bit Secure Hash Algorithm 2 (NIST FIPS 180-4)";
    }

    /** SHA-384 — 384-bit Secure Hash Algorithm 2 (NIST FIPS 180-4). */
    @Seed.Item(key = Sha384.KEY, head = Algorithm.KEY)
    public static final class Sha384 {
        public static final String KEY = "cg.algorithm:sha-384";
        private Sha384() {}

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -43;

        @Seed.Property(role = DigestName.KEY)
        public static final String DIGEST_NAME = "SHA-384";

        @Seed.Property(role = Asn1Oid.KEY)
        public static final String ASN1_OID = "2.16.840.1.101.3.4.2.2";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "SHA-384 — 384-bit Secure Hash Algorithm 2 (NIST FIPS 180-4)";
    }

    /** SHA-512 — 512-bit Secure Hash Algorithm 2 (NIST FIPS 180-4). */
    @Seed.Item(key = Sha512.KEY, head = Algorithm.KEY)
    public static final class Sha512 {
        public static final String KEY = "cg.algorithm:sha-512";
        private Sha512() {}

        @Seed.Property(role = CoseId.KEY)
        public static final long COSE_ID = -44;

        @Seed.Property(role = DigestName.KEY)
        public static final String DIGEST_NAME = "SHA-512";

        @Seed.Property(role = Asn1Oid.KEY)
        public static final String ASN1_OID = "2.16.840.1.101.3.4.2.3";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "SHA-512 — 512-bit Secure Hash Algorithm 2 (NIST FIPS 180-4)";
    }

    // ==================================================================================
    // Ciphersuites — whole-suite sememes bundling a KEM + AEAD + KDF.
    // Whole-suite (not piece-by-piece) to minimize misconfiguration surface.
    // Used as a single {@code INSTRUMENT → @suite-iid} binding on ENCRYPT bodies.
    // ==================================================================================

    /**
     * X25519 ECDH key agreement + HKDF-SHA256 key derivation + AES-256-GCM AEAD.
     *
     * <p>The default ciphersuite for ENCRYPT bodies (see
     * {@link EncryptionVocabulary.Encrypt}).  Used as
     * {@code INSTRUMENT → @ItemRef.iid(X25519_AES256GCM_HKDF.KEY)} to name this suite.
     *
     * <p>Concretely: sender generates ephemeral X25519 keypair; for each
     * recipient performs ECDH against the recipient's long-term encryption-track
     * X25519 pubkey to derive a shared secret; runs HKDF-SHA256 to derive a key
     * wrapping key (KEK); encrypts the data encryption key (DEK) with that KEK;
     * encrypts the cleartext with the DEK using AES-256-GCM (which includes its
     * own AEAD authentication tag).
     */
    @Seed.Item(key = X25519_AES256GCM_HKDF.KEY)
    public static final class X25519_AES256GCM_HKDF {
        public static final String KEY = "cg.algorithm:x25519-aes256gcm-hkdf-sha256";
        private X25519_AES256GCM_HKDF() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "X25519 ECDH key agreement + HKDF-SHA256 key derivation + AES-256-GCM AEAD "
                        + "ciphersuite for hybrid encryption with per-recipient key wrap";
    }
}
