package dev.everydaythings.graph.actor;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Group — a collective, modeled as an {@link Item}.
 *
 * <p>Covers any kind of grouping: organizations, families, teams, informal
 * collectives, governments, nation-states.  Variation in structure (legal
 * personhood, kinship vs voluntary association, hierarchical vs flat) is
 * expressed through bindings on the Group item rather than via separate
 * archetypes.  Sub-archetypes can be introduced later as concrete needs
 * arise (e.g., {@code LegalEntity}, {@code Family}) but the load-bearing
 * distinctions don't require an archetype-level split.
 */
@Seed.Item(key = Group.KEY)
@Seed.Cili("i35589")
public class Group extends Item {

    /** Canonical key for the Group archetype. */
    public static final String KEY = "cg.archetype:group";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a collective actor of any kind — organizations, families, teams, "
                    + "governments, informal collectives — varied by bindings rather "
                    + "than by sub-archetype";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "group";

    public Group(ItemRef iid) {
        super(iid);
    }

    public Group(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
