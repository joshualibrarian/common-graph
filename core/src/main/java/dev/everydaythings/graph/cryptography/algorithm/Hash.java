package dev.everydaythings.graph.cryptography.algorithm;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.AlgorithmVocabulary;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import io.ipfs.multihash.Multihash;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

/**
 * Hash — the sub-archetype for cryptographic hash (digest) algorithms.
 *
 * <p>Supplies {@code digest(bytes)} and {@code toMultihash(bytes)} that all
 * concrete hash leaves (SHA-2 family, SHA-3 family, BLAKE family) inherit
 * without overrides.  Keyless primitives — no Vault, no purpose, no key
 * agreement.
 *
 * <p>Each leaf carries the standard wire identifiers (multihash code, COSE id,
 * ASN.1 OID) plus the JCA {@code MessageDigest} name.  Operations read those
 * facts from instance fields populated by
 * {@link dev.everydaythings.graph.item.BodyBinder BodyBinder} at hydration.
 *
 * <p>Static helpers {@link #compute(Multihash.Type, byte[])} and
 * {@link #builtinByMultihashType(Multihash.Type)} provide librarian-less
 * lookup and execution paths — used by {@link
 * dev.everydaythings.graph.ref.ContentRef ContentRef} /
 * {@link dev.everydaythings.graph.ref.DatumRef DatumRef} construction and by
 * {@link dev.everydaythings.graph.canonical.HashTree HashTree}'s walker.
 */
@Seed.Item(key = Hash.KEY, head = Algorithm.KEY)
public abstract class Hash extends Algorithm {

    /** Canonical key for the hash-algorithm sub-archetype. */
    public static final String KEY = "cg.archetype:hash-algorithm";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String hashGloss =
            "a keyless cryptographic primitive that produces a fixed-length fingerprint "
                    + "of arbitrary input bytes — collision-resistant and preimage-resistant";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String hashLemma = "hash algorithm";

    // -- Instance fields populated from the leaf's manifest by BodyBinder --

    @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
    protected String digestName;

    @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
    protected long multihashCode;

    @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
    protected long digestLength;

    @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
    protected long coseId;

    protected Hash(ItemRef iid) {
        super(iid);
    }

