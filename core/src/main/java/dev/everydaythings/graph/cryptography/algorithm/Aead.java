package dev.everydaythings.graph.cryptography.algorithm;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.AlgorithmVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.AlgorithmParameterSpec;

/**
 * Aead — the sub-archetype for authenticated-encryption-with-associated-data
 * (AEAD) cryptographic content ciphers.
 *
 * <p>AEAD algorithms take a symmetric key (typically derived via a
 * {@link KeyAgreement key-agreement} suite or wrapped per-recipient by one)
 * plus a nonce and additional authenticated data, and produce ciphertext +
 * authentication tag.  No long-term key material lives in a Vault for an AEAD
 * cipher — the key flows in from the surrounding ciphersuite.
 *
 * <p>Phase-A leaves are data-only.  The {@code encrypt} / {@code decrypt}
 * runtime operations land when the content-encryption story comes online.
 */
@Seed.Item(key = Aead.KEY, head = Algorithm.KEY)
public abstract class Aead extends Algorithm {

    /** Canonical key for the AEAD content-cipher sub-archetype. */
    public static final String KEY = "cg.archetype:aead-algorithm";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String aeadGloss =
            "an authenticated-encryption-with-associated-data cipher: symmetric "
                    + "encryption that also produces an integrity tag bound to the ciphertext "
                    + "and any associated unencrypted data";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String aeadLemma = "AEAD algorithm";

    // -- Instance fields populated from the leaf's manifest by BodyBinder --

    @Seed.Property(role = AlgorithmVocabulary.Transformation.KEY)
    protected String transformation;

    @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
    protected long coseId;

    @Seed.Property(role = AlgorithmVocabulary.KeyBytes.KEY)
    protected long keyBytes;

    @Seed.Property(role = AlgorithmVocabulary.NonceBytes.KEY)
    protected long nonceBytes;

    @Seed.Property(role = AlgorithmVocabulary.TagBits.KEY)
    protected long tagBits;

    protected Aead(ItemRef iid) {
        super(iid);
    }

