package dev.everydaythings.graph.actor;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Actor — the abstract base archetype for social entities.
 *
 * <p>An Actor is a thing in the world that a {@link
 * dev.everydaythings.graph.cryptography.Signer Signer} may represent.  Actors are
 * orthogonal to Signers: a Signer is a cryptographic identity (has keys, can
 * sign), an Actor is a social identity (a person, a group, a service).  The
 * two are connected by the {@code REPRESENTS} predicate from
 * {@link ActorVocabulary}: zero or more Signers represent zero or more Actors.
 *
 * <p>An Actor can exist without any Signer.  Historical figures, the deceased,
 * children, anyone who hasn't ever held cryptographic keys still have Person
 * items in the graph if anyone cares to record them.  Equally, a Signer can
 * exist without representing any known Actor — anonymous pubkey-derived
 * Signers stay anonymous unless someone attests a representation link.
 *
 * <p>Three concrete sub-archetypes:
 * <ul>
 *   <li>{@link Person} — a human.</li>
 *   <li>{@link Group} — any collective (organization, family, team, informal
 *       group).  Sub-types live as bindings on the Group, not as archetypes.</li>
 *   <li>{@link Service} — software / automated entity (infrastructure or
 *       autonomous bot).  Variation also expressed via bindings.</li>
 * </ul>
 *
 * <p>Each sub-archetype is expected to grow its own sub-archetypes in time;
 * the current three are a deliberate minimum that captures the load-bearing
 * distinctions (human / collective / automated) without prematurely
 * multiplying classes.
 */
@Seed.Item(key = Actor.KEY)
public abstract class Actor extends Item {

    /** Canonical key for the Actor archetype. */
    public static final String KEY = "cg.archetype:actor";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a social entity in the world — a person, a group, or a service — "
                    + "that one or more signers may represent; orthogonal to the "
                    + "cryptographic signer archetype";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "actor";

    protected Actor(ItemRef iid) {
        super(iid);
    }

    protected Actor(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
