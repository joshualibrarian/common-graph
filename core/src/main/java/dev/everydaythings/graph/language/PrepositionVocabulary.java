package dev.everydaythings.graph.language;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.id.ItemRef;

import static dev.everydaythings.graph.Seed.*;

/**
 * Preposition sememes — function words whose job is to <em>assign a thematic
 * role</em> to the noun phrase that follows them.
 *
 * <p>In "create chess on myItem", the preposition "on" tells the parser that
 * "myItem" fills the GOAL role of the CREATE frame. Each preposition carries
 * an {@link AssignedRole} frame declaring which role it assigns.
 *
 * <p>These are lexemes in a small handful of languages; the role they assign
 * is universal (a sememe, not a language-specific concept). A given
 * preposition can be polysemous (e.g., "with" assigns INSTRUMENT in some
 * contexts, PARTNER in others) — that's handled by alternate {@link AssignedRole}
 * frames or alternate sememes.
 */
public final class PrepositionVocabulary {

    private PrepositionVocabulary() {}

    /**
     * The ASSIGNED_ROLE predicate — frames on a preposition declaring which
     * thematic role the preposition assigns to its object.
     *
     * <p>Used at parse time: when a preposition is recognized in input, the
     * parser looks up its ASSIGNED_ROLE frame to know which slot the following
     * noun phrase fills in the surrounding frame.
     */
    @Item(key = AssignedRole.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class AssignedRole {
        public static final String KEY = "cg.predicate:assigned-role";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private AssignedRole() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "declares which thematic role a preposition (or similar function "
                        + "word) assigns to its object";
    }

    /** "on", "to", "into" — assigns GOAL. */
    @Item(key = On.KEY)
    public static final class On {
        public static final String KEY = "cg.prep:on";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private On() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates target or destination — assigns GOAL";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "on";

        @Frame(predicate = AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ThematicRole.Goal.IID;
    }

    /** "with", "using" — assigns INSTRUMENT. */
    @Item(key = With.KEY)
    public static final class With {
        public static final String KEY = "cg.prep:with";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private With() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates tool or means — assigns INSTRUMENT";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "with";

        @Frame(predicate = AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ThematicRole.Instrument.IID;
    }

    /** "from" — assigns SOURCE. */
    @Item(key = From.KEY)
    public static final class From {
        public static final String KEY = "cg.prep:from";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private From() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates origin or source — assigns SOURCE";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "from";

        @Frame(predicate = AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ThematicRole.Source.IID;
    }

    /** "for" — assigns RECIPIENT. */
    @Item(key = For.KEY)
    public static final class For {
        public static final String KEY = "cg.prep:for";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private For() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates beneficiary or recipient — assigns RECIPIENT";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "for";

        @Frame(predicate = AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ThematicRole.Recipient.IID;
    }

    /** "between" — assigns PARTNER. */
    @Item(key = Between.KEY)
    public static final class Between {
        public static final String KEY = "cg.prep:between";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Between() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates companions or participants — assigns PARTNER";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "between";

        @Frame(predicate = AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ThematicRole.Partner.IID;
    }

    /** "named", "called" — assigns VALUE (a designation/label). */
    @Item(key = Named.KEY)
    public static final class Named {
        public static final String KEY = "cg.prep:named";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Named() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "indicates designation or label — assigns VALUE";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "named";

        @Frame(predicate = AssignedRole.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final ItemRef assigns = ThematicRole.Value.IID;
    }
}
