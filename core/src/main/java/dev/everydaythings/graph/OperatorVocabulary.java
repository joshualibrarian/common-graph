package dev.everydaythings.graph;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * Operator sememes — mathematical, logical, comparison, and collection
 * operators. The basic building blocks of expressions.
 *
 * <h2>⚠️ PARTIAL PORT — placeholder</h2>
 * This file currently carries a representative <i>five</i> operators (Add,
 * Subtract, Multiply, Equal, And) as a reminder. The full OLD set in
 * {@code value/OperatorOld.java} has <b>20</b> operators to port:
 *
 * <ul>
 *   <li><b>Logical</b>: And, Or, Not</li>
 *   <li><b>Arithmetic</b>: Add, Subtract, Multiply, Divide, Modulo, Power,
 *       Negate</li>
 *   <li><b>Comparison</b>: Equal, NotEqual, LessThan, GreaterThan,
 *       LessOrEqual, GreaterOrEqual</li>
 *   <li><b>Collection</b>: Concat, In, Contains, Pipe</li>
 * </ul>
 *
 * <p>The OLD operators also carried parse + eval behavior (precedence,
 * fixity, {@code applyBinary}). That behavior moves to the new text pipeline
 * (task #40) and lives on dispatch handlers, not the seed sememes. The seeds
 * here are pure-data placeholders; their IIDs are stable and survive into the
 * new pipeline.
 *
 * <p>Similarly, {@code value/Function.java} has 36 functions
 * (abs/sqrt/floor/upper/map/filter/etc.) that need an analogous shallow port
 * into a {@code FunctionVocabulary} when the time is right.
 *
 * <p>The {@link ColorVocabulary.Symbol} predicate is reused to record the
 * canonical operator glyph ("+", "-", "==", etc.).
 */
public final class OperatorVocabulary {

    private OperatorVocabulary() {}

    /** Binary addition — {@code +}. */
    @Seed.Item(key = Add.KEY)
    public static final class Add {
        public static final String KEY = "cg.op:add";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Add() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "binary addition operator";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "add";

        @Frame(predicate = ColorVocabulary.Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "+";
    }

    /** Binary subtraction — {@code -}. */
    @Seed.Item(key = Subtract.KEY)
    public static final class Subtract {
        public static final String KEY = "cg.op:subtract";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Subtract() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "binary subtraction operator";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "subtract";

        @Frame(predicate = ColorVocabulary.Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "-";
    }

    /** Binary multiplication — {@code *}. */
    @Seed.Item(key = Multiply.KEY)
    public static final class Multiply {
        public static final String KEY = "cg.op:multiply";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Multiply() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "binary multiplication operator";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "multiply";

        @Frame(predicate = ColorVocabulary.Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "*";
    }

    /** Equality comparison — {@code ==}. */
    @Seed.Item(key = Equal.KEY)
    public static final class Equal {
        public static final String KEY = "cg.op:equal";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Equal() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "equality comparison operator";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "equals";

        @Frame(predicate = ColorVocabulary.Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "==";
    }

    /** Logical conjunction — {@code &&}. */
    @Seed.Item(key = And.KEY)
    public static final class And {
        public static final String KEY = "cg.op:and";
        public static final ItemID IID = ItemID.fromString(KEY);
        private And() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "logical conjunction operator";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Conjunction.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishLemma = "and";

        @Frame(predicate = ColorVocabulary.Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "&&";
    }
}
