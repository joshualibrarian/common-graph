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
import java.security.interfaces.XECPublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * KeyAgreement — the sub-archetype for key-agreement (Diffie-Hellman-shaped)
 * cryptographic algorithms.
 *
 * <p>A key-agreement algorithm pairs a long-term private key (held by a
 * {@link dev.everydaythings.graph.identity.vault.Vault Vault}) with a peer's
 * public key to derive a shared secret.  Used as the KEM component of hybrid
 * encryption ciphersuites — the AEAD step (encryption proper) consumes the
 * derived symmetric key.
 *
 * <p>Two flavors covered here:
 * <ul>
 *   <li><b>X25519</b> — the key-agreement primitive itself (Curve25519 in
 *       Montgomery form).  Stands alone so multiple ciphersuites can reference it.</li>
 *   <li><b>EcdhEsHkdf256</b> — ECDH-ES + HKDF-SHA256 over X25519 (JOSE/COSE name).
 *       References X25519 as its underlying primitive; HKDF-SHA256 is the
 *       derivation step.</li>
 *   <li><b>RsaOaep256</b> — RSA-OAEP with SHA-256 (not DH-shaped, but the same
 *       conceptual slot: pair a private key with peer info to derive a session key).</li>
 * </ul>
 *
 * <p>Phase-A leaves are data-only.  The {@code agree(vault, peerPublicKey)}
 * runtime operation wires up alongside the X25519-in-Vault work in Phase C of
 * the identity-layer survey.
 */
@Seed.Item(key = KeyAgreement.KEY, head = Algorithm.KEY)
public abstract class KeyAgreement extends Algorithm implements PublicKeyAlgorithm {

    /** Canonical key for the key-agreement-algorithm sub-archetype. */
    public static final String KEY = "cg.archetype:key-agreement-algorithm";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String agreementGloss =
            "an algorithm that pairs a long-term private key with a peer's public key "
                    + "to derive a shared secret usable as the symmetric key for further encryption";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String agreementLemma = "key-agreement algorithm";

    // -- Instance fields populated from the leaf's manifest by BodyBinder --

    @Seed.Property(role = AlgorithmVocabulary.AgreementName.KEY)
    protected String agreementName;

    @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
    protected String keyFactoryName;

    @Seed.Property(role = AlgorithmVocabulary.SpkiPrefix.KEY)
    protected byte[] spkiPrefix;

    @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
    protected long multikeyCode;

    @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
    protected long coseId;

    @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
    protected long rawKeyBytes;

    protected KeyAgreement(ItemRef iid) {
        super(iid);
    }

