package dev.everydaythings.graph.value;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.eval.ParseContext;
import dev.everydaythings.graph.frame.eval.ParseContribution;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.LibrarianOld;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * The operator type sememe — the concept of "operator" in the meaning hierarchy.
 *
 * <p>Each concrete operator (Add, Subtract, And, etc.) is a static inner class
 * that extends Operator. Each inner class is both the sememe (the concept, with
 * glosses, symbols, precedence) AND the Java implementation (with evaluation logic).
 * The {@code @Implements} annotation on each inner class links it to the concept
 * via an IMPLEMENTED_BY frame in the graph.
 *
 * <p>This dual role is a Java convenience — on other platforms (Rust, C++), the
 * sememe data would be graph-native CBOR and the implementation would be native code.
 *
 * <p>Operator extends {@link Sememe} and carries shared infrastructure:
 * precedence, associativity, fixity, symbol accessors, type coercion helpers,
 * and quantity arithmetic utilities.
 */
@ItemSeed(key = OperatorOld.KEY)
public class OperatorOld extends Sememe {

    public static final String KEY = "cg.sememe:operator";

    public enum Associativity { LEFT, RIGHT, NONE }
    public enum Fixity { PREFIX, INFIX, POSTFIX }

    // ==================================================================================
    // CONCRETE OPERATORS — each is both sememe and implementation
    // ==================================================================================

    // --- Logical ---

    @Implements(And.KEY)
    @ItemSeed(key = And.KEY)
    public static class And extends OperatorOld {
        public static final String KEY = "cg.op:and";
        public static final ItemID IID = ItemID.fromString(KEY);

        And() { super(KEY, "&&", "and", 2, 1, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "and";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "&&";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"and"};

        @Override public boolean isShortCircuit() { return true; }
    }

    @Implements(Or.KEY)
    @ItemSeed(key = Or.KEY)
    public static class Or extends OperatorOld {
        public static final String KEY = "cg.op:or";
        public static final ItemID IID = ItemID.fromString(KEY);

        Or() { super(KEY, "||", "or", 2, 0, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "or";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "||";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"or"};

        @Override public boolean isShortCircuit() { return true; }
    }

    @Implements(Not.KEY)
    @ItemSeed(key = Not.KEY)
    public static class Not extends OperatorOld {
        public static final String KEY = "cg.op:not";
        public static final ItemID IID = ItemID.fromString(KEY);

        Not() { super(KEY, "!", "not", 1, 25, Associativity.RIGHT, Fixity.PREFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "not";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "!";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"not"};

        @Override public Object applyUnary(Object operand) {
            return !toBoolean(operand);
        }
    }

    // --- Arithmetic ---

    @Implements(Add.KEY)
    @ItemSeed(key = Add.KEY)
    public static class Add extends OperatorOld {
        public static final String KEY = "cg.op:add";
        public static final ItemID IID = ItemID.fromString(KEY);

        Add() { super(KEY, "+", "add", 2, 10, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "add";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "+";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"add"};

        @Override public Object applyBinary(Object left, Object right) {
            if (left instanceof Quantity lq && right instanceof Quantity rq)
                return quantityAdd(lq, rq);
            if (left instanceof Number l && right instanceof Number r)
                return toNumber(l) + toNumber(r);
            if (left instanceof String || right instanceof String)
                return String.valueOf(left) + String.valueOf(right);
            if (left == null || right == null)
                throw new IllegalArgumentException("Cannot add: operand is undefined (null)");
            return toNumber(left) + toNumber(right);
        }
    }

    @Implements(Subtract.KEY)
    @ItemSeed(key = Subtract.KEY)
    public static class Subtract extends OperatorOld {
        public static final String KEY = "cg.op:sub";
        public static final ItemID IID = ItemID.fromString(KEY);

        Subtract() { super(KEY, "-", "subtract", 2, 10, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "subtract";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "-";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"subtract"};

        @Override public Object applyBinary(Object left, Object right) {
            if (left instanceof Quantity lq && right instanceof Quantity rq)
                return quantitySubtract(lq, rq);
            return toNumber(left) - toNumber(right);
        }
    }

    @Implements(Multiply.KEY)
    @ItemSeed(key = Multiply.KEY)
    public static class Multiply extends OperatorOld {
        public static final String KEY = "cg.op:mul";
        public static final ItemID IID = ItemID.fromString(KEY);

        Multiply() { super(KEY, "*", "multiply", 2, 20, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "multiply";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "*";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"multiply"};

        @Override public Object applyBinary(Object left, Object right) {
            if (left instanceof Number n && right instanceof Unit u) return toQuantity(n, u);
            if (left instanceof Unit u && right instanceof Number n) return toQuantity(n, u);
            if (left instanceof Number n && right instanceof Quantity q) return scaleQuantity(q, toNumber(n));
            if (left instanceof Quantity q && right instanceof Number n) return scaleQuantity(q, toNumber(n));
            return toNumber(left) * toNumber(right);
        }
    }

