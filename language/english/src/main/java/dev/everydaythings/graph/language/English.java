package dev.everydaythings.graph.language;

import com.ibm.icu.util.ULocale;
import dev.everydaythings.graph.*;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;

/**
 * The English language item — singleton implementation, ISO 639-3 code "eng".
 *
 * <p>English extends {@link Language} and provides English-specific behavior: the
 * locale ({@code en}), word-order rules, agreement, lexeme selection, structural
 * insertions (articles, prepositions, copulas), and morphology. Concrete grammar
 * rules will be added incrementally as parse/render tests drive their need.
 *
 * <p>Sub-Languages for regional variants (English-US, English-GB, English-AU,
 * etc.) will extend this class, override {@link #locale()} to return their BCP-47
 * locale, and declare regional lexeme overrides ("color" vs "colour", date format,
 * currency). When a sub-Language doesn't override a behavior, it inherits from this
 * base English implementation.
 */
@Seed.Item(key = English.KEY, head = Language.KEY)
@Seed.Embodies(key = English.KEY)
public class English extends Language {

    /** Canonical key — matches {@code Language.English.KEY} in {@code :core}. */
    public static final String KEY = "cg.lang:eng";

    /** Deterministic IID for the English language item. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "the English language";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "English";

    public English(ItemID iid) {
        super(iid);
    }

    public English(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    @Override
    public ULocale locale() {
        return ULocale.ENGLISH;
    }

    // TODO: override parse(ParseContext) for English grammar contributions.
    // TODO: override render(FrameMap, ParseParams) for English text rendering.
}
