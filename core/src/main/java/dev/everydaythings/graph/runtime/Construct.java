package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;

/**
 * The {@code CONSTRUCT} predicate — post-instantiation hook for new items.
 *
 * <p>After a {@link Create} frame produces a new item and commits its initial
 * manifest, the librarian submits a CONSTRUCT frame targeting the new item.
 * An archetype that wants to set up domain-specific initial state (default
 * bindings, child structures, etc.) declares a {@code @Handler(predicate=Construct.KEY)}
 * on its embodying class.
 *
 * <p>By default no archetype handles CONSTRUCT — the hook is a no-op for items
 * with no special initialization needs.
 *
 * <p>Bindings:
 *
 * <ul>
 *   <li>{@code THEME → @<new-item>} — the freshly created item.</li>
 *   <li>{@code AGENT → @<creator>} — same agent who created (carried through).</li>
 * </ul>
 */
@Seed.Item(key = Construct.KEY, head = CoreVocabulary.Predicate.KEY)
public final class Construct {

    public static final String KEY = "cg.predicate:construct";
    public static final ItemID IID = ItemID.fromString(KEY);

    private Construct() {}

    @Seed.Frame(predicate = Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the post-create hook predicate — fires after a CREATE produces a new item, "
                    + "so the archetype can set up its initial state (default bindings, etc.)";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "construct";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "construction";
}
