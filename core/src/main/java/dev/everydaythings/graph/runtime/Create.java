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
import dev.everydaythings.graph.value.Literal;

/**
 * The {@code CREATE} predicate — instantiate a fresh item.
 *
 * <p>A CREATE frame submitted to a Librarian causes a new item to be minted,
 * its initial manifest committed, and (optionally) a post-construct hook fired.
 * The CREATE frame itself is the durable record of the act; no separate
 * "CREATED" response frame is emitted.
 *
 * <p>Bindings:
 *
 * <ul>
 *   <li>{@code THEME → @<archetype>} — required: the kind of item to create.</li>
 *   <li>{@code AGENT → @<signer>} — auto-populated from the CREATE frame's records;
 *       identifies who authorized the creation.</li>
 *   <li>{@code INSTRUMENT → @<implementation>} — optional: a specific
 *       implementation to use. Can be a Java-class {@link Literal}
 *       or an item reference. When absent, the librarian falls back to the
 *       archetype's own IMPLEMENTATION binding (Phase 2: trust-matrix-weighted
 *       selection among available implementations).</li>
 *   <li>Other bindings carry forward as initial manifest bindings on the new item.</li>
 * </ul>
 *
 * <p>The new item's IID is deterministically derived from the CREATE frame's
 * body DatumID — so repeating the same CREATE produces the same IID (idempotent)
 * but two CREATEs with different timestamps in their records produce different
 * IIDs (the records aren't part of the body; but the body's datum hash is
 * deterministic from the frame's content, which the caller controls).
 */
@Seed.Item(key = Create.KEY, head = CoreVocabulary.Predicate.KEY)
public final class Create {

    public static final String KEY = "cg.predicate:create";
    public static final ItemID IID = ItemID.fromString(KEY);

    private Create() {}

    @Seed.Frame(predicate = Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the predicate for instantiating a new item — submit a CREATE frame with "
                    + "a THEME archetype and an authorizing record; the new item is minted, "
                    + "committed, and post-constructed";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "create";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "creation";
}
