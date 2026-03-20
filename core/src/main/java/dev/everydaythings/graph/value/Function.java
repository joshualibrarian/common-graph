package dev.everydaythings.graph.value;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The function type sememe — the concept of "function" in the meaning hierarchy.
 *
 * <p>Each concrete function (abs, sqrt, format, etc.) is a static inner class
 * that extends Function. Each inner class is both the sememe (the concept, with
 * glosses, symbols, arity) AND the Java implementation (with evaluation logic).
 * The {@code @Implements} annotation on each inner class links it to the concept
 * via an IMPLEMENTED_BY frame in the graph.
 *
 * <p>This dual role is a Java convenience — on other platforms (Rust, C++), the
 * sememe data would be graph-native CBOR and the implementation would be native code.
 *
 * <p>Function extends {@link Sememe} and carries shared infrastructure:
 * arity, category, name accessors, and the lookup tables.
 */
@ItemSeed(key = Function.KEY)
public class Function extends Sememe {

    public static final String KEY = "cg.sememe:function";

    // ==================================================================================
    // INSTANCE FIELDS (shared by all functions)
    // ==================================================================================

    @Getter @ItemFrame(predicate = CoreVocabulary.Arity.KEY)
    private int minArity;

    @Getter @ItemFrame(predicate = CoreVocabulary.Bounds.KEY)
    private int maxArity;  // -1 for variadic

    @Getter @ItemFrame(predicate = CoreVocabulary.Category.KEY)
    private String category;

    // ==================================================================================
    // CATEGORIES
    // ==================================================================================

    public static final String MATH = "math";
    public static final String STRING = "string";
    public static final String COLLECTION = "collection";
    public static final String COERCION = "coercion";
    public static final String UTILITY = "utility";
    public static final String TIME = "time";

    // ==================================================================================
    // CONCRETE FUNCTIONS — each is both sememe and implementation
    // ==================================================================================

    // --- Math ---