    @Implements(Divide.KEY)
    @ItemSeed(key = Divide.KEY)
    public static class Divide extends OperatorOld {
        public static final String KEY = "cg.op:div";
        public static final ItemID IID = ItemID.fromString(KEY);

        Divide() { super(KEY, "/", "divide", 2, 20, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "divide";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "/";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"divide"};

        @Override public Object applyBinary(Object left, Object right) {
            if (left instanceof Quantity q && right instanceof Number n) {
                double r = toNumber(n);
                if (r == 0) throw new ArithmeticException("Division by zero");
                return scaleQuantity(q, 1.0 / r);
            }
            if (left instanceof Quantity lq && right instanceof Quantity rq)
                return quantityDivide(lq, rq);
            double r = toNumber(right);
            if (r == 0) throw new ArithmeticException("Division by zero");
            return toNumber(left) / r;
        }
    }

    @Implements(Modulo.KEY)
    @ItemSeed(key = Modulo.KEY)
    public static class Modulo extends OperatorOld {
        public static final String KEY = "cg.op:mod";
        public static final ItemID IID = ItemID.fromString(KEY);

        Modulo() { super(KEY, "%", "modulo", 2, 20, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "modulo";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "%";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"modulo"};

        @Override public Object applyBinary(Object left, Object right) {
            double r = toNumber(right);
            if (r == 0) throw new ArithmeticException("Modulo by zero");
            return toNumber(left) % r;
        }
    }

    @Implements(Power.KEY)
    @ItemSeed(key = Power.KEY)
    public static class Power extends OperatorOld {
        public static final String KEY = "cg.op:pow";
        public static final ItemID IID = ItemID.fromString(KEY);

        Power() { super(KEY, "^", "power", 2, 30, Associativity.RIGHT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "power";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "^";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"power"};

        @Override public Object applyBinary(Object left, Object right) {
            return Math.pow(toNumber(left), toNumber(right));
        }
    }

    @Implements(Negate.KEY)
    @ItemSeed(key = Negate.KEY)
    public static class Negate extends OperatorOld {
        public static final String KEY = "cg.op:neg";
        public static final ItemID IID = ItemID.fromString(KEY);

        Negate() { super(KEY, "-", "negate", 1, 25, Associativity.RIGHT, Fixity.PREFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "negate";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "-";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"negate"};

        @Override public Object applyUnary(Object operand) {
            if (operand instanceof Quantity q) return scaleQuantity(q, -1.0);
            if (operand instanceof Number n) return -n.doubleValue();
            return 0;
        }
    }

    // --- Comparison ---

    @Implements(Equal.KEY)
    @ItemSeed(key = Equal.KEY)
    public static class Equal extends OperatorOld {
        public static final String KEY = "cg.op:eq";
        public static final ItemID IID = ItemID.fromString(KEY);

        Equal() { super(KEY, "==", "equal", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "equal";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "==";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"equal"};

        @Override public Object applyBinary(Object left, Object right) { return valueEquals(left, right); }
    }

    @Implements(NotEqual.KEY)
    @ItemSeed(key = NotEqual.KEY)
    public static class NotEqual extends OperatorOld {
        public static final String KEY = "cg.op:ne";
        public static final ItemID IID = ItemID.fromString(KEY);

        NotEqual() { super(KEY, "!=", "not equal", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "not equal";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "!=";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"not equal"};

        @Override public Object applyBinary(Object left, Object right) { return !valueEquals(left, right); }
    }

    @Implements(LessThan.KEY)
    @ItemSeed(key = LessThan.KEY)
    public static class LessThan extends OperatorOld {
        public static final String KEY = "cg.op:lt";
        public static final ItemID IID = ItemID.fromString(KEY);

        LessThan() { super(KEY, "<", "less than", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "less than";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "<";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"less than"};

        @Override public Object applyBinary(Object left, Object right) { return valueCompare(left, right) < 0; }
    }

    @Implements(GreaterThan.KEY)
    @ItemSeed(key = GreaterThan.KEY)
    public static class GreaterThan extends OperatorOld {
        public static final String KEY = "cg.op:gt";
        public static final ItemID IID = ItemID.fromString(KEY);

        GreaterThan() { super(KEY, ">", "greater than", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "greater than";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = ">";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"greater than"};

        @Override public Object applyBinary(Object left, Object right) { return valueCompare(left, right) > 0; }
    }

    @Implements(LessOrEqual.KEY)
    @ItemSeed(key = LessOrEqual.KEY)
    public static class LessOrEqual extends OperatorOld {
        public static final String KEY = "cg.op:le";
        public static final ItemID IID = ItemID.fromString(KEY);

