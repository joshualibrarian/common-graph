package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * CILIID — Collaborative Interlingual Index identifier.  The language-neutral
 * concept identifier that aligns CG sememes with WordNets across languages.
 *
 * <p>Form: an {@code "i"} prefix followed by a non-negative integer.
 * Examples: {@code "i12345"}, {@code "i69788"}, {@code "i0"}.
 *
 * <p>Stored as an atomic Body whose head is the CILIID archetype and whose
 * content is the canonical text (the full {@code "iN"} string).  Two CILIIDs
 * with identical text produce identical CIDs, so the body is content-addressed
 * naturally.
 *
 * <p>The reference list of valid CILI identifiers lives in
 * {@code core/src/main/resources/ili.ttl} (Turtle form, with English
 * definitions and links back to PWN30 synsets).  Anchoring a CG sememe to a
 * specific CILIID claims the same meaning as the corresponding WordNet
 * synset, enabling cross-language alignment.
 */
@Seed.Item(key = CILIID.KEY, head = Identifier.KEY)
public final class CILIID extends Identifier {

    public static final String KEY = "cg.archetype:cili-id";

    /** "i" followed by one or more digits.  Leading zeros are permitted but unusual. */
    private static final Pattern PATTERN = Pattern.compile("^i\\d+$");

    private CILIID(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    /**
     * Parse a CILI identifier text into a typed CILIID.  Whitespace is
     * stripped; the result must match {@code i<digits>}.
     *
     * @throws IllegalArgumentException if {@code text} doesn't parse as a
     *         well-formed CILI identifier
     */
    @Decode
    public static CILIID fromText(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Not a valid CILI identifier (expected i<digits>): " + text);
        }
        return new CILIID(trimmed);
    }

    /**
     * The canonical {@code "iN"} text.
     */
    @Override
    @Encode
    public String encodeText() {
        return (String) atomicContent().orElseThrow();
    }

    /**
     * The integer portion of this CILI identifier (everything after the
     * leading {@code i}).  Useful when comparing or indexing numerically.
     */
    public long number() {
        return Long.parseLong(encodeText().substring(1));
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a Collaborative Interlingual Index (CILI) identifier — the language-neutral "
                    + "concept id that aligns sememes with WordNet synsets across languages";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "CILI identifier";
}
