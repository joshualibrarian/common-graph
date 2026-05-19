package dev.everydaythings.graph.operator;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.ParseContext;

/**
 * Abstract base for notation-bearing predicates that evaluate to a value.
 *
 * <p>Operators (Add, Subtract, And, Equal, …) and arithmetic-style functions
 * are predicates whose frame instances carry operand bindings and whose
 * runtime form computes a result from those operands. Concrete subclasses
 * declare their notation surface (the operator-form Lexeme with fixity,
 * precedence, associativity) and override {@link #evaluate(Frame)} with the
 * computation.
 *
 * <p>The parsing and rendering of operator expressions — recognizing
 * {@code 5 + 3 * 2} as text and assembling it into nested ADD/MULTIPLY frames,
 * and emitting the text back — lives in {@link OperatorNotation}, the Language
 * that owns operator-shape recognition. Each anchored Operator sememe delegates
 * its {@code parse(ctx)} contribution there.
 *
 * <p>This class also hosts the operator-adjacent vocabulary (Fixity, Precedence,
 * Associativity, Arity, plus the value sememes Infix/Prefix/Postfix/Mixfix/
 * Circumfix and Left/Right/NonAssociative) as nested seed-items — they're the
 * metadata vocabulary every operator declaration uses to describe its surface
 * form, so they travel with the operator concept.
 */
@Seed.Item(key = Operator.KEY, head = CoreVocabulary.Predicate.KEY)
public abstract class Operator extends Item {

    /** Canonical key for the operator concept itself — the archetype of all operators. */
    public static final String KEY = "cg.sememe:operator";

    protected Operator(ItemRef iid) {
        super(iid);
    }

