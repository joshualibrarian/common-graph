package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;

/**
 * The {@code DELETE} predicate — a request to remove an item from local storage.
 *
 * <p>A DELETE frame is a <i>request</i>: it claims "the signer wants this item gone."
 * Each librarian receiving the frame decides independently whether to honor the
 * request, based on its own trust matrix. Phase 1 honors only DELETEs whose
 * records carry a signature verifiable against this librarian's own KEL — the
 * implicit "I'm asking my own librarian to delete this" case. Other-signed
 * DELETEs are stored as data but not acted upon.
 *
 * <p>When honored, the librarian's handler:
 * <ul>
 *   <li>Cascade-deletes the target item's manifest bodies and their records</li>
 *   <li>Evicts the item from the in-memory cache</li>
 *   <li>Does NOT cascade through endorsed frames — those may be referenced
 *       elsewhere; dangling references are an accepted cost.</li>
 * </ul>
 *
 * <p>The DELETE frame itself is retained as audit data regardless of whether
 * the action was taken — "the signer requested this; at time T; with this
 * authorization claim."
 *
 * <p>Bindings:
 * <ul>
 *   <li>{@code THEME → @<item-iid>} — required: the item to delete.</li>
 *   <li>{@code AGENT → @<signer>} — auto-populated from records.</li>
 * </ul>
 */
@Seed.Item(key = Delete.KEY, head = CoreVocabulary.Predicate.KEY)
public final class Delete {

    public static final String KEY = "cg.predicate:delete";
    public static final ItemID IID = ItemID.fromString(KEY);

    private Delete() {}

    @Seed.Frame(predicate = Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the predicate for requesting removal of an item from local storage — "
                    + "each librarian decides whether to honor based on its trust matrix";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "delete";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "deletion";
}
