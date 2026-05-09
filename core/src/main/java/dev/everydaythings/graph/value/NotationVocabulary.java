package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;

/**
 * Sememes for notation metadata — the universal pre-linguistic vocabulary describing
 * how concepts are written, parsed, and rendered.
 *
 * <p>Notation lives below natural language. The same operator (e.g. ADD) renders as
 * {@code 5 + 5} regardless of whether the surrounding language is English, German,
 * Japanese, or any other. Operator metadata (symbol, fixity, precedence,
 * associativity) is the same data driving both parsing (precedence-as-weight) and
 * rendering (infix/prefix layout). All of it is data — frames on the operator —
 * so different deployments can fork notation conventions without touching code.
 *
 * <p>This file consolidates the metadata predicates and value sememes. Each inner
 * class is a pure-data seed: a {@code KEY}/{@code IID} holder with optional gloss
 * and lexeme {@code @Seed.Frame} declarations. Operator <i>code</i> (apply-binary,
 * apply-unary) lives separately on operator subclasses of {@link Item} per
 * operator (e.g. {@code Add.java}).
 */
public final class NotationVocabulary {

    private NotationVocabulary() {}

    // ==================================================================================
    // Metadata predicates — predicates whose frames declare facts ABOUT a sememe
    // ==================================================================================
    //
    // NOTE: There is no separate "Symbol" predicate. A symbol is just a Lexeme without
    // a Language qualifier — "+" as ADD's universal notation is Lexeme { VALUE:[] → "+" }.
    // The TokenDictionary auto-indexes both Lexeme and language-scoped lexemes the same
    // way; render lookup filters by qualifier presence (universal vs language-scoped).

    /**
     * The fixity predicate. A {@code Fixity} frame says: "this operator's surface
     * form is infix / prefix / postfix." Target is a {@link Infix}/{@link Prefix}/
     * {@link Postfix} sememe.
     */
    @Seed.Item(key = Fixity.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
    public static final class Fixity {
        public static final String KEY = "cg.predicate:fixity";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Fixity() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the syntactic position of an operator: infix, prefix, or postfix";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fixity";
    }

    /**
     * The precedence predicate. {@code Precedence { THEME → ADD, VALUE → 10 }}.
     * Higher numeric precedence = tighter binding (multiplication > addition).
     * Target is an integer literal.
     */
    @Seed.Item(key = Precedence.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
    public static final class Precedence {
        public static final String KEY = "cg.predicate:precedence";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Precedence() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the binding tightness of an operator; higher binds tighter";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "precedence";
    }

    /**
     * The associativity predicate. {@code Associativity { THEME → SUBTRACT, VALUE → Left }}.
     * Determines how operators of equal precedence group: {@code 5-3-1} parses as
     * {@code (5-3)-1} under left-associativity, {@code 5-(3-1)} under right.
     */
    @Seed.Item(key = Associativity.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
    public static final class Associativity {
        public static final String KEY = "cg.predicate:associativity";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Associativity() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how operators of equal precedence group: left, right, or none";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "associativity";
    }

    // ==================================================================================
    // Fixity values
    // ==================================================================================

    /** Infix — operator appears between operands ({@code 5 + 3}). */
    @Seed.Item(key = Infix.KEY)
    public static final class Infix {
        public static final String KEY = "cg.fixity:infix";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Infix() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears between operands";
    }

    /** Prefix — operator appears before operand ({@code -5}, {@code !x}). */
    @Seed.Item(key = Prefix.KEY)
    public static final class Prefix {
        public static final String KEY = "cg.fixity:prefix";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Prefix() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears before its operand";
    }

    /** Postfix — operator appears after operand ({@code n!}, {@code x++}). */
    @Seed.Item(key = Postfix.KEY)
    public static final class Postfix {
        public static final String KEY = "cg.fixity:postfix";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Postfix() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears after its operand";
    }

    // ==================================================================================
    // Associativity values
    // ==================================================================================

    /** Left-associative: {@code 5-3-1} parses as {@code (5-3)-1}. Most arithmetic operators. */
    @Seed.Item(key = Left.KEY)
    public static final class Left {
        public static final String KEY = "cg.associativity:left";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Left() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "left-to-right grouping for equal-precedence operators";
    }

    /** Right-associative: {@code 2^3^2} parses as {@code 2^(3^2)}. Exponentiation, assignment. */
    @Seed.Item(key = Right.KEY)
    public static final class Right {
        public static final String KEY = "cg.associativity:right";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Right() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "right-to-left grouping for equal-precedence operators";
    }

    /** Non-associative: chaining requires explicit parentheses. Comparison operators in some languages. */
    @Seed.Item(key = NonAssociative.KEY)
    public static final class NonAssociative {
        public static final String KEY = "cg.associativity:none";
        public static final ItemID IID = ItemID.fromString(KEY);
        private NonAssociative() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "no grouping for equal-precedence operators; require parentheses";
    }
}
