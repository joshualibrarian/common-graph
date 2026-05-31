package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.ThematicRole;

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

    // The Algorithm archetype and concrete algorithm declarations now live
    // alongside their runtime implementations in
    // {@link dev.everydaythings.graph.identity.algorithm.Algorithm}.  This
    // file retains only the metadata-role sememes used as binding roles on
    // algorithm seed manifests, plus the cross-cutting family value sememes.

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

    /**
     * SubjectPublicKeyInfo DER prefix — the algorithm-specific byte sequence
     * that wraps a raw public key into ASN.1 SPKI form acceptable to JCA's
     * {@code X509EncodedKeySpec}.  Per-algorithm constant; lets the abstract
     * decoder be uniform: prepend SPKI prefix to raw bytes, hand to JCA.
     */
    @Seed.Item(key = SpkiPrefix.KEY)
    public static final class SpkiPrefix {
        public static final String KEY = "cg.algorithm:spki-prefix";
        private SpkiPrefix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the SubjectPublicKeyInfo DER prefix for an algorithm — prepended to a "
                        + "raw public key to produce an ASN.1 SPKI envelope JCA can decode";
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

    /**
     * Kdf — references another algorithm sememe (a {@link
     * dev.everydaythings.graph.cryptography.algorithm.Kdf Kdf} subtype) naming the
     * key-derivation function used by a key-agreement composite.  Use as a
     * sememe reference, not a string — the role's value should be an
     * {@code ItemRef} pointing at the KDF algorithm.
     */
    @Seed.Item(key = Kdf.KEY)
    public static final class Kdf {
        public static final String KEY = "cg.algorithm:kdf";
        private Kdf() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the key-derivation function used by a composite key-management algorithm "
                        + "(reference to a KDF algorithm sememe like @cg.algorithm:hkdf-sha-256)";
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

    /**
     * Multihash code — the IPFS multihash table byte (or varint) identifying a
     * hash algorithm's output on the wire.  Used by {@link
     * dev.everydaythings.graph.ref.ContentRef} and friends to self-describe
     * hash bytes.
     */
    @Seed.Item(key = MultihashCode.KEY)
    public static final class MultihashCode {
        public static final String KEY = "cg.algorithm:multihash-code";
        private MultihashCode() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the multihash table code identifying a hash algorithm in the IPFS multihash table";
    }

    /** Digest length in bytes — for hash algorithms. */
    @Seed.Item(key = DigestLength.KEY)
    public static final class DigestLength {
        public static final String KEY = "cg.algorithm:digest-length";
        private DigestLength() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the length in bytes of digests produced by a hash algorithm";
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

}
