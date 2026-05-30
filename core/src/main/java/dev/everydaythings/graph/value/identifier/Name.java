package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.List;
import java.util.Objects;

/**
 * Name — abstract intermediate {@link Identifier} for plain-text designators.
 *
 * <p>A Name is a kind of identifier whose canonical form is just text — a
 * word or short phrase by which an entity is known.  Names contrast with
 * formatted identifiers (EmailAddress, PhoneNumber, URL, ISBN, platform-
 * specific account IDs): those have structural rules baked into their
 * syntax; Names are just words.
 *
 * <p>Names come in two structural shapes:
 *
 * <ul>
 *   <li><b>Atomic</b> — leaf-text Names (GivenName, FamilyName, Nickname,
 *       Alias, Pseudonym, Honorific, Suffix, Patronymic, Maternal, Handle).
 *       Each carries a single canonical text string in the Body's atomic
 *       content slot.  Light validation (non-empty after trim, no control
 *       characters, length cap).  Indexed by the token dictionary as
 *       words for name-based lookup.</li>
 *   <li><b>Compound</b> — structured Names whose body carries indexed
 *       bindings to other Name instances ({@link FullName}).  Used when a
 *       single name is composed of ordered parts (Western "given middle
 *       family suffix", Spanish "given paternal maternal", East Asian
 *       "family given").</li>
 * </ul>
 *
 * <p>Each concrete Name subtype represents a different kind of name.
 * "Joshua" stored as a GivenName is structurally different from "Joshua"
 * stored as a Nickname — the kind IS part of the identity.  Two
 * GivenName("Joshua") instances share content-addressed identity (same
 * CID), so name-based dedup works at the part level naturally.
 *
 * <p>Name also serves as the predicate when used in head-of-frame
 * position: {@code Person → [Name] → GivenName("Joshua")} reads
 * "this person is known by this name."  Same dual noun-and-predicate
 * pattern as {@link Identifier}.
 *
 * <p>Validation baseline (every Name subtype enforces):
 * <ul>
 *   <li>Non-empty after {@link String#trim trim}.</li>
 *   <li>No ISO control characters.</li>
 *   <li>Length cap (currently 256 chars; bumpable if real call sites need).</li>
 * </ul>
 * Subtypes MAY add stricter rules; most don't.
 *
 * <p>Grounded in OEWN synset oewn-06344646-n (CILI {@code i69761}):
 * "a language unit by which a person or thing is known."
 */
@Seed.Item(key = Name.KEY, head = Identifier.KEY)
@Seed.Cili("i69761")
public abstract class Name extends Identifier {

    public static final String KEY = "cg.archetype:name";

    /** Maximum canonical-text length any Name accepts.  Subtypes may tighten. */
    public static final int MAX_LENGTH = 256;

    /**
     * Atomic-form constructor — for leaf-text Name subtypes.  Subclasses
     * pass the canonical text after validation via
     * {@link #validateName(String, String)}.
     */
    protected Name(ItemRef head, String canonicalText) {
        super(head, canonicalText);
    }

    /**
     * Structured-form constructor — for compound Name subtypes ({@link
     * FullName}).  Subclasses pass the ordered binding list.
     */
    protected Name(ItemRef head, List<Binding> bindings) {
        super(head, bindings);
    }

    /**
     * Baseline plain-text validation shared by atomic Name subtypes.
     * Returns the canonical (trimmed) form.
     *
     * <p>Rules: non-empty after trim, no ISO control characters, length
     * within {@link #MAX_LENGTH}.  Subtypes that need stricter validation
     * call this first then apply their own rules.
     *
     * @param text the raw input text
     * @param typeName human-readable subtype name for error messages
     * @return the trimmed canonical text
     * @throws IllegalArgumentException if validation fails
     */
    protected static String validateName(String text, String typeName) {
        Objects.requireNonNull(text, typeName + " text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(typeName + " cannot be empty");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    typeName + " exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                throw new IllegalArgumentException(
                        typeName + " contains a control character at position " + i);
            }
        }
        return trimmed;
    }

    @Override
    @Encode
    public String encodeText() {
        // Atomic subtypes store their text in atomic content.  Structured
        // subtypes (FullName) override this to render from their bindings.
        return (String) atomicContent().orElseThrow(() -> new IllegalStateException(
                getClass().getSimpleName() + " has no atomic content and no encodeText override"));
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a plain-text designator for an entity — a word or short phrase by which "
                    + "it is known; the umbrella for given names, family names, nicknames, "
                    + "handles, and other word-shaped identifiers (in contrast to formatted "
                    + "identifiers like email addresses or phone numbers)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "name";

    /**
     * Past-participle verb forms used in secondary predication — "user <b>named</b>
     * bob", "a planet <b>called</b> Mars".  These are the surface forms the parser
     * looks up directly; inflected forms aren't derived at parse time, so they must
     * exist in the token dictionary as their own lexeme entries.  When the WordNet/
     * UniMorph import runs, it merges idempotently with these.
     *
     * <p>"called" carries Name's dub/designate sense ("they called him Bob"), which
     * collapses onto Name like "name" does — the same sememe in a different surface
     * word.
     */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY,
                        GrammaticalFeature.Past.KEY, GrammaticalFeature.Participle.KEY}))
    static final String[] englishParticiples = {"named", "called"};
}
