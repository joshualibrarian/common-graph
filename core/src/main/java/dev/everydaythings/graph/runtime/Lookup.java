package dev.everydaythings.graph.runtime;
import dev.everydaythings.graph.SchemaVocabulary;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;

/**
 * The {@code LOOKUP} predicate — token-dictionary lookup.
 *
 * <p>A LOOKUP frame submitted to a Librarian consults the TokenDictionary and
 * returns response frames carrying postings. The frame's bindings:
 *
 * <ul>
 *   <li>{@code THEME → "<token>"} — the token text being looked up (required)</li>
 *   <li>{@code ATTRIBUTE[LIMIT] → <integer>} — optional; if present, prefix
 *       (range-scan) match with the given upper bound on results. If absent,
 *       exact (point) match.</li>
 * </ul>
 *
 * <p>LOOKUP frames are <b>ephemeral</b> — the predicate's manifest carries
 * {@code CONFIG[RETENTION] → @Ephemeral}, so submit doesn't persist body or
 * records. The handler fires; response frames flow back to the submitter; no
 * audit trail is kept. This is appropriate because LOOKUP is the per-keystroke
 * query a UI client issues during autocomplete or token disambiguation —
 * persisting every keystroke would be both privacy-toxic and storage-bloat.
 */
@Seed.Item(key = Lookup.KEY, head = CoreVocabulary.Predicate.KEY)
public final class Lookup {

    public static final String KEY = "cg.predicate:lookup";
    public static final ItemID IID = ItemID.fromString(KEY);

    private Lookup() {}

    @Seed.Frame(predicate = Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the predicate for token-dictionary lookup — submit a LOOKUP frame "
                    + "with a token in THEME to receive postings as ephemeral responses";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "look up";

    @Seed.Frame(predicate = Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "lookup";

    /**
     * CONFIG[RETENTION] → Ephemeral: LOOKUP frames are not persisted.
     */
    @Seed.Frame(predicate = CoreVocabulary.Config.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {SchemaVocabulary.Retention.KEY}))
    static final ItemID retention = SchemaVocabulary.Ephemeral.IID;
}
