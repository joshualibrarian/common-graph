package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.quality.NameVocabulary;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Name — a compound Value carrying whichever name-parts apply to a person
 * or thing.  Bindings inside a Name body use name-part role sememes from
 * {@link NameVocabulary} (Given, Family, Middle, Nickname, Alias,
 * Patronymic, Maternal, Honorific, Suffix, ...).
 *
 * <p>Name also serves as the predicate when used in head-of-frame position:
 * {@code Person → [Name] → Name(given="Joshua")}.  Same sememe; the slot
 * the frame puts it in is what selects "predicate" vs "value-type".
 *
 * <p>Use whichever parts apply.  A Western name has GIVEN + FAMILY (+ MIDDLE).
 * A Spanish name might have GIVEN + FAMILY + MATERNAL (paternal then
 * maternal surname).  A Slavic name uses GIVEN + PATRONYMIC + FAMILY.  An
 * East Asian name has GIVEN + FAMILY in the same data shape; rendering
 * order is locale-dependent.  Mononymous individuals have just GIVEN.  Stage
 * or pen names use ALIAS.  All of these are the same value-type, different
 * bindings.
 *
 * <p>Names are content-addressed: two Name bodies with identical bindings
 * produce identical CIDs.  Dedup happens naturally — many people sharing
 * "Joshua" as their given name don't create N different Name bodies for
 * the lone-given-name case.
 *
 * <p>This is a <i>structured value</i>, not an Identifier.  Identifier
 * subclasses (EmailAddress, CILIID) commit to a single canonical text
 * form via {@code @Encode encodeText()}.  Names are too multi-cultural
 * for a single canonical text — rendering order is locale-dependent —
 * so Name extends Value directly.
 *
 * <p>Grounded in OEWN synset oewn-06344646-n (CILI {@code i69761}):
 * "a language unit by which a person or thing is known".
 */
@Seed.Item(key = Name.KEY, head = Value.KEY)
@Seed.Cili("i69761")
@Seed.Gloss(english = "a compound value carrying the parts of a person or thing's name "
                   + "(given, family, middle, nickname, patronymic, ...); cultures use "
                   + "whichever subset applies")
@Seed.Lexeme(english = "name", pos = PartOfSpeech.Noun.KEY)
public final class Name extends Value {

    public static final String KEY = "cg.archetype:name";

    private Name(List<Binding> bindings) {
        super(ItemRef.iid(KEY), bindings);
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    /** Fluent builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory for the common Western case: given + family.
     */
    public static Name of(String given, String family) {
        return builder().given(given).family(family).build();
    }

    /**
     * Typed view over an existing Body whose head is the Name archetype.
     * Throws if the body's head isn't Name; passes through if the body is
     * already a Name instance.
     */
    public static Name from(dev.everydaythings.graph.datum.Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Name name) return name;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the Name archetype: " + body.headRef());
        }
        // Bindings list is sorted-and-immutable on the source body; copy is safe.
        List<Binding> copy = new ArrayList<>(body.bindings());
        return new Name(copy);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The given name (first name), if present. */
    public Optional<String> given() { return part(NameVocabulary.Given.KEY); }

    /** The family name (surname), if present. */
    public Optional<String> family() { return part(NameVocabulary.Family.KEY); }

    /** The middle name, if present. */
    public Optional<String> middle() { return part(NameVocabulary.Middle.KEY); }

    /** The nickname, if present. */
    public Optional<String> nickname() { return part(NameVocabulary.Nickname.KEY); }

    /** The alias / pseudonym, if present. */
    public Optional<String> alias() { return part(NameVocabulary.Alias.KEY); }

    /** The patronymic, if present. */
    public Optional<String> patronymic() { return part(NameVocabulary.Patronymic.KEY); }

    /** The matronymic / maternal surname, if present. */
    public Optional<String> maternal() { return part(NameVocabulary.Maternal.KEY); }

    /** The honorific / title, if present. */
    public Optional<String> honorific() { return part(NameVocabulary.Honorific.KEY); }

    /** The generational or credential suffix, if present. */
    public Optional<String> suffix() { return part(NameVocabulary.Suffix.KEY); }

    private Optional<String> part(String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : bindings()) {
            if (role.equals(b.role())
                    && b.qualifiers().isEmpty()
                    && b.target() instanceof String s) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    /** Fluent builder for Name.  Empty or null parts are silently dropped. */
    public static final class Builder {
        private final List<Binding> bindings = new ArrayList<>();

        public Builder given(String text)      { return part(NameVocabulary.Given.KEY, text); }
        public Builder family(String text)     { return part(NameVocabulary.Family.KEY, text); }
        public Builder middle(String text)     { return part(NameVocabulary.Middle.KEY, text); }
        public Builder nickname(String text)   { return part(NameVocabulary.Nickname.KEY, text); }
        public Builder alias(String text)      { return part(NameVocabulary.Alias.KEY, text); }
        public Builder patronymic(String text) { return part(NameVocabulary.Patronymic.KEY, text); }
        public Builder maternal(String text)   { return part(NameVocabulary.Maternal.KEY, text); }
        public Builder honorific(String text)  { return part(NameVocabulary.Honorific.KEY, text); }
        public Builder suffix(String text)     { return part(NameVocabulary.Suffix.KEY, text); }

        /**
         * Set an arbitrary name-part by role IID.  Useful when communities
         * extend the name-part vocabulary with culture-specific roles not
         * pre-declared on this builder.
         */
        public Builder part(String roleKey, String text) {
            if (text == null || text.isEmpty()) return this;
            bindings.add(Binding.literal(ItemRef.iid(roleKey), text));
            return this;
        }

        public Name build() {
            return new Name(new ArrayList<>(bindings));
        }
    }
}
