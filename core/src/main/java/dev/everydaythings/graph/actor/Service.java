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
 * Service — an automated entity, modeled as an {@link Item}.
 *
 * <p>Software running on someone's behalf.  Bridges (web gateway, ActivityPub,
 * email, telephony), relays, witnesses, autonomous bots, search endpoints —
 * all variations of Service.  The reactive/autonomous distinction
 * (infrastructure vs participant) is carried by bindings on the Service
 * rather than by separate archetypes.
 *
 * <p>A Service's Signer is often operated on behalf of another entity (a
 * Group that runs the bridge, a Person who runs their own bot); that
 * relationship is expressed by additional REPRESENTS frames pointing from
 * the Service's Signer at the operating entity.
 */
@Seed.Item(key = Service.KEY)
@Seed.Cili("i38428")
public class Service extends Item {

    /** Canonical key for the Service archetype. */
    public static final String KEY = "cg.archetype:service";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "an automated actor — software running on someone's behalf, whether "
                    + "reactive infrastructure (bridges, relays, witnesses) or "
                    + "autonomous participant (bots, agents); varied by bindings";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "service";

    public Service(ItemRef iid) {
        super(iid);
    }

    public Service(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
