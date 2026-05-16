package dev.everydaythings.graph.encoding;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.ThematicRole;
import io.ipfs.multihash.Multihash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Digest — the archetype of cryptographic hash functions.
 *
 * <p>A <i>digest</i> is a fixed-length cryptographic fingerprint of arbitrary
 * input bytes, produced by a one-way hash function. Two inputs producing the
 * same digest are computationally infeasible to find (collision resistance);
 * given a digest, recovering the original input is computationally infeasible
 * (preimage resistance). Common Graph uses digests pervasively as content
 * addresses, structural identifiers, and pre-rotation commitments.
 *
 * <p>This class is itself a seed-item archetype (with head
 * {@link CoreVocabulary.Archetype}). Each inner class is an <i>instance</i> of
 * the Digest archetype — a specific hash function (SHA-256, SHA-512, BLAKE3,
 * etc.) with its multihash code, expected digest length, JCA algorithm name,
 * and behavior methods.
 *
 * <p>Java-side dispatch: {@code Digest.Sha256.digest(bytes)} produces the hash
 * bytes; {@code Digest.Sha256.MULTIHASH_CODE} gives the multihash table byte.
 *
 * <p>Graph-side dispatch: every entry is a seed item whose bootstrap frames
 * assert its multihash code and digest length via the {@link MultihashCode}
 * and {@link DigestLength} predicates.
 *
 * <p>Fields do double duty: {@code public static final} for Java-side use AND
 * {@code @Seed.Frame}-annotated for declarative seed-item generation — one
 * field, two roles.
 */
@Seed.Item(key = Digest.KEY, head = CoreVocabulary.Archetype.KEY)
public final class Digest {

    /** Canonical key for the Digest archetype. */
    public static final String KEY = "cg.archetype:digest";

    /** The archetype IID for Digest. */

    private Digest() {}

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a fixed-length cryptographic fingerprint of arbitrary input bytes, "
                    + "produced by a one-way hash function — used for content addressing, "
                    + "integrity verification, and forward commitment";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "digest";

    // ==================================================================================
    // Predicates relating Digest instances to their numeric/structural properties
    // ==================================================================================