    protected Operator(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // ==================================================================================
    // Evaluation — the universal dispatch entrypoint.
    //
    // {@link #receive(Frame)} is final on Operator: it routes through
    // {@link #evaluate(Frame)}, the method subclasses override.  Family base
    // classes (BinaryArithmetic, BinaryComparison, BinaryLogical, ...) extract
    // and type-check the operands once and call into narrower abstract methods,
    // so concrete operators write only the math.  Irregulars (Between, Concat,
    // ...) override {@link #evaluate(Frame)} directly and use the typed-operand
    // helpers below.
    // ==================================================================================

    /**
     * Universal handler entrypoint for operators.  Final — routes to
     * {@link #evaluate(Frame)}.  Subclasses override evaluate, not this.
     */
    @Override
    public final Object receive(Frame frame) {
        return evaluate(frame);
    }

    /**
     * Compute the result of this operator against the given frame.  Default
     * returns null (no behavior); concrete operators override.  Family base
     * classes (BinaryArithmetic, BinaryComparison, BinaryLogical, etc.) make
     * this final and route to a narrower abstract method.
     */
    protected Object evaluate(Frame frame) {
        return null;
    }

    /**
     * Look up an operand from a frame body by role, returning its raw target
     * (null when no binding for the role).
     */
    protected static Object operandAt(Frame frame, String roleKey) {
        return frame.body().binding(CompoundKey.of(ItemRef.iid(roleKey)))
                .map(Binding::target)
                .orElse(null);
    }

    /** Typed {@link Number} operand by role, or null when absent / wrong type. */
    protected static Number numberAt(Frame frame, String roleKey) {
        Object t = operandAt(frame, roleKey);
        return t instanceof Number n ? n : null;
    }

    /** Typed {@link Boolean} operand by role, or null when absent / wrong type. */
    protected static Boolean boolAt(Frame frame, String roleKey) {
        Object t = operandAt(frame, roleKey);
        return t instanceof Boolean b ? b : null;
    }

    /** Typed {@link String} operand by role, or null when absent / wrong type. */
    protected static String textAt(Frame frame, String roleKey) {
        Object t = operandAt(frame, roleKey);
        return t instanceof String s ? s : null;
    }

    // ==================================================================================
    // Parse — delegates to {@link OperatorNotation}, the Language that owns
    // operator-shape recognition. This operator sememe contributes its lexeme
    // data; OperatorNotation reads it and emits the FrameMap delta.
    // ==================================================================================

    @Override
    public FrameMap parse(ParseContext ctx) {
        // Each Language consumes only the anchors it can interpret (operator-form
        // symbol position for OperatorNotation; function-name+paren for
        // FunctionNotation). If neither yields a frame, the operator wasn't
        // recognized in any active surface form here.
        //
        // Notation parseAnchor methods are instance methods (so they can call
        // their Language's recognizeOperand for locale-aware literal handling),
        // fetched here from the librarian.  In seed/siloed mode (no librarian)
        // we return empty — parse only runs at runtime.
        if (librarian() == null) return FrameMap.empty();

        OperatorNotation opNotation = librarian().fetchItem(ItemRef.iid(OperatorNotation.KEY))
                .filter(OperatorNotation.class::isInstance)
                .map(OperatorNotation.class::cast)
                .orElse(null);
        if (opNotation != null) {
            FrameMap fromOperator = opNotation.parseAnchor(this, ctx);
            if (!fromOperator.isEmpty()) return fromOperator;
        }

        FunctionNotation fnNotation = librarian().fetchItem(ItemRef.iid(FunctionNotation.KEY))
                .filter(FunctionNotation.class::isInstance)
                .map(FunctionNotation.class::cast)
                .orElse(null);
        if (fnNotation != null) {
            return fnNotation.parseAnchor(this, ctx);
        }

        return FrameMap.empty();
    }

    // ==================================================================================
    // Operator-adjacent meta sememes — universal notation features riding on operator
    // declarations.  Each is a pure-data sememe used as a qualifier (Fixity / Precedence
    // / Associativity) or as a manifest-body binding role (Arity), plus the value
    // sememes those qualifiers point at (Infix / Prefix / Postfix / Mixfix / Circumfix;
    // Left / Right / NonAssociative).
    //
    // These are not operators themselves — they're the metadata vocabulary every
    // operator declaration uses to describe its surface form.  They live here as
    // inner classes so the operator concept and the words for talking about
    // operators travel together.
    // ==================================================================================

    /**
     * The fixity feature — qualifier identifying where an operator's symbol sits
     * relative to its operands.  As a qualifier on the symbol Lexeme's value-binding
     * it picks one of {@link Infix}, {@link Prefix}, {@link Postfix}, {@link Mixfix},
     * {@link Circumfix}.
     */
    @Seed.Item(key = Fixity.KEY)
    public static final class Fixity {
        public static final String KEY = "cg.notation:fixity";
        private Fixity() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the syntactic position of an operator: infix, prefix, postfix, mixfix, circumfix";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
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
        private Precedence() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the binding tightness of an operator; higher binds tighter";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
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
        private Associativity() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how operators of equal precedence group: left, right, or none";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "associativity";
    }

    /**
     * The arity feature — number of operands a predicate accepts.  Applied as a
     * binding directly on the predicate's manifest body via {@code @Seed.Property}:
     *
     * <pre>{@code
     * @Seed.Property(role = Operator.Arity.KEY)
     * static final long arity = 2;
     * }</pre>
     *
     * <p>Unlike fixity/precedence/associativity (which describe a particular surface
     * lexeme), arity is a semantic fact about the predicate itself.
     */
    @Seed.Item(key = Arity.KEY)
    public static final class Arity {
        public static final String KEY = "cg.notation:arity";
        private Arity() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the number of operands a predicate accepts";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "arity";
    }

    // ----- Fixity values --------------------------------------------------------------

    /** Infix — operator appears between operands ({@code 5 + 3}). */
    @Seed.Item(key = Infix.KEY)
    public static final class Infix {
        public static final String KEY = "cg.fixity:infix";
        private Infix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears between operands";
    }

    /** Prefix — operator appears before operand ({@code -5}, {@code !x}). */
    @Seed.Item(key = Prefix.KEY)
    public static final class Prefix {
        public static final String KEY = "cg.fixity:prefix";
        private Prefix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears before its operand";
    }

    /** Postfix — operator appears after operand ({@code n!}, {@code x++}). */
    @Seed.Item(key = Postfix.KEY)
    public static final class Postfix {
        public static final String KEY = "cg.fixity:postfix";
        private Postfix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator appears after its operand";
    }

    /**
     * Mixfix — operator interleaves with operands at multiple positions
     * ({@code if … then … else …}; {@code a ? b : c}).  Placeholder; full mixfix
     * support requires a position template that's not yet specified.
     */
    @Seed.Item(key = Mixfix.KEY)
    public static final class Mixfix {
        public static final String KEY = "cg.fixity:mixfix";
        private Mixfix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator interleaves with operands at multiple positions (e.g. if/then/else, ternary)";
    }

    /**
     * Circumfix — operator brackets its operand with matching tokens on both sides
     * ({@code |x|}, {@code ⌊x⌋}, {@code (…)}).  Placeholder.
     */
    @Seed.Item(key = Circumfix.KEY)
    public static final class Circumfix {
        public static final String KEY = "cg.fixity:circumfix";
        private Circumfix() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "operator brackets its operand with matching tokens on both sides (e.g. |x|, ⌊x⌋)";
    }

    // ----- Associativity values -------------------------------------------------------

    /** Left-associative: {@code 5-3-1} parses as {@code (5-3)-1}. Most arithmetic operators. */
    @Seed.Item(key = Left.KEY)
    public static final class Left {
        public static final String KEY = "cg.associativity:left";
        private Left() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "left-to-right grouping for equal-precedence operators";
    }

    /** Right-associative: {@code 2^3^2} parses as {@code 2^(3^2)}. Exponentiation, assignment. */
    @Seed.Item(key = Right.KEY)
    public static final class Right {
        public static final String KEY = "cg.associativity:right";
        private Right() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "right-to-left grouping for equal-precedence operators";
    }

    /** Non-associative: chaining requires explicit parentheses. */
    @Seed.Item(key = NonAssociative.KEY)
    public static final class NonAssociative {
        public static final String KEY = "cg.associativity:none";
        private NonAssociative() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "no grouping for equal-precedence operators; require parentheses";
    }
}
