package dev.everydaythings.graph.language;


import dev.everydaythings.graph.id.ItemRef;

import static dev.everydaythings.graph.Seed.*;

/**
 * Preposition sememes — function words whose job is to <em>assign a thematic
 * role</em> to the noun phrase that follows them.
 *
 * <p>In "create chess on myItem", the preposition "on" tells the parser that
 * "myItem" fills the GOAL role of the CREATE frame. Each preposition carries
 * an {@link LexicalVocabulary.AssignedRole} frame declaring which role it assigns.
 *
 * <p>These are lexemes in a small handful of languages; the role they assign
 * is universal (a sememe, not a language-specific concept). A given
 * preposition can be polysemous (e.g., "with" assigns INSTRUMENT in some
 * contexts, PARTNER in others) — that's handled by alternate
 * {@link LexicalVocabulary.AssignedRole} frames or alternate sememes.
 */
public final class PrepositionVocabulary {

    private PrepositionVocabulary() {}

    /** "on", "to", "into" — assigns GOAL. */
    @Item(key = On.KEY)
    public static final class On {
        public static final String KEY = "cg.prep:on";
        private On() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates target or destination — assigns GOAL";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "on";

        @Frame(predicate = LexicalVocabulary.AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ItemRef.iid(ThematicRole.Goal.KEY);
    }

    /** "with", "using" — assigns INSTRUMENT. */
    @Item(key = With.KEY)
    public static final class With {
        public static final String KEY = "cg.prep:with";
        private With() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates tool or means — assigns INSTRUMENT";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "with";

        @Frame(predicate = LexicalVocabulary.AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ItemRef.iid(ThematicRole.Instrument.KEY);
    }

    /** "from" — assigns SOURCE. */
    @Item(key = From.KEY)
    public static final class From {
        public static final String KEY = "cg.prep:from";
        private From() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates origin or source — assigns SOURCE";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "from";

        @Frame(predicate = LexicalVocabulary.AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ItemRef.iid(ThematicRole.Source.KEY);
    }

    /** "for" — assigns RECIPIENT. */
    @Item(key = For.KEY)
    public static final class For {
        public static final String KEY = "cg.prep:for";
        private For() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates beneficiary or recipient — assigns RECIPIENT";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "for";

        @Frame(predicate = LexicalVocabulary.AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ItemRef.iid(ThematicRole.Recipient.KEY);
    }

    /** "between" — assigns PARTNER. */
    @Item(key = Between.KEY)
    public static final class Between {
        public static final String KEY = "cg.prep:between";
        private Between() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates companions or participants — assigns PARTNER";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "between";

        @Frame(predicate = LexicalVocabulary.AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ItemRef.iid(ThematicRole.Partner.KEY);
    }

    /** "named", "called" — assigns VALUE (a designation/label). */
    @Item(key = Named.KEY)
    public static final class Named {
        public static final String KEY = "cg.prep:named";
        private Named() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates designation or label — assigns VALUE";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "named";

        @Frame(predicate = LexicalVocabulary.AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ItemRef.iid(ThematicRole.Value.KEY);
    }
}