    /** Relates a Digest instance to its multihash code byte. */
    @Seed.Item(key = MultihashCode.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class MultihashCode {
        public static final String KEY = "cg.predicate:multihash-code";
        private MultihashCode() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the multihash code byte identifying a hash algorithm in the IPFS multihash table";
    }

    /** Relates a Digest instance to its expected digest length in bytes. */
    @Seed.Item(key = DigestLength.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class DigestLength {
        public static final String KEY = "cg.predicate:digest-length";
        private DigestLength() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the length in bytes of digests produced by a hash algorithm";
    }

    // ==================================================================================
    // Digest instances — each is an instance of the Digest archetype
    // ==================================================================================

    /** SHA-2 256-bit — the most common cryptographic hash in current use. */
    @Seed.Item(key = Sha256.KEY, head = Digest.KEY)
    public static final class Sha256 {
        public static final String KEY = "cg.digest:sha2-256";

        @Seed.Frame(predicate = MultihashCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTIHASH_CODE = 0x12L;

        @Seed.Frame(predicate = DigestLength.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long DIGEST_LENGTH = 32L;

        public static final String JCA_NAME = "SHA-256";
        public static final Multihash.Type MULTIHASH_TYPE = Multihash.Type.sha2_256;

        private Sha256() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "SHA-2 256-bit cryptographic hash function";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "SHA-256";

        /** Produce a SHA-256 digest of the given bytes. */
        public static byte[] digest(byte[] input) {
            return Digest.compute(JCA_NAME, input);
        }

        /** Convenience: produce a {@link Multihash} carrying the SHA-256 digest. */
        public static Multihash toMultihash(byte[] input) {
            return new Multihash(MULTIHASH_TYPE, digest(input));
        }
    }

    /** SHA-2 512-bit. */
    @Seed.Item(key = Sha512.KEY, head = Digest.KEY)
    public static final class Sha512 {
        public static final String KEY = "cg.digest:sha2-512";

        @Seed.Frame(predicate = MultihashCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTIHASH_CODE = 0x13L;

        @Seed.Frame(predicate = DigestLength.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long DIGEST_LENGTH = 64L;

        public static final String JCA_NAME = "SHA-512";
        public static final Multihash.Type MULTIHASH_TYPE = Multihash.Type.sha2_512;

        private Sha512() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "SHA-2 512-bit cryptographic hash function";

        public static byte[] digest(byte[] input) {
            return Digest.compute(JCA_NAME, input);
        }

        public static Multihash toMultihash(byte[] input) {
            return new Multihash(MULTIHASH_TYPE, digest(input));
        }
    }

    /** SHA-3 256-bit. */
    @Seed.Item(key = Sha3_256.KEY, head = Digest.KEY)
    public static final class Sha3_256 {
        public static final String KEY = "cg.digest:sha3-256";

        @Seed.Frame(predicate = MultihashCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTIHASH_CODE = 0x16L;

        @Seed.Frame(predicate = DigestLength.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long DIGEST_LENGTH = 32L;

        public static final String JCA_NAME = "SHA3-256";
        public static final Multihash.Type MULTIHASH_TYPE = Multihash.Type.sha3_256;

        private Sha3_256() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "SHA-3 256-bit cryptographic hash function";

        public static byte[] digest(byte[] input) {
            return Digest.compute(JCA_NAME, input);
        }

        public static Multihash toMultihash(byte[] input) {
            return new Multihash(MULTIHASH_TYPE, digest(input));
        }
    }

    /** BLAKE3 256-bit output. */
    @Seed.Item(key = Blake3.KEY, head = Digest.KEY)
    public static final class Blake3 {
        public static final String KEY = "cg.digest:blake3";

        @Seed.Frame(predicate = MultihashCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTIHASH_CODE = 0x1eL;

        @Seed.Frame(predicate = DigestLength.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long DIGEST_LENGTH = 32L;

        public static final String JCA_NAME = "BLAKE3-256";
        public static final Multihash.Type MULTIHASH_TYPE = Multihash.Type.blake3;

        private Blake3() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "BLAKE3 cryptographic hash function (256-bit output)";

        public static byte[] digest(byte[] input) {
            return Digest.compute(JCA_NAME, input);
        }

        public static Multihash toMultihash(byte[] input) {
            return new Multihash(MULTIHASH_TYPE, digest(input));
        }
    }

    /** BLAKE2b 256-bit. */
    @Seed.Item(key = Blake2b_256.KEY, head = Digest.KEY)
    public static final class Blake2b_256 {
        public static final String KEY = "cg.digest:blake2b-256";

        @Seed.Frame(predicate = MultihashCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTIHASH_CODE = 0xb220L;

        @Seed.Frame(predicate = DigestLength.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long DIGEST_LENGTH = 32L;

        public static final String JCA_NAME = "BLAKE2B-256";
        public static final Multihash.Type MULTIHASH_TYPE = Multihash.Type.blake2b_256;

        private Blake2b_256() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "BLAKE2b cryptographic hash function (256-bit output)";

        public static byte[] digest(byte[] input) {
            return Digest.compute(JCA_NAME, input);
        }

        public static Multihash toMultihash(byte[] input) {
            return new Multihash(MULTIHASH_TYPE, digest(input));
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Compute a digest under the given multihash-typed algorithm. The
     * algorithm-to-JCA-name lookup is internal; callers work in
     * multihash terms and don't have to know JCA naming.
     */
    public static byte[] compute(Multihash.Type type, byte[] input) {
        return compute(jcaNameFor(type), input);
    }

    /** Internal: invoke JCA to compute a digest. */
    private static byte[] compute(String jcaName, byte[] input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        try {
            return MessageDigest.getInstance(jcaName).digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Hash algorithm not available: " + jcaName, e);
        }
    }

    /** Translate a multihash type to its JCA algorithm name. */
    private static String jcaNameFor(Multihash.Type type) {
        if (type == Multihash.Type.sha2_256)    return Sha256.JCA_NAME;
        if (type == Multihash.Type.sha2_512)    return Sha512.JCA_NAME;
        if (type == Multihash.Type.sha3_256)    return Sha3_256.JCA_NAME;
        if (type == Multihash.Type.blake3)      return Blake3.JCA_NAME;
        if (type == Multihash.Type.blake2b_256) return Blake2b_256.JCA_NAME;
        throw new IllegalArgumentException("Unsupported multihash type: " + type);
    }

    /**
     * Lookup the Multihash.Type → its corresponding Digest IID. Useful when
     * decoding raw multihash bytes into Digest-instance identity.
     */
    public static ItemRef iidForMultihashType(Multihash.Type type) {
        if (type == Multihash.Type.sha2_256)   return ItemRef.iid(Sha256.KEY);
        if (type == Multihash.Type.sha2_512)   return ItemRef.iid(Sha512.KEY);
        if (type == Multihash.Type.sha3_256)   return ItemRef.iid(Sha3_256.KEY);
        if (type == Multihash.Type.blake3)     return ItemRef.iid(Blake3.KEY);
        if (type == Multihash.Type.blake2b_256) return ItemRef.iid(Blake2b_256.KEY);
        return null;
    }
}
