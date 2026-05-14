package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ItemID;
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
 * associativity, arity) is the same data driving both parsing (precedence-as-weight)
 * and rendering (infix/prefix layout). All of it is data, so different deployments
 * can fork notation conventions without touching code.
 *
 * <p><b>Lexical vs semantic.</b> {@link Fixity}, {@link Precedence}, and
 * {@link Associativity} are <i>lexical</i> features — they describe how a particular
 * surface form (a {@code Lexeme}) is laid out, not anything about the predicate's
 * meaning. They live as qualifier sememes attached to {@link ThematicRole.Attribute}
 * bindings on the operator's Lexeme frame.
 *
 * <p>{@link Arity}, by contrast, is <i>semantic</i> — a fact about the predicate
 * itself (binary, ternary, etc.). It's a role sememe applied directly to the
 * predicate's manifest body via the {@code @Seed.Item.bindings} slot.
 *
 * <p>None of these are predicates. A symbol is just a Lexeme without a Language
 * qualifier — "+" as ADD's universal notation is {@code Lexeme { VALUE:[] → "+" }} —
 * and operator metadata rides on that same Lexeme frame as additional bindings.
 *
 * <p>Operator <i>code</i> (apply-binary, apply-unary) lives separately on operator
 * subclasses of {@link Item} per operator (e.g. {@code Add.java}).
 */
public final class NotationVocabulary {

    private NotationVocabulary() {}

    // ==================================================================================
    // Lexical-feature sememes — qualifier sememes for ATTRIBUTE bindings on a Lexeme
    // ==================================================================================
    //
    // These are NOT predicates. Each appears as a qualifier in a binding compound key:
    //   ATTRIBUTE[Precedence] → 10
    //   ATTRIBUTE[Associativity] → Left
    // riding on the operator-form Lexeme frame.

    /**
     * The fixity feature — describes where an operator's symbol sits relative to its
     * operands. As a qualifier on the symbol Lexeme's value-binding it picks one of
     * {@link Infix}, {@link Prefix}, {@link Postfix}, {@link Mixfix}, {@link Circumfix}.
     */
    @Seed.Item(key = Fixity.KEY)
    public static final class Fixity {
        public static final String KEY = "cg.notation:fixity";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Fixity() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the syntactic position of an operator: infix, prefix, postfix, mixfix, circumfix";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fixity";
    }

    /**
     * The precedence feature — qualifier identifying an integer ATTRIBUTE binding
     * carrying an operator's binding tightness. Higher precedence binds tighter
     * (multiplication > addition; exponentiation > multiplication).
     */
    @Seed.Item(key = Precedence.KEY)
    public static final class Precedence {
        public static final String KEY = "cg.notation:precedence";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Precedence() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the binding tightness of an operator; higher binds tighter";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "precedence";
    }

    /**
     * The associativity feature — qualifier identifying an ATTRIBUTE binding whose
     * target is one of {@link Left}, {@link Right}, {@link NonAssociative}.
     * Determines how operators of equal precedence group: {@code 5-3-1} parses as
     * {@code (5-3)-1} under left-associativity, {@code 5-(3-1)} under right.
     */
    @Seed.Item(key = Associativity.KEY)
    public static final class Associativity {
        public static final String KEY = "cg.notation:associativity";
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
    // Semantic feature sememe — role on the predicate's manifest body
    // ==================================================================================

    /**
     * The arity feature — number of operands a predicate accepts. Applied as a binding
     * directly on the predicate's manifest body via {@code @Seed.Item.bindings}:
     *
     * <pre>{@code
     * @Seed.Item(key = Add.KEY,
     *            head = Item.Predicate.KEY,
     *            bindings = {@Seed.Binding(role = Arity.KEY, integer = 2)})
     * }</pre>
     *
     * <p>Unlike fixity/precedence/associativity (which describe a particular surface
     * lexeme), arity is a semantic fact about the predicate itself.
     */
    @Seed.Item(key = Arity.KEY)
    public static final class Arity {
        public static final String KEY = "cg.notation:arity";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Arity() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the number of operands a predicate accepts";

        @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "arity";
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

    /**
     * Mixfix — operator interleaves with operands at multiple positions
     * ({@code if … then … else …}; {@code a ? b : c}). Placeholder; full mixfix
     * support requires a position template that's not yet specified.
     */
    @Seed.Item(key = Mixfix.KEY)
    public static final class Mixfix {
        public static final String KEY = "cg.fixity:mixfix";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Mixfix() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator interleaves with operands at multiple positions (e.g. if/then/else, ternary)";
    }

    /**
     * Circumfix — operator brackets its operand with matching tokens on both sides
     * ({@code |x|}, {@code ⌊x⌋}, {@code (…)}). Placeholder.
     */
    @Seed.Item(key = Circumfix.KEY)
    public static final class Circumfix {
        public static final String KEY = "cg.fixity:circumfix";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Circumfix() {}

        @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator brackets its operand with matching tokens on both sides (e.g. |x|, ⌊x⌋)";
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
