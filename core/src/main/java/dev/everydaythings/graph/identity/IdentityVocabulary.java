package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;

/**
 * Identity-specific narrowings — sememes whose meaning is tied to the identity /
 * key-management domain rather than being general English concepts.
 *
 * <p>Currently holds the two key-track purposes (signing vs encryption). General
 * sememes used in identity frames (Next, Threshold, Witness, Sequence, Expires,
 * Delegator, Compromise, Retirement, Fraud, Mistake) live in
 * {@code semantics.CoreVocabulary} because their meaning isn't identity-specific
 * — they happen to apply to identity contexts but mean what they mean everywhere.
 *
 * <p>Future identity-specific additions (TrustLevel ordinals, TrustScope sememes,
 * key-algorithm sememes if needed) will land here.
 *
 * <p>Each entry carries an English gloss and lemma lexemes via {@code @Seed.Frame}
 * annotations so the bootstrap produces queryable token-dictionary entries.
 */
public final class IdentityVocabulary {

    private IdentityVocabulary() {}

    /** The signing-key track — Ed25519-class keys used for signature attestation. */
    @Seed.Item(key = Signing.KEY)
    public static final class Signing {
        public static final String KEY = "cg.purpose:signing";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Signing() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic signing-key track of an identity (e.g., Ed25519)";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "signing";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "sign";
    }

    /** The encryption-key track — X25519-class keys used for confidentiality. */
    @Seed.Item(key = Encryption.KEY)
    public static final class Encryption {
        public static final String KEY = "cg.purpose:encryption";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Encryption() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic encryption-key track of an identity (e.g., X25519)";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "encryption";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "encrypt";
    }
}