    protected KeyAgreement(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // -- Operations --

    /**
     * Decode raw public-key bytes (multikey payload minus the codec prefix)
     * into a JCA {@link PublicKey} suitable for
     * {@link #agree(PrivateKey, PublicKey)}.  Prepends the algorithm-specific
     * SPKI prefix and hands the result to JCA's {@link X509EncodedKeySpec} flow.
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
     * Extract this algorithm's raw public-key bytes from a JCA {@link PublicKey}.
     * The inverse of {@link #decodePublicKey(byte[])}.  Each concrete leaf
     * implements its own algorithm-specific extraction.
     */
    public abstract byte[] publicKeyToRaw(PublicKey jcaKey);

    /**
     * Generate a fresh JCA {@link KeyPair} for this algorithm.  Default uses
     * {@link #keyFactoryName} as the {@link KeyPairGenerator} algorithm name;
     * leaves whose generator name differs (X25519 uses "X25519" not "XDH")
     * override.
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
     * Derive a shared secret by pairing our private key with a peer's public
     * key.  Returns the raw secret bytes; callers typically feed this into a
     * KDF (HKDF-SHA-256, etc.) to produce a content-encryption key.
     */
    public byte[] agree(PrivateKey privateKey, PublicKey peerPublicKey) {
        try {
            javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance(agreementName);
            ka.init(privateKey);
            ka.doPhase(peerPublicKey, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Key agreement failed for " + iid().encodeText(), e);
        }
    }

    // -- Accessors --

    public String agreementName()  { return agreementName; }
    public String keyFactoryName() { return keyFactoryName; }
    public long   multikeyCode()   { return multikeyCode; }
    public long   coseId()         { return coseId; }
    public long   rawKeyBytes()    { return rawKeyBytes; }

    // ==================================================================================
    // Built-in lookup — librarian-less fallback for algorithms backed by a Java
    // class with a {@code builtin()} factory.
    // ==================================================================================

    /** Built-in algorithm instance by multikey code, or {@code null}. */
    public static KeyAgreement builtinByMultikeyCode(int code) {
        if (code == (int) X25519.MULTIKEY_CODE) return X25519.builtin();
        return null;
    }

    /**
     * Convert a JCA {@link PublicKey} into a {@link MultiKey} by dispatching
     * on the algorithm name to a KeyAgreement leaf that knows the raw-bytes
     * encoding.  Today: X25519 / XDH only.
     *
     * @throws IllegalArgumentException if no built-in KeyAgreement leaf claims
     *         the JCA algorithm name
     */
    public static MultiKey toMultiKey(PublicKey jcaKey) {
        String alg = jcaKey.getAlgorithm();
        if ("X25519".equals(alg) || "XDH".equals(alg)) {
            X25519 x = X25519.builtin();
            return MultiKey.of(x, x.publicKeyToRaw(jcaKey));
        }
        throw new IllegalArgumentException(
                "No KeyAgreement leaf handles JCA algorithm: " + alg);
    }

    // ==================================================================================
    // Concrete key-agreement algorithms.
    // ==================================================================================

    /**
     * X25519 — Curve25519 in Montgomery form, used for ECDH key agreement.
     * Pure key-agreement primitive; combine with a KDF (HKDF, etc.) and an AEAD
     * to form a hybrid encryption ciphersuite.  See {@link Ciphersuite} for
     * bundles that compose X25519.
     */
    @Seed.Item(key = X25519.KEY, head = KeyAgreement.KEY)
    public static final class X25519 extends KeyAgreement {

        public static final String KEY = "cg.algorithm:x25519";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xec;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "XDH";

        @Seed.Property(role = AlgorithmVocabulary.KeyGenerator.KEY)
        public static final String KEY_GENERATOR = "X25519";

        @Seed.Property(role = AlgorithmVocabulary.AgreementName.KEY)
        public static final String AGREEMENT_NAME = "XDH";

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Okp.KEY);

        @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 32;

        @Seed.Property(role = AlgorithmVocabulary.Asn1Oid.KEY)
        public static final String ASN1_OID = "1.3.101.110";

        /**
         * SubjectPublicKeyInfo DER prefix for X25519.  Twelve bytes:
         * SEQUENCE(42) + SEQUENCE(5)(OID 1.3.101.110) + BIT STRING(33)(0x00).
         */
        @Seed.Property(role = AlgorithmVocabulary.SpkiPrefix.KEY)
        public static final byte[] SPKI_PREFIX = {
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
        };

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -28;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String x25519Gloss =
                "X25519 — Curve25519 in Montgomery form, used for ECDH key agreement";

        public X25519()                         { super(ItemRef.iid(KEY)); }
        public X25519(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public X25519(ItemRef iid, Librarian l) { super(iid, l); }

        /**
         * Extract the 32-byte raw form of an X25519 public key — little-endian
         * u-coordinate per RFC 7748.  No sign bit (Montgomery curves use only
         * the u-coordinate).
         */
        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            if (!(jcaKey instanceof XECPublicKey xec)) {
                throw new IllegalArgumentException(
                        "Expected XECPublicKey for X25519, got " + jcaKey.getClass().getName());
            }
            BigInteger u = xec.getU();
            byte[] uBE = u.toByteArray();
            byte[] raw = new byte[32];
            int copyLen = Math.min(uBE.length, 32);
            for (int i = 0; i < copyLen; i++) {
                raw[i] = uBE[uBE.length - 1 - i];
            }
            return raw;
        }

        /**
         * Override to use the explicit "X25519" JCA name rather than the
         * family name "XDH" (which would require a NamedParameterSpec to
         * disambiguate from X448).
         */
        @Override
        public KeyPair generateKeyPair() {
            try {
                return KeyPairGenerator.getInstance("X25519").generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("X25519 unavailable in this JCA provider", e);
            }
        }

        /**
         * Built-in instance for librarian-less use.  Has its instance fields
         * populated directly from the static {@code @Seed.Property} constants.
         */
        public static X25519 builtin() {
            X25519 a = new X25519();
            a.agreementName  = AGREEMENT_NAME;
            a.keyFactoryName = KEY_FACTORY;
            a.spkiPrefix     = SPKI_PREFIX.clone();
            a.coseId         = COSE_ID;
            a.multikeyCode   = MULTIKEY_CODE;
            a.rawKeyBytes    = RAW_KEY_BYTES;
            return a;
        }
    }

    /**
     * ECDH-ES with HKDF-SHA256 — the JOSE/COSE-named key-agreement suite over
     * X25519.  Composed: X25519 ECDH for the shared-secret derivation, then
     * HKDF-SHA256 to expand it into a wrap key.  Used as the KEM half of
     * X25519_AesGcm256_Hkdf256 (see {@link Ciphersuite}).
     */
    @Seed.Item(key = EcdhEsHkdf256.KEY, head = KeyAgreement.KEY)
    public static final class EcdhEsHkdf256 extends KeyAgreement {

        public static final String KEY = "cg.algorithm:ecdh-es-hkdf-256";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -25;

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0xec;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "XDH";

        @Seed.Property(role = AlgorithmVocabulary.AgreementName.KEY)
        public static final String AGREEMENT_NAME = "XDH";

        /** The KDF this composite uses — pointer to a {@link Kdf} sememe (HKDF-SHA-256). */
        @Seed.Property(role = AlgorithmVocabulary.Kdf.KEY)
        public static final ItemRef KDF = ItemRef.iid(Kdf.HkdfSha256.KEY);

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Okp.KEY);

        @Seed.Property(role = AlgorithmVocabulary.RawKeyBytes.KEY)
        public static final long RAW_KEY_BYTES = 32;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String ecdhEsGloss =
                "ECDH-ES with HKDF-SHA-256 over X25519 — key agreement for content-key derivation";

        public EcdhEsHkdf256()                         { super(ItemRef.iid(KEY)); }
        public EcdhEsHkdf256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public EcdhEsHkdf256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            throw new UnsupportedOperationException(
                    "EcdhEsHkdf256 publicKeyToRaw not yet implemented — use X25519 directly");
        }
    }

    /** RSA-OAEP with SHA-256 — key transport via RSA wrapping. */
    @Seed.Item(key = RsaOaep256.KEY, head = KeyAgreement.KEY)
    public static final class RsaOaep256 extends KeyAgreement {

        public static final String KEY = "cg.algorithm:rsa-oaep-256";

        @Seed.Property(role = AlgorithmVocabulary.Purpose.KEY)
        public static final ItemRef PURPOSE = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -41;

        @Seed.Property(role = AlgorithmVocabulary.MultikeyCode.KEY)
        public static final long MULTIKEY_CODE = 0x1205;

        @Seed.Property(role = AlgorithmVocabulary.KeyFactory.KEY)
        public static final String KEY_FACTORY = "RSA";

        // OAEP padding is an internal detail of RSA-OAEP-256, not a separately
        // composable KDF — no Kdf reference here.

        @Seed.Property(role = AlgorithmVocabulary.KeyFamily.KEY)
        public static final ItemRef KEY_FAMILY = ItemRef.iid(AlgorithmVocabulary.Rsa.KEY);

        @Seed.Property(role = AlgorithmVocabulary.KeyBits.KEY)
        public static final long KEY_BITS = 4096;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String rsaOaepGloss =
                "RSA-OAEP with SHA-256 — key transport via RSA wrapping, 4096-bit default";

        public RsaOaep256()                         { super(ItemRef.iid(KEY)); }
        public RsaOaep256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public RsaOaep256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override
        public byte[] publicKeyToRaw(PublicKey jcaKey) {
            throw new UnsupportedOperationException(
                    "RsaOaep256 publicKeyToRaw not yet implemented (SubjectPublicKeyInfo encoding)");
        }
    }
}
