package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.Value;

/**
 * Identifier — the archetype of addressable, canonicalizable handles for
 * things in the world.  Sits between {@link Value} and the concrete
 * identifier types:
 *
 * <pre>
 * Value
 *   └── Identifier              ← this class / cg.archetype:identifier
 *         ├── EmailAddress      ← alice@example.com
 *         ├── PhoneNumber       ← +1-555-1234
 *         ├── URL               ← https://example.com/path
 *         ├── ISBN              ← 978-...
 *         ├── DOI               ← 10.1038/...
 *         └── ...
 * </pre>
 *
 * <p>An Identifier is a Value whose semantics are <i>"this designates a thing
 * in some namespace"</i>.  Two further commitments distinguish it from plain
 * Value:
 *
 * <ol>
 *   <li><b>Canonical text form.</b>  Each subclass implements
 *       {@link Encode @Encode} {@code String encodeText()} returning the
 *       authoritative human-typeable form (the literal you'd write in a vCard,
 *       a contact form, a search box).  This is also what the token dictionary
 *       indexes so users can resolve "alice@example.com" to the right entity
 *       on a CG prompt.</li>
 *
 *   <li><b>Parse-and-validate factory.</b>  Each subclass exposes a
 *       {@code @Decode static T fromText(String)} that validates and
 *       canonicalizes raw input.  Invalid input throws; valid input lands as
 *       a typed instance whose body is content-addressed by its canonical
 *       text.  Validation is enforced in Java code (regex sometimes suffices,
 *       libphonenumber / RFC 5322 / RFC 3986 for the harder ones).</li>
 * </ol>
 *
 * <p>Storage: atomic-form Body.  The body's content is the canonical text (or
 * other leaf value).  Two equal identifiers have byte-identical bodies and
 * therefore identical CIDs, so reference-by-CID dedup happens naturally.
 *
 * <p>For identifiers whose canonical form needs internal structure (rare —
 * PostalAddress is the main case in mind), a subclass may opt to call the
 * structured Body constructor instead.  The atomic-form constructor below is
 * the common path.
 *
 * <p>Abstract by intent: there is no generic "Identifier" instance.  Always
 * mint a concrete subclass.
 *
 * <p>Identifier also serves as the predicate when used in head-of-frame
 * position: {@code Person → [Identifier] → CILIID("i12345")} reads "this
 * person is identified by this CILI id."  Same sememe; the slot the frame
 * puts it in is what selects "predicate" vs "value-type."  Grounded in OEWN
 * synset oewn-06350278-n (CILI {@code i69788}): "identifying word or words
 * by which someone or something is called and classified or distinguished
 * from others" (appellation, denomination, designation, appellative).
 */
@Seed.Item(key = Identifier.KEY, head = Value.KEY)
@Seed.Cili("i69788")
public abstract class Identifier extends Value {

    public static final String KEY = "cg.archetype:identifier";

    /**
     * Atomic-form constructor: builds a Body with the given head and the
     * canonical leaf content (typically a {@link String} canonical form, but
     * any leaf type Body accepts is fine).
     */
    protected Identifier(ItemRef head, Object canonicalContent) {
        super(head, canonicalContent);
    }

    /**
     * The canonical human-typeable form of this identifier — the literal
     * users write and look up by.  Each subclass implements this with an
     * {@link Encode @Encode} annotation so codecs and the token dictionary
     * pick it up automatically.
     *
     * <p>Round-trip contract: {@code Subclass.fromText(id.encodeText())}
     * must return a value equal to {@code id}.
     */
    @Encode
    public abstract String encodeText();

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the archetype of identifiers — addressable, canonicalizable handles "
                    + "that designate things in some namespace (email addresses, phone "
                    + "numbers, URLs, ISBNs, DOIs, ...); each carries a canonical text "
                    + "form for user lookup";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "identifier";
}
