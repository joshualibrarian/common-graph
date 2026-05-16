package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.id.ItemRef;

import java.util.List;

/**
 * The Value meta-archetype — the Java mirror of the {@code cg.archetype:value}
 * sememe.  Sits one level below {@link Body} in the Datum hierarchy and one
 * level below {@link dev.everydaythings.graph.CoreVocabulary.Archetype Archetype}
 * in the sememe hierarchy:
 *
 * <pre>
 * Datum
 *   └── Body
 *         └── Value          ← this class / cg.archetype:value
 *               ├── Color    ← cg.archetype:color
 *               ├── Quantity ← cg.value:quantity
 *               └── ...
 * </pre>
 *
 * <p>A Value is the kind-of-thing whose instances are bodies with head + typed
 * component bindings — Quantity ({@code Value=N, @Meter=1}), Color
 * ({@code R=255, G=0, B=0}), Point ({@code X=3, Y=4}), and so on.  Subclasses
 * supply their own typed accessors over the underlying bindings; this class
 * just forwards construction to {@link Body}.
 *
 * <p>Abstract by intent: there is no generic "Value" instance — you always
 * mint a specific subclass (Color, Quantity, ...).  For ad-hoc body-shaped
 * data without a typed subclass, use {@link Body} directly.
 */
@Seed.Item(key = Value.KEY)
public abstract class Value extends Body {

    public static final String KEY = "cg.archetype:value";

    protected Value(ItemRef head, List<Binding> bindings) {
        super(head, bindings);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the archetype of values — structured data shapes whose instances are bodies "
                    + "with head + typed component bindings (Quantity, Color, Point, ...)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "value";
}