    protected Hash(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // -- Operations --

    /** Compute this algorithm's digest of the given bytes. */
    public byte[] digest(byte[] input) {
        return compute(digestName, input);
    }

    /** Wrap the digest in a {@link Multihash} carrying this algorithm's table code. */
    public Multihash toMultihash(byte[] input) {
        return new Multihash(multihashType(), digest(input));
    }

    /** The IPFS {@link Multihash.Type} corresponding to this algorithm's multihash code. */
    public abstract Multihash.Type multihashType();

    // -- Accessors --

    public String digestName()    { return digestName; }
    public long   multihashCode() { return multihashCode; }
    public long   digestLength()  { return digestLength; }
    public long   coseId()        { return coseId; }

    // ==================================================================================
    // Static helpers — librarian-less compute and lookup paths.
    //
    // Callers like ContentRef, DatumRef, and HashTree run during bootstrap or in
    // librarian-less contexts where the full graph isn't available.  These keep
    // the multihash-typed API working without depending on hydration.
    // ==================================================================================

    /**
     * Compute a digest under the given multihash type.  Translates to the JCA
     * algorithm name internally; callers work in multihash terms.
     */
    public static byte[] compute(Multihash.Type type, byte[] input) {
        return compute(jcaNameFor(type), input);
    }

    /**
     * Invoke JCA to compute a digest by its JCA algorithm name.  If no
     * registered provider supplies the algorithm on the first attempt, install
     * BouncyCastle (which carries Blake3, Blake2*, and other non-JDK-native
     * algorithms) and retry once.  BC is added at lowest priority, so
     * JDK-native algorithms keep being preferred.
     */
    public static byte[] compute(String jcaName, byte[] input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        try {
            return MessageDigest.getInstance(jcaName).digest(input);
        } catch (NoSuchAlgorithmException firstAttempt) {
            installBouncyCastle();
            try {
                return MessageDigest.getInstance(jcaName).digest(input);
            } catch (NoSuchAlgorithmException retry) {
                throw new IllegalArgumentException("Hash algorithm not available: " + jcaName, retry);
            }
        }
    }

    /**
     * Register the BouncyCastle JCA provider so non-JDK-native digest names
     * (BLAKE3-256, BLAKE2B-256, BLAKE2S-256, etc.) resolve via {@code
     * MessageDigest.getInstance}.  Idempotent: calling twice does not register
     * two copies.  Added at lowest priority via {@link Security#addProvider},
     * so JDK-native algorithm names continue to resolve to the JDK provider.
     */
    public static synchronized void installBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** Translate a multihash type to its JCA algorithm name. */
    private static String jcaNameFor(Multihash.Type type) {
        if (type == Multihash.Type.sha2_256)    return Sha256.DIGEST_NAME;
        if (type == Multihash.Type.sha2_512)    return Sha512.DIGEST_NAME;
        if (type == Multihash.Type.sha3_256)    return Sha3_256.DIGEST_NAME;
        if (type == Multihash.Type.blake3)      return Blake3.DIGEST_NAME;
        if (type == Multihash.Type.blake2b_256) return Blake2b_256.DIGEST_NAME;
        throw new IllegalArgumentException("Unsupported multihash type: " + type);
    }

    /**
     * Lookup the Multihash.Type → its corresponding Hash sememe IID.  Useful
     * when decoding raw multihash bytes into algorithm identity.
     */
    public static ItemRef iidForMultihashType(Multihash.Type type) {
        if (type == Multihash.Type.sha2_256)    return ItemRef.iid(Sha256.KEY);
        if (type == Multihash.Type.sha2_512)    return ItemRef.iid(Sha512.KEY);
        if (type == Multihash.Type.sha3_256)    return ItemRef.iid(Sha3_256.KEY);
        if (type == Multihash.Type.blake3)      return ItemRef.iid(Blake3.KEY);
        if (type == Multihash.Type.blake2b_256) return ItemRef.iid(Blake2b_256.KEY);
        return null;
    }

    /** Built-in {@link Hash} instance by Multihash type, or {@code null}. */
    public static Hash builtinByMultihashType(Multihash.Type type) {
        if (type == Multihash.Type.sha2_256)    return Sha256.builtin();
        if (type == Multihash.Type.sha2_512)    return Sha512.builtin();
        if (type == Multihash.Type.sha3_256)    return Sha3_256.builtin();
        if (type == Multihash.Type.blake3)      return Blake3.builtin();
        if (type == Multihash.Type.blake2b_256) return Blake2b_256.builtin();
        return null;
    }

    // ==================================================================================
    // Concrete hash algorithms — pure data declarations.
    // ==================================================================================

    /** SHA-2 256-bit — the most common cryptographic hash in current use. */
    @Seed.Item(key = Sha256.KEY, head = Hash.KEY)
    @Seed.Embodies(key = Sha256.KEY)
    public static final class Sha256 extends Hash {

        public static final String KEY = "cg.algorithm:sha2-256";

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -16;

        @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
        public static final String DIGEST_NAME = "SHA-256";

        @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
        public static final long MULTIHASH_CODE = 0x12L;

        @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
        public static final long DIGEST_LENGTH = 32L;

        @Seed.Property(role = AlgorithmVocabulary.Asn1Oid.KEY)
        public static final String ASN1_OID = "2.16.840.1.101.3.4.2.1";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String sha256Gloss =
                "SHA-2 256-bit Secure Hash Algorithm (NIST FIPS 180-4)";

        public Sha256()                         { super(ItemRef.iid(KEY)); }
        public Sha256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Sha256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override public Multihash.Type multihashType() { return Multihash.Type.sha2_256; }

        public static final Multihash.Type MULTIHASH_TYPE = Multihash.Type.sha2_256;

        /** Static convenience: SHA-256 digest of bytes. */
        public static byte[] digestOf(byte[] input) {
            return Hash.compute(DIGEST_NAME, input);
        }

        /** Librarian-less builtin instance with instance fields populated from constants. */
        public static Sha256 builtin() {
            Sha256 a = new Sha256();
            a.digestName    = DIGEST_NAME;
            a.multihashCode = MULTIHASH_CODE;
            a.digestLength  = DIGEST_LENGTH;
            a.coseId        = COSE_ID;
            return a;
        }
    }

    /** SHA-2 384-bit. */
    @Seed.Item(key = Sha384.KEY, head = Hash.KEY)
    @Seed.Embodies(key = Sha384.KEY)
    public static final class Sha384 extends Hash {

        public static final String KEY = "cg.algorithm:sha2-384";

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -43;

        @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
        public static final String DIGEST_NAME = "SHA-384";

        @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
        public static final long MULTIHASH_CODE = 0x20L;

        @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
        public static final long DIGEST_LENGTH = 48L;

        @Seed.Property(role = AlgorithmVocabulary.Asn1Oid.KEY)
        public static final String ASN1_OID = "2.16.840.1.101.3.4.2.2";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String sha384Gloss =
                "SHA-2 384-bit Secure Hash Algorithm (NIST FIPS 180-4)";

        public Sha384()                         { super(ItemRef.iid(KEY)); }
        public Sha384(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Sha384(ItemRef iid, Librarian l) { super(iid, l); }

        // No Multihash.Type entry for SHA-2 384 — return null and let static-helper paths skip.
        @Override public Multihash.Type multihashType() {
            throw new UnsupportedOperationException("SHA-384 has no Multihash.Type entry");
        }

        public static Sha384 builtin() {
            Sha384 a = new Sha384();
            a.digestName    = DIGEST_NAME;
            a.multihashCode = MULTIHASH_CODE;
            a.digestLength  = DIGEST_LENGTH;
            a.coseId        = COSE_ID;
            return a;
        }
    }

    /** SHA-2 512-bit. */
    @Seed.Item(key = Sha512.KEY, head = Hash.KEY)
    @Seed.Embodies(key = Sha512.KEY)
    public static final class Sha512 extends Hash {

        public static final String KEY = "cg.algorithm:sha2-512";

        @Seed.Property(role = AlgorithmVocabulary.CoseId.KEY)
        public static final long COSE_ID = -44;

        @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
        public static final String DIGEST_NAME = "SHA-512";

        @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
        public static final long MULTIHASH_CODE = 0x13L;

        @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
        public static final long DIGEST_LENGTH = 64L;

        @Seed.Property(role = AlgorithmVocabulary.Asn1Oid.KEY)
        public static final String ASN1_OID = "2.16.840.1.101.3.4.2.3";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String sha512Gloss =
                "SHA-2 512-bit Secure Hash Algorithm (NIST FIPS 180-4)";

        public Sha512()                         { super(ItemRef.iid(KEY)); }
        public Sha512(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Sha512(ItemRef iid, Librarian l) { super(iid, l); }

        @Override public Multihash.Type multihashType() { return Multihash.Type.sha2_512; }

        public static Sha512 builtin() {
            Sha512 a = new Sha512();
            a.digestName    = DIGEST_NAME;
            a.multihashCode = MULTIHASH_CODE;
            a.digestLength  = DIGEST_LENGTH;
            a.coseId        = COSE_ID;
            return a;
        }
    }

    /** SHA-3 256-bit. */
    @Seed.Item(key = Sha3_256.KEY, head = Hash.KEY)
    @Seed.Embodies(key = Sha3_256.KEY)
    public static final class Sha3_256 extends Hash {

        public static final String KEY = "cg.algorithm:sha3-256";

        @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
        public static final String DIGEST_NAME = "SHA3-256";

        @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
        public static final long MULTIHASH_CODE = 0x16L;

        @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
        public static final long DIGEST_LENGTH = 32L;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String sha3_256Gloss =
                "SHA-3 256-bit Secure Hash Algorithm (NIST FIPS 202)";

        public Sha3_256()                         { super(ItemRef.iid(KEY)); }
        public Sha3_256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Sha3_256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override public Multihash.Type multihashType() { return Multihash.Type.sha3_256; }

        public static Sha3_256 builtin() {
            Sha3_256 a = new Sha3_256();
            a.digestName    = DIGEST_NAME;
            a.multihashCode = MULTIHASH_CODE;
            a.digestLength  = DIGEST_LENGTH;
            return a;
        }
    }

    /** BLAKE3 256-bit output. */
    @Seed.Item(key = Blake3.KEY, head = Hash.KEY)
    @Seed.Embodies(key = Blake3.KEY)
    public static final class Blake3 extends Hash {

        public static final String KEY = "cg.algorithm:blake3";

        @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
        public static final String DIGEST_NAME = "BLAKE3-256";

        @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
        public static final long MULTIHASH_CODE = 0x1eL;

        @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
        public static final long DIGEST_LENGTH = 32L;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String blake3Gloss =
                "BLAKE3 cryptographic hash function (256-bit output)";

        public Blake3()                         { super(ItemRef.iid(KEY)); }
        public Blake3(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Blake3(ItemRef iid, Librarian l) { super(iid, l); }

        @Override public Multihash.Type multihashType() { return Multihash.Type.blake3; }

        public static Blake3 builtin() {
            Blake3 a = new Blake3();
            a.digestName    = DIGEST_NAME;
            a.multihashCode = MULTIHASH_CODE;
            a.digestLength  = DIGEST_LENGTH;
            return a;
        }
    }

    /** BLAKE2b 256-bit. */
    @Seed.Item(key = Blake2b_256.KEY, head = Hash.KEY)
    @Seed.Embodies(key = Blake2b_256.KEY)
    public static final class Blake2b_256 extends Hash {

        public static final String KEY = "cg.algorithm:blake2b-256";

        @Seed.Property(role = AlgorithmVocabulary.DigestName.KEY)
        public static final String DIGEST_NAME = "BLAKE2B-256";

        @Seed.Property(role = AlgorithmVocabulary.MultihashCode.KEY)
        public static final long MULTIHASH_CODE = 0xb220L;

        @Seed.Property(role = AlgorithmVocabulary.DigestLength.KEY)
        public static final long DIGEST_LENGTH = 32L;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String blake2bGloss =
                "BLAKE2b cryptographic hash function (256-bit output)";

        public Blake2b_256()                         { super(ItemRef.iid(KEY)); }
        public Blake2b_256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public Blake2b_256(ItemRef iid, Librarian l) { super(iid, l); }

        @Override public Multihash.Type multihashType() { return Multihash.Type.blake2b_256; }

        public static Blake2b_256 builtin() {
            Blake2b_256 a = new Blake2b_256();
            a.digestName    = DIGEST_NAME;
            a.multihashCode = MULTIHASH_CODE;
            a.digestLength  = DIGEST_LENGTH;
            return a;
        }
    }
}
