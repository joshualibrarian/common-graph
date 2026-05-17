package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import java.util.List;
import java.util.Objects;

/**
 * Gradient — the abstract parent value-archetype for color gradients
 * painted as backgrounds.
 *
 * <p>Gradient is not directly instantiable.  Concrete gradients are
 * subarchetypes with their own shape: {@link LinearGradient} carries an
 * angle plus color stops; {@link RadialGradient} carries a shape +
 * position + color stops.  Each subarchetype's body has head = its
 * specific archetype IID and the protocol-specific bindings.
 *
 * <p>The parallel to {@link Endpoint} → TcpEndpoint/UnixEndpoint and to
 * {@link Quantity} → Length/Mass is exact: an abstract value-archetype
 * with no minted instances of its own, refined by subarchetypes with
 * their own shapes.
 */
@Seed.Item(key = Gradient.KEY, head = Value.KEY)
public abstract class Gradient extends Value {

    public static final String KEY = "cg.archetype:gradient";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the abstract archetype of color gradients — refined by per-shape "
                    + "subarchetypes (LinearGradient, RadialGradient) each declaring its "
                    + "own geometry";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "gradient";

    /**
     * Subclass constructor — each concrete gradient subarchetype calls
     * this with its own archetype IID as head and its shape-specific
     * bindings.
     */
    protected Gradient(ItemRef head, List<Binding> bindings) {
        super(head, bindings);
    }

    /**
     * Typed view over an existing Body whose head is one of the Gradient
     * subarchetypes.  Dispatches to the appropriate subclass's
     * {@code from(Body)} based on the body's head IID.
     */
    public static Gradient from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Gradient g) return g;
        ItemRef head = (body.headRef() instanceof ItemRef ir) ? ir : null;
        if (head == null) {
            throw new IllegalArgumentException(
                    "Gradient body head must be an ItemRef, got " + body.headRef());
        }
        if (ItemRef.iid(LinearGradient.KEY).equals(head)) return LinearGradient.from(body);
        if (ItemRef.iid(RadialGradient.KEY).equals(head)) return RadialGradient.from(body);
        throw new IllegalArgumentException(
                "Body head is not a known Gradient subarchetype: " + head);
    }
}