        LessOrEqual() { super(KEY, "<=", "less or equal", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "less or equal";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "<=";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"less or equal"};

        @Override public Object applyBinary(Object left, Object right) { return valueCompare(left, right) <= 0; }
    }

    @Implements(GreaterOrEqual.KEY)
    @ItemSeed(key = GreaterOrEqual.KEY)
    public static class GreaterOrEqual extends OperatorOld {
        public static final String KEY = "cg.op:ge";
        public static final ItemID IID = ItemID.fromString(KEY);

        GreaterOrEqual() { super(KEY, ">=", "greater or equal", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "greater or equal";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = ">=";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"greater or equal"};

        @Override public Object applyBinary(Object left, Object right) { return valueCompare(left, right) >= 0; }
    }

    // --- String ---

    @Implements(Concat.KEY)
    @ItemSeed(key = Concat.KEY)
    public static class Concat extends OperatorOld {
        public static final String KEY = "cg.op:concat";
        public static final ItemID IID = ItemID.fromString(KEY);

        Concat() { super(KEY, "++", "concat", 2, 10, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "concat";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "++";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"concat"};

        @Override public Object applyBinary(Object left, Object right) {
            return String.valueOf(left) + String.valueOf(right);
        }
    }

    // --- Collection ---

    @Implements(In.KEY)
    @ItemSeed(key = In.KEY)
    public static class In extends OperatorOld {
        public static final String KEY = "cg.op:in";
        public static final ItemID IID = ItemID.fromString(KEY);

        In() { super(KEY, "in", "in", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "in";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "in";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"in"};

        @Override public Object applyBinary(Object left, Object right) {
            if (right instanceof List<?> list) return list.contains(left);
            return false;
        }
    }

    @Implements(Contains.KEY)
    @ItemSeed(key = Contains.KEY)
    public static class Contains extends OperatorOld {
        public static final String KEY = "cg.op:contains";
        public static final ItemID IID = ItemID.fromString(KEY);

        Contains() { super(KEY, "contains", "contains", 2, 5, Associativity.NONE, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "contains";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "contains";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"contains"};

        @Override public Object applyBinary(Object left, Object right) {
            if (left instanceof List<?> list) return list.contains(right);
            return false;
        }
    }

    // --- Structural ---

    @Implements(Pipe.KEY)
    @ItemSeed(key = Pipe.KEY)
    public static class Pipe extends OperatorOld {
        public static final String KEY = "cg.op:pipe";
        public static final ItemID IID = ItemID.fromString(KEY);

        Pipe() { super(KEY, "|>", "pipe", 2, -10, Associativity.LEFT, Fixity.INFIX); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "pipe";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, index = true))
        static final String symbol = "|>";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"pipe"};
    }

    // ==================================================================================
    // LOOKUP TABLES
    // ==================================================================================


    // ==================================================================================
    // INSTANCE FIELDS (shared by all operators)
    // ==================================================================================

    @Getter @ItemFrame(predicate = CoreVocabulary.Arity.KEY)
    private int arity;

    @Getter @ItemFrame(predicate = CoreVocabulary.Precedence.KEY)
    private int precedence;

    @Getter @ItemFrame(predicate = CoreVocabulary.Associativity.KEY)
    private Associativity associativity;