    @Implements(Abs.KEY) @ItemSeed(key = Abs.KEY)
    public static class Abs extends Function {
        public static final String KEY = "cg.fn:abs";
        public static final ItemID IID = ItemID.fromString(KEY);
        Abs() { super(KEY, "abs", "compute the absolute value of a number", 1, 1, MATH); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "compute the absolute value of a number";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "abs";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"abs"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.abs(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Ceil.KEY) @ItemSeed(key = Ceil.KEY)
    public static class Ceil extends Function {
        public static final String KEY = "cg.fn:ceil";
        public static final ItemID IID = ItemID.fromString(KEY);
        Ceil() { super(KEY, "ceil", "round up to the nearest integer", 1, 1, MATH); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "round up to the nearest integer";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "ceil";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"ceil"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.ceil(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Floor.KEY) @ItemSeed(key = Floor.KEY)
    public static class Floor extends Function {
        public static final String KEY = "cg.fn:floor";
        public static final ItemID IID = ItemID.fromString(KEY);
        Floor() { super(KEY, "floor", "round down to the nearest integer", 1, 1, MATH); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "round down to the nearest integer";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "floor";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"floor"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.floor(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Round.KEY) @ItemSeed(key = Round.KEY)
    public static class Round extends Function {
        public static final String KEY = "cg.fn:round";
        public static final ItemID IID = ItemID.fromString(KEY);
        Round() { super(KEY, "round", "round to the nearest integer", 1, 1, MATH); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "round to the nearest integer";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "round";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"round"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.round(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Sqrt.KEY) @ItemSeed(key = Sqrt.KEY)
    public static class Sqrt extends Function {
        public static final String KEY = "cg.fn:sqrt";
        public static final ItemID IID = ItemID.fromString(KEY);
        Sqrt() { super(KEY, "sqrt", "compute the positive square root", 1, 1, MATH, "square root"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "compute the positive square root";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "sqrt";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"sqrt", "square root"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.sqrt(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Pow.KEY) @ItemSeed(key = Pow.KEY)
    public static class Pow extends Function {
        public static final String KEY = "cg.fn:pow";
        public static final ItemID IID = ItemID.fromString(KEY);
        Pow() { super(KEY, "pow", "raise to a power", 2, 2, MATH, "power", "exponent"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "raise to a power";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "pow";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"pow", "power", "exponent"};

        @Override public Object apply(List<Object> args) { return args.size() < 2 ? 0 : Math.pow(((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue()); }
    }

    @Implements(Log.KEY) @ItemSeed(key = Log.KEY)
    public static class Log extends Function {
        public static final String KEY = "cg.fn:log";
        public static final ItemID IID = ItemID.fromString(KEY);
        Log() { super(KEY, "log", "compute the natural logarithm", 1, 1, MATH, "logarithm"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "compute the natural logarithm";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "log";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"log", "logarithm"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.log(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Sin.KEY) @ItemSeed(key = Sin.KEY)
    public static class Sin extends Function {
        public static final String KEY = "cg.fn:sin";
        public static final ItemID IID = ItemID.fromString(KEY);
        Sin() { super(KEY, "sin", "compute the sine", 1, 1, MATH, "sine"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "compute the sine";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "sin";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"sin", "sine"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.sin(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Cos.KEY) @ItemSeed(key = Cos.KEY)
    public static class Cos extends Function {
        public static final String KEY = "cg.fn:cos";
        public static final ItemID IID = ItemID.fromString(KEY);
        Cos() { super(KEY, "cos", "compute the cosine", 1, 1, MATH, "cosine"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "compute the cosine";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "cos";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"cos", "cosine"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.cos(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Tan.KEY) @ItemSeed(key = Tan.KEY)
    public static class Tan extends Function {
        public static final String KEY = "cg.fn:tan";
        public static final ItemID IID = ItemID.fromString(KEY);
        Tan() { super(KEY, "tan", "compute the tangent", 1, 1, MATH, "tangent"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "compute the tangent";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "tan";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"tan", "tangent"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? 0 : Math.tan(((Number) args.getFirst()).doubleValue()); }
    }

    @Implements(Random.KEY) @ItemSeed(key = Random.KEY)
    public static class Random extends Function {
        public static final String KEY = "cg.fn:random";
        public static final ItemID IID = ItemID.fromString(KEY);
        Random() { super(KEY, "random", "generate a random number between 0 and 1", 0, 0, MATH, "rand"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "generate a random number between 0 and 1";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "random";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"random", "rand"};

        @Override public Object apply(List<Object> args) { return Math.random(); }
    }

    // --- Type Coercion ---

    @Implements(ToString.KEY) @ItemSeed(key = ToString.KEY)
    public static class ToString extends Function {
        public static final String KEY = "cg.fn:tostring";
        public static final ItemID IID = ItemID.fromString(KEY);
        ToString() { super(KEY, "toString", "convert a value to its string representation", 1, 1, COERCION, "str"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "convert a value to its string representation";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "toString";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"toString", "str"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? "" : String.valueOf(args.getFirst()); }
    }

    @Implements(ToNumber.KEY) @ItemSeed(key = ToNumber.KEY)
    public static class ToNumber extends Function {
        public static final String KEY = "cg.fn:tonumber";
        public static final ItemID IID = ItemID.fromString(KEY);
        ToNumber() { super(KEY, "toNumber", "convert a value to a number", 1, 1, COERCION, "num"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "convert a value to a number";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "toNumber";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"toNumber", "num"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return 0.0;
            Object arg = args.getFirst();
            if (arg instanceof Number n) return n.doubleValue();
            try { return Double.parseDouble(String.valueOf(arg)); } catch (NumberFormatException e) { return 0.0; }
        }
    }

    @Implements(ToBool.KEY) @ItemSeed(key = ToBool.KEY)
    public static class ToBool extends Function {
        public static final String KEY = "cg.fn:tobool";
        public static final ItemID IID = ItemID.fromString(KEY);
        ToBool() { super(KEY, "toBool", "convert a value to a boolean", 1, 1, COERCION, "bool"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "convert a value to a boolean";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "toBool";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"toBool", "bool"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return false;
            Object arg = args.getFirst();
            if (arg instanceof Boolean b) return b;
            if (arg instanceof Number n) return n.doubleValue() != 0;
            if (arg instanceof String s) return !s.isEmpty();
            return arg != null;
        }
    }

    // --- String ---

    @Implements(Upper.KEY) @ItemSeed(key = Upper.KEY)
    public static class Upper extends Function {
        public static final String KEY = "cg.fn:upper";
        public static final ItemID IID = ItemID.fromString(KEY);
        Upper() { super(KEY, "upper", "convert to uppercase", 1, 1, STRING, "uppercase"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "convert to uppercase";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "upper";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"upper", "uppercase"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? "" : String.valueOf(args.getFirst()).toUpperCase(); }
    }

    @Implements(Lower.KEY) @ItemSeed(key = Lower.KEY)
    public static class Lower extends Function {
        public static final String KEY = "cg.fn:lower";
        public static final ItemID IID = ItemID.fromString(KEY);
        Lower() { super(KEY, "lower", "convert to lowercase", 1, 1, STRING, "lowercase"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "convert to lowercase";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "lower";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"lower", "lowercase"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? "" : String.valueOf(args.getFirst()).toLowerCase(); }
    }

    @Implements(Trim.KEY) @ItemSeed(key = Trim.KEY)
    public static class Trim extends Function {
        public static final String KEY = "cg.fn:trim";
        public static final ItemID IID = ItemID.fromString(KEY);
        Trim() { super(KEY, "trim", "remove leading and trailing whitespace", 1, 1, STRING); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "remove leading and trailing whitespace";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "trim";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"trim"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? "" : String.valueOf(args.getFirst()).trim(); }
    }

    @Implements(Length.KEY) @ItemSeed(key = Length.KEY)
    public static class Length extends Function {
        public static final String KEY = "cg.fn:length";
        public static final ItemID IID = ItemID.fromString(KEY);
        Length() { super(KEY, "length", "get the length of a string or collection", 1, 1, STRING, "len"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "get the length of a string or collection";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "length";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"length", "len"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return 0;
            Object arg = args.getFirst();
            if (arg instanceof String s) return s.length();
            if (arg instanceof List<?> l) return l.size();
            return 0;
        }
    }

    @Implements(Substring.KEY) @ItemSeed(key = Substring.KEY)
    public static class Substring extends Function {
        public static final String KEY = "cg.fn:substring";
        public static final ItemID IID = ItemID.fromString(KEY);
        Substring() { super(KEY, "substring", "extract a portion of a string", 2, 3, STRING, "substr"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "extract a portion of a string";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "substring";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"substring", "substr"};

        @Override public Object apply(List<Object> args) {
            if (args.size() < 2) return "";
            String s = String.valueOf(args.get(0));
            int start = ((Number) args.get(1)).intValue();
            if (args.size() >= 3) {
                int end = ((Number) args.get(2)).intValue();
                return s.substring(Math.max(0, start), Math.min(s.length(), end));
            }
            return s.substring(Math.max(0, start));
        }
    }

    @Implements(Split.KEY) @ItemSeed(key = Split.KEY)
    public static class Split extends Function {
        public static final String KEY = "cg.fn:split";
        public static final ItemID IID = ItemID.fromString(KEY);
        Split() { super(KEY, "split", "split a string by delimiter", 2, 2, STRING); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "split a string by delimiter";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "split";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"split"};

        @Override public Object apply(List<Object> args) {
            if (args.size() < 2) return List.of();
            return List.of(String.valueOf(args.get(0)).split(String.valueOf(args.get(1))));
        }
    }

    @Implements(Join.KEY) @ItemSeed(key = Join.KEY)
    public static class Join extends Function {
        public static final String KEY = "cg.fn:join";
        public static final ItemID IID = ItemID.fromString(KEY);
        Join() { super(KEY, "join", "join a list into a string with delimiter", 2, 2, STRING); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "join a list into a string with delimiter";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "join";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"join"};

        @Override public Object apply(List<Object> args) {
            if (args.size() < 2) return "";
            Object list = args.get(0);
            String delim = String.valueOf(args.get(1));
            if (list instanceof List<?> l) return l.stream().map(String::valueOf).collect(Collectors.joining(delim));
            return "";
        }
    }

    @Implements(Format.KEY) @ItemSeed(key = Format.KEY)
    public static class Format extends Function {
        public static final String KEY = "cg.fn:format";
        public static final ItemID IID = ItemID.fromString(KEY);
        Format() { super(KEY, "format", "format a string with arguments", 1, -1, STRING); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "format a string with arguments";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "format";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"format"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return "";
            return String.format(String.valueOf(args.getFirst()), args.subList(1, args.size()).toArray());
        }
    }

    // --- Collection ---

    @Implements(MapFn.KEY) @ItemSeed(key = MapFn.KEY)
    public static class MapFn extends Function {
        public static final String KEY = "cg.fn:map";
        public static final ItemID IID = ItemID.fromString(KEY);
        MapFn() { super(KEY, "map", "apply a function to each element", 2, 2, COLLECTION); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "apply a function to each element";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "map";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"map"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? List.of() : args.getFirst(); } // TODO: lambda support
    }

    @Implements(Filter.KEY) @ItemSeed(key = Filter.KEY)
    public static class Filter extends Function {
        public static final String KEY = "cg.fn:filter";
        public static final ItemID IID = ItemID.fromString(KEY);
        Filter() { super(KEY, "filter", "keep elements matching a predicate", 2, 2, COLLECTION); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "keep elements matching a predicate";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "filter";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"filter"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() ? List.of() : args.getFirst(); } // TODO: lambda support
    }

    @Implements(Reduce.KEY) @ItemSeed(key = Reduce.KEY)
    public static class Reduce extends Function {
        public static final String KEY = "cg.fn:reduce";
        public static final ItemID IID = ItemID.fromString(KEY);
        Reduce() { super(KEY, "reduce", "fold a collection into a single value", 3, 3, COLLECTION, "fold"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "fold a collection into a single value";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "reduce";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"reduce", "fold"};

        @Override public Object apply(List<Object> args) { return args.size() < 2 ? null : args.get(1); } // TODO: lambda support
    }

    @Implements(Range.KEY) @ItemSeed(key = Range.KEY)
    public static class Range extends Function {
        public static final String KEY = "cg.fn:range";
        public static final ItemID IID = ItemID.fromString(KEY);
        Range() { super(KEY, "range", "generate a sequence of integers", 1, 3, COLLECTION); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "generate a sequence of integers";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "range";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"range"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return List.of();
            int start = 0, end, step = 1;
            if (args.size() == 1) { end = ((Number) args.get(0)).intValue(); }
            else { start = ((Number) args.get(0)).intValue(); end = ((Number) args.get(1)).intValue(); if (args.size() >= 3) step = ((Number) args.get(2)).intValue(); }
            List<Integer> result = new ArrayList<>();
            for (int i = start; step > 0 ? i < end : i > end; i += step) result.add(i);
            return result;
        }
    }

    @Implements(Reverse.KEY) @ItemSeed(key = Reverse.KEY)
    public static class Reverse extends Function {
        public static final String KEY = "cg.fn:reverse";
        public static final ItemID IID = ItemID.fromString(KEY);
        Reverse() { super(KEY, "reverse", "reverse a list or string", 1, 1, COLLECTION); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "reverse a list or string";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "reverse";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"reverse"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return List.of();
            Object arg = args.getFirst();
            if (arg instanceof List<?> list) { List<Object> r = new ArrayList<>(list); Collections.reverse(r); return r; }
            if (arg instanceof String s) return new StringBuilder(s).reverse().toString();
            return arg;
        }
    }

    @Implements(Sort.KEY) @ItemSeed(key = Sort.KEY)
    public static class Sort extends Function {
        public static final String KEY = "cg.fn:sort";
        public static final ItemID IID = ItemID.fromString(KEY);
        Sort() { super(KEY, "sort", "sort a list", 1, 1, COLLECTION); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "sort a list";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "sort";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"sort"};

        @Override @SuppressWarnings("unchecked") public Object apply(List<Object> args) {
            if (args.isEmpty()) return List.of();
            Object arg = args.getFirst();
            if (arg instanceof List<?> list) { List<Object> s = new ArrayList<>(list); s.sort((a, b) -> { if (a instanceof Comparable c && b != null) { try { return c.compareTo(b); } catch (ClassCastException e) { return 0; } } return 0; }); return s; }
            return arg;
        }
    }

    @Implements(Unique.KEY) @ItemSeed(key = Unique.KEY)
    public static class Unique extends Function {
        public static final String KEY = "cg.fn:unique";
        public static final ItemID IID = ItemID.fromString(KEY);
        Unique() { super(KEY, "unique", "remove duplicates from a list", 1, 1, COLLECTION, "distinct"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "remove duplicates from a list";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "unique";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"unique", "distinct"};

        @Override public Object apply(List<Object> args) { if (args.isEmpty()) return List.of(); Object a = args.getFirst(); return a instanceof List<?> l ? l.stream().distinct().toList() : a; }
    }

    @Implements(Flatten.KEY) @ItemSeed(key = Flatten.KEY)
    public static class Flatten extends Function {
        public static final String KEY = "cg.fn:flatten";
        public static final ItemID IID = ItemID.fromString(KEY);
        Flatten() { super(KEY, "flatten", "flatten nested lists into a single list", 1, 1, COLLECTION); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "flatten nested lists into a single list";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "flatten";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"flatten"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return List.of();
            Object arg = args.getFirst();
            if (arg instanceof List<?> list) { List<Object> flat = new ArrayList<>(); for (Object item : list) { if (item instanceof List<?> inner) flat.addAll(inner); else flat.add(item); } return flat; }
            return arg;
        }
    }

    // --- Utility ---

    @Implements(Typeof.KEY) @ItemSeed(key = Typeof.KEY)
    public static class Typeof extends Function {
        public static final String KEY = "cg.fn:typeof";
        public static final ItemID IID = ItemID.fromString(KEY);
        Typeof() { super(KEY, "typeof", "get the type name of a value", 1, 1, UTILITY, "type"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "get the type name of a value";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "typeof";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"typeof", "type"};

        @Override public Object apply(List<Object> args) {
            if (args.isEmpty()) return "null";
            Object a = args.getFirst();
            if (a == null) return "null";
            if (a instanceof Number) return "number";
            if (a instanceof String) return "string";
            if (a instanceof Boolean) return "boolean";
            if (a instanceof List) return "list";
            if (a instanceof ItemID) return "item";
            return a.getClass().getSimpleName().toLowerCase();
        }
    }

    @Implements(IsNull.KEY) @ItemSeed(key = IsNull.KEY)
    public static class IsNull extends Function {
        public static final String KEY = "cg.fn:isnull";
        public static final ItemID IID = ItemID.fromString(KEY);
        IsNull() { super(KEY, "isNull", "check whether a value is null", 1, 1, UTILITY, "null?"); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "check whether a value is null";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "isNull";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"isNull", "null?"};

        @Override public Object apply(List<Object> args) { return args.isEmpty() || args.getFirst() == null; }
    }

    @Implements(Coalesce.KEY) @ItemSeed(key = Coalesce.KEY)
    public static class Coalesce extends Function {
        public static final String KEY = "cg.fn:coalesce";
        public static final ItemID IID = ItemID.fromString(KEY);
        Coalesce() { super(KEY, "coalesce", "return the first non-null argument", 1, -1, UTILITY); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "return the first non-null argument";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "coalesce";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"coalesce"};

        @Override public Object apply(List<Object> args) { return args.stream().filter(a -> a != null).findFirst().orElse(null); }
    }

    @Implements(Default.KEY) @ItemSeed(key = Default.KEY)
    public static class Default extends Function {
        public static final String KEY = "cg.fn:default";
        public static final ItemID IID = ItemID.fromString(KEY);
        Default() { super(KEY, "default", "return a default value if the first is null", 2, 2, UTILITY); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "return a default value if the first is null";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "default";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"default"};

        @Override public Object apply(List<Object> args) { if (args.size() < 2) return null; return args.get(0) != null ? args.get(0) : args.get(1); }
    }

    // --- Time ---

    @Implements(Now.KEY) @ItemSeed(key = Now.KEY)
    public static class Now extends Function {
        public static final String KEY = "cg.fn:now";
        public static final ItemID IID = ItemID.fromString(KEY);
        Now() { super(KEY, "now", "current time in milliseconds", 0, 0, TIME); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "current time in milliseconds";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "now";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"now"};

        @Override public Object apply(List<Object> args) { return System.currentTimeMillis(); }
    }

    @Implements(Timestamp.KEY) @ItemSeed(key = Timestamp.KEY)
    public static class Timestamp extends Function {
        public static final String KEY = "cg.fn:timestamp";
        public static final ItemID IID = ItemID.fromString(KEY);
        Timestamp() { super(KEY, "timestamp", "current time in seconds since epoch", 0, 0, TIME); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "current time in seconds since epoch";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "timestamp";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"timestamp"};

        @Override public Object apply(List<Object> args) { return System.currentTimeMillis() / 1000; }
    }

    // ==================================================================================
    // LOOKUP (lazy holder to avoid circular static init)
    // ==================================================================================

    // Temporary lookup tables — used by the parser/lexer to resolve function names to IIDs.
    // These will be replaced by TokenDictionary lookups once the lexer is refactored.
    private static class Seeds {
        static final List<Function> ALL = List.of(
                new Abs(), new Ceil(), new Floor(), new Round(), new Sqrt(),
                new Pow(), new Log(), new Sin(), new Cos(), new Tan(), new Random(),
                new ToString(), new ToNumber(), new ToBool(),
                new Upper(), new Lower(), new Trim(), new Length(), new Substring(),
                new Split(), new Join(), new Format(),
                new MapFn(), new Filter(), new Reduce(), new Range(), new Reverse(),
                new Sort(), new Unique(), new Flatten(),
                new Typeof(), new IsNull(), new Coalesce(), new Default(),
                new Now(), new Timestamp()
        );
        static final Map<String, Function> BY_NAME = buildByName();
        static final Map<ItemID, Function> BY_ID;
        static {
            Map<ItemID, Function> byId = new LinkedHashMap<>();
            for (Function f : ALL) byId.put(f.iid(), f);
            BY_ID = Map.copyOf(byId);
        }
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Seed constructor — name and aliases map to Sememe's symbols and tokens. */
    protected Function(String canonicalKey, String name, String gloss,
                       int minArity, int maxArity, String category,
                       String... aliases) {
        super(canonicalKey,
                Map.of("en", gloss),
                Map.of(),
                List.of(name),
                List.of(aliases));
        this.minArity = minArity;
        this.maxArity = maxArity;
        this.category = category;
    }

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected Function(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected Function(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // EVALUATION — overridden by concrete functions
    // ==================================================================================

    /**
     * Apply this function to the given arguments.
     * Override in concrete functions.
     */
    public Object apply(List<Object> args) {
        throw new UnsupportedOperationException(
                "No evaluator for function: " + canonicalKey());
    }

    /**
     * Evaluate this function (delegates to apply).
     * Called by JavaRuntime's function wrapper.
     */
    public Object evaluate(List<Object> args) {
        return apply(args);
    }

    // ==================================================================================
    // CONVENIENCE ACCESSORS
    // ==================================================================================

    public String name() {
        List<String> syms = symbols();
        return (syms != null && !syms.isEmpty()) ? syms.getFirst() : displayToken();
    }

    public boolean isVariadic() {
        return maxArity < 0;
    }

    // ==================================================================================
    // LOOKUP
    // ==================================================================================

    public static Function lookupByName(String name) {
        if (name == null) return null;
        return Seeds.BY_NAME.get(name.toLowerCase());
    }

    public static Function lookupKnown(ItemID iid) {
        if (iid == null) return null;
        return Seeds.BY_ID.get(iid);
    }

    public static List<Function> seeds() {
        return Seeds.ALL;
    }

    // ==================================================================================
    // STATIC BUILDERS
    // ==================================================================================

    private static Map<String, Function> buildByName() {
        Map<String, Function> out = new LinkedHashMap<>();
        for (Function f : Seeds.ALL) {
            String name = f.name();
            if (name != null) out.put(name.toLowerCase(), f);
            List<String> toks = f.tokens();
            if (toks != null) {
                for (String alias : toks) {
                    if (alias != null) out.put(alias.toLowerCase(), f);
                }
            }
        }
        return Map.copyOf(out);
    }

    @Override
    public String toString() {
        return name() + "(" + (isVariadic() ? "..." : minArity == maxArity
                ? String.valueOf(minArity) : minArity + "-" + maxArity) + ")";
    }
}
