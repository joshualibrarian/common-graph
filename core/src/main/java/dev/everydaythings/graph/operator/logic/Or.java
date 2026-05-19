package dev.everydaythings.graph.operator.logic;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.BinaryLogical;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The logical-OR operator. Infix, left-associative, precedence 0 (lowest binary). */
@Seed.Item(key = Or.KEY, head = Operator.KEY)
@Seed.Embodies(key = Or.KEY)
public class Or extends BinaryLogical {

    public static final String KEY = "cg.predicate:or";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns Bool. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "logical disjunction — true when at least one operand is true";

    /** OperatorNotation lexeme — symbol with Infix qualifier plus Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {OperatorNotation.KEY, Operator.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = 0),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.Left.KEY)
          })
    static final String symbol = "||";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Conjunction.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishConjunctionLemma = "or";

    public Or(ItemRef iid) { super(iid); }
    public Or(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override protected Boolean apply(boolean l, boolean r) { return l || r; }
}