    @Getter @ItemFrame(predicate = CoreVocabulary.Fixity.KEY)
    private Fixity fixity;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Seed constructor. */
    protected OperatorOld(String canonicalKey, String symbol, String name,
                          int arity, int precedence,
                          Associativity associativity, Fixity fixity) {
        super(canonicalKey,
                Map.of("en", name),
                Map.of(),
                List.of(symbol),
                List.of(name));
        this.arity = arity;
        this.precedence = precedence;
        this.associativity = associativity != null ? associativity : Associativity.LEFT;
        this.fixity = fixity != null ? fixity : Fixity.INFIX;
    }

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected OperatorOld(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected OperatorOld(LibrarianOld librarian, ManifestOld manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // PARSING CONTRIBUTION — declares how this operator participates in parsing
    // ==================================================================================

    /**
     * Declare this operator's parsing metadata: fixity, precedence, associativity.
     *
     * <p>The expression language parser reads this to handle precedence climbing.
     * This replaces JavaRuntime's need to introspect operator fields directly —
     * the operator self-declares how it should be parsed.
     */
    public ParseContribution contribute(ParseContext context) {
        return ParseContribution.builder()
                .fixity(mapFixity(fixity))
                .precedence(precedence)
                .associativity(mapAssociativity(associativity))
                .build();
    }

    private static ParseContribution.Fixity mapFixity(Fixity f) {
        if (f == null) return ParseContribution.Fixity.INFIX;
        return switch (f) {
            case PREFIX -> ParseContribution.Fixity.PREFIX;
            case INFIX -> ParseContribution.Fixity.INFIX;
            case POSTFIX -> ParseContribution.Fixity.POSTFIX;
        };
    }

    private static ParseContribution.Associativity mapAssociativity(Associativity a) {
        if (a == null) return ParseContribution.Associativity.LEFT;
        return switch (a) {
            case LEFT -> ParseContribution.Associativity.LEFT;
            case RIGHT -> ParseContribution.Associativity.RIGHT;
            case NONE -> ParseContribution.Associativity.NONE;
        };
    }

    // ==================================================================================
    // EVALUATION — overridden by concrete operators
    // ==================================================================================

    /**
     * Apply this binary operator to two resolved operand values.
     * Override in concrete operators.
     */
    public Object applyBinary(Object left, Object right) {
        throw new UnsupportedOperationException(
                "No binary evaluation for operator: " + canonicalKey());
    }

    /**
     * Apply this unary operator to a resolved operand value.
     * Override in concrete operators.
     */
    public Object applyUnary(Object operand) {
        throw new UnsupportedOperationException(
                "No unary evaluation for operator: " + canonicalKey());
    }

    /**
     * Whether this operator uses short-circuit evaluation.
     * Override in AND and OR.
     */
    public boolean isShortCircuit() {
        return false;
    }

    // ==================================================================================
    // CONVENIENCE ACCESSORS
    // ==================================================================================

    public String symbol() {
        List<String> syms = symbols();
        return (syms != null && !syms.isEmpty()) ? syms.getFirst() : null;
    }

    public String name() {
        List<String> toks = tokens();
        return (toks != null && !toks.isEmpty()) ? toks.getFirst() : displayToken();
    }

    public boolean isPrefix() { return fixity == Fixity.PREFIX; }
    public boolean isInfix() { return fixity == Fixity.INFIX; }

    // ==================================================================================
    // TYPE COERCION AND HELPERS (shared by all operators)
    // ==================================================================================

    public static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    public static double toNumber(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    public static boolean valueEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) return toNumber(a) == toNumber(b);
        return a.equals(b);
    }

    @SuppressWarnings("unchecked")
    public static int valueCompare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) return Double.compare(toNumber(a), toNumber(b));
        if (a instanceof Comparable c && b != null) {
            try { return c.compareTo(b); }
            catch (ClassCastException e) { return 0; }
        }
        return 0;
    }

    // ==================================================================================
    // QUANTITY ARITHMETIC (shared utilities)
    // ==================================================================================

    protected static Quantity toQuantity(Number n, Unit u) {
        return Quantity.of(decimalFrom(toNumber(n)), u.iid());
    }

    protected static Quantity scaleQuantity(Quantity q, double factor) {
        return Quantity.of(decimalFrom(q.value().toDouble() * factor), q.unit());
    }

    protected static Quantity quantityAdd(Quantity left, Quantity right) {
        Unit lu = Unit.lookupSeed(left.unit());
        Unit ru = Unit.lookupSeed(right.unit());
        if (lu == null || ru == null) throw new ArithmeticException("Unknown unit in quantity arithmetic");
        if (!lu.isCompatibleWith(ru)) throw new ArithmeticException("Cannot add incompatible units");
        double converted = ru.convert(right.value().toDouble(), lu);
        return Quantity.of(decimalFrom(left.value().toDouble() + converted), left.unit());
    }

    protected static Quantity quantitySubtract(Quantity left, Quantity right) {
        Unit lu = Unit.lookupSeed(left.unit());
        Unit ru = Unit.lookupSeed(right.unit());
        if (lu == null || ru == null) throw new ArithmeticException("Unknown unit in quantity arithmetic");
        if (!lu.isCompatibleWith(ru)) throw new ArithmeticException("Cannot subtract incompatible units");
        double converted = ru.convert(right.value().toDouble(), lu);
        return Quantity.of(decimalFrom(left.value().toDouble() - converted), left.unit());
    }

    protected static double quantityDivide(Quantity left, Quantity right) {
        Unit lu = Unit.lookupSeed(left.unit());
        Unit ru = Unit.lookupSeed(right.unit());
        if (lu == null || ru == null) throw new ArithmeticException("Unknown unit in quantity arithmetic");
        if (!lu.isCompatibleWith(ru)) throw new ArithmeticException("Cannot divide incompatible units");
        double converted = ru.convert(right.value().toDouble(), lu);
        if (converted == 0) throw new ArithmeticException("Division by zero");
        return left.value().toDouble() / converted;
    }

    private static Decimal decimalFrom(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return Decimal.ofLong((long) d);
        return Decimal.parse(String.valueOf(d));
    }


    @Override
    public String toString() {
        return symbol() + " (" + name() + ")";
    }
}
