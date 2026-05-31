package dev.everydaythings.graph.operator.collection;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.operator.FunctionNotation;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.scene.ContextChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Transform — the per-item template-expansion operator.  For each source
 * item, instantiates the template with that item pushed onto the
 * resolution context, and returns the resulting list of expanded bodies.
 *
 * <p>The bedrock collection operator: most other list-shaped operators
 * (Keep / Reduce / Sort / GroupBy) compose around Transform's per-item
 * semantics.  In the scene layer, {@code @Scene.Repeat} desugars to a
 * Transform-headed body sitting as the target of a parent Container's
 * {@code Children} binding; at resolve time the operator's results splat
 * into the Container's siblings via the resolver's collection-expansion
 * path.
 *
 * <h2>Roles, per the paper's mapping</h2>
 *
 * <ul>
 *   <li><b>THEME</b> — the source collection.  Resolved eagerly via the
 *       chain.  Expected to be a {@link Collection} of {@link Body}s after
 *       resolution (a list of items to transform); other types yield an
 *       empty result for now.</li>
 *   <li><b>INSTRUMENT</b> — the template body.  Deliberately NOT
 *       pre-resolved (this is the special-form override); resolved fresh
 *       per iteration with the current source item pushed onto the chain
 *       so {@code ?}-mode references in the template find that item's
 *       bindings first.</li>
 * </ul>
 *
 * <p>"Transform reactions with this template" — reactions is the THEME
 * (the thing being acted on), template is the INSTRUMENT (by what means).
 * Reads naturally in English; matches the cognitive structure of the
 * operation per §9 of the white paper.
 */
@Seed.Item(key = Transform.KEY, head = Operator.KEY)
@Seed.Embodies(key = Transform.KEY)
public class Transform extends Operator {

    public static final String KEY = "cg.predicate:transform";

    /** Arity — binary: THEME (source) + INSTRUMENT (template). */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "for each item in a source collection, instantiate a template with the item "
                    + "in scope, and return the list of resulting bodies; the bedrock "
                    + "per-item operator from which other collection operations compose";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishVerbLemmas = {"transform"};

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "transform";

    /** FunctionNotation lexeme — calling as {@code transform(items, template)}. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {FunctionNotation.KEY}))
    static final String functionName = "transform";

    public Transform(ItemRef iid) {
        super(iid);
    }

    public Transform(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    private static final CompoundKey THEME_KEY = CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY));
    private static final CompoundKey INSTRUMENT_KEY = CompoundKey.of(ItemRef.iid(ThematicRole.Instrument.KEY));

    /**
     * Special-form override: don't eagerly resolve all arguments — Transform
     * needs INSTRUMENT (the template) left unresolved so it can re-resolve
     * it per iteration with each source item pushed onto the chain.
     */
    @Override
    public Object evaluate(Frame frame, ContextChain chain) {
        Body body = frame.body();
        Binding sourceBinding = body.binding(THEME_KEY).orElse(null);
        Binding templateBinding = body.binding(INSTRUMENT_KEY).orElse(null);
        if (sourceBinding == null || templateBinding == null) return List.of();

        Object source = chain.resolveOne(sourceBinding);
        if (!(source instanceof Collection<?> sourceCollection)) {
            // For first cut, only Collection-typed sources are iterable.
            // A future enhancement could handle list-bodies (multi-binding
            // bodies under a known role) by extracting their bindings here.
            return List.of();
        }

        Object template = templateBinding.target();
        if (!(template instanceof Body templateBody)) return List.of();

        List<Body> out = new ArrayList<>(sourceCollection.size());
        for (Object item : sourceCollection) {
            if (!(item instanceof Body itemBody)) continue;
            ContextChain perItem = chain.pushing(itemBody);
            out.add(perItem.resolveBody(templateBody));
        }
        return out;
    }

    @Override
    protected Object evaluate(Frame frame) {
        // Transform is a special form — it MUST run via the two-arg evaluate
        // that has chain context.  Returning null here causes
        // Operator.receive to emit an empty response, which is the
        // honest answer when something dispatches Transform via the
        // eager pipeline (no chain available, so the per-iteration
        // resolution can't happen).  In normal use Transform is reached
        // through ContextChain.resolveBody, which calls the two-arg form.
        return null;
    }
}