    protected Aead(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // -- Operations --

    /**
     * Encrypt {@code plaintext} under {@code key} with {@code nonce}, binding
     * {@code aad} as additional authenticated data.  Returns ciphertext with
     * authentication tag appended (standard JCA AEAD output format).
     */
    public byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, keyAlgorithmName()),
                    params(nonce));
            if (aad != null && aad.length > 0) cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException(
                    "AEAD encrypt failed for " + iid().encodeText(), e);
        }
    }

    /**
     * Decrypt and verify {@code ciphertext} (ciphertext + tag) under
     * {@code key} with {@code nonce}, binding {@code aad}.  Throws if the
     * authentication tag does not verify.
     */
    public byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, keyAlgorithmName()),
                    params(nonce));
            if (aad != null && aad.length > 0) cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException(
                    "AEAD decrypt failed for " + iid().encodeText(), e);
        }
    }

    /**
     * The JCA "key algorithm" name to wrap the symmetric key bytes in a
     * {@link SecretKeySpec}.  "AES" for AES-GCM, "ChaCha20" for
     * ChaCha20-Poly1305.
     */
    protected abstract String keyAlgorithmName();

    /**
     * Construct the right {@link AlgorithmParameterSpec} for this cipher and
     * nonce.  AES-GCM needs a {@link GCMParameterSpec} with tag length;
     * ChaCha20-Poly1305 uses a plain {@link IvParameterSpec}.
     */
    protected abstract AlgorithmParameterSpec params(byte[] nonce);

    // -- Accessors --

    public String transformation() { return transformation; }
    public long   coseId()         { return coseId; }
    public long   keyBytes()       { return keyBytes; }
    public long   nonceBytes()     { return nonceBytes; }
    public long   tagBits()        { return tagBits; }

    // ==================================================================================
    // Concrete AEAD algorithms.
    // ==================================================================================

    /** AES-128-GCM — AES with 128-bit key in GCM mode. */
    @Seed.Item(key = AesGcm128.KEY, head = Aead.KEY)
    public static final class AesGcm128 extends Aead {

        public static final String KEY = "cg.algorithm:aes-gcm-128";

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = 1;

        @Seed.Property(role = AlgorithmVocabulary.Transformation.KEY)
        public static final String TRANSFORMATION = "AES/GCM/NoPadding";

        @Seed.Property(role = AlgorithmVocabulary.KeyBytes.KEY)
        public static final long KEY_BYTES = 16;

        @Seed.Property(role = AlgorithmVocabulary.NonceBytes.KEY)
        public static final long NONCE_BYTES = 12;

        @Seed.Property(role = AlgorithmVocabulary.TagBits.KEY)
        public static final long TAG_BITS = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String gcm128Gloss =
                "AES-128-GCM — AES with 128-bit key in Galois/Counter Mode";

        public AesGcm128()                         { super(ItemRef.iid(KEY)); }
        public AesGcm128(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public AesGcm128(ItemRef iid, Librarian l) { super(iid, l); }

        @Override protected String keyAlgorithmName() { return "AES"; }

        @Override
        protected AlgorithmParameterSpec params(byte[] nonce) {
            return new GCMParameterSpec((int) tagBits, nonce);
        }

        /** Librarian-less factory with instance fields populated from static constants. */
        public static AesGcm128 builtin() {
            AesGcm128 a = new AesGcm128();
            a.transformation = TRANSFORMATION;
            a.coseId         = COSE_ID;
            a.keyBytes       = KEY_BYTES;
            a.nonceBytes     = NONCE_BYTES;
            a.tagBits        = TAG_BITS;
            return a;
        }
    }

    /** AES-256-GCM — AES with 256-bit key in GCM mode. */
    @Seed.Item(key = AesGcm256.KEY, head = Aead.KEY)
    public static final class AesGcm256 extends Aead {

        public static final String KEY = "cg.algorithm:aes-gcm-256";

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = 3;

        @Seed.Property(role = AlgorithmVocabulary.Transformation.KEY)
        public static final String TRANSFORMATION = "AES/GCM/NoPadding";

        @Seed.Property(role = AlgorithmVocabulary.KeyBytes.KEY)
        public static final long KEY_BYTES = 32;

        @Seed.Property(role = AlgorithmVocabulary.NonceBytes.KEY)
        public static final long NONCE_BYTES = 12;

        @Seed.Property(role = AlgorithmVocabulary.TagBits.KEY)
        public static final long TAG_BITS = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String gcm256Gloss =
                "AES-256-GCM — AES with 256-bit key in Galois/Counter Mode";

        public AesGcm256()                         { super(ItemRef.iid(KEY)); }
        public AesGcm256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public AesGcm256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override protected String keyAlgorithmName() { return "AES"; }

        @Override
        protected AlgorithmParameterSpec params(byte[] nonce) {
            return new GCMParameterSpec((int) tagBits, nonce);
        }

        public static AesGcm256 builtin() {
            AesGcm256 a = new AesGcm256();
            a.transformation = TRANSFORMATION;
            a.coseId         = COSE_ID;
            a.keyBytes       = KEY_BYTES;
            a.nonceBytes     = NONCE_BYTES;
            a.tagBits        = TAG_BITS;
            return a;
        }
    }

    /** ChaCha20-Poly1305 — ChaCha20 stream cipher with Poly1305 MAC. */
    @Seed.Item(key = ChaCha20Poly1305.KEY, head = Aead.KEY)
    public static final class ChaCha20Poly1305 extends Aead {

        public static final String KEY = "cg.algorithm:chacha20-poly1305";

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = 24;

        @Seed.Property(role = AlgorithmVocabulary.Transformation.KEY)
        public static final String TRANSFORMATION = "ChaCha20-Poly1305";

        @Seed.Property(role = AlgorithmVocabulary.KeyBytes.KEY)
        public static final long KEY_BYTES = 32;

        @Seed.Property(role = AlgorithmVocabulary.NonceBytes.KEY)
        public static final long NONCE_BYTES = 12;

        @Seed.Property(role = AlgorithmVocabulary.TagBits.KEY)
        public static final long TAG_BITS = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String chacha20Gloss =
                "ChaCha20-Poly1305 — ChaCha20 stream cipher with Poly1305 authentication";

        public ChaCha20Poly1305()                         { super(ItemRef.iid(KEY)); }
        public ChaCha20Poly1305(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public ChaCha20Poly1305(ItemRef iid, Librarian l) { super(iid, l); }

        @Override protected String keyAlgorithmName() { return "ChaCha20"; }

        @Override
        protected AlgorithmParameterSpec params(byte[] nonce) {
            return new IvParameterSpec(nonce);
        }

        public static ChaCha20Poly1305 builtin() {
            ChaCha20Poly1305 a = new ChaCha20Poly1305();
            a.transformation = TRANSFORMATION;
            a.coseId         = COSE_ID;
            a.keyBytes       = KEY_BYTES;
            a.nonceBytes     = NONCE_BYTES;
            a.tagBits        = TAG_BITS;
            return a;
        }
    }
}
