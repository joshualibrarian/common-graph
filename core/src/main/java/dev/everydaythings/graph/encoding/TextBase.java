package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import io.ipfs.multibase.Multibase;

/**
 * TextBase — the archetype of textual encodings for binary data.
 *
 * <p>A <i>text base</i> (or "baseN encoding") is a scheme for representing
 * arbitrary binary data as a string of characters drawn from a fixed alphabet
 * — useful for embedding bytes in text-based protocols, URLs, filenames, or
 * human-readable contexts where binary forms aren't safe. The base name
 * refers to the alphabet's radix: base16 uses 16 characters (hex), base32
 * uses 32, base58 uses 58, base64 uses 64.
 *
 * <p>This class is itself a seed-item archetype (with head
 * {@link CoreVocabulary.Archetype}). Each inner class is an <i>instance</i> of
 * the TextBase archetype — a specific text encoding with its multibase code
 * character, encoding/decoding behavior, and gloss/lexemes.
 *
 * <p>Java-side dispatch: {@code TextBase.Base32Lower.encode(bytes)} produces
 * the encoded string; {@code TextBase.Base32Lower.decode(text)} returns bytes.
 *
 * <p>The multibase code character (per the
 * <a href="https://github.com/multiformats/multibase">multibase</a> table) is
 * a single character prefix on a text-encoded string identifying which
 * encoding was used.
 */
@Seed.Item(key = TextBase.KEY, head = CoreVocabulary.Archetype.KEY)
public final class TextBase {

    /** Canonical key for the TextBase archetype. */
    public static final String KEY = "cg.archetype:text-base";

    /** The archetype IID for TextBase. */
    public static final ItemID IID = ItemID.fromString(KEY);

    private TextBase() {}

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a scheme for representing arbitrary binary data as text using a "
                    + "fixed alphabet — e.g. base16 (hex), base32, base58, base64";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "text base";

    // ==================================================================================
    // Predicate relating TextBase instances to their multibase code
    // ==================================================================================

    /** Relates a TextBase instance to its multibase code character. */
    @Seed.Item(key = MultibaseCode.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class MultibaseCode {
        public static final String KEY = "cg.predicate:multibase-code";
        public static final ItemID IID = ItemID.fromString(KEY);
        private MultibaseCode() {}

        @Seed.Frame(predicate = Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the multibase code character identifying a text encoding";
    }

    // ==================================================================================
    // TextBase instances — each is an instance of the TextBase archetype
    // ==================================================================================

    /** Base32 lowercase (RFC 4648). */
    @Seed.Item(key = Base32Lower.KEY, head = TextBase.KEY)
    public static final class Base32Lower {
        public static final String KEY = "cg.textbase:base32";
        public static final ItemID IID = ItemID.fromString(KEY);

        @Seed.Frame(predicate = MultibaseCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MULTIBASE_CODE = "b";

        public static final Multibase.Base MULTIBASE = Multibase.Base.Base32;

        private Base32Lower() {}

        @Seed.Frame(predicate = Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Base32 lowercase text encoding (RFC 4648)";

        @Seed.Frame(predicate = Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "base32";

        public static String encode(byte[] data) { return Multibase.encode(MULTIBASE, data); }
        public static byte[] decode(String text) { return Multibase.decode(text); }
    }

    /** Base32 uppercase (RFC 4648). */
    @Seed.Item(key = Base32Upper.KEY, head = TextBase.KEY)
    public static final class Base32Upper {
        public static final String KEY = "cg.textbase:base32-upper";
        public static final ItemID IID = ItemID.fromString(KEY);

        @Seed.Frame(predicate = MultibaseCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MULTIBASE_CODE = "B";

        public static final Multibase.Base MULTIBASE = Multibase.Base.Base32Upper;

        private Base32Upper() {}

        @Seed.Frame(predicate = Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Base32 uppercase text encoding (RFC 4648)";

        public static String encode(byte[] data) { return Multibase.encode(MULTIBASE, data); }
        public static byte[] decode(String text) { return Multibase.decode(text); }
    }

    /** Base58 with Bitcoin-style alphabet. */
    @Seed.Item(key = Base58Btc.KEY, head = TextBase.KEY)
    public static final class Base58Btc {
        public static final String KEY = "cg.textbase:base58-btc";
        public static final ItemID IID = ItemID.fromString(KEY);

        @Seed.Frame(predicate = MultibaseCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MULTIBASE_CODE = "z";

        public static final Multibase.Base MULTIBASE = Multibase.Base.Base58BTC;

        private Base58Btc() {}

        @Seed.Frame(predicate = Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Base58 text encoding with Bitcoin-style alphabet";

        @Seed.Frame(predicate = Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "base58";

        public static String encode(byte[] data) { return Multibase.encode(MULTIBASE, data); }
        public static byte[] decode(String text) { return Multibase.decode(text); }
    }

    /** Base64 URL-safe (RFC 4648). */
    @Seed.Item(key = Base64Url.KEY, head = TextBase.KEY)
    public static final class Base64Url {
        public static final String KEY = "cg.textbase:base64-url";
        public static final ItemID IID = ItemID.fromString(KEY);

        @Seed.Frame(predicate = MultibaseCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MULTIBASE_CODE = "u";

        public static final Multibase.Base MULTIBASE = Multibase.Base.Base64Url;

        private Base64Url() {}

        @Seed.Frame(predicate = Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Base64 URL-safe text encoding (RFC 4648)";

        public static String encode(byte[] data) { return Multibase.encode(MULTIBASE, data); }
        public static byte[] decode(String text) { return Multibase.decode(text); }
    }

    /** Base16 (hex) lowercase. */
    @Seed.Item(key = Base16Lower.KEY, head = TextBase.KEY)
    public static final class Base16Lower {
        public static final String KEY = "cg.textbase:base16";
        public static final ItemID IID = ItemID.fromString(KEY);

        @Seed.Frame(predicate = MultibaseCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MULTIBASE_CODE = "f";

        public static final Multibase.Base MULTIBASE = Multibase.Base.Base16;

        private Base16Lower() {}

        @Seed.Frame(predicate = Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Base16 (hex) lowercase text encoding";

        @Seed.Frame(predicate = Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "hex";

        public static String encode(byte[] data) { return Multibase.encode(MULTIBASE, data); }
        public static byte[] decode(String text) { return Multibase.decode(text); }
    }
}
