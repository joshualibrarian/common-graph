package dev.everydaythings.graph.operator.logic;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.Bool;

/**
 * The universal-truth wildcard.  Zero-ary; fully applied always returns TRUE.
 *
 * <p>In a binding-target position (e.g., {@code THEME = Any} inside a frame),
 * the partial-application rule for Bool-returning sememes turns Any into the
 * trivial pattern that accepts any candidate value.  Used as the "match
 * anything here" marker in queries and EXPECTS declarations.
 *
 * <p>Linguistically a determiner / pronoun ("any value", "any of them"),
 * but in the system it's a 0-ary operator returning Bool.  The matcher-ness
 * falls out of partial application; no special return type needed.
 */
@Seed.Item(key = Any.KEY, head = Operator.KEY)
@Seed.Embodies(key = Any.KEY)
public class Any extends Operator {

    public static final String KEY = "cg.predicate:any";

    /** Arity — zero-ary. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 0;

    /** Returns Bool — the operator yields true (and partial-applies to a matcher). */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the universal-truth wildcard — fully applied returns TRUE; in a binding-target "
                    + "with no candidate, becomes the trivial pattern that accepts any value";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "any";

    public Any(ItemRef iid) { super(iid); }
    public Any(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        return Boolean.TRUE;
    }
}
